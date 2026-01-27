package com.example.ai4research.domain.repository

import com.example.ai4research.domain.model.ItemStatus
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
    
    /**
     * 按类型和阅读状态筛选
     */
    fun observeItemsByReadStatus(type: ItemType, readStatus: ReadStatus): Flow<List<ResearchItem>>
    
    /**
     * 按类型和项目筛选
     */
    fun observeItemsByProject(type: ItemType, projectId: String): Flow<List<ResearchItem>>
    
    /**
     * 按类型查询标星条目
     */
    fun observeStarredItems(type: ItemType): Flow<List<ResearchItem>>

    suspend fun refreshItems(): Result<Unit>

    suspend fun getItem(id: String): ResearchItem?

    suspend fun createUrlItem(
        url: String,
        title: String? = null,
        note: String? = null,
        type: ItemType = ItemType.INSIGHT
    ): Result<ResearchItem>
    
    /**
     * 创建完整的研究条目（用于AI解析后的完整数据）
     * @param title 标题
     * @param summary 摘要
     * @param contentMd Markdown格式的详细内容
     * @param originUrl 来源链接
     * @param type 条目类型
     * @param status 状态（默认为已完成）
     * @param metaJson 元数据JSON字符串
     * @param tags 标签列表
     */
    suspend fun createFullItem(
        title: String,
        summary: String,
        contentMd: String,
        originUrl: String?,
        type: ItemType,
        status: ItemStatus = ItemStatus.DONE,
        metaJson: String? = null,
        tags: List<String>? = null
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
    
    suspend fun updateStarred(id: String, isStarred: Boolean): Result<Unit>

    suspend fun updateItem(
        id: String,
        title: String? = null,
        summary: String? = null,
        content: String? = null,
        tags: List<String>? = null,
        metaJson: String? = null
    ): Result<Unit>
    
    /**
     * 更新条目类型（用于用户分类选择后）
     */
    suspend fun updateItemType(id: String, type: ItemType): Result<Unit>

    /**
     * 更新条目的项目归属（同时同步远端关联）
     */
    suspend fun updateItemProject(id: String, projectId: String?): Result<Unit>

    suspend fun deleteItem(id: String): Result<Unit>
}
