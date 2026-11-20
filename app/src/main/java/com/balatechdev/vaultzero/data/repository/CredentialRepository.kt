package com.balatechdev.vaultzero.data.repository

import com.balatechdev.vaultzero.data.database.CredentialDao
import com.balatechdev.vaultzero.data.database.EncryptedCredentialDatabase
import com.balatechdev.vaultzero.data.model.CredentialEntity
import com.balatechdev.vaultzero.data.model.CredentialRecord
import com.balatechdev.vaultzero.data.model.PasswordStrength
import com.balatechdev.vaultzero.security.MemoryZeroizer
import com.balatechdev.vaultzero.security.auth.MasterPasswordValidator
import com.balatechdev.vaultzero.security.crypto.DecryptionService
import com.balatechdev.vaultzero.security.crypto.EncryptionService
import com.balatechdev.vaultzero.security.crypto.VaultKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets

/**
 * CredentialRepository
 *
 * Repository pattern interface for Credential Vault operations.
 */
interface CredentialRepository {
    val credentialsFlow: StateFlow<List<CredentialRecord>>

    fun loadAll(): List<CredentialRecord>
    fun addCredential(record: CredentialRecord): Result<CredentialRecord>
    fun updateCredential(record: CredentialRecord): Result<Unit>
    fun deleteCredential(id: String): Result<Unit>
    fun getCredentialById(id: String): CredentialRecord?
    fun searchCredentials(query: String, categoryId: String? = null): List<CredentialRecord>
    fun toggleFavorite(id: String): Result<Boolean>
    fun getFavorites(): List<CredentialRecord>
    fun lock()
}

/**
 * CredentialRepositoryImpl
 *
 * Concrete repository implementation:
 * - Encrypts sensitive fields (password, notes, totp) using AES-256-GCM.
 * - Decrypts fields into memory only while the vault session is active.
 * - Disallows sensitive logging.
 * - Zeroizes decrypted buffers upon vault locking.
 */
class CredentialRepositoryImpl(
    private val database: EncryptedCredentialDatabase,
    private val keyProvider: () -> ByteArray?,
    private val encryptionService: EncryptionService,
    private val decryptionService: DecryptionService
) : CredentialRepository {

    private val dao: CredentialDao = database.credentialDao

    private val _credentialsFlow = MutableStateFlow<List<CredentialRecord>>(emptyList())
    override val credentialsFlow: StateFlow<List<CredentialRecord>> = _credentialsFlow.asStateFlow()

    private fun requireKey(): ByteArray {
        return keyProvider() ?: throw IllegalStateException("Vault is locked: database encryption key is unavailable")
    }

    override fun loadAll(): List<CredentialRecord> {
        val key = requireKey()
        val entities = dao.getAll()
        val records = entities.mapNotNull { decryptEntity(it, key) }
        _credentialsFlow.value = records
        return records
    }

    override fun addCredential(record: CredentialRecord): Result<CredentialRecord> {
        return runCatching {
            val key = requireKey()
            val entity = encryptRecord(record, key)
            dao.insert(entity)
            loadAll()
            record
        }
    }

    override fun updateCredential(record: CredentialRecord): Result<Unit> {
        return runCatching {
            val key = requireKey()
            val updatedRecord = record.copy(updatedDate = System.currentTimeMillis())
            val entity = encryptRecord(updatedRecord, key)
            val rows = dao.update(entity)
            if (rows == 0) {
                throw NoSuchElementException("Credential with id '${record.id}' not found")
            }
            loadAll()
        }
    }

    override fun deleteCredential(id: String): Result<Unit> {
        return runCatching {
            val rows = dao.deleteById(id)
            if (rows == 0) {
                throw NoSuchElementException("Credential with id '$id' not found")
            }
            loadAll()
        }
    }

    override fun getCredentialById(id: String): CredentialRecord? {
        val key = requireKey()
        val entity = dao.getById(id) ?: return null
        return decryptEntity(entity, key)
    }

    override fun searchCredentials(query: String, categoryId: String?): List<CredentialRecord> {
        val key = requireKey()
        val entities = dao.search(query, categoryId)
        return entities.mapNotNull { decryptEntity(it, key) }
    }

    override fun toggleFavorite(id: String): Result<Boolean> {
        return runCatching {
            val key = requireKey()
            val entity = dao.getById(id) ?: throw NoSuchElementException("Credential with id '$id' not found")
            val newFav = !entity.isFavorite
            val updated = entity.copy(isFavorite = newFav, updatedDate = System.currentTimeMillis())
            dao.update(updated)
            loadAll()
            newFav
        }
    }

    override fun getFavorites(): List<CredentialRecord> {
        val key = requireKey()
        val entities = dao.getFavorites()
        return entities.mapNotNull { decryptEntity(it, key) }
    }

    override fun lock() {
        // Zeroize in-memory credentials flow
        _credentialsFlow.value.forEach { it.close() }
        _credentialsFlow.value = emptyList()
        database.close()
    }

    private fun encryptRecord(record: CredentialRecord, key: ByteArray): CredentialEntity {
        val passwordBytes = record.passwordString.toByteArray(StandardCharsets.UTF_8)
        val encryptedPassword = try {
            encryptionService.encryptToBytes(passwordBytes, key, record.id.toByteArray())
        } finally {
            MemoryZeroizer.zeroize(passwordBytes)
        }

        val encryptedNotes = if (record.notes.isNotEmpty()) {
            encryptionService.encryptToBytes(record.notes.toByteArray(StandardCharsets.UTF_8), key, record.id.toByteArray())
        } else null

        val encryptedTotp = if (record.totpSecret != null && record.totpSecret.isNotEmpty()) {
            encryptionService.encryptToBytes(record.totpSecret.toByteArray(StandardCharsets.UTF_8), key, record.id.toByteArray())
        } else null

        return CredentialEntity(
            id = record.id,
            title = record.title,
            username = record.username,
            encryptedPassword = encryptedPassword,
            encryptedNotes = encryptedNotes,
            encryptedTotpSecret = encryptedTotp,
            website = record.website,
            categoryId = record.categoryId,
            isFavorite = record.isFavorite,
            createdDate = record.createdDate,
            updatedDate = record.updatedDate
        )
    }

    private fun decryptEntity(entity: CredentialEntity, key: ByteArray): CredentialRecord? {
        return try {
            val passwordBytes = decryptionService.decrypt(entity.encryptedPassword, key, entity.id.toByteArray())
            val passwordChars = String(passwordBytes, StandardCharsets.UTF_8).toCharArray()
            MemoryZeroizer.zeroize(passwordBytes)

            val notes = if (entity.encryptedNotes != null) {
                String(decryptionService.decrypt(entity.encryptedNotes, key, entity.id.toByteArray()), StandardCharsets.UTF_8)
            } else ""

            val totpSecret = if (entity.encryptedTotpSecret != null) {
                String(decryptionService.decrypt(entity.encryptedTotpSecret, key, entity.id.toByteArray()), StandardCharsets.UTF_8)
            } else null

            val strength = MasterPasswordValidator.validate(passwordChars).strength

            CredentialRecord(
                id = entity.id,
                title = entity.title,
                username = entity.username,
                passwordChars = passwordChars,
                website = entity.website,
                notes = notes,
                totpSecret = totpSecret,
                categoryId = entity.categoryId,
                isFavorite = entity.isFavorite,
                createdDate = entity.createdDate,
                updatedDate = entity.updatedDate,
                passwordStrength = strength
            )
        } catch (e: Exception) {
            // Tampered or invalid key
            null
        }
    }
}
