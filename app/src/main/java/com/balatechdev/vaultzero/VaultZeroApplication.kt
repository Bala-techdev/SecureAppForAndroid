package com.balatechdev.vaultzero

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.os.Bundle
import com.balatechdev.vaultzero.di.AppContainer
import com.balatechdev.vaultzero.di.DefaultAppContainer
import java.util.concurrent.atomic.AtomicInteger

class VaultZeroApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val activeActivityCount = AtomicInteger(0)

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)

        // Install hardened unhandled exception handler to prevent memory dump leaks
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Prevent unhandled crashes from leaving key material in memory
            container.vaultSessionManager.lock()
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // Global Activity Lifecycle Defense: Auto-lock and activity tracking
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {
                activeActivityCount.incrementAndGet()
                container.vaultSessionManager.recordUserActivity()
            }
            override fun onActivityResumed(activity: Activity) {
                container.vaultSessionManager.recordUserActivity()
            }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {
                if (activeActivityCount.decrementAndGet() <= 0) {
                    // App sent to background - trigger inactivity check
                    container.vaultSessionManager.checkAndExpire()
                }
            }
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            // UI hidden: enforce session expiration check immediately
            container.vaultSessionManager.checkAndExpire()
        }
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            // System under heavy memory pressure: lock vault to wipe sensitive key material
            container.vaultSessionManager.lock()
        }
    }
}
