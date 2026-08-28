package com.nanzhufeng.ai.ui

import android.graphics.BitmapFactory
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.graphics.ImageDecoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import com.nanzhufeng.ai.domain.P6KZipManualLinkTarget
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.ModelPresetId
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.clickable
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.isSystemInDarkTheme
import com.nanzhufeng.ai.BuildConfig
import com.nanzhufeng.ai.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.nio.ByteBuffer
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.nanzhufeng.ai.domain.ChatGptImportItemId
import com.nanzhufeng.ai.domain.ChatGptImportTask
import com.nanzhufeng.ai.domain.ChatGptImportTaskId
import com.nanzhufeng.ai.domain.ClaudeImportItemId
import com.nanzhufeng.ai.domain.ClaudeImportTask
import com.nanzhufeng.ai.domain.ClaudeImportTaskId
import com.nanzhufeng.ai.domain.NanfengKnowledgeImportItemId
import com.nanzhufeng.ai.domain.NanfengKnowledgeImportTask
import com.nanzhufeng.ai.domain.NanfengKnowledgeImportTaskId

// DocumentsUI may materialize the ZIP MIME type by appending `.zip`. The suggested display
// name remains the v2 protocol label; receipts never use either value as package identity.
internal const val WORKSPACE_EXCHANGE_V2_DOCUMENT_MIME = "application/zip"
internal const val WORKSPACE_EXCHANGE_V2_SUGGESTED_DISPLAY_NAME = "nanfeng-ai-workspace-v2.nfai-exchange"

/** Shared skin tokens. Geometry and interaction stay fixed; only surfaces change by mode. */
internal var PageBackground by mutableStateOf(Color(0xFFF7F7F7))
internal var SettingsPageBackground by mutableStateOf(Color(0xFFEDEDED))
/** The uninterrupted drawer base; in light mode it must not become a hard-edged gray cover. */
internal var ConversationDrawerBaseSurface by mutableStateOf(Color.White)
/**
 * Neutral gray used only by the drawer's edge fade in light mode. The drawer itself is not a
 * gray scrim: list content, selection and explicit controls retain their own visual roles.
 */
internal var ConversationDrawerCanvas by mutableStateOf(Color(0xFFF2F2F2))
/** Ordinary conversation rows merge into the uninterrupted drawer base; only selected controls lift. */
internal var ConversationDrawerRowSurface by mutableStateOf(ConversationDrawerBaseSurface)
/** The drawer's search and planned shortcuts need one clear gray step above its continuous canvas. */
internal var ConversationDrawerQuickActionSurface by mutableStateOf(Color(0xFFEBEEEC))
// Only the page canvas is gray. Any surface that carries controls or readable
// information must remain fully bright, including the settings entry cards.
internal var ForegroundSurface by mutableStateOf(Color.White)
/** Semantic success only; it must not be reused as the interactive brand accent. */
internal var BrandGreen by mutableStateOf(Color(0xFF167A61))
/** Shared with the already-updated Desktop chat shell: bright, warm CTA orange. */
/** Product default only. Visible interactive accents always consume [AccentOrange] below. */
internal val DefaultAccentOrange = Color(0xFFE97128)
/** User-selected theme accent: pills, primary actions, selected rows and non-semantic icon tiles. */
internal var AccentOrange by mutableStateOf(DefaultAccentOrange)
internal var AccentOrangeHover by mutableStateOf(Color(0xFFD86520))
internal var AccentOrangePressed by mutableStateOf(Color(0xFFC2581A))
internal var AccentOrangeSoft by mutableStateOf(Color(0xFFFFF1E5))
internal var DrawerSettingsAccent by mutableStateOf(Color(0xFFB95520))
/** Low-emphasis input focus only; primary orange remains reserved for CTAs. */
internal var ComposerFocusBorder by mutableStateOf(Color(0xFFEFBD94))
internal var ComposerIdleBorder by mutableStateOf(Color(0xFFDFE2DE))
internal val AccentOnPrimary = Color.White
internal var AccentDisabled by mutableStateOf(Color(0xFFF4C8AA))
internal var ActionOrange by mutableStateOf(DefaultAccentOrange)
internal var NeutralBorder by mutableStateOf(Color(0xFFD8DEDA))
/** Transcript date rules must remain visible against the neutral conversation canvas. */
internal var SubtleDivider by mutableStateOf(NeutralBorder)
internal var BodyText by mutableStateOf(Color(0xFF1E2925))
internal var NeutralAssistantSurface by mutableStateOf(Color(0xFFF7F8F7))
internal var NeutralSystemSurface by mutableStateOf(Color(0xFFF2F4F3))
/** Search keeps a deeper local canvas so white file/result cards remain immediately distinct. */
internal var SearchPageCanvas by mutableStateOf(Color(0xFFEDEEEE))
/** Search input and history are one visual step deeper than the search canvas. */
internal var SearchControlSurface by mutableStateOf(Color(0xFFE1E4E2))
internal var ToolSurface by mutableStateOf(Color(0xFFF1F5F8))
internal var SemanticErrorSurface by mutableStateOf(Color(0xFFFFECEB))
internal var ToolText by mutableStateOf(Color(0xFF31566C))
internal var ErrorText by mutableStateOf(Color(0xFFB3261E))

internal data class ChatRoleVisual(val surface: Color, val label: Color, val body: Color)

/** Shared role mapping: user messages retain their warm bubble; direct actions use solid orange. */
internal fun chatRoleVisual(role: com.nanzhufeng.ai.domain.MessageRole): ChatRoleVisual = when (role) {
    // A user bubble follows the chosen theme hue, but deliberately uses the low-saturation
    // container tone so a long conversation never becomes a wall of strong brand colour.
    com.nanzhufeng.ai.domain.MessageRole.USER -> ChatRoleVisual(
        ActiveUserBubbleSurface,
        ActiveUserBubbleAccent,
        BodyText,
    )
    com.nanzhufeng.ai.domain.MessageRole.ASSISTANT -> ChatRoleVisual(NeutralAssistantSurface, SecondaryText, BodyText)
    com.nanzhufeng.ai.domain.MessageRole.SYSTEM -> ChatRoleVisual(NeutralSystemSurface, SecondaryText, BodyText)
    com.nanzhufeng.ai.domain.MessageRole.TOOL -> ChatRoleVisual(ToolSurface, ToolText, BodyText)
}

/** Theme-owned message colors remain observable while the role mapper stays usable in contracts. */
internal var ActiveUserBubbleSurface by mutableStateOf(AccentOrangeSoft)
internal var ActiveUserBubbleAccent by mutableStateOf(AccentOrange)
internal var SecondaryText by mutableStateOf(Color(0xFF64706B))
/** The one rounded appearance glyph is shared by the settings home and its compatibility page. */
internal val SettingsAppearanceIcon = Icons.Rounded.Brightness6
/** Placeholder text is intentionally much quieter than a user-entered value. */
internal var InputPlaceholderText by mutableStateOf(Color(0xFFA7AEAA))
internal val ErrorRed = Color(0xFFB3261E)
internal val CardShape = RoundedCornerShape(20.dp)
/** Shared canvas stripe for every multi-row settings card. */
internal val SettingsGroupedCardDividerHeight = 4.dp

/**
 * Dark mode is deliberately charcoal rather than black.  Only settings may use the darkest
 * canvas; conversation and work surfaces retain enough luminance to stay calm and readable.
 */
private fun applyAppearancePalette(dark: Boolean) {
    if (dark) {
        PageBackground = Color(0xFF2C2C2C)
        SettingsPageBackground = Color(0xFF262626)
        ConversationDrawerBaseSurface = Color(0xFF323232)
        ConversationDrawerCanvas = Color(0xFF323232)
        ConversationDrawerRowSurface = ConversationDrawerBaseSurface
        ConversationDrawerQuickActionSurface = Color(0xFF454545)
        ForegroundSurface = Color(0xFF484848)
        BrandGreen = Color(0xFF75CBB0)
        AccentOrangeSoft = Color(0xFF5A4031)
        ComposerFocusBorder = Color(0xFFC87848)
        ComposerIdleBorder = Color(0xFF626262)
        AccentDisabled = Color(0xFF7B583F)
        NeutralBorder = Color(0xFF5E5E5E)
        SubtleDivider = Color(0xFF5A5A5A)
        BodyText = Color(0xFFF5F5F2)
        SecondaryText = Color(0xFFC9CBC8)
        InputPlaceholderText = Color(0xFF9EA19E)
        NeutralAssistantSurface = Color(0xFF373737)
        NeutralSystemSurface = Color(0xFF404040)
        SearchPageCanvas = Color(0xFF303030)
        SearchControlSurface = Color(0xFF454545)
        ToolSurface = Color(0xFF3E4647)
        SemanticErrorSurface = Color(0xFF5A302F)
        ToolText = Color(0xFFD7E6E4)
        ErrorText = Color(0xFFFFB4AB)
    } else {
        PageBackground = Color(0xFFF7F7F7)
        SettingsPageBackground = Color(0xFFEDEDED)
        ConversationDrawerBaseSurface = Color.White
        ConversationDrawerCanvas = Color(0xFFF2F2F2)
        ConversationDrawerRowSurface = ConversationDrawerBaseSurface
        ConversationDrawerQuickActionSurface = Color(0xFFEBEEEC)
        ForegroundSurface = Color.White
        BrandGreen = Color(0xFF167A61)
        AccentOrangeSoft = Color(0xFFFFF1E5)
        ComposerFocusBorder = Color(0xFFEFBD94)
        ComposerIdleBorder = Color(0xFFDFE2DE)
        AccentDisabled = Color(0xFFF4C8AA)
        NeutralBorder = Color(0xFFD8DEDA)
        SubtleDivider = NeutralBorder
        BodyText = Color(0xFF1E2925)
        SecondaryText = Color(0xFF64706B)
        InputPlaceholderText = Color(0xFFA7AEAA)
        NeutralAssistantSurface = Color(0xFFF7F8F7)
        NeutralSystemSurface = Color(0xFFF2F4F3)
        SearchPageCanvas = Color(0xFFEDEEEE)
        SearchControlSurface = Color(0xFFE1E4E2)
        ToolSurface = Color(0xFFF1F5F8)
        SemanticErrorSurface = Color(0xFFFFECEB)
        ToolText = Color(0xFF31566C)
        ErrorText = Color(0xFFB3261E)
    }
}

private enum class SettingsDestination(val label: String) {
    HOME("设置"),
    APPEARANCE("外观"),
    PERSONALIZATION("个性化"),
    NOTIFICATIONS("提醒"),
    MODEL("模型与联网"),
    MODEL_CONFIGURATION("模型设置"),
    CONVERSATION_COST("费用与用量"),
    CONTEXT_SELECTIONS("上下文记录"),
    RUN_DIAGNOSTICS("运行诊断"),
    CONVERSATIONS("对话管理"),
    FAVORITE_CONVERSATIONS("收藏"),
    ARCHIVED_CONVERSATIONS("已归档"),
    RECYCLE_BIN("回收站"),
    WORKSPACE("工作区"),
    DEVELOPMENT("开发与诊断"),
    DATA_STORAGE("数据与存储"),
    JSON_IMPORT_RESULTS("JSON 导入结果"),
    ZIP_IMPORT_RESULTS("ZIP 导入结果"),
    LOCAL_BACKUP("备份与恢复"),
    ABOUT("关于"),
    PRIVACY("隐私与安全"),
}

private fun TextStyle.scaledForAppFontSize(scale: Float): TextStyle = copy(
    fontSize = fontSize * scale,
    lineHeight = lineHeight * scale,
)

private fun Typography.scaledForAppFontSize(scale: Float): Typography = copy(
    displayLarge = displayLarge.scaledForAppFontSize(scale),
    displayMedium = displayMedium.scaledForAppFontSize(scale),
    displaySmall = displaySmall.scaledForAppFontSize(scale),
    headlineLarge = headlineLarge.scaledForAppFontSize(scale),
    headlineMedium = headlineMedium.scaledForAppFontSize(scale),
    headlineSmall = headlineSmall.scaledForAppFontSize(scale),
    titleLarge = titleLarge.scaledForAppFontSize(scale),
    titleMedium = titleMedium.scaledForAppFontSize(scale),
    titleSmall = titleSmall.scaledForAppFontSize(scale),
    bodyLarge = bodyLarge.scaledForAppFontSize(scale),
    bodyMedium = bodyMedium.scaledForAppFontSize(scale),
    bodySmall = bodySmall.scaledForAppFontSize(scale),
    labelLarge = labelLarge.scaledForAppFontSize(scale),
    labelMedium = labelMedium.scaledForAppFontSize(scale),
    labelSmall = labelSmall.scaledForAppFontSize(scale),
)

/** Every settings icon that carries information follows the user's font-size preference. */
internal val LocalAppIconScale = staticCompositionLocalOf { 1f }
internal val LocalAppTextScale = staticCompositionLocalOf { 1f }

@Composable
internal fun scaledAppIconSize(base: Dp): Dp = base * LocalAppIconScale.current

@Composable
internal fun scaledAppTextUnit(base: androidx.compose.ui.unit.TextUnit): androidx.compose.ui.unit.TextUnit =
    base * LocalAppTextScale.current

// Settings used the unscaled Material defaults while the conversation's reading surfaces use a
// compact text hierarchy.  Keep every settings level in the same density family: title, rows,
// helper copy and their line heights all scale together instead of individually drifting larger.
private const val SettingsTextScaleFactor = 0.90f

private fun TextStyle.scaledForSettingsText(): TextStyle = copy(
    fontSize = fontSize * SettingsTextScaleFactor,
    lineHeight = lineHeight * SettingsTextScaleFactor,
)

private fun Typography.scaledForSettingsText(): Typography = copy(
    displayLarge = displayLarge.scaledForSettingsText(),
    displayMedium = displayMedium.scaledForSettingsText(),
    displaySmall = displaySmall.scaledForSettingsText(),
    headlineLarge = headlineLarge.scaledForSettingsText(),
    headlineMedium = headlineMedium.scaledForSettingsText(),
    headlineSmall = headlineSmall.scaledForSettingsText(),
    titleLarge = titleLarge.scaledForSettingsText(),
    titleMedium = titleMedium.scaledForSettingsText(),
    titleSmall = titleSmall.scaledForSettingsText(),
    bodyLarge = bodyLarge.scaledForSettingsText(),
    bodyMedium = bodyMedium.scaledForSettingsText(),
    bodySmall = bodySmall.scaledForSettingsText(),
    labelLarge = labelLarge.scaledForSettingsText(),
    labelMedium = labelMedium.scaledForSettingsText(),
    labelSmall = labelSmall.scaledForSettingsText(),
)

@Composable
private fun SettingsTextScale(content: @Composable () -> Unit) {
    val baseTypography = MaterialTheme.typography
    val settingsTypography = remember(baseTypography) { baseTypography.scaledForSettingsText() }
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        shapes = MaterialTheme.shapes,
        typography = settingsTypography,
        content = content,
    )
}

private fun com.nanzhufeng.ai.domain.AccentColor.toColor(): Color = when (this) {
    com.nanzhufeng.ai.domain.AccentColor.ORANGE -> DefaultAccentOrange
    com.nanzhufeng.ai.domain.AccentColor.BLUE -> Color(0xFF357AE8)
    com.nanzhufeng.ai.domain.AccentColor.BLACK -> Color(0xFF252A27)
    com.nanzhufeng.ai.domain.AccentColor.GREEN -> BrandGreen
    com.nanzhufeng.ai.domain.AccentColor.YELLOW -> Color(0xFFE6B335)
    com.nanzhufeng.ai.domain.AccentColor.PURPLE -> Color(0xFF8C63D9)
    com.nanzhufeng.ai.domain.AccentColor.PINK -> Color(0xFFE26091)
}

/** Muted companion for user message bubbles: same hue, intentionally lower chroma. */
private fun com.nanzhufeng.ai.domain.AccentColor.userBubbleColor(dark: Boolean = false): Color = when (this) {
    // Each value is darker than the neutral canvas but less saturated than its interactive
    // accent. That keeps the user's own messages visibly lifted without turning into CTAs.
    // Light orange keeps the bubble below the neutral page luminance, but uses the reference's
    // warmer apricot tone so it reads as the user's message instead of a dim gray-beige slab.
    com.nanzhufeng.ai.domain.AccentColor.ORANGE -> if (dark) Color(0xFF5D4031) else Color(0xFFF9E3D2)
    com.nanzhufeng.ai.domain.AccentColor.BLUE -> if (dark) Color(0xFF354654) else Color(0xFFDEE6EF)
    com.nanzhufeng.ai.domain.AccentColor.BLACK -> if (dark) Color(0xFF393D3A) else Color(0xFFDFE2DF)
    com.nanzhufeng.ai.domain.AccentColor.GREEN -> if (dark) Color(0xFF344B42) else Color(0xFFD8E5DF)
    com.nanzhufeng.ai.domain.AccentColor.YELLOW -> if (dark) Color(0xFF50462D) else Color(0xFFEDE4CD)
    com.nanzhufeng.ai.domain.AccentColor.PURPLE -> if (dark) Color(0xFF493E59) else Color(0xFFE5E0EF)
    com.nanzhufeng.ai.domain.AccentColor.PINK -> if (dark) Color(0xFF573D49) else Color(0xFFEBDDE4)
}

/** Muted selected-state surface for the current theme, never a second fixed orange token. */
private fun com.nanzhufeng.ai.domain.AccentColor.themeContainerColor(dark: Boolean): Color = when (this) {
    com.nanzhufeng.ai.domain.AccentColor.ORANGE -> if (dark) Color(0xFF5A4031) else Color(0xFFFFF1E5)
    com.nanzhufeng.ai.domain.AccentColor.BLUE -> if (dark) Color(0xFF34475B) else Color(0xFFE7F0FF)
    com.nanzhufeng.ai.domain.AccentColor.BLACK -> if (dark) Color(0xFF3D403E) else Color(0xFFE9ECE9)
    com.nanzhufeng.ai.domain.AccentColor.GREEN -> if (dark) Color(0xFF354D43) else Color(0xFFE3F3EA)
    com.nanzhufeng.ai.domain.AccentColor.YELLOW -> if (dark) Color(0xFF51472D) else Color(0xFFFFF4D8)
    com.nanzhufeng.ai.domain.AccentColor.PURPLE -> if (dark) Color(0xFF493E5B) else Color(0xFFF0EAFF)
    com.nanzhufeng.ai.domain.AccentColor.PINK -> if (dark) Color(0xFF593E4B) else Color(0xFFFFEAF1)
}

/** A compact navigation glyph needs more contrast than a selected background fill. */
private fun com.nanzhufeng.ai.domain.AccentColor.drawerSettingsIconColor(dark: Boolean): Color = when (this) {
    com.nanzhufeng.ai.domain.AccentColor.ORANGE -> if (dark) Color(0xFFE58A55) else Color(0xFFB95520)
    com.nanzhufeng.ai.domain.AccentColor.BLUE -> if (dark) Color(0xFF81AEF2) else Color(0xFF245DB8)
    com.nanzhufeng.ai.domain.AccentColor.BLACK -> if (dark) Color(0xFFD0D4D1) else Color(0xFF252A27)
    com.nanzhufeng.ai.domain.AccentColor.GREEN -> if (dark) Color(0xFF83D2B8) else Color(0xFF106C55)
    com.nanzhufeng.ai.domain.AccentColor.YELLOW -> if (dark) Color(0xFFF0CE62) else Color(0xFFAF8217)
    com.nanzhufeng.ai.domain.AccentColor.PURPLE -> if (dark) Color(0xFFC0A0F6) else Color(0xFF6941B0)
    com.nanzhufeng.ai.domain.AccentColor.PINK -> if (dark) Color(0xFFF39ABB) else Color(0xFFB9446C)
}

/** Utility icons remain secondary but cannot fade into the light setting cards. */
private fun settingsUtilityIconTint(): Color = if (BodyText.red > 0.5f) SecondaryText else Color(0xFF4D5954)

/** One entry per visible settings level; this is navigation state only, never business state. */
private data class SettingsNavigationEntry(
    val route: P5ARoute,
    val destination: SettingsDestination,
)

/**
 * Leaves the physical screen edge to Android system back. A drag which starts just inside either
 * app edge and travels inward returns to the still-available conversation drawer.
 */
private fun Modifier.settingsEdgeExit(onExit: () -> Unit): Modifier = pointerInput(onExit) {
    // Compose reports drag start after touch slop; keep the near-edge zone wide enough that the
    // original app-side edge touch is not reclassified as an interior drag.
    val activationEdge = 80.dp.toPx()
    val completionDistance = 72.dp.toPx()
    var direction = 0
    var travel = 0f
    detectHorizontalDragGestures(
        onDragStart = { offset ->
            direction = when {
                offset.x <= activationEdge -> 1
                offset.x >= size.width - activationEdge -> -1
                else -> 0
            }
            travel = 0f
        },
        onHorizontalDrag = { _, dragAmount -> if (direction != 0) travel += dragAmount },
        onDragCancel = { direction = 0 },
        onDragEnd = {
            if ((direction == 1 && travel >= completionDistance) ||
                (direction == -1 && travel <= -completionDistance)
            ) onExit()
            direction = 0
        },
    )
}

private fun java.io.InputStream.readMarkdownBounded(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream(limit.coerceAtMost(64 * 1024)); val buffer = ByteArray(8 * 1024)
    while (output.size() < limit) { val count = read(buffer, 0, minOf(buffer.size, limit - output.size())); if (count < 0) break; output.write(buffer, 0, count) }
    return output.toByteArray()
}

/** DocumentsUI may expose an opaque URI segment; use its Openable display name for ZIP routing. */
private fun pickerDisplayName(context: Context, uri: Uri, fallback: String): String = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            .takeIf { it >= 0 }
            ?.let(cursor::getString)
    }
}.getOrNull()?.takeIf { it.isNotBlank() && !it.startsWith("msf:") } ?: fallback

@Composable
internal fun NanfengAiApp(
    viewModel: CaptureViewModel,
    modelSettingsViewModel: ModelSettingsViewModel,
    invocationLedgerViewModel: InvocationLedgerViewModel,
    conversationCostLedgerViewModel: ConversationCostLedgerViewModel,
    knowledgeLibraryViewModel: KnowledgeLibraryViewModel,
    knowledgeExportViewModel: KnowledgeExportViewModel,
    conversationFoundationViewModel: ConversationFoundationViewModel,
    projectViewModel: ProjectViewModel,
    memoryViewModel: MemoryViewModel,
    contextBodySelectionViewModel: ContextBodySelectionViewModel,
    assistantExperienceSettingsViewModel: AssistantExperienceSettingsViewModel,
    notificationReminderSettingsViewModel: NotificationReminderSettingsViewModel,
    appearanceSettingsViewModel: AppearanceSettingsViewModel,
    markdownImportViewModel: MarkdownKnowledgeImportViewModel,
    markdownExportViewModel: MarkdownKnowledgeExportViewModel,
    jsonKnowledgeImportViewModel: JsonKnowledgeImportViewModel,
    jsonKnowledgeExportViewModel: JsonKnowledgeExportViewModel,
    chatGptExportImportViewModel: ChatGptExportImportViewModel,
    claudeExportImportViewModel: ClaudeExportImportViewModel,
    p6kZipImportViewModel: P6KZipImportViewModel,
    nanfengKnowledgeExportImportViewModel: NanfengKnowledgeExportImportViewModel,
    pdfTextImportViewModel: PdfTextImportViewModel,
    webTextSnapshotViewModel: WebTextSnapshotViewModel,
    offlineEvalViewModel: OfflineEvalViewModel,
    privacyDataViewModel: PrivacyDataViewModel,
    localBackupRestoreViewModel: LocalBackupRestoreViewModel,
    conversationExchangeExportViewModel: ConversationExchangeExportViewModel,
    workspaceExchangeV2ExportViewModel: WorkspaceExchangeV2ExportViewModel,
    workspaceExchangeV2RestoreViewModel: WorkspaceExchangeV2RestoreViewModel,
    accountSyncViewModel: P7DAccountSyncViewModel,
    dualPathConnectionViewModel: DualPathConnectionViewModel,
    p8ControlledAgentViewModel: P8ControlledAgentViewModel,
    scheduledMonitorViewModel: ScheduledMonitorViewModel,
    navigationViewModel: P5ANavigationViewModel,
    onRouteSelected: (P5ARoute) -> Unit,
    onConversationDrawerChanged: (Boolean) -> Unit,
    onExitSettingsToConversationDrawer: () -> Unit,
) {
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.onPhotoPickerResult(uri)
    }
    val markdownPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "selected.md"
        val mime = context.contentResolver.getType(uri) ?: "text/markdown"
        context.contentResolver.openInputStream(uri)?.use { input ->
            // Bound the untrusted provider stream before any parse or Room write.
            markdownImportViewModel.selectedFile(name, mime, input.readMarkdownBounded((com.nanzhufeng.ai.domain.MARKDOWN_TASK_MAX_BYTES + 1).toInt()))
        }
    }
    val jsonKnowledgePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "knowledge.json"; val mime = context.contentResolver.getType(uri) ?: "application/json"
        context.contentResolver.openInputStream(uri)?.use { input -> jsonKnowledgeImportViewModel.selectedFile(name, mime, input.readMarkdownBounded((com.nanzhufeng.ai.domain.JSON_KNOWLEDGE_MAX_BYTES + 1).toInt())) }
    }
    val chatGptExportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri ?: return@rememberLauncherForActivityResult; val name = uri.lastPathSegment?.substringAfterLast('/') ?: "conversations.json"; val mime = context.contentResolver.getType(uri) ?: "application/json"; context.contentResolver.openInputStream(uri)?.use { chatGptExportImportViewModel.selectedFile(name, mime, it.readMarkdownBounded((com.nanzhufeng.ai.domain.CHATGPT_EXPORT_MAX_BYTES + 1).toInt())) } }
    val claudeExportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri ?: return@rememberLauncherForActivityResult; val name = uri.lastPathSegment?.substringAfterLast('/') ?: "conversations.json"; val mime = context.contentResolver.getType(uri) ?: "application/json"; context.contentResolver.openInputStream(uri)?.use { claudeExportImportViewModel.selectedFile(name, mime, it.readMarkdownBounded((com.nanzhufeng.ai.domain.CLAUDE_EXPORT_MAX_BYTES + 1).toInt())) } }
    val p6kChatGptZipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri ?: return@rememberLauncherForActivityResult; val name = pickerDisplayName(context, uri, "chatgpt-export.zip"); val mime = context.contentResolver.getType(uri) ?: "application/zip"; context.contentResolver.openInputStream(uri)?.let { p6kZipImportViewModel.selected(com.nanzhufeng.ai.domain.ThirdPartyZipProvider.CHATGPT, name, mime, it) } }
    val p6kClaudeZipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri ?: return@rememberLauncherForActivityResult; val name = pickerDisplayName(context, uri, "claude-export.zip"); val mime = context.contentResolver.getType(uri) ?: "application/zip"; context.contentResolver.openInputStream(uri)?.let { p6kZipImportViewModel.selected(com.nanzhufeng.ai.domain.ThirdPartyZipProvider.CLAUDE, name, mime, it) } }
    val nanfengKnowledgeExportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri ?: return@rememberLauncherForActivityResult; val name = uri.lastPathSegment?.substringAfterLast('/') ?: "knowledge-export.json"; val mime = context.contentResolver.getType(uri) ?: "application/json"; context.contentResolver.openInputStream(uri)?.use { nanfengKnowledgeExportImportViewModel.selectedFile(name, mime, it.readMarkdownBounded((com.nanzhufeng.ai.domain.NANFENG_KNOWLEDGE_EXPORT_MAX_BYTES + 1).toInt())) } }
    val pdfTextPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "document.pdf"; val mime = context.contentResolver.getType(uri) ?: "application/pdf"
        context.contentResolver.openInputStream(uri)?.use { input -> pdfTextImportViewModel.selectedFile(name, mime, input.readMarkdownBounded((com.nanzhufeng.ai.domain.PDF_TEXT_MAX_BYTES + 1).toInt())) }
    }
    val localBackupExportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let(localBackupRestoreViewModel::exported) }
    val localBackupImportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(localBackupRestoreViewModel::selected) }
    var pendingConversationExchangeId by remember { mutableStateOf<com.nanzhufeng.ai.domain.ConversationId?>(null) }
    val conversationExchangeExportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val conversationId = pendingConversationExchangeId
        pendingConversationExchangeId = null
        if (uri != null && conversationId != null) conversationExchangeExportViewModel.export(conversationId, uri)
    }
    var pendingWorkspaceExchangeV2Scope by remember { mutableStateOf<com.nanzhufeng.ai.domain.WorkspaceExchangeV2ScopeSummary?>(null) }
    val workspaceExchangeV2ExportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(WORKSPACE_EXCHANGE_V2_DOCUMENT_MIME)) { uri ->
        val scope = pendingWorkspaceExchangeV2Scope
        pendingWorkspaceExchangeV2Scope = null
        if (uri != null && scope != null) workspaceExchangeV2ExportViewModel.export(uri) else workspaceExchangeV2ExportViewModel.clearScope()
    }
    val workspaceExchangeV2RestorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(workspaceExchangeV2RestoreViewModel::selectedDocument)
    }
    val appearance = appearanceSettingsViewModel.state.settings
    val darkAppearance = when (appearance.mode) {
        com.nanzhufeng.ai.domain.AppearanceMode.SYSTEM -> isSystemInDarkTheme()
        com.nanzhufeng.ai.domain.AppearanceMode.LIGHT -> false
        com.nanzhufeng.ai.domain.AppearanceMode.DARK -> true
    }
    val appearanceAccent = appearance.accentColor.toColor()
    val appTypography = remember(appearance.fontSize) {
        Typography().scaledForAppFontSize(appearance.fontSize.scale)
    }
    val activity = LocalActivity.current
    LaunchedEffect(darkAppearance, appearance.accentColor) {
        applyAppearancePalette(darkAppearance)
        // One source drives every non-semantic emphasis: selected service pills, primary
        // saves, drawer selection, conversation lifecycle tiles and the Composer send control.
        AccentOrange = appearanceAccent
        AccentOrangeHover = appearanceAccent.copy(alpha = 0.88f)
        AccentOrangePressed = appearanceAccent.copy(alpha = 0.74f)
        ActionOrange = appearanceAccent
        AccentOrangeSoft = appearance.accentColor.themeContainerColor(darkAppearance)
        DrawerSettingsAccent = appearance.accentColor.drawerSettingsIconColor(darkAppearance)
        ActiveUserBubbleSurface = appearance.accentColor.userBubbleColor(darkAppearance)
        ActiveUserBubbleAccent = appearanceAccent
    }
    SideEffect {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !darkAppearance
                isAppearanceLightNavigationBars = !darkAppearance
            }
        }
    }
    val scheme = if (darkAppearance) {
        darkColorScheme(
            primary = appearanceAccent,
            onPrimary = AccentOnPrimary,
            primaryContainer = appearanceAccent.copy(alpha = 0.28f),
            secondary = appearanceAccent,
            background = PageBackground,
            surface = ForegroundSurface,
            surfaceVariant = ForegroundSurface,
            surfaceContainerLow = ForegroundSurface,
            surfaceContainer = ForegroundSurface,
            surfaceContainerHigh = ForegroundSurface,
            surfaceContainerHighest = ForegroundSurface,
            surfaceTint = Color.Transparent,
            outline = Color.Transparent,
            onBackground = BodyText,
            onSurface = BodyText,
            error = ErrorRed,
        )
    } else lightColorScheme(
            primary = appearanceAccent,
            onPrimary = AccentOnPrimary,
            primaryContainer = appearance.accentColor.userBubbleColor(false),
            secondary = ActionOrange,
            background = PageBackground,
            surface = ForegroundSurface,
            // Material dialogs and popups consume elevated surface-container tokens by
            // default. Keep every app-owned foreground surface pure white; the neutral
            // scrim, not a themed tint, provides modal separation from the transcript.
            surfaceTint = Color.Transparent,
            surfaceVariant = ForegroundSurface,
            surfaceContainerLow = ForegroundSurface,
            surfaceContainer = ForegroundSurface,
            surfaceContainerHigh = ForegroundSurface,
            surfaceContainerHighest = ForegroundSurface,
            // Outlined controls are never used as visual frames in 南枫 AI.  Foreground is
            // established by a white surface and elevation instead of a gray/black border.
            outline = Color.Transparent,
            error = ErrorRed,
            onBackground = BodyText,
            onSurface = BodyText,
        )
    // This must be supplied at the application root rather than per text field.  It keeps
    // selection fill and drag handles in lockstep with the user's chosen accent everywhere:
    // Composer, settings forms, API key fields and plan editors all share one owner.
    val selectionColors = remember(appearanceAccent, darkAppearance) {
        TextSelectionColors(
            handleColor = appearanceAccent,
            backgroundColor = appearanceAccent.copy(alpha = if (darkAppearance) 0.46f else 0.34f),
        )
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = appTypography,
    ) {
        CompositionLocalProvider(
            LocalTextSelectionColors provides selectionColors,
            LocalAppIconScale provides appearance.fontSize.iconScale,
            LocalAppTextScale provides appearance.fontSize.scale,
        ) {
        // Settings can enter a third-level route (Memory, Projects, import, diagnostics and
        // similar workbenches).  Those routes used to fall back to [PageBackground] while the
        // settings home used [SettingsPageBackground], producing a visible dark-gray flash on
        // every level change.  Treat every non-conversation route as one settings canvas; only
        // the conversation surfaces keep their separate reading background.
        val rootRoute = navigationViewModel.state.route
        val usesSettingsCanvas = rootRoute != P5ARoute.CAPTURE && rootRoute != P5ARoute.CONVERSATION
        Surface(
            color = if (usesSettingsCanvas) SettingsPageBackground else PageBackground,
            // Settings owns a custom canvas colour.  Declare its foreground explicitly so
            // every heading and icon which correctly relies on LocalContentColor stays legible
            // in dark mode instead of inheriting the light-theme black default.
            contentColor = BodyText,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (accountSyncViewModel.state.detailVisible) {
                P7DAccountSyncScreen(accountSyncViewModel.state, accountSyncViewModel::close)
                return@Surface
            }
            Box(modifier = Modifier.fillMaxSize()) {
            if (dualPathConnectionViewModel.state.detailVisible) {
                DualPathConnectionDialog(
                    dualPathConnectionViewModel.state,
                    dualPathConnectionViewModel::close,
                    dualPathConnectionViewModel::selectLocal,
                    dualPathConnectionViewModel::requestOnlineGuidance,
                )
            }
            P5AAdaptiveScaffold(
                route = navigationViewModel.state.route,
                onRouteSelected = onRouteSelected,
            ) { windowLayout ->
                CaptureScreen(
                state = viewModel.state,
                onChooseImage = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onSaveText = { text -> viewModel.onTextInput(com.nanzhufeng.ai.domain.CaptureTextInput(
                    text = text,
                    sourceType = com.nanzhufeng.ai.domain.CaptureSourceType.MANUAL_TEXT,
                )) },
                onDismissMessage = viewModel::clearMessage,
                onRequestAi = viewModel::requestAiConfirmation,
                modelSettingsState = modelSettingsViewModel.state,
                onSaveModelSettings = modelSettingsViewModel::save,
                onSaveChatRoutingPolicy = modelSettingsViewModel::saveRoutingPolicy,
                onRevealModelCredential = modelSettingsViewModel::revealStoredCredential,
                onTestModelConnection = modelSettingsViewModel::testConnection,
                invocationLedgerState = invocationLedgerViewModel.state,
                onLoadInvocationLedger = invocationLedgerViewModel::load,
                conversationCostLedgerState = conversationCostLedgerViewModel.state,
                onLoadConversationCostLedger = conversationCostLedgerViewModel::load,
                privacyDataState = privacyDataViewModel.state,
                privacyDataViewModel = privacyDataViewModel,
                onOpenPrivacyData = privacyDataViewModel::show,
                localBackupState = localBackupRestoreViewModel.state,
                localBackupViewModel = localBackupRestoreViewModel,
                onExportLocalBackup = { localBackupExportPicker.launch("nanfeng-ai-local-backup.nfai-backup") },
                onImportLocalBackup = { localBackupImportPicker.launch(arrayOf("application/zip", "application/octet-stream")) },
                workspaceExchangeV2ExportState = workspaceExchangeV2ExportViewModel.state,
                onSelectWorkspaceExchangeV2Scope = workspaceExchangeV2ExportViewModel::selectCompleteWorkspaceScope,
                workspaceExchangeV2RestoreState = workspaceExchangeV2RestoreViewModel.state,
                onSelectWorkspaceExchangeV2RestoreDocument = {
                    workspaceExchangeV2RestorePicker.launch(arrayOf(WORKSPACE_EXCHANGE_V2_DOCUMENT_MIME, "application/x-zip-compressed", "application/octet-stream"))
                },
                accountSyncState = accountSyncViewModel.state,
                onOpenAccountSync = accountSyncViewModel::open,
                dualPathState = dualPathConnectionViewModel.state,
                onOpenDualPath = dualPathConnectionViewModel::open,
                onOpenP8ControlledAgent = p8ControlledAgentViewModel::show,
                knowledgeLibraryState = knowledgeLibraryViewModel.state,
                knowledgeLibraryViewModel = knowledgeLibraryViewModel,
                onOpenKnowledgeLibrary = knowledgeLibraryViewModel::showDialog,
                onOpenKnowledgeExport = knowledgeExportViewModel::showDialog,
                onOpenMarkdownImport = { markdownImportViewModel.showDialog(); markdownPicker.launch(arrayOf("text/markdown", "text/plain", "application/octet-stream")) },
                onOpenMarkdownExport = markdownExportViewModel::show,
                onOpenJsonKnowledgeImport = { jsonKnowledgeImportViewModel.show(); jsonKnowledgePicker.launch(arrayOf("application/json", "text/json", "application/octet-stream")) },
                onOpenJsonKnowledgeExport = jsonKnowledgeExportViewModel::show,
                onOpenChatGptExportImport = { chatGptExportImportViewModel.show(); chatGptExportPicker.launch(arrayOf("application/json", "text/json")) },
                chatGptImportState = chatGptExportImportViewModel.state,
                onOpenChatGptImportTask = chatGptExportImportViewModel::open,
                onBackChatGptImportTask = chatGptExportImportViewModel::back,
                onConfirmChatGptImportItem = chatGptExportImportViewModel::confirm,
                onSkipChatGptImportItem = chatGptExportImportViewModel::skip,
                onRetryChatGptImportTask = chatGptExportImportViewModel::retry,
                onCancelChatGptImport = chatGptExportImportViewModel::cancel,
                onOpenClaudeExportImport = { claudeExportImportViewModel.show(); claudeExportPicker.launch(arrayOf("application/json", "text/json")) },
                claudeImportState = claudeExportImportViewModel.state,
                onOpenClaudeImportTask = claudeExportImportViewModel::open,
                onBackClaudeImportTask = claudeExportImportViewModel::back,
                onConfirmClaudeImportItem = claudeExportImportViewModel::confirm,
                onSkipClaudeImportItem = claudeExportImportViewModel::skip,
                onRetryClaudeImportTask = claudeExportImportViewModel::retry,
                onCancelClaudeImport = claudeExportImportViewModel::cancel,
                p6kZipImportState = p6kZipImportViewModel.state,
                // Keep the system chooser honest: this import accepts ZIP archives only. The
                // staged file still passes the filename, app-private-copy and strict
                // central-directory checks in P6-K after the user selects it.
                onOpenP6KChatGptZip = { p6kZipImportViewModel.show(); p6kChatGptZipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed")) },
                onOpenP6KClaudeZip = { p6kZipImportViewModel.show(); p6kClaudeZipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed")) },
                onClearP6KZip = p6kZipImportViewModel::clear,
                onRetryP6KZip = p6kZipImportViewModel::retry,
                onViewP6KZip = p6kZipImportViewModel::view,
                onSelectP6KAsset = p6kZipImportViewModel::selectAsset,
                onSelectP6KTarget = p6kZipImportViewModel::selectTarget,
                onLinkP6KAsset = p6kZipImportViewModel::link,
                onOpenNanfengKnowledgeExportImport = { nanfengKnowledgeExportImportViewModel.show(); nanfengKnowledgeExportPicker.launch(arrayOf("application/json", "text/json")) },
                nanfengKnowledgeImportState = nanfengKnowledgeExportImportViewModel.state,
                onOpenNanfengKnowledgeImportTask = nanfengKnowledgeExportImportViewModel::open,
                onBackNanfengKnowledgeImportTask = nanfengKnowledgeExportImportViewModel::back,
                onConfirmNanfengKnowledgeImportItem = nanfengKnowledgeExportImportViewModel::confirm,
                onSkipNanfengKnowledgeImportItem = nanfengKnowledgeExportImportViewModel::skip,
                onRetryNanfengKnowledgeImportTask = nanfengKnowledgeExportImportViewModel::retry,
                onCancelNanfengKnowledgeImport = nanfengKnowledgeExportImportViewModel::cancel,
                onOpenPdfTextImport = { pdfTextImportViewModel.show(); pdfTextPicker.launch(arrayOf("application/pdf")) },
                onOpenWebTextSnapshot = webTextSnapshotViewModel::show,
                onOpenOfflineEval = offlineEvalViewModel::show,
                offlineEvalState = offlineEvalViewModel.state,
                offlineEvalViewModel = offlineEvalViewModel,
                conversationState = conversationFoundationViewModel.state,
                conversationViewModel = conversationFoundationViewModel,
                scheduledMonitorViewModel = scheduledMonitorViewModel,
                projectState = projectViewModel.state,
                    projectViewModel = projectViewModel,
                    memoryViewModel = memoryViewModel,
                contextBodySelectionViewModel = contextBodySelectionViewModel,
                assistantExperienceSettingsState = assistantExperienceSettingsViewModel.state,
                onUpdateAssistantExperienceSettings = assistantExperienceSettingsViewModel::update,
                notificationReminderSettingsState = notificationReminderSettingsViewModel.state,
                onUpdateNotificationReminderSettings = notificationReminderSettingsViewModel::update,
                appearanceSettingsState = appearanceSettingsViewModel.state,
                onUpdateAppearanceSettings = appearanceSettingsViewModel::update,
                onCreateDevelopmentConversation = conversationFoundationViewModel::createDevelopmentConversation,
                onStartDeterministicLocalStream = conversationFoundationViewModel::startDeterministicLocalStream,
                onStopLocalStream = conversationFoundationViewModel::stopLocalStream,
                onContinueLocalAnswer = { conversationFoundationViewModel.performAction(com.nanzhufeng.ai.domain.ConversationActionKind.CONTINUE) },
                onRetryLocalAnswer = { conversationFoundationViewModel.performAction(com.nanzhufeng.ai.domain.ConversationActionKind.RETRY) },
                onChangeLocalModel = { preset -> conversationFoundationViewModel.performAction(com.nanzhufeng.ai.domain.ConversationActionKind.CHANGE_MODEL, preset) },
                onSwitchConversationBranch = conversationFoundationViewModel::switchToBranch,
                route = navigationViewModel.state.route,
                windowLayout = windowLayout,
                onRouteSelected = onRouteSelected,
                conversationDrawerOpen = navigationViewModel.state.conversationDrawerOpen,
                onConversationDrawerChanged = onConversationDrawerChanged,
                onReturnToConversationDrawer = onExitSettingsToConversationDrawer,
            )
            }
            viewModel.state.requestPreview?.let { preview ->
                AiEgressConfirmationDialog(
                    preview = preview,
                    consentChecked = viewModel.state.isConsentChecked,
                    onConsentChecked = viewModel::setEgressConsentChecked,
                    onDismiss = viewModel::dismissAiConfirmation,
                    onConfirm = viewModel::confirmAndRunAi,
                )
            }
            viewModel.state.pendingCandidate?.let { candidate ->
                CandidateReviewDialog(
                    candidate = candidate,
                    isSaving = viewModel.state.isSavingCandidate,
                    onDismiss = { /* A candidate remains pending until the user saves or cancels it. */ },
                    onDiscard = viewModel::discardCandidate,
                    onSave = viewModel::saveCandidate,
                )
            }
            workspaceExchangeV2ExportViewModel.state.scope?.let { scope ->
                WorkspaceExchangeV2ScopeDialog(
                    scope = scope,
                    onDismiss = workspaceExchangeV2ExportViewModel::clearScope,
                    onExport = { onScope ->
                        pendingWorkspaceExchangeV2Scope = onScope
                        workspaceExchangeV2ExportPicker.launch(WORKSPACE_EXCHANGE_V2_SUGGESTED_DISPLAY_NAME)
                    },
                )
            }
            if (knowledgeLibraryViewModel.state.relationshipUi.builderVisible) {
                KnowledgeRelationshipBuilderDialog(
                    state = knowledgeLibraryViewModel.state.relationshipUi,
                    onDismiss = knowledgeLibraryViewModel::dismissRelationshipBuilder,
                    onType = knowledgeLibraryViewModel::selectRelationshipType,
                    onTarget = knowledgeLibraryViewModel::selectRelationshipTarget,
                    onConfirm = knowledgeLibraryViewModel::confirmRelationship,
                )
            }
            if (knowledgeLibraryViewModel.state.relationshipUi.listVisible) {
                KnowledgeRelationshipListDialog(
                    state = knowledgeLibraryViewModel.state.relationshipUi,
                    onDismiss = knowledgeLibraryViewModel::dismissRelationshipList,
                    onStatus = knowledgeLibraryViewModel::showRelationshipList,
                    onRevoke = knowledgeLibraryViewModel::revokeRelationship,
                )
            }
            if (knowledgeExportViewModel.state.dialogVisible) {
                KnowledgeExportDialog(
                    state = knowledgeExportViewModel.state,
                    onDismiss = knowledgeExportViewModel::dismissDialog,
                    onExport = knowledgeExportViewModel::export,
                )
            }
            if (markdownImportViewModel.state.dialogVisible) {
                MarkdownKnowledgeImportDialog(
                    state = markdownImportViewModel.state, onDismiss = markdownImportViewModel::dismiss, onBack = markdownImportViewModel::back,
                    onOpen = markdownImportViewModel::open, onRetry = markdownImportViewModel::retry, onEdit = markdownImportViewModel::edit,
                    onTitle = { markdownImportViewModel.update(title = it) }, onBody = { markdownImportViewModel.update(body = it) }, onTags = { markdownImportViewModel.update(tags = it) },
                    onConfirm = markdownImportViewModel::confirm, onSkip = markdownImportViewModel::skip, onCancel = markdownImportViewModel::cancel,
                )
            }
            if (markdownExportViewModel.state.visible) MarkdownKnowledgeExportDialog(markdownExportViewModel.state, markdownExportViewModel::dismiss, markdownExportViewModel::toggle, markdownExportViewModel::export)
            if (jsonKnowledgeImportViewModel.state.visible) JsonKnowledgeImportDialog(jsonKnowledgeImportViewModel.state, jsonKnowledgeImportViewModel::dismiss, jsonKnowledgeImportViewModel::back, jsonKnowledgeImportViewModel::open, jsonKnowledgeImportViewModel::retry, jsonKnowledgeImportViewModel::edit, { a, b, c -> jsonKnowledgeImportViewModel.update(a, b, c) }, jsonKnowledgeImportViewModel::confirm, jsonKnowledgeImportViewModel::skip, jsonKnowledgeImportViewModel::cancel)
            if (jsonKnowledgeExportViewModel.state.visible) JsonKnowledgeExportDialog(jsonKnowledgeExportViewModel.state, jsonKnowledgeExportViewModel::dismiss, jsonKnowledgeExportViewModel::toggle, jsonKnowledgeExportViewModel::export)
            if (pdfTextImportViewModel.state.visible) PdfTextImportDialog(pdfTextImportViewModel.state, pdfTextImportViewModel::dismiss, pdfTextImportViewModel::back, pdfTextImportViewModel::open, pdfTextImportViewModel::retry, pdfTextImportViewModel::edit, { a, b, c -> pdfTextImportViewModel.update(a, b, c) }, pdfTextImportViewModel::confirm, pdfTextImportViewModel::skip, pdfTextImportViewModel::cancel)
            if (webTextSnapshotViewModel.state.visible) WebTextSnapshotDialog(webTextSnapshotViewModel.state, webTextSnapshotViewModel::dismiss, webTextSnapshotViewModel::back, webTextSnapshotViewModel::open, webTextSnapshotViewModel::updateUrl, webTextSnapshotViewModel::setConfirmed, webTextSnapshotViewModel::start, webTextSnapshotViewModel::retry, webTextSnapshotViewModel::confirm, webTextSnapshotViewModel::skip, webTextSnapshotViewModel::cancel)
            if (contextBodySelectionViewModel.state.dialogVisible) ContextBodySelectionDialog(contextBodySelectionViewModel.state, contextBodySelectionViewModel)
            if (p8ControlledAgentViewModel.state.visible) P8ControlledAgentDialog(p8ControlledAgentViewModel.state, p8ControlledAgentViewModel::dismiss, p8ControlledAgentViewModel::begin, p8ControlledAgentViewModel::confirm, p8ControlledAgentViewModel::pause, p8ControlledAgentViewModel::resume, p8ControlledAgentViewModel::cancel)
            if (scheduledMonitorViewModel.state.visible) ScheduledMonitorDialog(
                state = scheduledMonitorViewModel.state,
                onDismiss = scheduledMonitorViewModel::dismiss,
                onStartCreate = scheduledMonitorViewModel::startCreate,
                onCancelCreate = scheduledMonitorViewModel::cancelCreate,
                onTitleChanged = scheduledMonitorViewModel::updateTitle,
                onInstructionChanged = scheduledMonitorViewModel::updateInstruction,
                onCadenceChanged = scheduledMonitorViewModel::updateCadence,
                onCreate = scheduledMonitorViewModel::create,
                onPause = scheduledMonitorViewModel::setPaused,
                onDelete = scheduledMonitorViewModel::delete,
                onRequestNotifications = { notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS) },
            )
            assistantExperienceSettingsViewModel.state.notice?.let { notice ->
                CenteredPersonalizationSaveNotice(
                    notice = notice,
                    onDismiss = assistantExperienceSettingsViewModel::clearNotice,
                )
            }
            }
        }
    }
}
}

@Composable
private fun CaptureScreen(
    state: CaptureScreenState,
    onChooseImage: () -> Unit,
    onSaveText: (String) -> Unit,
    onDismissMessage: () -> Unit,
    onRequestAi: () -> Unit,
    modelSettingsState: ModelSettingsUiState,
    onSaveModelSettings: (ProviderId, Boolean, ModelPresetId, String?) -> Unit,
    onSaveChatRoutingPolicy: (com.nanzhufeng.ai.domain.ChatRoutingPolicy) -> Unit,
    onRevealModelCredential: (ProviderId) -> Unit,
    onTestModelConnection: (ProviderId) -> Unit,
    invocationLedgerState: InvocationLedgerUiState,
    onLoadInvocationLedger: () -> Unit,
    conversationCostLedgerState: ConversationCostLedgerUiState,
    onLoadConversationCostLedger: () -> Unit,
    privacyDataState: PrivacyDataUiState,
    privacyDataViewModel: PrivacyDataViewModel,
    onOpenPrivacyData: () -> Unit,
    localBackupState: LocalBackupUiState,
    localBackupViewModel: LocalBackupRestoreViewModel,
    onExportLocalBackup: () -> Unit,
    onImportLocalBackup: () -> Unit,
    workspaceExchangeV2ExportState: WorkspaceExchangeV2ExportUiState,
    onSelectWorkspaceExchangeV2Scope: () -> Unit,
    workspaceExchangeV2RestoreState: WorkspaceExchangeV2RestoreUiState,
    onSelectWorkspaceExchangeV2RestoreDocument: () -> Unit,
    accountSyncState: P7DAccountSyncUiState,
    onOpenAccountSync: () -> Unit,
    dualPathState: DualPathConnectionUiState,
    onOpenDualPath: () -> Unit,
    onOpenP8ControlledAgent: () -> Unit,
    knowledgeLibraryState: KnowledgeLibraryUiState,
    knowledgeLibraryViewModel: KnowledgeLibraryViewModel,
    onOpenKnowledgeLibrary: () -> Unit,
    onOpenKnowledgeExport: () -> Unit,
    onOpenMarkdownImport: () -> Unit,
    onOpenMarkdownExport: () -> Unit,
    onOpenJsonKnowledgeImport: () -> Unit,
    onOpenJsonKnowledgeExport: () -> Unit,
    onOpenChatGptExportImport: () -> Unit,
    chatGptImportState: ChatGptImportUiState,
    onOpenChatGptImportTask: (ChatGptImportTask) -> Unit,
    onBackChatGptImportTask: () -> Unit,
    onConfirmChatGptImportItem: (ChatGptImportItemId) -> Unit,
    onSkipChatGptImportItem: (ChatGptImportItemId) -> Unit,
    onRetryChatGptImportTask: (ChatGptImportTaskId) -> Unit,
    onCancelChatGptImport: () -> Unit,
    onOpenClaudeExportImport: () -> Unit,
    claudeImportState: ClaudeImportUiState,
    onOpenClaudeImportTask: (ClaudeImportTask) -> Unit,
    onBackClaudeImportTask: () -> Unit,
    onConfirmClaudeImportItem: (ClaudeImportItemId) -> Unit,
    onSkipClaudeImportItem: (ClaudeImportItemId) -> Unit,
    onRetryClaudeImportTask: (ClaudeImportTaskId) -> Unit,
    onCancelClaudeImport: () -> Unit,
    p6kZipImportState: P6KZipImportUiState,
    onOpenP6KChatGptZip: () -> Unit,
    onOpenP6KClaudeZip: () -> Unit,
    onClearP6KZip: (String) -> Unit,
    onRetryP6KZip: (String) -> Unit,
    onViewP6KZip: (String) -> Unit,
    onSelectP6KAsset: (String) -> Unit,
    onSelectP6KTarget: (P6KZipManualLinkTarget) -> Unit,
    onLinkP6KAsset: () -> Unit,
    onOpenNanfengKnowledgeExportImport: () -> Unit,
    nanfengKnowledgeImportState: NanfengKnowledgeImportUiState,
    onOpenNanfengKnowledgeImportTask: (NanfengKnowledgeImportTask) -> Unit,
    onBackNanfengKnowledgeImportTask: () -> Unit,
    onConfirmNanfengKnowledgeImportItem: (NanfengKnowledgeImportItemId) -> Unit,
    onSkipNanfengKnowledgeImportItem: (NanfengKnowledgeImportItemId) -> Unit,
    onRetryNanfengKnowledgeImportTask: (NanfengKnowledgeImportTaskId) -> Unit,
    onCancelNanfengKnowledgeImport: () -> Unit,
    onOpenPdfTextImport: () -> Unit,
    onOpenWebTextSnapshot: () -> Unit,
    onOpenOfflineEval: () -> Unit,
    offlineEvalState: OfflineEvalUiState,
    offlineEvalViewModel: OfflineEvalViewModel,
    conversationState: ConversationFoundationUiState,
    conversationViewModel: ConversationFoundationViewModel,
    scheduledMonitorViewModel: ScheduledMonitorViewModel,
    projectState: ProjectUiState,
    projectViewModel: ProjectViewModel,
    memoryViewModel: MemoryViewModel,
    contextBodySelectionViewModel: ContextBodySelectionViewModel,
    assistantExperienceSettingsState: AssistantExperienceSettingsUiState,
    onUpdateAssistantExperienceSettings: ((com.nanzhufeng.ai.domain.AssistantExperienceSettings) -> com.nanzhufeng.ai.domain.AssistantExperienceSettings) -> Unit,
    notificationReminderSettingsState: NotificationReminderSettingsUiState,
    onUpdateNotificationReminderSettings: ((com.nanzhufeng.ai.domain.NotificationReminderSettings) -> com.nanzhufeng.ai.domain.NotificationReminderSettings) -> Unit,
    appearanceSettingsState: AppearanceSettingsUiState,
    onUpdateAppearanceSettings: ((com.nanzhufeng.ai.domain.AppearanceSettings) -> com.nanzhufeng.ai.domain.AppearanceSettings) -> Unit,
    onCreateDevelopmentConversation: () -> Unit,
    onStartDeterministicLocalStream: () -> Unit,
    onStopLocalStream: () -> Unit,
    onContinueLocalAnswer: () -> Unit,
    onRetryLocalAnswer: () -> Unit,
    onChangeLocalModel: (com.nanzhufeng.ai.domain.ModelPresetId) -> Unit,
    onSwitchConversationBranch: (com.nanzhufeng.ai.domain.MessageNodeId) -> Unit,
    route: P5ARoute,
    windowLayout: P5AWindowLayout,
    onRouteSelected: (P5ARoute) -> Unit,
    conversationDrawerOpen: Boolean,
    onConversationDrawerChanged: (Boolean) -> Unit,
    onReturnToConversationDrawer: () -> Unit,
) {
    val expanded = windowLayout == P5AWindowLayout.EXPANDED
    var settingsDestination by remember { mutableStateOf(SettingsDestination.HOME) }
    var personalizationDraft by remember { mutableStateOf(assistantExperienceSettingsState.settings) }
    var personalizationDirty by remember { mutableStateOf(false) }
    val settingsRoot = SettingsNavigationEntry(P5ARoute.SETTINGS, SettingsDestination.HOME)
    var settingsNavigationStack by remember { mutableStateOf(listOf(settingsRoot)) }
    // A lifecycle row opens its real conversation without discarding the list that supplied it.
    // The destination remains the explicit return owner until the user backs out of that chat.
    var lifecycleConversationReturnDestination by rememberSaveable { mutableStateOf<SettingsDestination?>(null) }
    // Settings child routes can leave the shared scroll container entirely. Keep one viewport
    // per settings level above that branch, so returning to the home menu restores its position.
    val settingsScrollStates = remember { mutableMapOf<SettingsDestination, androidx.compose.foundation.ScrollState>() }
    val settingsScrollState = settingsScrollStates.getOrPut(settingsDestination) {
        androidx.compose.foundation.ScrollState(initial = 0)
    }
    fun openSettingsLevel(route: P5ARoute, destination: SettingsDestination) {
        val next = SettingsNavigationEntry(route, destination)
        if (settingsNavigationStack.lastOrNull() == next) return
        settingsNavigationStack = settingsNavigationStack + next
        settingsDestination = destination
        onRouteSelected(route)
    }
    fun openPersonalizationSettings() {
        personalizationDraft = assistantExperienceSettingsState.settings
        personalizationDirty = false
        openSettingsLevel(P5ARoute.SETTINGS, SettingsDestination.PERSONALIZATION)
    }
    val savePersonalizationDraft: () -> Unit = {
        onUpdateAssistantExperienceSettings { personalizationDraft }
        personalizationDirty = false
    }
    val settingsOwnsCurrentRoute = settingsNavigationStack.lastOrNull()?.route == route
    fun lifecycleScope(destination: SettingsDestination): com.nanzhufeng.ai.domain.ConversationListScope = when (destination) {
        SettingsDestination.FAVORITE_CONVERSATIONS -> com.nanzhufeng.ai.domain.ConversationListScope.FAVORITES
        SettingsDestination.ARCHIVED_CONVERSATIONS -> com.nanzhufeng.ai.domain.ConversationListScope.ARCHIVED
        SettingsDestination.RECYCLE_BIN -> com.nanzhufeng.ai.domain.ConversationListScope.DELETED
        else -> com.nanzhufeng.ai.domain.ConversationListScope.ACTIVE
    }
    val returnToLifecycleList: (() -> Unit)? = lifecycleConversationReturnDestination?.let { destination ->
        {
            lifecycleConversationReturnDestination = null
            conversationViewModel.setListScope(lifecycleScope(destination))
            settingsDestination = destination
            onRouteSelected(P5ARoute.SETTINGS)
        }
    }
    val returnFromSettings: () -> Unit = {
        when {
            // A selected import task is a level below the import centre. It must close first,
            // before the settings route itself can move back to its parent.
            route == P5ARoute.ADAPTERS && chatGptImportState.selected != null -> onBackChatGptImportTask()
            route == P5ARoute.ADAPTERS && claudeImportState.selected != null -> onBackClaudeImportTask()
            route == P5ARoute.ADAPTERS && nanfengKnowledgeImportState.selected != null -> onBackNanfengKnowledgeImportTask()
            settingsOwnsCurrentRoute && settingsNavigationStack.size > 1 -> {
                settingsNavigationStack = settingsNavigationStack.dropLast(1)
                val parent = settingsNavigationStack.last()
                settingsDestination = parent.destination
                onRouteSelected(parent.route)
            }
            else -> {
                // Management's archived/recycle-bin projection is settings-only. Never let it
                // leak back into the drawer and make ordinary conversations look missing.
                settingsNavigationStack = listOf(settingsRoot)
                settingsDestination = SettingsDestination.HOME
                lifecycleConversationReturnDestination = null
                conversationViewModel.setListScope(com.nanzhufeng.ai.domain.ConversationListScope.ACTIVE)
                onReturnToConversationDrawer()
            }
        }
    }
    // The chat root owns its own LazyColumn. It must not inherit the settings/workspace
    // verticalScroll container, otherwise Compose measures the transcript at infinity.
    if (route == P5ARoute.CAPTURE || route == P5ARoute.CONVERSATION) {
        ConversationFoundationCard(
            conversationState,
            conversationViewModel,
            projectState,
            projectViewModel,
            memoryViewModel,
            assistantExperienceSettingsState.settings.memoryEnabled,
            contextBodySelectionViewModel,
            scheduledMonitorViewModel,
            notificationReminderSettingsState.settings,
            onRouteSelected,
            conversationDrawerOpen,
            onConversationDrawerChanged,
            onReturnToLifecycleList = returnToLifecycleList,
        )
        return
    }
    if (route == P5ARoute.MEMORY) {
        BackHandler(onBack = returnFromSettings)
        // Memory is a settings child page. It must not return before the shared 90% type scale,
        // otherwise its title, summary and bottom input visibly grow beyond the settings system.
        SettingsTextScale {
            MemorySummaryPage(
                state = memoryViewModel.state,
                onBack = returnFromSettings,
                onRefresh = memoryViewModel::refreshSummary,
                onDeleteMemory = memoryViewModel::clearSummary,
                onDisableMemorySummaryGenerationAndUse = {
                    personalizationDraft = personalizationDraft.copy(memoryRetrievalEnabled = false)
                    personalizationDirty = false
                    onUpdateAssistantExperienceSettings { current ->
                        current.copy(memoryRetrievalEnabled = false)
                    }
                    memoryViewModel.markSummaryGenerationAndUseDisabled()
                },
                onQuerySummary = memoryViewModel::querySummary,
                onAppendSummaryUpdate = memoryViewModel::appendSummaryUpdate,
            )
        }
        return
    }
    Column(
        modifier = Modifier
            .widthIn(max = 1280.dp)
            .fillMaxSize()
            .then(if (settingsOwnsCurrentRoute) Modifier.systemGestureExclusion() else Modifier)
            .then(if (settingsOwnsCurrentRoute) Modifier.settingsEdgeExit(returnFromSettings) else Modifier)
            .verticalScroll(settingsScrollState)
            .padding(start = if (expanded) 32.dp else 20.dp, end = if (expanded) 32.dp else 20.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (settingsOwnsCurrentRoute) {
            // Every settings return shares one stack: detail → parent route/category → settings
            // home → drawer. The bottom system gesture remains untouched because no bottom
            // exclusion is set.
            BackHandler(onBack = returnFromSettings)
        }
        SettingsTextScale {
            if (route == P5ARoute.SETTINGS) {
                SettingsPageHeader(
                    destination = settingsDestination,
                    onBack = returnFromSettings,
                    onSave = if (settingsDestination == SettingsDestination.PERSONALIZATION) {
                        savePersonalizationDraft
                    } else null,
                    saveEnabled = personalizationDirty,
                )
            } else if (route != P5ARoute.CAPTURE && route != P5ARoute.CONVERSATION) {
                val returnToSettingsParent = settingsOwnsCurrentRoute && settingsNavigationStack.size > 1
                val returnRoute = if (route == P5ARoute.CONTROL) P5ARoute.SETTINGS else P5ARoute.CONVERSATION
                TextButton(
                    onClick = { if (returnToSettingsParent) returnFromSettings() else onRouteSelected(returnRoute) },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(if (returnToSettingsParent) "返回上一级" else if (route == P5ARoute.CONTROL) "返回设置" else "返回对话")
                }
                Header(route)
            }
            when (route) {
            P5ARoute.CAPTURE, P5ARoute.CONVERSATION -> error("chat routes return before the settings scroll container")
            P5ARoute.KNOWLEDGE -> WorkbenchRoute(expanded) {
                KnowledgeLibraryPage(
                    state = knowledgeLibraryState,
                    onOpenDetail = knowledgeLibraryViewModel::openDetail,
                    onBackToList = knowledgeLibraryViewModel::backToList,
                    onSearch = knowledgeLibraryViewModel::updateSearch,
                    onStatus = knowledgeLibraryViewModel::setStatus,
                    onSource = knowledgeLibraryViewModel::setSourceType,
                    onArchiveRestore = knowledgeLibraryViewModel::archiveOrRestore,
                    onDeleteRestore = knowledgeLibraryViewModel::deleteOrRestoreTrash,
                    onStartCreate = knowledgeLibraryViewModel::startCreate,
                    onStartEdit = knowledgeLibraryViewModel::startEdit,
                    onEditTitle = { knowledgeLibraryViewModel.updateEdit(title = it) },
                    onEditBody = { knowledgeLibraryViewModel.updateEdit(body = it) },
                    onEditTags = { knowledgeLibraryViewModel.updateEdit(tags = it) },
                    onCancelEdit = knowledgeLibraryViewModel::cancelEdit,
                    onSaveEdit = knowledgeLibraryViewModel::saveEdit,
                    onFindDuplicateCandidates = knowledgeLibraryViewModel::findDuplicateCandidates,
                    onStartRelationshipBuilder = knowledgeLibraryViewModel::startRelationshipBuilder,
                    onShowRelationshipList = knowledgeLibraryViewModel::showRelationshipList,
                )
            }
            P5ARoute.PROJECTS -> WorkbenchRoute(expanded) { ProjectWorkspacePage(projectState, projectViewModel) }
            P5ARoute.MEMORY -> error("memory returns before the settings scroll container")
            P5ARoute.CONTEXT -> WorkbenchRoute(expanded) { ContextControlCard(conversationState, contextBodySelectionViewModel) }
            P5ARoute.EVAL -> WorkbenchRoute(expanded) {
                OfflineEvalPage(
                    state = offlineEvalState,
                    onStart = offlineEvalViewModel::start,
                    onSelect = offlineEvalViewModel::select,
                    onExport = offlineEvalViewModel::export,
                    onAlias = offlineEvalViewModel::alias,
                    onCase = offlineEvalViewModel::selectCase,
                    onScore = offlineEvalViewModel::score,
                    onDimension = offlineEvalViewModel::rotateDimension,
                    onSaveScore = offlineEvalViewModel::saveScore,
                    comparison = offlineEvalViewModel.comparison(),
                )
            }
            P5ARoute.SETTINGS -> SettingsHierarchy(
                destination = settingsDestination,
                expanded = expanded,
                assistantExperienceSettingsState = assistantExperienceSettingsState,
                onUpdateAssistantExperienceSettings = onUpdateAssistantExperienceSettings,
                personalizationDraft = personalizationDraft,
                onUpdatePersonalizationDraft = { transform ->
                    personalizationDraft = transform(personalizationDraft)
                    personalizationDirty = personalizationDraft != assistantExperienceSettingsState.settings
                },
                onSavePersonalization = savePersonalizationDraft,
                onOpenPersonalization = ::openPersonalizationSettings,
                notificationReminderSettingsState = notificationReminderSettingsState,
                onUpdateNotificationReminderSettings = onUpdateNotificationReminderSettings,
                appearanceSettingsState = appearanceSettingsState,
                onUpdateAppearanceSettings = onUpdateAppearanceSettings,
                onOpenMemoryManager = {
                    memoryViewModel.showDialog()
                    openSettingsLevel(P5ARoute.MEMORY, SettingsDestination.PERSONALIZATION)
                },
                modelSettingsState = modelSettingsState,
                onSaveModelSettings = onSaveModelSettings,
                onSaveChatRoutingPolicy = onSaveChatRoutingPolicy,
                onRevealModelCredential = onRevealModelCredential,
                onTestModelConnection = onTestModelConnection,
                invocationLedgerState = invocationLedgerState,
                onLoadInvocationLedger = onLoadInvocationLedger,
                conversationCostLedgerState = conversationCostLedgerState,
                onLoadConversationCostLedger = onLoadConversationCostLedger,
                conversationState = conversationState,
                conversationViewModel = conversationViewModel,
                projectState = projectState,
                onOpenProjectManager = {
                    projectViewModel.showDialog()
                    openSettingsLevel(P5ARoute.PROJECTS, SettingsDestination.WORKSPACE)
                },
                knowledgeLibraryState = knowledgeLibraryState,
                onOpenKnowledgeLibrary = {
                    onOpenKnowledgeLibrary()
                    openSettingsLevel(P5ARoute.KNOWLEDGE, SettingsDestination.WORKSPACE)
                },
                onOpenContext = { openSettingsLevel(P5ARoute.CONTEXT, SettingsDestination.DEVELOPMENT) },
                onOpenOfflineEval = {
                    onOpenOfflineEval()
                    openSettingsLevel(P5ARoute.EVAL, SettingsDestination.DEVELOPMENT)
                },
                privacyDataState = privacyDataState,
                privacyDataViewModel = privacyDataViewModel,
                onOpenPrivacyData = onOpenPrivacyData,
                dataStorageImportContent = {
                    DataImportCenterContent(
                        chatGptImportState = chatGptImportState,
                        onChooseChatGptImport = onOpenChatGptExportImport,
                        onOpenChatGptImportTask = onOpenChatGptImportTask,
                        onBackChatGptImportTask = onBackChatGptImportTask,
                        onRetryChatGptImportTask = onRetryChatGptImportTask,
                        onCancelChatGptImport = onCancelChatGptImport,
                        claudeImportState = claudeImportState,
                        onChooseClaudeImport = onOpenClaudeExportImport,
                        onOpenClaudeImportTask = onOpenClaudeImportTask,
                        onBackClaudeImportTask = onBackClaudeImportTask,
                        onRetryClaudeImportTask = onRetryClaudeImportTask,
                        onCancelClaudeImport = onCancelClaudeImport,
                        p6kZipImportState = p6kZipImportState,
                        onOpenP6KChatGptZip = onOpenP6KChatGptZip,
                        onOpenP6KClaudeZip = onOpenP6KClaudeZip,
                        onClearP6KZip = onClearP6KZip,
                        onRetryP6KZip = onRetryP6KZip,
                        onViewP6KZip = onViewP6KZip,
                        onSelectP6KAsset = onSelectP6KAsset,
                        onSelectP6KTarget = onSelectP6KTarget,
                        onLinkP6KAsset = onLinkP6KAsset,
                        onOpenJsonImportResults = { openSettingsLevel(P5ARoute.SETTINGS, SettingsDestination.JSON_IMPORT_RESULTS) },
                        onOpenZipImportResults = { openSettingsLevel(P5ARoute.SETTINGS, SettingsDestination.ZIP_IMPORT_RESULTS) },
                        workspaceExchangeV2ExportState = workspaceExchangeV2ExportState,
                        onSelectWorkspaceExchangeV2Scope = onSelectWorkspaceExchangeV2Scope,
                        workspaceExchangeV2RestoreState = workspaceExchangeV2RestoreState,
                        onSelectWorkspaceExchangeV2RestoreDocument = onSelectWorkspaceExchangeV2RestoreDocument,
                    )
                },
                jsonImportResultsContent = {
                    ImportResultsDetailsPage(
                        chatGptState = chatGptImportState,
                        claudeState = claudeImportState,
                        zipState = p6kZipImportState,
                        onClearZipBatch = onClearP6KZip,
                        onRetryZipRecovery = onRetryP6KZip,
                        showJson = true,
                        showZip = false,
                    )
                },
                zipImportResultsContent = {
                    ImportResultsDetailsPage(
                        chatGptState = chatGptImportState,
                        claudeState = claudeImportState,
                        zipState = p6kZipImportState,
                        onClearZipBatch = onClearP6KZip,
                        onRetryZipRecovery = onRetryP6KZip,
                        showJson = false,
                        showZip = true,
                    )
                },
                localBackupState = localBackupState,
                localBackupViewModel = localBackupViewModel,
                onExportLocalBackup = onExportLocalBackup,
                onImportLocalBackup = onImportLocalBackup,
                onSelect = { destination -> openSettingsLevel(P5ARoute.SETTINGS, destination) },
                onOpenLifecycleConversation = { destination, conversation ->
                    lifecycleConversationReturnDestination = destination
                    conversationViewModel.selectConversation(conversation.id)
                    onRouteSelected(P5ARoute.CONVERSATION)
                },
            )
            P5ARoute.ADAPTERS -> WorkbenchRoute(expanded) {
                Text("数据导入", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text("设置 · 数据 · 导入、同步与存储", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                DataImportCenterContent(
                    chatGptImportState = chatGptImportState,
                    onChooseChatGptImport = onOpenChatGptExportImport,
                    onOpenChatGptImportTask = onOpenChatGptImportTask,
                    onBackChatGptImportTask = onBackChatGptImportTask,
                    onRetryChatGptImportTask = onRetryChatGptImportTask,
                    onCancelChatGptImport = onCancelChatGptImport,
                    claudeImportState = claudeImportState,
                    onChooseClaudeImport = onOpenClaudeExportImport,
                    onOpenClaudeImportTask = onOpenClaudeImportTask,
                    onBackClaudeImportTask = onBackClaudeImportTask,
                    onRetryClaudeImportTask = onRetryClaudeImportTask,
                    onCancelClaudeImport = onCancelClaudeImport,
                    p6kZipImportState = p6kZipImportState,
                    onOpenP6KChatGptZip = onOpenP6KChatGptZip,
                    onOpenP6KClaudeZip = onOpenP6KClaudeZip,
                    onClearP6KZip = onClearP6KZip,
                    onRetryP6KZip = onRetryP6KZip,
                    onViewP6KZip = onViewP6KZip,
                    onSelectP6KAsset = onSelectP6KAsset,
                    onSelectP6KTarget = onSelectP6KTarget,
                    onLinkP6KAsset = onLinkP6KAsset,
                    onOpenJsonImportResults = { openSettingsLevel(P5ARoute.SETTINGS, SettingsDestination.JSON_IMPORT_RESULTS) },
                    onOpenZipImportResults = { openSettingsLevel(P5ARoute.SETTINGS, SettingsDestination.ZIP_IMPORT_RESULTS) },
                    workspaceExchangeV2ExportState = workspaceExchangeV2ExportState,
                    onSelectWorkspaceExchangeV2Scope = onSelectWorkspaceExchangeV2Scope,
                    workspaceExchangeV2RestoreState = workspaceExchangeV2RestoreState,
                    onSelectWorkspaceExchangeV2RestoreDocument = onSelectWorkspaceExchangeV2RestoreDocument,
                )
            }
            P5ARoute.CONTROL -> ControlHub { destination ->
                // This legacy compact-workspace route remains reachable from older navigation
                // state, but its return path now lands in the consolidated workspace setting.
                openSettingsLevel(destination, SettingsDestination.WORKSPACE)
            }
            }
        }
    }
}

@Composable
private fun DataImportCenterContent(
    chatGptImportState: ChatGptImportUiState,
    onChooseChatGptImport: () -> Unit,
    onOpenChatGptImportTask: (ChatGptImportTask) -> Unit,
    onBackChatGptImportTask: () -> Unit,
    onRetryChatGptImportTask: (ChatGptImportTaskId) -> Unit,
    onCancelChatGptImport: () -> Unit,
    claudeImportState: ClaudeImportUiState,
    onChooseClaudeImport: () -> Unit,
    onOpenClaudeImportTask: (ClaudeImportTask) -> Unit,
    onBackClaudeImportTask: () -> Unit,
    onRetryClaudeImportTask: (ClaudeImportTaskId) -> Unit,
    onCancelClaudeImport: () -> Unit,
    p6kZipImportState: P6KZipImportUiState,
    onOpenP6KChatGptZip: () -> Unit,
    onOpenP6KClaudeZip: () -> Unit,
    onClearP6KZip: (String) -> Unit,
    onRetryP6KZip: (String) -> Unit,
    onViewP6KZip: (String) -> Unit,
    onSelectP6KAsset: (String) -> Unit,
    onSelectP6KTarget: (P6KZipManualLinkTarget) -> Unit,
    onLinkP6KAsset: () -> Unit,
    onOpenJsonImportResults: () -> Unit,
    onOpenZipImportResults: () -> Unit,
    workspaceExchangeV2ExportState: WorkspaceExchangeV2ExportUiState,
    onSelectWorkspaceExchangeV2Scope: () -> Unit,
    workspaceExchangeV2RestoreState: WorkspaceExchangeV2RestoreUiState,
    onSelectWorkspaceExchangeV2RestoreDocument: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(26.dp)) {
        DataStorageFunctionalGroup(title = "对话") {
            DataStorageGroupedCard {
                ChatGptExportImportSettingsPage(
                    state = chatGptImportState,
                    onChoose = onChooseChatGptImport,
                    onOpen = onOpenChatGptImportTask,
                    onBack = onBackChatGptImportTask,
                    onRetry = onRetryChatGptImportTask,
                    onCancel = onCancelChatGptImport,
                    showHeader = false,
                    actionLabel = "导入 ChatGPT JSON",
                    grouped = true,
                    showTaskList = false,
                )
                DataStorageGroupedDivider()
                ClaudeExportImportSettingsPage(
                    state = claudeImportState,
                    onChoose = onChooseClaudeImport,
                    onOpen = onOpenClaudeImportTask,
                    onBack = onBackClaudeImportTask,
                    onRetry = onRetryClaudeImportTask,
                    onCancel = onCancelClaudeImport,
                    showHeader = false,
                    actionLabel = "导入 Claude JSON",
                    grouped = true,
                    showTaskList = false,
                )
                DataStorageGroupedDivider()
                DataStorageImportResultsRow(
                    summary = jsonImportResultSummary(chatGptImportState, claudeImportState),
                    onClick = onOpenJsonImportResults,
                )
            }
            DataStorageGroupedCard {
                P6KZipImportSettingsCard(
                    state = p6kZipImportState,
                    onChatGpt = onOpenP6KChatGptZip,
                    onClaude = onOpenP6KClaudeZip,
                    grouped = true,
                )
                DataStorageGroupedDivider()
                DataStorageImportResultsRow(
                    summary = zipImportResultSummary(p6kZipImportState),
                    onClick = onOpenZipImportResults,
                )
            }
        }
        DataStorageFunctionalGroup(title = "工作区") {
            DataStorageGroupedCard {
                WorkspaceExchangeV2ExportCard(
                    state = workspaceExchangeV2ExportState,
                    onSelectScope = onSelectWorkspaceExchangeV2Scope,
                    restoreState = workspaceExchangeV2RestoreState,
                    onSelectRestoreDocument = onSelectWorkspaceExchangeV2RestoreDocument,
                    grouped = true,
                )
            }
        }
    }
}

@Composable
private fun WorkspaceExchangeV2ExportCard(
    state: WorkspaceExchangeV2ExportUiState,
    onSelectScope: () -> Unit,
    restoreState: WorkspaceExchangeV2RestoreUiState,
    onSelectRestoreDocument: () -> Unit,
    grouped: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        if (grouped) {
            DataStorageGroupedActionRow(
                label = "导入工作区",
                onClick = onSelectRestoreDocument,
                enabled = !restoreState.working,
                working = restoreState.working,
            )
        } else {
            OutlinedButton(onClick = onSelectRestoreDocument, enabled = !restoreState.working, modifier = Modifier.fillMaxWidth().height(48.dp), shape = P5AInteractiveShape, border = null, colors = ButtonDefaults.outlinedButtonColors(containerColor = ForegroundSurface, contentColor = BodyText)) {
                if (restoreState.working) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("导入工作区")
            }
        }
        WorkspaceExchangeV2RestoreUiMessage(restoreState)?.let { message ->
            Spacer(Modifier.height(8.dp))
            Text(message.first, color = message.second, style = MaterialTheme.typography.bodySmall)
        }
        if (grouped) DataStorageGroupedDivider() else Spacer(Modifier.height(12.dp))
        if (grouped) {
            DataStorageGroupedActionRow(
                label = "导出工作区",
                onClick = onSelectScope,
                enabled = !state.working,
                working = state.working,
            )
        } else {
            Button(onClick = onSelectScope, enabled = !state.working, modifier = Modifier.fillMaxWidth().height(48.dp), shape = P5AInteractiveShape, colors = ButtonDefaults.buttonColors(containerColor = ForegroundSurface, contentColor = BodyText)) {
                if (state.working) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = AccentOnPrimary)
                else Text("导出工作区")
            }
        }
        state.notice?.let { notice -> Spacer(Modifier.height(8.dp)); Text(notice, color = SecondaryText, style = MaterialTheme.typography.bodySmall) }
        state.error?.let { error -> Spacer(Modifier.height(8.dp)); Text(error, color = ErrorRed, style = MaterialTheme.typography.bodySmall) }
    }
}

private fun WorkspaceExchangeV2RestoreUiMessage(state: WorkspaceExchangeV2RestoreUiState): Pair<String, Color>? = when (state.outcome) {
    WorkspaceExchangeV2RestoreUiOutcome.IDLE -> null
    WorkspaceExchangeV2RestoreUiOutcome.RESTORED -> "已严格恢复：${state.semanticHashPrefix}… · ${state.objectCount} 项对象 · ${state.attachmentCount} 项附件" to SecondaryText
    WorkspaceExchangeV2RestoreUiOutcome.REPLAYED -> "已验证相同恢复回执：${state.semanticHashPrefix}… · 未重复写入对象或附件" to SecondaryText
    WorkspaceExchangeV2RestoreUiOutcome.PACKAGE_REJECTED -> "所选交换包未通过完整校验，未读取或覆盖本机数据。" to ErrorRed
    WorkspaceExchangeV2RestoreUiOutcome.LOCAL_TRUTH_PRESENT -> "当前本机已有数据或待恢复记录，已拒绝覆盖。" to ErrorRed
    WorkspaceExchangeV2RestoreUiOutcome.INTENT_CONFLICT -> "恢复请求与已确认的交换包不一致，未写入本机数据。" to ErrorRed
    WorkspaceExchangeV2RestoreUiOutcome.RECOVERY_REQUIRED -> "检测到无法安全判定的恢复状态，已停止；请保留本机数据并联系支持。" to ErrorRed
    WorkspaceExchangeV2RestoreUiOutcome.FAILED_RECOVERABLY -> "恢复未完成，已保留可恢复状态；请重新选择同一交换包重试，不要清除数据。" to ErrorRed
}

@Composable
private fun WorkspaceExchangeV2ScopeDialog(
    scope: com.nanzhufeng.ai.domain.WorkspaceExchangeV2ScopeSummary,
    onDismiss: () -> Unit,
    onExport: (com.nanzhufeng.ai.domain.WorkspaceExchangeV2ScopeSummary) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("完整工作区范围") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("将严格导出当前范围内的全部对象；任一对象不完整、不可读取或不符合 v2 合同，均不会写入文件。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                Text("项目 ${scope.projectCount} · 对话 ${scope.conversationCount} · 知识 ${scope.knowledgeCount}", style = MaterialTheme.typography.bodyMedium)
                Text("记忆 ${scope.memoryCount} · 关系 ${scope.relationCount} · 附件 ${scope.attachmentCount}", style = MaterialTheme.typography.bodyMedium)
                Text("附件按高敏感级别处理；选择保存位置后即开始生成与回读，不再显示第二次产品确认。", color = SecondaryText, style = MaterialTheme.typography.labelSmall)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = { Button(onClick = { onExport(scope) }, shape = P5AInteractiveShape) { Text("选择保存位置") } },
    )
}

/** Existing local import owners are exposed here only; this card owns no parsing or network work. */
@Composable
private fun LocalKnowledgeImportEntryCard(
    onOpenMarkdownImport: () -> Unit,
    onOpenMarkdownExport: () -> Unit,
    onOpenJsonKnowledgeImport: () -> Unit,
    onOpenJsonKnowledgeExport: () -> Unit,
    onOpenPdfTextImport: () -> Unit,
    onOpenWebTextSnapshot: () -> Unit,
) = Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
    Text("知识文件与网页文本", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Text("选择文件即可导入或导出；不会读取模型 Key。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(12.dp))
    Button(onClick = onOpenMarkdownImport, modifier = Modifier.fillMaxWidth().height(48.dp), shape = P5AInteractiveShape, colors = ButtonDefaults.buttonColors(containerColor = ForegroundSurface, contentColor = BodyText)) { Text("导入 Markdown 文件") }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onOpenMarkdownExport, modifier = Modifier.fillMaxWidth().height(48.dp), shape = P5AInteractiveShape, border = null, colors = ButtonDefaults.outlinedButtonColors(containerColor = ForegroundSurface, contentColor = BodyText)) { Text("导出 Markdown 知识") }
    Spacer(Modifier.height(8.dp))
    Button(onClick = onOpenJsonKnowledgeImport, modifier = Modifier.fillMaxWidth().height(48.dp), shape = P5AInteractiveShape, colors = ButtonDefaults.buttonColors(containerColor = ForegroundSurface, contentColor = BodyText)) { Text("导入知识 JSON") }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onOpenJsonKnowledgeExport, modifier = Modifier.fillMaxWidth().height(48.dp), shape = P5AInteractiveShape, border = null, colors = ButtonDefaults.outlinedButtonColors(containerColor = ForegroundSurface, contentColor = BodyText)) { Text("导出知识 JSON") }
    Spacer(Modifier.height(8.dp))
    Button(onClick = onOpenPdfTextImport, modifier = Modifier.fillMaxWidth().height(48.dp), shape = P5AInteractiveShape, colors = ButtonDefaults.buttonColors(containerColor = ForegroundSurface, contentColor = BodyText)) { Text("导入 PDF 文本") }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onOpenWebTextSnapshot, modifier = Modifier.fillMaxWidth().height(48.dp), shape = P5AInteractiveShape, border = null, colors = ButtonDefaults.outlinedButtonColors(containerColor = ForegroundSurface, contentColor = BodyText)) { Text("保存网页文本快照") }
}

@Composable
private fun SettingsPageHeader(
    destination: SettingsDestination,
    onBack: () -> Unit,
    onSave: (() -> Unit)? = null,
    saveEnabled: Boolean = false,
) {
    val titleStyle = if (destination == SettingsDestination.FAVORITE_CONVERSATIONS || destination == SettingsDestination.ARCHIVED_CONVERSATIONS || destination == SettingsDestination.RECYCLE_BIN) {
        MaterialTheme.typography.titleLarge
    } else {
        MaterialTheme.typography.headlineSmall
    }
    if (destination == SettingsDestination.HOME) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回侧栏")
            }
            Text(destination.label, modifier = Modifier.semantics { heading() }, style = titleStyle, fontWeight = FontWeight.SemiBold)
        }
        return
    }
    Box(modifier = Modifier.fillMaxWidth().height(48.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).size(48.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回设置")
        }
        Text(
            destination.label,
            modifier = Modifier.align(Alignment.Center).semantics { heading() },
            style = titleStyle,
            fontWeight = FontWeight.SemiBold,
        )
        if (onSave != null) {
            Surface(
                onClick = onSave,
                enabled = saveEnabled,
                modifier = Modifier.align(Alignment.CenterEnd).size(40.dp),
                shape = CircleShape,
                color = if (saveEnabled) AccentOrange.copy(alpha = 0.14f) else ForegroundSurface,
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = "保存个性化设置",
                    modifier = Modifier.padding(10.dp),
                    tint = if (saveEnabled) AccentOrange else SecondaryText.copy(alpha = 0.45f),
                )
            }
        }
    }
}

@Composable
private fun SettingsHierarchy(
    destination: SettingsDestination,
    expanded: Boolean,
    assistantExperienceSettingsState: AssistantExperienceSettingsUiState,
    onUpdateAssistantExperienceSettings: ((com.nanzhufeng.ai.domain.AssistantExperienceSettings) -> com.nanzhufeng.ai.domain.AssistantExperienceSettings) -> Unit,
    personalizationDraft: com.nanzhufeng.ai.domain.AssistantExperienceSettings,
    onUpdatePersonalizationDraft: ((com.nanzhufeng.ai.domain.AssistantExperienceSettings) -> com.nanzhufeng.ai.domain.AssistantExperienceSettings) -> Unit,
    onSavePersonalization: () -> Unit,
    onOpenPersonalization: () -> Unit,
    notificationReminderSettingsState: NotificationReminderSettingsUiState,
    onUpdateNotificationReminderSettings: ((com.nanzhufeng.ai.domain.NotificationReminderSettings) -> com.nanzhufeng.ai.domain.NotificationReminderSettings) -> Unit,
    appearanceSettingsState: AppearanceSettingsUiState,
    onUpdateAppearanceSettings: ((com.nanzhufeng.ai.domain.AppearanceSettings) -> com.nanzhufeng.ai.domain.AppearanceSettings) -> Unit,
    onOpenMemoryManager: () -> Unit,
    modelSettingsState: ModelSettingsUiState,
    onSaveModelSettings: (ProviderId, Boolean, ModelPresetId, String?) -> Unit,
    onSaveChatRoutingPolicy: (com.nanzhufeng.ai.domain.ChatRoutingPolicy) -> Unit,
    onRevealModelCredential: (ProviderId) -> Unit,
    onTestModelConnection: (ProviderId) -> Unit,
    invocationLedgerState: InvocationLedgerUiState,
    onLoadInvocationLedger: () -> Unit,
    conversationCostLedgerState: ConversationCostLedgerUiState,
    onLoadConversationCostLedger: () -> Unit,
    conversationState: ConversationFoundationUiState,
    conversationViewModel: ConversationFoundationViewModel,
    projectState: ProjectUiState,
    onOpenProjectManager: () -> Unit,
    knowledgeLibraryState: KnowledgeLibraryUiState,
    onOpenKnowledgeLibrary: () -> Unit,
    onOpenContext: () -> Unit,
    onOpenOfflineEval: () -> Unit,
    privacyDataState: PrivacyDataUiState,
    privacyDataViewModel: PrivacyDataViewModel,
    onOpenPrivacyData: () -> Unit,
    dataStorageImportContent: @Composable () -> Unit,
    jsonImportResultsContent: @Composable () -> Unit,
    zipImportResultsContent: @Composable () -> Unit,
    localBackupState: LocalBackupUiState,
    localBackupViewModel: LocalBackupRestoreViewModel,
    onExportLocalBackup: () -> Unit,
    onImportLocalBackup: () -> Unit,
    onSelect: (SettingsDestination) -> Unit,
    onOpenLifecycleConversation: (SettingsDestination, com.nanzhufeng.ai.domain.Conversation) -> Unit,
) {
    // Entering either lifecycle list used to change the repository projection and push the
    // next settings page in the same frame. That made the new page briefly render the previous
    // list before its asynchronous reload completed. Keep the user on the management page until
    // the requested projection is ready, then navigate once with stable content.
    var pendingLifecycleDestination by remember { mutableStateOf<SettingsDestination?>(null) }
    LaunchedEffect(pendingLifecycleDestination, conversationState.listScope, conversationState.isLoading) {
        val target = pendingLifecycleDestination ?: return@LaunchedEffect
        val targetScope = when (target) {
            SettingsDestination.FAVORITE_CONVERSATIONS -> com.nanzhufeng.ai.domain.ConversationListScope.FAVORITES
            SettingsDestination.ARCHIVED_CONVERSATIONS -> com.nanzhufeng.ai.domain.ConversationListScope.ARCHIVED
            SettingsDestination.RECYCLE_BIN -> com.nanzhufeng.ai.domain.ConversationListScope.DELETED
            else -> return@LaunchedEffect
        }
        if (conversationState.listScope == targetScope && !conversationState.isLoading) {
            pendingLifecycleDestination = null
            onSelect(target)
        }
    }
    if (destination == SettingsDestination.HOME) {
        SettingsCategoryList(
            appearanceState = appearanceSettingsState,
            onUpdateAppearance = onUpdateAppearanceSettings,
            onOpenPersonalization = onOpenPersonalization,
            onSelect = onSelect,
        )
        return
    }
    WorkbenchRoute(expanded) {
        when (destination) {
            SettingsDestination.HOME -> Unit
            SettingsDestination.APPEARANCE -> AppearanceSettingsPage(
                state = appearanceSettingsState,
                onUpdate = onUpdateAppearanceSettings,
            )
            SettingsDestination.PERSONALIZATION -> AssistantPersonalizationSettingsCard(
                settings = personalizationDraft,
                notice = assistantExperienceSettingsState.notice,
                error = assistantExperienceSettingsState.error,
                onUpdate = onUpdatePersonalizationDraft,
                onSave = onSavePersonalization,
                onOpenMemoryManager = onOpenMemoryManager,
            )
            SettingsDestination.NOTIFICATIONS -> NotificationReminderSettingsCard(
                state = notificationReminderSettingsState,
                onUpdate = onUpdateNotificationReminderSettings,
            )
            SettingsDestination.MODEL -> {
                ModelServiceStatusCard(
                    state = modelSettingsState,
                    webSearchEnabled = assistantExperienceSettingsState.settings.webSearchEnabled,
                    onWebSearchEnabledChange = { enabled ->
                        onUpdateAssistantExperienceSettings { current -> current.copy(webSearchEnabled = enabled) }
                        // The Composer's current-conversation control inherits this value only
                        // when no local override exists, so refresh its projection immediately.
                        conversationViewModel.reload()
                    },
                    onOpen = { onSelect(SettingsDestination.MODEL_CONFIGURATION) },
                    onOpenConversationCostLedger = { onLoadConversationCostLedger(); onSelect(SettingsDestination.CONVERSATION_COST) },
                    onOpenContextSelections = { onSelect(SettingsDestination.CONTEXT_SELECTIONS) },
                    onOpenRuntimeDiagnostics = { onLoadInvocationLedger(); onSelect(SettingsDestination.RUN_DIAGNOSTICS) },
                )
            }
            SettingsDestination.MODEL_CONFIGURATION -> ModelSettingsConfigurationPage(
                state = modelSettingsState,
                onSave = onSaveModelSettings,
                onRevealStoredCredential = onRevealModelCredential,
                onTestConnection = onTestModelConnection,
            )
            SettingsDestination.CONVERSATION_COST -> ConversationCostLedgerPage(conversationCostLedgerState)
            SettingsDestination.CONTEXT_SELECTIONS -> ModelSettingsContextSelectionsPage(modelSettingsState.recentContextSelections, conversationState.conversations)
            SettingsDestination.RUN_DIAGNOSTICS -> RuntimeDiagnosticsPage(modelSettingsState, invocationLedgerState, conversationState.conversations)
            SettingsDestination.CONVERSATIONS -> ConversationManagementSettingsCard(
                state = conversationState,
                onOpenFavorites = {
                    pendingLifecycleDestination = SettingsDestination.FAVORITE_CONVERSATIONS
                    conversationViewModel.setListScope(com.nanzhufeng.ai.domain.ConversationListScope.FAVORITES)
                },
                onOpenArchived = {
                    pendingLifecycleDestination = SettingsDestination.ARCHIVED_CONVERSATIONS
                    conversationViewModel.setListScope(com.nanzhufeng.ai.domain.ConversationListScope.ARCHIVED)
                },
                onOpenRecycleBin = {
                    pendingLifecycleDestination = SettingsDestination.RECYCLE_BIN
                    conversationViewModel.setListScope(com.nanzhufeng.ai.domain.ConversationListScope.DELETED)
                },
            )
            SettingsDestination.FAVORITE_CONVERSATIONS -> FavoriteConversationListSettingsCard(
                state = conversationState,
                onUnfavorite = { conversation -> conversationViewModel.manage(conversation, com.nanzhufeng.ai.domain.ConversationManagementAction.UNFAVORITE, null) },
                onOpenConversation = { conversation -> onOpenLifecycleConversation(SettingsDestination.FAVORITE_CONVERSATIONS, conversation) },
            )
            SettingsDestination.ARCHIVED_CONVERSATIONS -> ConversationLifecycleListSettingsCard(
                state = conversationState,
                scope = com.nanzhufeng.ai.domain.ConversationListScope.ARCHIVED,
                onManage = { conversation, action -> conversationViewModel.manage(conversation, action, null) },
                onPermanentlyDelete = conversationViewModel::permanentlyDelete,
                onClearArchived = conversationViewModel::softDeleteConversations,
                onClearRecycleBin = conversationViewModel::permanentlyDeleteConversations,
                onOpenConversation = { conversation -> onOpenLifecycleConversation(SettingsDestination.ARCHIVED_CONVERSATIONS, conversation) },
            )
            SettingsDestination.RECYCLE_BIN -> ConversationLifecycleListSettingsCard(
                state = conversationState,
                scope = com.nanzhufeng.ai.domain.ConversationListScope.DELETED,
                onManage = { conversation, action -> conversationViewModel.manage(conversation, action, null) },
                onPermanentlyDelete = conversationViewModel::permanentlyDelete,
                onClearArchived = conversationViewModel::softDeleteConversations,
                onClearRecycleBin = conversationViewModel::permanentlyDeleteConversations,
                onOpenConversation = { conversation -> onOpenLifecycleConversation(SettingsDestination.RECYCLE_BIN, conversation) },
            )
            SettingsDestination.WORKSPACE -> WorkspaceSettingsCard(
                projectState = projectState,
                onOpenProjects = onOpenProjectManager,
                onOpenKnowledge = onOpenKnowledgeLibrary,
            )
            SettingsDestination.DEVELOPMENT -> DevelopmentDiagnosticsSettingsCard(
                onOpenContext = onOpenContext,
                onOpenOfflineEval = onOpenOfflineEval,
            )
            SettingsDestination.DATA_STORAGE -> Column(verticalArrangement = Arrangement.spacedBy(26.dp)) {
                dataStorageImportContent()
                DataStorageFunctionalGroup(title = "本机备份与恢复") {
                    DataStorageGroupedCard {
                        LocalBackupRestorePage(
                            state = localBackupState,
                            onExport = onExportLocalBackup,
                            onImport = onImportLocalBackup,
                            onReplace = localBackupViewModel::replace,
                            onRestore = localBackupViewModel::restore,
                            onCancel = localBackupViewModel::cancel,
                            grouped = true,
                        )
                    }
                }
            }
            SettingsDestination.JSON_IMPORT_RESULTS -> jsonImportResultsContent()
            SettingsDestination.ZIP_IMPORT_RESULTS -> zipImportResultsContent()
            SettingsDestination.LOCAL_BACKUP -> LocalBackupRestorePage(
                state = localBackupState,
                onExport = onExportLocalBackup,
                onImport = onImportLocalBackup,
                onReplace = localBackupViewModel::replace,
                onRestore = localBackupViewModel::restore,
                onCancel = localBackupViewModel::cancel,
            )
            SettingsDestination.ABOUT -> AboutSettingsCard()
            SettingsDestination.PRIVACY -> {
                LaunchedEffect(Unit) { onOpenPrivacyData() }
                PrivacyDataPage(
                    state = privacyDataState,
                    onPreview = privacyDataViewModel::preview,
                    onToggleTask = privacyDataViewModel::toggleTask,
                    onPreviewSelectedTasks = privacyDataViewModel::previewSelectedTasks,
                    onConfirmation = privacyDataViewModel::confirmation,
                    onDelete = privacyDataViewModel::delete,
                    onRetryFailedTaskDeletion = privacyDataViewModel::retryFailedTaskDeletion,
                )
            }
        }
    }
}

@Composable
private fun DataStorageFunctionalGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

/** One data-storage task is one bright card; sibling actions are divided by the page canvas. */
@Composable
internal fun DataStorageGroupedCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = ForegroundSurface,
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
internal fun DataStorageGroupedDivider() {
    HorizontalDivider(color = SettingsPageBackground, thickness = SettingsGroupedCardDividerHeight)
}

@Composable
internal fun DataStorageGroupedActionRow(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    working: Boolean = false,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RectangleShape,
        color = ForegroundSurface,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (working) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = AccentOrange)
                    Spacer(Modifier.width(12.dp))
                }
                Text(label, style = MaterialTheme.typography.titleMedium, color = BodyText)
            }
        }
    }
}

@Composable
private fun DataStorageImportResultsRow(
    summary: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 82.dp),
        shape = RectangleShape,
        color = ForegroundSurface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("导入结果", style = MaterialTheme.typography.titleMedium, color = BodyText)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = SecondaryText)
            }
            Text("查看详情", style = MaterialTheme.typography.labelLarge, color = AccentOrange)
        }
    }
}

private fun jsonImportResultSummary(
    chatGpt: ChatGptImportUiState,
    claude: ClaudeImportUiState,
): String {
    val batches = chatGpt.tasks.size + claude.tasks.size
    val imported = chatGpt.tasks.sumOf { task -> task.items.count { it.status == com.nanzhufeng.ai.domain.ChatGptImportItemStatus.CONFIRMED } } +
        claude.tasks.sumOf { task -> task.items.count { it.status == com.nanzhufeng.ai.domain.ClaudeImportItemStatus.CONFIRMED } }
    return "$batches 个导入批次 · $imported 个对话已导入"
}

private fun zipImportResultSummary(zip: P6KZipImportUiState): String {
    val imported = zip.tasks.sumOf { task -> task.items.count { it.status == com.nanzhufeng.ai.domain.P6KZipItemStatus.CONFIRMED } }
    val restoredAttachments = zip.tasks.sumOf { task -> task.assets.count { it.attachmentId != null } }
    return "${zip.tasks.size} 个导入批次 · $imported 个对话已导入 · $restoredAttachments 个附件已恢复"
}

@Composable
private fun SettingsCategoryList(
    appearanceState: AppearanceSettingsUiState,
    onUpdateAppearance: ((com.nanzhufeng.ai.domain.AppearanceSettings) -> com.nanzhufeng.ai.domain.AppearanceSettings) -> Unit,
    onOpenPersonalization: () -> Unit,
    onSelect: (SettingsDestination) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SettingsCategoryGroup(title = "对话") {
            SettingsCategoryRow(Icons.Rounded.PersonOutline, "个性化", grouped = true, onClick = onOpenPersonalization)
            SettingsCategoryDivider()
            SettingsCategoryRow(Icons.Rounded.Tune, "模型与联网", grouped = true) { onSelect(SettingsDestination.MODEL) }
            SettingsCategoryDivider()
            SettingsCategoryRow(Icons.Rounded.Notifications, "提醒", grouped = true) { onSelect(SettingsDestination.NOTIFICATIONS) }
            SettingsCategoryDivider()
            SettingsCategoryRow(
                icon = painterResource(R.drawable.ic_nanfeng_conversation_bubble),
                title = "对话管理",
                grouped = true,
            ) { onSelect(SettingsDestination.CONVERSATIONS) }
        }
        SettingsCategoryGroup(title = "外观") {
            AppearanceSettingsControls(
                state = appearanceState,
                onUpdate = onUpdateAppearance,
                appearanceIcon = SettingsAppearanceIcon,
                grouped = true,
            )
        }
        SettingsCategoryGroup(title = "应用与数据") {
            SettingsCategoryRow(Icons.Rounded.Storage, "数据与存储", iconTint = settingsUtilityIconTint(), grouped = true) { onSelect(SettingsDestination.DATA_STORAGE) }
            SettingsCategoryDivider()
            SettingsCategoryRow(Icons.Rounded.Lock, "隐私与安全", iconTint = settingsUtilityIconTint(), grouped = true) { onSelect(SettingsDestination.PRIVACY) }
            SettingsCategoryDivider()
            SettingsCategoryRow(Icons.Rounded.Settings, "关于", iconTint = settingsUtilityIconTint(), grouped = true) { onSelect(SettingsDestination.ABOUT) }
        }
        SettingsCategoryGroup(title = "工作区") {
            SettingsCategoryRow(Icons.Rounded.FolderOpen, "项目与知识", grouped = true) { onSelect(SettingsDestination.WORKSPACE) }
            SettingsCategoryDivider()
            SettingsCategoryRow(Icons.Rounded.Tune, "开发与诊断", grouped = true) { onSelect(SettingsDestination.DEVELOPMENT) }
        }
    }
}

@Composable
private fun SettingsCategoryGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            modifier = Modifier.padding(start = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            color = BodyText,
            fontWeight = FontWeight.SemiBold,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = ForegroundSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) { Column(content = content) }
    }
}

@Composable
private fun SettingsCategoryDivider() = Spacer(
    Modifier.fillMaxWidth().height(SettingsGroupedCardDividerHeight).background(SettingsPageBackground),
)

@Composable
private fun AssistantPersonalizationSettingsCard(
    settings: com.nanzhufeng.ai.domain.AssistantExperienceSettings,
    notice: String?,
    error: String?,
    onUpdate: ((com.nanzhufeng.ai.domain.AssistantExperienceSettings) -> com.nanzhufeng.ai.domain.AssistantExperienceSettings) -> Unit,
    onSave: () -> Unit,
    onOpenMemoryManager: () -> Unit,
) = Column(modifier = Modifier.fillMaxWidth()) {
    var stylePickerVisible by rememberSaveable { mutableStateOf(false) }
    var customInstructionsFullscreen by rememberSaveable { mutableStateOf(false) }
    val foregroundInputColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = ForegroundSurface,
        unfocusedContainerColor = ForegroundSurface,
        disabledContainerColor = ForegroundSurface,
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
        disabledBorderColor = Color.Transparent,
        focusedPlaceholderColor = InputPlaceholderText,
        unfocusedPlaceholderColor = InputPlaceholderText,
        disabledPlaceholderColor = InputPlaceholderText,
    )
    SettingsSwitchRow(
        title = "启用记忆",
        summary = "",
        checked = settings.memoryEnabled,
        onCheckedChange = { enabled -> onUpdate { current -> current.copy(personalizationEnabled = enabled, memoryRetrievalEnabled = enabled) } },
        surface = true,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "允许 南枫AI 根据你的聊天、文件和已关联的应用为你提供个性化体验。",
        modifier = Modifier.padding(horizontal = 4.dp),
        color = SecondaryText,
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(12.dp))
    SettingsSwitchRow(
        title = "资料库搜索",
        summary = "",
        checked = settings.librarySearchEnabled,
        onCheckedChange = { enabled -> onUpdate { current -> current.copy(librarySearchEnabled = enabled) } },
        surface = true,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        "允许 南枫AI 自动搜索资料库中的文件以查找答案。",
        modifier = Modifier.padding(horizontal = 4.dp),
        color = SecondaryText,
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(16.dp))
    ConversationStylePreferenceRow(
        style = settings.conversationStyle,
        onClick = { stylePickerVisible = true },
    )
    if (stylePickerVisible) {
        AppearancePickerDialog(
            title = "基础风格和语气",
            onDismiss = { stylePickerVisible = false },
        ) {
            com.nanzhufeng.ai.domain.ConversationStyle.entries.forEach { option ->
                AppearancePickerOption(
                    label = option.label(),
                    selected = option == settings.conversationStyle,
                    onClick = {
                        onUpdate { current -> current.copy(conversationStyle = option) }
                        stylePickerVisible = false
                    },
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "这是 南枫AI 在与你对话时使用的主要语气。这不会影响 南枫AI 的功能。",
        modifier = Modifier.padding(horizontal = 4.dp),
        color = SecondaryText,
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(12.dp))
    OutlinedButton(onClick = onOpenMemoryManager, modifier = Modifier.fillMaxWidth().height(52.dp), shape = P5AInteractiveShape, border = null, colors = ButtonDefaults.outlinedButtonColors(containerColor = ForegroundSurface)) {
        Column(Modifier.weight(1f)) {
            Text("记忆摘要", color = BodyText, fontWeight = FontWeight.Medium)
        }
        Text("›", style = MaterialTheme.typography.headlineSmall)
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "查看 南枫AI 对你的了解概览。如果你希望它持续记住某些信息，可以使用下方 自定义指令。",
        modifier = Modifier.padding(horizontal = 4.dp),
        color = SecondaryText,
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(16.dp))
    Text("你的昵称", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(4.dp))
    OutlinedTextField(
        value = settings.displayName,
        onValueChange = { value -> onUpdate { current -> current.copy(displayName = value) } },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("例如：南烛枫") },
        singleLine = true,
        shape = P5AInteractiveShape,
        colors = foregroundInputColors,
    )
    Spacer(Modifier.height(8.dp))
    Text("你的职业", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(4.dp))
    OutlinedTextField(
        value = settings.occupation,
        onValueChange = { value -> onUpdate { current -> current.copy(occupation = value) } },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("工程师、学生等") },
        singleLine = true,
        shape = P5AInteractiveShape,
        colors = foregroundInputColors,
    )
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("自定义指令", modifier = Modifier.weight(1f), color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        IconButton(
            onClick = { customInstructionsFullscreen = true },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                Icons.Rounded.OpenInFull,
                contentDescription = "全屏编辑自定义指令",
                modifier = Modifier.size(14.4.dp),
                tint = SecondaryText,
            )
        }
    }
    Spacer(Modifier.height(4.dp))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ForegroundSurface,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = settings.customInstructions,
            onValueChange = { value -> onUpdate { current -> current.copy(customInstructions = value) } },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("希望南枫 AI 如何回答你") },
            minLines = 5,
            maxLines = 5,
            shape = RoundedCornerShape(16.dp),
            colors = foregroundInputColors,
        )
        Text(
            "${settings.customInstructions.length} / ${com.nanzhufeng.ai.domain.AssistantExperienceSettings.CUSTOM_INSTRUCTIONS_MAX_LENGTH} 字",
            modifier = Modifier.align(Alignment.End).padding(end = 18.dp, bottom = 5.dp),
            color = SecondaryText,
            style = MaterialTheme.typography.labelSmall,
        )
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(
        "你的 自定义指令 将用于所有南枫AI的对话。",
        modifier = Modifier.padding(horizontal = 4.dp),
        color = SecondaryText,
        style = MaterialTheme.typography.bodySmall,
    )
    error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = ErrorRed, style = MaterialTheme.typography.bodySmall) }
    if (customInstructionsFullscreen) {
        CustomInstructionsFullscreenEditor(
            value = settings.customInstructions,
            onValueChange = { value -> onUpdate { current -> current.copy(customInstructions = value) } },
            onSave = {
                onSave()
                customInstructionsFullscreen = false
            },
            onDismiss = { customInstructionsFullscreen = false },
        )
    }
}

@Composable
private fun CustomInstructionsFullscreenEditor(
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val inputColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = ForegroundSurface,
        unfocusedContainerColor = ForegroundSurface,
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
        focusedPlaceholderColor = InputPlaceholderText,
        unfocusedPlaceholderColor = InputPlaceholderText,
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(color = SettingsPageBackground, contentColor = BodyText, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 12.dp),
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart).size(48.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭全屏自定义指令编辑")
                    }
                    Text(
                        "自定义指令",
                        modifier = Modifier.align(Alignment.Center).semantics { heading() },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Surface(
                        onClick = onSave,
                        modifier = Modifier.align(Alignment.CenterEnd).size(40.dp),
                        shape = CircleShape,
                        color = AccentOrange.copy(alpha = 0.14f),
                    ) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = "保存自定义指令",
                            modifier = Modifier.padding(10.dp),
                            tint = AccentOrange,
                        )
                    }
                }
                Surface(
                    color = ForegroundSurface,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().weight(1f).navigationBarsPadding(),
                ) {
                    Column(Modifier.fillMaxSize()) {
                        OutlinedTextField(
                            value = value,
                            onValueChange = onValueChange,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            placeholder = { Text("希望南枫 AI 如何回答你") },
                            minLines = 12,
                            maxLines = Int.MAX_VALUE,
                            shape = RoundedCornerShape(20.dp),
                            colors = inputColors,
                        )
                        Text(
                            "${value.length} / ${com.nanzhufeng.ai.domain.AssistantExperienceSettings.CUSTOM_INSTRUCTIONS_MAX_LENGTH} 字",
                            modifier = Modifier.align(Alignment.End).padding(end = 18.dp, bottom = 5.dp),
                            color = SecondaryText,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredPersonalizationSaveNotice(
    notice: String,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(notice) {
        delay(1_800)
        onDismiss()
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = ForegroundSurface,
            shadowElevation = 6.dp,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(18.dp))
                Text(notice, color = BodyText, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun com.nanzhufeng.ai.domain.ConversationStyle.label(): String = when (this) {
    com.nanzhufeng.ai.domain.ConversationStyle.DEFAULT -> "默认"
    com.nanzhufeng.ai.domain.ConversationStyle.DIRECT -> "直言不讳"
    com.nanzhufeng.ai.domain.ConversationStyle.PROFESSIONAL -> "专业可靠"
    com.nanzhufeng.ai.domain.ConversationStyle.FRIENDLY -> "亲和友善"
    com.nanzhufeng.ai.domain.ConversationStyle.EFFICIENT -> "高效务实"
    com.nanzhufeng.ai.domain.ConversationStyle.HUMOROUS -> "风趣搞笑"
}

@Composable
private fun ConversationStylePreferenceRow(
    style: com.nanzhufeng.ai.domain.ConversationStyle,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(52.dp).clickable(onClick = onClick),
        shape = P5AInteractiveShape,
        color = ForegroundSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("基础风格和语气", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(style.label(), color = SecondaryText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

private enum class AppearancePicker { MODE, ACCENT, FONT_SIZE }

private fun com.nanzhufeng.ai.domain.AppearanceMode.label(): String = when (this) {
    com.nanzhufeng.ai.domain.AppearanceMode.SYSTEM -> "系统（默认）"
    com.nanzhufeng.ai.domain.AppearanceMode.LIGHT -> "浅色"
    com.nanzhufeng.ai.domain.AppearanceMode.DARK -> "深色"
}

private fun com.nanzhufeng.ai.domain.AccentColor.label(): String = when (this) {
    com.nanzhufeng.ai.domain.AccentColor.ORANGE -> "橙色"
    com.nanzhufeng.ai.domain.AccentColor.BLUE -> "蓝色"
    com.nanzhufeng.ai.domain.AccentColor.BLACK -> "黑色"
    com.nanzhufeng.ai.domain.AccentColor.GREEN -> "绿色"
    com.nanzhufeng.ai.domain.AccentColor.YELLOW -> "黄色"
    com.nanzhufeng.ai.domain.AccentColor.PURPLE -> "紫色"
    com.nanzhufeng.ai.domain.AccentColor.PINK -> "粉色"
}

private fun com.nanzhufeng.ai.domain.AppFontSize.label(): String = when (this) {
    com.nanzhufeng.ai.domain.AppFontSize.SMALL -> "小"
    com.nanzhufeng.ai.domain.AppFontSize.STANDARD -> "标准"
    com.nanzhufeng.ai.domain.AppFontSize.LARGE -> "大"
}

@Composable
private fun AppearanceSettingsPage(
    state: AppearanceSettingsUiState,
    onUpdate: ((com.nanzhufeng.ai.domain.AppearanceSettings) -> com.nanzhufeng.ai.domain.AppearanceSettings) -> Unit,
) {
    AppearanceSettingsControls(
        state = state,
        onUpdate = onUpdate,
        appearanceIcon = SettingsAppearanceIcon,
        modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp),
    )
}

@Composable
private fun AppearanceSettingsControls(
    state: AppearanceSettingsUiState,
    onUpdate: ((com.nanzhufeng.ai.domain.AppearanceSettings) -> com.nanzhufeng.ai.domain.AppearanceSettings) -> Unit,
    appearanceIcon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier.fillMaxWidth(),
    grouped: Boolean = false,
) {
    var picker by rememberSaveable { mutableStateOf<AppearancePicker?>(null) }
    val settings = state.settings
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (grouped) 0.dp else 10.dp),
    ) {
        AppearancePreferenceRow(
            icon = appearanceIcon,
            title = "外观",
            value = settings.mode.label(),
            onClick = { picker = AppearancePicker.MODE },
            grouped = grouped,
        )
        if (grouped) SettingsCategoryDivider()
        AppearancePreferenceRow(
            icon = Icons.Rounded.FormatSize,
            title = "字体大小",
            value = settings.fontSize.label(),
            onClick = { picker = AppearancePicker.FONT_SIZE },
            grouped = grouped,
        )
        if (grouped) SettingsCategoryDivider()
        AppearancePreferenceRow(
            icon = Icons.Rounded.Tune,
            title = "强调色",
            value = settings.accentColor.label(),
            dotColor = settings.accentColor.toColor(),
            onClick = { picker = AppearancePicker.ACCENT },
            grouped = grouped,
        )
        state.error?.let { Text(it, color = ErrorRed, style = MaterialTheme.typography.bodySmall) }
    }
    when (picker) {
        AppearancePicker.MODE -> AppearancePickerDialog(
            title = "外观",
            onDismiss = { picker = null },
        ) {
            com.nanzhufeng.ai.domain.AppearanceMode.entries.forEach { option ->
                AppearancePickerOption(
                    label = option.label(),
                    selected = option == settings.mode,
                    onClick = { onUpdate { it.copy(mode = option) }; picker = null },
                )
            }
        }
        AppearancePicker.ACCENT -> AppearancePickerDialog(
            title = "强调色",
            onDismiss = { picker = null },
        ) {
            com.nanzhufeng.ai.domain.AccentColor.entries.forEach { option ->
                AppearancePickerOption(
                    label = option.label(),
                    dotColor = option.toColor(),
                    selected = option == settings.accentColor,
                    onClick = { onUpdate { it.copy(accentColor = option) }; picker = null },
                )
            }
        }
        AppearancePicker.FONT_SIZE -> AppearancePickerDialog(
            title = "字体大小",
            onDismiss = { picker = null },
        ) {
            com.nanzhufeng.ai.domain.AppFontSize.entries.forEach { option ->
                AppearancePickerOption(
                    label = option.label(),
                    selected = option == settings.fontSize,
                    onClick = { onUpdate { it.copy(fontSize = option) }; picker = null },
                    previewIcon = Icons.Rounded.FormatSize,
                    previewScale = option.scale,
                )
            }
        }
        null -> Unit
    }
}

@Composable
private fun AppearancePreferenceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
    dotColor: Color? = null,
    grouped: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(58.dp),
        shape = if (grouped) RoundedCornerShape(0.dp) else P5AInteractiveShape,
        color = if (grouped) Color.Transparent else ForegroundSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(scaledAppIconSize(17.dp)), tint = BodyText)
            Spacer(Modifier.width(18.dp))
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                dotColor?.let {
                    Box(Modifier.size(12.dp).clip(RoundedCornerShape(99.dp)).background(it))
                    Spacer(Modifier.width(7.dp))
                }
                Text(value, color = SecondaryText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

@Composable
private fun AppearancePickerDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp),
            shape = RoundedCornerShape(28.dp),
            color = ForegroundSurface,
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 26.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun AppearancePickerOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    dotColor: Color? = null,
    previewIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    previewScale: Float? = null,
) {
    val currentIconScale = LocalAppIconScale.current
    val previewMultiplier = previewScale?.let { it / currentIconScale } ?: 1f
    val titleStyle = MaterialTheme.typography.titleMedium.let { style ->
        style.copy(fontSize = style.fontSize * previewMultiplier, lineHeight = style.lineHeight * previewMultiplier)
    }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dotColor?.let {
            Box(Modifier.size(14.dp).clip(RoundedCornerShape(99.dp)).background(it))
            Spacer(Modifier.width(12.dp))
        }
        previewIcon?.let {
            Icon(it, contentDescription = null, modifier = Modifier.size(scaledAppIconSize(20.dp) * previewMultiplier), tint = BodyText)
            Spacer(Modifier.width(10.dp))
        }
        Text(label, modifier = Modifier.weight(1f), style = titleStyle, fontWeight = FontWeight.Medium)
        if (selected) Icon(Icons.Rounded.Check, contentDescription = "已选中", modifier = Modifier.size(scaledAppIconSize(20.dp)), tint = BodyText)
    }
}

@Composable
private fun NotificationReminderSettingsCard(
    state: NotificationReminderSettingsUiState,
    onUpdate: ((com.nanzhufeng.ai.domain.NotificationReminderSettings) -> com.nanzhufeng.ai.domain.NotificationReminderSettings) -> Unit,
) = Column(modifier = Modifier.fillMaxWidth()) {
    val settings = state.settings
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ForegroundSurface,
        shape = CardShape,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                SettingsSwitchRow(
                    title = "计划监控结果通知",
                    summary = if (settings.monitorResultsNotificationEnabled) "监控完成后可发送系统通知；仍需在系统中允许通知" else "已关闭：监控仍会执行，结果只保存在本机任务列表",
                    checked = settings.monitorResultsNotificationEnabled,
                    onCheckedChange = { onUpdate { current -> current.copy(monitorResultsNotificationEnabled = it) } },
                )
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = SubtleDivider)
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                SettingsSwitchRow(
                    title = "对话提醒建议",
                    summary = if (settings.conversationReminderSuggestionsEnabled) "仅少量高价值对话末尾显示“添加提醒 / 监控”" else "已关闭：不再显示对话尾部的提醒建议",
                    checked = settings.conversationReminderSuggestionsEnabled,
                    onCheckedChange = { onUpdate { current -> current.copy(conversationReminderSuggestionsEnabled = it) } },
                )
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = SubtleDivider)
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                SettingsSwitchRow(
                    title = "对话未读提醒",
                    summary = if (settings.unreadConversationIndicatorsEnabled) "在左侧对话列表显示橙色未读标记，不发送系统通知" else "已关闭：不显示左侧对话列表的未读标记",
                    checked = settings.unreadConversationIndicatorsEnabled,
                    onCheckedChange = { onUpdate { current -> current.copy(unreadConversationIndicatorsEnabled = it) } },
                )
            }
        }
    }
    state.notice?.let { Spacer(Modifier.height(10.dp)); Text(it, color = BrandGreen, style = MaterialTheme.typography.bodySmall) }
    state.error?.let { Spacer(Modifier.height(10.dp)); Text(it, color = ErrorRed, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    surface: Boolean = false,
) {
    val rowContent: @Composable (Modifier) -> Unit = { modifier -> Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (summary.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(summary, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.width(12.dp))
        SettingsSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    } }
    if (surface) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            color = ForegroundSurface,
            shape = P5AInteractiveShape,
            shadowElevation = 0.dp,
        ) {
            rowContent(Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        }
    } else {
        rowContent(Modifier.fillMaxWidth())
    }
}

@Composable
private fun WorkspaceSettingsCard(
    projectState: ProjectUiState,
    onOpenProjects: () -> Unit,
    onOpenKnowledge: () -> Unit,
) = Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text("Projects 用于范围整理；开启资料库搜索后，相关本地资料会随当前问题自动检索并加入上下文。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    DataStorageGroupedCard {
        DataStorageGroupedActionRow(
            label = "管理 Projects（${projectState.projects.size}）",
            onClick = onOpenProjects,
            enabled = true,
        )
        DataStorageGroupedDivider()
        DataStorageGroupedActionRow(
            label = "管理知识库",
            onClick = onOpenKnowledge,
            enabled = true,
        )
    }
}

@Composable
private fun DevelopmentDiagnosticsSettingsCard(
    onOpenContext: () -> Unit,
    onOpenOfflineEval: () -> Unit,
) = DataStorageGroupedCard {
    DataStorageGroupedActionRow(
        label = "本次 Context 控制",
        onClick = onOpenContext,
        enabled = true,
    )
    DataStorageGroupedDivider()
    DataStorageGroupedActionRow(
        label = "离线评测",
        onClick = onOpenOfflineEval,
        enabled = true,
    )
}

@Composable
private fun FeatureReviewSettingsCard() {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("新增功能审阅", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("新增能力在进入常用界面前，先在这里列出用途、现有入口与待您判断项。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("模型服务设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("当前：设置 → 模型与联网提供模型设置、费用与用量、上下文记录与运行诊断；模型设置只保留 OpenRouter、Qwen、DeepSeek 三个服务配置。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text("建议：保留设置 → AI 模型服务的单一入口，不在聊天或 Composer 增加按键。三类记录只显示本机安全元数据，不显示 Key、对话正文、附件、提示词或完整响应。", color = SecondaryText, style = MaterialTheme.typography.labelSmall)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("项目、知识与本地控制", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("当前：已重组为设置 → 项目与知识；个性化资料与记忆摘要统一在设置 → 个性化。可进入已有的 Projects、知识、Context 与离线评测。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text("建议：保留设置二级入口；不建议在聊天主页、Composer 或会话详情增加按键，避免把本地管理误解为发送、联网或自动执行。", color = SecondaryText, style = MaterialTheme.typography.labelSmall)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("个性化与记忆摘要", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("当前：Android 已提供设置 → 个性化。启用记忆后，昵称、职业、更多信息与自定义指令可参与相关回答；“记住了”会将安全的长期内容整理进本机记忆摘要，少量高价值对话也可由你点击确认加入。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text("建议：仅保留设置入口与低频的对话尾部确认，不新增常驻聊天或 Composer 按键；Desktop 尚待同一真实普通发送 owner 后再实现。", color = SecondaryText, style = MaterialTheme.typography.labelSmall)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("素材类型标签", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("当前：Android 模型调用会按实际提交顺序标注 <图片>、<PDF> 或 <视频>；回答引用时使用具体类型，不再笼统称为“附件”。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text("建议：作为随消息生成的透明说明保留，不增加聊天主页、Composer 或会话详情按键；Desktop 待有同一多模态发送 owner 后再实现。", color = SecondaryText, style = MaterialTheme.typography.labelSmall)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("后台继续生成", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("当前：Android 在普通模型生成期间显示系统“正在生成回复”通知；回到后台后请求继续运行，完成或手动停止即结束通知。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text("建议：保留为发送期间自动出现的系统状态，不增加聊天主页、Composer 或会话详情按键；断网、强制停止或系统终止时仍保留“结果未知”以避免重复调用。", color = SecondaryText, style = MaterialTheme.typography.labelSmall)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("ChatGPT / Claude ZIP 导入", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("当前：待您判断保留或删减 · 入口：设置 → 数据与导入 → 导入中心。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text("建议：保留设置入口；暂不在对话主页添加快捷按钮，避免高敏感导入被误触。", color = SecondaryText, style = MaterialTheme.typography.labelSmall)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("未关联媒体人工关联", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("当前：待外部归属证据后再判断是否启用 · 入口：ZIP 导入批次详情。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text("建议：仅在存在未关联媒体时提供二级操作；不在聊天主界面常驻功能按钮。", color = SecondaryText, style = MaterialTheme.typography.labelSmall)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Desktop Compare 联网执行", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("当前：待您判断是否保留；需先完成服务商、模型、费用与凭据安全方案。入口：既有对话 Composer 的“对比”操作。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text("阶段 1/2 已有 fail-closed owner 与 Security.framework 边界；建议：复用现有“对比”操作，不新增 Composer 常驻按钮；固定预设与状态只放在设置 → AI 模型服务。", color = SecondaryText, style = MaterialTheme.typography.labelSmall)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("本地精确复用", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("当前：待您判断是否保留；仅完成离线精确键与既有消息引用的安全索引，尚未接入普通聊天执行。入口：设置 → 功能审阅。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text("建议：暂不增加聊天或 Composer 按键；只有将来真实复用、用量和清理能力完整后，再在设置提供独立开关与清理入口，避免误解为联网缓存或省费承诺。", color = SecondaryText, style = MaterialTheme.typography.labelSmall)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("跨端文本会话交换", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("当前：待您判断是否保留；Android 现只可从设置 → 对话导出当前活动、未归属项目且无附件/工具结果的文本会话为 .nfai-exchange，Desktop 可按既有工作区导入。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text("建议：只保留设置二级入口，不在聊天主页或 Composer 增加按键；它不是本机备份、云同步，也不代表完整工作区跨端保真。", color = SecondaryText, style = MaterialTheme.typography.labelSmall)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("完整工作区交换（v2）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("当前：待您判断是否保留；Android 可从设置 → 数据与导入选择单个 v2 包，仅在空本机严格恢复；Desktop 仅可从设置选择 v2 包私有导入，或从已提交私有记录经系统保存位置回导。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text("建议：只保留双端设置二级入口，不在聊天主页、Composer 或工作页增加按键；它不是备份、云同步，也不会覆盖已有本机数据。", color = SecondaryText, style = MaterialTheme.typography.labelSmall)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("批量清理已归档与回收站", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("当前：Android 在设置 → 对话管理 → 已归档 / 回收站提供批量清理。清空已归档只会移入回收站；清空回收站才会永久删除。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text("建议：保留在两个会话管理页面的低频入口，不在聊天主页或 Composer 增加按键；永久删除前必须单独确认。Desktop 尚待同一会话生命周期 owner 后实现。", color = SecondaryText, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun AboutSettingsCard() = Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = CardShape,
    color = ForegroundSurface,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        AboutSettingsSection {
            Text("南枫 AI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("本机对话、项目与知识工作区。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        }
        SettingsCategoryDivider()
        AboutSettingsSection {
            Text("版本信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("Android 版 ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(3.dp))
            Text("构建号 ${BuildConfig.VERSION_CODE}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        }
        SettingsCategoryDivider()
        AboutSettingsSection {
            Text("数据与隐私", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("本机数据、导入导出与权限控制请在“隐私与安全”中查看。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AboutSettingsSection(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
        content = content,
    )
}

@Composable
private fun SettingsCategoryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    iconTint: Color = BodyText,
    grouped: Boolean = false,
    onClick: () -> Unit,
) {
    val rowContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(scaledAppIconSize(20.dp)))
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        }
    }
    if (grouped) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(0.dp),
            color = Color.Transparent,
            content = rowContent,
        )
    } else Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(58.dp),
        shape = P5AInteractiveShape,
        colors = CardDefaults.cardColors(containerColor = ForegroundSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        content = { rowContent() },
    )
}

@Composable
private fun SettingsCategoryRow(
    icon: androidx.compose.ui.graphics.painter.Painter,
    title: String,
    grouped: Boolean = false,
    onClick: () -> Unit,
) {
    val rowContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(icon, contentDescription = null, tint = BodyText, modifier = Modifier.size(scaledAppIconSize(20.dp)))
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        }
    }
    if (grouped) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(0.dp),
            color = Color.Transparent,
            content = rowContent,
        )
    } else Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(58.dp),
        shape = P5AInteractiveShape,
        colors = CardDefaults.cardColors(containerColor = ForegroundSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        content = { rowContent() },
    )
}

@Composable
private fun WorkbenchRoute(expanded: Boolean, content: @Composable ColumnScope.() -> Unit) {
    if (expanded) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1.8f), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
            WhiteCard(modifier = Modifier.weight(1f)) {
                Text("当前工作区", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("展开态只增加导航与辅助阅读区。操作、选择和本地事实仍由原有 ViewModel 与 Room 唯一持有。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
private fun ContextControlCard(state: ConversationFoundationUiState, viewModel: ContextBodySelectionViewModel) = WhiteCard {
    Text("本次 Context", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Text("所有来源默认关闭；选择只在当前控制面内存中，不构造 Prompt 或发送内容。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(12.dp))
    OutlinedButton(
        onClick = { viewModel.showDialog(state.selectedConversationId) },
        enabled = state.selectedConversationId != null,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = P5AInteractiveShape,
    ) { Text("打开 Context 控制面") }
}

@Composable
private fun ControlHub(onRouteSelected: (P5ARoute) -> Unit) = WhiteCard {
    Text("项目、知识与记忆", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Text("入口只切换工作区，不复制 Projects、Memory、Context 或离线 Eval 的业务状态。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(12.dp))
    listOf(P5ARoute.PROJECTS, P5ARoute.KNOWLEDGE, P5ARoute.MEMORY, P5ARoute.CONTEXT, P5ARoute.EVAL, P5ARoute.ADAPTERS).forEach { item ->
        OutlinedButton(onClick = { onRouteSelected(item) }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = P5AInteractiveShape) { Text(item.label) }
        Spacer(Modifier.height(8.dp))
    }
}

/** Full-resolution camera output is temporary app-private data until the attachment owner copies it. */
private fun createCameraCaptureUri(context: Context, prefix: String): Uri? = runCatching {
    val directory = File(context.cacheDir, "camera_capture")
    if (!directory.exists() && !directory.mkdirs()) return@runCatching null
    if (!directory.isDirectory) return@runCatching null
    val file = File.createTempFile("$prefix-", ".jpg", directory)
    FileProvider.getUriForFile(context, "${context.packageName}.attachment-share", file)
}.getOrNull()

@Composable
private fun ConversationFoundationCard(
    state: ConversationFoundationUiState,
    viewModel: ConversationFoundationViewModel,
    projectState: ProjectUiState,
    projectViewModel: ProjectViewModel,
    memoryViewModel: MemoryViewModel,
    memorySummaryGenerationEnabled: Boolean,
    contextBodySelectionViewModel: ContextBodySelectionViewModel,
    scheduledMonitorViewModel: ScheduledMonitorViewModel,
    notificationReminderSettings: com.nanzhufeng.ai.domain.NotificationReminderSettings,
    onRouteSelected: (P5ARoute) -> Unit,
    drawerOpen: Boolean,
    onDrawerOpenChanged: (Boolean) -> Unit,
    onReturnToLifecycleList: (() -> Unit)? = null,
) {
    var workspaceVisible by rememberSaveable { mutableStateOf(true) }
    val context = LocalContext.current
    val conversationVisualPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(com.nanzhufeng.ai.domain.CONVERSATION_ATTACHMENT_MAX_COUNT),
    ) { uris ->
        viewModel.onConversationVisualPickerResults(uris)
    }
    var conversationCameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    val conversationCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val uri = conversationCameraUri?.let(Uri::parse)
        conversationCameraUri = null
        viewModel.onConversationCameraResult(uri, captured)
    }
    val conversationDocumentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.onConversationDocumentPickerResults(uris)
    }
    val temporaryVisualPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(com.nanzhufeng.ai.domain.TemporaryConversationRecovery.MAX_ATTACHMENTS),
    ) { uris ->
        viewModel.onTemporaryVisualPickerResults(uris)
    }
    var temporaryCameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    val temporaryCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val uri = temporaryCameraUri?.let(Uri::parse)
        temporaryCameraUri = null
        viewModel.onTemporaryCameraResult(uri, captured)
    }
    val temporaryDocumentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.onTemporaryDocumentPickerResults(uris)
    }
    WhiteCard {
        Text("对话", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            if (state.conversations.isEmpty()) "还没有本地对话。" else "${state.conversations.size} 个本地对话，当前路径与草稿均可恢复。",
            color = SecondaryText, style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(10.dp))
        Button(onClick = { workspaceVisible = true }, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(16.dp)) { Text("进入本地对话") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { contextBodySelectionViewModel.showDialog(state.selectedConversationId) }, enabled = state.selectedConversationId != null, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(16.dp)) { Text("选择本次 Context 正文") }
    }
    if (workspaceVisible) ConversationWorkspaceDialog(
        state = state,
        notificationReminderSettings = notificationReminderSettings,
        onDismiss = onReturnToLifecycleList ?: { /* Root chat has no dismiss-to-workbench escape hatch. */ },
        interceptsSystemBack = onReturnToLifecycleList != null,
        onCreate = viewModel::createDevelopmentConversation,
        onSelect = viewModel::selectConversation, onSurfaceChanged = viewModel::selectSurface, onDraftChanged = viewModel::updateDraft, onSubmitDraft = viewModel::submitCurrentDraft,
        onRetryNormalSend = viewModel::retryLatestNormalSend, onMarkNormalSendFailed = viewModel::markLatestNormalSendFailed,
        onStartFixture = { viewModel.startDeterministicLocalStream() }, onStartFailureFixture = { viewModel.startDeterministicLocalStream(fail = true) },
        onStop = viewModel::stopLocalStream, onAction = viewModel::performAction, onSwitchBranch = viewModel::switchToBranch, onBranchFromMessage = viewModel::branchFromMessage, onDismissBranchCreation = viewModel::dismissBranchCreation,
        onEditUserMessage = viewModel::editCurrentPathUserMessage,
        onListScope = viewModel::setListScope, onSearchChanged = viewModel::updateSearchQuery, onSearchCategoryChanged = viewModel::selectSearchCategory, onSearchRequested = viewModel::submitSearch, onSearchFocus = viewModel::openSearchHistory, onCloseSearchHistory = viewModel::closeSearchHistory, onFillSearchHistory = viewModel::fillSearchHistory, onClearSearchHistory = viewModel::clearSearchHistory, onCloseSearch = viewModel::closeSearchPanel, onOpenSearchHit = viewModel::openSearchHit, onOpenSearchAttachment = viewModel::openSearchAttachment, onEnsureSearchAttachmentPreview = viewModel::ensureSearchAttachmentPreview,
        onManage = viewModel::manage, onBatchSoftDelete = viewModel::softDeleteConversations, onExport = viewModel::exportCurrentConversation,
        onAddCamera = {
            createCameraCaptureUri(context, "conversation")?.let { uri ->
                conversationCameraUri = uri.toString()
                conversationCamera.launch(uri)
            } ?: viewModel.reportCameraCaptureUnavailable(temporary = false)
        },
        onAddImage = { conversationVisualPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
        onAddFile = { conversationDocumentPicker.launch(arrayOf("video/mp4", "audio/*", "application/pdf", "text/plain", "text/markdown", "application/json", "text/csv")) },
        onRemoveAttachment = viewModel::removeDraftAttachment,
        onOpenImagePreview = viewModel::openImagePreview,
        onCloseImagePreview = viewModel::closeImagePreview,
        onOpenPdfPreview = viewModel::openPdfPreview,
        onOpenPdfPage = viewModel::openPdfPage,
        onClosePdfPreview = viewModel::closePdfPreview,
        onOpenVideoPreview = viewModel::openVideoPreview,
        onCloseVideoPreview = viewModel::closeVideoPreview,
        onOpenAudioPreview = viewModel::openAudioPreview,
        onCloseAudioPreview = viewModel::closeAudioPreview,
        onOpenTextPreview = viewModel::openTextPreview,
        onCloseTextPreview = viewModel::closeTextPreview,
        onRequestAttachmentTransfer = viewModel::requestAttachmentTransfer,
        onRequestAttachmentTransfers = viewModel::requestAttachmentTransfers,
        onConsumeAttachmentTransfer = viewModel::consumeAttachmentTransfer,
        onEnterTemporary = viewModel::enterTemporaryConversation,
        onUpdateTemporaryDraft = viewModel::updateTemporaryDraft,
        onUpdateTemporaryModelOverride = viewModel::updateTemporaryModelOverride,
        onSubmitTemporaryDraft = viewModel::submitTemporaryDraft,
        onExitTemporary = viewModel::leaveTemporaryConversation,
        onSelectP6GModel = viewModel::selectP6GModel,
        onSetCurrentConversationWebSearchEnabled = viewModel::setCurrentConversationWebSearchEnabled,
        onAddTemporaryCamera = {
            createCameraCaptureUri(context, "temporary")?.let { uri ->
                temporaryCameraUri = uri.toString()
                temporaryCamera.launch(uri)
            } ?: viewModel.reportCameraCaptureUnavailable(temporary = true)
        },
        onAddTemporaryImage = { temporaryVisualPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
        onAddTemporaryFile = { temporaryDocumentPicker.launch(arrayOf("video/mp4", "audio/*", "application/pdf", "text/plain", "text/markdown", "application/json", "text/csv")) },
        onRemoveTemporaryDraftAttachment = viewModel::removeTemporaryDraftAttachment,
        projects = projectState.activeProjects,
        currentProjectId = state.currentProjectId?.let { com.nanzhufeng.ai.domain.ProjectId(it) },
        onAssignProject = viewModel::assignProject,
        onCreateProject = projectViewModel::showCreateDialog,
        onManageProjects = { projectViewModel.showDialog() },
        onManageProject = { projectId -> projectViewModel.showDialog(projectId) },
        onPinProject = projectViewModel::pin,
        onArchiveProject = projectViewModel::archive,
        onCreateWorkConversation = viewModel::createWorkConversation,
        onOpenRoute = onRouteSelected,
        onOpenScheduledMonitors = scheduledMonitorViewModel::open,
        onCreateScheduledMonitor = { conversationId, source ->
            scheduledMonitorViewModel.refineConversationReminder(conversationId, source)
        },
        onCreateMemorySummary = { conversationId, draft -> memoryViewModel.saveSuggestedSummary(conversationId, draft) },
        memorySummaryGenerationEnabled = memorySummaryGenerationEnabled,
        drawerOpen = drawerOpen,
        onDrawerOpenChanged = onDrawerOpenChanged,
    )
}

@Composable
private fun ProjectCard(state: ProjectUiState, viewModel: ProjectViewModel) = WhiteCard {
    Text("Projects", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Text(if (state.projects.isEmpty()) "项目用于本地指令、会话与知识范围隔离。" else "${state.projects.size} 个本地项目；系统与安全规则始终优先。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(10.dp))
    Button(onClick = viewModel::showDialog, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(16.dp)) { Text("管理 Projects") }
}

@Composable
private fun MemoryCard(state: MemoryUiState, viewModel: MemoryViewModel) = WhiteCard {
    Text("长期 Memory", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Text(if (state.memories.isEmpty()) "显式创建、可暂停、可删除；启用记忆后会按当前问题自动检索。" else "${state.memories.size} 条可见本地记忆；启用记忆后会按当前问题自动检索。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(10.dp))
    Button(onClick = viewModel::showDialog, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(16.dp)) { Text("管理长期 Memory") }
}

@Composable
private fun PrivacyDataCard(state: PrivacyDataUiState, onOpen: () -> Unit) = Column(modifier = Modifier.fillMaxWidth()) {
    Text("隐私与数据", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Text("本地优先；可查看各类数据占用、范围删除或手动导出不含正文的安全诊断。模型外发遵循当前模型设置。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(10.dp))
    Button(onClick = onOpen, modifier = Modifier.fillMaxWidth().height(46.dp), shape = P5AInteractiveShape) { Text("打开隐私与数据") }
    state.notice?.let { Text(it, color = BrandGreen, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun Header(route: P5ARoute) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(route.label, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(
            when (route) {
                P5ARoute.CAPTURE -> "先把文字或图片安全保存到本机，再进入整理与知识沉淀。"
                P5ARoute.CONVERSATION -> "本地对话、草稿与当前路径由同一份持久化事实恢复。"
                P5ARoute.KNOWLEDGE -> "知识管理与导出保持明确确认和本地优先边界。"
                P5ARoute.PROJECTS -> "项目指令、会话与知识范围隔离由 Projects 统一管理。"
                P5ARoute.MEMORY -> "长期 Memory 必须显式创建；启用记忆后会按当前问题自动检索相关内容。"
                P5ARoute.CONTEXT -> "仅为当前会话显式选择本地 Context；不会构造 Prompt。"
                P5ARoute.EVAL -> "离线 Eval 只验证本地合同，不代表真实服务结果。"
                P5ARoute.SETTINGS -> "模型配置与调用账本保持本地、可审计的产品边界。"
                P5ARoute.ADAPTERS -> "每种导入/网页适配器独立确认，外部数据始终不可信。"
                P5ARoute.CONTROL -> "在紧凑屏中进入其余可恢复的本地工作区。"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = SecondaryText,
        )
    }
}

@Composable
private fun PreviewCard(state: CaptureScreenState, modifier: Modifier) {
    WhiteCard(modifier = modifier.heightIn(min = 320.dp)) {
        Text("当前草稿", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = BrandGreen)
        Spacer(Modifier.height(12.dp))
        when {
            state.isRestoring -> LoadingWorkspace("正在恢复本地草稿…")
            state.capturedImage != null -> CapturedPreview(state.capturedImage)
            state.capturedText != null -> CapturedTextPreview(state.capturedText)
            else -> EmptyWorkspace()
        }
    }
}

@Composable
private fun EmptyWorkspace() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 240.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF5F7F6)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.Image, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(42.dp))
            Text("还没有本地草稿", fontWeight = FontWeight.Medium)
            Text("可输入文字，或从相册选择图片", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CapturedTextPreview(draft: com.nanzhufeng.ai.domain.CaptureDraft) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            draft.text.orEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFF5F7F6))
                .padding(16.dp),
            color = BodyText,
        )
        Text(
            if (draft.sourceEvidence.single().sourceType == com.nanzhufeng.ai.domain.CaptureSourceType.ANDROID_TEXT_SHARE) {
                "来自系统文本分享，已保存到本机。"
            } else {
                "手工输入，已保存到本机。"
            },
            color = SecondaryText,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun LoadingWorkspace(label: String) {
    Box(modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(color = BrandGreen, modifier = Modifier.size(32.dp))
            Text(label, color = SecondaryText)
        }
    }
}

@Composable
private fun CapturedPreview(captured: CapturedImageState) {
    val preview = remember(captured.previewBytes) { decodePreview(captured.previewBytes) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp, max = 460.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFF5F7F6)),
            contentAlignment = Alignment.Center,
        ) {
            if (preview == null) {
                Text("私有副本已保存，但此设备暂时无法生成预览。", color = ErrorRed, modifier = Modifier.padding(20.dp))
            } else {
                Image(
                    bitmap = preview,
                    contentDescription = "已选择图片预览",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 460.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(captured.draft.attachments.single().displayName ?: "相册图片", fontWeight = FontWeight.Medium)
                Text(
                    "${captured.draft.attachments.single().mimeType} · ${formatBytes(captured.draft.attachments.single().byteCount)}",
                    color = SecondaryText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ActionCard(
    state: CaptureScreenState,
    onChooseImage: () -> Unit,
    onSaveText: (String) -> Unit,
    onRequestAi: () -> Unit,
) {
    val saveActionRequester = remember { BringIntoViewRequester() }
    var textInputFocused by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(textInputFocused, imeVisible) {
        if (textInputFocused && imeVisible) saveActionRequester.bringIntoView()
    }
    WhiteCard {
        Text("手工文本", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "输入后保存为本地草稿；空白内容不会覆盖已有草稿。",
            color = SecondaryText,
            style = MaterialTheme.typography.bodyMedium,
        )
        var text by rememberSaveable(state.capturedText?.id?.value) { mutableStateOf(state.capturedText?.text.orEmpty()) }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focus ->
                    textInputFocused = focus.isFocused
                }
                .p5aKeyboardTraversal(),
            minLines = 4,
            label = { Text("输入要捕获的文字") },
            enabled = !state.isRestoring && !state.isSavingText && !state.isImporting && state.pendingCandidate == null,
            shape = RoundedCornerShape(16.dp),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onSaveText(text) },
            enabled = !state.isRestoring && !state.isSavingText && !state.isImporting && state.pendingCandidate == null,
            modifier = Modifier.fillMaxWidth().height(52.dp).bringIntoViewRequester(saveActionRequester),
            shape = RoundedCornerShape(16.dp),
        ) {
            if (state.isSavingText) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("正在保存草稿…")
            } else {
                Icon(Icons.Rounded.Save, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("保存文本草稿")
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("相册输入", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("使用系统图片选择器，不申请整库读取权限。当前不会联网，也不会发送图片。", color = SecondaryText, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onChooseImage,
            enabled = !state.isImporting && !state.isRestoring && !state.isSavingText && state.pendingCandidate == null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ActionOrange),
        ) {
            if (state.isImporting) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("正在复制并保存…")
            } else {
                Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(if (state.capturedImage == null) "从相册选择" else "重新选择图片")
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("本地 Mock 整理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("先查看本次内容范围并单独确认；Mock 不连接真实服务，结果只会成为待核对候选。", color = SecondaryText, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onRequestAi,
            enabled = !state.isRestoring && !state.isImporting && !state.isSavingText && !state.isRunningAi &&
                state.currentDraft() != null && state.pendingCandidate == null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            if (state.isRunningAi) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("本地 Mock 正在运行…")
            } else {
                Text(if (state.pendingCandidate == null) "查看并确认本地整理" else "请先核对当前候选")
            }
        }
    }
}

@Composable
private fun AiEgressConfirmationDialog(
    preview: com.nanzhufeng.ai.domain.AiRequestPreview,
    consentChecked: Boolean,
    onConsentChecked: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ForegroundSurface,
        shape = RoundedCornerShape(24.dp),
        title = { Text("确认本次本地整理", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("服务：${preview.providerLabel}", fontWeight = FontWeight.Medium)
                Text("预设：${preview.presetLabel} · 实际模型：${preview.model.id}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                Text("发送范围：${if (preview.text.isNullOrBlank()) "不发送文字" else "当前草稿文字"}；图片 ${preview.imageCount} 张。", color = BodyText)
                preview.text?.let { text ->
                    Text(text.take(500), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(NeutralAssistantSurface).padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
                Text("隐私：本次由本地 Mock 在设备内生成，不会连接 OpenRouter 或其他真实服务，也不会读取密钥或外发文字、图片。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                Text("费用：本地 Mock 的已验证本地成本为 0；这不代表任何真实服务免费。Token 未估算时会显示为未知。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = consentChecked, onCheckedChange = onConsentChecked)
                    Text("我已确认以上本次内容范围与本地 Mock 语义", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("返回") } },
        confirmButton = { Button(onClick = onConfirm, enabled = consentChecked) { Text("运行本地 Mock") } },
    )
}

@Composable
private fun CandidateReviewDialog(
    candidate: com.nanzhufeng.ai.domain.StoredGeneratedCandidate,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onDiscard: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var title by rememberSaveable(candidate.candidate.id.value) { mutableStateOf(candidate.candidate.title) }
    var body by rememberSaveable(candidate.candidate.id.value) { mutableStateOf(candidate.candidate.body) }
    var saveChecked by rememberSaveable(candidate.candidate.id.value) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ForegroundSurface,
        shape = RoundedCornerShape(24.dp),
        title = { Text("核对生成候选", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(modifier = Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "${if (candidate.providerId == com.nanzhufeng.ai.domain.ProviderId.OPENROUTER) "OpenRouter（真实服务）" else "本地 Mock（非真实服务）"} · ${candidate.modelId}",
                    color = SecondaryText,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("这是候选，尚未保存为知识。可编辑后确认保存，或取消候选；两者都不会删除本地草稿和调用记录。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("标题") }, enabled = !isSaving, shape = RoundedCornerShape(14.dp))
                OutlinedTextField(value = body, onValueChange = { body = it }, modifier = Modifier.fillMaxWidth().p5aKeyboardTraversal(), minLines = 5, label = { Text("候选内容") }, enabled = !isSaving, shape = RoundedCornerShape(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = saveChecked, onCheckedChange = { saveChecked = it }, enabled = !isSaving)
                    Text("确认将编辑后的候选保存为本地知识", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDiscard, enabled = !isSaving) { Text("取消候选") } },
        confirmButton = {
            Button(onClick = { onSave(title, body) }, enabled = !isSaving && saveChecked && (title.isNotBlank() || body.isNotBlank())) {
                if (isSaving) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp)) else Text("确认保存")
            }
        },
    )
}

@Composable
private fun StatusCard(state: CaptureScreenState, onDismiss: () -> Unit) {
    val error = state.error
    val notice = state.notice
    if (error == null && notice == null) return
    WhiteCard(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = if (error == null) Icons.Rounded.CheckCircle else Icons.Rounded.Refresh,
                contentDescription = null,
                tint = if (error == null) BrandGreen else ErrorRed,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(error?.title ?: notice.orEmpty(), fontWeight = FontWeight.SemiBold)
                if (error != null) Text(error.suggestion, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "关闭提示")
            }
        }
    }
}

@Composable
internal fun WhiteCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = ForegroundSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

private fun decodePreview(bytes: ByteArray): ImageBitmap? = runCatching {
    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val largestSide = max(info.size.width, info.size.height)
            if (largestSide > 2048) {
                val scale = 2048f / largestSide
                decoder.setTargetSize(
                    (info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1),
                )
            }
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (max(bounds.outWidth / sample, bounds.outHeight / sample) > 2048) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
    requireNotNull(bitmap).asImageBitmap()
}.getOrNull()

private fun formatBytes(byteCount: Long?): String = when {
    byteCount == null -> "大小未知"
    byteCount < 1024 -> "$byteCount B"
    byteCount < 1024 * 1024 -> "%.1f KB".format(byteCount / 1024.0)
    else -> "%.1f MB".format(byteCount / (1024.0 * 1024.0))
}
