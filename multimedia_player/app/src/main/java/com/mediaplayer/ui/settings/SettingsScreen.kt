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
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
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
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.documentfile.provider.DocumentFile
import androidx.compose.ui.text.font.FontWeight
import com.mediaplayer.ui.components.BluetoothDeviceBottomSheet
import com.mediaplayer.util.LocalAudioServer
import com.mediaplayer.util.WifiDiscoveryManager
import kotlinx.coroutines.launch
import java.util.Locale

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

    LaunchedEffect(Unit) {
        vm.registerUsbDacListener(context)
    }
    
    // 🔥 NEW REPOSITORY INTEGRATION
    val folderRepo = remember { com.mediaplayer.data.repository.FolderPreferencesRepository(context) }
    val coroutineScope = rememberCoroutineScope()
    val savedFolders by folderRepo.selectedFoldersFlow.collectAsState(initial = emptySet())

    // Dialog state
    var showThemePicker      by remember { mutableStateOf(false) }
    var showCrossfadePicker  by remember { mutableStateOf(false) }
    var showNotifStylePicker by remember { mutableStateOf(false) }
    var showReplayGainPicker by remember { mutableStateOf(false) }
    var showMinDurPicker     by remember { mutableStateOf(false) }
    var showVisualizationSheet by remember { mutableStateOf(false) }
    var showFolderSelection  by remember { mutableStateOf(false) }
    var showSpeedPicker      by remember { mutableStateOf(false) }
    var showReverbPicker     by remember { mutableStateOf(false) }
    var showOutputPrecisionPicker by remember { mutableStateOf(false) }
    var showLyricsSourcesSheet by remember { mutableStateOf(false) }
    var showSeekBarStylePicker by remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { 
            // 1. Take persistent permission to survive reboots
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, takeFlags)
            
            // 2. Save the URI string to DataStore using the new Repository
            coroutineScope.launch {
                folderRepo.addFolderUri(it.toString())
            }
        }
    }

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

            SettingItem(
                icon  = Icons.Rounded.GraphicEq,
                title = "Seek bar style",
                sub   = if (s.seekBarStyle == "wavy") "Wavy line" else "Time inside pill",
                onClick = { showSeekBarStylePicker = true }
            )

            SwitchSetting(
                icon    = Icons.Rounded.OpenInFull,
                title   = "Open Now Playing when a song starts",
                sub     = "Automatically open full screen player on song select",
                checked = s.openNowPlayingOnSongStart,
                onToggle = vm::setOpenNowPlayingOnStart
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

            SwitchSetting(
                icon    = Icons.Rounded.GraphicEq,
                title   = "Skip silence",
                sub     = "Trim gaps longer than a second",
                checked = s.skipSilence,
                onToggle = vm::setSkipSilence
            )

            SettingItem(
                icon  = Icons.Rounded.Speed,
                title = "Playback speed / pitch",
                sub   = String.format("%.1fx speed %s", s.playbackSpeed, if (s.pitchLock) "· Pitch locked" else ""),
                onClick = { showSpeedPicker = true }
            )

            // ── EFFECTS ──────────────────────────────────────────────────────
            SectionHeader("Effects")

            val pState by playerVm.state.collectAsStateWithLifecycle()

            SwitchSetting(
                icon    = Icons.Rounded.Equalizer,
                title   = "Bass Boost",
                sub     = "Low frequency enhancement",
                checked = pState.bassBoostOn,
                onToggle = { playerVm.setBassBoostEnabled(it) }
            )

            SwitchSetting(
                icon    = Icons.Rounded.SurroundSound,
                title   = "Virtualizer",
                sub     = "3D spatial audio widening",
                checked = pState.virtualizerOn,
                onToggle = { playerVm.setVirtualizerEnabled(it) }
            )

            SwitchSetting(
                icon    = Icons.Rounded.VolumeUp,
                title   = "Loudness Enhancer",
                sub     = "Gain boost for quiet recordings",
                checked = pState.loudnessOn,
                onToggle = { playerVm.setLoudnessEnabled(it) }
            )

            SettingItem(
                icon  = Icons.Rounded.MeetingRoom,
                title = "Reverb / Environment",
                sub   = s.reverbPreset,
                onClick = { showReverbPicker = true }
            )

            // ── OUTPUT ───────────────────────────────────────────────────────
            SectionHeader("Output")

            SettingItem(
                icon  = Icons.Rounded.Memory,
                title = "Output precision",
                sub   = if (s.isUsbDacConnected) s.outputPrecision else "AudioTrack · CPH2649 · 48.0 kHz · 16-bit PCM",
                onClick = { showOutputPrecisionPicker = true }
            )

            ListItem(
                headlineContent = { Text("Prefer USB DAC") },
                supportingContent = { Text("Route bit-perfect audio directly to external USB DACs") },
                leadingContent = { Icon(Icons.Rounded.Usb, null) },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (s.isUsbDacConnected) {
                            Surface(
                                color = Color(0xFF4CAF50).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF4CAF50)),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    text = "CONNECTED",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF4CAF50),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Switch(checked = s.preferUsbDac, onCheckedChange = vm::setPreferUsbDac)
                    }
                },
                modifier = Modifier.clickable { vm.setPreferUsbDac(!s.preferUsbDac) }
            )

            SettingItem(
                icon  = Icons.Rounded.GraphicEq,
                title = "Sample rate",
                sub   = if (s.isUsbDacConnected) "${s.usbDacSampleRate} (USB DAC Native)" else "48.0 kHz (Hardware Native)"
            )

            SwitchSetting(
                icon    = Icons.Rounded.Hearing,
                title   = "Mono audio",
                sub     = "Combine channels into mono output for accessibility",
                checked = s.monoAudio,
                onToggle = vm::setMonoAudio
            )

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

            SettingItem(
                icon  = Icons.Rounded.Subtitles,
                title = "Lyrics sources",
                sub   = "Manage multi-source lyrics providers & ordering",
                onClick = { showLyricsSourcesSheet = true }
            )

            // ── TV CONNECTION (Wi-Fi) ─────────────────────────────────────
            SectionHeader("TV Connection (Wi-Fi)")

            val localLanIp = remember { LocalAudioServer(context).getLocalIpAddress() ?: "WiFi Not Connected" }


            SettingItem(
                icon  = Icons.Rounded.Wifi,
                title = "Phone LAN Address (WiFi)",
                sub   = if (localLanIp.startsWith("1") || localLanIp.startsWith("192") || localLanIp.startsWith("10")) "http://$localLanIp:8787" else localLanIp
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

            SettingItem(
                icon  = Icons.Rounded.FolderSpecial,
                title = "Music folders",
                sub   = "${savedFolders.size} folders selected",
                onClick = { showFolderSelection = true }
            )

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
                sub   = "1.2.0"
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
        val playerState by playerVm.state.collectAsStateWithLifecycle()
        VisualizationModeSheet(
            onDismiss = { showVisualizationSheet = false },
            onSettingsClick = onOpenVisualizerSettings, // 🔥 PASSING THE CALLBACK
            controller = playerVm,
            activeModeId = playerState.activeVisualizerMode
        )
    }

    if (showFolderSelection) {
        FolderSelectionDialog(
            selectedUris = savedFolders.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() },
            onAddFolderClick = { folderPickerLauncher.launch(null) },
            onRemoveFolder = { uri ->
                coroutineScope.launch {
                    folderRepo.removeFolderUri(uri.toString())
                }
            },
            onSaveAndScanClick = {
                showFolderSelection = false
                // Trigger scan
                val forceSyncRequest = OneTimeWorkRequestBuilder<LibrarySyncWorker>().build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "AutoLibrarySync",
                    ExistingWorkPolicy.REPLACE, 
                    forceSyncRequest
                )
                Toast.makeText(context, "Scanning selected folders...", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showFolderSelection = false }
        )
    }

    if (showSpeedPicker) {
        AlertDialog(
            onDismissRequest = { showSpeedPicker = false },
            title = { Text("Playback speed & pitch") },
            text = {
                Column {
                    Text(
                        text = String.format(Locale.US, "Speed: %.1fx", s.playbackSpeed),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Slider(
                        value = s.playbackSpeed,
                        onValueChange = { vm.setPlaybackSpeed(it) },
                        valueRange = 0.5f..2.0f
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { vm.setPitchLock(!s.pitchLock) }
                    ) {
                        Checkbox(checked = s.pitchLock, onCheckedChange = { vm.setPitchLock(it) })
                        Text("Lock pitch (preserve original voice pitch)")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedPicker = false }) { Text("Done") }
            }
        )
    }

    if (showSeekBarStylePicker) {
        RadioDialog(
            title   = "Seek bar style",
            options = listOf("pill" to "Time inside pill", "wavy" to "Wavy line"),
            current = s.seekBarStyle,
            onSelect= {
                vm.setSeekBarStyle(it)
                showSeekBarStylePicker = false
            },
            onDismiss = { showSeekBarStylePicker = false }
        )
    }

    if (showReverbPicker) {
        RadioDialog(
            title   = "Reverb / Environment",
            options = listOf("Off", "Small Room", "Large Hall", "Plate", "Studio").map { it to it },
            current = s.reverbPreset,
            onSelect = { vm.setReverbPreset(it); showReverbPicker = false },
            onDismiss = { showReverbPicker = false }
        )
    }

    if (showOutputPrecisionPicker) {
        RadioDialog(
            title   = "Output precision",
            options = listOf("16-bit PCM", "24-bit High-Res", "32-bit Float").map { it to it },
            current = s.outputPrecision,
            onSelect = { vm.setOutputPrecision(it); showOutputPrecisionPicker = false },
            onDismiss = { showOutputPrecisionPicker = false }
        )
    }

    if (showLyricsSourcesSheet) {
        LyricsSourcesScreen(
            onDismiss = { showLyricsSourcesSheet = false }
        )
    }
}

@Composable
fun FolderSelectionDialog(
    selectedUris: List<Uri>,
    onAddFolderClick: () -> Unit,
    onRemoveFolder: (Uri) -> Unit,
    onSaveAndScanClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Folders Selection") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "M-Box will only scan these specific folders for music.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                if (selectedUris.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No folders added yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(selectedUris) { uri ->
                            val folderName = DocumentFile.fromTreeUri(context, uri)?.name ?: "Unknown Folder"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(folderName, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                                    Text(
                                        uri.path ?: "", 
                                        style = MaterialTheme.typography.bodySmall, 
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                IconButton(onClick = { onRemoveFolder(uri) }) {
                                    Icon(Icons.Rounded.Delete, "Remove", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = onAddFolderClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Select Folder or Storage")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSaveAndScanClick) {
                Text("Save and Scan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
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
