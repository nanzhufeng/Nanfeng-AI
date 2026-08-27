package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsUiSimplificationContractsTest {
    @Test
    fun `normal settings keep developer controls out of the user path`() {
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val hierarchy = app.substring(app.indexOf("private fun SettingsHierarchy"), app.indexOf("private fun WorkbenchRoute"))

        for (token in listOf("AssistantPersonalizationSettingsCard", "NotificationReminderSettingsCard", "ModelServiceStatusCard", "ConversationManagementSettingsCard", "WorkspaceSettingsCard", "PrivacyDataPage", "AboutSettingsCard", "dataStorageImportContent", "LocalBackupRestorePage")) {
            assertTrue("missing user setting $token", hierarchy.contains(token))
        }
        for (token in listOf("InvocationLedgerCard", "P6GModelSelectionSettingsCard", "P6ETemporaryMaintenanceAcceptanceCard", "installP6GLocalFixtureCatalog")) {
            assertFalse("developer control leaked: $token", hierarchy.contains(token))
        }
    }

    @Test
    fun `same-purpose settings use one selection surface instead of button rows`() {
        val model = File("src/main/java/com/nanzhufeng/ai/ui/ModelSettingsUi.kt").readText()
        val privacy = File("src/main/java/com/nanzhufeng/ai/ui/PrivacyDataUi.kt").readText()
        val conversations = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()

        assertTrue(model.contains("AiPresetSelectionSurface"))
        assertFalse(model.contains("SettingSection("))
        assertFalse(model.contains("固定官方兼容端点"))
        assertTrue(model.contains("val savedKeyMask = \"••••••••••••••••••••••••••••••••\""))
        assertTrue(model.contains("keyEdited && it.isNotBlank()"))
        assertFalse(model.contains("已安全保存。输入新值可替换。"))
        assertTrue(privacy.contains("val scopes = listOf("))
        assertTrue(privacy.contains("PrivacyCleanupScopeDialog("))
        assertFalse(privacy.contains("安全诊断 JSON"))
        val management = conversations.substring(conversations.indexOf("internal fun ConversationManagementSettingsCard"), conversations.indexOf("private fun ConversationSelector"))
        assertTrue(management.contains("ConversationLifecycleEntry("))
        assertTrue(management.contains("DataStorageGroupedCard {") && management.contains("DataStorageGroupedDivider()"))
        assertTrue(management.contains("ConversationLifecycleListSettingsCard("))
        assertFalse(management.contains("DropdownMenu("))
        assertFalse(management.contains("Text(\"查看：\$selectedScope\""))
    }

    @Test
    fun `personalization and memory settings are visible only through truthful controls`() {
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val normalChat = File("src/main/java/com/nanzhufeng/ai/ai/NormalChatOpenRouterExecutor.kt").readText()

        for (token in listOf("个性化", "启用记忆", "记忆摘要", "提醒", "模型与联网", "项目与知识", "数据与存储", "隐私与安全", "关于", "Android 版 \${BuildConfig.VERSION_NAME}", "构建号 \${BuildConfig.VERSION_CODE}")) {
            assertTrue("missing reorganized setting $token", app.contains(token))
        }
        assertFalse(app.substring(app.indexOf("private fun SettingsCategoryList"), app.indexOf("private fun SettingsSwitchRow")).contains("更多本地控制面"))
        assertFalse(app.substring(app.indexOf("private fun SettingsCategoryList"), app.indexOf("private fun SettingsSwitchRow")).contains("记忆与上下文"))
        assertTrue(app.contains("你的昵称"))
        assertTrue(app.contains("你的职业"))
        assertTrue(app.contains("自定义指令"))
        assertTrue(app.contains("AssistantExperienceSettings.CUSTOM_INSTRUCTIONS_MAX_LENGTH"))
        assertTrue(app.contains("\${settings.customInstructions.length} / \${com.nanzhufeng.ai.domain.AssistantExperienceSettings.CUSTOM_INSTRUCTIONS_MAX_LENGTH} 字"))
        val personalization = app.substring(app.indexOf("private fun AssistantPersonalizationSettingsCard"), app.indexOf("private fun com.nanzhufeng.ai.domain.ConversationStyle.label"))
        assertTrue(personalization.contains("minLines = 5") && personalization.contains("maxLines = 5"))
        assertTrue(personalization.contains("padding(end = 18.dp, bottom = 5.dp)"))
        assertTrue(personalization.contains("你的 自定义指令 将用于所有南枫AI的对话。"))
        assertTrue(personalization.contains("Icons.Rounded.OpenInFull") && personalization.contains("全屏编辑自定义指令") && personalization.contains("modifier = Modifier.size(14.4.dp)"))
        assertTrue(personalization.contains("CustomInstructionsFullscreenEditor(") && personalization.contains("DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)"))
        assertTrue(app.contains("onSave = onSavePersonalization"))
        assertTrue(personalization.contains("onSave = {\n                onSave()\n                customInstructionsFullscreen = false"))
        assertFalse(personalization.contains("notice?.let { Spacer(Modifier.height(8.dp)); Text(it, color = BrandGreen"))
        assertTrue(app.contains("private fun CenteredPersonalizationSaveNotice"))
        assertTrue(app.contains("Dialog(onDismissRequest = onDismiss)"))
        assertTrue(app.contains("delay(1_800)"))
        assertTrue(app.contains("assistantExperienceSettingsViewModel::clearNotice"))
        assertTrue(personalization.contains("contentDescription = \"保存自定义指令\"") && personalization.contains("shape = CircleShape"))
        assertTrue(personalization.contains("modifier = Modifier.align(Alignment.Center).semantics { heading() }"))
        assertTrue(app.contains("contentDescription = \"保存个性化设置\"") && app.contains("color = if (saveEnabled) AccentOrange.copy(alpha = 0.14f) else ForegroundSurface"))
        assertTrue(normalChat.contains("experience.modelInstruction(isFirstAssistantReply)"))
        assertTrue(normalChat.contains("includeRelevantMemory = experience.memoryEnabled"))
        assertTrue(app.contains("计划监控结果通知"))
        assertTrue(app.contains("对话提醒建议"))
        assertTrue(app.contains("对话未读提醒"))
    }

    @Test
    fun `all settings levels share one canvas instead of changing background per route`() {
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()

        assertTrue(app.contains("val usesSettingsCanvas = rootRoute != P5ARoute.CAPTURE && rootRoute != P5ARoute.CONVERSATION"))
        assertTrue(app.contains("color = if (usesSettingsCanvas) SettingsPageBackground else PageBackground"))
        assertFalse(app.contains("darkAppearance && navigationViewModel.state.route == P5ARoute.SETTINGS"))
    }

    @Test
    fun `settings use the same compact text-density family as the conversation`() {
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()

        for (token in listOf(
            "private const val SettingsTextScaleFactor = 0.90f",
            "private fun Typography.scaledForSettingsText()",
            "fontSize = fontSize * SettingsTextScaleFactor",
            "lineHeight = lineHeight * SettingsTextScaleFactor",
            "private fun SettingsTextScale",
            "SettingsTextScale {",
        )) assertTrue("missing compact settings typography rule: $token", app.contains(token))
    }

    @Test
    fun `style and appearance selectors use one-line pills with the selected value on the right`() {
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val styleRow = app.substring(app.indexOf("private fun ConversationStylePreferenceRow"), app.indexOf("private enum class AppearancePicker"))
        val appearanceRow = app.substring(app.indexOf("private fun AppearancePreferenceRow"), app.indexOf("private fun AppearancePickerDialog"))

        for (row in listOf(styleRow, appearanceRow)) {
            assertTrue("selector must use the shared pill contour when it is not inside a grouped card", row.contains("shape = P5AInteractiveShape") || row.contains("else P5AInteractiveShape"))
            assertTrue("selector must show its selected value in the row", row.contains("Text(style.label()") || row.contains("Text(value,"))
            assertFalse("selector must not retain a dropdown chevron", row.contains("KeyboardArrowDown"))
            assertFalse("selector must not stack title and selected value", row.contains("Column(modifier = Modifier.weight(1f)"))
        }
        assertTrue(styleRow.contains("height(52.dp)"))
        assertTrue(appearanceRow.contains("height(58.dp)"))
        assertTrue(appearanceRow.contains("Modifier.size(scaledAppIconSize(17.dp))"))
    }

    @Test
    fun `settings status accents follow the selected theme color`() {
        val model = File("src/main/java/com/nanzhufeng/ai/ui/ModelSettingsUi.kt").readText()
        val costs = File("src/main/java/com/nanzhufeng/ai/ui/ConversationCostLedgerUi.kt").readText()
        val privacy = File("src/main/java/com/nanzhufeng/ai/ui/PrivacyDataUi.kt").readText()

        for (screen in listOf(model, costs, privacy)) {
            assertTrue("settings status accent must use the dynamic theme token", screen.contains("AccentOrange"))
            assertFalse("settings status accent must not retain the fixed green token", screen.contains("BrandGreen"))
        }
        assertTrue(model.contains("统计对话、标题与提醒整理的 Token 与金额"))
        assertTrue(costs.contains("会话标题整理"))
    }

    @Test
    fun `memory enablement uses the shared foreground pill with its privacy explanation below`() {
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val personalization = app.substring(
            app.indexOf("private fun AssistantPersonalizationSettingsCard"),
            app.indexOf("private fun ConversationStylePreferenceRow"),
        )
        val switchRow = app.substring(
            app.indexOf("private fun SettingsSwitchRow"),
            app.indexOf("private fun WorkspaceSettingsCard"),
        )

        assertTrue(personalization.contains("title = \"启用记忆\""))
        assertTrue(personalization.contains("surface = true"))
        assertTrue(personalization.contains("允许 南枫AI 根据你的聊天、文件和已关联的应用为你提供个性化体验。"))
        assertTrue(personalization.contains("这是 南枫AI 在与你对话时使用的主要语气。这不会影响 南枫AI 的功能。"))
        assertTrue(personalization.contains("资料库搜索"))
        assertTrue(personalization.contains("允许 南枫AI 自动搜索资料库中的文件以查找答案。"))
        assertTrue(personalization.contains("title = \"资料库搜索\""))
        assertTrue(personalization.contains("summary = \"\""))
        assertTrue(personalization.contains("Spacer(Modifier.height(6.dp))"))
        assertTrue(personalization.contains("modifier = Modifier.padding(horizontal = 4.dp)"))
        assertTrue(personalization.indexOf("title = \"资料库搜索\"") < personalization.indexOf("ConversationStylePreferenceRow("))
        assertFalse(personalization.contains("advancedExpanded"))
        assertFalse(personalization.contains("Text(\"高级\""))
        assertTrue(personalization.contains("Text(\"记忆摘要\", color = BodyText"))
        assertTrue(personalization.contains("查看 南枫AI 对你的了解概览。如果你希望它持续记住某些信息，可以使用下方 自定义指令。"))
        for (token in listOf("color = ForegroundSurface", "shape = P5AInteractiveShape", "heightIn(min = 52.dp)")) {
            assertTrue("memory switch foreground surface must keep the shared pill geometry: $token", switchRow.contains(token))
        }
    }

    @Test
    fun `settings switches use the compact wide common geometry`() {
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val model = File("src/main/java/com/nanzhufeng/ai/ui/ModelSettingsUi.kt").readText()
        val dimensions = File("src/main/java/com/nanzhufeng/ai/ui/SettingsControlDimensions.kt").readText()

        for (screen in listOf(app, model)) {
            assertTrue("settings switch must use the shared component", screen.contains("SettingsSwitch("))
        }
        assertTrue(dimensions.contains("SettingsSwitchTrackWidth = 58.dp"))
        assertTrue(dimensions.contains("SettingsSwitchTrackHeight = 28.dp"))
        assertTrue(dimensions.contains("requiredSize(SettingsSwitchTrackWidth, SettingsSwitchTouchTargetHeight)"))
    }

    @Test
    fun `reminder switches share one white card with inset dividers`() {
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val reminders = app.substring(
            app.indexOf("private fun NotificationReminderSettingsCard"),
            app.indexOf("private fun SettingsSwitchRow"),
        )

        assertTrue(reminders.contains("color = ForegroundSurface"))
        assertTrue(reminders.contains("shape = CardShape"))
        assertTrue(reminders.contains("shadowElevation = 1.dp"))
        assertTrue(reminders.contains("HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = SubtleDivider)"))
        assertTrue(reminders.contains("Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)"))
        assertFalse(reminders.contains("Spacer(Modifier.height(12.dp))"))
    }

    @Test
    fun `data storage removes redundant import export headers and stacks backup actions`() {
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val backup = File("src/main/java/com/nanzhufeng/ai/ui/LocalBackupRestoreUi.kt").readText()
        val exchange = app.substring(
            app.indexOf("private fun WorkspaceExchangeV2ExportCard"),
            app.indexOf("private fun WorkspaceExchangeV2RestoreUiMessage"),
        )

        assertFalse(exchange.contains("Text(\"导入\""))
        assertFalse(exchange.contains("Text(\"导出\""))
        assertFalse(backup.contains("Row(modifier = Modifier.fillMaxWidth()"))
        assertTrue(backup.countOccurrences("Modifier.fillMaxWidth().heightIn(min = 48.dp)") >= 2)
    }

    @Test
    fun `grouped settings and data storage cards use rounded rectangles with single-line rows`() {
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val chatGpt = File("src/main/java/com/nanzhufeng/ai/ui/ChatGptExportImportUi.kt").readText()
        val claude = File("src/main/java/com/nanzhufeng/ai/ui/ClaudeExportImportUi.kt").readText()
        val zip = File("src/main/java/com/nanzhufeng/ai/ui/P6KZipImportUi.kt").readText()
        val backup = File("src/main/java/com/nanzhufeng/ai/ui/LocalBackupRestoreUi.kt").readText()

        val settingsGroups = app.substring(app.indexOf("private fun SettingsCategoryGroup"), app.indexOf("private fun SettingsCategoryDivider"))
        val dataCard = app.substring(app.indexOf("internal fun DataStorageGroupedCard"), app.indexOf("internal fun DataStorageGroupedDivider"))
        val conversationImports = app.substring(app.indexOf("private fun ConversationImportSection"), app.indexOf("private fun WorkspaceExchangeV2ExportCard"))

        assertTrue(settingsGroups.contains("shape = CardShape"))
        assertFalse(settingsGroups.contains("shape = P5AInteractiveShape"))
        assertTrue(dataCard.contains("shape = CardShape"))
        assertTrue(app.contains("SettingsGroupedCardDividerHeight = 4.dp"))
        assertTrue(app.contains("height(SettingsGroupedCardDividerHeight).background(SettingsPageBackground)"))
        assertTrue(conversationImports.contains("actionLabel = \"导入 ChatGPT JSON\"") && conversationImports.contains("actionLabel = \"导入 Claude JSON\""))
        assertTrue(conversationImports.contains("DataStorageGroupedDivider()"))
        for (screen in listOf(chatGpt, claude, zip, backup)) {
            assertTrue(screen.contains("grouped: Boolean = false"))
            assertTrue(screen.contains("DataStorageGroupedActionRow"))
        }
    }

    @Test
    fun `workspace project and knowledge entries share one rounded card`() {
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val workspace = app.substring(
            app.indexOf("private fun WorkspaceSettingsCard"),
            app.indexOf("private fun DevelopmentDiagnosticsSettingsCard"),
        )

        assertTrue(workspace.contains("DataStorageGroupedCard"))
        assertTrue(workspace.contains("label = \"管理 Projects（\${projectState.projects.size}）\"") && workspace.contains("label = \"管理知识库\""))
        assertTrue(workspace.contains("DataStorageGroupedDivider()"))
        assertFalse(workspace.contains("P5AInteractiveShape"))
    }

    @Test
    fun `development diagnostics entries share one rounded card`() {
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val development = app.substring(
            app.indexOf("private fun DevelopmentDiagnosticsSettingsCard"),
            app.indexOf("private fun FeatureReviewSettingsCard"),
        )

        assertTrue(development.contains("DataStorageGroupedCard"))
        assertTrue(development.contains("label = \"本次 Context 控制\"") && development.contains("label = \"离线评测\""))
        assertTrue(development.contains("DataStorageGroupedDivider()"))
        assertFalse(development.contains("OutlinedButton"))
        assertFalse(development.contains("P5AInteractiveShape"))
    }
}

private fun String.countOccurrences(token: String): Int = split(token).size - 1
