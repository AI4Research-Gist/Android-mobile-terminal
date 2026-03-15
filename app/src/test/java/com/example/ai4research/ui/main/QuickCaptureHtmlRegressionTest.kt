package com.example.ai4research.ui.main

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickCaptureHtmlRegressionTest {

    private val assetPathCandidates = listOf(
        Path.of("app", "src", "main", "assets", "main_ui.html"),
        Path.of("src", "main", "assets", "main_ui.html")
    )

    @Test
    fun `quick capture html wires link and scan buttons to Android bridge`() {
        val assetPath = assetPathCandidates.firstOrNull(Files::exists)
            ?: error("Unable to locate main_ui.html from test working directory")
        val html = String(Files.readAllBytes(assetPath))

        assertTrue(html.contains("window.AndroidInterface.openLinkCapture"))
        assertTrue(html.contains("window.AndroidInterface.startScanCapture"))
        assertFalse(html.contains("// TODO: 链接采集"))
        assertFalse(html.contains("// TODO: 扫描采集"))
    }
}
