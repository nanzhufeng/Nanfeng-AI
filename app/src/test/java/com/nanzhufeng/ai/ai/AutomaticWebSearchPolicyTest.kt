package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticWebSearchPolicyTest {
    @Test
    fun `only absolute HTTP sources with a real host can prove live search`() {
        listOf(
            "https://",
            "https://not a url",
            "https:///missing-host",
            "ftp://example.test/file",
            "https://user@example.test/private",
        ).forEach { invalid ->
            assertNull(ProviderWebSource.fromProvider(invalid, "invalid"))
            assertFalse(runCatching { ProviderWebSource(invalid, "invalid") }.isSuccess)
        }

        val source = ProviderWebSource.fromProvider(" https://example.test/policy ", " 政策原文 ")
        assertEquals(ProviderWebSource("https://example.test/policy", "政策原文"), source)
        assertEquals("WEB_SEARCH_SUCCEEDED_WITH_SOURCES", WebSearchGroundingPolicy.completedAuditStatus(
            ChatRequestOptions(OfficialWebSearchRoute.QWEN_RESPONSES),
            listOf(requireNotNull(source)), streamed = false,
        ))
    }

    @Test
    fun `enabled search uses each provider official supported route`() {
        assertEquals(
            OfficialWebSearchRoute.OPENROUTER_SERVER_TOOL,
            AutomaticWebSearchPolicy.requestOptions(ProviderId.OPENROUTER, ChatRequestOptions.Standard, true, "查询今天的 AI 新闻", emptyList()).webSearchRoute,
        )
        assertEquals(
            OfficialWebSearchRoute.QWEN_RESPONSES,
            AutomaticWebSearchPolicy.requestOptions(ProviderId.QWEN, ChatRequestOptions.Standard, true, "英伟达最新财报", emptyList()).webSearchRoute,
        )
        assertEquals(
            OfficialWebSearchRoute.DEEPSEEK_RESPONSES,
            AutomaticWebSearchPolicy.requestOptions(ProviderId.DEEPSEEK, ChatRequestOptions.Standard, true, "现在的美元汇率", emptyList()).webSearchRoute,
        )
        assertEquals(
            OfficialWebSearchRoute.ZHIPU_CHAT_COMPLETIONS,
            AutomaticWebSearchPolicy.requestOptions(ProviderId.ZHIPU, ChatRequestOptions.Standard, true, "现在的美元汇率", emptyList()).webSearchRoute,
        )
    }

    @Test
    fun `Qwen attachment search retains chat completions while text-only search uses Responses`() {
        val attachment = ChatAttachment(ChatAttachmentKind.IMAGE, "image/png", "chart.png", byteArrayOf(1))

        assertEquals(
            OfficialWebSearchRoute.QWEN_RESPONSES,
            AutomaticWebSearchPolicy.requestOptions(ProviderId.QWEN, ChatRequestOptions.Standard, true, "查询今天的股价", emptyList()).webSearchRoute,
        )
        assertEquals(
            OfficialWebSearchRoute.QWEN_CHAT_COMPLETIONS,
            AutomaticWebSearchPolicy.requestOptions(ProviderId.QWEN, ChatRequestOptions.Standard, true, "结合图片查询今天的股价", listOf(attachment)).webSearchRoute,
        )
    }

    @Test
    fun `Qwen38 Max text search avoids the Responses tool loop without changing other Qwen models`() {
        assertEquals(
            OfficialWebSearchRoute.QWEN_CHAT_COMPLETIONS,
            AutomaticWebSearchPolicy.requestOptions(
                ProviderId.QWEN,
                ChatRequestOptions.Standard,
                true,
                "请问 MATCH 法案对阿斯麦股价有什么影响？",
                emptyList(),
                modelId = QWEN_3_8_MAX_MODEL_ID,
            ).webSearchRoute,
        )
        assertEquals(
            OfficialWebSearchRoute.QWEN_RESPONSES,
            AutomaticWebSearchPolicy.requestOptions(
                ProviderId.QWEN,
                ChatRequestOptions.Standard,
                true,
                "请问 MATCH 法案对阿斯麦股价有什么影响？",
                emptyList(),
                modelId = "qwen3.7-plus",
            ).webSearchRoute,
        )
    }

    @Test
    fun `disabled search blocks automatic and deep web routes`() {
        val deepRoute = ChatRequestOptions(OfficialWebSearchRoute.OPENROUTER_SERVER_TOOL)
        assertEquals(
            ChatRequestOptions.Standard,
            AutomaticWebSearchPolicy.requestOptions(ProviderId.OPENROUTER, deepRoute, false, "最新新闻", emptyList()),
        )
    }

    @Test
    fun `web-search routing preserves a previously selected reasoning effort`() {
        val high = ChatRequestOptions(reasoningEffort = ReasoningEffort.HIGH)

        assertEquals(
            ChatRequestOptions(OfficialWebSearchRoute.OPENROUTER_SERVER_TOOL, ReasoningEffort.HIGH),
            AutomaticWebSearchPolicy.requestOptions(ProviderId.OPENROUTER, high, true, "最新新闻", emptyList()),
        )
        assertEquals(
            ChatRequestOptions(OfficialWebSearchRoute.NONE, ReasoningEffort.HIGH),
            AutomaticWebSearchPolicy.requestOptions(ProviderId.OPENROUTER, high, false, "最新新闻", emptyList()),
        )
    }

    @Test
    fun `enabled search also grounds ordinary wording instead of guessing from keywords`() {
        assertEquals(
            OfficialWebSearchRoute.OPENROUTER_SERVER_TOOL,
            AutomaticWebSearchPolicy.requestOptions(ProviderId.OPENROUTER, ChatRequestOptions.Standard, true, "解释什么是递归", emptyList()).webSearchRoute,
        )
    }

    @Test
    fun `investment decision requests use current public information when search is enabled`() {
        assertEquals(
            OfficialWebSearchRoute.OPENROUTER_SERVER_TOOL,
            AutomaticWebSearchPolicy.requestOptions(
                ProviderId.OPENROUTER,
                ChatRequestOptions.Standard,
                true,
                "中证A500 和沪深300现在选哪个，如何配置仓位？",
                emptyList(),
            ).webSearchRoute,
        )
        assertEquals(
            OfficialWebSearchRoute.NONE,
            AutomaticWebSearchPolicy.requestOptions(
                ProviderId.OPENROUTER,
                ChatRequestOptions.Standard,
                false,
                "中证A500 和沪深300现在选哪个，如何配置仓位？",
                emptyList(),
            ).webSearchRoute,
        )
    }

    @Test
    fun `markdown and OCR attachment wording keeps the explicit web-search requirement`() {
        val attachment = ChatAttachment(ChatAttachmentKind.FILE, "text/markdown", "design.md", "# design".toByteArray())

        assertEquals(
            OfficialWebSearchRoute.DEEPSEEK_RESPONSES,
            AutomaticWebSearchPolicy.requestOptions(
                ProviderId.DEEPSEEK,
                ChatRequestOptions.Standard,
                true,
                "请分析这份文件",
                listOf(attachment),
            ).webSearchRoute,
        )
        assertEquals(
            OfficialWebSearchRoute.ZHIPU_CHAT_COMPLETIONS,
            AutomaticWebSearchPolicy.requestOptions(
                ProviderId.ZHIPU,
                ChatRequestOptions.Standard,
                true,
                "请分析 OCR Markdown 材料",
                listOf(attachment),
            ).webSearchRoute,
        )
    }

    @Test
    fun `DeepSeek Responses accepts its documented server-side search action without public URLs`() {
        OfficialWebSearchRoute.entries.forEach { route ->
            for (streamed in listOf(false, true)) {
                val options = ChatRequestOptions(route)
                assertEquals(
                    if (options.liveWebSearch) "WEB_SEARCH_COMPLETED_WITHOUT_SOURCES" else if (streamed) "STREAM_SUCCEEDED" else "SUCCEEDED",
                    WebSearchGroundingPolicy.completedAuditStatus(options, emptyList(), streamed),
                )
                if (options.liveWebSearch) assertEquals(
                    if (streamed) "WEB_SEARCH_STREAM_SUCCEEDED_WITH_SOURCES" else "WEB_SEARCH_SUCCEEDED_WITH_SOURCES",
                    WebSearchGroundingPolicy.completedAuditStatus(options, listOf(ProviderWebSource("https://example.test/policy", "政策原文")), streamed),
                )
            }
        }
    }
}
