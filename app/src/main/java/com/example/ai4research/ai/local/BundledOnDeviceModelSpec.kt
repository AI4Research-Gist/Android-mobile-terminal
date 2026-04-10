package com.example.ai4research.ai.local

import java.io.File

data class BundledOnDeviceModelSpec(
    val modelId: String,
    val version: String,
    val displayName: String,
    val relativeDirectory: String
) {
    fun resolveDirectory(rootDir: File): File = File(rootDir, relativeDirectory)
}

object BundledOnDeviceModels {
    val RECOMMENDED_QWEN3_0_6B = BundledOnDeviceModelSpec(
        modelId = "qwen3-0.6b-q4f16_0-mlc",
        version = "2026.04.02",
        displayName = "Qwen3-0.6B-q4f16_0-MLC",
        relativeDirectory = "qwen3-0.6b-q4f16_0-mlc"
    )

    val defaults: List<BundledOnDeviceModelSpec> = listOf(RECOMMENDED_QWEN3_0_6B)
}
