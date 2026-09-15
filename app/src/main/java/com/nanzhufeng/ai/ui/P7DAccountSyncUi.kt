package com.nanzhufeng.ai.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nanzhufeng.ai.data.*
import com.nanzhufeng.ai.domain.Conversation
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class P7DAccountSyncOperation(
    val progressLabel: String,
    val progressDetail: String,
) {
    SYNCING_CONVERSATION("正在同步到南枫云", "正在加密提交并核对云端回执，请稍候。"),
    READING_CLOUD_LIST("正在读取云端列表", "正在核对并合并云端会话，请稍候。"),
    CANCELLING_CONVERSATION("正在取消云端同步", "正在移除云端副本，本地对话会保留。"),
}

enum class P7DAccountSyncFeedbackKind { SUCCESS, ATTENTION, ERROR }

data class P7DAccountSyncFeedback(
    val operation: P7DAccountSyncOperation,
    val kind: P7DAccountSyncFeedbackKind,
    val title: String,
    val detail: String,
)

private const val P7D_ACCOUNT_SYNC_PROGRESS_FIRST_FRAME_DELAY_MS = 48L
private const val P7D_ACCOUNT_SYNC_PROGRESS_MIN_VISIBLE_MS = 520L
private const val P7D_ACCOUNT_SYNC_RESULT_VISIBLE_MS = 2_600L

private suspend fun awaitP7DAccountSyncProgressPresentation(startedAtElapsedMs: Long) {
    val remaining = P7D_ACCOUNT_SYNC_PROGRESS_MIN_VISIBLE_MS -
        (SystemClock.elapsedRealtime() - startedAtElapsedMs)
    if (remaining > 0L) delay(remaining)
}

/** Shared cloud acknowledgement: keep the cloud, but reduce the check to a quiet state cue. */
@Composable
internal fun NanfengCloudDoneIcon(
    tint: Color,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.semantics { if (contentDescription != null) this.contentDescription = contentDescription }) {
        val unit = size.minDimension / 24f
        val cloud = Path().apply {
            moveTo(19.35f * unit, 10.04f * unit)
            cubicTo(18.67f * unit, 6.59f * unit, 15.64f * unit, 4f * unit, 12f * unit, 4f * unit)
            cubicTo(9.11f * unit, 4f * unit, 6.6f * unit, 5.64f * unit, 5.35f * unit, 8.04f * unit)
            cubicTo(2.34f * unit, 8.36f * unit, 0f, 10.91f * unit, 0f, 14f * unit)
            cubicTo(0f, 17.31f * unit, 2.69f * unit, 20f * unit, 6f * unit, 20f * unit)
            lineTo(19f * unit, 20f * unit)
            cubicTo(21.76f * unit, 20f * unit, 24f * unit, 17.76f * unit, 24f * unit, 15f * unit)
            cubicTo(24f * unit, 12.36f * unit, 21.95f * unit, 10.22f * unit, 19.35f * unit, 10.04f * unit)
            close()
        }
        drawPath(cloud, color = tint)
        val check = Path().apply {
            moveTo(9.1f * unit, 13.7f * unit)
            lineTo(11.25f * unit, 15.85f * unit)
            lineTo(15f * unit, 12.1f * unit)
        }
        drawPath(check, color = Color.White, style = Stroke(width = 1.7f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Batch edit is a two-item checklist: two checks must always have two matching rows. */
@Composable
internal fun NanfengBatchEditIcon(
    tint: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.semantics { this.contentDescription = contentDescription }) {
        val unit = size.minDimension / 24f
        val paths = Path().apply {
            moveTo(3f * unit, 6f * unit)
            lineTo(5f * unit, 8f * unit)
            lineTo(9f * unit, 4f * unit)
            moveTo(3f * unit, 14f * unit)
            lineTo(5f * unit, 16f * unit)
            lineTo(9f * unit, 12f * unit)
            moveTo(13f * unit, 6f * unit)
            lineTo(21f * unit, 6f * unit)
            moveTo(13f * unit, 14f * unit)
            lineTo(21f * unit, 14f * unit)
        }
        drawPath(paths, color = tint, style = Stroke(width = 1.8f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

data class P7DAccountSyncUiState(
    val detailVisible: Boolean = false,
    val configured: Boolean = false,
    val session: P7FCloudSession? = null,
    val working: Boolean = false,
    val activeOperation: P7DAccountSyncOperation? = null,
    val completedOperationFeedback: P7DAccountSyncFeedback? = null,
    val notice: String? = null,
    val lastSyncedAtEpochMs: Long? = null,
    val recoveryReady: Boolean = false,
    val periodicEnabled: Boolean = false,
    val syncedConversationIds: Set<String> = emptySet(),
    val cloudConversations: List<com.nanzhufeng.ai.domain.Conversation> = emptyList(),
    val cloudPinnedConversationIds: Set<String> = emptySet(),
)

/** Opening the page is presentation-only. Network work starts only from an explicit button. */
class P7DAccountSyncViewModel(
    private val accountOwner: P7FGoogleAccountOwner,
    private val manualSync: P7FManualConversationSyncOwner,
    private val scheduler: P7FSelectedConversationSyncScheduler,
    private val onCloudProjectionRefreshed: () -> Unit = {},
) : ViewModel() {
    var state by mutableStateOf(loadState())
        private set

    private fun finishOperation(
        operation: P7DAccountSyncOperation,
        kind: P7DAccountSyncFeedbackKind,
        title: String,
        detail: String,
        update: (P7DAccountSyncUiState) -> P7DAccountSyncUiState = { it },
    ) {
        val feedback = P7DAccountSyncFeedback(operation, kind, title, detail)
        state = update(state).copy(
            working = false,
            activeOperation = null,
            completedOperationFeedback = feedback,
            notice = if (detail.isBlank()) title else "$title：$detail",
        )
        viewModelScope.launch {
            delay(P7D_ACCOUNT_SYNC_RESULT_VISIBLE_MS)
            if (state.completedOperationFeedback === feedback) {
                // The centered feedback card is the sole completion acknowledgement.
                // Clearing its paired notice prevents the workspace's legacy Toast
                // bridge from replaying the same result at the bottom afterward.
                state = state.copy(completedOperationFeedback = null, notice = null)
            }
        }
    }

    init { refreshCloudConversationProjection() }

    fun open() {
        // A prior rejected action is not current account state. Reopening must
        // project the durable account metadata instead of preserving stale red text.
        state = loadState().copy(detailVisible = true)
        refreshLatestSync()
        refreshCloudConversationProjection()
    }
    fun close() { state = state.copy(detailVisible = false, notice = null) }
    fun signIn(activityContext: Context) = runAccountAction(activityContext, switch = false)
    fun switchAccount(activityContext: Context) = runAccountAction(activityContext, switch = true)
    suspend fun loadAvatar(avatarUrl: String): ByteArray? = accountOwner.loadGoogleAvatar(avatarUrl)

    fun readCloudDocuments() {
        if (state.working || state.session == null) return
        val userId = state.session?.userId
        val progressStartedAtElapsedMs = SystemClock.elapsedRealtime()
        state = state.copy(
            working = true,
            activeOperation = P7DAccountSyncOperation.READING_CLOUD_LIST,
            completedOperationFeedback = null,
            notice = null,
        )
        viewModelScope.launch {
            delay(P7D_ACCOUNT_SYNC_PROGRESS_FIRST_FRAME_DELAY_MS)
            try {
                // Cloud reads do not perform migration uploads. Explicit
                // selected sync owns those writes and any write conflicts.
                val result = withContext(Dispatchers.IO) { manualSync.restoreAllRemoteConversations() }
                // Keep the on-device acceptance signal actionable without ever
                // logging a title, document id, payload, or account identifier.
                Log.i("NanfengCloudSync", "cloud-list-read outcome=" + when (result) {
                    is P7FCloudConversationBatchRestoreResult.Restored -> "restored:${result.restoredCount}:${result.updatedCount}"
                    is P7FCloudConversationBatchRestoreResult.PartiallyRestored -> "partial:${result.restoredCount}:${result.updatedCount}:${result.alreadyPresentCount}:${result.rejectedCount}"
                    is P7FCloudConversationBatchRestoreResult.Empty -> "empty:${result.ignoredCount}"
                    is P7FCloudConversationBatchRestoreResult.Rejected -> "rejected"
                })
                awaitP7DAccountSyncProgressPresentation(progressStartedAtElapsedMs)
                if (state.session?.userId == userId) when (result) {
                    is P7FCloudConversationBatchRestoreResult.Restored -> finishOperation(
                        P7DAccountSyncOperation.READING_CLOUD_LIST,
                        P7DAccountSyncFeedbackKind.SUCCESS,
                        "读取完成",
                        "新增 ${result.restoredCount} 个，已更新 ${result.updatedCount} 个",
                    )
                    is P7FCloudConversationBatchRestoreResult.PartiallyRestored -> finishOperation(
                        P7DAccountSyncOperation.READING_CLOUD_LIST,
                        P7DAccountSyncFeedbackKind.ATTENTION,
                        "读取完成",
                        "新增 ${result.restoredCount} 个，已更新 ${result.updatedCount} 个，已有 ${result.alreadyPresentCount} 个；${result.rejectedCount} 个未读入：${result.rejectedSummary}",
                    )
                    is P7FCloudConversationBatchRestoreResult.Empty -> finishOperation(
                        P7DAccountSyncOperation.READING_CLOUD_LIST,
                        P7DAccountSyncFeedbackKind.SUCCESS,
                        "读取完成",
                        if (result.ignoredCount > 0) "没有新增对话，已有 ${result.ignoredCount} 个" else "当前没有可恢复的对话",
                    )
                    is P7FCloudConversationBatchRestoreResult.Rejected -> finishOperation(
                        P7DAccountSyncOperation.READING_CLOUD_LIST,
                        P7DAccountSyncFeedbackKind.ERROR,
                        "读取失败",
                        result.message,
                    )
                }
                refreshCloudConversationProjection()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val notice = when (error.message) {
                    "REMOTE_401", "REMOTE_403", "SIGNED_OUT" -> "登录已失效，请重新登录后再读取。"
                    "REMOTE_404" -> "云端列表服务暂不可用，请稍后重试。"
                    "REMOTE_429" -> "读取太频繁，请稍后重试。"
                    "REMOTE_DOCUMENT_INVALID" -> "云端返回的数据无法校验，未导入任何内容。"
                    else -> "云端列表读取失败，请重试。"
                }
                awaitP7DAccountSyncProgressPresentation(progressStartedAtElapsedMs)
                if (state.session?.userId == userId) finishOperation(
                    P7DAccountSyncOperation.READING_CLOUD_LIST,
                    P7DAccountSyncFeedbackKind.ERROR,
                    "读取失败",
                    notice,
                )
            }
        }
    }

    fun signOut() {
        if (state.working) return
        val prior = state.session
        state = state.copy(working = true, notice = null)
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    manualSync.onSignedOut(prior)
                    scheduler.pauseForSignOut()
                    accountOwner.signOut()
                }
            }
            state = loadState().copy(detailVisible = true, notice = "已退出，本机对话保留。")
        }
    }

    fun requestConversationSync(conversation: Conversation) {
        when {
            !state.configured -> state = state.copy(detailVisible = true, notice = "Google 登录与云端服务尚未配置。")
            state.session == null -> state = state.copy(detailVisible = true, notice = "请先登录 Google 账号。")
            !state.recoveryReady -> state = state.copy(detailVisible = true, notice = "请先完成恢复保护。")
            else -> {
                val progressStartedAtElapsedMs = SystemClock.elapsedRealtime()
                state = state.copy(
                    working = true,
                    activeOperation = P7DAccountSyncOperation.SYNCING_CONVERSATION,
                    completedOperationFeedback = null,
                    notice = null,
                )
                viewModelScope.launch {
                    delay(P7D_ACCOUNT_SYNC_PROGRESS_FIRST_FRAME_DELAY_MS)
                    try {
                        val result = withContext(Dispatchers.IO) {
                            val preview = manualSync.preview(conversation.id)
                            if (preview.eligible) manualSync.sync(conversation.id)
                            else P7FManualConversationSyncResult.Rejected(preview.reason ?: "该对话暂时无法同步。")
                        }
                        awaitP7DAccountSyncProgressPresentation(progressStartedAtElapsedMs)
                        when (result) {
                            is P7FManualConversationSyncResult.Synced -> finishOperation(
                                P7DAccountSyncOperation.SYNCING_CONVERSATION,
                                P7DAccountSyncFeedbackKind.SUCCESS,
                                "同步成功",
                                "",
                            ) { it.copy(lastSyncedAtEpochMs = result.syncedAtEpochMs) }
                            is P7FManualConversationSyncResult.Rejected -> finishOperation(
                                P7DAccountSyncOperation.SYNCING_CONVERSATION,
                                P7DAccountSyncFeedbackKind.ERROR,
                                "同步失败",
                                result.message,
                            )
                        }
                        if (result is P7FManualConversationSyncResult.Synced) refreshCloudConversationProjection()
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        awaitP7DAccountSyncProgressPresentation(progressStartedAtElapsedMs)
                        finishOperation(
                            P7DAccountSyncOperation.SYNCING_CONVERSATION,
                            P7DAccountSyncFeedbackKind.ERROR,
                            "同步失败",
                            "请检查网络或登录后重试",
                        )
                    }
                }
            }
        }
    }

    fun cancelConversationSync(conversation: Conversation) {
        when {
            !state.configured -> state = state.copy(detailVisible = true, notice = "Google 登录与云端服务尚未配置。")
            state.session == null -> state = state.copy(detailVisible = true, notice = "请先登录 Google 账号。")
            state.working -> Unit
            else -> {
                val progressStartedAtElapsedMs = SystemClock.elapsedRealtime()
                state = state.copy(
                    working = true,
                    activeOperation = P7DAccountSyncOperation.CANCELLING_CONVERSATION,
                    completedOperationFeedback = null,
                    notice = null,
                )
                viewModelScope.launch {
                    delay(P7D_ACCOUNT_SYNC_PROGRESS_FIRST_FRAME_DELAY_MS)
                    try {
                        val result = withContext(Dispatchers.IO) { manualSync.cancelSync(conversation.id) }
                        awaitP7DAccountSyncProgressPresentation(progressStartedAtElapsedMs)
                        when (result) {
                            is P7FManualConversationCancelResult.Cancelled -> finishOperation(
                                P7DAccountSyncOperation.CANCELLING_CONVERSATION,
                                P7DAccountSyncFeedbackKind.SUCCESS,
                                "已取消同步",
                                "本地对话保留",
                            )
                            is P7FManualConversationCancelResult.Rejected -> finishOperation(
                                P7DAccountSyncOperation.CANCELLING_CONVERSATION,
                                P7DAccountSyncFeedbackKind.ERROR,
                                "取消同步失败",
                                result.message,
                            )
                        }
                        if (result is P7FManualConversationCancelResult.Cancelled) refreshCloudConversationProjection()
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        awaitP7DAccountSyncProgressPresentation(progressStartedAtElapsedMs)
                        finishOperation(
                            P7DAccountSyncOperation.CANCELLING_CONVERSATION,
                            P7DAccountSyncFeedbackKind.ERROR,
                            "取消同步失败",
                            "请检查网络或登录后重试",
                        )
                    }
                }
            }
        }
    }

    fun setPeriodicEnabled(enabled: Boolean) {
        if (state.session == null || state.working) return
        runCatching { scheduler.setEnabled(enabled) }
            .onSuccess { state = state.copy(periodicEnabled = enabled, notice = if (enabled) "定期同步已启用。" else "定期同步已关闭。") }
            .onFailure { state = state.copy(notice = "定期同步设置保存失败。") }
    }

    fun prepareRecoveryProtection(recoveryCode: String, recoveryCodeSaved: Boolean) {
        if (state.working || state.session == null) return
        val secret = recoveryCode.toCharArray()
        state = state.copy(working = true, notice = null)
        viewModelScope.launch {
            val result = try {
                withContext(Dispatchers.IO) { manualSync.prepareRecoveryProtection(secret, recoveryCodeSaved) }
            } finally {
                secret.fill('\u0000')
            }
            state = result.fold(
                onSuccess = {
                    if (scheduler.enabled()) scheduler.resumeIfEnabled()
                    loadState().copy(detailVisible = true, notice = "恢复保护已启用。")
                },
                onFailure = { state.copy(working = false, notice = it.message ?: "无法启用恢复保护。") },
            )
            if (result.isSuccess) refreshLatestSync()
        }
    }

    private fun runAccountAction(activityContext: Context, switch: Boolean) {
        if (state.working || !state.configured) return
        state = state.copy(working = true, notice = null)
        viewModelScope.launch {
            val prior = state.session
            val result = if (switch) {
                withContext(Dispatchers.IO) {
                    manualSync.onSignedOut(prior)
                    scheduler.pauseForSignOut()
                    accountOwner.switchAccount(activityContext)
                }
            } else accountOwner.signIn(activityContext)
            state = result.fold(
                onSuccess = { session ->
                    withContext(Dispatchers.IO) { manualSync.onAuthenticated(session) }
                    scheduler.resumeIfEnabled()
                    loadState().copy(detailVisible = true, notice = "已登录 Google 账号。")
                },
                onFailure = { error -> loadState().copy(detailVisible = true, notice = error.message ?: "登录失败。") },
            )
            if (result.isSuccess) {
                refreshLatestSync()
                refreshCloudConversationProjection()
            }
        }
    }

    private fun loadState(): P7DAccountSyncUiState {
        val session = accountOwner.cachedSession()
        return P7DAccountSyncUiState(
            configured = accountOwner.configured,
            session = session,
            recoveryReady = session?.let { accountOwner.recoveryReady(it.userId) } == true,
            periodicEnabled = scheduler.enabled(),
        )
    }

    private fun refreshLatestSync() {
        val session = state.session ?: return
        viewModelScope.launch {
            val latest = withContext(Dispatchers.IO) { manualSync.latestSync(session)?.lastSyncedAtEpochMs }
            if (state.session?.userId == session.userId) state = state.copy(lastSyncedAtEpochMs = latest)
        }
    }

    fun setCloudConversationPinned(conversation: Conversation, pinned: Boolean) {
        val session = state.session ?: return
        if (state.working) return
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                manualSync.setCloudConversationPinned(session, conversation.id, pinned)
            }
            if (state.session?.userId == session.userId) {
                state = state.copy(notice = if (saved) {
                    if (pinned) "云端列表已置顶，本地列表未改动。" else "已取消云端列表置顶，本地列表未改动。"
                } else "该对话不在当前账号的云端列表中。")
                if (saved) refreshCloudConversationProjection()
            }
        }
    }

    private fun refreshCloudConversationProjection() {
        val session = state.session ?: return
        viewModelScope.launch {
            val projection = withContext(Dispatchers.IO) {
                Triple(manualSync.syncedConversationIds(session), manualSync.cloudPinnedConversationIds(session), manualSync.cloudConversationProjection(session))
            }
            Log.i(
                "NanfengCloudSync",
                "cloud-list-projection synced=${projection.first.size} pinned=${projection.second.size} rows=${projection.third.size}",
            )
            if (state.session?.userId == session.userId) state = state.copy(
                syncedConversationIds = projection.first,
                cloudPinnedConversationIds = projection.second.intersect(projection.first),
                cloudConversations = projection.third,
            ).also { onCloudProjectionRefreshed() }
        }
    }

    class Factory(
        private val accountOwner: P7FGoogleAccountOwner,
        private val manualSync: P7FManualConversationSyncOwner,
        private val scheduler: P7FSelectedConversationSyncScheduler,
        private val onCloudProjectionRefreshed: () -> Unit = {},
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = P7DAccountSyncViewModel(accountOwner, manualSync, scheduler, onCloudProjectionRefreshed) as T
    }
}

@Composable
internal fun P7DAccountSyncScreen(
    state: P7DAccountSyncUiState,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onSwitchAccount: () -> Unit,
    onSignOut: () -> Unit,
    onPeriodicChanged: (Boolean) -> Unit,
    onPrepareRecovery: (String, Boolean) -> Unit,
    onReadCloudDocuments: () -> Unit,
    loadAvatar: suspend (String) -> ByteArray?,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var recoveryCode by remember(state.session?.userId) { mutableStateOf("") }
    var recoverySaved by remember(state.session?.userId) { mutableStateOf(false) }
    LaunchedEffect(state.notice, state.completedOperationFeedback) {
        if (state.completedOperationFeedback == null) {
            state.notice?.takeIf { it.isNotBlank() }?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
        }
    }
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(56.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).size(48.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回设置")
            }
            Text(
                "Google 账号与同步",
                modifier = Modifier.align(Alignment.Center).semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        WhiteCard(Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                P7DGoogleAccountAvatar(state.session, loadAvatar)
                Spacer(Modifier.height(12.dp))
                val session = state.session
                Text(
                    if (session == null) "未登录" else "${session.displayName?.takeIf(String::isNotBlank) ?: "Google 用户"}，您好！",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (session != null && session.email.isNotBlank()) Text(session.email, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                if (session == null) {
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = onSignIn,
                        enabled = state.configured && !state.working,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = P5AInteractiveShape,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) {
                        if (state.working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else { Icon(Icons.AutoMirrored.Rounded.Login, contentDescription = null, modifier = Modifier.size(20.dp)); Spacer(Modifier.size(8.dp)); Text("使用 Google 登录") }
                    }
                }
            }
        }
        WhiteCard(Modifier.fillMaxWidth()) {
            Text("账号管理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onSwitchAccount,
                enabled = state.session != null && !state.working,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = P5AInteractiveShape,
                border = null,
                colors = ButtonDefaults.outlinedButtonColors(containerColor = SettingsPageBackground, contentColor = BodyText),
            ) {
                Icon(Icons.Rounded.SyncAlt, contentDescription = null, modifier = Modifier.size(20.dp)); Spacer(Modifier.size(8.dp)); Text("切换 Google 账号")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onSignOut,
                enabled = state.session != null && !state.working,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = P5AInteractiveShape,
                border = null,
                colors = ButtonDefaults.outlinedButtonColors(containerColor = SettingsPageBackground, contentColor = BodyText),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(20.dp)); Spacer(Modifier.size(8.dp)); Text("退出登录")
            }
        }
        WhiteCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.lastSyncedAtEpochMs == null) {
                    Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                } else {
                    NanfengCloudDoneIcon(tint = MaterialTheme.colorScheme.primary, contentDescription = null, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.size(10.dp))
                Text("对话同步", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("定期同步", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    state.lastSyncedAtEpochMs?.let {
                        Text("上次同步  ${formatSyncTime(it)}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                }
                SettingsSwitch(checked = state.periodicEnabled, onCheckedChange = onPeriodicChanged, enabled = state.recoveryReady && !state.working)
            }
            if (state.session != null && !state.recoveryReady) {
                Spacer(Modifier.height(16.dp))
                Text("恢复保护", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = recoveryCode,
                    onValueChange = { recoveryCode = it.take(128) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("输入至少 12 个字符的恢复码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !state.working,
                    shape = P5AInteractiveShape,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = recoverySaved, onCheckedChange = { recoverySaved = it }, enabled = !state.working)
                    Text("我已保存恢复码", style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = { onPrepareRecovery(recoveryCode, recoverySaved); recoveryCode = "" },
                    enabled = recoveryCode.length >= 12 && recoverySaved && !state.working,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = P5AInteractiveShape,
                ) { Text("启用端到端加密同步") }
            }
        }
        if (state.session != null) WhiteCard(Modifier.fillMaxWidth()) {
            Text("恢复与安全", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(20.dp))
            Text(if (state.recoveryReady) "恢复保护已启用，云端只保存加密封包。" else "完成恢复保护后才能读取或同步云端对话。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(18.dp))
            OutlinedButton(
                onClick = onReadCloudDocuments,
                enabled = state.recoveryReady && !state.working,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = P5AInteractiveShape,
                border = null,
                colors = ButtonDefaults.outlinedButtonColors(containerColor = SettingsPageBackground, contentColor = BodyText),
            ) {
                Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("读取云端列表")
            }
        }
        if (!state.configured) Text("Google 登录与云端服务尚未配置。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            state.notice?.let {
                val isSuccess = it.startsWith("已") || it.endsWith("已启用。") || it.endsWith("已关闭。")
                Text(it, color = if (isSuccess) MaterialTheme.colorScheme.primary else ErrorRed, style = MaterialTheme.typography.bodySmall)
            }
        }
        P7DAccountSyncProgressDialog(state.activeOperation, state.completedOperationFeedback)
    }
}

@Composable
internal fun P7DAccountSyncProgressDialog(
    operation: P7DAccountSyncOperation?,
    completedFeedback: P7DAccountSyncFeedback? = null,
) {
    if (operation == null && completedFeedback == null) return
    val isWorking = operation != null
    val title = if (isWorking) operation.progressLabel else checkNotNull(completedFeedback).title
    val detail = if (isWorking) operation.progressDetail else checkNotNull(completedFeedback).detail
    val feedbackKind = completedFeedback?.kind
    Dialog(onDismissRequest = {}) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = ForegroundSurface,
            contentColor = BodyText,
            // In light mode ForegroundSurface is pure white. Avoid Material's
            // tonal overlay so this acknowledgement never turns gray; dark mode
            // still receives its own semantic foreground surface.
            tonalElevation = 0.dp,
            shadowElevation = 18.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (isWorking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        imageVector = if (feedbackKind == P7DAccountSyncFeedbackKind.SUCCESS) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(38.dp),
                        tint = when (feedbackKind) {
                            P7DAccountSyncFeedbackKind.SUCCESS -> MaterialTheme.colorScheme.primary
                            P7DAccountSyncFeedbackKind.ATTENTION -> MaterialTheme.colorScheme.tertiary
                            P7DAccountSyncFeedbackKind.ERROR -> ErrorRed
                            null -> MaterialTheme.colorScheme.primary
                        },
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                if (detail.isNotBlank()) Text(detail, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun P7DGoogleAccountAvatar(
    session: P7FCloudSession?,
    loadAvatar: suspend (String) -> ByteArray?,
) {
    val context = LocalContext.current.applicationContext
    val cache = remember(context) { P7DAvatarCache(context) }
    val avatarUrl = session?.avatarUrl?.trim()?.takeIf(String::isNotBlank)
    val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        key1 = session?.userId,
        key2 = avatarUrl,
    ) {
        val accountRef = session?.userId
        if (accountRef == null || avatarUrl == null || !P7DAvatarCache.isAllowedGoogleAvatarUrl(avatarUrl)) return@produceState
        value = withContext(Dispatchers.IO) {
            cache.read(accountRef, avatarUrl)?.let { bytes ->
                try { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
                finally { bytes.fill(0) }
            }
        }
        loadAvatar(avatarUrl)?.let { bytes ->
            try {
                val decoded = withContext(Dispatchers.IO) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
                if (decoded != null) {
                    withContext(Dispatchers.IO) { runCatching { cache.write(accountRef, avatarUrl, bytes) } }
                    value = decoded
                }
            } finally {
                bytes.fill(0)
            }
        }
    }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        modifier = Modifier.size(72.dp),
    ) {
        when {
            image != null -> Image(
                bitmap = checkNotNull(image),
                contentDescription = "Google 账号头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            session == null -> Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
            }
            else -> Box(contentAlignment = Alignment.Center) {
                Text(
                    text = (session.displayName?.trim()?.takeIf(String::isNotBlank) ?: session.email).firstOrNull()?.uppercase() ?: "G",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun formatSyncTime(epochMs: Long): String = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    .withZone(ZoneId.systemDefault())
    .format(Instant.ofEpochMilli(epochMs))
