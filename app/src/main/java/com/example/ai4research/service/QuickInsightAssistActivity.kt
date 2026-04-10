package com.example.ai4research.service

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class QuickInsightAssistActivity : ComponentActivity() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_PICK_IMAGE = "pick_image"
        const val MODE_REQUEST_AUDIO_PERMISSION = "request_audio_permission"
    }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            startService(
                Intent(this, FloatingWindowService::class.java).apply {
                    action = FloatingWindowService.ACTION_ATTACH_QUICK_INSIGHT_IMAGE
                    putExtra(FloatingWindowService.EXTRA_IMAGE_URI, uri.toString())
                }
            )
        } else {
            startService(
                Intent(this, FloatingWindowService::class.java).apply {
                    action = FloatingWindowService.ACTION_RESTORE_QUICK_INSIGHT
                }
            )
            Toast.makeText(this, "未选择图片", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    private val recordPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startService(
                Intent(this, FloatingWindowService::class.java).apply {
                    action = FloatingWindowService.ACTION_START_QUICK_INSIGHT_RECORDING
                }
            )
        } else {
            startService(
                Intent(this, FloatingWindowService::class.java).apply {
                    action = FloatingWindowService.ACTION_RESTORE_QUICK_INSIGHT
                }
            )
            Toast.makeText(this, "录音权限被拒绝", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when (intent.getStringExtra(EXTRA_MODE)) {
            MODE_PICK_IMAGE -> imagePickerLauncher.launch(arrayOf("image/*"))
            MODE_REQUEST_AUDIO_PERMISSION -> recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            else -> finish()
        }
    }
}
