package com.example.ai4research.domain.repository

import com.example.ai4research.domain.model.ItemType
import com.example.ai4research.domain.model.ReadStatus
import com.example.ai4research.domain.model.ResearchItem
import kotlinx.coroutines.flow.Flow

/**
 * Item Repository (Domain Interface)
 * SSOT: Room，网络只负责同步/创建/更新后写回 Room。
 */
interface ItemRepository {
    fun observeItems(type: ItemType? = null, query: String? = null): Flow<List<ResearchItem>>

    suspend fun refreshItems(): Result<Unit>

    suspend fun getItem(id: String): ResearchItem?

    suspend fun createUrlItem(
        url: String,
        title: String? = null,
        note: String? = null,
        type: ItemType = ItemType.INSIGHT
    ): Result<ResearchItem>

    /**
     * 本地创建：语音条目（音频文件先落地到本机，后续可扩展上传）
     */
    suspend fun createVoiceItem(
        title: String,
        audioUri: String,
        durationSeconds: Int,
        summary: String? = null
    ): Result<ResearchItem>

    /**
     * 本地创建：图片条目（用于拍照采集/待 OCR）
     */
    suspend fun createImageItem(
        imageUri: String,
        summary: String? = null
    ): Result<ResearchItem>

    suspend fun updateReadStatus(id: String, readStatus: ReadStatus): Result<Unit>

    suspend fun updateItem(
        id: String,
        title: String? = null,
        summary: String? = null,
        content: String? = null,
        tags: List<String>? = null
    ): Result<Unit>

    /**
     * 更新条目的项目归属（同时同步远端关联）
     */
    suspend fun updateItemProject(id: String, projectId: String?): Result<Unit>

    suspend fun deleteItem(id: String): Result<Unit>
}
