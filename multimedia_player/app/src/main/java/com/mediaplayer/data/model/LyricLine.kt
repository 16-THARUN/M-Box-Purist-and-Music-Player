package com.mediaplayer.data.model

data class LyricLine(
    val startTimeMs: Long,
    val text: String
)

object LyricsParser {
    private val wordTagRegex = Regex("""<(\d{2,}):(\d{2}(?:\.\d{1,3})?)>""")
    private val lrcRegex = Regex("""\[(\d{2,}):(\d{2}(?:\.\d{1,3})?)\](.*)""")

    fun parseLrc(rawLrcData: String): List<LyricLine> {
        val lines = rawLrcData.lines()
        val parsedLines = lines.mapNotNull { line ->
            val match = lrcRegex.find(line)
            if (match != null) {
                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toFloatOrNull() ?: 0f
                val rawContent = match.groupValues[3]
                val cleanText = rawContent.replace(wordTagRegex, "").trim()

                val timeMs = (minutes * 60 * 1000) + (seconds * 1000).toLong()
                if (cleanText.isNotBlank()) LyricLine(timeMs, cleanText) else null
            } else null
        }.sortedBy { it.startTimeMs }

        if (parsedLines.isEmpty() && rawLrcData.isNotBlank()) {
            return lines.filter { it.isNotBlank() }.map { LyricLine(startTimeMs = 0L, text = it.trim()) }
        }

        return parsedLines
    }
}
