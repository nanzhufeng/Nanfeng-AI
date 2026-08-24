package com.nanzhufeng.ai.data

import android.content.Context
import com.nanzhufeng.ai.domain.ModelCapabilities
import com.nanzhufeng.ai.domain.AttachmentInputTokenEstimate
import com.nanzhufeng.ai.domain.ModelHealth
import com.nanzhufeng.ai.domain.ModelPresetId
import com.nanzhufeng.ai.domain.ModelProfileDirectory
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.ResolvedModel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

/**
 * The app ships a verified offline seed, but always prefers the last successfully persisted
 * provider directory.  No model ID or capability is duplicated in a chat, UI or Adapter class.
 */
class AndroidModelProfileDirectory(context: Context) : ModelProfileDirectory {
    private val cacheFile = File(context.filesDir, "model-profile-directory-v1.json")
    private val bundledProfiles = load(context.assets.open("model_profiles.json").bufferedReader().use { it.readText() }).orEmpty()
    @Volatile private var profiles = mergeCachedProfiles(
        bundledProfiles,
        load(cacheFile.takeIf(File::isFile)?.readText()),
    )

    override fun profile(presetId: ModelPresetId): ResolvedModel? = profiles[presetId]

    override fun profiles(): Map<ModelPresetId, ResolvedModel> = profiles.toMap()

    override fun replaceIfNewer(profiles: Map<ModelPresetId, ResolvedModel>): Boolean {
        if (profiles.isEmpty()) return false
        val currentNewest = this.profiles.values.mapNotNull { it.metadataUpdatedAt }.maxOrNull()
        val replacementNewest = profiles.values.mapNotNull { it.metadataUpdatedAt }.maxOrNull() ?: return false
        if (currentNewest != null && !replacementNewest.isAfter(currentNewest)) return false
        return runCatching {
            cacheFile.parentFile?.mkdirs()
            val merged = mergeCachedProfiles(bundledProfiles, profiles)
            cacheFile.writeText(toJson(merged).toString())
            this.profiles = merged
            true
        }.getOrDefault(false)
    }

    /**
     * A provider list only validates ID availability; it does not prove multimodal capability
     * or token limits.  Therefore cached dynamic fields are retained, while every safety and
     * planning field comes from the current verified bundled profile.
     */
    private fun mergeCachedProfiles(bundled: Map<ModelPresetId, ResolvedModel>, cached: Map<ModelPresetId, ResolvedModel>?): Map<ModelPresetId, ResolvedModel> {
        if (cached == null) return bundled
        return bundled.mapValues { (preset, seed) ->
            cached[preset]?.copy(
                providerId = seed.providerId,
                displayName = seed.displayName,
                capabilities = seed.capabilities,
                contextWindowTokens = seed.contextWindowTokens,
                maxOutputTokens = seed.maxOutputTokens,
                tokenizerId = seed.tokenizerId,
                alternateModelIds = seed.alternateModelIds,
                attachmentInputTokenEstimate = seed.attachmentInputTokenEstimate,
            ) ?: seed
        }
    }

    private fun load(raw: String?): Map<ModelPresetId, ResolvedModel>? = runCatching {
        val items = JSONObject(raw ?: return null).getJSONArray("profiles")
        buildMap {
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                val preset = ModelPresetId.valueOf(item.getString("presetId"))
                val caps = item.getJSONObject("capabilities")
                put(preset, ResolvedModel(
                    providerId = ProviderId.valueOf(item.getString("providerId")),
                    modelId = item.getString("modelId"), displayName = item.getString("displayName"),
                    capabilities = ModelCapabilities(
                        supportsText = caps.getBoolean("text"), supportsVision = caps.getBoolean("image"),
                        supportsStreaming = caps.getBoolean("streaming"), supportsStructuredOutput = caps.optBoolean("structuredOutput"),
                        supportsPdf = caps.optBoolean("pdf"), supportsVideo = caps.optBoolean("video"),
                        supportsAudio = caps.optBoolean("audio"), supportsTools = caps.optBoolean("tools"), supportsReasoning = caps.optBoolean("reasoning"),
                    ),
                    contextWindowTokens = item.optLong("contextWindowTokens").takeIf { it > 0L },
                    health = ModelHealth.valueOf(item.optString("health", ModelHealth.UNKNOWN.name)),
                    metadataUpdatedAt = item.optString("metadataUpdatedAt").takeIf(String::isNotBlank)?.let(Instant::parse),
                    healthCheckedAt = item.optString("healthCheckedAt").takeIf(String::isNotBlank)?.let(Instant::parse),
                    maxOutputTokens = item.optLong("maxOutputTokens").takeIf { it > 0L },
                    tokenizerId = item.optString("tokenizerId", "heuristic-v1"),
                    alternateModelIds = item.optJSONArray("alternateModelIds")?.let { aliases ->
                        buildSet { for (aliasIndex in 0 until aliases.length()) aliases.optString(aliasIndex).trim().takeIf(String::isNotBlank)?.let(::add) }
                    }.orEmpty(),
                    attachmentInputTokenEstimate = item.optJSONObject("attachmentInputTokenEstimate")?.let { estimate ->
                        AttachmentInputTokenEstimate(
                            estimate.getInt("imageMinimumTokens"), estimate.getInt("imageTokensPerKiB"),
                            estimate.getInt("pdfMinimumTokens"), estimate.getInt("pdfTokensPerKiB"),
                            estimate.getInt("videoMinimumTokens"), estimate.getInt("videoTokensPerKiB"),
                        )
                    } ?: AttachmentInputTokenEstimate.Conservative,
                ))
            }
        }
    }.getOrNull()

    private fun toJson(profiles: Map<ModelPresetId, ResolvedModel>) = JSONObject().apply {
        put("profiles", JSONArray().apply {
            profiles.toSortedMap(compareBy { it.name }).forEach { (preset, model) -> put(JSONObject().apply {
                put("presetId", preset.name); put("providerId", model.providerId.name); put("modelId", model.modelId); put("displayName", model.displayName)
                put("contextWindowTokens", model.contextWindowTokens); put("maxOutputTokens", model.maxOutputTokens); put("tokenizerId", model.tokenizerId); put("alternateModelIds", JSONArray(model.alternateModelIds.sorted())); put("attachmentInputTokenEstimate", JSONObject().apply { put("imageMinimumTokens", model.attachmentInputTokenEstimate.imageMinimumTokens); put("imageTokensPerKiB", model.attachmentInputTokenEstimate.imageTokensPerKiB); put("pdfMinimumTokens", model.attachmentInputTokenEstimate.pdfMinimumTokens); put("pdfTokensPerKiB", model.attachmentInputTokenEstimate.pdfTokensPerKiB); put("videoMinimumTokens", model.attachmentInputTokenEstimate.videoMinimumTokens); put("videoTokensPerKiB", model.attachmentInputTokenEstimate.videoTokensPerKiB) }); put("health", model.health.name); put("metadataUpdatedAt", model.metadataUpdatedAt?.toString()); put("healthCheckedAt", model.healthCheckedAt?.toString())
                put("capabilities", JSONObject().apply {
                    put("text", model.capabilities.supportsText); put("image", model.capabilities.supportsVision); put("pdf", model.capabilities.supportsPdf); put("video", model.capabilities.supportsVideo); put("audio", model.capabilities.supportsAudio)
                    put("streaming", model.capabilities.supportsStreaming); put("tools", model.capabilities.supportsTools); put("reasoning", model.capabilities.supportsReasoning); put("structuredOutput", model.capabilities.supportsStructuredOutput)
                })
            }) }
        })
    }
}
