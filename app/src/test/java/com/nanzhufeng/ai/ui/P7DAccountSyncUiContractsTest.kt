package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class P7DAccountSyncUiContractsTest {
    @Test fun `offline account state never creates a fake identity or periodic sync`() {
        val state = P7DAccountSyncUiState()
        assertFalse(state.detailVisible)
        assertFalse(state.configured)
        assertFalse(state.working)
        assertNull(state.session)
        assertFalse(state.recoveryReady)
        assertFalse(state.periodicEnabled)
    }

    @Test fun `account page centers its title below system bars and keeps every card full width`() {
        val source = File("src/main/java/com/nanzhufeng/ai/ui/P7DAccountSyncUi.kt").readText()
        assertTrue(source.contains(".verticalScroll(rememberScrollState())\n            .statusBarsPadding()"))
        assertTrue(source.contains("Modifier.align(Alignment.Center).semantics { heading() }"))
        assertTrue(source.contains("IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)"))
        assertTrue(source.split("WhiteCard(Modifier.fillMaxWidth())").size - 1 >= 3)
    }

    @Test fun `credential interruption does not claim that the user cancelled login`() {
        val source = File("src/main/java/com/nanzhufeng/ai/data/P7FGoogleAccountSession.kt").readText()
        assertTrue(source.contains("CancellationException(\"Google 未完成授权，请重试。\")"))
        assertFalse(source.contains("CancellationException(\"已取消登录。\")"))
    }

    @Test fun `signed in account renders the cached Google avatar before its safe refresh`() {
        val source = File("src/main/java/com/nanzhufeng/ai/ui/P7DAccountSyncUi.kt").readText()
        assertTrue(source.contains("P7DGoogleAccountAvatar(state.session, loadAvatar)"))
        assertTrue(source.contains("cache.read(accountRef, avatarUrl)"))
        assertTrue(source.contains("loadAvatar(avatarUrl)"))
        assertTrue(source.contains("contentDescription = \"Google 账号头像\""))
    }

    @Test fun `account switch and sign out use the same grey filled action surface`() {
        val source = File("src/main/java/com/nanzhufeng/ai/ui/P7DAccountSyncUi.kt").readText()
        val management = source.substringAfter("Text(\"账号管理\"").substringBefore("Text(\"对话同步\"")
        assertTrue(management.contains("Text(\"切换 Google 账号\")"))
        assertTrue(management.contains("Text(\"退出登录\")"))
        assertTrue(management.split("border = null").size - 1 == 2)
        assertTrue(management.split("containerColor = SettingsPageBackground").size - 1 == 2)
        assertFalse(management.contains("containerColor = ForegroundSurface"))
    }
}
