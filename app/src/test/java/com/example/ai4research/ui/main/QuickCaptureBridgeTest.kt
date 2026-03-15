package com.example.ai4research.ui.main

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickCaptureBridgeTest {

    @Test
    fun `openLinkCapture posts link action when overlay permission already granted`() {
        val events = mutableListOf<String>()
        val bridge = QuickCaptureBridge(
            postToMainThread = { block ->
                events += "post"
                block()
            },
            hasOverlayPermission = { true },
            onOpenLinkCapture = { events += "link" },
            onShowLinkPermissionPrompt = { events += "prompt" },
            onStartScanCapture = { events += "scan" },
            onStartVoiceRecording = { events += "voice" }
        )

        bridge.openLinkCapture()

        assertEquals(listOf("post", "link"), events)
    }

    @Test
    fun `openLinkCapture shows permission prompt when overlay permission is missing`() {
        val events = mutableListOf<String>()
        val bridge = QuickCaptureBridge(
            postToMainThread = { block ->
                events += "post"
                block()
            },
            hasOverlayPermission = { false },
            onOpenLinkCapture = { events += "link" },
            onShowLinkPermissionPrompt = { events += "prompt" },
            onStartScanCapture = { events += "scan" },
            onStartVoiceRecording = { events += "voice" }
        )

        bridge.openLinkCapture()

        assertEquals(listOf("post", "prompt"), events)
    }

    @Test
    fun `startScanCapture posts scan action to main thread dispatcher`() {
        val events = mutableListOf<String>()
        val bridge = QuickCaptureBridge(
            postToMainThread = { block ->
                events += "post"
                block()
            },
            hasOverlayPermission = { true },
            onOpenLinkCapture = { events += "link" },
            onShowLinkPermissionPrompt = { events += "prompt" },
            onStartScanCapture = { events += "scan" },
            onStartVoiceRecording = { events += "voice" }
        )

        bridge.startScanCapture()

        assertEquals(listOf("post", "scan"), events)
    }

    @Test
    fun `startVoiceRecording still posts voice action to main thread dispatcher`() {
        val events = mutableListOf<String>()
        val bridge = QuickCaptureBridge(
            postToMainThread = { block ->
                events += "post"
                block()
            },
            hasOverlayPermission = { true },
            onOpenLinkCapture = { events += "link" },
            onShowLinkPermissionPrompt = { events += "prompt" },
            onStartScanCapture = { events += "scan" },
            onStartVoiceRecording = { events += "voice" }
        )

        bridge.startVoiceRecording()

        assertEquals(listOf("post", "voice"), events)
    }
}
