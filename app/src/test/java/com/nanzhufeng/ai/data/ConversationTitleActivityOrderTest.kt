package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomConversationRepository
import com.nanzhufeng.ai.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/** Production rename/title writers and Room list/reopen; no Provider or real user data. */
@RunWith(RobolectricTestRunner::class)
class ConversationTitleActivityOrderTest {
    private fun clock(day: Int)=Clock.fixed(Instant.parse("2026-08-${day.toString().padStart(2,'0')}T12:00:00Z"),ZoneOffset.UTC)
    private fun opening(day: Int,title: String): ConversationSnapshot {
        val tree=ConversationTreeService(clock(day))
        return tree.append(tree.append(tree.create(title),AppendMessageRequest(MessageRole.USER,listOf(ContentBlock.Text("公开问题")))),AppendMessageRequest(MessageRole.ASSISTANT,listOf(ContentBlock.Text("公开回答"))))
    }
    @Test fun `manual and automatic title changes keep order on reopen and sync while messages still promote`() {
        for (automatic in listOf(false,true)) {
            val context=ApplicationProvider.getApplicationContext<Context>()
            val name="title-order-${UUID.randomUUID()}.db"
            fun open()=Room.databaseBuilder(context,NanfengAiDatabase::class.java,name).allowMainThreadQueries().build()
            var db=open()
            try {
                var repository=RoomConversationRepository(db)
                val old=opening(10,"较旧对话").let {it.copy(conversation=it.conversation.copy(autoTitlePending=automatic))}
                repository.save(old)
                val middle=repository.save(opening(11,"中间对话"));val newest=repository.save(opening(12,"最新对话"))
                val expected=listOf(newest.conversation.id,middle.conversation.id,old.conversation.id)
                assertEquals(expected,repository.list(ConversationListScope.ACTIVE).map {it.id})
                if (automatic) assertTrue(repository.completeAutomaticTitle(old.conversation.id,"自动整理的新标题",clock(15).instant()))
                else {
                    val result=ManageConversationUseCase(ConversationManagementDomain(clock(15)),repository).execute(
                        ConversationManagementIntent(ConversationManagementIntentId.new(),old.conversation.id,ConversationManagementAction.RENAME,old.conversation.revision,"手动修改的新标题"))
                    assertTrue(result is ConversationManagementResult.Applied)
                }
                val renamed=repository.findById(old.conversation.id)!!
                assertEquals("Title metadata is not conversation activity",old.conversation.updatedAt,renamed.conversation.updatedAt)
                assertTrue(renamed.conversation.revision>old.conversation.revision)
                assertEquals((old.conversation.titleRevision ?: 0L)+1,renamed.conversation.titleRevision)
                assertEquals(expected,repository.list(ConversationListScope.ACTIVE).map {it.id})
                // A newer title version must sync even when the content/activity timestamp is equal.
                val merged=(mergeNewerRemoteConversation(old,renamed) as P7FExistingConversationMerge.Applied).snapshot
                assertEquals(renamed.conversation.title,merged.conversation.title)
                assertEquals(old.conversation.updatedAt,merged.conversation.updatedAt)
                db.close();db=open();repository=RoomConversationRepository(db)
                assertEquals(renamed.conversation.title,repository.findById(old.conversation.id)!!.conversation.title)
                assertEquals(expected,repository.list(ConversationListScope.ACTIVE).map {it.id})
                val appended=ConversationTreeService(clock(16)).append(repository.findById(old.conversation.id)!!,
                    AppendMessageRequest(MessageRole.USER,listOf(ContentBlock.Text("新的对话内容"))))
                repository.save(appended)
                assertEquals(listOf(old.conversation.id,newest.conversation.id,middle.conversation.id),repository.list(ConversationListScope.ACTIVE).map {it.id})
            } finally {db.close();context.deleteDatabase(name)}
        }
    }
}
