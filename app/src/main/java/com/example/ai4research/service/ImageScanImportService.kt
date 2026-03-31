package com.example.ai4research.service

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import com.example.ai4research.domain.model.ItemStatus
import com.example.ai4research.domain.model.ItemType
import com.example.ai4research.domain.model.ResearchItem
import com.example.ai4research.domain.repository.ItemRepository
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class ImageScanImportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiService: AIService,
    private val itemRepository: ItemRepository
) {
    companion object {
        private const val TAG = "ImageScanImportService"
    }

    private val gson = Gson()

    data class QueuedScanImport(
        val item: ResearchItem,
        val imageUris: List<Uri>,
        val captureMode: String,
        val parseStartedAt: Long
    )

    suspend fun queueImport(
        imageUris: List<Uri>,
        selectedType: ItemType,
        projectId: String?,
        projectName: String?,
        captureMode: String = "gallery"
    ): Result<QueuedScanImport> = withContext(Dispatchers.IO) {
        val normalizedUris = imageUris.distinct()
        if (normalizedUris.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("未选中可用图片"))
        }

        val parseStartedAt = System.currentTimeMillis()
        val pageCount = normalizedUris.size
        val placeholderTitle = if (pageCount > 1) "图片扫描（$pageCount 张）" else "图片扫描"
        val placeholderSummary = "已加入${getTypeDisplayName(selectedType)}，正在 OCR 识别与 AI 整理"
        val placeholderContent = buildPendingMarkdown(
            title = placeholderTitle,
            pageCount = pageCount,
            selectedType = selectedType,
            projectName = projectName
        )
        val placeholderMetaJson = buildPendingMetaJson(
            captureMode = captureMode,
            pageCount = pageCount,
            parseStartedAt = parseStartedAt,
            feedback = "已加入${getTypeDisplayName(selectedType)}，正在后台解析"
        )

        itemRepository.createLocalPendingItem(
            title = placeholderTitle,
            summary = placeholderSummary,
            contentMd = placeholderContent,
            originUrl = null,
            type = selectedType,
            status = ItemStatus.PROCESSING,
            metaJson = placeholderMetaJson,
            note = null,
            tags = null,
            projectId = projectId,
            projectName = projectName
        ).map { item ->
            QueuedScanImport(
                item = item,
                imageUris = normalizedUris,
                captureMode = captureMode,
                parseStartedAt = parseStartedAt
            )
        }
    }

    suspend fun processQueuedImport(queuedImport: QueuedScanImport): Result<ResearchItem> = withContext(Dispatchers.IO) {
        val itemId = queuedImport.item.id
        val pageCount = queuedImport.imageUris.size

        try {
            val copyFailures = mutableListOf<String>()
            val localPaths = queuedImport.imageUris.mapIndexedNotNull { index, uri ->
                copyUriToLocalFile(uri, queuedImport.captureMode).onFailure { error ->
                    val detail = "第${index + 1}张图片导入失败: ${error.readableMessage()}"
                    copyFailures += detail
                    Log.e(TAG, detail, error)
                }.getOrNull()
            }

            if (localPaths.isEmpty()) {
                throw IllegalArgumentException(
                    copyFailures.firstOrNull() ?: "没有找到可处理的图片"
                )
            }

            val payload = buildPayloadFromLocalPaths(localPaths, queuedImport.captureMode)
            val mergedMetaJson = mergeTrackingMeta(
                baseMetaJson = payload.metaJson,
                captureMode = queuedImport.captureMode,
                pageCount = localPaths.size,
                parseStartedAt = queuedImport.parseStartedAt,
                parseFinishedAt = System.currentTimeMillis(),
                feedback = "扫描解析完成"
            )

            val updateResult = itemRepository.updateItem(
                id = itemId,
                title = payload.title,
                summary = payload.summary,
                content = payload.markdown,
                originUrl = payload.originUrl,
                tags = payload.tags,
                metaJson = mergedMetaJson,
                status = ItemStatus.DONE
            )

            if (updateResult.isFailure) {
                throw updateResult.exceptionOrNull() ?: IllegalStateException("更新扫描条目失败")
            }

            val syncResult = itemRepository.syncLocalItemToRemote(itemId)
            if (syncResult.isSuccess) {
                return@withContext syncResult
            }

            val localItem = itemRepository.getItem(itemId)
            if (localItem != null) {
                return@withContext Result.success(localItem)
            }

            Result.failure(syncResult.exceptionOrNull() ?: IllegalStateException("同步扫描条目失败"))
        } catch (e: Exception) {
            val failedSummary = "图片已加入${getTypeDisplayName(queuedImport.item.type)}，但解析失败，可稍后重试"
            val failedMetaJson = mergeTrackingMeta(
                baseMetaJson = null,
                captureMode = queuedImport.captureMode,
                pageCount = pageCount,
                parseStartedAt = queuedImport.parseStartedAt,
                parseFinishedAt = System.currentTimeMillis(),
                feedback = e.message ?: "扫描解析失败"
            )

            itemRepository.updateItem(
                id = itemId,
                title = queuedImport.item.title,
                summary = failedSummary,
                content = buildFailedMarkdown(
                    title = queuedImport.item.title,
                    pageCount = pageCount,
                    errorMessage = e.message
                ),
                metaJson = failedMetaJson,
                status = ItemStatus.FAILED
            )
            runCatching { itemRepository.syncLocalItemToRemote(itemId) }

            Result.failure(e)
        }
    }

    suspend fun importFromUris(
        imageUris: List<Uri>,
        captureMode: String = "gallery"
    ): Result<ResearchItem> = withContext(Dispatchers.IO) {
        val copyFailures = mutableListOf<String>()
        val localPaths = imageUris.mapIndexedNotNull { index, uri ->
            copyUriToLocalFile(uri, captureMode).onFailure { error ->
                val detail = "第${index + 1}张图片导入失败: ${error.readableMessage()}"
                copyFailures += detail
                Log.e(TAG, detail, error)
            }.getOrNull()
        }

        if (localPaths.isEmpty()) {
            return@withContext Result.failure(
                IllegalArgumentException(copyFailures.firstOrNull() ?: "未选中可用图片")
            )
        }

        importFromLocalPaths(localPaths, captureMode)
    }

    suspend fun importFromLocalPaths(
        imagePaths: List<String>,
        captureMode: String = "gallery"
    ): Result<ResearchItem> = withContext(Dispatchers.IO) {
        val payload = buildPayloadFromLocalPaths(imagePaths, captureMode)
        itemRepository.createFullItem(
            title = payload.title,
            summary = payload.summary,
            contentMd = payload.markdown,
            originUrl = payload.originUrl,
            type = ItemType.ARTICLE,
            status = ItemStatus.DONE,
            metaJson = payload.metaJson,
            tags = payload.tags
        )
    }

    private suspend fun buildPayloadFromLocalPaths(
        imagePaths: List<String>,
        captureMode: String
    ): ScanPayload = withContext(Dispatchers.IO) {
        val normalizedPaths = imagePaths
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .filter { File(it).exists() }

        if (normalizedPaths.isEmpty()) {
            throw IllegalArgumentException("没有找到可处理的图片")
        }

        val pageResults = normalizedPaths.mapIndexed { index, path ->
            processSingleImage(path = path, pageIndex = index + 1)
        }
        if (pageResults.none { it.hasRecognizedSignal() }) {
            val details = pageResults.joinToString("；") { it.describeFailure() }
            throw IllegalStateException(
                details.ifBlank { "OCR 未识别到正文，请尝试更清晰的截图或多张连续长图" }
            )
        }

        val combinedRawText = buildCombinedRawText(pageResults)
        val organizedResult = aiService.organizeOcrBatch(
            rawText = combinedRawText.ifBlank { buildFallbackRawText(normalizedPaths.size) },
            pageHints = pageResults.mapNotNull { it.ocrResult }
        ).getOrElse {
            aiService.organizeOcrBatch(
                rawText = buildFallbackRawText(normalizedPaths.size),
                pageHints = pageResults.mapNotNull { it.ocrResult }
            ).getOrThrow()
        }

        val originUrl = organizedResult.referencedLinks.firstOrNull()?.takeIf { it.startsWith("http") }
            ?: organizedResult.paperCandidates.firstOrNull()?.url?.takeIf { it.startsWith("http") }
            ?: organizedResult.originalUrl.takeIf { it.startsWith("http") }

        val title = organizedResult.title.ifBlank { "图片扫描" }
        val summary = organizedResult.mediumSummary?.takeIf { it.isNotBlank() }
            ?: organizedResult.summaryZh?.takeIf { it.isNotBlank() }
            ?: organizedResult.summary

        ScanPayload(
            title = title,
            summary = summary,
            markdown = buildMarkdownContent(
                result = organizedResult,
                rawText = combinedRawText.ifBlank { "（OCR 暂未识别出正文）" },
                pageCount = pageResults.size,
                captureMode = captureMode
            ),
            metaJson = buildResultMetaJson(
                result = organizedResult,
                pageCount = pageResults.size,
                captureMode = captureMode
            ),
            tags = organizedResult.tags,
            originUrl = originUrl
        )
    }

    private suspend fun processSingleImage(path: String, pageIndex: Int): ImportedPage = withContext(Dispatchers.IO) {
        val bitmapResult = OcrBitmapLoader.loadBitmap(path)
        bitmapResult.fold(
            onSuccess = { bitmap ->
                try {
                    aiService.recognizeImageStructured(bitmap).fold(
                        onSuccess = { ocr ->
                            ImportedPage(pageIndex = pageIndex, imagePath = path, ocrResult = ocr)
                        },
                        onFailure = { error ->
                            Log.e(TAG, "OCR failed for page=$pageIndex path=$path", error)
                            ImportedPage(
                                pageIndex = pageIndex,
                                imagePath = path,
                                ocrResult = null,
                                errorMessage = "第${pageIndex}页 OCR 失败: ${error.readableMessage()}"
                            )
                        }
                    )
                } finally {
                    OcrBitmapLoader.recycle(bitmap)
                }
            },
            onFailure = { error ->
                Log.e(TAG, "Bitmap decode failed for page=$pageIndex path=$path", error)
                ImportedPage(
                    pageIndex = pageIndex,
                    imagePath = path,
                    ocrResult = null,
                    errorMessage = "第${pageIndex}页图片解码失败: ${error.readableMessage()}"
                )
            }
        )
    }

    private fun Throwable.readableMessage(): String {
        return message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
    }

    private fun ImportedPage.describeFailure(): String {
        return errorMessage
            ?: if (hasRecognizedSignal()) {
                "第${pageIndex}页已识别到部分线索"
            } else {
                "第${pageIndex}页未识别到正文"
            }
    }

    private fun getPageSignalSummary(page: ImportedPage): String {
        val result = page.ocrResult ?: return ""
        return listOfNotNull(
            result.title?.takeIf { it.isNotBlank() }?.let { "标题" },
            result.authors?.takeIf { it.isNotBlank() }?.let { "作者" },
            result.identifier?.takeIf { it.isNotBlank() }?.let { "编号" },
            result.referencedLinks.takeIf { it.isNotEmpty() }?.let { "链接" },
            result.content?.takeIf { it.isNotBlank() }?.let { "正文" }
        ).joinToString("、")
    }

    private fun ImportedPage.describeSuccessState(): String {
        val summary = getPageSignalSummary(this)
        return if (summary.isBlank()) {
            "第${pageIndex}页已识别"
        } else {
            "第${pageIndex}页识别到：$summary"
        }
    }

    private fun buildCombinedRawText(pageResults: List<ImportedPage>): String {
        return pageResults.joinToString("\n\n") { page ->
            val text = page.ocrResult?.content?.trim().orEmpty()
            val title = page.ocrResult?.title?.trim().orEmpty()
            val authors = page.ocrResult?.authors?.trim().orEmpty()
            val identifier = page.ocrResult?.identifier?.trim().orEmpty()
            val links = page.ocrResult?.referencedLinks.orEmpty()
            buildString {
                appendLine("## 第${page.pageIndex}页")
                if (title.isNotBlank()) {
                    appendLine("标题线索: $title")
                }
                if (authors.isNotBlank()) {
                    appendLine("作者线索: $authors")
                }
                if (identifier.isNotBlank()) {
                    appendLine("编号线索: $identifier")
                }
                if (links.isNotEmpty()) {
                    appendLine("链接线索: ${links.joinToString(", ")}")
                }
                appendLine(text.ifBlank { "（未识别到正文）" })
            }.trim()
        }.trim()
    }

    private fun buildFallbackRawText(pageCount: Int): String {
        return "共导入 $pageCount 张图片，但 OCR 暂未识别出稳定正文。"
    }

    private fun buildPendingMarkdown(
        title: String,
        pageCount: Int,
        selectedType: ItemType,
        projectName: String?
    ): String {
        return buildString {
            appendLine("# $title")
            appendLine()
            appendLine("当前状态：已加入${getTypeDisplayName(selectedType)}，正在后台进行 OCR 与 AI 整理。")
            appendLine()
            appendLine("- 图片数量: $pageCount")
            projectName?.takeIf { it.isNotBlank() }?.let {
                appendLine("- 项目: $it")
            }
        }
    }

    private fun buildFailedMarkdown(
        title: String,
        pageCount: Int,
        errorMessage: String?
    ): String {
        return buildString {
            appendLine("# $title")
            appendLine()
            appendLine("当前状态：图片扫描解析失败。")
            appendLine()
            appendLine("- 图片数量: $pageCount")
            errorMessage?.takeIf { it.isNotBlank() }?.let {
                appendLine("- 失败原因: $it")
            }
        }
    }

    private fun buildPendingMetaJson(
        captureMode: String,
        pageCount: Int,
        parseStartedAt: Long,
        feedback: String
    ): String {
        return gson.toJson(
            mapOf(
                "source" to "ocr",
                "capture_mode" to captureMode,
                "ocr_page_count" to pageCount,
                "parse_started_at" to parseStartedAt,
                "parse_feedback" to feedback
            )
        )
    }

    private fun mergeTrackingMeta(
        baseMetaJson: String?,
        captureMode: String,
        pageCount: Int,
        parseStartedAt: Long,
        parseFinishedAt: Long? = null,
        feedback: String? = null
    ): String {
        val merged = mutableMapOf<String, Any?>()

        if (!baseMetaJson.isNullOrBlank()) {
            val parsed = runCatching {
                @Suppress("UNCHECKED_CAST")
                gson.fromJson(baseMetaJson, Map::class.java) as? Map<String, Any?>
            }.getOrNull()
            if (parsed != null) {
                merged.putAll(parsed)
            }
        }

        merged["source"] = merged["source"] ?: "ocr"
        merged["capture_mode"] = captureMode
        merged["ocr_page_count"] = pageCount
        merged["parse_started_at"] = parseStartedAt
        parseFinishedAt?.let { merged["parse_finished_at"] = it }
        feedback?.let { merged["parse_feedback"] = it }

        return gson.toJson(merged)
    }

    private fun buildMarkdownContent(
        result: FullLinkParseResult,
        rawText: String,
        pageCount: Int,
        captureMode: String
    ): String {
        val sanitizedRawText = rawText.replace("```", "'''")
        return buildString {
            appendLine("# ${result.title}")
            appendLine()
            appendLine("## AI整理")
            appendLine(result.summaryZh ?: result.summary)

            result.summaryEn?.takeIf { it.isNotBlank() }?.let {
                appendLine()
                appendLine("### English Summary")
                appendLine(it)
            }

            result.authors?.takeIf { it.isNotBlank() }?.let {
                appendLine()
                appendLine("- 作者: $it")
            }
            result.identifier?.takeIf { it.isNotBlank() }?.let {
                appendLine("- 标识符: $it")
            }
            result.platform?.takeIf { it.isNotBlank() }?.let {
                appendLine("- 平台: $it")
            }
            result.accountName?.takeIf { it.isNotBlank() }?.let {
                appendLine("- 账号: $it")
            }
            result.publishDate?.takeIf { it.isNotBlank() }?.let {
                appendLine("- 发布时间: $it")
            }
            appendLine("- 采集方式: $captureMode")
            appendLine("- 图片数量: $pageCount")

            if (result.corePoints.isNotEmpty()) {
                appendLine()
                appendLine("### 核心要点")
                result.corePoints.take(5).forEach { point ->
                    appendLine("- $point")
                }
            }

            if (result.referencedLinks.isNotEmpty()) {
                appendLine()
                appendLine("### 提取到的链接")
                result.referencedLinks.forEach { link ->
                    appendLine("- $link")
                }
            }

            if (result.paperCandidates.isNotEmpty()) {
                appendLine()
                appendLine("### 论文线索")
                result.paperCandidates.forEach { candidate ->
                    val label = candidate.label?.takeIf { it.isNotBlank() } ?: candidate.kind
                    appendLine("- $label: ${candidate.url}")
                }
            }

            appendLine()
            appendLine("## OCR原文")
            appendLine("```text")
            appendLine(sanitizedRawText)
            appendLine("```")
        }
    }

    private fun buildResultMetaJson(
        result: FullLinkParseResult,
        pageCount: Int,
        captureMode: String
    ): String? {
        val base = runCatching {
            @Suppress("UNCHECKED_CAST")
            gson.fromJson(result.toMetaJson() ?: "{}", MutableMap::class.java) as? MutableMap<String, Any?>
        }.getOrNull() ?: mutableMapOf()

        base["capture_mode"] = captureMode
        base["ocr_page_count"] = pageCount
        if (base["source"] == null) {
            base["source"] = result.source
        }

        return if (base.isEmpty()) null else gson.toJson(base)
    }

    private fun copyUriToLocalFile(uri: Uri, captureMode: String): Result<String> = runCatching {
        val targetDir = File(context.filesDir, "imports/$captureMode").apply { mkdirs() }
        val extension = resolveExtension(uri)
        val targetFile = File(targetDir, "scan_${System.currentTimeMillis()}_${targetDir.listFiles()?.size ?: 0}.$extension")

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        } ?: error("无法读取图片: $uri")

        targetFile.absolutePath
    }

    private fun resolveExtension(uri: Uri): String {
        val mimeType = context.contentResolver.getType(uri)
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            ?.takeIf { it.isNotBlank() }
            ?: "jpg"
    }

    private fun getTypeDisplayName(type: ItemType): String {
        return when (type) {
            ItemType.PAPER -> "论文"
            ItemType.ARTICLE -> "资料"
            ItemType.COMPETITION -> "竞赛"
            ItemType.INSIGHT -> "动态"
            ItemType.VOICE -> "语音"
        }
    }

    private data class ImportedPage(
        val pageIndex: Int,
        val imagePath: String,
        val ocrResult: OCRResult?,
        val errorMessage: String? = null
    ) {
        fun hasRecognizedSignal(): Boolean {
            val result = ocrResult ?: return false
            return !result.content.isNullOrBlank() ||
                !result.title.isNullOrBlank() ||
                !result.authors.isNullOrBlank() ||
                !result.identifier.isNullOrBlank() ||
                result.referencedLinks.isNotEmpty()
        }
    }

    private data class ScanPayload(
        val title: String,
        val summary: String,
        val markdown: String,
        val metaJson: String?,
        val tags: List<String>,
        val originUrl: String?
    )
}
