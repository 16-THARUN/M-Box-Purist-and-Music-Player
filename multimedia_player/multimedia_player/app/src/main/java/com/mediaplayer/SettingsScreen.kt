package com.mediaplayer.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mediaplayer.data.prefs.AppTheme
import com.mediaplayer.data.prefs.NotificationStyle
import com.mediaplayer.viewmodel.SettingsViewModel

// ─────────────────────────────────────────────────────────────────────────────
// SettingsScreen
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenEq: () -> Unit,
    vm: SettingsViewModel = hiltViewModel()
) {
    val s by vm.state.collectAsState()

    // Dialog state
    var showThemePicker      by remember { mutableStateOf(false) }
    var showCrossfadePicker  by remember { mutableStateOf(false) }
    var showNotifStylePicker by remember { mutableStateOf(false) }
    var showReplayGainPicker by remember { mutableStateOf(false) }
    var showMinDurPicker     by remember { mutableStateOf(false) }

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

            // ── ABOUT ────────────────────────────────────────────────────────
            SectionHeader("About")

            SettingItem(
                icon  = Icons.Rounded.Info,
                title = "Version",
                sub   = "1.0.0"
            )

            Spacer(Modifier.height(32.dp))
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
