package com.example.ai4research.service

import android.graphics.Bitmap
import android.util.Base64
import com.example.ai4research.data.remote.api.SiliconFlowApiService
import com.example.ai4research.data.remote.dto.SimpleChatRequest
import com.example.ai4research.data.remote.dto.SimpleMessage
import com.example.ai4research.data.remote.dto.VLChatRequest
import com.example.ai4research.data.remote.dto.VLContent
import com.example.ai4research.data.remote.dto.VLImageUrl
import com.example.ai4research.data.remote.dto.VLMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI 服务管理器 - SiliconFlow 实现
 * 实现  接口，便于后续切换到其他云端模型
 */
@Singleton
class AIService @Inject constructor(
    private val siliconFlowApi: SiliconFlowApiService,
    private val json: Json,
    private val webContentFetcher: WebContentFetcher
) {

    companion object {
        private const val API_KEY = "sk-reqcxhxadjccraythtsdlzbbprgpbwvqghqshqrgplejcbsh"
        private const val AUTH_HEADER = "Bearer $API_KEY"
        
        // 系统提示词
        private const val SYSTEM_PROMPT_SUMMARIZE = """你是一个学术文献助手。请根据提供的内容，提取并总结以下信息：
1. 论文标题
2. 作者（如有）
3. DOI 或 arXiv ID（如有）
4. 摘要或主要内容概述（100字以内）

请以 JSON 格式返回，格式如下：
{"title": "...", "authors": "...", "doi": "...", "summary": "..."}
如果某项信息无法提取，请填写 null。"""

        private const val SYSTEM_PROMPT_OCR = """你是一个专业的论文识别助手。请分析这张图片，提取以下信息：
1. 如果是论文截图：提取标题、作者、DOI/arXiv ID
2. 如果是普通文本：进行OCR识别

请以 JSON 格式返回：
{"type": "paper", "title": "...", "authors": "...", "doi": "...", "content": "识别的文字内容"}
或
{"type": "text", "content": "识别的文字内容"}"""

        private const val SYSTEM_PROMPT_LINK = """你是一个链接解析助手。请分析这个链接，判断它的类型并提取信息：
- arXiv 链接：提取 arXiv ID 和可能的论文标题
- DOI 链接：提取 DOI
- 论文网页：尝试提取论文标题

请以 JSON 格式返回：
{"link_type": "arxiv/doi/webpage", "id": "...", "title": "..."}"""

        // 增强版链接解析提示词 - 用于完整解析链接内容
        private const val SYSTEM_PROMPT_FULL_PARSE = """你是一个专业的学术内容解析助手。请根据提供的链接信息，智能分析并提取结构化内容。

根据链接类型进行不同处理：
1. **arXiv论文**: 根据arXiv ID解析，提取标题、作者、摘要
2. **DOI文献**: 根据DOI提取文献信息
3. **微信公众号/网页文章**: 分析URL特征，推断可能的内容主题
4. **竞赛链接**: 识别Kaggle、天池等竞赛平台链接

请以JSON格式返回，格式如下：
{
  "title": "论文/文章标题（必填，如无法获取请根据链接推断）",
  "authors": "作者列表，用逗号分隔（可选）",
  "summary": "内容摘要或描述（必填，根据URL结构和领域知识生成一段描述性文字，说明这可能是什么内容）",
  "content_type": "paper/competition/article/insight",
  "source": "arxiv/doi/wechat/kaggle/web",
  "identifier": "arXiv ID或DOI（如有）",
  "tags": ["标签1", "标签2"],
  "meta": {
    "conference": "会议名称（如有）",
    "year": "年份（如有）",
    "platform": "平台名称（如有）"
  }
}

注意：
- title必须有值，如果无法从链接获取，请根据URL结构推断一个描述性标题
- summary必须有实质内容！请根据URL结构、标题关键词、平台特征等推断并生成一段有意义的描述（50-150字）。绝对禁止写"待解析"或类似的占位符
- content_type根据链接特征判断：学术链接返回paper，竞赛平台返回competition，公众号/博客返回article，其他返回insight
- 如果是arXiv链接(如arxiv.org/abs/xxxx.xxxxx)，请直接使用arXiv ID作为identifier
- 返回纯JSON，不要包含markdown代码块"""
    }

    fun getProviderName(): String = "SiliconFlow"
    
    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            // 简单测试请求
            val request = SimpleChatRequest(
                model = SiliconFlowApiService.MODEL_TEXT,
                messages = listOf(SimpleMessage(role = "user", content = "test")),
                maxTokens = 5
            )
            siliconFlowApi.chatCompletion(AUTH_HEADER, request)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 文本总结 - 使用 Qwen2.5-14B
     */
    suspend fun summarizeText(text: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = SimpleChatRequest(
                model = SiliconFlowApiService.MODEL_TEXT,
                messages = listOf(
                    SimpleMessage(role = "system", content = SYSTEM_PROMPT_SUMMARIZE),
                    SimpleMessage(role = "user", content = text)
                ),
                maxTokens = 1024
            )
            
            val response = siliconFlowApi.chatCompletion(AUTH_HEADER, request)
            val content = response.choices.firstOrNull()?.message?.content
            
            if (content != null) {
                Result.success(content)
            } else {
                Result.failure(Exception("AI 返回内容为空"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 链接解析 - 使用 Qwen2.5-14B
     */
    suspend fun parseLink(link: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = SimpleChatRequest(
                model = SiliconFlowApiService.MODEL_TEXT,
                messages = listOf(
                    SimpleMessage(role = "system", content = SYSTEM_PROMPT_LINK),
                    SimpleMessage(role = "user", content = "请解析这个链接：$link")
                ),
                maxTokens = 512
            )
            
            val response = siliconFlowApi.chatCompletion(AUTH_HEADER, request)
            val content = response.choices.firstOrNull()?.message?.content
            
            if (content != null) {
                Result.success(content)
            } else {
                Result.failure(Exception("链接解析失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 图片识别 - 使用 Qwen2.5-VL-32B
     */
    suspend fun recognizeImage(bitmap: Bitmap): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 将 Bitmap 转为 Base64
            val base64Image = bitmapToBase64(bitmap)
            val imageUrl = "data:image/jpeg;base64,$base64Image"
            
            val request = VLChatRequest(
                model = SiliconFlowApiService.MODEL_VISION,
                messages = listOf(
                    VLMessage(
                        role = "user",
                        content = listOf(
                            VLContent(type = "text", text = SYSTEM_PROMPT_OCR),
                            VLContent(
                                type = "image_url",
                                imageUrl = VLImageUrl(url = imageUrl)
                            )
                        )
                    )
                ),
                maxTokens = 1024
            )
            
            val response = siliconFlowApi.visionChatCompletion(AUTH_HEADER, request)
            val content = response.choices.firstOrNull()?.message?.content
            
            if (content != null) {
                Result.success(content)
            } else {
                Result.failure(Exception("图片识别失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 从文件路径识别图片
     */
    suspend fun recognizeImageFromPath(imagePath: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val bitmap = android.graphics.BitmapFactory.decodeFile(imagePath)
            if (bitmap != null) {
                recognizeImage(bitmap)
            } else {
                Result.failure(Exception("无法加载图片"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Bitmap 转 Base64
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        // 压缩图片以减少传输大小
        val scaledBitmap = if (bitmap.width > 1920 || bitmap.height > 1920) {
            val scale = minOf(1920f / bitmap.width, 1920f / bitmap.height)
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
        } else {
            bitmap
        }
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    /**
     * 简单对话 - 用于通用问答
     */
    suspend fun chat(message: String, systemPrompt: String?): Result<String> = withContext(Dispatchers.IO) {
        try {
            val messages = mutableListOf<SimpleMessage>()
            if (systemPrompt != null) {
                messages.add(SimpleMessage(role = "system", content = systemPrompt))
            }
            messages.add(SimpleMessage(role = "user", content = message))
            
            val request = SimpleChatRequest(
                model = SiliconFlowApiService.MODEL_TEXT,
                messages = messages,
                maxTokens = 2048
            )
            
            val response = siliconFlowApi.chatCompletion(AUTH_HEADER, request)
            val content = response.choices.firstOrNull()?.message?.content
            
            if (content != null) {
                Result.success(content)
            } else {
                Result.failure(Exception("AI 返回内容为空"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ==================== 结构化解析方法 ====================
    
    /**
     * 解析链接并返回结构化结果
     */
    suspend fun parseLinkStructured(link: String): Result<LinkParseResult> = withContext(Dispatchers.IO) {
        parseLink(link).mapCatching { jsonStr ->
            val cleanJson = extractJsonFromResponse(jsonStr)
            val jsonObj = json.decodeFromString<JsonObject>(cleanJson)
            LinkParseResult(
                linkType = jsonObj["link_type"]?.jsonPrimitive?.content ?: "unknown",
                id = jsonObj["id"]?.jsonPrimitive?.content,
                title = jsonObj["title"]?.jsonPrimitive?.content,
                originalUrl = link
            )
        }
    }
    
    /**
     * 识别图片并返回结构化结果
     */
    suspend fun recognizeImageStructured(bitmap: Bitmap): Result<OCRResult> = withContext(Dispatchers.IO) {
        recognizeImage(bitmap).mapCatching { jsonStr ->
            val cleanJson = extractJsonFromResponse(jsonStr)
            val jsonObj = json.decodeFromString<JsonObject>(cleanJson)
            OCRResult(
                type = jsonObj["type"]?.jsonPrimitive?.content ?: "text",
                title = jsonObj["title"]?.jsonPrimitive?.content,
                authors = jsonObj["authors"]?.jsonPrimitive?.content,
                doi = jsonObj["doi"]?.jsonPrimitive?.content,
                content = jsonObj["content"]?.jsonPrimitive?.content
            )
        }
    }
    
    /**
     * 总结并返回结构化结果
     */
    suspend fun summarizeStructured(text: String): Result<LiteratureInfo> = withContext(Dispatchers.IO) {
        summarizeText(text).mapCatching { jsonStr ->
            val cleanJson = extractJsonFromResponse(jsonStr)
            val jsonObj = json.decodeFromString<JsonObject>(cleanJson)
            LiteratureInfo(
                title = jsonObj["title"]?.jsonPrimitive?.content,
                authors = jsonObj["authors"]?.jsonPrimitive?.content,
                doi = jsonObj["doi"]?.jsonPrimitive?.content,
                summary = jsonObj["summary"]?.jsonPrimitive?.content
            )
        }
    }
    
    /**
     * 从AI响应中提取JSON
     * AI可能会在JSON前后添加额外文字
     */
    private fun extractJsonFromResponse(response: String): String {
        val jsonStart = response.indexOf('{')
        val jsonEnd = response.lastIndexOf('}')
        return if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
            response.substring(jsonStart, jsonEnd + 1)
        } else {
            response
        }
    }

    /**
     * 完整解析链接内容 - 先抓取网页真实内容，再用 AI 结构化解析
     * 
     * 流程：
     * 1. 使用 WebContentFetcher 抓取网页真实内容（arXiv API / DOI API / Jsoup）
     * 2. 将抓取的内容发送给 AI 进行结构化解析
     * 3. 返回可直接用于创建卡片的结构化数据
     */
    suspend fun parseFullLink(link: String): Result<FullLinkParseResult> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("AIService", "========== 开始解析链接 ==========")
            android.util.Log.d("AIService", "链接: $link")
            
            // Step 1: 抓取网页真实内容
            android.util.Log.d("AIService", "Step 1: 抓取网页内容...")
            val webContentResult = webContentFetcher.fetchContent(link)
            
            val webContent = webContentResult.getOrNull()
            if (webContent != null) {
                android.util.Log.d("AIService", "抓取成功: title=${webContent.title.take(50)}, source=${webContent.source}")
                android.util.Log.d("AIService", "内容长度: ${webContent.content.length} 字符")
                
                // Step 2: 用 AI 对抓取的内容进行结构化解析
                android.util.Log.d("AIService", "Step 2: AI 结构化解析...")
                return@withContext parseContentWithAI(webContent, link)
            } else {
                // 抓取失败，降级为仅解析 URL
                android.util.Log.w("AIService", "抓取失败，降级为 URL 解析: ${webContentResult.exceptionOrNull()?.message}")
                return@withContext parseUrlOnly(link)
            }
        } catch (e: Exception) {
            android.util.Log.e("AIService", "parseFullLink 异常: ${e.message}", e)
            // 返回一个基础结果，避免完全失败
            Result.success(createFallbackResult(link))
        }
    }
    
    /**
     * 用 AI 对抓取的网页内容进行结构化解析
     */
    private suspend fun parseContentWithAI(webContent: WebContent, originalUrl: String): Result<FullLinkParseResult> {
        try {
            // 构建提示词，包含真实抓取的内容
            val contentPrompt = buildString {
                appendLine("请根据以下网页内容，提取并总结关键信息：")
                appendLine()
                appendLine("【原始链接】$originalUrl")
                appendLine()
                appendLine("【抓取内容】")
                appendLine(webContent.content.take(5000)) // 限制长度避免超出 token
            }
            
            val systemPrompt = """你是一个专业的学术内容解析助手。请根据提供的网页内容，提取结构化信息。

请以JSON格式返回，格式如下：
{
  "title": "论文/文章标题（从内容中提取）",
  "authors": "作者列表，用逗号分隔（如有）",
  "summary_en": "英文摘要，100-150字（如果原文是英文，提取原文摘要；如果是中文，则翻译为英文）",
  "summary_zh": "中文摘要，100-150字（如果原文是中文，提取原文摘要；如果是英文，则翻译为中文）",
  "content_type": "paper/competition/article/insight",
  "source": "arxiv/doi/wechat/kaggle/web",
  "identifier": "arXiv ID或DOI（如有）",
  "tags": ["标签1", "标签2", "标签3"],
  "meta": {
    "conference": "会议/期刊名称（如有）",
    "year": "发表年份（如有）",
    "platform": "平台名称（如有）"
  }
}

注意：
- summary_en 和 summary_zh 必须同时提供，形成双语对照
- 摘要必须根据提供的正文内容生成，不要写"待解析"
- tags 应该从内容中提取关键词作为标签
- 如果是学术论文，content_type 为 paper
- 返回纯JSON，不要包含markdown代码块"""

            val request = SimpleChatRequest(
                model = SiliconFlowApiService.MODEL_TEXT,
                messages = listOf(
                    SimpleMessage(role = "system", content = systemPrompt),
                    SimpleMessage(role = "user", content = contentPrompt)
                ),
                maxTokens = 1500
            )
            
            val response = siliconFlowApi.chatCompletion(AUTH_HEADER, request)
            val aiContent = response.choices.firstOrNull()?.message?.content
            
            if (aiContent != null) {
                android.util.Log.d("AIService", "AI 返回: ${aiContent.take(200)}...")
                val cleanJson = extractJsonFromResponse(aiContent)
                val jsonObj = json.decodeFromString<JsonObject>(cleanJson)
                
                // 提取tags数组
                val tagsArray = jsonObj["tags"]?.let { element ->
                    try {
                        (element as? kotlinx.serialization.json.JsonArray)?.map { 
                            it.jsonPrimitive.content 
                        } ?: emptyList()
                    } catch (e: Exception) { emptyList() }
                } ?: emptyList()
                
                // 提取meta对象
                val metaObj = jsonObj["meta"]?.let { element ->
                    try {
                        element as? JsonObject
                    } catch (e: Exception) { null }
                }
                
                // 提取双语摘要
                val summaryEn = jsonObj["summary_en"]?.jsonPrimitive?.content
                val summaryZh = jsonObj["summary_zh"]?.jsonPrimitive?.content
                // 兼容旧格式的单语摘要
                val summary = jsonObj["summary"]?.jsonPrimitive?.content
                    ?: summaryZh ?: summaryEn ?: webContent.abstract ?: "已抓取内容，待总结"
                
                val result = FullLinkParseResult(
                    title = jsonObj["title"]?.jsonPrimitive?.content 
                        ?: webContent.title.ifEmpty { "未命名链接" },
                    authors = jsonObj["authors"]?.jsonPrimitive?.content 
                        ?: webContent.authors,
                    summary = summary,
                    summaryEn = summaryEn,
                    summaryZh = summaryZh,
                    contentType = jsonObj["content_type"]?.jsonPrimitive?.content 
                        ?: guessContentTypeFromUrl(originalUrl),
                    source = jsonObj["source"]?.jsonPrimitive?.content 
                        ?: webContent.source,
                    identifier = jsonObj["identifier"]?.jsonPrimitive?.content,
                    tags = tagsArray,
                    originalUrl = originalUrl,
                    conference = metaObj?.get("conference")?.jsonPrimitive?.content,
                    year = metaObj?.get("year")?.jsonPrimitive?.content,
                    platform = metaObj?.get("platform")?.jsonPrimitive?.content
                )
                
                android.util.Log.d("AIService", "✅ 解析完成: ${result.title}")
                return Result.success(result)
            } else {
                // AI 返回空，使用抓取的原始内容
                return Result.success(createResultFromWebContent(webContent, originalUrl))
            }
        } catch (e: Exception) {
            android.util.Log.e("AIService", "AI 解析失败，使用原始抓取内容: ${e.message}")
            return Result.success(createResultFromWebContent(webContent, originalUrl))
        }
    }
    
    /**
     * 从抓取的网页内容创建结果（AI 解析失败时的备用方案）
     */
    private fun createResultFromWebContent(webContent: WebContent, originalUrl: String): FullLinkParseResult {
        return FullLinkParseResult(
            title = webContent.title.ifEmpty { extractTitleFromUrl(originalUrl) },
            authors = webContent.authors,
            summary = webContent.abstract ?: webContent.content.take(300),
            summaryEn = null, // 无法确定原文语言，暂不生成双语
            summaryZh = null,
            contentType = guessContentTypeFromUrl(originalUrl),
            source = webContent.source,
            identifier = extractIdentifierFromUrl(originalUrl),
            tags = emptyList(),
            originalUrl = originalUrl,
            conference = null,
            year = null,
            platform = null
        )
    }
    
    /**
     * 仅解析 URL（网页抓取失败时的降级方案）
     */
    private suspend fun parseUrlOnly(link: String): Result<FullLinkParseResult> {
        try {
            val request = SimpleChatRequest(
                model = SiliconFlowApiService.MODEL_TEXT,
                messages = listOf(
                    SimpleMessage(role = "system", content = SYSTEM_PROMPT_FULL_PARSE),
                    SimpleMessage(role = "user", content = "请解析这个链接的内容：$link")
                ),
                maxTokens = 1024
            )
            
            val response = siliconFlowApi.chatCompletion(AUTH_HEADER, request)
            val content = response.choices.firstOrNull()?.message?.content
            
            if (content != null) {
                val cleanJson = extractJsonFromResponse(content)
                val jsonObj = json.decodeFromString<JsonObject>(cleanJson)
                
                val tagsArray = jsonObj["tags"]?.let { element ->
                    try {
                        (element as? kotlinx.serialization.json.JsonArray)?.map { 
                            it.jsonPrimitive.content 
                        } ?: emptyList()
                    } catch (e: Exception) { emptyList() }
                } ?: emptyList()
                
                val metaObj = jsonObj["meta"]?.let { element ->
                    try { element as? JsonObject } catch (e: Exception) { null }
                }
                
                return Result.success(FullLinkParseResult(
                    title = jsonObj["title"]?.jsonPrimitive?.content ?: "未命名链接",
                    authors = jsonObj["authors"]?.jsonPrimitive?.content,
                    summary = jsonObj["summary"]?.jsonPrimitive?.content ?: "链接已保存",
                    summaryEn = jsonObj["summary_en"]?.jsonPrimitive?.content,
                    summaryZh = jsonObj["summary_zh"]?.jsonPrimitive?.content,
                    contentType = jsonObj["content_type"]?.jsonPrimitive?.content ?: "insight",
                    source = jsonObj["source"]?.jsonPrimitive?.content ?: "web",
                    identifier = jsonObj["identifier"]?.jsonPrimitive?.content,
                    tags = tagsArray,
                    originalUrl = link,
                    conference = metaObj?.get("conference")?.jsonPrimitive?.content,
                    year = metaObj?.get("year")?.jsonPrimitive?.content,
                    platform = metaObj?.get("platform")?.jsonPrimitive?.content
                ))
            }
            return Result.success(createFallbackResult(link))
        } catch (e: Exception) {
            return Result.success(createFallbackResult(link))
        }
    }
    
    /**
     * 创建降级结果
     */
    private fun createFallbackResult(link: String): FullLinkParseResult {
        return FullLinkParseResult(
            title = extractTitleFromUrl(link),
            authors = null,
            summary = "链接已保存，内容抓取失败",
            summaryEn = null,
            summaryZh = null,
            contentType = guessContentTypeFromUrl(link),
            source = guessSourceFromUrl(link),
            identifier = extractIdentifierFromUrl(link),
            tags = emptyList(),
            originalUrl = link,
            conference = null,
            year = null,
            platform = null
        )
    }
    
    /**
     * 从URL中提取标题（作为备用方案）
     */
    private fun extractTitleFromUrl(url: String): String {
        return try {
            val uri = android.net.Uri.parse(url)
            val path = uri.lastPathSegment ?: uri.host ?: "未命名链接"
            // 清理路径，移除扩展名和特殊字符
            path.replace(Regex("\\.(html?|pdf|php|aspx?)$"), "")
                .replace(Regex("[_-]"), " ")
                .take(50)
        } catch (e: Exception) {
            "未命名链接"
        }
    }
    
    /**
     * 从URL推断内容类型
     */
    private fun guessContentTypeFromUrl(url: String): String {
        val lowerUrl = url.lowercase()
        return when {
            lowerUrl.contains("arxiv.org") -> "paper"
            lowerUrl.contains("doi.org") -> "paper"
            lowerUrl.contains("ieee.org") -> "paper"
            lowerUrl.contains("acm.org") -> "paper"
            lowerUrl.contains("springer.com") -> "paper"
            lowerUrl.contains("kaggle.com") -> "competition"
            lowerUrl.contains("tianchi") -> "competition"
            lowerUrl.contains("mp.weixin.qq.com") -> "article"
            else -> "insight"
        }
    }
    
    /**
     * 从URL推断来源
     */
    private fun guessSourceFromUrl(url: String): String {
        val lowerUrl = url.lowercase()
        return when {
            lowerUrl.contains("arxiv.org") -> "arxiv"
            lowerUrl.contains("doi.org") -> "doi"
            lowerUrl.contains("mp.weixin.qq.com") -> "wechat"
            lowerUrl.contains("kaggle.com") -> "kaggle"
            else -> "web"
        }
    }
    
    /**
     * 从URL提取标识符（arXiv ID 或 DOI）
     */
    private fun extractIdentifierFromUrl(url: String): String? {
        // arXiv ID 模式: arxiv.org/abs/xxxx.xxxxx
        val arxivRegex = Regex("arxiv\\.org/abs/(\\d+\\.\\d+)")
        arxivRegex.find(url)?.groupValues?.getOrNull(1)?.let { return it }
        
        // DOI 模式
        val doiRegex = Regex("doi\\.org/(10\\.\\d+/[^\\s]+)")
        doiRegex.find(url)?.groupValues?.getOrNull(1)?.let { return it }
        
        return null
    }
}

// 数据类定义
data class LinkParseResult(
    val linkType: String,
    val id: String?,
    val title: String?,
    val originalUrl: String
)

data class OCRResult(
    val type: String,
    val title: String?,
    val authors: String?,
    val doi: String?,
    val content: String?
)

data class LiteratureInfo(
    val title: String?,
    val authors: String?,
    val doi: String?,
    val summary: String?
)

/**
 * 完整链接解析结果 - 包含创建卡片所需的所有信息
 */
data class FullLinkParseResult(
    val title: String,
    val authors: String?,
    val summary: String,          // 主摘要（兼容旧格式）
    val summaryEn: String? = null,  // 英文摘要
    val summaryZh: String? = null,  // 中文摘要
    val contentType: String,  // paper, competition, article, insight
    val source: String,       // arxiv, doi, wechat, kaggle, web
    val identifier: String?,  // arXiv ID 或 DOI
    val tags: List<String>,
    val originalUrl: String,
    val conference: String?,
    val year: String?,
    val platform: String?
) {
    /**
     * 获取双语摘要格式（用于显示）
     */
    fun getBilingualSummary(): String {
        return when {
            summaryEn != null && summaryZh != null -> {
                "【英文】$summaryEn\n\n【中文】$summaryZh"
            }
            summaryZh != null -> summaryZh
            summaryEn != null -> summaryEn
            else -> summary
        }
    }
    
    /**
     * 转换为 ItemType
     */
    fun toItemType(): com.example.ai4research.domain.model.ItemType {
        return when (contentType.lowercase()) {
            "paper" -> com.example.ai4research.domain.model.ItemType.PAPER
            "competition" -> com.example.ai4research.domain.model.ItemType.COMPETITION
            else -> com.example.ai4research.domain.model.ItemType.INSIGHT
        }
    }
    
    /**
     * 生成 Markdown 内容
     */
    fun toMarkdownContent(): String {
        return buildString {
            appendLine("# $title")
            appendLine()
            authors?.let { appendLine("**作者**: $it") }
            identifier?.let { appendLine("**标识符**: $it") }
            conference?.let { appendLine("**会议/期刊**: $it") }
            year?.let { appendLine("**年份**: $it") }
            platform?.let { appendLine("**平台**: $it") }
            appendLine()
            appendLine("## 摘要")
            // 优先使用双语摘要
            if (summaryEn != null && summaryZh != null) {
                appendLine("### English")
                appendLine(summaryEn)
                appendLine()
                appendLine("### 中文")
                appendLine(summaryZh)
            } else {
                appendLine(summary)
            }
            appendLine()
            appendLine("---")
            appendLine("[原文链接]($originalUrl)")
            if (tags.isNotEmpty()) {
                appendLine()
                appendLine("**标签**: ${tags.joinToString(", ")}")
            }
        }
    }
    
    /**
     * 生成元数据JSON字符串（使用安全的序列化方式）
     */
    fun toMetaJson(): String? {
        return try {
            val metaMap = mutableMapOf<String, Any?>()
            metaMap["source"] = source
            identifier?.let { metaMap["identifier"] = it }
            authors?.let { metaMap["authors"] = it }
            conference?.let { metaMap["conference"] = it }
            year?.let { metaMap["year"] = it }
            summaryEn?.let { metaMap["summary_en"] = it }
            summaryZh?.let { metaMap["summary_zh"] = it }
            if (tags.isNotEmpty()) metaMap["tags"] = tags
            
            // 使用 org.json 安全序列化
            org.json.JSONObject(metaMap).toString()
        } catch (e: Exception) {
            android.util.Log.w("FullLinkParseResult", "toMetaJson 失败: ${e.message}")
            null
        }
    }
}
