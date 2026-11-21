    package com.balatechdev.vaultzero.security.auth

import android.content.Context
import com.balatechdev.vaultzero.security.MemoryZeroizer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

open class StorageException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
class CorruptedStorageException(message: String, cause: Throwable? = null) : StorageException(message, cause)

/**
 * AndroidPersistentVaultKeyStorage
 *
 * Production implementation of [VaultKeyStorage] persisting the [MasterKeyEnvelope] across app restarts.
 *
 * Security Architecture:
 * 1. Protected by an Android Keystore 256-bit AES-GCM key (`vaultzero_master_envelope_key`).
 * 2. Operates strictly via [Cipher] with the non-exportable [SecretKey] from `AndroidKeyStore`.
 * 3. Never attempts to read or export raw key bytes (`key.encoded` is NEVER accessed).
 * 4. Unique 12-byte CSPRNG IV generated for every encryption.
 * 5. Format version and container authenticated via AAD.
 * 6. Validates serialized envelope lengths and KDF parameter ranges.
 * 7. Writes atomically via staging file (.tmp) with fsync before atomic rename.
 * 8. Explicitly detects corruption and throws [CorruptedStorageException], preventing silent fresh installations.
 */
class AndroidPersistentVaultKeyStorage(
    private val context: Context? = null,
    private val storageFileName: String = DEFAULT_STORAGE_FILE,
    private val keyAlias: String = DEFAULT_KEYSTORE_ALIAS,
    private val customKeyStore: KeyStore? = null,
    private val customSecretKey: SecretKey? = null,
    private val customBaseDir: File? = null,
    private val secureRandom: SecureRandom = SecureRandom()
) : VaultKeyStorage {

    private val targetFile: File
        get() = File(customBaseDir ?: context?.filesDir ?: File("."), storageFileName)

    private val tmpFile: File
        get() = File(customBaseDir ?: context?.filesDir ?: File("."), "$storageFileName.tmp")

    private val keyStore: KeyStore by lazy {
        customKeyStore ?: KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    init {
        if (customSecretKey == null) {
            ensureKeystoreKeyExists()
        }
    }

    private fun ensureKeystoreKeyExists() {
        if (customSecretKey != null) return
        try {
            if (!keyStore.containsAlias(keyAlias)) {
                generateKeystoreKey()
            }
        } catch (e: Exception) {
            // In unit test environments without AndroidKeyStore SPI, defer or report
        }
    }

    private fun generateKeystoreKey() {
        if (customSecretKey != null) return
        try {
            val keyGenClass = Class.forName("android.security.keystore.KeyGenParameterSpec\$Builder")
            val keyPropertiesClass = Class.forName("android.security.keystore.KeyProperties")

            val purposeEncrypt = keyPropertiesClass.getField("PURPOSE_ENCRYPT").getInt(null)
            val purposeDecrypt = keyPropertiesClass.getField("PURPOSE_DECRYPT").getInt(null)
            val blockModeGcm = keyPropertiesClass.getField("BLOCK_MODE_GCM").get(null) as String
            val paddingNone = keyPropertiesClass.getField("ENCRYPTION_PADDING_NONE").get(null) as String

            val constructor = keyGenClass.getConstructor(String::class.java, Int::class.javaPrimitiveType)
            val builder = constructor.newInstance(keyAlias, purposeEncrypt or purposeDecrypt)

            val setBlockModes = keyGenClass.getMethod("setBlockModes", Array<String>::class.java)
            setBlockModes.invoke(builder, arrayOf(blockModeGcm))

            val setEncryptionPaddings = keyGenClass.getMethod("setEncryptionPaddings", Array<String>::class.java)
            setEncryptionPaddings.invoke(builder, arrayOf(paddingNone))

            val setKeySize = keyGenClass.getMethod("setKeySize", Int::class.javaPrimitiveType)
            setKeySize.invoke(builder, 256)

            val buildMethod = keyGenClass.getMethod("build")
            val spec = buildMethod.invoke(builder)

            val keyGenerator = KeyGenerator.getInstance("AES", ANDROID_KEYSTORE)
            val initMethod = keyGenerator.javaClass.getMethod("init", java.security.spec.AlgorithmParameterSpec::class.java)
            initMethod.invoke(keyGenerator, spec)
            keyGenerator.generateKey()
        } catch (e: Exception) {
            throw StorageException("Failed to generate Android Keystore envelope encryption key: ${e.message}", e)
        }
    }

    private fun getSecretKey(): SecretKey {
        if (customSecretKey != null) return customSecretKey
        ensureKeystoreKeyExists()
        return (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: throw StorageException("SecretKey for alias '$keyAlias' not found in KeyStore")
    }

    override fun saveEnvelope(envelope: MasterKeyEnvelope) {
    val serializedPlaintext = serializeEnvelope(envelope)

    // Encrypt the serialized envelope using the Android Keystore key.
    // Android Keystore generates the GCM IV automatically.
    val encryptionResult = try {
        val secretKey = getSecretKey()
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)

        cipher.init(Cipher.ENCRYPT_MODE, secretKey)

        val iv = cipher.iv
            ?: throw StorageException(
                "Android Keystore did not generate an IV"
            )

        cipher.updateAAD(AAD_V1)

        val encryptedPayload = cipher.doFinal(serializedPlaintext)

        Pair(iv, encryptedPayload)
    } finally {
        // Clear plaintext from memory even if encryption fails.
        MemoryZeroizer.zeroize(serializedPlaintext)
    }

    val iv = encryptionResult.first
    val encryptedPayload = encryptionResult.second

    // Write container format atomically:
    // MAGIC (5B) + VERSION (1B) + IV (12B) + LENGTH (4B) + CIPHERTEXT
    val parentDir = targetFile.parentFile

    if (parentDir != null && !parentDir.exists()) {
        if (!parentDir.mkdirs() && !parentDir.exists()) {
            throw StorageException(
                "Failed to create storage directory"
            )
        }
    }

    try {
        FileOutputStream(tmpFile).use { fos ->
            val dos = DataOutputStream(fos)

            dos.write(MAGIC_HEADER)
            dos.writeByte(STORAGE_VERSION_1.toInt())
            dos.write(iv)
            dos.writeInt(encryptedPayload.size)
            dos.write(encryptedPayload)

            dos.flush()
            fos.fd.sync()
        }

        // Commit the temporary file.
        if (!tmpFile.renameTo(targetFile)) {
            // Fallback replacement.
            if (targetFile.exists() && !targetFile.delete()) {
                throw StorageException(
                    "Failed to replace existing master key envelope"
                )
            }

            if (!tmpFile.renameTo(targetFile)) {
                throw StorageException(
                    "Failed to atomically commit master key envelope to storage"
                )
            }
        }

    } catch (e: Exception) {
        tmpFile.delete()

        if (e is StorageException) {
            throw e
        }

        throw StorageException(
            "Failed to write master key envelope: ${e.message}",
            e
        )
    }
}

    override fun getEnvelope(): MasterKeyEnvelope? {
        val file = targetFile
        if (!file.exists()) {
            return null
        }

        val totalLength = file.length()
        if (totalLength < MIN_FILE_SIZE_BYTES) {
            throw CorruptedStorageException("Master key envelope file is truncated (${totalLength} bytes)")
        }

        val fileBytes = try {
            FileInputStream(file).use { it.readBytes() }
        } catch (e: Exception) {
            throw StorageException("Failed to read master key envelope file: ${e.message}", e)
        }

        val dis = DataInputStream(ByteArrayInputStream(fileBytes))

        // 1. Validate Magic Header
        val magic = ByteArray(MAGIC_HEADER.size)
        dis.readFully(magic)
        if (!magic.contentEquals(MAGIC_HEADER)) {
            throw CorruptedStorageException("Invalid envelope magic header: corrupted or unauthenticated file")
        }

        // 2. Validate Storage Version
        val version = dis.readByte()
        if (version != STORAGE_VERSION_1) {
            throw CorruptedStorageException("Unsupported envelope storage version: $version")
        }

        // 3. Read IV
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        dis.readFully(iv)

        // 4. Read Payload Length
        val payloadLength = dis.readInt()
        if (payloadLength <= 0 || payloadLength > MAX_PAYLOAD_SIZE_BYTES) {
            throw CorruptedStorageException("Invalid envelope payload length: $payloadLength")
        }

        val ciphertextAndTag = ByteArray(payloadLength)
        dis.readFully(ciphertextAndTag)

        // 5. Decrypt using Android Keystore SecretKey
        val secretKey = getSecretKey()
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)

        val decryptedPlaintext = try {
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
            cipher.updateAAD(AAD_V1)
            cipher.doFinal(ciphertextAndTag)
        } catch (e: AEADBadTagException) {
            throw CorruptedStorageException("Master key envelope authentication failed: data tampered or wrong key", e)
        } catch (e: Exception) {
            throw CorruptedStorageException("Failed to decrypt master key envelope: ${e.message}", e)
        }

        // 6. Deserialize and validate envelope fields
        try {
            return deserializeEnvelope(decryptedPlaintext)
        } finally {
            MemoryZeroizer.zeroize(decryptedPlaintext)
        }
    }

    override fun hasMasterPassword(): Boolean {
        val file = targetFile
        if (!file.exists()) {
            return false
        }
        // Verify file is not corrupt or empty
        if (file.length() < MIN_FILE_SIZE_BYTES) {
            throw CorruptedStorageException("Master key envelope file is corrupt or truncated")
        }
        return true
    }

    override fun clear() {
        if (targetFile.exists()) {
            targetFile.delete()
        }
        if (tmpFile.exists()) {
            tmpFile.delete()
        }
    }

    private fun serializeEnvelope(envelope: MasterKeyEnvelope): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)

        dos.writeInt(envelope.salt.size)
        dos.write(envelope.salt)

        dos.writeInt(envelope.iv.size)
        dos.write(envelope.iv)

        dos.writeInt(envelope.wrappedKey.size)
        dos.write(envelope.wrappedKey)

        dos.writeInt(envelope.iterations)
        dos.writeInt(envelope.memoryKiB)
        dos.writeInt(envelope.parallelism)

        dos.flush()
        return baos.toByteArray()
    }

    private fun deserializeEnvelope(bytes: ByteArray): MasterKeyEnvelope {
        val dis = DataInputStream(ByteArrayInputStream(bytes))

        val saltLength = dis.readInt()
        if (saltLength !in 16..128) {
            throw CorruptedStorageException("Invalid envelope salt length: $saltLength")
        }
        val salt = ByteArray(saltLength)
        dis.readFully(salt)

        val ivLength = dis.readInt()
        if (ivLength != 12) {
            throw CorruptedStorageException("Invalid envelope IV length: $ivLength (expected 12)")
        }
        val iv = ByteArray(ivLength)
        dis.readFully(iv)

        val wrappedKeyLength = dis.readInt()
        if (wrappedKeyLength !in 16..256) {
            throw CorruptedStorageException("Invalid envelope wrappedKey length: $wrappedKeyLength")
        }
        val wrappedKey = ByteArray(wrappedKeyLength)
        dis.readFully(wrappedKey)

        val iterations = dis.readInt()
        if (iterations !in 1..10_000_000) {
            throw CorruptedStorageException("Invalid envelope iterations parameter: $iterations")
        }

        val memoryKiB = dis.readInt()
        if (memoryKiB !in 1024..2_097_152) { // 1 MiB to 2 GiB
            throw CorruptedStorageException("Invalid envelope memoryKiB parameter: $memoryKiB")
        }

        val parallelism = dis.readInt()
        if (parallelism !in 1..256) {
            throw CorruptedStorageException("Invalid envelope parallelism parameter: $parallelism")
        }

        return MasterKeyEnvelope(
            salt = salt,
            iv = iv,
            wrappedKey = wrappedKey,
            iterations = iterations,
            memoryKiB = memoryKiB,
            parallelism = parallelism
        )
    }

    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val DEFAULT_STORAGE_FILE = "vault_master.env"
        const val DEFAULT_KEYSTORE_ALIAS = "vaultzero_master_envelope_key"
        const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
        const val STORAGE_VERSION_1: Byte = 0x01
        const val MIN_FILE_SIZE_BYTES = 5 + 1 + 12 + 4 + 16 // Header + Nonce + Length + Min Tag = 38 bytes
        const val MAX_PAYLOAD_SIZE_BYTES = 64 * 1024 // 64 KB limit for envelope payload

        val MAGIC_HEADER = byteArrayOf(0x56, 0x30, 0x4D, 0x4B, 0x45) // "V0MKE"
        val AAD_V1: ByteArray = "V0_MASTER_ENV_v1".toByteArray(StandardCharsets.UTF_8)
    }
}
