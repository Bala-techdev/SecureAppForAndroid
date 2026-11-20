package com.balatechdev.vaultzero.security.recovery

import com.balatechdev.vaultzero.security.crypto.CryptoException
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

open class InvalidRecoveryPhraseException(message: String, cause: Throwable? = null) : CryptoException(message, cause)
class UnknownRecoveryWordException(word: String) : InvalidRecoveryPhraseException("Unknown word in BIP-39 recovery phrase: '$word'")
class InvalidRecoveryChecksumException(message: String = "Recovery phrase checksum verification failed") : InvalidRecoveryPhraseException(message)
class InvalidRecoveryWordCountException(count: Int) : InvalidRecoveryPhraseException("Expected 24 words for 256-bit recovery key, but got $count")

/**
 * Bip39Mnemonic
 *
 * Implements Bitcoin Improvement Proposal 39 (BIP-0039) for 256-bit entropy:
 * - 256 bits entropy -> 8 bits SHA-256 checksum -> 264 bits -> 24 words (11 bits each).
 * - Full bidirectional encoding and decoding.
 * - Strict checksum validation to immediately reject typo'd or transposed phrases.
 * - PBKDF2 HMAC-SHA512 seed derivation (2048 rounds).
 */
object Bip39Mnemonic {

    const val ENTROPY_BITS = 256
    const val ENTROPY_BYTES = ENTROPY_BITS / 8 // 32 bytes
    const val CHECKSUM_BITS = ENTROPY_BITS / 32 // 8 bits (1 byte)
    const val TOTAL_BITS = ENTROPY_BITS + CHECKSUM_BITS // 264 bits
    const val WORD_COUNT = TOTAL_BITS / 11 // 24 words
    const val BITS_PER_WORD = 11

    private val secureRandom = SecureRandom()

    /**
     * Generates 256 bits (32 bytes) of cryptographically secure random entropy.
     */
    fun generateEntropy256(random: SecureRandom = secureRandom): ByteArray {
        val entropy = ByteArray(ENTROPY_BYTES)
        random.nextBytes(entropy)
        return entropy
    }

    /**
     * Encodes 256 bits of entropy into a standard 24-word BIP-39 mnemonic phrase.
     */
    fun entropyToMnemonic(entropy: ByteArray): List<String> {
        require(entropy.size == ENTROPY_BYTES) {
            "BIP-39 24-word recovery requires 32 bytes (256 bits) of entropy, got ${entropy.size}"
        }

        // 1. Calculate SHA-256 checksum (first 8 bits)
        val sha256 = MessageDigest.getInstance("SHA-256").digest(entropy)
        val checksumByte = sha256[0]

        // 2. Build 264-bit boolean array
        val bits = BooleanArray(TOTAL_BITS)
        var bitIndex = 0

        // 256 entropy bits
        for (b in entropy) {
            for (shift in 7 downTo 0) {
                bits[bitIndex++] = ((b.toInt() ushr shift) and 1) == 1
            }
        }

        // 8 checksum bits
        for (shift in 7 downTo 0) {
            bits[bitIndex++] = ((checksumByte.toInt() ushr shift) and 1) == 1
        }

        // 3. Convert 264 bits into 24 11-bit indices
        val words = ArrayList<String>(WORD_COUNT)
        for (i in 0 until WORD_COUNT) {
            var wordIndex = 0
            val offset = i * BITS_PER_WORD
            for (j in 0 until BITS_PER_WORD) {
                wordIndex = (wordIndex shl 1) or (if (bits[offset + j]) 1 else 0)
            }
            words.add(Bip39WordList.WORDS[wordIndex])
        }

        return words
    }

    /**
     * Decodes a 24-word BIP-39 mnemonic back to 256 bits of raw entropy.
     * Validates that all words belong to the official wordlist and checks SHA-256 checksum.
     */
    fun mnemonicToEntropy(words: List<String>): ByteArray {
        if (words.size != WORD_COUNT) {
            throw InvalidRecoveryWordCountException(words.size)
        }

        val bits = BooleanArray(TOTAL_BITS)
        var bitIndex = 0

        for (rawWord in words) {
            val cleanWord = rawWord.trim().lowercase()
            val wordIdx = Bip39WordList.WORD_INDEX_MAP[cleanWord]
                ?: throw UnknownRecoveryWordException(cleanWord)

            for (shift in (BITS_PER_WORD - 1) downTo 0) {
                bits[bitIndex++] = ((wordIdx ushr shift) and 1) == 1
            }
        }

        // Extract 32 bytes of entropy (first 256 bits)
        val entropy = ByteArray(ENTROPY_BYTES)
        for (i in 0 until ENTROPY_BYTES) {
            var b = 0
            val offset = i * 8
            for (j in 0 until 8) {
                b = (b shl 1) or (if (bits[offset + j]) 1 else 0)
            }
            entropy[i] = b.toByte()
        }

        // Extract 1 byte checksum (last 8 bits)
        var expectedChecksum = 0
        val csOffset = ENTROPY_BITS
        for (j in 0 until 8) {
            expectedChecksum = (expectedChecksum shl 1) or (if (bits[csOffset + j]) 1 else 0)
        }

        // Verify SHA-256 checksum
        val sha256 = MessageDigest.getInstance("SHA-256").digest(entropy)
        val calculatedChecksum = sha256[0].toInt() and 0xFF

        if (calculatedChecksum != expectedChecksum) {
            throw InvalidRecoveryChecksumException(
                "Recovery phrase checksum mismatch (expected 0x${Integer.toHexString(expectedChecksum)}, got 0x${Integer.toHexString(calculatedChecksum)})"
            )
        }

        return entropy
    }

    /**
     * Derives a 512-bit binary seed using BIP-39 PBKDF2 (HMAC-SHA512 with 2048 iterations).
     */
    fun mnemonicToSeed(words: List<String>, passphrase: String = ""): ByteArray {
        // Validate phrase first
        mnemonicToEntropy(words)

        val normalizedMnemonic = Normalizer.normalize(
            words.joinToString(" ") { it.trim().lowercase() },
            Normalizer.Form.NFKD
        )
        val normalizedPassphrase = Normalizer.normalize("mnemonic$passphrase", Normalizer.Form.NFKD)

        val spec = PBEKeySpec(
            normalizedMnemonic.toCharArray(),
            normalizedPassphrase.toByteArray(Charsets.UTF_8),
            2048,
            512 // 64 bytes
        )

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        return factory.generateSecret(spec).encoded
    }
}
