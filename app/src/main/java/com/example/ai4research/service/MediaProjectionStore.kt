package com.example.ai4research.service

import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager

/**
 * Cache MediaProjection permission result to avoid repeated prompts.
 * Note: This cache is in-memory only and clears on process death.
 */
object MediaProjectionStore {
    private var resultCode: Int? = null
    private var resultData: Intent? = null
    private var mediaProjection: MediaProjection? = null

    fun hasPermission(): Boolean {
        return resultCode != null && resultData != null
    }

    fun setPermission(resultCode: Int, data: Intent) {
        this.resultCode = resultCode
        // Keep a copy to avoid accidental mutations
        this.resultData = Intent(data)
        // Reset projection so we can recreate with new data
        this.mediaProjection?.stop()
        this.mediaProjection = null
    }

    fun getOrCreateProjection(manager: MediaProjectionManager): MediaProjection? {
        mediaProjection?.let { return it }
        val code = resultCode ?: return null
        val data = resultData ?: return null
        return try {
            manager.getMediaProjection(code, data).also { mediaProjection = it }
        } catch (e: Exception) {
            clear()
            null
        }
    }

    fun clear() {
        mediaProjection?.stop()
        mediaProjection = null
        resultCode = null
        resultData = null
    }

    fun releaseProjection() {
        mediaProjection?.stop()
        mediaProjection = null
    }
}
