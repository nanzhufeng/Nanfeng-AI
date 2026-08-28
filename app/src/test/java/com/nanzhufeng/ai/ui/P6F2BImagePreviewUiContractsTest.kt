package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P6F2BImagePreviewUiContractsTest {
    private val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
    private val viewModel = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()

    @Test fun `image chips open a direct original canvas with viewport-only gesture state`() {
        val viewer = workspace.substring(workspace.indexOf("private fun ImagePreviewDialog"), workspace.indexOf("private fun ComposerSendButton"))
        for (token in listOf("AttachmentPreviewChip", "onOpenImagePreview", "ImagePreviewDialog", "detectTransformGestures", "remember(preview.id)", "size(renderedWidth, renderedHeight)", "ContentScale.FillBounds", "Surface(color = Color.Black", "FilePreviewTopActions", "关闭文件预览")) assertTrue(token, workspace.contains(token))
        assertFalse("original pixels must be remeasured, not magnified from a screen-sized layer", viewer.contains("graphicsLayer(scaleX = zoom"))
        assertFalse(viewer.contains("本地图片预览"))
        assertFalse(viewer.contains("仅本地原图"))
        assertFalse(viewer.contains("双指缩放或拖动只改变当前视口"))
        assertFalse(viewer.contains("http://"))
        assertFalse(viewer.contains("https://"))
    }

    @Test fun `new or removed draft assets reload projections before the UI can open a preview`() {
        assertTrue(viewModel.contains("reload(attachmentBatchNotice(outcome, \"当前会话\"))"))
        assertTrue(viewModel.contains("相机原图已私有复制到本地草稿"))
        assertTrue(viewModel.contains("reload(\"已从当前会话草稿移除图片"))
        assertTrue(viewModel.contains("currentAttachmentReferences"))
        assertFalse(viewModel.contains("selectedPath"))
    }
}
