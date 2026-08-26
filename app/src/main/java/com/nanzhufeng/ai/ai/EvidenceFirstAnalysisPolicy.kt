package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.ProviderId

/**
 * One shared owner for attachment-led analysis.  This deliberately classifies the user's task,
 * never the selected model name: a smaller model must not silently fall back to OCR-style
 * paraphrase just because the request did not use the deep slot.
 */
internal object EvidenceFirstAnalysisPolicy {
    private val analysisRequest = Regex(
        "分析|解读|研判|判断|评估|核实|验证|真假|怎么看|看法|意味着什么|影响|风险|观点|investigate|analy[sz]e|verify|assess|what does .* mean",
        RegexOption.IGNORE_CASE,
    )
    data class Mode(
        val structuredAnalysis: Boolean,
        val liveEvidence: Boolean,
    )

    fun modeFor(userMessage: String, attachments: List<ChatAttachment>): Mode {
        val structured = attachments.isNotEmpty() && analysisRequest.containsMatchIn(userMessage)
        return Mode(
            structuredAnalysis = structured,
            // Attachment analysis is not itself permission or intent to search the public web.
            // `NormalChatOpenRouterExecutor` fills this from the resolved current-information
            // route after the explicit per-conversation web-search preference is applied.
            liveEvidence = false,
        )
    }

    fun instruction(mode: Mode): String? = when {
        !mode.structuredAnalysis -> null
        mode.liveEvidence -> """
            分析合同（严格执行）：用户要求你分析随消息提交的材料，材料不是最终答案。不要把截图版面、账号、点赞数或原文逐段整理当作主要回答。先给直接结论，再清楚区分：已核验事实（每项附可打开来源）、材料中的原始观点、你的推理与因果链、能推翻该判断的反例或条件、以及风险与下一步。

            本轮已启用实时网页检索。涉及市场、行业、公司、政策、图表、数据、AI 或 token 的可验证主张，必须先查找来源再下结论；优先原始公告、监管文件、公司披露、数据发布者或一手研究。无法核实的内容明确写“尚未核实”，不得把截图、转述或模型记忆写成已证实事实。对投资相关内容给出条件化判断、估值/风险传导和可执行的核对点，不把可能性伪装成确定结论，也不提供确定性买卖指令。
        """.trimIndent()
        else -> """
            分析合同（严格执行）：用户要求你分析随消息提交的材料，材料不是最终答案。不要把截图版面、账号、点赞数或原文逐段整理当作主要回答。先给直接结论，再清楚区分：材料中可直接观察到的事实、材料作者的观点、你的推理与因果链、能推翻该判断的反例或条件、以及风险与下一步。没有实时来源时，必须明确哪些事实尚未核实；不得声称已联网或把截图转述当成外部事实。
        """.trimIndent()
    }
}
