package com.nanzhufeng.ai.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class P7FCloudConversationRestoreAggregationTest {
    @Test fun `successful updates are counted when other conversations fail`() {
        val outcome = summarizeRemoteConversationRestores(listOf(
            P7FCloudConversationRestoreResult.Updated("更新成功"),
            P7FCloudConversationRestoreResult.Rejected("失败"),
        ))
        assertTrue(outcome is P7FCloudConversationBatchRestoreResult.PartiallyRestored)
        assertEquals(1, (outcome as P7FCloudConversationBatchRestoreResult.PartiallyRestored).updatedCount)
        assertEquals(1, outcome.rejectedCount)
    }
    @Test fun `one invalid cloud conversation does not relabel restored siblings as a failed batch`() {
        val outcome = summarizeRemoteConversationRestores(
            listOf(
                P7FCloudConversationRestoreResult.Restored("已恢复"),
                P7FCloudConversationRestoreResult.Updated("已更新"),
                P7FCloudConversationRestoreResult.Rejected("云端对话恢复失败，未改动本机数据。"),
                P7FCloudConversationRestoreResult.AlreadyPresent("已有"),
            ),
        )

        assertTrue(outcome is P7FCloudConversationBatchRestoreResult.PartiallyRestored)
        outcome as P7FCloudConversationBatchRestoreResult.PartiallyRestored
        assertEquals(1, outcome.restoredCount)
        assertEquals(1, outcome.updatedCount)
        assertEquals(1, outcome.alreadyPresentCount)
        assertEquals(1, outcome.rejectedCount)
        assertEquals("云端对话恢复失败，未改动本机数据。", outcome.rejectedSummary)
        assertEquals("已有", outcome.latestTitle)
    }

    @Test fun `all rejected conversations remain an honest failure without local mutation claim`() {
        val outcome = summarizeRemoteConversationRestores(
            listOf(
                P7FCloudConversationRestoreResult.Rejected("invalid"),
                P7FCloudConversationRestoreResult.Rejected("unavailable"),
            ),
        )

        assertTrue(outcome is P7FCloudConversationBatchRestoreResult.Rejected)
        assertTrue((outcome as P7FCloudConversationBatchRestoreResult.Rejected).message.contains("2 个对话"))
        assertTrue((outcome as P7FCloudConversationBatchRestoreResult.Rejected).message.contains("invalid；unavailable"))
    }
}
