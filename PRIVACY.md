# Privacy Policy

**Effective Date:** November 20, 2026  
**Application:** VaultZero (`com.balatechdev.vaultzero`)

---

## 1. Zero Data Collection Principle

VaultZero is built with an absolute privacy-by-design architecture:

- **No Personal Data Collected**: We do not collect, process, transmit, or monetize any personal data.
- **No Analytics or Telemetry**: VaultZero contains zero tracking software, crash reporting SDKs (no Firebase Crashlytics, Sentry, or Bugsnag), and zero usage analytics.
- **No Advertising**: VaultZero contains no advertising SDKs or tracking identifiers (no Google Advertising ID, IDFA, or device fingerprinting).
- **No Cloud Synchronization**: VaultZero does not connect to any remote server or cloud service. All data resides exclusively on your local device.

---

## 2. Technical Safeguards & Local Storage

- **Offline Enforcement**: The application requests zero network permissions. `android.permission.INTERNET`, `ACCESS_NETWORK_STATE`, and `ACCESS_WIFI_STATE` are removed from the manifest.
- **Local Encryption**: All credentials, notes, TOTP secrets, and personal documents stored in VaultZero are encrypted locally using AES-256-GCM.
- **No OS Cloud Backup**: VaultZero explicitly disables Android cloud backup (`android:allowBackup="false"` and `android:dataExtractionRules`). Your encrypted vault will not be transmitted to Google Drive or device-to-device migration channels without your deliberate manual export.
- **Clipboard Privacy**: When copying credentials or authentication codes, VaultZero marks the clipboard content with Android's sensitive content flag (`ClipDescription.EXTRA_IS_SENSITIVE`) to suppress clipboard history and cloud sync, and automatically purges the clipboard after 30 seconds.
- **Window Privacy**: Screen capture, video recording, and task switcher snapshots are blocked via `FLAG_SECURE`.

---

## 3. Data Ownership and Export

You retain 100% ownership and control of your data. You may create manual, encrypted backups at any time using the Storage Access Framework (SAF). Backups exported by VaultZero are encrypted with outer AES-256-GCM authenticated encryption using an Argon2id-derived key from your chosen backup passphrase.

---

## 4. Children's Privacy

VaultZero does not collect any data from any user, including children under the age of 13.

---

## 5. Contact

For privacy-related questions or technical audits, consult the open-source repository documentation or contact the development team.
