package com.mediaplayer.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class LyricsSourceItem(
    val id: String,
    val name: String,
    val description: String,
    val enabled: Boolean,
    val providerType: String = "line"
)

val defaultLyricsSources = listOf(
    LyricsSourceItem("embedded_tag", "Embedded (file tags)", "Read MP4 ©lyr, ID3 USLT/SYLT, or FLAC LYRICS tag directly from local audio file", true, "syllable"),
    LyricsSourceItem("lyrics_plus", "LyricsPlus", "Syllable by syllable, on community mirrors", true, "syllable"),
    LyricsSourceItem("pax_senix", "PaxSenix", "Apple Music timings again, on a second host", true, "syllable"),
    LyricsSourceItem("better_lyrics", "BetterLyrics", "Apple Music timings, word by word", true, "syllable"),
    LyricsSourceItem("simp_music", "SimpMusic", "Matched on the video, so never the wrong lyrics", true, "line"),
    LyricsSourceItem("kugou", "KuGou", "Whole lines, strong outside the English catalogue", true, "line"),
    LyricsSourceItem("lrclib", "LRCLIB", "Whole lines only, and always up", true, "line"),
    LyricsSourceItem("musixmatch", "Musixmatch", "Whole lines, from the biggest lyrics database", true, "line"),
    LyricsSourceItem("genius", "Genius", "Plain text fallback, massive web catalogue", true, "plain")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSourcesScreen(
    onDismiss: () -> Unit
) {
    var sources by remember { mutableStateOf(defaultLyricsSources) }
    var prioritizeSyllable by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF14171A),
        contentColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Lyrics sources",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Sources are tried in this order. Drag or tap to reorder them. The first source with lyrics wins unless syllable lyrics are prioritized.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
            ) {
                itemsIndexed(sources) { index, item ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E2228)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DragHandle,
                                contentDescription = "Reorder",
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.padding(end = 12.dp)
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Text(
                                    text = item.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }

                            Checkbox(
                                checked = item.enabled,
                                onCheckedChange = { isChecked ->
                                    sources = sources.toMutableList().also { list ->
                                        list[index] = item.copy(enabled = isChecked)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E2228),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { prioritizeSyllable = !prioritizeSyllable }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Prioritize syllable lyrics",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = "Keep searching past a line-synced match for word-by-word lyrics.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }

                    Switch(
                        checked = prioritizeSyllable,
                        onCheckedChange = { prioritizeSyllable = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    sources = defaultLyricsSources
                    prioritizeSyllable = false
                }) {
                    Text("Reset to default", color = Color(0xFFFF5722))
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722))
                ) {
                    Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
