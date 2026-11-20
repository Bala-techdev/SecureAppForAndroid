package com.balatechdev.vaultzero.features.credentials

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.balatechdev.vaultzero.core.designsystem.components.CategoryFilterChips
import com.balatechdev.vaultzero.core.designsystem.components.EmptyStateView
import com.balatechdev.vaultzero.core.designsystem.components.VaultBottomNavigationBar
import com.balatechdev.vaultzero.core.designsystem.components.VaultCard
import com.balatechdev.vaultzero.core.designsystem.components.VaultSearchField
import com.balatechdev.vaultzero.core.designsystem.components.VaultTopAppBar
import com.balatechdev.vaultzero.core.designsystem.theme.VaultCategoryOrange
import com.balatechdev.vaultzero.core.designsystem.theme.VaultEmeraldPrimary
import com.balatechdev.vaultzero.core.designsystem.theme.VaultSecurityWarning
import com.balatechdev.vaultzero.data.model.CredentialItem
import com.balatechdev.vaultzero.data.model.DefaultCategories
import com.balatechdev.vaultzero.data.model.SampleData
import com.balatechdev.vaultzero.data.model.toCredentialItem
import com.balatechdev.vaultzero.data.repository.CredentialRepository
import androidx.compose.runtime.collectAsState

@Composable
fun CredentialVaultScreen(
    currentRoute: String,
    repository: CredentialRepository? = null,
    onNavigateToRoute: (String) -> Unit,
    onNavigateToAddCredential: () -> Unit,
    onNavigateToCredentialDetails: (String) -> Unit,
    onLockVault: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("cat_all") }

    val repositoryItems by repository?.credentialsFlow?.collectAsState(initial = emptyList())
        ?: remember { mutableStateOf(emptyList()) }

    val allCredentials: List<CredentialItem> = if (repository != null) {
        if (repositoryItems.isEmpty()) {
            repository.loadAll().map { it.toCredentialItem() }
        } else {
            repositoryItems.map { it.toCredentialItem() }
        }
    } else {
        SampleData.sampleCredentials
    }

    val filteredList = allCredentials.filter { item ->
        val matchesCategory = selectedCategory == "cat_all" || item.categoryId == selectedCategory
        val matchesQuery = searchQuery.isBlank() ||
                item.title.contains(searchQuery, ignoreCase = true) ||
                item.username.contains(searchQuery, ignoreCase = true)
        matchesCategory && matchesQuery
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            VaultTopAppBar(
                title = "Credentials",
                actions = {
                    IconButton(onClick = onLockVault) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock",
                            tint = VaultSecurityWarning
                        )
                    }
                }
            )
        },
        bottomBar = {
            VaultBottomNavigationBar(
                currentRoute = currentRoute,
                onNavigateToRoute = onNavigateToRoute
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAddCredential,
                containerColor = VaultEmeraldPrimary,
                contentColor = MaterialTheme.colorScheme.background,
                shape = CircleShape
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Credential")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Search Bar
            VaultSearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholderText = "Search by service, username..."
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Category Chips
            CategoryFilterChips(
                categories = DefaultCategories,
                selectedCategoryId = selectedCategory,
                onCategorySelected = { selectedCategory = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Items List
            if (filteredList.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.Password,
                    title = "No Credentials Found",
                    description = if (searchQuery.isNotEmpty()) "No results matching \"$searchQuery\"" else "Add your first offline credential",
                    actionButtonText = "+ Add Credential",
                    onActionClicked = onNavigateToAddCredential
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredList) { item ->
                        CredentialListItem(
                            item = item,
                            onClick = { onNavigateToCredentialDetails(item.id) }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CredentialListItem(
    item: com.balatechdev.vaultzero.data.model.CredentialItem,
    onClick: () -> Unit
) {
    VaultCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(VaultEmeraldPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.title.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = VaultEmeraldPrimary
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.username,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (item.isFavorite) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Favorite",
                    tint = VaultCategoryOrange,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
