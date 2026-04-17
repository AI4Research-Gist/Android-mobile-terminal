package com.example.ai4research.capture.pipeline

import com.example.ai4research.capture.model.CaptureInput
import com.example.ai4research.capture.model.CaptureRawEvidence
import com.example.ai4research.capture.model.CaptureRequest
import com.example.ai4research.capture.model.CaptureResult
import com.example.ai4research.domain.model.ItemType
import com.example.ai4research.service.AIService
import com.example.ai4research.service.LinkParseResult
import com.example.ai4research.service.OCRResult
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageCapturePreparer @Inject constructor(
    private val aiService: AIService
) {
    private val gson = Gson()

    suspend fun prepare(
        request: CaptureRequest,
        input: CaptureInput.ImagePaths
    ): CaptureResult = withContext(Dispatchers.IO) {
        val normalizedPaths = input.paths.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        require(normalizedPaths.isNotEmpty()) { "No valid image paths were found" }

        val pageResults = normalizedPaths.mapIndexed { index, path ->
            processSingleImage(path, index + 1)
        }
        val combinedRawText = buildCombinedRawText(pageResults, includeFailureSummary = true)
        val allLinks = pageResults.flatMap { it.referencedLinks }.distinct()
        val parsedLinkResult = allLinks.firstOrNull()
            ?.let { aiService.parseLinkStructured(it).getOrNull() }

        val title = request.titleHint?.takeIf { it.isNotBlank() }
            ?: parsedLinkResult?.title?.takeIf { it.isNotBlank() }
            ?: pageResults.mapNotNull { it.ocrResult?.title?.takeIf(String::isNotBlank) }.firstOrNull()
            ?: if (normalizedPaths.size > 1) "Image import (${normalizedPaths.size} pages)" else "Image import"

        val summary = request.summaryHint?.takeIf { it.isNotBlank() }
            ?: parsedLinkResult?.title?.takeIf { it.isNotBlank() }
            ?: pageResults.mapNotNull { it.ocrResult?.content?.takeIf(String::isNotBlank) }
                .joinToString("\n")
                .take(220)
                .ifBlank { "Images imported and waiting for further organization" }

        val rawEvidence = CaptureRawEvidence(
            normalizedUrl = allLinks.firstOrNull(),
            rawText = combinedRawText,
            ocrRawText = combinedRawText,
            fetchStrategy = parsedLinkResult?.linkType,
            fallbackReason = if (pageResults.none { it.ocrResult != null }) "ocr_unavailable" else null
        )

        val tags = buildSet {
            allLinks.forEach(::add)
        }.filter { it.isNotBlank() }

        val metaMap = linkedMapOf<String, Any>(
            "capture_entry" to request.entry.wireValue(),
            "capture_mode" to input.captureMode,
            "image_count" to normalizedPaths.size
        ).apply {
            putAll(rawEvidence.toMetaMap())
            if (allLinks.isNotEmpty()) put("referenced_links", allLinks)
            parsedLinkResult?.let {
                put("parsed_link_type", it.linkType)
                put("parsed_title", it.title ?: "")
                put("identifier", it.id ?: "")
            }
        }

        CaptureResult(
            title = title,
            summary = summary,
            contentMarkdown = buildMarkdownContent(parsedLinkResult, combinedRawText, normalizedPaths.size, input.captureMode),
            metaJson = gson.toJson(metaMap),
            originUrl = allLinks.firstOrNull(),
            type = request.expectedType ?: ItemType.ARTICLE,
            tags = tags,
            rawEvidence = rawEvidence
        )
    }

    private suspend fun processSingleImage(path: String, pageIndex: Int): ImportedPage {
        return withContext(Dispatchers.IO) {
            runCatching {
                val rawJson = aiService.recognizeImageFromPath(path).getOrThrow()
                val ocrResult = gson.fromJson(rawJson, OCRResult::class.java)
                ImportedPage(
                    pageIndex = pageIndex,
                    path = path,
                    ocrResult = ocrResult,
                    errorMessage = null,
                    referencedLinks = ocrResult.referencedLinks
                )
            }.getOrElse { error ->
                ImportedPage(
                    pageIndex = pageIndex,
                    path = path,
                    ocrResult = null,
                    errorMessage = error.message ?: "ocr_failed",
                    referencedLinks = emptyList()
                )
            }
        }
    }

    private fun buildCombinedRawText(
        pageResults: List<ImportedPage>,
        includeFailureSummary: Boolean
    ): String {
        return pageResults.joinToString("\n\n") { page ->
            buildString {
                appendLine("## Page ${page.pageIndex}")
                page.ocrResult?.title?.takeIf { it.isNotBlank() }?.let { appendLine("Title clue: $it") }
                page.ocrResult?.authors?.takeIf { it.isNotBlank() }?.let { appendLine("Author clue: $it") }
                page.ocrResult?.identifier?.takeIf { it.isNotBlank() }?.let { appendLine("Identifier clue: $it") }
                if (page.referencedLinks.isNotEmpty()) {
                    appendLine("Link clue: ${page.referencedLinks.joinToString(", ")}")
                }
                if (includeFailureSummary && !page.errorMessage.isNullOrBlank()) {
                    appendLine("Recognition status: ${page.errorMessage}")
                }
                appendLine(page.ocrResult?.content?.takeIf { it.isNotBlank() } ?: "(No OCR text recognized)")
            }.trim()
        }
    }

    private fun buildMarkdownContent(
        result: LinkParseResult?,
        rawText: String,
        pageCount: Int,
        captureMode: String
    ): String {
        if (result == null) {
            return buildString {
                appendLine("# Image Import")
                appendLine()
                appendLine("- Pages: $pageCount")
                appendLine("- Mode: $captureMode")
                appendLine()
                appendLine("## OCR Raw Text")
                appendLine(rawText.ifBlank { "(No OCR raw text)" })
            }
        }

        return buildString {
            appendLine("# ${result.title}")
            appendLine()
            appendLine("- Type: link")
            appendLine("- Source: ${result.linkType}")
            result.id?.takeIf { it.isNotBlank() }?.let { appendLine("- Identifier: $it") }
            result.originalUrl.takeIf { it.isNotBlank() }?.let { appendLine("- Link: $it") }
            appendLine("- Image pages: $pageCount")
            appendLine("- Capture mode: $captureMode")
            appendLine()
            appendLine("## Summary")
            appendLine(result.title ?: "Imported from images")
            appendLine()
            appendLine("## OCR Raw Text")
            appendLine(rawText.ifBlank { "(No OCR raw text)" })
        }
    }

    private data class ImportedPage(
        val pageIndex: Int,
        val path: String,
        val ocrResult: OCRResult?,
        val errorMessage: String?,
        val referencedLinks: List<String>
    )
}
