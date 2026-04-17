package com.example.ai4research.capture.pipeline

import com.example.ai4research.capture.model.CaptureInput
import com.example.ai4research.capture.model.CaptureRawEvidence
import com.example.ai4research.capture.model.CaptureRequest
import com.example.ai4research.capture.model.CaptureResult
import com.example.ai4research.domain.model.ItemType
import com.example.ai4research.service.AIService
import com.example.ai4research.service.LinkParseResult
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultCapturePipeline @Inject constructor(
    private val aiService: AIService,
    private val imageCapturePreparer: ImageCapturePreparer
) : CapturePipeline {
    private val gson = Gson()

    override suspend fun prepare(request: CaptureRequest): Result<CaptureResult> = withContext(Dispatchers.IO) {
        runCatching {
            when (val input = request.input) {
                is CaptureInput.SharedText -> prepareSharedText(request, input)
                is CaptureInput.Url -> prepareUrl(request, input)
                is CaptureInput.ImagePaths -> imageCapturePreparer.prepare(request, input)
            }
        }
    }

    private suspend fun prepareUrl(request: CaptureRequest, input: CaptureInput.Url): CaptureResult {
        val normalizedUrl = input.value.trim()
        require(normalizedUrl.isNotBlank()) { "Link cannot be blank" }

        val parseResult = aiService.parseLinkStructured(normalizedUrl).getOrThrow()
        val rawEvidence = CaptureRawEvidence(
            normalizedUrl = normalizedUrl,
            fetchStrategy = parseResult.linkType
        )
        return prepareParsedLinkResult(
            request = request,
            parseResult = parseResult,
            rawEvidence = rawEvidence,
            sharedInput = null,
            fallbackTitle = request.titleHint,
            fallbackSummary = request.summaryHint
        )
    }

    private suspend fun prepareSharedText(
        request: CaptureRequest,
        input: CaptureInput.SharedText
    ): CaptureResult {
        val normalizedUrl = input.url?.trim()?.takeIf { it.isNotBlank() }
        val requestedTitle = request.titleHint?.takeIf { it.isNotBlank() }
            ?: input.subject?.takeIf { it.isNotBlank() }
        val requestedSummary = request.summaryHint?.takeIf { it.isNotBlank() }

        val parseResult = normalizedUrl?.let { aiService.parseLinkStructured(it).getOrNull() }
        val rawEvidence = CaptureRawEvidence(
            normalizedUrl = normalizedUrl,
            rawShareText = input.rawText,
            rawText = input.rawText,
            fetchStrategy = parseResult?.linkType,
            fallbackReason = if (parseResult == null) "share_text_fallback" else null
        )

        return if (parseResult != null) {
            prepareParsedLinkResult(
                request = request,
                parseResult = parseResult,
                rawEvidence = rawEvidence,
                sharedInput = input,
                fallbackTitle = requestedTitle,
                fallbackSummary = requestedSummary
            )
        } else {
            val title = requestedTitle
                ?: input.rawText.lineSequence().firstOrNull { it.isNotBlank() }?.take(60)
                ?: "Shared Content"
            val summary = requestedSummary
                ?: input.rawText.trim().take(220).ifBlank { "Imported a piece of shared text" }
            val metaMap = buildShareMetaMap(input, title, summary, normalizedUrl, rawEvidence, request.entry.wireValue())

            CaptureResult(
                title = title,
                summary = summary,
                contentMarkdown = buildSimpleMarkdown(
                    title = title,
                    summary = summary,
                    sourceLabel = input.sourceLabel,
                    originUrl = normalizedUrl,
                    rawText = input.rawText
                ),
                metaJson = gson.toJson(metaMap),
                originUrl = normalizedUrl,
                type = request.expectedType ?: if (normalizedUrl.isNullOrBlank()) ItemType.INSIGHT else ItemType.ARTICLE,
                tags = buildShareTags(input, normalizedUrl),
                rawEvidence = rawEvidence
            )
        }
    }

    private fun prepareParsedLinkResult(
        request: CaptureRequest,
        parseResult: LinkParseResult,
        rawEvidence: CaptureRawEvidence,
        sharedInput: CaptureInput.SharedText?,
        fallbackTitle: String?,
        fallbackSummary: String?
    ): CaptureResult {
        val title = fallbackTitle?.takeIf { it.isNotBlank() }
            ?: parseResult.title?.takeIf { it.isNotBlank() }
            ?: parseResult.originalUrl
        val summary = fallbackSummary?.takeIf { it.isNotBlank() }
            ?: parseResult.title?.takeIf { it.isNotBlank() }
            ?: "Imported from link"

        val metaMap = linkedMapOf<String, Any?>(
            "source" to parseResult.linkType,
            "identifier" to parseResult.id,
            "content_type" to request.expectedType?.name?.lowercase(),
            "parsed_title" to parseResult.title,
            "capture_entry" to request.entry.wireValue()
        ).apply {
            putAll(rawEvidence.toMetaMap())
            if (sharedInput != null) {
                putAll(buildShareMetaMap(sharedInput, title, summary, parseResult.originalUrl, rawEvidence, request.entry.wireValue()))
            }
        }

        val tags = listOfNotNull(parseResult.linkType.takeIf { it.isNotBlank() }).distinct()

        return CaptureResult(
            title = title,
            summary = summary,
            contentMarkdown = buildParsedMarkdown(parseResult, sharedInput?.rawText),
            metaJson = gson.toJson(metaMap),
            originUrl = parseResult.originalUrl,
            type = request.expectedType ?: ItemType.ARTICLE,
            tags = tags,
            rawEvidence = rawEvidence
        )
    }

    private fun buildShareMetaMap(
        input: CaptureInput.SharedText,
        title: String,
        summary: String,
        url: String?,
        rawEvidence: CaptureRawEvidence,
        captureEntry: String
    ): Map<String, Any?> = linkedMapOf<String, Any?>(
        "source" to input.sourceId,
        "platform" to input.sourceLabel,
        "summary_short" to summary,
        "shared_title" to title,
        "share_url" to url,
        "share_subject" to input.subject,
        "share_source_id" to input.sourceId,
        "share_source_label" to input.sourceLabel,
        "shared_from_package" to input.referrerPackage,
        "capture_entry" to captureEntry,
        "import_method" to captureEntry
    ).apply { putAll(rawEvidence.toMetaMap()) }

    private fun buildSimpleMarkdown(
        title: String,
        summary: String,
        sourceLabel: String,
        originUrl: String?,
        rawText: String
    ): String {
        val sanitizedRawText = rawText.replace("```", "'''")
        return buildString {
            appendLine("# $title")
            appendLine()
            appendLine("## Summary")
            appendLine(summary.ifBlank { "(No summary)" })
            appendLine()
            appendLine("- Source: $sourceLabel")
            originUrl?.let { appendLine("- Original link: $it") }
            appendLine()
            appendLine("## Raw Text")
            appendLine("```text")
            appendLine(sanitizedRawText)
            appendLine("```")
        }
    }

    private fun buildParsedMarkdown(
        result: LinkParseResult,
        rawText: String?
    ): String {
        return buildString {
            appendLine("# ${result.title}")
            appendLine()
            appendLine("- Type: link")
            appendLine("- Source: ${result.linkType}")
            result.id?.takeIf { it.isNotBlank() }?.let { appendLine("- Identifier: $it") }
            result.originalUrl.takeIf { it.isNotBlank() }?.let { appendLine("- Link: $it") }
            appendLine()
            appendLine("## Summary")
            appendLine(result.title ?: "Imported from link")
            if (!rawText.isNullOrBlank()) {
                appendLine()
                appendLine("## Shared Raw Text")
                appendLine("```text")
                appendLine(rawText.replace("```", "'''"))
                appendLine("```")
            }
        }
    }

    private fun buildShareTags(input: CaptureInput.SharedText, url: String?): List<String> {
        return buildList {
            add(input.sourceId)
            if (!url.isNullOrBlank()) add("has_url")
        }.distinct()
    }

}
