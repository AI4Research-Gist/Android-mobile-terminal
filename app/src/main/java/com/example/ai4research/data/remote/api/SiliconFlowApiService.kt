package com.example.ai4research.data.remote.api

import com.example.ai4research.data.remote.dto.ChatCompletionResponse
import com.example.ai4research.data.remote.dto.SimpleChatRequest
import com.example.ai4research.data.remote.dto.VLChatRequest
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * SiliconFlow API 接口
 * 硅基流动大模型API服务
 */
interface SiliconFlowApiService {

    companion object {
        const val BASE_URL = "https://api.siliconflow.cn/v1/"
        
        // 模型名称
        const val MODEL_TEXT = "Qwen/Qwen2.5-14B-Instruct"  // 文本总结
        const val MODEL_VISION = "Qwen/Qwen2.5-VL-32B-Instruct"  // 图像识别
    }

    /**
     * 文本对话补全
     * 用于文本总结、链接解析等
     */
    @POST("chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: SimpleChatRequest
    ): ChatCompletionResponse

    /**
     * 视觉语言对话补全
     * 用于截图OCR识别
     */
    @POST("chat/completions")
    suspend fun visionChatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: VLChatRequest
    ): ChatCompletionResponse
}
