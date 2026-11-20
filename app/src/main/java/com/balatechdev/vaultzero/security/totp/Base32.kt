package com.balatechdev.vaultzero.security.totp

/**
 * Base32
 *
 * RFC 4648 standard Base32 encoder and decoder.
 * Supports case-insensitivity, ignores spaces and dashes, and handles padding.
 */
object Base32 {

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private val DECODE_TABLE = IntArray(128) { -1 }.apply {
        for (i in ALPHABET.indices) {
            val char = ALPHABET[i]
            this[char.code] = i
            this[char.lowercaseChar().code] = i
        }
    }

    /**
     * Decodes an RFC 4648 Base32 encoded string into raw bytes.
     * Throws [IllegalArgumentException] on invalid characters.
     */
    fun decode(base32String: String): ByteArray {
        val cleaned = base32String.replace(" ", "").replace("-", "").trimEnd('=').uppercase()
        if (cleaned.isEmpty()) {
            return ByteArray(0)
        }

        var buffer = 0
        var bitsLeft = 0
        val output = mutableListOf<Byte>()

        for (ch in cleaned) {
            if (ch.code >= DECODE_TABLE.size || DECODE_TABLE[ch.code] == -1) {
                throw IllegalArgumentException("Invalid Base32 character: '$ch'")
            }
            val value = DECODE_TABLE[ch.code]
            buffer = (buffer shl 5) or (value and 0x1F)
            bitsLeft += 5

            if (bitsLeft >= 8) {
                output.add(((buffer shr (bitsLeft - 8)) and 0xFF).toByte())
                bitsLeft -= 8
            }
        }

        return output.toByteArray()
    }

    /**
     * Encodes raw bytes into an RFC 4648 Base32 string.
     */
    fun encode(data: ByteArray): String {
        if (data.isEmpty()) return ""

        val sb = StringBuilder((data.size * 8 + 4) / 5)
        var buffer = 0
        var bitsLeft = 0

        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8

            while (bitsLeft >= 5) {
                val index = (buffer shr (bitsLeft - 5)) and 0x1F
                sb.append(ALPHABET[index])
                bitsLeft -= 5
            }
        }

        if (bitsLeft > 0) {
            val index = (buffer shl (5 - bitsLeft)) and 0x1F
            sb.append(ALPHABET[index])
        }

        return sb.toString()
    }

    /**
     * Validates whether a candidate string is valid RFC 4648 Base32.
     */
    fun isValid(candidate: String): Boolean {
        val cleaned = candidate.replace(" ", "").replace("-", "").trimEnd('=').uppercase()
        if (cleaned.isEmpty()) return false
        for (ch in cleaned) {
            if (ch.code >= DECODE_TABLE.size || DECODE_TABLE[ch.code] == -1) {
                return false
            }
        }
        return true
    }
}
