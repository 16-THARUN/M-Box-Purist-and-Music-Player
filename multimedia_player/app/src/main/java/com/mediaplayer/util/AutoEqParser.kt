package com.mediaplayer.util

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import kotlin.math.abs

data class AutoEqTarget(
    val name: String,
    val gains: FloatArray
)

object AutoEqParser {
    fun parseAutoEqJson(context: Context, uri: Uri): AutoEqTarget? {
        return try {
            // 1. Read the JSON text from the file Uri
            val inputStream = context.contentResolver.openInputStream(uri)
            val jsonString = inputStream?.bufferedReader().use { it?.readText() } ?: return null

            // 2. Parse the JSON Array (AutoEQ format)
            val rootArray = JSONArray(jsonString)
            val profile = rootArray.getJSONObject(0)
            
            // Extract the Profile Name (fallback to "Custom Target" if missing)
            val profileName = profile.optString("name", "Custom Target")
            val bandsArray = profile.getJSONArray("bands")

            // Extract available frequencies and gains
            val parsedBands = mutableMapOf<Float, Float>()
            for (i in 0 until bandsArray.length()) {
                val bandObj = bandsArray.getJSONObject(i)
                val frequency = bandObj.getDouble("frequency").toFloat()
                val gain = bandObj.getDouble("gain").toFloat()
                parsedBands[frequency] = gain
            }

            // 3. Map to our 10-Band targets
            val targetFrequencies = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
            val mappedGains = FloatArray(10)

            // Find the closest parsed frequency for each target band
            for (i in targetFrequencies.indices) {
                val targetFreq = targetFrequencies[i]
                val closestMatch = parsedBands.entries.minByOrNull { abs(it.key - targetFreq) }
                mappedGains[i] = closestMatch?.value ?: 0f
            }

            AutoEqTarget(name = profileName, gains = mappedGains)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
