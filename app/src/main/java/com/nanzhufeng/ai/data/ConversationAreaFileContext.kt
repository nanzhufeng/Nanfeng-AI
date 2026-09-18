package com.nanzhufeng.ai.data

import android.content.Context
import android.content.ContextWrapper
import com.nanzhufeng.ai.domain.ConversationSurface
import com.nanzhufeng.ai.domain.ConversationDataArea
import java.io.File

/** Only private business-file owners receive this wrapper. Framework and SDK initialization
 * continues to receive the real application context. Account/provider credentials stay global. */
class ConversationAreaFileContext(base: Context, val area: ConversationSurface) : ContextWrapper(base) {
    override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences =
        super.getSharedPreferences(if (area == ConversationSurface.CHAT) name else "$name-WORK", mode)
    override fun getApplicationContext(): Context = this
    override fun getFilesDir(): File = if (area == ConversationSurface.CHAT) super.getFilesDir()
        else File(super.getFilesDir(), "work-area").also { check(it.isDirectory || it.mkdirs()) }
    override fun getCacheDir(): File = if (area == ConversationSurface.CHAT) super.getCacheDir()
        else File(super.getCacheDir(), "work-area").also { check(it.isDirectory || it.mkdirs()) }
    override fun getDatabasePath(name: String): File = super.getDatabasePath(
        if (name == "nanfeng-ai.db") ConversationDataArea.databaseName(area) else name,
    )
}
