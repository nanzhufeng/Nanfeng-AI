package com.nanzhufeng.ai.domain

/** Current product scope: multi-model chat. Retained WORK storage stays isolated. */
object ProductScope {
    const val workspacesVisible = false
    fun canOpen(surface: ConversationSurface) = surface == ConversationSurface.CHAT || workspacesVisible
}
