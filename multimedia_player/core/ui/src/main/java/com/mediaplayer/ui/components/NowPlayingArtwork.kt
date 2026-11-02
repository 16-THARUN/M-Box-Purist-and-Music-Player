package com.mediaplayer.ui.components

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.mediaplayer.data.model.NowPlayingState
import com.mediaplayer.data.model.PlayerController
import kotlinx.coroutines.delay
import kotlin.math.*

enum class ArtworkDisplayMode {
    VINYL,
    LYRICS,
    COVER_ART
}

private fun angleDeg(p: Offset, c: Offset): Float =
    Math.toDegrees(atan2((p.y - c.y).toDouble(), (p.x - c.x).toDouble())).toFloat()

@Composable
fun Modifier.turntableScratch(
    enabled: Boolean,
    durationMs: Long,
    currentPositionMs: () -> Long,
    isPlaying: () -> Boolean,
    rotationDeg: () -> Float,
    onRotation: (Float) -> Unit,
    onScratching: (Boolean) -> Unit,
    onScrubPosition: (Long) -> Unit,
    onSeek: (positionMs: Long) -> Unit,
    onScratchEnd: (positionMs: Long, wasPlaying: Boolean) -> Unit
): Modifier {
    val duration by rememberUpdatedState(durationMs)
    val posNow by rememberUpdatedState(currentPositionMs)
    val playingNow by rememberUpdatedState(isPlaying)
    val rotNow by rememberUpdatedState(rotationDeg)
    val rotCb by rememberUpdatedState(onRotation)
    val scratchCb by rememberUpdatedState(onScratching)
    val scrubCb by rememberUpdatedState(onScrubPosition)
    val seekCb by rememberUpdatedState(onSeek)
    val endCb by rememberUpdatedState(onScratchEnd)
    if (!enabled) return this

    return this.pointerInput(Unit) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = minOf(size.width, size.height) / 2f
            val r0 = (down.position - center).getDistance()
            val dur = duration
            if (r0 > radius || r0 < radius * 0.15f || dur <= 0L) return@awaitEachGesture

            val msPerDeg = (dur * 0.10f).coerceIn(10_000f, 60_000f) / 360f
            val maxPos = (dur - 1_000L).coerceAtLeast(0L)
            var prevAngle = angleDeg(down.position, center)
            var turned = 0f
            var scratching = false
            var startPos = 0L
            var startRot = 0f
            var wasPlaying = false
            var target = 0L
            var lastSeekPos = Long.MIN_VALUE
            var lastSeekAt = 0L

            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break

                    val p = change.position
                    val dist = (p - center).getDistance()
                    val a = angleDeg(p, center)
                    var d = a - prevAngle
                    if (d > 180f) d -= 360f
                    if (d < -180f) d += 360f
                    prevAngle = a
                    if (dist >= radius * 0.15f) turned += d

                    if (!scratching) {
                        if ((p - down.position).getDistance() < slop) continue
                        scratching = true
                        startPos = posNow()
                        startRot = rotNow()
                        wasPlaying = playingNow()
                        scratchCb(true)
                    }
                    change.consume()

                    rotCb(((startRot + turned) % 360f + 360f) % 360f)
                    target = (startPos + (turned * msPerDeg).toLong()).coerceIn(0L, maxPos)
                    scrubCb(target)

                    Log.d("MBoxTurn", "rawAngle=%.1f, delta=%.1f, totalTurned=%.1f, target=%d".format(a, d, turned, target))

                    val now = change.uptimeMillis
                    if (now - lastSeekAt >= 120 && kotlin.math.abs(target - lastSeekPos) >= 250) {
                        seekCb(target)
                        lastSeekPos = target
                        lastSeekAt = now
                    }
                }
            } finally {
                if (scratching) {
                    endCb(target, wasPlaying)
                    scratchCb(false)
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun NowPlayingArtwork(
    imageUri: String?,
    isPlaying: Boolean,
    playerState: NowPlayingState,
    onSeekTo: (Long) -> Unit,
    onPauseForDjHold: () -> Unit = {},
    onResumeAfterDjHold: () -> Unit = {},
    displayMode: ArtworkDisplayMode = ArtworkDisplayMode.VINYL,
    onDisplayModeChange: ((ArtworkDisplayMode) -> Unit)? = null,
    playerVm: PlayerController? = null,
    modifier: Modifier = Modifier
) {
    var isTransitioning by remember { mutableStateOf(false) }

    fun cycleMode() {
        if (isTransitioning) return
        isTransitioning = true
        val nextMode = when (displayMode) {
            ArtworkDisplayMode.VINYL -> ArtworkDisplayMode.LYRICS
            ArtworkDisplayMode.LYRICS -> ArtworkDisplayMode.COVER_ART
            ArtworkDisplayMode.COVER_ART -> ArtworkDisplayMode.VINYL
        }
        onDisplayModeChange?.invoke(nextMode)
    }

    LaunchedEffect(displayMode) {
        delay(350L)
        isTransitioning = false
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(displayMode, isTransitioning) {
                detectTapGestures(
                    onDoubleTap = {
                        cycleMode()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = displayMode,
            transitionSpec = {
                (fadeIn(animationSpec = tween(500)) + scaleIn(initialScale = 0.9f))
                    .togetherWith(fadeOut(animationSpec = tween(500)) + scaleOut(targetScale = 0.9f))
            },
            label = "ArtworkTransition"
        ) { mode ->
            when (mode) {
                ArtworkDisplayMode.VINYL -> {
                    SpinningVinyl(
                        imageUri = imageUri,
                        isPlaying = isPlaying,
                        trackKey = playerState.currentTrack?.id?.toString() ?: playerState.currentTrack?.uri ?: "",
                        onSeekTo = onSeekTo,
                        positionMs = playerState.positionMs,
                        durationMs = playerState.durationMs,
                        onPauseForDjHold = onPauseForDjHold,
                        onResumeAfterDjHold = onResumeAfterDjHold,
                        onDoubleTapModeSwitch = { cycleMode() },
                        playerVm = playerVm
                    )
                }
                ArtworkDisplayMode.LYRICS -> {
                    LyricsOverlay(
                        state = playerState,
                        onSeekTo = onSeekTo,
                        onDoubleTapModeSwitch = { cycleMode() }
                    )
                }
                ArtworkDisplayMode.COVER_ART -> {
                    ClassicCoverArt(
                        imageUri = imageUri,
                        onDoubleTapModeSwitch = { cycleMode() }
                    )
                }
            }
        }
    }
}

@Composable
fun SpinningVinyl(
    imageUri: String?,
    isPlaying: Boolean,
    trackKey: String = "",
    onSeekTo: (Long) -> Unit,
    positionMs: Long,
    durationMs: Long,
    onPauseForDjHold: () -> Unit = {},
    onResumeAfterDjHold: () -> Unit = {},
    onDoubleTapModeSwitch: () -> Unit,
    playerVm: PlayerController? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var rotation by rememberSaveable { mutableFloatStateOf(0f) }
    var isScratching by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableLongStateOf(positionMs) }

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

    LaunchedEffect(isPlaying, isScratching, trackKey, isReduceMotion) {
        if (isPlaying && !isScratching && !isReduceMotion) {
            var last = withFrameMillis { it }
            while (true) {
                val now = withFrameMillis { it }
                rotation = (rotation + (now - last) * 0.06f) % 360f
                last = now
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp)
            .semantics {
                contentDescription = "Vinyl disc"
                customActions = listOf(
                    CustomAccessibilityAction("Forward 10 seconds") {
                        onSeekTo((positionMs + 10_000L).coerceAtMost((durationMs - 1000L).coerceAtLeast(0L)))
                        true
                    },
                    CustomAccessibilityAction("Back 10 seconds") {
                        onSeekTo((positionMs - 10_000L).coerceAtLeast(0L))
                        true
                    }
                )
            }
            .turntableScratch(
                enabled = true,
                durationMs = durationMs,
                currentPositionMs = { positionMs },
                isPlaying = { isPlaying },
                rotationDeg = { rotation },
                onRotation = { rotation = it },
                onScratching = { isScratching = it },
                onScrubPosition = { scrubPosition = it },
                onSeek = { target ->
                    if (playerVm != null) {
                        playerVm?.seekLive(target, source = "turntable")
                    } else {
                        onSeekTo(target)
                    }
                },
                onScratchEnd = { target, wasPlaying ->
                    if (playerVm != null) {
                        playerVm?.seekFinal(target, resume = wasPlaying, source = "turntable")
                    } else {
                        onSeekTo(target)
                    }
                }
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        onDoubleTapModeSwitch()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotation }
                .shadow(16.dp, CircleShape),
            shape = CircleShape,
            color = Color(0xFF111111),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                repeat(20) { i ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize(0.98f - (i * 0.02f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.03f), CircleShape)
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxSize(0.55f),
                    shape = CircleShape,
                    color = Color(0xFF222222),
                    border = BorderStroke(2.dp, Color.Black)
                ) {
                    if (imageUri != null) {
                        @OptIn(ExperimentalGlideComposeApi::class)
                        GlideImage(
                            model = imageUri,
                            contentDescription = "Album Art Label",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Surface(
                    modifier = Modifier.size(10.dp),
                    shape = CircleShape,
                    color = Color(0xFF0A0A0A),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {}
            }
        }
    }
}

@Composable
fun ClassicCoverArt(
    imageUri: String?,
    onDoubleTapModeSwitch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        onDoubleTapModeSwitch()
                    }
                )
            },
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 8.dp
    ) {
        if (imageUri != null) {
            @OptIn(ExperimentalGlideComposeApi::class)
            GlideImage(
                model = imageUri,
                contentDescription = "Classic Album Art",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Gray),
                contentAlignment = Alignment.Center
            ) {
                Text("No Art", color = Color.White)
            }
        }
    }
}

@Composable
fun LyricsOverlay(
    state: NowPlayingState,
    onSeekTo: (Long) -> Unit,
    onDoubleTapModeSwitch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        onDoubleTapModeSwitch()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        LyricsContent(
            lyrics = state.currentLyrics,
            positionMs = state.positionMs,
            isSyncedMode = true,
            onSeekTo = onSeekTo,
            activeLineColor = Color.White,
            inactiveLineColor = Color.White.copy(alpha = 0.70f),
            modifier = Modifier.fillMaxSize()
        )
    }
}
