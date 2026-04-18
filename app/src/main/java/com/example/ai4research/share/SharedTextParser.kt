package com.example.ai4research.share

import com.example.ai4research.domain.model.ItemType
import com.example.ai4research.util.LinkInputNormalizer

object SharedTextParser {
    private val genericSource = ShareSource("shared", "\u5206\u4eab")
    private val xiaohongshuSource = ShareSource("xiaohongshu", "\u5c0f\u7ea2\u4e66")
    private val zhihuSource = ShareSource("zhihu", "\u77e5\u4e4e")
    private val wechatSource = ShareSource("wechat", "\u5fae\u4fe1")

    private val urlRegex =
        Regex("""https?://[^\s<>"'()\[\]{}]+""", RegexOption.IGNORE_CASE)
    private val usefulTextRegex = Regex("""[A-Za-z0-9\u4E00-\u9FFF]""")
    private val quotesRegex = listOf(
        Regex("\u300c([^\u300d\\n]{2,80})\u300d"),
        Regex("\u201c([^\u201d\\n]{2,80})\u201d"),
        Regex("\"([^\"]{2,80})\"")
    )
    private val boilerplatePatterns = listOf(
        Regex("""(?m)^[A-Za-z0-9]{6,32}$"""),
        Regex("\u590d\u5236.*\u6253\u5f00.*App.*", RegexOption.IGNORE_CASE),
        Regex("\u590d\u5236\u540e\u6253\u5f00.*", RegexOption.IGNORE_CASE),
        Regex("\u590d\u5236.*\u6253\u5f00.*\u5c0f\u7ea2\u4e66.*", RegexOption.IGNORE_CASE),
        Regex("\u6253\u5f00.?\u5c0f\u7ea2\u4e66.?App.*", RegexOption.IGNORE_CASE),
        Regex("\u6253\u5f00.*\u5c0f\u7ea2\u4e66.*\u67e5\u770b.*", RegexOption.IGNORE_CASE),
        Regex("\u5c0f\u7ea2\u4e66.*App.*\u67e5\u770b.*", RegexOption.IGNORE_CASE),
        Regex("\u5c0f\u7ea2\u4e66.*\u67e5\u770b\u7b14\u8bb0.*", RegexOption.IGNORE_CASE),
        Regex("\u53d1\u5e03\u4e86.*\u5c0f\u7ea2\u4e66.*\u7b14\u8bb0.*", RegexOption.IGNORE_CASE),
        Regex("\u5feb\u6765\u770b\u770b\u5427[!\uFF01?\uFF1F\\s]*", RegexOption.IGNORE_CASE),
        Regex("\u5206\u4eab\u81ea\u5c0f\u7ea2\u4e66.*", RegexOption.IGNORE_CASE),
        Regex("\u6765\u81ea\u5c0f\u7ea2\u4e66.*", RegexOption.IGNORE_CASE),
        Regex("\u5206\u4eab\u81ea\u77e5\u4e4e.*", RegexOption.IGNORE_CASE),
        Regex("\u6765\u81ea\u77e5\u4e4e.*", RegexOption.IGNORE_CASE),
        Regex("\u5728\u77e5\u4e4e.*\u67e5\u770b.*", RegexOption.IGNORE_CASE),
        Regex("\u77e5\u4e4e.*\u9080\u8bf7\u4f60\u56de\u7b54.*", RegexOption.IGNORE_CASE)
    )

    fun parse(rawText: String?, subject: String?, referrerPackage: String? = null): SharedTextDraft? {
        val normalizedRawText = normalizeText(rawText).ifBlank { normalizeText(subject) }
        if (normalizedRawText.isBlank()) return null

        val normalizedSubject = normalizeText(subject).ifBlank { null }
        val url = extractPrimaryUrl(
            buildString {
                append(normalizedRawText)
                if (!normalizedSubject.isNullOrBlank()) {
                    append('\n')
                    append(normalizedSubject)
                }
            }
        )
        val source = detectSource(url, normalizedRawText, referrerPackage)
        val cleanedText = cleanSharedText(normalizedRawText, url)
        val title = extractTitle(normalizedSubject, normalizedRawText, cleanedText, source, url)
        val summary = extractSummary(cleanedText, normalizedRawText, title, source, url)
        val suggestedType = if (url.isNullOrBlank()) ItemType.INSIGHT else ItemType.ARTICLE

        return SharedTextDraft(
            rawText = normalizedRawText,
            subject = normalizedSubject,
            url = url,
            source = source,
            title = title,
            summary = summary,
            suggestedType = suggestedType,
            referrerPackage = referrerPackage
        )
    }

    fun extractUrlCandidate(text: String?): String? = extractPrimaryUrl(normalizeText(text))

    private fun detectSource(url: String?, text: String, referrerPackage: String?): ShareSource {
        val lowerUrl = url.orEmpty().lowercase()
        val lowerText = text.lowercase()
        val lowerPackage = referrerPackage.orEmpty().lowercase()
        return when {
            "xhslink.com" in lowerUrl ||
                "xiaohongshu.com" in lowerUrl ||
                "\u5c0f\u7ea2\u4e66" in lowerText ||
                "xiaohongshu" in lowerPackage ||
                "xingin" in lowerPackage -> xiaohongshuSource

            "zhihu.com" in lowerUrl ||
                "\u77e5\u4e4e" in lowerText ||
                "zhihu" in lowerPackage -> zhihuSource

            "mp.weixin.qq.com" in lowerUrl ||
                "\u5fae\u4fe1" in lowerText ||
                "weixin" in lowerPackage ||
                "wechat" in lowerPackage -> wechatSource

            else -> genericSource
        }
    }

    private fun cleanSharedText(text: String, url: String?): String {
        var cleaned = text
        if (!url.isNullOrBlank()) {
            cleaned = cleaned.replace(url, " ")
        }
        cleaned = urlRegex.replace(cleaned, " ")
        boilerplatePatterns.forEach { regex ->
            cleaned = regex.replace(cleaned, " ")
        }
        return cleaned
            .lines()
            .map(::normalizeText)
            .map(::trimNoisePunctuation)
            .filter(::isMeaningfulLine)
            .distinct()
            .joinToString("\n")
    }

    private fun extractTitle(
        subject: String?,
        rawText: String,
        cleanedText: String,
        source: ShareSource,
        url: String?
    ): String {
        val candidates = buildList {
            if (!subject.isNullOrBlank()) add(subject)
            addAll(
                quotesRegex.flatMap { regex ->
                    regex.findAll(rawText).map { it.groupValues[1] }.toList()
                }
            )
            addAll(cleanedText.lines())
        }

        return candidates
            .map(::sanitizeTitleCandidate)
            .firstOrNull(::isMeaningfulTitle)
            ?: fallbackTitle(source, url)
    }

    private fun extractSummary(
        cleanedText: String,
        fallbackText: String,
        title: String,
        source: ShareSource,
        url: String?
    ): String {
        val lines = cleanedText
            .lines()
            .map(::sanitizeSummaryLine)
            .filter(::isMeaningfulLine)
            .filterNot { it.equals(title, ignoreCase = true) || it.contains(title, ignoreCase = true) }

        val summary = when {
            lines.isNotEmpty() -> lines.joinToString("\n")
            !url.isNullOrBlank() -> fallbackSummary(source, url)
            else -> sanitizeSummaryLine(fallbackText)
        }

        val cleaned = summary.takeIf { it.isNotBlank() && !looksLikeShareCode(it) }
        return cleaned?.take(220) ?: fallbackSummary(source, url)
    }

    private fun sanitizeTitleCandidate(value: String): String {
        return trimNoisePunctuation(
            normalizeText(value)
                .removeSuffix("- \u77e5\u4e4e")
                .removeSuffix("| \u77e5\u4e4e")
                .removeSuffix("- \u5c0f\u7ea2\u4e66")
                .removeSuffix("| \u5c0f\u7ea2\u4e66")
        )
    }

    private fun sanitizeSummaryLine(value: String): String {
        return trimNoisePunctuation(normalizeText(value).replace(Regex("""\s{2,}"""), " "))
    }

    private fun isMeaningfulTitle(value: String): Boolean {
        return isMeaningfulLine(value) && value.length <= 80 && !looksLikeShareCode(value)
    }

    private fun isMeaningfulLine(value: String): Boolean {
        return value.isNotBlank() && usefulTextRegex.containsMatchIn(value) && !looksLikeShareCode(value)
    }

    private fun looksLikeShareCode(value: String): Boolean {
        val compact = value.replace(" ", "")
        return compact.length in 8..32 && compact.matches(Regex("""[A-Za-z0-9]+"""))
    }

    private fun fallbackTitle(source: ShareSource, url: String?): String {
        return when {
            source != genericSource -> "${source.displayName}\u5206\u4eab"
            !url.isNullOrBlank() -> "\u5206\u4eab\u94fe\u63a5"
            else -> "\u5206\u4eab\u5185\u5bb9"
        }
    }

    private fun fallbackSummary(source: ShareSource, url: String?): String {
        return when {
            !url.isNullOrBlank() && source != genericSource -> "\u6765\u81ea${source.displayName}\u7684\u5206\u4eab\u94fe\u63a5"
            !url.isNullOrBlank() -> "\u5bfc\u5165\u4e86\u4e00\u6761\u5206\u4eab\u94fe\u63a5"
            else -> "\u5bfc\u5165\u4e86\u4e00\u6bb5\u5206\u4eab\u6587\u672c"
        }
    }

    private fun extractPrimaryUrl(text: String): String? = LinkInputNormalizer.extractFirstUrl(text)

    private fun normalizeText(value: String?): String {
        return value.orEmpty()
            .replace("\u00A0", " ")
            .replace("\u200b", "")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .trim()
    }

    private fun trimNoisePunctuation(value: String): String {
        return value.trim(
            ' ', '\n', '\t', '-', '|', ':', '\uFF1A', ',', '\uFF0C', '.', '\u3002', ';', '\uFF1B',
            '!', '\uFF01', '?', '\uFF1F', '(', ')', '\uFF08', '\uFF09', '[', ']', '\u3010', '\u3011',
            '"', '\'', '\u201C', '\u201D', '\u2018', '\u2019', '\u300C', '\u300D'
        )
    }
}
