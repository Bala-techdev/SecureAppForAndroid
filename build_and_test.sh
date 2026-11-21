#!/usr/bin/env bash
set -e

JAVA_BIN="/usr/lib/jvm/java-21-openjdk-amd64/bin/java"
GRADLE_LIB="/home/balatechdev/.gradle/wrapper/dists/gradle-8.14.3-all/10utluxaxniiv4wxiphsi49nj/gradle-8.14.3/lib"
KOTLINC_CP="$GRADLE_LIB/*"
BC_JAR="/home/balatechdev/Projects/androidApp/SecureAppForAndroid/app/libs/bcprov-jdk18on-1.85.jar"
ANDROID_JAR="/home/balatechdev/Android/Sdk/platforms/android-37.0/android.jar"
JUNIT_JAR="$GRADLE_LIB/junit-4.13.2.jar"
HAMCREST_JAR="$GRADLE_LIB/hamcrest-core-1.3.jar"
KOTLIN_STDLIB="$GRADLE_LIB/kotlin-stdlib-2.0.21.jar"
COROUTINES_JAR="/home/balatechdev/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm/1.9.0/9beade4c1c1569e4f36cbd2c37e02e3e41502601/kotlinx-coroutines-core-jvm-1.9.0.jar"

BUILD_DIR="/home/balatechdev/Projects/androidApp/build_test/classes"
rm -rf "$BUILD_DIR" && mkdir -p "$BUILD_DIR"

COMPILER_CP="$KOTLIN_STDLIB:$ANDROID_JAR:$JUNIT_JAR:$HAMCREST_JAR:$BC_JAR:$COROUTINES_JAR"

echo "=== Compiling Kotlin sources ==="
$JAVA_BIN -cp "$KOTLINC_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib \
  -cp "$COMPILER_CP" \
  -d "$BUILD_DIR" \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/security/MemoryZeroizer.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/security/VaultLockState.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/data/model/VaultItemType.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/data/model/CredentialItem.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/data/model/CredentialEntity.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/security/auth/Argon2idParameters.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/security/auth/Argon2idKdf.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/security/auth/MasterKey.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/security/auth/HkdfSha256.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/security/auth/MasterKeyHierarchy.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/security/auth/MasterKeyEnvelope.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/security/auth/MasterPasswordValidator.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/security/auth/KeyEnvelopeService.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/security/auth/VaultKeyStorage.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/security/auth/AndroidPersistentVaultKeyStorage.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/security/auth/VaultSessionManager.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/security/auth/AuthResult.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/security/auth/VaultAuthService.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/data/repository/VaultStatusRepository.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/security/crypto/CryptoExceptions.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/security/crypto/EncryptedPayload.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/security/crypto/VaultKey.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/security/crypto/KeyDerivationService.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/security/crypto/EncryptionService.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/security/crypto/DecryptionService.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/security/crypto/DeviceKeyProvider.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/security/crypto/KeyManagementService.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/security/crypto/PasswordGenerator.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/security/clipboard/SecureClipboard.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/security/clipboard/AndroidSecureClipboard.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/data/database/SQLCipherSchema.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/data/database/EncryptedCredentialDatabase.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/data/database/PersistentEncryptedCredentialDatabase.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/data/repository/CredentialRepository.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/security/totp/Base32.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/security/totp/TotpAlgorithm.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/security/totp/TotpConfig.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/security/totp/TotpCode.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/security/totp/TotpGenerator.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/security/totp/TotpService.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/data/model/DocumentItem.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/data/model/DocumentEntity.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/security/crypto/DocumentCryptoEngine.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/data/database/EncryptedDocumentDatabase.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/data/database/PersistentEncryptedDocumentDatabase.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/data/repository/DocumentRepository.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/security/biometric/BiometricTypes.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/security/biometric/BiometricKeyManager.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/security/biometric/BiometricAuthService.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/security/recovery/Bip39WordList.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/security/recovery/Bip39Mnemonic.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/security/recovery/VaultRecoveryService.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/data/backup/BackupExceptions.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/data/backup/BackupModels.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/data/backup/VaultBackupService.kt \
  SecureAppForAndroid/app/src/main/java/com/balatechdev/vaultzero/core/designsystem/components/ZoomMath.kt \
  SecureAppForAndroid/app/src/test/java/com/balatechdev/vaultzero/foundation/FoundationUnitTest.kt \
  SecureAppForAndroid/app/src/test/java/com/balatechdev/vaultzero/security/auth/VaultAuthUnitTest.kt \
  SecureAppForAndroid/app/src/test/java/com/balatechdev/vaultzero/security/crypto/VaultCryptoUnitTest.kt \
  SecureAppForAndroid/app/src/test/java/com/balatechdev/vaultzero/data/database/CredentialDatabaseUnitTest.kt \
  SecureAppForAndroid/app/src/test/java/com/balatechdev/vaultzero/data/repository/CredentialRepositoryUnitTest.kt \
  SecureAppForAndroid/app/src/test/java/com/balatechdev/vaultzero/security/totp/TotpUnitTest.kt \
  SecureAppForAndroid/app/src/test/java/com/balatechdev/vaultzero/data/repository/DocumentVaultUnitTest.kt \
  SecureAppForAndroid/app/src/test/java/com/balatechdev/vaultzero/security/biometric/BiometricAuthUnitTest.kt \
  SecureAppForAndroid/app/src/test/java/com/balatechdev/vaultzero/security/recovery/VaultRecoveryUnitTest.kt \
  SecureAppForAndroid/app/src/test/java/com/balatechdev/vaultzero/data/backup/VaultBackupUnitTest.kt \
  SecureAppForAndroid/app/src/test/java/com/balatechdev/vaultzero/security/SecurityHardeningUnitTest.kt \
  SecureAppForAndroid/app/src/test/java/com/balatechdev/vaultzero/scenarios/ComprehensiveScenarioUnitTest.kt \
  SecureAppForAndroid/app/src/test/java/com/balatechdev/vaultzero/data/PersistenceAndLifecycleUnitTest.kt \
  SecureAppForAndroid/app/src/test/java/com/balatechdev/vaultzero/ui/ZoomableBoxUnitTest.kt

RUNNER_CP="$BUILD_DIR:$COMPILER_CP"

echo "=== Running FoundationUnitTest ==="
$JAVA_BIN -cp "$RUNNER_CP" org.junit.runner.JUnitCore com.balatechdev.vaultzero.foundation.FoundationUnitTest

echo "=== Running VaultAuthUnitTest ==="
$JAVA_BIN -cp "$RUNNER_CP" org.junit.runner.JUnitCore com.balatechdev.vaultzero.security.auth.VaultAuthUnitTest

echo "=== Running VaultCryptoUnitTest ==="
$JAVA_BIN -cp "$RUNNER_CP" org.junit.runner.JUnitCore com.balatechdev.vaultzero.security.crypto.VaultCryptoUnitTest

echo "=== Running CredentialDatabaseUnitTest ==="
$JAVA_BIN -cp "$RUNNER_CP" org.junit.runner.JUnitCore com.balatechdev.vaultzero.data.database.CredentialDatabaseUnitTest

echo "=== Running CredentialRepositoryUnitTest ==="
$JAVA_BIN -cp "$RUNNER_CP" org.junit.runner.JUnitCore com.balatechdev.vaultzero.data.repository.CredentialRepositoryUnitTest

echo "=== Running TotpUnitTest ==="
$JAVA_BIN -cp "$RUNNER_CP" org.junit.runner.JUnitCore com.balatechdev.vaultzero.security.totp.TotpUnitTest

echo "=== Running DocumentVaultUnitTest ==="
$JAVA_BIN -cp "$RUNNER_CP" org.junit.runner.JUnitCore com.balatechdev.vaultzero.data.repository.DocumentVaultUnitTest

echo "=== Running BiometricAuthUnitTest ==="
$JAVA_BIN -cp "$RUNNER_CP" org.junit.runner.JUnitCore com.balatechdev.vaultzero.security.biometric.BiometricAuthUnitTest

echo "=== Running VaultRecoveryUnitTest ==="
$JAVA_BIN -cp "$RUNNER_CP" org.junit.runner.JUnitCore com.balatechdev.vaultzero.security.recovery.VaultRecoveryUnitTest

echo "=== Running VaultBackupUnitTest ==="
$JAVA_BIN -cp "$RUNNER_CP" org.junit.runner.JUnitCore com.balatechdev.vaultzero.data.backup.VaultBackupUnitTest

echo "=== Running SecurityHardeningUnitTest ==="
$JAVA_BIN -cp "$RUNNER_CP" org.junit.runner.JUnitCore com.balatechdev.vaultzero.security.SecurityHardeningUnitTest

echo "=== Running ComprehensiveScenarioUnitTest ==="
$JAVA_BIN -cp "$RUNNER_CP" org.junit.runner.JUnitCore com.balatechdev.vaultzero.scenarios.ComprehensiveScenarioUnitTest

echo "=== Running PersistenceAndLifecycleUnitTest ==="
$JAVA_BIN -cp "$RUNNER_CP" org.junit.runner.JUnitCore com.balatechdev.vaultzero.data.PersistenceAndLifecycleUnitTest

echo "=== Running ZoomableBoxUnitTest ==="
$JAVA_BIN -cp "$RUNNER_CP" org.junit.runner.JUnitCore com.balatechdev.vaultzero.ui.ZoomableBoxUnitTest

echo "=== All Tests Passed Successfully ==="
