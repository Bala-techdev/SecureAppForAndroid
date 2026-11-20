package com.balatechdev.vaultzero.security.auth

import com.balatechdev.vaultzero.security.MemoryZeroizer
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MasterKey
 *
 * Holds the 256-bit root symmetric Master Key in memory.
 * Implements [AutoCloseable] so that sensitive key bytes are wiped immediately
 * via [MemoryZeroizer] upon session expiration or vault lock.
 */
class MasterKey(
    rawBytes: ByteArray
) : AutoCloseable {

    private val keyBytes: ByteArray = rawBytes.copyOf()
    private val closed = AtomicBoolean(false)

    val isDestroyed: Boolean
        get() = closed.get()

    init {
        require(rawBytes.size == KEY_LENGTH_BYTES) {
            "MasterKey must be exactly $KEY_LENGTH_BYTES bytes (256 bits), got ${rawBytes.size}"
        }
    }

    /**
     * Executes an operation with a temporary view of the key bytes.
     * Throws [IllegalStateException] if the key has been zeroized / closed.
     */
    fun <R> useKey(block: (ByteArray) -> R): R {
        check(!closed.get()) { "MasterKey has been closed and zeroized" }
        return block(keyBytes)
    }

    /**
     * Returns a copy of the key bytes. The caller is responsible for zeroizing the returned array.
     */
    fun copyRawKey(): ByteArray {
        check(!closed.get()) { "MasterKey has been closed and zeroized" }
        return keyBytes.copyOf()
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            MemoryZeroizer.zeroize(keyBytes)
        }
    }

    companion object {
        const val KEY_LENGTH_BYTES = 32 // 256 bits

        /**
         * Generates a new random 256-bit Master Key using CSPRNG.
         */
        fun generate(secureRandom: SecureRandom = SecureRandom()): MasterKey {
            val randomBytes = ByteArray(KEY_LENGTH_BYTES)
            secureRandom.nextBytes(randomBytes)
            try {
                return MasterKey(randomBytes)
            } finally {
                MemoryZeroizer.zeroize(randomBytes)
            }
        }
    }
}
