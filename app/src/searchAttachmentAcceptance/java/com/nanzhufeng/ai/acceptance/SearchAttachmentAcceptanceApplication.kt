package com.nanzhufeng.ai.acceptance

import android.app.Application
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.util.Base64
import android.util.Log
import androidx.work.Configuration
import com.nanzhufeng.ai.app.AppContainer
import com.nanzhufeng.ai.domain.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.FutureTask
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Disposable, separate-package visual acceptance data.
 *
 * The fixture reaches the same private-file store, attachment catalogue and conversation
 * repository as production. It never reads a user file, credential, network response or another
 * installed package, and it refuses to add a second fixture to a non-empty acceptance database.
 */
class SearchAttachmentAcceptanceApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration by lazy {
        Configuration.Builder()
            .setMinimumLoggingLevel(Log.WARN)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        val clock = Clock.fixed(Instant.parse("2026-08-29T02:00:00Z"), ZoneOffset.UTC)
        val seed = FutureTask { seed(clock) }
        Thread(seed, "search-attachment-acceptance-seed").start()
        seed.get()
    }

    private fun seed(clock: Clock) {
        val container = AppContainer(this, clock)
        if (container.conversationRepository.listActive().isNotEmpty()) return

        val sharedImage = import(
            container,
            "image/png",
            "共享图片-最后引用才清理.png",
            validPng(),
        )
        val video = import(container, "video/mp4", "C08-本地视频.mp4", validMp4())
        val audio = import(container, "audio/wav", "C08-本地音频.wav", validWav())
        val pdf = import(container, "application/pdf", "C08-本地报告.pdf", validPdf())
        val text = import(container, "text/plain", "C08-本地说明.txt", plainText(543))
        val markdown = import(container, "text/markdown", "C08-本机说明.md", "# C08 本机说明\n\n应用内安全预览。".encodeToByteArray())
        val json = import(container, "application/json", "C08-结构数据.json", "{\"fixture\":\"C08\",\"localOnly\":true}".encodeToByteArray())
        val zip = import(container, "application/zip", "C08-安全压缩包.zip", safeZip())
        val docx = import(
            container,
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "C08-Office文档.docx",
            validDocx(),
        )

        val tree = ConversationTreeService(clock)
        var snapshot = tree.create("搜索附件验收")
        snapshot = tree.append(
            snapshot,
            AppendMessageRequest(
                MessageRole.USER,
                listOf(ContentBlock.Text("C08 本机文字命中验收：搜索、排序与预览共享同一安全索引。")),
            ),
        )
        listOf(
            MessageRole.USER to sharedImage,
            MessageRole.ASSISTANT to pdf,
            MessageRole.USER to audio,
            MessageRole.ASSISTANT to video,
            MessageRole.USER to sharedImage,
            MessageRole.ASSISTANT to text,
            MessageRole.USER to markdown,
            MessageRole.ASSISTANT to json,
            MessageRole.USER to zip,
            MessageRole.ASSISTANT to docx,
        ).forEach { (role, attachment) ->
            snapshot = tree.append(
                snapshot,
                AppendMessageRequest(role, listOf(ContentBlock.Attachment(attachment))),
            )
        }
        snapshot = tree.saveDraft(snapshot, "C07 重启后仍保留的本机草稿", listOf(sharedImage))
        container.conversationRepository.save(snapshot)
        seedScheduledMonitors(container, snapshot.conversation.id, clock.instant())
        seedGlmOcrStates(container, sharedImage, markdown, clock.instant())
        seedRedactedCallMetadata(container, snapshot, clock.instant())
    }

    private fun import(
        container: AppContainer,
        mimeType: String,
        displayName: String,
        bytes: ByteArray,
    ): ConversationAttachmentReference {
        val imported = container.privateAttachmentStore.import(
            AttachmentImportRequest(ByteArrayInputStream(bytes), mimeType, displayName),
        ) as? AttachmentImportResult.Imported ?: error("验收附件导入失败：$displayName")
        return container.privateAttachmentRepository.save(imported.attachment).toConversationReference()
    }

    private fun validPng(): ByteArray = Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAACAAAAAYCAIAAAAUMWhjAAAACXBIWXMAAAABAAAAAQBPJcTWAAAAJklEQVR4nGN81aLDQEvAQlPTRy0YtWDUglELRi0YtWDUglELqAYAQ2oB+LgQPqkAAAAASUVORK5CYII=",
            Base64.DEFAULT,
        )

    private fun validMp4(): ByteArray = Base64.decode(VALID_MP4_BASE64, Base64.DEFAULT)

    private fun validWav(): ByteArray {
        val samples = ByteArray(1_600)
        return ByteBuffer.allocate(44 + samples.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".encodeToByteArray()); putInt(36 + samples.size); put("WAVE".encodeToByteArray())
            put("fmt ".encodeToByteArray()); putInt(16); putShort(1); putShort(1); putInt(8_000)
            putInt(16_000); putShort(2); putShort(16); put("data".encodeToByteArray()); putInt(samples.size); put(samples)
        }.array()
    }

    private fun validPdf(): ByteArray {
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(360, 480, 1).create())
        page.canvas.drawColor(Color.WHITE)
        page.canvas.drawText("C08 local PDF", 32f, 64f, Paint().apply { color = Color.DKGRAY; textSize = 20f })
        document.finishPage(page)
        return ByteArrayOutputStream().also { document.writeTo(it); document.close() }.toByteArray()
    }

    private fun safeZip(): ByteArray = zipBytes(mapOf("C08/readme.md" to "# C08\nlocal-only archive"))

    private fun validDocx(): ByteArray = zipBytes(
        mapOf(
            "[Content_Types].xml" to """<?xml version="1.0"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>""",
            "_rels/.rels" to """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""",
            "word/document.xml" to """<?xml version="1.0"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>C08 Office 文档应用内预览</w:t></w:r></w:p></w:body></w:document>""",
        ),
    )

    private fun zipBytes(entries: Map<String, String>): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip -> entries.forEach { (name, body) -> zip.putNextEntry(ZipEntry(name)); zip.write(body.encodeToByteArray()); zip.closeEntry() } }
    }.toByteArray()

    private fun plainText(size: Int): ByteArray = ByteArray(size) { 'A'.code.toByte() }

    private fun seedScheduledMonitors(container: AppContainer, conversationId: ConversationId, now: Instant) {
        fun task(id: String, title: String, status: ScheduledMonitorStatus) = ScheduledMonitorTask(
            ScheduledMonitorTaskId(id), title, "只读公开状态，不外发通知。", conversationId,
            ScheduledMonitorCadence.DAILY, ModelPresetId.GPT_5_6_TERRA, status, now.plusSeconds(86_400),
            createdAt = now, updatedAt = now,
        )
        container.scheduledMonitorRepository.create(task("c09-active", "公开项目每日检查", ScheduledMonitorStatus.ACTIVE))
        container.scheduledMonitorRepository.create(task("c09-paused", "已暂停的长期跟踪", ScheduledMonitorStatus.PAUSED))
        val success = task("c09-result", "已保存结果", ScheduledMonitorStatus.ACTIVE)
        container.scheduledMonitorRepository.create(success)
        val successRun = ScheduledMonitorRun(ScheduledMonitorRunId("c09-result-run"), success.id, now, now, status = ScheduledMonitorRunStatus.RUNNING)
        check(container.scheduledMonitorRepository.startRun(successRun))
        check(container.scheduledMonitorRepository.finishRun(
            success.copy(lastRunAt = now, latestResult = "公开状态已读取，结果保存在本机样本中。", lastProviderId = ProviderId.OPENROUTER, lastModelId = C12_MODEL_ID, lastInputTokens = 180, lastOutputTokens = 96, updatedAt = now),
            successRun.copy(completedAt = now, status = ScheduledMonitorRunStatus.SUCCEEDED, result = "公开状态已读取，结果保存在本机样本中。", providerId = ProviderId.OPENROUTER, modelId = C12_MODEL_ID, inputTokens = 180, outputTokens = 96),
        ))
        val failed = task("c09-failed", "配置待完成", ScheduledMonitorStatus.ACTIVE)
        container.scheduledMonitorRepository.create(failed)
        val failedRun = ScheduledMonitorRun(ScheduledMonitorRunId("c09-failed-run"), failed.id, now, now, status = ScheduledMonitorRunStatus.RUNNING)
        check(container.scheduledMonitorRepository.startRun(failedRun))
        check(container.scheduledMonitorRepository.finishRun(
            failed.copy(lastRunAt = now, lastSafeErrorCode = "PROVIDER_NOT_CONFIGURED", updatedAt = now),
            failedRun.copy(completedAt = now, status = ScheduledMonitorRunStatus.FAILED, safeErrorCode = "PROVIDER_NOT_CONFIGURED"),
        ))
    }

    private fun seedGlmOcrStates(container: AppContainer, source: ConversationAttachmentReference, result: ConversationAttachmentReference, now: Instant) {
        fun task(id: String, status: GlmOcrTaskStatus, error: String? = null, completed: Boolean = false) = GlmOcrTask(
            GlmOcrTaskId(id), source.id, if (completed) result.id else null, source.displayName ?: "C08-本地图片.png",
            source.mimeType, source.byteCount ?: error("missing bytes"), source.sha256 ?: error("missing hash"), status,
            requestId = if (completed) "c10-offline-fixture-request" else null, pageCount = if (completed) 1 else null,
            inputTokens = if (completed) 90 else null, outputTokens = if (completed) 410 else null,
            costCnyMicros = if (completed) 3_250 else null, safeErrorCode = error, createdAt = now, updatedAt = now,
        )
        container.glmOcrTasks.save(task("c10-processing", GlmOcrTaskStatus.PROCESSING))
        container.glmOcrTasks.save(task("c10-failed", GlmOcrTaskStatus.FAILED, "ZHIPU_NOT_ENABLED"))
        container.glmOcrTasks.save(task("c10-completed", GlmOcrTaskStatus.COMPLETED, completed = true))
    }

    private fun seedRedactedCallMetadata(container: AppContainer, snapshot: ConversationSnapshot, now: Instant) {
        val assistantId = snapshot.conversation.currentLeafMessageId ?: MessageNodeId("c12-assistant")
        container.assistantResponseModelAttributions.record(AssistantResponseModelAttribution(
            assistantId, NormalChatSendAttemptId("c12-offline-attempt"), ProviderId.OPENROUTER, ProviderId.OPENROUTER,
            C12_MODEL_ID, "GPT-5.6 Terra", now, webSearchUsed = false,
            usage = ProviderUsage(1_240, 680, 1_920, 220),
            cost = ProviderCost("c12-offline-fixture-v1", "USD", 14_250),
            costSource = ConversationCostSource.PROVIDER_RESPONSE,
        ))
        container.contextSelectionAudits.append(ContextSelectionAuditRecord(
            createdAt = now, conversationId = snapshot.conversation.id.value,
            attemptId = NormalChatSendAttemptId("c12-offline-attempt"), assistantMessageId = assistantId,
            providerId = ProviderId.OPENROUTER, modelId = C12_MODEL_ID, tokenizerId = "openrouter-catalog-v1",
            budget = ContextBudget(200_000, 8_192, 191_808, 76_723, 113_405, 1_680, "openrouter-catalog-v1"),
            selectedSources = listOf(
                ContextSelectionSource("知识库", "c12-contract", "C12 Android→Desktop 同步合同", 800),
                ContextSelectionSource("当前对话路径", "c12-current", "当前对话上下文", 660),
                ContextSelectionSource("记忆", "c12-memory", "本机用户偏好摘要", 220),
            ), participationAuditAvailable = true, webSearchUsed = false,
        ))
        container.providerDiagnostics.append(ProviderDiagnosticRecord(
            id = "c12-offline-diagnostic", createdAt = now, conversationId = snapshot.conversation.id.value,
            providerId = ProviderId.OPENROUTER, endpointHost = "openrouter.ai", apiModelId = C12_MODEL_ID,
            httpStatus = null, errorClass = ProviderDiagnosticErrorClass.NETWORK,
            redactedBody = "网络连接失败，未自动重发。", requestShape = "responses:text-only", latencyMs = 1_450,
        ))
        container.directChatCallAudit.append(DirectChatCallAuditRecord(
            ProviderId.OPENROUTER, "https://openrouter.ai/api/v1", C12_MODEL_ID, HISTORY_CURATION_AUDIT_ALIAS,
            "medium", now, 180, 96, "SUCCEEDED",
        ))
        container.invocationRepository.save(InvocationRecord(
            InvocationId("c12-glm-ocr-invocation"), AiTaskId("glm-ocr:c12-offline"), ProviderId.ZHIPU,
            "glm-ocr", 1, now, InvocationStatus.SUCCEEDED,
            usage = ProviderUsage(90, 410, 500), cost = ProviderCost("c12-offline-fixture-v1", "CNY", 3_250),
        ))
    }

    private companion object {
        const val C12_MODEL_ID = "openai/gpt-5.6-terra"
        const val VALID_MP4_BASE64 = "AAAAIGZ0eXBpc29tAAACAGlzb21pc28yYXZjMW1wNDEAAAN1bW9vdgAAAGxtdmhkAAAAAAAAAAAAAAAAAAAD6AAAAMgAAQAAAQAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAgAAAp90cmFrAAAAXHRraGQAAAADAAAAAAAAAAAAAAABAAAAAAAAAMgAAAAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAABAAAAAABAAAAAQAAAAAAAkZWR0cwAAABxlbHN0AAAAAAAAAAEAAADIAAAEAAABAAAAAAIXbWRpYQAAACBtZGhkAAAAAAAAAAAAAAAAAAAyAAAACgBVxAAAAAAALWhkbHIAAAAAAAAAAHZpZGUAAAAAAAAAAAAAAABWaWRlb0hhbmRsZXIAAAABwm1pbmYAAAAUdm1oZAAAAAEAAAAAAAAAAAAAACRkaW5mAAAAHGRyZWYAAAAAAAAAAQAAAAx1cmwgAAAAAQAAAYJzdGJsAAAAvnN0c2QAAAAAAAAAAQAAAK5hdmMxAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAAAABAAEABIAAAASAAAAAAAAAABFUxhdmM2Mi4yOC4xMDIgbGlieDI2NAAAAAAAAAAAAAAAGP//AAAANGF2Y0MBZAAK/+EAF2dkAAqs2V7ARAAAAwAEAAADAMg8SJZYAQAGaOvjyyLA/fj4AAAAABBwYXNwAAAAAQAAAAEAAAAUYnRydAAAAAAAAHcQAAAAAAAAABhzdHRzAAAAAAAAAAEAAAAFAAACAAAAABRzdHNzAAAAAAAAAAEAAAABAAAAOGN0dHMAAAAAAAAABQAAAAEAAAQAAAAAAQAACgAAAAABAAAEAAAAAAEAAAAAAAAAAQAAAgAAAAAcc3RzYwAAAAAAAAABAAAAAQAAAAUAAAABAAAAKHN0c3oAAAAAAAAAAAAAAAUAAALKAAAADAAAAAwAAAAMAAAADAAAABRzdGNvAAAAAAAAAAEAAAOlAAAAYnVkdGEAAABabWV0YQAAAAAAAAAhaGRscgAAAAAAAAAAbWRpcmFwcGwAAAAAAAAAAAAAAAAtaWxzdAAAACWpdG9vAAAAHWRhdGEAAAABAAAAAExhdmY2Mi4xMi4xMDIAAAAIZnJlZQAAAwJtZGF0AAACrgYF//+q3EXpvebZSLeWLNgg2SPu73gyNjQgLSBjb3JlIDE2NSByMzIyMiBiMzU2MDVhIC0gSC4yNjQvTVBFRy00IEFWQyBjb2RlYyAtIENvcHlsZWZ0IDIwMDMtMjAyNSAtIGh0dHA6Ly93d3cudmlkZW9sYW4ub3JnL3gyNjQuaHRtbCAtIG9wdGlvbnM6IGNhYmFjPTEgcmVmPTMgZGVibG9jaz0xOjA6MCBhbmFseXNlPTB4MzoweDExMyBtZT1oZXggc3VibWU9NyBwc3k9MSBwc3lfcmQ9MS4wMDowLjAwIG1peGVkX3JlZj0xIG1lX3JhbmdlPTE2IGNocm9tYV9tZT0xIHRyZWxsaXM9MSA4eDhkY3Q9MSBjcW09MCBkZWFkem9uZT0yMSwxMSBmYXN0X3Bza2lwPTEgY2hyb21hX3FwX29mZnNldD0tMiB0aHJlYWRzPTEg bG9va2FoZWFkX3RocmVhZHM9MSBzbGljZWRfdGhyZWFkcz0wIG5yPTAgZGVjaW1hdGU9MSBpbnRlcmxhY2VkPTAgYmx1cmF5X2NvbXBhdD0wIGNvbnN0cmFpbmVkX2ludHJhPTAgYmZyYW1lcz0zIGJfcHlyYW1pZD0yIGJfYWRhcHQ9MSBiX2JpYXM9MCBkaXJlY3Q9MSB3ZWlnaHRiPTEgb3Blbl9nb3A9MCB3ZWlnaHRwPTIga2V5aW50PTI1MCBrZXlpbnRfbWluPTI1IHNjZW5lY3V0PTQwIGludHJhX3JlZnJlc2g9MCByY19sb29rYWhlYWQ9NDAgcmM9Y3JmIG1idHJlZT0xIGNyZj0yMy4wIHFjb21wPTAuNjAgcXBtaW49MCBxcG1heD02OSBxcHN0ZXA9NCBpcF9yYXRpbz0xLjQwIGFxPTE6MS4wMACAAAAAFGWIhAAz//7fMvgUyg0HQnPMV7UdAAAACEGaJGxCv/7AAAAACEGeQniF/8GBAAAACAGeYXRCv8SAAAAACAGeY2pCv8SB"
    }
}
