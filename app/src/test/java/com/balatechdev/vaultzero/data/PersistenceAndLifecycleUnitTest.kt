package com.balatechdev.vaultzero.data

import com.balatechdev.vaultzero.data.database.CorruptedDatabaseException
import com.balatechdev.vaultzero.data.database.PersistentEncryptedCredentialDatabase
import com.balatechdev.vaultzero.data.database.PersistentEncryptedDocumentDatabase
import com.balatechdev.vaultzero.data.model.CredentialEntity
import com.balatechdev.vaultzero.data.model.DocumentCategory
import com.balatechdev.vaultzero.data.model.DocumentEntity
import com.balatechdev.vaultzero.security.auth.AndroidPersistentVaultKeyStorage
import com.balatechdev.vaultzero.security.auth.Argon2idParameters
import com.balatechdev.vaultzero.security.auth.AuthError
import com.balatechdev.vaultzero.security.auth.AuthResult
import com.balatechdev.vaultzero.security.auth.CorruptedStorageException
import com.balatechdev.vaultzero.security.auth.KeyEnvelopeService
import com.balatechdev.vaultzero.security.auth.VaultAuthServiceImpl
import com.balatechdev.vaultzero.security.auth.VaultSessionManager
import kotlin.experimental.xor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import javax.crypto.spec.SecretKeySpec

class PersistenceAndLifecycleUnitTest {

    private lateinit var tempDir: File
    private val testKeystoreKey = SecretKeySpec(ByteArray(32) { (it * 3 + 5).toByte() }, "AES")
    private val fastParams = Argon2idParameters.Test

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("vaultzero_test_dir").toFile()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun createPersistentStorage(): AndroidPersistentVaultKeyStorage {
        return AndroidPersistentVaultKeyStorage(
            context = null,
            storageFileName = "vault_master.env",
            customSecretKey = testKeystoreKey,
            customBaseDir = tempDir
        )
    }

    @Test
    fun test1_createMasterPassword() {
        val storage = createPersistentStorage()
        val sessionMgr = VaultSessionManager()
        val authService = VaultAuthServiceImpl(
            keyStorage = storage,
            sessionManager = sessionMgr
        )

        assertFalse(authService.isInitialized())

        val password = "StrongMasterPass123!@#".toCharArray()
        val confirm = "StrongMasterPass123!@#".toCharArray()

        val result = authService.createMasterPassword(password, confirm, fastParams)
        assertTrue("Creation must succeed", result is AuthResult.Success)
        assertTrue("Vault must now report initialized", authService.isInitialized())
        assertTrue("Session must be unlocked", sessionMgr.isUnlocked)
    }

    @Test
    fun test2_persistEnvelope() {
        val storage = createPersistentStorage()
        val envelopeService = KeyEnvelopeService()
        val password = "SecureEnvelopeKey2026!@#".toCharArray()

        val (envelope, _) = envelopeService.createEnvelope(password, fastParams)
        storage.saveEnvelope(envelope)

        val targetFile = File(tempDir, "vault_master.env")
        assertTrue("Envelope file must exist on disk", targetFile.exists())
        assertTrue("File size must exceed minimum container header", targetFile.length() > 38)
    }

    @Test
    fun test3_and_4_simulateProcessRestartAndRestoreEnvelope() {
        val password = "ProcessRestartPass2026!#$".toCharArray()

        // Phase 1: Initialize in original process
        val storage1 = createPersistentStorage()
        val authService1 = VaultAuthServiceImpl(keyStorage = storage1, sessionManager = VaultSessionManager())
        val initResult = authService1.createMasterPassword(password.copyOf(), password.copyOf(), fastParams)
        assertTrue(initResult is AuthResult.Success)

        // Phase 2: Simulate complete process death (drop all references, clear memory)
        val storage2 = createPersistentStorage()
        val sessionMgr2 = VaultSessionManager()
        val authService2 = VaultAuthServiceImpl(keyStorage = storage2, sessionManager = sessionMgr2)

        // Verify state after cold restart
        assertTrue("Storage must recognize existing vault on restart", storage2.hasMasterPassword())
        assertTrue("AuthService must report initialized on restart", authService2.isInitialized())
        assertFalse("Session must start locked on restart", sessionMgr2.isUnlocked)

        val restoredEnvelope = storage2.getEnvelope()
        assertNotNull("Envelope must be safely restored from disk", restoredEnvelope)
        assertEquals(fastParams.iterations, restoredEnvelope!!.iterations)
    }

    @Test
    fun test5_unlockUsingCorrectPassword() {
        val password = "CorrectPasswordToUnlock!123".toCharArray()

        val storage1 = createPersistentStorage()
        val authService1 = VaultAuthServiceImpl(keyStorage = storage1, sessionManager = VaultSessionManager())
        authService1.createMasterPassword(password.copyOf(), password.copyOf(), fastParams)

        // Cold restart simulation
        val storage2 = createPersistentStorage()
        val sessionMgr2 = VaultSessionManager()
        val authService2 = VaultAuthServiceImpl(keyStorage = storage2, sessionManager = sessionMgr2)

        val unlockResult = authService2.unlockVault(password.copyOf())
        assertTrue("Unlocking with correct password must succeed", unlockResult is AuthResult.Success)
        assertTrue("Vault session must become unlocked", sessionMgr2.isUnlocked)
        assertNotNull("Active session key hierarchy must exist", sessionMgr2.currentSession?.hierarchy)
    }

    @Test
    fun test6_rejectIncorrectPassword() {
        val correctPassword = "CorrectMasterPassword1!".toCharArray()
        val wrongPassword = "WrongMasterPassword99!".toCharArray()

        val storage1 = createPersistentStorage()
        val authService1 = VaultAuthServiceImpl(keyStorage = storage1, sessionManager = VaultSessionManager())
        authService1.createMasterPassword(correctPassword.copyOf(), correctPassword.copyOf(), fastParams)

        // Cold restart
        val storage2 = createPersistentStorage()
        val sessionMgr2 = VaultSessionManager()
        val authService2 = VaultAuthServiceImpl(keyStorage = storage2, sessionManager = sessionMgr2)

        val result = authService2.unlockVault(wrongPassword)
        assertTrue("Unlock with wrong password must fail", result is AuthResult.Failure)
        assertEquals(AuthError.InvalidCredentials, (result as AuthResult.Failure).error)
        assertFalse("Session must remain locked", sessionMgr2.isUnlocked)
    }

    @Test
    fun test7_detectCorruptedStorage() {
        val password = "DetectCorruptedStorage1!".toCharArray()

        val storage = createPersistentStorage()
        val authService = VaultAuthServiceImpl(keyStorage = storage, sessionManager = VaultSessionManager())
        authService.createMasterPassword(password.copyOf(), password.copyOf(), fastParams)

        val envFile = File(tempDir, "vault_master.env")
        assertTrue(envFile.exists())

        // Corrupt bytes in the ciphertext/tag region
        val fileBytes = envFile.readBytes()
        fileBytes[fileBytes.size - 5] = (fileBytes[fileBytes.size - 5] xor 0xFF.toByte())
        envFile.writeBytes(fileBytes)

        val restartStorage = createPersistentStorage()
        try {
            restartStorage.getEnvelope()
            fail("Expected CorruptedStorageException on tampered storage")
        } catch (e: CorruptedStorageException) {
            // Expected
            assertTrue(e.message!!.contains("authentication failed") || e.message!!.contains("corrupt"))
        }
    }

    @Test
    fun test8_verifyCredentialPersistence() {
        val dbk = ByteArray(32) { (it * 7 + 11).toByte() }
        val dbFile = File(tempDir, "vaultzero_credentials.encdb")

        // 1. Process 1: Open and write credentials
        val db1 = PersistentEncryptedCredentialDatabase(
            databaseFile = dbFile,
            keyProvider = { dbk }
        )

        val entity1 = CredentialEntity(
            id = "cred_1",
            title = "ProtonMail",
            username = "alice@proton.me",
            encryptedPassword = byteArrayOf(1, 2, 3, 4, 5, 6),
            encryptedNotes = byteArrayOf(7, 8, 9),
            website = "https://proton.me",
            categoryId = "cat_email",
            isFavorite = true
        )
        val entity2 = CredentialEntity(
            id = "cred_2",
            title = "GitHub",
            username = "alice-dev",
            encryptedPassword = byteArrayOf(10, 11, 12, 13),
            website = "https://github.com",
            categoryId = "cat_dev"
        )

        db1.credentialDao.insert(entity1)
        db1.credentialDao.insert(entity2)
        assertEquals(2, db1.credentialDao.getCount())

        // Lock / close DB
        db1.close()
        assertFalse(db1.isOpen())
        assertTrue("Encrypted database file must exist on disk", dbFile.exists())

        // 2. Process 2: Reopen on cold start with same DBK
        val db2 = PersistentEncryptedCredentialDatabase(
            databaseFile = dbFile,
            keyProvider = { dbk }
        )
        assertTrue(db2.isOpen())
        assertEquals(2, db2.credentialDao.getCount())

        val restored1 = db2.credentialDao.getById("cred_1")
        assertNotNull(restored1)
        assertEquals("ProtonMail", restored1!!.title)
        assertEquals("alice@proton.me", restored1.username)
        assertTrue(restored1.encryptedPassword.contentEquals(entity1.encryptedPassword))
        assertTrue(restored1.isFavorite)

        val restored2 = db2.credentialDao.getById("cred_2")
        assertNotNull(restored2)
        assertEquals("GitHub", restored2!!.title)

        // Test update and delete persist across close
        val updated2 = restored2.copy(title = "GitHub Enterprise")
        db2.credentialDao.update(updated2)
        db2.credentialDao.deleteById("cred_1")
        db2.close()

        // 3. Process 3: Reopen again
        val db3 = PersistentEncryptedCredentialDatabase(
            databaseFile = dbFile,
            keyProvider = { dbk }
        )
        assertEquals(1, db3.credentialDao.getCount())
        assertEquals("GitHub Enterprise", db3.credentialDao.getById("cred_2")?.title)
        db3.close()
    }

    @Test
    fun test9_verifyDocumentPersistence() {
        val dbk = ByteArray(32) { (it * 5 + 9).toByte() }
        val docDbFile = File(tempDir, "vaultzero_documents.encdb")

        // 1. Process 1: Open and write documents
        val docDb1 = PersistentEncryptedDocumentDatabase(
            databaseFile = docDbFile,
            keyProvider = { dbk }
        )

        val doc1 = DocumentEntity(
            id = "doc_pass",
            title = "Passport Scan",
            fileName = "passport.pdf",
            fileExtension = "pdf",
            mimeType = "application/pdf",
            fileSizeBytes = 1024 * 512,
            categoryId = DocumentCategory.IDENTITY.id,
            isFavorite = true
        )
        val doc2 = DocumentEntity(
            id = "doc_tax",
            title = "Tax Return 2025",
            fileName = "taxes.pdf",
            fileExtension = "pdf",
            mimeType = "application/pdf",
            fileSizeBytes = 2048 * 1024,
            categoryId = DocumentCategory.BANKING.id
        )

        docDb1.documentDao.insert(doc1)
        docDb1.documentDao.insert(doc2)
        assertEquals(2, docDb1.documentDao.getCount())
        docDb1.close()

        assertTrue(docDbFile.exists())

        // 2. Process 2: Reopen after restart
        val docDb2 = PersistentEncryptedDocumentDatabase(
            databaseFile = docDbFile,
            keyProvider = { dbk }
        )
        assertEquals(2, docDb2.documentDao.getCount())

        val restoredDoc1 = docDb2.documentDao.getById("doc_pass")
        assertNotNull(restoredDoc1)
        assertEquals("Passport Scan", restoredDoc1!!.title)
        assertEquals("passport.pdf", restoredDoc1.fileName)
        assertTrue(restoredDoc1.isFavorite)

        // Delete doc1
        docDb2.documentDao.deleteById("doc_pass")
        docDb2.close()

        // 3. Process 3: Reopen and verify deletion persisted
        val docDb3 = PersistentEncryptedDocumentDatabase(
            databaseFile = docDbFile,
            keyProvider = { dbk }
        )
        assertEquals(1, docDb3.documentDao.getCount())
        assertEquals(null, docDb3.documentDao.getById("doc_pass"))
        assertEquals("Tax Return 2025", docDb3.documentDao.getById("doc_tax")?.title)
        docDb3.close()
    }

    @Test(expected = CorruptedDatabaseException::class)
    fun test10_detectCorruptedDatabase() {
        val dbk = ByteArray(32) { (it * 7).toByte() }
        val dbFile = File(tempDir, "vaultzero_credentials.encdb")

        val db = PersistentEncryptedCredentialDatabase(
            databaseFile = dbFile,
            keyProvider = { dbk }
        )
        db.credentialDao.insert(CredentialEntity(id = "1", title = "T", username = "U", encryptedPassword = byteArrayOf(1)))
        db.close()

        // Corrupt database file bytes
        val bytes = dbFile.readBytes()
        bytes[bytes.size - 10] = (bytes[bytes.size - 10] xor 0xAA.toByte())
        dbFile.writeBytes(bytes)

        val reopened = PersistentEncryptedCredentialDatabase(
            databaseFile = dbFile,
            keyProvider = { dbk }
        )
        reopened.credentialDao.getAll() // Must fail tag verification and throw CorruptedDatabaseException
    }
}
