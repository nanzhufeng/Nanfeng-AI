package com.nanzhufeng.ai.ui

/** Returns every non-overlapping visible occurrence so repeated words remain separate targets. */
internal fun conversationFindOccurrenceStarts(value: String, query: String): List<Int> {
    val needle = query.trim()
    if (needle.isEmpty()) return emptyList()
    return buildList {
        var cursor = 0
        while (cursor < value.length) {
            val matchStart = value.indexOf(needle, startIndex = cursor, ignoreCase = true)
            if (matchStart < 0) break
            add(matchStart)
            cursor = matchStart + needle.length
        }
    }
}
