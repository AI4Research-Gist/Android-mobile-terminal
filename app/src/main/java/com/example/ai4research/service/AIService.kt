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
    private val json: Json
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
