package com.example.ai4research.service

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.ai4research.domain.model.ArticlePaperCandidate
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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
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
        private const val TAG = "AIService"
        private const val OCR_MAX_IMAGE_EDGE = 1600
        private const val API_KEY = "sk-devlxlityckbqwlvdtnmfwwxqmrhnlbffjcypryyitbqhkjn"
        private const val AUTH_HEADER = "Bearer $API_KEY"
        private const val INDEX_PARSE_SYSTEM_PROMPT = """
You are an academic indexing assistant for a mobile capture app.
Extract high-quality, objective index fields for papers and articles.

Return strict JSON only.

Priorities:
1. title
2. authors
3. identifier
4. conference / venue
5. year
6. domain_tags
7. keywords
8. method_tags
9. dedup_key
10. summary_short

Rules:
- Be conservative and factual.
- Do not invent user intent, notes, project, priority, or recommendations.
- summary_short must be a Chinese short summary and at most 2 sentences.
- summary_zh should be the concise Chinese display summary.
- summary_en should preserve the English research tone when possible.
- keywords should be concise and searchable.
- method_tags should be method-oriented, not evaluative.
- If unknown, return null or [].

Return JSON in this shape:
{
  "title": "string",
  "authors": "string",
  "summary": "string",
  "summary_short": "string",
  "summary_zh": "string",
  "summary_en": "string",
  "content_type": "paper|competition|article|insight",
  "source": "arxiv|doi|wechat|kaggle|web",
  "identifier": "string",
  "domain_tags": ["string"],
  "keywords": ["string"],
  "method_tags": ["string"],
  "dedup_key": "string",
  "tags": ["string"],
  "meta": {
    "conference": "string",
    "year": "string",
    "platform": "string"
  }
}
"""
        private const val INDEX_COMPLETION_SYSTEM_PROMPT = """
You are a second-pass academic index completion assistant.
You will receive an existing draft plus source content.
Only fill missing or weak index fields. Do not rewrite strong existing fields.

Return strict JSON only with the fields below:
{
  "conference": "string|null",
  "year": "string|null",
  "domain_tags": ["string"],
  "keywords": ["string"],
  "method_tags": ["string"],
  "dedup_key": "string|null",
  "summary_short": "string|null",
  "summary_zh": "string|null",
  "summary_en": "string|null"
}

Rules:
- Prefer precision over coverage.
- summary_short must be a Chinese short summary and at most 2 sentences.
- summary_zh should be concise Chinese.
- summary_en should preserve the English research tone when possible.
- keywords should be concise and useful for retrieval.
- If a field cannot be supported by evidence, return null or [].
"""
        
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
  },
  "organizer": "竞赛主办方（仅competition）",
  "prize_pool": "奖金池（仅competition）",
  "competition_type": "竞赛类型（仅competition）",
  "theme": "竞赛主题（仅competition）",
  "website": "官网链接（仅competition）",
  "registration_url": "报名链接（仅competition）",
  "timeline": [{"name": "报名截止", "date": "YYYY-MM-DD"}]
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
     * 文本总结 - 使用当前配置的文本模型
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
     * 链接解析 - 使用当前配置的文本模型
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
     * 图片识别 - 使用当前配置的视觉模型
     */
    suspend fun recognizeImage(bitmap: Bitmap): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
                return@withContext Result.failure(IllegalArgumentException("图片数据无效"))
            }

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
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OCR bitmap processing ran out of memory", oom)
            Result.failure(Exception("图片过大，OCR 处理内存不足", oom))
        } catch (e: Exception) {
            Log.e(TAG, "recognizeImage failed", e)
            Result.failure(e)
        }
    }

    /**
     * 从文件路径识别图片
     */
    suspend fun recognizeImageFromPath(imagePath: String): Result<String> = withContext(Dispatchers.IO) {
        val bitmapResult = OcrBitmapLoader.loadBitmap(imagePath, OCR_MAX_IMAGE_EDGE)
        val bitmap = bitmapResult.getOrElse { return@withContext Result.failure(it) }

        try {
            recognizeImage(bitmap)
        } finally {
            OcrBitmapLoader.recycle(bitmap)
        }
    }

    /**
     * Bitmap 转 Base64
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        // 压缩图片以减少传输大小
        val scaledBitmap = if (bitmap.width > OCR_MAX_IMAGE_EDGE || bitmap.height > OCR_MAX_IMAGE_EDGE) {
            val scale = minOf(OCR_MAX_IMAGE_EDGE.toFloat() / bitmap.width, OCR_MAX_IMAGE_EDGE.toFloat() / bitmap.height)
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
    
    /**
     * 优化语音识别文本 - 使用AI润色和结构化识别结果
     * @param rawText 原始语音识别文本
     * @return 优化后的文本
     */
    suspend fun enhanceTranscription(rawText: String): Result<String> = withContext(Dispatchers.IO) {
        if (rawText.isBlank()) {
            return@withContext Result.failure(Exception("语音识别结果为空"))
        }
        
        try {
            val systemPrompt = """你是一个专业的语音转文字优化助手。请对以下语音识别文本进行优化：
1. 纠正可能的识别错误（同音字、语法错误等）
2. 添加适当的标点符号
3. 保持原意不变，尽量不增减内容
4. 如果识别结果本身没有问题，直接返回原文

只返回优化后的文本，不要添加任何解释或额外内容。"""

            val request = SimpleChatRequest(
                model = SiliconFlowApiService.MODEL_TEXT,
                messages = listOf(
                    SimpleMessage(role = "system", content = systemPrompt),
                    SimpleMessage(role = "user", content = "请优化以下语音识别文本：\n$rawText")
                ),
                maxTokens = 1024
            )
            
            val response = siliconFlowApi.chatCompletion(AUTH_HEADER, request)
            val content = response.choices.firstOrNull()?.message?.content?.trim()
            
            if (!content.isNullOrBlank()) {
                android.util.Log.d("AIService", "语音优化完成: ${rawText.take(30)}... -> ${content.take(30)}...")
                Result.success(content)
            } else {
                // AI返回空则使用原文
                Result.success(rawText)
            }
        } catch (e: Exception) {
            android.util.Log.w("AIService", "语音优化失败，使用原文: ${e.message}")
            // 优化失败时返回原文
            Result.success(rawText)
        }
    }
    
    /**
     * 音频转文字 - 使用SiliconFlow ASR API
     * @param audioFilePath 音频文件路径
     * @return 转写文本
     */
    suspend fun transcribeAudio(audioFilePath: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val audioFile = java.io.File(audioFilePath)
            if (!audioFile.exists()) {
                return@withContext Result.failure(Exception("音频文件不存在"))
            }
            
            android.util.Log.d("AIService", "开始转写音频: $audioFilePath, 大小: ${audioFile.length()} bytes")
            
            // 创建音频文件的RequestBody
            val mediaType = "audio/mpeg".toMediaType()
            val requestBody = audioFile.asRequestBody(mediaType)
            
            // 创建Multipart
            val filePart = okhttp3.MultipartBody.Part.createFormData(
                "file", 
                audioFile.name, 
                requestBody
            )
            
            // 模型参数
            val modelBody = SiliconFlowApiService.MODEL_ASR.toRequestBody("text/plain".toMediaType())
            
            // 调用API
            val response = siliconFlowApi.audioTranscription(AUTH_HEADER, filePart, modelBody)
            val text = response.text.trim()
            
            if (text.isNotBlank()) {
                android.util.Log.d("AIService", "音频转写完成: ${text.take(50)}...")
                Result.success(text)
            } else {
                android.util.Log.w("AIService", "音频转写结果为空")
                Result.failure(Exception("转写结果为空"))
            }
        } catch (e: Exception) {
            android.util.Log.e("AIService", "音频转写失败: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun generateBilingualSummary(
        title: String,
        sourceContent: String,
        existingSummary: String? = null
    ): Result<BilingualSummaryResult> = withContext(Dispatchers.IO) {
        try {
            val prompt = buildString {
                appendLine("Generate a bilingual academic summary for mobile reading.")
                appendLine("Title: $title")
                existingSummary?.takeIf { it.isNotBlank() }?.let {
                    appendLine("Existing summary: $it")
                }
                appendLine()
                appendLine("Source content:")
                appendLine(sourceContent.take(6000))
            }

            val systemPrompt = """
You are an academic summary assistant.
Produce bilingual summaries for mobile reading.

Return strict JSON only:
{
  "summary_zh": "Chinese summary, concise and accurate",
  "summary_en": "English summary that preserves academic tone",
  "summary_short": "Chinese short summary, at most 2 sentences"
}

Rules:
- summary_zh should read naturally in Chinese.
- summary_en should preserve research tone and terminology.
- summary_short must be Chinese and at most 2 sentences.
- do not output markdown.
- do not output any explanation outside JSON.
""".trimIndent()

            val request = SimpleChatRequest(
                model = SiliconFlowApiService.MODEL_TEXT,
                messages = listOf(
                    SimpleMessage(role = "system", content = systemPrompt),
                    SimpleMessage(role = "user", content = prompt)
                ),
                maxTokens = 900,
                temperature = 0.3
            )

            val response = siliconFlowApi.chatCompletion(AUTH_HEADER, request)
            val content = response.choices.firstOrNull()?.message?.content
                ?: return@withContext Result.failure(Exception("Summary generation returned empty content"))
            val cleanJson = extractJsonFromResponse(content)
            val jsonObj = json.decodeFromString<JsonObject>(cleanJson)

            Result.success(
                BilingualSummaryResult(
                    summaryZh = jsonObj["summary_zh"]?.jsonPrimitive?.contentOrNull,
                    summaryEn = jsonObj["summary_en"]?.jsonPrimitive?.contentOrNull,
                    summaryShort = jsonObj["summary_short"]?.jsonPrimitive?.contentOrNull
                )
            )
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

    private fun readStringList(jsonObj: JsonObject?, key: String): List<String> {
        val element = jsonObj?.get(key) ?: return emptyList()
        return try {
            when (element) {
                is kotlinx.serialization.json.JsonArray -> {
                    element.mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
                        .filter { it.isNotBlank() }
                }
                else -> {
                    element.jsonPrimitive.content.split(",", "，")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun buildDedupKey(identifier: String?, title: String, year: String?): String? {
        val normalizedIdentifier = identifier?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        if (normalizedIdentifier != null) return normalizedIdentifier

        val normalizedTitle = title.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9\\u4e00-\\u9fff]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .takeIf { it.isNotBlank() }
            ?: return null

        return listOfNotNull(normalizedTitle, year?.trim()?.takeIf { it.isNotBlank() })
            .joinToString("#")
    }

    private fun inferYear(identifier: String?, url: String): String? {
        val source = identifier ?: url
        val match = Regex("""(?:arxiv:)?(\d{2})(\d{2})\.\d+""", RegexOption.IGNORE_CASE)
            .find(source)
            ?: Regex("""arxiv\.org/abs/(\d{2})(\d{2})\.\d+""", RegexOption.IGNORE_CASE).find(source)
        val yy = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null
        return (2000 + yy).toString()
    }

    private fun inferDomainTags(title: String): List<String> {
        val lower = title.lowercase()
        val tags = mutableListOf<String>()
        if (lower.contains("large language model") || lower.contains("llm")) tags += "LLM"
        if (lower.contains("language model") || lower.contains("nlp")) tags += "NLP"
        if (lower.contains("vision") || lower.contains("image") || lower.contains("video")) tags += "CV"
        if (lower.contains("multimodal")) tags += "Multimodal"
        if (lower.contains("reinforcement learning") || lower.contains("policy")) tags += "RL"
        return tags.distinct()
    }

    private fun inferKeywords(title: String): List<String> {
        val lower = title.lowercase()
        val keywords = linkedSetOf<String>()

        val phraseMap = listOf(
            "large language models" to "large language models",
            "large language model" to "large language model",
            "language models" to "language models",
            "language model" to "language model",
            "transformer" to "transformer",
            "reasoning" to "reasoning",
            "alignment" to "alignment",
            "retrieval" to "retrieval",
            "diffusion" to "diffusion",
            "conformity" to "conformity",
            "multimodal" to "multimodal"
        )

        phraseMap.forEach { (pattern, keyword) ->
            if (lower.contains(pattern)) keywords += keyword
        }

        if (keywords.isEmpty()) {
            lower.split(Regex("[^a-z0-9]+"))
                .filter { it.length >= 5 }
                .filterNot { it in setOf("think", "models", "paper", "using", "based", "large") }
                .take(4)
                .forEach { keywords += it }
        }

        return keywords.toList()
    }

    private fun extractReferencedLinks(content: String): List<String> {
        return Regex("""https?://[^\s)>\]"]+""")
            .findAll(content)
            .map { it.value.trimEnd('.', ',', ';') }
            .distinct()
            .toList()
    }

    private fun classifyPaperCandidate(url: String): ArticlePaperCandidate? {
        val lowerUrl = url.lowercase()
        val kind = when {
            lowerUrl.contains("arxiv.org") -> "arxiv"
            lowerUrl.contains("doi.org") -> "doi"
            lowerUrl.endsWith(".pdf") -> "pdf"
            lowerUrl.contains("openreview.net") ||
                lowerUrl.contains("aclanthology.org") ||
                lowerUrl.contains("ieeexplore.ieee.org") ||
                lowerUrl.contains("dl.acm.org") ||
                lowerUrl.contains("springer.com") ||
                lowerUrl.contains("nature.com") -> "paper_page"
            else -> null
        } ?: return null

        return ArticlePaperCandidate(url = url, kind = kind)
    }

    private fun readPaperCandidates(jsonObj: JsonObject?, key: String): List<ArticlePaperCandidate> {
        val element = jsonObj?.get(key) as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        return element.mapNotNull { entry ->
            val obj = entry as? JsonObject ?: return@mapNotNull null
            val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            ArticlePaperCandidate(
                url = url,
                label = obj["label"]?.jsonPrimitive?.contentOrNull,
                kind = obj["kind"]?.jsonPrimitive?.contentOrNull ?: "unknown"
            )
        }
    }

    internal fun shouldRunPaperIndexCompletion(result: FullLinkParseResult): Boolean {
        val paperLike = result.toItemType() == com.example.ai4research.domain.model.ItemType.PAPER
        if (!paperLike) return false

        return result.conference.isNullOrBlank() ||
            result.year.isNullOrBlank() ||
            result.summaryShort.isNullOrBlank() ||
            result.domainTags.isEmpty() ||
            result.keywords.isEmpty() ||
            result.methodTags.isEmpty() ||
            result.dedupKey.isNullOrBlank()
    }

    internal fun mergePaperIndexCompletion(
        base: FullLinkParseResult,
        conference: String?,
        year: String?,
        domainTags: List<String>,
        keywords: List<String>,
        methodTags: List<String>,
        dedupKey: String?,
        summaryShort: String?,
        summaryZh: String?,
        summaryEn: String?
    ): FullLinkParseResult {
        val mergedConference = base.conference ?: conference
        val mergedYear = base.year ?: year
        val mergedDomainTags = if (base.domainTags.isNotEmpty()) base.domainTags else domainTags
        val mergedKeywords = if (base.keywords.isNotEmpty()) base.keywords else keywords
        val mergedMethodTags = if (base.methodTags.isNotEmpty()) base.methodTags else methodTags
        val mergedSummaryShort = base.summaryShort ?: summaryShort
        val mergedSummaryZh = base.summaryZh ?: summaryZh
        val mergedSummaryEn = base.summaryEn ?: summaryEn
        val mergedDedupKey = base.dedupKey ?: dedupKey ?: buildDedupKey(base.identifier, base.title, mergedYear)
        val mergedTags = (base.tags + mergedDomainTags + mergedKeywords + mergedMethodTags)
            .filter { it.isNotBlank() }
            .distinct()
            .take(8)

        return base.copy(
            conference = mergedConference,
            year = mergedYear,
            domainTags = mergedDomainTags,
            keywords = mergedKeywords,
            methodTags = mergedMethodTags,
            dedupKey = mergedDedupKey,
            summaryShort = mergedSummaryShort,
            summaryZh = mergedSummaryZh,
            summaryEn = mergedSummaryEn,
            tags = mergedTags
        )
    }

    private suspend fun completePaperIndexesWithAI(
        draft: FullLinkParseResult,
        webContent: WebContent,
        originalUrl: String
    ): FullLinkParseResult {
        if (!shouldRunPaperIndexCompletion(draft)) return draft

        return try {
            val completionPrompt = buildString {
                appendLine("Fill the missing academic index fields for this draft.")
                appendLine("Original URL: $originalUrl")
                appendLine("Current draft title: ${draft.title}")
                appendLine("Current draft authors: ${draft.authors ?: "null"}")
                appendLine("Current draft identifier: ${draft.identifier ?: "null"}")
                appendLine("Current draft conference: ${draft.conference ?: "null"}")
                appendLine("Current draft year: ${draft.year ?: "null"}")
                appendLine("Current draft domain_tags: ${draft.domainTags}")
                appendLine("Current draft keywords: ${draft.keywords}")
                appendLine("Current draft method_tags: ${draft.methodTags}")
                appendLine("Current draft summary_short: ${draft.summaryShort ?: "null"}")
                appendLine()
                appendLine("Source content:")
                appendLine(webContent.content.take(5000))
            }

            val request = SimpleChatRequest(
                model = SiliconFlowApiService.MODEL_TEXT,
                messages = listOf(
                    SimpleMessage(role = "system", content = INDEX_COMPLETION_SYSTEM_PROMPT),
                    SimpleMessage(role = "user", content = completionPrompt)
                ),
                maxTokens = 900,
                temperature = 0.2
            )

            val response = siliconFlowApi.chatCompletion(AUTH_HEADER, request)
            val content = response.choices.firstOrNull()?.message?.content ?: return draft
            val cleanJson = extractJsonFromResponse(content)
            val jsonObj = json.decodeFromString<JsonObject>(cleanJson)

            val completedConference = jsonObj["conference"]?.jsonPrimitive?.contentOrNull
            val completedYear = jsonObj["year"]?.jsonPrimitive?.contentOrNull
            val completedDomainTags = readStringList(jsonObj, "domain_tags")
            val completedKeywords = readStringList(jsonObj, "keywords")
            val completedMethodTags = readStringList(jsonObj, "method_tags")
            val completedDedupKey = jsonObj["dedup_key"]?.jsonPrimitive?.contentOrNull
            val completedSummaryShort = jsonObj["summary_short"]?.jsonPrimitive?.contentOrNull
            val completedSummaryZh = jsonObj["summary_zh"]?.jsonPrimitive?.contentOrNull
            val completedSummaryEn = jsonObj["summary_en"]?.jsonPrimitive?.contentOrNull

            mergePaperIndexCompletion(
                base = draft,
                conference = completedConference,
                year = completedYear,
                domainTags = completedDomainTags,
                keywords = completedKeywords,
                methodTags = completedMethodTags,
                dedupKey = completedDedupKey,
                summaryShort = completedSummaryShort,
                summaryZh = completedSummaryZh,
                summaryEn = completedSummaryEn
            )
        } catch (e: Exception) {
            android.util.Log.w("AIService", "Second-pass paper index completion failed: ${e.message}")
            draft
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
            val contentPrompt = buildString {
                appendLine("Extract academic index fields from this source content.")
                appendLine()
                appendLine("Original URL: $originalUrl")
                appendLine()
                appendLine("Source title: ${webContent.title}")
                appendLine("Source authors: ${webContent.authors ?: "null"}")
                appendLine("Source abstract: ${webContent.abstract ?: "null"}")
                appendLine()
                appendLine("Source content:")
                appendLine(webContent.content.take(7000))
            }

            val request = SimpleChatRequest(
                model = SiliconFlowApiService.MODEL_TEXT,
                messages = listOf(
                    SimpleMessage(role = "system", content = INDEX_PARSE_SYSTEM_PROMPT),
                    SimpleMessage(role = "user", content = contentPrompt)
                ),
                maxTokens = 1800,
                temperature = 0.2
            )
            
            val response = siliconFlowApi.chatCompletion(AUTH_HEADER, request)
            val aiContent = response.choices.firstOrNull()?.message?.content
            
            if (aiContent != null) {
                android.util.Log.d("AIService", "AI 返回: ${aiContent.take(200)}...")
                val cleanJson = extractJsonFromResponse(aiContent)
                val jsonObj = json.decodeFromString<JsonObject>(cleanJson)
                
                // 提取索引标签字段，兼容旧 tags 输出
                val tagsArray = readStringList(jsonObj, "tags")
                val methodTags = readStringList(jsonObj, "method_tags")
                
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
                val summaryShort = jsonObj["summary_short"]?.jsonPrimitive?.content
                    ?: summary.take(160)
                
                val organizer = jsonObj["organizer"]?.jsonPrimitive?.content
                    ?: metaObj?.get("organizer")?.jsonPrimitive?.content
                val prizePool = jsonObj["prize_pool"]?.jsonPrimitive?.content
                    ?: jsonObj["prizePool"]?.jsonPrimitive?.content
                    ?: metaObj?.get("prizePool")?.jsonPrimitive?.content
                val theme = jsonObj["theme"]?.jsonPrimitive?.content
                    ?: metaObj?.get("theme")?.jsonPrimitive?.content
                val competitionType = jsonObj["competition_type"]?.jsonPrimitive?.content
                    ?: jsonObj["competitionType"]?.jsonPrimitive?.content
                    ?: metaObj?.get("competitionType")?.jsonPrimitive?.content
                val website = jsonObj["website"]?.jsonPrimitive?.content
                    ?: metaObj?.get("website")?.jsonPrimitive?.content
                val registrationUrl = jsonObj["registration_url"]?.jsonPrimitive?.content
                    ?: jsonObj["registrationUrl"]?.jsonPrimitive?.content
                    ?: metaObj?.get("registrationUrl")?.jsonPrimitive?.content
                val timeline = parseCompetitionTimeline(jsonObj, metaObj)

                val title = jsonObj["title"]?.jsonPrimitive?.content
                    ?: webContent.title.ifEmpty { "未命名链接" }
                val identifier = jsonObj["identifier"]?.jsonPrimitive?.content
                    ?: extractIdentifierFromUrl(originalUrl)
                val year = metaObj?.get("year")?.jsonPrimitive?.content
                    ?: inferYear(identifier, originalUrl)
                val domainTags = readStringList(jsonObj, "domain_tags")
                    .ifEmpty { inferDomainTags(title) }
                val keywords = readStringList(jsonObj, "keywords")
                    .ifEmpty { tagsArray }
                    .ifEmpty { inferKeywords(title) }
                val dedupKey = jsonObj["dedup_key"]?.jsonPrimitive?.content
                    ?: buildDedupKey(identifier, title, year)
                val mergedTags = (if (tagsArray.isNotEmpty()) tagsArray else keywords + domainTags + methodTags)
                    .distinct()
                    .take(8)
                val referencedLinks = readStringList(jsonObj, "referenced_links")
                    .ifEmpty { extractReferencedLinks(webContent.content) }
                val topicTags = readStringList(jsonObj, "topic_tags")
                    .ifEmpty { domainTags }
                val corePoints = readStringList(jsonObj, "core_points")
                val paperCandidates = readPaperCandidates(jsonObj, "paper_candidates")
                    .ifEmpty { referencedLinks.mapNotNull(::classifyPaperCandidate) }

                val draft = FullLinkParseResult(
                    title = title,
                    authors = jsonObj["authors"]?.jsonPrimitive?.content 
                        ?: webContent.authors,
                    summary = summary,
                    summaryShort = summaryShort,
                    summaryEn = summaryEn,
                    summaryZh = summaryZh,
                    contentType = jsonObj["content_type"]?.jsonPrimitive?.content 
                        ?: guessContentTypeFromUrl(originalUrl),
                    source = jsonObj["source"]?.jsonPrimitive?.content 
                        ?: webContent.source,
                    identifier = identifier,
                    tags = mergedTags,
                    originalUrl = originalUrl,
                    conference = metaObj?.get("conference")?.jsonPrimitive?.content,
                    year = year,
                    platform = metaObj?.get("platform")?.jsonPrimitive?.content,
                    accountName = jsonObj["account_name"]?.jsonPrimitive?.contentOrNull,
                    articleAuthor = jsonObj["author"]?.jsonPrimitive?.contentOrNull
                        ?: jsonObj["authors"]?.jsonPrimitive?.contentOrNull,
                    publishDate = jsonObj["publish_date"]?.jsonPrimitive?.contentOrNull,
                    domainTags = domainTags,
                    keywords = keywords,
                    methodTags = methodTags,
                    topicTags = topicTags,
                    corePoints = corePoints,
                    referencedLinks = referencedLinks,
                    paperCandidates = paperCandidates,
                    dedupKey = dedupKey,
                    organizer = organizer,
                    prizePool = prizePool,
                    theme = theme,
                    competitionType = competitionType,
                    website = website,
                    registrationUrl = registrationUrl,
                    timeline = timeline
                )

                val result = completePaperIndexesWithAI(draft, webContent, originalUrl)
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

    private fun parseCompetitionTimeline(
        jsonObj: JsonObject,
        metaObj: JsonObject?
    ): List<CompetitionTimelineNode>? {
        val element = jsonObj["timeline"] ?: metaObj?.get("timeline") ?: return null
        val array = element as? kotlinx.serialization.json.JsonArray ?: return null
        val nodes = array.mapNotNull { entry ->
            val obj = entry as? JsonObject ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val date = obj["date"]?.jsonPrimitive?.content ?: return@mapNotNull null
            CompetitionTimelineNode(name = name, date = date)
        }
        return nodes.ifEmpty { null }
    }
    
    /**
     * 从抓取的网页内容创建结果（AI 解析失败时的备用方案）
     */
    private fun createResultFromWebContent(webContent: WebContent, originalUrl: String): FullLinkParseResult {
        val title = webContent.title.ifEmpty { extractTitleFromUrl(originalUrl) }
        val identifier = extractIdentifierFromUrl(originalUrl)
        val summary = webContent.abstract ?: webContent.content.take(300)
        val year = inferYear(identifier, originalUrl)
        val domainTags = inferDomainTags(title)
        val keywords = inferKeywords(title)
        val referencedLinks = extractReferencedLinks(webContent.content)
        val paperCandidates = referencedLinks.mapNotNull(::classifyPaperCandidate)
        return FullLinkParseResult(
            title = title,
            authors = webContent.authors,
            summary = summary,
            summaryShort = summary.take(160),
            summaryEn = null, // 无法确定原文语言，暂不生成双语
            summaryZh = null,
            contentType = guessContentTypeFromUrl(originalUrl),
            source = webContent.source,
            identifier = identifier,
            tags = keywords,
            originalUrl = originalUrl,
            conference = null,
            year = year,
            platform = if (originalUrl.contains("arxiv.org", ignoreCase = true)) "arXiv" else null,
            articleAuthor = webContent.authors,
            domainTags = domainTags,
            keywords = keywords,
            topicTags = domainTags,
            referencedLinks = referencedLinks,
            paperCandidates = paperCandidates,
            dedupKey = buildDedupKey(identifier, title, year)
        )
    }
    
    /**
     * 仅解析 URL（网页抓取失败时的降级方案）
     */
    private suspend fun parseUrlOnly(link: String): Result<FullLinkParseResult> {
        try {
            val urlOnlyPrompt = buildString {
                appendLine("Extract academic index fields from this URL using URL structure and prior knowledge conservatively.")
                appendLine("URL: $link")
            }
            val request = SimpleChatRequest(
                model = SiliconFlowApiService.MODEL_TEXT,
                messages = listOf(
                    SimpleMessage(role = "system", content = INDEX_PARSE_SYSTEM_PROMPT),
                    SimpleMessage(role = "user", content = urlOnlyPrompt)
                ),
                maxTokens = 1200,
                temperature = 0.2
            )
            
            val response = siliconFlowApi.chatCompletion(AUTH_HEADER, request)
            val content = response.choices.firstOrNull()?.message?.content
            
            if (content != null) {
                val cleanJson = extractJsonFromResponse(content)
                val jsonObj = json.decodeFromString<JsonObject>(cleanJson)
                
                val tagsArray = readStringList(jsonObj, "tags")
                val methodTags = readStringList(jsonObj, "method_tags")
                
                val metaObj = jsonObj["meta"]?.let { element ->
                    try { element as? JsonObject } catch (e: Exception) { null }
                }
                
                val organizer = jsonObj["organizer"]?.jsonPrimitive?.content
                    ?: metaObj?.get("organizer")?.jsonPrimitive?.content
                val prizePool = jsonObj["prize_pool"]?.jsonPrimitive?.content
                    ?: jsonObj["prizePool"]?.jsonPrimitive?.content
                    ?: metaObj?.get("prizePool")?.jsonPrimitive?.content
                val theme = jsonObj["theme"]?.jsonPrimitive?.content
                    ?: metaObj?.get("theme")?.jsonPrimitive?.content
                val competitionType = jsonObj["competition_type"]?.jsonPrimitive?.content
                    ?: jsonObj["competitionType"]?.jsonPrimitive?.content
                    ?: metaObj?.get("competitionType")?.jsonPrimitive?.content
                val website = jsonObj["website"]?.jsonPrimitive?.content
                    ?: metaObj?.get("website")?.jsonPrimitive?.content
                val registrationUrl = jsonObj["registration_url"]?.jsonPrimitive?.content
                    ?: jsonObj["registrationUrl"]?.jsonPrimitive?.content
                    ?: metaObj?.get("registrationUrl")?.jsonPrimitive?.content
                val timeline = parseCompetitionTimeline(jsonObj, metaObj)
                
                val title = jsonObj["title"]?.jsonPrimitive?.content ?: "未命名链接"
                val identifier = jsonObj["identifier"]?.jsonPrimitive?.content ?: extractIdentifierFromUrl(link)
                val year = metaObj?.get("year")?.jsonPrimitive?.content
                    ?: inferYear(identifier, link)
                val domainTags = readStringList(jsonObj, "domain_tags")
                    .ifEmpty { inferDomainTags(title) }
                val keywords = readStringList(jsonObj, "keywords")
                    .ifEmpty { tagsArray }
                    .ifEmpty { inferKeywords(title) }
                val dedupKey = jsonObj["dedup_key"]?.jsonPrimitive?.content
                    ?: buildDedupKey(identifier, title, year)
                val mergedTags = (if (tagsArray.isNotEmpty()) tagsArray else keywords + domainTags + methodTags)
                    .distinct()
                    .take(8)
                val referencedLinks = readStringList(jsonObj, "referenced_links")
                val topicTags = readStringList(jsonObj, "topic_tags")
                    .ifEmpty { domainTags }
                val corePoints = readStringList(jsonObj, "core_points")
                val paperCandidates = readPaperCandidates(jsonObj, "paper_candidates")
                    .ifEmpty { referencedLinks.mapNotNull(::classifyPaperCandidate) }

                return Result.success(FullLinkParseResult(
                    title = title,
                    authors = jsonObj["authors"]?.jsonPrimitive?.content,
                    summary = jsonObj["summary"]?.jsonPrimitive?.content ?: "链接已保存",
                    summaryShort = jsonObj["summary_short"]?.jsonPrimitive?.content,
                    summaryEn = jsonObj["summary_en"]?.jsonPrimitive?.content,
                    summaryZh = jsonObj["summary_zh"]?.jsonPrimitive?.content,
                    contentType = jsonObj["content_type"]?.jsonPrimitive?.content ?: "insight",
                    source = jsonObj["source"]?.jsonPrimitive?.content ?: "web",
                    identifier = identifier,
                    tags = mergedTags,
                    originalUrl = link,
                    conference = metaObj?.get("conference")?.jsonPrimitive?.content,
                    year = year,
                    platform = metaObj?.get("platform")?.jsonPrimitive?.content,
                    accountName = jsonObj["account_name"]?.jsonPrimitive?.contentOrNull,
                    articleAuthor = jsonObj["author"]?.jsonPrimitive?.contentOrNull
                        ?: jsonObj["authors"]?.jsonPrimitive?.contentOrNull,
                    publishDate = jsonObj["publish_date"]?.jsonPrimitive?.contentOrNull,
                    domainTags = domainTags,
                    keywords = keywords,
                    methodTags = methodTags,
                    topicTags = topicTags,
                    corePoints = corePoints,
                    referencedLinks = referencedLinks,
                    paperCandidates = paperCandidates,
                    dedupKey = dedupKey,
                    organizer = organizer,
                    prizePool = prizePool,
                    theme = theme,
                    competitionType = competitionType,
                    website = website,
                    registrationUrl = registrationUrl,
                    timeline = timeline
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
        val title = extractTitleFromUrl(link)
        val identifier = extractIdentifierFromUrl(link)
        val year = inferYear(identifier, link)
        val domainTags = inferDomainTags(title)
        val keywords = inferKeywords(title)
        return FullLinkParseResult(
            title = title,
            authors = null,
            summary = "链接已保存，内容抓取失败",
            summaryShort = "链接已保存，可在桌面端继续整理。",
            summaryEn = null,
            summaryZh = null,
            contentType = guessContentTypeFromUrl(link),
            source = guessSourceFromUrl(link),
            identifier = identifier,
            tags = keywords,
            originalUrl = link,
            conference = null,
            year = year,
            platform = if (link.contains("arxiv.org", ignoreCase = true)) "arXiv" else null,
            domainTags = domainTags,
            keywords = keywords,
            topicTags = domainTags,
            dedupKey = buildDedupKey(identifier, title, year)
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
            lowerUrl.contains("xiaohongshu.com") -> "article"
            lowerUrl.contains("xhslink.com") -> "article"
            lowerUrl.contains("douyin.com") -> "article"
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
            lowerUrl.contains("xiaohongshu.com") || lowerUrl.contains("xhslink.com") -> "xiaohongshu"
            lowerUrl.contains("douyin.com") -> "douyin"
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

data class BilingualSummaryResult(
    val summaryZh: String?,
    val summaryEn: String?,
    val summaryShort: String?
)

data class CompetitionTimelineNode(
    val name: String,
    val date: String
)

/**
 * 完整链接解析结果 - 包含创建卡片所需的所有信息
 */
data class FullLinkParseResult(
    val title: String,
    val authors: String?,
    val summary: String,          // 主摘要（兼容旧格式）
    val summaryShort: String? = null,
    val summaryEn: String? = null,  // 英文摘要
    val summaryZh: String? = null,  // 中文摘要
    val contentType: String,  // paper, competition, article, insight
    val source: String,       // arxiv, doi, wechat, kaggle, web
    val identifier: String?,  // arXiv ID 或 DOI
    val tags: List<String>,
    val originalUrl: String,
    val conference: String?,
    val year: String?,
    val platform: String?,
    val accountName: String? = null,
    val articleAuthor: String? = null,
    val publishDate: String? = null,
    val domainTags: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    val methodTags: List<String> = emptyList(),
    val topicTags: List<String> = emptyList(),
    val corePoints: List<String> = emptyList(),
    val referencedLinks: List<String> = emptyList(),
    val paperCandidates: List<ArticlePaperCandidate> = emptyList(),
    val dedupKey: String? = null,
    val organizer: String? = null,
    val prizePool: String? = null,
    val theme: String? = null,
    val competitionType: String? = null,
    val website: String? = null,
    val registrationUrl: String? = null,
    val timeline: List<CompetitionTimelineNode>? = null
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
        val looksLikePaper = identifier != null ||
            conference != null ||
            year != null ||
            source.equals("arxiv", ignoreCase = true) ||
            source.equals("doi", ignoreCase = true)

        return when (contentType.lowercase()) {
            "paper" -> com.example.ai4research.domain.model.ItemType.PAPER
            "competition" -> com.example.ai4research.domain.model.ItemType.COMPETITION
            "article" -> if (looksLikePaper) {
                com.example.ai4research.domain.model.ItemType.PAPER
            } else {
                com.example.ai4research.domain.model.ItemType.ARTICLE
            }
            "insight" -> if (looksLikePaper) {
                com.example.ai4research.domain.model.ItemType.PAPER
            } else {
                com.example.ai4research.domain.model.ItemType.INSIGHT
            }
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
            accountName?.let { metaMap["account_name"] = it }
            articleAuthor?.let { metaMap["author"] = it }
            publishDate?.let { metaMap["publish_date"] = it }
            summaryShort?.let { metaMap["summary_short"] = it }
            summaryEn?.let { metaMap["summary_en"] = it }
            summaryZh?.let { metaMap["summary_zh"] = it }
            if (domainTags.isNotEmpty()) metaMap["domain_tags"] = domainTags
            if (keywords.isNotEmpty()) metaMap["keywords"] = keywords
            if (methodTags.isNotEmpty()) metaMap["method_tags"] = methodTags
            if (topicTags.isNotEmpty()) metaMap["topic_tags"] = topicTags
            if (corePoints.isNotEmpty()) metaMap["core_points"] = corePoints
            if (referencedLinks.isNotEmpty()) metaMap["referenced_links"] = referencedLinks
            if (paperCandidates.isNotEmpty()) {
                metaMap["paper_candidates"] = paperCandidates.map { candidate ->
                    mapOf(
                        "url" to candidate.url,
                        "label" to candidate.label,
                        "kind" to candidate.kind
                    )
                }
            }
            dedupKey?.let { metaMap["dedup_key"] = it }
            if (tags.isNotEmpty()) metaMap["tags"] = tags
            organizer?.let { metaMap["organizer"] = it }
            prizePool?.let { metaMap["prizePool"] = it }
            theme?.let { metaMap["theme"] = it }
            competitionType?.let { metaMap["competitionType"] = it }
            website?.let { metaMap["website"] = it }
            registrationUrl?.let { metaMap["registrationUrl"] = it }
            timeline?.let { nodes ->
                metaMap["timeline"] = nodes.map { node ->
                    mapOf("name" to node.name, "date" to node.date, "isPassed" to false)
                }
            }
            
            com.google.gson.Gson().toJson(metaMap)
        } catch (e: Exception) {
            android.util.Log.w("FullLinkParseResult", "toMetaJson 失败: ${e.message}")
            null
        }
    }
}
