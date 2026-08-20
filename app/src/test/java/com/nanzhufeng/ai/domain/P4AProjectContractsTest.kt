package com.nanzhufeng.ai.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class P4AProjectContractsTest {
    private val domain = ProjectDomain(Clock.fixed(Instant.parse("2026-08-13T12:00:00Z"), ZoneOffset.UTC))

    @Test fun `title boundaries revision history and empty instruction stay auditable`() {
        val created = domain.create(ProjectIntent(ProjectIntentId("create"), ProjectIntentAction.CREATE, ProjectId("p"), title = "  P4-A  ", description = " 本地范围 "))
        assertEquals("P4-A", created.project.title)
        val first = domain.reviseInstruction(created, "  仅处理这个项目  ")
        val cleared = domain.reviseInstruction(first, "  ")
        assertEquals(2, cleared.instructionRevisions.size)
        assertTrue(requireNotNull(cleared.activeInstruction).isEmptyInstruction)
        assertEquals(ProjectDomain.sha256(""), cleared.activeInstruction!!.contentHash)
        assertEquals(cleared, domain.reviseInstruction(cleared, ""))
        assertTrue(runCatching { domain.create(ProjectIntent(ProjectIntentId("bad"), ProjectIntentAction.CREATE, ProjectId("bad"), title = "😀".repeat(121))) }.isFailure)
    }

    @Test fun `instruction IR fixes system safety project ordering without prompt construction`() {
        val snapshot = domain.reviseInstruction(domain.create(ProjectIntent(ProjectIntentId("create"), ProjectIntentAction.CREATE, ProjectId("p"), title = "P")), "用户项目指令")
        val resolution = ProjectInstructionResolution.resolve(snapshot)
        assertEquals(listOf(InstructionLayerPriority.SYSTEM, InstructionLayerPriority.SAFETY, InstructionLayerPriority.PROJECT, InstructionLayerPriority.CONVERSATION, InstructionLayerPriority.CURRENT_USER), resolution.layers.map { it.priority })
        assertEquals("用户项目指令", resolution.layers.single { it.priority == InstructionLayerPriority.PROJECT }.userText)
        assertEquals(snapshot.activeInstruction!!.id, resolution.projectContext.instructionRevisionId)
        assertFalse(resolution.layers.any { it.source.contains("PROVIDER") || it.source.contains("PROMPT") })
    }
}
