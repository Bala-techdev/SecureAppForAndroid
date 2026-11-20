package com.balatechdev.vaultzero.security.recovery

import com.balatechdev.vaultzero.security.MemoryZeroizer
import com.balatechdev.vaultzero.security.auth.Argon2idKdf
import com.balatechdev.vaultzero.security.auth.Argon2idParameters
import com.balatechdev.vaultzero.security.auth.AuthError
import com.balatechdev.vaultzero.security.auth.AuthResult
import com.balatechdev.vaultzero.security.auth.MasterKey
import com.balatechdev.vaultzero.security.auth.MasterKeyHierarchy
import com.balatechdev.vaultzero.security.auth.MasterPasswordValidator
import com.balatechdev.vaultzero.security.auth.VaultKeyStorage
import com.balatechdev.vaultzero.security.auth.VaultSessionManager
import com.balatechdev.vaultzero.security.crypto.CryptoAuthException
import com.balatechdev.vaultzero.security.crypto.CryptoException
import com.balatechdev.vaultzero.security.crypto.EncryptedPayload
import com.balatechdev.vaultzero.security.crypto.VaultKey
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * RecoveryKeyEnvelope
 *
 * Encapsulates the emergency recovery copy of the Vault Encryption Key:
 * - Wrapped with a separate 256-bit KEK derived from the BIP-39 mnemonic via Argon2id.
 * - Stored with its own distinct 32-byte CSPRNG salt and 96-bit AES-GCM nonce.
 * - Never contains plaintext recovery words or plaintext master key.
 */
data class RecoveryKeyEnvelope(
    val salt: ByteArray,
    val nonce: ByteArray,
    val wrappedKey: ByteArray,
    val iterations: Int = Argon2idParameters.Default.iterations,
    val memoryKiB: Int = Argon2idParameters.Default.memoryKiB,
    val parallelism: Int = Argon2idParameters.Default.parallelism
) {
    fun toArgon2idParameters(): Argon2idParameters {
        return Argon2idParameters(
            memoryKiB = memoryKiB,
            iterations = iterations,
            parallelism = parallelism,
            saltLengthBytes = salt.size,
            outputLengthBytes = 32
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RecoveryKeyEnvelope
        if (!salt.contentEquals(other.salt)) return false
        if (!nonce.contentEquals(other.nonce)) return false
        if (!wrappedKey.contentEquals(other.wrappedKey)) return false
        if (iterations != other.iterations) return false
        if (memoryKiB != other.memoryKiB) return false
        if (parallelism != other.parallelism) return false
        return true
    }

    override fun hashCode(): Int {
        var result = salt.contentHashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + wrappedKey.contentHashCode()
        result = 31 * result + iterations
        result = 31 * result + memoryKiB
        result = 31 * result + parallelism
        return result
    }
}

/**
 * VaultRecoveryService
 *
 * Implements the offline disaster recovery architecture defined in SECURITY_DESIGN.md:
 * 1. Generates cryptographically secure 256-bit recovery secrets.
 * 2. Encodes secrets into 24-word BIP-39 mnemonic phrases.
 * 3. Protects an independent wrapped copy of the Vault Encryption Key.
 * 4. Recovers vault access without the original master password.
 * 5. Resets master password while preserving existing encrypted database and documents.
 */
interface VaultRecoveryService {
    fun generateRecoveryPhrase(): List<String>
    fun validateRecoveryPhrase(words: List<String>): Boolean
    fun verifyConfirmationWords(originalWords: List<String>, confirmedWords: Map<Int, String>): Boolean

    fun createRecoveryEnvelope(
        masterKeyBytes: ByteArray,
        mnemonicWords: List<String>,
        params: Argon2idParameters = Argon2idParameters.Default
    ): RecoveryKeyEnvelope

    fun recoverMasterKey(
        mnemonicWords: List<String>,
        envelope: RecoveryKeyEnvelope
    ): ByteArray

    fun resetMasterPasswordWithRecovery(
        mnemonicWords: List<String>,
        newPassword: CharArray,
        newConfirmation: CharArray,
        params: Argon2idParameters = Argon2idParameters.Default
    ): AuthResult<MasterKeyHierarchy>

    fun hasRecoveryEnvelope(): Boolean
    fun saveRecoveryEnvelope(envelope: RecoveryKeyEnvelope)
    fun getRecoveryEnvelope(): RecoveryKeyEnvelope?
}

class VaultRecoveryServiceImpl(
    private val keyStorage: VaultKeyStorage,
    private val sessionManager: VaultSessionManager,
    private val secureRandom: SecureRandom = SecureRandom()
) : VaultRecoveryService {

    private var inMemoryRecoveryEnvelope: RecoveryKeyEnvelope? = null

    override fun generateRecoveryPhrase(): List<String> {
        val entropy = Bip39Mnemonic.generateEntropy256(secureRandom)
        return Bip39Mnemonic.entropyToMnemonic(entropy)
    }

    override fun validateRecoveryPhrase(words: List<String>): Boolean {
        return try {
            Bip39Mnemonic.mnemonicToEntropy(words)
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun verifyConfirmationWords(originalWords: List<String>, confirmedWords: Map<Int, String>): Boolean {
        if (confirmedWords.isEmpty()) return false
        for ((oneBasedIndex, userWord) in confirmedWords) {
            val idx = oneBasedIndex - 1
            if (idx !in originalWords.indices) return false
            if (!originalWords[idx].equals(userWord.trim(), ignoreCase = true)) {
                return false
            }
        }
        return true
    }

    override fun createRecoveryEnvelope(
        masterKeyBytes: ByteArray,
        mnemonicWords: List<String>,
        params: Argon2idParameters
    ): RecoveryKeyEnvelope {
        // Validate mnemonic before deriving
        Bip39Mnemonic.mnemonicToEntropy(mnemonicWords)

        val salt = ByteArray(params.saltLengthBytes)
        secureRandom.nextBytes(salt)

        val nonce = ByteArray(12)
        secureRandom.nextBytes(nonce)

        val mnemonicChars = mnemonicWords.joinToString(" ").toCharArray()
        val recoveryKek = Argon2idKdf.deriveKey(mnemonicChars, salt, params)
        mnemonicChars.fill('0')

        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(recoveryKek, "AES")
            val gcmSpec = GCMParameterSpec(128, nonce)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

            val wrappedKey = cipher.doFinal(masterKeyBytes)

            val envelope = RecoveryKeyEnvelope(
                salt = salt,
                nonce = nonce,
                wrappedKey = wrappedKey,
                iterations = params.iterations,
                memoryKiB = params.memoryKiB,
                parallelism = params.parallelism
            )
            saveRecoveryEnvelope(envelope)
            return envelope
        } finally {
            MemoryZeroizer.zeroize(recoveryKek)
        }
    }

    override fun recoverMasterKey(
        mnemonicWords: List<String>,
        envelope: RecoveryKeyEnvelope
    ): ByteArray {
        // Strictly validates wordlist and checksum
        Bip39Mnemonic.mnemonicToEntropy(mnemonicWords)

        val params = envelope.toArgon2idParameters()
        val mnemonicChars = mnemonicWords.joinToString(" ").toCharArray()
        val recoveryKek = Argon2idKdf.deriveKey(mnemonicChars, envelope.salt, params)
        mnemonicChars.fill('0')

        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(recoveryKek, "AES")
            val gcmSpec = GCMParameterSpec(128, envelope.nonce)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

            return cipher.doFinal(envelope.wrappedKey)
        } catch (e: AEADBadTagException) {
            throw CryptoAuthException("Recovery phrase authentication failed: wrong words or corrupted envelope", e)
        } catch (e: Exception) {
            throw CryptoException("Failed to recover master key: ${e.message}", e)
        } finally {
            MemoryZeroizer.zeroize(recoveryKek)
        }
    }

    override fun resetMasterPasswordWithRecovery(
        mnemonicWords: List<String>,
        newPassword: CharArray,
        newConfirmation: CharArray,
        params: Argon2idParameters
    ): AuthResult<MasterKeyHierarchy> {
        val recoveryEnvelope = getRecoveryEnvelope()
            ?: return AuthResult.Failure(AuthError.CryptoError("No recovery envelope configured for this vault"))

        // 1. Confirm passwords match
        if (!MasterPasswordValidator.constantTimeEquals(newPassword, newConfirmation)) {
            return AuthResult.Failure(AuthError.PasswordMismatch)
        }

        // 2. Validate password strength
        val validation = MasterPasswordValidator.validate(newPassword)
        if (!validation.isValid) {
            return AuthResult.Failure(AuthError.WeakPassword(validation))
        }

        // 3. Recover original Master Key
        val rawMasterKeyBytes = try {
            recoverMasterKey(mnemonicWords, recoveryEnvelope)
        } catch (e: InvalidRecoveryPhraseException) {
            return AuthResult.Failure(AuthError.CryptoError(e.message ?: "Invalid recovery phrase"))
        } catch (e: CryptoAuthException) {
            return AuthResult.Failure(AuthError.InvalidCredentials)
        } catch (e: Exception) {
            return AuthResult.Failure(AuthError.CryptoError("Recovery failed: ${e.message}"))
        }

        try {
            val masterKey = MasterKey(rawMasterKeyBytes)
            val hierarchy = MasterKeyHierarchy.fromMasterKey(masterKey)

            // 4. Re-wrap Master Key with new master password
            val newSalt = ByteArray(params.saltLengthBytes)
            secureRandom.nextBytes(newSalt)
            val newIv = ByteArray(12)
            secureRandom.nextBytes(newIv)

            val newPasswordKek = Argon2idKdf.deriveKey(newPassword, newSalt, params)
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val keySpec = SecretKeySpec(newPasswordKek, "AES")
                val gcmSpec = GCMParameterSpec(128, newIv)
                cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

                val newWrappedMasterKey = masterKey.useKey { cipher.doFinal(it) }

                val newMasterEnvelope = com.balatechdev.vaultzero.security.auth.MasterKeyEnvelope(
                    salt = newSalt,
                    iv = newIv,
                    wrappedKey = newWrappedMasterKey,
                    iterations = params.iterations,
                    memoryKiB = params.memoryKiB,
                    parallelism = params.parallelism
                )

                // 5. Save updated master envelope in storage
                keyStorage.saveEnvelope(newMasterEnvelope)

                // 6. Start new active session with recovered hierarchy (preserves DB & docs)
                sessionManager.startSession(hierarchy)

                return AuthResult.Success(hierarchy)
            } finally {
                MemoryZeroizer.zeroize(newPasswordKek)
            }
        } finally {
            MemoryZeroizer.zeroize(rawMasterKeyBytes)
        }
    }

    override fun hasRecoveryEnvelope(): Boolean {
        return inMemoryRecoveryEnvelope != null
    }

    override fun saveRecoveryEnvelope(envelope: RecoveryKeyEnvelope) {
        inMemoryRecoveryEnvelope = envelope
    }

    override fun getRecoveryEnvelope(): RecoveryKeyEnvelope? {
        return inMemoryRecoveryEnvelope
    }
}
