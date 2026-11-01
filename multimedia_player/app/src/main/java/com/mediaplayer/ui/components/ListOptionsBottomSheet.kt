package com.mediaplayer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mediaplayer.data.prefs.ListType

enum class SortOption(val label: String) {
    TRACK_NUMBER("By track #"),
    DISC_TRACK_NUMBER("By disc and track #"),
    TITLE("By title"),
    FILENAME("By filename"),
    PATH("By path"),
    ARTIST("By artist"),
    ALBUM("By album"),
    YEAR("By year"),
    YEAR_ALBUM("By year-album"),
    DATE_ADDED("By date added"),
    DURATION("By duration"),
    ITEM_COUNT("By number of tracks")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListOptionsBottomSheet(
    listType: ListType,
    currentSort: SortOption,
    isReverse: Boolean,
    onSortSelected: (SortOption) -> Unit,
    onReverseToggled: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
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
            var showDisplaySettings by remember { mutableStateOf(false) }
            var showTitlesOnly by remember { mutableStateOf(false) }
            var rememberListPosition by remember { mutableStateOf(true) }
            var rememberPerTrackProgress by remember { mutableStateOf(false) }

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "List Options: ${listType.displayName}",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        text = listType.subtitleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }

                IconButton(onClick = { showDisplaySettings = !showDisplaySettings }) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Display Options",
                        tint = Color(0xFFFF5722)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Reverse Checkbox Row
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E2228),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Reverse Order",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White
                        )
                        Text(
                            text = "Sort items in descending Z-A order",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }

                    Checkbox(
                        checked = isReverse,
                        onCheckedChange = onReverseToggled
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E2228),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Show Titles Only", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                            Text("Hide subtitles & thumbnails for compact density", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f))
                        }
                        Switch(checked = showTitlesOnly, onCheckedChange = { showTitlesOnly = it })
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.1f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("List Position Memory", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                            Text("Remember scroll position per list type and restore it on return", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f))
                        }
                        Checkbox(checked = rememberListPosition, onCheckedChange = { rememberListPosition = it })
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.1f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Per Track Progress", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                            Text("Remember where each song was stopped and resume from there", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f))
                        }
                        Checkbox(checked = rememberPerTrackProgress, onCheckedChange = { rememberPerTrackProgress = it })
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Sort Order",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val availableSortOptions = when (listType) {
                ListType.ALL_SONGS, ListType.FOLDER_SONGS, ListType.ALBUM_SONGS, ListType.ARTIST_SONGS, ListType.GENRE_SONGS, ListType.YEAR_SONGS, ListType.COMPOSER_SONGS -> listOf(
                    SortOption.TRACK_NUMBER, SortOption.DISC_TRACK_NUMBER, SortOption.TITLE, SortOption.FILENAME, SortOption.PATH, SortOption.ARTIST, SortOption.ALBUM, SortOption.YEAR, SortOption.YEAR_ALBUM, SortOption.DATE_ADDED, SortOption.DURATION
                )
                ListType.ALBUMS -> listOf(SortOption.TITLE, SortOption.ARTIST, SortOption.YEAR, SortOption.DATE_ADDED, SortOption.ITEM_COUNT)
                ListType.ARTISTS, ListType.ALBUM_ARTISTS, ListType.COMPOSERS, ListType.GENRES -> listOf(SortOption.TITLE, SortOption.ITEM_COUNT)
                ListType.FOLDERS, ListType.FOLDERS_HIERARCHY -> listOf(SortOption.TITLE, SortOption.PATH, SortOption.ITEM_COUNT, SortOption.DATE_ADDED)
                ListType.YEARS -> listOf(SortOption.YEAR, SortOption.ITEM_COUNT)
                ListType.PLAYLISTS -> listOf(SortOption.TITLE, SortOption.DATE_ADDED, SortOption.ITEM_COUNT)
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
            ) {
                items(availableSortOptions) { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSortSelected(option) }
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        RadioButton(
                            selected = option == currentSort,
                            onClick = { onSortSelected(option) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Close", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
