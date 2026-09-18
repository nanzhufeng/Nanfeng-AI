package com.nanzhufeng.ai.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class P7FCloudDocumentListTest {
    @Test fun `list transport preserves siblings around retired and malformed envelopes`() {
        val documents = decodeCloudDocumentList("""[{"envelope":{"format":"nfai.sync.direct","appId":"com.nanzhufeng.ai"}},{"envelope":"broken"},{"envelope":{"format":"nfai.sync.envelope"}}]""")
        assertEquals(3, documents.size)
        assertEquals("broken", documents[1])
    }
    @Test fun `invalid row remains an item failure rather than an empty inventory`() {
        assertEquals(listOf("{}", "{}"), decodeCloudDocumentList("""[{},null]"""))
    }
}
