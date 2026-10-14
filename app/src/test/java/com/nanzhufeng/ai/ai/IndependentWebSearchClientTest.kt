package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IndependentWebSearchClientTest {
    private val fp="a".repeat(64)
    private val grant=IndependentSearchServiceGrant("normal-chat-egress-v3",IndependentSearchServiceGrant.SERVICE_ID,IndependentSearchServiceGrant.ENGINE_ID,fp)
    @Test fun `old grants changed materials and cancellation cannot issue a request`() {
        var requests=0
        val client=IndependentWebSearchClient(object:ProviderChatTransport {override fun execute(request:ProviderChatRequest,credential:CharArray):ProviderChatOutcome {requests++;error("not authorized")}})
        fun call(g:IndependentSearchServiceGrant,f:String,c:ProviderChatCancellation?=null)=client.search("public fact","public-1",g,f,{error("no secret read")},c)
        assertEquals(IndependentWebSearchClient.Result.Failed("EGRESS_AUTHORIZATION_REQUIRED"),call(grant.copy(disclosureVersion="normal-chat-relay-search-v2"),fp))
        assertEquals(IndependentWebSearchClient.Result.Failed("EGRESS_AUTHORIZATION_REQUIRED"),call(grant,"b".repeat(64)))
        assertEquals(IndependentWebSearchClient.Result.Failed("USER_CANCELLED"),call(grant,fp,ProviderChatCancellation().also {it.cancel()}))
        assertEquals(0,requests)
    }
    @Test fun `native sources are independent from facts and generated URLs`() {
        val credential="test-only".toCharArray()
        val client=IndependentWebSearchClient(object:ProviderChatTransport {override fun execute(request:ProviderChatRequest,key:CharArray):ProviderChatOutcome {
            assertEquals(IndependentSearchServiceGrant.ENDPOINT,request.endpoint)
            val body=org.json.JSONObject(request.jsonBody);assertEquals("search_pro",body.getString("search_engine"));assertFalse(body.getBoolean("search_intent"));assertEquals("noLimit",body.getString("search_recency_filter"))
            return ProviderChatOutcome.HttpResponse(200,"""{"text":"https://invented.example.org","search_result":[{"link":"http://127.0.0.1/","content":"private"},{"link":"https://python.org/release?version=3.14","content":"public release date","title":"Python"}]}""")
        }})
        val r=client.search("public fact","public-1",grant,fp,{credential},null) as IndependentWebSearchClient.Result.Completed
        assertEquals(listOf("https://python.org/release?version=3.14"),r.sources.map {it.url});assertTrue(credential.all {it=='\u0000'})
    }
    @Test fun `empty completion stays empty and unknown network is not retried`() {
        var calls=0
        var response:ProviderChatOutcome=ProviderChatOutcome.HttpResponse(200,"""{"search_result":[]}""")
        val client=IndependentWebSearchClient(object:ProviderChatTransport {override fun execute(r:ProviderChatRequest,c:CharArray):ProviderChatOutcome {calls++;return response}})
        val empty=client.search("public fact","public-1",grant,fp,{charArrayOf('x')},null) as IndependentWebSearchClient.Result.Completed
        assertTrue(empty.sources.isEmpty())
        response=ProviderChatOutcome.TimedOut
        assertEquals(IndependentWebSearchClient.Result.Failed("TIMEOUT",true),client.search("public fact","public-2",grant,fp,{charArrayOf('x')},null))
        assertEquals(2,calls)
    }
    @Test fun `long Unicode clues keep their complete tail in bounded segments`() {
        val clue="🌏公开事项日期与完整长尾".repeat(20)
        assertNull(IndependentWebSearchClient.queries(clue))
        val long="Python 3.14.0正式发布，核验官方2025年10月发布日期；Rust 1.90.0正式发布，核验官方2025年9月发布日期；Go 1.24.0正式发布，核验官方2025年2月发布日期。"
        val parts=requireNotNull(IndependentWebSearchClient.queries(long));assertTrue(parts.all {it.codePointCount(0,it.length)<=70})
        assertTrue(parts.any {it.contains("Go 1.24.0")});assertTrue(parts.any {it.contains("Rust 1.90.0")})
    }
    @Test fun `Chinese entities and numeric tokens are not rebound or cut`() {
        val q=requireNotNull(IndependentWebSearchClient.queries("中国科学院于2025年发布公开科学报告，报告涉及科学研究进展与公开数据核验，请核验该报告公开发布的日期与官方来源；北京大学于2025年发布招生简章，核验简章公开时间与2025年招生人数1,000人的表述；清华大学于2025年发布本科招生章程，核验2025年章程公开日期及官方来源。"))
        assertTrue(q.any {it.contains("北京大学") && it.contains("2025年")})
        assertTrue(q.any {it.contains("清华大学") && it.contains("2025年")})
        assertTrue(q.filter {it.contains("招生")}.all {!it.contains("中国科学院")})
        assertTrue(q.any {it.contains("1,000")})
        val single="中国科学院于2025年发布公开科学报告并且北京大学于2025年发布招生简章以及清华大学于2025年发布本科招生章程同时中国人民大学于2025年发布公开招生章程"
        val planned=requireNotNull(IndependentWebSearchClient.queries(single))
        assertTrue(planned.all {it.codePointCount(0,it.length)<=70})
        assertTrue(planned.any {it.contains("中国人民大学") && it.contains("2025年")})
    }

}
