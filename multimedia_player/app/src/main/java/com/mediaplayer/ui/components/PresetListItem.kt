package com.mediaplayer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mediaplayer.data.model.VisualizationPreset

@Composable
fun PresetListItem(
    preset: VisualizationPreset,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLikeClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Leading Waveform Icon
        Icon(
            imageVector = Icons.Filled.GraphicEq,
            contentDescription = "Waveform",
            tint = Color.White
        )
        
        Spacer(modifier = Modifier.width(20.dp))
        
        // Text Column
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = preset.title,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
            Text(
                text = preset.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
        
        // Trailing Checkmark (Only if selected)
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Selected",
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(16.dp))
        }
        
        // Trailing Thumb Up
        IconButton(
            onClick = onLikeClick,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.ThumbUp,
                contentDescription = "Like",
                tint = Color.White
            )
        }
    }
}
