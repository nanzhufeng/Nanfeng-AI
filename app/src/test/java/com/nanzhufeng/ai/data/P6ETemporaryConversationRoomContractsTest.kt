package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomTemporaryConversationRecoveryStore
import com.nanzhufeng.ai.domain.AttachmentId
import com.nanzhufeng.ai.domain.TemporaryAttachmentScope
import com.nanzhufeng.ai.domain.TemporaryConversationDomain
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class P6ETemporaryConversationRoomContractsTest {
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NanfengAiDatabase::class.java).allowMainThreadQueries().build()
    @After fun close() = database.close()

    @Test fun `temporary recovery writes only isolated tables with temporary session attachment scope`() {
        val owner = TemporaryConversationDomain(RoomTemporaryConversationRecoveryStore(database), Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC))
        val attachment = AttachmentId("temporary-attachment")
        owner.enterOrRestore()
        owner.updateDraft("isolated", listOf(attachment))
        owner.updateModelOverride("local.fixture-v1")
        owner.appendOfflineMessage()
        val id = owner.readRecovery()!!.id

        assertEquals(0, database.conversationDao().listAllNonDeletedConversations().size)
        assertEquals(TemporaryAttachmentScope.TEMPORARY_SESSION.name, database.temporaryConversationRecoveryDao().attachments(id.value).single().scope)
        assertEquals(listOf(attachment), owner.readRecovery()!!.messages.single().attachmentIds)
        assertEquals("local.fixture-v1", owner.readRecovery()!!.modelOverrideId)
        owner.exitOrClear()
        assertNull(database.temporaryConversationRecoveryDao().active())
    }
}
