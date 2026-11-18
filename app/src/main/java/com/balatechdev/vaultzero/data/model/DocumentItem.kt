package com.balatechdev.vaultzero.data.model

data class DocumentItem(
    val id: String,
    val title: String,
    val fileName: String,
    val fileExtension: String,
    val fileSizeFormatted: String,
    val categoryId: String = "cat_identity",
    val addedDate: String = "Nov 14, 2026",
    val notes: String = "",
    val isFavorite: Boolean = false
)
