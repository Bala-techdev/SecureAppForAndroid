package com.balatechdev.vaultzero.security.totp

import com.balatechdev.vaultzero.security.clipboard.SecureClipboard
import com.balatechdev.vaultzero.security.crypto.DecryptionService
import com.balatechdev.vaultzero.security.crypto.EncryptionService
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import java.nio.charset.StandardCharsets

/**
 * TotpService
 *
 * Coordinates TOTP code generation, live reactive countdown streams,
 * encrypted secret storage, and clipboard protection.
 */
interface TotpService {
    fun parseConfig(rawInput: String): TotpConfig
    fun isValidSecret(secret: String): Boolean
    fun generateCurrentCode(config: TotpConfig, currentTimeSeconds: Long = System.currentTimeMillis() / 1000L): TotpCode
    fun observeLiveCode(config: TotpConfig): Flow<TotpCode>
    fun copyOtpToClipboard(code: String, label: String = "OTP Code")
    fun encryptSecret(secret: String, encryptionKey: ByteArray): ByteArray
    fun decryptSecret(encryptedSecret: ByteArray, encryptionKey: ByteArray): String
}

class TotpServiceImpl(
    private val clipboard: SecureClipboard,
    private val encryptionService: EncryptionService,
    private val decryptionService: DecryptionService
) : TotpService {

    override fun parseConfig(rawInput: String): TotpConfig {
        val trimmed = rawInput.trim()
        return if (trimmed.startsWith("otpauth://", ignoreCase = true)) {
            TotpConfig.parseUri(trimmed)
        } else {
            // Manual entry
            TotpConfig(secret = trimmed)
        }
    }

    override fun isValidSecret(secret: String): Boolean {
        return try {
            val cleaned = secret.trim()
            if (cleaned.startsWith("otpauth://", ignoreCase = true)) {
                val cfg = TotpConfig.parseUri(cleaned)
                Base32.isValid(cfg.secret)
            } else {
                Base32.isValid(cleaned)
            }
        } catch (e: Exception) {
            false
        }
    }

    override fun generateCurrentCode(config: TotpConfig, currentTimeSeconds: Long): TotpCode {
        return TotpGenerator.generateTotp(config, currentTimeSeconds)
    }

    override fun observeLiveCode(config: TotpConfig): Flow<TotpCode> = flow {
        while (currentCoroutineContext().isActive) {
            val nowSec = System.currentTimeMillis() / 1000L
            val code = TotpGenerator.generateTotp(config, nowSec)
            emit(code)
            delay(1000L) // Refresh every second for countdown UI
        }
    }

    override fun copyOtpToClipboard(code: String, label: String) {
        clipboard.copySensitive(label = label, content = code, timeoutSeconds = 30L)
    }

    override fun encryptSecret(secret: String, encryptionKey: ByteArray): ByteArray {
        val bytes = secret.toByteArray(StandardCharsets.UTF_8)
        return encryptionService.encryptToBytes(bytes, encryptionKey)
    }

    override fun decryptSecret(encryptedSecret: ByteArray, encryptionKey: ByteArray): String {
        val decrypted = decryptionService.decrypt(encryptedSecret, encryptionKey)
        return String(decrypted, StandardCharsets.UTF_8)
    }
}
