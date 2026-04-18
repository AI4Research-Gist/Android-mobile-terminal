package com.example.ai4research.service

import com.example.ai4research.BuildConfig
import com.example.ai4research.data.remote.api.FirecrawlApiService
import com.example.ai4research.data.remote.dto.FirecrawlMetadata
import com.example.ai4research.data.remote.dto.FirecrawlScrapeData
import com.example.ai4research.data.remote.dto.FirecrawlScrapeRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

data class WebContent(
    val title: String,
    val content: String,
    val url: String,
    val authors: String? = null,
    val abstract: String? = null,
    val source: String = "web"
)

@Singleton
class WebContentFetcher @Inject constructor(
    @Named("web") private val okHttpClient: OkHttpClient,
    private val firecrawlApi: FirecrawlApiService
) {
    companion object {
        private const val TAG = "WebContentFetcher"
        private const val MAX_CONTENT_LENGTH = 8_000
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val ARXIV_API_BASE = "https://export.arxiv.org/api/query"
    }

    private val firecrawlAuthorizationHeader: String?
        get() = BuildConfig.FIRECRAWL_API_KEY.trim().takeIf { it.isNotEmpty() }?.let { "Bearer $it" }

    suspend fun fetchContent(url: String): Result<WebContent> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d(TAG, "Start fetching url=$url")
            when {
                isArxivLink(url) -> fetchArxivContent(url)
                isDoiLink(url) -> fetchDoiContent(url)
                isWechatLink(url) -> fetchWechatContent(url)
                shouldUseFirecrawl(url) -> {
                    val firecrawlResult = fetchWithFirecrawl(url)
                    if (firecrawlResult.isSuccess) firecrawlResult else fetchGenericWebContent(url)
                }

                else -> fetchGenericWebContent(url)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Fetch failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun isArxivLink(url: String): Boolean = url.contains("arxiv.org", ignoreCase = true)

    private fun isDoiLink(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        return "doi.org" in lower || "dx.doi.org" in lower
    }

    private fun isWechatLink(url: String): Boolean = url.contains("mp.weixin.qq.com", ignoreCase = true)

    private fun shouldUseFirecrawl(url: String): Boolean {
        if (firecrawlAuthorizationHeader == null) return false
        val lower = url.lowercase(Locale.ROOT)
        return listOf(
            "mp.weixin.qq.com",
            "xhslink.com",
            "xiaohongshu.com",
            "zhihu.com",
            "douyin.com",
            "iesdouyin.com"
        ).any { it in lower }
    }

    private suspend fun fetchArxivContent(url: String): Result<WebContent> {
        return try {
            val arxivId = extractArxivId(url) ?: return fetchGenericWebContent(url)
            val request = Request.Builder()
                .url("$ARXIV_API_BASE?id_list=$arxivId")
                .header("User-Agent", USER_AGENT)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val xml = response.body?.string() ?: error("arXiv API returned empty body")
            val doc = Jsoup.parse(xml, "", org.jsoup.parser.Parser.xmlParser())
            val entry = doc.select("entry").first() ?: error("No arXiv entry found")

            val title = entry.select("title").text().replace("\n", " ").trim()
            val summary = entry.select("summary").text().replace("\n", " ").trim()
            val authors = entry.select("author name").map { it.text() }.joinToString(", ")
            val categories = entry.select("category").map { it.attr("term") }.joinToString(", ")
            val published = entry.select("published").text().take(10)

            Result.success(
                WebContent(
                    title = title,
                    content = buildString {
                        appendLine("## Title")
                        appendLine(title)
                        appendLine()
                        appendLine("## Authors")
                        appendLine(authors)
                        appendLine()
                        appendLine("## arXiv ID")
                        appendLine(arxivId)
                        appendLine()
                        appendLine("## Published")
                        appendLine(published)
                        appendLine()
                        appendLine("## Categories")
                        appendLine(categories)
                        appendLine()
                        appendLine("## Abstract")
                        appendLine(summary)
                    },
                    url = url,
                    authors = authors,
                    abstract = summary,
                    source = "arxiv"
                )
            )
        } catch (e: Exception) {
            android.util.Log.e(TAG, "arXiv fetch failed, fallback to generic", e)
            fetchGenericWebContent(url)
        }
    }

    private fun extractArxivId(url: String): String? {
        val patterns = listOf(
            Regex("""arxiv\.org/(?:abs|pdf)/(\d+\.\d+)""", RegexOption.IGNORE_CASE),
            Regex("""arxiv\.org/(?:abs|pdf)/([\w.-]+/\d+)""", RegexOption.IGNORE_CASE),
            Regex("""arXiv:(\d+\.\d+)""", RegexOption.IGNORE_CASE)
        )
        return patterns.firstNotNullOfOrNull { pattern ->
            pattern.find(url)?.groupValues?.getOrNull(1)?.removeSuffix(".pdf")
        }
    }

    private suspend fun fetchDoiContent(url: String): Result<WebContent> {
        return try {
            val doi = extractDoi(url) ?: return fetchGenericWebContent(url)
            val request = Request.Builder()
                .url("https://api.crossref.org/works/$doi")
                .header("User-Agent", USER_AGENT)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val json = response.body?.string() ?: error("CrossRef API returned empty body")
            val message = org.json.JSONObject(json).getJSONObject("message")

            val title = message.optJSONArray("title")?.optString(0).orEmpty().ifBlank { "Untitled" }
            val authors = buildList {
                val authorsArray = message.optJSONArray("author")
                if (authorsArray != null) {
                    for (i in 0 until authorsArray.length()) {
                        val author = authorsArray.getJSONObject(i)
                        add("${author.optString("given", "")} ${author.optString("family", "")}".trim())
                    }
                }
            }.joinToString(", ")

            val abstractText = Jsoup.parse(message.optString("abstract", "")).text()
            val venue = message.optJSONArray("container-title")?.optString(0).orEmpty()
            val year = message.optJSONObject("published-print")
                ?.optJSONArray("date-parts")
                ?.optJSONArray(0)
                ?.optInt(0)
                ?.takeIf { it > 0 }
                ?.toString()
                .orEmpty()

            Result.success(
                WebContent(
                    title = title,
                    content = buildString {
                        appendLine("## Title")
                        appendLine(title)
                        appendLine()
                        appendLine("## Authors")
                        appendLine(authors)
                        appendLine()
                        appendLine("## DOI")
                        appendLine(doi)
                        if (venue.isNotBlank()) {
                            appendLine()
                            appendLine("## Venue")
                            appendLine(venue)
                        }
                        if (year.isNotBlank()) {
                            appendLine()
                            appendLine("## Year")
                            appendLine(year)
                        }
                        if (abstractText.isNotBlank()) {
                            appendLine()
                            appendLine("## Abstract")
                            appendLine(abstractText)
                        }
                    },
                    url = url,
                    authors = authors.ifBlank { null },
                    abstract = abstractText.ifBlank { null },
                    source = "doi"
                )
            )
        } catch (e: Exception) {
            android.util.Log.e(TAG, "DOI fetch failed, fallback to generic", e)
            fetchGenericWebContent(url)
        }
    }

    private fun extractDoi(url: String): String? {
        val patterns = listOf(
            Regex("""doi\.org/(10\.\d{4,}/[^\s]+)""", RegexOption.IGNORE_CASE),
            Regex("""(10\.\d{4,}/[^\s]+)""", RegexOption.IGNORE_CASE)
        )
        return patterns.firstNotNullOfOrNull { pattern -> pattern.find(url)?.groupValues?.getOrNull(1) }
    }

    private suspend fun fetchWechatContent(url: String): Result<WebContent> {
        val firecrawlResult = fetchWithFirecrawl(url)
        if (firecrawlResult.isSuccess) return firecrawlResult

        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .build()

            val response = okHttpClient.newCall(request).execute()
            val html = response.body?.string() ?: error("Wechat article returned empty body")
            val doc = Jsoup.parse(html)

            val title = doc.select("h1#activity-name").text().trim()
                .ifEmpty { doc.select("meta[property=og:title]").attr("content") }
                .ifEmpty { doc.title() }
                .ifBlank { "Wechat Share" }

            val author = doc.select("#js_name").text().trim()
                .ifEmpty { doc.select("meta[property=og:article:author]").attr("content") }

            val contentDiv = doc.select("#js_content")
            contentDiv.select("script, style, img").remove()
            var content = contentDiv.text().trim()
            if (content.isBlank()) {
                content = doc.select("meta[property=og:description]").attr("content")
            }
            if (content.length > MAX_CONTENT_LENGTH) {
                content = content.take(MAX_CONTENT_LENGTH) + "..."
            }

            Result.success(
                WebContent(
                    title = title,
                    content = buildString {
                        appendLine("## Title")
                        appendLine(title)
                        if (author.isNotBlank()) {
                            appendLine()
                            appendLine("## Account")
                            appendLine(author)
                        }
                        appendLine()
                        appendLine("## Body")
                        appendLine(content)
                    },
                    url = url,
                    authors = author.ifBlank { null },
                    source = "wechat"
                )
            )
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Wechat fetch failed", e)
            fetchGenericWebContent(url)
        }
    }

    private suspend fun fetchWithFirecrawl(url: String): Result<WebContent> {
        val authHeader = firecrawlAuthorizationHeader
            ?: return Result.failure(IllegalStateException("Firecrawl API key is missing"))

        return try {
            val profiles = buildFirecrawlProfiles(url)
            var lastError: Throwable? = null
            for (profile in profiles) {
                try {
                    val response = firecrawlApi.scrape(authHeader, buildFirecrawlRequest(url, profile))
                    val data = response.data
                    if (response.success && data != null && data.hasUsableContent()) {
                        return Result.success(data.toWebContent(url))
                    }
                    lastError = IllegalStateException(response.error ?: response.warning ?: "Firecrawl returned no usable content")
                } catch (e: Exception) {
                    lastError = e
                }
            }
            Result.failure(lastError ?: IllegalStateException("Firecrawl scraping failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private data class FirecrawlScrapeProfile(
        val onlyMainContent: Boolean,
        val timeout: Int,
        val waitFor: Int?,
        val mobile: Boolean,
        val blockAds: Boolean = true,
        val label: String
    )

    private fun buildFirecrawlRequest(url: String, profile: FirecrawlScrapeProfile): FirecrawlScrapeRequest {
        return FirecrawlScrapeRequest(
            url = url,
            formats = listOf("markdown", "html", "rawHtml", "links"),
            onlyMainContent = profile.onlyMainContent,
            timeout = profile.timeout,
            waitFor = profile.waitFor,
            mobile = profile.mobile,
            blockAds = profile.blockAds,
            proxy = "auto",
            removeBase64Images = true,
            skipTlsVerification = false
        )
    }

    private fun buildFirecrawlProfiles(url: String): List<FirecrawlScrapeProfile> {
        val lower = url.lowercase(Locale.ROOT)
        return when {
            "xhslink.com" in lower || "xiaohongshu.com" in lower -> listOf(
                FirecrawlScrapeProfile(false, 60_000, 4_000, true, false, "xiaohongshu-primary"),
                FirecrawlScrapeProfile(false, 75_000, 8_000, true, false, "xiaohongshu-retry")
            )

            "zhihu.com" in lower -> listOf(
                FirecrawlScrapeProfile(false, 45_000, 4_000, false, false, "zhihu-primary"),
                FirecrawlScrapeProfile(true, 60_000, 6_000, false, false, "zhihu-retry")
            )

            "mp.weixin.qq.com" in lower -> listOf(
                FirecrawlScrapeProfile(false, 45_000, 4_000, false, false, "wechat-primary"),
                FirecrawlScrapeProfile(false, 60_000, 8_000, true, true, "wechat-retry")
            )

            "douyin.com" in lower || "iesdouyin.com" in lower -> listOf(
                FirecrawlScrapeProfile(false, 60_000, 4_000, true, false, "douyin-primary"),
                FirecrawlScrapeProfile(false, 75_000, 8_000, true, false, "douyin-retry")
            )

            else -> listOf(
                FirecrawlScrapeProfile(true, 30_000, 1_000, false, true, "default-primary"),
                FirecrawlScrapeProfile(false, 45_000, 4_000, false, false, "default-retry")
            )
        }
    }

    private fun FirecrawlScrapeData.hasUsableContent(): Boolean {
        return !markdown.isNullOrBlank() ||
            !summary.isNullOrBlank() ||
            !html.isNullOrBlank() ||
            !rawHtml.isNullOrBlank()
    }

    private fun FirecrawlScrapeData.toWebContent(requestedUrl: String): WebContent {
        val title = metadata?.title?.takeIf { it.isNotBlank() }
            ?: inferTitleFromMarkdown()
            ?: requestedUrl

        val content = markdown?.takeIf { it.isNotBlank() }
            ?: summary?.takeIf { it.isNotBlank() }
            ?: html?.takeIf { it.isNotBlank() }?.let { Jsoup.parse(it).text() }
            ?: rawHtml?.takeIf { it.isNotBlank() }?.let { Jsoup.parse(it).text() }
            ?: ""

        if (content.isBlank()) {
            throw IllegalStateException("Firecrawl returned empty content")
        }

        return WebContent(
            title = title,
            content = content,
            url = metadata.resolvedUrl(requestedUrl),
            authors = metadata?.author?.takeIf { it.isNotBlank() },
            abstract = summary?.takeIf { it.isNotBlank() },
            source = "web"
        )
    }

    private fun FirecrawlMetadata?.resolvedUrl(fallback: String): String {
        return this?.url?.takeIf { it.isNotBlank() }
            ?: this?.sourceUrl?.takeIf { it.isNotBlank() }
            ?: fallback
    }

    private fun FirecrawlScrapeData.inferTitleFromMarkdown(): String? {
        return markdown
            ?.lineSequence()
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("#") }
            ?.removePrefix("#")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private suspend fun fetchGenericWebContent(url: String): Result<WebContent> {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .build()

            val response = okHttpClient.newCall(request).execute()
            val html = response.body?.string() ?: error("Web page returned empty body")
            val finalUrl = response.request.url.toString()
            val doc = Jsoup.parse(html)

            val title = doc.select("meta[property=og:title]").attr("content")
                .ifEmpty { doc.select("meta[name=title]").attr("content") }
                .ifEmpty { doc.title() }
                .ifBlank { "Untitled" }

            val content = extractMainContent(doc)
            val author = doc.select("meta[name=author]").attr("content")
                .ifEmpty { doc.select("meta[property=article:author]").attr("content") }
            val description = doc.select("meta[property=og:description]").attr("content")
                .ifEmpty { doc.select("meta[name=description]").attr("content") }

            Result.success(
                WebContent(
                    title = title,
                    content = buildString {
                        appendLine("## Title")
                        appendLine(title)
                        if (author.isNotBlank()) {
                            appendLine()
                            appendLine("## Author")
                            appendLine(author)
                        }
                        if (description.isNotBlank()) {
                            appendLine()
                            appendLine("## Description")
                            appendLine(description)
                        }
                        appendLine()
                        appendLine("## Body")
                        appendLine(content)
                    },
                    url = finalUrl,
                    authors = author.ifBlank { null },
                    abstract = description.ifBlank { null },
                    source = "web"
                )
            )
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Generic fetch failed", e)
            Result.failure(e)
        }
    }

    private fun extractMainContent(doc: Document): String {
        val workingDoc = doc.clone()
        workingDoc.select("script, style, nav, footer, header, aside, form, iframe, noscript, .nav, .header, .footer, .sidebar, .advertisement, .ad")
            .remove()
        val mainContent = workingDoc.select("article, main, .content, .article, .post-content, #content, #main").firstOrNull()
        val text = (mainContent ?: workingDoc.body())?.text().orEmpty()
        val cleaned = text.replace(Regex("\\s+"), " ").trim()
        return if (cleaned.length > MAX_CONTENT_LENGTH) cleaned.take(MAX_CONTENT_LENGTH) + "..." else cleaned
    }
}
