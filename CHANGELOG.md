# Changelog

All notable changes to the **VaultZero** Android application are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0] - 2026-11-20

### Initial Production Release

#### Added
- **Authentication & KDF**:
  - Master password creation, confirmation, and strength validation.
  - Argon2id memory-hard key derivation (`t=3, m=64MB, p=4`) with cryptographically secure 32-byte salts.
  - Automatic inactivity session expiration with memory zeroization.
  - Constant-time verification logic to defend against timing attacks.
- **Cryptographic Subsystems**:
  - AES-256-GCM authenticated encryption conforming to NIST SP 800-38D.
  - Unique 96-bit CSPRNG nonces per encryption operation.
  - Multi-tier key hierarchy: Master Key (`MK`), Database Key (`DBK`), and Document Encryption Key (`DEK`) derived via HKDF-SHA256 (RFC 5869).
  - Multi-pass file zeroization prior to unlinking/deletion on disk.
- **Credential Vault**:
  - Encrypted SQLite database powered by SQLCipher 4.5.5.
  - CRUD operations for credentials (title, username, password, website, notes, TOTP secret, category, favorites).
  - Integrated customizable password generator (length, uppercase, lowercase, digits, symbols).
  - Password strength meter and visibility toggling.
- **Two-Factor Authenticator (TOTP)**:
  - RFC 6238 and RFC 4226 compliant offline code generator.
  - HMAC-SHA1, HMAC-SHA256, and HMAC-SHA512 algorithm support.
  - 30-second countdown timer with progress indicator.
- **Document Vault**:
  - Secure offline document import via Android Storage Access Framework (SAF).
  - Support for PDF, JPG, JPEG, and PNG document formats.
  - 64 KB streaming encryption for large documents (tested with 10+ MB payloads).
  - In-memory rendering for images with zero disk footprint; volatile temporary file handling for PDF previews.
- **Biometric Authentication**:
  - AndroidX Biometric and Android Keystore integration (StrongBox / TEE).
  - Authorizes cryptographic operations via hardware-bound keys.
  - Handles key invalidation on new biometric enrollment and falls back to master password.
- **Vault Recovery System**:
  - 256-bit recovery secret with 24-word BIP-39 mnemonic generation and checksum validation.
  - Recovery envelope unwraps Master Key and enables master password reset while preserving all data.
- **Encrypted Backup & Restore**:
  - `.vzf` container export and import via Storage Access Framework.
  - Dual-layer AES-256-GCM authenticated encryption under user backup passphrase.
  - All-or-nothing atomic staging transaction to prevent partial restoration.
  - Hardware-independent migration path to restore onto new Android devices.
- **Security Hardening**:
  - Removed `android.permission.INTERNET` and network permissions.
  - Zero analytics, telemetry, or advertising SDKs.
  - Window protection via `FLAG_SECURE` against screenshots, video recording, and task switcher snapshots.
  - Android 13+ `ClipDescription.EXTRA_IS_SENSITIVE` and 30-second automated clipboard clear.
  - Explicit OS backup exclusion rules (`data_extraction_rules.xml` and `backup_rules.xml`).
- **UI & Polishing**:
  - Modern dark and professional light theme using Material 3 and Jetpack Compose.
  - Vector adaptive application icons (`ic_launcher.xml` and `ic_launcher_round.xml`).
  - Empty states, search filtering, category management, and loading indicators.
  - High-contrast accessible touch targets (minimum 48dp).

#### Security & Quality Verification
- 128 automated unit, integration, and scenario tests passed across 12 test suites.
- Formal zero-trust security audit documented in `SECURITY_AUDIT.md`.
- Comprehensive scenario testing documented in `TEST_REPORT.md`.
- Digitally signed production release APK generated and verified using APK Signature Schemes v1, v2, and v3.
