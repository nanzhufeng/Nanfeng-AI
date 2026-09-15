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
        assertTrue(privacy.contains("删除 ZIP 原始包"))
        assertTrue(privacy.contains("尚未全部内置"))
        assertTrue(privacy.contains("可以安全删除 ZIP 原始包"))
        assertTrue(privacy.contains("导入资料已内置"))
        assertTrue(privacy.contains("sourceDependentAttachmentCount"))
        assertTrue(privacy.contains("已导入附件"))
        assertTrue(privacy.contains("PrivacySummarySection(\"附件\", attachmentRows)"))
        assertFalse(privacy.contains("ZIP 原始包（本机保留）"))
        assertFalse(privacy.contains("安全诊断 JSON"))
        val privacySummary = privacy.substring(
            privacy.indexOf("internal fun PrivacyStorageSummary"),
            privacy.indexOf("private data class PrivacySummaryRow"),
        )
        for (removed in listOf("本地记录", "模型调用记录", "本地检查记录", "设置与密钥", "API Key", "credentialReferencePresent", "大小包含文本与附件实际字节")) {
            assertFalse("redundant privacy summary remains: $removed", privacySummary.contains(removed))
        }
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

        for (token in listOf("个性化", "启用记忆", "记忆摘要", "提醒", "模型与联网", "项目与知识", "Google 账号与同步", "导入与导出", "数据管理", "本机数据", "关于", "Android 版 \${BuildConfig.VERSION_NAME}", "构建号 \${BuildConfig.VERSION_CODE}", "开发时间 \${formatDevelopmentTime(BuildConfig.BUILD_TIME_EPOCH_SECONDS)}", "开发者信息", "开发者：席瑞", "联系邮箱：nanzhufeng.studio@gmail.com", "源码与更新：GitHub · nanzhufeng/Nanfeng-AI", "版权所有 © 2026 席瑞")) {
            assertTrue("missing reorganized setting $token", app.contains(token))
        }
        assertFalse(app.substring(app.indexOf("private fun SettingsCategoryList"), app.indexOf("private fun SettingsSwitchRow")).contains("更多本地控制面"))
        assertFalse(app.substring(app.indexOf("private fun SettingsCategoryList"), app.indexOf("private fun SettingsSwitchRow")).contains("记忆与上下文"))
        assertTrue(app.contains("你的昵称"))
        assertTrue(app.contains("你的职业"))
        assertTrue(app.contains("自定义指令"))
        assertTrue(app.contains("AssistantExperienceSettings.CUSTOM_INSTRUCTIONS_MAX_LENGTH"))
        assertTrue(app.contains("\${settings.customInstructions.length} / \${com.nanzhufeng.ai.domain.AssistantExperienceSettings.CUSTOM_INSTRUCTIONS_MAX_LENGTH} 字"))
        val personalization = app.substring(app.indexOf("private fun AssistantPersonalizationSettingsCard"), app.indexOf("private fun ConversationStylePreferenceRow"))
        assertTrue(personalization.contains("minLines = 5") && personalization.contains("maxLines = 5"))
        assertTrue(personalization.contains("padding(end = 18.dp, bottom = 5.dp)"))
        assertTrue(personalization.contains("你的 自定义指令 将用于所有南枫AI的对话。"))
        assertTrue(personalization.contains("Icons.Rounded.OpenInFull") && personalization.contains("全屏编辑自定义指令") && personalization.contains("modifier = Modifier.size(14.4.dp)"))
        assertTrue(personalization.contains("CustomInstructionsFullscreenEditor(") && personalization.contains("DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)"))
        assertTrue(app.contains("onSave = onSavePersonalization"))
        assertTrue(personalization.contains("onSave = {\n                onSave()\n                customInstructionsFullscreen = false"))
        assertFalse(personalization.contains("notice?.let { Spacer(Modifier.height(8.dp)); Text(it, color = BrandGreen"))
        assertFalse(app.contains("CenteredPersonalizationSaveNotice"))
        assertFalse(app.contains("assistantExperienceSettingsViewModel::clearNotice"))
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

        val policy = File("src/main/java/com/nanzhufeng/ai/ui/RootCanvasPolicy.kt").readText()
        assertTrue(policy.contains("P5ARoute.OCR -> RootCanvasKind.DOCUMENT"))
        assertTrue(policy.contains("P5ARoute.CAPTURE, P5ARoute.CONVERSATION -> RootCanvasKind.CONVERSATION"))
        assertTrue(policy.contains("else -> RootCanvasKind.SETTINGS"))
        assertTrue(app.contains("RootCanvasKind.SETTINGS -> SettingsPageBackground"))
        assertTrue(app.contains("color = rootCanvasColor"))
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
    fun `style row stays compact while its picker explains five choices on scrollable gray cards`() {
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val styleRow = app.substring(app.indexOf("private fun ConversationStylePreferenceRow"), app.indexOf("private enum class AppearancePicker"))
        val appearanceRow = app.substring(app.indexOf("private fun AppearancePreferenceRow"), app.indexOf("private fun AppearancePickerDialog"))

        for (row in listOf(styleRow.substringBefore("private fun ConversationStylePickerDialog"), appearanceRow)) {
            assertTrue("selector must use the shared pill contour when it is not inside a grouped card", row.contains("shape = P5AInteractiveShape") || row.contains("else P5AInteractiveShape"))
            assertTrue("selector must show its selected value in the row", row.contains("Text(style.definition().label") || row.contains("Text(value,"))
            assertFalse("selector must not retain a dropdown chevron", row.contains("KeyboardArrowDown"))
            assertFalse("selector must not stack title and selected value", row.contains("Column(modifier = Modifier.weight(1f)"))
        }
        val stylePicker = styleRow.substringAfter("private fun ConversationStylePickerDialog")
        for (token in listOf("ConversationStyle.selectable", "definition.summary", "SettingsPageBackground", "BorderStroke(", "verticalScroll(rememberScrollState())", "MaterialTheme.typography.bodyMedium", "Icons.Rounded.Check")) {
            assertTrue("missing conversation-style picker contract: $token", stylePicker.contains(token))
        }
        assertFalse(stylePicker.contains("ConversationStyle.entries"))
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
        }
        assertFalse("model status must not retain the fixed green token", model.contains("BrandGreen"))
        assertFalse("privacy status must not retain the fixed green token", privacy.contains("BrandGreen"))
        assertTrue("the OCR category remains in the shared compact summary", costs.contains("title = \"南枫转写\"") && costs.contains("ConversationCostCategoryMetric(\"次数\""))
        assertTrue(model.contains("统计对话、标题、历史与南枫转写的 Token 与金额"))
        assertTrue(costs.contains("会话标题整理"))
    }

    @Test
    fun `settings home title is centered larger and bold`() {
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val header = app.substringAfter("private fun SettingsPageHeader(").substringBefore("private fun SettingsHierarchy(")
        val home = header.substringAfter("if (destination == SettingsDestination.HOME)").substringBefore("return")

        assertTrue(home.contains("Box(modifier = Modifier.fillMaxWidth().height(48.dp))"))
        assertTrue(home.contains("Modifier.align(Alignment.Center).semantics { heading() }"))
        assertTrue(home.contains("style = MaterialTheme.typography.headlineMedium"))
        assertTrue(home.contains("fontWeight = FontWeight.Bold"))
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
        assertTrue(personalization.contains("当前风格会用于每次普通对话；只改变表达方式，不改变模型、联网、记忆或资料库功能。"))
        assertTrue(personalization.contains("历史资料库"))
        assertTrue(personalization.contains("低频整理有价值的历史对话，并在后续对话优先调用少量相关资料。"))
        assertTrue(personalization.contains("title = \"历史资料库\""))
        assertTrue(personalization.contains("checked = settings.historyLibraryEnabled"))
        assertTrue(personalization.contains("onCheckedChange = { enabled -> onUpdateSwitch"))
        assertTrue(personalization.contains("librarySearchEnabled = enabled"))
        assertTrue(personalization.contains("autoHistoryKnowledgeEnabled = enabled"))
        assertFalse(personalization.contains("autoHistoryCurationConsentVisible"))
        assertFalse(personalization.contains("title = \"自动沉淀历史资料\""))
        assertTrue(personalization.contains("summary = \"\""))
        assertTrue(personalization.contains("Spacer(Modifier.height(6.dp))"))
        assertTrue(personalization.contains("modifier = Modifier.padding(horizontal = 4.dp)"))
        assertTrue(personalization.indexOf("title = \"历史资料库\"") < personalization.indexOf("ConversationStylePreferenceRow("))
        assertFalse(personalization.contains("advancedExpanded"))
        assertFalse(personalization.contains("Text(\"高级\""))
        assertTrue(personalization.contains("Text(\"记忆摘要\", color = BodyText"))
        assertTrue(personalization.contains("查看 南枫AI 对你的了解概览。如果你希望它持续记住某些信息，可以使用下方 自定义指令。"))
        for (token in listOf("color = ForegroundSurface", "shape = P5AInteractiveShape", "heightIn(min = 52.dp)")) {
            assertTrue("memory switch foreground surface must keep the shared pill geometry: $token", switchRow.contains(token))
        }
    }

    @Test
    fun `personalization save only writes editable profile fields and never replays switch state`() {
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val save = app.substring(
            app.indexOf("fun savedWithPersonalizationEditorContent"),
            app.indexOf("val settingsOwnsCurrentRoute"),
        )
        assertTrue(save.contains("displayName = draft.displayName"))
        assertTrue(save.contains("customInstructions = draft.customInstructions"))
        assertTrue(save.contains("conversationStyle = draft.conversationStyle"))
        assertFalse(save.contains("memoryRetrievalEnabled ="))
        assertFalse(save.contains("librarySearchEnabled ="))
        assertTrue(app.contains("onUpdatePersonalizationSwitch = { transform ->"))
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
        assertTrue(backup.contains("workingOperation == LocalBackupOperation.EXPORT"))
        assertTrue(backup.contains("workingOperation == LocalBackupOperation.RESTORE"))
        assertTrue(backup.contains("MaterialTheme.typography.labelSmall"))
        assertTrue(backup.contains("备份完成，已确认保存的文件可以正常打开。"))
        assertTrue(backup.contains("LocalBackupStatusMessage(state, LocalBackupOperation.EXPORT)"))
        assertTrue(backup.contains("LocalBackupStatusMessage(state, LocalBackupOperation.RESTORE)"))
    }

    @Test
    fun `settings content keeps status hints above the system navigation area`() {
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val start = app.indexOf("Column(\n        modifier = Modifier\n            .widthIn(max = 1280.dp)")
        val settings = app.substring(start, app.indexOf("SettingsTextScale {", start))
        assertTrue(settings.contains(".verticalScroll(settingsScrollState)\n            .navigationBarsPadding()"))
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
        val conversationImports = app.substring(app.indexOf("private fun DataImportCenterContent"), app.indexOf("private fun WorkspaceExchangeV2ExportCard"))

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
