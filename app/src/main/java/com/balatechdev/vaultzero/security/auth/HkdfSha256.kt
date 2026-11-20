package com.balatechdev.vaultzero.security.auth

import com.balatechdev.vaultzero.security.MemoryZeroizer
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HkdfSha256
 *
 * Implements RFC 5869 HMAC-based Extract-and-Expand Key Derivation Function with SHA-256.
 * Used to deterministically derive subkeys (DBK, DEK) from the root Master Key.
 */
object HkdfSha256 {

    private const val HMAC_ALGORITHM = "HmacSHA256"
    private const val HASH_LEN = 32 // SHA-256 output length in bytes

    /**
     * HKDF Extract step: PRK = HMAC-Hash(salt, IKM)
     */
    fun extract(salt: ByteArray?, ikm: ByteArray): ByteArray {
        val effectiveSalt = if (salt == null || salt.isEmpty()) {
            ByteArray(HASH_LEN)
        } else {
            salt
        }

        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(effectiveSalt, HMAC_ALGORITHM))
        return mac.doFinal(ikm)
    }

    /**
     * HKDF Expand step: OKM = T(1) | T(2) | ... | T(N)
     */
    fun expand(prk: ByteArray, info: ByteArray, outputLengthBytes: Int): ByteArray {
        require(outputLengthBytes in 1..(255 * HASH_LEN)) {
            "Output length $outputLengthBytes out of bounds"
        }

        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(prk, HMAC_ALGORITHM))

        val n = (outputLengthBytes + HASH_LEN - 1) / HASH_LEN
        val okm = ByteArray(outputLengthBytes)
        var t = ByteArray(0)
        var offset = 0

        try {
            for (i in 1..n) {
                mac.update(t)
                mac.update(info)
                mac.update(i.toByte())
                t = mac.doFinal()

                val bytesToCopy = minOf(HASH_LEN, outputLengthBytes - offset)
                System.arraycopy(t, 0, okm, offset, bytesToCopy)
                offset += bytesToCopy
            }
        } finally {
            MemoryZeroizer.zeroize(t)
        }

        return okm
    }

    /**
     * Full HKDF (Extract + Expand)
     */
    fun deriveKey(
        salt: ByteArray?,
        ikm: ByteArray,
        info: String,
        outputLengthBytes: Int = 32
    ): ByteArray {
        val infoBytes = info.toByteArray(StandardCharsets.UTF_8)
        val prk = extract(salt, ikm)
        try {
            return expand(prk, infoBytes, outputLengthBytes)
        } finally {
            MemoryZeroizer.zeroize(prk)
        }
    }
}
