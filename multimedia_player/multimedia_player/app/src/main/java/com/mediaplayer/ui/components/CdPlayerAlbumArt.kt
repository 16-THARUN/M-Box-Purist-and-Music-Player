package com.mediaplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun CdPlayerAlbumArt(
    albumArtUri: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    var rotation by remember { mutableFloatStateOf(0f) }

    // Smoothly rotate only when playing, freezing exactly in place when paused
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            withFrameNanos {
                rotation = (rotation + 0.3f) % 360f // Adjust 0.3f for speed
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()      // Take up the full width of the screen
            .aspectRatio(1f)     // Force it to be a perfect circle (1:1 ratio)
            .padding(32.dp)      // Give it breathing room from the screen edges
            .shadow(16.dp, CircleShape) // Add a drop shadow below the CD
            .graphicsLayer { rotationZ = rotation }
            .clip(CircleShape)
            .background(
                // The shiny silver edge of the CD
                Brush.sweepGradient(
                    colors = listOf(
                        Color.LightGray, Color.White, Color.LightGray, Color.Gray, Color.LightGray
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // 1. The Album Art (Covers 96% of the CD, leaving a silver rim)
        GlideImage(
            model = albumArtUri,
            contentDescription = "CD Album Art",
            modifier = Modifier
                .fillMaxSize(0.96f)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        // 2. The Inner Plastic Ring
        Box(
            modifier = Modifier
                .fillMaxSize(0.25f) // Inner 25% of the CD
                .clip(CircleShape)
                .background(Color.LightGray.copy(alpha = 0.8f))
                .border(1.dp, Color.Gray.copy(alpha = 0.3f), CircleShape)
        )

        // 3. The Transparent Center Hole
        Box(
            modifier = Modifier
                .fillMaxSize(0.08f) // The very center
                .clip(CircleShape)
                // Using the app's background color so it looks like a real hole!
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, Color.DarkGray.copy(alpha = 0.4f), CircleShape)
        )
    }
}
