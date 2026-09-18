package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomConversationRepository
import com.nanzhufeng.ai.domain.AppendMessageRequest
import com.nanzhufeng.ai.domain.ContentBlock
import com.nanzhufeng.ai.domain.ConversationSnapshot
import com.nanzhufeng.ai.domain.ConversationTreeService
import com.nanzhufeng.ai.domain.MessageRole
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
class P3AConversationRoomContractsTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-12T15:00:00Z"), ZoneOffset.UTC)
    private lateinit var database: NanfengAiDatabase
    private lateinit var repository: RoomConversationRepository
    private lateinit var service: ConversationTreeService

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NanfengAiDatabase::class.java).allowMainThreadQueries().build()
        repository = RoomConversationRepository(database)
        service = ConversationTreeService(clock)
    }

    @After
    fun tearDown() = database.close()

    @Test fun `creation repair rolls back on write failure and survives database close and reopen`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "creation-repair-${UUID.randomUUID()}.db"
        database.close()
        database = Room.databaseBuilder(context, NanfengAiDatabase::class.java, name).allowMainThreadQueries().build()
        repository = RoomConversationRepository(database)
        try {
            val start = Instant.parse("2026-09-16T10:47:36.957Z")
            val old = repository.save(ConversationTreeService(Clock.fixed(start, ZoneOffset.UTC)).append(
                service.create(), AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("question"))),
            ))
            recordNativeSend(old, start)
            database.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_creation_repair BEFORE UPDATE OF createdAtEpochMs ON conversations BEGIN SELECT RAISE(ABORT, 'fixture failure'); END")
            org.junit.Assert.assertThrows(android.database.sqlite.SQLiteException::class.java) { repository.repairLegacyCreationTimes() }
            assertEquals(old, repository.findById(old.conversation.id))
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_creation_repair")
            assertEquals(1, repository.repairLegacyCreationTimes())
            database.close()
            database = Room.databaseBuilder(context, NanfengAiDatabase::class.java, name).allowMainThreadQueries().build()
            repository = RoomConversationRepository(database)
            assertEquals(start, repository.findById(old.conversation.id)!!.conversation.createdAt)
            assertEquals(0, repository.repairLegacyCreationTimes())
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun `cloud round trip keeps corrected creation date even when stale client activity is newer`() {
        val start = Instant.parse("2026-09-16T10:47:36.957Z")
        val blank = service.create()
        val sent = ConversationTreeService(Clock.fixed(start, ZoneOffset.UTC)).append(
            blank, AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("question"))),
        )
        val old = repository.save(sent)
        val corrected = old.copy(conversation = old.conversation.copy(createdAt = start, revision = old.conversation.revision + 1))
        val merged = (mergeNewerRemoteConversation(old, corrected) as P7FExistingConversationMerge.Applied).snapshot
        assertEquals(start, repository.saveVerifiedCloudMerge(merged).conversation.createdAt)
        val stale = old.copy(conversation = old.conversation.copy(updatedAt = start.plusSeconds(86400), revision = 10))
        assertEquals(start, mergeRemoteAdditionsForLocalCommit(stale, corrected).conversation.createdAt)
        assertEquals(start, mergeRemoteAdditionsForLocalCommit(corrected, stale).conversation.createdAt)
        assertEquals(start, (mergeNewerRemoteConversation(corrected, stale) as P7FExistingConversationMerge.Applied).snapshot.conversation.createdAt)
    }

    @Test fun `legacy native creation date repairs once without rewriting activity or messages`() {
        val start = Instant.parse("2026-09-15T16:40:47.885Z")
        val sender = ConversationTreeService(Clock.fixed(start, ZoneOffset.UTC))
        val blank = service.create(autoTitlePending = true)
        val sent = sender.append(blank, AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("question"))))
        val old = repository.save(sent.copy(conversation = sent.conversation.copy(createdAt = blank.conversation.createdAt)))
        // A portable snapshot alone is insufficient evidence for rewriting its source date.
        assertEquals(0, RoomConversationRepository(database).repairLegacyCreationTimes())
        recordNativeSend(old, start)
        assertEquals(1, RoomConversationRepository(database).repairLegacyCreationTimes())
        val repaired = RoomConversationRepository(database).findById(old.conversation.id)!!
        assertEquals(start, repaired.conversation.createdAt)
        assertEquals("2026/09/16", java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd").withZone(java.time.ZoneId.of("Asia/Shanghai")).format(repaired.conversation.createdAt))
        assertEquals(old.conversation.updatedAt, repaired.conversation.updatedAt)
        assertEquals(old.conversation.revision + 1, repaired.conversation.revision)
        assertEquals(old.nodes, repaired.nodes)
        assertEquals(0, RoomConversationRepository(database).repairLegacyCreationTimes())
        assertEquals(repaired, RoomConversationRepository(database).findById(old.conversation.id))
    }

    @Test fun `import provenance protects original creation date even after a native retry`() {
        val start = Instant.parse("2026-09-16T10:47:36.957Z")
        val sender = ConversationTreeService(Clock.fixed(start, ZoneOffset.UTC))
        val blank = service.create()
        val imported = repository.save(sender.append(blank, AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("imported")))))
        recordNativeSend(imported, start)
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO chatgpt_import_provenance (conversationId,sourceConversationId,packageHash,contentHash,importedAtEpochMs,adapterId,adapterVersion,revokedAtEpochMs) VALUES (?,?,?,?,?,?,?,NULL)",
            arrayOf<Any>(imported.conversation.id.value, "source", "package", "content", start.toEpochMilli(), "test", 1),
        )
        assertEquals(0, RoomConversationRepository(database).repairLegacyCreationTimes())
        assertEquals(imported, repository.findById(imported.conversation.id))
    }

    private fun recordNativeSend(snapshot: ConversationSnapshot, at: Instant) {
        val id = com.nanzhufeng.ai.domain.NormalChatSendAttemptId.new()
        com.nanzhufeng.ai.data.local.RoomNormalChatSendAttemptStore(database).create(
            com.nanzhufeng.ai.domain.NormalChatSendAttempt(
                id, snapshot.nodes.first().id, snapshot.conversation.id,
                com.nanzhufeng.ai.domain.ProviderId.OPENROUTER, "test-model", id.value,
                com.nanzhufeng.ai.domain.NormalChatSendAttemptStatus.COMPLETED, at, at,
            ),
        )
    }

    @Test fun `reused empty chat starts on first submission and later activity keeps that creation date`() {
        val blank = repository.save(service.create(autoTitlePending = true))
        val start = Instant.parse("2026-09-16T10:47:36.957Z")
        val sender = ConversationTreeService(Clock.fixed(start, ZoneOffset.UTC))
        val sent = repository.save(sender.append(blank, AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("question")))))
        assertEquals(start, sent.conversation.createdAt)
        val later = ConversationTreeService(Clock.fixed(start.plusSeconds(86400), ZoneOffset.UTC))
        val answered = repository.save(later.append(sent, AppendMessageRequest(MessageRole.ASSISTANT, listOf(ContentBlock.Text("answer")))))
        assertEquals(start, RoomConversationRepository(database).findById(answered.conversation.id)!!.conversation.createdAt)
        assertEquals(start.plusSeconds(86400), answered.conversation.updatedAt)
    }

    @Test fun `title logical revision survives repository recreation and non title writes`() {
        val original = repository.save(service.create("原标题"))
        val renamed = repository.save(original.copy(conversation = original.conversation.copy(title = "新标题")))
        assertEquals(1L, renamed.conversation.titleRevision)
        val pinned = repository.save(renamed.copy(conversation = renamed.conversation.copy(pinnedAt = clock.instant())))
        assertEquals(1L, pinned.conversation.titleRevision)
        assertEquals(1L, RoomConversationRepository(database).findById(original.conversation.id)!!.conversation.titleRevision)
        val remote = pinned.copy(conversation = pinned.conversation.copy(title = "远端标题", titleRevision = 7))
        assertEquals(7L, repository.saveVerifiedCloudMerge(remote).conversation.titleRevision)
        val next = repository.save(remote.copy(conversation = remote.conversation.copy(title = "再次改名")))
        assertEquals(8L, next.conversation.titleRevision)
    }

    @Test
    fun `new conversation append ordering idempotency and process rebuild keep one local truth`() {
        val created = service.create("Room 会话", projectId = "project-p3a")
        val withUser = service.append(created, AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("第一条"))))
        val snapshot = service.append(withUser, AppendMessageRequest(MessageRole.ASSISTANT, listOf(ContentBlock.Text("第二条"))))

        val saved = repository.save(snapshot)
        val recreated = RoomConversationRepository(database)

        assertEquals(snapshot, saved)
        assertEquals(snapshot, recreated.save(snapshot))
        assertEquals(snapshot, recreated.findById(snapshot.conversation.id))
        assertEquals(listOf("第一条", "第二条"), com.nanzhufeng.ai.domain.MessageTree(
            recreated.findById(snapshot.conversation.id)!!.conversation,
            recreated.findById(snapshot.conversation.id)!!.nodes,
        ).contextPath().map { (it.content.single() as ContentBlock.Text).text })
        assertEquals(listOf(snapshot.conversation), recreated.listActive())
    }

    @Test
    fun `K3 parsed continuation protocol survives Room without becoming visible text`() {
        val snapshot = service.append(
            service.append(service.create("K3 续接"), AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("继续")))),
            AppendMessageRequest(
                MessageRole.ASSISTANT,
                listOf(
                    ContentBlock.Reasoning("推理"),
                    ContentBlock.ProviderToolCall("call_1", "lookup", "{\"q\":\"K3\"}"),
                    ContentBlock.Text("结论"),
                ),
            ),
        )

        repository.save(snapshot)

        assertEquals(snapshot, RoomConversationRepository(database).findById(snapshot.conversation.id))
    }

    @Test
    fun `transaction failure rejects conflicting immutable node and leaves stored snapshot unchanged`() {
        val baseline = repository.save(
            service.append(service.create("事务合同"), AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("原文")))),
        )
        val original = baseline.nodes.single()
        val appended = service.append(
            baseline,
            AppendMessageRequest(MessageRole.ASSISTANT, listOf(ContentBlock.Text("如果事务失败不能留下我"))),
        )
        val conflicting = appended.copy(
            nodes = appended.nodes.map { node ->
                if (node.id == original.id) node.copy(content = listOf(ContentBlock.Text("非法改写"))) else node
            }.reversed(),
        )

        val failed = runCatching { repository.save(conflicting) }

        assertTrue(failed.isFailure)
        assertEquals(baseline, repository.findById(baseline.conversation.id))
    }

    @Test
    fun `verified cloud merge updates portable text with an existing message id`() {
        val baseline = repository.save(
            service.append(service.create("原标题"), AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("旧正文")))),
        )
        val remote = baseline.copy(
            conversation = baseline.conversation.copy(title = "云端新标题", revision = baseline.conversation.revision + 1),
            nodes = baseline.nodes.map { node -> node.copy(content = listOf(ContentBlock.Text("云端完整新正文"))) },
        )

        val restored = repository.saveVerifiedCloudMerge(remote)

        assertEquals("云端新标题", restored.conversation.title)
        assertEquals("云端完整新正文", (restored.nodes.single().content.single() as ContentBlock.Text).text)
        assertEquals(restored, repository.findById(restored.conversation.id))
    }

    @Test
    fun `verified cloud merge can swap existing sibling positions atomically`() {
        val root = service.append(service.create("分支更新"), AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("问题"))))
        val first = service.append(root, AppendMessageRequest(MessageRole.ASSISTANT, listOf(ContentBlock.Text("回答甲"))))
        val firstReply = first.nodes.last()
        val secondReply = firstReply.copy(id = com.nanzhufeng.ai.domain.MessageNodeId("second-reply"), siblingPosition = 1)
        val baseline = repository.save(first.copy(nodes = first.nodes + secondReply))
        val remote = baseline.copy(nodes = baseline.nodes.map { node ->
            if (node.parentMessageId == firstReply.parentMessageId) node.copy(siblingPosition = 1 - node.siblingPosition) else node
        })
        val saved = repository.saveVerifiedCloudMerge(remote)
        assertEquals(remote.nodes.associate { it.id to it.siblingPosition }, saved.nodes.associate { it.id to it.siblingPosition })
    }

    @Test
    fun `question only cloud snapshot cannot erase a completed local answer`() {
        val question = service.append(service.create("完整对话"), AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("问题"))))
        val complete = repository.save(service.append(question, AppendMessageRequest(MessageRole.ASSISTANT, listOf(ContentBlock.Text("完整回答")))))
        val partial = question.copy(conversation = question.conversation.copy(revision = complete.conversation.revision + 1))
        val merged = (mergeNewerRemoteConversation(complete, partial) as P7FExistingConversationMerge.Applied).snapshot
        assertEquals(2, merged.nodes.size)
        assertEquals(complete.conversation.currentLeafMessageId, merged.conversation.currentLeafMessageId)
        repository.saveVerifiedCloudMerge(merged)
        assertEquals(2, repository.findById(complete.conversation.id)!!.nodes.size)
    }

    @Test
    fun `accounted long answer crosses envelope decoder Room and footer without losing title or content`() {
        val baseline = repository.save(service.append(service.create("旧标题"),
            AppendMessageRequest(MessageRole.ASSISTANT, listOf(ContentBlock.Text("旧回答")))))
        val body = "完整回答不可截断。".repeat(800)
        val node = org.json.JSONObject().put("id", baseline.nodes.single().id.value)
            .put("parentMessageId", org.json.JSONObject.NULL).put("siblingPosition", 0)
            .put("role", "ASSISTANT").put("createdAtEpochMs", clock.millis())
            .put("deliveryState", "COMPLETE").put("revision", 1).put("revisesMessageId", org.json.JSONObject.NULL)
            .put("text", org.json.JSONArray().put(body))
            .put("modelUsage", org.json.JSONObject("""{"modelId":"deepseek-flash","modelDisplayName":"DS V4.1","inputTokens":12,"outputTokens":34,"totalTokens":46,"cachedInputTokens":null,"reasoningTokens":null,"costPriceVersion":"settled-v1","costCurrencyCode":"CNY","costTotalMicros":18700,"costSource":"PROVIDER_RESPONSE"}"""))
        val content = org.json.JSONObject().put("title", "DS V4.1 KFK 新标题")
            .put("titleRevision", 1)
            .put("currentLeafMessageId", baseline.nodes.single().id.value)
            .put("createdAtEpochMs", clock.millis()).put("updatedAtEpochMs", clock.millis() + 1000)
            .put("surface", "CHAT").put("nodes", org.json.JSONArray().put(node))
        val prepared = com.nanzhufeng.ai.domain.NfaiSyncPreparedSnapshot("com.nanzhufeng.ai", "conversation-integration", 2,
            listOf(com.nanzhufeng.ai.domain.NfaiSyncRecord("conversation", baseline.conversation.id.value, 2, "NORMAL", content.toString())))
        val recoveryCode = "room-contract-recovery".toCharArray()
        val sealed = com.nanzhufeng.ai.domain.NfaiSyncV1Gateway.seal(prepared, recoveryCode, ByteArray(32) { it.toByte() }) as com.nanzhufeng.ai.domain.NfaiSyncResult.Sealed
        val opened = com.nanzhufeng.ai.domain.NfaiSyncV1Gateway.open(sealed.canonicalEnvelope, recoveryCode, prepared.appId, prepared.documentId, 2) as com.nanzhufeng.ai.domain.NfaiSyncResult.Opened
        val decoded = P7FConversationSyncWireFormat.decodeWithModelUsage(opened.value.snapshot)
        database.runInTransaction {
            repository.saveVerifiedCloudMerge((mergeNewerRemoteConversation(baseline, decoded.snapshot) as P7FExistingConversationMerge.Applied).snapshot)
            decoded.modelUsageByAssistantMessage.values.forEach { value ->
                database.cloudResponseModelUsageDao().insert(com.nanzhufeng.ai.data.local.CloudResponseModelUsageEntity(
                    baseline.conversation.id.value, value.assistantMessageId.value, value.modelId, value.modelDisplayName,
                    value.usage.inputTokens, value.usage.outputTokens, value.usage.totalTokens, value.usage.cachedInputTokens,
                    value.usage.reasoningTokens, value.cost.priceVersion, value.cost.currencyCode, value.cost.totalMicros, value.costSource?.name))
            }
        }
        val saved = RoomConversationRepository(database).findById(baseline.conversation.id)!!
        assertEquals("DS V4.1 KFK 新标题", saved.conversation.title)
        assertEquals(body, (saved.nodes.single().content.single() as ContentBlock.Text).text)
        val usages = com.nanzhufeng.ai.data.local.RoomCloudResponseModelUsageStore(database).forMessages(saved.nodes.map { it.id })
        assertEquals(18700L, usages.values.single().single().cost.totalMicros)
        val footer = com.nanzhufeng.ai.domain.ConversationTranscriptPresentation(com.nanzhufeng.ai.domain.MessagePresentationRenderer())
            .render(saved.nodes, emptyList(), cloudResponseModelUsages = usages).single().metadata
        assertEquals("DS V4.1", footer.modelSnapshotLabel)
        assertTrue(footer.costLabel!!.contains("0.0187"))
    }

    @Test
    fun `cloud accounting fills unknown amount but never clears a settled amount`() {
        val store = com.nanzhufeng.ai.data.local.RoomCloudResponseModelUsageStore(database)
        val conversationId = com.nanzhufeng.ai.domain.ConversationId("cost-backfill")
        val messageId = com.nanzhufeng.ai.domain.MessageNodeId("cost-message")
        val unknown = com.nanzhufeng.ai.domain.CloudResponseModelUsage(messageId, "deepseek-flash", "DS V4.1")
        val settled = unknown.copy(cost = com.nanzhufeng.ai.domain.ProviderCost("original-v1", "CNY", 18700),
            costSource = com.nanzhufeng.ai.domain.ConversationCostSource.PROVIDER_RESPONSE)
        store.recordVerified(conversationId, listOf(unknown))
        store.recordVerified(conversationId, listOf(settled))
        assertEquals(18700L, store.forMessages(listOf(messageId)).getValue(messageId).single().cost.totalMicros)
        store.recordVerified(conversationId, listOf(unknown))
        assertEquals(settled, store.forMessages(listOf(messageId)).getValue(messageId).single())
    }

    @Test
    fun `newer cloud body survives local upload merge and Room readback with reused message revision`() {
        val local = repository.save(service.append(service.create("连续同步"),
            AppendMessageRequest(MessageRole.ASSISTANT, listOf(ContentBlock.Text("旧正文")))))
        val remote = local.copy(conversation = local.conversation.copy(updatedAt = local.conversation.updatedAt.plusSeconds(10)),
            nodes = local.nodes.map { it.copy(content = listOf(ContentBlock.Text("最新完整正文"))) })
        val merged = mergeRemoteAdditionsForLocalCommit(local, remote)
        repository.saveVerifiedCloudMerge(merged)
        assertEquals("最新完整正文", (repository.findById(local.conversation.id)!!.nodes.single().content.single() as ContentBlock.Text).text)
        val reversed = mergeRemoteAdditionsForLocalCommit(remote, local)
        assertEquals("最新完整正文", (reversed.nodes.single().content.single() as ContentBlock.Text).text)
    }

    @Test
    fun `schema three to four retains P2 capture knowledge candidate and ledger rows`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "p3a-migration-${UUID.randomUUID()}.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE capture_drafts (id TEXT NOT NULL PRIMARY KEY, text TEXT, createdAtEpochMs INTEGER NOT NULL, schemaVersion INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE knowledge_items (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, body TEXT NOT NULL, candidateId TEXT NOT NULL, invocationId TEXT NOT NULL, providerId TEXT NOT NULL, modelId TEXT NOT NULL, harnessVersion INTEGER NOT NULL, createdAtEpochMs INTEGER NOT NULL, schemaVersion INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE generated_candidates (id TEXT NOT NULL PRIMARY KEY, taskId TEXT NOT NULL, draftId TEXT NOT NULL, invocationId TEXT NOT NULL, providerId TEXT NOT NULL, modelId TEXT NOT NULL, harnessVersion INTEGER NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, generatedAtEpochMs INTEGER NOT NULL, schemaVersion INTEGER NOT NULL, status TEXT NOT NULL, updatedAtEpochMs INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE invocation_records (id TEXT NOT NULL PRIMARY KEY, taskId TEXT NOT NULL, providerId TEXT NOT NULL, modelId TEXT NOT NULL, harnessVersion INTEGER NOT NULL, completedAtEpochMs INTEGER NOT NULL, status TEXT NOT NULL, errorCode TEXT, registrySnapshotId TEXT, pricingVersion TEXT, inputTokens INTEGER, outputTokens INTEGER, totalTokens INTEGER, cachedInputTokens INTEGER, costPriceVersion TEXT, costCurrencyCode TEXT, costTotalMicros INTEGER)")
                }

                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build(),
        )
        val sqlite = helper.writableDatabase
        sqlite.execSQL("INSERT INTO capture_drafts VALUES ('draft-p2', 'P2 草稿', 1, 1)")
        sqlite.execSQL("INSERT INTO knowledge_items VALUES ('knowledge-p2', '标题', '正文', 'candidate-p2', 'invocation-p2', 'MOCK', 'mock', 1, 1, 1)")
        sqlite.execSQL("INSERT INTO generated_candidates VALUES ('candidate-p2', 'task', 'draft-p2', 'invocation-p2', 'MOCK', 'mock', 1, '标题', '正文', 1, 1, 'PENDING_REVIEW', 1)")
        sqlite.execSQL("INSERT INTO invocation_records VALUES ('invocation-p2', 'task', 'MOCK', 'mock', 1, 1, 'SUCCEEDED', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)")

        NanfengAiDatabase.MIGRATION_3_4.migrate(sqlite)

        listOf("capture_drafts", "knowledge_items", "generated_candidates", "invocation_records").forEach { table ->
            sqlite.query("SELECT COUNT(*) FROM $table").use { cursor -> assertTrue("迁移丢失 P2 表：$table", cursor.moveToFirst() && cursor.getInt(0) == 1) }
        }
        listOf("conversations", "message_nodes", "message_content_blocks", "conversation_drafts", "conversation_memory_sources").forEach { table ->
            sqlite.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'").use { cursor -> assertTrue("迁移缺少 P3-A 表：$table", cursor.moveToFirst()) }
        }
        helper.close()
        context.deleteDatabase(name)
    }

    @Test
    fun `conversation schema keeps content separate from ledger and excludes secret transport columns`() {
        val forbidden = setOf("apikey", "authorization", "prompt", "response", "credential", "costtotal", "inputtokens", "outputtokens")
        val tables = listOf("conversations", "message_nodes", "message_content_blocks", "conversation_drafts", "conversation_memory_sources")

        tables.flatMap { table ->
            database.openHelper.writableDatabase.query("PRAGMA table_info($table)").use { cursor ->
                generateSequence { if (cursor.moveToNext()) cursor.getString(cursor.getColumnIndexOrThrow("name")) else null }.toList()
            }
        }.forEach { column -> assertFalse("P3-A 会话表不能复制敏感运行字段：$column", forbidden.any { column.contains(it, ignoreCase = true) }) }
        assertNotNull(database.conversationDao())
    }
}
