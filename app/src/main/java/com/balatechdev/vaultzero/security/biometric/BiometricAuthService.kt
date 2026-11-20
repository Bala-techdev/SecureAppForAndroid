package com.balatechdev.vaultzero.security.biometric

import com.balatechdev.vaultzero.security.MemoryZeroizer
import com.balatechdev.vaultzero.security.auth.VaultSessionManager
import com.balatechdev.vaultzero.security.crypto.EncryptedPayload
import com.balatechdev.vaultzero.security.crypto.VaultKey
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher

interface BiometricAuthService {
    val keyManager: BiometricKeyManager
    val sessionManager: VaultSessionManager

    fun canAuthenticate(): BiometricAvailability
    fun isBiometricUnlockEnrolled(): Boolean
    fun enableBiometricUnlock(vaultKey: VaultKey): Boolean
    fun disableBiometricUnlock()

    fun prepareBiometricUnlockCipher(): Pair<Cipher, EncryptedPayload>?
    fun completeBiometricUnlock(authorizedCipher: Cipher): VaultKey

    fun authenticate(
        config: BiometricPromptConfig = BiometricPromptConfig(),
        onResult: (BiometricAuthResult) -> Unit
    )

    fun reauthenticateForSensitiveAction(
        action: SensitiveAction,
        onResult: (BiometricAuthResult) -> Unit
    )

    fun lockVault()
    fun isVaultLocked(): Boolean
    fun setAutoLockTimeout(timeoutMillis: Long)
}

/**
 * BiometricAuthServiceImpl
 *
 * Coordinates Android Keystore cryptographic authorization with biometric authentication.
 * Guarantees that biometric authentication directly authorizes the unwrapping of the
 * Vault Encryption Key from the hardware Keystore rather than merely bypassing the UI.
 */
class BiometricAuthServiceImpl(
    override val keyManager: BiometricKeyManager,
    override val sessionManager: VaultSessionManager,
    private val availabilityProvider: () -> BiometricAvailability = { BiometricAvailability.Available }
) : BiometricAuthService {

    private var storedBiometricEnvelope: EncryptedPayload? = null
    private val isEnrolledFlag = AtomicBoolean(false)

    // Interceptor hook for testing simulated biometric UI prompt responses
    var biometricPromptSimulator: ((BiometricPromptConfig, (BiometricAuthResult) -> Unit) -> Unit)? = null

    override fun canAuthenticate(): BiometricAvailability {
        return availabilityProvider()
    }

    override fun isBiometricUnlockEnrolled(): Boolean {
        return isEnrolledFlag.get() && storedBiometricEnvelope != null && keyManager.isKeyEnrolled()
    }

    override fun enableBiometricUnlock(vaultKey: VaultKey): Boolean {
        return try {
            keyManager.deleteKey()
            keyManager.generateKey()

            val (cipher, nonce) = keyManager.initEncryptCipher()
            val ciphertextAndTag = vaultKey.useKey { keyBytes ->
                cipher.doFinal(keyBytes)
            }

            storedBiometricEnvelope = EncryptedPayload(nonce = nonce, ciphertextAndTag = ciphertextAndTag)
            isEnrolledFlag.set(true)
            true
        } catch (e: Exception) {
            disableBiometricUnlock()
            false
        }
    }

    override fun disableBiometricUnlock() {
        keyManager.deleteKey()
        storedBiometricEnvelope = null
        isEnrolledFlag.set(false)
    }

    override fun prepareBiometricUnlockCipher(): Pair<Cipher, EncryptedPayload>? {
        val envelope = storedBiometricEnvelope ?: return null
        return try {
            val cipher = keyManager.initDecryptCipher(envelope.nonce)
            Pair(cipher, envelope)
        } catch (e: Exception) {
            if (keyManager.isKeyPermanentlyInvalidated(e)) {
                // Key invalidated due to new biometric enrollment on device
                disableBiometricUnlock()
            }
            null
        }
    }

    override fun completeBiometricUnlock(authorizedCipher: Cipher): VaultKey {
        val envelope = storedBiometricEnvelope
            ?: throw IllegalStateException("No biometric encrypted key envelope found")

        val decryptedKeyBytes = authorizedCipher.doFinal(envelope.ciphertextAndTag)
        return VaultKey(decryptedKeyBytes)
    }

    override fun authenticate(
        config: BiometricPromptConfig,
        onResult: (BiometricAuthResult) -> Unit
    ) {
        // 1. Check hardware availability
        val availability = canAuthenticate()
        if (availability !is BiometricAvailability.Available) {
            when (availability) {
                is BiometricAvailability.NotEnrolled -> {
                    onResult(BiometricAuthResult.NotEnrolled)
                    return
                }
                else -> {
                    onResult(BiometricAuthResult.Unavailable)
                    return
                }
            }
        }

        // 2. Check if biometric unlock is configured
        if (!isBiometricUnlockEnrolled()) {
            onResult(BiometricAuthResult.NotEnrolled)
            return
        }

        // 3. Prepare Keystore decrypt cipher
        val cipherAndEnvelope = prepareBiometricUnlockCipher()
        if (cipherAndEnvelope == null) {
            // Key was permanently invalidated or cipher initialization failed
            onResult(BiometricAuthResult.KeyInvalidated)
            return
        }

        val (cipher, _) = cipherAndEnvelope

        // 4. Delegate to biometric prompt UI or simulator
        val simulator = biometricPromptSimulator
        if (simulator != null) {
            simulator.invoke(config) { promptResult ->
                when (promptResult) {
                    is BiometricAuthResult.Success -> {
                        try {
                            val unlockedKey = completeBiometricUnlock(cipher)
                            onResult(BiometricAuthResult.Success(unlockedKey))
                        } catch (e: Exception) {
                            if (keyManager.isKeyPermanentlyInvalidated(e)) {
                                onResult(BiometricAuthResult.KeyInvalidated)
                            } else {
                                onResult(BiometricAuthResult.Error(-1, e.message ?: "Decryption failed"))
                            }
                        }
                    }
                    else -> onResult(promptResult)
                }
            }
        } else {
            // Fallback default: requires Master Password if prompt cannot be shown
            onResult(BiometricAuthResult.UserFallback)
        }
    }

    override fun reauthenticateForSensitiveAction(
        action: SensitiveAction,
        onResult: (BiometricAuthResult) -> Unit
    ) {
        val config = BiometricPromptConfig(
            title = "Authorize ${action.displayName}",
            subtitle = action.promptDescription,
            negativeButtonText = "Cancel"
        )
        authenticate(config, onResult)
    }

    // Manual locking
    override fun lockVault() {
        sessionManager.lock()
    }

    override fun isVaultLocked(): Boolean {
        return !sessionManager.isUnlocked
    }

    // Auto-locking
    override fun setAutoLockTimeout(timeoutMillis: Long) {
        sessionManager.timeoutDurationMillis = timeoutMillis
    }
}
