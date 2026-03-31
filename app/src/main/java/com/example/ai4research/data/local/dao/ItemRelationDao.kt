package com.example.ai4research.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.ai4research.data.local.entity.ItemRelationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemRelationDao {
    @Query(
        """
        SELECT * FROM item_relations
        WHERE owner_user_id = :ownerUserId
          AND (from_item_id = :itemId OR to_item_id = :itemId)
        ORDER BY created_at DESC
        """
    )
    fun observeRelationsForItem(ownerUserId: String, itemId: String): Flow<List<ItemRelationEntity>>

    @Query(
        """
        SELECT * FROM item_relations
        WHERE owner_user_id = :ownerUserId
          AND (from_item_id = :itemId OR to_item_id = :itemId)
        ORDER BY created_at DESC
        """
    )
    suspend fun getRelationsForItem(ownerUserId: String, itemId: String): List<ItemRelationEntity>

    @Query(
        """
        SELECT * FROM item_relations
        WHERE owner_user_id = :ownerUserId
          AND (from_item_id IN (:itemIds) OR to_item_id IN (:itemIds))
        ORDER BY created_at DESC
        """
    )
    suspend fun getRelationsForItems(ownerUserId: String, itemIds: List<String>): List<ItemRelationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelation(relation: ItemRelationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelations(relations: List<ItemRelationEntity>)

    @Query(
        """
        DELETE FROM item_relations
        WHERE owner_user_id = :ownerUserId
          AND from_item_id = :fromItemId
          AND relation_type = :relationType
        """
    )
    suspend fun deleteOutgoingRelationsByType(ownerUserId: String, fromItemId: String, relationType: String)

    @Query("DELETE FROM item_relations WHERE owner_user_id = :ownerUserId AND id = :relationId")
    suspend fun deleteRelationById(ownerUserId: String, relationId: String)

    @Query(
        """
        DELETE FROM item_relations
        WHERE owner_user_id = :ownerUserId
          AND (from_item_id = :itemId OR to_item_id = :itemId)
        """
    )
    suspend fun deleteRelationsForItem(ownerUserId: String, itemId: String)
}
