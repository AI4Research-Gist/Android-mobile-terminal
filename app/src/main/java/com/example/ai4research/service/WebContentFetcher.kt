package com.example.ai4research.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * 网页内容抓取结果
 */
data class WebContent(
    val title: String,
    val content: String,
    val url: String,
    val authors: String? = null,
    val abstract: String? = null,
    val source: String = "web"
)

/**
 * 网页内容抓取服务
 * 支持多种链接类型：arXiv、DOI、微信公众号、普通网页
 */
@Singleton
class WebContentFetcher @Inject constructor(
    @Named("web") private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "WebContentFetcher"
        private const val MAX_CONTENT_LENGTH = 8000 // 最大内容长度
        
        // User-Agent 模拟浏览器
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        
        // arXiv API
        private const val ARXIV_API_BASE = "https://export.arxiv.org/api/query"
    }
    
    /**
     * 根据链接类型智能抓取内容
     */
    suspend fun fetchContent(url: String): Result<WebContent> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d(TAG, "开始抓取链接: $url")
            
            val result = when {
                isArxivLink(url) -> fetchArxivContent(url)
                isDoiLink(url) -> fetchDoiContent(url)
                isWechatLink(url) -> fetchWechatContent(url)
                else -> fetchGenericWebContent(url)
            }
            
            android.util.Log.d(TAG, "抓取完成: title=${result.getOrNull()?.title?.take(50)}")
            result
        } catch (e: Exception) {
            android.util.Log.e(TAG, "抓取失败: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    // ==================== 链接类型判断 ====================
    
    private fun isArxivLink(url: String): Boolean {
        return url.contains("arxiv.org")
    }
    
    private fun isDoiLink(url: String): Boolean {
        return url.contains("doi.org") || url.contains("dx.doi.org")
    }
    
    private fun isWechatLink(url: String): Boolean {
        return url.contains("mp.weixin.qq.com")
    }
    
    // ==================== arXiv 论文抓取 ====================
    
    /**
     * 抓取 arXiv 论文内容
     * 使用 arXiv API 获取元数据
     */
    private suspend fun fetchArxivContent(url: String): Result<WebContent> {
        try {
            // 从 URL 提取 arXiv ID
            val arxivId = extractArxivId(url) ?: return fetchGenericWebContent(url)
            android.util.Log.d(TAG, "arXiv ID: $arxivId")
            
            // 调用 arXiv API
            val apiUrl = "$ARXIV_API_BASE?id_list=$arxivId"
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", USER_AGENT)
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            val xml = response.body?.string() ?: throw Exception("arXiv API 返回空响应")
            
            // 解析 XML 响应
            val doc = Jsoup.parse(xml, "", org.jsoup.parser.Parser.xmlParser())
            val entry = doc.select("entry").first() ?: throw Exception("未找到论文信息")
            
            val title = entry.select("title").text().replace("\n", " ").trim()
            val summary = entry.select("summary").text().replace("\n", " ").trim()
            val authors = entry.select("author name").map { it.text() }.joinToString(", ")
            val categories = entry.select("category").map { it.attr("term") }.joinToString(", ")
            val published = entry.select("published").text().take(10) // YYYY-MM-DD
            
            // 组合完整内容
            val content = buildString {
                appendLine("【论文标题】$title")
                appendLine()
                appendLine("【作者】$authors")
                appendLine()
                appendLine("【arXiv ID】$arxivId")
                appendLine()
                appendLine("【发布日期】$published")
                appendLine()
                appendLine("【分类】$categories")
                appendLine()
                appendLine("【摘要】")
                appendLine(summary)
            }
            
            return Result.success(WebContent(
                title = title,
                content = content,
                url = url,
                authors = authors,
                abstract = summary,
                source = "arxiv"
            ))
        } catch (e: Exception) {
            android.util.Log.e(TAG, "arXiv 抓取失败，尝试通用抓取: ${e.message}")
            return fetchGenericWebContent(url)
        }
    }
    
    /**
     * 从 URL 提取 arXiv ID
     * 支持多种格式：
     * - arxiv.org/abs/2301.12345
     * - arxiv.org/pdf/2301.12345
     * - arxiv.org/abs/cs.AI/0601001
     */
    private fun extractArxivId(url: String): String? {
        val patterns = listOf(
            Regex("""arxiv\.org/(?:abs|pdf)/(\d+\.\d+)"""),
            Regex("""arxiv\.org/(?:abs|pdf)/([\w.-]+/\d+)"""),
            Regex("""arXiv:(\d+\.\d+)""")
        )
        
        for (pattern in patterns) {
            val match = pattern.find(url)
            if (match != null) {
                return match.groupValues[1].removeSuffix(".pdf")
            }
        }
        return null
    }
    
    // ==================== DOI 文献抓取 ====================
    
    /**
     * 抓取 DOI 文献内容
     * 使用 CrossRef API 获取元数据
     */
    private suspend fun fetchDoiContent(url: String): Result<WebContent> {
        try {
            val doi = extractDoi(url) ?: return fetchGenericWebContent(url)
            android.util.Log.d(TAG, "DOI: $doi")
            
            // 调用 CrossRef API
            val apiUrl = "https://api.crossref.org/works/$doi"
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", USER_AGENT)
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            val json = response.body?.string() ?: throw Exception("CrossRef API 返回空响应")
            
            // 使用简单的 JSON 解析
            val jsonObj = org.json.JSONObject(json)
            val message = jsonObj.getJSONObject("message")
            
            val titleArray = message.optJSONArray("title")
            val title = titleArray?.optString(0) ?: "未知标题"
            
            val authorsArray = message.optJSONArray("author")
            val authors = buildList {
                if (authorsArray != null) {
                    for (i in 0 until authorsArray.length()) {
                        val author = authorsArray.getJSONObject(i)
                        val given = author.optString("given", "")
                        val family = author.optString("family", "")
                        add("$given $family".trim())
                    }
                }
            }.joinToString(", ")
            
            val abstractText = message.optString("abstract", "").let {
                Jsoup.parse(it).text() // 移除 HTML 标签
            }
            
            val containerTitle = message.optJSONArray("container-title")?.optString(0) ?: ""
            val publishedParts = message.optJSONObject("published-print")?.optJSONArray("date-parts")?.optJSONArray(0)
            val year = publishedParts?.optInt(0)?.toString() ?: ""
            
            val content = buildString {
                appendLine("【论文标题】$title")
                appendLine()
                appendLine("【作者】$authors")
                appendLine()
                appendLine("【DOI】$doi")
                appendLine()
                if (containerTitle.isNotEmpty()) appendLine("【期刊/会议】$containerTitle")
                if (year.isNotEmpty()) appendLine("【发表年份】$year")
                appendLine()
                if (abstractText.isNotEmpty()) {
                    appendLine("【摘要】")
                    appendLine(abstractText)
                }
            }
            
            return Result.success(WebContent(
                title = title,
                content = content,
                url = url,
                authors = authors,
                abstract = abstractText.takeIf { it.isNotEmpty() },
                source = "doi"
            ))
        } catch (e: Exception) {
            android.util.Log.e(TAG, "DOI 抓取失败，尝试通用抓取: ${e.message}")
            return fetchGenericWebContent(url)
        }
    }
    
    /**
     * 从 URL 提取 DOI
     */
    private fun extractDoi(url: String): String? {
        val patterns = listOf(
            Regex("""doi\.org/(10\.\d{4,}/[^\s]+)"""),
            Regex("""(10\.\d{4,}/[^\s]+)""")
        )
        
        for (pattern in patterns) {
            val match = pattern.find(url)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        return null
    }
    
    // ==================== 微信公众号抓取 ====================
    
    /**
     * 抓取微信公众号文章
     * 微信文章的正文通常在 #js_content div 中
     */
    private suspend fun fetchWechatContent(url: String): Result<WebContent> {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            val html = response.body?.string() ?: throw Exception("微信文章返回空响应")
            
            val doc = Jsoup.parse(html)
            
            // 提取标题
            val title = doc.select("h1#activity-name").text().trim()
                .ifEmpty { doc.select("meta[property=og:title]").attr("content") }
                .ifEmpty { doc.title() }
            
            // 提取作者/公众号名
            val author = doc.select("#js_name").text().trim()
                .ifEmpty { doc.select("meta[property=og:article:author]").attr("content") }
            
            // 提取正文内容
            val contentDiv = doc.select("#js_content")
            contentDiv.select("script, style, img").remove() // 移除脚本和图片
            
            var content = contentDiv.text().trim()
            
            // 如果 js_content 为空（可能需要 JS 渲染），尝试提取 meta 描述
            if (content.isEmpty()) {
                content = doc.select("meta[property=og:description]").attr("content")
            }
            
            // 限制长度
            if (content.length > MAX_CONTENT_LENGTH) {
                content = content.take(MAX_CONTENT_LENGTH) + "..."
            }
            
            val fullContent = buildString {
                appendLine("【文章标题】$title")
                appendLine()
                if (author.isNotEmpty()) {
                    appendLine("【公众号】$author")
                    appendLine()
                }
                appendLine("【正文内容】")
                appendLine(content)
            }
            
            return Result.success(WebContent(
                title = title,
                content = fullContent,
                url = url,
                authors = author.takeIf { it.isNotEmpty() },
                source = "wechat"
            ))
        } catch (e: Exception) {
            android.util.Log.e(TAG, "微信抓取失败，尝试通用抓取: ${e.message}")
            return fetchGenericWebContent(url)
        }
    }
    
    // ==================== 通用网页抓取 ====================
    
    /**
     * 通用网页内容抓取
     * 适用于大部分普通网页
     */
    private suspend fun fetchGenericWebContent(url: String): Result<WebContent> {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            val html = response.body?.string() ?: throw Exception("网页返回空响应")
            
            val doc = Jsoup.parse(html)
            
            // 提取标题
            val title = doc.select("meta[property=og:title]").attr("content")
                .ifEmpty { doc.select("meta[name=title]").attr("content") }
                .ifEmpty { doc.title() }
                .ifEmpty { "未知标题" }
            
            // 提取主要内容
            val content = extractMainContent(doc)
            
            // 尝试提取作者
            val author = doc.select("meta[name=author]").attr("content")
                .ifEmpty { doc.select("meta[property=article:author]").attr("content") }
            
            // 尝试提取描述
            val description = doc.select("meta[property=og:description]").attr("content")
                .ifEmpty { doc.select("meta[name=description]").attr("content") }
            
            val fullContent = buildString {
                appendLine("【标题】$title")
                appendLine()
                if (author.isNotEmpty()) {
                    appendLine("【作者】$author")
                    appendLine()
                }
                if (description.isNotEmpty()) {
                    appendLine("【描述】$description")
                    appendLine()
                }
                appendLine("【正文】")
                appendLine(content)
            }
            
            return Result.success(WebContent(
                title = title,
                content = fullContent,
                url = url,
                authors = author.takeIf { it.isNotEmpty() },
                abstract = description.takeIf { it.isNotEmpty() },
                source = "web"
            ))
        } catch (e: Exception) {
            android.util.Log.e(TAG, "通用抓取失败: ${e.message}")
            return Result.failure(e)
        }
    }
    
    /**
     * 提取网页主要内容
     * 移除导航、脚本、样式等无关元素
     */
    private fun extractMainContent(doc: Document): String {
        // 移除无关元素
        doc.select("script, style, nav, footer, header, aside, .nav, .header, .footer, .sidebar, .advertisement, .ad").remove()
        
        // 优先提取 article 或 main 标签
        val mainContent = doc.select("article, main, .content, .article, .post-content, #content, #main").firstOrNull()
        
        val text = (mainContent ?: doc.body())?.text() ?: ""
        
        // 清理多余空白
        val cleaned = text.replace(Regex("\\s+"), " ").trim()
        
        // 限制长度
        return if (cleaned.length > MAX_CONTENT_LENGTH) {
            cleaned.take(MAX_CONTENT_LENGTH) + "..."
        } else {
            cleaned
        }
    }
}
