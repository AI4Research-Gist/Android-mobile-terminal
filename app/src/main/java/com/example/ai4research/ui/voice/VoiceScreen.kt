package com.example.ai4research.ui.voice

import android.Manifest
import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ai4research.domain.model.ResearchItem
import com.example.ai4research.ui.components.GistCard
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun VoiceScreen(
    onItemClick: (String) -> Unit,
    viewModel: VoiceViewModel = hiltViewModel()
) {
    val voiceItems by viewModel.voiceItems.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val recordPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    // Simple player lifetime
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playingId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { player?.release() }
            player = null
        }
    }

    LaunchedEffect(errorMessage) {
        val msg = errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearError()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "语音灵感",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.96f)
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (!recordPermission.status.isGranted) {
                        recordPermission.launchPermissionRequest()
                        return@FloatingActionButton
                    }
                    if (isRecording) viewModel.stopRecording() else viewModel.startRecording()
                },
                containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(64.dp)
            ) {
                Text(if (isRecording) "停" else "录", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    if (!recordPermission.status.isGranted) {
                        GistCard(onClick = { recordPermission.launchPermissionRequest() }) {
                            Text(
                                text = "需要录音权限才能使用语音功能，点击授权。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    } else {
                        GistCard(onClick = {}) {
                            Text(
                                text = if (isRecording) "正在录音… 再点一次停止并保存" else "点击右下角「录」开始记录灵感",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                items(voiceItems, key = { it.id }) { item ->
                    VoiceItemCard(
                        item = item,
                        isPlaying = playingId == item.id,
                        onPlayToggle = {
                            val path = item.audioUrl ?: return@VoiceItemCard
                            if (playingId == item.id) {
                                runCatching { player?.stop() }
                                runCatching { player?.release() }
                                player = null
                                playingId = null
                                return@VoiceItemCard
                            }

                            // stop previous
                            runCatching { player?.stop() }
                            runCatching { player?.release() }
                            player = null
                            playingId = null

                            try {
                                val p = MediaPlayer().apply {
                                    setDataSource(path)
                                    prepare()
                                    start()
                                    setOnCompletionListener {
                                        runCatching { it.release() }
                                        player = null
                                        playingId = null
                                    }
                                }
                                player = p
                                playingId = item.id
                            } catch (e: Exception) {
                                scope.launch { snackbarHostState.showSnackbar("播放失败：${e.message ?: "未知错误"}") }
                            }
                        },
                        onClick = { onItemClick(item.id) }
                    )
                }

                if (voiceItems.isEmpty()) {
                    item {
                        GistCard(onClick = {}) {
                            Text(
                                text = "暂无语音记录。点击右下角开始录音。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceItemCard(
    item: ResearchItem,
    isPlaying: Boolean,
    onPlayToggle: () -> Unit,
    onClick: () -> Unit
) {
    GistCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FloatingActionButton(
                onClick = onPlayToggle,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "停止" else "播放",
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val fileName = item.audioUrl?.let { runCatching { File(it).name }.getOrNull() }
                if (!fileName.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                }
            }

            Text(
                text = SimpleDateFormat("MM/dd", Locale.CHINA).format(item.createdAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
        }
    }
}


