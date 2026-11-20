package com.balatechdev.vaultzero.security.auth

import com.balatechdev.vaultzero.data.model.PasswordStrength
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VaultAuthUnitTest {

    private lateinit var keyStorage: InMemoryVaultKeyStorage
    private lateinit var keyEnvelopeService: KeyEnvelopeService
    private lateinit var sessionManager: VaultSessionManager
    private lateinit var authService: VaultAuthServiceImpl

    // Use fast test parameters for Argon2id (1MB RAM, 2 iterations) to keep tests fast
    private val testParams = Argon2idParameters.Test

    private val validPassword = "V@ultZero#2026MasterKey".toCharArray()
    private val newPassword = "N3wStrong#Password2026".toCharArray()

    @Before
    fun setUp() {
        keyStorage = InMemoryVaultKeyStorage()
        keyEnvelopeService = KeyEnvelopeService()
        sessionManager = VaultSessionManager(timeoutDurationMillis = 1000L) // 1 second timeout
        authService = VaultAuthServiceImpl(
            keyStorage = keyStorage,
            keyEnvelopeService = keyEnvelopeService,
            sessionManager = sessionManager
        )
    }

    // ----------------------------------------------------
    // FEATURE 1: Create Master Password
    // ----------------------------------------------------
    @Test
    fun testCreateMasterPassword_success() {
        assertFalse(authService.isInitialized())

        val result = authService.createMasterPassword(
            password = validPassword,
            confirmation = validPassword.copyOf(),
            params = testParams
        )

        assertTrue(result is AuthResult.Success)
        val hierarchy = (result as AuthResult.Success).data
        assertNotNull(hierarchy)
        assertFalse(hierarchy.isDestroyed)

        // Verify envelope is persisted in storage
        assertTrue(keyStorage.hasMasterPassword())
        val envelope = keyStorage.getEnvelope()
        assertNotNull(envelope)
        assertEquals(32, envelope?.salt?.size)
        assertEquals(12, envelope?.iv?.size)

        // Verify session started automatically
        assertTrue(sessionManager.isUnlocked)
    }

    @Test
    fun testCreateMasterPassword_alreadyInitialized_fails() {
        authService.createMasterPassword(validPassword, validPassword.copyOf(), testParams)
        assertTrue(authService.isInitialized())

        val secondAttempt = authService.createMasterPassword(
            password = "AnotherPassword#2026".toCharArray(),
            confirmation = "AnotherPassword#2026".toCharArray(),
            params = testParams
        )

        assertTrue(secondAttempt is AuthResult.Failure)
        assertEquals(AuthError.VaultAlreadyInitialized, (secondAttempt as AuthResult.Failure).error)
    }

    // ----------------------------------------------------
    // FEATURE 2: Confirm Master Password
    // ----------------------------------------------------
    @Test
    fun testConfirmMasterPassword_matching_returnsTrue() {
        val pwd = "MySecurePassword123!".toCharArray()
        val confirm = "MySecurePassword123!".toCharArray()
        assertTrue(authService.confirmMasterPassword(pwd, confirm))
    }

    @Test
    fun testConfirmMasterPassword_mismatched_returnsFalse() {
        val pwd = "MySecurePassword123!".toCharArray()
        val confirm = "WrongConfirmation123!".toCharArray()
        assertFalse(authService.confirmMasterPassword(pwd, confirm))
    }

    // ----------------------------------------------------
    // FEATURE 3: Validate Password Strength
    // ----------------------------------------------------
    @Test
    fun testValidatePasswordStrength_tooShort_failsValidation() {
        val weakPwd = "Sh0rt!".toCharArray()
        val validation = authService.validatePasswordStrength(weakPwd)
        assertFalse(validation.isValid)
        assertEquals(PasswordStrength.WEAK, validation.strength)
        assertTrue(validation.errors.any { it.contains("at least 10 characters") })
    }

    @Test
    fun testValidatePasswordStrength_missingClasses_failsValidation() {
        val onlyLetters = "alllowercasepassword".toCharArray()
        val validation = authService.validatePasswordStrength(onlyLetters)
        assertFalse(validation.isValid)
        assertTrue(validation.errors.any { it.contains("uppercase") })
        assertTrue(validation.errors.any { it.contains("digit") })
        assertTrue(validation.errors.any { it.contains("symbol") })
    }

    @Test
    fun testValidatePasswordStrength_strongPassword_passesValidation() {
        val strong = "V@ultZero#2026SafePass".toCharArray()
        val validation = authService.validatePasswordStrength(strong)
        assertTrue(validation.isValid)
        assertTrue(validation.strength == PasswordStrength.GOOD || validation.strength == PasswordStrength.STRONG)
        assertTrue(validation.entropyScoreBits >= 60.0)
    }

    // ----------------------------------------------------
    // FEATURE 4 & 5: Unlock Vault and Reject Incorrect Password
    // ----------------------------------------------------
    @Test
    fun testUnlockVault_withCorrectPassword_succeeds() {
        authService.createMasterPassword(validPassword, validPassword.copyOf(), testParams)
        authService.lockVault()
        assertFalse(sessionManager.isUnlocked)

        val result = authService.unlockVault(validPassword)
        assertTrue(result is AuthResult.Success)
        assertTrue(sessionManager.isUnlocked)
    }

    @Test
    fun testUnlockVault_withIncorrectPassword_rejected() {
        authService.createMasterPassword(validPassword, validPassword.copyOf(), testParams)
        authService.lockVault()

        val incorrectPassword = "WrongPassword#99999".toCharArray()
        val result = authService.unlockVault(incorrectPassword)

        assertTrue(result is AuthResult.Failure)
        assertEquals(AuthError.InvalidCredentials, (result as AuthResult.Failure).error)
        assertFalse("Vault must remain locked on incorrect password", sessionManager.isUnlocked)
    }

    @Test
    fun testUnlockVault_whenNotInitialized_fails() {
        val result = authService.unlockVault(validPassword)
        assertTrue(result is AuthResult.Failure)
        assertEquals(AuthError.VaultNotInitialized, (result as AuthResult.Failure).error)
    }

    // ----------------------------------------------------
    // FEATURE 6: Lock Vault & Zeroize Memory
    // ----------------------------------------------------
    @Test
    fun testLockVault_zeroizesKeysAndClosesSession() {
        val result = authService.createMasterPassword(validPassword, validPassword.copyOf(), testParams)
        val hierarchy = (result as AuthResult.Success).data
        assertTrue(sessionManager.isUnlocked)
        assertFalse(hierarchy.isDestroyed)

        authService.lockVault()

        assertFalse(sessionManager.isUnlocked)
        assertTrue("Master key must be destroyed upon lock", hierarchy.isDestroyed)
    }

    // ----------------------------------------------------
    // FEATURE 7: Automatic Session Expiration
    // ----------------------------------------------------
    @Test
    fun testAutomaticSessionExpiration_expiresAfterTimeout() {
        val customSessionManager = VaultSessionManager(timeoutDurationMillis = 500L)
        val customAuthService = VaultAuthServiceImpl(
            keyStorage = keyStorage,
            keyEnvelopeService = keyEnvelopeService,
            sessionManager = customSessionManager
        )

        val now = System.currentTimeMillis()
        customAuthService.createMasterPassword(validPassword, validPassword.copyOf(), testParams)
        customSessionManager.recordUserActivity(now)

        // Activity at now + 200ms -> not expired
        assertFalse(customSessionManager.isSessionExpired(now + 200L))

        // Activity at now + 600ms -> expired
        assertTrue(customSessionManager.isSessionExpired(now + 600L))

        // Trigger checkAndExpire
        val didExpire = customSessionManager.checkAndExpire(now + 600L)
        assertTrue(didExpire)
        assertFalse(customSessionManager.isUnlocked(now + 600L))
    }

    @Test
    fun testRecordActivity_resetsInactivityTimer() {
        val customSessionManager = VaultSessionManager(timeoutDurationMillis = 500L)
        val now = System.currentTimeMillis()
        val hierarchy = MasterKeyHierarchy.fromMasterKey(MasterKey.generate())
        customSessionManager.startSession(hierarchy, now)

        // Record activity at now + 400ms
        customSessionManager.recordUserActivity(now + 400L)

        // At now + 600ms (only 200ms after last activity) -> still active!
        assertFalse(customSessionManager.isSessionExpired(now + 600L))
        assertTrue(customSessionManager.isUnlocked(now + 600L))

        // At now + 1000ms (600ms after last activity) -> now expired
        assertTrue(customSessionManager.isSessionExpired(now + 1000L))
        assertTrue(customSessionManager.checkAndExpire(now + 1000L))
        assertFalse(customSessionManager.isUnlocked(now + 1000L))
    }

    // ----------------------------------------------------
    // FEATURE 8: Change Master Password
    // ----------------------------------------------------
    @Test
    fun testChangeMasterPassword_preservesSubkeys() {
        // Step 1: Create vault with initial master password
        val initResult = authService.createMasterPassword(validPassword, validPassword.copyOf(), testParams)
        val originalHierarchy = (initResult as AuthResult.Success).data

        var originalDbk: ByteArray = ByteArray(0)
        var originalDek: ByteArray = ByteArray(0)
        originalHierarchy.useDatabaseKey { originalDbk = it.copyOf() }
        originalHierarchy.useDocumentKey { originalDek = it.copyOf() }

        // Step 2: Change master password to newPassword
        val changeResult = authService.changeMasterPassword(
            currentPassword = validPassword,
            newPassword = newPassword,
            newConfirmation = newPassword.copyOf(),
            params = testParams
        )
        assertTrue(changeResult is AuthResult.Success)

        // Step 3: Lock vault
        authService.lockVault()

        // Step 4: Old password should now be REJECTED
        val oldUnlockResult = authService.unlockVault(validPassword)
        assertTrue(oldUnlockResult is AuthResult.Failure)
        assertEquals(AuthError.InvalidCredentials, (oldUnlockResult as AuthResult.Failure).error)

        // Step 5: New password should SUCCEED
        val newUnlockResult = authService.unlockVault(newPassword)
        assertTrue(newUnlockResult is AuthResult.Success)
        val recoveredHierarchy = (newUnlockResult as AuthResult.Success).data

        // Step 6: Verify recovered DBK and DEK match the original keys!
        recoveredHierarchy.useDatabaseKey { recoveredDbk ->
            assertArrayEquals("DBK must remain identical after password change", originalDbk, recoveredDbk)
        }
        recoveredHierarchy.useDocumentKey { recoveredDek ->
            assertArrayEquals("DEK must remain identical after password change", originalDek, recoveredDek)
        }
    }

    @Test
    fun testChangeMasterPassword_wrongCurrentPassword_fails() {
        authService.createMasterPassword(validPassword, validPassword.copyOf(), testParams)

        val wrongCurrent = "IncorrectOldPassword#123".toCharArray()
        val result = authService.changeMasterPassword(
            currentPassword = wrongCurrent,
            newPassword = newPassword,
            newConfirmation = newPassword.copyOf(),
            params = testParams
        )

        assertTrue(result is AuthResult.Failure)
        assertEquals(AuthError.InvalidCredentials, (result as AuthResult.Failure).error)
    }

    // ----------------------------------------------------
    // Cryptographic Key Hierarchy & Zeroization
    // ----------------------------------------------------
    @Test
    fun testKeyHierarchy_subkeysAreDistinctAndDeterministic() {
        val mk = MasterKey.generate()
        val hierarchy1 = MasterKeyHierarchy.fromMasterKey(mk)

        var dbk1 = ByteArray(0)
        var dek1 = ByteArray(0)
        hierarchy1.useDatabaseKey { dbk1 = it.copyOf() }
        hierarchy1.useDocumentKey { dek1 = it.copyOf() }

        assertEquals(32, dbk1.size)
        assertEquals(32, dek1.size)
        assertFalse("DBK and DEK must be cryptographically isolated", dbk1.contentEquals(dek1))

        // Same MK yields identical DBK and DEK
        val hierarchy2 = MasterKeyHierarchy.fromMasterKey(MasterKey(mk.copyRawKey()))
        hierarchy2.useDatabaseKey { dbk2 ->
            assertArrayEquals(dbk1, dbk2)
        }
    }
}
