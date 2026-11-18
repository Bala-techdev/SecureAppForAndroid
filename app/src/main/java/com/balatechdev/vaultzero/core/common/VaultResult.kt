package com.balatechdev.vaultzero.core.common

sealed interface VaultResult<out T> {
    data class Success<T>(val data: T) : VaultResult<T>
    data class Error(val exception: Throwable, val message: String = exception.localizedMessage ?: "Unknown error") : VaultResult<Nothing>
    data object Loading : VaultResult<Nothing>
}
