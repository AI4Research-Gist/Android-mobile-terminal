package com.example.ai4research.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import dagger.hilt.android.AndroidEntryPoint

/**
 * Only requests MediaProjection consent, then hands control back to FloatingWindowService.
 */
@AndroidEntryPoint
class ScreenCaptureActivity : ComponentActivity() {

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var captureMode = "full"

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            MediaProjectionStore.setPermission(result.resultCode, result.data!!)
            resumeCaptureInService()
        } else {
            Toast.makeText(this, "截图权限被拒绝", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        captureMode = intent.getStringExtra(FloatingWindowService.EXTRA_CAPTURE_MODE) ?: "full"
        mediaProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        if (MediaProjectionStore.hasPermission()) {
            resumeCaptureInService()
            finish()
            return
        }

        requestScreenCapture()
    }

    private fun requestScreenCapture() {
        val captureIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            mediaProjectionManager.createScreenCaptureIntent(
                MediaProjectionConfig.createConfigForDefaultDisplay()
            )
        } else {
            mediaProjectionManager.createScreenCaptureIntent()
        }
        projectionLauncher.launch(captureIntent)
    }

    private fun resumeCaptureInService() {
        startService(
            Intent(this, FloatingWindowService::class.java).apply {
                action = FloatingWindowService.ACTION_CAPTURE_AFTER_PERMISSION
                putExtra(FloatingWindowService.EXTRA_CAPTURE_MODE, captureMode)
            }
        )
    }
}
