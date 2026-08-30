package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Test

class AutomaticWebSearchPolicyTest {
    @Test
    fun `current-information requests use each provider official supported route`() {
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
    fun `evergreen questions retain the selected ordinary route`() {
        assertEquals(
            ChatRequestOptions.Standard,
            AutomaticWebSearchPolicy.requestOptions(ProviderId.OPENROUTER, ChatRequestOptions.Standard, true, "解释什么是递归", emptyList()),
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
    fun `local document lookup wording does not enable a public web tool`() {
        val attachment = ChatAttachment(ChatAttachmentKind.FILE, "text/markdown", "design.md", "# design".toByteArray())

        assertEquals(
            ChatRequestOptions.Standard,
            AutomaticWebSearchPolicy.requestOptions(
                ProviderId.OPENROUTER,
                ChatRequestOptions.Standard,
                true,
                "请查找附件文档中的根因并给出修复方案",
                listOf(attachment),
            ),
        )
    }
}
