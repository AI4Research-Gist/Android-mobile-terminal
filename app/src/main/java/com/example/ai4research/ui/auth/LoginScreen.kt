package com.example.ai4research.ui.auth

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
fun LoginScreen(
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
    biometricHelper: BiometricHelper = BiometricHelper()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val hasBiometricCredentials by viewModel.hasBiometricCredentials.collectAsState()
    val iosColors = IOSTheme.colors

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showBiometricOption by remember { mutableStateOf(false) }

    // Check Biometric Availability
    LaunchedEffect(Unit) {
        if (context is FragmentActivity) {
            val status = biometricHelper.canAuthenticate(context)
            showBiometricOption = status == BiometricHelper.BiometricStatus.READY && hasBiometricCredentials
        }
    }

    // Handle UI State
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is AuthUiState.LoginSuccess -> {
                Toast.makeText(context, "欢迎回来!", Toast.LENGTH_SHORT).show()
                onLoginSuccess()
                viewModel.resetUiState()
            }
            is AuthUiState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                viewModel.resetUiState()
            }
            else -> {}
        }
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
            Spacer(modifier = Modifier.height(80.dp))

            // Tech Style Header
            Text(
                text = "AI4Research",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp
                ),
                color = iosColors.systemBlue
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "未来的科研助手",
                style = MaterialTheme.typography.titleMedium,
                color = iosColors.secondaryLabel
            )

            Spacer(modifier = Modifier.height(60.dp))

            // Form Fields
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
                imeAction = ImeAction.Done,
                onImeAction = { viewModel.login(email, password) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            IOSButton(
                text = "登录",
                onClick = { viewModel.login(email, password) },
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = iosColors.systemBlue
            )

            if (showBiometricOption) {
                Spacer(modifier = Modifier.height(24.dp))
                IconButton(
                    onClick = {
                        if (context is FragmentActivity) {
                            biometricHelper.showBiometricPrompt(
                                activity = context,
                                onSuccess = { viewModel.biometricLogin() },
                                onError = { _, msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
                            )
                        }
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(iosColors.systemBlue.copy(alpha = 0.1f))
                ) {
                    Icon(
                        Icons.Default.Fingerprint,
                        contentDescription = "生物识别登录",
                        tint = iosColors.systemBlue,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Footer Links
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onNavigateToRegister) {
                    Text(
                        "创建账号", 
                        color = iosColors.systemBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                TextButton(onClick = { /* Forgot Password */ }) {
                    Text(
                        "忘记密码?", 
                        color = iosColors.secondaryLabel
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
