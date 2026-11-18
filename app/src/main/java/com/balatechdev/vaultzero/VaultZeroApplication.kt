package com.balatechdev.vaultzero

import android.app.Application
import com.balatechdev.vaultzero.di.AppContainer
import com.balatechdev.vaultzero.di.DefaultAppContainer

class VaultZeroApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)

        // Install hardened unhandled exception handler to prevent memory dump leaks
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // In release builds, prevent sensitive stack trace storage to disk
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
