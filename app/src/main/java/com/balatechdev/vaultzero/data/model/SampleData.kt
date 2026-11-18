package com.balatechdev.vaultzero.data.model

object SampleData {
    val sampleCredentials = listOf(
        CredentialItem(
            id = "cred_1",
            title = "Proton Mail",
            username = "alex.turner@proton.me",
            passwordPlain = "K9#xL!m9$2pW@8qZ",
            websiteUrl = "https://mail.proton.me",
            categoryId = "cat_logins",
            notes = "Primary encrypted communication mail",
            isFavorite = true,
            passwordStrength = PasswordStrength.STRONG,
            lastModified = "Today, 11:15 AM"
        ),
        CredentialItem(
            id = "cred_2",
            title = "GitHub",
            username = "alex-dev-sec",
            passwordPlain = "GhP_87!b9V#mN2qX%rT4",
            websiteUrl = "https://github.com",
            categoryId = "cat_logins",
            notes = "Work & open-source developer account. Hardware 2FA key enabled.",
            isFavorite = true,
            passwordStrength = PasswordStrength.STRONG,
            lastModified = "Nov 15, 2026"
        ),
        CredentialItem(
            id = "cred_3",
            title = "Crypto Cold Storage PIN",
            username = "Trezor Safe 5",
            passwordPlain = "894102",
            websiteUrl = "",
            categoryId = "cat_financial",
            notes = "Hardware wallet physical device PIN. Do not write down anywhere.",
            isFavorite = true,
            passwordStrength = PasswordStrength.GOOD,
            lastModified = "Nov 10, 2026"
        ),
        CredentialItem(
            id = "cred_4",
            title = "Federal Credit Union",
            username = "aturner_fcu",
            passwordPlain = "Bank$991#AlphaSafe",
            websiteUrl = "https://online.myfcu.org",
            categoryId = "cat_financial",
            notes = "Savings & Checking. Security questions: Mother's pet -> Obsidian",
            isFavorite = false,
            passwordStrength = PasswordStrength.STRONG,
            lastModified = "Oct 28, 2026"
        ),
        CredentialItem(
            id = "cred_5",
            title = "Home Wi-Fi Router",
            username = "admin",
            passwordPlain = "WPA3#Orbit-Mesh-9921",
            websiteUrl = "http://192.168.1.1",
            categoryId = "cat_notes",
            notes = "Dual band guest network disabled. Firmware updated automatically.",
            isFavorite = false,
            passwordStrength = PasswordStrength.FAIR,
            lastModified = "Sep 12, 2026"
        ),
        CredentialItem(
            id = "cred_6",
            title = "Server SSH Master Key Passphrase",
            username = "root@node-primary",
            passwordPlain = "correct-horse-battery-staple-2026!",
            websiteUrl = "",
            categoryId = "cat_notes",
            notes = "ED25519 offline root key passphrase",
            isFavorite = true,
            passwordStrength = PasswordStrength.STRONG,
            lastModified = "Aug 04, 2026"
        )
    )

    val sampleDocuments = listOf(
        DocumentItem(
            id = "doc_1",
            title = "Passport Scan (High-Res)",
            fileName = "passport_biometric_scan.pdf",
            fileExtension = "PDF",
            fileSizeFormatted = "3.4 MB",
            categoryId = "cat_identity",
            addedDate = "Nov 12, 2026",
            notes = "Valid through 2034. Certified true copy.",
            isFavorite = true
        ),
        DocumentItem(
            id = "doc_2",
            title = "Off-Grid House Deed",
            fileName = "property_title_registered.pdf",
            fileExtension = "PDF",
            fileSizeFormatted = "8.1 MB",
            categoryId = "cat_financial",
            addedDate = "Oct 19, 2026",
            notes = "Deed of sale and registered land survey document.",
            isFavorite = false
        ),
        DocumentItem(
            id = "doc_3",
            title = "Health Insurance Card",
            fileName = "health_card_front_back.png",
            fileExtension = "PNG",
            fileSizeFormatted = "1.8 MB",
            categoryId = "cat_identity",
            addedDate = "Sep 01, 2026",
            notes = "Policy #HC-99281-XYZ",
            isFavorite = true
        ),
        DocumentItem(
            id = "doc_4",
            title = "BIP-39 Paper Vault Backup",
            fileName = "seed_mnemonic_archive.enc",
            fileExtension = "ENC",
            fileSizeFormatted = "480 KB",
            categoryId = "cat_notes",
            addedDate = "Aug 20, 2026",
            notes = "Offline encrypted backup container",
            isFavorite = true
        )
    )

    val securityAudit = SecurityAuditStatus()
}
