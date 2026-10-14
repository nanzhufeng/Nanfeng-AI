package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.*
import java.io.File
import java.time.Instant
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Public-only request prepared by Android, sent by the native credential owner, then replayed. */
@RunWith(RobolectricTestRunner::class)
class RelayCoreAttachmentWireReplayTest {
    @Test fun `public multiple attachment wire passes actual Android decoder and item coverage`() {
        val mode = System.getenv("NFAI_ANDROID_PUBLIC_WIRE_MODE")
        assumeTrue(mode in setOf("prepare", "replay"))
        val directory = File(requireNotNull(System.getenv("NFAI_ANDROID_PUBLIC_WIRE_DIR")))
        directory.mkdirs()
        val count = (System.getenv("NFAI_ANDROID_PUBLIC_WIRE_COUNT") ?: "6").toInt()
        require(count in 1..12)
        val claims = if(System.getenv("NFAI_ANDROID_PUBLIC_WIRE_CASE")=="current") listOf(
            "截至2026年10月12日，Python官网最新稳定版仍为Python 3.14.0。" to "https://www.python.org/downloads/",
            "截至2026年10月12日，Rust官网最新稳定版仍为Rust 1.90.0。" to "https://www.rust-lang.org/",
            "截至2026年10月12日，Node.js官网最新LTS仍为Node.js 22。" to "https://nodejs.org/en/about/previous-releases",
            "截至2026年10月12日，Go官网最新稳定版仍为Go 1.24。" to "https://go.dev/dl/",
            "截至2026年10月12日，Kotlin官网最新稳定版仍为Kotlin 2.1。" to "https://kotlinlang.org/docs/releases.html",
            "截至2026年10月12日，PostgreSQL官网最新稳定版仍为PostgreSQL 17。" to "https://www.postgresql.org/",
        ) else listOf(
            "Python 3.14.0在2025年10月8日发布。" to "https://www.python.org/downloads/release/python-3140/",
            "Rust 1.90.0在2025年9月18日发布。" to "https://blog.rust-lang.org/2025/09/18/Rust-1.90.0/",
            "SQLite支持事务。" to "https://www.sqlite.org/transaction.html",
            "PostgreSQL使用多版本并发控制。" to "https://www.postgresql.org/docs/current/mvcc.html",
            "Kotlin支持与Java互操作。" to "https://kotlinlang.org/docs/java-interop.html",
            "Node.js使用V8执行JavaScript。" to "https://nodejs.org/en/about/",
            "Svelte通过编译组件创建界面。" to "https://svelte.dev/docs/svelte/overview",
            "React用于构建用户界面。" to "https://react.dev/",
            "Git是分布式版本控制系统。" to "https://git-scm.com/about/distributed",
            "Docker以容器打包和运行应用。" to "https://docs.docker.com/get-started/docker-overview/",
            "Swift是一种开源编程语言。" to "https://www.swift.org/about/",
            "Go支持通过channel进行通信。" to "https://go.dev/doc/effective_go#channels",
        )
        val material = claims.take(count).chunked(2).mapIndexed { index, rows ->
            "### 附件：public-${index + 1}.md\n" + rows.mapIndexed { n, row -> "(${n + 1}) ${row.first}\n相关链接：${row.second}" }.joinToString("\n")
        }.joinToString("\n")
        val plan = AttachmentWebSearchPlan.build(material)
        assertEquals(count, plan.items.size)
        val search = ResolvedModel(ProviderId.RELAYCORE_GPT,"gpt-6.1-sol","6.1 Sol",ModelCapabilities(true,false,true),131072,ModelHealth.AVAILABLE,null,null,maxOutputTokens=2048)
        val answer = search.copy(providerId=ProviderId.RELAYCORE_CLAUDE,modelId="claude-sonnet-5-5")
        val routing = NormalChatRoutingSnapshot(mapOf(ModelPresetId.RELAYCORE_SONNET_5_5 to answer,RelayCoreWebSearchCompanion.preset to search),setOf(ModelPresetId.RELAYCORE_SONNET_5_5,RelayCoreWebSearchCompanion.preset),false,webSearchEnabled=true)
        val question = "分析报告，必须实际联网核验各项，不能用已有知识替代搜索。PUBLIC_WIRE_FIXTURE_V1"
        val choice = ComposerModelRoutingCatalog.relayCore.single {ModelPresetId.RELAYCORE_SONNET_5_5 in it.routes}.id
        val approval = NormalChatEgressAuthorization.forUserSend(ConversationDraft(text=question,updatedAt=Instant.EPOCH),1,choice,routing)
        val transport = object : ProviderChatTransport {
            override fun execute(request: ProviderChatRequest, credential: CharArray): ProviderChatOutcome {
                if(mode=="prepare") {
                    directory.resolve("request.json").writeText(request.jsonBody)
                    return ProviderChatOutcome.TimedOut
                }
                val adapter = RelayCoreChatAdapter(ProviderId.RELAYCORE_GPT)
                val options = ChatRequestOptions(OfficialWebSearchRoute.RELAYCORE_GPT_RESPONSES)
                val parsed = directory.resolve("wire.sse").inputStream().use {ProviderSseDecoder.read(it,{}, {adapter.decodeStreamingEvent(it,options)},ProviderStreamTextMode.RESPONSES_API)}
                val headers = Regex("\\[(A[0-9]+)\\]").findAll(parsed.text).map {it.groupValues[1]}.toList()
                println("PUBLIC_ANDROID_WIRE count=$count finish=${parsed.finishReason} sources=${parsed.webSources.size} searched=${parsed.webSearchPerformed} output=${parsed.outputTokens} reasoning=${parsed.reasoningTokens} headers=$headers")
                directory.resolve("decoded-public.json").writeText(JSONObject().put("text",parsed.text).put("sources",org.json.JSONArray(parsed.webSources.map {it.url})).put("finish",parsed.finishReason).put("reasoningTokens",parsed.reasoningTokens).toString())
                return ProviderChatOutcome.StreamedResponse(200,parsed.text,parsed.reasoning,parsed.inputTokens,parsed.outputTokens,parsed.reportedProviderCost,parsed.toolCallEncountered,webSources=parsed.webSources,finishReason=parsed.finishReason,webSearchPerformed=parsed.webSearchPerformed,returnedModelId=parsed.returnedModelId,reasoningTokens=parsed.reasoningTokens)
            }
        }
        val result = RelayCoreWebSearchCompanion(transport).search(search,"https://api.relaycore.cn/v1",question,emptyList(),approval,{"synthetic-only-no-secret".toCharArray()},null,{},materialText=material)
        if(mode=="replay") assertTrue("Android retrieval must retain item evidence: ${(result as? RelayCoreWebSearchCompanion.Result.Failed)?.code}",result is RelayCoreWebSearchCompanion.Result.Ready)
    }
}
