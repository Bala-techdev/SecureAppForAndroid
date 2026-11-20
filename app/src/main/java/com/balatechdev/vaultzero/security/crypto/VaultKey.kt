package com.balatechdev.vaultzero.security.crypto

import com.balatechdev.vaultzero.security.MemoryZeroizer
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean

/**
 * VaultKey
 *
 * Represents an in-memory 256-bit symmetric encryption key.
 * Implements [AutoCloseable] to ensure zeroization upon session closure.
 */
class VaultKey(
    rawBytes: ByteArray
) : AutoCloseable {

    private val keyBytes = rawBytes.copyOf()
    private val closed = AtomicBoolean(false)

    val isDestroyed: Boolean
        get() = closed.get()

    init {
        if (rawBytes.size != KEY_SIZE_BYTES) {
            throw InvalidKeyException("VaultKey must be exactly $KEY_SIZE_BYTES bytes (256 bits), got ${rawBytes.size}")
        }
    }

    /**
     * Executes a lambda with the key bytes safely and checks for zeroization.
     */
    fun <R> useKey(block: (ByteArray) -> R): R {
        check(!closed.get()) { "VaultKey has been closed and zeroized" }
        return block(keyBytes)
    }

    /**
     * Returns a copy of the key bytes. Caller is responsible for zeroizing the copy.
     */
    fun copyBytes(): ByteArray {
        check(!closed.get()) { "VaultKey has been closed and zeroized" }
        return keyBytes.copyOf()
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            MemoryZeroizer.zeroize(keyBytes)
        }
    }

    companion object {
        const val KEY_SIZE_BYTES = 32 // 256 bits

        /**
         * REQUIREMENT 1: Generate a 256-bit random vault encryption key using CSPRNG.
         */
        fun generate(secureRandom: SecureRandom = SecureRandom()): VaultKey {
            val randomBytes = ByteArray(KEY_SIZE_BYTES)
            secureRandom.nextBytes(randomBytes)
            try {
                return VaultKey(randomBytes)
            } finally {
                MemoryZeroizer.zeroize(randomBytes)
            }
        }
    }
}
