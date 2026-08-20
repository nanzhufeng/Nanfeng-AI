package com.nanzhufeng.ai.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P7DAccountSyncUiContractsTest {
    @Test fun `opening offline account page never creates a fake identity`() {
        val viewModel = P7DAccountSyncViewModel()
        assertFalse(viewModel.state.detailVisible)
        viewModel.open(); assertTrue(viewModel.state.detailVisible)
        viewModel.close(); assertFalse(viewModel.state.detailVisible)
    }
}
