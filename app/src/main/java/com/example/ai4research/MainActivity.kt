package com.example.ai4research

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.lifecycleScope
import com.example.ai4research.core.theme.AI4ResearchTheme
import com.example.ai4research.core.theme.ThemeManager
import com.example.ai4research.core.theme.ThemeMode
import com.example.ai4research.core.util.LocalWebViewCache
import com.example.ai4research.core.util.WebViewCache
import com.example.ai4research.data.repository.AuthRepository
import com.example.ai4research.navigation.NavigationGraph
import com.example.ai4research.navigation.Screen
import com.example.ai4research.service.FloatingWindowManager
import com.example.ai4research.service.FloatingWindowService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.ai4research.ui.splash.SplashScreenContent

/**
 * Main Activity
 * @AndroidEntryPoint Enable Hilt
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var authRepository: AuthRepository
    
    @Inject
    lateinit var themeManager: ThemeManager

    @Inject
    lateinit var floatingWindowManager: FloatingWindowManager
    
    private var isInitializing by mutableStateOf(true)
    private var isAnimationFinished by mutableStateOf(false)
    
    // 目标 Tab，用于从悬浮窗跳转时切换
    var targetTab by mutableStateOf<String?>(null)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        
        // System splash screen: immediately hide it because we have our own Compose splash
        splashScreen.setKeepOnScreenCondition { false }
        
        // 处理从悬浮窗跳转的 intent
        handleTargetTabIntent(intent)
        
        enableEdgeToEdge()
        
        setContent {
            // Collect theme mode
            val themeMode by themeManager.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
            val systemInDarkTheme = isSystemInDarkTheme()
            val context = LocalContext.current
            val webViewCache = remember { WebViewCache() }

            DisposableEffect(Unit) {
                onDispose {
                    webViewCache.clear()
                }
            }
            
            // Decide dark theme
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemInDarkTheme
            }
            
            // Start initialization immediately
            LaunchedEffect(Unit) {
                // Warm up WebView engine and preload login page while splash runs
                launch { webViewCache.warmUp(context) }
                launch { webViewCache.preloadLogin(context) }

                // Perform data loading in parallel off the main thread
                val isLoggedIn = withContext(Dispatchers.IO) { authRepository.isLoggedInFast() }
                startDestination = if (isLoggedIn) {
                    Screen.Main.route
                } else {
                    Screen.Login.route
                }
                if (isLoggedIn) {
                    webViewCache.preloadMain(context)
                }
                isInitializing = false
            }
            
            // Use the new UI Theme
            AI4ResearchTheme(darkTheme = darkTheme) {
                // Show splash animation if animation is not finished OR data is not ready
                // BUT: If animation finishes and data is NOT ready, we keep showing splash (maybe with loading indicator)
                // If data is ready but animation NOT finished, we wait for animation.
                
                val showSplash = !isAnimationFinished || isInitializing
                
                if (showSplash) {
                    // Show splash screen with animation
                    SplashScreenContent(
                        onAnimationFinished = {
                            isAnimationFinished = true
                        }
                    )
                } else {
                    // Both animation finished and initialization done - transition to app
                    val navController = rememberNavController()
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalWebViewCache provides webViewCache
                    ) {
                        NavigationGraph(
                            navController = navController,
                            startDestination = startDestination
                        )
                    }
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val enabled = floatingWindowManager.isFloatingWindowEnabled.first()
            if (!enabled) {
                floatingWindowManager.stopFloatingWindowService()
            }
        }
        // 注册广播接收器
        registerItemAddedReceiver()
    }
    
    override fun onPause() {
        super.onPause()
        // 注销广播接收器
        unregisterItemAddedReceiver()
    }
    
    // 条目添加成功的广播接收器
    private var itemAddedReceiver: BroadcastReceiver? = null
    
    private fun registerItemAddedReceiver() {
        if (itemAddedReceiver == null) {
            itemAddedReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == FloatingWindowService.ACTION_ITEM_ADDED) {
                        val itemId = intent.getStringExtra(FloatingWindowService.EXTRA_ITEM_ID)
                        val itemType = intent.getStringExtra(FloatingWindowService.EXTRA_ITEM_TYPE)
                        android.util.Log.d("MainActivity", "收到条目添加广播: id=$itemId, type=$itemType")
                        // 这里可以触发UI更新，但由于Room Flow会自动触发，所以主要用于日志
                    }
                }
            }
        }
        val filter = IntentFilter(FloatingWindowService.ACTION_ITEM_ADDED)
        registerReceiver(itemAddedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }
    
    private fun unregisterItemAddedReceiver() {
        itemAddedReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                // Ignore if not registered
            }
        }
    }
    
    private var startDestination by mutableStateOf(Screen.Login.route)
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleTargetTabIntent(intent)
    }
    
    private fun handleTargetTabIntent(intent: Intent?) {
        intent?.getStringExtra("target_type")?.let { typeStr ->
            // 将 ItemType 转换为 Tab 名称（与前端 NavBar 的 id 一致）
            targetTab = when (typeStr.uppercase()) {
                "PAPER" -> "papers"      // 前端使用复数形式
                "COMPETITION" -> "competitions"
                "INSIGHT" -> "home"       // 动态显示在首页
                else -> "papers"
            }
            android.util.Log.d("MainActivity", "收到跳转请求，目标Tab: $targetTab")
        }
    }
    
    fun clearTargetTab() {
        targetTab = null
    }
}
