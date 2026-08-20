package com.nanzhufeng.ai.ui

import java.io.File
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
    fun v2FeatureReviewKeepsDesktopReexportInSettingsWithoutAdvertisingNativeRestore() {
        val source = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val card = source.substringAfter("private fun FeatureReviewSettingsCard()").substringBefore("@Composable\nprivate fun SettingsCategoryRow")

        listOf(
            "完整工作区交换（v2）",
            "从已提交私有记录经系统保存位置回导",
            "只保留双端设置二级入口",
            "不在聊天主页、Composer 或工作页增加按键",
        ).forEach { token -> assertTrue("missing v2 review detail: $token", card.contains(token)) }
        assertTrue(card.contains("也不表示已恢复为原生对象"))
    }

    @Test
    fun localControlIsSettingsOnlyAndReusesExistingWorkspaceOwners() {
        val source = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val settings = source.substringAfter("private fun SettingsHierarchy(").substringBefore("@Composable\nprivate fun SettingsCategoryList")
        val control = source.substringAfter("private fun LocalControlEntryCard").substringBefore("@Composable\nprivate fun FeatureReviewSettingsCard")
        val hub = source.substringAfter("private fun ControlHub").substringBefore("@Composable\nprivate fun ConversationFoundationCard")
        val review = source.substringAfter("private fun FeatureReviewSettingsCard()").substringBefore("@Composable\nprivate fun SettingsCategoryRow")

        assertTrue(settings.contains("SettingsDestination.LOCAL_CONTROL -> LocalControlEntryCard(onOpenLocalControl)"))
        for (token in listOf("Projects", "知识", "长期 Memory", "不会读取凭据", "不调用 Provider", "不发起外部访问")) assertTrue("missing local-control boundary: $token", control.contains(token))
        for (route in listOf("P5ARoute.PROJECTS", "P5ARoute.KNOWLEDGE", "P5ARoute.MEMORY")) assertTrue("missing reachable route: $route", hub.contains(route))
        for (token in listOf("更多本地控制面", "待您判断保留或删减", "设置二级入口", "不建议在聊天主页、Composer 或会话详情增加按键")) assertTrue("missing feature review: $token", review.contains(token))
    }
}
