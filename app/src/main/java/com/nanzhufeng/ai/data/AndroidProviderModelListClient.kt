package com.nanzhufeng.ai.data

import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.ProviderModelListClient
import com.nanzhufeng.ai.domain.ProviderModelListResult
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.net.ssl.HttpsURLConnection
import org.json.JSONArray
import org.json.JSONObject

/** Provider-specific catalog paths stay at the data boundary, outside the chat executor. */
class AndroidProviderModelListClient : ProviderModelListClient {
    override fun fetch(providerId: ProviderId, endpoint: String, credential: CharArray): ProviderModelListResult = runCatching {
        val url = when (providerId) {
            ProviderId.DEEPSEEK -> endpoint.trimEnd('/') + "/models"
            ProviderId.QWEN -> URI(endpoint).let { "${it.scheme}://${it.host}/api/v1/models" }
            else -> return ProviderModelListResult.Unavailable
        }
        val connection = (URL(url).openConnection() as HttpsURLConnection).apply {
            requestMethod = "GET"; instanceFollowRedirects = false; doInput = true; doOutput = false; useCaches = false
            connectTimeout = CONNECT_TIMEOUT_MS; readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer ${credential.concatToString()}")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return ProviderModelListResult.Unavailable
            val bytes = connection.inputStream.use { it.readAtMost(MAX_RESPONSE_BYTES) } ?: return ProviderModelListResult.Unavailable
            val ids = parseIds(JSONObject(bytes.toString(Charsets.UTF_8)))
            if (ids.isEmpty()) ProviderModelListResult.Unavailable else ProviderModelListResult.Available(ids)
        } finally { connection.disconnect() }
    }.getOrDefault(ProviderModelListResult.Unavailable)

    private fun parseIds(root: JSONObject): Set<String> = buildSet {
        root.optJSONArray("data").addIdsTo(this)
        root.optJSONObject("output")?.optJSONArray("models").addIdsTo(this)
        root.optJSONArray("models").addIdsTo(this)
    }

    private fun JSONArray?.addIdsTo(target: MutableSet<String>) {
        this ?: return
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            listOf("id", "model_id", "model").mapNotNull { key -> item.optString(key).trim().takeIf(String::isNotBlank) }.forEach(target::add)
        }
    }

    private fun InputStream.readAtMost(limit: Int): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val count = read(buffer)
            if (count < 0) return output.toByteArray()
            if (output.size() + count > limit) return null
            output.write(buffer, 0, count)
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 20_000
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
    }
}
