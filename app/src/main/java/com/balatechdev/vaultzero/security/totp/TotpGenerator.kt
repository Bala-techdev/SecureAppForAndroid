package com.balatechdev.vaultzero.security.totp

import com.balatechdev.vaultzero.security.MemoryZeroizer
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * TotpGenerator
 *
 * Implements RFC 6238 (TOTP: Time-Based One-Time Password Algorithm)
 * and RFC 4226 (HOTP: HMAC-Based One-Time Password Algorithm).
 *
 * Evaluates codes offline using the local hardware system clock.
 */
object TotpGenerator {

    /**
     * Generates a one-time password code from raw secret bytes and timestamp.
     */
    fun generateCode(
        secretBytes: ByteArray,
        timeSeconds: Long,
        algorithm: TotpAlgorithm = TotpAlgorithm.SHA1,
        digits: Int = 6,
        periodSeconds: Int = 30
    ): String {
        require(digits in 6..8) { "Digits must be 6, 7, or 8, got $digits" }
        require(periodSeconds > 0) { "Period must be greater than 0, got $periodSeconds" }
        require(secretBytes.isNotEmpty()) { "Secret bytes cannot be empty" }

        // Time step counter: T = (Current Unix Time - T0) / X
        val timeStep = timeSeconds / periodSeconds

        // Convert 64-bit integer into 8-byte big-endian array
        val data = ByteBuffer.allocate(8).putLong(timeStep).array()

        val mac = Mac.getInstance(algorithm.hmacAlgorithm)
        mac.init(SecretKeySpec(secretBytes, algorithm.hmacAlgorithm))
        val hash = mac.doFinal(data)

        try {
            // Dynamic Truncation (RFC 4226 Section 5.4)
            val offset = hash[hash.size - 1].toInt() and 0x0F
            val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
                    ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                    ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                    (hash[offset + 3].toInt() and 0xFF)

            val divisor = when (digits) {
                6 -> 1_000_000
                7 -> 10_000_000
                8 -> 100_000_000
                else -> 1_000_000
            }

            val otp = binary % divisor
            return String.format("%0${digits}d", otp)
        } finally {
            MemoryZeroizer.zeroize(hash)
        }
    }

    /**
     * Generates a code and countdown metadata for a given [TotpConfig].
     */
    fun generateTotp(
        config: TotpConfig,
        currentTimeSeconds: Long = System.currentTimeMillis() / 1000L
    ): TotpCode {
        val secretBytes = config.decodeSecret()
        try {
            val code = generateCode(
                secretBytes = secretBytes,
                timeSeconds = currentTimeSeconds,
                algorithm = config.algorithm,
                digits = config.digits,
                periodSeconds = config.periodSeconds
            )
            val remaining = remainingSeconds(currentTimeSeconds, config.periodSeconds)
            val prog = progress(currentTimeSeconds, config.periodSeconds)

            return TotpCode(
                code = code,
                remainingSeconds = remaining,
                progress = prog,
                periodSeconds = config.periodSeconds
            )
        } finally {
            MemoryZeroizer.zeroize(secretBytes)
        }
    }

    /**
     * Calculates remaining seconds until the current time step window expires.
     */
    fun remainingSeconds(
        currentTimeSeconds: Long = System.currentTimeMillis() / 1000L,
        periodSeconds: Int = 30
    ): Int {
        val elapsedInPeriod = (currentTimeSeconds % periodSeconds).toInt()
        return periodSeconds - elapsedInPeriod
    }

    /**
     * Calculates progress ratio (1.0f at start of window down to 0.0f at expiration).
     */
    fun progress(
        currentTimeSeconds: Long = System.currentTimeMillis() / 1000L,
        periodSeconds: Int = 30
    ): Float {
        val remaining = remainingSeconds(currentTimeSeconds, periodSeconds)
        return remaining.toFloat() / periodSeconds.toFloat()
    }
}
