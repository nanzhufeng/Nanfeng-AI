package com.nanzhufeng.ai.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.domain.AssistantExperienceSettings
import com.nanzhufeng.ai.domain.ConversationStyle
import com.nanzhufeng.ai.domain.definition
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidAssistantExperienceSettingsRepositoryContractsTest {
    private lateinit var context: Context

    @Before
    fun clearSettings() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `selected conversation style survives repository recreation`() {
        AndroidAssistantExperienceSettingsRepository(context).save(
            AssistantExperienceSettings(conversationStyle = ConversationStyle.PROFESSIONAL),
        )

        val restarted = AndroidAssistantExperienceSettingsRepository(context).load()

        assertEquals(ConversationStyle.PROFESSIONAL, restarted.conversationStyle)
        assertEquals("专业可靠", restarted.conversationStyle.definition().label)
    }

    @Test
    fun `unknown legacy style resolves to the real default`() {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("conversation_style", "retired-style")
            .commit()

        val restored = AndroidAssistantExperienceSettingsRepository(context).load()

        assertEquals(ConversationStyle.DEFAULT, restored.conversationStyle)
        assertEquals(ConversationStyle.DEFAULT, restored.conversationStyle.effective())
    }

    private companion object {
        const val FILE = "assistant_experience_settings_v1"
    }
}
