package com.nanzhufeng.ai.domain
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test
class ChatCallAccountingTest {
 private fun call(id:String,role:String="ANSWER",cost:Long?=10,currency:String="USD")=ChatCallAccounting(id,"attempt","conversation",role,ProviderId.RELAYCORE_GPT,"gpt-6.1-sol","gpt-6.1-sol","GPT-6.1 Sol · Pro3",Instant.EPOCH,Instant.ofEpochMilli(20),"COMPLETED",ProviderUsage(100,20,120,10,5),if(cost==null) ProviderCost() else ProviderCost("fixture",currency,cost),if(cost==null) null else ConversationCostSource.LOCAL_ESTIMATE)
 @Test fun `zero is known unknown and mixed currencies never become combined totals`() {
  assertEquals(0L,combinedAccountingCost(listOf(call("a",cost=0),call("r","RETRIEVAL",0))).first.totalMicros)
  assertNull(combinedAccountingCost(listOf(call("a"),call("r","RETRIEVAL",null))).first.totalMicros)
  assertNull(combinedAccountingCost(listOf(call("a"),call("r","RETRIEVAL",10,"CNY"))).first.totalMicros)
  assertNull(combinedAccountingCost(listOf(call("a",cost=Long.MAX_VALUE),call("r","RETRIEVAL"))).first.totalMicros)
 }
 @Test fun `fee ledger counts physical answer helper and failed retry once`() {
  val answer=AssistantResponseModelAttribution(MessageNodeId("message"),NormalChatSendAttemptId("attempt"),ProviderId.RELAYCORE_GPT,ProviderId.RELAYCORE_GPT,"gpt-6.1-sol","GPT-6.1 Sol · Pro3",Instant.EPOCH,accountingCalls=listOf(call("a"),call("r","RETRIEVAL")))
  val rows=mergePhysicalCallCostRecords(listOf(answer),listOf(call("a"),call("r","RETRIEVAL"),call("retry",cost=null).copy(status="NETWORK")))
  assertEquals(3,rows.size)
  assertEquals(2,rows.count {it.cost.totalMicros!=null})
  assertEquals(setOf("a","r","retry"),rows.map {it.physicalCallId}.toSet())
  assertEquals(5L,rows.first().usage.reasoningTokens)
 }
 @Test fun `legacy Relay estimate displays nominal RMB without changing stored money`() {
  val saved=call("legacy",cost=1000000).copy(cost=ProviderCost("relaycore-verified-group-2026-10-10-Pro3","USD",1000000))
  val answer=AssistantResponseModelAttribution(MessageNodeId("legacy-message"),NormalChatSendAttemptId("attempt"),ProviderId.RELAYCORE_GPT,ProviderId.RELAYCORE_GPT,"gpt-6.1-sol","GPT-6.1 Sol · Pro3",Instant.EPOCH,cost=saved.cost,costSource=saved.costSource)
  assertEquals("¥1",answer.footerCostLabel())
  assertEquals("CNY",answer.withAvailableLocalCostEstimate().cost.currencyCode)
  assertEquals("USD",saved.cost.currencyCode)
  val current=call("current",cost=1000000,currency="CNY")
  val total=combinedAccountingCost(listOf(saved,current)).first
  assertEquals("CNY",total.currencyCode)
  assertEquals(2000000L,total.totalMicros)
  assertEquals("CNY",mergePhysicalCallCostRecords(emptyList(),listOf(saved)).single().cost.currencyCode)
  assertEquals("¥2",answer.copy(accountingCalls=listOf(saved,current),cost=ProviderCost("combined-call-accounting-v1","USD",2000000)).footerCostLabel())
 }
 @Test fun `footer shows mixed original currencies as RMB projection and keeps unknown separate`() {
  val calls=listOf(call("answer",cost=500000,currency="CNY"),call("luna","RETRIEVAL",100000,"USD"))
  val answer=AssistantResponseModelAttribution(MessageNodeId("mixed-message"),NormalChatSendAttemptId("attempt"),ProviderId.RELAYCORE_GPT,ProviderId.RELAYCORE_GPT,"gpt-6.1-sol","GPT-6.1 Sol · Pro3",Instant.EPOCH,accountingCalls=calls)
  assertEquals("¥1.172031",answer.footerCostLabel())
  assertEquals("¥1.172031 + 未知",answer.copy(accountingCalls=calls+call("unknown",cost=null)).footerCostLabel())
  assertEquals("USD",calls[1].cost.currencyCode)
  assertEquals(100000L,calls[1].cost.totalMicros)
 }

}
