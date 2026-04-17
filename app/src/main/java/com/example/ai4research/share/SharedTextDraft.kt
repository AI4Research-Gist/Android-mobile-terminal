package com.example.ai4research.share

import com.example.ai4research.domain.model.ItemType

data class SharedTextDraft(
    val rawText: String,
    val subject: String?,
    val url: String?,
    val source: ShareSource,
    val title: String,
    val summary: String,
    val suggestedType: ItemType,
    val referrerPackage: String? = null
)
