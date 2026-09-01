package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeLibraryUiSimplificationContractsTest {
    @Test
    fun `knowledge page uses centered title and icon-only parent navigation`() {
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val header = app.substringAfter("private fun RoutePageHeader(").substringBefore("@Composable\nprivate fun PreviewCard")

        assertTrue(header.contains("Icons.AutoMirrored.Outlined.ArrowBack"))
        assertTrue(header.contains("contentDescription = backContentDescription"))
        assertTrue(header.contains("Modifier.align(Alignment.Center).semantics { heading() }"))
        assertTrue(header.contains("P5ARoute.KNOWLEDGE -> null"))
        assertFalse(header.contains("知识管理与导出保持明确确认和本地优先边界"))
        val backHierarchy = app.substringAfter("val returnFromCurrentPage: () -> Unit = when").substringBefore("val settingsSearchReturnsToLocalData")
        assertTrue(backHierarchy.indexOf("knowledgeLibraryState.editing") < backHierarchy.indexOf("knowledgeLibraryState.detail != null"))
        assertTrue(backHierarchy.contains("knowledgeLibraryViewModel::cancelEdit"))
        assertTrue(backHierarchy.contains("knowledgeLibraryViewModel::backToList"))
        assertTrue(app.contains("BackHandler(onBack = returnFromCurrentPage)"))
        assertTrue(app.contains("Modifier.settingsEdgeExit(returnFromCurrentPage)"))
        assertTrue(app.contains("onBack = returnFromCurrentPage"))
        assertTrue(app.contains("knowledgeDetailVisible -> \"知识详情\""))
        assertTrue(app.contains("knowledgeEditorVisible -> \"编辑知识\""))
    }

    @Test
    fun `knowledge catalogue separates lifecycle filters from actions and removes source filter row`() {
        val source = File("src/main/java/com/nanzhufeng/ai/ui/KnowledgeLibraryUi.kt").readText()
        val page = source.substringAfter("fun KnowledgeLibraryPage(").substringBefore("@Composable\nprivate fun KnowledgeLoading")
        val projection = source.substringAfter("private fun filteredEntries(").substringBefore("\n    class Factory")

        assertTrue(page.contains("KnowledgeStatusSelector(state.status, onStatus)"))
        assertTrue(page.contains("KnowledgePrimaryActions("))
        assertTrue(page.contains("Text(\"新建知识\")"))
        assertTrue(page.contains("Text(\"整理当前对话\")"))
        assertTrue(source.contains("KnowledgeStatus.ACTIVE -> \"知识\""))
        assertTrue(source.contains("state.copy(status = status)"))
        assertFalse(page.contains("全部来源"))
        assertFalse(page.contains("listOf<CaptureSourceType?>"))
        assertFalse(source.contains("fun setSourceType("))
        assertFalse(source.contains("val sourceType: CaptureSourceType?"))
        assertFalse(page.contains("if (detail == null) \"本地知识\""))
        assertFalse(page.contains("Text(\"知识详情\""))
        assertTrue(source.contains("if (debounce) delay(180)"))
        assertTrue(source.contains("reloadJob?.cancel()"))
        assertFalse(projection.contains("manageKnowledge.detail("))
    }

    @Test
    fun `knowledge detail keeps user facts and icon actions without technical audit clutter`() {
        val source = File("src/main/java/com/nanzhufeng/ai/ui/KnowledgeLibraryUi.kt").readText()
        val detail = source.substringAfter("private fun KnowledgeDetailContent(").substringBefore("@Composable\nprivate fun KnowledgeDetailActions")

        assertTrue(detail.contains("source.sourceType.displayLabel()"))
        assertTrue(detail.contains("保存于 \${formatKnowledgeTime(item.createdAt)}"))
        assertTrue(detail.contains("状态 · \${snapshot.lifecycle.status.label()}"))
        assertTrue(detail.contains("KnowledgeDetailActions("))
        for (technicalCopy in listOf("Candidate：", "Invocation：", "Harness v", "生成与保存溯源", "本机查找重复候选", "建立本地关系", "查看关系", "不会显示 API Key")) {
            assertFalse("technical detail remains visible: $technicalCopy", detail.contains(technicalCopy))
        }
        assertTrue(source.contains("KnowledgeDetailIconAction(Icons.Rounded.Edit, \"编辑\""))
        assertTrue(source.contains("Icons.Rounded.DeleteOutline"))
    }
}
