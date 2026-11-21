package com.balatechdev.vaultzero.features.credentials

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.balatechdev.vaultzero.core.designsystem.components.VaultTopAppBar
import com.balatechdev.vaultzero.core.designsystem.theme.VaultCategoryOrange
import com.balatechdev.vaultzero.core.designsystem.theme.VaultEmeraldPrimary
import androidx.compose.foundation.layout.Box
import com.balatechdev.vaultzero.core.designsystem.theme.VaultSecurityAlert
import com.balatechdev.vaultzero.data.model.CredentialRecord
import com.balatechdev.vaultzero.data.model.DefaultCategories
import com.balatechdev.vaultzero.data.model.toCredentialItem
import com.balatechdev.vaultzero.data.repository.CredentialRepository
import com.balatechdev.vaultzero.security.crypto.PasswordGenerator

@Composable
fun EditCredentialScreen(
    credentialId: String,
    repository: CredentialRepository? = null,
    passwordGenerator: PasswordGenerator? = null,
    onNavigateBack: () -> Unit,
    onUpdateSuccess: () -> Unit,
    onDeleteSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val existing = repository?.getCredentialById(credentialId)?.toCredentialItem()

    if (existing == null) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                VaultTopAppBar(
                    title = "Edit Credential",
                    onNavigateBack = onNavigateBack
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Credential not found or has been deleted.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    var title by remember { mutableStateOf(existing.title) }
    var username by remember { mutableStateOf(existing.username) }
    var password by remember { mutableStateOf(existing.passwordPlain) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var websiteUrl by remember { mutableStateOf(existing.websiteUrl) }
    var totpSecret by remember { mutableStateOf(existing.totpSecret ?: "") }
    var notes by remember { mutableStateOf(existing.notes) }
    var selectedCategoryId by remember { mutableStateOf(existing.categoryId) }
    var isFavorite by remember { mutableStateOf(existing.isFavorite) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Credential") },
            text = { Text("Are you sure you want to permanently delete \"$title\"? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        repository?.deleteCredential(credentialId)
                        onDeleteSuccess()
                    }
                ) {
                    Text("Delete", color = VaultSecurityAlert, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            VaultTopAppBar(
                title = "Edit Credential",
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(onClick = onUpdateSuccess) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Save Changes",
                            tint = VaultEmeraldPrimary
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            // Title & Favorite
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Service / Title *") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = { isFavorite = !isFavorite },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) VaultCategoryOrange else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Categories
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DefaultCategories.filter { it.id != "cat_all" }.forEach { cat ->
                    val isSelected = selectedCategoryId == cat.id
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategoryId = cat.id },
                        label = { Text(cat.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VaultEmeraldPrimary.copy(alpha = 0.15f),
                            selectedLabelColor = VaultEmeraldPrimary
                        )
                    )
                }
            }

            // Username
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username or Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Password
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                }
            )

            // Website
            OutlinedTextField(
                value = websiteUrl,
                onValueChange = { websiteUrl = it },
                label = { Text("Website URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // TOTP Authenticator Key / URI
            OutlinedTextField(
                value = totpSecret,
                onValueChange = { totpSecret = it },
                label = { Text("TOTP Authenticator Key (Base32 or URI)") },
                singleLine = true,
                placeholder = { Text("e.g. JBSWY3DPEHPK3PXP") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Secure Notes") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (repository != null) {
                        val parsedSecret = if (totpSecret.startsWith("otpauth://")) {
                            com.balatechdev.vaultzero.security.totp.TotpConfig.parseUri(totpSecret.trim())?.secret ?: totpSecret.trim()
                        } else if (totpSecret.isNotBlank()) {
                            totpSecret.trim()
                        } else {
                            null
                        }
                        val record = CredentialRecord(
                            id = credentialId,
                            title = title.trim(),
                            username = username.trim(),
                            passwordChars = password.toCharArray(),
                            website = websiteUrl.trim(),
                            notes = notes.trim(),
                            totpSecret = parsedSecret,
                            categoryId = selectedCategoryId,
                            isFavorite = isFavorite
                        )
                        repository.updateCredential(record)
                    }
                    onUpdateSuccess()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VaultEmeraldPrimary)
            ) {
                Text("SAVE CHANGES", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultSecurityAlert)
            ) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("DELETE CREDENTIAL", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
