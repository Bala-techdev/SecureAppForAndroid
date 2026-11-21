# VaultZero Security & Cryptographic Audit Report

**Application:** VaultZero (Android Offline Zero-Knowledge Security Vault)  
**Package:** `com.balatechdev.vaultzero`  
**Target API:** 34 (Android 14) | **Min API:** 26 (Android 8.0)  
**Audit Date:** November 20, 2026  
**Auditor:** Independent Senior Android Security Engineer & Cryptography Auditor  
**Audit Verdict:** **REJECTED FOR PRODUCTION RELEASE — CRITICAL ARCHITECTURAL & CRYPTOGRAPHIC DEFECTS IDENTIFIED**

---

## 1. Executive Summary

An independent, zero-trust security and cryptographic audit was performed on the existing VaultZero implementation. While previous automated unit tests reported 128/128 passing suites, a comprehensive source-level and architectural analysis revealed that these test suites execute against simulated software providers and in-memory mock repositories, masking multiple critical vulnerabilities, fake implementations, and non-functional hardware Keystore bindings.

The application in its current state cannot be deployed to production. In particular:
1. **A hardcoded symmetric encryption key (`ByteArray(32) { (it * 7).toByte() }`) is embedded directly in UI screens and dependency injection**, encrypting documents and biometrics outside the master password hierarchy.
2. **The database engine is completely non-persistent and fake** (`ConcurrentHashMap` mocks masquerading as SQLCipher), leading to catastrophic data loss upon process exit or device restart.
3. **Android Keystore hardware keys are completely non-functional** because the code attempts to call `key.encoded` on non-exportable hardware-backed keys, which always returns `null` on Android devices.
4. **Biometric authorization is architecturally broken on hardware**, invoking `cipher.doFinal()` without biometric authentication on keys configured with `setUserAuthenticationRequired(true)`.
5. **Streaming AES-256-GCM decryption emits unauthenticated plaintext chunks** before validating the authentication tag (Release of Unverified Plaintext).
6. **Disaster recovery envelopes are stored exclusively in non-persistent memory**, rendering BIP-39 disaster recovery impossible after an app restart.

A total of **9 security findings** have been documented and classified below:
- **3 Critical Severity**
- **3 High Severity**
- **2 Medium Severity**
- **1 Low Severity**

---

## 2. Scope & Target Areas Audited

| # | Audit Area | Status | Primary Findings |
|---|------------|--------|------------------|
| 1 | **AndroidManifest.xml** | **PASS** | INTERNET permission explicitly stripped; `allowBackup="false"`; strict `backup_rules.xml` and `data_extraction_rules.xml`; all internal components unexported; single launcher activity. |
| 2 | **Cryptography** | **CRITICAL FAIL** | Hardcoded symmetric key embedded in UI and DI container; Release of Unverified Plaintext (RUP) in AES-GCM streaming. Argon2id parameters (64MB, t=3, p=4) and HKDF-SHA256 derivation logic are mathematically sound. |
| 3 | **Android Keystore** | **CRITICAL FAIL** | AndroidKeystoreDeviceKeyProvider attempts to read non-exportable `key.encoded` (always null on hardware); Biometric auth attempts `doFinal` without user authentication on hardware-gated keys. |
| 4 | **Database** | **CRITICAL FAIL** | No SQLCipher or SQLite database created on disk. Only in-memory `ConcurrentHashMap` mocks are instantiated. All data vanishes upon application termination. |
| 5 | **Document Vault** | **HIGH FAIL** | Documents encrypted with hardcoded dummy keys in UI screens; streaming decryption writes unverified plaintext chunks to disk before tag verification. |
| 6 | **Disaster Recovery** | **HIGH FAIL** | Recovery envelope is held purely in volatile RAM and is never persisted. BIP-39 256-bit entropy calculation (24 words) is verified and RFC/BIP-compliant, but recovery fails post-restart. |
| 7 | **Backup & Restore** | **MEDIUM FAIL** | Backup encryption (AES-256-GCM + Argon2id) is sound, but "atomic restore" lacks rollback: failures midway destroy existing vault records and documents. |
| 8 | **Application Privacy** | **MEDIUM FAIL** | `FLAG_SECURE` properly enforced; sensitive clipboard flag attached; zero logging. However, domain models construct immutable `java.lang.String` passwords in memory, bypassing zeroization. |

---

## 3. Cryptographic Verification: BIP-39 Recovery Mnemonic

A specific verification was performed on the BIP-39 mnemonic implementation in `Bip39Mnemonic.kt`:
- **Entropy Size:** 256 bits (32 bytes) via CSPRNG `SecureRandom`.
- **Checksum Calculation:** First 8 bits of SHA-256 digest of entropy:
  $$\text{Checksum} = \frac{256}{32} = 8\text{ bits}$$
- **Total Bits:** $256 + 8 = 264\text{ bits}$.
- **Word Count:** $264 / 11 = 24\text{ words}$.
- **Wordlist:** Official 2048-word English BIP-39 dictionary (`Bip39WordList.kt`).
- **Conclusion:** The BIP-39 mathematical formulation correctly adheres to standard specifications (256-bit entropy strictly yields 24 words). However, as documented in finding **VZ-AUDIT-006**, the resulting encrypted key envelope is never persisted to disk.

---

## 4. Detailed Audit Findings

```
================================================================================
CRITICAL SEVERITY FINDINGS
================================================================================
```

### [VZ-AUDIT-001] [CRITICAL] Hardcoded Static Encryption Key in Production DI & UI Screens
- **Affected Files:**
  - `app/src/main/java/com/balatechdev/vaultzero/di/AppContainer.kt` (lines 156, 166, 195, 212, 225)
  - `app/src/main/java/com/balatechdev/vaultzero/features/documents/AddDocumentScreen.kt` (lines 127, 135, 145)
  - `app/src/main/java/com/balatechdev/vaultzero/features/documents/DocumentPreviewScreen.kt` (lines 145, 155, 164)
  - `app/src/main/java/com/balatechdev/vaultzero/features/settings/SecuritySettingsScreen.kt` (lines 191, 192)
- **Exact Problem:**
  A deterministic, hardcoded 32-byte key is defined directly in application code:
  ```kotlin
  // DefaultAppContainer.kt:156
  private val fallbackBootstrapKey = ByteArray(32) { (it * 7).toByte() }

  // AddDocumentScreen.kt:127 & DocumentPreviewScreen.kt:145
  val dummyFallbackKey = ByteArray(32) { (it * 7).toByte() }
  ```
  In `AddDocumentScreen.kt`, documents are imported and encrypted using `masterKey = dummyFallbackKey`. In `DocumentPreviewScreen.kt`, documents are decrypted using `dummyFallbackKey`. In `AppContainer.kt`, repository key providers fall back to `fallbackBootstrapKey` if `currentSession` is null.
- **Potential Attack Scenario:**
  An adversary extracts the application APK, decompiles the DEX bytecode, and obtains the hardcoded 32-byte array `[0x00, 0x07, 0x0E, 0x15, ...]`. Because imported documents in `vault_documents/*.enc` are encrypted with this static key rather than the user's master password-derived key, the attacker can decrypt all stored documents without knowing the master password, recovery phrase, or biometric credentials.
- **Recommended Solution:**
  1. Completely remove `fallbackBootstrapKey` and `dummyFallbackKey` from `AppContainer`, `AddDocumentScreen`, `DocumentPreviewScreen`, and `SecuritySettingsScreen`.
  2. Mandate that all cryptographic operations retrieve subkeys exclusively from `VaultSessionManager.currentSession?.hierarchy`.
  3. If an operation is requested when `currentSession` is null, throw an explicit `VaultLockedException` and route the user to the unlock screen.

---

### [VZ-AUDIT-002] [CRITICAL] Non-Persistent Mock Database & Total Data Loss on Process Termination
- **Affected Files:**
  - `app/src/main/java/com/balatechdev/vaultzero/data/database/EncryptedCredentialDatabase.kt` (lines 28-115)
  - `app/src/main/java/com/balatechdev/vaultzero/data/database/EncryptedDocumentDatabase.kt` (lines 35-115)
  - `app/src/main/java/com/balatechdev/vaultzero/data/database/SQLCipherSchema.kt`
  - `app/src/main/java/com/balatechdev/vaultzero/di/AppContainer.kt` (lines 94-96, 152-154, 181-183)
- **Exact Problem:**
  While `SQLCipherSchema.kt` declares SQL statements and pragma definitions, the application **never initializes SQLite or SQLCipher**. Instead, `DefaultAppContainer` injects `InMemoryEncryptedCredentialDatabase` and `InMemoryEncryptedDocumentDatabase`, which hold all records in memory using `ConcurrentHashMap`. Furthermore, `VaultKeyStorage` is injected as `InMemoryVaultKeyStorage`. No database files or key envelopes are ever persisted to disk.
- **Potential Attack Scenario / System Failure:**
  The application fails the primary requirement of a password and document manager: data persistence. As soon as the user swipes the app away from recent tasks, Android reclaims the process under memory pressure, or the device is rebooted, **all user credentials, document metadata, master key envelopes, and vault settings are completely and irreversibly destroyed**.
- **Recommended Solution:**
  1. Implement a genuine SQLCipher SQLite database engine using `net.zetetic:sqlcipher-android` or Android Room with SQLCipher `SupportFactory`.
  2. Open the encrypted SQLite database on internal disk (`context.getDatabasePath("vaultzero_encrypted.db")`) encrypted with the active session's 256-bit `DBK`.
  3. Persist the `MasterKeyEnvelope` to a secure file in internal storage (`context.filesDir/vault_master.env`) so the vault state survives process death.

---

### [VZ-AUDIT-003] [CRITICAL] Android Keystore Provider Broken by Calling `SecretKey.getEncoded()`
- **Affected Files:**
  - `app/src/main/java/com/balatechdev/vaultzero/security/crypto/DeviceKeyProvider.kt` (lines 132-139)
- **Exact Problem:**
  `AndroidKeystoreDeviceKeyProvider` implements encryption and decryption by attempting to extract raw key bytes from the Android KeyStore:
  ```kotlin
  // DeviceKeyProvider.kt:133 & 138
  val key = getSecretKey()
  return encryptionService.encrypt(plaintext, key.encoded ?: throw CryptoException("Key bytes inaccessible"), associatedData)
  ```
  On Android, keys generated within the `AndroidKeyStore` provider (inside TEE or StrongBox) are non-exportable hardware keys. By architectural design, calling `secretKey.encoded` on an Android KeyStore key **always returns `null`**.
- **Potential Attack Scenario / System Failure:**
  On any real Android device where hardware Keystore is available, invoking `AndroidKeystoreDeviceKeyProvider.encrypt()` or `decrypt()` will unconditionally throw `CryptoException("Key bytes inaccessible")`. Device-bound key wrapping will crash immediately at runtime on actual hardware.
- **Recommended Solution:**
  Refactor `AndroidKeystoreDeviceKeyProvider` to never read `key.encoded`. Initialize `Cipher.getInstance("AES/GCM/NoPadding")` directly with the `SecretKey` reference returned by `KeyStore.getEntry()`. The Android KeyStore provider will execute the cryptographic operation inside the secure hardware environment without exposing key bytes to user space.

---

```
================================================================================
HIGH SEVERITY FINDINGS
================================================================================
```

### [VZ-AUDIT-004] [HIGH] Premature `Cipher.doFinal()` on Biometric Key Requiring User Authentication
- **Affected Files:**
  - `app/src/main/java/com/balatechdev/vaultzero/security/biometric/BiometricKeyManager.kt` (line 79)
  - `app/src/main/java/com/balatechdev/vaultzero/security/biometric/BiometricAuthService.kt` (lines 69-74)
- **Exact Problem:**
  `AndroidKeystoreBiometricKeyManager` configures the Keystore key with:
  ```kotlin
  setUserAuthRequired.invoke(builder, true) // setUserAuthenticationRequired(true)
  ```
  However, in `BiometricAuthServiceImpl.enableBiometricUnlock()`:
  ```kotlin
  val (cipher, nonce) = keyManager.initEncryptCipher()
  val ciphertextAndTag = vaultKey.useKey { keyBytes ->
      cipher.doFinal(keyBytes) // Invoked directly without BiometricPrompt!
  }
  ```
  Android Keystore strictly prohibits executing `cipher.doFinal()` on a key requiring user authentication unless an active hardware authorization token has been issued by a successful `BiometricPrompt` authentication.
- **Potential Attack Scenario / System Failure:**
  On physical Android hardware, calling `cipher.doFinal()` synchronously inside `enableBiometricUnlock()` triggers `android.security.keystore.UserNotAuthenticatedException`. The method catches this exception and returns `false`, preventing users from ever enrolling or enabling biometric unlock.
- **Recommended Solution:**
  Update the biometric enrollment flow to prompt the user with a `BiometricPrompt` initialized with `BiometricPrompt.CryptoObject(cipher)`. Execute `cipher.doFinal()` strictly within the `onAuthenticationSucceeded` callback.

---

### [VZ-AUDIT-005] [HIGH] Release of Unverified Plaintext (RUP) in Streaming AES-GCM Decryption
- **Affected Files:**
  - `app/src/main/java/com/balatechdev/vaultzero/security/crypto/DocumentCryptoEngine.kt` (lines 207-224)
- **Exact Problem:**
  In `DocumentCryptoEngine.decryptStream()`:
  ```kotlin
  while (input.read(buffer).also { bytesRead = it } != -1) {
      val outputChunk = cipher.update(buffer, 0, bytesRead)
      if (outputChunk != null && outputChunk.isNotEmpty()) {
          output.write(outputChunk) // Unauthenticated plaintext emitted!
          totalBytesWritten += outputChunk.size
      }
  }
  val finalBlock = cipher.doFinal() // Tag verified ONLY at the end!
  ```
  In AES-GCM, the 128-bit authentication tag is stored at the end of the ciphertext stream. Calling `cipher.update()` outputs unverified plaintext blocks to the destination `OutputStream` before `cipher.doFinal()` validates the tag.
- **Potential Attack Scenario:**
  If an encrypted document has been corrupted, truncated, or tampered with by an adversary, the decrypted stream will already have written forged/corrupted plaintext to the destination file before `AEADBadTagException` is thrown. Downstream consumers or file parsers reading from this stream may parse unauthenticated, malicious data.
- **Recommended Solution:**
  1. For documents, stream decryption into an isolated temporary staging file, validating `cipher.doFinal()` before moving the file to its final destination. If tag verification fails, immediately zeroize and unlink the staging file.
  2. Alternatively, implement chunked AEAD (e.g., using Google Tink `StreamingAead` or encrypting individual 64KB chunks with separate nonces and tags).

---

### [VZ-AUDIT-006] [HIGH] Disaster Recovery Envelope Non-Persistent (RAM-Only)
- **Affected Files:**
  - `app/src/main/java/com/balatechdev/vaultzero/security/recovery/VaultRecoveryService.kt` (lines 118, 289-300)
  - `app/src/main/java/com/balatechdev/vaultzero/di/AppContainer.kt` (lines 222-228)
- **Exact Problem:**
  `VaultRecoveryServiceImpl` stores the wrapped recovery key envelope in an in-memory field:
  ```kotlin
  private var inMemoryRecoveryEnvelope: RecoveryKeyEnvelope? = null
  ```
  It is never serialized or written to persistent storage.
- **Potential Attack Scenario / System Failure:**
  The user writes down their 24-word BIP-39 recovery phrase during setup. Later, they forget their master password and restart the app or reboot the phone. When they attempt emergency recovery with the mnemonic phrase, `getRecoveryEnvelope()` returns `null`, and the app responds with "No recovery envelope configured for this vault". The user is permanently locked out of their encrypted vault.
- **Recommended Solution:**
  Persist `RecoveryKeyEnvelope` (salt, nonce, wrapped key, and Argon2id parameters) to an encrypted file (`vault_recovery.env`) in app-private storage upon generation.

---

```
================================================================================
MEDIUM SEVERITY FINDINGS
================================================================================
```

### [VZ-AUDIT-007] [MEDIUM] Pseudo-Atomic Restore Leaves Data Inconsistent on Failure
- **Affected Files:**
  - `app/src/main/java/com/balatechdev/vaultzero/data/backup/VaultBackupService.kt` (lines 281-306)
- **Exact Problem:**
  In `VaultBackupServiceImpl.executeAtomicRestore()`:
  ```kotlin
  // Staged files are copied over existing files
  for (doc in pkg.documents) {
      val targetFile = File(documentStorageDir, doc.entity.encryptedFileName)
      val stagedFile = File(tempRestoreDir, doc.entity.encryptedFileName)
      stagedFile.copyTo(targetFile, overwrite = true)
  }
  documentDatabase.documentDao.clear()
  // ... insert documents ...
  credentialDatabase.credentialDao.clear()
  // ... insert credentials ...
  ```
  If an exception occurs during record insertion (e.g., disk full or malformed record), the primary document storage has already been overwritten, and database tables have already been cleared. There is no rollback mechanism.
- **Potential Attack Scenario / System Failure:**
  An interrupted or partially corrupted backup restore permanently wipes the user's existing vault without restoring the new backup.
- **Recommended Solution:**
  Implement true transactional rollback: before modifying active storage, move existing files and database tables to a temporary `.rollback` snapshot. If any stage of restore fails, restore from `.rollback` before re-throwing the exception.

---

### [VZ-AUDIT-008] [MEDIUM] Immutable String Allocation of Plaintext Passwords Bypassing Memory Zeroization
- **Affected Files:**
  - `app/src/main/java/com/balatechdev/vaultzero/data/model/CredentialEntity.kt` (lines 104, 137, 151)
  - `app/src/main/java/com/balatechdev/vaultzero/data/model/CredentialItem.kt` (line 12)
  - `app/src/main/java/com/balatechdev/vaultzero/data/repository/CredentialRepository.kt` (lines 142, 167)
- **Exact Problem:**
  While `CredentialRecord` defines `passwordChars: CharArray`, the codebase frequently converts passwords to immutable `String` instances:
  ```kotlin
  // CredentialEntity.kt:104
  val passwordString: String get() = String(passwordChars)

  // CredentialRepository.kt:167
  val passwordChars = String(passwordBytes, StandardCharsets.UTF_8).toCharArray()
  ```
  In the Java Virtual Machine, `java.lang.String` objects are immutable and allocated on the managed heap. They cannot be zeroized via `Arrays.fill()` and remain in JVM memory until garbage collection and compaction occur.
- **Potential Attack Scenario:**
  In a physical memory dump, core dump, or JVM heap analysis scenario, plaintext passwords remain readable in memory even after the vault has called `lock()` and zeroized its `CharArray` buffers.
- **Recommended Solution:**
  Eliminate all intermediate `String` conversions. Convert `ByteArray` directly to `CharArray` using `CharsetDecoder` with direct buffers, and maintain `CharArray` throughout the presentation layer without allocating `String(passwordChars)`.

---

```
================================================================================
LOW SEVERITY FINDINGS
================================================================================
```

### [VZ-AUDIT-009] [LOW] Ephemeral Plaintext Cache for PDF Rendering
- **Affected Files:**
  - `app/src/main/java/com/balatechdev/vaultzero/data/repository/DocumentRepository.kt` (lines 229-250)
  - `app/src/main/java/com/balatechdev/vaultzero/features/documents/DocumentPreviewScreen.kt` (lines 163-176, 192-198)
- **Exact Problem:**
  Android's `PdfRenderer` requires a seekable `ParcelFileDescriptor`, forcing `decryptDocumentToTempFile()` to write decrypted PDF bytes to `context.cacheDir/preview_tmp`. While cleaned up on `onDispose`, an abrupt process kill (e.g. sudden power off, `SIGKILL`, or OS panic) leaves the plaintext PDF in internal cache storage.
- **Potential Attack Scenario:**
  Forensic extraction of unencrypted cache files if device is seized immediately after an abnormal termination during document preview.
- **Recommended Solution:**
  Execute `cleanTempFiles()` during `Application.onCreate()` to wipe any orphaned plaintext preview files from previous sessions, and mount the cache folder with strict file permissions (`0700`).

---

## 5. Remediation Roadmap

To transition VaultZero from its current state to a production-ready, secure application, the following remediation steps must be completed in order:

1. **Phase 1: Remove Hardcoded Keys & Enforce Session Architecture**
   - Eliminate all instances of `fallbackBootstrapKey` and `dummyFallbackKey`.
   - Wire all UI screens (`AddDocumentScreen`, `DocumentPreviewScreen`, `SecuritySettingsScreen`) to consume keys solely through the authenticated session hierarchy.

2. **Phase 2: Implement Real SQLCipher Database Engine**
   - Replace in-memory mock databases with persistent SQLCipher SQLite storage (`vaultzero_encrypted.db`).
   - Implement persistent envelope storage for both master password and BIP-39 disaster recovery envelopes.

3. **Phase 3: Fix Android Keystore & Biometric Integration**
   - Refactor `AndroidKeystoreDeviceKeyProvider` to operate via `Cipher.init(..., secretKey)` without reading `key.encoded`.
   - Implement `BiometricPrompt.CryptoObject` authorization before invoking `cipher.doFinal()`.

4. **Phase 4: Cryptographic Hardening & Memory Hygiene**
   - Remediate Release of Unverified Plaintext in streaming decryption.
   - Refactor domain and repository models to avoid immutable `String` allocations for plaintext passwords.

---

## 6. Auditor Sign-Off

The findings documented above demonstrate that the application **cannot be certified as secure** in its current implementation despite unit test pass rates. Remediation of all Critical and High findings is required prior to production release.

**Auditor:** Independent Senior Android Security Engineer & Cryptography Auditor  
**Date:** November 20, 2026  
**Status:** Audit Completed — Awaiting Remediation Plan
