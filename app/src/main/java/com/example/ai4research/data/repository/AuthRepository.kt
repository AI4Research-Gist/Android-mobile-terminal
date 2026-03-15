package com.example.ai4research.data.repository

import com.example.ai4research.core.security.TokenManager
import com.example.ai4research.data.local.dao.ItemDao
import com.example.ai4research.data.local.dao.ProjectDao
import com.example.ai4research.data.local.dao.UserDao
import com.example.ai4research.data.local.entity.UserEntity
import com.example.ai4research.data.remote.api.NocoApiService
import com.example.ai4research.data.remote.dto.NocoUserCreateDto
import com.example.ai4research.data.remote.dto.NocoUserDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val apiService: NocoApiService,
    private val itemDao: ItemDao,
    private val projectDao: ProjectDao,
    private val userDao: UserDao,
    private val tokenManager: TokenManager
) {
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

            userDao.deleteAllUsers()
            userDao.insertUser(userEntity)
            tokenManager.saveAuthToken(response.id.toString())

            Result.success(userEntity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(
        account: String,
        password: String
    ): Result<UserEntity> {
        return try {
            val whereClause = buildLoginWhereClause(account, password)
            val response = apiService.loginUser(whereClause)

            if (response.list.isEmpty()) {
                return Result.failure(Exception("用户名或密码错误"))
            }

            val userDto = response.list.first()
            val userEntity = mapDtoToEntity(userDto)

            userDao.deleteAllUsers()
            userDao.insertUser(userEntity)
            tokenManager.saveAuthToken(userDto.id.toString())

            Result.success(userEntity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginWithBiometric(): Result<UserEntity> {
        return try {
            val userId = tokenManager.getCurrentUserId()
                ?: return Result.failure(Exception("未找到认证信息"))

            val userEntity = userDao.getUserById(userId)
                ?: return Result.failure(Exception("用户不存在"))

            Result.success(userEntity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout() {
        val userId = tokenManager.getCurrentUserId()
        if (!userId.isNullOrBlank()) {
            itemDao.deleteAllItemsByOwner(userId)
            projectDao.deleteAllProjectsByOwner(userId)
        }
        tokenManager.clearAuthToken()
        userDao.deleteAllUsers()
    }

    suspend fun getCurrentUser(): UserEntity? {
        val userId = tokenManager.getCurrentUserId() ?: return null
        return userDao.getUserById(userId)
    }

    fun observeCurrentUser(): Flow<UserEntity?> {
        val userId = tokenManager.getCurrentUserId() ?: return flowOf(null)
        return userDao.observeUserById(userId)
    }

    suspend fun enableBiometric(userId: String): Result<Unit> {
        return try {
            tokenManager.saveBiometricCredentials(userId)

            val userDto = apiService.getUserById(userId)
            val updatedDto = userDto.copy(biometricEnabled = true)
            apiService.updateUser(userId, updatedDto)

            val userEntity = userDao.getUserById(userId)
            if (userEntity != null) {
                userDao.insertUser(userEntity.copy(biometricEnabled = true))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun disableBiometric(userId: String): Result<Unit> {
        return try {
            tokenManager.clearBiometricCredentials()

            val userDto = apiService.getUserById(userId)
            val updatedDto = userDto.copy(biometricEnabled = false)
            apiService.updateUser(userId, updatedDto)

            val userEntity = userDao.getUserById(userId)
            if (userEntity != null) {
                userDao.insertUser(userEntity.copy(biometricEnabled = false))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun isLoggedInFast(): Boolean {
        return tokenManager.getCurrentUserId() != null
    }

    fun hasBiometricCredentials(): Boolean {
        return tokenManager.hasBiometricCredentials()
    }

    private fun buildLoginWhereClause(account: String, password: String): String {
        return when {
            account.contains("@") -> "(email,eq,$account)~and(password,eq,$password)"
            account.matches(Regex("^\\d+$")) -> "(Phonenumber,eq,$account)~and(password,eq,$password)"
            else -> "(username,eq,$account)~and(password,eq,$password)"
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
