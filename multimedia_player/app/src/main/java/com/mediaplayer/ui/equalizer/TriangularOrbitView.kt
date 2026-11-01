package com.mediaplayer.ui.equalizer

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TriangularOrbitView(
    targetMids: Float,
    targetTreble: Float,
    targetBass: Float,
    modifier: Modifier = Modifier
) {
    // Smooth transitions driven purely by single-touch preset selection
    val animMids by animateFloatAsState(
        targetValue = targetMids,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "animMids"
    )
    val animTreble by animateFloatAsState(
        targetValue = targetTreble,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "animTreble"
    )
    val animBass by animateFloatAsState(
        targetValue = targetBass,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "animBass"
    )

    val polyPath = remember { Path() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(230.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val orbitRadius = 70.dp.toPx()

            // Dashed orbital guide
            drawCircle(
                color = Color.White.copy(alpha = 0.2f),
                radius = orbitRadius,
                center = Offset(cx, cy),
                style = Stroke(
                    width = 1.2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                )
            )

            // Center origin point (+0 dB)
            drawCircle(
                color = Color.White.copy(alpha = 0.4f),
                radius = 3.dp.toPx(),
                center = Offset(cx, cy)
            )

            // Dynamic Point Calculations
            val midsY = cy - (orbitRadius * 0.7f) - (animMids / 7f) * (orbitRadius * 0.45f)
            val midsPoint = Offset(cx, midsY)

            val trebleX = cx - (orbitRadius * 0.75f) - (animTreble / 7f) * (orbitRadius * 0.4f)
            val trebleY = cy + (orbitRadius * 0.4f)
            val treblePoint = Offset(trebleX, trebleY)

            val bassX = cx + (orbitRadius * 0.75f) + (animBass / 7f) * (orbitRadius * 0.4f)
            val bassY = cy + (orbitRadius * 0.4f)
            val bassPoint = Offset(bassX, bassY)

            // Shaded connecting polygon
            polyPath.reset()
            polyPath.moveTo(midsPoint.x, midsPoint.y)
            polyPath.lineTo(treblePoint.x, treblePoint.y)
            polyPath.lineTo(bassPoint.x, bassPoint.y)
            polyPath.close()

            drawPath(polyPath, color = Color.White.copy(alpha = 0.12f))
            drawPath(polyPath, color = Color.White.copy(alpha = 0.55f), style = Stroke(width = 1.8.dp.toPx()))

            // Draw Node Points
            listOf(midsPoint, treblePoint, bassPoint).forEach { pt ->
                drawCircle(color = Color.White, radius = 5.dp.toPx(), center = pt)
                drawCircle(color = Color(0xFF1B1E22), radius = 2.5.dp.toPx(), center = pt)
            }
        }

        // Real-time Text Indicators
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp)
        ) {
            Text("Mids", color = Color(0xFFA0A5AC), fontSize = 12.sp)
            Text(
                text = if (targetMids >= 0) "+${targetMids.toInt()}" else "${targetMids.toInt()}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 32.dp, top = 36.dp)
        ) {
            Text("Treble", color = Color(0xFFA0A5AC), fontSize = 12.sp)
            Text(
                text = if (targetTreble >= 0) "+${targetTreble.toInt()}" else "${targetTreble.toInt()}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 32.dp, top = 36.dp)
        ) {
            Text("Bass", color = Color(0xFFA0A5AC), fontSize = 12.sp)
            Text(
                text = if (targetBass >= 0) "+${targetBass.toInt()}" else "${targetBass.toInt()}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}
