package com.nanzhufeng.ai.background

import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.MotionEvent
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.*
import com.nanzhufeng.ai.domain.GenerationIslandTask

/** Download-style centered carrier. Overlay failures affect presentation alone. */
internal class GenerationIslandOverlay(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val windows = context.getSystemService(WindowManager::class.java)
    private var carrier: FrameLayout? = null
    private var root: LinearLayout? = null
    private val presentation=com.nanzhufeng.ai.domain.GenerationIslandPresentation()
    val expanded get()=presentation.expanded
    private var renderedExpanded = false
    private var tasks = emptyList<GenerationIslandTask>()
    private var now = 0L
    private var structure=""
    private var headerView:TextView?=null
    private var detailsView:ScrollView?=null
    private val detailLabels=mutableMapOf<String,TextView>()
    fun hide() { handler.post { presentation.collapse(android.os.SystemClock.elapsedRealtime()); remove() } }
    fun update(next: List<GenerationIslandTask>, at: Long) {
        handler.post {
            now=at
            try {
                presentation.update(next,at)
                tasks=presentation.visible(at)
                if (GenerationIslandRuntime.foreground || !Settings.canDrawOverlays(context) ||
                    context.getSystemService(KeyguardManager::class.java).isKeyguardLocked ||
                    !context.getSystemService(PowerManager::class.java).isInteractive) { presentation.collapse(at); remove(); return@post }
                if (tasks.isEmpty()) { remove(); return@post }
                render()
            } catch (_: RuntimeException) { remove() }
        }
    }
    private fun render() {
        val active=tasks.filter {it.running};val task=active.firstOrNull() ?: tasks.last()
        val summary=if(active.size>1) "${active.size} 个对话生成中" else "${task.modelLabel} · ${task.stage} · ${task.savedCharacters} 字"
        val signature="$expanded:${context.resources.configuration.fontScale}:"+tasks.joinToString {"${it.key}:${it.startedAt}:${it.outcome}:${it.modelLabel}"}
        if(carrier!=null && signature==structure) {
            headerView?.text=summary
            tasks.forEach {detailLabels[it.key]?.text="${it.stage} · ${it.detail(now)}"}
            return
        }
        val previousScroll=detailsView?.scrollY ?: 0
        detailLabels.clear()
        val first=carrier == null
        val surface=root ?: LinearLayout(context).apply {
            orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER_HORIZONTAL
            elevation=dp(8).toFloat();clipToOutline=true
            background=shape(Color.rgb(22,24,31),24)
        }
        val oldWidth=surface.width*surface.scaleX;val oldHeight=surface.height*surface.scaleY
        val modeChanged=expanded != renderedExpanded
        if (modeChanged) surface.animate().cancel()
        surface.removeAllViews();surface.setPadding(dp(16),dp(8),dp(16),dp(8))
        val accent=when {task.running -> Color.rgb(194,166,255);task.outcome=="COMPLETE" -> Color.rgb(93,218,150);task.outcome=="CANCELLED" -> Color.rgb(193,196,202);else -> Color.rgb(255,135,135)}
        val header=text(summary,14,accent).apply {
            gravity=Gravity.CENTER;minimumHeight=dp(32)
            contentDescription="${text}，${if(expanded) "收起" else "展开"}详情"
            setOnClickListener { presentation.toggle(now);render() }
        }
        headerView=header
        surface.addView(header)
        if (expanded) {
            val list=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL }
            tasks.forEach { item ->
                list.addView(text(item.modelLabel,13,Color.WHITE).apply { setPadding(0,dp(8),0,dp(3)); maxLines=2 })
                list.addView(text("${item.stage} · ${item.detail(now)}",12,Color.LTGRAY).apply { maxLines=3;detailLabels[item.key]=this })
                list.addView(LinearLayout(context).apply {
                    gravity=Gravity.CENTER
                    addView(action("查看对话",accent) { runCatching { GenerationIslandRuntime.open(context,item) };remove() }, LinearLayout.LayoutParams(0,dp(48),1f))
                    if(item.running) addView(action("停止",Color.rgb(255,150,150)) { runCatching { GenerationIslandRuntime.stop(context,item) } },LinearLayout.LayoutParams(0,dp(48),1f))
                })
            }
            surface.addView(ScrollView(context).apply { addView(list);isFillViewport=false;detailsView=this;post {scrollTo(0,previousScroll)} },LinearLayout.LayoutParams(-1,minOf(dp(300),tasks.size*dp(116))))
            if(active.isEmpty()) surface.addView(action("关闭提醒",Color.LTGRAY) { presentation.dismiss();remove() },LinearLayout.LayoutParams(-1,dp(48)))
        }
        val maxWidth=context.resources.displayMetrics.widthPixels-dp(24)
        surface.measure(View.MeasureSpec.makeMeasureSpec(minOf(dp(if(expanded) 320 else 260),maxWidth),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
        val width=surface.measuredWidth;val height=surface.measuredHeight
        val holder=carrier ?: FrameLayout(context).apply {
            clipChildren=false;clipToPadding=false;addView(surface)
            setOnTouchListener { _,event ->
                val bounds=android.graphics.Rect();surface.getHitRect(bounds)
                val outside=event.actionMasked==MotionEvent.ACTION_OUTSIDE || (event.actionMasked==MotionEvent.ACTION_DOWN && !bounds.contains(event.x.toInt(),event.y.toInt()))
                if(expanded && outside) {now=android.os.SystemClock.elapsedRealtime();presentation.collapse(now);render();true} else false
            }
        }
        surface.layoutParams=FrameLayout.LayoutParams(width,height,Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        surface.pivotX=width/2f;surface.pivotY=0f
        val params=WindowManager.LayoutParams(minOf(dp(352),maxWidth),maxOf(height,oldHeight.toInt())+dp(12),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,PixelFormat.TRANSLUCENT).apply { gravity=Gravity.TOP or Gravity.CENTER_HORIZONTAL;y=dp(8);windowAnimations=0;title="NanfengAiIsland" }
        if (first) { windows.addView(holder,params);carrier=holder;root=surface } else windows.updateViewLayout(holder,params)
        if(first || modeChanged) {
            surface.scaleX=if(first) .9f else oldWidth/width;surface.scaleY=if(first) .9f else oldHeight/height
            surface.translationX=0f;surface.translationY=0f
            if(ValueAnimator.areAnimatorsEnabled()) surface.animate().scaleX(1f).scaleY(1f).setDuration(360).setInterpolator(OvershootInterpolator(1.1f)).start()
            else { surface.scaleX=1f;surface.scaleY=1f }
        }
        renderedExpanded=expanded
        structure=signature
    }
    private fun shape(color: Int,radius: Int)=GradientDrawable().apply {setColor(color);cornerRadius=dp(radius).toFloat()}
    private fun text(value: String,size: Int,color: Int)=TextView(context).apply {text=value;textSize=size.toFloat();setTextColor(color);includeFontPadding=false;maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END}
    private fun action(value: String,color: Int,onClick: ()->Unit)=text(value,12,color).apply {
        gravity=Gravity.CENTER;clipToOutline=true;background=RippleDrawable(ColorStateList.valueOf(0x20FFFFFF),shape(0x10FFFFFF,24),shape(Color.WHITE,24));setOnClickListener {onClick()}
    }
    private fun remove() {root?.animate()?.cancel();carrier?.let {runCatching {windows.removeViewImmediate(it)}};carrier=null;root=null;headerView=null;detailsView=null;detailLabels.clear();structure="";presentation.collapse(now);renderedExpanded=false}
    private fun dp(value: Int)=(value*context.resources.displayMetrics.density).toInt()
}
