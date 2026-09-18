package com.nanzhufeng.ai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeSyntaxTest {
    @Test fun `TOML offsets preserve exact source and distinguish semantic tokens`() {
        val code = "# 注释\nmodel = \"deepseek-v4-flash\"\n[model_providers.deepseek]\nurl = \"https://example.test/#frag\"\ncount = 42\nenabled = true"
        assertEquals(listOf(
            "comment" to "# 注释", "key" to "model", "string" to "\"deepseek-v4-flash\"",
            "section" to "[model_providers.deepseek]", "key" to "url", "string" to "\"https://example.test/#frag\"",
            "key" to "count", "number" to "42", "key" to "enabled", "keyword" to "true",
        ), codeSyntaxSpans(code, "toml").map { it.kind to code.substring(it.start, it.end) })
    }
    @Test fun `unknown and very large sources retain plain presentation`() {
        assertTrue(codeSyntaxSpans("unchanged = 'text'", "unknown").isEmpty())
        assertTrue(codeSyntaxSpans("x".repeat(100001), "toml").isEmpty())
    }
    @Test fun `JSON keys and strings preserve embedded comment markers and escaped quotes`() {
        val code = "{\"url\":\"https://example.test/#literal\",\"ok\":true}"
        assertEquals(listOf("key", "string", "key", "keyword"), codeSyntaxSpans(code, "json").map { it.kind })
    }
}
