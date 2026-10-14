package com.nanzhufeng.ai.domain
import org.junit.Assert.assertEquals
import org.junit.Test
class GenerationWakeLeaseTest {
    @Test fun `active request retains coverage beyond two minutes with finite total budget`() {
        val lease = GenerationWakeLease()
        assertEquals(0L,lease.timeoutMs(0))
        lease.begin(0)
        assertEquals(120_000L,lease.timeoutMs(0))
        assertEquals(120_000L,lease.timeoutMs(300_000))
        lease.begin(300_000) // A second task must not reset the active session hard cap.
        assertEquals(30_000L,lease.timeoutMs(1_770_000))
        assertEquals(0L,lease.timeoutMs(1_800_000))
        lease.finish(); assertEquals(0L,lease.timeoutMs(1_800_001))
        lease.begin(1_800_002); assertEquals(120_000L,lease.timeoutMs(1_800_002))
    }
}
