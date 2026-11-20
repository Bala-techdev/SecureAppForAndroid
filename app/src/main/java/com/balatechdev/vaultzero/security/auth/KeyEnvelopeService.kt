package com.balatechdev.vaultzero.security.auth

import com.balatechdev.vaultzero.security.MemoryZeroizer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class InvalidMasterPasswordException(message: String = "Incorrect master password") : GeneralSecurityException(message)

/**
 * KeyEnvelopeService
 *
 * Implements the Key Envelope Pattern defined in SECURITY_DESIGN.md:
 * - Wraps the root Master Key with AES-256-GCM using an Argon2id-derived KEK.
 * - Unwraps the Master Key with constant-time AEAD tag verification.
 * - Re-wraps the Master Key when changing master password without invalidating existing data.
 */
class KeyEnvelopeService(
    private val secureRandom: SecureRandom = SecureRandom()
) {

    /**
     * Creates a new envelope and initialized key hierarchy.
     */
    fun createEnvelope(
        masterPassword: CharArray,
        params: Argon2idParameters = Argon2idParameters.Default
    ): Pair<MasterKeyEnvelope, MasterKeyHierarchy> {
        val salt = ByteArray(params.saltLengthBytes)
        secureRandom.nextBytes(salt)

        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val masterKey = MasterKey.generate(secureRandom)
        val kek = Argon2idKdf.deriveKey(masterPassword, salt, params)

        try {
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            val keySpec = SecretKeySpec(kek, "AES")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

            val wrappedKey = masterKey.useKey { mkBytes ->
                cipher.doFinal(mkBytes)
            }

            val envelope = MasterKeyEnvelope(
                salt = salt,
                iv = iv,
                wrappedKey = wrappedKey,
                iterations = params.iterations,
                memoryKiB = params.memoryKiB,
                parallelism = params.parallelism
            )

            val hierarchy = MasterKeyHierarchy.fromMasterKey(masterKey)
            return Pair(envelope, hierarchy)
        } finally {
            MemoryZeroizer.zeroize(kek)
        }
    }

    /**
     * Unwraps the root Master Key from an existing envelope.
     * Throws [InvalidMasterPasswordException] if the password or GCM tag fails verification.
     */
    fun unwrapMasterKey(
        masterPassword: CharArray,
        envelope: MasterKeyEnvelope
    ): MasterKeyHierarchy {
        val params = envelope.toArgon2idParameters()
        val kek = Argon2idKdf.deriveKey(masterPassword, envelope.salt, params)

        var decryptedKeyBytes: ByteArray? = null
        try {
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            val keySpec = SecretKeySpec(kek, "AES")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, envelope.iv)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

            decryptedKeyBytes = cipher.doFinal(envelope.wrappedKey)
            val masterKey = MasterKey(decryptedKeyBytes)
            return MasterKeyHierarchy.fromMasterKey(masterKey)
        } catch (e: Exception) {
            // GeneralSecurityException (including AEADBadTagException) indicates wrong password or tampering
            throw InvalidMasterPasswordException()
        } finally {
            MemoryZeroizer.zeroize(kek)
            MemoryZeroizer.zeroize(decryptedKeyBytes)
        }
    }

    /**
     * Re-wraps an existing Master Key with a new master password.
     * Crucially preserves the underlying Master Key, DBK, and DEK.
     */
    fun rewrapMasterKey(
        currentHierarchy: MasterKeyHierarchy,
        newMasterPassword: CharArray,
        params: Argon2idParameters = Argon2idParameters.Default
    ): MasterKeyEnvelope {
        val salt = ByteArray(params.saltLengthBytes)
        secureRandom.nextBytes(salt)

        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val newKek = Argon2idKdf.deriveKey(newMasterPassword, salt, params)

        try {
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            val keySpec = SecretKeySpec(newKek, "AES")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

            val wrappedKey = currentHierarchy.masterKey.useKey { mkBytes ->
                cipher.doFinal(mkBytes)
            }

            return MasterKeyEnvelope(
                salt = salt,
                iv = iv,
                wrappedKey = wrappedKey,
                iterations = params.iterations,
                memoryKiB = params.memoryKiB,
                parallelism = params.parallelism
            )
        } finally {
            MemoryZeroizer.zeroize(newKek)
        }
    }

    companion object {
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
