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
            "全部" to "ALL",
            "正文" to "TEXT",
            "图片" to "IMAGE",
            "视频" to "VIDEO",
            "音频" to "AUDIO",
            "文件" to "FILE",
        )) {
            assertTrue(summary.contains("PrivacySummaryRow(\"$label\"") || summary.contains("label = \"$label\""))
            assertTrue(summary.contains("ConversationSearchCategory.$category"))
        }
        assertTrue(summary.contains("PrivacySummaryRow(\"记忆\", values[\"memory\"], \"条\", onClick = onOpenMemory)"))
        assertTrue(summary.contains("PrivacySummaryRow(\"知识库\", values[\"knowledge\"], \"条\", onClick = onOpenKnowledge)"))
        assertTrue(summary.contains("PrivacySummaryRow(\"项目\", values[\"projects\"], \"个\", onClick = onOpenProjects)"))
        assertFalse(summary.contains("PrivacySummaryRow(\"草稿\""))
        assertTrue(summary.contains("PrivacySummaryRow(\"其他导入资料\", values[\"import_source_assets\"], \"份\")"))
        assertTrue(summary.contains("valueText = \"\${searchableText?.count ?: 0} 条正文 · \${searchableAttachments?.count ?: 0} 项附件\""))
        assertTrue(summary.contains("PrivacySummarySection(\"附件\", attachmentRows)"))
        assertFalse(summary.contains("待清理残留文件"))
        assertTrue(privacy.contains("\"南枫转写\" to \"\$glmOcrTasks 条"))
        val cleanupDialog = privacy.substringAfter("private fun PrivacyCleanupScopeDialog")
        val scopes = cleanupDialog.substring(cleanupDialog.indexOf("val scopes = listOf("), cleanupDialog.indexOf("Dialog(onDismissRequest = onDismiss)"))
        assertFalse(scopes.contains("PrivacyDeleteScope.ORPHANED_ATTACHMENT_FILES"))
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

    @Test
    fun `本机数据先呈现缓存快照再后台更新并不暴露无引用残留`() {
        val viewModel = File("src/main/java/com/nanzhufeng/ai/ui/PrivacyDataViewModel.kt").readText()
        val manager = File("src/main/java/com/nanzhufeng/ai/data/AndroidPrivacyDataManager.kt").readText()
        val privacyUi = File("src/main/java/com/nanzhufeng/ai/ui/PrivacyDataUi.kt").readText()

        assertTrue(viewModel.contains("PrivacyDataUiState(inventory = manager.cachedInventory())"))
        assertTrue(viewModel.contains("val inventory: PrivacyInventory = PrivacyInventory.EmptySnapshot"))
        assertFalse(viewModel.contains("init { refresh() }"))
        assertTrue(viewModel.contains("fun show() { state = state.copy(visible = true, notice = null, error = null); refresh() }"))
        assertTrue(viewModel.contains("if (refreshJob?.isActive == true) return"))
        assertTrue(manager.contains("override fun cachedInventory(): PrivacyInventory"))
        assertTrue(manager.contains("?: return PrivacyInventory.EmptySnapshot"))
        assertTrue(manager.contains(".commit()"))
        assertFalse(privacyUi.contains("正在读取本机数据"))
        assertTrue(manager.contains("cleanupOrphanedAttachmentFilesSilently()"))
        assertFalse(manager.substring(manager.indexOf("private fun inventoryAggregates"), manager.indexOf("private operator fun PrivacyAggregate.plus")).contains("orphanedAttachmentAggregate()"))
    }
}
