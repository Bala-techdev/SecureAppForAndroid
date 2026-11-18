package com.balatechdev.vaultzero.data.model

data class SecurityAuditStatus(
    val healthScore: Int = 96,
    val totalCredentials: Int = 24,
    val totalDocuments: Int = 8,
    val weakPasswordsCount: Int = 1,
    val reusedPasswordsCount: Int = 0,
    val isBiometricsActive: Boolean = true,
    val isScreenshotProtected: Boolean = true,
    val isOfflineEnforced: Boolean = true,
    val lastBackupDate: String = "Yesterday, 8:30 PM"
)
