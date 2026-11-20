package com.balatechdev.vaultzero.security.auth

sealed interface AuthResult<out T> {
    data class Success<out T>(val data: T) : AuthResult<T>
    data class Failure(val error: AuthError) : AuthResult<Nothing>
}

sealed interface AuthError {
    data object InvalidCredentials : AuthError
    data class WeakPassword(val validation: PasswordValidationResult) : AuthError
    data object PasswordMismatch : AuthError
    data object VaultAlreadyInitialized : AuthError
    data object VaultNotInitialized : AuthError
    data object SessionExpired : AuthError
    data class CryptoError(val message: String) : AuthError
}
