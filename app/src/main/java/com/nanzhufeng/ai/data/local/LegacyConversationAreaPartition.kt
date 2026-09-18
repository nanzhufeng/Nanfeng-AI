package com.nanzhufeng.ai.data.local

import android.database.sqlite.SQLiteDatabase

/** Version 69 ownership manifest. No column-name inference or destructive fallback. */
internal object LegacyConversationAreaPartition {
    data class Edge(val parent: String, val parentColumns: String, val child: String, val childColumns: String)
    val tables = """capture_drafts
capture_draft_evidence
capture_draft_attachments
knowledge_items
knowledge_revisions
knowledge_tags
knowledge_item_tags
knowledge_revision_tags
knowledge_evidence
knowledge_attachments
private_attachment_assets
glm_ocr_tasks
temporary_conversation_recovery
temporary_conversation_messages
temporary_conversation_attachments
invocation_records
invocation_task_runs
provider_attempts
generations
generation_validations
conversation_real_text_executions
usage_ledger_entries
local_exact_reuse_entries
generated_candidates
conversations
message_nodes
message_content_blocks
local_search_index
conversation_drafts
conversation_draft_attachments
conversation_memory_sources
ai_runtime_events
conversation_runtime_states
normal_chat_send_attempts
resumable_attachment_uploads
assistant_response_model_attributions
cloud_response_model_usages
compare_conversation_sessions
compare_conversation_branches
compare_branch_execution_receipts
compare_branch_runtime_states
compare_branch_runtime_events
compare_branch_terminal_intents
compare_branch_follow_up_intents
compare_branch_adoption_intents
compare_synthesis_intents
conversation_attempt_lineages
debug_call_log
conversation_management_intents
projects
project_instruction_revisions
project_intents
knowledge_project_scopes
knowledge_relationships
knowledge_relationship_revisions
knowledge_relationship_intents
markdown_import_tasks
markdown_import_items
json_knowledge_import_tasks
json_knowledge_import_items
chatgpt_export_import_tasks
chatgpt_export_import_items
chatgpt_export_import_messages
chatgpt_import_provenance
chatgpt_import_receipts
claude_export_import_tasks
claude_export_import_items
claude_export_import_messages
claude_import_provenance
claude_import_receipts
nanfeng_knowledge_export_import_tasks
nanfeng_knowledge_export_import_items
nanfeng_knowledge_export_import_messages
nanfeng_knowledge_import_provenance
nanfeng_knowledge_import_receipts
p6k_zip_import_tasks
p6k_zip_asset_recovery_jobs
p6k_zip_import_items
p6k_zip_import_messages
p6k_zip_asset_candidates
p6k_zip_asset_link_receipts
p6k_zip_asset_catalog
p6k_zip_asset_occurrence
p6k_zip_asset_occurrence_receipt
p6k_zip_asset_link_provenance
p6k_zip_profile_candidates
p6k_zip_import_provenance
p6k_zip_import_message_provenance
p6k_zip_import_receipts
p6k_import_identity_ledger
p6k_import_batch_receipts
p6k_import_identity_migration_state
third_party_profile_personalization_settings
p6k_profile_import_provenance
p6k_profile_import_receipts
pdf_text_import_tasks
pdf_text_import_pages
pdf_text_import_items
web_text_snapshot_tasks
web_text_snapshot_items
offline_eval_runs
offline_eval_case_results
offline_eval_assertions
offline_eval_human_scores
memories
memory_revisions
memory_intents
memory_conflicts
sync_account_metadata
sync_intents
sync_jobs
sync_job_receipts
manual_conversation_sync_state
cloud_conversation_presentation
agent_runs
agent_steps
agent_events
agent_checkpoints
agent_side_effect_receipts
p9b_integration_sessions
p9b_integration_events
p9b_integration_receipts
workspace_exchange_v2_restore_receipts
workspace_exchange_v2_restore_provenance
workspace_exchange_v2_restore_settings
scheduled_monitor_tasks
scheduled_monitor_runs
reminder_draft_generation_records
conversation_title_generation_records""".trim().split(Regex("\\s+"))
    private val edges = listOf(
        Edge("message_nodes","id","local_exact_reuse_entries","responseMessageId"),
        Edge("generated_candidates","invocationId","invocation_records","id"),
        Edge("knowledge_items","candidateId","generated_candidates","id"),
        Edge("knowledge_items","id","knowledge_relationships","fromKnowledgeId"),
        Edge("knowledge_items","id","knowledge_relationships","toKnowledgeId"),
        Edge("conversations","id","conversation_real_text_executions","conversationId"),
        Edge("conversations","id","usage_ledger_entries","conversationId"),
        Edge("conversations","id","local_search_index","conversationId"),
        Edge("conversations","id","ai_runtime_events","conversationId"),
        Edge("conversations","id","conversation_runtime_states","conversationId"),
        Edge("conversations","id","normal_chat_send_attempts","conversationId"),
        Edge("conversations","id","cloud_response_model_usages","conversationId"),
        Edge("conversations","id","compare_conversation_sessions","conversationId"),
        Edge("conversations","id","conversation_attempt_lineages","conversationId"),
        Edge("conversations","id","debug_call_log","conversationId"),
        Edge("conversations","id","conversation_management_intents","conversationId"),
        Edge("conversations","id","manual_conversation_sync_state","conversationId"),
        Edge("conversations","id","cloud_conversation_presentation","conversationId"),
        Edge("conversations","id","chatgpt_import_provenance","conversationId"),
        Edge("conversations","id","chatgpt_import_receipts","conversationId"),
        Edge("conversations","id","claude_import_provenance","conversationId"),
        Edge("conversations","id","claude_import_receipts","conversationId"),
        Edge("conversations","id","nanfeng_knowledge_import_provenance","conversationId"),
        Edge("conversations","id","nanfeng_knowledge_import_receipts","conversationId"),
        Edge("conversations","id","p6k_zip_asset_link_receipts","conversationId"),
        Edge("conversations","id","p6k_zip_asset_occurrence_receipt","conversationId"),
        Edge("conversations","id","p6k_zip_asset_link_provenance","conversationId"),
        Edge("conversations","id","p6k_zip_import_provenance","conversationId"),
        Edge("conversations","id","p6k_zip_import_message_provenance","conversationId"),
        Edge("conversations","id","p6k_zip_import_receipts","conversationId"),
        Edge("conversations","id","scheduled_monitor_tasks","sourceConversationId"),
        Edge("conversations","id","reminder_draft_generation_records","sourceConversationId"),
        Edge("conversations","id","conversation_title_generation_records","sourceConversationId"),
        Edge("message_nodes","id","assistant_response_model_attributions","assistantMessageId"),
        Edge("normal_chat_send_attempts","attemptId","resumable_attachment_uploads","normalChatAttemptId"),
        Edge("conversations","id","p6k_import_identity_ledger","localConversationId"),
        Edge("conversations","id","p6k_zip_asset_candidates","linkedConversationId"),
        Edge("knowledge_items","id","knowledge_item_tags","knowledgeId"),
        Edge("knowledge_items","id","knowledge_revisions","knowledgeId"),
        Edge("knowledge_revisions","id","knowledge_revision_tags","revisionId"),
        Edge("memories","id","memory_intents","memoryId"),
        Edge("knowledge_relationships","id","knowledge_relationship_revisions","relationshipId"),
        Edge("knowledge_relationships","id","knowledge_relationship_intents","relationshipId"),
        Edge("projects","id","project_intents","projectId"),
        Edge("compare_conversation_sessions","sessionId","compare_conversation_branches","sessionId"),
        Edge("compare_conversation_sessions","sessionId","compare_branch_execution_receipts","sessionId"),
        Edge("compare_conversation_sessions","sessionId","compare_branch_runtime_states","sessionId"),
        Edge("compare_conversation_sessions","sessionId","compare_branch_runtime_events","sessionId"),
        Edge("compare_conversation_sessions","sessionId","compare_branch_terminal_intents","sessionId"),
        Edge("compare_conversation_sessions","sessionId","compare_branch_follow_up_intents","sessionId"),
        Edge("compare_conversation_sessions","sessionId","compare_branch_adoption_intents","sessionId"),
        Edge("compare_conversation_sessions","sessionId","compare_synthesis_intents","sessionId"),
        Edge("conversations","id","chatgpt_export_import_items","conversationId"),
        Edge("chatgpt_export_import_items","taskId,id","chatgpt_export_import_messages","taskId,itemId"),
        Edge("conversations","id","claude_export_import_items","conversationId"),
        Edge("claude_export_import_items","taskId,id","claude_export_import_messages","taskId,itemId"),
        Edge("conversations","id","nanfeng_knowledge_export_import_items","conversationId"),
        Edge("nanfeng_knowledge_export_import_items","taskId,id","nanfeng_knowledge_export_import_messages","taskId,itemId"),
        Edge("conversations","id","p6k_zip_import_items","conversationId"),
        Edge("p6k_zip_import_items","taskId,id","p6k_zip_import_messages","taskId,itemId"),
        Edge("knowledge_items","id","markdown_import_items","knowledgeId"),
        Edge("knowledge_items","id","json_knowledge_import_items","knowledgeId"),
        Edge("knowledge_items","id","pdf_text_import_items","knowledgeId"),
        Edge("knowledge_items","id","web_text_snapshot_items","knowledgeId"),
        Edge("message_nodes","invocationId","invocation_records","id"),
        Edge("conversation_attempt_lineages","invocationId","invocation_records","id"),
        Edge("conversation_real_text_executions","invocationId","invocation_records","id"),
        Edge("knowledge_items","invocationId","invocation_records","id")
    )
    private val references = listOf(
        Edge("p6k_zip_asset_catalog","attachmentId","private_attachment_assets","attachmentId"),
        Edge("workspace_exchange_v2_restore_provenance","packageHash","workspace_exchange_v2_restore_receipts","packageHash"),
        Edge("generated_candidates","draftId","capture_drafts","id"),
        Edge("capture_drafts","id","capture_draft_evidence","draftId"),
        Edge("capture_drafts","id","capture_draft_attachments","draftId"),
        Edge("capture_draft_attachments","attachmentId","private_attachment_assets","attachmentId"),
        Edge("private_attachment_assets","attachmentId","glm_ocr_tasks","sourceAttachmentId"),
        Edge("p6k_zip_import_tasks","id","p6k_import_batch_receipts","taskId"),
        Edge("p6k_zip_import_tasks","id","p6k_zip_asset_recovery_jobs","taskId"),
        Edge("p6k_zip_import_items","taskId,sourceConversationId","p6k_zip_asset_occurrence","taskId,sourceConversationId"),
        Edge("p6k_zip_asset_occurrence","taskId,entryName","p6k_zip_asset_catalog","taskId,entryName"),
        Edge("knowledge_relationships","fromKnowledgeId","knowledge_items","id"),
        Edge("knowledge_relationships","toKnowledgeId","knowledge_items","id"),
        Edge("message_content_blocks","attachmentId","private_attachment_assets","attachmentId"),
        Edge("conversation_draft_attachments","attachmentId","private_attachment_assets","attachmentId"),
        Edge("knowledge_attachments","attachmentId","private_attachment_assets","attachmentId"),
        Edge("resumable_attachment_uploads","attachmentId","private_attachment_assets","attachmentId"),
        Edge("knowledge_item_tags","tag","knowledge_tags","name"),
        Edge("knowledge_revision_tags","tag","knowledge_tags","name"),
        Edge("chatgpt_export_import_items","taskId","chatgpt_export_import_tasks","id"),
        Edge("claude_export_import_items","taskId","claude_export_import_tasks","id"),
        Edge("nanfeng_knowledge_export_import_items","taskId","nanfeng_knowledge_export_import_tasks","id"),
        Edge("p6k_zip_import_items","taskId","p6k_zip_import_tasks","id"),
        Edge("markdown_import_items","taskId","markdown_import_tasks","id"),
        Edge("json_knowledge_import_items","taskId","json_knowledge_import_tasks","id"),
        Edge("pdf_text_import_items","taskId","pdf_text_import_tasks","id"),
        Edge("web_text_snapshot_items","taskId","web_text_snapshot_tasks","id")
    )
    private fun quote(value: String) = "\"" + value.replace("\"", "\"\"") + "\""
    private fun selected(table: String) = quote("_work_" + table)
    private fun owned(table: String) = quote("_owned_" + table)
    private fun add(db: SQLiteDatabase, edge: Edge, target: (String) -> String, source: (String) -> String): Int {
        val equals = edge.parentColumns.split(',').zip(edge.childColumns.split(',')).joinToString(" AND ") { (p,c) -> "p.${quote(p)}=c.${quote(c)}" }
        db.execSQL("INSERT OR IGNORE INTO ${target(edge.child)} SELECT c.rowid FROM main.${quote(edge.child)} c JOIN main.${quote(edge.parent)} p ON $equals JOIN ${source(edge.parent)} s ON s.id=p.rowid")
        return db.rawQuery("SELECT changes()",null).use { it.moveToFirst(); it.getInt(0) }
    }
    fun prepare(db: SQLiteDatabase) {
        val actual = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT IN ('android_metadata','room_master_table','sqlite_sequence') AND name NOT LIKE 'sqlite_%'",null).use { c -> buildSet { while(c.moveToNext()) add(c.getString(0)) } }
        require(actual == tables.toSet()) { "旧数据库结构与区域迁移版本不一致，原数据未改动。" }
        for (table in tables) {
            db.execSQL("CREATE TEMP TABLE ${owned(table)} (id INTEGER PRIMARY KEY)")
            db.execSQL("CREATE TEMP TABLE ${selected(table)} (id INTEGER PRIMARY KEY)")
        }
        val roots = mapOf(
            "conversations" to "surface='WORK' OR projectId IS NOT NULL",
            "projects" to "1",
            "knowledge_items" to "id IN (SELECT knowledgeId FROM knowledge_project_scopes)",
            "memories" to "projectId IS NOT NULL OR conversationId IN (SELECT id FROM conversations WHERE surface='WORK' OR projectId IS NOT NULL)",
            "memory_conflicts" to "projectId IS NOT NULL OR conversationId IN (SELECT id FROM conversations WHERE surface='WORK' OR projectId IS NOT NULL)",
            "knowledge_relationships" to "projectId IS NOT NULL"
        )
        for ((table,predicate) in roots) db.execSQL("INSERT INTO ${owned(table)} SELECT rowid FROM ${quote(table)} WHERE $predicate")
        val foreign = buildList {
            for (table in tables) db.rawQuery("PRAGMA foreign_key_list(${quote(table)})",null).use { c ->
                val rows = mutableMapOf<Int,MutableList<List<String>>>()
                while(c.moveToNext()) rows.getOrPut(c.getInt(0)) { mutableListOf() }.add(listOf(c.getString(2),c.getString(4),c.getString(3)))
                for (parts in rows.values) add(Edge(parts.first()[0],parts.joinToString(",") { it[1] },table,parts.joinToString(",") { it[2] }))
            }
        }
        // Real FK ownership edges plus reviewed semantic edges. Byte assets are dependencies.
        do { var changed=0; for (edge in foreign.filter { it.parent != "private_attachment_assets" } + edges) changed += add(db,edge,::owned,::owned) } while(changed > 0)
        for ((kind,table) in mapOf("projects" to "projects","conversations" to "conversations","knowledge" to "knowledge_items","memory" to "memories","relations" to "knowledge_relationships")) {
            db.execSQL("INSERT OR IGNORE INTO ${owned("workspace_exchange_v2_restore_provenance")} SELECT rowid FROM workspace_exchange_v2_restore_provenance WHERE ownerKind='$kind' AND ownerId IN (SELECT id FROM $table WHERE rowid IN (SELECT id FROM ${owned(table)}))")
        }
        for (table in tables) db.execSQL("INSERT INTO ${selected(table)} SELECT id FROM ${owned(table)}")
        val requiredParents = foreign.map { Edge(it.child,it.childColumns,it.parent,it.parentColumns) } + references
        do { var changed=0; for (edge in requiredParents) changed += add(db,edge,::selected,::selected) } while(changed > 0)
    }
    fun prune(db: SQLiteDatabase) {
        for (table in tables) {
            db.execSQL("DELETE FROM work.${quote(table)} WHERE rowid NOT IN (SELECT id FROM ${selected(table)})")
            db.execSQL("DELETE FROM main.${quote(table)} WHERE rowid IN (SELECT id FROM ${owned(table)})")
        }
        db.execSQL("UPDATE work.conversations SET surface='WORK'")
        for (schema in listOf("main","work")) db.rawQuery("PRAGMA $schema.foreign_key_check",null).use { require(!it.moveToFirst()) { "迁移关联校验失败，原数据未改动。" } }
    }
}
