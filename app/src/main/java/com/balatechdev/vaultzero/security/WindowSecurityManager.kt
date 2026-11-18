package com.balatechdev.vaultzero.security

import android.view.Window
import android.view.WindowManager

/**
 * WindowSecurityManager
 *
 * Enforces hardware-backed window privacy defenses:
 * 1. Blocks screenshot capture across all Android versions.
 * 2. Blocks video screen recording and screen casting.
 * 3. Prevents task switcher snapshots from exposing sensitive credentials in memory or disk cache.
 */
object WindowSecurityManager {

    fun enableSecureScreen(window: Window) {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
    }

    fun disableSecureScreen(window: Window) {
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
