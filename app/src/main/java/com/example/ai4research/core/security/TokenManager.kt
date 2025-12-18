package com.example.ai4research.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Token 管理器
 * 使用 EncryptedSharedPreferences 安全存储敏感信息
 */
@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    /**
     * 保存认证 Token
     */
    fun saveAuthToken(token: String) {
        sharedPreferences.edit().putString(KEY_AUTH_TOKEN, token).apply()
    }
    
    /**
     * 获取认证 Token
     */
    fun getAuthToken(): String? {
        return sharedPreferences.getString(KEY_AUTH_TOKEN, null)
    }
    
    /**
     * 保存密码哈希（用于生物识别快速登录）
     */
    fun savePasswordHash(hash: String) {
        sharedPreferences.edit().putString(KEY_PASSWORD_HASH, hash).apply()
    }
    
    /**
     * 获取密码哈希
     */
    fun getPasswordHash(): String? {
        return sharedPreferences.getString(KEY_PASSWORD_HASH, null)
    }
    
    /**
     * 保存用户邮箱（用于生物识别登录）
     */
    fun saveUserEmail(email: String) {
        sharedPreferences.edit().putString(KEY_USER_EMAIL, email).apply()
    }
    
    /**
     * 获取用户邮箱
     */
    fun getUserEmail(): String? {
        return sharedPreferences.getString(KEY_USER_EMAIL, null)
    }
    
    /**
     * 检查是否有生物识别凭证
     */
    fun hasBiometricCredentials(): Boolean {
        return getUserEmail() != null && getPasswordHash() != null
    }
    
    /**
     * 清除所有 Token（登出）
     */
    fun clearAllTokens() {
        sharedPreferences.edit().clear().apply()
    }
    
    /**
     * 清除生物识别凭证
     */
    fun clearBiometricCredentials() {
        sharedPreferences.edit()
            .remove(KEY_PASSWORD_HASH)
            .apply()
    }
    
    companion object {
        private const val PREFS_NAME = "ai4research_secure_prefs"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_PASSWORD_HASH = "password_hash"
        private const val KEY_USER_EMAIL = "user_email"
    }
}

