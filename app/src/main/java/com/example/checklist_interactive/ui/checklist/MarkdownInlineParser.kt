package com.example.checklist_interactive.ui.checklist

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * Very small inline markdown parser for bold (**text**), italic (*text*), and inline code (`text`).
 * Returns an AnnotatedString with appropriate span styles.
 */
fun parseInlineMarkdown(text: String, bodyFontSize: Int): AnnotatedString {
    val input = text
    return buildAnnotatedString {
        var pos = 0
        val length = input.length

        while (pos < length) {
            // Find earliest match among code, bold, italic
            val codeMatch = Regex("`([^`]+?)`").find(input, pos)
            val boldMatch = Regex("\\*\\*([^*]+?)\\*\\*").find(input, pos)
            val italicMatch = Regex("\\*([^*]+?)\\*").find(input, pos)

            // Choose the earliest starting match
            val next = listOfNotNull(codeMatch, boldMatch, italicMatch).minWithOrNull(
                compareBy<MatchResult> { it.range.first }
                    .thenByDescending { it.range.last - it.range.first }
            )

            if (next == null) {
                append(input.substring(pos))
                break
            }

            if (next.range.first > pos) {
                append(input.substring(pos, next.range.first))
            }

            val content = next.groupValues[1]
            when (next) {
                codeMatch -> {
                    pushStyle(SpanStyle(fontFamily = FontFamily.Monospace))
                    append(content)
                    pop()
                }
                boldMatch -> {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(content)
                    pop()
                }
                italicMatch -> {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(content)
                    pop()
                }
                else -> append(content)
            }

            pos = next.range.last + 1
        }
    }
}
