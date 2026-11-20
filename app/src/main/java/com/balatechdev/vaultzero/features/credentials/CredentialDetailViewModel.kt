package com.balatechdev.vaultzero.features.credentials

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.balatechdev.vaultzero.core.base.BaseViewModel
import com.balatechdev.vaultzero.data.model.CredentialRecord
import com.balatechdev.vaultzero.data.repository.CredentialRepository
import com.balatechdev.vaultzero.security.clipboard.SecureClipboard

data class CredentialDetailUiState(
    val credential: CredentialRecord? = null,
    val isPasswordVisible: Boolean = false,
    val snackbarMessage: String? = null,
    val isLoading: Boolean = false
)

class CredentialDetailViewModel(
    private val repository: CredentialRepository,
    private val clipboard: SecureClipboard
) : BaseViewModel<CredentialDetailUiState>(CredentialDetailUiState()) {

    fun load(id: String) {
        val record = repository.getCredentialById(id)
        updateState { it.copy(credential = record) }
    }

    fun togglePasswordVisibility() {
        updateState { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun copyUsername() {
        val cred = _uiState.value.credential ?: return
        clipboard.copySensitive(label = "Username", content = cred.username)
        updateState { it.copy(snackbarMessage = "Username copied (clears in 30s)") }
    }

    fun copyPassword() {
        val cred = _uiState.value.credential ?: return
        clipboard.copySensitive(label = "Password", content = cred.passwordString)
        updateState { it.copy(snackbarMessage = "Password copied to secure clipboard (clears in 30s)") }
    }

    fun copyTotp() {
        val totp = _uiState.value.credential?.totpSecret ?: return
        clipboard.copySensitive(label = "TOTP Secret", content = totp)
        updateState { it.copy(snackbarMessage = "TOTP secret copied (clears in 30s)") }
    }

    fun toggleFavorite() {
        val cred = _uiState.value.credential ?: return
        repository.toggleFavorite(cred.id)
        load(cred.id)
    }

    fun deleteCredential(onSuccess: () -> Unit) {
        val cred = _uiState.value.credential ?: return
        repository.deleteCredential(cred.id)
        onSuccess()
    }

    fun clearSnackbarMessage() {
        updateState { it.copy(snackbarMessage = null) }
    }

    companion object {
        fun provideFactory(
            repository: CredentialRepository,
            clipboard: SecureClipboard
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return CredentialDetailViewModel(repository, clipboard) as T
                }
            }
    }
}
