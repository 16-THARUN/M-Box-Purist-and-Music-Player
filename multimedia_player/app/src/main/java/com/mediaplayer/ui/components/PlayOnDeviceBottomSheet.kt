@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
package com.mediaplayer.ui.components

import android.content.Context
import android.content.Intent
import android.media.MediaRouter2
import android.os.Build
import android.provider.Settings
import androidx.annotation.OptIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.mediaplayer.viewmodel.PlayerViewModel


data class DiscoveredCastRoute(
    val id: String,
    val name: String,
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val route: MediaRouter.RouteInfo
)

@OptIn(ExperimentalFoundationApi::class)
@ExperimentalMaterial3Api
@Composable
fun PlayOnDeviceBottomSheet(
    playerState: PlayerViewModel.PlayerUiState,
    onSelectOutputDevice: (String) -> Unit,
    onOpenOutputSettings: () -> Unit,
    onSelectCastRoute: (MediaRouter.RouteInfo) -> Unit,
    playerVm: PlayerViewModel? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val track = playerState.currentTrack

    var castRoutes by remember { mutableStateOf<List<DiscoveredCastRoute>>(emptyList()) }
    var isSearching by remember { mutableStateOf(true) }
    var showCastDebugOverlay by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val mediaRouter = MediaRouter.getInstance(context)
        val selector = MediaRouteSelector.Builder()
            .addControlCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO)
            .build()

        val callback = object : MediaRouter.Callback() {
            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) { updateRoutes(router) }
            override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) { updateRoutes(router) }
            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) { updateRoutes(router) }

            private fun updateRoutes(router: MediaRouter) {
                val list = router.routes.filter { !it.isDefault && !it.isBluetooth }.map {
                    DiscoveredCastRoute(id = it.id, name = it.name, isConnected = it.isSelected, route = it)
                }
                castRoutes = list
                isSearching = false
            }
        }

        mediaRouter.addCallback(selector, callback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
        castRoutes = mediaRouter.routes.filter { !it.isDefault && !it.isBluetooth }.map {
            DiscoveredCastRoute(id = it.id, name = it.name, isConnected = it.isSelected, route = it)
        }
        isSearching = false

        onDispose {
            mediaRouter.removeCallback(callback)
        }
    }

    val qualityBadge = when {
        track?.mimeType?.contains("eac3", true) == true || track?.title?.contains("leader", true) == true -> "E-AC-3 JOC"
        (track?.sampleRate ?: 0) > 48000 || (track?.bitrate ?: 0) > 1500000 -> "Hi-Res"
        track?.mimeType?.contains("flac", true) == true || track?.mimeType?.contains("alac", true) == true || track?.mimeType?.contains("wav", true) == true -> "Lossless"
        else -> track?.mimeType?.substringAfterLast("/")?.uppercase() ?: "MP3"
    }

    val activeDeviceName = when (playerState.activeOutputDevice) {
        "m-box TV" -> if (playerState.tvDeviceName.isNotBlank()) playerState.tvDeviceName else "m-box TV"
        "Cast" -> playerState.castDeviceName.ifBlank { "Chromecast" }
        else -> "This phone"
    }

    val hasTvAvailable = playerState.isMBoxTvConnected ||
                        playerState.tvDeviceName.isNotBlank() ||
                        playerState.activeOutputDevice == "m-box TV"

    val castState = playerVm?.castState?.collectAsStateWithLifecycle()?.value

    if (showCastDebugOverlay) {
        CastDebugOverlay(
            rttMs = castState?.rttMs ?: 0L,
            latencyMs = castState?.latencyMs ?: 0L,
            lastSeq = castState?.lastSeq ?: 0L,
            tvPosSec = castState?.shownPositionSec ?: 0.0,
            shownPosSec = castState?.shownPositionSec ?: 0.0,
            driftMs = castState?.driftMs ?: 0L,
            recentEvents = castState?.recentEvents ?: emptyList(),
            recentCommands = castState?.recentCommands ?: emptyList(),
            onDismiss = { showCastDebugOverlay = false }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF14171A),
        contentColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Play on",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E2228),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = activeDeviceName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFFF5722).copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = qualityBadge,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFFFF5722),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (track != null) {
                            Text(
                                text = "${track.title} · ${track.artist}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(0xFFFF5722),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        if (playerState.activeOutputDevice == "This phone") {
                            Text(
                                text = playerState.signalPath?.deviceType ?: "Built-in speaker",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    IconButton(onClick = { showCastDebugOverlay = true }) {
                        Icon(
                            imageVector = if (playerState.activeOutputDevice == "This phone") Icons.Rounded.Smartphone else Icons.Rounded.Tv,
                            contentDescription = "Debug Overlay",
                            tint = Color(0xFFFF5722),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // TV Controls (Volume, Mute, Lyrics on TV, Ambient Cover) when active
            if (playerState.activeOutputDevice == "m-box TV" && playerVm != null) {
                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1E2228),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "TV CONTROLS",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFFF5722),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // TV volume: only the phone's volume buttons (they press the TV's own volume keys,
                        // so the soundbar / Q-Symphony follows). No slider: it jumped the volume (2026-10-22).
                        Text(
                            text = "TV volume ${castState?.tvVolume ?: "–"}${if (castState?.isMuted == true) " (muted)" else ""} · use the phone's volume buttons",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Lyrics on TV switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Lyrics on TV", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                            Switch(
                                checked = castState?.lyricsOn ?: false,
                                onCheckedChange = { playerVm.sendSetLyricsToTv(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFF5722))
                            )
                        }

                        // Ambient Cover Calm Screen
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Ambient Cover (Calm Screen)", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                            Switch(
                                checked = castState?.displayMode == "ambient",
                                onCheckedChange = {
                                    playerVm.sendSetDisplayModeToTv(if (it) "ambient" else "vinyl")
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFF5722))
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 250.dp)
            ) {
                if (hasTvAvailable) {
                    item {
                        Text(
                            text = "M-BOX TV",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }

                    item {
                        val isTvActive = playerState.activeOutputDevice == "m-box TV"
                        val tvName = if (playerState.tvDeviceName.isNotBlank()) playerState.tvDeviceName else "m-box TV"
                        val dolbyNote = if (qualityBadge.contains("E-AC-3") || track?.mimeType?.contains("eac3", true) == true || track?.title?.contains("leader", true) == true) " · Dolby → soundbar" else ""

                        DeviceRowItem(
                            title = tvName,
                            subtitle = "m-box TV$dolbyNote",
                            icon = Icons.Rounded.Tv,
                            isActive = isTvActive,
                            onClick = {
                                onSelectOutputDevice("m-box TV")
                                onDismiss()
                            },
                            onLongClick = {
                                showCastDebugOverlay = true
                            }
                        )
                    }
                }

                if (castRoutes.isNotEmpty()) {
                    item {
                        Text(
                            text = "CHROMECAST",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }

                    items(castRoutes) { routeItem ->
                        val isCastActive = playerState.activeOutputDevice == "Cast" && playerState.castDeviceName == routeItem.name
                        DeviceRowItem(
                            title = routeItem.name,
                            subtitle = "Chromecast",
                            icon = Icons.Rounded.Cast,
                            isActive = isCastActive,
                            onClick = {
                                onSelectCastRoute(routeItem.route)
                                onDismiss()
                            }
                        )
                    }
                }

                if (playerState.activeOutputDevice != "This phone") {
                    item {
                        Text(
                            text = "LOCAL",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }

                    item {
                        DeviceRowItem(
                            title = "This phone",
                            subtitle = playerState.signalPath?.deviceType ?: "Built-in speaker",
                            icon = Icons.Rounded.Smartphone,
                            isActive = false,
                            onClick = {
                                onSelectOutputDevice("This phone")
                                onDismiss()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= 34) {
                        try {
                            val mediaRouter2 = MediaRouter2.getInstance(context)
                            mediaRouter2.showSystemOutputSwitcher()
                        } catch (e: Exception) {
                            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        }
                    } else {
                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    }
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2228))
            ) {
                Icon(Icons.Rounded.Bluetooth, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Find Bluetooth devices", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DeviceRowItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) Color(0xFFFF5722).copy(alpha = 0.15f) else Color(0xFF1E2228),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) Color(0xFFFF5722) else Color.White,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = if (isActive) Color(0xFFFF5722) else Color.White
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }

            if (isActive) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = "Connected",
                    tint = Color(0xFFFF5722),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
