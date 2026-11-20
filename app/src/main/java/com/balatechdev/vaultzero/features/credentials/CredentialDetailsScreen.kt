package com.balatechdev.vaultzero.features.credentials

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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.balatechdev.vaultzero.core.designsystem.components.MaskedPasswordRow
import com.balatechdev.vaultzero.core.designsystem.components.PasswordStrengthMeter
import com.balatechdev.vaultzero.core.designsystem.components.VaultCard
import com.balatechdev.vaultzero.core.designsystem.components.VaultTopAppBar
import com.balatechdev.vaultzero.core.designsystem.theme.VaultCategoryOrange
import com.balatechdev.vaultzero.core.designsystem.theme.VaultEmeraldPrimary
import com.balatechdev.vaultzero.data.model.SampleData
import kotlinx.coroutines.launch

import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.balatechdev.vaultzero.data.model.toCredentialItem
import com.balatechdev.vaultzero.data.repository.CredentialRepository
import com.balatechdev.vaultzero.security.clipboard.SecureClipboard
import com.balatechdev.vaultzero.security.totp.TotpConfig
import com.balatechdev.vaultzero.security.totp.TotpGenerator
import com.balatechdev.vaultzero.security.totp.TotpService
import kotlinx.coroutines.delay

@Composable
fun CredentialDetailsScreen(
    credentialId: String,
    repository: CredentialRepository? = null,
    clipboard: SecureClipboard? = null,
    totpService: TotpService? = null,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val credential = repository?.getCredentialById(credentialId)?.toCredentialItem()
        ?: SampleData.sampleCredentials.find { it.id == credentialId }
        ?: SampleData.sampleCredentials.first()

    val rawTotpSecret = repository?.getCredentialById(credentialId)?.totpSecret
        ?: credential.totpSecret
        ?: ""

    var currentOtpCode by remember { mutableStateOf("------") }
    var remainingSeconds by remember { mutableIntStateOf(30) }
    var countdownProgress by remember { mutableFloatStateOf(1.0f) }

    LaunchedEffect(rawTotpSecret) {
        if (rawTotpSecret.isNotBlank()) {
            val config = TotpConfig(
                issuer = credential.title,
                accountName = credential.username,
                secret = rawTotpSecret
            )
            while (true) {
                val nowSec = System.currentTimeMillis() / 1000L
                val totp = TotpGenerator.generateTotp(config, nowSec)
                currentOtpCode = totp.formattedCode
                remainingSeconds = totp.remainingSeconds
                countdownProgress = totp.progress
                delay(1000L)
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            VaultTopAppBar(
                title = credential.title,
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(onClick = { onNavigateToEdit(credential.id) }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
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
            // Header Hero
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(VaultEmeraldPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = credential.title.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = VaultEmeraldPrimary
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = credential.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Encrypted with AES-256-GCM",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VaultEmeraldPrimary
                    )
                }

                Icon(
                    imageVector = if (credential.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = null,
                    tint = if (credential.isFavorite) VaultCategoryOrange else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Username Card
            VaultCard {
                Text(
                    text = "USERNAME / EMAIL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = credential.username,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = {
                            clipboard?.copySensitive(label = "Username", content = credential.username)
                            scope.launch {
                                snackbarHostState.showSnackbar("Username copied to secure clipboard (auto-clearing in 30s)")
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Username",
                            tint = VaultEmeraldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Password Card
            VaultCard {
                Text(
                    text = "PASSWORD",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                MaskedPasswordRow(
                    passwordPlain = credential.passwordPlain,
                    onCopyClicked = {
                        clipboard?.copySensitive(label = "Password", content = credential.passwordPlain)
                        scope.launch {
                            snackbarHostState.showSnackbar("Password copied to secure clipboard (auto-clearing in 30s)")
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                PasswordStrengthMeter(strength = credential.passwordStrength)
            }

            // TOTP Authenticator Card
            if (rawTotpSecret.isNotBlank()) {
                VaultCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TIME-BASED AUTH CODE (TOTP)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${remainingSeconds}s",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (remainingSeconds <= 5) MaterialTheme.colorScheme.error else VaultEmeraldPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = currentOtpCode,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 3.sp
                            ),
                            color = VaultEmeraldPrimary
                        )
                        IconButton(
                            onClick = {
                                val cleanCode = currentOtpCode.replace(" ", "")
                                clipboard?.copySensitive(label = "OTP Code", content = cleanCode)
                                scope.launch {
                                    snackbarHostState.showSnackbar("OTP code copied (clears in 30s)")
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy OTP Code",
                                tint = VaultEmeraldPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { countdownProgress },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = if (remainingSeconds <= 5) MaterialTheme.colorScheme.error else VaultEmeraldPrimary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            // Website URL Card
            if (credential.websiteUrl.isNotEmpty()) {
                VaultCard {
                    Text(
                        text = "WEBSITE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = credential.websiteUrl,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = {}) {
                            Icon(
                                imageVector = Icons.Default.OpenInBrowser,
                                contentDescription = "Open Website",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Secure Notes Card
            if (credential.notes.isNotEmpty()) {
                VaultCard {
                    Text(
                        text = "SECURE NOTES",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = credential.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Metadata
            VaultCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Last Modified",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = credential.lastModified,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { onNavigateToEdit(credential.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VaultEmeraldPrimary)
            ) {
                Icon(imageVector = Icons.Default.Edit, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("EDIT CREDENTIAL", fontWeight = FontWeight.Bold)
            }
        }
    }
}
