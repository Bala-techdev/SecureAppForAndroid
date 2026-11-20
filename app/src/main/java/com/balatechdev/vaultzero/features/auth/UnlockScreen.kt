package com.balatechdev.vaultzero.features.auth

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.balatechdev.vaultzero.core.designsystem.theme.VaultEmeraldPrimary
import com.balatechdev.vaultzero.core.designsystem.theme.VaultSecurityAlert

import com.balatechdev.vaultzero.security.auth.AuthResult
import com.balatechdev.vaultzero.security.auth.VaultAuthService
import com.balatechdev.vaultzero.security.biometric.BiometricAuthResult
import com.balatechdev.vaultzero.security.biometric.BiometricAuthService
import com.balatechdev.vaultzero.security.biometric.BiometricAvailability

@Composable
fun UnlockScreen(
    authService: VaultAuthService? = null,
    biometricAuthService: BiometricAuthService? = null,
    onUnlockSuccessful: () -> Unit,
    onEmergencyRecoveryClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    var passwordInput by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun handlePasswordUnlock() {
        if (passwordInput.isBlank()) {
            errorMessage = "Master Password required"
            return
        }

        if (authService != null) {
            val passChars = passwordInput.toCharArray()
            val result = authService.unlockVault(passChars)
            passChars.fill('0')
            when (result) {
                is AuthResult.Success -> {
                    errorMessage = null
                    onUnlockSuccessful()
                }
                is AuthResult.Failure -> {
                    errorMessage = "Incorrect Master Password. Please check your credentials."
                }
            }
        } else {
            onUnlockSuccessful()
        }
    }

    fun handleBiometricUnlock() {
        if (biometricAuthService == null) {
            onUnlockSuccessful()
            return
        }

        val availability = biometricAuthService.canAuthenticate()
        when (availability) {
            is BiometricAvailability.NotEnrolled -> {
                errorMessage = "No biometrics enrolled on this device. Please unlock with your Master Password."
                return
            }
            is BiometricAvailability.HardwareUnavailable, is BiometricAvailability.NoHardware -> {
                errorMessage = "Biometric hardware is unavailable. Please use Master Password."
                return
            }
            else -> { /* Proceed */ }
        }

        biometricAuthService.authenticate { result ->
            when (result) {
                is BiometricAuthResult.Success -> {
                    errorMessage = null
                    onUnlockSuccessful()
                }
                is BiometricAuthResult.KeyInvalidated -> {
                    errorMessage = "Biometrics changed on device! Keystore key was invalidated for your protection. Unlock with Master Password to re-enable."
                }
                is BiometricAuthResult.Failed -> {
                    errorMessage = "Biometric not recognized. Please retry or enter your Master Password."
                }
                is BiometricAuthResult.Cancelled -> {
                    errorMessage = "Biometric authentication cancelled."
                }
                is BiometricAuthResult.UserFallback -> {
                    errorMessage = null
                }
                is BiometricAuthResult.NotEnrolled -> {
                    errorMessage = "Biometrics not configured in VaultZero. Please unlock with Master Password."
                }
                is BiometricAuthResult.Unavailable -> {
                    errorMessage = "Biometric sensor unavailable. Please use Master Password."
                }
                is BiometricAuthResult.Error -> {
                    errorMessage = result.message
                }
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Logo
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 40.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(VaultEmeraldPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Vault Locked",
                        tint = VaultEmeraldPrimary,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "VAULTZERO",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 4.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = "OFFLINE ENCRYPTED VAULT",
                    style = MaterialTheme.typography.labelSmall,
                    color = VaultEmeraldPrimary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Input Form
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = {
                        passwordInput = it
                        errorMessage = null
                    },
                    label = { Text("Master Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VaultEmeraldPrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (passwordInput.isNotBlank()) onUnlockSuccessful()
                            else errorMessage = "Master Password required"
                        }
                    ),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = VaultSecurityAlert,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { handlePasswordUnlock() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VaultEmeraldPrimary,
                        contentColor = MaterialTheme.colorScheme.background
                    )
                ) {
                    Text(
                        text = "UNLOCK VAULT",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { handleBiometricUnlock() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        tint = VaultEmeraldPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unlock with Biometrics")
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(onClick = onEmergencyRecoveryClicked) {
                    Text(
                        text = "Forgot Master Password? Use Recovery Key",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Footer
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Hardware Keystore • Screen Protected (FLAG_SECURE)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
