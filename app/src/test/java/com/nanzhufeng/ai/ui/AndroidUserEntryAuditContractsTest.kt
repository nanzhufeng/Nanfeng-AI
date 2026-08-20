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
}
