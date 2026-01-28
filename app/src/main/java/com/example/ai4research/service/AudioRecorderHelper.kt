package com.example.ai4research.service

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import javax.inject.Inject

/**
 * 音频录制帮助类
 * 封装MediaRecorder，提供简单的录音API
 */
class AudioRecorderHelper @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "AudioRecorderHelper"
        private const val RECORDINGS_DIR = "recordings"
        private const val MAX_AMPLITUDE = 32767f // MediaRecorder.getMaxAmplitude() 的最大值
    }
    
    private var mediaRecorder: MediaRecorder? = null
    private var currentFilePath: String? = null
    private var startTime: Long = 0
    private var isRecording = false
    
    /**
     * 开始录音
     * @return 录音文件路径，失败返回null
     */
    fun startRecording(): String? {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return currentFilePath
        }
        
        try {
            // 创建录音目录
            val recordingsDir = File(context.cacheDir, RECORDINGS_DIR)
            if (!recordingsDir.exists()) {
                recordingsDir.mkdirs()
            }
            
            // 生成唯一文件名
            val fileName = "voice_${System.currentTimeMillis()}.m4a"
            val audioFile = File(recordingsDir, fileName)
            currentFilePath = audioFile.absolutePath
            
            // 配置MediaRecorder
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(currentFilePath)
                
                prepare()
                start()
            }
            
            startTime = System.currentTimeMillis()
            isRecording = true
            Log.d(TAG, "Recording started: $currentFilePath")
            
            return currentFilePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}", e)
            releaseRecorder()
            return null
        }
    }
    
    /**
     * 停止录音
     * @return Pair<文件路径, 录音时长(秒)>，失败返回null
     */
    fun stopRecording(): Pair<String, Int>? {
        if (!isRecording) {
            Log.w(TAG, "Not recording")
            return null
        }
        
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            
            val duration = ((System.currentTimeMillis() - startTime) / 1000).toInt()
            val filePath = currentFilePath
            
            Log.d(TAG, "Recording stopped: $filePath, duration: ${duration}s")
            
            isRecording = false
            mediaRecorder = null
            
            return if (filePath != null) {
                Pair(filePath, duration)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording: ${e.message}", e)
            releaseRecorder()
            return null
        }
    }
    
    /**
     * 取消录音（删除已录制的文件）
     */
    fun cancelRecording() {
        if (isRecording) {
            try {
                mediaRecorder?.apply {
                    stop()
                    release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping recorder: ${e.message}")
            }
            
            // 删除录音文件
            currentFilePath?.let { path ->
                try {
                    File(path).delete()
                    Log.d(TAG, "Deleted cancelled recording: $path")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to delete recording: ${e.message}")
                }
            }
        }
        
        releaseRecorder()
    }
    
    /**
     * 获取当前音频振幅（归一化到0-1）
     * 用于波形动画显示
     */
    fun getAmplitude(): Float {
        return if (isRecording && mediaRecorder != null) {
            try {
                val amplitude = mediaRecorder!!.maxAmplitude
                (amplitude / MAX_AMPLITUDE).coerceIn(0f, 1f)
            } catch (e: Exception) {
                0f
            }
        } else {
            0f
        }
    }
    
    /**
     * 获取当前录音时长（秒）
     */
    fun getCurrentDuration(): Int {
        return if (isRecording) {
            ((System.currentTimeMillis() - startTime) / 1000).toInt()
        } else {
            0
        }
    }
    
    /**
     * 是否正在录音
     */
    fun isRecording(): Boolean = isRecording
    
    /**
     * 释放资源
     */
    private fun releaseRecorder() {
        try {
            mediaRecorder?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing recorder: ${e.message}")
        }
        mediaRecorder = null
        currentFilePath = null
        isRecording = false
    }
    
    /**
     * 清理所有录音缓存文件
     */
    fun clearRecordingsCache() {
        val recordingsDir = File(context.cacheDir, RECORDINGS_DIR)
        if (recordingsDir.exists()) {
            recordingsDir.listFiles()?.forEach { it.delete() }
            Log.d(TAG, "Cleared recordings cache")
        }
    }
}
