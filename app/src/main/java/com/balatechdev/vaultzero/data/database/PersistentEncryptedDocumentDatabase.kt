package com.balatechdev.vaultzero.data.database

import com.balatechdev.vaultzero.data.model.DocumentEntity
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

/**
 * PersistentEncryptedDocumentDatabase
 *
 * Production implementation of [EncryptedDocumentDatabase] providing persistent,
 * authenticated storage for document metadata records.
 *
 * Security Architecture:
 * 1. Persistent file container on internal storage (`vaultzero_documents.encdb`).
 * 2. Entire container is encrypted with AES-256-GCM using the 256-bit Database Key (DBK).
 * 3. Unique 12-byte CSPRNG IV generated on every commit.
 * 4. Authenticated Additional Data (`V0_DOC_DB_v1`) binds container integrity.
 * 5. Atomic write via .tmp file and fsync before rename.
 * 6. In-memory data is completely cleared upon vault lock ([close]).
 */
class PersistentEncryptedDocumentDatabase(
    private val databaseFile: File,
    private val keyProvider: () -> ByteArray?,
    private val secureRandom: SecureRandom = SecureRandom()
) : EncryptedDocumentDatabase {

    private val tmpFile = File(databaseFile.parentFile, "${databaseFile.name}.tmp")
    private val isOpen = AtomicBoolean(true)
    private val cache = ConcurrentHashMap<String, DocumentEntity>()
    private var isLoaded = false

    private fun requireKey(): ByteArray {
        val key = keyProvider()
        return requireNotNull(key) { "Vault is locked or database key (DBK) is unavailable" }
    }

    @Synchronized
    private fun ensureLoaded() {
        if (!isOpen.get()) {
            throw IllegalStateException("Document database has been closed and locked")
        }
        if (isLoaded) return

        if (!databaseFile.exists() || databaseFile.length() == 0L) {
            isLoaded = true
            return
        }

        val key = requireKey()
        val fileBytes = FileInputStream(databaseFile).use { it.readBytes() }
        if (fileBytes.size < MIN_CONTAINER_SIZE) {
            throw CorruptedDatabaseException("Document database file is corrupted or truncated")
        }

        val dis = DataInputStream(ByteArrayInputStream(fileBytes))
        val magic = ByteArray(MAGIC_HEADER.size)
        dis.readFully(magic)
        if (!magic.contentEquals(MAGIC_HEADER)) {
            throw CorruptedDatabaseException("Invalid document database magic header")
        }

        val version = dis.readByte()
        if (version != VERSION_1) {
            throw CorruptedDatabaseException("Unsupported document database version: $version")
        }

        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        dis.readFully(iv)

        val payloadLength = dis.readInt()
        if (payloadLength <= 0 || payloadLength > MAX_PAYLOAD_SIZE) {
            throw CorruptedDatabaseException("Invalid document database payload size: $payloadLength")
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
            throw CorruptedDatabaseException("Document database authentication failed: tampered data or wrong key", e)
        } catch (e: Exception) {
            throw CorruptedDatabaseException("Failed to decrypt document database: ${e.message}", e)
        }

        try {
            val pDis = DataInputStream(ByteArrayInputStream(plaintextBytes))
            val count = pDis.readInt()
            cache.clear()
            for (i in 0 until count) {
                val id = pDis.readUTF()
                val title = pDis.readUTF()
                val fileName = pDis.readUTF()
                val fileExtension = pDis.readUTF()
                val mimeType = pDis.readUTF()
                val fileSizeBytes = pDis.readLong()
                val categoryId = pDis.readUTF()
                val encryptedFileName = pDis.readUTF()
                val isFavorite = pDis.readBoolean()
                val notes = pDis.readUTF()
                val createdDate = pDis.readLong()
                val updatedDate = pDis.readLong()

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
            dos.writeUTF(entity.fileName)
            dos.writeUTF(entity.fileExtension)
            dos.writeUTF(entity.mimeType)
            dos.writeLong(entity.fileSizeBytes)
            dos.writeUTF(entity.categoryId)
            dos.writeUTF(entity.encryptedFileName)
            dos.writeBoolean(entity.isFavorite)
            dos.writeUTF(entity.notes)
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
                    throw DatabaseStorageException("Failed to atomically commit document database to disk")
                }
            }
        } catch (e: Exception) {
            tmpFile.delete()
            throw DatabaseStorageException("Failed to write document database: ${e.message}", e)
        }
    }

    override val documentDao: DocumentDao = object : DocumentDao {
        override fun insert(entity: DocumentEntity): Long {
            check(isOpen.get()) { "Database has been locked and closed" }
            ensureLoaded()
            cache[entity.id] = entity
            persistToDisk()
            return 1L
        }

        override fun update(entity: DocumentEntity): Int {
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

        override fun getById(id: String): DocumentEntity? {
            check(isOpen.get()) { "Database has been locked and closed" }
            ensureLoaded()
            return cache[id]
        }

        override fun getAll(): List<DocumentEntity> {
            check(isOpen.get()) { "Database has been locked and closed" }
            ensureLoaded()
            return cache.values.sortedByDescending { it.createdDate }
        }

        override fun search(query: String, categoryId: String?): List<DocumentEntity> {
            check(isOpen.get()) { "Database has been locked and closed" }
            ensureLoaded()
            return cache.values.filter { item ->
                val matchesCategory = categoryId == null || categoryId == "cat_all" || item.categoryId == categoryId
                val matchesQuery = query.isBlank() ||
                        item.title.contains(query, ignoreCase = true) ||
                        item.fileName.contains(query, ignoreCase = true) ||
                        item.notes.contains(query, ignoreCase = true)
                matchesCategory && matchesQuery
            }.sortedByDescending { it.createdDate }
        }

        override fun getFavorites(): List<DocumentEntity> {
            check(isOpen.get()) { "Database has been locked and closed" }
            ensureLoaded()
            return cache.values.filter { it.isFavorite }.sortedByDescending { it.createdDate }
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
        const val MAX_PAYLOAD_SIZE = 25 * 1024 * 1024 // 25 MB max documents database

        val MAGIC_HEADER = byteArrayOf(0x56, 0x30, 0x44, 0x44, 0x42) // "V0DDB"
        val AAD_V1: ByteArray = "V0_DOC_DB_v1".toByteArray(StandardCharsets.UTF_8)
    }
}
