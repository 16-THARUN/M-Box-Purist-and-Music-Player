package com.mediaplayer.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Poweramp-style FastScrollbar:
 * - Single cohesive unit: Thin 4dp track + ONE letter-index thumb knob.
 * - Always anchored to the END (right edge) of the list container.
 * - Letter bubble IS the thumb (no separate buttons or floating popups).
 */
@Composable
fun FastScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    getSectionText: ((Int) -> String?)? = null,
    minItemsForScrollbar: Int = 5,
    trackColor: Color = Color.Gray.copy(alpha = 0.25f),
    thumbBackgroundColor: Color = MaterialTheme.colorScheme.primaryContainer,
    thumbTextColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    enableDimScrim: Boolean = true
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Drag and container height state
    var isDragging by remember { mutableStateOf(false) }
    var dragOffsetYPx by remember { mutableFloatStateOf(0f) }
    var containerHeightPx by remember { mutableFloatStateOf(0f) }

    // Single unified thumb dimensions (a sleek rounded-rect letter bubble)
    val thumbWidthDp = 36.dp
    val thumbHeightDp = 32.dp
    val thumbHeightPx = with(density) { thumbHeightDp.toPx() }

    val availableTravelPx = (containerHeightPx - thumbHeightPx).coerceAtLeast(0f)

    // Calculate scroll progress (0f to 1f) from LazyListState
    val scrollProgress by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val visibleItems = layoutInfo.visibleItemsInfo
            if (totalItems <= minItemsForScrollbar || visibleItems.isEmpty()) {
                return@derivedStateOf 0f
            }

            val firstVisibleIndex = listState.firstVisibleItemIndex
            val firstVisibleOffset = listState.firstVisibleItemScrollOffset

            val avgItemSize = visibleItems.sumOf { it.size }.toFloat() / visibleItems.size
            if (avgItemSize <= 0f) return@derivedStateOf 0f

            val exactIndex = firstVisibleIndex + (firstVisibleOffset.toFloat() / avgItemSize)
            val maxScrollIndex = (totalItems - visibleItems.size).coerceAtLeast(1)

            (exactIndex / maxScrollIndex).coerceIn(0f, 1f)
        }
    }

    // Determine current thumb vertical offset Y in pixels
    val currentOffsetYPx = if (isDragging) dragOffsetYPx else scrollProgress * availableTravelPx

    // Determine item index for the section letter
    val currentItemIndex by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            if (totalItems <= 0) return@derivedStateOf 0
            if (isDragging && availableTravelPx > 0f) {
                val dragProgress = (dragOffsetYPx / availableTravelPx).coerceIn(0f, 1f)
                (dragProgress * (totalItems - 1)).toInt().coerceIn(0, totalItems - 1)
            } else {
                listState.firstVisibleItemIndex.coerceIn(0, (totalItems - 1).coerceAtLeast(0))
            }
        }
    }

    // Section letter/prefix text (e.g. "A", "B", "J", "S", "#")
    val sectionText = remember(currentItemIndex) {
        getSectionText?.invoke(currentItemIndex)?.takeIf { it.isNotBlank() } ?: "•"
    }

    // Auto-fade visibility logic
    val isScrollInProgress = listState.isScrollInProgress
    var isBarActive by remember { mutableStateOf(false) }

    LaunchedEffect(isScrollInProgress, isDragging) {
        if (isScrollInProgress || isDragging) {
            isBarActive = true
        } else {
            delay(800L)
            isBarActive = false
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (isBarActive) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isBarActive) 150 else 500
        ),
        label = "FastScrollbarAlpha"
    )

    val shouldShowScrollbar by remember {
        derivedStateOf {
            val totalItemsCount = listState.layoutInfo.totalItemsCount
            val visibleItemsCount = listState.layoutInfo.visibleItemsInfo.size
            totalItemsCount > minItemsForScrollbar && totalItemsCount > visibleItemsCount
        }
    }

    if (shouldShowScrollbar) {
        // ALWAYS anchor the outer container to Alignment.CenterEnd (Right Edge)
        Box(
            modifier = modifier
                .fillMaxHeight()
                .onSizeChanged { size ->
                    containerHeightPx = size.height.toFloat()
                }
        ) {
            // Optional dimming scrim over the list while dragging
            if (isDragging && enableDimScrim) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                )
            }

            // Only render when height is measured (> 0) to avoid initial (0,0) jump
            if (containerHeightPx > 0f) {
                // SINGLE UNIFIED SCROLLBAR COLUMN (Pinned to END / Right edge)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp)
                        .fillMaxHeight()
                        .width(44.dp) // Touch target column
                        .graphicsLayer { this.alpha = alpha }
                        .pointerInput(containerHeightPx, availableTravelPx) {
                            detectVerticalDragGestures(
                                onDragStart = { offset ->
                                    isDragging = true
                                    dragOffsetYPx = (offset.y - thumbHeightPx / 2f).coerceIn(0f, availableTravelPx)
                                    val progress = if (availableTravelPx > 0) dragOffsetYPx / availableTravelPx else 0f
                                    val totalItems = listState.layoutInfo.totalItemsCount
                                    if (totalItems > 0) {
                                        val targetIdx = (progress * (totalItems - 1)).toInt().coerceIn(0, totalItems - 1)
                                        coroutineScope.launch {
                                            listState.scrollToItem(targetIdx)
                                        }
                                    }
                                },
                                onDragEnd = { isDragging = false },
                                onDragCancel = { isDragging = false },
                                onVerticalDrag = { change, dragAmount ->
                                    dragOffsetYPx = (dragOffsetYPx + dragAmount).coerceIn(0f, availableTravelPx)
                                    val progress = if (availableTravelPx > 0) dragOffsetYPx / availableTravelPx else 0f
                                    val totalItems = listState.layoutInfo.totalItemsCount
                                    if (totalItems > 0) {
                                        val targetIdx = (progress * (totalItems - 1)).toInt().coerceIn(0, totalItems - 1)
                                        coroutineScope.launch {
                                            listState.scrollToItem(targetIdx)
                                        }
                                    }
                                    change.consume()
                                }
                            )
                        }
                ) {
                    // 1. Thin 4dp Track inset vertically, centered horizontally in the 44dp column
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .width(4.dp)
                            .fillMaxHeight()
                            .padding(vertical = (thumbHeightDp / 2))
                            .background(trackColor, CircleShape)
                    )

                    // 2. THE SINGLE UNIFIED THUMB KNOB (Letter Bubble directly ON the track)
                    // Centered horizontally on the 4dp track (concentric), Y-offset driven by scroll/drag
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = thumbBackgroundColor,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset { IntOffset(x = 0, y = currentOffsetYPx.roundToInt()) }
                            .width(thumbWidthDp)
                            .height(thumbHeightDp)
                            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = sectionText,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    textAlign = TextAlign.Center
                                ),
                                color = thumbTextColor,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
