package com.balatechdev.vaultzero.security.crypto

import com.balatechdev.vaultzero.security.MemoryZeroizer
import com.balatechdev.vaultzero.security.auth.Argon2idParameters
import java.security.SecureRandom

/**
 * WrappedVaultKey
 *
 * Encapsulates the encrypted Vault Encryption Key under three independent defense rings:
 * 1. Password-derived key (Argon2id)
 * 2. Device-bound key (Android Keystore hardware TEE / StrongBox)
 * 3. Recovery-derived key (Independent BIP-39 recovery secret)
 *
 * The raw vault key is NEVER stored in plaintext.
 */
data class WrappedVaultKey(
    val passwordWrappedKey: EncryptedPayload,
    val passwordSalt: ByteArray,
    val passwordParams: Argon2idParameters,
    val recoveryWrappedKey: EncryptedPayload? = null,
    val recoverySalt: ByteArray? = null,
    val deviceWrappedKey: EncryptedPayload? = null,
    val keyVersion: Int = 1
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WrappedVaultKey
        if (passwordWrappedKey != other.passwordWrappedKey) return false
        if (!passwordSalt.contentEquals(other.passwordSalt)) return false
        if (passwordParams != other.passwordParams) return false
        if (recoveryWrappedKey != other.recoveryWrappedKey) return false
        if (recoverySalt != null) {
            if (other.recoverySalt == null) return false
            if (!recoverySalt.contentEquals(other.recoverySalt)) return false
        } else if (other.recoverySalt != null) return false
        if (deviceWrappedKey != other.deviceWrappedKey) return false
        if (keyVersion != other.keyVersion) return false
        return true
    }

    override fun hashCode(): Int {
        var result = passwordWrappedKey.hashCode()
        result = 31 * result + passwordSalt.contentHashCode()
        result = 31 * result + passwordParams.hashCode()
        result = 31 * result + (recoveryWrappedKey?.hashCode() ?: 0)
        result = 31 * result + (recoverySalt?.contentHashCode() ?: 0)
        result = 31 * result + (deviceWrappedKey?.hashCode() ?: 0)
        result = 31 * result + keyVersion
        return result
    }
}

/**
 * KeyManagementService
 *
 * Orchestrates vault encryption key lifecycle:
 * - 256-bit vault encryption key generation.
 * - Multi-layer key envelope wrapping (password, hardware Keystore, recovery phrase).
 * - Secure unwrapping and memory zeroization.
 * - Key rotation architecture.
 */
interface KeyManagementService {
    fun createVaultKey(
        password: CharArray,
        recoverySecret: CharArray? = null,
        deviceKeyProvider: DeviceKeyProvider? = null,
        params: Argon2idParameters = Argon2idParameters.Default
    ): Pair<WrappedVaultKey, VaultKey>

    fun unlockWithPassword(
        password: CharArray,
        wrappedKey: WrappedVaultKey
    ): VaultKey

    fun unlockWithRecoverySecret(
        recoverySecret: CharArray,
        wrappedKey: WrappedVaultKey
    ): VaultKey

    fun unlockWithDeviceKey(
        deviceKeyProvider: DeviceKeyProvider,
        wrappedKey: WrappedVaultKey
    ): VaultKey

    // Key Rotation Architecture
    fun rotateMasterPassword(
        currentVaultKey: VaultKey,
        newPassword: CharArray,
        existingWrappedKey: WrappedVaultKey,
        params: Argon2idParameters = Argon2idParameters.Default
    ): WrappedVaultKey

    fun rotateRecoverySecret(
        currentVaultKey: VaultKey,
        newRecoverySecret: CharArray,
        existingWrappedKey: WrappedVaultKey,
        params: Argon2idParameters = Argon2idParameters.Default
    ): WrappedVaultKey

    fun rotateDeviceKey(
        currentVaultKey: VaultKey,
        deviceKeyProvider: DeviceKeyProvider,
        existingWrappedKey: WrappedVaultKey
    ): WrappedVaultKey

    fun rotateVaultKey(
        oldVaultKey: VaultKey,
        password: CharArray,
        recoverySecret: CharArray? = null,
        deviceKeyProvider: DeviceKeyProvider? = null,
        params: Argon2idParameters = Argon2idParameters.Default
    ): Pair<WrappedVaultKey, VaultKey>
}

class KeyManagementServiceImpl(
    private val keyDerivationService: KeyDerivationService = KeyDerivationServiceImpl(),
    private val encryptionService: EncryptionService = EncryptionServiceImpl(),
    private val decryptionService: DecryptionService = DecryptionServiceImpl(),
    private val secureRandom: SecureRandom = SecureRandom()
) : KeyManagementService {

    // REQUIREMENT 1: Generate 256-bit random vault encryption key and wrap it
    override fun createVaultKey(
        password: CharArray,
        recoverySecret: CharArray?,
        deviceKeyProvider: DeviceKeyProvider?,
        params: Argon2idParameters
    ): Pair<WrappedVaultKey, VaultKey> {
        val vaultKey = VaultKey.generate(secureRandom)

        // Password envelope
        val passwordSalt = keyDerivationService.generateSalt(32)
        val passwordKek = keyDerivationService.deriveKeyFromPassword(password, passwordSalt, params)
        val passwordWrapped = try {
            encryptionService.encrypt(vaultKey.copyBytes(), passwordKek)
        } finally {
            MemoryZeroizer.zeroize(passwordKek)
        }

        // Recovery envelope (optional)
        var recoveryWrapped: EncryptedPayload? = null
        var recoverySalt: ByteArray? = null
        if (recoverySecret != null && recoverySecret.isNotEmpty()) {
            recoverySalt = keyDerivationService.generateSalt(32)
            val recoveryKek = keyDerivationService.deriveKeyFromRecoverySecret(recoverySecret, recoverySalt, params)
            recoveryWrapped = try {
                encryptionService.encrypt(vaultKey.copyBytes(), recoveryKek)
            } finally {
                MemoryZeroizer.zeroize(recoveryKek)
            }
        }

        // Device-bound Keystore envelope (optional)
        val deviceWrapped = deviceKeyProvider?.encrypt(vaultKey.copyBytes())

        val wrappedVaultKey = WrappedVaultKey(
            passwordWrappedKey = passwordWrapped,
            passwordSalt = passwordSalt,
            passwordParams = params,
            recoveryWrappedKey = recoveryWrapped,
            recoverySalt = recoverySalt,
            deviceWrappedKey = deviceWrapped,
            keyVersion = 1
        )

        return Pair(wrappedVaultKey, vaultKey)
    }

    override fun unlockWithPassword(
        password: CharArray,
        wrappedKey: WrappedVaultKey
    ): VaultKey {
        val passwordKek = keyDerivationService.deriveKeyFromPassword(
            password,
            wrappedKey.passwordSalt,
            wrappedKey.passwordParams
        )
        try {
            val decryptedBytes = decryptionService.decrypt(wrappedKey.passwordWrappedKey, passwordKek)
            return VaultKey(decryptedBytes)
        } finally {
            MemoryZeroizer.zeroize(passwordKek)
        }
    }

    override fun unlockWithRecoverySecret(
        recoverySecret: CharArray,
        wrappedKey: WrappedVaultKey
    ): VaultKey {
        val salt = wrappedKey.recoverySalt
            ?: throw CryptoException("Vault was not initialized with an emergency recovery key")
        val wrappedPayload = wrappedKey.recoveryWrappedKey
            ?: throw CryptoException("Recovery wrapped key not found")

        val recoveryKek = keyDerivationService.deriveKeyFromRecoverySecret(
            recoverySecret,
            salt,
            wrappedKey.passwordParams
        )
        try {
            val decryptedBytes = decryptionService.decrypt(wrappedPayload, recoveryKek)
            return VaultKey(decryptedBytes)
        } finally {
            MemoryZeroizer.zeroize(recoveryKek)
        }
    }

    override fun unlockWithDeviceKey(
        deviceKeyProvider: DeviceKeyProvider,
        wrappedKey: WrappedVaultKey
    ): VaultKey {
        val deviceWrapped = wrappedKey.deviceWrappedKey
            ?: throw CryptoException("Vault key is not wrapped with a device-bound key")

        val decryptedBytes = deviceKeyProvider.decrypt(deviceWrapped)
        return VaultKey(decryptedBytes)
    }

    // REQUIREMENT 10: Key rotation architecture
    override fun rotateMasterPassword(
        currentVaultKey: VaultKey,
        newPassword: CharArray,
        existingWrappedKey: WrappedVaultKey,
        params: Argon2idParameters
    ): WrappedVaultKey {
        val newPasswordSalt = keyDerivationService.generateSalt(32)
        val newPasswordKek = keyDerivationService.deriveKeyFromPassword(newPassword, newPasswordSalt, params)

        val newPasswordWrapped = try {
            encryptionService.encrypt(currentVaultKey.copyBytes(), newPasswordKek)
        } finally {
            MemoryZeroizer.zeroize(newPasswordKek)
        }

        return existingWrappedKey.copy(
            passwordWrappedKey = newPasswordWrapped,
            passwordSalt = newPasswordSalt,
            passwordParams = params,
            keyVersion = existingWrappedKey.keyVersion + 1
        )
    }

    override fun rotateRecoverySecret(
        currentVaultKey: VaultKey,
        newRecoverySecret: CharArray,
        existingWrappedKey: WrappedVaultKey,
        params: Argon2idParameters
    ): WrappedVaultKey {
        val newRecoverySalt = keyDerivationService.generateSalt(32)
        val newRecoveryKek = keyDerivationService.deriveKeyFromRecoverySecret(newRecoverySecret, newRecoverySalt, params)

        val newRecoveryWrapped = try {
            encryptionService.encrypt(currentVaultKey.copyBytes(), newRecoveryKek)
        } finally {
            MemoryZeroizer.zeroize(newRecoveryKek)
        }

        return existingWrappedKey.copy(
            recoveryWrappedKey = newRecoveryWrapped,
            recoverySalt = newRecoverySalt,
            keyVersion = existingWrappedKey.keyVersion + 1
        )
    }

    override fun rotateDeviceKey(
        currentVaultKey: VaultKey,
        deviceKeyProvider: DeviceKeyProvider,
        existingWrappedKey: WrappedVaultKey
    ): WrappedVaultKey {
        deviceKeyProvider.rotateKey()
        val newDeviceWrapped = deviceKeyProvider.encrypt(currentVaultKey.copyBytes())

        return existingWrappedKey.copy(
            deviceWrappedKey = newDeviceWrapped,
            keyVersion = existingWrappedKey.keyVersion + 1
        )
    }

    override fun rotateVaultKey(
        oldVaultKey: VaultKey,
        password: CharArray,
        recoverySecret: CharArray?,
        deviceKeyProvider: DeviceKeyProvider?,
        params: Argon2idParameters
    ): Pair<WrappedVaultKey, VaultKey> {
        val (newWrappedKey, newVaultKey) = createVaultKey(password, recoverySecret, deviceKeyProvider, params)
        return Pair(newWrappedKey.copy(keyVersion = 1), newVaultKey)
    }
}
