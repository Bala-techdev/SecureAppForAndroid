package com.balatechdev.vaultzero.data.repository

import com.balatechdev.vaultzero.data.database.InMemoryEncryptedDocumentDatabase
import com.balatechdev.vaultzero.data.model.DocumentCategory
import com.balatechdev.vaultzero.security.crypto.CryptoAuthException
import com.balatechdev.vaultzero.security.crypto.CryptoException
import com.balatechdev.vaultzero.security.crypto.DocumentCryptoEngine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom
import java.util.Arrays

class DocumentVaultUnitTest {

    private lateinit var testStorageDir: File
    private lateinit var testTempDir: File
    private lateinit var database: InMemoryEncryptedDocumentDatabase
    private lateinit var repository: DocumentRepository
    private val masterKey = ByteArray(32) { (it * 3 + 1).toByte() }

    @Before
    fun setUp() {
        val rootDir = File(System.getProperty("java.io.tmpdir"), "vaultzero_doc_test_${System.nanoTime()}")
        testStorageDir = File(rootDir, "vault_documents").apply { mkdirs() }
        testTempDir = File(rootDir, "preview_tmp").apply { mkdirs() }
        database = InMemoryEncryptedDocumentDatabase()
        repository = DocumentRepositoryImpl(
            database = database,
            storageDir = testStorageDir,
            keyProvider = { masterKey }
        )
    }

    @After
    fun tearDown() {
        database.close()
        testStorageDir.parentFile?.deleteRecursively()
    }

    // =========================================================================
    // 1. IMPORT & ENCRYPTION TESTS
    // =========================================================================

    @Test
    fun testImportAndEncryptDocument_success() {
        val originalText = "CONFIDENTIAL PASSPORT DATA: John Doe, Serial #A12345678"
        val originalBytes = originalText.toByteArray(Charsets.UTF_8)

        val entity = repository.importDocument(
            sourceStream = ByteArrayInputStream(originalBytes),
            title = "Passport Scan",
            fileName = "passport.pdf",
            categoryId = DocumentCategory.IDENTITY.id,
            notes = "Valid until 2035",
            masterKey = masterKey
        )

        assertNotNull(entity.id)
        assertEquals("Passport Scan", entity.title)
        assertEquals("passport.pdf", entity.fileName)
        assertEquals("pdf", entity.fileExtension)
        assertEquals("application/pdf", entity.mimeType)
        assertEquals(originalBytes.size.toLong(), entity.fileSizeBytes)
        assertEquals(DocumentCategory.IDENTITY.id, entity.categoryId)

        // Verify encrypted file exists in internal storage
        val encFile = File(testStorageDir, entity.encryptedFileName)
        assertTrue("Encrypted file must exist in internal storage", encFile.exists())

        // Validate 18-byte header (V0DOC + version 0x01 + 12-byte nonce)
        val fileBytes = encFile.readBytes()
        assertTrue(fileBytes.size > 18 + 16) // Header + tag + payload
        val magic = fileBytes.copyOfRange(0, 5)
        assertTrue(magic.contentEquals(DocumentCryptoEngine.MAGIC_HEADER))
        assertEquals(DocumentCryptoEngine.VERSION_BYTE, fileBytes[5])

        // Verify database entry
        val dbRecord = repository.getDocumentById(entity.id)
        assertNotNull(dbRecord)
        assertEquals(entity.id, dbRecord?.id)
    }

    // =========================================================================
    // 2. DECRYPTION TESTS
    // =========================================================================

    @Test
    fun testDecryptDocumentToBytes_matchesOriginalExact() {
        val originalContent = "TAX_RETURN_2025_GROSS_INCOME_VERIFIED".toByteArray(Charsets.UTF_8)
        val entity = repository.importDocument(
            sourceStream = ByteArrayInputStream(originalContent),
            title = "Tax 2025",
            fileName = "tax_2025.pdf",
            categoryId = DocumentCategory.BANKING.id,
            notes = "Filed April 2025",
            masterKey = masterKey
        )

        val decryptedBytes = repository.decryptDocumentToBytes(entity.id, masterKey)
        assertTrue("Decrypted bytes must match original plaintext exactly", originalContent.contentEquals(decryptedBytes))
    }

    @Test
    fun testDecryptDocumentToTempFile_successAndSecureWipe() {
        val originalContent = "DIPLOMA_BACHELOR_COMPUTER_SCIENCE".toByteArray(Charsets.UTF_8)
        val entity = repository.importDocument(
            sourceStream = ByteArrayInputStream(originalContent),
            title = "University Degree",
            fileName = "degree.jpg",
            categoryId = DocumentCategory.EDUCATION.id,
            notes = "Graduated with honors",
            masterKey = masterKey
        )

        val tempFile = repository.decryptDocumentToTempFile(entity.id, masterKey, testTempDir)
        assertTrue("Temp file must exist", tempFile.exists())
        assertEquals(originalContent.size.toLong(), tempFile.length())
        assertTrue(originalContent.contentEquals(tempFile.readBytes()))

        // Verify secure wipe deletes file
        val wiped = DocumentCryptoEngine.secureWipeAndDelete(tempFile)
        assertTrue(wiped)
        assertFalse(tempFile.exists())
    }

    // =========================================================================
    // 3. LARGE FILE STREAMING TESTS (NO OOM)
    // =========================================================================

    @Test
    fun testLargeFileStreaming_multiMegabyteIntegrity() {
        // Generate 2.5 MB of structured pseudo-random data
        val random = SecureRandom()
        val dataSize = 2500 * 1024 // 2.5 MB
        val sampleData = ByteArray(dataSize)
        random.nextBytes(sampleData)

        val entity = repository.importDocument(
            sourceStream = ByteArrayInputStream(sampleData),
            title = "Large Medical Scan",
            fileName = "mri_scan.png",
            categoryId = DocumentCategory.PERSONAL.id,
            notes = "High-res full body scan",
            masterKey = masterKey
        )

        assertEquals(dataSize.toLong(), entity.fileSizeBytes)

        // Stream decrypt
        val encFile = File(testStorageDir, entity.encryptedFileName)
        val outStream = ByteArrayOutputStream(dataSize)
        val aad = DocumentCryptoEngine.buildAssociatedData(entity.id, entity.fileExtension, entity.mimeType)

        encFile.inputStream().use { inStream ->
            val decryptedLength = DocumentCryptoEngine.decryptStream(inStream, outStream, masterKey, aad)
            assertEquals(dataSize.toLong(), decryptedLength)
        }

        val decryptedData = outStream.toByteArray()
        assertTrue("Large file streaming must preserve byte-for-byte fidelity", Arrays.equals(sampleData, decryptedData))
    }

    // =========================================================================
    // 4. CORRUPTION & TAMPERING DETECTION TESTS
    // =========================================================================

    @Test
    fun testCiphertextCorruption_detectedByGcmTag() {
        val original = "HIGHLY_CLASSIFIED_AGREEMENT".toByteArray(Charsets.UTF_8)
        val entity = repository.importDocument(
            sourceStream = ByteArrayInputStream(original),
            title = "Contract",
            fileName = "contract.pdf",
            categoryId = DocumentCategory.BANKING.id,
            notes = "",
            masterKey = masterKey
        )

        val encFile = File(testStorageDir, entity.encryptedFileName)
        val fileBytes = encFile.readBytes()

        // Flip bits in the middle of ciphertext
        val tamperedByteIndex = 25
        fileBytes[tamperedByteIndex] = (fileBytes[tamperedByteIndex].toInt() xor 0xFF).toByte()
        encFile.writeBytes(fileBytes)

        try {
            repository.decryptDocumentToBytes(entity.id, masterKey)
            fail("Decryption must fail with CryptoAuthException when ciphertext is tampered")
        } catch (e: CryptoAuthException) {
            // Expected
            assertTrue(e.message!!.contains("authentication failed"))
        }
    }

    @Test
    fun testNonceTampering_detected() {
        val original = "SECRET_KEYS_LIST".toByteArray(Charsets.UTF_8)
        val entity = repository.importDocument(
            sourceStream = ByteArrayInputStream(original),
            title = "Keys",
            fileName = "keys.jpg",
            categoryId = DocumentCategory.PERSONAL.id,
            notes = "",
            masterKey = masterKey
        )

        val encFile = File(testStorageDir, entity.encryptedFileName)
        val fileBytes = encFile.readBytes()

        // Tamper with nonce byte (nonce is at bytes 6..17)
        fileBytes[8] = (fileBytes[8].toInt() xor 0xAA).toByte()
        encFile.writeBytes(fileBytes)

        try {
            repository.decryptDocumentToBytes(entity.id, masterKey)
            fail("Decryption must fail when nonce is tampered")
        } catch (e: CryptoAuthException) {
            // Expected
        }
    }

    @Test
    fun testAssociatedDataMismatch_detected() {
        val original = "AAD_TEST_FILE".toByteArray(Charsets.UTF_8)
        val entity = repository.importDocument(
            sourceStream = ByteArrayInputStream(original),
            title = "AAD Test",
            fileName = "test.png",
            categoryId = DocumentCategory.OTHER.id,
            notes = "",
            masterKey = masterKey
        )

        val encFile = File(testStorageDir, entity.encryptedFileName)
        val wrongAad = DocumentCryptoEngine.buildAssociatedData("wrong_doc_id", "png", "image/png")

        try {
            encFile.inputStream().use { inStream ->
                val out = ByteArrayOutputStream()
                DocumentCryptoEngine.decryptStream(inStream, out, masterKey, wrongAad)
            }
            fail("Decryption must fail when AAD does not match")
        } catch (e: CryptoAuthException) {
            // Expected
        }
    }

    // =========================================================================
    // 5. INCOMPLETE IMPORT & CRASH HANDLING
    // =========================================================================

    @Test
    fun testIncompleteImport_cleansPartialFile() {
        // Stream that throws IOException mid-read
        val brokenStream = object : java.io.InputStream() {
            private var count = 0
            override fun read(): Int {
                if (count++ > 50) throw java.io.IOException("Simulated app crash / connection drop")
                return 42
            }
        }

        try {
            repository.importDocument(
                sourceStream = brokenStream,
                title = "Broken Import",
                fileName = "broken.pdf",
                categoryId = DocumentCategory.OTHER.id,
                notes = "",
                masterKey = masterKey
            )
            fail("Must propagate stream error")
        } catch (e: Exception) {
            // Expected
        }

        // Verify no leftover .part files exist
        val partFiles = testStorageDir.listFiles { _, name -> name.endsWith(".part") }
        assertTrue("No .part files should remain after failed import", partFiles.isNullOrEmpty())
    }

    @Test
    fun testCleanupOrphanedImports_cleansDanglingFiles() {
        // Create artificial orphaned .part and .tmp files
        File(testStorageDir, "dangling_1.part").writeText("part data")
        File(testStorageDir, "dangling_2.tmp").writeText("tmp data")
        File(testStorageDir, "valid.enc").writeText("valid data")

        val cleaned = repository.cleanupOrphanedImports()
        assertEquals(2, cleaned)
        assertFalse(File(testStorageDir, "dangling_1.part").exists())
        assertFalse(File(testStorageDir, "dangling_2.tmp").exists())
        assertTrue(File(testStorageDir, "valid.enc").exists())
    }

    // =========================================================================
    // 6. RENAME, CATEGORIZE, SEARCH & DELETE
    // =========================================================================

    @Test
    fun testRenameDocument_updatesRecord() {
        val original = "DATA".toByteArray()
        val entity = repository.importDocument(
            sourceStream = ByteArrayInputStream(original),
            title = "Original Title",
            fileName = "doc.pdf",
            categoryId = DocumentCategory.OTHER.id,
            notes = "",
            masterKey = masterKey
        )

        val renamed = repository.renameDocument(entity.id, "Renamed Title 2026")
        assertTrue(renamed)

        val updated = repository.getDocumentById(entity.id)
        assertEquals("Renamed Title 2026", updated?.title)
    }

    @Test
    fun testUpdateCategory_updatesRecord() {
        val original = "DATA".toByteArray()
        val entity = repository.importDocument(
            sourceStream = ByteArrayInputStream(original),
            title = "Driver License",
            fileName = "license.jpeg",
            categoryId = DocumentCategory.OTHER.id,
            notes = "",
            masterKey = masterKey
        )

        val updated = repository.updateCategory(entity.id, DocumentCategory.IDENTITY.id)
        assertTrue(updated)

        val record = repository.getDocumentById(entity.id)
        assertEquals(DocumentCategory.IDENTITY.id, record?.categoryId)
    }

    @Test
    fun testSearchDocuments_filtersByQueryAndCategory() {
        repository.importDocument(
            sourceStream = ByteArrayInputStream("A".toByteArray()),
            title = "Passport Card",
            fileName = "passport_card.pdf",
            categoryId = DocumentCategory.IDENTITY.id,
            notes = "Biometric chip",
            masterKey = masterKey
        )
        repository.importDocument(
            sourceStream = ByteArrayInputStream("B".toByteArray()),
            title = "Bank Statement",
            fileName = "statement_oct.pdf",
            categoryId = DocumentCategory.BANKING.id,
            notes = "Savings account",
            masterKey = masterKey
        )

        val identityResults = repository.searchDocuments(query = "", categoryId = DocumentCategory.IDENTITY.id)
        assertEquals(1, identityResults.size)
        assertEquals("Passport Card", identityResults.first().title)

        val searchResults = repository.searchDocuments(query = "oct", categoryId = null)
        assertEquals(1, searchResults.size)
        assertEquals("Bank Statement", searchResults.first().title)
    }

    @Test
    fun testDeleteDocument_wipesFileAndRemovesDbEntry() {
        val entity = repository.importDocument(
            sourceStream = ByteArrayInputStream("SENSITIVE_SECRET".toByteArray()),
            title = "To Delete",
            fileName = "delete_me.png",
            categoryId = DocumentCategory.PERSONAL.id,
            notes = "",
            masterKey = masterKey
        )

        val encFile = File(testStorageDir, entity.encryptedFileName)
        assertTrue(encFile.exists())

        val deleted = repository.deleteDocument(entity.id)
        assertTrue(deleted)

        // File must be deleted from storage
        assertFalse("Encrypted file must be wiped and deleted", encFile.exists())
        // Record must be deleted from DB
        assertNull(repository.getDocumentById(entity.id))
    }

    // =========================================================================
    // 7. UNSUPPORTED FORMAT REJECTION
    // =========================================================================

    @Test
    fun testUnsupportedFormats_rejected() {
        val unsupportedFiles = listOf("malware.exe", "notes.txt", "archive.zip", "script.sh")
        for (file in unsupportedFiles) {
            try {
                repository.importDocument(
                    sourceStream = ByteArrayInputStream("DATA".toByteArray()),
                    title = "Test",
                    fileName = file,
                    categoryId = DocumentCategory.OTHER.id,
                    notes = "",
                    masterKey = masterKey
                )
                fail("Must reject unsupported file format: $file")
            } catch (e: IllegalArgumentException) {
                // Expected
                assertTrue(e.message!!.contains("Unsupported document format"))
            }
        }
    }
}
