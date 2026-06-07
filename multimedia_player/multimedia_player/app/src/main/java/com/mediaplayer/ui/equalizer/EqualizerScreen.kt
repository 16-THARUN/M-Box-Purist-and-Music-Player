package com.mediaplayer.ui.equalizer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
    val state by vm.state.collectAsState()

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
            // ── Preset chips ──────────────────────────────────
            Text("Presets", style = MaterialTheme.typography.labelLarge,
                 modifier = Modifier.padding(vertical = 8.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(EqPreset.values().toList()) { preset ->
                    FilterChip(
                        selected = state.activePreset == preset,
                        onClick = { vm.applyEqPreset(preset) },
                        label = { Text(preset.label) }
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
                state.eqBands.forEachIndexed { i, level ->
                    EqBandSlider(
                        band = i,
                        levelMb = level,
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

            // ── Bass boost ────────────────────────────────────
            EffectRow(
                title = "Bass boost",
                enabled = state.bassBoostOn,
                onToggle = vm::setBassBoostEnabled
            ) {
                Slider(
                    value = state.bassBoostStrength.toFloat(),
                    onValueChange = { vm.setBassBoostStrength(it.toInt()) },
                    valueRange = 0f..1000f,
                    enabled = state.bassBoostOn,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── Virtualizer ───────────────────────────────────
            EffectRow(
                title = "Surround / Virtualizer",
                enabled = state.virtualizerOn,
                onToggle = vm::setVirtualizerEnabled
            ) {
                Slider(
                    value = state.virtualizerStrength.toFloat(),
                    onValueChange = { vm.setVirtualizerStrength(it.toInt()) },
                    valueRange = 0f..1000f,
                    enabled = state.virtualizerOn,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── Loudness Enhancer ─────────────────────────────
            EffectRow(
                title = "Loudness Enhancer",
                enabled = state.loudnessOn,
                onToggle = vm::setLoudnessEnabled
            ) {
                Slider(
                    value = state.loudnessGainMb.toFloat(),
                    onValueChange = { vm.setLoudnessGain(it.toInt()) },
                    valueRange = 0f..2000f,
                    enabled = state.loudnessOn,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Gain: ${String.format("%.1f", state.loudnessGainMb / 100f)} dB",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(32.dp))
        }
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
