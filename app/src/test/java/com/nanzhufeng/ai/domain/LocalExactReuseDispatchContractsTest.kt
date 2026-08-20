package com.nanzhufeng.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LocalExactReuseDispatchContractsTest {
    @Test fun `exact hit routes only to the existing local response`() {
        val index = LocalExactReuseIndex()
        val exact = key()
        assertEquals(true, index.record(LocalExactReuseEntry(exact, "message:fixture", 10, 30)))
        val port = RecordingPort()

        val result = LocalExactReuseDispatchOwner(index).dispatch(exact, false, 11, port)

        assertEquals(LocalExactReuseDispatchResult.Reused("message:fixture"), result)
        assertEquals(listOf("message:fixture"), port.reused)
        assertFalse(port.continued.isNotEmpty())
    }

    @Test fun `miss ineligible and unknown only expose a content free continuation decision`() {
        val index = LocalExactReuseIndex()
        val owner = LocalExactReuseDispatchOwner(index)
        val port = RecordingPort()

        assertEquals(LocalExactReuseOutcome.MISS, continued(owner.dispatch(key(), false, 11, port)).outcome)
        assertEquals(LocalExactReuseOutcome.INELIGIBLE, continued(owner.dispatch(key(), true, 11, port)).outcome)
        assertEquals(LocalExactReuseOutcome.UNKNOWN, continued(owner.dispatch(null, false, 11, port)).outcome)
        assertEquals(emptyList<String>(), port.reused)
        assertEquals(listOf(LocalExactReuseOutcome.MISS, LocalExactReuseOutcome.INELIGIBLE, LocalExactReuseOutcome.UNKNOWN), port.continued.map { it.outcome })
        assertFalse(LocalExactReuseDispatchOwner::class.java.declaredFields.any { field ->
            field.name.contains("transport", true) || field.name.contains("usage", true) || field.name.contains("credential", true)
        })
    }

    private fun continued(result: LocalExactReuseDispatchResult): LocalExactReuseDecision =
        (result as LocalExactReuseDispatchResult.Continued).decision

    private fun key() = LocalExactReuseKey(
        scopeId = "workspace:fixture", providerId = "openrouter", modelSnapshotId = "model:fixture", endpointMode = "chat-completions",
        generationParametersHash = "b".repeat(64), toolSchemaHash = "c".repeat(64), contextManifestHash = "d".repeat(64),
        messageTreeHash = "e".repeat(64), attachmentHash = "f".repeat(64), templateVersion = "template:v1", policyVersion = 1,
        canonicalRequestHash = "a".repeat(64), sensitivity = LocalExactReuseSensitivity.LOW,
    )

    private class RecordingPort : LocalExactReuseDispatchPort {
        val reused = mutableListOf<String>()
        val continued = mutableListOf<LocalExactReuseDecision>()
        override fun reuseExistingLocalResponse(responseMessageId: String) { reused += responseMessageId }
        override fun continueWithoutReuse(decision: LocalExactReuseDecision) { continued += decision }
    }
}
