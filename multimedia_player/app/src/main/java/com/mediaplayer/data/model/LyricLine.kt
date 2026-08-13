package com.mediaplayer.data.model

data class LyricLine(
    val startTimeMs: Long,
    val text: String
)

object LyricsParser {
    // 🔥 THE FIX: More forgiving Regex. 
    // Supports [01:22], [01:22.4], [01:22.45], and [100:22.456]
    private val lrcRegex = Regex("""\[(\d{2,}):(\d{2}(?:\.\d{1,3})?)\](.*)""")

    fun parseLrc(rawLrcData: String): List<LyricLine> {
        val lines = rawLrcData.lines()
        val parsedLines = lines.mapNotNull { line ->
            val match = lrcRegex.find(line)
            if (match != null) {
                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toFloatOrNull() ?: 0f
                val text = match.groupValues[3].trim()

                val timeMs = (minutes * 60 * 1000) + (seconds * 1000).toLong()
                LyricLine(timeMs, text)
            } else null
        }.sortedBy { it.startTimeMs }

        // 🔥 THE PLAIN TEXT FALLBACK: 
        // If the regex found no timestamps, but we have text, don't throw it away!
        // Return it as a static list.
        if (parsedLines.isEmpty() && rawLrcData.isNotBlank()) {
            return lines.filter { it.isNotBlank() }.map { LyricLine(startTimeMs = 0L, text = it.trim()) }
        }

        return parsedLines
    }
}
