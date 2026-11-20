package com.balatechdev.vaultzero.security.biometric

import com.balatechdev.vaultzero.security.crypto.VaultKey

/**
 * BiometricAvailability
 *
 * Represents the device's hardware biometric capabilities and enrollment state.
 */
sealed class BiometricAvailability {
    object Available : BiometricAvailability()
    object NotEnrolled : BiometricAvailability()
    object HardwareUnavailable : BiometricAvailability()
    object NoHardware : BiometricAvailability()
    data class SecurityUpdateRequired(val details: String) : BiometricAvailability()
    data class Unsupported(val reason: String) : BiometricAvailability()

    val isAvailable: Boolean
        get() = this is Available
}

/**
 * BiometricAuthResult
 *
 * Distinct states resulting from a biometric authentication attempt.
 */
sealed class BiometricAuthResult {
    data class Success(val unlockedKey: VaultKey) : BiometricAuthResult()
    data class Error(val errorCode: Int, val message: String) : BiometricAuthResult()
    object Failed : BiometricAuthResult()
    object Cancelled : BiometricAuthResult()
    object KeyInvalidated : BiometricAuthResult()
    object UserFallback : BiometricAuthResult()
    object NotEnrolled : BiometricAuthResult()
    object Unavailable : BiometricAuthResult()

    val isSuccess: Boolean
        get() = this is Success
}

/**
 * BiometricPromptConfig
 *
 * Configuration passed to AndroidX BiometricPrompt.
 */
data class BiometricPromptConfig(
    val title: String = "Unlock VaultZero",
    val subtitle: String = "Confirm your biometric identity",
    val description: String = "Authorize cryptographic key access via hardware Keystore",
    val negativeButtonText: String = "Use Master Password",
    val allowDeviceCredential: Boolean = false
)

/**
 * SensitiveAction
 *
 * Defines high-risk operations requiring explicit biometric re-authentication.
 */
enum class SensitiveAction(val displayName: String, val promptDescription: String) {
    VIEW_PASSWORD("View Master Password", "Authorize viewing sensitive master key"),
    EXPORT_VAULT("Export Vault Documents", "Authorize exporting unencrypted documents"),
    DELETE_VAULT("Delete Entire Vault", "Authorize permanent destruction of all vault data"),
    CHANGE_SECURITY_SETTINGS("Modify Security Settings", "Authorize changes to hardware security policies")
}
