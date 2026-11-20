package com.balatechdev.vaultzero.security.crypto

import java.security.SecureRandom

/**
 * PasswordGeneratorRules
 *
 * Configurable options for secure password generation.
 */
data class PasswordGeneratorRules(
    val length: Int = 16,
    val includeUppercase: Boolean = true,
    val includeLowercase: Boolean = true,
    val includeDigits: Boolean = true,
    val includeSymbols: Boolean = true,
    val excludeAmbiguous: Boolean = true
) {
    init {
        require(length in 8..64) { "Password length must be between 8 and 64 characters, got $length" }
        require(includeUppercase || includeLowercase || includeDigits || includeSymbols) {
            "At least one character class must be enabled"
        }
    }
}

/**
 * PasswordGenerator
 *
 * Cryptographically secure password generator conforming to OWASP guidelines:
 * - CSPRNG via SecureRandom
 * - Guaranteed inclusion of all selected character classes
 * - Uniform distribution shuffle
 */
class PasswordGenerator(
    private val secureRandom: SecureRandom = SecureRandom()
) {
    fun generate(rules: PasswordGeneratorRules = PasswordGeneratorRules()): CharArray {
        val uppercasePool = if (rules.excludeAmbiguous) "ABCDEFGHJKLMNPQRSTUVWXYZ" else "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val lowercasePool = if (rules.excludeAmbiguous) "abcdefghijkmnopqrstuvwxyz" else "abcdefghijklmnopqrstuvwxyz"
        val digitsPool = if (rules.excludeAmbiguous) "23456789" else "0123456789"
        val symbolsPool = "!@#$%^&*()-_=+[]{}|;:,.<>?"

        val pools = mutableListOf<String>()
        if (rules.includeUppercase) pools.add(uppercasePool)
        if (rules.includeLowercase) pools.add(lowercasePool)
        if (rules.includeDigits) pools.add(digitsPool)
        if (rules.includeSymbols) pools.add(symbolsPool)

        val fullPool = pools.joinToString("")
        val password = CharArray(rules.length)

        // Guarantee at least one character from each selected class
        var index = 0
        for (pool in pools) {
            if (index < rules.length) {
                password[index++] = pool[secureRandom.nextInt(pool.length)]
            }
        }

        // Fill remaining length from complete pool
        while (index < rules.length) {
            password[index++] = fullPool[secureRandom.nextInt(fullPool.length)]
        }

        // Fisher-Yates shuffle using SecureRandom for unbiased permutation
        for (i in password.indices.reversed()) {
            val j = secureRandom.nextInt(i + 1)
            val temp = password[i]
            password[i] = password[j]
            password[j] = temp
        }

        return password
    }

    fun generateString(rules: PasswordGeneratorRules = PasswordGeneratorRules()): String {
        return String(generate(rules))
    }
}
