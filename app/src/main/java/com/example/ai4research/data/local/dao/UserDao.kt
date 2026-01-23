package com.example.ai4research.data.local.dao

import androidx.room.*
import com.example.ai4research.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

/**
 * 用户数据访问对象
 */
@Dao
interface UserDao {
    /**
     * 插入或更新用户
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)
    
    /**
     * 获取当前用户（假设只有一个登录用户）
     */
    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getCurrentUser(): UserEntity?
    
    /**
     * 获取当前用户（Flow）
     */
    @Query("SELECT * FROM users LIMIT 1")
    fun getCurrentUserFlow(): Flow<UserEntity?>
    
    /**
     * 根据ID获取用户
     */
    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: String): UserEntity?
    
    /**
     * 根据ID观察用户
     */
    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    fun observeUserById(userId: String): Flow<UserEntity?>
    
    /**
     * 根据邮箱获取用户
     */
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?
    
    /**
     * 更新生物识别状态
     */
    @Query("UPDATE users SET biometricEnabled = :enabled WHERE id = :userId")
    suspend fun updateBiometricEnabled(userId: String, enabled: Boolean)
    
    /**
     * 删除所有用户（登出）
     */
    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()
    
    /**
     * 删除特定用户
     */
    @Delete
    suspend fun deleteUser(user: UserEntity)
}

