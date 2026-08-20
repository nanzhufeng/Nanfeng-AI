package com.nanzhufeng.ai.domain

import org.json.JSONObject
import java.security.MessageDigest

/**
 * K6 canonical, local-only personalization projection.  This is deliberately not an account:
 * it has no login identifier, contact data, credential, session, device, billing or security
 * field.  Provider adapters must use [ThirdPartyProfilePersonalizationSchema] rather than
 * guessing from arbitrary export JSON.
 */
data class ThirdPartyProfilePersonalization(
    val displayName: String? = null,
    val language: String? = null,
    val timezone: String? = null,
    val publicBio: String? = null,
    val customInstructions: String? = null,
    val theme: String? = null,
    val notificationsEnabled: Boolean? = null,
) {
    val mappedFieldCount: Int get() = listOf(displayName, language, timezone, publicBio, customInstructions, theme).count { it != null } + if (notificationsEnabled != null) 1 else 0
    fun canonicalHash(): String = MessageDigest.getInstance("SHA-256").digest(canonicalJson().toByteArray()).joinToString("") { "%02x".format(it) }
    fun canonicalJson(): String = JSONObject().apply {
        displayName?.let { put("displayName", it) }; language?.let { put("language", it) }; timezone?.let { put("timezone", it) }
        publicBio?.let { put("publicBio", it) }; customInstructions?.let { put("customInstructions", it) }; theme?.let { put("theme", it) }
        notificationsEnabled?.let { put("notificationsEnabled", it) }
    }.let(::canonicalPersonalizationJson)
}

sealed interface ThirdPartyProfilePersonalizationParseResult {
    data class Mapped(val value: ThirdPartyProfilePersonalization) : ThirdPartyProfilePersonalizationParseResult
    data object NoSafeFields : ThirdPartyProfilePersonalizationParseResult
    data object Rejected : ThirdPartyProfilePersonalizationParseResult
}

/** Versioned adapter boundary.  Unknown, additional, or sensitive fields reject the profile item only. */
object ThirdPartyProfilePersonalizationSchema {
    const val FORMAT = "nfai.third-party-profile-personalization"
    const val VERSION = 1

    fun parse(bytes: ByteArray): ThirdPartyProfilePersonalizationParseResult = runCatching {
        require(bytes.size <= 16 * 1024) { "profile payload too large" }
        val root = JSONObject(String(bytes, Charsets.UTF_8))
        require(root.keysSet() == setOf("format", "version", "personalization"))
        require(root.getString("format") == FORMAT && root.getInt("version") == VERSION)
        val value = root.getJSONObject("personalization")
        require(value.keysSet().all { it in ALLOWED })
        val mapped = ThirdPartyProfilePersonalization(
            displayName = value.optionalText("displayName", 180),
            language = value.optionalText("language", 32),
            timezone = value.optionalText("timezone", 64),
            publicBio = value.optionalText("publicBio", 1_000),
            customInstructions = value.optionalText("customInstructions", 4_000),
            theme = value.optionalText("theme", 32),
            notificationsEnabled = if (value.has("notificationsEnabled")) value.getBoolean("notificationsEnabled") else null,
        )
        if (mapped.mappedFieldCount == 0) ThirdPartyProfilePersonalizationParseResult.NoSafeFields else ThirdPartyProfilePersonalizationParseResult.Mapped(mapped)
    }.getOrElse { ThirdPartyProfilePersonalizationParseResult.Rejected }

    private fun JSONObject.optionalText(key: String, max: Int): String? = if (!has(key)) null else getString(key).also {
        require(it.isNotBlank() && it.codePointCount(0, it.length) <= max)
    }
    private val ALLOWED = setOf("displayName", "language", "timezone", "publicBio", "customInstructions", "theme", "notificationsEnabled")
}

private fun JSONObject.keysSet(): Set<String> = buildSet { val iterator = keys(); while (iterator.hasNext()) add(iterator.next()) }
private fun canonicalPersonalizationJson(value: JSONObject): String = value.keysSet().sorted().joinToString(prefix = "{", postfix = "}") { key -> "\"$key\":${when (val raw = value.get(key)) { is String -> JSONObject.quote(raw); else -> raw.toString() }}" }
