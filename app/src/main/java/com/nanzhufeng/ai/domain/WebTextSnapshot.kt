package com.nanzhufeng.ai.domain

import java.net.IDN
import java.net.URI
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.UUID

const val WEB_TEXT_SNAPSHOT_VERSION = 1
const val WEB_TEXT_MAX_REDIRECTS = 3
const val WEB_TEXT_MAX_HTML_BYTES = 1024 * 1024
const val WEB_TEXT_MAX_EXTRACTED_CODE_POINTS = 60_000

@JvmInline value class WebTextSnapshotTaskId(val value: String) { companion object { fun new() = WebTextSnapshotTaskId(UUID.randomUUID().toString()) } }
@JvmInline value class WebTextSnapshotItemId(val value: String) { companion object { fun new() = WebTextSnapshotItemId(UUID.randomUUID().toString()) } }
enum class WebTextSnapshotStatus { QUEUED, FETCHING, EXTRACTING, AWAITING_CONFIRMATION, PARTIALLY_COMPLETED, COMPLETED, FAILED, CANCELLED }
enum class WebTextSnapshotItemStatus { PENDING_CONFIRMATION, CONFIRMED, SKIPPED, FAILED }
enum class WebTextSnapshotFailure { INVALID_URL, HIGH_SENSITIVITY_URL, DNS_NOT_PUBLIC, REDIRECT_REJECTED, TOO_MANY_REDIRECTS, HTTP_STATUS, CONTENT_TYPE, CONTENT_ENCODING, RESPONSE_TOO_LARGE, TIMEOUT, CERTIFICATE, NETWORK, MALFORMED_HTML, HTML_TOO_COMPLEX, NO_VISIBLE_TEXT, HIGH_SENSITIVITY, PRIVATE_COPY_FAILED, MISSING_PRIVATE_ASSET, INTERRUPTED, KNOWLEDGE_REJECTED }
data class SafeWebUrl(val fetchUrl: String, val displayUrl: String, val host: String)
data class WebTextSnapshotAsset(val storageKey: String, val displayUrl: String, val host: String, val rawHtmlSha256: String, val byteCount: Long, val adapterVersion: Int = WEB_TEXT_SNAPSHOT_VERSION)
data class WebTextSnapshotItem(val id: WebTextSnapshotItemId, val taskId: WebTextSnapshotTaskId, val ordinal: Int, val title: String, val body: String, val candidateSha256: String, val status: WebTextSnapshotItemStatus = WebTextSnapshotItemStatus.PENDING_CONFIRMATION, val failure: WebTextSnapshotFailure? = null, val knowledgeId: KnowledgeItemId? = null)
data class WebTextSnapshotTask(val id: WebTextSnapshotTaskId, val status: WebTextSnapshotStatus, val requestedUrl: String, val asset: WebTextSnapshotAsset? = null, val extractedSha256: String? = null, val failure: WebTextSnapshotFailure? = null, val retryCount: Int = 0, val createdAt: Instant, val updatedAt: Instant, val items: List<WebTextSnapshotItem> = emptyList())
data class WebFetchResponse(val status: Int, val contentType: String?, val contentEncoding: String?, val body: ByteArray, val redirectLocation: String? = null)
sealed interface WebFetchResult { data class Success(val finalUrl: SafeWebUrl, val response: WebFetchResponse) : WebFetchResult; data class Failure(val reason: WebTextSnapshotFailure) : WebFetchResult }
interface PublicWebFetcher { fun fetch(url: SafeWebUrl): WebFetchResult }
interface WebTextSnapshotPrivateAssetStore { fun copy(taskId: WebTextSnapshotTaskId, asset: WebTextSnapshotAsset, html: ByteArray): Boolean; fun read(storageKey: String): ByteArray? }
interface WebTextSnapshotTaskRepository { fun save(task: WebTextSnapshotTask): WebTextSnapshotTask; fun find(id: WebTextSnapshotTaskId): WebTextSnapshotTask?; fun list(): List<WebTextSnapshotTask> }

object WebTextSnapshotUrlPolicy {
    private val sensitive = Regex("(?i)(?:^|[?&])(?:token|key|api[_-]?key|authorization|password|secret|session|code)=")
    fun validate(raw: String): SafeWebUrl? = runCatching {
        val uri = URI(raw.trim())
        if (uri.scheme?.lowercase() != "https" || uri.userInfo != null || uri.fragment != null || uri.port !in setOf(-1, 443) || uri.host.isNullOrBlank()) return null
        val host = IDN.toASCII(uri.host, IDN.USE_STD3_ASCII_RULES).lowercase()
        if (host == "localhost" || host.contains(':') || host.matches(Regex("\\d{1,3}(?:\\.\\d{1,3}){3}")) || !host.contains('.')) return null
        if (!uri.rawQuery.isNullOrBlank() && sensitive.containsMatchIn("?${uri.rawQuery}")) return null
        val path = uri.rawPath?.ifBlank { "/" } ?: "/"
        SafeWebUrl(uri.toASCIIString(), "https://$host$path", host)
    }.getOrNull()
}

object DeterministicWebTextExtractor {
    fun extract(html: ByteArray): Pair<String, String>? {
        return try {
        if (html.size > WEB_TEXT_MAX_HTML_BYTES) return null
        val text = html.toString(Charsets.UTF_8)
        if (text.count { it == '<' } > 12_000) return null
        var safe = text.replace(Regex("(?is)<!--.*?-->"), " ")
        safe = safe.replace(Regex("(?is)<(script|style|noscript|template|form|iframe|object|embed|svg|canvas)[^>]*>.*?</\\1\\s*>"), " ")
        safe = safe.replace(Regex("(?is)<[^>]*(?:hidden|aria-hidden\\s*=\\s*[\"']?true)[^>]*>.*?</[^>]+>"), " ")
        val title = Regex("(?is)<title[^>]*>(.*?)</title>").find(safe)?.groupValues?.get(1)?.plain().orEmpty()
        val blocks = Regex("(?is)<(?:h[1-6]|p|li|article|main|section|div)[^>]*>(.*?)</(?:h[1-6]|p|li|article|main|section|div)>").findAll(safe).map { it.groupValues[1].plain() }.filter { it.isNotBlank() }.toList()
        val body = blocks.joinToString("\n\n").ifBlank { safe.plain() }.trim()
        if (body.isBlank() || body.codePointCount(0, body.length) > WEB_TEXT_MAX_EXTRACTED_CODE_POINTS || MemoryDomain.sensitiveRejection(body) != null) null
        else (title.ifBlank { body.lineSequence().first().take(120) }) to body
        } catch (_: Exception) { null }
    }
    private fun String.plain() = replace(Regex("(?is)<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
}

class ManageWebTextSnapshotUseCase(private val tasks: WebTextSnapshotTaskRepository, private val assets: WebTextSnapshotPrivateAssetStore, private val fetcher: PublicWebFetcher, private val knowledge: ManageKnowledgeUseCase, private val clock: Clock) {
    fun start(rawUrl: String): WebTextSnapshotTask {
        val safe = WebTextSnapshotUrlPolicy.validate(rawUrl) ?: return failed(rawUrl, WebTextSnapshotFailure.INVALID_URL)
        val queued = tasks.save(WebTextSnapshotTask(WebTextSnapshotTaskId.new(), WebTextSnapshotStatus.QUEUED, safe.displayUrl, createdAt = clock.instant(), updatedAt = clock.instant()))
        return fetch(queued, safe)
    }
    fun retry(id: WebTextSnapshotTaskId, rawUrl: String): WebTextSnapshotTask { val existing = tasks.find(id) ?: throw IllegalArgumentException("missing web task"); val safe = WebTextSnapshotUrlPolicy.validate(rawUrl) ?: return tasks.save(existing.copy(status = WebTextSnapshotStatus.FAILED, failure = WebTextSnapshotFailure.INVALID_URL, retryCount = existing.retryCount + 1, updatedAt = clock.instant())); return fetch(existing.copy(status = WebTextSnapshotStatus.QUEUED, requestedUrl = safe.displayUrl, failure = null, retryCount = existing.retryCount + 1, updatedAt = clock.instant()), safe) }
    fun recoverInterrupted(): Int = tasks.list().filter { it.status in setOf(WebTextSnapshotStatus.FETCHING, WebTextSnapshotStatus.EXTRACTING, WebTextSnapshotStatus.QUEUED) }.count { task ->
        tasks.save(task.copy(status = WebTextSnapshotStatus.FAILED, failure = WebTextSnapshotFailure.INTERRUPTED, updatedAt = clock.instant()))
        true
    }
    fun list() = tasks.list()
    fun cancel(id: WebTextSnapshotTaskId) = tasks.find(id)?.let { task ->
        if (task.status in setOf(WebTextSnapshotStatus.COMPLETED, WebTextSnapshotStatus.CANCELLED)) task
        else tasks.save(task.copy(status = WebTextSnapshotStatus.CANCELLED, updatedAt = clock.instant()))
    } ?: throw IllegalArgumentException("missing web task")
    fun skip(taskId: WebTextSnapshotTaskId, itemId: WebTextSnapshotItemId) = update(taskId, itemId) { it.copy(status = WebTextSnapshotItemStatus.SKIPPED) }
    fun confirm(taskId: WebTextSnapshotTaskId, itemId: WebTextSnapshotItemId, title: String, body: String) { val task = tasks.find(taskId) ?: return; val item = task.items.firstOrNull { it.id == itemId } ?: return; if (item.status != WebTextSnapshotItemStatus.PENDING_CONFIRMATION) return; val result = knowledge.execute(KnowledgeIntent(KnowledgeIntentId.new(), KnowledgeIntentAction.CREATE_WEB_TEXT_SNAPSHOT, KnowledgeItemId.new(), title, body, emptySet(), KnowledgeScope.GLOBAL, null, "web:${task.id.value}:${item.candidateSha256}")); update(taskId, itemId) { when (result) { is KnowledgeMutationResult.Applied -> it.copy(status = WebTextSnapshotItemStatus.CONFIRMED, knowledgeId = result.snapshot.item.id); is KnowledgeMutationResult.Replayed -> it.copy(status = WebTextSnapshotItemStatus.CONFIRMED, knowledgeId = result.snapshot.item.id); is KnowledgeMutationResult.Rejected -> it.copy(status = WebTextSnapshotItemStatus.FAILED, failure = if (result.code == KnowledgeRejectionCode.HIGH_SENSITIVITY) WebTextSnapshotFailure.HIGH_SENSITIVITY else WebTextSnapshotFailure.KNOWLEDGE_REJECTED) } } }
    private fun fetch(seed: WebTextSnapshotTask, safe: SafeWebUrl): WebTextSnapshotTask { val fetching = tasks.save(seed.copy(status = WebTextSnapshotStatus.FETCHING, updatedAt = clock.instant())); return when (val result = fetcher.fetch(safe)) { is WebFetchResult.Failure -> tasks.save(fetching.copy(status = WebTextSnapshotStatus.FAILED, failure = result.reason, updatedAt = clock.instant())); is WebFetchResult.Success -> { val response = result.response; if (response.status !in 200..299) return tasks.save(fetching.copy(status = WebTextSnapshotStatus.FAILED, failure = WebTextSnapshotFailure.HTTP_STATUS, updatedAt = clock.instant())); if (response.contentType?.lowercase()?.startsWith("text/html") != true) return tasks.save(fetching.copy(status = WebTextSnapshotStatus.FAILED, failure = WebTextSnapshotFailure.CONTENT_TYPE, updatedAt = clock.instant())); val asset = WebTextSnapshotAsset("web-text-snapshots/v1/${fetching.id.value}/snapshot.html", result.finalUrl.displayUrl, result.finalUrl.host, response.body.sha256(), response.body.size.toLong()); if (!assets.copy(fetching.id, asset, response.body)) return tasks.save(fetching.copy(status = WebTextSnapshotStatus.FAILED, failure = WebTextSnapshotFailure.PRIVATE_COPY_FAILED, updatedAt = clock.instant())); val extracting = tasks.save(fetching.copy(status = WebTextSnapshotStatus.EXTRACTING, asset = asset, updatedAt = clock.instant())); val extracted = DeterministicWebTextExtractor.extract(response.body) ?: return tasks.save(extracting.copy(status = WebTextSnapshotStatus.FAILED, failure = if (MemoryDomain.sensitiveRejection(response.body.toString(Charsets.UTF_8)) != null) WebTextSnapshotFailure.HIGH_SENSITIVITY else WebTextSnapshotFailure.NO_VISIBLE_TEXT, updatedAt = clock.instant())); val item = WebTextSnapshotItem(WebTextSnapshotItemId.new(), extracting.id, 0, extracted.first, extracted.second, (extracted.first + "\n" + extracted.second).sha256()); tasks.save(extracting.copy(status = WebTextSnapshotStatus.AWAITING_CONFIRMATION, extractedSha256 = extracted.second.sha256(), items = listOf(item), updatedAt = clock.instant())) } }
    }
    private fun failed(raw: String, reason: WebTextSnapshotFailure): WebTextSnapshotTask { val now = clock.instant(); return tasks.save(WebTextSnapshotTask(WebTextSnapshotTaskId.new(), WebTextSnapshotStatus.FAILED, raw.take(180), failure = reason, createdAt = now, updatedAt = now)) }
    private fun update(taskId: WebTextSnapshotTaskId, itemId: WebTextSnapshotItemId, transform: (WebTextSnapshotItem) -> WebTextSnapshotItem) { val task = tasks.find(taskId) ?: return; val next = task.copy(items = task.items.map { if (it.id == itemId) transform(it) else it }, updatedAt = clock.instant()); val status = when { next.items.all { it.status in setOf(WebTextSnapshotItemStatus.CONFIRMED, WebTextSnapshotItemStatus.SKIPPED) } -> WebTextSnapshotStatus.COMPLETED; next.items.any { it.status != WebTextSnapshotItemStatus.PENDING_CONFIRMATION } -> WebTextSnapshotStatus.PARTIALLY_COMPLETED; else -> WebTextSnapshotStatus.AWAITING_CONFIRMATION }; tasks.save(next.copy(status = status)) }
}
private fun ByteArray.sha256() = MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }
private fun String.sha256() = toByteArray(Charsets.UTF_8).sha256()
