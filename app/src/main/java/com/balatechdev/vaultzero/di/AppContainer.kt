package com.balatechdev.vaultzero.di

import android.content.Context
import com.balatechdev.vaultzero.data.repository.VaultStatusRepository
import com.balatechdev.vaultzero.data.repository.VaultStatusRepositoryImpl
import com.balatechdev.vaultzero.security.auth.InMemoryVaultKeyStorage
import com.balatechdev.vaultzero.security.auth.VaultAuthService
import com.balatechdev.vaultzero.security.auth.VaultAuthServiceImpl
import com.balatechdev.vaultzero.security.auth.VaultKeyStorage
import com.balatechdev.vaultzero.security.auth.VaultSessionManager
import com.balatechdev.vaultzero.data.database.EncryptedCredentialDatabase
import com.balatechdev.vaultzero.data.database.InMemoryEncryptedCredentialDatabase
import com.balatechdev.vaultzero.data.repository.CredentialRepository
import com.balatechdev.vaultzero.data.repository.CredentialRepositoryImpl
import com.balatechdev.vaultzero.security.clipboard.InMemorySecureClipboard
import com.balatechdev.vaultzero.security.clipboard.SecureClipboard
import com.balatechdev.vaultzero.security.crypto.PasswordGenerator
import com.balatechdev.vaultzero.security.crypto.AndroidKeystoreDeviceKeyProvider
import com.balatechdev.vaultzero.security.crypto.DecryptionService
import com.balatechdev.vaultzero.security.crypto.DecryptionServiceImpl
import com.balatechdev.vaultzero.security.crypto.DeviceKeyProvider
import com.balatechdev.vaultzero.security.crypto.EncryptionService
import com.balatechdev.vaultzero.security.crypto.EncryptionServiceImpl
import com.balatechdev.vaultzero.security.crypto.KeyDerivationService
import com.balatechdev.vaultzero.security.crypto.KeyDerivationServiceImpl
import com.balatechdev.vaultzero.security.crypto.KeyManagementService
import com.balatechdev.vaultzero.security.crypto.KeyManagementServiceImpl
import com.balatechdev.vaultzero.security.crypto.SoftwareDeviceKeyProvider

import com.balatechdev.vaultzero.data.database.EncryptedDocumentDatabase
import com.balatechdev.vaultzero.data.database.InMemoryEncryptedDocumentDatabase
import com.balatechdev.vaultzero.data.repository.DocumentRepository
import com.balatechdev.vaultzero.data.repository.DocumentRepositoryImpl
import com.balatechdev.vaultzero.security.totp.TotpService
import com.balatechdev.vaultzero.security.totp.TotpServiceImpl

import com.balatechdev.vaultzero.security.biometric.AndroidKeystoreBiometricKeyManager
import com.balatechdev.vaultzero.security.biometric.BiometricAuthService
import com.balatechdev.vaultzero.security.biometric.BiometricAuthServiceImpl
import com.balatechdev.vaultzero.security.biometric.BiometricAvailability
import com.balatechdev.vaultzero.security.biometric.SoftwareBiometricKeyManager
import com.balatechdev.vaultzero.security.crypto.VaultKey

import com.balatechdev.vaultzero.security.recovery.VaultRecoveryService
import com.balatechdev.vaultzero.security.recovery.VaultRecoveryServiceImpl

/**
 * AppContainer
 *
 * Dependency injection foundation providing clean inversion of control.
 * Scalable to Dagger/Hilt or custom modules in subsequent phases.
 */
interface AppContainer {
    val vaultKeyStorage: VaultKeyStorage
    val vaultSessionManager: VaultSessionManager
    val vaultAuthService: VaultAuthService
    val vaultStatusRepository: VaultStatusRepository

    // Cryptographic Core Services
    val encryptionService: EncryptionService
    val decryptionService: DecryptionService
    val keyDerivationService: KeyDerivationService
    val deviceKeyProvider: DeviceKeyProvider
    val keyManagementService: KeyManagementService

    // Credential Vault Services
    val secureClipboard: SecureClipboard
    val passwordGenerator: PasswordGenerator
    val credentialDatabase: EncryptedCredentialDatabase
    val credentialRepository: CredentialRepository

    // TOTP Authenticator Service
    val totpService: TotpService

    // Document Vault Services
    val documentDatabase: EncryptedDocumentDatabase
    val documentRepository: DocumentRepository

    // Biometric Authentication Service
    val biometricAuthService: BiometricAuthService

    // Disaster Recovery Service
    val vaultRecoveryService: VaultRecoveryService
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    override val vaultKeyStorage: VaultKeyStorage by lazy {
        InMemoryVaultKeyStorage()
    }

    override val vaultSessionManager: VaultSessionManager by lazy {
        VaultSessionManager()
    }

    override val vaultAuthService: VaultAuthService by lazy {
        VaultAuthServiceImpl(
            keyStorage = vaultKeyStorage,
            sessionManager = vaultSessionManager
        )
    }

    override val vaultStatusRepository: VaultStatusRepository by lazy {
        VaultStatusRepositoryImpl(
            sessionManager = vaultSessionManager,
            authService = vaultAuthService
        )
    }

    override val encryptionService: EncryptionService by lazy {
        EncryptionServiceImpl()
    }

    override val decryptionService: DecryptionService by lazy {
        DecryptionServiceImpl()
    }

    override val keyDerivationService: KeyDerivationService by lazy {
        KeyDerivationServiceImpl()
    }

    override val deviceKeyProvider: DeviceKeyProvider by lazy {
        if (AndroidKeystoreDeviceKeyProvider.isKeystoreAvailable()) {
            AndroidKeystoreDeviceKeyProvider()
        } else {
            SoftwareDeviceKeyProvider()
        }
    }

    override val keyManagementService: KeyManagementService by lazy {
        KeyManagementServiceImpl(
            keyDerivationService = keyDerivationService,
            encryptionService = encryptionService,
            decryptionService = decryptionService
        )
    }

    override val secureClipboard: SecureClipboard by lazy {
        InMemorySecureClipboard()
    }

    override val passwordGenerator: PasswordGenerator by lazy {
        PasswordGenerator()
    }

    override val credentialDatabase: EncryptedCredentialDatabase by lazy {
        InMemoryEncryptedCredentialDatabase()
    }

    private val fallbackBootstrapKey = ByteArray(32) { (it * 7).toByte() }

    override val credentialRepository: CredentialRepository by lazy {
        val repo = CredentialRepositoryImpl(
            database = credentialDatabase,
            keyProvider = {
                vaultSessionManager.currentSession?.hierarchy?.let { h ->
                    var dbk: ByteArray? = null
                    h.useDatabaseKey { dbk = it.copyOf() }
                    dbk
                } ?: fallbackBootstrapKey
            },
            encryptionService = encryptionService,
            decryptionService = decryptionService
        )
        if (credentialDatabase.credentialDao.getCount() == 0) {
            com.balatechdev.vaultzero.data.model.SampleData.sampleCredentials.forEach { item ->
                repo.addCredential(item.toCredentialRecord())
            }
        }
        repo
    }

    override val totpService: TotpService by lazy {
        TotpServiceImpl(
            clipboard = secureClipboard,
            encryptionService = encryptionService,
            decryptionService = decryptionService
        )
    }

    override val documentDatabase: EncryptedDocumentDatabase by lazy {
        InMemoryEncryptedDocumentDatabase()
    }

    override val documentRepository: DocumentRepository by lazy {
        val storageDir = context.filesDir.resolve("vault_documents")
        val repo = DocumentRepositoryImpl(
            database = documentDatabase,
            storageDir = storageDir,
            keyProvider = {
                vaultSessionManager.currentSession?.hierarchy?.let { h ->
                    var dbk: ByteArray? = null
                    h.useDatabaseKey { dbk = it.copyOf() }
                    dbk
                } ?: fallbackBootstrapKey
            }
        )
        repo
    }

    override val biometricAuthService: BiometricAuthService by lazy {
        val keyMgr = if (AndroidKeystoreDeviceKeyProvider.isKeystoreAvailable()) {
            AndroidKeystoreBiometricKeyManager()
        } else {
            SoftwareBiometricKeyManager()
        }
        val service = BiometricAuthServiceImpl(
            keyManager = keyMgr,
            sessionManager = vaultSessionManager,
            availabilityProvider = { BiometricAvailability.Available }
        )
        val bootstrapKey = VaultKey(fallbackBootstrapKey)
        service.enableBiometricUnlock(bootstrapKey)
        service
    }

    override val vaultRecoveryService: VaultRecoveryService by lazy {
        val service = VaultRecoveryServiceImpl(
            keyStorage = vaultKeyStorage,
            sessionManager = vaultSessionManager
        )
        // Bootstrap sample recovery phrase and envelope for demonstration
        val samplePhrase = service.generateRecoveryPhrase()
        service.createRecoveryEnvelope(
            masterKeyBytes = fallbackBootstrapKey,
            mnemonicWords = samplePhrase
        )
        service
    }
}
