package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidUserEntryAuditContractsTest {
    @Test
    fun adaptersRouteExposesExistingLocalKnowledgeOwnersWithoutCredentialControls() {
        val source = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val card = source.substringAfter("private fun LocalKnowledgeImportEntryCard(").substringBefore("@Composable\nprivate fun SettingsPageHeader")

        listOf(
            "onOpenMarkdownImport",
            "onOpenMarkdownExport",
            "onOpenJsonKnowledgeImport",
            "onOpenJsonKnowledgeExport",
            "onOpenPdfTextImport",
            "onOpenWebTextSnapshot",
            "导入 Markdown 文件",
            "导入 PDF 文本",
            "保存网页文本快照",
        ).forEach { token -> assertTrue("missing local entry: $token", card.contains(token)) }
        assertTrue(!card.contains("revealStoredCredential"))
        assertTrue(!card.contains("Provider"))
    }

    @Test
    fun v2FeatureReviewSynchronizesAndroidEmptyLocalRestoreAndDesktopPrivateReexport() {
        val source = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val card = source.substringAfter("private fun FeatureReviewSettingsCard()").substringBefore("@Composable\nprivate fun SettingsCategoryRow")

        listOf(
            "完整工作区交换（v2）",
            "Android 可从设置 → 数据与导入选择单个 v2 包，仅在空本机严格恢复",
            "只保留双端设置二级入口",
            "不在聊天主页、Composer 或工作页增加按键",
        ).forEach { token -> assertTrue("missing v2 review detail: $token", card.contains(token)) }
        assertTrue(card.contains("也不会覆盖已有本机数据"))
    }

    @Test
    fun aboutSettingsShowsRuntimeVersionWithoutInventingSupportOrLegalActions() {
        val source = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val hierarchy = source.substringAfter("private fun SettingsHierarchy(").substringBefore("@Composable\nprivate fun SettingsCategoryList")
        val about = source.substringAfter("private fun AboutSettingsCard()").substringBefore("@Composable\nprivate fun SettingsCategoryRow")

        assertTrue(hierarchy.contains("SettingsDestination.ABOUT -> AboutSettingsCard"))
        for (token in listOf("Android 版 \${BuildConfig.VERSION_NAME}", "构建号 \${BuildConfig.VERSION_CODE}", "数据与隐私")) assertTrue("missing about detail: $token", about.contains(token))
        assertTrue("about modules must share one full-width rounded foreground card", about.contains("modifier = Modifier.fillMaxWidth()") && about.contains("shape = CardShape"))
        assertTrue("about modules must retain the shared canvas separators", Regex("SettingsCategoryDivider\\(\\)").findAll(about).count() == 2)
        assertTrue("about sections must not shrink to their text", about.contains("AboutSettingsSection") && about.contains("Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)"))
        for (forbidden in listOf("帮助中心", "使用条款", "许可证")) assertFalse("invented about action: $forbidden", about.contains(forbidden))
    }

    @Test
    fun workspaceSettingsKeepExistingOwnersReachableWithoutADeveloperNamedHub() {
        val source = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val settings = source.substringAfter("private fun SettingsHierarchy(").substringBefore("@Composable\nprivate fun SettingsCategoryList")
        val workspace = source.substringAfter("private fun WorkspaceSettingsCard(").substringBefore("@Composable\nprivate fun FeatureReviewSettingsCard")
        val review = source.substringAfter("private fun FeatureReviewSettingsCard()").substringBefore("@Composable\nprivate fun SettingsCategoryRow")

        assertTrue(settings.contains("SettingsDestination.WORKSPACE -> WorkspaceSettingsCard"))
        for (token in listOf("管理 Projects", "管理知识库", "label = \"本次 Context 控制\"", "onClick = onOpenContext", "label = \"离线评测\"", "onClick = onOpenOfflineEval")) assertTrue("missing workspace setting: $token", workspace.contains(token))
        assertFalse(settings.contains("SettingsDestination.LOCAL_CONTROL"))
        for (token in listOf("项目、知识与本地控制", "个性化资料与记忆摘要统一在设置 → 个性化", "Projects、知识、Context 与离线评测", "设置二级入口", "不建议在聊天主页、Composer 或会话详情增加按键")) assertTrue("missing feature review: $token", review.contains(token))
    }

    @Test
    fun scheduledMonitoringIsReviewedAsAnExplicitAndroidOwnerAndNotASpoofedDesktopFeature() {
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val executor = File("src/main/java/com/nanzhufeng/ai/ai/ScheduledMonitorExecutor.kt").readText()
        val review = app.substringAfter("private fun FeatureReviewSettingsCard()").substringBefore("@Composable\nprivate fun AboutSettingsCard")

        for (token in listOf("计划监控与对话提醒", "本地依据当前一问一答整理建议草案，保存前可编辑且不自动发送对话正文", "Desktop 尚待同等真实 owner")) assertFalse("duplicate feature-review prompt remains: $token", review.contains(token))
        for (token in listOf("已计划", "添加提醒 / 监控", "ConversationTailMonitorAction")) assertTrue("missing approved entry: $token", workspace.contains(token))
        assertTrue(workspace.contains("ScheduledMonitorSuggestionPolicy.shouldOffer"))
        assertTrue(workspace.contains("assistantIndex < 0 || assistantIndex != messages.lastIndex"))
        assertTrue(executor.contains("监控要求：\${task.instruction}"))
        assertFalse(executor.contains("state.messages"))
        assertFalse(executor.contains("sourceConversationId"))
    }

    @Test
    fun notificationAndReminderSwitchesGateTheirExistingAndroidOwners() {
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val scheduler = File("src/main/java/com/nanzhufeng/ai/data/ScheduledMonitorScheduling.kt").readText()
        val audit = File("../docs/ANDROID_DESKTOP_USER_ENTRY_AUDIT_20260816.md").readText()

        for (token in listOf("SettingsDestination.NOTIFICATIONS", "NotificationReminderSettingsCard", "计划监控结果通知", "对话提醒建议", "对话未读提醒")) assertTrue("missing notification setting: $token", app.contains(token))
        assertTrue(workspace.contains("notificationReminderSettings.conversationReminderSuggestionsEnabled"))
        assertTrue(workspace.contains("notificationReminderSettings.unreadConversationIndicatorsEnabled"))
        assertTrue(scheduler.contains("container.loadNotificationReminderSettings.execute().monitorResultsNotificationEnabled"))
        for (token in listOf("通知与提醒设置", "Desktop：** 待实现", "不显示无效开关或伪通知")) assertTrue("missing cross-platform audit: $token", audit.contains(token))
    }

    @Test
    fun favoriteUsesTheExistingConversationMenuAndManagementRoute() {
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val audit = File("../docs/ANDROID_DESKTOP_USER_ENTRY_AUDIT_20260816.md").readText()
        val menu = workspace.substring(workspace.indexOf("private fun ConversationActionSheet"), workspace.indexOf("private fun requestPinConversationShortcut"))

        assertTrue(app.contains("FAVORITE_CONVERSATIONS(\"收藏\")"))
        assertTrue(app.contains("ConversationListScope.FAVORITES"))
        assertTrue(workspace.contains("FavoriteConversationListSettingsCard"))
        assertTrue(menu.indexOf("ConversationManagementAction.PIN") < menu.indexOf("ConversationManagementAction.FAVORITE"))
        assertTrue(audit.contains("2026-08-27 本地会话收藏"))
        assertTrue(audit.contains("Desktop：** 待实现"))
    }
}
