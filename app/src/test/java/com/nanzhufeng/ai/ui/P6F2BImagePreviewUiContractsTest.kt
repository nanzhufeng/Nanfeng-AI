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
        for (token in listOf("AttachmentPreviewChip", "onOpenImagePreview", "ImagePreviewDialog", "detectTransformGestures", "remember(preview.id)", "initialOriginalImageScale", "maximumOriginalImageZoom", "size(renderedWidth, renderedHeight)", "ContentScale.Fit", "Surface(color = Color.Black", "FilePreviewTopActions", "关闭文件预览")) assertTrue(token, workspace.contains(token))
        assertFalse("original pixels must be remeasured, not magnified from a screen-sized layer", viewer.contains("graphicsLayer(scaleX = zoom"))
        assertFalse("original viewer must never force pixels into arbitrary bounds", viewer.contains("ContentScale.FillBounds"))
        assertFalse(viewer.contains("本地图片预览"))
        assertFalse(viewer.contains("仅本地原图"))
        assertFalse(viewer.contains("双指缩放或拖动只改变当前视口"))
        assertFalse(viewer.contains("http://"))
        assertFalse(viewer.contains("https://"))
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

    @Test fun `search image cards keep one white-card height and reserve missing content as whitespace`() {
        assertTrue(workspace.contains(".height(218.dp)"))
        assertTrue(workspace.contains("Modifier.fillMaxSize().padding(10.dp)"))
        assertTrue(workspace.contains("minLines = 2, maxLines = 2"))
        assertTrue(workspace.contains("Box(Modifier.fillMaxWidth().height(16.dp))"))
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
