package com.balatechdev.vaultzero.data.database

import com.balatechdev.vaultzero.data.model.CredentialEntity

/**
 * SQLCipherSchema
 *
 * Defines the SQLCipher encrypted SQLite database schema and pragma configurations.
 * Conforms to SQLCipher 4.x standards (AES-256-CBC with HMAC-SHA512).
 */
object SQLCipherSchema {

    const val DATABASE_NAME = "vaultzero_encrypted.db"
    const val DATABASE_VERSION = 1
    const val TABLE_CREDENTIALS = "credentials"

    // SQLCipher Configuration Pragmas
    val PRAGMA_STATEMENTS = listOf(
        "PRAGMA cipher_page_size = 4096;",
        "PRAGMA kdf_iter = 256000;",
        "PRAGMA cipher_hmac_algorithm = HMAC_SHA512;",
        "PRAGMA cipher_default_kdf_algorithm = PBKDF2_HMAC_SHA512;"
    )

    // Table Creation Statement
    const val CREATE_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS $TABLE_CREDENTIALS (
            id TEXT PRIMARY KEY NOT NULL,
            title TEXT NOT NULL,
            username TEXT NOT NULL,
            encrypted_password BLOB NOT NULL,
            encrypted_notes BLOB,
            encrypted_totp_secret BLOB,
            website TEXT,
            category_id TEXT NOT NULL DEFAULT 'cat_logins',
            is_favorite INTEGER NOT NULL DEFAULT 0,
            created_date INTEGER NOT NULL,
            updated_date INTEGER NOT NULL
        );
    """

    // Index statements for fast offline searching
    val CREATE_INDEX_STATEMENTS = listOf(
        "CREATE INDEX IF NOT EXISTS idx_cred_title ON $TABLE_CREDENTIALS(title);",
        "CREATE INDEX IF NOT EXISTS idx_cred_username ON $TABLE_CREDENTIALS(username);",
        "CREATE INDEX IF NOT EXISTS idx_cred_category ON $TABLE_CREDENTIALS(category_id);",
        "CREATE INDEX IF NOT EXISTS idx_cred_favorite ON $TABLE_CREDENTIALS(is_favorite);"
    )
}

/**
 * CredentialDao
 *
 * Data Access Object interface for encrypted credential management.
 */
interface CredentialDao {
    fun insert(entity: CredentialEntity): Long
    fun update(entity: CredentialEntity): Int
    fun deleteById(id: String): Int
    fun getById(id: String): CredentialEntity?
    fun getAll(): List<CredentialEntity>
    fun search(query: String, categoryId: String? = null): List<CredentialEntity>
    fun getFavorites(): List<CredentialEntity>
    fun getCount(): Int
    fun clear(): Int
}
