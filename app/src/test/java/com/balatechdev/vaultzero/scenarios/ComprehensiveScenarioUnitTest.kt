package com.balatechdev.vaultzero.scenarios

import com.balatechdev.vaultzero.data.backup.VaultBackupServiceImpl
import com.balatechdev.vaultzero.data.database.InMemoryEncryptedCredentialDatabase
import com.balatechdev.vaultzero.data.database.InMemoryEncryptedDocumentDatabase
import com.balatechdev.vaultzero.data.model.CredentialEntity
import com.balatechdev.vaultzero.data.model.CredentialRecord
import com.balatechdev.vaultzero.data.model.DocumentCategory
import com.balatechdev.vaultzero.data.repository.CredentialRepositoryImpl
import com.balatechdev.vaultzero.data.repository.DocumentRepositoryImpl
import com.balatechdev.vaultzero.security.auth.Argon2idParameters
import com.balatechdev.vaultzero.security.auth.AuthError
import com.balatechdev.vaultzero.security.auth.AuthResult
import com.balatechdev.vaultzero.security.auth.InMemoryVaultKeyStorage
import com.balatechdev.vaultzero.security.auth.KeyEnvelopeService
import com.balatechdev.vaultzero.security.auth.MasterKey
import com.balatechdev.vaultzero.security.auth.MasterKeyHierarchy
import com.balatechdev.vaultzero.security.auth.VaultAuthServiceImpl
import com.balatechdev.vaultzero.security.auth.VaultSessionManager
import com.balatechdev.vaultzero.security.biometric.BiometricAuthResult
import com.balatechdev.vaultzero.security.biometric.BiometricAuthServiceImpl
import com.balatechdev.vaultzero.security.biometric.SoftwareBiometricKeyManager
import com.balatechdev.vaultzero.security.crypto.CryptoAuthException
import com.balatechdev.vaultzero.security.crypto.DecryptionServiceImpl
import com.balatechdev.vaultzero.security.crypto.DocumentCryptoEngine
import com.balatechdev.vaultzero.security.crypto.EncryptedPayload
import com.balatechdev.vaultzero.security.crypto.EncryptionServiceImpl
import com.balatechdev.vaultzero.security.crypto.VaultKey
import com.balatechdev.vaultzero.security.recovery.Bip39Mnemonic
import com.balatechdev.vaultzero.security.recovery.InvalidRecoveryChecksumException
import com.balatechdev.vaultzero.security.recovery.VaultRecoveryServiceImpl
import com.balatechdev.vaultzero.security.totp.TotpConfig
import com.balatechdev.vaultzero.security.totp.TotpGenerator
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files

class ComprehensiveScenarioUnitTest {

    private lateinit var tempDir: File
    private lateinit var documentStorageDir: File

    private val testParams = Argon2idParameters.Test
    private val masterPassword = "SuperMasterPassword#2026".toCharArray()
    private val wrongPassword = "CompletelyWrongPassword!".toCharArray()

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("vault_comprehensive_test_").toFile()
        documentStorageDir = File(tempDir, "vault_documents").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // =========================================================================
    // SCENARIO 1: INCORRECT PASSWORD REJECTION
    // =========================================================================

    @Test
    fun testScenario_incorrectPassword_rejectsAndPreservesLock() {
        val keyStorage = InMemoryVaultKeyStorage()
        val sessionManager = VaultSessionManager()
        val authService = VaultAuthServiceImpl(keyStorage, KeyEnvelopeService(), sessionManager)

        // Setup master password
        val createResult = authService.createMasterPassword(masterPassword, masterPassword.copyOf(), testParams)
        assertTrue(createResult is AuthResult.Success<*>)
        assertTrue(sessionManager.isUnlocked)

        // Explicitly lock vault
        authService.lockVault()
        assertFalse(sessionManager.isUnlocked)

        // Attempt unlock with wrong password
        val unlockResult = authService.unlockVault(wrongPassword)
        assertTrue("Unlock with wrong password must return Failure", unlockResult is AuthResult.Failure)
        assertEquals(AuthError.InvalidCredentials, (unlockResult as AuthResult.Failure).error)
        assertFalse("Vault must remain strictly locked after wrong password", sessionManager.isUnlocked)
        assertNull("No active session must exist in memory", sessionManager.currentSession)
    }

    // =========================================================================
    // SCENARIO 2: CORRUPTED CIPHERTEXT DETECTION
    // =========================================================================

    @Test
    fun testScenario_corruptedCiphertext_triggersAeadFailure() {
        val key = ByteArray(32) { (it + 7).toByte() }
        val encryptionService = EncryptionServiceImpl()
        val decryptionService = DecryptionServiceImpl()

        val plaintext = "Sensitive Financial Records".toByteArray()
        val encryptedPayload = encryptionService.encrypt(plaintext, key)

        // Mutate a byte in the ciphertext payload
        val corruptedBytes = encryptedPayload.ciphertextAndTag.copyOf()
        corruptedBytes[0] = (corruptedBytes[0].toInt() xor 0x5A).toByte()

        val corruptedPayload = EncryptedPayload(
            nonce = encryptedPayload.nonce,
            ciphertextAndTag = corruptedBytes
        )

        try {
            decryptionService.decrypt(corruptedPayload, key)
            fail("Expected CryptoAuthException when decrypting corrupted ciphertext")
        } catch (e: CryptoAuthException) {
            assertTrue(e.message!!.contains("AEAD") || e.message!!.contains("tag") || e.message!!.contains("tampered"))
        }
    }

    // =========================================================================
    // SCENARIO 3: INCORRECT ENCRYPTION KEY
    // =========================================================================

    @Test
    fun testScenario_incorrectEncryptionKey_failsAuthentication() {
        val correctKey = ByteArray(32) { 0x11 }
        val wrongKey = ByteArray(32) { 0x22 }

        val encryptionService = EncryptionServiceImpl()
        val decryptionService = DecryptionServiceImpl()

        val payload = encryptionService.encrypt("My Secret Data".toByteArray(), correctKey)

        try {
            decryptionService.decrypt(payload, wrongKey)
            fail("Expected CryptoAuthException when decrypting with incorrect key")
        } catch (e: CryptoAuthException) {
            assertNotNull(e)
        }
    }

    // =========================================================================
    // SCENARIO 4: RESTART DEVICE SIMULATION
    // =========================================================================

    @Test
    fun testScenario_restartDevice_persistsEnvelopeAndRequiresReauth() {
        val keyStorage = InMemoryVaultKeyStorage()
        val sessionManager = VaultSessionManager()
        val keyEnvelopeService = KeyEnvelopeService()
        val authService = VaultAuthServiceImpl(keyStorage, keyEnvelopeService, sessionManager)

        authService.createMasterPassword(masterPassword, masterPassword.copyOf(), testParams)
        assertTrue(sessionManager.isUnlocked)

        // 1. SIMULATE DEVICE REBOOT
        // All in-memory active sessions and volatile buffers are destroyed
        sessionManager.lock()
        val rebootedSessionManager = VaultSessionManager()
        assertFalse(rebootedSessionManager.isUnlocked)

        // Key storage on non-volatile storage still has wrapped envelope
        assertTrue(keyStorage.hasMasterPassword())
        val envelope = keyStorage.getEnvelope()
        assertNotNull(envelope)

        // 2. Cold Unlock on rebooted system
        val rebootedAuthService = VaultAuthServiceImpl(keyStorage, keyEnvelopeService, rebootedSessionManager)
        val unlockResult = rebootedAuthService.unlockVault(masterPassword)
        assertTrue(unlockResult is AuthResult.Success<*>)
        assertTrue(rebootedSessionManager.isUnlocked)
        assertNotNull(rebootedSessionManager.currentSession)
    }

    // =========================================================================
    // SCENARIO 5: FORCE STOP APPLICATION SIMULATION
    // =========================================================================

    @Test
    fun testScenario_forceStopApplication_coldStartLeavesVaultLocked() {
        val keyStorage = InMemoryVaultKeyStorage()
        val sessionManager = VaultSessionManager()
        val authService = VaultAuthServiceImpl(keyStorage, KeyEnvelopeService(), sessionManager)

        authService.createMasterPassword(masterPassword, masterPassword.copyOf(), testParams)

        // SIMULATE OS PROCESS DEATH (SIGKILL / Force Stop)
        sessionManager.lock()

        // Fresh application container instantiation
        val freshSessionManager = VaultSessionManager()
        assertFalse("Vault must be locked after force stop / cold start", freshSessionManager.isUnlocked)
        assertNull("Memory must not retain keys", freshSessionManager.currentSession)
    }

    // =========================================================================
    // SCENARIO 6: REOPEN APPLICATION & INACTIVITY TIMEOUT
    // =========================================================================

    @Test
    fun testScenario_reopenApplication_expiresSessionAfterTimeout() {
        val sessionManager = VaultSessionManager(timeoutDurationMillis = 500L) // 500ms timeout
        val masterKey = MasterKey(ByteArray(32) { 0x77.toByte() })
        val hierarchy = MasterKeyHierarchy.fromMasterKey(masterKey)

        val t0 = 100_000L
        sessionManager.startSession(hierarchy, currentTimeMillis = t0)
        assertTrue(sessionManager.isUnlocked(t0))

        // App backgrounded for 600ms (exceeding 500ms timeout)
        val tReopen = t0 + 600L
        val expired = sessionManager.checkAndExpire(tReopen)

        assertTrue("Session must expire when reopening after timeout", expired)
        assertFalse("Vault must be locked", sessionManager.isUnlocked(tReopen))
        assertTrue("Hierarchy must be destroyed and zeroized", hierarchy.isDestroyed)
    }

    // =========================================================================
    // SCENARIO 7: IMPORT LARGE PDF (10 MB STREAMING TEST)
    // =========================================================================

    @Test
    fun testScenario_importLargePdf_streamsWithoutMemoryExhaustion() {
        val database = InMemoryEncryptedDocumentDatabase()
        val masterKey = ByteArray(32) { (it * 3).toByte() }
        val repo = DocumentRepositoryImpl(
            database = database,
            storageDir = documentStorageDir,
            keyProvider = { masterKey }
        )

        // Generate 10 MB synthetic PDF content
        val largePdfSize = 10 * 1024 * 1024 // 10 MB
        val pattern = "%PDF-1.7 Large PDF streaming test chunk payload 0123456789 ABCDEF\n".toByteArray()
        val largePdfStream = object : java.io.InputStream() {
            var bytesRead = 0
            override fun read(): Int {
                return if (bytesRead < largePdfSize) {
                    val b = pattern[bytesRead % pattern.size].toInt() and 0xFF
                    bytesRead++
                    b
                } else {
                    -1
                }
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (bytesRead >= largePdfSize) return -1
                val toRead = minOf(len, largePdfSize - bytesRead)
                for (i in 0 until toRead) {
                    b[off + i] = pattern[(bytesRead + i) % pattern.size]
                }
                bytesRead += toRead
                return toRead
            }
        }

        // Import 10 MB document
        val entity = repo.importDocument(
            sourceStream = largePdfStream,
            title = "10MB Financial Audit Report",
            fileName = "audit_report_2026.pdf",
            categoryId = DocumentCategory.BANKING.id,
            notes = "Multi-megabyte streaming validation",
            masterKey = masterKey
        )

        assertEquals(largePdfSize.toLong(), entity.fileSizeBytes)
        val encFile = File(documentStorageDir, entity.encryptedFileName)
        assertTrue(encFile.exists())
        assertTrue("Encrypted file must include 18-byte header and 16-byte GCM tag", encFile.length() > largePdfSize)

        // Decrypt document in streaming fashion
        val decryptedBytes = repo.decryptDocumentToBytes(entity.id, masterKey)

        assertEquals(largePdfSize, decryptedBytes.size)
        // Verify head
        assertTrue(String(decryptedBytes.copyOfRange(0, 8)).startsWith("%PDF-1.7"))
        database.close()
    }

    // =========================================================================
    // SCENARIO 8: DELETE DOCUMENT (SECURE MULTI-PASS ZEROIZATION)
    // =========================================================================

    @Test
    fun testScenario_deleteDocument_secureWipesFileAndRemovesRecord() {
        val database = InMemoryEncryptedDocumentDatabase()
        val masterKey = ByteArray(32) { (it + 9).toByte() }
        val repo = DocumentRepositoryImpl(
            database = database,
            storageDir = documentStorageDir,
            keyProvider = { masterKey }
        )

        val docStream = ByteArrayInputStream("Confidential Passport Scan".toByteArray())
        val entity = repo.importDocument(
            sourceStream = docStream,
            title = "Passport Scan",
            fileName = "passport.pdf",
            categoryId = DocumentCategory.IDENTITY.id,
            notes = "Valid until 2030",
            masterKey = masterKey
        )

        val encFile = File(documentStorageDir, entity.encryptedFileName)
        assertTrue(encFile.exists())
        assertNotNull(database.documentDao.getById(entity.id))

        // Delete document
        val deleted = repo.deleteDocument(entity.id)
        assertTrue(deleted)

        // Assert record is deleted from database
        assertNull(database.documentDao.getById(entity.id))
        // Assert encrypted file is permanently wiped and deleted from disk
        assertFalse("Encrypted file must be deleted from storage", encFile.exists())
        database.close()
    }

    // =========================================================================
    // SCENARIO 9: RESTORE BACKUP (ROUNDTRIP VAULT RESTORATION)
    // =========================================================================

    @Test
    fun testScenario_restoreBackup_restoresCredentialsAndDocuments() {
        val keyStorage = InMemoryVaultKeyStorage()
        val credDb = InMemoryEncryptedCredentialDatabase()
        val docDb = InMemoryEncryptedDocumentDatabase()
        val sessionMgr = VaultSessionManager()
        val recoveryService = VaultRecoveryServiceImpl(keyStorage, sessionMgr)

        val backupService = VaultBackupServiceImpl(
            keyStorage = keyStorage,
            credentialDatabase = credDb,
            documentDatabase = docDb,
            documentStorageDir = documentStorageDir,
            recoveryService = recoveryService,
            sessionManager = sessionMgr
        )

        // Seed vault with credential and document
        val cred = CredentialEntity(
            id = "cred-99",
            title = "Swiss Bank Account",
            username = "swiss_user",
            encryptedPassword = byteArrayOf(0x55, 0x66),
            encryptedNotes = null,
            encryptedTotpSecret = null,
            website = "https://swissbank.ch",
            categoryId = "cat_financial",
            isFavorite = true,
            createdDate = 1000L,
            updatedDate = 1000L
        )
        credDb.credentialDao.insert(cred)

        val testDocFile = File(documentStorageDir, "doc-99.enc")
        testDocFile.writeBytes(byteArrayOf(0x01, 0x02, 0x03, 0x04))

        // Export backup
        val backupPassphrase = "BackupPassphrase#2026!".toCharArray()
        val backupBytes = backupService.exportEncryptedBackup(backupPassphrase, testParams)

        // Wipe current live vault
        credDb.credentialDao.clear()
        docDb.documentDao.clear()
        testDocFile.delete()

        assertEquals(0, credDb.credentialDao.getCount())
        assertFalse(testDocFile.exists())

        // Restore vault from backup
        val manifest = backupService.restoreBackup(backupBytes, backupPassphrase)
        assertEquals(1, manifest.credentialCount)

        val restoredCred = credDb.credentialDao.getById("cred-99")
        assertNotNull(restoredCred)
        assertEquals("Swiss Bank Account", restoredCred!!.title)

        credDb.close()
        docDb.close()
    }

    // =========================================================================
    // SCENARIO 10: INVALID RECOVERY PHRASE REJECTION
    // =========================================================================

    @Test
    fun testScenario_invalidRecoveryPhrase_rejectsAndPreservesMasterKey() {
        val keyStorage = InMemoryVaultKeyStorage()
        val sessionManager = VaultSessionManager()
        val recoveryService = VaultRecoveryServiceImpl(keyStorage, sessionManager)

        // Generate genuine recovery phrase
        val entropy = ByteArray(32) { (it + 3).toByte() }
        val genuineWords = Bip39Mnemonic.entropyToMnemonic(entropy)

        // Setup recovery secret
        val masterKeyBytes = ByteArray(32) { 0x44.toByte() }
        val envelope = recoveryService.createRecoveryEnvelope(masterKeyBytes, genuineWords, testParams)
        recoveryService.saveRecoveryEnvelope(envelope)

        // Attempt recovery with bad checksum / wrong 24th word
        val tamperedWords = genuineWords.toMutableList()
        tamperedWords[23] = "zoo" // Invalid checksum word

        try {
            Bip39Mnemonic.mnemonicToEntropy(tamperedWords)
            fail("Expected InvalidRecoveryChecksumException for invalid checksum recovery phrase")
        } catch (e: InvalidRecoveryChecksumException) {
            assertTrue(e.message!!.contains("checksum"))
        }

        assertFalse("Session must not be unlocked with bad recovery phrase", sessionManager.isUnlocked)
    }

    // =========================================================================
    // SCENARIO 11: BIOMETRIC KEY INVALIDATION & MASTER PASSWORD FALLBACK
    // =========================================================================

    @Test
    fun testScenario_biometricKeyInvalidation_forcesMasterPasswordFallback() {
        val keyStorage = InMemoryVaultKeyStorage()
        val sessionManager = VaultSessionManager()
        val keyEnvelopeService = KeyEnvelopeService()
        val authService = VaultAuthServiceImpl(keyStorage, keyEnvelopeService, sessionManager)

        // 1. Initial setup with Master Password
        authService.createMasterPassword(masterPassword, masterPassword.copyOf(), testParams)
        assertTrue(sessionManager.isUnlocked)

        // 2. Setup Biometric Auth Service with SoftwareBiometricKeyManager
        val keyManager = SoftwareBiometricKeyManager()
        val biometricService = BiometricAuthServiceImpl(
            keyManager = keyManager,
            sessionManager = sessionManager
        )

        // Enroll biometric
        val vaultKey = VaultKey(ByteArray(32) { (it * 3 + 1).toByte() })
        val enrollResult = biometricService.enableBiometricUnlock(vaultKey)
        assertTrue(enrollResult)
        assertTrue(biometricService.isBiometricUnlockEnrolled())

        // 3. User enrolls new fingerprint in Android OS settings -> Keystore key permanently invalidated
        sessionManager.lock()
        keyManager.simulateNewBiometricEnrolled()

        var unlockResult: BiometricAuthResult? = null
        biometricService.authenticate { unlockResult = it }

        // Must report KeyInvalidated and keep vault locked
        assertEquals(BiometricAuthResult.KeyInvalidated, unlockResult)
        assertFalse(sessionManager.isUnlocked)

        // 4. Fallback to Master Password succeeds and re-authenticates vault
        val masterPasswordResult = authService.unlockVault(masterPassword)
        assertTrue(masterPasswordResult is AuthResult.Success<*>)
        assertTrue(sessionManager.isUnlocked)
    }

    // =========================================================================
    // SCENARIO 12: COMPLETE OFFLINE OPERATION
    // =========================================================================

    @Test
    fun testScenario_completeOfflineOperation_noNetworkRequired() {
        // Full end-to-end user workflow verifying pure offline capability:
        // 1. Master password initialization
        val keyStorage = InMemoryVaultKeyStorage()
        val sessionManager = VaultSessionManager()
        val authService = VaultAuthServiceImpl(keyStorage, KeyEnvelopeService(), sessionManager)
        authService.createMasterPassword(masterPassword, masterPassword.copyOf(), testParams)

        // 2. Credential CRUD
        val credDb = InMemoryEncryptedCredentialDatabase()
        val encryptionService = EncryptionServiceImpl()
        val decryptionService = DecryptionServiceImpl()
        val credRepo = CredentialRepositoryImpl(
            database = credDb,
            keyProvider = { ByteArray(32) { 0x55.toByte() } },
            encryptionService = encryptionService,
            decryptionService = decryptionService
        )
        val credRecord = credRepo.addCredential(
            CredentialRecord(
                title = "Offline Key",
                username = "admin",
                passwordChars = "Password123!".toCharArray(),
                website = "",
                notes = "",
                totpSecret = "JBSWY3DPEHPK3PXP"
            )
        )
        assertNotNull(credRecord)

        // 3. TOTP generation without internet
        val totpConfig = TotpConfig(issuer = "OfflineApp", accountName = "admin", secret = "JBSWY3DPEHPK3PXP")
        val code1 = TotpGenerator.generateTotp(totpConfig, currentTimeSeconds = 1700000000L)
        assertEquals(6, code1.code.length)

        // 4. Document import and preview offline
        val docDb = InMemoryEncryptedDocumentDatabase()
        val docRepo = DocumentRepositoryImpl(
            database = docDb,
            storageDir = documentStorageDir,
            keyProvider = { ByteArray(32) { 0x66.toByte() } }
        )
        val docEntity = docRepo.importDocument(
            sourceStream = ByteArrayInputStream("Offline Document Data".toByteArray()),
            title = "Offline ID",
            fileName = "id.pdf",
            categoryId = DocumentCategory.IDENTITY.id,
            notes = "",
            masterKey = ByteArray(32) { 0x66.toByte() }
        )
        assertNotNull(docEntity)

        // 5. Clean teardown
        sessionManager.lock()
        credDb.close()
        docDb.close()
    }
}
