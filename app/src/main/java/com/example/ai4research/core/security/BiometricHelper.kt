package com.example.ai4research.core.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 生物识别助手类
 * 处理指纹/面容识别逻辑
 */
@Singleton
class BiometricHelper @Inject constructor() {
    
    /**
     * 检查设备是否支持生物识别
     * @return BiometricStatus
     */
    fun canAuthenticate(activity: FragmentActivity): BiometricStatus {
        val biometricManager = BiometricManager.from(activity)
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.READY
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricStatus.UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NONE_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricStatus.SECURITY_UPDATE_REQUIRED
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> BiometricStatus.UNSUPPORTED
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> BiometricStatus.UNKNOWN
            else -> BiometricStatus.UNKNOWN
        }
    }
    
    /**
     * 显示生物识别提示框
     * @param activity FragmentActivity
     * @param title 标题
     * @param subtitle 副标题
     * @param negativeButtonText 取消按钮文本
     * @param onSuccess 验证成功回调
     * @param onError 验证失败回调
     */
    fun showBiometricPrompt(
        activity: FragmentActivity,
        title: String = "登录 AI4Research",
        subtitle: String = "使用生物识别验证身份",
        negativeButtonText: String = "使用密码",
        onSuccess: () -> Unit,
        onError: (errorCode: Int, errorMessage: String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }
                
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errorCode, errString.toString())
                }
                
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // 验证失败但可以重试，不调用 onError
                }
            }
        )
        
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        
        biometricPrompt.authenticate(promptInfo)
    }
    
    /**
     * 生物识别状态
     */
    enum class BiometricStatus {
        READY,  // 可用
        NO_HARDWARE,  // 无硬件
        UNAVAILABLE,  // 暂时不可用
        NONE_ENROLLED,  // 未录入
        SECURITY_UPDATE_REQUIRED,  // 需要安全更新
        UNSUPPORTED,  // 不支持
        UNKNOWN  // 未知
    }
}

