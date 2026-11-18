package com.balatechdev.vaultzero.security

sealed interface VaultLockState {
    data object Uninitialized : VaultLockState
    data object Locked : VaultLockState
    data object Unlocked : VaultLockState
}
