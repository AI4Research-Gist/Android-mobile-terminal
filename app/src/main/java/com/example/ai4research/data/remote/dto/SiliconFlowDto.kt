package com.example.ai4research.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * SiliconFlow API 数据类
 * 兼容 OpenAI 格式
 */

// ==================== 请求 ====================

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false,
    @SerialName("max_tokens")
    val maxTokens: Int = 2048,
    val temperature: Double = 0.7,
    @SerialName("top_p")
    val topP: Double = 0.7
)

@Serializable
data class ChatMessage(
    val role: String,  // "system", "user", "assistant"
    val content: MessageContent
)

/**
 * 消息内容 - 支持文本和图片
 */
@Serializable
sealed class MessageContent {
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : MessageContent()
    
    @Serializable
    @SerialName("multimodal")
    data class Multimodal(val parts: List<ContentPart>) : MessageContent()
}

@Serializable
sealed class ContentPart {
    @Serializable
    @SerialName("text")
    data class TextPart(val text: String) : ContentPart()
    
    @Serializable
    @SerialName("image_url")
    data class ImagePart(
        @SerialName("image_url")
        val imageUrl: ImageUrl
    ) : ContentPart()
}

@Serializable
data class ImageUrl(
    val url: String  // base64 data URL or http URL
)

// ==================== 响应 ====================

@Serializable
data class ChatCompletionResponse(
    val id: String,
    @SerialName("object")
    val objectType: String = "chat.completion",
    val created: Long,
    val model: String,
    val choices: List<Choice>,
    val usage: Usage? = null
)

@Serializable
data class Choice(
    val index: Int,
    val message: AssistantMessage,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
data class AssistantMessage(
    val role: String = "assistant",
    val content: String? = null,
    @SerialName("reasoning_content")
    val reasoningContent: String? = null
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens")
    val promptTokens: Int,
    @SerialName("completion_tokens")
    val completionTokens: Int,
    @SerialName("total_tokens")
    val totalTokens: Int
)

// ==================== 简化版请求（用于 Retrofit）====================

@Serializable
data class SimpleChatRequest(
    val model: String,
    val messages: List<SimpleMessage>,
    val stream: Boolean = false,
    @SerialName("max_tokens")
    val maxTokens: Int = 2048,
    val temperature: Double = 0.7
)

@Serializable
data class SimpleMessage(
    val role: String,
    val content: String  // 纯文本内容
)

@Serializable
data class VLChatRequest(
    val model: String,
    val messages: List<VLMessage>,
    val stream: Boolean = false,
    @SerialName("max_tokens")
    val maxTokens: Int = 2048
)

@Serializable
data class VLMessage(
    val role: String,
    val content: List<VLContent>
)

@Serializable
data class VLContent(
    val type: String,  // "text" or "image_url"
    val text: String? = null,
    @SerialName("image_url")
    val imageUrl: VLImageUrl? = null
)

@Serializable
data class VLImageUrl(
    val url: String
)
