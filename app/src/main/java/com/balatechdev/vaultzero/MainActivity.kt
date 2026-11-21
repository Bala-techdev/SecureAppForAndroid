package com.balatechdev.vaultzero

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.balatechdev.vaultzero.core.designsystem.theme.VaultZeroTheme
import com.balatechdev.vaultzero.navigation.VaultNavGraph
import com.balatechdev.vaultzero.security.WindowSecurityManager

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // HARDWARE DEFENSE: Enforce FLAG_SECURE on window before rendering any view
        WindowSecurityManager.enableSecureScreen(window)

        enableEdgeToEdge()

        val appContainer = (application as VaultZeroApplication).container

        setContent {
            VaultZeroTheme {
                val navController = rememberNavController()
                VaultNavGraph(
                    navController = navController,
                    appContainer = appContainer
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        WindowSecurityManager.enableSecureScreen(window)
        val appContainer = (application as? VaultZeroApplication)?.container
        appContainer?.vaultSessionManager?.checkAndExpire()
    }
}
