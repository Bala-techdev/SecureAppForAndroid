package com.balatechdev.vaultzero.features.documents

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.balatechdev.vaultzero.core.designsystem.components.VaultCard
import com.balatechdev.vaultzero.core.designsystem.components.VaultTopAppBar
import com.balatechdev.vaultzero.core.designsystem.theme.VaultCategoryOrange
import com.balatechdev.vaultzero.core.designsystem.theme.VaultCategoryPurple
import com.balatechdev.vaultzero.core.designsystem.theme.VaultEmeraldPrimary
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.balatechdev.vaultzero.data.model.DocumentCategory
import com.balatechdev.vaultzero.data.repository.DocumentRepository
import com.balatechdev.vaultzero.security.crypto.DocumentCryptoEngine
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable

@Composable
fun AddDocumentScreen(
    repository: DocumentRepository? = null,
    onNavigateBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

   var selectedUriString by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedUri = selectedUriString?.let { Uri.parse(it) }

    var title by rememberSaveable { mutableStateOf("") }
    var selectedFileName by rememberSaveable { mutableStateOf("Select a document") }
    var selectedFileSize by rememberSaveable { mutableStateOf("No file selected") }
    var selectedCategoryId by rememberSaveable {
        mutableStateOf(DocumentCategory.BANKING.id)
    }
    var notes by rememberSaveable { mutableStateOf("") }
    var isFavorite by rememberSaveable { mutableStateOf(false) }

    // SAF Document Picker Launcher
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUriString = uri.toString()
            var displayName = "selected_document"
            var fileSize = 0L

            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIdx != -1) displayName = cursor.getString(nameIdx) ?: displayName
                        if (sizeIdx != -1) fileSize = cursor.getLong(sizeIdx)
                    }
                }
            } catch (e: Exception) {
                displayName = uri.lastPathSegment ?: "document"
            }

            val ext = displayName.substringAfterLast('.', "")
            if (!DocumentCryptoEngine.isSupportedExtension(ext)) {
                scope.launch {
                    snackbarHostState.showSnackbar("Unsupported file type .$ext. Only PDF, JPG, JPEG, and PNG are supported.")
                }
            } else {
                selectedFileName = displayName
                selectedFileSize = if (fileSize > 0) com.balatechdev.vaultzero.data.model.DocumentEntity.formatFileSize(fileSize) else "Unknown Size"
                if (title.isBlank()) {
                    title = displayName.substringBeforeLast('.')
                }
            }
        }
    }

    val scrollState = rememberScrollState()

    fun handleSave() {
        if (title.isBlank()) return
        try {
            if (repository != null) {
                if (selectedUri != null) {
                    repository.importFromUri(
                        context = context,
                        uri = selectedUri!!,
                        title = title.trim(),
                        categoryId = selectedCategoryId,
                        notes = notes.trim()
                    )
                } else {
                    val sampleContent = "Secure encrypted payload for $title\nOriginal: $selectedFileName".toByteArray(Charsets.UTF_8)
                    repository.importDocument(
                        sourceStream = sampleContent.inputStream(),
                        title = title.trim(),
                        fileName = selectedFileName,
                        categoryId = selectedCategoryId,
                        notes = notes.trim()
                    )
                }
            }
            onSaveSuccess()
        } catch (e: Exception) {
            scope.launch {
                snackbarHostState.showSnackbar("Failed to encrypt document: ${e.message}")
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            VaultTopAppBar(
                title = "Encrypt Document",
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(
                        onClick = { handleSave() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Save",
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            // File Ingest Picker Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    .clickable {
                        documentPickerLauncher.launch(
                            arrayOf("application/pdf", "image/jpeg", "image/png")
                        )
                    }
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(VaultCategoryPurple.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = null,
                            tint = VaultCategoryPurple,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = selectedFileName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "File Size: $selectedFileSize • Tap to import via SAF (PDF, JPG, PNG)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Title & Favorite
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Document Title *") },
                    placeholder = { Text("e.g. Tax Returns 2025") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(onClick = { isFavorite = !isFavorite }) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) VaultCategoryOrange else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Category Chips
            Text(
                text = "CATEGORY",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DocumentCategory.entries.forEach { cat ->
                    val isSelected = selectedCategoryId == cat.id
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategoryId = cat.id },
                        label = { Text(cat.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VaultEmeraldPrimary.copy(alpha = 0.15f),
                            selectedLabelColor = VaultEmeraldPrimary
                        )
                    )
                }
            }

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Secure Notes / Metadata") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Encryption Info Card
            VaultCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = VaultEmeraldPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Zero Data Leakage Assurance",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "This file will be chunk-encrypted using Google Tink Streaming AEAD (AES-256-GCM HKDF) directly into internal no-backup storage.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

          Button(
                onClick = {
                    handleSave()
                },
                enabled = title.isNotBlank() && selectedUri != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VaultEmeraldPrimary)
            ) {
                Text("ENCRYPT & STORE IN VAULT", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
