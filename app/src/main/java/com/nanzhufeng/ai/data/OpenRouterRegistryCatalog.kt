package com.nanzhufeng.ai.data

import android.content.Context
import com.nanzhufeng.ai.domain.ModelCapabilities
import com.nanzhufeng.ai.domain.ModelDescriptor
import com.nanzhufeng.ai.domain.ModelPresetId
import com.nanzhufeng.ai.domain.ModelPresetMapping
import com.nanzhufeng.ai.domain.ModelPricing
import com.nanzhufeng.ai.domain.ModelRegistrySnapshot
import com.nanzhufeng.ai.domain.ModelRegistrySnapshotId
import com.nanzhufeng.ai.domain.ModelRegistrySnapshotStore
import com.nanzhufeng.ai.domain.ModelRegistrySnapshotHasher
import com.nanzhufeng.ai.domain.OpenRouterCatalogFailure
import com.nanzhufeng.ai.domain.OpenRouterCatalogFetchResult
import com.nanzhufeng.ai.domain.OpenRouterCatalogModel
import com.nanzhufeng.ai.domain.OpenRouterCatalogResponse
import com.nanzhufeng.ai.domain.OpenRouterRegistryCatalogClient
import com.nanzhufeng.ai.domain.OPENROUTER_MODELS_URL
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.RegistrySnapshotSource
import com.nanzhufeng.ai.domain.RegistryVerificationStatus
import com.nanzhufeng.ai.domain.StoredModelRegistrySnapshots
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import javax.net.ssl.HttpsURLConnection
import org.json.JSONArray
import org.json.JSONObject

/** Fixed no-auth/no-body GET policy; P2-J has no path for inference traffic or credentials. */
internal object OpenRouterRegistryRequestPolicy {
    const val URL = OPENROUTER_MODELS_URL
    const val METHOD = "GET"
    const val CONNECT_TIMEOUT_MS = 8_000
    const val READ_TIMEOUT_MS = 20_000
    const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024
}

class AndroidOpenRouterRegistryCatalogClient : OpenRouterRegistryCatalogClient {
    override fun fetchCatalog(): OpenRouterCatalogFetchResult = runCatching {
        val connection = (URL(OpenRouterRegistryRequestPolicy.URL).openConnection() as HttpsURLConnection).apply {
            requestMethod = OpenRouterRegistryRequestPolicy.METHOD
            instanceFollowRedirects = false
            doInput = true
            doOutput = false
            useCaches = false
            connectTimeout = OpenRouterRegistryRequestPolicy.CONNECT_TIMEOUT_MS
            readTimeout = OpenRouterRegistryRequestPolicy.READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            when {
                code in 300..399 -> return OpenRouterCatalogFetchResult.Failed(OpenRouterCatalogFailure.REDIRECT)
                code != HttpURLConnection.HTTP_OK -> return OpenRouterCatalogFetchResult.Failed(OpenRouterCatalogFailure.HTTP_STATUS)
            }
            val body = connection.inputStream.use { it.readAtMost(OpenRouterRegistryRequestPolicy.MAX_RESPONSE_BYTES) }
                ?: return OpenRouterCatalogFetchResult.Failed(OpenRouterCatalogFailure.RESPONSE_TOO_LARGE)
            OpenRouterCatalogFetchResult.Fetched(
                OpenRouterCatalogResponse(
                    models = OpenRouterCatalogJsonParser.parse(body),
                    sourceEtag = connection.getHeaderField("ETag"),
                ),
            )
        } finally {
            connection.disconnect()
        }
    }.getOrElse { error ->
        OpenRouterCatalogFetchResult.Failed(
            when (error) {
                is java.net.SocketTimeoutException -> OpenRouterCatalogFailure.TIMEOUT
                is org.json.JSONException, is IllegalArgumentException -> OpenRouterCatalogFailure.MALFORMED_RESPONSE
                else -> OpenRouterCatalogFailure.NETWORK
            },
        )
    }
}

internal object OpenRouterCatalogJsonParser {
    fun parse(bytes: ByteArray): List<OpenRouterCatalogModel> {
        val data = JSONObject(bytes.toString(Charsets.UTF_8)).optJSONArray("data")
            ?: throw IllegalArgumentException("OpenRouter model registry lacks data.")
        return buildList {
            for (index in 0 until data.length()) {
                val item = data.optJSONObject(index) ?: continue
                val architecture = item.optJSONObject("architecture")
                val pricing = item.optJSONObject("pricing")
                add(OpenRouterCatalogModel(
                    id = item.optString("id").trim(),
                    displayName = item.optString("name").trim(),
                    contextWindowTokens = item.optLongOrNull("context_length"),
                    inputModalities = architecture.stringSet("input_modalities"),
                    outputModalities = architecture.stringSet("output_modalities"),
                    supportedParameters = item.stringSet("supported_parameters"),
                    promptUsdPerToken = pricing.decimalStringOrNull("prompt"),
                    completionUsdPerToken = pricing.decimalStringOrNull("completion"),
                    cacheReadUsdPerToken = pricing.decimalStringOrNull("cache_read"),
                ))
            }
        }
    }

    private fun JSONObject?.stringSet(name: String): Set<String> = this?.optJSONArray(name)
        ?.let { array -> (0 until array.length()).mapNotNull { array.optString(it).trim().lowercase().takeIf(String::isNotBlank) }.toSet() }
        .orEmpty()

    private fun JSONObject?.decimalStringOrNull(name: String): String? = this?.opt(name)
        ?.toString()?.trim()?.takeIf(String::isNotBlank)

    private fun JSONObject.optLongOrNull(name: String): Long? = takeIf { has(name) && !isNull(name) }
        ?.optLong(name, -1L)?.takeIf { it > 0L }
}

/** Application-private, sanitized snapshot file. It deliberately has no raw OpenRouter body. */
class AndroidModelRegistrySnapshotStore internal constructor(private val root: File) : ModelRegistrySnapshotStore {
    constructor(context: Context) : this(File(context.filesDir, "model-registry/openrouter/v1"))

    override fun load(): StoredModelRegistrySnapshots = runCatching {
        val file = File(root, SNAPSHOT_FILE)
        if (!file.isFile) return StoredModelRegistrySnapshots(null, null)
        val document = JSONObject(FileInputStream(file).use { it.readBytes().toString(Charsets.UTF_8) })
        val current = document.optJSONObject("current")?.let(::snapshotFromJson)?.takeIf(::hasValidHash)
        val previous = document.optJSONObject("previousStable")?.let(::snapshotFromJson)?.takeIf(::hasValidHash)
        when {
            current != null -> StoredModelRegistrySnapshots(current, previous)
            previous != null -> StoredModelRegistrySnapshots(previous, null)
            else -> StoredModelRegistrySnapshots(null, null)
        }
    }.getOrElse { StoredModelRegistrySnapshots(null, null) }

    override fun persist(snapshots: StoredModelRegistrySnapshots): Boolean = runCatching {
        if (snapshots.current == null) return false
        root.mkdirs() || root.isDirectory || return false
        val destination = File(root, SNAPSHOT_FILE)
        val temporary = File(root, "$SNAPSHOT_FILE.part")
        FileOutputStream(temporary).use { output ->
            output.write(JSONObject().apply {
                put("current", snapshots.current.toJson())
                snapshots.previousStable?.let { put("previousStable", it.toJson()) }
            }.toString().toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        if (!temporary.renameTo(destination)) {
            temporary.delete()
            return false
        }
        val restored = load()
        val valid = restored.current?.let { restoredSnapshot ->
            restoredSnapshot.catalogSha256 == snapshots.current.catalogSha256 &&
                restoredSnapshot.catalogVersion == snapshots.current.catalogVersion &&
                restoredSnapshot.catalogSha256 == ModelRegistrySnapshotHasher.sha256(restoredSnapshot.models, restoredSnapshot.presetMappings)
        } == true
        if (!valid) destination.delete()
        valid
    }.getOrDefault(false)

    private fun ModelRegistrySnapshot.toJson(): JSONObject = JSONObject().apply {
        put("id", id.value); put("schemaVersion", schemaVersion); put("providerId", providerId.name)
        put("catalogVersion", catalogVersion); put("source", source.name); put("capturedAt", capturedAt.toString())
        put("verificationStatus", verificationStatus.name); put("lastVerifiedAt", lastVerifiedAt?.toString())
        put("sourceUrl", sourceUrl); put("sourceEtag", sourceEtag); put("catalogSha256", catalogSha256)
        put("mappingUsesFallback", mappingUsesFallback)
        put("models", JSONArray(models.map { model -> JSONObject().apply {
            put("id", model.id); put("displayName", model.displayName); put("contextWindowTokens", model.contextWindowTokens)
            put("text", model.capabilities.supportsText); put("vision", model.capabilities.supportsVision)
            put("streaming", model.capabilities.supportsStreaming); put("structured", model.capabilities.supportsStructuredOutput)
            put("inputModalities", JSONArray(model.inputModalities.sorted()))
            put("outputModalities", JSONArray(model.outputModalities.sorted()))
            put("supportedParameters", JSONArray(model.supportedParameters.sorted()))
            put("priceVersion", model.pricing.priceVersion); put("currencyCode", model.pricing.currencyCode)
            put("inputMicros", model.pricing.inputMicrosPerToken); put("outputMicros", model.pricing.outputMicrosPerToken)
            put("cacheReadMicros", model.pricing.cachedInputMicrosPerToken)
        } }))
        put("presetMappings", JSONArray(presetMappings.map { JSONObject().put("preset", it.presetId.name).put("modelId", it.modelId) }))
    }

    private fun snapshotFromJson(json: JSONObject): ModelRegistrySnapshot = ModelRegistrySnapshot(
        id = ModelRegistrySnapshotId(json.getString("id")), schemaVersion = json.getInt("schemaVersion"),
        providerId = ProviderId.valueOf(json.getString("providerId")), catalogVersion = json.getString("catalogVersion"),
        source = RegistrySnapshotSource.valueOf(json.getString("source")), capturedAt = Instant.parse(json.getString("capturedAt")),
        verificationStatus = RegistryVerificationStatus.valueOf(json.getString("verificationStatus")),
        lastVerifiedAt = json.stringOrNull("lastVerifiedAt")?.let(Instant::parse),
        models = json.getJSONArray("models").let { array -> (0 until array.length()).map { index -> array.getJSONObject(index).let { model ->
            ModelDescriptor(
                id = model.getString("id"), displayName = model.getString("displayName"),
                capabilities = ModelCapabilities(model.getBoolean("text"), model.getBoolean("vision"), model.getBoolean("streaming"), model.getBoolean("structured")),
                contextWindowTokens = model.longOrNull("contextWindowTokens"),
                pricing = ModelPricing(model.stringOrNull("priceVersion"), model.stringOrNull("currencyCode"), model.longOrNull("inputMicros"), model.longOrNull("outputMicros"), model.longOrNull("cacheReadMicros")),
                inputModalities = model.stringSet("inputModalities"), outputModalities = model.stringSet("outputModalities"),
                supportedParameters = model.stringSet("supportedParameters"),
            )
        } } },
        presetMappings = json.getJSONArray("presetMappings").let { array -> (0 until array.length()).map { index -> array.getJSONObject(index).let { mapping ->
            ModelPresetMapping(ModelPresetId.valueOf(mapping.getString("preset")), mapping.getString("modelId"))
        } } },
        sourceUrl = json.stringOrNull("sourceUrl"), sourceEtag = json.stringOrNull("sourceEtag"),
        catalogSha256 = json.stringOrNull("catalogSha256"), mappingUsesFallback = json.optBoolean("mappingUsesFallback"),
    )

    private fun JSONObject.stringOrNull(name: String): String? = takeIf { has(name) && !isNull(name) }?.getString(name)
    private fun JSONObject.longOrNull(name: String): Long? = takeIf { has(name) && !isNull(name) }?.getLong(name)
    private fun JSONObject.stringSet(name: String): Set<String> = optJSONArray(name)?.let { array ->
        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }.toSet()
    }.orEmpty()
    private fun hasValidHash(snapshot: ModelRegistrySnapshot): Boolean =
        snapshot.catalogSha256 != null && snapshot.catalogSha256 == ModelRegistrySnapshotHasher.sha256(snapshot.models, snapshot.presetMappings)

    private companion object { const val SNAPSHOT_FILE = "snapshots.json" }
}

private fun java.io.InputStream.readAtMost(limit: Int): ByteArray? {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = read(buffer)
        if (read < 0) return output.toByteArray()
        if (output.size() + read > limit) return null
        output.write(buffer, 0, read)
    }
}
