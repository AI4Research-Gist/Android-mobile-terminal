package com.example.ai4research.ui.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai4research.domain.model.ItemType
import com.example.ai4research.domain.model.ResearchItem
import com.example.ai4research.domain.repository.ItemRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class VoiceViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val itemRepository: ItemRepository
) : ViewModel() {

    val voiceItems: StateFlow<List<ResearchItem>> =
        itemRepository.observeItems(type = ItemType.VOICE)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var recorder: MediaRecorder? = null
    private var startElapsedMs: Long = 0L
    private var outputFile: File? = null

    fun startRecording() {
        if (_isRecording.value) return

        try {
            val dir = File(context.filesDir, "voice").apply { mkdirs() }
            val fileName = "voice_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.m4a"
            val file = File(dir, fileName)
            outputFile = file

            val r = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44_100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            recorder = r
            startElapsedMs = SystemClock.elapsedRealtime()
            _isRecording.value = true
            _errorMessage.value = null
        } catch (e: Exception) {
            _errorMessage.value = e.message ?: "启动录音失败"
            safeRelease()
        }
    }

    fun stopRecording() {
        if (!_isRecording.value) return

        val durationSeconds = ((SystemClock.elapsedRealtime() - startElapsedMs) / 1000L).toInt().coerceAtLeast(1)
        val file = outputFile

        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            // ignore stop errors for very short recordings
        } finally {
            recorder = null
            _isRecording.value = false
        }

        if (file == null || !file.exists()) {
            _errorMessage.value = "录音文件生成失败"
            return
        }

        viewModelScope.launch {
            val title = "语音灵感 ${SimpleDateFormat("MM/dd HH:mm", Locale.CHINA).format(Date())}"
            val result = itemRepository.createVoiceItem(
                title = title,
                audioUri = file.absolutePath,
                durationSeconds = durationSeconds,
                summary = "语音长度：${durationSeconds}s"
            )
            result.exceptionOrNull()?.let { _errorMessage.value = it.message ?: "保存失败" }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        safeRelease()
    }

    private fun safeRelease() {
        runCatching { recorder?.release() }
        recorder = null
        _isRecording.value = false
    }
}






