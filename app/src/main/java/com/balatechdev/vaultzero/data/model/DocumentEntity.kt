package com.balatechdev.vaultzero.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * DocumentCategory
 *
 * Supported document categorization:
 * - Identity (passports, driver's licenses, IDs)
 * - Education (degrees, transcripts, certificates)
 * - Banking (statements, tax returns, contracts)
 * - Personal (medical, insurance, family records)
 * - Other (miscellaneous sensitive files)
 */
enum class DocumentCategory(
    val id: String,
    val displayName: String
) {
    IDENTITY("doc_identity", "Identity"),
    EDUCATION("doc_education", "Education"),
    BANKING("doc_banking", "Banking"),
    PERSONAL("doc_personal", "Personal"),
    OTHER("doc_other", "Other");

    companion object {
        fun fromId(id: String): DocumentCategory =
            entries.find { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) } ?: OTHER
    }
}

/**
 * DocumentEntity
 *
 * Encrypted document metadata record stored in the encrypted database.
 * The actual file payload is encrypted with AES-256-GCM in internal app storage.
 */
data class DocumentEntity(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val fileName: String,
    val fileExtension: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val categoryId: String = DocumentCategory.OTHER.id,
    val encryptedFileName: String = "$id.enc",
    val isFavorite: Boolean = false,
    val notes: String = "",
    val createdDate: Long = System.currentTimeMillis(),
    val updatedDate: Long = System.currentTimeMillis()
) {
    val category: DocumentCategory
        get() = DocumentCategory.fromId(categoryId)

    val formattedSize: String
        get() = formatFileSize(fileSizeBytes)

    fun toDocumentItem(): DocumentItem {
        val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(createdDate))
        return DocumentItem(
            id = id,
            title = title,
            fileName = fileName,
            fileExtension = fileExtension.uppercase(Locale.US).removePrefix("."),
            fileSizeFormatted = formattedSize,
            categoryId = categoryId,
            addedDate = dateStr,
            notes = notes,
            isFavorite = isFavorite
        )
    }

    companion object {
        fun formatFileSize(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
            val mb = kb / 1024.0
            return String.format(Locale.US, "%.1f MB", mb)
        }
    }
}
