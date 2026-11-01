package com.mediaplayer.util

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette

fun extractDarkDominantColor(bitmap: Bitmap, defaultColor: Color): Color {
    // Generate the palette from the album art
    val palette = Palette.from(bitmap).generate()
    
    // Try to get a Dark Vibrant color first (best for dark mode)
    // Fallback to Dark Muted, then Dominant, and finally your default color
    val swatch = palette.darkVibrantSwatch 
        ?: palette.darkMutedSwatch 
        ?: palette.dominantSwatch

    return swatch?.rgb?.let { Color(it) } ?: defaultColor
}
