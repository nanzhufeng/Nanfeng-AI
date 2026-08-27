package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FBP6043ScrollToLatestContractsTest {
    private val source = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()

    @Test
    fun `FB-P6-043 advances by one visible reading page until the true end`() {
        for (token in listOf(
            "val showJumpToLatest by remember",
            "TranscriptJumpControlIdleHideMillis = 3_000L",
            "TranscriptJumpDirection",
            "TranscriptScrollObservation",
            "val activeTranscriptUserDragging by activeTranscriptListState.interactionSource.collectIsDraggedAsState()",
            "manualScrollInProgress",
            "revealedTranscriptJumpDirection = direction",
            "transcriptJumpActivityGeneration += 1",
            "delay(TranscriptJumpControlIdleHideMillis)",
            "revealedTranscriptJumpDirection = null",
            "revealedTranscriptJumpDirection == TranscriptJumpDirection.BOTTOM",
            "activeTranscriptListState.canScrollForward",
            "val showJumpToTop by remember",
            "revealedTranscriptJumpDirection == TranscriptJumpDirection.TOP",
            "activeTranscriptListState.canScrollBackward",
            "animateForwardByVisibleViewport(",
            "animateBackwardByVisibleViewport(",
            "with(density) { floatingComposerHeight.toPx() }",
            "val viewportHeightPx = layoutInfo.viewportSize.height.toFloat()",
            "val visibleReadingHeightPx = (viewportHeightPx - bottomOverlayHeightPx).coerceAtLeast(0f)",
            "animateScrollBy(visibleReadingHeightPx)",
            "animateScrollBy(-visibleReadingHeightPx)",
            "onLongClick = {",
            "activeTranscriptListState.scrollToTrueBottom()",
            "activeTranscriptListState.scrollToTrueTop()",
            "if (workMode) workFollowLatest = true else chatFollowLatest = true",
            "if (workMode) workFollowLatest = false else chatFollowLatest = false",
            "scrollToTrueBottom()",
            "scrollToTrueTop()",
            "while (canScrollForward)",
            "while (canScrollBackward)",
            "scrollBy(viewportHeightPx)",
            "scrollBy(-viewportHeightPx)",
            "listState.layoutInfo.totalItemsCount == 0 || !listState.canScrollForward",
            "if (atLatest) chatFollowLatest = true",
            "if (atLatest) onFollowLatestChanged(true)",
            "collectIsDraggedAsState()",
            "if (chatUserDragging) chatFollowLatest = false",
            "if (workUserDragging) onFollowLatestChanged(false)",
            "val jumpToLatestBottomPadding = floatingComposerHeight + 4.dp",
            ".align(Alignment.BottomCenter)",
            "padding(bottom = jumpToLatestBottomPadding + 28.dp)",
            ".align(Alignment.TopCenter)",
            ".padding(top = 16.dp)",
            "JumpToTopButton(",
            "JumpToLatestButton(",
            "TranscriptJumpButton(",
            "Icons.Rounded.KeyboardArrowUp",
            "Icons.Rounded.KeyboardArrowDown",
            ".combinedClickable(",
            "modifier = Modifier.size(30.dp)",
            "DraftComposer(",
        )) assertTrue("missing $token", source.contains(token))
        assertFalse(source.contains("ConversationScrollAdvanceDistance"))
        assertFalse(source.contains("animateForwardByFixedDistance"))
        assertFalse(source.contains("collect { atLatest -> chatFollowLatest = atLatest }"))
        assertFalse(source.contains("animateToNextAssistantReplyEnd"))
        assertFalse(source.contains("animateScrollToItem(targetItemIndex)"))
        assertFalse(source.contains("Modifier.align(Alignment.BottomEnd).padding(12.dp)"))
    }
}
