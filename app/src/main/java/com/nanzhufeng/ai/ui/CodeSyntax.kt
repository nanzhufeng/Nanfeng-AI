package com.nanzhufeng.ai.ui

/** Presentation offsets only. Never transform source bytes or treat code as Markdown. */
internal data class CodeSyntaxSpan(val start: Int, val end: Int, val kind: String)

internal fun codeSyntaxSpans(source: String, language: String?): List<CodeSyntaxSpan> {
    val lang = language.orEmpty().lowercase()
    val supported = "toml ini yaml yml json jsonc js javascript ts typescript jsx tsx java kotlin kt python py bash sh shell zsh sql rust rs go c cpp csharp cs".split(' ')
    if (lang !in supported || source.length > 100_000) return emptyList()
    val comments = if (lang in "toml ini yaml yml python py bash sh shell zsh".split(' ')) "#[^\\n]*" else "//[^\\n]*|/\\*[\\s\\S]*?(?:\\*/|$)"
    val section = if (lang in listOf("toml", "ini")) "^[ \\t]*\\[\\[?[^\\]\\n]+\\]\\]?" else "(?!)"
    val pattern = Regex("""("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*')|($comments)|($section)|(\b[A-Za-z_][\w.-]*(?=[ \t]*[=:]))|(\b(?:0x[0-9a-fA-F]+|\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)\b)|(\b(?:true|false|null|None|True|False|const|let|var|val|fun|function|class|def|return|if|else|for|while|import|from|export|async|await|try|catch|throw|new|public|private|SELECT|FROM|WHERE|INSERT|UPDATE|CREATE|TABLE)\b)""", RegexOption.MULTILINE)
    fun followedByColon(end: Int): Boolean {
        var offset = end
        while (offset < source.length && (source[offset] == ' ' || source[offset] == '\t')) offset++
        return offset < source.length && source[offset] == ':'
    }
    return pattern.findAll(source).map { match ->
        val kind = when {
            match.groups[1] != null -> if (followedByColon(match.range.last + 1)) "key" else "string"
            match.groups[2] != null -> "comment"
            match.groups[3] != null -> "section"
            match.groups[4] != null -> "key"
            match.groups[5] != null -> "number"
            else -> "keyword"
        }
        CodeSyntaxSpan(match.range.first, match.range.last + 1, kind)
    }.toList()
}
