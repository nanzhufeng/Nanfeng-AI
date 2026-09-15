package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationSearchSurfaceContractsTest {
    @Test
    fun `search canvas and controls keep two deeper gray steps while results remain white cards`() {
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val source = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val page = source.substring(source.indexOf("private fun ConversationSearchPage"), source.indexOf("private fun SearchAllOrTextResults"))

        assertTrue(app.contains("SearchPageCanvas by mutableStateOf(Color(0xFFEDEEEE))"))
        assertTrue(app.contains("SearchControlSurface by mutableStateOf(Color(0xFFE1E4E2))"))
        assertTrue(page.contains("Surface(color = SearchPageCanvas"))
        assertTrue(page.split("color = SearchControlSurface").size - 1 == 5)
        assertTrue(page.contains("conversationEdgeGrayFade(\n                                topBand = 0.dp,\n                                bottomBand = 112.dp,\n                                edgeColor = SearchPageCanvas,"))
        assertTrue(page.contains("modifier = Modifier.align(Alignment.Center).semantics { heading() }"))
        assertTrue(page.contains("style = MaterialTheme.typography.titleLarge"))
        assertTrue(page.contains("fontWeight = FontWeight.Bold"))
        assertTrue(page.contains("color = if (selected) MaterialTheme.colorScheme.primary else SecondaryText"))
        assertTrue(page.contains("fontWeight = FontWeight.SemiBold"))
        assertTrue(source.contains("onOpenHit(hit) },\n                    color = ForegroundSurface"))
    }

    @Test
    fun `external locate keeps the complete catalogue while search quick locate restores the original viewport`() {
        val source = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val position = File("src/main/java/com/nanzhufeng/ai/ui/ConversationSearchPosition.kt").readText()
        val owner = source.substring(
            source.indexOf("var searchAttachmentActionTarget by remember"),
            source.indexOf("if (searchPageVisible) {"),
        )
        val page = source.substring(
            source.indexOf("private data class ConversationSearchScrollStates"),
            source.indexOf("private fun searchHighlightedText"),
        )

        for (token in listOf(
            "all = rememberLazyListState()",
            "text = rememberLazyListState()",
            "image = rememberLazyGridState()",
            "video = rememberLazyGridState()",
            "audio = rememberLazyGridState()",
            "file = rememberLazyGridState()",
        )) assertTrue("missing hoisted search viewport $token", owner.contains(token))

        assertTrue(owner.contains("var searchReturnAttachmentId by rememberSaveable"))
        assertTrue(owner.contains("if (!isVisible) searchAllAttachmentItemIndex"))
        assertTrue(owner.contains("if (!isVisible) searchGridAttachmentItemIndex"))
        assertTrue(source.contains("onOpenAttachmentHit = { hit ->\n                onOpenSearchAttachment(hit.attachment)"))
        val quickLocate = source.substringAfter("onOpenConversation = {\n                val hit = target.hit").substringBefore("onRequestDelete = {")
        assertTrue(quickLocate.contains("onLocateSearchAttachment(hit)"))
        assertTrue(quickLocate.contains("returnToSearchAfterSearchOpen = true"))
        assertTrue(quickLocate.contains("searchReturnAttachmentId = null"))
        assertFalse(quickLocate.contains("searchReturnAttachmentId = hit.attachment.id.value"))
        assertFalse(quickLocate.contains("searchLocateConversationId = hit.conversationId.value"))
        assertFalse(quickLocate.contains("searchLocateMessageId = hit.messageNodeId.value"))
        val externalLocate = source.substringAfter("LocalAttachmentSearchLocateRequest").substringBefore("onQueryChanged =")
        assertTrue(externalLocate.contains("searchReturnAttachmentId = attachmentId.value"))
        assertTrue(externalLocate.contains("searchLocateConversationId = state.selectedConversationId?.value"))
        assertTrue(externalLocate.contains("searchLocateMessageId = messageNodeId.value"))
        assertTrue(externalLocate.contains("onSearchChanged(\"\")"))
        assertTrue(externalLocate.contains("onSearchCategoryChanged(ConversationSearchCategory.ALL)"))
        assertFalse(externalLocate.contains("conversationAttachmentSearchCategory"))
        assertTrue(source.contains("onQueryChanged = { query ->\n                searchReturnAttachmentId = null\n                searchLocateConversationId = null\n                searchLocateMessageId = null\n                searchLocateAttachmentId = null"))
        assertTrue(source.contains("onSelectCategory = { category ->") && source.contains("searchReturnAttachmentId = null\n                searchLocateConversationId = null\n                searchLocateMessageId = null\n                searchLocateAttachmentId = null\n                onSearchCategoryChanged(category)"))

        assertTrue(position.contains("internal fun searchAllAttachmentItemIndex"))
        assertTrue(position.contains("internal fun searchGridAttachmentItemIndex"))
        assertTrue(page.contains("listState = scrollStates.list(state.searchCategory)"))
        assertTrue(page.contains("gridState = scrollStates.grid(state.searchCategory)"))
        assertTrue(source.contains("LazyColumn(state = listState"))
        assertTrue(source.contains("state = gridState"))
    }

    @Test
    fun `attachment sort columns keep month grouping as default and file types use their own dropdown`() {
        val source = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val position = File("src/main/java/com/nanzhufeng/ai/ui/ConversationSearchPosition.kt").readText()
        val page = source.substring(
            source.indexOf("private fun ConversationSearchPage"),
            source.indexOf("private fun SearchAllOrTextResults"),
        )
        val control = source.substring(
            source.indexOf("private fun SearchAttachmentSortControl("),
            source.indexOf("private fun searchHighlightedText("),
        )

        assertTrue(source.contains("mutableStateOf(ConversationAttachmentSortMode.DEFAULT)"))
        assertTrue(source.contains("mutableStateOf(ConversationAttachmentFileType.ALL)"))
        assertFalse(source.contains("if (state.searchCategory != ConversationSearchCategory.TEXT)"))
        assertTrue(page.contains("SearchAttachmentSortControl("))
        assertTrue(control.contains("SearchAttachmentSortColumn("))
        assertTrue(control.contains("label = \"时间\""))
        assertTrue(control.contains("label = \"大小\""))
        assertTrue(control.contains("\"还原\""))
        assertTrue(control.contains("ConversationAttachmentSortColumn.TIME"))
        assertTrue(control.contains("ConversationAttachmentSortColumn.SIZE"))
        assertTrue(control.contains("Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center)"))
        assertTrue(control.contains("nextAttachmentSortMode(selected, column)"))
        assertTrue(control.contains("modifier = Modifier.width(128.dp).height(36.dp)"))
        assertTrue(control.countOccurrences("RoundedCornerShape(50)") >= 3)
        assertTrue(control.countOccurrences("color = SearchControlSurface") >= 3)
        assertTrue(control.contains("private fun SearchAttachmentFileTypeControl("))
        assertFalse(control.contains("Text(\"类型\""))
        assertTrue(control.contains("modifier = Modifier.width(128.dp).height(36.dp)"))
        assertTrue(page.contains("if (state.searchCategory == ConversationSearchCategory.FILE) {"))
        assertTrue(page.contains("SearchAttachmentFileTypeControl("))
        assertTrue(page.contains("modifier = Modifier.align(Alignment.CenterStart)"))
        assertTrue(page.contains("modifier = Modifier.align(Alignment.CenterEnd)"))
        assertTrue(control.countOccurrences("Modifier.width(72.dp).fillMaxHeight()") >= 2)
        assertTrue(control.contains("modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp)"))
        assertTrue(control.contains("horizontalArrangement = Arrangement.Center"))
        assertTrue(control.contains("containerColor = ForegroundSurface"))
        assertTrue(control.contains("Icons.Rounded.SwapVert"))
        assertTrue(control.contains("ConversationAttachmentFileType.entries.forEach"))
        assertFalse(control.contains("ConversationAttachmentSortMode.entries.forEach"))
        for (label in listOf("默认排序", "时间从新到旧", "时间从旧到新", "大小从大到小", "大小从小到大")) {
            assertTrue("missing sort label $label", position.contains("\"$label\""))
        }
        for (label in listOf("MD", "PDF", "ZIP", "DOCX", "TXT", "JSON", "其他")) {
            assertTrue("missing file type $label", position.contains("\"$label\""))
        }
        for (removed in listOf("XLSX", "PPTX", "CSV", "XML", "YAML", "HTML")) {
            assertFalse("file type must merge into other: $removed", position.contains("\"$removed\""))
        }
        assertTrue(source.contains("if (sortMode == ConversationAttachmentSortMode.DEFAULT)"))
        assertTrue(source.contains("sortedUnifiedSearchAttachmentEntries(state.attachmentSearchResults, state.glmOcrSearchResults, sortMode)"))
        assertTrue(source.contains("sortedUnifiedSearchAttachmentEntries(hits, glmOcrHits, sortMode)"))
        assertTrue(source.contains("unifiedSearchAttachmentMonthGroups(state.attachmentSearchResults, state.glmOcrSearchResults)"))
        assertTrue(source.contains("unifiedSearchAttachmentMonthGroups(hits, glmOcrHits)"))
        assertFalse(source.contains("SearchAttachmentMonthHeading(\"南枫转写\""))
        assertTrue(position.contains("SIZE_DESCENDING -> entries.sortedWith(compareByDescending<UnifiedSearchAttachmentEntry> { it.attachment.byteCount }"))
        assertTrue(source.contains("filterSearchFileType(state.attachmentSearchResults, attachmentFileType)"))
        assertTrue(source.contains("searchScrollStates.grid(state.searchCategory).scrollToItem(0)"))
    }

    @Test fun `opening a search attachment does not create a locate request or move its result`() {
        val source = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val openHandler = source.substringAfter("onOpenAttachmentHit = { hit ->").substringBefore("onEnsureAttachmentPreview")

        assertTrue(openHandler.contains("onOpenSearchAttachment(hit.attachment)"))
        assertFalse(openHandler.contains("searchReturnAttachmentId"))
        assertFalse(openHandler.contains("searchLocateConversationId"))
        assertFalse(openHandler.contains("searchLocateMessageId"))
        assertFalse(openHandler.contains("searchLocateAttachmentId"))
    }

    @Test fun `sort columns toggle independently and file types keep unusual extensions under other`() {
        assertEquals(
            ConversationAttachmentSortMode.TIME_DESCENDING,
            nextAttachmentSortMode(ConversationAttachmentSortMode.DEFAULT, ConversationAttachmentSortColumn.TIME),
        )
        assertEquals(
            ConversationAttachmentSortMode.TIME_ASCENDING,
            nextAttachmentSortMode(ConversationAttachmentSortMode.TIME_DESCENDING, ConversationAttachmentSortColumn.TIME),
        )
        assertEquals(
            ConversationAttachmentSortMode.SIZE_DESCENDING,
            nextAttachmentSortMode(ConversationAttachmentSortMode.TIME_ASCENDING, ConversationAttachmentSortColumn.SIZE),
        )

        fun hit(id: String, name: String, mimeType: String) = com.nanzhufeng.ai.domain.ConversationAttachmentSearchHit(
            conversationId = com.nanzhufeng.ai.domain.ConversationId("conversation-$id"),
            messageNodeId = com.nanzhufeng.ai.domain.MessageNodeId("message-$id"),
            title = "对话$id",
            attachment = com.nanzhufeng.ai.domain.ConversationAttachmentReference(
                id = com.nanzhufeng.ai.domain.AttachmentId(id),
                mimeType = mimeType,
                displayName = name,
                byteCount = 1L,
                sha256 = "b".repeat(64),
            ),
            timestampEpochMs = 1L,
        )
        val hits = listOf(
            hit("md", "笔记。 md", "text/plain"),
            hit("pdf", "说明.pdf", "application/pdf"),
            hit("zip", "资料.zip", "application/zip"),
            hit("xlsx", "表格.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            hit("pptx", "演示.pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            hit("csv", "数据.csv", "text/csv"),
            hit("xml", "结构.xml", "application/xml"),
            hit("yaml", "配置.yaml", "application/x-yaml"),
            hit("html", "页面.html", "text/html"),
            hit("other", "脚本.py", "application/octet-stream"),
        )
        fun ids(type: ConversationAttachmentFileType) = filterSearchFileType(hits, type).map { it.attachment.id.value }
        assertEquals(listOf("md"), ids(ConversationAttachmentFileType.MARKDOWN))
        assertEquals(listOf("pdf"), ids(ConversationAttachmentFileType.PDF))
        assertEquals(listOf("zip"), ids(ConversationAttachmentFileType.ZIP))
        assertEquals(listOf("xlsx", "pptx", "csv", "xml", "yaml", "html", "other"), ids(ConversationAttachmentFileType.OTHER))
    }

    @Test
    fun `attachment time and size sorting are global stable orders with sort-aware anchors`() {
        fun hit(id: String, timestamp: String, byteCount: Long) = com.nanzhufeng.ai.domain.ConversationAttachmentSearchHit(
            conversationId = com.nanzhufeng.ai.domain.ConversationId("conversation-$id"),
            messageNodeId = com.nanzhufeng.ai.domain.MessageNodeId("message-$id"),
            title = "对话$id",
            attachment = com.nanzhufeng.ai.domain.ConversationAttachmentReference(
                id = com.nanzhufeng.ai.domain.AttachmentId(id),
                mimeType = "image/png",
                displayName = "$id.png",
                byteCount = byteCount,
                sha256 = "b".repeat(64),
            ),
            timestampEpochMs = java.time.Instant.parse(timestamp).toEpochMilli(),
        )
        val hits = listOf(
            hit("older-large", "2026-06-01T00:00:00Z", 300L),
            hit("newer-small", "2026-08-01T00:00:00Z", 100L),
            hit("middle", "2026-07-01T00:00:00Z", 200L),
        )
        val ocrHit = com.nanzhufeng.ai.domain.GlmOcrDocumentSearchHit(
            taskId = com.nanzhufeng.ai.domain.GlmOcrTaskId("ocr-task"),
            kind = com.nanzhufeng.ai.domain.GlmOcrSearchDocumentKind.MARKDOWN,
            title = "南枫转写 · Markdown",
            attachment = com.nanzhufeng.ai.domain.ConversationAttachmentReference(
                id = com.nanzhufeng.ai.domain.AttachmentId("ocr-largest"),
                mimeType = "text/markdown",
                displayName = "ocr.md",
                byteCount = 400L,
                sha256 = "c".repeat(64),
            ),
            timestampEpochMs = java.time.Instant.parse("2026-05-01T00:00:00Z").toEpochMilli(),
        )

        fun ids(mode: ConversationAttachmentSortMode) = sortedSearchAttachmentHits(hits, mode).map { it.attachment.id.value }
        assertEquals(listOf("newer-small", "middle", "older-large"), ids(ConversationAttachmentSortMode.DEFAULT))
        assertEquals(listOf("newer-small", "middle", "older-large"), ids(ConversationAttachmentSortMode.TIME_DESCENDING))
        assertEquals(listOf("older-large", "middle", "newer-small"), ids(ConversationAttachmentSortMode.TIME_ASCENDING))
        assertEquals(listOf("older-large", "middle", "newer-small"), ids(ConversationAttachmentSortMode.SIZE_DESCENDING))
        assertEquals(listOf("newer-small", "middle", "older-large"), ids(ConversationAttachmentSortMode.SIZE_ASCENDING))
        assertEquals(
            listOf("ocr-largest", "older-large", "middle", "newer-small"),
            sortedUnifiedSearchAttachmentEntries(hits, listOf(ocrHit), ConversationAttachmentSortMode.SIZE_DESCENDING).map { it.attachment.id.value },
        )

        val state = ConversationFoundationUiState(
            searchResults = listOf(
                com.nanzhufeng.ai.domain.ConversationSearchHit(
                    conversationId = com.nanzhufeng.ai.domain.ConversationId("text"),
                    messageNodeId = null,
                    title = "正文",
                    snippet = "正文",
                    titleMatch = true,
                ),
            ),
            attachmentSearchResults = hits,
            glmOcrSearchResults = listOf(ocrHit),
        )
        assertEquals(4, searchGridAttachmentItemIndex(hits, "newer-small", sortMode = ConversationAttachmentSortMode.SIZE_DESCENDING, glmOcrHits = listOf(ocrHit)))
        assertEquals(6, searchAllAttachmentItemIndex(state, "newer-small", sortMode = ConversationAttachmentSortMode.SIZE_DESCENDING))
    }

    @Test
    fun `attachment fallback indices include text total and month heading items`() {
        fun hit(id: String, timestamp: String) = com.nanzhufeng.ai.domain.ConversationAttachmentSearchHit(
            conversationId = com.nanzhufeng.ai.domain.ConversationId("conversation-$id"),
            messageNodeId = com.nanzhufeng.ai.domain.MessageNodeId("message-$id"),
            title = "对话$id",
            attachment = com.nanzhufeng.ai.domain.ConversationAttachmentReference(
                id = com.nanzhufeng.ai.domain.AttachmentId(id),
                mimeType = "image/png",
                displayName = "$id.png",
                byteCount = 1L,
                sha256 = "a".repeat(64),
            ),
            timestampEpochMs = java.time.Instant.parse(timestamp).toEpochMilli(),
        )
        val hits = listOf(
            hit("aug-a", "2026-08-20T00:00:00Z"),
            hit("aug-b", "2026-08-10T00:00:00Z"),
            hit("jul-c", "2026-07-20T00:00:00Z"),
        )
        val state = ConversationFoundationUiState(
            searchResults = listOf(
                com.nanzhufeng.ai.domain.ConversationSearchHit(
                    conversationId = com.nanzhufeng.ai.domain.ConversationId("text"),
                    messageNodeId = null,
                    title = "正文",
                    snippet = "正文",
                    titleMatch = true,
                ),
            ),
            attachmentSearchResults = hits,
        )

        assertEquals(5, searchAllAttachmentItemIndex(state, "aug-b"))
        assertEquals(7, searchAllAttachmentItemIndex(state, "jul-c"))
        assertEquals(3, searchGridAttachmentItemIndex(hits, "aug-b"))
        assertEquals(5, searchGridAttachmentItemIndex(hits, "jul-c"))
        assertEquals(null, searchGridAttachmentItemIndex(hits, "missing"))

        val sharedReferenceHits = listOf(
            hit("shared", "2026-08-20T00:00:00Z"),
            hit("shared", "2026-07-20T00:00:00Z").copy(
                conversationId = com.nanzhufeng.ai.domain.ConversationId("conversation-second"),
                messageNodeId = com.nanzhufeng.ai.domain.MessageNodeId("message-second"),
            ),
        )
        assertEquals(
            4,
            searchGridAttachmentItemIndex(
                sharedReferenceHits,
                attachmentId = "shared",
                conversationId = "conversation-second",
                messageNodeId = "message-second",
            ),
        )
    }

    @Test
    fun `正文和全部同步展示本机事实并由同一筛选真实排序`() {
        val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val results = workspace.substringAfter("private fun SearchAllOrTextResults").substringBefore("private fun SearchAttachmentGrid")
        val position = File("src/main/java/com/nanzhufeng/ai/ui/ConversationSearchPosition.kt").readText()
        val viewModel = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()

        assertFalse(workspace.contains("if (state.searchCategory != ConversationSearchCategory.TEXT)"))
        assertTrue(results.contains("val textResults = remember(state.searchResults, sortMode)"))
        assertTrue(results.contains("sortedConversationSearchHits(state.searchResults, sortMode)"))
        assertTrue(results.contains("formatAttachmentBytes(hit.byteCount)"))
        assertTrue(results.contains("formatSearchAttachmentTimestamp(hit.timestampEpochMs)"))
        assertTrue(results.contains("maxLines = 2"))
        assertTrue(results.contains("\"正文 · \${textResults.size} 条\""))
        assertTrue(results.contains("\"附件 · \$totalAttachmentCount 项\""))
        assertTrue(position.contains("internal fun sortedConversationSearchHits("))
        assertTrue(viewModel.contains("val (results, attachments, glmOcrDocuments) = coroutineScope"))
        assertTrue(viewModel.contains("val text = async(Dispatchers.IO)"))
        assertTrue(viewModel.contains("val attachment = async(Dispatchers.IO)"))

        fun hit(id: String, time: Long, bytes: Long) = com.nanzhufeng.ai.domain.ConversationSearchHit(
            conversationId = com.nanzhufeng.ai.domain.ConversationId(id),
            messageNodeId = null,
            title = id,
            snippet = id,
            titleMatch = false,
            timestampEpochMs = time,
            byteCount = bytes,
        )
        val hits = listOf(hit("old-large", 1L, 300L), hit("new-small", 3L, 100L), hit("middle", 2L, 200L))
        assertEquals(listOf("new-small", "middle", "old-large"), sortedConversationSearchHits(hits, ConversationAttachmentSortMode.TIME_DESCENDING).map { it.conversationId.value })
        assertEquals(listOf("old-large", "middle", "new-small"), sortedConversationSearchHits(hits, ConversationAttachmentSortMode.SIZE_DESCENDING).map { it.conversationId.value })
    }

    private fun String.countOccurrences(token: String): Int = windowed(token.length, 1).count { it == token }
}
