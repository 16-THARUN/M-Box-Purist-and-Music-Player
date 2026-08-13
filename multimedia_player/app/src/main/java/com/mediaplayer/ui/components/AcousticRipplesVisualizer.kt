package com.mediaplayer.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.abs
import kotlin.math.hypot

@Composable
fun AcousticRipplesVisualizer(
    fftData: ByteArray,
    accentColor: Color,
    vinylScale: Float,
    modifier: Modifier = Modifier
) {
    var rawMagnitude by remember { mutableFloatStateOf(0f) }
    
    LaunchedEffect(fftData) {
        if (fftData.size > 2) {
            var sum = 0f
            // 🔥 THE FIX: True FFT Pythagorean math
            for (i in 2 until fftData.size step 2) {
                val real = fftData[i].toFloat()
                val imaginary = fftData[i + 1].toFloat()
                sum += hypot(real, imaginary)
            }
            rawMagnitude = sum / (fftData.size / 2f)
        } else {
            rawMagnitude = 0f
        }
    }
    
    val smoothMagnitude by animateFloatAsState(
        targetValue = rawMagnitude, 
        animationSpec = tween(150),
        label = "RippleMagnitude"
    )
    
    Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        
        // 🔥 THE FIX: Escape the shadow by adding 30 pixels
        val vinylRadius = ((size.width * vinylScale) / 2f) + 30f 
        
        val maxRipples = 4
        
        for (i in 1..maxRipples) {
            // 🔥 MASSIVE MULTIPLIER (x15)
            val rippleRadius = vinylRadius + (smoothMagnitude * i * 15f) 
            
            val alpha = (1f - (i / (maxRipples + 1f))).coerceIn(0f, 1f)
            
            drawCircle(
                color = accentColor.copy(alpha = alpha * 0.6f),
                radius = rippleRadius,
                center = center,
                style = Stroke(width = 6f) 
            )
        }
    }
}
