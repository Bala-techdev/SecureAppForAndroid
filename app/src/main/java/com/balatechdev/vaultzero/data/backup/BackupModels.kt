package com.balatechdev.vaultzero.data.backup

import com.balatechdev.vaultzero.data.model.CredentialEntity
import com.balatechdev.vaultzero.data.model.DocumentEntity
import com.balatechdev.vaultzero.security.auth.MasterKeyEnvelope
import com.balatechdev.vaultzero.security.recovery.RecoveryKeyEnvelope
import java.security.MessageDigest

/**
 * BackupManifest
 *
 * Header metadata describing the archive content and cryptographic integrity info.
 */
data class BackupManifest(
    val formatVersion: Int = CURRENT_VERSION,
    val appVersion: String = "1.0.0",
    val createdAtTimestamp: Long = System.currentTimeMillis(),
    val credentialCount: Int,
    val documentCount: Int,
    val checksumSha256: String = ""
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

/**
 * EncryptedDocumentBackupEntry
 *
 * Encapsulates the database metadata entity and the raw AES-256-GCM encrypted file payload.
 * Plaintext files are NEVER stored in the backup.
 */
data class EncryptedDocumentBackupEntry(
    val entity: DocumentEntity,
    val encryptedFileBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncryptedDocumentBackupEntry
        if (entity != other.entity) return false
        if (!encryptedFileBytes.contentEquals(other.encryptedFileBytes)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entity.hashCode()
        result = 31 * result + encryptedFileBytes.contentHashCode()
        return result
    }
}

/**
 * VaultBackupPackage
 *
 * In-memory representation of the complete encrypted vault archive.
 * All credentials and document files are already encrypted under their respective keys.
 */
data class VaultBackupPackage(
    val manifest: BackupManifest,
    val masterKeyEnvelope: MasterKeyEnvelope?,
    val recoveryEnvelope: RecoveryKeyEnvelope?,
    val credentials: List<CredentialEntity>,
    val documents: List<EncryptedDocumentBackupEntry>
) {
    fun calculateChecksum(): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update("VaultZero_Backup_v${manifest.formatVersion}".toByteArray())
        md.update("${manifest.createdAtTimestamp}_${credentials.size}_${documents.size}".toByteArray())
        masterKeyEnvelope?.let { md.update(it.wrappedKey) }
        recoveryEnvelope?.let { md.update(it.wrappedKey) }
        for (c in credentials) {
            md.update(c.id.toByteArray())
            md.update(c.encryptedPassword)
        }
        for (d in documents) {
            md.update(d.entity.id.toByteArray())
            md.update(d.encryptedFileBytes)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
