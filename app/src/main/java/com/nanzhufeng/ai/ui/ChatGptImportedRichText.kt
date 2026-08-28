package com.nanzhufeng.ai.ui

private const val CHATGPT_MARKER_OPEN = '\uE200'
private const val CHATGPT_MARKER_PAYLOAD = '\uE202'
private const val CHATGPT_MARKER_CLOSE = '\uE201'

internal data class ChatGptImportedMarker(
    val start: Int,
    val endExclusive: Int,
    val kind: String,
    val itemCount: Int,
) {
    val label: String
        get() {
            val base = when (kind.lowercase()) {
                "cite" -> "来源"
                "filecite" -> "附件来源"
                "i", "image", "image_group" -> "图片内容"
                "navlist" -> "相关链接"
                "url" -> "访问链接"
                "entity" -> "相关实体"
                "product", "products" -> "相关商品"
                "finance" -> "行情信息"
                "weather" -> "天气信息"
                "sports" -> "赛事信息"
                else -> "相关内容"
            }
            return if (itemCount > 1) "$base +${itemCount - 1}" else base
        }

    val inlineContentId: String get() = "chatgpt-imported-marker-${kind.lowercase()}-${itemCount.coerceIn(1, 9)}"
}

private val chatGptImportedMarkerRegex = Regex(
    "$CHATGPT_MARKER_OPEN([A-Za-z][A-Za-z0-9_-]{0,31})$CHATGPT_MARKER_PAYLOAD(.*?)$CHATGPT_MARKER_CLOSE",
    setOf(RegexOption.DOT_MATCHES_ALL),
)
private val chatGptImportedReferenceRegex = Regex("turn\\d+[A-Za-z_-]*\\d+")
private val chatGptAutomationLabelRegex = Regex("\\\"label\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"")

internal fun chatGptImportedMarkers(value: String): List<ChatGptImportedMarker> =
    chatGptImportedMarkerRegex.findAll(value).map { match ->
        val payload = match.groupValues[2]
        ChatGptImportedMarker(
            start = match.range.first,
            endExclusive = match.range.last + 1,
            kind = match.groupValues[1],
            itemCount = chatGptImportedReferenceRegex.findAll(payload).count().coerceIn(1, 9),
        )
    }.toList()

internal fun chatGptImportedAutomationLabel(value: String): String? {
    val marker = chatGptImportedMarkerRegex.matchEntire(value.trim()) ?: return null
    if (!marker.groupValues[1].equals("genui", ignoreCase = true)) return null
    if (!marker.groupValues[2].contains("\"suggest_automation\"")) return null
    val encodedLabel = chatGptAutomationLabelRegex.find(marker.groupValues[2])?.groupValues?.get(1) ?: return null
    return decodeJsonString(encodedLabel).trim().takeIf { it.isNotEmpty() }?.take(160)
}

private fun decodeJsonString(value: String): String = buildString(value.length) {
    var index = 0
    while (index < value.length) {
        val current = value[index++]
        if (current != '\\' || index >= value.length) {
            append(current)
            continue
        }
        when (val escaped = value[index++]) {
            '\"', '\\', '/' -> append(escaped)
            'b' -> append('\b')
            'f' -> append('\u000C')
            'n' -> append('\n')
            'r' -> append('\r')
            't' -> append('\t')
            'u' -> {
                val end = (index + 4).coerceAtMost(value.length)
                val codePoint = value.substring(index, end).takeIf { it.length == 4 }?.toIntOrNull(16)
                if (codePoint != null) {
                    append(codePoint.toChar())
                    index = end
                } else {
                    append('u')
                }
            }
            else -> append(escaped)
        }
    }
}
