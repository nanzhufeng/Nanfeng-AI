package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.ModelCapabilities
import com.nanzhufeng.ai.domain.ModelHealth
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.ResolvedModel
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import com.nanzhufeng.ai.domain.*
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font

@RunWith(RobolectricTestRunner::class)
class UniversalChatAttachmentBridgeContractsTest {
    private val now = Instant.parse("2026-08-30T10:00:00Z")

    @Test fun `text only target stages image for the explicitly authorized reader without calling it`() {
        val result = UniversalChatAttachmentBridge().resolve(
            ConversationId("conversation-a"),
            model(ProviderId.DEEPSEEK, "deepseek-flash", vision = false, video = false),
            "分析这张图",
            listOf(ChatAttachment(ChatAttachmentKind.IMAGE, "image/png", "chart.png", byteArrayOf(1, 2, 3))),
            false,
        )

        assertTrue(result is ChatAttachmentBridgeResult.Ready)
        result as ChatAttachmentBridgeResult.Ready
        assertTrue(result.providerAttachments.isEmpty());assertEquals(1,result.readingAttachments.size)
    }

    @Test fun `non-native PDF stays local or rejects without falling back to OCR`() {
        val result = UniversalChatAttachmentBridge().resolve(
            ConversationId("conversation-b"),
            model(ProviderId.ZHIPU, "glm-5.3", vision = false, pdf = false, video = false),
            "整理",
            listOf(ChatAttachment(ChatAttachmentKind.PDF, "application/pdf", "scan.pdf", "%PDF-broken".toByteArray())),
            false,
        )

        assertFalse(result is ChatAttachmentBridgeResult.Ready && result.receivers.isNotEmpty())
        if (result is ChatAttachmentBridgeResult.Ready) assertTrue(result.providerAttachments.isEmpty())
    }

    @Test fun `text file stays local before the selected model receives the request`() {
        val result = UniversalChatAttachmentBridge().resolve(
            ConversationId("conversation-c"),
            model(ProviderId.ZHIPU, "glm-5.3", vision = false, video = false),
            "整理",
            listOf(ChatAttachment(ChatAttachmentKind.FILE, "text/markdown", "notes.md", "# 标题\n\n完整内容".toByteArray())),
            true,
        ) as ChatAttachmentBridgeResult.Ready

        assertTrue(result.contextText.contains("完整 UTF-8 文本"))
        assertTrue(result.contextText.contains("完整内容"))
        assertTrue(result.providerAttachments.isEmpty())
        assertTrue(result.receivers.isEmpty())
    }

    @Test fun `native vision model receives the original image directly`() {
        val image = ChatAttachment(ChatAttachmentKind.IMAGE, "image/png", "chart.png", byteArrayOf(1, 2, 3))
        val result = UniversalChatAttachmentBridge().resolve(
            ConversationId("conversation-native"),
            model(ProviderId.QWEN, "qwen3.7-plus", vision = true, video = false),
            "分析图片",
            listOf(image),
            false,
        ) as ChatAttachmentBridgeResult.Ready

        assertTrue(result.contextText.isEmpty())
        assertEquals(listOf(image), result.providerAttachments)
        assertTrue(result.receivers.isEmpty())
    }

    @Test fun `ordinary chat bridge source contains no alternate provider path`() {
        val source = java.io.File("src/main/java/com/nanzhufeng/ai/ai/UniversalChatAttachmentBridge.kt").readText()
        assertFalse(source.contains("describeWithQwen"))
        assertFalse(source.contains("transcribeWithGlmOcr"))
        assertFalse(source.contains("chat-attachment-bridge:"))
    }

    @Test fun `all supported document MIME types reach attachment fact search through production bridge`() {
        val claim = "(1) Python 3.14.0 release 2025."
        val types=org.json.JSONObject(java.io.File("../protocol/fixtures/attachment-document-types-v1.json").readText()).getJSONArray("mimeTypes")
        assertEquals(TEXT_ATTACHMENT_MIME_TYPES,(0 until types.length()).map {types.getString(it)}.toSet())
        val plain = TEXT_ATTACHMENT_MIME_TYPES - OFFICE_OPEN_XML_MIME_TYPES
        val documents = plain.map { mime -> ChatAttachment(ChatAttachmentKind.FILE, mime, "private-2026.txt", claim.toByteArray()) } + listOf(
            ChatAttachment(ChatAttachmentKind.FILE, DOCX_MIME_TYPE, "private.docx", office("word/document.xml", "<w:document xmlns:w=\"urn:w\"><w:body><w:p><w:r><w:t>$claim</w:t></w:r></w:p></w:body></w:document>")),
            ChatAttachment(ChatAttachmentKind.FILE, XLSX_MIME_TYPE, "private.xlsx", office("xl/worksheets/sheet1.xml", "<worksheet><sheetData><row><c t=\"inlineStr\"><is><t>$claim</t></is></c></row></sheetData></worksheet>")),
            ChatAttachment(ChatAttachmentKind.FILE, PPTX_MIME_TYPE, "private.pptx", office("ppt/slides/slide1.xml", "<p:sld xmlns:p=\"urn:p\" xmlns:a=\"urn:a\"><a:p><a:r><a:t>$claim</a:t></a:r></a:p></p:sld>")),
            ChatAttachment(ChatAttachmentKind.PDF, "application/pdf", "private.pdf", pdf(claim)),
        )
        for (attachment in documents) {
            val result = UniversalChatAttachmentBridge().resolve(ConversationId("matrix"), model(ProviderId.QWEN,"qwen3.7-plus",true,video=false),"核验",listOf(attachment),true)
            assertTrue(attachment.mimeType, result is ChatAttachmentBridgeResult.Ready)
            val ready = result as ChatAttachmentBridgeResult.Ready
            assertTrue(ready.providerAttachments.isEmpty())
            val plan=AttachmentWebSearchPlan.build(ready.contextText)
            assertTrue(attachment.mimeType, plan.items.any {it.query.contains("Python 3.14.0")})
            assertFalse(attachment.mimeType,plan.queryText().contains("private"))
        }
    }

    @Test fun `every supported text and Office type retains twenty facts across mixed attachments`() {
        val claim=(1..20).joinToString("\n") {"($it) Python事项${it}在2025年发布。"}
        val projections=mutableListOf<String>()
        for(mime in TEXT_ATTACHMENT_MIME_TYPES) {
            val bytes=when(mime) {
                DOCX_MIME_TYPE->office("word/document.xml","<w:document xmlns:w=\"urn:w\"><w:p><w:r><w:t>$claim</w:t></w:r></w:p></w:document>")
                XLSX_MIME_TYPE->office("xl/worksheets/sheet1.xml","<worksheet><row><c t=\"inlineStr\"><is><t>$claim</t></is></c></row></worksheet>")
                PPTX_MIME_TYPE->office("ppt/slides/slide1.xml","<p:sld xmlns:p=\"urn:p\" xmlns:a=\"urn:a\"><a:p><a:r><a:t>$claim</a:t></a:r></a:p></p:sld>")
                else->claim.toByteArray()
            }
            val ready=UniversalChatAttachmentBridge().resolve(ConversationId("twenty"),model(ProviderId.QWEN,"qwen3.7-plus",true,video=false),"核验",listOf(ChatAttachment(ChatAttachmentKind.FILE,mime,"private",bytes)),true) as ChatAttachmentBridgeResult.Ready
            val plan=AttachmentWebSearchPlan.build(ready.contextText);assertEquals(mime,20,plan.items.size);assertFalse(plan.limited);assertTrue(plan.queryText().contains("Python事项20"));projections.add(ready.contextText)
        }
        val mixed=AttachmentWebSearchPlan.build(projections.joinToString("\n### 附件\n"));assertEquals(20*projections.size,mixed.items.size);assertFalse(mixed.limited)
    }

    @Test fun `mixed production projections preserve every file past the former sample limit`() {
        val first=(1..14).joinToString("\n") {"($it) Python测试第${it}版2025年发布。"}
        val attachments=listOf(
            ChatAttachment(ChatAttachmentKind.FILE,"text/markdown","first.md",first.toByteArray()),
            ChatAttachment(ChatAttachmentKind.FILE,"text/csv","second.csv","(1) Rust 1.90.0在2025年发布。".toByteArray()),
            ChatAttachment(ChatAttachmentKind.FILE,DOCX_MIME_TYPE,"third.docx",office("word/document.xml","<w:document xmlns:w=\"urn:w\"><w:p><w:r><w:t>(1) Node 22在2024年发布。</w:t></w:r></w:p></w:document>")),
        )
        val ready=UniversalChatAttachmentBridge().resolve(ConversationId("mixed"),model(ProviderId.QWEN,"qwen3.7-plus",true,video=false),"核验",attachments,true) as ChatAttachmentBridgeResult.Ready
        val plan=AttachmentWebSearchPlan.build(ready.contextText)
        assertEquals(16,plan.items.size)
        assertFalse(plan.limited)
        assertTrue(plan.items[1].query.startsWith("Rust"))
        assertTrue(plan.items[2].query.startsWith("Node"))
    }

    @Test fun `Office send uses complete text instead of preview truncation`() {
        val body="x".repeat(128*1024)+"\n(1) Rust 1.90.0在2025年发布。"
        val attachment=ChatAttachment(ChatAttachmentKind.FILE,DOCX_MIME_TYPE,"large.docx",office("word/document.xml","<w:document xmlns:w=\"urn:w\"><w:p><w:r><w:t>$body</w:t></w:r></w:p></w:document>"))
        val ready=UniversalChatAttachmentBridge().resolve(ConversationId("large"),model(ProviderId.QWEN,"qwen3.7-plus",true,video=false),"核验",listOf(attachment),true) as ChatAttachmentBridgeResult.Ready
        assertTrue(ready.contextText.contains("Rust 1.90.0"))
        assertTrue(requireNotNull(extractOfficeOpenXmlText(attachment.open().use {it.readBytes()},DOCX_MIME_TYPE)).truncated)
    }

    @Test fun `ZIP reads mixed document bodies and cannot hide unsupported or unsafe members`() {
        fun archive(vararg entries: Pair<String,String>): ByteArray=ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip -> for ((name,text) in entries) {zip.putNextEntry(ZipEntry(name));zip.write(text.toByteArray());zip.closeEntry()} }
            bytes.toByteArray()
        }
        fun resolve(bytes: ByteArray)=UniversalChatAttachmentBridge().resolve(ConversationId("zip"),model(ProviderId.QWEN,"qwen3.7-plus",true,video=false),"核验",listOf(ChatAttachment(ChatAttachmentKind.FILE,"application/zip","reports.zip",bytes)),true)
        val ready=resolve(archive("one.md" to "(1) Python 3.14.0在2025年发布。\n(2) Python 3.13.0在2024年发布。","two.csv" to "(1) Rust 1.90.0在2025年发布。")) as ChatAttachmentBridgeResult.Ready
        val plan=AttachmentWebSearchPlan.build(ready.contextText)
        assertTrue(plan.items[1].query.startsWith("Rust"))
        assertFalse(plan.queryText().contains("one.md"))
        assertTrue(resolve(archive("one.md" to "(1) Python 3.14.0","two.exe" to "opaque")) is ChatAttachmentBridgeResult.Failed)
        assertTrue(resolve(archive("../one.md" to "(1) Python 3.14.0")) is ChatAttachmentBridgeResult.Failed)
        assertTrue(resolve(archive("fake/" to "not a directory","one.md" to "(1) Python 3.14.0")) is ChatAttachmentBridgeResult.Failed)
        assertTrue(resolve(archive("binary.txt" to "\u0000opaque")) is ChatAttachmentBridgeResult.Failed)
    }

    @Test fun `native OpenRouter search retains media while text-only routes stage explicit projection`() {
        val target=model(ProviderId.OPENROUTER,"media-capable-fixture",true,video=true).let { it.copy(capabilities=it.capabilities.copy(supportsPdf=true,supportsAudio=true)) }
        val options=AutomaticWebSearchPolicy.requestOptions(ProviderId.OPENROUTER,ChatRequestOptions.Standard,true,"分析附件并核验",emptyList(),target.modelId)
        val media=listOf(
            ChatAttachment(ChatAttachmentKind.IMAGE,"image/png","image.png",byteArrayOf(1,2,3)),
            ChatAttachment(ChatAttachmentKind.PDF,"application/pdf","file.pdf",pdf("Public complete text")),
            ChatAttachment(ChatAttachmentKind.AUDIO,"audio/mpeg","audio.mp3",byteArrayOf(7,8,9)),
            ChatAttachment(ChatAttachmentKind.VIDEO,"video/mp4","video.mp4",byteArrayOf(10,11,12)),
        )
        for ((index,attachment) in media.withIndex()) {
            val ready=UniversalChatAttachmentBridge().resolve(ConversationId("media-search"),target,"分析",listOf(attachment),options.requiresAttachmentTextProjection) as ChatAttachmentBridgeResult.Ready
            assertEquals(listOf(attachment),ready.providerAttachments)
            val prepared=OpenRouterChatAdapter().prepare(target,listOf("user" to "核验"),ready.providerAttachments,true,options) as ChatAdapterPrepareResult.Ready
            val bytes=ByteArrayOutputStream().also {prepared.body.writeTo(it)}.toByteArray()
            val body=org.json.JSONObject(bytes.toString(Charsets.UTF_8))
            assertEquals("openrouter:web_search",body.getJSONArray("tools").getJSONObject(0).getString("type"))
            assertTrue(bytes.toString(Charsets.UTF_8).contains(listOf("image_url","file_data","input_audio","video_url")[index]))
            val textOnly=ChatRequestOptions(OfficialWebSearchRoute.RELAYCORE_GPT_RESPONSES)
            val staged=UniversalChatAttachmentBridge().resolve(ConversationId("text-search"),target,"分析",listOf(attachment),textOnly.requiresAttachmentTextProjection) as ChatAttachmentBridgeResult.Ready
            assertTrue(staged.providerAttachments.isEmpty())
            assertTrue(staged.readingAttachments.isNotEmpty() || staged.contextText.isNotBlank())
        }
    }

    private fun office(path: String, xml: String): ByteArray = ByteArrayOutputStream().use { bytes ->
        ZipOutputStream(bytes).use { zip -> zip.putNextEntry(ZipEntry(path));zip.write(xml.toByteArray());zip.closeEntry() }
        bytes.toByteArray()
    }
    private fun pdf(text: String): ByteArray = ByteArrayOutputStream().use { bytes ->
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(org.robolectric.RuntimeEnvironment.getApplication())
        PDDocument().use { document ->
            val page=PDPage();document.addPage(page)
            PDPageContentStream(document,page).use { content -> content.beginText();content.setFont(PDType1Font.HELVETICA,12f);content.newLineAtOffset(20f,700f);content.showText(text);content.endText() }
            document.save(bytes)
        }
        bytes.toByteArray()
    }

    private fun model(provider: ProviderId, id: String, vision: Boolean, pdf: Boolean = true, video: Boolean) = ResolvedModel(
        providerId = provider,
        modelId = id,
        displayName = id,
        capabilities = ModelCapabilities(true, vision, pdf, supportsVideo = video),
        contextWindowTokens = 1_000_000,
        health = ModelHealth.AVAILABLE,
        metadataUpdatedAt = now,
        healthCheckedAt = now,
        maxOutputTokens = 8_192,
    )
}
