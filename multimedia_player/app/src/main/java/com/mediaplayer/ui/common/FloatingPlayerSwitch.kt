package com.mediaplayer.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun FloatingPlayerSwitch(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onToggle,
        modifier = modifier
            .size(56.dp)
            .shadow(elevation = 12.dp, shape = CircleShape)
            .clip(CircleShape)
            .background(accentColor)
    ) {
        Icon(
            imageVector = Icons.Rounded.OpenInFull,
            contentDescription = if (isExpanded) "Minimize Player" else "Expand Player",
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}
