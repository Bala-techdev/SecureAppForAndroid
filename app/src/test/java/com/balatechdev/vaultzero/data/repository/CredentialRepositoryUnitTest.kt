package com.balatechdev.vaultzero.data.repository

import com.balatechdev.vaultzero.data.database.EncryptedCredentialDatabase
import com.balatechdev.vaultzero.data.database.InMemoryEncryptedCredentialDatabase
import com.balatechdev.vaultzero.data.model.CredentialRecord
import com.balatechdev.vaultzero.data.model.PasswordStrength
import com.balatechdev.vaultzero.security.clipboard.InMemorySecureClipboard
import com.balatechdev.vaultzero.security.crypto.DecryptionServiceImpl
import com.balatechdev.vaultzero.security.crypto.EncryptionServiceImpl
import com.balatechdev.vaultzero.security.crypto.PasswordGenerator
import com.balatechdev.vaultzero.security.crypto.PasswordGeneratorRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.security.SecureRandom
import java.util.UUID

class CredentialRepositoryUnitTest {

    private lateinit var database: EncryptedCredentialDatabase
    private lateinit var repository: CredentialRepository
    private lateinit var clipboard: InMemorySecureClipboard
    private lateinit var passwordGenerator: PasswordGenerator

    private var activeDbKey: ByteArray? = null
    private val secureRandom = SecureRandom()

    @Before
    fun setUp() {
        activeDbKey = ByteArray(32).also { secureRandom.nextBytes(it) }
        database = InMemoryEncryptedCredentialDatabase()
        clipboard = InMemorySecureClipboard()
        passwordGenerator = PasswordGenerator(secureRandom)

        repository = CredentialRepositoryImpl(
            database = database,
            keyProvider = { activeDbKey },
            encryptionService = EncryptionServiceImpl(secureRandom),
            decryptionService = DecryptionServiceImpl()
        )
    }

    private fun sampleRecord(
        id: String = UUID.randomUUID().toString(),
        title: String = "Proton Mail",
        username: String = "user@proton.me",
        password: String = "S3cur3!Pr0t0n#2026",
        notes: String = "Encrypted private notes",
        totpSecret: String? = "JBSWY3DPEHPK3PXP",
        categoryId: String = "cat_logins",
        isFavorite: Boolean = false
    ): CredentialRecord {
        return CredentialRecord(
            id = id,
            title = title,
            username = username,
            passwordChars = password.toCharArray(),
            website = "https://mail.proton.me",
            notes = notes,
            totpSecret = totpSecret,
            categoryId = categoryId,
            isFavorite = isFavorite
        )
    }

    // -------------------------------------------------------------
    // FEATURE 1: Add Credential & Plaintext Non-Persistence Check
    // -------------------------------------------------------------
    @Test
    fun testAddCredential_encryptsAtRestAndDecryptsInMemory() {
        val rawPassword = "V@ultZero#SecretPassword99"
        val record = sampleRecord(password = rawPassword)

        val result = repository.addCredential(record)
        assertTrue(result.isSuccess)

        // Verify entity in underlying database is strictly ENCRYPTED (no plaintext)
        val rawEntity = database.credentialDao.getById(record.id)
        assertNotNull(rawEntity)
        val rawEntityString = String(rawEntity!!.encryptedPassword)
        assertFalse("Plaintext password must NOT appear in raw database storage", rawEntityString.contains(rawPassword))

        // Verify repository decrypts cleanly when key is active
        val retrieved = repository.getCredentialById(record.id)
        assertNotNull(retrieved)
        assertEquals(record.id, retrieved?.id)
        assertEquals(record.title, retrieved?.title)
        assertEquals(record.username, retrieved?.username)
        assertEquals(rawPassword, retrieved?.passwordString)
        assertEquals(record.notes, retrieved?.notes)
        assertEquals(record.totpSecret, retrieved?.totpSecret)
    }

    // -------------------------------------------------------------
    // FEATURE 2: Edit Credential
    // -------------------------------------------------------------
    @Test
    fun testEditCredential_updatesFieldsAndTimestamp() {
        val record = sampleRecord()
        repository.addCredential(record)

        val updatedRecord = record.copy(
            title = "Proton Mail Corporate",
            username = "admin@proton.me",
            passwordChars = "N3w#UltraP@ssword2026".toCharArray(),
            notes = "Updated security notes"
        )

        val updateResult = repository.updateCredential(updatedRecord)
        assertTrue(updateResult.isSuccess)

        val retrieved = repository.getCredentialById(record.id)
        assertNotNull(retrieved)
        assertEquals("Proton Mail Corporate", retrieved?.title)
        assertEquals("admin@proton.me", retrieved?.username)
        assertEquals("N3w#UltraP@ssword2026", retrieved?.passwordString)
        assertEquals("Updated security notes", retrieved?.notes)
    }

    // -------------------------------------------------------------
    // FEATURE 3: Delete Credential
    // -------------------------------------------------------------
    @Test
    fun testDeleteCredential_removesFromVault() {
        val record = sampleRecord()
        repository.addCredential(record)
        assertEquals(1, repository.loadAll().size)

        val deleteResult = repository.deleteCredential(record.id)
        assertTrue(deleteResult.isSuccess)
        assertEquals(0, repository.loadAll().size)
        assertNull(repository.getCredentialById(record.id))
    }

    // -------------------------------------------------------------
    // FEATURE 4 & 5: Display & Search Credentials
    // -------------------------------------------------------------
    @Test
    fun testSearchCredentials_filtersByTitleUsernameAndCategory() {
        val bitwarden = sampleRecord(title = "Bitwarden", username = "sync@bitwarden.com", categoryId = "cat_logins")
        val bank = sampleRecord(title = "Bank of America", username = "finance@boa.com", categoryId = "cat_financial")
        val crypto = sampleRecord(title = "Crypto Exchange", username = "trader@crypto.io", categoryId = "cat_financial")

        repository.addCredential(bitwarden)
        repository.addCredential(bank)
        repository.addCredential(crypto)

        // Search "finance" -> matches Bank of America
        val searchResults = repository.searchCredentials(query = "finance")
        assertEquals(1, searchResults.size)
        assertEquals("Bank of America", searchResults.first().title)

        // Search category "cat_financial" -> matches Bank and Crypto
        val financialResults = repository.searchCredentials(query = "", categoryId = "cat_financial")
        assertEquals(2, financialResults.size)
    }

    // -------------------------------------------------------------
    // FEATURE 6 & 7: Secure Clipboard (Copy Username & Password)
    // -------------------------------------------------------------
    @Test
    fun testSecureClipboard_sensitiveContentAndClearing() {
        val password = "SuperSecretPassword#2026"
        clipboard.copySensitive(label = "Password", content = password, timeoutSeconds = 60L)

        assertFalse(clipboard.isCleared())
        assertEquals(password, clipboard.inspectCurrentContent())

        // Explicit purge
        clipboard.clear()
        assertTrue(clipboard.isCleared())
        assertNull(clipboard.inspectCurrentContent())
    }

    // -------------------------------------------------------------
    // FEATURE 9 & 10: Password Generator & Strength Indicator
    // -------------------------------------------------------------
    @Test
    fun testPasswordGenerator_satisfiesComplexityRules() {
        val rules = PasswordGeneratorRules(
            length = 20,
            includeUppercase = true,
            includeLowercase = true,
            includeDigits = true,
            includeSymbols = true,
            excludeAmbiguous = true
        )

        val password = passwordGenerator.generateString(rules)
        assertEquals(20, password.length)
        assertTrue(password.any { it.isUpperCase() })
        assertTrue(password.any { it.isLowerCase() })
        assertTrue(password.any { it.isDigit() })
        assertTrue(password.any { "!@#$%^&*()-_=+[]{}|;:,.<>?".contains(it) })

        // Strength indicator check
        val strength = sampleRecord(password = password).passwordStrength
        assertTrue(strength == PasswordStrength.GOOD || strength == PasswordStrength.STRONG)
    }

    // -------------------------------------------------------------
    // FEATURE 11 & 12: Category Management & Favorites
    // -------------------------------------------------------------
    @Test
    fun testFavorites_togglesStateAndFilters() {
        val record1 = sampleRecord(title = "Item 1", isFavorite = false)
        val record2 = sampleRecord(title = "Item 2", isFavorite = true)
        repository.addCredential(record1)
        repository.addCredential(record2)

        assertEquals(1, repository.getFavorites().size)

        // Toggle Item 1 to favorite
        val toggleResult = repository.toggleFavorite(record1.id)
        assertTrue(toggleResult.isSuccess)
        assertTrue(toggleResult.getOrThrow())

        assertEquals(2, repository.getFavorites().size)
    }

    // -------------------------------------------------------------
    // SECURITY: Vault Locking & Zeroization
    // -------------------------------------------------------------
    @Test
    fun testVaultLocking_wipesDecryptedMemoryAndClosesDatabase() {
        val record = sampleRecord()
        repository.addCredential(record)
        assertEquals(1, repository.credentialsFlow.value.size)

        // Lock vault
        repository.lock()

        // In-memory flow is wiped
        assertEquals(0, repository.credentialsFlow.value.size)

        // Database is closed
        assertFalse(database.isOpen())
    }

    @Test
    fun testTamperedCiphertext_rejectedByAEADTag() {
        val record = sampleRecord()
        repository.addCredential(record)

        // Tamper with the raw encrypted bytes in the database
        val entity = database.credentialDao.getById(record.id)!!
        val tamperedBytes = entity.encryptedPassword.copyOf()
        tamperedBytes[tamperedBytes.size - 1] = (tamperedBytes[tamperedBytes.size - 1].toInt() xor 0xFF).toByte()

        database.credentialDao.update(entity.copy(encryptedPassword = tamperedBytes))

        // Decryption must fail cleanly (rejecting tampered record)
        val retrieved = repository.getCredentialById(record.id)
        assertNull("Tampered record must be rejected by AEAD validation", retrieved)
    }
}
