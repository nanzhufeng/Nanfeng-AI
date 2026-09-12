package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P6F2BImagePreviewUiContractsTest {
    private val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
    private val viewModel = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()

    @Test fun `image chips open a direct original canvas with viewport-only gesture state`() {
        val viewer = workspace.substring(workspace.indexOf("private fun ImagePreviewDialog"), workspace.indexOf("private fun ComposerSendButton"))
        for (token in listOf("AttachmentPreviewChip", "onOpenImagePreview", "ImagePreviewDialog", "rememberDecodedBitmap(preview.bytes, preview.id)", "initialOriginalImageScale", "maximumOriginalImageZoom", "imagePreviewGestureTransform", "awaitEachGesture", "OriginalImageZoomLayer", "Constraints.fixed(measuredWidthPx, measuredHeightPx)", "imagePlaceable.place(placedX.toInt(), placedY.toInt())", "ContentScale.Fit", "Surface(color = Color.Black", "FilePreviewTopActions", "关闭文件预览")) assertTrue(token, workspace.contains(token))
        assertFalse("zoomed pixels must not be clamped back to viewport size", viewer.contains("size(renderedWidth, renderedHeight)"))
        assertFalse("original pixels must be remeasured, not magnified from a screen-sized layer", viewer.contains("graphicsLayer(scaleX = zoom"))
        assertFalse("original viewer must never force pixels into arbitrary bounds", viewer.contains("ContentScale.FillBounds"))
        assertFalse(viewer.contains("本地图片预览"))
        assertFalse(viewer.contains("仅本地原图"))
        assertFalse(viewer.contains("双指缩放或拖动只改变当前视口"))
        assertFalse(viewer.contains("http://"))
        assertFalse(viewer.contains("https://"))
        assertTrue(viewer.contains("The stable viewport is the sole image-gesture owner"))
        assertTrue(viewer.contains("gestureZoom = next.zoom"))
        assertTrue(viewer.contains("currentZoom = gestureZoom"))
        assertFalse("image preview must not stack a second transform recognizer", viewer.contains("detectTransformGestures"))
        assertFalse("image preview must not stack a separate tap recognizer", viewer.contains("detectTapGestures"))
        assertFalse("a moving image node must never own pointer input", viewer.substring(viewer.lastIndexOf("Image(")).contains("pointerInput"))
    }

    @Test fun `one horizontal gesture keeps its viewport owner while it opens the next image`() {
        val viewer = workspace.substring(workspace.indexOf("private fun ImagePreviewDialog"), workspace.indexOf("private fun ComposerSendButton"))
        val gestureOwner = viewer.substring(viewer.indexOf(".pointerInput("), viewer.indexOf("awaitEachGesture"))
        assertTrue(viewer.contains("val currentPreviewId = rememberUpdatedState(preview.id)"))
        assertTrue(viewer.contains("val currentGeneratedImageIds = rememberUpdatedState(generatedImageIds)"))
        assertTrue(viewer.contains("val gesturePreviewId = currentPreviewId.value"))
        assertTrue(viewer.contains("val gestureGeneratedImageIds = currentGeneratedImageIds.value"))
        assertTrue(viewer.contains("gestureGeneratedImageIds.getOrNull(targetIndex)"))
        assertFalse("switching an image must not recreate the active gesture owner", gestureOwner.contains("preview.id"))
        assertFalse("changing image dimensions must not split one touchpad swipe into two", gestureOwner.contains("fittedWidthPx"))
        assertFalse("changing image dimensions must not split one touchpad swipe into two", gestureOwner.contains("fittedHeightPx"))
        assertFalse("switching an image must not recreate the active gesture owner", gestureOwner.contains("generatedImageIds"))
    }

    @Test fun `manual pinch accumulates and enlarged image pans inside viewport bounds`() {
        val firstPinch = imagePreviewGestureTransform(
            currentZoom = 1f,
            zoomChange = 1.5f,
            panX = 0f,
            panY = 0f,
            focusX = 150f,
            focusY = 150f,
            placedX = 0f,
            placedY = 50f,
            fittedWidthPx = 300f,
            fittedHeightPx = 200f,
            viewportWidthPx = 300f,
            viewportHeightPx = 300f,
            maximumZoom = 6f,
        )
        val secondPinch = imagePreviewGestureTransform(
            currentZoom = firstPinch.zoom,
            zoomChange = 1.5f,
            panX = 0f,
            panY = 0f,
            focusX = 150f,
            focusY = 150f,
            placedX = firstPinch.offsetX,
            placedY = firstPinch.offsetY,
            fittedWidthPx = 300f,
            fittedHeightPx = 200f,
            viewportWidthPx = 300f,
            viewportHeightPx = 300f,
            maximumZoom = 6f,
        )
        val panned = imagePreviewGestureTransform(
            currentZoom = secondPinch.zoom,
            zoomChange = 1f,
            panX = 24f,
            panY = -18f,
            focusX = 150f,
            focusY = 150f,
            placedX = secondPinch.offsetX,
            placedY = secondPinch.offsetY,
            fittedWidthPx = 300f,
            fittedHeightPx = 200f,
            viewportWidthPx = 300f,
            viewportHeightPx = 300f,
            maximumZoom = 6f,
        )

        assertEquals(1.5f, firstPinch.zoom, 0f)
        assertEquals(2.25f, secondPinch.zoom, 0f)
        assertEquals(secondPinch.zoom, panned.zoom, 0f)
        assertTrue(panned.offsetX > secondPinch.offsetX)
        assertTrue(panned.offsetY < secondPinch.offsetY)
    }

    @Test fun `long originals open width filled and zoom reaches native pixels beyond viewport caps`() {
        val longInitial = initialOriginalImageScale(12_000f, 30_000f, 1_000f, 2_000f)
        assertEquals(1_000f, 12_000f * longInitial, 0.01f)
        assertTrue(30_000f * longInitial > 2_000f)
        val longMaximum = maximumOriginalImageZoom(longInitial)
        assertEquals(1f, longInitial * longMaximum, 0.0001f)

        val ordinaryInitial = initialOriginalImageScale(4_000f, 3_000f, 1_000f, 2_000f)
        assertEquals(1_000f, 4_000f * ordinaryInitial, 0.01f)
        assertTrue(3_000f * ordinaryInitial <= 2_000f)
    }

    @Test fun `tall original pans vertically at its initial scale without requiring a pinch first`() {
        val panned = imagePreviewGestureTransform(
            currentZoom = 1f,
            zoomChange = 1f,
            panX = 0f,
            panY = -300f,
            focusX = 500f,
            focusY = 1_000f,
            placedX = 0f,
            placedY = 0f,
            fittedWidthPx = 1_000f,
            fittedHeightPx = 2_500f,
            viewportWidthPx = 1_000f,
            viewportHeightPx = 2_000f,
            maximumZoom = 6f,
        )
        assertEquals(1f, panned.zoom, 0f)
        assertEquals(-300f, panned.offsetY, 0f)

        val viewer = workspace.substring(workspace.indexOf("private fun ImagePreviewDialog"), workspace.indexOf("private fun ComposerSendButton"))
        assertTrue(viewer.contains("gestureFittedHeightPx * gestureZoom > viewportHeightPx"))
        assertFalse(viewer.contains("gestureZoom > 1.001f &&\n                                                change.pressed"))
    }

    @Test fun `tall original can pinch out to show the whole image and pinch in then pan freely`() {
        val fitWhole = imagePreviewGestureTransform(
            currentZoom = 1f,
            zoomChange = 0.5f,
            panX = 0f,
            panY = 0f,
            focusX = 500f,
            focusY = 1_000f,
            placedX = 0f,
            placedY = 0f,
            fittedWidthPx = 1_000f,
            fittedHeightPx = 2_500f,
            viewportWidthPx = 1_000f,
            viewportHeightPx = 2_000f,
            maximumZoom = 6f,
        )
        assertEquals(0.8f, fitWhole.zoom, 0.0001f)

        val enlarged = imagePreviewGestureTransform(
            currentZoom = fitWhole.zoom,
            zoomChange = 3.125f,
            panX = 0f,
            panY = 0f,
            focusX = 500f,
            focusY = 1_000f,
            placedX = 100f,
            placedY = 0f,
            fittedWidthPx = 1_000f,
            fittedHeightPx = 2_500f,
            viewportWidthPx = 1_000f,
            viewportHeightPx = 2_000f,
            maximumZoom = 6f,
        )
        val moved = imagePreviewGestureTransform(
            currentZoom = enlarged.zoom,
            zoomChange = 1f,
            panX = -180f,
            panY = -260f,
            focusX = 500f,
            focusY = 1_000f,
            placedX = enlarged.offsetX,
            placedY = enlarged.offsetY,
            fittedWidthPx = 1_000f,
            fittedHeightPx = 2_500f,
            viewportWidthPx = 1_000f,
            viewportHeightPx = 2_000f,
            maximumZoom = 6f,
        )
        assertEquals(2.5f, enlarged.zoom, 0.0001f)
        assertTrue(moved.offsetX < enlarged.offsetX)
        assertTrue(moved.offsetY < enlarged.offsetY)
    }

    @Test fun `image region owns focused double tap zoom and a second double tap resets`() {
        val enlarged = imagePreviewDoubleTapTransform(
            currentZoom = 1f,
            focusX = 75f,
            focusY = 100f,
            placedX = 0f,
            placedY = 50f,
            fittedWidthPx = 300f,
            fittedHeightPx = 200f,
            viewportWidthPx = 300f,
            viewportHeightPx = 300f,
            maximumZoom = 6f,
        )
        assertEquals(ImagePreviewDoubleTapZoom, enlarged.zoom, 0f)
        assertEquals(-112.5f, enlarged.offsetX, 0.01f)
        assertEquals(-25f, enlarged.offsetY, 0.01f)
        assertEquals(75f, enlarged.offsetX + 75f * enlarged.zoom, 0.01f)
        assertEquals(100f, enlarged.offsetY + (100f - 50f) * enlarged.zoom, 0.01f)

        val reset = imagePreviewDoubleTapTransform(
            currentZoom = enlarged.zoom,
            focusX = 75f,
            focusY = 100f,
            placedX = enlarged.offsetX,
            placedY = enlarged.offsetY,
            fittedWidthPx = 300f,
            fittedHeightPx = 200f,
            viewportWidthPx = 300f,
            viewportHeightPx = 300f,
            maximumZoom = 6f,
        )
        assertEquals(1f, reset.zoom, 0f)
        assertEquals(0f, reset.offsetX, 0f)
        assertEquals(0f, reset.offsetY, 0f)

        val viewer = workspace.substring(workspace.indexOf("private fun ImagePreviewDialog"), workspace.indexOf("private fun ComposerSendButton"))
        assertTrue(viewer.indexOf("The stable viewport is the sole image-gesture owner") < viewer.lastIndexOf("Image("))
        assertTrue(viewer.contains("sinceLastTap in doubleTapMinTimeMillis..doubleTapTimeoutMillis"))
        assertTrue(viewer.contains("focusX = upPosition.x"))
        assertTrue(viewer.contains("pendingSingleTap = imageGestureScope.launch"))
        assertEquals(1, Regex("imagePreviewDoubleTapTransform\\(").findAll(workspace).count())
    }

    @Test fun `search image cards keep one white-card height and reserve missing content as whitespace`() {
        assertTrue(workspace.contains(".height(204.dp)"))
        assertTrue(workspace.contains("Modifier.fillMaxSize().padding(10.dp)"))
        assertTrue(workspace.contains("verticalArrangement = Arrangement.spacedBy(6.dp)"))
        assertTrue(workspace.contains("minLines = 2, maxLines = 2"))
        assertTrue(workspace.contains("Row(Modifier.fillMaxWidth().height(14.dp)"))
        assertTrue(workspace.contains("formatAttachmentBytes(hit.attachment.byteCount)"))
        assertTrue(workspace.contains("contentScale = ContentScale.Fit"))
    }

    @Test fun `new or removed draft assets reload projections before the UI can open a preview`() {
        assertTrue(viewModel.contains("reload(attachmentBatchNotice(outcome, \"当前会话\"))"))
        assertTrue(viewModel.contains("相机原图已私有复制到本地草稿"))
        assertTrue(viewModel.contains("reload(\"已从当前会话草稿移除图片"))
        assertTrue(viewModel.contains("currentAttachmentReferences"))
        assertFalse(viewModel.contains("selectedPath"))
    }
}
