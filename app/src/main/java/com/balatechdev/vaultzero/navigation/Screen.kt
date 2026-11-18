package com.balatechdev.vaultzero.navigation

sealed class Screen(val route: String) {
    // 1. Splash Screen
    data object Splash : Screen("splash")

    // 2. Welcome Screen
    data object Welcome : Screen("welcome")

    // 3. Initial Setup
    data object InitialSetup : Screen("initial_setup")

    // 4. Master Password Creation
    data object MasterPasswordCreation : Screen("master_password_creation")

    // 5. Unlock Screen
    data object Unlock : Screen("unlock")

    // 6. Dashboard
    data object Dashboard : Screen("dashboard")

    // 7. Credential Vault
    data object CredentialVault : Screen("credential_vault")

    // 8. Add Credential
    data object AddCredential : Screen("add_credential")

    // 9. Edit Credential
    data object EditCredential : Screen("edit_credential/{credentialId}") {
        fun createRoute(credentialId: String) = "edit_credential/$credentialId"
    }

    // 10. Credential Details
    data object CredentialDetails : Screen("credential_details/{credentialId}") {
        fun createRoute(credentialId: String) = "credential_details/$credentialId"
    }

    // 11. Document Vault
    data object DocumentVault : Screen("document_vault")

    // 12. Add Document
    data object AddDocument : Screen("add_document")

    // 13. Document Preview
    data object DocumentPreview : Screen("document_preview/{documentId}") {
        fun createRoute(documentId: String) = "document_preview/$documentId"
    }

    // 14. Categories
    data object Categories : Screen("categories")

    // 15. Settings
    data object Settings : Screen("settings")

    // 16. Security Settings
    data object SecuritySettings : Screen("security_settings")

    // 17. Backup and Restore
    data object BackupRestore : Screen("backup_restore")

    // 18. Recovery Screen
    data object Recovery : Screen("recovery")
}
