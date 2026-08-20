package com.nanzhufeng.ai.domain

import java.net.URI

/**
 * A deterministic, local-only title for a freshly created conversation.  It deliberately never
 * calls a model or a Provider: the title is derived in the same local transaction as the first
 * user message, so there is no delayed rename or title that cannot be read back after restart.
 */
object ConversationAutoTitle {
    const val NEW_CONVERSATION_TITLE = "新对话"

    fun titleForFirstMessage(snapshot: ConversationSnapshot, draft: ConversationDraft): String? {
        if (!snapshot.conversation.autoTitlePending || snapshot.nodes.isNotEmpty()) return null
        return draft.text.takeIf(String::isNotBlank)?.let(::fromText) ?: fromAttachments(draft.attachments)
    }

    fun fromText(raw: String): String {
        val normalized = raw
            .replace(Regex("```[\\s\\S]*?```"), " ")
            .lineSequence()
            .map(::stripMarkdownLine)
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
        linkOnlyTitle(normalized)?.let { return it }
        val sentence = normalized
            .split(Regex("[。！？!?；;]+"))
            .asSequence()
            .map(::removeRequestPrefix)
            .map(String::trim)
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
        return trimToDisplayBudget(sentence.ifBlank { "新对话" })
    }

    private fun stripMarkdownLine(line: String): String = line
        .trim()
        .replace(Regex("^(?:#{1,6}|>|[-*+]|\\d+[.)])\\s*"), "")
        .replace(Regex("!\\[[^]]*]\\([^)]*\\)"), "")
        .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
        .replace("**", "")
        .replace("__", "")
        .replace("`", "")

    private fun removeRequestPrefix(value: String): String = value
        .replace(Regex("^(?:需求[:：]\\s*)?(?:请(?:帮我)?|帮我|麻烦(?:你)?|能否|可以|我想(?:要)?|我需要|想请你)\\s*"), "")
        .replace(Regex("^把\\s*"), "")
        .replace(Regex("一下(?:，.*)?$"), "")

    private fun linkOnlyTitle(value: String): String? {
        val match = Regex("^https?://[^\\s]+$").matchEntire(value) ?: return null
        val host = runCatching { URI(match.value).host }.getOrNull()?.removePrefix("www.") ?: return "分享链接"
        return trimToDisplayBudget("链接：$host")
    }

    private fun fromAttachments(attachments: List<ConversationAttachmentReference>): String? = attachments.firstOrNull()?.mimeType?.let { mime ->
        when {
            mime.startsWith("image/") -> "图片对话"
            mime.startsWith("video/") -> "视频对话"
            mime.startsWith("audio/") -> "音频对话"
            else -> "文件对话"
        }
    }

    private fun trimToDisplayBudget(value: String): String {
        var budget = 0
        val builder = StringBuilder()
        for (codePoint in value.codePoints().toArray()) {
            val width = if (codePoint in 0x2E80..0x9FFF || codePoint in 0xF900..0xFAFF) 2 else 1
            if (budget + width > MAX_DISPLAY_WIDTH) return builder.append('…').toString()
            builder.append(String(Character.toChars(codePoint)))
            budget += width
        }
        return builder.toString()
    }

    private const val MAX_DISPLAY_WIDTH = 32
}
