package com.mediaplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediaplayer.data.model.toTimeString

@Composable
fun PillSeekBar(
    currentPositionMs: Long,
    totalDurationMs: Long,
    accentColor: Color,
    onSeek: (Long) -> Unit,
    onDraggingChange: ((Boolean, Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    val duration = if (totalDurationMs > 0) totalDurationMs else 1L
    val actualProgress = (currentPositionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    var showRemainingTime by remember { mutableStateOf(false) }

    val displayProgress = if (isDragging) dragProgress else actualProgress
    val currentPosMs = if (isDragging) (dragProgress * duration).toLong() else currentPositionMs

    val posStr = currentPosMs.toTimeString()
    val rightTimeStr = if (showRemainingTime && duration > 0) {
        "-${(duration - currentPosMs).coerceAtLeast(0L).toTimeString()}"
    } else {
        duration.toTimeString()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 24.dp)
            .semantics {
                contentDescription = "Seek, $posStr of $rightTimeStr"
                setProgress { targetVal ->
                    onSeek((targetVal * duration).toLong())
                    true
                }
            }
            .pointerInput(duration) {
                detectTapGestures(
                    onTap = { tapOffset ->
                        val widthPx = size.width.toFloat()
                        if (widthPx > 0) {
                            val remainingToggleZonePx = widthPx - density.density * 60f
                            if (tapOffset.x >= remainingToggleZonePx) {
                                showRemainingTime = !showRemainingTime
                            } else {
                                val newProg = (tapOffset.x / widthPx).coerceIn(0f, 1f)
                                onSeek((newProg * duration).toLong())
                            }
                        }
                    }
                )
            }
            .pointerInput(duration) {
                detectDragGestures(
                    onDragStart = { startOffset ->
                        isDragging = true
                        val widthPx = size.width.toFloat()
                        if (widthPx > 0) {
                            dragProgress = (startOffset.x / widthPx).coerceIn(0f, 1f)
                            val targetMs = (dragProgress * duration).toLong()
                            onDraggingChange?.invoke(true, targetMs)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    onDragEnd = {
                        if (isDragging) {
                            val targetMs = (dragProgress * duration).toLong()
                            onSeek(targetMs)
                            onDraggingChange?.invoke(false, targetMs)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            isDragging = false
                        }
                    },
                    onDragCancel = {
                        if (isDragging) {
                            isDragging = false
                            onDraggingChange?.invoke(false, currentPositionMs)
                        }
                    },
                    onDrag = { change, _ ->
                        if (isDragging) {
                            val widthPx = size.width.toFloat()
                            if (widthPx > 0) {
                                dragProgress = (change.position.x / widthPx).coerceIn(0f, 1f)
                                val targetMs = (dragProgress * duration).toLong()
                                onDraggingChange?.invoke(true, targetMs)
                                change.consume()
                            }
                        }
                    }
                )
            }
    ) {
        val pillHeightDp = 30.dp

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(pillHeightDp)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color.White.copy(alpha = 0.15f))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val widthPx = size.width
                val heightPx = size.height
                val filledWidthPx = (displayProgress * widthPx).coerceIn(0f, widthPx)

                if (filledWidthPx > 0f) {
                    drawRect(
                        color = accentColor,
                        size = Size(filledWidthPx, heightPx)
                    )
                }

                if (filledWidthPx > 0f && filledWidthPx < widthPx) {
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(filledWidthPx - 1.5.dp.toPx(), 0f),
                        size = Size(3.dp.toPx(), heightPx)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = posStr,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        shadow = Shadow(color = Color.Black.copy(alpha = 0.6f), blurRadius = 4f)
                    ),
                    color = Color.White
                )

                Text(
                    text = rightTimeStr,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        shadow = Shadow(color = Color.Black.copy(alpha = 0.6f), blurRadius = 4f)
                    ),
                    color = Color.White
                )
            }
        }
    }
}
