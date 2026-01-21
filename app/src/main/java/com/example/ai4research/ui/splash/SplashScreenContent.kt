package com.example.ai4research.ui.splash

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
fun SplashScreenContent(onAnimationFinished: () -> Unit) {
    // Controls the "drawing" progress: 0f = not started, 1f = fully drawn
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 2500, // Slightly slower for better effect
                easing = FastOutLinearInEasing
            )
        )
        onAnimationFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        val primaryColor = MaterialTheme.colorScheme.primary
        
        // The formula to draw
        // Using a slightly simplified textual representation that looks like math
        // ideally we would use SVG paths, but getting text path is a robust dynamic way
        val formulaText = "Attention(Q,K,V) = softmax(QKᵀ/√d_k)V"
        
        // Create the Path for the text
        val textPath = remember {
            val paint = Paint().apply {
                textSize = 100f // Base text size, will scale
                typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC) // Serif Italic looks more "math-like"
            }
            val androidPath = android.graphics.Path()
            paint.getTextPath(formulaText, 0, formulaText.length, 0f, 0f, androidPath)
            androidPath.asComposePath()
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            
            // Calculate scale to fit width
            val pathBounds = textPath.getBounds()
            val scaleFactor = (canvasWidth * 0.9f) / pathBounds.width
            
            // Center the path
            val translateX = (canvasWidth - pathBounds.width * scaleFactor) / 2 - pathBounds.left * scaleFactor
            val translateY = (canvasHeight - pathBounds.height * scaleFactor) / 2 - pathBounds.top * scaleFactor

            // Prepare the drawing path
            val drawPath = Path()
            val measure = android.graphics.PathMeasure(textPath.asAndroidPath(), false)
            val totalLength = measure.length
            
            // Extract segment based on progress
            // Note: simple text paths might be disjoint (multiple contours), so we need to handle that
            // or just simply assume total length covers it for a simple effect.
            // For disconnected paths (letters), PathMeasure only measures the first contour unless we iterate.
            
            // Better approach for disjoint text: Draw percentage of *each* contour or accumulate length.
            // Simplified "write on" effect:
            
            var currentLength = 0f
            var distanceDrawn = totalLength * progress.value // This is wrong for multi-contour, need total sum first
            
            // 1. Calculate REAL total length of all letters
            var realTotalLength = 0f
            measure.setPath(textPath.asAndroidPath(), false)
            do {
                realTotalLength += measure.length
            } while (measure.nextContour())
            
            // 2. Draw segments
            val targetDist = realTotalLength * progress.value
            measure.setPath(textPath.asAndroidPath(), false)
            
            var currentDist = 0f
            
            // Transform context for scaling/centering
            with(drawContext.canvas) {
                save()
                translate(translateX, translateY)
                scale(scaleFactor, scaleFactor)
                
                do {
                    val contourLength = measure.length
                    val needsDraw = currentDist + contourLength < targetDist
                    val partialDraw = targetDist > currentDist && targetDist < (currentDist + contourLength)
                    
                    if (needsDraw) {
                        // Draw full contour
                        val fullContour = Path()
                        measure.getSegment(0f, contourLength, fullContour.asAndroidPath(), true)
                        drawPath(
                            path = fullContour,
                            color = primaryColor,
                            style = Stroke(width = 2f) // Thin elegant strokes
                        )
                    } else if (partialDraw) {
                        // Draw partial contour
                        val partialPath = Path()
                        val segmentLength = targetDist - currentDist
                        measure.getSegment(0f, segmentLength, partialPath.asAndroidPath(), true)
                        drawPath(
                            path = partialPath,
                            color = primaryColor,
                            style = Stroke(width = 2f)
                        )
                    }
                    
                    currentDist += contourLength
                } while (measure.nextContour())
                
                restore()
            }
        }
    }
}
