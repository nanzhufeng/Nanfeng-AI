package com.nanzhufeng.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class LocalExactReuseV1ContractsTest {
    private fun key(requestHash: String = "a".repeat(64), sensitivity: LocalExactReuseSensitivity = LocalExactReuseSensitivity.LOW) = LocalExactReuseKey(
        scopeId = "workspace:fixture", providerId = "openrouter", modelSnapshotId = "model:fixture", endpointMode = "chat-completions",
        generationParametersHash = "b".repeat(64), toolSchemaHash = "c".repeat(64), contextManifestHash = "d".repeat(64), messageTreeHash = "e".repeat(64), attachmentHash = "f".repeat(64), templateVersion = "template:v1", policyVersion = 1, canonicalRequestHash = requestHash, sensitivity = sensitivity,
    )

    @Test fun `only a normal complete key may hit an unexpired non revoked exact entry`() {
        val index = LocalExactReuseIndex(); val exact = key()
        assertEquals(true, index.record(LocalExactReuseEntry(exact, "message:fixture", 10, 20)))
        assertEquals(LocalExactReuseOutcome.LOCAL_EXACT_HIT, index.resolve(exact, false, 19).outcome)
        assertEquals("message:fixture", index.resolve(exact, false, 19).responseMessageId)
        assertEquals(LocalExactReuseOutcome.MISS, index.resolve(key("9".repeat(64)), false, 19).outcome)
        assertEquals(LocalExactReuseOutcome.MISS, index.resolve(exact, false, 20).outcome)
    }

    @Test fun `temporary high sensitive revoked and incomplete requests fail closed`() {
        val index = LocalExactReuseIndex(); val exact = key()
        assertEquals(true, index.record(LocalExactReuseEntry(exact, "message:fixture", 10, 30)))
        assertEquals(LocalExactReuseOutcome.INELIGIBLE, index.resolve(exact, true, 11).outcome)
        assertFalse(index.record(LocalExactReuseEntry(key(sensitivity = LocalExactReuseSensitivity.HIGH), "message:high", 10, 30)))
        assertEquals(LocalExactReuseOutcome.INELIGIBLE, index.resolve(key(sensitivity = LocalExactReuseSensitivity.HIGH), false, 11).outcome)
        assertEquals(LocalExactReuseOutcome.UNKNOWN, index.resolve(null, false, 11).outcome); assertNull(index.resolve(null, false, 11).responseMessageId)
        assertFalse(index.record(LocalExactReuseEntry(key("8".repeat(64)), "message:revoked", 10, 30, revoked = true)))
    }
}
