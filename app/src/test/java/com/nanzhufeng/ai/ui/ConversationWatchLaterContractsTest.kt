package com.nanzhufeng.ai.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.AndroidConversationReadMarkerStore
import com.nanzhufeng.ai.domain.ConversationId
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConversationWatchLaterContractsTest {
    @Test
    fun `watch later marker persists and only explicit open clears it`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("conversation_read_markers_v1", Context.MODE_PRIVATE).edit().clear().commit()
        val store = AndroidConversationReadMarkerStore(context)
        val id = ConversationId("watch-later-fixture")

        store.markWatchLater(id, 1234L)
        assertEquals(1234L, AndroidConversationReadMarkerStore(context).watchLaterAtEpochMs(id))
        store.clearWatchLater(id)
        assertNull(AndroidConversationReadMarkerStore(context).watchLaterAtEpochMs(id))
    }

    @Test
    fun `drawer prioritizes watch later in pinned and recent and shows one theme dot`() {
        val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val viewModel = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()

        assertTrue(workspace.contains("val allDrawerConversations = state.conversations.filter { it.currentLeafMessageId != null }"))
        assertTrue(workspace.contains("prioritizeWatchLater(drawerConversations.filter { conversation ->"))
        assertTrue(workspace.contains("if (cloudListVisible) conversation.id.value in cloudPinnedConversationIds else conversation.pinnedAt != null"))
        assertTrue(workspace.contains("val projectConversations = prioritizeWatchLater("))
        assertTrue(workspace.contains("watchLater = conversation.id in watchLaterAtEpochMs"))
        assertTrue(workspace.contains("ConversationMenuAction(Icons.Rounded.Visibility, \"未读\""))
        assertTrue(workspace.contains("if (unread || watchLater)"))
        assertTrue(workspace.contains("if (watchLater) \"未读对话\""))
        val row = workspace.substringAfter("private fun ConversationNavigationRow(")
        assertTrue(row.indexOf("if (conversation.pinnedAt != null)") < row.indexOf("if (unread || watchLater)"))
        assertTrue(row.indexOf("if (unread || watchLater)") < row.indexOf("Text(conversation.title"))
        assertTrue(viewModel.contains("fun clearConversationWatchLater(id:"))
        assertTrue(viewModel.contains("conversationReadMarkerStore.clearWatchLater(id)"))
        assertTrue(viewModel.contains("fun markConversationWatchLater(conversation: Conversation)"))
    }
}
