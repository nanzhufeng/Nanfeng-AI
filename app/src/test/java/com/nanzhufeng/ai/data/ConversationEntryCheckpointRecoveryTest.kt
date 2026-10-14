package com.nanzhufeng.ai.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ConversationEntryCheckpointRecoveryTest {
    @Test fun `generation checkpoint reopens the selected Opus conversation while another chat uses Astra`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val models = AndroidP6GModelSelectionStore(context)
        val old = ConversationId("fixture-astra")
        val current = ConversationId("fixture-opus")
        val opus = "logical:deep:claude-opus"
        val astra = "logical:relaycore:RELAYCORE_GPT_6_ASTRA"
        assertTrue(models.saveComposerModelSelection(P6GConversationOverride(current, 1, opus), P6GGlobalDefault(1, null, opus)))
        // Later explicit selection in another chat changes its own model and the new-chat default.
        assertTrue(models.saveComposerModelSelection(P6GConversationOverride(old, 1, astra), P6GGlobalDefault(2, null, astra)))
        val preferences = context.getSharedPreferences("entry-checkpoint-fixture", Context.MODE_PRIVATE)
        val checkpoint = AndroidConversationEntryCheckpoint(preferences)
        assertTrue(checkpoint.remember(old, false, 1_000))
        assertTrue(checkpoint.remember(current, true, 2_000))
        val reopened = AndroidConversationEntryCheckpoint(preferences)
        assertEquals(current, reopened.retainedId(2_000 + ConversationAppEntryPolicy.RETENTION_MILLIS + 1))
        val restoredModels = AndroidP6GModelSelectionStore(context)
        assertEquals(opus, restoredModels.readConversationOverride(current).modelId)
        assertEquals(astra, restoredModels.readConversationOverride(old).modelId)
        assertEquals(astra, restoredModels.readGlobalDefault().modelId)
        val snapshot = NormalChatRoutingSnapshot(emptyMap(), emptySet(), autoRoutingEnabled = true)
        // Missing/unavailable provider profiles never turn the manual choice into an automatic route.
        assertEquals(listOf(ModelPresetId.CLAUDE_OPUS_5_5), snapshot.presets(ConversationDraft(updatedAt = java.time.Instant.EPOCH), restoredModels.readConversationOverride(current).modelId))
        val disk = File(context.applicationInfo.dataDir, "shared_prefs/entry-checkpoint-fixture.xml").readText()
        assertTrue(disk.contains("fixture-opus"))
        assertFalse(disk.contains("fixture-astra"))
    }
}
