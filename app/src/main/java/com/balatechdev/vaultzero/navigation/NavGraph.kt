package com.balatechdev.vaultzero.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.balatechdev.vaultzero.di.AppContainer
import com.balatechdev.vaultzero.features.auth.UnlockScreen
import com.balatechdev.vaultzero.features.categories.CategoriesScreen
import com.balatechdev.vaultzero.features.credentials.AddCredentialScreen
import com.balatechdev.vaultzero.features.credentials.CredentialDetailsScreen
import com.balatechdev.vaultzero.features.credentials.CredentialVaultScreen
import com.balatechdev.vaultzero.features.credentials.EditCredentialScreen
import com.balatechdev.vaultzero.features.dashboard.DashboardScreen
import com.balatechdev.vaultzero.features.documents.AddDocumentScreen
import com.balatechdev.vaultzero.features.documents.DocumentPreviewScreen
import com.balatechdev.vaultzero.features.documents.DocumentVaultScreen
import com.balatechdev.vaultzero.features.onboarding.InitialSetupScreen
import com.balatechdev.vaultzero.features.onboarding.MasterPasswordCreationScreen
import com.balatechdev.vaultzero.features.settings.BackupRestoreScreen
import com.balatechdev.vaultzero.features.settings.RecoveryScreen
import com.balatechdev.vaultzero.features.settings.SecuritySettingsScreen
import com.balatechdev.vaultzero.features.settings.SettingsScreen
import com.balatechdev.vaultzero.features.splash.SplashScreen
import com.balatechdev.vaultzero.features.welcome.WelcomeScreen

@Composable
fun VaultNavGraph(
    navController: NavHostController,
    appContainer: AppContainer,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        modifier = modifier
    ) {
        // 1. Splash Screen
        composable(Screen.Splash.route) {
            SplashScreen(
                onSplashCompleted = {
                    navController.navigate(Screen.Welcome.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        // 2. Welcome Screen
        composable(Screen.Welcome.route) {
            WelcomeScreen(
                onCreateVaultClicked = { navController.navigate(Screen.InitialSetup.route) },
                onRestoreVaultClicked = { navController.navigate(Screen.BackupRestore.route) }
            )
        }

        // 3. Initial Setup Screen
        composable(Screen.InitialSetup.route) {
            InitialSetupScreen(
                onNavigateBack = { navController.popBackStack() },
                onProceedToPasswordCreation = { navController.navigate(Screen.MasterPasswordCreation.route) }
            )
        }

        // 4. Master Password Creation Screen
        composable(Screen.MasterPasswordCreation.route) {
            MasterPasswordCreationScreen(
                onNavigateBack = { navController.popBackStack() },
                onVaultInitialized = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                }
            )
        }

        // 5. Unlock Screen
        composable(Screen.Unlock.route) {
            UnlockScreen(
                onUnlockSuccessful = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Unlock.route) { inclusive = true }
                    }
                },
                onEmergencyRecoveryClicked = {
                    navController.navigate(Screen.Recovery.route)
                }
            )
        }

        // 6. Dashboard Screen
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                currentRoute = currentRoute ?: Screen.Dashboard.route,
                onNavigateToRoute = { route ->
                    if (route != Screen.Dashboard.route) {
                        navController.navigate(route) {
                            launchSingleTop = true
                        }
                    }
                },
                onLockVault = {
                    navController.navigate(Screen.Unlock.route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                },
                onNavigateToAddCredential = { navController.navigate(Screen.AddCredential.route) },
                onNavigateToAddDocument = { navController.navigate(Screen.AddDocument.route) },
                onNavigateToCredentialDetails = { id ->
                    navController.navigate(Screen.CredentialDetails.createRoute(id))
                }
            )
        }

        // 7. Credential Vault Screen
        composable(Screen.CredentialVault.route) {
            CredentialVaultScreen(
                currentRoute = currentRoute ?: Screen.CredentialVault.route,
                onNavigateToRoute = { route ->
                    navController.navigate(route) { launchSingleTop = true }
                },
                onNavigateToAddCredential = { navController.navigate(Screen.AddCredential.route) },
                onNavigateToCredentialDetails = { id ->
                    navController.navigate(Screen.CredentialDetails.createRoute(id))
                },
                onLockVault = {
                    navController.navigate(Screen.Unlock.route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                }
            )
        }

        // 8. Add Credential Screen
        composable(Screen.AddCredential.route) {
            AddCredentialScreen(
                onNavigateBack = { navController.popBackStack() },
                onSaveSuccess = { navController.popBackStack() }
            )
        }

        // 9. Edit Credential Screen
        composable(
            route = Screen.EditCredential.route,
            arguments = listOf(navArgument("credentialId") { type = NavType.StringType })
        ) { backStackEntry ->
            val credentialId = backStackEntry.arguments?.getString("credentialId") ?: ""
            EditCredentialScreen(
                credentialId = credentialId,
                onNavigateBack = { navController.popBackStack() },
                onUpdateSuccess = { navController.popBackStack() },
                onDeleteSuccess = {
                    navController.popBackStack(Screen.CredentialVault.route, inclusive = false)
                }
            )
        }

        // 10. Credential Details Screen
        composable(
            route = Screen.CredentialDetails.route,
            arguments = listOf(navArgument("credentialId") { type = NavType.StringType })
        ) { backStackEntry ->
            val credentialId = backStackEntry.arguments?.getString("credentialId") ?: ""
            CredentialDetailsScreen(
                credentialId = credentialId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { id ->
                    navController.navigate(Screen.EditCredential.createRoute(id))
                }
            )
        }

        // 11. Document Vault Screen
        composable(Screen.DocumentVault.route) {
            DocumentVaultScreen(
                currentRoute = currentRoute ?: Screen.DocumentVault.route,
                onNavigateToRoute = { route ->
                    navController.navigate(route) { launchSingleTop = true }
                },
                onNavigateToAddDocument = { navController.navigate(Screen.AddDocument.route) },
                onNavigateToDocumentPreview = { id ->
                    navController.navigate(Screen.DocumentPreview.createRoute(id))
                },
                onLockVault = {
                    navController.navigate(Screen.Unlock.route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                }
            )
        }

        // 12. Add Document Screen
        composable(Screen.AddDocument.route) {
            AddDocumentScreen(
                onNavigateBack = { navController.popBackStack() },
                onSaveSuccess = { navController.popBackStack() }
            )
        }

        // 13. Document Preview Screen
        composable(
            route = Screen.DocumentPreview.route,
            arguments = listOf(navArgument("documentId") { type = NavType.StringType })
        ) { backStackEntry ->
            val documentId = backStackEntry.arguments?.getString("documentId") ?: ""
            DocumentPreviewScreen(
                documentId = documentId,
                onNavigateBack = { navController.popBackStack() },
                onDeleteSuccess = { navController.popBackStack() }
            )
        }

        // 14. Categories Screen
        composable(Screen.Categories.route) {
            CategoriesScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 15. Settings Screen
        composable(Screen.Settings.route) {
            SettingsScreen(
                currentRoute = currentRoute ?: Screen.Settings.route,
                onNavigateToRoute = { route ->
                    navController.navigate(route) { launchSingleTop = true }
                },
                onLockVault = {
                    navController.navigate(Screen.Unlock.route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                }
            )
        }

        // 16. Security Settings Screen
        composable(Screen.SecuritySettings.route) {
            SecuritySettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 17. Backup and Restore Screen
        composable(Screen.BackupRestore.route) {
            BackupRestoreScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 18. Recovery Screen
        composable(Screen.Recovery.route) {
            RecoveryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
