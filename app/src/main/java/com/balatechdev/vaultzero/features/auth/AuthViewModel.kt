package com.balatechdev.vaultzero.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.balatechdev.vaultzero.core.base.BaseViewModel
import com.balatechdev.vaultzero.data.repository.VaultStatusRepository
import com.balatechdev.vaultzero.security.MemoryZeroizer
import com.balatechdev.vaultzero.security.auth.AuthError
import com.balatechdev.vaultzero.security.auth.AuthResult
import com.balatechdev.vaultzero.security.auth.VaultAuthService

data class AuthUiState(
    val passwordInput: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isBiometricAvailable: Boolean = true
)

class AuthViewModel(
    private val vaultStatusRepository: VaultStatusRepository,
    private val vaultAuthService: VaultAuthService? = null
) : BaseViewModel<AuthUiState>(AuthUiState()) {

    fun onPasswordChanged(password: String) {
        updateState { it.copy(passwordInput = password, errorMessage = null) }
    }

    fun unlockWithPassword(onSuccess: () -> Unit) {
        val input = _uiState.value.passwordInput
        if (input.isBlank()) {
            updateState { it.copy(errorMessage = "Master password cannot be empty") }
            return
        }

        val chars = input.toCharArray()
        try {
            if (vaultAuthService != null && vaultAuthService.isInitialized()) {
                when (val result = vaultAuthService.unlockVault(chars)) {
                    is AuthResult.Success -> {
                        vaultStatusRepository.unlockVault()
                        updateState { it.copy(passwordInput = "", errorMessage = null) }
                        onSuccess()
                    }
                    is AuthResult.Failure -> {
                        val message = when (result.error) {
                            is AuthError.InvalidCredentials -> "Incorrect master password. Please verify and try again."
                            is AuthError.SessionExpired -> "Session expired. Please re-authenticate."
                            else -> "Authentication failed. Access denied."
                        }
                        updateState { it.copy(errorMessage = message) }
                    }
                }
            } else {
                vaultStatusRepository.unlockVault()
                updateState { it.copy(passwordInput = "", errorMessage = null) }
                onSuccess()
            }
        } finally {
            MemoryZeroizer.zeroize(chars)
        }
    }

    fun unlockWithBiometrics(onSuccess: () -> Unit) {
        vaultStatusRepository.unlockVault()
        onSuccess()
    }

    companion object {
        fun provideFactory(
            repository: VaultStatusRepository,
            authService: VaultAuthService? = null
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AuthViewModel(repository, authService) as T
                }
            }
    }
}
