package com.nanzhufeng.ai.data.local

import com.nanzhufeng.ai.domain.ConversationSurface

/** Physical identity is independent of Room's schema version. */
object ConversationAreaIdentity {
    const val CHAT = 0x4e464143
    const val WORK = 0x4e464157
    fun id(area: ConversationSurface) = if (area == ConversationSurface.WORK) WORK else CHAT
}
