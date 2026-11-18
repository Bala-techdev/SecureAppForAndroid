package com.balatechdev.vaultzero.security

import java.nio.ByteBuffer
import java.util.Arrays

/**
 * MemoryZeroizer
 *
 * Provides explicit memory wiping utilities for sensitive buffers.
 * Avoids String pooling by mandating CharArray/ByteArray lifecycle destruction.
 */
object MemoryZeroizer {

    fun zeroize(array: ByteArray?) {
        if (array != null) {
            Arrays.fill(array, 0.toByte())
        }
    }

    fun zeroize(array: CharArray?) {
        if (array != null) {
            Arrays.fill(array, '\u0000')
        }
    }

    fun zeroize(buffer: ByteBuffer?) {
        if (buffer != null && !buffer.isReadOnly) {
            buffer.clear()
            while (buffer.hasRemaining()) {
                buffer.put(0.toByte())
            }
            buffer.clear()
        }
    }
}
