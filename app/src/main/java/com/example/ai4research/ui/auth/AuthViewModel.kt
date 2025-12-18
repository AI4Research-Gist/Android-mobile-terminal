package com.example.ai4research.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai4research.data.local.entity.UserEntity
import com.example.ai4research.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 认证 ViewModel
 * 处理登录、注册的 UI 状态和业务逻辑
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    
    // UI 状态
    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()
    
    // 当前用户
    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()
    
    // 是否有生物识别凭证
    private val _hasBiometricCredentials = MutableStateFlow(false)
    val hasBiometricCredentials: StateFlow<Boolean> = _hasBiometricCredentials.asStateFlow()
    
    init {
        checkLoginStatus()
        checkBiometricCredentials()
    }
    
    /**
     * 检查登录状态
     */
    private fun checkLoginStatus() {
        viewModelScope.launch {
            val isLoggedIn = authRepository.isLoggedIn()
            if (isLoggedIn) {
                _currentUser.value = authRepository.getCurrentUser()
            }
        }
    }
    
    /**
     * 检查生物识别凭证
     */
    private fun checkBiometricCredentials() {
        _hasBiometricCredentials.value = authRepository.hasBiometricCredentials()
    }
    
    /**
     * 用户注册
     */
    fun register(email: String, password: String, confirmPassword: String, username: String) {
        // 验证输入
        if (email.isBlank() || password.isBlank() || username.isBlank()) {
            _uiState.value = AuthUiState.Error("请填写所有字段")
            return
        }
        
        if (!isValidEmail(email)) {
            _uiState.value = AuthUiState.Error("邮箱格式不正确")
            return
        }
        
        if (password.length < 6) {
            _uiState.value = AuthUiState.Error("密码至少6位")
            return
        }
        
        if (password != confirmPassword) {
            _uiState.value = AuthUiState.Error("两次密码不一致")
            return
        }
        
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            
            val result = authRepository.register(email, password, username)
            
            result.onSuccess { user ->
                _currentUser.value = user
                _uiState.value = AuthUiState.RegisterSuccess(user)
            }.onFailure { error ->
                _uiState.value = AuthUiState.Error(error.message ?: "注册失败")
            }
        }
    }
    
    /**
     * 用户登录
     */
    fun login(email: String, password: String) {
        // 验证输入
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState.Error("请填写邮箱和密码")
            return
        }
        
        if (!isValidEmail(email)) {
            _uiState.value = AuthUiState.Error("邮箱格式不正确")
            return
        }
        
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            
            val result = authRepository.login(email, password)
            
            result.onSuccess { user ->
                _currentUser.value = user
                _uiState.value = AuthUiState.LoginSuccess(user)
            }.onFailure { error ->
                _uiState.value = AuthUiState.Error(error.message ?: "登录失败")
            }
        }
    }
    
    /**
     * 生物识别登录
     */
    fun biometricLogin() {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            
            val result = authRepository.biometricLogin()
            
            result.onSuccess { user ->
                _currentUser.value = user
                _uiState.value = AuthUiState.LoginSuccess(user)
            }.onFailure { error ->
                _uiState.value = AuthUiState.Error(error.message ?: "生物识别登录失败")
            }
        }
    }
    
    /**
     * 启用生物识别
     */
    fun enableBiometric(password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            
            val result = authRepository.enableBiometric(password)
            
            result.onSuccess {
                _hasBiometricCredentials.value = true
                _uiState.value = AuthUiState.BiometricEnabled
            }.onFailure { error ->
                _uiState.value = AuthUiState.Error(error.message ?: "启用生物识别失败")
            }
        }
    }
    
    /**
     * 禁用生物识别
     */
    fun disableBiometric() {
        viewModelScope.launch {
            val result = authRepository.disableBiometric()
            
            result.onSuccess {
                _hasBiometricCredentials.value = false
                _uiState.value = AuthUiState.BiometricDisabled
            }.onFailure { error ->
                _uiState.value = AuthUiState.Error(error.message ?: "禁用生物识别失败")
            }
        }
    }
    
    /**
     * 登出
     */
    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _currentUser.value = null
            _hasBiometricCredentials.value = false
            _uiState.value = AuthUiState.LogoutSuccess
        }
    }
    
    /**
     * 重置 UI 状态
     */
    fun resetUiState() {
        _uiState.value = AuthUiState.Idle
    }
    
    /**
     * 验证邮箱格式
     */
    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}

/**
 * 认证 UI 状态
 */
sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class LoginSuccess(val user: UserEntity) : AuthUiState()
    data class RegisterSuccess(val user: UserEntity) : AuthUiState()
    object LogoutSuccess : AuthUiState()
    object BiometricEnabled : AuthUiState()
    object BiometricDisabled : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

