package com.nanzhufeng.ai.domain

import java.security.MessageDigest
import java.time.Clock

/** A user-selected, immutable text snapshot. No source repository or attachment is shared. */
object CrossAreaMessageReference {
    fun copyToNewDraft(source: ConversationSnapshot, messageId: MessageNodeId, target: ConversationSurface,
                       tree: ConversationTreeService, clock: Clock): ConversationSnapshot {
        require(source.conversation.surface != target) { "引用目标必须是另一区域。" }
        require(source.conversation.deletedAt == null) { "源会话已删除。" }
        val message = source.nodes.singleOrNull { it.id == messageId } ?: error("所选消息已不可用。")
        val text = message.content.filterIsInstance<ContentBlock.Text>().joinToString("\n") { it.text }
        require(text.isNotBlank() && text.length <= 100_000) { "所选消息没有可引用文字，或超出单次引用上限。" }
        val hash = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        val areaLabel = if (source.conversation.surface == ConversationSurface.WORK) "工作区" else "对话区"
        val reference = buildString {
            append("引用自$areaLabel：${source.conversation.title}\n\n")
            append(text.lineSequence().joinToString("\n") { "> $it" })
            append("\n\n来源：${source.conversation.surface.name}/${source.conversation.id.value}/${message.id.value}；版本 SHA-256 $hash\n")
        }
        require(reference.length <= 120_000) { "引用文字超过草稿上限，未改动原记录。" }
        return tree.create(ConversationAutoTitle.NEW_CONVERSATION_TITLE, autoTitlePending = true, surface = target)
            .copy(draft = ConversationDraft(text = reference, updatedAt = clock.instant()))
    }
}
