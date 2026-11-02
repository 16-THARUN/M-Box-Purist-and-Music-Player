package com.mediaplayer.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class AudioPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    // Maps the Band Index (Int) to the Level in millibels (Int)
    val bandLevels: Map<Int, Int> 
)
