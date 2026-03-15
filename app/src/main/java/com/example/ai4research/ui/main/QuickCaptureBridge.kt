package com.example.ai4research.ui.main

internal class QuickCaptureBridge(
    private val postToMainThread: ((() -> Unit) -> Unit),
    private val hasOverlayPermission: () -> Boolean,
    private val onOpenLinkCapture: () -> Unit,
    private val onShowLinkPermissionPrompt: () -> Unit,
    private val onStartScanCapture: () -> Unit,
    private val onStartVoiceRecording: () -> Unit
) {
    fun openLinkCapture() {
        postToMainThread {
            if (hasOverlayPermission()) {
                onOpenLinkCapture()
            } else {
                onShowLinkPermissionPrompt()
            }
        }
    }

    fun startScanCapture() {
        postToMainThread(onStartScanCapture)
    }

    fun startVoiceRecording() {
        postToMainThread(onStartVoiceRecording)
    }
}
