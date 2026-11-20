package com.balatechdev.vaultzero.security.crypto

import com.balatechdev.vaultzero.security.auth.Argon2idParameters
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.security.SecureRandom

class VaultCryptoUnitTest {

    private lateinit var encryptionService: EncryptionService
    private lateinit var decryptionService: DecryptionService
    private lateinit var keyDerivationService: KeyDerivationService
    private lateinit var keyManagementService: KeyManagementService
    private val secureRandom = SecureRandom()

    private val testParams = Argon2idParameters.Test

    @Before
    fun setUp() {
        encryptionService = EncryptionServiceImpl(secureRandom)
        decryptionService = DecryptionServiceImpl()
        keyDerivationService = KeyDerivationServiceImpl(secureRandom)
        keyManagementService = KeyManagementServiceImpl(
            keyDerivationService = keyDerivationService,
            encryptionService = encryptionService,
            decryptionService = decryptionService,
            secureRandom = secureRandom
        )
    }

    // -------------------------------------------------------------------------
    // TEST 1: Encrypt and Decrypt
    // -------------------------------------------------------------------------
    @Test
    fun testEncryptAndDecrypt_standardPayload_succeeds() {
        val vaultKey = VaultKey.generate(secureRandom)
        val plaintext = "VaultZero Top-Secret Credential Payload 2026".toByteArray(StandardCharsets.UTF_8)

        val encryptedPayload = encryptionService.encrypt(plaintext, vaultKey)
        assertNotNull(encryptedPayload)
        assertEquals(12, encryptedPayload.nonce.size)
        assertTrue(encryptedPayload.ciphertextAndTag.size >= plaintext.size + 16)

        val decrypted = decryptionService.decrypt(encryptedPayload, vaultKey)
        assertArrayEquals(plaintext, decrypted)
        assertEquals("VaultZero Top-Secret Credential Payload 2026", String(decrypted, StandardCharsets.UTF_8))
    }

    @Test
    fun testEncryptAndDecrypt_withAssociatedData_authenticatesAAD() {
        val key = ByteArray(32).also { secureRandom.nextBytes(it) }
        val plaintext = "Sensitive Item Details".toByteArray(StandardCharsets.UTF_8)
        val aad = "credential_id_88849".toByteArray(StandardCharsets.UTF_8)
        val wrongAad = "credential_id_99999".toByteArray(StandardCharsets.UTF_8)

        val payload = encryptionService.encrypt(plaintext, key, aad)

        // Decrypt with correct AAD -> succeeds
        val decrypted = decryptionService.decrypt(payload, key, aad)
        assertArrayEquals(plaintext, decrypted)

        // Decrypt with wrong AAD -> fails
        try {
            decryptionService.decrypt(payload, key, wrongAad)
            fail("Decryption must fail when AAD does not match")
        } catch (expected: CryptoAuthException) {
            // Expected
        }
    }

    // -------------------------------------------------------------------------
    // TEST 2: Wrong Key
    // -------------------------------------------------------------------------
    @Test
    fun testDecrypt_withWrongKey_throwsCryptoAuthException() {
        val key1 = ByteArray(32).also { secureRandom.nextBytes(it) }
        val key2 = ByteArray(32).also { secureRandom.nextBytes(it) }
        val plaintext = "Confidential Banking Secret".toByteArray(StandardCharsets.UTF_8)

        val payload = encryptionService.encrypt(plaintext, key1)

        try {
            decryptionService.decrypt(payload, key2)
            fail("Decryption with wrong key must fail with CryptoAuthException")
        } catch (expected: CryptoAuthException) {
            // Expected: AEADBadTagException wrapped in CryptoAuthException
        }
    }

    // -------------------------------------------------------------------------
    // TEST 3: Wrong Nonce
    // -------------------------------------------------------------------------
    @Test
    fun testDecrypt_withWrongNonce_throwsCryptoAuthException() {
        val key = ByteArray(32).also { secureRandom.nextBytes(it) }
        val plaintext = "Master Recovery Phrase 24 Words".toByteArray(StandardCharsets.UTF_8)

        val originalPayload = encryptionService.encrypt(plaintext, key)

        // Tamper with the 12-byte nonce (flip the first byte)
        val tamperedNonce = originalPayload.nonce.copyOf()
        tamperedNonce[0] = (tamperedNonce[0].toInt() xor 0xFF).toByte()

        val tamperedPayload = EncryptedPayload(tamperedNonce, originalPayload.ciphertextAndTag)

        try {
            decryptionService.decrypt(tamperedPayload, key)
            fail("Decryption with wrong nonce must fail with CryptoAuthException")
        } catch (expected: CryptoAuthException) {
            // Expected
        }
    }

    // -------------------------------------------------------------------------
    // TEST 4: Modified Ciphertext
    // -------------------------------------------------------------------------
    @Test
    fun testDecrypt_withModifiedCiphertext_throwsCryptoAuthException() {
        val key = ByteArray(32).also { secureRandom.nextBytes(it) }
        val plaintext = "Sensitive Transaction $1,000,000 USD".toByteArray(StandardCharsets.UTF_8)

        val originalPayload = encryptionService.encrypt(plaintext, key)

        // Tamper with 1 byte in the ciphertext body
        val tamperedBytes = originalPayload.ciphertextAndTag.copyOf()
        tamperedBytes[0] = (tamperedBytes[0].toInt() xor 0x01).toByte()

        val tamperedPayload = EncryptedPayload(originalPayload.nonce, tamperedBytes)

        try {
            decryptionService.decrypt(tamperedPayload, key)
            fail("Decryption with tampered ciphertext must fail with CryptoAuthException")
        } catch (expected: CryptoAuthException) {
            // Expected: GCM authentication tag detects bit flip
        }
    }

    // -------------------------------------------------------------------------
    // TEST 5: Modified Authentication Tag
    // -------------------------------------------------------------------------
    @Test
    fun testDecrypt_withModifiedAuthenticationTag_throwsCryptoAuthException() {
        val key = ByteArray(32).also { secureRandom.nextBytes(it) }
        val plaintext = "Encrypted Hardware Token Seed".toByteArray(StandardCharsets.UTF_8)

        val originalPayload = encryptionService.encrypt(plaintext, key)

        // Tamper with the last byte of the 16-byte GCM authentication tag
        val tamperedBytes = originalPayload.ciphertextAndTag.copyOf()
        val tagLastIndex = tamperedBytes.size - 1
        tamperedBytes[tagLastIndex] = (tamperedBytes[tagLastIndex].toInt() xor 0x42).toByte()

        val tamperedPayload = EncryptedPayload(originalPayload.nonce, tamperedBytes)

        try {
            decryptionService.decrypt(tamperedPayload, key)
            fail("Decryption with tampered authentication tag must fail with CryptoAuthException")
        } catch (expected: CryptoAuthException) {
            // Expected
        }
    }

    // -------------------------------------------------------------------------
    // TEST 6: Empty Input
    // -------------------------------------------------------------------------
    @Test
    fun testEncryptAndDecrypt_emptyInput_succeeds() {
        val key = ByteArray(32).also { secureRandom.nextBytes(it) }
        val emptyPlaintext = ByteArray(0)

        val payload = encryptionService.encrypt(emptyPlaintext, key)
        assertNotNull(payload)
        assertEquals(12, payload.nonce.size)
        // With empty plaintext, ciphertextAndTag contains exactly the 16-byte authentication tag
        assertEquals(16, payload.ciphertextAndTag.size)
        assertEquals(0, payload.ciphertext.size)
        assertEquals(16, payload.tag.size)

        val decrypted = decryptionService.decrypt(payload, key)
        assertEquals(0, decrypted.size)
        assertArrayEquals(emptyPlaintext, decrypted)
    }

    // -------------------------------------------------------------------------
    // TEST 7: Large Input (1 MB)
    // -------------------------------------------------------------------------
    @Test
    fun testEncryptAndDecrypt_largeInput_succeeds() {
        val key = ByteArray(32).also { secureRandom.nextBytes(it) }
        val largeSize = 1024 * 1024 // 1 Megabyte
        val largePlaintext = ByteArray(largeSize)
        secureRandom.nextBytes(largePlaintext)

        val payload = encryptionService.encrypt(largePlaintext, key)
        assertEquals(12, payload.nonce.size)
        assertEquals(largeSize + 16, payload.ciphertextAndTag.size)

        val decrypted = decryptionService.decrypt(payload, key)
        assertArrayEquals(largePlaintext, decrypted)
    }

    // -------------------------------------------------------------------------
    // TEST 8: Randomness & Entropy Validation
    // -------------------------------------------------------------------------
    @Test
    fun testRandomnessValidation_noncesAndKeysAreUniqueAndHighEntropy() {
        val count = 500
        val key = ByteArray(32).also { secureRandom.nextBytes(it) }
        val plaintext = "Entropy Test".toByteArray()

        val seenNonces = HashSet<String>()
        val byteFrequency = IntArray(256)

        for (i in 0 until count) {
            val payload = encryptionService.encrypt(plaintext, key)
            val nonceHex = payload.nonce.joinToString("") { "%02x".format(it) }

            // Uniqueness validation (Zero collision requirement)
            assertTrue("Nonce collision detected at iteration $i!", seenNonces.add(nonceHex))

            for (b in payload.nonce) {
                byteFrequency[b.toInt() and 0xFF]++
            }
        }

        // Shannon entropy calculation over generated nonces: H = - sum(p * log2(p))
        val totalBytes = (count * 12).toDouble()
        var entropy = 0.0
        for (f in byteFrequency) {
            if (f > 0) {
                val p = f / totalBytes
                entropy -= p * (Math.log(p) / Math.log(2.0))
            }
        }

        // Expected theoretical maximum entropy is 8.0 bits/byte. High-quality CSPRNG must exceed 7.5 bits/byte.
        assertTrue("Shannon entropy ($entropy) must exceed 7.5 bits per byte", entropy > 7.5)
    }

    // -------------------------------------------------------------------------
    // KEY ARCHITECTURE & MULTI-RING ENVELOPE WRAPPING
    // -------------------------------------------------------------------------
    @Test
    fun testKeyManagement_multiRingWrappingAndUnwrapping() {
        val masterPassword = "MasterPassword#2026Safe".toCharArray()
        val recoverySecret = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about".toCharArray()
        val deviceKeyProvider = SoftwareDeviceKeyProvider()

        // Initialize vault key with password, recovery, and device key
        val (wrappedKey, originalVaultKey) = keyManagementService.createVaultKey(
            password = masterPassword,
            recoverySecret = recoverySecret,
            deviceKeyProvider = deviceKeyProvider,
            params = testParams
        )

        var originalKeyBytes = ByteArray(0)
        originalVaultKey.useKey { originalKeyBytes = it.copyOf() }

        // Ring 1: Unlock with Master Password
        val unlockedWithPassword = keyManagementService.unlockWithPassword(masterPassword, wrappedKey)
        unlockedWithPassword.useKey { bytes ->
            assertArrayEquals(originalKeyBytes, bytes)
        }

        // Ring 2: Unlock with Recovery Secret
        val unlockedWithRecovery = keyManagementService.unlockWithRecoverySecret(recoverySecret, wrappedKey)
        unlockedWithRecovery.useKey { bytes ->
            assertArrayEquals(originalKeyBytes, bytes)
        }

        // Ring 3: Unlock with Device Key
        val unlockedWithDevice = keyManagementService.unlockWithDeviceKey(deviceKeyProvider, wrappedKey)
        unlockedWithDevice.useKey { bytes ->
            assertArrayEquals(originalKeyBytes, bytes)
        }

        // Wrong password rejection
        try {
            keyManagementService.unlockWithPassword("WrongPassword#99999".toCharArray(), wrappedKey)
            fail("Must reject incorrect password")
        } catch (expected: CryptoAuthException) {
            // Expected
        }
    }

    // -------------------------------------------------------------------------
    // KEY ROTATION ARCHITECTURE
    // -------------------------------------------------------------------------
    @Test
    fun testKeyRotationArchitecture_rotationPreservesVaultKey() {
        val initialPassword = "OldPassword#2026".toCharArray()
        val newPassword = "NewRotatedPassword#2026".toCharArray()

        val (wrappedKey, vaultKey) = keyManagementService.createVaultKey(
            password = initialPassword,
            params = testParams
        )
        assertEquals(1, wrappedKey.keyVersion)

        var expectedRawKey = ByteArray(0)
        vaultKey.useKey { expectedRawKey = it.copyOf() }

        // Rotate Master Password
        val rotatedWrappedKey = keyManagementService.rotateMasterPassword(
            currentVaultKey = vaultKey,
            newPassword = newPassword,
            existingWrappedKey = wrappedKey,
            params = testParams
        )
        assertEquals(2, rotatedWrappedKey.keyVersion)

        // Old password rejected
        try {
            keyManagementService.unlockWithPassword(initialPassword, rotatedWrappedKey)
            fail("Old password must fail after key rotation")
        } catch (expected: CryptoAuthException) {
            // Expected
        }

        // New password unlocks the EXACT same vault key
        val unlockedWithNew = keyManagementService.unlockWithPassword(newPassword, rotatedWrappedKey)
        unlockedWithNew.useKey { bytes ->
            assertArrayEquals("Vault key must remain unchanged after password rotation", expectedRawKey, bytes)
        }
    }

    @Test
    fun testVaultKey_zeroizationLifecycle() {
        val vaultKey = VaultKey.generate(secureRandom)
        assertFalse(vaultKey.isDestroyed)

        vaultKey.close()
        assertTrue(vaultKey.isDestroyed)

        try {
            vaultKey.copyBytes()
            fail("Must not allow copy after close")
        } catch (expected: IllegalStateException) {
            // Expected
        }

        try {
            vaultKey.useKey { }
            fail("Must not allow use after close")
        } catch (expected: IllegalStateException) {
            // Expected
        }
    }
}
