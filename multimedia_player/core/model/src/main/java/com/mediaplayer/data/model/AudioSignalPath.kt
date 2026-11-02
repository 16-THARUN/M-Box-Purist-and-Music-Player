package com.mediaplayer.data.model

data class AudioSignalPath(
    val format: String,          // e.g., "FLAC"
    val sourceInfo: String,      // e.g., "16 bit 44.1 kHz"
    val decoder: String,         // e.g., "ExoPlayer (C2.android.flac.decoder)"
    val hardwareInfo: String,    // e.g., "48 kHz | 256 frames"
    val outputApi: String,       // e.g., "AudioTrack (Java)"
    val latency: Int,            // e.g., 120 ms
    val bitDepth: String,        // e.g., "16-bit PCM" or "32-bit Float"
    
    // Detailed fields for Audiophile Bottom Sheet
    val channels: String = "Stereo",
    val gapless: Boolean = true,
    val resamplerQuality: String = "High (SINC)",
    val resamplerCutoff: String = "95%",
    val dspBitDepth: String = "32-bit Float",
    val eqMode: String = "Perfect Speakers",
    val toneControl: String = "Bass: +2dB, Treble: 0dB",
    val preamp: String = "-3.0 dB",
    val outputFlags: String = "DIRECT, RAW",
    val deviceType: String = "Wired Headphones"
)
