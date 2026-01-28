package com.example.ai4research.ui.voice

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai4research.domain.repository.ItemRepository
import com.example.ai4research.service.AIService
import com.example.ai4research.service.AudioRecorderHelper
import com.example.ai4research.service.VoiceRecordingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 语音录制ViewModel
 * 管理录音状态、语音识别和保存流程
 * 
 * 流程：录音 -> 上传音频文件到SiliconFlow ASR API -> AI优化文本 -> 用户编辑 -> 保存
 */
@HiltViewModel
class VoiceRecordingViewModel @Inject constructor(
    application: Application,
    private val aiService: AIService,
    private val itemRepository: ItemRepository
) : AndroidViewModel(application) {
    
    companion object {
        private const val TAG = "VoiceRecordingVM"
    }
    
    private val context = application.applicationContext
    
    // 录音帮助类
    private val audioRecorder = AudioRecorderHelper(context)
    
    // 录音状态
    private val _recordingState = MutableStateFlow<VoiceRecordingState>(VoiceRecordingState.Idle)
    val recordingState: StateFlow<VoiceRecordingState> = _recordingState.asStateFlow()
    
    // 可编辑的转写文本
    private val _editableText = MutableStateFlow("")
    val editableText: StateFlow<String> = _editableText.asStateFlow()
    
    // 权限状态
    private val _hasRecordPermission = MutableStateFlow(checkRecordPermission())
    val hasRecordPermission: StateFlow<Boolean> = _hasRecordPermission.asStateFlow()
    
    // 保存成功后的item ID
    private val _savedItemId = MutableStateFlow<String?>(null)
    val savedItemId: StateFlow<String?> = _savedItemId.asStateFlow()
    
    // 定时更新状态的Job
    private var updateJob: Job? = null
    
    // 当前录音文件路径
    private var currentAudioFilePath: String? = null
    
    /**
     * 检查录音权限
     */
    private fun checkRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 更新权限状态
     */
    fun updatePermissionStatus() {
        _hasRecordPermission.value = checkRecordPermission()
    }
    
    /**
     * 开始录音
     */
    fun startRecording() {
        if (!checkRecordPermission()) {
            _recordingState.value = VoiceRecordingState.Error("请先授予录音权限")
            return
        }
        
        // 开始音频录制
        val filePath = audioRecorder.startRecording()
        if (filePath == null) {
            _recordingState.value = VoiceRecordingState.Error("无法启动录音")
            return
        }
        
        currentAudioFilePath = filePath
        
        // 更新状态
        _recordingState.value = VoiceRecordingState.Recording(0, 0f)
        
        // 启动定时更新
        startUpdateTimer()
        
        Log.d(TAG, "Recording started: $filePath")
    }
    
    /**
     * 停止录音并开始转写
     */
    fun stopRecording() {
        // 停止定时更新
        updateJob?.cancel()
        updateJob = null
        
        // 停止音频录制
        val result = audioRecorder.stopRecording()
        
        if (result != null) {
            val (filePath, duration) = result
            Log.d(TAG, "Recording stopped: ${duration}s, path: $filePath")
            
            // 开始转写流程
            startTranscription(filePath, duration)
        } else {
            _recordingState.value = VoiceRecordingState.Error("录音保存失败")
        }
    }
    
    /**
     * 开始音频转写流程
     */
    private fun startTranscription(filePath: String, duration: Int) {
        _recordingState.value = VoiceRecordingState.Processing("正在转写语音...")
        
        viewModelScope.launch {
            try {
                // 1. 调用ASR API进行语音转文字
                val transcribeResult = aiService.transcribeAudio(filePath)
                
                val rawText = transcribeResult.getOrElse { e ->
                    Log.w(TAG, "ASR转写失败: ${e.message}")
                    // ASR失败时显示空文本，用户可手动输入
                    ""
                }
                
                Log.d(TAG, "ASR转写结果: $rawText")
                
                // 2. 如果有转写结果，使用AI优化
                val enhancedText = if (rawText.isNotBlank()) {
                    _recordingState.value = VoiceRecordingState.Processing("正在优化文本...")
                    val enhanceResult = aiService.enhanceTranscription(rawText)
                    enhanceResult.getOrElse { rawText }
                } else {
                    ""
                }
                
                _editableText.value = enhancedText
                _recordingState.value = VoiceRecordingState.Completed(
                    transcription = enhancedText,
                    durationSeconds = duration,
                    audioFilePath = filePath
                )
                
                Log.d(TAG, "转写完成: duration=${duration}s, text=${enhancedText.take(50)}...")
                
            } catch (e: Exception) {
                Log.e(TAG, "转写流程异常: ${e.message}", e)
                // 即使出错也进入完成状态，用户可以手动输入
                _editableText.value = ""
                _recordingState.value = VoiceRecordingState.Completed(
                    transcription = "",
                    durationSeconds = duration,
                    audioFilePath = filePath
                )
            }
        }
    }
    
    /**
     * 取消录音
     */
    fun cancelRecording() {
        updateJob?.cancel()
        updateJob = null
        audioRecorder.cancelRecording()
        currentAudioFilePath = null
        _recordingState.value = VoiceRecordingState.Idle
        _editableText.value = ""
        Log.d(TAG, "Recording cancelled")
    }
    
    /**
     * 更新可编辑文本
     */
    fun updateEditableText(text: String) {
        _editableText.value = text
    }
    
    /**
     * 保存语音条目
     */
    fun saveVoiceItem() {
        val currentState = _recordingState.value
        if (currentState !is VoiceRecordingState.Completed) {
            return
        }
        
        viewModelScope.launch {
            try {
                _recordingState.value = VoiceRecordingState.Processing("正在保存...")
                
                val text = _editableText.value.ifBlank { "语音灵感" }
                val title = generateTitle(text)
                
                val result = itemRepository.createVoiceItem(
                    title = title,
                    audioUri = currentState.audioFilePath,
                    durationSeconds = currentState.durationSeconds,
                    summary = text
                )
                
                result.onSuccess { item ->
                    Log.d(TAG, "Voice item saved: ${item.id}")
                    _savedItemId.value = item.id
                }
                
                result.onFailure { e ->
                    Log.e(TAG, "Failed to save voice item: ${e.message}", e)
                    _recordingState.value = VoiceRecordingState.Error("保存失败: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving voice item: ${e.message}", e)
                _recordingState.value = VoiceRecordingState.Error("保存失败: ${e.message}")
            }
        }
    }
    
    /**
     * 从文本生成标题
     */
    private fun generateTitle(text: String): String {
        return if (text.length > 20) {
            text.take(20) + "..."
        } else {
            text.ifBlank { "语音灵感" }
        }
    }
    
    /**
     * 重置状态
     */
    fun reset() {
        cancelRecording()
        _savedItemId.value = null
    }
    
    /**
     * 启动定时更新录音状态
     */
    private fun startUpdateTimer() {
        updateJob = viewModelScope.launch {
            while (isActive && audioRecorder.isRecording()) {
                val duration = audioRecorder.getCurrentDuration()
                val amplitude = audioRecorder.getAmplitude()
                _recordingState.value = VoiceRecordingState.Recording(duration, amplitude)
                delay(100) // 每100ms更新一次
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        updateJob?.cancel()
        audioRecorder.cancelRecording()
    }
}
