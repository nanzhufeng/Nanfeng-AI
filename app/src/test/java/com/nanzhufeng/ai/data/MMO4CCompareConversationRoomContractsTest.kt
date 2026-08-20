package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomCompareConversationSessionStore
import com.nanzhufeng.ai.data.local.RoomConversationRepository
import com.nanzhufeng.ai.domain.AppendMessageRequest
import com.nanzhufeng.ai.domain.CanonicalContextSnapshotId
import com.nanzhufeng.ai.domain.CanonicalContextSnapshotRef
import com.nanzhufeng.ai.domain.CompareBranchAdoptionIntent
import com.nanzhufeng.ai.domain.CompareBranchAdoptionIntentId
import com.nanzhufeng.ai.domain.CompareBranchCancellationId
import com.nanzhufeng.ai.domain.CompareBranchExecutionGrant
import com.nanzhufeng.ai.domain.CompareBranchFollowUpIntent
import com.nanzhufeng.ai.domain.CompareBranchFollowUpIntentId
import com.nanzhufeng.ai.domain.CompareBranchId
import com.nanzhufeng.ai.domain.CompareBranchReservationState
import com.nanzhufeng.ai.domain.CompareBranchTerminalIntent
import com.nanzhufeng.ai.domain.CompareBranchTerminalIntentId
import com.nanzhufeng.ai.domain.CompareConversationBranchPlan
import com.nanzhufeng.ai.domain.CompareConversationSessionId
import com.nanzhufeng.ai.domain.CompareConversationSessionIntentId
import com.nanzhufeng.ai.domain.CompareConversationSessionPlan
import com.nanzhufeng.ai.domain.CompareConversationSessionRejection
import com.nanzhufeng.ai.domain.CompareDispatchIntent
import com.nanzhufeng.ai.domain.CompareDispatchIntentId
import com.nanzhufeng.ai.domain.CompareDispatchRecoveryState
import com.nanzhufeng.ai.domain.CompareSessionStoreResult
import com.nanzhufeng.ai.domain.CompareSynthesisConsentBudgetGate
import com.nanzhufeng.ai.domain.CompareSynthesisIntent
import com.nanzhufeng.ai.domain.CompareSynthesisIntentId
import com.nanzhufeng.ai.domain.ContentBlock
import com.nanzhufeng.ai.domain.ConversationTreeService
import com.nanzhufeng.ai.domain.InvocationId
import com.nanzhufeng.ai.domain.LogicalModelId
import com.nanzhufeng.ai.domain.MessageNodeId
import com.nanzhufeng.ai.domain.MessageRole
import com.nanzhufeng.ai.domain.ModelDeploymentId
import com.nanzhufeng.ai.domain.ProviderAttemptId
import com.nanzhufeng.ai.domain.ProviderHandle
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MMO4CCompareConversationRoomContractsTest {
    private val now = Instant.parse("2026-08-16T08:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private lateinit var database: NanfengAiDatabase
    private lateinit var conversations: RoomConversationRepository
    private lateinit var store: RoomCompareConversationSessionStore

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NanfengAiDatabase::class.java).allowMainThreadQueries().build()
        conversations = RoomConversationRepository(database)
        store = RoomCompareConversationSessionStore(database, clock)
    }

    @After fun tearDown() = database.close()

    @Test fun `persisted compare branches have isolated runtime references while the shared leaf and P3 runtime remain untouched`() {
        val snapshot = savedUserLeaf()
        val leaf = snapshot.conversation.currentLeafMessageId!!
        val plan = plan(snapshot.conversation.id.value, leaf)
        val stored = store.persist(plan) as CompareSessionStoreResult.Stored

        assertEquals(leaf, conversations.findById(snapshot.conversation.id)!!.conversation.currentLeafMessageId)
        assertEquals(1, conversations.findById(snapshot.conversation.id)!!.nodes.size)
        assertEquals(2, stored.value.runtimeReferences.size)
        assertEquals(2, stored.value.runtimeReferences.map { it.executionId }.toSet().size)
        assertEquals(2, stored.value.runtimeReferences.map { it.usageReservationReplayToken }.toSet().size)
        assertTrue(database.conversationDao().runtimeStateForConversation(snapshot.conversation.id.value) == null)
        assertTrue(store.persist(plan) is CompareSessionStoreResult.Replayed)
        assertEquals(CompareConversationSessionRejection.SESSION_INTENT_CONFLICT,
            (store.persist(plan.copy(createdAt = now.plusSeconds(1))) as CompareSessionStoreResult.Rejected).reason)

        val restarted = RoomCompareConversationSessionStore(database, clock).read(plan.sessionId)!!
        assertEquals(plan, restarted.plan)
        assertEquals(leaf, conversations.findById(snapshot.conversation.id)!!.conversation.currentLeafMessageId)
        database.conversationDao().findNode(plan.branches.first().assistantMessageId.value)?.let { error("reserved assistant must not be inserted") }
    }

    @Test fun `terminal branches stay isolated and follow-up adoption synthesis retain sibling contracts after restart`() {
        val snapshot = savedUserLeaf()
        val plan = plan(snapshot.conversation.id.value, snapshot.conversation.currentLeafMessageId!!)
        store.persist(plan)
        val first = plan.branches[0]
        val second = plan.branches[1]
        assertTrue(store.recordTerminal(CompareBranchTerminalIntent(CompareBranchTerminalIntentId("success"), plan.sessionId, first.branchId, CompareBranchReservationState.SUCCEEDED), now.plusSeconds(1)) is CompareSessionStoreResult.Stored)
        assertTrue(store.recordTerminal(CompareBranchTerminalIntent(CompareBranchTerminalIntentId("success"), plan.sessionId, first.branchId, CompareBranchReservationState.SUCCEEDED), now.plusSeconds(1)) is CompareSessionStoreResult.Replayed)
        assertEquals(CompareConversationSessionRejection.BRANCH_TERMINAL_INTENT_CONFLICT,
            (store.recordTerminal(CompareBranchTerminalIntent(CompareBranchTerminalIntentId("success"), plan.sessionId, first.branchId, CompareBranchReservationState.FAILED), now.plusSeconds(1)) as CompareSessionStoreResult.Rejected).reason)
        assertTrue(store.recordTerminal(CompareBranchTerminalIntent(CompareBranchTerminalIntentId("cancel"), plan.sessionId, second.branchId, CompareBranchReservationState.CANCELLED, second.cancellationId), now.plusSeconds(2)) is CompareSessionStoreResult.Stored)
        val read = RoomCompareConversationSessionStore(database, clock).read(plan.sessionId)!!
        assertEquals(listOf(CompareBranchReservationState.SUCCEEDED, CompareBranchReservationState.CANCELLED), read.plan.branches.map { it.state })
        assertEquals(snapshot.conversation.currentLeafMessageId, conversations.findById(snapshot.conversation.id)!!.conversation.currentLeafMessageId)
        val follow = store.storeFollowUp(CompareBranchFollowUpIntent(CompareBranchFollowUpIntentId("follow"), plan.sessionId, first.branchId, context("follow", "c"))) as CompareSessionStoreResult.Stored
        assertEquals(setOf(second.assistantMessageId), follow.value.excludedSiblingAssistantMessageIds)
        val adoption = store.storeAdoption(CompareBranchAdoptionIntent(CompareBranchAdoptionIntentId("adopt"), plan.sessionId, first.branchId)) as CompareSessionStoreResult.Stored
        assertEquals(setOf(second.assistantMessageId), adoption.value.preservedSiblingAssistantMessageIds)
        assertEquals(CompareConversationSessionRejection.ADOPTION_REQUIRES_SUCCESS,
            (store.storeAdoption(CompareBranchAdoptionIntent(CompareBranchAdoptionIntentId("bad-adopt"), plan.sessionId, second.branchId)) as CompareSessionStoreResult.Rejected).reason)

        val bothSuccess = plan(snapshot.conversation.id.value, snapshot.conversation.currentLeafMessageId!!, "two-success")
        store.persist(bothSuccess)
        bothSuccess.branches.forEachIndexed { index, branch -> store.recordTerminal(CompareBranchTerminalIntent(CompareBranchTerminalIntentId("done-$index"), bothSuccess.sessionId, branch.branchId, CompareBranchReservationState.SUCCEEDED), now.plusSeconds((index + 3).toLong())) }
        val synthesis = store.storeSynthesis(CompareSynthesisIntent(CompareSynthesisIntentId("synthesis"), bothSuccess.sessionId, bothSuccess.branches.map { it.branchId }, context("synthesis", "d"), CompareSynthesisConsentBudgetGate("gate", "e".repeat(64), "USD", 99, now.plusSeconds(60)))) as CompareSessionStoreResult.Stored
        assertTrue(synthesis.value.isIndependentInvocation)
        assertEquals(2, synthesis.value.sourceAssistantMessageIds.size)

        val partial = plan(snapshot.conversation.id.value, snapshot.conversation.currentLeafMessageId!!, "partial")
        store.persist(partial)
        store.recordTerminal(CompareBranchTerminalIntent(CompareBranchTerminalIntentId("failed"), partial.sessionId, partial.branches[0].branchId, CompareBranchReservationState.FAILED), now.plusSeconds(10))
        store.recordTerminal(CompareBranchTerminalIntent(CompareBranchTerminalIntentId("cancelled"), partial.sessionId, partial.branches[1].branchId, CompareBranchReservationState.CANCELLED, partial.branches[1].cancellationId), now.plusSeconds(11))
        assertEquals(listOf(CompareBranchReservationState.FAILED, CompareBranchReservationState.CANCELLED), store.read(partial.sessionId)!!.plan.branches.map { it.state })
    }

    @Test fun `dispatch facts are content-free append-only and restart requires fresh confirmation until batch acceptance`() {
        val snapshot = savedUserLeaf()
        val plan = plan(snapshot.conversation.id.value, snapshot.conversation.currentLeafMessageId!!, "dispatch")
        store.persist(plan)
        val intent = CompareDispatchIntent(
            CompareDispatchIntentId("dispatch-intent"), plan.sessionId, plan.confirmationId, plan.requestFingerprint,
            plan.canonicalContext, plan.branches.map { it.grant.grantId },
        )

        assertEquals(CompareDispatchRecoveryState.RETRY_REQUIRED, store.read(plan.sessionId)!!.dispatchRecoveryState)
        assertTrue(store.recordDispatchIntent(intent, now) is CompareSessionStoreResult.Stored)
        assertEquals(CompareDispatchRecoveryState.PENDING_ACCEPTANCE_RETRY_REQUIRED,
            RoomCompareConversationSessionStore(database, clock).read(plan.sessionId)!!.dispatchRecoveryState)
        assertTrue(store.recordDispatchAccepted(intent, now.plusSeconds(1)) is CompareSessionStoreResult.Stored)
        assertEquals(CompareDispatchRecoveryState.ACCEPTED,
            RoomCompareConversationSessionStore(database, clock).read(plan.sessionId)!!.dispatchRecoveryState)
        assertTrue(store.recordDispatchAccepted(intent, now.plusSeconds(2)) is CompareSessionStoreResult.Replayed)

        val events = database.compareConversationDao().runtimeEvents(plan.sessionId.value)
        assertEquals(2, events.count { it.kind == "DISPATCH_INTENT_RECORDED" })
        assertEquals(2, events.count { it.kind == "DISPATCH_ACCEPTED" })
        assertTrue(events.none { it.payloadFingerprint.contains("input") || it.eventId.contains("input") })
        assertEquals(snapshot.conversation.currentLeafMessageId, conversations.findById(snapshot.conversation.id)!!.conversation.currentLeafMessageId)
        assertTrue(database.conversationDao().runtimeStateForConversation(snapshot.conversation.id.value) == null)
    }

    @Test fun `schema thirty one migration preserves existing P3 facts and only appends compare tables`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "mmo4c-31-${UUID.randomUUID()}.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(31) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE conversations (id TEXT NOT NULL PRIMARY KEY, currentLeafMessageId TEXT, title TEXT NOT NULL)")
                db.execSQL("CREATE TABLE conversation_runtime_states (invocationId TEXT NOT NULL PRIMARY KEY, conversationId TEXT NOT NULL, messageId TEXT NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX index_conversation_runtime_states_conversationId ON conversation_runtime_states (conversationId)")
                db.execSQL("CREATE TABLE conversation_real_text_executions (executionId TEXT NOT NULL PRIMARY KEY, idempotencyKey TEXT NOT NULL)")
                db.execSQL("CREATE TABLE usage_ledger_entries (entryId TEXT NOT NULL PRIMARY KEY, replayToken TEXT NOT NULL)")
                db.execSQL("INSERT INTO conversations VALUES ('c', 'leaf', 'existing')")
                db.execSQL("INSERT INTO conversation_runtime_states VALUES ('i', 'c', 'leaf')")
                db.execSQL("INSERT INTO conversation_real_text_executions VALUES ('e', 'key')")
                db.execSQL("INSERT INTO usage_ledger_entries VALUES ('u', 'token')")
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build())
        try {
            val sqlite = helper.writableDatabase
            NanfengAiDatabase.MIGRATION_31_32.migrate(sqlite)
            listOf("conversations", "conversation_runtime_states", "conversation_real_text_executions", "usage_ledger_entries").forEach { table ->
                sqlite.query("SELECT COUNT(*) FROM $table").use { assertTrue(it.moveToFirst() && it.getInt(0) == 1) }
            }
            sqlite.query("SELECT 1 FROM sqlite_master WHERE type='index' AND name='index_conversation_runtime_states_conversationId'").use { assertTrue(it.moveToFirst()) }
            listOf("compare_conversation_sessions", "compare_conversation_branches", "compare_branch_runtime_states", "compare_branch_runtime_events", "compare_branch_terminal_intents", "compare_branch_follow_up_intents", "compare_branch_adoption_intents", "compare_synthesis_intents").forEach { table ->
                sqlite.query("SELECT 1 FROM sqlite_master WHERE type='table' AND name='$table'").use { assertTrue("missing $table", it.moveToFirst()) }
            }
        } finally { helper.close(); context.deleteDatabase(name) }
    }

    private fun savedUserLeaf() = ConversationTreeService(clock).let { tree ->
        tree.append(tree.create("Compare"), AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("input")))).also(conversations::save)
    }

    private fun plan(conversationId: String, leaf: MessageNodeId, suffix: String = "one") = CompareConversationSessionPlan(
        CompareConversationSessionId("session-$suffix"), CompareConversationSessionIntentId("intent-$suffix"), "confirmation-$suffix", "a".repeat(64),
        com.nanzhufeng.ai.domain.ConversationId(conversationId), leaf, context("original-$suffix", "a"), leaf,
        listOf(branch("one-$suffix", "logical.chatgpt", "deployment-gpt"), branch("two-$suffix", "logical.claude", "deployment-claude")),
        createdAt = now,
    )

    private fun branch(id: String, logical: String, deployment: String) = CompareConversationBranchPlan(
        CompareBranchId(id), CompareBranchExecutionGrant("grant-$id", CompareBranchId(id), "a".repeat(64), "a".repeat(64), "f".repeat(64),
            LogicalModelId(logical), ModelDeploymentId(deployment), ProviderHandle("openrouter"), "provider/$id", "catalog", "price", "USD", 10, "e".repeat(64), now.plusSeconds(300)),
        MessageNodeId("assistant-$id"), InvocationId("invocation-$id"), ProviderAttemptId("attempt-$id"), CompareBranchCancellationId("cancel-$id"),
    )

    private fun context(id: String, hash: String) = CanonicalContextSnapshotRef(CanonicalContextSnapshotId(id), hash.repeat(64), 1)
}
