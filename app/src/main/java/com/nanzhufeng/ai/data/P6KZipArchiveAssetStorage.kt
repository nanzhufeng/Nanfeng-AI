package com.nanzhufeng.ai.data

import java.nio.charset.StandardCharsets
import java.util.Base64

/** Opaque catalog key; it never exposes a filesystem path or raw ZIP entry name to Conversation. */
object P6KZipArchiveAssetStorage {
    private const val PREFIX = "p6k-zip-assets/v1/"
    private val TASK_ID = Regex("[A-Za-z0-9-]{1,80}")
    private val ENTRY_NAME = Regex("[A-Za-z0-9._-]{1,240}")

    data class Location(val taskId: String, val entryName: String)

    fun key(taskId: String, entryName: String): String {
        require(taskId.matches(TASK_ID) && entryName.matches(ENTRY_NAME))
        val encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(entryName.toByteArray(StandardCharsets.UTF_8))
        return "$PREFIX$taskId/$encoded"
    }

    fun parse(storageKey: String): Location? {
        if (!storageKey.startsWith(PREFIX)) return null
        val remainder = storageKey.removePrefix(PREFIX)
        val taskId = remainder.substringBefore('/', "")
        val encoded = remainder.substringAfter('/', "")
        if (!taskId.matches(TASK_ID) || encoded.isBlank() || '/' in encoded) return null
        val entryName = runCatching {
            String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8)
        }.getOrNull() ?: return null
        return entryName.takeIf(ENTRY_NAME::matches)?.let { Location(taskId, it) }
    }
}
