package com.mediaplayer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.mediaplayer.util.DeviceState
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

private data class SparkParticle(
    var angleRad: Float,
    var distPx: Float,
    var maxDistPx: Float,
    var speedPx: Float,
    var alpha: Float,
    var sizePx: Float
)

@Composable
fun OrbitingStardustVisualizer(
    fftData: FloatArray,
    accentColor: Color,
    deviceState: DeviceState,
    vinylScale: Float,
    centerOffset: Offset? = null,
    vinylRadiusOverride: Float = 0f,
    modifier: Modifier = Modifier
) {
    val particleCount = 28
    val animatedData = remember { FloatArray(particleCount) }
    val targetData = remember { FloatArray(particleCount) }
    val autoGainSmoother = remember { AutoGainBandSmoother(particleCount, 12f, 110f) }

    var tick by remember { mutableIntStateOf(0) }
    var lastUpdateTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var lastNanos by remember { mutableLongStateOf(0L) }

    val density = LocalDensity.current.density

    val transition = rememberInfiniteTransition(label = "StardustRotation")
    val rotationAngle by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing)), label = "rot"
    )

    val sparks = remember { mutableStateListOf<SparkParticle>() }
    var lastBeatTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(fftData) {
        if (fftData.isNotEmpty()) {
            lastUpdateTime = System.currentTimeMillis()
            val len = fftData.size
            for (i in 0 until particleCount) {
                val bandIdx = mapSymmetricBandIndex(i, particleCount, len)
                targetData[i] = fftData[bandIdx]
            }

            val bassEnergy = targetData[0]
            val now = System.currentTimeMillis()
            if (bassEnergy > 0.70f && now - lastBeatTime > 250L) {
                lastBeatTime = now
                val sparkCount = Random.nextInt(6, 11)
                for (k in 0 until sparkCount) {
                    sparks.add(
                        SparkParticle(
                            angleRad = Random.nextFloat() * 2f * Math.PI.toFloat(),
                            distPx = 0f,
                            maxDistPx = (Random.nextFloat() * 80f + 60f) * density,
                            speedPx = (Random.nextFloat() * 150f + 100f) * density,
                            alpha = 1.0f,
                            sizePx = (Random.nextFloat() * 2f + 2f) * density
                        )
                    )
                }
            }
        }
    }

    val frameDelay = when (deviceState) {
        DeviceState.OPTIMAL -> 0L
        DeviceState.LOW_BATTERY -> 32L
        DeviceState.POWER_SAVER -> 64L
    }

    LaunchedEffect(deviceState) {
        while (true) {
            withFrameNanos { now ->
                val dtMs = if (lastNanos > 0L) ((now - lastNanos) / 1_000_000f).coerceIn(1f, 50f) else 16f
                lastNanos = now

                if (System.currentTimeMillis() - lastUpdateTime > 150L) {
                    for (i in 0 until particleCount) targetData[i] = 0f
                }

                autoGainSmoother.process(targetData, dtMs, animatedData)

                val dtSec = dtMs / 1000f
                val iterator = sparks.iterator()
                while (iterator.hasNext()) {
                    val s = iterator.next()
                    s.distPx += s.speedPx * dtSec
                    s.alpha = (1f - (s.distPx / s.maxDistPx)).coerceIn(0f, 1f)
                    if (s.distPx >= s.maxDistPx || s.alpha <= 0f) {
                        iterator.remove()
                    }
                }

                tick++
            }
            if (frameDelay > 0) delay(frameDelay.milliseconds)
        }
    }

    val drawColor = remember(accentColor) { getLightenedAccentColor(accentColor) }

    Canvas(modifier = modifier.fillMaxSize()) {
        @Suppress("UNUSED_VARIABLE")
        val _t = tick

        val center = centerOffset ?: Offset(size.width / 2f, size.height / 2f)
        val R = if (vinylRadiusOverride > 0f) vinylRadiusOverride else min(size.width, size.height) * 0.40f

        val baseOrbitR = R + 12.dp.toPx()
        val maxOrbitR = R + 60.dp.toPx()
        val rotRad = Math.toRadians(rotationAngle.toDouble())

        for (i in 0 until particleCount) {
            val energy = animatedData[i].coerceIn(0f, 1f)
            val baseAngle = (i * (2.0 * Math.PI / particleCount))
            val angle = baseAngle + rotRad

            val currentOrbit = baseOrbitR + energy * (maxOrbitR - baseOrbitR)
            val dotRadius = 2.dp.toPx() + energy * 3.dp.toPx()
            val mainAlpha = 0.40f + energy * 0.60f

            drawCircle(
                color = drawColor.copy(alpha = mainAlpha),
                radius = dotRadius,
                center = Offset(
                    center.x + (currentOrbit * cos(angle)).toFloat(),
                    center.y + (currentOrbit * sin(angle)).toFloat()
                )
            )

            for (t in 1..3) {
                val trailAngle = angle - (t * 0.05)
                val trailAlpha = mainAlpha * (1f - t * 0.28f)
                val trailRadius = dotRadius * (1f - t * 0.20f)
                if (trailAlpha > 0f && trailRadius > 0f) {
                    drawCircle(
                        color = drawColor.copy(alpha = trailAlpha),
                        radius = trailRadius,
                        center = Offset(
                            center.x + (currentOrbit * cos(trailAngle)).toFloat(),
                            center.y + (currentOrbit * sin(trailAngle)).toFloat()
                        )
                    )
                }
            }
        }

        for (s in sparks) {
            val sparkPos = Offset(
                center.x + ((R + s.distPx) * cos(s.angleRad.toDouble())).toFloat(),
                center.y + ((R + s.distPx) * sin(s.angleRad.toDouble())).toFloat()
            )
            drawCircle(
                color = Color.White.copy(alpha = s.alpha),
                radius = s.sizePx,
                center = sparkPos
            )
        }
    }
}
