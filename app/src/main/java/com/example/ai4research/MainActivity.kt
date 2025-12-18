package com.example.ai4research

import android.os.Bundle
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
import com.example.ai4research.ui.theme.AI4ResearchTheme
import com.example.ai4research.core.theme.ThemeManager
import com.example.ai4research.core.theme.ThemeMode
import com.example.ai4research.data.repository.AuthRepository
import com.example.ai4research.navigation.NavigationGraph
import com.example.ai4research.navigation.Screen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                AppNavigation(authRepository)
            }
        }
    }
}

/**
 * App Navigation Entry
 * Checks login status and decides start destination
 */
@Composable
fun AppNavigation(authRepository: AuthRepository) {
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
        }
    }
    
    if (isLoading) {
        // Show Loading
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary
            )
        }
    } else {
        // Show Nav Graph
        NavigationGraph(
            navController = navController,
            startDestination = startDestination
        )
    }
}
