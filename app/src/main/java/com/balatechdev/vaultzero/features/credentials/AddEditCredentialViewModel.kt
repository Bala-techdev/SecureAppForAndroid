package com.balatechdev.vaultzero.features.credentials

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.balatechdev.vaultzero.core.base.BaseViewModel
import com.balatechdev.vaultzero.data.model.CredentialRecord
import com.balatechdev.vaultzero.data.model.PasswordStrength
import com.balatechdev.vaultzero.data.repository.CredentialRepository
import com.balatechdev.vaultzero.security.auth.MasterPasswordValidator
import com.balatechdev.vaultzero.security.crypto.PasswordGenerator
import com.balatechdev.vaultzero.security.crypto.PasswordGeneratorRules
import java.util.UUID

data class AddEditCredentialUiState(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val username: String = "",
    val passwordInput: String = "",
    val website: String = "",
    val notes: String = "",
    val totpSecret: String = "",
    val categoryId: String = "cat_logins",
    val isFavorite: Boolean = false,
    val isPasswordVisible: Boolean = false,
    val passwordStrength: PasswordStrength = PasswordStrength.WEAK,
    val generatorRules: PasswordGeneratorRules = PasswordGeneratorRules(),
    val isEditMode: Boolean = false,
    val validationError: String? = null,
    val isSaving: Boolean = false
)

class AddEditCredentialViewModel(
    private val repository: CredentialRepository,
    private val passwordGenerator: PasswordGenerator = PasswordGenerator()
) : BaseViewModel<AddEditCredentialUiState>(AddEditCredentialUiState()) {

    fun loadExisting(id: String) {
        val record = repository.getCredentialById(id) ?: return
        updateState {
            it.copy(
                id = record.id,
                title = record.title,
                username = record.username,
                passwordInput = record.passwordString,
                website = record.website,
                notes = record.notes,
                totpSecret = record.totpSecret ?: "",
                categoryId = record.categoryId,
                isFavorite = record.isFavorite,
                passwordStrength = record.passwordStrength,
                isEditMode = true
            )
        }
    }

    fun onTitleChanged(value: String) = updateState { it.copy(title = value, validationError = null) }
    fun onUsernameChanged(value: String) = updateState { it.copy(username = value, validationError = null) }
    fun onWebsiteChanged(value: String) = updateState { it.copy(website = value) }
    fun onNotesChanged(value: String) = updateState { it.copy(notes = value) }
    fun onTotpSecretChanged(value: String) = updateState { it.copy(totpSecret = value) }
    fun onCategoryChanged(value: String) = updateState { it.copy(categoryId = value) }
    fun onToggleFavorite() = updateState { it.copy(isFavorite = !it.isFavorite) }
    fun onTogglePasswordVisibility() = updateState { it.copy(isPasswordVisible = !it.isPasswordVisible) }

    fun onPasswordChanged(value: String) {
        val chars = value.toCharArray()
        val strength = if (value.isNotEmpty()) {
            MasterPasswordValidator.validate(chars).strength
        } else {
            PasswordStrength.WEAK
        }
        updateState {
            it.copy(
                passwordInput = value,
                passwordStrength = strength,
                validationError = null
            )
        }
    }

    fun generatePassword(rules: PasswordGeneratorRules = _uiState.value.generatorRules) {
        val generated = passwordGenerator.generateString(rules)
        onPasswordChanged(generated)
    }

    fun save(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.title.isBlank()) {
            updateState { it.copy(validationError = "Title cannot be empty") }
            return
        }
        if (state.username.isBlank()) {
            updateState { it.copy(validationError = "Username or email cannot be empty") }
            return
        }
        if (state.passwordInput.isBlank()) {
            updateState { it.copy(validationError = "Password cannot be empty") }
            return
        }

        updateState { it.copy(isSaving = true) }

        val record = CredentialRecord(
            id = state.id,
            title = state.title.trim(),
            username = state.username.trim(),
            passwordChars = state.passwordInput.toCharArray(),
            website = state.website.trim(),
            notes = state.notes.trim(),
            totpSecret = state.totpSecret.trim().ifEmpty { null },
            categoryId = state.categoryId,
            isFavorite = state.isFavorite,
            passwordStrength = state.passwordStrength
        )

        val result = if (state.isEditMode) {
            repository.updateCredential(record)
        } else {
            repository.addCredential(record)
        }

        result.onSuccess {
            onSuccess()
        }.onFailure { error ->
            updateState { it.copy(isSaving = false, validationError = error.message ?: "Failed to save credential") }
        }
    }

    companion object {
        fun provideFactory(
            repository: CredentialRepository,
            passwordGenerator: PasswordGenerator = PasswordGenerator()
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AddEditCredentialViewModel(repository, passwordGenerator) as T
                }
            }
    }
}
