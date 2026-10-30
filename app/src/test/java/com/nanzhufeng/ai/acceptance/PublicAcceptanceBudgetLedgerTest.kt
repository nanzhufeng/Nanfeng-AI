package com.nanzhufeng.ai.acceptance

import org.junit.Assert.*
import org.junit.Test

class PublicAcceptanceBudgetLedgerTest {
    @Test fun `crash after dispatch retains reservation across reload`() {
        val before=PublicAcceptanceBudgetLedger.State(50,0)
        val pending=PublicAcceptanceBudgetLedger.reserve(before,100)
        val reloaded=PublicAcceptanceBudgetLedger.State(pending.spent,pending.unknown)
        assertEquals(150L,reloaded.spent);assertEquals(100L,reloaded.unknown)
        assertEquals(reloaded,PublicAcceptanceBudgetLedger.settle(reloaded,100,null))
    }
    @Test fun `known later call never clears an earlier unknown charge`() {
        val earlier=PublicAcceptanceBudgetLedger.State(150,100)
        val pending=PublicAcceptanceBudgetLedger.reserve(earlier,200)
        val settled=PublicAcceptanceBudgetLedger.settle(pending,200,20)
        assertEquals(170L,settled.spent);assertEquals(100L,settled.unknown)
    }
    @Test fun `reported excess is recorded honestly and invalid amounts cannot lower budget`() {
        val pending=PublicAcceptanceBudgetLedger.reserve(PublicAcceptanceBudgetLedger.State(0,0),100)
        assertEquals(PublicAcceptanceBudgetLedger.State(150,0),PublicAcceptanceBudgetLedger.settle(pending,100,150))
        assertThrows(IllegalArgumentException::class.java) {PublicAcceptanceBudgetLedger.settle(pending,100,-1)}
        assertThrows(IllegalArgumentException::class.java) {PublicAcceptanceBudgetLedger.settle(pending,101,1)}
    }
}
