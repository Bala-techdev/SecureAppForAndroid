package com.balatechdev.vaultzero.di

import android.content.Context
import com.balatechdev.vaultzero.data.repository.VaultStatusRepository
import com.balatechdev.vaultzero.data.repository.VaultStatusRepositoryImpl

/**
 * AppContainer
 *
 * Dependency injection foundation providing clean inversion of control.
 * Scalable to Dagger/Hilt or custom modules in subsequent phases.
 */
interface AppContainer {
    val vaultStatusRepository: VaultStatusRepository
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    override val vaultStatusRepository: VaultStatusRepository by lazy {
        VaultStatusRepositoryImpl()
    }
}
