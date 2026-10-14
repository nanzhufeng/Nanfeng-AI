package com.nanzhufeng.ai.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.domain.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LastComposerModelPersistenceTest {
    private lateinit var context: Context
    @Before fun reset() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("p6g-model-selection-v1", Context.MODE_PRIVATE).edit().clear().commit()
    }
    private fun owner() = P6GModelSelectionOwner(AndroidP6GModelSelectionStore(context), P6GModelRouter())
    @Test fun `last selected model and explicit Auto survive owner and storage recreation`() {
        val selected = ComposerModelRoutingCatalog.deep.first().id
        assertTrue(owner().setComposerModelSelection(ConversationId("old"), selected, 0, 0) is P6GSelectionMutationResult.Applied)
        owner().initializeConversationModel(ConversationId("new"))
        assertEquals(selected, owner().readConversationOverride(ConversationId("new")).modelId)
        assertTrue(owner().setComposerModelSelection(ConversationId("other"), null, 0, 1) is P6GSelectionMutationResult.Applied)
        owner().initializeConversationModel(ConversationId("auto"))
        assertNull(owner().readConversationOverride(ConversationId("auto")).modelId)
        assertEquals(1L, owner().readConversationOverride(ConversationId("auto")).revision)
        assertEquals(selected, owner().readConversationOverride(ConversationId("new")).modelId)
    }
}
