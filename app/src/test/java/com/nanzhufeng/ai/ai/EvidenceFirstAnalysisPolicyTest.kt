package com.nanzhufeng.ai.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceFirstAnalysisPolicyTest {
    private val image = ChatAttachment(ChatAttachmentKind.IMAGE, "image/png", "post.png", byteArrayOf(1))

    @Test fun `explicit attachment analysis is evidence-first but does not imply public web search`() {
        val mode = EvidenceFirstAnalysisPolicy.modeFor("请分析这张截图", listOf(image))

        assertTrue(mode.structuredAnalysis)
        assertFalse(mode.liveEvidence)
    }

    @Test fun `ordinary picture viewing does not turn on the analysis or web-search contract`() {
        val mode = EvidenceFirstAnalysisPolicy.modeFor("这是一张旅行照片", listOf(image))

        assertFalse(mode.structuredAnalysis)
        assertFalse(mode.liveEvidence)
    }

    @Test fun `the shared contract demands conclusions evidence counterexamples and risk instead of OCR paraphrase`() {
        val instruction = EvidenceFirstAnalysisPolicy.instruction(
            EvidenceFirstAnalysisPolicy.modeFor("请分析这张图", listOf(image)).copy(liveEvidence = true),
        ).orEmpty()

        assertTrue(instruction.contains("不要把截图版面"))
        assertTrue(instruction.contains("先给直接结论"))
        assertTrue(instruction.contains("已核验事实"))
        assertTrue(instruction.contains("反例或条件"))
        assertTrue(instruction.contains("风险与下一步"))
    }
}
