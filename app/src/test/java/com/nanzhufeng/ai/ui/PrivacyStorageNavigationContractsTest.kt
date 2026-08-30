package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyStorageNavigationContractsTest {
    @Test
    fun `local data owns inventory and destructive controls while import export stays focused`() {
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val storageBranch = app.substring(
            app.indexOf("SettingsDestination.DATA_STORAGE ->"),
            app.indexOf("SettingsDestination.JSON_IMPORT_RESULTS ->"),
        )
        val localDataBranch = app.substring(
            app.indexOf("SettingsDestination.PRIVACY ->"),
            app.indexOf("@Composable\nprivate fun MemoryOverviewSettingsCard"),
        )

        assertFalse(storageBranch.contains("PrivacyStorageSummary("))
        assertTrue(localDataBranch.contains("LaunchedEffect(Unit) { onOpenPrivacyData() }"))
        assertTrue(localDataBranch.contains("PrivacyStorageSummary("))
        assertTrue(localDataBranch.contains("PrivacyDataPage("))
        assertTrue(app.contains("PRIVACY(\"本机数据\")"))
        assertTrue(app.contains("SettingsCategoryRow(Icons.Rounded.Storage, \"本机数据\""))
        assertFalse(app.contains("清理与删除"))
    }

    @Test
    fun `inventory drill downs reuse real search and workspace destinations`() {
        val privacy = File("src/main/java/com/nanzhufeng/ai/ui/PrivacyDataUi.kt").readText()
        val summary = privacy.substring(
            privacy.indexOf("internal fun PrivacyStorageSummary"),
            privacy.indexOf("private data class PrivacySummaryRow"),
        )

        for ((label, category) in listOf(
            "对话" to "ALL",
            "消息" to "TEXT",
            "图片" to "IMAGE",
            "视频" to "VIDEO",
            "音频" to "AUDIO",
            "文档与其他文件" to "FILE",
        )) {
            assertTrue(summary.contains("PrivacySummaryRow(\"$label\""))
            assertTrue(summary.contains("ConversationSearchCategory.$category"))
        }
        assertTrue(summary.contains("PrivacySummaryRow(\"记忆\", values[\"memory\"], \"条\", onOpenMemory)"))
        assertTrue(summary.contains("PrivacySummaryRow(\"知识\", values[\"knowledge\"], \"条\", onOpenKnowledge)"))
        assertTrue(summary.contains("PrivacySummaryRow(\"项目\", values[\"projects\"], \"个\", onOpenProjects)"))
        assertFalse(summary.contains("PrivacySummaryRow(\"草稿\""))
        assertTrue(summary.contains("PrivacySummaryRow(\"其他导入资料\", values[\"import_source_assets\"], \"份\")"))
        assertTrue(summary.contains("PrivacySummaryRow(\"待清理残留文件\", values[\"orphaned_attachment_files\"], \"个\")"))
        assertTrue(privacy.contains("\"南枫转写\" to \"\$glmOcrTasks 条"))
        assertTrue(privacy.contains("PrivacyDeleteScope.ORPHANED_ATTACHMENT_FILES"))
    }

    @Test
    fun `only actionable storage rows show a chevron and open the existing search page`() {
        val privacy = File("src/main/java/com/nanzhufeng/ai/ui/PrivacyDataUi.kt").readText()
        val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()

        val rows = privacy.substring(
            privacy.indexOf("private fun PrivacySummarySection"),
            privacy.indexOf("private fun PrivacyImportSummary"),
        )
        assertTrue(rows.contains("if (row.onClick != null)"))
        assertTrue(rows.contains("Icons.Rounded.ChevronRight"))
        assertTrue(rows.contains("Surface(\n                    onClick = row.onClick"))

        assertTrue(app.contains("pendingConversationSearchCategory = category"))
        assertTrue(app.contains("openSettingsLevel(P5ARoute.CONVERSATION, SettingsDestination.PRIVACY)"))
        assertTrue(app.contains("settingsSearchReturnsToLocalData"))
        assertTrue(app.contains("searchDismissesToParent = settingsSearchReturnsToLocalData"))
        assertTrue(workspace.contains("LaunchedEffect(initialSearchCategory)"))
        assertTrue(workspace.contains("onSearchCategoryChanged(category)"))
        assertTrue(workspace.contains("searchPageVisible = true"))
        assertTrue(workspace.contains("if (searchDismissesToParent)"))
    }

    @Test
    fun `memory inventory opens a small hub backed by existing owners`() {
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val hub = app.substring(
            app.indexOf("private fun MemoryOverviewSettingsCard"),
            app.indexOf("private fun DataStorageFunctionalGroup"),
        )

        assertTrue(app.contains("MEMORY_OVERVIEW(\"记忆\")"))
        assertTrue(app.contains("onOpenMemory = { onSelect(SettingsDestination.MEMORY_OVERVIEW) }"))
        assertTrue(hub.contains("记忆摘要") && hub.contains("onOpenMemorySummary"))
        assertTrue(hub.contains("个性化与资料库搜索") && hub.contains("onOpenPersonalization"))
        assertFalse(hub.contains("查看详情"))
    }
}
