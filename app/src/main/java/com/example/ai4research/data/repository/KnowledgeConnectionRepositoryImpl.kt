package com.example.ai4research.data.repository

import com.example.ai4research.core.security.TokenManager
import com.example.ai4research.data.local.dao.ItemDao
import com.example.ai4research.data.local.dao.ItemRelationDao
import com.example.ai4research.data.local.entity.ItemRelationEntity
import com.example.ai4research.data.mapper.ItemMapper
import com.example.ai4research.data.mapper.ItemRelationMapper
import com.example.ai4research.domain.model.ItemConnection
import com.example.ai4research.domain.model.ItemType
import com.example.ai4research.domain.model.RelationType
import com.example.ai4research.domain.repository.KnowledgeConnectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KnowledgeConnectionRepositoryImpl @Inject constructor(
    private val itemDao: ItemDao,
    private val itemRelationDao: ItemRelationDao,
    private val tokenManager: TokenManager
) : KnowledgeConnectionRepository {

    private fun currentUserId(): String? = tokenManager.getCurrentUserId()

    override fun observeConnectionsForItem(itemId: String): Flow<List<ItemConnection>> {
        val ownerUserId = currentUserId() ?: return flowOf(emptyList())
        return combine(
            itemRelationDao.observeRelationsForItem(ownerUserId, itemId),
            itemDao.observeAllItems(ownerUserId)
        ) { relationEntities, itemEntities ->
            val itemMap = itemEntities
                .map(ItemMapper::entityToDomain)
                .associateBy { it.id }
            relationEntities.mapNotNull { entity ->
                val relation = ItemRelationMapper.entityToDomain(entity)
                val otherItemId = if (relation.fromItemId == itemId) relation.toItemId else relation.fromItemId
                itemMap[otherItemId]?.let { relatedItem ->
                    ItemConnection(relation = relation, item = relatedItem)
                }
            }
        }
    }

    override suspend fun getConnectionsForItem(itemId: String): List<ItemConnection> {
        val ownerUserId = currentUserId() ?: return emptyList()
        val relations = itemRelationDao.getRelationsForItem(ownerUserId, itemId)
        val itemMap = itemDao.getItemsByOwner(ownerUserId)
            .map(ItemMapper::entityToDomain)
            .associateBy { it.id }

        return relations.mapNotNull { entity ->
            val relation = ItemRelationMapper.entityToDomain(entity)
            val otherItemId = if (relation.fromItemId == itemId) relation.toItemId else relation.fromItemId
            itemMap[otherItemId]?.let { relatedItem ->
                ItemConnection(relation = relation, item = relatedItem)
            }
        }
    }

    override suspend fun rebuildAutoConnectionsForItem(itemId: String): Result<Unit> {
        val ownerUserId = currentUserId()
            ?: return Result.failure(IllegalStateException("User must be logged in"))

        return try {
            val allItems = itemDao.getItemsByOwner(ownerUserId).map(ItemMapper::entityToDomain)
            val item = allItems.firstOrNull { it.id == itemId }
                ?: return Result.failure(IllegalArgumentException("Item not found"))

            val plannedRelations = KnowledgeConnectionPlanner.planForItem(item, allItems)
            clearAutoRelations(ownerUserId, item)

            if (plannedRelations.isNotEmpty()) {
                itemRelationDao.insertRelations(
                    plannedRelations.map { planned ->
                        ItemRelationEntity(
                            id = UUID.randomUUID().toString(),
                            ownerUserId = ownerUserId,
                            fromItemId = item.id,
                            toItemId = planned.toItemId,
                            relationType = planned.relationType.toStorageValue(),
                            confidence = planned.confidence,
                            source = planned.source,
                            createdAt = System.currentTimeMillis()
                        )
                    }
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun replaceInsightConnections(insightId: String, targetItemIds: List<String>): Result<Unit> {
        val ownerUserId = currentUserId()
            ?: return Result.failure(IllegalStateException("User must be logged in"))

        return try {
            val sourceItem = itemDao.getItemById(ownerUserId, insightId)
                ?: return Result.failure(IllegalArgumentException("Insight item not found"))
            if (ItemType.fromString(sourceItem.type) != ItemType.INSIGHT) {
                return Result.failure(IllegalArgumentException("Only insight items can replace insight connections"))
            }

            val validTargetIds = targetItemIds
                .map(String::trim)
                .filter { it.isNotBlank() && it != insightId }
                .distinct()
                .filter { targetId -> itemDao.getItemById(ownerUserId, targetId) != null }

            itemRelationDao.deleteOutgoingRelationsByType(
                ownerUserId = ownerUserId,
                fromItemId = insightId,
                relationType = RelationType.INSIGHT_REFERENCES_ITEM.toStorageValue()
            )

            if (validTargetIds.isNotEmpty()) {
                itemRelationDao.insertRelations(
                    validTargetIds.map { targetId ->
                        ItemRelationEntity(
                            id = UUID.randomUUID().toString(),
                            ownerUserId = ownerUserId,
                            fromItemId = insightId,
                            toItemId = targetId,
                            relationType = RelationType.INSIGHT_REFERENCES_ITEM.toStorageValue(),
                            confidence = 1.0,
                            source = "manual",
                            createdAt = System.currentTimeMillis()
                        )
                    }
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteConnectionsForItem(itemId: String): Result<Unit> {
        val ownerUserId = currentUserId()
            ?: return Result.failure(IllegalStateException("User must be logged in"))

        return try {
            itemRelationDao.deleteRelationsForItem(ownerUserId, itemId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun clearAutoRelations(ownerUserId: String, item: com.example.ai4research.domain.model.ResearchItem) {
        when (item.type) {
            ItemType.PAPER -> {
                itemRelationDao.deleteOutgoingRelationsByType(
                    ownerUserId = ownerUserId,
                    fromItemId = item.id,
                    relationType = RelationType.DUPLICATE_OF.toStorageValue()
                )
            }

            ItemType.ARTICLE -> {
                itemRelationDao.deleteOutgoingRelationsByType(
                    ownerUserId = ownerUserId,
                    fromItemId = item.id,
                    relationType = RelationType.ARTICLE_MENTIONS_PAPER.toStorageValue()
                )
                itemRelationDao.deleteOutgoingRelationsByType(
                    ownerUserId = ownerUserId,
                    fromItemId = item.id,
                    relationType = RelationType.ARTICLE_RELATED_PAPER.toStorageValue()
                )
            }

            else -> Unit
        }
    }
}
