package com.mediaplayer.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class CategoryHomeItem(
    val id: Int,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconColor: Color
)

val libraryCategories = listOf(
    CategoryHomeItem(0, "All Songs", "All tracks in your library", Icons.Rounded.MusicNote, Color(0xFF00E5FF)),
    CategoryHomeItem(4, "Folders", "Flat list of music directories", Icons.Rounded.Folder, Color(0xFFFF9100)),
    CategoryHomeItem(5, "Folders Hierarchy", "Nested folder tree view", Icons.Rounded.FolderSpecial, Color(0xFFD500F9)),
    CategoryHomeItem(1, "Albums", "Grouped by album", Icons.Rounded.Album, Color(0xFFFF1744)),
    CategoryHomeItem(2, "Artists", "Grouped by track artist", Icons.Rounded.Person, Color(0xFF00E676)),
    CategoryHomeItem(6, "Album Artists", "Grouped by album artist", Icons.Rounded.Groups, Color(0xFF2979FF)),
    CategoryHomeItem(7, "Genres", "Grouped by music genre", Icons.Rounded.GraphicEq, Color(0xFFFF3D00)),
    CategoryHomeItem(8, "Years", "Grouped by release year", Icons.Rounded.CalendarToday, Color(0xFFFFEA00)),
    CategoryHomeItem(9, "Composers", "Grouped by composer", Icons.Rounded.LibraryMusic, Color(0xFF1DE9B6)),
    CategoryHomeItem(3, "Playlists", "Custom user playlists", Icons.Rounded.QueueMusic, Color(0xFFF50057))
)

@Composable
fun LibraryHomeCategoryList(
    onCategoryClick: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(libraryCategories) { _, item ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCategoryClick(item.id) },
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E2228)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(item.iconColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = item.iconColor,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = item.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }

                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}
