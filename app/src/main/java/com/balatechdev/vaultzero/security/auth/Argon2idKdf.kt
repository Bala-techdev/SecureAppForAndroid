package com.balatechdev.vaultzero.security.auth

import com.balatechdev.vaultzero.security.MemoryZeroizer
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets

/**
 * Argon2idKdf
 *
 * Implements memory-hard Argon2id key derivation using Bouncy Castle.
 * Ensures intermediate password byte buffers are zeroized immediately after key derivation.
 */
object Argon2idKdf {

    /**
     * Derives a cryptographic key from a master password CharArray and salt using Argon2id.
     * Intermediate UTF-8 encoded password bytes are wiped immediately.
     */
    fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        params: Argon2idParameters = Argon2idParameters.Default
    ): ByteArray {
        val passwordBytes = charArrayToUtf8ByteArray(password)
        try {
            return deriveKey(passwordBytes, salt, params)
        } finally {
            MemoryZeroizer.zeroize(passwordBytes)
        }
    }

    /**
     * Derives a cryptographic key from a raw password ByteArray and salt using Argon2id.
     */
    fun deriveKey(
        passwordBytes: ByteArray,
        salt: ByteArray,
        params: Argon2idParameters = Argon2idParameters.Default
    ): ByteArray {
        val argonParameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withMemoryAsKB(params.memoryKiB)
            .withIterations(params.iterations)
            .withParallelism(params.parallelism)
            .withSalt(salt)
            .build()

        val generator = Argon2BytesGenerator()
        generator.init(argonParameters)

        val outputKey = ByteArray(params.outputLengthBytes)
        generator.generateBytes(passwordBytes, outputKey, 0, outputKey.size)
        return outputKey
    }

    /**
     * Converts a CharArray directly to a UTF-8 ByteArray without creating an immutable String.
     */
    fun charArrayToUtf8ByteArray(chars: CharArray): ByteArray {
        val charBuffer = CharBuffer.wrap(chars)
        val byteBuffer = StandardCharsets.UTF_8.encode(charBuffer)
        val bytes = ByteArray(byteBuffer.remaining())
        byteBuffer.get(bytes)
        MemoryZeroizer.zeroize(byteBuffer)
        return bytes
    }
}
