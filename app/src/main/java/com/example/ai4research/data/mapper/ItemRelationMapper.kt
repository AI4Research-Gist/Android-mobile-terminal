package com.example.ai4research.data.mapper

import com.example.ai4research.data.local.entity.ItemRelationEntity
import com.example.ai4research.domain.model.ItemRelation
import com.example.ai4research.domain.model.RelationType
import java.util.Date

object ItemRelationMapper {
    fun entityToDomain(entity: ItemRelationEntity): ItemRelation {
        return ItemRelation(
            id = entity.id,
            fromItemId = entity.fromItemId,
            toItemId = entity.toItemId,
            relationType = RelationType.fromStorageValue(entity.relationType),
            confidence = entity.confidence,
            source = entity.source,
            createdAt = Date(entity.createdAt)
        )
    }

    fun domainToEntity(relation: ItemRelation, ownerUserId: String): ItemRelationEntity {
        return ItemRelationEntity(
            id = relation.id,
            ownerUserId = ownerUserId,
            fromItemId = relation.fromItemId,
            toItemId = relation.toItemId,
            relationType = relation.relationType.toStorageValue(),
            confidence = relation.confidence,
            source = relation.source,
            createdAt = relation.createdAt.time
        )
    }
}
