package com.example.ai4research.domain.model

import java.util.Date

data class ResearchItem(
    val id: String,
    val type: ItemType,
    val title: String,
    val summary: String,
    val note: String? = null,
    val contentMarkdown: String,
    val originUrl: String?,
    val audioUrl: String?,
    val status: ItemStatus,
    val readStatus: ReadStatus,
    val isStarred: Boolean = false,
    val projectId: String?,
    val projectName: String?,
    val metaData: ItemMetaData?,
    val rawMetaJson: String? = null,
    val createdAt: Date
)

enum class ItemType {
    PAPER,
    ARTICLE,
    COMPETITION,
    INSIGHT,
    VOICE;

    companion object {
        fun fromString(value: String): ItemType {
            return when (value.lowercase()) {
                "paper" -> PAPER
                "article" -> ARTICLE
                "competition" -> COMPETITION
                "insight" -> INSIGHT
                "voice" -> VOICE
                else -> INSIGHT
            }
        }
    }

    fun toServerString(): String = name.lowercase()
}

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

sealed class ItemMetaData {
    data class PaperMeta(
        val authors: List<String> = emptyList(),
        val conference: String? = null,
        val year: Int? = null,
        val tags: List<String> = emptyList(),
        val source: String? = null,
        val identifier: String? = null,
        val domainTags: List<String> = emptyList(),
        val keywords: List<String> = emptyList(),
        val methodTags: List<String> = emptyList(),
        val dedupKey: String? = null,
        val summaryShort: String? = null,
        val summaryEn: String? = null,
        val summaryZh: String? = null
    ) : ItemMetaData()

    data class ArticleMeta(
        val platform: String? = null,
        val accountName: String? = null,
        val author: String? = null,
        val publishDate: String? = null,
        val identifier: String? = null,
        val summaryShort: String? = null,
        val keywords: List<String> = emptyList(),
        val topicTags: List<String> = emptyList(),
        val corePoints: List<String> = emptyList(),
        val referencedLinks: List<String> = emptyList(),
        val paperCandidates: List<ArticlePaperCandidate> = emptyList()
    ) : ItemMetaData()

    data class CompetitionMeta(
        val timeline: List<TimelineEvent>? = null,
        val prizePool: String? = null,
        val organizer: String? = null,
        val deadline: String? = null,
        val theme: String? = null,
        val competitionType: String? = null,
        val website: String? = null,
        val registrationUrl: String? = null
    ) : ItemMetaData()

    data class InsightMeta(
        val tags: List<String>
    ) : ItemMetaData()

    data class VoiceMeta(
        val duration: Int,
        val transcription: String?
    ) : ItemMetaData()
}

data class TimelineEvent(
    val name: String,
    val date: Date,
    val isPassed: Boolean
)

data class ArticlePaperCandidate(
    val url: String,
    val label: String? = null,
    val kind: String = "unknown"
)

data class Project(
    val id: String,
    val name: String,
    val description: String?
)
