package com.nanzhufeng.ai.domain

/**
 * The only policy owner for conversational memory summaries.
 *
 * A direct user command grants one-time permission to save a concise summary.  Suggestions are
 * deliberately much narrower: they are never emitted for ordinary questions, transient plans,
 * credentials, or a single turn with no durable personal signal.
 */
data class MemorySummaryDraft(val title: String, val body: String)

object MemorySummaryPolicy {
    private val command = Regex("^\\s*(?:请)?记住(?:了|一下|这[件些]?事)?(?:[：:，,\\s]|$)", RegexOption.IGNORE_CASE)
    private val sensitive = Regex("(?i)(?:密码|api[ _-]?key|authorization|恢复码|银行卡|passcode|bearer\\s+)")
    private val durableSignals = Regex("(?:我叫|我的名字|职业|工作|长期|偏好|习惯|项目|南枫|投资|关注|住在|计划|目标|以后|记忆)")

    fun isExplicitSaveCommand(text: String): Boolean = command.containsMatchIn(text) && !sensitive.containsMatchIn(text)

    /** Only turns with two durable signals are eligible for a low-frequency opt-in. */
    fun suggestionFor(userText: String, assistantText: String): MemorySummaryDraft? {
        if (isExplicitSaveCommand(userText) || sensitive.containsMatchIn(userText)) return null
        if (durableSignals.findAll(userText).count() < 2 || assistantText.length < 80) return null
        val title = when {
            userText.contains("南枫") || userText.contains("项目") -> "个人项目"
            userText.contains("偏好") || userText.contains("设计") -> "开发与设计偏好"
            userText.contains("投资") || userText.contains("关注") -> "长期研究方向"
            else -> "概览"
        }
        val compact = userText.replace(Regex("\\s+"), " ").trim().take(280)
        return MemorySummaryDraft(title, compact)
    }

    fun commandInstruction(): String = """
        用户刚刚明确要求“记住”。请仅从本轮及必要的相邻对话中提取长期有效、会影响未来回答的非敏感信息，整理成一条本机记忆摘要。
        不要保存密码、密钥、验证码、支付信息、详细住址或一次性安排；证据不足时不要编造。
        回复必须严格使用：
        【记忆主题】一个简短分类（如：概览、个人项目、开发与设计偏好、长期研究方向、工作流程偏好、技术与工具、背景与长期规划）
        【记忆摘要】2-5 条简洁、可复用的事实或偏好
    """.trimIndent()

    fun parseModelSummary(text: String): MemorySummaryDraft? {
        val match = Regex("【记忆主题】\\s*(.+?)\\s*【记忆摘要】\\s*(.+)", setOf(RegexOption.DOT_MATCHES_ALL)).find(text) ?: return null
        val title = match.groupValues[1].trim().take(120)
        val body = match.groupValues[2].trim().take(1_500)
        if (title.isBlank() || body.isBlank() || sensitive.containsMatchIn("$title\n$body")) return null
        return MemorySummaryDraft(title, body)
    }
}
