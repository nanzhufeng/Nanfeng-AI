package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomConversationRepository
import com.nanzhufeng.ai.domain.*
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class P3EConversationManagementExportContractsTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-13T12:00:00Z"), ZoneOffset.UTC)
    private lateinit var database: NanfengAiDatabase
    private lateinit var repository: RoomConversationRepository
    private lateinit var tree: ConversationTreeService
    private lateinit var management: ManageConversationUseCase

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NanfengAiDatabase::class.java).allowMainThreadQueries().build()
        repository = RoomConversationRepository(database)
        tree = ConversationTreeService(clock)
        management = ManageConversationUseCase(ConversationManagementDomain(clock), repository)
    }
    @After fun close() = database.close()

    @Test fun `rename uses code-point boundary and persisted intent handles replay conflict and rebuild`() {
        val saved = repository.save(tree.create("原名"))
        val intent = ConversationManagementIntent(ConversationManagementIntentId("rename-1"), saved.conversation.id, ConversationManagementAction.RENAME, saved.conversation.revision, "  中文 Cafe  ")
        val first = management.execute(intent) as ConversationManagementResult.Applied
        assertEquals("中文 Cafe", first.snapshot.conversation.title)
        assertTrue(management.execute(intent) is ConversationManagementResult.Replayed)
        assertTrue(management.execute(intent.copy(title = "另一标题")) is ConversationManagementResult.Rejected)
        assertEquals("中文 Cafe", RoomConversationRepository(database).findById(saved.conversation.id)!!.conversation.title)
        assertTrue(management.execute(intent.copy(id = ConversationManagementIntentId("blank"), title = " \t ")) is ConversationManagementResult.Rejected)
        assertTrue(management.execute(intent.copy(id = ConversationManagementIntentId("long"), title = "😀".repeat(121))) is ConversationManagementResult.Rejected)
    }

    @Test fun `pin archive ordering and complete branch search survive rebuild`() {
        val a = repository.save(tree.append(tree.create("Alpha"), AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("visible English markdown `code`")))))
        val b = repository.save(tree.create("中文会话"))
        management.execute(ConversationManagementIntent(ConversationManagementIntentId("pin"), b.conversation.id, ConversationManagementAction.PIN, b.conversation.revision))
        assertEquals(listOf(b.conversation.id, a.conversation.id), repository.list(ConversationListScope.ACTIVE).map { it.id })
        management.execute(ConversationManagementIntent(ConversationManagementIntentId("archive"), a.conversation.id, ConversationManagementAction.ARCHIVE, a.conversation.revision))
        assertEquals(listOf(b.conversation.id), RoomConversationRepository(database).list(ConversationListScope.ACTIVE).map { it.id })
        assertEquals(listOf(a.conversation.id), RoomConversationRepository(database).list(ConversationListScope.ARCHIVED).map { it.id })

        val base = tree.append(tree.create("分支"), AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("可见中文正文"))))
        val answered = tree.append(base, AppendMessageRequest(MessageRole.ASSISTANT, listOf(ContentBlock.Text("隐藏兄弟 needle"))))
        val revised = tree.editUserMessage(answered, base.conversation.currentLeafMessageId!!, listOf(ContentBlock.Text("当前路径文本")))
        repository.save(revised)
        val search = SearchConversationsUseCase(repository, ConversationSearchProjection(ConversationManagementDomain(clock)))
        assertTrue(search.execute("当前路径", ConversationListScope.ACTIVE).any { it.conversationId == revised.conversation.id })
        val hiddenBranchHit = search.execute("needle", ConversationListScope.ACTIVE).single { it.conversationId == revised.conversation.id }
        assertEquals(answered.conversation.currentLeafMessageId, hiddenBranchHit.messageNodeId)
        assertEquals(answered.conversation.currentLeafMessageId, conversationSearchLeafForMessage(revised, requireNotNull(hiddenBranchHit.messageNodeId)))
        assertTrue(search.execute("alpha", ConversationListScope.ARCHIVED).any { it.conversationId == a.conversation.id })
    }

    @Test fun `favorite is durable independent of pin and is cleared by archive`() {
        val saved = repository.save(tree.create("收藏会话"))
        val favorited = management.execute(
            ConversationManagementIntent(ConversationManagementIntentId("favorite"), saved.conversation.id, ConversationManagementAction.FAVORITE, saved.conversation.revision),
        ) as ConversationManagementResult.Applied
        assertTrue(favorited.snapshot.conversation.favoritedAt != null)
        assertEquals(listOf(saved.conversation.id), repository.list(ConversationListScope.FAVORITES).map { it.id })

        val archived = management.execute(
            ConversationManagementIntent(ConversationManagementIntentId("favorite-archive"), saved.conversation.id, ConversationManagementAction.ARCHIVE, favorited.snapshot.conversation.revision),
        ) as ConversationManagementResult.Applied
        assertEquals(null, archived.snapshot.conversation.favoritedAt)
        assertTrue(repository.list(ConversationListScope.FAVORITES).isEmpty())
    }

    @Test fun `schema fifty three to fifty four adds a nullable local favorite timestamp`() {
        val context = ApplicationProvider.getApplicationContext<Context>(); val name = "favorite-${UUID.randomUUID()}.db"; context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(53) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE conversations (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL)")
            }
            override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build())
        val sqlite = helper.writableDatabase
        NanfengAiDatabase.MIGRATION_53_54.migrate(sqlite)
        sqlite.query("PRAGMA table_info(conversations)").use { columns ->
            assertTrue(generateSequence { if (columns.moveToNext()) columns.getString(1) else null }.contains("favoritedAtEpochMs"))
        }
        helper.close(); context.deleteDatabase(name)
    }

    @Test fun `six range attachment search stays local and returns only current path attachments`() {
        val image = ConversationAttachmentReference(AttachmentId("image"), "image/png", "roadmap.png", 3, "a".repeat(64))
        val audio = ConversationAttachmentReference(AttachmentId("audio"), "audio/mpeg", "review.mp3", 3, "b".repeat(64))
        val base = tree.append(tree.create("产品路线图"), AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Attachment(image))))
        val hidden = tree.append(base, AppendMessageRequest(MessageRole.ASSISTANT, listOf(ContentBlock.Attachment(audio))))
        val current = tree.editUserMessage(hidden, base.conversation.currentLeafMessageId!!, listOf(ContentBlock.Text("当前正文"), ContentBlock.Attachment(image)))
        repository.save(current)
        val search = SearchConversationAttachmentsUseCase(repository)

        assertEquals(listOf(image.id), search.execute("roadmap", ConversationSearchCategory.IMAGE, ConversationListScope.ACTIVE).map { it.attachment.id })
        assertTrue(search.execute("review", ConversationSearchCategory.AUDIO, ConversationListScope.ACTIVE).isEmpty())
        assertEquals(listOf(image.id), search.execute("路线图", ConversationSearchCategory.ALL, ConversationListScope.ACTIVE).map { it.attachment.id })
    }

    @Test fun `every attachment category matches the owning message context without crossing category boundaries`() {
        val attachments = listOf(
            ConversationAttachmentReference(AttachmentId("context-image"), "image/png", "chart.png", 3, "1".repeat(64)),
            ConversationAttachmentReference(AttachmentId("context-video"), "video/mp4", "clip.mp4", 3, "2".repeat(64)),
            ConversationAttachmentReference(AttachmentId("context-audio"), "audio/mpeg", "voice.mp3", 3, "3".repeat(64)),
            ConversationAttachmentReference(AttachmentId("context-file"), "application/pdf", "brief.pdf", 3, "4".repeat(64)),
        )
        repository.save(
            tree.append(
                tree.create("分组搜索"),
                AppendMessageRequest(
                    MessageRole.USER,
                    listOf(ContentBlock.Text("这是统一关键词的上下文")) + attachments.map { ContentBlock.Attachment(it) },
                ),
            ),
        )
        val search = SearchConversationAttachmentsUseCase(repository)

        val expectedByCategory = mapOf(
            ConversationSearchCategory.IMAGE to attachments[0].id,
            ConversationSearchCategory.VIDEO to attachments[1].id,
            ConversationSearchCategory.AUDIO to attachments[2].id,
            ConversationSearchCategory.FILE to attachments[3].id,
        )
        expectedByCategory.forEach { (category, expectedId) ->
            val hit = search.execute("统一关键词", category, ConversationListScope.ACTIVE).single()
            assertEquals(expectedId, hit.attachment.id)
            assertTrue(hit.matchSnippet.orEmpty().contains("统一关键词"))
        }
        assertEquals(attachments.map { it.id }.toSet(), search.execute("统一关键词", ConversationSearchCategory.ALL, ConversationListScope.ACTIVE).map { it.attachment.id }.toSet())
        assertTrue(search.execute("统一关键词", ConversationSearchCategory.TEXT, ConversationListScope.ACTIVE).isEmpty())
    }

    @Test fun `opening search browses local conversations and category attachments before a keyword is entered`() {
        val image = ConversationAttachmentReference(AttachmentId("browse-image"), "image/png", "diagram.png", 3, "c".repeat(64))
        val saved = repository.save(tree.append(tree.create("搜索可浏览会话"), AppendMessageRequest(MessageRole.ASSISTANT, listOf(ContentBlock.Text("可直接浏览的正文"), ContentBlock.Attachment(image)))))
        val textSearch = SearchConversationsUseCase(repository, ConversationSearchProjection(ConversationManagementDomain(clock)))
        val attachmentSearch = SearchConversationAttachmentsUseCase(repository)

        assertEquals(listOf(saved.conversation.id), textSearch.browse(ConversationListScope.ACTIVE).map { it.conversationId })
        assertEquals(listOf(image.id), attachmentSearch.browse(ConversationSearchCategory.IMAGE, ConversationListScope.ACTIVE).map { it.attachment.id })
        assertEquals(saved.nodes.last().createdAt.toEpochMilli(), attachmentSearch.browse(ConversationSearchCategory.IMAGE, ConversationListScope.ACTIVE).single().timestampEpochMs)
    }

    @Test fun `正文目录使用真实本机文本时间和 UTF8 字节数`() {
        val body = "中文正文 abc"
        val saved = repository.save(
            tree.append(tree.create("正文事实"), AppendMessageRequest(MessageRole.ASSISTANT, listOf(ContentBlock.Text(body)))),
        )
        val search = SearchConversationsUseCase(repository, ConversationSearchProjection(ConversationManagementDomain(clock)))

        val browseHit = search.browse(ConversationListScope.ACTIVE).single { it.conversationId == saved.conversation.id }
        val queryHit = search.execute("正文", ConversationListScope.ACTIVE).single { it.conversationId == saved.conversation.id && it.messageNodeId != null }
        val expectedBytes = body.toByteArray(Charsets.UTF_8).size.toLong()
        val expectedTimestamp = saved.nodes.last().createdAt.toEpochMilli()
        assertEquals(expectedBytes, browseHit.byteCount)
        assertEquals(expectedTimestamp, browseHit.timestampEpochMs)
        assertEquals(expectedBytes, queryHit.byteCount)
        assertEquals(expectedTimestamp, queryHit.timestampEpochMs)
    }

    @Test fun `attachment catalogue never truncates valid local occurrences at fifty`() {
        var snapshot = tree.create("全量附件目录")
        repeat(75) { index ->
            val image = ConversationAttachmentReference(
                AttachmentId("catalog-$index"), "image/png", "image-$index.png", 3, index.toString().padStart(64, 'a').takeLast(64),
            )
            snapshot = tree.append(snapshot, AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Attachment(image))))
        }
        repository.save(snapshot)

        assertEquals(75, SearchConversationAttachmentsUseCase(repository).browse(ConversationSearchCategory.IMAGE, ConversationListScope.ACTIVE).size)
    }

    @Test fun `nonempty but stale local index never hides another current message match`() {
        val first = repository.save(tree.append(tree.create("第一条"), AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("共同关键词")))))
        val second = repository.save(tree.append(tree.create("第二条"), AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("共同关键词")))))
        database.conversationDao().deleteSearchIndexForConversation(second.conversation.id.value)
        val search = SearchConversationsUseCase(repository, ConversationSearchProjection(ConversationManagementDomain(clock)))

        assertEquals(
            setOf(first.conversation.id, second.conversation.id),
            search.execute("共同关键词", ConversationListScope.ACTIVE).map { it.conversationId }.toSet(),
        )
    }

    @Test fun `sidebar lifecycle clears pin rejects archived pin and survives room reopen`() {
        val saved = repository.save(tree.create("侧栏可恢复会话"))
        val pinned = management.execute(
            ConversationManagementIntent(ConversationManagementIntentId("sidebar-pin"), saved.conversation.id, ConversationManagementAction.PIN, saved.conversation.revision),
        ) as ConversationManagementResult.Applied
        assertTrue(pinned.snapshot.conversation.pinnedAt != null)
        assertEquals(2L, pinned.snapshot.conversation.revision)

        val archived = management.execute(
            ConversationManagementIntent(ConversationManagementIntentId("sidebar-archive"), saved.conversation.id, ConversationManagementAction.ARCHIVE, pinned.snapshot.conversation.revision),
        ) as ConversationManagementResult.Applied
        assertTrue(archived.snapshot.conversation.archivedAt != null)
        assertEquals(null, archived.snapshot.conversation.pinnedAt)
        assertEquals(3L, archived.snapshot.conversation.revision)
        assertTrue(management.execute(
            ConversationManagementIntent(ConversationManagementIntentId("archived-pin"), saved.conversation.id, ConversationManagementAction.PIN, archived.snapshot.conversation.revision),
        ) is ConversationManagementResult.Rejected)
        assertTrue(management.execute(
            ConversationManagementIntent(ConversationManagementIntentId("stale-restore"), saved.conversation.id, ConversationManagementAction.UNARCHIVE, 2L),
        ) is ConversationManagementResult.Rejected)

        val restored = management.execute(
            ConversationManagementIntent(ConversationManagementIntentId("sidebar-restore"), saved.conversation.id, ConversationManagementAction.UNARCHIVE, archived.snapshot.conversation.revision),
        ) as ConversationManagementResult.Applied
        val reopened = RoomConversationRepository(database).findById(saved.conversation.id)!!.conversation
        assertEquals(restored.snapshot.conversation, reopened)
        assertEquals(null, reopened.archivedAt)
        assertEquals(null, reopened.pinnedAt)
        assertEquals(4L, reopened.revision)
    }

    @Test fun `soft delete has its own recoverable lifecycle and does not remove the message tree`() {
        val saved = repository.save(tree.append(tree.create("可恢复删除"), AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("保留消息")))))
        val deleted = management.execute(
            ConversationManagementIntent(ConversationManagementIntentId("soft-delete"), saved.conversation.id, ConversationManagementAction.SOFT_DELETE, saved.conversation.revision),
        ) as ConversationManagementResult.Applied
        assertTrue(deleted.snapshot.conversation.deletedAt != null)
        assertEquals(1, deleted.snapshot.nodes.size)
        assertEquals(emptyList<ConversationId>(), repository.list(ConversationListScope.ACTIVE).map { it.id })
        assertEquals(listOf(saved.conversation.id), repository.list(ConversationListScope.DELETED).map { it.id })
        val restored = management.execute(
            ConversationManagementIntent(ConversationManagementIntentId("restore-deleted"), saved.conversation.id, ConversationManagementAction.RESTORE_DELETED, deleted.snapshot.conversation.revision),
        ) as ConversationManagementResult.Applied
        assertEquals(null, restored.snapshot.conversation.deletedAt)
        assertEquals(1, RoomConversationRepository(database).findById(saved.conversation.id)!!.nodes.size)
    }

    @Test fun `current-path export is atomic readable hashed and excludes hidden branch paths and secrets`() {
        val attachment = AttachmentReference("attachments/v1/safe", "image/png", "secret-name.png", AttachmentId("att"), 3, "a".repeat(64))
        val created = tree.create("导出会话")
        val user = tree.append(created, AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("用户拥有的正文"), ContentBlock.Attachment(attachment))))
        val hidden = tree.append(user, AppendMessageRequest(MessageRole.ASSISTANT, listOf(ContentBlock.Text("隐藏回答 SECRET_TOKEN"))))
        val current = tree.editUserMessage(hidden, user.conversation.currentLeafMessageId!!, listOf(ContentBlock.Text("当前分支正文")))
        val saved = repository.save(current)
        val root = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "p3e-export-${UUID.randomUUID()}")
        val store = AndroidConversationExportStore(root)
        val result = ExportConversationPackageUseCase(repository, store, clock).execute(saved.conversation.id) as ConversationExportResult.Success
        assertEquals("current-path-only", result.export.payload.scope)
        assertTrue(result.export.payload.messages.flatMap { it.content }.any { it.text == "当前分支正文" })
        assertFalse(result.export.payload.messages.flatMap { it.content }.any { it.text?.contains("SECRET_TOKEN") == true })
        val encoded = result.export.payload.toString()
        assertFalse(encoded.contains("secret-name.txt"))
        assertEquals(result.export.fileName, (store.latestVerified() as ConversationExportResult.Success).export.fileName)
        File(root, result.export.fileName).writeText("tamper")
        assertTrue(store.verify(File(root, result.export.fileName)) !is ConversationExportResult.Success)
        root.deleteRecursively()
    }

    @Test fun `schema six to seven retains conversations and adds management intent fact`() {
        val context = ApplicationProvider.getApplicationContext<Context>(); val name = "p3e-${UUID.randomUUID()}.db"; context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(6) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) { db.execSQL("CREATE TABLE conversations (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, projectId TEXT, currentLeafMessageId TEXT, createdAtEpochMs INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL, defaultProviderId TEXT, defaultModelId TEXT, harnessId TEXT, harnessVersion INTEGER, contextPolicyVersion INTEGER NOT NULL, archivedAtEpochMs INTEGER, pinnedAtEpochMs INTEGER, deletedAtEpochMs INTEGER, schemaVersion INTEGER NOT NULL)"); db.execSQL("INSERT INTO conversations VALUES ('c', '旧会话', NULL, NULL, 1, 1, NULL, NULL, NULL, NULL, 1, NULL, NULL, NULL, 1)") }
            override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build())
        val sqlite = helper.writableDatabase; NanfengAiDatabase.MIGRATION_6_7.migrate(sqlite)
        sqlite.query("SELECT title FROM conversations WHERE id='c'").use { assertTrue(it.moveToFirst()); assertEquals("旧会话", it.getString(0)) }
        sqlite.query("SELECT name FROM sqlite_master WHERE type='table' AND name='conversation_management_intents'").use { assertTrue(it.moveToFirst()) }
        helper.close(); context.deleteDatabase(name)
    }
}
