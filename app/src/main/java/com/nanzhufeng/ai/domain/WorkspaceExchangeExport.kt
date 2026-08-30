package com.nanzhufeng.ai.domain

import org.json.JSONArray
import org.json.JSONObject
import java.time.Clock
import java.util.UUID

/**
 * P6's full v1 export mapper. It reads existing domain owners, but it does not mutate Room,
 * stage a file, read settings, or decide a user's selection. Every cross-object dependency is
 * supplied explicitly so a project, relationship, or private binary is never silently added.
 */
data class NfaiExchangeWorkspaceSelection(
    val objects: NfaiExchangeExportSelection,
    val attachmentClassifications: Map<String, String> = emptyMap(),
) {
    init {
        require(attachmentClassifications.keys == objects.attachmentIds) { "每个已选附件都必须有明确敏感级别。" }
        require(attachmentClassifications.values.all { it in setOf("NORMAL", "HIGH_SENSITIVE") }) { "附件敏感级别无效。" }
    }
}

data class NfaiExchangeSafeSettings(val uiLanguage: String, val theme: String) {
    init {
        require(uiLanguage.matches(Regex("[a-z]{2,3}(-[A-Z]{2})?"))) { "安全语言设置无效。" }
        require(theme in setOf("SYSTEM", "LIGHT", "DARK")) { "安全主题设置无效。" }
    }
}

sealed interface WorkspaceExchangePreparation {
    data class Prepared(val snapshot: NfaiExchangePreparedSnapshot) : WorkspaceExchangePreparation
    data class Rejected(val reason: String) : WorkspaceExchangePreparation
}

/** Input adapter for the five existing Android domain owners and the private attachment boundary. */
interface NfaiExchangeWorkspaceSource {
    fun project(id: ProjectId): ProjectSnapshot?
    fun conversation(id: ConversationId): ConversationSnapshot?
    fun knowledge(id: KnowledgeItemId): KnowledgeSnapshot?
    fun memory(id: MemoryId): MemorySnapshot?
    fun relationship(id: KnowledgeRelationshipId): KnowledgeRelationshipSnapshot?
    fun attachment(id: AttachmentId): AttachmentReference?
    fun readPrivateAttachment(asset: AttachmentReference): AttachmentReadResult
}

class RepositoryNfaiExchangeWorkspaceSource(
    private val projects: ProjectRepository,
    private val conversations: ConversationRepository,
    private val knowledge: KnowledgeManagementRepository,
    private val memories: MemoryRepository,
    private val relationships: KnowledgeRelationshipRepository,
    private val attachments: PrivateAttachmentRepository,
    private val privateStore: PrivateAttachmentStore,
) : NfaiExchangeWorkspaceSource {
    override fun project(id: ProjectId) = projects.findById(id)
    override fun conversation(id: ConversationId) = conversations.findById(id)
    override fun knowledge(id: KnowledgeItemId) = knowledge.findSnapshot(id)
    override fun memory(id: MemoryId) = memories.findById(id)
    override fun relationship(id: KnowledgeRelationshipId) = relationships.list(KnowledgeRelationshipListFilter(status = null)).firstOrNull { it.relationship.id == id }
    override fun attachment(id: AttachmentId) = attachments.findById(id)
    override fun readPrivateAttachment(asset: AttachmentReference) = privateStore.read(asset)
}

class ExportWorkspaceExchangeUseCase(
    private val source: NfaiExchangeWorkspaceSource,
    private val appVersion: String,
    private val clock: Clock,
    private val exportId: () -> String = { UUID.randomUUID().toString() },
) {
    fun prepare(
        selection: NfaiExchangeWorkspaceSelection,
        settings: NfaiExchangeSafeSettings,
    ): WorkspaceExchangePreparation = runCatching {
        val objects = selection.objects
        val projects = objects.projectIds.sorted().map { id -> source.project(ProjectId(id)) ?: error("所选项目不存在或不可读取。") }
        val conversations = objects.conversationIds.sorted().map { id -> source.conversation(ConversationId(id)) ?: error("所选对话不存在或不可读取。") }
        val knowledge = objects.knowledgeIds.sorted().map { id -> source.knowledge(KnowledgeItemId(id)) ?: error("所选知识不存在或不可读取。") }
        val memories = objects.memoryIds.sorted().map { id -> source.memory(MemoryId(id)) ?: error("所选记忆不存在或不可读取。") }
        val relations = objects.relationIds.sorted().map { id -> source.relationship(KnowledgeRelationshipId(id)) ?: error("所选关系不存在或不可读取。") }

        projects.forEach { snapshot ->
            require(snapshot.project.deletedAt == null) { "已删除项目不能进入 v1 交换包。" }
        }
        conversations.forEach { snapshot ->
            val conversation = snapshot.conversation
            require(conversation.surface == ConversationSurface.CHAT && conversation.deletedAt == null) { "工作区或已删除对话不能进入 v1 交换包。" }
            require(conversation.currentLeafMessageId != null && snapshot.nodes.isNotEmpty()) { "所选对话没有完整消息树。" }
            require(snapshot.draft.text.isBlank() && snapshot.draft.attachments.isEmpty()) { "请先处理所选对话草稿后再交换。" }
            require(conversation.projectId == null || conversation.projectId in objects.projectIds) { "项目对话必须同时明确选择所属项目。" }
            require(conversation.settings.memorySources.isEmpty()) { "带有对话记忆引用的对话尚无 v1 可回导表达。" }
            require(snapshot.nodes.all { node -> node.content.isNotEmpty() && node.content.none { it is ContentBlock.ToolResult || it is ContentBlock.Reasoning } }) { "工具结果或模型思考过程尚无 v1 可回导表达。" }
        }
        knowledge.forEach { snapshot ->
            require(snapshot.item.attachments.isEmpty()) { "知识附件尚无 v1 关联位置，不能部分导出。" }
            require(snapshot.lifecycle.scope != KnowledgeScope.PROJECT || snapshot.lifecycle.projectId?.value in objects.projectIds) { "项目知识必须同时明确选择所属项目。" }
        }
        memories.forEach { snapshot ->
            val scope = snapshot.memory.scope
            require(scope.projectId == null || scope.projectId.value in objects.projectIds) { "项目范围记忆必须同时明确选择所属项目。" }
            require(scope.conversationId == null || scope.conversationId.value in objects.conversationIds) { "对话范围记忆必须同时明确选择所属对话。" }
        }
        relations.forEach { snapshot ->
            val relation = snapshot.relationship
            require(relation.fromKnowledgeId.value in objects.knowledgeIds && relation.toKnowledgeId.value in objects.knowledgeIds) { "关系两端知识必须都被明确选择。" }
            require(relation.type == KnowledgeRelationshipType.RELATED) { "当前 v1 不能表示此知识关系类型。" }
        }

        val assetReferences = conversations.flatMap { snapshot ->
            snapshot.nodes.flatMap { node -> node.content.filterIsInstance<ContentBlock.Attachment>().map { it.attachment } }
        }
        require(assetReferences.map { it.id.value }.toSet() == objects.attachmentIds) { "所选附件必须与所选对话消息引用完全一致。" }
        val assets = assetReferences.sortedBy { it.id.value }.map { reference ->
            val asset = source.attachment(reference.id) ?: error("所选附件无法从私有目录读取。")
            require(asset.mimeType == reference.mimeType && asset.byteCount == reference.byteCount && asset.sha256 == reference.sha256) { "附件元数据与消息引用不一致。" }
            val bytes = (source.readPrivateAttachment(asset) as? AttachmentReadResult.Content)?.bytes ?: error("附件私有内容不可用，未生成部分交换包。")
            require(bytes.size.toLong() == reference.byteCount && sha256(bytes) == reference.sha256) { "附件完整性校验失败。" }
            NfaiExchangeAsset("assets/${reference.sha256}", bytes)
        }.distinctBy { it.entry }

        val highSensitive = projects.any { false } ||
            conversations.any { it.nodes.any { node -> node.content.filterIsInstance<ContentBlock.Text>().any { text -> isHighSensitive(text.text) } } } ||
            knowledge.any { isHighSensitive(it.item.body) } ||
            memories.any { isHighSensitive(it.memory.body) } ||
            selection.attachmentClassifications.values.any { it == "HIGH_SENSITIVE" }
        val exchange = JSONObject().apply {
            put("format", NFAI_EXCHANGE_V1_FORMAT)
            put("version", NFAI_EXCHANGE_V1_VERSION)
            put("export", JSONObject().apply {
                put("id", exportId()); put("createdAt", clock.instant().toString())
                put("origin", JSONObject().put("platform", "ANDROID").put("appVersion", appVersion))
                put("semanticHash", ""); put("sensitivity", if (highSensitive) "HIGH_SENSITIVE" else "NORMAL")
            })
            put("projects", JSONArray(projects.map(::projectJson)))
            put("conversations", JSONArray(conversations.map { conversationJson(it, selection.attachmentClassifications) }))
            put("knowledge", JSONArray(knowledge.map(::knowledgeJson)))
            put("memory", JSONArray(memories.map(::memoryJson)))
            put("relations", JSONArray(relations.map(::relationJson)))
            put("settings", JSONObject().put("uiLanguage", settings.uiLanguage).put("theme", settings.theme))
        }
        WorkspaceExchangePreparation.Prepared(NfaiExchangePreparedSnapshot(objects, NfaiExchangeV1Gateway.withComputedSemanticHash(exchange), assets))
    }.getOrElse { WorkspaceExchangePreparation.Rejected(it.message ?: "所选对象无法构成完整交换包。") }

    private fun projectJson(snapshot: ProjectSnapshot) = snapshot.project.let { project -> JSONObject().apply {
        put("id", project.id.value); put("title", project.title); put("description", project.description)
        put("pinned", project.pinnedAt != null); put("archived", project.archivedAt != null)
        put("revision", snapshot.instructionRevisions.maxOfOrNull { it.revision } ?: 0)
        put("createdAt", project.createdAt.toString()); put("updatedAt", project.updatedAt.toString())
    } }

    private fun conversationJson(snapshot: ConversationSnapshot, classifications: Map<String, String>) = snapshot.conversation.let { conversation -> JSONObject().apply {
        put("id", conversation.id.value); put("projectId", conversation.projectId ?: JSONObject.NULL); put("title", conversation.title)
        put("currentLeafId", requireNotNull(conversation.currentLeafMessageId).value); put("pinned", conversation.pinnedAt != null); put("archived", conversation.archivedAt != null)
        put("revision", conversation.revision); put("createdAt", conversation.createdAt.toString()); put("updatedAt", conversation.updatedAt.toString())
        put("messages", JSONArray(snapshot.nodes.sortedWith(compareBy<MessageNode> { it.createdAt }.thenBy { it.id.value }).map { node -> JSONObject().apply {
            put("id", node.id.value); put("parentId", node.parentMessageId?.value ?: JSONObject.NULL); put("ordinal", node.siblingPosition)
            put("role", node.role.name.lowercase()); put("delivery", node.deliveryState.name); put("revision", node.revision.revision); put("createdAt", node.createdAt.toString())
            put("blocks", JSONArray(node.content.mapIndexed { ordinal, block -> when (block) {
                is ContentBlock.Text -> JSONObject().put("kind", "TEXT").put("ordinal", ordinal).put("text", block.text)
                is ContentBlock.Reasoning -> error("模型思考过程尚无 v1 可回导表达。")
                is ContentBlock.Attachment -> block.attachment.let { attachment -> JSONObject().put("kind", "ASSET_REF").put("ordinal", ordinal).put("asset", JSONObject().apply {
                    put("id", attachment.id.value); put("entry", "assets/${attachment.sha256}"); put("mimeType", attachment.mimeType)
                    put("displayName", attachment.displayName ?: "附件"); put("byteCount", attachment.byteCount); put("sha256", attachment.sha256)
                    put("classification", requireNotNull(classifications[attachment.id.value]))
                }) }
                is ContentBlock.ToolResult -> error("工具结果尚无 v1 可回导表达。")
            } }))
        } }))
    } }

    private fun knowledgeJson(snapshot: KnowledgeSnapshot) = snapshot.let { value -> JSONObject().apply {
        val item = value.item; val lifecycle = value.lifecycle
        put("id", item.id.value); put("title", item.title); put("body", item.body); put("tags", JSONArray(lifecycle.tags.sorted()))
        put("scope", lifecycle.scope.name); put("projectId", lifecycle.projectId?.value ?: JSONObject.NULL); put("status", lifecycle.status.name)
        // v1 defines contentHash as the exchanged body bytes. Android's lifecycle hash also
        // includes owner-specific title normalization, so forwarding it would create a package
        // that its own gateway (and Desktop's semantic IR) correctly rejects.
        put("revision", value.revisions.maxOfOrNull { it.revision } ?: 0); put("contentHash", sha256(item.body.toByteArray()))
        put("createdAt", item.createdAt.toString()); put("updatedAt", lifecycle.updatedAt.toString())
        put("classification", if (isHighSensitive(item.body)) "HIGH_SENSITIVE" else "NORMAL")
    } }

    private fun memoryJson(snapshot: MemorySnapshot) = snapshot.memory.let { memory -> JSONObject().apply {
        put("id", memory.id.value); put("body", memory.body); put("scope", memory.scope.kind.name)
        put("scopeId", memory.scope.projectId?.value ?: memory.scope.conversationId?.value ?: JSONObject.NULL); put("status", memory.status.name)
        put("revision", snapshot.revisions.maxOfOrNull { it.revision } ?: 0); put("contentHash", memory.contentHash)
        put("createdAt", memory.createdAt.toString()); put("updatedAt", memory.updatedAt.toString())
        put("classification", if (isHighSensitive(memory.body)) "HIGH_SENSITIVE" else "NORMAL")
    } }

    private fun relationJson(snapshot: KnowledgeRelationshipSnapshot) = snapshot.relationship.let { relation -> JSONObject().apply {
        put("id", relation.id.value); put("fromId", relation.fromKnowledgeId.value); put("toId", relation.toKnowledgeId.value)
        put("kind", relation.type.name); put("status", relation.status.name); put("revision", snapshot.revisions.maxOfOrNull { it.revision } ?: 0)
        put("createdAt", relation.createdAt.toString())
    } }

    private fun isHighSensitive(value: String): Boolean = MemoryDomain.sensitiveRejection(value) != null
    private fun sha256(bytes: ByteArray): String = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
