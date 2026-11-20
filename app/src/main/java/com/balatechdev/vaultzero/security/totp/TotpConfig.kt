package com.balatechdev.vaultzero.security.totp

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * TotpConfig
 *
 * Encapsulates the configuration parameters for generating TOTP authentication codes.
 */
data class TotpConfig(
    val issuer: String = "",
    val accountName: String = "",
    val secret: String,
    val algorithm: TotpAlgorithm = TotpAlgorithm.SHA1,
    val digits: Int = DEFAULT_DIGITS,
    val periodSeconds: Int = DEFAULT_PERIOD_SECONDS
) {
    init {
        require(digits in 6..8) { "TOTP code digits must be between 6 and 8, got $digits" }
        require(periodSeconds > 0) { "TOTP period must be positive, got $periodSeconds" }
        require(secret.isNotBlank()) { "TOTP secret cannot be blank" }
    }

    /**
     * Decodes the Base32 secret into raw byte array.
     */
    fun decodeSecret(): ByteArray {
        return Base32.decode(secret)
    }

    companion object {
        const val DEFAULT_DIGITS = 6
        const val DEFAULT_PERIOD_SECONDS = 30

        /**
         * Parses an `otpauth://totp/` URI conforming to the Key Uri Format specification.
         * Example: `otpauth://totp/GitHub:alex?secret=JBSWY3DPEHPK3PXP&issuer=GitHub&algorithm=SHA1&digits=6&period=30`
         */
        fun parseUri(uriString: String): TotpConfig {
            val trimmed = uriString.trim()
            if (!trimmed.startsWith("otpauth://totp/", ignoreCase = true)) {
                throw IllegalArgumentException("URI must start with 'otpauth://totp/'")
            }

            val pathAndQuery = trimmed.substring("otpauth://totp/".length)
            val parts = pathAndQuery.split("?", limit = 2)
            val rawLabel = parts[0]
            val queryString = if (parts.size > 1) parts[1] else ""

            val decodedLabel = URLDecoder.decode(rawLabel, StandardCharsets.UTF_8.name())
            var labelIssuer = ""
            var labelAccount = decodedLabel

            if (decodedLabel.contains(":")) {
                val labelParts = decodedLabel.split(":", limit = 2)
                labelIssuer = labelParts[0].trim()
                labelAccount = labelParts[1].trim()
            }

            val queryParams = mutableMapOf<String, String>()
            if (queryString.isNotEmpty()) {
                queryString.split("&").forEach { param ->
                    val keyValue = param.split("=", limit = 2)
                    if (keyValue.isNotEmpty()) {
                        val key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8.name()).lowercase()
                        val value = if (keyValue.size > 1) URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.name()) else ""
                        queryParams[key] = value
                    }
                }
            }

            val secret = queryParams["secret"]
                ?: throw IllegalArgumentException("Missing 'secret' parameter in OTP URI")

            val issuer = queryParams["issuer"] ?: labelIssuer
            val algorithm = TotpAlgorithm.fromString(queryParams["algorithm"] ?: "SHA1")
            val digits = queryParams["digits"]?.toIntOrNull() ?: DEFAULT_DIGITS
            val period = queryParams["period"]?.toIntOrNull() ?: DEFAULT_PERIOD_SECONDS

            return TotpConfig(
                issuer = issuer,
                accountName = labelAccount,
                secret = secret,
                algorithm = algorithm,
                digits = digits,
                periodSeconds = period
            )
        }
    }
}
