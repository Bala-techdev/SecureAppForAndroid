package com.balatechdev.vaultzero.security.biometric

import com.balatechdev.vaultzero.security.crypto.CryptoException
import com.balatechdev.vaultzero.security.crypto.EncryptedPayload
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * BiometricKeyManager
 *
 * Interfaces with the Android Keystore (TEE / StrongBox) to manage keys that
 * strictly require biometric user authorization.
 *
 * Cryptographic Defenses:
 * 1. [setUserAuthenticationRequired(true)] enforces hardware authorization before Cipher.doFinal().
 * 2. [setInvalidatedByBiometricEnrollment(true)] permanently destroys key validity if a new
 *    biometric (e.g. rogue fingerprint) is enrolled into Android OS.
 */
interface BiometricKeyManager {
    val keyAlias: String
    fun isKeyEnrolled(): Boolean
    fun generateKey()
    fun deleteKey()
    fun getSecretKey(): SecretKey
    fun initEncryptCipher(): Pair<Cipher, ByteArray>
    fun initDecryptCipher(nonce: ByteArray): Cipher
    fun isKeyPermanentlyInvalidated(e: Throwable): Boolean
}

/**
 * AndroidKeystoreBiometricKeyManager
 *
 * Hardware-backed implementation using the AndroidKeyStore SPI.
 */
class AndroidKeystoreBiometricKeyManager(
    override val keyAlias: String = "vaultzero_biometric_auth_key",
    private val secureRandom: SecureRandom = SecureRandom()
) : BiometricKeyManager {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    override fun isKeyEnrolled(): Boolean {
        return try {
            keyStore.containsAlias(keyAlias)
        } catch (e: Exception) {
            false
        }
    }

    override fun generateKey() {
        try {
            val keyGenClass = Class.forName("android.security.keystore.KeyGenParameterSpec\$Builder")
            val keyPropertiesClass = Class.forName("android.security.keystore.KeyProperties")

            val purposeEncrypt = keyPropertiesClass.getField("PURPOSE_ENCRYPT").getInt(null)
            val purposeDecrypt = keyPropertiesClass.getField("PURPOSE_DECRYPT").getInt(null)
            val blockModeGcm = keyPropertiesClass.getField("BLOCK_MODE_GCM").get(null) as String
            val paddingNone = keyPropertiesClass.getField("ENCRYPTION_PADDING_NONE").get(null) as String

            val constructor = keyGenClass.getConstructor(String::class.java, Int::class.javaPrimitiveType)
            val builder = constructor.newInstance(keyAlias, purposeEncrypt or purposeDecrypt)

            val setBlockModes = keyGenClass.getMethod("setBlockModes", Array<String>::class.java)
            setBlockModes.invoke(builder, arrayOf(blockModeGcm))

            val setEncryptionPaddings = keyGenClass.getMethod("setEncryptionPaddings", Array<String>::class.java)
            setEncryptionPaddings.invoke(builder, arrayOf(paddingNone))

            val setKeySize = keyGenClass.getMethod("setKeySize", Int::class.javaPrimitiveType)
            setKeySize.invoke(builder, 256)

            // CRITICAL: Require biometric user authentication
            val setUserAuthRequired = keyGenClass.getMethod("setUserAuthenticationRequired", Boolean::class.javaPrimitiveType)
            setUserAuthRequired.invoke(builder, true)

            // CRITICAL: Invalidate key if new biometrics are enrolled
            try {
                val setInvalidated = keyGenClass.getMethod("setInvalidatedByBiometricEnrollment", Boolean::class.javaPrimitiveType)
                setInvalidated.invoke(builder, true)
            } catch (e: NoSuchMethodException) {
                // API 24+ support
            }

            val buildMethod = keyGenClass.getMethod("build")
            val spec = buildMethod.invoke(builder)

            val keyGenerator = KeyGenerator.getInstance("AES", ANDROID_KEYSTORE)
            val initMethod = keyGenerator.javaClass.getMethod("init", java.security.spec.AlgorithmParameterSpec::class.java)
            initMethod.invoke(keyGenerator, spec)
            keyGenerator.generateKey()
        } catch (e: Exception) {
            throw CryptoException("Failed to generate hardware-bound biometric key: ${e.message}", e)
        }
    }

    override fun deleteKey() {
        try {
            if (keyStore.containsAlias(keyAlias)) {
                keyStore.deleteEntry(keyAlias)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    override fun getSecretKey(): SecretKey {
        return (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: throw CryptoException("Biometric SecretKey not found in Android KeyStore")
    }

    override fun initEncryptCipher(): Pair<Cipher, ByteArray> {
        val key = getSecretKey()
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val nonce = cipher.iv ?: ByteArray(12).also { secureRandom.nextBytes(it) }
        return Pair(cipher, nonce)
    }

    override fun initDecryptCipher(nonce: ByteArray): Cipher {
        val key = getSecretKey()
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val spec = GCMParameterSpec(128, nonce)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        return cipher
    }

    override fun isKeyPermanentlyInvalidated(e: Throwable): Boolean {
        var current: Throwable? = e
        while (current != null) {
            val name = current.javaClass.name
            if (name == "android.security.keystore.KeyPermanentlyInvalidatedException" ||
                current.message?.contains("KeyPermanentlyInvalidatedException", ignoreCase = true) == true
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

/**
 * SoftwareBiometricKeyManager
 *
 * Deterministic software implementation for JVM test suites, simulating
 * hardware Keystore key generation, cryptographic wrapping, and biometric enrollment invalidation.
 */
class SoftwareBiometricKeyManager(
    override val keyAlias: String = "simulated_biometric_key",
    private val secureRandom: SecureRandom = SecureRandom()
) : BiometricKeyManager {

    private var rawKey: ByteArray? = null
    var isKeyInvalidatedFlag: Boolean = false

    override fun isKeyEnrolled(): Boolean = rawKey != null

    override fun generateKey() {
        val key = ByteArray(32)
        secureRandom.nextBytes(key)
        rawKey = key
        isKeyInvalidatedFlag = false
    }

    override fun deleteKey() {
        rawKey = null
        isKeyInvalidatedFlag = false
    }

    override fun getSecretKey(): SecretKey {
        val bytes = rawKey ?: throw CryptoException("No simulated biometric key enrolled")
        return SecretKeySpec(bytes, "AES")
    }

    override fun initEncryptCipher(): Pair<Cipher, ByteArray> {
        if (isKeyInvalidatedFlag) {
            throw CryptoException("KeyPermanentlyInvalidatedException: Biometric enrollment changed")
        }
        val key = getSecretKey()
        val nonce = ByteArray(12).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
        return Pair(cipher, nonce)
    }

    override fun initDecryptCipher(nonce: ByteArray): Cipher {
        if (isKeyInvalidatedFlag) {
            throw CryptoException("KeyPermanentlyInvalidatedException: Biometric enrollment changed")
        }
        val key = getSecretKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
        return cipher
    }

    override fun isKeyPermanentlyInvalidated(e: Throwable): Boolean {
        var current: Throwable? = e
        while (current != null) {
            if (current.message?.contains("KeyPermanentlyInvalidatedException", ignoreCase = true) == true) {
                return true
            }
            current = current.cause
        }
        return false
    }

    fun simulateNewBiometricEnrolled() {
        isKeyInvalidatedFlag = true
    }
}
