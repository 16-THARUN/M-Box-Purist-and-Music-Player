package com.mediaplayer.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.material3.*

data class TvMediaRow(
    val title: String,
    val items: List<TvMediaItem>
)

data class TvMediaItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val artworkUri: String? = null
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvLibraryScreen(
    rows: List<TvMediaRow>,
    onItemSelected: (TvMediaItem) -> Unit
) {
    val firstItemFocusRequester = remember { FocusRequester() }

    TvLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101014))
            .padding(vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        itemsIndexed(rows) { rowIndex, row ->
            Column(modifier = Modifier.padding(start = 48.dp)) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                TvLazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(row.items) { itemIndex, item ->
                        val modifier = if (rowIndex == 0 && itemIndex == 0) {
                            Modifier.focusRequester(firstItemFocusRequester)
                        } else Modifier
                        TvMediaCard(
                            item = item,
                            modifier = modifier,
                            onClick = { onItemSelected(item) }
                        )
                    }
                }
            }
        }
    }

    // Restore initial D-pad focus to the first card on entry
    LaunchedEffect(Unit) {
        firstItemFocusRequester.requestFocus()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvMediaCard(
    item: TvMediaItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(width = 160.dp, height = 200.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF1E1E24),
            focusedContainerColor = Color(0xFFFF7A00)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF2A2A2A))
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                maxLines = 1
            )
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1
            )
        }
    }
}
