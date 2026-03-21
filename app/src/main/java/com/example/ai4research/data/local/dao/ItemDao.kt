package com.example.ai4research.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.ai4research.data.local.entity.ItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE owner_user_id = :ownerUserId ORDER BY created_at DESC")
    fun observeAllItems(ownerUserId: String): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE owner_user_id = :ownerUserId AND type = :type ORDER BY created_at DESC")
    fun observeItemsByType(ownerUserId: String, type: String): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE owner_user_id = :ownerUserId AND status = :status ORDER BY created_at DESC")
    fun observeItemsByStatus(ownerUserId: String, status: String): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE owner_user_id = :ownerUserId AND read_status = :readStatus ORDER BY created_at DESC")
    fun observeItemsByReadStatus(ownerUserId: String, readStatus: String): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE owner_user_id = :ownerUserId AND project_id = :projectId ORDER BY created_at DESC")
    fun observeItemsByProject(ownerUserId: String, projectId: String): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE owner_user_id = :ownerUserId AND type = :type AND read_status LIKE :readStatus || '%' ORDER BY created_at DESC")
    fun observeItemsByTypeAndReadStatus(ownerUserId: String, type: String, readStatus: String): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE owner_user_id = :ownerUserId AND type = :type AND project_id = :projectId ORDER BY created_at DESC")
    fun observeItemsByTypeAndProject(ownerUserId: String, type: String, projectId: String): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE owner_user_id = :ownerUserId AND type = :type AND is_starred = 1 ORDER BY created_at DESC")
    fun observeStarredItemsByType(ownerUserId: String, type: String): Flow<List<ItemEntity>>

    @Query("UPDATE items SET is_starred = :isStarred WHERE id = :id AND owner_user_id = :ownerUserId")
    suspend fun updateStarred(ownerUserId: String, id: String, isStarred: Boolean)

    @Query("SELECT * FROM items WHERE id = :id AND owner_user_id = :ownerUserId")
    suspend fun getItemById(ownerUserId: String, id: String): ItemEntity?

    @Query("SELECT * FROM items WHERE owner_user_id = :ownerUserId ORDER BY created_at DESC")
    suspend fun getItemsByOwner(ownerUserId: String): List<ItemEntity>

    @Query("SELECT * FROM items WHERE owner_user_id = :ownerUserId AND (title LIKE '%' || :query || '%' OR summary LIKE '%' || :query || '%') ORDER BY created_at DESC")
    fun searchItems(ownerUserId: String, query: String): Flow<List<ItemEntity>>

    @Query(
        """
        SELECT * FROM items
        WHERE owner_user_id = :ownerUserId
          AND type = :type
          AND (title LIKE '%' || :query || '%' OR summary LIKE '%' || :query || '%')
        ORDER BY created_at DESC
        """
    )
    fun searchItemsByType(ownerUserId: String, type: String, query: String): Flow<List<ItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ItemEntity>)

    @Update
    suspend fun updateItem(item: ItemEntity)

    @Query("UPDATE items SET read_status = :readStatus WHERE id = :id AND owner_user_id = :ownerUserId")
    suspend fun updateReadStatus(ownerUserId: String, id: String, readStatus: String)

    @Query("UPDATE items SET project_id = :projectId, project_name = :projectName WHERE id = :id AND owner_user_id = :ownerUserId")
    suspend fun updateProject(ownerUserId: String, id: String, projectId: String?, projectName: String?)

    @Delete
    suspend fun deleteItem(item: ItemEntity)

    @Query("DELETE FROM items WHERE id = :id AND owner_user_id = :ownerUserId")
    suspend fun deleteItemById(ownerUserId: String, id: String)

    @Query("DELETE FROM items WHERE owner_user_id = :ownerUserId")
    suspend fun deleteAllItemsByOwner(ownerUserId: String)
}
