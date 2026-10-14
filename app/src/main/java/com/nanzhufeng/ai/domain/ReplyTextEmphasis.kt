package com.nanzhufeng.ai.domain

enum class ReplyEmphasisTone { IMPORTANT, CAUTION }
data class ReplyEmphasisSelection(val span: InlinePresentation.Strong, val tone: ReplyEmphasisTone)

/** A bounded visual projection of authored emphasis; it is never a truth or safety verdict. */
class ReplyEmphasisBudget {
    var cautionSection = false
    private var important = 0
    private var caution = 0
    private var characters = 0
    fun take(value: String): ReplyEmphasisTone? {
        val text=value.trim()
        if (text.length !in 2..40 || text.none(Char::isLetter) ||
            text.any { it in "`[]<>$&" } || text.contains("NFMARKDOWNTOKEN") ||
            text.contains('\n') || characters+text.length>96) return null
        val tone=if(cautionSection || isCaution(text)) ReplyEmphasisTone.CAUTION else ReplyEmphasisTone.IMPORTANT
        if(tone==ReplyEmphasisTone.IMPORTANT) {if(important>=2)return null;important++}
        else {if(caution>=1)return null;caution++}
        characters+=text.length
        return tone
    }
    companion object {
        private val prefix=Regex("^(?:(?:主要|潜在|关键|重要|安全|特别|核心)?(?:风险|注意|警告|限制|提醒)|避免|不要|不建议|谨慎|warning\\b|caution\\b|risks?\\b|limitations?\\b)",RegexOption.IGNORE_CASE)
        fun isCaution(value:String):Boolean {
            val text=value.replace("**", "").replace("__", "").trim()
                .replace(Regex("^(?:[一二三四五六七八九十]+|[0-9]+)[、.）)]\\s*"),"")
            return prefix.containsMatchIn(text)
        }
    }
}

fun replyEmphasisSelections(blocks:List<PresentationBlock>):List<ReplyEmphasisSelection> {
    val budget=ReplyEmphasisBudget()
    return buildList {
        fun select(spans:List<InlinePresentation>) {spans.filterIsInstance<InlinePresentation.Strong>().forEach { span ->
            budget.take(span.value)?.let { add(ReplyEmphasisSelection(span,it)) }
        }}
        blocks.forEach {block -> when(block) {
            is PresentationBlock.Heading -> budget.cautionSection=ReplyEmphasisBudget.isCaution(block.spans.joinToString("") {when(it){
                is InlinePresentation.Text -> it.value
                is InlinePresentation.Strong -> it.value
                else -> ""
            }})
            is PresentationBlock.Paragraph -> select(block.spans)
            is PresentationBlock.UnorderedList -> block.items.forEach {select(it.spans)}
            is PresentationBlock.OrderedList -> block.items.forEach {select(it.spans)}
            else -> Unit
        }}
    }
}
