package com.nanzhufeng.ai.domain

/** Presentation metadata only. Neither message content nor credentials leave the execution owner. */
data class GenerationIslandTask(
    val key: String,
    val area: ConversationSurface,
    val conversationId: ConversationId,
    val startedAt: Long,
    val savedCharacters: Int = 0,
    val modelLabel: String = "模型连接中",
    val updatedAt: Long = startedAt,
    val outcome: String? = null,
) {
    val running get() = outcome == null
    val stage get() = when (outcome) {
        "COMPLETE" -> "回复已完成"
        "CANCELLED" -> "已停止生成"
        null -> if (savedCharacters > 0) "正在生成" else "等待模型输出"
        else -> "生成中断"
    }
    fun detail(now: Long): String = "已保存 $savedCharacters 字 · ${( (if (running) now else updatedAt) - startedAt).coerceAtLeast(0) / 1000} 秒" +
        if (outcome != null && outcome !in setOf("COMPLETE", "CANCELLED")) "\n返回对话查看原因，保留断点后手动续写。" else ""
}

/** A terminal task cannot be revived by a delayed checkpoint. Explicit begin starts a new run. */
class GenerationIslandLedger {
    private val tasks = linkedMapOf<String, GenerationIslandTask>()
    @Synchronized fun begin(area: ConversationSurface, id: ConversationId, now: Long) {
        val key = "${area.name}:${id.value}"
        if (tasks[key]?.running == true) return
        tasks[key] = GenerationIslandTask(key, area, id, now)
    }
    @Synchronized fun checkpoint(area: ConversationSurface, id: ConversationId, characters: Int, now: Long, model: String = "") {
        val key = "${area.name}:${id.value}"
        val old = tasks[key]?.takeIf { it.running } ?: return
        tasks[key] = old.copy(savedCharacters = characters.coerceAtLeast(old.savedCharacters), updatedAt = now, modelLabel = model.ifBlank { old.modelLabel })
    }
    @Synchronized fun finish(area: ConversationSurface, id: ConversationId, outcome: String, now: Long, finalCharacters: Int? = null) {
        val key = "${area.name}:${id.value}"
        val old = tasks[key]?.takeIf { it.running } ?: return
        tasks[key] = old.copy(outcome = outcome, updatedAt = now, savedCharacters = finalCharacters?.coerceAtLeast(0) ?: old.savedCharacters)
        // Finished presentation is bounded; running tasks never disappear to make room.
        tasks.values.filterNot { it.running }.dropLast(8).forEach { tasks.remove(it.key) }
    }
    @Synchronized fun snapshot() = tasks.values.toList()
}

/** Background presentation countdowns; interruption never pauses or renews on expansion. */
class GenerationIslandPresentation {
    var expanded = false; private set
    private var tasks = emptyList<GenerationIslandTask>()
    private val deadlines = mutableMapOf<String,Long>()
    private val dismissed = mutableSetOf<String>()
    private var expandedKeys = emptySet<String>()
    private fun identity(task: GenerationIslandTask) = "${task.key}:${task.startedAt}"
    fun update(next: List<GenerationIslandTask>,now: Long) {
        if (expanded && next.none { identity(it) in expandedKeys }) { expanded=false;expandedKeys=emptySet() }
        tasks=next
        next.filterNot {it.running}.forEach {
            val deadline=if(it.outcome=="COMPLETE") {if(now-it.updatedAt<8000) now+2600L else 0L} else minOf(now+3000L,it.updatedAt+3000L)
            deadlines.putIfAbsent(identity(it),deadline)
        }
        val ids=next.mapTo(mutableSetOf(),::identity)
        deadlines.keys.retainAll(ids);dismissed.retainAll(ids)
    }
    fun visible(now: Long):List<GenerationIslandTask> {
        val visible=tasks.filter {identity(it) !in dismissed && (it.running || (it.outcome=="COMPLETE" && expanded && identity(it) in expandedKeys) || now<(deadlines[identity(it)] ?: 0))}
        if(visible.isEmpty()) {expanded=false;expandedKeys=emptySet()}
        return visible
    }
    fun toggle(now:Long) {
        val opening=!expanded
        val shown=visible(now)
        if(opening) expandedKeys=shown.mapTo(mutableSetOf(),::identity)
        else shown.filter {it.outcome=="COMPLETE"}.forEach {deadlines[identity(it)]=now+2600L}
        expanded=opening && shown.isNotEmpty()
    }
    fun dismiss() {visible(Long.MIN_VALUE).filterNot {it.running}.forEach {dismissed.add(identity(it))};expanded=false}
    fun collapse(now:Long) {if(expanded) toggle(now)}
}
