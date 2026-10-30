package com.nanzhufeng.ai.acceptance

import com.nanzhufeng.ai.domain.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PublicAcceptanceBudgetPolicyTest {
    private val model=ResolvedModel(ProviderId.OPENROUTER,"anthropic/claude-opus-5.5","Opus",ModelCapabilities(true,true,true),200000,ModelHealth.UNKNOWN,null,null,4096)
    private fun body()=JSONObject("""{"model":"anthropic/claude-opus-5.5","messages":[{"role":"user","content":"public fixture"}],"max_tokens":4096}""")
    @Test fun `native search cannot pass a small fixed reserve even with available budget`() {
        for(extra in listOf("""{"tools":[{"type":"openrouter:web_search"}]}""","""{"plugins":[{"id":"web"}]}""","""{"enable_search":true}""")) {
            val request=body();val fields=JSONObject(extra);fields.keys().forEach {request.put(it,fields.get(it))}
            val error=assertThrows(IllegalStateException::class.java) {PublicAcceptanceBudgetPolicy.requireAllowance(request,model,2401600,4000000,1000000)}
            assertEquals("PUBLIC_ACCEPTANCE_UNBOUNDED_TOOL_COST",error.message)
        }
    }
    @Test fun `media input is rejected before a paid call`() {
        val request=body().put("messages",org.json.JSONArray("""[{"role":"user","content":[{"type":"image_url","image_url":{"url":"data:image/png;base64,fixture"}}]}]"""))
        assertThrows(IllegalStateException::class.java) {PublicAcceptanceBudgetPolicy.requireAllowance(request,model,0,10000000,1000000)}
    }
    @Test fun `long text and output increase reservation without resetting existing cumulative spend`() {
        val request=body().put("messages",org.json.JSONArray().put(JSONObject().put("role","user").put("content","a".repeat(60000))))
        val reserved=PublicAcceptanceBudgetPolicy.requireAllowance(request,model,0,10000000,1000000)
        assertTrue(reserved>1000000)
        assertThrows(IllegalStateException::class.java) {PublicAcceptanceBudgetPolicy.requireAllowance(request,model,10000000-reserved+1,10000000,1000000)}
        assertEquals(reserved,PublicAcceptanceBudgetPolicy.requireAllowance(request,model,10000000-reserved,10000000,1000000))
    }
    @Test fun `bounded text does not invent or require a provider context limit`() {
        val withoutContext=model.copy(contextWindowTokens=null)
        assertEquals(PublicAcceptanceBudgetPolicy.requireAllowance(body(),model,0,10000000,1000000),
            PublicAcceptanceBudgetPolicy.requireAllowance(body(),withoutContext,0,10000000,1000000))
    }
    @Test fun `unknown model or output bound is never treated as a free request`() {
        assertThrows(IllegalStateException::class.java) {PublicAcceptanceBudgetPolicy.requireAllowance(body(),null,0,10000000,1000000)}
        val request=body();request.remove("max_tokens")
        assertThrows(IllegalStateException::class.java) {PublicAcceptanceBudgetPolicy.requireAllowance(request,model,0,10000000,1000000)}
    }
}
