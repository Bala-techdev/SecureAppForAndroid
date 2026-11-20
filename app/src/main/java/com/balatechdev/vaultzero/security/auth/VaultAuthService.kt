package com.balatechdev.vaultzero.security.auth

/**
 * VaultAuthService
 *
 * Primary authentication foundation service implementing the 8 core features:
 * 1. Create master password
 * 2. Confirm master password
 * 3. Validate password strength
 * 4. Unlock vault
 * 5. Reject incorrect password
 * 6. Lock vault
 * 7. Automatic session expiration
 * 8. Change master password
 */
interface VaultAuthService {
    fun isInitialized(): Boolean
    fun validatePasswordStrength(password: CharArray): PasswordValidationResult
    fun confirmMasterPassword(password: CharArray, confirmation: CharArray): Boolean
    fun createMasterPassword(
        password: CharArray,
        confirmation: CharArray,
        params: Argon2idParameters = Argon2idParameters.Default
    ): AuthResult<MasterKeyHierarchy>

    fun unlockVault(password: CharArray): AuthResult<MasterKeyHierarchy>
    fun lockVault()
    fun isSessionExpired(): Boolean
    fun recordActivity()
    fun checkAndExpireSession(): Boolean
    fun changeMasterPassword(
        currentPassword: CharArray,
        newPassword: CharArray,
        newConfirmation: CharArray,
        params: Argon2idParameters = Argon2idParameters.Default
    ): AuthResult<Unit>
}

class VaultAuthServiceImpl(
    private val keyStorage: VaultKeyStorage,
    private val keyEnvelopeService: KeyEnvelopeService = KeyEnvelopeService(),
    val sessionManager: VaultSessionManager = VaultSessionManager()
) : VaultAuthService {

    override fun isInitialized(): Boolean {
        return keyStorage.hasMasterPassword()
    }

    // FEATURE 3: Validate password strength
    override fun validatePasswordStrength(password: CharArray): PasswordValidationResult {
        return MasterPasswordValidator.validate(password)
    }

    // FEATURE 2: Confirm master password
    override fun confirmMasterPassword(password: CharArray, confirmation: CharArray): Boolean {
        return MasterPasswordValidator.constantTimeEquals(password, confirmation)
    }

    // FEATURE 1: Create master password
    override fun createMasterPassword(
        password: CharArray,
        confirmation: CharArray,
        params: Argon2idParameters
    ): AuthResult<MasterKeyHierarchy> {
        if (keyStorage.hasMasterPassword()) {
            return AuthResult.Failure(AuthError.VaultAlreadyInitialized)
        }

        if (!confirmMasterPassword(password, confirmation)) {
            return AuthResult.Failure(AuthError.PasswordMismatch)
        }

        val validation = validatePasswordStrength(password)
        if (!validation.isValid) {
            return AuthResult.Failure(AuthError.WeakPassword(validation))
        }

        return try {
            val (envelope, hierarchy) = keyEnvelopeService.createEnvelope(password, params)
            keyStorage.saveEnvelope(envelope)
            sessionManager.startSession(hierarchy)
            AuthResult.Success(hierarchy)
        } catch (e: Exception) {
            AuthResult.Failure(AuthError.CryptoError(e.message ?: "Vault initialization failed"))
        }
    }

    // FEATURE 4 & 5: Unlock vault and reject incorrect password
    override fun unlockVault(password: CharArray): AuthResult<MasterKeyHierarchy> {
        val envelope = keyStorage.getEnvelope()
            ?: return AuthResult.Failure(AuthError.VaultNotInitialized)

        return try {
            val hierarchy = keyEnvelopeService.unwrapMasterKey(password, envelope)
            sessionManager.startSession(hierarchy)
            AuthResult.Success(hierarchy)
        } catch (e: InvalidMasterPasswordException) {
            // Constant-time rejection of incorrect password
            AuthResult.Failure(AuthError.InvalidCredentials)
        } catch (e: Exception) {
            AuthResult.Failure(AuthError.CryptoError(e.message ?: "Authentication failed"))
        }
    }

    // FEATURE 6: Lock vault
    override fun lockVault() {
        sessionManager.lock()
    }

    // FEATURE 7: Automatic session expiration
    override fun isSessionExpired(): Boolean {
        return sessionManager.isSessionExpired()
    }

    override fun recordActivity() {
        sessionManager.recordUserActivity()
    }

    override fun checkAndExpireSession(): Boolean {
        return sessionManager.checkAndExpire()
    }

    // FEATURE 8: Change master password
    override fun changeMasterPassword(
        currentPassword: CharArray,
        newPassword: CharArray,
        newConfirmation: CharArray,
        params: Argon2idParameters
    ): AuthResult<Unit> {
        val envelope = keyStorage.getEnvelope()
            ?: return AuthResult.Failure(AuthError.VaultNotInitialized)

        if (!confirmMasterPassword(newPassword, newConfirmation)) {
            return AuthResult.Failure(AuthError.PasswordMismatch)
        }

        val validation = validatePasswordStrength(newPassword)
        if (!validation.isValid) {
            return AuthResult.Failure(AuthError.WeakPassword(validation))
        }

        return try {
            // Verify current password by unwrapping existing MasterKey
            val currentHierarchy = keyEnvelopeService.unwrapMasterKey(currentPassword, envelope)
            try {
                // Re-wrap the existing MasterKey under new master password
                val newEnvelope = keyEnvelopeService.rewrapMasterKey(currentHierarchy, newPassword, params)
                keyStorage.saveEnvelope(newEnvelope)

                // Refresh active session with current hierarchy
                sessionManager.startSession(currentHierarchy)
                AuthResult.Success(Unit)
            } finally {
                // Notice: currentHierarchy is transferred into active session,
                // if session already active it manages its lifecycle
            }
        } catch (e: InvalidMasterPasswordException) {
            AuthResult.Failure(AuthError.InvalidCredentials)
        } catch (e: Exception) {
            AuthResult.Failure(AuthError.CryptoError(e.message ?: "Password update failed"))
        }
    }
}
