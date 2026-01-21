package com.example.ai4research.data.repository

import com.example.ai4research.core.security.PasswordHasher
import com.example.ai4research.core.security.TokenManager
import com.example.ai4research.data.local.dao.UserDao
import com.example.ai4research.data.local.entity.UserEntity
import com.example.ai4research.data.remote.api.NocoApiService
import com.example.ai4research.data.remote.dto.NocoUserCreateDto
import com.example.ai4research.data.remote.dto.NocoUserDto
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 认证仓库
 * 处理用户注册、登录、登出逻辑
 */
@Singleton
class AuthRepository @Inject constructor(
    private val api: NocoApiService,
    private val userDao: UserDao,
    private val tokenManager: TokenManager,
    private val passwordHasher: PasswordHasher
) {
    
    /**
     * 用户注册
     * @param email 邮箱
     * @param password 密码（明文）
     * @param username 用户名
     * @param phone 手机号（可选）
     * @return Result<UserEntity>
     */
    suspend fun register(
        email: String,
        password: String,
        username: String,
        phone: String? = null
    ): Result<UserEntity> {
        return try {
            // 1. 检查用户名是否已存在
            val usernameExists = checkUsernameExists(username)
            if (usernameExists) {
                return Result.failure(Exception("用户名已存在"))
            }
            
            // 2. 哈希密码
            val passwordHash = passwordHasher.hashPassword(password)
            
            // 3. 创建用户DTO（只包含必要字段）
            val userCreateDto = NocoUserCreateDto(
                phone = phone,
                email = email,
                passwordHash = passwordHash,
                username = username,
                avatarUrl = null,
                biometricEnabled = false
            )
            
            // 4. 调用 NocoDB API 注册
            val response = api.registerUser(userCreateDto)
            
            // 5. 保存到本地数据库
            val userEntity = UserEntity(
                id = response.id?.toString() ?: throw Exception("注册失败：未返回用户ID"),
                email = response.email,
                username = response.username,
                phone = response.phone,
                avatarUrl = response.avatarUrl,
                biometricEnabled = false
            )
            userDao.insertUser(userEntity)
            
            // 6. 保存 Token
            tokenManager.saveAuthToken(userEntity.id)
            tokenManager.saveUserEmail(email)
            
            Result.success(userEntity)
        } catch (e: retrofit2.HttpException) {
            // 捕获 HTTP 错误并显示详细信息
            val errorBody = e.response()?.errorBody()?.string()
            val errorMessage = "注册失败 (${e.code()}): ${errorBody ?: e.message()}"
            android.util.Log.e("AuthRepository", errorMessage)
            Result.failure(Exception(errorMessage))
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "注册异常: ${e.message}", e)
            Result.failure(Exception("注册失败: ${e.message}"))
        }
    }
    
    /**
     * 检查用户名是否已存在
     * @param username 用户名
     * @return Boolean 是否存在
     */
    suspend fun checkUsernameExists(username: String): Boolean {
        return try {
            val where = "(username,eq,$username)"
            val response = api.checkUsernameExists(where)
            response.list.isNotEmpty()
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "检查用户名失败: ${e.message}", e)
            false
        }
    }
    
    /**
     * 用户登录
     * @param identifier 用户名、手机号或邮箱
     * @param password 密码（明文）
     * @return Result<UserEntity>
     */
    suspend fun login(
        identifier: String,
        password: String
    ): Result<UserEntity> {
        return try {
            // 1. 哈希密码
            val passwordHash = passwordHasher.hashPassword(password)
            
            // 2. 判断 identifier 类型并构建查询条件
            // 尝试按用户名、手机号、邮箱查询
            val whereUsername = "(username,eq,$identifier)"
            val wherePhone = "(Phonenumber,eq,$identifier)"
            val whereEmail = "(email,eq,$identifier)"
            
            // 3. 依次尝试查询用户
            var user: com.example.ai4research.data.remote.dto.NocoUserDto? = null
            
            // 先按用户名查询
            val usernameResponse = api.loginUser(whereUsername)
            if (usernameResponse.list.isNotEmpty()) {
                user = usernameResponse.list.first()
            }
            
            // 如果用户名没找到，按手机号查询
            if (user == null) {
                val phoneResponse = api.loginUser(wherePhone)
                if (phoneResponse.list.isNotEmpty()) {
                    user = phoneResponse.list.first()
                }
            }
            
            // 如果手机号也没找到，按邮箱查询
            if (user == null) {
                val emailResponse = api.loginUser(whereEmail)
                if (emailResponse.list.isNotEmpty()) {
                    user = emailResponse.list.first()
                }
            }
            
            // 4. 验证用户是否存在
            if (user == null) {
                return Result.failure(Exception("用户不存在"))
            }
            
            // 5. 验证密码
            if (user.passwordHash != passwordHash) {
                return Result.failure(Exception("密码错误"))
            }
            
            // 6. 保存到本地数据库
            val userEntity = UserEntity(
                id = user.id?.toString() ?: throw Exception("用户ID为空"),
                email = user.email,
                username = user.username,
                phone = user.phone,
                avatarUrl = user.avatarUrl,
                biometricEnabled = user.biometricEnabled ?: false
            )
            userDao.insertUser(userEntity)
            
            // 7. 保存 Token
            tokenManager.saveAuthToken(userEntity.id)
            tokenManager.saveUserEmail(user.email)
            
            Result.success(userEntity)
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "登录失败: ${e.message}", e)
            Result.failure(Exception("登录失败: ${e.message}"))
        }
    }
    
    /**
     * 生物识别登录
     * 使用已保存的邮箱和密码哈希自动登录
     */
    suspend fun biometricLogin(): Result<UserEntity> {
        return try {
            val email = tokenManager.getUserEmail() 
                ?: return Result.failure(Exception("未找到保存的邮箱"))
            val passwordHash = tokenManager.getPasswordHash() 
                ?: return Result.failure(Exception("未找到保存的密码"))
            
            // 调用 NocoDB API 查询用户
            val where = "(email,eq,$email)"
            val response = api.loginUser(where)
            
            if (response.list.isEmpty()) {
                return Result.failure(Exception("用户不存在"))
            }
            
            val user = response.list.first()
            
            // 验证密码哈希
            if (user.passwordHash != passwordHash) {
                return Result.failure(Exception("凭证已失效"))
            }
            
            // 保存到本地数据库
            val userEntity = UserEntity(
                id = user.id?.toString() ?: throw Exception("用户ID为空"),
                email = user.email,
                username = user.username,
                avatarUrl = user.avatarUrl,
                biometricEnabled = true
            )
            userDao.insertUser(userEntity)
            
            // 更新 Token
            tokenManager.saveAuthToken(userEntity.id)
            
            Result.success(userEntity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 启用生物识别
     * @param password 用户当前密码（用于验证）
     */
    suspend fun enableBiometric(password: String): Result<Unit> {
        return try {
            val passwordHash = passwordHasher.hashPassword(password)
            tokenManager.savePasswordHash(passwordHash)
            
            // 更新本地数据库
            val user = userDao.getCurrentUser()
            if (user != null) {
                userDao.updateBiometricEnabled(user.id, true)
                
                // 更新远程数据库
                val userDto = NocoUserDto(
                    email = user.email,
                    passwordHash = passwordHash,
                    username = user.username,
                    avatarUrl = user.avatarUrl,
                    biometricEnabled = true
                )
                api.updateUser(user.id, userDto)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 禁用生物识别
     */
    suspend fun disableBiometric(): Result<Unit> {
        return try {
            tokenManager.clearBiometricCredentials()
            
            // 更新本地数据库
            val user = userDao.getCurrentUser()
            if (user != null) {
                userDao.updateBiometricEnabled(user.id, false)
                
                // 更新远程数据库（保持密码哈希，只改状态）
                val passwordHash = tokenManager.getPasswordHash() ?: ""
                val userDto = NocoUserDto(
                    email = user.email,
                    passwordHash = passwordHash,
                    username = user.username,
                    avatarUrl = user.avatarUrl,
                    biometricEnabled = false
                )
                api.updateUser(user.id, userDto)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 登出
     */
    suspend fun logout() {
        tokenManager.clearAllTokens()
        userDao.deleteAllUsers()
    }
    
    /**
     * 获取当前用户
     */
    suspend fun getCurrentUser(): UserEntity? {
        return userDao.getCurrentUser()
    }
    
    /**
     * 获取当前用户（Flow）
     */
    fun getCurrentUserFlow(): Flow<UserEntity?> {
        return userDao.getCurrentUserFlow()
    }
    
    /**
     * 检查是否已登录
     */
    suspend fun isLoggedIn(): Boolean {
        return tokenManager.getAuthToken() != null && getCurrentUser() != null
    }
    
    /**
     * 检查是否有生物识别凭证
     */
    fun hasBiometricCredentials(): Boolean {
        return tokenManager.hasBiometricCredentials()
    }
}

