package com.example.ai4research.data.local.dao

import androidx.room.*
import com.example.ai4research.data.local.entity.ItemEntity
import kotlinx.coroutines.flow.Flow

/**
 * Item DAO - 数据访问对象
 * 使用 Flow 实现响应式数据流
 */
@Dao
interface ItemDao {
    /**
     * 观察所有 Items（主要数据源）
     */
    @Query("SELECT * FROM items ORDER BY created_at DESC")
    fun observeAllItems(): Flow<List<ItemEntity>>
    
    /**
     * 根据类型过滤
     */
    @Query("SELECT * FROM items WHERE type = :type ORDER BY created_at DESC")
    fun observeItemsByType(type: String): Flow<List<ItemEntity>>
    
    /**
     * 根据状态过滤
     */
    @Query("SELECT * FROM items WHERE status = :status ORDER BY created_at DESC")
    fun observeItemsByStatus(status: String): Flow<List<ItemEntity>>
    
    /**
     * 根据阅读状态过滤
     */
    @Query("SELECT * FROM items WHERE read_status = :readStatus ORDER BY created_at DESC")
    fun observeItemsByReadStatus(readStatus: String): Flow<List<ItemEntity>>
    
    /**
     * 根据项目过滤
     */
    @Query("SELECT * FROM items WHERE project_id = :projectId ORDER BY created_at DESC")
    fun observeItemsByProject(projectId: String): Flow<List<ItemEntity>>
    
    /**
     * 获取单个 Item
     */
    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getItemById(id: String): ItemEntity?
    
    /**
     * 搜索（标题和摘要）
     */
    @Query("SELECT * FROM items WHERE title LIKE '%' || :query || '%' OR summary LIKE '%' || :query || '%' ORDER BY created_at DESC")
    fun searchItems(query: String): Flow<List<ItemEntity>>

    /**
     * 搜索 + 类型过滤（标题和摘要）
     */
    @Query(
        """
        SELECT * FROM items
        WHERE type = :type AND (title LIKE '%' || :query || '%' OR summary LIKE '%' || :query || '%')
        ORDER BY created_at DESC
        """
    )
    fun searchItemsByType(type: String, query: String): Flow<List<ItemEntity>>
    
    /**
     * 插入或替换
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ItemEntity)
    
    /**
     * 批量插入或替换
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ItemEntity>)
    
    /**
     * 更新 Item
     */
    @Update
    suspend fun updateItem(item: ItemEntity)
    
    /**
     * 更新阅读状态
     */
    @Query("UPDATE items SET read_status = :readStatus WHERE id = :id")
    suspend fun updateReadStatus(id: String, readStatus: String)
    
    /**
     * 更新项目归属
     */
    @Query("UPDATE items SET project_id = :projectId, project_name = :projectName WHERE id = :id")
    suspend fun updateProject(id: String, projectId: String, projectName: String)
    
    /**
     * 删除 Item
     */
    @Delete
    suspend fun deleteItem(item: ItemEntity)

    /**
     * 按 id 删除
     */
    @Query("DELETE FROM items WHERE id = :id")
    suspend fun deleteItemById(id: String)
    
    /**
     * 清空所有数据（慎用）
     */
    @Query("DELETE FROM items")
    suspend fun deleteAllItems()
}

