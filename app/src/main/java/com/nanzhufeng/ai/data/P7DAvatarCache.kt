package com.nanzhufeng.ai.data

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.security.MessageDigest

/** Private, account+URL-isolated cache only; fetching remains gated by a future verified session. */
class P7DAvatarCache(context: Context) {
    private val root = File(context.filesDir, "p7d-avatar-cache/v1").also { it.mkdirs() }
    fun read(accountRef: String, url: String): ByteArray? {
        val target = target(accountRef, url).takeIf(File::isFile) ?: return null
        return runCatching {
            if (target.length() !in 1..MAX_BYTES) return@runCatching null
            target.inputStream().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_BYTES) return@use null
                    output.write(buffer, 0, read)
                }
                output.toByteArray().takeIf { it.isNotEmpty() }
            }
        }.getOrElse {
            target.delete()
            null
        }
    }
    fun write(accountRef: String, url: String, bytes: ByteArray) {
        require(bytes.size in 1..MAX_BYTES); val target = target(accountRef, url); val part = File(target.parentFile, ".${target.name}.part")
        part.outputStream().use { it.write(bytes); it.fd.sync() }; require(part.renameTo(target)) { "AVATAR_CACHE_WRITE_REJECTED" }
    }
    fun deleteAccount(accountRef: String) { File(root, digest(accountRef)).deleteRecursively() }
    private fun target(accountRef: String, url: String): File { validate(url); val dir = File(root, digest(accountRef)).also { it.mkdirs() }; return File(dir, digest(url) + ".img") }
    companion object {
        const val MAX_BYTES = 2 * 1024 * 1024

        fun isAllowedGoogleAvatarUrl(value: String): Boolean = runCatching {
            val uri = URI(value)
            val host = uri.host?.lowercase() ?: return@runCatching false
            uri.scheme == "https" && uri.userInfo == null && uri.port == -1 &&
                (host == "googleusercontent.com" || host.endsWith(".googleusercontent.com"))
        }.getOrDefault(false)
    }

    private fun validate(value: String) { require(isAllowedGoogleAvatarUrl(value)) { "AVATAR_URL_REJECTED" } }
    private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
