package com.nanzhufeng.ai.ai

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.*
import com.nanzhufeng.ai.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.*

@RunWith(RobolectricTestRunner::class)
class AttachmentSearchCheckpointTest {
    @Test fun `schema 71 upgrade preserves v2 receipt and reopens schema 72`() {
        val context=ApplicationProvider.getApplicationContext<Context>();val name="scope-upgrade-${java.util.UUID.randomUUID()}.db"
        val id=NormalChatSendAttemptId.new();val at=Instant.parse("2026-10-12T05:00:00Z")
        val old=org.json.JSONObject(java.io.File("schemas/com.nanzhufeng.ai.data.local.NanfengAiDatabase/71.json").readText()).getJSONObject("database")
        val initial=Room.databaseBuilder(context,NanfengAiDatabase::class.java,name).allowMainThreadQueries().build()
        try {
            RoomNormalChatSendAttemptStore(initial).create(NormalChatSendAttempt(id,MessageNodeId("00000000-0000-0000-0000-000000000001"),ConversationId("00000000-0000-0000-0000-000000000002"),ProviderId.RELAYCORE_GPT,"gpt-6.1-sol","test-key",NormalChatSendAttemptStatus.FAILED,at,at,egressDisclosureVersion="normal-chat-egress-v2"))
            // Isolated fixture becomes the exact previous schema: only the new table was added.
            val sqlite=initial.openHelper.writableDatabase
            sqlite.execSQL("DROP TABLE normal_chat_search_checkpoints")
            sqlite.execSQL("UPDATE room_master_table SET identity_hash=? WHERE id=42",arrayOf(old.getString("identityHash")))
            sqlite.version=71
        } finally {initial.close()}
        repeat(2) {
            val upgraded=Room.databaseBuilder(context,NanfengAiDatabase::class.java,name).addMigrations(NanfengAiDatabase.MIGRATION_71_72).allowMainThreadQueries().build()
            try {assertEquals(72,upgraded.openHelper.writableDatabase.version);assertEquals("normal-chat-egress-v2",RoomNormalChatSendAttemptStore(upgraded).findLatestForConversation(ConversationId("00000000-0000-0000-0000-000000000002"))?.egressDisclosureVersion)} finally {upgraded.close()}
        }
        context.deleteDatabase(name)
    }

    @Test fun `completed evidence survives reopen with exact scope and original paid call`() {
        val context=ApplicationProvider.getApplicationContext<Context>();val name="scope-checkpoint-${java.util.UUID.randomUUID()}.db";val at=Instant.parse("2026-10-12T05:00:00Z")
        val attemptId=NormalChatSendAttemptId.new();val fp="a".repeat(64)
        val call=ChatCallAccounting("call-original",attemptId.value,"conversation","RETRIEVAL",ProviderId.RELAYCORE_GPT,"gpt-6.1-sol","gpt-6.1-sol","Sol",at,at,"RELAY_WEB_RETRIEVAL_SUCCEEDED")
        val item=AttachmentWebSearchPlan.Item("A1","Python在2025年发布版本。",emptyList())
        fun database()=Room.databaseBuilder(context,NanfengAiDatabase::class.java,name).allowMainThreadQueries().build()
        fun withDatabase(action:(NanfengAiDatabase)->Unit) {val db=database();try {action(db)} finally {db.close()}}
        withDatabase {db ->
            RoomNormalChatSendAttemptStore(db).create(NormalChatSendAttempt(attemptId,MessageNodeId("00000000-0000-0000-0000-000000000001"),ConversationId("00000000-0000-0000-0000-000000000002"),ProviderId.RELAYCORE_GPT,"gpt-6.1-sol","test-key",NormalChatSendAttemptStatus.FAILED,at,at,egressDisclosureVersion="normal-chat-egress-v2"))
            RoomNormalChatSendAttemptStore(db).saveSearchCheckpoint(attemptId,NormalChatSearchCheckpoint(fp,item.id,"A1 ${item.query}\n已核实：公开发布日期。\n来源：https://example.org/source",listOf(NormalChatSearchSource("https://example.org/source","公开来源")),"gpt-6.1-sol","Sol",at,call.callId))
        }
        withDatabase {db ->
            val store=RoomNormalChatSendAttemptStore(db);val model=ResolvedModel(ProviderId.RELAYCORE_GPT,"gpt-6.1-sol","Sol",ModelCapabilities(true,false,true),131072,ModelHealth.AVAILABLE,null,null)
            val cache=AttemptAttachmentSearchCache(store,attemptId,fp,model,Clock.fixed(at.plusSeconds(5),ZoneOffset.UTC),mapOf(call.callId to call))
            assertNotNull(cache.load(item));assertNull(AttemptAttachmentSearchCache(store,attemptId,"b".repeat(64),model,Clock.fixed(at,ZoneOffset.UTC),mapOf(call.callId to call)).load(item))
            assertNull(AttemptAttachmentSearchCache(store,attemptId,fp,model,Clock.fixed(at.plusSeconds(601),ZoneOffset.UTC),mapOf(call.callId to call)).load(item))
            assertNull(AttemptAttachmentSearchCache(store,attemptId,fp,model,Clock.fixed(at,ZoneOffset.UTC),emptyMap()).load(item))
            assertNull(cache.load(item.copy(query="最新价格是多少")))
            var sends=0
            val out=AttachmentWebSearchCoordinator.run(AttachmentWebSearchPlan.Plan(listOf(item)),{false},{_,_->sends++;error("cached query must not resend")},cache=cache) as RelayCoreWebSearchCompanion.Result.Ready
            assertEquals(0,sends);assertTrue(out.completeScope);assertTrue(out.reply.text.contains("并未再次发送"))
        }
        context.deleteDatabase(name)
    }
    @Test fun `service evidence reopens without a fake model or new paid request`() {
        val context=ApplicationProvider.getApplicationContext<Context>();val name="service-checkpoint-${java.util.UUID.randomUUID()}.db"
        val at=Instant.parse("2026-10-12T05:00:00Z");val id=NormalChatSendAttemptId.new();val fp="a".repeat(64)
        val item=AttachmentWebSearchPlan.Item("A1","Python 3.14.0公开发布",emptyList())
        val call=ChatCallAccounting("original-service",id.value,"conversation","RETRIEVAL",ProviderId.ZHIPU,"",null,IndependentSearchServiceGrant.LABEL,at,at,"RELAY_WEB_RETRIEVAL_SUCCEEDED",serviceId=IndependentSearchServiceGrant.SERVICE_ID,serviceEngineId=IndependentSearchServiceGrant.ENGINE_ID)
        fun database()=Room.databaseBuilder(context,NanfengAiDatabase::class.java,name).allowMainThreadQueries().build()
        var db=database()
        try {
            val store=RoomNormalChatSendAttemptStore(db)
            store.create(NormalChatSendAttempt(id,MessageNodeId("00000000-0000-0000-0000-000000000001"),ConversationId("00000000-0000-0000-0000-000000000002"),ProviderId.RELAYCORE_CLAUDE,"claude-sonnet-5-5","test-service",NormalChatSendAttemptStatus.UNKNOWN,at,at,egressDisclosureVersion="normal-chat-egress-v2"))
            AttemptIndependentSearchCache(store,id,fp,Clock.fixed(at,ZoneOffset.UTC),emptyMap()).save(item,"${item.id} ${item.query}\n查询已执行，事实未核实。\n来源：https://python.org/",listOf(ProviderWebSource("https://python.org/","Python")),listOf(call.callId))
        } finally {db.close()}
        db=database()
        try {
            val store=RoomNormalChatSendAttemptStore(db);val clock=Clock.fixed(at.plusSeconds(5),ZoneOffset.UTC)
            val cache=AttemptIndependentSearchCache(store,id,fp,clock,mapOf(call.callId to call))
            val row=requireNotNull(cache.load(item));assertEquals("",row.actualModelId);assertEquals(IndependentSearchServiceGrant.SERVICE_ID,row.serviceId)
            assertNull(AttemptIndependentSearchCache(store,id,"b".repeat(64),clock,mapOf(call.callId to call)).load(item))
            assertNull(AttemptIndependentSearchCache(store,id,fp,clock,emptyMap()).load(item))
            val grant=IndependentSearchServiceGrant("normal-chat-egress-v3",IndependentSearchServiceGrant.SERVICE_ID,IndependentSearchServiceGrant.ENGINE_ID,fp)
            var sends=0
            val client=IndependentWebSearchClient(object:ProviderChatTransport {override fun execute(r:ProviderChatRequest,c:CharArray):ProviderChatOutcome {sends++;error("cached source must not resend")}})
            val result=IndependentAttachmentWebSearch.run(AttachmentWebSearchPlan.Plan(listOf(item)),grant,fp,client,{error("no key needed")},null,clock,{}, {_,_,_->error("no accounting rewrite")},{_,_->},cache) as RelayCoreWebSearchCompanion.Result.Ready
            assertEquals(0,sends);assertTrue(result.completeScope);assertNull(result.reply.returnedModelId)
            assertEquals("normal-chat-egress-v2",store.findLatestForConversation(ConversationId("00000000-0000-0000-0000-000000000002"))?.egressDisclosureVersion)
        } finally {db.close();context.deleteDatabase(name)}
    }

}
