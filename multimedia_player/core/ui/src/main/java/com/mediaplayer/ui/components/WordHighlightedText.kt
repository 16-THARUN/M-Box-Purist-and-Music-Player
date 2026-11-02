package com.mediaplayer.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

@Composable
fun WordHighlightedText(
    lineText: String,
    lineStartMs: Long,
    lineEndMs: Long,
    currentPositionMs: Long,
    isActiveLine: Boolean,
    accentColor: Color
) {
    if (!isActiveLine) {
        // Inactive lines render uniformly muted
        Text(
            text = lineText,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        )
        return
    }

    val words = lineText.split(" ")
    val totalWords = words.size
    val lineDuration = (lineEndMs - lineStartMs).coerceAtLeast(1L)
    val lineProgress = ((currentPositionMs - lineStartMs).toFloat() / lineDuration).coerceIn(0f, 1f)
    
    // Calculate which word index is currently active
    val activeWordIndex = (lineProgress * totalWords).toInt().coerceIn(0, totalWords - 1)

    val annotatedString = buildAnnotatedString {
        words.forEachIndexed { index, word ->
            if (index <= activeWordIndex) {
                // Highlighted active/passed words
                withStyle(
                    style = SpanStyle(
                        color = accentColor,
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append(word)
                }
            } else {
                // Upcoming words in the line
                withStyle(
                    style = SpanStyle(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        fontWeight = FontWeight.Normal
                    )
                ) {
                    append(word)
                }
            }
            if (index < words.size - 1) append(" ")
        }
    }

    Text(
        text = annotatedString,
        style = MaterialTheme.typography.headlineSmall.copy(lineHeight = 32.sp)
    )
}
