pub mod conversation_real_text_execution_v1;
pub mod desktop_compare_credentials_v1;
pub mod desktop_compare_execution_v1;
pub mod dual_path_contract_v1;
pub mod local_exact_reuse_v1;
pub mod p6g_model_selection;
pub mod p7c_remote_gateway_v1;
pub mod p7d_sync_coordinator_v1;
pub mod p7e_isolated_workspace_v1;
pub mod p8_agent_ledger_v1;
pub mod p9b_integration_contract_v1;
pub mod sync_state_v1;
pub mod sync_v1;
pub mod usage_ledger_v1;

use crate::p6g_model_selection::{
    self as p6g, CatalogCandidate, CatalogSnapshot, ConversationOverride, GlobalDefault,
    RouteDecision, RouteMetadata, RouteRequest,
};
use base64::{engine::general_purpose::STANDARD as BASE64, Engine as _};
use image::{ImageFormat, ImageReader};
use lopdf::Document;
use rusqlite::{params, Connection, OptionalExtension, Transaction};
use serde::{Deserialize, Serialize};
use serde_json::{json, Map, Value};
use sha2::{Digest, Sha256};
use std::{
    collections::{BTreeMap, BTreeSet},
    fs,
    io::{Cursor, Read, Write},
    path::{Path, PathBuf},
    sync::Mutex,
};
use tauri::{Manager, State};
use zip::{write::SimpleFileOptions, CompressionMethod, ZipArchive, ZipWriter};

const MAX_PACKAGE_BYTES: u64 = 128 * 1024 * 1024;
const MAX_CONVERSATION_ATTACHMENT_BYTES: u64 = 20 * 1024 * 1024;
const MAX_CONVERSATION_ATTACHMENT_COUNT: usize = 4;
const MAX_CONVERSATION_IMAGE_PIXELS: u64 = 40_000_000;
const MAX_DESKTOP_IMAGE_THUMBNAIL_EDGE: u32 = 360;
const MAX_INERT_TEXT_PREVIEW_BYTES: usize = 128 * 1024;
const ATTACHMENT_RETENTION_MILLIS: i64 = 24 * 60 * 60 * 1000;
const TEMPORARY_CONVERSATION_SCHEMA_VERSION: u32 = 1;
const TEMPORARY_CONVERSATION_MAX_TEXT_CHARS: usize = 12_000;
const P6E_ACCEPTANCE_ENV: &str = "NANFENG_AI_P6E_ACCEPTANCE";
const P6E_ACCEPTANCE_ROOT_NAME: &str = "nanfeng-ai-p6e-acceptance";
const P6H_ACCEPTANCE_ENV: &str = "NANFENG_AI_P6H_ACCEPTANCE";
const P6H_ACCEPTANCE_ROOT_NAME: &str = "nanfeng-ai-p6h-acceptance-20260814";
const P6I_ACCEPTANCE_ENV: &str = "NANFENG_AI_P6I_ACCEPTANCE";
const P6I_ACCEPTANCE_ROOT_NAME: &str = "nanfeng-ai-p6i-acceptance-20260815";
const P6J_ACCEPTANCE_ENV: &str = "NANFENG_AI_P6J_ACCEPTANCE";
const P6J_ACCEPTANCE_ROOT_NAME: &str = "nanfeng-ai-p6j-acceptance-20260815";
// FB-P6-050 is a UI-only acceptance run.  It must never reuse a normal Desktop
// app-data directory just because the temporary bundle was copied with a new ID.
const FB_P6_050_ACCEPTANCE_ENV: &str = "NANFENG_AI_FB_P6_050_ACCEPTANCE";
const FB_P6_050_ACCEPTANCE_ROOT_NAME: &str = "nanfeng-ai-fb-p6-050-acceptance-20260815";
const P6E_ACCEPTANCE_RECEIPT_FILE: &str = "p6e-acceptance-receipt.json";
const CHATGPT_EXPORT_MAX_BYTES: usize = 32 * 1024 * 1024;
const CHATGPT_EXPORT_MAX_CONVERSATIONS: usize = 200;
const CHATGPT_EXPORT_MAX_MAPPING_NODES: usize = 2_000;
const CHATGPT_EXPORT_MAX_TEXT_CODEPOINTS: usize = 120_000;
// P6-I owns a separate parser, task queue and SQLite tables. Keep these limits independent from
// P6-H even though their values intentionally match the cross-platform contract.
const CLAUDE_EXPORT_MAX_BYTES: usize = 32 * 1024 * 1024;
const CLAUDE_EXPORT_MAX_CONVERSATIONS: usize = 200;
const CLAUDE_EXPORT_MAX_MESSAGES: usize = 2_000;
const CLAUDE_EXPORT_MAX_TEXT_CODEPOINTS: usize = 120_000;
// P6-K deliberately has the same bounded archive envelope as the Android adapter.  The
// individual JSON entry cap is what keeps parser memory bounded; package hashing is streamed.
const P6K_ZIP_MAX_ARCHIVE_BYTES: u64 = 2 * 1024 * 1024 * 1024;
const P6K_ZIP_MAX_ENTRIES: usize = 5_000;
const P6K_ZIP_MAX_ENTRY_BYTES: u64 = 256 * 1024 * 1024;
const P6K_ZIP_MAX_TOTAL_UNCOMPRESSED_BYTES: u64 = 4 * 1024 * 1024 * 1024;
const P6K_ZIP_MAX_COMPRESSION_RATIO: u64 = 100;
// These limits are versioned for the P6-K ZIP adapter only.  They were set from the
// user-authorized Claude archive's safe structural envelope (282 conversations / 38.4 MiB),
// while the archive and entry inventory limits above remain independently enforced.
const P6K_CLAUDE_EXPORT_MAX_BYTES: usize = 64 * 1024 * 1024;
const P6K_CLAUDE_EXPORT_MAX_CONVERSATIONS: usize = 1_000;
const P6K_CLAUDE_EXPORT_MAX_MESSAGES_PER_CONVERSATION: usize = 2_000;
const P6K_ZIP_MAX_CANDIDATES: usize = 2_000;
const P6K_ZIP_MAX_MESSAGES: usize = 100_000;
const P6K_GRAPH_MERGED: &str = "P6K_GRAPH_MERGED";
const MAX_ENTRIES: usize = 100_000;
const PAYLOADS: [&str; 6] = [
    "payload/projects.json",
    "payload/conversations.json",
    "payload/knowledge.json",
    "payload/memory.json",
    "payload/relations.json",
    "payload/settings.safe.json",
];

#[derive(Debug, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
struct WorkspaceSummary {
    id: String,
    title: String,
    semantic_hash: String,
    package_hash: String,
    project_count: usize,
    conversation_count: usize,
    knowledge_count: usize,
    memory_count: usize,
    relation_count: usize,
    asset_count: usize,
    asset_byte_count: u64,
    high_sensitive: bool,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct PreflightReceipt {
    staging_id: String,
    package_hash: String,
    byte_count: u64,
    semantic_hash: String,
    origin: String,
    sensitivity: String,
    project_count: usize,
    conversation_count: usize,
    message_count: usize,
    knowledge_count: usize,
    memory_count: usize,
    relation_count: usize,
    asset_count: usize,
    asset_byte_count: u64,
    high_sensitive: bool,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct WorkspaceProjection {
    summary: WorkspaceSummary,
    exchange: Value,
}

/** P6-H's inert Desktop mapping. It carries no path, URI, credential or executable content. */
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct ChatGptExportMessage {
    source_id: String,
    parent_source_id: Option<String>,
    sibling_position: usize,
    role: String,
    text: String,
    created_at_ms: i64,
    imported_model: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct ChatGptExportCandidate {
    source_conversation_id: String,
    title: String,
    created_at_ms: i64,
    updated_at_ms: i64,
    messages: Vec<ChatGptExportMessage>,
    content_hash: String,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct ChatGptImportTaskProjection {
    id: String,
    status: String,
    display_name: String,
    mime_type: String,
    byte_count: u64,
    package_hash: String,
    candidates: Vec<Result<ChatGptExportCandidate, String>>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct ChatGptImportItemProjection {
    id: String,
    ordinal: i64,
    title: Option<String>,
    status: String,
    failure: Option<String>,
    conversation_id: Option<String>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct ChatGptImportTaskReadProjection {
    id: String,
    status: String,
    items: Vec<ChatGptImportItemProjection>,
}

/** P6-K exposes only recovery-safe task state. Conversation text continues to be owned by
 * workspace_exchange, never by this projection or a parallel conversation schema. */
#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct P6kZipImportItemProjection { id: String, ordinal: i64, status: String, failure: Option<String>, conversation_id: Option<String> }

/// K8 intentionally exposes ordinal/MIME/size/status only: an archive entry name and its bytes
/// remain private until the user explicitly associates this ordinal with an imported message.
#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct P6kManualAssetProjection { ordinal: i64, mime_type: String, byte_count: u64, status: String }

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct P6kZipImportTaskProjection {
    id: String,
    provider: String,
    status: String,
    display_name: String,
    byte_count: u64,
    failure: Option<String>,
    entry_count: usize,
    imported_count: usize,
    failed_count: usize,
    skipped_count: usize,
    unmapped_asset_count: usize,
    profile_status: String,
    profile_mapped_field_count: usize,
    items: Vec<P6kZipImportItemProjection>,
    manual_assets: Vec<P6kManualAssetProjection>,
}

/** Settings-facing K6 projection: never returns third-party profile values to the shell. */
#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct P6kProfilePersonalizationSettingsStatusProjection { applied: bool, mapped_field_count: usize }

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct P6kZipImportSelectionArgs { workspace_id: String, provider: String, selected_path: String }

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct P6kManualAssetLinkArgs { task_id: String, asset_ordinal: i64, workspace_id: String, conversation_id: String, message_id: String }

type ChatGptParsedNode<'a> = (&'a Map<String, Value>, Option<String>, Vec<String>);

/** P6-I keeps its source schema and persisted tables separate from P6-H. */
type ClaudeExportMessage = ChatGptExportMessage;
type ClaudeExportCandidate = ChatGptExportCandidate;
type ClaudeImportTaskProjection = ChatGptImportTaskProjection;
type ClaudeImportItemProjection = ChatGptImportItemProjection;
type ClaudeImportTaskReadProjection = ChatGptImportTaskReadProjection;
/** P6-J deliberately reuses only inert projection structs; its parser and SQLite tables are separate. */
type NanfengKnowledgeExportMessage = ChatGptExportMessage;
type NanfengKnowledgeExportCandidate = ChatGptExportCandidate;

/** P6-F2-A returns only a sanitized local projection, never an asset path, URI or raw exchange row. */
#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct DesktopLocalSearchHit {
    conversation_id: String,
    message_id: Option<String>,
    title: String,
    snippet: String,
    content_kind: String,
    timestamp: String,
    title_match: bool,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct ImportArgs {
    staging_id: String,
    workspace_title: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DomainMutationArgs {
    intent_id: String,
    workspace_id: String,
    entity: String,
    action: String,
    object_id: Option<String>,
    expected_revision: Option<u64>,
    fields: Value,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DesktopAttachmentImportArgs {
    workspace_id: String,
    selected_path: String,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
struct ChatGptImportItemActionArgs {
    workspace_id: String,
    task_id: String,
    item_id: String,
}

type ClaudeImportItemActionArgs = ChatGptImportItemActionArgs;

/// The UI can name only a workspace-owned attachment ID, never a private asset path.
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DesktopImagePreviewArgs {
    workspace_id: String,
    attachment_id: String,
    full_size: bool,
}

/// The web UI can name a workspace-owned PDF attachment and an optional 1-based page only.
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DesktopPdfPreviewArgs {
    workspace_id: String,
    attachment_id: String,
    page_number: Option<u32>,
}

/// The UI may name only a workspace-owned MP4 attachment and an optional local resume position.
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DesktopVideoPreviewArgs {
    workspace_id: String,
    attachment_id: String,
    position_millis: Option<u64>,
}

/// The UI can only name an owned audio attachment and an optional local resume position.
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DesktopAudioPreviewArgs {
    workspace_id: String,
    attachment_id: String,
    position_millis: Option<u64>,
}

/// Inert text preview: no path, no bytes and no executable content crosses this boundary.
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DesktopTextPreviewArgs {
    workspace_id: String,
    attachment_id: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DesktopTemporaryAttachmentImportArgs {
    temporary_id: String,
    selected_path: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct TemporaryConversationUpdateArgs {
    temporary_id: String,
    draft: String,
    model_override_id: Option<String>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct TemporaryConversationAppendArgs {
    temporary_id: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct TemporaryConversationClearArgs {
    temporary_id: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct TemporaryConversationRemoveAttachmentArgs {
    temporary_id: String,
    attachment_id: String,
}

/// Separate from exchange/Conversation JSON.  It intentionally has no workspace, project,
/// provider, URI/path, runtime, cache or export fields.
#[derive(Debug, Serialize, Deserialize, Clone)]
#[serde(rename_all = "camelCase")]
struct DesktopTemporaryConversationRecovery {
    schema_version: u32,
    temporary_id: String,
    created_at_ms: i64,
    updated_at_ms: i64,
    draft: String,
    messages: Vec<DesktopTemporaryMessage>,
    model_override_id: Option<String>,
    attachments: Vec<DesktopAttachmentMetadata>,
    draft_attachment_ids: Vec<String>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
#[serde(rename_all = "camelCase")]
struct DesktopTemporaryMessage {
    role: String,
    text: String,
    attachment_ids: Vec<String>,
    created_at_ms: i64,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
#[serde(rename_all = "camelCase")]
struct DesktopAttachmentMetadata {
    id: String,
    mime_type: String,
    display_name: String,
    byte_count: u64,
    sha256: String,
}

/// Bounded display bytes for a thumbnail or a user-requested original; no path, URI or source
/// selection detail crosses the Tauri boundary.
#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct DesktopImagePreview {
    attachment_id: String,
    mime_type: String,
    display_name: String,
    byte_count: u64,
    width: u32,
    height: u32,
    data_url: String,
    is_thumbnail: bool,
}

/// Sanitized PDF display payload. It intentionally contains no filesystem path, URI, text
/// extraction, action metadata or arbitrary-reader capability.
#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct DesktopPdfPreview {
    attachment_id: String,
    display_name: String,
    byte_count: u64,
    page_number: u32,
    page_count: u32,
    data_url: String,
}

/// Display payload for a verified local MP4. No path, URI, source metadata or provider data crosses IPC.
#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct DesktopVideoPreview {
    attachment_id: String,
    display_name: String,
    byte_count: u64,
    duration_millis: u64,
    position_millis: u64,
    data_url: String,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct DesktopAudioPreview {
    attachment_id: String,
    display_name: String,
    mime_type: String,
    byte_count: u64,
    position_millis: u64,
    data_url: String,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct DesktopTextPreview {
    attachment_id: String,
    display_name: String,
    mime_type: String,
    byte_count: u64,
    text: String,
    truncated: bool,
}

/// Acceptance-only aggregate. It has no clock, path, content or user-data inputs and is persisted
/// only below the dedicated `/tmp` private root selected by the explicit process marker.
#[derive(Debug, Serialize, Deserialize, Clone, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
struct P6eTemporaryMaintenanceAcceptanceReceipt {
    retained_at_23h59: bool,
    removed_at_24h: bool,
    attachment_removed_at_24h: bool,
    message_present_before_expiry: bool,
    model_override_present_before_expiry: bool,
    ordinary_surfaces_clean: bool,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct P6eAcceptanceStatus {
    enabled: bool,
    receipt: Option<P6eTemporaryMaintenanceAcceptanceReceipt>,
}

/// Safe, aggregate-only result for the private attachment owner's startup maintenance.  It never
/// exposes a source URI, an app-private path, or attachment contents.
#[derive(Debug, PartialEq, Eq)]
struct AttachmentMaintenanceReceipt {
    referenced_retained: usize,
    fresh_orphans_retained: usize,
    expired_orphans_removed: usize,
    expired_staging_removed: usize,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct HistoryArgs {
    intent_id: String,
    workspace_id: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct ModelMetadataArgs {
    intent_id: String,
    workspace_id: String,
    expected_revision: u64,
    provider_id: String,
    model_id: String,
    metadata: Value,
}

/// P6-G carries only a reviewed local catalog snapshot. These arguments intentionally have no
/// credential, endpoint, prompt, request body, runtime or invocation fields.
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct P6gCatalogCandidateArgs {
    expected_revision: u64,
    catalog_version: String,
    policy_version: u64,
    candidate: CatalogCandidate,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct P6gCatalogRemoveArgs {
    expected_revision: u64,
    catalog_version: String,
    policy_version: u64,
    model_id: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct P6gGlobalDefaultArgs {
    expected_revision: u64,
    tier: Option<p6g::ModelTier>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct P6gConversationOverrideArgs {
    workspace_id: String,
    conversation_id: String,
    expected_revision: u64,
    model_id: Option<String>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct P6gEvaluateArgs {
    workspace_id: String,
    conversation_id: String,
    request: RouteRequest,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct P6gMutationReceipt {
    revision: u64,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct P6gCatalogProjection {
    revision: u64,
    snapshot: CatalogSnapshot,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct P6gSelectionProjection {
    catalog: P6gCatalogProjection,
    global_default: GlobalDefault,
    conversation_override: ConversationOverride,
    last_route: Option<RouteMetadata>,
}

#[derive(Debug, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
struct MutationReceipt {
    intent_id: String,
    workspace_id: String,
    object_id: String,
    revision: u64,
    semantic_hash: String,
    replayed: bool,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct WorkbenchHistory {
    can_undo: bool,
    can_redo: bool,
    recycle_bin: Vec<RecycleBinEntry>,
    model_metadata: Vec<Value>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct RecycleBinEntry {
    entity: String,
    id: String,
    title: String,
    revision: u64,
}

struct DesktopWorkspaceStore {
    root: PathBuf,
    database: PathBuf,
    /// P7-E can only create its own named, isolated sync workspace.  It is intentionally not
    /// represented by P6's `workspaces` table and has no Tauri command until account/recovery
    /// ownership is explicitly connected in a later authorized phase.
    #[allow(
        dead_code,
        reason = "P7-E remains owner-internal until an authorized account/recovery entrypoint exists"
    )]
    p7e_isolated_store: p7e_isolated_workspace_v1::IsolatedWorkspaceStore,
    /// P8-A opens only a private, local ledger. P8-B may read aggregate status internally, but no
    /// Tauri command, UI, production registry or executor reaches it.
    #[allow(
        dead_code,
        reason = "P8-A is contract-tested only; UI/production tool binding is not authorized"
    )]
    p8_agent_ledger: p8_agent_ledger_v1::AgentLedgerStore,
}

struct AppState {
    store: Mutex<DesktopWorkspaceStore>,
    p6e_acceptance_enabled: bool,
    p6h_acceptance_enabled: bool,
}

struct PreflightedPackage {
    bytes: Vec<u8>,
    exchange: Value,
    assets: BTreeMap<String, Vec<u8>>,
    receipt: PreflightReceipt,
}

fn sha256(bytes: &[u8]) -> String {
    format!("{:x}", Sha256::digest(bytes))
}

fn sha256_file(path: &Path) -> Result<String, String> {
    let mut file = fs::File::open(path).map_err(|_| json_error("ZIP 私有副本不可读"))?;
    let mut digest = Sha256::new();
    let mut buffer = [0u8; 64 * 1024];
    loop {
        let read = file.read(&mut buffer).map_err(|_| json_error("ZIP 私有副本不可读"))?;
        if read == 0 { break; }
        digest.update(&buffer[..read]);
    }
    Ok(format!("{:x}", digest.finalize()))
}

fn p6k_inventory(file: &Path) -> Result<Vec<(String, u64, u64, String)>, &'static str> {
    let input = fs::File::open(file).map_err(|_| "ZIP 私有副本不可读")?;
    let mut zip = ZipArchive::new(input).map_err(|_| "ZIP 格式无效")?;
    if zip.len() > P6K_ZIP_MAX_ENTRIES { return Err("ZIP 条目数量超限"); }
    let mut names = BTreeSet::new(); let mut total = 0u64; let mut entries = Vec::new();
    for index in 0..zip.len() {
        let entry = zip.by_index(index).map_err(|_| "ZIP 条目不可读")?;
        if entry.is_dir() { continue; }
        let name = entry.name().replace('\\', "/");
        if name.is_empty() || name.starts_with('/') || (name.len() >= 3 && name.as_bytes()[1] == b':' && name.as_bytes()[2] == b'/') || name.split('/').any(|part| part.is_empty() || part == "..") { return Err("ZIP 包含不安全路径"); }
        let normalized = name.to_ascii_lowercase(); if !names.insert(normalized) { return Err("ZIP 包含重复条目"); }
        let size = entry.size(); let compressed = entry.compressed_size();
        if size > P6K_ZIP_MAX_ENTRY_BYTES { return Err("ZIP 单文件超限"); }
        if !matches!(entry.compression(), CompressionMethod::Stored | CompressionMethod::Deflated) || (size > 0 && compressed == 0) || (compressed > 0 && size / compressed > P6K_ZIP_MAX_COMPRESSION_RATIO) { return Err("ZIP 压缩方式或压缩比不安全"); }
        total = total.checked_add(size).ok_or("ZIP 解压总量超限")?; if total > P6K_ZIP_MAX_TOTAL_UNCOMPRESSED_BYTES { return Err("ZIP 解压总量超限"); }
        entries.push((name.clone(), size, compressed, p6k_mime(&name).into()));
    }
    Ok(entries)
}

fn p6k_mime(name: &str) -> &'static str { match name.rsplit('.').next().unwrap_or("").to_ascii_lowercase().as_str() { "json" => "application/json", "pdf" => "application/pdf", "png" => "image/png", "jpg" | "jpeg" => "image/jpeg", "webp" => "image/webp", "mp4" => "video/mp4", "mp3" => "audio/mpeg", "wav" => "audio/wav", "txt" | "md" => "text/plain", _ => "application/octet-stream" } }

fn p6k_chatgpt_conversation_entry(name: &str) -> bool {
    name == "conversations.json" || name.strip_prefix("conversations-").and_then(|value| value.strip_suffix(".json")).is_some_and(|value| !value.is_empty() && value.bytes().all(|byte| byte.is_ascii_digit()))
}

/// K6 accepts exactly one synthetic/versioned profile envelope.  Provider exports with any other
/// profile-looking JSON are intentionally not guessed: the conversation import can still proceed.
#[derive(Clone, Debug, PartialEq)]
struct P6kProfilePersonalization { display_name: Option<String>, language: Option<String>, timezone: Option<String>, public_bio: Option<String>, custom_instructions: Option<String>, theme: Option<String>, notifications_enabled: Option<bool> }
impl P6kProfilePersonalization {
    fn field_count(&self) -> usize { [self.display_name.as_ref(),self.language.as_ref(),self.timezone.as_ref(),self.public_bio.as_ref(),self.custom_instructions.as_ref(),self.theme.as_ref()].into_iter().flatten().count() + usize::from(self.notifications_enabled.is_some()) }
    fn canonical_hash(&self) -> Result<String, String> { Ok(sha256(canonical_json(&json!({"displayName":self.display_name,"language":self.language,"timezone":self.timezone,"publicBio":self.public_bio,"customInstructions":self.custom_instructions,"theme":self.theme,"notificationsEnabled":self.notifications_enabled}))?.as_bytes())) }
}

fn p6k_profile_candidate(bytes: &[u8]) -> (String, usize, Option<P6kProfilePersonalization>) {
    let parsed = (|| -> Result<P6kProfilePersonalization, ()> {
        if bytes.len() > 16 * 1024 { return Err(()); }
        let root: Value = serde_json::from_slice(bytes).map_err(|_| ())?;
        let root = root.as_object().ok_or(())?;
        if root.keys().map(String::as_str).collect::<BTreeSet<_>>() != ["format", "version", "personalization"].into_iter().collect()
            || root.get("format").and_then(Value::as_str) != Some("nfai.third-party-profile-personalization")
            || root.get("version").and_then(Value::as_u64) != Some(1) { return Err(()); }
        let values = root.get("personalization").and_then(Value::as_object).ok_or(())?;
        let allowed: BTreeSet<&str> = ["displayName", "language", "timezone", "publicBio", "customInstructions", "theme", "notificationsEnabled"].into_iter().collect();
        if !values.keys().all(|key| allowed.contains(key.as_str())) { return Err(()); }
        let limits = [("displayName",180usize),("language",32),("timezone",64),("publicBio",1000),("customInstructions",4000),("theme",32)];
        let text = |key: &str, max: usize| -> Result<Option<String>, ()> { match values.get(key) { None => Ok(None), Some(value) => { let value=value.as_str().ok_or(())?; if value.trim().is_empty() || value.chars().count()>max { return Err(()); } Ok(Some(value.to_owned())) } } };
        let profile = P6kProfilePersonalization { display_name:text(limits[0].0,limits[0].1)?, language:text(limits[1].0,limits[1].1)?, timezone:text(limits[2].0,limits[2].1)?, public_bio:text(limits[3].0,limits[3].1)?, custom_instructions:text(limits[4].0,limits[4].1)?, theme:text(limits[5].0,limits[5].1)?, notifications_enabled:match values.get("notificationsEnabled") { None=>None, Some(value)=>Some(value.as_bool().ok_or(())?) } };
        Ok(profile)
    })();
    match parsed { Ok(value) if value.field_count()==0 => ("NO_SAFE_PROFILE_FIELDS".into(),0,None), Ok(value) => { let count=value.field_count(); ("MAPPED_PENDING_OWNER_COMMIT".into(),count,Some(value)) }, Err(()) => ("REJECTED_UNSAFE_PROFILE_SCHEMA".into(),0,None) }
}

type P6kZipCandidate = Result<ChatGptExportCandidate, String>;
type P6kZipAsset = (String, u64, String, String);
type P6kZipParseResult = (Vec<P6kZipCandidate>, Vec<P6kZipAsset>, String, usize, Option<P6kProfilePersonalization>);

fn p6k_zip_candidates(file: &Path, provider: &str) -> Result<P6kZipParseResult, String> {
    let input = fs::File::open(file).map_err(|_| json_error("ZIP 私有副本不可读"))?;
    let mut zip = ZipArchive::new(input).map_err(|_| json_error("ZIP 格式无效"))?;
    let mut selected = Vec::new();
    let mut assets = Vec::new();
    let mut profile = ("NO_SAFE_PROFILE_FIELDS".to_owned(), 0usize, None);
    for index in 0..zip.len() {
        let mut entry = zip.by_index(index).map_err(|_| json_error("ZIP 条目不可读"))?;
        if entry.is_dir() { continue; }
        let name = entry.name().replace('\\', "/");
        let selected_entry = match provider { "CHATGPT" => p6k_chatgpt_conversation_entry(&name), "CLAUDE" => name == "conversations.json", _ => false };
        if name == "profile-personalization-v1.json" {
            let mut bytes = Vec::with_capacity(entry.size() as usize);
            entry.read_to_end(&mut bytes).map_err(|_| json_error("ZIP profile 条目不可读"))?;
            profile = p6k_profile_candidate(&bytes);
        } else if selected_entry {
            let mut bytes = Vec::with_capacity(entry.size() as usize);
            entry.read_to_end(&mut bytes).map_err(|_| json_error("ZIP 会话条目不可读"))?;
            let parsed = match provider {
                "CHATGPT" => parse_p6k_chatgpt_export_with_limit(&bytes, P6K_ZIP_MAX_ENTRY_BYTES as usize),
                "CLAUDE" => parse_claude_export_with_limits(&bytes, P6K_CLAUDE_EXPORT_MAX_BYTES, P6K_CLAUDE_EXPORT_MAX_CONVERSATIONS, P6K_CLAUDE_EXPORT_MAX_MESSAGES_PER_CONVERSATION),
                _ => unreachable!(),
            }?;
            selected.extend(parsed);
            if selected.len() > P6K_ZIP_MAX_CANDIDATES { return Err(json_error("ZIP 会话候选总数超限")); }
        } else if !name.to_ascii_lowercase().ends_with(".json") {
            let mut digest = Sha256::new();
            let mut buffer = [0u8; 64 * 1024];
            loop { let read = entry.read(&mut buffer).map_err(|_| json_error("ZIP 媒体条目不可读"))?; if read == 0 { break; } digest.update(&buffer[..read]); }
            assets.push((name, entry.size(), p6k_mime(entry.name()).into(), format!("{:x}", digest.finalize())));
        }
    }
    if selected.is_empty() { return Err(json_error(match provider { "CHATGPT" => "ChatGPT ZIP 未找到受支持的 conversations.json 版本", "CLAUDE" => "Claude ZIP 未找到根 conversations.json 版本", _ => "ZIP 服务来源无效" })); }
    if provider == "CHATGPT" { selected = p6k_normalize_chatgpt_candidates(selected); }
    let message_count = selected.iter().filter_map(|candidate| candidate.as_ref().ok()).map(|candidate| candidate.messages.len()).sum::<usize>();
    if message_count > P6K_ZIP_MAX_MESSAGES { return Err(json_error("ZIP 消息总数超限")); }
    Ok((selected, assets, profile.0, profile.1, profile.2))
}

fn json_error(message: &str) -> String {
    format!("Desktop 本地数据错误：{message}")
}

fn require_string<'a>(object: &'a Map<String, Value>, key: &str) -> Result<&'a str, String> {
    object
        .get(key)
        .and_then(Value::as_str)
        .ok_or_else(|| json_error(&format!("{key} 缺失或不是文本")))
}

fn require_array<'a>(object: &'a Map<String, Value>, key: &str) -> Result<&'a Vec<Value>, String> {
    object
        .get(key)
        .and_then(Value::as_array)
        .ok_or_else(|| json_error(&format!("{key} 缺失或不是数组")))
}

fn is_stable_id(value: &str) -> bool {
    let bytes = value.as_bytes();
    (2..=64).contains(&bytes.len())
        && (bytes[0].is_ascii_lowercase() || bytes[0].is_ascii_digit())
        && bytes.iter().all(|byte| {
            byte.is_ascii_lowercase() || byte.is_ascii_digit() || *byte == b'_' || *byte == b'-'
        })
}

/// P6-E model overrides are opaque local identifiers, not entity IDs. Keep this in lockstep with
/// Android's `TemporaryConversationRecovery` contract; it never accepts a URI, path, Key or URL.
fn is_temporary_model_override_id(value: &str) -> bool {
    let bytes = value.as_bytes();
    (1..=160).contains(&bytes.len())
        && bytes
            .iter()
            .all(|byte| byte.is_ascii_alphanumeric() || matches!(*byte, b'.' | b'_' | b':' | b'-'))
}

fn is_sha256(value: &str) -> bool {
    value.len() == 64
        && value
            .bytes()
            .all(|byte| byte.is_ascii_hexdigit() && !byte.is_ascii_uppercase())
}

fn local_now() -> &'static str {
    // Exchange v1 requires UTC text. This local-only workbench deliberately does not ingest a
    // device identifier or any external clock source; precise wall-clock display is a later UX
    // concern, while revisions are the ordering authority.
    "2026-08-13T00:00:00Z"
}

fn local_now_millis() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|value| value.as_millis() as i64)
        .unwrap_or(0)
}

fn system_now_millis() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|duration| duration.as_millis().min(i64::MAX as u128) as i64)
        .unwrap_or_default()
}

/// Deterministic UTC rendering without consulting a network/device service; used to preserve
/// export epoch timestamps in ordinary Conversation presentation fields.
fn rfc3339_from_unix_millis(millis: i64) -> String {
    let days = millis.div_euclid(86_400_000);
    let within_day = millis.rem_euclid(86_400_000);
    let z = days + 719_468;
    let era = if z >= 0 { z } else { z - 146_096 } / 146_097;
    let doe = z - era * 146_097;
    let yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365;
    let mut year = yoe + era * 400;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let month_index = (5 * doy + 2) / 153;
    let day = doy - (153 * month_index + 2) / 5 + 1;
    let month = month_index + if month_index < 10 { 3 } else { -9 };
    year += if month <= 2 { 1 } else { 0 };
    let hour = within_day / 3_600_000;
    let minute = (within_day / 60_000) % 60;
    let second = (within_day / 1_000) % 60;
    let ms = within_day % 1_000;
    format!("{year:04}-{month:02}-{day:02}T{hour:02}:{minute:02}:{second:02}.{ms:03}Z")
}

fn require_short_text(
    value: &Value,
    field: &str,
    max: usize,
    allow_empty: bool,
) -> Result<String, String> {
    let text = value
        .as_str()
        .ok_or_else(|| json_error(&format!("{field} 必须是文本")))?
        .trim();
    if (!allow_empty && text.is_empty()) || text.chars().count() > max {
        return Err(json_error(&format!("{field} 长度无效")));
    }
    Ok(text.to_owned())
}

fn allowed_keys(fields: &Map<String, Value>, allowed: &[&str]) -> Result<(), String> {
    if fields.keys().any(|key| !allowed.contains(&key.as_str())) {
        return Err(json_error("领域写入包含未知或不安全字段"));
    }
    ensure_no_forbidden_keys(&Value::Object(fields.clone()))
}

fn entity_array(entity: &str) -> Result<&'static str, String> {
    match entity {
        "project" => Ok("projects"),
        "conversation" => Ok("conversations"),
        "knowledge" => Ok("knowledge"),
        "memory" => Ok("memory"),
        "relation" => Ok("relations"),
        _ => Err(json_error("不支持的领域对象")),
    }
}

fn is_deleted(entity: &str, value: &Map<String, Value>) -> bool {
    match entity {
        "project" | "conversation" => value.get("deleted").and_then(Value::as_bool) == Some(true),
        "knowledge" | "memory" => value.get("status").and_then(Value::as_str) == Some("DELETED"),
        "relation" => value.get("status").and_then(Value::as_str) == Some("REVOKED"),
        _ => true,
    }
}

fn revision_of(value: &Map<String, Value>) -> Result<u64, String> {
    value
        .get("revision")
        .and_then(Value::as_u64)
        .ok_or_else(|| json_error("对象 revision 无效"))
}

fn find_object_mut<'a>(
    root: &'a mut Map<String, Value>,
    entity: &str,
    id: &str,
) -> Result<&'a mut Map<String, Value>, String> {
    root.get_mut(entity_array(entity)?)
        .and_then(Value::as_array_mut)
        .ok_or_else(|| json_error("领域集合无效"))?
        .iter_mut()
        .find(|value| value.get("id").and_then(Value::as_str) == Some(id))
        .and_then(Value::as_object_mut)
        .ok_or_else(|| json_error("对象不存在"))
}

fn object_is_active(root: &Map<String, Value>, entity: &str, id: &str) -> bool {
    root.get(entity_array(entity).unwrap_or_default())
        .and_then(Value::as_array)
        .and_then(|values| {
            values
                .iter()
                .find(|value| value.get("id").and_then(Value::as_str) == Some(id))
        })
        .and_then(Value::as_object)
        .is_some_and(|value| !is_deleted(entity, value))
}

fn generated_id(entity: &str, intent_id: &str) -> String {
    let prefix = match entity {
        "project" => "project",
        "conversation" => "conversation",
        "knowledge" => "knowledge",
        "memory" => "memory",
        "relation" => "relation",
        _ => "local",
    };
    format!("{prefix}-{}", &intent_id[..intent_id.len().min(32)])
}

fn refresh_exchange_hash(exchange: &mut Value) -> Result<String, String> {
    let hash = semantic_hash(exchange)?;
    object(exchange, "exchange")?
        .get("export")
        .and_then(Value::as_object)
        .ok_or_else(|| json_error("export 无效"))?;
    exchange
        .get_mut("export")
        .and_then(Value::as_object_mut)
        .ok_or_else(|| json_error("export 无效"))?
        .insert("semanticHash".to_owned(), Value::String(hash.clone()));
    Ok(hash)
}

fn desktop_attachment_kind(
    bytes: &[u8],
    extension: Option<&str>,
) -> Option<(&'static str, &'static str)> {
    let ext = extension.unwrap_or_default().to_ascii_lowercase();
    if bytes.starts_with(&[0xff, 0xd8, 0xff]) && matches!(ext.as_str(), "jpg" | "jpeg") {
        return Some(("image/jpeg", ".jpg"));
    }
    if bytes.starts_with(b"\x89PNG\r\n\x1a\n") && ext == "png" {
        return Some(("image/png", ".png"));
    }
    if bytes.len() >= 12 && &bytes[..4] == b"RIFF" && &bytes[8..12] == b"WEBP" && ext == "webp" {
        return Some(("image/webp", ".webp"));
    }
    if bytes.starts_with(b"%PDF-") && ext == "pdf" {
        return Some(("application/pdf", ".pdf"));
    }
    // ISO-BMFF is content-addressed here.  The native dialog supplies only a user-selected
    // path; it deliberately does not send a caller-claimed MIME type, and a path extension is
    // not a stable security fact.  Restrict the accepted brands to video-capable MP4 families so
    // an audio-only ISO-BMFF file (for example M4A) cannot enter the video adapter.
    if is_mp4_video_iso_bmff(bytes) {
        return Some(("video/mp4", ".mp4"));
    }
    // P6-F2-E audio remains a closed, magic-verified local set.  M4A is explicitly
    // separated from video before the MP4 video-brand gate above.
    if (bytes.starts_with(b"ID3")
        || (bytes.len() >= 2 && bytes[0] == 0xff && bytes[1] & 0xe0 == 0xe0))
        && ext == "mp3"
    {
        return Some(("audio/mpeg", ".mp3"));
    }
    if bytes.len() >= 12 && &bytes[..4] == b"RIFF" && &bytes[8..12] == b"WAVE" && ext == "wav" {
        return Some(("audio/wav", ".wav"));
    }
    if bytes.len() >= 12 && &bytes[4..8] == b"ftyp" && &bytes[8..12] == b"M4A " && ext == "m4a" {
        return Some(("audio/mp4", ".m4a"));
    }
    if !bytes.is_empty() && !bytes.contains(&0) {
        return match ext.as_str() {
            "txt" => Some(("text/plain", ".txt")),
            "md" | "markdown" => Some(("text/markdown", ".md")),
            "json" => Some(("application/json", ".json")),
            "csv" => Some(("text/csv", ".csv")),
            _ => None,
        };
    }
    None
}

fn is_mp4_video_iso_bmff(bytes: &[u8]) -> bool {
    if bytes.len() < 16 || &bytes[4..8] != b"ftyp" {
        return false;
    }
    let is_video_brand = |brand: &[u8]| {
        matches!(
            brand,
            b"isom" | b"iso2" | b"avc1" | b"mp41" | b"mp42" | b"dash"
        )
    };
    is_video_brand(&bytes[8..12]) || bytes[16..].chunks_exact(4).any(is_video_brand)
}

/** Minimal bounded ISO-BMFF `mvhd` reader. It is metadata-only and never invokes a decoder. */
fn mp4_duration_millis(bytes: &[u8]) -> Option<u64> {
    fn boxes(bytes: &[u8], depth: u8) -> Option<u64> {
        if depth > 8 {
            return None;
        }
        let mut offset = 0usize;
        while offset.checked_add(8)? <= bytes.len() {
            let raw_size = u32::from_be_bytes(bytes[offset..offset + 4].try_into().ok()?) as usize;
            let kind = &bytes[offset + 4..offset + 8];
            let (header, size) = if raw_size == 1 {
                if offset.checked_add(16)? > bytes.len() {
                    return None;
                }
                (
                    16usize,
                    u64::from_be_bytes(bytes[offset + 8..offset + 16].try_into().ok()?) as usize,
                )
            } else if raw_size == 0 {
                (8usize, bytes.len() - offset)
            } else {
                (8usize, raw_size)
            };
            if size < header || offset.checked_add(size)? > bytes.len() {
                return None;
            }
            let payload = &bytes[offset + header..offset + size];
            if kind == b"mvhd" {
                if payload.len() < 20 {
                    return None;
                }
                let version = payload[0];
                let (timescale_at, duration_at, duration_len) = if version == 1 {
                    (20usize, 24usize, 8usize)
                } else {
                    (12usize, 16usize, 4usize)
                };
                if payload.len() < duration_at + duration_len {
                    return None;
                }
                let timescale =
                    u32::from_be_bytes(payload[timescale_at..timescale_at + 4].try_into().ok()?)
                        as u64;
                let duration = if duration_len == 8 {
                    u64::from_be_bytes(payload[duration_at..duration_at + 8].try_into().ok()?)
                } else {
                    u32::from_be_bytes(payload[duration_at..duration_at + 4].try_into().ok()?)
                        as u64
                };
                return (timescale > 0 && duration > 0)
                    .then(|| duration.saturating_mul(1000) / timescale)
                    .filter(|value| *value <= 4 * 60 * 60 * 1000);
            }
            if matches!(kind, b"moov" | b"trak" | b"mdia") {
                if let Some(value) = boxes(payload, depth + 1) {
                    return Some(value);
                }
            }
            offset += size;
        }
        None
    }
    boxes(bytes, 0)
}

fn message_blocks(fields: &Map<String, Value>, text_key: &str) -> Result<Vec<Value>, String> {
    let text = fields
        .get(text_key)
        .and_then(Value::as_str)
        .map(str::trim)
        .unwrap_or_default();
    let attachment_blocks = fields
        .get("attachmentBlocks")
        .and_then(Value::as_array)
        .cloned()
        .unwrap_or_default();
    if text.is_empty() && attachment_blocks.is_empty() {
        return Err(json_error("请输入文字或保留附件后再发送"));
    }
    if attachment_blocks.len() > MAX_CONVERSATION_ATTACHMENT_COUNT {
        return Err(json_error("每条消息最多 4 个附件"));
    }
    let mut blocks = Vec::new();
    if !text.is_empty() {
        blocks.push(json!({"kind":"TEXT","ordinal":0,"text":require_short_text(fields.get(text_key).unwrap_or(&Value::Null), text_key, 2_000_000, false)?}));
    }
    for (index, block) in attachment_blocks.into_iter().enumerate() {
        blocks.push(json!({"kind":"ASSET_REF","ordinal":blocks.len(),"asset":block}));
        if index >= MAX_CONVERSATION_ATTACHMENT_COUNT {
            return Err(json_error("附件数量无效"));
        }
    }
    Ok(blocks)
}

fn apply_domain_mutation(
    exchange: &mut Value,
    args: &DomainMutationArgs,
) -> Result<(String, u64), String> {
    if !is_stable_id(&args.intent_id) || !is_stable_id(&args.workspace_id) {
        return Err(json_error("intent 或 workspace stable ID 无效"));
    }
    let fields = args
        .fields
        .as_object()
        .ok_or_else(|| json_error("fields 必须是对象"))?;
    let root = exchange
        .as_object_mut()
        .ok_or_else(|| json_error("交换 IR 无效"))?;
    let now = local_now();
    if args.action == "create" {
        let id = generated_id(&args.entity, &args.intent_id);
        let record = match args.entity.as_str() {
            "project" => {
                allowed_keys(fields, &["title", "description"])?;
                json!({"id": id, "title": require_short_text(fields.get("title").unwrap_or(&Value::Null), "title", 120, false)?, "description": require_short_text(fields.get("description").unwrap_or(&Value::String(String::new())), "description", 2000, true)?, "pinned": false, "archived": false, "revision": 1, "createdAt": now, "updatedAt": now})
            }
            "knowledge" => {
                allowed_keys(
                    fields,
                    &[
                        "title",
                        "body",
                        "tags",
                        "scope",
                        "projectId",
                        "classification",
                    ],
                )?;
                let body = require_short_text(
                    fields.get("body").unwrap_or(&Value::Null),
                    "body",
                    2_000_000,
                    false,
                )?;
                let scope = require_short_text(
                    fields
                        .get("scope")
                        .unwrap_or(&Value::String("GLOBAL".into())),
                    "scope",
                    16,
                    false,
                )?;
                let project_id = fields.get("projectId").cloned().unwrap_or(Value::Null);
                if (scope == "GLOBAL" && !project_id.is_null())
                    || (scope == "PROJECT"
                        && !project_id
                            .as_str()
                            .is_some_and(|value| object_is_active(root, "project", value)))
                    || !matches!(scope.as_str(), "GLOBAL" | "PROJECT")
                {
                    return Err(json_error("Knowledge scope 或项目状态无效"));
                }
                let tags = fields.get("tags").cloned().unwrap_or_else(|| json!([]));
                if !tags.as_array().is_some_and(|items| {
                    items.len() <= 100
                        && items.iter().all(|item| {
                            item.as_str()
                                .is_some_and(|text| text.chars().count() <= 100)
                        })
                }) {
                    return Err(json_error("Knowledge tags 无效"));
                }
                json!({"id": id, "title": require_short_text(fields.get("title").unwrap_or(&Value::Null), "title", 120, false)?, "body": body, "tags": tags, "scope": scope, "projectId": project_id, "status":"ACTIVE", "revision":1, "contentHash":sha256(body.as_bytes()), "createdAt":now, "updatedAt":now, "classification": fields.get("classification").and_then(Value::as_str).unwrap_or("NORMAL")})
            }
            "memory" => {
                allowed_keys(fields, &["body", "scope", "scopeId", "classification"])?;
                let body = require_short_text(
                    fields.get("body").unwrap_or(&Value::Null),
                    "body",
                    2_000_000,
                    false,
                )?;
                let scope = require_short_text(
                    fields
                        .get("scope")
                        .unwrap_or(&Value::String("GLOBAL".into())),
                    "scope",
                    16,
                    false,
                )?;
                let scope_id = fields.get("scopeId").cloned().unwrap_or(Value::Null);
                let scope_ok = match scope.as_str() {
                    "GLOBAL" => scope_id.is_null(),
                    "PROJECT" => scope_id
                        .as_str()
                        .is_some_and(|id| object_is_active(root, "project", id)),
                    "CONVERSATION" => scope_id
                        .as_str()
                        .is_some_and(|id| object_is_active(root, "conversation", id)),
                    _ => false,
                };
                if !scope_ok {
                    return Err(json_error("Memory scope 或端点状态无效"));
                }
                json!({"id": id, "body":body, "scope":scope, "scopeId":scope_id, "status":"ACTIVE", "revision":1, "contentHash":sha256(body.as_bytes()), "createdAt":now, "updatedAt":now, "classification":fields.get("classification").and_then(Value::as_str).unwrap_or("NORMAL")})
            }
            "conversation" => {
                allowed_keys(
                    fields,
                    &["title", "projectId", "firstMessage", "attachmentBlocks"],
                )?;
                let project_id = fields.get("projectId").cloned().unwrap_or(Value::Null);
                if !project_id.is_null()
                    && !project_id
                        .as_str()
                        .is_some_and(|value| object_is_active(root, "project", value))
                {
                    return Err(json_error("Conversation project 无效或已归档"));
                }
                let message_id = format!(
                    "message-{}",
                    &args.intent_id[..args.intent_id.len().min(30)]
                );
                let blocks = message_blocks(fields, "firstMessage")?;
                json!({"id":id, "projectId":project_id, "title":require_short_text(fields.get("title").unwrap_or(&Value::Null), "title",120,false)?, "currentLeafId":message_id, "pinned":false,"archived":false,"revision":1,"createdAt":now,"updatedAt":now,"messages":[{"id":message_id,"parentId":Value::Null,"ordinal":0,"role":"user","delivery":"COMPLETE","revision":1,"createdAt":now,"blocks":blocks}]})
            }
            "relation" => {
                allowed_keys(fields, &["fromId", "toId", "kind"])?;
                let from = require_short_text(
                    fields.get("fromId").unwrap_or(&Value::Null),
                    "fromId",
                    64,
                    false,
                )?;
                let to = require_short_text(
                    fields.get("toId").unwrap_or(&Value::Null),
                    "toId",
                    64,
                    false,
                )?;
                let kind = require_short_text(
                    fields.get("kind").unwrap_or(&Value::Null),
                    "kind",
                    32,
                    false,
                )?;
                if from == to
                    || !object_is_active(root, "knowledge", &from)
                    || !object_is_active(root, "knowledge", &to)
                    || !matches!(kind.as_str(), "RELATED" | "DERIVED_FROM" | "REFERENCES")
                {
                    return Err(json_error("关系端点、状态或类型无效"));
                }
                let knowledge_items = root
                    .get("knowledge")
                    .and_then(Value::as_array)
                    .ok_or_else(|| json_error("knowledge 集合无效"))?;
                let related = knowledge_items
                    .iter()
                    .filter_map(Value::as_object)
                    .filter(|item| {
                        item.get("id").and_then(Value::as_str) == Some(&from)
                            || item.get("id").and_then(Value::as_str) == Some(&to)
                    })
                    .collect::<Vec<_>>();
                if related.len() != 2
                    || related[0].get("scope") != related[1].get("scope")
                    || related[0].get("projectId") != related[1].get("projectId")
                {
                    return Err(json_error("关系端点必须处于同一活动 scope"));
                }
                let (from, to) = if kind == "RELATED" && from > to {
                    (to, from)
                } else {
                    (from, to)
                };
                let duplicate =
                    root.get("relations")
                        .and_then(Value::as_array)
                        .is_some_and(|items| {
                            items.iter().filter_map(Value::as_object).any(|item| {
                                item.get("status").and_then(Value::as_str) == Some("ACTIVE")
                                    && item.get("kind").and_then(Value::as_str) == Some(&kind)
                                    && item.get("fromId").and_then(Value::as_str) == Some(&from)
                                    && item.get("toId").and_then(Value::as_str) == Some(&to)
                            })
                        });
                if duplicate {
                    return Err(json_error("活动关系重复；不会自动去重或覆盖"));
                }
                json!({"id":id,"fromId":from,"toId":to,"kind":kind,"status":"ACTIVE","revision":1,"createdAt":now})
            }
            _ => return Err(json_error("不支持的创建对象")),
        };
        root.get_mut(entity_array(&args.entity)?)
            .and_then(Value::as_array_mut)
            .ok_or_else(|| json_error("领域集合无效"))?
            .push(record);
        return Ok((id, 1));
    }
    let id = args
        .object_id
        .as_deref()
        .ok_or_else(|| json_error("操作必须指定对象 ID"))?;
    if !is_stable_id(id) {
        return Err(json_error("对象 stable ID 无效"));
    }
    if args.action == "setProject" && args.entity == "conversation" {
        let project_id = fields.get("projectId").and_then(Value::as_str);
        if let Some(project_id) = project_id {
            if !is_stable_id(project_id)
                || !root
                    .get("projects")
                    .and_then(Value::as_array)
                    .is_some_and(|projects| {
                        projects.iter().filter_map(Value::as_object).any(|project| {
                            project.get("id").and_then(Value::as_str) == Some(project_id)
                                && !is_deleted("project", project)
                                && project.get("archived").and_then(Value::as_bool) != Some(true)
                        })
                    })
            {
                return Err(json_error("项目不存在或不可用，不能变更会话归属"));
            }
        }
    }
    // P6-F branch owner: creates a new local Conversation from the selected persisted tree
    // prefix.  It is deliberately a typed, idempotent DomainMutation, not a UI-only copy.
    if args.action == "branchFromMessage" && args.entity == "conversation" {
        allowed_keys(fields, &["messageId"])?;
        let source_message_id = require_short_text(
            fields.get("messageId").unwrap_or(&Value::Null),
            "messageId",
            128,
            false,
        )?;
        let source = root
            .get("conversations")
            .and_then(Value::as_array)
            .and_then(|items| {
                items
                    .iter()
                    .find(|item| item.get("id").and_then(Value::as_str) == Some(id))
            })
            .cloned()
            .ok_or_else(|| json_error("来源会话不存在"))?;
        if args.expected_revision != Some(revision_of(object(&source, "conversation")?)?) {
            return Err(json_error("REVISION_CONFLICT：来源会话已变更，未创建分支"));
        }
        let source_object = object(&source, "conversation")?;
        let messages = require_array(source_object, "messages")?;
        let by_id = messages
            .iter()
            .map(|message| {
                let object = object(message, "message")?;
                Ok((require_string(object, "id")?.to_owned(), message.clone()))
            })
            .collect::<Result<BTreeMap<_, _>, String>>()?;
        let mut prefix = Vec::new();
        let mut cursor = source_message_id.clone();
        for _ in 0..256 {
            let message = by_id
                .get(&cursor)
                .ok_or_else(|| json_error("分支来源消息不存在"))?;
            prefix.push(message.clone());
            match message.get("parentId").and_then(Value::as_str) {
                Some(parent) => cursor = parent.to_owned(),
                None => break,
            }
        }
        if prefix.len() == 256
            && prefix
                .last()
                .and_then(|message| message.get("parentId"))
                .is_some()
        {
            return Err(json_error("消息树过深或存在循环，未创建分支"));
        }
        prefix.reverse();
        let branch_id = generated_id("conversation-branch", &args.intent_id);
        let mut old_to_new = BTreeMap::new();
        for (index, message) in prefix.iter().enumerate() {
            old_to_new.insert(
                require_string(object(message, "message")?, "id")?.to_owned(),
                format!(
                    "message-{}-{index}",
                    &args.intent_id[..args.intent_id.len().min(24)]
                ),
            );
        }
        let branch_messages = prefix
            .into_iter()
            .enumerate()
            .map(|(index, message)| {
                let mut copied = message
                    .as_object()
                    .cloned()
                    .ok_or_else(|| json_error("消息对象无效"))?;
                let original_id = require_string(&copied, "id")?.to_owned();
                copied.insert("id".into(), Value::String(old_to_new[&original_id].clone()));
                let parent = copied
                    .get("parentId")
                    .and_then(Value::as_str)
                    .and_then(|parent| old_to_new.get(parent).cloned())
                    .map(Value::String)
                    .unwrap_or(Value::Null);
                copied.insert("parentId".into(), parent);
                copied.insert("ordinal".into(), Value::Number((index as u64).into()));
                Ok(Value::Object(copied))
            })
            .collect::<Result<Vec<_>, String>>()?;
        let source_title = require_short_text(
            source_object.get("title").unwrap_or(&Value::Null),
            "title",
            120,
            false,
        )?;
        let record = json!({
            "id": branch_id,
            "projectId": source_object.get("projectId").cloned().unwrap_or(Value::Null),
            "title": format!("{source_title} · 分支"),
            "currentLeafId": old_to_new[&source_message_id].clone(),
            "pinned": false, "archived": false, "revision": 1,
            "createdAt": now, "updatedAt": now,
            "branchProvenance": {"sourceConversationId": id, "sourceMessageId": source_message_id, "kind": "LOCAL_MESSAGE_TREE_PREFIX"},
            "messages": branch_messages,
        });
        root.get_mut("conversations")
            .and_then(Value::as_array_mut)
            .ok_or_else(|| json_error("会话集合无效"))?
            .push(record);
        return Ok((branch_id, 1));
    }
    let item = find_object_mut(root, &args.entity, id)?;
    let current = revision_of(item)?;
    if args.expected_revision != Some(current) {
        return Err(json_error(&format!(
            "REVISION_CONFLICT：expected={:?}，actual={current}",
            args.expected_revision
        )));
    }
    match args.action.as_str() {
        "update" => match args.entity.as_str() {
            "project" => {
                allowed_keys(fields, &["title", "description", "pinned"])?;
                if let Some(value) = fields.get("title") {
                    item.insert(
                        "title".into(),
                        Value::String(require_short_text(value, "title", 120, false)?),
                    );
                }
                if let Some(value) = fields.get("description") {
                    item.insert(
                        "description".into(),
                        Value::String(require_short_text(value, "description", 2000, true)?),
                    );
                }
                if let Some(value) = fields.get("pinned") {
                    item.insert(
                        "pinned".into(),
                        Value::Bool(
                            value
                                .as_bool()
                                .ok_or_else(|| json_error("pinned 必须是布尔值"))?,
                        ),
                    );
                }
            }
            "conversation" => {
                allowed_keys(fields, &["title"])?;
                item.insert(
                    "title".into(),
                    Value::String(require_short_text(
                        fields.get("title").unwrap_or(&Value::Null),
                        "title",
                        120,
                        false,
                    )?),
                );
            }
            "knowledge" => {
                allowed_keys(fields, &["title", "body", "tags"])?;
                if let Some(value) = fields.get("title") {
                    item.insert(
                        "title".into(),
                        Value::String(require_short_text(value, "title", 120, false)?),
                    );
                }
                if let Some(value) = fields.get("body") {
                    let body = require_short_text(value, "body", 2_000_000, false)?;
                    item.insert("contentHash".into(), Value::String(sha256(body.as_bytes())));
                    item.insert("body".into(), Value::String(body));
                }
                if let Some(value) = fields.get("tags") {
                    if !value.as_array().is_some_and(|items| items.len() <= 100) {
                        return Err(json_error("tags 无效"));
                    }
                    item.insert("tags".into(), value.clone());
                }
            }
            "memory" => {
                allowed_keys(fields, &["body"])?;
                let body = require_short_text(
                    fields.get("body").unwrap_or(&Value::Null),
                    "body",
                    2_000_000,
                    false,
                )?;
                item.insert("body".into(), Value::String(body.clone()));
                item.insert("contentHash".into(), Value::String(sha256(body.as_bytes())));
            }
            _ => return Err(json_error("该对象不支持 update")),
        },
        "appendMessage" if args.entity == "conversation" => {
            allowed_keys(fields, &["text", "role", "attachmentBlocks"])?;
            let role = fields.get("role").and_then(Value::as_str).unwrap_or("user");
            if !matches!(role, "user" | "tool") {
                return Err(json_error("只允许显式本地 user 或 tool 树节点"));
            }
            let parent = item
                .get("currentLeafId")
                .and_then(Value::as_str)
                .ok_or_else(|| json_error("currentLeafId 无效"))?
                .to_owned();
            let message_id = format!(
                "message-{}",
                &args.intent_id[..args.intent_id.len().min(30)]
            );
            let messages = item
                .get_mut("messages")
                .and_then(Value::as_array_mut)
                .ok_or_else(|| json_error("messages 无效"))?;
            let ordinal = messages.len() as u64;
            let blocks = message_blocks(fields, "text")?;
            messages.push(json!({"id":message_id,"parentId":parent,"ordinal":ordinal,"role":role,"delivery":"COMPLETE","revision":1,"createdAt":now,"blocks":blocks}));
            item.insert("currentLeafId".into(), Value::String(message_id));
        }
        "setPinned" if args.entity == "conversation" => {
            allowed_keys(fields, &["pinned"])?;
            if item.get("archived").and_then(Value::as_bool) == Some(true) {
                return Err(json_error("已归档会话必须先恢复，不能置顶"));
            }
            let pinned = fields
                .get("pinned")
                .and_then(Value::as_bool)
                .ok_or_else(|| json_error("pinned 必须是布尔值"))?;
            item.insert("pinned".into(), Value::Bool(pinned));
        }
        "archive" if args.entity == "conversation" => {
            allowed_keys(fields, &[])?;
            item.insert("archived".into(), Value::Bool(true));
            item.insert("pinned".into(), Value::Bool(false));
        }
        "setProject" if args.entity == "conversation" => {
            allowed_keys(fields, &["projectId"])?;
            let project_id = match fields.get("projectId") {
                Some(Value::String(value)) if is_stable_id(value) => Value::String(value.clone()),
                Some(Value::Null) | None => Value::Null,
                _ => return Err(json_error("projectId 必须是稳定 ID 或空值")),
            };
            item.insert("projectId".into(), project_id);
        }
        "softDelete" => match args.entity.as_str() {
            "project" | "conversation" => {
                item.insert("deleted".into(), Value::Bool(true));
                item.insert("archived".into(), Value::Bool(true));
                item.insert("pinned".into(), Value::Bool(false));
            }
            "knowledge" | "memory" => {
                item.insert("status".into(), Value::String("DELETED".into()));
            }
            "relation" => {
                item.insert("status".into(), Value::String("REVOKED".into()));
            }
            _ => return Err(json_error("不支持 soft delete")),
        },
        "restoreDeleted" if args.entity == "conversation" => {
            allowed_keys(fields, &[])?;
            item.insert("deleted".into(), Value::Bool(false));
            item.insert("archived".into(), Value::Bool(false));
            item.insert("pinned".into(), Value::Bool(false));
        }
        "restore" => match args.entity.as_str() {
            "conversation" => {
                item.insert("archived".into(), Value::Bool(false));
                item.insert("pinned".into(), Value::Bool(false));
            }
            "project" => {
                item.insert("archived".into(), Value::Bool(false));
            }
            "knowledge" | "memory" => {
                item.insert("status".into(), Value::String("ACTIVE".into()));
            }
            "relation" => {
                item.insert("status".into(), Value::String("ACTIVE".into()));
            }
            _ => return Err(json_error("不支持 restore")),
        },
        _ => return Err(json_error("不支持的领域操作")),
    }
    let next = current
        .checked_add(1)
        .ok_or_else(|| json_error("revision 溢出"))?;
    item.insert("revision".into(), Value::Number(next.into()));
    if args.entity != "relation" {
        item.insert("updatedAt".into(), Value::String(now.into()));
    }
    Ok((id.to_owned(), next))
}

fn canonical_json(value: &Value) -> Result<String, String> {
    match value {
        Value::Null => Ok("null".to_owned()),
        Value::Bool(value) => Ok(value.to_string()),
        Value::Number(value) if value.as_i64().is_some() || value.as_u64().is_some() => {
            Ok(value.to_string())
        }
        Value::Number(_) => Err(json_error("交换 JSON 只允许安全整数")),
        Value::String(value) => {
            serde_json::to_string(value).map_err(|_| json_error("JSON 字符串无效"))
        }
        Value::Array(values) => values
            .iter()
            .map(canonical_json)
            .collect::<Result<Vec<_>, _>>()
            .map(|values| format!("[{}]", values.join(","))),
        Value::Object(values) => values
            .iter()
            .map(|(key, value)| {
                Ok(format!(
                    "{}:{}",
                    serde_json::to_string(key).map_err(|_| json_error("JSON key 无效"))?,
                    canonical_json(value)?
                ))
            })
            .collect::<Result<Vec<_>, String>>()
            .map(|values| format!("{{{}}}", values.join(","))),
    }
}

fn semantic_hash(exchange: &Value) -> Result<String, String> {
    let mut copy = exchange.clone();
    copy.get_mut("export")
        .and_then(Value::as_object_mut)
        .ok_or_else(|| json_error("export 无效"))?
        .remove("semanticHash");
    Ok(sha256(canonical_json(&copy)?.as_bytes()))
}

fn ensure_no_forbidden_keys(value: &Value) -> Result<(), String> {
    const FORBIDDEN: [&str; 13] = [
        "credential",
        "api_key",
        "apikey",
        "authorization",
        "providerraw",
        "providerpayload",
        "prompt",
        "runspec",
        "runtime",
        "diagnostic",
        "routepref",
        "uri",
        "path",
    ];
    match value {
        Value::Array(values) => values.iter().try_for_each(ensure_no_forbidden_keys),
        Value::Object(values) => values.iter().try_for_each(|(key, value)| {
            let compact = key.to_ascii_lowercase().replace(['_', '-'], "");
            if FORBIDDEN.iter().any(|blocked| compact.contains(blocked)) && key != "entry" {
                return Err(json_error(&format!("不允许的交换字段：{key}")));
            }
            ensure_no_forbidden_keys(value)
        }),
        _ => Ok(()),
    }
}

fn object<'a>(value: &'a Value, context: &str) -> Result<&'a Map<String, Value>, String> {
    value
        .as_object()
        .ok_or_else(|| json_error(&format!("{context} 必须是对象")))
}

fn collect_asset_refs(exchange: &Value) -> Result<BTreeSet<String>, String> {
    let conversations = require_array(object(exchange, "exchange")?, "conversations")?;
    let mut refs = BTreeSet::new();
    for conversation in conversations {
        let messages = require_array(object(conversation, "conversation")?, "messages")?;
        for message in messages {
            let blocks = require_array(object(message, "message")?, "blocks")?;
            for block in blocks {
                let block = object(block, "block")?;
                if block.get("kind").and_then(Value::as_str) == Some("ASSET_REF") {
                    let asset = object(
                        block.get("asset").ok_or_else(|| json_error("asset 缺失"))?,
                        "asset",
                    )?;
                    let hash = require_string(asset, "sha256")?;
                    let entry = require_string(asset, "entry")?;
                    if !is_sha256(hash)
                        || entry != format!("assets/{hash}")
                        || !is_stable_id(require_string(asset, "id")?)
                    {
                        return Err(json_error("asset 内容寻址或 stable ID 无效"));
                    }
                    refs.insert(entry.to_owned());
                }
            }
        }
    }
    Ok(refs)
}

fn collect_asset_ref_counts(exchange: &Value) -> Result<BTreeMap<String, u64>, String> {
    let conversations = require_array(object(exchange, "exchange")?, "conversations")?;
    let mut refs = BTreeMap::new();
    for conversation in conversations {
        let messages = require_array(object(conversation, "conversation")?, "messages")?;
        for message in messages {
            let blocks = require_array(object(message, "message")?, "blocks")?;
            for block in blocks {
                let block = object(block, "block")?;
                if block.get("kind").and_then(Value::as_str) != Some("ASSET_REF") {
                    continue;
                }
                let asset = object(
                    block.get("asset").ok_or_else(|| json_error("asset 缺失"))?,
                    "asset",
                )?;
                let hash = require_string(asset, "sha256")?;
                if !is_sha256(hash)
                    || require_string(asset, "entry")? != format!("assets/{hash}")
                    || !is_stable_id(require_string(asset, "id")?)
                {
                    return Err(json_error("asset 内容寻址或 stable ID 无效"));
                }
                *refs.entry(hash.to_owned()).or_insert(0) += 1;
            }
        }
    }
    Ok(refs)
}

fn validate_exchange(exchange: &Value) -> Result<(bool, usize), String> {
    let root = object(exchange, "exchange")?;
    let expected: BTreeSet<&str> = [
        "format",
        "version",
        "export",
        "projects",
        "conversations",
        "knowledge",
        "memory",
        "relations",
        "settings",
    ]
    .into_iter()
    .collect();
    if root.keys().map(String::as_str).collect::<BTreeSet<_>>() != expected
        || root.get("format").and_then(Value::as_str) != Some("nfai.exchange")
        || root.get("version").and_then(Value::as_u64) != Some(1)
    {
        return Err(json_error("不是受支持的 nfai.exchange.v1"));
    }
    ensure_no_forbidden_keys(exchange)?;
    let export = object(
        root.get("export")
            .ok_or_else(|| json_error("export 缺失"))?,
        "export",
    )?;
    let export_id = require_string(export, "id")?;
    let declared_semantic_hash = require_string(export, "semanticHash")?;
    if !is_stable_id(export_id)
        || !is_sha256(declared_semantic_hash)
        || semantic_hash(exchange)? != declared_semantic_hash
    {
        return Err(json_error("semantic hash 或 export stable ID 无效"));
    }
    if !matches!(
        object(
            export
                .get("origin")
                .ok_or_else(|| json_error("origin 缺失"))?,
            "origin"
        )?
        .get("platform")
        .and_then(Value::as_str),
        Some("ANDROID" | "DESKTOP")
    ) {
        return Err(json_error("origin platform 无效"));
    }
    if !matches!(
        export.get("sensitivity").and_then(Value::as_str),
        Some("NORMAL" | "HIGH_SENSITIVE")
    ) {
        return Err(json_error("sensitivity 无效"));
    }

    let mut object_ids = BTreeSet::new();
    for group in [
        "projects",
        "conversations",
        "knowledge",
        "memory",
        "relations",
    ] {
        let entries = require_array(root, group)?;
        for entry in entries {
            let id = require_string(object(entry, group)?, "id")?;
            if !is_stable_id(id) || !object_ids.insert(id.to_owned()) {
                return Err(json_error("stable ID 重复、跨域冲突或无效"));
            }
        }
    }
    let mut messages = 0;
    for conversation in require_array(root, "conversations")? {
        let conversation = object(conversation, "conversation")?;
        if let Some(project_id) = conversation.get("projectId").and_then(Value::as_str) {
            if !object_ids.contains(project_id) {
                return Err(json_error("会话引用的项目不存在"));
            }
        }
        let tree = require_array(conversation, "messages")?;
        if tree.is_empty() {
            return Err(json_error("会话必须拥有消息树"));
        }
        let mut ids = BTreeSet::new();
        for message in tree {
            let message = object(message, "message")?;
            let id = require_string(message, "id")?;
            if !is_stable_id(id) || !ids.insert(id.to_owned()) {
                return Err(json_error("消息 stable ID 重复或无效"));
            }
            if message.get("delivery").and_then(Value::as_str) == Some("PARTIAL")
                && message.get("role").and_then(Value::as_str) != Some("assistant")
            {
                return Err(json_error("只有 assistant 消息可为 PARTIAL"));
            }
            messages += 1;
        }
        if !ids.contains(require_string(conversation, "currentLeafId")?) {
            return Err(json_error("currentLeafId 不在消息树内"));
        }
        for message in tree {
            let message = object(message, "message")?;
            if let Some(parent_id) = message.get("parentId").and_then(Value::as_str) {
                if !ids.contains(parent_id) {
                    return Err(json_error("消息 parentId 不在同一树"));
                }
            }
            for block in require_array(message, "blocks")? {
                let block = object(block, "block")?;
                match block.get("kind").and_then(Value::as_str) {
                    Some("TEXT" | "MARKDOWN" | "CODE")
                        if block.get("text").and_then(Value::as_str).is_some() => {}
                    Some("ASSET_REF") => {
                        let _ = object(
                            block.get("asset").ok_or_else(|| json_error("asset 缺失"))?,
                            "asset",
                        )?;
                    }
                    _ => return Err(json_error("消息 block 无效或正文缺失")),
                }
            }
        }
    }
    for group in ["knowledge", "memory"] {
        for item in require_array(root, group)? {
            let item = object(item, group)?;
            let body = require_string(item, "body")?;
            let content_hash = require_string(item, "contentHash")?;
            if !is_sha256(content_hash) || sha256(body.as_bytes()) != content_hash {
                return Err(json_error(&format!("{group} contentHash 无效")));
            }
        }
    }
    for relation in require_array(root, "relations")? {
        let relation = object(relation, "relation")?;
        if !object_ids.contains(require_string(relation, "fromId")?)
            || !object_ids.contains(require_string(relation, "toId")?)
        {
            return Err(json_error("relation 引用不存在"));
        }
    }
    let high_sensitive = export.get("sensitivity").and_then(Value::as_str)
        == Some("HIGH_SENSITIVE")
        || exchange.to_string().contains("HIGH_SENSITIVE");
    Ok((high_sensitive, messages))
}

fn safe_entry(name: &str) -> bool {
    !name.is_empty()
        && !name.starts_with('/')
        && !name.contains("..")
        && !name.contains('\\')
        && !name.ends_with('/')
}

/// P6 v2 is intentionally an IR-only gate for now. It proves both runtimes reject lossy or
/// unsafe owner data before a future v2 staging/SQLite transaction is allowed to exist.
fn validate_exchange_v2_ir(exchange: &Value) -> Result<String, String> {
    let root = object(exchange, "v2 exchange")?;
    let expected: BTreeSet<&str> = ["format", "version", "export", "projects", "conversations", "knowledge", "memory", "relations", "settings"].into_iter().collect();
    if root.keys().map(String::as_str).collect::<BTreeSet<_>>() != expected
        || root.get("format").and_then(Value::as_str) != Some("nfai.exchange")
        || root.get("version").and_then(Value::as_u64) != Some(2) {
        return Err(json_error("不是受支持的 nfai.exchange.v2 IR"));
    }
    ensure_no_forbidden_keys(exchange)?;
    fn reject_source_reference(value: &Value) -> Result<(), String> {
        match value {
            Value::Object(map) => { if map.contains_key("sourceReference") { return Err(json_error("v2 不接受 sourceReference locator")); } for child in map.values() { reject_source_reference(child)?; } }
            Value::Array(items) => for child in items { reject_source_reference(child)?; },
            _ => {}
        }
        Ok(())
    }
    reject_source_reference(exchange)?;
    let export = object(root.get("export").ok_or_else(|| json_error("v2 export 缺失"))?, "v2 export")?;
    let export_expected: BTreeSet<&str> = ["id", "createdAt", "origin", "semanticHash", "sensitivity"].into_iter().collect();
    if export.keys().map(String::as_str).collect::<BTreeSet<_>>() != export_expected { return Err(json_error("v2 export 字段无效")); }
    let semantic = require_string(export, "semanticHash")?;
    if !is_stable_id(require_string(export, "id")?) || !is_sha256(semantic) || semantic_hash(exchange)? != semantic { return Err(json_error("v2 semantic hash 无效")); }
    let projects = require_array(root, "projects")?; let conversations = require_array(root, "conversations")?; let knowledge = require_array(root, "knowledge")?; let memory = require_array(root, "memory")?; let relations = require_array(root, "relations")?;
    let mut ids = BTreeSet::new();
    for (name, entries) in [("projects", projects), ("conversations", conversations), ("knowledge", knowledge), ("memory", memory), ("relations", relations)] {
        for entry in entries { let item = object(entry, name)?; let id = require_string(item, "id")?; if !is_stable_id(id) || !ids.insert(id.to_owned()) { return Err(json_error("v2 stable ID 无效或重复")); } }
    }
    for entry in projects {
        let item = object(entry, "v2 project")?; let expected: BTreeSet<&str> = ["id", "title", "description", "appearance", "pinned", "archived", "createdAt", "updatedAt", "schemaVersion", "instructionHistory"].into_iter().collect();
        if item.keys().map(String::as_str).collect::<BTreeSet<_>>() != expected { return Err(json_error("v2 project 字段无效")); }
        let appearance = object(item.get("appearance").ok_or_else(|| json_error("v2 project appearance 缺失"))?, "appearance")?;
        if appearance.keys().map(String::as_str).collect::<BTreeSet<_>>() != ["color", "icon"].into_iter().collect() { return Err(json_error("v2 project appearance 字段无效")); }
        let history = require_array(item, "instructionHistory")?; let mut revisions = BTreeSet::new();
        for revision in history { let revision = object(revision, "instructionHistory")?; let expected: BTreeSet<&str> = ["id", "revision", "content", "source", "contentHash", "createdAt", "schemaVersion"].into_iter().collect(); if revision.keys().map(String::as_str).collect::<BTreeSet<_>>() != expected || revision.get("source").and_then(Value::as_str) != Some("USER") || sha256(require_string(revision, "content")?.as_bytes()) != require_string(revision, "contentHash")? || !revisions.insert(require_string(revision, "id")?.to_owned()) { return Err(json_error("v2 project instruction history 无效")); } }
    }
    for entry in conversations {
        let item = object(entry, "v2 conversation")?; let expected: BTreeSet<&str> = ["id", "projectId", "title", "currentLeafId", "pinned", "archived", "revision", "createdAt", "updatedAt", "autoTitlePending", "surface", "schemaVersion", "settings", "messages"].into_iter().collect(); if item.keys().map(String::as_str).collect::<BTreeSet<_>>() != expected || item.get("surface").and_then(Value::as_str) != Some("CHAT") { return Err(json_error("v2 conversation 字段或 surface 无效")); }
        if let Some(project) = item.get("projectId").and_then(Value::as_str) { if !ids.contains(project) { return Err(json_error("v2 conversation project 缺失")); } }
        let settings = object(item.get("settings").ok_or_else(|| json_error("v2 conversation settings 缺失"))?, "settings")?; let settings_expected: BTreeSet<&str> = ["defaultProviderId", "defaultModelId", "harnessId", "harnessVersion", "contextPolicyVersion", "memorySources", "schemaVersion"].into_iter().collect(); if settings.keys().map(String::as_str).collect::<BTreeSet<_>>() != settings_expected { return Err(json_error("v2 conversation settings 字段无效")); }
        for source in require_array(settings, "memorySources")? { let source = object(source, "memorySource")?; if source.keys().map(String::as_str).collect::<BTreeSet<_>>() != ["memoryId", "sourceKind", "sourceVersion"].into_iter().collect() || !memory.iter().any(|item| object(item, "memory").ok().and_then(|item| item.get("id")).and_then(Value::as_str) == source.get("memoryId").and_then(Value::as_str)) { return Err(json_error("v2 memory source 无效")); } }
    }
    for (name, entries, expected) in [
        ("knowledge", knowledge, ["id", "title", "body", "sourceEvidence", "provenance", "attachments", "status", "scope", "projectId", "tags", "contentHash", "createdAt", "updatedAt", "schemaVersion", "history"].as_slice()),
        ("memory", memory, ["id", "title", "body", "scope", "scopeId", "source", "sourceStableId", "sourceSummary", "status", "contentHash", "conceptHash", "createdAt", "updatedAt", "lastConfirmedAt", "deletedAt", "schemaVersion", "history"].as_slice()),
        ("relation", relations, ["id", "type", "fromId", "toId", "scope", "projectId", "status", "createdAt", "updatedAt", "createdByIntentId", "latestIntentId", "suggestionSource", "history"].as_slice()),
    ] { for entry in entries { let item = object(entry, name)?; if item.keys().map(String::as_str).collect::<BTreeSet<_>>() != expected.iter().copied().collect() || !item.get("history").is_some_and(Value::is_array) { return Err(json_error("v2 owner history 或字段无效")); } } }
    for relation in relations { let relation = object(relation, "relation")?; if !knowledge.iter().any(|item| object(item, "knowledge").ok().and_then(|item| item.get("id")).and_then(Value::as_str) == relation.get("fromId").and_then(Value::as_str)) || !knowledge.iter().any(|item| object(item, "knowledge").ok().and_then(|item| item.get("id")).and_then(Value::as_str) == relation.get("toId").and_then(Value::as_str)) { return Err(json_error("v2 relation endpoint 缺失")); } }
    Ok(semantic.to_owned())
}

fn preflight_package(bytes: Vec<u8>) -> Result<PreflightedPackage, String> {
    if bytes.is_empty() || bytes.len() as u64 > MAX_PACKAGE_BYTES {
        return Err(json_error("交换包为空或超过 128 MiB 限制"));
    }
    let package_hash = sha256(&bytes);
    let mut archive = ZipArchive::new(Cursor::new(&bytes)).map_err(|_| json_error("ZIP 包无效"))?;
    if archive.is_empty() || archive.len() > MAX_ENTRIES {
        return Err(json_error("ZIP entry 数量不安全"));
    }
    let mut entries = BTreeMap::new();
    let mut total = 0u64;
    for index in 0..archive.len() {
        let mut file = archive
            .by_index(index)
            .map_err(|_| json_error("ZIP entry 无效"))?;
        let name = file.name().to_owned();
        if !safe_entry(&name)
            || !file.is_file()
            || entries.contains_key(&name)
            || file.size() > MAX_PACKAGE_BYTES
        {
            return Err(json_error("ZIP 包含不安全或重复 entry"));
        }
        total = total
            .checked_add(file.size())
            .ok_or_else(|| json_error("ZIP size overflow"))?;
        if total > MAX_PACKAGE_BYTES {
            return Err(json_error("ZIP 解压总量超过限制"));
        }
        let mut content = Vec::with_capacity(file.size() as usize);
        file.read_to_end(&mut content)
            .map_err(|_| json_error("ZIP entry 读取失败"))?;
        if content.len() as u64 != file.size() {
            return Err(json_error("ZIP entry size 不符"));
        }
        entries.insert(name, content);
    }
    let manifest: Value = serde_json::from_slice(
        entries
            .get("manifest.json")
            .ok_or_else(|| json_error("缺少 manifest"))?,
    )
    .map_err(|_| json_error("manifest JSON 无效"))?;
    let manifest = object(&manifest, "manifest")?;
    if manifest.get("format").and_then(Value::as_str) != Some("nfai.exchange.package")
        || manifest.get("packageVersion").and_then(Value::as_u64) != Some(1)
        || manifest.get("exchangeVersion").and_then(Value::as_u64) != Some(1)
    {
        return Err(json_error("manifest 版本不支持"));
    }
    let files = require_array(manifest, "files")?;
    if files.len() + 1 != entries.len() {
        return Err(json_error("包包含未知或未列出的 entry"));
    }
    for file in files {
        let file = object(file, "manifest file")?;
        let path = require_string(file, "path")?;
        let content = entries
            .get(path)
            .ok_or_else(|| json_error("manifest entry 不存在"))?;
        if file.get("byteCount").and_then(Value::as_u64) != Some(content.len() as u64)
            || require_string(file, "sha256")? != sha256(content)
        {
            return Err(json_error("manifest hash 或大小不符"));
        }
    }
    let export = manifest
        .get("export")
        .cloned()
        .ok_or_else(|| json_error("manifest export 缺失"))?;
    let exchange = json!({
        "format": "nfai.exchange", "version": 1, "export": export,
        "projects": serde_json::from_slice::<Value>(entries.get(PAYLOADS[0]).ok_or_else(|| json_error("payload 缺失"))?).map_err(|_| json_error("projects JSON 无效"))?,
        "conversations": serde_json::from_slice::<Value>(entries.get(PAYLOADS[1]).ok_or_else(|| json_error("payload 缺失"))?).map_err(|_| json_error("conversations JSON 无效"))?,
        "knowledge": serde_json::from_slice::<Value>(entries.get(PAYLOADS[2]).ok_or_else(|| json_error("payload 缺失"))?).map_err(|_| json_error("knowledge JSON 无效"))?,
        "memory": serde_json::from_slice::<Value>(entries.get(PAYLOADS[3]).ok_or_else(|| json_error("payload 缺失"))?).map_err(|_| json_error("memory JSON 无效"))?,
        "relations": serde_json::from_slice::<Value>(entries.get(PAYLOADS[4]).ok_or_else(|| json_error("payload 缺失"))?).map_err(|_| json_error("relations JSON 无效"))?,
        "settings": serde_json::from_slice::<Value>(entries.get(PAYLOADS[5]).ok_or_else(|| json_error("payload 缺失"))?).map_err(|_| json_error("settings JSON 无效"))?
    });
    let (high_sensitive, message_count) = validate_exchange(&exchange)?;
    let assets = collect_asset_refs(&exchange)?;
    if entries
        .keys()
        .filter(|key| key.as_str() != "manifest.json")
        .map(String::as_str)
        .collect::<BTreeSet<_>>()
        != files
            .iter()
            .filter_map(|file| file.get("path").and_then(Value::as_str))
            .collect::<BTreeSet<_>>()
    {
        return Err(json_error("manifest entry 集合不一致"));
    }
    let mut asset_bytes = 0;
    let mut copied_assets = BTreeMap::new();
    for entry in &assets {
        let content = entries
            .get(entry)
            .ok_or_else(|| json_error("引用 asset 不存在"))?;
        let hash = entry
            .strip_prefix("assets/")
            .ok_or_else(|| json_error("asset entry 无效"))?;
        if sha256(content) != hash {
            return Err(json_error("asset content hash 不符"));
        }
        asset_bytes += content.len() as u64;
        copied_assets.insert(entry.clone(), content.clone());
    }
    let root = object(&exchange, "exchange")?;
    let export = object(
        root.get("export")
            .ok_or_else(|| json_error("export 缺失"))?,
        "export",
    )?;
    Ok(PreflightedPackage {
        bytes,
        exchange: exchange.clone(),
        assets: copied_assets,
        receipt: PreflightReceipt {
            staging_id: package_hash.clone(),
            package_hash,
            byte_count: total,
            semantic_hash: require_string(export, "semanticHash")?.to_owned(),
            origin: object(
                export
                    .get("origin")
                    .ok_or_else(|| json_error("origin 缺失"))?,
                "origin",
            )?
            .get("platform")
            .and_then(Value::as_str)
            .unwrap_or_default()
            .to_owned(),
            sensitivity: require_string(export, "sensitivity")?.to_owned(),
            project_count: require_array(root, "projects")?.len(),
            conversation_count: require_array(root, "conversations")?.len(),
            message_count,
            knowledge_count: require_array(root, "knowledge")?.len(),
            memory_count: require_array(root, "memory")?.len(),
            relation_count: require_array(root, "relations")?.len(),
            asset_count: assets.len(),
            asset_byte_count: asset_bytes,
            high_sensitive,
        },
    })
}

fn chatgpt_timestamp(value: Option<&Value>, field: &str) -> Result<i64, String> {
    let seconds = match value {
        Some(Value::Number(number)) => number.as_f64(),
        Some(Value::String(text)) => text.parse::<f64>().ok(),
        _ => None,
    }
    .filter(|value| value.is_finite() && *value >= 0.0)
    .ok_or_else(|| json_error(&format!("ChatGPT {field} 无效")))?;
    let millis = seconds * 1000.0;
    if millis > i64::MAX as f64 {
        return Err(json_error("ChatGPT 时间超出安全范围"));
    }
    Ok(millis as i64)
}

fn chatgpt_id(value: &str) -> Result<&str, String> {
    if value.is_empty()
        || value.len() > 200
        || !value
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'.' | b'_' | b':' | b'-'))
    {
        return Err(json_error("ChatGPT opaque ID 无效"));
    }
    Ok(value)
}

/// Strictly maps the documented ChatGPT export subset. Unknown fields are inertly ignored;
/// unknown roles/content and unsafe trees reject only the candidate supplied to this function.
fn parse_chatgpt_export_conversation_with_scope(value: &Value, allow_external_parent: bool) -> Result<ChatGptExportCandidate, String> {
    let conversation = object(value, "ChatGPT conversation")?;
    let source_conversation_id = chatgpt_id(require_string(conversation, "id")?)?.to_owned();
    let title = require_string(conversation, "title")?.trim();
    if title.is_empty() || title.chars().count() > 120 {
        return Err(json_error("ChatGPT title 无效"));
    }
    let created_at_ms = chatgpt_timestamp(conversation.get("create_time"), "create_time")?;
    let updated_at_ms = chatgpt_timestamp(conversation.get("update_time"), "update_time")?;
    let mapping = object(
        conversation
            .get("mapping")
            .ok_or_else(|| json_error("ChatGPT mapping 缺失"))?,
        "ChatGPT mapping",
    )?;
    if mapping.is_empty() || mapping.len() > CHATGPT_EXPORT_MAX_MAPPING_NODES {
        return Err(json_error("ChatGPT mapping 节点数无效"));
    }
    // Older export revisions omit every `children` field.  We may rebuild only those missing
    // edges from parent IDs in this same mapping; an explicit children field remains strict.
    let mut raw_nodes = BTreeMap::<String, (&Map<String, Value>, Option<String>, Option<Vec<String>>)>::new();
    for (id, raw) in mapping {
        chatgpt_id(id)?;
        let node = object(raw, "ChatGPT mapping node")?;
        let parent = match node.get("parent") {
            None | Some(Value::Null) => None,
            Some(Value::String(value)) => Some(chatgpt_id(value)?.to_owned()),
            _ => return Err(json_error("ChatGPT parent 无效")),
        };
        let children = node.get("children").map(|value| value.as_array().ok_or_else(|| json_error("ChatGPT children 无效"))).transpose()?.map(|values| values.iter().map(|child| child.as_str().ok_or_else(|| json_error("ChatGPT child 无效")).and_then(chatgpt_id).map(str::to_owned)).collect::<Result<Vec<_>, _>>()).transpose()?;
        if children.as_ref().is_some_and(|items| items.len() != items.iter().collect::<BTreeSet<_>>().len()) { return Err(json_error("ChatGPT child 重复")); }
        raw_nodes.insert(id.clone(), (node, parent, children));
    }
    for (_, parent, _) in raw_nodes.values() { if parent.as_ref().is_some_and(|value| !raw_nodes.contains_key(value)) && !allow_external_parent { return Err(json_error("ChatGPT parent 不存在")); } }
    let mut derived_children = BTreeMap::<String, Vec<String>>::new();
    for (id, (_, parent, _)) in &raw_nodes { if let Some(parent) = parent.as_ref().filter(|parent| raw_nodes.contains_key(*parent)) { derived_children.entry(parent.clone()).or_default().push(id.clone()); } }
    let mut nodes = BTreeMap::<String, ChatGptParsedNode>::new();
    for (id, (node, parent, explicit_children)) in raw_nodes {
        let derived = derived_children.remove(&id).unwrap_or_default();
        let has_explicit_children = explicit_children.is_some();
        let children = explicit_children.unwrap_or(derived.clone());
        if has_explicit_children && children.iter().collect::<BTreeSet<_>>() != derived.iter().collect::<BTreeSet<_>>() { return Err(json_error("ChatGPT parent/children 不一致")); }
        nodes.insert(id, (node, parent, children));
    }
    let roots = nodes
        .iter()
        .filter_map(|(id, (_, parent, _))| (parent.is_none() || (allow_external_parent && parent.as_ref().is_some_and(|value| !nodes.contains_key(value)))).then_some(id.clone()))
        .collect::<Vec<_>>();
    if (!allow_external_parent && roots.len() != 1) || roots.is_empty() {
        return Err(json_error("ChatGPT tree 必须有唯一根"));
    }
    for (id, (_, _parent, children)) in &nodes {
        for child in children {
            if !nodes.contains_key(child) || nodes.get(child).is_some_and(|(_, parent, _)| parent.as_deref() != Some(id)) {
                return Err(json_error("ChatGPT parent/children 不一致"));
            }
        }
    }
    fn visit(
        id: &str,
        nodes: &BTreeMap<String, ChatGptParsedNode>,
        seen: &mut BTreeSet<String>,
        ordered: &mut Vec<String>,
    ) -> Result<(), String> {
        if !seen.insert(id.to_owned()) {
            return Err(json_error("ChatGPT tree 有环"));
        }
        ordered.push(id.to_owned());
        for child in &nodes
            .get(id)
            .ok_or_else(|| json_error("ChatGPT node 缺失"))?
            .2
        {
            visit(child, nodes, seen, ordered)?;
        }
        Ok(())
    }
    let mut seen = BTreeSet::new();
    let mut ordered = Vec::new();
    for root in &roots { visit(root, &nodes, &mut seen, &mut ordered)?; }
    for id in nodes.keys().filter(|id| !seen.contains(*id)).cloned().collect::<Vec<_>>() { visit(&id, &nodes, &mut seen, &mut ordered)?; }
    if seen.len() != nodes.len() {
        return Err(json_error("ChatGPT tree 有孤立节点"));
    }
    let mut messages = Vec::new();
    for (position, id) in ordered.iter().enumerate() {
        let (node, parent, _) = nodes.get(id).expect("visited node exists");
        let Some(message) = node.get("message").filter(|value| !value.is_null()) else {
            continue;
        };
        let message = object(message, "ChatGPT message")?;
        let role = object(
            message
                .get("author")
                .ok_or_else(|| json_error("ChatGPT author 缺失"))?,
            "ChatGPT author",
        )?
        .get("role")
        .and_then(Value::as_str)
        .ok_or_else(|| json_error("ChatGPT role 缺失"))?;
        if !matches!(role, "user" | "assistant" | "tool") {
            return Err(json_error("ChatGPT role 不受支持"));
        }
        let parts = object(
            message
                .get("content")
                .ok_or_else(|| json_error("ChatGPT content 缺失"))?,
            "ChatGPT content",
        )?
        .get("parts")
        .and_then(Value::as_array)
        .ok_or_else(|| json_error("ChatGPT content.parts 无效"))?;
        let text = parts
            .iter()
            .map(|part| {
                part.as_str()
                    .ok_or_else(|| json_error("ChatGPT content 类型不受支持"))
            })
            .collect::<Result<Vec<_>, _>>()?
            .join("\n")
            .trim()
            .to_owned();
        if text.is_empty() {
            continue;
        }
        if text.chars().count() > CHATGPT_EXPORT_MAX_TEXT_CODEPOINTS {
            return Err(json_error("ChatGPT 文本超过上限"));
        }
        let created_at_ms = message
            .get("create_time")
            .map(|value| chatgpt_timestamp(Some(value), "message.create_time"))
            .transpose()?
            .unwrap_or(created_at_ms);
        let imported_model = message
            .get("metadata")
            .and_then(Value::as_object)
            .and_then(|metadata| metadata.get("model_slug"))
            .and_then(Value::as_str)
            .map(str::trim)
            .filter(|value| !value.is_empty())
            .map(|value| value.chars().take(120).collect());
        messages.push(ChatGptExportMessage {
            source_id: id.clone(),
            parent_source_id: parent.clone(),
            sibling_position: position,
            role: role.to_owned(),
            text,
            created_at_ms,
            imported_model,
        });
    }
    let visible = messages.iter().map(|message| message.source_id.clone()).collect::<BTreeSet<_>>();
    for message in &mut messages {
        let mut parent = message.parent_source_id.clone();
        while let Some(parent_id) = parent.clone() {
            if visible.contains(&parent_id) || !nodes.contains_key(&parent_id) {
                break;
            }
            parent = nodes.get(&parent_id).and_then(|(_, parent, _)| parent.clone());
        }
        message.parent_source_id = parent;
    }
    if messages.is_empty() {
        return Err(json_error("ChatGPT 会话没有可安全导入的文本"));
    }
    let content_hash = sha256(canonical_json(&json!({"id": source_conversation_id, "messages": messages.iter().map(|message| json!({"id":message.source_id,"parent":message.parent_source_id,"role":message.role,"text":message.text,"created":message.created_at_ms,"model":message.imported_model})).collect::<Vec<_>>() }))?.as_bytes());
    Ok(ChatGptExportCandidate {
        source_conversation_id,
        title: title.to_owned(),
        created_at_ms,
        updated_at_ms,
        messages,
        content_hash,
    })
}

fn parse_chatgpt_export_conversation(value: &Value) -> Result<ChatGptExportCandidate, String> { parse_chatgpt_export_conversation_with_scope(value, false) }

/// Rejects duplicate object keys and deeply nested payloads before serde_json materialises an
/// object (serde_json otherwise keeps only the last duplicate key).
fn validate_chatgpt_strict_json(bytes: &[u8]) -> Result<(), String> {
    struct Scanner<'a> {
        bytes: &'a [u8],
        at: usize,
    }
    impl<'a> Scanner<'a> {
        fn whitespace(&mut self) {
            while self.at < self.bytes.len() && self.bytes[self.at].is_ascii_whitespace() {
                self.at += 1;
            }
        }
        fn string(&mut self) -> Result<String, String> {
            let start = self.at;
            if self.bytes.get(self.at) != Some(&b'"') {
                return Err(json_error("ChatGPT JSON 字符串无效"));
            }
            self.at += 1;
            let mut escaped = false;
            while let Some(byte) = self.bytes.get(self.at) {
                self.at += 1;
                if escaped {
                    escaped = false;
                    continue;
                }
                if *byte == b'\\' {
                    escaped = true;
                    continue;
                }
                if *byte == b'"' {
                    return serde_json::from_slice::<String>(&self.bytes[start..self.at])
                        .map_err(|_| json_error("ChatGPT JSON 字符串无效"));
                }
                if *byte < 0x20 {
                    return Err(json_error("ChatGPT JSON 控制字符无效"));
                }
            }
            Err(json_error("ChatGPT JSON 字符串未闭合"))
        }
        fn scalar(&mut self) -> Result<(), String> {
            let start = self.at;
            while let Some(byte) = self.bytes.get(self.at) {
                if matches!(*byte, b',' | b']' | b'}') || byte.is_ascii_whitespace() {
                    break;
                }
                self.at += 1;
            }
            if start == self.at {
                return Err(json_error("ChatGPT JSON 标量无效"));
            }
            serde_json::from_slice::<Value>(&self.bytes[start..self.at])
                .map(|_| ())
                .map_err(|_| json_error("ChatGPT JSON 标量无效"))
        }
        fn value(&mut self, depth: usize) -> Result<(), String> {
            if depth > 24 {
                return Err(json_error("ChatGPT JSON 嵌套超过上限"));
            }
            self.whitespace();
            match self.bytes.get(self.at) {
                Some(b'"') => {
                    self.string()?;
                    Ok(())
                }
                Some(b'{') => self.object(depth + 1),
                Some(b'[') => self.array(depth + 1),
                Some(_) => self.scalar(),
                None => Err(json_error("ChatGPT JSON 意外结束")),
            }
        }
        fn object(&mut self, depth: usize) -> Result<(), String> {
            self.at += 1;
            self.whitespace();
            let mut keys = BTreeSet::new();
            if self.bytes.get(self.at) == Some(&b'}') {
                self.at += 1;
                return Ok(());
            }
            loop {
                self.whitespace();
                let key = self.string()?;
                if !keys.insert(key) {
                    return Err(json_error("ChatGPT JSON 存在重复键"));
                }
                self.whitespace();
                if self.bytes.get(self.at) != Some(&b':') {
                    return Err(json_error("ChatGPT JSON 对象缺少冒号"));
                }
                self.at += 1;
                self.value(depth)?;
                self.whitespace();
                match self.bytes.get(self.at) {
                    Some(b',') => self.at += 1,
                    Some(b'}') => {
                        self.at += 1;
                        return Ok(());
                    }
                    _ => return Err(json_error("ChatGPT JSON 对象无效")),
                }
            }
        }
        fn array(&mut self, depth: usize) -> Result<(), String> {
            self.at += 1;
            self.whitespace();
            if self.bytes.get(self.at) == Some(&b']') {
                self.at += 1;
                return Ok(());
            }
            loop {
                self.value(depth)?;
                self.whitespace();
                match self.bytes.get(self.at) {
                    Some(b',') => self.at += 1,
                    Some(b']') => {
                        self.at += 1;
                        return Ok(());
                    }
                    _ => return Err(json_error("ChatGPT JSON 数组无效")),
                }
            }
        }
    }
    let mut scanner = Scanner { bytes, at: 0 };
    scanner.value(0)?;
    scanner.whitespace();
    if scanner.at != bytes.len() {
        return Err(json_error("ChatGPT JSON 尾随内容无效"));
    }
    Ok(())
}

fn parse_chatgpt_export_with_limit(bytes: &[u8], max_bytes: usize) -> Result<Vec<Result<ChatGptExportCandidate, String>>, String> {
    if bytes.is_empty() || bytes.len() > max_bytes {
        return Err(json_error("ChatGPT export 大小无效"));
    }
    std::str::from_utf8(bytes).map_err(|_| json_error("ChatGPT export 不是 UTF-8"))?;
    validate_chatgpt_strict_json(bytes)?;
    let root: Value =
        serde_json::from_slice(bytes).map_err(|_| json_error("ChatGPT export JSON 无效"))?;
    let conversations = root
        .as_array()
        .ok_or_else(|| json_error("ChatGPT export 顶层必须是数组"))?;
    if conversations.is_empty() || conversations.len() > CHATGPT_EXPORT_MAX_CONVERSATIONS {
        return Err(json_error("ChatGPT export 会话数量无效"));
    }
    Ok(conversations
        .iter()
        .map(parse_chatgpt_export_conversation)
        .collect())
}

fn parse_chatgpt_export(bytes: &[u8]) -> Result<Vec<Result<ChatGptExportCandidate, String>>, String> { parse_chatgpt_export_with_limit(bytes, CHATGPT_EXPORT_MAX_BYTES) }

fn parse_p6k_chatgpt_export_with_limit(bytes: &[u8], max_bytes: usize) -> Result<Vec<Result<ChatGptExportCandidate, String>>, String> {
    if bytes.is_empty() || bytes.len() > max_bytes { return Err(json_error("ChatGPT export 大小无效")); }
    std::str::from_utf8(bytes).map_err(|_| json_error("ChatGPT export 不是 UTF-8"))?;
    validate_chatgpt_strict_json(bytes)?;
    let root: Value = serde_json::from_slice(bytes).map_err(|_| json_error("ChatGPT export JSON 无效"))?;
    let conversations = root.as_array().ok_or_else(|| json_error("ChatGPT export 顶层必须是数组"))?;
    if conversations.is_empty() || conversations.len() > CHATGPT_EXPORT_MAX_CONVERSATIONS { return Err(json_error("ChatGPT export 会话数量无效")); }
    Ok(conversations.iter().map(|conversation| parse_chatgpt_export_conversation_with_scope(conversation, true)).collect())
}

fn p6k_find(parent: &mut [usize], at: usize) -> usize {
    if parent[at] != at { let root = p6k_find(parent, parent[at]); parent[at] = root; }
    parent[at]
}

fn p6k_union(parent: &mut [usize], left: usize, right: usize) {
    let left = p6k_find(parent, left); let right = p6k_find(parent, right);
    if left != right { parent[right] = left; }
}

/// P6-K may receive a provider ZIP split into multiple numbered conversation entries.  A source
/// message ID is globally unique in that one ZIP scope; cross-candidate parents are accepted only
/// when they resolve exactly once, form one acyclic graph and keep nondecreasing message time.
fn p6k_normalize_chatgpt_candidates(mut candidates: Vec<Result<ChatGptExportCandidate, String>>) -> Vec<Result<ChatGptExportCandidate, String>> {
    let mut eligible = candidates.iter().map(Result::is_ok).collect::<Vec<_>>();
    loop {
        let mut owners = BTreeMap::<String, (usize, i64)>::new();
        let mut invalid = BTreeSet::<usize>::new();
        for (candidate_index, candidate) in candidates.iter().enumerate().filter(|(index, _)| eligible[*index]) {
            let Ok(candidate) = candidate else { continue; };
            for message in &candidate.messages {
                if let Some((other, _)) = owners.insert(message.source_id.clone(), (candidate_index, message.created_at_ms)) { invalid.insert(candidate_index); invalid.insert(other); }
            }
        }
        for (candidate_index, candidate) in candidates.iter().enumerate().filter(|(index, _)| eligible[*index]) {
            let Ok(candidate) = candidate else { continue; };
            for message in &candidate.messages {
                if let Some(parent_id) = &message.parent_source_id {
                    match owners.get(parent_id) {
                        Some((_, parent_time)) if *parent_time <= message.created_at_ms => {}
                        _ => { invalid.insert(candidate_index); }
                    }
                }
            }
        }
        if invalid.is_empty() { break; }
        let mut changed = false;
        for index in invalid { if eligible[index] { eligible[index] = false; candidates[index] = Err(json_error("ChatGPT ZIP 全局图引用、角色或时间语义无效")); changed = true; } }
        if !changed { break; }
    }
    let mut owners = BTreeMap::<String, usize>::new();
    for (candidate_index, candidate) in candidates.iter().enumerate().filter(|(index, _)| eligible[*index]) {
        if let Ok(candidate) = candidate { for message in &candidate.messages { owners.insert(message.source_id.clone(), candidate_index); } }
    }
    let mut parent = (0..candidates.len()).collect::<Vec<_>>();
    for (candidate_index, candidate) in candidates.iter().enumerate().filter(|(index, _)| eligible[*index]) {
        if let Ok(candidate) = candidate { for message in &candidate.messages { if let Some(parent_id) = &message.parent_source_id { if let Some(owner) = owners.get(parent_id) { p6k_union(&mut parent, candidate_index, *owner); } } } }
    }
    let mut groups = BTreeMap::<usize, Vec<usize>>::new();
    for (index, is_eligible) in eligible.iter().enumerate().take(candidates.len()) { if *is_eligible { let root = p6k_find(&mut parent, index); groups.entry(root).or_default().push(index); } }
    for indices in groups.values() {
        let mut all_messages = BTreeMap::<String, ChatGptExportMessage>::new();
        let mut source_conversations = Vec::<String>::new();
        let mut title_source: Option<(String, String)> = None;
        let mut created_at_ms = i64::MAX; let mut updated_at_ms = i64::MIN;
        for index in indices {
            let Ok(candidate) = &candidates[*index] else { continue; };
            source_conversations.push(candidate.source_conversation_id.clone());
            if title_source.as_ref().is_none_or(|(source, _)| candidate.source_conversation_id < *source) { title_source = Some((candidate.source_conversation_id.clone(), candidate.title.clone())); }
            created_at_ms = created_at_ms.min(candidate.created_at_ms); updated_at_ms = updated_at_ms.max(candidate.updated_at_ms);
            for message in &candidate.messages { all_messages.insert(message.source_id.clone(), message.clone()); }
        }
        let mut children = BTreeMap::<String, Vec<String>>::new(); let mut roots = Vec::<String>::new(); let mut invalid = false;
        for message in all_messages.values() {
            if let Some(parent_id) = &message.parent_source_id { if all_messages.contains_key(parent_id) { children.entry(parent_id.clone()).or_default().push(message.source_id.clone()); } else { invalid = true; } } else { roots.push(message.source_id.clone()); }
        }
        if roots.len() != 1 { invalid = true; }
        for child_ids in children.values_mut() { child_ids.sort_by_key(|id| { let child = &all_messages[id]; (child.created_at_ms, child.sibling_position, child.source_id.clone()) }); }
        fn visit(id: &str, children: &BTreeMap<String, Vec<String>>, seen: &mut BTreeSet<String>, ordered: &mut Vec<String>) -> bool { if !seen.insert(id.to_owned()) { return false; } ordered.push(id.to_owned()); children.get(id).into_iter().flatten().all(|child| visit(child, children, seen, ordered)) }
        let mut seen = BTreeSet::new(); let mut ordered = Vec::new();
        if roots.first().is_none_or(|root| !visit(root, &children, &mut seen, &mut ordered)) || seen.len() != all_messages.len() { invalid = true; }
        if invalid {
            for index in indices { candidates[*index] = Err(json_error("ChatGPT ZIP 全局图必须是唯一无环可达树")); }
            continue;
        }
        source_conversations.sort();
        let graph_source = format!("p6k-chatgpt-graph-{}", &sha256(canonical_json(&json!({"sources":source_conversations})) .unwrap_or_default().as_bytes())[..24]);
        let messages = ordered.into_iter().enumerate().map(|(position, id)| { let mut message = all_messages[&id].clone(); message.sibling_position = position; message }).collect::<Vec<_>>();
        let content_hash = sha256(canonical_json(&json!({"source":graph_source,"messages":messages.iter().map(|message| json!({"id":message.source_id,"parent":message.parent_source_id,"role":message.role,"text":message.text,"created":message.created_at_ms,"model":message.imported_model})).collect::<Vec<_>>() })).unwrap_or_default().as_bytes());
        let merged = ChatGptExportCandidate { source_conversation_id: graph_source, title: title_source.map(|(_, title)| title).unwrap_or_else(|| "ChatGPT ZIP".into()), created_at_ms, updated_at_ms, messages, content_hash };
        let keeper = *indices.iter().min().unwrap_or(&0);
        candidates[keeper] = Ok(merged);
        for index in indices { if *index != keeper { candidates[*index] = Err(P6K_GRAPH_MERGED.into()); } }
    }
    candidates
}

fn claude_id(value: &str) -> Result<&str, String> {
    if value.is_empty()
        || value.len() > 200
        || !value
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || byte == b'-')
    {
        return Err(json_error("Claude UUID 无效"));
    }
    Ok(value)
}

/// Claude export times are ISO-8601. The parser is intentionally self-contained so importing a
/// local export does not add a time service or accept a locale-dependent timestamp.
fn claude_iso_timestamp(value: &str, field: &str) -> Result<i64, String> {
    let bytes = value.as_bytes();
    if bytes.len() < 20
        || bytes.get(4) != Some(&b'-')
        || bytes.get(7) != Some(&b'-')
        || bytes.get(10) != Some(&b'T')
        || bytes.get(13) != Some(&b':')
        || bytes.get(16) != Some(&b':')
    {
        return Err(json_error(&format!("Claude {field} 不是 ISO-8601")));
    }
    let number = |start: usize, end: usize| -> Option<i64> {
        std::str::from_utf8(&bytes[start..end]).ok()?.parse().ok()
    };
    let year = number(0, 4).ok_or_else(|| json_error(&format!("Claude {field} 无效")))?;
    let month = number(5, 7).ok_or_else(|| json_error(&format!("Claude {field} 无效")))?;
    let day = number(8, 10).ok_or_else(|| json_error(&format!("Claude {field} 无效")))?;
    let hour = number(11, 13).ok_or_else(|| json_error(&format!("Claude {field} 无效")))?;
    let minute = number(14, 16).ok_or_else(|| json_error(&format!("Claude {field} 无效")))?;
    let second = number(17, 19).ok_or_else(|| json_error(&format!("Claude {field} 无效")))?;
    if !(1..=12).contains(&month)
        || !(1..=31).contains(&day)
        || hour > 23
        || minute > 59
        || second > 59
    {
        return Err(json_error(&format!("Claude {field} 超出范围")));
    }
    let mut at = 19;
    let mut millis = 0i64;
    if bytes.get(at) == Some(&b'.') {
        at += 1;
        let start = at;
        while bytes.get(at).is_some_and(u8::is_ascii_digit) {
            at += 1;
        }
        let fraction = std::str::from_utf8(&bytes[start..at])
            .ok()
            .filter(|text| !text.is_empty())
            .ok_or_else(|| json_error(&format!("Claude {field} 小数无效")))?;
        millis = fraction
            .chars()
            .take(3)
            .collect::<String>()
            .parse::<i64>()
            .map_err(|_| json_error(&format!("Claude {field} 小数无效")))?;
        for _ in fraction.len()..3 {
            millis *= 10;
        }
    }
    let offset_minutes = match bytes.get(at) {
        Some(b'Z') if at + 1 == bytes.len() => 0,
        Some(sign @ (b'+' | b'-')) if at + 6 == bytes.len() && bytes.get(at + 3) == Some(&b':') => {
            let hours = number(at + 1, at + 3)
                .ok_or_else(|| json_error(&format!("Claude {field} 时区无效")))?;
            let minutes = number(at + 4, at + 6)
                .ok_or_else(|| json_error(&format!("Claude {field} 时区无效")))?;
            if hours > 23 || minutes > 59 {
                return Err(json_error(&format!("Claude {field} 时区无效")));
            }
            let raw = hours * 60 + minutes;
            if *sign == b'+' {
                raw
            } else {
                -raw
            }
        }
        _ => return Err(json_error(&format!("Claude {field} 时区无效"))),
    };
    // Howard Hinnant's civil-date conversion; valid Gregorian dates are required before a local
    // timestamp can become an immutable imported message time.
    let y = year - if month <= 2 { 1 } else { 0 };
    let era = if y >= 0 { y } else { y - 399 } / 400;
    let yoe = y - era * 400;
    let mp = month + if month > 2 { -3 } else { 9 };
    let doy = (153 * mp + 2) / 5 + day - 1;
    let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
    let days = era * 146097 + doe - 719468;
    days.checked_mul(86_400_000)
        .and_then(|base| {
            base.checked_add(hour * 3_600_000 + minute * 60_000 + second * 1_000 + millis)
        })
        .and_then(|base| base.checked_sub(offset_minutes * 60_000))
        .ok_or_else(|| json_error("Claude 时间超出安全范围"))
}

fn claude_text(message: &Map<String, Value>) -> Result<String, String> {
    let raw = message.get("text").or_else(|| message.get("content"));
    let text = raw
        .and_then(Value::as_str)
        .ok_or_else(|| json_error("Claude text/content 必须是文本"))?
        .trim();
    if text.is_empty() || text.chars().count() > CLAUDE_EXPORT_MAX_TEXT_CODEPOINTS {
        return Err(json_error("Claude 文本无效或超过上限"));
    }
    Ok(text.to_owned())
}

fn parse_claude_export_conversation_with_limit(value: &Value, max_messages: usize) -> Result<ClaudeExportCandidate, String> {
    let conversation = object(value, "Claude conversation")?;
    let source_conversation_id = claude_id(require_string(conversation, "uuid")?)?.to_owned();
    let title = match conversation.get("name") {
        None | Some(Value::Null) => "未命名 Claude 对话".to_owned(),
        Some(Value::String(value)) if value.trim().chars().count() <= 120 => {
            value.trim().to_owned()
        }
        _ => return Err(json_error("Claude name 无效")),
    };
    let title = if title.is_empty() {
        "未命名 Claude 对话".to_owned()
    } else {
        title
    };
    let created_at_ms =
        claude_iso_timestamp(require_string(conversation, "created_at")?, "created_at")?;
    let updated_at_ms =
        claude_iso_timestamp(require_string(conversation, "updated_at")?, "updated_at")?;
    let rows = require_array(conversation, "chat_messages")?;
    if rows.is_empty() || rows.len() > max_messages {
        return Err(json_error("Claude chat_messages 数量无效"));
    }
    let mut raw = BTreeMap::<String, (Option<String>, usize, String, String, i64)>::new();
    for (position, value) in rows.iter().enumerate() {
        let message = object(value, "Claude chat_message")?;
        let id = claude_id(require_string(message, "uuid")?)?.to_owned();
        let parent = match message.get("parent_message_uuid") {
            None | Some(Value::Null) => None,
            Some(Value::String(value)) => Some(claude_id(value)?.to_owned()),
            _ => return Err(json_error("Claude parent_message_uuid 无效")),
        };
        let role = match require_string(message, "sender")? {
            "human" => "USER",
            "assistant" => "ASSISTANT",
            "tool" => "TOOL",
            _ => return Err(json_error("Claude sender 不受支持")),
        }
        .to_owned();
        let text = claude_text(message)?;
        let created =
            claude_iso_timestamp(require_string(message, "created_at")?, "message.created_at")?;
        if raw
            .insert(id, (parent, position, role, text, created))
            .is_some()
        {
            return Err(json_error("Claude message UUID 重复"));
        }
    }
    // A single first message can point at an omitted parent outside the array.  Canonicalize
    // only that unique in-document root to None; every other missing parent makes the tree fail.
    let roots = raw
        .iter()
        .filter_map(|(id, (parent, ..))| (parent.is_none() || parent.as_ref().is_some_and(|value| !raw.contains_key(value))).then_some(id.clone()))
        .collect::<Vec<_>>();
    if roots.len() != 1 {
        return Err(json_error("Claude tree 必须有唯一根"));
    }
    for (id, (parent, ..)) in &raw {
        if parent.as_deref() == Some(id) {
            return Err(json_error("Claude tree 有环"));
        }
    }
    let mut children = BTreeMap::<String, Vec<String>>::new();
    for (id, (parent, ..)) in &raw {
        if let Some(parent) = parent {
            if raw.contains_key(parent) { children.entry(parent.clone()).or_default().push(id.clone()); }
        }
    }
    for list in children.values_mut() {
        list.sort_by_key(|id| raw[id].1);
    }
    fn visit(
        id: &str,
        children: &BTreeMap<String, Vec<String>>,
        seen: &mut BTreeSet<String>,
        ordered: &mut Vec<String>,
    ) -> Result<(), String> {
        if !seen.insert(id.to_owned()) {
            return Err(json_error("Claude tree 有环"));
        }
        ordered.push(id.to_owned());
        for child in children.get(id).into_iter().flatten() {
            visit(child, children, seen, ordered)?;
        }
        Ok(())
    }
    let mut seen = BTreeSet::new();
    let mut ordered = Vec::new();
    visit(&roots[0], &children, &mut seen, &mut ordered)?;
    if seen.len() != raw.len() {
        return Err(json_error("Claude tree 有不可达节点"));
    }
    let messages = ordered
        .into_iter()
        .map(|id| {
            let (parent, position, role, text, created) = &raw[&id];
            ClaudeExportMessage {
                source_id: id,
                parent_source_id: parent.clone().filter(|parent| raw.contains_key(parent)),
                sibling_position: *position,
                role: role.clone(),
                text: text.clone(),
                created_at_ms: *created,
                imported_model: None,
            }
        })
        .collect::<Vec<_>>();
    let content_hash = sha256(canonical_json(&json!({"id":source_conversation_id,"messages":messages.iter().map(|message| json!({"id":message.source_id,"parent":message.parent_source_id,"role":message.role,"text":message.text,"created":message.created_at_ms})).collect::<Vec<_>>() }))?.as_bytes());
    Ok(ClaudeExportCandidate {
        source_conversation_id,
        title,
        created_at_ms,
        updated_at_ms,
        messages,
        content_hash,
    })
}

fn parse_claude_export_with_limits(bytes: &[u8], max_bytes: usize, max_conversations: usize, max_messages: usize) -> Result<Vec<Result<ClaudeExportCandidate, String>>, String> {
    if bytes.is_empty() || bytes.len() > max_bytes {
        return Err(json_error("Claude export 大小无效"));
    }
    let text = std::str::from_utf8(bytes).map_err(|_| json_error("Claude export 不是 UTF-8"))?;
    validate_chatgpt_strict_json(text.strip_prefix('\u{feff}').unwrap_or(text).as_bytes())?;
    let root: Value = serde_json::from_str(text.strip_prefix('\u{feff}').unwrap_or(text))
        .map_err(|_| json_error("Claude export JSON 无效"))?;
    let conversations = root
        .as_array()
        .ok_or_else(|| json_error("Claude export 顶层必须是数组"))?;
    if conversations.is_empty() || conversations.len() > max_conversations {
        return Err(json_error("Claude export 会话数量无效"));
    }
    Ok(conversations
        .iter()
        .map(|conversation| parse_claude_export_conversation_with_limit(conversation, max_messages))
        .collect())
}

fn parse_claude_export_with_limit(bytes: &[u8], max_bytes: usize) -> Result<Vec<Result<ClaudeExportCandidate, String>>, String> { parse_claude_export_with_limits(bytes, max_bytes, CLAUDE_EXPORT_MAX_CONVERSATIONS, CLAUDE_EXPORT_MAX_MESSAGES) }

fn parse_claude_export(bytes: &[u8]) -> Result<Vec<Result<ClaudeExportCandidate, String>>, String> { parse_claude_export_with_limit(bytes, CLAUDE_EXPORT_MAX_BYTES) }

fn p6j_visible_text(message: &Map<String, Value>) -> Result<String, String> {
    let visible = message.get("content").and_then(Value::as_array).map(|blocks| {
        blocks.iter().filter_map(|block| block.as_object()).filter_map(|block| {
            let allowed = block.get("type").and_then(Value::as_str).map(|kind| kind == "text").unwrap_or(true);
            allowed.then(|| block.get("text").and_then(Value::as_str)).flatten()
        }).collect::<Vec<_>>().join("\n\n")
    }).unwrap_or_default();
    let text = if visible.trim().is_empty() { message.get("text").and_then(Value::as_str).unwrap_or("") } else { &visible };
    let text = text.trim();
    if text.is_empty() || text.chars().count() > CLAUDE_EXPORT_MAX_TEXT_CODEPOINTS { return Err(json_error("知识库消息没有安全可见文本")); }
    Ok(text.to_owned())
}

fn parse_nanfeng_knowledge_export_record(value: &Value) -> Result<NanfengKnowledgeExportCandidate, String> {
    let record = object(value, "知识库记录")?;
    let id = record.get("id").and_then(Value::as_i64).filter(|id| *id > 0).ok_or_else(|| json_error("知识库 record id 无效"))?;
    let title = require_string(record, "title")?.trim();
    if title.is_empty() || title.chars().count() > 120 || title.chars().any(char::is_control) { return Err(json_error("知识库 title 无效")); }
    let created_at_ms = claude_iso_timestamp(require_string(record, "createdAt")?, "createdAt")?;
    let updated_at_ms = claude_iso_timestamp(require_string(record, "updatedAt")?, "updatedAt")?;
    let source_text = require_string(record, "sourceText")?;
    let source: Value = serde_json::from_str(source_text).map_err(|_| json_error("知识库 sourceText 不是 JSON 对象"))?;
    let source = source.as_object().ok_or_else(|| json_error("该知识记录不是对话"))?;
    let rows = source.get("chat_messages").and_then(Value::as_array).ok_or_else(|| json_error("该知识记录不是对话"))?;
    if rows.is_empty() || rows.len() > CLAUDE_EXPORT_MAX_MESSAGES { return Err(json_error("知识库 chat_messages 数量无效")); }
    let source_conversation_id = format!("knowledge-record-{id}");
    let visible = rows.iter().map(|value| -> Result<Option<(String, String, i64)>, String> {
        let message = object(value, "知识库 chat_message")?;
        let role = match require_string(message, "sender")?.to_ascii_lowercase().as_str() { "human" | "user" => "USER", "assistant" => "ASSISTANT", "tool" => return Ok(None), _ => return Err(json_error("知识库 sender 不受支持")) }.to_owned();
        let created_at_ms = match message.get("created_at") { None | Some(Value::Null) => created_at_ms, Some(Value::String(value)) => claude_iso_timestamp(value, "message.created_at")?, _ => return Err(json_error("知识库 message.created_at 无效")) };
        Ok(Some((role, p6j_visible_text(message)?, created_at_ms)))
    }).collect::<Result<Vec<_>, String>>()?;
    let messages = visible.into_iter().flatten().enumerate().map(|(ordinal, (role, text, created_at_ms))| {
        let source_id = format!("{source_conversation_id}-message-{ordinal}");
        NanfengKnowledgeExportMessage { source_id: source_id.clone(), parent_source_id: (ordinal > 0).then(|| format!("{source_conversation_id}-message-{}", ordinal - 1)), sibling_position: 0, role, text, created_at_ms, imported_model: None }
    }).collect::<Vec<_>>();
    if messages.is_empty() { return Err(json_error("知识库 chat_messages 没有可见会话消息")); }
    let content_hash = sha256(canonical_json(&json!({"id":source_conversation_id,"messages":messages.iter().map(|message| json!({"id":message.source_id,"role":message.role,"text":message.text,"created":message.created_at_ms})).collect::<Vec<_>>() }))?.as_bytes());
    Ok(NanfengKnowledgeExportCandidate { source_conversation_id, title: title.to_owned(), created_at_ms, updated_at_ms, messages, content_hash })
}

fn parse_nanfeng_knowledge_export(bytes: &[u8]) -> Result<Vec<Result<NanfengKnowledgeExportCandidate, String>>, String> {
    if bytes.is_empty() || bytes.len() > CLAUDE_EXPORT_MAX_BYTES { return Err(json_error("知识库 export 大小无效")); }
    let text = std::str::from_utf8(bytes).map_err(|_| json_error("知识库 export 不是 UTF-8"))?;
    let text = text.strip_prefix('\u{feff}').unwrap_or(text); validate_chatgpt_strict_json(text.as_bytes())?;
    let root: Value = serde_json::from_str(text).map_err(|_| json_error("知识库 export JSON 无效"))?;
    let records = root.as_array().ok_or_else(|| json_error("知识库 export 顶层必须是数组"))?;
    if records.is_empty() || records.len() > CLAUDE_EXPORT_MAX_CONVERSATIONS { return Err(json_error("知识库 export 记录数量无效")); }
    Ok(records.iter().map(parse_nanfeng_knowledge_export_record).collect())
}

impl DesktopWorkspaceStore {
    fn open(root: PathBuf) -> Result<Self, String> {
        fs::create_dir_all(root.join("staging")).map_err(|_| json_error("无法创建私有 staging"))?;
        fs::create_dir_all(root.join("packages"))
            .map_err(|_| json_error("无法创建私有 package 存储"))?;
        fs::create_dir_all(root.join("assets"))
            .map_err(|_| json_error("无法创建私有 asset 存储"))?;
        let p7e_isolated_store = p7e_isolated_workspace_v1::IsolatedWorkspaceStore::open(
            root.join("p7e-isolated-workspace-v1"),
        )
        .map_err(|_| json_error("无法创建 P7E 隔离工作区"))?;
        let p8_agent_ledger =
            p8_agent_ledger_v1::AgentLedgerStore::open(root.join("p8-agent-ledger-v1"))
                .map_err(|_| json_error("无法创建 P8 Agent 本地账本"))?;
        let store = Self {
            database: root.join("workspace.sqlite3"),
            root,
            p7e_isolated_store,
            p8_agent_ledger,
        };
        store.migrate()?;
        store.run_startup_attachment_maintenance()?;
        Ok(store)
    }

    fn p6e_acceptance_receipt(
        &self,
    ) -> Result<Option<P6eTemporaryMaintenanceAcceptanceReceipt>, String> {
        let path = self.root.join(P6E_ACCEPTANCE_RECEIPT_FILE);
        if !path.exists() {
            return Ok(None);
        }
        let text = fs::read_to_string(path).map_err(|_| json_error("P6-E 验收回执无法读取"))?;
        serde_json::from_str(&text)
            .map(Some)
            .map_err(|_| json_error("P6-E 验收回执无效"))
    }

    fn p6e_ordinary_surface_record_count(&self) -> Result<i64, String> {
        self.connection()?
            .query_row(
                "SELECT
                    (SELECT COUNT(*) FROM workspaces) +
                    (SELECT COUNT(*) FROM workspace_exchange) +
                    (SELECT COUNT(*) FROM workspace_assets) +
                    (SELECT COUNT(*) FROM import_journal) +
                    (SELECT COUNT(*) FROM domain_intents) +
                    (SELECT COUNT(*) FROM object_provenance) +
                    (SELECT COUNT(*) FROM model_metadata) +
                    (SELECT COUNT(*) FROM desktop_conversation_attachments) +
                    (SELECT COUNT(*) FROM sync_account_metadata) +
                    (SELECT COUNT(*) FROM sync_intents)",
                [],
                |row| row.get(0),
            )
            .map_err(|_| json_error("P6-E 验收无法检查普通表面"))
    }

    /// The only Desktop P6-E time-controlled path.  It is not parameterized, uses a fixed UTC
    /// Clock equivalent, and can only be reached after `run` has selected the dedicated fixture
    /// root.  Normal workspaces and the production app-data root are never read or written.
    fn run_p6e_temporary_maintenance_acceptance(
        &self,
    ) -> Result<P6eTemporaryMaintenanceAcceptanceReceipt, String> {
        let recovery_count: i64 = self
            .connection()?
            .query_row(
                "SELECT COUNT(*) FROM desktop_temporary_recovery",
                [],
                |row| row.get(0),
            )
            .map_err(|_| json_error("P6-E 验收无法检查临时恢复记录"))?;
        if recovery_count != 0 {
            return Err(json_error("P6-E 验收夹具拒绝覆盖已有临时聊天"));
        }
        let normal_count = self.p6e_ordinary_surface_record_count()?;
        if normal_count != 0 {
            return Err(json_error("P6-E 验收根目录不纯净"));
        }

        const START: i64 = 1_786_665_600_000; // 2026-08-14T00:00:00Z
        let entered = self.enter_or_restore_temporary_at(None, START)?;
        self.update_temporary_at(
            TemporaryConversationUpdateArgs {
                temporary_id: entered.temporary_id.clone(),
                draft: String::new(),
                model_override_id: Some("local.p6e-acceptance".into()),
            },
            START,
        )?;
        let fixture = self.root.join("staging").join("p6e-acceptance.txt");
        fs::write(&fixture, b"p6e acceptance private copy")
            .map_err(|_| json_error("P6-E 验收私有附件无法创建"))?;
        let attached = self.import_temporary_attachment_at(
            DesktopTemporaryAttachmentImportArgs {
                temporary_id: entered.temporary_id.clone(),
                selected_path: fixture.to_string_lossy().into_owned(),
            },
            START,
        )?;
        fs::remove_file(&fixture).map_err(|_| json_error("P6-E 验收私有附件无法收口"))?;
        let attachment_sha256 = attached
            .attachments
            .first()
            .map(|item| item.sha256.clone())
            .ok_or_else(|| json_error("P6-E 验收附件引用缺失"))?;
        self.update_temporary_at(
            TemporaryConversationUpdateArgs {
                temporary_id: entered.temporary_id.clone(),
                draft: "P6E_ACCEPTANCE_MESSAGE".into(),
                model_override_id: Some("local.p6e-acceptance".into()),
            },
            START,
        )?;
        self.append_temporary_message_at(
            TemporaryConversationAppendArgs {
                temporary_id: entered.temporary_id.clone(),
            },
            START,
        )?;

        self.prune_temporary_at(START + ATTACHMENT_RETENTION_MILLIS - 1)?;
        let before = self.read_temporary_recovery_at(START + ATTACHMENT_RETENTION_MILLIS - 1)?;
        self.prune_temporary_at(START + ATTACHMENT_RETENTION_MILLIS)?;
        let removed = self
            .read_temporary_recovery_at(START + ATTACHMENT_RETENTION_MILLIS)?
            .is_none();
        let ordinary_after = self.p6e_ordinary_surface_record_count()?;
        let receipt = P6eTemporaryMaintenanceAcceptanceReceipt {
            retained_at_23h59: before.is_some(),
            removed_at_24h: removed,
            attachment_removed_at_24h: !self.root.join("assets").join(attachment_sha256).exists(),
            message_present_before_expiry: before.as_ref().is_some_and(|item| {
                item.messages
                    .iter()
                    .any(|message| message.text == "P6E_ACCEPTANCE_MESSAGE")
            }),
            model_override_present_before_expiry: before
                .as_ref()
                .and_then(|item| item.model_override_id.as_deref())
                == Some("local.p6e-acceptance"),
            ordinary_surfaces_clean: ordinary_after == 0,
        };
        fs::write(
            self.root.join(P6E_ACCEPTANCE_RECEIPT_FILE),
            serde_json::to_vec(&receipt).map_err(|_| json_error("P6-E 验收回执无法编码"))?,
        )
        .map_err(|_| json_error("P6-E 验收回执无法保存"))?;
        Ok(receipt)
    }

    /// P8-B's explicit invisible state entrance. It cannot schedule or execute an Agent tool.
    #[allow(
        dead_code,
        reason = "P8-B is production-wired but intentionally absent from the Tauri/UI surface"
    )]
    fn p8_b_read_only_agent_ledger_status(&self) -> p8_agent_ledger_v1::ReadOnlyLedgerStatus {
        self.p8_agent_ledger.read_only_status()
    }

    /// Internal P7-E ownership bridge.  This remains deliberately absent from the Tauri invoke
    /// handler: no UI may submit recovery material or open a remote document through this method.
    #[allow(
        dead_code,
        reason = "No P7-E Tauri command is authorized yet; the owner bridge is integration-tested only"
    )]
    fn open_p7e_to_new_isolated_workspace(
        &self,
        envelope: &str,
        recovery: &str,
        expected_app: &str,
        expected_document: &str,
        minimum_revision: u64,
        workspace_id: &str,
    ) -> Result<String, String> {
        self.p7e_isolated_store.open_to_new_workspace(
            envelope,
            recovery,
            expected_app,
            expected_document,
            minimum_revision,
            workspace_id,
        )
    }

    #[allow(
        dead_code,
        reason = "No P7-E Tauri command is authorized yet; the owner bridge is integration-tested only"
    )]
    fn read_p7e_isolated_workspace(
        &self,
        workspace_id: &str,
    ) -> Result<p7e_isolated_workspace_v1::SemanticSnapshot, String> {
        self.p7e_isolated_store
            .read_workspace_snapshot(workspace_id)
    }

    #[allow(
        dead_code,
        reason = "No P7-E Tauri command is authorized yet; the owner bridge is integration-tested only"
    )]
    fn seal_p7e_isolated_workspace(
        &self,
        workspace_id: &str,
        recovery: &str,
        data_key: &[u8],
    ) -> Result<String, String> {
        self.p7e_isolated_store
            .seal_workspace(workspace_id, recovery, data_key)
    }

    fn connection(&self) -> Result<Connection, String> {
        let connection =
            Connection::open(&self.database).map_err(|_| json_error("无法打开 SQLite 工作区"))?;
        connection
            .pragma_update(None, "foreign_keys", "ON")
            .map_err(|_| json_error("无法启用 SQLite foreign keys"))?;
        connection
            .busy_timeout(std::time::Duration::from_secs(3))
            .map_err(|_| json_error("无法设置 SQLite busy timeout"))?;
        Ok(connection)
    }

    fn migrate(&self) -> Result<(), String> {
        let mut connection = self.connection()?;
        let current: u32 = connection
            .pragma_query_value(None, "user_version", |row| row.get(0))
            .map_err(|_| json_error("无法读取 SQLite schema version"))?;
        if current > 20 {
            return Err(json_error("SQLite schema 版本比当前客户端更新"));
        }
        if current == 0 {
            let transaction = connection
                .transaction()
                .map_err(|_| json_error("无法开启 SQLite migration"))?;
            transaction.execute_batch("CREATE TABLE workspaces (id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, semantic_hash TEXT NOT NULL, package_hash TEXT NOT NULL, created_at TEXT NOT NULL); CREATE TABLE workspace_exchange (workspace_id TEXT PRIMARY KEY NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE, exchange_json TEXT NOT NULL); CREATE TABLE workspace_assets (workspace_id TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE, sha256 TEXT NOT NULL, byte_count INTEGER NOT NULL, PRIMARY KEY(workspace_id, sha256)); CREATE TABLE import_journal (package_hash TEXT PRIMARY KEY NOT NULL, workspace_id TEXT NOT NULL REFERENCES workspaces(id), semantic_hash TEXT NOT NULL, committed_at TEXT NOT NULL); CREATE TABLE window_layout (id INTEGER PRIMARY KEY CHECK(id = 1), inspector_open INTEGER NOT NULL, left_tree_open INTEGER NOT NULL);") .map_err(|_| json_error("SQLite migration 1 失败"))?;
            transaction
                .pragma_update(None, "user_version", 1)
                .map_err(|_| json_error("无法写入 SQLite schema version"))?;
            transaction
                .commit()
                .map_err(|_| json_error("SQLite migration 无法提交"))?;
        }
        if current < 2 {
            let transaction = connection
                .transaction()
                .map_err(|_| json_error("无法开启 SQLite migration 2"))?;
            transaction.execute_batch("CREATE TABLE domain_intents (intent_id TEXT PRIMARY KEY NOT NULL, workspace_id TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE, entity TEXT NOT NULL, action TEXT NOT NULL, object_id TEXT NOT NULL, expected_revision INTEGER, before_exchange_json TEXT NOT NULL, after_exchange_json TEXT NOT NULL, before_semantic_hash TEXT NOT NULL, after_semantic_hash TEXT NOT NULL, undone INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL); CREATE INDEX domain_intents_workspace_order ON domain_intents(workspace_id, intent_id); CREATE TABLE object_provenance (workspace_id TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE, entity TEXT NOT NULL, object_id TEXT NOT NULL, source TEXT NOT NULL, origin_semantic_hash TEXT, imported_revision INTEGER, edited_revision INTEGER, PRIMARY KEY(workspace_id, entity, object_id)); CREATE TABLE model_metadata (workspace_id TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE, provider_id TEXT NOT NULL, model_id TEXT NOT NULL, revision INTEGER NOT NULL, metadata_json TEXT NOT NULL, updated_at TEXT NOT NULL, PRIMARY KEY(workspace_id, provider_id, model_id));").map_err(|_| json_error("SQLite migration 2 失败"))?;
            transaction
                .pragma_update(None, "user_version", 2)
                .map_err(|_| json_error("无法写入 SQLite schema version 2"))?;
            transaction
                .commit()
                .map_err(|_| json_error("SQLite migration 2 无法提交"))?;
        }
        if current < 3 {
            let transaction = connection
                .transaction()
                .map_err(|_| json_error("无法开启 SQLite migration 3"))?;
            transaction.execute_batch("ALTER TABLE domain_intents ADD COLUMN result_revision INTEGER NOT NULL DEFAULT 0;").map_err(|_| json_error("SQLite migration 3 失败"))?;
            transaction
                .pragma_update(None, "user_version", 3)
                .map_err(|_| json_error("无法写入 SQLite schema version 3"))?;
            transaction
                .commit()
                .map_err(|_| json_error("SQLite migration 3 无法提交"))?;
        }
        if current < 4 {
            let transaction = connection
                .transaction()
                .map_err(|_| json_error("无法开启 SQLite migration 4"))?;
            transaction.execute_batch("CREATE TABLE sync_account_metadata (account_ref TEXT PRIMARY KEY NOT NULL, state TEXT NOT NULL, revision INTEGER NOT NULL, key_alias_ref TEXT, wrapped_key_ref TEXT, wrapped_key_sha256 TEXT, direction_fact TEXT, last_error TEXT, updated_at TEXT NOT NULL); CREATE TABLE sync_intents (intent_id TEXT PRIMARY KEY NOT NULL, account_ref TEXT NOT NULL, expected_revision INTEGER, resulting_revision INTEGER NOT NULL, resulting_state TEXT NOT NULL, created_at TEXT NOT NULL); CREATE INDEX sync_intents_account_ref ON sync_intents(account_ref);").map_err(|_| json_error("SQLite migration 4 失败"))?;
            transaction
                .pragma_update(None, "user_version", 4)
                .map_err(|_| json_error("无法写入 SQLite schema version 4"))?;
            transaction
                .commit()
                .map_err(|_| json_error("SQLite migration 4 无法提交"))?;
        }
        if current < 5 {
            let transaction = connection
                .transaction()
                .map_err(|_| json_error("无法开启 SQLite migration 5"))?;
            transaction.execute_batch("CREATE TABLE desktop_conversation_attachments (workspace_id TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE, attachment_id TEXT NOT NULL, mime_type TEXT NOT NULL, display_name TEXT NOT NULL, byte_count INTEGER NOT NULL, sha256 TEXT NOT NULL, created_at TEXT NOT NULL, PRIMARY KEY(workspace_id, attachment_id), UNIQUE(workspace_id, sha256));")
                .map_err(|_| json_error("SQLite migration 5 失败"))?;
            transaction
                .pragma_update(None, "user_version", 5)
                .map_err(|_| json_error("无法写入 SQLite schema version 5"))?;
            transaction
                .commit()
                .map_err(|_| json_error("SQLite migration 5 无法提交"))?;
        }
        if current < 6 {
            let transaction = connection
                .transaction()
                .map_err(|_| json_error("无法开启 SQLite migration 6"))?;
            transaction.execute_batch("CREATE TABLE desktop_attachment_assets (sha256 TEXT PRIMARY KEY NOT NULL, byte_count INTEGER NOT NULL, created_at_ms INTEGER NOT NULL, last_referenced_at_ms INTEGER, last_unreferenced_at_ms INTEGER NOT NULL, reference_count INTEGER NOT NULL); CREATE TABLE desktop_attachment_staging (staging_id TEXT PRIMARY KEY NOT NULL, staging_name TEXT UNIQUE NOT NULL, sha256 TEXT NOT NULL, byte_count INTEGER NOT NULL, created_at_ms INTEGER NOT NULL);")
                .map_err(|_| json_error("SQLite migration 6 失败"))?;
            transaction
                .pragma_update(None, "user_version", 6)
                .map_err(|_| json_error("无法写入 SQLite schema version 6"))?;
            transaction
                .commit()
                .map_err(|_| json_error("SQLite migration 6 无法提交"))?;
        }
        if current < 7 {
            let transaction = connection
                .transaction()
                .map_err(|_| json_error("无法开启 SQLite migration 7"))?;
            transaction.execute_batch("CREATE TABLE desktop_temporary_recovery (temporary_id TEXT PRIMARY KEY NOT NULL, recovery_json TEXT NOT NULL, updated_at_ms INTEGER NOT NULL); CREATE TABLE desktop_temporary_attachments (temporary_id TEXT NOT NULL REFERENCES desktop_temporary_recovery(temporary_id) ON DELETE CASCADE, attachment_id TEXT NOT NULL, mime_type TEXT NOT NULL, display_name TEXT NOT NULL, byte_count INTEGER NOT NULL, sha256 TEXT NOT NULL, PRIMARY KEY(temporary_id, attachment_id), UNIQUE(temporary_id, sha256)); CREATE INDEX desktop_temporary_attachments_sha256 ON desktop_temporary_attachments(sha256);")
                .map_err(|_| json_error("SQLite migration 7 失败"))?;
            transaction
                .pragma_update(None, "user_version", 7)
                .map_err(|_| json_error("无法写入 SQLite schema version 7"))?;
            transaction
                .commit()
                .map_err(|_| json_error("SQLite migration 7 无法提交"))?;
        }
        if current < 8 {
            let transaction = connection
                .transaction()
                .map_err(|_| json_error("无法开启 SQLite migration 8"))?;
            transaction.execute_batch("CREATE TABLE desktop_local_search_index (workspace_id TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE, entry_id TEXT NOT NULL, conversation_id TEXT NOT NULL, message_id TEXT, content_kind TEXT NOT NULL, title TEXT NOT NULL, normalized_text TEXT NOT NULL, snippet TEXT NOT NULL, timestamp TEXT NOT NULL, PRIMARY KEY(workspace_id, entry_id)); CREATE INDEX desktop_local_search_index_query ON desktop_local_search_index(workspace_id, normalized_text); CREATE TABLE desktop_local_search_history (workspace_id TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE, scope TEXT NOT NULL, normalized_query TEXT NOT NULL, last_used_ms INTEGER NOT NULL, PRIMARY KEY(workspace_id, scope, normalized_query));")
                .map_err(|_| json_error("SQLite migration 8 失败"))?;
            transaction
                .pragma_update(None, "user_version", 8)
                .map_err(|_| json_error("无法写入 SQLite schema version 8"))?;
            transaction
                .commit()
                .map_err(|_| json_error("SQLite migration 8 无法提交"))?;
        }
        if current < 9 {
            let transaction = connection
                .transaction()
                .map_err(|_| json_error("无法开启 SQLite migration 9"))?;
            transaction.execute_batch("CREATE TABLE desktop_pdf_preview_positions (workspace_id TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE, attachment_id TEXT NOT NULL, page_number INTEGER NOT NULL, updated_at_ms INTEGER NOT NULL, PRIMARY KEY(workspace_id, attachment_id));")
                .map_err(|_| json_error("SQLite migration 9 失败"))?;
            transaction
                .pragma_update(None, "user_version", 9)
                .map_err(|_| json_error("无法写入 SQLite schema version"))?;
            transaction
                .commit()
                .map_err(|_| json_error("SQLite migration 9 无法提交"))?;
        }
        if current < 10 {
            let transaction = connection
                .transaction()
                .map_err(|_| json_error("无法开启 SQLite migration 10"))?;
            transaction.execute_batch("CREATE TABLE desktop_video_preview_positions (workspace_id TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE, attachment_id TEXT NOT NULL, position_millis INTEGER NOT NULL, updated_at_ms INTEGER NOT NULL, PRIMARY KEY(workspace_id, attachment_id));")
                .map_err(|_| json_error("SQLite migration 10 失败"))?;
            transaction
                .pragma_update(None, "user_version", 10)
                .map_err(|_| json_error("无法写入 SQLite schema version 10"))?;
            transaction
                .commit()
                .map_err(|_| json_error("SQLite migration 10 无法提交"))?;
        }
        if current < 11 {
            let transaction = connection
                .transaction()
                .map_err(|_| json_error("无法开启 SQLite migration 11"))?;
            transaction.execute_batch("CREATE TABLE desktop_audio_preview_positions (workspace_id TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE, attachment_id TEXT NOT NULL, position_millis INTEGER NOT NULL, updated_at_ms INTEGER NOT NULL, PRIMARY KEY(workspace_id, attachment_id));")
                .map_err(|_| json_error("SQLite migration 11 失败"))?;
            transaction
                .pragma_update(None, "user_version", 11)
                .map_err(|_| json_error("无法写入 SQLite schema version 11"))?;
            transaction
                .commit()
                .map_err(|_| json_error("SQLite migration 11 无法提交"))?;
        }
        if current < 12 {
            let transaction = connection
                .transaction()
                .map_err(|_| json_error("无法开启 SQLite migration 12"))?;
            transaction.execute_batch("CREATE TABLE p6g_catalog (id INTEGER PRIMARY KEY CHECK(id=1), revision INTEGER NOT NULL, catalog_json TEXT NOT NULL); CREATE TABLE p6g_global_default (id INTEGER PRIMARY KEY CHECK(id=1), revision INTEGER NOT NULL, tier TEXT); CREATE TABLE p6g_conversation_override (workspace_id TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE, conversation_id TEXT NOT NULL, revision INTEGER NOT NULL, model_id TEXT, PRIMARY KEY(workspace_id, conversation_id)); CREATE TABLE p6g_route_metadata (workspace_id TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE, conversation_id TEXT NOT NULL, revision INTEGER NOT NULL, metadata_json TEXT NOT NULL, created_at_ms INTEGER NOT NULL, PRIMARY KEY(workspace_id, conversation_id, revision));")
                .map_err(|_| json_error("SQLite migration 12 失败"))?;
            transaction
                .pragma_update(None, "user_version", 12)
                .map_err(|_| json_error("无法写入 SQLite schema version 12"))?;
            transaction
                .commit()
                .map_err(|_| json_error("SQLite migration 12 无法提交"))?;
        }
        if current < 13 {
            let transaction = connection
                .transaction()
                .map_err(|_| json_error("无法开启 SQLite migration 13"))?;
            transaction.execute_batch("CREATE TABLE chatgpt_import_tasks (id TEXT PRIMARY KEY NOT NULL, status TEXT NOT NULL, storage_key TEXT, display_name TEXT, mime_type TEXT, byte_count INTEGER, package_hash TEXT, failure TEXT, retry_count INTEGER NOT NULL, created_at_ms INTEGER NOT NULL, updated_at_ms INTEGER NOT NULL); CREATE INDEX chatgpt_import_tasks_status_updated ON chatgpt_import_tasks(status,updated_at_ms); CREATE TABLE chatgpt_import_items (task_id TEXT NOT NULL REFERENCES chatgpt_import_tasks(id) ON DELETE CASCADE, id TEXT NOT NULL, ordinal INTEGER NOT NULL, source_conversation_id TEXT, title TEXT, content_hash TEXT, status TEXT NOT NULL, failure TEXT, conversation_id TEXT, PRIMARY KEY(task_id,id)); CREATE TABLE chatgpt_import_messages (task_id TEXT NOT NULL, item_id TEXT NOT NULL, source_message_id TEXT NOT NULL, parent_source_message_id TEXT, sibling_position INTEGER NOT NULL, role TEXT NOT NULL, text TEXT NOT NULL, created_at_ms INTEGER NOT NULL, imported_model TEXT, PRIMARY KEY(task_id,item_id,source_message_id)); CREATE TABLE chatgpt_import_provenance (conversation_id TEXT PRIMARY KEY NOT NULL, source_conversation_id TEXT NOT NULL, package_hash TEXT NOT NULL, content_hash TEXT NOT NULL, imported_at_ms INTEGER NOT NULL, adapter_id TEXT NOT NULL, adapter_version INTEGER NOT NULL, revoked_at_ms INTEGER); CREATE UNIQUE INDEX chatgpt_import_provenance_source_package ON chatgpt_import_provenance(source_conversation_id,package_hash); CREATE TABLE chatgpt_import_receipts (source_conversation_id TEXT NOT NULL, package_hash TEXT NOT NULL, conversation_id TEXT NOT NULL, content_hash TEXT NOT NULL, committed_at_ms INTEGER NOT NULL, PRIMARY KEY(source_conversation_id,package_hash));")
                .map_err(|_| json_error("SQLite migration 13 失败"))?;
            transaction
                .pragma_update(None, "user_version", 13)
                .map_err(|_| json_error("无法写入 SQLite schema version 13"))?;
            transaction
                .commit()
                .map_err(|_| json_error("SQLite migration 13 无法提交"))?;
        }
        if current < 14 {
            let transaction = connection
                .transaction()
                .map_err(|_| json_error("无法开启 SQLite migration 14"))?;
            transaction.execute_batch("CREATE TABLE claude_import_tasks (id TEXT PRIMARY KEY NOT NULL, status TEXT NOT NULL, storage_key TEXT, display_name TEXT, mime_type TEXT, byte_count INTEGER, package_hash TEXT, failure TEXT, retry_count INTEGER NOT NULL, created_at_ms INTEGER NOT NULL, updated_at_ms INTEGER NOT NULL); CREATE INDEX claude_import_tasks_status_updated ON claude_import_tasks(status,updated_at_ms); CREATE TABLE claude_import_items (task_id TEXT NOT NULL REFERENCES claude_import_tasks(id) ON DELETE CASCADE, id TEXT NOT NULL, ordinal INTEGER NOT NULL, source_conversation_id TEXT, title TEXT, content_hash TEXT, status TEXT NOT NULL, failure TEXT, conversation_id TEXT, PRIMARY KEY(task_id,id)); CREATE TABLE claude_import_messages (task_id TEXT NOT NULL, item_id TEXT NOT NULL, source_message_id TEXT NOT NULL, parent_source_message_id TEXT, sibling_position INTEGER NOT NULL, role TEXT NOT NULL, text TEXT NOT NULL, created_at_ms INTEGER NOT NULL, imported_model TEXT, PRIMARY KEY(task_id,item_id,source_message_id)); CREATE TABLE claude_import_provenance (conversation_id TEXT PRIMARY KEY NOT NULL, source_conversation_id TEXT NOT NULL, package_hash TEXT NOT NULL, content_hash TEXT NOT NULL, imported_at_ms INTEGER NOT NULL, adapter_id TEXT NOT NULL, adapter_version INTEGER NOT NULL, revoked_at_ms INTEGER); CREATE UNIQUE INDEX claude_import_provenance_source_package ON claude_import_provenance(source_conversation_id,package_hash); CREATE TABLE claude_import_receipts (source_conversation_id TEXT NOT NULL, package_hash TEXT NOT NULL, conversation_id TEXT NOT NULL, content_hash TEXT NOT NULL, committed_at_ms INTEGER NOT NULL, PRIMARY KEY(source_conversation_id,package_hash));")
                .map_err(|_| json_error("SQLite migration 14 失败"))?;
            transaction
                .pragma_update(None, "user_version", 14)
                .map_err(|_| json_error("无法写入 SQLite schema version 14"))?;
            transaction
                .commit()
                .map_err(|_| json_error("SQLite migration 14 无法提交"))?;
        }
        if current < 15 {
            let transaction = connection.transaction().map_err(|_| json_error("无法开启 SQLite migration 15"))?;
            transaction.execute_batch("CREATE TABLE nanfeng_knowledge_import_tasks (id TEXT PRIMARY KEY NOT NULL, status TEXT NOT NULL, storage_key TEXT, display_name TEXT, mime_type TEXT, byte_count INTEGER, package_hash TEXT, failure TEXT, retry_count INTEGER NOT NULL, created_at_ms INTEGER NOT NULL, updated_at_ms INTEGER NOT NULL); CREATE INDEX nanfeng_knowledge_import_tasks_status_updated ON nanfeng_knowledge_import_tasks(status,updated_at_ms); CREATE TABLE nanfeng_knowledge_import_items (task_id TEXT NOT NULL REFERENCES nanfeng_knowledge_import_tasks(id) ON DELETE CASCADE, id TEXT NOT NULL, ordinal INTEGER NOT NULL, source_conversation_id TEXT, title TEXT, content_hash TEXT, status TEXT NOT NULL, failure TEXT, conversation_id TEXT, PRIMARY KEY(task_id,id)); CREATE TABLE nanfeng_knowledge_import_messages (task_id TEXT NOT NULL, item_id TEXT NOT NULL, source_message_id TEXT NOT NULL, parent_source_message_id TEXT, sibling_position INTEGER NOT NULL, role TEXT NOT NULL, text TEXT NOT NULL, created_at_ms INTEGER NOT NULL, PRIMARY KEY(task_id,item_id,source_message_id)); CREATE TABLE nanfeng_knowledge_import_provenance (conversation_id TEXT PRIMARY KEY NOT NULL, source_conversation_id TEXT NOT NULL, package_hash TEXT NOT NULL, content_hash TEXT NOT NULL, imported_at_ms INTEGER NOT NULL, adapter_id TEXT NOT NULL, adapter_version INTEGER NOT NULL, revoked_at_ms INTEGER); CREATE UNIQUE INDEX nanfeng_knowledge_import_provenance_source_package ON nanfeng_knowledge_import_provenance(source_conversation_id,package_hash); CREATE TABLE nanfeng_knowledge_import_receipts (source_conversation_id TEXT NOT NULL, package_hash TEXT NOT NULL, conversation_id TEXT NOT NULL, content_hash TEXT NOT NULL, committed_at_ms INTEGER NOT NULL, PRIMARY KEY(source_conversation_id,package_hash));").map_err(|_| json_error("SQLite migration 15 失败"))?;
            transaction.pragma_update(None, "user_version", 15).map_err(|_| json_error("无法写入 SQLite schema version 15"))?;
            transaction.commit().map_err(|_| json_error("SQLite migration 15 无法提交"))?;
        }
        if current < 16 {
            let transaction = connection.transaction().map_err(|_| json_error("无法开启 SQLite migration 16"))?;
            transaction.execute_batch("CREATE TABLE p6k_zip_import_tasks (id TEXT PRIMARY KEY NOT NULL, provider TEXT NOT NULL, status TEXT NOT NULL, storage_key TEXT NOT NULL, display_name TEXT NOT NULL, byte_count INTEGER NOT NULL, package_hash TEXT NOT NULL, failure TEXT, entry_count INTEGER NOT NULL, created_at_ms INTEGER NOT NULL, updated_at_ms INTEGER NOT NULL); CREATE INDEX p6k_zip_import_tasks_status_updated ON p6k_zip_import_tasks(status,updated_at_ms); CREATE TABLE p6k_zip_import_entries (task_id TEXT NOT NULL REFERENCES p6k_zip_import_tasks(id) ON DELETE CASCADE, ordinal INTEGER NOT NULL, entry_name TEXT NOT NULL, uncompressed_bytes INTEGER NOT NULL, compressed_bytes INTEGER NOT NULL, mime_type TEXT NOT NULL, PRIMARY KEY(task_id,ordinal), UNIQUE(task_id,entry_name));").map_err(|_| json_error("SQLite migration 16 失败"))?;
            transaction.pragma_update(None, "user_version", 16).map_err(|_| json_error("无法写入 SQLite schema version 16"))?;
            transaction.commit().map_err(|_| json_error("SQLite migration 16 无法提交"))?;
        }
        if current < 17 {
            let transaction = connection.transaction().map_err(|_| json_error("无法开启 SQLite migration 17"))?;
            // P6-K owns private staging/recovery records only.  Conversation and message data is
            // committed into the already-rendered workspace_exchange document.
            transaction.execute_batch("ALTER TABLE p6k_zip_import_tasks ADD COLUMN workspace_id TEXT; CREATE TABLE p6k_zip_import_items (task_id TEXT NOT NULL REFERENCES p6k_zip_import_tasks(id) ON DELETE CASCADE, id TEXT NOT NULL, ordinal INTEGER NOT NULL, source_conversation_id TEXT, title TEXT, content_hash TEXT, status TEXT NOT NULL, failure TEXT, conversation_id TEXT, PRIMARY KEY(task_id,id)); CREATE TABLE p6k_zip_import_messages (task_id TEXT NOT NULL, item_id TEXT NOT NULL, source_message_id TEXT NOT NULL, parent_source_message_id TEXT, sibling_position INTEGER NOT NULL, role TEXT NOT NULL, text TEXT NOT NULL, created_at_ms INTEGER NOT NULL, imported_model TEXT, PRIMARY KEY(task_id,item_id,source_message_id)); CREATE TABLE p6k_zip_import_asset_candidates (task_id TEXT NOT NULL REFERENCES p6k_zip_import_tasks(id) ON DELETE CASCADE, ordinal INTEGER NOT NULL, entry_name TEXT NOT NULL, sha256 TEXT NOT NULL, byte_count INTEGER NOT NULL, mime_type TEXT NOT NULL, status TEXT NOT NULL, PRIMARY KEY(task_id,ordinal)); CREATE TABLE p6k_zip_import_profile_candidates (task_id TEXT PRIMARY KEY REFERENCES p6k_zip_import_tasks(id) ON DELETE CASCADE, status TEXT NOT NULL, mapped_field_count INTEGER NOT NULL); CREATE TABLE p6k_zip_import_provenance (conversation_id TEXT PRIMARY KEY NOT NULL, task_id TEXT NOT NULL REFERENCES p6k_zip_import_tasks(id) ON DELETE CASCADE, workspace_id TEXT NOT NULL, provider TEXT NOT NULL, source_conversation_id TEXT NOT NULL, package_hash TEXT NOT NULL, content_hash TEXT NOT NULL, imported_at_ms INTEGER NOT NULL, adapter_id TEXT NOT NULL, adapter_version INTEGER NOT NULL); CREATE TABLE p6k_zip_import_receipts (workspace_id TEXT NOT NULL, provider TEXT NOT NULL, source_conversation_id TEXT NOT NULL, package_hash TEXT NOT NULL, conversation_id TEXT NOT NULL, content_hash TEXT NOT NULL, committed_at_ms INTEGER NOT NULL, PRIMARY KEY(workspace_id,provider,source_conversation_id,package_hash)); CREATE INDEX p6k_zip_import_tasks_workspace_updated ON p6k_zip_import_tasks(workspace_id,updated_at_ms);").map_err(|_| json_error("SQLite migration 17 失败"))?;
            transaction.pragma_update(None, "user_version", 17).map_err(|_| json_error("无法写入 SQLite schema version 17"))?;
            transaction.commit().map_err(|_| json_error("SQLite migration 17 无法提交"))?;
        }
        if current < 18 {
            let transaction = connection.transaction().map_err(|_| json_error("无法开启 SQLite migration 18"))?;
            transaction.execute_batch("CREATE TABLE p6k_profile_personalization_settings (id INTEGER PRIMARY KEY CHECK(id=1), display_name TEXT, language TEXT, timezone TEXT, public_bio TEXT, custom_instructions TEXT, theme TEXT, notifications_enabled INTEGER, source_task_id TEXT NOT NULL, revision INTEGER NOT NULL, updated_at_ms INTEGER NOT NULL); CREATE TABLE p6k_profile_import_provenance (task_id TEXT PRIMARY KEY NOT NULL, provider TEXT NOT NULL, package_hash TEXT NOT NULL, canonical_hash TEXT NOT NULL, imported_at_ms INTEGER NOT NULL); CREATE TABLE p6k_profile_import_receipts (provider TEXT NOT NULL, package_hash TEXT NOT NULL, task_id TEXT NOT NULL, canonical_hash TEXT NOT NULL, committed_at_ms INTEGER NOT NULL, PRIMARY KEY(provider,package_hash)); CREATE INDEX p6k_profile_import_receipts_task ON p6k_profile_import_receipts(task_id);").map_err(|_| json_error("SQLite migration 18 失败"))?;
            transaction.pragma_update(None, "user_version", 18).map_err(|_| json_error("无法写入 SQLite schema version 18"))?;
            transaction.commit().map_err(|_| json_error("SQLite migration 18 无法提交"))?;
        }
        if current < 19 {
            let transaction = connection.transaction().map_err(|_| json_error("无法开启 SQLite migration 19"))?;
            transaction.execute_batch("CREATE TABLE p6k_zip_asset_link_receipts (task_id TEXT NOT NULL REFERENCES p6k_zip_import_tasks(id) ON DELETE CASCADE, ordinal INTEGER NOT NULL, sha256 TEXT NOT NULL, attachment_id TEXT NOT NULL, workspace_id TEXT NOT NULL, conversation_id TEXT NOT NULL, message_id TEXT NOT NULL, committed_at_ms INTEGER NOT NULL, PRIMARY KEY(task_id,ordinal)); CREATE INDEX p6k_zip_asset_link_receipts_target ON p6k_zip_asset_link_receipts(workspace_id,conversation_id,message_id);")
                .map_err(|_| json_error("SQLite migration 19 失败"))?;
            transaction.pragma_update(None, "user_version", 19).map_err(|_| json_error("无法写入 SQLite schema version 19"))?;
            transaction.commit().map_err(|_| json_error("SQLite migration 19 无法提交"))?;
        }
        if current < 20 {
            let transaction = connection.transaction().map_err(|_| json_error("无法开启 SQLite migration 20"))?;
            local_exact_reuse_v1::SqliteLocalExactReuseStore::migrate(&transaction).map_err(|_| json_error("SQLite migration 20 失败"))?;
            transaction.pragma_update(None, "user_version", 20).map_err(|_| json_error("无法写入 SQLite schema version 20"))?;
            transaction.commit().map_err(|_| json_error("SQLite migration 20 无法提交"))?;
        }
        Ok(())
    }

    fn rebuild_local_search_index(
        &self,
        transaction: &Transaction<'_>,
        workspace_id: &str,
        exchange: &Value,
    ) -> Result<(), String> {
        transaction
            .execute(
                "DELETE FROM desktop_local_search_index WHERE workspace_id=?1",
                [workspace_id],
            )
            .map_err(|_| json_error("无法清理本地搜索索引"))?;
        let root = object(exchange, "exchange")?;
        for conversation in require_array(root, "conversations")? {
            let conversation = object(conversation, "conversation")?;
            if conversation
                .get("deleted")
                .and_then(Value::as_bool)
                .unwrap_or(false)
                || conversation
                    .get("archived")
                    .and_then(Value::as_bool)
                    .unwrap_or(false)
            {
                continue;
            }
            let conversation_id = require_string(conversation, "id")?;
            let title = require_string(conversation, "title")?;
            let timestamp = conversation
                .get("updatedAt")
                .and_then(Value::as_str)
                .unwrap_or("");
            let insert = |entry_id: String,
                          message_id: Option<&str>,
                          content_kind: &str,
                          raw: &str,
                          timestamp: &str|
             -> Result<(), String> {
                let normalized = raw
                    .split_whitespace()
                    .collect::<Vec<_>>()
                    .join(" ")
                    .to_lowercase();
                if normalized.is_empty() {
                    return Ok(());
                }
                let snippet: String = raw
                    .split_whitespace()
                    .collect::<Vec<_>>()
                    .join(" ")
                    .chars()
                    .take(240)
                    .collect();
                transaction.execute("INSERT INTO desktop_local_search_index(workspace_id,entry_id,conversation_id,message_id,content_kind,title,normalized_text,snippet,timestamp) VALUES(?1,?2,?3,?4,?5,?6,?7,?8,?9)", params![workspace_id, entry_id, conversation_id, message_id, content_kind, title, normalized, snippet, timestamp]).map_err(|_| json_error("无法写入本地搜索索引"))?;
                Ok(())
            };
            insert(
                format!("{conversation_id}:title"),
                None,
                "TEXT",
                title,
                timestamp,
            )?;
            let messages = require_array(conversation, "messages")?;
            let by_id: BTreeMap<&str, &Value> = messages
                .iter()
                .filter_map(|m| m.get("id").and_then(Value::as_str).map(|id| (id, m)))
                .collect();
            let mut current = conversation.get("currentLeafId").and_then(Value::as_str);
            let mut path = Vec::new();
            while let Some(id) = current {
                let Some(message) = by_id.get(id) else { break };
                path.push(*message);
                current = message.get("parentId").and_then(Value::as_str);
            }
            path.reverse();
            for message in path {
                let message = object(message, "message")?;
                let role = message.get("role").and_then(Value::as_str).unwrap_or("");
                if role != "user" && role != "assistant" {
                    continue;
                }
                let message_id = message.get("id").and_then(Value::as_str).unwrap_or("");
                let message_time = message
                    .get("createdAt")
                    .and_then(Value::as_str)
                    .unwrap_or(timestamp);
                for (position, block) in require_array(message, "blocks")?.iter().enumerate() {
                    let kind = block.get("kind").and_then(Value::as_str).unwrap_or("");
                    if kind == "TEXT" {
                        if let Some(text) = block.get("text").and_then(Value::as_str) {
                            insert(
                                format!("{conversation_id}:{message_id}:text:{position}"),
                                Some(message_id),
                                "TEXT",
                                text,
                                message_time,
                            )?;
                        }
                    }
                    if kind == "ASSET_REF" {
                        let asset = block.get("asset").and_then(Value::as_object);
                        let label = asset
                            .map(|a| {
                                format!(
                                    "{} · {}",
                                    a.get("displayName")
                                        .and_then(Value::as_str)
                                        .unwrap_or("本地附件"),
                                    a.get("mimeType")
                                        .and_then(Value::as_str)
                                        .unwrap_or("未知类型")
                                )
                            })
                            .unwrap_or_default();
                        insert(
                            format!("{conversation_id}:{message_id}:attachment:{position}"),
                            Some(message_id),
                            "ATTACHMENT",
                            &label,
                            message_time,
                        )?;
                    }
                }
            }
        }
        Ok(())
    }

    fn search_local_index(
        &self,
        workspace_id: &str,
        query: &str,
    ) -> Result<Vec<DesktopLocalSearchHit>, String> {
        let normalized = query
            .split_whitespace()
            .collect::<Vec<_>>()
            .join(" ")
            .to_lowercase();
        if normalized.is_empty() {
            return Ok(Vec::new());
        }
        let mut connection = self.connection()?;
        let transaction = connection
            .transaction()
            .map_err(|_| json_error("无法开启本地搜索 transaction"))?;
        let exchange_text: String = transaction
            .query_row(
                "SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?1",
                [workspace_id],
                |row| row.get(0),
            )
            .map_err(|_| json_error("工作区不存在"))?;
        let exchange: Value =
            serde_json::from_str(&exchange_text).map_err(|_| json_error("本地交换 IR 无法读取"))?;
        let indexed_count: i64 = transaction
            .query_row(
                "SELECT COUNT(*) FROM desktop_local_search_index WHERE workspace_id=?1",
                [workspace_id],
                |row| row.get(0),
            )
            .map_err(|_| json_error("无法检查本地搜索索引"))?;
        if indexed_count == 0 {
            self.rebuild_local_search_index(&transaction, workspace_id, &exchange)?;
        }
        transaction.execute("INSERT INTO desktop_local_search_history(workspace_id,scope,normalized_query,last_used_ms) VALUES(?1,'CHAT',?2,?3) ON CONFLICT(workspace_id,scope,normalized_query) DO UPDATE SET last_used_ms=excluded.last_used_ms", params![workspace_id, normalized, system_now_millis()]).map_err(|_| json_error("无法写入本地搜索历史"))?;
        transaction.execute("DELETE FROM desktop_local_search_history WHERE workspace_id=?1 AND scope='CHAT' AND normalized_query NOT IN (SELECT normalized_query FROM desktop_local_search_history WHERE workspace_id=?1 AND scope='CHAT' ORDER BY last_used_ms DESC, normalized_query ASC LIMIT 10)", [workspace_id]).map_err(|_| json_error("无法裁剪本地搜索历史"))?;
        let mut statement = transaction.prepare("SELECT conversation_id,message_id,title,snippet,content_kind,timestamp FROM desktop_local_search_index WHERE workspace_id=?1 AND normalized_text LIKE '%' || ?2 || '%' ORDER BY CASE WHEN content_kind='TEXT' AND message_id IS NULL THEN 0 WHEN content_kind='TEXT' THEN 1 ELSE 2 END, timestamp DESC, conversation_id ASC, COALESCE(message_id,'') ASC LIMIT 50").map_err(|_| json_error("无法读取本地搜索索引"))?;
        let hits = statement
            .query_map(params![workspace_id, normalized], |row| {
                Ok(DesktopLocalSearchHit {
                    conversation_id: row.get(0)?,
                    message_id: row.get(1)?,
                    title: row.get(2)?,
                    snippet: row.get(3)?,
                    content_kind: row.get(4)?,
                    timestamp: row.get(5)?,
                    title_match: row.get::<_, String>(4)? == "TEXT"
                        && row.get::<_, Option<String>>(1)?.is_none(),
                })
            })
            .map_err(|_| json_error("本地搜索结果无效"))?
            .collect::<Result<Vec<_>, _>>()
            .map_err(|_| json_error("本地搜索结果无法读取"))?;
        drop(statement);
        transaction
            .commit()
            .map_err(|_| json_error("本地搜索未提交；已回滚"))?;
        Ok(hits)
    }

    fn local_search_history(&self, workspace_id: &str) -> Result<Vec<String>, String> {
        let connection = self.connection()?;
        let mut statement = connection.prepare("SELECT normalized_query FROM desktop_local_search_history WHERE workspace_id=?1 AND scope='CHAT' ORDER BY last_used_ms DESC, normalized_query ASC LIMIT 10").map_err(|_| json_error("无法读取本地搜索历史"))?;
        let result = statement
            .query_map([workspace_id], |row| row.get(0))
            .map_err(|_| json_error("本地搜索历史无效"))?
            .collect::<Result<Vec<_>, _>>()
            .map_err(|_| json_error("本地搜索历史无法读取"));
        result
    }
    fn clear_local_search_history(&self, workspace_id: &str) -> Result<(), String> {
        self.connection()?
            .execute(
                "DELETE FROM desktop_local_search_history WHERE workspace_id=?1 AND scope='CHAT'",
                [workspace_id],
            )
            .map_err(|_| json_error("无法清空本地搜索历史"))?;
        Ok(())
    }

    /// The sole Desktop image-reader adapter.  It resolves a workspace-owned attachment ID,
    /// rechecks its private-copy hash and returns display bytes only; callers never receive a
    /// filesystem path, URI or arbitrary file-reading capability.
    fn image_preview(&self, args: &DesktopImagePreviewArgs) -> Result<DesktopImagePreview, String> {
        if !is_stable_id(&args.workspace_id) || !is_stable_id(&args.attachment_id) {
            return Err(json_error("图片预览引用无效"));
        }
        let connection = self.connection()?;
        let metadata = connection.query_row(
            "SELECT mime_type,display_name,byte_count,sha256 FROM desktop_conversation_attachments WHERE workspace_id=?1 AND attachment_id=?2",
            params![args.workspace_id, args.attachment_id],
            |row| Ok((row.get::<_, String>(0)?, row.get::<_, String>(1)?, row.get::<_, u64>(2)?, row.get::<_, String>(3)?)),
        ).map_err(|_| json_error("本地图片附件不可用"))?;
        if !matches!(
            metadata.0.as_str(),
            "image/jpeg" | "image/png" | "image/webp"
        ) || !is_sha256(&metadata.3)
        {
            return Err(json_error("该本地附件不是受支持的图片"));
        }
        let asset = self.root.join("assets").join(&metadata.3);
        let bytes = fs::read(&asset).map_err(|_| json_error("本地图片附件缺失"))?;
        if bytes.len() as u64 != metadata.2 || sha256(&bytes) != metadata.3 {
            return Err(json_error("本地图片附件校验不一致"));
        }
        let reader = ImageReader::new(Cursor::new(&bytes))
            .with_guessed_format()
            .map_err(|_| json_error("本地图片格式无效"))?;
        let image = reader
            .decode()
            .map_err(|_| json_error("本地图片已损坏，无法预览"))?;
        let width = image.width();
        let height = image.height();
        if width == 0
            || height == 0
            || u64::from(width) * u64::from(height) > MAX_CONVERSATION_IMAGE_PIXELS
        {
            return Err(json_error("图片像素超过本地安全预览上限"));
        }
        let (mime_type, image_bytes, is_thumbnail) = if args.full_size {
            (metadata.0.clone(), bytes, false)
        } else {
            let thumbnail = image.thumbnail(
                MAX_DESKTOP_IMAGE_THUMBNAIL_EDGE,
                MAX_DESKTOP_IMAGE_THUMBNAIL_EDGE,
            );
            let mut output = Cursor::new(Vec::new());
            thumbnail
                .write_to(&mut output, ImageFormat::Png)
                .map_err(|_| json_error("本地缩略图生成失败"))?;
            ("image/png".to_owned(), output.into_inner(), true)
        };
        Ok(DesktopImagePreview {
            attachment_id: args.attachment_id.clone(),
            mime_type,
            display_name: metadata.1,
            byte_count: metadata.2,
            width,
            height,
            data_url: format!(
                "data:{};base64,{}",
                if is_thumbnail {
                    "image/png"
                } else {
                    &metadata.0
                },
                BASE64.encode(image_bytes)
            ),
            is_thumbnail,
        })
    }

    /// The sole Desktop PDF reader adapter. It validates the workspace-owned private copy and
    /// parses page count locally before returning an inert data URL to a sandboxed reader view.
    fn pdf_preview(&self, args: &DesktopPdfPreviewArgs) -> Result<DesktopPdfPreview, String> {
        if !is_stable_id(&args.workspace_id) || !is_stable_id(&args.attachment_id) {
            return Err(json_error("PDF 预览引用无效"));
        }
        let connection = self.connection()?;
        let metadata = connection.query_row(
            "SELECT mime_type,display_name,byte_count,sha256 FROM desktop_conversation_attachments WHERE workspace_id=?1 AND attachment_id=?2",
            params![args.workspace_id, args.attachment_id],
            |row| Ok((row.get::<_, String>(0)?, row.get::<_, String>(1)?, row.get::<_, u64>(2)?, row.get::<_, String>(3)?)),
        ).map_err(|_| json_error("本地 PDF 附件不可用"))?;
        if metadata.0 != "application/pdf" || !is_sha256(&metadata.3) {
            return Err(json_error("该本地附件不是受支持的 PDF"));
        }
        let bytes = fs::read(self.root.join("assets").join(&metadata.3))
            .map_err(|_| json_error("本地 PDF 附件缺失"))?;
        if bytes.len() as u64 != metadata.2 || sha256(&bytes) != metadata.3 {
            return Err(json_error("本地 PDF 附件校验不一致"));
        }
        let page_count = Document::load_mem(&bytes)
            .map_err(|_| json_error("本地 PDF 已损坏，无法阅读"))?
            .get_pages()
            .len() as u32;
        if page_count == 0 {
            return Err(json_error("本地 PDF 没有可阅读页面"));
        }
        let persisted = connection.query_row(
            "SELECT page_number FROM desktop_pdf_preview_positions WHERE workspace_id=?1 AND attachment_id=?2",
            params![args.workspace_id, args.attachment_id],
            |row| row.get::<_, u32>(0),
        ).ok();
        let page_number = args
            .page_number
            .or(persisted)
            .unwrap_or(1)
            .clamp(1, page_count);
        connection.execute(
            "INSERT INTO desktop_pdf_preview_positions(workspace_id,attachment_id,page_number,updated_at_ms) VALUES(?1,?2,?3,?4) ON CONFLICT(workspace_id,attachment_id) DO UPDATE SET page_number=excluded.page_number,updated_at_ms=excluded.updated_at_ms",
            params![args.workspace_id, args.attachment_id, page_number, local_now_millis()],
        ).map_err(|_| json_error("本地 PDF 页码未保存"))?;
        Ok(DesktopPdfPreview {
            attachment_id: args.attachment_id.clone(),
            display_name: metadata.1,
            byte_count: metadata.2,
            page_number,
            page_count,
            data_url: format!("data:application/pdf;base64,{}", BASE64.encode(bytes)),
        })
    }

    /// The only Desktop video reader. Browser playback receives a verified data URL, never a filesystem capability.
    fn video_preview(&self, args: &DesktopVideoPreviewArgs) -> Result<DesktopVideoPreview, String> {
        if !is_stable_id(&args.workspace_id) || !is_stable_id(&args.attachment_id) {
            return Err(json_error("视频预览引用无效"));
        }
        let connection = self.connection()?;
        let metadata = connection.query_row(
            "SELECT mime_type,display_name,byte_count,sha256 FROM desktop_conversation_attachments WHERE workspace_id=?1 AND attachment_id=?2",
            params![args.workspace_id, args.attachment_id],
            |row| Ok((row.get::<_, String>(0)?, row.get::<_, String>(1)?, row.get::<_, u64>(2)?, row.get::<_, String>(3)?)),
        ).map_err(|_| json_error("本地视频附件不可用"))?;
        if metadata.0 != "video/mp4" || !is_sha256(&metadata.3) {
            return Err(json_error("该本地附件不是受支持的视频"));
        }
        let bytes = fs::read(self.root.join("assets").join(&metadata.3))
            .map_err(|_| json_error("本地视频附件缺失"))?;
        if bytes.len() as u64 != metadata.2 || sha256(&bytes) != metadata.3 {
            return Err(json_error("本地视频附件校验不一致"));
        }
        let duration_millis = mp4_duration_millis(&bytes)
            .ok_or_else(|| json_error("本地视频 metadata 无效或超过安全时长"))?;
        let persisted = connection.query_row("SELECT position_millis FROM desktop_video_preview_positions WHERE workspace_id=?1 AND attachment_id=?2", params![args.workspace_id, args.attachment_id], |row| row.get::<_, u64>(0)).ok();
        let position_millis = args
            .position_millis
            .or(persisted)
            .unwrap_or(0)
            .min(duration_millis);
        connection.execute("INSERT INTO desktop_video_preview_positions(workspace_id,attachment_id,position_millis,updated_at_ms) VALUES(?1,?2,?3,?4) ON CONFLICT(workspace_id,attachment_id) DO UPDATE SET position_millis=excluded.position_millis,updated_at_ms=excluded.updated_at_ms", params![args.workspace_id, args.attachment_id, position_millis, local_now_millis()]).map_err(|_| json_error("本地视频播放位置未保存"))?;
        Ok(DesktopVideoPreview {
            attachment_id: args.attachment_id.clone(),
            display_name: metadata.1,
            byte_count: metadata.2,
            duration_millis,
            position_millis,
            data_url: format!("data:video/mp4;base64,{}", BASE64.encode(bytes)),
        })
    }

    /// The only Desktop audio reader. It verifies the workspace-owned private copy before a data URL exists.
    fn audio_preview(&self, args: &DesktopAudioPreviewArgs) -> Result<DesktopAudioPreview, String> {
        if !is_stable_id(&args.workspace_id) || !is_stable_id(&args.attachment_id) {
            return Err(json_error("音频预览引用无效"));
        }
        let connection = self.connection()?;
        let metadata = connection.query_row(
            "SELECT mime_type,display_name,byte_count,sha256 FROM desktop_conversation_attachments WHERE workspace_id=?1 AND attachment_id=?2",
            params![args.workspace_id, args.attachment_id],
            |row| Ok((row.get::<_, String>(0)?, row.get::<_, String>(1)?, row.get::<_, u64>(2)?, row.get::<_, String>(3)?)),
        ).map_err(|_| json_error("本地音频附件不可用"))?;
        if !matches!(
            metadata.0.as_str(),
            "audio/mpeg" | "audio/wav" | "audio/mp4"
        ) || !is_sha256(&metadata.3)
        {
            return Err(json_error("该本地附件不是受支持的音频"));
        }
        let bytes = fs::read(self.root.join("assets").join(&metadata.3))
            .map_err(|_| json_error("本地音频附件缺失"))?;
        if bytes.len() as u64 != metadata.2 || sha256(&bytes) != metadata.3 {
            return Err(json_error("本地音频附件校验不一致"));
        }
        let persisted = connection.query_row("SELECT position_millis FROM desktop_audio_preview_positions WHERE workspace_id=?1 AND attachment_id=?2", params![args.workspace_id, args.attachment_id], |row| row.get::<_, u64>(0)).ok();
        let position_millis = args.position_millis.or(persisted).unwrap_or(0);
        connection.execute("INSERT INTO desktop_audio_preview_positions(workspace_id,attachment_id,position_millis,updated_at_ms) VALUES(?1,?2,?3,?4) ON CONFLICT(workspace_id,attachment_id) DO UPDATE SET position_millis=excluded.position_millis,updated_at_ms=excluded.updated_at_ms", params![args.workspace_id, args.attachment_id, position_millis, local_now_millis()]).map_err(|_| json_error("本地音频播放位置未保存"))?;
        Ok(DesktopAudioPreview {
            attachment_id: args.attachment_id.clone(),
            display_name: metadata.1,
            mime_type: metadata.0.clone(),
            byte_count: metadata.2,
            position_millis,
            data_url: format!("data:{};base64,{}", metadata.0, BASE64.encode(bytes)),
        })
    }

    fn text_preview(&self, args: &DesktopTextPreviewArgs) -> Result<DesktopTextPreview, String> {
        if !is_stable_id(&args.workspace_id) || !is_stable_id(&args.attachment_id) {
            return Err(json_error("文本预览引用无效"));
        }
        let connection = self.connection()?;
        let metadata = connection.query_row("SELECT mime_type,display_name,byte_count,sha256 FROM desktop_conversation_attachments WHERE workspace_id=?1 AND attachment_id=?2", params![args.workspace_id, args.attachment_id], |row| Ok((row.get::<_, String>(0)?, row.get::<_, String>(1)?, row.get::<_, u64>(2)?, row.get::<_, String>(3)?))).map_err(|_| json_error("本地文本附件不可用"))?;
        if !matches!(
            metadata.0.as_str(),
            "text/plain" | "text/markdown" | "application/json" | "text/csv"
        ) || !is_sha256(&metadata.3)
        {
            return Err(json_error("该本地附件不是可安全预览的文本"));
        }
        let bytes = fs::read(self.root.join("assets").join(&metadata.3))
            .map_err(|_| json_error("本地文本附件缺失"))?;
        if bytes.len() as u64 != metadata.2 || sha256(&bytes) != metadata.3 {
            return Err(json_error("本地文本附件校验不一致"));
        }
        let truncated = bytes.len() > MAX_INERT_TEXT_PREVIEW_BYTES;
        let text = std::str::from_utf8(&bytes[..bytes.len().min(MAX_INERT_TEXT_PREVIEW_BYTES)])
            .map_err(|_| json_error("文件不是可安全解码的 UTF-8 文本"))?
            .strip_prefix('\u{feff}')
            .unwrap_or_else(|| {
                std::str::from_utf8(&bytes[..bytes.len().min(MAX_INERT_TEXT_PREVIEW_BYTES)])
                    .unwrap()
            })
            .to_owned();
        Ok(DesktopTextPreview {
            attachment_id: args.attachment_id.clone(),
            display_name: metadata.1,
            mime_type: metadata.0,
            byte_count: metadata.2,
            text,
            truncated,
        })
    }

    /// The only attachment cleanup path. `open` calls it with the system clock; tests pass a
    /// synthetic epoch value through this same owner method, never through file mtimes or shell
    /// deletion.  `last_unreferenced_at_ms` begins precisely when the last message reference is
    /// observed gone, so a pre-existing orphan cannot be deleted merely because its file is old.
    fn run_attachment_maintenance_at(
        &self,
        now_ms: i64,
    ) -> Result<AttachmentMaintenanceReceipt, String> {
        let mut connection = self.connection()?;
        let transaction = connection
            .transaction()
            .map_err(|_| json_error("无法开启附件维护 transaction"))?;
        let cutoff = now_ms.saturating_sub(ATTACHMENT_RETENTION_MILLIS);
        let mut reference_counts = BTreeMap::new();
        {
            let mut rows = transaction
                .prepare("SELECT exchange_json FROM workspace_exchange")
                .map_err(|_| json_error("无法读取附件引用"))?;
            let values = rows
                .query_map([], |row| row.get::<_, String>(0))
                .map_err(|_| json_error("无法读取附件引用"))?;
            for value in values {
                let exchange: Value =
                    serde_json::from_str(&value.map_err(|_| json_error("附件引用无效"))?)
                        .map_err(|_| json_error("附件引用无效"))?;
                for (sha256, count) in collect_asset_ref_counts(&exchange)? {
                    *reference_counts.entry(sha256).or_insert(0u64) += count;
                }
            }
        }
        {
            let mut rows = transaction
                .prepare(
                    "SELECT sha256,COUNT(*) FROM desktop_temporary_attachments GROUP BY sha256",
                )
                .map_err(|_| json_error("无法读取临时附件引用"))?;
            let values = rows
                .query_map([], |row| {
                    Ok((row.get::<_, String>(0)?, row.get::<_, u64>(1)?))
                })
                .map_err(|_| json_error("无法读取临时附件引用"))?;
            for value in values {
                let (sha256, count) = value.map_err(|_| json_error("临时附件引用无效"))?;
                *reference_counts.entry(sha256).or_insert(0u64) += count;
            }
        }

        // Legacy v5 files had no business timestamp. Seed them as fresh at first v6 maintenance;
        // this one-time compatibility bridge is deliberately not based on filesystem mtime.
        for entry in fs::read_dir(self.root.join("assets"))
            .map_err(|_| json_error("无法检查私有附件目录"))?
        {
            let entry = entry.map_err(|_| json_error("私有附件目录无效"))?;
            let name = entry.file_name().to_string_lossy().to_string();
            if is_sha256(&name)
                && entry
                    .file_type()
                    .map_err(|_| json_error("私有附件类型无效"))?
                    .is_file()
            {
                let byte_count = entry
                    .metadata()
                    .map_err(|_| json_error("私有附件元数据无效"))?
                    .len();
                transaction.execute("INSERT INTO desktop_attachment_assets(sha256,byte_count,created_at_ms,last_referenced_at_ms,last_unreferenced_at_ms,reference_count) VALUES(?1,?2,?3,NULL,?3,0) ON CONFLICT(sha256) DO NOTHING", params![name, byte_count, now_ms])
                    .map_err(|_| json_error("无法补齐附件元数据"))?;
            }
        }

        let mut referenced_retained = 0usize;
        let mut fresh_orphans_retained = 0usize;
        let mut expired_orphans_removed = 0usize;
        let assets = transaction
            .prepare("SELECT sha256,last_unreferenced_at_ms FROM desktop_attachment_assets ORDER BY sha256")
            .map_err(|_| json_error("无法读取附件维护元数据"))?
            .query_map([], |row| Ok((row.get::<_, String>(0)?, row.get::<_, i64>(1)?)))
            .map_err(|_| json_error("无法读取附件维护元数据"))?
            .collect::<Result<Vec<_>, _>>()
            .map_err(|_| json_error("附件维护元数据无效"))?;
        for (sha256, last_unreferenced_at_ms) in assets {
            let reference_count = reference_counts.get(&sha256).copied().unwrap_or_default();
            if reference_count > 0 {
                transaction.execute("UPDATE desktop_attachment_assets SET reference_count=?2,last_referenced_at_ms=?3,last_unreferenced_at_ms=?3 WHERE sha256=?1", params![sha256, reference_count, now_ms])
                    .map_err(|_| json_error("无法更新附件引用事实"))?;
                referenced_retained += 1;
                continue;
            }
            let prior_reference_count: i64 = transaction
                .query_row(
                    "SELECT reference_count FROM desktop_attachment_assets WHERE sha256=?1",
                    [&sha256],
                    |row| row.get(0),
                )
                .map_err(|_| json_error("附件引用事实缺失"))?;
            let unreferenced_since = if prior_reference_count > 0 {
                now_ms
            } else {
                last_unreferenced_at_ms
            };
            if unreferenced_since <= cutoff {
                let path = self.root.join("assets").join(&sha256);
                if path.exists() {
                    fs::remove_file(&path).map_err(|_| json_error("过期私有附件清理失败"))?;
                }
                transaction
                    .execute(
                        "DELETE FROM desktop_attachment_assets WHERE sha256=?1",
                        [&sha256],
                    )
                    .map_err(|_| json_error("无法删除过期附件元数据"))?;
                transaction
                    .execute(
                        "DELETE FROM desktop_conversation_attachments WHERE sha256=?1",
                        [&sha256],
                    )
                    .map_err(|_| json_error("无法删除过期附件引用元数据"))?;
                transaction
                    .execute(
                        "DELETE FROM desktop_temporary_attachments WHERE sha256=?1",
                        [&sha256],
                    )
                    .map_err(|_| json_error("无法删除过期临时附件引用元数据"))?;
                expired_orphans_removed += 1;
            } else {
                transaction.execute("UPDATE desktop_attachment_assets SET reference_count=0,last_unreferenced_at_ms=?2 WHERE sha256=?1", params![sha256, unreferenced_since]).map_err(|_| json_error("无法记录附件解除引用时间"))?;
                fresh_orphans_retained += 1;
            }
        }

        let staging = transaction
            .prepare("SELECT staging_id,staging_name FROM desktop_attachment_staging WHERE created_at_ms<=?1 ORDER BY staging_id")
            .map_err(|_| json_error("无法读取附件 staging 元数据"))?
            .query_map([cutoff], |row| Ok((row.get::<_, String>(0)?, row.get::<_, String>(1)?)))
            .map_err(|_| json_error("无法读取附件 staging 元数据"))?
            .collect::<Result<Vec<_>, _>>()
            .map_err(|_| json_error("附件 staging 元数据无效"))?;
        let mut expired_staging_removed = 0usize;
        for (staging_id, staging_name) in staging {
            let path = self.root.join("staging").join(staging_name);
            if path.exists() {
                fs::remove_file(path).map_err(|_| json_error("过期附件 staging 清理失败"))?;
            }
            transaction
                .execute(
                    "DELETE FROM desktop_attachment_staging WHERE staging_id=?1",
                    [&staging_id],
                )
                .map_err(|_| json_error("无法删除过期附件 staging 元数据"))?;
            expired_staging_removed += 1;
        }
        transaction
            .commit()
            .map_err(|_| json_error("附件维护未提交；已回滚"))?;
        Ok(AttachmentMaintenanceReceipt {
            referenced_retained,
            fresh_orphans_retained,
            expired_orphans_removed,
            expired_staging_removed,
        })
    }

    fn run_startup_attachment_maintenance(&self) -> Result<AttachmentMaintenanceReceipt, String> {
        let now_ms = system_now_millis();
        self.clear_expired_temporary_at(now_ms)?;
        self.run_attachment_maintenance_at(now_ms)
    }

    fn enter_or_restore_temporary(
        &self,
        temporary_id: Option<String>,
    ) -> Result<DesktopTemporaryConversationRecovery, String> {
        self.enter_or_restore_temporary_at(temporary_id, system_now_millis())
    }

    fn enter_or_restore_temporary_at(
        &self,
        temporary_id: Option<String>,
        now_ms: i64,
    ) -> Result<DesktopTemporaryConversationRecovery, String> {
        self.prune_temporary_at(now_ms)?;
        let connection = self.connection()?;
        let temporary_id = temporary_id.or_else(|| {
            connection
                .query_row(
                    "SELECT temporary_id FROM desktop_temporary_recovery ORDER BY updated_at_ms DESC, temporary_id ASC LIMIT 1",
                    [],
                    |row| row.get::<_, String>(0),
                )
                .ok()
        }).unwrap_or_else(|| {
            format!(
                "temporary-{}",
                &sha256(format!("{now_ms}-{}", local_now()).as_bytes())[..24]
            )
        });
        if !is_stable_id(&temporary_id) {
            return Err(json_error("临时会话 ID 无效"));
        }
        if let Ok(record) = connection.query_row(
            "SELECT recovery_json FROM desktop_temporary_recovery WHERE temporary_id=?1",
            [&temporary_id],
            |row| row.get::<_, String>(0),
        ) {
            return Self::parse_temporary_record(&record, &temporary_id).or_else(|error| {
                self.clear_temporary(&temporary_id)?;
                Err(error)
            });
        }
        let record = DesktopTemporaryConversationRecovery {
            schema_version: TEMPORARY_CONVERSATION_SCHEMA_VERSION,
            temporary_id: temporary_id.clone(),
            created_at_ms: now_ms,
            updated_at_ms: now_ms,
            draft: String::new(),
            messages: Vec::new(),
            model_override_id: None,
            attachments: Vec::new(),
            draft_attachment_ids: Vec::new(),
        };
        let json = serde_json::to_string(&record).map_err(|_| json_error("临时会话无法编码"))?;
        connection.execute(
            "INSERT INTO desktop_temporary_recovery(temporary_id,recovery_json,updated_at_ms) VALUES(?1,?2,?3)",
            params![temporary_id, json, now_ms],
        ).map_err(|_| json_error("临时会话无法保存"))?;
        Ok(record)
    }

    fn read_temporary_recovery(
        &self,
    ) -> Result<Option<DesktopTemporaryConversationRecovery>, String> {
        self.read_temporary_recovery_at(system_now_millis())
    }

    fn read_temporary_recovery_at(
        &self,
        now_ms: i64,
    ) -> Result<Option<DesktopTemporaryConversationRecovery>, String> {
        self.prune_temporary_at(now_ms)?;
        let connection = self.connection()?;
        let pair = connection
            .query_row(
                "SELECT temporary_id,recovery_json FROM desktop_temporary_recovery ORDER BY updated_at_ms DESC, temporary_id ASC LIMIT 1",
                [],
                |row| Ok((row.get::<_, String>(0)?, row.get::<_, String>(1)?)),
            )
            .ok();
        match pair {
            Some((temporary_id, json)) => Self::parse_temporary_record(&json, &temporary_id)
                .map(Some)
                .or_else(|error| {
                    self.clear_temporary(&temporary_id)?;
                    Err(error)
                }),
            None => Ok(None),
        }
    }

    fn parse_temporary_record(
        text: &str,
        expected_id: &str,
    ) -> Result<DesktopTemporaryConversationRecovery, String> {
        let record: DesktopTemporaryConversationRecovery =
            serde_json::from_str(text).map_err(|_| json_error("临时会话恢复记录无效"))?;
        let valid = record.schema_version == TEMPORARY_CONVERSATION_SCHEMA_VERSION
            && record.temporary_id == expected_id
            && is_stable_id(&record.temporary_id)
            && record.draft.chars().count() <= TEMPORARY_CONVERSATION_MAX_TEXT_CHARS
            && record.messages.len() <= 1_000
            && record.attachments.len() <= MAX_CONVERSATION_ATTACHMENT_COUNT
            && record.draft_attachment_ids.len() <= MAX_CONVERSATION_ATTACHMENT_COUNT
            && record
                .draft_attachment_ids
                .iter()
                .all(|id| record.attachments.iter().any(|item| &item.id == id))
            && record.messages.iter().all(|item| {
                item.role == "user"
                    && item.text.chars().count() <= TEMPORARY_CONVERSATION_MAX_TEXT_CHARS
            })
            && record
                .attachments
                .iter()
                .all(|item| is_stable_id(&item.id) && is_sha256(&item.sha256));
        if !valid {
            return Err(json_error("临时会话恢复记录不安全"));
        }
        Ok(record)
    }

    fn save_temporary(
        &self,
        record: &DesktopTemporaryConversationRecovery,
    ) -> Result<DesktopTemporaryConversationRecovery, String> {
        let json = serde_json::to_string(record).map_err(|_| json_error("临时会话无法编码"))?;
        let connection = self.connection()?;
        connection.execute(
            "UPDATE desktop_temporary_recovery SET recovery_json=?1,updated_at_ms=?2 WHERE temporary_id=?3",
            params![json, record.updated_at_ms, record.temporary_id],
        ).map_err(|_| json_error("临时会话无法保存"))?;
        Ok(record.clone())
    }

    fn update_temporary(
        &self,
        args: TemporaryConversationUpdateArgs,
    ) -> Result<DesktopTemporaryConversationRecovery, String> {
        self.update_temporary_at(args, system_now_millis())
    }

    fn update_temporary_at(
        &self,
        args: TemporaryConversationUpdateArgs,
        now_ms: i64,
    ) -> Result<DesktopTemporaryConversationRecovery, String> {
        let mut record = self.enter_or_restore_temporary_at(Some(args.temporary_id), now_ms)?;
        if args.draft.chars().count() > TEMPORARY_CONVERSATION_MAX_TEXT_CHARS
            || args
                .model_override_id
                .as_deref()
                .is_some_and(|value| !is_temporary_model_override_id(value))
        {
            return Err(json_error("临时会话草稿或模型标识无效"));
        }
        record.draft = args.draft;
        record.model_override_id = args.model_override_id;
        record.updated_at_ms = now_ms;
        self.save_temporary(&record)
    }

    fn append_temporary_message(
        &self,
        args: TemporaryConversationAppendArgs,
    ) -> Result<DesktopTemporaryConversationRecovery, String> {
        self.append_temporary_message_at(args, system_now_millis())
    }

    fn append_temporary_message_at(
        &self,
        args: TemporaryConversationAppendArgs,
        now_ms: i64,
    ) -> Result<DesktopTemporaryConversationRecovery, String> {
        let mut record = self.enter_or_restore_temporary_at(Some(args.temporary_id), now_ms)?;
        if record.draft.trim().is_empty() && record.draft_attachment_ids.is_empty() {
            return Err(json_error("请输入文字或保留附件后再发送"));
        }
        record.messages.push(DesktopTemporaryMessage {
            role: "user".into(),
            text: record.draft.trim().to_owned(),
            attachment_ids: record.draft_attachment_ids.clone(),
            created_at_ms: now_ms,
        });
        record.draft.clear();
        record.draft_attachment_ids.clear();
        record.updated_at_ms = now_ms;
        let connection = self.connection()?;
        let transaction = connection
            .unchecked_transaction()
            .map_err(|_| json_error("无法开启临时消息 transaction"))?;
        transaction.execute("UPDATE desktop_temporary_recovery SET recovery_json=?1,updated_at_ms=?2 WHERE temporary_id=?3", params![serde_json::to_string(&record).map_err(|_| json_error("临时会话无法编码"))?, record.updated_at_ms, record.temporary_id])
            .map_err(|_| json_error("临时消息无法保存"))?;
        transaction
            .commit()
            .map_err(|_| json_error("临时消息未提交；已回滚"))?;
        Ok(record)
    }

    fn clear_temporary(&self, temporary_id: &str) -> Result<(), String> {
        if !is_stable_id(temporary_id) {
            return Err(json_error("临时会话 ID 无效"));
        }
        let mut connection = self.connection()?;
        let transaction = connection
            .transaction()
            .map_err(|_| json_error("无法开启临时会话清除 transaction"))?;
        let attachments = transaction
            .prepare("SELECT sha256 FROM desktop_temporary_attachments WHERE temporary_id=?1")
            .map_err(|_| json_error("无法读取临时附件"))?
            .query_map([temporary_id], |row| row.get::<_, String>(0))
            .map_err(|_| json_error("无法读取临时附件"))?
            .collect::<Result<Vec<_>, _>>()
            .map_err(|_| json_error("临时附件无效"))?;
        transaction
            .execute(
                "DELETE FROM desktop_temporary_recovery WHERE temporary_id=?1",
                [temporary_id],
            )
            .map_err(|_| json_error("临时会话清除失败"))?;
        for sha256 in attachments {
            let mut normal_count = 0u64;
            let mut statement = transaction
                .prepare("SELECT exchange_json FROM workspace_exchange")
                .map_err(|_| json_error("无法读取普通附件引用"))?;
            let values = statement
                .query_map([], |row| row.get::<_, String>(0))
                .map_err(|_| json_error("无法读取普通附件引用"))?;
            for value in values {
                let exchange: Value =
                    serde_json::from_str(&value.map_err(|_| json_error("普通附件引用无效"))?)
                        .map_err(|_| json_error("普通附件引用无效"))?;
                normal_count += collect_asset_ref_counts(&exchange)?
                    .get(&sha256)
                    .copied()
                    .unwrap_or_default();
            }
            let temporary_count = transaction
                .query_row(
                    "SELECT COUNT(*) FROM desktop_temporary_attachments WHERE sha256=?1",
                    [&sha256],
                    |row| row.get::<_, i64>(0),
                )
                .map_err(|_| json_error("无法读取临时附件引用"))?;
            if normal_count == 0 && temporary_count == 0 {
                let path = self.root.join("assets").join(&sha256);
                if path.exists() {
                    fs::remove_file(path).map_err(|_| json_error("临时私有附件清理失败"))?;
                }
                transaction
                    .execute(
                        "DELETE FROM desktop_attachment_assets WHERE sha256=?1",
                        [&sha256],
                    )
                    .map_err(|_| json_error("临时附件元数据清理失败"))?;
                transaction
                    .execute(
                        "DELETE FROM desktop_conversation_attachments WHERE sha256=?1",
                        [&sha256],
                    )
                    .map_err(|_| json_error("临时附件普通引用清理失败"))?;
            }
        }
        transaction
            .commit()
            .map_err(|_| json_error("临时会话清除未提交；已回滚"))?;
        Ok(())
    }

    fn remove_temporary_attachment(
        &self,
        args: TemporaryConversationRemoveAttachmentArgs,
    ) -> Result<DesktopTemporaryConversationRecovery, String> {
        let mut record = self.enter_or_restore_temporary(Some(args.temporary_id))?;
        if !is_stable_id(&args.attachment_id)
            || !record.draft_attachment_ids.contains(&args.attachment_id)
        {
            return Err(json_error("附件不在当前临时草稿中"));
        }
        record
            .draft_attachment_ids
            .retain(|id| id != &args.attachment_id);
        record.updated_at_ms = system_now_millis();
        self.save_temporary(&record)
    }

    fn prune_temporary_at(&self, now_ms: i64) -> Result<(), String> {
        self.clear_expired_temporary_at(now_ms)?;
        self.run_attachment_maintenance_at(now_ms)?;
        Ok(())
    }

    fn clear_expired_temporary_at(&self, now_ms: i64) -> Result<(), String> {
        let cutoff = now_ms.saturating_sub(ATTACHMENT_RETENTION_MILLIS);
        let connection = self.connection()?;
        let ids = connection
            .prepare("SELECT temporary_id FROM desktop_temporary_recovery WHERE updated_at_ms<=?1")
            .map_err(|_| json_error("无法读取过期临时会话"))?
            .query_map([cutoff], |row| row.get::<_, String>(0))
            .map_err(|_| json_error("无法读取过期临时会话"))?
            .collect::<Result<Vec<_>, _>>()
            .map_err(|_| json_error("过期临时会话无效"))?;
        drop(connection);
        for temporary_id in ids {
            self.clear_temporary(&temporary_id)?;
        }
        Ok(())
    }

    fn import_temporary_attachment(
        &self,
        args: DesktopTemporaryAttachmentImportArgs,
    ) -> Result<DesktopTemporaryConversationRecovery, String> {
        self.import_temporary_attachment_at(args, system_now_millis())
    }

    fn import_temporary_attachment_at(
        &self,
        args: DesktopTemporaryAttachmentImportArgs,
        now_ms: i64,
    ) -> Result<DesktopTemporaryConversationRecovery, String> {
        let mut record = self.enter_or_restore_temporary_at(Some(args.temporary_id), now_ms)?;
        if record.draft_attachment_ids.len() >= MAX_CONVERSATION_ATTACHMENT_COUNT {
            return Err(json_error("每条临时消息最多保留 4 个附件"));
        }
        let source = PathBuf::from(&args.selected_path);
        let metadata = fs::symlink_metadata(&source).map_err(|_| json_error("所选附件不可读"))?;
        if !metadata.file_type().is_file()
            || metadata.file_type().is_symlink()
            || metadata.len() == 0
            || metadata.len() > MAX_CONVERSATION_ATTACHMENT_BYTES
        {
            return Err(json_error("附件必须是小于 20 MB 的普通文件"));
        }
        let mut input = fs::File::open(&source).map_err(|_| json_error("所选附件不可读"))?;
        let mut bytes = Vec::with_capacity(metadata.len() as usize);
        input
            .read_to_end(&mut bytes)
            .map_err(|_| json_error("所选附件读取失败"))?;
        let (mime_type, _) =
            desktop_attachment_kind(&bytes, source.extension().and_then(|value| value.to_str()))
                .ok_or_else(|| json_error("文件 MIME 或内容标识不受支持"))?;
        let sha256 = sha256(&bytes);
        if let Some(existing) = record.attachments.iter().find(|item| item.sha256 == sha256) {
            if !record.draft_attachment_ids.contains(&existing.id) {
                record.draft_attachment_ids.push(existing.id.clone());
                record.updated_at_ms = system_now_millis();
                return self.save_temporary(&record);
            }
            return Ok(record);
        }
        let display_name = source
            .file_name()
            .and_then(|value| value.to_str())
            .unwrap_or("本地附件");
        if display_name.contains("content://")
            || display_name.contains('/')
            || display_name.contains('\\')
            || display_name.chars().count() > 180
        {
            return Err(json_error("附件显示名无效"));
        }
        let attachment = DesktopAttachmentMetadata {
            id: format!("attachment-{}", &sha256[..24]),
            mime_type: mime_type.into(),
            display_name: display_name.into(),
            byte_count: bytes.len() as u64,
            sha256: sha256.clone(),
        };
        let target = self.root.join("assets").join(&sha256);
        if !target.exists() {
            fs::write(&target, &bytes).map_err(|_| json_error("附件私有复制失败"))?;
        }
        record.attachments.push(attachment.clone());
        record.draft_attachment_ids.push(attachment.id.clone());
        record.updated_at_ms = now_ms;
        let connection = self.connection()?;
        let transaction = connection
            .unchecked_transaction()
            .map_err(|_| json_error("无法开启临时附件 transaction"))?;
        transaction.execute("INSERT INTO desktop_attachment_assets(sha256,byte_count,created_at_ms,last_referenced_at_ms,last_unreferenced_at_ms,reference_count) VALUES(?1,?2,?3,NULL,?3,0) ON CONFLICT(sha256) DO NOTHING", params![sha256, attachment.byte_count, record.updated_at_ms])
            .map_err(|_| json_error("临时附件元数据未保存"))?;
        transaction.execute("INSERT INTO desktop_temporary_attachments(temporary_id,attachment_id,mime_type,display_name,byte_count,sha256) VALUES(?1,?2,?3,?4,?5,?6) ON CONFLICT(temporary_id,sha256) DO NOTHING", params![record.temporary_id, attachment.id, attachment.mime_type, attachment.display_name, attachment.byte_count, attachment.sha256])
            .map_err(|_| json_error("临时附件引用未保存"))?;
        transaction.execute("UPDATE desktop_temporary_recovery SET recovery_json=?1,updated_at_ms=?2 WHERE temporary_id=?3", params![serde_json::to_string(&record).map_err(|_| json_error("临时会话无法编码"))?, record.updated_at_ms, record.temporary_id])
            .map_err(|_| json_error("临时附件恢复记录未保存"))?;
        transaction
            .commit()
            .map_err(|_| json_error("临时附件未提交；已回滚"))?;
        Ok(record)
    }

    fn import_conversation_attachment(
        &self,
        args: DesktopAttachmentImportArgs,
    ) -> Result<DesktopAttachmentMetadata, String> {
        if !is_stable_id(&args.workspace_id) {
            return Err(json_error("workspace ID 无效"));
        }
        let source = PathBuf::from(&args.selected_path);
        let metadata = fs::symlink_metadata(&source).map_err(|_| json_error("所选附件不可读"))?;
        if !metadata.file_type().is_file()
            || metadata.file_type().is_symlink()
            || metadata.len() == 0
            || metadata.len() > MAX_CONVERSATION_ATTACHMENT_BYTES
        {
            return Err(json_error("附件必须是小于 20 MB 的普通文件"));
        }
        let mut input = fs::File::open(&source).map_err(|_| json_error("所选附件不可读"))?;
        let mut bytes = Vec::with_capacity(metadata.len() as usize);
        input
            .read_to_end(&mut bytes)
            .map_err(|_| json_error("所选附件读取失败"))?;
        let (mime_type, _extension) =
            desktop_attachment_kind(&bytes, source.extension().and_then(|value| value.to_str()))
                .ok_or_else(|| json_error("文件 MIME 或内容标识不受支持"))?;
        let sha256 = sha256(&bytes);
        let id = format!("attachment-{}", &sha256[..24]);
        let target = self.root.join("assets").join(&sha256);
        fs::create_dir_all(self.root.join("assets"))
            .map_err(|_| json_error("无法创建私有附件目录"))?;
        let now_ms = system_now_millis();
        if !target.exists() {
            let staging_id = format!("attachment-staging-{}", &sha256[..24]);
            let staging_name = format!("{staging_id}.pending");
            let staging = self.root.join("staging").join(&staging_name);
            // The partial copy has owner metadata before it becomes visible as an asset.  A crash
            // therefore leaves an auditable, 24-hour-bounded private staging candidate instead of
            // relying on a source path or filesystem mtime.
            fs::write(&staging, &bytes).map_err(|_| json_error("附件私有 staging 复制失败"))?;
            let connection = self.connection()?;
            connection.execute("INSERT OR REPLACE INTO desktop_attachment_staging(staging_id,staging_name,sha256,byte_count,created_at_ms) VALUES(?1,?2,?3,?4,?5)", params![staging_id, staging_name, sha256, bytes.len() as u64, now_ms])
                .map_err(|_| json_error("附件 staging 元数据未保存"))?;
            fs::rename(&staging, &target)
                .map_err(|_| json_error("附件私有 staging 无法原子发布"))?;
            let transaction = connection
                .unchecked_transaction()
                .map_err(|_| json_error("无法开启附件发布 transaction"))?;
            transaction.execute("INSERT INTO desktop_attachment_assets(sha256,byte_count,created_at_ms,last_referenced_at_ms,last_unreferenced_at_ms,reference_count) VALUES(?1,?2,?3,NULL,?3,0) ON CONFLICT(sha256) DO NOTHING", params![sha256, bytes.len() as u64, now_ms])
                .map_err(|_| json_error("附件资产元数据未保存"))?;
            transaction
                .execute(
                    "DELETE FROM desktop_attachment_staging WHERE staging_id=?1",
                    [&staging_id],
                )
                .map_err(|_| json_error("附件 staging 元数据未完成"))?;
            transaction
                .commit()
                .map_err(|_| json_error("附件私有发布未提交；已回滚"))?;
        }
        let display_name = source
            .file_name()
            .and_then(|value| value.to_str())
            .unwrap_or("本地附件");
        if display_name.contains("content://")
            || display_name.contains('/')
            || display_name.contains('\\')
            || display_name.chars().count() > 180
        {
            return Err(json_error("附件显示名无效"));
        }
        let metadata = DesktopAttachmentMetadata {
            id,
            mime_type: mime_type.to_owned(),
            display_name: display_name.to_owned(),
            byte_count: bytes.len() as u64,
            sha256,
        };
        let connection = self.connection()?;
        connection.execute("INSERT INTO desktop_attachment_assets(sha256,byte_count,created_at_ms,last_referenced_at_ms,last_unreferenced_at_ms,reference_count) VALUES(?1,?2,?3,NULL,?3,0) ON CONFLICT(sha256) DO NOTHING", params![metadata.sha256, metadata.byte_count, now_ms])
            .map_err(|_| json_error("附件资产元数据未保存"))?;
        connection.execute("INSERT INTO desktop_conversation_attachments(workspace_id, attachment_id, mime_type, display_name, byte_count, sha256, created_at) VALUES (?1,?2,?3,?4,?5,?6,?7) ON CONFLICT(workspace_id,sha256) DO NOTHING", params![args.workspace_id, metadata.id, metadata.mime_type, metadata.display_name, metadata.byte_count, metadata.sha256, local_now()])
            .map_err(|_| json_error("附件元数据未保存"))?;
        connection.query_row("SELECT attachment_id,mime_type,display_name,byte_count,sha256 FROM desktop_conversation_attachments WHERE workspace_id=?1 AND sha256=?2", params![args.workspace_id, metadata.sha256], |row| Ok(DesktopAttachmentMetadata { id: row.get(0)?, mime_type: row.get(1)?, display_name: row.get(2)?, byte_count: row.get(3)?, sha256: row.get(4)? }))
            .map_err(|_| json_error("附件元数据无法回读"))
    }

    fn stage_selected_file(&self, selected_path: &str) -> Result<PreflightReceipt, String> {
        let source = PathBuf::from(selected_path);
        if source.extension().and_then(|value| value.to_str()) != Some("nfai-exchange") {
            return Err(json_error("只接受 .nfai-exchange 文件"));
        }
        if fs::symlink_metadata(&source)
            .map_err(|_| json_error("所选文件不可读"))?
            .file_type()
            .is_symlink()
        {
            return Err(json_error("不接受符号链接文件"));
        }
        let bytes = fs::read(&source).map_err(|_| json_error("所选文件不可读"))?;
        let preflight = preflight_package(bytes)?;
        let staging = self
            .root
            .join("staging")
            .join(format!("{}.nfai-exchange", preflight.receipt.staging_id));
        fs::write(staging, &preflight.bytes).map_err(|_| json_error("无法写入私有 staging"))?;
        Ok(preflight.receipt)
    }

    /// The only P6-K picker path: selected archive -> private staging -> strict adapter ->
    /// immediate transaction per safe conversation.  No confirmation or alternate message store.
    fn stage_p6k_zip_import_selected(&self, args: P6kZipImportSelectionArgs) -> Result<P6kZipImportTaskProjection, String> {
        if !is_stable_id(&args.workspace_id) || !matches!(args.provider.as_str(), "CHATGPT" | "CLAUDE") { return Err(json_error("ZIP 导入引用无效")); }
        self.workspace_projection(&args.workspace_id)?;
        let source = PathBuf::from(&args.selected_path);
        let display_name = source.file_name().and_then(|value| value.to_str()).ok_or_else(|| json_error("所选 ZIP 文件名无效"))?;
        let metadata = fs::symlink_metadata(&source).map_err(|_| json_error("所选 ZIP 文件不可读"))?;
        if !display_name.to_ascii_lowercase().ends_with(".zip") || metadata.file_type().is_symlink() || metadata.len() == 0 || metadata.len() > P6K_ZIP_MAX_ARCHIVE_BYTES { return Err(json_error("只接受安全范围内的用户选择 ZIP，且不接受符号链接")); }
        let id = format!("p6k-zip-{}", local_now_millis());
        let storage_key = format!("p6k-zip-import-assets/{id}/package.zip"); let target = self.root.join(&storage_key);
        let parent = target.parent().ok_or_else(|| json_error("ZIP 私有副本路径无效"))?; fs::create_dir_all(parent).map_err(|_| json_error("无法创建 ZIP 私有副本"))?;
        let temporary = parent.join(".package.part"); fs::copy(&source, &temporary).map_err(|_| json_error("无法复制 ZIP 私有副本"))?; fs::rename(&temporary, &target).map_err(|_| json_error("无法发布 ZIP 私有副本"))?;
        let package_hash = sha256_file(&target)?;
        let inventory = match p6k_inventory(&target) {
            Ok(inventory) => inventory,
            Err(reason) => { let _ = fs::remove_file(&target); return Err(json_error(reason)); }
        };
        let (candidates, assets, profile_status, mapped_profile_field_count, profile_value) = match p6k_zip_candidates(&target, &args.provider) {
            Ok(candidates) => candidates,
            Err(reason) => { let _ = fs::remove_file(&target); return Err(reason); }
        };
        let now = local_now_millis(); let mut connection = self.connection()?; let transaction = connection.transaction().map_err(|_| json_error("无法开启 ZIP 导入 transaction"))?;
        transaction.execute("INSERT INTO p6k_zip_import_tasks(id,provider,status,storage_key,display_name,byte_count,package_hash,failure,entry_count,workspace_id,created_at_ms,updated_at_ms) VALUES(?1,?2,'STAGED',?3,?4,?5,?6,NULL,?7,?8,?9,?9)", params![id,args.provider,storage_key,display_name,metadata.len(),package_hash,inventory.len(),args.workspace_id,now]).map_err(|_| json_error("无法保存 ZIP 导入任务"))?;
        for (ordinal, entry) in inventory.iter().enumerate() { transaction.execute("INSERT INTO p6k_zip_import_entries(task_id,ordinal,entry_name,uncompressed_bytes,compressed_bytes,mime_type) VALUES(?1,?2,?3,?4,?5,?6)", params![id,ordinal,entry.0,entry.1,entry.2,entry.3]).map_err(|_| json_error("无法保存 ZIP 清单"))?; }
        for (ordinal, candidate) in candidates.iter().enumerate() { let item_id = format!("p6k-item-{ordinal}"); match candidate { Ok(candidate) => { transaction.execute("INSERT INTO p6k_zip_import_items(task_id,id,ordinal,source_conversation_id,title,content_hash,status,failure,conversation_id) VALUES(?1,?2,?3,?4,?5,?6,'PENDING',NULL,NULL)", params![id,item_id,ordinal,candidate.source_conversation_id,candidate.title,candidate.content_hash]).map_err(|_| json_error("无法保存 ZIP 会话候选"))?; for message in &candidate.messages { transaction.execute("INSERT INTO p6k_zip_import_messages(task_id,item_id,source_message_id,parent_source_message_id,sibling_position,role,text,created_at_ms,imported_model) VALUES(?1,?2,?3,?4,?5,?6,?7,?8,?9)", params![id,item_id,message.source_id,message.parent_source_id,message.sibling_position,message.role,message.text,message.created_at_ms,message.imported_model]).map_err(|_| json_error("无法保存 ZIP 消息候选"))?; } }, Err(reason) if reason == P6K_GRAPH_MERGED => { transaction.execute("INSERT INTO p6k_zip_import_items(task_id,id,ordinal,source_conversation_id,title,content_hash,status,failure,conversation_id) VALUES(?1,?2,?3,NULL,NULL,NULL,'SKIPPED',NULL,NULL)", params![id,item_id,ordinal]).map_err(|_| json_error("无法保存 ZIP 合并片段"))?; }, Err(reason) => { transaction.execute("INSERT INTO p6k_zip_import_items(task_id,id,ordinal,source_conversation_id,title,content_hash,status,failure,conversation_id) VALUES(?1,?2,?3,NULL,NULL,NULL,'FAILED',?4,NULL)", params![id,item_id,ordinal,reason]).map_err(|_| json_error("无法保存 ZIP 失败候选"))?; } } }
        for (ordinal, asset) in assets.iter().enumerate() { transaction.execute("INSERT INTO p6k_zip_import_asset_candidates(task_id,ordinal,entry_name,sha256,byte_count,mime_type,status) VALUES(?1,?2,?3,?4,?5,?6,'UNMAPPED_REJECTED')", params![id,ordinal,asset.0,asset.3,asset.1,asset.2]).map_err(|_| json_error("无法保存未关联媒体统计"))?; }
        transaction.execute("INSERT INTO p6k_zip_import_profile_candidates(task_id,status,mapped_field_count) VALUES(?1,?2,?3)", params![id,profile_status,mapped_profile_field_count]).map_err(|_| json_error("无法保存资料边界状态"))?;
        transaction.commit().map_err(|_| json_error("ZIP 导入任务未提交；已回滚"))?;
        if let Some(profile) = profile_value.as_ref() { self.commit_p6k_profile_personalization(&id, profile)?; }
        self.run_p6k_zip_import_task(&id)?; self.read_p6k_zip_import_task(&id)
    }

    /// K6's one Desktop settings owner.  The task's safe status and the canonical profile,
    /// receipt and provenance change together; no raw export JSON or account data is retained.
    fn commit_p6k_profile_personalization(&self, task_id: &str, profile: &P6kProfilePersonalization) -> Result<(), String> {
        let mut connection = self.connection()?; let transaction = connection.transaction().map_err(|_| json_error("无法开启资料设置 transaction"))?;
        let (provider, package_hash): (String,String) = transaction.query_row("SELECT provider,package_hash FROM p6k_zip_import_tasks WHERE id=?1", [task_id], |row| Ok((row.get(0)?,row.get(1)?))).map_err(|_| json_error("ZIP 资料任务不存在"))?;
        let canonical_hash = profile.canonical_hash()?;
        if let Some(existing_hash) = transaction.query_row("SELECT canonical_hash FROM p6k_profile_import_receipts WHERE provider=?1 AND package_hash=?2", params![provider,package_hash], |row| row.get::<_,String>(0)).optional().map_err(|_| json_error("无法读取资料 receipt"))? {
            let status = if existing_hash == canonical_hash { "OWNER_COMMITTED" } else { "CONFLICT_PROFILE_REIMPORT" };
            transaction.execute("UPDATE p6k_zip_import_profile_candidates SET status=?2 WHERE task_id=?1", params![task_id,status]).map_err(|_| json_error("无法更新资料状态"))?;
            return transaction.commit().map_err(|_| json_error("资料重放未提交"));
        }
        let current = transaction.query_row("SELECT source_task_id,revision FROM p6k_profile_personalization_settings WHERE id=1", [], |row| Ok((row.get::<_,String>(0)?,row.get::<_,i64>(1)?))).optional().map_err(|_| json_error("无法读取资料设置"))?;
        if current.as_ref().is_some_and(|(source,_)| source != task_id) {
            transaction.execute("UPDATE p6k_zip_import_profile_candidates SET status='CONFLICT_PROFILE_OWNER' WHERE task_id=?1", [task_id]).map_err(|_| json_error("无法更新资料冲突状态"))?;
            return transaction.commit().map_err(|_| json_error("资料冲突未提交"));
        }
        let revision=current.map(|(_,revision)| revision+1).unwrap_or(1); let now=local_now_millis();
        transaction.execute("INSERT OR REPLACE INTO p6k_profile_personalization_settings(id,display_name,language,timezone,public_bio,custom_instructions,theme,notifications_enabled,source_task_id,revision,updated_at_ms) VALUES(1,?1,?2,?3,?4,?5,?6,?7,?8,?9,?10)", params![profile.display_name,profile.language,profile.timezone,profile.public_bio,profile.custom_instructions,profile.theme,profile.notifications_enabled.map(|v|if v {1} else {0}),task_id,revision,now]).map_err(|_| json_error("无法保存资料设置"))?;
        transaction.execute("INSERT INTO p6k_profile_import_provenance(task_id,provider,package_hash,canonical_hash,imported_at_ms) VALUES(?1,?2,?3,?4,?5)", params![task_id,provider,package_hash,canonical_hash,now]).map_err(|_| json_error("无法保存资料 provenance"))?;
        transaction.execute("INSERT INTO p6k_profile_import_receipts(provider,package_hash,task_id,canonical_hash,committed_at_ms) VALUES(?1,?2,?3,?4,?5)", params![provider,package_hash,task_id,canonical_hash,now]).map_err(|_| json_error("无法保存资料 receipt"))?;
        transaction.execute("UPDATE p6k_zip_import_profile_candidates SET status='OWNER_COMMITTED' WHERE task_id=?1", [task_id]).map_err(|_| json_error("无法完成资料状态"))?;
        transaction.commit().map_err(|_| json_error("资料设置未提交"))
    }

    fn read_p6k_profile_personalization(&self) -> Result<Option<P6kProfilePersonalization>, String> {
        let connection=self.connection()?;
        connection.query_row("SELECT display_name,language,timezone,public_bio,custom_instructions,theme,notifications_enabled FROM p6k_profile_personalization_settings WHERE id=1", [], |row| Ok(P6kProfilePersonalization { display_name:row.get(0)?,language:row.get(1)?,timezone:row.get(2)?,public_bio:row.get(3)?,custom_instructions:row.get(4)?,theme:row.get(5)?,notifications_enabled:row.get::<_,Option<i64>>(6)?.map(|value|value!=0) })).optional().map_err(|_| json_error("无法读取资料设置"))
    }

    fn p6k_profile_personalization_settings_status(&self) -> Result<P6kProfilePersonalizationSettingsStatusProjection, String> {
        let value = self.read_p6k_profile_personalization()?;
        Ok(P6kProfilePersonalizationSettingsStatusProjection { applied: value.is_some(), mapped_field_count: value.as_ref().map(P6kProfilePersonalization::field_count).unwrap_or(0) })
    }

    fn p6k_update_task_status(connection: &Connection, task_id: &str) -> Result<(), String> {
        connection.execute("UPDATE p6k_zip_import_tasks SET status=CASE WHEN EXISTS(SELECT 1 FROM p6k_zip_import_items WHERE task_id=?1 AND status='PENDING') THEN 'PARTIALLY_COMPLETED' WHEN EXISTS(SELECT 1 FROM p6k_zip_import_items WHERE task_id=?1 AND status='FAILED') THEN 'PARTIALLY_COMPLETED' ELSE 'COMPLETED' END,failure=CASE WHEN EXISTS(SELECT 1 FROM p6k_zip_import_items WHERE task_id=?1 AND status='FAILED') THEN 'CANDIDATE_REJECTED' ELSE NULL END,updated_at_ms=?2 WHERE id=?1", params![task_id,local_now_millis()]).map_err(|_| json_error("无法更新 ZIP 导入状态"))?; Ok(())
    }

    fn run_p6k_zip_import_task(&self, task_id: &str) -> Result<(), String> {
        let connection = self.connection()?; let pending = connection.prepare("SELECT id FROM p6k_zip_import_items WHERE task_id=?1 AND status='PENDING' ORDER BY ordinal").map_err(|_| json_error("无法读取 ZIP 待提交项"))?.query_map([task_id], |row| row.get::<_,String>(0)).map_err(|_| json_error("无法读取 ZIP 待提交项"))?.collect::<Result<Vec<_>,_>>().map_err(|_| json_error("无法读取 ZIP 待提交项"))?;
        for item_id in pending {
            if let Err(reason) = self.commit_p6k_zip_import_item(task_id, &item_id) {
                let connection = self.connection()?;
                connection.execute("UPDATE p6k_zip_import_items SET status='FAILED',failure=?3 WHERE task_id=?1 AND id=?2", params![task_id, item_id, reason]).map_err(|_| json_error("无法回写 ZIP 失败候选"))?;
            }
        }
        let connection = self.connection()?; Self::p6k_update_task_status(&connection, task_id)
    }

    fn commit_p6k_zip_import_item(&self, task_id: &str, item_id: &str) -> Result<(), String> {
        let mut connection = self.connection()?; let transaction = connection.transaction().map_err(|_| json_error("无法开启 ZIP 会话提交 transaction"))?;
        let (workspace_id, provider, package_hash): (String,String,String) = transaction.query_row("SELECT workspace_id,provider,package_hash FROM p6k_zip_import_tasks WHERE id=?1", [task_id], |row| Ok((row.get(0)?,row.get(1)?,row.get(2)?))).map_err(|_| json_error("ZIP 导入任务不存在"))?;
        let (source_id, title, content_hash, status): (String,String,String,String) = transaction.query_row("SELECT source_conversation_id,title,content_hash,status FROM p6k_zip_import_items WHERE task_id=?1 AND id=?2", params![task_id,item_id], |row| Ok((row.get(0)?,row.get(1)?,row.get(2)?,row.get(3)?))).map_err(|_| json_error("ZIP 会话候选不存在"))?;
        if status != "PENDING" { transaction.commit().map_err(|_| json_error("无法结束 ZIP 重放读取"))?; return Ok(()); }
        if let Ok((conversation_id, existing_hash)) = transaction.query_row("SELECT conversation_id,content_hash FROM p6k_zip_import_receipts WHERE workspace_id=?1 AND provider=?2 AND source_conversation_id=?3 AND package_hash=?4", params![workspace_id,provider,source_id,package_hash], |row| Ok((row.get::<_,String>(0)?,row.get::<_,String>(1)?))) { if existing_hash != content_hash { return Err(json_error("同源不同内容，需重新选择 ZIP")); } transaction.execute("UPDATE p6k_zip_import_items SET status='COMMITTED',conversation_id=?3 WHERE task_id=?1 AND id=?2", params![task_id,item_id,conversation_id]).map_err(|_| json_error("无法回写 ZIP 幂等结果"))?; transaction.commit().map_err(|_| json_error("ZIP 重放结果未提交"))?; return Ok(()); }
        let messages = transaction.prepare("SELECT source_message_id,parent_source_message_id,sibling_position,role,text,created_at_ms,imported_model FROM p6k_zip_import_messages WHERE task_id=?1 AND item_id=?2 ORDER BY sibling_position,source_message_id").map_err(|_| json_error("无法读取 ZIP 消息候选"))?.query_map(params![task_id,item_id], |row| Ok((row.get::<_,String>(0)?,row.get::<_,Option<String>>(1)?,row.get::<_,i64>(2)?,row.get::<_,String>(3)?,row.get::<_,String>(4)?,row.get::<_,i64>(5)?,row.get::<_,Option<String>>(6)?))).map_err(|_| json_error("ZIP 消息候选无效"))?.collect::<Result<Vec<_>,_>>().map_err(|_| json_error("ZIP 消息候选无法读取"))?;
        if messages.is_empty() { return Err(json_error("ZIP 候选没有安全消息")); }
        let exchange_text: String = transaction.query_row("SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?1", [&workspace_id], |row| row.get(0)).map_err(|_| json_error("工作区不存在"))?; let mut exchange: Value = serde_json::from_str(&exchange_text).map_err(|_| json_error("本地工作区无法读取"))?;
        let conversation_id = format!("conversation-p6k-{}", &sha256(format!("{provider}:{source_id}:{package_hash}").as_bytes())[..24]);
        let ids = messages.iter().map(|message| (message.0.clone(),format!("message-p6k-{}-{}", &sha256(task_id.as_bytes())[..12],message.0))).collect::<BTreeMap<_,_>>();
        let leaf = messages.iter().filter(|message| !messages.iter().any(|other| other.1.as_deref()==Some(&message.0))).max_by_key(|message| (message.5,message.0.clone())).map(|message| ids[&message.0].clone()).ok_or_else(|| json_error("ZIP current leaf 无效"))?;
        let imported_messages = messages.iter().map(|message| {
            let parent_id = message.1.as_ref().map(|parent| ids.get(parent).cloned().ok_or_else(|| json_error("ZIP 父消息不在安全候选内"))).transpose()?;
            Ok(json!({"id":ids[&message.0],"parentId":parent_id,"ordinal":message.2,"role":message.3,"delivery":"COMPLETE","revision":1,"createdAt":rfc3339_from_unix_millis(message.5),"importedAtEpochMs":message.5,"importedModel":message.6,"blocks":[{"kind":"TEXT","text":message.4}]}))
        }).collect::<Result<Vec<_>, String>>()?;
        let conversation = json!({"id":conversation_id,"title":title,"revision":1,"projectId":Value::Null,"pinned":false,"archived":false,"deleted":false,"createdAt":rfc3339_from_unix_millis(messages[0].5),"updatedAt":rfc3339_from_unix_millis(messages.iter().map(|message| message.5).max().unwrap_or(messages[0].5)),"importedFrom":format!("{}_ZIP",provider),"currentLeafId":leaf,"messages":imported_messages});
        exchange.as_object_mut().ok_or_else(|| json_error("本地工作区根无效"))?.get_mut("conversations").and_then(Value::as_array_mut).ok_or_else(|| json_error("本地会话集合无效"))?.push(conversation); let semantic_hash = refresh_exchange_hash(&mut exchange)?; validate_exchange(&exchange)?;
        transaction.execute("UPDATE workspace_exchange SET exchange_json=?1 WHERE workspace_id=?2", params![canonical_json(&exchange)?,workspace_id]).map_err(|_| json_error("ZIP 会话未写入"))?; self.rebuild_local_search_index(&transaction,&workspace_id,&exchange)?; transaction.execute("UPDATE workspaces SET semantic_hash=?1 WHERE id=?2",params![semantic_hash,workspace_id]).map_err(|_| json_error("ZIP workspace hash 未更新"))?;
        transaction.execute("INSERT INTO p6k_zip_import_provenance(conversation_id,task_id,workspace_id,provider,source_conversation_id,package_hash,content_hash,imported_at_ms,adapter_id,adapter_version) VALUES(?1,?2,?3,?4,?5,?6,?7,?8,'p6k-zip',2)",params![conversation_id,task_id,workspace_id,provider,source_id,package_hash,content_hash,local_now_millis()]).map_err(|_| json_error("ZIP provenance 未写入"))?; transaction.execute("INSERT INTO p6k_zip_import_receipts(workspace_id,provider,source_conversation_id,package_hash,conversation_id,content_hash,committed_at_ms) VALUES(?1,?2,?3,?4,?5,?6,?7)",params![workspace_id,provider,source_id,package_hash,conversation_id,content_hash,local_now_millis()]).map_err(|_| json_error("ZIP receipt 未写入"))?; transaction.execute("UPDATE p6k_zip_import_items SET status='COMMITTED',conversation_id=?3 WHERE task_id=?1 AND id=?2",params![task_id,item_id,conversation_id]).map_err(|_| json_error("ZIP 项目状态未写入"))?; transaction.commit().map_err(|_| json_error("ZIP 会话提交未完成；已回滚"))
    }

    fn read_p6k_zip_import_task(&self, task_id: &str) -> Result<P6kZipImportTaskProjection, String> {
        let connection = self.connection()?; let (id,provider,status,display_name,byte_count,failure,entry_count): (String,String,String,String,u64,Option<String>,i64) = connection.query_row("SELECT id,provider,status,display_name,byte_count,failure,entry_count FROM p6k_zip_import_tasks WHERE id=?1",[task_id],|row| Ok((row.get(0)?,row.get(1)?,row.get(2)?,row.get(3)?,row.get(4)?,row.get(5)?,row.get(6)?))).map_err(|_| json_error("ZIP 导入任务不存在"))?;
        let items = connection.prepare("SELECT id,ordinal,status,failure,conversation_id FROM p6k_zip_import_items WHERE task_id=?1 ORDER BY ordinal").map_err(|_| json_error("ZIP 项目无法读取"))?.query_map([task_id],|row| Ok(P6kZipImportItemProjection{id:row.get(0)?,ordinal:row.get(1)?,status:row.get(2)?,failure:row.get(3)?,conversation_id:row.get(4)?})).map_err(|_| json_error("ZIP 项目无效"))?.collect::<Result<Vec<_>,_>>().map_err(|_| json_error("ZIP 项目无法回读"))?;
        let manual_assets = connection.prepare("SELECT ordinal,mime_type,byte_count,status FROM p6k_zip_import_asset_candidates WHERE task_id=?1 ORDER BY ordinal").map_err(|_| json_error("ZIP 媒体候选无法读取"))?.query_map([task_id], |row| Ok(P6kManualAssetProjection { ordinal:row.get(0)?, mime_type:row.get(1)?, byte_count:row.get(2)?, status:row.get(3)? })).map_err(|_| json_error("ZIP 媒体候选无效"))?.collect::<Result<Vec<_>,_>>().map_err(|_| json_error("ZIP 媒体候选无法回读"))?;
        let unmapped_asset_count = manual_assets.iter().filter(|asset| asset.status == "UNMAPPED_REJECTED" || asset.status == "MANUAL_LINK_FAILED").count(); let (profile_status,profile_mapped_field_count) = connection.query_row("SELECT status,mapped_field_count FROM p6k_zip_import_profile_candidates WHERE task_id=?1",[task_id],|row| Ok((row.get::<_,String>(0)?,row.get::<_,i64>(1)? as usize))).unwrap_or_else(|_| ("NO_SAFE_PROFILE_FIELDS".into(),0)); let imported_count=items.iter().filter(|item|item.status=="COMMITTED").count(); let failed_count=items.iter().filter(|item|item.status=="FAILED").count(); let skipped_count=items.iter().filter(|item|item.status=="SKIPPED").count(); Ok(P6kZipImportTaskProjection{id,provider,status,display_name,byte_count,failure,entry_count:entry_count as usize,imported_count,failed_count,skipped_count,unmapped_asset_count,profile_status,profile_mapped_field_count,items,manual_assets})
    }

    fn read_latest_p6k_zip_import_task(&self) -> Result<Option<P6kZipImportTaskProjection>, String> { let connection=self.connection()?; let id=connection.query_row("SELECT id FROM p6k_zip_import_tasks ORDER BY updated_at_ms DESC LIMIT 1",[],|row|row.get::<_,String>(0)).optional().map_err(|_|json_error("ZIP 导入任务无法读取"))?; id.map(|task_id|self.read_p6k_zip_import_task(&task_id)).transpose() }

    /// K8's only Desktop path from an unassociated archived media entry to the existing
    /// attachment owner.  It never derives a target from entry names, and performs no archive
    /// extraction until this explicit target has passed provenance and message-tree checks.
    fn link_p6k_zip_manual_asset(&self, args: P6kManualAssetLinkArgs) -> Result<P6kZipImportTaskProjection, String> {
        if !is_stable_id(&args.task_id) || !is_stable_id(&args.workspace_id) || !is_stable_id(&args.conversation_id) || !is_stable_id(&args.message_id) || args.asset_ordinal < 0 { return Err(json_error("人工关联引用无效")); }
        let connection = self.connection()?;
        let prior: Option<(String,String,String)> = connection.query_row("SELECT sha256,conversation_id,message_id FROM p6k_zip_asset_link_receipts WHERE task_id=?1 AND ordinal=?2", params![args.task_id,args.asset_ordinal], |row| Ok((row.get(0)?,row.get(1)?,row.get(2)?))).optional().map_err(|_| json_error("人工关联回执无法读取"))?;
        if let Some((_sha, conversation_id, message_id)) = prior { if conversation_id == args.conversation_id && message_id == args.message_id { return self.read_p6k_zip_import_task(&args.task_id); } return Err(json_error("该媒体已归属另一条消息")); }
        let source: Result<(), String> = (|| {
            let (storage_key, candidate_workspace, entry_name, expected_sha, expected_bytes, expected_mime): (String,String,String,String,u64,String) = connection.query_row("SELECT t.storage_key,t.workspace_id,a.entry_name,a.sha256,a.byte_count,a.mime_type FROM p6k_zip_import_tasks t JOIN p6k_zip_import_asset_candidates a ON a.task_id=t.id WHERE t.id=?1 AND a.ordinal=?2 AND a.status IN ('UNMAPPED_REJECTED','MANUAL_LINK_FAILED')", params![args.task_id,args.asset_ordinal], |row| Ok((row.get(0)?,row.get(1)?,row.get(2)?,row.get(3)?,row.get(4)?,row.get(5)?))).map_err(|_| json_error("未关联媒体候选不存在"))?;
            if candidate_workspace != args.workspace_id || !storage_key.starts_with(&format!("p6k-zip-import-assets/{}/", args.task_id)) || !safe_entry(&entry_name) { return Err(json_error("ZIP 私有媒体引用无效")); }
            let belongs: i64 = connection.query_row("SELECT COUNT(*) FROM p6k_zip_import_provenance WHERE task_id=?1 AND workspace_id=?2 AND conversation_id=?3", params![args.task_id,args.workspace_id,args.conversation_id], |row| row.get(0)).map_err(|_| json_error("ZIP provenance 无法读取"))?;
            if belongs != 1 { return Err(json_error("目标不是该 ZIP 已导入会话")); }
            let exchange_text:String = connection.query_row("SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?1", [&args.workspace_id], |row| row.get(0)).map_err(|_| json_error("目标工作区不存在"))?;
            let mut exchange:Value = serde_json::from_str(&exchange_text).map_err(|_| json_error("本地工作区无法读取"))?;
            let target_message = exchange.get("conversations").and_then(Value::as_array).and_then(|conversations| conversations.iter().find(|conversation| conversation.get("id").and_then(Value::as_str)==Some(args.conversation_id.as_str()))).and_then(|conversation| conversation.get("messages")).and_then(Value::as_array).and_then(|messages| messages.iter().find(|message| message.get("id").and_then(Value::as_str)==Some(args.message_id.as_str()))).ok_or_else(|| json_error("目标消息不存在"))?;
            let blocks = target_message.get("blocks").and_then(Value::as_array).ok_or_else(|| json_error("目标消息 blocks 无效"))?;
            if blocks.len() > MAX_CONVERSATION_ATTACHMENT_COUNT { return Err(json_error("目标消息附件数量已达上限")); }
            let archive = fs::File::open(self.root.join(&storage_key)).map_err(|_| json_error("ZIP 私有副本不可读"))?; let mut zip = ZipArchive::new(archive).map_err(|_| json_error("ZIP 私有副本无效"))?; let mut entry = zip.by_name(&entry_name).map_err(|_| json_error("ZIP 媒体条目不存在"))?;
            if entry.is_dir() || entry.name().replace('\\', "/") != entry_name || entry.size() != expected_bytes || expected_bytes == 0 || expected_bytes > MAX_CONVERSATION_ATTACHMENT_BYTES { return Err(json_error("ZIP 媒体元数据不匹配")); }
            let mut bytes=Vec::with_capacity(expected_bytes as usize); entry.read_to_end(&mut bytes).map_err(|_| json_error("ZIP 媒体条目不可读"))?; if bytes.len() as u64 != expected_bytes || sha256(&bytes) != expected_sha { return Err(json_error("ZIP 媒体 hash 不匹配")); }
            let extension=entry_name.rsplit('.').next().filter(|value| *value != entry_name.as_str()); let (mime_type,_)=desktop_attachment_kind(&bytes,extension).ok_or_else(|| json_error("ZIP 媒体 MIME 或内容标识不受支持"))?; if mime_type != expected_mime { return Err(json_error("ZIP 媒体 MIME 不匹配")); }
            let attachment_id=format!("attachment-{}",&expected_sha[..24]); let attachment=json!({"id":attachment_id,"mimeType":mime_type,"displayName":"已关联媒体","byteCount":expected_bytes,"sha256":expected_sha,"entry":format!("assets/{expected_sha}")});
            let target = self.root.join("assets").join(&expected_sha); fs::create_dir_all(self.root.join("assets")).map_err(|_| json_error("无法创建私有附件目录"))?; if !target.exists() { fs::write(&target,&bytes).map_err(|_| json_error("ZIP 媒体私有提取失败"))?; }
            let conversation = exchange.get_mut("conversations").and_then(Value::as_array_mut).and_then(|conversations| conversations.iter_mut().find(|conversation| conversation.get("id").and_then(Value::as_str)==Some(args.conversation_id.as_str()))).ok_or_else(|| json_error("目标会话不存在"))?; let message = conversation.get_mut("messages").and_then(Value::as_array_mut).and_then(|messages| messages.iter_mut().find(|message| message.get("id").and_then(Value::as_str)==Some(args.message_id.as_str()))).ok_or_else(|| json_error("目标消息不存在"))?; let message_blocks=message.get_mut("blocks").and_then(Value::as_array_mut).ok_or_else(||json_error("目标消息 blocks 无效"))?;
            if message_blocks.iter().any(|block| block.get("kind").and_then(Value::as_str)==Some("ASSET_REF") && block.get("asset").and_then(|asset|asset.get("sha256")).and_then(Value::as_str)==Some(expected_sha.as_str())) { return Err(json_error("目标消息已存在相同媒体")); }
            let ordinal=message_blocks.len(); message_blocks.push(json!({"kind":"ASSET_REF","ordinal":ordinal,"asset":attachment})); let semantic_hash=refresh_exchange_hash(&mut exchange)?; validate_exchange(&exchange)?;
            let mut tx_connection=self.connection()?; let transaction=tx_connection.transaction().map_err(|_|json_error("无法开启人工关联 transaction"))?; let now=local_now_millis(); transaction.execute("INSERT INTO desktop_attachment_assets(sha256,byte_count,created_at_ms,last_referenced_at_ms,last_unreferenced_at_ms,reference_count) VALUES(?1,?2,?3,NULL,?3,0) ON CONFLICT(sha256) DO NOTHING",params![expected_sha,expected_bytes,now]).map_err(|_|json_error("附件资产元数据未保存"))?; transaction.execute("INSERT INTO desktop_conversation_attachments(workspace_id,attachment_id,mime_type,display_name,byte_count,sha256,created_at) VALUES(?1,?2,?3,'已关联媒体',?4,?5,?6) ON CONFLICT(workspace_id,sha256) DO NOTHING",params![args.workspace_id,attachment_id,mime_type,expected_bytes,expected_sha,local_now()]).map_err(|_|json_error("附件元数据未保存"))?; transaction.execute("UPDATE workspace_exchange SET exchange_json=?1 WHERE workspace_id=?2",params![canonical_json(&exchange)?,args.workspace_id]).map_err(|_|json_error("关联消息未写入"))?; self.rebuild_local_search_index(&transaction,&args.workspace_id,&exchange)?; transaction.execute("UPDATE workspaces SET semantic_hash=?1 WHERE id=?2",params![semantic_hash,args.workspace_id]).map_err(|_|json_error("workspace hash 未更新"))?; transaction.execute("UPDATE p6k_zip_import_asset_candidates SET status='MANUAL_LINKED' WHERE task_id=?1 AND ordinal=?2",params![args.task_id,args.asset_ordinal]).map_err(|_|json_error("媒体关联状态未写入"))?; transaction.execute("INSERT INTO p6k_zip_asset_link_receipts(task_id,ordinal,sha256,attachment_id,workspace_id,conversation_id,message_id,committed_at_ms) VALUES(?1,?2,?3,?4,?5,?6,?7,?8)",params![args.task_id,args.asset_ordinal,expected_sha,attachment_id,args.workspace_id,args.conversation_id,args.message_id,now]).map_err(|_|json_error("媒体关联回执未写入"))?; transaction.commit().map_err(|_|json_error("人工关联未提交；已回滚"))?;
            Ok(())
        })();
        if let Err(error) = source { let _ = connection.execute("UPDATE p6k_zip_import_asset_candidates SET status='MANUAL_LINK_FAILED' WHERE task_id=?1 AND ordinal=?2 AND status!='MANUAL_LINKED'",params![args.task_id,args.asset_ordinal]); return Err(error); }
        self.read_p6k_zip_import_task(&args.task_id)
    }

    fn refresh_uncommitted_p6k_zip_import_task(&self, task_id: &str) -> Result<Option<P6kProfilePersonalization>, String> {
        let connection = self.connection()?;
        let (provider, storage_key): (String, String) = connection.query_row("SELECT provider,storage_key FROM p6k_zip_import_tasks WHERE id=?1", [task_id], |row| Ok((row.get(0)?, row.get(1)?))).map_err(|_| json_error("ZIP 导入任务不存在"))?;
        let expected_prefix = format!("p6k-zip-import-assets/{task_id}/");
        if !storage_key.starts_with(&expected_prefix) { return Err(json_error("ZIP 私有副本引用无效")); }
        let (candidates, assets, profile_status, mapped_profile_field_count, profile_value) = p6k_zip_candidates(&self.root.join(&storage_key), &provider)?;
        let mut connection = self.connection()?; let transaction = connection.transaction().map_err(|_| json_error("无法开启 ZIP 重解析 transaction"))?;
        let committed: i64 = transaction.query_row("SELECT COUNT(*) FROM p6k_zip_import_provenance WHERE task_id=?1", [task_id], |row| row.get(0)).map_err(|_| json_error("无法读取 ZIP receipt"))?;
        if committed != 0 { return Err(json_error("已提交 ZIP 批次不可重解析")); }
        transaction.execute("DELETE FROM p6k_zip_import_messages WHERE task_id=?1", [task_id]).map_err(|_| json_error("无法清理 ZIP 旧消息候选"))?;
        transaction.execute("DELETE FROM p6k_zip_import_items WHERE task_id=?1", [task_id]).map_err(|_| json_error("无法清理 ZIP 旧会话候选"))?;
        transaction.execute("DELETE FROM p6k_zip_import_asset_candidates WHERE task_id=?1", [task_id]).map_err(|_| json_error("无法清理 ZIP 旧媒体候选"))?;
        transaction.execute("DELETE FROM p6k_zip_import_profile_candidates WHERE task_id=?1", [task_id]).map_err(|_| json_error("无法清理 ZIP 旧资料候选"))?;
        for (ordinal, candidate) in candidates.iter().enumerate() { let item_id = format!("p6k-item-{ordinal}"); match candidate { Ok(candidate) => { transaction.execute("INSERT INTO p6k_zip_import_items(task_id,id,ordinal,source_conversation_id,title,content_hash,status,failure,conversation_id) VALUES(?1,?2,?3,?4,?5,?6,'PENDING',NULL,NULL)", params![task_id,item_id,ordinal,candidate.source_conversation_id,candidate.title,candidate.content_hash]).map_err(|_| json_error("无法保存 ZIP 重解析会话候选"))?; for message in &candidate.messages { transaction.execute("INSERT INTO p6k_zip_import_messages(task_id,item_id,source_message_id,parent_source_message_id,sibling_position,role,text,created_at_ms,imported_model) VALUES(?1,?2,?3,?4,?5,?6,?7,?8,?9)", params![task_id,item_id,message.source_id,message.parent_source_id,message.sibling_position,message.role,message.text,message.created_at_ms,message.imported_model]).map_err(|_| json_error("无法保存 ZIP 重解析消息候选"))?; } }, Err(reason) if reason == P6K_GRAPH_MERGED => { transaction.execute("INSERT INTO p6k_zip_import_items(task_id,id,ordinal,source_conversation_id,title,content_hash,status,failure,conversation_id) VALUES(?1,?2,?3,NULL,NULL,NULL,'SKIPPED',NULL,NULL)", params![task_id,item_id,ordinal]).map_err(|_| json_error("无法保存 ZIP 合并片段"))?; }, Err(reason) => { transaction.execute("INSERT INTO p6k_zip_import_items(task_id,id,ordinal,source_conversation_id,title,content_hash,status,failure,conversation_id) VALUES(?1,?2,?3,NULL,NULL,NULL,'FAILED',?4,NULL)", params![task_id,item_id,ordinal,reason]).map_err(|_| json_error("无法保存 ZIP 重解析失败候选"))?; } } }
        for (ordinal, asset) in assets.iter().enumerate() { transaction.execute("INSERT INTO p6k_zip_import_asset_candidates(task_id,ordinal,entry_name,sha256,byte_count,mime_type,status) VALUES(?1,?2,?3,?4,?5,?6,'UNMAPPED_REJECTED')", params![task_id,ordinal,asset.0,asset.3,asset.1,asset.2]).map_err(|_| json_error("无法保存 ZIP 重解析媒体候选"))?; }
        transaction.execute("INSERT INTO p6k_zip_import_profile_candidates(task_id,status,mapped_field_count) VALUES(?1,?2,?3)", params![task_id,profile_status,mapped_profile_field_count]).map_err(|_| json_error("无法保存 ZIP 重解析资料候选"))?;
        transaction.execute("UPDATE p6k_zip_import_tasks SET status='STAGED',failure=NULL,updated_at_ms=?2 WHERE id=?1", params![task_id, local_now_millis()]).map_err(|_| json_error("无法更新 ZIP 重解析任务"))?;
        transaction.commit().map_err(|_| json_error("ZIP 重解析未提交；已回滚"))?;
        Ok(profile_value)
    }

    fn retry_p6k_zip_import_task(&self, task_id: &str) -> Result<P6kZipImportTaskProjection, String> {
        if !is_stable_id(task_id) { return Err(json_error("ZIP 任务引用无效")); }
        let connection = self.connection()?;
        let (provider, committed): (String, i64) = connection.query_row("SELECT provider,(SELECT COUNT(*) FROM p6k_zip_import_provenance WHERE task_id=p6k_zip_import_tasks.id) FROM p6k_zip_import_tasks WHERE id=?1", [task_id], |row| Ok((row.get(0)?, row.get(1)?))).map_err(|_| json_error("ZIP 导入任务不存在"))?;
        if provider == "CHATGPT" && committed == 0 { if let Some(profile) = self.refresh_uncommitted_p6k_zip_import_task(task_id)?.as_ref() { self.commit_p6k_profile_personalization(task_id, profile)?; } }
        self.run_p6k_zip_import_task(task_id)?; self.read_p6k_zip_import_task(task_id)
    }

    fn skip_p6k_zip_import_failures(&self, task_id: &str) -> Result<P6kZipImportTaskProjection, String> { let connection=self.connection()?; connection.execute("UPDATE p6k_zip_import_items SET status='SKIPPED',failure=NULL WHERE task_id=?1 AND status='FAILED'",[task_id]).map_err(|_|json_error("无法跳过 ZIP 失败项"))?; Self::p6k_update_task_status(&connection,task_id)?; self.read_p6k_zip_import_task(task_id) }

    fn delete_p6k_zip_import_batch(&self, task_id: &str) -> Result<(), String> { if !is_stable_id(task_id) { return Err(json_error("ZIP 任务引用无效")); } let mut connection=self.connection()?; let transaction=connection.transaction().map_err(|_|json_error("无法开启 ZIP 批次删除 transaction"))?; let (workspace_id,storage_key):(String,String)=transaction.query_row("SELECT workspace_id,storage_key FROM p6k_zip_import_tasks WHERE id=?1",[task_id],|row|Ok((row.get(0)?,row.get(1)?))).map_err(|_|json_error("ZIP 导入任务不存在"))?; let ids=transaction.prepare("SELECT conversation_id FROM p6k_zip_import_provenance WHERE task_id=?1").map_err(|_|json_error("无法读取 ZIP provenance"))?.query_map([task_id],|row|row.get::<_,String>(0)).map_err(|_|json_error("无法读取 ZIP provenance"))?.collect::<Result<Vec<_>,_>>().map_err(|_|json_error("无法读取 ZIP provenance"))?; let exchange_text:String=transaction.query_row("SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?1",[&workspace_id],|row|row.get(0)).map_err(|_|json_error("工作区不存在"))?; let mut exchange:Value=serde_json::from_str(&exchange_text).map_err(|_|json_error("本地工作区无法读取"))?; for conversation in exchange.get_mut("conversations").and_then(Value::as_array_mut).into_iter().flatten() { if ids.iter().any(|id| conversation.get("id").and_then(Value::as_str)==Some(id)) { conversation.as_object_mut().ok_or_else(||json_error("本地会话无效"))?.insert("deleted".into(),Value::Bool(true)); } } let semantic_hash=refresh_exchange_hash(&mut exchange)?; validate_exchange(&exchange)?; transaction.execute("UPDATE workspace_exchange SET exchange_json=?1 WHERE workspace_id=?2",params![canonical_json(&exchange)?,workspace_id]).map_err(|_|json_error("ZIP 批次软删除未写入"))?; self.rebuild_local_search_index(&transaction,&workspace_id,&exchange)?; transaction.execute("UPDATE workspaces SET semantic_hash=?1 WHERE id=?2",params![semantic_hash,workspace_id]).map_err(|_|json_error("ZIP workspace hash 未更新"))?; transaction.execute("DELETE FROM p6k_zip_import_receipts WHERE conversation_id IN (SELECT conversation_id FROM p6k_zip_import_provenance WHERE task_id=?1)",[task_id]).map_err(|_|json_error("无法删除 ZIP receipt"))?; transaction.execute("DELETE FROM p6k_profile_personalization_settings WHERE id=1 AND source_task_id=?1",[task_id]).map_err(|_|json_error("无法撤销资料设置"))?; transaction.execute("DELETE FROM p6k_profile_import_receipts WHERE task_id=?1",[task_id]).map_err(|_|json_error("无法删除资料 receipt"))?; transaction.execute("DELETE FROM p6k_profile_import_provenance WHERE task_id=?1",[task_id]).map_err(|_|json_error("无法删除资料 provenance"))?; transaction.execute("DELETE FROM p6k_zip_import_tasks WHERE id=?1",[task_id]).map_err(|_|json_error("无法删除 ZIP 批次"))?; transaction.commit().map_err(|_|json_error("ZIP 批次删除未提交"))?; if storage_key.starts_with(&format!("p6k-zip-import-assets/{task_id}/")) { let _=fs::remove_file(self.root.join(storage_key)); } Ok(()) }

    /// P6-H's only Desktop file-owner path. The picker path is consumed immediately: SQLite
    /// retains only a private relative storage key and safe display metadata, never that path.
    fn stage_chatgpt_export_selected(
        &self,
        selected_path: &str,
    ) -> Result<ChatGptImportTaskProjection, String> {
        let source = PathBuf::from(selected_path);
        let display_name = source
            .file_name()
            .and_then(|value| value.to_str())
            .ok_or_else(|| json_error("所选 ChatGPT 文件名无效"))?;
        // ChatGPT's browser export commonly keeps `conversations.json`, but users may safely
        // rename a downloaded copy.  The import trust boundary is strict JSON preflight plus
        // immediate private copy, never an exact public filename.
        if !display_name.to_ascii_lowercase().ends_with(".json")
            || fs::symlink_metadata(&source)
                .map_err(|_| json_error("所选 ChatGPT 文件不可读"))?
                .file_type()
                .is_symlink()
        {
            return Err(json_error("只接受用户选择的 JSON 文件，且不接受符号链接"));
        }
        let bytes = fs::read(&source).map_err(|_| json_error("所选 ChatGPT 文件不可读"))?;
        let candidates = parse_chatgpt_export(&bytes)?;
        let package_hash = sha256(&bytes);
        let id = format!(
            "chatgpt-task-{}-{}",
            &package_hash[..16],
            local_now_millis()
        );
        let storage_key = format!("chatgpt-import-assets/{id}/{package_hash}.json");
        let target = self.root.join(&storage_key);
        let parent = target
            .parent()
            .ok_or_else(|| json_error("ChatGPT 私有副本路径无效"))?;
        fs::create_dir_all(parent).map_err(|_| json_error("无法创建 ChatGPT 私有副本"))?;
        let temporary = parent.join(format!(".{package_hash}.part"));
        fs::write(&temporary, &bytes).map_err(|_| json_error("无法写入 ChatGPT 私有副本"))?;
        fs::rename(&temporary, &target).map_err(|_| json_error("无法发布 ChatGPT 私有副本"))?;
        let mut connection = self.connection()?;
        let transaction = connection
            .transaction()
            .map_err(|_| json_error("无法开启 ChatGPT 导入 transaction"))?;
        let now = local_now_millis();
        transaction.execute("INSERT INTO chatgpt_import_tasks(id,status,storage_key,display_name,mime_type,byte_count,package_hash,failure,retry_count,created_at_ms,updated_at_ms) VALUES(?1,'AWAITING_CONFIRMATION',?2,?3,'application/json',?4,?5,NULL,0,?6,?6)", params![id, storage_key, display_name, bytes.len() as u64, package_hash, now]).map_err(|_| json_error("无法保存 ChatGPT 导入任务"))?;
        for (ordinal, candidate) in candidates.iter().enumerate() {
            match candidate {
                Ok(candidate) => {
                    transaction.execute("INSERT INTO chatgpt_import_items(task_id,id,ordinal,source_conversation_id,title,content_hash,status,failure,conversation_id) VALUES(?1,?2,?3,?4,?5,?6,'PENDING_CONFIRMATION',NULL,NULL)", params![id, format!("chatgpt-item-{ordinal}"), ordinal, candidate.source_conversation_id, candidate.title, candidate.content_hash]).map_err(|_| json_error("无法保存 ChatGPT 候选"))?;
                    for message in &candidate.messages {
                        transaction.execute("INSERT INTO chatgpt_import_messages(task_id,item_id,source_message_id,parent_source_message_id,sibling_position,role,text,created_at_ms,imported_model) VALUES(?1,?2,?3,?4,?5,?6,?7,?8,?9)", params![id, format!("chatgpt-item-{ordinal}"), message.source_id, message.parent_source_id, message.sibling_position, message.role, message.text, message.created_at_ms, message.imported_model]).map_err(|_| json_error("无法保存 ChatGPT 候选消息"))?;
                    }
                }
                Err(reason) => {
                    transaction.execute("INSERT INTO chatgpt_import_items(task_id,id,ordinal,source_conversation_id,title,content_hash,status,failure,conversation_id) VALUES(?1,?2,?3,NULL,NULL,NULL,'FAILED',?4,NULL)", params![id, format!("chatgpt-item-{ordinal}"), ordinal, reason]).map_err(|_| json_error("无法保存 ChatGPT 失败候选"))?;
                }
            }
        }
        transaction
            .commit()
            .map_err(|_| json_error("ChatGPT 导入任务未提交；已回滚"))?;
        Ok(ChatGptImportTaskProjection {
            id,
            status: "AWAITING_CONFIRMATION".into(),
            display_name: display_name.into(),
            mime_type: "application/json".into(),
            byte_count: bytes.len() as u64,
            package_hash,
            candidates,
        })
    }

    fn skip_chatgpt_import_item(&self, args: ChatGptImportItemActionArgs) -> Result<(), String> {
        let connection = self.connection()?;
        connection.execute("UPDATE chatgpt_import_items SET status='SKIPPED',failure=NULL WHERE task_id=?1 AND id=?2 AND status='PENDING_CONFIRMATION'", params![args.task_id, args.item_id]).map_err(|_| json_error("无法跳过 ChatGPT 候选"))?;
        connection.execute("UPDATE chatgpt_import_tasks SET status=CASE WHEN NOT EXISTS(SELECT 1 FROM chatgpt_import_items WHERE task_id=?1 AND status='PENDING_CONFIRMATION') THEN 'COMPLETED' ELSE 'PARTIALLY_COMPLETED' END,updated_at_ms=?2 WHERE id=?1", params![args.task_id, local_now_millis()]).map_err(|_| json_error("无法更新 ChatGPT 任务状态"))?;
        Ok(())
    }

    fn read_chatgpt_import_task(
        &self,
        task_id: &str,
    ) -> Result<ChatGptImportTaskReadProjection, String> {
        let connection = self.connection()?;
        let (id, status) = connection
            .query_row(
                "SELECT id,status FROM chatgpt_import_tasks WHERE id=?1",
                [task_id],
                |row| Ok((row.get(0)?, row.get(1)?)),
            )
            .map_err(|_| json_error("ChatGPT 任务不存在"))?;
        let items = connection.prepare("SELECT id,ordinal,title,status,failure,conversation_id FROM chatgpt_import_items WHERE task_id=?1 ORDER BY ordinal,id").map_err(|_| json_error("ChatGPT 候选无法读取"))?.query_map([task_id], |row| Ok(ChatGptImportItemProjection { id: row.get(0)?, ordinal: row.get(1)?, title: row.get(2)?, status: row.get(3)?, failure: row.get(4)?, conversation_id: row.get(5)? })).map_err(|_| json_error("ChatGPT 候选无效"))?.collect::<Result<Vec<_>,_>>().map_err(|_| json_error("ChatGPT 候选无法回读"))?;
        Ok(ChatGptImportTaskReadProjection { id, status, items })
    }

    fn cancel_chatgpt_import_task(&self, task_id: &str) -> Result<(), String> {
        self.connection()?.execute("UPDATE chatgpt_import_tasks SET status='CANCELLED',updated_at_ms=?2 WHERE id=?1 AND status NOT IN ('COMPLETED','CANCELLED')", params![task_id, local_now_millis()]).map_err(|_| json_error("无法取消 ChatGPT 任务"))?;
        Ok(())
    }

    fn read_latest_chatgpt_import_task(
        &self,
    ) -> Result<Option<ChatGptImportTaskReadProjection>, String> {
        let connection = self.connection()?;
        let id = connection.query_row("SELECT id FROM chatgpt_import_tasks WHERE status NOT IN ('COMPLETED','CANCELLED') ORDER BY updated_at_ms DESC LIMIT 1", [], |row| row.get::<_, String>(0)).ok();
        id.map(|task_id| self.read_chatgpt_import_task(&task_id))
            .transpose()
    }

    fn retry_chatgpt_import_task(&self, task_id: &str) -> Result<(), String> {
        if !is_stable_id(task_id) {
            return Err(json_error("ChatGPT 任务引用无效"));
        }
        let mut connection = self.connection()?;
        let (storage_key, package_hash): (String, String) = connection
            .query_row(
                "SELECT storage_key,package_hash FROM chatgpt_import_tasks WHERE id=?1",
                [task_id],
                |row| Ok((row.get(0)?, row.get(1)?)),
            )
            .map_err(|_| json_error("ChatGPT 任务不存在"))?;
        if !storage_key.starts_with(&format!("chatgpt-import-assets/{task_id}/"))
            || !storage_key.ends_with(".json")
        {
            return Err(json_error("ChatGPT 私有副本引用无效"));
        }
        let bytes = fs::read(self.root.join(&storage_key))
            .map_err(|_| json_error("ChatGPT 私有副本不可读；请重新选择文件"))?;
        if sha256(&bytes) != package_hash {
            return Err(json_error("ChatGPT 私有副本 hash 不一致；请重新选择文件"));
        }
        let candidates = parse_chatgpt_export(&bytes)?;
        let transaction = connection
            .transaction()
            .map_err(|_| json_error("无法开启 ChatGPT 重试 transaction"))?;
        transaction
            .execute(
                "DELETE FROM chatgpt_import_messages WHERE task_id=?1",
                [task_id],
            )
            .map_err(|_| json_error("无法重置 ChatGPT 候选消息"))?;
        transaction
            .execute(
                "DELETE FROM chatgpt_import_items WHERE task_id=?1",
                [task_id],
            )
            .map_err(|_| json_error("无法重置 ChatGPT 候选"))?;
        for (ordinal, candidate) in candidates.iter().enumerate() {
            let item_id = format!("chatgpt-item-{ordinal}");
            match candidate {
                Ok(candidate) => {
                    transaction.execute("INSERT INTO chatgpt_import_items(task_id,id,ordinal,source_conversation_id,title,content_hash,status,failure,conversation_id) VALUES(?1,?2,?3,?4,?5,?6,'PENDING_CONFIRMATION',NULL,NULL)", params![task_id, item_id, ordinal, candidate.source_conversation_id, candidate.title, candidate.content_hash]).map_err(|_| json_error("无法重建 ChatGPT 候选"))?;
                    for message in &candidate.messages {
                        transaction.execute("INSERT INTO chatgpt_import_messages(task_id,item_id,source_message_id,parent_source_message_id,sibling_position,role,text,created_at_ms,imported_model) VALUES(?1,?2,?3,?4,?5,?6,?7,?8,?9)", params![task_id, format!("chatgpt-item-{ordinal}"), message.source_id, message.parent_source_id, message.sibling_position, message.role, message.text, message.created_at_ms, message.imported_model]).map_err(|_| json_error("无法重建 ChatGPT 候选消息"))?;
                    }
                }
                Err(reason) => {
                    transaction.execute("INSERT INTO chatgpt_import_items(task_id,id,ordinal,source_conversation_id,title,content_hash,status,failure,conversation_id) VALUES(?1,?2,?3,NULL,NULL,NULL,'FAILED',?4,NULL)", params![task_id, item_id, ordinal, reason]).map_err(|_| json_error("无法重建 ChatGPT 失败候选"))?;
                }
            }
        }
        transaction.execute("UPDATE chatgpt_import_tasks SET status='AWAITING_CONFIRMATION',failure=NULL,retry_count=retry_count+1,updated_at_ms=?2 WHERE id=?1", params![task_id, local_now_millis()]).map_err(|_| json_error("无法更新 ChatGPT 重试状态"))?;
        transaction
            .commit()
            .map_err(|_| json_error("ChatGPT 重试未提交；已回滚"))
    }

    fn confirm_chatgpt_import_item(
        &self,
        args: ChatGptImportItemActionArgs,
    ) -> Result<String, String> {
        if !is_stable_id(&args.workspace_id)
            || !is_stable_id(&args.task_id)
            || !is_stable_id(&args.item_id)
        {
            return Err(json_error("ChatGPT 导入引用无效"));
        }
        let mut connection = self.connection()?;
        let transaction = connection
            .transaction()
            .map_err(|_| json_error("无法开启 ChatGPT 确认 transaction"))?;
        let (package_hash, task_status): (String, String) = transaction
            .query_row(
                "SELECT package_hash,status FROM chatgpt_import_tasks WHERE id=?1",
                [&args.task_id],
                |row| Ok((row.get(0)?, row.get(1)?)),
            )
            .map_err(|_| json_error("ChatGPT 任务不存在"))?;
        if task_status == "CANCELLED" {
            return Err(json_error("ChatGPT 任务已取消"));
        }
        let (source_id, title, content_hash, item_status): (String, String, String, String) = transaction.query_row("SELECT source_conversation_id,title,content_hash,status FROM chatgpt_import_items WHERE task_id=?1 AND id=?2", params![args.task_id, args.item_id], |row| Ok((row.get(0)?,row.get(1)?,row.get(2)?,row.get(3)?))).map_err(|_| json_error("ChatGPT 候选不存在或不可确认"))?;
        if item_status == "CONFIRMED" {
            let id: String = transaction
                .query_row(
                    "SELECT conversation_id FROM chatgpt_import_items WHERE task_id=?1 AND id=?2",
                    params![args.task_id, args.item_id],
                    |row| row.get(0),
                )
                .map_err(|_| json_error("已确认候选无法回读"))?;
            transaction
                .commit()
                .map_err(|_| json_error("无法结束 ChatGPT 重放读取"))?;
            return Ok(id);
        }
        if item_status != "PENDING_CONFIRMATION" {
            return Err(json_error("该 ChatGPT 候选不可确认"));
        }
        if let Ok((existing_id, existing_hash)) = transaction.query_row("SELECT conversation_id,content_hash FROM chatgpt_import_receipts WHERE source_conversation_id=?1 AND package_hash=?2", params![source_id, package_hash], |row| Ok((row.get::<_,String>(0)?,row.get::<_,String>(1)?))) {
            if existing_hash != content_hash { return Err(json_error("同源不同内容，需作为冲突 reimport 处理")); }
            transaction.execute("UPDATE chatgpt_import_items SET status='CONFIRMED',conversation_id=?3 WHERE task_id=?1 AND id=?2", params![args.task_id,args.item_id,existing_id]).map_err(|_| json_error("无法回写 ChatGPT 幂等结果"))?;
            transaction.commit().map_err(|_| json_error("ChatGPT 重放结果未提交"))?; return Ok(existing_id);
        }
        let mut statement = transaction.prepare("SELECT source_message_id,parent_source_message_id,sibling_position,role,text,created_at_ms,imported_model FROM chatgpt_import_messages WHERE task_id=?1 AND item_id=?2 ORDER BY sibling_position,source_message_id").map_err(|_| json_error("无法读取 ChatGPT 候选消息"))?;
        let messages = statement
            .query_map(params![args.task_id, args.item_id], |row| {
                Ok((
                    row.get::<_, String>(0)?,
                    row.get::<_, Option<String>>(1)?,
                    row.get::<_, i64>(2)?,
                    row.get::<_, String>(3)?,
                    row.get::<_, String>(4)?,
                    row.get::<_, i64>(5)?,
                    row.get::<_, Option<String>>(6)?,
                ))
            })
            .map_err(|_| json_error("ChatGPT 候选消息无效"))?
            .collect::<Result<Vec<_>, _>>()
            .map_err(|_| json_error("ChatGPT 候选消息无法读取"))?;
        drop(statement);
        if messages.is_empty() {
            return Err(json_error("ChatGPT 候选没有安全消息"));
        }
        let conversation_id = format!(
            "conversation-chatgpt-{}",
            &sha256(format!("{source_id}:{package_hash}").as_bytes())[..24]
        );
        let ids = messages
            .iter()
            .map(|message| {
                (
                    message.0.clone(),
                    format!(
                        "message-chatgpt-{}-{}",
                        args.task_id["chatgpt-task-".len()..]
                            .chars()
                            .take(12)
                            .collect::<String>(),
                        message.0
                    ),
                )
            })
            .collect::<BTreeMap<_, _>>();
        let leaf = messages
            .iter()
            .filter(|message| {
                !messages
                    .iter()
                    .any(|other| other.1.as_deref() == Some(&message.0))
            })
            .max_by_key(|message| (message.5, message.0.clone()))
            .map(|message| ids[&message.0].clone())
            .ok_or_else(|| json_error("ChatGPT current leaf 无效"))?;
        let exchange_text: String = transaction
            .query_row(
                "SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?1",
                [&args.workspace_id],
                |row| row.get(0),
            )
            .map_err(|_| json_error("工作区不存在"))?;
        let mut exchange: Value =
            serde_json::from_str(&exchange_text).map_err(|_| json_error("本地工作区无法读取"))?;
        let conversation = json!({"id":conversation_id,"title":title,"revision":1,"projectId":Value::Null,"pinned":false,"archived":false,"deleted":false,"createdAt":rfc3339_from_unix_millis(messages[0].5),"updatedAt":rfc3339_from_unix_millis(messages.iter().map(|message| message.5).max().unwrap_or(messages[0].5)),"importedFrom":"CHATGPT_EXPORT","currentLeafId":leaf,"messages":messages.iter().map(|message| json!({"id":ids[&message.0],"parentId":message.1.as_ref().map(|parent| ids[parent].clone()),"ordinal":message.2,"role":message.3,"delivery":"COMPLETE","revision":1,"createdAt":rfc3339_from_unix_millis(message.5),"importedAtEpochMs":message.5,"importedModel":message.6,"blocks":[{"kind":"TEXT","text":message.4}]})).collect::<Vec<_>>()});
        exchange
            .as_object_mut()
            .ok_or_else(|| json_error("本地工作区根无效"))?
            .get_mut("conversations")
            .and_then(Value::as_array_mut)
            .ok_or_else(|| json_error("本地会话集合无效"))?
            .push(conversation);
        let semantic_hash = refresh_exchange_hash(&mut exchange)?;
        validate_exchange(&exchange)?;
        transaction
            .execute(
                "UPDATE workspace_exchange SET exchange_json=?1 WHERE workspace_id=?2",
                params![canonical_json(&exchange)?, args.workspace_id],
            )
            .map_err(|_| json_error("ChatGPT 会话未写入"))?;
        self.rebuild_local_search_index(&transaction, &args.workspace_id, &exchange)?;
        transaction
            .execute(
                "UPDATE workspaces SET semantic_hash=?1 WHERE id=?2",
                params![semantic_hash, args.workspace_id],
            )
            .map_err(|_| json_error("ChatGPT workspace hash 未更新"))?;
        transaction.execute("INSERT INTO chatgpt_import_provenance(conversation_id,source_conversation_id,package_hash,content_hash,imported_at_ms,adapter_id,adapter_version,revoked_at_ms) VALUES(?1,?2,?3,?4,?5,'chatgpt-export-json',1,NULL)", params![conversation_id,source_id,package_hash,content_hash,local_now_millis()]).map_err(|_| json_error("ChatGPT provenance 未写入"))?;
        transaction.execute("INSERT INTO chatgpt_import_receipts(source_conversation_id,package_hash,conversation_id,content_hash,committed_at_ms) VALUES(?1,?2,?3,?4,?5)", params![source_id,package_hash,conversation_id,content_hash,local_now_millis()]).map_err(|_| json_error("ChatGPT receipt 未写入"))?;
        transaction.execute("UPDATE chatgpt_import_items SET status='CONFIRMED',conversation_id=?3 WHERE task_id=?1 AND id=?2", params![args.task_id,args.item_id,conversation_id]).map_err(|_| json_error("ChatGPT 项目状态未写入"))?;
        transaction.execute("UPDATE chatgpt_import_tasks SET status=CASE WHEN NOT EXISTS(SELECT 1 FROM chatgpt_import_items WHERE task_id=?1 AND status='PENDING_CONFIRMATION') THEN 'COMPLETED' ELSE 'PARTIALLY_COMPLETED' END,updated_at_ms=?2 WHERE id=?1", params![args.task_id,local_now_millis()]).map_err(|_| json_error("ChatGPT 任务状态未写入"))?;
        transaction
            .commit()
            .map_err(|_| json_error("ChatGPT 确认未提交；已回滚"))?;
        Ok(conversation_id)
    }

    /// P6-I's only Desktop file-owner path. It immediately copies the user-selected JSON into the
    /// app-private root; neither the selected path nor a bookmark reaches SQLite.
    fn stage_claude_export_selected(
        &self,
        selected_path: &str,
    ) -> Result<ClaudeImportTaskProjection, String> {
        let source = PathBuf::from(selected_path);
        let display_name = source
            .file_name()
            .and_then(|value| value.to_str())
            .ok_or_else(|| json_error("所选 Claude 文件名无效"))?;
        if !display_name.to_ascii_lowercase().ends_with(".json")
            || fs::symlink_metadata(&source)
                .map_err(|_| json_error("所选 Claude 文件不可读"))?
                .file_type()
                .is_symlink()
        {
            return Err(json_error("只接受用户选择的 JSON 文件，且不接受符号链接"));
        }
        let bytes = fs::read(&source).map_err(|_| json_error("所选 Claude 文件不可读"))?;
        let candidates = parse_claude_export(&bytes)?;
        let package_hash = sha256(&bytes);
        let id = format!("claude-task-{}-{}", &package_hash[..16], local_now_millis());
        let storage_key = format!("claude-import-assets/{id}/{package_hash}.json");
        let target = self.root.join(&storage_key);
        let parent = target
            .parent()
            .ok_or_else(|| json_error("Claude 私有副本路径无效"))?;
        fs::create_dir_all(parent).map_err(|_| json_error("无法创建 Claude 私有副本"))?;
        let temporary = parent.join(format!(".{package_hash}.part"));
        fs::write(&temporary, &bytes).map_err(|_| json_error("无法写入 Claude 私有副本"))?;
        fs::rename(&temporary, &target).map_err(|_| json_error("无法发布 Claude 私有副本"))?;
        let mut connection = self.connection()?;
        let transaction = connection
            .transaction()
            .map_err(|_| json_error("无法开启 Claude 导入 transaction"))?;
        let now = local_now_millis();
        transaction.execute("INSERT INTO claude_import_tasks(id,status,storage_key,display_name,mime_type,byte_count,package_hash,failure,retry_count,created_at_ms,updated_at_ms) VALUES(?1,'AWAITING_CONFIRMATION',?2,?3,'application/json',?4,?5,NULL,0,?6,?6)", params![id, storage_key, display_name, bytes.len() as u64, package_hash, now]).map_err(|_| json_error("无法保存 Claude 导入任务"))?;
        for (ordinal, candidate) in candidates.iter().enumerate() {
            let item_id = format!("claude-item-{ordinal}");
            match candidate {
                Ok(candidate) => {
                    transaction.execute("INSERT INTO claude_import_items(task_id,id,ordinal,source_conversation_id,title,content_hash,status,failure,conversation_id) VALUES(?1,?2,?3,?4,?5,?6,'PENDING_CONFIRMATION',NULL,NULL)", params![id,item_id,ordinal,candidate.source_conversation_id,candidate.title,candidate.content_hash]).map_err(|_| json_error("无法保存 Claude 候选"))?;
                    for message in &candidate.messages {
                        transaction.execute("INSERT INTO claude_import_messages(task_id,item_id,source_message_id,parent_source_message_id,sibling_position,role,text,created_at_ms,imported_model) VALUES(?1,?2,?3,?4,?5,?6,?7,?8,NULL)", params![id,format!("claude-item-{ordinal}"),message.source_id,message.parent_source_id,message.sibling_position,message.role,message.text,message.created_at_ms]).map_err(|_| json_error("无法保存 Claude 候选消息"))?;
                    }
                }
                Err(reason) => {
                    transaction.execute("INSERT INTO claude_import_items(task_id,id,ordinal,source_conversation_id,title,content_hash,status,failure,conversation_id) VALUES(?1,?2,?3,NULL,NULL,NULL,'FAILED',?4,NULL)", params![id,item_id,ordinal,reason]).map_err(|_| json_error("无法保存 Claude 失败候选"))?;
                }
            }
        }
        transaction
            .commit()
            .map_err(|_| json_error("Claude 导入任务未提交；已回滚"))?;
        Ok(ClaudeImportTaskProjection {
            id,
            status: "AWAITING_CONFIRMATION".into(),
            display_name: display_name.into(),
            mime_type: "application/json".into(),
            byte_count: bytes.len() as u64,
            package_hash,
            candidates,
        })
    }

    fn read_claude_import_task(
        &self,
        task_id: &str,
    ) -> Result<ClaudeImportTaskReadProjection, String> {
        let connection = self.connection()?;
        let (id, status) = connection
            .query_row(
                "SELECT id,status FROM claude_import_tasks WHERE id=?1",
                [task_id],
                |row| Ok((row.get(0)?, row.get(1)?)),
            )
            .map_err(|_| json_error("Claude 任务不存在"))?;
        let items = connection.prepare("SELECT id,ordinal,title,status,failure,conversation_id FROM claude_import_items WHERE task_id=?1 ORDER BY ordinal,id").map_err(|_| json_error("Claude 候选无法读取"))?.query_map([task_id], |row| Ok(ClaudeImportItemProjection { id: row.get(0)?, ordinal: row.get(1)?, title: row.get(2)?, status: row.get(3)?, failure: row.get(4)?, conversation_id: row.get(5)? })).map_err(|_| json_error("Claude 候选无效"))?.collect::<Result<Vec<_>,_>>().map_err(|_| json_error("Claude 候选无法回读"))?;
        Ok(ClaudeImportTaskReadProjection { id, status, items })
    }

    fn read_latest_claude_import_task(
        &self,
    ) -> Result<Option<ClaudeImportTaskReadProjection>, String> {
        let connection = self.connection()?;
        let id = connection.query_row("SELECT id FROM claude_import_tasks WHERE status NOT IN ('COMPLETED','CANCELLED') ORDER BY updated_at_ms DESC LIMIT 1", [], |row| row.get::<_, String>(0)).ok();
        id.map(|task_id| self.read_claude_import_task(&task_id))
            .transpose()
    }

    /** P6-J's Desktop owner copies a user-selected JSON export before parsing or persisting it. */
    fn stage_nanfeng_knowledge_export_selected(&self, selected_path: &str) -> Result<ChatGptImportTaskProjection, String> {
        let source = PathBuf::from(selected_path);
        let display_name = source.file_name().and_then(|value| value.to_str()).ok_or_else(|| json_error("所选知识库文件名无效"))?;
        if !display_name.to_ascii_lowercase().ends_with(".json") || fs::symlink_metadata(&source).map_err(|_| json_error("所选知识库文件不可读"))?.file_type().is_symlink() { return Err(json_error("只接受用户选择的 JSON 文件，且不接受符号链接")); }
        let bytes = fs::read(&source).map_err(|_| json_error("所选知识库文件不可读"))?;
        let candidates = parse_nanfeng_knowledge_export(&bytes)?; let package_hash = sha256(&bytes);
        let id = format!("p6j-task-{}-{}", &package_hash[..16], local_now_millis()); let storage_key = format!("nanfeng-knowledge-import-assets/{id}/{package_hash}.json");
        let target = self.root.join(&storage_key); let parent = target.parent().ok_or_else(|| json_error("知识库私有副本路径无效"))?;
        fs::create_dir_all(parent).map_err(|_| json_error("无法创建知识库私有副本"))?;
        let temporary = parent.join(format!(".{package_hash}.part")); fs::write(&temporary, &bytes).map_err(|_| json_error("无法写入知识库私有副本"))?; fs::rename(&temporary, &target).map_err(|_| json_error("无法发布知识库私有副本"))?;
        let mut connection = self.connection()?; let transaction = connection.transaction().map_err(|_| json_error("无法开启知识库导入 transaction"))?; let now = local_now_millis();
        transaction.execute("INSERT INTO nanfeng_knowledge_import_tasks(id,status,storage_key,display_name,mime_type,byte_count,package_hash,failure,retry_count,created_at_ms,updated_at_ms) VALUES(?1,'AWAITING_CONFIRMATION',?2,?3,'application/json',?4,?5,NULL,0,?6,?6)", params![id, storage_key, display_name, bytes.len() as u64, package_hash, now]).map_err(|_| json_error("无法保存知识库导入任务"))?;
        for (ordinal, candidate) in candidates.iter().enumerate() { let item_id = format!("p6j-item-{ordinal}"); match candidate { Ok(candidate) => { transaction.execute("INSERT INTO nanfeng_knowledge_import_items(task_id,id,ordinal,source_conversation_id,title,content_hash,status,failure,conversation_id) VALUES(?1,?2,?3,?4,?5,?6,'PENDING_CONFIRMATION',NULL,NULL)", params![id,item_id,ordinal,candidate.source_conversation_id,candidate.title,candidate.content_hash]).map_err(|_| json_error("无法保存知识库候选"))?; for message in &candidate.messages { transaction.execute("INSERT INTO nanfeng_knowledge_import_messages(task_id,item_id,source_message_id,parent_source_message_id,sibling_position,role,text,created_at_ms) VALUES(?1,?2,?3,?4,?5,?6,?7,?8)", params![id,format!("p6j-item-{ordinal}"),message.source_id,message.parent_source_id,message.sibling_position,message.role,message.text,message.created_at_ms]).map_err(|_| json_error("无法保存知识库候选消息"))?; } }, Err(reason) => { transaction.execute("INSERT INTO nanfeng_knowledge_import_items(task_id,id,ordinal,source_conversation_id,title,content_hash,status,failure,conversation_id) VALUES(?1,?2,?3,NULL,NULL,NULL,'FAILED',?4,NULL)", params![id,item_id,ordinal,reason]).map_err(|_| json_error("无法保存知识库失败候选"))?; } } }
        transaction.commit().map_err(|_| json_error("知识库导入任务未提交；已回滚"))?;
        Ok(ChatGptImportTaskProjection { id, status: "AWAITING_CONFIRMATION".to_owned(), display_name: display_name.to_owned(), mime_type: "application/json".to_owned(), byte_count: bytes.len() as u64, package_hash, candidates })
    }

    fn read_nanfeng_knowledge_import_task(&self, task_id: &str) -> Result<ChatGptImportTaskReadProjection, String> {
        let connection = self.connection()?; let (id,status):(String,String) = connection.query_row("SELECT id,status FROM nanfeng_knowledge_import_tasks WHERE id=?1", [task_id], |row| Ok((row.get(0)?,row.get(1)?))).map_err(|_| json_error("知识库导入任务不存在"))?;
        let items = connection.prepare("SELECT id,ordinal,title,status,failure,conversation_id FROM nanfeng_knowledge_import_items WHERE task_id=?1 ORDER BY ordinal,id").map_err(|_| json_error("知识库候选无法读取"))?.query_map([task_id], |row| Ok(ChatGptImportItemProjection { id: row.get(0)?, ordinal: row.get(1)?, title: row.get(2)?, status: row.get(3)?, failure: row.get(4)?, conversation_id: row.get(5)? })).map_err(|_| json_error("知识库候选无效"))?.collect::<Result<Vec<_>,_>>().map_err(|_| json_error("知识库候选无法回读"))?;
        Ok(ChatGptImportTaskReadProjection { id, status, items })
    }
    fn read_latest_nanfeng_knowledge_import_task(&self) -> Result<Option<ChatGptImportTaskReadProjection>, String> {
        let connection = self.connection()?;
        connection.query_row("SELECT id FROM nanfeng_knowledge_import_tasks WHERE status NOT IN ('COMPLETED','CANCELLED') ORDER BY updated_at_ms DESC LIMIT 1", [], |row| row.get::<_, String>(0)).ok().map(|id| self.read_nanfeng_knowledge_import_task(&id)).transpose()
    }

    fn skip_nanfeng_knowledge_import_item(&self, args: ChatGptImportItemActionArgs) -> Result<(), String> { let connection = self.connection()?; connection.execute("UPDATE nanfeng_knowledge_import_items SET status='SKIPPED',failure=NULL WHERE task_id=?1 AND id=?2 AND status='PENDING_CONFIRMATION'", params![args.task_id,args.item_id]).map_err(|_| json_error("无法跳过知识库候选"))?; connection.execute("UPDATE nanfeng_knowledge_import_tasks SET status=CASE WHEN NOT EXISTS(SELECT 1 FROM nanfeng_knowledge_import_items WHERE task_id=?1 AND status='PENDING_CONFIRMATION') THEN 'COMPLETED' ELSE 'PARTIALLY_COMPLETED' END,updated_at_ms=?2 WHERE id=?1", params![args.task_id,local_now_millis()]).map_err(|_| json_error("无法更新知识库任务状态"))?; Ok(()) }
    fn cancel_nanfeng_knowledge_import_task(&self, task_id: &str) -> Result<(), String> { self.connection()?.execute("UPDATE nanfeng_knowledge_import_tasks SET status='CANCELLED',updated_at_ms=?2 WHERE id=?1 AND status NOT IN ('COMPLETED','CANCELLED')", params![task_id,local_now_millis()]).map_err(|_| json_error("无法取消知识库导入任务"))?; Ok(()) }
    fn retry_nanfeng_knowledge_import_task(&self, task_id: &str) -> Result<(), String> {
        if !is_stable_id(task_id) { return Err(json_error("知识库任务引用无效")); }
        let mut connection = self.connection()?; let (storage_key,package_hash,status):(String,String,String) = connection.query_row("SELECT storage_key,package_hash,status FROM nanfeng_knowledge_import_tasks WHERE id=?1", [task_id], |row| Ok((row.get(0)?,row.get(1)?,row.get(2)?))).map_err(|_| json_error("知识库任务不存在"))?;
        if status != "FAILED" { return Err(json_error("仅失败的知识库任务可重试；已确认内容不会被重置")); }
        if !storage_key.starts_with(&format!("nanfeng-knowledge-import-assets/{task_id}/")) || !storage_key.ends_with(".json") { return Err(json_error("知识库私有副本引用无效")); }
        let bytes = fs::read(self.root.join(storage_key)).map_err(|_| json_error("知识库私有副本不可读；请重新选择文件"))?; if sha256(&bytes) != package_hash { return Err(json_error("知识库私有副本 hash 不一致；请重新选择文件")); }
        let candidates = parse_nanfeng_knowledge_export(&bytes)?; let transaction = connection.transaction().map_err(|_| json_error("无法开启知识库重试 transaction"))?;
        transaction.execute("DELETE FROM nanfeng_knowledge_import_messages WHERE task_id=?1", [task_id]).map_err(|_| json_error("无法重置知识库候选消息"))?; transaction.execute("DELETE FROM nanfeng_knowledge_import_items WHERE task_id=?1", [task_id]).map_err(|_| json_error("无法重置知识库候选"))?;
        for (ordinal,candidate) in candidates.iter().enumerate() { let item_id=format!("p6j-item-{ordinal}"); match candidate { Ok(candidate) => { transaction.execute("INSERT INTO nanfeng_knowledge_import_items(task_id,id,ordinal,source_conversation_id,title,content_hash,status,failure,conversation_id) VALUES(?1,?2,?3,?4,?5,?6,'PENDING_CONFIRMATION',NULL,NULL)",params![task_id,item_id,ordinal,candidate.source_conversation_id,candidate.title,candidate.content_hash]).map_err(|_|json_error("无法重建知识库候选"))?; for message in &candidate.messages { transaction.execute("INSERT INTO nanfeng_knowledge_import_messages(task_id,item_id,source_message_id,parent_source_message_id,sibling_position,role,text,created_at_ms) VALUES(?1,?2,?3,?4,?5,?6,?7,?8)",params![task_id,format!("p6j-item-{ordinal}"),message.source_id,message.parent_source_id,message.sibling_position,message.role,message.text,message.created_at_ms]).map_err(|_|json_error("无法重建知识库候选消息"))?; } }, Err(reason) => { transaction.execute("INSERT INTO nanfeng_knowledge_import_items(task_id,id,ordinal,source_conversation_id,title,content_hash,status,failure,conversation_id) VALUES(?1,?2,?3,NULL,NULL,NULL,'FAILED',?4,NULL)",params![task_id,item_id,ordinal,reason]).map_err(|_|json_error("无法重建知识库失败候选"))?; } } }
        transaction.execute("UPDATE nanfeng_knowledge_import_tasks SET status='AWAITING_CONFIRMATION',failure=NULL,retry_count=retry_count+1,updated_at_ms=?2 WHERE id=?1",params![task_id,local_now_millis()]).map_err(|_|json_error("无法更新知识库重试状态"))?; transaction.commit().map_err(|_|json_error("知识库重试未提交；已回滚"))
    }

    fn confirm_nanfeng_knowledge_import_item(&self, args: ChatGptImportItemActionArgs) -> Result<String, String> {
        if !is_stable_id(&args.workspace_id) || !is_stable_id(&args.task_id) || !is_stable_id(&args.item_id) { return Err(json_error("知识库导入引用无效")); }
        let mut connection = self.connection()?; let transaction = connection.transaction().map_err(|_| json_error("无法开启知识库确认 transaction"))?;
        let (package_hash, task_status):(String,String) = transaction.query_row("SELECT package_hash,status FROM nanfeng_knowledge_import_tasks WHERE id=?1", [&args.task_id], |row| Ok((row.get(0)?,row.get(1)?))).map_err(|_| json_error("知识库任务不存在"))?; if task_status == "CANCELLED" { return Err(json_error("知识库任务已取消")); }
        let (source_id,title,content_hash,status):(String,String,String,String) = transaction.query_row("SELECT source_conversation_id,title,content_hash,status FROM nanfeng_knowledge_import_items WHERE task_id=?1 AND id=?2", params![args.task_id,args.item_id], |row| Ok((row.get(0)?,row.get(1)?,row.get(2)?,row.get(3)?))).map_err(|_| json_error("知识库候选不存在或不可确认"))?;
        if status == "CONFIRMED" {
            let conversation_id = transaction.query_row("SELECT conversation_id FROM nanfeng_knowledge_import_items WHERE task_id=?1 AND id=?2", params![args.task_id,args.item_id], |row| row.get::<_, Option<String>>(0)).map_err(|_| json_error("知识库幂等结果无法读取"))?.ok_or_else(|| json_error("知识库幂等结果缺失"))?;
            transaction.commit().map_err(|_| json_error("知识库幂等结果未提交"))?;
            return Ok(conversation_id);
        }
        if status != "PENDING_CONFIRMATION" { return Err(json_error("该知识库候选不可确认")); }
        if let Ok((existing_id,existing_hash)) = transaction.query_row("SELECT conversation_id,content_hash FROM nanfeng_knowledge_import_receipts WHERE source_conversation_id=?1 AND package_hash=?2", params![source_id,package_hash], |row| Ok((row.get::<_,String>(0)?,row.get::<_,String>(1)?))) { if existing_hash != content_hash { return Err(json_error("同源不同内容，需作为冲突 reimport 处理")); } transaction.execute("UPDATE nanfeng_knowledge_import_items SET status='CONFIRMED',conversation_id=?3 WHERE task_id=?1 AND id=?2", params![args.task_id,args.item_id,existing_id]).map_err(|_| json_error("无法回写知识库幂等结果"))?; transaction.commit().map_err(|_| json_error("知识库重放结果未提交"))?; return Ok(existing_id); }
        let messages = transaction.prepare("SELECT source_message_id,parent_source_message_id,sibling_position,role,text,created_at_ms FROM nanfeng_knowledge_import_messages WHERE task_id=?1 AND item_id=?2 ORDER BY sibling_position,source_message_id").map_err(|_| json_error("无法读取知识库候选消息"))?.query_map(params![args.task_id,args.item_id], |row| Ok((row.get::<_,String>(0)?,row.get::<_,Option<String>>(1)?,row.get::<_,i64>(2)?,row.get::<_,String>(3)?,row.get::<_,String>(4)?,row.get::<_,i64>(5)?))).map_err(|_| json_error("知识库候选消息无效"))?.collect::<Result<Vec<_>,_>>().map_err(|_| json_error("知识库候选消息无法读取"))?; if messages.is_empty() { return Err(json_error("知识库候选没有安全消息")); }
        let conversation_id = format!("conversation-p6j-{}", &sha256(format!("{source_id}:{package_hash}").as_bytes())[..24]); let ids = messages.iter().map(|message| (message.0.clone(), format!("message-p6j-{}-{}", args.task_id["p6j-task-".len()..].chars().take(12).collect::<String>(), message.0))).collect::<BTreeMap<_,_>>(); let leaf = messages.last().map(|message| ids[&message.0].clone()).ok_or_else(|| json_error("知识库 current leaf 无效"))?;
        let exchange_text:String = transaction.query_row("SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?1", [&args.workspace_id], |row| row.get(0)).map_err(|_| json_error("工作区不存在"))?; let mut exchange:Value = serde_json::from_str(&exchange_text).map_err(|_| json_error("本地工作区无法读取"))?;
        let conversation = json!({"id":conversation_id,"title":title,"revision":1,"projectId":Value::Null,"pinned":false,"archived":false,"deleted":false,"createdAt":rfc3339_from_unix_millis(messages[0].5),"updatedAt":rfc3339_from_unix_millis(messages.iter().map(|message| message.5).max().unwrap_or(messages[0].5)),"importedFrom":"NANFENG_KNOWLEDGE_EXPORT","currentLeafId":leaf,"messages":messages.iter().map(|message| json!({"id":ids[&message.0],"parentId":message.1.as_ref().map(|parent| ids[parent].clone()),"ordinal":message.2,"role":message.3,"delivery":"COMPLETE","revision":1,"createdAt":rfc3339_from_unix_millis(message.5),"importedAtEpochMs":message.5,"importedModel":Value::Null,"blocks":[{"kind":"TEXT","text":message.4}]})).collect::<Vec<_>>()});
        exchange.as_object_mut().ok_or_else(|| json_error("本地工作区根无效"))?.get_mut("conversations").and_then(Value::as_array_mut).ok_or_else(|| json_error("本地会话集合无效"))?.push(conversation); let semantic_hash = refresh_exchange_hash(&mut exchange)?; validate_exchange(&exchange)?; transaction.execute("UPDATE workspace_exchange SET exchange_json=?1 WHERE workspace_id=?2", params![canonical_json(&exchange)?,args.workspace_id]).map_err(|_| json_error("知识库会话未写入"))?; self.rebuild_local_search_index(&transaction,&args.workspace_id,&exchange)?; transaction.execute("UPDATE workspaces SET semantic_hash=?1 WHERE id=?2", params![semantic_hash,args.workspace_id]).map_err(|_| json_error("知识库 workspace hash 未更新"))?;
        transaction.execute("INSERT INTO nanfeng_knowledge_import_provenance(conversation_id,source_conversation_id,package_hash,content_hash,imported_at_ms,adapter_id,adapter_version,revoked_at_ms) VALUES(?1,?2,?3,?4,?5,'nanfeng-knowledge-export-json',1,NULL)",params![conversation_id,source_id,package_hash,content_hash,local_now_millis()]).map_err(|_| json_error("知识库 provenance 未写入"))?; transaction.execute("INSERT INTO nanfeng_knowledge_import_receipts(source_conversation_id,package_hash,conversation_id,content_hash,committed_at_ms) VALUES(?1,?2,?3,?4,?5)",params![source_id,package_hash,conversation_id,content_hash,local_now_millis()]).map_err(|_| json_error("知识库 receipt 未写入"))?; transaction.execute("UPDATE nanfeng_knowledge_import_items SET status='CONFIRMED',conversation_id=?3 WHERE task_id=?1 AND id=?2",params![args.task_id,args.item_id,conversation_id]).map_err(|_| json_error("知识库项目状态未写入"))?; transaction.execute("UPDATE nanfeng_knowledge_import_tasks SET status=CASE WHEN NOT EXISTS(SELECT 1 FROM nanfeng_knowledge_import_items WHERE task_id=?1 AND status='PENDING_CONFIRMATION') THEN 'COMPLETED' ELSE 'PARTIALLY_COMPLETED' END,updated_at_ms=?2 WHERE id=?1",params![args.task_id,local_now_millis()]).map_err(|_| json_error("知识库任务状态未写入"))?; transaction.commit().map_err(|_| json_error("知识库确认未提交；已回滚"))?; Ok(conversation_id)
    }

    fn skip_claude_import_item(&self, args: ClaudeImportItemActionArgs) -> Result<(), String> {
        let connection = self.connection()?;
        connection.execute("UPDATE claude_import_items SET status='SKIPPED',failure=NULL WHERE task_id=?1 AND id=?2 AND status='PENDING_CONFIRMATION'", params![args.task_id,args.item_id]).map_err(|_| json_error("无法跳过 Claude 候选"))?;
        connection.execute("UPDATE claude_import_tasks SET status=CASE WHEN NOT EXISTS(SELECT 1 FROM claude_import_items WHERE task_id=?1 AND status='PENDING_CONFIRMATION') THEN 'COMPLETED' ELSE 'PARTIALLY_COMPLETED' END,updated_at_ms=?2 WHERE id=?1", params![args.task_id,local_now_millis()]).map_err(|_| json_error("无法更新 Claude 任务状态"))?;
        Ok(())
    }

    fn cancel_claude_import_task(&self, task_id: &str) -> Result<(), String> {
        self.connection()?.execute("UPDATE claude_import_tasks SET status='CANCELLED',updated_at_ms=?2 WHERE id=?1 AND status NOT IN ('COMPLETED','CANCELLED')", params![task_id,local_now_millis()]).map_err(|_| json_error("无法取消 Claude 任务"))?;
        Ok(())
    }

    fn retry_claude_import_task(&self, task_id: &str) -> Result<(), String> {
        if !is_stable_id(task_id) {
            return Err(json_error("Claude 任务引用无效"));
        }
        let mut connection = self.connection()?;
        let (storage_key, package_hash): (String, String) = connection
            .query_row(
                "SELECT storage_key,package_hash FROM claude_import_tasks WHERE id=?1",
                [task_id],
                |row| Ok((row.get(0)?, row.get(1)?)),
            )
            .map_err(|_| json_error("Claude 任务不存在"))?;
        if !storage_key.starts_with(&format!("claude-import-assets/{task_id}/"))
            || !storage_key.ends_with(".json")
        {
            return Err(json_error("Claude 私有副本引用无效"));
        }
        let bytes = fs::read(self.root.join(storage_key))
            .map_err(|_| json_error("Claude 私有副本不可读；请重新选择文件"))?;
        if sha256(&bytes) != package_hash {
            return Err(json_error("Claude 私有副本 hash 不一致；请重新选择文件"));
        }
        let candidates = parse_claude_export(&bytes)?;
        let transaction = connection
            .transaction()
            .map_err(|_| json_error("无法开启 Claude 重试 transaction"))?;
        transaction
            .execute(
                "DELETE FROM claude_import_messages WHERE task_id=?1",
                [task_id],
            )
            .map_err(|_| json_error("无法重置 Claude 候选消息"))?;
        transaction
            .execute(
                "DELETE FROM claude_import_items WHERE task_id=?1",
                [task_id],
            )
            .map_err(|_| json_error("无法重置 Claude 候选"))?;
        for (ordinal, candidate) in candidates.iter().enumerate() {
            let item_id = format!("claude-item-{ordinal}");
            match candidate {
                Ok(candidate) => {
                    transaction.execute("INSERT INTO claude_import_items(task_id,id,ordinal,source_conversation_id,title,content_hash,status,failure,conversation_id) VALUES(?1,?2,?3,?4,?5,?6,'PENDING_CONFIRMATION',NULL,NULL)", params![task_id,item_id,ordinal,candidate.source_conversation_id,candidate.title,candidate.content_hash]).map_err(|_| json_error("无法重建 Claude 候选"))?;
                    for message in &candidate.messages {
                        transaction.execute("INSERT INTO claude_import_messages(task_id,item_id,source_message_id,parent_source_message_id,sibling_position,role,text,created_at_ms,imported_model) VALUES(?1,?2,?3,?4,?5,?6,?7,?8,NULL)", params![task_id,format!("claude-item-{ordinal}"),message.source_id,message.parent_source_id,message.sibling_position,message.role,message.text,message.created_at_ms]).map_err(|_| json_error("无法重建 Claude 候选消息"))?;
                    }
                }
                Err(reason) => {
                    transaction.execute("INSERT INTO claude_import_items(task_id,id,ordinal,source_conversation_id,title,content_hash,status,failure,conversation_id) VALUES(?1,?2,?3,NULL,NULL,NULL,'FAILED',?4,NULL)", params![task_id,item_id,ordinal,reason]).map_err(|_| json_error("无法重建 Claude 失败候选"))?;
                }
            }
        }
        transaction.execute("UPDATE claude_import_tasks SET status='AWAITING_CONFIRMATION',failure=NULL,retry_count=retry_count+1,updated_at_ms=?2 WHERE id=?1", params![task_id,local_now_millis()]).map_err(|_| json_error("无法更新 Claude 重试状态"))?;
        transaction
            .commit()
            .map_err(|_| json_error("Claude 重试未提交；已回滚"))
    }

    fn confirm_claude_import_item(
        &self,
        args: ClaudeImportItemActionArgs,
    ) -> Result<String, String> {
        if !is_stable_id(&args.workspace_id)
            || !is_stable_id(&args.task_id)
            || !is_stable_id(&args.item_id)
        {
            return Err(json_error("Claude 导入引用无效"));
        }
        let mut connection = self.connection()?;
        let transaction = connection
            .transaction()
            .map_err(|_| json_error("无法开启 Claude 确认 transaction"))?;
        let (package_hash, task_status): (String, String) = transaction
            .query_row(
                "SELECT package_hash,status FROM claude_import_tasks WHERE id=?1",
                [&args.task_id],
                |row| Ok((row.get(0)?, row.get(1)?)),
            )
            .map_err(|_| json_error("Claude 任务不存在"))?;
        if task_status == "CANCELLED" {
            return Err(json_error("Claude 任务已取消"));
        }
        let (source_id,title,content_hash,item_status): (String,String,String,String) = transaction.query_row("SELECT source_conversation_id,title,content_hash,status FROM claude_import_items WHERE task_id=?1 AND id=?2", params![args.task_id,args.item_id], |row| Ok((row.get(0)?,row.get(1)?,row.get(2)?,row.get(3)?))).map_err(|_| json_error("Claude 候选不存在或不可确认"))?;
        if item_status == "CONFIRMED" {
            let id = transaction
                .query_row(
                    "SELECT conversation_id FROM claude_import_items WHERE task_id=?1 AND id=?2",
                    params![args.task_id, args.item_id],
                    |row| row.get(0),
                )
                .map_err(|_| json_error("已确认 Claude 候选无法回读"))?;
            transaction
                .commit()
                .map_err(|_| json_error("无法结束 Claude 重放读取"))?;
            return Ok(id);
        }
        if item_status != "PENDING_CONFIRMATION" {
            return Err(json_error("该 Claude 候选不可确认"));
        }
        if let Ok((existing_id,existing_hash)) = transaction.query_row("SELECT conversation_id,content_hash FROM claude_import_receipts WHERE source_conversation_id=?1 AND package_hash=?2", params![source_id,package_hash], |row| Ok((row.get::<_,String>(0)?,row.get::<_,String>(1)?))) { if existing_hash != content_hash { return Err(json_error("同源不同内容，需作为冲突 reimport 处理")); } transaction.execute("UPDATE claude_import_items SET status='CONFIRMED',conversation_id=?3 WHERE task_id=?1 AND id=?2", params![args.task_id,args.item_id,existing_id]).map_err(|_| json_error("无法回写 Claude 幂等结果"))?; transaction.commit().map_err(|_| json_error("Claude 重放结果未提交"))?; return Ok(existing_id); }
        let messages = transaction.prepare("SELECT source_message_id,parent_source_message_id,sibling_position,role,text,created_at_ms FROM claude_import_messages WHERE task_id=?1 AND item_id=?2 ORDER BY sibling_position,source_message_id").map_err(|_| json_error("无法读取 Claude 候选消息"))?.query_map(params![args.task_id,args.item_id], |row| Ok((row.get::<_,String>(0)?,row.get::<_,Option<String>>(1)?,row.get::<_,i64>(2)?,row.get::<_,String>(3)?,row.get::<_,String>(4)?,row.get::<_,i64>(5)?))).map_err(|_| json_error("Claude 候选消息无效"))?.collect::<Result<Vec<_>,_>>().map_err(|_| json_error("Claude 候选消息无法读取"))?;
        if messages.is_empty() {
            return Err(json_error("Claude 候选没有安全消息"));
        }
        let conversation_id = format!(
            "conversation-claude-{}",
            &sha256(format!("{source_id}:{package_hash}").as_bytes())[..24]
        );
        let ids = messages
            .iter()
            .map(|message| {
                (
                    message.0.clone(),
                    format!(
                        "message-claude-{}-{}",
                        args.task_id["claude-task-".len()..]
                            .chars()
                            .take(12)
                            .collect::<String>(),
                        message.0
                    ),
                )
            })
            .collect::<BTreeMap<_, _>>();
        let leaf = messages
            .iter()
            .filter(|message| {
                !messages
                    .iter()
                    .any(|other| other.1.as_deref() == Some(&message.0))
            })
            .max_by_key(|message| (message.5, message.0.clone()))
            .map(|message| ids[&message.0].clone())
            .ok_or_else(|| json_error("Claude current leaf 无效"))?;
        let exchange_text: String = transaction
            .query_row(
                "SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?1",
                [&args.workspace_id],
                |row| row.get(0),
            )
            .map_err(|_| json_error("工作区不存在"))?;
        let mut exchange: Value =
            serde_json::from_str(&exchange_text).map_err(|_| json_error("本地工作区无法读取"))?;
        let conversation = json!({"id":conversation_id,"title":title,"revision":1,"projectId":Value::Null,"pinned":false,"archived":false,"deleted":false,"createdAt":rfc3339_from_unix_millis(messages[0].5),"updatedAt":rfc3339_from_unix_millis(messages.iter().map(|message| message.5).max().unwrap_or(messages[0].5)),"importedFrom":"CLAUDE_EXPORT","currentLeafId":leaf,"messages":messages.iter().map(|message| json!({"id":ids[&message.0],"parentId":message.1.as_ref().map(|parent| ids[parent].clone()),"ordinal":message.2,"role":message.3,"delivery":"COMPLETE","revision":1,"createdAt":rfc3339_from_unix_millis(message.5),"importedAtEpochMs":message.5,"importedModel":Value::Null,"blocks":[{"kind":"TEXT","text":message.4}]})).collect::<Vec<_>>()});
        exchange
            .as_object_mut()
            .ok_or_else(|| json_error("本地工作区根无效"))?
            .get_mut("conversations")
            .and_then(Value::as_array_mut)
            .ok_or_else(|| json_error("本地会话集合无效"))?
            .push(conversation);
        let semantic_hash = refresh_exchange_hash(&mut exchange)?;
        validate_exchange(&exchange)?;
        transaction
            .execute(
                "UPDATE workspace_exchange SET exchange_json=?1 WHERE workspace_id=?2",
                params![canonical_json(&exchange)?, args.workspace_id],
            )
            .map_err(|_| json_error("Claude 会话未写入"))?;
        self.rebuild_local_search_index(&transaction, &args.workspace_id, &exchange)?;
        transaction
            .execute(
                "UPDATE workspaces SET semantic_hash=?1 WHERE id=?2",
                params![semantic_hash, args.workspace_id],
            )
            .map_err(|_| json_error("Claude workspace hash 未更新"))?;
        transaction.execute("INSERT INTO claude_import_provenance(conversation_id,source_conversation_id,package_hash,content_hash,imported_at_ms,adapter_id,adapter_version,revoked_at_ms) VALUES(?1,?2,?3,?4,?5,'claude-export-json',1,NULL)", params![conversation_id,source_id,package_hash,content_hash,local_now_millis()]).map_err(|_| json_error("Claude provenance 未写入"))?;
        transaction.execute("INSERT INTO claude_import_receipts(source_conversation_id,package_hash,conversation_id,content_hash,committed_at_ms) VALUES(?1,?2,?3,?4,?5)", params![source_id,package_hash,conversation_id,content_hash,local_now_millis()]).map_err(|_| json_error("Claude receipt 未写入"))?;
        transaction.execute("UPDATE claude_import_items SET status='CONFIRMED',conversation_id=?3 WHERE task_id=?1 AND id=?2", params![args.task_id,args.item_id,conversation_id]).map_err(|_| json_error("Claude 项目状态未写入"))?;
        transaction.execute("UPDATE claude_import_tasks SET status=CASE WHEN NOT EXISTS(SELECT 1 FROM claude_import_items WHERE task_id=?1 AND status='PENDING_CONFIRMATION') THEN 'COMPLETED' ELSE 'PARTIALLY_COMPLETED' END,updated_at_ms=?2 WHERE id=?1", params![args.task_id,local_now_millis()]).map_err(|_| json_error("Claude 任务状态未写入"))?;
        transaction
            .commit()
            .map_err(|_| json_error("Claude 确认未提交；已回滚"))?;
        Ok(conversation_id)
    }

    fn import_staged(&self, args: ImportArgs) -> Result<WorkspaceProjection, String> {
        if !is_stable_id(&args.staging_id) && !is_sha256(&args.staging_id) {
            return Err(json_error("staging ID 无效"));
        }
        let title = args.workspace_title.trim();
        if title.is_empty() || title.chars().count() > 120 {
            return Err(json_error("新工作区名称必须为 1–120 个字符"));
        }
        let staging = self
            .root
            .join("staging")
            .join(format!("{}.nfai-exchange", args.staging_id));
        let preflight = preflight_package(
            fs::read(&staging).map_err(|_| json_error("staging 不存在或已清理；请重新选择文件"))?,
        )?;
        let workspace_id = format!("workspace-{}", &preflight.receipt.package_hash[..24]);
        // Write only verified, content-addressed private blobs before the DB transaction. A crash
        // before commit can leave an orphan blob, but never a visible half workspace.
        for (entry, content) in &preflight.assets {
            let hash = entry
                .strip_prefix("assets/")
                .ok_or_else(|| json_error("asset entry 无效"))?;
            let target = self.root.join("assets").join(hash);
            if !target.exists() {
                fs::write(target, content)
                    .map_err(|_| json_error("asset 私有 staging 落盘失败；未创建工作区"))?;
            }
        }
        let archived_package = self
            .root
            .join("packages")
            .join(format!("{}.nfai-exchange", preflight.receipt.package_hash));
        if !archived_package.exists() {
            fs::write(&archived_package, &preflight.bytes)
                .map_err(|_| json_error("原始交换包私有 staging 落盘失败；未创建工作区"))?;
        }
        let mut connection = self.connection()?;
        let transaction = connection
            .transaction()
            .map_err(|_| json_error("无法开启导入 transaction"))?;
        let already_exists: bool = transaction
            .query_row(
                "SELECT EXISTS(SELECT 1 FROM workspaces WHERE id = ?1)",
                [&workspace_id],
                |row| row.get(0),
            )
            .map_err(|_| json_error("无法检查 workspace"))?;
        if already_exists {
            return Err(json_error("该交换包已经导入为独立工作区；不执行隐式合并"));
        }
        self.commit_import(&transaction, &workspace_id, title, &preflight, false)?;
        transaction
            .commit()
            .map_err(|_| json_error("导入未提交；未创建半工作区"))?;
        let _ = fs::remove_file(staging);
        self.workspace_projection(&workspace_id)
    }

    fn commit_import(
        &self,
        transaction: &Transaction<'_>,
        workspace_id: &str,
        title: &str,
        preflight: &PreflightedPackage,
        inject_interrupt: bool,
    ) -> Result<(), String> {
        let now = "1970-01-01T00:00:00Z";
        transaction.execute("INSERT INTO workspaces(id, title, semantic_hash, package_hash, created_at) VALUES (?1, ?2, ?3, ?4, ?5)", params![workspace_id, title, preflight.receipt.semantic_hash, preflight.receipt.package_hash, now]).map_err(|_| json_error("无法写入 workspace"))?;
        if inject_interrupt {
            return Err(json_error("模拟导入中断；transaction 必须回滚"));
        }
        transaction
            .execute(
                "INSERT INTO workspace_exchange(workspace_id, exchange_json) VALUES (?1, ?2)",
                params![workspace_id, canonical_json(&preflight.exchange)?],
            )
            .map_err(|_| json_error("无法写入交换 IR"))?;
        self.rebuild_local_search_index(transaction, workspace_id, &preflight.exchange)?;
        // P6-C keeps the imported semantic origin separate from later local revisions. The
        // exchange format deliberately stays v1-compatible, so provenance remains private.
        let imported_root = object(&preflight.exchange, "exchange")?;
        for (entity, collection) in [
            ("project", "projects"),
            ("conversation", "conversations"),
            ("knowledge", "knowledge"),
            ("memory", "memory"),
            ("relation", "relations"),
        ] {
            for item in require_array(imported_root, collection)? {
                let item = object(item, collection)?;
                transaction.execute("INSERT INTO object_provenance(workspace_id, entity, object_id, source, origin_semantic_hash, imported_revision, edited_revision) VALUES (?1,?2,?3,'IMPORTED',?4,?5,NULL) ON CONFLICT(workspace_id,entity,object_id) DO NOTHING", params![workspace_id, entity, require_string(item,"id")?, preflight.receipt.semantic_hash, revision_of(item)?]).map_err(|_| json_error("无法记录 imported provenance"))?;
            }
        }
        for (entry, bytes) in &preflight.assets {
            transaction.execute("INSERT INTO workspace_assets(workspace_id, sha256, byte_count) VALUES (?1, ?2, ?3)", params![workspace_id, entry.strip_prefix("assets/").unwrap_or_default(), bytes.len() as u64]).map_err(|_| json_error("无法写入 asset 索引"))?;
        }
        transaction.execute("INSERT INTO import_journal(package_hash, workspace_id, semantic_hash, committed_at) VALUES (?1, ?2, ?3, ?4)", params![preflight.receipt.package_hash, workspace_id, preflight.receipt.semantic_hash, now]).map_err(|_| json_error("无法写入 import journal"))?;
        Ok(())
    }

    fn workspace_projection(&self, workspace_id: &str) -> Result<WorkspaceProjection, String> {
        let connection = self.connection()?;
        let row: (String, String, String, String) = connection.query_row("SELECT w.title, w.semantic_hash, w.package_hash, x.exchange_json FROM workspaces w JOIN workspace_exchange x ON x.workspace_id=w.id WHERE w.id=?1", [workspace_id], |row| Ok((row.get(0)?, row.get(1)?, row.get(2)?, row.get(3)?))).map_err(|_| json_error("工作区不存在"))?;
        let exchange: Value =
            serde_json::from_str(&row.3).map_err(|_| json_error("本地交换 IR 无法读取"))?;
        self.projection_from_exchange(workspace_id.to_owned(), row.0, row.1, row.2, exchange)
    }

    fn projection_from_exchange(
        &self,
        id: String,
        title: String,
        semantic_hash: String,
        package_hash: String,
        exchange: Value,
    ) -> Result<WorkspaceProjection, String> {
        let root = object(&exchange, "exchange")?;
        let assets = collect_asset_refs(&exchange)?;
        let mut asset_byte_count = 0;
        for entry in &assets {
            let hash = entry.strip_prefix("assets/").unwrap_or_default();
            asset_byte_count += fs::metadata(self.root.join("assets").join(hash))
                .map(|metadata| metadata.len())
                .unwrap_or(0);
        }
        let high_sensitive = exchange.to_string().contains("HIGH_SENSITIVE");
        Ok(WorkspaceProjection {
            summary: WorkspaceSummary {
                id,
                title,
                semantic_hash,
                package_hash,
                project_count: require_array(root, "projects")?.len(),
                conversation_count: require_array(root, "conversations")?.len(),
                knowledge_count: require_array(root, "knowledge")?.len(),
                memory_count: require_array(root, "memory")?.len(),
                relation_count: require_array(root, "relations")?.len(),
                asset_count: assets.len(),
                asset_byte_count,
                high_sensitive,
            },
            exchange,
        })
    }

    fn list_workspaces(&self) -> Result<Vec<WorkspaceSummary>, String> {
        let connection = self.connection()?;
        let mut statement = connection.prepare("SELECT id, title, semantic_hash, package_hash, exchange_json FROM workspaces JOIN workspace_exchange ON workspaces.id=workspace_exchange.workspace_id ORDER BY title COLLATE NOCASE, id").map_err(|_| json_error("无法读取 workspace 列表"))?;
        let rows = statement
            .query_map([], |row| {
                Ok((
                    row.get::<_, String>(0)?,
                    row.get::<_, String>(1)?,
                    row.get::<_, String>(2)?,
                    row.get::<_, String>(3)?,
                    row.get::<_, String>(4)?,
                ))
            })
            .map_err(|_| json_error("无法读取 workspace 行"))?;
        rows.map(|row| {
            let (id, title, semantic_hash, package_hash, exchange_json) =
                row.map_err(|_| json_error("workspace 行无效"))?;
            Ok(self
                .projection_from_exchange(
                    id,
                    title,
                    semantic_hash,
                    package_hash,
                    serde_json::from_str(&exchange_json)
                        .map_err(|_| json_error("workspace JSON 无效"))?,
                )?
                .summary)
        })
        .collect()
    }

    fn mutate_domain(&self, mut args: DomainMutationArgs) -> Result<MutationReceipt, String> {
        let mut connection = self.connection()?;
        let transaction = connection
            .transaction()
            .map_err(|_| json_error("无法开启领域 transaction"))?;
        if let Ok((object_id, revision, semantic_hash)) = transaction.query_row("SELECT object_id, result_revision, after_semantic_hash FROM domain_intents WHERE intent_id=?1", [&args.intent_id], |row| Ok((row.get::<_, String>(0)?, row.get::<_, u64>(1)?, row.get::<_, String>(2)?))) {
            transaction.commit().map_err(|_| json_error("无法结束重放读取"))?;
            return Ok(MutationReceipt { intent_id: args.intent_id, workspace_id: args.workspace_id, object_id, revision, semantic_hash, replayed: true });
        }
        let before_text: String = transaction
            .query_row(
                "SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?1",
                [&args.workspace_id],
                |row| row.get(0),
            )
            .map_err(|_| json_error("工作区不存在"))?;
        let before: Value =
            serde_json::from_str(&before_text).map_err(|_| json_error("本地交换 IR 无法读取"))?;
        self.hydrate_conversation_attachment_blocks(&transaction, &mut args)?;
        let before_hash = semantic_hash(&before)?;
        let mut after = before.clone();
        let (object_id, revision) = apply_domain_mutation(&mut after, &args)?;
        let after_hash = refresh_exchange_hash(&mut after)?;
        validate_exchange(&after)?;
        let after_text = canonical_json(&after)?;
        transaction
            .execute(
                "UPDATE workspace_exchange SET exchange_json=?1 WHERE workspace_id=?2",
                params![after_text, args.workspace_id],
            )
            .map_err(|_| json_error("无法保存领域修订"))?;
        self.rebuild_local_search_index(&transaction, &args.workspace_id, &after)?;
        transaction
            .execute(
                "UPDATE workspaces SET semantic_hash=?1 WHERE id=?2",
                params![after_hash, args.workspace_id],
            )
            .map_err(|_| json_error("无法更新工作区语义 hash"))?;
        transaction.execute("INSERT INTO domain_intents(intent_id, workspace_id, entity, action, object_id, expected_revision, before_exchange_json, after_exchange_json, before_semantic_hash, after_semantic_hash, undone, created_at, result_revision) VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,0,?11,?12)", params![args.intent_id, args.workspace_id, args.entity, args.action, object_id, args.expected_revision, canonical_json(&before)?, canonical_json(&after)?, before_hash, after_hash, local_now(), revision]).map_err(|_| json_error("无法追加领域 intent；不会覆盖历史"))?;
        transaction.execute("INSERT INTO object_provenance(workspace_id, entity, object_id, source, origin_semantic_hash, imported_revision, edited_revision) VALUES (?1,?2,?3,'LOCAL_EDIT',?4,NULL,?5) ON CONFLICT(workspace_id,entity,object_id) DO UPDATE SET source='LOCAL_EDIT', edited_revision=excluded.edited_revision", params![args.workspace_id, args.entity, object_id, before_hash, revision]).map_err(|_| json_error("无法记录对象来源"))?;
        transaction.execute("UPDATE domain_intents SET undone=1 WHERE workspace_id=?1 AND rowid < (SELECT rowid FROM domain_intents WHERE intent_id=?2) AND undone=2", params![args.workspace_id, args.intent_id]).map_err(|_| json_error("无法截断 redo 分支"))?;
        transaction
            .commit()
            .map_err(|_| json_error("领域写入未提交；已回滚"))?;
        Ok(MutationReceipt {
            intent_id: args.intent_id,
            workspace_id: args.workspace_id,
            object_id,
            revision,
            semantic_hash: after_hash,
            replayed: false,
        })
    }

    fn hydrate_conversation_attachment_blocks(
        &self,
        transaction: &Transaction<'_>,
        args: &mut DomainMutationArgs,
    ) -> Result<(), String> {
        if args.entity != "conversation"
            || !matches!(args.action.as_str(), "create" | "appendMessage")
        {
            return Ok(());
        }
        let fields = args
            .fields
            .as_object_mut()
            .ok_or_else(|| json_error("fields 必须是对象"))?;
        let Some(ids) = fields.remove("attachmentIds") else {
            return Ok(());
        };
        let ids = ids
            .as_array()
            .ok_or_else(|| json_error("attachmentIds 必须是数组"))?;
        if ids.len() > MAX_CONVERSATION_ATTACHMENT_COUNT {
            return Err(json_error("每条消息最多 4 个附件"));
        }
        let mut blocks = Vec::with_capacity(ids.len());
        let mut total = 0u64;
        let mut hashes = BTreeSet::new();
        for id in ids {
            let id = id
                .as_str()
                .filter(|id| is_stable_id(id))
                .ok_or_else(|| json_error("附件 ID 无效"))?;
            let metadata = transaction.query_row("SELECT attachment_id,mime_type,display_name,byte_count,sha256 FROM desktop_conversation_attachments WHERE workspace_id=?1 AND attachment_id=?2", params![args.workspace_id, id], |row| Ok(DesktopAttachmentMetadata { id: row.get(0)?, mime_type: row.get(1)?, display_name: row.get(2)?, byte_count: row.get(3)?, sha256: row.get(4)? }))
                .map_err(|_| json_error("附件未在当前私有工作区注册"))?;
            if !hashes.insert(metadata.sha256.clone()) {
                continue;
            }
            total = total.saturating_add(metadata.byte_count);
            if total > 40 * 1024 * 1024 {
                return Err(json_error("当前消息附件总大小超过 40 MB"));
            }
            blocks.push(json!({"id":metadata.id,"mimeType":metadata.mime_type,"displayName":metadata.display_name,"byteCount":metadata.byte_count,"sha256":metadata.sha256,"entry":format!("assets/{}", metadata.sha256)}));
        }
        fields.insert("attachmentBlocks".into(), Value::Array(blocks));
        Ok(())
    }

    fn adjust_snapshot_revision(
        snapshot: &mut Value,
        current: &Value,
        entity: &str,
        object_id: &str,
    ) -> Result<u64, String> {
        let current_root = current
            .as_object()
            .ok_or_else(|| json_error("当前 IR 无效"))?;
        let current_item = current_root
            .get(entity_array(entity)?)
            .and_then(Value::as_array)
            .and_then(|items| {
                items
                    .iter()
                    .find(|item| item.get("id").and_then(Value::as_str) == Some(object_id))
            })
            .and_then(Value::as_object);
        let snapshot_root = snapshot
            .as_object_mut()
            .ok_or_else(|| json_error("历史 IR 无效"))?;
        let snapshot_item = snapshot_root
            .get_mut(entity_array(entity)?)
            .and_then(Value::as_array_mut)
            .and_then(|items| {
                items
                    .iter_mut()
                    .find(|item| item.get("id").and_then(Value::as_str) == Some(object_id))
            })
            .and_then(Value::as_object_mut);
        let base_revision = match (current_item, snapshot_item.as_ref()) {
            (Some(item), _) => revision_of(item)?,
            (None, Some(item)) => revision_of(item)?,
            (None, None) => return Err(json_error("undo 对象已不存在，不能猜测覆盖")),
        };
        let next = base_revision
            .checked_add(1)
            .ok_or_else(|| json_error("revision 溢出"))?;
        if let Some(item) = snapshot_item {
            item.insert("revision".into(), Value::Number(next.into()));
            if entity != "relation" {
                item.insert("updatedAt".into(), Value::String(local_now().into()));
            }
        }
        Ok(next)
    }

    fn apply_history(&self, args: HistoryArgs, redo: bool) -> Result<MutationReceipt, String> {
        if !is_stable_id(&args.intent_id) || !is_stable_id(&args.workspace_id) {
            return Err(json_error("history intent 或 workspace ID 无效"));
        }
        let mut connection = self.connection()?;
        let transaction = connection
            .transaction()
            .map_err(|_| json_error("无法开启历史 transaction"))?;
        if let Ok((object_id, revision, semantic_hash)) = transaction.query_row(
            "SELECT object_id, result_revision, after_semantic_hash FROM domain_intents WHERE intent_id=?1",
            [&args.intent_id],
            |row| Ok((row.get::<_, String>(0)?, row.get::<_, u64>(1)?, row.get::<_, String>(2)?)),
        ) {
            transaction
                .commit()
                .map_err(|_| json_error("无法结束 history 重放读取"))?;
            return Ok(MutationReceipt {
                intent_id: args.intent_id,
                workspace_id: args.workspace_id,
                object_id,
                revision,
                semantic_hash,
                replayed: true,
            });
        }
        let wanted = if redo { 2 } else { 0 };
        let row: (String,String,String,String) = transaction.query_row("SELECT entity, object_id, before_exchange_json, after_exchange_json FROM domain_intents WHERE workspace_id=?1 AND undone=?2 ORDER BY rowid DESC LIMIT 1", params![args.workspace_id, wanted], |row| Ok((row.get(0)?,row.get(1)?,row.get(2)?,row.get(3)?))).map_err(|_| json_error(if redo { "没有可 redo 的本地动作" } else { "没有可 undo 的本地动作" }))?;
        let current_text: String = transaction
            .query_row(
                "SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?1",
                [&args.workspace_id],
                |row| row.get(0),
            )
            .map_err(|_| json_error("工作区不存在"))?;
        let current: Value =
            serde_json::from_str(&current_text).map_err(|_| json_error("当前 IR 无效"))?;
        let mut target: Value = serde_json::from_str(if redo { &row.3 } else { &row.2 })
            .map_err(|_| json_error("历史 IR 无效"))?;
        let revision = Self::adjust_snapshot_revision(&mut target, &current, &row.0, &row.1)?;
        let next_hash = refresh_exchange_hash(&mut target)?;
        validate_exchange(&target)?;
        transaction
            .execute(
                "UPDATE workspace_exchange SET exchange_json=?1 WHERE workspace_id=?2",
                params![canonical_json(&target)?, args.workspace_id],
            )
            .map_err(|_| json_error("无法保存历史 revision"))?;
        transaction
            .execute(
                "UPDATE workspaces SET semantic_hash=?1 WHERE id=?2",
                params![next_hash, args.workspace_id],
            )
            .map_err(|_| json_error("无法更新历史 hash"))?;
        transaction.execute("UPDATE domain_intents SET undone=?1 WHERE workspace_id=?2 AND entity=?3 AND object_id=?4 AND undone=?5", params![if redo { 0 } else { 2 }, args.workspace_id, row.0, row.1, wanted]).map_err(|_| json_error("无法更新动作栈"))?;
        transaction.execute("INSERT INTO domain_intents(intent_id, workspace_id, entity, action, object_id, expected_revision, before_exchange_json, after_exchange_json, before_semantic_hash, after_semantic_hash, undone, created_at, result_revision) VALUES (?1,?2,?3,?4,?5,NULL,?6,?7,?8,?9,1,?10,?11)", params![args.intent_id,args.workspace_id,row.0,if redo {"redo"} else {"undo"},row.1,canonical_json(&current)?,canonical_json(&target)?,semantic_hash(&current)?,next_hash,local_now(),revision]).map_err(|_| json_error("无法追加 undo/redo 历史"))?;
        transaction
            .commit()
            .map_err(|_| json_error("undo/redo 未提交；已回滚"))?;
        Ok(MutationReceipt {
            intent_id: args.intent_id,
            workspace_id: args.workspace_id,
            object_id: row.1,
            revision,
            semantic_hash: next_hash,
            replayed: false,
        })
    }

    fn workbench_history(&self, workspace_id: &str) -> Result<WorkbenchHistory, String> {
        let connection = self.connection()?;
        let exchange_text: String = connection
            .query_row(
                "SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?1",
                [workspace_id],
                |row| row.get(0),
            )
            .map_err(|_| json_error("工作区不存在"))?;
        let exchange: Value =
            serde_json::from_str(&exchange_text).map_err(|_| json_error("本地交换 IR 无效"))?;
        let mut recycle_bin = Vec::new();
        for entity in ["project", "conversation", "knowledge", "memory", "relation"] {
            for value in exchange
                .get(entity_array(entity)?)
                .and_then(Value::as_array)
                .into_iter()
                .flatten()
                .filter_map(Value::as_object)
            {
                if is_deleted(entity, value) {
                    recycle_bin.push(RecycleBinEntry {
                        entity: entity.into(),
                        id: value
                            .get("id")
                            .and_then(Value::as_str)
                            .unwrap_or_default()
                            .into(),
                        title: value
                            .get("title")
                            .or_else(|| value.get("body"))
                            .and_then(Value::as_str)
                            .unwrap_or("已删除对象")
                            .chars()
                            .take(40)
                            .collect(),
                        revision: revision_of(value)?,
                    });
                }
            }
        }
        let mut statement=connection.prepare("SELECT metadata_json FROM model_metadata WHERE workspace_id=?1 ORDER BY provider_id,model_id").map_err(|_|json_error("无法读取模型 metadata"))?;
        let model_metadata = statement
            .query_map([workspace_id], |row| row.get::<_, String>(0))
            .map_err(|_| json_error("无法读取模型 metadata"))?
            .filter_map(Result::ok)
            .filter_map(|text| serde_json::from_str(&text).ok())
            .collect();
        let can_undo: bool=connection.query_row("SELECT EXISTS(SELECT 1 FROM domain_intents WHERE workspace_id=?1 AND undone=0 AND action NOT IN ('undo','redo'))",[workspace_id],|row|row.get(0)).map_err(|_|json_error("无法读取 undo 栈"))?;
        let can_redo: bool = connection
            .query_row(
                "SELECT EXISTS(SELECT 1 FROM domain_intents WHERE workspace_id=?1 AND undone=2)",
                [workspace_id],
                |row| row.get(0),
            )
            .map_err(|_| json_error("无法读取 redo 栈"))?;
        Ok(WorkbenchHistory {
            can_undo,
            can_redo,
            recycle_bin,
            model_metadata,
        })
    }

    fn upsert_model_metadata(&self, args: ModelMetadataArgs) -> Result<MutationReceipt, String> {
        if !is_stable_id(&args.intent_id)
            || !is_stable_id(&args.workspace_id)
            || !is_stable_id(&args.provider_id)
            || !is_stable_id(&args.model_id)
        {
            return Err(json_error("metadata ID 无效"));
        }
        let metadata = args
            .metadata
            .as_object()
            .ok_or_else(|| json_error("metadata 必须是对象"))?;
        allowed_keys(
            metadata,
            &[
                "capabilities",
                "priceVersion",
                "currency",
                "source",
                "updatedAt",
                "pricingStatus",
                "safeUsage",
            ],
        )?;
        let forbidden = Value::Object(metadata.clone());
        ensure_no_forbidden_keys(&forbidden)?;
        if metadata
            .get("pricingStatus")
            .and_then(Value::as_str)
            .is_some_and(|value| !matches!(value, "FIXTURE" | "MANUAL" | "UNKNOWN"))
        {
            return Err(json_error("metadata pricingStatus 无效"));
        }
        let mut connection = self.connection()?;
        let transaction = connection
            .transaction()
            .map_err(|_| json_error("无法开启 metadata transaction"))?;
        if let Ok(replayed_revision) = transaction.query_row(
            "SELECT result_revision FROM domain_intents WHERE intent_id=?1",
            [&args.intent_id],
            |row| row.get::<_, u64>(0),
        ) {
            let workspace_id = args.workspace_id.clone();
            return Ok(MutationReceipt {
                intent_id: args.intent_id,
                workspace_id,
                object_id: format!("{}:{}", args.provider_id, args.model_id),
                revision: replayed_revision,
                semantic_hash: transaction
                    .query_row(
                        "SELECT semantic_hash FROM workspaces WHERE id=?1",
                        [&args.workspace_id],
                        |row| row.get(0),
                    )
                    .map_err(|_| json_error("工作区不存在"))?,
                replayed: true,
            });
        }
        let current: Option<u64>=transaction.query_row("SELECT revision FROM model_metadata WHERE workspace_id=?1 AND provider_id=?2 AND model_id=?3",params![args.workspace_id,args.provider_id,args.model_id],|row|row.get(0)).ok();
        if current.unwrap_or(0) != args.expected_revision {
            return Err(json_error("REVISION_CONFLICT：模型 metadata 已变化"));
        }
        let revision = args.expected_revision + 1;
        let safe = json!({"providerId":args.provider_id,"modelId":args.model_id,"revision":revision,"metadata":metadata,"localOnly":true,"costDisclosure":"非真实价格；仅本地 metadata"});
        transaction.execute("INSERT INTO model_metadata(workspace_id,provider_id,model_id,revision,metadata_json,updated_at) VALUES(?1,?2,?3,?4,?5,?6) ON CONFLICT(workspace_id,provider_id,model_id) DO UPDATE SET revision=excluded.revision,metadata_json=excluded.metadata_json,updated_at=excluded.updated_at",params![args.workspace_id,args.provider_id,args.model_id,revision,canonical_json(&safe)?,local_now()]).map_err(|_|json_error("无法保存模型 metadata"))?;
        let semantic_hash: String = transaction
            .query_row(
                "SELECT semantic_hash FROM workspaces WHERE id=?1",
                [&args.workspace_id],
                |row| row.get(0),
            )
            .map_err(|_| json_error("工作区不存在"))?;
        transaction.execute("INSERT INTO domain_intents(intent_id,workspace_id,entity,action,object_id,expected_revision,before_exchange_json,after_exchange_json,before_semantic_hash,after_semantic_hash,undone,created_at,result_revision) VALUES(?1,?2,'modelMetadata','upsert',?3,?4,'{}','{}',?5,?5,0,?6,?7)",params![args.intent_id,args.workspace_id,format!("{}:{}",args.provider_id,args.model_id),args.expected_revision,semantic_hash,local_now(),revision]).map_err(|_|json_error("无法记录 metadata intent"))?;
        transaction
            .commit()
            .map_err(|_| json_error("metadata 未提交；已回滚"))?;
        Ok(MutationReceipt {
            intent_id: args.intent_id,
            workspace_id: args.workspace_id,
            object_id: format!("{}:{}", args.provider_id, args.model_id),
            revision,
            semantic_hash,
            replayed: false,
        })
    }

    fn p6g_catalog(&self, connection: &Connection) -> Result<P6gCatalogProjection, String> {
        let row = connection
            .query_row(
                "SELECT revision,catalog_json FROM p6g_catalog WHERE id=1",
                [],
                |row| Ok((row.get::<_, u64>(0)?, row.get::<_, String>(1)?)),
            )
            .ok();
        match row {
            Some((revision, encoded)) => Ok(P6gCatalogProjection {
                revision,
                snapshot: serde_json::from_str(&encoded)
                    .ok()
                    .filter(|snapshot: &CatalogSnapshot| {
                        p6g::validate_catalog(snapshot, is_temporary_model_override_id).is_ok()
                    })
                    .unwrap_or_else(p6g::empty_catalog),
            }),
            None => Ok(P6gCatalogProjection {
                revision: 0,
                snapshot: p6g::empty_catalog(),
            }),
        }
    }

    fn p6g_global_default(&self, connection: &Connection) -> Result<GlobalDefault, String> {
        let row: Option<(u64, Option<String>)> = connection
            .query_row(
                "SELECT revision,tier FROM p6g_global_default WHERE id=1",
                [],
                |row| Ok((row.get(0)?, row.get(1)?)),
            )
            .ok();
        Ok(row
            .and_then(|(revision, encoded)| {
                encoded
                    .and_then(|value| serde_json::from_value(Value::String(value)).ok())
                    .map(|tier| GlobalDefault {
                        revision,
                        tier: Some(tier),
                    })
            })
            .unwrap_or(GlobalDefault {
                revision: 0,
                tier: None,
            }))
    }

    fn require_p6g_conversation(
        &self,
        connection: &Connection,
        workspace_id: &str,
        conversation_id: &str,
    ) -> Result<(), String> {
        if !is_stable_id(workspace_id) || !is_stable_id(conversation_id) {
            return Err(json_error("P6-G workspace 或 conversation ID 无效"));
        }
        let exchange: String = connection
            .query_row(
                "SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?1",
                [workspace_id],
                |row| row.get(0),
            )
            .map_err(|_| json_error("P6-G 工作区不存在"))?;
        let exists = serde_json::from_str::<Value>(&exchange)
            .ok()
            .and_then(|value| {
                value
                    .get("conversations")
                    .and_then(Value::as_array)
                    .map(|items| {
                        items.iter().any(|item| {
                            item.get("id").and_then(Value::as_str) == Some(conversation_id)
                                && item.get("deleted").and_then(Value::as_bool) != Some(true)
                        })
                    })
            })
            .unwrap_or(false);
        if !exists {
            return Err(json_error("P6-G 普通会话不存在或不可用"));
        }
        Ok(())
    }

    fn p6g_override(
        &self,
        connection: &Connection,
        workspace_id: &str,
        conversation_id: &str,
    ) -> Result<ConversationOverride, String> {
        self.require_p6g_conversation(connection, workspace_id, conversation_id)?;
        Ok(connection.query_row("SELECT revision,model_id FROM p6g_conversation_override WHERE workspace_id=?1 AND conversation_id=?2", params![workspace_id, conversation_id], |row| Ok(ConversationOverride { conversation_id: conversation_id.into(), revision: row.get(0)?, model_id: row.get(1)? })).unwrap_or(ConversationOverride { conversation_id: conversation_id.into(), revision: 0, model_id: None }))
    }

    fn p6g_last_route(
        &self,
        connection: &Connection,
        workspace_id: &str,
        conversation_id: &str,
    ) -> Result<Option<RouteMetadata>, String> {
        let encoded = connection.query_row("SELECT metadata_json FROM p6g_route_metadata WHERE workspace_id=?1 AND conversation_id=?2 ORDER BY revision DESC LIMIT 1", params![workspace_id, conversation_id], |row| row.get::<_, String>(0)).ok();
        Ok(encoded.and_then(|value| serde_json::from_str(&value).ok()))
    }

    fn p6g_selection(
        &self,
        workspace_id: &str,
        conversation_id: &str,
    ) -> Result<P6gSelectionProjection, String> {
        let connection = self.connection()?;
        let conversation_override =
            self.p6g_override(&connection, workspace_id, conversation_id)?;
        Ok(P6gSelectionProjection {
            catalog: self.p6g_catalog(&connection)?,
            global_default: self.p6g_global_default(&connection)?,
            conversation_override,
            last_route: self.p6g_last_route(&connection, workspace_id, conversation_id)?,
        })
    }

    fn p6g_upsert_catalog_candidate(
        &self,
        args: P6gCatalogCandidateArgs,
    ) -> Result<P6gMutationReceipt, String> {
        let mut connection = self.connection()?;
        let transaction = connection
            .transaction()
            .map_err(|_| json_error("无法开启 P6-G catalog transaction"))?;
        let current = self.p6g_catalog(&transaction)?;
        if current.revision != args.expected_revision {
            return Err(json_error("REVISION_CONFLICT：P6-G catalog 已变化"));
        }
        let mut snapshot = current.snapshot;
        snapshot.catalog_version = args.catalog_version;
        snapshot.policy_version = args.policy_version;
        if let Some(index) = snapshot
            .candidates
            .iter()
            .position(|item| item.model_id == args.candidate.model_id)
        {
            snapshot.candidates[index] = args.candidate;
        } else {
            snapshot.candidates.push(args.candidate);
        }
        p6g::validate_catalog(&snapshot, is_temporary_model_override_id).map_err(json_error)?;
        let revision = current
            .revision
            .checked_add(1)
            .ok_or_else(|| json_error("P6-G catalog revision 溢出"))?;
        let encoded = canonical_json(
            &serde_json::to_value(snapshot).map_err(|_| json_error("P6-G catalog 序列化失败"))?,
        )?;
        transaction.execute("INSERT INTO p6g_catalog(id,revision,catalog_json) VALUES(1,?1,?2) ON CONFLICT(id) DO UPDATE SET revision=excluded.revision,catalog_json=excluded.catalog_json", params![revision, encoded]).map_err(|_| json_error("无法保存 P6-G catalog"))?;
        transaction
            .commit()
            .map_err(|_| json_error("P6-G catalog 未提交；已回滚"))?;
        Ok(P6gMutationReceipt { revision })
    }

    fn p6g_remove_catalog_candidate(
        &self,
        args: P6gCatalogRemoveArgs,
    ) -> Result<P6gMutationReceipt, String> {
        if !is_temporary_model_override_id(&args.model_id) {
            return Err(json_error("P6-G model ID 无效"));
        }
        let mut connection = self.connection()?;
        let transaction = connection
            .transaction()
            .map_err(|_| json_error("无法开启 P6-G catalog transaction"))?;
        let current = self.p6g_catalog(&transaction)?;
        if current.revision != args.expected_revision {
            return Err(json_error("REVISION_CONFLICT：P6-G catalog 已变化"));
        }
        let mut snapshot = current.snapshot;
        snapshot.catalog_version = args.catalog_version;
        snapshot.policy_version = args.policy_version;
        let before = snapshot.candidates.len();
        snapshot
            .candidates
            .retain(|item| item.model_id != args.model_id);
        if before == snapshot.candidates.len() {
            return Err(json_error("P6-G catalog 不存在该模型"));
        }
        p6g::validate_catalog(&snapshot, is_temporary_model_override_id).map_err(json_error)?;
        let revision = current
            .revision
            .checked_add(1)
            .ok_or_else(|| json_error("P6-G catalog revision 溢出"))?;
        let encoded = canonical_json(
            &serde_json::to_value(snapshot).map_err(|_| json_error("P6-G catalog 序列化失败"))?,
        )?;
        transaction
            .execute(
                "UPDATE p6g_catalog SET revision=?1,catalog_json=?2 WHERE id=1",
                params![revision, encoded],
            )
            .map_err(|_| json_error("无法删除 P6-G catalog 项"))?;
        transaction
            .commit()
            .map_err(|_| json_error("P6-G catalog 未提交；已回滚"))?;
        Ok(P6gMutationReceipt { revision })
    }

    fn p6g_set_global_default(
        &self,
        args: P6gGlobalDefaultArgs,
    ) -> Result<P6gMutationReceipt, String> {
        let mut connection = self.connection()?;
        let transaction = connection
            .transaction()
            .map_err(|_| json_error("无法开启 P6-G global transaction"))?;
        let current = self.p6g_global_default(&transaction)?;
        if current.revision != args.expected_revision {
            return Err(json_error("REVISION_CONFLICT：P6-G 全局默认已变化"));
        }
        let revision = current
            .revision
            .checked_add(1)
            .ok_or_else(|| json_error("P6-G global revision 溢出"))?;
        let tier = args.tier.and_then(|value| {
            serde_json::to_value(value)
                .ok()
                .and_then(|encoded| encoded.as_str().map(str::to_owned))
        });
        transaction.execute("INSERT INTO p6g_global_default(id,revision,tier) VALUES(1,?1,?2) ON CONFLICT(id) DO UPDATE SET revision=excluded.revision,tier=excluded.tier", params![revision, tier]).map_err(|_| json_error("无法保存 P6-G 全局默认"))?;
        transaction
            .commit()
            .map_err(|_| json_error("P6-G 全局默认未提交；已回滚"))?;
        Ok(P6gMutationReceipt { revision })
    }

    fn p6g_set_conversation_override(
        &self,
        args: P6gConversationOverrideArgs,
    ) -> Result<P6gMutationReceipt, String> {
        let mut connection = self.connection()?;
        let transaction = connection
            .transaction()
            .map_err(|_| json_error("无法开启 P6-G override transaction"))?;
        let current = self.p6g_override(&transaction, &args.workspace_id, &args.conversation_id)?;
        if current.revision != args.expected_revision {
            return Err(json_error("REVISION_CONFLICT：P6-G 会话选择已变化"));
        }
        if let Some(model_id) = &args.model_id {
            let catalog = self.p6g_catalog(&transaction)?;
            if !catalog
                .snapshot
                .candidates
                .iter()
                .any(|item| &item.model_id == model_id)
            {
                return Err(json_error("P6-G 手动模型不在本地 catalog"));
            }
        }
        let revision = current
            .revision
            .checked_add(1)
            .ok_or_else(|| json_error("P6-G override revision 溢出"))?;
        transaction.execute("INSERT INTO p6g_conversation_override(workspace_id,conversation_id,revision,model_id) VALUES(?1,?2,?3,?4) ON CONFLICT(workspace_id,conversation_id) DO UPDATE SET revision=excluded.revision,model_id=excluded.model_id", params![args.workspace_id, args.conversation_id, revision, args.model_id]).map_err(|_| json_error("无法保存 P6-G 会话选择"))?;
        transaction
            .commit()
            .map_err(|_| json_error("P6-G 会话选择未提交；已回滚"))?;
        Ok(P6gMutationReceipt { revision })
    }

    fn p6g_evaluate(&self, args: P6gEvaluateArgs) -> Result<RouteDecision, String> {
        let mut connection = self.connection()?;
        let transaction = connection
            .transaction()
            .map_err(|_| json_error("无法开启 P6-G route transaction"))?;
        let catalog = self.p6g_catalog(&transaction)?;
        let global = self.p6g_global_default(&transaction)?;
        let override_value =
            self.p6g_override(&transaction, &args.workspace_id, &args.conversation_id)?;
        let mut request = args.request;
        if let Some(tier) = global.tier {
            request.tier = tier;
        }
        let decision = p6g::route(
            &request,
            override_value.model_id.as_deref(),
            &catalog.snapshot.candidates,
        );
        let metadata = RouteMetadata {
            conversation_id: args.conversation_id.clone(),
            policy_version: catalog.snapshot.policy_version,
            catalog_version: catalog.snapshot.catalog_version,
            tier: request.tier,
            source: decision.source.clone(),
            reason: decision.reason.clone(),
            model_id: decision
                .candidate
                .as_ref()
                .map(|item| item.model_id.clone()),
            display_name: decision
                .candidate
                .as_ref()
                .map(|item| item.display_name.clone()),
            rejected_candidates: decision.rejected_candidates.clone(),
        };
        let revision: u64 = transaction.query_row("SELECT COALESCE(MAX(revision),0) + 1 FROM p6g_route_metadata WHERE workspace_id=?1 AND conversation_id=?2", params![args.workspace_id, args.conversation_id], |row| row.get(0)).map_err(|_| json_error("无法读取 P6-G route revision"))?;
        transaction.execute("INSERT INTO p6g_route_metadata(workspace_id,conversation_id,revision,metadata_json,created_at_ms) VALUES(?1,?2,?3,?4,?5)", params![args.workspace_id, args.conversation_id, revision, canonical_json(&serde_json::to_value(metadata).map_err(|_| json_error("P6-G route metadata 序列化失败"))?)?, system_now_millis()]).map_err(|_| json_error("无法保存 P6-G route metadata"))?;
        transaction
            .commit()
            .map_err(|_| json_error("P6-G route metadata 未提交；已回滚"))?;
        Ok(decision)
    }

    fn export_workspace_to_path(
        &self,
        workspace_id: &str,
        selected_path: &str,
    ) -> Result<PreflightReceipt, String> {
        if !is_stable_id(workspace_id)
            || Path::new(selected_path)
                .extension()
                .and_then(|value| value.to_str())
                != Some("nfai-exchange")
        {
            return Err(json_error(
                "导出参数无效；必须由文件选择器提供 .nfai-exchange 目标",
            ));
        }
        let projection = self.workspace_projection(workspace_id)?;
        let mut assets = BTreeMap::new();
        for entry in collect_asset_refs(&projection.exchange)? {
            let hash = entry.strip_prefix("assets/").unwrap_or_default().to_owned();
            assets.insert(
                entry,
                fs::read(self.root.join("assets").join(hash))
                    .map_err(|_| json_error("私有 asset 缺失，拒绝导出"))?,
            );
        }
        let bytes = package_exchange(&projection.exchange, &assets)?;
        let check = preflight_package(bytes.clone())?;
        let target = PathBuf::from(selected_path);
        let temporary = target.with_extension("nfai-exchange.part");
        fs::write(&temporary, bytes).map_err(|_| json_error("无法写入用户选择的导出文件"))?;
        fs::rename(&temporary, &target).map_err(|_| json_error("无法原子完成导出文件"))?;
        let readback =
            preflight_package(fs::read(&target).map_err(|_| json_error("导出回读失败"))?)?;
        if readback.receipt.semantic_hash != check.receipt.semantic_hash
            || readback.receipt.package_hash != check.receipt.package_hash
        {
            return Err(json_error("导出回读 hash 不一致"));
        }
        Ok(readback.receipt)
    }
}

fn package_exchange(
    exchange: &Value,
    assets: &BTreeMap<String, Vec<u8>>,
) -> Result<Vec<u8>, String> {
    validate_exchange(exchange)?;
    let root = object(exchange, "exchange")?;
    let mut payloads = BTreeMap::new();
    payloads.insert(
        PAYLOADS[0].to_owned(),
        canonical_json(
            root.get("projects")
                .ok_or_else(|| json_error("projects 缺失"))?,
        )?
        .into_bytes(),
    );
    payloads.insert(
        PAYLOADS[1].to_owned(),
        canonical_json(
            root.get("conversations")
                .ok_or_else(|| json_error("conversations 缺失"))?,
        )?
        .into_bytes(),
    );
    payloads.insert(
        PAYLOADS[2].to_owned(),
        canonical_json(
            root.get("knowledge")
                .ok_or_else(|| json_error("knowledge 缺失"))?,
        )?
        .into_bytes(),
    );
    payloads.insert(
        PAYLOADS[3].to_owned(),
        canonical_json(
            root.get("memory")
                .ok_or_else(|| json_error("memory 缺失"))?,
        )?
        .into_bytes(),
    );
    payloads.insert(
        PAYLOADS[4].to_owned(),
        canonical_json(
            root.get("relations")
                .ok_or_else(|| json_error("relations 缺失"))?,
        )?
        .into_bytes(),
    );
    payloads.insert(
        PAYLOADS[5].to_owned(),
        canonical_json(
            root.get("settings")
                .ok_or_else(|| json_error("settings 缺失"))?,
        )?
        .into_bytes(),
    );
    for (entry, bytes) in assets {
        payloads.insert(entry.to_owned(), bytes.clone());
    }
    let files: Vec<Value> = payloads.iter().map(|(path, bytes)| json!({"path": path, "byteCount": bytes.len(), "sha256": sha256(bytes)})).collect();
    let manifest = json!({"format": "nfai.exchange.package", "packageVersion": 1, "exchangeVersion": 1, "export": root.get("export").ok_or_else(|| json_error("export 缺失"))?, "files": files});
    let mut writer = ZipWriter::new(Cursor::new(Vec::new()));
    let options = SimpleFileOptions::default()
        .compression_method(CompressionMethod::Deflated)
        .compression_level(Some(9));
    writer
        .start_file("manifest.json", options)
        .map_err(|_| json_error("无法创建 manifest entry"))?;
    writer
        .write_all(canonical_json(&manifest)?.as_bytes())
        .map_err(|_| json_error("无法写入 manifest"))?;
    for (path, bytes) in payloads {
        writer
            .start_file(path, options)
            .map_err(|_| json_error("无法创建 payload entry"))?;
        writer
            .write_all(&bytes)
            .map_err(|_| json_error("无法写入 payload entry"))?;
    }
    writer
        .finish()
        .map_err(|_| json_error("无法完成 ZIP"))?
        .into_inner()
        .pipe(Ok)
}

trait Pipe: Sized {
    fn pipe<T>(self, transform: impl FnOnce(Self) -> T) -> T {
        transform(self)
    }
}
impl<T> Pipe for T {}

#[tauri::command]
fn stage_preflight_selected_exchange(
    state: State<'_, AppState>,
    selected_path: String,
) -> Result<PreflightReceipt, String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .stage_selected_file(&selected_path)
}

#[tauri::command]
fn stage_chatgpt_export_selected(
    state: State<'_, AppState>,
    selected_path: String,
) -> Result<ChatGptImportTaskProjection, String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .stage_chatgpt_export_selected(&selected_path)
}

#[tauri::command]
fn stage_p6k_zip_import_selected(state: State<'_, AppState>, args: P6kZipImportSelectionArgs) -> Result<P6kZipImportTaskProjection, String> {
    state.store.lock().map_err(|_| json_error("Desktop store 被锁定"))?.stage_p6k_zip_import_selected(args)
}

#[tauri::command]
fn retry_p6k_zip_import_task(state: State<'_, AppState>, task_id: String) -> Result<P6kZipImportTaskProjection, String> {
    state.store.lock().map_err(|_| json_error("Desktop store 被锁定"))?.retry_p6k_zip_import_task(&task_id)
}

#[tauri::command]
fn skip_p6k_zip_import_failures(state: State<'_, AppState>, task_id: String) -> Result<P6kZipImportTaskProjection, String> {
    state.store.lock().map_err(|_| json_error("Desktop store 被锁定"))?.skip_p6k_zip_import_failures(&task_id)
}

#[tauri::command]
fn delete_p6k_zip_import_batch(state: State<'_, AppState>, task_id: String) -> Result<(), String> {
    state.store.lock().map_err(|_| json_error("Desktop store 被锁定"))?.delete_p6k_zip_import_batch(&task_id)
}

#[tauri::command]
fn read_latest_p6k_zip_import_task(state: State<'_, AppState>) -> Result<Option<P6kZipImportTaskProjection>, String> {
    state.store.lock().map_err(|_| json_error("Desktop store 被锁定"))?.read_latest_p6k_zip_import_task()
}

#[tauri::command]
fn link_p6k_zip_manual_asset(state: State<'_, AppState>, args: P6kManualAssetLinkArgs) -> Result<P6kZipImportTaskProjection, String> {
    state.store.lock().map_err(|_| json_error("Desktop store 被锁定"))?.link_p6k_zip_manual_asset(args)
}

#[tauri::command]
fn read_desktop_p6k_profile_personalization_settings_status(state: State<'_, AppState>) -> Result<P6kProfilePersonalizationSettingsStatusProjection, String> {
    state.store.lock().map_err(|_| json_error("Desktop store 被锁定"))?.p6k_profile_personalization_settings_status()
}

#[tauri::command]
fn confirm_chatgpt_import_item(
    state: State<'_, AppState>,
    args: ChatGptImportItemActionArgs,
) -> Result<String, String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .confirm_chatgpt_import_item(args)
}

#[tauri::command]
fn skip_chatgpt_import_item(
    state: State<'_, AppState>,
    args: ChatGptImportItemActionArgs,
) -> Result<(), String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .skip_chatgpt_import_item(args)
}

#[tauri::command]
fn read_chatgpt_import_task(
    state: State<'_, AppState>,
    task_id: String,
) -> Result<ChatGptImportTaskReadProjection, String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .read_chatgpt_import_task(&task_id)
}

#[tauri::command]
fn cancel_chatgpt_import_task(state: State<'_, AppState>, task_id: String) -> Result<(), String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .cancel_chatgpt_import_task(&task_id)
}

#[tauri::command]
fn retry_chatgpt_import_task(state: State<'_, AppState>, task_id: String) -> Result<(), String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .retry_chatgpt_import_task(&task_id)
}

#[tauri::command]
fn read_latest_chatgpt_import_task(
    state: State<'_, AppState>,
) -> Result<Option<ChatGptImportTaskReadProjection>, String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .read_latest_chatgpt_import_task()
}

#[tauri::command]
fn stage_claude_export_selected(
    state: State<'_, AppState>,
    selected_path: String,
) -> Result<ClaudeImportTaskProjection, String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .stage_claude_export_selected(&selected_path)
}

#[tauri::command]
fn confirm_claude_import_item(
    state: State<'_, AppState>,
    args: ClaudeImportItemActionArgs,
) -> Result<String, String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .confirm_claude_import_item(args)
}

#[tauri::command]
fn skip_claude_import_item(
    state: State<'_, AppState>,
    args: ClaudeImportItemActionArgs,
) -> Result<(), String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .skip_claude_import_item(args)
}

#[tauri::command]
fn read_claude_import_task(
    state: State<'_, AppState>,
    task_id: String,
) -> Result<ClaudeImportTaskReadProjection, String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .read_claude_import_task(&task_id)
}

#[tauri::command]
fn cancel_claude_import_task(state: State<'_, AppState>, task_id: String) -> Result<(), String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .cancel_claude_import_task(&task_id)
}

#[tauri::command]
fn retry_claude_import_task(state: State<'_, AppState>, task_id: String) -> Result<(), String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .retry_claude_import_task(&task_id)
}

#[tauri::command]
fn stage_nanfeng_knowledge_export_selected(state: State<'_, AppState>, selected_path: String) -> Result<ChatGptImportTaskProjection, String> { state.store.lock().map_err(|_| json_error("Desktop store 被锁定"))?.stage_nanfeng_knowledge_export_selected(&selected_path) }
#[tauri::command]
fn confirm_nanfeng_knowledge_import_item(state: State<'_, AppState>, args: ChatGptImportItemActionArgs) -> Result<String, String> { state.store.lock().map_err(|_| json_error("Desktop store 被锁定"))?.confirm_nanfeng_knowledge_import_item(args) }
#[tauri::command]
fn skip_nanfeng_knowledge_import_item(state: State<'_, AppState>, args: ChatGptImportItemActionArgs) -> Result<(), String> { state.store.lock().map_err(|_| json_error("Desktop store 被锁定"))?.skip_nanfeng_knowledge_import_item(args) }
#[tauri::command]
fn read_nanfeng_knowledge_import_task(state: State<'_, AppState>, task_id: String) -> Result<ChatGptImportTaskReadProjection, String> { state.store.lock().map_err(|_| json_error("Desktop store 被锁定"))?.read_nanfeng_knowledge_import_task(&task_id) }
#[tauri::command]
fn read_latest_nanfeng_knowledge_import_task(state: State<'_, AppState>) -> Result<Option<ChatGptImportTaskReadProjection>, String> { state.store.lock().map_err(|_| json_error("Desktop store 被锁定"))?.read_latest_nanfeng_knowledge_import_task() }
#[tauri::command]
fn cancel_nanfeng_knowledge_import_task(state: State<'_, AppState>, task_id: String) -> Result<(), String> { state.store.lock().map_err(|_| json_error("Desktop store 被锁定"))?.cancel_nanfeng_knowledge_import_task(&task_id) }
#[tauri::command]
fn retry_nanfeng_knowledge_import_task(state: State<'_, AppState>, task_id: String) -> Result<(), String> { state.store.lock().map_err(|_| json_error("Desktop store 被锁定"))?.retry_nanfeng_knowledge_import_task(&task_id) }

#[tauri::command]
fn read_latest_claude_import_task(
    state: State<'_, AppState>,
) -> Result<Option<ClaudeImportTaskReadProjection>, String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .read_latest_claude_import_task()
}

#[tauri::command]
fn read_p6h_diagnostics_status(state: State<'_, AppState>) -> bool {
    state.p6h_acceptance_enabled
}

#[tauri::command]
fn import_staged_exchange_as_new_workspace(
    state: State<'_, AppState>,
    args: ImportArgs,
) -> Result<WorkspaceProjection, String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .import_staged(args)
}

#[tauri::command]
fn list_desktop_workspaces(state: State<'_, AppState>) -> Result<Vec<WorkspaceSummary>, String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .list_workspaces()
}

#[tauri::command]
fn read_desktop_workspace(
    state: State<'_, AppState>,
    workspace_id: String,
) -> Result<WorkspaceProjection, String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .workspace_projection(&workspace_id)
}

#[tauri::command]
fn search_desktop_local_index(
    state: State<'_, AppState>,
    workspace_id: String,
    query: String,
) -> Result<Vec<DesktopLocalSearchHit>, String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .search_local_index(&workspace_id, &query)
}

#[tauri::command]
fn read_desktop_local_search_history(
    state: State<'_, AppState>,
    workspace_id: String,
) -> Result<Vec<String>, String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .local_search_history(&workspace_id)
}
#[tauri::command]
fn clear_desktop_local_search_history(
    state: State<'_, AppState>,
    workspace_id: String,
) -> Result<(), String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .clear_local_search_history(&workspace_id)
}

#[tauri::command]
fn read_desktop_image_preview(
    state: State<'_, AppState>,
    args: DesktopImagePreviewArgs,
) -> Result<DesktopImagePreview, String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .image_preview(&args)
}

#[tauri::command]
fn read_desktop_pdf_preview(
    state: State<'_, AppState>,
    args: DesktopPdfPreviewArgs,
) -> Result<DesktopPdfPreview, String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("本地 PDF 预览锁不可用"))?
        .pdf_preview(&args)
}

#[tauri::command]
fn read_desktop_video_preview(
    state: State<'_, AppState>,
    args: DesktopVideoPreviewArgs,
) -> Result<DesktopVideoPreview, String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("本地视频预览锁不可用"))?
        .video_preview(&args)
}

#[tauri::command]
fn read_desktop_audio_preview(
    state: State<'_, AppState>,
    args: DesktopAudioPreviewArgs,
) -> Result<DesktopAudioPreview, String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("本地音频预览锁不可用"))?
        .audio_preview(&args)
}

#[tauri::command]
fn read_desktop_text_preview(
    state: State<'_, AppState>,
    args: DesktopTextPreviewArgs,
) -> Result<DesktopTextPreview, String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("本地文本预览锁不可用"))?
        .text_preview(&args)
}

#[tauri::command]
fn export_desktop_workspace_to_selected_path(
    state: State<'_, AppState>,
    workspace_id: String,
    selected_path: String,
) -> Result<PreflightReceipt, String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .export_workspace_to_path(&workspace_id, &selected_path)
}

#[tauri::command]
fn mutate_desktop_domain(
    state: State<'_, AppState>,
    args: DomainMutationArgs,
) -> Result<MutationReceipt, String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .mutate_domain(args)
}

#[tauri::command]
fn import_desktop_conversation_attachment(
    state: State<'_, AppState>,
    args: DesktopAttachmentImportArgs,
) -> Result<DesktopAttachmentMetadata, String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .import_conversation_attachment(args)
}

#[tauri::command]
fn enter_or_restore_desktop_temporary_conversation(
    state: State<'_, AppState>,
    temporary_id: Option<String>,
) -> Result<DesktopTemporaryConversationRecovery, String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .enter_or_restore_temporary(temporary_id)
}

#[tauri::command]
fn read_desktop_temporary_conversation(
    state: State<'_, AppState>,
) -> Result<Option<DesktopTemporaryConversationRecovery>, String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .read_temporary_recovery()
}

#[tauri::command]
fn update_desktop_temporary_conversation(
    state: State<'_, AppState>,
    args: TemporaryConversationUpdateArgs,
) -> Result<DesktopTemporaryConversationRecovery, String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .update_temporary(args)
}

#[tauri::command]
fn append_desktop_temporary_message(
    state: State<'_, AppState>,
    args: TemporaryConversationAppendArgs,
) -> Result<DesktopTemporaryConversationRecovery, String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .append_temporary_message(args)
}

#[tauri::command]
fn import_desktop_temporary_attachment(
    state: State<'_, AppState>,
    args: DesktopTemporaryAttachmentImportArgs,
) -> Result<DesktopTemporaryConversationRecovery, String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .import_temporary_attachment(args)
}

#[tauri::command]
fn clear_desktop_temporary_conversation(
    state: State<'_, AppState>,
    args: TemporaryConversationClearArgs,
) -> Result<(), String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .clear_temporary(&args.temporary_id)
}

#[tauri::command]
fn remove_desktop_temporary_attachment(
    state: State<'_, AppState>,
    args: TemporaryConversationRemoveAttachmentArgs,
) -> Result<DesktopTemporaryConversationRecovery, String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .remove_temporary_attachment(args)
}

#[tauri::command]
fn undo_desktop_domain(
    state: State<'_, AppState>,
    args: HistoryArgs,
) -> Result<MutationReceipt, String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .apply_history(args, false)
}

#[tauri::command]
fn redo_desktop_domain(
    state: State<'_, AppState>,
    args: HistoryArgs,
) -> Result<MutationReceipt, String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .apply_history(args, true)
}

#[tauri::command]
fn read_desktop_workbench_history(
    state: State<'_, AppState>,
    workspace_id: String,
) -> Result<WorkbenchHistory, String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .workbench_history(&workspace_id)
}

#[tauri::command]
fn upsert_desktop_model_metadata(
    state: State<'_, AppState>,
    args: ModelMetadataArgs,
) -> Result<MutationReceipt, String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .upsert_model_metadata(args)
}

/// P6-G local selection commands. They expose only catalog/default/override/route facts; no
/// command can receive a Key, URL, Prompt, HTTP payload, Invocation or executor instruction.
#[tauri::command]
fn read_desktop_p6g_catalog(state: State<'_, AppState>) -> Result<P6gCatalogProjection, String> {
    let store = state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?;
    store.p6g_catalog(&store.connection()?)
}

#[tauri::command]
fn read_desktop_p6g_global_default(state: State<'_, AppState>) -> Result<GlobalDefault, String> {
    let store = state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?;
    store.p6g_global_default(&store.connection()?)
}

#[tauri::command]
fn upsert_desktop_p6g_catalog_candidate(
    state: State<'_, AppState>,
    args: P6gCatalogCandidateArgs,
) -> Result<P6gMutationReceipt, String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .p6g_upsert_catalog_candidate(args)
}

#[tauri::command]
fn remove_desktop_p6g_catalog_candidate(
    state: State<'_, AppState>,
    args: P6gCatalogRemoveArgs,
) -> Result<P6gMutationReceipt, String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .p6g_remove_catalog_candidate(args)
}

#[tauri::command]
fn read_desktop_p6g_selection(
    state: State<'_, AppState>,
    workspace_id: String,
    conversation_id: String,
) -> Result<P6gSelectionProjection, String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .p6g_selection(&workspace_id, &conversation_id)
}

#[tauri::command]
fn set_desktop_p6g_global_default(
    state: State<'_, AppState>,
    args: P6gGlobalDefaultArgs,
) -> Result<P6gMutationReceipt, String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .p6g_set_global_default(args)
}

#[tauri::command]
fn set_desktop_p6g_conversation_override(
    state: State<'_, AppState>,
    args: P6gConversationOverrideArgs,
) -> Result<P6gMutationReceipt, String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .p6g_set_conversation_override(args)
}

#[tauri::command]
fn clear_desktop_p6g_conversation_override(
    state: State<'_, AppState>,
    workspace_id: String,
    conversation_id: String,
    expected_revision: u64,
) -> Result<P6gMutationReceipt, String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .p6g_set_conversation_override(P6gConversationOverrideArgs {
            workspace_id,
            conversation_id,
            expected_revision,
            model_id: None,
        })
}

#[tauri::command]
fn evaluate_desktop_p6g_auto_route(
    state: State<'_, AppState>,
    args: P6gEvaluateArgs,
) -> Result<RouteDecision, String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .p6g_evaluate(args)
}

/// P8-C permits durable local inspection only. No P8 executor is exposed through Tauri.
#[tauri::command]
fn inspect_p8_agent_runs(
    state: State<'_, AppState>,
) -> Result<Vec<p8_agent_ledger_v1::RunInspection>, String> {
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .p8_agent_ledger
        .inspect_runs()
}

/// P10-A status only: no Key value is read, no transport is constructed, and no state is written.
#[tauri::command]
fn read_dual_path_status() -> dual_path_contract_v1::ConnectionCapability {
    dual_path_contract_v1::current_status(&dual_path_contract_v1::NoCredentialStore)
}

/// Deliberately no-argument.  Production always reports disabled and never reveals the fixture
/// receipt; only an explicitly marked process has the dedicated `/tmp` root.
#[tauri::command]
fn read_p6e_temporary_maintenance_acceptance_status(
    state: State<'_, AppState>,
) -> Result<P6eAcceptanceStatus, String> {
    if !state.p6e_acceptance_enabled {
        return Ok(P6eAcceptanceStatus {
            enabled: false,
            receipt: None,
        });
    }
    let store = state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?;
    Ok(P6eAcceptanceStatus {
        enabled: true,
        receipt: store.p6e_acceptance_receipt()?,
    })
}

/// Deliberately no-argument.  The env marker and root selection happen before Tauri starts, so a
/// normal production window cannot use this controlled-clock maintenance fixture.
#[tauri::command]
fn run_p6e_temporary_maintenance_acceptance(
    state: State<'_, AppState>,
) -> Result<P6eTemporaryMaintenanceAcceptanceReceipt, String> {
    if !state.p6e_acceptance_enabled {
        return Err(json_error("P6-E 维护验收仅限专用 acceptance 启动"));
    }
    state
        .store
        .lock()
        .map_err(|_| json_error("Desktop store 被锁定"))?
        .run_p6e_temporary_maintenance_acceptance()
}

pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .setup(|app| {
            let p6e_acceptance_enabled =
                std::env::var(P6E_ACCEPTANCE_ENV).ok().as_deref() == Some("1");
            let p6h_acceptance_enabled = std::env::var(P6H_ACCEPTANCE_ENV).ok().as_deref()
                == Some("1")
                || std::env::args().any(|argument| argument == "--p6h-acceptance");
            let p6i_acceptance_enabled = std::env::var(P6I_ACCEPTANCE_ENV).ok().as_deref()
                == Some("1")
                || std::env::args().any(|argument| argument == "--p6i-acceptance");
            let p6j_acceptance_enabled = std::env::var(P6J_ACCEPTANCE_ENV).ok().as_deref()
                == Some("1")
                || std::env::args().any(|argument| argument == "--p6j-acceptance");
            let fb_p6_050_acceptance_enabled = std::env::var(FB_P6_050_ACCEPTANCE_ENV)
                .ok()
                .as_deref()
                == Some("1")
                || std::env::args().any(|argument| argument == "--fb-p6-050-acceptance");
            let root = if p6e_acceptance_enabled {
                PathBuf::from("/tmp").join(P6E_ACCEPTANCE_ROOT_NAME)
            } else if p6h_acceptance_enabled {
                PathBuf::from("/tmp").join(P6H_ACCEPTANCE_ROOT_NAME)
            } else if p6i_acceptance_enabled {
                PathBuf::from("/tmp").join(P6I_ACCEPTANCE_ROOT_NAME)
            } else if p6j_acceptance_enabled {
                PathBuf::from("/tmp").join(P6J_ACCEPTANCE_ROOT_NAME)
            } else if fb_p6_050_acceptance_enabled {
                PathBuf::from("/tmp").join(FB_P6_050_ACCEPTANCE_ROOT_NAME)
            } else {
                app.path()
                    .app_data_dir()
                    .map_err(|_| "private app data unavailable")?
                    .join("p6b-workspace")
            };
            app.manage(AppState {
                store: Mutex::new(
                    DesktopWorkspaceStore::open(root).map_err(|_| "desktop SQLite unavailable")?,
                ),
                p6e_acceptance_enabled,
                p6h_acceptance_enabled,
            });
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            stage_preflight_selected_exchange,
            stage_chatgpt_export_selected,
            stage_p6k_zip_import_selected,
            retry_p6k_zip_import_task,
            skip_p6k_zip_import_failures,
            delete_p6k_zip_import_batch,
            read_latest_p6k_zip_import_task,
            link_p6k_zip_manual_asset,
            read_desktop_p6k_profile_personalization_settings_status,
            confirm_chatgpt_import_item,
            skip_chatgpt_import_item,
            read_chatgpt_import_task,
            cancel_chatgpt_import_task,
            retry_chatgpt_import_task,
            read_latest_chatgpt_import_task,
            stage_claude_export_selected,
            confirm_claude_import_item,
            skip_claude_import_item,
            read_claude_import_task,
            cancel_claude_import_task,
            retry_claude_import_task,
            read_latest_claude_import_task,
            stage_nanfeng_knowledge_export_selected,
            confirm_nanfeng_knowledge_import_item,
            skip_nanfeng_knowledge_import_item,
            read_nanfeng_knowledge_import_task,
            read_latest_nanfeng_knowledge_import_task,
            cancel_nanfeng_knowledge_import_task,
            retry_nanfeng_knowledge_import_task,
            read_p6h_diagnostics_status,
            import_staged_exchange_as_new_workspace,
            list_desktop_workspaces,
            read_desktop_workspace,
            search_desktop_local_index,
            read_desktop_local_search_history,
            clear_desktop_local_search_history,
            read_desktop_image_preview,
            read_desktop_pdf_preview,
            read_desktop_video_preview,
            read_desktop_audio_preview,
            read_desktop_text_preview,
            export_desktop_workspace_to_selected_path,
            mutate_desktop_domain,
            import_desktop_conversation_attachment,
            enter_or_restore_desktop_temporary_conversation,
            read_desktop_temporary_conversation,
            update_desktop_temporary_conversation,
            append_desktop_temporary_message,
            import_desktop_temporary_attachment,
            clear_desktop_temporary_conversation,
            remove_desktop_temporary_attachment,
            undo_desktop_domain,
            redo_desktop_domain,
            read_desktop_workbench_history,
            upsert_desktop_model_metadata,
            read_desktop_p6g_catalog,
            read_desktop_p6g_global_default,
            upsert_desktop_p6g_catalog_candidate,
            remove_desktop_p6g_catalog_candidate,
            read_desktop_p6g_selection,
            set_desktop_p6g_global_default,
            set_desktop_p6g_conversation_override,
            clear_desktop_p6g_conversation_override,
            evaluate_desktop_p6g_auto_route,
            inspect_p8_agent_runs,
            read_dual_path_status,
            read_p6e_temporary_maintenance_acceptance_status,
            run_p6e_temporary_maintenance_acceptance
        ])
        .run(tauri::generate_context!())
        .expect("tauri desktop startup failed");
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

    fn golden() -> Vec<u8> {
        fs::read("../../protocol/artifacts/nfai.exchange.v1.golden.nfai-exchange").expect("golden")
    }

    #[test]
    fn v2_owner_fidelity_golden_is_shared_and_rejects_lossy_or_locator_fields() {
        let mut exchange: Value = serde_json::from_str(include_str!("../../../protocol/fixtures/nfai.exchange.v2.golden.json")).unwrap();
        assert_eq!(validate_exchange_v2_ir(&exchange).unwrap(), "aaeafcfbcfdf1d36ab4c8484e5d6d3537b6d4a60abc7216b3aa4fd702b1c0ef8");
        exchange["knowledge"][0]["sourceEvidence"][0]["sourceReference"] = Value::String("content://forbidden".into());
        let semantic = semantic_hash(&exchange).unwrap(); exchange["export"]["semanticHash"] = Value::String(semantic);
        assert!(validate_exchange_v2_ir(&exchange).is_err());
        let mut lossy: Value = serde_json::from_str(include_str!("../../../protocol/fixtures/nfai.exchange.v2.golden.json")).unwrap();
        lossy["projects"][0].as_object_mut().unwrap().remove("instructionHistory");
        let semantic = semantic_hash(&lossy).unwrap(); lossy["export"]["semanticHash"] = Value::String(semantic);
        assert!(validate_exchange_v2_ir(&lossy).is_err());
    }

    #[test]
    fn v1_package_boundary_rejects_v2_owner_fidelity_ir_without_persistence() {
        let directory = tempdir().unwrap();
        let store = DesktopWorkspaceStore::open(directory.path().join("app-data")).unwrap();
        let exchange: Value = serde_json::from_str(include_str!("../../../protocol/fixtures/nfai.exchange.v2.golden.json")).unwrap();

        // v2 has no production package/staging owner yet.  It must not silently pass through the
        // v1 package writer/importer merely because its top-level collections look similar.
        assert!(package_exchange(&exchange, &BTreeMap::new()).is_err());
        assert!(store.list_workspaces().unwrap().is_empty());
        assert_eq!(store.connection().unwrap().query_row("SELECT COUNT(*) FROM import_journal", [], |row| row.get::<_, u64>(0)).unwrap(), 0);
    }

    #[test]
    fn chatgpt_export_parser_keeps_branch_order_roles_and_model_inert() {
        let bytes = br#"[{"id":"chatgpt-alpha","title":"Alpha","create_time":1700000000,"update_time":1700000001,"mapping":{"m-user":{"parent":null,"children":["m-assistant"],"message":{"author":{"role":"user"},"content":{"parts":["hello"]},"create_time":1700000000}},"m-assistant":{"parent":"m-user","children":[],"message":{"author":{"role":"assistant"},"content":{"parts":["world"]},"metadata":{"model_slug":"gpt-local"},"create_time":1700000001}}}}]"#;
        let parsed = parse_chatgpt_export(bytes).unwrap();
        let candidate = parsed.into_iter().next().unwrap().unwrap();
        assert_eq!(candidate.messages.len(), 2);
        assert_eq!(
            candidate.messages[1].parent_source_id.as_deref(),
            Some("m-user")
        );
        assert_eq!(
            candidate.messages[1].imported_model.as_deref(),
            Some("gpt-local")
        );
    }

    #[test]
    fn chatgpt_export_parser_isolates_unsafe_conversation_but_rejects_bad_package() {
        let bytes = br#"[{"id":"good","title":"Good","create_time":1,"update_time":2,"mapping":{"m":{"parent":null,"children":[],"message":{"author":{"role":"user"},"content":{"parts":["ok"]}}}}},{"id":"bad","title":"Bad","create_time":1,"update_time":2,"mapping":{"m":{"parent":null,"children":[],"message":{"author":{"role":"system"},"content":{"parts":["no"]}}}}}]"#;
        let parsed = parse_chatgpt_export(bytes).unwrap();
        assert!(parsed[0].is_ok());
        assert!(parsed[1].is_err());
        assert!(parse_chatgpt_export(br#"{"not":"array"}"#).is_err());
    }

    #[test]
    fn chatgpt_export_parser_rejects_duplicate_json_keys_before_mapping() {
        let duplicate = br#"[{"id":"one","id":"two","title":"Alpha","create_time":1,"update_time":2,"mapping":{}}]"#;
        assert!(parse_chatgpt_export(duplicate).is_err());
    }

    #[test]
    fn p6k_zip_inventory_keeps_only_safe_metadata_and_rejects_traversal() {
        let directory = tempdir().unwrap(); let safe = directory.path().join("safe.zip");
        { let file = fs::File::create(&safe).unwrap(); let mut writer = ZipWriter::new(file); writer.start_file("manifest.json", SimpleFileOptions::default()).unwrap(); writer.write_all(b"{}").unwrap(); writer.start_file("assets/image.png", SimpleFileOptions::default()).unwrap(); writer.write_all(b"pixels").unwrap(); writer.finish().unwrap(); }
        let inventory = p6k_inventory(&safe).unwrap(); assert_eq!(inventory.len(), 2); assert_eq!(inventory[0].3, "application/json");
        let traversal = directory.path().join("traversal.zip"); { let file = fs::File::create(&traversal).unwrap(); let mut writer = ZipWriter::new(file); writer.start_file("../escape.json", SimpleFileOptions::default()).unwrap(); writer.write_all(b"{}").unwrap(); writer.finish().unwrap(); }
        assert!(p6k_inventory(&traversal).is_err());
    }

    #[test]
    fn p6k_chatgpt_zip_normalizes_cross_file_parent_and_isolates_unresolved_fragment() {
        let (directory, store, imported) = imported_store(); let source = directory.path().join("fixture-cross-file.zip");
        {
            let file = fs::File::create(&source).unwrap(); let mut writer = ZipWriter::new(file);
            writer.start_file("conversations-1.json", SimpleFileOptions::default()).unwrap();
            writer.write_all(br#"[{"id":"fragment-root","title":"Root","create_time":1,"update_time":4,"mapping":{"node-root":{"parent":null,"message":{"author":{"role":"user"},"content":{"parts":["root"]},"create_time":1}}}}]"#).unwrap();
            writer.start_file("conversations-2.json", SimpleFileOptions::default()).unwrap();
            writer.write_all(br#"[{"id":"fragment-child","title":"Child","create_time":2,"update_time":4,"mapping":{"node-child":{"parent":"node-root","message":{"author":{"role":"assistant"},"content":{"parts":["child"]},"create_time":2}}}}]"#).unwrap();
            writer.start_file("conversations-3.json", SimpleFileOptions::default()).unwrap();
            writer.write_all(br#"[{"id":"fragment-invalid","title":"Invalid","create_time":3,"update_time":4,"mapping":{"node-invalid":{"parent":"outside-zip","message":{"author":{"role":"user"},"content":{"parts":["reject"]},"create_time":3}}}}]"#).unwrap();
            writer.finish().unwrap();
        }
        let task = store.stage_p6k_zip_import_selected(P6kZipImportSelectionArgs { workspace_id: imported.summary.id.clone(), provider: "CHATGPT".into(), selected_path: source.to_string_lossy().into_owned() }).unwrap();
        assert_eq!(task.status, "PARTIALLY_COMPLETED"); assert_eq!(task.imported_count, 1); assert_eq!(task.failed_count, 1); assert_eq!(task.skipped_count, 1);
        let conversation_id = task.items.iter().find_map(|item| item.conversation_id.clone()).unwrap();
        let stored: String = store.connection().unwrap().query_row("SELECT storage_key FROM p6k_zip_import_tasks WHERE id=?1", [&task.id], |row| row.get(0)).unwrap();
        assert_eq!(store.retry_p6k_zip_import_task(&task.id).unwrap().imported_count, 1);
        assert_eq!(store.workspace_projection(&imported.summary.id).unwrap().exchange["conversations"].as_array().unwrap().iter().filter(|value| value["id"] == conversation_id).count(), 1);
        drop(store);
        let reopened = DesktopWorkspaceStore::open(directory.path().join("app-data")).unwrap();
        assert_eq!(reopened.read_latest_p6k_zip_import_task().unwrap().unwrap().id, task.id);
        reopened.delete_p6k_zip_import_batch(&task.id).unwrap();
        assert!(reopened.workspace_projection(&imported.summary.id).unwrap().exchange["conversations"].as_array().unwrap().iter().find(|value| value["id"] == conversation_id).unwrap()["deleted"].as_bool().unwrap());
        assert!(!reopened.root.join(stored).exists());
    }

    #[test]
    fn p6k_claude_versioned_limit_accepts_282_conversations_without_widening_p6i() {
        let conversations = (0..282).map(|index| json!({"uuid":format!("c-{index}"),"name":"Fixture","created_at":"2026-08-15T08:00:00Z","updated_at":"2026-08-15T08:01:00Z","chat_messages":[{"uuid":format!("m-{index}"),"sender":"human","created_at":"2026-08-15T08:00:00Z","text":"fixture"}]})).collect::<Vec<_>>();
        let bytes = serde_json::to_vec(&conversations).unwrap();
        assert!(parse_claude_export_with_limit(&bytes, CLAUDE_EXPORT_MAX_BYTES).is_err());
        let parsed = parse_claude_export_with_limits(&bytes, P6K_CLAUDE_EXPORT_MAX_BYTES, P6K_CLAUDE_EXPORT_MAX_CONVERSATIONS, P6K_CLAUDE_EXPORT_MAX_MESSAGES_PER_CONVERSATION).unwrap();
        assert_eq!(parsed.len(), 282); assert!(parsed.iter().all(Result::is_ok));
    }

    #[test]
    fn p6k_chatgpt_zip_direct_commit_is_private_idempotent_and_batch_soft_deletable() {
        let (directory, store, imported) = imported_store(); let source = directory.path().join("fixture-chatgpt.zip");
        { let file = fs::File::create(&source).unwrap(); let mut writer = ZipWriter::new(file); writer.start_file("conversations.json", SimpleFileOptions::default()).unwrap(); writer.write_all(br#"[{"id":"p6k-alpha","title":"P6K Alpha","create_time":1,"update_time":2,"mapping":{"m-user":{"parent":null,"message":{"author":{"role":"user"},"content":{"parts":["fixture user"]}}},"m-assistant":{"parent":"m-user","message":{"author":{"role":"assistant"},"content":{"parts":["fixture assistant"]}}}}},{"id":"p6k-rejected","title":"P6K Rejected","create_time":1,"update_time":2,"mapping":{"m":{"parent":null,"message":{"author":{"role":"system"},"content":{"parts":["must reject"]}}}}}]"#).unwrap(); writer.start_file("assets/unmapped.png", SimpleFileOptions::default()).unwrap(); writer.write_all(b"pixels").unwrap(); writer.finish().unwrap(); }
        let task = store.stage_p6k_zip_import_selected(P6kZipImportSelectionArgs { workspace_id: imported.summary.id.clone(), provider: "CHATGPT".into(), selected_path: source.to_string_lossy().into_owned() }).unwrap();
        assert_eq!(task.status, "PARTIALLY_COMPLETED"); assert_eq!(task.imported_count, 1); assert_eq!(task.failed_count, 1); assert_eq!(task.unmapped_asset_count, 1); assert_eq!(task.profile_status, "NO_SAFE_PROFILE_FIELDS"); assert_eq!(task.profile_mapped_field_count, 0);
        let stored: String = store.connection().unwrap().query_row("SELECT storage_key FROM p6k_zip_import_tasks WHERE id=?1", [&task.id], |row| row.get(0)).unwrap(); assert!(!stored.contains(source.to_str().unwrap())); assert!(store.root.join(&stored).is_file());
        let conversation_id = task.items[0].conversation_id.clone().unwrap(); assert_eq!(store.skip_p6k_zip_import_failures(&task.id).unwrap().status, "COMPLETED"); store.retry_p6k_zip_import_task(&task.id).unwrap(); assert_eq!(store.workspace_projection(&imported.summary.id).unwrap().exchange["conversations"].as_array().unwrap().iter().filter(|value| value["id"] == conversation_id).count(), 1); assert_eq!(store.connection().unwrap().query_row("SELECT COUNT(*) FROM desktop_conversation_attachments", [], |row| row.get::<_, i64>(0)).unwrap(), 0); drop(store);
        let reopened = DesktopWorkspaceStore::open(directory.path().join("app-data")).unwrap(); assert_eq!(reopened.read_latest_p6k_zip_import_task().unwrap().unwrap().id, task.id); reopened.delete_p6k_zip_import_batch(&task.id).unwrap(); assert!(reopened.workspace_projection(&imported.summary.id).unwrap().exchange["conversations"].as_array().unwrap().iter().find(|value| value["id"] == conversation_id).unwrap()["deleted"].as_bool().unwrap()); assert!(!reopened.root.join(stored).exists());
    }

    #[test]
    fn p6k_manual_asset_link_uses_explicit_message_receipts_for_image_video_and_pdf() {
        let (directory, store, imported) = imported_store(); let source = directory.path().join("fixture-manual-media.zip");
        { let file=fs::File::create(&source).unwrap(); let mut writer=ZipWriter::new(file); writer.start_file("conversations.json",SimpleFileOptions::default()).unwrap(); writer.write_all(br#"[{"id":"manual-media","title":"Manual media","create_time":1,"update_time":2,"mapping":{"user":{"parent":null,"message":{"author":{"role":"user"},"content":{"parts":["fixture"]}}},"assistant":{"parent":"user","message":{"author":{"role":"assistant"},"content":{"parts":["fixture"]}}}}}]"#).unwrap(); writer.start_file("assets/image.png",SimpleFileOptions::default()).unwrap(); writer.write_all(b"\x89PNG\r\n\x1a\nfixture").unwrap(); writer.start_file("assets/video.mp4",SimpleFileOptions::default()).unwrap(); writer.write_all(b"\0\0\0\x20ftypisom\0\0\x02\0isomiso2").unwrap(); writer.start_file("assets/document.pdf",SimpleFileOptions::default()).unwrap(); writer.write_all(b"%PDF-1.4\nfixture").unwrap(); writer.finish().unwrap(); }
        let task=store.stage_p6k_zip_import_selected(P6kZipImportSelectionArgs{workspace_id:imported.summary.id.clone(),provider:"CHATGPT".into(),selected_path:source.to_string_lossy().into_owned()}).unwrap(); assert_eq!(task.unmapped_asset_count,3); assert!(task.manual_assets.iter().all(|asset| asset.status=="UNMAPPED_REJECTED"));
        let conversation_id=task.items[0].conversation_id.clone().unwrap(); let user_message=store.workspace_projection(&imported.summary.id).unwrap().exchange["conversations"].as_array().unwrap().iter().find(|value|value["id"]==conversation_id).unwrap()["messages"].as_array().unwrap().iter().find(|message|message["role"]=="user").unwrap()["id"].as_str().unwrap().to_owned();
        for ordinal in 0..3 { store.link_p6k_zip_manual_asset(P6kManualAssetLinkArgs{task_id:task.id.clone(),asset_ordinal:ordinal,workspace_id:imported.summary.id.clone(),conversation_id:conversation_id.clone(),message_id:user_message.clone()}).unwrap(); }
        let replay=store.link_p6k_zip_manual_asset(P6kManualAssetLinkArgs{task_id:task.id.clone(),asset_ordinal:0,workspace_id:imported.summary.id.clone(),conversation_id:conversation_id.clone(),message_id:user_message.clone()}).unwrap(); assert_eq!(replay.manual_assets.iter().filter(|asset|asset.status=="MANUAL_LINKED").count(),3);
        let linked_projection=store.workspace_projection(&imported.summary.id).unwrap(); let target=linked_projection.exchange["conversations"].as_array().unwrap().iter().find(|value|value["id"]==conversation_id).unwrap()["messages"].as_array().unwrap().iter().find(|message|message["id"]==user_message).unwrap(); assert_eq!(target["blocks"].as_array().unwrap().iter().filter(|block|block["kind"]=="ASSET_REF").count(),3); assert_eq!(store.connection().unwrap().query_row("SELECT COUNT(*) FROM p6k_zip_asset_link_receipts WHERE task_id=?1",[&task.id],|row|row.get::<_,i64>(0)).unwrap(),3);
        let stored:String=store.connection().unwrap().query_row("SELECT storage_key FROM p6k_zip_import_tasks WHERE id=?1",[&task.id],|row|row.get(0)).unwrap(); drop(store); let reopened=DesktopWorkspaceStore::open(directory.path().join("app-data")).unwrap(); reopened.delete_p6k_zip_import_batch(&task.id).unwrap(); assert!(!reopened.root.join(stored).exists()); assert!(reopened.workspace_projection(&imported.summary.id).unwrap().exchange["conversations"].as_array().unwrap().iter().find(|value|value["id"]==conversation_id).unwrap()["deleted"].as_bool().unwrap());
    }

    #[test]
    fn p6k_versioned_profile_fixture_commits_replays_reopens_and_revokes_with_its_batch() {
        let (directory, store, imported) = imported_store(); let source = directory.path().join("fixture-profile.zip");
        { let file = fs::File::create(&source).unwrap(); let mut writer = ZipWriter::new(file); writer.start_file("conversations.json", SimpleFileOptions::default()).unwrap(); writer.write_all(br#"[{"id":"profile-conversation","title":"Fixture","create_time":1,"update_time":2,"mapping":{"m":{"parent":null,"message":{"author":{"role":"user"},"content":{"parts":["fixture"]}}}}}]"#).unwrap(); writer.start_file("profile-personalization-v1.json", SimpleFileOptions::default()).unwrap(); writer.write_all(br#"{"format":"nfai.third-party-profile-personalization","version":1,"personalization":{"displayName":"Fixture","language":"zh-CN","notificationsEnabled":true}}"#).unwrap(); writer.finish().unwrap(); }
        let task = store.stage_p6k_zip_import_selected(P6kZipImportSelectionArgs { workspace_id: imported.summary.id.clone(), provider: "CHATGPT".into(), selected_path: source.to_string_lossy().into_owned() }).unwrap();
        assert_eq!(task.profile_status, "OWNER_COMMITTED"); assert_eq!(task.profile_mapped_field_count, 3);
        assert_eq!(store.read_p6k_profile_personalization().unwrap().unwrap().display_name.as_deref(), Some("Fixture"));
        assert_eq!(store.retry_p6k_zip_import_task(&task.id).unwrap().profile_status, "OWNER_COMMITTED"); drop(store);
        let reopened = DesktopWorkspaceStore::open(directory.path().join("app-data")).unwrap(); assert_eq!(reopened.read_p6k_profile_personalization().unwrap().unwrap().language.as_deref(), Some("zh-CN"));
        reopened.delete_p6k_zip_import_batch(&task.id).unwrap(); assert!(reopened.read_p6k_profile_personalization().unwrap().is_none());
    }

    #[test]
    fn p6k_claude_zip_accepts_only_one_inferable_missing_root_parent() {
        let (directory, store, imported) = imported_store(); let source = directory.path().join("fixture-claude.zip");
        { let file = fs::File::create(&source).unwrap(); let mut writer = ZipWriter::new(file); writer.start_file("conversations.json", SimpleFileOptions::default()).unwrap(); writer.write_all(br#"[{"uuid":"p6k-claude","name":"P6K Claude","created_at":"2026-08-15T08:00:00Z","updated_at":"2026-08-15T08:01:00Z","chat_messages":[{"uuid":"m-one","sender":"human","created_at":"2026-08-15T08:00:00Z","text":"fixture Claude","parent_message_uuid":"external-root"}]}]"#).unwrap(); writer.finish().unwrap(); }
        let task = store.stage_p6k_zip_import_selected(P6kZipImportSelectionArgs { workspace_id: imported.summary.id.clone(), provider: "CLAUDE".into(), selected_path: source.to_string_lossy().into_owned() }).unwrap(); assert_eq!(task.status, "COMPLETED"); let conversation_id = task.items[0].conversation_id.clone().unwrap(); let message = store.workspace_projection(&imported.summary.id).unwrap().exchange["conversations"].as_array().unwrap().iter().find(|value| value["id"] == conversation_id).unwrap()["messages"][0].clone(); assert!(message["parentId"].is_null());
    }

    #[test]
    fn chatgpt_selected_file_is_privately_copied_and_task_reopens_without_source_path() {
        let directory = tempdir().unwrap();
        let source = directory.path().join("conversations.json");
        fs::write(&source, br#"[{"id":"alpha","title":"Alpha","create_time":1,"update_time":2,"mapping":{"m":{"parent":null,"children":[],"message":{"author":{"role":"user"},"content":{"parts":["inert"]}}}}}]"#).unwrap();
        let root = directory.path().join("app-data");
        let task = DesktopWorkspaceStore::open(root.clone())
            .unwrap()
            .stage_chatgpt_export_selected(source.to_str().unwrap())
            .unwrap();
        assert_eq!(task.candidates.len(), 1);
        let reopened = DesktopWorkspaceStore::open(root).unwrap();
        let connection = reopened.connection().unwrap();
        let stored: (String, String) = connection
            .query_row(
                "SELECT storage_key,display_name FROM chatgpt_import_tasks WHERE id=?1",
                [&task.id],
                |row| Ok((row.get(0)?, row.get(1)?)),
            )
            .unwrap();
        assert!(!stored.0.contains(source.to_str().unwrap()));
        assert_eq!(stored.1, "conversations.json");
        assert!(reopened.root.join(stored.0).is_file());
    }

    #[test]
    fn chatgpt_selected_file_allows_a_user_renamed_json_without_retaining_its_path() {
        let directory = tempdir().unwrap();
        let source = directory.path().join("p6h-visible-conversations.json");
        fs::write(&source, br#"[{"id":"alpha","title":"Alpha","create_time":1,"update_time":2,"mapping":{"m":{"parent":null,"children":[],"message":{"author":{"role":"user"},"content":{"parts":["inert"]}}}}}]"#).unwrap();
        let root = directory.path().join("app-data");
        let task = DesktopWorkspaceStore::open(root.clone())
            .unwrap()
            .stage_chatgpt_export_selected(source.to_str().unwrap())
            .unwrap();
        let reopened = DesktopWorkspaceStore::open(root).unwrap();
        let stored: (String, String) = reopened
            .connection()
            .unwrap()
            .query_row(
                "SELECT storage_key,display_name FROM chatgpt_import_tasks WHERE id=?1",
                [&task.id],
                |row| Ok((row.get(0)?, row.get(1)?)),
            )
            .unwrap();
        assert_eq!(stored.1, "p6h-visible-conversations.json");
        assert!(!stored.0.contains(source.to_str().unwrap()));
        assert!(reopened.root.join(stored.0).is_file());
    }

    #[test]
    fn json_only_chatgpt_and_claude_entrypoints_reject_zip_before_any_private_copy() {
        let directory = tempdir().unwrap();
        let source = directory.path().join("official-export.zip");
        fs::write(&source, b"PK\x03\x04not-a-user-package").unwrap();
        let store = DesktopWorkspaceStore::open(directory.path().join("app-data")).unwrap();
        assert!(store.stage_chatgpt_export_selected(source.to_str().unwrap()).is_err());
        assert!(store.stage_claude_export_selected(source.to_str().unwrap()).is_err());
        assert!(!store.root.join("chatgpt-import-assets").exists());
        assert!(!store.root.join("claude-import-assets").exists());
    }

    #[test]
    fn chatgpt_confirm_skip_is_atomic_idempotent_and_survives_reopen() {
        let (directory, store, imported) = imported_store();
        let source = directory.path().join("conversations.json");
        fs::write(&source, br#"[{"id":"alpha","title":"Alpha","create_time":1,"update_time":2,"mapping":{"u":{"parent":null,"children":["a"],"message":{"author":{"role":"user"},"content":{"parts":["hello"]}}},"a":{"parent":"u","children":[],"message":{"author":{"role":"assistant"},"content":{"parts":["world"]},"metadata":{"model_slug":"inert-model"}}}}},{"id":"skip","title":"Skip","create_time":1,"update_time":2,"mapping":{"s":{"parent":null,"children":[],"message":{"author":{"role":"user"},"content":{"parts":["skip"]}}}}}]"#).unwrap();
        let task = store
            .stage_chatgpt_export_selected(source.to_str().unwrap())
            .unwrap();
        let confirmed = store
            .confirm_chatgpt_import_item(ChatGptImportItemActionArgs {
                workspace_id: imported.summary.id.clone(),
                task_id: task.id.clone(),
                item_id: "chatgpt-item-0".into(),
            })
            .unwrap();
        store
            .skip_chatgpt_import_item(ChatGptImportItemActionArgs {
                workspace_id: imported.summary.id.clone(),
                task_id: task.id.clone(),
                item_id: "chatgpt-item-1".into(),
            })
            .unwrap();
        let replayed = store
            .confirm_chatgpt_import_item(ChatGptImportItemActionArgs {
                workspace_id: imported.summary.id.clone(),
                task_id: task.id.clone(),
                item_id: "chatgpt-item-0".into(),
            })
            .unwrap();
        assert_eq!(confirmed, replayed);
        drop(store);
        let reopened = DesktopWorkspaceStore::open(directory.path().join("app-data")).unwrap();
        let projection = reopened.workspace_projection(&imported.summary.id).unwrap();
        let conversation = projection.exchange["conversations"]
            .as_array()
            .unwrap()
            .iter()
            .find(|value| value["id"] == confirmed)
            .unwrap();
        assert_eq!(conversation["messages"].as_array().unwrap().len(), 2);
        let connection = reopened.connection().unwrap();
        assert_eq!(
            connection
                .query_row(
                    "SELECT status FROM chatgpt_import_tasks WHERE id=?1",
                    [&task.id],
                    |row| row.get::<_, String>(0)
                )
                .unwrap(),
            "COMPLETED"
        );
        assert_eq!(
            connection
                .query_row(
                    "SELECT COUNT(*) FROM chatgpt_import_provenance WHERE conversation_id=?1",
                    [&confirmed],
                    |row| row.get::<_, i64>(0)
                )
                .unwrap(),
            1
        );
    }

    #[test]
    fn migration_reopen_and_strict_golden_preflight() {
        let directory = tempdir().unwrap();
        let store = DesktopWorkspaceStore::open(directory.path().join("app-data")).unwrap();
        let package = preflight_package(golden()).unwrap();
        assert_eq!(
            package.receipt.semantic_hash,
            "ad41c1ee6aa64b9e2f218034dbefccc333c43fb923b874b12ff51ce5972d4031"
        );
        drop(store);
        let reopened = DesktopWorkspaceStore::open(directory.path().join("app-data")).unwrap();
        assert!(reopened.list_workspaces().unwrap().is_empty());
        assert_eq!(
            reopened
                .connection()
                .unwrap()
                .pragma_query_value(None, "user_version", |row| row.get::<_, u32>(0))
                .unwrap(),
            // P6-K then appends its isolated ZIP recovery ledger, K6 profile owner and K8 asset receipts.
            20
        );
    }

    #[test]
    fn p6f2d_mp4_magic_accepts_iso_bmff_ftyp() {
        let bytes = b"\0\0\0\x20ftypisom\0\0\x02\0isomiso2";
        assert_eq!(
            desktop_attachment_kind(bytes, Some("mp4")),
            Some(("video/mp4", ".mp4"))
        );
        assert_eq!(
            desktop_attachment_kind(bytes, Some("renamed")),
            Some(("video/mp4", ".mp4"))
        );
        assert_eq!(
            desktop_attachment_kind(b"\0\0\0\x18ftypM4A \0\0\0\0M4A ", Some("mp4")),
            None
        );
    }

    #[test]
    fn p6f2d_video_owner_persists_a_nonzero_workspace_scoped_position() {
        let (_directory, store, imported) = imported_store();
        // A minimal ISO-BMFF stream sufficient for the bounded local metadata reader:
        // ftyp + moov/mvhd(version 0, timescale 1000, duration 6000ms).
        let mut source = b"\0\0\0\x18ftypisom\0\0\x02\0isomiso2".to_vec();
        let mvhd_payload = [
            0, 0, 0, 0, // version and flags
            0, 0, 0, 0, // creation time
            0, 0, 0, 0, // modification time
            0, 0, 3, 232, // timescale = 1000
            0, 0, 23, 112, // duration = 6000
        ];
        let mut mvhd = (8 + mvhd_payload.len() as u32).to_be_bytes().to_vec();
        mvhd.extend_from_slice(b"mvhd");
        mvhd.extend_from_slice(&mvhd_payload);
        let mut moov = (8 + mvhd.len() as u32).to_be_bytes().to_vec();
        moov.extend_from_slice(b"moov");
        moov.extend_from_slice(&mvhd);
        source.extend_from_slice(&moov);
        let digest = sha256(&source);
        fs::write(store.root.join("assets").join(&digest), &source).unwrap();
        store.connection().unwrap().execute(
            "INSERT INTO desktop_conversation_attachments(workspace_id,attachment_id,mime_type,display_name,byte_count,sha256,created_at) VALUES(?1,?2,?3,?4,?5,?6,?7)",
            params![imported.summary.id, "attachment-video", "video/mp4", "local.mp4", source.len() as u64, digest, local_now()],
        ).unwrap();

        let saved = store
            .video_preview(&DesktopVideoPreviewArgs {
                workspace_id: imported.summary.id.clone(),
                attachment_id: "attachment-video".into(),
                position_millis: Some(1237),
            })
            .unwrap();
        assert_eq!(saved.position_millis, 1237);
        assert!(saved.data_url.starts_with("data:video/mp4;base64,"));

        let reopened = store
            .video_preview(&DesktopVideoPreviewArgs {
                workspace_id: imported.summary.id.clone(),
                attachment_id: "attachment-video".into(),
                position_millis: None,
            })
            .unwrap();
        assert_eq!(reopened.position_millis, 1237);
        assert!(store
            .video_preview(&DesktopVideoPreviewArgs {
                workspace_id: "workspace-not-owner".into(),
                attachment_id: "attachment-video".into(),
                position_millis: Some(1237),
            })
            .is_err());
    }

    #[test]
    fn p6f2e_audio_and_text_previews_are_private_bounded_and_owner_checked() {
        let (_directory, store, imported) = imported_store();

        let audio = b"RIFF\x10\0\0\0WAVEfmt ";
        assert_eq!(
            desktop_attachment_kind(audio, Some("wav")),
            Some(("audio/wav", ".wav"))
        );
        let audio_hash = sha256(audio);
        fs::write(store.root.join("assets").join(&audio_hash), audio).unwrap();
        store.connection().unwrap().execute(
            "INSERT INTO desktop_conversation_attachments(workspace_id,attachment_id,mime_type,display_name,byte_count,sha256,created_at) VALUES(?1,?2,?3,?4,?5,?6,?7)",
            params![imported.summary.id, "attachment-audio", "audio/wav", "local.wav", audio.len() as u64, audio_hash, local_now()],
        ).unwrap();
        let saved_audio = store
            .audio_preview(&DesktopAudioPreviewArgs {
                workspace_id: imported.summary.id.clone(),
                attachment_id: "attachment-audio".into(),
                position_millis: Some(137),
            })
            .unwrap();
        assert_eq!(saved_audio.position_millis, 137);
        assert!(saved_audio.data_url.starts_with("data:audio/wav;base64,"));
        assert_eq!(
            store
                .audio_preview(&DesktopAudioPreviewArgs {
                    workspace_id: imported.summary.id.clone(),
                    attachment_id: "attachment-audio".into(),
                    position_millis: None,
                })
                .unwrap()
                .position_millis,
            137
        );
        assert!(store
            .audio_preview(&DesktopAudioPreviewArgs {
                workspace_id: "workspace-not-owner".into(),
                attachment_id: "attachment-audio".into(),
                position_millis: None,
            })
            .is_err());
        fs::remove_file(store.root.join("assets").join(&audio_hash)).unwrap();
        assert!(store
            .audio_preview(&DesktopAudioPreviewArgs {
                workspace_id: imported.summary.id.clone(),
                attachment_id: "attachment-audio".into(),
                position_millis: None,
            })
            .unwrap_err()
            .contains("缺失"));

        let mut text = "\u{feff}".as_bytes().to_vec();
        text.extend(std::iter::repeat_n(b'x', MAX_INERT_TEXT_PREVIEW_BYTES + 9));
        let text_hash = sha256(&text);
        fs::write(store.root.join("assets").join(&text_hash), &text).unwrap();
        store.connection().unwrap().execute(
            "INSERT INTO desktop_conversation_attachments(workspace_id,attachment_id,mime_type,display_name,byte_count,sha256,created_at) VALUES(?1,?2,?3,?4,?5,?6,?7)",
            params![imported.summary.id, "attachment-text", "text/markdown", "local.md", text.len() as u64, text_hash, local_now()],
        ).unwrap();
        let preview = store
            .text_preview(&DesktopTextPreviewArgs {
                workspace_id: imported.summary.id.clone(),
                attachment_id: "attachment-text".into(),
            })
            .unwrap();
        assert!(preview.truncated);
        assert_eq!(preview.text.len(), MAX_INERT_TEXT_PREVIEW_BYTES - 3);
        assert!(!preview.text.starts_with('\u{feff}'));
        fs::write(store.root.join("assets").join(&text_hash), b"tampered").unwrap();
        assert!(store
            .text_preview(&DesktopTextPreviewArgs {
                workspace_id: imported.summary.id.clone(),
                attachment_id: "attachment-text".into(),
            })
            .unwrap_err()
            .contains("校验不一致"));

        let malformed = [0xc3_u8];
        let malformed_hash = sha256(&malformed);
        fs::write(store.root.join("assets").join(&malformed_hash), malformed).unwrap();
        store.connection().unwrap().execute(
            "INSERT INTO desktop_conversation_attachments(workspace_id,attachment_id,mime_type,display_name,byte_count,sha256,created_at) VALUES(?1,?2,?3,?4,?5,?6,?7)",
            params![imported.summary.id, "attachment-malformed", "text/plain", "bad.txt", 1u64, malformed_hash, local_now()],
        ).unwrap();
        assert!(store
            .text_preview(&DesktopTextPreviewArgs {
                workspace_id: imported.summary.id,
                attachment_id: "attachment-malformed".into(),
            })
            .unwrap_err()
            .contains("UTF-8"));
    }

    #[test]
    fn image_preview_reads_only_workspace_owned_private_copy_and_rejects_missing_asset() {
        let (_directory, store, imported) = imported_store();
        let image = image::DynamicImage::ImageRgba8(
            image::RgbaImage::from_raw(2, 1, vec![0x12, 0x34, 0x56, 0xff, 0x65, 0x43, 0x21, 0xff])
                .unwrap(),
        );
        let mut encoded = Cursor::new(Vec::new());
        image.write_to(&mut encoded, ImageFormat::Png).unwrap();
        let source = encoded.into_inner();
        let digest = sha256(&source);
        fs::write(store.root.join("assets").join(&digest), &source).unwrap();
        store.connection().unwrap().execute(
            "INSERT INTO desktop_conversation_attachments(workspace_id,attachment_id,mime_type,display_name,byte_count,sha256,created_at) VALUES(?1,?2,?3,?4,?5,?6,?7)",
            params![imported.summary.id, "attachment-image", "image/png", "local.png", source.len() as u64, digest, local_now()],
        ).unwrap();

        let thumbnail = store
            .image_preview(&DesktopImagePreviewArgs {
                workspace_id: imported.summary.id.clone(),
                attachment_id: "attachment-image".into(),
                full_size: false,
            })
            .unwrap();
        assert!(thumbnail.is_thumbnail);
        assert_eq!(thumbnail.mime_type, "image/png");
        assert_eq!((thumbnail.width, thumbnail.height), (2, 1));
        assert!(thumbnail.data_url.starts_with("data:image/png;base64,"));
        assert!(!thumbnail.data_url.contains("assets/"));

        let original = store
            .image_preview(&DesktopImagePreviewArgs {
                workspace_id: imported.summary.id.clone(),
                attachment_id: "attachment-image".into(),
                full_size: true,
            })
            .unwrap();
        assert!(!original.is_thumbnail);
        assert_eq!(
            original.data_url,
            format!("data:image/png;base64,{}", BASE64.encode(source))
        );
        fs::remove_file(store.root.join("assets").join(digest)).unwrap();
        assert!(store
            .image_preview(&DesktopImagePreviewArgs {
                workspace_id: imported.summary.id,
                attachment_id: "attachment-image".into(),
                full_size: false,
            })
            .unwrap_err()
            .contains("缺失"));
    }

    #[test]
    fn attachment_maintenance_uses_owner_metadata_clock_and_is_idempotent() {
        let (directory, store, imported) = imported_store();
        let now = system_now_millis();
        let referenced = sha256(b"referenced attachment");
        let fresh = sha256(b"fresh orphan");
        let expired = sha256(b"expired orphan");
        let staging_hash = sha256(b"interrupted staging");
        let assets = store.root.join("assets");
        let staging = store.root.join("staging");
        for (hash, bytes) in [
            (&referenced, b"referenced attachment".as_slice()),
            (&fresh, b"fresh orphan".as_slice()),
            (&expired, b"expired orphan".as_slice()),
        ] {
            fs::write(assets.join(hash), bytes).unwrap();
        }
        let staging_name = "attachment-staging-interrupted.pending";
        fs::write(staging.join(staging_name), b"interrupted staging").unwrap();
        let connection = store.connection().unwrap();
        for (hash, bytes, last_unref) in [
            (&referenced, b"referenced attachment".as_slice(), now),
            (&fresh, b"fresh orphan".as_slice(), now),
            (
                &expired,
                b"expired orphan".as_slice(),
                now - ATTACHMENT_RETENTION_MILLIS,
            ),
        ] {
            connection.execute("INSERT INTO desktop_attachment_assets(sha256,byte_count,created_at_ms,last_referenced_at_ms,last_unreferenced_at_ms,reference_count) VALUES(?1,?2,?3,NULL,?4,0)", params![hash, bytes.len() as u64, now, last_unref]).unwrap();
        }
        connection.execute("INSERT INTO desktop_attachment_staging(staging_id,staging_name,sha256,byte_count,created_at_ms) VALUES(?1,?2,?3,?4,?5)", params!["attachment-staging-interrupted", staging_name, staging_hash, 19u64, now - ATTACHMENT_RETENTION_MILLIS]).unwrap();
        let mut exchange = imported.exchange.clone();
        exchange["conversations"] = json!([{
            "messages": [{"blocks": [{"kind":"ASSET_REF","asset":{"id":"attachment-referenced","sha256":referenced,"entry":format!("assets/{referenced}")}}]}]
        }]);
        connection
            .execute(
                "UPDATE workspace_exchange SET exchange_json=?1 WHERE workspace_id=?2",
                params![canonical_json(&exchange).unwrap(), imported.summary.id],
            )
            .unwrap();
        let staging_metadata: String = connection
            .query_row(
                "SELECT staging_name || sha256 FROM desktop_attachment_staging WHERE staging_id=?1",
                ["attachment-staging-interrupted"],
                |row| row.get(0),
            )
            .unwrap();
        assert!(!staging_metadata.contains('/'));
        drop(connection);

        let first = store.run_attachment_maintenance_at(now).unwrap();
        assert_eq!(first.referenced_retained, 1);
        assert!(first.fresh_orphans_retained >= 1);
        assert_eq!(first.expired_orphans_removed, 1);
        assert_eq!(first.expired_staging_removed, 1);
        assert!(assets.join(&referenced).exists());
        assert!(assets.join(&fresh).exists());
        assert!(!assets.join(&expired).exists());
        assert!(!staging.join(staging_name).exists());

        // This is the storage-owner observation of the final reference being removed.  No file
        // timestamp is touched; the first zero-reference maintenance records the exact business
        // time, then 23:59 is retained and 24h is eligible.
        exchange["conversations"] = json!([]);
        store
            .connection()
            .unwrap()
            .execute(
                "UPDATE workspace_exchange SET exchange_json=?1 WHERE workspace_id=?2",
                params![canonical_json(&exchange).unwrap(), imported.summary.id],
            )
            .unwrap();
        let released = store.run_attachment_maintenance_at(now).unwrap();
        assert!(released.fresh_orphans_retained >= 2);
        let before_deadline = store
            .run_attachment_maintenance_at(now + ATTACHMENT_RETENTION_MILLIS - 1)
            .unwrap();
        assert_eq!(before_deadline.expired_orphans_removed, 0);
        assert!(assets.join(&referenced).exists());
        let at_deadline = store
            .run_attachment_maintenance_at(now + ATTACHMENT_RETENTION_MILLIS)
            .unwrap();
        assert!(at_deadline.expired_orphans_removed >= 2);
        assert!(!assets.join(&referenced).exists());
        assert!(!assets.join(&fresh).exists());
        assert_eq!(
            store
                .run_attachment_maintenance_at(now + ATTACHMENT_RETENTION_MILLIS)
                .unwrap(),
            AttachmentMaintenanceReceipt {
                referenced_retained: 0,
                fresh_orphans_retained: 0,
                expired_orphans_removed: 0,
                expired_staging_removed: 0
            }
        );

        drop(store);
        let reopened = DesktopWorkspaceStore::open(directory.path().join("app-data")).unwrap();
        assert_eq!(
            reopened
                .connection()
                .unwrap()
                .query_row(
                    "SELECT COUNT(*) FROM desktop_attachment_assets",
                    [],
                    |row| row.get::<_, u64>(0)
                )
                .unwrap(),
            0
        );
    }

    #[test]
    fn import_is_atomic_and_export_roundtrips_semantics() {
        let directory = tempdir().unwrap();
        let store = DesktopWorkspaceStore::open(directory.path().join("app-data")).unwrap();
        let source = directory.path().join("golden.nfai-exchange");
        fs::write(&source, golden()).unwrap();
        let receipt = store.stage_selected_file(source.to_str().unwrap()).unwrap();
        let imported = store
            .import_staged(ImportArgs {
                staging_id: receipt.staging_id,
                workspace_title: "导入的工作区".into(),
            })
            .unwrap();
        assert_eq!(imported.summary.project_count, 1);
        assert!(store
            .import_staged(ImportArgs {
                staging_id: receipt.package_hash,
                workspace_title: "重复".into()
            })
            .is_err());
        let output = directory.path().join("roundtrip.nfai-exchange");
        let export = store
            .export_workspace_to_path(&imported.summary.id, output.to_str().unwrap())
            .unwrap();
        assert_eq!(export.semantic_hash, imported.summary.semantic_hash);
        assert_eq!(
            preflight_package(fs::read(output).unwrap())
                .unwrap()
                .exchange,
            imported.exchange
        );
    }

    #[test]
    fn p6f_branch_from_message_is_typed_idempotent_and_survives_reopen() {
        let (directory, store, imported) = imported_store();
        let source = imported.exchange["conversations"][0].clone();
        let source_id = source["id"].as_str().unwrap().to_owned();
        let source_revision = source["revision"].as_u64().unwrap();
        let source_message_id = source["messages"][0]["id"].as_str().unwrap().to_owned();
        let args = DomainMutationArgs {
            intent_id: "p6f-message-branch-intent".into(),
            workspace_id: imported.summary.id.clone(),
            entity: "conversation".into(),
            action: "branchFromMessage".into(),
            object_id: Some(source_id.clone()),
            expected_revision: Some(source_revision),
            fields: json!({"messageId":source_message_id}),
        };
        let first = store.mutate_domain(args).unwrap();
        let replay = store
            .mutate_domain(DomainMutationArgs {
                intent_id: "p6f-message-branch-intent".into(),
                workspace_id: imported.summary.id.clone(),
                entity: "conversation".into(),
                action: "branchFromMessage".into(),
                object_id: Some(source_id),
                expected_revision: Some(source_revision),
                fields: json!({"messageId":source_message_id}),
            })
            .unwrap();
        assert!(replay.replayed);
        assert_eq!(first.object_id, replay.object_id);
        let projection = store.workspace_projection(&imported.summary.id).unwrap();
        let branch = projection.exchange["conversations"]
            .as_array()
            .unwrap()
            .iter()
            .find(|conversation| conversation["id"] == first.object_id)
            .unwrap();
        assert_eq!(
            branch["branchProvenance"]["sourceConversationId"],
            imported.exchange["conversations"][0]["id"]
        );
        assert_eq!(
            branch["branchProvenance"]["kind"],
            "LOCAL_MESSAGE_TREE_PREFIX"
        );
        assert_eq!(branch["messages"].as_array().unwrap().len(), 1);
        drop(store);
        let reopened = DesktopWorkspaceStore::open(directory.path().join("app-data")).unwrap();
        assert!(reopened
            .workspace_projection(&imported.summary.id)
            .unwrap()
            .exchange["conversations"]
            .as_array()
            .unwrap()
            .iter()
            .any(|conversation| conversation["id"] == first.object_id));
    }

    #[test]
    fn rejects_corrupt_and_duplicate_ids_without_workspace() {
        let directory = tempdir().unwrap();
        let store = DesktopWorkspaceStore::open(directory.path().join("app-data")).unwrap();
        let mut corrupt = golden();
        corrupt[48] ^= 1;
        assert!(preflight_package(corrupt).is_err());
        let package = preflight_package(golden()).unwrap();
        let mut duplicate = package.exchange.clone();
        duplicate["knowledge"][0]["id"] = duplicate["projects"][0]["id"].clone();
        assert!(validate_exchange(&duplicate).is_err());
        assert!(store.list_workspaces().unwrap().is_empty());
    }

    #[test]
    fn stable_ids_accept_uuid_compatible_numeric_prefixes() {
        assert!(is_stable_id("0f10b4af-c7b0-4f86-bd70-7e6dfd10b29d"));
        assert!(!is_stable_id("-f10b4af-c7b0-4f86-bd70-7e6dfd10b29d"));
    }

    #[test]
    fn interrupted_import_transaction_leaves_no_workspace() {
        let directory = tempdir().unwrap();
        let store = DesktopWorkspaceStore::open(directory.path().join("app-data")).unwrap();
        let package = preflight_package(golden()).unwrap();
        let mut connection = store.connection().unwrap();
        let transaction = connection.transaction().unwrap();
        assert!(store
            .commit_import(
                &transaction,
                "workspace-interrupted",
                "中断导入",
                &package,
                true,
            )
            .is_err());
        drop(transaction);
        assert!(store.list_workspaces().unwrap().is_empty());
    }

    fn imported_store() -> (
        tempfile::TempDir,
        DesktopWorkspaceStore,
        WorkspaceProjection,
    ) {
        let directory = tempdir().unwrap();
        let store = DesktopWorkspaceStore::open(directory.path().join("app-data")).unwrap();
        let source = directory.path().join("golden.nfai-exchange");
        fs::write(&source, golden()).unwrap();
        let receipt = store.stage_selected_file(source.to_str().unwrap()).unwrap();
        let imported = store
            .import_staged(ImportArgs {
                staging_id: receipt.staging_id,
                workspace_title: "P6-C 测试工作区".into(),
            })
            .unwrap();
        (directory, store, imported)
    }

    #[test]
    fn p6g_owner_reopens_rejects_stale_revision_and_fails_closed_on_catalog_corruption() {
        let (directory, store, imported) = imported_store();
        let conversation = store.mutate_domain(DomainMutationArgs {
            intent_id: "p6g-create-conversation".into(), workspace_id: imported.summary.id.clone(), entity: "conversation".into(), action: "create".into(), object_id: None, expected_revision: None,
            fields: json!({"title":"P6-G fixture","projectId":null,"firstMessage":"local only"}),
        }).unwrap();
        let candidate = CatalogCandidate {
            provider_family: p6g::ProviderFamily::Anthropic,
            provider_id: "anthropic".into(),
            model_id: "anthropic.fixture".into(),
            display_name: "Anthropic fixture".into(),
            tiers: vec![p6g::ModelTier::Balanced],
            capabilities: vec![p6g::Capability::Text],
            available: true,
            known_cost_micros: Some(7),
            latency_rank: 1,
            context_window_tokens: Some(8192),
        };
        assert_eq!(
            store
                .p6g_upsert_catalog_candidate(P6gCatalogCandidateArgs {
                    expected_revision: 0,
                    catalog_version: "fixture-v1".into(),
                    policy_version: 1,
                    candidate
                })
                .unwrap()
                .revision,
            1
        );
        assert!(store
            .p6g_upsert_catalog_candidate(P6gCatalogCandidateArgs {
                expected_revision: 0,
                catalog_version: "fixture-v1".into(),
                policy_version: 1,
                candidate: CatalogCandidate {
                    provider_family: p6g::ProviderFamily::Openai,
                    provider_id: "openai".into(),
                    model_id: "openai.fixture".into(),
                    display_name: "OpenAI fixture".into(),
                    tiers: vec![p6g::ModelTier::Balanced],
                    capabilities: vec![p6g::Capability::Text],
                    available: true,
                    known_cost_micros: Some(1),
                    latency_rank: 1,
                    context_window_tokens: Some(8192)
                }
            })
            .unwrap_err()
            .contains("REVISION_CONFLICT"));
        assert_eq!(
            store
                .p6g_set_global_default(P6gGlobalDefaultArgs {
                    expected_revision: 0,
                    tier: Some(p6g::ModelTier::Balanced)
                })
                .unwrap()
                .revision,
            1
        );
        assert_eq!(
            store
                .p6g_set_conversation_override(P6gConversationOverrideArgs {
                    workspace_id: imported.summary.id.clone(),
                    conversation_id: conversation.object_id.clone(),
                    expected_revision: 0,
                    model_id: Some("anthropic.fixture".into())
                })
                .unwrap()
                .revision,
            1
        );
        let decision = store
            .p6g_evaluate(P6gEvaluateArgs {
                workspace_id: imported.summary.id.clone(),
                conversation_id: conversation.object_id.clone(),
                request: RouteRequest {
                    tier: p6g::ModelTier::Fast,
                    required_capabilities: vec![p6g::Capability::Text],
                    exact_historical_cache_hit: false,
                    local_safe_required: false,
                    unknown_cost_confirmed: false,
                    context_tokens: None,
                    budget_micros: None,
                },
            })
            .unwrap();
        assert_eq!(decision.reason, p6g::RouteReason::ManualOverride);
        assert_eq!(
            store
                .p6g_selection(&imported.summary.id, &conversation.object_id)
                .unwrap()
                .last_route
                .unwrap()
                .model_id
                .as_deref(),
            Some("anthropic.fixture")
        );
        drop(store);
        let reopened = DesktopWorkspaceStore::open(directory.path().join("app-data")).unwrap();
        assert_eq!(
            reopened
                .p6g_selection(&imported.summary.id, &conversation.object_id)
                .unwrap()
                .conversation_override
                .model_id
                .as_deref(),
            Some("anthropic.fixture")
        );
        reopened
            .connection()
            .unwrap()
            .execute(
                "UPDATE p6g_catalog SET catalog_json='{broken' WHERE id=1",
                [],
            )
            .unwrap();
        assert!(reopened
            .p6g_catalog(&reopened.connection().unwrap())
            .unwrap()
            .snapshot
            .candidates
            .is_empty());
    }

    fn p7e_semantic_snapshot() -> p7e_isolated_workspace_v1::SemanticSnapshot {
        p7e_isolated_workspace_v1::SemanticSnapshot {
            app_id: "com.nanzhufeng.ai".into(),
            document_id: "desktop-owner-p7e".into(),
            revision: 2,
            records: vec![
                p7e_isolated_workspace_v1::SemanticRecord {
                    kind: "project".into(),
                    id: "project-p7e".into(),
                    revision: 2,
                    classification: "NORMAL".into(),
                    content_json: r#"{"format":"nfai.sync.semantic-record.v1","semanticVersion":1,"kind":"project","id":"project-p7e","revision":2,"value":{"title":"P7E owner bridge"}}"#.into(),
                },
                p7e_isolated_workspace_v1::SemanticRecord {
                    kind: "safe_settings".into(),
                    id: "safe-settings".into(),
                    revision: 1,
                    classification: "NORMAL".into(),
                    content_json: r#"{"format":"nfai.sync.semantic-record.v1","semanticVersion":1,"kind":"safe_settings","id":"safe-settings","revision":1,"value":{}}"#.into(),
                },
            ],
        }
    }

    #[test]
    fn p7e_owner_bridge_creates_only_an_isolated_workspace_and_never_a_tauri_entrypoint() {
        let (directory, store, imported) = imported_store();
        let snapshot = p7e_semantic_snapshot();
        let payload = json!({
            "format":"nfai.sync.payload", "protocolVersion":1, "schemaVersion":1,
            "appId":snapshot.app_id, "documentId":snapshot.document_id, "revision":snapshot.revision,
            "records": snapshot.records.iter().map(|record| json!({
                "kind":record.kind, "id":record.id, "revision":record.revision,
                "classification":record.classification,
                "content":serde_json::from_str::<Value>(&record.content_json).unwrap(),
            })).collect::<Vec<_>>(),
        });
        let envelope = sync_v1::seal_known(
            payload,
            "fixture recovery only",
            sync_v1::KnownMaterial {
                data_key: vec![7; 32],
                salt: vec![2; 16],
                wrapping_nonce: vec![3; 12],
                payload_nonce: vec![4; 12],
            },
        )
        .unwrap();

        let isolated_hash = store
            .open_p7e_to_new_isolated_workspace(
                &envelope,
                "fixture recovery only",
                "com.nanzhufeng.ai",
                "desktop-owner-p7e",
                2,
                "desktop-p7e-b",
            )
            .unwrap();

        assert_eq!(1, store.list_workspaces().unwrap().len());
        assert!(store.workspace_projection(&imported.summary.id).is_ok());
        assert!(directory
            .path()
            .join("app-data/p7e-isolated-workspace-v1/workspaces/desktop-p7e-b/workspace.sqlite3")
            .is_file());
        let readback = store.read_p7e_isolated_workspace("desktop-p7e-b").unwrap();
        assert_eq!(snapshot.app_id, readback.app_id);
        assert_eq!(snapshot.document_id, readback.document_id);
        assert_eq!(snapshot.revision, readback.revision);
        assert_eq!(snapshot.records.len(), readback.records.len());
        for (expected, actual) in snapshot.records.iter().zip(readback.records.iter()) {
            assert_eq!(expected.kind, actual.kind);
            assert_eq!(expected.id, actual.id);
            assert_eq!(expected.revision, actual.revision);
            assert_eq!(expected.classification, actual.classification);
            assert_eq!(
                serde_json::from_str::<Value>(&expected.content_json).unwrap(),
                serde_json::from_str::<Value>(&actual.content_json).unwrap(),
            );
        }
        assert_eq!(
            2,
            sync_v1::open(
                &store
                    .seal_p7e_isolated_workspace("desktop-p7e-b", "fixture recovery only", &[9; 32])
                    .unwrap(),
                "fixture recovery only",
                "com.nanzhufeng.ai",
                "desktop-owner-p7e",
                2,
            )
            .unwrap()
            .payload["revision"]
                .as_u64()
                .unwrap()
        );
        // A real store re-open must retain only the isolated P7-E owner state; the P6 workspace
        // remains independently readable and no Tauri command is involved.
        drop(store);
        let reopened = DesktopWorkspaceStore::open(directory.path().join("app-data")).unwrap();
        assert_eq!(1, reopened.list_workspaces().unwrap().len());
        let after_restart = reopened
            .read_p7e_isolated_workspace("desktop-p7e-b")
            .unwrap();
        assert_eq!(snapshot.revision, after_restart.revision);
        assert_eq!(snapshot.records.len(), after_restart.records.len());
        for (expected, actual) in snapshot.records.iter().zip(after_restart.records.iter()) {
            assert_eq!(expected.kind, actual.kind);
            assert_eq!(expected.id, actual.id);
            assert_eq!(expected.revision, actual.revision);
            assert_eq!(expected.classification, actual.classification);
            assert_eq!(
                serde_json::from_str::<Value>(&expected.content_json).unwrap(),
                serde_json::from_str::<Value>(&actual.content_json).unwrap(),
            );
        }
        assert!(reopened.workspace_projection(&imported.summary.id).is_ok());
        assert_eq!(64, isolated_hash.len());
    }

    #[test]
    fn domain_intent_conflict_idempotency_undo_redo_and_export_are_persistent() {
        let (directory, store, imported) = imported_store();
        let create = DomainMutationArgs {
            intent_id: "intent-create-project-p6c".into(),
            workspace_id: imported.summary.id.clone(),
            entity: "project".into(),
            action: "create".into(),
            object_id: None,
            expected_revision: None,
            fields: json!({"title":"本地 P6-C 项目","description":"可撤销的本地项目"}),
        };
        let created = store.mutate_domain(create).unwrap();
        assert_eq!(created.revision, 1);
        assert!(
            store
                .mutate_domain(DomainMutationArgs {
                    intent_id: "intent-create-project-p6c".into(),
                    workspace_id: imported.summary.id.clone(),
                    entity: "project".into(),
                    action: "create".into(),
                    object_id: None,
                    expected_revision: None,
                    fields: json!({"title":"被重放的名称","description":"x"})
                })
                .unwrap()
                .replayed
        );
        let updated = store
            .mutate_domain(DomainMutationArgs {
                intent_id: "intent-update-project-p6c".into(),
                workspace_id: imported.summary.id.clone(),
                entity: "project".into(),
                action: "update".into(),
                object_id: Some(created.object_id.clone()),
                expected_revision: Some(1),
                fields: json!({"title":"已编辑项目"}),
            })
            .unwrap();
        assert_eq!(updated.revision, 2);
        assert!(store
            .mutate_domain(DomainMutationArgs {
                intent_id: "intent-conflict-project-p6c".into(),
                workspace_id: imported.summary.id.clone(),
                entity: "project".into(),
                action: "update".into(),
                object_id: Some(created.object_id.clone()),
                expected_revision: Some(1),
                fields: json!({"title":"不得覆盖"})
            })
            .unwrap_err()
            .contains("REVISION_CONFLICT"));
        let undone = store
            .apply_history(
                HistoryArgs {
                    intent_id: "intent-undo-project-p6c".into(),
                    workspace_id: imported.summary.id.clone(),
                },
                false,
            )
            .unwrap();
        assert_eq!(undone.revision, 3);
        let redone = store
            .apply_history(
                HistoryArgs {
                    intent_id: "intent-redo-project-p6c".into(),
                    workspace_id: imported.summary.id.clone(),
                },
                true,
            )
            .unwrap();
        assert_eq!(redone.revision, 4);
        let history = store.workbench_history(&imported.summary.id).unwrap();
        assert!(history.can_undo);
        drop(store);
        let reopened = DesktopWorkspaceStore::open(directory.path().join("app-data")).unwrap();
        assert!(
            reopened
                .workbench_history(&imported.summary.id)
                .unwrap()
                .can_undo
        );
        let output = directory.path().join("p6c-roundtrip.nfai-exchange");
        let receipt = reopened
            .export_workspace_to_path(&imported.summary.id, output.to_str().unwrap())
            .unwrap();
        assert_eq!(
            receipt.semantic_hash,
            reopened
                .workspace_projection(&imported.summary.id)
                .unwrap()
                .summary
                .semantic_hash
        );
        assert!(preflight_package(fs::read(output).unwrap()).is_ok());
    }

    #[test]
    fn undo_and_redo_of_a_created_object_do_not_require_it_in_the_before_snapshot() {
        let (_directory, store, imported) = imported_store();
        let created = store
            .mutate_domain(DomainMutationArgs {
                intent_id: "intent-create-undo-project-p6d".into(),
                workspace_id: imported.summary.id.clone(),
                entity: "project".into(),
                action: "create".into(),
                object_id: None,
                expected_revision: None,
                fields: json!({"title":"P6-D 可逆创建","description":"非敏感测试"}),
            })
            .unwrap();
        let undone = store
            .apply_history(
                HistoryArgs {
                    intent_id: "intent-undo-created-project-p6d".into(),
                    workspace_id: imported.summary.id.clone(),
                },
                false,
            )
            .unwrap();
        assert_eq!(undone.revision, 2);
        assert!(!store
            .workspace_projection(&imported.summary.id)
            .unwrap()
            .exchange["projects"]
            .as_array()
            .unwrap()
            .iter()
            .any(|item| item["id"] == created.object_id));
        let redone = store
            .apply_history(
                HistoryArgs {
                    intent_id: "intent-redo-created-project-p6d".into(),
                    workspace_id: imported.summary.id.clone(),
                },
                true,
            )
            .unwrap();
        assert_eq!(redone.revision, 2);
        assert!(store
            .workspace_projection(&imported.summary.id)
            .unwrap()
            .exchange["projects"]
            .as_array()
            .unwrap()
            .iter()
            .any(|item| item["id"] == created.object_id && item["revision"] == 2));
    }

    #[test]
    fn relation_scope_soft_delete_restore_and_safe_metadata_are_enforced() {
        let (_directory, store, imported) = imported_store();
        let knowledge = store.mutate_domain(DomainMutationArgs { intent_id:"intent-create-knowledge-p6c".into(), workspace_id:imported.summary.id.clone(), entity:"knowledge".into(), action:"create".into(), object_id:None, expected_revision:None, fields:json!({"title":"本地知识","body":"只作为文本保存","scope":"PROJECT","projectId":"project-p6a-01","tags":["local"]}) }).unwrap();
        let knowledge_two = store.mutate_domain(DomainMutationArgs { intent_id:"intent-create-knowledge-two-p6c".into(), workspace_id:imported.summary.id.clone(), entity:"knowledge".into(), action:"create".into(), object_id:None, expected_revision:None, fields:json!({"title":"第二本地知识","body":"同一项目范围","scope":"PROJECT","projectId":"project-p6a-01","tags":["local"]}) }).unwrap();
        let relation = store.mutate_domain(DomainMutationArgs { intent_id:"intent-create-relation-p6c".into(), workspace_id:imported.summary.id.clone(), entity:"relation".into(), action:"create".into(), object_id:None, expected_revision:None, fields:json!({"fromId":knowledge_two.object_id,"toId":knowledge.object_id,"kind":"RELATED"}) }).unwrap();
        let deleted = store
            .mutate_domain(DomainMutationArgs {
                intent_id: "intent-delete-knowledge-p6c".into(),
                workspace_id: imported.summary.id.clone(),
                entity: "knowledge".into(),
                action: "softDelete".into(),
                object_id: Some(knowledge.object_id.clone()),
                expected_revision: Some(1),
                fields: json!({}),
            })
            .unwrap();
        assert_eq!(deleted.revision, 2);
        assert_eq!(
            store
                .workbench_history(&imported.summary.id)
                .unwrap()
                .recycle_bin
                .len(),
            1
        );
        let restored = store
            .mutate_domain(DomainMutationArgs {
                intent_id: "intent-restore-knowledge-p6c".into(),
                workspace_id: imported.summary.id.clone(),
                entity: "knowledge".into(),
                action: "restore".into(),
                object_id: Some(knowledge.object_id),
                expected_revision: Some(2),
                fields: json!({}),
            })
            .unwrap();
        assert_eq!(restored.revision, 3);
        assert!(store
            .mutate_domain(DomainMutationArgs {
                intent_id: "intent-delete-relation-p6c".into(),
                workspace_id: imported.summary.id.clone(),
                entity: "relation".into(),
                action: "softDelete".into(),
                object_id: Some(relation.object_id),
                expected_revision: Some(1),
                fields: json!({})
            })
            .is_ok());
        assert!(store.upsert_model_metadata(ModelMetadataArgs { intent_id:"intent-model-metadata-p6c".into(), workspace_id:imported.summary.id.clone(), expected_revision:0, provider_id:"provider-local".into(), model_id:"model-fixture".into(), metadata:json!({"capabilities":["TEXT"],"priceVersion":"fixture-v1","currency":"CNY","source":"本地 fixture","updatedAt":"2026-08-13T00:00:00Z","pricingStatus":"FIXTURE","safeUsage":{"knownCostMicros":null}}) }).is_ok());
        assert_eq!(
            store
                .workbench_history(&imported.summary.id)
                .unwrap()
                .model_metadata
                .len(),
            1
        );
        assert!(store
            .upsert_model_metadata(ModelMetadataArgs {
                intent_id: "intent-model-secret-p6c".into(),
                workspace_id: imported.summary.id,
                expected_revision: 1,
                provider_id: "provider-local".into(),
                model_id: "model-fixture".into(),
                metadata: json!({"apiKey":"forbidden"})
            })
            .unwrap_err()
            .contains("未知或不安全"));
    }

    #[test]
    fn conversation_pin_archive_restore_are_typed_and_survive_reopen() {
        let (directory, store, imported) = imported_store();
        let projection = store.workspace_projection(&imported.summary.id).unwrap();
        let conversation = projection.exchange["conversations"][0].clone();
        let id = conversation["id"].as_str().unwrap().to_owned();
        let messages = conversation["messages"].clone();
        let initial_revision = conversation["revision"].as_u64().unwrap();

        let pinned = store
            .mutate_domain(DomainMutationArgs {
                intent_id: "intent-pin-conversation-p6d-sidebar".into(),
                workspace_id: imported.summary.id.clone(),
                entity: "conversation".into(),
                action: "setPinned".into(),
                object_id: Some(id.clone()),
                expected_revision: Some(initial_revision),
                fields: json!({"pinned":true}),
            })
            .unwrap();
        assert_eq!(initial_revision + 1, pinned.revision);
        assert!(
            store
                .mutate_domain(DomainMutationArgs {
                    intent_id: "intent-pin-conversation-p6d-sidebar".into(),
                    workspace_id: imported.summary.id.clone(),
                    entity: "conversation".into(),
                    action: "setPinned".into(),
                    object_id: Some(id.clone()),
                    expected_revision: Some(initial_revision),
                    fields: json!({"pinned":false}),
                })
                .unwrap()
                .replayed
        );

        let archived = store
            .mutate_domain(DomainMutationArgs {
                intent_id: "intent-archive-conversation-p6d-sidebar".into(),
                workspace_id: imported.summary.id.clone(),
                entity: "conversation".into(),
                action: "archive".into(),
                object_id: Some(id.clone()),
                expected_revision: Some(pinned.revision),
                fields: json!({}),
            })
            .unwrap();
        let archived_item = store
            .workspace_projection(&imported.summary.id)
            .unwrap()
            .exchange["conversations"]
            .as_array()
            .unwrap()
            .iter()
            .find(|item| item["id"] == id)
            .unwrap()
            .clone();
        assert_eq!(Value::Bool(true), archived_item["archived"]);
        assert_eq!(Value::Bool(false), archived_item["pinned"]);
        assert_eq!(messages, archived_item["messages"]);
        assert!(store
            .mutate_domain(DomainMutationArgs {
                intent_id: "intent-pin-archived-conversation-p6d-sidebar".into(),
                workspace_id: imported.summary.id.clone(),
                entity: "conversation".into(),
                action: "setPinned".into(),
                object_id: Some(id.clone()),
                expected_revision: Some(archived.revision),
                fields: json!({"pinned":true}),
            })
            .unwrap_err()
            .contains("已归档会话"));

        let restored = store
            .mutate_domain(DomainMutationArgs {
                intent_id: "intent-restore-conversation-p6d-sidebar".into(),
                workspace_id: imported.summary.id.clone(),
                entity: "conversation".into(),
                action: "restore".into(),
                object_id: Some(id.clone()),
                expected_revision: Some(archived.revision),
                fields: json!({}),
            })
            .unwrap();
        drop(store);
        let reopened = DesktopWorkspaceStore::open(directory.path().join("app-data")).unwrap();
        let readback = reopened.workspace_projection(&imported.summary.id).unwrap();
        let restored_item = readback.exchange["conversations"]
            .as_array()
            .unwrap()
            .iter()
            .find(|item| item["id"] == id)
            .unwrap();
        assert_eq!(
            restored.revision,
            restored_item["revision"].as_u64().unwrap()
        );
        assert_eq!(Value::Bool(false), restored_item["archived"]);
        assert_eq!(Value::Bool(false), restored_item["pinned"]);
        assert_eq!(messages, restored_item["messages"]);
    }

    #[test]
    fn temporary_conversation_is_isolated_recoverable_and_clear_removes_its_private_asset() {
        let directory = tempdir().unwrap();
        let root = directory.path().join("app-data");
        let store = DesktopWorkspaceStore::open(root.clone()).unwrap();
        let entered = store.enter_or_restore_temporary(None).unwrap();
        assert!(entered.messages.is_empty());
        let updated = store
            .update_temporary(TemporaryConversationUpdateArgs {
                temporary_id: entered.temporary_id.clone(),
                draft: "临时草稿".into(),
                model_override_id: Some("local.fixture-v1".into()),
            })
            .unwrap();
        assert_eq!(updated.draft, "临时草稿");
        let source = directory.path().join("temporary.png");
        fs::write(
            &source,
            [0x89, b'P', b'N', b'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00],
        )
        .unwrap();
        let attached = store
            .import_temporary_attachment(DesktopTemporaryAttachmentImportArgs {
                temporary_id: entered.temporary_id.clone(),
                selected_path: source.to_string_lossy().into_owned(),
            })
            .unwrap();
        assert_eq!(attached.draft_attachment_ids.len(), 1);
        let sha256 = attached.attachments[0].sha256.clone();
        let appended = store
            .append_temporary_message(TemporaryConversationAppendArgs {
                temporary_id: entered.temporary_id.clone(),
            })
            .unwrap();
        assert_eq!(appended.messages.len(), 1);
        assert!(appended.draft_attachment_ids.is_empty());
        let reopened = DesktopWorkspaceStore::open(root.clone())
            .unwrap()
            .enter_or_restore_temporary(Some(entered.temporary_id.clone()))
            .unwrap();
        assert_eq!(reopened.messages.len(), 1);
        assert_eq!(
            reopened.model_override_id.as_deref(),
            Some("local.fixture-v1")
        );
        let resumed_without_id = DesktopWorkspaceStore::open(root.clone())
            .unwrap()
            .read_temporary_recovery()
            .unwrap()
            .unwrap();
        assert_eq!(resumed_without_id.temporary_id, entered.temporary_id);
        assert_eq!(resumed_without_id.messages.len(), 1);
        store.clear_temporary(&entered.temporary_id).unwrap();
        assert!(!root.join("assets").join(sha256).exists());
        let connection = store.connection().unwrap();
        assert_eq!(
            connection
                .query_row(
                    "SELECT COUNT(*) FROM desktop_temporary_recovery",
                    [],
                    |row| row.get::<_, i64>(0)
                )
                .unwrap(),
            0
        );
        assert_eq!(
            connection
                .query_row("SELECT COUNT(*) FROM workspace_exchange", [], |row| row
                    .get::<_, i64>(0))
                .unwrap(),
            0
        );
    }

    #[test]
    fn temporary_conversation_expiring_at_24h_clears_record_and_last_asset_in_one_owner_path() {
        let directory = tempdir().unwrap();
        let root = directory.path().join("app-data");
        let store = DesktopWorkspaceStore::open(root.clone()).unwrap();
        let entered = store.enter_or_restore_temporary(None).unwrap();
        let source = directory.path().join("temporary.txt");
        fs::write(&source, b"temporary attachment").unwrap();
        let attached = store
            .import_temporary_attachment(DesktopTemporaryAttachmentImportArgs {
                temporary_id: entered.temporary_id.clone(),
                selected_path: source.to_string_lossy().into_owned(),
            })
            .unwrap();
        let sha256 = attached.attachments[0].sha256.clone();
        let expired_at = attached.updated_at_ms + ATTACHMENT_RETENTION_MILLIS;
        store.prune_temporary_at(expired_at - 1).unwrap();
        assert!(root.join("assets").join(&sha256).exists());
        store.prune_temporary_at(expired_at).unwrap();
        assert!(!root.join("assets").join(&sha256).exists());
        assert!(store
            .connection()
            .unwrap()
            .query_row(
                "SELECT recovery_json FROM desktop_temporary_recovery WHERE temporary_id=?1",
                [&entered.temporary_id],
                |row| row.get::<_, String>(0)
            )
            .is_err());
    }

    #[test]
    fn p6e_acceptance_fixture_uses_fixed_owner_clock_and_reopens_only_its_receipt() {
        let directory = tempdir().unwrap();
        let root = directory.path().join(P6E_ACCEPTANCE_ROOT_NAME);
        let store = DesktopWorkspaceStore::open(root.clone()).unwrap();
        let receipt = store.run_p6e_temporary_maintenance_acceptance().unwrap();
        assert_eq!(
            receipt,
            P6eTemporaryMaintenanceAcceptanceReceipt {
                retained_at_23h59: true,
                removed_at_24h: true,
                attachment_removed_at_24h: true,
                message_present_before_expiry: true,
                model_override_present_before_expiry: true,
                ordinary_surfaces_clean: true,
            }
        );
        assert_eq!(store.p6e_ordinary_surface_record_count().unwrap(), 0);
        drop(store);
        let reopened = DesktopWorkspaceStore::open(root).unwrap();
        assert_eq!(reopened.p6e_acceptance_receipt().unwrap(), Some(receipt));
        assert!(reopened
            .read_temporary_recovery_at(1_786_752_000_000)
            .unwrap()
            .is_none());
    }

    #[test]
    fn claude_export_parser_keeps_a_tree_and_isolates_a_bad_candidate() {
        let bytes = br#"[{"uuid":"alpha-1","name":"Alpha","created_at":"2026-08-15T08:00:00Z","updated_at":"2026-08-15T08:01:00Z","chat_messages":[{"uuid":"m-user","sender":"human","created_at":"2026-08-15T08:00:00Z","text":"hello","parent_message_uuid":null},{"uuid":"m-assistant","sender":"assistant","created_at":"2026-08-15T08:00:01Z","content":"world","parent_message_uuid":"m-user"}]},{"uuid":"bad-1","name":"Bad","created_at":"2026-08-15T08:00:00Z","updated_at":"2026-08-15T08:01:00Z","chat_messages":[{"uuid":"m-bad","sender":"system","created_at":"2026-08-15T08:00:00Z","text":"no","parent_message_uuid":null}]}]"#;
        let parsed = parse_claude_export(bytes).unwrap();
        let candidate = parsed[0].as_ref().unwrap();
        assert_eq!(candidate.messages.len(), 2);
        assert_eq!(
            candidate.messages[1].parent_source_id.as_deref(),
            Some("m-user")
        );
        assert_eq!(candidate.messages[1].role, "ASSISTANT");
        assert!(parsed[1].is_err());
        assert!(parse_claude_export(br#"[{"uuid":"one","uuid":"two"}]"#).is_err());
    }

    #[test]
    fn claude_selected_file_private_copy_confirm_skip_and_reopen_are_atomic() {
        let (directory, store, imported) = imported_store();
        let source = directory.path().join("p6i-visible-conversations.json");
        fs::write(&source, br#"[{"uuid":"alpha-1","name":"P6I Alpha","created_at":"2026-08-15T08:00:00Z","updated_at":"2026-08-15T08:01:00Z","chat_messages":[{"uuid":"m-user","sender":"human","created_at":"2026-08-15T08:00:00Z","text":"hello","parent_message_uuid":null},{"uuid":"m-assistant","sender":"assistant","created_at":"2026-08-15T08:00:01Z","text":"world","parent_message_uuid":"m-user"}]},{"uuid":"skip-1","name":"P6I Skip","created_at":"2026-08-15T08:00:00Z","updated_at":"2026-08-15T08:01:00Z","chat_messages":[{"uuid":"m-skip","sender":"human","created_at":"2026-08-15T08:00:00Z","text":"skip","parent_message_uuid":null}]}]"#).unwrap();
        let task = store
            .stage_claude_export_selected(source.to_str().unwrap())
            .unwrap();
        let stored: String = store
            .connection()
            .unwrap()
            .query_row(
                "SELECT storage_key FROM claude_import_tasks WHERE id=?1",
                [&task.id],
                |row| row.get(0),
            )
            .unwrap();
        assert!(!stored.contains(source.to_str().unwrap()));
        assert!(store.root.join(stored).is_file());
        let conversation_id = store
            .confirm_claude_import_item(ClaudeImportItemActionArgs {
                workspace_id: imported.summary.id.clone(),
                task_id: task.id.clone(),
                item_id: "claude-item-0".into(),
            })
            .unwrap();
        store
            .skip_claude_import_item(ClaudeImportItemActionArgs {
                workspace_id: imported.summary.id.clone(),
                task_id: task.id.clone(),
                item_id: "claude-item-1".into(),
            })
            .unwrap();
        drop(store);
        let reopened = DesktopWorkspaceStore::open(directory.path().join("app-data")).unwrap();
        let task_readback = reopened.read_claude_import_task(&task.id).unwrap();
        assert_eq!(task_readback.status, "COMPLETED");
        let conversation = reopened
            .workspace_projection(&imported.summary.id)
            .unwrap()
            .exchange["conversations"]
            .as_array()
            .unwrap()
            .iter()
            .find(|value| value["id"] == conversation_id)
            .unwrap()
            .clone();
        assert_eq!(conversation["importedFrom"], "CLAUDE_EXPORT");
        assert_eq!(conversation["messages"].as_array().unwrap().len(), 2);
        assert_eq!(
            reopened
                .connection()
                .unwrap()
                .pragma_query_value(None, "user_version", |row| row.get::<_, i64>(0))
                .unwrap(),
            19
        );
    }

    #[test]
    fn p6j_selected_file_confirm_skip_reopen_export_and_rollback_are_atomic_and_idempotent() {
        let tool_only = br#"[{"id":99,"title":"P6J Tool filter","createdAt":"2026-08-15T08:00:00Z","updatedAt":"2026-08-15T08:00:00Z","sourceText":"{\"chat_messages\":[{\"sender\":\"tool\",\"text\":\"must not import\"},{\"sender\":\"human\",\"text\":\"visible\"}]}"}]"#;
        let filtered = parse_nanfeng_knowledge_export(tool_only).unwrap();
        assert_eq!(filtered[0].as_ref().unwrap().messages.len(), 1);
        assert_eq!(filtered[0].as_ref().unwrap().messages[0].text, "visible");
        let (directory, store, imported) = imported_store();
        let source = directory.path().join("p6j-static-conversations.json");
        fs::write(&source, br#"[{"id":101,"title":"P6J Alpha","createdAt":"2026-08-15T08:00:00Z","updatedAt":"2026-08-15T08:01:00Z","sourceText":"{\"chat_messages\":[{\"sender\":\"human\",\"created_at\":\"2026-08-15T08:00:00Z\",\"text\":\"P6J user visible\"},{\"sender\":\"assistant\",\"created_at\":\"2026-08-15T08:00:01Z\",\"content\":[{\"type\":\"thinking\",\"text\":\"must not export\"},{\"type\":\"tool\",\"text\":\"tool directive must not export\"},{\"type\":\"text\",\"text\":\"P6J assistant visible\"}]}]}"},{"id":102,"title":"P6J Skip","createdAt":"2026-08-15T08:00:00Z","updatedAt":"2026-08-15T08:01:00Z","sourceText":"{\"chat_messages\":[{\"sender\":\"human\",\"text\":\"skip me\"}]}"}]"#).unwrap();
        let task = store.stage_nanfeng_knowledge_export_selected(source.to_str().unwrap()).unwrap();
        let stored: String = store.connection().unwrap().query_row("SELECT storage_key FROM nanfeng_knowledge_import_tasks WHERE id=?1", [&task.id], |row| row.get(0)).unwrap();
        assert!(!stored.contains(source.to_str().unwrap()));
        assert!(store.root.join(stored).is_file());
        drop(store);

        let reopened = DesktopWorkspaceStore::open(directory.path().join("app-data")).unwrap();
        assert_eq!(reopened.read_latest_nanfeng_knowledge_import_task().unwrap().unwrap().id, task.id);
        let bad_workspace = ChatGptImportItemActionArgs { workspace_id: "workspace-missing".into(), task_id: task.id.clone(), item_id: "p6j-item-0".into() };
        assert!(reopened.confirm_nanfeng_knowledge_import_item(bad_workspace).is_err());
        assert_eq!(reopened.read_nanfeng_knowledge_import_task(&task.id).unwrap().items[0].status, "PENDING_CONFIRMATION");
        assert_eq!(reopened.connection().unwrap().query_row("SELECT COUNT(*) FROM nanfeng_knowledge_import_receipts", [], |row| row.get::<_, i64>(0)).unwrap(), 0);

        let action = ChatGptImportItemActionArgs { workspace_id: imported.summary.id.clone(), task_id: task.id.clone(), item_id: "p6j-item-0".into() };
        let conversation_id = reopened.confirm_nanfeng_knowledge_import_item(action.clone()).unwrap();
        assert_eq!(reopened.confirm_nanfeng_knowledge_import_item(action).unwrap(), conversation_id);
        reopened.skip_nanfeng_knowledge_import_item(ChatGptImportItemActionArgs { workspace_id: imported.summary.id.clone(), task_id: task.id.clone(), item_id: "p6j-item-1".into() }).unwrap();
        let readback = reopened.read_nanfeng_knowledge_import_task(&task.id).unwrap();
        assert_eq!(readback.status, "COMPLETED");
        assert!(reopened.read_latest_nanfeng_knowledge_import_task().unwrap().is_none());
        let conversation = reopened.workspace_projection(&imported.summary.id).unwrap().exchange["conversations"].as_array().unwrap().iter().find(|value| value["id"] == conversation_id).unwrap().clone();
        assert_eq!(conversation["importedFrom"], "NANFENG_KNOWLEDGE_EXPORT");
        let visible = canonical_json(&conversation).unwrap();
        assert!(visible.contains("P6J user visible") && visible.contains("P6J assistant visible"));
        assert!(!visible.contains("must not export") && !visible.contains("tool directive"));

        let exported = directory.path().join("p6j-roundtrip.nfai-exchange");
        let receipt = reopened.export_workspace_to_path(&imported.summary.id, exported.to_str().unwrap()).unwrap();
        let strict_readback = preflight_package(fs::read(exported).unwrap()).unwrap();
        assert_eq!(strict_readback.receipt.semantic_hash, receipt.semantic_hash);
        assert_eq!(strict_readback.receipt.package_hash, receipt.package_hash);
        assert_eq!(reopened.connection().unwrap().pragma_query_value(None, "user_version", |row| row.get::<_, i64>(0)).unwrap(), 20);
        assert!(reopened.retry_nanfeng_knowledge_import_task(&task.id).is_err());
    }
}
