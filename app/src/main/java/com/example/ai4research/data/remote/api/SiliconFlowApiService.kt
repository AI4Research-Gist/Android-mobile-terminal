package com.example.ai4research.data.remote.api

import com.example.ai4research.BuildConfig
import com.example.ai4research.data.remote.dto.ChatCompletionResponse
import com.example.ai4research.data.remote.dto.SimpleChatRequest
import com.example.ai4research.data.remote.dto.TranscriptionResponse
import com.example.ai4research.data.remote.dto.VLChatRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface SiliconFlowApiService {

    companion object {
        val BASE_URL: String = BuildConfig.SILICONFLOW_BASE_URL

        const val MODEL_TEXT = "Qwen/Qwen3.5-397B-A17B"
        const val MODEL_FAST_TEXT = "Qwen/Qwen3-8B"
        const val MODEL_VISION = "Qwen/Qwen3.5-397B-A17B"
        const val MODEL_ASR = "FunAudioLLM/SenseVoiceSmall"
    }

    @POST("chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: SimpleChatRequest
    ): ChatCompletionResponse

    @POST("chat/completions")
    suspend fun visionChatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: VLChatRequest
    ): ChatCompletionResponse

    @Multipart
    @POST("audio/transcriptions")
    suspend fun audioTranscription(
        @Header("Authorization") authorization: String,
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody
    ): TranscriptionResponse
}
