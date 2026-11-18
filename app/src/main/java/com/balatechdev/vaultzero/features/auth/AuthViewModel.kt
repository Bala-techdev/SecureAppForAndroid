package com.balatechdev.vaultzero.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.balatechdev.vaultzero.core.base.BaseViewModel
import com.balatechdev.vaultzero.data.repository.VaultStatusRepository
import kotlinx.coroutines.flow.asStateFlow

data class AuthUiState(
    val passwordInput: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isBiometricAvailable: Boolean = true
)

class AuthViewModel(
    private val vaultStatusRepository: VaultStatusRepository
) : BaseViewModel<AuthUiState>(AuthUiState()) {

    fun onPasswordChanged(password: String) {
        updateState { it.copy(passwordInput = password, errorMessage = null) }
    }

    fun unlockWithPassword(onSuccess: () -> Unit) {
        if (_uiState.value.passwordInput.isBlank()) {
            updateState { it.copy(errorMessage = "Master password cannot be empty") }
            return
        }

        // Level 1 foundation: triggers repository unlock
        vaultStatusRepository.unlockVault()
        updateState { it.copy(passwordInput = "", errorMessage = null) }
        onSuccess()
    }

    fun unlockWithBiometrics(onSuccess: () -> Unit) {
        vaultStatusRepository.unlockVault()
        onSuccess()
    }

    companion object {
        fun provideFactory(repository: VaultStatusRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AuthViewModel(repository) as T
                }
            }
    }
}
