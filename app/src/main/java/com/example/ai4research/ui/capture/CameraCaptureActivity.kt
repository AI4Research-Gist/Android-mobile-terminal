package com.example.ai4research.ui.capture

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.example.ai4research.core.theme.AI4ResearchTheme
import com.example.ai4research.domain.repository.ItemRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class CameraCaptureActivity : ComponentActivity() {

    @Inject
    lateinit var itemRepository: ItemRepository

    private var outputUri: Uri? = null

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = outputUri
        if (success && uri != null) {
            CoroutineScope(Dispatchers.Main).launch {
                withContext(Dispatchers.IO) {
                    itemRepository.createImageItem(imageUri = uri.toString())
                }
                finish()
            }
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AI4ResearchTheme {
                // Keep a minimal themed surface; activity will finish quickly after camera result.
            }
        }

        val file = File(cacheDir, "capture_${UUID.randomUUID()}.jpg")
        outputUri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )
        takePicture.launch(outputUri)
    }
}





