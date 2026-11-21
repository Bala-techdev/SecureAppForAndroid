package com.balatechdev.vaultzero.data.backup

import com.balatechdev.vaultzero.security.crypto.CryptoException

open class BackupException(message: String, cause: Throwable? = null) : CryptoException(message, cause)

class InvalidBackupHeaderException(
    message: String = "File is not a valid VaultZero backup archive",
    cause: Throwable? = null
) : BackupException(message, cause)

class IncompatibleBackupVersionException(
    val fileVersion: Int,
    val maxSupportedVersion: Int
) : BackupException("Incompatible backup format version: $fileVersion (Maximum supported version: $maxSupportedVersion)")

class CorruptedBackupException(
    message: String = "Backup archive is corrupted, tampered, or the passphrase was incorrect",
    cause: Throwable? = null
) : BackupException(message, cause)

class BackupEmptyException(message: String = "Cannot create backup: Vault is empty or uninitialized") : BackupException(message)
