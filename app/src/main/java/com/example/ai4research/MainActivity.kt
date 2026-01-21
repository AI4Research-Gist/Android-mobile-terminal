package com.example.ai4research

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.ai4research.core.theme.AI4ResearchTheme
import com.example.ai4research.core.theme.ThemeManager
import com.example.ai4research.core.theme.ThemeMode
import com.example.ai4research.data.repository.AuthRepository
import com.example.ai4research.navigation.NavigationGraph
import com.example.ai4research.navigation.Screen
import com.example.ai4research.service.FloatingWindowService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
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
    
    private var isInitializing by mutableStateOf(true)
    private var isAnimationFinished by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        
        // Keep splash screen visible only while strictly initializing (optional),
        // but we want to show our Compose Splash Content immediately.
        // So we return false (or true strictly for data load, but NOT for animation).
        // Since we handle "isInitializing" in the UI (AppNavigation/SplashScreenContent),
        // we can just let the system splash go away as soon as the app draws.
        splashScreen.setKeepOnScreenCondition { false }
        
        enableEdgeToEdge()
        
        setContent {
            // Collect theme mode
            val themeMode by themeManager.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
            val systemInDarkTheme = isSystemInDarkTheme()
            
            // Decide dark theme
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemInDarkTheme
            }
            
            // Use the new UI Theme
            AI4ResearchTheme(darkTheme = darkTheme) {
                // Show splash animation first, then transition to app content
                if (!isAnimationFinished || isInitializing) {
                    // Show splash screen with animation
                    SplashScreenContent(
                        onAnimationFinished = {
                            isAnimationFinished = true
                        }
                    )
                    
                    // Start initialization in background while animation plays
                    LaunchedEffect(Unit) {
                        val isLoggedIn = authRepository.isLoggedIn()
                        startDestination = if (isLoggedIn) {
                            Screen.Main.route
                        } else {
                            Screen.Login.route
                        }
                        isInitializing = false
                    }
                } else {
                    // Both animation finished and initialization done - show app
                    val navController = rememberNavController()
                    NavigationGraph(
                        navController = navController,
                        startDestination = startDestination
                    )
                }
            }
        }
    }
    
    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            // In a real app, use registerForActivityResult
            startActivity(intent) 
        } else {
            startService(Intent(this, FloatingWindowService::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        if (Settings.canDrawOverlays(this)) {
            startService(Intent(this, FloatingWindowService::class.java))
        }
    }
    
    private var startDestination by mutableStateOf(Screen.Login.route)
}

/**
 * App Navigation Entry
 * Checks login status and decides start destination
 */
@Composable
fun AppNavigation(
    authRepository: AuthRepository,
    onInitialized: () -> Unit
) {
    val navController = rememberNavController()
    var isLoading by remember { mutableStateOf(true) }
    var startDestination by remember { mutableStateOf(Screen.Login.route) }
    val coroutineScope = rememberCoroutineScope()
    
    // Check Login Status
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            val isLoggedIn = authRepository.isLoggedIn()
            startDestination = if (isLoggedIn) {
                Screen.Main.route
            } else {
                Screen.Login.route
            }
            isLoading = false
            onInitialized()
        }
    }
    
    if (!isLoading) {
        // Show Nav Graph
        NavigationGraph(
            navController = navController,
            startDestination = startDestination
        )
    }
}
