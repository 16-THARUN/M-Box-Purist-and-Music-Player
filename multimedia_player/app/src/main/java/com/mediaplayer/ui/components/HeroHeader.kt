package com.mediaplayer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun HeroHeader(
    title: String,
    subtitle: String,
    trackCount: Int,
    totalDurationMs: Long,
    artworkUri: String?,
    onShuffleClick: () -> Unit,
    onPlayClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSelectClick: () -> Unit,
    onOverflowClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF1B1E24),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    color = Color(0xFF2A2E38)
                ) {
                    if (!artworkUri.isNullOrBlank()) {
                        GlideImage(
                            model = artworkUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.MusicNote, null, tint = Color(0xFFFF5722), modifier = Modifier.size(48.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    val hours = totalDurationMs / 1000 / 3600
                    val mins = (totalDurationMs / 1000 % 3600) / 60
                    val durationText = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"

                    Text(
                        text = "$trackCount tracks | $durationText",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFFF5722),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onShuffleClick) {
                    Icon(Icons.Rounded.Shuffle, "Shuffle", tint = Color(0xFFFF5722))
                }
                IconButton(onClick = onPlayClick) {
                    Icon(Icons.Rounded.PlayArrow, "Play", tint = Color(0xFFFF5722))
                }
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Rounded.Search, "Search", tint = Color.White)
                }
                TextButton(onClick = onSelectClick) {
                    Text("Select", color = Color.White, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onOverflowClick) {
                    Icon(Icons.Rounded.MoreVert, "Options", tint = Color.White)
                }
            }
        }
    }
}
