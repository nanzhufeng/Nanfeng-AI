package com.nanzhufeng.ai.ai

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.nanzhufeng.ai.data.AndroidModelProfileDirectory
import com.nanzhufeng.ai.domain.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AllModelAttachmentPreparationTest {
    @Test fun `every maintained deployment has a cold start profile and a complete media path`() {
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(ApplicationProvider.getApplicationContext<Context>())
        val profiles=AndroidModelProfileDirectory(ApplicationProvider.getApplicationContext<Context>())
        val manifest=org.json.JSONObject(java.io.File("../protocol/model-service/model-service-v1.json").readText()).getJSONArray("models")
        val failures=mutableListOf<String>()
        val pdf=java.io.ByteArrayOutputStream().also { out -> com.tom_roush.pdfbox.pdmodel.PDDocument().use { doc -> doc.addPage(com.tom_roush.pdfbox.pdmodel.PDPage());doc.save(out) } }.toByteArray()
        for(index in 0 until manifest.length()) {
            val preset=ModelPresetId.valueOf(manifest.getJSONObject(index).getString("presetId"))
            val model=profiles.profile(preset)
            if(model==null) {failures+="$preset: missing cold-start input profile";continue}
            for(web in listOf(false,true)) for((kind,mime,bytes) in listOf(
                Triple(ChatAttachmentKind.IMAGE,"image/png",byteArrayOf(1,2,3)),
                Triple(ChatAttachmentKind.PDF,"application/pdf",pdf),
                Triple(ChatAttachmentKind.AUDIO,"audio/wav",byteArrayOf(1,2,3)),
                Triple(ChatAttachmentKind.VIDEO,"video/mp4",byteArrayOf(1,2,3)))) {
                val force=web && model.providerId!=ProviderId.OPENROUTER
                val ready=UniversalChatAttachmentBridge().resolve(ConversationId("matrix"),model,"分析所有材料",listOf(ChatAttachment(kind,mime,"fixture",bytes)),force)
                if(ready !is ChatAttachmentBridgeResult.Ready) failures+="$preset/$mime/web=$web: no material path"
                else if(ready.providerAttachments.isEmpty() && ready.contextText.isBlank() && ready.readingAttachments.isEmpty()) failures+="$preset/$mime/web=$web: lost material"
            }
        }
        assertTrue(failures.joinToString("\n"),failures.isEmpty())
    }
}
