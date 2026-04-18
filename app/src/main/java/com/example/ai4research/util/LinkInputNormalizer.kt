package com.example.ai4research.util

object LinkInputNormalizer {
    private val urlRegex =
        Regex("""https?://[^\s<>"'()\[\]{}]+""", RegexOption.IGNORE_CASE)

    private val trailingTokens = listOf(
        "\uFF0C",
        "\u3002",
        "\uFF1B",
        "\uFF01",
        "\uFF1F",
        "\u3001"
    )

    fun extractFirstUrl(text: String?): String? {
        val normalized = normalizeText(text)
        if (normalized.isBlank()) return null
        return urlRegex.findAll(normalized)
            .map { normalizeUrlCandidate(it.value) }
            .firstOrNull { it.isNotBlank() }
    }

    fun normalizeUrlCandidate(url: String): String {
        var normalized = url.trim().trim(
            ' ', '\n', '\t', ',', '.', ';', ':', '!', '?',
            '\uFF0C', '\u3002', '\uFF1B', '\uFF1A', '\uFF01', '\uFF1F', '\u3001',
            ')', ']', '}', '>', '\uFF09', '\u3011', '\u300F', '\u300D',
            '"', '\'', '\u201C', '\u201D', '\u2018', '\u2019'
        )

        trailingTokens.forEach { token ->
            normalized = normalized.substringBefore(token)
        }
        return normalized.trim()
    }

    private fun normalizeText(value: String?): String {
        return value.orEmpty()
            .replace("\u00A0", " ")
            .replace("\u200b", "")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .trim()
    }
}
