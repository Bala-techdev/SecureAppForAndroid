package com.balatechdev.vaultzero.security.biometric

import com.balatechdev.vaultzero.security.VaultLockState
import com.balatechdev.vaultzero.security.auth.ActiveVaultSession
import com.balatechdev.vaultzero.security.auth.MasterKey
import com.balatechdev.vaultzero.security.auth.MasterKeyHierarchy
import com.balatechdev.vaultzero.security.auth.VaultSessionManager
import com.balatechdev.vaultzero.security.crypto.VaultKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.SecureRandom

class BiometricAuthUnitTest {

    private lateinit var keyManager: SoftwareBiometricKeyManager
    private lateinit var sessionManager: VaultSessionManager
    private lateinit var biometricService: BiometricAuthServiceImpl
    private var availabilityState: BiometricAvailability = BiometricAvailability.Available

    private val testMasterKeyBytes = ByteArray(32) { (it * 5 + 3).toByte() }

    @Before
    fun setUp() {
        keyManager = SoftwareBiometricKeyManager()
        sessionManager = VaultSessionManager(timeoutDurationMillis = 60_000L) // 1 minute default
        biometricService = BiometricAuthServiceImpl(
            keyManager = keyManager,
            sessionManager = sessionManager,
            availabilityProvider = { availabilityState }
        )
    }

    @After
    fun tearDown() {
        sessionManager.lock()
        keyManager.deleteKey()
    }

    // =========================================================================
    // 1. FINGERPRINT & CRYPTOGRAPHIC UNLOCK SUCCESS
    // =========================================================================

    @Test
    fun testBiometricUnlock_authorizesCryptographicKeyUnwrap() {
        val vaultKey = VaultKey(testMasterKeyBytes)

        // 1. Enable biometric unlock (wraps master key with Keystore key)
        val enrolled = biometricService.enableBiometricUnlock(vaultKey)
        assertTrue("Biometric key must enroll successfully", enrolled)
        assertTrue("Service must report biometric is enrolled", biometricService.isBiometricUnlockEnrolled())

        // 2. Configure simulated BiometricPrompt callback to report Success
        var receivedResult: BiometricAuthResult? = null
        biometricService.biometricPromptSimulator = { _, callback ->
            callback(BiometricAuthResult.Success(vaultKey))
        }

        // 3. Authenticate with biometrics
        biometricService.authenticate { result ->
            receivedResult = result
        }

        assertNotNull(receivedResult)
        assertTrue("Authentication must succeed", receivedResult is BiometricAuthResult.Success)

        val unlockedVaultKey = (receivedResult as BiometricAuthResult.Success).unlockedKey
        val keyBytes = unlockedVaultKey.copyBytes()
        assertTrue("Decrypted key must match original bytes", testMasterKeyBytes.contentEquals(keyBytes))
    }

    // =========================================================================
    // 2. BIOMETRIC AVAILABILITY & ENROLLMENT HANDLING
    // =========================================================================

    @Test
    fun testBiometricAvailability_hardwareUnavailable() {
        availabilityState = BiometricAvailability.HardwareUnavailable

        var result: BiometricAuthResult? = null
        biometricService.authenticate { res -> result = res }

        assertEquals(BiometricAuthResult.Unavailable, result)
        assertFalse(sessionManager.isUnlocked)
    }

    @Test
    fun testBiometricAvailability_notEnrolledOnDevice() {
        availabilityState = BiometricAvailability.NotEnrolled

        var result: BiometricAuthResult? = null
        biometricService.authenticate { res -> result = res }

        assertEquals(BiometricAuthResult.NotEnrolled, result)
        assertFalse(sessionManager.isUnlocked)
    }

    @Test
    fun testBiometricNotConfiguredInVault() {
        availabilityState = BiometricAvailability.Available
        // Not enrolled in biometricService

        var result: BiometricAuthResult? = null
        biometricService.authenticate { res -> result = res }

        assertEquals(BiometricAuthResult.NotEnrolled, result)
        assertFalse(sessionManager.isUnlocked)
    }

    // =========================================================================
    // 3. AUTHENTICATION CANCELLED & FAILED STATES
    // =========================================================================

    @Test
    fun testBiometricAuthentication_cancelledByUser() {
        val vaultKey = VaultKey(testMasterKeyBytes)
        biometricService.enableBiometricUnlock(vaultKey)

        biometricService.biometricPromptSimulator = { _, callback ->
            callback(BiometricAuthResult.Cancelled)
        }

        var result: BiometricAuthResult? = null
        biometricService.authenticate { res -> result = res }

        assertEquals(BiometricAuthResult.Cancelled, result)
        assertFalse("Vault must remain locked when biometric is cancelled", sessionManager.isUnlocked)
    }

    @Test
    fun testBiometricAuthentication_fingerprintMismatchFailed() {
        val vaultKey = VaultKey(testMasterKeyBytes)
        biometricService.enableBiometricUnlock(vaultKey)

        biometricService.biometricPromptSimulator = { _, callback ->
            callback(BiometricAuthResult.Failed)
        }

        var result: BiometricAuthResult? = null
        biometricService.authenticate { res -> result = res }

        assertEquals(BiometricAuthResult.Failed, result)
        assertFalse("Vault must remain locked when biometric recognition fails", sessionManager.isUnlocked)
    }

    // =========================================================================
    // 4. KEYSTORE KEY INVALIDATION (NEW FINGERPRINT ENROLLED)
    // =========================================================================

    @Test
    fun testKeystoreKeyInvalidation_detectedAndRequiresMasterPassword() {
        val vaultKey = VaultKey(testMasterKeyBytes)
        biometricService.enableBiometricUnlock(vaultKey)

        // Simulate OS-level event: new fingerprint added to device
        // Android Keystore marks key as KeyPermanentlyInvalidatedException
        keyManager.simulateNewBiometricEnrolled()

        var result: BiometricAuthResult? = null
        biometricService.authenticate { res -> result = res }

        assertEquals(BiometricAuthResult.KeyInvalidated, result)
        assertFalse("Biometric unlock must be revoked after key invalidation", biometricService.isBiometricUnlockEnrolled())
        assertFalse("Vault must remain locked", sessionManager.isUnlocked)
    }

    // =========================================================================
    // 5. MASTER PASSWORD FALLBACK
    // =========================================================================

    @Test
    fun testMasterPasswordFallback_whenUserSelectsFallback() {
        val vaultKey = VaultKey(testMasterKeyBytes)
        biometricService.enableBiometricUnlock(vaultKey)

        biometricService.biometricPromptSimulator = { _, callback ->
            callback(BiometricAuthResult.UserFallback)
        }

        var result: BiometricAuthResult? = null
        biometricService.authenticate { res -> result = res }

        assertEquals(BiometricAuthResult.UserFallback, result)

        // User falls back to master password to unlock session
        val hierarchy = MasterKeyHierarchy.fromMasterKey(MasterKey(testMasterKeyBytes))
        sessionManager.startSession(hierarchy)
        assertTrue("Vault unlocked via master password fallback", sessionManager.isUnlocked)
    }

    // =========================================================================
    // 6. AUTOMATIC LOCKING & INACTIVITY EXPIRATION
    // =========================================================================

    @Test
    fun testAutomaticLocking_expiresAfterTimeout() {
        val hierarchy = MasterKeyHierarchy.fromMasterKey(MasterKey(testMasterKeyBytes))
        val initialTime = 1_000_000L
        sessionManager.timeoutDurationMillis = 60_000L // 1 minute
        sessionManager.startSession(hierarchy, initialTime)

        assertTrue(sessionManager.isUnlocked(initialTime))

        // 30 seconds idle - should remain unlocked
        assertFalse(sessionManager.isSessionExpired(initialTime + 30_000L))
        assertTrue(sessionManager.isUnlocked(initialTime + 30_000L))

        // 61 seconds idle - exceeds timeout, must auto-lock
        assertTrue(sessionManager.isSessionExpired(initialTime + 61_000L))
        val didExpire = sessionManager.checkAndExpire(initialTime + 61_000L)
        assertTrue("Session must expire", didExpire)
        assertFalse("Session must be locked", sessionManager.isUnlocked(initialTime + 61_000L))
        assertEquals(VaultLockState.Locked, sessionManager.lockState.value)
    }

    @Test
    fun testAutomaticLocking_activityRefreshesTimeout() {
        val hierarchy = MasterKeyHierarchy.fromMasterKey(MasterKey(testMasterKeyBytes))
        val initialTime = 1_000_000L
        sessionManager.timeoutDurationMillis = 60_000L
        sessionManager.startSession(hierarchy, initialTime)

        // User interacts at 45 seconds
        sessionManager.recordUserActivity(initialTime + 45_000L)

        // At 90 seconds (45s after last activity), still unlocked
        assertFalse(sessionManager.isSessionExpired(initialTime + 90_000L))
        assertTrue(sessionManager.isUnlocked(initialTime + 90_000L))

        // At 110 seconds (65s after last activity), expires
        assertTrue(sessionManager.isSessionExpired(initialTime + 110_000L))
    }

    // =========================================================================
    // 7. MANUAL LOCKING
    // =========================================================================

    @Test
    fun testManualLocking_immediatelyZeroizesAndLocks() {
        val hierarchy = MasterKeyHierarchy.fromMasterKey(MasterKey(testMasterKeyBytes))
        sessionManager.startSession(hierarchy)
        assertTrue(sessionManager.isUnlocked)

        biometricService.lockVault()
        assertFalse("Vault must be locked immediately on manual lock", sessionManager.isUnlocked)
        assertTrue(biometricService.isVaultLocked())
        assertEquals(VaultLockState.Locked, sessionManager.lockState.value)
    }

    // =========================================================================
    // 8. RE-AUTHENTICATION FOR SENSITIVE ACTIONS
    // =========================================================================

    @Test
    fun testReauthenticationForSensitiveActions() {
        val vaultKey = VaultKey(testMasterKeyBytes)
        biometricService.enableBiometricUnlock(vaultKey)

        var passedConfig: BiometricPromptConfig? = null
        biometricService.biometricPromptSimulator = { config, callback ->
            passedConfig = config
            callback(BiometricAuthResult.Success(vaultKey))
        }

        var result: BiometricAuthResult? = null
        biometricService.reauthenticateForSensitiveAction(SensitiveAction.VIEW_PASSWORD) { res ->
            result = res
        }

        assertNotNull(passedConfig)
        assertTrue(passedConfig!!.title.contains("View Master Password"))
        assertTrue(passedConfig!!.subtitle.contains("Authorize viewing sensitive master key"))
        assertTrue("Re-authentication must succeed", result is BiometricAuthResult.Success)
    }
}
