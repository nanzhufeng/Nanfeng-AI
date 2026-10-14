package com.nanzhufeng.ai.domain

import java.time.Instant

/** Private business evidence, not diagnostics or an upstream payload archive. */
data class NormalChatSearchSource(val url:String,val title:String?)
data class NormalChatSearchCheckpoint(
    val scopeFingerprint:String,val itemId:String,val evidenceText:String,val sources:List<NormalChatSearchSource>,
    val actualModelId:String,val sourceLabel:String,val createdAt:Instant,val originCallId:String,
    val serviceId:String?=null,val serviceEngineId:String?=null,val planVersion:String?=null,val originCallIds:List<String> = emptyList(),
) {
    init {
        require(scopeFingerprint.matches(Regex("[0-9a-f]{64}")))
        require(itemId.matches(Regex("A[1-9][0-9]*|INVENTORY")))
        require(evidenceText.length in 1..32768 && sources.size<=24)
        require((actualModelId.length in 1..200 || actualModelId.isEmpty() && serviceId==IndependentSearchServiceGrant.SERVICE_ID && serviceEngineId==IndependentSearchServiceGrant.ENGINE_ID) && sourceLabel.length in 1..200 && originCallId.length in 1..180)
    }
}
