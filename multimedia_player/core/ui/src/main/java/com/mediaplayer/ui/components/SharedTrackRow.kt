package com.mediaplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.mediaplayer.data.model.Track
import com.mediaplayer.data.model.toTimeString

fun cleanTrackTitle(title: String, uri: String = ""): String {
    var name = title.ifBlank { uri.substringAfterLast("/").substringAfterLast("\\") }
    val extensions = listOf(".flac", ".mp3", ".ogg", ".opus", ".m4a", ".wav", ".aac", ".alac")
    for (ext in extensions) {
        if (name.contains(ext, ignoreCase = true)) {
            name = name.substringBeforeLast(ext, name)
        }
    }
    if (name.contains("@QualityPixels", ignoreCase = true)) {
        name = name.substringBefore("@QualityPixels", name)
    }
    return name.trim()
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun SharedTrackRow(
    track: Track,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    accentColor: Color,
    onClick: (Track) -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onSelectionToggled: ((Track) -> Unit)? = null,
    onOverflowClick: ((Track) -> Unit)? = null
) {
    val cleanTitle = remember(track.title, track.uri) { cleanTrackTitle(track.title, track.uri) }
    val durationStr = remember(track.duration) { track.duration.toTimeString() }

    val ext = remember(track.mimeType) {
        val rawExt = track.mimeType.substringAfterLast("/").lowercase()
        if (rawExt.isBlank() || rawExt == "mpeg") "mp3" else rawExt
    }
    val isDolby = remember(track.mimeType, track.title, track.uri) {
        track.mimeType.contains("eac3", true) || track.title.contains("leader", true) || track.uri.contains("eac3", true)
    }
    val isHiRes = remember(track.sampleRate, track.bitrate) {
        track.sampleRate > 48000 || track.bitrate > 1500000
    }

    val metaText = remember(durationStr, ext, isDolby, isHiRes, track.sampleRate) {
        when {
            isDolby -> "$durationStr | E-AC-3 JOC · 5.1"
            isHiRes -> "$durationStr | $ext | ${track.sampleRate / 1000.0} kHz | 24 bit"
            else -> "$durationStr | $ext"
        }
    }

    Surface(
        color = if (isCurrentTrack) accentColor.copy(alpha = 0.12f) else Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                if (onSelectionToggled != null && isSelected) {
                    onSelectionToggled(track)
                } else {
                    onClick(track)
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onSelectionToggled != null && isSelected) {
                IconButton(onClick = { onSelectionToggled(track) }) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Selected",
                        tint = accentColor
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!track.albumArtUri.isNullOrBlank()) {
                        GlideImage(
                            model = track.albumArtUri,
                            contentDescription = cleanTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isCurrentTrack) {
                        AnimatedEqualizerIcon(
                            isPlaying = isPlaying,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = cleanTitle,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 15.sp
                        ),
                        color = if (isCurrentTrack) accentColor else MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${track.artist.ifBlank { "Unknown Artist" }} · ${track.album.ifBlank { "Unknown Album" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = metaText,
                style = MaterialTheme.typography.labelSmall,
                color = if (isHiRes || isDolby) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (onOverflowClick != null) {
                IconButton(
                    onClick = { onOverflowClick(track) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
