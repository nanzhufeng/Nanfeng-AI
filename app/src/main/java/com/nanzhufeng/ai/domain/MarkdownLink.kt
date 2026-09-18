package com.nanzhufeng.ai.domain

internal data class MarkdownLink(val label: String, val url: String, val end: Int)

/** Balanced, inert Markdown syntax. URL activation remains owned by the UI allow-list. */
internal fun markdownLinkAt(text: String, start: Int): MarkdownLink? {
    if (text.getOrNull(start) != '[') return null
    fun closing(open: Int, left: Char, right: Char): Int? {
        var depth = 1
        var cursor = open + 1
        while (cursor < text.length) {
            when (text[cursor]) {
                '\\' -> cursor++
                left -> depth++
                right -> { depth--; if (depth == 0) return cursor }
            }
            cursor++
        }
        return null
    }
    val labelEnd = closing(start, '[', ']') ?: return null
    var opening = labelEnd + 1
    while (text.getOrNull(opening)?.isWhitespace() == true) opening++
    if (text.getOrNull(opening) != '(') return null
    val end = closing(opening, '(', ')') ?: return null
    val destination = text.substring(opening + 1, end).trim()
    val match = Regex("^(?:<([^<>\\n]+)>|([^\\s]+?))(?:\\s+\"[^\"]*\")?$").matchEntire(destination) ?: return null
    return MarkdownLink(text.substring(start + 1, labelEnd), match.groupValues[1].ifEmpty { match.groupValues[2] }, end + 1)
}
