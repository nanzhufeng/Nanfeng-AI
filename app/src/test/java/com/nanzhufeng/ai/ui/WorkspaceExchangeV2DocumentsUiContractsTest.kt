package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceExchangeV2DocumentsUiContractsTest {
    private val appSource = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
    private val resultSource = File("src/main/java/com/nanzhufeng/ai/domain/WorkspaceExchangeV2ExportPort.kt").readText()
    private val restorePortSource = File("src/main/java/com/nanzhufeng/ai/data/AndroidWorkspaceExchangeV2OpenDocumentRestorePort.kt").readText()
    private val restoreUiSource = File("src/main/java/com/nanzhufeng/ai/ui/WorkspaceExchangeV2RestoreUi.kt").readText()

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

    @Test
    fun `v2 OpenDocument restore sends only bounded selected bytes to the atomic owner`() {
        val picker = appSource.substring(appSource.indexOf("val workspaceExchangeV2RestorePicker"), appSource.indexOf("val appearance ="))
        assertTrue(picker.contains("ActivityResultContracts.OpenDocument()"))
        assertTrue(picker.contains("workspaceExchangeV2RestoreViewModel::selectedDocument"))
        for (forbidden in listOf("openInputStream", "NfaiExchangeV2PackageReader", "JSONObject", "ZipInputStream")) assertFalse("UI must not parse selected v2 package: $forbidden", picker.contains(forbidden))
        for (required in listOf("NFAI_EXCHANGE_V2_MAX_PACKAGE_BYTES", "readSelectedBytes", "WorkspaceExchangeV2RestoreRequest", "owner.restore")) assertTrue("missing controlled bridge: $required", restorePortSource.contains(required))
        for (forbidden in listOf("JSONObject", "ZipInputStream", "displayName", "lastPathSegment", "openOutputStream")) assertFalse("bridge must not parse or retain: $forbidden", restorePortSource.contains(forbidden))
        val state = restoreUiSource.substring(restoreUiSource.indexOf("data class WorkspaceExchangeV2RestoreUiState"), restoreUiSource.indexOf("class WorkspaceExchangeV2RestoreViewModel"))
        for (forbidden in listOf("Uri?", "displayName", "packageBytes", "exchangeJson", "assets", "Throwable")) assertFalse("UI state must stay content-free: $forbidden", state.contains(forbidden))
    }
}
