package com.balatechdev.vaultzero.features.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.balatechdev.vaultzero.core.designsystem.components.VaultCard
import com.balatechdev.vaultzero.core.designsystem.components.VaultTopAppBar
import com.balatechdev.vaultzero.core.designsystem.theme.VaultEmeraldPrimary
import com.balatechdev.vaultzero.core.designsystem.theme.VaultSecurityAlert
import com.balatechdev.vaultzero.core.designsystem.theme.VaultSecurityInfo
import com.balatechdev.vaultzero.data.backup.CorruptedBackupException
import com.balatechdev.vaultzero.data.backup.IncompatibleBackupVersionException
import com.balatechdev.vaultzero.data.backup.InvalidBackupHeaderException
import com.balatechdev.vaultzero.data.backup.VaultBackupService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BackupRestoreScreen(
    backupService: VaultBackupService? = null,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()

    var showExportDialog by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf("") }

    var showRestoreDialog by remember { mutableStateOf(false) }
    var restorePassword by remember { mutableStateOf("") }
    var selectedRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var restoreStatusMessage by remember { mutableStateOf<String?>(null) }
    var restoreErrorMessage by remember { mutableStateOf<String?>(null) }

    // SAF CreateDocument Launcher (Export)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null && exportPassword.isNotBlank()) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { outStream ->
                            val passChars = exportPassword.toCharArray()
                            backupService?.exportBackupToStream(outStream, passChars)
                            passChars.fill('0')
                        }
                    }
                    exportPassword = ""
                    snackbarHostState.showSnackbar("Encrypted backup archive exported successfully via SAF!")
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Export failed: ${e.message}")
                }
            }
        }
    }

    // SAF OpenDocument Launcher (Restore)
    val restorePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedRestoreUri = uri
            showRestoreDialog = true
        }
    }

    fun executeRestore() {
        val uri = selectedRestoreUri ?: return
        val pass = restorePassword
        if (pass.isBlank()) return

        scope.launch {
            try {
                restoreErrorMessage = null
                val manifest = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { inStream ->
                        val passChars = pass.toCharArray()
                        val m = backupService?.restoreBackupFromStream(inStream, passChars)
                        passChars.fill('0')
                        m
                    }
                }
                showRestoreDialog = false
                restorePassword = ""
                restoreStatusMessage = "Restored successfully: ${manifest?.credentialCount ?: 0} credentials, ${manifest?.documentCount ?: 0} documents."
                snackbarHostState.showSnackbar(restoreStatusMessage ?: "Restore completed!")
            } catch (e: InvalidBackupHeaderException) {
                restoreErrorMessage = "File is not a valid VaultZero backup archive (.vzf)."
            } catch (e: IncompatibleBackupVersionException) {
                restoreErrorMessage = "Incompatible backup format version (${e.fileVersion}). Please update the app."
            } catch (e: CorruptedBackupException) {
                restoreErrorMessage = "Authentication failed: Incorrect passphrase or corrupted archive."
            } catch (e: Exception) {
                restoreErrorMessage = "Restore failed: ${e.message}"
            }
        }
    }

    // Export Passphrase Dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Set Backup Encryption Password") },
            text = {
                Column {
                    Text(
                        text = "Your export container will be encrypted with Argon2id and AES-256-GCM using this passphrase. You will need this passphrase to restore your vault.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = exportPassword,
                        onValueChange = { exportPassword = it },
                        label = { Text("Backup Passphrase *") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (exportPassword.isNotBlank()) {
                            showExportDialog = false
                            val defaultFileName = "vaultzero_backup_${System.currentTimeMillis()}.vzf"
                            exportLauncher.launch(defaultFileName)
                        }
                    },
                    enabled = exportPassword.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = VaultEmeraldPrimary)
                ) {
                    Text("Select Destination via SAF")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Restore Passphrase Dialog
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("Enter Backup Passphrase") },
            text = {
                Column {
                    Text(
                        text = "Enter the encryption passphrase used when creating this .vzf backup archive.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = restorePassword,
                        onValueChange = { restorePassword = it },
                        label = { Text("Backup Passphrase *") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (restoreErrorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = restoreErrorMessage ?: "",
                            color = VaultSecurityAlert,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { executeRestore() },
                    enabled = restorePassword.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = VaultEmeraldPrimary)
                ) {
                    Text("Restore Archive")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            VaultTopAppBar(
                title = "Backup & Restore",
                onNavigateBack = onNavigateBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Explanation Card
            VaultCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(VaultSecurityInfo.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = VaultSecurityInfo,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "Air-Gapped Encrypted Archives",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Backups are encrypted offline and never touch the cloud.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (restoreStatusMessage != null) {
                VaultCard {
                    Text(
                        text = restoreStatusMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VaultEmeraldPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Export Section
            VaultCard {
                Text(
                    text = "EXPORT ENCRYPTED VAULT (.vzf)",
                    style = MaterialTheme.typography.labelSmall,
                    color = VaultEmeraldPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Packages your SQLCipher database, encrypted documents, and key-wrapping metadata into a single authenticated file container (.vzf). Handled via Android Storage Access Framework (SAF).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = { showExportDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VaultEmeraldPrimary)
                ) {
                    Icon(imageVector = Icons.Default.FileUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("CREATE ENCRYPTED BACKUP VIA SAF", fontWeight = FontWeight.Bold)
                }
            }

            // Restore Section
            VaultCard {
                Text(
                    text = "RESTORE VAULT ARCHIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = VaultSecurityInfo,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Select a previously exported .vzf file from your device, USB drive, or SD card to restore your credentials and documents. Enforces all-or-nothing atomic replacement.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedButton(
                    onClick = {
                        restorePickerLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.FileDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SELECT .VZF FILE TO RESTORE VIA SAF", fontWeight = FontWeight.SemiBold)
                }
            }

            // Migration & Same-Device Documentation Card
            VaultCard {
                Text(
                    text = "DEVICE MIGRATION & RESTORE SPECIFICATION",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• Same-Device Restore:\n" +
                            "  Replaces current database and document folders atomically, retaining existing device key envelopes.\n\n" +
                            "• New-Device Migration:\n" +
                            "  Transfer the .vzf file to your new device. Restore with your passphrase. The Master Key is restored, and the new device automatically initializes its own TEE Keystore key and biometrics locally.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Format: VaultZero Encrypted Archive (Magic: VZBAK)\nKDF: Argon2id (m=64MB, t=3, p=4)\nCipher: AES-256-GCM Dual-Layer Authenticated",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
