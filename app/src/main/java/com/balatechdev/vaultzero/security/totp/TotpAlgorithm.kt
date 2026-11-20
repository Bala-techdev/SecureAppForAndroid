package com.balatechdev.vaultzero.security.totp

/**
 * TotpAlgorithm
 *
 * Supported HMAC hash algorithms defined by RFC 6238:
 * - HMAC-SHA1 (Default standard per RFC 6238)
 * - HMAC-SHA256
 * - HMAC-SHA512
 */
enum class TotpAlgorithm(val hmacAlgorithm: String) {
    SHA1("HmacSHA1"),
    SHA256("HmacSHA256"),
    SHA512("HmacSHA512");

    companion object {
        fun fromString(value: String): TotpAlgorithm {
            return when (value.uppercase().trim()) {
                "SHA256", "HMAC-SHA256", "HMACSHA256" -> SHA256
                "SHA512", "HMAC-SHA512", "HMACSHA512" -> SHA512
                else -> SHA1
            }
        }
    }
}
