package com.nanzhufeng.ai

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prevents the discarded durable-gateway proposal from silently returning to ordinary chat. */
class DirectProviderRollbackContractsTest {
    @Test
    fun `ordinary chat remains the established direct provider path`() {
        val container = File("src/main/java/com/nanzhufeng/ai/app/AppContainer.kt").readText()
        val executor = File("src/main/java/com/nanzhufeng/ai/ai/NormalChatOpenRouterExecutor.kt").readText()
        val transport = File("src/main/java/com/nanzhufeng/ai/ai/OfficialProviderChatTransport.kt").readText()
        val policy = File("src/main/java/com/nanzhufeng/ai/domain/ChatRoutingPolicy.kt").readText()
        val settings = File("src/main/java/com/nanzhufeng/ai/ui/ModelSettingsUi.kt").readText()

        assertTrue(container.contains("transport = OfficialProviderChatTransport()"))
        assertTrue(container.contains("AndroidNormalChatBackgroundExecution"))
        for (source in listOf(container, executor, transport, policy, settings)) {
            assertFalse("discarded gateway route must not return", source.contains("DURABLE_GATEWAY"))
            assertFalse("discarded gateway transport must not return", source.contains("AndroidGenerationGateway"))
            assertFalse("discarded execution-mode UI must not return", source.contains("ExecutionModeSettingsRow"))
        }
        assertFalse(executor.contains("onDurableTaskAccepted"))
        assertFalse(transport.contains("acknowledgeDurableTerminal"))
        assertFalse(policy.contains("ExecutionMode"))
    }
}
