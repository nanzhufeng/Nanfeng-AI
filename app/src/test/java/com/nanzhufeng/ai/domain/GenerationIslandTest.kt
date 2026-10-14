package com.nanzhufeng.ai.domain
import org.junit.Assert.*
import org.junit.Test
class GenerationIslandTest {
    @Test fun `saved checkpoint and terminal outcome survive stale presentation events`() {
        val ledger = GenerationIslandLedger(); val id = ConversationId("chat")
        ledger.begin(ConversationSurface.CHAT,id,0)
        assertEquals("等待模型输出",ledger.snapshot().single().stage)
        ledger.checkpoint(ConversationSurface.CHAT,id,123,1000)
        assertEquals("已保存 123 字 · 2 秒",ledger.snapshot().single().detail(2000))
        ledger.finish(ConversationSurface.CHAT,id,"UNKNOWN",3000)
        ledger.checkpoint(ConversationSurface.CHAT,id,999,4000)
        assertFalse(ledger.snapshot().single().running)
        assertEquals(123,ledger.snapshot().single().savedCharacters)
        assertEquals("生成中断",ledger.snapshot().single().stage)
    }
    @Test fun `equal conversation ids remain isolated and duplicate begin does not reset progress`() {
        val ledger=GenerationIslandLedger(); val id=ConversationId("same")
        ledger.begin(ConversationSurface.CHAT,id,0);ledger.checkpoint(ConversationSurface.CHAT,id,7,1000)
        ledger.begin(ConversationSurface.CHAT,id,2000); ledger.begin(ConversationSurface.WORK,id,2000)
        assertEquals(listOf(7,0),ledger.snapshot().map { it.savedCharacters })
        ledger.finish(ConversationSurface.CHAT,id,"COMPLETE",3000)
        assertTrue(ledger.snapshot().last().running)
        ledger.begin(ConversationSurface.CHAT,id,4000)
        assertEquals(0,ledger.snapshot().first().savedCharacters)
    }
    @Test fun `waiting callbacks do not invent output or percentage and terminal duration is frozen`() {
        val ledger=GenerationIslandLedger();val id=ConversationId("wait")
        ledger.begin(ConversationSurface.CHAT,id,0);ledger.checkpoint(ConversationSurface.CHAT,id,0,15000)
        assertEquals("等待模型输出",ledger.snapshot().single().stage)
        ledger.finish(ConversationSurface.CHAT,id,"CANCELLED",20000)
        assertEquals("已保存 0 字 · 20 秒",ledger.snapshot().single().detail(90000))
    }
    @Test fun `reading expanded completion pauses timeout and old results never resurrect`() {
        val id=ConversationId("p");val view=GenerationIslandPresentation()
        val old=GenerationIslandTask("old",ConversationSurface.CHAT,id,0,123,updatedAt=3000,outcome="COMPLETE")
        view.update(listOf(old),3000);view.toggle(3500)
        assertEquals(1,view.visible(60000).size);view.toggle(60000)
        assertEquals(1,view.visible(62000).size);assertTrue(view.visible(63000).isEmpty())
        val running=GenerationIslandTask("new",ConversationSurface.CHAT,id,70000)
        view.update(listOf(old,running),70000);view.toggle(70000)
        assertEquals(listOf("new"),view.visible(70001).map {it.key})
    }

    @Test fun `terminal counts reflect committed text after final whitespace normalization`() {
        val ledger=GenerationIslandLedger();val id=ConversationId("trim")
        ledger.begin(ConversationSurface.CHAT,id,0);ledger.checkpoint(ConversationSurface.CHAT,id,5,1000)
        ledger.finish(ConversationSurface.CHAT,id,"COMPLETE",2000,4)
        ledger.checkpoint(ConversationSurface.CHAT,id,5,3000)
        assertEquals(4,ledger.snapshot().single().savedCharacters)
    }

    @Test fun `interruption expires while expanded and does not hide a running peer`() {
        val ledger = GenerationIslandLedger(); val id = ConversationId("interrupted")
        ledger.begin(ConversationSurface.CHAT,id,0)
        val presentation = GenerationIslandPresentation()
        presentation.update(ledger.snapshot(),2000);presentation.toggle(2000)
        ledger.finish(ConversationSurface.CHAT,id,"UNKNOWN",3000)
        presentation.update(ledger.snapshot(),3000)
        assertEquals(1,presentation.visible(5900).size)
        assertTrue(presentation.visible(6100).isEmpty())
        assertFalse(presentation.expanded)
        ledger.begin(ConversationSurface.CHAT,ConversationId("peer"),6200)
        presentation.update(ledger.snapshot(),6200)
        assertEquals("CHAT:peer",presentation.visible(6200).single().key)
    }
    @Test fun `a fresh generation defaults compact even without an empty presentation tick`() {
        val view=GenerationIslandPresentation();val id=ConversationId("new")
        view.update(listOf(GenerationIslandTask("old",ConversationSurface.CHAT,id,0)),0)
        assertFalse(view.expanded);view.toggle(1000)
        view.update(listOf(GenerationIslandTask("new",ConversationSurface.CHAT,id,9000)),9000)
        assertFalse(view.expanded)
    }

}
