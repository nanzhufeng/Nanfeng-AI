package com.nanzhufeng.ai.data

import com.nanzhufeng.ai.domain.*
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import javax.net.ssl.HttpsURLConnection

/** P4-O's only network implementation: one manually followed HTTPS document, no browser state. */
class AndroidPublicWebFetcher : PublicWebFetcher {
    override fun fetch(url: SafeWebUrl): WebFetchResult {
        var current = url
        repeat(WEB_TEXT_MAX_REDIRECTS + 1) { index ->
            if (!allPublic(current.host)) return WebFetchResult.Failure(WebTextSnapshotFailure.DNS_NOT_PUBLIC)
            val connection = try { (URL(current.fetchUrl).openConnection() as HttpsURLConnection).apply {
                instanceFollowRedirects = false; connectTimeout = 4_000; readTimeout = 8_000
                requestMethod = "GET"; setRequestProperty("Accept", "text/html"); setRequestProperty("Accept-Encoding", "gzip, identity")
                setRequestProperty("User-Agent", "NanfengAI-WebTextSnapshot/1")
                setRequestProperty("Cookie", ""); setRequestProperty("Authorization", ""); setRequestProperty("Referer", "")
            } } catch (_: Exception) { return WebFetchResult.Failure(WebTextSnapshotFailure.NETWORK) }
            try {
                connection.connect()
                if (connection.headerFields.entries.sumOf { (key, values) -> (key?.length ?: 0) + values.sumOf(String::length) } > 16 * 1024) return WebFetchResult.Failure(WebTextSnapshotFailure.RESPONSE_TOO_LARGE)
                val code = connection.responseCode
                if (code in 300..399) {
                    if (index == WEB_TEXT_MAX_REDIRECTS) return WebFetchResult.Failure(WebTextSnapshotFailure.TOO_MANY_REDIRECTS)
                    val location = connection.getHeaderField("Location") ?: return WebFetchResult.Failure(WebTextSnapshotFailure.REDIRECT_REJECTED)
                    current = WebTextSnapshotUrlPolicy.validate(URI(current.fetchUrl).resolve(location).toString()) ?: return WebFetchResult.Failure(WebTextSnapshotFailure.REDIRECT_REJECTED)
                    return@repeat
                }
                val length = connection.contentLengthLong
                if (length > 512 * 1024) return WebFetchResult.Failure(WebTextSnapshotFailure.RESPONSE_TOO_LARGE)
                val encoding = connection.contentEncoding?.lowercase()?.substringBefore(';') ?: "identity"
                if (encoding !in setOf("identity", "gzip")) return WebFetchResult.Failure(WebTextSnapshotFailure.CONTENT_ENCODING)
                val raw = connection.inputStream.use { input ->
                    val compressed = LimitedInputStream(input, 512 * 1024)
                    if (encoding == "gzip") GZIPInputStream(compressed).use(::readBounded) else readBounded(compressed)
                }
                if (raw.size > WEB_TEXT_MAX_HTML_BYTES) return WebFetchResult.Failure(WebTextSnapshotFailure.RESPONSE_TOO_LARGE)
                return WebFetchResult.Success(current, WebFetchResponse(code, connection.contentType, encoding, raw))
            } catch (_: java.net.SocketTimeoutException) { return WebFetchResult.Failure(WebTextSnapshotFailure.TIMEOUT) }
            catch (_: javax.net.ssl.SSLException) { return WebFetchResult.Failure(WebTextSnapshotFailure.CERTIFICATE) }
            catch (_: Exception) { return WebFetchResult.Failure(WebTextSnapshotFailure.NETWORK) }
            finally { connection.disconnect() }
        }
        return WebFetchResult.Failure(WebTextSnapshotFailure.TOO_MANY_REDIRECTS)
    }
    private fun readBounded(input: java.io.InputStream): ByteArray { val out = ByteArrayOutputStream(); val buffer = ByteArray(8192); while (out.size() <= WEB_TEXT_MAX_HTML_BYTES) { val n = input.read(buffer); if (n < 0) break; out.write(buffer, 0, n) }; return out.toByteArray() }
    private fun allPublic(host: String): Boolean = runCatching { InetAddress.getAllByName(host).isNotEmpty() && InetAddress.getAllByName(host).all(::isPublic) }.getOrDefault(false)
    private fun isPublic(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress) return false
        val b = address.address
        if (b.size == 4) { val a = b.map { it.toInt() and 0xff }; return !(a[0] == 0 || a[0] >= 224 || (a[0] == 100 && a[1] in 64..127) || (a[0] == 192 && a[1] == 0 && a[2] == 0) || (a[0] == 192 && a[1] == 0 && a[2] == 2) || (a[0] == 198 && a[1] in 18..19) || (a[0] == 198 && a[1] == 51 && a[2] == 100) || (a[0] == 203 && a[1] == 0 && a[2] == 113)) }
        return !(b[0].toInt() and 0xff == 0xfc || b[0].toInt() and 0xff == 0xfd || b[0].toInt() and 0xff == 0xfe && (b[1].toInt() and 0xc0) == 0x80)
    }
}

private class LimitedInputStream(input: java.io.InputStream, private val limit: Int) : FilterInputStream(input) {
    private var read = 0
    override fun read(): Int { if (read >= limit) throw java.io.IOException("response limit"); val value = super.read(); if (value >= 0) read++; return value }
    override fun read(buffer: ByteArray, off: Int, len: Int): Int { if (read >= limit) throw java.io.IOException("response limit"); val count = super.read(buffer, off, minOf(len, limit - read)); if (count > 0) read += count; return count }
}

class AndroidWebTextSnapshotPrivateAssetStore(private val context: android.content.Context) : WebTextSnapshotPrivateAssetStore {
    private val root = java.io.File(context.filesDir, "web-text-snapshots/v1")
    override fun copy(taskId: WebTextSnapshotTaskId, asset: WebTextSnapshotAsset, html: ByteArray): Boolean = runCatching {
        if (html.size > WEB_TEXT_MAX_HTML_BYTES) return false
        val target = java.io.File(context.filesDir, asset.storageKey); target.parentFile?.mkdirs() ?: return false
        val part = java.io.File(target.parentFile, ".${target.name}.part")
        java.io.FileOutputStream(part).use { it.write(html); it.fd.sync() }
        part.renameTo(target)
    }.getOrDefault(false)
    override fun read(storageKey: String): ByteArray? = if (storageKey.matches(Regex("web-text-snapshots/v1/[A-Za-z0-9-]+/snapshot\\.html"))) java.io.File(context.filesDir, storageKey).takeIf(java.io.File::isFile)?.readBytes() else null
}
