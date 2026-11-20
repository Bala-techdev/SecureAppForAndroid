package com.balatechdev.vaultzero.features.credentials

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.balatechdev.vaultzero.core.designsystem.components.PasswordStrengthMeter
import com.balatechdev.vaultzero.core.designsystem.components.VaultCard
import com.balatechdev.vaultzero.core.designsystem.components.VaultTopAppBar
import com.balatechdev.vaultzero.core.designsystem.theme.VaultCategoryOrange
import com.balatechdev.vaultzero.core.designsystem.theme.VaultEmeraldPrimary
import com.balatechdev.vaultzero.data.model.DefaultCategories
import com.balatechdev.vaultzero.data.model.CredentialRecord
import com.balatechdev.vaultzero.data.model.PasswordStrength
import com.balatechdev.vaultzero.data.repository.CredentialRepository
import com.balatechdev.vaultzero.security.crypto.PasswordGenerator
import com.balatechdev.vaultzero.security.crypto.PasswordGeneratorRules
import java.security.SecureRandom

@Composable
fun AddCredentialScreen(
    repository: CredentialRepository? = null,
    passwordGenerator: PasswordGenerator? = null,
    onNavigateBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    var title by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var websiteUrl by remember { mutableStateOf("") }
    var totpSecret by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf("cat_logins") }
    var isFavorite by remember { mutableStateOf(false) }

    // Generator state
    var showGenerator by remember { mutableStateOf(false) }
    var generatedLength by remember { mutableFloatStateOf(20f) }
    var useUppercase by remember { mutableStateOf(true) }
    var useNumbers by remember { mutableStateOf(true) }
    var useSymbols by remember { mutableStateOf(true) }

    fun generateSecurePassword(length: Int, upper: Boolean, numbers: Boolean, symbols: Boolean): String {
        val lowerChars = "abcdefghijklmnopqrstuvwxyz"
        val upperChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val numberChars = "0123456789"
        val symbolChars = "!@#$%^&*()-_=+[]{}|;:,.<>?"

        var pool = lowerChars
        if (upper) pool += upperChars
        if (numbers) pool += numberChars
        if (symbols) pool += symbolChars

        val random = SecureRandom()
        val builder = StringBuilder()
        for (i in 0 until length) {
            val index = random.nextInt(pool.length)
            builder.append(pool[index])
        }
        return builder.toString()
    }

    val strength = when {
        password.length >= 16 -> PasswordStrength.STRONG
        password.length >= 12 -> PasswordStrength.GOOD
        password.length >= 8 -> PasswordStrength.FAIR
        else -> PasswordStrength.WEAK
    }

    val scrollState = rememberScrollState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            VaultTopAppBar(
                title = "New Credential",
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(
                        onClick = {
                            if (title.isNotBlank()) onSaveSuccess()
                        }
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

            // Category Chips
            Text(
                text = "CATEGORY",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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

            // Password Field with Generator Action
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            showGenerator = !showGenerator
                        }) {
                            Icon(
                                imageVector = Icons.Default.Autorenew,
                                contentDescription = "Generate Password",
                                tint = VaultEmeraldPrimary
                            )
                        }
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    }
                }
            )

            if (password.isNotEmpty()) {
                PasswordStrengthMeter(strength = strength)
            }

            // In-line Password Generator Section
            if (showGenerator) {
                VaultCard {
                    Text(
                        text = "PASSWORD GENERATOR (CSPRNG)",
                        style = MaterialTheme.typography.labelSmall,
                        color = VaultEmeraldPrimary,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Length: ${generatedLength.toInt()} characters",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Slider(
                        value = generatedLength,
                        onValueChange = { generatedLength = it },
                        valueRange = 8f..64f,
                        steps = 55,
                        colors = SliderDefaults.colors(
                            thumbColor = VaultEmeraldPrimary,
                            activeTrackColor = VaultEmeraldPrimary
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Uppercase (A-Z)")
                        Switch(
                            checked = useUppercase,
                            onCheckedChange = { useUppercase = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = VaultEmeraldPrimary)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Numbers (0-9)")
                        Switch(
                            checked = useNumbers,
                            onCheckedChange = { useNumbers = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = VaultEmeraldPrimary)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Symbols (!@#$)")
                        Switch(
                            checked = useSymbols,
                            onCheckedChange = { useSymbols = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = VaultEmeraldPrimary)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            password = generateSecurePassword(
                                length = generatedLength.toInt(),
                                upper = useUppercase,
                                numbers = useNumbers,
                                symbols = useSymbols
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = VaultEmeraldPrimary)
                    ) {
                        Text("Generate & Apply")
                    }
                }
            }

            // Website URL
            OutlinedTextField(
                value = websiteUrl,
                onValueChange = { websiteUrl = it },
                label = { Text("Website URL") },
                singleLine = true,
                placeholder = { Text("https://example.com") },
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
                    if (title.isNotBlank()) {
                        if (repository != null) {
                            val parsedSecret = if (totpSecret.startsWith("otpauth://")) {
                                com.balatechdev.vaultzero.security.totp.TotpConfig.parseUri(totpSecret.trim())?.secret ?: totpSecret.trim()
                            } else if (totpSecret.isNotBlank()) {
                                totpSecret.trim()
                            } else {
                                null
                            }
                            val record = CredentialRecord(
                                title = title.trim(),
                                username = username.trim(),
                                passwordChars = password.toCharArray(),
                                website = websiteUrl.trim(),
                                notes = notes.trim(),
                                totpSecret = parsedSecret,
                                categoryId = selectedCategoryId,
                                isFavorite = isFavorite,
                                passwordStrength = strength
                            )
                            repository.addCredential(record)
                        }
                        onSaveSuccess()
                    }
                },
                enabled = title.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VaultEmeraldPrimary)
            ) {
                Text(
                    text = "SAVE CREDENTIAL",
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
