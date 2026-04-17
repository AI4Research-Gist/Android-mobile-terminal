package com.example.ai4research.share

import com.example.ai4research.domain.model.ItemType

object SharedTextParser {
    private val genericSource = ShareSource("shared", "分享")
    private val xiaohongshuSource = ShareSource("xiaohongshu", "小红书")
    private val zhihuSource = ShareSource("zhihu", "知乎")
    private val wechatSource = ShareSource("wechat", "微信")

    private val urlRegex =
        Regex("""https?://[^\s<>"'「」『』【】\[\]()（）]+""", RegexOption.IGNORE_CASE)

    private val usefulTextRegex = Regex("""[A-Za-z0-9\u4E00-\u9FFF]""")

    private val boilerplatePatterns = listOf(
        Regex("""(?m)^[A-Za-z0-9]{6,32}$"""),
        Regex("""复制.*打开.*App.*""", RegexOption.IGNORE_CASE),
        Regex("""复制后打开.*""", RegexOption.IGNORE_CASE),
        Regex("""打开.*小红书.*""", RegexOption.IGNORE_CASE),
        Regex("""来自小红书.*""", RegexOption.IGNORE_CASE),
        Regex("""分享自知乎.*""", RegexOption.IGNORE_CASE),
        Regex("""来自知乎.*""", RegexOption.IGNORE_CASE)
    )

    fun parse(rawText: String?, subject: String?, referrerPackage: String? = null): SharedTextDraft? {
        val normalizedRawText = normalizeText(rawText).ifBlank { normalizeText(subject) }
        if (normalizedRawText.isBlank()) return null

        val normalizedSubject = normalizeText(subject).ifBlank { null }
        val url = extractPrimaryUrl(buildString {
            append(normalizedRawText)
            if (!normalizedSubject.isNullOrBlank()) {
                append('\n')
                append(normalizedSubject)
            }
        })
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
                "小红书" in lowerText ||
                "xiaohongshu" in lowerPackage ||
                "xingin" in lowerPackage -> xiaohongshuSource

            "zhihu.com" in lowerUrl ||
                "知乎" in lowerText ||
                "zhihu" in lowerPackage -> zhihuSource

            "mp.weixin.qq.com" in lowerUrl ||
                "微信" in lowerText ||
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
                listOf(
                    Regex("「([^」\\n]{2,80})」"),
                    Regex("“([^”\\n]{2,80})”"),
                    Regex("\"([^\"]{2,80})\"")
                ).flatMap { regex ->
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
                .removeSuffix("- 知乎")
                .removeSuffix("| 知乎")
                .removeSuffix("- 小红书")
                .removeSuffix("| 小红书")
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
            source != genericSource -> "${source.displayName}分享"
            !url.isNullOrBlank() -> "分享链接"
            else -> "分享内容"
        }
    }

    private fun fallbackSummary(source: ShareSource, url: String?): String {
        return when {
            !url.isNullOrBlank() && source != genericSource -> "来自${source.displayName}的分享链接"
            !url.isNullOrBlank() -> "导入了一条分享链接"
            else -> "导入了一段分享文本"
        }
    }

    private fun extractPrimaryUrl(text: String): String? {
        return urlRegex.findAll(text)
            .map { normalizeExtractedUrl(it.value) }
            .firstOrNull { it.isNotBlank() }
    }

    private fun normalizeText(value: String?): String {
        return value.orEmpty()
            .replace("聽", " ")
            .replace("\u200b", "")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .trim()
    }

    private fun trimNoisePunctuation(value: String): String {
        return value.trim(
            ' ', '\n', '\t', '-', '|', ':', '：', ',', '，', '.', '。', ';', '；',
            '!', '！', '?', '？', '(', ')', '（', '）', '[', ']', '【', '】',
            '"', '\'', '“', '”', '‘', '’', '「', '」'
        )
    }

    private fun normalizeExtractedUrl(value: String): String {
        return value.trim()
            .trim(' ', '\n', '\t', ',', '.', ';', ':', '!', '?', '，', '。', '；', '：', '！', '？', ')', ']', '}', '>', '）', '】', '」', '"', '\'', '“', '”', '‘', '’')
    }
}
