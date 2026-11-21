# VaultZero (Android)

**VaultZero** is a zero-knowledge, offline personal security vault and password manager built natively for Android using Jetpack Compose, SQLCipher, and modern hardware-backed cryptography.

---

## Features

- **Strict Offline Architecture**: Completely isolated from external networks. No `android.permission.INTERNET`, no analytics, no advertising SDKs, and no remote telemetry.
- **Master Password Authentication**: Protected with Argon2id memory-hard key derivation (`t=3, m=64MB, p=4`), cryptographically secure random salts, and constant-time verification.
- **Hierarchical Key Architecture**:
  - **Master Key (MK)**: 256-bit root key wrapped with password-derived key.
  - **Database Key (DBK)**: Subkey derived via HKDF-SHA256 for SQLCipher SQLite encryption.
  - **Document Encryption Key (DEK)**: Dedicated subkey for streaming authenticated document encryption.
- **Credential Vault**: Offline credential manager with AES-256-GCM database encryption, search, category management, favorites, and secure password generator.
- **TOTP 2FA Authenticator**: RFC 6238-compliant time-based one-time password generator supporting HMAC-SHA1, SHA256, and SHA512 offline with local hardware clock.
- **Document Vault**: Encrypted file storage for PDF, JPG, JPEG, and PNG documents with unique 96-bit CSPRNG nonces per document, authenticated metadata (AAD), path traversal sanitization, and streaming 64 KB chunking.
- **Hardware-Backed Biometrics**: Biometric unlock using AndroidX Biometric and Android Keystore (StrongBox / TEE) with automatic re-wrapping and master password fallback on key invalidation.
- **BIP-39 Vault Recovery**: 256-bit recovery secret encoded into a 24-word BIP-39 mnemonic phrase with checksum verification, allowing full vault recovery without the master password.
- **Encrypted Backup & Restore**: Full vault export/import using `.vzf` container format protected by outer AES-256-GCM authenticated encryption and Argon2id passphrase derivation.
- **System Hardening & Privacy**:
  - `FLAG_SECURE` enforced to prevent screenshots, screen recording, and task switcher snapshots.
  - `AndroidSecureClipboard` with `EXTRA_IS_SENSITIVE` (API 33+) / `IS_SENSITIVE` (API 31-32) and automated 30-second clearing.
  - In-memory `MemoryZeroizer` to overwrite key buffers upon session termination.
  - OS backup exclusions configured in `data_extraction_rules.xml` and `backup_rules.xml`.

---

## Technical Specifications

| Parameter | Specification |
|---|---|
| **Platform** | Android 8.0+ (API Level 26 to 34) |
| **Language & UI** | Kotlin 2.0 / Jetpack Compose / Material 3 |
| **Database** | SQLCipher 4.5.5 Encrypted SQLite |
| **Symmetric Encryption** | AES-256-GCM (128-bit authentication tag, 96-bit nonce) |
| **Password KDF** | Argon2id (`t=3, m=64MB, p=4`, 256-bit output) |
| **Subkey Derivation** | HKDF-SHA256 (RFC 5869) |
| **Key Storage** | Android Keystore (hardware-backed SE/StrongBox) |
| **Two-Factor Auth** | TOTP (RFC 6238) / HOTP (RFC 4226) |
| **Recovery Standard** | BIP-0039 (256-bit entropy, 24 English words) |
| **Network Permissions** | NONE (`android.permission.INTERNET` removed) |

---

## Build Instructions

### Prerequisites
- Linux / macOS / Windows
- OpenJDK 17 or OpenJDK 21
- Android SDK (API 34 platform and Build-Tools 34.0.0 or 36.0.0)

### 1. Run Automated Test Suite
To compile and execute all 128 unit and scenario tests:
```bash
bash build_and_test.sh
```

### 2. Build and Sign Release APK
To compile Android resources with `aapt2`, translate bytecode with `d8`, align with `zipalign`, and cryptographically sign with `apksigner`:
```bash
bash build_release_apk.sh
```

### 3. Release Artifact Location
Upon successful execution, the signed production release APK is generated at:
```
build/outputs/apk/release/VaultZero-v1.0.0-release-signed.apk
```

To verify the digital signature:
```bash
apksigner verify --verbose build/outputs/apk/release/VaultZero-v1.0.0-release-signed.apk
```

---

## Project Structure

```
SecureAppForAndroid/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml              # Offline permissions and component declaration
│   │   ├── java/com/balatechdev/vaultzero/
│   │   │   ├── core/                        # Base architecture, common result types, themes
│   │   │   ├── data/                        # Database, repositories, models, backup service
│   │   │   ├── di/                          # Dependency injection container
│   │   │   ├── features/                    # Jetpack Compose UI screens & ViewModels
│   │   │   ├── navigation/                  # NavGraph and screen destination routing
│   │   │   └── security/                    # Cryptographic engines, Argon2id, Keystore, TOTP
│   │   └── res/                             # Vector icons, adaptive launcher, backup rules
│   ├── build.gradle.kts                     # App module configuration & release signing
│   └── proguard-rules.pro                   # R8/ProGuard obfuscation & optimization rules
├── build_and_test.sh                        # Automated test runner (128 tests)
├── build_release_apk.sh                     # Production APK packaging & signing pipeline
├── ARCHITECTURE.md                          # Comprehensive technical architecture
├── SECURITY_DESIGN.md                       # Cryptographic key hierarchy specification
├── SECURITY_AUDIT.md                        # Zero-trust vulnerability audit report
├── TEST_REPORT.md                           # Verification report across 14 test areas
├── SECURITY.md                              # Vulnerability reporting & security practices
├── PRIVACY.md                               # Zero-knowledge privacy policy
└── CHANGELOG.md                             # Release notes & version history
```

---

## License

This software is provided for personal data protection and security management under the project terms.
