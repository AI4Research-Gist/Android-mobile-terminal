package com.example.ai4research.capture.model

import java.util.Locale

enum class CaptureSource {
    SHARE_INTENT,
    LINK_PASTE,
    IMAGE_SCAN,
    SCREENSHOT,
    QUICK_INSIGHT,
    VOICE;

    fun wireValue(): String = name.lowercase(Locale.ROOT)
}
