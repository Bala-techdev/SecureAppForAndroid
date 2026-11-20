package com.balatechdev.vaultzero.security.auth

import com.balatechdev.vaultzero.data.model.PasswordStrength
import com.balatechdev.vaultzero.security.MemoryZeroizer
import java.security.MessageDigest

data class PasswordValidationResult(
    val isValid: Boolean,
    val strength: PasswordStrength,
    val entropyScoreBits: Double,
    val errors: List<String>
)

/**
 * MasterPasswordValidator
 *
 * Enforces strong master password policy, calculates entropy,
 * and performs constant-time equality confirmation checks.
 */
object MasterPasswordValidator {

    const val MINIMUM_LENGTH = 10
    const val RECOMMENDED_LENGTH = 14

    /**
     * Evaluates the strength and validity of a candidate master password.
     */
    fun validate(password: CharArray): PasswordValidationResult {
        val errors = mutableListOf<String>()

        if (password.size < MINIMUM_LENGTH) {
            errors.add("Password must be at least $MINIMUM_LENGTH characters long")
        }

        var hasUpper = false
        var hasLower = false
        var hasDigit = false
        var hasSymbol = false

        for (ch in password) {
            when {
                ch.isUpperCase() -> hasUpper = true
                ch.isLowerCase() -> hasLower = true
                ch.isDigit() -> hasDigit = true
                else -> hasSymbol = true
            }
        }

        if (!hasUpper) errors.add("Password must contain at least one uppercase letter")
        if (!hasLower) errors.add("Password must contain at least one lowercase letter")
        if (!hasDigit) errors.add("Password must contain at least one numeric digit")
        if (!hasSymbol) errors.add("Password must contain at least one symbol or punctuation mark")

        // Calculate entropy: E = L * log2(poolSize)
        var poolSize = 0
        if (hasLower) poolSize += 26
        if (hasUpper) poolSize += 26
        if (hasDigit) poolSize += 10
        if (hasSymbol) poolSize += 33

        val entropy = if (poolSize > 0 && password.isNotEmpty()) {
            password.size * (Math.log(poolSize.toDouble()) / Math.log(2.0))
        } else {
            0.0
        }

        val strength = when {
            password.size < MINIMUM_LENGTH || entropy < 40.0 -> PasswordStrength.WEAK
            entropy < 60.0 -> PasswordStrength.FAIR
            entropy < 80.0 -> PasswordStrength.GOOD
            else -> PasswordStrength.STRONG
        }

        val isValid = errors.isEmpty()

        return PasswordValidationResult(
            isValid = isValid,
            strength = strength,
            entropyScoreBits = entropy,
            errors = errors
        )
    }

    /**
     * Performs a constant-time comparison between two CharArrays to prevent timing attacks.
     */
    fun constantTimeEquals(a: CharArray, b: CharArray): Boolean {
        val aBytes = Argon2idKdf.charArrayToUtf8ByteArray(a)
        val bBytes = Argon2idKdf.charArrayToUtf8ByteArray(b)
        try {
            return MessageDigest.isEqual(aBytes, bBytes)
        } finally {
            MemoryZeroizer.zeroize(aBytes)
            MemoryZeroizer.zeroize(bBytes)
        }
    }
}
