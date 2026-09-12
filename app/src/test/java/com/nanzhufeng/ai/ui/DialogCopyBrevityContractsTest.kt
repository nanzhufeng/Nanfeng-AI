package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DialogCopyBrevityContractsTest {
    @Test
    fun `common dialogs keep only user-facing consequences`() {
        val expectedByFile = mapOf(
            "ConversationWorkspace.kt" to listOf(
                "仅移除这条消息中的附件。",
                "可从回收站恢复。",
                "无法恢复。",
            ),
            "MemoryWorkspace.kt" to listOf(
                "停止生成和使用记忆摘要；已保存内容不会删除。",
            ),
            "PrivacyDataUi.kt" to listOf("此操作不可撤销。"),
            "ScheduledMonitorDialog.kt" to listOf("不发送完整对话或附件。"),
        )
        expectedByFile.forEach { (name, expected) ->
            val source = uiSource(name)
            expected.forEach { copy -> assertTrue("missing compact dialog copy in $name: $copy", source.contains(copy)) }
        }

        val forbiddenByFile = mapOf(
            "ConversationWorkspace.kt" to listOf(
                "共享文件会保留到最后一个引用消失后再清理",
                "不会物理删除消息、附件或调用关联",
                "已保存的记忆摘要和费用统计不会受影响",
            ),
            "MemoryWorkspace.kt" to listOf(
                "历史记录仍可审计",
                "普通对话不再检索或发送已保存的记忆",
            ),
            "ProjectWorkspace.kt" to listOf("这是本机显式归属操作"),
            "KnowledgeRelationshipUi.kt" to listOf("不会自动进入 Context、Prompt、导出或 Provider"),
            "NanfengAiApp.kt" to listOf("任一对象不完整、不可读取或不符合 v2 合同"),
            "PrivacyDataUi.kt" to listOf("先把已归属附件转为正常本机附件并逐项校验"),
            "DualPathConnectionUi.kt" to listOf("Key presence", "ONLINE_PROVIDER 未启动"),
            "ContextBodySelectionUi.kt" to listOf("append-only 动作谱系"),
        )
        forbiddenByFile.forEach { (name, forbidden) ->
            val source = uiSource(name)
            forbidden.forEach { copy -> assertFalse("verbose implementation copy returned in $name: $copy", source.contains(copy)) }
        }
    }

    private fun uiSource(name: String): String = File("src/main/java/com/nanzhufeng/ai/ui/$name").readText()
}
