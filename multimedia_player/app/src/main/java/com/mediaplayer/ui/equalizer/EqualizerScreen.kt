package com.mediaplayer.ui.equalizer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mediaplayer.data.prefs.EqPreset
import com.mediaplayer.viewmodel.PlayerViewModel

private val BAND_LABELS = listOf("32", "64", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
private const val MIN_MB = -1500f
private const val MAX_MB = 1500f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    onBack: () -> Unit,
    vm: PlayerViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val eqBands by vm.eqBands.collectAsStateWithLifecycle()

    val currentTrack = state.currentTrack

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Equalizer", style = MaterialTheme.typography.titleLarge)
                        if (currentTrack != null) {
                            Text(
                                text = "Playing: ${currentTrack.title}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, "Back")
                    }
                },
                actions = {
                    Text(
                        text = if (state.eqEnabled) "On" else "Off",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (state.eqEnabled)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Switch(
                        checked = state.eqEnabled,
                        onCheckedChange = vm::setEqEnabled,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // ── Preset Header ─────────────────────────────────
            PresetHeader(vm)

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(vm.eqPresetsLabels) { label ->
                    val preset = EqPreset.values().find { it.label == label } ?: EqPreset.FLAT
                    FilterChip(
                        selected = state.activePreset == preset,
                        onClick = { vm.applyEqPreset(preset) },
                        label = { Text(preset.label) }
                    )
                }
                items(state.customPresets) { preset ->
                    FilterChip(
                        selected = false, // We don't have an active custom preset state yet
                        onClick = { vm.applyAudioPreset(preset) },
                        label = { Text(preset.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── 10-band EQ sliders ────────────────────────────
            Text("10-band equalizer", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                eqBands.forEachIndexed { i, level ->
                    EqBandSlider(
                        band = i,
                        levelMb = (level * 100).toInt(),
                        label = BAND_LABELS[i],
                        enabled = state.eqEnabled,
                        onValueChange = { vm.setEqBand(i, it) }
                    )
                }
            }

            // dB labels at sides
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), Arrangement.SpaceBetween) {
                Text("+15 dB", style = MaterialTheme.typography.labelSmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("0", style = MaterialTheme.typography.labelSmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("-15 dB", style = MaterialTheme.typography.labelSmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(24.dp))

            // --- PREMIUM DSP SELECTION UI ---
            val echoSignatures = listOf("Flat", "Signature", "Acoustic", "Bass Boost", "Pure Clarity", "Soft Bass", "Electronic", "Rock", "Pop", "Jazz", "Voice")
            val dolbyProfiles = listOf("Dolby Open", "Dolby Rich", "Dolby Focused")
            val diracProfiles = listOf("Dirac Music", "Dirac Movie", "Dirac Game")

            val accentColor = MaterialTheme.colorScheme.primary

            Column(modifier = Modifier.fillMaxWidth()) {
                // --- ECHO SIGNATURE ---
                Text(
                    text = "Echo Signature",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.padding(bottom = 12.dp, top = 8.dp)
                )
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    echoSignatures.forEach { signature ->
                        DspChip(
                            label = signature,
                            isSelected = state.selectedEcho == signature,
                            accentColor = accentColor,
                            onClick = { vm.setEchoSignature(signature) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // --- DOLBY ATMOS ---
                Text(
                    text = "Dolby Atmos",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    dolbyProfiles.forEach { profile ->
                        DspChip(
                            label = profile,
                            isSelected = state.selectedDolby == profile,
                            accentColor = accentColor,
                            onClick = { vm.setDolbyProfile(profile) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // --- DIRAC AUDIO ---
                Text(
                    text = "Dirac Audio",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    diracProfiles.forEach { profile ->
                        DspChip(
                            label = profile,
                            isSelected = state.selectedDirac == profile,
                            accentColor = accentColor,
                            onClick = { vm.setDiracProfile(profile) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
fun PresetHeader(vm: PlayerViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }
    
    // 🔥 THE MENU STATE
    var expanded by remember { mutableStateOf(false) }

    // 1. Storage Launcher: EXPORT (Save to phone)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val currentBands = mutableMapOf<Int, Int>()
            state.eqBands.forEachIndexed { i, level -> currentBands[i] = level }
            val preset = com.mediaplayer.data.model.AudioPreset(
                name = "Exported Preset",
                bandLevels = currentBands
            )
            vm.exportPresetToUri(preset, uri)
        }
    }

    // 2. Storage Launcher: IMPORT (Load from phone)
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            vm.importPresetFromUri(uri)
        }
    }

    // 🔥 THE UI LAYOUT
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Presets", 
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        // 🔥 THE DROPDOWN MENU WRAPPER
        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Preset Options")
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                // 1. IMPORT OPTION
                DropdownMenuItem(
                    text = { Text("Import") },
                    leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                    onClick = {
                        expanded = false
                        importLauncher.launch(arrayOf("application/json"))
                    }
                )
                
                // 2. EXPORT OPTION
                DropdownMenuItem(
                    text = { Text("Export") },
                    leadingIcon = { Icon(Icons.Filled.Upload, contentDescription = null) },
                    onClick = {
                        expanded = false
                        // Suggest a filename based on the current track or generic
                        exportLauncher.launch("my_preset.json")
                    }
                )
                
                // 3. SAVE CUSTOM OPTION
                DropdownMenuItem(
                    text = { Text("Save custom preset") },
                    leadingIcon = { Icon(Icons.Filled.Save, contentDescription = null) },
                    onClick = {
                        expanded = false
                        showAddDialog = true
                    }
                )
            }
        }
    }

    // 🔥 THE NAMING DIALOG (Triggered by the + Button)
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Save Custom Preset") },
            text = {
                OutlinedTextField(
                    value = newPresetName,
                    onValueChange = { newPresetName = it },
                    label = { Text("Preset Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPresetName.isNotBlank()) {
                            vm.saveCurrentConfigurationAsPreset(newPresetName)
                            showAddDialog = false
                            newPresetName = "" 
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun EqBandSlider(
    band: Int,
    levelMb: Int,
    label: String,
    enabled: Boolean,
    onValueChange: (Int) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(36.dp) // Slightly wider for easier touch
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .width(36.dp),
            contentAlignment = Alignment.Center
        ) {
            // 👇 The fixed Vertical Slider
            Slider(
                value = levelMb.toFloat(),
                onValueChange = { onValueChange(it.toInt()) },
                valueRange = MIN_MB..MAX_MB,
                enabled = enabled,
                modifier = Modifier
                    .graphicsLayer {
                        rotationZ = 270f
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(
                            constraints.copy(
                                minWidth = constraints.maxHeight,
                                maxWidth = constraints.maxHeight,
                                minHeight = constraints.maxWidth,
                                maxHeight = constraints.maxWidth
                            )
                        )
                        layout(placeable.height, placeable.width) {
                            placeable.place(-placeable.width, 0)
                        }
                    }
                    .width(180.dp)
                    .height(36.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val dbValue = (levelMb / 100f)
        Text(
            text = "${if (dbValue >= 0) "+" else ""}${String.format("%.1f", dbValue)}",
            style = MaterialTheme.typography.labelSmall,
            color = if (levelMb > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun DspChip(
    label: String,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val textColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
    val fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = fontWeight
        )
    }
}

@Composable
private fun EffectRow(
    title: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            content()
        }
    }
}
