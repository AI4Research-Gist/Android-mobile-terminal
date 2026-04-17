package com.example.ai4research.capture.model

import com.example.ai4research.domain.model.ItemType

data class CaptureResult(
    val title: String,
    val summary: String,
    val contentMarkdown: String,
    val metaJson: String? = null,
    val originUrl: String? = null,
    val type: ItemType,
    val tags: List<String>,
    val rawEvidence: CaptureRawEvidence? = null
)
