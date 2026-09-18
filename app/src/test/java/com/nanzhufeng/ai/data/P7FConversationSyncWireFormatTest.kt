package com.nanzhufeng.ai.data

import com.nanzhufeng.ai.domain.NfaiSyncPreparedSnapshot
import com.nanzhufeng.ai.domain.NfaiSyncRecord
import com.nanzhufeng.ai.domain.AttachmentId
import com.nanzhufeng.ai.domain.ContentBlock
import com.nanzhufeng.ai.domain.ConversationAttachmentReference
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.MessageNode
import com.nanzhufeng.ai.domain.MessageNodeId
import com.nanzhufeng.ai.domain.MessageRole
import com.nanzhufeng.ai.domain.CloudResponseModelUsage
import com.nanzhufeng.ai.domain.ConversationCostSource
import com.nanzhufeng.ai.domain.ProviderCost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class P7FConversationSyncWireFormatTest {
    @Test fun `both platform wires preserve area with identical conversation and message ids`() {
        val fixture = listOf(java.io.File("../protocol/fixtures/data-area-sync-v1.json"), java.io.File("protocol/fixtures/data-area-sync-v1.json")).first { it.isFile }
        val vectors = org.json.JSONArray(fixture.readText())
        repeat(vectors.length()) { index ->
            val vector = vectors.getJSONObject(index)
            val area = com.nanzhufeng.ai.domain.ConversationSurface.valueOf(vector.getString("area"))
            val id = vector.getString("conversationId")
            assertEquals(vector.getString("documentId"), com.nanzhufeng.ai.domain.ConversationDataArea.cloudDocumentId(area, id))
            val decoded = P7FConversationSyncWireFormat.decode(NfaiSyncPreparedSnapshot("com.nanzhufeng.ai", vector.getString("documentId"), 1,
                listOf(NfaiSyncRecord("conversation", id, 1, "NORMAL", vector.getJSONObject("content").toString()))))
            assertEquals(area, decoded.conversation.surface)
            assertEquals(id, decoded.conversation.id.value)
            assertEquals("m1", decoded.nodes.single().id.value)
        }
    }

    @Test fun `shared cross platform title vectors agree on read and upload`() {
        val fixture = listOf(java.io.File("../protocol/fixtures/title-sync-v1.json"), java.io.File("protocol/fixtures/title-sync-v1.json"))
            .first { it.isFile }.readText()
        val vectors = org.json.JSONArray(fixture)
        val service = com.nanzhufeng.ai.domain.ConversationTreeService(java.time.Clock.fixed(Instant.EPOCH, java.time.ZoneOffset.UTC))
        val base = service.append(service.create("Fixture"), com.nanzhufeng.ai.domain.AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("Fixture message"))))
        repeat(vectors.length()) { index ->
            val vector = vectors.getJSONObject(index)
            fun snapshot(key: String): com.nanzhufeng.ai.domain.ConversationSnapshot {
                val value = vector.getJSONObject(key)
                return base.copy(conversation = base.conversation.copy(title = value.getString("title"),
                    titleRevision = if (value.isNull("titleRevision")) null else value.getLong("titleRevision"), updatedAt = Instant.parse(value.getString("updatedAt"))))
            }
            val local = snapshot("local"); val remote = snapshot("remote")
            val results = listOf(runCatching { mergeRemoteAdditionsForLocalCommit(local, remote) }, runCatching {
                when(val merged = mergeNewerRemoteConversation(local, remote)) {
                    is P7FExistingConversationMerge.Applied -> merged.snapshot
                    P7FExistingConversationMerge.Unchanged -> local
                }
            })
            for (result in results) {
                if (vector.optBoolean("conflict")) assertTrue(vector.getString("name"), result.exceptionOrNull()?.message?.contains("标题版本冲突") == true)
                else {
                    assertEquals(vector.getString("name"), vector.getString("expectedTitle"), result.getOrThrow().conversation.title)
                    assertEquals(vector.getLong("expectedRevision"), result.getOrThrow().conversation.titleRevision)
                }
            }
        }
    }
    @Test fun `packing a synced answer keeps its provider settlement ahead of a local estimate`() {
        val localEstimate = CloudResponseModelUsage(
            assistantMessageId = MessageNodeId("settled-answer"),
            modelId = "openai/gpt-6-astra",
            modelDisplayName = "GPT-6 Astra",
            cost = ProviderCost("local-price-v1", "USD", 18),
            costSource = ConversationCostSource.LOCAL_ESTIMATE,
        )
        val providerSettlement = localEstimate.copy(
            cost = ProviderCost("provider-response-v1", "USD", 56),
            costSource = ConversationCostSource.PROVIDER_RESPONSE,
        )

        val packed = portableUsageForCloud(localEstimate, providerSettlement)

        assertEquals(56L, packed?.cost?.totalMicros)
        assertEquals("provider-response-v1", packed?.cost?.priceVersion)
        assertEquals(ConversationCostSource.PROVIDER_RESPONSE, packed?.costSource)
    }

    @Test fun `legacy token only DeepSeek answer retains the desktop historical displayed amount`() {
        val decoded = P7FConversationSyncWireFormat.decodeWithModelUsage(NfaiSyncPreparedSnapshot(
            "com.nanzhufeng.ai", "conversation-historical-cost", 1,
            listOf(NfaiSyncRecord("conversation", "historical-cost", 1, "NORMAL", """
                {"title":"历史金额","currentLeafMessageId":"m1","createdAtEpochMs":1789312922263,"updatedAtEpochMs":1789312922263,"surface":"CHAT","nodes":[
                {"id":"m1","parentMessageId":null,"siblingPosition":0,"role":"ASSISTANT","createdAtEpochMs":1789226522263,"deliveryState":"COMPLETE","revision":1,"revisesMessageId":null,"text":["回答"],
                "modelUsage":{"modelId":"deepseek-flash","modelDisplayName":"DS V4.1","inputTokens":5083,"outputTokens":3361,"totalTokens":8444,"cachedInputTokens":0,"reasoningTokens":null,"costPriceVersion":null,"costCurrencyCode":null,"costTotalMicros":null,"costSource":null}}]}
            """.trimIndent())),
        ))
        val usage = decoded.modelUsageByAssistantMessage.values.single()
        assertEquals(2779L, usage.cost.totalMicros)
        assertEquals("USD", usage.cost.currencyCode)
        assertEquals(com.nanzhufeng.ai.domain.ConversationCostSource.LOCAL_ESTIMATE, usage.costSource)
        // Android retains six decimals; Desktop rounds this same amount to four.
        assertEquals("¥0.018676", usage.footerCostLabel())
    }

    @Test fun `portable projection keeps text conversation while leaving local attachment previews out of cloud`() {
        val conversationId = ConversationId("attachment-portable")
        val root = MessageNode(
            id = MessageNodeId("m1"), conversationId = conversationId, parentMessageId = null,
            siblingPosition = 0, role = MessageRole.USER,
            content = listOf(
                ContentBlock.Text("保留同步文本"),
                ContentBlock.Attachment(ConversationAttachmentReference(AttachmentId("local-preview"), "image/png", "预览图.png", 12, "a".repeat(64))),
            ),
            createdAt = Instant.ofEpochMilli(1_000),
        )
        val reply = MessageNode(
            id = MessageNodeId("m2"), conversationId = conversationId, parentMessageId = root.id,
            siblingPosition = 0, role = MessageRole.ASSISTANT, content = listOf(ContentBlock.Text("保留后续回答")),
            createdAt = Instant.ofEpochMilli(2_000),
        )
        val attachmentOnly = MessageNode(
            id = MessageNodeId("m3"), conversationId = conversationId, parentMessageId = reply.id,
            siblingPosition = 0, role = MessageRole.USER,
            content = listOf(ContentBlock.Attachment(ConversationAttachmentReference(AttachmentId("local-only"), "image/png", "仅本机.png", 12, "b".repeat(64)))),
            createdAt = Instant.ofEpochMilli(3_000),
        )
        val afterAttachment = MessageNode(
            id = MessageNodeId("m4"), conversationId = conversationId, parentMessageId = attachmentOnly.id,
            siblingPosition = 0, role = MessageRole.ASSISTANT,
            content = listOf(ContentBlock.Text("图片后的回答也必须同步")),
            createdAt = Instant.ofEpochMilli(4_000),
        )

        val portable = portableCompletedTextNodes(listOf(root, reply, attachmentOnly, afterAttachment))

        assertEquals(listOf("m1", "m2", "m4"), portable.map { it.id.value })
        assertEquals(MessageNodeId("m2"), portable.last().parentMessageId)
        assertTrue(portable.all { node -> node.content.all { it is ContentBlock.Text } })
        assertTrue(root.content.any { it is ContentBlock.Attachment })
    }

    @Test fun `cloud read fills an empty local shell without importing cloud pin state`() {
        val remote = P7FConversationSyncWireFormat.decode(NfaiSyncPreparedSnapshot(
            appId = "com.nanzhufeng.ai",
            documentId = "conversation-0123456789012345678901234567890123456789",
            revision = 1,
            records = listOf(NfaiSyncRecord("conversation", "shell-1", 1, "NORMAL", """
                {"title":"远端完整会话","currentLeafMessageId":"m1","createdAtEpochMs":1000,"updatedAtEpochMs":2000,"surface":"CHAT","nodes":[
                  {"id":"m1","parentMessageId":null,"siblingPosition":0,"role":"USER","createdAtEpochMs":1000,"deliveryState":"COMPLETE","revision":1,"revisesMessageId":null,"text":["内容"]}
                ]}
            """.trimIndent())),
        ))
        val localPin = Instant.ofEpochMilli(9_999)
        val emptyLocal = remote.copy(
            conversation = remote.conversation.copy(
                currentLeafMessageId = null,
                pinnedAt = localPin,
                projectId = "local-project",
            ),
            nodes = emptyList(),
        )

        val merged = mergeRemoteIntoEmptyLocalConversation(emptyLocal, remote)

        assertEquals(1, merged.nodes.size)
        assertEquals("m1", merged.conversation.currentLeafMessageId?.value)
        assertEquals(localPin, merged.conversation.pinnedAt)
        assertEquals("local-project", merged.conversation.projectId)
        assertEquals(remote, mergeRemoteIntoEmptyLocalConversation(remote, emptyLocal))
    }

    @Test fun `verified cloud snapshot replaces stale portable tree even when the sender revision lags`() {
        val existing = P7FConversationSyncWireFormat.decode(NfaiSyncPreparedSnapshot(
            appId = "com.nanzhufeng.ai",
            documentId = "conversation-0123456789012345678901234567890123456789",
            revision = 1,
            records = listOf(NfaiSyncRecord("conversation", "title-update", 3, "NORMAL", """
                {"title":"手机旧标题","currentLeafMessageId":"m1","createdAtEpochMs":1000,"updatedAtEpochMs":2000,"surface":"CHAT","nodes":[
                  {"id":"m1","parentMessageId":null,"siblingPosition":0,"role":"USER","createdAtEpochMs":1000,"deliveryState":"COMPLETE","revision":1,"revisesMessageId":null,"text":["手机消息"]}
                ]}
            """.trimIndent())),
        ))
        val remoteMessage = existing.nodes.single().copy(
            id = MessageNodeId("m2"),
            parentMessageId = MessageNodeId("m1"),
            content = listOf(ContentBlock.Text("电脑端新增消息")),
            createdAt = Instant.ofEpochMilli(3_000),
        )
        val remote = existing.copy(
            conversation = existing.conversation.copy(
                title = "电脑端新标题",
                titleRevision = 1,
                currentLeafMessageId = remoteMessage.id,
                updatedAt = Instant.ofEpochMilli(3_000),
                // An interrupted older client can reuse this semantic revision;
                // the verified cloud read must still update stale text instead
                // of leaving a permanent conflict.
                revision = 2,
            ),
            nodes = existing.nodes + remoteMessage,
        )

        val merged = mergeNewerRemoteConversation(existing, remote) as P7FExistingConversationMerge.Applied

        assertEquals("电脑端新标题", merged.snapshot.conversation.title)
        assertEquals(3L, merged.snapshot.conversation.revision)
        assertEquals(2, merged.snapshot.nodes.size)
        assertEquals("电脑端新增消息", (merged.snapshot.nodes.last().content.single() as ContentBlock.Text).text)
        assertEquals(remoteMessage.id, merged.snapshot.conversation.currentLeafMessageId)

        // Reverse direction: reading the older server snapshot must not undo
        // a rename which is still waiting for upload on this device.
        val reverse = mergeNewerRemoteConversation(remote, existing) as P7FExistingConversationMerge.Applied
        assertEquals("电脑端新标题", reverse.snapshot.conversation.title)
        assertEquals(Instant.ofEpochMilli(3_000), reverse.snapshot.conversation.updatedAt)
    }

    @Test fun `local upload retains a remote turn created after its last receipt`() {
        val local = P7FConversationSyncWireFormat.decode(NfaiSyncPreparedSnapshot(
            appId = "com.nanzhufeng.ai", documentId = "conversation-0123456789012345678901234567890123456789", revision = 2,
            records = listOf(NfaiSyncRecord("conversation", "union-1", 2, "NORMAL", """
                {"title":"本机标题","currentLeafMessageId":"local","createdAtEpochMs":1000,"updatedAtEpochMs":3000,"surface":"CHAT","nodes":[
                  {"id":"root","parentMessageId":null,"siblingPosition":0,"role":"USER","createdAtEpochMs":1000,"deliveryState":"COMPLETE","revision":1,"revisesMessageId":null,"text":["问题"]},
                  {"id":"local","parentMessageId":"root","siblingPosition":0,"role":"ASSISTANT","createdAtEpochMs":3000,"deliveryState":"COMPLETE","revision":1,"revisesMessageId":null,"text":["手机新增"]}
                ]}
            """.trimIndent())),
        ))
        val remoteTurn = local.nodes.first().copy(
            id = MessageNodeId("remote"), parentMessageId = MessageNodeId("root"),
            role = MessageRole.ASSISTANT, content = listOf(ContentBlock.Text("电脑新增")),
            createdAt = Instant.ofEpochMilli(2_000), siblingPosition = 1,
        )
        val merged = mergeRemoteAdditionsForLocalCommit(local, local.copy(
            conversation = local.conversation.copy(currentLeafMessageId = remoteTurn.id),
            nodes = listOf(local.nodes.first(), remoteTurn),
        ))

        assertEquals(listOf("root", "remote", "local"), merged.nodes.map { it.id.value })
        assertEquals("本机标题", merged.conversation.title)
        assertEquals(MessageNodeId("local"), merged.conversation.currentLeafMessageId)
        val newerRemote = local.copy(conversation = local.conversation.copy(
            title = "电脑新标题", titleRevision = 1, updatedAt = Instant.ofEpochMilli(4_000)))
        assertEquals("电脑新标题", mergeRemoteAdditionsForLocalCommit(local, newerRemote).conversation.title)
        assertEquals("电脑新标题", mergeRemoteAdditionsForLocalCommit(newerRemote, local).conversation.title)
        val sameTimestampNewerRevision = local.copy(conversation = local.conversation.copy(
            title = "同一时刻的电脑新标题",
            titleRevision = 1,
            revision = local.conversation.revision + 1,
        ))
        assertEquals("同一时刻的电脑新标题", mergeRemoteAdditionsForLocalCommit(local, sameTimestampNewerRevision).conversation.title)
        fun manage(at: Long, action: com.nanzhufeng.ai.domain.ConversationManagementAction, title: String? = null) =
            com.nanzhufeng.ai.domain.ConversationManagementDomain(java.time.Clock.fixed(Instant.ofEpochMilli(at), java.time.ZoneOffset.UTC)).apply(
                local, com.nanzhufeng.ai.domain.ConversationManagementIntent(
                    com.nanzhufeng.ai.domain.ConversationManagementIntentId.new(), local.conversation.id, action, local.conversation.revision, title))
        val renamed = manage(5_000, com.nanzhufeng.ai.domain.ConversationManagementAction.RENAME, "用户的新标题")
        val pinnedOldTitle = manage(6_000, com.nanzhufeng.ai.domain.ConversationManagementAction.PIN)
        assertEquals("用户的新标题", mergeRemoteAdditionsForLocalCommit(renamed, pinnedOldTitle).conversation.title)
        assertEquals("用户的新标题", mergeRemoteAdditionsForLocalCommit(pinnedOldTitle, renamed).conversation.title)
        val laterEditWithSlowClock = renamed.copy(conversation = renamed.conversation.copy(
            title = "第二次明确改名", titleRevision = 2, updatedAt = Instant.ofEpochMilli(1)))
        assertEquals("第二次明确改名", mergeRemoteAdditionsForLocalCommit(renamed, laterEditWithSlowClock).conversation.title)
        val concurrent = renamed.copy(conversation = renamed.conversation.copy(title = "另一端同时改名", updatedAt = Instant.ofEpochMilli(9_999)))
        assertTrue(runCatching { mergeRemoteAdditionsForLocalCommit(renamed, concurrent) }.exceptionOrNull()?.message?.contains("标题版本冲突") == true)
        val ambiguous = local.copy(conversation = local.conversation.copy(title = "无法确定先后的另一标题"))
        for ((left, right) in listOf(local to ambiguous, ambiguous to local)) {
            val readFailure = runCatching { mergeNewerRemoteConversation(left, right) }.exceptionOrNull()
            val uploadFailure = runCatching { mergeRemoteAdditionsForLocalCommit(left, right) }.exceptionOrNull()
            assertTrue(readFailure?.message?.contains("标题版本冲突") == true)
            assertTrue(uploadFailure?.message?.contains("标题版本冲突") == true)
        }
    }

    @Test fun `current selected-conversation payload restores only its text tree`() {
        val snapshot = NfaiSyncPreparedSnapshot(
            appId = "com.nanzhufeng.ai",
            documentId = "conversation-0123456789012345678901234567890123456789",
            revision = 3,
            records = listOf(NfaiSyncRecord("conversation", "conversation-1", 2, "NORMAL", """
                {"title":"跨端对话","currentLeafMessageId":"m2","createdAtEpochMs":1000,"updatedAtEpochMs":2000,"surface":"CHAT","nodes":[
                  {"id":"m1","parentMessageId":null,"siblingPosition":0,"role":"USER","createdAtEpochMs":1000,"deliveryState":"COMPLETE","revision":1,"revisesMessageId":null,"text":["你好"]},
                  {"id":"m2","parentMessageId":"m1","siblingPosition":0,"role":"ASSISTANT","createdAtEpochMs":2000,"deliveryState":"COMPLETE","revision":1,"revisesMessageId":null,"text":["你好，我在。"]}
                ]}
            """.trimIndent())),
        )

        val restored = P7FConversationSyncWireFormat.decode(snapshot)

        assertEquals("conversation-1", restored.conversation.id.value)
        assertEquals("跨端对话", restored.conversation.title)
        assertEquals("m2", restored.conversation.currentLeafMessageId?.value)
        assertEquals(2, restored.nodes.size)
        assertEquals("你好，我在。", restored.nodes.last().content.single().let { it as com.nanzhufeng.ai.domain.ContentBlock.Text }.text)
    }

    @Test fun `legacy Desktop payload restores lower-case Desktop roles but rejects unsafe records`() {
        val snapshot = NfaiSyncPreparedSnapshot(
            appId = "com.nanzhufeng.ai",
            documentId = "conversation-0123456789012345678901234567890123456789",
            revision = 2,
            records = listOf(
                NfaiSyncRecord("conversation", "desktop-1", 1, "NORMAL", """
                  {"title":"Desktop 对话","createdAt":"2026-09-12T00:00:00Z","updatedAt":"2026-09-12T00:01:00Z","currentLeafId":"m1","pinned":true,"archived":false,"favorited":true,"messages":[
                    {"id":"m1","parentId":null,"ordinal":0,"role":"user","createdAt":"2026-09-12T00:00:00Z","revision":1,"delivery":"COMPLETE","blocks":[{"kind":"TEXT","text":"仅恢复文本"}]}
                  ]}
                """.trimIndent()),
                NfaiSyncRecord("safe_settings", "profile-interests", 1, "NORMAL", "{\"interests\":\"不导入\"}"),
            ),
        )

        val restored = P7FConversationSyncWireFormat.decode(snapshot)

        assertEquals("desktop-1", restored.conversation.id.value)
        assertEquals(null, restored.conversation.pinnedAt)
        assertEquals(null, restored.conversation.archivedAt)
        assertEquals(1789171260000L, restored.conversation.favoritedAt?.toEpochMilli())
        assertEquals("仅恢复文本", (restored.nodes.single().content.single() as com.nanzhufeng.ai.domain.ContentBlock.Text).text)
        assertTrue(restored.nodes.all { it.content.all { block -> block is com.nanzhufeng.ai.domain.ContentBlock.Text } })
    }

    @Test fun `legacy Desktop duplicate root ordinals are normalized without losing either conversation branch`() {
        val restored = P7FConversationSyncWireFormat.decode(NfaiSyncPreparedSnapshot(
            appId = "com.nanzhufeng.ai",
            documentId = "conversation-0123456789012345678901234567890123456789",
            revision = 2,
            records = listOf(NfaiSyncRecord("conversation", "desktop-duplicate-root", 1, "NORMAL", """
                {"title":"完整历史","createdAt":"2026-09-12T00:00:00Z","updatedAt":"2026-09-12T00:01:00Z","currentLeafId":"m2","messages":[
                  {"id":"m1","parentId":null,"ordinal":0,"role":"user","createdAt":"2026-09-12T00:00:00Z","revision":1,"delivery":"COMPLETE","blocks":[{"kind":"TEXT","text":"第一条根消息"}]},
                  {"id":"m2","parentId":null,"ordinal":0,"role":"assistant","createdAt":"2026-09-12T00:01:00Z","revision":1,"delivery":"COMPLETE","blocks":[{"kind":"TEXT","text":"第二条根消息"}]}
                ]}
            """.trimIndent())),
        ))

        assertEquals(listOf("m1", "m2"), restored.nodes.map { it.id.value })
        assertEquals(listOf(0, 1), restored.nodes.map { it.siblingPosition })
        assertEquals("m2", restored.conversation.currentLeafMessageId?.value)
    }

    @Test fun `legacy Desktop attachment metadata never blocks the adjacent text conversation`() {
        val snapshot = NfaiSyncPreparedSnapshot(
            appId = "com.nanzhufeng.ai",
            documentId = "conversation-0123456789012345678901234567890123456789",
            revision = 2,
            records = listOf(NfaiSyncRecord("conversation", "desktop-attachment", 1, "NORMAL", """
                {"title":"带附件的 Desktop 对话","createdAt":"2026-09-12T00:00:00Z","updatedAt":"2026-09-12T00:01:00Z","currentLeafId":"m1","messages":[
                  {"id":"m1","parentId":null,"ordinal":0,"role":"user","createdAt":"2026-09-12T00:00:00Z","revision":1,"delivery":"COMPLETE","blocks":[
                    {"kind":"TEXT","text":"保留这段文字"},
                    {"kind":"ASSET_REF","asset":{"id":"desktop-only","displayName":"图片.png","mimeType":"image/png","byteCount":1024}}
                  ]}
                ]}
            """.trimIndent())),
        )

        val restored = P7FConversationSyncWireFormat.decode(snapshot)

        assertEquals("保留这段文字", (restored.nodes.single().content.single() as com.nanzhufeng.ai.domain.ContentBlock.Text).text)
    }

    @Test fun `legacy Desktop tool-only branch preserves its later completed text`() {
        val snapshot = NfaiSyncPreparedSnapshot(
            appId = "com.nanzhufeng.ai",
            documentId = "conversation-0123456789012345678901234567890123456789",
            revision = 2,
            records = listOf(NfaiSyncRecord("conversation", "desktop-tool-branch", 1, "NORMAL", """
                {"title":"兼容旧工具分支","createdAt":"2026-09-12T00:00:00Z","updatedAt":"2026-09-12T00:01:00Z","currentLeafId":"m3","messages":[
                  {"id":"m1","parentId":null,"ordinal":0,"role":"user","createdAt":"2026-09-12T00:00:00Z","revision":1,"delivery":"COMPLETE","blocks":[{"kind":"TEXT","text":"保留根消息"}]},
                  {"id":"m2","parentId":"m1","ordinal":0,"role":"assistant","createdAt":"2026-09-12T00:00:30Z","revision":1,"delivery":"COMPLETE","blocks":[{"kind":"TOOL_RESULT","text":"只限 Desktop"}]},
                  {"id":"m3","parentId":"m2","ordinal":0,"role":"assistant","createdAt":"2026-09-12T00:01:00Z","revision":1,"delivery":"COMPLETE","blocks":[{"kind":"TEXT","text":"工具后的完整回答"}]}
                ]}
            """.trimIndent())),
        )

        val restored = P7FConversationSyncWireFormat.decode(snapshot)

        assertEquals(listOf("m1", "m3"), restored.nodes.map { it.id.value })
        assertEquals("m1", restored.nodes.last().parentMessageId?.value)
        assertEquals("m3", restored.conversation.currentLeafMessageId?.value)
    }

    @Test fun `legacy Desktop empty text placeholder preserves its later completed text`() {
        val snapshot = NfaiSyncPreparedSnapshot(
            appId = "com.nanzhufeng.ai",
            documentId = "conversation-0123456789012345678901234567890123456789",
            revision = 2,
            records = listOf(NfaiSyncRecord("conversation", "desktop-empty-placeholder", 1, "NORMAL", """
                {"title":"兼容空文本占位","createdAt":"2026-09-12T00:00:00Z","updatedAt":"2026-09-12T00:01:00Z","currentLeafId":"m3","messages":[
                  {"id":"m1","parentId":null,"ordinal":0,"role":"user","createdAt":"2026-09-12T00:00:00Z","revision":1,"delivery":"COMPLETE","blocks":[{"kind":"TEXT","text":"保留根消息"}]},
                  {"id":"m2","parentId":"m1","ordinal":0,"role":"assistant","createdAt":"2026-09-12T00:00:30Z","revision":1,"delivery":"COMPLETE","blocks":[{"kind":"TEXT","text":""}]},
                  {"id":"m3","parentId":"m2","ordinal":0,"role":"assistant","createdAt":"2026-09-12T00:01:00Z","revision":1,"delivery":"COMPLETE","blocks":[{"kind":"TEXT","text":"空占位后的完整回答"}]}
                ]}
            """.trimIndent())),
        )

        val restored = P7FConversationSyncWireFormat.decode(snapshot)

        assertEquals(listOf("m1", "m3"), restored.nodes.map { it.id.value })
        assertEquals("m1", restored.nodes.last().parentMessageId?.value)
        assertEquals("m3", restored.conversation.currentLeafMessageId?.value)
    }

    @Test fun `legacy Desktop retry markers and exact duplicate messages restore one valid tree`() {
        val snapshot = NfaiSyncPreparedSnapshot(
            appId = "com.nanzhufeng.ai",
            documentId = "conversation-0123456789012345678901234567890123456789",
            revision = 2,
            records = listOf(NfaiSyncRecord("conversation", "desktop-retry", 1, "NORMAL", """
                {"title":"旧重试对话","createdAt":"2026-09-12T00:00:00Z","updatedAt":"2026-09-12T00:01:00Z","currentLeafId":"m2","messages":[
                  {"id":"m1","parentId":null,"ordinal":0,"role":"user","createdAt":"2026-09-12T00:00:00Z","revision":1,"delivery":"COMPLETE","blocks":[{"kind":"TEXT","text":"问题"}]},
                  {"id":"m1","parentId":null,"ordinal":0,"role":"user","createdAt":"2026-09-12T00:00:00Z","revision":1,"delivery":"COMPLETE","blocks":[{"kind":"TEXT","text":"问题"}]},
                  {"id":"m2","parentId":"m1","ordinal":0,"role":"assistant","createdAt":"2026-09-12T00:01:00Z","revision":1,"delivery":"UNKNOWN","blocks":[{"kind":"TEXT","text":"旧尝试"}]}
                ]}
            """.trimIndent())),
        )

        val restored = P7FConversationSyncWireFormat.decode(snapshot)

        assertEquals(2, restored.nodes.size)
        assertEquals(com.nanzhufeng.ai.domain.MessageDeliveryState.FAILED, restored.nodes.last().deliveryState)
    }

    @Test fun `current payload preserves list collection state across devices`() {
        val snapshot = NfaiSyncPreparedSnapshot(
            appId = "com.nanzhufeng.ai",
            documentId = "conversation-0123456789012345678901234567890123456789",
            revision = 3,
            records = listOf(NfaiSyncRecord("conversation", "conversation-state", 2, "NORMAL", """
                {"title":"状态同步","currentLeafMessageId":"m1","createdAtEpochMs":1000,"updatedAtEpochMs":2000,"surface":"CHAT","pinnedAtEpochMs":1200,"archivedAtEpochMs":null,"favoritedAtEpochMs":1500,"nodes":[
                  {"id":"m1","parentMessageId":null,"siblingPosition":0,"role":"ASSISTANT","createdAtEpochMs":1000,"deliveryState":"COMPLETE","revision":1,"revisesMessageId":null,"text":["保留列表状态"],"modelUsage":{"modelId":"openai/gpt-6-astra","modelDisplayName":"GPT-6 Astra","inputTokens":12,"outputTokens":34,"totalTokens":46,"cachedInputTokens":null,"reasoningTokens":null,"costPriceVersion":"provider-v1","costCurrencyCode":"USD","costTotalMicros":56,"costSource":"PROVIDER_RESPONSE"}}
                ]}
            """.trimIndent())),
        )

        val decoded = P7FConversationSyncWireFormat.decodeWithModelUsage(snapshot)
        val restored = decoded.snapshot

        assertEquals(1200L, restored.conversation.pinnedAt?.toEpochMilli())
        assertEquals(1500L, restored.conversation.favoritedAt?.toEpochMilli())
        assertEquals(null, restored.conversation.archivedAt)
        val usage = decoded.modelUsageByAssistantMessage.getValue(com.nanzhufeng.ai.domain.MessageNodeId("m1"))
        assertEquals("openai/gpt-6-astra", usage.modelId)
        assertEquals(46L, usage.usage.totalTokens)
        assertEquals(56L, usage.cost.totalMicros)
        val transcript = com.nanzhufeng.ai.domain.ConversationTranscriptPresentation(
            com.nanzhufeng.ai.domain.MessagePresentationRenderer(),
        ).render(
            path = restored.nodes,
            lineages = emptyList(),
            cloudResponseModelUsages = decoded.modelUsageByAssistantMessage.mapValues { listOf(it.value) },
        ).single()
        assertEquals("GPT-6 Astra", transcript.metadata.modelSnapshotLabel)
        assertTrue(transcript.metadata.costLabel != null)
    }
}
