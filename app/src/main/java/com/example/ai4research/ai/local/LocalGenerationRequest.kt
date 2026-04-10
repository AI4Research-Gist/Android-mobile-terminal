package com.example.ai4research.ai.local

data class LocalChatMessage(
    val role: String,
    val content: String
)

data class LocalGenerationRequest(
    val messages: List<LocalChatMessage>,
    val maxTokens: Int = 256,
    val temperature: Float = 0.3f
)
