package com.nanzhufeng.ai.data
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.nanzhufeng.ai.data.local.*
import com.nanzhufeng.ai.domain.*
import com.nanzhufeng.ai.ai.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
@RunWith(RobolectricTestRunner::class)
class ChatAccountingPersistenceTest {
 @Test fun `RC Gemini trailing usage produces a separate answer fee after Room reopen`() {
  val ctx=ApplicationProvider.getApplicationContext<Context>();val at=Instant.parse("2026-10-13T00:00:00Z")
  for((preset,expected,footer) in listOf(Triple(ModelPresetId.RELAYCORE_GEMINI_3_1_PRO,7952L,"¥0.075155"),Triple(ModelPresetId.RELAYCORE_GEMINI_3_8_FLASH,2772L,"¥0.069975"))) {
   val name="gemini-usage-${UUID.randomUUID()}.db";var db=open(ctx,name)
   try {
    val spec=RelayCoreModels.spec(preset)!!;val adapter=RelayCoreChatAdapter(spec.provider);val options=ChatRequestOptions(groundedAnswer=true)
    val model=ResolvedModel(spec.provider,spec.apiId,spec.compactName,ModelCapabilities(true,false,true),null,ModelHealth.UNKNOWN,null,null)
    val ready=adapter.prepare(model,listOf("user" to "public minimal input"),emptyList(),true,options) as ChatAdapterPrepareResult.Ready
    val requested=JSONObject(ready.jsonBody).optJSONObject("stream_options")?.optBoolean("include_usage")==true
    val usageFrame=if(requested) "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":10000,\"completion_tokens\":1000,\"prompt_tokens_details\":{\"cached_tokens\":2000},\"completion_tokens_details\":{\"reasoning_tokens\":250}}}\n\n" else ""
    val wire="data: {\"model\":\"${spec.apiId}\",\"choices\":[{\"delta\":{\"content\":\"public answer\"},\"finish_reason\":\"stop\"}]}\n\n"+usageFrame+"data: [DONE]\n\n"
    val reply=ProviderSseDecoder.read(wire.byteInputStream(),{},{adapter.decodeStreamingEvent(it,options)},adapter.streamTextMode(options))
    val usage=ProviderUsage(inputTokens=reply.inputTokens,outputTokens=reply.outputTokens,cachedInputTokens=reply.cachedInputTokens,reasoningTokens=reply.reasoningTokens)
    val cost=ConversationCostEstimator.estimate(spec.apiId,usage,at,spec.compactName)
    assertEquals(expected,cost?.totalMicros);assertEquals("CNY",cost?.currencyCode)
    val answer=call("answer").copy(providerId=spec.provider,requestedModelId=spec.apiId,actualModelId=reply.returnedModelId,modelDisplayName=spec.compactName,requestedAt=at,finishedAt=at.plusMillis(1),usage=usage,cost=requireNotNull(cost))
    val helper=call("helper","RETRIEVAL").copy(providerId=ProviderId.OPENROUTER,requestedModelId="openai/gpt-6-luna",actualModelId="openai/gpt-6-luna",cost=ProviderCost("fixture","USD",10000),costSource=ConversationCostSource.PROVIDER_RESPONSE)
    val audit=AndroidDirectChatCallAuditStore(ctx,db);audit.append(record(helper));audit.append(record(answer))
    val attribution=AssistantResponseModelAttribution(MessageNodeId("answer"),NormalChatSendAttemptId("attempt"),spec.provider,spec.provider,spec.apiId,spec.compactName,at,usage=usage,accountingCalls=listOf(helper,answer))
    RoomAssistantResponseModelAttributionStore(db).record(attribution)
    db.close();db=open(ctx,name)
    val saved=RoomAssistantResponseModelAttributionStore(db).forMessages(listOf(MessageNodeId("answer"))).values.flatten().single()
    assertEquals(footer,saved.footerCostLabel());assertEquals(2,saved.accountingCalls.size)
    val rows=AndroidDirectChatCallAuditStore(ctx,db).listNewestFirst()
    assertEquals(2,rows.size);assertEquals(expected,rows.single {it.accounting?.role=="ANSWER"}.cost.totalMicros)
    assertEquals(2000L,saved.accountingCalls.single {it.role=="ANSWER"}.usage.cachedInputTokens)
   }finally{db.close();ctx.deleteDatabase(name)}
  }
 }
 private fun call(id:String,role:String="ANSWER",status:String="COMPLETED")=ChatCallAccounting(id,"attempt","conversation",role,ProviderId.RELAYCORE_GPT,"gpt-6.1-sol","gpt-6.1-sol","GPT-6.1 Sol · Pro3",Instant.ofEpochMilli(100),Instant.ofEpochMilli(200),status,ProviderUsage(100,20,120,10,5),ProviderCost("fixture","USD",10),ConversationCostSource.LOCAL_ESTIMATE,1048576,2)
 private fun record(call:ChatCallAccounting)=DirectChatCallAuditRecord(call.providerId,"https://api.relaycore.cn/v1/responses",call.requestedModelId,call.modelDisplayName,"medium",call.requestedAt,call.usage.inputTokens,call.usage.outputTokens,call.status,call.actualModelId,call.usage.cachedInputTokens,call.usage.reasoningTokens,call.cost,call.costSource,call)
 private fun open(ctx:Context,name:String)=Room.databaseBuilder(ctx,NanfengAiDatabase::class.java,name).addMigrations(NanfengAiDatabase.MIGRATION_70_71,NanfengAiDatabase.MIGRATION_71_72).allowMainThreadQueries().build()
 @Test fun `completed helper failed answer replay and concurrent calls persist through reopen`() {
  val ctx=ApplicationProvider.getApplicationContext<Context>();val name="accounting-${UUID.randomUUID()}.db";var db=open(ctx,name)
  try {
   val store=AndroidDirectChatCallAuditStore(ctx,db);val ready=call("helper","RETRIEVAL")
   store.append(record(ready.copy(finishedAt=null,status="RELAY_WEB_RETRIEVAL_PENDING",usage=ProviderUsage(),cost=ProviderCost(),costSource=null)))
   store.append(record(ready));store.append(record(ready));store.append(record(call("failed",status="NETWORK").copy(usage=ProviderUsage(),cost=ProviderCost(),costSource=null)))
   val pool=Executors.newFixedThreadPool(4)
   try {pool.invokeAll((0 until 24).map {n->java.util.concurrent.Callable { AndroidDirectChatCallAuditStore(ctx,db).append(record(call("concurrent-$n"))) }}).forEach {it.get()} }finally{pool.shutdown()}
   assertEquals(26,store.summary().recordCount)
   db.close();db=open(ctx,name)
   val rows=AndroidDirectChatCallAuditStore(ctx,db).listNewestFirst();assertEquals(26,rows.size)
   assertEquals(5L,rows.single {it.accounting?.callId=="helper"}.reasoningTokens)
   assertEquals(10L,rows.single {it.accounting?.callId=="helper"}.cost.totalMicros)
   assertNull(rows.single {it.accounting?.callId=="failed"}.cost.totalMicros)
  }finally{db.close();ctx.deleteDatabase(name)}
 }
 @Test fun `sub millisecond request survives pending final replay and database reopen`() {
  val ctx=ApplicationProvider.getApplicationContext<Context>();val name="accounting-precision-${UUID.randomUUID()}.db";var db=open(ctx,name)
  try {
   val store=AndroidDirectChatCallAuditStore(ctx,db)
   for (role in listOf("ANSWER","RETRIEVAL")) {
    val at=Instant.parse("2026-10-10T07:38:57.123456789Z")
    val completed=call("precision-$role",role).copy(requestedAt=at,finishedAt=at.plusSeconds(107).plusNanos(98765))
    val pending=completed.copy(actualModelId=null,finishedAt=null,status=if(role=="ANSWER") "CALL_PENDING" else "RELAY_WEB_RETRIEVAL_PENDING",usage=ProviderUsage(),cost=ProviderCost(),costSource=null)
    store.append(record(pending));store.append(record(pending))
    store.append(record(completed));store.append(record(completed))
   }
   db.close();db=open(ctx,name)
   val rows=AndroidDirectChatCallAuditStore(ctx,db).listNewestFirst()
   assertEquals(2,rows.size)
   rows.forEach {row ->
    assertEquals(Instant.parse("2026-10-10T07:38:57.123Z"),row.accounting!!.requestedAt)
    assertEquals(Instant.parse("2026-10-10T07:40:44.123Z"),row.accounting.finishedAt)
    assertEquals(100L,row.inputTokens);assertEquals(10L,row.cost.totalMicros)
   }
  }finally{db.close();ctx.deleteDatabase(name)}
 }
 @Test fun `sub millisecond answer attribution replay preserves physical call facts`() {
  val ctx=ApplicationProvider.getApplicationContext<Context>();val name="accounting-attribution-precision-${UUID.randomUUID()}.db";var db=open(ctx,name)
  try {
   val at=Instant.parse("2026-10-10T07:38:57.123456789Z")
   val calls=listOf(call("answer-nanos").copy(requestedAt=at,finishedAt=at.plusSeconds(1)))
   val attribution=AssistantResponseModelAttribution(MessageNodeId("answer"),NormalChatSendAttemptId("attempt"),ProviderId.RELAYCORE_GPT,ProviderId.RELAYCORE_GPT,"gpt-6.1-sol","GPT-6.1 Sol · Pro3",at,accountingCalls=calls)
   val store=RoomAssistantResponseModelAttributionStore(db);store.record(attribution);store.record(attribution)
   db.close();db=open(ctx,name)
   RoomAssistantResponseModelAttributionStore(db).record(attribution)
   assertEquals(calls.map(ChatAccountingJsonCodec::canonical),RoomAssistantResponseModelAttributionStore(db).forMessages(listOf(MessageNodeId("answer"))).getValue(MessageNodeId("answer")).single().accountingCalls)
  }finally{db.close();ctx.deleteDatabase(name)}
 }
 @Test fun `real ownership and measured fact conflicts remain rejected without mutation`() {
  val ctx=ApplicationProvider.getApplicationContext<Context>();val name="accounting-conflict-${UUID.randomUUID()}.db";val db=open(ctx,name)
  try {
   val store=AndroidDirectChatCallAuditStore(ctx,db);val completed=call("immutable")
   store.append(record(completed))
   listOf(completed.copy(conversationId="other"),completed.copy(rootAttemptId="other"),
    completed.copy(providerId=ProviderId.OPENROUTER),completed.copy(requestedModelId="other"),
    completed.copy(role="RETRIEVAL"),completed.copy(requestedAt=completed.requestedAt.plusMillis(1)),
    completed.copy(usage=ProviderUsage(1,2,3)),completed.copy(cost=ProviderCost("fixture","USD",11)))
    .forEach {assertTrue(runCatching {store.append(record(it))}.isFailure)}
   assertEquals(completed,store.listNewestFirst().single().accounting)
  }finally{db.close();ctx.deleteDatabase(name)}
 }
 @Test fun `answer stores independent calls and binds both context records beyond old retention cap`() {
  val ctx=ApplicationProvider.getApplicationContext<Context>();val name="accounting-answer-${UUID.randomUUID()}.db";var db=open(ctx,name)
  try {
   val calls=listOf(call("answer"),call("helper","RETRIEVAL"));val costs=combinedAccountingCost(calls)
   val attribution=AssistantResponseModelAttribution(MessageNodeId("answer"),NormalChatSendAttemptId("attempt"),ProviderId.RELAYCORE_GPT,ProviderId.RELAYCORE_GPT,"gpt-6.1-sol","GPT-6.1 Sol · Pro3",Instant.EPOCH,usage=calls.first().usage,cost=costs.first,costSource=costs.second,accountingCalls=calls)
   val store=RoomAssistantResponseModelAttributionStore(db);store.record(attribution);store.record(attribution)
   assertTrue(runCatching {store.record(attribution.copy(accountingCalls=listOf(call("contradiction"))))}.isFailure)
   val contexts=AndroidContextSelectionAuditStore(ctx,db)
   val sample=ContextSelectionAuditRecord(Instant.EPOCH,"conversation",NormalChatSendAttemptId("attempt"),providerId=ProviderId.RELAYCORE_GPT,modelId="gpt-6.1-sol",tokenizerId="heuristic-v1",budget=ContextBudget(1000,100,900,300,600),selectedSources=listOf(ContextSelectionSource("本轮","current","当前问题",3)))
   contexts.append(sample);contexts.append(sample.copy(modelId="claude-opus-5-5",providerId=ProviderId.RELAYCORE_CLAUDE))
   contexts.bindAnswer(NormalChatSendAttemptId("attempt"),MessageNodeId("answer"),true)
   repeat(250) {contexts.append(sample.copy(attemptId=NormalChatSendAttemptId("other-$it")))}
   db.close();db=open(ctx,name)
   assertEquals(calls,RoomAssistantResponseModelAttributionStore(db).forMessages(listOf(MessageNodeId("answer"))).getValue(MessageNodeId("answer")).single().accountingCalls)
   assertEquals(2,AndroidContextSelectionAuditStore(ctx,db).forAssistantMessages(listOf(MessageNodeId("answer"))).getValue(MessageNodeId("answer")).size)
  }finally{db.close();ctx.deleteDatabase(name)}
 }
 @Test fun `six Luna searches and answer reopen as two fee rows without rewriting seven calls`() {
  val ctx=ApplicationProvider.getApplicationContext<Context>();val name="accounting-group-${UUID.randomUUID()}.db";var db=open(ctx,name)
  try {
   val searches=(1..6).map { n -> call("luna-$n","RETRIEVAL").copy(providerId=ProviderId.OPENROUTER,requestedModelId="openai/gpt-6-luna",actualModelId="openai/gpt-6-luna",modelDisplayName="GPT-6 Luna",cost=ProviderCost("fixture","USD",100000),costSource=ConversationCostSource.PROVIDER_RESPONSE) }
   val answer=call("main").copy(cost=ProviderCost("fixture","CNY",18087))
   val calls=searches+answer
   val store=AndroidDirectChatCallAuditStore(ctx,db);calls.forEach { store.append(record(it));store.append(record(it)) }
   val attribution=AssistantResponseModelAttribution(MessageNodeId("reply"),NormalChatSendAttemptId("attempt"),answer.providerId,answer.providerId,answer.requestedModelId,answer.modelDisplayName,Instant.EPOCH,accountingCalls=calls)
   RoomAssistantResponseModelAttributionStore(db).record(attribution)
   db.close();db=open(ctx,name)
   val physical=AndroidDirectChatCallAuditStore(ctx,db).listNewestFirst().mapNotNull { it.accounting }
   val saved=RoomAssistantResponseModelAttributionStore(db).forMessages(listOf(MessageNodeId("reply"))).getValue(MessageNodeId("reply"))
   val rows=mergeConversationCostRecords(saved,physical)
   assertEquals(7,physical.size);assertEquals(2,rows.size)
   assertEquals("¥4.032185",rows.single { it.physicalCallRole=="RETRIEVAL" }.footerCostLabel())
   assertEquals("¥0.018087",rows.single { it.physicalCallRole=="ANSWER" }.footerCostLabel())
   assertEquals("¥4.050272",CnyMoneyDisplay.summaryLabel(rows.flatMap { it.ledgerCostFacts() }))
   assertEquals("¥4.050272",saved.single().footerCostLabel())
   assertEquals(7,AndroidDirectChatCallAuditStore(ctx,db).summary().recordCount)
  }finally{db.close();ctx.deleteDatabase(name)}
 }
 @Test fun `full schema 70 upgrades preserving legacy attribution and new audit tables`() {
  val ctx=ApplicationProvider.getApplicationContext<Context>();val name="accounting-upgrade-${UUID.randomUUID()}.db"
  val schema=JSONObject(File("schemas/com.nanzhufeng.ai.data.local.NanfengAiDatabase/70.json").readText()).getJSONObject("database")
  val helper=FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(ctx).name(name).callback(object:SupportSQLiteOpenHelper.Callback(70){
   override fun onCreate(db:SupportSQLiteDatabase){
    val entities=schema.getJSONArray("entities")
    for(i in 0 until entities.length()){val e=entities.getJSONObject(i);db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName")));val indices=(e.optJSONArray("indices") ?: org.json.JSONArray());for(j in 0 until indices.length())db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName")))}
    val setup=schema.getJSONArray("setupQueries");for(i in 0 until setup.length())db.execSQL(setup.getString(i))
    db.execSQL("INSERT INTO assistant_response_model_attributions(assistantMessageId,attemptId,providerId,receiverProviderId,modelId,modelDisplayName,recordedAtEpochMs) VALUES('legacy','legacy-attempt','OPENROUTER','OPENROUTER','openai/gpt-6-luna','GPT-6 Luna',100)")
   }
   override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int){}
  }).build())
  try {helper.writableDatabase;helper.close();val db=open(ctx,name);try {assertNull(db.assistantResponseModelAttributionDao().find("legacy","legacy-attempt")!!.accountingCallsJson);assertEquals(0,db.directChatCallAuditDao().count());assertEquals(0,db.contextSelectionAuditDao().recent(10).size)}finally{db.close()}}finally{helper.close();ctx.deleteDatabase(name)}
 }
}
