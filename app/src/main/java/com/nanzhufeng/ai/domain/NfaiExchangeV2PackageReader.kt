package com.nanzhufeng.ai.domain

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/** The largest single v2 package that a user-selected document may enter Android memory as. */
const val NFAI_EXCHANGE_V2_MAX_PACKAGE_BYTES = 128L * 1024L * 1024L

/**
 * The sole Android v2 package preflight owner. It only accepts caller-bounded bytes and keeps
 * attachment bytes in memory for the current call; it never opens SAF, Room, files, or a network.
 */
data class NfaiExchangeV2PackageRead(
    val receipt: NfaiExchangeV2PackageReceipt,
    val exchangeJson: String,
    val assets: Map<String, ByteArray>,
)

object NfaiExchangeV2PackageReader {
    fun read(packageBytes: ByteArray): NfaiExchangeV2PackageRead {
        require(packageBytes.size in 1..NFAI_EXCHANGE_V2_MAX_PACKAGE_BYTES.toInt()) { "v2 package 为空或超过 128 MiB 限制。" }
        val entries = readZip(packageBytes)
        val manifest = JSONObject(entries["manifest.json"]?.toString(Charsets.UTF_8) ?: error("v2 缺少 manifest。"))
        manifest.requireExact("format", "packageVersion", "exchangeVersion", "export", "files")
        require(manifest.optString("format") == PACKAGE_FORMAT && manifest.optInt("packageVersion") == 2 && manifest.optInt("exchangeVersion") == 2) { "v2 manifest 版本不支持。" }
        val files = manifest.getJSONArray("files")
        require(files.length() + 1 == entries.size) { "v2 package 包含未知或未列 entry。" }
        val listed = mutableSetOf<String>()
        repeat(files.length()) { index ->
            val file = files.getJSONObject(index); file.requireExact("path", "byteCount", "sha256")
            val path = file.getString("path"); val bytes = entries[path] ?: error("v2 manifest entry 不存在。")
            require(path != "manifest.json" && listed.add(path) && file.getLong("byteCount") == bytes.size.toLong() && file.getString("sha256") == sha256(bytes)) { "v2 manifest hash、大小或清单不符。" }
        }
        require(listed == entries.keys - "manifest.json") { "v2 manifest entry 集合不一致。" }
        val exchangeJson = entries["exchange.json"]?.toString(Charsets.UTF_8) ?: error("v2 缺少 exchange.json。")
        val exchange = JSONObject(exchangeJson)
        val verified = NfaiExchangeV2Ir.validate(exchange)
        require(canonical(manifest.getJSONObject("export")) == canonical(exchange.getJSONObject("export"))) { "v2 manifest export 与 exchange 不一致。" }
        val ledger = attachmentLedger(exchange)
        val expectedAssets = ledger.values.map { it.entry }.toSet()
        require(entries.keys.filter { it.startsWith("assets/") }.toSet() == expectedAssets && entries.keys.all { it == "manifest.json" || it == "exchange.json" || it.startsWith("assets/") }) { "v2 asset 账本与 package entry 不一致。" }
        ledger.values.forEach { asset ->
            val bytes = entries[asset.entry] ?: error("v2 attachment asset 不存在。")
            require(bytes.size.toLong() == asset.byteCount && sha256(bytes) == asset.sha256) { "v2 attachment asset hash 或大小不符。" }
        }
        val ownerHashes = sortedMapOf<String, String>()
        listOf("project" to "projects", "conversation" to "conversations", "knowledge" to "knowledge", "memory" to "memory", "relation" to "relations").forEach { (kind, group) ->
            exchange.getJSONArray(group).objects().forEach { owner -> ownerHashes["$kind/${owner.getString("id")}"] = sha256(canonical(owner).toByteArray(Charsets.UTF_8)) }
        }
        ownerHashes["settings/root"] = sha256(canonical(exchange.getJSONObject("settings")).toByteArray(Charsets.UTF_8))
        ledger.values.forEach { asset -> ownerHashes["asset/${asset.sha256}"] = asset.sha256 }
        val receipt = NfaiExchangeV2PackageReceipt(
            packageHash = sha256(packageBytes), semanticHash = verified.semanticHash,
            origin = exchange.getJSONObject("export").getJSONObject("origin").getString("platform"),
            sensitivity = exchange.getJSONObject("export").getString("sensitivity"),
            rootCounts = listOf("projects", "conversations", "knowledge", "memory", "relations").associateWith { exchange.getJSONArray(it).length() },
            assetCount = expectedAssets.size, assetBytes = expectedAssets.sumOf { entries.getValue(it).size.toLong() }, ownerFieldHashes = ownerHashes,
        )
        return NfaiExchangeV2PackageRead(receipt, canonical(exchange), expectedAssets.associateWith { entries.getValue(it).copyOf() })
    }

    private fun attachmentLedger(exchange: JSONObject): Map<String, Asset> {
        val byId = sortedMapOf<String, Asset>()
        fun record(value: JSONObject) {
            value.requireExact("id", "entry", "mimeType", "displayName", "byteCount", "sha256", "classification")
            val asset = Asset(value.getString("id"), value.getString("entry"), value.getString("mimeType"), value.getString("displayName"), value.getLong("byteCount"), value.getString("sha256"), value.getString("classification"))
            require(asset.id.matches(STABLE_ID) && asset.entry == "assets/${asset.sha256}" && asset.sha256.matches(SHA256) && asset.byteCount > 0 && asset.mimeType in CONVERSATION_ALLOWED_MIME_TYPES && asset.displayName.isNotBlank() && !looksLikeLocator(asset.displayName) && asset.classification in setOf("NORMAL", "HIGH_SENSITIVE")) { "v2 attachment metadata 无效。" }
            val previous = byId.putIfAbsent(asset.id, asset)
            require(previous == null || previous == asset) { "同一 v2 attachment ID 的元数据不一致。" }
        }
        exchange.getJSONArray("conversations").objects().forEach { conversation -> conversation.getJSONArray("messages").objects().filter { it.optString("kind") == "ASSET_REF" }.forEach { record(it.getJSONObject("asset")) } }
        exchange.getJSONArray("knowledge").objects().forEach { knowledge -> knowledge.getJSONArray("attachments").objects().forEach(::record) }
        return byId
    }

    private fun readZip(bytes: ByteArray): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>(); var total = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip -> while (true) { val entry = zip.nextEntry ?: break; require(!entry.isDirectory && entry.name.isNotBlank() && !entry.name.startsWith('/') && !entry.name.contains("..") && !entry.name.contains('\\') && entries.size < MAX_ENTRIES) { "v2 ZIP 包含不安全或过多 entry。" }; val value = zip.readBounded(MAX_PACKAGE_BYTES, total); require(entries.put(entry.name, value) == null) { "v2 ZIP 包含重复 entry。" }; total += value.size; require(total <= MAX_PACKAGE_BYTES) { "v2 ZIP 解压总量超过限制。" }; zip.closeEntry() } }
        require(entries.isNotEmpty()) { "v2 ZIP 包无效。" }; return entries
    }

    private fun ZipInputStream.readBounded(max: Long, alreadyRead: Long): ByteArray { val out = ByteArrayOutputStream(); val buffer = ByteArray(8192); while (true) { val read = read(buffer); if (read < 0) break; require(alreadyRead + out.size() + read <= max) { "v2 ZIP entry 超限。" }; out.write(buffer, 0, read) }; return out.toByteArray() }
    private fun JSONArray.objects(): List<JSONObject> = List(length()) { get(it) as? JSONObject ?: error("v2 JSON 数组必须是对象数组。") }
    private fun JSONObject.requireExact(vararg expected: String) { require(keys().asSequence().toSet() == expected.toSet()) { "v2 字段缺失或未知。" } }
    private fun canonical(value: Any?): String = when (value) { null, JSONObject.NULL -> "null"; is String -> quote(value); is Boolean, is Int, is Long -> value.toString(); is Number -> require(value.toDouble().isFinite() && value.toLong().toDouble() == value.toDouble()) { "仅允许安全整数。" }.let { value.toLong().toString() }; is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") { "${quote(it)}:${canonical(value.get(it))}" }; is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.get(it)) }; else -> error("不支持的 JSON 值。") }
    private fun quote(value: String) = buildString { append('"'); value.forEach { char -> when (char) { '"' -> append("\\\""); '\\' -> append("\\\\"); '\b' -> append("\\b"); '\u000C' -> append("\\f"); '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t"); else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char) } }; append('"') }
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun looksLikeLocator(value: String) = value.contains(Regex("(?i)(://|^/|^~[/\\\\]|^[a-z]:[/\\\\]|content:|file:|\\\\\\\\)")) || value.contains('/') || value.contains('\\')
    private data class Asset(val id: String, val entry: String, val mimeType: String, val displayName: String, val byteCount: Long, val sha256: String, val classification: String)
    private const val PACKAGE_FORMAT = "nfai.exchange.package"
    private const val MAX_ENTRIES = 100_000
    private const val MAX_PACKAGE_BYTES = NFAI_EXCHANGE_V2_MAX_PACKAGE_BYTES
    private val STABLE_ID = Regex("[a-z0-9][a-z0-9_-]{1,63}")
    private val SHA256 = Regex("[a-f0-9]{64}")
}
