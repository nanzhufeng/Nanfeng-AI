package com.nanzhufeng.ai.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class P7DAccountSyncUiState(
    val detailVisible: Boolean = false,
    val configured: Boolean = false,
    val session: P7FCloudSession? = null,
    val working: Boolean = false,
    val notice: String? = null,
    val lastSyncedAtEpochMs: Long? = null,
    val recoveryReady: Boolean = false,
    val periodicEnabled: Boolean = false,
)

/** Opening the page is presentation-only. Network work starts only from an explicit button. */
class P7DAccountSyncViewModel(
    private val accountOwner: P7FGoogleAccountOwner,
    private val manualSync: P7FManualConversationSyncOwner,
    private val scheduler: P7FSelectedConversationSyncScheduler,
) : ViewModel() {
    var state by mutableStateOf(loadState())
        private set

    init { refreshLatestSync() }

    fun open() {
        val notice = state.notice
        state = loadState().copy(detailVisible = true, notice = notice)
        refreshLatestSync()
    }
    fun close() { state = state.copy(detailVisible = false, notice = null) }
    fun signIn(activityContext: Context) = runAccountAction(activityContext, switch = false)
    fun switchAccount(activityContext: Context) = runAccountAction(activityContext, switch = true)
    suspend fun loadAvatar(avatarUrl: String): ByteArray? = accountOwner.loadGoogleAvatar(avatarUrl)

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
                state = state.copy(working = true, notice = null)
                viewModelScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        val preview = manualSync.preview(conversation.id)
                        if (preview.eligible) manualSync.sync(conversation.id)
                        else P7FManualConversationSyncResult.Rejected(preview.reason ?: "该对话暂时无法同步。")
                    }
                    state = when (result) {
                        is P7FManualConversationSyncResult.Synced -> state.copy(working = false, lastSyncedAtEpochMs = result.syncedAtEpochMs, notice = "已同步「${result.title}」。")
                        is P7FManualConversationSyncResult.Rejected -> state.copy(working = false, notice = result.message)
                        P7FManualConversationSyncResult.Conflict -> state.copy(working = false, notice = "云端版本已变化，未覆盖。")
                    }
                }
            }
        }
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

    fun setPeriodicEnabled(enabled: Boolean) {
        if (state.session == null || !state.recoveryReady || state.working) return
        runCatching { scheduler.setEnabled(enabled) }
            .onSuccess { state = state.copy(periodicEnabled = enabled, notice = if (enabled) "定期同步已启用。" else "定期同步已关闭。") }
            .onFailure { state = state.copy(notice = "定期同步设置保存失败。") }
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
                    if (accountOwner.recoveryReady(session.userId)) scheduler.resumeIfEnabled()
                    loadState().copy(detailVisible = true, notice = "已登录 Google 账号。")
                },
                onFailure = { error -> loadState().copy(detailVisible = true, notice = error.message ?: "登录失败。") },
            )
            if (result.isSuccess) refreshLatestSync()
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

    class Factory(
        private val accountOwner: P7FGoogleAccountOwner,
        private val manualSync: P7FManualConversationSyncOwner,
        private val scheduler: P7FSelectedConversationSyncScheduler,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = P7DAccountSyncViewModel(accountOwner, manualSync, scheduler) as T
    }
}

@Composable
internal fun P7DAccountSyncScreen(
    state: P7DAccountSyncUiState,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onSwitchAccount: () -> Unit,
    onSignOut: () -> Unit,
    onPrepareRecovery: (String, Boolean) -> Unit,
    onPeriodicChanged: (Boolean) -> Unit,
    loadAvatar: suspend (String) -> ByteArray?,
) {
    BackHandler(onBack = onBack)
    var recoveryCode by rememberSaveable(state.session?.userId) { mutableStateOf("") }
    var recoverySaved by rememberSaveable(state.session?.userId) { mutableStateOf(false) }
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
                Icon(if (state.lastSyncedAtEpochMs == null) Icons.Rounded.CloudOff else Icons.Rounded.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.size(10.dp))
                Text("对话同步", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Text("仅包含你手动同步过的对话。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            if (state.session != null && !state.recoveryReady) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = recoveryCode,
                    onValueChange = { recoveryCode = it.take(128) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("恢复码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !state.working,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = recoverySaved, onCheckedChange = { recoverySaved = it }, enabled = !state.working)
                    Text("已保存恢复码", style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = { onPrepareRecovery(recoveryCode, recoverySaved) },
                    enabled = recoveryCode.length >= 12 && recoverySaved && !state.working,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = P5AInteractiveShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) { Text("启用对话同步") }
                Text("恢复码只显示这一次，不保存。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            } else if (state.recoveryReady) {
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("定期同步", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text("每 12 小时更新已选对话", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                    SettingsSwitch(checked = state.periodicEnabled, onCheckedChange = onPeriodicChanged, enabled = !state.working)
                }
            }
            state.lastSyncedAtEpochMs?.let { Spacer(Modifier.height(8.dp)); Text("上次同步  ${formatSyncTime(it)}", color = SecondaryText, style = MaterialTheme.typography.bodySmall) }
        }
        if (!state.configured) Text("Google 登录与云端服务尚未配置。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        state.notice?.let {
            val isSuccess = it.startsWith("已") || it.endsWith("已启用。") || it.endsWith("已关闭。")
            Text(it, color = if (isSuccess) MaterialTheme.colorScheme.primary else ErrorRed, style = MaterialTheme.typography.bodySmall)
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
