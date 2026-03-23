package com.example.ai4research.service

import android.content.Intent
import android.os.Build
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.Log

/**
 * Cache MediaProjection permission result to avoid repeated prompts.
 * Note: This cache is in-memory only and clears on process death.
 */
object MediaProjectionStore {
    private const val TAG = "MediaProjectionStore"
    private var resultCode: Int? = null
    private var resultData: Intent? = null
    private var mediaProjection: MediaProjection? = null
    private var lastProjectionError: String? = null

    private fun canReuseConsentIntent(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    }

    fun hasPermission(): Boolean {
        return mediaProjection != null || (canReuseConsentIntent() && resultCode != null && resultData != null)
    }

    fun setPermission(resultCode: Int, data: Intent) {
        this.resultCode = resultCode
        // Keep the original consent intent; some devices are sensitive to reconstructed copies.
        this.resultData = data
        // Reset projection so we can recreate with new data
        this.mediaProjection?.stop()
        this.mediaProjection = null
        this.lastProjectionError = null
    }

    fun getOrCreateProjection(manager: MediaProjectionManager): MediaProjection? {
        mediaProjection?.let { return it }
        val code = resultCode ?: return null
        val data = resultData ?: return null
        return try {
            manager.getMediaProjection(code, data).also {
                mediaProjection = it
                lastProjectionError = null
            }
        } catch (e: Exception) {
            lastProjectionError = "${e.javaClass.simpleName}: ${e.message ?: "unknown error"}"
            Log.e(TAG, "Failed to create MediaProjection", e)
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
        if (!canReuseConsentIntent()) {
            resultCode = null
            resultData = null
        }
    }

    fun prepareProjection(manager: MediaProjectionManager): Boolean {
        return getOrCreateProjection(manager) != null
    }

    fun peekLastProjectionError(): String? = lastProjectionError
}
