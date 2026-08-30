package com.nanzhufeng.ai.domain

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Clock
import java.util.UUID

/**
 * Read-only owner -> v2 IR mapper.  It deliberately neither creates a package nor registers an
 * Android entry point: the future package/SAF path must consume this already-validated IR.
 */
sealed interface WorkspaceExchangeV2Mapping {
    data class Prepared(val exchangeJson: String) : WorkspaceExchangeV2Mapping
    data class Rejected(val reason: String) : WorkspaceExchangeV2Mapping
}

class NfaiExchangeV2OwnerMapper(
    private val source: NfaiExchangeWorkspaceSource,
    private val appVersion: String,
    private val clock: Clock,
    private val exportId: () -> String = { UUID.randomUUID().toString() },
) {
    fun map(
        selection: NfaiExchangeWorkspaceSelection,
        settings: NfaiExchangeSafeSettings,
    ): WorkspaceExchangeV2Mapping = runCatching {
        val selected = selection.objects
        val projects = selected.projectIds.sorted().map { id -> source.project(ProjectId(id)) ?: error("所选项目不存在或不可读取。") }
        val conversations = selected.conversationIds.sorted().map { id -> source.conversation(ConversationId(id)) ?: error("所选对话不存在或不可读取。") }
        val knowledge = selected.knowledgeIds.sorted().map { id -> source.knowledge(KnowledgeItemId(id)) ?: error("所选知识不存在或不可读取。") }
        val memories = selected.memoryIds.sorted().map { id -> source.memory(MemoryId(id)) ?: error("所选记忆不存在或不可读取。") }
        val relations = selected.relationIds.sorted().map { id -> source.relationship(KnowledgeRelationshipId(id)) ?: error("所选关系不存在或不可读取。") }

        val projectIds = projects.map { it.project.id.value }.toSet()
        val conversationIds = conversations.map { it.conversation.id.value }.toSet()
        val knowledgeIds = knowledge.map { it.item.id.value }.toSet()
        val memoryById = memories.associateBy { it.memory.id.value }

        projects.forEach { snapshot ->
            require(snapshot.project.deletedAt == null) { "已删除项目不能进入 v2 交换包。" }
            validateOrderedHistory(snapshot.instructionRevisions.map { it.revision }, "项目指令历史")
            snapshot.instructionRevisions.forEach { revision ->
                require(revision.projectId == snapshot.project.id && revision.source == ProjectInstructionSource.USER) { "项目指令历史 owner 不一致。" }
                require(sha256(revision.content) == revision.contentHash) { "项目指令历史摘要不一致。" }
            }
        }
        conversations.forEach { snapshot ->
            val conversation = snapshot.conversation
            require(conversation.surface == ConversationSurface.CHAT && conversation.deletedAt == null) { "工作区或已删除对话不能进入 v2 交换包。" }
            require(conversation.projectId == null || conversation.projectId in projectIds) { "项目对话必须同时明确选择所属项目。" }
            require(conversation.currentLeafMessageId != null && snapshot.nodes.isNotEmpty()) { "所选对话没有完整消息树。" }
            require(snapshot.draft.text.isBlank() && snapshot.draft.attachments.isEmpty()) { "请先处理所选对话草稿后再交换。" }
            require(snapshot.nodes.all { node ->
                node.conversationId == conversation.id && node.deliveryState == MessageDeliveryState.COMPLETE &&
                    node.invocation == null && node.checkpoint == null && node.content.none { it is ContentBlock.ToolResult || it is ContentBlock.Reasoning }
            }) { "运行中、调用记录或工具结果不能进入 v2 交换。" }
            conversation.settings.memorySources.forEach { reference ->
                val memory = memoryById[reference.memoryId] ?: error("会话记忆来源不在选择闭包中。")
                require(reference.sourceKind == memory.memory.source.name && reference.sourceVersion == memory.memory.schemaVersion) {
                    "会话记忆来源类型或版本无法由 owner 精确保真。"
                }
            }
            requireSafeIdentifier(conversation.settings.defaultModelId, "默认模型")
            requireSafeIdentifier(conversation.settings.harnessId, "Harness")
        }
        knowledge.forEach { snapshot ->
            val item = snapshot.item
            require(item.sourceEvidence.isNotEmpty() && snapshot.revisions.isNotEmpty()) { "知识来源或历史缺失，不能降级交换。" }
            require(snapshot.lifecycle.scope != KnowledgeScope.PROJECT || snapshot.lifecycle.projectId?.value in projectIds) { "项目知识必须同时明确选择所属项目。" }
            require(snapshot.lifecycle.contentHash == itemContentHash(item.title, item.body)) { "知识 owner 内容摘要不一致。" }
            item.sourceEvidence.forEach { evidence ->
                require(evidence.sourceReference == null) { "知识 sourceReference 不能进入 v2 交换。" }
                require(evidence.contributedFields.isNotEmpty() && evidence.contributedFields.all(::isSafeFieldName)) { "知识来源贡献字段无效。" }
            }
            validateOrderedHistory(snapshot.revisions.map { it.revision }, "知识历史")
            snapshot.revisions.forEach { revision ->
                require(revision.knowledgeId == item.id && revision.contentHash == itemContentHash(revision.title, revision.body)) { "知识历史 owner 或摘要不一致。" }
            }
        }
        memories.forEach { snapshot ->
            val memory = snapshot.memory
            require(snapshot.revisions.isNotEmpty()) { "记忆历史缺失，不能降级交换。" }
            require(memory.scope.projectId == null || memory.scope.projectId.value in projectIds) { "项目范围记忆必须同时明确选择所属项目。" }
            require(memory.scope.conversationId == null || memory.scope.conversationId.value in conversationIds) { "对话范围记忆必须同时明确选择所属对话。" }
            requireSafeMemorySource(memory.sourceStableId, "记忆来源标识")
            requireSafeMemorySource(memory.sourceSummary, "记忆来源摘要")
            require(memory.contentHash == MemoryDomain.sha256("${canonical(memory.title)}\n${canonical(memory.body)}")) { "记忆内容摘要不一致。" }
            require(memory.conceptHash == MemoryDomain.sha256(canonical(memory.title))) { "记忆概念摘要不一致。" }
            validateOrderedHistory(snapshot.revisions.map { it.revision }, "记忆历史")
            snapshot.revisions.forEach { revision ->
                require(revision.memoryId == memory.id && revision.contentHash == MemoryDomain.sha256("${canonical(revision.title)}\n${canonical(revision.body)}")) { "记忆历史 owner 或摘要不一致。" }
                requireSafeMemorySource(revision.sourceStableId, "记忆历史来源标识")
                requireSafeMemorySource(revision.sourceSummary, "记忆历史来源摘要")
            }
        }
        relations.forEach { snapshot ->
            val relation = snapshot.relationship
            require(snapshot.revisions.isNotEmpty()) { "知识关系历史缺失，不能降级交换。" }
            require(relation.fromKnowledgeId.value in knowledgeIds && relation.toKnowledgeId.value in knowledgeIds) { "关系两端知识必须都被明确选择。" }
            require((relation.scope == KnowledgeScope.PROJECT) == (relation.projectId != null) &&
                (relation.projectId == null || relation.projectId.value in projectIds)) { "关系 scope/project 无法精确保真。" }
            validateOrderedHistory(snapshot.revisions.map { it.revision }, "知识关系历史")
            snapshot.revisions.forEach { revision -> require(revision.relationshipId == relation.id) { "知识关系历史 owner 不一致。" } }
        }

        val attachmentReferences = (conversations.flatMap { snapshot ->
            snapshot.nodes.flatMap { node -> node.content.filterIsInstance<ContentBlock.Attachment>().map { it.attachment.toPrivateReference() } }
        } + knowledge.flatMap { it.item.attachments }).sortedBy { it.id.value }
        require(attachmentReferences.map { it.id.value }.toSet() == selected.attachmentIds) { "所选附件必须与 owner 引用完全一致。" }
        val attachmentJson = attachmentReferences.associateBy({ it.id.value }) { attachment ->
            val byteCount = requireNotNull(attachment.byteCount) { "附件元数据不完整。" }
            val hash = requireNotNull(attachment.sha256) { "附件元数据不完整。" }
            require(byteCount > 0) { "附件元数据不完整。" }
            require(attachment.mimeType in CONVERSATION_ALLOWED_MIME_TYPES) { "附件 MIME 未被交换合同支持。" }
            val displayName = requireNotNull(attachment.displayName) { "附件显示名缺失，不能猜测。" }
            require(displayName.isNotBlank() && !looksLikeLocator(displayName)) { "附件显示名疑似路径或 URI。" }
            val owner = source.attachment(attachment.id) ?: error("附件 owner 不可读取。")
            require(owner.isReadyPrivateCopy() && owner.id == attachment.id && owner.mimeType == attachment.mimeType && owner.displayName == attachment.displayName && owner.byteCount == byteCount && owner.sha256 == hash) { "附件引用与 owner 不一致。" }
            val bytes = (source.readPrivateAttachment(owner) as? AttachmentReadResult.Content)?.bytes ?: error("附件私有内容不可读取。")
            require(bytes.size.toLong() == byteCount && sha256(bytes) == hash) { "附件内容摘要不一致。" }
            JSONObject().apply {
                put("id", attachment.id.value); put("entry", "assets/$hash"); put("mimeType", attachment.mimeType)
                put("displayName", displayName); put("byteCount", byteCount); put("sha256", hash)
                put("classification", requireNotNull(selection.attachmentClassifications[attachment.id.value]))
            }
        }

        val exchange = JSONObject().apply {
            put("format", "nfai.exchange"); put("version", 2)
            put("export", JSONObject().apply {
                put("id", exportId()); put("createdAt", clock.instant().toString())
                put("origin", JSONObject().put("platform", "ANDROID").put("appVersion", appVersion))
                put("semanticHash", ""); put("sensitivity", if (containsHighSensitive(conversations, knowledge, memories, selection)) "HIGH_SENSITIVE" else "NORMAL")
            })
            put("projects", JSONArray(projects.map(::projectJson)))
            put("conversations", JSONArray(conversations.map { conversationJson(it, attachmentJson) }))
            put("knowledge", JSONArray(knowledge.map { knowledgeJson(it, attachmentJson) }))
            put("memory", JSONArray(memories.map(::memoryJson)))
            put("relations", JSONArray(relations.map(::relationJson)))
            put("settings", JSONObject().put("uiLanguage", settings.uiLanguage).put("theme", settings.theme))
        }
        exchange.getJSONObject("export").put("semanticHash", NfaiExchangeV2Ir.semanticHash(exchange))
        NfaiExchangeV2Ir.validate(exchange)
        WorkspaceExchangeV2Mapping.Prepared(exchange.toString())
    }.getOrElse { WorkspaceExchangeV2Mapping.Rejected(it.message ?: "所选对象无法构成完整 v2 交换 IR。") }

    private fun projectJson(snapshot: ProjectSnapshot) = snapshot.project.let { project -> JSONObject().apply {
        put("id", project.id.value); put("title", project.title); put("description", project.description)
        put("appearance", JSONObject().put("color", project.color?.name ?: JSONObject.NULL).put("icon", project.icon?.name ?: JSONObject.NULL))
        put("pinned", project.pinnedAt != null); put("archived", project.archivedAt != null); put("createdAt", project.createdAt.toString()); put("updatedAt", project.updatedAt.toString()); put("schemaVersion", project.schemaVersion)
        put("instructionHistory", JSONArray(snapshot.instructionRevisions.sortedBy { it.revision }.map { revision -> JSONObject().apply {
            put("id", revision.id.value); put("revision", revision.revision); put("content", revision.content); put("source", revision.source.name); put("contentHash", revision.contentHash); put("createdAt", revision.createdAt.toString()); put("schemaVersion", revision.schemaVersion)
        } }))
    } }

    private fun conversationJson(snapshot: ConversationSnapshot, attachments: Map<String, JSONObject>) = snapshot.conversation.let { conversation -> JSONObject().apply {
        put("id", conversation.id.value); put("projectId", conversation.projectId ?: JSONObject.NULL); put("title", conversation.title); put("currentLeafId", requireNotNull(conversation.currentLeafMessageId).value)
        put("pinned", conversation.pinnedAt != null); put("archived", conversation.archivedAt != null); put("revision", conversation.revision); put("createdAt", conversation.createdAt.toString()); put("updatedAt", conversation.updatedAt.toString())
        put("autoTitlePending", conversation.autoTitlePending); put("surface", conversation.surface.name); put("schemaVersion", conversation.schemaVersion)
        put("settings", JSONObject().apply {
            val settings = conversation.settings
            put("defaultProviderId", settings.defaultProviderId?.name ?: JSONObject.NULL); put("defaultModelId", settings.defaultModelId ?: JSONObject.NULL); put("harnessId", settings.harnessId ?: JSONObject.NULL); put("harnessVersion", settings.harnessVersion ?: JSONObject.NULL)
            put("contextPolicyVersion", settings.contextPolicyVersion); put("memorySources", JSONArray(settings.memorySources.map { source -> JSONObject().put("memoryId", source.memoryId).put("sourceKind", source.sourceKind).put("sourceVersion", source.sourceVersion) })); put("schemaVersion", settings.schemaVersion)
        })
        put("messages", JSONArray(snapshot.nodes.sortedWith(compareBy<MessageNode> { it.createdAt }.thenBy { it.id.value }).map { node -> JSONObject().apply {
            put("id", node.id.value); put("parentId", node.parentMessageId?.value ?: JSONObject.NULL); put("ordinal", node.siblingPosition); put("role", node.role.name.lowercase()); put("delivery", node.deliveryState.name); put("revision", node.revision.revision); put("createdAt", node.createdAt.toString())
            put("blocks", JSONArray(node.content.mapIndexed { ordinal, block -> when (block) {
                is ContentBlock.Text -> JSONObject().put("kind", "TEXT").put("ordinal", ordinal).put("text", block.text)
                is ContentBlock.Reasoning -> error("模型思考过程不能进入 v2 交换。")
                is ContentBlock.Attachment -> JSONObject().put("kind", "ASSET_REF").put("ordinal", ordinal).put("asset", attachments[block.attachment.id.value] ?: error("消息附件缺少 owner 元数据。"))
                is ContentBlock.ToolResult -> error("工具结果不能进入 v2 交换。")
            } }))
        } }))
    } }

    private fun knowledgeJson(snapshot: KnowledgeSnapshot, attachments: Map<String, JSONObject>) = snapshot.let { value -> JSONObject().apply {
        val item = value.item; val lifecycle = value.lifecycle
        put("id", item.id.value); put("title", item.title); put("body", item.body)
        put("sourceEvidence", JSONArray(item.sourceEvidence.sortedWith(compareBy<SourceEvidence> { it.receivedAt }.thenBy { it.sourceType.name }).map { source -> JSONObject().put("sourceType", source.sourceType.name).put("receivedAt", source.receivedAt.toString()).put("contributedFields", JSONArray(source.contributedFields.sorted())) }))
        put("provenance", JSONObject().put("candidateId", item.provenance.candidateId.value).put("invocationId", item.provenance.invocationId.value).put("providerId", item.provenance.providerId.name).put("modelId", item.provenance.modelId).put("harnessVersion", item.provenance.harnessVersion))
        put("attachments", JSONArray(item.attachments.sortedBy { it.id.value }.map { attachment -> attachments[attachment.id.value] ?: error("知识附件缺少 owner 元数据。 ") }))
        put("status", lifecycle.status.name); put("scope", lifecycle.scope.name); put("projectId", lifecycle.projectId?.value ?: JSONObject.NULL); put("tags", JSONArray(lifecycle.tags.sorted())); put("contentHash", lifecycle.contentHash); put("createdAt", item.createdAt.toString()); put("updatedAt", lifecycle.updatedAt.toString()); put("schemaVersion", item.schemaVersion)
        put("history", JSONArray(value.revisions.sortedBy { it.revision }.map { revision -> JSONObject().apply {
            put("id", revision.id.value); put("revision", revision.revision); put("title", revision.title); put("body", revision.body); put("status", revision.status.name); put("scope", revision.scope.name); put("projectId", revision.projectId?.value ?: JSONObject.NULL); put("tags", JSONArray(revision.tags.sorted())); put("contentHash", revision.contentHash); put("createdAt", revision.createdAt.toString())
        } }))
    } }

    private fun memoryJson(snapshot: MemorySnapshot) = snapshot.memory.let { memory -> JSONObject().apply {
        put("id", memory.id.value); put("title", memory.title); put("body", memory.body); put("scope", memory.scope.kind.name); put("scopeId", memory.scope.projectId?.value ?: memory.scope.conversationId?.value ?: JSONObject.NULL); put("source", memory.source.name); put("sourceStableId", memory.sourceStableId); put("sourceSummary", memory.sourceSummary); put("status", memory.status.name); put("contentHash", memory.contentHash); put("conceptHash", memory.conceptHash); put("createdAt", memory.createdAt.toString()); put("updatedAt", memory.updatedAt.toString()); put("lastConfirmedAt", memory.lastConfirmedAt.toString()); put("deletedAt", memory.deletedAt?.toString() ?: JSONObject.NULL); put("schemaVersion", memory.schemaVersion)
        put("history", JSONArray(snapshot.revisions.sortedBy { it.revision }.map { revision -> JSONObject().apply {
            put("id", revision.id.value); put("revision", revision.revision); put("title", revision.title); put("body", revision.body); put("scope", revision.scope.kind.name); put("scopeId", revision.scope.projectId?.value ?: revision.scope.conversationId?.value ?: JSONObject.NULL); put("source", revision.source.name); put("sourceStableId", revision.sourceStableId); put("sourceSummary", revision.sourceSummary); put("status", revision.status.name); put("contentHash", revision.contentHash); put("createdAt", revision.createdAt.toString()); put("schemaVersion", revision.schemaVersion)
        } }))
    } }

    private fun relationJson(snapshot: KnowledgeRelationshipSnapshot) = snapshot.relationship.let { relation -> JSONObject().apply {
        put("id", relation.id.value); put("type", relation.type.name); put("fromId", relation.fromKnowledgeId.value); put("toId", relation.toKnowledgeId.value); put("scope", relation.scope.name); put("projectId", relation.projectId?.value ?: JSONObject.NULL); put("status", relation.status.name); put("createdAt", relation.createdAt.toString()); put("updatedAt", relation.updatedAt.toString()); put("createdByIntentId", relation.createdByIntentId.value); put("latestIntentId", relation.latestIntentId.value); put("suggestionSource", relation.suggestionSource.name)
        put("history", JSONArray(snapshot.revisions.sortedBy { it.revision }.map { revision -> JSONObject().put("id", revision.id.value).put("revision", revision.revision).put("action", revision.action.name).put("status", revision.status.name).put("intentId", revision.intentId.value).put("createdAt", revision.createdAt.toString()) }))
    } }

    private fun ConversationAttachmentReference.toPrivateReference() = AttachmentReference("owner-only", mimeType, displayName, id, byteCount, sha256)
    private fun containsHighSensitive(conversations: List<ConversationSnapshot>, knowledge: List<KnowledgeSnapshot>, memories: List<MemorySnapshot>, selection: NfaiExchangeWorkspaceSelection) =
        conversations.any { it.nodes.any { node -> node.content.filterIsInstance<ContentBlock.Text>().any { text -> MemoryDomain.sensitiveRejection(text.text) != null } } } || knowledge.any { MemoryDomain.sensitiveRejection(it.item.body) != null } || memories.any { MemoryDomain.sensitiveRejection(it.memory.body) != null } || selection.attachmentClassifications.values.any { it == "HIGH_SENSITIVE" }
    private fun validateOrderedHistory(revisions: List<Int>, name: String) { require(revisions == revisions.sorted() && revisions.distinct().size == revisions.size && revisions.all { it > 0 }) { "$name revision 无效。" } }
    private fun itemContentHash(title: String, body: String) = MemoryDomain.sha256("${canonical(title)}\n${canonical(body)}")
    private fun canonical(value: String) = value.trim().lowercase(java.util.Locale.ROOT).replace(Regex("\\s+"), " ")
    private fun sha256(value: String) = sha256(value.toByteArray())
    private fun sha256(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
    private fun isSafeFieldName(value: String) = value.matches(Regex("[a-z][A-Za-z0-9]{0,63}"))
    private fun requireSafeIdentifier(value: String?, label: String) { if (value != null) require(value.isNotBlank() && !looksLikeLocator(value) && !containsForbidden(value)) { "$label 含不安全定位或凭据语义。" } }
    private fun requireSafeMemorySource(value: String, label: String) { require(value.isNotBlank() && !looksLikeLocator(value) && !containsForbidden(value) && MemoryDomain.sensitiveRejection(value) == null) { "$label 含不安全定位或敏感值。" } }
    private fun looksLikeLocator(value: String) = value.contains(Regex("(?i)(://|^/|^~[/\\\\]|^[a-z]:[/\\\\]|content:|file:|\\\\\\\\)")) || value.contains('/') || value.contains('\\')
    private fun containsForbidden(value: String) = value.contains(Regex("(?i)(credential|api[_-]?key|authorization|provider[_-]?(raw|payload)|runtime[_-]?chunk|diagnostic|route[_-]?pref)"))
}
