package com.example.ai4research.ai.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnDeviceModelDirectoryProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun rootDir(): File = File(context.filesDir, "models")

    fun modelDir(modelId: String): File = File(rootDir(), modelId)
}
