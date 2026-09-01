package com.nanzhufeng.ai.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.ConversationStyle
import com.nanzhufeng.ai.domain.ConversationStyleOverride
import com.nanzhufeng.ai.domain.ConversationStyleOverrideOwner
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidConversationStyleOverrideStoreContractsTest {
    private lateinit var context: Context

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun tearDown() {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun `style override persists independently for each conversation after owner recreation`() {
        val first = ConversationId("style-store-first")
        val second = ConversationId("style-store-second")
        val store = AndroidConversationStyleOverrideStore(context)

        assertTrue(store.save(ConversationStyleOverride(first, revision = 1, style = ConversationStyle.DIRECT)))
        assertTrue(store.save(ConversationStyleOverride(second, revision = 3, style = ConversationStyle.EFFICIENT)))

        val recreated = AndroidConversationStyleOverrideStore(context)
        assertEquals(ConversationStyleOverride(first, 1, ConversationStyle.DIRECT), recreated.read(first))
        assertEquals(ConversationStyleOverride(second, 3, ConversationStyle.EFFICIENT), recreated.read(second))
    }

    @Test fun `unknown persisted override inherits the current global style instead of forcing default`() {
        val conversationId = ConversationId("style-store-unknown")
        val prefix = "conversation.${conversationId.value}"
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .putLong("$prefix.revision", 5)
            .putString("$prefix.style", "REMOVED_STYLE")
            .commit()

        val store = AndroidConversationStyleOverrideStore(context)
        val restored = store.read(conversationId)

        assertEquals(5L, restored.revision)
        assertNull(restored.style)
        assertEquals(ConversationStyle.PROFESSIONAL, ConversationStyleOverrideOwner(store).effectiveStyle(conversationId, ConversationStyle.PROFESSIONAL))
    }

    private companion object {
        const val PREFERENCES = "conversation-style-overrides-v1"
    }
}
