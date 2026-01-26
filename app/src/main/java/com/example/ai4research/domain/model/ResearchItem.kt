package com.example.ai4research.domain.model

import java.util.Date

/**
 * Domain Layer - 纯业务模型
 * UI 层使用的清洁数据类
 */
data class ResearchItem(
    val id: String,
    val type: ItemType,
    val title: String,
    val summary: String,
    val contentMarkdown: String,
    val originUrl: String?,
    val audioUrl: String?,
    val status: ItemStatus,
    val readStatus: ReadStatus,
    val isStarred: Boolean = false,
    val projectId: String?,
    val projectName: String?,
    val metaData: ItemMetaData?,
    val createdAt: Date
)

/**
 * Item 类型枚举
 */
enum class ItemType {
    PAPER,
    COMPETITION,
    INSIGHT,
    VOICE;
    
    companion object {
        fun fromString(value: String): ItemType {
            return when (value.lowercase()) {
                "paper" -> PAPER
                "competition" -> COMPETITION
                "insight" -> INSIGHT
                "voice" -> VOICE
                else -> INSIGHT
            }
        }
    }
    
    fun toServerString(): String {
        return this.name.lowercase()
    }
}

/**
 * Item 状态枚举
 */
enum class ItemStatus {
    PROCESSING,
    DONE,
    FAILED;
    
    companion object {
        fun fromString(value: String): ItemStatus {
            val normalized = value.trim().lowercase()
            return when {
                normalized.startsWith("processing") -> PROCESSING
                normalized.startsWith("done") -> DONE
                normalized.startsWith("failed") -> FAILED
                else -> PROCESSING
            }
        }
    }
    
    fun toServerString(): String {
        return when (this) {
            PROCESSING -> "processing (解析中)"
            DONE -> "done (完成)"
            FAILED -> "failed (失败)"
        }
    }
}

/**
 * 阅读状态枚举
 */
enum class ReadStatus {
    UNREAD,
    READING,
    READ;
    
    companion object {
        fun fromString(value: String): ReadStatus {
            val normalized = value.trim().lowercase()
            return when {
                normalized.startsWith("unread") -> UNREAD
                normalized.startsWith("reading") -> READING
                normalized.startsWith("read") -> READ
                else -> UNREAD
            }
        }
    }
    
    fun toServerString(): String {
        return when (this) {
            UNREAD -> "unread (未读)"
            READING -> "reading (在读)"
            READ -> "read (已读)"
        }
    }
}

/**
 * 元数据 - 不同类型的 Item 有不同的元数据结构
 */
sealed class ItemMetaData {
    /**
     * 论文元数据
     */
    data class PaperMeta(
        val authors: List<String>,
        val conference: String?,
        val year: Int?,
        val tags: List<String>
    ) : ItemMetaData()
    
    /**
     * 比赛元数据（时间线）
     */
    data class CompetitionMeta(
        val timeline: List<TimelineEvent>? = null,
        val prizePool: String? = null,
        val organizer: String? = null
    ) : ItemMetaData()
    
    /**
     * 灵感元数据
     */
    data class InsightMeta(
        val tags: List<String>
    ) : ItemMetaData()
    
    /**
     * 语音元数据
     */
    data class VoiceMeta(
        val duration: Int,  // 秒
        val transcription: String?  // AI 转录文本
    ) : ItemMetaData()
}

/**
 * 时间线事件（用于比赛）
 */
data class TimelineEvent(
    val name: String,
    val date: Date,
    val isPassed: Boolean
)

/**
 * 项目（用于分组）
 */
data class Project(
    val id: String,
    val name: String,
    val description: String?
)

