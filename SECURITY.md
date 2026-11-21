# Security Policy

**VaultZero** is engineered from the ground up on zero-knowledge, defense-in-depth principles. This document outlines the security architecture, threat model, and vulnerability reporting procedures.

---

## Security Model & Design Principles

VaultZero operates on four core security tenets:

1. **Zero-Knowledge Architecture**: All sensitive data (credentials, notes, two-factor secrets, and personal documents) are encrypted locally on the device prior to persistence. Cryptographic keys are derived from user secrets that are never transmitted or stored in plaintext.
2. **Strict Air-Gapped Operation**: The application does not request `android.permission.INTERNET`, contains zero network libraries (no OkHttp, Retrofit, or Volley), and incorporates zero third-party analytics or advertising SDKs.
3. **Cryptographic Domain Separation**: 
   - Root keys are derived using memory-hard Argon2id (`t=3, m=64MB, p=4`).
   - Distinct subkeys (`DBK` for database and `DEK` for documents) are derived using HKDF-SHA256 (RFC 5869) with dedicated context info strings.
   - Master and recovery keys are maintained in separate wrapped envelopes.
4. **Defense-in-Depth Platform Hardening**:
   - `FLAG_SECURE` prevents UI capture in screenshots, screen recorders, and the task switcher.
   - Android 13+ `ClipDescription.EXTRA_IS_SENSITIVE` suppresses clipboard preview and sync.
   - Sensitive memory buffers (`ByteArray` and `CharArray`) are explicitly overwritten with zeroes upon session termination via `MemoryZeroizer`.
   - Android OS backup exclusions prevent unencrypted extraction via cloud backup or ADB.

> **Notice:** VaultZero is designed following established cryptographic specifications (NIST SP 800-38D, RFC 6238, RFC 5869, BIP-0039). The software does not claim independent third-party formal security certification (e.g., FIPS 140-3, Common Criteria, or ISO 27001).

---

## Supported Versions

| Version | Supported | Status |
|---|---|---|
| `1.0.0` | Yes | Active Production Release |

---

## Cryptographic Primitives

| Component | Primitive | Parameter / Standard |
|---|---|---|
| **Data Encryption** | AES-256-GCM | 256-bit key, 96-bit nonce, 128-bit authentication tag |
| **Password Hashing** | Argon2id | `t=3` iterations, `m=64` MB memory, `p=4` lanes, 32-byte salt |
| **Key Derivation** | HKDF-SHA256 | RFC 5869 Extract-and-Expand |
| **Mnemonic Recovery** | BIP-0039 | 256-bit entropy, 24 English words with 8-bit SHA-256 checksum |
| **Two-Factor Auth** | TOTP / HOTP | RFC 6238 / RFC 4226 (HMAC-SHA1, HMAC-SHA256, HMAC-SHA512) |
| **Random Number Generator** | `java.security.SecureRandom` | Hardware-seeded CSPRNG (`/dev/urandom`) |

---

## Reporting a Vulnerability

We welcome security research and responsible vulnerability disclosure. If you discover a potential vulnerability in VaultZero, please report it privately:

1. **Email**: Open a security advisory or email the maintainer directly.
2. **Details to Include**:
   - Description of the potential vulnerability.
   - Steps to reproduce or proof-of-concept scenario.
   - Affected Android versions or hardware devices.
   - Potential impact assessment.
3. **Disclosure Policy**: Please allow reasonable time for investigation and remediation before public disclosure. We commit to acknowledging receipt within 48 hours and providing a remediation timeline.
