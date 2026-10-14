package com.nanzhufeng.ai.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.asStateFlow

internal data class IslandFeedbackEvent(val id:Long,val message:String,val failed:Boolean,val createdAtNanos:Long=System.nanoTime())
internal object NanfengIslandFeedback {
    private val sequence=java.util.concurrent.atomic.AtomicLong()
    private val mutable=kotlinx.coroutines.flow.MutableStateFlow<IslandFeedbackEvent?>(null)
    val events=mutable.asStateFlow()
    fun show(message:String,failed:Boolean=false) {mutable.value=IslandFeedbackEvent(sequence.incrementAndGet(),message,failed)}
}

/** Foreground action results use the platform toast; generation islands belong to the background overlay. */
@Composable internal fun ForegroundActionFeedbackHost() {
    val context = LocalContext.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val mountedAt = remember { System.nanoTime() }
    val event by NanfengIslandFeedback.events.collectAsState()
    LaunchedEffect(event?.id) {
        event?.takeIf {
            it.createdAtNanos >= mountedAt && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
        }?.let {
            android.widget.Toast.makeText(context, it.message, if (it.failed) android.widget.Toast.LENGTH_LONG else android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable internal fun BackgroundIslandPermissionRow() {
    val context=LocalContext.current
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    var allowed by remember {mutableStateOf(Settings.canDrawOverlays(context))}
    DisposableEffect(lifecycle) {
        val observer=androidx.lifecycle.LifecycleEventObserver { _,event -> if(event==androidx.lifecycle.Lifecycle.Event.ON_RESUME) allowed=Settings.canDrawOverlays(context) }
        lifecycle.addObserver(observer);onDispose {lifecycle.removeObserver(observer)}
    }
    TextButton(onClick={runCatching {context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:${context.packageName}")))}}) {
        Text("后台生成灵动岛 · ${if(allowed) "已授权，点按管理" else "点按授权悬浮显示"}")
    }
    Text("离开应用后显示模型、已保存字数和耗时；关闭悬浮权限不影响生成。锁屏不展示。",color=SecondaryText,style=MaterialTheme.typography.bodySmall)
}
