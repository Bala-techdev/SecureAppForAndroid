package com.balatechdev.vaultzero.security.crypto

import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * DecryptionService
 *
 * Implements secure authenticated decryption using AES-256-GCM:
 * - Constant-time authentication tag validation
 * - Detection of ciphertext, nonce, tag, or AAD modification
 * - Throws [CryptoAuthException] on any tampering or tag mismatch
 */
interface DecryptionService {
    fun decrypt(
        payload: EncryptedPayload,
        key: ByteArray,
        associatedData: ByteArray? = null
    ): ByteArray

    fun decrypt(
        payload: EncryptedPayload,
        key: VaultKey,
        associatedData: ByteArray? = null
    ): ByteArray

    fun decrypt(
        combinedBytes: ByteArray,
        key: ByteArray,
        associatedData: ByteArray? = null
    ): ByteArray {
        val payload = EncryptedPayload.fromBytes(combinedBytes)
        return decrypt(payload, key, associatedData)
    }
}

class DecryptionServiceImpl : DecryptionService {

    override fun decrypt(
        payload: EncryptedPayload,
        key: ByteArray,
        associatedData: ByteArray?
    ): ByteArray {
        if (key.size != KEY_LENGTH_BYTES) {
            throw InvalidKeyException("AES-256 requires a 32-byte (256-bit) key, got ${key.size}")
        }

        try {
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            val secretKeySpec = SecretKeySpec(key, "AES")
            val gcmParameterSpec = GCMParameterSpec(TAG_LENGTH_BITS, payload.nonce)
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, gcmParameterSpec)

            if (associatedData != null && associatedData.isNotEmpty()) {
                cipher.updateAAD(associatedData)
            }

            // REQUIREMENT 6, 7 & 8: Decrypt and verify 128-bit authentication tag
            return cipher.doFinal(payload.ciphertextAndTag)
        } catch (e: AEADBadTagException) {
            // Ciphertext, tag, nonce, or key was modified
            throw CryptoAuthException("Decryption authentication tag verification failed (tampered data or wrong key)", e)
        } catch (e: BadPaddingException) {
            throw CryptoAuthException("Decryption failed: bad padding or invalid authentication tag", e)
        } catch (e: CryptoException) {
            throw e
        } catch (e: Exception) {
            throw CryptoException("Decryption operation failed: ${e.message}", e)
        }
    }

    override fun decrypt(
        payload: EncryptedPayload,
        key: VaultKey,
        associatedData: ByteArray?
    ): ByteArray {
        return key.useKey { keyBytes ->
            decrypt(payload, keyBytes, associatedData)
        }
    }

    companion object {
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_LENGTH_BYTES = 32
        const val TAG_LENGTH_BITS = 128
    }
}
