package com.balatechdev.vaultzero.features.credentials

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.balatechdev.vaultzero.core.base.BaseViewModel
import com.balatechdev.vaultzero.data.model.CredentialRecord
import com.balatechdev.vaultzero.data.repository.CredentialRepository
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

data class CredentialVaultUiState(
    val searchQuery: String = "",
    val selectedCategoryId: String = "cat_all",
    val isFavoritesOnly: Boolean = false,
    val credentials: List<CredentialRecord> = emptyList(),
    val filteredCredentials: List<CredentialRecord> = emptyList(),
    val isLoading: Boolean = false
)

class CredentialVaultViewModel(
    private val repository: CredentialRepository
) : BaseViewModel<CredentialVaultUiState>(CredentialVaultUiState()) {

    init {
        repository.credentialsFlow
            .onEach { all ->
                updateState { state ->
                    state.copy(
                        credentials = all,
                        filteredCredentials = applyFilter(all, state.searchQuery, state.selectedCategoryId, state.isFavoritesOnly)
                    )
                }
            }
            .launchIn(viewModelScope)

        refresh()
    }

    fun refresh() {
        runCatching { repository.loadAll() }
    }

    fun onSearchQueryChanged(query: String) {
        updateState { state ->
            state.copy(
                searchQuery = query,
                filteredCredentials = applyFilter(state.credentials, query, state.selectedCategoryId, state.isFavoritesOnly)
            )
        }
    }

    fun onCategorySelected(categoryId: String) {
        updateState { state ->
            state.copy(
                selectedCategoryId = categoryId,
                filteredCredentials = applyFilter(state.credentials, state.searchQuery, categoryId, state.isFavoritesOnly)
            )
        }
    }

    fun onToggleFavoritesOnly() {
        updateState { state ->
            val newFavOnly = !state.isFavoritesOnly
            state.copy(
                isFavoritesOnly = newFavOnly,
                filteredCredentials = applyFilter(state.credentials, state.searchQuery, state.selectedCategoryId, newFavOnly)
            )
        }
    }

    fun onToggleFavorite(id: String) {
        repository.toggleFavorite(id)
    }

    fun onDeleteCredential(id: String) {
        repository.deleteCredential(id)
    }

    private fun applyFilter(
        items: List<CredentialRecord>,
        query: String,
        categoryId: String,
        favoritesOnly: Boolean
    ): List<CredentialRecord> {
        return items.filter { item ->
            val matchesCategory = categoryId == "cat_all" || item.categoryId == categoryId
            val matchesFavorite = !favoritesOnly || item.isFavorite
            val matchesQuery = query.isBlank() ||
                    item.title.contains(query, ignoreCase = true) ||
                    item.username.contains(query, ignoreCase = true) ||
                    item.website.contains(query, ignoreCase = true)
            matchesCategory && matchesFavorite && matchesQuery
        }
    }

    companion object {
        fun provideFactory(repository: CredentialRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return CredentialVaultViewModel(repository) as T
                }
            }
    }
}
