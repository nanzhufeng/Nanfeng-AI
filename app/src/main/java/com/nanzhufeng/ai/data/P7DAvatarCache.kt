package com.nanzhufeng.ai.data

import android.content.Context
import java.io.File
import java.net.URI
import java.security.MessageDigest

/** Private, account+URL-isolated cache only; fetching remains gated by a future verified session. */
class P7DAvatarCache(context: Context) {
    private val root = File(context.filesDir, "p7d-avatar-cache/v1").also { it.mkdirs() }
    fun read(accountRef: String, url: String): ByteArray? = target(accountRef, url).takeIf(File::isFile)?.readBytes()
    fun write(accountRef: String, url: String, bytes: ByteArray) {
        require(bytes.size in 1..MAX_BYTES); val target = target(accountRef, url); val part = File(target.parentFile, ".${target.name}.part")
        part.outputStream().use { it.write(bytes); it.fd.sync() }; require(part.renameTo(target)) { "AVATAR_CACHE_WRITE_REJECTED" }
    }
    fun deleteAccount(accountRef: String) { File(root, digest(accountRef)).deleteRecursively() }
    private fun target(accountRef: String, url: String): File { validate(url); val dir = File(root, digest(accountRef)).also { it.mkdirs() }; return File(dir, digest(url) + ".img") }
    private fun validate(value: String) { val uri = URI(value); val host = uri.host?.lowercase() ?: error("AVATAR_URL_REJECTED"); require(uri.scheme == "https" && uri.userInfo == null && uri.port == -1 && host.endsWith(".googleusercontent.com")) { "AVATAR_URL_REJECTED" } }
    private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    private companion object { const val MAX_BYTES = 2 * 1024 * 1024 }
}
