package com.example.ai4research.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 用户实体 - 本地缓存
 * 用于存储当前登录用户的基本信息（非敏感）
 */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val id: String,  // NocoDB 生成的用户ID
    val email: String,  // 用户邮箱（账号）
    val username: String,  // 用户昵称
    val phone: String? = null,  // 用户手机号
    val avatarUrl: String? = null,  // 头像URL
    val biometricEnabled: Boolean = false,  // 是否开启了生物识别
    val updatedAt: Long = System.currentTimeMillis()  // 最后更新时间
)

