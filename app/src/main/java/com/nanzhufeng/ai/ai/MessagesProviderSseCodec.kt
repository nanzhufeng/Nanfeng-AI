package com.nanzhufeng.ai.ai

/** Messages protocol projected into the same answer-only stream lifecycle. */
internal object MessagesProviderSseCodec {
    @Suppress("UNCHECKED_CAST") private fun Any?.objectValue() = this as? Map<String, Any?>
    private fun Map<String, Any?>.objectValue(key: String) = this[key].objectValue()
    private fun Map<String, Any?>.text(key: String) = this[key] as? String
    fun decode(raw: String): ProviderSseEvent? = runCatching {
        val root = StrictJson.parse(raw).objectValue() ?: return null
        if (root["error"] != null) return ProviderSseEvent(terminal = ProviderStreamTerminal.FAILED)
        val block = root.objectValue("content_block")
        val delta = root.objectValue("delta")
        val message = root.objectValue("message")
        val usage = message?.objectValue("usage") ?: root.objectValue("usage")
        fun number(name: String): Long? = (usage?.get(name) as? java.math.BigDecimal)?.let { runCatching { it.longValueExact() }.getOrNull() }?.takeIf { it >= 0 }
        val input = number("input_tokens")?.let { count -> runCatching { Math.addExact(Math.addExact(count, number("cache_read_input_tokens") ?: 0L), number("cache_creation_input_tokens") ?: 0L) }.getOrNull() }
        val resultBlock = block?.text("type") == "web_search_tool_result"
        val results = if(resultBlock) block?.get("content") as? List<*> else null
        val sources = buildList {
            results?.forEach { candidate ->
                val source=candidate.objectValue() ?: return@forEach
                if(source.text("type")=="web_search_result") ProviderWebSource.fromProvider(source.text("url"),source.text("title"))?.let(::add)
            }
            val citation=delta?.objectValue("citation")
            if(citation?.text("type")=="web_search_result_location") ProviderWebSource.fromProvider(citation.text("url"),citation.text("title"))?.let(::add)
        }.distinctBy { it.url }.take(10)
        val reason=delta?.text("stop_reason")
        val terminal=when {
            // A server-side search result is a tool outcome, not the assistant's terminal.
            // Empty/error queries may be followed by valid sources and a normal end_turn.
            reason in setOf("max_tokens","pause_turn") -> ProviderStreamTerminal.INCOMPLETE
            reason != null && reason.isNotBlank() && reason != "end_turn" -> ProviderStreamTerminal.FAILED
            root.text("type")=="message_stop" -> ProviderStreamTerminal.COMPLETED
            else -> null
        }
        val text=when {
            block?.text("type")=="text" -> block.text("text")
            delta?.text("type")=="text_delta" -> delta.text("text")
            else -> null
        }?.takeIf(String::isNotEmpty)
        ProviderSseEvent(text=text,reasoning=delta?.takeIf { it.text("type")=="thinking_delta" }?.text("thinking"),
            inputTokens=input,outputTokens=number("output_tokens"),cachedInputTokens=number("cache_read_input_tokens"),
            toolCallEncountered=block?.text("type")=="tool_use",webSources=sources,webSearchPerformed=resultBlock && sources.isNotEmpty(),
            returnedModelId=message?.text("model")?.trim()?.takeIf { it.isNotEmpty() && it!="null" && it.length<=200 && it.none(Char::isISOControl) },
            terminal=terminal,messageEndTurn=reason=="end_turn",nativeSearchResultBoundary=resultBlock)
    }.getOrNull()
}
