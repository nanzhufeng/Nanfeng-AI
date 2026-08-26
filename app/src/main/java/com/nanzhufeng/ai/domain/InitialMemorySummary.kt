package com.nanzhufeng.ai.domain

/**
 * User-confirmed first version of the global summary. It is seeded once on an empty local
 * Memory store, then future confirmed summaries are appended as independent, auditable entries.
 */
object InitialMemorySummary {
    /** A soft-deleted summary counts as existing so an explicit deletion survives app restart. */
    fun shouldSeed(existingGlobalSummaryCount: Int): Boolean = existingGlobalSummaryCount == 0

    val entries = listOf(
        MemorySummaryDraft(
            title = "概览",
            body = "主要使用简体中文，长期围绕 AI、软件开发、投资和科技产业进行深入研究，并正在打造多个以“南枫”为品牌的应用。偏好信息密度高、结构清晰的回答：先给结论，再区分已确认事实、推测、判断和待验证内容。对于图文整理、分析框架和长期项目，倾向建立可持续复用的方法，而不是一次性的答案。",
        ),
        MemorySummaryDraft(
            title = "开发项目",
            body = "持续开发多个项目，其中移动端“南枫 AI”是当前重要方向：补充 Token 消耗对应的消费金额，优先从 API 获取真实费用，无法获取时提供估算参考。还维护南枫知识库、南枫记、Android 视频下载器、批量重命名工具，以及规划中的 Windows 工具。开发过程中偏好逐步确认 UI、及时修改，不做一次性大范围调整；强调极简设计、多模型架构、本地优先和长期可维护性。",
        ),
        MemorySummaryDraft(
            title = "工作方式",
            body = "回答遵循固定风格：先结论后原因；分析时明确区分事实、推测和判断；投资问题尽量提供数字区间。上传视频、音频、转写稿等内容时，默认直接开始完整分析，无需额外确认。整理讨论内容时，偏好白色或浅色商业科技风、16:9 信息图，并保持统一视觉规范；涉及 Android UI 时，遵循已建立的 dp 间距视觉基准。",
        ),
        MemorySummaryDraft(
            title = "AI 与技术关注",
            body = "长期关注 AI Agent、Codex、Claude、OpenRouter、Google Gemini 等生态的发展，也持续比较不同模型的实际能力、成本和工作流。研究多模型协作、API Provider Framework、移动端 AI 产品设计以及 Agent 架构，兼顾性能、成本和可持续性；希望每日简报覆盖 AI、科技产业及美国或中国科技政策的重要动态，并标注可靠首发时间。",
        ),
        MemorySummaryDraft(
            title = "投资与产业研究",
            body = "持续跟踪 AI 产业链、美股和部分 A 股行业，重点关注英伟达、微软、谷歌、亚马逊、台积电，以及光模块、HBM、液冷、电力等方向。投资方法强调安全边际、分批布局、关注毛利率、自由现金流、收入增速、客户资本开支和库存变化；已建立围绕算力、电力、散热、存储等因素的长期分析框架，关注产业周期与潜在风险。",
        ),
        MemorySummaryDraft(
            title = "品牌与视觉",
            body = "为“南枫”系列产品建立统一视觉语言，包括极简几何化图标、圆润线条、高对比配色以及统一的信息图规范。持续迭代南枫 AI 图标，希望突出“南枫”的含义，优化线条流动感、圆润程度、橙色底色和白色主体等细节，并保持整体品牌风格一致。",
        ),
        MemorySummaryDraft(
            title = "深入探索",
            body = "希望持续查看各个南枫项目之间的整体规划与演进关系，梳理 AI 研究主题之间的长期联系，并定期回顾投资框架与关注重点。",
        ),
    )
}
