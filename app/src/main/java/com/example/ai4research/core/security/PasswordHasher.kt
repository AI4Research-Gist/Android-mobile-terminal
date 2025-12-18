package com.example.ai4research.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 密码哈希工具类
 * 使用 SHA-256 + 盐值进行哈希
 */
@Singleton
class PasswordHasher @Inject constructor() {
    
    /**
     * 哈希密码（使用固定盐值以便在客户端验证）
     * 注意：生产环境应该在服务端处理密码哈希
     */
    fun hashPassword(password: String): String {
        val salt = FIXED_SALT  // 使用固定盐值
        val saltedPassword = password + salt
        return sha256(saltedPassword)
    }
    
    /**
     * SHA-256 哈希
     */
    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * 验证密码
     */
    fun verifyPassword(password: String, hash: String): Boolean {
        return hashPassword(password) == hash
    }
    
    companion object {
        // 固定盐值（生产环境应该每个用户使用不同的盐值，存储在服务端）
        private const val FIXED_SALT = "AI4Research_Salt_2024"
    }
}

