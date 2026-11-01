package com.mediaplayer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediaplayer.data.model.LyricLine
import kotlinx.coroutines.delay

@Composable
fun LyricsContent(
    lyrics: List<LyricLine>,
    positionMs: Long,
    isSyncedMode: Boolean,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    activeLineColor: Color = Color.White,
    inactiveLineColor: Color = Color.White.copy(alpha = 0.65f),
    accentColor: Color = Color.Unspecified,
    onItemClick: ((LyricLine) -> Unit)? = null
) {
    val isSynced = remember(lyrics) { lyrics.any { it.startTimeMs > 0 } }
    val currentActiveLineIndex = remember(positionMs, lyrics, isSyncedMode, isSynced) {
        if (!isSyncedMode || !isSynced || lyrics.isEmpty()) -1
        else lyrics.indexOfLast { it.startTimeMs <= positionMs }.coerceAtLeast(0)
    }

    val listState = rememberLazyListState()
    val isUserDragging by listState.interactionSource.collectIsDraggedAsState()
    var isUserBrowsing by remember { mutableStateOf(false) }
    var idleTimeoutKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(isUserDragging) {
        if (isUserDragging) {
            isUserBrowsing = true
            idleTimeoutKey++
        }
    }

    LaunchedEffect(currentActiveLineIndex, isUserBrowsing, isSyncedMode) {
        if (isSyncedMode && !isUserBrowsing && currentActiveLineIndex >= 0 && lyrics.isNotEmpty()) {
            val layoutInfo = listState.layoutInfo
            val viewportHeight = layoutInfo.viewportSize.height
            val activeItemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentActiveLineIndex }
            val approxItemHeightPx = activeItemInfo?.size ?: with(listState.layoutInfo) {
                visibleItemsInfo.firstOrNull()?.size ?: 100
            }
            val targetOffset = -((viewportHeight * 0.30f) - (approxItemHeightPx / 2f)).toInt()

            listState.animateScrollToItem(
                index = currentActiveLineIndex,
                scrollOffset = targetOffset
            )
        }
    }

    LaunchedEffect(idleTimeoutKey, isUserBrowsing) {
        if (isUserBrowsing) {
            delay(4000L)
            isUserBrowsing = false
        }
    }

    val isActiveLineVisible = remember(listState.layoutInfo.visibleItemsInfo, currentActiveLineIndex) {
        listState.layoutInfo.visibleItemsInfo.any { it.index == currentActiveLineIndex }
    }

    val fadeBrush = remember {
        Brush.verticalGradient(
            0.0f to Color.Transparent,
            0.06f to Color.Black,
            0.94f to Color.Black,
            1.0f to Color.Transparent
        )
    }

    val density = LocalDensity.current
    var listViewportHeightPx by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier.onGloballyPositioned { coords ->
            listViewportHeightPx = coords.size.height
        }
    ) {
        if (lyrics.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No lyrics available",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            val topPaddingDp = if (isSyncedMode) with(density) { (listViewportHeightPx * 0.30f).toDp().coerceAtLeast(16.dp) } else 16.dp
            val bottomPaddingDp = if (isSyncedMode) with(density) { (listViewportHeightPx * 0.50f).toDp().coerceAtLeast(32.dp) } else 32.dp

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.99f }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = fadeBrush,
                            blendMode = BlendMode.DstIn
                        )
                    }
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = PaddingValues(top = topPaddingDp, bottom = bottomPaddingDp)
                ) {
                    itemsIndexed(lyrics) { index, line ->
                        val isActive = isSyncedMode && isSynced && index == currentActiveLineIndex
                        val isPast = isSyncedMode && isSynced && index < currentActiveLineIndex
                        var isTapHighlighted by remember { mutableStateOf(false) }

                        val lineAlpha by animateFloatAsState(
                            targetValue = when {
                                !isSyncedMode || !isSynced -> 0.85f
                                isActive -> 1.0f
                                isPast -> 0.55f
                                else -> 0.70f
                            },
                            animationSpec = tween(durationMillis = 250),
                            label = "LineAlpha"
                        )

                        val lineScale by animateFloatAsState(
                            targetValue = if (isActive) 1.08f else 1.0f,
                            animationSpec = tween(durationMillis = 250),
                            label = "LineScale"
                        )

                        val fontSizeSp = if (isActive) 22.sp else 18.sp
                        val lineHeightSp = if (isActive) (22 * 1.35f).sp else (18 * 1.35f).sp

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp, horizontal = 12.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isTapHighlighted) Color.White.copy(alpha = 0.15f)
                                    else Color.Transparent
                                )
                                .clickable(enabled = isSyncedMode && isSynced && line.startTimeMs > 0) {
                                    isTapHighlighted = true
                                    onSeekTo(line.startTimeMs)
                                    onItemClick?.invoke(line)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            LaunchedEffect(isTapHighlighted) {
                                if (isTapHighlighted) {
                                    delay(150L)
                                    isTapHighlighted = false
                                }
                            }

                            Text(
                                text = line.text,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = fontSizeSp,
                                    lineHeight = lineHeightSp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    shadow = Shadow(
                                        color = Color.Black.copy(alpha = 0.85f),
                                        offset = Offset(0f, 2f),
                                        blurRadius = 8f
                                    )
                                ),
                                color = (if (isActive) activeLineColor else inactiveLineColor).copy(alpha = lineAlpha),
                                textAlign = TextAlign.Center,
                                softWrap = true,
                                overflow = TextOverflow.Clip,
                                modifier = Modifier
                                    .graphicsLayer {
                                        scaleX = lineScale
                                        scaleY = lineScale
                                    }
                                    .padding(horizontal = 24.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // FLOATING CHIP OVERLAY (FIX 3)
                AnimatedVisibility(
                    visible = isSyncedMode && isSynced && isUserBrowsing && !isActiveLineVisible && currentActiveLineIndex >= 0,
                    enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 2 },
                    exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 2 },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                ) {
                    Surface(
                        onClick = { isUserBrowsing = false },
                        shape = RoundedCornerShape(20.dp),
                        color = (if (accentColor != Color.Unspecified) accentColor else Color(0xFF1E1E1E)).copy(alpha = 0.95f),
                        shadowElevation = 6.dp,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Back to current line",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
