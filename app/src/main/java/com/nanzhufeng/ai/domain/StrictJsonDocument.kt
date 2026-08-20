package com.nanzhufeng.ai.domain

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Bounded JSON syntax shared by local file adapters.  Values are inert data: this parser never
 * resolves a URI, evaluates a tool payload, or permits duplicate object keys to be overwritten.
 */
sealed interface StrictJsonValue {
    data class Obj(val fields: LinkedHashMap<String, StrictJsonValue>) : StrictJsonValue
    data class Arr(val values: List<StrictJsonValue>) : StrictJsonValue
    data class Str(val value: String) : StrictJsonValue
    data class Num(val lexical: String) : StrictJsonValue
    data class Bool(val value: Boolean) : StrictJsonValue
    data object Null : StrictJsonValue
}

enum class StrictJsonFailure { INVALID_UTF8, MALFORMED, DUPLICATE_KEY, TOO_DEEP, STRING_TOO_LONG }
class StrictJsonRejected(val failure: StrictJsonFailure) : RuntimeException()

object StrictJsonDocument {
    fun decodeUtf8(bytes: ByteArray): String = try {
        StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: Exception) { throw StrictJsonRejected(StrictJsonFailure.INVALID_UTF8) }

    fun parseUtf8(bytes: ByteArray, maxDepth: Int, maxStringCodePoints: Int): StrictJsonValue =
        Parser(decodeUtf8(bytes).removePrefix("\uFEFF"), maxDepth, maxStringCodePoints).parse()

    private class Parser(private val source: String, private val maxDepth: Int, private val maxString: Int) {
        private var p = 0
        fun parse(): StrictJsonValue { val value = value(0); ws(); if (p != source.length) reject(StrictJsonFailure.MALFORMED); return value }
        private fun value(depth: Int): StrictJsonValue {
            if (depth > maxDepth) reject(StrictJsonFailure.TOO_DEEP); ws()
            return when (peek()) {
                '{' -> obj(depth + 1); '[' -> arr(depth + 1); '"' -> StrictJsonValue.Str(string())
                't' -> literal("true", StrictJsonValue.Bool(true)); 'f' -> literal("false", StrictJsonValue.Bool(false))
                'n' -> literal("null", StrictJsonValue.Null); '-', in '0'..'9' -> StrictJsonValue.Num(number())
                else -> reject(StrictJsonFailure.MALFORMED)
            }
        }
        private fun obj(depth: Int): StrictJsonValue.Obj { take('{'); ws(); val fields = linkedMapOf<String, StrictJsonValue>(); if (consume('}')) return StrictJsonValue.Obj(fields); while (true) { ws(); if (peek() != '"') reject(StrictJsonFailure.MALFORMED); val key = string(); if (fields.put(key, valueAfterColon(depth)) != null) reject(StrictJsonFailure.DUPLICATE_KEY); ws(); if (consume('}')) return StrictJsonValue.Obj(fields); take(',') } }
        private fun valueAfterColon(depth: Int): StrictJsonValue { ws(); take(':'); return value(depth) }
        private fun arr(depth: Int): StrictJsonValue.Arr { take('['); ws(); val out = mutableListOf<StrictJsonValue>(); if (consume(']')) return StrictJsonValue.Arr(out); while (true) { out += value(depth); ws(); if (consume(']')) return StrictJsonValue.Arr(out); take(',') } }
        private fun string(): String { take('"'); val out = StringBuilder(); while (p < source.length) { when (val c = source[p++]) { '"' -> return out.toString().also { if (it.codePointCount(0, it.length) > maxString) reject(StrictJsonFailure.STRING_TOO_LONG) }; '\\' -> { val e = source.getOrNull(p++) ?: reject(StrictJsonFailure.MALFORMED); out.append(when (e) { '"','\\','/' -> e; 'b' -> '\b'; 'f' -> '\u000c'; 'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'; 'u' -> source.substring(p, (p + 4).also { if (it > source.length) reject(StrictJsonFailure.MALFORMED) }).also { p += 4 }.toIntOrNull(16)?.toChar() ?: reject(StrictJsonFailure.MALFORMED); else -> reject(StrictJsonFailure.MALFORMED) }) }; else -> if (c.code < 0x20) reject(StrictJsonFailure.MALFORMED) else out.append(c) } }; reject(StrictJsonFailure.MALFORMED) }
        private fun number(): String { val start = p; consume('-'); if (consume('0')) { } else { if (peek() !in '1'..'9') reject(StrictJsonFailure.MALFORMED); while (peek() in '0'..'9') p++ }; if (consume('.')) { if (peek() !in '0'..'9') reject(StrictJsonFailure.MALFORMED); while (peek() in '0'..'9') p++ }; if (peek() in listOf('e', 'E')) { p++; if (peek() in listOf('+', '-')) p++; if (peek() !in '0'..'9') reject(StrictJsonFailure.MALFORMED); while (peek() in '0'..'9') p++ }; return source.substring(start, p) }
        private fun literal(text: String, value: StrictJsonValue): StrictJsonValue { if (!source.startsWith(text, p)) reject(StrictJsonFailure.MALFORMED); p += text.length; return value }
        private fun ws() { while (source.getOrNull(p)?.isWhitespace() == true) p++ }
        private fun take(c: Char) { if (!consume(c)) reject(StrictJsonFailure.MALFORMED) }
        private fun consume(c: Char): Boolean = if (peek() == c) { p++; true } else false
        private fun peek(): Char? = source.getOrNull(p)
        private fun reject(failure: StrictJsonFailure): Nothing = throw StrictJsonRejected(failure)
    }
}
