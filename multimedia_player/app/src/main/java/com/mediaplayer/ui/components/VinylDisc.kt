package com.mediaplayer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

@Composable
fun VinylDisc(
    albumArt: Painter,
    isPlaying: Boolean,
    amplitude: Float = 0f,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // 1. Responsive diameter calculation (80% of the smallest dimension)
        val discDiameter = minOf(maxWidth, maxHeight) * 0.8f
        
        val infiniteTransition = rememberInfiniteTransition(label = "DiscRotation")
        val angle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "Rotation"
        )

        // The background gradient (Static, does not rotate)
        Box(
            modifier = Modifier
                .size(discDiameter)
                .clip(CircleShape)
                .background(
                    Brush.sweepGradient(
                        listOf(Color(0xFF111111), Color(0xFF222222), Color(0xFF111111))
                    )
                )
        )

        // The Disc Container (only this part rotates)
        Box(
            modifier = Modifier
                .size(discDiameter)
                .graphicsLayer { 
                    rotationZ = if (isPlaying) angle else 0f 
                },
            contentAlignment = Alignment.Center
        ) {
            // Groove Rings (Reactive to amplitude)
            repeat(5) { i ->
                val grooveFactor = 0.65f + (i * 0.06f)
                Box(
                    modifier = Modifier
                        .fillMaxSize(grooveFactor + (amplitude * 0.02f))
                        .border(
                            width = (discDiameter * 0.005f),
                            color = Color.White.copy(alpha = 0.05f),
                            shape = CircleShape
                        )
                )
            }

            // Inner Album Art Label (55% of diameter)
            Box(
                modifier = Modifier
                    .fillMaxSize(0.55f)
                    .clip(CircleShape)
                    .background(Color.DarkGray)
            ) {
                Image(
                    painter = albumArt,
                    contentDescription = "Album Art Label",
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }

            // Spindle Hole (4% of diameter)
            Box(
                modifier = Modifier
                    .fillMaxSize(0.04f)
                    .clip(CircleShape)
                    .background(Color(0xFF0A0A0A))
                    .border(
                        width = (discDiameter * 0.002f),
                        color = Color.White.copy(alpha = 0.2f),
                        shape = CircleShape
                    )
            )
        }
    }
}
