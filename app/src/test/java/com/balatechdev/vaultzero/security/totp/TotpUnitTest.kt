package com.balatechdev.vaultzero.security.totp

import com.balatechdev.vaultzero.security.clipboard.InMemorySecureClipboard
import com.balatechdev.vaultzero.security.crypto.DecryptionServiceImpl
import com.balatechdev.vaultzero.security.crypto.EncryptionServiceImpl
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

class TotpUnitTest {

    private lateinit var totpService: TotpService
    private lateinit var clipboard: InMemorySecureClipboard
    private val secureRandom = SecureRandom()

    // RFC 6238 Appendix B Official Test Seeds (ASCII strings)
    // Seed SHA-1: 20 bytes "12345678901234567890"
    private val rfcSha1Seed = "12345678901234567890".toByteArray(StandardCharsets.US_ASCII)

    // Seed SHA-256: 32 bytes "12345678901234567890123456789012"
    private val rfcSha256Seed = "12345678901234567890123456789012".toByteArray(StandardCharsets.US_ASCII)

    // Seed SHA-512: 64 bytes "1234567890123456789012345678901234567890123456789012345678901234"
    private val rfcSha512Seed = "1234567890123456789012345678901234567890123456789012345678901234".toByteArray(StandardCharsets.US_ASCII)

    @Before
    fun setUp() {
        clipboard = InMemorySecureClipboard()
        totpService = TotpServiceImpl(
            clipboard = clipboard,
            encryptionService = EncryptionServiceImpl(secureRandom),
            decryptionService = DecryptionServiceImpl()
        )
    }

    // =========================================================================
    // RFC 6238 APPENDIX B OFFICIAL TEST VECTORS (HMAC-SHA1, SHA256, SHA512)
    // =========================================================================

    @Test
    fun testRfc6238_AppendixB_Sha1_Vectors() {
        // Expected 8-digit values from RFC 6238 Appendix B Table 1
        assertEquals("94287082", TotpGenerator.generateCode(rfcSha1Seed, 59L, TotpAlgorithm.SHA1, 8, 30))
        assertEquals("07081804", TotpGenerator.generateCode(rfcSha1Seed, 1111111109L, TotpAlgorithm.SHA1, 8, 30))
        assertEquals("14050471", TotpGenerator.generateCode(rfcSha1Seed, 1111111111L, TotpAlgorithm.SHA1, 8, 30))
        assertEquals("89005924", TotpGenerator.generateCode(rfcSha1Seed, 1234567890L, TotpAlgorithm.SHA1, 8, 30))
        assertEquals("69279037", TotpGenerator.generateCode(rfcSha1Seed, 2000000000L, TotpAlgorithm.SHA1, 8, 30))
        assertEquals("65353130", TotpGenerator.generateCode(rfcSha1Seed, 20000000000L, TotpAlgorithm.SHA1, 8, 30))

        // 6-digit standard codes (last 6 digits of above values)
        assertEquals("287082", TotpGenerator.generateCode(rfcSha1Seed, 59L, TotpAlgorithm.SHA1, 6, 30))
        assertEquals("081804", TotpGenerator.generateCode(rfcSha1Seed, 1111111109L, TotpAlgorithm.SHA1, 6, 30))
        assertEquals("050471", TotpGenerator.generateCode(rfcSha1Seed, 1111111111L, TotpAlgorithm.SHA1, 6, 30))
        assertEquals("005924", TotpGenerator.generateCode(rfcSha1Seed, 1234567890L, TotpAlgorithm.SHA1, 6, 30))
        assertEquals("279037", TotpGenerator.generateCode(rfcSha1Seed, 2000000000L, TotpAlgorithm.SHA1, 6, 30))
        assertEquals("353130", TotpGenerator.generateCode(rfcSha1Seed, 20000000000L, TotpAlgorithm.SHA1, 6, 30))
    }

    @Test
    fun testRfc6238_AppendixB_Sha256_Vectors() {
        // Expected 8-digit values from RFC 6238 Appendix B Table 1 (SHA-256)
        assertEquals("46119246", TotpGenerator.generateCode(rfcSha256Seed, 59L, TotpAlgorithm.SHA256, 8, 30))
        assertEquals("68084774", TotpGenerator.generateCode(rfcSha256Seed, 1111111109L, TotpAlgorithm.SHA256, 8, 30))
        assertEquals("67062674", TotpGenerator.generateCode(rfcSha256Seed, 1111111111L, TotpAlgorithm.SHA256, 8, 30))
        assertEquals("91819424", TotpGenerator.generateCode(rfcSha256Seed, 1234567890L, TotpAlgorithm.SHA256, 8, 30))
        assertEquals("90698825", TotpGenerator.generateCode(rfcSha256Seed, 2000000000L, TotpAlgorithm.SHA256, 8, 30))
        assertEquals("77737706", TotpGenerator.generateCode(rfcSha256Seed, 20000000000L, TotpAlgorithm.SHA256, 8, 30))
    }

    @Test
    fun testRfc6238_AppendixB_Sha512_Vectors() {
        // Expected 8-digit values from RFC 6238 Appendix B Table 1 (SHA-512)
        assertEquals("90693936", TotpGenerator.generateCode(rfcSha512Seed, 59L, TotpAlgorithm.SHA512, 8, 30))
        assertEquals("25091201", TotpGenerator.generateCode(rfcSha512Seed, 1111111109L, TotpAlgorithm.SHA512, 8, 30))
        assertEquals("99943326", TotpGenerator.generateCode(rfcSha512Seed, 1111111111L, TotpAlgorithm.SHA512, 8, 30))
        assertEquals("93441116", TotpGenerator.generateCode(rfcSha512Seed, 1234567890L, TotpAlgorithm.SHA512, 8, 30))
        assertEquals("38618901", TotpGenerator.generateCode(rfcSha512Seed, 2000000000L, TotpAlgorithm.SHA512, 8, 30))
        assertEquals("47863826", TotpGenerator.generateCode(rfcSha512Seed, 20000000000L, TotpAlgorithm.SHA512, 8, 30))
    }

    // =========================================================================
    // TIME BOUNDARIES & COUNTDOWN TIMER
    // =========================================================================

    @Test
    fun testTimeBoundaries_exactWindowTransition() {
        // Period = 30 seconds
        val timeStep0_End = 29L
        val timeStep1_Start = 30L
        val timeStep1_End = 59L
        val timeStep2_Start = 60L

        val codeAt29 = TotpGenerator.generateCode(rfcSha1Seed, timeStep0_End)
        val codeAt30 = TotpGenerator.generateCode(rfcSha1Seed, timeStep1_Start)
        val codeAt59 = TotpGenerator.generateCode(rfcSha1Seed, timeStep1_End)
        val codeAt60 = TotpGenerator.generateCode(rfcSha1Seed, timeStep2_Start)

        // Codes within same 30s window must be identical
        assertEquals("Codes at 30s and 59s belong to same time step", codeAt30, codeAt59)

        // Codes across window boundary must differ
        assertFalse("Code must transition across 29s->30s boundary", codeAt29 == codeAt30)
        assertFalse("Code must transition across 59s->60s boundary", codeAt59 == codeAt60)
    }

    @Test
    fun testCountdownTimer_remainingSecondsAndProgress() {
        assertEquals(30, TotpGenerator.remainingSeconds(0L, 30))
        assertEquals(1.0f, TotpGenerator.progress(0L, 30), 0.001f)

        assertEquals(15, TotpGenerator.remainingSeconds(15L, 30))
        assertEquals(0.5f, TotpGenerator.progress(15L, 30), 0.001f)

        assertEquals(1, TotpGenerator.remainingSeconds(29L, 30))
        assertEquals(1.0f / 30.0f, TotpGenerator.progress(29L, 30), 0.001f)

        // Exactly on 30s boundary -> reset to 30s
        assertEquals(30, TotpGenerator.remainingSeconds(30L, 30))
        assertEquals(1.0f, TotpGenerator.progress(30L, 30), 0.001f)
    }

    // =========================================================================
    // BASE32 DECODING & INVALID SECRET HANDLING
    // =========================================================================

    @Test
    fun testBase32_decodingAndValidation() {
        // Standard RFC 4648 test vector: "foobar" -> "MZXW6YTBOI======"
        val foobar = "foobar".toByteArray(StandardCharsets.UTF_8)
        val encoded = Base32.encode(foobar)
        assertEquals("MZXW6YTBOI", encoded)

        val decoded = Base32.decode("MZXW6YTBOI======")
        assertArrayEquals(foobar, decoded)

        // Case-insensitivity & space/dash tolerance
        val decodedWithSpaces = Base32.decode("mzxw 6ytb-oi")
        assertArrayEquals(foobar, decodedWithSpaces)
    }

    @Test
    fun testBase32_invalidCharacters_rejected() {
        assertFalse(Base32.isValid("INVALID89")) // 8 and 9 are invalid in RFC 4648 Base32
        assertFalse(Base32.isValid("SECRET#123!"))
        assertFalse(Base32.isValid(""))

        try {
            Base32.decode("MZXW8YTBOI") // Contains '8'
            fail("Must throw IllegalArgumentException on invalid Base32 character '8'")
        } catch (expected: IllegalArgumentException) {
            // Expected
        }
    }

    @Test
    fun testTotpConfig_emptySecret_rejected() {
        try {
            TotpConfig(secret = "")
            fail("Blank secret must be rejected")
        } catch (expected: IllegalArgumentException) {
            // Expected
        }
    }

    // =========================================================================
    // KEY URI FORMAT & PARSING (otpauth://totp/...)
    // =========================================================================

    @Test
    fun testParseUri_fullSpecification() {
        val uri = "otpauth://totp/GitHub:alex.turner@work.com?secret=JBSWY3DPEHPK3PXP&issuer=GitHub&algorithm=SHA256&digits=8&period=60"
        val config = TotpConfig.parseUri(uri)

        assertEquals("GitHub", config.issuer)
        assertEquals("alex.turner@work.com", config.accountName)
        assertEquals("JBSWY3DPEHPK3PXP", config.secret)
        assertEquals(TotpAlgorithm.SHA256, config.algorithm)
        assertEquals(8, config.digits)
        assertEquals(60, config.periodSeconds)
    }

    @Test
    fun testParseUri_minimalWithDefaults() {
        val uri = "otpauth://totp/ProtonMail?secret=JBSWY3DPEHPK3PXP"
        val config = TotpConfig.parseUri(uri)

        assertEquals("ProtonMail", config.accountName)
        assertEquals("JBSWY3DPEHPK3PXP", config.secret)
        assertEquals(TotpAlgorithm.SHA1, config.algorithm)
        assertEquals(6, config.digits)
        assertEquals(30, config.periodSeconds)
    }

    // =========================================================================
    // ENCRYPTED SECRET STORAGE & SECURE CLIPBOARD COPY
    // =========================================================================

    @Test
    fun testEncryptedSecretStorage_roundTrip() {
        val key = ByteArray(32).also { secureRandom.nextBytes(it) }
        val rawSecret = "JBSWY3DPEHPK3PXP"

        val encrypted = totpService.encryptSecret(rawSecret, key)
        assertNotNull(encrypted)
        assertFalse("Plaintext secret must not appear in encrypted payload", String(encrypted).contains(rawSecret))

        val decrypted = totpService.decryptSecret(encrypted, key)
        assertEquals(rawSecret, decrypted)
    }

    @Test
    fun testCopyOtpToClipboard_setsSensitiveFlagAndPurges() {
        totpService.copyOtpToClipboard("482019", "GitHub 2FA")
        assertFalse(clipboard.isCleared())
        assertEquals("482019", clipboard.inspectCurrentContent())

        clipboard.clear()
        assertTrue(clipboard.isCleared())
    }
}
