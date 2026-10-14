package com.nanzhufeng.ai.domain

/** A user-triggered continuation preserves exact saved text; it never guesses a semantic overlap. */
object ChatContinuation {
    const val INSTRUCTION = "上一段回答因连接中断而未完成。请只从上面已生成内容的末尾自然继续，不要重复、改写或概述已有文字；继续完成原问题。"
    private const val MIN_ECHO_CODE_POINTS = 16
    private const val MAX_OVERLAP_CODE_POINTS = 8192

    fun join(checkpoint: String, generated: String): String = checkpoint + stripEcho(checkpoint, generated)

    fun stripEcho(checkpoint: String, generated: String): String {
        if (checkpoint.isEmpty() || generated.isEmpty()) return generated
        if (checkpoint.codePointCount(0, checkpoint.length) >= MIN_ECHO_CODE_POINTS && generated.startsWith(checkpoint))
            return generated.substring(checkpoint.length)
        val pattern = generated.codePoints().limit(MAX_OVERLAP_CODE_POINTS.toLong()).toArray()
        if (pattern.size < MIN_ECHO_CODE_POINTS) return generated
        val failure = IntArray(pattern.size)
        for (i in 1 until pattern.size) {
            var j = failure[i - 1]
            while (j > 0 && pattern[i] != pattern[j]) j = failure[j - 1]
            if (pattern[i] == pattern[j]) j++
            failure[i] = j
        }
        val tail = checkpoint.codePoints().toArray().takeLast(MAX_OVERLAP_CODE_POINTS)
        var overlap = 0
        tail.forEach { point ->
            while (overlap > 0 && (overlap == pattern.size || point != pattern[overlap])) overlap = failure[overlap - 1]
            if (overlap < pattern.size && point == pattern[overlap]) overlap++
        }
        return if (overlap >= MIN_ECHO_CODE_POINTS) generated.substring(generated.offsetByCodePoints(0, overlap)) else generated
    }

    /** Hold a possible checkpoint echo until it is identifiable, then emit append-only deltas. */
    class Stream(checkpoint: String) {
        private val candidates = listOf(checkpoint, checkpoint.substringAfterLast("\n\n"), checkpoint.substringAfterLast('\n'))
            .filter { it.codePointCount(0, it.length) >= MIN_ECHO_CODE_POINTS }.distinct().sortedByDescending(String::length)
        private val pending = StringBuilder()
        private var decided = candidates.isEmpty()
        fun append(delta: String): String {
            if (decided) return delta
            pending.append(delta)
            val text = pending.toString()
            if (candidates.any { it.startsWith(text) }) return ""
            val echo = candidates.firstOrNull { text.startsWith(it) }
            decided = true
            pending.setLength(0)
            return if (echo == null) text else text.substring(echo.length)
        }
        fun finish(): String {
            val text = pending.toString()
            pending.setLength(0)
            decided = true
            return if (text.isNotEmpty() && candidates.any { it.startsWith(text) }) "" else text
        }
    }
}
