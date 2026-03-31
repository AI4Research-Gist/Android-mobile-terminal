package com.example.ai4research.domain.model

import java.util.Date

data class ItemRelation(
    val id: String,
    val fromItemId: String,
    val toItemId: String,
    val relationType: RelationType,
    val confidence: Double,
    val source: String,
    val createdAt: Date
)

enum class RelationType {
    DUPLICATE_OF,
    ARTICLE_MENTIONS_PAPER,
    ARTICLE_RELATED_PAPER,
    INSIGHT_REFERENCES_ITEM;

    fun toStorageValue(): String = name.lowercase()

    companion object {
        fun fromStorageValue(value: String): RelationType {
            return when (value.trim().lowercase()) {
                "duplicate_of" -> DUPLICATE_OF
                "article_mentions_paper" -> ARTICLE_MENTIONS_PAPER
                "article_related_paper" -> ARTICLE_RELATED_PAPER
                "insight_references_item" -> INSIGHT_REFERENCES_ITEM
                else -> ARTICLE_RELATED_PAPER
            }
        }
    }
}

data class ItemConnection(
    val relation: ItemRelation,
    val item: ResearchItem
)
