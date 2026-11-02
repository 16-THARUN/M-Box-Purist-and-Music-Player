package com.mediaplayer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mediaplayer.data.model.mboxVisualizerPresets
import com.mediaplayer.data.model.PlayerController

import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualizationModeSheet(
    onDismiss: () -> Unit,
    onSettingsClick: () -> Unit,
    controller: PlayerController? = null,
    activeModeId: String = "circular_rings"
) {
    val selectedPresetId = activeModeId
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E), // Dark theme surface matching the screenshot
        dragHandle = null, // Removing default handle to match the custom header
        modifier = if (isLandscape) {
            Modifier
                .fillMaxHeight(0.60f)
                .widthIn(max = 480.dp)
        } else {
            Modifier.fillMaxHeight(0.90f)
        }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // 🔥 THE STICKY HEADER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Search",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                
                // 3D Cube Icon
                IconButton(onClick = { /* TODO */ }) {
                    Icon(Icons.Filled.ViewInAr, contentDescription = "3D View", tint = Color.White)
                }
                
                // 🔥 THE FIX: Route the click properly
                IconButton(onClick = { 
                    onDismiss() // Close the sheet first so it doesn't stay open in the background
                    onSettingsClick() // Trigger the safe navigation
                }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
                }
                
                // Close Icon
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
            }
            
            // 🔥 THE PRESET LIST
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(mboxVisualizerPresets) { preset ->
                    PresetListItem(
                        preset = preset,
                        isSelected = preset.id == selectedPresetId,
                        onClick = {
                            controller?.setVisualizerMode(preset.id)
                            onDismiss() // 🔥 THE FIX: Close the bottom sheet after selection
                        },
                        onLikeClick = {
                            // viewModel.toggleLike(preset.id)
                        }
                    )
                }
            }
        }
    }
}
