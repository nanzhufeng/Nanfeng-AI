package com.nanzhufeng.ai.domain

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ChatContinuationTest {
    @Test fun `shared unicode and overlap fixtures preserve literal checkpoint`() {
        val fixtures=JSONArray(File("../protocol/fixtures/chat-continuation-v1.json").readText())
        for(i in 0 until fixtures.length()) {
            val sample=fixtures.getJSONObject(i);val prefix=sample.getString("checkpoint");val generated=sample.getString("generated")
            assertEquals(sample.getString("name"),sample.getString("suffix"),ChatContinuation.stripEcho(prefix,generated))
            assertEquals(prefix+sample.getString("suffix"),ChatContinuation.join(prefix,generated))
        }
    }
    @Test fun `streamed checkpoint echo is withheld across every possible chunk boundary`() {
        val prefix="这是需要保留的长断点文字，恢复过程中不能重写或者重复。"
        val output=prefix+"之后继续生成的新内容。"
        for (cut in 0..output.length) {
            val stream=ChatContinuation.Stream(prefix)
            val emitted=stream.append(output.substring(0,cut))+stream.append(output.substring(cut))+stream.finish()
            assertEquals("之后继续生成的新内容。",emitted)
        }
    }
    @Test fun `new stream text is emitted without erasing the checkpoint`() {
        val stream=ChatContinuation.Stream("这是一整段已生成的回答，必须在重试后完整保留。")
        assertEquals("后续",stream.append("后续"));assertEquals("内容。",stream.append("内容。"));assertEquals("",stream.finish())
    }
}
