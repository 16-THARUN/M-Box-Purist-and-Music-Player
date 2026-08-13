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
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.mediaplayer.ui.components.*
import com.mediaplayer.ui.theme.MBoxOrange
import com.mediaplayer.util.FftState
import com.mediaplayer.viewmodel.PlayerViewModel
import com.mediaplayer.viewmodel.toTimeString
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

val fullScreenVisualizers = listOf("aurora_borealis", "liquid_spline", "hex_grid", "cinematic_leaks", "analog_glitch", "anamorphic_flare", "volumetric_particles", "video_glitch", "video_flare", "video_particles", "oled_edge", "glass_bars")
val vinylAnchoredVisualizers = listOf("circular_rings", "orbiting_stardust", "acoustic_ripples", "groove_bleed", "oscilloscope", "digital_bars", "particle_swarms", "symmetrical_waves", "plain_bars", "bottom_glass_bars")

// 🔥 THE FIX 1: Safe Activity Extractor
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    onOpenEq: () -> Unit,
    onOpenQueue: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // 🔥 THE FIX 1: Safely get the Activity to scope the ViewModel globally
    val activity = context.findActivity() as? ComponentActivity

    // Require the activity scope so UI state isn't wiped on navigation
    val vm: PlayerViewModel = if (activity != null) {
        hiltViewModel(activity)
    } else {
        hiltViewModel() // Fallback, though activity should not be null here
    }

    val state by vm.state.collectAsStateWithLifecycle()
    var showAudioInfo by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }

    // 🔥 THE NEW UI STATE
    var showVisualizationSheet by remember { mutableStateOf(false) } 

    var isAmbientMode by remember { mutableStateOf(false) }
    var lastTouchTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val isPlaying = state.isPlaying

    val interactionSource = remember { MutableInteractionSource() }

    // 1. Get the current window and insets controller
    val view = LocalView.current
    
    // 🔥 THE FIX 3: Safely extract the window
    val window = context.findActivity()?.window
    
    val insetsController = remember(window, view) {
        window?.let { 
            WindowCompat.getInsetsController(it, view).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    // 2. The 2-Second Bulletproof Timer
    LaunchedEffect(lastTouchTime, isPlaying) {
        // Show the bars instantly whenever the screen is touched
        insetsController?.show(WindowInsetsCompat.Type.systemBars())
        
        if (isPlaying) {
            // Wait exactly 1.5 seconds to hide player controls
            delay(1500L)
            isAmbientMode = true
            
            // Wait another 0.5 seconds (Total 2.0 seconds) to hide system bars
            delay(500L)
            insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            // Always show controls and system bars if music is paused
            isAmbientMode = false
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // 3. Restore system bars when leaving the Now Playing Screen
    DisposableEffect(Unit) {
        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Dynamic Theming with Palette API
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 🔥 THE FIX: Restore the dynamic background
            .then(
                if (state.activeVisualizerMode in fullScreenVisualizers) {
                    Modifier.background(Color.Transparent) // Let the fullscreen visualizer handle it
                } else {
                    Modifier.background(
                        Brush.verticalGradient(
                            colors = listOf(animatedBgColor.copy(alpha = 0.4f), MaterialTheme.colorScheme.background)
                        )
                    )
                }
            )
            // Consumes the tap securely without rippling, waking up the UI
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                lastTouchTime = System.currentTimeMillis()
                isAmbientMode = false
            }
    ) {
        // 1. ABSOLUTE ROOT: ONLY FULL-SCREEN BACKGROUNDS
        if (state.activeVisualizerMode in fullScreenVisualizers) {
            VisualizerLayer(
                activeVisualizerMode = state.activeVisualizerMode,
                fftFlow = vm.fftData,
                accentColor = animatedAccentColor,
                bgColor = animatedBgColor,
                isPlaying = state.isPlaying,
                vinylScale = 0.85f,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isLandscape) {
            LandscapeLayout(
                vm = vm,
                state = state,
                accentColor = animatedAccentColor,
                showLyrics = showLyrics,
                isAmbientMode = isAmbientMode,
                onToggleLyrics = { showLyrics = !showLyrics },
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
                onBack = onBack,
                onToggleVisualizer = vm::toggleVisualizer,
                isVisualizerEnabled = state.isVisualizerEnabled,
                activeVisualizerMode = state.activeVisualizerMode
            )
        } else {
            PortraitLayout(
                vm = vm,
                state = state,
                accentColor = animatedAccentColor,
                showLyrics = showLyrics,
                isAmbientMode = isAmbientMode,
                onToggleLyrics = { showLyrics = !showLyrics },
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
                onToggleVisualizer = vm::toggleVisualizer,
                isVisualizerEnabled = state.isVisualizerEnabled,
                activeVisualizerMode = state.activeVisualizerMode
            )
        }

        // 🔥 RENDER THE SHEET DIRECTLY OVER THE PLAYER
        if (showVisualizationSheet) {
            VisualizationModeSheet(
                onDismiss = { showVisualizationSheet = false },
                onSettingsClick = { /* Optional: route to advanced visualizer settings if needed */ },
                viewModel = vm 
            )
        }
    }

    if (showAudioInfo) {
        AudioInfoBottomSheet(
            signalPath = state.signalPath ?: AudioSignalPath(
                format = if (track?.mimeType?.contains("opus", true) == true || track?.mimeType?.contains("ogg", true) == true) "Opus" else (track?.mimeType?.substringAfterLast("/")?.uppercase() ?: "OPUS"),
                sourceInfo = if (track?.mimeType?.contains("opus", true) == true || track?.mimeType?.contains("ogg", true) == true) "16 bit 48 kHz 237 kbps" else "${track?.bitrate?.div(1000) ?: 168} kbps | ${track?.sampleRate?.div(1000.0) ?: 48.0} kHz",
                decoder = "Poweramp FFmpeg Engine",
                hardwareInfo = "Direct HD Output",
                outputApi = "OpenSL ES",
                latency = 120,
                bitDepth = "32 bit -> 16 bit",
                channels = "Stereo 2.0",
                gapless = true,
                resamplerQuality = "SoX Very High",
                dspBitDepth = "Float32",
                eqMode = "Graphic 10-Band",
                toneControl = "Flat",
                preamp = "0.0 dB",
                outputFlags = "RAW",
                deviceType = "Internal DAC"
            ),
            onDismiss = { showAudioInfo = false }
        )
    }
}

@Composable
fun PortraitLayout(
    vm: PlayerViewModel,
    state: PlayerViewModel.PlayerUiState,
    accentColor: Color,
    showLyrics: Boolean,
    isAmbientMode: Boolean,
    onToggleLyrics: () -> Unit,
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
    onSkipToQueueItem: (Int) -> Unit,
    onBack: () -> Unit,
    onToggleVisualizer: () -> Unit,
    isVisualizerEnabled: Boolean,
    activeVisualizerMode: String
) {
    // 🔥 THE FIX: Changed root back to Column for elevated vinyl position
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        // ==========================================
        // 1. TOP HEADER (Smooth slide up/down)
        // ==========================================
        AnimatedVisibility(
            visible = !isAmbientMode,
            enter = fadeIn(tween(600)) + expandVertically(tween(600), expandFrom = Alignment.Top),
            exit = fadeOut(tween(600)) + shrinkVertically(tween(600), shrinkTowards = Alignment.Top)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp) // Standard Android toolbar height
            ) {
                // THE BACK BUTTON (Docked Left, Circular Background)
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
                        tint = Color.White
                    )
                }

                // THE CENTER TEXT (Absolute Center)
                Text(
                    text = "NOW PLAYING",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold, 
                        letterSpacing = 2.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.Center)
                )

                // THE RIGHT ICONS (Visualizer, EQ, Queue)
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onOpenVisualizerMenu) {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = "Visualizer Modes",
                            tint = if (activeVisualizerMode != "none") accentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    IconButton(onClick = onOpenEq) { 
                        Icon(
                            imageVector = Icons.Rounded.Tune, 
                            contentDescription = "Equalizer",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        ) 
                    }
                    IconButton(onClick = onOpenQueue) { 
                        Icon(
                            imageVector = Icons.Rounded.QueueMusic, 
                            contentDescription = "Queue",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        ) 
                    }
                }
            }
        }

        // ==========================================
        // 2. ZONE 1: THE TURNTABLE SPACE (Glides to Center)
        // ==========================================
        Box(
            modifier = Modifier
                .weight(1f) // 🔥 Expands smoothly as top/bottom shrink, pushing Vinyl to center
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // 🔥 THE ALIGNMENT FIX: Draw anchored visualizers exactly behind the vinyl inside this Box!
            if (activeVisualizerMode in vinylAnchoredVisualizers) {
                VisualizerLayer(
                    activeVisualizerMode = activeVisualizerMode,
                    fftFlow = vm.fftData,
                    accentColor = accentColor,
                    bgColor = Color.Transparent, // No background here
                    isPlaying = state.isPlaying,
                    vinylScale = 0.85f,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // The Vinyl Record
            VinylAlbumCover(
                imageUri = state.currentTrack?.albumArtUri,
                isPlaying = state.isPlaying,
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                onSeekTo = onSeekTo,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .aspectRatio(1f) // 🔥 Forces the size to remain strictly identical
            )
        }

        // ==========================================
        // 3. ZONE 2: BOTTOM CONTROLS (Smooth shrink down)
        // ==========================================
        AnimatedVisibility(
            visible = !isAmbientMode,
            enter = fadeIn(tween(600)) + expandVertically(tween(600), expandFrom = Alignment.Bottom),
            exit = fadeOut(tween(600)) + shrinkVertically(tween(600), shrinkTowards = Alignment.Bottom)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(32.dp))

                // 3. ZONE 2: THE TITLE SWIPE WITH LYRICS BUTTON
                SwipeableTrackTitle(
                    playlist = state.currentPlaylist,
                    currentIndex = state.currentTrackIndex,
                    onTrackSelected = onSkipToQueueItem,
                    onToggleLyrics = onToggleLyrics,
                    primaryTextColor = if (activeVisualizerMode == "cinematic_leaks") Color.White else Color.Black,
                    secondaryTextColor = if (activeVisualizerMode == "cinematic_leaks") Color.White.copy(alpha = 0.75f) else Color.Black.copy(alpha = 0.6f),
                    iconTintColor = if (activeVisualizerMode == "cinematic_leaks") Color.White else Color.Black,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(32.dp))

                // 5. THE CONTROL DECK
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PremiumSeekSection(
                        state = state,
                        accentColor = accentColor,
                        timerTextColor = if (activeVisualizerMode == "cinematic_leaks") Color.White.copy(alpha = 0.75f) else Color.Black.copy(alpha = 0.6f),
                        onSeekTo = onSeekTo,
                        onShowAudioInfo = onShowAudioInfo
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    PlaybackControlRow(
                        state = state,
                        accentColor = accentColor,
                        controlIconColor = if (activeVisualizerMode == "cinematic_leaks") Color.White else Color.Black,
                        onTogglePlayPause = onTogglePlayPause,
                        onSkipPrevious = onSkipPrevious,
                        onSkipNext = onSkipNext,
                        onToggleShuffle = onToggleShuffle,
                        onCycleRepeatMode = onCycleRepeatMode
                    )

                    Spacer(modifier = Modifier.height(36.dp))
                }
            }
        }
    }
}

@Composable
fun LandscapeLayout(
    vm: PlayerViewModel,
    state: PlayerViewModel.PlayerUiState,
    accentColor: Color,
    showLyrics: Boolean,
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
    onBack: () -> Unit,
    onToggleVisualizer: () -> Unit,
    isVisualizerEnabled: Boolean,
    activeVisualizerMode: String
) {
    val isCinematic = activeVisualizerMode in listOf(
        "cinematic_leaks", "analog_glitch", "anamorphic_flare", "volumetric_particles",
        "video_glitch", "video_flare", "video_particles"
    )
    val primaryTextColor = if (isCinematic) Color.White else Color.Black
    val secondaryTextColor = if (isCinematic) Color.White.copy(alpha = 0.75f) else Color.Black.copy(alpha = 0.6f)
    val iconTintColor = if (isCinematic) Color.White else Color.Black

    Row(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedContent(
            targetState = showLyrics,
            transitionSpec = {
                slideInVertically(initialOffsetY = { it }) + fadeIn() togetherWith
                slideOutVertically(targetOffsetY = { it }) + fadeOut()
            },
            label = "LandscapeLyricsTransition",
            modifier = Modifier.fillMaxSize()
        ) { targetShowLyrics ->
            if (targetShowLyrics) {
                LyricsView(
                    state = state,
                    accentColor = accentColor,
                    onClose = onToggleLyrics,
                    onTogglePlayPause = onTogglePlayPause,
                    onSaveLyrics = { vm.updateLyricsManually(state.currentTrack?.id ?: 0L, it) }
                )
            } else {
                val rightSideWeight by animateFloatAsState(
                    targetValue = if (isAmbientMode) 0f else 1f,
                    animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                    label = "RightSideWeight"
                )

                Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    // ==========================================
                    // LEFT HALF: Vinyl Space 
                    // ==========================================
                    Box(
                        modifier = Modifier
                            .weight(1f) 
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        // 🔥 THE FIX: Render center-dependent visualizers directly behind the vinyl
                        if (activeVisualizerMode in vinylAnchoredVisualizers) {
                            VisualizerLayer(
                                activeVisualizerMode = activeVisualizerMode,
                                fftFlow = vm.fftData,
                                accentColor = accentColor,
                                bgColor = Color.Transparent,
                                isPlaying = state.isPlaying,
                                vinylScale = 0.85f,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // The Vinyl Record
                        VinylAlbumCover(
                            imageUri = state.currentTrack?.albumArtUri,
                            isPlaying = state.isPlaying,
                            positionMs = state.positionMs,
                            durationMs = state.durationMs,
                            onSeekTo = onSeekTo,
                            modifier = Modifier
                                .fillMaxHeight(0.85f)
                                .aspectRatio(1f)
                        )
                    }

                    // Only add the spacer if the right side is somewhat visible
                    if (rightSideWeight > 0.05f) {
                        Spacer(modifier = Modifier.width((32 * rightSideWeight).dp))
                    }

                    // ==========================================
                    // RIGHT HALF: Controls (Shrinks and fades smoothly)
                    // ==========================================
                    // 🔥 THE FIX: We use the animated weight. As it goes to 0, 
                    // the left side naturally pushes to the center!
                    if (rightSideWeight > 0.01f) {
                        Box(
                            modifier = Modifier
                                .weight(rightSideWeight)
                                .fillMaxHeight()
                                .alpha(rightSideWeight) // Fades out as it shrinks
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceEvenly,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // 1. TOP HEADER
                                Box(modifier = Modifier.fillMaxWidth().height(56.dp)) {
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
                                            tint = Color.White
                                        )
                                    }

                                    Text(
                                        text = "NOW PLAYING",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold, 
                                            letterSpacing = 2.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                    Row(
                                        modifier = Modifier.align(Alignment.CenterEnd),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(onClick = onOpenVisualizerMenu) {
                                            Icon(
                                                imageVector = Icons.Filled.GraphicEq,
                                                contentDescription = "Visualizer Modes",
                                                tint = if (activeVisualizerMode != "none") accentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                            )
                                        }
                                        IconButton(onClick = onOpenEq) { 
                                            Icon(
                                                imageVector = Icons.Rounded.Tune, 
                                                contentDescription = "Equalizer",
                                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                            ) 
                                        }
                                        IconButton(onClick = onOpenQueue) { 
                                            Icon(
                                                imageVector = Icons.Rounded.QueueMusic, 
                                                contentDescription = "Queue",
                                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                            ) 
                                        }
                                    }
                                }

                                // 2. TRACK INFO
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

                                // 3. PROGRESS BAR & TIMERS
                                PremiumSeekSection(
                                    state = state,
                                    accentColor = accentColor,
                                    timerTextColor = secondaryTextColor,
                                    onSeekTo = onSeekTo,
                                    onShowAudioInfo = onShowAudioInfo
                                )
                                
                                // 4. PLAYBACK CONTROLS
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
        }
    }
}

@Composable
fun LyricsView(
    state: PlayerViewModel.PlayerUiState,
    accentColor: Color,
    onClose: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSaveLyrics: (String) -> Unit
) {
    val context = LocalContext.current
    val lyrics = state.currentLyrics

    // 🔥 THE FIX: Detect if this is a plain text file (e.g., all timestamps are 0L or list is short)
    val isActuallySynced = remember(lyrics) {
        lyrics.count { it.startTimeMs > 0L } > 2
    }

    // Override the user's toggle if the file simply doesn't have sync data
    var isSyncedMode by remember(isActuallySynced) { mutableStateOf(isActuallySynced) }
    
    var isEditing by remember { mutableStateOf(false) }
    var editableLrcText by remember { mutableStateOf("") }
    
    // 🔥 THE SYNC ENGINE: Finds the last line whose timestamp has passed
    val currentActiveLineIndex = remember(state.positionMs, lyrics) {
        if (isActuallySynced) {
            lyrics.indexOfLast { it.startTimeMs <= state.positionMs }.coerceAtLeast(0)
        } else -1
    }

    val listState = rememberLazyListState()

    LaunchedEffect(currentActiveLineIndex, isSyncedMode) {
        if (isSyncedMode && currentActiveLineIndex >= 0 && lyrics.isNotEmpty()) {
            // 🔥 THE FIX: Centers the active line by scrolling to (index - offset)
            val centerOffsetIndex = (currentActiveLineIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(index = centerOffsetIndex)
        }
    }

    if (isEditing) {
        BackHandler { isEditing = false }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .systemBarsPadding()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { isEditing = false }) {
                    Text("Cancel")
                }
                Text(
                    "Edit Lyrics",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                TextButton(onClick = { 
                    onSaveLyrics(editableLrcText)
                    isEditing = false 
                }) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            OutlinedTextField(
                value = editableLrcText,
                onValueChange = { editableLrcText = it },
                modifier = Modifier.fillMaxSize(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                placeholder = { Text("[00:00.00] Paste LRC here...") }
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(top = 16.dp)
                .navigationBarsPadding() // 🔥 THE FIX: Protects the bottom controls from bezel clipping
        ) {
            // Top Header
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp), 
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Close Lyrics") }
                Text("Lyrics", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                IconButton(onClick = { 
                    editableLrcText = state.rawLyricsText
                    isEditing = true 
                }) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit Lyrics")
                }
            }

            // Lyrics List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                itemsIndexed(lyrics) { index, line ->
                    val isActive = index == currentActiveLineIndex && isSyncedMode
                    
                    // Calculate estimated end time for the line based on the next line's start time
                    val nextLineStartMs = lyrics.getOrNull(index + 1)?.startTimeMs ?: (line.startTimeMs + 4000L)

                    WordHighlightedText(
                        lineText = line.text,
                        lineStartMs = line.startTimeMs,
                        lineEndMs = nextLineStartMs,
                        currentPositionMs = state.positionMs,
                        isActiveLine = isActive,
                        accentColor = accentColor
                    )
                }
            }

            // Bottom Controls (Synced/Static Toggle & Play/Pause)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    // Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp)),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TextButton(
                            onClick = { 
                                if (isActuallySynced) {
                                    isSyncedMode = true 
                                } else {
                                    Toast.makeText(
                                        context, 
                                        "No timestamps found. Tap the Edit pencil to add them.", 
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }, 
                            modifier = Modifier.weight(1f), 
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = if (isSyncedMode) accentColor.copy(alpha = 0.2f) else Color.Transparent,
                                contentColor = if (isSyncedMode) accentColor 
                                               else if (!isActuallySynced) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) 
                                               else MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text("Synced", fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = { isSyncedMode = false }, 
                            modifier = Modifier.weight(1f), 
                            colors = ButtonDefaults.textButtonColors(containerColor = if (!isSyncedMode) accentColor.copy(alpha = 0.2f) else Color.Transparent)
                        ) {
                            Text("Static", color = if (!isSyncedMode) accentColor else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    // Mini Playback
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onTogglePlayPause, modifier = Modifier.background(accentColor, CircleShape).size(48.dp)) {
                            Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White)
                        }
                        Spacer(Modifier.width(16.dp))
                        PremiumSeekbar(value = state.positionMs.toFloat() / (if (state.durationMs > 0) state.durationMs else 1), onValueChange = {}, onValueChangeFinished = {}, activeColor = accentColor)
                    }
                }
            }
        }
    }
}

@Composable
fun VisualizerLayer(
    activeVisualizerMode: String,
    fftFlow: StateFlow<FftState>,
    accentColor: Color,
    bgColor: Color,
    isPlaying: Boolean = true,
    vinylScale: Float = 0.85f,
    modifier: Modifier = Modifier
) {
    // 0. Base Background (Deep Cinematic Black or Gradient)
    val isCinematic = activeVisualizerMode in listOf(
        "cinematic_leaks", "aurora_borealis", "liquid_spline", "oled_edge", "glass_bars",
        "analog_glitch", "anamorphic_flare", "volumetric_particles",
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

    val fftState by fftFlow.collectAsStateWithLifecycle()
    val rawFft = fftState.data

    val scope = rememberCoroutineScope()
    val fftFloatFlow = remember(fftFlow) {
        fftFlow.map { state ->
            val bytes = state.data ?: return@map FloatArray(0)
            FloatArray(bytes.size / 2) { i ->
                hypot(bytes[i * 2].toFloat(), bytes[i * 2 + 1].toFloat())
            }
        }.stateIn(scope, SharingStarted.WhileSubscribed(5000), FloatArray(0))
    }

    // 🔥 THE 60FPS ENGINE: Maintain a persistent array of smoothed magnitudes
    val smoothedData = remember { FloatArray(128) }
    
    // Continuous 60fps loop that interpolates the current values toward the target FFT
    LaunchedEffect(rawFft, isPlaying) {
        if (rawFft == null || !isPlaying) return@LaunchedEffect
        
        while (true) {
            awaitFrame() // Syncs with the display's 60Hz/120Hz refresh rate
            
            for (i in 0 until minOf(rawFft.size / 2, smoothedData.size)) {
                // Calculate true Pythagorean magnitude
                val real = rawFft[i * 2].toFloat()
                val imaginary = rawFft[i * 2 + 1].toFloat()
                val targetMagnitude = hypot(real, imaginary)
                
                // Low-pass filter formula: current += (target - current) * smoothingFactor
                // Lower factor (0.15f) = buttery smooth glide, Higher factor (0.5f) = snappy
                smoothedData[i] += (targetMagnitude - smoothedData[i]) * 0.15f
            }
        }
    }

    if (rawFft == null || rawFft.isEmpty() || !isPlaying) return

    AnimatedVisibility(
        visible = shouldShowVisualizer,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(500)),
        exit = fadeOut(animationSpec = tween(500))
    ) {
        Crossfade(
            targetState = activeVisualizerMode,
            modifier = Modifier.fillMaxSize(),
            animationSpec = tween(500),
            label = "Visualizer Swap"
        ) { modeId ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // 🔥 THE SYNCED ROUTER
                when (modeId) {
                    // --- THE NEW M-BOX SUITE ---
                    "circular_rings" -> CircularSpectrumVisualizer(
                        fftData = rawFft,
                        modifier = Modifier.fillMaxSize().alpha(0.8f),
                        accentColor = accentColor,
                        vinylScale = vinylScale
                    )
                    "orbiting_stardust" -> OrbitingStardustVisualizer(
                        fftData = rawFft,
                        accentColor = accentColor,
                        vinylScale = vinylScale,
                        modifier = Modifier.fillMaxSize()
                    )
                    "acoustic_ripples" -> AcousticRipplesVisualizer(
                        fftData = rawFft,
                        modifier = Modifier.fillMaxSize(),
                        accentColor = accentColor,
                        vinylScale = vinylScale
                    )
                    "oled_edge" -> OledEdgeVisualizer(
                        fftData = rawFft,
                        modifier = Modifier.fillMaxSize(),
                        color = accentColor
                    )
                    "aurora_borealis" -> AuroraBorealisVisualizer(
                        fftFlow = fftFloatFlow,
                        modifier = Modifier.fillMaxSize(),
                        color = accentColor
                    )
                    "liquid_spline" -> LiquidSplineVisualizer(
                        fftFlow = fftFloatFlow,
                        modifier = Modifier.fillMaxSize(),
                        color = accentColor
                    )
                    "glass_bars" -> BottomGlassBars(
                        smoothedData = smoothedData,
                        accentColor = accentColor,
                        modifier = Modifier.fillMaxSize()
                    )
                    "groove_bleed" -> VinylGrooveBleedVisualizer(
                        fftFlow = fftFloatFlow,
                        modifier = Modifier.fillMaxSize(),
                        color = accentColor
                    )
                    "hex_grid" -> HexagonalAudioGridVisualizer(
                        fftFlow = fftFloatFlow,
                        modifier = Modifier.fillMaxSize(),
                        color = accentColor
                    )
                    "cinematic_leaks" -> ReferenceLightLeaks(
                        modifier = Modifier.fillMaxSize()
                    )
                    "analog_glitch" -> AnalogGlitchVisualizer(
                        modifier = Modifier.fillMaxSize()
                    )
                    "anamorphic_flare" -> AnamorphicFlareVisualizer(
                        modifier = Modifier.fillMaxSize()
                    )
                    "volumetric_particles" -> VolumetricParticleVisualizer(
                        modifier = Modifier.fillMaxSize()
                    )

                    // 🔥 THE NEW CINEMATIC VIDEO BACKGROUNDS
                    "video_glitch" -> LoopingVideoVisualizer(
                        videoResId = R.raw.glitch_burn,
                        isPlaying = isPlaying, // Pass playback state
                        modifier = Modifier.fillMaxSize()
                    )
                    "video_flare" -> LoopingVideoVisualizer(
                        videoResId = R.raw.lens_flare,
                        isPlaying = isPlaying, // Pass playback state
                        modifier = Modifier.fillMaxSize()
                    )
                    "video_particles" -> LoopingVideoVisualizer(
                        videoResId = R.raw.particle_grid,
                        isPlaying = isPlaying, // Pass playback state
                        modifier = Modifier.fillMaxSize()
                    )

                    // --- YOUR CLASSIC PRESETS ---
                    "symmetrical_waves", "1", "2" -> SymmetricalVisualizer(
                        fftFlow = fftFloatFlow,
                        modifier = Modifier.fillMaxSize().alpha(0.3f),
                        barColor = accentColor
                    )
                    "particle_swarms", "5", "8" -> SpectrumDotsVisualizer(
                        fftFlow = fftFloatFlow,
                        modifier = Modifier.fillMaxSize().alpha(0.3f),
                        dotColor = accentColor
                    )
                    "digital_bars", "6" -> NarrowVisualizer(
                        fftFlow = fftFloatFlow,
                        modifier = Modifier.fillMaxSize().alpha(0.3f),
                        barColor = accentColor
                    )
                    "oscilloscope", "plain_bars", "7" -> PlainVisualizer(
                        fftFlow = fftFloatFlow,
                        modifier = Modifier.fillMaxSize().alpha(0.3f),
                        barColor = accentColor
                    )

                    // --- SAFE FALLBACK ---
                    else -> Box(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun VinylAlbumCover(
    imageUri: String?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val basePlaybackVelocityPerSec = 200f
    val safeDurationMs = if (durationMs > 0) durationMs else 300_000L
    val maxAbsoluteDegrees = (safeDurationMs / 1000f) * basePlaybackVelocityPerSec

    // 1. Phase 3 State Isolation: Raw FloatState to bypass recomposition
    val visualDegreesState = remember(imageUri) { 
        mutableFloatStateOf((positionMs / 1000f) * basePlaybackVelocityPerSec) 
    }
    var isDragging by remember { mutableStateOf(false) }
    val currentIsPlaying by rememberUpdatedState(isPlaying)

    // 2. The 60/120Hz V-Sync Hardware Loop
    LaunchedEffect(Unit) {
        var lastFrameNanos = -1L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastFrameNanos == -1L) {
                    lastFrameNanos = frameNanos
                } else {
                    val deltaNanos = frameNanos - lastFrameNanos
                    var deltaSeconds = deltaNanos / 1_000_000_000f
                    lastFrameNanos = frameNanos

                    // Anti-Wake Clamp
                    if (deltaSeconds > 0.1f) deltaSeconds = 0.016f

                    if (currentIsPlaying && !isDragging) {
                        val playbackDelta = basePlaybackVelocityPerSec * deltaSeconds
                        // Update raw state directly
                        visualDegreesState.floatValue = (visualDegreesState.floatValue + playbackDelta)
                            .coerceIn(0f, maxAbsoluteDegrees)
                    }
                }
            }
        }
    }

    // 3. Relaxed 180° Shock Fence: Snap only on major jumps
    LaunchedEffect(positionMs) {
        val expectedAngle = (positionMs / 1000f) * basePlaybackVelocityPerSec
        val drift = abs(expectedAngle - visualDegreesState.floatValue)

        if (!isDragging && drift > 180f) {
            visualDegreesState.floatValue = expectedAngle
        }
    }

    Surface(
        modifier = modifier
            .shadow(24.dp, CircleShape)
            .pointerInput(durationMs) { // 5. Preserve DJ Scrubbing
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onDrag = { change, _ ->
                        val size = size
                        val center = Offset(size.width / 2f, size.height / 2f)

                        val startAngle = atan2(change.previousPosition.y - center.y, change.previousPosition.x - center.x) * (180f / PI.toFloat())
                        val endAngle = atan2(change.position.y - center.y, change.position.x - center.x) * (180f / PI.toFloat())

                        var delta = (endAngle - startAngle).toFloat()
                        if (delta > 180f) delta -= 360f
                        if (delta < -180f) delta += 360f

                        delta *= 4f // 4x DJ scrub multiplier

                        // Manual drag deltas update visualDegreesState.floatValue directly
                        visualDegreesState.floatValue = (visualDegreesState.floatValue + delta)
                            .coerceIn(0f, maxAbsoluteDegrees)
                        
                        onSeekTo((visualDegreesState.floatValue / basePlaybackVelocityPerSec * 1000L).toLong())
                        change.consume()
                    }
                )
            },
        shape = CircleShape,
        color = Color(0xFF111111),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Box(
            // 🔥 THE 120FPS SECRET: Reading floatValue inside this block bypasses Recomposition entirely!
            modifier = Modifier.graphicsLayer { 
                rotationZ = visualDegreesState.floatValue % 360f 
            },
            contentAlignment = Alignment.Center
        ) {
            repeat(12) { i ->
                Surface(
                    modifier = Modifier.fillMaxSize((12 - i) / 12f),
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.04f))
                ) {}
            }

            Surface(
                modifier = Modifier.fillMaxSize(0.65f),
                shape = CircleShape,
                color = Color(0xFF222222),
                border = BorderStroke(2.dp, Color.Black)
            ) {
                AnimatedContent(
                    targetState = imageUri,
                    transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) },
                    label = "VinylArtDissolve"
                ) { targetUri ->
                    if (targetUri != null) {
                        GlideImage(
                            model = targetUri,
                            contentDescription = "Album Art",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.MusicNote, null, Modifier.size(48.dp), tint = Color.White.copy(alpha = 0.1f))
                        }
                    }
                }
            }
            Surface(
                modifier = Modifier.size(12.dp),
                shape = CircleShape,
                color = Color(0xFF0A0A0A),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
            ) {}
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
    var dragFraction by remember { mutableFloatStateOf(-1f) }
    val currentFraction = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs) else 0f
    val displayFraction = if (dragFraction >= 0f) dragFraction else currentFraction

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PremiumSeekbar(
            value = displayFraction,
            onValueChange = { dragFraction = it },
            onValueChangeFinished = {
                onSeekTo((dragFraction * state.durationMs).toLong())
                dragFraction = -1f
            },
            activeColor = accentColor
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(
                text = state.positionMs.toTimeString(),
                style = MaterialTheme.typography.labelSmall,
                color = timerTextColor
            )
            CodecBadge(state.currentTrack, accentColor, onShowAudioInfo)
            Text(
                text = state.durationMs.toTimeString(),
                style = MaterialTheme.typography.labelSmall,
                color = timerTextColor
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumSeekbar(value: Float, onValueChange: (Float) -> Unit, onValueChangeFinished: () -> Unit, activeColor: Color) {
    Slider(
        value = value, onValueChange = onValueChange, onValueChangeFinished = onValueChangeFinished, modifier = Modifier.fillMaxWidth(),
        colors = SliderDefaults.colors(thumbColor = activeColor, activeTrackColor = activeColor, inactiveTrackColor = activeColor.copy(alpha = 0.1f)),
        thumb = {
            Surface(
                modifier = Modifier.size(14.dp),
                shape = CircleShape,
                color = activeColor,
                shadowElevation = 4.dp
            ) {}
        },
        track = { sliderState ->
            val fraction = (sliderState.value - sliderState.valueRange.start) / (sliderState.valueRange.endInclusive - sliderState.valueRange.start)
            Box(Modifier.fillMaxWidth().height(18.dp), Alignment.CenterStart) {
                Box(Modifier.fillMaxWidth().height(2.dp).clip(CircleShape).background(activeColor.copy(alpha = 0.15f)))
                Box(Modifier.fillMaxWidth(fraction).height(4.dp).clip(CircleShape).background(activeColor))
            }
        }
    )
}

@Composable
fun CodecBadge(track: Track?, accentColor: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, color = accentColor.copy(alpha = 0.1f), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f))) {
        val isOpus = track?.mimeType?.contains("opus", true) == true || track?.mimeType?.contains("ogg", true) == true
        val sampleRate = if (isOpus) "48.0 kHz" else (track?.sampleRate?.let { "${it / 1000.0} kHz" } ?: "48.0 kHz")
        val bitrate = if (isOpus) "237 kbps" else (track?.bitrate?.let { "${it / 1000} kbps" } ?: "168 kbps")
        val format = if (isOpus) "OPUS" else (track?.mimeType?.substringAfterLast("/")?.uppercase() ?: "OPUS")
        Text("$sampleRate • $bitrate • $format", Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = accentColor)
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
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
        // Shuffle Button
        IconButton(onClick = onToggleShuffle) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = "Shuffle",
                tint = if (state.shuffleOn) accentColor else controlIconColor.copy(alpha = 0.4f)
            )
        }

        // Previous Button
        IconButton(onClick = onSkipPrevious, Modifier.size(64.dp)) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = "Previous",
                modifier = Modifier.size(40.dp),
                tint = controlIconColor
            )
        }

        // Play/Pause Button (Keeps accent color)
        Surface(
            onClick = onTogglePlayPause,
            shape = CircleShape,
            color = accentColor,
            modifier = Modifier
                .size(80.dp)
                .shadow(12.dp, CircleShape)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    null,
                    Modifier.size(44.dp),
                    tint = Color.White
                )
            }
        }

        // Next Button
        IconButton(onClick = onSkipNext, Modifier.size(64.dp)) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "Next",
                modifier = Modifier.size(40.dp),
                tint = controlIconColor
            )
        }

        // Repeat Button
        IconButton(onClick = onCycleRepeatMode) {
            Icon(
                imageVector = if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                contentDescription = "Repeat",
                tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) accentColor else controlIconColor.copy(alpha = 0.4f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioInfoBottomSheet(signalPath: AudioSignalPath, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF121212), contentColor = Color.White, dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState())) {
            Text("Audio Info", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(16.dp))
            PipelineItem("Track", "${signalPath.format} ${signalPath.sourceInfo}\n${signalPath.channels}", Icons.Rounded.Album, "Gapless")
            PipelineItem("Decoder", signalPath.decoder, Icons.Rounded.Memory, iconColor = Color(0xFF4CAF50))
            PipelineItem("Output", signalPath.outputApi, Icons.Rounded.ElectricalServices, "32 bit -> 16 bit\n48 kHz", iconColor = Color(0xFFFF9800), showLine = false)
        }
    }
}

@Composable
fun VisualizerToggleButton(
    isEnabled: Boolean,
    onClick: () -> Unit,
    accentColor: Color
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = if (isEnabled) Icons.Filled.GraphicEq else Icons.Outlined.GraphicEq,
            contentDescription = "Toggle Visualizer",
            tint = if (isEnabled) accentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )
    }
}

@Composable
fun PipelineItem(title: String, content: String, icon: ImageVector?, extra: String? = null, iconColor: Color = Color.White, showLine: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(Modifier.width(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (icon != null) Icon(icon, null, Modifier.size(20.dp), tint = iconColor)
        }
        Column(Modifier.padding(start = 8.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
            Text(content, style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp), color = Color.White.copy(alpha = 0.9f))
            if (extra != null) Text(extra, style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.Underline), color = Color.White.copy(alpha = 0.7f))
        }
    }
}
