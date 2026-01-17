package com.example.ai4research.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun ParticleBackground(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "particle_animation")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    // Generate stars (Static + Twinkling)
    val stars = remember {
        List(300) { // Increased star count
            Star(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                size = Random.nextFloat() * 3f + 1f,
                alpha = Random.nextFloat() * 0.8f + 0.2f,
                twinkleSpeed = Random.nextFloat() * 5f + 2f,
                twinkleOffset = Random.nextFloat() * 10f
            )
        }
    }

    // Generate Meteors
    val meteors = remember {
        mutableStateListOf<Meteor>()
    }

    // Meteor Spawner - Increased frequency
    LaunchedEffect(Unit) {
        while (true) {
            // Random delay between meteors - Faster frequency
            kotlinx.coroutines.delay(Random.nextLong(800, 2500))
            meteors.add(createMeteor())
        }
    }

    // Meteor Updater
    LaunchedEffect(Unit) {
        while (true) {
            val frameTime = 16L // ~60 FPS
            val iterator = meteors.iterator()
            while (iterator.hasNext()) {
                val meteor = iterator.next()
                meteor.update()
                if (meteor.isFinished()) {
                    iterator.remove()
                }
            }
            withFrameMillis { } // Wait for next frame
        }
    }

    // Deep Space Background - Slightly Lighter for better contrast
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0F172A), // Dark Slate
            Color(0xFF1E293B), // Slate 800
            Color(0xFF334155)  // Slate 700 (Bottom light)
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Draw Stars
            stars.forEach { star ->
                val twinkle = 0.5f + 0.5f * sin(time * star.twinkleSpeed + star.twinkleOffset)
                val currentAlpha = star.alpha * twinkle
                
                drawCircle(
                    color = Color.White.copy(alpha = currentAlpha),
                    radius = star.size,
                    center = Offset(star.x * width, star.y * height)
                )
            }

            // Draw Meteors
            meteors.forEach { meteor ->
                drawMeteor(meteor, width, height)
            }
        }
    }
}

private fun createMeteor(): Meteor {
    // Random start position (can be off-screen top/left/right)
    val startX = Random.nextFloat() * 1.5f - 0.25f 
    val startY = -0.2f // Start well above screen
    
    // Random angle: -45 to 225 degrees (mostly downward)
    // 0 is Right, 90 is Down, 180 is Left
    // We want them to fall down, so roughly 45 to 135 degrees is pure down
    // Let's give them a wide spread: 10 degrees to 170 degrees
    val angleDegrees = Random.nextFloat() * 160f + 10f 
    
    val speed = 0.02f + Random.nextFloat() * 0.025f // Faster
    val length = 0.15f + Random.nextFloat() * 0.15f // Longer tails
    
    return Meteor(
        x = startX,
        y = startY,
        angle = Math.toRadians(angleDegrees.toDouble()).toFloat(),
        speed = speed,
        length = length,
        width = 3f + Random.nextFloat() * 3f // Thicker
    )
}

private class Star(
    val x: Float,
    val y: Float,
    val size: Float,
    val alpha: Float,
    val twinkleSpeed: Float,
    val twinkleOffset: Float
)

private class Meteor(
    var x: Float,
    var y: Float,
    val angle: Float,
    val speed: Float,
    val length: Float,
    val width: Float
) {
    var progress = 0f

    fun update() {
        x += speed * cos(angle)
        y += speed * sin(angle)
        progress += speed
    }

    fun isFinished(): Boolean {
        // Extended bounds check
        return y > 1.5f || x > 1.5f || x < -0.5f
    }
}

private fun DrawScope.drawMeteor(meteor: Meteor, width: Float, height: Float) {
    val headX = meteor.x * width
    val headY = meteor.y * height
    
    // Calculate tail end position
    val tailLenPixels = meteor.length * height 
    val tailX = headX - tailLenPixels * cos(meteor.angle)
    val tailY = headY - tailLenPixels * sin(meteor.angle)

    // Gradient for the tail (Bright White -> Transparent)
    val brush = Brush.linearGradient(
        colors = listOf(
            Color.White,
            Color.White.copy(alpha = 0f)
        ),
        start = Offset(headX, headY),
        end = Offset(tailX, tailY)
    )

    drawLine(
        brush = brush,
        start = Offset(headX, headY),
        end = Offset(tailX, tailY),
        strokeWidth = meteor.width,
        cap = androidx.compose.ui.graphics.StrokeCap.Round
    )
    
    // Glowing head
    drawCircle(
        color = Color.White, // Pure white head
        radius = meteor.width * 1.2f,
        center = Offset(headX, headY)
    )
    
    // Outer glow for head
    drawCircle(
        color = Color.White.copy(alpha = 0.5f),
        radius = meteor.width * 2.5f,
        center = Offset(headX, headY)
    )
}
