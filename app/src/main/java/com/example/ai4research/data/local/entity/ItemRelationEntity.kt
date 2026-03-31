package com.example.ai4research.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "item_relations",
    indices = [
        Index(value = ["owner_user_id", "from_item_id"]),
        Index(value = ["owner_user_id", "to_item_id"]),
        Index(value = ["owner_user_id", "from_item_id", "to_item_id", "relation_type"], unique = true)
    ]
)
data class ItemRelationEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "owner_user_id")
    val ownerUserId: String,

    @ColumnInfo(name = "from_item_id")
    val fromItemId: String,

    @ColumnInfo(name = "to_item_id")
    val toItemId: String,

    @ColumnInfo(name = "relation_type")
    val relationType: String,

    @ColumnInfo(name = "confidence")
    val confidence: Double,

    @ColumnInfo(name = "source")
    val source: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
