package com.example.ai4research.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * 截图Activity
 * 处理MediaProjection权限请求和截图/区域选择
 * 仅负责获取截图并发送广播，不处理AI逻辑
 */
@AndroidEntryPoint
class ScreenCaptureActivity : ComponentActivity() {

    companion object {
        const val ACTION_CAPTURE_COMPLETED = "com.example.ai4research.action.CAPTURE_COMPLETED"
        const val EXTRA_IMAGE_PATH = "image_path"
    }

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0
    
    private var captureMode = "full" // "full" or "region"
    private var showRegionSelector by mutableStateOf(false)

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            MediaProjectionStore.setPermission(result.resultCode, result.data!!)
            mediaProjection = mediaProjectionManager.getMediaProjection(
                result.resultCode,
                result.data!!
            )
            
            if (captureMode == "full") {
                performScreenCapture()
            } else {
                showRegionSelector = true
            }
        } else {
            Toast.makeText(this, "截图权限被拒绝", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        captureMode = intent.getStringExtra("mode") ?: "full"
        
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        
        // 获取屏幕尺寸
        val metrics = DisplayMetrics()
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            val bounds = windowMetrics.bounds
            screenWidth = bounds.width()
            screenHeight = bounds.height()
            screenDensity = resources.configuration.densityDpi
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
            screenDensity = metrics.densityDpi
        }
        
        setContent {
            if (showRegionSelector) {
                RegionSelectorScreen(
                    onRegionSelected = { rect ->
                        showRegionSelector = false
                        performRegionCapture(rect)
                    },
                    onCancel = {
                        showRegionSelector = false
                        finish()
                    }
                )
            } else {
                // 透明背景，请求权限
                Box(modifier = Modifier.fillMaxSize().background(Color.Transparent))
            }
        }

        // If permission was granted before, skip prompt
        if (MediaProjectionStore.hasPermission()) {
            mediaProjection = MediaProjectionStore.getOrCreateProjection(mediaProjectionManager)
            if (mediaProjection == null) {
                requestScreenCapture()
            } else {
                if (captureMode == "full") {
                    performScreenCapture()
                } else {
                    showRegionSelector = true
                }
            }
        } else {
            // 请求截图权限
            requestScreenCapture()
        }
    }

    private fun requestScreenCapture() {
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        projectionLauncher.launch(captureIntent)
    }

    private fun performScreenCapture() {
        if (mediaProjection == null) {
            finish()
            return
        }

        imageReader = ImageReader.newInstance(
            screenWidth, screenHeight,
            PixelFormat.RGBA_8888, 2
        )

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null, null
        )

        Handler(Looper.getMainLooper()).postDelayed({
            captureImage()
        }, 500)
    }

    private fun performRegionCapture(region: Rect) {
        if (mediaProjection == null) {
            finish()
            return
        }

        imageReader = ImageReader.newInstance(
            screenWidth, screenHeight,
            PixelFormat.RGBA_8888, 2
        )

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null, null
        )

        Handler(Looper.getMainLooper()).postDelayed({
            captureImage(region)
        }, 500)
    }

    private fun captureImage(region: Rect? = null) {
        val image = imageReader?.acquireLatestImage()
        if (image != null) {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            val bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()

            // 如果有区域选择，裁剪图片
            val finalBitmap = if (region != null) {
                Bitmap.createBitmap(
                    bitmap,
                    region.left.toInt().coerceIn(0, bitmap.width - 1),
                    region.top.toInt().coerceIn(0, bitmap.height - 1),
                    region.width.toInt().coerceIn(1, bitmap.width - region.left.toInt()),
                    region.height.toInt().coerceIn(1, bitmap.height - region.top.toInt())
                )
            } else {
                Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
            }

            // 保存截图
            val imagePath = saveBitmap(finalBitmap)
            
            cleanup()
            
            if (imagePath != null) {
                // 发送广播给 Service
                val intent = Intent(ACTION_CAPTURE_COMPLETED).apply {
                    putExtra(EXTRA_IMAGE_PATH, imagePath)
                    setPackage(packageName) // 限制包名，增强安全
                }
                sendBroadcast(intent)
            } else {
                Toast.makeText(this, "保存截图失败", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "截图失败", Toast.LENGTH_SHORT).show()
            cleanup()
        }
        
        finish()
    }

    private fun saveBitmap(bitmap: Bitmap): String? {
        try {
            val screenshotDir = File(cacheDir, "screenshots")
            if (!screenshotDir.exists()) {
                screenshotDir.mkdirs()
            }
            
            val file = File(screenshotDir, "screenshot_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            
            return file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun cleanup() {
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanup()
    }
}

/**
 * 区域选择界面
 */
@Composable
fun RegionSelectorScreen(
    onRegionSelected: (Rect) -> Unit,
    onCancel: () -> Unit
) {
    var startOffset by remember { mutableStateOf(Offset.Zero) }
    var endOffset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        startOffset = offset
                        endOffset = offset
                        isDragging = true
                    },
                    onDrag = { change, _ ->
                        endOffset = change.position
                    },
                    onDragEnd = {
                        isDragging = false
                    }
                )
            }
    ) {
        // 绘制选择区域和遮罩
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val dimColor = Color.Black.copy(alpha = 0.5f)

            if (startOffset != Offset.Zero && endOffset != Offset.Zero) {
                val left = minOf(startOffset.x, endOffset.x)
                val top = minOf(startOffset.y, endOffset.y)
                val right = maxOf(startOffset.x, endOffset.x)
                val bottom = maxOf(startOffset.y, endOffset.y)
                
                // 绘制四个矩形来实现中间镂空的遮罩效果
                // 1. 顶部
                drawRect(color = dimColor, topLeft = Offset(0f, 0f), size = Size(canvasWidth, top))
                // 2. 底部
                drawRect(color = dimColor, topLeft = Offset(0f, bottom), size = Size(canvasWidth, canvasHeight - bottom))
                // 3. 左侧 (中间部分)
                drawRect(color = dimColor, topLeft = Offset(0f, top), size = Size(left, bottom - top))
                // 4. 右侧 (中间部分)
                drawRect(color = dimColor, topLeft = Offset(right, top), size = Size(canvasWidth - right, bottom - top))
                
                // 绘制选中区域边框
                drawRect(
                    color = Color(0xFF10B981),
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    style = Stroke(
                        width = 4f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f))
                    )
                )
                
                // 绘制四角
                val cornerSize = 30f
                val cornerStroke = Stroke(width = 6f)
                
                // 左上角
                drawLine(Color(0xFF10B981), Offset(left, top), Offset(left + cornerSize, top), strokeWidth = 6f)
                drawLine(Color(0xFF10B981), Offset(left, top), Offset(left, top + cornerSize), strokeWidth = 6f)
                
                // 右上角
                drawLine(Color(0xFF10B981), Offset(right, top), Offset(right - cornerSize, top), strokeWidth = 6f)
                drawLine(Color(0xFF10B981), Offset(right, top), Offset(right, top + cornerSize), strokeWidth = 6f)
                
                // 左下角
                drawLine(Color(0xFF10B981), Offset(left, bottom), Offset(left + cornerSize, bottom), strokeWidth = 6f)
                drawLine(Color(0xFF10B981), Offset(left, bottom), Offset(left, bottom - cornerSize), strokeWidth = 6f)
                
                // 右下角
                drawLine(Color(0xFF10B981), Offset(right, bottom), Offset(right - cornerSize, bottom), strokeWidth = 6f)
                drawLine(Color(0xFF10B981), Offset(right, bottom), Offset(right, bottom - cornerSize), strokeWidth = 6f)
            } else {
                // 没有选择时，全屏遮罩
                drawRect(color = dimColor)
            }
        }
        
        // 提示文字
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "拖拽选择截图区域",
                color = Color.White,
                fontSize = 18.sp
            )
        }
        
        // 底部按钮
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
            ) {
                Text("取消")
            }
            
            Button(
                onClick = {
                    if (startOffset != Offset.Zero && endOffset != Offset.Zero) {
                        val left = minOf(startOffset.x, endOffset.x)
                        val top = minOf(startOffset.y, endOffset.y)
                        val right = maxOf(startOffset.x, endOffset.x)
                        val bottom = maxOf(startOffset.y, endOffset.y)
                        onRegionSelected(Rect(left, top, right, bottom))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                enabled = startOffset != Offset.Zero && endOffset != Offset.Zero
            ) {
                Text("确认截图")
            }
        }
    }
}
