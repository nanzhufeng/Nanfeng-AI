package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceExchangeV2DocumentsUiContractsTest {
    private val appSource = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
    private val resultSource = File("src/main/java/com/nanzhufeng/ai/domain/WorkspaceExchangeV2ExportPort.kt").readText()

    @Test
    fun `v2 DocumentsUI keeps ZIP MIME, canonical suggested name, and content free receipt`() {
        assertTrue(appSource.contains("internal const val WORKSPACE_EXCHANGE_V2_DOCUMENT_MIME = \"application/zip\""))
        assertTrue(appSource.contains("internal const val WORKSPACE_EXCHANGE_V2_SUGGESTED_DISPLAY_NAME = \"nanfeng-ai-workspace-v2.nfai-exchange\""))
        assertTrue(appSource.contains("ActivityResultContracts.CreateDocument(WORKSPACE_EXCHANGE_V2_DOCUMENT_MIME)"))
        assertTrue(appSource.contains("workspaceExchangeV2ExportPicker.launch(WORKSPACE_EXCHANGE_V2_SUGGESTED_DISPLAY_NAME)"))
        for (field in listOf("packageHash", "semanticHash", "objectCount", "attachmentCount")) assertTrue(resultSource.contains("val $field"))
        val receipt = resultSource.substring(resultSource.indexOf("data class Exported"), resultSource.indexOf("data class Rejected"))
        for (forbidden in listOf("displayName", "mimeType", "destination", "Uri")) assertFalse("receipt must not expose $forbidden", receipt.contains(forbidden))
    }
}
