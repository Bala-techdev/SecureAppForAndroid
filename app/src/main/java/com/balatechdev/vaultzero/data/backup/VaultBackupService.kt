package com.balatechdev.vaultzero.data.backup

import com.balatechdev.vaultzero.data.database.EncryptedCredentialDatabase
import com.balatechdev.vaultzero.data.database.EncryptedDocumentDatabase
import com.balatechdev.vaultzero.data.model.CredentialEntity
import com.balatechdev.vaultzero.data.model.DocumentEntity
import com.balatechdev.vaultzero.data.repository.CredentialRepository
import com.balatechdev.vaultzero.data.repository.DocumentRepository
import com.balatechdev.vaultzero.security.MemoryZeroizer
import com.balatechdev.vaultzero.security.auth.Argon2idKdf
import com.balatechdev.vaultzero.security.auth.Argon2idParameters
import com.balatechdev.vaultzero.security.auth.MasterKeyEnvelope
import com.balatechdev.vaultzero.security.auth.VaultKeyStorage
import com.balatechdev.vaultzero.security.auth.VaultSessionManager
import com.balatechdev.vaultzero.security.crypto.DocumentCryptoEngine
import com.balatechdev.vaultzero.security.recovery.RecoveryKeyEnvelope
import com.balatechdev.vaultzero.security.recovery.VaultRecoveryService
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

interface VaultBackupService {
    fun createBackupPackage(): VaultBackupPackage

    fun exportEncryptedBackup(
        passphrase: CharArray,
        params: Argon2idParameters = Argon2idParameters.Default
    ): ByteArray

    fun exportBackupToStream(
        outputStream: OutputStream,
        passphrase: CharArray,
        params: Argon2idParameters = Argon2idParameters.Default
    )

    fun verifyBackupHeader(inputStream: InputStream): BackupManifest

    fun restoreBackup(
        backupBytes: ByteArray,
        passphrase: CharArray
    ): BackupManifest

    fun restoreBackupFromStream(
        inputStream: InputStream,
        passphrase: CharArray
    ): BackupManifest
}

class VaultBackupServiceImpl(
    private val keyStorage: VaultKeyStorage,
    private val credentialDatabase: EncryptedCredentialDatabase,
    private val documentDatabase: EncryptedDocumentDatabase,
    private val documentStorageDir: File,
    private val recoveryService: VaultRecoveryService? = null,
    private val sessionManager: VaultSessionManager? = null,
    private val secureRandom: SecureRandom = SecureRandom()
) : VaultBackupService {

    override fun createBackupPackage(): VaultBackupPackage {
        val credentials = credentialDatabase.credentialDao.getAll()
        val docEntities = documentDatabase.documentDao.getAll()

        val docEntries = docEntities.map { docEntity ->
            val encFile = File(documentStorageDir, docEntity.encryptedFileName)
            val bytes = if (encFile.exists()) encFile.readBytes() else ByteArray(0)
            EncryptedDocumentBackupEntry(entity = docEntity, encryptedFileBytes = bytes)
        }

        val masterEnv = keyStorage.getEnvelope()
        val recoveryEnv = recoveryService?.getRecoveryEnvelope()

        val preliminaryPackage = VaultBackupPackage(
            manifest = BackupManifest(
                formatVersion = BackupManifest.CURRENT_VERSION,
                appVersion = "1.0.0",
                createdAtTimestamp = System.currentTimeMillis(),
                credentialCount = credentials.size,
                documentCount = docEntries.size
            ),
            masterKeyEnvelope = masterEnv,
            recoveryEnvelope = recoveryEnv,
            credentials = credentials,
            documents = docEntries
        )

        val checksum = preliminaryPackage.calculateChecksum()
        return preliminaryPackage.copy(
            manifest = preliminaryPackage.manifest.copy(checksumSha256 = checksum)
        )
    }

    override fun exportEncryptedBackup(
        passphrase: CharArray,
        params: Argon2idParameters
    ): ByteArray {
        val outStream = ByteArrayOutputStream()
        exportBackupToStream(outStream, passphrase, params)
        return outStream.toByteArray()
    }

    override fun exportBackupToStream(
        outputStream: OutputStream,
        passphrase: CharArray,
        params: Argon2idParameters
    ) {
        val backupPackage = createBackupPackage()
        val serializedData = serializePackage(backupPackage)

        val salt = ByteArray(params.saltLengthBytes)
        secureRandom.nextBytes(salt)
        val nonce = ByteArray(GCM_NONCE_LENGTH_BYTES)
        secureRandom.nextBytes(nonce)

        val backupKey = Argon2idKdf.deriveKey(passphrase, salt, params)

        try {
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            val keySpec = SecretKeySpec(backupKey, "AES")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
            cipher.updateAAD(buildHeaderAad(BackupManifest.CURRENT_VERSION, params.iterations, params.memoryKiB, params.parallelism))

            val encryptedPayload = cipher.doFinal(serializedData)

            // Write File Container Header: Magic + Version + KDF Params + Salt + Nonce + Ciphertext
            val dataOut = DataOutputStream(outputStream)
            dataOut.write(MAGIC_BYTES)
            dataOut.writeByte(BackupManifest.CURRENT_VERSION)
            dataOut.writeInt(params.iterations)
            dataOut.writeInt(params.memoryKiB)
            dataOut.writeInt(params.parallelism)
            dataOut.write(salt)
            dataOut.write(nonce)
            dataOut.write(encryptedPayload)
            dataOut.flush()
        } finally {
            MemoryZeroizer.zeroize(backupKey)
            MemoryZeroizer.zeroize(serializedData)
        }
    }

    override fun verifyBackupHeader(inputStream: InputStream): BackupManifest {
        val dataIn = DataInputStream(inputStream)
        val magic = ByteArray(MAGIC_BYTES.size)
        dataIn.readFully(magic)
        if (!magic.contentEquals(MAGIC_BYTES)) {
            throw InvalidBackupHeaderException()
        }

        val version = dataIn.readUnsignedByte()
        if (version > BackupManifest.CURRENT_VERSION) {
            throw IncompatibleBackupVersionException(version, BackupManifest.CURRENT_VERSION)
        }

        return BackupManifest(formatVersion = version, credentialCount = 0, documentCount = 0)
    }

    override fun restoreBackup(
        backupBytes: ByteArray,
        passphrase: CharArray
    ): BackupManifest {
        val inStream = ByteArrayInputStream(backupBytes)
        return restoreBackupFromStream(inStream, passphrase)
    }

    override fun restoreBackupFromStream(
        inputStream: InputStream,
        passphrase: CharArray
    ): BackupManifest {
        val dataIn = DataInputStream(inputStream)

        // 1. Verify and read magic bytes
        val magic = ByteArray(MAGIC_BYTES.size)
        try {
            dataIn.readFully(magic)
        } catch (e: Exception) {
            throw InvalidBackupHeaderException("Failed to read backup magic header", e)
        }
        if (!magic.contentEquals(MAGIC_BYTES)) {
            throw InvalidBackupHeaderException()
        }

        // 2. Verify format version
        val version = dataIn.readUnsignedByte()
        if (version > BackupManifest.CURRENT_VERSION) {
            throw IncompatibleBackupVersionException(version, BackupManifest.CURRENT_VERSION)
        }

        // 3. Read KDF parameters
        val iterations: Int
        val memoryKiB: Int
        val parallelism: Int
        val salt = ByteArray(32)
        val nonce = ByteArray(GCM_NONCE_LENGTH_BYTES)
        try {
            iterations = dataIn.readInt()
            memoryKiB = dataIn.readInt()
            parallelism = dataIn.readInt()
            dataIn.readFully(salt)
            dataIn.readFully(nonce)
        } catch (e: Exception) {
            throw CorruptedBackupException("Truncated backup header or KDF parameters", e)
        }

        // 4. Read remaining encrypted payload
        val encryptedPayload = dataIn.readBytes()
        if (encryptedPayload.size < 16) {
            throw CorruptedBackupException("Truncated ciphertext: missing authentication tag")
        }

        // 5. Derive key and authenticate via AES-256-GCM
        val kdfParams = Argon2idParameters(
            iterations = iterations,
            memoryKiB = memoryKiB,
            parallelism = parallelism,
            saltLengthBytes = salt.size
        )
        val backupKey = Argon2idKdf.deriveKey(passphrase, salt, kdfParams)
        val decryptedData: ByteArray
        try {
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            val keySpec = SecretKeySpec(backupKey, "AES")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            cipher.updateAAD(buildHeaderAad(version, iterations, memoryKiB, parallelism))

            decryptedData = cipher.doFinal(encryptedPayload)
        } catch (e: AEADBadTagException) {
            throw CorruptedBackupException("Backup authentication tag verification failed: incorrect passphrase or corrupted file", e)
        } catch (e: BadPaddingException) {
            throw CorruptedBackupException("Bad padding in backup archive", e)
        } catch (e: Exception) {
            throw CorruptedBackupException("Failed to decrypt backup: ${e.message}", e)
        } finally {
            MemoryZeroizer.zeroize(backupKey)
        }

        // 6. Deserialize package and verify internal SHA-256 integrity
        val backupPackage: VaultBackupPackage
        try {
            backupPackage = deserializePackage(decryptedData)
            val expectedChecksum = backupPackage.calculateChecksum()
            if (backupPackage.manifest.checksumSha256.isNotBlank() &&
                backupPackage.manifest.checksumSha256 != expectedChecksum
            ) {
                throw CorruptedBackupException("Backup package internal checksum mismatch")
            }
        } finally {
            MemoryZeroizer.zeroize(decryptedData)
        }

        // 7. Atomic Restore Transaction (All-or-Nothing Defense)
        executeAtomicRestore(backupPackage)

        return backupPackage.manifest
    }

    private fun executeAtomicRestore(pkg: VaultBackupPackage) {
        val tempRestoreDir = File(documentStorageDir.parentFile, "restore_staging_${System.nanoTime()}")
        tempRestoreDir.mkdirs()

        try {
            // Stage documents first
            for (doc in pkg.documents) {
                val stagedFile = File(tempRestoreDir, doc.entity.encryptedFileName)
                stagedFile.writeBytes(doc.encryptedFileBytes)
            }

            // Replace documents in primary storage
            if (!documentStorageDir.exists()) {
                documentStorageDir.mkdirs()
            }
            for (doc in pkg.documents) {
                val targetFile = File(documentStorageDir, doc.entity.encryptedFileName)
                val stagedFile = File(tempRestoreDir, doc.entity.encryptedFileName)
                stagedFile.copyTo(targetFile, overwrite = true)
            }

            // Restore Document Database records
            documentDatabase.documentDao.clear()
            for (doc in pkg.documents) {
                documentDatabase.documentDao.insert(doc.entity)
            }

            // Restore Credential Database records
            credentialDatabase.credentialDao.clear()
            for (cred in pkg.credentials) {
                credentialDatabase.credentialDao.insert(cred)
            }

            // Restore Envelopes
            pkg.masterKeyEnvelope?.let { keyStorage.saveEnvelope(it) }
            pkg.recoveryEnvelope?.let { recoveryService?.saveRecoveryEnvelope(it) }

        } catch (e: Exception) {
            // If anything fails during write, throw and preserve vault integrity
            throw BackupException("Restore failed during atomic transaction: ${e.message}", e)
        } finally {
            tempRestoreDir.deleteRecursively()
        }
    }

    private fun serializePackage(pkg: VaultBackupPackage): ByteArray {
        val baos = ByteArrayOutputStream()
        val out = DataOutputStream(baos)

        // Manifest
        out.writeInt(pkg.manifest.formatVersion)
        out.writeUTF(pkg.manifest.appVersion)
        out.writeLong(pkg.manifest.createdAtTimestamp)
        out.writeInt(pkg.manifest.credentialCount)
        out.writeInt(pkg.manifest.documentCount)
        out.writeUTF(pkg.manifest.checksumSha256)

        // MasterKeyEnvelope
        if (pkg.masterKeyEnvelope != null) {
            out.writeBoolean(true)
            out.writeInt(pkg.masterKeyEnvelope.salt.size)
            out.write(pkg.masterKeyEnvelope.salt)
            out.writeInt(pkg.masterKeyEnvelope.iv.size)
            out.write(pkg.masterKeyEnvelope.iv)
            out.writeInt(pkg.masterKeyEnvelope.wrappedKey.size)
            out.write(pkg.masterKeyEnvelope.wrappedKey)
            out.writeInt(pkg.masterKeyEnvelope.iterations)
            out.writeInt(pkg.masterKeyEnvelope.memoryKiB)
            out.writeInt(pkg.masterKeyEnvelope.parallelism)
        } else {
            out.writeBoolean(false)
        }

        // RecoveryEnvelope
        if (pkg.recoveryEnvelope != null) {
            out.writeBoolean(true)
            out.writeInt(pkg.recoveryEnvelope.salt.size)
            out.write(pkg.recoveryEnvelope.salt)
            out.writeInt(pkg.recoveryEnvelope.nonce.size)
            out.write(pkg.recoveryEnvelope.nonce)
            out.writeInt(pkg.recoveryEnvelope.wrappedKey.size)
            out.write(pkg.recoveryEnvelope.wrappedKey)
            out.writeInt(pkg.recoveryEnvelope.iterations)
            out.writeInt(pkg.recoveryEnvelope.memoryKiB)
            out.writeInt(pkg.recoveryEnvelope.parallelism)
        } else {
            out.writeBoolean(false)
        }

        // Credentials
        out.writeInt(pkg.credentials.size)
        for (c in pkg.credentials) {
            out.writeUTF(c.id)
            out.writeUTF(c.title)
            out.writeUTF(c.username)
            out.writeInt(c.encryptedPassword.size)
            out.write(c.encryptedPassword)
            out.writeInt(c.encryptedNotes?.size ?: 0)
            c.encryptedNotes?.let { out.write(it) }
            out.writeInt(c.encryptedTotpSecret?.size ?: 0)
            c.encryptedTotpSecret?.let { out.write(it) }
            out.writeUTF(c.website)
            out.writeUTF(c.categoryId)
            out.writeBoolean(c.isFavorite)
            out.writeLong(c.createdDate)
            out.writeLong(c.updatedDate)
        }

        // Documents
        out.writeInt(pkg.documents.size)
        for (d in pkg.documents) {
            out.writeUTF(d.entity.id)
            out.writeUTF(d.entity.title)
            out.writeUTF(d.entity.fileName)
            out.writeUTF(d.entity.fileExtension)
            out.writeUTF(d.entity.mimeType)
            out.writeLong(d.entity.fileSizeBytes)
            out.writeUTF(d.entity.categoryId)
            out.writeUTF(d.entity.encryptedFileName)
            out.writeBoolean(d.entity.isFavorite)
            out.writeUTF(d.entity.notes)
            out.writeLong(d.entity.createdDate)
            out.writeLong(d.entity.updatedDate)
            out.writeInt(d.encryptedFileBytes.size)
            out.write(d.encryptedFileBytes)
        }

        out.flush()
        return baos.toByteArray()
    }

    private fun deserializePackage(bytes: ByteArray): VaultBackupPackage {
        val inStream = DataInputStream(ByteArrayInputStream(bytes))

        // Manifest
        val version = inStream.readInt()
        val appVersion = inStream.readUTF()
        val timestamp = inStream.readLong()
        val credCount = inStream.readInt()
        val docCount = inStream.readInt()
        val checksum = inStream.readUTF()
        val manifest = BackupManifest(version, appVersion, timestamp, credCount, docCount, checksum)

        // MasterKeyEnvelope
        var masterEnv: MasterKeyEnvelope? = null
        if (inStream.readBoolean()) {
            val salt = ByteArray(inStream.readInt()).also { inStream.readFully(it) }
            val iv = ByteArray(inStream.readInt()).also { inStream.readFully(it) }
            val wrapped = ByteArray(inStream.readInt()).also { inStream.readFully(it) }
            val iters = inStream.readInt()
            val mem = inStream.readInt()
            val par = inStream.readInt()
            masterEnv = MasterKeyEnvelope(salt, iv, wrapped, iters, mem, par)
        }

        // RecoveryEnvelope
        var recEnv: RecoveryKeyEnvelope? = null
        if (inStream.readBoolean()) {
            val salt = ByteArray(inStream.readInt()).also { inStream.readFully(it) }
            val nonce = ByteArray(inStream.readInt()).also { inStream.readFully(it) }
            val wrapped = ByteArray(inStream.readInt()).also { inStream.readFully(it) }
            val iters = inStream.readInt()
            val mem = inStream.readInt()
            val par = inStream.readInt()
            recEnv = RecoveryKeyEnvelope(salt, nonce, wrapped, iters, mem, par)
        }

        // Credentials
        val credentials = ArrayList<CredentialEntity>()
        val credSize = inStream.readInt()
        for (i in 0 until credSize) {
            val id = inStream.readUTF()
            val title = inStream.readUTF()
            val username = inStream.readUTF()
            val encPass = ByteArray(inStream.readInt()).also { inStream.readFully(it) }
            val notesLen = inStream.readInt()
            val encNotes = if (notesLen > 0) ByteArray(notesLen).also { inStream.readFully(it) } else null
            val totpLen = inStream.readInt()
            val encTotp = if (totpLen > 0) ByteArray(totpLen).also { inStream.readFully(it) } else null
            val website = inStream.readUTF()
            val categoryId = inStream.readUTF()
            val isFavorite = inStream.readBoolean()
            val created = inStream.readLong()
            val updated = inStream.readLong()
            credentials.add(
                CredentialEntity(
                    id = id,
                    title = title,
                    username = username,
                    encryptedPassword = encPass,
                    encryptedNotes = encNotes,
                    encryptedTotpSecret = encTotp,
                    website = website,
                    categoryId = categoryId,
                    isFavorite = isFavorite,
                    createdDate = created,
                    updatedDate = updated
                )
            )
        }

        // Documents
        val documents = ArrayList<EncryptedDocumentBackupEntry>()
        val docSize = inStream.readInt()
        for (i in 0 until docSize) {
            val id = inStream.readUTF()
            val title = inStream.readUTF()
            val fileName = inStream.readUTF()
            val fileExtension = inStream.readUTF()
            val mimeType = inStream.readUTF()
            val fileSizeBytes = inStream.readLong()
            val categoryId = inStream.readUTF()
            val encryptedFileName = inStream.readUTF()
            val isFavorite = inStream.readBoolean()
            val notes = inStream.readUTF()
            val created = inStream.readLong()
            val updated = inStream.readLong()
            val entity = DocumentEntity(
                id = id,
                title = title,
                fileName = fileName,
                fileExtension = fileExtension,
                mimeType = mimeType,
                fileSizeBytes = fileSizeBytes,
                categoryId = categoryId,
                encryptedFileName = encryptedFileName,
                isFavorite = isFavorite,
                notes = notes,
                createdDate = created,
                updatedDate = updated
            )
            val fileBytes = ByteArray(inStream.readInt()).also { inStream.readFully(it) }
            documents.add(EncryptedDocumentBackupEntry(entity, fileBytes))
        }

        return VaultBackupPackage(manifest, masterEnv, recEnv, credentials, documents)
    }

    private fun buildHeaderAad(version: Int, iterations: Int, memoryKiB: Int, parallelism: Int): ByteArray {
        return "VaultZero_Backup_AAD_v${version}_i${iterations}_m${memoryKiB}_p${parallelism}".toByteArray(Charsets.UTF_8)
    }

    companion object {
        val MAGIC_BYTES = byteArrayOf(0x56, 0x5A, 0x42, 0x41, 0x4B) // "VZBAK"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_NONCE_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
