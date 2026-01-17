package com.example.ai4research.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

data class ClickParticle(
    val id: Long,
    val initialPosition: Offset,
    val angle: Float,
    val speed: Float,
    val color: Color,
    val createdAt: Long = System.currentTimeMillis()
)

@Composable
fun ClickParticleEffect(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val particles = remember { mutableStateListOf<ClickParticle>() }
    val scope = rememberCoroutineScope()
    
    // Animation loop to update particles
    var time by remember { mutableLongStateOf(0L) }
    
    LaunchedEffect(Unit) {
        while (true) {
            time = System.currentTimeMillis()
            // Remove old particles (older than 1000ms)
            particles.removeAll { time - it.createdAt > 1000 }
            delay(16) // ~60fps
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    // Spawn particles on tap
                    repeat(8) {
                        particles.add(
                            ClickParticle(
                                id = Random.nextLong(),
                                initialPosition = offset,
                                angle = Random.nextFloat() * 360f,
                                speed = Random.nextFloat() * 5f + 2f,
                                color = listOf(
                                    Color(0xFF00C6FF), // Cyan
                                    Color(0xFF0072FF), // Blue
                                    Color(0xFFFFFFFF)  // White
                                ).random()
                            )
                        )
                    }
                }
            }
    ) {
        content()
        
        Canvas(modifier = Modifier.fillMaxSize()) {
            val currentTime = System.currentTimeMillis()
            
            particles.forEach { particle ->
                val age = currentTime - particle.createdAt
                if (age in 0..1000) {
                    val progress = age / 1000f
                    val distance = particle.speed * age / 10f
                    val radians = Math.toRadians(particle.angle.toDouble())
                    val x = particle.initialPosition.x + (distance * Math.cos(radians)).toFloat()
                    val y = particle.initialPosition.y + (distance * Math.sin(radians)).toFloat()
                    
                    val alpha = 1f - progress
                    val radius = 4f * (1f - progress)

                    drawCircle(
                        color = particle.color.copy(alpha = alpha),
                        radius = radius,
                        center = Offset(x, y)
                    )
                }
            }
        }
    }
}
