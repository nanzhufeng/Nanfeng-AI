package com.nanzhufeng.ai.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class P7DAvatarCacheContractsTest {
    @Test fun `cache is account URL isolated and rejects non googleusercontent sources`() {
        val cache = P7DAvatarCache(ApplicationProvider.getApplicationContext())
        val url = "https://lh3.googleusercontent.com/a-/safe"; cache.write("account-a", url, byteArrayOf(1, 2))
        assertArrayEquals(byteArrayOf(1, 2), cache.read("account-a", url)); assertNull(cache.read("account-b", url))
        cache.deleteAccount("account-a"); assertNull(cache.read("account-a", url))
        org.junit.Assert.assertTrue(runCatching { cache.write("account-a", "https://evil.invalid/a", byteArrayOf(1)) }.isFailure)
    }
}
