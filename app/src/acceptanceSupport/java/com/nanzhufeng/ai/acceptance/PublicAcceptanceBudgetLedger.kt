package com.nanzhufeng.ai.acceptance

/** Test-only budget state. Persist reserve() before network; a crash keeps it unknown. */
internal object PublicAcceptanceBudgetLedger {
    data class State(val spent:Long,val unknown:Long) {
        init {require(spent>=0 && unknown>=0 && unknown<=spent)}
    }
    fun reserve(state:State,amount:Long):State {
        require(amount>0)
        return State(Math.addExact(state.spent,amount),Math.addExact(state.unknown,amount))
    }
    fun settle(state:State,reserved:Long,knownCharge:Long?):State {
        require(reserved>0 && reserved<=state.unknown)
        if(knownCharge==null) return state
        require(knownCharge>=0)
        return State(Math.addExact(Math.subtractExact(state.spent,reserved),knownCharge),Math.subtractExact(state.unknown,reserved))
    }
}
