package com.example.ai4research.ui.auth

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ai4research.R
import com.example.ai4research.core.security.BiometricHelper
import com.example.ai4research.core.theme.IOSColors
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
    var contentVisible by remember { mutableStateOf(false) }
    val isLoading = uiState is AuthUiState.Loading

    LaunchedEffect(Unit) {
        contentVisible = true
    }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is AuthUiState.RegisterSuccess -> {
                Toast.makeText(context, "注册成功！", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, "生物识别已启用", Toast.LENGTH_SHORT).show()
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
            title = { Text("开启生物识别登录？") },
            text = { Text("使用 Face ID 或指纹以便下次一键登录。") },
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
                TextButton(
                    onClick = {
                        showBiometricDialog = false
                        onRegisterSuccess()
                    }
                ) { Text("稍后", color = iosColors.secondaryLabel) }
            },
            containerColor = iosColors.secondarySystemBackground
        )
    }

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            iosColors.systemBackground,
            iosColors.systemBlue.copy(alpha = 0.08f),
            iosColors.systemPurple.copy(alpha = 0.08f)
        )
    )
    val cardShape = RoundedCornerShape(28.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        AuthGlow(iosColors)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            BrandPill(iosColors)

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "创建账号",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp
                ),
                color = iosColors.label
            )

            Text(
                text = "加入你的研究操作系统",
                style = MaterialTheme.typography.titleMedium,
                color = iosColors.secondaryLabel
            )

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(450)) + slideInVertically(tween(450)) { it / 3 }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(cardShape)
                        .background(iosColors.secondarySystemBackground.copy(alpha = 0.92f))
                        .border(1.dp, iosColors.separator.copy(alpha = 0.7f), cardShape)
                        .padding(20.dp)
                ) {
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
                        imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                        containerColor = iosColors.systemBackground,
                        enabled = !isLoading
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
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Email,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                        containerColor = iosColors.systemBackground,
                        enabled = !isLoading
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
                        imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                        containerColor = iosColors.systemBackground,
                        enabled = !isLoading
                    )

                    Text(
                        text = "密码至少 6 位",
                        style = MaterialTheme.typography.bodySmall,
                        color = iosColors.tertiaryLabel,
                        modifier = Modifier.padding(start = 6.dp, top = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

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
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                        onImeAction = {
                            viewModel.register(email, password, confirmPassword, username)
                        },
                        containerColor = iosColors.systemBackground,
                        enabled = !isLoading
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    IOSButton(
                        text = if (isLoading) "正在创建..." else "创建账号",
                        onClick = {
                            viewModel.register(email, password, confirmPassword, username)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        backgroundColor = iosColors.systemBlue,
                        enabled = !isLoading
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            TextButton(onClick = onNavigateToLogin) {
                Text(
                    text = "已有账号？立即登录",
                    color = iosColors.systemBlue,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun BrandPill(iosColors: IOSColors) {
    val pillShape = RoundedCornerShape(22.dp)
    val titleBrush = Brush.linearGradient(
        colors = listOf(iosColors.systemBlue, iosColors.systemPurple)
    )

    Row(
        modifier = Modifier
            .clip(pillShape)
            .border(1.dp, iosColors.separator.copy(alpha = 0.6f), pillShape)
            .background(iosColors.secondarySystemBackground.copy(alpha = 0.75f))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_logo),
            contentDescription = "AI4Research logo",
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "AI4Research",
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
                brush = titleBrush
            )
        )
    }
}

@Composable
private fun AuthGlow(iosColors: IOSColors) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .size(260.dp)
                .offset(x = (-120).dp, y = 40.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            iosColors.systemBlue.copy(alpha = 0.28f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(220.dp)
                .offset(x = 180.dp, y = 120.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            iosColors.systemPurple.copy(alpha = 0.22f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )
    }
}
