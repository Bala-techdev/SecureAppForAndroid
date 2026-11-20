package com.balatechdev.vaultzero.security.recovery

import com.balatechdev.vaultzero.security.auth.AuthResult
import com.balatechdev.vaultzero.security.auth.InMemoryVaultKeyStorage
import com.balatechdev.vaultzero.security.auth.MasterKey
import com.balatechdev.vaultzero.security.auth.MasterKeyHierarchy
import com.balatechdev.vaultzero.security.auth.VaultSessionManager
import com.balatechdev.vaultzero.security.crypto.CryptoAuthException
import com.balatechdev.vaultzero.security.crypto.VaultKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.security.SecureRandom
import java.util.Arrays

class VaultRecoveryUnitTest {

    private lateinit var keyStorage: InMemoryVaultKeyStorage
    private lateinit var sessionManager: VaultSessionManager
    private lateinit var recoveryService: VaultRecoveryServiceImpl

    private val masterKeyBytes = ByteArray(32) { (it * 7 + 11).toByte() }

    @Before
    fun setUp() {
        keyStorage = InMemoryVaultKeyStorage()
        sessionManager = VaultSessionManager()
        recoveryService = VaultRecoveryServiceImpl(
            keyStorage = keyStorage,
            sessionManager = sessionManager
        )
    }

    @After
    fun tearDown() {
        sessionManager.lock()
        keyStorage.clear()
    }

    // =========================================================================
    // 1. BIP-39 SPECIFICATION TEST VECTORS
    // =========================================================================

    @Test
    fun testBip39_officialVectors_allZeros() {
        // Official BIP-0039 test vector for 256-bit zero entropy
        val zeroEntropy = ByteArray(32) { 0.toByte() }
        val mnemonic = Bip39Mnemonic.entropyToMnemonic(zeroEntropy)

        assertEquals(24, mnemonic.size)
        // First 23 words are "abandon", 24th word is "art"
        for (i in 0 until 23) {
            assertEquals("abandon", mnemonic[i])
        }
        assertEquals("art", mnemonic[23])

        // Roundtrip decoding
        val decoded = Bip39Mnemonic.mnemonicToEntropy(mnemonic)
        assertTrue(zeroEntropy.contentEquals(decoded))
    }

    @Test
    fun testBip39_officialVectors_all0x7f() {
        // Official BIP-0039 test vector for 256-bit 0x7f entropy
        val entropy = ByteArray(32) { 0x7F.toByte() }
        val mnemonic = Bip39Mnemonic.entropyToMnemonic(entropy)

        assertEquals(24, mnemonic.size)
        assertEquals("legal", mnemonic[0])
        assertEquals("title", mnemonic[23])

        val decoded = Bip39Mnemonic.mnemonicToEntropy(mnemonic)
        assertTrue(entropy.contentEquals(decoded))
    }

    @Test
    fun testBip39_officialVectors_all0xff() {
        // Official BIP-0039 test vector for 256-bit 0xff entropy
        val entropy = ByteArray(32) { 0xFF.toByte() }
        val mnemonic = Bip39Mnemonic.entropyToMnemonic(entropy)

        assertEquals(24, mnemonic.size)
        for (i in 0 until 23) {
            assertEquals("zoo", mnemonic[i])
        }
        assertEquals("vote", mnemonic[23])

        val decoded = Bip39Mnemonic.mnemonicToEntropy(mnemonic)
        assertTrue(entropy.contentEquals(decoded))
    }

    // =========================================================================
    // 2. INCORRECT WORDS, INVALID CHECKSUM & INVALID WORD COUNT
    // =========================================================================

    @Test
    fun testIncorrectWords_unknownDictionaryWordRejected() {
        val validMnemonic = recoveryService.generateRecoveryPhrase().toMutableList()
        // Replace word #5 with non-dictionary word
        validMnemonic[4] = "notabipword123"

        try {
            Bip39Mnemonic.mnemonicToEntropy(validMnemonic)
            fail("Must throw UnknownRecoveryWordException when word is not in BIP-39 wordlist")
        } catch (e: UnknownRecoveryWordException) {
            assertTrue(e.message!!.contains("notabipword123"))
        }
    }

    @Test
    fun testInvalidWordCount_rejected() {
        val validMnemonic = recoveryService.generateRecoveryPhrase()

        // 23 words instead of 24
        val shortPhrase = validMnemonic.subList(0, 23)
        try {
            Bip39Mnemonic.mnemonicToEntropy(shortPhrase)
            fail("Must reject non-24 word count")
        } catch (e: InvalidRecoveryWordCountException) {
            // Expected
        }

        // 12 words instead of 24
        val twelveWords = validMnemonic.subList(0, 12)
        try {
            Bip39Mnemonic.mnemonicToEntropy(twelveWords)
            fail("Must reject 12 word count when 24 required")
        } catch (e: InvalidRecoveryWordCountException) {
            // Expected
        }
    }

    @Test
    fun testInvalidChecksum_detectedAndRejected() {
        val validMnemonic = recoveryService.generateRecoveryPhrase().toMutableList()

        // Replace the 24th checksum word with a DIFFERENT valid BIP-39 word that alters the checksum
        val currentLast = validMnemonic[23]
        val candidate = if (currentLast == "abandon") "zoo" else "abandon"
        validMnemonic[23] = candidate

        try {
            Bip39Mnemonic.mnemonicToEntropy(validMnemonic)
            fail("Must throw InvalidRecoveryChecksumException when checksum bits mismatch")
        } catch (e: InvalidRecoveryChecksumException) {
            assertTrue(e.message!!.contains("checksum"))
        }
    }

    // =========================================================================
    // 3. WRONG RECOVERY PHRASE (DIFFERENT VAULT)
    // =========================================================================

    @Test
    fun testWrongRecoveryPhrase_gcmAuthTagFailure() {
        val vaultPhrase = recoveryService.generateRecoveryPhrase()
        val envelope = recoveryService.createRecoveryEnvelope(masterKeyBytes, vaultPhrase)

        // Generate an entirely different, valid BIP-39 phrase
        val wrongPhrase = recoveryService.generateRecoveryPhrase()

        try {
            recoveryService.recoverMasterKey(wrongPhrase, envelope)
            fail("Decryption must fail with CryptoAuthException when wrong recovery phrase is supplied")
        } catch (e: CryptoAuthException) {
            assertTrue(e.message!!.contains("authentication failed"))
        }
    }

    // =========================================================================
    // 4. SUCCESSFUL RECOVERY & MASTER PASSWORD RESET
    // =========================================================================

    @Test
    fun testSuccessfulRecovery_exactKeyRestored() {
        val phrase = recoveryService.generateRecoveryPhrase()
        val envelope = recoveryService.createRecoveryEnvelope(masterKeyBytes, phrase)

        val recoveredKey = recoveryService.recoverMasterKey(phrase, envelope)
        assertTrue("Recovered key must match original master key exact bytes", Arrays.equals(masterKeyBytes, recoveredKey))
    }

    @Test
    fun testResetMasterPasswordWithRecovery_preservesUnderlyingData() {
        val phrase = recoveryService.generateRecoveryPhrase()
        val envelope = recoveryService.createRecoveryEnvelope(masterKeyBytes, phrase)

        val newPassword = "NewStr0ngP@ssw0rd!2026".toCharArray()
        val newConfirm = "NewStr0ngP@ssw0rd!2026".toCharArray()

        val result = recoveryService.resetMasterPasswordWithRecovery(
            mnemonicWords = phrase,
            newPassword = newPassword,
            newConfirmation = newConfirm
        )

        assertTrue("Master password reset must succeed", result is AuthResult.Success)
        assertTrue("Session must be unlocked after recovery", sessionManager.isUnlocked)

        val activeSession = sessionManager.currentSession
        assertNotNull(activeSession)

        // Verify underlying Master Key is preserved!
        val sessionKey = activeSession!!.hierarchy.masterKey.useKey { it.copyOf() }
        assertTrue("Preserved master key must be identical to original", Arrays.equals(masterKeyBytes, sessionKey))

        // Verify DBK and DEK subkeys match original derivation
        val originalHierarchy = MasterKeyHierarchy.fromMasterKey(MasterKey(masterKeyBytes))
        var originalDbk: ByteArray? = null
        var restoredDbk: ByteArray? = null
        originalHierarchy.useDatabaseKey { originalDbk = it.copyOf() }
        activeSession.hierarchy.useDatabaseKey { restoredDbk = it.copyOf() }

        assertTrue("Database Key (DBK) must be preserved for existing database records", Arrays.equals(originalDbk, restoredDbk))
    }

    // =========================================================================
    // 5. WORD CONFIRMATION TEST
    // =========================================================================

    @Test
    fun testWordConfirmationVerification() {
        val phrase = recoveryService.generateRecoveryPhrase()

        // Correct confirmation
        val correctConfirmation = mapOf(
            4 to phrase[3],
            12 to phrase[11],
            18 to phrase[17]
        )
        assertTrue(recoveryService.verifyConfirmationWords(phrase, correctConfirmation))

        // Case insensitivity
        val caseInsensitiveConfirmation = mapOf(
            4 to phrase[3].uppercase(),
            12 to phrase[11].lowercase()
        )
        assertTrue(recoveryService.verifyConfirmationWords(phrase, caseInsensitiveConfirmation))

        // Wrong word
        val wrongConfirmation = mapOf(
            4 to "wrongword",
            12 to phrase[11]
        )
        assertFalse(recoveryService.verifyConfirmationWords(phrase, wrongConfirmation))
    }
}
