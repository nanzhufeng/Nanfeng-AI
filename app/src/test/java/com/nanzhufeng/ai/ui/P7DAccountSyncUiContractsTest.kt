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

    @Test fun `account sync failures name the actionable state instead of a generic warning`() {
        val source = File("src/main/java/com/nanzhufeng/ai/data/P7FManualConversationSync.kt").readText()
        assertTrue(source.contains("syncStateActionMessage(metadata)"))
        assertTrue(source.contains("云端已有不同版本，请先读取云端列表并处理冲突。"))
        assertTrue(source.contains("KEY_MATERIAL_UNAVAILABLE"))
        assertFalse(source.contains("请使用恢复码"))
        assertTrue(source.contains("账号状态正在更新，请重新登录后重试。"))
        assertFalse(source.contains("直接同步"))
        assertFalse(source.contains("账号同步状态需要先处理。"))
    }

    @Test fun `reopening account sync clears stale action text and reloads durable state`() {
        val source = File("src/main/java/com/nanzhufeng/ai/ui/P7DAccountSyncUi.kt").readText()
        val open = source.substringAfter("fun open() {").substringBefore("fun close()")
        assertTrue(open.contains("state = loadState().copy(detailVisible = true)"))
        assertFalse(open.contains("state.notice"))
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

    @Test fun `periodic sync status shares its compact two-line row with the switch on phone`() {
        val source = File("src/main/java/com/nanzhufeng/ai/ui/P7DAccountSyncUi.kt").readText()
        val syncCard = source.substringAfter("Text(\"对话同步\"").substringBefore("if (state.session != null) WhiteCard")
        assertTrue(syncCard.contains("Column(modifier = Modifier.weight(1f))"))
        assertTrue(syncCard.contains("Text(\"定期同步\""))
        assertTrue(syncCard.contains("state.lastSyncedAtEpochMs?.let {\n                        Text(\"上次同步"))
        assertTrue(syncCard.indexOf("state.lastSyncedAtEpochMs?.let") < syncCard.indexOf("SettingsSwitch("))
        assertFalse(syncCard.contains("}\n            state.lastSyncedAtEpochMs"))
    }

    @Test fun `account page uses login only without recovery ceremony`() {
        val source = File("src/main/java/com/nanzhufeng/ai/ui/P7DAccountSyncUi.kt").readText()
        assertFalse(source.contains("fun prepareRecoveryProtection"))
        assertFalse(source.contains("启用端到端加密同步"))
        assertFalse(source.contains("PasswordVisualTransformation()"))
        assertFalse(source.contains("使用当前 Google 账号同步，传输由 HTTPS 保护。"))
        assertTrue(source.contains("Text(\"读取云端列表\""))
    }

    @Test fun `cloud read immediately restores ordinary conversations without leaving its current surface`() {
        val ui = File("src/main/java/com/nanzhufeng/ai/ui/P7DAccountSyncUi.kt").readText()
        val cloudRead = ui.substringAfter("fun readCloudDocuments() {").substringBefore("fun signOut()")
        val owner = File("src/main/java/com/nanzhufeng/ai/data/P7FManualConversationSync.kt").readText()
        assertFalse(cloudRead.contains("manualSync.migratePreviouslySelectedLegacyConversations()"))
        assertTrue(owner.contains("fun migratePreviouslySelectedLegacyConversations()"))
        assertTrue(owner.contains("ledger?.remoteRevision == remote.revision"))
        assertTrue(owner.contains("ledger.payloadHash == remote.payloadHash"))
        assertTrue(owner.contains("ledger.localContentHash == localContentHash"))
        assertTrue(owner.contains("readCloudListPresentation(session, remote)"))
        assertTrue(owner.contains("restoreRemoteConversationInternal(header.documentId, remote, session.userId)"))
        assertTrue(owner.contains("The server list is ordered by the shared cloud update order"))
        assertTrue(owner.contains("if (conversations.findById(conversationId) == null) continue"))
        assertTrue(ui.contains("manualSync.restoreAllRemoteConversations()"))
        assertFalse(cloudRead.contains("detailVisible = false"))
        assertFalse(cloudRead.contains("detailVisible = true"))
        assertTrue(ui.contains("新增 \${result.restoredCount} 个，已更新 \${result.updatedCount} 个"))
        assertFalse(ui.contains("选择一条云端对话恢复"))
        assertFalse(ui.contains("恢复云端对话"))
        assertTrue(owner.contains("fun restoreAllRemoteConversations()"))
        assertTrue(owner.contains("existing == null -> runCatching { conversations.save(restored) }"))
        assertTrue(owner.contains("manualConversationSyncStateDao().save("))
        assertTrue(owner.contains("val remoteContentHash = (remoteConversationRecord.contentJson"))
        assertFalse(owner.contains("preparedSnapshot(local, documentId, remote.revision)"))
        assertTrue(owner.contains("subsequent mobile"))
        assertTrue(owner.contains("summarizeRemoteConversationRestores"))
        assertTrue(owner.contains("P7FCloudConversationBatchRestoreResult.PartiallyRestored"))
        assertTrue(owner.contains("mergeNewerRemoteConversation"))
        assertFalse(owner.contains("P7FExistingConversationMerge.Conflict"))
        assertFalse(owner.contains("云端尚未支持完整传输，未同步。"))
        assertTrue(ui.contains("新增 \${result.restoredCount} 个，已更新 \${result.updatedCount} 个，已有 \${result.alreadyPresentCount} 个；\${result.rejectedCount} 个未读入：\${result.rejectedSummary}"))
    }

    @Test fun `drawer cloud read keeps the drawer visible and presents its result in place`() {
        val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val drawerRead = workspace.substringAfter("onReadCloud = {").substringBefore("},\n                    )")
        assertTrue(drawerRead.contains("cloudListVisible = true"))
        assertTrue(drawerRead.contains("onReadCloudConversations()"))
        assertTrue(workspace.contains("LaunchedEffect(syncNotice, syncCompletedFeedback)"))
        assertTrue(workspace.contains("Toast.makeText(context, it, Toast.LENGTH_LONG).show()"))
    }

    @Test fun `sync and cloud read show immediate progress then sustained in-place feedback`() {
        val account = File("src/main/java/com/nanzhufeng/ai/ui/P7DAccountSyncUi.kt").readText()
        val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val cloudRead = account.substringAfter("fun readCloudDocuments() {").substringBefore("fun signOut()")
        val directSync = account.substringAfter("fun requestConversationSync(conversation: Conversation) {").substringBefore("fun cancelConversationSync")
        assertTrue(account.contains("enum class P7DAccountSyncOperation"))
        assertTrue(cloudRead.contains("activeOperation = P7DAccountSyncOperation.READING_CLOUD_LIST"))
        assertTrue(directSync.contains("activeOperation = P7DAccountSyncOperation.SYNCING_CONVERSATION"))
        assertTrue(cloudRead.contains("delay(P7D_ACCOUNT_SYNC_PROGRESS_FIRST_FRAME_DELAY_MS)"))
        assertTrue(directSync.contains("delay(P7D_ACCOUNT_SYNC_PROGRESS_FIRST_FRAME_DELAY_MS)"))
        assertTrue(account.contains("P7D_ACCOUNT_SYNC_PROGRESS_MIN_VISIBLE_MS = 520L"))
        assertTrue(account.contains("awaitP7DAccountSyncProgressPresentation(progressStartedAtElapsedMs)"))
        assertTrue(cloudRead.contains("finishOperation("))
        assertTrue(directSync.contains("finishOperation("))
        assertTrue(account.contains("fun P7DAccountSyncProgressDialog"))
        assertTrue(account.contains("CircularProgressIndicator"))
        assertTrue(account.contains("usePlatformDefaultWidth = false"))
        assertTrue(account.contains("widthIn(max = 340.dp)"))
        assertTrue(account.contains("import androidx.compose.ui.window.Dialog"))
        assertTrue(account.contains("LaunchedEffect(state.notice, state.completedOperationFeedback)"))
        assertTrue(account.contains("Toast.makeText(context, it, Toast.LENGTH_LONG).show()"))
        assertTrue(app.contains("syncOperation = accountSyncViewModel.state.activeOperation"))
        assertTrue(app.contains("syncCompletedFeedback = accountSyncViewModel.state.completedOperationFeedback"))
        assertTrue(workspace.contains("syncOperation: P7DAccountSyncOperation?"))
        assertTrue(workspace.contains("syncCompletedFeedback: P7DAccountSyncFeedback?"))
        assertTrue(workspace.contains("P7DAccountSyncProgressDialog(syncOperation, syncCompletedFeedback)"))
        assertTrue(account.contains("\"同步成功\""))
        assertTrue(cloudRead.contains("cloudReadFeedback(result)"))
    }

    @Test fun `completed cloud feedback uses the foreground surface without tonal tint and never falls through to a toast`() {
        val account = File("src/main/java/com/nanzhufeng/ai/ui/P7DAccountSyncUi.kt").readText()
        val dialog = account.substringAfter("internal fun P7DAccountSyncProgressDialog(").substringBefore("private fun P7DNoDimProgressDialogEffect")
        val completion = account.substringAfter("private fun finishOperation(").substringBefore("init { refreshCloudConversationProjection() }")
        assertTrue(dialog.contains("color = ForegroundSurface"))
        assertTrue(dialog.contains("contentColor = BodyText"))
        assertTrue(dialog.contains("tonalElevation = 0.dp"))
        assertTrue(dialog.contains("color = SecondaryText"))
        assertTrue(completion.contains("state = state.copy(completedOperationFeedback = null, notice = null)"))
    }

    @Test fun `a cloud-bound conversation presents cancel sync while preserving local history`() {
        val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val account = File("src/main/java/com/nanzhufeng/ai/ui/P7DAccountSyncUi.kt").readText()
        assertTrue(workspace.contains("if (isSyncedToCloud) \"取消同步\" else \"同步到南枫云\""))
        assertTrue(workspace.contains("onCancelConversationSync(conversation)"))
        assertTrue(account.contains("fun cancelConversationSync(conversation: Conversation)"))
        assertTrue(account.contains("本地对话保留"))
    }

    @Test fun `every ordinary conversation action menu keeps rename after favorite`() {
        val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val menu = workspace.substringAfter("private fun ConversationActionSheet").substringBefore("private fun ConversationMenuAction")
        assertFalse(menu.contains("includeRename"))
        assertTrue(menu.contains("workMode -> 12"))
        assertTrue(menu.contains("else -> 9"))
        val workMenu = menu.substringAfter("if (workMode) {").substringBefore("} else {")
        val standardMenu = menu.substringAfter("} else {").substringBefore("}\n                }\n            }")
        assertTrue(workMenu.indexOf("Icons.Rounded.Bookmark") < workMenu.indexOf("Icons.Rounded.Edit, \"重命名\""))
        assertTrue(workMenu.indexOf("Icons.Rounded.Edit, \"重命名\"") < workMenu.indexOf("Icons.Rounded.Share"))
        assertTrue(standardMenu.indexOf("Icons.Rounded.Bookmark") < standardMenu.indexOf("Icons.Rounded.Edit, \"重命名\""))
        assertTrue(standardMenu.indexOf("Icons.Rounded.Edit, \"重命名\"") < standardMenu.indexOf("Icons.Rounded.Share"))
    }

    @Test fun `local synced conversations show a cloud marker before their title`() {
        val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        assertTrue(workspace.contains("syncedToCloud = !cloudListVisible && conversation.id.value in syncedConversationIds"))
        assertTrue(workspace.contains("if (syncedToCloud)"))
        assertTrue(workspace.contains("NanfengCloudDoneIcon("))
        assertTrue(workspace.contains("contentDescription = \"已同步到云端\""))
        val account = File("src/main/java/com/nanzhufeng/ai/ui/P7DAccountSyncUi.kt").readText()
        assertTrue(account.contains("fun NanfengCloudDoneIcon"))
        assertTrue(account.contains("Stroke(width = 1.7f * unit"))
    }

    @Test fun `cloud list pinning is account isolated presentation and never mutates local pinning`() {
        val owner = File("src/main/java/com/nanzhufeng/ai/data/P7FManualConversationSync.kt").readText()
        val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val accountUi = File("src/main/java/com/nanzhufeng/ai/ui/P7DAccountSyncUi.kt").readText()
        val database = File("src/main/java/com/nanzhufeng/ai/data/local/NanfengAiDatabase.kt").readText()
        assertTrue(owner.contains("fun setCloudConversationPinned"))
        assertTrue(owner.contains("CloudConversationPresentationEntity"))
        assertTrue(database.contains("cloud_conversation_presentation"))
        assertTrue(database.contains("MIGRATION_66_67"))
        assertTrue(workspace.contains("if (cloudListVisible) conversation.id.value in cloudPinnedConversationIds else conversation.pinnedAt != null"))
        assertTrue(workspace.contains("onToggleCloudPinned(!cloudPinned)"))
        assertTrue(accountUi.contains("云端列表已置顶，本地列表未改动。"))
    }

    @Test fun `phone drawer centers local and cloud lists and opens cloud projection after read`() {
        val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        assertTrue(workspace.contains("DrawerListModeButton(\"本地\""))
        assertTrue(workspace.contains("DrawerListModeButton(\"云端\""))
        assertTrue(workspace.contains("Modifier.align(Alignment.Center).fillMaxWidth(0.3333f).height(26.dp)"))
        assertTrue(workspace.contains(".size(26.dp)\n            .conversationForegroundShadow(CircleShape)"))
        assertTrue(workspace.contains("Box(Modifier.fillMaxWidth().height(30.dp))"))
        assertTrue(workspace.contains("contentDescription = \"读取云端列表\""))
        assertTrue(workspace.contains(".filter { it.id.value in syncedConversationIds }"))
        assertTrue(workspace.contains("projection must not inherit that ordering: its only grouping"))
        assertTrue(workspace.contains("sortedWith(compareByDescending<com.nanzhufeng.ai.domain.Conversation> { it.updatedAt }.thenBy { it.id.value })"))
        assertTrue(workspace.contains("item(key = \"drawer-list-mode\""))
        assertTrue(workspace.contains("Text(\"最近\", style = MaterialTheme.typography.labelSmall"))
        assertTrue(workspace.contains("cloudListVisible = true\n                            onReadCloudConversations()"))
    }

    @Test fun `phone cloud switch and utilities use the shared soft foreground shadow`() {
        val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val controls = workspace.substringAfter("private fun DrawerNavigationListControls(").substringBefore("private fun DrawerRoundIconButton(")
        val utilities = workspace.substringAfter("private fun DrawerRoundIconButton(").substringBefore("private fun ConversationBatchEditControls(")
        assertTrue(controls.contains("val modeShape = RoundedCornerShape(50)"))
        assertTrue(controls.contains(".conversationForegroundShadow(modeShape)"))
        assertTrue(controls.contains("shadowElevation = 0.dp"))
        assertFalse(controls.contains("shadowElevation = 3.dp"))
        assertTrue(utilities.contains(".conversationForegroundShadow(CircleShape)"))
        assertTrue(utilities.contains("shadowElevation = 0.dp"))
        assertFalse(utilities.contains("shadowElevation = 2.dp"))
        assertTrue(controls.contains("tint = if (batchEditing) AccentOrange else BodyText"))
        assertTrue(utilities.contains(".clickable(enabled = enabled, onClick = onClick)"))
        assertFalse(utilities.contains("Surface(\n        onClick = onClick,"))
    }

    @Test fun `phone batch editor uses a two row checklist rather than the rename pen`() {
        val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val controls = workspace.substringAfter("private fun DrawerNavigationListControls(").substringBefore("private fun DrawerRoundIconButton(")
        assertTrue(controls.contains("icon = null"))
        assertFalse(controls.contains("icon = Icons.Rounded.Edit"))
        assertTrue(controls.contains("\"批量编辑对话\""))
        val account = File("src/main/java/com/nanzhufeng/ai/ui/P7DAccountSyncUi.kt").readText()
        assertTrue(account.contains("fun NanfengBatchEditIcon"))
        assertTrue(account.contains("lineTo(21f * unit, 14f * unit)"))
        assertFalse(account.contains("lineTo(21f * unit, 18f * unit)"))
    }

    @Test fun `sync acknowledgement keeps an in-place result card and only uses toast for unrelated notices`() {
        val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        assertTrue(workspace.contains("LaunchedEffect(syncNotice, syncCompletedFeedback)"))
        assertTrue(workspace.contains("Toast.makeText(context, it, Toast.LENGTH_LONG).show()"))
        assertTrue(workspace.contains("P7DAccountSyncProgressDialog(syncOperation, syncCompletedFeedback)"))
        assertFalse(app.contains("android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()"))
    }
}
