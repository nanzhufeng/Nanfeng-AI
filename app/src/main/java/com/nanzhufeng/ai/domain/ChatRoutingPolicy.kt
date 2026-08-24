package com.nanzhufeng.ai.domain

/**
 * User-owned routing choices.  These switches are deliberately separate from Provider
 * credentials: changing a provider setting must never silently change where Auto can send.
 */
data class ChatRoutingPolicy(
    val autoRoutingEnabled: Boolean = true,
    val automaticFallbackEnabled: Boolean = true,
    val qualityEscalationEnabled: Boolean = true,
    val crossModelReviewPolicy: CrossModelReviewPolicy = CrossModelReviewPolicy.IMPORTANT_ONLY,
    /** Cross-provider fallback is opt-in.  Auto may fall back inside OpenRouter by default. */
    val crossProviderFallbackEnabled: Boolean = false,
)

enum class CrossModelReviewPolicy { NEVER, IMPORTANT_ONLY, ALWAYS }

interface ChatRoutingPolicyRepository {
    fun load(): ChatRoutingPolicy
    fun save(policy: ChatRoutingPolicy): ChatRoutingPolicy
}

class LoadChatRoutingPolicyUseCase(private val repository: ChatRoutingPolicyRepository) {
    fun execute(): ChatRoutingPolicy = repository.load()
}

class SaveChatRoutingPolicyUseCase(private val repository: ChatRoutingPolicyRepository) {
    fun execute(policy: ChatRoutingPolicy): ChatRoutingPolicy = repository.save(policy)
}
