package com.balatechdev.vaultzero.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.balatechdev.vaultzero.core.designsystem.theme.VaultSecurityAlert
import com.balatechdev.vaultzero.core.designsystem.theme.VaultSecuritySafe
import com.balatechdev.vaultzero.core.designsystem.theme.VaultSecurityWarning
import com.balatechdev.vaultzero.data.model.PasswordStrength

@Composable
fun PasswordStrengthMeter(
    strength: PasswordStrength,
    modifier: Modifier = Modifier
) {
    val targetColor = when (strength) {
        PasswordStrength.WEAK -> VaultSecurityAlert
        PasswordStrength.FAIR -> VaultSecurityWarning
        PasswordStrength.GOOD -> MaterialTheme.colorScheme.secondary
        PasswordStrength.STRONG -> VaultSecuritySafe
    }

    val animatedColor by animateColorAsState(targetValue = targetColor, label = "strengthColor")
    val animatedProgress by animateFloatAsState(targetValue = strength.scorePercent, label = "strengthProgress")

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Password Entropy",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = strength.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = animatedColor
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.outlineVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = animatedProgress)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(animatedColor)
            )
        }
    }
}
