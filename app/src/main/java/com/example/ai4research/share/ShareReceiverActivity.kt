package com.example.ai4research.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.ai4research.MainActivity
import com.example.ai4research.capture.model.CaptureInput
import com.example.ai4research.capture.model.CaptureRequest
import com.example.ai4research.capture.model.CaptureSource
import com.example.ai4research.capture.pipeline.CapturePipeline
import com.example.ai4research.core.theme.AI4ResearchTheme
import com.example.ai4research.data.repository.AuthRepository
import com.example.ai4research.domain.model.ItemStatus
import com.example.ai4research.domain.model.ItemType
import com.example.ai4research.domain.repository.ItemRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var itemRepository: ItemRepository

    @Inject
    lateinit var capturePipeline: CapturePipeline

    private var parsedDraft by mutableStateOf<SharedTextDraft?>(null)
    private var isLoading by mutableStateOf(true)
    private var isSaving by mutableStateOf(false)
    private var isLoggedIn by mutableStateOf(false)
    private var screenError by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        setContent {
            AI4ResearchTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ShareImportScreen(
                        draft = parsedDraft,
                        isLoading = isLoading,
                        isSaving = isSaving,
                        isLoggedIn = isLoggedIn,
                        screenError = screenError,
                        onClose = { finish() },
                        onOpenMainApp = { openMainApp() },
                        onSave = { title, summary, url, type ->
                            importSharedContent(title, summary, url, type)
                        }
                    )
                }
            }
        }
        loadShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        loadShareIntent(intent)
    }

    private fun loadShareIntent(intent: Intent) {
        isLoading = true
        isSaving = false
        screenError = null
        lifecycleScope.launch {
            isLoggedIn = authRepository.isLoggedInFast()
            parsedDraft = parseIncomingIntent(intent)
            if (parsedDraft == null) {
                screenError = "No share content could be parsed"
            }
            isLoading = false
        }
    }

    private fun parseIncomingIntent(intent: Intent): SharedTextDraft? {
        val extraText = intent.getStringExtra(Intent.EXTRA_TEXT)
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        val referrerPackage = resolveReferrerPackage(intent)
        return SharedTextParser.parse(extraText, subject, referrerPackage)
    }

    private fun resolveReferrerPackage(intent: Intent): String? {
        val referrerUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_REFERRER)
        val referrerName = intent.getStringExtra(Intent.EXTRA_REFERRER_NAME)
        return referrerUri?.host
            ?: referrerName
            ?: callingActivity?.packageName
            ?: intent.`package`
    }

    private fun importSharedContent(title: String, summary: String, url: String, type: ItemType) {
        val draft = parsedDraft ?: return
        isSaving = true
        screenError = null

        lifecycleScope.launch {
            val captureRequest = CaptureRequest(
                entry = CaptureSource.SHARE_INTENT,
                input = CaptureInput.SharedText(
                    rawText = draft.rawText,
                    subject = draft.subject,
                    url = url.trim().ifBlank { null } ?: draft.url,
                    referrerPackage = draft.referrerPackage,
                    sourceId = draft.source.id,
                    sourceLabel = draft.source.displayName
                ),
                expectedType = type,
                titleHint = title.trim(),
                summaryHint = summary.trim()
            )

            val result = capturePipeline.prepare(captureRequest).fold(
                onSuccess = { prepared ->
                    itemRepository.createFullItem(
                        title = prepared.title,
                        summary = prepared.summary,
                        contentMd = prepared.contentMarkdown,
                        originUrl = prepared.originUrl,
                        type = prepared.type,
                        status = ItemStatus.DONE,
                        metaJson = prepared.metaJson,
                        note = draft.subject?.takeIf { it.isNotBlank() },
                        tags = prepared.tags
                    )
                },
                onFailure = { Result.failure(it) }
            )

            isSaving = false
            result.fold(
                onSuccess = { openMainApp(it.id) },
                onFailure = { screenError = it.message ?: "Share import failed" }
            )
        }
    }

    private fun openMainApp(itemId: String? = null) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (!itemId.isNullOrBlank()) {
                putExtra("shared_item_id", itemId)
            }
        }
        startActivity(intent)
        finish()
    }
}

@Composable
private fun ShareImportScreen(
    draft: SharedTextDraft?,
    isLoading: Boolean,
    isSaving: Boolean,
    isLoggedIn: Boolean,
    screenError: String?,
    onClose: () -> Unit,
    onOpenMainApp: () -> Unit,
    onSave: (title: String, summary: String, url: String, type: ItemType) -> Unit
) {
    var title by remember(draft?.title) { mutableStateOf(draft?.title.orEmpty()) }
    var summary by remember(draft?.summary) { mutableStateOf(draft?.summary.orEmpty()) }
    var url by remember(draft?.url) { mutableStateOf(draft?.url.orEmpty()) }
    var selectedType by remember(draft?.suggestedType) { mutableStateOf(draft?.suggestedType ?: ItemType.INSIGHT) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Import Shared Content",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )

        when {
            isLoading -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("Parsing shared content...", style = MaterialTheme.typography.bodyLarge)
                }
            }

            draft == null -> {
                Text(
                    text = screenError ?: "No importable content was found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
                OutlinedButton(onClick = onClose) {
                    Text("Close")
                }
            }

            else -> {
                Text(
                    text = "Source: ${draft.source.displayName}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                if (!screenError.isNullOrBlank()) {
                    Text(
                        text = screenError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (!isLoggedIn) {
                    Text(
                        text = "You are not logged in right now, so the app cannot save this into your library yet. Open the main app first and log in, then try again.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onOpenMainApp) {
                            Text("Open App")
                        }
                        OutlinedButton(onClick = onClose) {
                            Text("Close")
                        }
                    }
                    return@Column
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Title") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    singleLine = false
                )

                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    label = { Text("Summary / Notes") }
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Link") },
                    singleLine = false
                )

                Text(
                    text = "Type",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ItemType.ARTICLE to "Article",
                        ItemType.INSIGHT to "Insight",
                        ItemType.PAPER to "Paper",
                        ItemType.COMPETITION to "Competition"
                    ).forEach { (type, label) ->
                        val selected = selectedType == type
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            tonalElevation = if (selected) 2.dp else 0.dp,
                            modifier = Modifier.clickable { selectedType = type }
                        ) {
                            Text(
                                text = label,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                Text(
                    text = "Original Shared Text",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = draft.rawText,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onClose) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { onSave(title, summary, url, selectedType) },
                        enabled = !isSaving && title.isNotBlank() && summary.isNotBlank()
                    ) {
                        Text(if (isSaving) "Importing..." else "Save To App")
                    }
                }
            }
        }
    }
}
