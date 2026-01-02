package com.example.ai4research.core.floating

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object OverlayPermission {
    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun createSettingsIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
    }
}


