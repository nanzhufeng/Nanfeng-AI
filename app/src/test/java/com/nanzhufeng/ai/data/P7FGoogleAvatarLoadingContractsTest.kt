package com.nanzhufeng.ai.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class P7FGoogleAvatarLoadingContractsTest {
    @Test fun `avatar refresh prefers the authenticated proxy and constrains the Google fallback`() {
        val source = File("src/main/java/com/nanzhufeng/ai/data/P7FGoogleAccountSession.kt").readText()
        assertTrue(source.contains("/functions/v1/google-avatar"))
        assertTrue(source.contains("Authorization\", \"Bearer \$accessToken\""))
        assertTrue(source.contains("client?.fetchGoogleAvatar(session.accessToken, avatarUrl)"))
        assertTrue(source.contains("connection.instanceFollowRedirects = false"))
        assertTrue(source.contains("P7DAvatarCache.MAX_BYTES"))
        assertTrue(source.contains("AVATAR_TIMEOUT_MILLIS = 8_000"))
        assertTrue(source.contains("avatarCache.deleteAccount(session.userId)"))
    }
}
