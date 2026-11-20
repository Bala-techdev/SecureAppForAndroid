package com.balatechdev.vaultzero.security.crypto

import com.balatechdev.vaultzero.security.MemoryZeroizer
import com.balatechdev.vaultzero.security.auth.Argon2idKdf
import com.balatechdev.vaultzero.security.auth.Argon2idParameters
import com.balatechdev.vaultzero.security.auth.HkdfSha256
import java.security.SecureRandom

/**
 * KeyDerivationService
 *
 * Provides cryptographic key derivation:
 * - Memory-hard password-based key derivation using Argon2id.
 * - HKDF-SHA256 Extract-and-Expand key expansion for subkey isolation.
 * - Cryptographically secure random salt generation.
 */
interface KeyDerivationService {
    fun generateSalt(lengthBytes: Int = 32): ByteArray
    fun deriveKeyFromPassword(
        password: CharArray,
        salt: ByteArray,
        params: Argon2idParameters = Argon2idParameters.Default
    ): ByteArray

    fun deriveKeyFromRecoverySecret(
        recoverySecret: CharArray,
        salt: ByteArray,
        params: Argon2idParameters = Argon2idParameters.Default
    ): ByteArray

    fun expandSubkey(
        inputKey: ByteArray,
        info: String,
        salt: ByteArray? = null,
        outputLengthBytes: Int = 32
    ): ByteArray
}

class KeyDerivationServiceImpl(
    private val secureRandom: SecureRandom = SecureRandom()
) : KeyDerivationService {

    override fun generateSalt(lengthBytes: Int): ByteArray {
        require(lengthBytes >= 16) { "Salt length must be at least 16 bytes (128 bits)" }
        val salt = ByteArray(lengthBytes)
        secureRandom.nextBytes(salt)
        return salt
    }

    override fun deriveKeyFromPassword(
        password: CharArray,
        salt: ByteArray,
        params: Argon2idParameters
    ): ByteArray {
        return Argon2idKdf.deriveKey(password, salt, params)
    }

    override fun deriveKeyFromRecoverySecret(
        recoverySecret: CharArray,
        salt: ByteArray,
        params: Argon2idParameters
    ): ByteArray {
        // Recovery secret is passed through memory-hard Argon2id to derive a 256-bit KEK
        return Argon2idKdf.deriveKey(recoverySecret, salt, params)
    }

    override fun expandSubkey(
        inputKey: ByteArray,
        info: String,
        salt: ByteArray?,
        outputLengthBytes: Int
    ): ByteArray {
        return HkdfSha256.deriveKey(salt, inputKey, info, outputLengthBytes)
    }
}
