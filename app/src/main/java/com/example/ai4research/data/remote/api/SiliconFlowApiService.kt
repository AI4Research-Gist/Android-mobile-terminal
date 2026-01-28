package com.example.ai4research.data.remote.api

import com.example.ai4research.data.remote.dto.ChatCompletionResponse
import com.example.ai4research.data.remote.dto.SimpleChatRequest
import com.example.ai4research.data.remote.dto.VLChatRequest
import com.example.ai4research.data.remote.dto.TranscriptionResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

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
        const val MODEL_ASR = "FunAudioLLM/SenseVoiceSmall"  // 语音识别
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

    /**
     * 音频转文字
     * 使用Whisper/SenseVoice模型进行语音转文字
     */
    @Multipart
    @POST("audio/transcriptions")
    suspend fun audioTranscription(
        @Header("Authorization") authorization: String,
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody
    ): TranscriptionResponse
}
