package com.nanzhufeng.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ThirdPartyProfilePersonalizationContractsTest {
    @Test fun `only versioned allowlist fields become canonical personalization`() {
        val result = ThirdPartyProfilePersonalizationSchema.parse("""{"format":"nfai.third-party-profile-personalization","version":1,"personalization":{"displayName":"Fixture","language":"zh-CN","notificationsEnabled":true}}""".toByteArray())
        assertTrue(result is ThirdPartyProfilePersonalizationParseResult.Mapped)
        val mapped = (result as ThirdPartyProfilePersonalizationParseResult.Mapped).value
        assertEquals(3, mapped.mappedFieldCount)
        assertEquals("Fixture", mapped.displayName)
    }

    @Test fun `sensitive or unknown profile data is rejected without a partial projection`() {
        val result = ThirdPartyProfilePersonalizationSchema.parse("""{"format":"nfai.third-party-profile-personalization","version":1,"personalization":{"displayName":"Fixture","email":"never-map@example.invalid"}}""".toByteArray())
        assertTrue(result is ThirdPartyProfilePersonalizationParseResult.Rejected)
    }
}
