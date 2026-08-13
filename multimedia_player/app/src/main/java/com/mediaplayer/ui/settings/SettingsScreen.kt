package com.mediaplayer.ui.settings

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mediaplayer.data.prefs.AppTheme
import com.mediaplayer.data.prefs.NotificationStyle
import com.mediaplayer.data.model.mboxVisualizerPresets
import com.mediaplayer.data.scanner.LibrarySyncWorker
import com.mediaplayer.ui.components.VisualizationModeSheet
import com.mediaplayer.util.UpdateManager
import com.mediaplayer.viewmodel.PlayerViewModel
import com.mediaplayer.viewmodel.SettingsViewModel

// ─────────────────────────────────────────────────────────────────────────────
// SettingsScreen
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenEq: () -> Unit,
    onOpenVisualizerSettings: () -> Unit, // 🔥 NEW CALLBACK
    vm: SettingsViewModel = hiltViewModel()
) {
    val activity = LocalContext.current as ComponentActivity
    val context = LocalContext.current
    val playerVm: PlayerViewModel = hiltViewModel(activity)
    val s by vm.state.collectAsStateWithLifecycle()
    val updateManager = remember { UpdateManager(context) }

    // Dialog state
    var showThemePicker      by remember { mutableStateOf(false) }
    var showCrossfadePicker  by remember { mutableStateOf(false) }
    var showNotifStylePicker by remember { mutableStateOf(false) }
    var showReplayGainPicker by remember { mutableStateOf(false) }
    var showMinDurPicker     by remember { mutableStateOf(false) }
    var showVisualizationSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {

            // ── APPEARANCE ───────────────────────────────────────────────────
            SectionHeader("Appearance")

            SettingItem(
                icon  = Icons.Rounded.Palette,
                title = "Theme",
                sub   = s.appTheme.label,
                onClick = { showThemePicker = true }
            )

            // ── PLAYBACK ─────────────────────────────────────────────────────
            SectionHeader("Playback")

            SwitchSetting(
                icon    = Icons.Rounded.BlurOn,
                title   = "Gapless playback",
                sub     = "No silence between tracks",
                checked = s.gapless,
                onToggle = vm::setGapless
            )

            SettingItem(
                icon  = Icons.Rounded.Tune,
                title = "Equalizer",
                sub   = if (s.eqEnabled) "On · ${s.activePresetLabel}" else "Off",
                onClick = onOpenEq
            )

            SettingItem(
                icon  = Icons.Rounded.Waves,
                title = "Crossfade",
                sub   = if (s.crossfadeDuration == 0) "Disabled"
                        else "${s.crossfadeDuration} s",
                onClick = { showCrossfadePicker = true }
            )

            SwitchSetting(
                icon    = Icons.Rounded.VolumeUp,
                title   = "Replay Gain",
                sub     = "Normalize loudness across tracks",
                checked = s.replayGainOn,
                onToggle = vm::setReplayGainOn
            )

            if (s.replayGainOn) {
                SettingItem(
                    icon  = Icons.Rounded.Audiotrack,
                    title = "Replay Gain mode",
                    sub   = if (s.replayGainMode == "album") "Album" else "Track",
                    onClick = { showReplayGainPicker = true },
                    indent = true
                )
            }

            // ── NOTIFICATION / LOCKSCREEN ────────────────────────────────────
            SectionHeader("Notification & lock screen")

            SettingItem(
                icon  = Icons.Rounded.Notifications,
                title = "Notification style",
                sub   = s.notifStyle.label,
                onClick = { showNotifStylePicker = true }
            )

            SwitchSetting(
                icon    = Icons.Rounded.Lock,
                title   = "Show album art on lock screen",
                checked = s.lockscreenArt,
                onToggle = vm::setLockscreenArt
            )

            SwitchSetting(
                icon    = Icons.Rounded.Tag,
                title   = "Show track number in list",
                checked = s.showTrackNumber,
                onToggle = vm::setShowTrackNumber
            )

            // ── LIBRARY SCANNING ─────────────────────────────────────────────
            SectionHeader("Library scanning")

            SwitchSetting(
                icon    = Icons.Rounded.FilterAlt,
                title   = "Skip short files",
                sub     = "Exclude tracks shorter than minimum duration",
                checked = s.scanExcludeShort,
                onToggle = vm::setScanExcludeShort
            )

            if (s.scanExcludeShort) {
                SettingItem(
                    icon  = Icons.Rounded.Timer,
                    title = "Minimum track duration",
                    sub   = "${s.scanMinDuration} seconds",
                    onClick = { showMinDurPicker = true },
                    indent = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    // Force an immediate background sync, replacing any currently running scan
                    val forceSyncRequest = OneTimeWorkRequestBuilder<LibrarySyncWorker>().build()
                    WorkManager.getInstance(context).enqueueUniqueWork(
                        "AutoLibrarySync",
                        ExistingWorkPolicy.REPLACE, 
                        forceSyncRequest
                    )
                    Toast.makeText(context, "Library sync started...", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Force Sync Library", style = MaterialTheme.typography.titleMedium)
            }

            // ── ABOUT ────────────────────────────────────────────────────────
            SectionHeader("About")

            SettingItem(
                icon  = Icons.Rounded.Info,
                title = "Version",
                sub   = "1.1.0"
            )

            // 🔥 THE UPDATE INITIATOR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        Toast.makeText(context, "Fetching latest update from GitHub...", Toast.LENGTH_SHORT).show()
                        
                        // 🔥 THE GITHUB RELEASE LINK
                        // Replace YOUR_GITHUB_NAME and YOUR_REPO_NAME. 
                        // Make sure 'mbox-update.apk' exactly matches the file you uploaded to GitHub!
                        val githubReleaseUrl = "https://github.com/YOUR_GITHUB_NAME/YOUR_REPO_NAME/releases/latest/download/mbox-update.apk"
                        
                        updateManager.downloadAndInstallUpdate(githubReleaseUrl)
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.SystemUpdate,
                    contentDescription = "Check for Updates",
                    tint = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.width(20.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Check for Updates",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Download and install the latest version",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 🔥 THE FIX: An invisible bumper at the bottom of the list
            Spacer(modifier = Modifier.height(112.dp))
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    if (showThemePicker) {
        RadioDialog(
            title   = "Theme",
            options = AppTheme.values().map { it.label to it },
            current = s.appTheme,
            onSelect = { vm.setAppTheme(it); showThemePicker = false },
            onDismiss = { showThemePicker = false }
        )
    }

    if (showCrossfadePicker) {
        RadioDialog(
            title   = "Crossfade duration",
            options = (listOf(0) + (1..12).toList()).map { s ->
                val label = if (s == 0) "Disabled" else "$s seconds"
                label to s
            },
            current = s.crossfadeDuration,
            onSelect = { vm.setCrossfade(it); showCrossfadePicker = false },
            onDismiss = { showCrossfadePicker = false }
        )
    }

    if (showNotifStylePicker) {
        RadioDialog(
            title   = "Notification style",
            options = NotificationStyle.values().map { it.label to it },
            current = s.notifStyle,
            onSelect = { vm.setNotifStyle(it); showNotifStylePicker = false },
            onDismiss = { showNotifStylePicker = false }
        )
    }

    if (showReplayGainPicker) {
        RadioDialog(
            title   = "Replay Gain mode",
            options = listOf("Track" to "track", "Album" to "album"),
            current = s.replayGainMode,
            onSelect = { vm.setReplayGainMode(it); showReplayGainPicker = false },
            onDismiss = { showReplayGainPicker = false }
        )
    }

    if (showMinDurPicker) {
        RadioDialog(
            title   = "Minimum duration",
            options = listOf(10, 20, 30, 60, 90, 120).map { "$it seconds" to it },
            current = s.scanMinDuration,
            onSelect = { vm.setScanMinDuration(it); showMinDurPicker = false },
            onDismiss = { showMinDurPicker = false }
        )
    }

    if (showVisualizationSheet) {
        VisualizationModeSheet(
            onDismiss = { showVisualizationSheet = false },
            onSettingsClick = onOpenVisualizerSettings, // 🔥 PASSING THE CALLBACK
            viewModel = playerVm
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Composable helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text  = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 16.dp, top = 20.dp, bottom = 4.dp)
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun SettingItem(
    icon: ImageVector,
    title: String,
    sub: String = "",
    onClick: (() -> Unit)? = null,
    indent: Boolean = false
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (sub.isNotBlank()) ({ Text(sub) }) else null,
        leadingContent = {
            Icon(
                icon, null,
                modifier = Modifier.padding(start = if (indent) 24.dp else 0.dp),
                tint = if (indent) MaterialTheme.colorScheme.onSurfaceVariant
                       else       MaterialTheme.colorScheme.onSurface
            )
        },
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    )
}

@Composable
private fun SwitchSetting(
    icon: ImageVector,
    title: String,
    sub: String = "",
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    ListItem(
        headlineContent   = { Text(title) },
        supportingContent = if (sub.isNotBlank()) ({ Text(sub) }) else null,
        leadingContent    = { Icon(icon, null) },
        trailingContent   = {
            Switch(checked = checked, onCheckedChange = onToggle)
        },
        modifier = Modifier.clickable { onToggle(!checked) }
    )
}

@Composable
private fun <T> RadioDialog(
    title: String,
    options: List<Pair<String, T>>,
    current: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text  = {
            Column {
                options.forEach { (label, value) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = value == current,
                            onClick  = { onSelect(value) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
