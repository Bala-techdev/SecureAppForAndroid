package com.balatechdev.vaultzero.data.model

data class CredentialItem(
    val id: String,
    val title: String,
    val username: String,
    val passwordMasked: String = "••••••••••••••••",
    val passwordPlain: String = "v0#SecurePass99!",
    val websiteUrl: String = "",
    val categoryId: String = "cat_logins",
    val notes: String = "",
    val totpSecret: String? = null,
    val isFavorite: Boolean = false,
    val passwordStrength: PasswordStrength = PasswordStrength.STRONG,
    val lastModified: String = "Today, 10:45 AM"
)

enum class PasswordStrength(val label: String, val scorePercent: Float) {
    WEAK("Weak", 0.25f),
    FAIR("Fair", 0.50f),
    GOOD("Good", 0.75f),
    STRONG("Strong", 1.0f)
}
