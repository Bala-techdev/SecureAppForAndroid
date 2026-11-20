package com.balatechdev.vaultzero.data.model

import com.balatechdev.vaultzero.security.MemoryZeroizer
import java.util.UUID

/**
 * CredentialEntity
 *
 * Persistent database entity stored in encrypted SQLite.
 * Sensitive fields (password, notes, totpSecret) are encrypted with AES-256-GCM.
 * Plaintext password is NEVER stored.
 */
data class CredentialEntity(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val username: String,
    val encryptedPassword: ByteArray,
    val encryptedNotes: ByteArray? = null,
    val encryptedTotpSecret: ByteArray? = null,
    val website: String = "",
    val categoryId: String = "cat_logins",
    val isFavorite: Boolean = false,
    val createdDate: Long = System.currentTimeMillis(),
    val updatedDate: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CredentialEntity
        if (id != other.id) return false
        if (title != other.title) return false
        if (username != other.username) return false
        if (!encryptedPassword.contentEquals(other.encryptedPassword)) return false
        if (encryptedNotes != null) {
            if (other.encryptedNotes == null) return false
            if (!encryptedNotes.contentEquals(other.encryptedNotes)) return false
        } else if (other.encryptedNotes != null) return false
        if (encryptedTotpSecret != null) {
            if (other.encryptedTotpSecret == null) return false
            if (!encryptedTotpSecret.contentEquals(other.encryptedTotpSecret)) return false
        } else if (other.encryptedTotpSecret != null) return false
        if (website != other.website) return false
        if (categoryId != other.categoryId) return false
        if (isFavorite != other.isFavorite) return false
        if (createdDate != other.createdDate) return false
        if (updatedDate != other.updatedDate) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + username.hashCode()
        result = 31 * result + encryptedPassword.contentHashCode()
        result = 31 * result + (encryptedNotes?.contentHashCode() ?: 0)
        result = 31 * result + (encryptedTotpSecret?.contentHashCode() ?: 0)
        result = 31 * result + website.hashCode()
        result = 31 * result + categoryId.hashCode()
        result = 31 * result + isFavorite.hashCode()
        result = 31 * result + createdDate.hashCode()
        result = 31 * result + updatedDate.hashCode()
        return result
    }
}

/**
 * CredentialRecord
 *
 * In-memory decrypted domain model used by the presentation and repository layers.
 * Holds sensitive password as CharArray to allow zeroization.
 */
data class CredentialRecord(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val username: String,
    val passwordChars: CharArray,
    val website: String = "",
    val notes: String = "",
    val totpSecret: String? = null,
    val categoryId: String = "cat_logins",
    val isFavorite: Boolean = false,
    val createdDate: Long = System.currentTimeMillis(),
    val updatedDate: Long = System.currentTimeMillis(),
    val passwordStrength: PasswordStrength = PasswordStrength.STRONG
) : AutoCloseable {

    val passwordString: String
        get() = String(passwordChars)

    override fun close() {
        MemoryZeroizer.zeroize(passwordChars)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CredentialRecord
        if (id != other.id) return false
        if (title != other.title) return false
        if (username != other.username) return false
        if (!passwordChars.contentEquals(other.passwordChars)) return false
        if (website != other.website) return false
        if (notes != other.notes) return false
        if (totpSecret != other.totpSecret) return false
        if (categoryId != other.categoryId) return false
        if (isFavorite != other.isFavorite) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + username.hashCode()
        result = 31 * result + passwordChars.contentHashCode()
        result = 31 * result + website.hashCode()
        result = 31 * result + notes.hashCode()
        result = 31 * result + (totpSecret?.hashCode() ?: 0)
        result = 31 * result + categoryId.hashCode()
        result = 31 * result + isFavorite.hashCode()
        return result
    }
}

fun CredentialRecord.toCredentialItem(): CredentialItem = CredentialItem(
    id = id,
    title = title,
    username = username,
    passwordPlain = passwordString,
    passwordMasked = "••••••••••••••••",
    websiteUrl = website,
    categoryId = categoryId,
    notes = notes,
    totpSecret = totpSecret,
    isFavorite = isFavorite,
    passwordStrength = passwordStrength,
    lastModified = "Recently"
)

fun CredentialItem.toCredentialRecord(): CredentialRecord = CredentialRecord(
    id = id,
    title = title,
    username = username,
    passwordChars = passwordPlain.toCharArray(),
    website = websiteUrl,
    notes = notes,
    totpSecret = totpSecret,
    categoryId = categoryId,
    isFavorite = isFavorite,
    passwordStrength = passwordStrength
)

