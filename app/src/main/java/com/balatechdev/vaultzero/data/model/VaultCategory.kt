package com.balatechdev.vaultzero.data.model

import androidx.compose.ui.graphics.Color
import com.balatechdev.vaultzero.core.designsystem.theme.VaultCategoryIndigo
import com.balatechdev.vaultzero.core.designsystem.theme.VaultCategoryOrange
import com.balatechdev.vaultzero.core.designsystem.theme.VaultCategoryPurple
import com.balatechdev.vaultzero.core.designsystem.theme.VaultEmeraldPrimary
import com.balatechdev.vaultzero.core.designsystem.theme.VaultSecurityInfo

data class VaultCategory(
    val id: String,
    val name: String,
    val iconName: String,
    val color: Color,
    val itemCount: Int = 0
)

val DefaultCategories = listOf(
    VaultCategory("cat_all", "All Items", "Folder", VaultEmeraldPrimary, 32),
    VaultCategory("cat_logins", "Logins", "Password", VaultSecurityInfo, 18),
    VaultCategory("cat_financial", "Financial", "CreditCard", VaultCategoryOrange, 6),
    VaultCategory("cat_identity", "Identities", "Badge", VaultCategoryIndigo, 4),
    VaultCategory("cat_notes", "Secure Notes", "Description", VaultCategoryPurple, 4)
)
