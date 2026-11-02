package com.mediaplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediaplayer.cast.TvRemoteVolume
import kotlinx.coroutines.delay

/**
 * The phone's own TV volume slider, shown instead of Android's while casting to M-Box.
 * Looks like the Samsung TV's volume bar (the "Q-Symphony" pill): dark rounded pill, white fill
 * from the bottom, the level on top, the TV's name underneath.
 *
 * - Appears when a phone volume button is pressed (TvRemoteVolume.poke), hides after 2.5 s.
 * - Drag up/down (or tap) to choose a level: the phone presses the TV's own volume keys one at a
 *   time until the TV reaches it (PlayerViewModel.moveTvVolumeTo) — it can never jump.
 * - The number always comes back from the TV (never guessed).
 */
@Composable
fun TvVolumeOverlay() {
    val ui by TvRemoteVolume.ui.collectAsState()
    if (!ui.active) return

    var visible by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var wanted by remember { mutableIntStateOf(-1) }      // level under the finger, -1 = none
    var touchAt by remember { mutableLongStateOf(0L) }

    LaunchedEffect(ui.poke, touchAt, dragging) {
        if (ui.poke == 0L && touchAt == 0L) return@LaunchedEffect
        visible = true
        if (dragging) return@LaunchedEffect
        delay(2500)
        visible = false
        wanted = -1
    }

    val shown = if (wanted >= 0) wanted else ui.level
    val fill by animateFloatAsState(
        targetValue = if (ui.muted) 0f else shown / 100f,
        animationSpec = tween(120), label = "tvVol"
    )

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(150)) + slideInHorizontally(tween(180)) { it / 2 },
            exit = fadeOut(tween(250)) + slideOutHorizontally(tween(250)) { it / 2 }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(end = 16.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xE6101418))
                    .padding(horizontal = 10.dp, vertical = 14.dp)
            ) {
                Text(
                    text = if (ui.muted) "Mute" else "$shown",
                    color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))

                // The pill (drag / tap target)
                val trackH = 200.dp
                Box(
                    contentAlignment = Alignment.BottomCenter,
                    modifier = Modifier
                        .width(44.dp)
                        .height(trackH)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0x33FFFFFF))
                        .pointerInput(Unit) {
                            fun levelAt(y: Float) = ((1f - (y / size.height)).coerceIn(0f, 1f) * 100f).toInt()
                            detectVerticalDragGestures(
                                onDragStart = { o -> dragging = true; touchAt = System.currentTimeMillis(); wanted = levelAt(o.y); TvRemoteVolume.target(wanted) },
                                onDragEnd = { dragging = false; touchAt = System.currentTimeMillis() },
                                onDragCancel = { dragging = false; touchAt = System.currentTimeMillis() },
                                onVerticalDrag = { change, _ -> wanted = levelAt(change.position.y); TvRemoteVolume.target(wanted) }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { o ->
                                touchAt = System.currentTimeMillis()
                                wanted = ((1f - (o.y / size.height)).coerceIn(0f, 1f) * 100f).toInt()
                                TvRemoteVolume.target(wanted)
                            }
                        }
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fill.coerceIn(0f, 1f))
                            .background(Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFDCE6F5))))
                    )
                    if (ui.muted) Icon(Icons.Rounded.VolumeOff, contentDescription = "Muted", tint = Color.White,
                        modifier = Modifier.align(Alignment.Center).size(22.dp))
                }

                Spacer(Modifier.height(10.dp))
                Icon(Icons.Rounded.Tv, contentDescription = null, tint = Color(0xFFFF5722), modifier = Modifier.size(18.dp))
                Text(
                    text = ui.tvName, color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp,
                    textAlign = TextAlign.Center, maxLines = 2,
                    modifier = Modifier.widthIn(max = 64.dp)
                )
            }
        }
    }
}
