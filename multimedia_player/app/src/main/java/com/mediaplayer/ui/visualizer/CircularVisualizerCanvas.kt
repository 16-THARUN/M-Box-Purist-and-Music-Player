package com.mediaplayer.ui.visualizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CircularVisualizerContainer(
    currentType: CircularVisualizerType,
    onCycleType: () -> Unit,
    magnitudes: FloatArray,
    peakDots: FloatArray,
    waveformData: FloatArray,
    accentColor: Color = Color(0xFFFF8533),
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { onCycleType() }
                }
        ) {
            val center = this.center
            val vinylRadius = size.minDimension * 0.32f
            val maxSpikeLength = size.minDimension * 0.16f

            when (currentType) {
                CircularVisualizerType.BOTTOM_GLASS_BARS -> {
                    val bars = 36
                    val angleStep = 360f / bars
                    for (i in 0 until bars) {
                        val magIndex = (i % (magnitudes.size / 2))
                        val mag = magnitudes.getOrElse(magIndex) { 0f }
                        val barHeight = 8.dp.toPx() + mag * maxSpikeLength * 0.85f

                        rotate(degrees = i * angleStep, pivot = center) {
                            drawRoundRect(
                                color = accentColor.copy(alpha = 0.45f + mag * 0.55f),
                                topLeft = Offset(center.x - 7.dp.toPx(), center.y - vinylRadius - barHeight),
                                size = Size(14.dp.toPx(), barHeight),
                                cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx())
                            )
                        }
                    }
                }

                CircularVisualizerType.CLASSIC_SYMMETRICAL -> {
                    val bars = 48
                    val angleStep = 360f / bars
                    for (i in 0 until bars) {
                        val symmIndex = if (i < bars / 2) i else (bars - 1 - i)
                        val mag = magnitudes.getOrElse(symmIndex % magnitudes.size) { 0f }
                        val peak = peakDots.getOrElse(symmIndex % peakDots.size) { 0f }

                        val barLength = 4.dp.toPx() + mag * maxSpikeLength
                        val dotOffset = 10.dp.toPx() + peak * maxSpikeLength

                        rotate(degrees = i * angleStep, pivot = center) {
                            drawLine(
                                color = accentColor.copy(alpha = 0.85f),
                                start = Offset(center.x, center.y - vinylRadius),
                                end = Offset(center.x, center.y - vinylRadius - barLength),
                                strokeWidth = 3.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = 0.9f),
                                radius = 2.5.dp.toPx(),
                                center = Offset(center.x, center.y - vinylRadius - dotOffset)
                            )
                        }
                    }
                }

                CircularVisualizerType.SPECTRUM_DOTS -> {
                    val dotColumns = 36
                    val angleStep = 360f / dotColumns
                    val rings = 4

                    for (col in 0 until dotColumns) {
                        val mag = magnitudes.getOrElse(col % magnitudes.size) { 0f }
                        for (r in 1..rings) {
                            val ringRadius = vinylRadius + (r * 11.dp.toPx()) + (mag * (r * 7.dp.toPx()))
                            val dotAlpha = (1f - (r / (rings + 1f))).coerceIn(0.2f, 1f)
                            rotate(degrees = col * angleStep, pivot = center) {
                                drawCircle(
                                    color = accentColor.copy(alpha = dotAlpha),
                                    radius = (3.dp.toPx() + mag * 2.5.dp.toPx()),
                                    center = Offset(center.x, center.y - ringRadius)
                                )
                            }
                        }
                    }
                }

                CircularVisualizerType.NARROW_BARS -> {
                    val bars = 96
                    val angleStep = 360f / bars
                    for (i in 0 until bars) {
                        val mag = magnitudes.getOrElse(i % magnitudes.size) { 0f }
                        val rayLength = 3.dp.toPx() + mag * maxSpikeLength * 1.1f

                        rotate(degrees = i * angleStep, pivot = center) {
                            drawLine(
                                color = if (i % 2 == 0) accentColor else accentColor.copy(alpha = 0.5f),
                                start = Offset(center.x, center.y - vinylRadius),
                                end = Offset(center.x, center.y - vinylRadius - rayLength),
                                strokeWidth = 1.6.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }

                CircularVisualizerType.PLAIN_OSCILLOSCOPE -> {
                    if (waveformData.isNotEmpty()) {
                        val path = Path()
                        val pointsCount = waveformData.size
                        val stepRad = (2 * PI / pointsCount)

                        for (i in 0 until pointsCount) {
                            val waveAmp = waveformData[i] * 18.dp.toPx()
                            val r = vinylRadius + 14.dp.toPx() + waveAmp
                            val angle = i * stepRad - (PI / 2)
                            val x = center.x + (r * cos(angle)).toFloat()
                            val y = center.y + (r * sin(angle)).toFloat()

                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        path.close()

                        drawPath(
                            path = path,
                            color = accentColor,
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                        drawPath(
                            path = path,
                            color = accentColor.copy(alpha = 0.25f),
                            style = Stroke(width = 7.dp.toPx())
                        )
                    }
                }
            }
        }

        content()

        TopPresetBadge(
            preset = currentType,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        )
    }
}

@Composable
fun TopPresetBadge(
    preset: CircularVisualizerType,
    modifier: Modifier = Modifier
) {
    val indexString = "${preset.ordinal + 1}/5 ${preset.title}"
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0x33000000),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                text = indexString,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White
            )
            Text(
                text = preset.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.55f)
            )
        }
    }
}
