package com.balatechdev.vaultzero.security

import com.balatechdev.vaultzero.data.database.InMemoryEncryptedCredentialDatabase
import com.balatechdev.vaultzero.data.database.InMemoryEncryptedDocumentDatabase
import com.balatechdev.vaultzero.data.model.CredentialEntity
import com.balatechdev.vaultzero.data.repository.DocumentRepositoryImpl
import com.balatechdev.vaultzero.security.auth.HkdfSha256
import com.balatechdev.vaultzero.security.auth.MasterKey
import com.balatechdev.vaultzero.security.auth.MasterKeyHierarchy
import com.balatechdev.vaultzero.security.auth.VaultSessionManager
import com.balatechdev.vaultzero.security.clipboard.InMemorySecureClipboard
import com.balatechdev.vaultzero.security.crypto.DocumentCryptoEngine
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

class SecurityHardeningUnitTest {

    private lateinit var tempDir: File
    private lateinit var storageDir: File

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("security_hardening_test_").toFile()
        storageDir = File(tempDir, "vault_documents").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // =========================================================================
    // 1. NO PLAINTEXT CREDENTIALS IN DATABASE
    // =========================================================================

    @Test
    fun testNoPlaintextCredentialsInDatabase() {
        val database = InMemoryEncryptedCredentialDatabase()
        val plainPassword = "SuperSecretPassword123!"

        // Emulate encrypted credential insertion
        val encryptedPassword = byteArrayOf(0x0A, 0x1B, 0x2C, 0x3D) // AES ciphertext
        val entity = CredentialEntity(
            id = "test-1",
            title = "Personal Bank",
            username = "alice",
            encryptedPassword = encryptedPassword,
            encryptedNotes = null,
            encryptedTotpSecret = null,
            website = "https://bank.example.com",
            categoryId = "cat_financial",
            isFavorite = false,
            createdDate = 1000L,
            updatedDate = 1000L
        )
        database.credentialDao.insert(entity)

        val retrieved = database.credentialDao.getById("test-1")
        assertNotNull(retrieved)
        // Ensure no plaintext string matches
        assertFalse(String(retrieved!!.encryptedPassword).contains(plainPassword))
        assertArrayEquals(encryptedPassword, retrieved.encryptedPassword)
        database.close()
    }

    // =========================================================================
    // 2. NO PLAINTEXT DOCUMENTS ON DISK
    // =========================================================================

    @Test
    fun testNoPlaintextDocumentsOnDisk() {
        val database = InMemoryEncryptedDocumentDatabase()
        val masterKey = ByteArray(32) { (it + 5).toByte() }
        val repo = DocumentRepositoryImpl(
            database = database,
            storageDir = storageDir,
            keyProvider = { masterKey }
        )

        val plainText = "TOP SECRET MILITARY CONTRACT CONFIDENTIAL 2026"
        val input = ByteArrayInputStream(plainText.toByteArray(Charsets.UTF_8))

        val entity = repo.importDocument(
            sourceStream = input,
            title = "Classified",
            fileName = "contract.pdf",
            categoryId = "Personal",
            notes = "",
            masterKey = masterKey
        )

        // Storage directory must contain ONLY the .enc file
        val files = storageDir.listFiles()
        assertNotNull(files)
        assertEquals(1, files!!.size)
        assertTrue(files[0].name.endsWith(".enc"))

        // The disk bytes must NEVER contain the plaintext
        val onDiskBytes = files[0].readBytes()
        assertFalse(String(onDiskBytes).contains(plainText))
        assertTrue(onDiskBytes.copyOfRange(0, 5).contentEquals(DocumentCryptoEngine.MAGIC_HEADER))
        database.close()
    }

    // =========================================================================
    // 3. FILE IMPORT PATH TRAVERSAL SANITIZATION
    // =========================================================================

    @Test
    fun testDocumentPathTraversal_sanitizesUnsafeCharacters() {
        val database = InMemoryEncryptedDocumentDatabase()
        val masterKey = ByteArray(32) { (it + 1).toByte() }
        val repo = DocumentRepositoryImpl(
            database = database,
            storageDir = storageDir,
            keyProvider = { masterKey }
        )

        val maliciousFileName = "../../../../etc/shadow.pdf"
        val input = ByteArrayInputStream("Harmless content".toByteArray(Charsets.UTF_8))

        val entity = repo.importDocument(
            sourceStream = input,
            title = "Test",
            fileName = maliciousFileName,
            categoryId = "Personal",
            notes = "",
            masterKey = masterKey
        )

        // File name must be sanitized: no directory traversal
        assertFalse(entity.fileName.contains(".."))
        assertFalse(entity.fileName.contains("/"))
        assertFalse(entity.fileName.contains("\\"))
        assertTrue(entity.encryptedFileName.endsWith(".enc"))
        // Encrypted file must stay strictly inside storageDir
        val storedFile = File(storageDir, entity.encryptedFileName)
        assertTrue(storedFile.exists())
        assertEquals(storageDir.canonicalPath, storedFile.parentFile!!.canonicalPath)
        database.close()
    }

    // =========================================================================
    // 4. FILE IMPORT SIZE LIMIT (DOS PROTECTION)
    // =========================================================================

    @Test
    fun testDocumentSizeLimit_rejectsExceedingFiles() {
        val exceedingBytes = DocumentCryptoEngine.MAX_FILE_SIZE_BYTES + 1024L
        // Simulated infinite/oversized stream
        val oversizedStream = object : java.io.InputStream() {
            var counter = 0L
            override fun read(): Int {
                return if (counter++ < exceedingBytes) 0x41 else -1
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (counter >= exceedingBytes) return -1
                val toRead = minOf(len.toLong(), exceedingBytes - counter).toInt()
                java.util.Arrays.fill(b, off, off + toRead, 0x41.toByte())
                counter += toRead
                return toRead
            }
        }

        val key = ByteArray(32) { 0x01 }
        val out = java.io.ByteArrayOutputStream()

        try {
            DocumentCryptoEngine.encryptStream(oversizedStream, out, key)
            fail("Expected IllegalArgumentException for file exceeding 100 MB")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("100 MB"))
        }
    }

    // =========================================================================
    // 5. EMPTY FILE IMPORT REJECTION
    // =========================================================================

    @Test
    fun testDocumentEmptyFile_isRejected() {
        val database = InMemoryEncryptedDocumentDatabase()
        val masterKey = ByteArray(32) { (it + 1).toByte() }
        val repo = DocumentRepositoryImpl(
            database = database,
            storageDir = storageDir,
            keyProvider = { masterKey }
        )

        val emptyStream = ByteArrayInputStream(ByteArray(0))

        try {
            repo.importDocument(
                sourceStream = emptyStream,
                title = "Empty Doc",
                fileName = "empty.pdf",
                categoryId = "Personal",
                notes = "",
                masterKey = masterKey
            )
            fail("Expected IllegalArgumentException for 0-byte file import")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("empty"))
        }

        // Storage dir must remain empty
        assertEquals(0, storageDir.listFiles()?.size ?: 0)
        database.close()
    }

    // =========================================================================
    // 6. SECURE CLIPBOARD SENSITIVE FLAG AND TIMED PURGE
    // =========================================================================

    @Test
    fun testSecureClipboard_autoPurgeAfterTimeout() {
        var systemClipboardCleared = false
        val clipboard = InMemorySecureClipboard(
            onClearSystemClipboard = { systemClipboardCleared = true }
        )

        clipboard.copySensitive(label = "MasterPassword", content = "SecretP@ssword99!", timeoutSeconds = 1)
        assertFalse(clipboard.isCleared())
        assertEquals("SecretP@ssword99!", clipboard.inspectCurrentContent())

        // Wait for 1.2s timeout to fire
        Thread.sleep(1200)

        assertTrue(clipboard.isCleared())
        assertNull(clipboard.inspectCurrentContent())
        assertTrue(systemClipboardCleared)
    }

    // =========================================================================
    // 7. CRYPTOGRAPHIC KEY HIERARCHY DOMAIN SEPARATION
    // =========================================================================

    @Test
    fun testDomainSeparation_dbkAndDekAreDistinct() {
        val masterKeyBytes = ByteArray(32) { (it * 7 + 13).toByte() }
        val masterKey = MasterKey(masterKeyBytes)

        val hierarchy = MasterKeyHierarchy.fromMasterKey(masterKey)

        var dbk: ByteArray? = null
        var dek: ByteArray? = null

        hierarchy.useDatabaseKey { dbk = it.copyOf() }
        hierarchy.useDocumentKey { dek = it.copyOf() }

        assertNotNull(dbk)
        assertNotNull(dek)
        assertEquals(32, dbk!!.size)
        assertEquals(32, dek!!.size)

        // DBK and DEK MUST NOT be identical (strict domain separation)
        assertFalse("DBK and DEK must be cryptographically distinct", dbk.contentEquals(dek))
        assertFalse("DBK must not equal raw Master Key", dbk.contentEquals(masterKeyBytes))
        assertFalse("DEK must not equal raw Master Key", dek.contentEquals(masterKeyBytes))

        hierarchy.close()
        assertTrue(hierarchy.isDestroyed)
    }

    // =========================================================================
    // 8. MEMORY ZEROIZATION OF SENSITIVE BUFFERS
    // =========================================================================

    @Test
    fun testMemoryZeroizer_zeroizesByteArrayAndCharArray() {
        val secretBytes = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        MemoryZeroizer.zeroize(secretBytes)
        for (b in secretBytes) {
            assertEquals(0x00.toByte(), b)
        }

        val secretChars = "MySuperSecretPass".toCharArray()
        MemoryZeroizer.zeroize(secretChars)
        for (c in secretChars) {
            assertEquals('\u0000', c)
        }
    }

    // =========================================================================
    // 9. SESSION TIMEOUT & AUTO-LOCK ZEROIZES KEY MATERIAL
    // =========================================================================

    @Test
    fun testSessionAutoLock_zeroizesActiveKeyHierarchy() {
        val sessionManager = VaultSessionManager(timeoutDurationMillis = 500L)
        val masterKey = MasterKey(ByteArray(32) { 0x55.toByte() })
        val hierarchy = MasterKeyHierarchy.fromMasterKey(masterKey)

        val start = 1_000_000L
        sessionManager.startSession(hierarchy, currentTimeMillis = start)
        assertTrue(sessionManager.isUnlocked(currentTimeMillis = start))

        // Exceed timeout
        val expiredTime = start + 600L
        assertTrue(sessionManager.isSessionExpired(currentTimeMillis = expiredTime))
        val wasExpired = sessionManager.checkAndExpire(currentTimeMillis = expiredTime)
        assertTrue(wasExpired)
        assertFalse(sessionManager.isUnlocked(currentTimeMillis = expiredTime))

        // Key hierarchy must be closed and destroyed
        assertTrue(hierarchy.isDestroyed)
        try {
            hierarchy.useDatabaseKey { }
            fail("Expected exception when accessing zeroized key hierarchy")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("zeroized"))
        }
    }
}
