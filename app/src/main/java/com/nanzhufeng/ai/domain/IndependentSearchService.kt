package com.nanzhufeng.ai.domain

/** A search receiver is a service and engine, separate from the selected response model. */
data class IndependentSearchServiceGrant(
    val disclosureVersion: String,
    val serviceId: String,
    val engineId: String,
    val materialFingerprint: String,
) {
    fun allows(fingerprint: String): Boolean = disclosureVersion == NormalChatEgressAuthorization.DISCLOSURE_VERSION &&
        serviceId == SERVICE_ID && engineId == ENGINE_ID && materialFingerprint == fingerprint && fingerprint.matches(Regex("[0-9a-f]{64}"))
    companion object {
        const val SERVICE_ID = "ZHIPU_WEB_SEARCH"
        const val ENGINE_ID = "search_pro"
        const val LABEL = "智谱网页搜索 · 高级版"
        const val ENDPOINT = "https://open.bigmodel.cn/api/paas/v4/web_search"
    }
}
