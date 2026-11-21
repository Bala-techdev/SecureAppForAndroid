package com.balatechdev.vaultzero.security.crypto

import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * DeviceKeyProvider
 *
 * Interface for hardware-backed device-bound key protection.
 * On Android, this binds to the Android Keystore (TEE / StrongBox).
 * In test or fallback environments, provides an isolated software implementation.
 */
interface DeviceKeyProvider {
    val alias: String
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray? = null): EncryptedPayload
    fun decrypt(payload: EncryptedPayload, associatedData: ByteArray? = null): ByteArray
    fun isHardwareBacked(): Boolean
    fun rotateKey()
    fun deleteKey()
}

/**
 * SoftwareDeviceKeyProvider
 *
 * Isolated device key provider used when running in JVM environments or tests
 * where the AndroidKeyStore SPI is not available.
 */
class SoftwareDeviceKeyProvider(
    override val alias: String = "vaultzero_device_master_key",
    private val secureRandom: SecureRandom = SecureRandom()
) : DeviceKeyProvider {

    private val encryptionService: EncryptionService = EncryptionServiceImpl(secureRandom)
    private val decryptionService: DecryptionService = DecryptionServiceImpl()
    private var rawKey: ByteArray = generateRawKey()

    private fun generateRawKey(): ByteArray {
        val key = ByteArray(32)
        secureRandom.nextBytes(key)
        return key
    }

    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray?): EncryptedPayload {
        return encryptionService.encrypt(plaintext, rawKey, associatedData)
    }

    override fun decrypt(payload: EncryptedPayload, associatedData: ByteArray?): ByteArray {
        return decryptionService.decrypt(payload, rawKey, associatedData)
    }

    override fun isHardwareBacked(): Boolean = false

    override fun rotateKey() {
        rawKey = generateRawKey()
    }

    override fun deleteKey() {
        rawKey = ByteArray(32)
    }
}

/**
 * AndroidKeystoreDeviceKeyProvider
 *
 * Production hardware-backed key provider using the Android KeyStore provider.
 * Protects a 256-bit AES key in hardware TEE / StrongBox.
 */
class AndroidKeystoreDeviceKeyProvider(
    override val alias: String = "vaultzero_keystore_device_key",
    private val secureRandom: SecureRandom = SecureRandom()
) : DeviceKeyProvider {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    private val encryptionService: EncryptionService = EncryptionServiceImpl(secureRandom)
    private val decryptionService: DecryptionService = DecryptionServiceImpl()

    init {
        ensureKeyExists()
    }

    private fun ensureKeyExists() {
        if (!keyStore.containsAlias(alias)) {
            generateKeystoreKey()
        }
    }

    private fun generateKeystoreKey() {
        try {
            // Reflection allows compiling on JVM while functioning on Android runtime
            val keyGenClass = Class.forName("android.security.keystore.KeyGenParameterSpec\$Builder")
            val keyPropertiesClass = Class.forName("android.security.keystore.KeyProperties")

            val purposeEncrypt = keyPropertiesClass.getField("PURPOSE_ENCRYPT").getInt(null)
            val purposeDecrypt = keyPropertiesClass.getField("PURPOSE_DECRYPT").getInt(null)
            val blockModeGcm = keyPropertiesClass.getField("BLOCK_MODE_GCM").get(null) as String
            val paddingNone = keyPropertiesClass.getField("ENCRYPTION_PADDING_NONE").get(null) as String

            val constructor = keyGenClass.getConstructor(String::class.java, Int::class.javaPrimitiveType)
            val builder = constructor.newInstance(alias, purposeEncrypt or purposeDecrypt)

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
        } catch (e: ClassNotFoundException) {
            // AndroidKeyStore unavailable in this environment
        } catch (e: Exception) {
            throw CryptoException("Failed to generate Android Keystore key: ${e.message}", e)
        }
    }

    private fun getSecretKey(): SecretKey {
        return (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: throw CryptoException("SecretKey for alias '$alias' not found in Android KeyStore")
    }

    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray?): EncryptedPayload {
        val key = getSecretKey()
        try {
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key)
            val nonce = cipher.iv ?: ByteArray(12).also { secureRandom.nextBytes(it) }
            if (associatedData != null && associatedData.isNotEmpty()) {
                cipher.updateAAD(associatedData)
            }
            val ciphertextAndTag = cipher.doFinal(plaintext)
            return EncryptedPayload(nonce = nonce, ciphertextAndTag = ciphertextAndTag)
        } catch (e: Exception) {
            throw CryptoException("Android Keystore encryption failed: ${e.message}", e)
        }
    }

    override fun decrypt(payload: EncryptedPayload, associatedData: ByteArray?): ByteArray {
        val key = getSecretKey()
        try {
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            val spec = javax.crypto.spec.GCMParameterSpec(128, payload.nonce)
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key, spec)
            if (associatedData != null && associatedData.isNotEmpty()) {
                cipher.updateAAD(associatedData)
            }
            return cipher.doFinal(payload.ciphertextAndTag)
        } catch (e: javax.crypto.AEADBadTagException) {
            throw CryptoAuthException("Android Keystore decryption tag mismatch or data corrupted", e)
        } catch (e: Exception) {
            throw CryptoException("Android Keystore decryption failed: ${e.message}", e)
        }
    }

    override fun isHardwareBacked(): Boolean {
        return try {
            val key = getSecretKey()
            val factory = javax.crypto.SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE)
            val keyInfoClass = Class.forName("android.security.keystore.KeyInfo")
            val keyInfo = factory.getKeySpec(key, keyInfoClass as Class<out java.security.spec.KeySpec>)
            val isInsideSecureHardwareMethod = keyInfoClass.getMethod("isInsideSecureHardware")
            isInsideSecureHardwareMethod.invoke(keyInfo) as Boolean
        } catch (e: Exception) {
            false
        }
    }

    override fun rotateKey() {
        deleteKey()
        generateKeystoreKey()
    }

    override fun deleteKey() {
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
    }

    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"

        fun isKeystoreAvailable(): Boolean {
            return try {
                KeyStore.getInstance(ANDROID_KEYSTORE) != null
            } catch (e: Exception) {
                false
            }
        }
    }
}
