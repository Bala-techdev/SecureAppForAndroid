package com.balatechdev.vaultzero.data.repository

import com.balatechdev.vaultzero.security.VaultLockState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface VaultStatusRepository {
    val lockState: StateFlow<VaultLockState>
    fun unlockVault()
    fun lockVault()
    fun isVaultInitialized(): Boolean
}

class VaultStatusRepositoryImpl : VaultStatusRepository {
    private val _lockState = MutableStateFlow<VaultLockState>(VaultLockState.Locked)
    override val lockState: StateFlow<VaultLockState> = _lockState.asStateFlow()

    override fun unlockVault() {
        _lockState.value = VaultLockState.Unlocked
    }

    override fun lockVault() {
        _lockState.value = VaultLockState.Locked
    }

    override fun isVaultInitialized(): Boolean = true
}
