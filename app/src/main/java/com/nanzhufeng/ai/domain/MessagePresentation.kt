package com.nanzhufeng.ai.domain

import java.net.URI
import java.security.MessageDigest

/**
 * P3-D's in-memory-only projection boundary. It never accepts provider chunks and it never
 * writes parsed content back into Conversation/Room. Text is untrusted presentation input.
 */
const val MESSAGE_PRESENTATION_PARSER_VERSION = 12

data class PresentationBlockIdentity(
    val messageId: MessageNodeId,
    val contentBlockPosition: Int,
    val parserVersion: Int = MESSAGE_PRESENTATION_PARSER_VERSION,
)

sealed interface InlinePresentation {
    data class Text(val value: String) : InlinePresentation
    /** Markdown `**strong**`: display emphasis only; no stored message content is rewritten. */
    data class Strong(val value: String) : InlinePresentation
    /** Markdown `*emphasis*`: kept distinct from Strong for an accessible visual hierarchy. */
    data class Emphasis(val value: String) : InlinePresentation
    data class Code(val value: String) : InlinePresentation
    /** A strictly http(s) URL rendered as a real Compose link action. */
    data class Link(val label: String, val url: String) : InlinePresentation
}

sealed interface PresentationBlock {
    val identity: PresentationBlockIdentity
    data class Paragraph(override val identity: PresentationBlockIdentity, val spans: List<InlinePresentation>) : PresentationBlock
    /** Parenthetical note metadata is auxiliary reading content, not italic prose emphasis. */
    data class Note(override val identity: PresentationBlockIdentity, val spans: List<InlinePresentation>) : PresentationBlock
    data class Heading(override val identity: PresentationBlockIdentity, val level: Int, val spans: List<InlinePresentation>) : PresentationBlock
    data class UnorderedList(override val identity: PresentationBlockIdentity, val items: List<UnorderedPresentationItem>) : PresentationBlock
    data class OrderedList(override val identity: PresentationBlockIdentity, val items: List<OrderedPresentationItem>) : PresentationBlock
    /** Markdown thematic break such as `---`; it is structure, never literal prose. */
    data class HorizontalRule(override val identity: PresentationBlockIdentity) : PresentationBlock
    data class Quote(override val identity: PresentationBlockIdentity, val spans: List<InlinePresentation>) : PresentationBlock
    data class CodeFence(override val identity: PresentationBlockIdentity, val language: String?, val code: String) : PresentationBlock
    /** A bounded Markdown pipe table; cells reuse the same safe inline projection as prose. */
    data class Table(
        override val identity: PresentationBlockIdentity,
        val headers: List<List<InlinePresentation>>,
        val rows: List<List<List<InlinePresentation>>>,
    ) : PresentationBlock
    /** Lossless fallback for malformed/unknown syntax, including incomplete fenced blocks. */
    data class PlainText(override val identity: PresentationBlockIdentity, val raw: String) : PresentationBlock
    data class AttachmentReference(
        override val identity: PresentationBlockIdentity,
        val attachment: ConversationAttachmentReference,
    ) : PresentationBlock
    /** Kept structurally separate so the UI can default it to a collapsed disclosure. */
    data class Reasoning(override val identity: PresentationBlockIdentity, val text: String) : PresentationBlock
    data class SafeToolSummary(override val identity: PresentationBlockIdentity, val toolName: String, val summary: String) : PresentationBlock
}

data class UnorderedPresentationItem(val spans: List<InlinePresentation>, val depth: Int = 0)
data class OrderedPresentationItem(val ordinal: Int, val spans: List<InlinePresentation>, val depth: Int = 0)

data class PresentedMessage(
    val messageId: MessageNodeId,
    val role: MessageRole,
    val deliveryState: MessageDeliveryState,
    val blocks: List<PresentationBlock>,
)

/** Small, testable contract consumed by Compose LazyColumn; no index is ever a message identity. */
object ConversationListVirtualizationContract {
    fun itemKey(message: PresentedMessage): String = message.messageId.value
    fun contentType(message: PresentedMessage): String = message.role.name
}

/**
 * Cache ownership deliberately ends at this object. The key includes block identity and parser
 * version; a delta changes only its message's text fingerprint, leaving the rest untouched.
 */
class MessagePresentationRenderer(private val parserVersion: Int = MESSAGE_PRESENTATION_PARSER_VERSION) {
    private data class Cached(val fingerprint: String, val blocks: List<PresentationBlock>)
    private val cache = linkedMapOf<PresentationBlockIdentity, Cached>()

    fun render(messages: List<MessageNode>): List<PresentedMessage> {
        val active = linkedSetOf<PresentationBlockIdentity>()
        val projected = messages.map { message ->
            val blocks = message.content.flatMapIndexed { index, block ->
                val identity = PresentationBlockIdentity(message.id, index, parserVersion)
                active += identity
                val fingerprint = block.fingerprint()
                val cached = cache[identity]
                if (cached?.fingerprint == fingerprint) cached.blocks else parse(identity, block).also { cache[identity] = Cached(fingerprint, it) }
            }
            PresentedMessage(message.id, message.role, message.deliveryState, blocks)
        }
        cache.keys.retainAll(active)
        return projected
    }

    fun cachedBlockCount(): Int = cache.size

    /** Reuses the conversation-safe Markdown projection for an inert standalone reading surface. */
    fun renderText(identity: PresentationBlockIdentity, text: String): List<PresentationBlock> =
        SafeMarkdownParser.parse(identity, text)

    private fun parse(identity: PresentationBlockIdentity, block: ContentBlock): List<PresentationBlock> = when (block) {
        is ContentBlock.Text -> block.text.splitLegacyLeakedProviderTrace()?.let { legacy ->
            listOf(PresentationBlock.Reasoning(identity, legacy.reasoning)) + SafeMarkdownParser.parse(identity, legacy.answer)
        } ?: SafeMarkdownParser.parse(identity, block.text)
        is ContentBlock.Attachment -> listOf(PresentationBlock.AttachmentReference(identity, block.attachment))
        is ContentBlock.Reasoning -> listOf(PresentationBlock.Reasoning(identity, block.text))
        is ContentBlock.ProviderToolCall -> emptyList()
        is ContentBlock.ToolResult -> listOf(PresentationBlock.SafeToolSummary(identity, block.toolName, block.safeSummary))
    }

    private fun ContentBlock.fingerprint(): String {
        val raw = when (this) {
            is ContentBlock.Text -> "text|$schemaVersion|$text"
            is ContentBlock.Attachment -> "attachment|$schemaVersion|${attachment.id.value}|${attachment.mimeType}|${attachment.displayName}|${attachment.byteCount}|${attachment.sha256}"
            is ContentBlock.Reasoning -> "reasoning|$schemaVersion|$text"
            is ContentBlock.ProviderToolCall -> "provider-tool-call|$schemaVersion|${callId.orEmpty()}|$toolName|$argumentsJson"
            is ContentBlock.ToolResult -> "tool|$schemaVersion|$toolName|$safeSummary"
        }
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

private data class LegacyLeakedProviderTrace(val reasoning: String, val answer: String)

/**
 * Old DeepSeek Responses records were persisted before the adapter distinguished `reasoning`
 * items from `message.output_text`.  Project only the unmistakable mixed English planning trace
 * as a disclosure on read; no historical message is rewritten, and ordinary English answers are
 * never guessed at or moved.
 */
private fun String.splitLegacyLeakedProviderTrace(): LegacyLeakedProviderTrace? {
    if (LEGACY_PROVIDER_TRACE_PREFIXES.none { trimStart().startsWith(it) }) return null
    val finalStart = LEGACY_PROVIDER_FINAL_OPENING.findAll(this).lastOrNull()?.range?.first ?: return null
    val reasoning = substring(0, finalStart).trim()
    val answer = substring(finalStart).trim()
    return LegacyLeakedProviderTrace(reasoning, answer).takeIf { reasoning.length >= 80 && answer.isNotBlank() }
}

private val LEGACY_PROVIDER_TRACE_PREFIXES = listOf("The user asks:", "Let me ", "I have ", "Now I have ")
private val LEGACY_PROVIDER_FINAL_OPENING = Regex("[\\p{IsHan}]{2,4}，(?:直接给结论|如果只能二选一|结论先说|先给结论)")

private object SafeMarkdownParser {
    private val fence = Regex("^\\s*```([A-Za-z0-9_+.-]{0,32})\\s*$")
    private val heading = Regex("^(#{1,6})\\s+(.+)$")
    private val unordered = Regex("^(\\s*)[-*+]\\s+(.+)$")
    private val ordered = Regex("^(\\s*)(\\d+)\\.\\s+(.+)$")
    private val horizontalRule = Regex("^\\s*(?:-{3,}|\\*{3,}|_{3,})\\s*$")
    private val quote = Regex("^\\s*>\\s?(.*)$")
    private val tableDivider = Regex("^\\s*\\|?\\s*:?-{3,}:?\\s*(?:\\|\\s*:?-{3,}:?\\s*)+\\|?\\s*$")
    private val rawHttpUrl = Regex("https?://[^\\s<>()]+")
    private val parentheticalNote = Regex("^[（(]\\s*(?:注|备注|说明|note)\\s*[：:].*[）)]$", RegexOption.IGNORE_CASE)
    private val looseFenceOpen = Regex("^\\s*(`{2,3})(?:\\s*(bash|sh|shell|zsh|json|kotlin|java|python|javascript|js|xml|html|sql|text|plaintext))?(.*)$", RegexOption.IGNORE_CASE)
    private val inlineHeading = Regex("^(.*?[：:])\\s*(#{1,6})\\s*(\\S.*)$")
    private val compactHeading = Regex("^(\\s*)(#{1,6})(\\S.*)$")
    // A CSS colour token such as #fff is prose/code content, never a Markdown heading whose
    // author merely omitted the conventional space after #.
    private val cssHexLiteral = Regex("^\\s*#[0-9A-Fa-f]{3,8}(?=\\s|/|,|;|$).*$")
    private val escapedMarkdownControl = Regex("\\\\([`*_#])")
    private val compactUnordered = Regex("^(\\s*)([-+*])([\\p{IsHan}A-Za-z（(\"“].*)$")
    private val sourcePreambleOnly = Regex("^(?:(?:主要|核心)?)?(?:参考资料|参考|资料来源|来源|sources?|references?)[：:、,，;；·\\s]*$", RegexOption.IGNORE_CASE)
    private val sourceSeparatorNoise = Regex("^[、,，;；·\\s]+$")
    /** Model-visible material markers remain a stable bold reading label without Markdown. */
    private val standaloneMaterialMarker = Regex("^\\s*<(?:图片|PDF|视频|音频|文件)>\\s*$")
    // Models often write Chinese section labels without Markdown hashes. Treat only familiar,
    // unambiguous section forms as hierarchy; ordinary short prose remains ordinary prose.
    private val chineseNumberedHeading = Regex("^(?:第[一二三四五六七八九十百]+(?:层|部分|章|节)?[：:]|[一二三四五六七八九十百]+[、.．])\\s*.+$")
    private val shortStandaloneHeading = Regex("^.{2,28}[：:]?$")

    fun parse(identity: PresentationBlockIdentity, source: String): List<PresentationBlock> {
        val lines = normalizeLooseMarkdown(source).split("\n")
        // Markdown generators use either two or four spaces for a nested list. Map the
        // distinct indentation columns present in this document to semantic list depths, so
        // both conventions render as one visual level at a time.
        val listIndentColumns = lines.mapNotNull(::listIndentation).distinct().sorted()
        val result = mutableListOf<PresentationBlock>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            if (line.isBlank()) { index++; continue }
            val fenceMatch = fence.matchEntire(line)
            if (fenceMatch != null) {
                val close = (index + 1 until lines.size).firstOrNull { fence.matchEntire(lines[it]) != null }
                val language = fenceMatch.groupValues[1].ifBlank { null }
                result += PresentationBlock.CodeFence(identity, language, lines.subList(index + 1, close ?: lines.size).joinToString("\n"))
                index = close?.plus(1) ?: lines.size
                continue
            }
            if (index + 1 < lines.size && tableCells(line) != null && tableDivider.matches(lines[index + 1])) {
                val headers = tableCells(line)!!
                val rows = mutableListOf<List<List<InlinePresentation>>>()
                index += 2
                while (index < lines.size) {
                    val cells = tableCells(lines[index]) ?: break
                    rows += cells.map(::inline)
                    index++
                }
                // Provider transition rows may omit trailing empty cells or add a column.
                // Keep every supplied cell, and never flatten unrelated document blocks.
                val width = maxOf(headers.size, rows.maxOfOrNull { it.size } ?: 0)
                fun pad(cells: List<List<InlinePresentation>>) = cells + List(width - cells.size) { emptyList<InlinePresentation>() }
                result += PresentationBlock.Table(identity, pad(headers.map(::inline)), rows.map(::pad))
                continue
            }
            if (horizontalRule.matches(line)) {
                result += PresentationBlock.HorizontalRule(identity)
                index++
                continue
            }
            noteSpans(line)?.let { spans ->
                result += PresentationBlock.Note(identity, spans)
                index++
                continue
            }
            if (standaloneMaterialMarker.matches(line)) {
                result += PresentationBlock.Paragraph(identity, listOf(InlinePresentation.Strong(line.trim())))
                index++
                continue
            }
            inferredHeadingLevel(lines, index)?.let { level ->
                result += PresentationBlock.Heading(identity, level, inline(line.trim()))
                index++
                return@let
            } ?:
            heading.matchEntire(line)?.let { match ->
                result += PresentationBlock.Heading(identity, match.groupValues[1].length, inline(match.groupValues[2]))
                index++
                return@let
            } ?: run {
                val unorderedFirst = unordered.matchEntire(line)
                val orderedFirst = ordered.matchEntire(line)
                val quoteFirst = quote.matchEntire(line)
                when {
                    unorderedFirst != null -> {
                        val items = mutableListOf<UnorderedPresentationItem>()
                        while (index < lines.size) {
                            val item = unordered.matchEntire(lines[index])
                            if (item != null) {
                                items += UnorderedPresentationItem(
                                    spans = inline(item.groupValues[2]),
                                    depth = listDepth(item.groupValues[1], listIndentColumns),
                                )
                                index++
                            } else if (lines[index].isBlank() && unordered.matchEntire(lines.getOrNull(index + 1).orEmpty()) != null) {
                                index++
                            } else break
                        }
                        result += PresentationBlock.UnorderedList(identity, attachUnorderedListOnlySources(items))
                    }
                    orderedFirst != null -> {
                        val items = mutableListOf<OrderedPresentationItem>()
                        while (index < lines.size) {
                            val item = ordered.matchEntire(lines[index])
                            if (item != null) {
                                items += OrderedPresentationItem(
                                    ordinal = item.groupValues[2].toInt(),
                                    spans = inline(item.groupValues[3]),
                                    depth = listDepth(item.groupValues[1], listIndentColumns),
                                )
                                index++
                            } else if (lines[index].isBlank() && ordered.matchEntire(lines.getOrNull(index + 1).orEmpty()) != null) {
                                index++
                            } else break
                        }
                        result += PresentationBlock.OrderedList(identity, attachOrderedListOnlySources(items))
                    }
                    quoteFirst != null -> {
                        val quoted = mutableListOf<String>()
                        while (index < lines.size) {
                            val item = quote.matchEntire(lines[index]) ?: break
                            quoted += item.groupValues[1]; index++
                        }
                        result += PresentationBlock.Quote(identity, inline(quoted.joinToString("\n")))
                    }
                    else -> {
                        val paragraph = mutableListOf<String>()
                        while (index < lines.size && lines[index].isNotBlank() &&
                            fence.matchEntire(lines[index]) == null && heading.matchEntire(lines[index]) == null &&
                            inferredHeadingLevel(lines, index) == null &&
                            unordered.matchEntire(lines[index]) == null && ordered.matchEntire(lines[index]) == null &&
                            horizontalRule.matches(lines[index]).not() && quote.matchEntire(lines[index]) == null
                        ) { paragraph += lines[index]; index++ }
                        result += PresentationBlock.Paragraph(identity, inline(paragraph.joinToString("\n")))
                    }
                }
            }
        }
        return collapseDedicatedSourceSections(attachStandaloneSourceBlocks(result).filterNot { block -> block.isSourceSeparatorNoise() })
            .ifEmpty { listOf(PresentationBlock.PlainText(identity, source.withoutMarkdownControlDebris())) }
    }

    /** Repairs only unambiguous near-Markdown syntax in the in-memory reading projection. */
    private fun normalizeLooseMarkdown(source: String): String {
        val normalized = mutableListOf<String>()
        var openFence: Pair<Int, Boolean>? = null
        source.replace("\r\n", "\n").replace('\r', '\n').split("\n").forEach { originalLine ->
            val activeFence = openFence
            if (activeFence != null) {
                val ticks = activeFence.first
                val closeOnly = Regex("^\\s*`{$ticks}\\s*$")
                val closingSuffix = Regex("^(.*?)`{$ticks}\\s*$")
                when {
                    closeOnly.matches(originalLine) -> {
                        normalized += "```"
                        openFence = null
                    }
                    closingSuffix.matches(originalLine) -> {
                        val code = closingSuffix.matchEntire(originalLine)!!.groupValues[1]
                        if (code.isNotBlank()) normalized += code
                        normalized += "```"
                        openFence = null
                    }
                    else -> normalized += originalLine
                }
                return@forEach
            }

            // Provider output frequently escapes Markdown controls (\\# / \\* / \\`).  This
            // projection accepts those explicit escapes as normal markup, while persisted text
            // remains byte-for-byte untouched.
            val displayLine = originalLine.replace(escapedMarkdownControl, "$1")
            val opening = looseFenceOpen.matchEntire(displayLine)
            if (opening != null) {
                val ticks = opening.groupValues[1]
                val language = opening.groupValues[2]
                val remainder = opening.groupValues[3]
                if (language.isNotBlank() || remainder.isBlank()) {
                    normalized += "```$language"
                    remainder.trimStart().takeIf(String::isNotBlank)?.let(normalized::add)
                    openFence = ticks.length to (ticks.length == 2)
                    return@forEach
                }
            }

            val headingSplit = inlineHeading.matchEntire(displayLine)
            val candidates = if (headingSplit != null) {
                listOf(headingSplit.groupValues[1].trimEnd(), "${headingSplit.groupValues[2]} ${headingSplit.groupValues[3].trimStart()}")
            } else listOf(displayLine)
            candidates.forEach { candidate ->
                val withHeadingSpacing = if (heading.matches(candidate)) candidate else compactHeading.matchEntire(candidate)?.let { match ->
                    if (cssHexLiteral.matches(candidate)) candidate else
                        "${match.groupValues[1]}${match.groupValues[2]} ${match.groupValues[3].trimStart()}"
                } ?: candidate
                val withCompactListSpacing = compactUnordered.matchEntire(withHeadingSpacing)?.let { match ->
                    "${match.groupValues[1]}${match.groupValues[2]} ${match.groupValues[3]}"
                } ?: withHeadingSpacing
                normalized += withCompactListSpacing.replace(Regex("([。！？；：])\\s*\\*\\s+(?=\\S)"), "$1\n- ")
            }
        }
        if (openFence?.second == true) normalized += "```"
        // Join only a standalone source destination, never literal fenced code.
        var inFence = false
        var index = 0
        while (index < normalized.size) {
            val line = normalized[index]
            if (fence.matches(line)) inFence = !inFence
            else if (!inFence && line.trimEnd().endsWith(']') && normalized.getOrNull(index + 1)?.trimStart()?.startsWith('(') == true) {
                val next = normalized[index + 1].trimStart()
                val candidate = line.trimEnd() + next
                val start = candidate.indexOf('[')
                val link = markdownLinkAt(candidate, start)
                if (start >= 0 && link?.end == candidate.length &&
                    candidate.substring(0, start).trim().matches(Regex("(?:[-+*]|\\d+[.)])?"))) {
                    normalized[index] = candidate
                    normalized.removeAt(index + 1)
                }
            }
            index++
        }
        return normalized.joinToString("\n")
    }

    /**
     * A raw URL on its own line is source metadata, never a paragraph. Attach it to the closest
     * readable preceding block so the transcript has one inline source affordance at that text's
     * end instead of an empty icon row.
     */
    private fun attachStandaloneSourceBlocks(blocks: List<PresentationBlock>): List<PresentationBlock> {
        val attached = mutableListOf<PresentationBlock>()
        blocks.forEach { block ->
            val sources = ((block as? PresentationBlock.Paragraph)?.spans)?.sourceOnlyLinks()
            if (sources.isNullOrEmpty()) {
                attached += block
            } else {
                val ownerIndex = attached.indexOfLast { it is PresentationBlock.Paragraph || it is PresentationBlock.Note || it is PresentationBlock.Heading || it is PresentationBlock.Quote || it is PresentationBlock.UnorderedList || it is PresentationBlock.OrderedList }
                if (ownerIndex < 0) {
                    // No readable owner exists; retain the data rather than silently discarding it.
                    attached += block
                } else {
                    attached[ownerIndex] = attached[ownerIndex].appendSources(sources)
                }
            }
        }
        return attached
    }

    /**
     * Models sometimes append a bibliography-like “可查资料入口” section after an answer. That
     * section is navigation metadata, not a second piece of prose: hide its heading and labels,
     * then put the verified website shortcuts at the end of the preceding actual conclusion.
     * We only collapse it when it contains at least one URL, so a user-authored section named
     * “来源” without source links remains fully visible.
     */
    private fun collapseDedicatedSourceSections(blocks: List<PresentationBlock>): List<PresentationBlock> {
        val visible = mutableListOf<PresentationBlock>()
        var index = 0
        while (index < blocks.size) {
            val header = blocks[index]
            if (!header.isDedicatedSourceSectionHeader()) {
                visible += header
                index++
                continue
            }
            val section = mutableListOf<PresentationBlock>()
            var cursor = index + 1
            while (cursor < blocks.size && blocks[cursor].isDedicatedSourceSectionItem()) {
                section += blocks[cursor]
                cursor++
            }
            val sources = (header.sourceLinks() + section.flatMap { block -> block.sourceLinks() }).distinctBy(InlinePresentation.Link::url)
            val ownerIndex = visible.indexOfLast { it.isReadableSourceOwner() }
            if (sources.isEmpty()) {
                // A source heading without a valid http(s) target is provider formatting debris,
                // never reader-facing "来源：、 、 、" prose.
            } else if (ownerIndex < 0) {
                visible += header
                visible += section
            } else {
                visible[ownerIndex] = visible[ownerIndex].appendSources(sources)
            }
            index = cursor
        }
        return visible
    }

    private fun PresentationBlock.isDedicatedSourceSectionHeader(): Boolean {
        val label = when (this) {
            is PresentationBlock.Heading -> spans.visibleLabel()
            is PresentationBlock.Paragraph -> spans.visibleLabel()
            else -> return false
        }.lowercase()
            .replace(Regex("[\\s：:：、,，;；·]"), "")
        return label in setOf("可查资料入口", "可查的资料入口", "参考资料", "资料来源", "来源", "主要参考", "主要来源", "参考", "sources", "references")
    }

    private fun PresentationBlock.isDedicatedSourceSectionItem(): Boolean =
        this is PresentationBlock.UnorderedList || this is PresentationBlock.OrderedList

    private fun PresentationBlock.isReadableSourceOwner(): Boolean = when (this) {
        is PresentationBlock.Paragraph,
        is PresentationBlock.Note,
        is PresentationBlock.Heading,
        is PresentationBlock.Quote,
        is PresentationBlock.UnorderedList,
        is PresentationBlock.OrderedList,
        -> true
        else -> false
    }

    private fun PresentationBlock.isSourceSeparatorNoise(): Boolean =
        this is PresentationBlock.Paragraph && sourceSeparatorNoise.matches(spans.visibleLabel())

    private fun PresentationBlock.sourceLinks(): List<InlinePresentation.Link> = when (this) {
        is PresentationBlock.UnorderedList -> items.flatMap { it.spans.filterIsInstance<InlinePresentation.Link>() }
        is PresentationBlock.OrderedList -> items.flatMap { it.spans.filterIsInstance<InlinePresentation.Link>() }
        is PresentationBlock.Paragraph -> spans.filterIsInstance<InlinePresentation.Link>()
        is PresentationBlock.Note -> spans.filterIsInstance<InlinePresentation.Link>()
        is PresentationBlock.Heading -> spans.filterIsInstance<InlinePresentation.Link>()
        is PresentationBlock.Quote -> spans.filterIsInstance<InlinePresentation.Link>()
        else -> emptyList()
    }

    private fun List<InlinePresentation>.visibleLabel(): String = joinToString("") { span -> when (span) {
        is InlinePresentation.Text -> span.value
        is InlinePresentation.Strong -> span.value
        is InlinePresentation.Emphasis -> span.value
        is InlinePresentation.Code -> span.value
        is InlinePresentation.Link -> span.label
    } }

    private fun attachUnorderedListOnlySources(items: List<UnorderedPresentationItem>): List<UnorderedPresentationItem> {
        val attached = mutableListOf<UnorderedPresentationItem>()
        items.forEach { item ->
            val sources = item.spans.sourceOnlyLinks()
            val ownerIndex = attached.indexOfLast { it.depth <= item.depth }
            if (sources.isNullOrEmpty() || ownerIndex < 0) attached += item
            else attached[ownerIndex] = attached[ownerIndex].copy(spans = attached[ownerIndex].spans + sources)
        }
        return attached
    }

    private fun attachOrderedListOnlySources(items: List<OrderedPresentationItem>): List<OrderedPresentationItem> {
        val attached = mutableListOf<OrderedPresentationItem>()
        items.forEach { item ->
            val sources = item.spans.sourceOnlyLinks()
            val ownerIndex = attached.indexOfLast { it.depth <= item.depth }
            if (sources.isNullOrEmpty() || ownerIndex < 0) attached += item
            else attached[ownerIndex] = attached[ownerIndex].copy(spans = attached[ownerIndex].spans + sources)
        }
        return attached
    }

    private fun List<InlinePresentation>.sourceOnlyLinks(): List<InlinePresentation.Link>? {
        val links = filterIsInstance<InlinePresentation.Link>()
        return links.takeIf { it.isNotEmpty() && all { span -> span is InlinePresentation.Link || (span is InlinePresentation.Text && span.value.isBlank()) } }
    }

    private fun PresentationBlock.appendSources(sources: List<InlinePresentation.Link>): PresentationBlock = when (this) {
        is PresentationBlock.Paragraph -> copy(spans = spans + sources)
        is PresentationBlock.Note -> copy(spans = spans + sources)
        is PresentationBlock.Heading -> copy(spans = spans + sources)
        is PresentationBlock.Quote -> copy(spans = spans + sources)
        is PresentationBlock.UnorderedList -> if (items.isEmpty()) this else copy(items = items.toMutableList().also { rows ->
            val last = rows.lastIndex
            rows[last] = rows[last].copy(spans = rows[last].spans + sources)
        })
        is PresentationBlock.OrderedList -> if (items.isEmpty()) this else copy(items = items.toMutableList().also { rows ->
            val last = rows.lastIndex
            rows[last] = rows[last].copy(spans = rows[last].spans + sources)
        })
        else -> this
    }

    private fun noteSpans(line: String): List<InlinePresentation>? {
        val trimmed = line.trim()
        val unwrapped = if (trimmed.length >= 3 && trimmed.startsWith('*') && trimmed.endsWith('*') && !trimmed.startsWith("**") && !trimmed.endsWith("**")) {
            trimmed.substring(1, trimmed.length - 1).trim()
        } else trimmed
        return unwrapped.takeIf(parentheticalNote::matches)?.let(::inline)
    }

    private fun listIndentation(line: String): Int? {
        val indentation = unordered.matchEntire(line)?.groupValues?.get(1)
            ?: ordered.matchEntire(line)?.groupValues?.get(1)
            ?: return null
        return indentation.fold(0) { columns, character -> columns + if (character == '\t') 4 else 1 }
    }

    private fun listDepth(indentation: String, indentationColumns: List<Int>): Int {
        val columns = indentation.fold(0) { total, character -> total + if (character == '\t') 4 else 1 }
        return indentationColumns.indexOf(columns).coerceAtLeast(0)
    }

    /**
     * A Chinese numbered section is as explicit as a Markdown heading. A short standalone line
     * gets the smallest heading tier only when it introduces a blank-separated block; this
     * preserves normal wrapped paragraphs and never rewrites the persisted content.
     */
    private fun inferredHeadingLevel(lines: List<String>, index: Int): Int? {
        val line = lines[index].trim()
        if (heading.matches(line) || unordered.matches(line) || ordered.matches(line) ||
            quote.matches(line) || horizontalRule.matches(line)
        ) return null
        if (cssHexLiteral.matches(line)) return null
        if (chineseNumberedHeading.matches(line)) return 2
        val next = lines.getOrNull(index + 1) ?: return null
        return if (next.isBlank() && shortStandaloneHeading.matches(line) &&
            !line.endsWith('。') && !line.endsWith('！') && !line.endsWith('？') &&
            !line.startsWith("http://") && !line.startsWith("https://")
        ) 3 else null
    }

    /** Recognizes only explicitly closed inline forms; all other bytes stay visible as text. */
    private fun inline(source: String): List<InlinePresentation> {
        val result = mutableListOf<InlinePresentation>()
        var cursor = 0
        fun appendText(until: Int) { if (until > cursor) result += InlinePresentation.Text(source.substring(cursor, until)) }
        fun nextStrong(from: Int): Int {
            var candidate = source.indexOf("**", from)
            while (candidate >= 0) {
                if (source.getOrNull(candidate - 1) != '*' && source.getOrNull(candidate + 2) != '*') return candidate
                candidate = source.indexOf("**", candidate + 2)
            }
            return -1
        }
        fun nextEmphasis(from: Int): Int {
            var candidate = source.indexOf('*', from)
            while (candidate >= 0) {
                if (source.getOrNull(candidate - 1) != '*' && source.getOrNull(candidate + 1) != '*') return candidate
                candidate = source.indexOf('*', candidate + 1)
            }
            return -1
        }
        while (cursor < source.length) {
            val codeStart = source.indexOf('`', cursor)
            val linkStart = source.indexOf('[', cursor)
            val rawUrlMatch = rawHttpUrl.find(source, cursor)
            val rawUrlStart = rawUrlMatch?.range?.first ?: -1
            val strongStart = nextStrong(cursor)
            val emphasisStart = nextEmphasis(cursor)
            val start = listOf(codeStart, linkStart, rawUrlStart, strongStart, emphasisStart).filter { it >= 0 }.minOrNull() ?: break
            if (start == codeStart) {
                val end = source.indexOf('`', start + 1)
                if (end > start + 1) {
                    val value = source.substring(start + 1, end)
                    appendText(start)
                    result += if (rawHttpUrl.matchEntire(value) != null) InlinePresentation.Link(value, value) else InlinePresentation.Code(value)
                    cursor = end + 1
                    continue
                }
            } else if (start == linkStart) {
                val link = markdownLinkAt(source, start)
                if (link != null) {
                    val wrappedSource = start > cursor && source[start - 1] == '(' && source.getOrNull(link.end) == ')'
                    appendText(if (wrappedSource) start - 1 else start)
                    result += InlinePresentation.Link(link.label, link.url)
                    cursor = link.end + if (wrappedSource) 1 else 0
                    continue
                }
            } else if (start == strongStart) {
                val end = source.indexOf("**", start + 2)
                if (end > start + 2 && source.getOrNull(end - 1) != '*' && source.getOrNull(end + 2) != '*') {
                    appendText(start)
                    result += InlinePresentation.Strong(source.substring(start + 2, end))
                    cursor = end + 2
                    continue
                }
            } else if (start == emphasisStart) {
                val end = source.indexOf('*', start + 1)
                if (end > start + 1 && source.getOrNull(end - 1) != '*' && source.getOrNull(end + 1) != '*') {
                    appendText(start)
                    result += InlinePresentation.Emphasis(source.substring(start + 1, end))
                    cursor = end + 1
                    continue
                }
            } else if (rawUrlMatch != null) {
                val url = rawUrlMatch.value.trimEnd('.', ',', ';', '。', '，', '；')
                if (url.isNotBlank()) {
                    appendText(start)
                    result += InlinePresentation.Link(linkLabel(url), url)
                    rawUrlMatch.value.removePrefix(url).takeIf(String::isNotEmpty)?.let { result += InlinePresentation.Text(it) }
                    cursor = start + rawUrlMatch.value.length
                    continue
                }
            }
            appendText(start + 1); cursor = start + 1
        }
        appendText(source.length)
        val withoutSourcePreamble = result.filterNot { span ->
            span is InlinePresentation.Text && span.value.isSourcePreambleWithoutUrls()
        }
        // A failed opener is consumed one character at a time by the defensive scanner. Join
        // adjacent text before removing its control debris, otherwise ** can arrive as two
        // separately harmless-looking * spans and leak into the reader surface.
        val mergedText = buildList<InlinePresentation> {
            withoutSourcePreamble.forEach { span ->
                val previous = lastOrNull()
                if (span is InlinePresentation.Text && previous is InlinePresentation.Text) {
                    this[lastIndex] = previous.copy(value = previous.value + span.value)
                } else add(span)
            }
        }
        return mergedText.map { span ->
            if (span is InlinePresentation.Text) span.copy(value = span.value.withoutMarkdownControlDebris()) else span
        }.ifEmpty { listOf(InlinePresentation.Text(source.withoutMarkdownControlDebris())) }
    }

    /**
     * Unclosed or escaped Markdown punctuation is formatting debris, not reader-facing prose.
     * Keep operators and real code spans intact: successful inline syntax is already represented
     * by a typed span before this cleanup runs.
     */
    private fun String.withoutMarkdownControlDebris(): String = this
        .replace("`", "")
        .replace(Regex("(?m)^(\\s*)#+\\s*"), "$1")
        .replace(Regex("(?m)^(\\s*)#(?=[0-9A-Fa-f]{3,8}(?:\\b|\\s|/|,|;|$))"), "$1")
        .replace(Regex("\\*{2,3}(?=[\\p{L}\\p{N}])"), "")
        .replace(Regex("(?<=[\\p{L}\\p{N}])\\*{2,3}"), "")
        .replace(Regex("(?m)(^|\\s)\\*(?=\\s)"), "$1")
        .replace(Regex(" {2,}"), " ")

    private fun String.isSourcePreambleWithoutUrls(): Boolean = sourcePreambleOnly.matches(trim())

    private fun tableCells(line: String): List<String>? {
        if (!line.contains('|')) return null
        return line.trim().removePrefix("|").removeSuffix("|").split('|').map(String::trim).takeIf { it.size >= 2 }
    }

    private fun linkLabel(url: String): String = runCatching { URI(url).host?.removePrefix("www.") }.getOrNull() ?: url
}

const val CONVERSATION_DRAFT_MAX_LENGTH = 12_000

object ConversationDraftPolicy {
    fun normalize(text: String, attachments: List<ConversationAttachmentReference>, updatedAt: java.time.Instant): ConversationDraft {
        // A draft is an in-progress editor value, not final message prose. An IME inserts a
        // trailing newline before the user starts the next line; trimming it here races the
        // asynchronous draft write and makes the return key appear to do nothing.
        val normalizedText = text
        require(normalizedText.length <= CONVERSATION_DRAFT_MAX_LENGTH) { "草稿不能超过 $CONVERSATION_DRAFT_MAX_LENGTH 个字符。" }
        val deduplicated = attachments.distinctBy { it.id }
        require(deduplicated.size <= CONVERSATION_ATTACHMENT_MAX_COUNT) { "每个会话最多添加 $CONVERSATION_ATTACHMENT_MAX_COUNT 项附件。" }
        require(deduplicated.all { it.byteCount <= CONVERSATION_ATTACHMENT_MAX_BYTES }) { "单个会话附件不能超过 20 MB。" }
        require(deduplicated.sumOf { it.byteCount } <= CONVERSATION_ATTACHMENT_MAX_TOTAL_BYTES) { "会话附件总大小不能超过 40 MB。" }
        return ConversationDraft(normalizedText, deduplicated, updatedAt)
    }

    fun isSendable(draft: ConversationDraft): Boolean = draft.text.isNotBlank() || draft.attachments.isNotEmpty()
}

sealed interface ConversationDraftResult {
    data class Saved(val draft: ConversationDraft) : ConversationDraftResult
    data class Rejected(val reason: String) : ConversationDraftResult
}

sealed interface ConversationDraftSubmissionResult {
    data class Submitted(val snapshot: ConversationSnapshot) : ConversationDraftSubmissionResult
    data class Rejected(val reason: String) : ConversationDraftSubmissionResult
}

class SaveConversationDraftUseCase(
    private val repository: ConversationDraftRepository,
    private val clock: java.time.Clock,
) {
    fun execute(conversationId: ConversationId, text: String, attachments: List<ConversationAttachmentReference>): ConversationDraftResult = runCatching {
        ConversationDraftResult.Saved(repository.saveDraft(conversationId, ConversationDraftPolicy.normalize(text, attachments, clock.instant())))
    }.getOrElse { ConversationDraftResult.Rejected(it.message ?: "草稿未保存，本地内容保持不变。") }
}

class SubmitConversationDraftUseCase(
    private val conversations: ConversationRepository,
    private val drafts: ConversationDraftRepository,
    private val tree: ConversationTreeService,
) {
    fun execute(conversationId: ConversationId): ConversationDraftSubmissionResult {
        val snapshot = conversations.findById(conversationId) ?: return ConversationDraftSubmissionResult.Rejected("会话未能从本机回读。")
        val draft = drafts.loadDraft(conversationId) ?: return ConversationDraftSubmissionResult.Rejected("草稿未能从本机回读。")
        if (!ConversationDraftPolicy.isSendable(draft)) return ConversationDraftSubmissionResult.Rejected("请输入文字或保留附件后再发送。")
        val content = buildList {
            if (draft.text.isNotBlank()) add(ContentBlock.Text(draft.text))
            draft.attachments.forEach { add(ContentBlock.Attachment(it)) }
        }
        val appended = tree.append(snapshot, AppendMessageRequest(MessageRole.USER, content))
        val cleared = tree.saveDraft(appended, "", emptyList())
        return drafts.submitDraft(cleared, draft)
    }
}

data class ConversationRecoveryPresentation(
    val title: String,
    val detail: String,
    val canContinue: Boolean,
    val canRetry: Boolean,
)

object ConversationRecoveryPresenter {
    fun present(state: ConversationRuntimeState?, message: MessageNode?): ConversationRecoveryPresentation? {
        if (state == null || !state.isTerminal || message == null) return null
        val hasPartial = message.content.filterIsInstance<ContentBlock.Text>().any { it.text.isNotBlank() }
        return when (state.status) {
            ConversationRuntimeStatus.CANCELLED -> ConversationRecoveryPresentation("已停止本地生成", if (hasPartial) "已保留部分内容；可继续或重试。" else "没有可继续的输出；可重新发送。", hasPartial, false)
            ConversationRuntimeStatus.FAILED -> when (state.safeErrorCode) {
                "AUTH", "AUTH_FAILED", "BALANCE", "INSUFFICIENT_BALANCE" -> ConversationRecoveryPresentation("本地状态显示不可自动重试的服务问题", "鉴权或余额问题不能盲目重试；本地 fixture 未读取凭据。", false, false)
                else -> ConversationRecoveryPresentation("本地生成未完成", if (hasPartial) "已保留部分内容；可继续或新建本地回答版本。" else "没有部分输出；请重新发送，不显示继续。", hasPartial, true)
            }
            ConversationRuntimeStatus.COMPLETED -> ConversationRecoveryPresentation("本地回答已完成", "可新建保留原回答的本地回答版本。", false, true)
            ConversationRuntimeStatus.STREAMING -> null
        }
    }
}

/** A deterministic, auditable fixture for list identity and virtualization contract tests. */
object DeterministicLongConversationFixture {
    const val MESSAGE_COUNT = 240
    fun messages(conversationId: ConversationId): List<MessageNode> = (0 until MESSAGE_COUNT).map { index ->
        val id = MessageNodeId("p3d-fixture-message-${index.toString().padStart(3, '0')}")
        MessageNode(
            id = id, conversationId = conversationId,
            parentMessageId = if (index == 0) null else MessageNodeId("p3d-fixture-message-${(index - 1).toString().padStart(3, '0')}"),
            siblingPosition = 0,
            role = if (index % 2 == 0) MessageRole.USER else MessageRole.ASSISTANT,
            content = listOf(ContentBlock.Text("fixture-$index: 可审计的本地长会话内容。")),
            createdAt = java.time.Instant.EPOCH.plusSeconds(index.toLong()),
        )
    }
}
