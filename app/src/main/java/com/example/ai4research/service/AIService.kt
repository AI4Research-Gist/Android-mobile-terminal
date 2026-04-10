package com.example.ai4research.service

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.ai4research.BuildConfig
import com.example.ai4research.ai.AiTarget
import com.example.ai4research.ai.AiTaskRouter
import com.example.ai4research.ai.AiTaskType
import com.example.ai4research.ai.local.LocalAiBackend
import com.example.ai4research.ai.local.LocalChatMessage
import com.example.ai4research.ai.local.LocalGenerationRequest
import com.example.ai4research.domain.model.ArticlePaperCandidate
import com.example.ai4research.domain.model.ItemType
import com.example.ai4research.domain.model.ProjectOverview
import com.example.ai4research.domain.model.ResearchItem
import com.example.ai4research.domain.model.StructuredReadingCard
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
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
    private val webContentFetcher: WebContentFetcher,
    private val aiTaskRouter: AiTaskRouter,
    private val localAiBackend: LocalAiBackend
) {

    companion object {
        private const val TAG = "AIService"
        private const val OCR_MAX_IMAGE_EDGE = 1600
        private val AUTH_HEADER: String
            get() {
                val apiKey = BuildConfig.SILICONFLOW_API_KEY.trim()
                check(apiKey.isNotEmpty()) {
                    "SiliconFlow API key is missing. Set AI4RESEARCH_SILICONFLOW_API_KEY in .env.local or your environment."
                }
                return "Bearer $apiKey"
            }
        private const val SYSTEM_PROMPT_OCR_V2 = """你是移动端截图 OCR 助手。请尽量忠实识别图片中的正文，并提取可验证的结构化线索。

输出严格 JSON，不要包含 markdown 代码块。

返回格式：
{
  "type": "paper|article|text",
  "title": "string|null",
  "authors": "string|null",
  "identifier": "string|null",
  "identifier_type": "arxiv|doi|null",
  "doi": "string|null",
  "referenced_links": ["string"],
  "content": "识别出的正文"
}

规则：
- 优先保证正文 OCR 完整。
- 如果识别到 DOI 或 arXiv 编号，请写入 identifier。
- doi 字段仅在确认为 DOI 时填写；否则可为 null。
- referenced_links 只保留图片中明确出现的链接。
- 没把握的字段返回 null 或 []。"""
        private const val OCR_BATCH_ORGANIZE_SYSTEM_PROMPT = """
You are an OCR post-processing assistant for a mobile capture app.
The user may upload one or more screenshots of the same article, paper, or notes page.

Return strict JSON only.

Goals:
1. clean and organize the OCR content conservatively
2. infer whether the content is paper, article, competition, or insight
3. extract explicit links, arXiv IDs, DOI, article account names, and searchable tags
4. produce a short Chinese summary for card display

Return JSON in this shape:
{
  "title": "string",
  "authors": "string|null",
  "summary": "string",
  "medium_summary": "string",
  "summary_short": "string",
  "summary_zh": "string",
  "summary_en": "string|null",
  "content_type": "paper|article|competition|insight",
  "source": "ocr|wechat|arxiv|doi|web",
  "identifier": "string|null",
  "domain_tags": ["string"],
  "keywords": ["string"],
  "method_tags": ["string"],
  "topic_tags": ["string"],
  "core_points": ["string"],
  "tags": ["string"],
  "referenced_links": ["string"],
  "paper_candidates": [
    { "url": "string", "label": "string|null", "kind": "arxiv|doi|paper_page|pdf|web" }
  ],
  "account_name": "string|null",
  "author": "string|null",
  "publish_date": "string|null",
  "dedup_key": "string|null",
  "meta": {
    "conference": "string|null",
    "year": "string|null",
    "platform": "string|null"
  }
}

Rules:
- Be conservative and factual.
- Do not fabricate links or identifiers.
- If the OCR text is article-like, keep content_type as article even if it mentions papers.
- medium_summary should preserve the main structure and cover multiple important points.
- If the screenshots clearly show a paper page, use content_type=paper.
- summary_short must be concise Chinese within 2 sentences.
- core_points should be 0 to 5 short bullet-like points.
- Unknown fields must be null or [].
"""
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
            val messages = listOf(
                SimpleMessage(role = "system", content = SYSTEM_PROMPT_LINK),
                SimpleMessage(role = "user", content = "请解析这个链接：$link")
            )

            tryOnDeviceText(
                taskType = AiTaskType.LINK_PARSE,
                messages = messages,
                maxTokens = 512,
                temperature = 0.2f
            )?.let { return@withContext it }

            val request = SimpleChatRequest(
                model = SiliconFlowApiService.MODEL_TEXT,
                messages = messages,
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

            val uploadProfiles = listOf(
                OcrUploadProfile(maxEdge = OCR_MAX_IMAGE_EDGE, jpegQuality = 85),
                OcrUploadProfile(maxEdge = 1280, jpegQuality = 72),
                OcrUploadProfile(maxEdge = 1024, jpegQuality = 60)
            )

            var lastError: Exception? = null
            uploadProfiles.forEachIndexed { index, profile ->
                try {
                    val base64Image = bitmapToBase64(
                        bitmap = bitmap,
                        maxEdge = profile.maxEdge,
                        jpegQuality = profile.jpegQuality
                    )
                    val imageUrl = "data:image/jpeg;base64,$base64Image"

                    val request = VLChatRequest(
                        model = SiliconFlowApiService.MODEL_VISION,
                        messages = listOf(
                            VLMessage(
                                role = "user",
                                content = listOf(
                                    VLContent(type = "text", text = SYSTEM_PROMPT_OCR_V2),
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
                        return@withContext Result.success(content)
                    }
                    lastError = Exception("图片识别失败")
                } catch (e: Exception) {
                    lastError = e
                    val shouldRetry = isRetriableVisionError(e) && index != uploadProfiles.lastIndex
                    Log.w(
                        TAG,
                        "recognizeImage attempt ${index + 1} failed, maxEdge=${profile.maxEdge}, quality=${profile.jpegQuality}, retry=$shouldRetry",
                        e
                    )
                    if (!shouldRetry) {
                        throw e
                    }
                }
            }

            Result.failure(lastError ?: Exception("图片识别失败"))
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OCR bitmap processing ran out of memory", oom)
            Result.failure(Exception("图片过大，OCR 处理内存不足", oom))
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "recognizeImage timed out", e)
            Result.failure(Exception("图片上传超时，请重试或换一张更清晰但尺寸更小的图片", e))
        } catch (e: SocketException) {
            Log.e(TAG, "recognizeImage socket failed", e)
            Result.failure(Exception("图片上传时连接中断，请稍后重试", e))
        } catch (e: IOException) {
            Log.e(TAG, "recognizeImage io failed", e)
            Result.failure(Exception("网络波动导致图片解析失败，请稍后重试", e))
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
    private fun bitmapToBase64(
        bitmap: Bitmap,
        maxEdge: Int = OCR_MAX_IMAGE_EDGE,
        jpegQuality: Int = 85
    ): String {
        val outputStream = ByteArrayOutputStream()
        // 压缩图片以减少传输大小
        val scaledBitmap = if (bitmap.width > maxEdge || bitmap.height > maxEdge) {
            val scale = minOf(maxEdge.toFloat() / bitmap.width, maxEdge.toFloat() / bitmap.height)
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
        } else {
            bitmap
        }
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    private fun isRetriableVisionError(error: Throwable): Boolean {
        return error is SocketTimeoutException ||
            error is SocketException ||
            error is IOException ||
            error.message?.contains("connection abort", ignoreCase = true) == true ||
            error.message?.contains("timeout", ignoreCase = true) == true
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

            tryOnDeviceText(
                taskType = AiTaskType.TRANSCRIPTION_ENHANCE,
                messages = request.messages,
                maxTokens = 512,
                temperature = 0.2f
            )?.let { localResult ->
                return@withContext localResult.fold(
                    onSuccess = { content ->
                        if (content.isBlank()) Result.success(rawText) else Result.success(content.trim())
                    },
                    onFailure = { Result.success(rawText) }
                )
            }
            
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

    suspend fun generateStructuredReadingCard(
        title: String,
        sourceContent: String,
        existingSummary: String? = null,
        existingCard: StructuredReadingCard? = null,
        itemType: String = "paper"
    ): Result<StructuredReadingCardResult> = withContext(Dispatchers.IO) {
        try {
            val prompt = buildString {
                appendLine("Build a structured reading card for a mobile research app.")
                appendLine("Item type: $itemType")
                appendLine("Title: $title")
                existingSummary?.takeIf { it.isNotBlank() }?.let {
                    appendLine("Existing summary: $it")
                }
                existingCard?.takeUnless { it.isEmpty() }?.let { card ->
                    appendLine("Existing reading card draft:")
                    appendLine("research_question=${card.researchQuestion ?: "null"}")
                    appendLine("method=${card.method ?: "null"}")
                    appendLine("dataset=${card.dataset ?: "null"}")
                    appendLine("findings=${card.findings ?: "null"}")
                    appendLine("limitations=${card.limitations ?: "null"}")
                    appendLine("reuse_points=${card.reusePoints ?: "null"}")
                    appendLine("my_notes=${card.myNotes ?: "null"}")
                }
                appendLine()
                appendLine("Source content:")
                appendLine(sourceContent.take(8000))
            }

            val systemPrompt = """
You are a research reading assistant.
Turn a paper or article into a concise structured reading card.

Return strict JSON only:
{
  "research_question": "string|null",
  "method": "string|null",
  "dataset": "string|null",
  "findings": "string|null",
  "limitations": "string|null",
  "reuse_points": "string|null",
  "my_notes": "string|null"
}

Rules:
- Be evidence-based and conservative.
- Use concise Chinese by default.
- my_notes should contain only concrete takeaways grounded in the source.
- Unknown fields must be null.
- Do not output markdown or explanations outside JSON.
""".trimIndent()

            val request = SimpleChatRequest(
                model = SiliconFlowApiService.MODEL_TEXT,
                messages = listOf(
                    SimpleMessage(role = "system", content = systemPrompt),
                    SimpleMessage(role = "user", content = prompt)
                ),
                maxTokens = 1000,
                temperature = 0.3
            )

            val response = siliconFlowApi.chatCompletion(AUTH_HEADER, request)
            val content = response.choices.firstOrNull()?.message?.content
                ?: return@withContext Result.failure(Exception("Reading card generation returned empty content"))
            val cleanJson = extractJsonFromResponse(content)
            val jsonObj = json.decodeFromString<JsonObject>(cleanJson)

            Result.success(
                StructuredReadingCardResult(
                    card = StructuredReadingCard(
                        researchQuestion = readFlexibleString(jsonObj, "research_question"),
                        method = readFlexibleString(jsonObj, "method"),
                        dataset = readFlexibleString(jsonObj, "dataset"),
                        findings = readFlexibleString(jsonObj, "findings"),
                        limitations = readFlexibleString(jsonObj, "limitations"),
                        reusePoints = readFlexibleString(jsonObj, "reuse_points"),
                        myNotes = readFlexibleString(jsonObj, "my_notes")
                    ),
                    rawJson = cleanJson
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun answerQuestionAboutItem(
        title: String,
        summary: String,
        contentMarkdown: String,
        metaJson: String?,
        question: String,
        itemType: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (question.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Question is empty"))
        }

        val compactMeta = buildCompactItemMeta(metaJson)
        val compactContent = buildCompactItemContent(summary, contentMarkdown, metaJson)

        val prompt = buildString {
            appendLine("Answer the user's question about one research item from a mobile app.")
            appendLine("Item type: $itemType")
            appendLine("Title: $title")
            appendLine("Summary: ${summary.ifBlank { "null" }}")
            appendLine("Compact meta: $compactMeta")
            appendLine()
            appendLine("Prioritized context:")
            appendLine(compactContent)
            appendLine()
            appendLine("User question:")
            appendLine(question.trim())
        }

        val systemPrompt = """
You are an academic reading assistant.
Answer only using the provided item context.

Rules:
- Respond in concise Chinese unless the user asks otherwise.
- If evidence is insufficient, say what is missing.
- Prefer concrete, structured answers.
- Prioritize the summary and compact meta first, then use the excerpted content.
- Do not fabricate experiments, metrics, or conclusions.
""".trimIndent()

        try {
            val messages = listOf(
                SimpleMessage(role = "system", content = systemPrompt),
                SimpleMessage(role = "user", content = prompt)
            )

            tryOnDeviceText(
                taskType = AiTaskType.ITEM_QA_SHORT,
                messages = messages,
                maxTokens = 700,
                temperature = 0.2f
            )?.let { return@withContext it }

            fun buildRequest(model: String) = SimpleChatRequest(
                model = model,
                messages = messages,
                maxTokens = 700,
                temperature = 0.2,
                enableThinking = false
            )

            val response = runCatching {
                siliconFlowApi.chatCompletion(AUTH_HEADER, buildRequest(SiliconFlowApiService.MODEL_FAST_TEXT))
            }.recoverCatching {
                siliconFlowApi.chatCompletion(AUTH_HEADER, buildRequest(SiliconFlowApiService.MODEL_TEXT))
            }.getOrThrow()

            val content = response.choices.firstOrNull()?.message?.content

            if (content != null) {
                Result.success(content)
            } else {
                Result.failure(Exception("AI returned empty content"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun tryOnDeviceText(
        taskType: AiTaskType,
        messages: List<SimpleMessage>,
        maxTokens: Int,
        temperature: Float
    ): Result<String>? {
        val decision = aiTaskRouter.decide(taskType)
        if (decision.preferredTarget != AiTarget.LOCAL) {
            return null
        }

        val localResult = localAiBackend.generateText(
            taskType = taskType,
            request = LocalGenerationRequest(
                messages = messages.map { LocalChatMessage(role = it.role, content = it.content) },
                maxTokens = maxTokens,
                temperature = temperature
            )
        )

        if (localResult.isSuccess) {
            Log.d(TAG, "On-device route succeeded for $taskType")
            return localResult
        }

        if (!decision.allowCloudFallback) {
            Log.w(TAG, "On-device route failed for $taskType without cloud fallback: ${localResult.exceptionOrNull()?.message}")
            return localResult
        }

        Log.w(TAG, "On-device route failed for $taskType, falling back to cloud: ${localResult.exceptionOrNull()?.message}")
        return null
    }

    private fun buildCompactItemMeta(metaJson: String?): String {
        if (metaJson.isNullOrBlank()) return "null"

        return runCatching {
            val jsonObj = json.decodeFromString<JsonObject>(metaJson)
            buildMap<String, Any?> {
                readFlexibleString(jsonObj, "identifier")?.let { put("identifier", it) }
                readFlexibleString(jsonObj, "conference")?.let { put("conference", it) }
                readFlexibleString(jsonObj, "year")?.let { put("year", it) }
                readFlexibleString(jsonObj, "platform")?.let { put("platform", it) }
                readFlexibleString(jsonObj, "account_name")?.let { put("account_name", it) }
                readFlexibleString(jsonObj, "summary_short")?.let { put("summary_short", it) }
                readFlexibleString(jsonObj, "medium_summary")?.let { put("medium_summary", it) }
                readFlexibleString(jsonObj, "summary_zh")?.let { put("summary_zh", it) }
                readFlexibleString(jsonObj, "summary_en")?.let { put("summary_en", it) }
                readFlexibleStringList(jsonObj, "keywords").takeIf { it.isNotEmpty() }?.let { put("keywords", it.take(8)) }
                readFlexibleStringList(jsonObj, "domain_tags").takeIf { it.isNotEmpty() }?.let { put("domain_tags", it.take(8)) }
                readFlexibleStringList(jsonObj, "method_tags").takeIf { it.isNotEmpty() }?.let { put("method_tags", it.take(8)) }
                (jsonObj["reading_card"] as? JsonObject)?.let { readingCard ->
                    put(
                        "reading_card",
                        buildMap<String, String> {
                            readFlexibleString(readingCard, "research_question")?.let { put("research_question", it) }
                            readFlexibleString(readingCard, "method")?.let { put("method", it) }
                            readFlexibleString(readingCard, "dataset")?.let { put("dataset", it) }
                            readFlexibleString(readingCard, "findings")?.let { put("findings", it) }
                            readFlexibleString(readingCard, "limitations")?.let { put("limitations", it) }
                            readFlexibleString(readingCard, "reuse_points")?.let { put("reuse_points", it) }
                            readFlexibleString(readingCard, "my_notes")?.let { put("my_notes", it) }
                        }
                    )
                }
            }.let { compact ->
                if (compact.isEmpty()) "null" else com.google.gson.Gson().toJson(compact)
            }
        }.getOrDefault("null")
    }

    private fun buildCompactItemContent(summary: String, contentMarkdown: String, metaJson: String?): String {
        val ocrFocusedContent = extractOcrOriginalContent(contentMarkdown)
        val normalizedContent = (ocrFocusedContent ?: contentMarkdown).trim()
            .replace(Regex("```[\\s\\S]*?```"), " ")
            .replace(Regex("`([^`]*)`"), "$1")
            .replace(Regex("#{1,6}\\s*"), "")
            .replace(Regex("\\[(.*?)\\]\\((.*?)\\)"), "$1")
            .replace(Regex("\\s+"), " ")
            .trim()

        val isOcrSource = isOcrDerived(metaJson)
        val excerpt = when {
            normalizedContent.isBlank() -> summary
            isOcrSource -> buildMultiWindowExcerpt(normalizedContent, 9000)
            normalizedContent.length <= 2400 -> normalizedContent
            else -> buildMultiWindowExcerpt(normalizedContent, 3600)
        }

        val summaryLabel = if (isOcrSource) "中等摘要：" else "摘要优先视图："
        val contentLabel = if (isOcrSource) "OCR 原文关键片段：" else "正文摘录："

        return buildString {
            if (summary.isNotBlank()) {
                appendLine(summaryLabel)
                appendLine(summary.take(if (isOcrSource) 1200 else 600))
                appendLine()
            }
            appendLine(contentLabel)
            appendLine(excerpt.ifBlank { "null" })
        }.trim()
    }

    private fun extractOcrOriginalContent(contentMarkdown: String): String? {
        val marker = "## OCR原文"
        val start = contentMarkdown.indexOf(marker)
        if (start == -1) return null

        val section = contentMarkdown.substring(start + marker.length).trim()
        return when {
            section.startsWith("```text") -> {
                section.removePrefix("```text")
                    .substringBeforeLast("```")
                    .trim()
                    .takeIf { it.isNotBlank() }
            }
            section.startsWith("```") -> {
                section.removePrefix("```")
                    .substringBeforeLast("```")
                    .trim()
                    .takeIf { it.isNotBlank() }
            }
            else -> section.takeIf { it.isNotBlank() }
        }
    }

    private fun buildMultiWindowExcerpt(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text

        val window = maxChars / 3
        val head = text.take(window)
        val middleStart = (text.length / 2 - window / 2).coerceAtLeast(0)
        val middleEnd = (middleStart + window).coerceAtMost(text.length)
        val middle = text.substring(middleStart, middleEnd)
        val tail = text.takeLast(window)

        return buildString {
            appendLine("【开头片段】")
            appendLine(head)
            appendLine()
            appendLine("【中间片段】")
            appendLine(middle)
            appendLine()
            appendLine("【结尾片段】")
            appendLine(tail)
        }.trim()
    }

    private fun isOcrDerived(metaJson: String?): Boolean {
        if (metaJson.isNullOrBlank()) return false
        return runCatching {
            val jsonObj = json.decodeFromString<JsonObject>(metaJson)
            val source = readFlexibleString(jsonObj, "source")
            source == "ocr" || jsonObj["ocr_page_count"] != null || jsonObj["capture_mode"] != null
        }.getOrDefault(false)
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
                    summaryZh = readFlexibleString(jsonObj, "summary_zh"),
                    summaryEn = readFlexibleString(jsonObj, "summary_en"),
                    summaryShort = readFlexibleString(jsonObj, "summary_short")
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
    suspend fun summarizeProjectContextMarkdown(
        projectName: String,
        markdownContent: String
    ): Result<ProjectContextSummaryResult> = withContext(Dispatchers.IO) {
        try {
            val prompt = buildString {
                appendLine("Project name: $projectName")
                appendLine("Summarize this research background markdown for future project-level collaboration.")
                appendLine()
                appendLine("Markdown content:")
                appendLine(markdownContent.take(10000))
            }

            val systemPrompt = """
You are a research project context summarizer.

Return strict JSON only:
{
  "title": "string|null",
  "summary": "string",
  "keywords": ["string"]
}

Rules:
- summary must be concise but information-dense Chinese.
- summary should preserve the user's current research direction, current problems, constraints, and goals.
- keywords should be short and useful for retrieval.
- do not output markdown.
- do not output explanations outside JSON.
""".trimIndent()

            val request = SimpleChatRequest(
                model = SiliconFlowApiService.MODEL_TEXT,
                messages = listOf(
                    SimpleMessage(role = "system", content = systemPrompt),
                    SimpleMessage(role = "user", content = prompt)
                ),
                maxTokens = 1000,
                temperature = 0.2,
                enableThinking = false
            )

            val response = siliconFlowApi.chatCompletion(AUTH_HEADER, request)
            val content = response.choices.firstOrNull()?.message?.content
                ?: return@withContext Result.failure(Exception("Project context summary returned empty content"))
            val cleanJson = extractJsonFromResponse(content)
            val jsonObj = json.decodeFromString<JsonObject>(cleanJson)

            Result.success(
                ProjectContextSummaryResult(
                    title = readFlexibleString(jsonObj, "title"),
                    summary = readFlexibleString(jsonObj, "summary") ?: markdownContent.take(300),
                    keywords = readFlexibleStringList(jsonObj, "keywords").take(12)
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun summarizeProjectOverview(
        overview: ProjectOverview
    ): Result<ProjectOverviewSummaryResult> = withContext(Dispatchers.IO) {
        try {
            val prompt = buildProjectOverviewPrompt(overview)

            val systemPrompt = """
You are a project-level research assistant for a personal research app.

Return strict JSON only:
{
  "current_theme": "string",
  "recent_progress": ["string"],
  "key_literature": ["string"],
  "insight_focus": ["string"],
  "pending_questions": ["string"],
  "next_actions": ["string"]
}

Rules:
- respond in concise, concrete Chinese
- each list item should be actionable and non-generic
- do not invent papers or experiments not in input
- if evidence is weak, explicitly frame as hypothesis or suggestion
- lists should have 2-5 items when possible
- no markdown and no extra explanation outside JSON
""".trimIndent()

            val request = SimpleChatRequest(
                model = SiliconFlowApiService.MODEL_TEXT,
                messages = listOf(
                    SimpleMessage(role = "system", content = systemPrompt),
                    SimpleMessage(role = "user", content = prompt)
                ),
                maxTokens = 1200,
                temperature = 0.2,
                enableThinking = false
            )

            val response = siliconFlowApi.chatCompletion(AUTH_HEADER, request)
            val content = response.choices.firstOrNull()?.message?.content
                ?: return@withContext Result.failure(Exception("Project summary returned empty content"))
            val cleanJson = extractJsonFromResponse(content)
            val jsonObj = json.decodeFromString<JsonObject>(cleanJson)

            val currentTheme = readFlexibleStringByKeys(
                jsonObj,
                "current_theme",
                "project_theme",
                "theme"
            ) ?: "${overview.project.name}：研究主题待进一步聚焦"

            Result.success(
                ProjectOverviewSummaryResult(
                    currentTheme = currentTheme,
                    recentProgress = readFlexibleStringListByKeys(
                        jsonObj,
                        "recent_progress",
                        "progress",
                        "latest_progress"
                    ).take(5),
                    keyLiterature = readFlexibleStringListByKeys(
                        jsonObj,
                        "key_literature",
                        "important_literature",
                        "literature"
                    ).take(5),
                    insightFocus = readFlexibleStringListByKeys(
                        jsonObj,
                        "insight_focus",
                        "insight_points",
                        "insights"
                    ).take(5),
                    pendingQuestions = readFlexibleStringListByKeys(
                        jsonObj,
                        "pending_questions",
                        "open_questions",
                        "gaps"
                    ).take(5),
                    nextActions = readFlexibleStringListByKeys(
                        jsonObj,
                        "next_actions",
                        "actions",
                        "next_steps"
                    ).take(5)
                ).withFallbacks(overview)
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recommendItemsForInsight(
        insight: ResearchItem,
        candidates: List<ResearchItem>,
        projectContextSummary: String? = null,
        projectContextKeywords: List<String> = emptyList()
    ): Result<InsightRecommendationResult> = withContext(Dispatchers.IO) {
        try {
            if (candidates.isEmpty()) {
                return@withContext Result.success(InsightRecommendationResult(emptyList()))
            }

            val prompt = buildInsightRecommendationPrompt(
                insight = insight,
                candidates = candidates,
                projectContextSummary = projectContextSummary,
                projectContextKeywords = projectContextKeywords
            )

            val systemPrompt = """
You are a research assistant that helps connect a new insight note to the most relevant local papers and articles.

Return strict JSON only:
{
  "recommendations": [
    {
      "candidate_index": 0,
      "reason": "string",
      "suggested_question": "string|null"
    }
  ]
}

Rules:
- choose only from the provided candidate list
- prefer 2-5 recommendations when evidence exists
- reasons must be specific, concise, and grounded in the provided text
- if the match is weak, say it is a tentative lead
- do not invent facts, papers, experiments, or metadata
- do not output markdown or any extra explanation
""".trimIndent()

            val request = SimpleChatRequest(
                model = SiliconFlowApiService.MODEL_TEXT,
                messages = listOf(
                    SimpleMessage(role = "system", content = systemPrompt),
                    SimpleMessage(role = "user", content = prompt)
                ),
                maxTokens = 1200,
                temperature = 0.2,
                enableThinking = false
            )

            val response = siliconFlowApi.chatCompletion(AUTH_HEADER, request)
            val content = response.choices.firstOrNull()?.message?.content
                ?: return@withContext Result.failure(Exception("Insight recommendation returned empty content"))
            val cleanJson = extractJsonFromResponse(content)
            val jsonObj = json.decodeFromString<JsonObject>(cleanJson)
            val recommendationArray = jsonObj["recommendations"] as? JsonArray ?: JsonArray(emptyList())

            val recommendations = recommendationArray.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val index = readFlexibleStringByKeys(obj, "candidate_index", "index")
                    ?.toIntOrNull()
                    ?: obj["candidate_index"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: return@mapNotNull null
                InsightRecommendation(
                    candidateIndex = index,
                    reason = readFlexibleString(obj, "reason") ?: return@mapNotNull null,
                    suggestedQuestion = readFlexibleStringByKeys(obj, "suggested_question", "question")
                )
            }.distinctBy { it.candidateIndex }

            Result.success(InsightRecommendationResult(recommendations))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun compareResearchItems(
        leftItem: ResearchItem,
        rightItem: ResearchItem,
        projectContextSummary: String? = null,
        projectContextKeywords: List<String> = emptyList()
    ): Result<LiteratureComparisonResult> = withContext(Dispatchers.IO) {
        try {
            val prompt = buildLiteratureComparisonPrompt(
                leftItem = leftItem,
                rightItem = rightItem,
                projectContextSummary = projectContextSummary,
                projectContextKeywords = projectContextKeywords
            )

            val systemPrompt = """
You are a research comparison assistant for a personal research app.

Return strict JSON only:
{
  "common_points": ["string"],
  "differences": ["string"],
  "complementarities": ["string"],
  "project_fit": "string",
  "recommendation": "string"
}

Rules:
- respond in concise, specific Chinese
- common_points / differences / complementarities should each contain 2-5 items when possible
- do not invent experiments, datasets, or conclusions not present in the inputs
- recommendation should say how the user should use these two items next
- if evidence is weak, state that clearly
- no markdown and no extra explanation
""".trimIndent()

            val request = SimpleChatRequest(
                model = SiliconFlowApiService.MODEL_TEXT,
                messages = listOf(
                    SimpleMessage(role = "system", content = systemPrompt),
                    SimpleMessage(role = "user", content = prompt)
                ),
                maxTokens = 1200,
                temperature = 0.2,
                enableThinking = false
            )

            val response = siliconFlowApi.chatCompletion(AUTH_HEADER, request)
            val content = response.choices.firstOrNull()?.message?.content
                ?: return@withContext Result.failure(Exception("Literature comparison returned empty content"))
            val cleanJson = extractJsonFromResponse(content)
            val jsonObj = json.decodeFromString<JsonObject>(cleanJson)

            Result.success(
                LiteratureComparisonResult(
                    commonPoints = readFlexibleStringListByKeys(jsonObj, "common_points", "similarities", "same_points").take(5),
                    differences = readFlexibleStringListByKeys(jsonObj, "differences", "different_points").take(5),
                    complementarities = readFlexibleStringListByKeys(jsonObj, "complementarities", "complementary_points", "synergy").take(5),
                    projectFit = readFlexibleStringByKeys(jsonObj, "project_fit", "fit", "project_relevance")
                        ?: "需要结合当前项目目标进一步判断哪一篇更适合作为优先入口",
                    recommendation = readFlexibleStringByKeys(jsonObj, "recommendation", "entry_point", "next_step")
                        ?: "建议先从与你当前项目更贴近的一篇开始，再用另一篇补方法或背景。"
                ).withFallbacks(leftItem, rightItem)
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun parseLinkStructured(link: String): Result<LinkParseResult> = withContext(Dispatchers.IO) {
        parseLink(link).mapCatching { jsonStr ->
            val cleanJson = extractJsonFromResponse(jsonStr)
            val jsonObj = json.decodeFromString<JsonObject>(cleanJson)
            LinkParseResult(
                linkType = readFlexibleString(jsonObj, "link_type") ?: "unknown",
                id = readFlexibleString(jsonObj, "id"),
                title = readFlexibleString(jsonObj, "title"),
                originalUrl = link
            )
        }
    }
    
    /**
     * 识别图片并返回结构化结果
     */
    suspend fun recognizeImageStructured(bitmap: Bitmap): Result<OCRResult> = withContext(Dispatchers.IO) {
        recognizeImage(bitmap).mapCatching { jsonStr ->
            try {
                val cleanJson = extractJsonFromResponse(jsonStr)
                val jsonObj = json.decodeFromString<JsonObject>(cleanJson)
                OCRResult(
                    type = readFlexibleString(jsonObj, "type") ?: "text",
                    title = readFlexibleString(jsonObj, "title"),
                    authors = readFlexibleString(jsonObj, "authors"),
                    identifier = readFlexibleString(jsonObj, "identifier")
                        ?: readFlexibleString(jsonObj, "doi"),
                    identifierType = readFlexibleString(jsonObj, "identifier_type")
                        ?: readFlexibleString(jsonObj, "doi")?.let { "doi" },
                    doi = readFlexibleString(jsonObj, "doi"),
                    referencedLinks = readFlexibleStringList(jsonObj, "referenced_links"),
                    content = readFlexibleString(jsonObj, "content")
                        ?: jsonStr.takeIf { it.isNotBlank() }
                )
            } catch (_: Exception) {
                OCRResult(
                    type = "text",
                    title = null,
                    authors = null,
                    identifier = extractIdentifierFromText(jsonStr),
                    identifierType = null,
                    doi = extractDoiFromInput(jsonStr),
                    referencedLinks = extractCanonicalReferencesFromText(jsonStr),
                    content = jsonStr.takeIf { it.isNotBlank() }
                )
            }
        }
    }

    suspend fun organizeOcrBatch(
        rawText: String,
        pageHints: List<OCRResult> = emptyList()
    ): Result<FullLinkParseResult> = withContext(Dispatchers.IO) {
        try {
            val trimmedText = rawText.trim()
            if (trimmedText.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("OCR 原文为空"))
            }

            val hintTitle = pageHints.firstNotNullOfOrNull { it.title?.trim()?.takeIf(String::isNotBlank) }
            val hintAuthors = pageHints.firstNotNullOfOrNull { it.authors?.trim()?.takeIf(String::isNotBlank) }
            val hintIdentifier = pageHints.firstNotNullOfOrNull {
                it.identifier?.trim()?.takeIf(String::isNotBlank)
                    ?: it.doi?.trim()?.takeIf(String::isNotBlank)
            } ?: extractIdentifierFromText(trimmedText)

            val detectedReferences = extractCanonicalReferencesFromText(trimmedText)
            val hintLinks = (pageHints.flatMap { it.referencedLinks } + detectedReferences).distinct()

            data class OcrOrganizeAttempt(
                val model: String,
                val promptSizes: List<Int>
            )

            val attemptPlans = listOf(
                OcrOrganizeAttempt(
                    model = SiliconFlowApiService.MODEL_FAST_TEXT,
                    promptSizes = listOf(12000, 8000, 5000)
                ),
                // 大模型只做兜底，且避免再次走最长 prompt，降低整条链路超时概率。
                OcrOrganizeAttempt(
                    model = SiliconFlowApiService.MODEL_TEXT,
                    promptSizes = listOf(8000, 5000)
                )
            )
            var aiContent: String? = null
            var lastError: Exception? = null

            run breaking@{
                attemptPlans.forEach { plan ->
                    plan.promptSizes.forEachIndexed { index, maxPromptChars ->
                        try {
                            val prompt = buildString {
                                appendLine("OCR pages: ${pageHints.size.coerceAtLeast(1)}")
                                appendLine("Detected title hint: ${hintTitle ?: "null"}")
                                appendLine("Detected authors hint: ${hintAuthors ?: "null"}")
                                appendLine("Detected identifier hint: ${hintIdentifier ?: "null"}")
                                appendLine("Detected links: ${if (hintLinks.isEmpty()) "[]" else hintLinks.joinToString(", ")}")
                                appendLine()
                                appendLine("OCR text:")
                                appendLine(buildOcrPromptText(trimmedText, maxPromptChars))
                            }

                            val request = SimpleChatRequest(
                                model = plan.model,
                                messages = listOf(
                                    SimpleMessage(role = "system", content = OCR_BATCH_ORGANIZE_SYSTEM_PROMPT),
                                    SimpleMessage(role = "user", content = prompt)
                                ),
                                maxTokens = 1800,
                                temperature = 0.2
                            )

                            val response = siliconFlowApi.chatCompletion(AUTH_HEADER, request)
                            aiContent = response.choices.firstOrNull()?.message?.content
                            if (!aiContent.isNullOrBlank()) {
                                return@breaking
                            }
                            lastError = Exception("OCR organize returned empty content")
                        } catch (e: Exception) {
                            lastError = e
                            Log.w(
                                TAG,
                                "organizeOcrBatch model=${plan.model} attempt ${index + 1} failed, maxPromptChars=$maxPromptChars",
                                e
                            )
                        }
                    }
                }
            }

            if (aiContent.isNullOrBlank()) {
                Log.w(TAG, "organizeOcrBatch fallback activated: ${lastError?.message}")
                return@withContext Result.success(
                    createOcrFallbackResult(
                        rawText = trimmedText,
                        hintTitle = hintTitle,
                        hintAuthors = hintAuthors,
                        hintIdentifier = hintIdentifier,
                        detectedReferences = hintLinks
                    )
                )
            }

            val cleanJson = extractJsonFromResponse(aiContent)
            val jsonObj = json.decodeFromString<JsonObject>(cleanJson)
            val metaObj = jsonObj["meta"] as? JsonObject

            val title = readFlexibleString(jsonObj, "title")
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?: hintTitle
                ?: trimmedText.lineSequence()
                    .map { it.trim() }
                    .firstOrNull { it.length in 4..80 }
                ?: "图片扫描"

            val identifier = readFlexibleString(jsonObj, "identifier")
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?: hintIdentifier

            val summaryZh = readFlexibleString(jsonObj, "summary_zh")
            val summaryEn = readFlexibleString(jsonObj, "summary_en")
            val mediumSummary = readFlexibleString(jsonObj, "medium_summary")
                ?: readFlexibleString(jsonObj, "summary")
                ?: summaryZh
                ?: summaryEn
                ?: buildFallbackMediumSummary(trimmedText)
            val summary = mediumSummary
            val summaryShort = readFlexibleString(jsonObj, "summary_short")
                ?: summaryZh?.take(120)
                ?: mediumSummary.take(120)

            val tags = readFlexibleStringList(jsonObj, "tags")
            val domainTags = readFlexibleStringList(jsonObj, "domain_tags")
                .ifEmpty { inferDomainTags(title) }
            val keywords = readFlexibleStringList(jsonObj, "keywords")
                .ifEmpty { tags }
                .ifEmpty { inferKeywords(title) }
            val methodTags = readFlexibleStringList(jsonObj, "method_tags")
            val mergedReferences = (readFlexibleStringList(jsonObj, "referenced_links") + hintLinks)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
            val topicTags = readFlexibleStringList(jsonObj, "topic_tags")
                .ifEmpty { domainTags }
            val corePoints = readFlexibleStringList(jsonObj, "core_points")
            val paperCandidates = (
                readPaperCandidates(jsonObj, "paper_candidates") +
                    mergedReferences.mapNotNull(::classifyPaperCandidate) +
                    buildPaperCandidatesFromIdentifier(identifier)
                ).distinctBy { "${it.kind}:${it.url}" }

            val source = readFlexibleString(jsonObj, "source")
                ?.takeIf(String::isNotBlank)
                ?: inferSourceFromOcr(identifier, mergedReferences)
            val contentType = readFlexibleString(jsonObj, "content_type")
                ?.takeIf(String::isNotBlank)
                ?: inferContentTypeFromOcr(identifier, mergedReferences, trimmedText)
            val year = readFlexibleString(metaObj, "year")
                ?: inferYear(identifier, mergedReferences.firstOrNull() ?: title)
            val dedupKey = readFlexibleString(jsonObj, "dedup_key")
                ?: buildDedupKey(identifier, title, year)
            val mergedTags = (if (tags.isNotEmpty()) tags else keywords + domainTags + methodTags)
                .distinct()
                .take(10)
            val primaryUrl = mergedReferences.firstOrNull()
                ?: paperCandidates.firstOrNull()?.url
                ?: ""

            Result.success(
                FullLinkParseResult(
                    title = title,
                    authors = readFlexibleString(jsonObj, "authors") ?: hintAuthors,
                    summary = summary,
                    summaryShort = summaryShort,
                    mediumSummary = mediumSummary,
                    summaryEn = summaryEn,
                    summaryZh = summaryZh,
                    contentType = contentType,
                    source = source,
                    identifier = identifier,
                    tags = mergedTags,
                    originalUrl = primaryUrl,
                    conference = readFlexibleString(metaObj, "conference"),
                    year = year,
                    platform = readFlexibleString(metaObj, "platform"),
                    accountName = readFlexibleString(jsonObj, "account_name"),
                    articleAuthor = readFlexibleString(jsonObj, "author")
                        ?: readFlexibleString(jsonObj, "authors")
                        ?: hintAuthors,
                    publishDate = readFlexibleString(jsonObj, "publish_date"),
                    domainTags = domainTags,
                    keywords = keywords,
                    methodTags = methodTags,
                    topicTags = topicTags,
                    corePoints = corePoints,
                    referencedLinks = mergedReferences,
                    paperCandidates = paperCandidates,
                    dedupKey = dedupKey
                )
            )
        } catch (e: Exception) {
            Result.success(
                createOcrFallbackResult(
                    rawText = rawText,
                    hintTitle = pageHints.firstNotNullOfOrNull { it.title?.trim()?.takeIf(String::isNotBlank) },
                    hintAuthors = pageHints.firstNotNullOfOrNull { it.authors?.trim()?.takeIf(String::isNotBlank) },
                    hintIdentifier = pageHints.firstNotNullOfOrNull {
                        it.identifier?.trim()?.takeIf(String::isNotBlank)
                            ?: it.doi?.trim()?.takeIf(String::isNotBlank)
                    } ?: extractIdentifierFromText(rawText),
                    detectedReferences = extractCanonicalReferencesFromText(rawText)
                )
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
                title = readFlexibleString(jsonObj, "title"),
                authors = readFlexibleString(jsonObj, "authors"),
                doi = readFlexibleString(jsonObj, "doi"),
                summary = readFlexibleString(jsonObj, "summary")
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

    internal fun readFlexibleString(jsonObj: JsonObject?, key: String): String? {
        return readFlexibleString(jsonObj?.get(key))
    }

    internal fun readFlexibleString(element: JsonElement?): String? {
        return when (element) {
            null -> null
            is JsonArray -> element
                .mapNotNull(::readFlexibleString)
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinct()
                .joinToString(", ")
                .takeIf { it.isNotBlank() }
            is JsonObject -> {
                listOf("title", "name", "text", "label", "value", "content").firstNotNullOfOrNull { key ->
                    readFlexibleString(element[key])
                } ?: element.values
                    .mapNotNull(::readFlexibleString)
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .distinct()
                    .joinToString(", ")
                    .takeIf { it.isNotBlank() }
            }
            else -> element.jsonPrimitive.contentOrNull
                ?.trim()
                ?.takeIf(String::isNotBlank)
        }
    }

    private fun readFlexibleStringList(jsonObj: JsonObject?, key: String): List<String> {
        val element = jsonObj?.get(key) ?: return emptyList()
        return try {
            when (element) {
                is JsonArray -> element
                    .mapNotNull(::readFlexibleString)
                    .flatMap { value -> value.split(Regex("[,\\uFF0C\\n]+")) }
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                else -> readFlexibleString(element)
                    ?.split(Regex("[,\\uFF0C\\n]+"))
                    .orEmpty()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun readFlexibleStringByKeys(jsonObj: JsonObject?, vararg keys: String): String? {
        return keys.firstNotNullOfOrNull { key ->
            readFlexibleString(jsonObj, key)
        }
    }

    private fun readFlexibleStringListByKeys(jsonObj: JsonObject?, vararg keys: String): List<String> {
        val merged = keys.flatMap { key -> readFlexibleStringList(jsonObj, key) }
        return merged
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun buildProjectOverviewPrompt(overview: ProjectOverview): String {
        val contextSummary = overview.contextDocument?.summary?.takeIf { it.isNotBlank() } ?: "暂无研究背景文档"
        val contextKeywords = overview.contextDocument?.keywords.orEmpty().joinToString(", ").ifBlank { "无" }
        val description = overview.project.description?.takeIf { it.isNotBlank() } ?: "无"
        val stats = overview.stats

        fun buildItemLines(items: List<ResearchItem>, limit: Int): String {
            return items.take(limit).joinToString("\n") { item ->
                val summary = item.summary.takeIf { it.isNotBlank() }?.let { truncateForPrompt(it, 120) } ?: "无摘要"
                "- [${typeLabel(item.type)}] ${item.title.take(120)} | $summary"
            }.ifBlank { "- 无" }
        }

        return buildString {
            appendLine("请基于以下项目信息，生成项目级研究总结。")
            appendLine("项目名称: ${overview.project.name}")
            appendLine("项目描述: $description")
            appendLine()
            appendLine("研究背景摘要:")
            appendLine(truncateForPrompt(contextSummary, 700))
            appendLine("研究背景关键词: $contextKeywords")
            appendLine()
            appendLine("项目统计:")
            appendLine("- 总条目: ${stats.totalItems}")
            appendLine("- 论文: ${stats.paperCount}")
            appendLine("- 资料: ${stats.articleCount}")
            appendLine("- 灵感: ${stats.insightCount}")
            appendLine("- 重复关系: ${stats.duplicateRelationCount}")
            appendLine("- 资料关联论文: ${stats.articlePaperRelationCount}")
            appendLine()
            appendLine("最近新增条目:")
            appendLine(buildItemLines(overview.recentItems, 10))
            appendLine()
            appendLine("重点论文:")
            appendLine(buildItemLines(overview.keyPapers, 6))
            appendLine()
            appendLine("最近灵感:")
            appendLine(buildItemLines(overview.recentInsights, 8))
        }
    }

    private fun typeLabel(type: ItemType): String = when (type) {
        ItemType.PAPER -> "论文"
        ItemType.ARTICLE -> "资料"
        ItemType.COMPETITION -> "比赛"
        ItemType.INSIGHT -> "灵感"
        ItemType.VOICE -> "语音"
    }

    private fun buildInsightRecommendationPrompt(
        insight: ResearchItem,
        candidates: List<ResearchItem>,
        projectContextSummary: String?,
        projectContextKeywords: List<String>
    ): String {
        val insightBody = insight.contentMarkdown.takeIf { it.isNotBlank() } ?: insight.summary
        val contextSummary = projectContextSummary?.takeIf { it.isNotBlank() } ?: "暂无项目研究背景"
        val contextKeywordsText = projectContextKeywords.joinToString(", ").ifBlank { "无" }
        val candidateText = candidates.mapIndexed { index, candidate ->
            val summary = candidate.summary.takeIf { it.isNotBlank() }?.let { truncateForPrompt(it, 140) } ?: "无摘要"
            val projectName = candidate.projectName?.takeIf { it.isNotBlank() } ?: "未归属"
            "$index. [${typeLabel(candidate.type)}] ${candidate.title.take(120)} | 项目=$projectName | 摘要=$summary"
        }.joinToString("\n")

        return buildString {
            appendLine("请为下面这条灵感，从候选资料中挑出最值得反查的条目。")
            appendLine("灵感标题: ${insight.title}")
            appendLine("灵感项目: ${insight.projectName ?: "未归属"}")
            appendLine("灵感内容:")
            appendLine(truncateForPrompt(insightBody, 900))
            appendLine()
            appendLine("项目研究背景摘要:")
            appendLine(truncateForPrompt(contextSummary, 500))
            appendLine("项目研究背景关键词: $contextKeywordsText")
            appendLine()
            appendLine("候选条目列表:")
            appendLine(candidateText)
        }
    }

    private fun buildLiteratureComparisonPrompt(
        leftItem: ResearchItem,
        rightItem: ResearchItem,
        projectContextSummary: String?,
        projectContextKeywords: List<String>
    ): String {
        fun describeItem(item: ResearchItem): String {
            val summary = item.summary.takeIf { it.isNotBlank() }?.let { truncateForPrompt(it, 240) } ?: "无摘要"
            val content = item.contentMarkdown.takeIf { it.isNotBlank() }?.let { truncateForPrompt(it, 500) } ?: "无正文"
            return buildString {
                appendLine("类型: ${typeLabel(item.type)}")
                appendLine("标题: ${item.title}")
                appendLine("所属项目: ${item.projectName ?: "未归属"}")
                appendLine("摘要: $summary")
                appendLine("正文摘要: $content")
            }
        }

        val contextSummary = projectContextSummary?.takeIf { it.isNotBlank() } ?: "暂无项目研究背景"
        val contextKeywordsText = projectContextKeywords.joinToString(", ").ifBlank { "无" }

        return buildString {
            appendLine("请比较下面两条资料，并判断它们如何服务当前项目。")
            appendLine("项目研究背景摘要:")
            appendLine(truncateForPrompt(contextSummary, 500))
            appendLine("项目研究背景关键词: $contextKeywordsText")
            appendLine()
            appendLine("资料 A:")
            appendLine(describeItem(leftItem))
            appendLine()
            appendLine("资料 B:")
            appendLine(describeItem(rightItem))
        }
    }

    private fun truncateForPrompt(value: String, maxLength: Int): String {
        val normalized = value
            .replace(Regex("\\s+"), " ")
            .trim()
        return if (normalized.length <= maxLength) normalized else "${normalized.take(maxLength)}..."
    }

    private fun ProjectOverviewSummaryResult.withFallbacks(
        overview: ProjectOverview
    ): ProjectOverviewSummaryResult {
        val fallbackLiterature = overview.keyPapers.take(3).map { it.title }.ifEmpty {
            overview.recentItems.filter { it.type == ItemType.PAPER || it.type == ItemType.ARTICLE }
                .take(3)
                .map { it.title }
        }
        val fallbackInsights = overview.recentInsights.take(3).map { it.title }
        val fallbackProgress = overview.recentItems.take(3).map { "${typeLabel(it.type)}：${it.title}" }

        return copy(
            recentProgress = recentProgress.ifEmpty { fallbackProgress },
            keyLiterature = keyLiterature.ifEmpty { fallbackLiterature },
            insightFocus = insightFocus.ifEmpty {
                if (fallbackInsights.isNotEmpty()) fallbackInsights else listOf("当前灵感样本较少，建议补充本周研究想法记录")
            },
            pendingQuestions = pendingQuestions.ifEmpty {
                listOf("当前证据链仍需补充，建议围绕核心问题建立可验证假设")
            },
            nextActions = nextActions.ifEmpty {
                listOf("优先阅读重点论文并补齐结构化阅读卡", "基于现有灵感建立 1-2 条可验证实验路线")
            }
        )
    }

    private fun LiteratureComparisonResult.withFallbacks(
        leftItem: ResearchItem,
        rightItem: ResearchItem
    ): LiteratureComparisonResult {
        val fallbackCommon = commonPoints.ifEmpty {
            listOf("两条资料都与当前主题存在相关性，值得放在同一组阅读路径中比较。")
        }
        val fallbackDifferences = differences.ifEmpty {
            listOf("两条资料的切入角度或侧重点并不完全相同，建议结合正文进一步确认差异。")
        }
        val fallbackComplementarities = complementarities.ifEmpty {
            listOf("可以把一条作为主线，另一条作为补充背景、方法或证据来源。")
        }

        return copy(
            commonPoints = fallbackCommon,
            differences = fallbackDifferences,
            complementarities = fallbackComplementarities,
            projectFit = projectFit.ifBlank {
                "建议先判断 `${leftItem.title}` 和 `${rightItem.title}` 哪一条更贴近你当前项目目标。"
            },
            recommendation = recommendation.ifBlank {
                "优先阅读更贴近当前项目的一条，再用另一条补齐背景、方法或反例。"
            }
        )
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

    private fun extractIdentifierFromText(text: String): String? {
        extractArxivIdFromInput(text)?.let { return it }
        extractDoiFromInput(text)?.let { return it }

        val arxivMatch = Regex("""(?i)\barxiv[:\s]*([a-z\-]+(?:\.[a-z\-]+)?/\d{7}|\d{4}\.\d{4,5}(?:v\d+)?)\b""")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
        if (!arxivMatch.isNullOrBlank()) return arxivMatch

        return Regex("""(?i)\b10\.\d{4,9}/[-._;()/:a-z0-9]+\b""")
            .find(text)
            ?.value
    }

    private fun extractCanonicalReferencesFromText(text: String): List<String> {
        val collected = linkedSetOf<String>()
        extractReferencedLinks(text).forEach { collected += it }

        Regex("""(?i)\barxiv[:\s]*([a-z\-]+(?:\.[a-z\-]+)?/\d{7}|\d{4}\.\d{4,5}(?:v\d+)?)\b""")
            .findAll(text)
            .mapNotNull { it.groupValues.getOrNull(1) }
            .forEach { collected += "https://arxiv.org/abs/${it.trim()}" }

        Regex("""(?i)\b10\.\d{4,9}/[-._;()/:a-z0-9]+\b""")
            .findAll(text)
            .map { it.value.trim().trimEnd('.', ',', ';') }
            .forEach { collected += "https://doi.org/$it" }

        return collected.toList()
    }

    private fun buildPaperCandidatesFromIdentifier(identifier: String?): List<ArticlePaperCandidate> {
        val normalized = identifier?.trim()?.takeIf(String::isNotBlank) ?: return emptyList()
        return when {
            extractArxivIdFromInput(normalized) != null -> {
                listOf(
                    ArticlePaperCandidate(
                        url = "https://arxiv.org/abs/${extractArxivIdFromInput(normalized)}",
                        label = normalized,
                        kind = "arxiv"
                    )
                )
            }
            extractDoiFromInput(normalized) != null -> {
                listOf(
                    ArticlePaperCandidate(
                        url = "https://doi.org/${extractDoiFromInput(normalized)}",
                        label = normalized,
                        kind = "doi"
                    )
                )
            }
            else -> emptyList()
        }
    }

    private fun inferSourceFromOcr(identifier: String?, referencedLinks: List<String>): String {
        val primary = referencedLinks.firstOrNull()
        if (!primary.isNullOrBlank()) {
            return guessSourceFromUrl(primary)
        }

        return when {
            identifier == null -> "ocr"
            extractArxivIdFromInput(identifier) != null -> "arxiv"
            extractDoiFromInput(identifier) != null -> "doi"
            else -> "ocr"
        }
    }

    private fun inferContentTypeFromOcr(
        identifier: String?,
        referencedLinks: List<String>,
        rawText: String
    ): String {
        if (!identifier.isNullOrBlank()) return "paper"

        val lowerText = rawText.lowercase()
        val articleLikeLink = referencedLinks.any {
            val lower = it.lowercase()
            lower.contains("mp.weixin.qq.com") ||
                lower.contains("weixin.qq.com") ||
                lower.contains("zhihu.com") ||
                lower.contains("medium.com") ||
                lower.contains("substack.com")
        }
        if (articleLikeLink) return "article"

        return when {
            lowerText.contains("kaggle") || lowerText.contains("报名") || lowerText.contains("截止") -> "competition"
            lowerText.contains("abstract") || lowerText.contains("introduction") || lowerText.contains("references") -> "paper"
            lowerText.contains("公众号") || lowerText.contains("微信") || lowerText.contains("作者") -> "article"
            else -> "insight"
        }
    }

    private fun buildOcrPromptText(rawText: String, maxLength: Int = 12000): String {
        val normalized = rawText.trim()
        if (normalized.length <= maxLength) return normalized

        val windowSize = (maxLength / 3).coerceAtLeast(1200)
        val head = normalized.take(windowSize)
        val middleStart = (normalized.length / 2 - windowSize / 2).coerceAtLeast(0)
        val middle = normalized.substring(middleStart, (middleStart + windowSize).coerceAtMost(normalized.length))
        val tail = normalized.takeLast(windowSize)

        return buildString {
            appendLine("[OCR total length: ${normalized.length}]")
            appendLine("[Because the OCR text is long, the context below keeps the beginning, middle, and ending sections.]")
            appendLine()
            appendLine("=== OCR BEGINNING ===")
            appendLine(head)
            appendLine()
            appendLine("=== OCR MIDDLE ===")
            appendLine(middle)
            appendLine()
            appendLine("=== OCR ENDING ===")
            appendLine(tail)
        }.trim()
    }

    private fun buildFallbackMediumSummary(rawText: String): String {
        val normalized = rawText
            .replace(Regex("\\s+"), " ")
            .trim()

        if (normalized.length <= 420) return normalized.ifBlank { "已完成 OCR 扫描" }

        val head = normalized.take(180)
        val middleStart = (normalized.length / 2 - 90).coerceAtLeast(0)
        val middle = normalized.substring(middleStart, (middleStart + 180).coerceAtMost(normalized.length))
        val tail = normalized.takeLast(180)

        return listOf(head, middle, tail)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString(" ... ")
    }

    private fun createOcrFallbackResult(
        rawText: String,
        hintTitle: String?,
        hintAuthors: String?,
        hintIdentifier: String?,
        detectedReferences: List<String>
    ): FullLinkParseResult {
        val title = hintTitle
            ?: rawText.lineSequence().map { it.trim() }.firstOrNull { it.length in 4..80 }
            ?: "图片扫描"
        val identifier = hintIdentifier ?: extractIdentifierFromText(rawText)
        val references = (detectedReferences + buildPaperCandidatesFromIdentifier(identifier).map { it.url })
            .distinct()
        val contentType = inferContentTypeFromOcr(identifier, references, rawText)
        val source = inferSourceFromOcr(identifier, references)
        val keywords = inferKeywords(title)
        val domainTags = inferDomainTags(title)
        val paperCandidates = (
            references.mapNotNull(::classifyPaperCandidate) + buildPaperCandidatesFromIdentifier(identifier)
            ).distinctBy { "${it.kind}:${it.url}" }
        val year = inferYear(identifier, references.firstOrNull() ?: title)
        val summary = rawText.take(180).ifBlank { "已完成 OCR 扫描" }

        val mediumSummary = buildFallbackMediumSummary(rawText)

        return FullLinkParseResult(
            title = title,
            authors = hintAuthors,
            summary = mediumSummary,
            summaryShort = summary.take(120),
            mediumSummary = mediumSummary,
            summaryEn = null,
            summaryZh = summary.take(120),
            contentType = contentType,
            source = source,
            identifier = identifier,
            tags = (keywords + domainTags).distinct().take(8),
            originalUrl = references.firstOrNull().orEmpty(),
            conference = null,
            year = year,
            platform = null,
            accountName = null,
            articleAuthor = hintAuthors,
            publishDate = null,
            domainTags = domainTags,
            keywords = keywords,
            methodTags = emptyList(),
            topicTags = domainTags,
            corePoints = emptyList(),
            referencedLinks = references,
            paperCandidates = paperCandidates,
            dedupKey = buildDedupKey(identifier, title, year)
        )
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
        val element = jsonObj?.get(key) as? JsonArray ?: return emptyList()
        return element.mapNotNull { entry ->
            when (entry) {
                is JsonObject -> {
                    val url = readFlexibleString(entry, "url") ?: return@mapNotNull null
                    ArticlePaperCandidate(
                        url = url,
                        label = readFlexibleString(entry, "label"),
                        kind = readFlexibleString(entry, "kind") ?: "unknown"
                    )
                }
                else -> {
                    val url = readFlexibleString(entry) ?: return@mapNotNull null
                    classifyPaperCandidate(url)
                }
            }
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

            val completedConference = readFlexibleString(jsonObj, "conference")
            val completedYear = readFlexibleString(jsonObj, "year")
            val completedDomainTags = readFlexibleStringList(jsonObj, "domain_tags")
            val completedKeywords = readFlexibleStringList(jsonObj, "keywords")
            val completedMethodTags = readFlexibleStringList(jsonObj, "method_tags")
            val completedDedupKey = readFlexibleString(jsonObj, "dedup_key")
            val completedSummaryShort = readFlexibleString(jsonObj, "summary_short")
            val completedSummaryZh = readFlexibleString(jsonObj, "summary_zh")
            val completedSummaryEn = readFlexibleString(jsonObj, "summary_en")

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
            val normalizedLink = normalizeLinkInput(link)
            android.util.Log.d("AIService", "========== 开始解析链接 ==========")
            android.util.Log.d("AIService", "链接: $link")
            if (normalizedLink != link) {
                android.util.Log.d("AIService", "已规范化输入: $normalizedLink")
            }
            
            // Step 1: 抓取网页真实内容
            android.util.Log.d("AIService", "Step 1: 抓取网页内容...")
            val webContentResult = webContentFetcher.fetchContent(normalizedLink)
            
            val webContent = webContentResult.getOrNull()
            if (webContent != null) {
                android.util.Log.d("AIService", "抓取成功: title=${webContent.title.take(50)}, source=${webContent.source}")
                android.util.Log.d("AIService", "内容长度: ${webContent.content.length} 字符")
                
                // Step 2: 用 AI 对抓取的内容进行结构化解析
                android.util.Log.d("AIService", "Step 2: AI 结构化解析...")
                return@withContext parseContentWithAI(webContent, normalizedLink)
            } else {
                // 抓取失败，降级为仅解析 URL
                android.util.Log.w("AIService", "抓取失败，降级为 URL 解析: ${webContentResult.exceptionOrNull()?.message}")
                return@withContext parseUrlOnly(normalizedLink).map { result ->
                    result.copy(
                        feedbackMessage = webContentResult.exceptionOrNull()?.message
                            ?: "未抓到网页正文，已切换为快速链接解析"
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AIService", "parseFullLink 异常: ${e.message}", e)
            // 返回一个基础结果，避免完全失败
            Result.success(
                createFallbackResult(link).copy(
                    feedbackMessage = "链接解析异常，已返回基础结果"
                )
            )
        }
    }

    fun createQuickLinkDraft(link: String): FullLinkParseResult {
        val normalizedLink = normalizeLinkInput(link)
        return createFallbackResult(normalizedLink).copy(
            feedbackMessage = "链接已加入队列，正在后台解析"
        )
    }

    fun normalizeLinkInput(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return trimmed
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            return trimmed
        }

        extractArxivIdFromInput(trimmed)?.let { return "https://arxiv.org/abs/$it" }
        extractDoiFromInput(trimmed)?.let { return "https://doi.org/$it" }

        return trimmed
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
                val tagsArray = readFlexibleStringList(jsonObj, "tags")
                val methodTags = readFlexibleStringList(jsonObj, "method_tags")
                
                // 提取meta对象
                val metaObj = jsonObj["meta"]?.let { element ->
                    try {
                        element as? JsonObject
                    } catch (e: Exception) { null }
                }
                
                // 提取双语摘要
                val summaryEn = readFlexibleString(jsonObj, "summary_en")
                val summaryZh = readFlexibleString(jsonObj, "summary_zh")
                // 兼容旧格式的单语摘要
                val summary = readFlexibleString(jsonObj, "summary")
                    ?: summaryZh ?: summaryEn ?: webContent.abstract ?: "已抓取内容，待总结"
                val summaryShort = readFlexibleString(jsonObj, "summary_short")
                    ?: summary.take(160)
                
                val organizer = readFlexibleString(jsonObj, "organizer")
                    ?: readFlexibleString(metaObj, "organizer")
                val prizePool = readFlexibleString(jsonObj, "prize_pool")
                    ?: readFlexibleString(jsonObj, "prizePool")
                    ?: readFlexibleString(metaObj, "prizePool")
                val theme = readFlexibleString(jsonObj, "theme")
                    ?: readFlexibleString(metaObj, "theme")
                val competitionType = readFlexibleString(jsonObj, "competition_type")
                    ?: readFlexibleString(jsonObj, "competitionType")
                    ?: readFlexibleString(metaObj, "competitionType")
                val website = readFlexibleString(jsonObj, "website")
                    ?: readFlexibleString(metaObj, "website")
                val registrationUrl = readFlexibleString(jsonObj, "registration_url")
                    ?: readFlexibleString(jsonObj, "registrationUrl")
                    ?: readFlexibleString(metaObj, "registrationUrl")
                val timeline = parseCompetitionTimeline(jsonObj, metaObj)

                val title = readFlexibleString(jsonObj, "title")
                    ?: webContent.title.ifEmpty { "未命名链接" }
                val identifier = readFlexibleString(jsonObj, "identifier")
                    ?: extractIdentifierFromUrl(originalUrl)
                val year = readFlexibleString(metaObj, "year")
                    ?: inferYear(identifier, originalUrl)
                val domainTags = readFlexibleStringList(jsonObj, "domain_tags")
                    .ifEmpty { inferDomainTags(title) }
                val keywords = readFlexibleStringList(jsonObj, "keywords")
                    .ifEmpty { tagsArray }
                    .ifEmpty { inferKeywords(title) }
                val dedupKey = readFlexibleString(jsonObj, "dedup_key")
                    ?: buildDedupKey(identifier, title, year)
                val mergedTags = (if (tagsArray.isNotEmpty()) tagsArray else keywords + domainTags + methodTags)
                    .distinct()
                    .take(8)
                val referencedLinks = readFlexibleStringList(jsonObj, "referenced_links")
                    .ifEmpty { extractReferencedLinks(webContent.content) }
                val topicTags = readFlexibleStringList(jsonObj, "topic_tags")
                    .ifEmpty { domainTags }
                val corePoints = readFlexibleStringList(jsonObj, "core_points")
                val paperCandidates = readPaperCandidates(jsonObj, "paper_candidates")
                    .ifEmpty { referencedLinks.mapNotNull(::classifyPaperCandidate) }

                val draft = FullLinkParseResult(
                    title = title,
                    authors = readFlexibleString(jsonObj, "authors")
                        ?: webContent.authors,
                    summary = summary,
                    summaryShort = summaryShort,
                    summaryEn = summaryEn,
                    summaryZh = summaryZh,
                    contentType = readFlexibleString(jsonObj, "content_type")
                        ?: guessContentTypeFromUrl(originalUrl),
                    source = readFlexibleString(jsonObj, "source")
                        ?: webContent.source,
                    identifier = identifier,
                    tags = mergedTags,
                    originalUrl = originalUrl,
                    conference = readFlexibleString(metaObj, "conference"),
                    year = year,
                    platform = readFlexibleString(metaObj, "platform"),
                    accountName = readFlexibleString(jsonObj, "account_name"),
                    articleAuthor = readFlexibleString(jsonObj, "author")
                        ?: readFlexibleString(jsonObj, "authors"),
                    publishDate = readFlexibleString(jsonObj, "publish_date"),
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
        val array = element as? JsonArray ?: return null
        val nodes = array.mapNotNull { entry ->
            val obj = entry as? JsonObject ?: return@mapNotNull null
            val name = readFlexibleString(obj, "name") ?: return@mapNotNull null
            val date = readFlexibleString(obj, "date") ?: return@mapNotNull null
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
                
                val tagsArray = readFlexibleStringList(jsonObj, "tags")
                val methodTags = readFlexibleStringList(jsonObj, "method_tags")
                
                val metaObj = jsonObj["meta"]?.let { element ->
                    try { element as? JsonObject } catch (e: Exception) { null }
                }
                
                val organizer = readFlexibleString(jsonObj, "organizer")
                    ?: readFlexibleString(metaObj, "organizer")
                val prizePool = readFlexibleString(jsonObj, "prize_pool")
                    ?: readFlexibleString(jsonObj, "prizePool")
                    ?: readFlexibleString(metaObj, "prizePool")
                val theme = readFlexibleString(jsonObj, "theme")
                    ?: readFlexibleString(metaObj, "theme")
                val competitionType = readFlexibleString(jsonObj, "competition_type")
                    ?: readFlexibleString(jsonObj, "competitionType")
                    ?: readFlexibleString(metaObj, "competitionType")
                val website = readFlexibleString(jsonObj, "website")
                    ?: readFlexibleString(metaObj, "website")
                val registrationUrl = readFlexibleString(jsonObj, "registration_url")
                    ?: readFlexibleString(jsonObj, "registrationUrl")
                    ?: readFlexibleString(metaObj, "registrationUrl")
                val timeline = parseCompetitionTimeline(jsonObj, metaObj)
                
                val title = jsonObj["title"]?.jsonPrimitive?.content ?: "未命名链接"
                val identifier = readFlexibleString(jsonObj, "identifier") ?: extractIdentifierFromUrl(link)
                val year = readFlexibleString(metaObj, "year")
                    ?: inferYear(identifier, link)
                val domainTags = readFlexibleStringList(jsonObj, "domain_tags")
                    .ifEmpty { inferDomainTags(title) }
                val keywords = readFlexibleStringList(jsonObj, "keywords")
                    .ifEmpty { tagsArray }
                    .ifEmpty { inferKeywords(title) }
                val dedupKey = readFlexibleString(jsonObj, "dedup_key")
                    ?: buildDedupKey(identifier, title, year)
                val mergedTags = (if (tagsArray.isNotEmpty()) tagsArray else keywords + domainTags + methodTags)
                    .distinct()
                    .take(8)
                val referencedLinks = readFlexibleStringList(jsonObj, "referenced_links")
                val topicTags = readFlexibleStringList(jsonObj, "topic_tags")
                    .ifEmpty { domainTags }
                val corePoints = readFlexibleStringList(jsonObj, "core_points")
                val paperCandidates = readPaperCandidates(jsonObj, "paper_candidates")
                    .ifEmpty { referencedLinks.mapNotNull(::classifyPaperCandidate) }

                return Result.success(FullLinkParseResult(
                    title = title,
                    authors = readFlexibleString(jsonObj, "authors"),
                    summary = jsonObj["summary"]?.jsonPrimitive?.content ?: "链接已保存",
                    summaryShort = readFlexibleString(jsonObj, "summary_short"),
                    summaryEn = readFlexibleString(jsonObj, "summary_en"),
                    summaryZh = readFlexibleString(jsonObj, "summary_zh"),
                    contentType = readFlexibleString(jsonObj, "content_type") ?: "insight",
                    source = readFlexibleString(jsonObj, "source") ?: "web",
                    identifier = identifier,
                    tags = mergedTags,
                    originalUrl = link,
                    conference = readFlexibleString(metaObj, "conference"),
                    year = year,
                    platform = readFlexibleString(metaObj, "platform"),
                    accountName = readFlexibleString(jsonObj, "account_name"),
                    articleAuthor = readFlexibleString(jsonObj, "author")
                        ?: readFlexibleString(jsonObj, "authors"),
                    publishDate = readFlexibleString(jsonObj, "publish_date"),
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
                    timeline = timeline,
                    feedbackMessage = "未抓到网页正文，已根据链接信息生成结果"
                ))
            }
            return Result.success(
                createFallbackResult(link).copy(
                    feedbackMessage = "未抓到网页正文，已返回基础链接卡片"
                )
            )
        } catch (e: Exception) {
            return Result.success(
                createFallbackResult(link).copy(
                    feedbackMessage = "链接抓取失败，已返回基础链接卡片"
                )
            )
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
            extractArxivIdFromInput(lowerUrl) != null -> "paper"
            extractDoiFromInput(lowerUrl) != null -> "paper"
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
            extractArxivIdFromInput(lowerUrl) != null -> "arxiv"
            extractDoiFromInput(lowerUrl) != null -> "doi"
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
        extractArxivIdFromInput(url)?.let { return it }
        extractDoiFromInput(url)?.let { return it }

        // arXiv ID 模式: arxiv.org/abs/xxxx.xxxxx
        val arxivRegex = Regex("arxiv\\.org/abs/(\\d+\\.\\d+)")
        arxivRegex.find(url)?.groupValues?.getOrNull(1)?.let { return it }
        
        // DOI 模式
        val doiRegex = Regex("doi\\.org/(10\\.\\d+/[^\\s]+)")
        doiRegex.find(url)?.groupValues?.getOrNull(1)?.let { return it }
        
        return null
    }

    private fun extractArxivIdFromInput(input: String): String? {
        val normalized = input.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("arxiv.org/abs/")
            .removePrefix("arxiv.org/pdf/")

        val patterns = listOf(
            Regex("""(?i)^(?:arxiv:)?(\d{4}\.\d{4,5}(?:v\d+)?)$"""),
            Regex("""(?i)^(?:arxiv:)?([a-z\-]+(?:\.[a-z\-]+)?/\d{7}(?:v\d+)?)$""")
        )

        return patterns.firstNotNullOfOrNull { pattern ->
            pattern.find(normalized)?.groupValues?.getOrNull(1)
        }
    }

    private fun extractDoiFromInput(input: String): String? {
        val normalized = input.trim().trimEnd('/', '.', '，', ',', ';', '；')
        val patterns = listOf(
            Regex("""(?i)^(10\.\d{4,9}/\S+)$"""),
            Regex("""(?i)^(?:https?://)?(?:dx\.)?doi\.org/(10\.\d{4,9}/\S+)$""")
        )

        return patterns.firstNotNullOfOrNull { pattern ->
            pattern.find(normalized)?.groupValues?.getOrNull(1)
        }
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
    val identifier: String? = null,
    val identifierType: String? = null,
    val doi: String?,
    val referencedLinks: List<String> = emptyList(),
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

data class StructuredReadingCardResult(
    val card: StructuredReadingCard,
    val rawJson: String? = null
)

private data class OcrUploadProfile(
    val maxEdge: Int,
    val jpegQuality: Int
)

data class ProjectContextSummaryResult(
    val title: String?,
    val summary: String,
    val keywords: List<String>
)

data class ProjectOverviewSummaryResult(
    val currentTheme: String,
    val recentProgress: List<String>,
    val keyLiterature: List<String>,
    val insightFocus: List<String>,
    val pendingQuestions: List<String>,
    val nextActions: List<String>
)

data class InsightRecommendationResult(
    val recommendations: List<InsightRecommendation>
)

data class InsightRecommendation(
    val candidateIndex: Int,
    val reason: String,
    val suggestedQuestion: String? = null
)

data class LiteratureComparisonResult(
    val commonPoints: List<String>,
    val differences: List<String>,
    val complementarities: List<String>,
    val projectFit: String,
    val recommendation: String
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
    val mediumSummary: String? = null,
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
    val timeline: List<CompetitionTimelineNode>? = null,
    val feedbackMessage: String? = null
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
            "article" -> com.example.ai4research.domain.model.ItemType.ARTICLE
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
            mediumSummary?.let { metaMap["medium_summary"] = it }
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
