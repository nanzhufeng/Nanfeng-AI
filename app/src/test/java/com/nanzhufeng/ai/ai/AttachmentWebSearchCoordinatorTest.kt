package com.nanzhufeng.ai.ai

import org.junit.Assert.*
import org.junit.Test

class AttachmentWebSearchCoordinatorTest {
    @Test fun `failed supplement cannot erase earlier native unverified evidence`() {
        val two=AttachmentWebSearchPlan.Plan(plan().items.take(2));var calls=0
        val result=AttachmentWebSearchCoordinator.run(two,{false},{batch,_->
            if(++calls==1) reply(batch,true) else RelayCoreWebSearchCompanion.Result.Failed("WEB_SEARCH_SCOPE_MISMATCH")
        }) as RelayCoreWebSearchCompanion.Result.Ready
        assertEquals(3,calls);assertEquals(2,result.coveredCount);assertTrue(result.completeScope)
        assertTrue(result.reply.text.contains("证据不足，仍未核实"));assertEquals(2,result.reply.webSources.size)
        assertFalse(result.reply.text.contains("未完成查询"))
    }
    private fun plan()=AttachmentWebSearchPlan.build((1..20).joinToString("\n") {"- 公开事项$it：机构于2026年发布第${it}项数据。"})
    private fun reply(batch:AttachmentWebSearchPlan.Plan,unverified:Boolean=false):RelayCoreWebSearchCompanion.Result.Ready {
        val text=batch.items.joinToString("\n\n") {"${it.id} ${it.query}\n${if(unverified) "证据不足，仍未核实" else "已核实"}：已执行该项查询。\n来源：https://example.org/${it.id}"}
        return RelayCoreWebSearchCompanion.Result.Ready(ChatAdapterDecodedResult.Text("逐项核验结果：\n$text",10,2,webSearchPerformed=true,
            webSources=batch.items.map {ProviderWebSource("https://example.org/${it.id}","官方资料")}),itemEvidence=batch.items.associate {it.id to "${it.id} ${it.query}\n${if(unverified) "证据不足，仍未核实" else "已核实"}：已执行该项查询。\n来源：https://example.org/${it.id}"})
    }
    @Test fun `known empty single query gets one supplement but unknown does not resend`() {
        val single=AttachmentWebSearchPlan.Plan(plan().items.take(1));var calls=0
        val result=AttachmentWebSearchCoordinator.run(single,{false},{batch,_->if(++calls==1) RelayCoreWebSearchCompanion.Result.Failed("WEB_SEARCH_NO_SOURCES") else reply(batch)}) as RelayCoreWebSearchCompanion.Result.Ready
        assertEquals(2,calls);assertTrue(result.completeScope)
        calls=0;val fail=AttachmentWebSearchCoordinator.run(single,{false},{_,_->calls++;RelayCoreWebSearchCompanion.Result.Failed("WEB_SEARCH_NO_SOURCES")})
        assertEquals(2,calls);assertTrue(fail is RelayCoreWebSearchCompanion.Result.Failed)
    }
    @Test fun `complete material reaches search and final evidence past the old 12 limit`() {
        val seen=mutableListOf<String>();val result=AttachmentWebSearchCoordinator.run(plan(),{false},{batch,_ ->seen+=batch.items.map {it.id};reply(batch)}) as RelayCoreWebSearchCompanion.Result.Ready
        assertEquals((1..20).map {"A$it"},seen);assertEquals(20,result.coveredCount);assertTrue(result.completeScope)
        assertTrue(result.answerContext().contains("公开事项20"));assertEquals(20,result.reply.webSources.size)
    }
    @Test fun `insufficient batch results receive one targeted query per item`() {
        var calls=0;val singles=mutableListOf<String>();val result=AttachmentWebSearchCoordinator.run(plan(),{false},{batch,_ ->calls++;if(batch.items.size==1) singles+=batch.items.single().id;reply(batch,batch.items.size>1)}) as RelayCoreWebSearchCompanion.Result.Ready
        assertEquals((1..20).map {"A$it"},singles);assertEquals(25,calls);assertTrue(result.completeScope);assertFalse(result.reply.text.contains("证据不足"))
    }
    @Test fun `unknown child stops searching without a resend but retains prior evidence`() {
        val saved=mutableSetOf<String>()
        val cache=object:AttachmentSearchCheckpointCache {
            override fun load(item:AttachmentWebSearchPlan.Item):RelayCoreWebSearchCompanion.Result.Ready?=null
            override fun save(item:AttachmentWebSearchPlan.Item,evidence:String,result:RelayCoreWebSearchCompanion.Result.Ready) {saved+=item.id}
        }
        var calls=0;val result=AttachmentWebSearchCoordinator.run(plan(),{false},{batch,_ ->if(++calls==2) RelayCoreWebSearchCompanion.Result.Failed("NETWORK",unknown=true) else reply(batch)},cache=cache) as RelayCoreWebSearchCompanion.Result.Failed
        assertEquals(2,calls);assertTrue(result.unknown);assertEquals("NETWORK",result.code)
        assertEquals(setOf("A1","A2","A3","A4"),saved)

    }
    @Test fun `cancellation prevents any later batch and final answer`() {
        var cancelled=false;var calls=0;val result=AttachmentWebSearchCoordinator.run(plan(),{cancelled},{batch,_ ->calls++;cancelled=true;reply(batch)})
        assertEquals(1,calls);assertEquals("USER_CANCELLED",(result as RelayCoreWebSearchCompanion.Result.Failed).code)
    }
    @Test fun `whole task deadline leaves explicit scope incompleteness`() {
        var elapsed=0L;var calls=0;val result=AttachmentWebSearchCoordinator.run(plan(),{false},{batch,_ ->calls++;elapsed=600001;reply(batch)},{elapsed}) as RelayCoreWebSearchCompanion.Result.Ready
        assertEquals(1,calls);assertFalse(result.completeScope);assertTrue(result.reply.text.contains("检索时限或次数上限"))
    }
}
