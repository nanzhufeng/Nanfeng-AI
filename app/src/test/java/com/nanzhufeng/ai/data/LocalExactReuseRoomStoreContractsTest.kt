package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomLocalExactReuseEntryStore
import com.nanzhufeng.ai.domain.LocalExactReuseEntry
import com.nanzhufeng.ai.domain.LocalExactReuseKey
import com.nanzhufeng.ai.domain.LocalExactReuseOutcome
import com.nanzhufeng.ai.domain.LocalExactReuseSensitivity
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalExactReuseRoomStoreContractsTest {
    @Test fun `content free exact entry survives reopen then revoke and cleanup`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "p6l-reuse-${UUID.randomUUID()}.db"
        context.deleteDatabase(name)
        val key = key()
        val first = open(context, name)
        val store = RoomLocalExactReuseEntryStore(first)
        assertTrue(store.record(LocalExactReuseEntry(key, "message:fixture", 10, 30)))
        assertEquals(LocalExactReuseOutcome.LOCAL_EXACT_HIT, store.resolve(key, false, 11).outcome)
        first.close()

        val reopened = open(context, name)
        val restored = RoomLocalExactReuseEntryStore(reopened)
        assertEquals(LocalExactReuseOutcome.LOCAL_EXACT_HIT, restored.resolve(key, false, 11).outcome)
        assertTrue(restored.revoke(key.canonicalRequestHash))
        assertEquals(LocalExactReuseOutcome.MISS, restored.resolve(key, false, 11).outcome)
        assertEquals(1, restored.purgeExpiredOrRevoked(11))
        assertEquals(LocalExactReuseOutcome.MISS, restored.resolve(key, false, 11).outcome)
        reopened.close(); context.deleteDatabase(name)
    }

    private fun open(context: Context, name: String) = Room.databaseBuilder(context, NanfengAiDatabase::class.java, name).allowMainThreadQueries().build()
    private fun key() = LocalExactReuseKey(
        scopeId = "workspace:fixture", providerId = "openrouter", modelSnapshotId = "model:fixture", endpointMode = "chat-completions",
        generationParametersHash = "b".repeat(64), toolSchemaHash = "c".repeat(64), contextManifestHash = "d".repeat(64),
        messageTreeHash = "e".repeat(64), attachmentHash = "f".repeat(64), templateVersion = "template:v1",
        policyVersion = 1, canonicalRequestHash = "a".repeat(64), sensitivity = LocalExactReuseSensitivity.LOW,
    )
}
