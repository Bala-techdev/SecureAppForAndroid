package com.balatechdev.vaultzero.features.documents

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.balatechdev.vaultzero.core.designsystem.components.EmptyStateView
import com.balatechdev.vaultzero.core.designsystem.components.VaultBottomNavigationBar
import com.balatechdev.vaultzero.core.designsystem.components.VaultCard
import com.balatechdev.vaultzero.core.designsystem.components.VaultSearchField
import com.balatechdev.vaultzero.core.designsystem.components.VaultTopAppBar
import com.balatechdev.vaultzero.core.designsystem.theme.VaultCategoryOrange
import com.balatechdev.vaultzero.core.designsystem.theme.VaultCategoryPurple
import com.balatechdev.vaultzero.core.designsystem.theme.VaultEmeraldPrimary
import com.balatechdev.vaultzero.core.designsystem.theme.VaultSecurityWarning
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.collectAsState
import com.balatechdev.vaultzero.data.model.DocumentCategory
import com.balatechdev.vaultzero.data.model.DocumentItem
import com.balatechdev.vaultzero.data.model.SampleData
import com.balatechdev.vaultzero.data.repository.DocumentRepository

@Composable
fun DocumentVaultScreen(
    currentRoute: String,
    repository: DocumentRepository? = null,
    onNavigateToRoute: (String) -> Unit,
    onNavigateToAddDocument: () -> Unit,
    onNavigateToDocumentPreview: (String) -> Unit,
    onLockVault: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf("cat_all") }

    val dbItems by repository?.documentsFlow?.collectAsState(initial = emptyList())
        ?: remember { mutableStateOf(emptyList()) }

    val allDocuments = if (repository != null) {
        val list = if (dbItems.isEmpty()) repository.getAllDocuments() else dbItems
        if (list.isEmpty()) {
            SampleData.sampleDocuments
        } else {
            list.map { it.toDocumentItem() }
        }
    } else {
        SampleData.sampleDocuments
    }

    val filteredList = allDocuments.filter { doc ->
        val matchesCategory = selectedCategoryId == "cat_all" || doc.categoryId == selectedCategoryId
        val matchesQuery = searchQuery.isBlank() ||
                doc.title.contains(searchQuery, ignoreCase = true) ||
                doc.fileName.contains(searchQuery, ignoreCase = true) ||
                doc.notes.contains(searchQuery, ignoreCase = true)
        matchesCategory && matchesQuery
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            VaultTopAppBar(
                title = "Documents",
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
                onClick = onNavigateToAddDocument,
                containerColor = VaultEmeraldPrimary,
                contentColor = MaterialTheme.colorScheme.background,
                shape = CircleShape
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Document")
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

            VaultSearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholderText = "Search encrypted files, IDs..."
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Document Category Chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedCategoryId == "cat_all",
                        onClick = { selectedCategoryId = "cat_all" },
                        label = { Text("All") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VaultEmeraldPrimary.copy(alpha = 0.15f),
                            selectedLabelColor = VaultEmeraldPrimary
                        )
                    )
                }
                items(DocumentCategory.entries.toTypedArray()) { cat ->
                    FilterChip(
                        selected = selectedCategoryId == cat.id,
                        onClick = { selectedCategoryId = cat.id },
                        label = { Text(cat.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VaultEmeraldPrimary.copy(alpha = 0.15f),
                            selectedLabelColor = VaultEmeraldPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredList.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.Description,
                    title = "No Documents Found",
                    description = "Securely import and encrypt documents in internal sandbox",
                    actionButtonText = "+ Add Document",
                    onActionClicked = onNavigateToAddDocument
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredList) { doc ->
                        DocumentListItem(
                            doc = doc,
                            onClick = { onNavigateToDocumentPreview(doc.id) }
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
private fun DocumentListItem(
    doc: DocumentItem,
    onClick: () -> Unit
) {
    VaultCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(VaultCategoryPurple.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = doc.fileExtension,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = VaultCategoryPurple
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doc.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${doc.fileName} • ${doc.fileSizeFormatted}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (doc.isFavorite) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = VaultCategoryOrange,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
