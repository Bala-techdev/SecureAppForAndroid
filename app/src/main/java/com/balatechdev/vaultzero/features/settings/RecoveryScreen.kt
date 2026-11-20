package com.balatechdev.vaultzero.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.balatechdev.vaultzero.core.designsystem.components.PasswordStrengthMeter
import com.balatechdev.vaultzero.core.designsystem.components.VaultCard
import com.balatechdev.vaultzero.core.designsystem.components.VaultTopAppBar
import com.balatechdev.vaultzero.core.designsystem.theme.VaultEmeraldPrimary
import com.balatechdev.vaultzero.core.designsystem.theme.VaultSecurityAlert
import com.balatechdev.vaultzero.core.designsystem.theme.VaultSecuritySafe
import com.balatechdev.vaultzero.data.model.PasswordStrength
import com.balatechdev.vaultzero.security.auth.AuthResult
import com.balatechdev.vaultzero.security.recovery.Bip39Mnemonic
import com.balatechdev.vaultzero.security.recovery.InvalidRecoveryChecksumException
import com.balatechdev.vaultzero.security.recovery.UnknownRecoveryWordException
import com.balatechdev.vaultzero.security.recovery.VaultRecoveryService
import kotlinx.coroutines.launch

@Composable
fun RecoveryScreen(
    recoveryService: VaultRecoveryService? = null,
    onNavigateBack: () -> Unit,
    onRecoverySuccess: () -> Unit = onNavigateBack,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Recover Vault, 1: View/Backup Phrase
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Generated BIP-39 phrase for View/Backup mode (never hardcoded!)
    val generatedPhrase = remember {
        recoveryService?.generateRecoveryPhrase() ?: Bip39Mnemonic.entropyToMnemonic(Bip39Mnemonic.generateEntropy256())
    }

    // Confirmation test state (Words #4, #12, #18)
    var confirmWord4 by remember { mutableStateOf("") }
    var confirmWord12 by remember { mutableStateOf("") }
    var confirmWord18 by remember { mutableStateOf("") }
    var isConfirmed by remember { mutableStateOf(false) }

    // Recovery mode input state
    var recoveryPhraseInput by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var recoveryErrorMessage by remember { mutableStateOf<String?>(null) }
    var recoverySuccessMessage by remember { mutableStateOf<String?>(null) }

    val strength = when {
        newPassword.length >= 16 -> PasswordStrength.STRONG
        newPassword.length >= 12 -> PasswordStrength.GOOD
        newPassword.length >= 8 -> PasswordStrength.FAIR
        else -> PasswordStrength.WEAK
    }

    fun handleRecoveryAttempt() {
        recoveryErrorMessage = null
        val words = recoveryPhraseInput.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (words.size != 24) {
            recoveryErrorMessage = "Please enter all 24 words of your BIP-39 recovery phrase (got ${words.size})."
            return
        }

        try {
            // Validate phrase checksum and dictionary words
            Bip39Mnemonic.mnemonicToEntropy(words)
        } catch (e: UnknownRecoveryWordException) {
            recoveryErrorMessage = e.message
            return
        } catch (e: InvalidRecoveryChecksumException) {
            recoveryErrorMessage = "Invalid recovery phrase checksum. Please check for spelling mistakes or transpositions."
            return
        } catch (e: Exception) {
            recoveryErrorMessage = "Invalid recovery phrase: ${e.message}"
            return
        }

        if (newPassword.isBlank() || newPassword != confirmPassword) {
            recoveryErrorMessage = "New master passwords do not match or are blank."
            return
        }

        if (newPassword.length < 12) {
            recoveryErrorMessage = "New master password must be at least 12 characters long."
            return
        }

        if (recoveryService != null) {
            val passChars = newPassword.toCharArray()
            val confChars = confirmPassword.toCharArray()
            val result = recoveryService.resetMasterPasswordWithRecovery(
                mnemonicWords = words,
                newPassword = passChars,
                newConfirmation = confChars
            )
            passChars.fill('0')
            confChars.fill('0')

            when (result) {
                is AuthResult.Success -> {
                    recoverySuccessMessage = "Master Password successfully reset! Existing database and documents preserved."
                    scope.launch {
                        snackbarHostState.showSnackbar("Vault recovered! Redirecting to Dashboard...")
                        onRecoverySuccess()
                    }
                }
                is AuthResult.Failure -> {
                    recoveryErrorMessage = "Recovery failed: ${result.error}"
                }
            }
        } else {
            onRecoverySuccess()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            VaultTopAppBar(
                title = "Disaster Recovery",
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
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("RECOVER VAULT") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("VIEW / BACKUP") }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (selectedTab == 0) {
                    // TAB 0: RECOVER VAULT & RESET MASTER PASSWORD
                    VaultCard {
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(VaultEmeraldPrimary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LockReset,
                                    contentDescription = null,
                                    tint = VaultEmeraldPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "RESET MASTER PASSWORD",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = VaultEmeraldPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Enter your 24-word BIP-39 recovery phrase to restore access and set a new Master Password. Your encrypted credentials and documents will remain safe and intact.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = recoveryPhraseInput,
                        onValueChange = { recoveryPhraseInput = it },
                        label = { Text("24-Word Recovery Phrase") },
                        placeholder = { Text("word1 word2 word3 ... word24") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New Master Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        }
                    )

                    if (newPassword.isNotEmpty()) {
                        PasswordStrengthMeter(strength = strength)
                    }

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm New Master Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )

                    if (recoveryErrorMessage != null) {
                        Text(
                            text = recoveryErrorMessage ?: "",
                            color = VaultSecurityAlert,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (recoverySuccessMessage != null) {
                        Text(
                            text = recoverySuccessMessage ?: "",
                            color = VaultSecuritySafe,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = { handleRecoveryAttempt() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VaultEmeraldPrimary)
                    ) {
                        Text("RECOVER & RESET MASTER PASSWORD", fontWeight = FontWeight.Bold)
                    }
                } else {
                    // TAB 1: VIEW & BACKUP BIP-39 RECOVERY PHRASE
                    VaultCard {
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(VaultSecurityAlert.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = VaultSecurityAlert,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "OFFLINE 256-BIT BIP-39 KEY",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = VaultSecurityAlert
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Generated from 256 bits of CSPRNG entropy with SHA-256 checksum. Write these 24 words down in exact numerical order on physical paper. Never store them online.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 24 Words Grid
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (row in 0 until 12) {
                            val idx1 = row * 2
                            val idx2 = row * 2 + 1
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                WordTile(index = idx1 + 1, word = generatedPhrase[idx1], modifier = Modifier.weight(1f))
                                WordTile(index = idx2 + 1, word = generatedPhrase[idx2], modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    // Required Word Confirmation
                    Text(
                        text = "REQUIRE CONFIRMATION",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    VaultCard {
                        Text(
                            text = "To guarantee you saved the phrase, verify words #4, #12, and #18:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = confirmWord4,
                                onValueChange = { confirmWord4 = it },
                                label = { Text("#4") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = confirmWord12,
                                onValueChange = { confirmWord12 = it },
                                label = { Text("#12") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = confirmWord18,
                                onValueChange = { confirmWord18 = it },
                                label = { Text("#18") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                val match4 = confirmWord4.trim().equals(generatedPhrase[3], ignoreCase = true)
                                val match12 = confirmWord12.trim().equals(generatedPhrase[11], ignoreCase = true)
                                val match18 = confirmWord18.trim().equals(generatedPhrase[17], ignoreCase = true)
                                if (match4 && match12 && match18) {
                                    isConfirmed = true
                                    scope.launch { snackbarHostState.showSnackbar("Recovery phrase verified and confirmed!") }
                                } else {
                                    isConfirmed = false
                                    scope.launch { snackbarHostState.showSnackbar("Words do not match. Please verify your written phrase.") }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = VaultEmeraldPrimary)
                        ) {
                            Text("Verify & Confirm Phrase")
                        }

                        if (isConfirmed) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = VaultSecuritySafe)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Confirmation Verified", color = VaultSecuritySafe, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Button(
                        onClick = onNavigateBack,
                        enabled = isConfirmed,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VaultEmeraldPrimary)
                    ) {
                        Text("COMPLETE RECOVERY SETUP", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun WordTile(
    index: Int,
    word: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$index.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(26.dp)
        )
        Text(
            text = word,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
