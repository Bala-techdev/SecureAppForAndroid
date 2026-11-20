package com.balatechdev.vaultzero.security.totp

/**
 * TotpCode
 *
 * Current generated TOTP code with time-window metadata for UI countdown presentation.
 */
data class TotpCode(
    val code: String,
    val remainingSeconds: Int,
    val progress: Float,
    val periodSeconds: Int
) {
    /**
     * Splits code into readable grouped format (e.g., "123 456" for 6 digits, "1234 5678" for 8 digits).
     */
    val formattedCode: String
        get() = when (code.length) {
            6 -> "${code.substring(0, 3)} ${code.substring(3)}"
            8 -> "${code.substring(0, 4)} ${code.substring(4)}"
            else -> code
        }
}
