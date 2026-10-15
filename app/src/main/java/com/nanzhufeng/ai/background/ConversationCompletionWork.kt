package com.nanzhufeng.ai.background

import android.content.Context
import android.content.Intent
import com.nanzhufeng.ai.domain.ConversationCompletionWorkQueue
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.ConversationSurface
import java.util.concurrent.Executors

/** Process-local secondary work must outlive the reply foreground service, without keeping its spinner. */
object ConversationCompletionWork {
    const val ACTION_METADATA_CHANGED = "com.nanzhufeng.ai.action.CONVERSATION_COMPLETION_METADATA_CHANGED"
    private val queue = ConversationCompletionWorkQueue(Executors.newFixedThreadPool(2) { task ->
        Thread(task, "conversation-completion").apply { isDaemon = true }
    })

    fun enqueue(context: Context, area: ConversationSurface, id: ConversationId, work: () -> Unit) {
        val app = context.applicationContext
        queue.enqueue("${area.name}:${id.value}") {
            try { work() } finally {
                app.sendBroadcast(Intent(ACTION_METADATA_CHANGED).setPackage(app.packageName)
                    .putExtra("dataArea", area.name)
                    .putExtra(NormalChatGenerationForegroundService.EXTRA_CONVERSATION_ID, id.value))
            }
        }
    }
}
