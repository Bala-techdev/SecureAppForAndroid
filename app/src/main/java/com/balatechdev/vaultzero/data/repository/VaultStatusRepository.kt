package com.balatechdev.vaultzero.data.repository

import com.balatechdev.vaultzero.security.VaultLockState
import com.balatechdev.vaultzero.security.auth.MasterKey
import com.balatechdev.vaultzero.security.auth.MasterKeyHierarchy
import com.balatechdev.vaultzero.security.auth.VaultAuthService
import com.balatechdev.vaultzero.security.auth.VaultSessionManager
import kotlinx.coroutines.flow.StateFlow

interface VaultStatusRepository {
    val lockState: StateFlow<VaultLockState>
    fun unlockVault()
    fun lockVault()
    fun isVaultInitialized(): Boolean
}

class VaultStatusRepositoryImpl(
    val sessionManager: VaultSessionManager = VaultSessionManager(),
    private val authService: VaultAuthService? = null
) : VaultStatusRepository {

    override val lockState: StateFlow<VaultLockState> = sessionManager.lockState

    override fun unlockVault() {
        if (!sessionManager.isUnlocked) {
            val ephemeralHierarchy = MasterKeyHierarchy.fromMasterKey(MasterKey.generate())
            sessionManager.startSession(ephemeralHierarchy)
        }
    }

    override fun lockVault() {
        sessionManager.lock()
    }

    override fun isVaultInitialized(): Boolean = authService?.isInitialized() ?: true
}
