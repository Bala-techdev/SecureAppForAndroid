package com.balatechdev.vaultzero.data.backup

import com.balatechdev.vaultzero.data.database.EncryptedCredentialDatabase
import com.balatechdev.vaultzero.data.database.EncryptedDocumentDatabase
import com.balatechdev.vaultzero.data.database.InMemoryEncryptedCredentialDatabase
import com.balatechdev.vaultzero.data.database.InMemoryEncryptedDocumentDatabase
import com.balatechdev.vaultzero.data.model.CredentialEntity
import com.balatechdev.vaultzero.data.model.DocumentEntity
import com.balatechdev.vaultzero.security.auth.Argon2idParameters
import com.balatechdev.vaultzero.security.auth.InMemoryVaultKeyStorage
import com.balatechdev.vaultzero.security.auth.KeyEnvelopeService
import com.balatechdev.vaultzero.security.auth.MasterKey
import com.balatechdev.vaultzero.security.auth.MasterKeyHierarchy
import com.balatechdev.vaultzero.security.auth.VaultAuthServiceImpl
import com.balatechdev.vaultzero.security.auth.VaultSessionManager
import com.balatechdev.vaultzero.security.recovery.RecoveryKeyEnvelope
import com.balatechdev.vaultzero.security.recovery.VaultRecoveryServiceImpl
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.UUID

class VaultBackupUnitTest {

    private lateinit var tempDir: File
    private lateinit var documentStorageDir: File

    private lateinit var keyStorage: InMemoryVaultKeyStorage
    private lateinit var sessionManager: VaultSessionManager
    private lateinit var recoveryService: VaultRecoveryServiceImpl
    private lateinit var credentialDatabase: EncryptedCredentialDatabase
    private lateinit var documentDatabase: EncryptedDocumentDatabase

    private lateinit var backupService: VaultBackupServiceImpl

    private val testParams = Argon2idParameters.Test
    private val testPassphrase = "BackupSecretPassword#2026".toCharArray()

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("vault_backup_test_").toFile()
        documentStorageDir = File(tempDir, "vault_documents").apply { mkdirs() }

        keyStorage = InMemoryVaultKeyStorage()
        sessionManager = VaultSessionManager()
        recoveryService = VaultRecoveryServiceImpl(keyStorage, sessionManager)

        credentialDatabase = InMemoryEncryptedCredentialDatabase()
        documentDatabase = InMemoryEncryptedDocumentDatabase()

        backupService = VaultBackupServiceImpl(
            keyStorage = keyStorage,
            credentialDatabase = credentialDatabase,
            documentDatabase = documentDatabase,
            documentStorageDir = documentStorageDir,
            recoveryService = recoveryService,
            sessionManager = sessionManager
        )
    }

    @After
    fun tearDown() {
        sessionManager.lock()
        credentialDatabase.close()
        documentDatabase.close()
        tempDir.deleteRecursively()
    }

    private fun createSampleCredential(
        id: String = UUID.randomUUID().toString(),
        title: String = "Test Account",
        username: String = "alice@example.com"
    ): CredentialEntity {
        return CredentialEntity(
            id = id,
            title = title,
            username = username,
            encryptedPassword = byteArrayOf(0x10, 0x20, 0x30, 0x40),
            encryptedNotes = byteArrayOf(0x01, 0x02),
            encryptedTotpSecret = byteArrayOf(0x0A, 0x0B),
            website = "https://example.com",
            categoryId = "cat_general",
            isFavorite = true,
            createdDate = 1700000000000L,
            updatedDate = 1700000050000L
        )
    }

    private fun createSampleDocument(
        id: String = UUID.randomUUID().toString(),
        title: String = "Passport Copy",
        encFileName: String = "passport_$id.enc",
        fileContent: ByteArray = "Simulated encrypted document content AES-256".toByteArray()
    ): DocumentEntity {
        val file = File(documentStorageDir, encFileName)
        file.writeBytes(fileContent)

        return DocumentEntity(
            id = id,
            title = title,
            fileName = "passport.pdf",
            fileExtension = "pdf",
            mimeType = "application/pdf",
            fileSizeBytes = fileContent.size.toLong(),
            categoryId = "Identity",
            encryptedFileName = encFileName,
            isFavorite = false,
            notes = "Stored safely",
            createdDate = 1700000100000L,
            updatedDate = 1700000200000L
        )
    }

    // =========================================================================
    // 1. ROUNDTRIP EXPORT AND RESTORE
    // =========================================================================

    @Test
    fun testExportAndRestore_roundtripSuccess() {
        // 1. Setup sample data
        val cred1 = createSampleCredential(title = "GitHub", username = "octocat")
        val cred2 = createSampleCredential(title = "Google", username = "test@gmail.com")
        credentialDatabase.credentialDao.insert(cred1)
        credentialDatabase.credentialDao.insert(cred2)

        val doc1 = createSampleDocument(title = "National ID", fileContent = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte()))
        val doc2 = createSampleDocument(title = "Tax Return", fileContent = byteArrayOf(0x11, 0x22, 0x33, 0x44))
        documentDatabase.documentDao.insert(doc1)
        documentDatabase.documentDao.insert(doc2)

        // Mock envelopes
        val masterEnv = com.balatechdev.vaultzero.security.auth.MasterKeyEnvelope(
            salt = byteArrayOf(1, 2, 3),
            iv = byteArrayOf(4, 5, 6),
            wrappedKey = byteArrayOf(7, 8, 9, 10),
            iterations = 1,
            memoryKiB = 1024,
            parallelism = 1
        )
        keyStorage.saveEnvelope(masterEnv)

        val recEnv = RecoveryKeyEnvelope(
            salt = byteArrayOf(9, 8, 7),
            nonce = byteArrayOf(6, 5, 4),
            wrappedKey = byteArrayOf(3, 2, 1),
            iterations = 1,
            memoryKiB = 1024,
            parallelism = 1
        )
        recoveryService.saveRecoveryEnvelope(recEnv)

        // 2. Export Encrypted Backup
        val backupBytes = backupService.exportEncryptedBackup(testPassphrase, testParams)
        assertTrue(backupBytes.isNotEmpty())

        // 3. Clear all live data
        credentialDatabase.credentialDao.clear()
        documentDatabase.documentDao.clear()
        documentStorageDir.listFiles()?.forEach { it.delete() }
        keyStorage.clear()

        assertEquals(0, credentialDatabase.credentialDao.getCount())
        assertEquals(0, documentDatabase.documentDao.getCount())
        assertEquals(0, documentStorageDir.listFiles()?.size ?: 0)

        // 4. Restore from backup
        val manifest = backupService.restoreBackup(backupBytes, testPassphrase)

        assertEquals(1, manifest.formatVersion)
        assertEquals(2, manifest.credentialCount)
        assertEquals(2, manifest.documentCount)

        // 5. Verify restored credentials
        assertEquals(2, credentialDatabase.credentialDao.getCount())
        val restoredCred1 = credentialDatabase.credentialDao.getById(cred1.id)
        assertNotNull(restoredCred1)
        assertEquals(cred1.title, restoredCred1!!.title)
        assertArrayEquals(cred1.encryptedPassword, restoredCred1.encryptedPassword)

        // 6. Verify restored documents and files
        assertEquals(2, documentDatabase.documentDao.getCount())
        val restoredDoc1 = documentDatabase.documentDao.getById(doc1.id)
        assertNotNull(restoredDoc1)
        assertEquals(doc1.title, restoredDoc1!!.title)

        val restoredFile1 = File(documentStorageDir, doc1.encryptedFileName)
        assertTrue(restoredFile1.exists())
        assertArrayEquals(byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte()), restoredFile1.readBytes())

        // 7. Verify restored envelopes
        assertNotNull(keyStorage.getEnvelope())
        assertArrayEquals(masterEnv.wrappedKey, keyStorage.getEnvelope()!!.wrappedKey)
        assertNotNull(recoveryService.getRecoveryEnvelope())
        assertArrayEquals(recEnv.wrappedKey, recoveryService.getRecoveryEnvelope()!!.wrappedKey)
    }

    // =========================================================================
    // 2. CORRUPTED CIPHERTEXT DETECTION
    // =========================================================================

    @Test
    fun testCorruptedCiphertext_triggersCorruptedBackupException() {
        val cred = createSampleCredential()
        credentialDatabase.credentialDao.insert(cred)

        val backupBytes = backupService.exportEncryptedBackup(testPassphrase, testParams)

        // Mutate a byte in the encrypted payload portion (after header + salt + nonce)
        val corruptedBytes = backupBytes.copyOf()
        corruptedBytes[corruptedBytes.size - 5] = (corruptedBytes[corruptedBytes.size - 5].toInt() xor 0xFF).toByte()

        try {
            backupService.restoreBackup(corruptedBytes, testPassphrase)
            fail("Expected CorruptedBackupException for modified ciphertext")
        } catch (e: CorruptedBackupException) {
            assertTrue(e.message!!.contains("authentication tag verification failed") || e.message!!.contains("Failed to decrypt"))
        }
    }

    // =========================================================================
    // 3. INCORRECT PASSPHRASE REJECTION
    // =========================================================================

    @Test
    fun testIncorrectPassphrase_triggersCorruptedBackupException() {
        val cred = createSampleCredential(title = "Existing Record")
        credentialDatabase.credentialDao.insert(cred)

        val backupBytes = backupService.exportEncryptedBackup(testPassphrase, testParams)

        val wrongPassphrase = "CompletelyWrongPassphrase!".toCharArray()
        try {
            backupService.restoreBackup(backupBytes, wrongPassphrase)
            fail("Expected CorruptedBackupException for incorrect passphrase")
        } catch (e: CorruptedBackupException) {
            assertTrue(e.message!!.contains("authentication tag verification failed") || e.message!!.contains("Failed to decrypt"))
        }

        // Verify existing vault remains intact
        assertEquals(1, credentialDatabase.credentialDao.getCount())
        assertEquals("Existing Record", credentialDatabase.credentialDao.getAll().first().title)
    }

    // =========================================================================
    // 4. INVALID HEADER DETECTION
    // =========================================================================

    @Test
    fun testInvalidHeader_triggersInvalidBackupHeaderException() {
        val invalidBytes = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x01) + ByteArray(100)

        try {
            backupService.restoreBackup(invalidBytes, testPassphrase)
            fail("Expected InvalidBackupHeaderException for bad magic bytes")
        } catch (e: InvalidBackupHeaderException) {
            assertNotNull(e)
        }
    }

    // =========================================================================
    // 5. INCOMPATIBLE VERSION REJECTION
    // =========================================================================

    @Test
    fun testIncompatibleVersion_triggersIncompatibleBackupVersionException() {
        val backupBytes = backupService.exportEncryptedBackup(testPassphrase, testParams)

        // Overwrite version byte (index 5) with future version 99
        val futureVersionBytes = backupBytes.copyOf()
        futureVersionBytes[5] = 99.toByte()

        try {
            backupService.restoreBackup(futureVersionBytes, testPassphrase)
            fail("Expected IncompatibleBackupVersionException for future version")
        } catch (e: IncompatibleBackupVersionException) {
            assertEquals(99, e.fileVersion)
            assertEquals(1, e.maxSupportedVersion)
        }
    }

    // =========================================================================
    // 6. CHECKSUM VERIFICATION
    // =========================================================================

    @Test
    fun testBackupPackageChecksumCalculation() {
        val pkg = backupService.createBackupPackage()
        val calculated = pkg.calculateChecksum()
        assertTrue(calculated.isNotBlank())
        assertEquals(64, calculated.length) // SHA-256 hex string
        assertEquals(calculated, pkg.manifest.checksumSha256)
    }

    // =========================================================================
    // 7. PREVENT PARTIAL RESTORATION (ATOMIC ROLLBACK)
    // =========================================================================

    @Test
    fun testPreventPartialRestoration_onCorruptedData() {
        // Pre-populate original vault
        val originalCred = createSampleCredential(title = "Original Important Item")
        credentialDatabase.credentialDao.insert(originalCred)
        val originalDoc = createSampleDocument(title = "Original Doc", encFileName = "orig.enc")
        documentDatabase.documentDao.insert(originalDoc)

        val origDocFile = File(documentStorageDir, "orig.enc")
        assertTrue(origDocFile.exists())

        // Feed corrupted backup
        val corruptedBytes = byteArrayOf(0x56, 0x5A, 0x42, 0x41, 0x4B, 0x01) + ByteArray(20)

        try {
            backupService.restoreBackup(corruptedBytes, testPassphrase)
            fail("Should fail restore on corrupted file")
        } catch (e: Exception) {
            // Expected
        }

        // Live state must remain 100% unaltered
        assertEquals(1, credentialDatabase.credentialDao.getCount())
        assertEquals("Original Important Item", credentialDatabase.credentialDao.getAll().first().title)
        assertEquals(1, documentDatabase.documentDao.getCount())
        assertTrue(origDocFile.exists())
    }

    // =========================================================================
    // 8. DEVICE MIGRATION SIMULATION
    // =========================================================================

    @Test
    fun testMigrationToNewDevice_unlockWithMasterPassword() {
        // ------------------ DEVICE A ------------------
        val deviceAPassword = "MasterSecretPassword#2026".toCharArray()
        val keyEnvelopeService = KeyEnvelopeService()
        val authServiceA = VaultAuthServiceImpl(keyStorage, keyEnvelopeService, sessionManager)

        // Setup master password on Device A
        val initResult = authServiceA.createMasterPassword(
            password = deviceAPassword,
            confirmation = deviceAPassword.copyOf(),
            params = testParams
        )
        assertTrue(initResult is com.balatechdev.vaultzero.security.auth.AuthResult.Success<*>)

        // Insert item on Device A
        val credA = createSampleCredential(title = "Device A Credential")
        credentialDatabase.credentialDao.insert(credA)

        // Export backup from Device A with a user-chosen backup passphrase
        val migrationPassphrase = "MigrationPassword!999".toCharArray()
        val backupBytes = backupService.exportEncryptedBackup(migrationPassphrase, testParams)

        // ------------------ DEVICE B ------------------
        // Device B has fresh, clean storage
        val keyStorageB = InMemoryVaultKeyStorage()
        val sessionManagerB = VaultSessionManager()
        val credDbB = InMemoryEncryptedCredentialDatabase()
        val docDbB = InMemoryEncryptedDocumentDatabase()
        val docDirB = File(tempDir, "device_b_docs").apply { mkdirs() }

        val backupServiceB = VaultBackupServiceImpl(
            keyStorage = keyStorageB,
            credentialDatabase = credDbB,
            documentDatabase = docDbB,
            documentStorageDir = docDirB,
            sessionManager = sessionManagerB
        )

        // Restore onto Device B using only migration passphrase
        val restoredManifest = backupServiceB.restoreBackup(backupBytes, migrationPassphrase)
        assertEquals(1, restoredManifest.credentialCount)

        // Now verify Device B can unlock using the original master password!
        val authServiceB = VaultAuthServiceImpl(keyStorageB, keyEnvelopeService, sessionManagerB)
        assertTrue(authServiceB.isInitialized())

        val unlockResult = authServiceB.unlockVault(deviceAPassword)
        assertTrue(unlockResult is com.balatechdev.vaultzero.security.auth.AuthResult.Success<*>)
        assertTrue(sessionManagerB.isUnlocked())

        // Verify restored credential is accessible on Device B
        val credsOnB = credDbB.credentialDao.getAll()
        assertEquals(1, credsOnB.size)
        assertEquals("Device A Credential", credsOnB.first().title)
    }

    // =========================================================================
    // 9. EMPTY VAULT BACKUP & RESTORE
    // =========================================================================

    @Test
    fun testEmptyVault_backupAndRestore_succeeds() {
        // Empty vault
        val backupBytes = backupService.exportEncryptedBackup(testPassphrase, testParams)
        assertTrue(backupBytes.isNotEmpty())

        val manifest = backupService.restoreBackup(backupBytes, testPassphrase)
        assertEquals(0, manifest.credentialCount)
        assertEquals(0, manifest.documentCount)
    }

    // =========================================================================
    // 10. VERIFY BACKUP HEADER FAST-CHECK
    // =========================================================================

    @Test
    fun testVerifyBackupHeader() {
        val backupBytes = backupService.exportEncryptedBackup(testPassphrase, testParams)
        val manifest = backupService.verifyBackupHeader(ByteArrayInputStream(backupBytes))

        assertEquals(BackupManifest.CURRENT_VERSION, manifest.formatVersion)
    }
}
