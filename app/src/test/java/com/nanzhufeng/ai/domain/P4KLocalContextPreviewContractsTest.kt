package com.nanzhufeng.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P4KLocalContextPreviewContractsTest {
    private val metadata = ContextSelectionSnapshot(
        conversationId = ConversationId("conversation"), currentBranchId = null,
        projectContext = ProjectInstructionResolution.resolve(null).projectContext,
        messages = emptyList(), sourceBoundaries = emptyList(),
    )

    @Test fun `stable prefix is derived only from explicit stable project metadata in deterministic order`() {
        val snapshot = compression(
            entry(ContextBodyLayer.L2_CONVERSATION, ContextBodySourceKind.CONVERSATION_PATH, "conversation", 1, "conversation hash"),
            entry(ContextBodyLayer.L1_PROJECT, ContextBodySourceKind.PROJECT_INSTRUCTION, "project", 3, "project hash"),
            entry(ContextBodyLayer.L0_STABLE, ContextBodySourceKind.GLOBAL_KNOWLEDGE, "knowledge", 2, "knowledge hash"),
        )
        val prefix = StableContextPrefixMetadataDomain().create(snapshot)

        assertEquals(STABLE_CONTEXT_PREFIX_METADATA_FORMAT, prefix.format)
        assertEquals(listOf("knowledge", "project"), prefix.sources.map { it.sourceId })
        assertFalse(prefix.toString().contains("conversation hash"))
        assertTrue(prefix.fingerprint.matches(Regex("[0-9a-f]{64}")))
    }

    @Test fun `prefix invalidation is metadata-only and identifies source content change`() {
        val domain = StableContextPrefixMetadataDomain()
        val before = domain.create(compression(entry(ContextBodyLayer.L0_STABLE, ContextBodySourceKind.GLOBAL_MEMORY, "memory", 1, "old")))
        val after = domain.create(compression(entry(ContextBodyLayer.L0_STABLE, ContextBodySourceKind.GLOBAL_MEMORY, "memory", 2, "new")))

        assertEquals(setOf(StablePrefixInvalidationReason.SOURCE_REVISION_OR_HASH_CHANGED), domain.invalidationReasons(before, after))
        assertFalse(before.fingerprint == after.fingerprint)
    }

    @Test fun `bounded UI budget presets are interpreted by the P4-J domain`() {
        assertEquals(480, LocalContextCompressionBudgetPreset.COMPACT.toPolicy().maxTotalCodePoints)
        assertEquals(1_800, LocalContextCompressionBudgetPreset.BALANCED.toPolicy().maxTotalCodePoints)
        assertEquals(3_600, LocalContextCompressionBudgetPreset.EXPANDED.toPolicy().maxTotalCodePoints)
    }

    private fun compression(vararg entries: LocalContextCompressionEntry) = LocalContextCompressionSnapshot(
        ConversationId("conversation"), metadata, entries.toList(), LocalContextCompressionPolicy(),
    )

    private fun entry(layer: ContextBodyLayer, kind: ContextBodySourceKind, sourceId: String, revision: Int, source: String): LocalContextCompressionEntry {
        val body = "保留"
        return LocalContextCompressionEntry(
            layer, kind, sourceId, null, revision, MemoryDomain.sha256(source), body, MemoryDomain.sha256(body), 2, 2, 0, LOCAL_CONTEXT_COMPRESSION_POLICY_VERSION,
        )
    }
}
