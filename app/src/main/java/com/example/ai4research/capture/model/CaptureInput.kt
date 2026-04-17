package com.example.ai4research.capture.model

sealed class CaptureInput {
    data class Url(val value: String) : CaptureInput()

    data class SharedText(
        val rawText: String,
        val subject: String? = null,
        val url: String? = null,
        val referrerPackage: String? = null,
        val sourceId: String = "shared",
        val sourceLabel: String = "分享"
    ) : CaptureInput()

    data class ImagePaths(
        val paths: List<String>,
        val captureMode: String
    ) : CaptureInput()
}
