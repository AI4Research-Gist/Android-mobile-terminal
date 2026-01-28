package com.example.ai4research.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.ai4research.ui.auth.AuthScreen
import com.example.ai4research.ui.auth.AuthViewModel
import com.example.ai4research.ui.detail.DetailScreen
import com.example.ai4research.ui.main.MainScreen

/**
 * 应用导航图
 * iOS 风格转场动画：从右向左滑入，从左向右滑出
 */
@Composable
fun NavigationGraph(
    navController: NavHostController,
    startDestination: String
) {
    // 使用共享的 AuthViewModel
    val authViewModel: AuthViewModel = hiltViewModel()
    
    // iOS 风格动画配置
    val animationDuration = 400
    
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(animationDuration)
            )
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(animationDuration)
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(animationDuration)
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(animationDuration)
            )
        }
    ) {
        // 登录页面
        composable(Screen.Login.route) {
            AuthScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onRegisterSuccess = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                viewModel = authViewModel
            )
        }
        
        // 注册页面
        composable(Screen.Register.route) {
            AuthScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onRegisterSuccess = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                viewModel = authViewModel
            )
        }
        
        // 主页面 (包含底部导航)
        composable(Screen.Main.route) {
            MainScreen(
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToDetail = { itemId ->
                    navController.navigate(Screen.Detail.createRoute(itemId))
                },
                onNavigateToVoiceRecording = {
                    navController.navigate(Screen.VoiceRecording.route)
                }
            )
        }
        
        // 详情页面
        composable(
            route = Screen.Detail.route,
            arguments = listOf(navArgument("itemId") { type = NavType.StringType })
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getString("itemId") ?: ""
            DetailScreen(
                itemId = itemId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        // 语音录制页面
        composable(Screen.VoiceRecording.route) {
            com.example.ai4research.ui.voice.VoiceRecordingScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onSaveSuccess = { itemId ->
                    navController.popBackStack()
                }
            )
        }
    }
}
