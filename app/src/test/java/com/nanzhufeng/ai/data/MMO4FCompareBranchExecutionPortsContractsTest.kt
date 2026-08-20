package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomCompareBranchExecutionPorts
import com.nanzhufeng.ai.data.local.RoomCompareConversationSessionStore
import com.nanzhufeng.ai.data.local.RoomConversationRepository
import com.nanzhufeng.ai.domain.AppendMessageRequest
import com.nanzhufeng.ai.domain.CanonicalContextSnapshotId
import com.nanzhufeng.ai.domain.CanonicalContextSnapshotRef
import com.nanzhufeng.ai.domain.CompareBranchCancellationId
import com.nanzhufeng.ai.domain.CompareBranchExecutionGrant
import com.nanzhufeng.ai.domain.CompareBranchId
import com.nanzhufeng.ai.domain.CompareConversationBranchPlan
import com.nanzhufeng.ai.domain.CompareConversationSessionId
import com.nanzhufeng.ai.domain.CompareConversationSessionIntentId
import com.nanzhufeng.ai.domain.CompareConversationSessionPlan
import com.nanzhufeng.ai.domain.CompareSessionStoreResult
import com.nanzhufeng.ai.domain.ContentBlock
import com.nanzhufeng.ai.domain.ConversationRealTextExecutionState
import com.nanzhufeng.ai.domain.ConversationTreeService
import com.nanzhufeng.ai.domain.InvocationId
import com.nanzhufeng.ai.domain.LogicalModelId
import com.nanzhufeng.ai.domain.MessageNodeId
import com.nanzhufeng.ai.domain.MessageRole
import com.nanzhufeng.ai.domain.ModelDeploymentId
import com.nanzhufeng.ai.domain.ProviderAttemptId
import com.nanzhufeng.ai.domain.ProviderHandle
import com.nanzhufeng.ai.domain.ProviderTransportModelId
import com.nanzhufeng.ai.domain.ProviderTransportProviderHandle
import com.nanzhufeng.ai.domain.RealTextExecutionCancellationIdentity
import com.nanzhufeng.ai.domain.RealTextExecutionPreflightId
import com.nanzhufeng.ai.domain.RealTextExecutionReadyPlan
import com.nanzhufeng.ai.domain.RealTextExecutionRuntimeReceiptResult
import com.nanzhufeng.ai.domain.RealTextExecutionUsageReservationResult
import com.nanzhufeng.ai.domain.RealTextUsageReservationPlan
import com.nanzhufeng.ai.domain.RealTextVerifiedCompareDeployment
import com.nanzhufeng.ai.domain.UsageLedgerKind
import com.nanzhufeng.ai.domain.UsageLedgerSource
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MMO4FCompareBranchExecutionPortsContractsTest {
    private val now = Instant.parse("2026-08-16T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private lateinit var database: NanfengAiDatabase
    private lateinit var conversations: RoomConversationRepository
    private lateinit var sessions: RoomCompareConversationSessionStore
    private lateinit var ports: RoomCompareBranchExecutionPorts

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NanfengAiDatabase::class.java).allowMainThreadQueries().build()
        conversations = RoomConversationRepository(database)
        sessions = RoomCompareConversationSessionStore(database, clock)
        ports = RoomCompareBranchExecutionPorts(database)
    }

    @After fun tearDown() = database.close()

    @Test fun `compare receipt and reservation are isolated from shared message tree and P3 runtime`() {
        val saved = savedUserLeaf()
        val session = persistedSession(saved.conversation.id.value, saved.conversation.currentLeafMessageId!!)
        val plan = ready(session, session.branches.first())

        assertTrue(ports.reserve(plan.usageReservation, now) is RealTextExecutionUsageReservationResult.Reserved)
        assertTrue(ports.start(plan, now.plusSeconds(1)) is RealTextExecutionRuntimeReceiptResult.Applied)
        assertTrue(ports.appendPartial(plan, "never persisted", now.plusSeconds(2)) is RealTextExecutionRuntimeReceiptResult.Applied)
        assertTrue(ports.finish(plan, ConversationRealTextExecutionState.SUCCEEDED, null, now.plusSeconds(3)) is RealTextExecutionRuntimeReceiptResult.Applied)

        val receipt = database.compareConversationDao().executionReceipt(plan.cancellation.executionId.value)!!
        assertEquals(ConversationRealTextExecutionState.SUCCEEDED.name, receipt.state)
        assertEquals(session.sessionId.value, receipt.sessionId)
        assertFalse(receipt.toString().contains("never persisted"))
        assertEquals(1, database.usageLedgerDao().entriesForExecution(plan.cancellation.executionId.value).size)
        assertEquals(UsageLedgerKind.BUDGET_RESERVATION.name, database.usageLedgerDao().entriesForExecution(plan.cancellation.executionId.value).single().kind)
        assertEquals(saved.conversation.currentLeafMessageId, conversations.findById(saved.conversation.id)!!.conversation.currentLeafMessageId)
        assertEquals(1, conversations.findById(saved.conversation.id)!!.nodes.size)
        assertNull(database.conversationDao().runtimeStateForConversation(saved.conversation.id.value))
        assertEquals("RESERVED", database.compareConversationDao().runtimeState(session.sessionId.value, session.branches.first().branchId.value)!!.state)
    }

    @Test fun `failure release is idempotent and a restarted port cannot bind another branch`() {
        val saved = savedUserLeaf()
        val session = persistedSession(saved.conversation.id.value, saved.conversation.currentLeafMessageId!!)
        val plan = ready(session, session.branches.first())
        assertTrue(ports.reserve(plan.usageReservation, now) is RealTextExecutionUsageReservationResult.Reserved)
        assertTrue(ports.finish(plan, ConversationRealTextExecutionState.FAILED, "TRANSPORT_DISABLED_NO_NETWORK", now.plusSeconds(1)) is RealTextExecutionRuntimeReceiptResult.Applied)
        assertTrue(ports.release(plan.usageReservation, "TRANSPORT_DISABLED_NO_NETWORK", now.plusSeconds(2)) is RealTextExecutionUsageReservationResult.Reserved)
        assertTrue(RoomCompareBranchExecutionPorts(database).release(plan.usageReservation, "TRANSPORT_DISABLED_NO_NETWORK", now.plusSeconds(3)) is RealTextExecutionUsageReservationResult.Replayed)
        assertEquals(2, database.usageLedgerDao().entriesForExecution(plan.cancellation.executionId.value).size)

        val mismatched = plan.copy(cancellation = plan.cancellation.copy(executionId = session.branches.last().let { branch ->
            com.nanzhufeng.ai.domain.ConversationRealTextExecutionId("compare:${session.sessionId.value}:${branch.branchId.value}:execution")
        }))
        assertTrue(ports.start(mismatched, now.plusSeconds(4)) is RealTextExecutionRuntimeReceiptResult.Rejected)
    }

    @Test fun `schema thirty two migration keeps compare and P3 tables while adding only receipt table`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "mmo4f-32-${UUID.randomUUID()}.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(32) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE compare_conversation_branches (sessionId TEXT NOT NULL, branchId TEXT NOT NULL, executionId TEXT NOT NULL, PRIMARY KEY(sessionId, branchId))")
                db.execSQL("CREATE TABLE conversation_real_text_executions (executionId TEXT NOT NULL PRIMARY KEY)")
                db.execSQL("CREATE TABLE usage_ledger_entries (entryId TEXT NOT NULL PRIMARY KEY)")
                db.execSQL("INSERT INTO compare_conversation_branches VALUES ('s', 'b', 'e')")
                db.execSQL("INSERT INTO conversation_real_text_executions VALUES ('p3')")
                db.execSQL("INSERT INTO usage_ledger_entries VALUES ('usage')")
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build())
        try {
            val sqlite = helper.writableDatabase
            NanfengAiDatabase.MIGRATION_32_33.migrate(sqlite)
            listOf("compare_conversation_branches", "conversation_real_text_executions", "usage_ledger_entries").forEach { table ->
                sqlite.query("SELECT COUNT(*) FROM $table").use { assertTrue(it.moveToFirst() && it.getInt(0) == 1) }
            }
            sqlite.query("SELECT 1 FROM sqlite_master WHERE type='table' AND name='compare_branch_execution_receipts'").use { assertTrue(it.moveToFirst()) }
        } finally { helper.close(); context.deleteDatabase(name) }
    }

    private fun savedUserLeaf() = ConversationTreeService(clock).let { tree ->
        tree.append(tree.create("Compare"), AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("input")))).also(conversations::save)
    }

    private fun persistedSession(conversationId: String, leaf: MessageNodeId): CompareConversationSessionPlan {
        val session = CompareConversationSessionPlan(
            CompareConversationSessionId("session"), CompareConversationSessionIntentId("intent"), "confirmation", "a".repeat(64),
            com.nanzhufeng.ai.domain.ConversationId(conversationId), leaf,
            CanonicalContextSnapshotRef(CanonicalContextSnapshotId("context"), "b".repeat(64), 1), leaf,
            listOf(branch("chatgpt", "logical.chatgpt", "deployment-gpt"), branch("claude", "logical.claude", "deployment-claude")), createdAt = now,
        )
        assertTrue(sessions.persist(session) is CompareSessionStoreResult.Stored)
        return session
    }

    private fun branch(id: String, logical: String, deployment: String) = CompareConversationBranchPlan(
        CompareBranchId(id), CompareBranchExecutionGrant("grant-$id", CompareBranchId(id), "a".repeat(64), "b".repeat(64), "c".repeat(64),
            LogicalModelId(logical), ModelDeploymentId(deployment), ProviderHandle("openrouter"), "provider/$id", "catalog", "price", "USD", 10,
            "d".repeat(64), now.plusSeconds(300)),
        MessageNodeId("assistant-$id"), InvocationId("invocation-$id"), ProviderAttemptId("attempt-$id"), CompareBranchCancellationId("cancel-$id"),
    )

    private fun ready(session: CompareConversationSessionPlan, branch: CompareConversationBranchPlan): RealTextExecutionReadyPlan {
        val executionId = com.nanzhufeng.ai.domain.ConversationRealTextExecutionId("compare:${session.sessionId.value}:${branch.branchId.value}:execution")
        val reservation = RealTextUsageReservationPlan("compare:${session.sessionId.value}:${branch.branchId.value}:reservation", executionId, session.conversationId,
            session.parentUserMessageId, branch.invocationId, branch.attemptId, ProviderTransportModelId(branch.grant.providerModelId), 1, 2, 10,
            "USD", "e".repeat(64), UsageLedgerSource.ANDROID_LOCAL)
        return RealTextExecutionReadyPlan(RealTextExecutionPreflightId("preflight-${branch.branchId.value}"),
            RealTextExecutionCancellationIdentity(RealTextExecutionPreflightId("preflight-${branch.branchId.value}"), executionId, branch.invocationId,
                branch.attemptId, branch.grant.requestFingerprint), ProviderTransportProviderHandle("openrouter"), ProviderTransportModelId(branch.grant.providerModelId),
            presetId = null, verifiedCompareDeployment = RealTextVerifiedCompareDeployment(branch.grant.logicalModelId, branch.grant.deploymentId,
                branch.grant.provider, branch.grant.catalogVersion, branch.grant.priceVersion), feeConfirmationFingerprint = branch.grant.confirmationScopeFingerprint,
            usageReservation = reservation, attachments = emptyList(), plannedAt = now)
    }
}
