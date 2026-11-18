package com.balatechdev.vaultzero.features.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.balatechdev.vaultzero.core.designsystem.components.VaultBottomNavigationBar
import com.balatechdev.vaultzero.core.designsystem.components.VaultCard
import com.balatechdev.vaultzero.core.designsystem.components.VaultTopAppBar
import com.balatechdev.vaultzero.core.designsystem.theme.VaultCategoryIndigo
import com.balatechdev.vaultzero.core.designsystem.theme.VaultCategoryOrange
import com.balatechdev.vaultzero.core.designsystem.theme.VaultCategoryPurple
import com.balatechdev.vaultzero.core.designsystem.theme.VaultEmeraldPrimary
import com.balatechdev.vaultzero.core.designsystem.theme.VaultSecurityInfo
import com.balatechdev.vaultzero.core.designsystem.theme.VaultSecurityWarning
import com.balatechdev.vaultzero.navigation.Screen

@Composable
fun SettingsScreen(
    currentRoute: String,
    onNavigateToRoute: (String) -> Unit,
    onLockVault: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            VaultTopAppBar(title = "Settings")
        },
        bottomBar = {
            VaultBottomNavigationBar(
                currentRoute = currentRoute,
                onNavigateToRoute = onNavigateToRoute
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                VaultProfileCard()
            }

            item {
                Text(
                    text = "CONFIGURATION",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                SettingsNavigationRow(
                    icon = Icons.Default.Security,
                    iconTint = VaultEmeraldPrimary,
                    title = "Security & Protection",
                    subtitle = "Biometrics, Auto-Lock, FLAG_SECURE",
                    onClick = { onNavigateToRoute(Screen.SecuritySettings.route) }
                )
            }

            item {
                SettingsNavigationRow(
                    icon = Icons.Default.Backup,
                    iconTint = VaultSecurityInfo,
                    title = "Backup & Restore",
                    subtitle = "Export / Import encrypted .vzf archive",
                    onClick = { onNavigateToRoute(Screen.BackupRestore.route) }
                )
            }

            item {
                SettingsNavigationRow(
                    icon = Icons.Default.VpnKey,
                    iconTint = VaultCategoryOrange,
                    title = "Emergency Recovery Key",
                    subtitle = "View or verify BIP-39 recovery phrase",
                    onClick = { onNavigateToRoute(Screen.Recovery.route) }
                )
            }

            item {
                SettingsNavigationRow(
                    icon = Icons.Default.Category,
                    iconTint = VaultCategoryPurple,
                    title = "Categories",
                    subtitle = "Organize folders and tags",
                    onClick = { onNavigateToRoute(Screen.Categories.route) }
                )
            }

            item {
                SettingsNavigationRow(
                    icon = Icons.Default.Info,
                    iconTint = VaultCategoryIndigo,
                    title = "About VaultZero",
                    subtitle = "Version 1.0.0 • Air-gapped Architecture",
                    onClick = {}
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onLockVault,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VaultSecurityWarning)
                ) {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("LOCK VAULT IMMEDIATELY", fontWeight = FontWeight.Bold, color = Color.Black)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun VaultProfileCard() {
    VaultCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(VaultEmeraldPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = VaultEmeraldPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = "Local Master Vault",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Status: Encrypted & Active • StrongBox Hardware",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VaultEmeraldPrimary
                )
            }
        }
    }
}

@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    VaultCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
