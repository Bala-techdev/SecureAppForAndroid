package com.balatechdev.vaultzero.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.balatechdev.vaultzero.data.database.EncryptedDocumentDatabase
import com.balatechdev.vaultzero.data.model.DocumentCategory
import com.balatechdev.vaultzero.data.model.DocumentEntity
import com.balatechdev.vaultzero.security.crypto.CryptoAuthException
import com.balatechdev.vaultzero.security.crypto.CryptoException
import com.balatechdev.vaultzero.security.crypto.DocumentCryptoEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.UUID

interface DocumentRepository {
    val documentsFlow: StateFlow<List<DocumentEntity>>

    fun importDocument(
        sourceStream: InputStream,
        title: String,
        fileName: String,
        categoryId: String = DocumentCategory.OTHER.id,
        notes: String = "",
        masterKey: ByteArray
    ): DocumentEntity

    fun importFromUri(
        context: Context,
        uri: Uri,
        title: String,
        categoryId: String = DocumentCategory.OTHER.id,
        notes: String = "",
        masterKey: ByteArray
    ): DocumentEntity

    fun decryptDocumentToBytes(documentId: String, masterKey: ByteArray): ByteArray

    fun decryptDocumentToTempFile(documentId: String, masterKey: ByteArray, tempDir: File): File

    fun renameDocument(documentId: String, newTitle: String): Boolean

    fun updateCategory(documentId: String, newCategoryId: String): Boolean

    fun updateDocument(entity: DocumentEntity): Boolean

    fun deleteDocument(documentId: String): Boolean

    fun getDocumentById(id: String): DocumentEntity?

    fun getAllDocuments(): List<DocumentEntity>

    fun searchDocuments(query: String, categoryId: String? = null): List<DocumentEntity>

    fun cleanupOrphanedImports(): Int

    fun cleanTempFiles(tempDir: File): Int
}

class DocumentRepositoryImpl(
    private val database: EncryptedDocumentDatabase,
    private val storageDir: File,
    private val keyProvider: () -> ByteArray?
) : DocumentRepository {

    private val _documentsFlow = MutableStateFlow<List<DocumentEntity>>(emptyList())
    override val documentsFlow: StateFlow<List<DocumentEntity>> = _documentsFlow.asStateFlow()

    init {
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        cleanupOrphanedImports()
        refreshFlow()
    }

    private fun refreshFlow() {
        if (database.isOpen()) {
            _documentsFlow.value = database.documentDao.getAll()
        }
    }

    private fun resolveKey(masterKey: ByteArray?): ByteArray {
        val key = masterKey ?: keyProvider()
        return requireNotNull(key) { "Vault is locked or encryption key is unavailable" }
    }

    override fun importDocument(
        sourceStream: InputStream,
        title: String,
        fileName: String,
        categoryId: String,
        notes: String,
        masterKey: ByteArray
    ): DocumentEntity {
        val key = resolveKey(masterKey)
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
        val mimeType = when (ext) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            else -> "application/octet-stream"
        }

        DocumentCryptoEngine.validateSupportedFileType(ext, mimeType)

        val docId = UUID.randomUUID().toString()
        val encFileName = "$docId.enc"
        val partFile = File(storageDir, "$docId.part")
        val finalEncFile = File(storageDir, encFileName)

        val aad = DocumentCryptoEngine.buildAssociatedData(docId, ext, mimeType)
        var totalPlaintextBytes = 0L

        try {
            FileOutputStream(partFile).use { outStream ->
                totalPlaintextBytes = DocumentCryptoEngine.encryptStream(
                    input = sourceStream,
                    output = outStream,
                    key = key,
                    associatedData = aad
                )
            }

            // Atomic rename to final encrypted file on complete success
            if (!partFile.renameTo(finalEncFile)) {
                // Fallback copy if rename fails across file systems
                partFile.copyTo(finalEncFile, overwrite = true)
                partFile.delete()
            }
        } catch (e: Exception) {
            // Clean incomplete import immediately to handle crashes/aborts
            if (partFile.exists()) {
                partFile.delete()
            }
            if (finalEncFile.exists()) {
                finalEncFile.delete()
            }
            throw e
        }

        val entity = DocumentEntity(
            id = docId,
            title = title.ifBlank { fileName },
            fileName = fileName,
            fileExtension = ext,
            mimeType = mimeType,
            fileSizeBytes = totalPlaintextBytes,
            categoryId = categoryId,
            encryptedFileName = encFileName,
            isFavorite = false,
            notes = notes,
            createdDate = System.currentTimeMillis(),
            updatedDate = System.currentTimeMillis()
        )

        database.documentDao.insert(entity)
        refreshFlow()
        return entity
    }

    override fun importFromUri(
        context: Context,
        uri: Uri,
        title: String,
        categoryId: String,
        notes: String,
        masterKey: ByteArray
    ): DocumentEntity {
        var resolvedFileName = "document.pdf"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    resolvedFileName = cursor.getString(nameIndex) ?: resolvedFileName
                }
            }
        } catch (e: Exception) {
            // Use fallback name if query fails
        }

        val inStream = context.contentResolver.openInputStream(uri)
            ?: throw CryptoException("Unable to open storage stream for URI: $uri")

        return inStream.use { stream ->
            importDocument(
                sourceStream = stream,
                title = title.ifBlank { resolvedFileName },
                fileName = resolvedFileName,
                categoryId = categoryId,
                notes = notes,
                masterKey = masterKey
            )
        }
    }

    override fun decryptDocumentToBytes(documentId: String, masterKey: ByteArray): ByteArray {
        val key = resolveKey(masterKey)
        val entity = database.documentDao.getById(documentId)
            ?: throw IllegalArgumentException("Document not found with ID: $documentId")

        val encFile = File(storageDir, entity.encryptedFileName)
        if (!encFile.exists()) {
            throw CryptoException("Encrypted document file is missing on storage: ${entity.encryptedFileName}")
        }

        val aad = DocumentCryptoEngine.buildAssociatedData(entity.id, entity.fileExtension, entity.mimeType)
        return DocumentCryptoEngine.decryptToBytes(encFile, key, aad)
    }

    override fun decryptDocumentToTempFile(documentId: String, masterKey: ByteArray, tempDir: File): File {
        val key = resolveKey(masterKey)
        val entity = database.documentDao.getById(documentId)
            ?: throw IllegalArgumentException("Document not found with ID: $documentId")

        val encFile = File(storageDir, entity.encryptedFileName)
        if (!encFile.exists()) {
            throw CryptoException("Encrypted document file is missing on storage: ${entity.encryptedFileName}")
        }

        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }

        val tempFile = File(tempDir, "preview_${entity.id}_${System.currentTimeMillis()}.${entity.fileExtension}")
        val aad = DocumentCryptoEngine.buildAssociatedData(entity.id, entity.fileExtension, entity.mimeType)

        try {
            FileInputStream(encFile).use { inStream ->
                FileOutputStream(tempFile).use { outStream ->
                    DocumentCryptoEngine.decryptStream(inStream, outStream, key, aad)
                }
            }
            return tempFile
        } catch (e: Exception) {
            // Clean partial/corrupt temp file immediately
            DocumentCryptoEngine.secureWipeAndDelete(tempFile)
            throw e
        }
    }

    override fun renameDocument(documentId: String, newTitle: String): Boolean {
        val entity = database.documentDao.getById(documentId) ?: return false
        val updated = entity.copy(
            title = newTitle.trim(),
            updatedDate = System.currentTimeMillis()
        )
        val rows = database.documentDao.update(updated)
        if (rows > 0) {
            refreshFlow()
            return true
        }
        return false
    }

    override fun updateCategory(documentId: String, newCategoryId: String): Boolean {
        val entity = database.documentDao.getById(documentId) ?: return false
        val updated = entity.copy(
            categoryId = newCategoryId,
            updatedDate = System.currentTimeMillis()
        )
        val rows = database.documentDao.update(updated)
        if (rows > 0) {
            refreshFlow()
            return true
        }
        return false
    }

    override fun updateDocument(entity: DocumentEntity): Boolean {
        val rows = database.documentDao.update(entity)
        if (rows > 0) {
            refreshFlow()
            return true
        }
        return false
    }

    override fun deleteDocument(documentId: String): Boolean {
        val entity = database.documentDao.getById(documentId) ?: return false
        val encFile = File(storageDir, entity.encryptedFileName)
        if (encFile.exists()) {
            DocumentCryptoEngine.secureWipeAndDelete(encFile)
        }
        val rows = database.documentDao.deleteById(documentId)
        refreshFlow()
        return rows > 0
    }

    override fun getDocumentById(id: String): DocumentEntity? {
        return database.documentDao.getById(id)
    }

    override fun getAllDocuments(): List<DocumentEntity> {
        return database.documentDao.getAll()
    }

    override fun searchDocuments(query: String, categoryId: String?): List<DocumentEntity> {
        return database.documentDao.search(query, categoryId)
    }

    override fun cleanupOrphanedImports(): Int {
        var cleaned = 0
        storageDir.listFiles()?.forEach { file ->
            if (file.name.endsWith(".part") || file.name.endsWith(".tmp")) {
                if (file.delete()) {
                    cleaned++
                }
            }
        }
        return cleaned
    }

    override fun cleanTempFiles(tempDir: File): Int {
        var cleaned = 0
        if (tempDir.exists()) {
            tempDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("preview_") || file.name.endsWith(".tmp")) {
                    if (DocumentCryptoEngine.secureWipeAndDelete(file)) {
                        cleaned++
                    }
                }
            }
        }
        return cleaned
    }
}
