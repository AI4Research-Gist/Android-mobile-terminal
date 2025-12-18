package com.example.ai4research.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * NocoDB 用户数据传输对象
 * 注意：注册时不发送 id, CreatedAt, UpdatedAt 等系统字段
 */
@Serializable
data class NocoUserDto(
    @SerialName("Id")
    val id: Int? = null,  // NocoDB 自动生成的ID（仅用于响应）
    
    @SerialName("email")
    val email: String,
    
    @SerialName("password_hash")
    val passwordHash: String,
    
    @SerialName("username")
    val username: String,
    
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    
    @SerialName("biometric_enabled")
    val biometricEnabled: Boolean? = null,  // 改为 null，让 NocoDB 使用默认值
    
    @SerialName("CreatedAt")
    val createdAt: String? = null,
    
    @SerialName("UpdatedAt")
    val updatedAt: String? = null
)

/**
 * 用于注册的简化数据类（只包含必要字段）
 */
@Serializable
data class NocoUserCreateDto(
    @SerialName("email")
    val email: String,
    
    @SerialName("password_hash")
    val passwordHash: String,
    
    @SerialName("username")
    val username: String,
    
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    
    @SerialName("biometric_enabled")
    val biometricEnabled: Boolean = false
)

/**
 * 用户列表响应
 */
@Serializable
data class NocoUserListResponse(
    @SerialName("list")
    val list: List<NocoUserDto>,
    
    @SerialName("pageInfo")
    val pageInfo: PageInfo
)

// PageInfo 已在 NocoItemDto.kt 中定义

