package com.nanzhufeng.ai.data

import android.content.Context
import com.nanzhufeng.ai.domain.ChatRoutingPolicy
import com.nanzhufeng.ai.domain.ChatRoutingPolicyRepository
import com.nanzhufeng.ai.domain.CrossModelReviewPolicy

/** Small, content-free local preferences.  No prompt, reply, model key, or provider response is stored here. */
class AndroidChatRoutingPolicyRepository(context: Context) : ChatRoutingPolicyRepository {
    private val preferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun load(): ChatRoutingPolicy = ChatRoutingPolicy(
        autoRoutingEnabled = preferences.getBoolean(AUTO_ROUTING, true),
        automaticFallbackEnabled = preferences.getBoolean(AUTOMATIC_FALLBACK, true),
        qualityEscalationEnabled = preferences.getBoolean(QUALITY_ESCALATION, false),
        crossModelReviewPolicy = preferences.getString(CROSS_MODEL_REVIEW, CrossModelReviewPolicy.NEVER.name)
            ?.let { runCatching { CrossModelReviewPolicy.valueOf(it) }.getOrNull() }
            ?: CrossModelReviewPolicy.NEVER,
        crossProviderFallbackEnabled = preferences.getBoolean(CROSS_PROVIDER_FALLBACK, false),
    )

    override fun save(policy: ChatRoutingPolicy): ChatRoutingPolicy {
        check(preferences.edit()
            .putBoolean(AUTO_ROUTING, policy.autoRoutingEnabled)
            .putBoolean(AUTOMATIC_FALLBACK, policy.automaticFallbackEnabled)
            .putBoolean(QUALITY_ESCALATION, policy.qualityEscalationEnabled)
            .putString(CROSS_MODEL_REVIEW, policy.crossModelReviewPolicy.name)
            .putBoolean(CROSS_PROVIDER_FALLBACK, policy.crossProviderFallbackEnabled)
            .commit()) { "路由设置无法写入本机。" }
        return load()
    }

    private companion object {
        const val FILE = "chat_routing_policy_v1"
        const val AUTO_ROUTING = "auto_routing"
        const val AUTOMATIC_FALLBACK = "automatic_fallback"
        const val QUALITY_ESCALATION = "quality_escalation"
        const val CROSS_MODEL_REVIEW = "cross_model_review"
        const val CROSS_PROVIDER_FALLBACK = "cross_provider_fallback"
    }
}
