package com.mediaplayer.util

import android.content.Context
import android.net.Uri
import com.mediaplayer.data.model.SoundSignature
import org.json.JSONArray
import org.json.JSONObject

object PresetImporter {
    fun parsePresetsJson(context: Context, fileUri: Uri): List<SoundSignature> {
        val importedList = mutableListOf<SoundSignature>()
        try {
            val jsonString = context.contentResolver.openInputStream(fileUri)?.bufferedReader().use {
                it?.readText()
            } ?: return emptyList()

            val trimmedJson = jsonString.trim()
            if (trimmedJson.startsWith("[")) {
                val jsonArray = JSONArray(trimmedJson)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    importedList.add(parseObject(obj))
                }
            } else if (trimmedJson.startsWith("{")) {
                val obj = JSONObject(trimmedJson)
                importedList.add(parseObject(obj))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return importedList
    }

    private fun parseObject(obj: JSONObject): SoundSignature {
        return SoundSignature(
            name = obj.optString("name", "Custom Preset"),
            mids = obj.optDouble("mids", 0.0).toFloat().coerceIn(-7f, 7f),
            treble = obj.optDouble("treble", 0.0).toFloat().coerceIn(-7f, 7f),
            bass = obj.optDouble("bass", 0.0).toFloat().coerceIn(-7f, 7f),
            subtitle = if (obj.has("subtitle")) obj.getString("subtitle") else null
        )
    }
}
