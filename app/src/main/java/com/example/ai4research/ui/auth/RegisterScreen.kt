package com.example.ai4research.ui.auth

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ai4research.core.security.BiometricHelper
import com.example.ai4research.core.theme.IOSTheme
import com.example.ai4research.ui.components.IOSButton
import com.example.ai4research.ui.components.IOSTextField

@Composable
fun RegisterScreen(
    onNavigateToLogin: () -> Unit,
    onRegisterSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
    biometricHelper: BiometricHelper = BiometricHelper()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val iosColors = IOSTheme.colors

    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showBiometricDialog by remember { mutableStateOf(false) }

    // Handle UI State
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is AuthUiState.RegisterSuccess -> {
                Toast.makeText(context, "注册成功!", Toast.LENGTH_SHORT).show()
                // Ask for biometric enrollment
                if (context is FragmentActivity) {
                    val status = biometricHelper.canAuthenticate(context)
                    if (status == BiometricHelper.BiometricStatus.READY) {
                        showBiometricDialog = true
                    } else {
                        onRegisterSuccess()
                    }
                } else {
                    onRegisterSuccess()
                }
                viewModel.resetUiState()
            }
            is AuthUiState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                viewModel.resetUiState()
            }
            is AuthUiState.BiometricEnabled -> {
                Toast.makeText(context, "生物识别登录已启用", Toast.LENGTH_SHORT).show()
                onRegisterSuccess()
                viewModel.resetUiState()
            }
            else -> {}
        }
    }

    if (showBiometricDialog) {
        AlertDialog(
            onDismissRequest = {
                showBiometricDialog = false
                onRegisterSuccess()
            },
            title = { Text("启用生物识别登录?") },
            text = { Text("使用指纹或面部识别以便下次快速登录。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBiometricDialog = false
                        if (context is FragmentActivity) {
                            biometricHelper.showBiometricPrompt(
                                activity = context,
                                onSuccess = { viewModel.enableBiometric(password) },
                                onError = { _, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    onRegisterSuccess()
                                }
                            )
                        }
                    }
                ) { Text("启用") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBiometricDialog = false
                    onRegisterSuccess()
                }) { Text("跳过", color = iosColors.secondaryLabel) }
            },
            containerColor = iosColors.secondarySystemBackground
        )
    }

    // Modern Tech Background Gradient
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            iosColors.systemBackground,
            iosColors.systemBlue.copy(alpha = 0.05f),
            iosColors.systemPurple.copy(alpha = 0.05f)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // Tech Style Header
            Text(
                text = "创建账号",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp
                ),
                color = iosColors.systemBlue
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "加入 Gist 开始记录",
                style = MaterialTheme.typography.titleMedium,
                color = iosColors.secondaryLabel
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Form
            IOSTextField(
                value = username,
                onValueChange = { username = it },
                placeholder = "用户名",
                leadingIcon = { 
                    Icon(
                        Icons.Default.Person, 
                        contentDescription = null,
                        tint = iosColors.secondaryLabel
                    ) 
                },
                imeAction = ImeAction.Next
            )

            Spacer(modifier = Modifier.height(16.dp))

            IOSTextField(
                value = email,
                onValueChange = { email = it },
                placeholder = "邮箱",
                leadingIcon = { 
                    Icon(
                        Icons.Default.Email, 
                        contentDescription = null,
                        tint = iosColors.secondaryLabel
                    ) 
                },
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            )

            Spacer(modifier = Modifier.height(16.dp))

            IOSTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = "密码",
                isPassword = true,
                leadingIcon = { 
                    Icon(
                        Icons.Default.Lock, 
                        contentDescription = null,
                        tint = iosColors.secondaryLabel
                    ) 
                },
                imeAction = ImeAction.Next
            )

            Spacer(modifier = Modifier.height(16.dp))

            IOSTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                placeholder = "确认密码",
                isPassword = true,
                leadingIcon = { 
                    Icon(
                        Icons.Default.Lock, 
                        contentDescription = null,
                        tint = iosColors.secondaryLabel
                    ) 
                },
                imeAction = ImeAction.Done,
                onImeAction = { viewModel.register(email, password, confirmPassword, username) }
            )

            Spacer(modifier = Modifier.height(32.dp))

            IOSButton(
                text = "注册",
                onClick = { viewModel.register(email, password, confirmPassword, username) },
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = iosColors.systemBlue
            )

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(
                onClick = onNavigateToLogin
            ) {
                Text(
                    "已有账号? 立即登录",
                    color = iosColors.systemBlue,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
