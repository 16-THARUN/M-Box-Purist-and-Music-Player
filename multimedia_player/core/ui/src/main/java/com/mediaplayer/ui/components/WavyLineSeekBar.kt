package com.mediaplayer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import com.mediaplayer.data.model.toTimeString
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun WavyLineSeekBar(
    currentPositionMs: Long,
    totalDurationMs: Long,
    isPlaying: Boolean,
    accentColor: Color,
    onSeek: (Long) -> Unit,
    onDraggingChange: ((Boolean, Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val context = LocalContext.current

    val duration = if (totalDurationMs > 0) totalDurationMs else 1L
    val actualProgress = (currentPositionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    val displayProgress = if (isDragging) dragProgress else actualProgress

    val isReduceMotion = remember(context) {
        try {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.TRANSITION_ANIMATION_SCALE, 1f
            ) == 0f ||
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f
            ) == 0f
        } catch (e: Exception) {
            false
        }
    }

    val peakAmplitudePx by animateFloatAsState(
        targetValue = if (isPlaying && !isDragging && !isReduceMotion) with(density) { 3.dp.toPx() } else 0f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "WaveAmplitude"
    )

    val thumbRadiusPx by animateFloatAsState(
        targetValue = if (isDragging) with(density) { 9.dp.toPx() } else with(density) { 6.dp.toPx() },
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f),
        label = "ThumbRadius"
    )

    var wavePhase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying, isDragging, isReduceMotion) {
        if (isPlaying && !isDragging && !isReduceMotion) {
            var lastTime = withFrameNanos { it }
            while (true) {
                withFrameNanos { currentTime ->
                    val deltaNanos = currentTime - lastTime
                    lastTime = currentTime
                    val deltaSeconds = deltaNanos / 1_000_000_000f
                    wavePhase = (wavePhase + deltaSeconds * (2f * PI.toFloat() / 1.5f)) % (2f * PI.toFloat())
                }
            }
        }
    }

    val posStr = (if (isDragging) (dragProgress * duration).toLong() else currentPositionMs).toTimeString()
    val durStr = totalDurationMs.toTimeString()

    val path = remember { Path() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 24.dp)
            .semantics {
                contentDescription = "Seek, $posStr of $durStr"
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
                            val newProg = (tapOffset.x / widthPx).coerceIn(0f, 1f)
                            onSeek((newProg * duration).toLong())
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
        Canvas(modifier = Modifier.fillMaxSize()) {
            val widthPx = size.width
            val heightPx = size.height
            val centerY = heightPx / 2f

            val strokeWidthPx = 4.dp.toPx()
            val wavelengthPx = 24.dp.toPx()
            val thumbXPx = (displayProgress * widthPx).coerceIn(0f, widthPx)
            val fadeDistPx = 12.dp.toPx()

            if (thumbXPx < widthPx) {
                drawLine(
                    color = Color.White.copy(alpha = 0.25f),
                    start = Offset(thumbXPx, centerY),
                    end = Offset(widthPx, centerY),
                    strokeWidth = strokeWidthPx,
                    cap = StrokeCap.Round
                )
            }

            if (thumbXPx > 0f) {
                path.reset()
                path.moveTo(0f, centerY)

                val stepPx = 2f
                var x = 0f
                while (x <= thumbXPx) {
                    val distToThumb = thumbXPx - x
                    val fadeFactor = (distToThumb / fadeDistPx).coerceIn(0f, 1f)
                    val currentAmplitude = peakAmplitudePx * fadeFactor

                    val y = if (currentAmplitude > 0.1f) {
                        centerY + sin((2f * PI.toFloat() * x / wavelengthPx) - wavePhase) * currentAmplitude
                    } else {
                        centerY
                    }

                    if (x == 0f) {
                        path.moveTo(0f, y)
                    } else {
                        path.lineTo(x, y)
                    }
                    x += stepPx
                }
                path.lineTo(thumbXPx, centerY)

                drawPath(
                    path = path,
                    color = accentColor,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                )
            }

            drawCircle(
                color = accentColor,
                radius = thumbRadiusPx,
                center = Offset(thumbXPx, centerY)
            )

            if (isDragging) {
                drawCircle(
                    color = Color.White,
                    radius = thumbRadiusPx * 0.45f,
                    center = Offset(thumbXPx, centerY)
                )
            }
        }
    }
}
