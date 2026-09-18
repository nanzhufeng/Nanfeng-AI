package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** K9: Settings may expose task state, never the selected ZIP filename. */
class P6KZipImportUiContractsTest {
    @Test fun `import detail page remains anonymous while retaining ZIP batch revoke`() {
        val source = File("src/main/java/com/nanzhufeng/ai/ui/P6KZipImportUi.kt").readText()

        assertTrue(source.contains("导入 ChatGPT ZIP"))
        assertTrue(source.contains("导入 Claude ZIP"))
        assertTrue(source.contains("ImportResultsDetailsPage"))
        assertTrue(source.contains("删除本批次"))
        assertTrue(source.contains("重复导入会按官方身份去重"))
        assertTrue(source.contains("这些对话和附件都不会被重新导入"))
        assertTrue(source.contains("个对话已导入"))
        assertTrue(source.contains("个未导入"))
        assertFalse(source.contains("这里不显示聊天正文、标题或原始文件名"))
        assertFalse(source.contains("init { show() }"))
        assertTrue(File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText().contains("p6kZipImportViewModel.show()"))
        assertTrue(source.contains("已保留任务与私有副本；请重试删除"))
        assertTrue(source.contains("workingProvider: ThirdPartyZipProvider?"))
        assertTrue(source.contains("workingProvider = provider"))
        assertTrue(source.contains("state.workingProvider == ThirdPartyZipProvider.CHATGPT"))
        assertTrue(source.contains("state.workingProvider == ThirdPartyZipProvider.CLAUDE"))
        assertFalse(source.contains("task.displayName"))
        assertFalse(source.contains("item.candidate!!.title"))
        assertFalse(source.contains("target.conversationTitle"))
    }

    @Test fun `only the selected ZIP provider presents a loading indicator while both rows remain identified`() {
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val row = app.substring(app.indexOf("internal fun DataStorageGroupedActionRow"), app.indexOf("private fun SettingsCategoryList"))

        assertTrue(row.contains("Row(verticalAlignment = Alignment.CenterVertically)"))
        assertTrue(row.contains("if (working)"))
        assertTrue(row.contains("Text(label, style = MaterialTheme.typography.titleMedium, color = BodyText)"))
    }

    @Test fun `JSON and ZIP retain separate cards and separate results destinations`() {
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val zipUi = File("src/main/java/com/nanzhufeng/ai/ui/P6KZipImportUi.kt").readText()
        val importCenter = app.substring(app.indexOf("private fun DataImportCenterContent"), app.indexOf("private fun WorkspaceExchangeV2ExportCard"))

        for (label in listOf("导入 ChatGPT JSON", "导入 Claude JSON")) {
            assertTrue("missing grouped import action $label", importCenter.contains(label))
        }
        for (label in listOf("导入 ChatGPT ZIP", "导入 Claude ZIP")) assertTrue("missing grouped import action $label", zipUi.contains(label))
        assertTrue(importCenter.contains("DataStorageImportResultsRow"))
        assertTrue(app.contains("JSON_IMPORT_RESULTS(\"JSON 导入结果\")"))
        assertTrue(app.contains("ZIP_IMPORT_RESULTS(\"ZIP 导入结果\")"))
        assertTrue(app.contains("SettingsDestination.JSON_IMPORT_RESULTS -> jsonImportResultsContent()"))
        assertTrue(app.contains("SettingsDestination.ZIP_IMPORT_RESULTS -> zipImportResultsContent()"))
        assertTrue(importCenter.contains("onOpenJsonImportResults"))
        assertTrue(importCenter.contains("onOpenZipImportResults"))
        assertTrue(importCenter.contains("showTaskList = false"))
        val resultsRow = app.substring(app.indexOf("private fun DataStorageImportResultsRow"), app.indexOf("private fun SettingsCategoryList"))
        assertTrue(resultsRow.contains("Icons.Rounded.ChevronRight"))
        assertTrue(resultsRow.contains("查看导入结果"))
        assertFalse(resultsRow.contains("summary:"))
        assertFalse(resultsRow.contains("查看详情"))
        assertTrue(zipUi.contains("个附件已恢复"))
        assertTrue(zipUi.contains("源包缺少"))
        assertTrue(zipUi.contains("个附件未关联"))
        assertFalse(zipUi.contains("张 ChatGPT 生成图已依据官方图片清单和有界时间关系恢复"))
        assertTrue("JSON result entry must remain visible even before a task exists", !importCenter.contains("if (chatGptImportState.tasks.isNotEmpty() || claudeImportState.tasks.isNotEmpty())"))
        assertTrue("ZIP result entry must remain visible even before a task exists", !importCenter.contains("if (p6kZipImportState.tasks.isNotEmpty())"))
    }

    @Test fun `conversation ZIP pickers request only ZIP document MIME types`() {
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val pickerActions = app.substring(app.indexOf("onOpenP6KChatGptZip ="), app.indexOf("onClearP6KZip ="))

        for (token in listOf("p6kChatGptZipPicker.launch(arrayOf(\"application/zip\", \"application/x-zip-compressed\"))", "p6kClaudeZipPicker.launch(arrayOf(\"application/zip\", \"application/x-zip-compressed\"))")) {
            assertTrue("missing ZIP-only picker request $token", pickerActions.contains(token))
        }
        assertFalse(pickerActions.contains("arrayOf(\"*/*\")"))
    }

    @Test fun `legacy empty attachment marker cannot suppress the source mapping retry`() {
        val intake = File("src/main/java/com/nanzhufeng/ai/data/AndroidP6KZipIntakeStore.kt").readText()
        assertTrue(intake.contains("mapping.assets.isNotEmpty()"))
        assertTrue(intake.contains("assets-v2.done"))
        assertTrue(intake.contains("legacyAssetMarker(task.id.value).delete()"))
    }

    @Test fun `ZIP attachment recovery is durable foreground work and UI reads Room only`() {
        val intake = File("src/main/java/com/nanzhufeng/ai/data/AndroidP6KZipIntakeStore.kt").readText()
        val scheduling = File("src/main/java/com/nanzhufeng/ai/data/P6KZipAssetRecoveryScheduling.kt").readText()
        val ui = File("src/main/java/com/nanzhufeng/ai/ui/P6KZipImportUi.kt").readText()
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val listOwner = intake.substring(intake.indexOf("override fun list()"), intake.indexOf("override fun recoveryJobs()"))

        assertFalse(listOwner.contains("ZipFile("))
        assertFalse(listOwner.contains("assetMapper.map"))
        assertFalse(listOwner.contains("reconcileMappedAssets"))
        assertTrue(scheduling.contains("OneTimeWorkRequestBuilder<P6KZipAssetRecoveryWorker>()"))
        assertTrue(scheduling.contains("setForeground(recoveryForegroundInfo(applicationContext, ConversationSurface.valueOf(inputData.getString(\"dataArea\") ?: \"CHAT\")))"))
        assertTrue(scheduling.contains("FOREGROUND_SERVICE_TYPE_DATA_SYNC"))
        assertTrue(scheduling.contains("resumeScope.launch {"))
        assertTrue(scheduling.contains("jobs.resumable().forEach"))
        assertTrue(scheduling.contains("migrateLegacyJobs()"))
        assertTrue(scheduling.contains("existing.indexVersion < P6K_ZIP_ASSET_MAPPING_INDEX_VERSION"))
        assertTrue(scheduling.contains("processedConversations = 0"))
        assertTrue(scheduling.contains("P6KZipAssetRecoveryState.PENDING"))
        assertTrue(manifest.contains("androidx.work.impl.foreground.SystemForegroundService"))
        assertTrue(manifest.contains("android:foregroundServiceType=\"dataSync\""))
        assertFalse(scheduling.contains("setExpedited"))
        assertTrue(ui.contains("正在恢复 ${'$'}{job.linkedOccurrences}/${'$'}{job.uniqueAssets}"))
        assertTrue(ui.contains("源包缺少 ${'$'}it 个附件"))
        assertTrue(ui.contains("${'$'}it 个附件未关联"))
        assertFalse(ui.contains("缺少官方显示名，已使用文件 ID 回退命名"))
        assertTrue(ui.contains("重试附件恢复"))
        val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        assertTrue(workspace.contains("val ownedPlaybackFile = localFile"))
        assertTrue(workspace.contains("onDispose { ownedPlaybackFile?.delete() }"))
        assertFalse(workspace.contains("DisposableEffect(localFile) { onDispose { localFile?.delete() } }"))
    }
}
