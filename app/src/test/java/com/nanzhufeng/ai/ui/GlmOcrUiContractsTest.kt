package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlmOcrUiContractsTest {
    @Test fun `drawer places document markdown directly after scheduled tasks`() {
        val source = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val scheduled = source.indexOf("drawer-scheduled")
        val ocr = source.indexOf("drawer-document-markdown")
        val pinned = source.indexOf("drawer-pinned-header")
        assertTrue(scheduled >= 0 && ocr > scheduled && pinned > ocr)
        assertTrue(source.substring(ocr, pinned).contains("GLM_OCR_WORKSPACE_TITLE"))
        val routes = File("src/main/java/com/nanzhufeng/ai/ui/P5AAdaptiveUi.kt").readText()
        assertTrue(routes.contains("GLM_OCR_WORKSPACE_TITLE = \"南枫转写\""))
        assertTrue(routes.contains("OCR(\"document-markdown\", GLM_OCR_WORKSPACE_TITLE)"))
    }

    @Test fun `workspace is a complete result list with a bottom floating picker`() {
        val source = File("src/main/java/com/nanzhufeng/ai/ui/GlmOcrWorkspace.kt").readText()
        assertTrue(source.contains("LazyColumn("))
        assertTrue(source.contains("items(state.tasks, key = { it.id.value })"))
        assertTrue(source.contains("ExtendedFloatingActionButton("))
        assertTrue(source.contains("Modifier.align(Alignment.BottomCenter)"))
        assertTrue(source.contains("modifier = Modifier.fillMaxSize().p5aDismissOnInwardEdgeSwipe(returnToPreviousLevel)"))
        assertTrue(source.contains("color = ForegroundSurface"))
        assertTrue(source.contains("Text(if (state.importing) \"正在导入…\" else \"选择图片或 PDF\""))
        assertFalse(source.contains("Text(\"Markdown 结果\""))
        assertFalse(source.contains("每条转写都保留原始来源和完整文字。"))
        assertTrue(source.contains("combinedClickable(onClick = onClick, onLongClick = onLongPress)"))
        assertTrue(source.contains("在新对话中继续"))
        assertTrue(source.contains("arrayOf(\"image/jpeg\", \"image/png\", \"application/pdf\")"))
    }

    @Test fun `start transcription is the explicit action without a duplicate checkbox gate`() {
        val source = File("src/main/java/com/nanzhufeng/ai/ui/GlmOcrWorkspace.kt").readText()
        assertTrue(source.contains("private fun OcrStartCard(onStart: () -> Unit)"))
        assertTrue(source.contains("Button(\n                onClick = onStart"))
        assertFalse(source.contains("privacyConfirmed"))
        assertFalse(source.contains("Checkbox("))
        assertFalse(source.contains("我确认将这个文件发送给智谱 BigModel"))
    }

    @Test fun `task detail exposes source and generated markdown through unified file viewers`() {
        val source = File("src/main/java/com/nanzhufeng/ai/ui/GlmOcrWorkspace.kt").readText()
        assertTrue(source.contains("Text(\"原始文件\""))
        assertTrue(source.contains("Text(\"Markdown 文件\""))
        assertTrue(source.contains("onOpenSource(task.id)"))
        assertTrue(source.contains("onOpenMarkdown(task.id)"))
        assertTrue(source.contains("SharedTextPreviewDialog("))
        assertTrue(source.contains("AttachmentTransferEffect("))
        assertTrue(source.contains("Text(\"点击查看文本内容\""))
        assertFalse(source.contains("items(markdownChunks)"))
        assertFalse(source.contains("detailMarkdown"))
    }

    @Test fun `ocr source uses the same in app image and pdf viewers as conversation and search`() {
        val workspace = File("src/main/java/com/nanzhufeng/ai/ui/GlmOcrWorkspace.kt").readText()
        val owner = File("src/main/java/com/nanzhufeng/ai/domain/GlmOcr.kt").readText()
        val appContainer = File("src/main/java/com/nanzhufeng/ai/app/AppContainer.kt").readText()
        assertTrue(workspace.contains("ConversationAttachmentPreviewProjection"))
        assertTrue(workspace.contains("attachmentPreview.original(reference)"))
        assertTrue(workspace.contains("attachmentPreview.pdfPage(reference, pageNumber)"))
        assertTrue(workspace.contains("withContext(Dispatchers.IO) { owner.sourceReference(id) }"))
        assertTrue(workspace.contains("generation != sourceOpenGeneration || state.selectedTaskId != id"))
        assertFalse(workspace.contains("val reference = owner.sourceReference(id)"))
        assertTrue(workspace.contains("SharedImagePreviewDialog("))
        assertTrue(workspace.contains("SharedPdfPreviewDialog("))
        assertTrue(workspace.contains("pdfPreviewPosition.savePage"))
        assertTrue(owner.contains("fun sourceReference(id: GlmOcrTaskId): ConversationAttachmentReference?"))
        assertFalse(workspace.contains("Intent.ACTION_VIEW"))
        assertFalse(appContainer.contains("glmOcrSourceOpener"))
        assertFalse(File("src/main/java/com/nanzhufeng/ai/data/AndroidGlmOcrSourceOpener.kt").exists())
    }

    @Test fun `ocr PDF opens immediately and matches shared bounded paging performance`() {
        val workspace = File("src/main/java/com/nanzhufeng/ai/ui/GlmOcrWorkspace.kt").readText()
        val sharedViewer = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        for (token in listOf(
            "pdfPreview = ConversationAttachmentPdfPreview(",
            "pdfPreviewLoading = true",
            "pdfPreviewRequestGeneration",
            "OCR_PDF_PAGE_CACHE_MAX_ENTRIES = 4",
            "OCR_PDF_PAGE_CACHE_MAX_BYTES = 24L * 1024L * 1024L",
            "cachedPdfPage",
            "cachePdfPage",
            "prefetchPdfNeighbours",
            "OCR_PDF_NEIGHBOUR_PREFETCH_DELAY_MS",
        )) assertTrue(token, workspace.contains(token))
        val prefetch = workspace.substring(workspace.indexOf("private fun prefetchPdfNeighbours"), workspace.indexOf("fun closePdfPreview"))
        assertFalse(prefetch.contains("state = state.copy"))
        assertTrue(sharedViewer.contains("page == null && loading -> Unit"))
        assertFalse(workspace.contains("正在阅读本地 PDF；不会外发。"))
    }

    @Test fun `ocr is settings visible but composer catalog has no ocr choice`() {
        val settings = File("src/main/java/com/nanzhufeng/ai/ui/ModelSettingsUi.kt").readText()
        val routing = File("src/main/java/com/nanzhufeng/ai/domain/ChatModelRouting.kt").readText()
        assertTrue(settings.contains("Text(\"GLM-OCR\""))
        assertTrue(settings.contains("仅在左侧栏“\$GLM_OCR_WORKSPACE_TITLE”中调用"))
        val presetSurface = settings.substringAfter("private fun AiPresetSelectionSurface(").substringBefore("private fun AiSelectionSurface(")
        assertTrue(presetSurface.contains("allowedProvider == ProviderId.ZHIPU"))
        assertTrue(presetSurface.contains("Text(\"GLM-OCR\""))
        val configurationPage = settings.substringAfter("internal fun ModelSettingsConfigurationPage(").substringBefore("private fun AiPresetSelectionSurface(")
        assertFalse(configurationPage.contains("Text(\"GLM-OCR\""))
        assertFalse(routing.contains("ModelPresetId.GLM_OCR"))
    }

    @Test fun `ocr owns a centered fixed header outside the settings scroll container`() {
        val workspace = File("src/main/java/com/nanzhufeng/ai/ui/GlmOcrWorkspace.kt").readText()
        assertTrue(workspace.contains("private fun OcrTopBar"))
        assertTrue(workspace.contains("contentAlignment = Alignment.Center"))
        assertTrue(workspace.contains("GLM_OCR_WORKSPACE_TITLE"))
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val routeOwner = app.substringAfter("if (route == P5ARoute.OCR)").substringBefore("Column(")
        assertTrue(routeOwner.contains("GlmOcrWorkspacePage("))
        assertTrue(routeOwner.contains("onBack = onReturnToConversationDrawer"))
        assertTrue(routeOwner.contains("return"))
        val canvasPolicy = File("src/main/java/com/nanzhufeng/ai/ui/RootCanvasPolicy.kt").readText()
        assertTrue(canvasPolicy.contains("P5ARoute.OCR -> RootCanvasKind.DOCUMENT"))
        assertTrue(app.contains("RootCanvasKind.DOCUMENT -> ForegroundSurface"))
        assertTrue(app.contains("isAppearanceLightStatusBars = !darkAppearance"))
    }

    @Test fun `every ocr level shares system top and inward edge back ownership`() {
        val source = File("src/main/java/com/nanzhufeng/ai/ui/GlmOcrWorkspace.kt").readText()
        assertTrue(source.contains("val returnToPreviousLevel = if (detailVisible) viewModel::closeDetail else onBack"))
        assertTrue(source.contains("BackHandler(onBack = returnToPreviousLevel)"))
        assertTrue(source.contains("p5aDismissOnInwardEdgeSwipe(returnToPreviousLevel)"))
        assertTrue(source.contains("OcrTopBar(onBack = returnToPreviousLevel)"))
    }

    @Test fun `ocr invocation metadata and both files join existing settings and search surfaces`() {
        val ledger = File("src/main/java/com/nanzhufeng/ai/ui/InvocationLedgerUi.kt").readText()
        assertTrue(ledger.contains("glmOcrTasksByInvocationTaskId"))
        assertTrue(ledger.contains("if (glmOcrTask == null) record.displayModelName() else \"南枫转写\""))
        assertTrue(ledger.contains("文件："))
        assertTrue(ledger.contains("服务商请求 ID："))

        val searchOwner = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()
        val searchUi = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val routeOwner = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        assertTrue(searchOwner.contains("glmOcr.searchDocuments(query, category)"))
        assertTrue(searchUi.contains("GlmOcrSearchAttachmentRow("))
        assertTrue(searchUi.contains("private fun GlmOcrSearchAttachmentCard("))
        assertTrue(searchUi.contains("onOpenSearchAttachment(hit.attachment)"))
        assertTrue(searchUi.contains("SearchGlmOcrActionMenuTarget"))
        assertTrue(searchUi.contains("onDeleteGlmOcrSearchHit(hit)"))
        assertTrue(routeOwner.contains("glmOcrWorkspaceViewModel.openDetail(hit.taskId)"))
        assertTrue(routeOwner.contains("onRouteSelected(P5ARoute.OCR)"))
    }
}
