package com.nanzhufeng.ai.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.AndroidModelProfileDirectory
import com.nanzhufeng.ai.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MercuryVisionPreparationTest {
    private fun models()=AndroidModelProfileDirectory(ApplicationProvider.getApplicationContext<Context>()).profiles()
    @Test fun `Mercury image is staged for explicit reader instead of rejected or sent raw to Mercury`() {
        val mercury=requireNotNull(models()[ModelPresetId.MERCURY_3])
        val image=ChatAttachment(ChatAttachmentKind.IMAGE,"image/jpeg","public.jpg",byteArrayOf(1,2,3))
        val result=UniversalChatAttachmentBridge().resolve(ConversationId("public-fixture"),mercury,"分析截图",listOf(image),false)
        assertTrue("Mercury image must reach the authorized reading stage",result is ChatAttachmentBridgeResult.Ready)
        result as ChatAttachmentBridgeResult.Ready
        assertTrue(result.providerAttachments.isEmpty())
        assertEquals(listOf(image),result.readingAttachments)
        assertTrue(result.unprojectedSearchAttachments.isEmpty())
    }
}
