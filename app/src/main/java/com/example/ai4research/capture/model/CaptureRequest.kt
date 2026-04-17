package com.example.ai4research.capture.model

import com.example.ai4research.domain.model.ItemType

data class CaptureRequest(
    val entry: CaptureSource,
    val input: CaptureInput,
    val expectedType: ItemType? = null,
    val titleHint: String? = null,
    val summaryHint: String? = null,
    val projectId: String? = null,
    val projectName: String? = null
)
