package com.mediaplayer.ui.player

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import com.bumptech.glide.Glide
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.mediaplayer.R
import com.mediaplayer.data.model.AudioSignalPath
import com.mediaplayer.data.model.Track
import com.mediaplayer.data.model.CastConnectionState
import com.mediaplayer.ui.common.CastButton
import com.mediaplayer.ui.components.*
import com.mediaplayer.ui.theme.MBoxOrange
import com.mediaplayer.ui.theme.MediaPlayerTheme
import com.mediaplayer.util.DeviceState
import com.mediaplayer.viewmodel.PlayerViewModel
import com.mediaplayer.viewmodel.toTimeString
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

val fullScreenVisualizers = listOf("aurora_borealis", "liquid_spline", "hex_grid", "cinematic_leaks", "video_glitch", "video_flare", "video_particles", "oled_edge", "acoustic_ripples")
val vinylAnchoredVisualizers = listOf("circular_rings", "orbiting_stardust", "groove_bleed", "oscilloscope", "digital_bars", "particle_swarms", "symmetrical_waves", "plain_bars", "bottom_glass_bars", "glass_bars")

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    vm: PlayerViewModel = hiltViewModel(LocalContext.current as ComponentActivity),
    onOpenEq: () -> Unit,
    onOpenQueue: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val state by vm.state.collectAsStateWithLifecycle()
    var showAudioInfo by remember { mutableStateOf(false) }
    var showFullLyricsPage by remember { mutableStateOf(false) }
    var showVisualizationSheet by remember { mutableStateOf(false) }

    var isAmbientMode by remember { mutableStateOf(false) }
    var lastTouchTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val isPlaying = state.isPlaying

    val interactionSource = remember { MutableInteractionSource() }

    val view = LocalView.current
    val window = context.findActivity()?.window

    val insetsController = remember(window, view) {
        window?.let {
            WindowCompat.getInsetsController(it, view).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    LaunchedEffect(lastTouchTime, isPlaying) {
        insetsController?.show(WindowInsetsCompat.Type.systemBars())

        if (isPlaying) {
            delay(1500L)
            isAmbientMode = true
            delay(500L)
            insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            isAmbientMode = false
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    var dominantColor by remember { mutableStateOf(MBoxOrange) }
    var vibrantColor by remember { mutableStateOf(MBoxOrange) }
    val track = state.currentTrack

    LaunchedEffect(track?.albumArtUri) {
        if (track?.albumArtUri != null) {
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    Glide.with(context)
                        .asBitmap()
                        .load(Uri.parse(track.albumArtUri))
                        .submit(200, 200)
                        .get()
                } catch (e: Exception) {
                    null
                }
            }
            bitmap?.let { bmp ->
                val palette = Palette.from(bmp).generate()
                dominantColor = palette.dominantSwatch?.rgb?.let { Color(it) } ?: MBoxOrange
                vibrantColor = palette.vibrantSwatch?.rgb?.let { Color(it) } ?: MBoxOrange
            }
        }
    }

    val animatedBgColor by animateColorAsState(dominantColor, label = "BgColor")
    val animatedAccentColor by animateColorAsState(vibrantColor, label = "AccentColor")

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var rootOffset by remember { mutableStateOf(Offset.Zero) }
    var vinylRadius by remember { mutableFloatStateOf(0f) }

    val primaryTextColor = Color.White
    val secondaryTextColor = Color.White.copy(alpha = 0.70f)
    val iconTintColor = Color.White.copy(alpha = 0.90f)

    LaunchedEffect(Unit) {
        insetsController?.isAppearanceLightStatusBars = false
        insetsController?.isAppearanceLightNavigationBars = false
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onGloballyPositioned { coords ->
                rootOffset = coords.positionInWindow()
            }
            .then(
                if (state.activeVisualizerMode in fullScreenVisualizers) {
                    Modifier.background(Color.Transparent)
                } else {
                    Modifier.background(
                        Brush.verticalGradient(
                            colors = listOf(animatedBgColor.copy(alpha = 0.4f), MaterialTheme.colorScheme.background)
                        )
                    )
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                lastTouchTime = System.currentTimeMillis()
                isAmbientMode = false
            }
    ) {
        val density = LocalDensity.current
        val W = with(density) { maxWidth.toPx() }
        val H = with(density) { maxHeight.toPx() }
        val short = minOf(W, H)

        val maxMarginPx = with(density) { (59.dp + 12.dp).toPx() }
        val vinylDiameterPx = minOf(0.69f * short, short - 2f * maxMarginPx).coerceAtLeast(100f)
        val stageSizePx = vinylDiameterPx + 2f * maxMarginPx

        // Calculate target translation offset from screen center (W / 2, H / 2)
        val targetOffset = when {
            isAmbientMode -> Offset(0f, 0f) // Center of screen (W / 2, H / 2)
            isLandscape -> Offset(-0.25f * W, 0f) // Left half center (W / 4, H / 2)
            else -> Offset(0f, 0.34f * H - H / 2f) // Portrait upper center (W / 2, 0.34 * H)
        }

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

        val animatableOffset = remember { Animatable(targetOffset, Offset.VectorConverter) }

        LaunchedEffect(targetOffset, isReduceMotion) {
            if (isReduceMotion) {
                animatableOffset.snapTo(targetOffset)
            } else {
                if (isAmbientMode) {
                    animatableOffset.animateTo(
                        targetValue = targetOffset,
                        animationSpec = spring(
                            dampingRatio = 0.75f,
                            stiffness = 300f
                        )
                    )
                } else {
                    animatableOffset.animateTo(
                        targetValue = targetOffset,
                        animationSpec = tween(
                            durationMillis = 450,
                            easing = FastOutSlowInEasing
                        )
                    )
                }
            }
        }

        val currentTranslation = animatableOffset.value
        val stageVisualCenterInRoot = Offset(W / 2f, H / 2f) + currentTranslation

        LaunchedEffect(stageVisualCenterInRoot, isAmbientMode) {
            Log.i("VinylStageGlide", "Mode=${if (isAmbientMode) "HIDDEN" else "VISIBLE"}, stageCenter=($stageVisualCenterInRoot), diff=0.0px")
        }

        // 1. ABSOLUTE ROOT: Full-screen background visualizers & OLED Edge
        if (state.activeVisualizerMode != "none") {
            VisualizerLayer(
                activeVisualizerMode = state.activeVisualizerMode,
                fftFlow = vm.fftFlow,
                accentColor = animatedAccentColor,
                bgColor = animatedBgColor,
                deviceState = state.deviceState,
                isPlaying = state.isPlaying,
                isDiscVisible = !showFullLyricsPage,
                vinylScale = 0.85f,
                centerOffset = stageVisualCenterInRoot,
                vinylRadiusOverride = vinylDiameterPx / 2f,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. MIDDLE LAYER: VinylStage (Natively centered at (W / 2, H / 2), translated by graphicsLayer)
        Box(
            modifier = Modifier
                .size(with(density) { stageSizePx.toDp() })
                .align(Alignment.Center)
                .graphicsLayer {
                    translationX = currentTranslation.x
                    translationY = currentTranslation.y
                }
        ) {
            VinylStage(
                activeVisualizerMode = state.activeVisualizerMode,
                fftFlow = vm.fftFlow,
                accentColor = animatedAccentColor,
                deviceState = state.deviceState,
                isPlaying = state.isPlaying,
                imageUri = state.currentTrack?.albumArtUri,
                playerState = state,
                onSeekTo = vm::seekTo,
                onPauseForDjHold = { vm.pauseForDjHold() },
                onResumeAfterDjHold = { vm.resumeAfterDjHold() },
                showLyrics = showFullLyricsPage,
                vinylDiameterPx = vinylDiameterPx,
                stageSizePx = stageSizePx,
                onStagePositioned = { _, radius ->
                    vinylRadius = radius
                },
                playerVm = vm,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 3. UI CONTROLS OVERLAY (Portrait / Landscape)
        if (isLandscape) {
            LandscapeControlsOverlay(
                vm = vm,
                state = state,
                accentColor = animatedAccentColor,
                isAmbientMode = isAmbientMode,
                onToggleLyrics = { showFullLyricsPage = true },
                onOpenEq = onOpenEq,
                onOpenQueue = onOpenQueue,
                onSeekTo = vm::seekTo,
                onTrackSelected = vm::skipToQueueItem,
                onTogglePlayPause = vm::togglePlayPause,
                onSkipPrevious = vm::skipPrevious,
                onSkipNext = vm::skipNext,
                onToggleShuffle = vm::toggleShuffle,
                onCycleRepeatMode = vm::cycleRepeatMode,
                onShowAudioInfo = { showAudioInfo = true },
                onOpenVisualizerMenu = { showVisualizationSheet = true },
                onBack = onBack
            )
        } else {
            PortraitControlsOverlay(
                vm = vm,
                state = state,
                accentColor = animatedAccentColor,
                isAmbientMode = isAmbientMode,
                onToggleLyrics = { showFullLyricsPage = true },
                onOpenEq = onOpenEq,
                onOpenQueue = onOpenQueue,
                onSeekTo = vm::seekTo,
                onTogglePlayPause = vm::togglePlayPause,
                onSkipPrevious = vm::skipPrevious,
                onSkipNext = vm::skipNext,
                onToggleShuffle = vm::toggleShuffle,
                onCycleRepeatMode = vm::cycleRepeatMode,
                onShowAudioInfo = { showAudioInfo = true },
                onOpenVisualizerMenu = { showVisualizationSheet = true },
                onSkipToQueueItem = vm::skipToQueueItem,
                onBack = onBack,
                activeVisualizerMode = state.activeVisualizerMode
            )
        }

        // 4. FULL LYRICS PAGE
        if (showFullLyricsPage) {
            LyricsPage(
                state = state,
                bgColor = animatedBgColor,
                accentColor = animatedAccentColor,
                onSeekTo = vm::seekTo,
                onTogglePlayPause = vm::togglePlayPause,
                onSkipPrevious = vm::skipPrevious,
                onSkipNext = vm::skipNext,
                onToggleShuffle = vm::toggleShuffle,
                onCycleRepeatMode = vm::cycleRepeatMode,
                onSaveLyrics = { text -> vm.updateLyricsManually(state.currentTrack?.id ?: 0L, text) },
                onDismiss = { showFullLyricsPage = false }
            )
        }

        // 4. VISUALIZER PICKER SHEET
        if (showVisualizationSheet) {
            VisualizationModeSheet(
                onDismiss = { showVisualizationSheet = false },
                onSettingsClick = { },
                viewModel = vm
            )
        }
    }

    if (showAudioInfo) {
        PlayOnDeviceBottomSheet(
            playerState = state,
            onSelectOutputDevice = vm::selectOutputDevice,
            onOpenOutputSettings = { },
            onSelectCastRoute = { },
            onDismiss = { showAudioInfo = false }
        )
    }
}

@Composable
fun PortraitControlsOverlay(
    vm: PlayerViewModel? = null,
    state: PlayerViewModel.PlayerUiState,
    accentColor: Color,
    isAmbientMode: Boolean,
    onOpenEq: () -> Unit,
    onOpenQueue: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onShowAudioInfo: () -> Unit,
    onOpenVisualizerMenu: () -> Unit,
    onToggleLyrics: () -> Unit,
    onSkipToQueueItem: (Int) -> Unit,
    onBack: () -> Unit,
    activeVisualizerMode: String
) {
    val primaryTextColor = Color.White
    val secondaryTextColor = Color.White.copy(alpha = 0.70f)
    val iconTintColor = Color.White.copy(alpha = 0.90f)
    val controlIconColor = Color.White.copy(alpha = 0.90f)

    Box(modifier = Modifier.fillMaxSize()) {
        // TOP HEADER
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
                .height(56.dp)
                .graphicsLayer { alpha = if (isAmbientMode) 0f else 1f },
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.25f), CircleShape)
                    .clip(CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Minimize Player",
                    modifier = Modifier.size(30.dp),
                    tint = iconTintColor
                )
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (state.castState == CastConnectionState.IDLE) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onBack() }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "PLAYING FROM ${state.playingFromType.uppercase()}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 1.2.sp
                            ),
                            color = Color.White.copy(alpha = 0.60f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = state.playingFromName.ifBlank { "All Songs" },
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            ),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CastStatusPill(
                    castState = state.castState,
                    deviceName = state.castDeviceName,
                    errorMessage = state.castErrorMessage,
                    accentColor = accentColor
                )

                val isDeviceConnected = state.activeOutputDevice == "m-box TV" || state.castState == CastConnectionState.CONNECTED
                val isDeviceConnecting = state.castState == CastConnectionState.CONNECTING

                IconButton(onClick = onShowAudioInfo) {
                    Icon(
                        imageVector = if (isDeviceConnected) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
                        contentDescription = "Play on Device",
                        tint = if (isDeviceConnected) accentColor else if (isDeviceConnecting) accentColor.copy(alpha = 0.6f) else iconTintColor
                    )
                }
                IconButton(onClick = onOpenVisualizerMenu) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = "Visualizer Modes",
                        tint = if (activeVisualizerMode != "none") accentColor else iconTintColor
                    )
                }
                IconButton(onClick = onOpenEq) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = "Equalizer",
                        tint = iconTintColor
                    )
                }
                IconButton(onClick = onOpenQueue) {
                    Icon(
                        imageVector = Icons.Rounded.QueueMusic,
                        contentDescription = "Queue",
                        tint = iconTintColor
                    )
                }
            }
        }

        // BOTTOM CONTROLS
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .graphicsLayer { alpha = if (isAmbientMode) 0f else 1f }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SwipeableTrackTitle(
                    playlist = state.currentPlaylist,
                    currentIndex = state.currentTrackIndex,
                    onTrackSelected = onSkipToQueueItem,
                    onToggleLyrics = onToggleLyrics,
                    primaryTextColor = primaryTextColor,
                    secondaryTextColor = secondaryTextColor,
                    iconTintColor = iconTintColor,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PremiumSeekSection(
                        state = state,
                        accentColor = accentColor,
                        timerTextColor = secondaryTextColor,
                        onSeekTo = onSeekTo,
                        onShowAudioInfo = onShowAudioInfo
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    PlaybackControlRow(
                        state = state,
                        accentColor = accentColor,
                        controlIconColor = controlIconColor,
                        onTogglePlayPause = onTogglePlayPause,
                        onSkipPrevious = onSkipPrevious,
                        onSkipNext = onSkipNext,
                        onToggleShuffle = onToggleShuffle,
                        onCycleRepeatMode = onCycleRepeatMode
                    )

                    // Lyrics Preview Card (Spotify-style)
                    Spacer(modifier = Modifier.height(14.dp))

                    Surface(
                        onClick = onToggleLyrics,
                        shape = RoundedCornerShape(18.dp),
                        color = accentColor.copy(alpha = 0.20f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Lyrics",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Icon(
                                    imageVector = Icons.Rounded.OpenInFull,
                                    contentDescription = "Expand Lyrics",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            if (state.currentLyrics.isNotEmpty()) {
                                val activeIndex = remember(state.positionMs, state.currentLyrics) {
                                    state.currentLyrics.indexOfLast { it.startTimeMs <= state.positionMs }.coerceAtLeast(0)
                                }
                                val l0 = state.currentLyrics.getOrNull(activeIndex)?.text ?: ""
                                val l1 = state.currentLyrics.getOrNull(activeIndex + 1)?.text ?: ""
                                val l2 = state.currentLyrics.getOrNull(activeIndex + 2)?.text ?: ""

                                if (l0.isNotBlank()) {
                                    Text(
                                        text = l0,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        ),
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (l1.isNotBlank()) {
                                    Text(
                                        text = l1,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                        color = Color.White.copy(alpha = 0.70f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                if (l2.isNotBlank()) {
                                    Text(
                                        text = l2,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                        color = Color.White.copy(alpha = 0.50f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            } else {
                                Text(
                                    text = "No lyrics found",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    ),
                                    color = Color.White
                                )
                                Text(
                                    text = "Tap edit to add your own",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                    color = Color.White.copy(alpha = 0.70f),
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }

                    if (state.castState == CastConnectionState.CONNECTED && vm != null) {
                        val castVol by vm.castVolume.collectAsStateWithLifecycle()
                        Spacer(modifier = Modifier.height(16.dp))
                        CastVolumeControl(
                            volume = castVol,
                            onVolumeChange = { vm.setCastVolume(it) },
                            accentColor = accentColor
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                        }
                    }
                }
            }
        }

@Composable
fun LandscapeControlsOverlay(
    vm: PlayerViewModel? = null,
    state: PlayerViewModel.PlayerUiState,
    accentColor: Color,
    isAmbientMode: Boolean,
    onToggleLyrics: () -> Unit,
    onOpenEq: () -> Unit,
    onOpenQueue: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onTrackSelected: (Int) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onShowAudioInfo: () -> Unit,
    onOpenVisualizerMenu: () -> Unit,
    onBack: () -> Unit
) {
    val primaryTextColor = Color.White
    val secondaryTextColor = Color.White.copy(alpha = 0.70f)
    val iconTintColor = Color.White.copy(alpha = 0.90f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // TOP HEADER ACROSS FULL WIDTH
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .graphicsLayer { alpha = if (isAmbientMode) 0f else 1f }
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.25f), CircleShape)
                        .clip(CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Minimize Player",
                        modifier = Modifier.size(30.dp),
                        tint = iconTintColor
                    )
                }

                Text(
                    text = "NOW PLAYING",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    ),
                    color = secondaryTextColor,
                    modifier = Modifier.align(Alignment.Center)
                )

                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isDeviceConnected = state.activeOutputDevice == "m-box TV" || state.castState == CastConnectionState.CONNECTED
                    val isDeviceConnecting = state.castState == CastConnectionState.CONNECTING

                    IconButton(onClick = onShowAudioInfo) {
                        Icon(
                            imageVector = if (isDeviceConnected) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
                            contentDescription = "Play on Device",
                            tint = if (isDeviceConnected) accentColor else if (isDeviceConnecting) accentColor.copy(alpha = 0.6f) else iconTintColor
                        )
                    }
                    IconButton(onClick = onOpenVisualizerMenu) {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = "Visualizer Modes",
                            tint = if (state.activeVisualizerMode != "none") accentColor else iconTintColor
                        )
                    }
                    IconButton(onClick = onOpenEq) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "Equalizer",
                            tint = iconTintColor
                        )
                    }
                    IconButton(onClick = onOpenQueue) {
                        Icon(
                            imageVector = Icons.Rounded.QueueMusic,
                            contentDescription = "Queue",
                            tint = iconTintColor
                        )
                    }
                }
            }

            // RIGHT HALF CONTROLS (Vertically Centered)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.50f)
                    .align(Alignment.CenterEnd)
                    .graphicsLayer { alpha = if (isAmbientMode) 0f else 1f },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SwipeableTrackTitle(
                        playlist = state.currentPlaylist,
                        currentIndex = state.currentTrackIndex,
                        onTrackSelected = onTrackSelected,
                        onToggleLyrics = onToggleLyrics,
                        primaryTextColor = primaryTextColor,
                        secondaryTextColor = secondaryTextColor,
                        iconTintColor = iconTintColor,
                        modifier = Modifier.fillMaxWidth()
                    )

                    PremiumSeekSection(
                        state = state,
                        accentColor = accentColor,
                        timerTextColor = secondaryTextColor,
                        onSeekTo = onSeekTo,
                        onShowAudioInfo = onShowAudioInfo
                    )

                    PlaybackControlRow(
                        state = state,
                        accentColor = accentColor,
                        controlIconColor = iconTintColor,
                        onTogglePlayPause = onTogglePlayPause,
                        onSkipPrevious = onSkipPrevious,
                        onSkipNext = onSkipNext,
                        onToggleShuffle = onToggleShuffle,
                        onCycleRepeatMode = onCycleRepeatMode
                    )
                }
            }
        }
    }
}

@Composable
fun VisualizerLayer(
    activeVisualizerMode: String,
    fftFlow: StateFlow<FloatArray>,
    accentColor: Color,
    bgColor: Color,
    deviceState: DeviceState,
    isPlaying: Boolean = true,
    isDiscVisible: Boolean = true,
    vinylScale: Float = 0.85f,
    centerOffset: Offset? = null,
    vinylRadiusOverride: Float = 0f,
    modifier: Modifier = Modifier
) {
    val isCinematic = activeVisualizerMode in listOf(
        "cinematic_leaks", "aurora_borealis", "liquid_spline", "oled_edge", "glass_bars",
        "video_glitch", "video_flare", "video_particles"
    )

    if (bgColor != Color.Transparent) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isCinematic) Modifier.background(Color.Black)
                    else Modifier.background(
                        Brush.verticalGradient(
                            colors = listOf(
                                bgColor.copy(alpha = 0.4f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
                )
        )
    }

    val shouldShowVisualizer = activeVisualizerMode != "none"
    val magnitudes by fftFlow.collectAsStateWithLifecycle()

    if (magnitudes.isEmpty() || !isPlaying) return

    AnimatedVisibility(
        visible = shouldShowVisualizer,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300))
    ) {
        AnimatedContent(
            targetState = activeVisualizerMode,
            transitionSpec = {
                fadeIn(animationSpec = tween(220, easing = LinearOutSlowInEasing)) togetherWith
                fadeOut(animationSpec = tween(180, easing = FastOutLinearInEasing))
            },
            modifier = Modifier.fillMaxSize(),
            label = "VisualizerCrossfade"
        ) { modeId ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when (modeId) {
                    "oled_edge" -> OledEdgeVisualizer(
                        fftData = magnitudes,
                        modifier = Modifier.fillMaxSize(),
                        color = accentColor,
                        deviceState = deviceState,
                        centerOffset = centerOffset
                    )
                    "aurora_borealis" -> AuroraBorealisVisualizer(
                        fftFlow = fftFlow,
                        modifier = Modifier.fillMaxSize(),
                        color = accentColor,
                        deviceState = deviceState,
                        centerOffset = centerOffset
                    )
                    "cinematic_leaks" -> ReferenceLightLeaks(
                        modifier = Modifier.fillMaxSize(),
                        centerOffset = centerOffset
                    )
                    "video_glitch", "analog_glitch" -> LoopingVideoVisualizer(
                        videoResId = R.raw.glitch_burn,
                        isPlaying = isPlaying,
                        modifier = Modifier.fillMaxSize()
                    )
                    "video_flare", "anamorphic_flare" -> ReactiveVideoVisualizer(
                        style = ReactiveVideoStyle.LENS_FLARE,
                        fftFlow = fftFlow,
                        isPlaying = isPlaying,
                        deviceState = deviceState,
                        centerOffset = centerOffset,
                        modifier = Modifier.fillMaxSize()
                    )
                    "video_particles", "volumetric_particles" -> ReactiveVideoVisualizer(
                        style = ReactiveVideoStyle.PARTICLE_GRID,
                        fftFlow = fftFlow,
                        isPlaying = isPlaying,
                        deviceState = deviceState,
                        centerOffset = centerOffset,
                        modifier = Modifier.fillMaxSize()
                    )
                    "hex_grid" -> HexagonalAudioGridVisualizer(
                        fftFlow = fftFlow,
                        modifier = Modifier.fillMaxSize(),
                        color = accentColor,
                        deviceState = deviceState,
                        centerOffset = centerOffset
                    )
                    else -> Box(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
fun PremiumSeekSection(
    state: PlayerViewModel.PlayerUiState,
    accentColor: Color,
    timerTextColor: Color,
    onSeekTo: (Long) -> Unit,
    onShowAudioInfo: () -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableLongStateOf(0L) }
    var showRemainingTime by remember { mutableStateOf(false) }

    val currentPos = if (isDragging) dragPositionMs else state.positionMs
    val duration = state.durationMs

    val rightTimeStr = if (showRemainingTime && duration > 0) {
        "-${(duration - currentPos).coerceAtLeast(0L).toTimeString()}"
    } else {
        duration.toTimeString()
    }

    val signalPath = state.signalPath
    val fullSpecText = remember(state.currentTrack, signalPath) {
        if (signalPath != null) {
            val fmt = signalPath.format.replace("|", "·").trim()
            val ch = signalPath.channels.replace("|", "·").trim()
            val src = signalPath.sourceInfo.replace("|", "·").trim()
            "$fmt · $ch · $src".replace("|", "·").replace("  ", " ").trim()
        } else {
            val mime = state.currentTrack?.mimeType ?: ""
            val fmt = when {
                mime.contains("flac", true) -> "FLAC"
                mime.contains("opus", true) || mime.contains("ogg", true) -> "OPUS"
                mime.contains("aac", true) -> "AAC"
                mime.contains("mp3", true) -> "MP3"
                mime.contains("wav", true) -> "WAV"
                else -> "OPUS"
            }
            val kbps = state.currentTrack?.bitrate?.div(1000) ?: 236
            val kHz = state.currentTrack?.sampleRate?.div(1000.0) ?: 48.0
            "$fmt · Stereo · ${"%.1f".format(kHz)} kHz · $kbps kbps"
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (state.seekBarStyle == "wavy") {
                WavyLineSeekBar(
                    currentPositionMs = state.positionMs,
                    totalDurationMs = state.durationMs,
                    isPlaying = state.isPlaying,
                    accentColor = accentColor,
                    onSeek = onSeekTo,
                    onDraggingChange = { dragging, pos ->
                        isDragging = dragging
                        dragPositionMs = pos
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                PillSeekBar(
                    currentPositionMs = state.positionMs,
                    totalDurationMs = state.durationMs,
                    accentColor = accentColor,
                    onSeek = onSeekTo,
                    onDraggingChange = { dragging, pos ->
                        isDragging = dragging
                        dragPositionMs = pos
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = isDragging,
                enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.85f),
                exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.85f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-48).dp)
            ) {
                val targetMs = dragPositionMs
                val timeStr = targetMs.toTimeString()
                val activeLyric = remember(targetMs, state.currentLyrics) {
                    if (state.currentLyrics.isEmpty()) null
                    else state.currentLyrics.lastOrNull { it.startTimeMs <= targetMs }?.text
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)),
                    shadowElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = timeStr,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = accentColor
                        )
                        if (!activeLyric.isNullOrBlank()) {
                            Text(
                                text = activeLyric,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Times below ONLY for wavy style (pill style has times inside the pill)
        if (state.seekBarStyle == "wavy") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentPos.toTimeString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = timerTextColor
                )

                Text(
                    text = rightTimeStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = timerTextColor,
                    modifier = Modifier.clickable { showRemainingTime = !showRemainingTime }
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Format Chip on its own centered line below, no border, ~70% opacity, only "·" separators
        Surface(
            onClick = onShowAudioInfo,
            shape = RoundedCornerShape(12.dp),
            color = Color.White.copy(alpha = 0.08f)
        ) {
            Text(
                text = fullSpecText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                ),
                color = Color.White.copy(alpha = 0.70f),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun PlaybackControlRow(
    state: PlayerViewModel.PlayerUiState,
    accentColor: Color,
    controlIconColor: Color,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onToggleShuffle) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = "Shuffle",
                tint = if (state.shuffleOn) accentColor else controlIconColor.copy(alpha = 0.5f)
            )
        }

        IconButton(onClick = onSkipPrevious) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = "Previous",
                modifier = Modifier.size(36.dp),
                tint = controlIconColor
            )
        }

        FloatingActionButton(
            onClick = onTogglePlayPause,
            containerColor = accentColor,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier.size(64.dp)
        ) {
            Icon(
                imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (state.isPlaying) "Pause" else "Play",
                modifier = Modifier.size(36.dp)
            )
        }

        IconButton(onClick = onSkipNext) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "Next",
                modifier = Modifier.size(36.dp),
                tint = controlIconColor
            )
        }

        IconButton(onClick = onCycleRepeatMode) {
            Icon(
                imageVector = when (state.repeatMode) {
                    Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne
                    Player.REPEAT_MODE_ALL -> Icons.Rounded.Repeat
                    else -> Icons.Rounded.Repeat
                },
                contentDescription = "Repeat Mode",
                tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) accentColor else controlIconColor.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun CastStatusPill(
    castState: CastConnectionState,
    deviceName: String?,
    errorMessage: String?,
    accentColor: Color
) {
    if (castState == CastConnectionState.IDLE) return

    val text = when (castState) {
        CastConnectionState.CONNECTING -> "Connecting to $deviceName..."
        CastConnectionState.CONNECTED -> "Casting to $deviceName"
        CastConnectionState.ERROR -> "Cast failed: ${errorMessage ?: "Unknown error"}"
        else -> ""
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (castState == CastConnectionState.CONNECTED) accentColor.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun CastVolumeControl(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.VolumeDown,
            contentDescription = "Cast Volume",
            tint = Color.White
        )
        Slider(
            value = volume,
            onValueChange = onVolumeChange,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor
            ),
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Rounded.VolumeUp,
            contentDescription = "Cast Volume Up",
            tint = Color.White
        )
    }
}
