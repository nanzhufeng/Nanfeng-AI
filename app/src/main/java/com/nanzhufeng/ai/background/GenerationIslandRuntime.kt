package com.nanzhufeng.ai.background

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.nanzhufeng.ai.CONVERSATION_SHORTCUT_ID_EXTRA
import com.nanzhufeng.ai.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Shared metadata projection of the existing Service owner, never an execution scheduler. */
object GenerationIslandRuntime {
    private val ledger = GenerationIslandLedger()
    private val mutableTasks = MutableStateFlow<List<GenerationIslandTask>>(emptyList())
    val tasks = mutableTasks.asStateFlow()
    private val visibleActivities = java.util.concurrent.ConcurrentHashMap.newKeySet<Any>()
    val foreground get() = visibleActivities.isNotEmpty()
    private var overlay: GenerationIslandOverlay? = null
    private var ticker: Job? = null
    fun visibility(owner: Any, visible: Boolean) { if (visible) visibleActivities.add(owner) else visibleActivities.remove(owner); if (foreground) overlay?.hide() }
    @Synchronized fun begin(context: Context, area: ConversationSurface, id: ConversationId) {
        ledger.begin(area,id,SystemClock.elapsedRealtime()); publish()
        if (overlay == null) overlay = GenerationIslandOverlay(context.applicationContext)
        if (ticker?.isActive != true) ticker = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            while (isActive) {
                val now=SystemClock.elapsedRealtime()
                val all=tasks.value
                overlay?.update(all,now)
                if (all.none { it.running || now-it.updatedAt < 8_000 } && overlay?.expanded != true) break
                delay(1000)
            }
            overlay?.hide()
        }
    }
    fun checkpoint(area: ConversationSurface,id: ConversationId,characters: Int,model: String, now: Long = SystemClock.elapsedRealtime()) {
        ledger.checkpoint(area,id,characters,now,model);publish()
    }
    fun finish(area: ConversationSurface,id: ConversationId,outcome: String, finalCharacters: Int? = null) {
        ledger.finish(area,id,outcome,SystemClock.elapsedRealtime(),finalCharacters);publish()
    }
    private fun publish() { mutableTasks.value=ledger.snapshot() }
    fun open(context: Context, task: GenerationIslandTask) {
        val target = if (task.area == ConversationSurface.WORK) com.nanzhufeng.ai.NanfengWorkActivity::class.java else com.nanzhufeng.ai.NanfengAiActivity::class.java
        context.startActivity(Intent(context,target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(CONVERSATION_SHORTCUT_ID_EXTRA,task.conversationId.value))
    }
    fun stop(context: Context,task: GenerationIslandTask) {
        AndroidNormalChatBackgroundExecution(context,task.area).cancel(task.conversationId)
    }
}
