package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.NormalChatRoutingSnapshot
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QuestionWebResearchTest {
    @Test fun `explicit gap headings preserve other sourced items and never become verified facts`() {
        val fixture=org.json.JSONObject(java.io.File("../protocol/fixtures/openrouter-exa-companion-v1.json").readText())
        val response=fixture.getJSONObject("response")
        val original=response.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        val prefix=original.substringBefore("[A5]")
        val plan=QuestionWebResearch.plan(fixture.getString("question").replace("某上市公司","上市公司"))
        for(heading in listOf("**缺口**：","**资料充分，无关键缺口：**","证据不足：")) {
            val entries=linkedMapOf<String,String>()
            val evidence=AttachmentWebSearchPlan.evidenceWithLimit(plan,prefix+"[A5] $heading 尚缺净债务比较数据。",listOf("https://example.test/filing"),false,2000) {id,text ->entries[id]=text}
            assertNotNull(evidence);assertEquals(5,entries.size);assertTrue(entries["A1"]!!.contains("已核实"));assertTrue(entries["A5"]!!.contains("证据不足，仍未核实"))
        }
        assertNull(AttachmentWebSearchPlan.evidenceWithLimit(plan,prefix+"[A5] 缺口：该事项尚未查询。",listOf("https://example.test/filing"),false,2000))
        assertNull(AttachmentWebSearchPlan.evidenceWithLimit(plan,prefix+"[A5] 缺口：尚缺比较数据。",emptyList(),false,2000))
    }
    @Test fun `unknown or high first retrieval cost stops automatic supplements`() {
        for(cost in listOf<ProviderReportedCost?>(null,ProviderReportedCost(10001,"USD","fixture"))) {
            val plan=QuestionWebResearch.plan("公开公司价格和经营数据？")
            var calls=0
            val result=AttachmentWebSearchCoordinator.run(plan,{false},{batch,_ ->
                calls++
                RelayCoreWebSearchCompanion.Result.Ready(ChatAdapterDecodedResult.Text("evidence",1,1,reportedProviderCost=cost),
                    itemEvidence=batch.items.associate {it.id to "证据不足，仍未核实"})
            },batchSize=32,supplementSingleUnverified=true) as RelayCoreWebSearchCompanion.Result.Ready
            assertEquals(1,calls);assertFalse(result.completeScope);assertTrue(result.answerContext().contains("证据不足，仍未核实"))
        }
    }
    @Test fun `all unresolved ordinary scopes share one supplement and cannot loop`() {
        val plan=QuestionWebResearch.plan("某公司下跌是否准确？原因是什么？估值如何？")
        val batches=mutableListOf<List<String>>()
        val result=AttachmentWebSearchCoordinator.run(plan,{false},{batch,_ ->
            batches+=batch.items.map {it.id}
            val entries=batch.items.associate {it.id to "证据不足，仍未核实：官方数据未公布。来源：https://example.test/filing"}
            RelayCoreWebSearchCompanion.Result.Ready(ChatAdapterDecodedResult.Text("public evidence",1,1,reportedProviderCost=ProviderReportedCost(100,"USD","fixture")),itemEvidence=entries)
        },batchSize=32,supplementSingleUnverified=true) as RelayCoreWebSearchCompanion.Result.Ready
        assertEquals(2,batches.size)
        assertEquals(plan.items.map {it.id},batches[0]);assertEquals(batches[0],batches[1])
        assertTrue(result.answerContext().contains("证据不足，仍未核实"))
    }

    @Test fun `existing native receipt keeps valuation evidence through Android production helper without network`() {
        val root=System.getenv("NFAI_QUESTION_OUTPUT")
        org.junit.Assume.assumeNotNull(root)
        val saved=org.json.JSONObject(java.io.File(root!!,"retrieval-2.json").readText())
        val sourceRows=saved.getJSONArray("sources")
        val sources=(0 until sourceRows.length()).map {sourceRows.getJSONArray(it).let {row ->ProviderWebSource(row.getString(1),row.getString(0))}}
        val question="英特尔凌晨暴跌23%，什么原因？基本面是否出问题了？这家公司是否值得投资？分析一下"
        val model=com.nanzhufeng.ai.domain.ResolvedModel(com.nanzhufeng.ai.domain.ProviderId.OPENROUTER,"openai/gpt-6-luna","Luna",com.nanzhufeng.ai.domain.ModelCapabilities(true,false,true),131072,com.nanzhufeng.ai.domain.ModelHealth.AVAILABLE,null,null,maxOutputTokens=8192)
        val preset=com.nanzhufeng.ai.domain.ModelPresetId.GPT_6_LUNA
        val snapshot=NormalChatRoutingSnapshot(mapOf(preset to model),setOf(preset),false)
        val draft=com.nanzhufeng.ai.domain.ConversationDraft(text=question,updatedAt=java.time.Instant.EPOCH)
        val choice=com.nanzhufeng.ai.domain.ComposerModelRoutingCatalog.choices.single {preset in it.routes}.id
        val approval=com.nanzhufeng.ai.domain.NormalChatEgressAuthorization.forUserSend(draft,1,choice,snapshot)
        val helper=RelayCoreWebSearchCompanion(object:ProviderChatTransport {
            override fun execute(request:ProviderChatRequest,credential:CharArray)=ProviderChatOutcome.StreamedResponse(200,saved.getString("text"),null,null,null,null,false,webSources=sources,webSearchPerformed=true,returnedModelId=model.modelId)
        })
        val one=AttachmentWebSearchPlan.Plan(listOf(QuestionWebResearch.plan(question).items[3]))
        val result=helper.search(model,"https://openrouter.ai/api/v1",question,emptyList(),approval,{"offline-fixture".toCharArray()},null,{},batchPlan=one,ordinaryQuestionResearch=true)
        assertTrue(result is RelayCoreWebSearchCompanion.Result.Ready)
        val evidence=(result as RelayCoreWebSearchCompanion.Result.Ready).answerContext()
        assertTrue(evidence.contains("自由现金流"));assertTrue(evidence.contains("负债"));assertTrue(result.reply.webSources.isNotEmpty())
    }

    @Test fun `plain question retains every subquestion premise and required inputs`() {
        val plan=QuestionWebResearch.plan("某上市公司跌幅23%是否准确？业绩为什么恶化？目前估值是否合理？")
        assertEquals(5,plan.items.size)
        assertTrue(plan.items[1].query.endsWith("某上市公司跌幅23%是否准确"))
        assertTrue(plan.items[2].query.endsWith("业绩为什么恶化"))
        assertTrue(plan.items[3].query.endsWith("目前估值是否合理"))
        assertTrue(plan.items.last().query.contains("所必需的最新公开数据"))
        assertFalse(plan.limited)
    }
    @Test fun `ordinary research retries single missing evidence once without hiding the gap`() {
        val item=QuestionWebResearch.plan("比较两个公开产品？").items.last()
        var calls=0
        val result=AttachmentWebSearchCoordinator.run(AttachmentWebSearchPlan.Plan(listOf(item)),{false},{batch,_ ->
            calls++
            val reply=ChatAdapterDecodedResult.Text("[${item.id}] 未核实\n公开产品当前价格资料不足。来源：https://example.test/prices",1,1,
                webSources=listOf(ProviderWebSource("https://example.test/prices","Price")),webSearchPerformed=true,reportedProviderCost=ProviderReportedCost(100,"USD","fixture"))
            val entries=linkedMapOf<String,String>();val evidence=requireNotNull(AttachmentWebSearchPlan.evidence(batch,reply.text,reply.webSources.map {it.url},allowUnverified=true,onItem={id,text ->entries[id]=text}))
            RelayCoreWebSearchCompanion.Result.Ready(reply.copy(text=evidence),itemEvidence=entries)
        },supplementSingleUnverified=true) as RelayCoreWebSearchCompanion.Result.Ready
        assertEquals(2,calls);assertEquals(1,result.coveredCount);assertTrue(result.reply.text.contains("证据不足，仍未核实"))
    }
    @Test fun `unknown first request stops supplement and cannot become partial ready`() {
        var calls=0
        val result=AttachmentWebSearchCoordinator.run(QuestionWebResearch.plan("公开事件原因？"),{false},{_,_->calls++;RelayCoreWebSearchCompanion.Result.Failed("TIMEOUT",unknown=true)},supplementSingleUnverified=true)
        assertEquals(1,calls);assertTrue((result as RelayCoreWebSearchCompanion.Result.Failed).unknown)
    }
    @Test fun `URL queries and decimal versions survive planning and oversized scope is explicit`() {
        val p=QuestionWebResearch.plan("核对 https://example.test/release?v=3.14.0&x=1")
        assertEquals(3,p.items.size);assertTrue(p.items[1].query.contains("?v=3.14.0&x=1"))
        val large=QuestionWebResearch.plan("字".repeat(6001));assertTrue(large.limited);assertTrue(large.items.isEmpty())
    }
}
