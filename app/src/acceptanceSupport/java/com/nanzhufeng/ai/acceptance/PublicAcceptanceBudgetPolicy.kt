package com.nanzhufeng.ai.acceptance

import com.nanzhufeng.ai.domain.ConversationCostEstimator
import com.nanzhufeng.ai.domain.ProviderUsage
import com.nanzhufeng.ai.domain.ResolvedModel
import org.json.JSONArray
import org.json.JSONObject

/** Test-only preflight. Catalog price bounds still require current price verification by the operator. */
internal object PublicAcceptanceBudgetPolicy {
    fun requireAllowance(body: JSONObject, model: ResolvedModel?, spent: Long, limit: Long, minimum: Long): Long {
        check(spent >= 0 && limit > 0 && minimum > 0) { "PUBLIC_ACCEPTANCE_INVALID_BUDGET" }
        // Server-side searches can add input and tool charges not bounded by our output cap.
        check((body.optJSONArray("tools")?.length() ?: 0) == 0 &&
            (body.optJSONArray("plugins")?.length() ?: 0) == 0 && !body.optBoolean("enable_search")) {
            "PUBLIC_ACCEPTANCE_UNBOUNDED_TOOL_COST"
        }
        check(textOnly(body.opt("messages")) && textOnly(body.opt("input"))) { "PUBLIC_ACCEPTANCE_UNBOUNDED_MEDIA_COST" }
        val resolved=checkNotNull(model) { "PUBLIC_ACCEPTANCE_UNKNOWN_PRICE_BOUND" }
        check(resolved.modelId==body.optString("model")) { "PUBLIC_ACCEPTANCE_MODEL_BOUND_MISMATCH" }
        val messageCount=(body.optJSONArray("messages")?.length() ?: body.optJSONArray("input")?.length() ?: 1).toLong()
        // Text-only UTF-8 bytes conservatively bound tokenized visible input. Reserve generous
        // framing/system overhead as well; this is a preflight estimate, not a provider billing cap.
        val input=Math.addExact(body.toString().toByteArray(Charsets.UTF_8).size.toLong(),Math.multiplyExact(messageCount+1,4096L))
        val output=listOf("max_output_tokens","max_completion_tokens","max_tokens").firstNotNullOfOrNull {
            body.optLong(it,0).takeIf {n->n>0}
        }
        check(output!=null) { "PUBLIC_ACCEPTANCE_UNKNOWN_OUTPUT_BOUND" }
        val price=checkNotNull(ConversationCostEstimator.estimate(resolved.modelId,ProviderUsage(input,output),modelDisplayName=resolved.displayName)) {
            "PUBLIC_ACCEPTANCE_UNKNOWN_PRICE_BOUND"
        }
        val amount=checkNotNull(price.totalMicros?.takeIf {it>=0}) { "PUBLIC_ACCEPTANCE_UNKNOWN_PRICE_BOUND" }
        val bound=when(price.currencyCode) {
            "USD" -> Math.multiplyExact(amount,8L)
            "CNY" -> amount
            else -> error("PUBLIC_ACCEPTANCE_UNKNOWN_CURRENCY_BOUND")
        }
        val reserved=maxOf(minimum,Math.multiplyExact(bound,2L))
        check(spent<=limit && reserved<=limit-spent) { "PUBLIC_ACCEPTANCE_BUDGET_STOP" }
        return reserved
    }

    private fun textOnly(value: Any?): Boolean = when(value) {
        null, JSONObject.NULL -> true
        is JSONArray -> (0 until value.length()).all {textOnly(value.opt(it))}
        is JSONObject -> {
            val type=value.optString("type")
            (type.isEmpty() || type in setOf("text","input_text","message")) &&
                value.keys().asSequence().all { key -> key !in setOf("image_url","input_audio","video_url","file","file_id") && textOnly(value.opt(key)) }
        }
        else -> true
    }
}
