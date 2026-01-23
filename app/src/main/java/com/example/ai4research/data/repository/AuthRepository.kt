package com.example.ai4research.data.repository

import com.example.ai4research.core.security.TokenManager
import com.example.ai4research.data.local.dao.UserDao
import com.example.ai4research.data.local.entity.UserEntity
import com.example.ai4research.data.remote.api.NocoApiService
import com.example.ai4research.data.remote.dto.NocoUserCreateDto
import com.example.ai4research.data.remote.dto.NocoUserDto
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val apiService: NocoApiService,
    private val userDao: UserDao,
    private val tokenManager: TokenManager
) {
    /**
     * 用户注册
     */
    suspend fun register(
        email: String,
        password: String,
        username: String,
        phone: String? = null
    ): Result<UserEntity> {
        return try {
            val createDto = NocoUserCreateDto(
                email = email,
                password = password,
                username = username,
                phone = phone
            )
            
            val response = apiService.registerUser(createDto)
            val userEntity = mapDtoToEntity(response)
            
            // 保存到本地数据库
            userDao.insertUser(userEntity)
            
            // 保存认证令牌
            tokenManager.saveAuthToken(response.id.toString())
            
            Result.success(userEntity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 用户登录
     */
    suspend fun login(
        account: String,
        password: String
    ): Result<UserEntity> {
        return try {
            // 构建查询条件：支持邮箱、用户名、手机号登录
            val whereClause = buildLoginWhereClause(account, password)
            
            val response = apiService.loginUser(whereClause)
            
            if (response.list.isEmpty()) {
                return Result.failure(Exception("用户名或密码错误"))
            }
            
            val userDto = response.list.first()
            val userEntity = mapDtoToEntity(userDto)
            
            // 保存到本地数据库
            userDao.insertUser(userEntity)
            
            // 保存认证令牌
            tokenManager.saveAuthToken(userDto.id.toString())
            
            Result.success(userEntity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 生物识别登录
     */
    suspend fun loginWithBiometric(): Result<UserEntity> {
        return try {
            val userId = tokenManager.getAuthToken()
                ?: return Result.failure(Exception("未找到认证信息"))
            
            val userEntity = userDao.getUserById(userId)
                ?: return Result.failure(Exception("用户不存在"))
            
            Result.success(userEntity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 登出
     */
    suspend fun logout() {
        tokenManager.clearAuthToken()
        userDao.deleteAllUsers()
    }
    
    /**
     * 获取当前用户
     */
    suspend fun getCurrentUser(): UserEntity? {
        val userId = tokenManager.getAuthToken() ?: return null
        return userDao.getUserById(userId)
    }
    
    /**
     * 观察当前用户
     */
    fun observeCurrentUser(): Flow<UserEntity?> {
        val userId = tokenManager.getAuthToken() ?: return kotlinx.coroutines.flow.flowOf(null)
        return userDao.observeUserById(userId)
    }
    
    /**
     * 启用生物识别
     */
    suspend fun enableBiometric(userId: String): Result<Unit> {
        return try {
            tokenManager.saveBiometricCredentials(userId)
            
            // 更新远程数据库
            val userDto = apiService.getUserById(userId)
            val updatedDto = userDto.copy(biometricEnabled = true)
            apiService.updateUser(userId, updatedDto)
            
            // 更新本地数据库
            val userEntity = userDao.getUserById(userId)
            if (userEntity != null) {
                val updated = userEntity.copy(biometricEnabled = true)
                userDao.insertUser(updated)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 禁用生物识别
     */
    suspend fun disableBiometric(userId: String): Result<Unit> {
        return try {
            tokenManager.clearBiometricCredentials()
            
            // 更新远程数据库
            val userDto = apiService.getUserById(userId)
            val updatedDto = userDto.copy(biometricEnabled = false)
            apiService.updateUser(userId, updatedDto)
            
            // 更新本地数据库
            val userEntity = userDao.getUserById(userId)
            if (userEntity != null) {
                val updated = userEntity.copy(biometricEnabled = false)
                userDao.insertUser(updated)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Fast login check for startup routing (token only).
     */
    suspend fun isLoggedInFast(): Boolean {
        return tokenManager.getAuthToken() != null
    }
    
    /**
     * 检查是否有生物识别凭证
     */
    fun hasBiometricCredentials(): Boolean {
        return tokenManager.hasBiometricCredentials()
    }
    
    // ==================== Private Helper Methods ====================
    
    private fun buildLoginWhereClause(account: String, password: String): String {
        // NocoDB where clause format: (field1,eq,value1)~and(field2,eq,value2)
        // 支持多种登录方式：邮箱、用户名、手机号
        return when {
            account.contains("@") -> {
                // 邮箱登录
                "(email,eq,$account)~and(password,eq,$password)"
            }
            account.matches(Regex("^\\d+$")) -> {
                // 手机号登录
                "(Phonenumber,eq,$account)~and(password,eq,$password)"
            }
            else -> {
                // 用户名登录
                "(username,eq,$account)~and(password,eq,$password)"
            }
        }
    }
    
    private fun mapDtoToEntity(dto: NocoUserDto): UserEntity {
        return UserEntity(
            id = dto.id.toString(),
            email = dto.email,
            username = dto.username,
            phone = dto.phone,
            avatarUrl = dto.avatarUrl,
            biometricEnabled = dto.biometricEnabled ?: false,
            updatedAt = System.currentTimeMillis()
        )
    }
}
