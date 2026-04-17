package com.example.ai4research.capture.model

data class CaptureRawEvidence(
    val normalizedUrl: String? = null,
    val rawShareText: String? = null,
    val rawHtml: String? = null,
    val rawText: String? = null,
    val ocrRawText: String? = null,
    val fetchStrategy: String? = null,
    val fallbackReason: String? = null,
    val parseVersion: String = "capture_v1"
) {
    fun toMetaMap(): Map<String, Any> = buildMap {
        put("parse_version", parseVersion)
        normalizedUrl?.takeIf { it.isNotBlank() }?.let { put("normalized_url", it) }
        rawShareText?.takeIf { it.isNotBlank() }?.let { put("raw_share_text", it) }
        rawHtml?.takeIf { it.isNotBlank() }?.let { put("raw_html", it) }
        rawText?.takeIf { it.isNotBlank() }?.let { put("raw_text", it) }
        ocrRawText?.takeIf { it.isNotBlank() }?.let { put("ocr_raw_text", it) }
        fetchStrategy?.takeIf { it.isNotBlank() }?.let { put("fetch_strategy", it) }
        fallbackReason?.takeIf { it.isNotBlank() }?.let { put("fallback_reason", it) }
    }
}
