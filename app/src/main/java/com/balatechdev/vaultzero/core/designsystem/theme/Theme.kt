package com.balatechdev.vaultzero.core.designsystem.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val VaultDarkColorScheme = darkColorScheme(
    primary = VaultEmeraldPrimary,
    onPrimary = VaultDarkBackground,
    primaryContainer = VaultEmeraldContainerDark,
    onPrimaryContainer = VaultEmeraldPrimary,
    secondary = VaultSecurityInfo,
    onSecondary = VaultDarkBackground,
    background = VaultDarkBackground,
    surface = VaultDarkSurface,
    surfaceVariant = VaultDarkSurfaceVariant,
    onBackground = VaultDarkTextPrimary,
    onSurface = VaultDarkTextPrimary,
    onSurfaceVariant = VaultDarkTextSecondary,
    outline = VaultDarkBorder,
    outlineVariant = VaultDarkBorder.copy(alpha = 0.6f),
    error = VaultSecurityAlert,
    onError = VaultDarkBackground
)

private val VaultLightColorScheme = lightColorScheme(
    primary = VaultEmeraldLight,
    onPrimary = VaultLightSurface,
    primaryContainer = VaultEmeraldContainerLight,
    onPrimaryContainer = VaultEmeraldDark,
    secondary = VaultSecurityInfo,
    onSecondary = VaultLightSurface,
    background = VaultLightBackground,
    surface = VaultLightSurface,
    surfaceVariant = VaultLightSurfaceVariant,
    onBackground = VaultLightTextPrimary,
    onSurface = VaultLightTextPrimary,
    onSurfaceVariant = VaultLightTextSecondary,
    outline = VaultLightBorder,
    outlineVariant = VaultLightBorder.copy(alpha = 0.6f),
    error = VaultSecurityAlert,
    onError = VaultLightSurface
)

@Composable
fun VaultZeroTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) VaultDarkColorScheme else VaultLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VaultTypography,
        content = content
    )
}
