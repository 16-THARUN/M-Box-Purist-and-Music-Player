package com.mediaplayer.ui.player

import android.content.res.Configuration
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.bumptech.glide.Glide
import com.mediaplayer.data.model.AudioSignalPath
import com.mediaplayer.data.model.Track
import com.mediaplayer.ui.components.CdPlayerAlbumArt
import com.mediaplayer.ui.components.WavySlider
import com.mediaplayer.ui.theme.MBoxOrange
import com.mediaplayer.ui.theme.MediaPlayerTheme
import com.mediaplayer.util.extractDarkDominantColor
import com.mediaplayer.viewmodel.PlayerViewModel
import com.mediaplayer.viewmodel.toTimeString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// This file is being replaced by NowPlayingScreen.kt for the main UI.
// Keeping this as a legacy entry point or to be refactored later.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onOpenEq: () -> Unit,
    onBack: () -> Unit
) {
    NowPlayingScreen(
        onOpenEq = onOpenEq,
        onOpenQueue = {}, // Legacy entry point doesn't have a queue action defined here
        onBack = onBack
    )
}
