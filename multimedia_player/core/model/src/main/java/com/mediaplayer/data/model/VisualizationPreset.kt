package com.mediaplayer.data.model

data class VisualizationPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val isLiked: Boolean = false
)

// 🔥 THE NEW M-BOX PRESETS
val mboxVisualizerPresets = listOf(
    // 🔋 BATTERY SAVER
    VisualizationPreset("none", "Normal (Off)", "No Visualization"),

    // 💿 CATEGORY 1: THE VINYL ECOSYSTEM
    VisualizationPreset("circular_rings", "Circular Spectrum", "Vinyl Ecosystem", isLiked = true),
    VisualizationPreset("orbiting_stardust", "Orbiting Stardust", "Vinyl Ecosystem"),
    VisualizationPreset("acoustic_ripples", "Acoustic Ripples", "Vinyl Ecosystem"),
    VisualizationPreset("groove_bleed", "Vinyl Groove Bleed", "Vinyl Ecosystem"),

    // 🌌 CATEGORY 2: AMBIENT & IMMERSIVE
    VisualizationPreset("aurora_borealis", "Aurora Borealis", "Immersive Full Screen"),
    VisualizationPreset("oled_edge", "OLED Edge Lighting", "Immersive Full Screen"),
    VisualizationPreset("cinematic_leaks", "Cinematic Light Leaks", "Immersive Full Screen"),

    // 🔥 THE NEW CINEMATIC VIDEO BACKGROUNDS
    VisualizationPreset("video_glitch", "Cinematic Glitch Burn", "Premium Video Background"),
    VisualizationPreset("video_flare", "Anamorphic Lens Flare", "Premium Video Background"),
    VisualizationPreset("video_particles", "Quantum Particle Grid", "Premium Video Background"),

    // 📐 CATEGORY 3: MINIMALIST & GEOMETRIC
    VisualizationPreset("liquid_spline", "Liquid Spline", "Minimalist Waveform"),
    VisualizationPreset("hex_grid", "Hexagonal Audio Grid", "Geometric"),
    VisualizationPreset("glass_bars", "Bottom Glass Bars", "Minimalist Spectrum"),

    // 📻 CATEGORY 4: THE LEGACY COLLECTION
    VisualizationPreset("symmetrical_waves", "Classic Symmetrical", "Legacy Spectrum"),
    VisualizationPreset("particle_swarms", "Spectrum Dots", "Legacy Particles"),
    VisualizationPreset("digital_bars", "Narrow Bars", "Legacy Spectrum"),
    VisualizationPreset("oscilloscope", "Plain Oscilloscope", "Legacy Waveform")
)
