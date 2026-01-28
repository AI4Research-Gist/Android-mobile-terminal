package com.example.ai4research.service

/**
 * 语音录制状态
 */
sealed class VoiceRecordingState {
    /**
     * 空闲状态 - 等待用户开始录音
     */
    object Idle : VoiceRecordingState()
    
    /**
     * 录音中
     * @param durationSeconds 当前录音时长（秒）
     * @param amplitude 当前音频振幅（0-1）用于波形显示
     */
    data class Recording(
        val durationSeconds: Int = 0,
        val amplitude: Float = 0f
    ) : VoiceRecordingState()
    
    /**
     * 处理中 - 正在进行语音识别和AI优化
     * @param stage 当前处理阶段描述
     */
    data class Processing(
        val stage: String = "正在识别语音..."
    ) : VoiceRecordingState()
    
    /**
     * 完成 - 语音识别完成，可预览和编辑
     * @param transcription 转写的文本内容
     * @param durationSeconds 录音总时长
     * @param audioFilePath 音频文件路径
     */
    data class Completed(
        val transcription: String,
        val durationSeconds: Int,
        val audioFilePath: String
    ) : VoiceRecordingState()
    
    /**
     * 错误状态
     * @param message 错误信息
     */
    data class Error(
        val message: String
    ) : VoiceRecordingState()
}

/**
 * 语音识别结果
 */
data class VoiceTranscriptionResult(
    val rawText: String,           // 原始识别文本
    val enhancedText: String?,     // AI优化后的文本
    val confidence: Float = 0f     // 识别置信度
)
