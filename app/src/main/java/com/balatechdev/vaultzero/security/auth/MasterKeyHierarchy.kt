package com.balatechdev.vaultzero.security.auth

import com.balatechdev.vaultzero.security.MemoryZeroizer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MasterKeyHierarchy
 *
 * Encapsulates the complete cryptographic key hierarchy defined in SECURITY_DESIGN.md:
 * - Master Key (MK): 256-bit root key
 * - Database Key (DBK): 256-bit key for Room + SQLCipher (info: "VaultZero_DBK_v1")
 * - Document Encryption Key (DEK): 256-bit key for document encryption (info: "VaultZero_DEK_v1")
 *
 * Implements [AutoCloseable] to securely zeroize all keys upon session termination.
 */
class MasterKeyHierarchy(
    val masterKey: MasterKey,
    private val dbkBytes: ByteArray,
    private val dekBytes: ByteArray
) : AutoCloseable {

    private val closed = AtomicBoolean(false)

    val isDestroyed: Boolean
        get() = closed.get() || masterKey.isDestroyed

    /**
     * Executes an operation with a temporary view of the Database Key bytes.
     */
    fun <R> useDatabaseKey(block: (ByteArray) -> R): R {
        check(!closed.get()) { "MasterKeyHierarchy has been closed and zeroized" }
        return block(dbkBytes)
    }

    /**
     * Executes an operation with a temporary view of the Document Encryption Key bytes.
     */
    fun <R> useDocumentKey(block: (ByteArray) -> R): R {
        check(!closed.get()) { "MasterKeyHierarchy has been closed and zeroized" }
        return block(dekBytes)
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            masterKey.close()
            MemoryZeroizer.zeroize(dbkBytes)
            MemoryZeroizer.zeroize(dekBytes)
        }
    }

    companion object {
        const val DBK_INFO = "VaultZero_DBK_v1"
        const val DEK_INFO = "VaultZero_DEK_v1"

        /**
         * Derives DBK and DEK subkeys from a MasterKey using HKDF-SHA256.
         */
        fun fromMasterKey(masterKey: MasterKey): MasterKeyHierarchy {
            val (dbk, dek) = masterKey.useKey { mkBytes ->
                val dbk = HkdfSha256.deriveKey(salt = null, ikm = mkBytes, info = DBK_INFO, outputLengthBytes = 32)
                val dek = HkdfSha256.deriveKey(salt = null, ikm = mkBytes, info = DEK_INFO, outputLengthBytes = 32)
                Pair(dbk, dek)
            }
            return MasterKeyHierarchy(
                masterKey = masterKey,
                dbkBytes = dbk,
                dekBytes = dek
            )
        }
    }
}
