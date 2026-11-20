package com.balatechdev.vaultzero.security.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * EncryptionService
 *
 * Implements authenticated symmetric encryption using AES-256-GCM (NIST SP 800-38D):
 * - 256-bit keys
 * - Unique 96-bit (12-byte) CSPRNG nonce per encryption operation
 * - 128-bit (16-byte) authentication tag appended to the ciphertext
 * - Optional Additional Authenticated Data (AAD)
 */
interface EncryptionService {
    fun encrypt(
        plaintext: ByteArray,
        key: ByteArray,
        associatedData: ByteArray? = null
    ): EncryptedPayload

    fun encrypt(
        plaintext: ByteArray,
        key: VaultKey,
        associatedData: ByteArray? = null
    ): EncryptedPayload

    fun encryptToBytes(
        plaintext: ByteArray,
        key: ByteArray,
        associatedData: ByteArray? = null
    ): ByteArray {
        return encrypt(plaintext, key, associatedData).toBytes()
    }
}

class EncryptionServiceImpl(
    private val secureRandom: SecureRandom = SecureRandom()
) : EncryptionService {

    override fun encrypt(
        plaintext: ByteArray,
        key: ByteArray,
        associatedData: ByteArray?
    ): EncryptedPayload {
        if (key.size != KEY_LENGTH_BYTES) {
            throw InvalidKeyException("AES-256 requires a 32-byte (256-bit) key, got ${key.size}")
        }

        // REQUIREMENT 3: Generate a unique 96-bit nonce for each encryption
        val nonce = ByteArray(NONCE_LENGTH_BYTES)
        secureRandom.nextBytes(nonce)

        try {
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            val secretKeySpec = SecretKeySpec(key, "AES")
            val gcmParameterSpec = GCMParameterSpec(TAG_LENGTH_BITS, nonce)
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, gcmParameterSpec)

            // REQUIREMENT 5: Authenticate additional content when provided
            if (associatedData != null && associatedData.isNotEmpty()) {
                cipher.updateAAD(associatedData)
            }

            // Encrypt and append 128-bit GCM tag
            val ciphertextAndTag = cipher.doFinal(plaintext)

            // REQUIREMENT 4: Store nonce with ciphertext
            return EncryptedPayload(nonce = nonce, ciphertextAndTag = ciphertextAndTag)
        } catch (e: Exception) {
            throw CryptoException("Encryption failed: ${e.message}", e)
        }
    }

    override fun encrypt(
        plaintext: ByteArray,
        key: VaultKey,
        associatedData: ByteArray?
    ): EncryptedPayload {
        return key.useKey { keyBytes ->
            encrypt(plaintext, keyBytes, associatedData)
        }
    }

    companion object {
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_LENGTH_BYTES = 32   // 256 bits
        const val NONCE_LENGTH_BYTES = 12 // 96 bits
        const val TAG_LENGTH_BITS = 128   // 16 bytes
    }
}
