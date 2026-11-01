package com.mediaplayer.ui.equalizer

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mediaplayer.data.model.EqualizerRepository
import com.mediaplayer.data.model.SoundSignature
import com.mediaplayer.util.PresetImporter
import com.mediaplayer.viewmodel.PlayerViewModel

@Composable
fun EqualizerScreen(
    onBack: () -> Unit,
    vm: PlayerViewModel
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    
    // Wire UI to ViewModel state
    val activeMids = state.mids.toFloat()
    val activeTreble = state.treble.toFloat()
    val activeBass = state.bass.toFloat()
    val selectedName = state.selectedEcho
    val activeDolby = state.selectedDolby
    val activeDirac = state.selectedDirac

    var customImportedProfiles by remember { mutableStateOf<List<SoundSignature>>(emptyList()) }

    // System File Picker for .json
    val jsonFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val parsedList = PresetImporter.parsePresetsJson(context, it)
            if (parsedList.isNotEmpty()) {
                customImportedProfiles = customImportedProfiles + parsedList
                // Automatically activate the first imported preset
                val first = parsedList.first()
                vm.updateOrbitalEq(first.mids.toInt(), first.treble.toInt(), first.bass.toInt())
                vm.setEchoPresetName(first.name)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF14171A))
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        // App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Text(
                    text = "Equalizer",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Radar Visualizer (Animated by single-touch selections)
        TriangularOrbitView(
            targetMids = activeMids,
            targetTreble = activeTreble,
            targetBass = activeBass
        )

        // Section: Headset Profiles (KZ Castor / IEM Customization)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Headset Target (Audiophile)",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            
            IconButton(
                onClick = { jsonFilePickerLauncher.launch("application/json") },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Import JSON Preset",
                    tint = Color(0xFF98B4CC)
                )
            }
        }

        // Scrollable Row containing built-in + imported presets
        val combinedHeadsetList = remember(customImportedProfiles) {
            EqualizerRepository.headsetProfiles + customImportedProfiles
        }

        HeadsetTargetRow(
            items = combinedHeadsetList,
            selectedName = selectedName,
            onSelect = { profile ->
                vm.updateOrbitalEq(profile.mids.toInt(), profile.treble.toInt(), profile.bass.toInt())
                vm.setEchoPresetName(profile.name)
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Section: Dolby Atmos
        SectionHeader("Dolby Atmos")
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EqualizerRepository.dolbyModes.forEach { mode ->
                EqualizerChip(
                    text = mode,
                    isSelected = activeDolby == mode,
                    onClick = { vm.setDolbyMode(mode) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Section: Dirac Audio
        SectionHeader("Dirac Audio")
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EqualizerRepository.diracModes.forEach { mode ->
                EqualizerChip(
                    text = mode,
                    isSelected = activeDirac == mode,
                    onClick = { vm.setDiracMode(if (activeDirac == mode) "" else mode) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(36.dp))
    }
}

@Composable
private fun HeadsetTargetRow(
    items: List<SoundSignature>,
    selectedName: String,
    onSelect: (SoundSignature) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { profile ->
            EqualizerChip(
                text = profile.name,
                isSelected = selectedName == profile.name,
                onClick = { onSelect(profile) },
                modifier = Modifier.defaultMinSize(minWidth = 96.dp)
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = Color.White,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
    )
}



@Composable
fun EqualizerChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isSelected) Color(0xFF98B4CC) else Color(0xFF22262B)
    val textColor = if (isSelected) Color(0xFF14171A) else Color(0xFFB0B5BC)

    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}
