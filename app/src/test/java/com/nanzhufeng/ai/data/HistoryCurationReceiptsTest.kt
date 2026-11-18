package com.nanzhufeng.ai.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.domain.ConversationSurface
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class HistoryCurationReceiptsTest {
    @Test fun `receipts remain bounded and readable across owner recreation`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.getExternalFilesDir("diagnostics"), "history-curation-chat-v1.json")
        file.delete()
        repeat(70) { index ->
            AndroidHistoryCurationReceipts(context, ConversationSurface.CHAT).append(JSONObject().put("phase", "FINISHED").put("sequence", index))
        }
        val values = JSONArray(file.readText())
        assertEquals(64, values.length())
        assertEquals(6, values.getJSONObject(0).getInt("sequence"))
        assertEquals(69, values.getJSONObject(63).getInt("sequence"))
        assertFalse(File(file.path + ".new").exists())
    }
}
