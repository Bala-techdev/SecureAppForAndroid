package com.balatechdev.vaultzero.security.auth

import java.util.Arrays

/**
 * MasterKeyEnvelope
 *
 * Encapsulates the encrypted Master Key and the KDF parameters required to unlock it.
 * As defined in SECURITY_DESIGN.md, never contains the master password or plaintext key.
 */
data class MasterKeyEnvelope(
    val salt: ByteArray,
    val iv: ByteArray,
    val wrappedKey: ByteArray,
    val iterations: Int,
    val memoryKiB: Int,
    val parallelism: Int
) {
    fun toArgon2idParameters(): Argon2idParameters {
        return Argon2idParameters(
            memoryKiB = memoryKiB,
            iterations = iterations,
            parallelism = parallelism,
            saltLengthBytes = salt.size,
            outputLengthBytes = 32
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MasterKeyEnvelope
        if (!salt.contentEquals(other.salt)) return false
        if (!iv.contentEquals(other.iv)) return false
        if (!wrappedKey.contentEquals(other.wrappedKey)) return false
        if (iterations != other.iterations) return false
        if (memoryKiB != other.memoryKiB) return false
        if (parallelism != other.parallelism) return false

        return true
    }

    override fun hashCode(): Int {
        var result = salt.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + wrappedKey.contentHashCode()
        result = 31 * result + iterations
        result = 31 * result + memoryKiB
        result = 31 * result + parallelism
        return result
    }
}
