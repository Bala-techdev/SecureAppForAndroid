package com.balatechdev.vaultzero.data.database

import com.balatechdev.vaultzero.data.model.CredentialEntity
import com.balatechdev.vaultzero.security.MemoryZeroizer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

open class DatabaseStorageException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
class CorruptedDatabaseException(message: String, cause: Throwable? = null) : DatabaseStorageException(message, cause)

/**
 * PersistentEncryptedCredentialDatabase
 *
 * Production implementation of [EncryptedCredentialDatabase] providing persistent,
 * authenticated, zero-knowledge storage for credential entities.
 *
 * Security Architecture:
 * 1. Persistent file container on internal storage (`vaultzero_credentials.encdb`).
 * 2. Entire container is encrypted with AES-256-GCM using the 256-bit Database Key (DBK).
 * 3. Individual entity credentials (password, notes, totp) are separately encrypted with AES-256-GCM.
 * 4. Unique 12-byte CSPRNG IV generated on every commit.
 * 5. Authenticated Additional Data (`V0_CRED_DB_v1`) binds container integrity.
 * 6. Atomic write via .tmp file and fsync before rename prevents corruption from power loss.
 * 7. In-memory data is completely cleared upon vault lock ([close]).
 */
class PersistentEncryptedCredentialDatabase(
    private val databaseFile: File,
    private val keyProvider: () -> ByteArray?,
    private val secureRandom: SecureRandom = SecureRandom()
) : EncryptedCredentialDatabase {

    private val tmpFile = File(databaseFile.parentFile, "${databaseFile.name}.tmp")
    private val isOpen = AtomicBoolean(true)
    private val cache = ConcurrentHashMap<String, CredentialEntity>()
    private var isLoaded = false

    private fun requireKey(): ByteArray {
        val key = keyProvider()
        return requireNotNull(key) { "Vault is locked or database key (DBK) is unavailable" }
    }

    @Synchronized
    private fun ensureLoaded() {
        if (!isOpen.get()) {
            throw IllegalStateException("Database has been closed and locked")
        }
        if (isLoaded) return

        if (!databaseFile.exists() || databaseFile.length() == 0L) {
            isLoaded = true
            return
        }

        val key = requireKey()
        val fileBytes = FileInputStream(databaseFile).use { it.readBytes() }
        if (fileBytes.size < MIN_CONTAINER_SIZE) {
            throw CorruptedDatabaseException("Credential database file is corrupted or truncated")
        }

        val dis = DataInputStream(ByteArrayInputStream(fileBytes))
        val magic = ByteArray(MAGIC_HEADER.size)
        dis.readFully(magic)
        if (!magic.contentEquals(MAGIC_HEADER)) {
            throw CorruptedDatabaseException("Invalid credential database magic header")
        }

        val version = dis.readByte()
        if (version != VERSION_1) {
            throw CorruptedDatabaseException("Unsupported credential database version: $version")
        }

        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        dis.readFully(iv)

        val payloadLength = dis.readInt()
        if (payloadLength <= 0 || payloadLength > MAX_PAYLOAD_SIZE) {
            throw CorruptedDatabaseException("Invalid credential database payload size: $payloadLength")
        }

        val ciphertextAndTag = ByteArray(payloadLength)
        dis.readFully(ciphertextAndTag)

        val plaintextBytes: ByteArray
        try {
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            val keySpec = SecretKeySpec(key, "AES")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            cipher.updateAAD(AAD_V1)
            plaintextBytes = cipher.doFinal(ciphertextAndTag)
        } catch (e: AEADBadTagException) {
            throw CorruptedDatabaseException("Credential database authentication failed: tampered data or wrong key", e)
        } catch (e: Exception) {
            throw CorruptedDatabaseException("Failed to decrypt credential database: ${e.message}", e)
        }

        try {
            val pDis = DataInputStream(ByteArrayInputStream(plaintextBytes))
            val count = pDis.readInt()
            cache.clear()
            for (i in 0 until count) {
                val id = pDis.readUTF()
                val title = pDis.readUTF()
                val username = pDis.readUTF()

                val passLen = pDis.readInt()
                val encryptedPass = ByteArray(passLen)
                pDis.readFully(encryptedPass)

                val hasNotes = pDis.readBoolean()
                val encryptedNotes = if (hasNotes) {
                    val nLen = pDis.readInt()
                    val nBytes = ByteArray(nLen)
                    pDis.readFully(nBytes)
                    nBytes
                } else null

                val hasTotp = pDis.readBoolean()
                val encryptedTotp = if (hasTotp) {
                    val tLen = pDis.readInt()
                    val tBytes = ByteArray(tLen)
                    pDis.readFully(tBytes)
                    tBytes
                } else null

                val website = pDis.readUTF()
                val categoryId = pDis.readUTF()
                val isFavorite = pDis.readBoolean()
                val createdDate = pDis.readLong()
                val updatedDate = pDis.readLong()

                val entity = CredentialEntity(
                    id = id,
                    title = title,
                    username = username,
                    encryptedPassword = encryptedPass,
                    encryptedNotes = encryptedNotes,
                    encryptedTotpSecret = encryptedTotp,
                    website = website,
                    categoryId = categoryId,
                    isFavorite = isFavorite,
                    createdDate = createdDate,
                    updatedDate = updatedDate
                )
                cache[id] = entity
            }
            isLoaded = true
        } finally {
            MemoryZeroizer.zeroize(plaintextBytes)
        }
    }

    @Synchronized
    private fun persistToDisk() {
        val key = requireKey()

        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeInt(cache.size)
        for (entity in cache.values) {
            dos.writeUTF(entity.id)
            dos.writeUTF(entity.title)
            dos.writeUTF(entity.username)

            dos.writeInt(entity.encryptedPassword.size)
            dos.write(entity.encryptedPassword)

            if (entity.encryptedNotes != null) {
                dos.writeBoolean(true)
                dos.writeInt(entity.encryptedNotes.size)
                dos.write(entity.encryptedNotes)
            } else {
                dos.writeBoolean(false)
            }

            if (entity.encryptedTotpSecret != null) {
                dos.writeBoolean(true)
                dos.writeInt(entity.encryptedTotpSecret.size)
                dos.write(entity.encryptedTotpSecret)
            } else {
                dos.writeBoolean(false)
            }

            dos.writeUTF(entity.website)
            dos.writeUTF(entity.categoryId)
            dos.writeBoolean(entity.isFavorite)
            dos.writeLong(entity.createdDate)
            dos.writeLong(entity.updatedDate)
        }
        dos.flush()
        val plaintextBytes = baos.toByteArray()

        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val encryptedPayload: ByteArray
        try {
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            val keySpec = SecretKeySpec(key, "AES")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
            cipher.updateAAD(AAD_V1)
            encryptedPayload = cipher.doFinal(plaintextBytes)
        } finally {
            MemoryZeroizer.zeroize(plaintextBytes)
        }

        val parentDir = databaseFile.parentFile
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs()
        }

        try {
            FileOutputStream(tmpFile).use { fos ->
                val fDos = DataOutputStream(fos)
                fDos.write(MAGIC_HEADER)
                fDos.writeByte(VERSION_1.toInt())
                fDos.write(iv)
                fDos.writeInt(encryptedPayload.size)
                fDos.write(encryptedPayload)
                fDos.flush()
                fos.fd.sync()
            }

            if (!tmpFile.renameTo(databaseFile)) {
                databaseFile.delete()
                if (!tmpFile.renameTo(databaseFile)) {
                    throw DatabaseStorageException("Failed to atomically commit credential database to disk")
                }
            }
        } catch (e: Exception) {
            tmpFile.delete()
            throw DatabaseStorageException("Failed to write credential database: ${e.message}", e)
        }
    }

    override val credentialDao: CredentialDao = object : CredentialDao {
        override fun insert(entity: CredentialEntity): Long {
            check(isOpen.get()) { "Database has been locked and closed" }
            ensureLoaded()
            cache[entity.id] = entity
            persistToDisk()
            return 1L
        }

        override fun update(entity: CredentialEntity): Int {
            check(isOpen.get()) { "Database has been locked and closed" }
            ensureLoaded()
            return if (cache.containsKey(entity.id)) {
                cache[entity.id] = entity
                persistToDisk()
                1
            } else {
                0
            }
        }

        override fun deleteById(id: String): Int {
            check(isOpen.get()) { "Database has been locked and closed" }
            ensureLoaded()
            val removed = cache.remove(id)
            return if (removed != null) {
                persistToDisk()
                1
            } else {
                0
            }
        }

        override fun getById(id: String): CredentialEntity? {
            check(isOpen.get()) { "Database has been locked and closed" }
            ensureLoaded()
            return cache[id]
        }

        override fun getAll(): List<CredentialEntity> {
            check(isOpen.get()) { "Database has been locked and closed" }
            ensureLoaded()
            return cache.values.sortedByDescending { it.updatedDate }
        }

        override fun search(query: String, categoryId: String?): List<CredentialEntity> {
            check(isOpen.get()) { "Database has been locked and closed" }
            ensureLoaded()
            return cache.values.filter { item ->
                val matchesCategory = categoryId == null || categoryId == "cat_all" || item.categoryId == categoryId
                val matchesQuery = query.isBlank() ||
                        item.title.contains(query, ignoreCase = true) ||
                        item.username.contains(query, ignoreCase = true) ||
                        item.website.contains(query, ignoreCase = true)
                matchesCategory && matchesQuery
            }.sortedByDescending { it.updatedDate }
        }

        override fun getFavorites(): List<CredentialEntity> {
            check(isOpen.get()) { "Database has been locked and closed" }
            ensureLoaded()
            return cache.values.filter { it.isFavorite }.sortedByDescending { it.updatedDate }
        }

        override fun getCount(): Int {
            check(isOpen.get()) { "Database has been locked and closed" }
            ensureLoaded()
            return cache.size
        }

        override fun clear(): Int {
            check(isOpen.get()) { "Database has been locked and closed" }
            ensureLoaded()
            val count = cache.size
            cache.clear()
            persistToDisk()
            return count
        }
    }

    override fun isOpen(): Boolean = isOpen.get()

    override fun close() {
        if (isOpen.compareAndSet(true, false)) {
            cache.clear()
            isLoaded = false
        }
    }

    companion object {
        const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
        const val VERSION_1: Byte = 0x01
        const val MIN_CONTAINER_SIZE = 5 + 1 + 12 + 4 + 16
        const val MAX_PAYLOAD_SIZE = 25 * 1024 * 1024 // 25 MB max credentials database

        val MAGIC_HEADER = byteArrayOf(0x56, 0x30, 0x43, 0x44, 0x42) // "V0CDB"
        val AAD_V1: ByteArray = "V0_CRED_DB_v1".toByteArray(StandardCharsets.UTF_8)
    }
}
