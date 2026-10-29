//! Desktop Google account and selected-conversation encrypted sync owner.
//! Secrets live only in the app-owned credential store; SQLite keeps safe state, receipts and diagnostics.
use crate::desktop_data_area;
use crate::sync_state_v1::{self, CredentialStore, SqliteMetadataStore};
use crate::sync_v1::{self, AccountWrappingMaterial};
use aes_gcm::{
    aead::{Aead, KeyInit},
    Aes256Gcm, Nonce,
};
use base64::{
    engine::general_purpose::{STANDARD as BASE64, URL_SAFE_NO_PAD},
    Engine as _,
};
use getrandom::fill as random_fill;
use reqwest::{blocking::Client, redirect::Policy, Url};
use rusqlite::{params, Connection, ErrorCode, OptionalExtension, Transaction, TransactionBehavior};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use std::{
    collections::{BTreeMap, BTreeSet},
    fs,
    io::{Read, Write},
    net::{IpAddr, Ipv4Addr, SocketAddr, TcpListener},
    path::{Path, PathBuf},
    process::Command,
    sync::{Arc, Mutex},
    time::{Duration, Instant, SystemTime, UNIX_EPOCH},
};
use zeroize::{Zeroize, Zeroizing};

const APP_ID: &str = "com.nanzhufeng.ai";
const SESSION_SERVICE: &str = "com.nanzhufeng.ai.desktop.google-session.v1";
const RECOVERY_SERVICE: &str = "com.nanzhufeng.ai.desktop.recovery-wrap.v1";
// This is derived wrapping material only, never the recovery code itself.  It
// survives a renderer/native restart between the one-time display and the
// user's explicit "I saved it" acknowledgement.
const PENDING_RECOVERY_SERVICE: &str = "com.nanzhufeng.ai.desktop.recovery-pending.v1";
const RECOVERY_ROTATION_SERVICE: &str = "com.nanzhufeng.ai.desktop.recovery-rotation.v1";
const DATA_KEY_SERVICE: &str = "com.nanzhufeng.ai.app-private-sync.v2";
const AVATAR_MAX_BYTES: usize = 2 * 1024 * 1024;
const SESSION_MAX_BYTES: usize = 16 * 1024;
const SYNC_INVENTORY_MAX_BYTES: usize = 32 * 1024 * 1024;
const SYNC_INVENTORY_MAX_DOCUMENTS: usize = 10_000;
/// Refresh before an RPC rather than discovering expiry through a failed sync read.
const SESSION_REFRESH_SKEW_SECONDS: u64 = 60;

/// Account-sync credentials are application-owned.  They intentionally do not
/// enter macOS Keychain: the encrypted document and its install-local key live
/// in the private Desktop root, with no system authorization UI.
#[derive(Clone)]
pub struct AppPrivateAccountCredentialStore {
    root: PathBuf,
}

#[derive(Debug, Default, Serialize, Deserialize)]
struct PrivateAccountCredentialDocument {
    version: u8,
    #[serde(default)]
    records: BTreeMap<String, PrivateAccountCredentialRecord>,
}

#[derive(Debug, Serialize, Deserialize)]
struct PrivateAccountCredentialRecord {
    nonce: String,
    ciphertext: String,
}

static ACCOUNT_CREDENTIAL_ACCESS_LOCK: Mutex<()> = Mutex::new(());

fn valid_credential_scope(service: &str, account: &str) -> bool {
    if service == SESSION_SERVICE {
        return account == "active";
    }
    matches!(
        service,
        RECOVERY_SERVICE | PENDING_RECOVERY_SERVICE | RECOVERY_ROTATION_SERVICE | DATA_KEY_SERVICE
    ) && account.len() == 32
        && account
            .bytes()
            .all(|value| value.is_ascii_hexdigit() && !value.is_ascii_uppercase())
}

impl AppPrivateAccountCredentialStore {
    pub fn at(workspace_root: impl AsRef<Path>) -> Self {
        Self {
            root: workspace_root.as_ref().join("account-sync-credentials-v2"),
        }
    }

    fn key_path(&self) -> PathBuf {
        self.root.join("local.key")
    }

    fn document_path(&self) -> PathBuf {
        self.root.join("credentials.json")
    }

    fn record_id(service: &str, account: &str) -> Result<String, String> {
        if !valid_credential_scope(service, account) {
            return Err(safe_error("应用私有同步凭据范围无效"));
        }
        Ok(format!("{service}:{account}"))
    }

    fn ensure_private_root(&self) -> Result<(), String> {
        fs::create_dir_all(&self.root).map_err(|_| safe_error("应用私有同步凭据目录无法创建"))?;
        set_private_permissions(&self.root, true)
    }

    fn load_key(&self, create_if_missing: bool) -> Result<[u8; 32], String> {
        let path = self.key_path();
        #[cfg(windows)]
        {
            use crate::desktop_windows_private_store::KeyLoadError;
            return crate::desktop_windows_private_store::load_or_create_key(
                &self.root,
                &path,
                create_if_missing,
            )
            .map_err(|error| match error {
                KeyLoadError::Missing => safe_error("应用私有同步凭据不存在"),
                KeyLoadError::Invalid => safe_error("应用私有同步凭据密钥无效"),
                KeyLoadError::Unavailable => safe_error("应用私有同步凭据无法读取"),
            });
        }
        #[cfg(not(windows))]
        match fs::read(&path) {
            Ok(bytes) => {
                let key: [u8; 32] = bytes
                    .try_into()
                    .map_err(|_| safe_error("应用私有同步凭据密钥无效"))?;
                set_private_permissions(&path, false)?;
                Ok(key)
            }
            Err(error) if error.kind() == std::io::ErrorKind::NotFound && create_if_missing => {
                self.ensure_private_root()?;
                let mut key = [0u8; 32];
                random_fill(&mut key).map_err(|_| safe_error("应用私有同步凭据密钥无法生成"))?;
                let mut options = fs::OpenOptions::new();
                options.write(true).create_new(true);
                #[cfg(unix)]
                {
                    use std::os::unix::fs::OpenOptionsExt;
                    options.mode(0o600);
                }
                match options.open(&path) {
                    Ok(mut file) => {
                        file.write_all(&key)
                            .map_err(|_| safe_error("应用私有同步凭据密钥无法写入"))?;
                        file.sync_all()
                            .map_err(|_| safe_error("应用私有同步凭据密钥无法提交"))?;
                        set_private_permissions(&path, false)?;
                        Ok(key)
                    }
                    Err(error) if error.kind() == std::io::ErrorKind::AlreadyExists => {
                        self.load_key(false)
                    }
                    Err(_) => Err(safe_error("应用私有同步凭据密钥无法写入")),
                }
            }
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
                Err(safe_error("应用私有同步凭据不存在"))
            }
            Err(_) => Err(safe_error("应用私有同步凭据无法读取")),
        }
    }

    fn read_document(&self) -> Result<PrivateAccountCredentialDocument, String> {
        let path = self.document_path();
        match fs::symlink_metadata(&path) {
            Ok(_) => set_private_permissions(&path, false)?,
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
            Err(_) => return Err(safe_error("应用私有同步凭据无法读取")),
        }
        match fs::read(path) {
            Ok(bytes) => {
                serde_json::from_slice(&bytes).map_err(|_| safe_error("应用私有同步凭据记录无效"))
            }
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
                Ok(PrivateAccountCredentialDocument {
                    version: 2,
                    ..Default::default()
                })
            }
            Err(_) => Err(safe_error("应用私有同步凭据无法读取")),
        }
    }

    fn write_document(&self, document: &PrivateAccountCredentialDocument) -> Result<(), String> {
        self.ensure_private_root()?;
        let encoded =
            serde_json::to_vec(document).map_err(|_| safe_error("应用私有同步凭据无法编码"))?;
        #[cfg(windows)]
        {
            crate::desktop_windows_private_store::atomic_write_private(
                &self.document_path(),
                &encoded,
            )
            .map_err(|_| safe_error("应用私有同步凭据无法提交"))
        }
        #[cfg(not(windows))]
        {
            let temporary = self
                .root
                .join(format!(".credentials-{}.tmp", std::process::id()));
            fs::write(&temporary, encoded)
                .map_err(|_| safe_error("应用私有同步凭据无法写入"))?;
            set_private_permissions(&temporary, false)?;
            fs::rename(&temporary, self.document_path())
                .map_err(|_| safe_error("应用私有同步凭据无法提交"))?;
            set_private_permissions(&self.document_path(), false)
        }
    }

    fn decrypt(
        &self,
        record: &PrivateAccountCredentialRecord,
        key: &[u8; 32],
    ) -> Result<Vec<u8>, String> {
        let nonce = BASE64
            .decode(&record.nonce)
            .map_err(|_| safe_error("应用私有同步凭据记录无效"))?;
        let ciphertext = BASE64
            .decode(&record.ciphertext)
            .map_err(|_| safe_error("应用私有同步凭据记录无效"))?;
        if nonce.len() != 12 {
            return Err(safe_error("应用私有同步凭据记录无效"));
        }
        Aes256Gcm::new_from_slice(key)
            .map_err(|_| safe_error("应用私有同步凭据密钥无效"))?
            .decrypt(Nonce::from_slice(&nonce), ciphertext.as_ref())
            .map_err(|_| safe_error("应用私有同步凭据无法解密"))
    }
}

impl CredentialStore for AppPrivateAccountCredentialStore {
    fn save(&self, service: &str, account: &str, secret: &[u8]) -> Result<(), String> {
        let record_id = Self::record_id(service, account)?;
        let valid = match service {
            SESSION_SERVICE => !secret.is_empty() && secret.len() <= SESSION_MAX_BYTES,
            RECOVERY_SERVICE
            | PENDING_RECOVERY_SERVICE
            | RECOVERY_ROTATION_SERVICE
            | DATA_KEY_SERVICE => secret.len() == 32,
            _ => false,
        };
        if !valid {
            return Err(safe_error("应用私有同步凭据未通过安全校验"));
        }
        let _access = ACCOUNT_CREDENTIAL_ACCESS_LOCK
            .lock()
            .map_err(|_| safe_error("应用私有同步凭据状态忙"))?;
        #[cfg(windows)]
        let _process_access = crate::desktop_windows_private_store::lock_store(&self.root)
            .map_err(|_| safe_error("应用私有同步凭据状态忙"))?;
        let key = self.load_key(true)?;
        let mut nonce = [0u8; 12];
        random_fill(&mut nonce).map_err(|_| safe_error("应用私有同步凭据随机数无法生成"))?;
        let ciphertext = Aes256Gcm::new_from_slice(&key)
            .map_err(|_| safe_error("应用私有同步凭据密钥无效"))?
            .encrypt(Nonce::from_slice(&nonce), secret)
            .map_err(|_| safe_error("应用私有同步凭据无法加密"))?;
        let mut document = self.read_document()?;
        document.version = 2;
        document.records.insert(
            record_id,
            PrivateAccountCredentialRecord {
                nonce: BASE64.encode(nonce),
                ciphertext: BASE64.encode(ciphertext),
            },
        );
        self.write_document(&document)
    }

    fn read(&self, service: &str, account: &str) -> Result<Vec<u8>, String> {
        self.read_optional(service, account)?
            .ok_or_else(|| safe_error("应用私有同步凭据不存在"))
    }

    fn read_optional(&self, service: &str, account: &str) -> Result<Option<Vec<u8>>, String> {
        let record_id = Self::record_id(service, account)?;
        let _access = ACCOUNT_CREDENTIAL_ACCESS_LOCK.lock()
            .map_err(|_| safe_error("应用私有同步凭据状态忙"))?;
        #[cfg(windows)]
        let _process_access = crate::desktop_windows_private_store::lock_store(&self.root)
            .map_err(|_| safe_error("应用私有同步凭据状态忙"))?;
        let document = self.read_document()?;
        let Some(record) = document.records.get(&record_id) else {
            return Ok(None);
        };
        let key = self.load_key(false)?;
        self.decrypt(record, &key).map(Some)
    }

    fn delete(&self, service: &str, account: &str) -> Result<(), String> {
        let record_id = Self::record_id(service, account)?;
        let _access = ACCOUNT_CREDENTIAL_ACCESS_LOCK
            .lock()
            .map_err(|_| safe_error("应用私有同步凭据状态忙"))?;
        #[cfg(windows)]
        let _process_access = crate::desktop_windows_private_store::lock_store(&self.root)
            .map_err(|_| safe_error("应用私有同步凭据状态忙"))?;
        let mut document = self.read_document()?;
        document.records.remove(&record_id);
        self.write_document(&document)
    }
}

fn set_private_permissions(path: &Path, directory: bool) -> Result<(), String> {
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        fs::set_permissions(
            path,
            fs::Permissions::from_mode(if directory { 0o700 } else { 0o600 }),
        )
        .map_err(|_| safe_error("应用私有同步凭据权限无法收紧"))?;
    }
    #[cfg(windows)]
    {
        crate::desktop_windows_private_store::secure_path(path, directory)
            .map_err(|_| safe_error("应用私有同步凭据权限无法收紧"))?;
    }
    #[cfg(not(any(unix, windows)))]
    {
        let _ = (path, directory);
    }
    Ok(())
}

fn now_ms() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as i64
}
fn safe_error(message: &str) -> String {
    serde_json::to_string(&json!({"error":message}))
        .unwrap_or_else(|_| "{\"error\":\"同步操作失败\"}".into())
}
fn restore_store_error(error: rusqlite::Error, fallback: &str) -> String {
    if matches!(error.sqlite_error_code(), Some(ErrorCode::DatabaseBusy | ErrorCode::DatabaseLocked)) {
        safe_error("本机工作区正忙，请稍后重试")
    } else {
        safe_error(fallback)
    }
}
fn write_cloud_restore_intent(
    transaction: &rusqlite::Transaction<'_>, account: &str, metadata_revision: i64,
    created_at: &str, timestamp: i64,
) -> Result<(), String> {
    // Several valid documents can restore within one clock tick. The receipt
    // identity must remain unique without depending on wall-clock resolution.
    let intent_id = format!("desktop-cloud-restore-{timestamp}-{}", random_urlsafe(12)?);
    transaction.execute(
        "INSERT INTO sync_intents(intent_id,account_ref,expected_revision,resulting_revision,resulting_state,created_at) VALUES(?1,?2,?3,?4,'READY',?5)",
        params![intent_id,account,metadata_revision,metadata_revision+1,created_at],
    ).map_err(|_| safe_error("云端恢复意图回执未写入"))?;
    Ok(())
}

fn restore_failure_code(error: &str) -> &'static str {
    if error.contains("SYNC_CANCELLED_DURING_READ") { "SYNC_CANCELLED_DURING_READ" }
    else if error.contains("旧加密记录") { "LEGACY_DEVICE_REQUIRED" }
    else if error.contains("标题版本冲突") { "TITLE_CONFLICT" }
    else if error.contains("本机对话有待续同步变更") { "LOCAL_SYNC_PENDING" }
    else if error.contains("本机工作区正忙") { "LOCAL_STORE_BUSY" }
    else { "DOCUMENT_RESTORE_FAILED" }
}
fn sha256(value: &[u8]) -> String {
    format!("{:x}", Sha256::digest(value))
}
fn random_urlsafe(bytes: usize) -> Result<String, String> {
    let mut value = vec![0u8; bytes];
    random_fill(&mut value).map_err(|_| safe_error("系统随机源不可用"))?;
    let encoded = URL_SAFE_NO_PAD.encode(&value);
    value.zeroize();
    Ok(encoded)
}

fn new_sync_job_id(account: &str) -> Result<String, String> {
    Ok(format!("sync-job-{}-{}-{}", &account[..12], now_ms(), random_urlsafe(12)?))
}

#[derive(Debug, Clone)]
pub struct ServiceConfig {
    pub supabase_url: Url,
    pub publishable_key: String,
    pub localhost_mock: bool,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Availability {
    Disabled,
    Configured,
}

fn first_non_blank<'a>(values: impl IntoIterator<Item = Option<&'a str>>) -> String {
    values
        .into_iter()
        .flatten()
        .map(str::trim)
        .find(|value| !value.is_empty())
        .unwrap_or_default()
        .to_owned()
}

fn runtime_or_bundled(runtime_key: &str, bundled_value: Option<&'static str>) -> String {
    let runtime_value = std::env::var(runtime_key).ok();
    first_non_blank([runtime_value.as_deref(), bundled_value])
}

pub fn resolve_config(
    localhost_mock: bool,
) -> Result<(Availability, Option<ServiceConfig>), String> {
    let url = runtime_or_bundled(
        "NANFENG_SUPABASE_URL",
        option_env!("NANFENG_DESKTOP_BUNDLED_SUPABASE_URL"),
    );
    let key = runtime_or_bundled(
        "NANFENG_SUPABASE_PUBLISHABLE_KEY",
        option_env!("NANFENG_DESKTOP_BUNDLED_SUPABASE_PUBLISHABLE_KEY"),
    );
    if url.trim().is_empty() || key.trim().is_empty() {
        return Ok((Availability::Disabled, None));
    }
    let supabase_url = Url::parse(url.trim()).map_err(|_| safe_error("南枫云地址无效"))?;
    let valid_remote = |value: &Url| {
        value.scheme() == "https" && value.username().is_empty() && value.password().is_none()
    };
    let valid_mock = |value: &Url| matches!(value.host_str(), Some("127.0.0.1" | "localhost"));
    if localhost_mock {
        if !valid_mock(&supabase_url) {
            return Err(safe_error("隔离 Mock 只允许 localhost"));
        }
    } else if !valid_remote(&supabase_url) {
        return Err(safe_error("南枫云配置未通过安全校验"));
    }
    Ok((
        Availability::Configured,
        Some(ServiceConfig {
            supabase_url,
            publishable_key: key.trim().into(),
            localhost_mock,
        }),
    ))
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Session {
    pub user_id: String,
    pub email: String,
    pub display_name: Option<String>,
    pub avatar_url: Option<String>,
    pub access_token: String,
    pub refresh_token: String,
    pub expires_at_epoch_seconds: u64,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AccountProjection {
    pub configured: bool,
    pub state: String,
    pub email: Option<String>,
    pub display_name: Option<String>,
    pub avatar_data_url: Option<String>,
    pub recovery_state: String,
    pub recovery_confirmation_pending: bool,
    pub periodic_enabled: bool,
    pub rotation_pending: bool,
    pub last_success_at_ms: Option<i64>,
    pub selected_conversation_count: u64,
    pub synced_conversation_keys: Vec<String>,
    pub sync_stage: String,
    pub notice: Option<String>,
    pub device_id: String,
    pub diagnostics: Vec<DiagnosticProjection>,
    pub notifications: Vec<NotificationProjection>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DiagnosticProjection {
    pub event: String,
    pub outcome: String,
    pub safe_code: Option<String>,
    pub occurred_at_ms: i64,
}
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct NotificationProjection {
    pub kind: String,
    pub message: String,
    pub occurred_at_ms: i64,
    pub unread: bool,
}

#[derive(Clone)]
pub struct PendingRecovery {
    pub account_ref: String,
    pub wrapping_key: Vec<u8>,
    pub salt: Vec<u8>,
    pub code_hash: String,
    pub created_at_ms: i64,
    pub rotation: bool,
}
impl Drop for PendingRecovery {
    fn drop(&mut self) {
        self.wrapping_key.zeroize();
        self.salt.zeroize();
    }
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RecoveryCodeProjection {
    pub recovery_code: String,
    pub confirmation_hash: String,
}

pub fn migrate(transaction: &Transaction<'_>) -> Result<(), String> {
    transaction.execute_batch(
        "CREATE TABLE desktop_cloud_account_state (
            account_ref TEXT PRIMARY KEY NOT NULL, user_id TEXT NOT NULL, email TEXT NOT NULL,
            display_name TEXT, avatar_url TEXT, state TEXT NOT NULL, periodic_enabled INTEGER NOT NULL DEFAULT 0,
            recovery_confirmed INTEGER NOT NULL DEFAULT 0, recovery_salt_b64 TEXT, recovery_generation INTEGER NOT NULL DEFAULT 0,
            last_success_at_ms INTEGER, last_error_code TEXT, device_id TEXT NOT NULL, revision INTEGER NOT NULL, updated_at_ms INTEGER NOT NULL
        );
        CREATE TABLE desktop_selected_conversation_sync (
            account_ref TEXT NOT NULL, workspace_id TEXT NOT NULL, conversation_id TEXT NOT NULL, document_id TEXT NOT NULL,
            remote_revision INTEGER NOT NULL, payload_hash TEXT NOT NULL, local_content_hash TEXT NOT NULL, last_synced_at_ms INTEGER NOT NULL,
            PRIMARY KEY(account_ref,workspace_id,conversation_id), UNIQUE(account_ref,document_id)
        );
        CREATE TABLE desktop_sync_jobs (
            job_id TEXT PRIMARY KEY NOT NULL, account_ref TEXT NOT NULL, workspace_id TEXT, conversation_id TEXT, document_id TEXT,
            stage TEXT NOT NULL, payload_hash TEXT, expected_remote_revision INTEGER, attempt INTEGER NOT NULL,
            next_retry_at_ms INTEGER, safe_error_code TEXT, created_at_ms INTEGER NOT NULL, updated_at_ms INTEGER NOT NULL
        );
        CREATE INDEX desktop_sync_jobs_account_stage ON desktop_sync_jobs(account_ref,stage,updated_at_ms);
        CREATE TABLE desktop_sync_diagnostics (
            id TEXT PRIMARY KEY NOT NULL, account_ref TEXT, event TEXT NOT NULL, outcome TEXT NOT NULL,
            safe_code TEXT, occurred_at_ms INTEGER NOT NULL
        );
        CREATE INDEX desktop_sync_diagnostics_recent ON desktop_sync_diagnostics(occurred_at_ms DESC);
        CREATE TABLE desktop_sync_notifications (
            id TEXT PRIMARY KEY NOT NULL, account_ref TEXT, kind TEXT NOT NULL, message TEXT NOT NULL,
            occurred_at_ms INTEGER NOT NULL, unread INTEGER NOT NULL DEFAULT 1
        );
        CREATE INDEX desktop_sync_notifications_recent ON desktop_sync_notifications(occurred_at_ms DESC);"
    ).map_err(|_| safe_error("SQLite migration 27 失败"))
}

pub fn migrate_recovery_rotation(transaction: &Transaction<'_>) -> Result<(), String> {
    transaction.execute_batch(
        "CREATE TABLE desktop_recovery_rotation (
            account_ref TEXT PRIMARY KEY NOT NULL, new_salt_b64 TEXT NOT NULL,
            generation INTEGER NOT NULL, stage TEXT NOT NULL, completed_documents INTEGER NOT NULL DEFAULT 0,
            total_documents INTEGER NOT NULL DEFAULT 0, safe_error_code TEXT, updated_at_ms INTEGER NOT NULL
        );"
    ).map_err(|_| safe_error("SQLite migration 28 失败"))
}

/// Durable metadata for a recovery code that has been displayed but not yet
/// acknowledged.  The code is deliberately absent: the private credential
/// owner stores only its derived 32-byte wrapping key.
pub fn migrate_pending_recovery_confirmation(transaction: &Transaction<'_>) -> Result<(), String> {
    let exists: bool = transaction
        .query_row(
            "SELECT EXISTS(SELECT 1 FROM sqlite_master WHERE type='table' AND name='desktop_cloud_account_state')",
            [],
            |row| row.get(0),
        )
        .map_err(|_| safe_error("SQLite migration 42 账号状态表无法读取"))?;
    // Focused historical-schema fixtures may intentionally omit the account
    // subsystem. Production databases create this table in migration 27.
    if !exists {
        return Ok(());
    }
    let columns = transaction
        .prepare("SELECT name FROM pragma_table_info('desktop_cloud_account_state')")
        .and_then(|mut statement| {
            statement
                .query_map([], |row| row.get::<_, String>(0))?
                .collect::<Result<BTreeSet<_>, _>>()
        })
        .map_err(|_| safe_error("SQLite migration 42 账号状态字段无法读取"))?;
    for (name, definition) in [
        ("pending_recovery_salt_b64", "TEXT"),
        ("pending_recovery_code_hash", "TEXT"),
        ("pending_recovery_created_at_ms", "INTEGER"),
    ] {
        if !columns.contains(name) {
            transaction
                .execute_batch(&format!(
                    "ALTER TABLE desktop_cloud_account_state ADD COLUMN {name} {definition};"
                ))
                .map_err(|_| safe_error("SQLite migration 42 失败"))?;
        }
    }
    Ok(())
}

fn write_diagnostic(
    connection: &Connection,
    account: Option<&str>,
    event: &str,
    outcome: &str,
    code: Option<&str>,
) {
    append_diagnostic(connection,account,event,outcome,code);
    prune_sync_diagnostics(connection);
}
fn append_diagnostic(
    connection:&Connection, account:Option<&str>, event:&str, outcome:&str, code:Option<&str>,
) {
    let timestamp = now_ms();
    // Several valid items can finish in the same millisecond within a group.
    // Optional feedback keeps its best-effort boundary, with distinct IDs.
    let Ok(nonce)=random_urlsafe(12) else { return; };
    let id = format!("sync-diag-{timestamp}-{nonce}");
    let _ = connection.execute("INSERT INTO desktop_sync_diagnostics(id,account_ref,event,outcome,safe_code,occurred_at_ms) VALUES(?1,?2,?3,?4,?5,?6)", params![id,account,event,outcome,code,timestamp]);
}
fn prune_sync_diagnostics(connection:&Connection) {
    let _ = connection.execute("DELETE FROM desktop_sync_diagnostics WHERE id NOT IN (SELECT id FROM desktop_sync_diagnostics ORDER BY occurred_at_ms DESC LIMIT 100)", []);
}
fn write_notification(connection: &Connection, account: Option<&str>, kind: &str, message: &str) {
    append_notification(connection,account,kind,message);
    prune_sync_notifications(connection);
}
fn append_notification(connection:&Connection,account:Option<&str>,kind:&str,message:&str) {
    let timestamp = now_ms();
    let Ok(nonce)=random_urlsafe(12) else { return; };
    let id = format!("sync-note-{timestamp}-{nonce}");
    let _ = connection.execute("INSERT INTO desktop_sync_notifications(id,account_ref,kind,message,occurred_at_ms,unread) VALUES(?1,?2,?3,?4,?5,1)", params![id,account,kind,message,timestamp]);
}
fn prune_sync_notifications(connection:&Connection) {
    let _ = connection.execute("DELETE FROM desktop_sync_notifications WHERE id NOT IN (SELECT id FROM desktop_sync_notifications ORDER BY occurred_at_ms DESC LIMIT 50)", []);
}

fn require_restore_transaction(transaction:&rusqlite::Transaction<'_>)->Result<(),String> {
    if transaction.is_autocommit() {
        Err(safe_error("云端恢复事务已回滚，未继续写入成功反馈"))
    } else { Ok(()) }
}
fn prune_restore_feedback(transaction:&rusqlite::Transaction<'_>)->Result<(),String> {
    require_restore_transaction(transaction)?;
    prune_sync_diagnostics(transaction);
    require_restore_transaction(transaction)?;
    prune_sync_notifications(transaction);
    require_restore_transaction(transaction)
}

fn save_session<C: CredentialStore>(credentials: &C, session: &Session) -> Result<(), String> {
    let mut encoded = serde_json::to_vec(session).map_err(|_| safe_error("账号会话无法编码"))?;
    let result = credentials
        .save(SESSION_SERVICE, "active", &encoded)
        .map_err(|_| safe_error("账号会话无法安全保存"));
    encoded.zeroize();
    result
}
pub fn read_session<C: CredentialStore>(credentials: &C) -> Result<Option<Session>, String> {
    let Some(mut encoded) = credentials.read_optional(SESSION_SERVICE, "active")? else {
        return Ok(None);
    };
    let result = serde_json::from_slice(&encoded).map_err(|_| safe_error("账号会话无法读取"));
    encoded.zeroize();
    result.map(Some)
}

/// Exchanges an expiring Supabase access token without opening Google again. The refresh token
/// stays inside the app-owned credential boundary and is never included in diagnostics.
fn refresh_session(config: &ServiceConfig, current: &Session) -> Result<Session, String> {
    let builder=Client::builder();
    let client = if config.localhost_mock {builder.no_proxy()} else {builder}
        .redirect(Policy::none())
        .timeout(Duration::from_secs(30))
        .build()
        .map_err(|_| safe_error("南枫云会话刷新不可用"))?;
    let endpoint = config
        .supabase_url
        .join("auth/v1/token?grant_type=refresh_token")
        .map_err(|_| safe_error("南枫云认证地址无效"))?;
    let response: Value = client
        .post(endpoint)
        .header("apikey", &config.publishable_key)
        .json(&json!({"refresh_token":current.refresh_token}))
        .send()
        .map_err(|_| safe_error("南枫云会话刷新未完成"))?
        .error_for_status()
        .map_err(|_| safe_error("登录已失效，请重新登录"))?
        .json()
        .map_err(|_| safe_error("南枫云会话刷新响应无效"))?;
    let user = response
        .get("user")
        .and_then(Value::as_object)
        .ok_or_else(|| safe_error("南枫云会话刷新响应无效"))?;
    let user_id = user
        .get("id")
        .and_then(Value::as_str)
        .ok_or_else(|| safe_error("南枫云会话刷新响应无效"))?;
    if user_id != current.user_id {
        return Err(safe_error("南枫云账号已变化，请重新登录"));
    }
    let metadata = user.get("user_metadata").and_then(Value::as_object);
    Ok(Session {
        user_id: current.user_id.clone(),
        email: user
            .get("email")
            .and_then(Value::as_str)
            .filter(|value| !value.is_empty())
            .unwrap_or(&current.email)
            .into(),
        display_name: metadata
            .and_then(|value| value.get("full_name").or_else(|| value.get("name")))
            .and_then(Value::as_str)
            .filter(|value| !value.is_empty())
            .map(str::to_owned)
            .or_else(|| current.display_name.clone()),
        avatar_url: metadata
            .and_then(|value| value.get("avatar_url").or_else(|| value.get("picture")))
            .and_then(Value::as_str)
            .filter(|value| allowed_avatar_url(value))
            .map(str::to_owned)
            .or_else(|| current.avatar_url.clone()),
        access_token: response
            .get("access_token")
            .and_then(Value::as_str)
            .filter(|value| !value.is_empty())
            .ok_or_else(|| safe_error("南枫云会话刷新响应无效"))?
            .into(),
        refresh_token: response
            .get("refresh_token")
            .and_then(Value::as_str)
            .filter(|value| !value.is_empty())
            .unwrap_or(&current.refresh_token)
            .into(),
        expires_at_epoch_seconds: SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs()
            + response
                .get("expires_in")
                .and_then(Value::as_u64)
                .unwrap_or(3600)
                .clamp(60, 86_400),
    })
}

/// The single RPC-session owner. Normal account-page reads stay local; explicit cloud actions
/// refresh just-in-time and atomically replace only the session secret when necessary.
pub fn active_session<C: CredentialStore>(
    credentials: &C, config: &ServiceConfig, auth_generation: &Mutex<u64>,
) -> Result<Session, String> {
    active_session_for_expected_account(credentials, config, auth_generation, None)
}

pub fn active_session_for_expected_account<C: CredentialStore>(
    credentials: &C,
    config: &ServiceConfig,
    auth_generation: &Mutex<u64>,
    expected_email: Option<&str>,
) -> Result<Session, String> {
    let generation = *auth_generation
        .lock()
        .map_err(|_| safe_error("账号操作状态不可用"))?;
    let current = read_session(credentials)?.ok_or_else(|| safe_error("请先登录 Google 账号"))?;
    if expected_email.is_some_and(|email| email != current.email.as_str()) {
        return Err(safe_error("账号已变化，已停止原账号的延迟同步核对"));
    }
    let now = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs();
    if current.expires_at_epoch_seconds > now + SESSION_REFRESH_SKEW_SECONDS {
        return Ok(current);
    }
    let refreshed = refresh_session(config, &current)?;
    // Keep the account-operation lock through the credential write. A sign-out
    // or account switch cannot be followed by this older network response.
    let current_generation = auth_generation
        .lock()
        .map_err(|_| safe_error("账号操作状态不可用"))?;
    let still_active = read_session(credentials)?;
    if *current_generation != generation
        || !still_active.as_ref().is_some_and(|active| {
            active.user_id == current.user_id
                && active.access_token == current.access_token
                && active.refresh_token == current.refresh_token
        })
    {
        return Err(safe_error("登录状态已变化；迟到会话刷新未写入"));
    }
    save_session(credentials, &refreshed)?;
    Ok(refreshed)
}

pub trait Browser {
    fn open(&self, url: &Url) -> Result<(), String>;
}
pub struct SystemBrowser;
impl Browser for SystemBrowser {
    fn open(&self, url: &Url) -> Result<(), String> {
        #[cfg(target_os = "macos")]
        let result = Command::new("open").arg(url.as_str()).status();
        #[cfg(target_os = "windows")]
        let result = Command::new("rundll32")
            .arg("url.dll,FileProtocolHandler")
            .arg(url.as_str())
            .status();
        #[cfg(all(not(target_os = "macos"), not(target_os = "windows")))]
        let result = Command::new("xdg-open").arg(url.as_str()).status();
        result
            .map_err(|_| safe_error("无法打开系统浏览器"))?
            .success()
            .then_some(())
            .ok_or_else(|| safe_error("系统浏览器未打开"))
    }
}

// This browser exists only in Windows Debug builds. The outer startup gate also
// requires a marked, empty acceptance root and a loopback Supabase fixture.
#[cfg(all(windows, debug_assertions))]
pub struct WindowsAcceptanceBrowser;
#[cfg(all(windows, debug_assertions))]
impl Browser for WindowsAcceptanceBrowser {
    fn open(&self, url: &Url) -> Result<(), String> {
        if url.scheme() != "http"
            || !matches!(url.host_str(), Some("127.0.0.1" | "localhost"))
            || url.port().is_none()
            || url.path() != "/auth/v1/authorize"
        {
            return Err(safe_error("账号合成授权地址无效"));
        }
        let query = url.query_pairs().collect::<BTreeMap<_, _>>();
        if query.get("provider").map(|value| value.as_ref()) != Some("google") {
            return Err(safe_error("账号合成授权请求无效"));
        }
        let callback = query
            .get("redirect_to")
            .and_then(|value| Url::parse(value).ok())
            .ok_or_else(|| safe_error("账号合成回调地址无效"))?;
        if callback.scheme() != "http"
            || callback.host_str() != Some("127.0.0.1")
            || callback.port().is_none()
            || callback.path() != "/oauth/callback"
            || !callback.username().is_empty()
            || callback.password().is_some()
            || callback.fragment().is_some()
            || !callback.query_pairs().any(|(key, value)| key == "state" && !value.is_empty())
        {
            return Err(safe_error("账号合成回调地址无效"));
        }
        let delay_ms = std::env::var("NANFENG_AI_WINDOWS_ACCOUNT_CALLBACK_DELAY_MS")
            .ok()
            .and_then(|value| value.parse::<u64>().ok())
            .unwrap_or(0)
            .min(5_000);
        if delay_ms > 0 {
            std::thread::sleep(Duration::from_millis(delay_ms));
        }
        let mut callback = callback;
        callback
            .query_pairs_mut()
            .append_pair("code", "nfai-synthetic-windows-account-code");
        let target = format!("{}?{}", callback.path(), callback.query().unwrap_or_default());
        let mut stream = std::net::TcpStream::connect(("127.0.0.1", callback.port().unwrap()))
            .map_err(|_| safe_error("账号合成回调连接失败"))?;
        stream
            .write_all(format!("GET {target} HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n").as_bytes())
            .map_err(|_| safe_error("账号合成回调发送失败"))
    }
}

fn callback_code<F: Fn() -> Result<(), String>>(
    listener: TcpListener, expected_state: &str, still_authorized: &F,
) -> Result<String, String> {
    listener
        .set_nonblocking(true)
        .map_err(|_| safe_error("OAuth callback 无法等待"))?;
    let deadline = Instant::now() + Duration::from_secs(180);
    loop {
        still_authorized()?;
        if Instant::now() >= deadline {
            return Err(safe_error("OAuth callback 未完成"));
        }
        let (mut stream, peer) = match listener.accept() {
            Ok(value) => value,
            Err(error)
                if error.kind() == std::io::ErrorKind::WouldBlock && Instant::now() < deadline =>
            {
                std::thread::sleep(Duration::from_millis(25));
                continue;
            }
            Err(_) => return Err(safe_error("OAuth callback 未完成")),
        };
        if !peer.ip().is_loopback() {
            continue;
        }
        // An unrelated browser request must not consume the one authorized callback.
        // Keep the original deadline across all connections.
        stream
            .set_nonblocking(false)
            .map_err(|_| safe_error("OAuth callback 无法读取"))?;
        stream.set_read_timeout(Some(Duration::from_secs(10))).ok();
        let mut bytes = Vec::with_capacity(16 * 1024);
        let read_result = (|| {
            while !bytes.windows(2).any(|value| value == b"\r\n") && bytes.len() < 16 * 1024 {
                let mut chunk = [0u8; 1024];
                let count = stream.read(&mut chunk).ok()?;
                if count == 0 { return None; }
                bytes.extend_from_slice(&chunk[..count]);
            }
            let first = std::str::from_utf8(&bytes).ok()?.lines().next()?;
            let target = first.strip_prefix("GET ")?.split_whitespace().next()?;
            if !target.starts_with('/') { return None; }
            let url = Url::parse(&format!("http://127.0.0.1{target}")).ok()?;
            if url.host_str() != Some("127.0.0.1")
                || url.path() != "/oauth/callback"
                || url.fragment().is_some() {
                return None;
            }
            let values = url.query_pairs().collect::<std::collections::BTreeMap<_, _>>();
            if !values.get("state").is_some_and(|value| value.as_ref() == expected_state) {
                return None;
            }
            Some(values.get("code").filter(|value| !value.is_empty()).map(ToString::to_string))
        })();
        let valid = read_result.is_some();
        let code = read_result.flatten();
        let body = if code.is_some() {
            "授权已完成，可以返回南枫 AI。"
        } else {
            "授权未完成，请返回南枫 AI 重试。"
        };
        let status = if valid { "200 OK" } else { "400 Bad Request" };
        let response = format!("HTTP/1.1 {status}\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}", body.len(), body);
        let _ = stream.write_all(response.as_bytes());
        if valid {
            return code.ok_or_else(|| safe_error("Google 授权未完成或状态校验失败"));
        }
    }
}

pub fn authorize_with_system_browser<B: Browser>(
    config: &ServiceConfig,
    browser: &B,
) -> Result<Session, String> {
    authorize_with_system_browser_guarded(config, browser, || Ok(()))
}

pub fn authorize_with_system_browser_guarded<B: Browser, F: Fn() -> Result<(), String>>(
    config: &ServiceConfig,
    browser: &B,
    before_token_exchange: F,
) -> Result<Session, String> {
    let listener = TcpListener::bind(SocketAddr::new(IpAddr::V4(Ipv4Addr::LOCALHOST), 0))
        .map_err(|_| safe_error("无法创建 OAuth callback"))?;
    let callback_base = format!(
        "http://127.0.0.1:{}/oauth/callback",
        listener
            .local_addr()
            .map_err(|_| safe_error("OAuth callback 无效"))?
            .port()
    );
    let state = random_urlsafe(24)?;
    let verifier = Zeroizing::new(random_urlsafe(48)?);
    let challenge = URL_SAFE_NO_PAD.encode(Sha256::digest(verifier.as_bytes()));
    let mut callback = Url::parse(&callback_base).map_err(|_| safe_error("OAuth callback 无效"))?;
    callback.query_pairs_mut().append_pair("state", &state);
    let mut authorize = config
        .supabase_url
        .join("auth/v1/authorize")
        .map_err(|_| safe_error("南枫云认证地址无效"))?;
    authorize
        .query_pairs_mut()
        .append_pair("provider", "google")
        .append_pair("redirect_to", callback.as_str())
        .append_pair("scopes", "openid email profile")
        .append_pair("code_challenge", &challenge)
        .append_pair("code_challenge_method", "s256")
        .append_pair("access_type", "offline")
        .append_pair("prompt", "select_account");
    browser.open(&authorize)?;
    let code = callback_code(listener, &state, &before_token_exchange)?;
    before_token_exchange()?;
    let builder=Client::builder();
    let client = if config.localhost_mock {builder.no_proxy()} else {builder}
        .redirect(Policy::none())
        .timeout(Duration::from_secs(30))
        .build()
        .map_err(|_| safe_error("OAuth 网络客户端不可用"))?;
    let endpoint = config
        .supabase_url
        .join("auth/v1/token?grant_type=pkce")
        .map_err(|_| safe_error("南枫云认证地址无效"))?;
    let response: Value = client
        .post(endpoint)
        .header("apikey", &config.publishable_key)
        .json(&json!({"auth_code":code,"code_verifier":verifier.as_str()}))
        .send()
        .map_err(|_| safe_error("南枫云登录失败"))?
        .error_for_status()
        .map_err(|_| safe_error("南枫云拒绝登录"))?
        .json()
        .map_err(|_| safe_error("南枫云会话无效"))?;
    let user = response
        .get("user")
        .and_then(Value::as_object)
        .ok_or_else(|| safe_error("南枫云未返回账号"))?;
    let metadata = user.get("user_metadata").and_then(Value::as_object);
    let session = Session {
        user_id: user
            .get("id")
            .and_then(Value::as_str)
            .ok_or_else(|| safe_error("南枫云账号无效"))?
            .into(),
        email: user
            .get("email")
            .and_then(Value::as_str)
            .unwrap_or_default()
            .into(),
        display_name: metadata
            .and_then(|value| value.get("full_name").or_else(|| value.get("name")))
            .and_then(Value::as_str)
            .map(str::to_owned),
        avatar_url: metadata
            .and_then(|value| value.get("avatar_url").or_else(|| value.get("picture")))
            .and_then(Value::as_str)
            .filter(|value| allowed_avatar_url(value))
            .map(str::to_owned),
        access_token: response
            .get("access_token")
            .and_then(Value::as_str)
            .ok_or_else(|| safe_error("南枫云会话缺少访问令牌"))?
            .into(),
        refresh_token: response
            .get("refresh_token")
            .and_then(Value::as_str)
            .ok_or_else(|| safe_error("南枫云会话缺少刷新令牌"))?
            .into(),
        expires_at_epoch_seconds: SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs()
            + response
                .get("expires_in")
                .and_then(Value::as_u64)
                .unwrap_or(3600)
                .clamp(60, 86_400),
    };
    Ok(session)
}

pub fn persist_authenticated_session<C: CredentialStore>(
    connection: &mut Connection,
    credentials: &C,
    session: &Session,
) -> Result<(), String> {
    persist_authenticated_session_with_checkpoint(connection, credentials, session, |_| Ok(()))
}

pub(crate) fn persist_authenticated_session_with_checkpoint<C: CredentialStore>(
    connection: &mut Connection,
    credentials: &C,
    session: &Session,
    checkpoint: impl Fn(&str) -> Result<(), String>,
) -> Result<(), String> {
    let account = sync_state_v1::account_ref(&session.user_id)?;
    let previous = read_session(credentials)?;
    let published = std::cell::Cell::new(false);
    let device_id = connection
        .query_row(
            "SELECT device_id FROM desktop_cloud_account_state ORDER BY updated_at_ms DESC LIMIT 1",
            [],
            |row| row.get::<_, String>(0),
        )
        .optional()
        .ok()
        .flatten()
        .unwrap_or(random_urlsafe(18)?);
    let mut state_store = SqliteMetadataStore { connection };
    let current = state_store.metadata(&account)?.map(|value| value.revision);
    let result = sync_state_v1::authenticate_direct_with_account_update(
        &mut state_store,
        &format!("desktop-google-auth-{}-{}", &account[..12], random_urlsafe(18)?),
        current,
        &session.user_id,
        |transaction| {
            transaction.execute("INSERT INTO desktop_cloud_account_state(account_ref,user_id,email,display_name,avatar_url,state,periodic_enabled,recovery_confirmed,recovery_salt_b64,recovery_generation,last_success_at_ms,last_error_code,device_id,revision,updated_at_ms) VALUES(?1,?2,?3,?4,?5,'READY',0,0,NULL,0,NULL,NULL,?6,1,?7) ON CONFLICT(account_ref) DO UPDATE SET user_id=excluded.user_id,email=excluded.email,display_name=excluded.display_name,avatar_url=excluded.avatar_url,state=CASE WHEN desktop_cloud_account_state.state IN ('SIGNED_OUT','SIGNED_OUT_KEEP_LOCAL','AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION','AUTHENTICATED_NEEDS_RECOVERY_MATERIAL','DIRECTION_REQUIRED') OR desktop_cloud_account_state.last_error_code IN ('RECOVERY_MATERIAL_REQUIRED','KEY_MATERIAL_UNAVAILABLE') THEN 'READY' ELSE desktop_cloud_account_state.state END,last_error_code=CASE WHEN desktop_cloud_account_state.last_error_code IN ('RECOVERY_MATERIAL_REQUIRED','KEY_MATERIAL_UNAVAILABLE') THEN NULL ELSE desktop_cloud_account_state.last_error_code END,revision=desktop_cloud_account_state.revision+1,updated_at_ms=excluded.updated_at_ms", params![account,session.user_id,session.email,session.display_name,session.avatar_url,device_id,now_ms()]).map_err(|_| safe_error("账号状态未保存"))?;
            Ok(())
        },
        || {
            // Both SQL owners have accepted the candidate within the transaction.
            // A failed private-store write rolls back all SQL changes.
            checkpoint("before-session")?;
            save_session(credentials, session)?;
            published.set(true);
            checkpoint("after-session")?;
            Ok(())
        },
    );
    if let Err(error) = result {
        if published.get() {
            // SQL commit failed after publishing. Restore the prior private session;
            // never report a successful login or conceal a failed compensation.
            let restored = match previous.as_ref() {
                Some(previous) => save_session(credentials, previous),
                None => credentials.delete(SESSION_SERVICE, "active"),
            };
            restored.map_err(|_| safe_error("账号提交失败，原会话恢复未完成，请保留本机数据并重新登录"))?;
        }
        return Err(error);
    }
    checkpoint("after-commit")?;
    write_diagnostic(
        state_store.connection,
        Some(&account),
        "OAUTH_CALLBACK",
        "SUCCESS",
        None,
    );
    Ok(())
}

/// Initialize a newly opened data area and retire only the former recovery-key gate.
/// A real remote conflict or unrelated failure remains untouched.
pub fn ensure_current_area_direct_account<C: CredentialStore>(
    connection: &mut Connection,
    credentials: &C,
) -> Result<(), String> {
    let Some(session) = read_session(credentials)? else {
        // A process can stop after the private session deletion but before the
        // sign-out SQL commit. Definite absence is distinct from a denied read
        // (propagated above): cancel local periodic work and pair both owners.
        let accounts: Vec<String> = {
            let mut statement = connection.prepare(
                "SELECT account_ref FROM desktop_cloud_account_state WHERE state NOT IN ('SIGNED_OUT','SIGNED_OUT_KEEP_LOCAL') OR periodic_enabled<>0",
            ).map_err(|_| safe_error("退出恢复状态不可读"))?;
            let rows = statement.query_map([], |row| row.get(0))
                .map_err(|_| safe_error("退出恢复状态不可读"))?;
            rows.collect::<Result<_, _>>().map_err(|_| safe_error("退出恢复状态不可读"))?
        };
        for account in accounts {
            let mut store = SqliteMetadataStore { connection };
            sync_state_v1::sign_out_keep_local_with_account_update(
                &mut store,
                &format!("desktop-signout-reconcile-{}", random_urlsafe(18)?),
                &account,
                |transaction| {
                    transaction.execute("UPDATE desktop_cloud_account_state SET state='SIGNED_OUT_KEEP_LOCAL',periodic_enabled=0,revision=revision+1,updated_at_ms=?2 WHERE account_ref=?1", params![account,now_ms()])
                        .map_err(|_| safe_error("退出恢复状态未保存"))?;
                    Ok(())
                },
                || Ok(()),
            )?;
        }
        return Ok(());
    };
    let account = sync_state_v1::account_ref(&session.user_id)?;
    let row: Option<(String, Option<String>)> = connection
        .query_row(
            "SELECT state,last_error_code FROM desktop_cloud_account_state WHERE account_ref=?1",
            [&account],
            |row| Ok((row.get(0)?, row.get(1)?)),
        )
        .optional()
        .map_err(|_| safe_error("本区域账号状态不可读"))?;
    // The private verified session can have been published just before a
    // process interruption rolled back the SQL transaction. Reconcile only its
    // account/profile projection; unrelated cloud failures and receipts remain.
    let profile: Option<(String, String, Option<String>, Option<String>)> = connection
        .query_row(
            "SELECT user_id,email,display_name,avatar_url FROM desktop_cloud_account_state WHERE account_ref=?1",
            [&account], |row| Ok((row.get(0)?,row.get(1)?,row.get(2)?,row.get(3)?)),
        ).optional().map_err(|_| safe_error("本区域账号资料不可读"))?;
    let profile_changed = profile.as_ref().is_some_and(|profile| {
        profile.0 != session.user_id || profile.1 != session.email
            || profile.2 != session.display_name || profile.3 != session.avatar_url
    });
    let metadata = SqliteMetadataStore { connection }.metadata(&account)?;
    let retired_row = row.as_ref().is_some_and(|(state, error)| {
        matches!(state.as_str(), "SIGNED_OUT" | "SIGNED_OUT_KEEP_LOCAL" | "AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION" | "AUTHENTICATED_NEEDS_RECOVERY_MATERIAL" | "DIRECTION_REQUIRED")
            || matches!(error.as_deref(), Some("RECOVERY_MATERIAL_REQUIRED" | "KEY_MATERIAL_UNAVAILABLE"))
    });
    let retired_metadata = metadata.as_ref().is_some_and(|value| {
        matches!(value.state, sync_state_v1::State::SignedOut | sync_state_v1::State::SignedOutKeepLocal | sync_state_v1::State::NeedsRecoveryConfirmation | sync_state_v1::State::DirectionRequired)
            || (value.state == sync_state_v1::State::Failed && value.last_error.as_deref() == Some("KEY_MATERIAL_UNAVAILABLE"))
    });
    if row.is_none() || metadata.is_none() || retired_row || retired_metadata || profile_changed {
        persist_authenticated_session(connection, credentials, &session)?;
    }
    Ok(())
}

pub fn sign_in_with_system_browser<C: CredentialStore, B: Browser>(
    connection: &mut Connection,
    credentials: &C,
    config: &ServiceConfig,
    browser: &B,
) -> Result<Session, String> {
    let session = authorize_with_system_browser(config, browser)?;
    persist_authenticated_session(connection, credentials, &session)?;
    Ok(session)
}

pub fn create_recovery_code<C: CredentialStore>(
    connection: &Connection,
    credentials: &C,
) -> Result<(RecoveryCodeProjection, PendingRecovery), String> {
    let session = read_session(credentials)?.ok_or_else(|| safe_error("请先登录 Google 账号"))?;
    let account = sync_state_v1::account_ref(&session.user_id)?;
    let setup_allowed = connection
        .query_row(
            "SELECT state='AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION' AND recovery_confirmed=0
             FROM desktop_cloud_account_state WHERE account_ref=?1",
            [&account],
            |row| row.get::<_, bool>(0),
        )
        .optional()
        .map_err(|_| safe_error("账号状态无法读取"))?
        .unwrap_or(false);
    if !setup_allowed {
        return Err(safe_error("当前账号不能新建恢复码"));
    }
    let code = format!(
        "NF-{}-{}-{}-{}",
        random_urlsafe(6)?,
        random_urlsafe(6)?,
        random_urlsafe(6)?,
        random_urlsafe(6)?
    );
    let material = sync_v1::create_account_wrapping_material(&code)
        .map_err(|_| safe_error("恢复码无法创建"))?;
    let confirmation_hash = sha256(code.as_bytes());
    let created_at_ms = now_ms();
    credentials
        .save(PENDING_RECOVERY_SERVICE, &account, &material.wrapping_key)
        .map_err(|_| safe_error("恢复码待确认材料无法安全保存"))?;
    let pending_salt = URL_SAFE_NO_PAD.encode(&material.salt);
    if connection
        .execute(
            "UPDATE desktop_cloud_account_state
         SET pending_recovery_salt_b64=?2,
             pending_recovery_code_hash=?3,
             pending_recovery_created_at_ms=?4,
             revision=revision+1,
             updated_at_ms=?4
         WHERE account_ref=?1
           AND state='AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION'
           AND recovery_confirmed=0",
            params![account, pending_salt, confirmation_hash, created_at_ms],
        )
        .map_err(|_| safe_error("恢复码待确认状态未保存"))?
        != 1
    {
        let _ = credentials.delete(PENDING_RECOVERY_SERVICE, &account);
        return Err(safe_error("当前账号不能新建恢复码"));
    }
    write_diagnostic(
        connection,
        Some(&account),
        "RECOVERY_CREATE",
        "PENDING_CONFIRMATION",
        None,
    );
    Ok((
        RecoveryCodeProjection {
            recovery_code: code,
            confirmation_hash: confirmation_hash.clone(),
        },
        PendingRecovery {
            account_ref: account,
            wrapping_key: material.wrapping_key,
            salt: material.salt,
            code_hash: confirmation_hash,
            created_at_ms,
            rotation: false,
        },
    ))
}

/// Reconstruct a pending acknowledgement after a renderer/native restart. It
/// contains no recovery-code text and expires before it can enable sync.
pub fn load_pending_recovery<C: CredentialStore>(
    connection: &Connection,
    credentials: &C,
    account: &str,
) -> Result<Option<PendingRecovery>, String> {
    let row = connection.query_row(
        "SELECT pending_recovery_salt_b64,pending_recovery_code_hash,pending_recovery_created_at_ms
         FROM desktop_cloud_account_state
         WHERE account_ref=?1
           AND state='AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION'
           AND recovery_confirmed=0",
        [account],
        |row| Ok((row.get::<_, Option<String>>(0)?, row.get::<_, Option<String>>(1)?, row.get::<_, Option<i64>>(2)?)),
    ).optional().map_err(|_| safe_error("恢复码待确认状态无法读取"))?;
    let Some((Some(salt_b64), Some(code_hash), Some(created_at_ms))) = row else {
        return Ok(None);
    };
    if created_at_ms + 10 * 60 * 1000 < now_ms() {
        return Ok(None);
    }
    let mut salt = URL_SAFE_NO_PAD
        .decode(salt_b64)
        .map_err(|_| safe_error("恢复码待确认材料无效"))?;
    if salt.len() != 16
        || code_hash.len() != 64
        || !code_hash.bytes().all(|value| value.is_ascii_hexdigit())
    {
        salt.zeroize();
        return Err(safe_error("恢复码待确认材料无效"));
    }
    let wrapping_key = credentials.read(PENDING_RECOVERY_SERVICE, account)?;
    if wrapping_key.len() != 32 {
        return Err(safe_error("恢复码待确认材料无效"));
    }
    Ok(Some(PendingRecovery {
        account_ref: account.to_owned(),
        wrapping_key,
        salt,
        code_hash,
        created_at_ms,
        rotation: false,
    }))
}

/// One narrow compatibility path for a code that was visibly issued by an
/// older process but whose in-memory pending state was lost.  The caller must
/// actively re-enter that code; it is neither persisted nor logged.
pub fn pending_recovery_from_visible_code(
    account: &str,
    recovery_code: &str,
) -> Result<PendingRecovery, String> {
    let material = sync_v1::create_account_wrapping_material(recovery_code)
        .map_err(|_| safe_error("恢复码格式无效"))?;
    Ok(PendingRecovery {
        account_ref: account.to_owned(),
        wrapping_key: material.wrapping_key,
        salt: material.salt,
        code_hash: sha256(recovery_code.as_bytes()),
        created_at_ms: now_ms(),
        rotation: false,
    })
}

pub fn confirm_recovery<C: CredentialStore>(
    connection: &mut Connection,
    credentials: &C,
    pending: PendingRecovery,
    confirmation_hash: &str,
) -> Result<(), String> {
    if pending.rotation {
        return Err(safe_error("恢复码轮换必须完成云端重加密"));
    }
    if pending.created_at_ms + 10 * 60 * 1000 < now_ms() || pending.code_hash != confirmation_hash {
        return Err(safe_error("恢复码确认已失效，请重新创建"));
    }
    let mut data_key = vec![0u8; 32];
    random_fill(&mut data_key).map_err(|_| safe_error("账号加密密钥无法生成"))?;
    credentials
        .save(DATA_KEY_SERVICE, &pending.account_ref, &data_key)
        .map_err(|_| safe_error("账号加密密钥无法安全保存"))?;
    data_key.zeroize();
    credentials
        .save(
            RECOVERY_SERVICE,
            &pending.account_ref,
            &pending.wrapping_key,
        )
        .map_err(|_| {
            let _ = credentials.delete(DATA_KEY_SERVICE, &pending.account_ref);
            safe_error("恢复保护无法安全保存")
        })?;
    let salt = URL_SAFE_NO_PAD.encode(&pending.salt);
    let current = connection
        .query_row(
            "SELECT recovery_generation FROM desktop_cloud_account_state WHERE account_ref=?1",
            [&pending.account_ref],
            |row| row.get::<_, i64>(0),
        )
        .map_err(|_| safe_error("账号状态不存在"))?;
    connection.execute("UPDATE desktop_cloud_account_state SET recovery_confirmed=1,recovery_salt_b64=?2,recovery_generation=?3,state='DIRECTION_REQUIRED',pending_recovery_salt_b64=NULL,pending_recovery_code_hash=NULL,pending_recovery_created_at_ms=NULL,revision=revision+1,updated_at_ms=?4 WHERE account_ref=?1", params![pending.account_ref,salt,current+1,now_ms()]).map_err(|_| safe_error("恢复保护状态未保存"))?;
    let mut state_store = SqliteMetadataStore { connection };
    if let Some(metadata) = state_store.metadata(&pending.account_ref)? {
        if metadata.state == sync_state_v1::State::NeedsRecoveryConfirmation {
            sync_state_v1::confirm_recovery_saved(
                &mut state_store,
                &format!("desktop-recovery-confirm-{}", now_ms()),
                &pending.account_ref,
                metadata.revision,
            )?;
        }
    }
    write_diagnostic(
        state_store.connection,
        Some(&pending.account_ref),
        "RECOVERY_CONFIRM",
        "SUCCESS",
        None,
    );
    let _ = credentials.delete(PENDING_RECOVERY_SERVICE, &pending.account_ref);
    Ok(())
}

pub fn prepare_custom_recovery_rotation<C: CredentialStore>(
    connection: &Connection,
    credentials: &C,
    code: &str,
) -> Result<(RecoveryCodeProjection, PendingRecovery), String> {
    if code.chars().count() < 12
        || code.len() > 128
        || code.trim() != code
        || code.chars().any(char::is_control)
    {
        return Err(safe_error(
            "恢复码需为 12–128 字节，且不能包含首尾空格或控制字符",
        ));
    }
    let session = read_session(credentials)?.ok_or_else(|| safe_error("请先登录 Google 账号"))?;
    let account = sync_state_v1::account_ref(&session.user_id)?;
    let confirmed: i64 = connection
        .query_row(
            "SELECT recovery_confirmed FROM desktop_cloud_account_state WHERE account_ref=?1",
            [&account],
            |row| row.get(0),
        )
        .map_err(|_| safe_error("账号状态不存在"))?;
    if confirmed != 1
        || credentials.read(RECOVERY_SERVICE, &account).is_err()
        || credentials.read(DATA_KEY_SERVICE, &account).is_err()
    {
        return Err(safe_error("当前设备无法解锁现有云端数据"));
    }
    let material = sync_v1::create_account_wrapping_material(&code)
        .map_err(|_| safe_error("新恢复码无法创建"))?;
    let confirmation_hash = sha256(code.as_bytes());
    write_diagnostic(
        connection,
        Some(&account),
        "RECOVERY_ROTATION_CREATE",
        "PENDING_CONFIRMATION",
        None,
    );
    Ok((
        RecoveryCodeProjection {
            recovery_code: code.to_owned(),
            confirmation_hash: confirmation_hash.clone(),
        },
        PendingRecovery {
            account_ref: account,
            wrapping_key: material.wrapping_key,
            salt: material.salt,
            code_hash: confirmation_hash,
            created_at_ms: now_ms(),
            rotation: true,
        },
    ))
}

fn wrapping_material<C: CredentialStore>(
    connection: &Connection,
    credentials: &C,
    account: &str,
) -> Result<AccountWrappingMaterial, String> {
    let key = credentials
        .read(RECOVERY_SERVICE, account)
        .map_err(|_| safe_error("恢复保护材料不可用"))?;
    let salt: String = connection.query_row("SELECT recovery_salt_b64 FROM desktop_cloud_account_state WHERE account_ref=?1 AND recovery_confirmed=1", [account], |row| row.get(0)).map_err(|_| safe_error("恢复保护尚未确认"))?;
    let salt = URL_SAFE_NO_PAD
        .decode(salt)
        .map_err(|_| safe_error("恢复保护材料无效"))?;
    if key.len() != 32 || salt.len() != 16 {
        return Err(safe_error("恢复保护材料无效"));
    }
    Ok(AccountWrappingMaterial {
        wrapping_key: key,
        salt,
    })
}

/// A persisted `recovery_confirmed` bit only says that this account was once
/// connected.  It must never be used as evidence that this particular install
/// still has both private materials required to encrypt another document.
///
/// This matters after retiring a system credential owner: the safe path is to
/// ask for the already-existing recovery code and validate it against a cloud
/// document, not to create a different code or attempt an upload that cannot
/// be decrypted later.
fn private_recovery_material_present<C: CredentialStore>(credentials: &C, account: &str) -> bool {
    match credentials.read(RECOVERY_SERVICE, account) {
        Ok(mut value) => {
            value.zeroize();
            true
        }
        Err(_) => false,
    }
}

fn private_sync_materials_present<C: CredentialStore>(credentials: &C, account: &str) -> bool {
    for service in [RECOVERY_SERVICE, DATA_KEY_SERVICE] {
        match credentials.read(service, account) {
            Ok(mut value) => value.zeroize(),
            Err(_) => return false,
        }
    }
    true
}

/// A legacy confirmation build wrote only the wrapping material.  It never
/// uploaded a document, but it left a confirmed account unable to select its
/// first direction.  Repair only that exact, receipt-free direction state.
fn ensure_initial_data_key_for_direction<C: CredentialStore>(
    connection: &Connection,
    credentials: &C,
    account: &str,
) -> Result<(), String> {
    match credentials.read(DATA_KEY_SERVICE, account) {
        Ok(mut existing) => {
            existing.zeroize();
            return Ok(());
        }
        Err(_) => {}
    }
    let allowed = connection
        .query_row(
            "SELECT recovery_confirmed=1 AND state='DIRECTION_REQUIRED'
             FROM desktop_cloud_account_state WHERE account_ref=?1",
            [account],
            |row| row.get::<_, bool>(0),
        )
        .optional()
        .map_err(|_| safe_error("账号同步状态无法读取"))?
        .unwrap_or(false);
    let receipts: u64 = connection
        .query_row(
            "SELECT COUNT(*) FROM desktop_selected_conversation_sync WHERE account_ref=?1",
            [account],
            |row| row.get(0),
        )
        .map_err(|_| safe_error("本机同步回执无法读取"))?;
    if !allowed || receipts != 0 || !private_recovery_material_present(credentials, account) {
        return Err(safe_error("账号加密材料不可用"));
    }
    let mut data_key = vec![0u8; 32];
    random_fill(&mut data_key).map_err(|_| safe_error("账号加密密钥无法生成"))?;
    let saved = credentials.save(DATA_KEY_SERVICE, account, &data_key);
    data_key.zeroize();
    saved.map_err(|_| safe_error("账号加密密钥无法安全保存"))?;
    write_diagnostic(
        connection,
        Some(account),
        "RECOVERY_DATA_KEY_REPAIR",
        "SUCCESS",
        None,
    );
    Ok(())
}

fn require_private_sync_materials<C: CredentialStore>(
    connection: &Connection,
    credentials: &C,
    account: &str,
) -> Result<(), String> {
    let confirmed = connection
        .query_row(
            "SELECT recovery_confirmed FROM desktop_cloud_account_state WHERE account_ref=?1",
            [account],
            |row| row.get::<_, i64>(0),
        )
        .optional()
        .map_err(|_| safe_error("账号状态无法读取"))?
        .unwrap_or(0)
        == 1;
    if !confirmed {
        return Err(safe_error("请先确认恢复码"));
    }
    if private_sync_materials_present(credentials, account) {
        return Ok(());
    }

    // This is a local safety transition only: it neither reads old system
    // credentials nor touches remote documents.  Disabling the timer prevents
    // repeated background failures while the user reconnects this device with
    // their existing recovery code.
    let _ = connection.execute(
        "UPDATE desktop_cloud_account_state
         SET state='AUTHENTICATED_NEEDS_RECOVERY_MATERIAL', periodic_enabled=0,
             last_error_code='RECOVERY_MATERIAL_REQUIRED', revision=revision+1,
             updated_at_ms=?2
         WHERE account_ref=?1",
        params![account, now_ms()],
    );
    write_diagnostic(
        connection,
        Some(account),
        "RECOVERY_MATERIAL",
        "RECONNECT_REQUIRED",
        Some("RECOVERY_MATERIAL_REQUIRED"),
    );
    Err(safe_error(
        "请先在“使用手机端已有恢复码”中验证原恢复码，再同步",
    ))
}

#[derive(Debug, Clone)]
pub struct RemoteEnvelope {
    pub revision: u64,
    pub payload_hash: String,
    pub envelope: String,
}
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CloudFailure {
    Known,
    ServerUnavailable,
    UnexpectedHttpStatus(u16),
    InvalidEnvelope,
    StaleRevision,
    RpcContractRejected,
    CloudValidatorFailure,
    Unauthorized,
    NotFound,
    RateLimited,
    RequestRejected,
    MalformedResponse,
    UnknownCommit,
}

fn cloud_failure_code(failure: CloudFailure) -> String {
    match failure {
        CloudFailure::Unauthorized => "REMOTE_AUTH_REJECTED".into(),
        CloudFailure::NotFound => "REMOTE_RPC_UNAVAILABLE".into(),
        CloudFailure::RateLimited => "REMOTE_RATE_LIMITED".into(),
        CloudFailure::RequestRejected => "REMOTE_REQUEST_REJECTED".into(),
        CloudFailure::MalformedResponse => "REMOTE_RESPONSE_INVALID".into(),
        CloudFailure::Known => "REMOTE_CONNECTION_FAILED".into(),
        CloudFailure::ServerUnavailable => "REMOTE_SERVER_UNAVAILABLE".into(),
        CloudFailure::UnexpectedHttpStatus(status) => format!("REMOTE_HTTP_{status}"),
        CloudFailure::InvalidEnvelope => "REMOTE_ENVELOPE_REJECTED".into(),
        CloudFailure::StaleRevision => "REMOTE_STALE_REVISION".into(),
        CloudFailure::RpcContractRejected => "REMOTE_RPC_CONTRACT_REJECTED".into(),
        CloudFailure::CloudValidatorFailure => "REMOTE_CLOUD_VALIDATOR_FAILED".into(),
        CloudFailure::UnknownCommit => "COMMIT_RESULT_UNKNOWN".into(),
    }
}

fn cloud_read_failure_message(failure: CloudFailure) -> &'static str {
    match failure {
        CloudFailure::Unauthorized => "登录已失效，请重新登录后再同步",
        CloudFailure::NotFound => "南枫云同步服务暂不可用，请稍后重试",
        CloudFailure::RateLimited => "同步请求过于频繁，请稍后重试",
        CloudFailure::RequestRejected => "南枫云拒绝了本次读取；本机和云端内容均未改动",
        CloudFailure::MalformedResponse => "南枫云返回格式不兼容，已停止同步",
        CloudFailure::Known => "南枫云连接未完成，请检查网络后重试",
        CloudFailure::ServerUnavailable => "南枫云服务端暂时不可用，本机和云端内容均未改动",
        CloudFailure::UnexpectedHttpStatus(_) => {
            "南枫云返回了未支持的服务状态，本机和云端内容均未改动"
        }
        CloudFailure::InvalidEnvelope => "本机同步内容未通过南枫云校验，本机和云端内容均未改动",
        CloudFailure::StaleRevision => "云端版本已变化，本机和云端内容均未改动",
        CloudFailure::RpcContractRejected => "南枫云同步接口版本未刷新，本机和云端内容均未改动",
        CloudFailure::CloudValidatorFailure => "南枫云同步校验服务异常，本机和云端内容均未改动",
        CloudFailure::UnknownCommit => "云端状态未知，已停止自动重试",
    }
}

fn cloud_commit_failure_message(failure: CloudFailure) -> &'static str {
    match failure {
        CloudFailure::Unauthorized => "登录已失效，请重新登录后再同步",
        CloudFailure::NotFound => "南枫云同步服务暂不可用，请稍后重试",
        CloudFailure::RateLimited => "同步请求过于频繁，请稍后重试",
        CloudFailure::RequestRejected => "南枫云拒绝了本次提交；本机和云端内容均未改动",
        CloudFailure::MalformedResponse => "南枫云返回格式不兼容，已停止同步",
        CloudFailure::Known => "南枫云连接未完成，请检查网络后重试",
        CloudFailure::ServerUnavailable => "南枫云服务端暂时不可用，本机和云端内容均未改动",
        CloudFailure::UnexpectedHttpStatus(_) => {
            "南枫云返回了未支持的服务状态，本机和云端内容均未改动"
        }
        CloudFailure::InvalidEnvelope => "本机同步内容未通过南枫云校验，本机和云端内容均未改动",
        CloudFailure::StaleRevision => "云端版本已变化，本机和云端内容均未改动",
        CloudFailure::RpcContractRejected => "南枫云同步接口版本未刷新，本机和云端内容均未改动",
        CloudFailure::CloudValidatorFailure => "南枫云同步校验服务异常，本机和云端内容均未改动",
        CloudFailure::UnknownCommit => "云端状态未知，已停止自动重试",
    }
}

/// Only protocol sentinels and bounded provider codes are inspected.  The server body
/// is deliberately discarded: it can never become a user message, diagnostic, or log.
fn classify_rejected_rpc_response(body: &str) -> CloudFailure {
    let provider_code = serde_json::from_str::<Value>(body)
        .ok()
        .and_then(|value| value.get("code").and_then(Value::as_str).map(str::to_owned))
        .filter(|value| {
            !value.is_empty()
                && value.len() <= 24
                && value
                    .bytes()
                    .all(|byte| byte.is_ascii_uppercase() || byte.is_ascii_digit() || byte == b'_')
        });
    if body.contains("NFAI_SYNC_INVALID_ENVELOPE") {
        CloudFailure::InvalidEnvelope
    } else if body.contains("NFAI_SYNC_STALE_REVISION") {
        CloudFailure::StaleRevision
    } else if provider_code.as_deref() == Some("2201B") {
        CloudFailure::CloudValidatorFailure
    } else if provider_code
        .as_deref()
        .is_some_and(|code| code.starts_with("PGRST"))
    {
        CloudFailure::RpcContractRejected
    } else {
        CloudFailure::RequestRejected
    }
}
pub trait CloudGateway {
    fn read(&self, document_id: &str) -> Result<Option<RemoteEnvelope>, CloudFailure>;
    fn commit(
        &self,
        document_id: &str,
        expected_revision: u64,
        envelope: &str,
    ) -> Result<(u64, String), CloudFailure>;
    fn list(&self) -> Result<Vec<RemoteEnvelope>, CloudFailure> {
        Err(CloudFailure::Known)
    }
    fn delete(&self, _document_id: &str, _expected_revision: u64) -> Result<bool, CloudFailure> {
        Err(CloudFailure::Known)
    }
}

// This ID is a tiny account-level cloud-list projection shared with Android.
// It never represents a conversation and must therefore never enter the
// normal conversation restore path.
const CLOUD_LIST_PRESENTATION_DOCUMENT_ID: &str = "cloud-conversation-list-v1";
const WORK_CLOUD_LIST_DOCUMENT_ID: &str = "cloud-work-conversation-list-v1";

fn cloud_list_document_id(connection: &Connection) -> Result<&'static str, String> {
    Ok(match desktop_data_area::read_area(connection)? {
        crate::DesktopDataArea::Chat => CLOUD_LIST_PRESENTATION_DOCUMENT_ID,
        crate::DesktopDataArea::Work => WORK_CLOUD_LIST_DOCUMENT_ID,
    })
}

fn selected_document_id(
    connection: &Connection,
    account: &str,
    workspace_id: &str,
    conversation_id: &str,
) -> Result<String, String> {
    let stored = connection
        .query_row(
            "SELECT document_id FROM desktop_selected_conversation_sync WHERE account_ref=?1 AND workspace_id=?2 AND conversation_id=?3",
            params![account, workspace_id, conversation_id],
            |row| row.get::<_, String>(0),
        )
        .optional()
        .map_err(|_| safe_error("同步身份无法读取"))?;
    Ok(stored.unwrap_or(desktop_data_area::document_id(
        desktop_data_area::read_area(connection)?,
        conversation_id,
    )))
}

/// One cloud operation keeps the identity that supplied its access token.
/// Later owners cannot silently pair that gateway with a replacement session.
#[derive(Clone)]
pub struct BoundAccountCredentialStore<C = AppPrivateAccountCredentialStore> {
    inner: C,
    generation: Arc<Mutex<u64>>,
    expected_generation: u64,
    user_id: String,
    access_token: Zeroizing<String>,
    refresh_token: Zeroizing<String>,
}
impl<C: CredentialStore> BoundAccountCredentialStore<C> {
    pub fn new(inner: C, generation: Arc<Mutex<u64>>,
        expected_generation: u64, session: &Session) -> Result<Self, String> {
        let bound = Self { inner, generation, expected_generation, user_id: session.user_id.clone(),
            access_token: Zeroizing::new(session.access_token.clone()),
            refresh_token: Zeroizing::new(session.refresh_token.clone()) };
        bound.require_current()?;
        Ok(bound)
    }
    fn check_locked(&self, generation: u64) -> Result<(), String> {
        let current = read_session(&self.inner)?;
        self.check_session(generation,current.as_ref())
    }
    fn check_session(&self, generation: u64, current: Option<&Session>) -> Result<(), String> {
        if generation != self.expected_generation || !current.is_some_and(|session|
            session.user_id == self.user_id && session.access_token == *self.access_token
                && session.refresh_token == *self.refresh_token) {
            return Err(safe_error("登录状态已变化，已停止旧账号云端操作"));
        }
        Ok(())
    }
    pub(crate) fn require_current(&self) -> Result<(), String> {
        let generation = self.generation.lock().map_err(|_| safe_error("账号操作状态不可用"))?;
        self.check_locked(*generation)
    }
    fn access<T>(&self, operation: impl FnOnce(&C) -> Result<T,String>) -> Result<T,String> {
        self.with_account_write(|| operation(&self.inner))
    }
}
impl<C: CredentialStore> CredentialStore for BoundAccountCredentialStore<C> {
    fn prepared_account_write_user_id(&self) -> Option<&str> { Some(&self.user_id) }
    fn with_account_write<T>(&self, write: impl FnOnce() -> Result<T,String>) -> Result<T,String> {
        let generation = self.generation.lock().map_err(|_| safe_error("账号操作状态不可用"))?;
        self.check_locked(*generation)?;
        write()
    }
    fn save(&self,s:&str,a:&str,v:&[u8])->Result<(),String>{self.access(|inner|inner.save(s,a,v))}
    fn read(&self,s:&str,a:&str)->Result<Vec<u8>,String>{self.access(|inner|inner.read(s,a))}
    fn read_optional(&self,s:&str,a:&str)->Result<Option<Vec<u8>>,String>{
        if s != SESSION_SERVICE || a != "active" {
            return self.access(|inner|inner.read_optional(s,a));
        }
        let generation=self.generation.lock().map_err(|_|safe_error("账号操作状态不可用"))?;
        // Validate the exact bytes being returned while retaining the generation
        // lock. Every call reads fresh private storage; no session is cached.
        let mut encoded=self.inner.read_optional(s,a)?;
        let checked=(|| {
            let current=encoded.as_deref().map(|bytes|serde_json::from_slice::<Session>(bytes)
                .map_err(|_|safe_error("账号会话无法读取"))).transpose()?;
            self.check_session(*generation,current.as_ref())
        })();
        if let Err(error)=checked {
            if let Some(bytes)=encoded.as_mut(){bytes.zeroize();}
            return Err(error);
        }
        Ok(encoded)
    }
    fn delete(&self,s:&str,a:&str)->Result<(),String>{self.access(|inner|inner.delete(s,a))}
}

fn require_same_session<C: CredentialStore>(credentials: &C, expected: &Session) -> Result<(),String> {
    let current = read_session(credentials)?;
    if !current.as_ref().is_some_and(|session| session.user_id == expected.user_id
        && session.access_token == expected.access_token && session.refresh_token == expected.refresh_token) {
        return Err(safe_error("登录状态已变化，未应用旧账号云端结果"));
    }
    Ok(())
}

pub struct SupabaseGateway {
    client: Client,
    config: ServiceConfig,
    access_token: Zeroizing<String>,
    credentials: Option<BoundAccountCredentialStore>,
}
impl SupabaseGateway {
    pub fn new(config: ServiceConfig, session: &Session) -> Result<Self, String> {
        Ok(Self {
            client: Client::builder()
                .redirect(Policy::none())
                .timeout(Duration::from_secs(30))
                .build()
                .map_err(|_| safe_error("南枫云客户端不可用"))?,
            config,
            access_token: Zeroizing::new(session.access_token.clone()),
            credentials: None,
        })
    }
    pub fn new_bound(config: ServiceConfig, session: &Session, credentials: BoundAccountCredentialStore) -> Result<Self,String> {
        credentials.require_current()?;
        let mut gateway = Self::new(config, session)?;
        gateway.credentials = Some(credentials);
        Ok(gateway)
    }
    fn require_current(&self) -> Result<(),CloudFailure> {
        if let Some(credentials) = self.credentials.as_ref() {
            credentials.require_current().map_err(|_| CloudFailure::Unauthorized)?;
        }
        Ok(())
    }
    fn rpc(&self, name: &str, body: Value) -> Result<Value, CloudFailure> {
        self.rpc_with_limit(name, body, None)
    }
    fn rpc_with_limit(&self, name: &str, body: Value, max_response_bytes: Option<usize>) -> Result<Value, CloudFailure> {
        self.require_current()?;
        let url = self
            .config
            .supabase_url
            .join(&format!("rest/v1/rpc/{name}"))
            .map_err(|_| CloudFailure::Known)?;
        let response = self
            .client
            .post(url)
            .header("apikey", &self.config.publishable_key)
            .bearer_auth(self.access_token.as_str())
            .json(&body)
            .send()
            .map_err(|_| CloudFailure::Known)?;
        let status = response.status();
        if !status.is_success() {
            return Err(match status.as_u16() {
                401 | 403 => CloudFailure::Unauthorized,
                404 => CloudFailure::NotFound,
                429 => CloudFailure::RateLimited,
                400 | 409 | 422 => {
                    let safe_body = response.text().unwrap_or_default();
                    classify_rejected_rpc_response(&safe_body)
                }
                500..=599 => CloudFailure::ServerUnavailable,
                status => CloudFailure::UnexpectedHttpStatus(status),
            });
        }
        if let Some(limit) = max_response_bytes {
            if response.content_length().is_some_and(|length| length > limit as u64) {
                return Err(CloudFailure::MalformedResponse);
            }
            let mut bytes = Vec::new();
            response.take(limit as u64 + 1).read_to_end(&mut bytes)
                .map_err(|_| CloudFailure::MalformedResponse)?;
            if bytes.len() > limit { return Err(CloudFailure::MalformedResponse); }
            self.require_current()?;
            return serde_json::from_slice(&bytes).map_err(|_| CloudFailure::MalformedResponse);
        }
        let value = response.json().map_err(|_| CloudFailure::MalformedResponse)?;
        self.require_current()?;
        Ok(value)
    }
}
fn parse_sync_inventory(response: &Value) -> Result<Vec<RemoteEnvelope>, CloudFailure> {
    let rows = response.get("documents").and_then(Value::as_array)
        .ok_or(CloudFailure::MalformedResponse)?;
    if response.get("documentCount").and_then(Value::as_u64) != Some(rows.len() as u64)
        || rows.len() > SYNC_INVENTORY_MAX_DOCUMENTS {
        return Err(CloudFailure::MalformedResponse);
    }
    Ok(rows.iter().map(|row| {
        // Keep valid siblings even when one document is malformed. Its missing
        // identity also prevents the list from pruning local selected receipts.
        let envelope = match row.get("envelope") {
            Some(Value::String(raw)) => raw.clone(),
            Some(value) => value.to_string(),
            None => "{}".into(),
        };
        let root = serde_json::from_str::<Value>(&envelope).unwrap_or(Value::Null);
        RemoteEnvelope {
            revision: root.get("revision").and_then(Value::as_u64).unwrap_or(0),
            payload_hash: root.get("payloadHash").and_then(Value::as_str).unwrap_or("").into(),
            envelope,
        }
    }).collect())
}
impl CloudGateway for SupabaseGateway {
    fn list(&self) -> Result<Vec<RemoteEnvelope>, CloudFailure> {
        let response = self.rpc_with_limit(
            "nanfeng_sync_inventory", json!({"p_app_id":APP_ID}), Some(SYNC_INVENTORY_MAX_BYTES),
        )?;
        parse_sync_inventory(&response)
    }
    fn read(&self, document_id: &str) -> Result<Option<RemoteEnvelope>, CloudFailure> {
        let response = self.rpc(
            "nanfeng_sync_read_document",
            json!({"p_app_id":APP_ID,"p_document_id":document_id}),
        )?;
        // PostgREST serializes a SECURITY DEFINER function returning no rows as
        // `[]`. That is the expected first-sync state, not a malformed cloud
        // response. Any non-empty array remains a single row contract.
        let value = match &response {
            Value::Array(items) if items.is_empty() => return Ok(None),
            Value::Array(items) => items.first().ok_or(CloudFailure::MalformedResponse)?,
            _ => &response,
        };
        if value.get("missing").and_then(Value::as_bool) == Some(true) || value.is_null() {
            return Ok(None);
        }
        let envelope = match value
            .get("envelope")
            .cloned()
            .ok_or(CloudFailure::MalformedResponse)?
        {
            Value::String(value) => value,
            value => serde_json::to_string(&value).map_err(|_| CloudFailure::MalformedResponse)?,
        };
        let root: Value =
            serde_json::from_str(&envelope).map_err(|_| CloudFailure::MalformedResponse)?;
        Ok(Some(RemoteEnvelope {
            revision: root
                .get("revision")
                .and_then(Value::as_u64)
                .ok_or(CloudFailure::MalformedResponse)?,
            payload_hash: root
                .get("payloadHash")
                .and_then(Value::as_str)
                .ok_or(CloudFailure::MalformedResponse)?
                .into(),
            envelope,
        }))
    }
    fn commit(
        &self,
        document_id: &str,
        expected_revision: u64,
        envelope: &str,
    ) -> Result<(u64, String), CloudFailure> {
        self.require_current()?;
        let parsed: Value = serde_json::from_str(envelope).map_err(|_| CloudFailure::Known)?;
        let url = self
            .config
            .supabase_url
            .join("rest/v1/rpc/nanfeng_sync_commit_document")
            .map_err(|_| CloudFailure::Known)?;
        let response = self.client.post(url).header("apikey", &self.config.publishable_key).bearer_auth(self.access_token.as_str()).json(&json!({"p_app_id":APP_ID,"p_document_id":document_id,"p_expected_revision":expected_revision,"p_envelope":parsed})).send().map_err(|_| CloudFailure::UnknownCommit)?;
        let status = response.status();
        if !status.is_success() {
            return Err(match status.as_u16() {
                401 | 403 => CloudFailure::Unauthorized,
                404 => CloudFailure::NotFound,
                429 => CloudFailure::RateLimited,
                400 | 409 | 422 => {
                    let safe_body = response.text().unwrap_or_default();
                    classify_rejected_rpc_response(&safe_body)
                }
                500..=599 => CloudFailure::ServerUnavailable,
                status => CloudFailure::UnexpectedHttpStatus(status),
            });
        }
        let value: Value = response
            .json()
            .map_err(|_| CloudFailure::MalformedResponse)?;
        self.require_current().map_err(|_| CloudFailure::UnknownCommit)?;
        let value = value
            .as_array()
            .and_then(|items| items.first())
            .unwrap_or(&value);
        Ok((
            value
                .get("revision")
                .and_then(Value::as_u64)
                .ok_or(CloudFailure::Known)?,
            value
                .get("payload_hash")
                .and_then(Value::as_str)
                .ok_or(CloudFailure::Known)?
                .into(),
        ))
    }
    fn delete(&self, document_id: &str, expected_revision: u64) -> Result<bool, CloudFailure> {
        let response = self.rpc(
            "nanfeng_sync_delete_document",
            json!({
                "p_app_id": APP_ID,
                "p_document_id": document_id,
                "p_expected_revision": expected_revision,
            }),
        )?;
        response
            .as_array()
            .and_then(|items| items.first())
            .and_then(|item| item.get("deleted"))
            .and_then(Value::as_bool)
            .ok_or(CloudFailure::MalformedResponse)
    }
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RotationReceipt {
    pub status: String,
    pub completed_documents: u64,
    pub total_documents: u64,
    pub safe_code: Option<String>,
}

struct EnvelopePublicIdentity {
    document_id:Result<String,String>,
    format:Option<String>,
}

fn envelope_public_identity(envelope:&str)->EnvelopePublicIdentity {
    #[cfg(test)] let _phase=RestorePhaseTimer::start("identity");
    let Ok(value)=serde_json::from_str::<Value>(envelope) else {
        return EnvelopePublicIdentity {document_id:Err(safe_error("云端文档标识无效")),format:None};
    };
    let document_id = value.get("documentId").and_then(Value::as_str)
        .filter(|value| (value.starts_with("conversation-") && value.len() <= 64)
            || matches!(*value, CLOUD_LIST_PRESENTATION_DOCUMENT_ID | WORK_CLOUD_LIST_DOCUMENT_ID))
        .map(str::to_owned).ok_or_else(|| safe_error("云端文档标识无效"));
    let format = value.get("format").and_then(Value::as_str).map(str::to_owned);
    // A bad ID must not erase a recognizable legacy format: the original
    // migration refusal/diagnostic path still owns that independent failure.
    EnvelopePublicIdentity {document_id,format}
}

fn envelope_document_identity(envelope: &str) -> Result<(String, Option<String>), String> {
    let identity=envelope_public_identity(envelope);
    identity.document_id.map(|id|(id,identity.format))
}

fn envelope_document_id(envelope: &str) -> Result<String, String> {
    envelope_document_identity(envelope).map(|(document_id, _)| document_id)
}

pub fn rotate_recovery_material<C: CredentialStore, G: CloudGateway>(
    connection: &mut Connection,
    credentials: &C,
    gateway: &G,
    pending: Option<PendingRecovery>,
    confirmation_hash: Option<&str>,
) -> Result<RotationReceipt, String> {
    let session = read_session(credentials)?.ok_or_else(|| safe_error("请先登录 Google 账号"))?;
    let account = sync_state_v1::account_ref(&session.user_id)?;
    let old_material = wrapping_material(connection, credentials, &account)?;
    let current_generation: i64 = connection.query_row(
        "SELECT recovery_generation FROM desktop_cloud_account_state WHERE account_ref=?1 AND recovery_confirmed=1",
        [&account],
        |row| row.get(0),
    ).map_err(|_| safe_error("现有恢复保护不可用"))?;
    let (new_material, generation) = if let Some(pending) = pending {
        if !pending.rotation
            || pending.account_ref != account
            || pending.created_at_ms + 10 * 60 * 1000 < now_ms()
            || confirmation_hash != Some(pending.code_hash.as_str())
        {
            return Err(safe_error("新恢复码确认已失效，请重新创建"));
        }
        let generation = current_generation + 1;
        credentials
            .save(RECOVERY_ROTATION_SERVICE, &account, &pending.wrapping_key)
            .map_err(|_| safe_error("新恢复保护无法安全暂存"))?;
        let salt = URL_SAFE_NO_PAD.encode(&pending.salt);
        connection.execute(
            "INSERT INTO desktop_recovery_rotation(account_ref,new_salt_b64,generation,stage,completed_documents,total_documents,safe_error_code,updated_at_ms) VALUES(?1,?2,?3,'REWRAPPING',0,0,NULL,?4) ON CONFLICT(account_ref) DO UPDATE SET new_salt_b64=excluded.new_salt_b64,generation=excluded.generation,stage='REWRAPPING',completed_documents=0,total_documents=0,safe_error_code=NULL,updated_at_ms=excluded.updated_at_ms",
            params![account, salt, generation, now_ms()],
        ).map_err(|_| safe_error("恢复码轮换记录未保存"))?;
        (
            AccountWrappingMaterial {
                wrapping_key: pending.wrapping_key.clone(),
                salt: pending.salt.clone(),
            },
            generation,
        )
    } else {
        let (salt, generation): (String, i64) = connection.query_row(
            "SELECT new_salt_b64,generation FROM desktop_recovery_rotation WHERE account_ref=?1",
            [&account],
            |row| Ok((row.get(0)?, row.get(1)?)),
        ).map_err(|_| safe_error("没有可继续的恢复码轮换"))?;
        let salt = URL_SAFE_NO_PAD
            .decode(salt)
            .map_err(|_| safe_error("恢复码轮换记录无效"))?;
        let wrapping_key = credentials
            .read(RECOVERY_ROTATION_SERVICE, &account)
            .map_err(|_| safe_error("新恢复保护无法解锁"))?;
        (AccountWrappingMaterial { wrapping_key, salt }, generation)
    };
    let documents = connection.prepare(
        "SELECT document_id,remote_revision,payload_hash FROM desktop_selected_conversation_sync WHERE account_ref=?1 ORDER BY document_id"
    ).map_err(|_| safe_error("已选同步文档无法读取"))?
        .query_map([&account], |row| Ok((row.get::<_,String>(0)?,row.get::<_,u64>(1)?,row.get::<_,String>(2)?)))
        .map_err(|_| safe_error("已选同步文档无法读取"))?
        .collect::<Result<Vec<_>,_>>().map_err(|_| safe_error("已选同步文档无效"))?;
    let total = documents.len() as u64;
    connection.execute("UPDATE desktop_recovery_rotation SET total_documents=?2,updated_at_ms=?3 WHERE account_ref=?1", params![account,total,now_ms()]).ok();
    let data_key = Zeroizing::new(
        credentials
            .read(DATA_KEY_SERVICE, &account)
            .map_err(|_| safe_error("账号加密密钥不可用"))?,
    );
    let mut completed = 0u64;
    for (document_id, known_revision, known_hash) in documents {
        let remote = gateway
            .read(&document_id)
            .map_err(|_| safe_error("恢复码轮换无法读取云端文档"))?
            .ok_or_else(|| safe_error("恢复码轮换发现云端文档缺失"))?;
        if sync_v1::open_with_account_wrapping_material(
            &remote.envelope,
            &new_material,
            APP_ID,
            &document_id,
            remote.revision,
        )
        .is_ok()
        {
            completed += 1;
            continue;
        }
        if remote.revision != known_revision || remote.payload_hash != known_hash {
            connection.execute("UPDATE desktop_recovery_rotation SET stage='CONFLICT',completed_documents=?2,safe_error_code='REMOTE_CHANGED',updated_at_ms=?3 WHERE account_ref=?1",params![account,completed,now_ms()]).ok();
            write_notification(
                connection,
                Some(&account),
                "CONFLICT",
                "恢复码轮换期间云端版本变化，已停止。",
            );
            return Ok(RotationReceipt {
                status: "CONFLICT".into(),
                completed_documents: completed,
                total_documents: total,
                safe_code: Some("REMOTE_CHANGED".into()),
            });
        }
        let mut payload = sync_v1::open_with_account_wrapping_material(
            &remote.envelope,
            &old_material,
            APP_ID,
            &document_id,
            remote.revision,
        )
        .map_err(|_| safe_error("现有恢复保护无法打开云端文档"))?
        .payload;
        payload["revision"] = Value::Number((remote.revision + 1).into());
        let envelope = sync_v1::seal_with_account_wrapping_material(
            payload,
            data_key.as_slice(),
            &new_material,
        )
        .map_err(|_| safe_error("云端文档无法使用新恢复码重加密"))?;
        let expected_hash = serde_json::from_str::<Value>(&envelope)
            .ok()
            .and_then(|value| {
                value
                    .get("payloadHash")
                    .and_then(Value::as_str)
                    .map(str::to_owned)
            })
            .ok_or_else(|| safe_error("重加密完整性无效"))?;
        let committed = match gateway.commit(&document_id, remote.revision, &envelope) {
            Ok(value) => value,
            Err(failure) => {
                let (stage, code) = if failure == CloudFailure::UnknownCommit {
                    ("UNKNOWN", "COMMIT_RESULT_UNKNOWN")
                } else {
                    ("RETRY_WAIT", "NETWORK_OR_REMOTE_REJECTED")
                };
                connection.execute("UPDATE desktop_recovery_rotation SET stage=?2,completed_documents=?3,safe_error_code=?4,updated_at_ms=?5 WHERE account_ref=?1",params![account,stage,completed,code,now_ms()]).ok();
                return Ok(RotationReceipt {
                    status: stage.into(),
                    completed_documents: completed,
                    total_documents: total,
                    safe_code: Some(code.into()),
                });
            }
        };
        let readback = gateway
            .read(&document_id)
            .map_err(|_| safe_error("新恢复保护的云端回读失败"))?
            .ok_or_else(|| safe_error("新恢复保护的云端回读不存在"))?;
        if committed.0 != remote.revision + 1
            || committed.1 != expected_hash
            || readback.payload_hash != expected_hash
            || readback.revision != committed.0
        {
            return Err(safe_error("新恢复保护的云端回读不一致"));
        }
        connection.execute("UPDATE desktop_selected_conversation_sync SET remote_revision=?4,payload_hash=?5,last_synced_at_ms=?6 WHERE account_ref=?1 AND document_id=?2 AND remote_revision=?3", params![account,document_id,known_revision,readback.revision,expected_hash,now_ms()]).map_err(|_| safe_error("恢复码轮换回执未保存"))?;
        completed += 1;
        connection.execute("UPDATE desktop_recovery_rotation SET stage='REWRAPPING',completed_documents=?2,safe_error_code=NULL,updated_at_ms=?3 WHERE account_ref=?1",params![account,completed,now_ms()]).ok();
    }
    credentials
        .save(RECOVERY_SERVICE, &account, &new_material.wrapping_key)
        .map_err(|_| safe_error("新恢复保护无法激活"))?;
    let transaction = connection
        .transaction()
        .map_err(|_| safe_error("恢复码轮换 transaction 无法开启"))?;
    let promote = transaction.execute("UPDATE desktop_cloud_account_state SET recovery_salt_b64=?2,recovery_generation=?3,state='READY',last_error_code=NULL,revision=revision+1,updated_at_ms=?4 WHERE account_ref=?1",params![account,URL_SAFE_NO_PAD.encode(&new_material.salt),generation,now_ms()])
        .and_then(|_| transaction.execute("DELETE FROM desktop_recovery_rotation WHERE account_ref=?1",[&account]))
        .and_then(|_| transaction.commit());
    if promote.is_err() {
        let _ = credentials.save(RECOVERY_SERVICE, &account, &old_material.wrapping_key);
        return Err(safe_error("新恢复保护未原子激活"));
    }
    let _ = credentials.delete(RECOVERY_ROTATION_SERVICE, &account);
    write_diagnostic(
        connection,
        Some(&account),
        "RECOVERY_ROTATION",
        "SUCCESS",
        None,
    );
    write_notification(
        connection,
        Some(&account),
        "SUCCESS",
        "恢复码已更换，所有已选云端对话已重加密。",
    );
    Ok(RotationReceipt {
        status: "ROTATED".into(),
        completed_documents: completed,
        total_documents: total,
        safe_code: None,
    })
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RemoteDocumentProjection {
    pub document_id: String,
    pub title: String,
    pub revision: u64,
    pub payload_hash_prefix: String,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RestoreReceipt {
    pub status: String,
    pub workspace_id: String,
    pub conversation_id: String,
    pub title: String,
    pub remote_revision: u64,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RestoreAllReceipt {
    pub restored: Vec<RestoreReceipt>,
    pub failed_count: usize,
    pub failure_reasons: BTreeMap<String, usize>,
    /// None means inventory validity is unknown, never an empty account.
    pub cloud_conversation_keys: Option<Vec<String>>,
    /// Retired plaintext records are counted without decoding their payload.
    pub skipped_legacy_count: usize,
    /** Account-owned, content-free cloud-list presentation state. */
    pub cloud_pinned_conversation_ids: Vec<String>,
    pub cloud_list_presentation_present: bool,
    /// Compatibility field; cloud reads no longer perform migration writes.
    pub upgraded_legacy_direct_count: usize,
}

fn selected_local_conversation_title(connection: &Connection, document_id: &str) -> Option<String> {
    let (workspace_id, conversation_id): (String, String) = connection
        .query_row(
            "SELECT workspace_id, conversation_id FROM desktop_selected_conversation_sync WHERE document_id=?1 ORDER BY last_synced_at_ms DESC LIMIT 1",
            [document_id],
            |row| Ok((row.get(0)?, row.get(1)?)),
        )
        .ok()?;
    let exchange: String = connection
        .query_row(
            "SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?1",
            [workspace_id],
            |row| row.get(0),
        )
        .ok()?;
    serde_json::from_str::<Value>(&exchange)
        .ok()?
        .get("conversations")?
        .as_array()?
        .iter()
        .find(|conversation| {
            conversation.get("id").and_then(Value::as_str) == Some(conversation_id.as_str())
        })?
        .get("title")?
        .as_str()
        .map(str::trim)
        .filter(|title| !title.is_empty())
        .map(|title| title.chars().take(48).collect())
}

/// Apply the verified portable cloud snapshot to an already-restored conversation. A cloud read
/// is the cross-device latest-state path: an equal revision from an older client may still carry
/// a newer complete title/message tree, so it must not leave the receiving device stale. Only
/// non-portable local blocks remain alongside their matching message.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum ExistingRemoteConversationMerge {
    Updated,
    Unchanged,
}

// The wire carries only text. Replace its text slots in order while retaining
// local attachment/runtime blocks at their existing slots. Never append all
// local material after all text: that changes its original context and leaves
// stale ordinals. Extra/removed remote text is applied without dropping local blocks.
fn preserve_local_message_blocks(local: &Value, remote: &mut Value) -> Result<(), String> {
    let incoming = remote.get("blocks").and_then(Value::as_array)
        .ok_or_else(|| safe_error("云端消息内容无效"))?;
    if incoming.iter().any(|block| block.get("kind").and_then(Value::as_str) != Some("TEXT")) {
        return Err(safe_error("云端消息内容超出文本同步范围"));
    }
    let mut text = incoming.iter().cloned();
    let mut blocks = Vec::new();
    for block in local.get("blocks").and_then(Value::as_array).into_iter().flatten() {
        if block.get("kind").and_then(Value::as_str) == Some("TEXT") {
            if let Some(replacement) = text.next() { blocks.push(replacement); }
        } else {
            blocks.push(block.clone());
        }
    }
    blocks.extend(text);
    for (ordinal, block) in blocks.iter_mut().enumerate() {
        block.as_object_mut().ok_or_else(|| safe_error("本机消息内容无效"))?
            .insert("ordinal".into(), json!(ordinal));
    }
    remote["blocks"] = Value::Array(blocks);
    Ok(())
}

fn portable_message_text(message: &Value) -> Vec<String> {
    message
        .get("blocks")
        .and_then(Value::as_array)
        .map(|blocks| {
            blocks
                .iter()
                .filter(|block| block.get("kind").and_then(Value::as_str) == Some("TEXT"))
                .filter_map(|block| block.get("text").and_then(Value::as_str).map(str::to_owned))
                .collect()
        })
        .unwrap_or_default()
}

// A prefix snapshot is not a deletion and must not hide retained descendants.
fn complete_sync_leaf(preferred: Option<&Value>, alternate: Option<&Value>, messages: &[Value]) -> Option<Value> {
    let Some(preferred_id) = preferred.and_then(Value::as_str) else { return alternate.cloned(); };
    let mut cursor = alternate.and_then(Value::as_str);
    let mut visited = BTreeSet::new();
    while let Some(id) = cursor {
        if !visited.insert(id) { break; }
        if id == preferred_id { return alternate.cloned(); }
        cursor = messages.iter().find(|m| m.get("id").and_then(Value::as_str) == Some(id))
            .and_then(|m| m.get("parentId")).and_then(Value::as_str);
    }
    preferred.cloned()
}

fn incoming_title_is_newer(
    local: &serde_json::Map<String, Value>, remote: &serde_json::Map<String, Value>, remote_revision: u64,
) -> Result<bool, String> {
    let title_revision = |value: &serde_json::Map<String, Value>| -> Result<Option<u64>, String> {
        match value.get("titleRevision") {
            None | Some(Value::Null) => Ok(None),
            Some(value) => value.as_u64().filter(|v| *v > 0 && *v <= i64::MAX as u64).map(Some)
                .ok_or_else(|| safe_error("标题版本必须是正整数")),
        }
    };
    let local_title_revision = title_revision(local)?;
    let remote_title_revision = title_revision(remote)?;
    if local_title_revision.is_some() || remote_title_revision.is_some() {
        let order = remote_title_revision.unwrap_or(0).cmp(&local_title_revision.unwrap_or(0));
        if order.is_eq() && local.get("title") != remote.get("title") {
            return Err(safe_error("标题版本冲突：两个标题有相同的标题版本；已保留原值"));
        }
        return Ok(!order.is_lt());
    }
    let parse = |value: Option<&Value>| value.and_then(Value::as_str)
        .and_then(|value| chrono::DateTime::parse_from_rfc3339(value).ok());
    let time_order = parse(remote.get("updatedAt")).cmp(&parse(local.get("updatedAt")));
    let revision_order = remote_revision.cmp(&local.get("revision").and_then(Value::as_u64).unwrap_or(1));
    if local.get("title") != remote.get("title") {
        return Err(safe_error("标题版本冲突：旧记录缺少标题先后依据；已保留本机标题，未覆盖"));
    }
    Ok(time_order.is_gt() || (time_order.is_eq() && !revision_order.is_lt()))
}

fn merge_newer_remote_conversation(
    transaction: &rusqlite::Transaction<'_>,
    workspace_id: &str,
    conversation_id: &str,
    remote_conversation: &serde_json::Map<String, Value>,
    remote_semantic_revision: u64,
) -> Result<ExistingRemoteConversationMerge, String> {
    let exchange_json: String = transaction
        .query_row(
            "SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?1",
            [workspace_id],
            |row| row.get(0),
        )
        .map_err(|_| safe_error("本机对话无法读取"))?;
    let mut exchange: Value =
        serde_json::from_str(&exchange_json).map_err(|_| safe_error("本机对话结构无效"))?;
    let local = exchange
        .get_mut("conversations")
        .and_then(Value::as_array_mut)
        .and_then(|items| {
            items
                .iter_mut()
                .find(|item| item.get("id").and_then(Value::as_str) == Some(conversation_id))
        })
        .and_then(Value::as_object_mut)
        .ok_or_else(|| safe_error("本机对话不存在"))?;
    let local_revision = local.get("revision").and_then(Value::as_u64).unwrap_or(1);
    let remote_title_is_newer = incoming_title_is_newer(local, remote_conversation, remote_semantic_revision)?;
    let local_updated_at = local.get("updatedAt").cloned();
    let timestamp = |value: Option<&Value>| value.and_then(Value::as_str).and_then(|v| chrono::DateTime::parse_from_rfc3339(v).ok());
    let remote_time_is_newer = timestamp(remote_conversation.get("updatedAt")) > timestamp(local_updated_at.as_ref());
    let local_title = local.get("title").cloned();
    let local_title_revision = local.get("titleRevision").cloned();
    let local_leaf = local.get("currentLeafId").cloned();
    let local_favorited: bool = transaction.query_row(
        "SELECT EXISTS(SELECT 1 FROM desktop_conversation_favorites WHERE workspace_id=?1 AND conversation_id=?2)",
        params![workspace_id, conversation_id], |row| row.get(0),
    ).map_err(|error| restore_store_error(error, "本机会话收藏无法核对"))?;
    let local_pinned = local.get("pinned").cloned();
    let local_archived = local.get("archived").cloned();
    let remote_messages = remote_conversation
        .get("messages")
        .and_then(Value::as_array)
        .ok_or_else(|| safe_error("云端对话消息无效"))?;
    let local_messages = local
        .get_mut("messages")
        .and_then(Value::as_array_mut)
        .ok_or_else(|| safe_error("本机对话消息无效"))?;
    // The portable envelope is a snapshot, so it does not carry a separate
    // "arrived on this device" event. Capture the pre-merge identities before
    // changing this local tree: newly received completed assistant turns must
    // advance this Desktop's local unread watermark, while historical or
    // already-visible turns must not.
    let local_message_ids = local_messages
        .iter()
        .filter_map(|message| message.get("id").and_then(Value::as_str))
        .map(str::to_owned)
        .collect::<BTreeSet<_>>();
    let remote_ids = remote_messages
        .iter()
        .map(|message| message.get("id").and_then(Value::as_str).map(str::to_owned))
        .collect::<Option<BTreeSet<_>>>()
        .ok_or_else(|| safe_error("云端消息标识无效"))?;
    if remote_ids.len() != remote_messages.len()
        || remote_messages.iter().any(|message| {
            message
                .get("parentId")
                .and_then(Value::as_str)
                .is_some_and(|parent| !remote_ids.contains(parent))
        })
        || remote_conversation
            .get("currentLeafId")
            .and_then(Value::as_str)
            .is_none_or(|leaf| !remote_ids.contains(leaf))
    {
        return Err(safe_error("云端消息树无效"));
    }
    if remote_semantic_revision == local_revision {
        let same_messages = local_messages.len() == remote_messages.len()
            && remote_messages.iter().all(|remote| {
                let remote_id = remote.get("id").and_then(Value::as_str);
                local_messages
                    .iter()
                    .find(|local| local.get("id").and_then(Value::as_str) == remote_id)
                    .is_some_and(|local| {
                        local.get("revision") == remote.get("revision")
                            && portable_message_text(local) == portable_message_text(remote)
                            && portable_model_usage(local) == portable_model_usage(remote)
                    })
            });
        if same_messages
            && local_title.as_ref() == remote_conversation.get("title")
            && local_title_revision.as_ref() == remote_conversation.get("titleRevision")
            && local_leaf.as_ref() == remote_conversation.get("currentLeafId")
            && local_pinned.as_ref() == remote_conversation.get("pinned")
            && local_archived.as_ref() == remote_conversation.get("archived")
            && remote_conversation.get("favorited").and_then(Value::as_bool).is_none_or(|favorited| favorited == local_favorited)
        {
            return Ok(ExistingRemoteConversationMerge::Unchanged);
        }
    }
    for remote_message in remote_messages {
        let remote_id = remote_message
            .get("id")
            .and_then(Value::as_str)
            .ok_or_else(|| safe_error("云端消息标识无效"))?;
        let local_index = local_messages.iter().position(|local_message| {
            local_message.get("id").and_then(Value::as_str) == Some(remote_id)
        });
        let Some(local_index) = local_index else {
            local_messages.push(remote_message.clone());
            continue;
        };
        let local_message = &local_messages[local_index];
        let mut updated = remote_message.clone();
        preserve_local_message_blocks(local_message, &mut updated)?;
        preserve_local_model_usage(local_message, &mut updated)?;
        local_messages[local_index] = updated;
    }
    // No per-message tombstones exist in this protocol. A missing answer is
    // retained and included in the next upload instead of being erased.
    let complete_leaf = complete_sync_leaf(remote_conversation.get("currentLeafId"), local_leaf.as_ref(), local_messages);
    for field in [
        "title",
        "titleRevision",
        "createdAt",
        "updatedAt",
        "currentLeafId",
        "pinned",
        "archived",
        "favorited",
    ] {
        if matches!(field, "title" | "titleRevision") && !remote_title_is_newer { continue; }
        if field == "updatedAt" && !remote_time_is_newer && local_updated_at.is_some() { continue; }
        if let Some(value) = remote_conversation.get(field) {
            local.insert(field.into(), value.clone());
        }
    }
    if let Some(leaf) = complete_leaf {
        local.insert("currentLeafId".into(), leaf);
    }
    local.insert(
        "revision".into(),
        Value::Number(remote_semantic_revision.max(local_revision).into()),
    );
    for assistant_message_id in remote_messages.iter().filter_map(|message| {
        let id = message.get("id").and_then(Value::as_str)?;
        (message.get("role").and_then(Value::as_str) == Some("assistant")
            && message.get("delivery").and_then(Value::as_str) == Some("COMPLETE")
            && !local_message_ids.contains(id))
        .then_some(id)
    }) {
        crate::desktop_conversation_read_state_v1::record_completed_assistant(
            transaction,
            workspace_id,
            conversation_id,
            assistant_message_id,
            now_ms(),
        )?;
    }
    if let Some(favorited) = remote_conversation.get("favorited").and_then(Value::as_bool) {
        if favorited {
            transaction.execute(
                "INSERT INTO desktop_conversation_favorites(workspace_id,conversation_id,favorited_at_ms) VALUES(?1,?2,?3)
                 ON CONFLICT(workspace_id,conversation_id) DO NOTHING",
                params![workspace_id, conversation_id, now_ms()],
            ).map_err(|error| restore_store_error(error, "云端会话收藏未保存"))?;
        } else {
            transaction.execute(
                "DELETE FROM desktop_conversation_favorites WHERE workspace_id=?1 AND conversation_id=?2",
                params![workspace_id, conversation_id],
            ).map_err(|error| restore_store_error(error, "云端会话收藏未保存"))?;
        }
    }
    crate::attachment_search_checkpoint_v1::reconcile_workspace(transaction, workspace_id, &exchange)?;
    // Older local workspaces can predate the full export envelope. They are still valid owners
    // of an already-synced conversation; merge the title and retain their existing workspace
    // hash rather than rejecting a safe cross-device update solely for that legacy shape.
    let semantic_hash = crate::semantic_hash(&exchange).ok();
    transaction
        .execute(
            "UPDATE workspace_exchange SET exchange_json=?1 WHERE workspace_id=?2",
            params![crate::canonical_json(&exchange)?, workspace_id],
        )
        .map_err(|error| restore_store_error(error, "本机对话更新未保存"))?;
    if let Some(semantic_hash) = semantic_hash {
        transaction
            .execute(
                "UPDATE workspaces SET semantic_hash=?1 WHERE id=?2",
                params![semantic_hash, workspace_id],
            )
            .map_err(|error| restore_store_error(error, "本机对话 hash 未更新"))?;
    }
    Ok(ExistingRemoteConversationMerge::Updated)
}

/// An explicit local sync is also a read of the document it is about to update.
/// Keep every completed remote turn that arrived after this device's last receipt
/// before producing the next envelope.  This is deliberately different from a
/// cloud-read merge: local title/leaf/collection state are the user's explicit
/// edit source here, while remote additions are unioned so a stale desktop can
/// never erase a phone turn simply by syncing its own later edit.
fn merge_remote_additions_for_local_commit(
    transaction: &rusqlite::Transaction<'_>,
    workspace_id: &str,
    conversation_id: &str,
    remote_conversation: &serde_json::Map<String, Value>,
    remote_semantic_revision: u64,
) -> Result<ExistingRemoteConversationMerge, String> {
    let exchange_json: String = transaction
        .query_row(
            "SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?1",
            [workspace_id],
            |row| row.get(0),
        )
        .map_err(|_| safe_error("本机对话无法读取"))?;
    let mut exchange: Value =
        serde_json::from_str(&exchange_json).map_err(|_| safe_error("本机对话结构无效"))?;
    let local = exchange
        .get_mut("conversations")
        .and_then(Value::as_array_mut)
        .and_then(|items| {
            items
                .iter_mut()
                .find(|item| item.get("id").and_then(Value::as_str) == Some(conversation_id))
        })
        .and_then(Value::as_object_mut)
        .ok_or_else(|| safe_error("本机对话不存在"))?;
    let remote_messages = remote_conversation
        .get("messages")
        .and_then(Value::as_array)
        .ok_or_else(|| safe_error("云端对话消息无效"))?;
    let remote_ids = remote_messages
        .iter()
        .map(|message| message.get("id").and_then(Value::as_str).map(str::to_owned))
        .collect::<Option<BTreeSet<_>>>()
        .ok_or_else(|| safe_error("云端消息标识无效"))?;
    if remote_ids.len() != remote_messages.len()
        || remote_messages.iter().any(|message| {
            message
                .get("parentId")
                .and_then(Value::as_str)
                .is_some_and(|parent| !remote_ids.contains(parent))
        })
    {
        return Err(safe_error("云端消息树无效"));
    }
    let parse_updated = |value: Option<&Value>| value.and_then(Value::as_str)
        .and_then(|value| chrono::DateTime::parse_from_rfc3339(value).ok());
    let remote_updated = parse_updated(remote_conversation.get("updatedAt"));
    let local_updated = parse_updated(local.get("updatedAt"));
    let remote_is_newer = remote_updated.is_some() && remote_updated > local_updated;
    let same_updated_at = remote_updated == local_updated;
    let local_semantic_revision = local.get("revision").and_then(Value::as_u64).unwrap_or(1);
    let remote_title_is_newer = incoming_title_is_newer(local, remote_conversation, remote_semantic_revision)?;
    let previous_leaf = local.get("currentLeafId").cloned();
    let local_messages = local
        .get_mut("messages")
        .and_then(Value::as_array_mut)
        .ok_or_else(|| safe_error("本机对话消息无效"))?;
    let mut changed = false;
    for remote_message in remote_messages {
        let remote_id = remote_message
            .get("id")
            .and_then(Value::as_str)
            .ok_or_else(|| safe_error("云端消息标识无效"))?;
        let Some(local_index) = local_messages
            .iter()
            .position(|message| message.get("id").and_then(Value::as_str) == Some(remote_id))
        else {
            local_messages.push(remote_message.clone());
            changed = true;
            continue;
        };
        let local_message = &local_messages[local_index];
        let local_revision = local_message
            .get("revision")
            .and_then(Value::as_u64)
            .unwrap_or(1);
        let remote_revision = remote_message
            .get("revision")
            .and_then(Value::as_u64)
            .unwrap_or(1);
        if !remote_is_newer && !(same_updated_at && remote_revision > local_revision) {
            continue;
        }
        let mut updated = remote_message.clone();
        preserve_local_message_blocks(local_message, &mut updated)?;
        preserve_local_model_usage(local_message, &mut updated)?;
        local_messages[local_index] = updated;
        changed = true;
    }
    let complete_leaf = if remote_is_newer {
        complete_sync_leaf(remote_conversation.get("currentLeafId"), previous_leaf.as_ref(), local_messages)
    } else {
        complete_sync_leaf(previous_leaf.as_ref(), remote_conversation.get("currentLeafId"), local_messages)
    };
    if remote_title_is_newer {
        if let Some(value) = remote_conversation.get("titleRevision") {
            if local.get("titleRevision") != Some(value) {
                local.insert("titleRevision".into(), value.clone());
                changed = true;
            }
        }
        if let Some(value) = remote_conversation.get("title") {
            if local.get("title") != Some(value) {
                local.insert("title".into(), value.clone());
                changed = true;
            }
        }
    }
    let local_revision = local_semantic_revision;
    if remote_is_newer {
        for field in ["updatedAt", "currentLeafId"] {
            if let Some(value) = remote_conversation.get(field) {
                if local.get(field) != Some(value) {
                    local.insert(field.into(), value.clone());
                    changed = true;
                }
            }
        }
    }
    if let Some(leaf) = complete_leaf {
        if local.get("currentLeafId") != Some(&leaf) {
            local.insert("currentLeafId".into(), leaf);
            changed = true;
        }
    }
    if remote_semantic_revision > local_revision {
        local.insert(
            "revision".into(),
            Value::Number(remote_semantic_revision.into()),
        );
        changed = true;
    }
    if !changed {
        return Ok(ExistingRemoteConversationMerge::Unchanged);
    }
    crate::attachment_search_checkpoint_v1::reconcile_workspace(transaction, workspace_id, &exchange)?;
    let semantic_hash = crate::semantic_hash(&exchange).ok();
    transaction
        .execute(
            "UPDATE workspace_exchange SET exchange_json=?1 WHERE workspace_id=?2",
            params![crate::canonical_json(&exchange)?, workspace_id],
        )
        .map_err(|_| safe_error("本机对话更新未保存"))?;
    if let Some(semantic_hash) = semantic_hash {
        transaction
            .execute(
                "UPDATE workspaces SET semantic_hash=?1 WHERE id=?2",
                params![semantic_hash, workspace_id],
            )
            .map_err(|_| safe_error("本机对话 hash 未更新"))?;
    }
    Ok(ExistingRemoteConversationMerge::Updated)
}

pub fn list_remote_documents<G: CloudGateway>(
    connection: &Connection,
    gateway: &G,
) -> Result<Vec<RemoteDocumentProjection>, String> {
    let mut documents = gateway
        .list()
        .map_err(|failure| safe_error(cloud_read_failure_message(failure)))?
        .into_iter()
        .map(|remote| {
            let document_id = envelope_document_id(&remote.envelope)?;
            if matches!(document_id.as_str(), CLOUD_LIST_PRESENTATION_DOCUMENT_ID | WORK_CLOUD_LIST_DOCUMENT_ID) {
                return Ok(None);
            }
            let title = serde_json::from_str::<Value>(&remote.envelope)
                .ok()
                .filter(|value| {
                    value.get("format").and_then(Value::as_str) == Some(sync_v1::DIRECT_ENVELOPE)
                })
                .and_then(|value| {
                    value
                        .pointer("/payload/records")
                        .and_then(Value::as_array)
                        .cloned()
                })
                .and_then(|records| {
                    records.into_iter().find(|record| {
                        record.get("kind").and_then(Value::as_str) == Some("conversation")
                    })
                })
                .and_then(|record| {
                    record
                        .pointer("/content/title")
                        .and_then(Value::as_str)
                        .map(str::trim)
                        .map(str::to_owned)
                })
                .filter(|title| !title.is_empty())
                .map(|title| title.chars().take(48).collect())
                .or_else(|| selected_local_conversation_title(connection, &document_id))
                .unwrap_or_else(|| "云端会话".into());
            Ok(Some(RemoteDocumentProjection {
                document_id,
                title,
                revision: remote.revision,
                payload_hash_prefix: remote.payload_hash.chars().take(12).collect(),
            }))
        })
        .collect::<Result<Vec<_>, String>>()?
        .into_iter()
        .flatten()
        .collect::<Vec<_>>();
    documents.sort_by(|left, right| left.document_id.cmp(&right.document_id));
    Ok(documents)
}

/// Safely recover from a pre-app-private-store confirmation marker when the
/// authenticated account is demonstrably empty.  The remote list is read here
/// again rather than trusting a renderer cache.  Existing cloud documents or
/// selected local sync rows must use the existing-recovery-code path instead.
pub fn bootstrap_empty_remote_recovery<C: CredentialStore, G: CloudGateway>(
    connection: &mut Connection,
    credentials: &C,
    gateway: &G,
) -> Result<(), String> {
    let session = read_session(credentials)?.ok_or_else(|| safe_error("请先登录 Google 账号"))?;
    let account = sync_state_v1::account_ref(&session.user_id)?;
    let confirmed = connection
        .query_row(
            "SELECT recovery_confirmed FROM desktop_cloud_account_state WHERE account_ref=?1",
            [&account],
            |row| row.get::<_, i64>(0),
        )
        .optional()
        .map_err(|_| safe_error("账号状态无法读取"))?
        .unwrap_or(0)
        == 1;
    if !confirmed || private_sync_materials_present(credentials, &account) {
        return Err(safe_error("当前账号不需要重新建立恢复保护"));
    }
    let remote = gateway
        .list()
        .map_err(|failure| safe_error(cloud_read_failure_message(failure)))?;
    if !remote.is_empty() {
        return Err(safe_error("云端已有对话，请使用已有恢复码连接此设备"));
    }
    let selected: u64 = connection
        .query_row(
            "SELECT COUNT(*) FROM desktop_selected_conversation_sync WHERE account_ref=?1",
            [&account],
            |row| row.get(0),
        )
        .map_err(|_| safe_error("本机同步状态无法读取"))?;
    let rotations: u64 = connection
        .query_row(
            "SELECT COUNT(*) FROM desktop_recovery_rotation WHERE account_ref=?1",
            [&account],
            |row| row.get(0),
        )
        .map_err(|_| safe_error("本机同步状态无法读取"))?;
    if selected != 0 || rotations != 0 {
        return Err(safe_error("本机已有同步记录，不能重新建立恢复保护"));
    }

    // Delete only incomplete stale material in the application-private
    // credential document.  This never reads macOS Keychain and never touches
    // conversation content.
    credentials
        .delete(RECOVERY_SERVICE, &account)
        .map_err(|_| safe_error("旧恢复保护无法清理"))?;
    credentials
        .delete(DATA_KEY_SERVICE, &account)
        .map_err(|_| safe_error("旧同步材料无法清理"))?;
    credentials
        .delete(PENDING_RECOVERY_SERVICE, &account)
        .map_err(|_| safe_error("旧恢复码待确认材料无法清理"))?;
    let mut state_store = SqliteMetadataStore { connection };
    let metadata = state_store
        .metadata(&account)?
        .ok_or_else(|| safe_error("账号状态不存在"))?;
    sync_state_v1::restart_recovery_setup_for_empty_remote(
        &mut state_store,
        &format!("desktop-empty-remote-recovery-{}", now_ms()),
        &account,
        metadata.revision,
    )
    .map_err(|_| safe_error("恢复保护状态未重置"))?;
    state_store
        .connection
        .execute(
            "UPDATE desktop_cloud_account_state
             SET state='AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION', periodic_enabled=0,
                 recovery_confirmed=0, recovery_salt_b64=NULL,
                 pending_recovery_salt_b64=NULL,pending_recovery_code_hash=NULL,pending_recovery_created_at_ms=NULL,
                 last_error_code=NULL,
                 revision=revision+1, updated_at_ms=?2
             WHERE account_ref=?1",
            params![account, now_ms()],
        )
        .map_err(|_| safe_error("恢复保护状态未重置"))?;
    write_diagnostic(
        state_store.connection,
        Some(&account),
        "RECOVERY_EMPTY_CLOUD_BOOTSTRAP",
        "SUCCESS",
        None,
    );
    Ok(())
}

pub fn restore_remote_conversation<C: CredentialStore, G: CloudGateway>(
    connection: &mut Connection,
    credentials: &C,
    gateway: &G,
    document_id: &str,
) -> Result<RestoreReceipt, String> {
    if !document_id.starts_with("conversation-") || document_id.len() > 64 {
        return Err(safe_error("云端文档标识无效"));
    }
    let session = read_session(credentials)?.ok_or_else(|| safe_error("请先登录 Google 账号"))?;
    let account=sync_state_v1::account_ref(&session.user_id)?;
    let cancel_epoch=cancellation_epoch(connection,&account,document_id)?;
    let remote = gateway
        .read(document_id)
        .map_err(|_| safe_error("云端恢复文档无法读取"))?
        .ok_or_else(|| safe_error("云端恢复文档不存在"))?;
    require_same_session(credentials, &session)?;
    restore_remote_envelope_observed(connection, credentials, remote,Some(cancel_epoch))
}

// The batch list RPC already returns each full envelope, avoiding an extra read.
fn open_account_document<C: CredentialStore>(
    connection: &Connection,
    credentials: &C,
    account: &str,
    remote: &RemoteEnvelope,
    document_id: &str,
) -> Result<Value, String> {
    let format = serde_json::from_str::<Value>(&remote.envelope)
        .ok()
        .and_then(|value| value.get("format").and_then(Value::as_str).map(str::to_owned));
    open_account_document_from_public_format(connection,credentials,account,remote,document_id,format.as_deref())
}

// Public format selects the existing decoder only; its strict envelope/hash/
// payload checks remain authoritative. No decoded body or credential is cached.
fn open_account_document_from_public_format<C: CredentialStore>(
    connection:&Connection, credentials:&C, account:&str, remote:&RemoteEnvelope,
    document_id:&str, format:Option<&str>,
) -> Result<Value,String> {
    #[cfg(test)] let _phase=RestorePhaseTimer::start("open-integrity");
    if format == Some(sync_v1::DIRECT_ENVELOPE) {
        return sync_v1::open_direct(&remote.envelope, APP_ID, document_id, remote.revision)
            .map_err(|_| safe_error("云端对话完整性校验未通过"));
    }
    if format != Some(sync_v1::ENVELOPE) {
        return Err(safe_error("云端封包格式无法读取"));
    }
    let mut material = wrapping_material(connection, credentials, account)
        .map_err(|_| safe_error("旧加密记录需要在原设备更新后同步一次"))?;
    let result = sync_v1::open_with_account_wrapping_material(
        &remote.envelope,
        &material,
        APP_ID,
        document_id,
        remote.revision,
    )
    .map(|opened| opened.payload)
    .map_err(|_| safe_error("旧加密记录需要在原设备更新后同步一次"));
    material.wrapping_key.zeroize();
    result
}

fn migrate_legacy_document<C: CredentialStore, G: CloudGateway>(
    connection: &Connection,
    credentials: &C,
    gateway: &G,
    account: &str,
    remote: RemoteEnvelope,
) -> Result<RemoteEnvelope, String> {
    let root: Value = serde_json::from_str(&remote.envelope)
        .map_err(|_| safe_error("云端封包格式无法读取"))?;
    if root.get("format").and_then(Value::as_str) != Some(sync_v1::ENVELOPE) {
        return Ok(remote);
    }
    let document_id = envelope_document_id(&remote.envelope)?;
    let mut payload = open_account_document(connection, credentials, account, &remote, &document_id)?;
    let revision = remote.revision.checked_add(1).ok_or_else(|| safe_error("云端版本无效"))?;
    payload["revision"] = json!(revision);
    let envelope = sync_v1::seal_direct(payload).map_err(|_| safe_error("旧记录格式更新未完成"))?;
    let expected_hash = serde_json::from_str::<Value>(&envelope).unwrap()["payloadHash"]
        .as_str().unwrap().to_owned();
    if read_session(credentials)?
        .map(|session| sync_state_v1::account_ref(&session.user_id))
        .transpose()?
        .as_deref() != Some(account)
    {
        return Err(safe_error("登录状态已变化，未更新旧记录"));
    }
    let committed = gateway.commit(&document_id, remote.revision, &envelope);
    let read_back = gateway.read(&document_id)
        .map_err(|_| safe_error("旧记录格式更新尚未确认，请再次读取核对"))?
        .ok_or_else(|| safe_error("旧记录格式更新尚未确认，请再次读取核对"))?;
    if read_back.revision != revision
        || read_back.payload_hash != expected_hash
        || sync_v1::open_direct(&read_back.envelope, APP_ID, &document_id, revision).is_err()
    {
        return Err(safe_error(if committed.is_err() {
            "旧记录格式更新未完成，原内容已保留"
        } else {
            "旧记录格式更新回读未通过"
        }));
    }
    Ok(read_back)
}

// Test-only phase sampling is enabled exclusively by the marked-root manual profile.
#[cfg(test)]
thread_local! { static RESTORE_PHASE_COSTS: std::cell::RefCell<Option<BTreeMap<&'static str,(u64,u64)>>> = const { std::cell::RefCell::new(None) }; }
#[cfg(test)]
struct RestorePhaseTimer(Option<(&'static str,std::time::Instant)>);
#[cfg(test)]
impl RestorePhaseTimer {
    fn start(label:&'static str)->Self { Self(RESTORE_PHASE_COSTS.with(|costs| costs.borrow().as_ref().map(|_|(label,std::time::Instant::now())))) }
}
#[cfg(test)]
impl Drop for RestorePhaseTimer {
    fn drop(&mut self) { if let Some((label,started))=self.0.take() { RESTORE_PHASE_COSTS.with(|costs| {
        if let Some(rows)=costs.borrow_mut().as_mut() { let row=rows.entry(label).or_default();row.0+=1;row.1=row.1.saturating_add(started.elapsed().as_nanos().min(u64::MAX as u128) as u64); }
    }); } }
}
#[cfg(test)]
struct RestorePhaseCapture;
#[cfg(test)]
impl RestorePhaseCapture {
    fn begin()->Self { RESTORE_PHASE_COSTS.with(|costs| { assert!(costs.borrow().is_none()); *costs.borrow_mut()=Some(BTreeMap::new()); });Self }
    fn finish(self)->BTreeMap<&'static str,(u64,u64)> { RESTORE_PHASE_COSTS.with(|costs| costs.borrow_mut().take().unwrap()) }
}
#[cfg(test)]
impl Drop for RestorePhaseCapture { fn drop(&mut self) { RESTORE_PHASE_COSTS.with(|costs| { costs.borrow_mut().take(); }); } }

// Validated transient business data only; no credential/session bytes or network owner.
struct PreparedConversationRestore {
    account:String, document_id:String, remote_revision:u64, remote_payload_hash:String,
    restored_local_hash:String, conversation_id:String, workspace_id:String,
    conversation:serde_json::Map<String,Value>, restored_title:String, restored_favorited:bool,
    semantic_revision:u64, portable_plans:Vec<Value>, safe_settings:Option<Value>,
    data_area:crate::DesktopDataArea,
    // Outer None is a trusted already-local preparation, not a network read.
    cancel_epoch:Option<Option<i64>>,
}

fn prepare_remote_conversation_restore<C: CredentialStore>(
    connection: &Connection,
    credentials: &C,
    remote: &RemoteEnvelope,
) -> Result<PreparedConversationRestore, String> {
    prepare_remote_conversation_restore_in_area(connection,credentials,remote,None)
}

fn prepare_remote_conversation_restore_in_area<C: CredentialStore>(
    connection:&Connection, credentials:&C, remote:&RemoteEnvelope,
    area_hint:Option<crate::DesktopDataArea>,
) -> Result<PreparedConversationRestore,String> {
    let (document_id, format) = envelope_document_identity(&remote.envelope)?;
    prepare_remote_conversation_restore_from_public_identity(connection,credentials,remote,area_hint,document_id,format)
}

fn prepare_remote_conversation_restore_from_public_identity<C: CredentialStore>(
    connection:&Connection, credentials:&C, remote:&RemoteEnvelope,
    area_hint:Option<crate::DesktopDataArea>, document_id:String, format:Option<String>,
) -> Result<PreparedConversationRestore,String> {
    #[cfg(test)] let _phase=RestorePhaseTimer::start("prepare");
    // A direct document needs only an operation identity during preparation.
    // The bound store still freshly checks generation/user/tokens before any SQL
    // write in with_account_write below; neither session bytes nor secrets are cached.
    // Unbound stores and legacy wrapping keep their original fresh-read path.
    let prepared_identity = credentials.prepared_account_write_user_id()
        .filter(|_| format.as_deref() == Some(sync_v1::DIRECT_ENVELOPE));
    let account = if let Some(user_id) = prepared_identity {
        sync_state_v1::account_ref(user_id)?
    } else {
        let session = read_session(credentials)?.ok_or_else(|| safe_error("请先登录 Google 账号"))?;
        sync_state_v1::account_ref(&session.user_id)?
    };
    let payload = open_account_document_from_public_format(connection,credentials,&account,remote,&document_id,format.as_deref())?;
    let records = payload
        .get("records")
        .and_then(Value::as_array)
        .ok_or_else(|| safe_error("云端恢复内容无效"))?;
    if records.is_empty() || records.len() > 3 {
        return Err(safe_error(
            "当前 Desktop 只能恢复单个纯文本对话、安全个性化字段及其已确认提醒计划",
        ));
    }
    let record = records
        .iter()
        .find(|record| record.get("kind").and_then(Value::as_str) == Some("conversation"))
        .ok_or_else(|| safe_error("云端恢复缺少纯文本对话"))?;
    let safe_settings = records
        .iter()
        .find(|record| record.get("kind").and_then(Value::as_str) == Some("safe_settings"));
    let reminder_plans = records.iter().find(|record| {
        record.get("kind").and_then(Value::as_str) == Some("relation")
            && record
                .get("content")
                .and_then(|content| content.get("type"))
                .and_then(Value::as_str)
                == Some("REMINDER_PLANS_V1")
    });
    if records
        .iter()
        .any(|item| match item.get("kind").and_then(Value::as_str) {
            Some("conversation" | "safe_settings") => false,
            Some("relation") => {
                item.get("content")
                    .and_then(|content| content.get("type"))
                    .and_then(Value::as_str)
                    != Some("REMINDER_PLANS_V1")
            }
            _ => true,
        })
    {
        return Err(safe_error("云端恢复包含不支持的记录"));
    }
    let portable_plans = reminder_plans
        .and_then(|record| record.get("content"))
        .and_then(|content| content.get("plans"))
        .and_then(Value::as_array)
        .map(Vec::as_slice)
        .unwrap_or(&[]);
    if portable_plans.len() > 100 {
        return Err(safe_error("云端提醒计划数量超出限制"));
    }
    let restored_local_hash = sha256(
        serde_json::to_string(records)
            .map_err(|_| safe_error("云端恢复内容无法编码"))?
            .as_bytes(),
    );
    let conversation_id = record
        .get("id")
        .and_then(Value::as_str)
        .filter(|value| !value.is_empty() && value.len() <= 64)
        .ok_or_else(|| safe_error("云端对话标识无效"))?
        .to_owned();
    let area = desktop_data_area::content_area(
        record
            .get("content")
            .ok_or_else(|| safe_error("云端对话内容无效"))?,
    )?;
    let expected_area=match area_hint {Some(area)=>area,None=>desktop_data_area::read_area(connection)?};
    if area != expected_area {
        return Err(safe_error("OTHER_DATA_AREA"));
    }
    let expected_id = desktop_data_area::document_id(area, &conversation_id);
    let legacy_id = desktop_data_area::document_id(crate::DesktopDataArea::Chat, &conversation_id);
    if document_id != expected_id && document_id != legacy_id {
        return Err(safe_error("云端对话身份不一致"));
    }
    let mut conversation = record
        .get("content")
        .and_then(Value::as_object)
        .cloned()
        .ok_or_else(|| safe_error("云端对话内容无效"))?;
    normalize_android_selected_conversation(&mut conversation)?;
    conversation.remove("surface");
    let messages = conversation
        .get("messages")
        .and_then(Value::as_array)
        .filter(|items| !items.is_empty())
        .ok_or_else(|| safe_error("云端对话没有可恢复消息"))?;
    let leaf = conversation
        .get("currentLeafId")
        .and_then(Value::as_str)
        .map(str::to_owned)
        .or_else(|| {
            messages
                .last()
                .and_then(|message| message.get("id"))
                .and_then(Value::as_str)
                .map(str::to_owned)
        })
        .ok_or_else(|| safe_error("云端对话消息树无效"))?;
    conversation.insert("id".into(), Value::String(conversation_id.clone()));
    conversation.insert("projectId".into(), Value::Null);
    conversation.insert("currentLeafId".into(), Value::String(leaf));
    conversation.insert(
        "revision".into(),
        Value::Number(
            record
                .get("revision")
                .and_then(Value::as_u64)
                .unwrap_or(1)
                .into(),
        ),
    );
    let restored_title = conversation
        .get("title")
        .and_then(Value::as_str)
        .unwrap_or("云端恢复对话")
        .to_owned();
    let restored_favorited = conversation
        .get("favorited")
        .and_then(Value::as_bool)
        .unwrap_or(false);
    // Reading the cloud list on the source device must open the original
    // local conversation, not manufacture a second "cloud restore" copy.
    // A ledger row exists only after this installation has already synced or
    // restored this exact document.
    let workspace_id = format!(
        "workspace-cloud-{}",
        &sha256(format!("{account}:{document_id}").as_bytes())[..40]
    );
    Ok(PreparedConversationRestore {
        account, document_id, remote_revision:remote.revision, remote_payload_hash:remote.payload_hash.clone(),
        restored_local_hash, conversation_id, workspace_id, conversation, restored_title, restored_favorited,
        semantic_revision:record.get("revision").and_then(Value::as_u64).unwrap_or(1),
        portable_plans:portable_plans.to_vec(), safe_settings:safe_settings.cloned(),data_area:area,cancel_epoch:None,
    })
}

fn restore_remote_envelope<C: CredentialStore>(
    connection:&mut Connection, credentials:&C, remote:RemoteEnvelope,
) -> Result<RestoreReceipt,String> {
    restore_remote_envelope_observed(connection,credentials,remote,None)
}
fn restore_remote_envelope_observed<C:CredentialStore>(
    connection:&mut Connection,credentials:&C,remote:RemoteEnvelope,observed:Option<Option<i64>>,
)->Result<RestoreReceipt,String> {
    let mut prepared=prepare_remote_conversation_restore(connection,credentials,&remote)?;
    prepared.cancel_epoch=observed;
    restore_prepared_conversation(connection,credentials,&prepared)
}

fn restore_prepared_conversation<C: CredentialStore>(
    connection:&mut Connection, credentials:&C, prepared:&PreparedConversationRestore,
) -> Result<RestoreReceipt,String> {
    credentials.with_account_write(|| {
        // Binding and body observations share the writer snapshot with their
        // changes. Never read credentials or access the network here.
        let transaction=connection.transaction_with_behavior(TransactionBehavior::Immediate)
            .map_err(|error|restore_store_error(error,"云端恢复 transaction 无法开启"))?;
        let current_area=desktop_data_area::read_area(&transaction)?;
        let receipt=apply_prepared_conversation_restore(&transaction,prepared,current_area,true)?;
        transaction.commit().map_err(|error|restore_store_error(error,"云端恢复未原子提交"))?;
        Ok(receipt)
    })
}

const CLOUD_RESTORE_GROUP_DOCUMENTS: usize = 64;
const CLOUD_RESTORE_GROUP_BYTES: usize = 1024 * 1024;

// Only the bound direct preparation path calls this helper. Preparation is not
// authentication: the latest private session is checked under the generation
// lock immediately before BEGIN and that lock remains held through COMMIT.
fn restore_prepared_conversation_group<C: CredentialStore>(
    connection:&mut Connection, credentials:&C, prepared:&[PreparedConversationRestore],
) -> Result<Vec<Result<RestoreReceipt,String>>,String> {
    enum Outcome {
        Committed(Vec<Result<RestoreReceipt,String>>),
        RolledBackConstraint,
    }
    let outcome=credentials.with_account_write(|| {
        let transaction=connection.transaction_with_behavior(TransactionBehavior::Immediate)
            .map_err(|error|restore_store_error(error,"云端恢复分组事务无法开启"))?;
        // Preparation's captured area is only a hint. Validate every prepared
        // item against this actual writer snapshot without repeated schema scans.
        let current_area=desktop_data_area::read_area(&transaction)?;
        if prepared.iter().any(|item|item.data_area!=current_area) {
            return Err(safe_error("数据库区域身份已变化，已停止读取"));
        }
        let mut results=Vec::with_capacity(prepared.len());
        for item in prepared {
            transaction.execute_batch("SAVEPOINT nfai_cloud_restore_item")
                .map_err(|error|restore_store_error(error,"云端恢复单项事务无法开启"))?;
            let result=apply_prepared_conversation_restore(&transaction,item,current_area,false);
            if result.is_err() {
                transaction.execute_batch("ROLLBACK TO nfai_cloud_restore_item")
                    .map_err(|error|restore_store_error(error,"云端恢复单项未完整回滚"))?;
            }
            transaction.execute_batch("RELEASE nfai_cloud_restore_item")
                .map_err(|error|restore_store_error(error,"云端恢复单项事务未关闭"))?;
            results.push(result);
        }
        // Optional feedback stays in the same transaction. Bound its retention
        // once per commit, and stop if a feedback statement rolled back the root.
        prune_restore_feedback(&transaction)?;
        match transaction.commit() {
            Ok(())=>Ok(Outcome::Committed(results)),
            Err(error) if error.sqlite_error_code()==Some(ErrorCode::ConstraintViolation)
                && connection.is_autocommit()=>Ok(Outcome::RolledBackConstraint),
            Err(error)=>Err(restore_store_error(error,"云端恢复分组未原子提交")),
        }
    })?;
    match outcome {
        Outcome::Committed(results)=>Ok(results),
        // Deferred FK constraints may only be identified at COMMIT. A known
        // fully rolled-back constraint failure gets one bounded single-item
        // isolation pass, with a new guard for every item. Never retry BUSY,
        // I/O errors, unknown rollback, or a successfully committed group.
        Outcome::RolledBackConstraint=>{
            let mut results=Vec::with_capacity(prepared.len());
            for item in prepared {
                let result=restore_prepared_conversation(connection,credentials,item);
                if result.is_err() {
                    // Bound read rejects token/generation invalidation before
                    // the next item can write. There are no private reads in SQL.
                    let account=read_session(credentials)?.map(|session|
                        sync_state_v1::account_ref(&session.user_id)).transpose()?;
                    if account.as_deref()!=Some(item.account.as_str()) {
                        return Err(safe_error("登录状态已变化，已停止读取"));
                    }
                }
                results.push(result);
            }
            Ok(results)
        }
    }
}

fn flush_prepared_conversation_group<C: CredentialStore>(
    connection:&mut Connection, credentials:&C, pending:&mut Vec<PreparedConversationRestore>,
    restored:&mut Vec<RestoreReceipt>, failed_count:&mut usize,
    failure_reasons:&mut BTreeMap<String,usize>,
) -> Result<(),String> {
    if pending.is_empty() { return Ok(()); }
    #[cfg(test)] let _phase=RestorePhaseTimer::start("group");
    let items=std::mem::take(pending);
    let results=restore_prepared_conversation_group(connection,credentials,&items)?;
    // No successful receipt escapes the group until its actual durable commit.
    for (item,result) in items.iter().zip(results) {
        match result {
            Ok(receipt)=>restored.push(receipt),
            Err(error)=>{
                let account=read_session(credentials)?.map(|session|
                    sync_state_v1::account_ref(&session.user_id)).transpose()?;
                if account.as_deref()!=Some(item.account.as_str()) {
                    return Err(safe_error("登录状态已变化，已停止读取"));
                }
                *failed_count+=1;
                let code=restore_failure_code(&error);
                *failure_reasons.entry(code.into()).or_default()+=1;
                write_diagnostic(connection,Some(&item.account),"CLOUD_DOCUMENT_RESTORE","FAILED",Some(code));
            }
        }
    }
    Ok(())
}

fn apply_prepared_conversation_restore(
    transaction:&rusqlite::Transaction<'_>, prepared:&PreparedConversationRestore,
    current_area:crate::DesktopDataArea, prune_feedback:bool,
) -> Result<RestoreReceipt,String> {
    #[cfg(test)] let _phase=RestorePhaseTimer::start("apply");
    require_restore_transaction(transaction)?;
    if let Some(observed)=prepared.cancel_epoch {
        if cancellation_epoch(transaction,&prepared.account,&prepared.document_id)?!=observed {
            return Err(safe_error("SYNC_CANCELLED_DURING_READ"));
        }
    }
    if prepared.data_area!=current_area {return Err(safe_error("OTHER_DATA_AREA"));}
    let PreparedConversationRestore { account,document_id,remote_revision,remote_payload_hash,
        restored_local_hash,conversation_id,workspace_id,conversation,restored_title,restored_favorited,
        semantic_revision,portable_plans,safe_settings,data_area:_,cancel_epoch:_ }=prepared;
    let receipt_binding = transaction
        .query_row(
            "SELECT workspace_id, conversation_id FROM desktop_selected_conversation_sync WHERE account_ref=?1 AND document_id=?2 ORDER BY last_synced_at_ms DESC LIMIT 1",
            params![account, document_id],
            |row| Ok((row.get::<_, String>(0)?, row.get::<_, String>(1)?)),
        )
        .optional()
        .map_err(|_| safe_error("云端恢复回执无法读取"))?;
    // Historical restores could leave the deterministic account-owned workspace
    // without its receipt. Repair only that exact local conversation and never
    // adopt a copy already bound to another account.
    let existing_binding = if receipt_binding.is_some() {
        receipt_binding
    } else {
        transaction.query_row(
            "SELECT w.workspace_id, ?2 FROM workspace_exchange w, json_each(w.exchange_json,'$.conversations') c
             WHERE w.workspace_id=?1 AND json_extract(c.value,'$.id')=?2
             AND NOT EXISTS(SELECT 1 FROM desktop_selected_conversation_sync s
                 WHERE s.workspace_id=w.workspace_id AND s.account_ref<>?3) LIMIT 1",
            params![workspace_id, conversation_id, account],
            |row| Ok((row.get::<_, String>(0)?, row.get::<_, String>(1)?)),
        ).optional().map_err(|_| safe_error("历史恢复对话无法核对"))?
    };
    let mut stale_binding = None;
    if let Some((existing_workspace_id, existing_conversation_id)) = existing_binding {
        let workspace_exists: bool = transaction
            .query_row(
                "SELECT EXISTS(SELECT 1 FROM workspaces WHERE id=?1)",
                [&existing_workspace_id],
                |row| row.get(0),
            )
            .unwrap_or(false);
        if workspace_exists {
            // Cloud list reads must not acknowledge an older snapshot while a
            // selected local edit is waiting to be uploaded. The queued sync
            // will read and union the remote tree before its CAS commit.
            let local_sync_pending: bool = transaction.query_row(
                "SELECT EXISTS(SELECT 1 FROM desktop_sync_jobs WHERE account_ref=?1 AND workspace_id=?2 AND conversation_id=?3 AND stage IN ('TITLE_PENDING','CONTENT_PENDING','DELETE_PENDING','TITLE_CONFLICT','UNKNOWN','COMMITTING'))",
                params![account, existing_workspace_id, existing_conversation_id], |row| row.get(0),
            ).map_err(|error| restore_store_error(error, "本机续同步状态无法核对"))?;
            if local_sync_pending {
                return Err(safe_error("本机对话有待续同步变更，请同步完成后再读取云端列表"));
            }
            recover_local_conversation_occurrences_in_transaction(
                &transaction, &account, &existing_workspace_id, &existing_conversation_id,
            )?;
            let merge = merge_newer_remote_conversation(
                &transaction,
                &existing_workspace_id,
                &existing_conversation_id,
                &conversation,
                *semantic_revision,
            )?;
            transaction
                .execute(
                    "INSERT INTO desktop_selected_conversation_sync(account_ref,workspace_id,conversation_id,document_id,remote_revision,payload_hash,local_content_hash,last_synced_at_ms) VALUES(?1,?2,?3,?4,?5,?6,?7,?8) ON CONFLICT(account_ref,workspace_id,conversation_id) DO UPDATE SET document_id=excluded.document_id,remote_revision=excluded.remote_revision,payload_hash=excluded.payload_hash,local_content_hash=excluded.local_content_hash,last_synced_at_ms=excluded.last_synced_at_ms",
                    params![account, existing_workspace_id, existing_conversation_id, document_id, *remote_revision, remote_payload_hash, restored_local_hash, now_ms()],
                )
                .map_err(|error| restore_store_error(error, "云端恢复回执未更新"))?;
            return Ok(RestoreReceipt {
                status: if merge == ExistingRemoteConversationMerge::Updated { "UPDATED_LOCAL" } else { "ALREADY_LOCAL" }.into(),
                workspace_id: existing_workspace_id,
                conversation_id: existing_conversation_id,
                title: selected_local_conversation_title(transaction, &document_id)
                    .unwrap_or_else(||restored_title.clone()),
                remote_revision: *remote_revision,
            });
        }
        // A missing workspace leaves a stale binding. Replace its receipt only
        // within the recovered workspace transaction, so a failed restore
        // preserves the old identity for subsequent reconciliation.
        stale_binding = Some((existing_workspace_id, existing_conversation_id));
    }
    #[cfg(test)] let _encoding_phase=RestorePhaseTimer::start("new-exchange-encoding");
    let now = "2026-09-01T00:00:00Z";
    let mut exchange = json!({
        "format":"nfai.exchange","version":1,
        "export":{"id":format!("export-cloud-{}", &sha256(document_id.as_bytes())[..24]),"createdAt":now,"origin":{"platform":"DESKTOP","appVersion":"cloud-restore-v1"},"sensitivity":"NORMAL"},
        "projects":[],"conversations":[Value::Object(conversation.clone())],"knowledge":[],"memory":[],"relations":[],
        "settings":{"theme":"SYSTEM","uiLanguage":"zh-CN"}
    });
    let semantic_hash = crate::semantic_hash(&exchange)?;
    exchange["export"]["semanticHash"] = Value::String(semantic_hash.clone());
    let encoded = serde_json::to_string(&exchange).map_err(|_| safe_error("恢复工作区无法编码"))?;
    let package_hash = sha256(encoded.as_bytes());
    #[cfg(test)] drop(_encoding_phase);
    let workspace_occupied: bool = transaction
        .query_row(
            "SELECT EXISTS(SELECT 1 FROM workspaces WHERE id=?1)",
            [&workspace_id],
            |row| row.get(0),
        )
        .map_err(|_| safe_error("恢复工作区无法读取"))?;
    if workspace_occupied {
        return Err(safe_error("同一云端文档的本机恢复状态无法核对"));
    }
    let result = (|| -> Result<(), String> {
        if let Some((existing_workspace_id, existing_conversation_id)) = stale_binding {
            transaction.execute(
                "DELETE FROM desktop_selected_conversation_sync WHERE account_ref=?1 AND workspace_id=?2 AND conversation_id=?3 AND document_id=?4",
                params![account, existing_workspace_id, existing_conversation_id, document_id],
            ).map_err(|_| safe_error("过期云端恢复回执无法清理"))?;
        }
        transaction.execute("INSERT INTO workspaces(id,title,semantic_hash,package_hash,created_at) VALUES(?1,?2,?3,?4,?5)",params![workspace_id,restored_title,semantic_hash,package_hash,now]).map_err(|_| safe_error("云端恢复工作区未写入"))?;
        transaction
            .execute(
                "INSERT INTO workspace_exchange(workspace_id,exchange_json) VALUES(?1,?2)",
                params![workspace_id, encoded],
            )
            .map_err(|_| safe_error("云端恢复对话未写入"))?;
        if *restored_favorited {
            transaction
                .execute(
                    "INSERT INTO desktop_conversation_favorites(workspace_id,conversation_id,favorited_at_ms) VALUES(?1,?2,?3)
                     ON CONFLICT(workspace_id,conversation_id) DO UPDATE SET favorited_at_ms=excluded.favorited_at_ms",
                    params![workspace_id, conversation_id, now_ms()],
                )
                .map_err(|_| safe_error("云端恢复收藏状态未写入"))?;
        }
        transaction.execute("INSERT INTO desktop_selected_conversation_sync(account_ref,workspace_id,conversation_id,document_id,remote_revision,payload_hash,local_content_hash,last_synced_at_ms) VALUES(?1,?2,?3,?4,?5,?6,?7,?8)",params![account,workspace_id,conversation_id,document_id,*remote_revision,remote_payload_hash,restored_local_hash,now_ms()]).map_err(|_| safe_error("云端恢复回执未写入"))?;
        for portable in portable_plans {
            let source_id = portable
                .get("id")
                .and_then(Value::as_str)
                .filter(|value| !value.is_empty() && value.len() <= 64)
                .ok_or_else(|| safe_error("云端提醒标识无效"))?;
            let text = |key: &str, limit: usize| {
                portable
                    .get(key)
                    .and_then(Value::as_str)
                    .filter(|value| !value.trim().is_empty() && value.chars().count() <= limit)
                    .map(str::to_owned)
                    .ok_or_else(|| safe_error("云端提醒字段无效"))
            };
            let args = crate::desktop_reminders_v1::PortablePlanArgs {
                source_key: format!("{document_id}:{source_id}"),
                workspace_id: workspace_id.clone(),
                conversation_id: conversation_id.clone(),
                title: text("title", 40)?,
                instruction: text("instruction", 8_000)?,
                schedule_kind: text("scheduleKind", 16)?,
                anchor_local: text("anchorLocal", 32)?,
                timezone_id: text("timezoneId", 64)?,
                missed_policy: text("missedPolicy", 16)?,
                source_status: text("status", 16)?,
                next_run_at_ms: portable.get("nextRunAtMs").and_then(Value::as_i64),
            };
            crate::desktop_reminders_v1::restore_portable_plan(&transaction, &args, now_ms())
                .map_err(|_| safe_error("云端提醒计划未安全恢复"))?;
        }
        if let Some(interests) = safe_settings.as_ref()
            .and_then(|item| item.get("content"))
            .and_then(|content| content.get("interests"))
            .and_then(Value::as_str)
            .map(str::trim)
            .filter(|value| value.chars().count() <= 500)
        {
            transaction.execute(
                "INSERT INTO desktop_portable_personalization_v1(id,interests,revision,updated_at_ms) VALUES(1,?1,1,?2)
                 ON CONFLICT(id) DO UPDATE SET interests=excluded.interests,revision=desktop_portable_personalization_v1.revision+1,updated_at_ms=excluded.updated_at_ms",
                params![interests,now_ms()],
            ).map_err(|_| safe_error("云端关注方向兼容值未恢复"))?;
        }
        recover_local_conversation_occurrences_in_transaction(
            &transaction, &account, &workspace_id, &conversation_id,
        )?;
        transaction.execute("UPDATE desktop_cloud_account_state SET state='READY',last_success_at_ms=?2,last_error_code=NULL,revision=revision+1,updated_at_ms=?2 WHERE account_ref=?1",params![account,now_ms()]).map_err(|_| safe_error("云端恢复账号状态未写入"))?;
        let metadata_revision: i64 = transaction
            .query_row(
                "SELECT revision FROM sync_account_metadata WHERE account_ref=?1",
                [&account],
                |row| row.get(0),
            )
            .map_err(|_| safe_error("账号密钥状态不存在"))?;
        transaction.execute("UPDATE sync_account_metadata SET state='READY',revision=?2,direction_fact='EMPTY_LOCAL_REMOTE_PRESENT',last_error=NULL,updated_at=?3 WHERE account_ref=?1",params![account,metadata_revision+1,now]).map_err(|_| safe_error("云端恢复状态未写入"))?;
        write_cloud_restore_intent(&transaction,&account,metadata_revision,now,now_ms())?;
        // Success feedback shares the document's durable commit. Optional
        // feedback writes keep their existing best-effort behavior.
        append_diagnostic(&transaction, Some(&account), "CLOUD_RESTORE", "SUCCESS", None);
        require_restore_transaction(transaction)?;
        append_notification(
            &transaction, Some(&account), "SUCCESS",
            "云端对话已恢复为新的本机工作区，未覆盖现有数据。",
        );
        require_restore_transaction(transaction)?;
        if prune_feedback {prune_restore_feedback(transaction)?;}
        Ok(())
    })();
    result?;
    Ok(RestoreReceipt {
        status: "RESTORED_AS_NEW_WORKSPACE".into(),
        workspace_id:workspace_id.clone(),
        conversation_id:conversation_id.clone(),
        title:restored_title.clone(),
        remote_revision: *remote_revision,
    })
}

/// Reading cloud history is a complete user action: every eligible cloud conversation is
/// restored immediately.  The renderer never receives document IDs as a second, manual
/// recovery step. Bound direct documents use bounded atomic groups and per-item
/// savepoints. An interruption keeps committed groups; an uncommitted group has
/// no published receipts. Unbound and legacy paths keep single-document commits.
pub fn restore_all_remote_conversations<C: CredentialStore, G: CloudGateway>(
    connection: &mut Connection,
    credentials: &C,
    gateway: &G,
) -> Result<RestoreAllReceipt, String> {
    let account = read_session(credentials)?.map(|s| sync_state_v1::account_ref(&s.user_id)).transpose()?;
    // Snapshot receipts before the network request. Do not prune a selection
    // created or updated while that request was in flight.
    let before: Vec<(String,String,String,i64,String,String,i64)> = if let Some(account) = account.as_ref() {
        let mut statement = connection.prepare("SELECT workspace_id,conversation_id,document_id,remote_revision,payload_hash,local_content_hash,last_synced_at_ms FROM desktop_selected_conversation_sync WHERE account_ref=?1")
            .map_err(|_| safe_error("本机同步列表无法读取"))?;
        let rows = statement.query_map([account], |r| Ok((r.get(0)?,r.get(1)?,r.get(2)?,r.get(3)?,r.get(4)?,r.get(5)?,r.get(6)?)))
            .map_err(|_| safe_error("本机同步列表无法读取"))?;
        rows.collect::<Result<Vec<_>,_>>().map_err(|_| safe_error("本机同步列表无法读取"))?
    } else { Vec::new() };
    let cancel_epochs=account.as_deref().map(|account|cancellation_epochs(connection,account)).transpose()?.unwrap_or_default();
    let started = std::time::Instant::now();
    let documents = match gateway.list() {
        Err(CloudFailure::Known | CloudFailure::ServerUnavailable)
            if started.elapsed() < Duration::from_secs(5) => {
                // One bounded retry for a fast transient read failure only.
                // Never retry authorization, corruption, rate limits or writes;
                // a full request timeout must not start another long wait.
                std::thread::sleep(Duration::from_millis(200));
                gateway.list()
            }
        result => result,
    }.map_err(|failure| safe_error(cloud_read_failure_message(failure)))?;
    if read_session(credentials)?.map(|s| sync_state_v1::account_ref(&s.user_id)).transpose()? != account {
        return Err(safe_error("登录状态已变化，未应用云端列表。"));
    }
    with_local_occurrence_locator(connection, |connection| {
    // Reused for preparation only, never as a write authorization. Each group
    // re-reads this owner in its actual IMMEDIATE writer snapshot.
    let restore_area=desktop_data_area::read_area(connection)?;
    // Decode each public header once. Retain only ID/format, never parsed bodies;
    // these observations cannot authorize a write or bypass strict protocol checks.
    let public_identities=documents.iter().map(|d|envelope_public_identity(&d.envelope)).collect::<Vec<_>>();
    let present_ids=public_identities.iter().map(|identity|identity.document_id.as_ref()
        .map(|id|id.clone()).map_err(|error|error.clone())).collect::<Result<BTreeSet<_>,_>>();
    // Migrate only cryptographically verified legacy envelopes with CAS and exact readback.
    let mut upgraded_legacy_direct_count = 0;
    let mut restored = Vec::with_capacity(documents.len());
    let mut failed_count = 0;
    let mut failure_reasons = BTreeMap::<String, usize>::new();
    let mut skipped_legacy_count = 0;
    let mut cloud_pinned_conversation_ids = BTreeSet::new();
    let mut cloud_list_presentation_present = false;
    let mut pending=Vec::with_capacity(CLOUD_RESTORE_GROUP_DOCUMENTS);
    let mut pending_bytes=0usize;
    for (document,public_identity) in documents.into_iter().zip(public_identities) {
        let format=public_identity.format.as_deref();
        let was_legacy = format == Some(sync_v1::ENVELOPE);
        let prepared_identity = if format == Some(sync_v1::DIRECT_ENVELOPE) {
            credentials.prepared_account_write_user_id()
        } else { None };
        // Unidentifiable public rows cannot authorize repeatedly reopening the
        // private session. Legacy envelopes retain their verified migration
        // path; local receipt cleanup below still runs under the account guard.
        if !was_legacy && public_identity.document_id.is_err() {
            failed_count += 1;
            continue;
        }
        if let Some(user_id) = prepared_identity {
            // Only bound direct reads may prepare using their operation identity.
            // Every atomic write transaction and final cleanup uses a fresh guard.
            if Some(sync_state_v1::account_ref(user_id)?) != account {
                return Err(safe_error("登录状态已变化，已停止读取"));
            }
        } else if read_session(credentials)?.map(|session| sync_state_v1::account_ref(&session.user_id)).transpose()? != account {
            return Err(safe_error("登录状态已变化，已停止读取"));
        }
        let group_eligible=prepared_identity.is_some() && !was_legacy
            && format==Some(sync_v1::DIRECT_ENVELOPE);
        if !group_eligible {
            // Legacy migration may access the network. Commit pending local
            // documents first; never perform migration inside a write group.
            flush_prepared_conversation_group(connection,credentials,&mut pending,
                &mut restored,&mut failed_count,&mut failure_reasons)?;
            pending_bytes=0;
        }
        let document = if let Some(account) = account.as_deref().filter(|_|was_legacy) {
            match migrate_legacy_document(connection, credentials, gateway, account, document) {
                Ok(document) => {
                    if was_legacy {
                        upgraded_legacy_direct_count += 1;
                    }
                    document
                },
                Err(error) => {
                    failed_count += 1;
                    let code = if error.contains("旧加密记录") { "LEGACY_DEVICE_REQUIRED" } else { "LEGACY_MIGRATION_PENDING" };
                    *failure_reasons.entry(code.into()).or_default() += 1;
                    write_diagnostic(connection, Some(account), "CLOUD_DOCUMENT_MIGRATION", "FAILED", Some(code));
                    continue;
                }
            }
        } else {
            document
        };
        // Verified legacy migration produces a different envelope; its header
        // must be observed again. Direct/unknown formats retain the original one.
        let identity=if was_legacy {envelope_document_identity(&document.envelope)}
            else {public_identity.document_id.map(|id|(id,public_identity.format))};
        let Ok((document_id,format)) = identity else {
            failed_count += 1;
            continue;
        };
        if matches!(document_id.as_str(), CLOUD_LIST_PRESENTATION_DOCUMENT_ID | WORK_CLOUD_LIST_DOCUMENT_ID) {
            flush_prepared_conversation_group(connection,credentials,&mut pending,
                &mut restored,&mut failed_count,&mut failure_reasons)?;
            pending_bytes=0;
            if document_id != cloud_list_document_id(connection)? {
                continue;
            }
            match cloud_list_presentation_ids(connection, credentials, &document) {
                Ok(pinned) => {
                    cloud_pinned_conversation_ids = pinned;
                    cloud_list_presentation_present = true;
                }
                Err(_) => {
                    failed_count += 1;
                    *failure_reasons.entry("CLOUD_PRESENTATION_FAILED".into()).or_default() += 1;
                    write_diagnostic(connection, account.as_deref(), "CLOUD_PRESENTATION_RESTORE", "FAILED", Some("CLOUD_PRESENTATION_FAILED"));
                }
            }
            continue;
        }
        if format.as_deref() != Some(sync_v1::DIRECT_ENVELOPE) {
            skipped_legacy_count += 1;
            continue;
        }
        let result=if group_eligible {
            let bytes=document.envelope.len();
            match prepare_remote_conversation_restore_from_public_identity(connection,credentials,&document,Some(restore_area),document_id,format) {
                Ok(mut prepared)=>{
                    prepared.cancel_epoch=Some(cancel_epochs.get(&prepared.document_id).copied());
                    if !pending.is_empty() && pending_bytes.saturating_add(bytes)>CLOUD_RESTORE_GROUP_BYTES {
                        flush_prepared_conversation_group(connection,credentials,&mut pending,
                            &mut restored,&mut failed_count,&mut failure_reasons)?;
                        pending_bytes=0;
                    }
                    pending.push(prepared);
                    pending_bytes=pending_bytes.saturating_add(bytes);
                    // A permitted document larger than the target byte budget
                    // gets a one-document group; it is never truncated/refused.
                    if pending.len()>=CLOUD_RESTORE_GROUP_DOCUMENTS || pending_bytes>=CLOUD_RESTORE_GROUP_BYTES {
                        flush_prepared_conversation_group(connection,credentials,&mut pending,
                            &mut restored,&mut failed_count,&mut failure_reasons)?;
                        pending_bytes=0;
                    }
                    continue;
                },
                Err(error)=>Err(error),
            }
        } else { let observed=cancel_epochs.get(&document_id).copied();restore_remote_envelope_observed(connection, credentials, document,Some(observed)) };
        match result {
            Ok(receipt) => restored.push(receipt),
            Err(error) => {
                // Stop an invalidated bound inventory rather than spending its
                // remaining item budget on rejected writes. Ordinary document/SQL
                // failures retain independent-item isolation, including area skips.
                if prepared_identity.is_some() && read_session(credentials)?.map(|session| sync_state_v1::account_ref(&session.user_id)).transpose()? != account {
                    return Err(safe_error("登录状态已变化，已停止读取"));
                }
                if error.contains("OTHER_DATA_AREA") { continue; }
                failed_count += 1;
                let code = restore_failure_code(&error);
                *failure_reasons.entry(code.into()).or_default() += 1;
                write_diagnostic(connection, account.as_deref(), "CLOUD_DOCUMENT_RESTORE", "FAILED", Some(code));
            }
        }
    }
    flush_prepared_conversation_group(connection,credentials,&mut pending,
        &mut restored,&mut failed_count,&mut failure_reasons)?;
    if skipped_legacy_count != 0 {
        write_diagnostic(
            connection,
            None,
            "CLOUD_LIST_LEGACY_SKIPPED",
            "SUCCESS",
            Some("LEGACY_DIRECT_PROTOCOL_RETIRED"),
        );
    }
    let cloud_conversation_keys = credentials.with_account_write(|| { Ok(if let (Some(account), Ok(present_ids)) = (account.as_ref(), present_ids) {
        let transaction = connection.transaction().map_err(|_| safe_error("云端列表核对事务未开启"))?;
        for (workspace, conversation, document, revision, payload_hash, local_hash, timestamp) in before {
            if present_ids.contains(&document) { continue; }
            let removed = transaction.execute("DELETE FROM desktop_selected_conversation_sync WHERE account_ref=?1 AND workspace_id=?2 AND conversation_id=?3 AND document_id=?4 AND remote_revision=?5 AND payload_hash=?6 AND local_content_hash=?7 AND last_synced_at_ms=?8",
                params![account,workspace,conversation,document,revision,payload_hash,local_hash,timestamp]).map_err(|_| safe_error("失效同步身份未清理"))?;
            if removed > 0 {
                transaction.execute("DELETE FROM desktop_sync_jobs WHERE account_ref=?1 AND workspace_id=?2 AND conversation_id=?3 AND stage NOT IN ('UNKNOWN','COMMITTING','CANCELLED')",
                    params![account,workspace,conversation]).map_err(|_| safe_error("失效同步任务未清理"))?;
            }
        }
        transaction.commit().map_err(|_| safe_error("云端列表核对未保存"))?;
        let mut statement = connection.prepare("SELECT workspace_id || ':' || conversation_id FROM desktop_selected_conversation_sync WHERE account_ref=?1")
            .map_err(|_| safe_error("云端列表身份无法读取"))?;
        let rows = statement.query_map([account], |r| r.get(0)).map_err(|_| safe_error("云端列表身份无法读取"))?;
        Some(rows.collect::<Result<Vec<String>,_>>().map_err(|_| safe_error("云端列表身份无法读取"))?)
    } else { None }) })?;
    Ok(RestoreAllReceipt {
        restored,
        failed_count,
        failure_reasons,
        cloud_conversation_keys,
        skipped_legacy_count,
        cloud_pinned_conversation_ids: cloud_pinned_conversation_ids.into_iter().collect(),
        cloud_list_presentation_present,
        upgraded_legacy_direct_count,
    })
    })
}


fn cloud_list_presentation_ids<C: CredentialStore>(
    connection: &Connection,
    credentials: &C,
    remote: &RemoteEnvelope,
) -> Result<BTreeSet<String>, String> {
    let list_document_id = cloud_list_document_id(connection)?;
    let session = read_session(credentials)?.ok_or_else(|| safe_error("请先登录 Google 账号"))?;
    let account = sync_state_v1::account_ref(&session.user_id)?;
    let payload = open_account_document(connection, credentials, &account, remote, list_document_id)?;
    let records = payload
        .get("records")
        .and_then(Value::as_array)
        .ok_or_else(|| safe_error("云端列表投影无效"))?;
    let record = records
        .iter()
        .find(|record| {
            record.get("kind").and_then(Value::as_str) == Some("safe_settings")
                && record.get("id").and_then(Value::as_str)
                    == Some(list_document_id)
        })
        .ok_or_else(|| safe_error("云端列表投影无效"))?;
    let content = record
        .get("content")
        .and_then(Value::as_object)
        .ok_or_else(|| safe_error("云端列表投影无效"))?;
    if content.get("type").and_then(Value::as_str) != Some("CLOUD_CONVERSATION_LIST_V1") {
        return Err(safe_error("云端列表投影无效"));
    }
    let values = content
        .get("pinnedConversationIds")
        .and_then(Value::as_array)
        .ok_or_else(|| safe_error("云端列表投影无效"))?;
    if values.len() > 10_000 {
        return Err(safe_error("云端列表投影过大"));
    }
    values
        .iter()
        .map(|value| {
            let id = value
                .as_str()
                .ok_or_else(|| safe_error("云端列表投影无效"))?;
            if id.len() < 2
                || id.len() > 128
                || !id
                    .chars()
                    .all(|c| c.is_ascii_alphanumeric() || matches!(c, '.' | '_' | '-'))
            {
                return Err(safe_error("云端列表投影无效"));
            }
            Ok(id.to_owned())
        })
        .collect()
}

/// Writes IDs only.  It never opens a workspace, mutates a local pin, or serializes a title/body.
pub fn set_cloud_list_pinned<C: CredentialStore, G: CloudGateway>(
    connection: &Connection,
    credentials: &C,
    gateway: &G,
    conversation_id: &str,
    pinned: bool,
) -> Result<Vec<String>, String> {
    if conversation_id.len() < 2
        || conversation_id.len() > 128
        || !conversation_id
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || matches!(c, '.' | '_' | '-'))
    {
        return Err(safe_error("云端对话标识无效"));
    }
    let session = read_session(credentials)?.ok_or_else(|| safe_error("请先登录 Google 账号"))?;
    let list_document_id = cloud_list_document_id(connection)?;
    let remote = gateway
        .read(list_document_id)
        .map_err(|failure| safe_error(cloud_read_failure_message(failure)))?;
    require_same_session(credentials, &session)?;
    let (expected_revision, mut values) = match remote {
        Some(remote) => (remote.revision, cloud_list_presentation_ids(connection, credentials, &remote)?),
        None => (0, BTreeSet::new()),
    };
    if pinned {
        values.insert(conversation_id.to_owned());
    } else {
        values.remove(conversation_id);
    }
    let payload = json!({
        "format":"nfai.sync.payload", "protocolVersion":1, "schemaVersion":1,
        "appId":APP_ID, "documentId":list_document_id,
        "revision":expected_revision + 1,
        "records":[{"kind":"safe_settings","id":list_document_id,
          "revision":expected_revision + 1,"classification":"NORMAL",
          "content":{"type":"CLOUD_CONVERSATION_LIST_V1","pinnedConversationIds":values.iter().collect::<Vec<_>>()}}]
    });
    let envelope = sync_v1::seal_direct(payload).map_err(|_| safe_error("云端列表置顶无法准备"))?;
    let (revision, hash) = gateway
        .commit(
            list_document_id,
            expected_revision,
            &envelope,
        )
        .map_err(|failure| safe_error(cloud_read_failure_message(failure)))?;
    let read_back = gateway
        .read(list_document_id)
        .map_err(|failure| safe_error(cloud_read_failure_message(failure)))?
        .ok_or_else(|| safe_error("云端列表置顶回读失败"))?;
    if read_back.revision != revision || read_back.payload_hash != hash {
        return Err(safe_error("云端列表置顶回读不一致"));
    }
    Ok(values.into_iter().collect())
}

fn restore_portable_model_usage(restored: &mut Value, usage: &serde_json::Map<String, Value>) {
    let model_id = usage.get("modelId").and_then(Value::as_str)
        .filter(|value| !value.is_empty() && value.len() <= 256);
    let display_name = usage.get("modelDisplayName").and_then(Value::as_str)
        .filter(|value| !value.is_empty() && value.len() <= 256);
    let (Some(model_id), Some(display_name)) = (model_id, display_name) else { return; };
    restored["modelSnapshot"] = json!({"modelId":model_id,"displayName":display_name});
    if usage.get("factsVersion").and_then(Value::as_u64) == Some(2) {
        restored["actualModelId"] = usage.get("actualModelId").cloned().unwrap_or(Value::Null);
    }
    restored["usage"] = json!({});
    for key in ["inputTokens", "outputTokens", "totalTokens", "cachedInputTokens", "reasoningTokens"] {
        if let Some(value) = usage.get(key).and_then(Value::as_u64) {
            restored["usage"][key] = json!(value);
        }
    }
    // Amount, denomination, tariff and provenance are independent facts. A
    // missing settlement must not discard known metadata or become a zero.
    let cost = usage.get("costTotalMicros").and_then(Value::as_u64);
    restored["chargeMicros"] = cost.map(|value| json!(value)).unwrap_or(Value::Null);
    restored["currencyCode"] = usage.get("costCurrencyCode").cloned()
        .unwrap_or_else(|| if cost.is_some() {json!("USD")} else {Value::Null});
    restored["costPriceVersion"] = usage.get("costPriceVersion").cloned()
        .unwrap_or_else(|| if cost.is_some() {json!("android-history-v1")} else {Value::Null});
    restored["costSource"] = usage.get("costSource").cloned().unwrap_or(Value::Null);
}

/// Android's selected-conversation wire shape is intentionally text-only but names tree fields
/// differently from Desktop's older local exchange shape. Normalize it at the encrypted boundary
/// so neither the restore writer nor user-visible history needs a second schema.
fn normalize_android_selected_conversation(
    conversation: &mut serde_json::Map<String, Value>,
) -> Result<(), String> {
    let Some(nodes) = conversation.get("nodes").and_then(Value::as_array) else {
        // Desktop already uses the local message-tree shape, but its portable
        // modelUsage still needs projection back into the local answer fields.
        if let Some(messages) = conversation.get_mut("messages").and_then(Value::as_array_mut) {
            for message in messages {
                let usage = message.get("modelUsage").and_then(Value::as_object).cloned();
                if let Some(usage) = usage {
                    if !message.get("role").and_then(Value::as_str)
                        .is_some_and(|role| role.eq_ignore_ascii_case("assistant")) {
                        return Err(safe_error("云端模型用量只能属于助手回答"));
                    }
                    restore_portable_model_usage(message, &usage);
                    if let Some(object) = message.as_object_mut() { object.remove("modelUsage"); }
                }
            }
        }
        return Ok(());
    };
    if nodes.is_empty() || nodes.len() > 1_000 {
        return Err(safe_error("云端对话消息数量无效"));
    }
    let timestamp = |value: Option<&Value>| -> Result<String, String> {
        value
            .and_then(Value::as_i64)
            .filter(|millis| *millis >= 0)
            .map(crate::rfc3339_from_unix_millis)
            .ok_or_else(|| safe_error("云端对话时间无效"))
    };
    let mut messages = Vec::with_capacity(nodes.len());
    for node in nodes {
        let object = node
            .as_object()
            .ok_or_else(|| safe_error("云端对话消息无效"))?;
        let id = object
            .get("id")
            .and_then(Value::as_str)
            .filter(|value| !value.is_empty() && value.len() <= 128)
            .ok_or_else(|| safe_error("云端消息标识无效"))?;
        let role = object
            .get("role")
            .and_then(Value::as_str)
            .map(str::to_ascii_lowercase)
            .filter(|value| matches!(value.as_str(), "user" | "assistant" | "system"))
            .ok_or_else(|| safe_error("云端消息角色无效"))?;
        let delivery = object
            .get("deliveryState")
            .and_then(Value::as_str)
            .filter(|value| *value == "COMPLETE")
            .ok_or_else(|| safe_error("未完成的回复不会恢复"))?;
        let text = object
            .get("text")
            .and_then(Value::as_array)
            .filter(|items| !items.is_empty() && items.len() <= 64)
            .ok_or_else(|| safe_error("云端消息文本无效"))?;
        let blocks = text
            .iter()
            .map(|item| {
                item.as_str()
                    .filter(|value| !value.is_empty() && value.len() <= 32 * 1024)
                    .map(|value| json!({"kind":"TEXT","text":value}))
                    .ok_or_else(|| safe_error("云端消息文本无效"))
            })
            .collect::<Result<Vec<_>, _>>()?;
        let mut restored = json!({
            "id":id,
            "parentId":object.get("parentMessageId").cloned().unwrap_or(Value::Null),
            "ordinal":object.get("siblingPosition").and_then(Value::as_u64).unwrap_or(0),
            "role":role,
            "createdAt":timestamp(object.get("createdAtEpochMs"))?,
            "revision":object.get("revision").and_then(Value::as_u64).unwrap_or(1),
            "delivery":delivery,
            "blocks":blocks,
        });
        if let Some(usage) = object.get("modelUsage").and_then(Value::as_object) {
            if role != "assistant" {
                return Err(safe_error("云端模型用量只能属于助手回答"));
            }
            restore_portable_model_usage(&mut restored, usage);
        }
        messages.push(restored);
    }
    let created = timestamp(conversation.get("createdAtEpochMs"))?;
    let updated = timestamp(conversation.get("updatedAtEpochMs"))?;
    let leaf = conversation
        .get("currentLeafMessageId")
        .cloned()
        .unwrap_or_else(|| {
            messages
                .last()
                .and_then(|message| message.get("id"))
                .cloned()
                .unwrap_or(Value::Null)
        });
    conversation.remove("nodes");
    conversation.remove("currentLeafMessageId");
    conversation.remove("createdAtEpochMs");
    conversation.remove("updatedAtEpochMs");
    conversation.remove("surface");
    let pinned = conversation
        .remove("pinnedAtEpochMs")
        .and_then(|value| value.as_i64().map(|_| true))
        .unwrap_or(false);
    let archived = conversation
        .remove("archivedAtEpochMs")
        .and_then(|value| value.as_i64().map(|_| true))
        .unwrap_or(false);
    let favorited = conversation
        .remove("favoritedAtEpochMs")
        .and_then(|value| value.as_i64().map(|_| true))
        .unwrap_or(false);
    conversation.insert("createdAt".into(), Value::String(created));
    conversation.insert("updatedAt".into(), Value::String(updated));
    conversation.insert("currentLeafId".into(), leaf);
    conversation.insert("pinned".into(), Value::Bool(pinned));
    conversation.insert("archived".into(), Value::Bool(archived));
    conversation.insert("favorited".into(), Value::Bool(favorited));
    conversation.insert("messages".into(), Value::Array(messages));
    Ok(())
}

fn portable_reminder_plans(
    connection: &Connection,
    workspace_id: &str,
    conversation_id: &str,
) -> Result<Vec<Value>, String> {
    let exists = connection
        .query_row(
            "SELECT EXISTS(SELECT 1 FROM sqlite_master WHERE type='table' AND name='desktop_reminder_plans_v1')",
            [],
            |row| row.get::<_, i64>(0),
        )
        .unwrap_or(0)
        != 0;
    if !exists {
        return Ok(Vec::new());
    }
    let mut statement=connection.prepare("SELECT plan_id,title,instruction,schedule_kind,anchor_local,timezone_id,missed_policy,status,next_run_at_ms FROM desktop_reminder_plans_v1 WHERE workspace_id=?1 AND conversation_id=?2 AND status IN ('ACTIVE','PAUSED','COMPLETED','FAILED','UNKNOWN') ORDER BY created_at_ms,plan_id LIMIT 101").map_err(|_|safe_error("提醒计划无法读取"))?;
    let plans = statement
        .query_map(params![workspace_id, conversation_id], |row| {
            Ok(json!({
                "id":row.get::<_,String>(0)?,
                "title":row.get::<_,String>(1)?,
                "instruction":row.get::<_,String>(2)?,
                "scheduleKind":row.get::<_,String>(3)?,
                "anchorLocal":row.get::<_,String>(4)?,
                "timezoneId":row.get::<_,String>(5)?,
                "missedPolicy":row.get::<_,String>(6)?,
                "status":row.get::<_,String>(7)?,
                "nextRunAtMs":row.get::<_,Option<i64>>(8)?,
            }))
        })
        .map_err(|_| safe_error("提醒计划无法读取"))?
        .collect::<Result<Vec<_>, _>>()
        .map_err(|_| safe_error("提醒计划无效"))?;
    if plans.len() > 100 {
        return Err(safe_error("提醒计划数量超出同步限制"));
    }
    Ok(plans)
}

/// The direct cross-device conversation boundary is text-only.  Attachments,
/// previews, tool/runtime blocks and their metadata all remain local, so an
/// existing local attachment preview is never changed merely by syncing.
fn portable_message_blocks(blocks: &[Value]) -> Result<Vec<Value>, String> {
    blocks
        .iter()
        .filter_map(|block| -> Option<Result<Value, String>> {
            let kind = match block.get("kind").and_then(Value::as_str) {
                Some(kind) => kind,
                None => return Some(Err(safe_error("对话消息无效"))),
            };
            // An ASSET_REF must not block its adjacent text, but neither its bytes
            // nor display metadata are part of this portable record. TOOL_RESULT is
            // local runtime evidence for the same reason.
            (kind == "TEXT")
                .then(|| {
                    block
                        .get("text")
                        .and_then(Value::as_str)
                        .filter(|text| !text.is_empty())
                })
                .flatten()
                .map(|text| Ok(json!({ "kind": "TEXT", "text": text })))
        })
        .collect()
}

/// Portable answer history deliberately retains model/cost facts but never a
/// provider route, endpoint, credential, or request body.
fn portable_model_usage(message: &Value) -> Option<Value> {
    let snapshot = message.get("modelSnapshot")?.as_object()?;
    let model_id = snapshot.get("modelId")?.as_str()?.trim();
    let display_name = snapshot.get("displayName")?.as_str()?.trim();
    if model_id.is_empty()
        || display_name.is_empty()
        || model_id.len() > 256
        || display_name.len() > 256
    {
        return None;
    }
    let usage = message
        .get("usage")
        .filter(|usage| ["inputTokens", "outputTokens", "totalTokens", "cachedInputTokens", "reasoningTokens"]
            .iter().any(|key| usage.get(*key).and_then(Value::as_u64)
                .is_some_and(|value| value <= 10_000_000)))
        .or_else(|| message.get("estimatedUsage"));
    let usage_token = |name: &str| {
        usage
            .and_then(|value| value.get(name))
            .and_then(Value::as_u64)
            .filter(|value| *value <= 10_000_000)
    };
    let cost = message
        .get("chargeMicros")
        .and_then(Value::as_u64)
        .filter(|value| *value <= 1_000_000_000_000);
    let cost_source = message.get("costSource").and_then(Value::as_str)
        .filter(|value| matches!(*value,"PROVIDER_RESPONSE"|"LOCAL_ESTIMATE"));
    let price_version = message.get("costPriceVersion").and_then(Value::as_str)
        .filter(|value| !value.is_empty() && value.len() <= 128)
        .or_else(|| (message.get("costPriceVersion").is_none() && cost.is_some() && cost_source == Some("PROVIDER_RESPONSE"))
            .then_some("desktop-provider-reported-v1"));
    let mut portable = json!({
        "modelId":model_id,
        "modelDisplayName":display_name,
        "inputTokens":usage_token("inputTokens"),
        "outputTokens":usage_token("outputTokens"),
        "totalTokens":usage_token("totalTokens"),
        "cachedInputTokens":usage_token("cachedInputTokens"),
        "reasoningTokens":usage_token("reasoningTokens"),
        "costPriceVersion":price_version,
        "costCurrencyCode": message.get("currencyCode").and_then(Value::as_str).filter(|value| value.len() == 3 && value.bytes().all(|byte| byte.is_ascii_uppercase())).or_else(|| (message.get("currencyCode").is_none() && cost.is_some()).then_some("USD")),
        "costTotalMicros":cost,
        "costSource":cost_source,
    });
    if let Some(actual) = message.get("actualModelId") {
        portable["factsVersion"] = json!(2);
        portable["actualModelId"] = actual.clone();
    }
    Some(portable)
}

fn preserve_local_model_usage(local_message: &Value, updated: &mut Value) -> Result<(), String> {
    let same_requested_model = local_message.pointer("/modelSnapshot/modelId").and_then(Value::as_str)
        .filter(|value| !value.is_empty())
        .is_some_and(|value| updated.pointer("/modelSnapshot/modelId").and_then(Value::as_str) == Some(value));
    let remote_actual = updated.get("actualModelId").cloned();
    // Capture only accounting facts before the existing whole-record precedence
    // replaces them. No message content or provider routing belongs here.
    let remote_unpriced = same_requested_model.then(|| {
        let mut facts = serde_json::Map::new();
        for key in ["usage", "chargeMicros", "currencyCode", "costPriceVersion", "costSource"] {
            if let Some(value) = updated.get(key) { facts.insert(key.into(), value.clone()); }
        }
        Value::Object(facts)
    });
    // A completed answer's model/cost fact is not mutable conversation state.  A
    // service settlement always outranks an estimate, regardless of which device
    // uploaded it.  Only preserve the local projection when the remote one is not a
    // provider settlement; this is the same precedence used by Android packing.
    if portable_model_usage(local_message).is_some()
        && !message_has_provider_settlement(&updated)
    {
        let updated_object = updated
            .as_object_mut()
            .ok_or_else(|| safe_error("云端消息内容无效"))?;
        for key in [
            "modelSnapshot",
            "actualModelId",
            "usage",
            "estimatedUsage",
            "chargeMicros",
            "currencyCode",
            "costPriceVersion",
            "costSource",
        ] {
            if let Some(value) = local_message.get(key) {
                updated_object.insert(key.into(), value.clone());
            } else {
                updated_object.remove(key);
            }
        }
        if same_requested_model && !updated.get("actualModelId").is_some_and(|value| value.is_string()) {
            if let Some(actual) = remote_actual { updated["actualModelId"] = actual; }
        }
        if let Some(remote) = remote_unpriced.as_ref() {
            enrich_unpriced_model_usage(updated, remote);
        }
    } else if !updated.get("actualModelId").is_some_and(|value| value.is_string()) {
        // A v1 portable settlement carries the requested model but no returned
        // identity. Replacing an estimate must not erase an already observed
        // identity for this same answer/model. Never borrow it for a different
        // requested model, or replace an explicitly supplied remote fact.
        let requested = |message: &Value| message.get("modelSnapshot")
            .and_then(|snapshot| snapshot.get("modelId"))
            .and_then(Value::as_str).map(str::to_owned);
        let local_model = requested(local_message).filter(|value| !value.is_empty());
        if local_model.is_some() && local_model == requested(updated) {
            if let Some(actual) = local_message.get("actualModelId").and_then(Value::as_str)
                .filter(|value| !value.is_empty() && value.len() <= 256)
            {
                updated["actualModelId"] = json!(actual);
            }
        }
    }
    Ok(())
}

fn enrich_unpriced_model_usage(selected: &mut Value, other: &Value) {
    let known = |value: Option<&Value>| value.filter(|value| !value.is_null()).cloned();
    if known(selected.get("chargeMicros")).is_some() || known(other.get("chargeMicros")).is_some() {
        return;
    }
    for facts in [&*selected, other] {
        if known(facts.get("costSource")).is_some_and(|source| source != "PROVIDER_RESPONSE") {
            return;
        }
    }
    for key in ["currencyCode", "costPriceVersion", "costSource"] {
        if let (Some(original), Some(incoming)) = (known(selected.get(key)), known(other.get(key))) {
            if original != incoming { return; }
        }
    }
    let counters = ["inputTokens", "outputTokens", "totalTokens", "cachedInputTokens", "reasoningTokens"];
    let mut incoming_is_superset = true;
    let mut incoming_has_report = false;
    for key in counters {
        let original = selected.get("usage").and_then(|usage| usage.get(key)).and_then(Value::as_u64);
        let incoming = other.get("usage").and_then(|usage| usage.get(key)).and_then(Value::as_u64);
        if original.is_some() && incoming.is_some() && original != incoming { return; }
        incoming_is_superset &= original.is_none() || original == incoming;
        incoming_has_report |= incoming.is_some();
    }
    // Keep one complete report; never assemble token counters from two reports.
    if incoming_is_superset && incoming_has_report {
        selected["usage"] = other["usage"].clone();
    }
    for key in ["currencyCode", "costPriceVersion", "costSource"] {
        if known(selected.get(key)).is_none() {
            if let Some(value) = known(other.get(key)) { selected[key] = value; }
        }
    }
}

fn message_has_provider_settlement(message: &Value) -> bool {
    message
        .get("chargeMicros")
        .and_then(Value::as_u64)
        .is_some()
        && message.get("costSource").and_then(Value::as_str) == Some("PROVIDER_RESPONSE")
}

fn conversation_payload(
    connection: &Connection,
    workspace_id: &str,
    conversation_id: &str,
    document_id: &str,
    revision: u64,
) -> Result<(Value, String), String> {
    let exchange: String = connection
        .query_row(
            "SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?1",
            [workspace_id],
            |row| row.get(0),
        )
        .map_err(|_| safe_error("工作区不存在"))?;
    let root: Value = serde_json::from_str(&exchange).map_err(|_| safe_error("工作区数据无效"))?;
    let conversation = root
        .get("conversations")
        .and_then(Value::as_array)
        .and_then(|items| {
            items
                .iter()
                .find(|item| item.get("id").and_then(Value::as_str) == Some(conversation_id))
        })
        .ok_or_else(|| safe_error("对话不存在"))?;
    if conversation.get("deleted").and_then(Value::as_bool) == Some(true) {
        return Err(safe_error("已删除的对话不会同步"));
    }
    if conversation
        .get("draft")
        .is_some_and(|draft| !draft.is_null() && draft != "")
    {
        return Err(safe_error("请先发送或清空未完成草稿"));
    }
    let messages = conversation
        .get("messages")
        .and_then(Value::as_array)
        .ok_or_else(|| safe_error("对话消息无效"))?;
    // A provider can terminate one branch with a timeout, cancellation, or a
    // credential error after previous turns have already been committed.  That
    // must not make the whole conversation unsyncable.  Build the portable
    // copy from the connected, completed portion only; the source workspace is
    // never rewritten, so its diagnostic/partial branch remains available
    // locally for retry and inspection.
    let mut safe_messages = Vec::with_capacity(messages.len());
    let mut retained_message_ids = BTreeSet::new();
    let mut retained_message_bytes = BTreeMap::new();
    let mut portable_parent_by_source_id = BTreeMap::<String, Option<String>>::new();
    // Local exchange order is not a tree guarantee: imports and branch edits
    // can place a child before its structural attachment/tool parent. Resolve
    // the completed tree to a fixed point instead of silently losing that
    // later text turn because its parent has not appeared in this array yet.
    let mut pending = messages.iter().collect::<Vec<_>>();
    while !pending.is_empty() {
        let mut deferred = Vec::new();
        let mut progressed = false;
        for message in pending {
            let is_complete = !message
                .get("delivery")
                .and_then(Value::as_str)
                .is_some_and(|value| value != "COMPLETE");
            let parent_id = message.get("parentId").and_then(Value::as_str);
            if !is_complete {
                // An unfinished branch is intentionally local-only. Its
                // descendants cannot become a portable history until that
                // branch is completed by the source device.
                continue;
            }
            if parent_id
                .is_some_and(|parent_id| !portable_parent_by_source_id.contains_key(parent_id))
            {
                deferred.push(message);
                continue;
            }
            progressed = true;
            let blocks = message
                .get("blocks")
                .and_then(Value::as_array)
                .ok_or_else(|| safe_error("对话消息无效"))?;
            let message_id = message
                .get("id")
                .and_then(Value::as_str)
                .ok_or_else(|| safe_error("对话消息无效"))?;
            let canonical_source =
                serde_json::to_string(message).map_err(|_| safe_error("对话消息无效"))?;
            if let Some(previous) = retained_message_bytes.get(message_id) {
                if previous == &canonical_source {
                    // A historical write can contain the exact same message twice.
                    // It is one occurrence, not a second portable turn.
                    continue;
                }
                return Err(safe_error("对话消息标识冲突，未同步。"));
            }
            let portable_blocks = portable_message_blocks(blocks)?;
            let portable_parent_id = parent_id
                .and_then(|parent_id| portable_parent_by_source_id.get(parent_id))
                .cloned()
                .flatten();
            // Attachments and runtime records stay local.  They must not cut off a
            // later completed text reply: skip only their own payload and connect
            // descendants to the closest portable text parent.
            if !portable_blocks.iter().any(|block| block["kind"] == "TEXT") {
                portable_parent_by_source_id.insert(message_id.to_owned(), portable_parent_id);
                continue;
            }
            let mut portable = json!({
                "id":message.get("id"), "parentId":portable_parent_id, "ordinal":message.get("ordinal").and_then(Value::as_u64).unwrap_or(0),
                "role":message.get("role"), "createdAt":message.get("createdAt"), "revision":message.get("revision").and_then(Value::as_u64).unwrap_or(1),
                "delivery":"COMPLETE",
                "blocks":portable_blocks
            });
            if let Some(model_usage) = portable_model_usage(message) {
                portable["modelUsage"] = model_usage;
            }
            safe_messages.push(portable);
            retained_message_ids.insert(message_id.to_owned());
            retained_message_bytes.insert(message_id.to_owned(), canonical_source);
            portable_parent_by_source_id.insert(message_id.to_owned(), Some(message_id.to_owned()));
        }
        if !progressed {
            // A child of an incomplete/missing parent is not a complete,
            // connected conversation turn. Keep it local without failing the
            // already completed portable history.
            break;
        }
        pending = deferred;
    }
    if safe_messages.is_empty() {
        return Err(safe_error("没有可同步的已完成消息"));
    }
    let current_leaf_id = conversation
        .get("currentLeafId")
        .and_then(Value::as_str)
        .filter(|id| retained_message_ids.contains(*id))
        .map(str::to_owned)
        .or_else(|| {
            safe_messages
                .last()
                .and_then(|message| message.get("id"))
                .and_then(Value::as_str)
                .map(str::to_owned)
        });
    let favorited: bool = connection
        .query_row(
            "SELECT EXISTS(SELECT 1 FROM desktop_conversation_favorites WHERE workspace_id=?1 AND conversation_id=?2)",
            params![workspace_id, conversation_id],
            |row| row.get(0),
        )
        .unwrap_or(false);
    let mut content = json!({"title":conversation.get("title").and_then(Value::as_str).unwrap_or("未命名会话"),"createdAt":conversation.get("createdAt"),"updatedAt":conversation.get("updatedAt"),"currentLeafId":current_leaf_id,"pinned":conversation.get("pinned").and_then(Value::as_bool).unwrap_or(false),"archived":conversation.get("archived").and_then(Value::as_bool).unwrap_or(false),"favorited":favorited,"messages":safe_messages});
    content["surface"] = desktop_data_area::read_area(connection)?.wire().into();
    if let Some(version) = conversation.get("titleRevision").filter(|v| !v.is_null()) {
        content["titleRevision"] = version.clone();
    }
    let interests: String = connection
        .query_row(
            "SELECT interests FROM desktop_portable_personalization_v1 WHERE id=1",
            [],
            |row| row.get(0),
        )
        .unwrap_or_default();
    let reminder_plans = portable_reminder_plans(connection, workspace_id, conversation_id)?;
    let records = json!([
        {"kind":"conversation","id":conversation_id,"revision":conversation.get("revision").and_then(Value::as_u64).unwrap_or(1),"classification":"NORMAL","content":content},
        // Android ignores this safe-settings record while restoring a conversation.  The marker
        // lets a Desktop source prove that its direct envelope was emitted by the portable
        // writer, so an explicit cloud read can migrate only its own older documents once.
        {"kind":"safe_settings","id":"profile-interests","revision":1,"classification":"NORMAL","content":{"interests":interests,"portableConversationFormat":4}},
        {"kind":"relation","id":format!("reminder-plans-{}",&sha256(conversation_id.as_bytes())[..24]),"revision":1,"classification":"NORMAL","content":{"type":"REMINDER_PLANS_V1","plans":reminder_plans}}
    ]);
    let local_hash = sha256(
        serde_json::to_string(&records)
            .map_err(|_| safe_error("对话及安全设置无法编码"))?
            .as_bytes(),
    );
    let payload = json!({"format":"nfai.sync.payload","protocolVersion":1,"schemaVersion":1,"appId":APP_ID,"documentId":document_id,"revision":revision,"records":records});
    Ok((payload, local_hash))
}

/// Direct envelopes written before portable-history v2 can omit the answer's
/// model and settled amount even when the source has those facts. They may also
/// contain terminal retry branches or duplicate message occurrences. A later
/// explicit read from the source rewrites that same document with the current
/// completed-tree projection instead of returning UP_TO_DATE.
fn remote_requires_current_portable_rewrite(envelope: &str) -> bool {
    let Ok(root) = serde_json::from_str::<Value>(envelope) else {
        return true;
    };
    let Some(records) = root.pointer("/payload/records").and_then(Value::as_array) else {
        return true;
    };
    let Some(messages) = records
        .iter()
        .find(|record| record.get("kind").and_then(Value::as_str) == Some("conversation"))
        .and_then(|record| record.pointer("/content/messages"))
        .and_then(Value::as_array)
    else {
        return true;
    };
    let has_current_portable_marker = records.iter().any(|record| {
        record.get("kind").and_then(Value::as_str) == Some("safe_settings")
            && record.get("id").and_then(Value::as_str) == Some("profile-interests")
            && record
                .pointer("/content/portableConversationFormat")
                .and_then(Value::as_u64)
                == Some(4)
    });
    if !has_current_portable_marker {
        return true;
    }
    let mut ids = BTreeSet::new();
    messages.iter().any(|message| {
        let blocks = message.get("blocks").and_then(Value::as_array);
        let portable_text = blocks.is_some_and(|blocks| {
            blocks.iter().any(|block| {
                block.get("kind").and_then(Value::as_str) == Some("TEXT")
                    && block
                        .get("text")
                        .and_then(Value::as_str)
                        .is_some_and(|text| !text.is_empty())
            })
        });
        let portable_blocks_only = blocks.is_some_and(|blocks| {
            blocks
                .iter()
                .all(|block| block.get("kind").and_then(Value::as_str) == Some("TEXT"))
        });
        message.get("delivery").and_then(Value::as_str) != Some("COMPLETE")
            || message
                .get("id")
                .and_then(Value::as_str)
                .is_none_or(|id| !ids.insert(id))
            || !portable_text
            || !portable_blocks_only
    })
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SyncReceipt {
    pub status: String,
    pub safe_code: Option<String>,
    pub synced_at_ms: Option<i64>,
}

fn cancellation_job_id(account:&str,document:&str)->String {
    format!("sync-cancel-{}",sha256(format!("{account}:{document}").as_bytes()))
}
fn cancellation_epoch(connection:&Connection,account:&str,document:&str)->Result<Option<i64>,String> {
    let value=connection.query_row("SELECT attempt FROM desktop_sync_jobs WHERE job_id=?1 AND account_ref=?2 AND document_id=?3 AND stage='CANCELLED'",
        params![cancellation_job_id(account,document),account,document],|row|row.get::<_,i64>(0)).optional()
        .map_err(|_|safe_error("取消同步代次无法读取"))?;
    if value.is_some_and(|n|n<1){return Err(safe_error("取消同步代次无效"));}
    Ok(value)
}
fn cancellation_epochs(connection:&Connection,account:&str)->Result<BTreeMap<String,i64>,String> {
    let mut statement=connection.prepare("SELECT document_id,attempt,job_id FROM desktop_sync_jobs WHERE account_ref=?1 AND stage='CANCELLED'")
        .map_err(|_|safe_error("取消同步代次无法读取"))?;
    let rows=statement.query_map([account],|row|Ok((row.get::<_,String>(0)?,row.get::<_,i64>(1)?,row.get::<_,String>(2)?)))
        .map_err(|_|safe_error("取消同步代次无法读取"))?;
    let mut result=BTreeMap::new();
    for row in rows {
        let(document,epoch,job)=row.map_err(|_|safe_error("取消同步代次无法读取"))?;
        if epoch<1 || job!=cancellation_job_id(account,&document) || result.insert(document,epoch).is_some(){return Err(safe_error("取消同步代次无效"));}
    }
    Ok(result)
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CancelSyncReceipt {
    pub status: String,
}

/// Removes the account-owned remote document with an optimistic revision check.  The
/// corresponding local workspace is intentionally retained: "取消同步" only removes the
/// cloud copy, while the ordinary delete action remains the shared local lifecycle action.
pub fn cancel_remote_conversation<C: CredentialStore, G: CloudGateway>(
    connection: &mut Connection,
    credentials: &C,
    gateway: &G,
    workspace_id: &str,
    conversation_id: &str,
) -> Result<CancelSyncReceipt, String> {
    let session = read_session(credentials)?.ok_or_else(|| safe_error("请先登录 Google 账号"))?;
    let account = sync_state_v1::account_ref(&session.user_id)?;
    let document_id = selected_document_id(connection, &account, workspace_id, conversation_id)?;
    let remote = gateway
        .read(&document_id)
        .map_err(|failure| safe_error(cloud_read_failure_message(failure)))?;
    require_same_session(credentials, &session)?;
    if let Some(remote) = remote {
        let deleted = gateway
            .delete(&document_id, remote.revision)
            .map_err(|failure| safe_error(cloud_commit_failure_message(failure)))?;
        if !deleted {
            return Err(safe_error("云端对话未删除"));
        }
    }
    require_same_session(credentials, &session)?;
    credentials.with_account_write(|| {
        let transaction=connection.transaction_with_behavior(TransactionBehavior::Immediate)
            .map_err(|_|safe_error("取消同步事务无法开启"))?;
        transaction.execute("DELETE FROM desktop_selected_conversation_sync WHERE account_ref=?1 AND workspace_id=?2 AND conversation_id=?3",
            params![account,workspace_id,conversation_id]).map_err(|_|safe_error("本机同步回执未清除"))?;
        transaction.execute("DELETE FROM desktop_sync_jobs WHERE account_ref=?1 AND workspace_id=?2 AND conversation_id=?3 AND stage NOT IN ('UNKNOWN','COMMITTING','CANCELLED')",
            params![account,workspace_id,conversation_id]).map_err(|_|safe_error("失效同步任务未清理"))?;
        // Keep one body-free fact per account/document. A later explicit sync
        // does not erase this epoch, so cancel/resync/cancel cannot become ABA.
        let written=transaction.execute("INSERT INTO desktop_sync_jobs(job_id,account_ref,workspace_id,conversation_id,document_id,stage,attempt,created_at_ms,updated_at_ms)
            VALUES(?1,?2,?3,?4,?5,'CANCELLED',1,?6,?6)
            ON CONFLICT(job_id) DO UPDATE SET workspace_id=excluded.workspace_id,conversation_id=excluded.conversation_id,
              attempt=desktop_sync_jobs.attempt+1,updated_at_ms=excluded.updated_at_ms
            WHERE desktop_sync_jobs.account_ref=excluded.account_ref AND desktop_sync_jobs.document_id=excluded.document_id
              AND desktop_sync_jobs.stage='CANCELLED' AND desktop_sync_jobs.attempt<9223372036854775807",
            params![cancellation_job_id(&account,&document_id),account,workspace_id,conversation_id,document_id,now_ms()])
            .map_err(|_|safe_error("取消同步代次未保存"))?;
        if written!=1{return Err(safe_error("取消同步代次未保存"));}
        transaction.commit().map_err(|_|safe_error("取消同步未原子提交"))?;
        Ok(())
    })?;
    write_diagnostic(connection, Some(&account), "SYNC_CANCEL", "SUCCESS", None);
    write_notification(
        connection,
        Some(&account),
        "SUCCESS",
        "已取消该对话的南枫云同步，本机对话保留。",
    );
    Ok(CancelSyncReceipt {
        status: "CANCELLED".into(),
    })
}

fn mark_sync_commit_unknown(
    connection: &Connection,
    account: &str,
    job_id: &str,
) -> Result<SyncReceipt, String> {
    let transaction = connection.unchecked_transaction()
        .map_err(|_| safe_error("云端提交结果未知，本机核对事务未开启；请勿重复上传"))?;
    let timestamp = now_ms();
    let jobs = transaction.execute("UPDATE desktop_sync_jobs SET stage='UNKNOWN',safe_error_code='COMMIT_RESULT_UNKNOWN',updated_at_ms=?3 WHERE job_id=?1 AND account_ref=?2", params![job_id,account,timestamp])
        .map_err(|_| safe_error("云端提交结果未知，本机核对任务未保存；请勿重复上传"))?;
    if jobs != 1 { return Err(safe_error("云端提交结果未知，本机核对任务不存在；请勿重复上传")); }
    // A commit may have reached the service.  This state is pending
    // verification, not a failed write and never authorizes a duplicate send.
    let accounts = transaction.execute("UPDATE desktop_cloud_account_state SET state='VERIFYING',last_error_code='COMMIT_RESULT_UNKNOWN',updated_at_ms=?2 WHERE account_ref=?1", params![account,timestamp])
        .map_err(|_| safe_error("云端提交结果未知，本机核对状态未保存；请勿重复上传"))?;
    if accounts != 1 { return Err(safe_error("云端提交结果未知，本机账号状态不存在；请勿重复上传")); }
    transaction.commit().map_err(|_| safe_error("云端提交结果未知，本机核对状态未提交；请勿重复上传"))?;
    write_diagnostic(
        connection,
        Some(account),
        "SYNC_READBACK",
        "UNKNOWN",
        Some("COMMIT_RESULT_UNKNOWN"),
    );
    Ok(SyncReceipt {
        status: "UNKNOWN".into(),
        safe_code: Some("COMMIT_RESULT_UNKNOWN".into()),
        synced_at_ms: None,
    })
}

/// Repair historical duplicate occurrences only by stable conversation identity
/// and matching message ancestry/content. Never copy from another account's receipt.
fn recover_local_conversation_occurrences(
    connection: &mut Connection, account: &str, workspace_id: &str, conversation_id: &str,
) -> Result<(), String> {
    let transaction = connection.transaction().map_err(|_| safe_error("本机对话修复事务未开启"))?;
    recover_local_conversation_occurrences_in_transaction(
        &transaction, account, workspace_id, conversation_id,
    )?;
    transaction.commit().map_err(|_| safe_error("本机对话修复未提交"))
}

// Build the other-account workspace exclusion once per statement. A correlated
// NOT EXISTS is otherwise run for every unrelated workspace before JSON traversal.
// Workspace IDs are NOT NULL in the receipt schema, so NOT IN has the same exclusion
// semantics and still rejects protected workspaces before opening their JSON.
const LOCAL_OCCURRENCE_CANDIDATES_SQL: &str = "SELECT c.value FROM workspace_exchange w, json_each(w.exchange_json,'$.conversations') c
             WHERE w.workspace_id<>?1
             AND w.workspace_id NOT IN(SELECT s.workspace_id FROM desktop_selected_conversation_sync s
                 WHERE s.conversation_id=?2 AND s.account_ref<>?3)
             AND json_extract(c.value,'$.id')=?2
             AND coalesce(json_extract(c.value,'$.deleted'),0)=0
             ORDER BY w.workspace_id LIMIT 32";

const INDEXED_LOCAL_OCCURRENCE_CANDIDATES_SQL: &str = "SELECT c.value FROM workspace_exchange w, json_each(w.exchange_json,'$.conversations') c
    WHERE w.workspace_id<>?1
    AND w.workspace_id IN (SELECT workspace_id FROM temp.nfai_restore_occurrence_ids WHERE conversation_id=?2
                          UNION SELECT workspace_id FROM temp.nfai_restore_occurrence_bad)
    AND w.workspace_id NOT IN (SELECT workspace_id FROM temp.nfai_restore_occurrence_receipts WHERE conversation_id=?2 AND account_ref<>?3)
    AND json_extract(c.value,'$.id')=?2 AND coalesce(json_extract(c.value,'$.deleted'),0)=0
    ORDER BY w.workspace_id LIMIT 32";

// Connection-local derived IDs only. Canonical bodies and account ownership
// are still checked by the original candidate query and transaction. TEMP
// triggers track this connection's writes/rollbacks; data_version detects
// commits made by any other connection before the next guarded write.
fn with_local_occurrence_locator<T>(connection: &mut Connection, run: impl FnOnce(&mut Connection) -> Result<T,String>) -> Result<T,String> {
    let existing: i64 = connection.query_row("SELECT count(*) FROM temp.sqlite_master WHERE name GLOB 'nfai_restore_occurrence_*'", [], |r| r.get(0))
        .map_err(|_| safe_error("本机同源定位状态无法读取"))?;
    if existing != 0 { return Err(safe_error("本机同源定位作用域已存在")); }
    let install = (|| {
        connection.execute_batch("CREATE TEMP TABLE nfai_restore_occurrence_ids(conversation_id TEXT NOT NULL,workspace_id TEXT NOT NULL,PRIMARY KEY(conversation_id,workspace_id)) WITHOUT ROWID;
            CREATE INDEX temp.nfai_restore_occurrence_workspace_id ON nfai_restore_occurrence_ids(workspace_id);
            CREATE TEMP TABLE nfai_restore_occurrence_bad(workspace_id TEXT PRIMARY KEY NOT NULL) WITHOUT ROWID;
            CREATE TEMP TABLE nfai_restore_occurrence_receipts(conversation_id TEXT NOT NULL,workspace_id TEXT NOT NULL,account_ref TEXT NOT NULL,PRIMARY KEY(conversation_id,workspace_id,account_ref)) WITHOUT ROWID;
            CREATE TEMP TABLE nfai_restore_occurrence_version(value INTEGER NOT NULL);
            INSERT INTO nfai_restore_occurrence_version VALUES(-1);")?;
        for event in ["INSERT","UPDATE","DELETE"] {
            let mut workspace = String::new();
            if event != "INSERT" { workspace.push_str("DELETE FROM nfai_restore_occurrence_ids WHERE workspace_id=OLD.workspace_id; DELETE FROM nfai_restore_occurrence_bad WHERE workspace_id=OLD.workspace_id;"); }
            if event != "DELETE" {
                workspace.push_str("DELETE FROM nfai_restore_occurrence_ids WHERE workspace_id=NEW.workspace_id; DELETE FROM nfai_restore_occurrence_bad WHERE workspace_id=NEW.workspace_id;
                    INSERT INTO nfai_restore_occurrence_bad SELECT NEW.workspace_id WHERE NOT json_valid(NEW.exchange_json) OR EXISTS(SELECT 1 FROM json_each(CASE WHEN json_valid(NEW.exchange_json) THEN NEW.exchange_json ELSE '{}' END,'$.conversations') c WHERE c.value IS NOT NULL AND NOT json_valid(c.value));
                    INSERT OR IGNORE INTO nfai_restore_occurrence_ids SELECT json_extract(CASE WHEN json_valid(c.value) THEN c.value ELSE '{}' END,'$.id'),NEW.workspace_id FROM json_each(CASE WHEN json_valid(NEW.exchange_json) THEN NEW.exchange_json ELSE '{}' END,'$.conversations') c WHERE json_extract(CASE WHEN json_valid(c.value) THEN c.value ELSE '{}' END,'$.id') IS NOT NULL;");
            }
            connection.execute_batch(&format!("CREATE TEMP TRIGGER nfai_restore_occurrence_workspace_{event} AFTER {event} ON main.workspace_exchange BEGIN {workspace} END;"))?;
            let mut receipts = String::new();
            if event != "INSERT" { receipts.push_str("DELETE FROM nfai_restore_occurrence_receipts WHERE conversation_id=OLD.conversation_id AND workspace_id=OLD.workspace_id AND account_ref=OLD.account_ref;"); }
            if event != "DELETE" { receipts.push_str("INSERT OR REPLACE INTO nfai_restore_occurrence_receipts VALUES(NEW.conversation_id,NEW.workspace_id,NEW.account_ref);"); }
            connection.execute_batch(&format!("CREATE TEMP TRIGGER nfai_restore_occurrence_receipt_{event} AFTER {event} ON main.desktop_selected_conversation_sync BEGIN {receipts} END;"))?;
        }
        Ok::<_,rusqlite::Error>(())
    })();
    let result = match install { Ok(()) => run(connection), Err(_) => Err(safe_error("本机同源定位未准备")) };
    // Always clean this owned scope, including a partially installed scope or
    // early document/account refusal. Never change main schema or PRAGMAs.
    let cleanup = (|| {
        for event in ["INSERT","UPDATE","DELETE"] {
            connection.execute_batch(&format!("DROP TRIGGER IF EXISTS temp.nfai_restore_occurrence_workspace_{event}; DROP TRIGGER IF EXISTS temp.nfai_restore_occurrence_receipt_{event};"))?;
        }
        connection.execute_batch("DROP TABLE IF EXISTS temp.nfai_restore_occurrence_ids; DROP TABLE IF EXISTS temp.nfai_restore_occurrence_bad; DROP TABLE IF EXISTS temp.nfai_restore_occurrence_receipts; DROP TABLE IF EXISTS temp.nfai_restore_occurrence_version;")
    })();
    match result { Err(error) => Err(error), Ok(value) => cleanup.map(|_|value).map_err(|_|safe_error("本机同源定位清理未完成")) }
}

fn local_occurrence_candidates_sql(connection: &Connection) -> Result<&'static str,String> {
    let installed: bool = connection.query_row("SELECT EXISTS(SELECT 1 FROM temp.sqlite_master WHERE name='nfai_restore_occurrence_version')", [], |r| r.get(0))
        .map_err(|_| safe_error("本机同源定位状态无法读取"))?;
    if !installed { return Ok(LOCAL_OCCURRENCE_CANDIDATES_SQL); }
    let version: i64 = connection.query_row("PRAGMA main.data_version", [], |r| r.get(0)).map_err(|_|safe_error("本机同源版本无法核对"))?;
    let indexed: i64 = connection.query_row("SELECT value FROM temp.nfai_restore_occurrence_version", [], |r| r.get(0)).map_err(|_|safe_error("本机同源版本无法核对"))?;
    if version != indexed {
        // Called inside the existing body write transaction. Its writer lock
        // prevents another connection from committing during this rebuild.
        connection.execute_batch("DELETE FROM temp.nfai_restore_occurrence_ids; DELETE FROM temp.nfai_restore_occurrence_bad; DELETE FROM temp.nfai_restore_occurrence_receipts;
            INSERT OR IGNORE INTO temp.nfai_restore_occurrence_ids SELECT json_extract(CASE WHEN json_valid(c.value) THEN c.value ELSE '{}' END,'$.id'),w.workspace_id FROM main.workspace_exchange w,json_each(CASE WHEN json_valid(w.exchange_json) THEN w.exchange_json ELSE '{}' END,'$.conversations') c WHERE json_extract(CASE WHEN json_valid(c.value) THEN c.value ELSE '{}' END,'$.id') IS NOT NULL;
            INSERT INTO temp.nfai_restore_occurrence_bad SELECT w.workspace_id FROM main.workspace_exchange w WHERE NOT json_valid(w.exchange_json) OR EXISTS(SELECT 1 FROM json_each(CASE WHEN json_valid(w.exchange_json) THEN w.exchange_json ELSE '{}' END,'$.conversations') c WHERE c.value IS NOT NULL AND NOT json_valid(c.value));
            INSERT INTO temp.nfai_restore_occurrence_receipts SELECT conversation_id,workspace_id,account_ref FROM main.desktop_selected_conversation_sync;")
            .map_err(|_|safe_error("本机同源定位无法更新"))?;
        connection.execute("UPDATE temp.nfai_restore_occurrence_version SET value=?1", [version]).map_err(|_|safe_error("本机同源版本未保存"))?;
    }
    Ok(INDEXED_LOCAL_OCCURRENCE_CANDIDATES_SQL)
}

fn recover_local_conversation_occurrences_in_transaction(
    transaction: &rusqlite::Transaction<'_>, account: &str, workspace_id: &str, conversation_id: &str,
) -> Result<(), String> {
    let candidates: Vec<String> = {
        let mut statement = transaction.prepare(
            local_occurrence_candidates_sql(transaction)?
        ).map_err(|_| safe_error("本机同源对话无法核对"))?;
        let rows = statement.query_map(params![workspace_id, conversation_id, account], |row| row.get(0))
            .map_err(|_| safe_error("本机同源对话无法核对"))?;
        rows.collect::<Result<Vec<_>, _>>().map_err(|_| safe_error("本机同源对话无法读取"))?
    };
    if candidates.is_empty() { return Ok(()); }
    for candidate in candidates {
        let source: Value = serde_json::from_str(&candidate).map_err(|_| safe_error("本机同源对话结构无效"))?;
        let target_json: String = transaction.query_row(
            "SELECT c.value FROM workspace_exchange w,json_each(w.exchange_json,'$.conversations') c WHERE w.workspace_id=?1 AND json_extract(c.value,'$.id')=?2",
            params![workspace_id, conversation_id], |row| row.get(0),
        ).map_err(|_| safe_error("本机对话修复目标不存在"))?;
        let target: Value = serde_json::from_str(&target_json).map_err(|_| safe_error("本机对话结构无效"))?;
        let Some(source_messages) = source.get("messages").and_then(Value::as_array) else { continue; };
        let Some(target_messages) = target.get("messages").and_then(Value::as_array) else { continue; };
        let anchored = source_messages.iter().any(|original| target_messages.iter().any(|current|
            original.get("id") == current.get("id") && original.get("id").and_then(Value::as_str).is_some()
            && original.get("parentId") == current.get("parentId") && original.get("role") == current.get("role")
            && !portable_message_text(original).is_empty() && portable_message_text(original) == portable_message_text(current)));
        if !anchored { continue; }
        // Only fill missing identities. Existing text/title/model/cost facts
        // remain owned by the selected copy and the normal remote merge.
        let mut recovery = target.clone();
        recovery["messages"] = Value::Array(source_messages.iter().map(|original|
            target_messages.iter().find(|current| current.get("id") == original.get("id"))
                .unwrap_or(original).clone()).collect());
        recovery["currentLeafId"] = source.get("currentLeafId").cloned().unwrap_or(Value::Null);
        merge_remote_additions_for_local_commit(transaction, workspace_id, conversation_id,
            recovery.as_object().ok_or_else(|| safe_error("本机同源对话结构无效"))?,
            target.get("revision").and_then(Value::as_u64).unwrap_or(1))?;
    }
    Ok(())
}

pub fn sync_selected_conversation<C: CredentialStore, G: CloudGateway>(
    connection: &mut Connection,
    credentials: &C,
    gateway: &G,
    workspace_id: &str,
    conversation_id: &str,
) -> Result<SyncReceipt, String> {
    sync_selected_conversation_internal(connection, credentials, gateway, workspace_id, conversation_id, false, 2)
}

pub fn continue_selected_conversation<C: CredentialStore, G: CloudGateway>(
    connection: &mut Connection, credentials: &C, gateway: &G,
    workspace_id: &str, conversation_id: &str,
) -> Result<SyncReceipt, String> {
    sync_selected_conversation_internal(connection, credentials, gateway, workspace_id, conversation_id, true, 2)
}

fn sync_selected_conversation_internal<C: CredentialStore, G: CloudGateway>(
    connection: &mut Connection, credentials: &C, gateway: &G,
    workspace_id: &str, conversation_id: &str, require_existing_selection: bool, stale_retries_left: u8,
) -> Result<SyncReceipt, String> {
    let session = read_session(credentials)?.ok_or_else(|| safe_error("请先登录 Google 账号"))?;
    let account = sync_state_v1::account_ref(&session.user_id)?;
    let unresolved_unknown: i64 = connection.query_row(
        "SELECT COUNT(*) FROM desktop_sync_jobs WHERE account_ref=?1 AND workspace_id=?2 AND conversation_id=?3 AND stage IN ('UNKNOWN','COMMITTING')",
        params![account,workspace_id,conversation_id],
        |row| row.get(0),
    ).map_err(|_| safe_error("未知提交任务无法核对，未访问云端"))?;
    if unresolved_unknown > 0 {
        return Ok(SyncReceipt {
            status: "UNKNOWN".into(),
            safe_code: Some("COMMIT_RESULT_UNKNOWN".into()),
            synced_at_ms: None,
        });
    }
    let document_id = selected_document_id(connection, &account, workspace_id, conversation_id)?;
    let ledger: Option<(i64,String)> = connection.query_row("SELECT remote_revision,payload_hash FROM desktop_selected_conversation_sync WHERE account_ref=?1 AND workspace_id=?2 AND conversation_id=?3", params![account,workspace_id,conversation_id], |row| Ok((row.get(0)?,row.get(1)?))).optional().map_err(|_| safe_error("同步回执无法读取"))?;
    if require_existing_selection && ledger.is_none() {
        return Err(safe_error("该对话已停止云端同步，未重新上传。"));
    }
    if ledger.is_some() {
        let deleted: Option<bool> = connection.query_row(
            "SELECT coalesce(json_extract(c.value,'$.deleted'),0) FROM workspace_exchange w,json_each(w.exchange_json,'$.conversations') c WHERE w.workspace_id=?1 AND json_extract(c.value,'$.id')=?2",
            params![workspace_id,conversation_id], |r| r.get(0)).optional().map_err(|_| safe_error("本机删除状态无法读取"))?;
        let deletion_pending: bool = connection.query_row("SELECT EXISTS(SELECT 1 FROM desktop_sync_jobs WHERE account_ref=?1 AND workspace_id=?2 AND conversation_id=?3 AND stage='DELETE_PENDING')",
            params![account,workspace_id,conversation_id], |r| r.get(0)).map_err(|_| safe_error("删除任务无法核对"))?;
        if deleted == Some(true) || (deleted.is_none() && deletion_pending) {
            cancel_remote_conversation(connection, credentials, gateway, workspace_id, conversation_id)?;
            return Ok(SyncReceipt { status: "SYNCED".into(), safe_code: Some("REMOTE_COPY_REMOVED".into()), synced_at_ms: Some(now_ms()) });
        }
    }
    // A temporary recovery ID or a stale/other-workspace target is not a
    // normal conversation. Validate the existing local owner before deriving
    // any cloud material; selected deletion has already been handled above.
    let local_target_exists: bool = connection.query_row(
        "SELECT EXISTS(SELECT 1 FROM workspace_exchange w,json_each(w.exchange_json,'$.conversations') c WHERE w.workspace_id=?1 AND json_extract(c.value,'$.id')=?2)",
        params![workspace_id,conversation_id], |row| row.get(0),
    ).map_err(|_| safe_error("本地对话身份无法核对，未访问云端"))?;
    if !local_target_exists {
        return Err(safe_error("对话不存在，未访问云端"));
    }
    let remote_result = gateway.read(&document_id);
    require_same_session(credentials, &session)?;
    let remote = match remote_result {
        Ok(remote) => remote,
        Err(failure) => {
            let code = cloud_failure_code(failure);
            connection.execute(
                "UPDATE desktop_cloud_account_state SET state='FAILED',last_error_code=?2,updated_at_ms=?3 WHERE account_ref=?1",
                params![account, code, now_ms()],
            ).ok();
            write_diagnostic(
                connection,
                Some(&account),
                "SYNC_REMOTE_CHECK",
                "FAILED",
                Some(&code),
            );
            write_notification(
                connection,
                Some(&account),
                "FAILED",
                cloud_read_failure_message(failure),
            );
            return Err(safe_error(cloud_read_failure_message(failure)));
        }
    };
    if remote.is_none() && ledger.is_some() {
        credentials.with_account_write(|| {
        let transaction = connection.transaction().map_err(|_| safe_error("同步身份清理事务未开启"))?;
        transaction.execute("DELETE FROM desktop_selected_conversation_sync WHERE account_ref=?1 AND workspace_id=?2 AND conversation_id=?3", params![account,workspace_id,conversation_id])
            .map_err(|_| safe_error("失效同步身份未清理"))?;
        transaction.execute("DELETE FROM desktop_sync_jobs WHERE account_ref=?1 AND workspace_id=?2 AND conversation_id=?3 AND stage NOT IN ('UNKNOWN','COMMITTING','CANCELLED')", params![account,workspace_id,conversation_id])
            .map_err(|_| safe_error("失效同步任务未清理"))?;
        transaction.commit().map_err(|_| safe_error("同步身份清理未保存"))?;
        Ok(())
        })?;
        return Err(safe_error("该对话已从云端删除，已停止续同步；本机内容保留。"));
    }
    credentials.with_account_write(|| recover_local_conversation_occurrences(connection, &account, workspace_id, conversation_id))?;
    // Before this explicit local write, fold any verified remote additions
    // into the local source.  Optimistic revision alone cannot tell which
    // device has a new turn: without this union a stale desktop could commit a
    // valid envelope that simply omits the phone's latest completed answer.
    // Verify both current direct and historical encrypted remote documents before
    // merging. Missing legacy material or an unknown format must never authorize
    // replacement of a remote snapshot by the local source.
    let remote_is_direct = remote
        .as_ref()
        .and_then(|value| serde_json::from_str::<Value>(&value.envelope).ok())
        .and_then(|value| {
            value
                .get("format")
                .and_then(Value::as_str)
                .map(str::to_owned)
        })
        .as_deref()
        == Some(sync_v1::DIRECT_ENVELOPE);
    if let Some(remote) = remote.as_ref() {
        let payload = open_account_document(connection, credentials, &account, remote, &document_id)?;
        let record = payload
            .get("records")
            .and_then(Value::as_array)
            .and_then(|records| {
                records.iter().find(|record| {
                    record.get("kind").and_then(Value::as_str) == Some("conversation")
                })
            })
            .ok_or_else(|| safe_error("云端对话内容无效"))?;
        if record.get("id").and_then(Value::as_str) != Some(conversation_id) {
            return Err(safe_error("云端对话标识不一致"));
        }
        let remote_semantic_revision = record.get("revision").and_then(Value::as_u64).unwrap_or(1);
        let mut remote_conversation = record
            .get("content")
            .and_then(Value::as_object)
            .cloned()
            .ok_or_else(|| safe_error("云端对话内容无效"))?;
        normalize_android_selected_conversation(&mut remote_conversation)?;
        credentials.with_account_write(|| {
        let transaction = connection
            .transaction()
            .map_err(|_| safe_error("本机对话合并 transaction 无法开启"))?;
        merge_remote_additions_for_local_commit(
            &transaction,
            workspace_id,
            conversation_id,
            &remote_conversation,
            remote_semantic_revision,
        )?;
        transaction
            .commit()
            .map_err(|_| safe_error("本机对话合并未提交"))?;
        Ok(())
        })?;
    }
    // Explicitly syncing this local conversation makes its complete current
    // text tree the newest source. A stale receipt is not a user conflict:
    // commit against the revision just read instead of stopping. Explicit
    // cloud reads take the opposite, equally concrete path and install the
    // verified cloud tree in merge_newer_remote_conversation.
    let expected = remote.as_ref().map(|value| value.revision).unwrap_or(0);
    let (payload, local_hash) = conversation_payload(
        connection,
        workspace_id,
        conversation_id,
        &document_id,
        expected + 1,
    )?;
    // A previous encrypted envelope can have the same local content hash.  It
    // must still be committed once so the selected document is migrated to the
    // direct Google-account format instead of being permanently reported as up
    // to date.
    if ledger.is_some()
        && remote_is_direct
        && ledger.as_ref().is_some_and(|known| remote.as_ref().is_some_and(|current| {
            known.0 as u64 == current.revision && known.1 == current.payload_hash
        }))
        && !remote.as_ref().is_some_and(|value| remote_requires_current_portable_rewrite(&value.envelope))
        && connection.query_row("SELECT local_content_hash FROM desktop_selected_conversation_sync WHERE account_ref=?1 AND workspace_id=?2 AND conversation_id=?3", params![account,workspace_id,conversation_id], |row| row.get::<_,String>(0)).ok().as_deref() == Some(&local_hash) {
        return Ok(SyncReceipt { status:"UP_TO_DATE".into(), safe_code:None, synced_at_ms:connection.query_row("SELECT last_synced_at_ms FROM desktop_selected_conversation_sync WHERE account_ref=?1 AND workspace_id=?2 AND conversation_id=?3", params![account,workspace_id,conversation_id], |row| row.get(0)).ok() });
    }
    let envelope = sync_v1::seal_direct(payload).map_err(|_| safe_error("对话无法准备同步内容"))?;
    let payload_hash = serde_json::from_str::<Value>(&envelope)
        .ok()
        .and_then(|value| {
            value
                .get("payloadHash")
                .and_then(Value::as_str)
                .map(str::to_owned)
        })
        .ok_or_else(|| safe_error("加密完整性校验失败"))?;
    let job_id = new_sync_job_id(&account)?;
    credentials.with_account_write(|| {
        let transaction = connection.transaction().map_err(|_| safe_error("同步任务事务未开启"))?;
        transaction.execute("UPDATE desktop_cloud_account_state SET state='SYNCING',last_error_code=NULL,updated_at_ms=?2 WHERE account_ref=?1", params![account,now_ms()]).map_err(|_| safe_error("同步状态未保存"))?;
        transaction.execute("INSERT INTO desktop_sync_jobs(job_id,account_ref,workspace_id,conversation_id,document_id,stage,payload_hash,expected_remote_revision,attempt,next_retry_at_ms,safe_error_code,created_at_ms,updated_at_ms) VALUES(?1,?2,?3,?4,?5,'COMMITTING',?6,?7,1,NULL,NULL,?8,?8)", params![job_id,account,workspace_id,conversation_id,document_id,payload_hash,expected as i64,now_ms()]).map_err(|_| safe_error("同步任务未保存"))?;
        transaction.commit().map_err(|_| safe_error("同步任务未提交"))
    })?;
    let committed_result = gateway.commit(&document_id, expected, &envelope);
    // If identity changed during the write, keep COMMITTING for explicit
    // reconciliation. Do not acknowledge or retry an external result.
    require_same_session(credentials, &session)?;
    let committed = match committed_result {
        Ok(value) => value,
        Err(CloudFailure::StaleRevision) if stale_retries_left > 0 => {
            connection.execute("UPDATE desktop_sync_jobs SET stage='SUPERSEDED',safe_error_code='REMOTE_STALE_REVISION',updated_at_ms=?2 WHERE job_id=?1", params![job_id,now_ms()])
                .map_err(|_| safe_error("并发同步任务未更新"))?;
            write_diagnostic(connection, Some(&account), "SYNC_COMMIT", "REMOTE_CHANGED_RECONCILING", Some("REMOTE_STALE_REVISION"));
            // A definite CAS rejection never committed this envelope. Re-read the
            // latest cloud tree and rebuild the local message union before retrying.
            return sync_selected_conversation_internal(connection, credentials, gateway,
                workspace_id, conversation_id, require_existing_selection, stale_retries_left - 1);
        }
        Err(CloudFailure::StaleRevision) => {
            let timestamp = now_ms();
            let transaction = connection.transaction().map_err(|_| safe_error("并发同步恢复事务未开启"))?;
            transaction.execute("UPDATE desktop_sync_jobs SET stage='RETRY_WAIT',next_retry_at_ms=NULL,safe_error_code='REMOTE_STALE_REVISION',updated_at_ms=?2 WHERE job_id=?1", params![job_id,timestamp])
                .map_err(|_| safe_error("并发同步任务未保存"))?;
            if ledger.is_some() {
                // The selected receipt authorizes later content sync. Keep the
                // current marker if another local write queued a newer change.
                let inserted = transaction.execute(
                    "INSERT INTO desktop_sync_jobs(job_id,account_ref,workspace_id,conversation_id,document_id,stage,payload_hash,attempt,next_retry_at_ms,safe_error_code,created_at_ms,updated_at_ms)
                     SELECT 'content-pending:'||account_ref||':'||workspace_id||':'||conversation_id,account_ref,workspace_id,conversation_id,document_id,'CONTENT_PENDING',?4,1,?5,'REMOTE_STALE_REVISION',?6,?6
                     FROM desktop_selected_conversation_sync WHERE account_ref=?1 AND workspace_id=?2 AND conversation_id=?3
                     ON CONFLICT(job_id) DO UPDATE SET stage='CONTENT_PENDING',attempt=MIN(desktop_sync_jobs.attempt+1,16),next_retry_at_ms=?6+MIN(1800000,30000*(1 << MIN(desktop_sync_jobs.attempt,6))),safe_error_code='REMOTE_STALE_REVISION',updated_at_ms=?6",
                    params![account,workspace_id,conversation_id,local_hash,timestamp+30_000,timestamp],
                ).map_err(|_| safe_error("并发内容续同步任务未保存"))?;
                if inserted != 1 { return Err(safe_error("已选同步身份无法确认")); }
            }
            transaction.execute("UPDATE desktop_cloud_account_state SET state='FAILED',last_error_code='REMOTE_STALE_REVISION',updated_at_ms=?2 WHERE account_ref=?1", params![account,timestamp])
                .map_err(|_| safe_error("并发同步状态未保存"))?;
            transaction.commit().map_err(|_| safe_error("并发同步恢复事务未提交"))?;
            write_diagnostic(connection, Some(&account), "SYNC_COMMIT", "RETRY_WAIT", Some("REMOTE_STALE_REVISION"));
            return Err(safe_error(if ledger.is_some() {
                "云端持续发生并发变更；本机消息已保留，稍后自动继续同步"
            } else {
                "云端持续发生并发变更；本机消息已保留，请稍后手动重试"
            }));
        }
        Err(CloudFailure::UnknownCommit) => {
            write_diagnostic(
                connection,
                Some(&account),
                "SYNC_COMMIT",
                "UNKNOWN",
                Some("COMMIT_RESULT_UNKNOWN"),
            );
            return mark_sync_commit_unknown(connection, &account, &job_id);
        }
        Err(failure) => {
            let code = cloud_failure_code(failure);
            connection.execute("UPDATE desktop_sync_jobs SET stage='RETRY_WAIT',next_retry_at_ms=?2,safe_error_code=?3,updated_at_ms=?4 WHERE job_id=?1",params![job_id,now_ms()+12*60*60*1000,code,now_ms()]).ok();
            connection.execute("UPDATE desktop_cloud_account_state SET state='FAILED',last_error_code=?2,updated_at_ms=?3 WHERE account_ref=?1",params![account,code,now_ms()]).ok();
            write_diagnostic(
                connection,
                Some(&account),
                "SYNC_COMMIT",
                "RETRY_WAIT",
                Some(&code),
            );
            return Err(safe_error(cloud_commit_failure_message(failure)));
        }
    };
    if committed.0 != expected + 1 || committed.1 != payload_hash {
        return Err(safe_error("云端回读与本机同步内容不一致"));
    }
    let readback_result = gateway.read(&document_id);
    require_same_session(credentials, &session)?;
    let readback = match readback_result {
        // A confirmed commit followed by a transient, missing, or stale read
        // must not surface as a false failure.  Reconciliation reads first and
        // never re-uploads this payload automatically.
        Ok(Some(value)) if value.revision >= committed.0 => value,
        Ok(Some(_)) | Ok(None) | Err(_) => {
            return mark_sync_commit_unknown(connection, &account, &job_id);
        }
    };
    if readback.revision != committed.0 || readback.payload_hash != payload_hash {
        connection.execute("UPDATE desktop_cloud_account_state SET state='CONFLICT',last_error_code='READBACK_MISMATCH',updated_at_ms=?2 WHERE account_ref=?1", params![account,now_ms()]).ok();
        write_diagnostic(
            connection,
            Some(&account),
            "SYNC_READBACK",
            "CONFLICT",
            Some("READBACK_MISMATCH"),
        );
        return Err(safe_error("云端回读与本机同步内容不一致"));
    }
    // Matching metadata is insufficient: the readback must still be a valid,
    // identity-bound document before acknowledging this external commit.
    if open_account_document(connection, credentials, &account, &readback, &document_id).is_err() {
        return mark_sync_commit_unknown(connection, &account, &job_id);
    }
    let timestamp = now_ms();
    credentials.with_account_write(|| {
    let transaction = connection
        .transaction()
        .map_err(|_| safe_error("同步回执 transaction 无法开启"))?;
    transaction.execute("INSERT INTO desktop_selected_conversation_sync(account_ref,workspace_id,conversation_id,document_id,remote_revision,payload_hash,local_content_hash,last_synced_at_ms) VALUES(?1,?2,?3,?4,?5,?6,?7,?8) ON CONFLICT(account_ref,workspace_id,conversation_id) DO UPDATE SET document_id=excluded.document_id,remote_revision=excluded.remote_revision,payload_hash=excluded.payload_hash,local_content_hash=excluded.local_content_hash,last_synced_at_ms=excluded.last_synced_at_ms", params![account,workspace_id,conversation_id,document_id,readback.revision as i64,payload_hash,local_hash,timestamp]).map_err(|_| safe_error("同步回执未保存"))?;
    transaction
        .execute(
            "UPDATE desktop_sync_jobs SET stage='COMPLETED',updated_at_ms=?2 WHERE job_id=?1",
            params![job_id, timestamp],
        )
        .map_err(|_| safe_error("同步任务未完成"))?;
    transaction.execute("UPDATE desktop_cloud_account_state SET state='READY',last_success_at_ms=?2,last_error_code=NULL,updated_at_ms=?2 WHERE account_ref=?1", params![account,timestamp]).map_err(|_| safe_error("同步状态未完成"))?;
    transaction
        .commit()
        .map_err(|_| safe_error("同步回执未提交"))?;
    Ok(())
    })?;
    write_diagnostic(connection, Some(&account), "SYNC_READBACK", "SUCCESS", None);
    write_notification(
        connection,
        Some(&account),
        "SUCCESS",
        "所选对话已完成同步。",
    );
    Ok(SyncReceipt {
        status: "SYNCED".into(),
        safe_code: None,
        synced_at_ms: Some(timestamp),
    })
}

/// A local title save and its retry marker share one SQLite transaction.
/// Only existing selected identities are eligible; this never enrolls a conversation.
pub(crate) fn queue_selected_title_change(transaction: &Transaction<'_>, workspace: &str, conversation: &str) -> Result<(), String> {
    let title: String = transaction.query_row(
        "SELECT json_object('title',json_extract(c.value,'$.title'),'titleRevision',json_extract(c.value,'$.titleRevision')) FROM workspace_exchange w,json_each(w.exchange_json,'$.conversations') c WHERE w.workspace_id=?1 AND json_extract(c.value,'$.id')=?2",
        params![workspace,conversation], |r| r.get(0)).map_err(|_| safe_error("标题变更未能读取"))?;
    queue_selected_change(transaction, workspace, conversation, &sha256(title.as_bytes()))
}

pub(crate) fn queue_selected_deletion(transaction: &Transaction<'_>, workspace: &str, conversation: &str) -> Result<(), String> {
    queue_selected_change(transaction, workspace, conversation, &sha256(format!("deleted:{}", now_ms()).as_bytes()))?;
    transaction.execute("UPDATE desktop_sync_jobs SET stage='DELETE_PENDING' WHERE workspace_id=?1 AND conversation_id=?2 AND job_id='title-pending:'||account_ref||':'||workspace_id||':'||conversation_id", params![workspace,conversation])
        .map_err(|_| safe_error("删除续同步任务未保存"))?;
    transaction.execute("DELETE FROM desktop_sync_jobs WHERE workspace_id=?1 AND conversation_id=?2 AND job_id='content-pending:'||account_ref||':'||workspace_id||':'||conversation_id", params![workspace,conversation])
        .map_err(|_| safe_error("删除续同步任务未保存"))?;
    Ok(())
}

pub(crate) fn queue_selected_content_change(transaction: &Transaction<'_>, workspace: &str, conversation: &str, marker: &str) -> Result<(), String> {
    transaction.execute(
        "INSERT INTO desktop_sync_jobs(job_id,account_ref,workspace_id,conversation_id,document_id,stage,payload_hash,attempt,next_retry_at_ms,created_at_ms,updated_at_ms)
         SELECT 'content-pending:'||account_ref||':'||workspace_id||':'||conversation_id,account_ref,workspace_id,conversation_id,document_id,'CONTENT_PENDING',?3,0,?4,?4,?4 FROM desktop_selected_conversation_sync WHERE workspace_id=?1 AND conversation_id=?2
         ON CONFLICT(job_id) DO UPDATE SET stage='CONTENT_PENDING',payload_hash=excluded.payload_hash,attempt=0,next_retry_at_ms=excluded.next_retry_at_ms,updated_at_ms=excluded.updated_at_ms,safe_error_code=NULL",
        params![workspace,conversation,marker,now_ms()],
    ).map_err(|_| safe_error("回答续同步任务未保存"))?;
    Ok(())
}

fn queue_selected_change(transaction: &Transaction<'_>, workspace: &str, conversation: &str, marker: &str) -> Result<(), String> {
    transaction.execute(
        "INSERT INTO desktop_sync_jobs(job_id,account_ref,workspace_id,conversation_id,document_id,stage,payload_hash,attempt,next_retry_at_ms,created_at_ms,updated_at_ms)
         SELECT 'title-pending:'||account_ref||':'||workspace_id||':'||conversation_id,account_ref,workspace_id,conversation_id,document_id,'TITLE_PENDING',?3,0,?4,?4,?4 FROM desktop_selected_conversation_sync WHERE workspace_id=?1 AND conversation_id=?2
         ON CONFLICT(job_id) DO UPDATE SET stage='TITLE_PENDING',payload_hash=excluded.payload_hash,attempt=0,next_retry_at_ms=excluded.next_retry_at_ms,updated_at_ms=excluded.updated_at_ms,safe_error_code=NULL",
        params![workspace,conversation,marker,now_ms()]).map_err(|_| safe_error("对话续同步任务未保存"))?;
    Ok(())
}

pub(crate) fn pending_title_targets<C: CredentialStore>(connection: &Connection, credentials: &C) -> Result<Vec<(String,String,String,String)>, String> {
    let Some(session) = read_session(credentials)? else { return Ok(Vec::new()); };
    let account = sync_state_v1::account_ref(&session.user_id)?;
    let mut statement = connection.prepare("SELECT j.job_id,j.workspace_id,j.conversation_id,j.payload_hash FROM desktop_sync_jobs j JOIN desktop_selected_conversation_sync s ON s.account_ref=j.account_ref AND s.workspace_id=j.workspace_id AND s.conversation_id=j.conversation_id WHERE j.account_ref=?1 AND j.stage IN ('TITLE_PENDING','DELETE_PENDING','CONTENT_PENDING') AND coalesce(j.next_retry_at_ms,0)<=?2 ORDER BY j.updated_at_ms LIMIT 32")
        .map_err(|_| safe_error("标题续同步任务无法读取"))?;
    let targets = statement.query_map(params![account,now_ms()], |r| Ok((r.get(0)?,r.get(1)?,r.get(2)?,r.get(3)?)))
        .map_err(|_| safe_error("标题续同步任务无法读取"))?.collect::<Result<Vec<_>,_>>().map_err(|_| safe_error("标题续同步任务无效"))?;
    Ok(targets)
}

pub(crate) fn finish_pending_title_attempt(connection: &Connection, job: &str, marker: &str, result: &Result<SyncReceipt,String>) -> Result<(),String> {
    if result.as_ref().is_ok_and(|r| matches!(r.status.as_str(),"SYNCED"|"UP_TO_DATE"|"SYNCED_AFTER_RECONCILE"|"SYNCED_AFTER_REMOTE_RECONCILE")) {
        connection.execute("DELETE FROM desktop_sync_jobs WHERE job_id=?1 AND payload_hash=?2",params![job,marker])
            .map_err(|_| safe_error("标题续同步回执未确认"))?;
    } else {
        let conflict = result.as_ref().err().is_some_and(|e| e.contains("标题版本冲突"));
        connection.execute("UPDATE desktop_sync_jobs SET stage=CASE WHEN stage='DELETE_PENDING' THEN stage WHEN ?3='TITLE_CONFLICT' THEN ?3 ELSE stage END,attempt=attempt+1,next_retry_at_ms=?4+MIN(1800000,30000*(1 << MIN(attempt,6))),updated_at_ms=?4 WHERE job_id=?1 AND payload_hash=?2",params![job,marker,if conflict { "TITLE_CONFLICT" } else { "TITLE_PENDING" },now_ms()])
            .map_err(|_| safe_error("标题续同步重试状态未保存"))?;
    }
    Ok(())
}

pub fn periodic_targets<C: CredentialStore>(
    connection: &Connection,
    credentials: &C,
) -> Result<Vec<(String, String)>, String> {
    let Some(session) = read_session(credentials)? else {
        return Ok(Vec::new());
    };
    let account = sync_state_v1::account_ref(&session.user_id)?;
    let enabled = connection.query_row(
        "SELECT periodic_enabled=1 AND state NOT IN ('SIGNED_OUT_KEEP_LOCAL','SIGNED_OUT') FROM desktop_cloud_account_state WHERE account_ref=?1",
        [&account],
        |row| row.get::<_,i64>(0),
    ).optional().map_err(|_| safe_error("定期同步状态无法读取"))?.unwrap_or(0) != 0;
    if !enabled {
        return Ok(Vec::new());
    }
    connection.prepare("SELECT workspace_id,conversation_id FROM desktop_selected_conversation_sync WHERE account_ref=?1 ORDER BY workspace_id,conversation_id")
        .map_err(|_| safe_error("定期同步目标无法读取"))?
        .query_map([&account], |row| Ok((row.get(0)?,row.get(1)?)))
        .map_err(|_| safe_error("定期同步目标无法读取"))?
        .collect::<Result<Vec<_>,_>>().map_err(|_| safe_error("定期同步目标无效"))
}

pub fn reconcile_unknown_commit<C: CredentialStore, G: CloudGateway>(
    connection: &mut Connection,
    credentials: &C,
    gateway: &G,
    workspace_id: &str,
    conversation_id: &str,
) -> Result<SyncReceipt, String> {
    let session = read_session(credentials)?.ok_or_else(|| safe_error("请先登录 Google 账号"))?;
    let account = sync_state_v1::account_ref(&session.user_id)?;
    let job: Option<(String, String, i64)> = connection
        .query_row(
            "SELECT document_id,payload_hash,expected_remote_revision FROM desktop_sync_jobs WHERE account_ref=?1 AND workspace_id=?2 AND conversation_id=?3 AND stage IN ('UNKNOWN','COMMITTING') ORDER BY updated_at_ms DESC LIMIT 1",
            params![account, workspace_id, conversation_id],
            |row| Ok((row.get(0)?, row.get(1)?, row.get(2)?)),
        )
        .optional()
        .map_err(|_| safe_error("未知提交状态无法读取"))?;
    let Some((document_id, payload_hash, expected_revision)) = job else {
        return Err(safe_error("没有需要核对的未知提交"));
    };
    let remote = gateway
        .read(&document_id)
        .map_err(|_| safe_error("仍无法核对云端提交结果"))?;
    require_same_session(credentials, &session)?;
    match remote {
        Some(remote)
            if remote.revision == expected_revision as u64 + 1
                && remote.payload_hash == payload_hash =>
        {
            open_account_document(connection, credentials, &account, &remote, &document_id)?;
            let envelope: Value = serde_json::from_str(&remote.envelope)
                .map_err(|_| safe_error("云端对话完整性校验未通过"))?;
            if envelope.get("payloadHash").and_then(Value::as_str) != Some(payload_hash.as_str())
                || envelope.get("revision").and_then(Value::as_u64) != Some(remote.revision)
            {
                return Err(safe_error("云端对话完整性校验未通过"));
            }
            let (_, local_hash) = conversation_payload(
                connection,
                workspace_id,
                conversation_id,
                &document_id,
                remote.revision,
            )?;
            let timestamp = now_ms();
            credentials.with_account_write(|| {
            let transaction = connection
                .transaction()
                .map_err(|_| safe_error("核对回执 transaction 无法开启"))?;
            transaction
                .execute(
                    "INSERT INTO desktop_selected_conversation_sync(account_ref,workspace_id,conversation_id,document_id,remote_revision,payload_hash,local_content_hash,last_synced_at_ms) VALUES(?1,?2,?3,?4,?5,?6,?7,?8) ON CONFLICT(account_ref,workspace_id,conversation_id) DO UPDATE SET document_id=excluded.document_id,remote_revision=excluded.remote_revision,payload_hash=excluded.payload_hash,local_content_hash=excluded.local_content_hash,last_synced_at_ms=excluded.last_synced_at_ms",
                    params![account, workspace_id, conversation_id, document_id, remote.revision as i64, payload_hash, local_hash, timestamp],
                )
                .map_err(|_| safe_error("核对回执未保存"))?;
            transaction
                .execute(
                    "UPDATE desktop_sync_jobs SET stage='COMPLETED',safe_error_code=NULL,updated_at_ms=?4 WHERE account_ref=?1 AND workspace_id=?2 AND conversation_id=?3 AND stage IN ('UNKNOWN','COMMITTING')",
                    params![account, workspace_id, conversation_id, timestamp],
                )
                .map_err(|_| safe_error("核对任务未完成"))?;
            transaction
                .execute(
                    "UPDATE desktop_cloud_account_state SET state='READY',last_success_at_ms=?2,last_error_code=NULL,updated_at_ms=?2 WHERE account_ref=?1",
                    params![account, timestamp],
                )
                .map_err(|_| safe_error("核对状态未完成"))?;
            transaction
                .commit()
                .map_err(|_| safe_error("核对回执未提交"))?;
            Ok(())
            })?;
            write_diagnostic(
                connection,
                Some(&account),
                "SYNC_RECONCILE",
                "SUCCESS",
                None,
            );
            write_notification(
                connection,
                Some(&account),
                "SUCCESS",
                "已核对上次云端提交，无需重复上传。",
            );
            Ok(SyncReceipt {
                status: "SYNCED_AFTER_RECONCILE".into(),
                safe_code: None,
                synced_at_ms: Some(timestamp),
            })
        }
        None if expected_revision == 0 => {
            write_diagnostic(
                connection,
                Some(&account),
                "SYNC_RECONCILE",
                "REMOTE_UNCHANGED",
                None,
            );
            Ok(SyncReceipt {
                status: "SAFE_TO_RETRY_EXPLICITLY".into(),
                safe_code: Some("REMOTE_UNCHANGED".into()),
                synced_at_ms: None,
            })
        }
        None => {
            // A previously existing remote document has disappeared. Retire the
            // selection, but keep the unresolved attempt for later verification.
            // Never convert UNKNOWN to a deletable stage or recreate the document.
            let transaction = connection.transaction()
                .map_err(|_| safe_error("同步身份清理事务未开启"))?;
            transaction.execute("DELETE FROM desktop_selected_conversation_sync WHERE account_ref=?1 AND workspace_id=?2 AND conversation_id=?3", params![account,workspace_id,conversation_id])
                .map_err(|_| safe_error("失效同步身份未清理"))?;
            transaction.execute("DELETE FROM desktop_sync_jobs WHERE account_ref=?1 AND workspace_id=?2 AND conversation_id=?3 AND stage NOT IN ('UNKNOWN','COMMITTING','CANCELLED')", params![account,workspace_id,conversation_id])
                .map_err(|_| safe_error("失效同步任务未清理"))?;
            transaction.commit().map_err(|_| safe_error("同步身份清理未保存"))?;
            Err(safe_error("该对话已从云端删除，已停止续同步；本机内容和未知提交记录保留。"))
        }
        other => {
            if let Some(remote) = other.as_ref() {
                open_account_document(connection, credentials, &account, remote, &document_id)?;
            }
            // A different verified remote revision is not a user-facing
            // content conflict.  It can be a phone update that landed while
            // this client was waiting for its uncertain submit result.  Mark
            // only the old attempt as superseded, then take the ordinary
            // read-union-commit path.  That path preserves both completed
            // trees and never re-sends the unknown envelope.
            connection.execute("UPDATE desktop_sync_jobs SET stage='SUPERSEDED',safe_error_code='REMOTE_CHANGED',updated_at_ms=?4 WHERE account_ref=?1 AND workspace_id=?2 AND conversation_id=?3 AND stage IN ('UNKNOWN','COMMITTING')", params![account,workspace_id,conversation_id,now_ms()]).map_err(|_| safe_error("核对任务未更新"))?;
            write_diagnostic(
                connection,
                Some(&account),
                "SYNC_RECONCILE",
                "REMOTE_CHANGED_RECONCILING",
                Some("REMOTE_CHANGED"),
            );
            let receipt = sync_selected_conversation(
                connection,
                credentials,
                gateway,
                workspace_id,
                conversation_id,
            )?;
            if !matches!(receipt.status.as_str(), "SYNCED" | "UP_TO_DATE") {
                return Ok(receipt);
            }
            write_notification(
                connection,
                Some(&account),
                "SUCCESS",
                "已合并云端最新对话并完成同步。",
            );
            Ok(SyncReceipt {
                status: "SYNCED_AFTER_REMOTE_RECONCILE".into(),
                safe_code: None,
                synced_at_ms: receipt.synced_at_ms,
            })
        }
    }
}

pub fn set_periodic_enabled<C: CredentialStore>(
    connection: &Connection,
    credentials: &C,
    enabled: bool,
) -> Result<(), String> {
    let session = read_session(credentials)?.ok_or_else(|| safe_error("请先登录 Google 账号"))?;
    let account = sync_state_v1::account_ref(&session.user_id)?;
    connection.execute("UPDATE desktop_cloud_account_state SET periodic_enabled=?2,revision=revision+1,updated_at_ms=?3 WHERE account_ref=?1", params![account,enabled as i64,now_ms()]).map_err(|_| safe_error("定期同步设置未保存"))?;
    Ok(())
}

pub fn choose_selected_local_start<C: CredentialStore>(
    connection: &mut Connection,
    credentials: &C,
) -> Result<(), String> {
    let session = read_session(credentials)?.ok_or_else(|| safe_error("请先登录 Google 账号"))?;
    let account = sync_state_v1::account_ref(&session.user_id)?;
    let mut store = SqliteMetadataStore { connection };
    let metadata = store
        .metadata(&account)?
        .ok_or_else(|| safe_error("账号同步状态不存在"))?;
    if metadata.state == sync_state_v1::State::DirectionRequired {
        sync_state_v1::choose_direction(
            &mut store,
            &format!("desktop-direction-{}", now_ms()),
            &account,
            metadata.revision,
            "LOCAL_SELECTED_REMOTE_GUARDED",
        )?;
    }
    store.connection.execute(
        "UPDATE desktop_cloud_account_state SET state='READY',last_error_code=NULL,revision=revision+1,updated_at_ms=?2 WHERE account_ref=?1",
        params![account, now_ms()],
    ).map_err(|_| safe_error("同步方向未保存"))?;
    write_diagnostic(
        store.connection,
        Some(&account),
        "DIRECTION_SELECT",
        "LOCAL_SELECTED_REMOTE_GUARDED",
        None,
    );
    Ok(())
}

pub fn sign_out_keep_local<C: CredentialStore>(
    connection: &mut Connection,
    credentials: &C,
) -> Result<(), String> {
    sign_out_keep_local_with_checkpoint(connection, credentials, |_| Ok(()))
}

pub(crate) fn sign_out_keep_local_with_checkpoint<C: CredentialStore>(
    connection: &mut Connection,
    credentials: &C,
    checkpoint: impl Fn(&str) -> Result<(), String>,
) -> Result<(), String> {
    let Some(session) = read_session(credentials)? else {
        return Ok(());
    };
    let account = sync_state_v1::account_ref(&session.user_id)?;
    let mut store = SqliteMetadataStore { connection };
    let removed = std::cell::Cell::new(false);
    let result = sync_state_v1::sign_out_keep_local_with_account_update(
        &mut store,
        &format!("desktop-signout-{}-{}", now_ms(), random_urlsafe(18)?),
        &account,
        |transaction| {
            transaction.execute("UPDATE desktop_cloud_account_state SET state='SIGNED_OUT_KEEP_LOCAL',periodic_enabled=0,revision=revision+1,updated_at_ms=?2 WHERE account_ref=?1", params![account,now_ms()]).map_err(|_| safe_error("退出状态未保存"))?;
            Ok(())
        },
        || {
            checkpoint("before-session")?;
            credentials.delete(SESSION_SERVICE, "active")
                .map_err(|_| safe_error("本机账号会话未清除"))?;
            removed.set(true);
            checkpoint("after-session")?;
            Ok(())
        },
    );
    if let Err(error) = result {
        if removed.get() {
            save_session(credentials, &session)
                .map_err(|_| safe_error("退出提交失败，原会话恢复未完成，请保留本机数据并重新登录"))?;
        }
        return Err(error);
    }
    checkpoint("after-commit")?;
    write_diagnostic(
        store.connection,
        Some(&account),
        "SIGN_OUT",
        "SUCCESS_KEEP_LOCAL",
        None,
    );
    Ok(())
}

fn allowed_avatar_url(value: &str) -> bool {
    Url::parse(value).ok().is_some_and(|url| {
        url.scheme() == "https"
            && url.username().is_empty()
            && url.password().is_none()
            && url.port().is_none()
            && url.host_str().is_some_and(|host| {
                host == "googleusercontent.com" || host.ends_with(".googleusercontent.com")
            })
    })
}
pub fn fetch_avatar(config: &ServiceConfig, session: &Session) -> Option<String> {
    let avatar = session
        .avatar_url
        .as_deref()
        .filter(|value| allowed_avatar_url(value))?;
    let client = Client::builder()
        .redirect(Policy::none())
        .timeout(Duration::from_secs(8))
        .build()
        .ok()?;
    let proxy = config
        .supabase_url
        .join("functions/v1/google-avatar")
        .ok()?;
    let response = client
        .post(proxy)
        .header("apikey", &config.publishable_key)
        .bearer_auth(&session.access_token)
        .json(&json!({"url":avatar}))
        .send()
        .ok()
        .filter(|value| {
            value.status().is_success()
                && value
                    .headers()
                    .get("content-type")
                    .and_then(|value| value.to_str().ok())
                    .is_some_and(|value| value.starts_with("image/"))
        })
        .or_else(|| {
            client.get(avatar).send().ok().filter(|value| {
                value.status().is_success()
                    && value
                        .headers()
                        .get("content-type")
                        .and_then(|value| value.to_str().ok())
                        .is_some_and(|value| value.starts_with("image/"))
            })
        })?;
    let mime = response
        .headers()
        .get("content-type")?
        .to_str()
        .ok()?
        .split(';')
        .next()?
        .to_owned();
    let bytes = response.bytes().ok()?;
    if bytes.is_empty() || bytes.len() > AVATAR_MAX_BYTES {
        return None;
    }
    Some(format!(
        "data:{mime};base64,{}",
        base64::engine::general_purpose::STANDARD.encode(bytes)
    ))
}

pub fn projection<C: CredentialStore>(
    connection: &Connection,
    credentials: &C,
    configured: bool,
    avatar_data_url: Option<String>,
) -> Result<AccountProjection, String> {
    let session = read_session(credentials)?;
    let account = session.as_ref()
        .map(|value| sync_state_v1::account_ref(&value.user_id)).transpose()?;
    let row = account.as_ref().map(|account| connection.query_row("SELECT state,periodic_enabled,recovery_confirmed,last_success_at_ms,last_error_code,device_id,pending_recovery_created_at_ms FROM desktop_cloud_account_state WHERE account_ref=?1", [account], |row| Ok((row.get::<_,String>(0)?,row.get::<_,i64>(1)? != 0,row.get::<_,i64>(2)? != 0,row.get::<_,Option<i64>>(3)?,row.get::<_,Option<String>>(4)?,row.get::<_,String>(5)?,row.get::<_,Option<i64>>(6)?))).optional().map_err(|_| safe_error("账号状态无法读取"))).transpose()?.flatten();
    let selected = account
        .as_ref()
        .and_then(|value| {
            connection
                .query_row(
                    "SELECT COUNT(*) FROM desktop_selected_conversation_sync WHERE account_ref=?1",
                    [value],
                    |row| row.get::<_, u64>(0),
                )
                .ok()
        })
        .unwrap_or(0);
    // The sidebar needs the durable receipt identity, not merely the count, to
    // mark local rows that are available on another device.  This stays scoped
    // to the authenticated account and exposes no title or conversation body.
    let synced_conversation_keys = if let Some(value) = account.as_ref() {
        connection
            .prepare("SELECT workspace_id,conversation_id FROM desktop_selected_conversation_sync WHERE account_ref=?1 ORDER BY workspace_id,conversation_id")
            .map_err(|_| safe_error("已选同步文档无法读取"))?
            .query_map([value], |row| {
                Ok(format!("{}:{}", row.get::<_, String>(0)?, row.get::<_, String>(1)?))
            })
            .map_err(|_| safe_error("已选同步文档无法读取"))?
            .collect::<Result<Vec<_>, _>>()
            .map_err(|_| safe_error("已选同步文档无效"))?
    } else {
        Vec::new()
    };
    let diagnostics = connection.prepare("SELECT event,outcome,safe_code,occurred_at_ms FROM desktop_sync_diagnostics ORDER BY occurred_at_ms DESC LIMIT 12").map_err(|_| safe_error("同步诊断无法读取"))?.query_map([], |row| Ok(DiagnosticProjection { event:row.get(0)?, outcome:row.get(1)?, safe_code:row.get(2)?, occurred_at_ms:row.get(3)? })).map_err(|_| safe_error("同步诊断无法读取"))?.collect::<Result<Vec<_>,_>>().map_err(|_| safe_error("同步诊断无效"))?;
    let notifications = connection.prepare("SELECT kind,message,occurred_at_ms,unread FROM desktop_sync_notifications ORDER BY occurred_at_ms DESC LIMIT 8").map_err(|_| safe_error("同步通知无法读取"))?.query_map([], |row| Ok(NotificationProjection { kind:row.get(0)?, message:row.get(1)?, occurred_at_ms:row.get(2)?, unread:row.get::<_,i64>(3)? != 0 })).map_err(|_| safe_error("同步通知无法读取"))?.collect::<Result<Vec<_>,_>>().map_err(|_| safe_error("同步通知无效"))?;
    let state = row
        .as_ref()
        .map(|value| match value.0.as_str() {
            "AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION"
            | "AUTHENTICATED_NEEDS_RECOVERY_MATERIAL" => "READY".to_owned(),
            _ => value.0.clone(),
        })
        .unwrap_or_else(|| {
            if configured {
                "SIGNED_OUT".into()
            } else {
                "NOT_CONFIGURED".into()
            }
        });
    Ok(AccountProjection {
        configured,
        state: state.clone(),
        email: session.as_ref().map(|value| value.email.clone()),
        display_name: session
            .as_ref()
            .and_then(|value| value.display_name.clone()),
        avatar_data_url,
        recovery_state: "NOT_REQUIRED".into(),
        recovery_confirmation_pending: false,
        periodic_enabled: row.as_ref().is_some_and(|value| value.1),
        rotation_pending: false,
        last_success_at_ms: row.as_ref().and_then(|value| value.3),
        selected_conversation_count: selected,
        synced_conversation_keys,
        sync_stage: state,
        notice: row
            .as_ref()
            .and_then(|value| value.4.clone())
            .filter(|code| code != "RECOVERY_MATERIAL_REQUIRED"),
        device_id: row
            .map(|value| value.5)
            .unwrap_or_else(|| "尚未建立设备身份".into()),
        diagnostics,
        notifications,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    include!("answer_model_facts_tests.rs");
    use std::{cell::RefCell, collections::BTreeMap};
    fn expected_account_fixture(expires: u64) -> Mem {
        let keys = Mem(RefCell::new(BTreeMap::new()));
        save_session(&keys, &Session { user_id: "expected-user".into(), email: "account-a@example.invalid".into(),
            display_name: None, avatar_url: None, access_token: "synthetic-access".into(),
            refresh_token: "synthetic-refresh".into(), expires_at_epoch_seconds: expires }).unwrap();
        keys
    }
    #[test]
    fn expected_account_rejects_mismatch_before_expired_session_network_refresh() {
        use std::io::Write;
        use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};
        let keys = expected_account_fixture(0);
        let before = keys.0.borrow().clone();
        let listener = std::net::TcpListener::bind("127.0.0.1:0").unwrap();
        let address = listener.local_addr().unwrap(); listener.set_nonblocking(true).unwrap();
        let count = Arc::new(AtomicUsize::new(0)); let stop = Arc::new(AtomicBool::new(false));
        let observed = count.clone(); let stopped = stop.clone();
        let worker = std::thread::spawn(move || {
            while !stopped.load(Ordering::SeqCst) {
                match listener.accept() {
                    Ok((mut stream, _)) => { observed.fetch_add(1, Ordering::SeqCst); let _ = stream.write_all(b"HTTP/1.1 400 Bad Request\r\nContent-Length: 2\r\nConnection: close\r\n\r\n{}"); },
                    Err(error) if error.kind() == std::io::ErrorKind::WouldBlock => std::thread::sleep(std::time::Duration::from_millis(1)),
                    Err(_) => break,
                }
            }
        });
        let config = ServiceConfig { supabase_url: Url::parse(&format!("http://{address}/")).unwrap(), publishable_key: "synthetic-publishable".into(), localhost_mock: true };
        let result = active_session_for_expected_account(&keys, &config, &Mutex::new(0), Some("account-b@example.invalid"));
        stop.store(true, Ordering::SeqCst); worker.join().unwrap();
        assert_eq!(count.load(Ordering::SeqCst), 0, "changed account must be rejected before refreshing tokens");
        assert!(result.unwrap_err().contains("账号已变化")); assert_eq!(*keys.0.borrow(), before);
    }
    #[test]
    fn expected_account_matching_session_keeps_existing_cloud_admission() {
        let keys = expected_account_fixture(u64::MAX);
        let config = ServiceConfig { supabase_url: Url::parse("http://127.0.0.1:9/").unwrap(), publishable_key: "synthetic-publishable".into(), localhost_mock: true };
        let session = active_session_for_expected_account(&keys, &config, &Mutex::new(0), Some("account-a@example.invalid")).unwrap();
        assert_eq!(session.email, "account-a@example.invalid"); assert_eq!(session.access_token, "synthetic-access");
    }
    #[test]
    fn expected_account_optional_guard_preserves_explicit_current_account_actions() {
        let keys = expected_account_fixture(u64::MAX);
        let config = ServiceConfig { supabase_url: Url::parse("http://127.0.0.1:9/").unwrap(), publishable_key: "synthetic-publishable".into(), localhost_mock: true };
        assert_eq!(active_session_for_expected_account(&keys, &config, &Mutex::new(0), None).unwrap().email, "account-a@example.invalid");
    }


    fn locator_rows(connection: &Connection, indexed: bool, conversation: &str, account: &str) -> Result<Vec<String>,String> {
        let sql = if indexed { local_occurrence_candidates_sql(connection)? } else { LOCAL_OCCURRENCE_CANDIDATES_SQL };
        let mut statement = connection.prepare(sql).map_err(|e|e.to_string())?;
        let rows = statement.query_map(params!["target",conversation,account], |r|r.get(0)).map_err(|e|e.to_string())?;
        rows.collect::<Result<Vec<_>,_>>().map_err(|e|e.to_string())
    }

    #[test]
    fn inventory_locator_unrelated_query_is_bounded_at_full_inventory_size() {
        for count in [256usize,2048,10_000] {
            let mut connection=database();
            let tx=connection.transaction().unwrap();
            for i in 0..count {
                let workspace=format!("locator-{i:05}");let conversation=format!("conversation-{i:05}");
                tx.execute("INSERT INTO workspace_exchange VALUES(?1,?2)",params![workspace,json!({"conversations":[{"id":conversation}]}).to_string()]).unwrap();
                tx.execute("INSERT INTO desktop_selected_conversation_sync VALUES('current-account',?1,?2,?3,1,'fake','fake',1)",params![workspace,conversation,format!("doc-{i}")]).unwrap();
            }
            tx.commit().unwrap();
            let main_schema:Vec<String>=connection.prepare("SELECT sql FROM main.sqlite_master WHERE sql IS NOT NULL ORDER BY name").unwrap().query_map([],|r|r.get(0)).unwrap().collect::<Result<_,_>>().unwrap();
            with_local_occurrence_locator(&mut connection,|connection|{
                let tx=connection.transaction().unwrap();
                let sql=local_occurrence_candidates_sql(&tx)?;
                let mut query=tx.prepare(sql).unwrap();
                assert!(query.query_map(params!["target","absent","current-account"],|r|r.get::<_,String>(0)).unwrap().collect::<Result<Vec<_>,_>>().unwrap().is_empty());
                let steps=query.get_status(rusqlite::StatementStatus::VmStep);
                println!("indexed_locator workspaces={count} receipts={count} query_vm_steps={steps}");
                assert!(steps<=1000,"an unrelated lookup must not scan all workspace JSON or receipts: {steps}");
                drop(query);tx.commit().unwrap();Ok(())
            }).unwrap();
            assert_eq!(connection.query_row("SELECT count(*) FROM temp.sqlite_master WHERE name GLOB 'nfai_restore_occurrence_*'",[],|r|r.get::<_,i64>(0)).unwrap(),0);
            let after:Vec<String>=connection.prepare("SELECT sql FROM main.sqlite_master WHERE sql IS NOT NULL ORDER BY name").unwrap().query_map([],|r|r.get(0)).unwrap().collect::<Result<_,_>>().unwrap();assert_eq!(main_schema,after);
        }
    }

    #[test]
    fn inventory_locator_workspace_maintenance_is_bounded_as_inventory_grows() {
        for count in [256usize,2048] {
            let mut connection=database();
            for i in 0..count { connection.execute("INSERT INTO workspace_exchange VALUES(?1,?2)",params![format!("maintenance-{i}"),json!({"conversations":[{"id":format!("maintenance-conversation-{i}")}]}).to_string()]).unwrap(); }
            let schema:Vec<String>=connection.prepare("SELECT sql FROM main.sqlite_master WHERE sql IS NOT NULL ORDER BY name").unwrap().query_map([],|r|r.get(0)).unwrap().collect::<Result<_,_>>().unwrap();
            with_local_occurrence_locator(&mut connection,|connection| {
                assert!(locator_rows(connection,true,"new-item","current-account")?.is_empty());
                let tx=connection.transaction().unwrap();
                let mut insert=tx.prepare("INSERT INTO workspace_exchange VALUES('changing-workspace',?1)").unwrap();
                insert.execute([json!({"conversations":[{"id":"new-item"},{"id":"shared-item"}]}).to_string()]).unwrap();
                let insert_steps=insert.get_status(rusqlite::StatementStatus::VmStep);drop(insert);
                assert_eq!(locator_rows(&tx,true,"new-item","current-account")?,locator_rows(&tx,false,"new-item","current-account")?);
                assert_eq!(locator_rows(&tx,true,"new-item","current-account")?.len(),1);
                let mut update=tx.prepare("UPDATE workspace_exchange SET exchange_json=?1 WHERE workspace_id='changing-workspace'").unwrap();
                update.execute([json!({"conversations":[{"id":"renamed-item"}]}).to_string()]).unwrap();let update_steps=update.get_status(rusqlite::StatementStatus::VmStep);drop(update);
                assert!(locator_rows(&tx,true,"new-item","current-account")?.is_empty());
                assert_eq!(locator_rows(&tx,true,"renamed-item","current-account")?,locator_rows(&tx,false,"renamed-item","current-account")?);
                let mut delete=tx.prepare("DELETE FROM workspace_exchange WHERE workspace_id='changing-workspace'").unwrap();delete.execute([]).unwrap();let delete_steps=delete.get_status(rusqlite::StatementStatus::VmStep);drop(delete);
                assert!(locator_rows(&tx,true,"renamed-item","current-account")?.is_empty());
                assert_eq!(tx.query_row("SELECT count(*) FROM temp.nfai_restore_occurrence_ids",[],|r|r.get::<_,i64>(0)).unwrap(),count as i64);
                tx.rollback().unwrap();
                for (kind,steps) in [("insert",insert_steps),("update",update_steps),("delete",delete_steps)] {
                    println!("locator maintenance {kind} inventory={count} vm={steps}");
                    assert!(steps<=512,"single-workspace maintenance must not scan every occurrence: {kind} count={count} vm={steps}");
                }
                Ok(())
            }).unwrap();
            assert_eq!(connection.query_row("SELECT count(*) FROM temp.sqlite_master WHERE name GLOB 'nfai_restore_occurrence_*'",[],|r|r.get::<_,i64>(0)).unwrap(),0);
            let after:Vec<String>=connection.prepare("SELECT sql FROM main.sqlite_master WHERE sql IS NOT NULL ORDER BY name").unwrap().query_map([],|r|r.get(0)).unwrap().collect::<Result<_,_>>().unwrap();assert_eq!(schema,after);
        }
    }

    #[test]
    fn inventory_locator_preserves_candidate_semantics_and_tracks_writes_and_rollback() {
        let mut connection=database();
        for i in 0..80 {
            connection.execute("INSERT INTO workspace_exchange VALUES(?1,?2)",params![format!("w-{i:03}"),json!({"conversations":[{"id":"wanted","deleted":i%7==0,"tag":i},{"id":"other"},{"id":null}]}).to_string()]).unwrap();
            if i%5==0 {connection.execute("INSERT INTO desktop_selected_conversation_sync VALUES('other-account',?1,'wanted',?2,1,'fake','fake',1)",params![format!("w-{i:03}"),format!("doc-{i}")]).unwrap();}
        }
        with_local_occurrence_locator(&mut connection,|connection|{
            for account in ["current-account","other-account","missing-account"] {
                for id in ["wanted","other","absent"] {assert_eq!(locator_rows(connection,true,id,account),locator_rows(connection,false,id,account));}
            }
            assert_eq!(locator_rows(connection,true,"wanted","current-account")?.len(),32);
            connection.execute("INSERT INTO workspace_exchange VALUES('000-protected','not-json')",[]).unwrap();
            connection.execute("INSERT INTO desktop_selected_conversation_sync VALUES('other-account','000-protected','wanted','protected',1,'fake','fake',1)",[]).unwrap();
            assert_eq!(locator_rows(connection,true,"wanted","current-account"),locator_rows(connection,false,"wanted","current-account"));
            assert!(locator_rows(connection,true,"other","current-account").is_err());
            connection.execute("DELETE FROM workspace_exchange WHERE workspace_id='000-protected'",[]).unwrap();
            for body in ["not-json",r#"{"conversations":["bad-json-scalar"]}"#,r#"{"conversations":[null,1,true,{"id":"other"}]}"#] {
                let tx=connection.transaction().unwrap();tx.execute("INSERT OR REPLACE INTO workspace_exchange VALUES('000-edge',?1)",[body]).unwrap();
                assert_eq!(locator_rows(&tx,true,"other","current-account"),locator_rows(&tx,false,"other","current-account"));tx.rollback().unwrap();
                assert_eq!(locator_rows(connection,true,"other","current-account"),locator_rows(connection,false,"other","current-account"));
            }
            let tx=connection.transaction().unwrap();tx.execute("UPDATE workspace_exchange SET workspace_id='000-renamed',exchange_json=?1 WHERE workspace_id='w-079'",[json!({"conversations":[{"id":"fresh","tag":"committed"},{"id":"fresh","tag":"duplicate"}]}).to_string()]).unwrap();
            assert_eq!(locator_rows(&tx,true,"fresh","current-account"),locator_rows(&tx,false,"fresh","current-account"));tx.commit().unwrap();
            connection.execute("INSERT OR REPLACE INTO desktop_selected_conversation_sync VALUES('other-account','000-renamed','fresh','fresh',1,'fake','fake',1)",[]).unwrap();assert!(locator_rows(connection,true,"fresh","current-account")?.is_empty());
            connection.execute("UPDATE desktop_selected_conversation_sync SET conversation_id='changed',account_ref='new-account' WHERE workspace_id='000-renamed'",[]).unwrap();assert_eq!(locator_rows(connection,true,"fresh","current-account")?.len(),2);
            Err::<(),String>("synthetic early refusal".into())
        }).unwrap_err();
        assert_eq!(connection.query_row("SELECT count(*) FROM temp.sqlite_master WHERE name GLOB 'nfai_restore_occurrence_*'",[],|r|r.get::<_,i64>(0)).unwrap(),0);
    }

    #[test]
    fn inventory_locator_refreshes_external_commits_without_receiving_temp_triggers() {
        static NEXT:std::sync::atomic::AtomicU64=std::sync::atomic::AtomicU64::new(0);
        let uri=format!("file:nfai-locator-{}?mode=memory&cache=shared",NEXT.fetch_add(1,std::sync::atomic::Ordering::Relaxed));
        let flags=rusqlite::OpenFlags::SQLITE_OPEN_READ_WRITE|rusqlite::OpenFlags::SQLITE_OPEN_CREATE|rusqlite::OpenFlags::SQLITE_OPEN_URI;
        let mut connection=Connection::open_with_flags(&uri,flags).unwrap();let external=Connection::open_with_flags(&uri,flags).unwrap();
        let template=database();let schema:Vec<String>=template.prepare("SELECT sql FROM sqlite_master WHERE name IN ('workspace_exchange','desktop_selected_conversation_sync')").unwrap().query_map([],|r|r.get(0)).unwrap().collect::<Result<_,_>>().unwrap();for sql in schema{connection.execute_batch(&sql).unwrap();}
        with_local_occurrence_locator(&mut connection,|connection|{
            assert!(locator_rows(connection,true,"wanted","current-account")?.is_empty());
            assert_eq!(external.query_row("SELECT count(*) FROM temp.sqlite_master",[],|r|r.get::<_,i64>(0)).unwrap(),0);
            external.execute("INSERT INTO workspace_exchange VALUES('external',?1)",[json!({"conversations":[{"id":"wanted","tag":"external"}]}).to_string()]).unwrap();
            let tx=connection.transaction().unwrap();assert_eq!(locator_rows(&tx,true,"wanted","current-account")?.len(),1);tx.commit().unwrap();
            external.execute("INSERT INTO desktop_selected_conversation_sync VALUES('other-account','external','wanted','external-doc',1,'fake','fake',1)",[]).unwrap();external.execute("UPDATE workspace_exchange SET exchange_json='not-json' WHERE workspace_id='external'",[]).unwrap();
            let tx=connection.transaction().unwrap();assert_eq!(locator_rows(&tx,true,"wanted","current-account"),locator_rows(&tx,false,"wanted","current-account"));tx.commit().unwrap();
            external.execute("DELETE FROM desktop_selected_conversation_sync",[]).unwrap();let tx=connection.transaction().unwrap();assert!(locator_rows(&tx,true,"wanted","current-account").is_err());tx.rollback().unwrap();
            external.execute("DELETE FROM workspace_exchange",[]).unwrap();assert!(locator_rows(connection,true,"wanted","current-account")?.is_empty());Ok(())
        }).unwrap();
    }

    #[test]
    fn unrelated_local_workspaces_do_not_scan_every_selected_receipt_per_workspace() {
        for count in [256usize, 2048] {
            let mut connection = database();
            let transaction = connection.transaction().unwrap();
            for i in 0..count {
                let workspace_id = format!("scan-workspace-{i:05}");
                let conversation_id = format!("scan-conversation-{i:05}");
                let exchange = json!({"conversations":[{"id":conversation_id,"deleted":false}]}).to_string();
                transaction.execute("INSERT INTO workspace_exchange VALUES(?1,?2)", params![workspace_id,exchange]).unwrap();
                transaction.execute("INSERT INTO desktop_selected_conversation_sync VALUES('other-account',?1,?2,?3,1,'fake','fake',1)", params![workspace_id,conversation_id,format!("scan-document-{i:05}")]).unwrap();
            }
            transaction.commit().unwrap();
            let mut statement = connection.prepare(LOCAL_OCCURRENCE_CANDIDATES_SQL).unwrap();
            let rows = statement.query_map(params!["absent-workspace","absent-conversation","current-account"], |row| row.get::<_,String>(0)).unwrap().collect::<Result<Vec<_>,_>>().unwrap();
            assert!(rows.is_empty());
            let steps = statement.get_status(rusqlite::StatementStatus::VmStep);
            println!("local_occurrence_query workspaces={count} receipts={count} vm_steps={steps}");
            assert!(steps <= (count * 80 + 1000) as i32, "unrelated conversations must not amplify receipt scans: {steps} steps for {count}");
        }
    }

    #[test]
    fn local_occurrence_candidate_filter_preserves_account_tombstone_order_and_limit_semantics() {
        let mut connection = database();
        let transaction = connection.transaction().unwrap();
        for i in 0..100 {
            let workspace_id = format!("source-{i:03}");
            let conversation = match i % 7 {
                2 => json!({"id":"wanted","deleted":true,"tag":i}),
                4 => json!({"id":null,"tag":i}),
                5 => json!({"id":"wanted","deleted":null,"tag":i}),
                6 => json!({"id":"unrelated","tag":i}),
                _ => json!({"id":"wanted","deleted":false,"tag":i}),
            };
            transaction.execute("INSERT INTO workspace_exchange VALUES(?1,?2)", params![workspace_id,json!({"conversations":[conversation]}).to_string()]).unwrap();
            if i % 7 <= 1 {
                let account = if i % 7 == 0 { "current-account" } else { "other-account" };
                transaction.execute("INSERT INTO desktop_selected_conversation_sync VALUES(?1,?2,'wanted',?3,1,'fake','fake',1)", params![account,workspace_id,format!("source-document-{i}")]).unwrap();
            }
        }
        transaction.commit().unwrap();
        const ORIGINAL: &str = "SELECT c.value FROM workspace_exchange w, json_each(w.exchange_json,'$.conversations') c WHERE w.workspace_id<>?1 AND json_extract(c.value,'$.id')=?2 AND coalesce(json_extract(c.value,'$.deleted'),0)=0 AND NOT EXISTS(SELECT 1 FROM desktop_selected_conversation_sync s WHERE s.workspace_id=w.workspace_id AND s.conversation_id=?2 AND s.account_ref<>?3) ORDER BY w.workspace_id LIMIT 32";
        let read = |sql: &str, conversation: &str, account: &str| -> rusqlite::Result<Vec<String>> {
            connection.prepare(sql)?.query_map(params!["source-000",conversation,account], |row| row.get(0))?.collect()
        };
        for account in ["current-account", "other-account", "missing-account"] {
            for conversation in ["wanted", "unrelated", "absent"] {
                assert_eq!(read(LOCAL_OCCURRENCE_CANDIDATES_SQL,conversation,account).unwrap(), read(ORIGINAL,conversation,account).unwrap());
            }
        }
        assert_eq!(read(LOCAL_OCCURRENCE_CANDIDATES_SQL,"wanted","current-account").unwrap().len(),32);
        // Even a malformed workspace owned by another account must remain
        // excluded before JSON traversal, exactly as the original query did.
        connection.execute("INSERT INTO workspace_exchange VALUES('000-protected-malformed','not-json')", []).unwrap();
        connection.execute("INSERT INTO desktop_selected_conversation_sync VALUES('other-account','000-protected-malformed','wanted','protected-malformed-document',1,'fake','fake',1)", []).unwrap();
        assert_eq!(read(LOCAL_OCCURRENCE_CANDIDATES_SQL,"wanted","current-account").unwrap(), read(ORIGINAL,"wanted","current-account").unwrap());
        connection.execute("INSERT INTO workspace_exchange VALUES('malformed-source','not-json')", []).unwrap();
        assert!(read(ORIGINAL,"wanted","current-account").is_err());
        assert!(read(LOCAL_OCCURRENCE_CANDIDATES_SQL,"wanted","current-account").is_err());
    }

    #[test]
    fn malformed_public_inventory_does_not_reopen_private_session_for_each_item() {
        struct CountPrivate<'a> { inner: &'a Mem, reads: std::cell::Cell<usize> }
        impl CredentialStore for CountPrivate<'_> {
            fn save(&self,s:&str,a:&str,v:&[u8])->Result<(),String>{self.inner.save(s,a,v)}
            fn read(&self,s:&str,a:&str)->Result<Vec<u8>,String>{self.inner.read(s,a)}
            fn read_optional(&self,s:&str,a:&str)->Result<Option<Vec<u8>>,String>{
                if s==SESSION_SERVICE { self.reads.set(self.reads.get()+1); }
                self.inner.read_optional(s,a)
            }
            fn delete(&self,s:&str,a:&str)->Result<(),String>{self.inner.delete(s,a)}
        }
        struct PublicInvalid(usize);
        impl CloudGateway for PublicInvalid {
            fn read(&self,_:&str)->Result<Option<RemoteEnvelope>,CloudFailure>{panic!("unidentifiable inventory must not read documents")}
            fn commit(&self,_:&str,_:u64,_:&str)->Result<(u64,String),CloudFailure>{panic!("invalid inventory must not commit")}
            fn list(&self)->Result<Vec<RemoteEnvelope>,CloudFailure>{
                Ok((0..self.0).map(|_|RemoteEnvelope{revision:0,payload_hash:String::new(),envelope:"{}".into()}).collect())
            }
        }
        let mut connection=database();
        let private=Mem(RefCell::new(BTreeMap::new()));
        persist_authenticated_session(&mut connection,&private,&Session{
            user_id:"00000000-0000-4000-8000-000000000117".into(),email:"inventory-budget@example.invalid".into(),
            display_name:None,avatar_url:None,access_token:"synthetic-inventory-access".into(),
            refresh_token:"synthetic-inventory-refresh".into(),expires_at_epoch_seconds:9_999_999_999,
        }).unwrap();
        let counted=CountPrivate{inner:&private,reads:std::cell::Cell::new(0)};
        let small=restore_all_remote_conversations(&mut connection,&counted,&PublicInvalid(1)).unwrap();
        assert_eq!(small.failed_count,1);assert!(small.restored.is_empty());assert!(small.cloud_conversation_keys.is_none());
        let small_reads=counted.reads.replace(0);
        let large=restore_all_remote_conversations(&mut connection,&counted,&PublicInvalid(SYNC_INVENTORY_MAX_DOCUMENTS)).unwrap();
        assert_eq!(large.failed_count,SYNC_INVENTORY_MAX_DOCUMENTS);assert!(large.restored.is_empty());assert!(large.cloud_conversation_keys.is_none());
        assert_eq!(counted.reads.get(),small_reads,"public malformed rows must not amplify private credential access");
    }


    #[test]
    fn selected_sync_requires_local_conversation_owner_before_any_cloud_read() {
        struct CountingCloud(std::cell::Cell<usize>);
        impl CloudGateway for CountingCloud {
            fn read(&self, _: &str) -> Result<Option<RemoteEnvelope>, CloudFailure> {
                self.0.set(self.0.get()+1);
                Ok(None)
            }
            fn commit(&self, _: &str, _: u64, _: &str) -> Result<(u64,String), CloudFailure> {
                panic!("missing local targets must never commit")
            }
        }
        let mut connection=database();
        let credentials=Mem(RefCell::new(BTreeMap::new()));
        persist_authenticated_session(&mut connection,&credentials,&Session{
            user_id:"00000000-0000-4000-8000-000000000093".into(),email:"local-owner@example.invalid".into(),
            display_name:None,avatar_url:None,access_token:"synthetic-owner-access".into(),
            refresh_token:"synthetic-owner-refresh".into(),expires_at_epoch_seconds:9_999_999_999,
        }).unwrap();
        connection.execute_batch("CREATE TABLE desktop_temporary_recovery(temporary_id TEXT PRIMARY KEY,recovery_json TEXT NOT NULL,updated_at_ms INTEGER NOT NULL); INSERT INTO desktop_temporary_recovery VALUES('temporary-only-synthetic','synthetic recovery',123);").unwrap();
        for workspace in ["workspace-local","workspace-other"] {
            connection.execute("INSERT INTO workspaces VALUES(?1,'safe','s','p','2026-10-02T00:00:00Z')",[workspace]).unwrap();
            connection.execute("INSERT INTO workspace_exchange VALUES(?1,?2)",params![workspace,json!({"conversations":[{"id":if workspace=="workspace-local"{"local-normal"}else{"other-normal"},"title":"safe","revision":1,"messages":[]}]}).to_string()]).unwrap();
        }
        let cloud=CountingCloud(std::cell::Cell::new(0));
        for id in ["temporary-only-synthetic","missing-normal","other-normal"] {
            assert!(sync_selected_conversation(&mut connection,&credentials,&cloud,"workspace-local",id).is_err());
            assert_eq!(cloud.0.get(),0,"only the ordinary workspace owner may authorize cloud materialization");
        }
        assert_eq!(connection.query_row("SELECT COUNT(*) FROM desktop_selected_conversation_sync",[],|r|r.get::<_,i64>(0)).unwrap(),0);
        assert_eq!(connection.query_row("SELECT COUNT(*) FROM desktop_sync_jobs",[],|r|r.get::<_,i64>(0)).unwrap(),0);
        assert_eq!(connection.query_row("SELECT recovery_json,updated_at_ms FROM desktop_temporary_recovery",[],|r|Ok((r.get::<_,String>(0)?,r.get::<_,i64>(1)?))).unwrap(),("synthetic recovery".into(),123));
    }

    #[test]
    fn rapid_sync_jobs_for_one_account_have_distinct_ids() {
        let ids = (0..256)
            .map(|_| new_sync_job_id("account-fixture-id").unwrap())
            .collect::<BTreeSet<_>>();
        assert_eq!(ids.len(), 256);
    }

    #[test]
    fn completed_answer_queues_only_selected_conversation_and_delete_supersedes_it() {
        let mut connection = database();
        connection.execute("INSERT INTO desktop_selected_conversation_sync(account_ref,workspace_id,conversation_id,document_id,remote_revision,payload_hash,local_content_hash,last_synced_at_ms) VALUES('account','workspace','selected','conversation-doc',1,'old','old',1)", []).unwrap();
        let transaction = connection.transaction().unwrap();
        queue_selected_content_change(&transaction, "workspace", "unselected", "hash-1").unwrap();
        queue_selected_content_change(&transaction, "workspace", "selected", "hash-1").unwrap();
        queue_selected_content_change(&transaction, "workspace", "selected", "hash-2").unwrap();
        transaction.commit().unwrap();
        let pending: (i64, String, String) = connection.query_row(
            "SELECT count(*),stage,payload_hash FROM desktop_sync_jobs", [],
            |row| Ok((row.get(0)?,row.get(1)?,row.get(2)?)),
        ).unwrap();
        assert_eq!(pending, (1, "CONTENT_PENDING".into(), "hash-2".into()));
        connection.execute("UPDATE desktop_sync_jobs SET stage='TITLE_CONFLICT' WHERE stage='CONTENT_PENDING'", []).unwrap();
        let transaction = connection.transaction().unwrap();
        queue_selected_content_change(&transaction, "workspace", "selected", "hash-3").unwrap();
        transaction.commit().unwrap();
        let resumed: (String,String) = connection.query_row("SELECT stage,payload_hash FROM desktop_sync_jobs WHERE job_id LIKE 'content-pending:%'", [], |row| Ok((row.get(0)?,row.get(1)?))).unwrap();
        assert_eq!(resumed, ("CONTENT_PENDING".into(), "hash-3".into()));
        connection.execute("UPDATE desktop_sync_jobs SET stage='TITLE_CONFLICT' WHERE stage='CONTENT_PENDING'", []).unwrap();
        let transaction = connection.transaction().unwrap();
        queue_selected_deletion(&transaction, "workspace", "selected").unwrap();
        transaction.commit().unwrap();
        let (content, deletion): (i64, i64) = connection.query_row(
            "SELECT count(*) FILTER (WHERE job_id LIKE 'content-pending:%'),count(*) FILTER (WHERE stage='DELETE_PENDING') FROM desktop_sync_jobs", [],
            |row| Ok((row.get(0)?,row.get(1)?)),
        ).unwrap();
        assert_eq!((content, deletion), (0, 1));
    }

    #[test]
    fn legacy_migration_preserves_records_and_resolves_unknown_commit_by_exact_readback() {
        struct MigrationCloud {
            document: RefCell<RemoteEnvelope>,
            writes: std::cell::Cell<u32>,
            unknown_after_write: bool,
            unknown_before_write: bool,
        }
        impl CloudGateway for MigrationCloud {
            fn read(&self, _: &str) -> Result<Option<RemoteEnvelope>, CloudFailure> {
                Ok(Some(self.document.borrow().clone()))
            }
            fn commit(
                &self,
                _: &str,
                expected: u64,
                envelope: &str,
            ) -> Result<(u64, String), CloudFailure> {
                if expected != self.document.borrow().revision {
                    return Err(CloudFailure::StaleRevision);
                }
                if self.unknown_before_write {
                    return Err(CloudFailure::UnknownCommit);
                }
                let root: Value = serde_json::from_str(envelope).unwrap();
                let hash = root["payloadHash"].as_str().unwrap().to_owned();
                self.writes.set(self.writes.get() + 1);
                *self.document.borrow_mut() = RemoteEnvelope {
                    revision: expected + 1,
                    payload_hash: hash.clone(),
                    envelope: envelope.into(),
                };
                if self.unknown_after_write {
                    Err(CloudFailure::UnknownCommit)
                } else {
                    Ok((expected + 1, hash))
                }
            }
            fn list(&self) -> Result<Vec<RemoteEnvelope>, CloudFailure> {
                Ok(vec![self.document.borrow().clone()])
            }
        }

        let mut connection = database();
        let credentials = Mem(RefCell::new(BTreeMap::new()));
        let session = Session {
            user_id: "migration-fixture".into(),
            email: "fixture@example.invalid".into(),
            display_name: None,
            avatar_url: None,
            access_token: "fixture".into(),
            refresh_token: "fixture".into(),
            expires_at_epoch_seconds: 9_999_999_999,
        };
        save_session(&credentials, &session).unwrap();
        let account = sync_state_v1::account_ref(&session.user_id).unwrap();
        let document_id = desktop_data_area::document_id(
            crate::DesktopDataArea::Chat,
            "migration-conversation",
        );
        let payload = json!({
            "format":"nfai.sync.payload","protocolVersion":1,"schemaVersion":1,
            "appId":APP_ID,"documentId":document_id,"revision":4,
            "records":[{"kind":"conversation","id":"migration-conversation",
                "revision":8,"classification":"NORMAL",
                "content":{"surface":"CHAT","title":"Fixture","titleRevision":3,"nodes":[]}}]
        });
        let recovery_code = "synthetic-migration-code";
        let encrypted = sync_v1::seal(payload.clone(), recovery_code, &[7u8; 32]).unwrap();
        let root: Value = serde_json::from_str(&encrypted).unwrap();
        let remote = RemoteEnvelope {
            revision: 4,
            payload_hash: root["payloadHash"].as_str().unwrap().into(),
            envelope: encrypted,
        };
        let material = sync_v1::recover_account_material(
            &remote.envelope,
            recovery_code,
            APP_ID,
            &document_id,
            4,
        )
        .unwrap();
        let cloud = MigrationCloud {
            document: RefCell::new(remote.clone()),
            writes: std::cell::Cell::new(0),
            unknown_after_write: true,
            unknown_before_write: false,
        };

        assert!(migrate_legacy_document(
            &connection,
            &credentials,
            &cloud,
            &account,
            remote.clone(),
        )
        .is_err());
        assert_eq!(cloud.writes.get(), 0);
        assert_eq!(cloud.document.borrow().envelope, remote.envelope);

        let not_written = MigrationCloud {
            document: RefCell::new(remote.clone()),
            writes: std::cell::Cell::new(0),
            unknown_after_write: false,
            unknown_before_write: true,
        };

        credentials
            .save(RECOVERY_SERVICE, &account, &material.wrapping_key)
            .unwrap();
        connection.execute(
            "INSERT INTO desktop_cloud_account_state VALUES(?1,?2,?3,NULL,NULL,'READY',0,1,?4,1,NULL,NULL,'fixture-device',1,1,NULL,NULL,NULL)",
            params![account, session.user_id, session.email, URL_SAFE_NO_PAD.encode(&material.salt)],
        ).unwrap();
        assert!(migrate_legacy_document(
            &connection,
            &credentials,
            &not_written,
            &account,
            remote.clone(),
        )
        .is_err());
        assert_eq!(not_written.writes.get(), 0);
        assert_eq!(not_written.document.borrow().envelope, remote.envelope);
        let migrated = migrate_legacy_document(
            &connection,
            &credentials,
            &cloud,
            &account,
            remote.clone(),
        )
        .unwrap();
        let opened = sync_v1::open_direct(&migrated.envelope, APP_ID, &document_id, 5).unwrap();
        assert_eq!(opened["records"], payload["records"]);
        assert_eq!(cloud.writes.get(), 1);
        assert_eq!(migrated.revision, 5);
        assert_eq!(cloud.document.borrow().payload_hash, migrated.payload_hash);

        assert_eq!(
            migrate_legacy_document(&connection, &credentials, &cloud, &account, migrated.clone())
                .unwrap().envelope,
            migrated.envelope,
        );
        assert_eq!(cloud.writes.get(), 1);
        assert!(migrate_legacy_document(&connection, &credentials, &cloud, &account, remote.clone()).is_ok());
        assert_eq!(cloud.writes.get(), 1);

        let second_cloud = MigrationCloud {
            document: RefCell::new(remote),
            writes: std::cell::Cell::new(0),
            unknown_after_write: true,
            unknown_before_write: false,
        };
        let receipt = restore_all_remote_conversations(&mut connection, &credentials, &second_cloud)
            .unwrap();
        assert_eq!(second_cloud.writes.get(), 1);
        assert_eq!(receipt.upgraded_legacy_direct_count, 1);
    }

    #[test]
    fn cloud_list_recovers_one_fast_transient_failure_but_never_retries_permanent_failures() {
        struct Flaky { attempts: std::cell::Cell<usize>, failure: CloudFailure }
        impl CloudGateway for Flaky {
            fn read(&self, _: &str) -> Result<Option<RemoteEnvelope>, CloudFailure> { panic!("unexpected read") }
            fn commit(&self, _: &str, _: u64, _: &str) -> Result<(u64, String), CloudFailure> { panic!("read must not write") }
            fn list(&self) -> Result<Vec<RemoteEnvelope>, CloudFailure> {
                let count = self.attempts.get(); self.attempts.set(count + 1);
                if count == 0 { Err(self.failure) } else { Ok(vec![]) }
            }
        }
        for failure in [CloudFailure::ServerUnavailable, CloudFailure::Known, CloudFailure::Unauthorized, CloudFailure::RateLimited, CloudFailure::MalformedResponse] {
            let cloud = Flaky { attempts: std::cell::Cell::new(0), failure };
            let result = restore_all_remote_conversations(&mut database(), &Mem(RefCell::new(BTreeMap::new())), &cloud);
            let transient = matches!(failure, CloudFailure::ServerUnavailable | CloudFailure::Known);
            assert_eq!(result.is_ok(), transient);
            assert_eq!(cloud.attempts.get(), if transient { 2 } else { 1 });
        }
    }

    #[test]
    fn shared_cross_platform_title_vectors_agree_on_read_and_upload() {
        let vectors: Vec<Value> = serde_json::from_str(include_str!("../../../protocol/fixtures/title-sync-v1.json")).unwrap();
        for vector in vectors {
            for upload in [false, true] {
                let mut connection = database();
                let mut local = vector["local"].clone();
                let mut remote = vector["remote"].clone();
                for value in [&mut local,&mut remote] {
                    value["id"] = json!("c"); value["revision"] = json!(9); value["currentLeafId"] = json!("m");
                    value["messages"] = json!([{"id":"m","parentId":null,"role":"user","revision":1,"delivery":"COMPLETE","blocks":[{"kind":"TEXT","text":"Fixture message"}]}]);
                }
                connection.execute("INSERT INTO workspaces VALUES('w','fixture','hash','package','2026-01-01T00:00:00Z')",[]).unwrap();
                connection.execute("INSERT INTO workspace_exchange VALUES('w',?1)",[json!({"conversations":[local]}).to_string()]).unwrap();
                let transaction = connection.transaction().unwrap();
                let result = if upload { merge_remote_additions_for_local_commit(&transaction,"w","c",remote.as_object().unwrap(),9) }
                    else { merge_newer_remote_conversation(&transaction,"w","c",remote.as_object().unwrap(),9) };
                if vector["conflict"] == true { assert!(result.unwrap_err().contains("标题版本冲突"),"{}",vector["name"]); }
                else {
                    result.unwrap();
                    let actual: (String,u64) = transaction.query_row("SELECT json_extract(exchange_json,'$.conversations[0].title'),json_extract(exchange_json,'$.conversations[0].titleRevision') FROM workspace_exchange WHERE workspace_id='w'",[],|r|Ok((r.get(0)?,r.get(1)?))).unwrap();
                    assert_eq!(actual,(vector["expectedTitle"].as_str().unwrap().into(),vector["expectedRevision"].as_u64().unwrap()),"{}",vector["name"]);
                }
            }
        }
    }

    #[test]
    fn android_selected_conversation_wire_shape_normalizes_to_desktop_text_tree() {
        let mut content = json!({
            "title":"Android 对话","currentLeafMessageId":"m2","createdAtEpochMs":1000,"updatedAtEpochMs":2000,"surface":"CHAT",
            "nodes":[
                {"id":"m1","parentMessageId":null,"siblingPosition":0,"role":"USER","createdAtEpochMs":1000,"deliveryState":"COMPLETE","revision":1,"revisesMessageId":null,"text":["你好"]},
                {"id":"m2","parentMessageId":"m1","siblingPosition":0,"role":"ASSISTANT","createdAtEpochMs":2000,"deliveryState":"COMPLETE","revision":1,"revisesMessageId":null,"text":["你好，我在。"]}
            ]
        })
        .as_object()
        .cloned()
        .unwrap();

        normalize_android_selected_conversation(&mut content).unwrap();

        assert_eq!(content["currentLeafId"], "m2");
        assert_eq!(content["messages"][1]["role"], "assistant");
        assert_eq!(content["messages"][1]["blocks"][0]["text"], "你好，我在。");
        assert!(content.get("nodes").is_none());
    }

    struct Mem(RefCell<BTreeMap<(String, String), Vec<u8>>>);
    impl CredentialStore for Mem {
        fn read_optional(&self, s: &str, a: &str) -> Result<Option<Vec<u8>>, String> {
            Ok(self.0.borrow().get(&(s.into(), a.into())).cloned())
        }
        fn save(&self, s: &str, a: &str, v: &[u8]) -> Result<(), String> {
            self.0.borrow_mut().insert((s.into(), a.into()), v.into());
            Ok(())
        }
        fn read(&self, s: &str, a: &str) -> Result<Vec<u8>, String> {
            self.0
                .borrow()
                .get(&(s.into(), a.into()))
                .cloned()
                .ok_or_else(|| "missing".into())
        }
        fn delete(&self, s: &str, a: &str) -> Result<(), String> {
            self.0.borrow_mut().remove(&(s.into(), a.into()));
            Ok(())
        }
    }
    fn database() -> Connection {
        database_on(Connection::open_in_memory().unwrap())
    }

    #[test]
    fn signout_private_delete_failure_preserves_both_sql_owners_and_intents() {
        struct RejectDelete<'a>(&'a Mem);
        impl CredentialStore for RejectDelete<'_> {
            fn save(&self,s:&str,a:&str,v:&[u8])->Result<(),String>{self.0.save(s,a,v)}
            fn read(&self,s:&str,a:&str)->Result<Vec<u8>,String>{self.0.read(s,a)}
            fn read_optional(&self,s:&str,a:&str)->Result<Option<Vec<u8>>,String>{self.0.read_optional(s,a)}
            fn delete(&self,_:&str,_:&str)->Result<(),String>{Err("synthetic private-store rejection".into())}
        }
        let mut connection=database();
        let credentials=Mem(RefCell::new(BTreeMap::new()));
        let session=Session{user_id:"synthetic-signout-account".into(),email:"signout@example.invalid".into(),display_name:None,avatar_url:None,access_token:"synthetic-access".into(),refresh_token:"synthetic-refresh".into(),expires_at_epoch_seconds:9_999_999_999};
        persist_authenticated_session(&mut connection,&credentials,&session).unwrap();
        let snapshot=|c:&Connection|(
            c.query_row("SELECT state,revision FROM desktop_cloud_account_state",[],|r|Ok((r.get::<_,String>(0)?,r.get::<_,i64>(1)?))).unwrap(),
            c.query_row("SELECT state,revision FROM sync_account_metadata",[],|r|Ok((r.get::<_,String>(0)?,r.get::<_,i64>(1)?))).unwrap(),
            c.query_row("SELECT COUNT(*) FROM sync_intents",[],|r|r.get::<_,i64>(0)).unwrap());
        let before=snapshot(&connection);
        assert!(sign_out_keep_local(&mut connection,&RejectDelete(&credentials)).is_err());
        assert_eq!(snapshot(&connection),before);
        assert_eq!(read_session(&credentials).unwrap().unwrap().email,session.email);
    }

    #[test]
    fn signout_sql_rejection_or_commit_failure_preserves_private_session_and_sql() {
        for commit_failure in [false,true] {
            let mut connection=database();
            let credentials=Mem(RefCell::new(BTreeMap::new()));
            let session=Session{user_id:"synthetic-signout-account".into(),email:"signout@example.invalid".into(),display_name:None,avatar_url:None,access_token:"synthetic-access".into(),refresh_token:"synthetic-refresh".into(),expires_at_epoch_seconds:9_999_999_999};
            persist_authenticated_session(&mut connection,&credentials,&session).unwrap();
            let original=credentials.read(SESSION_SERVICE,"active").unwrap();
            if commit_failure {
                connection.execute_batch("PRAGMA foreign_keys=ON;CREATE TABLE synthetic_signout_parent(id INTEGER PRIMARY KEY);CREATE TABLE synthetic_signout_child(id INTEGER REFERENCES synthetic_signout_parent(id) DEFERRABLE INITIALLY DEFERRED);CREATE TRIGGER synthetic_signout_commit BEFORE UPDATE ON desktop_cloud_account_state BEGIN INSERT INTO synthetic_signout_child VALUES(71);END;").unwrap();
            } else {
                connection.execute_batch("CREATE TRIGGER synthetic_signout_update BEFORE UPDATE ON desktop_cloud_account_state BEGIN SELECT RAISE(ABORT,'synthetic account update rejected');END;").unwrap();
            }
            assert!(sign_out_keep_local(&mut connection,&credentials).is_err());
            assert_eq!(credentials.read(SESSION_SERVICE,"active").unwrap(),original);
            assert_eq!(connection.query_row("SELECT state,revision FROM desktop_cloud_account_state",[],|r|Ok((r.get::<_,String>(0)?,r.get::<_,i64>(1)?))).unwrap(),("READY".into(),1));
            assert_eq!(connection.query_row("SELECT state,revision FROM sync_account_metadata",[],|r|Ok((r.get::<_,String>(0)?,r.get::<_,i64>(1)?))).unwrap(),("READY".into(),1));
            assert_eq!(connection.query_row("SELECT COUNT(*) FROM sync_intents",[],|r|r.get::<_,i64>(0)).unwrap(),1);
        }
    }
    fn database_on(mut c: Connection) -> Connection {
        c.execute_batch("CREATE TABLE sync_account_metadata (account_ref TEXT PRIMARY KEY NOT NULL, state TEXT NOT NULL, revision INTEGER NOT NULL, key_alias_ref TEXT, wrapped_key_ref TEXT, wrapped_key_sha256 TEXT, direction_fact TEXT, last_error TEXT, updated_at TEXT NOT NULL); CREATE TABLE sync_intents (intent_id TEXT PRIMARY KEY NOT NULL, account_ref TEXT NOT NULL, expected_revision INTEGER, resulting_revision INTEGER NOT NULL, resulting_state TEXT NOT NULL, created_at TEXT NOT NULL); CREATE TABLE workspaces(id TEXT PRIMARY KEY,title TEXT NOT NULL,semantic_hash TEXT NOT NULL,package_hash TEXT NOT NULL,created_at TEXT NOT NULL); CREATE TABLE workspace_exchange(workspace_id TEXT PRIMARY KEY,exchange_json TEXT NOT NULL); CREATE TABLE desktop_portable_personalization_v1(id INTEGER PRIMARY KEY CHECK(id=1),interests TEXT NOT NULL,revision INTEGER NOT NULL,updated_at_ms INTEGER NOT NULL); INSERT INTO desktop_portable_personalization_v1 VALUES(1,'',0,0);").unwrap();
        let tx = c.transaction().unwrap();
        migrate(&tx).unwrap();
        tx.commit().unwrap();
        let tx = c.transaction().unwrap();
        crate::desktop_app_settings_v1::migrate(&tx).unwrap();
        tx.commit().unwrap();
        let tx = c.transaction().unwrap();
        migrate_recovery_rotation(&tx).unwrap();
        tx.commit().unwrap();
        let tx = c.transaction().unwrap();
        migrate_pending_recovery_confirmation(&tx).unwrap();
        tx.commit().unwrap();
        let tx = c.transaction().unwrap();
        crate::desktop_reminders_v1::migrate(&tx).unwrap();
        tx.commit().unwrap();
        c
    }

    #[cfg(windows)]
    #[test]
    fn account_login_kill_child() {
        let Some(root) = std::env::var_os("NANFENG_AI_ACCOUNT_KILL_ROOT") else { return; };
        let root = PathBuf::from(root);
        assert_eq!(fs::read(root.join(".synthetic-account-login-kill")).unwrap(), b"account-login-owner-v1");
        let phase = std::env::var("NANFENG_AI_ACCOUNT_KILL_PHASE").unwrap();
        let mut connection = database_on(Connection::open(root.join("account.sqlite3")).unwrap());
        let credentials = AppPrivateAccountCredentialStore::at(&root);
        let original = Session {
            user_id: "synthetic-owner-kill-profile".into(), email: "original@example.invalid".into(),
            display_name: None, avatar_url: None, access_token: "synthetic-original-access".into(),
            refresh_token: "synthetic-original-refresh".into(), expires_at_epoch_seconds: 9_999_999_999,
        };
        persist_authenticated_session(&mut connection, &credentials, &original).unwrap();
        connection.execute_batch("UPDATE desktop_cloud_account_state SET state='FAILED',last_error_code='REMOTE_CONFLICT'; UPDATE sync_account_metadata SET state='FAILED',last_error='REMOTE_CONFLICT'; INSERT INTO desktop_selected_conversation_sync SELECT account_ref,'synthetic-workspace','synthetic-conversation','synthetic-document',1,'synthetic-payload','synthetic-local',1 FROM desktop_cloud_account_state;").unwrap();
        struct Paused<'a> { inner: &'a AppPrivateAccountCredentialStore, root: &'a Path, phase: &'a str }
        impl CredentialStore for Paused<'_> {
            fn save(&self, s: &str, a: &str, value: &[u8]) -> Result<(), String> {
                if self.phase == "before-session" {
                    fs::write(self.root.join("reached"), b"before-session").unwrap();
                    loop { std::thread::park_timeout(Duration::from_millis(100)); }
                }
                self.inner.save(s,a,value)?;
                if self.phase == "after-session" {
                    fs::write(self.root.join("reached"), b"after-session").unwrap();
                    loop { std::thread::park_timeout(Duration::from_millis(100)); }
                }
                Ok(())
            }
            fn read(&self,s:&str,a:&str)->Result<Vec<u8>,String>{self.inner.read(s,a)}
            fn read_optional(&self,s:&str,a:&str)->Result<Option<Vec<u8>>,String>{self.inner.read_optional(s,a)}
            fn delete(&self,s:&str,a:&str)->Result<(),String>{self.inner.delete(s,a)}
        }
        let candidate = Session { email: "updated@example.invalid".into(), display_name: Some("updated synthetic profile".into()), access_token: "synthetic-updated-access".into(), ..original };
        let paused=Paused{inner:&credentials,root:&root,phase:&phase};
        persist_authenticated_session(&mut connection,&paused,&candidate).unwrap();
        assert_eq!(phase,"after-commit");
        fs::write(root.join("reached"), b"after-commit").unwrap();
        loop { std::thread::park_timeout(Duration::from_millis(100)); }
    }

    #[cfg(windows)]
    #[test]
    fn account_login_process_kill_recovers_session_and_sql_profile_pair() {
        struct OwnedChild(std::process::Child);
        impl Drop for OwnedChild { fn drop(&mut self) { let _=self.0.kill();let _=self.0.wait(); } }
        for phase in ["before-session","after-session","after-commit"] {
            let root=PathBuf::from("D:/CodexTemp").join(format!("nanfeng-ai-account-kill.{}",random_urlsafe(18).unwrap()));
            fs::create_dir_all(&root).unwrap();
            fs::write(root.join(".synthetic-account-login-kill"),b"account-login-owner-v1").unwrap();
            println!("OWNER_ACCOUNT_KILL phase={} root={}",phase,root.display());
            let mut child=OwnedChild(Command::new(std::env::current_exe().unwrap())
                .arg("--exact").arg("desktop_account_sync_v1::tests::account_login_kill_child").arg("--nocapture")
                .env("NANFENG_AI_ACCOUNT_KILL_ROOT",&root).env("NANFENG_AI_ACCOUNT_KILL_PHASE",phase)
                .stdout(std::process::Stdio::null()).stderr(std::process::Stdio::null()).spawn().unwrap());
            let deadline=Instant::now()+Duration::from_secs(15);
            while !root.join("reached").exists() && Instant::now()<deadline {
                assert!(child.0.try_wait().unwrap().is_none(),"child exited before checkpoint");
                std::thread::sleep(Duration::from_millis(20));
            }
            assert!(root.join("reached").exists(),"checkpoint not reached");
            child.0.kill().unwrap();child.0.wait().unwrap();
            let mut connection=Connection::open(root.join("account.sqlite3")).unwrap();
            let credentials=AppPrivateAccountCredentialStore::at(&root);
            ensure_current_area_direct_account(&mut connection,&credentials).unwrap();
            let session=read_session(&credentials).unwrap().unwrap();
            let expected=if phase=="before-session" {"original@example.invalid"} else {"updated@example.invalid"};
            assert_eq!(session.email,expected);
            let profile:(String,Option<String>)=connection.query_row("SELECT email,display_name FROM desktop_cloud_account_state",[],|r|Ok((r.get(0)?,r.get(1)?))).unwrap();
            assert_eq!(profile,(session.email.clone(),session.display_name.clone()));
            assert_eq!(connection.query_row("SELECT state,last_error_code FROM desktop_cloud_account_state",[],|r|Ok((r.get::<_,String>(0)?,r.get::<_,String>(1)?))).unwrap(),("FAILED".into(),"REMOTE_CONFLICT".into()));
            assert_eq!(connection.query_row("SELECT state,last_error FROM sync_account_metadata",[],|r|Ok((r.get::<_,String>(0)?,r.get::<_,String>(1)?))).unwrap(),("FAILED".into(),"REMOTE_CONFLICT".into()));
            assert_eq!(connection.query_row("SELECT COUNT(*) FROM desktop_selected_conversation_sync",[],|r|r.get::<_,i64>(0)).unwrap(),1);
            let revision=connection.query_row("SELECT revision FROM desktop_cloud_account_state",[],|r|r.get::<_,i64>(0)).unwrap();
            ensure_current_area_direct_account(&mut connection,&credentials).unwrap();
            assert_eq!(connection.query_row("SELECT revision FROM desktop_cloud_account_state",[],|r|r.get::<_,i64>(0)).unwrap(),revision);
            assert_eq!(connection.query_row("PRAGMA integrity_check",[],|r|r.get::<_,String>(0)).unwrap(),"ok");
        }
    }

    #[cfg(windows)]
    #[test]
    fn account_signout_kill_child() {
        let Some(root) = std::env::var_os("NANFENG_AI_SIGNOUT_KILL_ROOT") else { return; };
        let root = PathBuf::from(root);
        assert_eq!(fs::read(root.join(".synthetic-account-signout-kill")).unwrap(), b"account-signout-owner-v1");
        let mut connection = database_on(Connection::open(root.join("account.sqlite3")).unwrap());
        let credentials = AppPrivateAccountCredentialStore::at(&root);
        let session = Session {
            user_id: "synthetic-signout-kill-profile".into(), email: "original@example.invalid".into(),
            display_name: None, avatar_url: None, access_token: "synthetic-access".into(),
            refresh_token: "synthetic-refresh".into(), expires_at_epoch_seconds: 9_999_999_999,
        };
        persist_authenticated_session(&mut connection, &credentials, &session).unwrap();
        connection.execute("UPDATE desktop_cloud_account_state SET periodic_enabled=1", []).unwrap();
        struct Paused<'a> { inner: &'a AppPrivateAccountCredentialStore, root: &'a Path }
        impl CredentialStore for Paused<'_> {
            fn save(&self,s:&str,a:&str,v:&[u8])->Result<(),String>{self.inner.save(s,a,v)}
            fn read(&self,s:&str,a:&str)->Result<Vec<u8>,String>{self.inner.read(s,a)}
            fn read_optional(&self,s:&str,a:&str)->Result<Option<Vec<u8>>,String>{self.inner.read_optional(s,a)}
            fn delete(&self,s:&str,a:&str)->Result<(),String>{
                self.inner.delete(s,a)?;
                fs::write(self.root.join("reached"), b"after-session-delete").unwrap();
                loop { std::thread::park_timeout(Duration::from_millis(100)); }
            }
        }
        sign_out_keep_local(&mut connection, &Paused { inner: &credentials, root: &root }).unwrap();
    }

    #[test]
    fn signout_reconcile_denied_credential_read_never_means_absent() {
        let mut connection=database();
        let credentials=Mem(RefCell::new(BTreeMap::new()));
        let session=Session { user_id:"synthetic-reconcile-owner".into(),email:"original@example.invalid".into(),display_name:None,avatar_url:None,access_token:"synthetic-access".into(),refresh_token:"synthetic-refresh".into(),expires_at_epoch_seconds:9_999_999_999 };
        persist_authenticated_session(&mut connection,&credentials,&session).unwrap();
        connection.execute("UPDATE desktop_cloud_account_state SET periodic_enabled=1",[]).unwrap();
        struct Denied;
        impl CredentialStore for Denied {
            fn save(&self,_:&str,_:&str,_:&[u8])->Result<(),String>{Err("synthetic-denied".into())}
            fn read(&self,_:&str,_:&str)->Result<Vec<u8>,String>{Err("synthetic-denied".into())}
            fn read_optional(&self,_:&str,_:&str)->Result<Option<Vec<u8>>,String>{Err("synthetic-denied".into())}
            fn delete(&self,_:&str,_:&str)->Result<(),String>{Err("synthetic-denied".into())}
        }
        assert!(ensure_current_area_direct_account(&mut connection,&Denied).is_err());
        assert_eq!(connection.query_row("SELECT state,periodic_enabled,revision FROM desktop_cloud_account_state",[],|r|Ok((r.get::<_,String>(0)?,r.get::<_,i64>(1)?,r.get::<_,i64>(2)?))).unwrap(),("READY".into(),1,1));
        assert_eq!(connection.query_row("SELECT state,revision FROM sync_account_metadata",[],|r|Ok((r.get::<_,String>(0)?,r.get::<_,i64>(1)?))).unwrap(),("READY".into(),1));
        assert_eq!(connection.query_row("SELECT COUNT(*) FROM sync_intents",[],|r|r.get::<_,i64>(0)).unwrap(),1);
    }

    #[test]
    fn unknown_job_read_failure_blocks_cloud_access() {
        let mut connection=database();
        let credentials=Mem(RefCell::new(BTreeMap::new()));
        let session=Session { user_id:"synthetic-unknown-read-owner".into(),email:"original@example.invalid".into(),display_name:None,avatar_url:None,access_token:"synthetic-access".into(),refresh_token:"synthetic-refresh".into(),expires_at_epoch_seconds:9_999_999_999 };
        persist_authenticated_session(&mut connection,&credentials,&session).unwrap();
        connection.execute_batch("ALTER TABLE desktop_sync_jobs RENAME TO synthetic_unreadable_jobs").unwrap();
        struct Counted(std::cell::Cell<u32>);
        impl CloudGateway for Counted {
            fn read(&self,_:&str)->Result<Option<RemoteEnvelope>,CloudFailure>{self.0.set(self.0.get()+1);Ok(None)}
            fn commit(&self,_:&str,_:u64,_:&str)->Result<(u64,String),CloudFailure>{panic!("unreadable UNKNOWN ledger must never commit")}
            fn delete(&self,_:&str,_:u64)->Result<bool,CloudFailure>{panic!("unreadable UNKNOWN ledger must never delete")}
        }
        let gateway=Counted(std::cell::Cell::new(0));
        assert!(sync_selected_conversation(&mut connection,&credentials,&gateway,"synthetic-workspace","synthetic-conversation").is_err());
        assert_eq!(gateway.0.get(),0,"UNKNOWN核对读取失败不得当零并进入云端");
    }

    #[test]
    fn malformed_account_projection_never_claims_signed_out() {
        let mut connection=database();
        let credentials=Mem(RefCell::new(BTreeMap::new()));
        let session=Session { user_id:"synthetic-projection-owner".into(),email:"original@example.invalid".into(),display_name:None,avatar_url:None,access_token:"synthetic-access".into(),refresh_token:"synthetic-refresh".into(),expires_at_epoch_seconds:9_999_999_999 };
        persist_authenticated_session(&mut connection,&credentials,&session).unwrap();
        connection.execute("UPDATE desktop_cloud_account_state SET periodic_enabled='synthetic-invalid-integer'",[]).unwrap();
        ensure_current_area_direct_account(&mut connection,&credentials).unwrap();
        assert!(projection(&connection,&credentials,true,None).is_err(),"账号SQL读取错误不得投影成SIGNED_OUT");
        assert_eq!(read_session(&credentials).unwrap().unwrap().email,session.email);
    }

    #[cfg(windows)]
    #[test]
    fn account_signout_process_kill_cancels_periodic_work_on_cold_read() {
        struct OwnedChild(std::process::Child);
        impl Drop for OwnedChild { fn drop(&mut self) { let _=self.0.kill();let _=self.0.wait(); } }
        let root=PathBuf::from("D:/CodexTemp").join(format!("nanfeng-ai-signout-kill.{}",random_urlsafe(18).unwrap()));
        fs::create_dir_all(&root).unwrap();
        fs::write(root.join(".synthetic-account-signout-kill"),b"account-signout-owner-v1").unwrap();
        println!("OWNER_SIGNOUT_KILL root={}",root.display());
        let mut child=OwnedChild(Command::new(std::env::current_exe().unwrap())
            .arg("--exact").arg("desktop_account_sync_v1::tests::account_signout_kill_child").arg("--nocapture")
            .env("NANFENG_AI_SIGNOUT_KILL_ROOT",&root)
            .stdout(std::process::Stdio::null()).stderr(std::process::Stdio::null()).spawn().unwrap());
        let deadline=Instant::now()+Duration::from_secs(15);
        while !root.join("reached").exists() && Instant::now()<deadline {
            assert!(child.0.try_wait().unwrap().is_none(),"child exited before checkpoint");
            std::thread::sleep(Duration::from_millis(20));
        }
        assert!(root.join("reached").exists(),"checkpoint not reached");
        child.0.kill().unwrap();child.0.wait().unwrap();
        let mut connection=Connection::open(root.join("account.sqlite3")).unwrap();
        let credentials=AppPrivateAccountCredentialStore::at(&root);
        ensure_current_area_direct_account(&mut connection,&credentials).unwrap();
        assert!(read_session(&credentials).unwrap().is_none());
        let state:(String,i64)=connection.query_row("SELECT state,periodic_enabled FROM desktop_cloud_account_state",[],|r|Ok((r.get(0)?,r.get(1)?))).unwrap();
        assert_eq!(state,("SIGNED_OUT_KEEP_LOCAL".into(),0),"退出中断冷启必须取消定期工作并配对SQL状态");
        assert_eq!(connection.query_row("SELECT state FROM sync_account_metadata",[],|r|r.get::<_,String>(0)).unwrap(),"SIGNED_OUT_KEEP_LOCAL");
        let revisions:(i64,i64,i64)=connection.query_row("SELECT (SELECT revision FROM desktop_cloud_account_state),(SELECT revision FROM sync_account_metadata),(SELECT COUNT(*) FROM sync_intents)",[],|r|Ok((r.get(0)?,r.get(1)?,r.get(2)?))).unwrap();
        ensure_current_area_direct_account(&mut connection,&credentials).unwrap();
        assert_eq!(connection.query_row("SELECT (SELECT revision FROM desktop_cloud_account_state),(SELECT revision FROM sync_account_metadata),(SELECT COUNT(*) FROM sync_intents)",[],|r|Ok((r.get::<_,i64>(0)?,r.get::<_,i64>(1)?,r.get::<_,i64>(2)?))).unwrap(),revisions);
    }

    #[test]
    fn rejected_login_identity_preserves_the_previously_authenticated_session() {
        let mut connection = database();
        let credentials = Mem(RefCell::new(BTreeMap::new()));
        let original = Session {
            user_id: "synthetic-original-account".into(),
            email: "original@example.invalid".into(),
            display_name: None,
            avatar_url: None,
            access_token: "synthetic-original-access".into(),
            refresh_token: "synthetic-original-refresh".into(),
            expires_at_epoch_seconds: 9_999_999_999,
        };
        persist_authenticated_session(&mut connection, &credentials, &original).unwrap();
        let before = credentials.read(SESSION_SERVICE, "active").unwrap();
        let mut invalid = original.clone();
        invalid.user_id = "invalid/user".into();
        invalid.access_token = "synthetic-rejected-access".into();
        assert!(persist_authenticated_session(&mut connection, &credentials, &invalid).is_err());
        assert_eq!(credentials.read(SESSION_SERVICE, "active").unwrap(), before);
        assert_eq!(connection.query_row("SELECT COUNT(*) FROM desktop_cloud_account_state", [], |row| row.get::<_, i64>(0)).unwrap(), 1);
    }

    #[test]
    fn rejected_login_database_write_preserves_the_previously_authenticated_session() {
        let mut connection = database();
        let credentials = Mem(RefCell::new(BTreeMap::new()));
        let original = Session {
            user_id: "synthetic-original-account".into(),
            email: "original@example.invalid".into(),
            display_name: None,
            avatar_url: None,
            access_token: "synthetic-original-access".into(),
            refresh_token: "synthetic-original-refresh".into(),
            expires_at_epoch_seconds: 9_999_999_999,
        };
        persist_authenticated_session(&mut connection, &credentials, &original).unwrap();
        let before = credentials.read(SESSION_SERVICE, "active").unwrap();
        connection.execute_batch("CREATE TRIGGER reject_new_login BEFORE INSERT ON desktop_cloud_account_state BEGIN SELECT RAISE(ABORT,'synthetic SQL rejection'); END;").unwrap();
        let mut rejected = original.clone();
        rejected.user_id = "synthetic-rejected-account".into();
        assert!(persist_authenticated_session(&mut connection, &credentials, &rejected).is_err());
        assert_eq!(credentials.read(SESSION_SERVICE, "active").unwrap(), before);
        assert_eq!(connection.query_row("SELECT COUNT(*) FROM desktop_cloud_account_state", [], |row| row.get::<_, i64>(0)).unwrap(), 1);
    }

    #[test]
    fn rejected_login_sql_commit_restores_previously_published_session() {
        let mut connection = database();
        let credentials = Mem(RefCell::new(BTreeMap::new()));
        let original = Session {
            user_id: "synthetic-original-account".into(),
            email: "original@example.invalid".into(),
            display_name: None,
            avatar_url: None,
            access_token: "synthetic-original-access".into(),
            refresh_token: "synthetic-original-refresh".into(),
            expires_at_epoch_seconds: 9_999_999_999,
        };
        persist_authenticated_session(&mut connection, &credentials, &original).unwrap();
        let before = credentials.read(SESSION_SERVICE, "active").unwrap();
        connection.execute_batch("PRAGMA foreign_keys=ON; CREATE TABLE synthetic_commit_guard(account_ref TEXT REFERENCES sync_account_metadata(account_ref) DEFERRABLE INITIALLY DEFERRED); CREATE TRIGGER reject_new_login AFTER INSERT ON sync_intents BEGIN INSERT INTO synthetic_commit_guard VALUES('synthetic-missing-reference'); END;").unwrap();
        let mut rejected = original.clone();
        rejected.user_id = "synthetic-rejected-account".into();
        assert!(persist_authenticated_session(&mut connection, &credentials, &rejected).is_err());
        assert_eq!(credentials.read(SESSION_SERVICE, "active").unwrap(), before);
        assert_eq!(connection.query_row("SELECT COUNT(*) FROM desktop_cloud_account_state", [], |row| row.get::<_, i64>(0)).unwrap(), 1);
    }


    #[test]
    fn unreadable_login_metadata_does_not_become_a_new_account() {
        let mut connection = database();
        let credentials = Mem(RefCell::new(BTreeMap::new()));
        let original = Session {
            user_id: "synthetic-original-account".into(),
            email: "original@example.invalid".into(),
            display_name: None,
            avatar_url: None,
            access_token: "synthetic-original-access".into(),
            refresh_token: "synthetic-original-refresh".into(),
            expires_at_epoch_seconds: 9_999_999_999,
        };
        persist_authenticated_session(&mut connection, &credentials, &original).unwrap();
        let before = credentials.read(SESSION_SERVICE, "active").unwrap();
        connection.execute_batch("UPDATE sync_account_metadata SET state='SYNTHETIC_INVALID_STATE'").unwrap();
        let mut rejected = original.clone();
        rejected.display_name = Some("synthetic-rejected-name".into());
        assert!(persist_authenticated_session(&mut connection, &credentials, &rejected).is_err());
        assert_eq!(credentials.read(SESSION_SERVICE, "active").unwrap(), before);
        assert_eq!(connection.query_row("SELECT COUNT(*) FROM desktop_cloud_account_state", [], |row| row.get::<_, i64>(0)).unwrap(), 1);
    }


    #[test]
    fn rejected_login_metadata_write_rolls_back_both_sql_owners() {
        let mut connection = database();
        let credentials = Mem(RefCell::new(BTreeMap::new()));
        let original = Session {
            user_id: "synthetic-original-account".into(),
            email: "original@example.invalid".into(),
            display_name: None,
            avatar_url: None,
            access_token: "synthetic-original-access".into(),
            refresh_token: "synthetic-original-refresh".into(),
            expires_at_epoch_seconds: 9_999_999_999,
        };
        persist_authenticated_session(&mut connection, &credentials, &original).unwrap();
        let before = credentials.read(SESSION_SERVICE, "active").unwrap();
        connection.execute_batch("CREATE TRIGGER reject_new_login BEFORE INSERT ON sync_account_metadata BEGIN SELECT RAISE(ABORT,'synthetic SQL rejection'); END;").unwrap();
        let mut rejected = original.clone();
        rejected.user_id = "synthetic-rejected-account".into();
        assert!(persist_authenticated_session(&mut connection, &credentials, &rejected).is_err());
        assert_eq!(credentials.read(SESSION_SERVICE, "active").unwrap(), before);
        assert_eq!(connection.query_row("SELECT COUNT(*) FROM desktop_cloud_account_state", [], |row| row.get::<_, i64>(0)).unwrap(), 1);
    }


    #[cfg(windows)]
    #[test]
    fn rejected_login_credential_save_preserves_both_sql_owners_and_old_session() {
        use std::os::windows::fs::OpenOptionsExt;
        use windows::Win32::Storage::FileSystem::FILE_SHARE_READ;
        let root = tempfile::tempdir().unwrap();
        let credentials = AppPrivateAccountCredentialStore::at(root.path());
        let mut connection = database();
        let original = Session {
            user_id: "synthetic-credential-pair".into(), email: "original@example.invalid".into(),
            display_name: None, avatar_url: None, access_token: "synthetic-old-access".into(),
            refresh_token: "synthetic-old-refresh".into(), expires_at_epoch_seconds: 9_999_999_999,
        };
        persist_authenticated_session(&mut connection, &credentials, &original).unwrap();
        let before = connection.query_row("SELECT revision,email,display_name FROM desktop_cloud_account_state",[],|r|Ok((r.get::<_,i64>(0)?,r.get::<_,String>(1)?,r.get::<_,Option<String>>(2)?))).unwrap();
        let intents = connection.query_row("SELECT COUNT(*) FROM sync_intents",[],|r|r.get::<_,i64>(0)).unwrap();
        let busy = fs::OpenOptions::new().read(true).share_mode(FILE_SHARE_READ.0).open(credentials.document_path()).unwrap();
        let mut rejected = original.clone();
        rejected.email = "rejected@example.invalid".into();
        rejected.display_name = Some("synthetic rejected profile".into());
        assert!(persist_authenticated_session(&mut connection, &credentials, &rejected).is_err());
        drop(busy);
        assert_eq!(read_session(&credentials).unwrap().unwrap().email, original.email);
        assert_eq!(connection.query_row("SELECT revision,email,display_name FROM desktop_cloud_account_state",[],|r|Ok((r.get::<_,i64>(0)?,r.get::<_,String>(1)?,r.get::<_,Option<String>>(2)?))).unwrap(), before);
        assert_eq!(connection.query_row("SELECT COUNT(*) FROM sync_intents",[],|r|r.get::<_,i64>(0)).unwrap(), intents);
    }

    #[test]
    fn same_conversation_id_uses_distinct_chat_and_work_cloud_documents() {
        let c = database();
        let chat_id = selected_document_id(&c, "account", "workspace", "same-id").unwrap();
        assert_eq!(
            chat_id,
            desktop_data_area::document_id(crate::DesktopDataArea::Chat, "same-id")
        );
        c.execute_batch("CREATE TABLE desktop_data_area_v1(id INTEGER PRIMARY KEY,area TEXT NOT NULL,split_done INTEGER NOT NULL); INSERT INTO desktop_data_area_v1 VALUES(1,'WORK',1);").unwrap();
        let work_id = selected_document_id(&c, "account", "workspace", "same-id").unwrap();
        assert_eq!(
            work_id,
            desktop_data_area::document_id(crate::DesktopDataArea::Work, "same-id")
        );
        assert_ne!(chat_id, work_id);
        assert_eq!(cloud_list_document_id(&c).unwrap(), WORK_CLOUD_LIST_DOCUMENT_ID);
        c.execute(
            "INSERT INTO desktop_selected_conversation_sync(account_ref,workspace_id,conversation_id,document_id,remote_revision,payload_hash,local_content_hash,last_synced_at_ms) VALUES('account','workspace','same-id',?1,1,'payload','local',1)",
            [&chat_id],
        )
        .unwrap();
        assert_eq!(
            selected_document_id(&c, "account", "workspace", "same-id").unwrap(),
            chat_id,
            "an existing selected receipt keeps its original remote identity"
        );
    }

    #[test]
    fn restored_document_receipt_and_success_feedback_use_one_durable_commit() {
        unsafe extern "C" fn count_commit(context:*mut std::ffi::c_void)->std::ffi::c_int {
            let count=unsafe{&*(context as *const std::cell::Cell<usize>)};
            count.set(count.get()+1);0
        }
        let commits=std::cell::Cell::new(0usize);
        let mut connection=database();desktop_data_area::migrate(&connection).unwrap();
        connection.execute("UPDATE desktop_data_area_v1 SET area='CHAT'",[]).unwrap();
        let credentials=Mem(RefCell::new(BTreeMap::new()));
        persist_authenticated_session(&mut connection,&credentials,&Session{user_id:"synthetic-one-commit".into(),email:"one-commit@example.invalid".into(),display_name:None,avatar_url:None,
            access_token:"synthetic-access".into(),refresh_token:"synthetic-refresh".into(),expires_at_epoch_seconds:9_999_999_999}).unwrap();
        let vectors:Value=serde_json::from_str(include_str!("../../../protocol/fixtures/data-area-sync-v1.json")).unwrap();
        let vector=vectors.as_array().unwrap().iter().find(|v|v["area"]=="CHAT").unwrap();
        let envelope=sync_v1::seal_direct(json!({"format":"nfai.sync.payload","protocolVersion":1,"schemaVersion":1,"appId":APP_ID,"documentId":vector["documentId"],"revision":1,
            "records":[{"kind":"conversation","id":vector["conversationId"],"revision":1,"classification":"NORMAL","content":vector["content"]}]})).unwrap();
        let parsed:Value=serde_json::from_str(&envelope).unwrap();
        unsafe{rusqlite::ffi::sqlite3_commit_hook(connection.handle(),Some(count_commit),(&commits as *const std::cell::Cell<usize>).cast_mut().cast());}
        let result=restore_remote_envelope(&mut connection,&credentials,RemoteEnvelope{revision:1,payload_hash:parsed["payloadHash"].as_str().unwrap().into(),envelope});
        unsafe{rusqlite::ffi::sqlite3_commit_hook(connection.handle(),None,std::ptr::null_mut());}
        assert!(result.is_ok());
        assert_eq!(connection.query_row("SELECT COUNT(*) FROM desktop_selected_conversation_sync",[],|r|r.get::<_,i64>(0)).unwrap(),1);
        assert_eq!(connection.query_row("SELECT COUNT(*) FROM desktop_sync_diagnostics WHERE event='CLOUD_RESTORE' AND outcome='SUCCESS'",[],|r|r.get::<_,i64>(0)).unwrap(),1);
        assert_eq!(connection.query_row("SELECT COUNT(*) FROM desktop_sync_notifications WHERE kind='SUCCESS'",[],|r|r.get::<_,i64>(0)).unwrap(),1);
        assert_eq!(commits.get(),1,"one recovered document must not flush separate success-feedback transactions");
    }

    #[test]
    fn direct_restore_uses_one_fresh_bound_session_read_for_both_new_and_existing_workspaces() {
        struct CountPrivate { mem: Mem, reads: std::cell::Cell<usize> }
        impl CredentialStore for CountPrivate {
            fn read_optional(&self,s:&str,a:&str)->Result<Option<Vec<u8>>,String>{
                if s==SESSION_SERVICE && a=="active" { self.reads.set(self.reads.get()+1); }
                self.mem.read_optional(s,a)
            }
            fn read(&self,s:&str,a:&str)->Result<Vec<u8>,String>{self.read_optional(s,a)?.ok_or_else(||"missing".into())}
            fn save(&self,s:&str,a:&str,v:&[u8])->Result<(),String>{self.mem.save(s,a,v)}
            fn delete(&self,s:&str,a:&str)->Result<(),String>{self.mem.delete(s,a)}
        }
        let private=CountPrivate{mem:Mem(RefCell::new(BTreeMap::new())),reads:std::cell::Cell::new(0)};
        let mut connection=database();desktop_data_area::migrate(&connection).unwrap();
        connection.execute("UPDATE desktop_data_area_v1 SET area='CHAT'",[]).unwrap();
        let session=Session{user_id:"synthetic-restore-read-budget".into(),email:"restore-read-budget@example.invalid".into(),display_name:None,avatar_url:None,
            access_token:"synthetic-access".into(),refresh_token:"synthetic-refresh".into(),expires_at_epoch_seconds:9_999_999_999};
        persist_authenticated_session(&mut connection,&private,&session).unwrap();
        let bound=BoundAccountCredentialStore::new(private,Arc::new(Mutex::new(1)),1,&session).unwrap();
        let vectors:Value=serde_json::from_str(include_str!("../../../protocol/fixtures/data-area-sync-v1.json")).unwrap();
        let vector=vectors.as_array().unwrap().iter().find(|v|v["area"]=="CHAT").unwrap();
        let envelope=sync_v1::seal_direct(json!({"format":"nfai.sync.payload","protocolVersion":1,"schemaVersion":1,"appId":APP_ID,"documentId":vector["documentId"],"revision":1,
            "records":[{"kind":"conversation","id":vector["conversationId"],"revision":1,"classification":"NORMAL","content":vector["content"]}]})).unwrap();
        let parsed:Value=serde_json::from_str(&envelope).unwrap();
        for expected_status in ["RESTORED_AS_NEW_WORKSPACE","ALREADY_LOCAL"] {
            bound.inner.reads.set(0);
            let receipt=restore_remote_envelope(&mut connection,&bound,RemoteEnvelope{revision:1,payload_hash:parsed["payloadHash"].as_str().unwrap().into(),envelope:envelope.clone()}).unwrap();
            assert_eq!(receipt.status,expected_status);
            assert_eq!(bound.inner.reads.get(),1,"bound restore must validate the latest session once at the durable write guard");
            println!("direct_restore status={expected_status} latest_session_reads={}",bound.inner.reads.get());
            assert_eq!(connection.query_row("SELECT count(*) FROM desktop_selected_conversation_sync",[],|row|row.get::<_,i64>(0)).unwrap(),1);
            let mut metadata=session.clone();metadata.display_name=Some("changed synthetic display name".into());
            save_session(&bound.inner.mem,&metadata).unwrap();
        }
        struct Many(Vec<RemoteEnvelope>);
        impl CloudGateway for Many {
            fn list(&self)->Result<Vec<RemoteEnvelope>,CloudFailure>{Ok(self.0.clone())}
            fn read(&self,_:&str)->Result<Option<RemoteEnvelope>,CloudFailure>{panic!("no per-document reads")}
            fn commit(&self,_:&str,_:u64,_:&str)->Result<(u64,String),CloudFailure>{panic!("no uploads")}
        }
        let many=|prefix:&str|Many((0..3).map(|i|{
            let id=format!("{prefix}-{i}");let document=desktop_data_area::document_id(crate::DesktopDataArea::Chat,&id);
            let encoded=sync_v1::seal_direct(json!({"format":"nfai.sync.payload","protocolVersion":1,"schemaVersion":1,"appId":APP_ID,"documentId":document,"revision":1,
                "records":[{"kind":"conversation","id":id,"revision":1,"classification":"NORMAL","content":vector["content"]}]})).unwrap();
            let parsed:Value=serde_json::from_str(&encoded).unwrap();RemoteEnvelope{revision:1,payload_hash:parsed["payloadHash"].as_str().unwrap().into(),envelope:encoded}
        }).collect());
        bound.inner.reads.set(0);
        let all=restore_all_remote_conversations(&mut connection,&bound,&many("synthetic-whole-valid")).unwrap();
        assert_eq!(all.restored.len(),3);assert_eq!(all.failed_count,0);
        assert_eq!(bound.inner.reads.get(),4,"one atomic group guard plus initial/post-network/final-cleanup reads");
        println!("direct_inventory documents=3 latest_private_session_reads={}",bound.inner.reads.get());
        struct ChangeAfterFirst<'a>{bound:&'a BoundAccountCredentialStore<CountPrivate>,hints:std::cell::Cell<usize>}
        impl CredentialStore for ChangeAfterFirst<'_> {
            fn prepared_account_write_user_id(&self)->Option<&str>{
                self.hints.set(self.hints.get()+1);if self.hints.get()==3 {*self.bound.generation.lock().unwrap()+=1;}
                self.bound.prepared_account_write_user_id()
            }
            fn with_account_write<T>(&self,write:impl FnOnce()->Result<T,String>)->Result<T,String>{self.bound.with_account_write(write)}
            fn read_optional(&self,s:&str,a:&str)->Result<Option<Vec<u8>>,String>{self.bound.read_optional(s,a)}
            fn read(&self,s:&str,a:&str)->Result<Vec<u8>,String>{self.bound.read(s,a)}
            fn save(&self,s:&str,a:&str,v:&[u8])->Result<(),String>{self.bound.save(s,a,v)}
            fn delete(&self,s:&str,a:&str)->Result<(),String>{self.bound.delete(s,a)}
        }
        let actor=ChangeAfterFirst{bound:&bound,hints:std::cell::Cell::new(0)};
        assert!(restore_all_remote_conversations(&mut connection,&actor,&many("synthetic-whole-late")).is_err());
        assert_eq!(actor.hints.get(),6,"preparation hints never authorize a write; the group guard rejects invalidation");
        assert_eq!(connection.query_row("SELECT count(*) FROM desktop_selected_conversation_sync WHERE conversation_id LIKE 'synthetic-whole-late-%'",[],|r|r.get::<_,i64>(0)).unwrap(),0);
        assert_eq!(connection.query_row("SELECT count(*) FROM desktop_selected_conversation_sync WHERE conversation_id LIKE 'synthetic-whole-valid-%'",[],|r|r.get::<_,i64>(0)).unwrap(),3,"account invalidation must not prune the original selected subset");
        assert!(bound.inner.prepared_account_write_user_id().is_none(),"unbound stores never supply identity without their original fresh read");
        bound.inner.reads.set(0);
        assert_eq!(restore_remote_envelope(&mut connection,&bound.inner,RemoteEnvelope{revision:1,payload_hash:parsed["payloadHash"].as_str().unwrap().into(),envelope:envelope.clone()}).unwrap().status,"ALREADY_LOCAL");
        assert_eq!(bound.inner.reads.get(),1);
        bound.inner.mem.delete(SESSION_SERVICE,"active").unwrap();bound.inner.reads.set(0);
        let before=connection.total_changes();
        assert!(restore_remote_envelope(&mut connection,&bound.inner,RemoteEnvelope{revision:1,payload_hash:parsed["payloadHash"].as_str().unwrap().into(),envelope}).is_err());
        assert_eq!(bound.inner.reads.get(),1);assert_eq!(connection.total_changes(),before);
    }

    #[test]
    fn prepared_direct_restore_identity_never_bypasses_latest_write_guard() {
        struct Private { mem: Mem, reads: std::cell::Cell<usize>, fail: std::cell::Cell<bool> }
        impl CredentialStore for Private {
            fn read_optional(&self,s:&str,a:&str)->Result<Option<Vec<u8>>,String>{
                if s==SESSION_SERVICE && a=="active" { self.reads.set(self.reads.get()+1); if self.fail.get(){return Err("synthetic unreadable session".into());} }
                self.mem.read_optional(s,a)
            }
            fn read(&self,s:&str,a:&str)->Result<Vec<u8>,String>{self.read_optional(s,a)?.ok_or_else(||"missing".into())}
            fn save(&self,s:&str,a:&str,v:&[u8])->Result<(),String>{self.mem.save(s,a,v)}
            fn delete(&self,s:&str,a:&str)->Result<(),String>{self.mem.delete(s,a)}
        }
        struct ChangeAfterHint<'a> { bound: &'a BoundAccountCredentialStore<Private>, original: &'a Session, fault: &'a str }
        impl CredentialStore for ChangeAfterHint<'_> {
            fn prepared_account_write_user_id(&self)->Option<&str>{
                let hint=self.bound.prepared_account_write_user_id();
                match self.fault {
                    "generation"=>*self.bound.generation.lock().unwrap()+=1,
                    "missing"=>self.bound.inner.mem.delete(SESSION_SERVICE,"active").unwrap(),
                    "corrupt"=>self.bound.inner.mem.save(SESSION_SERVICE,"active",b"not-json").unwrap(),
                    "unreadable"=>self.bound.inner.fail.set(true),
                    kind=>{ let mut changed=self.original.clone();match kind {
                        "user"=>changed.user_id="synthetic-replacement-user".into(),
                        "access"=>changed.access_token="synthetic-replacement-access".into(),
                        "refresh"=>changed.refresh_token="synthetic-replacement-refresh".into(),
                        _=>panic!("unexpected synthetic fault"),
                    };save_session(&self.bound.inner.mem,&changed).unwrap(); },
                };hint
            }
            fn with_account_write<T>(&self,write:impl FnOnce()->Result<T,String>)->Result<T,String>{self.bound.with_account_write(write)}
            fn read_optional(&self,s:&str,a:&str)->Result<Option<Vec<u8>>,String>{self.bound.read_optional(s,a)}
            fn read(&self,s:&str,a:&str)->Result<Vec<u8>,String>{self.bound.read(s,a)}
            fn save(&self,s:&str,a:&str,v:&[u8])->Result<(),String>{self.bound.save(s,a,v)}
            fn delete(&self,s:&str,a:&str)->Result<(),String>{self.bound.delete(s,a)}
        }
        let vectors:Value=serde_json::from_str(include_str!("../../../protocol/fixtures/data-area-sync-v1.json")).unwrap();
        let vector=vectors.as_array().unwrap().iter().find(|v|v["area"]=="CHAT").unwrap();
        let envelope=sync_v1::seal_direct(json!({"format":"nfai.sync.payload","protocolVersion":1,"schemaVersion":1,"appId":APP_ID,"documentId":vector["documentId"],"revision":1,
            "records":[{"kind":"conversation","id":vector["conversationId"],"revision":1,"classification":"NORMAL","content":vector["content"]}]})).unwrap();
        let parsed:Value=serde_json::from_str(&envelope).unwrap();
        for existing in [false,true] {
            for fault in ["generation","user","access","refresh","missing","corrupt","unreadable"] {
                let mut connection=database();desktop_data_area::migrate(&connection).unwrap();
                connection.execute("UPDATE desktop_data_area_v1 SET area='CHAT'",[]).unwrap();
                let private=Private{mem:Mem(RefCell::new(BTreeMap::new())),reads:std::cell::Cell::new(0),fail:std::cell::Cell::new(false)};
                let session=Session{user_id:"synthetic-restore-guard".into(),email:"restore-guard@example.invalid".into(),display_name:None,avatar_url:None,
                    access_token:"synthetic-access".into(),refresh_token:"synthetic-refresh".into(),expires_at_epoch_seconds:9_999_999_999};
                persist_authenticated_session(&mut connection,&private,&session).unwrap();
                let bound=BoundAccountCredentialStore::new(private,Arc::new(Mutex::new(1)),1,&session).unwrap();
                let remote=||RemoteEnvelope{revision:1,payload_hash:parsed["payloadHash"].as_str().unwrap().into(),envelope:envelope.clone()};
                if existing { restore_remote_envelope(&mut connection,&bound,remote()).unwrap(); }
                bound.inner.reads.set(0);let before=connection.total_changes();
                assert!(restore_remote_envelope(&mut connection,&ChangeAfterHint{bound:&bound,original:&session,fault},remote()).is_err(),"{fault}, existing={existing}");
                assert_eq!(bound.inner.reads.get(),1,"latest private record is checked even after obtaining a preparatory identity");
                assert_eq!(connection.total_changes(),before,"failed latest guard must precede every SQL write");
                assert_eq!(connection.query_row("SELECT count(*) FROM desktop_selected_conversation_sync",[],|row|row.get::<_,i64>(0)).unwrap(),if existing {1}else{0});
            }
        }
    }

    #[test]
    fn bound_session_reads_validate_current_bytes_once_without_caching() {
        struct CountPrivate { mem: Mem, reads: std::cell::Cell<usize>, fail: std::cell::Cell<bool> }
        impl CredentialStore for CountPrivate {
            fn read_optional(&self,s:&str,a:&str)->Result<Option<Vec<u8>>,String>{
                self.reads.set(self.reads.get()+1);
                if self.fail.get(){return Err("synthetic private read failure".into());}
                self.mem.read_optional(s,a)
            }
            fn read(&self,s:&str,a:&str)->Result<Vec<u8>,String>{self.read_optional(s,a)?.ok_or_else(||"missing".into())}
            fn save(&self,s:&str,a:&str,v:&[u8])->Result<(),String>{self.mem.save(s,a,v)}
            fn delete(&self,s:&str,a:&str)->Result<(),String>{self.mem.delete(s,a)}
        }
        let store=CountPrivate{mem:Mem(RefCell::new(BTreeMap::new())),reads:std::cell::Cell::new(0),fail:std::cell::Cell::new(false)};
        let session=Session{user_id:"synthetic-single-read".into(),email:"single-read@example.invalid".into(),display_name:None,avatar_url:None,
            access_token:"synthetic-access".into(),refresh_token:"synthetic-refresh".into(),expires_at_epoch_seconds:9_999_999_999};
        save_session(&store,&session).unwrap();
        let generation=Arc::new(Mutex::new(1));
        let bound=BoundAccountCredentialStore::new(store,generation.clone(),1,&session).unwrap();
        bound.inner.reads.set(0);
        assert_eq!(read_session(&bound).unwrap().unwrap().user_id,session.user_id);
        assert_eq!(bound.inner.reads.get(),1,"one requested session must not reopen the same private record for its guard");
        let mut current=session.clone();current.display_name=Some("new synthetic display name".into());
        let raw=format!(" \n{}\n ",serde_json::to_string(&current).unwrap()).into_bytes();
        bound.inner.mem.save(SESSION_SERVICE,"active",&raw).unwrap();
        assert_eq!(bound.read_optional(SESSION_SERVICE,"active").unwrap(),Some(raw));
        current.access_token="synthetic-replacement-access".into();save_session(&bound.inner.mem,&current).unwrap();
        assert!(bound.read_optional(SESSION_SERVICE,"active").is_err(),"same generation with a replacement token must refuse");
        save_session(&bound.inner.mem,&session).unwrap();*generation.lock().unwrap()=2;
        assert!(bound.read_optional(SESSION_SERVICE,"active").is_err(),"changed generation must refuse");
        *generation.lock().unwrap()=1;bound.inner.mem.delete(SESSION_SERVICE,"active").unwrap();
        assert!(bound.read_optional(SESSION_SERVICE,"active").is_err(),"missing session must refuse");
        bound.inner.mem.save(SESSION_SERVICE,"active",b"not-json").unwrap();
        assert!(bound.read_optional(SESSION_SERVICE,"active").is_err(),"corrupt session must refuse");
        save_session(&bound.inner.mem,&session).unwrap();bound.inner.fail.set(true);
        assert!(bound.read_optional(SESSION_SERVICE,"active").is_err(),"unreadable session must refuse");
        bound.inner.fail.set(false);assert!(read_session(&bound).unwrap().is_some());
    }

    #[test]
    fn same_millisecond_restore_intents_persist_every_revision() {
        let mut connection=database();
        for revision in 1..=3 {
            let transaction=connection.transaction().unwrap();
            write_cloud_restore_intent(&transaction,"synthetic-clock-account",revision,"2026-10-02T00:00:00Z",123).unwrap();
            transaction.commit().unwrap();
        }
        let mut statement=connection.prepare("SELECT expected_revision,resulting_revision FROM sync_intents WHERE account_ref='synthetic-clock-account' ORDER BY resulting_revision").unwrap();
        let rows=statement.query_map([],|r|Ok((r.get::<_,i64>(0)?,r.get::<_,i64>(1)?))).unwrap().collect::<Result<Vec<_>,_>>().unwrap();
        assert_eq!(rows,vec![(1,2),(2,3),(3,4)]);
    }

    #[test]
    #[ignore = "Manual synthetic cost profile; requires an explicitly marked profile root"]
    fn profile_valid_inventory_private_reads_and_durable_sql() {
        struct Many(Vec<RemoteEnvelope>);
        impl CloudGateway for Many {
            fn read(&self, _: &str) -> Result<Option<RemoteEnvelope>, CloudFailure> { panic!("inventory profile must not read documents individually") }
            fn commit(&self, _: &str, _: u64, _: &str) -> Result<(u64,String),CloudFailure> { panic!("inventory profile must not upload") }
            fn list(&self) -> Result<Vec<RemoteEnvelope>,CloudFailure> { Ok(self.0.clone()) }
        }
        // A manual control disables only the non-secret preparatory hint while
        // retaining the exact same bound store, fresh guards, SQL and credentials.
        struct NoIdentityHint<'a,C:CredentialStore>(&'a BoundAccountCredentialStore<C>);
        impl<C:CredentialStore> CredentialStore for NoIdentityHint<'_,C> {
            fn with_account_write<T>(&self,write:impl FnOnce()->Result<T,String>)->Result<T,String>{self.0.with_account_write(write)}
            fn read_optional(&self,s:&str,a:&str)->Result<Option<Vec<u8>>,String>{self.0.read_optional(s,a)}
            fn read(&self,s:&str,a:&str)->Result<Vec<u8>,String>{self.0.read(s,a)}
            fn save(&self,s:&str,a:&str,v:&[u8])->Result<(),String>{self.0.save(s,a,v)}
            fn delete(&self,s:&str,a:&str)->Result<(),String>{self.0.delete(s,a)}
        }
        let root = PathBuf::from(std::env::var_os("NANFENG_AI_SYNTHETIC_PROFILE_ROOT").expect("explicit synthetic profile root required")).canonicalize().unwrap();
        assert!(root.starts_with(Path::new("D:/CodexTemp").canonicalize().unwrap()));
        assert!(root.file_name().unwrap().to_str().unwrap().starts_with("nanfeng-ai-windows-acceptance."));
        let marker: Value = serde_json::from_slice(&fs::read(root.join(".nanfeng-windows-acceptance.json")).unwrap()).unwrap();
        assert_eq!(marker["identifier"],"com.nanzhufeng.ai.desktop.windowsacceptance");
        assert_eq!(marker["profileOnly"],true);
        let source = PathBuf::from(marker["syntheticCredentialSource"].as_str().unwrap()).canonicalize().unwrap();
        assert!(source.starts_with(Path::new("D:/CodexTemp").canonicalize().unwrap()));
        let source_marker: Value = serde_json::from_slice(&fs::read(source.join(".nanfeng-windows-acceptance.json")).unwrap()).unwrap();
        assert_eq!(source_marker["identifier"],marker["identifier"]);
        struct ProfilePrivate { inner:AppPrivateAccountCredentialStore, session_reads:std::cell::Cell<usize> }
        impl CredentialStore for ProfilePrivate {
            fn read_optional(&self,s:&str,a:&str)->Result<Option<Vec<u8>>,String>{
                if s==SESSION_SERVICE && a=="active" {self.session_reads.set(self.session_reads.get()+1);}
                self.inner.read_optional(s,a)
            }
            fn read(&self,s:&str,a:&str)->Result<Vec<u8>,String>{self.inner.read(s,a)}
            fn save(&self,s:&str,a:&str,v:&[u8])->Result<(),String>{self.inner.save(s,a,v)}
            fn delete(&self,s:&str,a:&str)->Result<(),String>{self.inner.delete(s,a)}
        }
        let private = ProfilePrivate{inner:AppPrivateAccountCredentialStore::at(&source),session_reads:std::cell::Cell::new(0)};
        let session = read_session(&private).unwrap().unwrap();
        assert!(session.user_id == "00000000-0000-4000-8000-000000000118" && session.email == "cloud-atomic@example.invalid", "only the synthetic fixture account is allowed");
        assert!(session.access_token == "synthetic-cloud-atomic-access", "only synthetic credentials are allowed");
        let bound = BoundAccountCredentialStore::new(private,Arc::new(Mutex::new(1)),1,&session).unwrap();
        let mem = Mem(RefCell::new(BTreeMap::new()));
        save_session(&mem,&session).unwrap();
        let vectors: Value = serde_json::from_str(include_str!("../../../protocol/fixtures/data-area-sync-v1.json")).unwrap();
        let vector = vectors.as_array().unwrap().iter().find(|v|v["platform"]=="ANDROID" && v["area"]=="CHAT").unwrap();
        let new_count=std::env::var("NANFENG_AI_SYNTHETIC_PROFILE_NEW_COUNT").map(|value|value.parse::<usize>().unwrap()).unwrap_or(64);
        assert!(matches!(new_count,64|2048));
        let mut documents: Vec<RemoteEnvelope> = (0..new_count).map(|i| {
            let id = format!("synthetic-profile-valid-{i}");
            let document = desktop_data_area::document_id(crate::DesktopDataArea::Chat,&id);
            let mut content=vector["content"].clone();
            content["title"]=json!(format!("SYNTHETIC_PROFILE_{i}"));
            content["nodes"][0]["text"]=json!([format!("SYNTHETIC_PROFILE_BODY_{i}")]);
            let envelope=sync_v1::seal_direct(json!({"format":"nfai.sync.payload","protocolVersion":1,"schemaVersion":1,"appId":APP_ID,"documentId":document,"revision":1,
                "records":[{"kind":"conversation","id":id,"revision":1,"classification":"NORMAL","content":content}]})).unwrap();
            let parsed:Value=serde_json::from_str(&envelope).unwrap();
            RemoteEnvelope{revision:1,payload_hash:parsed["payloadHash"].as_str().unwrap().into(),envelope}
        }).collect();
        let originals: Vec<Value> = serde_json::from_slice(&fs::read(source.join("synthetic-valid-cloud-documents.json")).unwrap()).unwrap();
        assert_eq!(originals.len(),13);
        documents.extend(originals.into_iter().map(|value|RemoteEnvelope{
            revision:value["revision"].as_u64().unwrap(),payload_hash:value["payloadHash"].as_str().unwrap().into(),envelope:serde_json::to_string(&value).unwrap(),
        }));
        let gateway=Many(documents);

        // Only this opt-in marked-root test installs tracing. Never expand SQL
        // parameters or emit SQL text; aggregate public owner labels and hashes.
        #[derive(Default)]
        struct SqlCosts { rows:BTreeMap<String,(u64,u64,u64)>, callback_failed:bool }
        unsafe extern "C" fn record_sql_cost(kind:u32,context:*mut std::ffi::c_void,
            statement:*mut std::ffi::c_void,nanos:*mut std::ffi::c_void)->i32 {
            if kind!=rusqlite::ffi::SQLITE_TRACE_PROFILE || context.is_null() || statement.is_null() {return 0;}
            let costs=&mut *(context as *mut SqlCosts);
            let observed=std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
                let statement=statement as *mut rusqlite::ffi::sqlite3_stmt;
                let sql=rusqlite::ffi::sqlite3_sql(statement);
                if sql.is_null(){return;}
                let bytes=std::ffi::CStr::from_ptr(sql).to_bytes();
                let text=String::from_utf8_lossy(bytes).to_ascii_lowercase();
                let verb=match text.split_whitespace().next().unwrap_or("") {
                    "select"=>"SELECT","insert"=>"INSERT","delete"=>"DELETE","update"=>"UPDATE",
                    "pragma"=>"PRAGMA","begin"=>"BEGIN","commit"=>"COMMIT","savepoint"=>"SAVEPOINT",
                    "release"=>"RELEASE","rollback"=>"ROLLBACK",_=>"OTHER",
                };
                let owner=["desktop_sync_diagnostics","desktop_sync_notifications","sync_intents",
                    "nfai_restore_occurrence_","workspace_exchange","desktop_selected_conversation_sync",
                    "sync_account_metadata","desktop_cloud_account_state","workspaces"]
                    .into_iter().find(|name|text.contains(name)).unwrap_or("other");
                let label=format!("{verb}:{owner}:{}",sha256(bytes));
                let steps=rusqlite::ffi::sqlite3_stmt_status(statement,rusqlite::ffi::SQLITE_STMTSTATUS_VM_STEP,1);
                let duration=if nanos.is_null(){0}else{*(nanos as *const u64)};
                let entry=costs.rows.entry(label).or_default();entry.0+=1;entry.1+=steps.max(0) as u64;entry.2+=duration;
            }));
            if observed.is_err(){costs.callback_failed=true;}
            0
        }
        struct TraceRegistration(*mut rusqlite::ffi::sqlite3);
        impl Drop for TraceRegistration {
            fn drop(&mut self){unsafe{rusqlite::ffi::sqlite3_trace_v2(self.0,0,None,std::ptr::null_mut());}}
        }
        fn measure<C:CredentialStore>(mut connection:Connection,credentials:&C,gateway:&Many,label:&str,private_reads:Option<&std::cell::Cell<usize>>)->Value {
            connection.pragma_update(None,"foreign_keys","ON").unwrap();
            connection.pragma_update(None,"journal_mode","WAL").unwrap();
            connection.pragma_update(None,"synchronous","FULL").unwrap();
            // Exact candidate SQL, no business writes: quantify statement compilation separately.
            let candidate_preparation=with_local_occurrence_locator(&mut connection,|connection| {
                let tx=connection.transaction().unwrap();let sql=local_occurrence_candidates_sql(&tx)?;
                let mut rows=Vec::new();
                for cached in [false,true] {
                    let started=std::time::Instant::now();let mut total=0usize;
                    for _ in 0..512 {
                        let values=if cached {
                            tx.prepare_cached(sql).unwrap().query_map(params!["synthetic-absent-target","synthetic-absent-conversation","synthetic-absent-account"],|r|r.get::<_,String>(0)).unwrap().collect::<Result<Vec<_>,_>>().unwrap()
                        } else {
                            tx.prepare(sql).unwrap().query_map(params!["synthetic-absent-target","synthetic-absent-conversation","synthetic-absent-account"],|r|r.get::<_,String>(0)).unwrap().collect::<Result<Vec<_>,_>>().unwrap()
                        };total+=values.len();
                    }
                    assert_eq!(total,0);rows.push(json!({"cached":cached,"calls":512,"totalMs":started.elapsed().as_secs_f64()*1000.0,"resultCount":total}));
                }
                tx.rollback().unwrap();Ok(rows)
            }).unwrap();
            let initial:i64=connection.query_row("SELECT COUNT(*) FROM desktop_selected_conversation_sync",[],|r|r.get(0)).unwrap();
            let started=std::time::Instant::now();
            for _ in 0..64 { assert!(read_session(credentials).unwrap().is_some()); }
            let reads=started.elapsed().as_secs_f64()*1000.0;
            let started=std::time::Instant::now();
            for _ in 0..64 { credentials.with_account_write(||Ok(())).unwrap(); }
            let guard=started.elapsed().as_secs_f64()*1000.0;
            let private_before=private_reads.map(std::cell::Cell::get);
            let mut sql_costs=Box::new(SqlCosts::default());
            let handle=unsafe{connection.handle()};
            let trace_result=unsafe{rusqlite::ffi::sqlite3_trace_v2(handle,rusqlite::ffi::SQLITE_TRACE_PROFILE,
                Some(record_sql_cost),sql_costs.as_mut() as *mut SqlCosts as *mut std::ffi::c_void)};
            let trace=TraceRegistration(handle);assert_eq!(trace_result,rusqlite::ffi::SQLITE_OK);
            let phases=RestorePhaseCapture::begin();
            let started=std::time::Instant::now();
            let receipt=restore_all_remote_conversations(&mut connection,credentials,gateway);
            let restore=started.elapsed().as_secs_f64()*1000.0;
            let phase_costs=phases.finish();
            drop(trace);assert!(!sql_costs.callback_failed);
            let receipt=receipt.unwrap();
            let restore_private_reads=private_before.map(|before|private_reads.unwrap().get()-before);
            assert_eq!(receipt.failed_count,0);assert_eq!(receipt.restored.len(),gateway.0.len());
            let selected:i64=connection.query_row("SELECT COUNT(*) FROM desktop_selected_conversation_sync",[],|r|r.get(0)).unwrap();
            assert_eq!(selected,initial+(gateway.0.len()-13) as i64);
            for item in receipt.restored {
                let count:i64=connection.query_row("SELECT COUNT(*) FROM workspace_exchange WHERE workspace_id=?1",[item.workspace_id],|r|r.get(0)).unwrap();
                assert_eq!(count,1);
            }
            let integrity:String=connection.query_row("PRAGMA integrity_check",[],|r|r.get(0)).unwrap();assert_eq!(integrity,"ok");
            assert!(connection.prepare("PRAGMA foreign_key_check").unwrap().query([]).unwrap().next().unwrap().is_none());
            json!({"label":label,"newCount":gateway.0.len()-13,"existingCount":13,"phaseCostsInclusiveCallsNs":phase_costs,"candidateStatementPreparationControl":candidate_preparation,"sessionRead64Ms":reads,"accountGuard64Ms":guard,"restore77Ms":restore,"restoreActiveSessionReads":restore_private_reads,"selectedBefore":initial,"selectedAfter":selected,"integrityOk":true,"foreignKeys":0,
                "sqlProfileScope":"marked synthetic restore only; unexpanded statement hash/owner/count/VM/approx ns; hooks removed before validation; phase costs inclusive, nonadditive",
                "sqlCosts":sql_costs.rows})
        }
        let sql=fs::read_to_string(root.join("profile-snapshot.sql")).unwrap();
        let memory=||{let c=Connection::open_in_memory().unwrap();c.execute_batch("PRAGMA foreign_keys=OFF;").unwrap();c.execute_batch(&sql).unwrap();c};
        let after=std::env::var("NANFENG_AI_SYNTHETIC_PROFILE_PHASE").unwrap_or_default();
        let (mem_file,bound_file,result_file)=match after.as_str(){
            ""=>("mem-file.sqlite3","bound-file.sqlite3","profile-cost-results.json"),
            "after-read-reuse"=>("after-mem-file.sqlite3","after-bound-file.sqlite3","profile-cost-results-after-read-reuse.json"),
            "after-single-commit"=>("final-mem-file.sqlite3","final-bound-file.sqlite3","profile-cost-results-after-single-commit.json"),
            "after-guard-identity-no-hint"=>("identity-control-mem.sqlite3","identity-control-bound.sqlite3","profile-cost-results-identity-no-hint.json"),
            "after-guard-identity"=>("identity-mem.sqlite3","identity-bound.sqlite3","profile-cost-results-identity.json"),
            "after-guard-inventory-no-hint"=>("inventory-control-mem.sqlite3","inventory-control-bound.sqlite3","profile-cost-results-inventory-no-hint.json"),
            "after-guard-inventory"=>("inventory-mem.sqlite3","inventory-bound.sqlite3","profile-cost-results-inventory.json"),
            _=>panic!("unknown synthetic profile phase"),
        };
        assert!(!root.join(result_file).exists(),"preserve prior profile receipts before any profile writes");
        let no_hint=NoIdentityHint(&bound);
        let control=after=="after-guard-identity-no-hint" || after=="after-guard-inventory-no-hint";
        let bound_only=marker["boundOnly"]==true;
        let results=if bound_only { vec![measure(Connection::open(root.join(bound_file)).unwrap(),&bound,&gateway,"bound-private-file-wal-full",Some(&bound.inner.session_reads))] } else {
        let bound_memory=if control {measure(memory(),&no_hint,&gateway,"bound-private-memory-sql",Some(&bound.inner.session_reads))} else {measure(memory(),&bound,&gateway,"bound-private-memory-sql",Some(&bound.inner.session_reads))};
        let results=vec![measure(memory(),&mem,&gateway,"mem-credentials-memory-sql",None),
            bound_memory,
            measure(Connection::open(root.join(mem_file)).unwrap(),&mem,&gateway,"mem-credentials-file-wal-full",None),
            if control {measure(Connection::open(root.join(bound_file)).unwrap(),&no_hint,&gateway,"bound-private-file-wal-full",Some(&bound.inner.session_reads))} else {measure(Connection::open(root.join(bound_file)).unwrap(),&bound,&gateway,"bound-private-file-wal-full",Some(&bound.inner.session_reads))}];
        results };
        fs::write(root.join(result_file),serde_json::to_vec_pretty(&results).unwrap()).unwrap();
        println!("{}",serde_json::to_string(&results).unwrap());
    }


    struct SyntheticRestoreInventory(Vec<RemoteEnvelope>);
    impl CloudGateway for SyntheticRestoreInventory {
        fn list(&self)->Result<Vec<RemoteEnvelope>,CloudFailure>{Ok(self.0.clone())}
        fn read(&self,_:&str)->Result<Option<RemoteEnvelope>,CloudFailure>{panic!("no per-document reads")}
        fn commit(&self,_:&str,_:u64,_:&str)->Result<(u64,String),CloudFailure>{panic!("restore does not upload")}
    }
    fn synthetic_restore_inventory(prefix:&str,count:usize)->SyntheticRestoreInventory {
        synthetic_restore_inventory_in_area(prefix,count,crate::DesktopDataArea::Chat)
    }
    fn synthetic_restore_inventory_in_area(prefix:&str,count:usize,area:crate::DesktopDataArea)->SyntheticRestoreInventory {
        let vectors:Value=serde_json::from_str(include_str!("../../../protocol/fixtures/data-area-sync-v1.json")).unwrap();
        let vector=vectors.as_array().unwrap().iter().find(|v|v["area"]==area.wire()).unwrap();
        SyntheticRestoreInventory((0..count).map(|i| {
            let id=format!("{prefix}-{i}");
            let document_id=desktop_data_area::document_id(area,&id);
            let envelope=sync_v1::seal_direct(json!({"format":"nfai.sync.payload","protocolVersion":1,
                "schemaVersion":1,"appId":APP_ID,"documentId":document_id,"revision":1,
                "records":[{"kind":"conversation","id":id,"revision":1,"classification":"NORMAL","content":vector["content"]}]})).unwrap();
            let parsed:Value=serde_json::from_str(&envelope).unwrap();
            RemoteEnvelope{revision:1,payload_hash:parsed["payloadHash"].as_str().unwrap().into(),envelope}
        }).collect())
    }
    fn synthetic_bound_restore()->(Connection,BoundAccountCredentialStore<Mem>) {
        let mut connection=database();desktop_data_area::migrate(&connection).unwrap();
        connection.execute("UPDATE desktop_data_area_v1 SET area='CHAT'",[]).unwrap();
        let credentials=Mem(RefCell::new(BTreeMap::new()));
        let session=Session{user_id:"synthetic-restore-group".into(),email:"restore-group@example.invalid".into(),
            display_name:None,avatar_url:None,access_token:"synthetic-access".into(),refresh_token:"synthetic-refresh".into(),
            expires_at_epoch_seconds:9_999_999_999};
        persist_authenticated_session(&mut connection,&credentials,&session).unwrap();
        let bound=BoundAccountCredentialStore::new(credentials,Arc::new(Mutex::new(1)),1,&session).unwrap();
        (connection,bound)
    }
    fn restore_group_counts(c:&Connection)->Vec<i64> {
        ["workspaces","workspace_exchange","desktop_selected_conversation_sync","sync_intents",
            "desktop_sync_diagnostics","desktop_sync_notifications"]
            .iter().map(|table|c.query_row(&format!("SELECT count(*) FROM {table}"),[],|r|r.get(0)).unwrap()).collect()
    }
    struct CancellationEpochCloud(RefCell<Option<RemoteEnvelope>>);
    impl CloudGateway for CancellationEpochCloud {
        fn read(&self,_:&str)->Result<Option<RemoteEnvelope>,CloudFailure>{Ok(self.0.borrow().clone())}
        fn commit(&self,_:&str,_:u64,_:&str)->Result<(u64,String),CloudFailure>{panic!("cancel never uploads")}
        fn delete(&self,_:&str,revision:u64)->Result<bool,CloudFailure>{
            assert_eq!(self.0.borrow().as_ref().map(|d|d.revision),Some(revision));
            self.0.borrow_mut().take();Ok(true)
        }
    }
    #[test]
    fn cancellation_epoch_rejects_old_single_and_group_writers_without_changing_local_body() {
        let(mut connection,credentials)=synthetic_bound_restore();
        let remote=synthetic_restore_inventory("synthetic-cancel-epoch",1).0.remove(0);
        let seed=restore_remote_envelope(&mut connection,&credentials,remote.clone()).unwrap();
        let mut prepared=prepare_remote_conversation_restore(&connection,&credentials,&remote).unwrap();
        prepared.cancel_epoch=Some(cancellation_epoch(&connection,&prepared.account,&prepared.document_id).unwrap());
        let account=prepared.account.clone();let document=prepared.document_id.clone();
        let body:String=connection.query_row("SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?1",[&seed.workspace_id],|r|r.get(0)).unwrap();
        let cloud=CancellationEpochCloud(RefCell::new(Some(remote)));
        assert_eq!(cancel_remote_conversation(&mut connection,&credentials,&cloud,&seed.workspace_id,&seed.conversation_id).unwrap().status,"CANCELLED");
        assert!(restore_prepared_conversation(&mut connection,&credentials,&prepared).unwrap_err().contains("SYNC_CANCELLED_DURING_READ"));
        let results=restore_prepared_conversation_group(&mut connection,&credentials,&[prepared]).unwrap();
        assert!(results[0].as_ref().unwrap_err().contains("SYNC_CANCELLED_DURING_READ"));
        assert_eq!(cancellation_epoch(&connection,&account,&document).unwrap(),Some(1));
        assert_eq!(connection.query_row("SELECT COUNT(*) FROM desktop_selected_conversation_sync",[],|r|r.get::<_,i64>(0)).unwrap(),0);
        assert_eq!(connection.query_row("SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?1",[&seed.workspace_id],|r|r.get::<_,String>(0)).unwrap(),body);
    }
    #[test]
    fn cancellation_epoch_advances_on_repeated_cancel_and_does_not_turn_into_aba() {
        let(mut connection,credentials)=synthetic_bound_restore();
        let remote=synthetic_restore_inventory("synthetic-cancel-aba",1).0.remove(0);
        let seed=restore_remote_envelope(&mut connection,&credentials,remote.clone()).unwrap();
        let cloud=CancellationEpochCloud(RefCell::new(Some(remote.clone())));
        cancel_remote_conversation(&mut connection,&credentials,&cloud,&seed.workspace_id,&seed.conversation_id).unwrap();
        let mut prepared=prepare_remote_conversation_restore(&connection,&credentials,&remote).unwrap();
        prepared.cancel_epoch=Some(cancellation_epoch(&connection,&prepared.account,&prepared.document_id).unwrap());
        // No remote document remains. Explicit retry is idempotent remotely,
        // but still invalidates a read that started after the first cancel.
        cancel_remote_conversation(&mut connection,&credentials,&cloud,&seed.workspace_id,&seed.conversation_id).unwrap();
        assert_eq!(cancellation_epoch(&connection,&prepared.account,&prepared.document_id).unwrap(),Some(2));
        assert!(restore_prepared_conversation(&mut connection,&credentials,&prepared).unwrap_err().contains("SYNC_CANCELLED_DURING_READ"));
        assert_eq!(connection.query_row("SELECT COUNT(*) FROM desktop_sync_jobs WHERE stage='CANCELLED'",[],|r|r.get::<_,i64>(0)).unwrap(),1);
    }
    #[test]
    fn cancellation_epoch_failure_rolls_back_local_selection_and_allows_explicit_retry() {
        let(mut connection,credentials)=synthetic_bound_restore();
        let remote=synthetic_restore_inventory("synthetic-cancel-atomic",1).0.remove(0);
        let seed=restore_remote_envelope(&mut connection,&credentials,remote.clone()).unwrap();
        let cloud=CancellationEpochCloud(RefCell::new(Some(remote)));
        connection.execute_batch("CREATE TRIGGER synthetic_cancel_epoch_refusal BEFORE INSERT ON desktop_sync_jobs WHEN NEW.stage='CANCELLED' BEGIN SELECT RAISE(ABORT,'synthetic cancellation receipt refusal'); END;").unwrap();
        assert!(cancel_remote_conversation(&mut connection,&credentials,&cloud,&seed.workspace_id,&seed.conversation_id).is_err());
        assert_eq!(connection.query_row("SELECT COUNT(*) FROM desktop_selected_conversation_sync",[],|r|r.get::<_,i64>(0)).unwrap(),1);
        assert_eq!(connection.query_row("SELECT COUNT(*) FROM desktop_sync_jobs WHERE stage='CANCELLED'",[],|r|r.get::<_,i64>(0)).unwrap(),0);
        connection.execute_batch("DROP TRIGGER synthetic_cancel_epoch_refusal").unwrap();
        cancel_remote_conversation(&mut connection,&credentials,&cloud,&seed.workspace_id,&seed.conversation_id).unwrap();
        assert_eq!(connection.query_row("SELECT COUNT(*) FROM desktop_selected_conversation_sync",[],|r|r.get::<_,i64>(0)).unwrap(),0);
    }
    #[test]
    fn inventory_public_header_reuse_preserves_integrity_and_unknown_identity_cleanup_guard() {
        // A recognizable header is never integrity proof or write authority.
        // Malformed/unidentifiable inventory also cannot authorize missing cleanup.
        for case in ["hash", "app", "revision", "version", "unknown", "duplicate", "escaped-duplicate", "syntax"] {
            let (mut connection,bound)=synthetic_bound_restore();
            let original=synthetic_restore_inventory("synthetic-header-existing",1);
            let seeded=restore_all_remote_conversations(&mut connection,&bound,&original).unwrap();
            let workspace=&seeded.restored[0].workspace_id;
            let before:String=connection.query_row("SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?1",[workspace],|r|r.get(0)).unwrap();
            let mut bad=original.0[0].clone();
            let mut root:Value=serde_json::from_str(&bad.envelope).unwrap();
            match case {
                "hash"=>root["payloadHash"]=json!("0".repeat(64)),
                "app"=>root["appId"]=json!("synthetic.foreign.app"),
                "revision"=>root["revision"]=json!(2),
                "version"=>root["protocolVersion"]=json!(2),
                "unknown"=>root["unknownSyntheticField"]=json!(true),
                _=>{},
            }
            bad.envelope=serde_json::to_string(&root).unwrap();
            if case=="duplicate" {bad.envelope=bad.envelope.replacen("\"schemaVersion\":1","\"schemaVersion\":1,\"schemaVersion\":1",1);}
            if case=="escaped-duplicate" {bad.envelope=bad.envelope.replacen("\"schemaVersion\":1","\"schemaVersion\":1,\"\\u0073chemaVersion\":1",1);}
            if case=="syntax" {bad.envelope.pop();}
            let mut inventory=synthetic_restore_inventory("synthetic-header-good",1);
            inventory.0.insert(0,bad);
            let receipt=restore_all_remote_conversations(&mut connection,&bound,&inventory).unwrap();
            assert_eq!(receipt.failed_count,1,"{case}");
            assert_eq!(receipt.restored.len(),1,"{case}");
            assert_eq!(connection.query_row("SELECT count(*) FROM desktop_selected_conversation_sync",[],|r|r.get::<_,i64>(0)).unwrap(),2,"{case}");
            assert_eq!(connection.query_row("SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?1",[workspace],|r|r.get::<_,String>(0)).unwrap(),before,"{case}");
            assert_eq!(connection.query_row("SELECT count(*) FROM pragma_foreign_key_check",[],|r|r.get::<_,i64>(0)).unwrap(),0,"{case}");
            assert!(connection.is_autocommit());
        }
    }
    #[test]
    fn invalid_legacy_public_identity_keeps_original_migration_failure_classification() {
        let (mut connection,bound)=synthetic_bound_restore();
        let inventory=SyntheticRestoreInventory(vec![RemoteEnvelope {
            revision:1,payload_hash:"synthetic-unusable-hash".into(),
            envelope:json!({"format":sync_v1::ENVELOPE,"documentId":"synthetic-invalid-document"}).to_string(),
        }]);
        let receipt=restore_all_remote_conversations(&mut connection,&bound,&inventory).unwrap();
        assert_eq!(receipt.failed_count,1);
        assert!(receipt.restored.is_empty());
        assert_eq!(connection.query_row("SELECT count(*) FROM desktop_sync_diagnostics WHERE event='CLOUD_DOCUMENT_MIGRATION' AND outcome='FAILED' AND safe_code='LEGACY_MIGRATION_PENDING'",[],|r|r.get::<_,i64>(0)).unwrap(),1);
        assert_eq!(connection.query_row("SELECT count(*) FROM desktop_selected_conversation_sync",[],|r|r.get::<_,i64>(0)).unwrap(),0);
    }
    #[test]
    fn prepared_restore_group_does_not_commit_a_callers_transaction_or_publish_rolled_back_feedback() {
        let (mut connection,bound)=synthetic_bound_restore();
        let before=restore_group_counts(&connection);
        let prepared=synthetic_restore_inventory("synthetic-caller-rollback",2).0.iter()
            .map(|document|prepare_remote_conversation_restore(&connection,&bound,document).unwrap()).collect::<Vec<_>>();
        assert_eq!(restore_group_counts(&connection),before,"preparation has no business writes");
        bound.with_account_write(|| {
            let tx=connection.transaction_with_behavior(TransactionBehavior::Immediate).unwrap();
            let current_area=desktop_data_area::read_area(&tx).unwrap();
            for item in &prepared { apply_prepared_conversation_restore(&tx,item,current_area,true).unwrap(); }
            assert!(!tx.is_autocommit(),"the caller owns the only transaction");
            assert_eq!(tx.query_row("SELECT count(*) FROM desktop_selected_conversation_sync",[],|r|r.get::<_,i64>(0)).unwrap(),2);
            tx.rollback().unwrap();Ok(())
        }).unwrap();
        assert_eq!(restore_group_counts(&connection),before,"body, receipt, intent, feedback all roll back together");
    }
    #[test]
    fn bound_restore_group_isolates_body_and_receipt_refusal_but_stops_on_transaction_rollback() {
        for stage in ["body","receipt","rollback"] {
            let (mut connection,bound)=synthetic_bound_restore();
            let before=restore_group_counts(&connection);
            let table=if stage=="body" {"workspace_exchange"} else {"desktop_selected_conversation_sync"};
            let condition=if stage=="body" {"json_extract(NEW.exchange_json,'$.conversations[0].id')"} else {"NEW.conversation_id"};
            let action=if stage=="rollback" {"ROLLBACK"} else {"ABORT"};
            connection.execute_batch(&format!("CREATE TRIGGER synthetic_reject_group BEFORE INSERT ON {table} WHEN {condition}='synthetic-savepoint-1' BEGIN SELECT RAISE({action},'synthetic item refusal'); END;")).unwrap();
            let result=restore_all_remote_conversations(&mut connection,&bound,&synthetic_restore_inventory("synthetic-savepoint",3));
            if stage=="rollback" {
                assert!(result.is_err(),"unexpected whole-transaction rollback is terminal, not retried");
                assert_eq!(restore_group_counts(&connection),before);continue;
            }
            let receipt=result.unwrap();assert_eq!(receipt.restored.len(),2);assert_eq!(receipt.failed_count,1);
            assert_eq!(receipt.restored.iter().map(|r|r.conversation_id.as_str()).collect::<Vec<_>>(),vec!["synthetic-savepoint-0","synthetic-savepoint-2"]);
            assert_eq!(connection.query_row("SELECT count(*) FROM desktop_selected_conversation_sync WHERE conversation_id='synthetic-savepoint-1'",[],|r|r.get::<_,i64>(0)).unwrap(),0);
            assert_eq!(connection.query_row("SELECT count(*) FROM desktop_sync_diagnostics WHERE event='CLOUD_RESTORE' AND outcome='SUCCESS'",[],|r|r.get::<_,i64>(0)).unwrap(),2);
            assert_eq!(connection.query_row("SELECT count(*) FROM desktop_sync_notifications WHERE kind='SUCCESS'",[],|r|r.get::<_,i64>(0)).unwrap(),2);
            assert_eq!(connection.query_row("SELECT count(*) FROM workspaces",[],|r|r.get::<_,i64>(0)).unwrap(),before[0]+2);
        }
    }


    #[test]
    fn restore_group_prepared_area_hint_cannot_authorize_a_changed_or_missing_writer_area() {
        for change in ["WORK","MISSING"] {
            let (mut connection,bound)=synthetic_bound_restore();
            let prepared=synthetic_restore_inventory("synthetic-area-hint",2).0.iter().map(|document|
                prepare_remote_conversation_restore_in_area(&connection,&bound,document,Some(crate::DesktopDataArea::Chat)).unwrap()).collect::<Vec<_>>();
            if change=="WORK" {connection.execute("UPDATE desktop_data_area_v1 SET area='WORK'",[]).unwrap();}
            else {connection.execute("DELETE FROM desktop_data_area_v1",[]).unwrap();}
            let before=restore_group_counts(&connection);
            assert!(restore_prepared_conversation_group(&mut connection,&bound,&prepared).is_err(),"{change}");
            assert_eq!(restore_group_counts(&connection),before,"prepared area is not a writer authorization");
            assert!(connection.is_autocommit());
        }
    }
    #[test]
    fn restore_group_captured_area_keeps_mixed_inventory_in_its_actual_chat_or_work_owner() {
        for area in [crate::DesktopDataArea::Chat,crate::DesktopDataArea::Work] {
            let (mut connection,bound)=synthetic_bound_restore();
            connection.execute("UPDATE desktop_data_area_v1 SET area=?1",[area.wire()]).unwrap();
            let mut inventory=synthetic_restore_inventory_in_area("synthetic-mixed-chat",2,crate::DesktopDataArea::Chat);
            inventory.0.extend(synthetic_restore_inventory_in_area("synthetic-mixed-work",2,crate::DesktopDataArea::Work).0);
            let receipt=restore_all_remote_conversations(&mut connection,&bound,&inventory).unwrap();
            assert_eq!(receipt.restored.len(),2);assert_eq!(receipt.failed_count,0);
            let prefix=if area==crate::DesktopDataArea::Chat {"synthetic-mixed-chat-"} else {"synthetic-mixed-work-"};
            assert!(receipt.restored.iter().all(|r|r.conversation_id.starts_with(prefix)));
            assert_eq!(connection.query_row("SELECT count(*) FROM desktop_selected_conversation_sync",[],|r|r.get::<_,i64>(0)).unwrap(),2);
            assert_eq!(connection.query_row("SELECT count(*) FROM workspaces",[],|r|r.get::<_,i64>(0)).unwrap(),2);
        }
    }

    #[test]
    fn restore_group_feedback_transaction_abort_never_writes_success_outside_the_rolled_back_transaction() {
        for (table,verb) in [("desktop_sync_diagnostics","INSERT"),("desktop_sync_diagnostics","DELETE"),
            ("desktop_sync_notifications","INSERT"),("desktop_sync_notifications","DELETE")] {
            let (mut connection,bound)=synthetic_bound_restore();
            // Exercise DELETE itself, not a no-op retention query. This fixture
            // models old feedback left over after a best-effort cleanup refusal.
            if verb=="DELETE" {
                if table=="desktop_sync_diagnostics" {
                    for i in 0..100 {connection.execute("INSERT INTO desktop_sync_diagnostics(id,event,outcome,occurred_at_ms) VALUES(?1,'SYNTHETIC_OLD','SUCCESS',?2)",params![format!("synthetic-rollback-diag-{i}"),i]).unwrap();}
                } else {
                    for i in 0..51 {connection.execute("INSERT INTO desktop_sync_notifications(id,kind,message,occurred_at_ms,unread) VALUES(?1,'SUCCESS','SYNTHETIC_OLD',?2,1)",params![format!("synthetic-rollback-note-{i}"),i]).unwrap();}
                }
            }
            let before=restore_group_counts(&connection);
            connection.execute_batch(&format!("CREATE TRIGGER synthetic_abort_feedback BEFORE {verb} ON {table} BEGIN SELECT RAISE(ROLLBACK,'synthetic feedback transaction abort'); END;")).unwrap();
            let result=restore_all_remote_conversations(&mut connection,&bound,&synthetic_restore_inventory("synthetic-feedback-abort",2));
            assert!(result.is_err(),"a lost transaction must stop the inventory");
            assert_eq!(restore_group_counts(&connection),before,"{verb}:{table}: success feedback cannot escape a rolled-back body/receipt");
            assert_eq!(connection.query_row("SELECT count(*) FROM sqlite_temp_master WHERE name LIKE 'nfai_restore_occurrence_%'",[],|r|r.get::<_,i64>(0)).unwrap(),0);
            assert!(connection.is_autocommit());
        }
    }
    #[test]
    fn restore_group_feedback_retention_keeps_committed_body_and_recent_bounded_feedback() {
        let (mut connection,bound)=synthetic_bound_restore();
        for i in 0..100 {
            connection.execute("INSERT INTO desktop_sync_diagnostics(id,event,outcome,occurred_at_ms) VALUES(?1,'SYNTHETIC_OLD','SUCCESS',?2)",params![format!("synthetic-old-diag-{i}"),i]).unwrap();
        }
        for i in 0..50 {
            connection.execute("INSERT INTO desktop_sync_notifications(id,kind,message,occurred_at_ms,unread) VALUES(?1,'SUCCESS','SYNTHETIC_OLD',?2,1)",params![format!("synthetic-old-note-{i}"),i]).unwrap();
        }
        let receipt=restore_all_remote_conversations(&mut connection,&bound,&synthetic_restore_inventory("synthetic-feedback-retention",96)).unwrap();
        assert_eq!(receipt.restored.len(),96);assert_eq!(receipt.failed_count,0);
        assert_eq!(connection.query_row("SELECT count(*) FROM desktop_selected_conversation_sync",[],|r|r.get::<_,i64>(0)).unwrap(),96);
        assert_eq!(connection.query_row("SELECT count(*) FROM workspaces",[],|r|r.get::<_,i64>(0)).unwrap(),96);
        assert_eq!(connection.query_row("SELECT count(*) FROM desktop_sync_diagnostics",[],|r|r.get::<_,i64>(0)).unwrap(),100);
        assert_eq!(connection.query_row("SELECT count(*) FROM desktop_sync_notifications",[],|r|r.get::<_,i64>(0)).unwrap(),50);
        assert_eq!(connection.query_row("SELECT count(*) FROM desktop_sync_diagnostics WHERE event='CLOUD_RESTORE'",[],|r|r.get::<_,i64>(0)).unwrap(),96);
        assert_eq!(connection.query_row("SELECT count(*) FROM desktop_sync_notifications WHERE message='SYNTHETIC_OLD'",[],|r|r.get::<_,i64>(0)).unwrap(),0);
    }

    #[test]
    fn bound_restore_group_deferred_commit_constraint_rolls_back_then_isolates_only_the_bad_item() {
        let (mut connection,bound)=synthetic_bound_restore();
        connection.execute_batch("PRAGMA foreign_keys=ON; CREATE TABLE synthetic_group_parent(id INTEGER PRIMARY KEY); CREATE TABLE synthetic_group_child(id INTEGER REFERENCES synthetic_group_parent(id) DEFERRABLE INITIALLY DEFERRED); CREATE TRIGGER synthetic_group_commit_reject AFTER INSERT ON desktop_selected_conversation_sync WHEN NEW.conversation_id='synthetic-deferred-1' BEGIN INSERT INTO synthetic_group_child VALUES(1); END;").unwrap();
        let receipt=restore_all_remote_conversations(&mut connection,&bound,&synthetic_restore_inventory("synthetic-deferred",3)).unwrap();
        assert_eq!(receipt.restored.len(),2);assert_eq!(receipt.failed_count,1);
        assert_eq!(connection.query_row("SELECT count(*) FROM desktop_selected_conversation_sync",[],|r|r.get::<_,i64>(0)).unwrap(),2);
        assert_eq!(connection.query_row("SELECT count(*) FROM synthetic_group_child",[],|r|r.get::<_,i64>(0)).unwrap(),0);
        assert_eq!(connection.query_row("SELECT count(*) FROM pragma_foreign_key_check",[],|r|r.get::<_,i64>(0)).unwrap(),0);
        assert_eq!(connection.query_row("SELECT count(*) FROM desktop_sync_diagnostics WHERE event='CLOUD_RESTORE' AND outcome='SUCCESS'",[],|r|r.get::<_,i64>(0)).unwrap(),2);
        assert_eq!(connection.query_row("SELECT count(*) FROM desktop_sync_notifications WHERE kind='SUCCESS'",[],|r|r.get::<_,i64>(0)).unwrap(),2);
        assert!(connection.is_autocommit());
    }
    #[test]
    fn bound_restore_group_keeps_only_committed_prefix_after_account_invalidation_and_never_prunes() {
        struct InvalidateAfterCommit<'a>{bound:&'a BoundAccountCredentialStore<Mem>,commits:std::cell::Cell<usize>}
        impl CredentialStore for InvalidateAfterCommit<'_> {
            fn prepared_account_write_user_id(&self)->Option<&str>{self.bound.prepared_account_write_user_id()}
            fn with_account_write<T>(&self,write:impl FnOnce()->Result<T,String>)->Result<T,String>{
                let result=self.bound.with_account_write(write);
                if result.is_ok() {self.commits.set(self.commits.get()+1);*self.bound.generation.lock().unwrap()+=1;}
                result
            }
            fn read_optional(&self,s:&str,a:&str)->Result<Option<Vec<u8>>,String>{self.bound.read_optional(s,a)}
            fn read(&self,s:&str,a:&str)->Result<Vec<u8>,String>{self.bound.read(s,a)}
            fn save(&self,s:&str,a:&str,v:&[u8])->Result<(),String>{self.bound.save(s,a,v)}
            fn delete(&self,s:&str,a:&str)->Result<(),String>{self.bound.delete(s,a)}
        }
        let (mut connection,bound)=synthetic_bound_restore();
        restore_all_remote_conversations(&mut connection,&bound,&synthetic_restore_inventory("synthetic-before-group",1)).unwrap();
        let actor=InvalidateAfterCommit{bound:&bound,commits:std::cell::Cell::new(0)};
        assert!(restore_all_remote_conversations(&mut connection,&actor,&synthetic_restore_inventory("synthetic-committed-prefix",CLOUD_RESTORE_GROUP_DOCUMENTS+1)).is_err());
        assert_eq!(actor.commits.get(),1,"only the first actually committed group may publish");
        assert_eq!(connection.query_row("SELECT count(*) FROM desktop_selected_conversation_sync WHERE conversation_id LIKE 'synthetic-committed-prefix-%'",[],|r|r.get::<_,i64>(0)).unwrap(),CLOUD_RESTORE_GROUP_DOCUMENTS as i64);
        assert_eq!(connection.query_row("SELECT count(*) FROM desktop_selected_conversation_sync WHERE conversation_id='synthetic-before-group-0'",[],|r|r.get::<_,i64>(0)).unwrap(),1,"final missing-document prune must not run after invalidation");
        assert_eq!(connection.query_row("SELECT count(*) FROM desktop_selected_conversation_sync WHERE conversation_id=?1",[format!("synthetic-committed-prefix-{}",CLOUD_RESTORE_GROUP_DOCUMENTS)],|r|r.get::<_,i64>(0)).unwrap(),0);
        assert_eq!(connection.query_row("SELECT count(*) FROM sqlite_temp_master WHERE name LIKE 'nfai_restore_occurrence_%'",[],|r|r.get::<_,i64>(0)).unwrap(),0);
    }

    #[test]
    fn stale_restore_receipt_is_replaced_atomically_or_preserved_on_failure() {
        struct OneDocument(RemoteEnvelope);
        impl CloudGateway for OneDocument {
            fn read(&self, _: &str) -> Result<Option<RemoteEnvelope>, CloudFailure> {
                Ok(Some(self.0.clone()))
            }
            fn commit(&self, _: &str, _: u64, _: &str) -> Result<(u64, String), CloudFailure> {
                panic!("restore must not upload")
            }
            fn list(&self) -> Result<Vec<RemoteEnvelope>, CloudFailure> {
                Ok(vec![self.0.clone()])
            }
        }
        let vectors: Value = serde_json::from_str(include_str!("../../../protocol/fixtures/data-area-sync-v1.json")).unwrap();
        let vector = vectors.as_array().unwrap().iter().find(|v| v["area"] == "CHAT").unwrap();
        let conversation_id = vector["conversationId"].as_str().unwrap();
        let document_id = vector["documentId"].as_str().unwrap();
        let payload = json!({
            "format":"nfai.sync.payload", "protocolVersion":1, "schemaVersion":1,
            "appId":APP_ID, "documentId":document_id, "revision":1,
            "records":[{"kind":"conversation", "id":conversation_id,
                "revision":1, "classification":"NORMAL", "content":vector["content"]}],
        });
        let envelope = sync_v1::seal_direct(payload).unwrap();
        let parsed: Value = serde_json::from_str(&envelope).unwrap();
        let gateway = OneDocument(RemoteEnvelope {
            revision: 1, payload_hash: parsed["payloadHash"].as_str().unwrap().into(), envelope,
        });
        for failure in ["workspace", "receipt", "commit", "none"] {
            let mut connection = database();
            desktop_data_area::migrate(&connection).unwrap();
            connection.execute("UPDATE desktop_data_area_v1 SET area='CHAT'", []).unwrap();
            let credentials = Mem(RefCell::new(BTreeMap::new()));
            let session = Session {user_id:"synthetic-stale-restore".into(), email:"restore@example.invalid".into(),
                display_name:None, avatar_url:None, access_token:"synthetic-access".into(),
                refresh_token:"synthetic-refresh".into(), expires_at_epoch_seconds:9_999_999_999};
            persist_authenticated_session(&mut connection, &credentials, &session).unwrap();
            let account = sync_state_v1::account_ref(&session.user_id).unwrap();
            connection.execute(
                "INSERT INTO desktop_selected_conversation_sync VALUES(?1,'missing-workspace',?2,?3,7,'old-payload','old-content',123)",
                params![account, conversation_id, document_id],
            ).unwrap();
            let snapshot = |c: &Connection| {
                ["desktop_selected_conversation_sync", "workspaces", "workspace_exchange",
                    "desktop_cloud_account_state", "sync_account_metadata", "sync_intents",
                    "desktop_portable_personalization_v1"]
                    .into_iter().map(|table| {
                        let mut statement = c.prepare(&format!("SELECT * FROM {table} ORDER BY 1")).unwrap();
                        let columns = statement.column_count();
                        statement.query_map([], |row| (0..columns).map(|i| row.get::<_, rusqlite::types::Value>(i)).collect::<Result<Vec<_>,_>>())
                            .unwrap().collect::<Result<Vec<_>,_>>().unwrap()
                    }).collect::<Vec<_>>()
            };
            let before = snapshot(&connection);
            match failure {
                "workspace" => connection.execute_batch("CREATE TRIGGER reject_restore BEFORE INSERT ON workspace_exchange BEGIN SELECT RAISE(ABORT,'synthetic workspace rejection'); END;").unwrap(),
                "receipt" => connection.execute_batch("CREATE TRIGGER reject_restore BEFORE INSERT ON desktop_selected_conversation_sync BEGIN SELECT RAISE(ABORT,'synthetic receipt rejection'); END;").unwrap(),
                "commit" => connection.execute_batch("PRAGMA foreign_keys=ON; CREATE TABLE synthetic_restore_parent(id INTEGER PRIMARY KEY); CREATE TABLE synthetic_restore_child(id INTEGER REFERENCES synthetic_restore_parent(id) DEFERRABLE INITIALLY DEFERRED); CREATE TRIGGER reject_restore AFTER INSERT ON desktop_selected_conversation_sync BEGIN INSERT INTO synthetic_restore_child VALUES(1); END;").unwrap(),
                _ => {}
            }
            let result = restore_all_remote_conversations(&mut connection, &credentials, &gateway).unwrap();
            if failure != "none" {
                assert_eq!(result.failed_count, 1, "{failure}");
                assert!(result.restored.is_empty(), "{failure}");
                assert_eq!(snapshot(&connection), before, "failed {failure} must preserve the complete old receipt and business state");
                assert_eq!(connection.query_row("SELECT COUNT(*) FROM desktop_sync_diagnostics WHERE event='CLOUD_RESTORE' AND outcome='SUCCESS'",[],|r|r.get::<_,i64>(0)).unwrap(),0,"{failure} must not publish success diagnostics");
                assert_eq!(connection.query_row("SELECT COUNT(*) FROM desktop_sync_notifications WHERE kind='SUCCESS'",[],|r|r.get::<_,i64>(0)).unwrap(),0,"{failure} must not publish success notifications");
            } else {
                assert_eq!(result.failed_count, 0);
                assert_eq!(result.restored.len(), 1);
                let restored = &result.restored[0];
                let binding: (String, String, i64) = connection.query_row(
                    "SELECT workspace_id,conversation_id,remote_revision FROM desktop_selected_conversation_sync WHERE account_ref=?1 AND document_id=?2",
                    params![account, document_id], |r| Ok((r.get(0)?,r.get(1)?,r.get(2)?)),
                ).unwrap();
                assert_eq!(binding, (restored.workspace_id.clone(), conversation_id.into(), 1));
                let count: i64 = connection.query_row("SELECT COUNT(*) FROM workspace_exchange WHERE workspace_id=?1", [&restored.workspace_id], |r|r.get(0)).unwrap();
                assert_eq!(count, 1);
                assert!(result.cloud_conversation_keys.unwrap().contains(&format!("{}:{}", restored.workspace_id, conversation_id)));
            }
        }
    }

    #[test]
    fn fixed_mac_area_vectors_restore_only_into_the_matching_windows_database() {
        struct OneDocument(RemoteEnvelope);
        impl CloudGateway for OneDocument {
            fn read(&self, _: &str) -> Result<Option<RemoteEnvelope>, CloudFailure> {
                Ok(Some(self.0.clone()))
            }
            fn commit(&self, _: &str, _: u64, _: &str) -> Result<(u64, String), CloudFailure> {
                panic!("fixture restore must not upload")
            }
            fn list(&self) -> Result<Vec<RemoteEnvelope>, CloudFailure> {
                Ok(vec![self.0.clone()])
            }
        }
        let vectors: Value = serde_json::from_str(include_str!("../../../protocol/fixtures/data-area-sync-v1.json")).unwrap();
        for vector in vectors.as_array().unwrap() {
            let source_area = crate::DesktopDataArea::parse(vector["area"].as_str().unwrap()).unwrap();
            let conversation_id = vector["conversationId"].as_str().unwrap();
            let document_id = vector["documentId"].as_str().unwrap();
            assert_eq!(desktop_data_area::document_id(source_area, conversation_id), document_id);
            for target in [crate::DesktopDataArea::Chat, crate::DesktopDataArea::Work] {
                let mut connection = database();
                desktop_data_area::migrate(&connection).unwrap();
                connection.execute("UPDATE desktop_data_area_v1 SET area=?1", [target.wire()]).unwrap();
                let credentials = Mem(RefCell::new(BTreeMap::new()));
                persist_authenticated_session(&mut connection, &credentials, &Session {
                    user_id: "area-fixture".into(), email: "fixture@example.invalid".into(),
                    display_name: None, avatar_url: None, access_token: "a".into(),
                    refresh_token: "r".into(), expires_at_epoch_seconds: 9_999_999_999,
                }).unwrap();
                let payload = json!({
                    "format":"nfai.sync.payload", "protocolVersion":1, "schemaVersion":1,
                    "appId":APP_ID, "documentId":document_id, "revision":1,
                    "records":[{"kind":"conversation", "id":conversation_id,
                        "revision":1, "classification":"NORMAL", "content":vector["content"]}],
                });
                let envelope = sync_v1::seal_direct(payload).unwrap();
                let parsed: Value = serde_json::from_str(&envelope).unwrap();
                let remote = RemoteEnvelope {
                    revision: 1, payload_hash: parsed["payloadHash"].as_str().unwrap().into(), envelope,
                };
                let result = restore_all_remote_conversations(&mut connection, &credentials, &OneDocument(remote)).unwrap();
                assert_eq!(result.failed_count, 0, "{source_area:?} into {target:?}");
                let count: i64 = connection.query_row("SELECT count(*) FROM workspace_exchange", [], |row| row.get(0)).unwrap();
                assert_eq!(count, if target == source_area { 1 } else { 0 }, "{source_area:?} into {target:?}");
            }
        }
    }

    #[test]
    fn bound_cloud_credentials_reject_changed_generation_and_session() {
        let root = tempfile::tempdir().unwrap();
        let credentials = AppPrivateAccountCredentialStore::at(root.path());
        let session = Session {user_id:"synthetic-bound-account".into(),email:"bound@example.invalid".into(),
            display_name:None,avatar_url:None,access_token:"synthetic-access".into(),
            refresh_token:"synthetic-refresh".into(),expires_at_epoch_seconds:9_999_999_999};
        save_session(&credentials,&session).unwrap();
        let generation=Arc::new(Mutex::new(7));
        let bound=BoundAccountCredentialStore::new(credentials.clone(),generation.clone(),7,&session).unwrap();
        assert_eq!(read_session(&bound).unwrap().unwrap().user_id,session.user_id);
        let writes=std::cell::Cell::new(0);
        *generation.lock().unwrap()=9; // Switch away and back, even with identical stored tokens.
        assert!(read_session(&bound).is_err());
        assert!(bound.with_account_write(|| {writes.set(writes.get()+1);Ok(())}).is_err());
        assert_eq!(writes.get(),0);
        assert!(BoundAccountCredentialStore::new(credentials.clone(),generation.clone(),7,&session).is_err());
        let bound=BoundAccountCredentialStore::new(credentials.clone(),generation,9,&session).unwrap();
        let mut replacement=session.clone();replacement.access_token="synthetic-new-token".into();
        save_session(&credentials,&replacement).unwrap();
        assert!(read_session(&bound).is_err());
        assert_eq!(read_session(&credentials).unwrap().unwrap().access_token,replacement.access_token);
    }

    #[test]
    fn selected_sync_rejects_account_switch_during_remote_read() {
        struct SwitchingCloud<'a> { credentials: &'a Mem, writes: std::cell::Cell<u32> }
        impl CloudGateway for SwitchingCloud<'_> {
            fn read(&self, _: &str) -> Result<Option<RemoteEnvelope>, CloudFailure> {
                let mut session = read_session(self.credentials).unwrap().unwrap();
                session.user_id = "synthetic-new-account".into();
                session.access_token = "synthetic-new-access".into();
                save_session(self.credentials, &session).unwrap();
                Ok(None)
            }
            fn commit(&self, _: &str, _: u64, _: &str) -> Result<(u64,String),CloudFailure> {
                self.writes.set(self.writes.get()+1); Err(CloudFailure::Known)
            }
        }
        let mut connection = database();
        let credentials = Mem(RefCell::new(BTreeMap::new()));
        persist_authenticated_session(&mut connection, &credentials, &Session {
            user_id:"synthetic-old-account".into(),email:"old@example.invalid".into(),
            display_name:None,avatar_url:None,access_token:"synthetic-old-access".into(),
            refresh_token:"synthetic-refresh".into(),expires_at_epoch_seconds:9_999_999_999,
        }).unwrap();
        let exchange=json!({"conversations":[{"id":"conversation-safe","title":"safe","revision":1,
            "currentLeafId":"message-safe","messages":[{"id":"message-safe","parentId":null,
            "ordinal":0,"role":"user","delivery":"COMPLETE","createdAt":"2026-09-01T00:00:00Z",
            "revision":1,"blocks":[{"kind":"TEXT","text":"synthetic safe text"}]}]}]});
        connection.execute("INSERT INTO workspaces VALUES('workspace-safe','safe','s','p','2026-09-01T00:00:00Z')",[]).unwrap();
        connection.execute("INSERT INTO workspace_exchange VALUES('workspace-safe',?1)",[exchange.to_string()]).unwrap();
        let gateway=SwitchingCloud{credentials:&credentials,writes:std::cell::Cell::new(0)};
        assert!(sync_selected_conversation(&mut connection,&credentials,&gateway,"workspace-safe","conversation-safe").is_err());
        assert_eq!(gateway.writes.get(),0,"late read must not authorize an old-account commit");
        let jobs:i64=connection.query_row("SELECT count(*) FROM desktop_sync_jobs",[],|r|r.get(0)).unwrap();
        assert_eq!(jobs,0,"late read must not publish a sync task");
    }

    #[test]
    fn selected_sync_never_overwrites_an_unverified_remote_format() {
        struct UnverifiedRemote(RefCell<u32>);
        impl CloudGateway for UnverifiedRemote {
            fn read(&self, _: &str) -> Result<Option<RemoteEnvelope>, CloudFailure> {
                Ok(Some(RemoteEnvelope {revision:1,payload_hash:"synthetic-unverified".into(),envelope:"{}".into()}))
            }
            fn commit(&self, _: &str, _: u64, _: &str) -> Result<(u64,String), CloudFailure> {
                *self.0.borrow_mut() += 1; Err(CloudFailure::Known)
            }
        }
        let mut connection=database();
        let credentials=Mem(RefCell::new(BTreeMap::new()));
        persist_authenticated_session(&mut connection,&credentials,&Session {
            user_id:"synthetic-selected-account".into(),email:"selected@example.invalid".into(),
            display_name:None,avatar_url:None,access_token:"synthetic-access".into(),
            refresh_token:"synthetic-refresh".into(),expires_at_epoch_seconds:9_999_999_999,
        }).unwrap();
        let exchange=json!({"conversations":[{"id":"conversation-safe","title":"safe","revision":1,
            "currentLeafId":"message-safe","messages":[{"id":"message-safe","parentId":null,
            "ordinal":0,"role":"user","delivery":"COMPLETE","createdAt":"2026-09-01T00:00:00Z",
            "revision":1,"blocks":[{"kind":"TEXT","text":"synthetic safe text"}]}]}]});
        connection.execute("INSERT INTO workspaces VALUES('workspace-safe','safe','s','p','2026-09-01T00:00:00Z')",[]).unwrap();
        connection.execute("INSERT INTO workspace_exchange VALUES('workspace-safe',?1)",[exchange.to_string()]).unwrap();
        let gateway=UnverifiedRemote(RefCell::new(0));
        assert!(sync_selected_conversation(&mut connection,&credentials,&gateway,"workspace-safe","conversation-safe").is_err());
        assert_eq!(*gateway.0.borrow(),0,"an unverified remote must never reach CAS write");
    }

    #[test]
    fn cloud_list_reports_invalid_presentation_without_overwriting_local_pins() {
        struct InvalidPresentation(RemoteEnvelope);
        impl CloudGateway for InvalidPresentation {
            fn read(&self, _: &str) -> Result<Option<RemoteEnvelope>, CloudFailure> { panic!("inventory already contains envelope") }
            fn commit(&self, _: &str, _: u64, _: &str) -> Result<(u64, String), CloudFailure> { panic!("read must not write") }
            fn list(&self) -> Result<Vec<RemoteEnvelope>, CloudFailure> { Ok(vec![self.0.clone()]) }
        }
        let mut connection = database();
        let credentials = Mem(RefCell::new(BTreeMap::new()));
        save_session(&credentials, &Session {
            user_id: "synthetic-list-account".into(), email: "list@example.invalid".into(),
            display_name: None, avatar_url: None, access_token: "synthetic-access".into(),
            refresh_token: "synthetic-refresh".into(), expires_at_epoch_seconds: 9_999_999_999,
        }).unwrap();
        let envelope = sync_v1::seal_direct(json!({
            "format":"nfai.sync.payload","protocolVersion":1,"schemaVersion":1,
            "appId":APP_ID,"documentId":CLOUD_LIST_PRESENTATION_DOCUMENT_ID,"revision":1,
            "records":[{"kind":"safe_settings","id":CLOUD_LIST_PRESENTATION_DOCUMENT_ID,
                "revision":1,"classification":"NORMAL","content":{"type":"INVALID_PRESENTATION"}}]
        })).unwrap();
        let parsed: Value = serde_json::from_str(&envelope).unwrap();
        let receipt = restore_all_remote_conversations(&mut connection, &credentials,
            &InvalidPresentation(RemoteEnvelope { revision:1,
                payload_hash:parsed["payloadHash"].as_str().unwrap().into(), envelope })).unwrap();
        assert_eq!(receipt.failed_count, 1);
        assert_eq!(receipt.failure_reasons.get("CLOUD_PRESENTATION_FAILED"), Some(&1));
        assert!(!receipt.cloud_list_presentation_present);
        assert!(receipt.cloud_pinned_conversation_ids.is_empty());
        assert!(receipt.restored.is_empty());
    }

    #[test]
    fn cloud_list_skips_other_area_without_restoring_or_counting_a_failure() {
        struct OneDocument(RemoteEnvelope);
        impl CloudGateway for OneDocument {
            fn read(&self, _: &str) -> Result<Option<RemoteEnvelope>, CloudFailure> {
                panic!("list already has the complete envelope")
            }
            fn commit(&self, _: &str, _: u64, _: &str) -> Result<(u64, String), CloudFailure> {
                panic!("cloud list must not upload")
            }
            fn list(&self) -> Result<Vec<RemoteEnvelope>, CloudFailure> {
                Ok(vec![self.0.clone()])
            }
        }
        let mut chat = database();
        let keys = Mem(RefCell::new(BTreeMap::new()));
        save_session(
            &keys,
            &Session {
                user_id: "area-fixture-account".into(),
                email: "area@example.invalid".into(),
                display_name: None,
                avatar_url: None,
                access_token: "fixture-access".into(),
                refresh_token: "fixture-refresh".into(),
                expires_at_epoch_seconds: 9_999_999_999,
            },
        )
        .unwrap();
        let document_id = desktop_data_area::document_id(crate::DesktopDataArea::Work, "same-id");
        let envelope = sync_v1::seal_direct(json!({
            "format":"nfai.sync.payload","protocolVersion":1,"schemaVersion":1,
            "appId":APP_ID,"documentId":document_id,"revision":1,
            "records":[{"kind":"conversation","id":"same-id","revision":1,
                "classification":"NORMAL","content":{"surface":"WORK"}}]
        }))
        .unwrap();
        let parsed: Value = serde_json::from_str(&envelope).unwrap();
        let remote = RemoteEnvelope {
            revision: 1,
            payload_hash: parsed["payloadHash"].as_str().unwrap().into(),
            envelope,
        };
        assert!(restore_remote_envelope(&mut chat, &keys, remote.clone())
            .unwrap_err()
            .contains("OTHER_DATA_AREA"));
        let receipt = restore_all_remote_conversations(&mut chat, &keys, &OneDocument(remote)).unwrap();
        assert!(receipt.restored.is_empty());
        assert_eq!(receipt.failed_count, 0);
        let count: i64 = chat
            .query_row("SELECT COUNT(*) FROM workspaces", [], |row| row.get(0))
            .unwrap();
        assert_eq!(count, 0);
    }

    #[test]
    fn reading_existing_cloud_workspace_repairs_missing_receipt_without_duplicates() {
        let mut connection = database();
        let credentials = Mem(RefCell::new(BTreeMap::new()));
        let session = Session {
            user_id: "receipt-fixture".into(),
            email: "fixture@example.invalid".into(),
            display_name: None,
            avatar_url: None,
            access_token: "synthetic-access".into(),
            refresh_token: "synthetic-refresh".into(),
            expires_at_epoch_seconds: 9_999_999_999,
        };
        persist_authenticated_session(&mut connection, &credentials, &session).unwrap();
        connection.execute(
            "INSERT INTO workspaces VALUES('source','Fixture','semantic','package','2026-09-01T00:00:00Z')",
            [],
        ).unwrap();
        connection.execute(
            "INSERT INTO workspace_exchange VALUES('source',?1)",
            [json!({"conversations":[{"id":"fixture-conversation","title":"Fixture","revision":1,
                "currentLeafId":"m1","messages":[{"id":"m1","parentId":null,"ordinal":0,
                "role":"user","delivery":"COMPLETE","createdAt":"2026-09-01T00:00:00Z",
                "revision":1,"blocks":[{"kind":"TEXT","text":"Fixture text"}]}]}]}).to_string()],
        ).unwrap();
        let document = desktop_data_area::document_id(crate::DesktopDataArea::Chat, "fixture-conversation");
        let (payload, _) = conversation_payload(&connection, "source", "fixture-conversation", &document, 1).unwrap();
        let envelope = sync_v1::seal_direct(payload).unwrap();
        let parsed: Value = serde_json::from_str(&envelope).unwrap();
        let remote = RemoteEnvelope {
            revision: 1,
            payload_hash: parsed["payloadHash"].as_str().unwrap().into(),
            envelope,
        };
        connection.execute("DELETE FROM workspace_exchange", []).unwrap();
        connection.execute("DELETE FROM workspaces", []).unwrap();
        let first = restore_remote_envelope(&mut connection, &credentials, remote.clone()).unwrap();
        connection.execute("DELETE FROM desktop_selected_conversation_sync", []).unwrap();
        let second = restore_remote_envelope(&mut connection, &credentials, remote.clone()).unwrap();
        assert_eq!(second.workspace_id, first.workspace_id);
        let workspaces: i64 = connection.query_row("SELECT COUNT(*) FROM workspaces", [], |row| row.get(0)).unwrap();
        let receipts: i64 = connection.query_row("SELECT COUNT(*) FROM desktop_selected_conversation_sync", [], |row| row.get(0)).unwrap();
        assert_eq!(workspaces, 1);
        assert_eq!(receipts, 1);
        connection.execute(
            "UPDATE desktop_selected_conversation_sync SET account_ref='foreign-account-fixture'",
            [],
        ).unwrap();
        assert!(restore_remote_envelope(&mut connection, &credentials, remote)
            .unwrap_err().contains("本机恢复状态无法核对"));
        let workspaces: i64 = connection.query_row("SELECT COUNT(*) FROM workspaces", [], |row| row.get(0)).unwrap();
        assert_eq!(workspaces, 1);
    }

    #[test]
    fn work_cloud_pin_writes_only_the_work_presentation_document() {
        struct Cloud(RefCell<Option<RemoteEnvelope>>);
        impl CloudGateway for Cloud {
            fn read(&self, id: &str) -> Result<Option<RemoteEnvelope>, CloudFailure> {
                assert_eq!(id, WORK_CLOUD_LIST_DOCUMENT_ID);
                Ok(self.0.borrow().clone())
            }
            fn commit(
                &self,
                id: &str,
                expected_revision: u64,
                envelope: &str,
            ) -> Result<(u64, String), CloudFailure> {
                assert_eq!(id, WORK_CLOUD_LIST_DOCUMENT_ID);
                assert_eq!(expected_revision, 0);
                let value: Value = serde_json::from_str(envelope).unwrap();
                let revision = value["revision"].as_u64().unwrap();
                let hash = value["payloadHash"].as_str().unwrap().to_owned();
                *self.0.borrow_mut() = Some(RemoteEnvelope {
                    revision,
                    payload_hash: hash.clone(),
                    envelope: envelope.into(),
                });
                Ok((revision, hash))
            }
            fn list(&self) -> Result<Vec<RemoteEnvelope>, CloudFailure> {
                panic!("pin uses the selected document, not account inventory")
            }
        }
        let work = database();
        work.execute_batch("CREATE TABLE desktop_data_area_v1(id INTEGER PRIMARY KEY,area TEXT NOT NULL,split_done INTEGER NOT NULL); INSERT INTO desktop_data_area_v1 VALUES(1,'WORK',1);").unwrap();
        let keys = Mem(RefCell::new(BTreeMap::new()));
        save_session(
            &keys,
            &Session {
                user_id: "work-pin-account".into(),
                email: "work-pin@example.invalid".into(),
                display_name: None,
                avatar_url: None,
                access_token: "fixture-access".into(),
                refresh_token: "fixture-refresh".into(),
                expires_at_epoch_seconds: 9_999_999_999,
            },
        )
        .unwrap();
        let cloud = Cloud(RefCell::new(None));
        assert_eq!(
            set_cloud_list_pinned(&work, &keys, &cloud, "same-id", true).unwrap(),
            vec!["same-id"]
        );
        let document = cloud.0.borrow();
        let envelope: Value = serde_json::from_str(&document.as_ref().unwrap().envelope).unwrap();
        assert_eq!(envelope["documentId"], WORK_CLOUD_LIST_DOCUMENT_ID);
    }
    #[test]
    fn production_credential_scope_separates_active_session_from_opaque_account_keys() {
        let account = "0123456789abcdef0123456789abcdef";
        assert!(valid_credential_scope(SESSION_SERVICE, "active"));
        assert!(valid_credential_scope(DATA_KEY_SERVICE, account));
        assert!(valid_credential_scope(RECOVERY_SERVICE, account));
        assert!(!valid_credential_scope(SESSION_SERVICE, account));
        assert!(!valid_credential_scope(DATA_KEY_SERVICE, "active"));
        assert!(!valid_credential_scope("other.service", account));
    }

    #[test]
    fn direct_account_without_private_material_remains_ready_for_periodic_sync() {
        let c = database();
        let keys = Mem(RefCell::new(BTreeMap::new()));
        let session = Session {
            user_id: "verified-user-reconnect".into(),
            email: "reconnect@example.invalid".into(),
            display_name: None,
            avatar_url: None,
            access_token: "access-token".into(),
            refresh_token: "refresh-token".into(),
            expires_at_epoch_seconds: 9_999_999_999,
        };
        save_session(&keys, &session).unwrap();
        let account = sync_state_v1::account_ref(&session.user_id).unwrap();
        c.execute(
            "INSERT INTO desktop_cloud_account_state VALUES(?1,?2,?3,NULL,NULL,'READY',1,1,?4,1,NULL,NULL,'device-safe',1,1,NULL,NULL,NULL)",
            params![account, session.user_id, session.email, URL_SAFE_NO_PAD.encode([7u8; 16])],
        )
        .unwrap();

        let projection = projection(&c, &keys, true, None).unwrap();
        assert_eq!(projection.state, "READY");
        assert_eq!(projection.recovery_state, "NOT_REQUIRED");
        assert!(projection.periodic_enabled);

        set_periodic_enabled(&c, &keys, true).unwrap();
        let state: String = c
            .query_row(
                "SELECT state FROM desktop_cloud_account_state WHERE account_ref=?1",
                [&account],
                |row| row.get(0),
            )
            .unwrap();
        assert_eq!(state, "READY");
        assert!(periodic_targets(&c, &keys).unwrap().is_empty());
    }

    #[test]
    fn empty_remote_can_restart_recovery_setup_only_without_local_sync_receipts() {
        struct EmptyCloud;
        impl CloudGateway for EmptyCloud {
            fn read(&self, _: &str) -> Result<Option<RemoteEnvelope>, CloudFailure> {
                Ok(None)
            }
            fn commit(&self, _: &str, _: u64, _: &str) -> Result<(u64, String), CloudFailure> {
                Err(CloudFailure::Known)
            }
            fn list(&self) -> Result<Vec<RemoteEnvelope>, CloudFailure> {
                Ok(vec![])
            }
        }
        let mut c = database();
        let keys = Mem(RefCell::new(BTreeMap::new()));
        let session = Session {
            user_id: "verified-user-empty-remote".into(),
            email: "empty@example.invalid".into(),
            display_name: None,
            avatar_url: None,
            access_token: "access-token".into(),
            refresh_token: "refresh-token".into(),
            expires_at_epoch_seconds: 9_999_999_999,
        };
        save_session(&keys, &session).unwrap();
        let account = sync_state_v1::account_ref(&session.user_id).unwrap();
        c.execute(
            "INSERT INTO desktop_cloud_account_state VALUES(?1,?2,?3,NULL,NULL,'READY',1,1,?4,1,NULL,NULL,'device-safe',1,1,NULL,NULL,NULL)",
            params![account, session.user_id, session.email, URL_SAFE_NO_PAD.encode([8u8; 16])],
        )
        .unwrap();
        let mut store = SqliteMetadataStore { connection: &mut c };
        sync_state_v1::authenticate(
            &mut store,
            &keys,
            "empty-remote-auth",
            None,
            "verified-user-empty-remote",
        )
        .unwrap();

        bootstrap_empty_remote_recovery(store.connection, &keys, &EmptyCloud).unwrap();
        let projection = projection(store.connection, &keys, true, None).unwrap();
        assert_eq!(projection.state, "READY");
        assert_eq!(projection.recovery_state, "NOT_REQUIRED");
        let confirmed: i64 = store
            .connection
            .query_row(
                "SELECT recovery_confirmed FROM desktop_cloud_account_state WHERE account_ref=?1",
                [&account],
                |row| row.get(0),
            )
            .unwrap();
        assert_eq!(confirmed, 0);
    }

    #[test]
    fn corrupted_private_session_is_an_error_not_a_signed_out_account() {
        let root = tempfile::tempdir().unwrap();
        let credentials = AppPrivateAccountCredentialStore::at(root.path());
        assert!(read_session(&credentials).unwrap().is_none());
        credentials.save(SESSION_SERVICE, "active", b"synthetic-session").unwrap();
        fs::write(credentials.document_path(), b"{synthetic-corrupt-document").unwrap();
        assert!(read_session(&credentials).is_err());
        assert_eq!(fs::read(credentials.document_path()).unwrap(), b"{synthetic-corrupt-document");
    }

    #[test]
    fn account_sync_credentials_stay_encrypted_in_the_app_private_root() {
        let root = tempfile::tempdir().unwrap();
        let credentials = AppPrivateAccountCredentialStore::at(root.path());
        let session = b"private-session-value";
        credentials
            .save(SESSION_SERVICE, "active", session)
            .unwrap();

        let document = fs::read(credentials.document_path()).unwrap();
        assert!(!document
            .windows(session.len())
            .any(|window| window == session));
        assert_eq!(
            credentials.read(SESSION_SERVICE, "active").unwrap(),
            session
        );
        let updated = b"updated-private-session-value";
        credentials
            .save(SESSION_SERVICE, "active", updated)
            .unwrap();
        assert_eq!(
            credentials.read(SESSION_SERVICE, "active").unwrap(),
            updated
        );
        let document = fs::read(credentials.document_path()).unwrap();
        assert!(!document
            .windows(updated.len())
            .any(|window| window == updated));
        assert!(credentials.key_path().starts_with(root.path()));
    }

    #[cfg(windows)]
    #[test]
    fn account_credential_busy_commit_preserves_the_previous_secret() {
        use std::os::windows::fs::OpenOptionsExt;
        use windows::Win32::Storage::FileSystem::FILE_SHARE_READ;

        let root = tempfile::tempdir().unwrap();
        let credentials = AppPrivateAccountCredentialStore::at(root.path());
        let old = b"account-old-session";
        credentials.save(SESSION_SERVICE, "active", old).unwrap();
        let busy = fs::OpenOptions::new()
            .read(true)
            .share_mode(FILE_SHARE_READ.0)
            .open(credentials.document_path())
            .unwrap();
        assert!(credentials
            .save(SESSION_SERVICE, "active", b"account-new-session")
            .is_err());
        drop(busy);
        assert_eq!(
            credentials.read(SESSION_SERVICE, "active").unwrap(),
            old
        );
    }

    #[test]
    fn recovery_code_is_returned_once_and_only_derived_material_is_stored() {
        let mut c = database();
        let keys = Mem(RefCell::new(BTreeMap::new()));
        let session = Session {
            user_id: "verified-user-123".into(),
            email: "user@example.invalid".into(),
            display_name: None,
            avatar_url: None,
            access_token: "access-secret".into(),
            refresh_token: "refresh-secret".into(),
            expires_at_epoch_seconds: 9999999999,
        };
        save_session(&keys, &session).unwrap();
        let account = sync_state_v1::account_ref(&session.user_id).unwrap();
        c.execute("INSERT INTO desktop_cloud_account_state VALUES(?1,?2,?3,NULL,NULL,'AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION',0,0,NULL,0,NULL,NULL,'device-safe',1,1,NULL,NULL,NULL)",params![account,session.user_id,session.email]).unwrap();
        let mut store = SqliteMetadataStore { connection: &mut c };
        sync_state_v1::authenticate(&mut store, &keys, "auth", None, "verified-user-123").unwrap();
        let (shown, pending) = create_recovery_code(store.connection, &keys).unwrap();
        assert!(shown.recovery_code.starts_with("NF-"));
        confirm_recovery(store.connection, &keys, pending, &shown.confirmation_hash).unwrap();
        assert_eq!(keys.read(DATA_KEY_SERVICE, &account).unwrap().len(), 32);
        let dump = format!("{:?}", keys.0.borrow());
        assert!(!dump.contains(&shown.recovery_code));
        assert!(!dump.contains("access-secret"));
    }

    #[test]
    fn pending_recovery_survives_a_process_restart_until_confirmation() {
        let mut c = database();
        let keys = Mem(RefCell::new(BTreeMap::new()));
        let session = Session {
            user_id: "verified-user-pending-recovery".into(),
            email: "pending@example.invalid".into(),
            display_name: None,
            avatar_url: None,
            access_token: "access-secret".into(),
            refresh_token: "refresh-secret".into(),
            expires_at_epoch_seconds: 9_999_999_999,
        };
        save_session(&keys, &session).unwrap();
        let account = sync_state_v1::account_ref(&session.user_id).unwrap();
        c.execute("INSERT INTO desktop_cloud_account_state VALUES(?1,?2,?3,NULL,NULL,'AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION',0,0,NULL,0,NULL,NULL,'device-safe',1,1,NULL,NULL,NULL)",params![account,session.user_id,session.email]).unwrap();
        let mut store = SqliteMetadataStore { connection: &mut c };
        sync_state_v1::authenticate(&mut store, &keys, "auth", None, &session.user_id).unwrap();
        let (shown, _memory_only_pending) = create_recovery_code(store.connection, &keys).unwrap();
        let resumed = load_pending_recovery(store.connection, &keys, &account)
            .unwrap()
            .unwrap();
        confirm_recovery(store.connection, &keys, resumed, &shown.confirmation_hash).unwrap();
        assert!(keys.read(PENDING_RECOVERY_SERVICE, &account).is_err());
        let pending_count: i64 = store.connection.query_row("SELECT COUNT(*) FROM desktop_cloud_account_state WHERE pending_recovery_salt_b64 IS NOT NULL OR pending_recovery_code_hash IS NOT NULL", [], |row| row.get(0)).unwrap();
        assert_eq!(pending_count, 0);
    }

    #[test]
    fn visibly_saved_code_can_resume_when_legacy_memory_pending_was_lost() {
        let mut c = database();
        let keys = Mem(RefCell::new(BTreeMap::new()));
        let session = Session {
            user_id: "verified-user-visible-recovery".into(),
            email: "visible@example.invalid".into(),
            display_name: None,
            avatar_url: None,
            access_token: "access-secret".into(),
            refresh_token: "refresh-secret".into(),
            expires_at_epoch_seconds: 9_999_999_999,
        };
        save_session(&keys, &session).unwrap();
        let account = sync_state_v1::account_ref(&session.user_id).unwrap();
        c.execute("INSERT INTO desktop_cloud_account_state VALUES(?1,?2,?3,NULL,NULL,'AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION',0,0,NULL,0,NULL,NULL,'device-safe',1,1,NULL,NULL,NULL)",params![account,session.user_id,session.email]).unwrap();
        let mut store = SqliteMetadataStore { connection: &mut c };
        sync_state_v1::authenticate(&mut store, &keys, "auth", None, &session.user_id).unwrap();
        let code = "NF-visible-recovery-code-for-resume";
        let pending = pending_recovery_from_visible_code(&account, code).unwrap();
        let confirmation_hash = sha256(code.as_bytes());
        confirm_recovery(store.connection, &keys, pending, &confirmation_hash).unwrap();
        let confirmed: i64 = store
            .connection
            .query_row(
                "SELECT recovery_confirmed FROM desktop_cloud_account_state WHERE account_ref=?1",
                [&account],
                |row| row.get(0),
            )
            .unwrap();
        assert_eq!(confirmed, 1);
    }

    #[test]
    fn legacy_confirmed_account_does_not_recreate_data_key_for_direct_direction_choice() {
        let mut c = database();
        let keys = Mem(RefCell::new(BTreeMap::new()));
        let session = Session {
            user_id: "verified-user-data-key-repair".into(),
            email: "repair@example.invalid".into(),
            display_name: None,
            avatar_url: None,
            access_token: "access-secret".into(),
            refresh_token: "refresh-secret".into(),
            expires_at_epoch_seconds: 9_999_999_999,
        };
        save_session(&keys, &session).unwrap();
        let account = sync_state_v1::account_ref(&session.user_id).unwrap();
        c.execute("INSERT INTO desktop_cloud_account_state VALUES(?1,?2,?3,NULL,NULL,'AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION',0,0,NULL,0,NULL,NULL,'device-safe',1,1,NULL,NULL,NULL)",params![account,session.user_id,session.email]).unwrap();
        let mut store = SqliteMetadataStore { connection: &mut c };
        sync_state_v1::authenticate(&mut store, &keys, "auth", None, &session.user_id).unwrap();
        let (shown, pending) = create_recovery_code(store.connection, &keys).unwrap();
        confirm_recovery(store.connection, &keys, pending, &shown.confirmation_hash).unwrap();
        keys.delete(DATA_KEY_SERVICE, &account).unwrap();

        choose_selected_local_start(store.connection, &keys).unwrap();

        assert!(keys.read(DATA_KEY_SERVICE, &account).is_err());
        let state: String = store
            .connection
            .query_row(
                "SELECT state FROM desktop_cloud_account_state WHERE account_ref=?1",
                [&account],
                |row| row.get(0),
            )
            .unwrap();
        assert_eq!(state, "READY");
    }

    #[test]
    fn recovery_code_creation_rejects_a_confirmed_or_non_setup_account() {
        let c = database();
        let keys = Mem(RefCell::new(BTreeMap::new()));
        let session = Session {
            user_id: "verified-user-recovery-guard".into(),
            email: "guard@example.invalid".into(),
            display_name: None,
            avatar_url: None,
            access_token: "access-token".into(),
            refresh_token: "refresh-token".into(),
            expires_at_epoch_seconds: 9_999_999_999,
        };
        save_session(&keys, &session).unwrap();
        let account = sync_state_v1::account_ref(&session.user_id).unwrap();
        c.execute(
            "INSERT INTO desktop_cloud_account_state VALUES(?1,?2,?3,NULL,NULL,'READY',0,1,?4,1,NULL,NULL,'device-safe',1,1,NULL,NULL,NULL)",
            params![account, session.user_id, session.email, URL_SAFE_NO_PAD.encode([9u8; 16])],
        )
        .unwrap();
        let error = match create_recovery_code(&c, &keys) {
            Err(error) => error,
            Ok(_) => panic!("confirmed account unexpectedly created a recovery code"),
        };
        assert!(error.contains("不能新建恢复码"));
    }
    #[test]
    fn definite_stale_commit_is_bounded_and_keeps_local_conversation() {
        struct AlwaysStale(RefCell<u8>, Option<RemoteEnvelope>);
        impl CloudGateway for AlwaysStale {
            fn read(&self, _: &str) -> Result<Option<RemoteEnvelope>, CloudFailure> { Ok(self.1.clone()) }
            fn commit(&self, _: &str, _: u64, _: &str) -> Result<(u64, String), CloudFailure> {
                *self.0.borrow_mut() += 1;
                Err(CloudFailure::StaleRevision)
            }
        }
        let mut connection = database();
        let keys = Mem(RefCell::new(BTreeMap::new()));
        let session = Session {
            user_id: "stale-test-user".into(), email: "stale@example.invalid".into(),
            display_name: None, avatar_url: None, access_token: "synthetic".into(),
            refresh_token: "synthetic".into(), expires_at_epoch_seconds: 9_999_999_999,
        };
        save_session(&keys, &session).unwrap();
        let account = sync_state_v1::account_ref(&session.user_id).unwrap();
        connection.execute("INSERT INTO desktop_cloud_account_state VALUES(?1,?2,?3,NULL,NULL,'READY',0,0,NULL,0,NULL,NULL,'device-safe',1,1,NULL,NULL,NULL)",
            params![account,session.user_id,session.email]).unwrap();
        connection.execute("INSERT INTO workspaces VALUES('workspace-stale','测试','semantic','package','2026-09-01T00:00:00Z')", []).unwrap();
        connection.execute("INSERT INTO workspace_exchange VALUES('workspace-stale',?1)",
            [json!({"conversations":[{"id":"conversation-stale","title":"测试","revision":1,"currentLeafId":"message-stale","messages":[{"id":"message-stale","parentId":null,"ordinal":0,"role":"user","delivery":"COMPLETE","createdAt":"2026-09-01T00:00:00Z","revision":1,"blocks":[{"kind":"TEXT","text":"本机消息"}]}]}]}).to_string()]).unwrap();
        let cloud = AlwaysStale(RefCell::new(0), None);
        assert!(sync_selected_conversation(&mut connection, &keys, &cloud, "workspace-stale", "conversation-stale")
            .unwrap_err().contains("本机消息已保留"));
        assert_eq!(*cloud.0.borrow(), 3);
        let stages: Vec<String> = connection.prepare("SELECT stage FROM desktop_sync_jobs ORDER BY created_at_ms,job_id").unwrap()
            .query_map([], |row| row.get(0)).unwrap().map(Result::unwrap).collect();
        assert_eq!(stages.iter().filter(|stage| stage.as_str() == "SUPERSEDED").count(), 2);
        assert_eq!(stages.iter().filter(|stage| stage.as_str() == "RETRY_WAIT").count(), 1);
        let local: String = connection.query_row("SELECT exchange_json FROM workspace_exchange WHERE workspace_id='workspace-stale'", [], |row| row.get(0)).unwrap();
        assert!(local.contains("本机消息"));
        let document_id = selected_document_id(&connection, &account, "workspace-stale", "conversation-stale").unwrap();
        let (payload, _) = conversation_payload(&connection, "workspace-stale", "conversation-stale", &document_id, 1).unwrap();
        let envelope = sync_v1::seal_direct(payload).unwrap();
        let payload_hash = serde_json::from_str::<Value>(&envelope).unwrap()["payloadHash"].as_str().unwrap().to_owned();
        connection.execute("INSERT INTO desktop_selected_conversation_sync VALUES(?1,'workspace-stale','conversation-stale',?2,1,?3,'old-local-hash',1)", params![account,document_id,payload_hash]).unwrap();
        let selected_cloud = AlwaysStale(RefCell::new(0), Some(RemoteEnvelope { revision: 1, payload_hash, envelope }));
        assert!(sync_selected_conversation(&mut connection, &keys, &selected_cloud, "workspace-stale", "conversation-stale")
            .unwrap_err().contains("本机消息已保留"));
        assert_eq!(*selected_cloud.0.borrow(), 3);
        let pending: i64 = connection.query_row("SELECT COUNT(*) FROM desktop_sync_jobs WHERE account_ref=?1 AND workspace_id='workspace-stale' AND conversation_id='conversation-stale' AND stage='CONTENT_PENDING'", [&account], |row| row.get(0)).unwrap();
        assert_eq!(pending, 1, "selected content must be queued for the next background cycle");
    }

    #[test]
    fn normal_sync_does_not_receipt_unverified_matching_readback() {
        struct Cloud(RefCell<Option<RemoteEnvelope>>);
        impl CloudGateway for Cloud {
            fn delete(&self, _: &str, expected: u64) -> Result<bool, CloudFailure> {
                if self.0.borrow().as_ref().is_some_and(|r| r.revision != expected) { return Err(CloudFailure::StaleRevision); }
                Ok(self.0.borrow_mut().take().is_some())
            }
            fn read(&self, _: &str) -> Result<Option<RemoteEnvelope>, CloudFailure> {
                Ok(self.0.borrow().clone())
            }
            fn commit(&self, _: &str, _: u64, e: &str) -> Result<(u64, String), CloudFailure> {
                let v: Value = serde_json::from_str(e).unwrap();
                let r = v["revision"].as_u64().unwrap();
                let h = v["payloadHash"].as_str().unwrap().to_owned();
                *self.0.borrow_mut() = Some(RemoteEnvelope {
                    revision: r,
                    payload_hash: h.clone(),
                    envelope: json!({"format":"unknown-format","revision":r,"payloadHash":h}).to_string(),
                });
                Ok((r, h))
            }
            fn list(&self) -> Result<Vec<RemoteEnvelope>, CloudFailure> {
                Ok(self.0.borrow().clone().into_iter().collect())
            }
        }
        let mut c = database();
        let keys = Mem(RefCell::new(BTreeMap::new()));
        let session = Session {
            user_id: "verified-user-456".into(),
            email: "user@example.invalid".into(),
            display_name: None,
            avatar_url: None,
            access_token: "a".into(),
            refresh_token: "r".into(),
            expires_at_epoch_seconds: 9999999999,
        };
        save_session(&keys, &session).unwrap();
        let account = sync_state_v1::account_ref(&session.user_id).unwrap();
        c.execute("INSERT INTO desktop_cloud_account_state VALUES(?1,?2,?3,NULL,NULL,'AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION',0,0,NULL,0,NULL,NULL,'device-safe',1,1,NULL,NULL,NULL)",params![account,session.user_id,session.email]).unwrap();
        let mut store = SqliteMetadataStore { connection: &mut c };
        sync_state_v1::authenticate(&mut store, &keys, "auth", None, "verified-user-456").unwrap();
        let (shown, pending) = create_recovery_code(store.connection, &keys).unwrap();
        confirm_recovery(store.connection, &keys, pending, &shown.confirmation_hash).unwrap();
        let meta = store.metadata(&account).unwrap().unwrap();
        sync_state_v1::choose_direction(
            &mut store,
            "direction",
            &account,
            meta.revision,
            "LOCAL_PRESENT_EMPTY_REMOTE",
        )
        .unwrap();
        store
            .connection
            .execute(
                "UPDATE desktop_cloud_account_state SET state='READY' WHERE account_ref=?1",
                [&account],
            )
            .unwrap();
        store.connection.execute("INSERT INTO workspaces VALUES('workspace-safe','安全对话','semantic-safe','package-safe','2026-09-01T00:00:00Z')", []).unwrap();
        store.connection.execute("INSERT INTO workspace_exchange VALUES('workspace-safe',?1)",[json!({"conversations":[{"id":"conversation-safe","title":"安全对话","revision":1,"currentLeafId":"message-safe","messages":[{"id":"message-safe","parentId":null,"ordinal":0,"role":"user","delivery":"COMPLETE","createdAt":"2026-09-01T00:00:00Z","revision":1,"blocks":[{"kind":"TEXT","text":"安全正文"},{"kind":"ASSET_REF","asset":"local-only"}]}]}]}).to_string()]).unwrap();
        let cloud = Cloud(RefCell::new(None));
        let result = sync_selected_conversation(store.connection, &keys, &cloud, "workspace-safe", "conversation-safe");
        assert!(!result.is_ok_and(|receipt| receipt.status == "SYNCED"), "unverified readback was accepted as successful sync");
        let selections: i64 = store.connection.query_row("SELECT count(*) FROM desktop_selected_conversation_sync", [], |r| r.get(0)).unwrap();
        assert_eq!(selections, 0);
        let stage: String = store.connection.query_row("SELECT stage FROM desktop_sync_jobs ORDER BY updated_at_ms DESC LIMIT 1", [], |r| r.get(0)).unwrap();
        assert_eq!(stage, "UNKNOWN");
    }

    #[test]
    fn normal_sync_unknown_sql_rejection_is_explicit_and_blocks_cloud_retry() {
        struct Cloud(RefCell<Option<RemoteEnvelope>>, std::cell::Cell<usize>);
        impl CloudGateway for Cloud {
            fn delete(&self, _: &str, expected: u64) -> Result<bool, CloudFailure> {
                if self.0.borrow().as_ref().is_some_and(|r| r.revision != expected) { return Err(CloudFailure::StaleRevision); }
                Ok(self.0.borrow_mut().take().is_some())
            }
            fn read(&self, _: &str) -> Result<Option<RemoteEnvelope>, CloudFailure> {
                self.1.set(self.1.get()+1); Ok(self.0.borrow().clone())
            }
            fn commit(&self, _: &str, _: u64, e: &str) -> Result<(u64, String), CloudFailure> {
                let v: Value = serde_json::from_str(e).unwrap();
                let r = v["revision"].as_u64().unwrap();
                let h = v["payloadHash"].as_str().unwrap().to_owned();
                *self.0.borrow_mut() = Some(RemoteEnvelope {
                    revision: r,
                    payload_hash: h.clone(),
                    envelope: json!({"format":"unknown-format","revision":r,"payloadHash":h}).to_string(),
                });
                Ok((r, h))
            }
            fn list(&self) -> Result<Vec<RemoteEnvelope>, CloudFailure> {
                Ok(self.0.borrow().clone().into_iter().collect())
            }
        }
        for reject_account in [false, true] {
        let mut c = database();
        let keys = Mem(RefCell::new(BTreeMap::new()));
        let session = Session {
            user_id: "verified-user-456".into(),
            email: "user@example.invalid".into(),
            display_name: None,
            avatar_url: None,
            access_token: "a".into(),
            refresh_token: "r".into(),
            expires_at_epoch_seconds: 9999999999,
        };
        save_session(&keys, &session).unwrap();
        let account = sync_state_v1::account_ref(&session.user_id).unwrap();
        c.execute("INSERT INTO desktop_cloud_account_state VALUES(?1,?2,?3,NULL,NULL,'AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION',0,0,NULL,0,NULL,NULL,'device-safe',1,1,NULL,NULL,NULL)",params![account,session.user_id,session.email]).unwrap();
        let mut store = SqliteMetadataStore { connection: &mut c };
        sync_state_v1::authenticate(&mut store, &keys, "auth", None, "verified-user-456").unwrap();
        let (shown, pending) = create_recovery_code(store.connection, &keys).unwrap();
        confirm_recovery(store.connection, &keys, pending, &shown.confirmation_hash).unwrap();
        let meta = store.metadata(&account).unwrap().unwrap();
        sync_state_v1::choose_direction(
            &mut store,
            "direction",
            &account,
            meta.revision,
            "LOCAL_PRESENT_EMPTY_REMOTE",
        )
        .unwrap();
        store
            .connection
            .execute(
                "UPDATE desktop_cloud_account_state SET state='READY' WHERE account_ref=?1",
                [&account],
            )
            .unwrap();
        store.connection.execute("INSERT INTO workspaces VALUES('workspace-safe','安全对话','semantic-safe','package-safe','2026-09-01T00:00:00Z')", []).unwrap();
        store.connection.execute("INSERT INTO workspace_exchange VALUES('workspace-safe',?1)",[json!({"conversations":[{"id":"conversation-safe","title":"安全对话","revision":1,"currentLeafId":"message-safe","messages":[{"id":"message-safe","parentId":null,"ordinal":0,"role":"user","delivery":"COMPLETE","createdAt":"2026-09-01T00:00:00Z","revision":1,"blocks":[{"kind":"TEXT","text":"安全正文"},{"kind":"ASSET_REF","asset":"local-only"}]}]}]}).to_string()]).unwrap();
        let cloud = Cloud(RefCell::new(None), std::cell::Cell::new(0));
        let trigger = if reject_account {
            "CREATE TRIGGER synthetic_reject_unknown BEFORE UPDATE OF state ON desktop_cloud_account_state WHEN NEW.state='VERIFYING' BEGIN SELECT RAISE(ABORT,'synthetic account reject'); END;"
        } else {
            "CREATE TRIGGER synthetic_reject_unknown BEFORE UPDATE OF stage ON desktop_sync_jobs WHEN NEW.stage='UNKNOWN' BEGIN SELECT RAISE(ABORT,'synthetic job reject'); END;"
        };
        store.connection.execute_batch(trigger).unwrap();
        let result = sync_selected_conversation(store.connection, &keys, &cloud, "workspace-safe", "conversation-safe");
        assert!(result.is_err(), "SQL rejection must not return an unpersisted UNKNOWN receipt: {result:?}");
        let reads = cloud.1.get();
        store.connection.execute_batch("DROP TRIGGER synthetic_reject_unknown").unwrap();
        let retry = sync_selected_conversation(store.connection, &keys, &cloud, "workspace-safe", "conversation-safe").unwrap();
        assert_eq!(retry.status, "UNKNOWN");
        assert_eq!(cloud.1.get(), reads, "unresolved external attempt must block another cloud access");
        assert_eq!(store.connection.query_row("SELECT count(*) FROM desktop_sync_jobs", [], |r| r.get::<_,i64>(0)).unwrap(), 1);
        }
    }

    #[test]
    fn selected_conversation_sync_readback_and_latest_update_are_receipted() {
        struct Cloud(RefCell<Option<RemoteEnvelope>>);
        impl CloudGateway for Cloud {
            fn delete(&self, _: &str, expected: u64) -> Result<bool, CloudFailure> {
                if self.0.borrow().as_ref().is_some_and(|r| r.revision != expected) { return Err(CloudFailure::StaleRevision); }
                Ok(self.0.borrow_mut().take().is_some())
            }
            fn read(&self, _: &str) -> Result<Option<RemoteEnvelope>, CloudFailure> {
                Ok(self.0.borrow().clone())
            }
            fn commit(&self, _: &str, _: u64, e: &str) -> Result<(u64, String), CloudFailure> {
                let v: Value = serde_json::from_str(e).unwrap();
                let r = v["revision"].as_u64().unwrap();
                let h = v["payloadHash"].as_str().unwrap().to_owned();
                *self.0.borrow_mut() = Some(RemoteEnvelope {
                    revision: r,
                    payload_hash: h.clone(),
                    envelope: e.into(),
                });
                Ok((r, h))
            }
            fn list(&self) -> Result<Vec<RemoteEnvelope>, CloudFailure> {
                Ok(self.0.borrow().clone().into_iter().collect())
            }
        }
        let mut c = database();
        let keys = Mem(RefCell::new(BTreeMap::new()));
        let session = Session {
            user_id: "verified-user-456".into(),
            email: "user@example.invalid".into(),
            display_name: None,
            avatar_url: None,
            access_token: "a".into(),
            refresh_token: "r".into(),
            expires_at_epoch_seconds: 9999999999,
        };
        save_session(&keys, &session).unwrap();
        let account = sync_state_v1::account_ref(&session.user_id).unwrap();
        c.execute("INSERT INTO desktop_cloud_account_state VALUES(?1,?2,?3,NULL,NULL,'AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION',0,0,NULL,0,NULL,NULL,'device-safe',1,1,NULL,NULL,NULL)",params![account,session.user_id,session.email]).unwrap();
        let mut store = SqliteMetadataStore { connection: &mut c };
        sync_state_v1::authenticate(&mut store, &keys, "auth", None, "verified-user-456").unwrap();
        let (shown, pending) = create_recovery_code(store.connection, &keys).unwrap();
        confirm_recovery(store.connection, &keys, pending, &shown.confirmation_hash).unwrap();
        let meta = store.metadata(&account).unwrap().unwrap();
        sync_state_v1::choose_direction(
            &mut store,
            "direction",
            &account,
            meta.revision,
            "LOCAL_PRESENT_EMPTY_REMOTE",
        )
        .unwrap();
        store
            .connection
            .execute(
                "UPDATE desktop_cloud_account_state SET state='READY' WHERE account_ref=?1",
                [&account],
            )
            .unwrap();
        store.connection.execute("INSERT INTO workspaces VALUES('workspace-safe','安全对话','semantic-safe','package-safe','2026-09-01T00:00:00Z')", []).unwrap();
        store.connection.execute("INSERT INTO workspace_exchange VALUES('workspace-safe',?1)",[json!({"conversations":[{"id":"conversation-safe","title":"安全对话","revision":1,"currentLeafId":"message-safe","messages":[{"id":"message-safe","parentId":null,"ordinal":0,"role":"user","delivery":"COMPLETE","createdAt":"2026-09-01T00:00:00Z","revision":1,"blocks":[{"kind":"TEXT","text":"安全正文"},{"kind":"ASSET_REF","asset":"local-only"}]}]}]}).to_string()]).unwrap();
        let cloud = Cloud(RefCell::new(None));
        let receipt = sync_selected_conversation(
            store.connection,
            &keys,
            &cloud,
            "workspace-safe",
            "conversation-safe",
        )
        .unwrap();
        assert_eq!(receipt.status, "SYNCED");
        // A corrupt neighbouring document must not suppress a valid row or
        // require the user to click again after partial local commits.
        struct MixedList<'a>(&'a Cloud);
        impl CloudGateway for MixedList<'_> {
            fn read(&self, _: &str) -> Result<Option<RemoteEnvelope>, CloudFailure> { panic!("list must reuse its envelopes") }
            fn commit(&self, _: &str, _: u64, _: &str) -> Result<(u64, String), CloudFailure> { panic!("reading current documents must not upload") }
            fn list(&self) -> Result<Vec<RemoteEnvelope>, CloudFailure> {
                let mut rows = vec![RemoteEnvelope { revision: 1, payload_hash: "invalid".into(), envelope: "{}".into() }];
                rows.extend(self.0.list()?);
                Ok(rows)
            }
        }
        let mixed = restore_all_remote_conversations(store.connection, &keys, &MixedList(&cloud));
        assert!(mixed.is_ok(), "one invalid document must not abort valid cloud history: {mixed:?}");
        let mixed = mixed.unwrap();
        assert_eq!(mixed.restored.len(), 1);
        assert_eq!(mixed.failed_count, 1);
        assert!(mixed.cloud_conversation_keys.is_none(), "an unidentified item must disable absence-based pruning");
        let listed = restore_all_remote_conversations(store.connection, &keys, &cloud).unwrap();
        assert_eq!(listed.restored.len(), 1);
        assert_eq!(listed.restored[0].status, "ALREADY_LOCAL");
        assert_eq!(listed.restored[0].workspace_id, "workspace-safe");
        // A cloud list read may race the automatic continuation of an already
        // selected local edit. It must not install the older cloud snapshot or
        // acknowledge that snapshot as the result of the local edit.
        let pending_favorite = store.connection.transaction().unwrap();
        pending_favorite.execute("INSERT INTO desktop_conversation_favorites(workspace_id,conversation_id,favorited_at_ms) VALUES('workspace-safe','conversation-safe',1)", []).unwrap();
        queue_selected_content_change(&pending_favorite, "workspace-safe", "conversation-safe", "favorite-pending").unwrap();
        pending_favorite.commit().unwrap();
        let pending_read = restore_all_remote_conversations(store.connection, &keys, &cloud).unwrap();
        assert_eq!(pending_read.failure_reasons.get("LOCAL_SYNC_PENDING"), Some(&1));
        let still_favorited: i64 = store.connection.query_row("SELECT count(*) FROM desktop_conversation_favorites WHERE workspace_id='workspace-safe' AND conversation_id='conversation-safe'", [], |row| row.get(0)).unwrap();
        assert_eq!(still_favorited, 1);
        let sync_result = sync_selected_conversation(store.connection, &keys, &cloud, "workspace-safe", "conversation-safe");
        assert_eq!(sync_result.as_ref().unwrap().status, "SYNCED");
        let pending_upload = cloud.0.borrow().clone().unwrap();
        let pending_document = format!("conversation-{}", &sha256(b"conversation-safe")[..40]);
        let pending_payload = sync_v1::open_direct(&pending_upload.envelope, APP_ID, &pending_document, pending_upload.revision).unwrap();
        assert_eq!(pending_payload["records"][0]["content"]["favorited"], true);
        finish_pending_title_attempt(store.connection, &format!("content-pending:{account}:workspace-safe:conversation-safe"), "favorite-pending", &sync_result).unwrap();
        assert_eq!(restore_all_remote_conversations(store.connection, &keys, &cloud).unwrap().failed_count, 0);
        let read_marker_migration = store.connection.transaction().unwrap();
        crate::desktop_conversation_read_state_v1::migrate(&read_marker_migration).unwrap();
        read_marker_migration.commit().unwrap();
        // The existing Desktop copy establishes its local read baseline before
        // a later phone answer arrives through the same restored document.
        let baseline = crate::desktop_conversation_read_state_v1::read_projection(
            store.connection,
            "workspace-safe",
            Some("conversation-safe"),
            1,
        )
        .unwrap();
        assert!(baseline.conversations.iter().all(|row| !row.unread));
        assert_eq!(
            projection(store.connection, &keys, true, None)
                .unwrap()
                .selected_conversation_count,
            1
        );
        let mut remote_exchange: Value = store
            .connection
            .query_row(
                "SELECT exchange_json FROM workspace_exchange WHERE workspace_id='workspace-safe'",
                [],
                |row| row.get(0),
            )
            .map(|value: String| serde_json::from_str(&value).unwrap())
            .unwrap();
        // A locally saved rename has not reached the cloud yet. Reading the
        // old cloud snapshot must neither undo it nor acknowledge it as sent.
        let original_exchange = remote_exchange.clone();
        remote_exchange["conversations"][0]["title"] = json!("尚未上传的新标题");
        remote_exchange["conversations"][0]["titleRevision"] = json!(1);
        remote_exchange["conversations"][0]["updatedAt"] = json!("2026-09-01T00:02:00Z");
        remote_exchange["conversations"][0]["revision"] = json!(2);
        store.connection.execute("UPDATE workspace_exchange SET exchange_json=?1 WHERE workspace_id='workspace-safe'", [remote_exchange.to_string()]).unwrap();
        restore_all_remote_conversations(store.connection, &keys, &cloud).unwrap();
        let title_after_read: String = store.connection.query_row("SELECT json_extract(exchange_json,'$.conversations[0].title') FROM workspace_exchange WHERE workspace_id='workspace-safe'", [], |row| row.get(0)).unwrap();
        assert_eq!(title_after_read, "尚未上传的新标题");
        assert_eq!(sync_selected_conversation(store.connection, &keys, &cloud, "workspace-safe", "conversation-safe").unwrap().status, "SYNCED");
        let uploaded = cloud.0.borrow().clone().unwrap();
        let document = format!("conversation-{}", &sha256(b"conversation-safe")[..40]);
        let payload = sync_v1::open_direct(&uploaded.envelope, APP_ID, &document, uploaded.revision).unwrap();
        assert_eq!(payload["records"][0]["content"]["title"], "尚未上传的新标题");
        // Return to the original baseline for the existing phone-update cases.
        *cloud.0.borrow_mut() = None;
        store.connection.execute("DELETE FROM desktop_selected_conversation_sync", []).unwrap();
        store.connection.execute("UPDATE workspace_exchange SET exchange_json=?1 WHERE workspace_id='workspace-safe'", [original_exchange.to_string()]).unwrap();
        sync_selected_conversation(store.connection, &keys, &cloud, "workspace-safe", "conversation-safe").unwrap();
        remote_exchange = original_exchange;
        let remote_conversation = remote_exchange["conversations"]
            .as_array_mut()
            .unwrap()
            .first_mut()
            .unwrap();
        remote_conversation["title"] = Value::String("手机端新标题".into());
        remote_conversation["titleRevision"] = json!(1);
        remote_conversation["revision"] = Value::Number(2.into());
        remote_conversation["currentLeafId"] = Value::String("message-phone".into());
        remote_conversation["messages"]
            .as_array_mut()
            .unwrap()
            .push(json!({
                "id":"message-phone","parentId":"message-safe","ordinal":0,"role":"assistant",
                "delivery":"COMPLETE","createdAt":"2026-09-01T00:01:00Z","revision":1,
                "blocks":[{"kind":"TEXT","text":"手机端新增正文"}]
            }));
        store.connection.execute(
            "UPDATE workspace_exchange SET exchange_json=?1 WHERE workspace_id='workspace-safe'",
            [remote_exchange.to_string()],
        ).unwrap();
        assert_eq!(
            sync_selected_conversation(
                store.connection,
                &keys,
                &cloud,
                "workspace-safe",
                "conversation-safe"
            )
            .unwrap()
            .status,
            "SYNCED"
        );
        let stale_exchange = json!({"conversations":[{"id":"conversation-safe","title":"电脑端旧标题","revision":1,"currentLeafId":"message-safe","messages":[{"id":"message-safe","parentId":null,"ordinal":0,"role":"user","delivery":"COMPLETE","createdAt":"2026-09-01T00:00:00Z","revision":1,"blocks":[{"kind":"TEXT","text":"安全正文"}]}]}]});
        store.connection.execute(
            "UPDATE workspace_exchange SET exchange_json=?1 WHERE workspace_id='workspace-safe'",
            [stale_exchange.to_string()],
        ).unwrap();
        let refreshed = restore_all_remote_conversations(store.connection, &keys, &cloud).unwrap();
        assert_eq!(refreshed.restored[0].status, "UPDATED_LOCAL");
        let refreshed_title: String = store.connection.query_row(
            "SELECT json_extract(exchange_json, '$.conversations[0].title') FROM workspace_exchange WHERE workspace_id='workspace-safe'",
            [],
            |row| row.get(0),
        ).unwrap();
        assert_eq!(refreshed_title, "手机端新标题");
        let refreshed_message_count: i64 = store.connection.query_row(
            "SELECT json_array_length(json_extract(exchange_json, '$.conversations[0].messages')) FROM workspace_exchange WHERE workspace_id='workspace-safe'",
            [],
            |row| row.get(0),
        ).unwrap();
        assert_eq!(refreshed_message_count, 2);
        let unread_after_phone_merge = crate::desktop_conversation_read_state_v1::read_projection(
            store.connection,
            "workspace-safe",
            None,
            2,
        )
        .unwrap();
        assert!(unread_after_phone_merge.conversations.iter().any(|row| {
            row.conversation_id == "conversation-safe" && row.unread
        }));
        let incomplete = json!({"id":"conversation-safe","title":"手机端新标题","revision":3,
            "currentLeafId":"message-safe","messages":[{"id":"message-safe","parentId":null,
            "ordinal":0,"role":"user","delivery":"COMPLETE","createdAt":"2026-09-01T00:00:00Z",
            "revision":1,"blocks":[{"kind":"TEXT","text":"安全正文"}]}]});
        let transaction = store.connection.transaction().unwrap();
        merge_newer_remote_conversation(&transaction, "workspace-safe", "conversation-safe",
            incomplete.as_object().unwrap(), 3).unwrap();
        transaction.commit().unwrap();
        let retained: (i64, String) = store.connection.query_row(
            "SELECT json_array_length(json_extract(exchange_json,'$.conversations[0].messages')), json_extract(exchange_json,'$.conversations[0].currentLeafId') FROM workspace_exchange WHERE workspace_id='workspace-safe'",
            [], |row| Ok((row.get(0)?, row.get(1)?)),
        ).unwrap();
        assert_eq!(retained, (2, "message-phone".into()));
        // An old installation made a separate cloud workspace for this same
        // conversation. Continuing that selected copy must recover the answer
        // from its original local occurrence, without matching on titles.
        let original: String = store.connection.query_row(
            "SELECT exchange_json FROM workspace_exchange WHERE workspace_id='workspace-safe'", [], |row| row.get(0),
        ).unwrap();
        store.connection.execute("INSERT INTO workspaces VALUES('workspace-original','original','s','p','2026-09-01T00:00:00Z')", []).unwrap();
        store.connection.execute("INSERT INTO workspace_exchange VALUES('workspace-original',?1)", [&original]).unwrap();
        store.connection.execute("UPDATE workspace_exchange SET exchange_json=?1 WHERE workspace_id='workspace-safe'",
            [json!({"conversations":[incomplete]}).to_string()]).unwrap();
        *cloud.0.borrow_mut() = None;
        assert!(continue_selected_conversation(store.connection, &keys, &cloud, "workspace-safe", "conversation-safe").unwrap_err().contains("已从云端删除"));
        assert!(cloud.0.borrow().is_none());
        assert!(continue_selected_conversation(store.connection, &keys, &cloud, "workspace-safe", "conversation-safe").is_err());
        // Only a new explicit user sync may recreate the removed copy.
        sync_selected_conversation(store.connection, &keys, &cloud, "workspace-safe", "conversation-safe").unwrap();
        let recovered_count: i64 = store.connection.query_row(
            "SELECT json_array_length(json_extract(exchange_json,'$.conversations[0].messages')) FROM workspace_exchange WHERE workspace_id='workspace-safe'",
            [], |row| row.get(0),
        ).unwrap();
        assert_eq!(recovered_count, 2);
        // Repeated edits must cross the real envelope/restore seams even when
        // the message identity and semantic revision are unchanged.
        for (second, body) in [(10, "云端列表第二次完整正文"), (20, "云端列表第三次完整正文")] {
            let previous: String = store.connection.query_row(
                "SELECT exchange_json FROM workspace_exchange WHERE workspace_id='workspace-safe'",
                [], |row| row.get(0),
            ).unwrap();
            let mut updated: Value = serde_json::from_str(&previous).unwrap();
            updated["conversations"][0]["updatedAt"] = json!(format!("2026-09-01T00:02:{second:02}Z"));
            updated["conversations"][0]["messages"][1]["blocks"][0]["text"] = json!(body);
            store.connection.execute(
                "UPDATE workspace_exchange SET exchange_json=?1 WHERE workspace_id='workspace-safe'",
                [updated.to_string()],
            ).unwrap();
            assert_eq!(sync_selected_conversation(store.connection, &keys, &cloud,
                "workspace-safe", "conversation-safe").unwrap().status, "SYNCED");
            store.connection.execute(
                "UPDATE workspace_exchange SET exchange_json=?1 WHERE workspace_id='workspace-safe'",
                [&previous],
            ).unwrap();
            assert_eq!(restore_all_remote_conversations(store.connection, &keys, &cloud)
                .unwrap().restored[0].status, "UPDATED_LOCAL");
            let restored_body: String = store.connection.query_row(
                "SELECT json_extract(exchange_json, '$.conversations[0].messages[1].blocks[0].text') FROM workspace_exchange WHERE workspace_id='workspace-safe'",
                [], |row| row.get(0),
            ).unwrap();
            assert_eq!(restored_body, body);
        }
        cloud.0.borrow_mut().as_mut().unwrap().payload_hash = "f".repeat(64);
        let latest = sync_selected_conversation(
            store.connection,
            &keys,
            &cloud,
            "workspace-safe",
            "conversation-safe",
        )
        .unwrap();
        assert_eq!(latest.status, "SYNCED");
        store.connection.execute("UPDATE workspace_exchange SET exchange_json=json_set(exchange_json,'$.conversations[0].deleted',json('true')) WHERE workspace_id='workspace-safe'", []).unwrap();
        let removed = continue_selected_conversation(store.connection, &keys, &cloud, "workspace-safe", "conversation-safe").unwrap();
        assert_eq!(removed.safe_code.as_deref(), Some("REMOTE_COPY_REMOVED"));
        assert!(cloud.0.borrow().is_none(), "local deletion must remove its cloud copy instead of uploading");
        store.connection.execute("UPDATE workspace_exchange SET exchange_json=json_set(exchange_json,'$.conversations[0].deleted',json('false')) WHERE workspace_id='workspace-safe'", []).unwrap();
        sync_selected_conversation(store.connection, &keys, &cloud, "workspace-safe", "conversation-safe").unwrap();
        *cloud.0.borrow_mut() = None;
        store.connection.execute("INSERT INTO desktop_selected_conversation_sync SELECT 'other-fixture-account',workspace_id,conversation_id,document_id,remote_revision,payload_hash,local_content_hash,last_synced_at_ms FROM desktop_selected_conversation_sync WHERE account_ref=?1", [&account]).unwrap();
        let empty = restore_all_remote_conversations(store.connection, &keys, &cloud).unwrap();
        assert!(empty.cloud_conversation_keys.unwrap().is_empty());
        assert!(continue_selected_conversation(store.connection, &keys, &cloud, "workspace-safe", "conversation-safe").is_err());
        assert!(cloud.0.borrow().is_none());
        let kept: i64 = store.connection.query_row("SELECT count(*) FROM workspace_exchange WHERE workspace_id='workspace-safe'", [], |r| r.get(0)).unwrap();
        assert_eq!(kept, 1, "remote deletion must not delete the other device's local content");
        let other: i64 = store.connection.query_row("SELECT count(*) FROM desktop_selected_conversation_sync WHERE account_ref='other-fixture-account'", [], |r| r.get(0)).unwrap();
        assert_eq!(other, 1, "inventory cleanup is strictly account isolated");
    }

    #[test]
    fn local_commit_resolves_title_by_its_own_revision_not_pin_or_message_time() {
        let mut c = database();
        c.execute(
            "INSERT INTO workspaces(id,title,semantic_hash,package_hash,created_at) VALUES(?1,?2,?3,?4,?5)",
            params!["workspace-union", "测试", "before", "package", "2026-09-01T00:00:00Z"],
        )
        .unwrap();
        c.execute(
            "INSERT INTO workspace_exchange VALUES('workspace-union',?1)",
            [json!({"conversations":[{"id":"conversation-union","title":"电脑端最新标题","revision":2,"currentLeafId":"desktop","messages":[
                {"id":"root","parentId":null,"ordinal":0,"role":"user","delivery":"COMPLETE","createdAt":"2026-09-01T00:00:00Z","revision":1,"blocks":[{"kind":"TEXT","text":"根消息"}]},
                {"id":"desktop","parentId":"root","ordinal":2,"role":"assistant","delivery":"COMPLETE","createdAt":"2026-09-01T00:02:00Z","revision":1,"blocks":[{"kind":"TEXT","text":"电脑端新增"}]}
            ]}]}).to_string()],
        )
        .unwrap();
        let remote = json!({
            "title":"手机端旧标题","titleRevision":1,"revision":3,"currentLeafId":"phone","messages":[
                {"id":"root","parentId":null,"ordinal":0,"role":"user","delivery":"COMPLETE","createdAt":"2026-09-01T00:00:00Z","revision":1,"blocks":[{"kind":"TEXT","text":"根消息"}]},
                {"id":"phone","parentId":"root","ordinal":1,"role":"assistant","delivery":"COMPLETE","createdAt":"2026-09-01T00:01:00Z","revision":1,"blocks":[{"kind":"TEXT","text":"手机端新增"}]}
            ]
        });
        let transaction = c.transaction().unwrap();
        assert_eq!(
            merge_remote_additions_for_local_commit(
                &transaction,
                "workspace-union",
                "conversation-union",
                remote.as_object().unwrap(),
                3,
            )
            .unwrap(),
            ExistingRemoteConversationMerge::Updated,
        );
        transaction.commit().unwrap();
        let merged: Value = c
            .query_row(
                "SELECT exchange_json FROM workspace_exchange WHERE workspace_id='workspace-union'",
                [],
                |row| row.get::<_, String>(0),
            )
            .map(|value| serde_json::from_str(&value).unwrap())
            .unwrap();
        let conversation = &merged["conversations"][0];
        assert_eq!(conversation["title"], "手机端旧标题");
        assert_eq!(conversation["currentLeafId"], "desktop");
        assert_eq!(conversation["revision"], 3);
        let ids = conversation["messages"]
            .as_array()
            .unwrap()
            .iter()
            .map(|message| message["id"].as_str().unwrap())
            .collect::<BTreeSet<_>>();
        assert_eq!(ids, BTreeSet::from(["root", "phone", "desktop"]));
        let mut newer_remote = remote.clone();
        newer_remote["updatedAt"] = json!("2026-09-02T00:00:00Z");
        newer_remote["title"] = json!("手机端最新标题");
        newer_remote["titleRevision"] = json!(2);
        newer_remote["messages"][0]["blocks"][0]["text"] = json!("最新完整正文");
        let transaction = c.transaction().unwrap();
        merge_remote_additions_for_local_commit(&transaction, "workspace-union", "conversation-union", newer_remote.as_object().unwrap(), 3).unwrap();
        transaction.commit().unwrap();
        let title: String = c.query_row("SELECT json_extract(exchange_json,'$.conversations[0].title') FROM workspace_exchange WHERE workspace_id='workspace-union'", [], |row| row.get(0)).unwrap();
        assert_eq!(title, "手机端最新标题");
        let body: String = c.query_row("SELECT json_extract(exchange_json,'$.conversations[0].messages[0].blocks[0].text') FROM workspace_exchange WHERE workspace_id='workspace-union'", [], |row| row.get(0)).unwrap();
        assert_eq!(body, "最新完整正文");
        let mut later_pin = remote.clone();
        later_pin["updatedAt"] = json!("2036-09-02T00:00:00Z");
        later_pin["pinned"] = json!(true);
        let transaction = c.transaction().unwrap();
        merge_remote_additions_for_local_commit(&transaction, "workspace-union", "conversation-union", later_pin.as_object().unwrap(), 999).unwrap();
        merge_newer_remote_conversation(&transaction, "workspace-union", "conversation-union", later_pin.as_object().unwrap(), 999).unwrap();
        transaction.commit().unwrap();
        let preserved: (String, u64) = c.query_row("SELECT json_extract(exchange_json,'$.conversations[0].title'),json_extract(exchange_json,'$.conversations[0].titleRevision') FROM workspace_exchange WHERE workspace_id='workspace-union'", [], |row| Ok((row.get(0)?, row.get(1)?))).unwrap();
        assert_eq!(preserved, ("手机端最新标题".into(), 2));
        let mut ambiguous = newer_remote.clone();
        ambiguous["title"] = json!("相同版本的另一个标题");
        let transaction = c.transaction().unwrap();
        assert!(merge_newer_remote_conversation(&transaction, "workspace-union", "conversation-union", ambiguous.as_object().unwrap(), 3).unwrap_err().contains("标题版本冲突"));
        assert!(merge_remote_additions_for_local_commit(&transaction, "workspace-union", "conversation-union", ambiguous.as_object().unwrap(), 3).unwrap_err().contains("标题版本冲突"));
        transaction.rollback().unwrap();
        let preserved: String = c.query_row("SELECT json_extract(exchange_json,'$.conversations[0].title') FROM workspace_exchange WHERE workspace_id='workspace-union'", [], |row| row.get(0)).unwrap();
        assert_eq!(preserved, "手机端最新标题");
    }

    #[test]
    fn desktop_wire_answer_restores_model_usage_and_amount() {
        let mut conversation = json!({"messages":[{"id":"answer","role":"assistant","blocks":[{"kind":"TEXT","text":"完成"}],
            "modelUsage":{"modelId":"qwen3.7-plus","modelDisplayName":"Qwen3.7 Plus",
                "inputTokens":11,"outputTokens":7,"costTotalMicros":12,
                "costCurrencyCode":"USD","costPriceVersion":"provider-v1","costSource":"PROVIDER_RESPONSE"}}]});
        normalize_android_selected_conversation(conversation.as_object_mut().unwrap()).unwrap();
        let answer = &conversation["messages"][0];
        assert_eq!(answer["modelSnapshot"]["modelId"], "qwen3.7-plus");
        assert_eq!(answer["usage"]["inputTokens"], 11);
        assert_eq!(answer["usage"]["outputTokens"], 7);
        assert_eq!(answer["chargeMicros"], 12);
        assert_eq!(answer["currencyCode"], "USD");
        assert!(answer.get("modelUsage").is_none());
    }

    #[test]
    fn selected_conversation_rejects_model_usage_on_non_assistant_messages() {
        let usage = json!({"modelId":"qwen3.7-plus","modelDisplayName":"Qwen3.7 Plus",
            "inputTokens":11,"outputTokens":7,"totalTokens":18,
            "cachedInputTokens":null,"reasoningTokens":null,"costTotalMicros":12,
            "costCurrencyCode":"USD","costPriceVersion":"provider-v1","costSource":"PROVIDER_RESPONSE"});
        let mut desktop = json!({"messages":[{"id":"user","role":"user","blocks":[{"kind":"TEXT","text":"问题"}],"modelUsage":usage}]});
        assert!(normalize_android_selected_conversation(desktop.as_object_mut().unwrap()).is_err());
        let mut android = json!({"nodes":[{"id":"user","role":"USER","parentMessageId":null,
            "siblingPosition":0,"createdAtEpochMs":1000,"deliveryState":"COMPLETE",
            "revision":1,"text":["问题"],"modelUsage":usage}],
            "createdAtEpochMs":1000,"updatedAtEpochMs":1000});
        assert!(normalize_android_selected_conversation(android.as_object_mut().unwrap()).is_err());
    }

    #[test]
    fn local_estimate_never_overwrites_remote_provider_settlement() {
        let mut c = database();
        c.execute(
            "INSERT INTO workspaces(id,title,semantic_hash,package_hash,created_at) VALUES(?1,?2,?3,?4,?5)",
            params!["workspace-cost", "测试", "before", "package", "2026-09-01T00:00:00Z"],
        ).unwrap();
        let answer = |micros: u64, source: &str| json!({
            "id":"answer","parentId":"root","ordinal":1,"role":"assistant","delivery":"COMPLETE","createdAt":"2026-09-01T00:01:00Z","revision":1,
            "blocks":[{"kind":"TEXT","text":"完成"}],
            "modelSnapshot":{"modelId":"qwen3.7-plus","displayName":"Qwen3.7 Plus"},
            "usage":{"inputTokens":12,"outputTokens":4,"totalTokens":16},
            "chargeMicros":micros,"currencyCode":"CNY","costPriceVersion":"provider-v1","costSource":source
        });
        c.execute(
            "INSERT INTO workspace_exchange VALUES('workspace-cost',?1)",
            [json!({"conversations":[{"id":"conversation-cost","title":"费用","revision":1,"updatedAt":"2026-09-01T00:01:00Z","currentLeafId":"answer","messages":[
                {"id":"root","parentId":null,"ordinal":0,"role":"user","delivery":"COMPLETE","createdAt":"2026-09-01T00:00:00Z","revision":1,"blocks":[{"kind":"TEXT","text":"问题"}]},
                answer(18, "LOCAL_ESTIMATE")
            ]}]}).to_string()],
        ).unwrap();
        let remote = json!({"title":"费用","revision":1,"updatedAt":"2026-09-01T00:02:00Z","currentLeafId":"answer","messages":[
            {"id":"root","parentId":null,"ordinal":0,"role":"user","delivery":"COMPLETE","createdAt":"2026-09-01T00:00:00Z","revision":1,"blocks":[{"kind":"TEXT","text":"问题"}]},
            answer(18_700, "PROVIDER_RESPONSE")
        ]});
        let transaction = c.transaction().unwrap();
        merge_remote_additions_for_local_commit(&transaction, "workspace-cost", "conversation-cost", remote.as_object().unwrap(), 2).unwrap();
        transaction.commit().unwrap();
        let merged: Value = c.query_row(
            "SELECT exchange_json FROM workspace_exchange WHERE workspace_id='workspace-cost'", [],
            |row| row.get::<_, String>(0),
        ).map(|value| serde_json::from_str(&value).unwrap()).unwrap();
        let restored = &merged["conversations"][0]["messages"][1];
        assert_eq!(restored["chargeMicros"], 18_700);
        assert_eq!(restored["costSource"], "PROVIDER_RESPONSE");
    }

    #[test]
    fn cloud_read_accepts_provider_amount_at_same_semantic_revision() {
        let mut c = database();
        c.execute(
            "INSERT INTO workspaces(id,title,semantic_hash,package_hash,created_at) VALUES(?1,?2,?3,?4,?5)",
            params!["workspace-cost", "测试", "before", "package", "2026-09-01T00:00:00Z"],
        ).unwrap();
        let answer = |micros: u64, source: &str| json!({
            "id":"answer","parentId":"root","ordinal":1,"role":"assistant","delivery":"COMPLETE","createdAt":"2026-09-01T00:01:00Z","revision":1,
            "blocks":[{"kind":"TEXT","text":"完成"}],
            "modelSnapshot":{"modelId":"qwen3.7-plus","displayName":"Qwen3.7 Plus"},
            "usage":{"inputTokens":12,"outputTokens":4,"totalTokens":16},
            "chargeMicros":micros,"currencyCode":"CNY","costPriceVersion":"provider-v1","costSource":source
        });
        c.execute(
            "INSERT INTO workspace_exchange VALUES('workspace-cost',?1)",
            [json!({"conversations":[{"id":"conversation-cost","title":"费用","revision":1,"updatedAt":"2026-09-01T00:01:00Z","currentLeafId":"answer","messages":[
                {"id":"root","parentId":null,"ordinal":0,"role":"user","delivery":"COMPLETE","createdAt":"2026-09-01T00:00:00Z","revision":1,"blocks":[{"kind":"TEXT","text":"问题"}]},
                answer(18, "LOCAL_ESTIMATE")
            ]}]}).to_string()],
        ).unwrap();
        let remote = json!({"title":"费用","revision":1,"updatedAt":"2026-09-01T00:02:00Z","currentLeafId":"answer","messages":[
            {"id":"root","parentId":null,"ordinal":0,"role":"user","delivery":"COMPLETE","createdAt":"2026-09-01T00:00:00Z","revision":1,"blocks":[{"kind":"TEXT","text":"问题"}]},
            answer(18_700, "PROVIDER_RESPONSE")
        ]});
        let transaction = c.transaction().unwrap();
        merge_newer_remote_conversation(&transaction, "workspace-cost", "conversation-cost", remote.as_object().unwrap(), 1).unwrap();
        transaction.commit().unwrap();
        let merged: Value = c.query_row(
            "SELECT exchange_json FROM workspace_exchange WHERE workspace_id='workspace-cost'", [],
            |row| row.get::<_, String>(0),
        ).map(|value| serde_json::from_str(&value).unwrap()).unwrap();
        let restored = &merged["conversations"][0]["messages"][1];
        assert_eq!(restored["chargeMicros"], 18_700);
        assert_eq!(restored["costSource"], "PROVIDER_RESPONSE");
    }

    #[test]
    fn cloud_read_preserves_local_provider_amount_against_remote_estimate() {
        let mut c = database();
        c.execute(
            "INSERT INTO workspaces(id,title,semantic_hash,package_hash,created_at) VALUES(?1,?2,?3,?4,?5)",
            params!["workspace-cost", "测试", "before", "package", "2026-09-01T00:00:00Z"],
        ).unwrap();
        let answer = |micros: u64, source: &str| json!({
            "id":"answer","parentId":"root","ordinal":1,"role":"assistant","delivery":"COMPLETE","createdAt":"2026-09-01T00:01:00Z","revision":1,
            "blocks":[{"kind":"TEXT","text":"完成"}],
            "modelSnapshot":{"modelId":"qwen3.7-plus","displayName":"Qwen3.7 Plus"},
            "usage":{"inputTokens":12,"outputTokens":4,"totalTokens":16},
            "chargeMicros":micros,"currencyCode":"CNY","costPriceVersion":"provider-v1","costSource":source
        });
        c.execute(
            "INSERT INTO workspace_exchange VALUES('workspace-cost',?1)",
            [json!({"conversations":[{"id":"conversation-cost","title":"费用","revision":1,"updatedAt":"2026-09-01T00:01:00Z","currentLeafId":"answer","messages":[
                {"id":"root","parentId":null,"ordinal":0,"role":"user","delivery":"COMPLETE","createdAt":"2026-09-01T00:00:00Z","revision":1,"blocks":[{"kind":"TEXT","text":"问题"}]},
                answer(18_700, "PROVIDER_RESPONSE")
            ]}]}).to_string()],
        ).unwrap();
        let remote = json!({"title":"费用","revision":1,"updatedAt":"2026-09-01T00:02:00Z","currentLeafId":"answer","messages":[
            {"id":"root","parentId":null,"ordinal":0,"role":"user","delivery":"COMPLETE","createdAt":"2026-09-01T00:00:00Z","revision":1,"blocks":[{"kind":"TEXT","text":"问题"}]},
            answer(18, "LOCAL_ESTIMATE")
        ]});
        let transaction = c.transaction().unwrap();
        merge_newer_remote_conversation(&transaction, "workspace-cost", "conversation-cost", remote.as_object().unwrap(), 2).unwrap();
        transaction.commit().unwrap();
        let merged: Value = c.query_row(
            "SELECT exchange_json FROM workspace_exchange WHERE workspace_id='workspace-cost'", [],
            |row| row.get::<_, String>(0),
        ).map(|value| serde_json::from_str(&value).unwrap()).unwrap();
        let restored = &merged["conversations"][0]["messages"][1];
        assert_eq!(restored["chargeMicros"], 18_700);
        assert_eq!(restored["costSource"], "PROVIDER_RESPONSE");
    }

    #[test]
    fn current_sync_rewrites_legacy_retry_or_duplicate_history_instead_of_reporting_up_to_date() {
        let v1_without_portable_model_usage = json!({"payload":{"records":[
            {"kind":"conversation","content":{"messages":[
                {"id":"m1","delivery":"COMPLETE","blocks":[{"kind":"TEXT","text":"portable"}]}
            ]}},
            {"kind":"safe_settings","id":"profile-interests","content":{"portableConversationFormat":1}}
        ]}}).to_string();
        let clean_v2 = json!({"payload":{"records":[
            {"kind":"conversation","content":{"messages":[
                {"id":"m1","delivery":"COMPLETE","blocks":[{"kind":"TEXT","text":"portable"}]}
            ]}},
            {"kind":"safe_settings","id":"profile-interests","content":{"portableConversationFormat":2}}
        ]}}).to_string();
        let v3_without_portable_model_usage = json!({"payload":{"records":[
            {"kind":"conversation","content":{"messages":[
                {"id":"m1","delivery":"COMPLETE","blocks":[{"kind":"TEXT","text":"portable"}]}
            ]}},
            {"kind":"safe_settings","id":"profile-interests","content":{"portableConversationFormat":3}}
        ]}}).to_string();
        let clean_v4 = json!({"payload":{"records":[
            {"kind":"conversation","content":{"messages":[
                {"id":"m1","delivery":"COMPLETE","blocks":[{"kind":"TEXT","text":"portable"}]}
            ]}},
            {"kind":"safe_settings","id":"profile-interests","content":{"portableConversationFormat":4}}
        ]}}).to_string();
        let unmarked = json!({"payload":{"records":[{"kind":"conversation","content":{"messages":[
            {"id":"m1","delivery":"COMPLETE","blocks":[{"kind":"TEXT","text":"portable"}]}
        ]}}]}})
        .to_string();
        let retry = json!({"payload":{"records":[{"kind":"conversation","content":{"messages":[
            {"id":"m1","delivery":"COMPLETE"}, {"id":"m2","delivery":"FAILED"}
        ]}}]}})
        .to_string();
        let duplicate = json!({"payload":{"records":[{"kind":"conversation","content":{"messages":[
            {"id":"m1","delivery":"COMPLETE"}, {"id":"m1","delivery":"COMPLETE"}
        ]}}]}})
        .to_string();
        let tool_result = json!({"payload":{"records":[{"kind":"conversation","content":{"messages":[
            {"id":"m1","delivery":"COMPLETE","blocks":[{"kind":"TOOL_RESULT","text":"local-only"}]}
        ]}}]}}).to_string();
        assert!(remote_requires_current_portable_rewrite(
            &v1_without_portable_model_usage
        ));
        assert!(remote_requires_current_portable_rewrite(&clean_v2));
        assert!(remote_requires_current_portable_rewrite(
            &v3_without_portable_model_usage
        ));
        assert!(!remote_requires_current_portable_rewrite(&clean_v4));
        assert!(remote_requires_current_portable_rewrite(&unmarked));
        assert!(remote_requires_current_portable_rewrite(&retry));
        assert!(remote_requires_current_portable_rewrite(&duplicate));
        assert!(remote_requires_current_portable_rewrite(&tool_result));
    }

    #[test]
    fn cloud_list_presentation_document_id_is_valid_but_never_a_conversation() {
        assert_eq!(
            envelope_document_id(
                &json!({"documentId":CLOUD_LIST_PRESENTATION_DOCUMENT_ID}).to_string(),
            )
            .unwrap(),
            CLOUD_LIST_PRESENTATION_DOCUMENT_ID,
        );
        assert_eq!(
            envelope_document_id(&json!({"documentId":"conversation-0123456789"}).to_string())
                .unwrap(),
            "conversation-0123456789",
        );
    }

    #[test]
    fn portable_model_usage_keeps_model_and_settled_amount_without_provider_route() {
        let usage = portable_model_usage(&json!({
            "modelSnapshot":{"providerId":"OPENROUTER","modelId":"openai/gpt-6-astra","displayName":"GPT-6 Astra"},
            "usage":{"inputTokens":12,"outputTokens":34},
            "chargeMicros":56,"currencyCode":"USD","costSource":"PROVIDER_RESPONSE"
        })).unwrap();
        assert_eq!(usage["modelId"], "openai/gpt-6-astra");
        assert_eq!(usage["costTotalMicros"], 56);
        assert!(usage.get("providerId").is_none());
        let legacy = portable_model_usage(&json!({
            "modelSnapshot":{"modelId":"anthropic/claude-sonnet-5","displayName":"Sonnet 5"},
            "usage":{"inputTokens":null,"outputTokens":null},
            "estimatedUsage":{"inputTokens":21,"outputTokens":2115}
        })).unwrap();
        assert_eq!(legacy["inputTokens"], 21);
        assert_eq!(legacy["outputTokens"], 2115);
    }

    #[test]
    fn existing_conversation_restore_updates_the_favorite_table_used_by_the_ui() {
        let mut connection = database();
        connection.execute("INSERT INTO workspaces(id,title,semantic_hash,package_hash,created_at) VALUES('workspace-favorite','收藏','old','old','2026-09-01T00:00:00Z')", []).unwrap();
        let conversation = json!({"id":"conversation-favorite","title":"收藏","revision":1,"currentLeafId":"message-favorite","messages":[
            {"id":"message-favorite","parentId":null,"ordinal":0,"role":"user","delivery":"COMPLETE","revision":1,"blocks":[{"kind":"TEXT","text":"合成内容"}]}
        ]});
        connection.execute("INSERT INTO workspace_exchange(workspace_id,exchange_json) VALUES('workspace-favorite',?1)", [json!({"conversations":[conversation.clone()]}).to_string()]).unwrap();
        for (favorited, expected) in [(true, 1_i64), (false, 0_i64)] {
            let mut remote = conversation.as_object().unwrap().clone();
            remote.insert("favorited".into(), json!(favorited));
            let transaction = connection.transaction().unwrap();
            merge_newer_remote_conversation(&transaction, "workspace-favorite", "conversation-favorite", &remote, 2).unwrap();
            transaction.commit().unwrap();
            let actual: i64 = connection.query_row("SELECT count(*) FROM desktop_conversation_favorites WHERE workspace_id='workspace-favorite' AND conversation_id='conversation-favorite'", [], |row| row.get(0)).unwrap();
            assert_eq!(actual, expected);
        }
    }

    fn sync_search_lifecycle_fixture()->(Connection,Value) {
        let c=database();c.execute_batch("PRAGMA foreign_keys=ON").unwrap();crate::desktop_ordinary_chat_v1::migrate(&c).unwrap();crate::attachment_search_checkpoint_v1::migrate(&c).unwrap();
        let mut exchange:Value=serde_json::from_str(include_str!("../../../protocol/fixtures/nfai.exchange.v1.golden.json")).unwrap();for key in ["projects","knowledge","memory","relations"] {exchange[key]=json!([]);}
        exchange["conversations"]=json!([{"id":"sync-life","projectId":null,"title":"原题","titleRevision":1,"revision":1,"pinned":false,"archived":false,"createdAt":"2026-09-01T00:00:00Z","updatedAt":"2026-09-01T00:00:00Z","currentLeafId":"reply","messages":[
            {"id":"source","parentId":null,"ordinal":0,"role":"user","delivery":"COMPLETE","revision":1,"createdAt":"2026-09-01T00:00:00Z","blocks":[{"kind":"TEXT","ordinal":0,"text":"原合成材料"},{"kind":"ASSET_REF","ordinal":1,"asset":{"id":"original-local-material","sha256":"synthetic-local-hash","displayName":"本机材料保持.md"}}]},
            {"id":"reply","parentId":"source","ordinal":1,"role":"assistant","delivery":"COMPLETE","revision":1,"createdAt":"2026-09-01T00:00:01Z","blocks":[{"kind":"TEXT","ordinal":0,"text":"原回答和费用保持"}]}
        ]}]);
        c.execute("INSERT INTO workspaces VALUES('sync-life-workspace','合成生命周期','before','package','2026-09-01T00:00:00Z')",[]).unwrap();c.execute("INSERT INTO workspace_exchange VALUES('sync-life-workspace',?1)",[exchange.to_string()]).unwrap();
        for (attempt,state) in [("active","RUNNING"),("complete","COMPLETED")] {
            c.execute("INSERT INTO desktop_ordinary_chat_attempts(attempt_id,workspace_id,conversation_id,user_message_id,assistant_message_id,idempotency_key,request_fingerprint,state,input_tokens,output_tokens,charge_micros,created_at_ms,updated_at_ms,terminal_at_ms) VALUES(?1,'sync-life-workspace','sync-life','source','reply',?1,'original-request',?2,17,5,6,1000,1000,1000)",params![attempt,state]).unwrap();
            let entry=json!({"text":"A1 合成旧证据","footer":"","model":"original-model","created_at":1000,"invocation":"original-call"});
            c.execute("INSERT INTO desktop_attachment_search_checkpoints VALUES(?1,'scope','A1',?2,1000)",params![attempt,entry.to_string()]).unwrap();c.execute("INSERT INTO desktop_attachment_search_coverage VALUES(?1,'scope',8,4,0,1000)",[attempt]).unwrap();
        }
        let mut remote=exchange["conversations"][0].clone();remote["title"]=json!("新标题");remote["titleRevision"]=json!(2);remote["revision"]=json!(2);remote["updatedAt"]=json!("2026-09-01T00:01:00Z");remote["messages"][0]["blocks"]=json!([{"kind":"TEXT","ordinal":0,"text":"原合成材料"}]);(c,remote)
    }
    fn sync_search_lifecycle_merge(c:&mut Connection,remote:&Value,upload:bool)->Result<(),String> {
        let tx=c.transaction().unwrap();let result=if upload {merge_remote_additions_for_local_commit(&tx,"sync-life-workspace","sync-life",remote.as_object().unwrap(),2)}else{merge_newer_remote_conversation(&tx,"sync-life-workspace","sync-life",remote.as_object().unwrap(),2)};result?;tx.commit().map_err(|e|e.to_string())
    }
    fn assert_sync_search_lifecycle(upload:bool) {
        for changed in [false,true] {
            let (mut c,mut remote)=sync_search_lifecycle_fixture();let complete=|c:&Connection|c.query_row("SELECT state,idempotency_key,input_tokens,output_tokens,charge_micros,updated_at_ms,terminal_at_ms FROM desktop_ordinary_chat_attempts WHERE attempt_id='complete'",[],|r|Ok((r.get::<_,String>(0)?,r.get::<_,String>(1)?,r.get::<_,i64>(2)?,r.get::<_,i64>(3)?,r.get::<_,i64>(4)?,r.get::<_,i64>(5)?,r.get::<_,i64>(6)?))).unwrap();let original=complete(&c);
            if changed {remote["messages"][0]["blocks"][0]["text"]=json!("云端更新合成材料");remote["messages"][0]["revision"]=json!(2);}
            sync_search_lifecycle_merge(&mut c,&remote,upload).unwrap();
            for table in ["desktop_attachment_search_checkpoints","desktop_attachment_search_coverage"] {assert_eq!(c.query_row(&format!("SELECT COUNT(*) FROM {table}"),[],|r|r.get::<_,i64>(0)).unwrap(),if changed {0}else{2},"changed={changed}, table={table}");}
            let state:(String,Option<String>)=c.query_row("SELECT state,safe_error_code FROM desktop_ordinary_chat_attempts WHERE attempt_id='active'",[],|r|Ok((r.get(0)?,r.get(1)?))).unwrap();assert_eq!(state,if changed {("CANCELLED".into(),Some("SOURCE_MESSAGE_CHANGED".into()))}else{("RUNNING".into(),None)});assert_eq!(complete(&c),original);
            let encoded:String=c.query_row("SELECT exchange_json FROM workspace_exchange WHERE workspace_id='sync-life-workspace'",[],|r|r.get(0)).unwrap();let stored:Value=serde_json::from_str(&encoded).unwrap();assert_eq!(stored["conversations"][0]["messages"][0]["blocks"][1]["asset"]["id"],"original-local-material");assert_eq!(stored["conversations"][0]["messages"][1]["blocks"][0]["text"],"原回答和费用保持");assert_eq!(stored["conversations"][0]["messages"][0]["blocks"][0]["text"],if changed {"云端更新合成材料"}else{"原合成材料"});
            if changed {assert!(crate::attachment_search_checkpoint_v1::coverage(&c,"active","scope",8,4,false).is_err());}
            assert!(c.prepare("PRAGMA foreign_key_check").unwrap().query([]).unwrap().next().unwrap().is_none());
        }
    }
    #[test]
    fn sync_search_lifecycle_cloud_read_invalidates_only_changed_source_and_preserves_history() {assert_sync_search_lifecycle(false);}
    #[test]
    fn sync_search_lifecycle_local_commit_invalidates_only_changed_source_and_preserves_history() {assert_sync_search_lifecycle(true);}
    #[test]
    fn sync_search_lifecycle_cleanup_failure_rolls_back_cloud_merge_and_attempts() {
        for upload in [false,true] {
            let (mut c,mut remote)=sync_search_lifecycle_fixture();let before:String=c.query_row("SELECT exchange_json FROM workspace_exchange WHERE workspace_id='sync-life-workspace'",[],|r|r.get(0)).unwrap();remote["messages"][0]["blocks"][0]["text"]=json!("云端更新合成材料");remote["messages"][0]["revision"]=json!(2);
            c.execute_batch("CREATE TRIGGER owned_sync_lifecycle_fault BEFORE DELETE ON desktop_attachment_search_checkpoints BEGIN SELECT RAISE(ABORT,'owned sync cleanup failure');END;").unwrap();assert!(sync_search_lifecycle_merge(&mut c,&remote,upload).is_err());assert_eq!(c.query_row("SELECT exchange_json FROM workspace_exchange WHERE workspace_id='sync-life-workspace'",[],|r|r.get::<_,String>(0)).unwrap(),before);assert_eq!(c.query_row("SELECT state FROM desktop_ordinary_chat_attempts WHERE attempt_id='active'",[],|r|r.get::<_,String>(0)).unwrap(),"RUNNING");
            for table in ["desktop_attachment_search_checkpoints","desktop_attachment_search_coverage"] {assert_eq!(c.query_row(&format!("SELECT COUNT(*) FROM {table}"),[],|r|r.get::<_,i64>(0)).unwrap(),2);}
            c.execute_batch("DROP TRIGGER owned_sync_lifecycle_fault").unwrap();sync_search_lifecycle_merge(&mut c,&remote,upload).unwrap();assert_eq!(c.query_row("SELECT COUNT(*) FROM desktop_attachment_search_checkpoints",[],|r|r.get::<_,i64>(0)).unwrap(),0);
        }
    }


    fn assert_sync_material_block_roundtrip(upload: bool) {
        for (text_case, with_tool) in [("same",false),("changed",false),("short",false),("long",false),("same",true),("changed",true),("short",true),("long",true)] {
            let changed = text_case != "same";
            let mut c = database();
            let mut exchange: Value = serde_json::from_str(include_str!("../../../protocol/fixtures/nfai.exchange.v1.golden.json")).unwrap();
            let bytes = b"Synthetic material body\r\n";
            let hash = crate::sha256(bytes);
            let mut blocks = json!([
                {"kind":"TEXT","ordinal":0,"text":"正文前段"},
                {"kind":"ASSET_REF","ordinal":1,"asset":{"id":"asset-lf-name","entry":format!("assets/{hash}"),"mimeType":"text/plain","displayName":"notes\nNAME_ONLY_2099.txt","byteCount":bytes.len(),"sha256":hash,"classification":"NORMAL"}},
                {"kind":"TEXT","ordinal":2,"text":"正文中段"},
                {"kind":"TOOL_RESULT","ordinal":3,"text":"local-only-runtime-evidence"},
                {"kind":"TEXT","ordinal":4,"text":"正文后段"}
            ]);
            if !with_tool {blocks.as_array_mut().unwrap().remove(3);for (i,b) in blocks.as_array_mut().unwrap().iter_mut().enumerate(){b["ordinal"]=json!(i);}}
            for group in ["projects","knowledge","memory","relations"] {exchange[group]=json!([]);}
            exchange["conversations"] = json!([{"id":"conversation-material","projectId":null,"title":"原标题","titleRevision":1,"pinned":false,"archived":false,"revision":1,"createdAt":"2026-09-01T00:00:00Z","updatedAt":"2026-09-01T00:00:00Z","currentLeafId":"message-material","messages":[
                {"id":"message-material","parentId":null,"ordinal":0,"role":"user","delivery":"COMPLETE","revision":1,"createdAt":"2026-09-01T00:00:00Z","blocks":blocks}
            ]}]);
            // TOOL_RESULT is intentionally local-only and is removed before a v1 export,
            // while its place in the local message must survive the sync merge.
            c.execute("INSERT INTO workspaces VALUES('workspace-material','合成材料','before','package','2026-09-01T00:00:00Z')",[]).unwrap();
            c.execute("INSERT INTO workspace_exchange VALUES('workspace-material',?1)",[exchange.to_string()]).unwrap();
            let mut remote=exchange["conversations"][0].clone();
            remote["title"]=json!("云端新标题");remote["titleRevision"]=json!(2);remote["revision"]=json!(2);remote["updatedAt"]=json!("2026-09-01T00:01:00Z");
            remote["messages"][0]["blocks"]=json!([
                {"kind":"TEXT","text":if changed {"更新前段"} else {"正文前段"}},
                {"kind":"TEXT","text":"正文中段"},
                {"kind":"TEXT","text":"正文后段"}
            ]);
            if text_case == "short" {remote["messages"][0]["blocks"].as_array_mut().unwrap().truncate(1);}
            if text_case == "long" {remote["messages"][0]["blocks"].as_array_mut().unwrap().push(json!({"kind":"TEXT","text":"新增尾段"}));}
            remote["messages"][0]["revision"]=json!(2);
            let tx=c.transaction().unwrap();
            let result=if upload {merge_remote_additions_for_local_commit(&tx,"workspace-material","conversation-material",remote.as_object().unwrap(),2)} else {merge_newer_remote_conversation(&tx,"workspace-material","conversation-material",remote.as_object().unwrap(),2)};
            assert_eq!(result.unwrap(),ExistingRemoteConversationMerge::Updated);tx.commit().unwrap();
            let stored:String=c.query_row("SELECT exchange_json FROM workspace_exchange WHERE workspace_id='workspace-material'",[],|r|r.get(0)).unwrap();
            let mut restored:Value=serde_json::from_str(&stored).unwrap();
            let actual=restored["conversations"][0]["messages"][0]["blocks"].clone();
            let mut expected=blocks.clone();if changed {expected[0]["text"]=json!("更新前段");}
            if text_case == "short" {expected.as_array_mut().unwrap().retain(|b|b["kind"]!="TEXT" || b["ordinal"]==0);}
            if text_case == "long" {expected.as_array_mut().unwrap().push(json!({"kind":"TEXT","text":"新增尾段"}));}
            for (i,b) in expected.as_array_mut().unwrap().iter_mut().enumerate(){b["ordinal"]=json!(i);}
            assert_eq!(actual,expected,"sync must retain local material placement/name and local runtime blocks while applying cloud text");
            let (payload,_)=conversation_payload(&c,"workspace-material","conversation-material","document-material",2).unwrap();
            let wire=payload.to_string();assert!(!wire.contains("NAME_ONLY_2099")&&!wire.contains("local-only-runtime-evidence")&&!wire.contains(&hash));
            assert_eq!(payload["records"][0]["content"]["messages"][0]["blocks"],remote["messages"][0]["blocks"]);
            if with_tool {continue;}
            restored["export"]["id"]=json!(format!("export-sync-{}-{text_case}",if upload {"upload"} else {"read"}));
            let portable=crate::portable_exchange_v1(&restored).unwrap();
            let assets=BTreeMap::from([(format!("assets/{hash}"),bytes.to_vec())]);
            let package=crate::package_exchange(&portable,&assets).unwrap();
            if let Some(output)=std::env::var_os("NANFENG_AI_SYNC_MATERIAL_TEST_ARTIFACT") {
                let output=PathBuf::from(output);assert_eq!(std::fs::read(output.join(".synthetic-sync-material-v1")).unwrap(),b"synthetic-sync-material-v1");
                let name=format!("{}-{text_case}.nfai-exchange",if upload {"local-commit"} else {"cloud-read"});
                let path=output.join(name);assert!(!path.exists());std::fs::write(path,&package).unwrap();
            }
            let roundtrip=crate::preflight_package(package).unwrap();
            assert_eq!(roundtrip.exchange["conversations"][0]["messages"][0]["blocks"],portable["conversations"][0]["messages"][0]["blocks"]);
        }
    }

    #[test]
    fn sync_material_blocks_cloud_read_keeps_placement_and_exchange() {assert_sync_material_block_roundtrip(false);}

    #[test]
    fn sync_material_blocks_local_commit_keeps_placement_and_exchange() {assert_sync_material_block_roundtrip(true);}

    #[test]
    fn conversation_payload_keeps_text_only_and_never_uploads_attachment_metadata() {
        let c = database();
        c.execute(
            "INSERT INTO workspaces(id,title,semantic_hash,package_hash,created_at) VALUES(?1,?2,?3,?4,?5)",
            params!["workspace-mixed", "混合内容", "semantic-mixed", "package-mixed", "2026-09-01T00:00:00Z"],
        )
        .unwrap();
        c.execute(
            "INSERT INTO workspace_exchange VALUES('workspace-mixed',?1)",
            [json!({"conversations":[{"id":"conversation-mixed","title":"混合内容","revision":1,"currentLeafId":"assistant","messages":[
                {"id":"user","parentId":null,"ordinal":0,"role":"user","delivery":"COMPLETE","blocks":[
                    {"kind":"TEXT","text":"查看附件"},
                    {"kind":"ASSET_REF","asset":{"id":"asset-1","displayName":"资料.pdf","mimeType":"application/pdf","byteCount":42,"privatePath":"/private/local/资料.pdf"}}
                ]},
                {"id":"assistant","parentId":"user","ordinal":1,"role":"assistant","delivery":"COMPLETE","blocks":[{"kind":"TOOL_RESULT","text":"已完成解析"}]},
                {"id":"after-tool","parentId":"assistant","ordinal":2,"role":"assistant","delivery":"COMPLETE","blocks":[{"kind":"TEXT","text":"解析后的完整回答"}]}
            ]}]}).to_string()],
        )
        .unwrap();
        c.execute(
            "INSERT INTO desktop_conversation_favorites(workspace_id,conversation_id,favorited_at_ms) VALUES(?1,?2,?3)",
            params!["workspace-mixed", "conversation-mixed", 1_i64],
        )
        .unwrap();
        let (payload, _) = conversation_payload(
            &c,
            "workspace-mixed",
            "conversation-mixed",
            "conversation-mixed-document",
            1,
        )
        .unwrap();
        assert_eq!(payload["records"][0]["content"]["surface"], "CHAT");
        let blocks = payload["records"][0]["content"]["messages"][0]["blocks"]
            .as_array()
            .unwrap();
        assert_eq!(blocks.len(), 1);
        assert_eq!(blocks[0]["kind"], "TEXT");
        let messages = payload["records"][0]["content"]["messages"]
            .as_array()
            .unwrap();
        assert_eq!(messages.len(), 2);
        assert_eq!(messages[1]["id"], "after-tool");
        assert_eq!(messages[1]["parentId"], "user");
        assert_eq!(
            payload["records"][0]["content"]["favorited"],
            Value::Bool(true)
        );
        assert_eq!(messages.len(), 2);
        assert_eq!(messages[0]["id"], "user");
        assert!(messages
            .iter()
            .flat_map(|message| message["blocks"].as_array().unwrap())
            .all(|block| { block["kind"].as_str() == Some("TEXT") }));
        assert!(!payload.to_string().contains("资料.pdf"));
        assert!(sync_v1::seal_direct(payload).is_ok());
        c.execute_batch("CREATE TABLE desktop_data_area_v1(id INTEGER PRIMARY KEY,area TEXT NOT NULL,split_done INTEGER NOT NULL); INSERT INTO desktop_data_area_v1 VALUES(1,'WORK',1);").unwrap();
        let (work_payload, _) = conversation_payload(
            &c,
            "workspace-mixed",
            "conversation-mixed",
            "work-conversation-mixed-document",
            1,
        )
        .unwrap();
        assert_eq!(work_payload["records"][0]["content"]["surface"], "WORK");
    }
    #[test]
    fn conversation_payload_syncs_completed_prefix_without_mutating_terminal_failure() {
        let c = database();
        c.execute(
            "INSERT INTO workspace_exchange VALUES('workspace-terminal',?1)",
            [json!({"conversations":[{"id":"conversation-terminal","title":"模型终态异常","revision":1,"currentLeafId":"timeout","messages":[
                {"id":"user","parentId":null,"ordinal":0,"role":"user","delivery":"COMPLETE","blocks":[{"kind":"TEXT","text":"继续回答"}]},
                {"id":"answer","parentId":"user","ordinal":1,"role":"assistant","delivery":"COMPLETE","blocks":[{"kind":"TEXT","text":"已完成的回答"}]},
                {"id":"retry","parentId":"answer","ordinal":2,"role":"user","delivery":"COMPLETE","blocks":[{"kind":"TEXT","text":"补充一下"}]},
                {"id":"timeout","parentId":"retry","ordinal":3,"role":"assistant","delivery":"UNKNOWN","safeErrorCode":"TIMEOUT","blocks":[{"kind":"TEXT","text":"未确认的部分文本"}]}
            ]}]}).to_string()],
        )
        .unwrap();
        let (payload, _) = conversation_payload(
            &c,
            "workspace-terminal",
            "conversation-terminal",
            "conversation-terminal-document",
            1,
        )
        .unwrap();
        let content = &payload["records"][0]["content"];
        assert_eq!(content["messages"].as_array().unwrap().len(), 3);
        assert_eq!(content["currentLeafId"], "retry");
        assert!(content["messages"]
            .as_array()
            .unwrap()
            .iter()
            .all(|message| message["delivery"] == "COMPLETE"));
        let local: Value = c
            .query_row(
                "SELECT exchange_json FROM workspace_exchange WHERE workspace_id='workspace-terminal'",
                [],
                |row| row.get(0),
            )
            .map(|raw: String| serde_json::from_str(&raw).unwrap())
            .unwrap();
        assert_eq!(
            local["conversations"][0]["messages"]
                .as_array()
                .unwrap()
                .len(),
            4
        );
        assert_eq!(
            local["conversations"][0]["messages"][3]["delivery"],
            "UNKNOWN"
        );
    }
    #[test]
    fn android_collection_state_survives_desktop_normalization() {
        let mut conversation = serde_json::from_value(json!({
            "title":"跨端状态", "currentLeafMessageId":"m1", "createdAtEpochMs":1000,
            "updatedAtEpochMs":2000, "surface":"CHAT", "pinnedAtEpochMs":1200,
            "archivedAtEpochMs":null, "favoritedAtEpochMs":1500,
            "nodes":[{"id":"m1","parentMessageId":null,"siblingPosition":0,"role":"USER","createdAtEpochMs":1000,"deliveryState":"COMPLETE","revision":1,"revisesMessageId":null,"text":["正文"]}]
        }))
        .unwrap();

        normalize_android_selected_conversation(&mut conversation).unwrap();

        assert_eq!(conversation.get("pinned"), Some(&Value::Bool(true)));
        assert_eq!(conversation.get("archived"), Some(&Value::Bool(false)));
        assert_eq!(conversation.get("favorited"), Some(&Value::Bool(true)));
        assert!(conversation.get("favoritedAtEpochMs").is_none());
    }
    #[test]
    fn unknown_commit_is_not_silently_retried() {
        struct Readback(RemoteEnvelope);
        impl CloudGateway for Readback {
            fn read(&self, _: &str) -> Result<Option<RemoteEnvelope>, CloudFailure> {
                Ok(Some(self.0.clone()))
            }
            fn commit(&self, _: &str, _: u64, _: &str) -> Result<(u64, String), CloudFailure> {
                panic!("reconcile must not resend")
            }
        }
        let mut c = database();
        let keys = Mem(RefCell::new(BTreeMap::new()));
        let session = Session {
            user_id: "verified-user-789".into(),
            email: "user@example.invalid".into(),
            display_name: None,
            avatar_url: None,
            access_token: "a".into(),
            refresh_token: "r".into(),
            expires_at_epoch_seconds: 9999999999,
        };
        save_session(&keys, &session).unwrap();
        let account = sync_state_v1::account_ref(&session.user_id).unwrap();
        c.execute("INSERT INTO desktop_cloud_account_state VALUES(?1,?2,?3,NULL,NULL,'FAILED',0,1,'safe-salt',1,NULL,'COMMIT_RESULT_UNKNOWN','device-safe',1,1,NULL,NULL,NULL)",params![account,session.user_id,session.email]).unwrap();
        c.execute("INSERT INTO workspace_exchange VALUES('workspace-safe',?1)",[json!({"conversations":[{"id":"conversation-safe","title":"安全对话","revision":1,"currentLeafId":"message-safe","messages":[{"id":"message-safe","parentId":null,"ordinal":0,"role":"user","delivery":"COMPLETE","createdAt":"2026-09-01T00:00:00Z","revision":1,"blocks":[{"kind":"TEXT","text":"安全正文"}]}]}]}).to_string()]).unwrap();
        c.execute("INSERT INTO desktop_sync_jobs VALUES('job-safe',?1,'workspace-safe','conversation-safe','document-safe','UNKNOWN','payload-safe',0,1,NULL,'COMMIT_RESULT_UNKNOWN',1,1)",[&account]).unwrap();
        let (payload, _) = conversation_payload(&c, "workspace-safe", "conversation-safe", "document-safe", 1).unwrap();
        let envelope = sync_v1::seal_direct(payload).unwrap();
        let parsed: Value = serde_json::from_str(&envelope).unwrap();
        let payload_hash = parsed["payloadHash"].as_str().unwrap().to_owned();
        c.execute("UPDATE desktop_sync_jobs SET payload_hash=?1 WHERE job_id='job-safe'", [&payload_hash]).unwrap();
        let cloud = Readback(RemoteEnvelope { revision: 1, payload_hash, envelope });
        let receipt =
            reconcile_unknown_commit(&mut c, &keys, &cloud, "workspace-safe", "conversation-safe")
                .unwrap();
        assert_eq!(receipt.status, "SYNCED_AFTER_RECONCILE");
        assert_eq!(
            c.query_row(
                "SELECT stage FROM desktop_sync_jobs WHERE job_id='job-safe'",
                [],
                |row| row.get::<_, String>(0)
            )
            .unwrap(),
            "COMPLETED"
        );
    }

    #[test]
    fn matching_unverified_readback_does_not_complete_unknown_commit() {
        struct Readback(RemoteEnvelope);
        impl CloudGateway for Readback {
            fn read(&self, _: &str) -> Result<Option<RemoteEnvelope>, CloudFailure> {
                Ok(Some(self.0.clone()))
            }
            fn commit(&self, _: &str, _: u64, _: &str) -> Result<(u64, String), CloudFailure> {
                panic!("reconcile must not resend")
            }
        }
        let mut c = database();
        let keys = Mem(RefCell::new(BTreeMap::new()));
        let session = Session {
            user_id: "verified-user-789".into(),
            email: "user@example.invalid".into(),
            display_name: None,
            avatar_url: None,
            access_token: "a".into(),
            refresh_token: "r".into(),
            expires_at_epoch_seconds: 9999999999,
        };
        save_session(&keys, &session).unwrap();
        let account = sync_state_v1::account_ref(&session.user_id).unwrap();
        c.execute("INSERT INTO desktop_cloud_account_state VALUES(?1,?2,?3,NULL,NULL,'FAILED',0,1,'safe-salt',1,NULL,'COMMIT_RESULT_UNKNOWN','device-safe',1,1,NULL,NULL,NULL)",params![account,session.user_id,session.email]).unwrap();
        c.execute("INSERT INTO workspace_exchange VALUES('workspace-safe',?1)",[json!({"conversations":[{"id":"conversation-safe","title":"安全对话","revision":1,"currentLeafId":"message-safe","messages":[{"id":"message-safe","parentId":null,"ordinal":0,"role":"user","delivery":"COMPLETE","createdAt":"2026-09-01T00:00:00Z","revision":1,"blocks":[{"kind":"TEXT","text":"安全正文"}]}]}]}).to_string()]).unwrap();
        c.execute("INSERT INTO desktop_sync_jobs VALUES('job-safe',?1,'workspace-safe','conversation-safe','document-safe','UNKNOWN','payload-safe',0,1,NULL,'COMMIT_RESULT_UNKNOWN',1,1)",[&account]).unwrap();
        let cloud = Readback(RemoteEnvelope {
            revision: 1,
            payload_hash: "payload-safe".into(),
            envelope: "{}".into(),
        });
        assert!(reconcile_unknown_commit(&mut c, &keys, &cloud, "workspace-safe", "conversation-safe").is_err());
        assert_eq!(c.query_row("SELECT COUNT(*) FROM desktop_selected_conversation_sync", [], |row| row.get::<_,i64>(0)).unwrap(), 0);
        assert_eq!(
            c.query_row(
                "SELECT stage FROM desktop_sync_jobs WHERE job_id='job-safe'",
                [],
                |row| row.get::<_, String>(0)
            )
            .unwrap(),
            "UNKNOWN"
        );
    }


    #[test]
    fn missing_previous_remote_preserves_unknown_attempt_and_stops_selection() {
        struct Readback(RemoteEnvelope);
        impl CloudGateway for Readback {
            fn read(&self, _: &str) -> Result<Option<RemoteEnvelope>, CloudFailure> {
                Ok(None)
            }
            fn commit(&self, _: &str, _: u64, _: &str) -> Result<(u64, String), CloudFailure> {
                panic!("reconcile must not resend")
            }
        }
        let mut c = database();
        let keys = Mem(RefCell::new(BTreeMap::new()));
        let session = Session {
            user_id: "verified-user-789".into(),
            email: "user@example.invalid".into(),
            display_name: None,
            avatar_url: None,
            access_token: "a".into(),
            refresh_token: "r".into(),
            expires_at_epoch_seconds: 9999999999,
        };
        save_session(&keys, &session).unwrap();
        let account = sync_state_v1::account_ref(&session.user_id).unwrap();
        c.execute("INSERT INTO desktop_cloud_account_state VALUES(?1,?2,?3,NULL,NULL,'FAILED',0,1,'safe-salt',1,NULL,'COMMIT_RESULT_UNKNOWN','device-safe',1,1,NULL,NULL,NULL)",params![account,session.user_id,session.email]).unwrap();
        c.execute("INSERT INTO workspace_exchange VALUES('workspace-safe',?1)",[json!({"conversations":[{"id":"conversation-safe","title":"安全对话","revision":1,"currentLeafId":"message-safe","messages":[{"id":"message-safe","parentId":null,"ordinal":0,"role":"user","delivery":"COMPLETE","createdAt":"2026-09-01T00:00:00Z","revision":1,"blocks":[{"kind":"TEXT","text":"安全正文"}]}]}]}).to_string()]).unwrap();
        c.execute("INSERT INTO desktop_sync_jobs VALUES('job-safe',?1,'workspace-safe','conversation-safe','document-safe','UNKNOWN','payload-safe',1,1,NULL,'COMMIT_RESULT_UNKNOWN',1,1)",[&account]).unwrap();
        c.execute("INSERT INTO desktop_selected_conversation_sync VALUES(?1,'workspace-safe','conversation-safe','document-safe',1,'prior-hash','prior-local',1)",[&account]).unwrap();
        let cloud = Readback(RemoteEnvelope {
            revision: 1,
            payload_hash: "payload-safe".into(),
            envelope: "{}".into(),
        });
        assert!(reconcile_unknown_commit(&mut c, &keys, &cloud, "workspace-safe", "conversation-safe").is_err());
        assert_eq!(c.query_row("SELECT COUNT(*) FROM desktop_selected_conversation_sync", [], |row| row.get::<_,i64>(0)).unwrap(), 0);
        assert_eq!(
            c.query_row(
                "SELECT stage FROM desktop_sync_jobs WHERE job_id='job-safe'",
                [],
                |row| row.get::<_, String>(0)
            )
            .unwrap(),
            "UNKNOWN"
        );
    }



    #[test]
    fn unverified_remote_does_not_supersede_an_unknown_commit() {
        struct Cloud(RefCell<RemoteEnvelope>);
        impl CloudGateway for Cloud {
            fn read(&self, _: &str) -> Result<Option<RemoteEnvelope>, CloudFailure> {
                Ok(Some(self.0.borrow().clone()))
            }
            fn commit(
                &self,
                _: &str,
                expected: u64,
                envelope: &str,
            ) -> Result<(u64, String), CloudFailure> {
                let value: Value = serde_json::from_str(envelope).unwrap();
                let revision = value["revision"].as_u64().unwrap();
                assert_eq!(revision, expected + 1);
                let hash = value["payloadHash"].as_str().unwrap().to_owned();
                *self.0.borrow_mut() = RemoteEnvelope {
                    revision,
                    payload_hash: hash.clone(),
                    envelope: envelope.into(),
                };
                Ok((revision, hash))
            }
        }
        let mut c = database();
        let keys = Mem(RefCell::new(BTreeMap::new()));
        let session = Session {
            user_id: "verified-user-unknown-merge".into(),
            email: "unknown-merge@example.invalid".into(),
            display_name: None,
            avatar_url: None,
            access_token: "a".into(),
            refresh_token: "r".into(),
            expires_at_epoch_seconds: 9_999_999_999,
        };
        save_session(&keys, &session).unwrap();
        let account = sync_state_v1::account_ref(&session.user_id).unwrap();
        c.execute("INSERT INTO desktop_cloud_account_state VALUES(?1,?2,?3,NULL,NULL,'VERIFYING',0,1,'safe-salt',1,NULL,'COMMIT_RESULT_UNKNOWN','device-safe',1,1,NULL,NULL,NULL)",params![account,session.user_id,session.email]).unwrap();
        c.execute("INSERT INTO workspaces(id,title,semantic_hash,package_hash,created_at) VALUES('workspace-unknown','测试','before','package','2026-09-01T00:00:00Z')", []).unwrap();
        c.execute("INSERT INTO workspace_exchange VALUES('workspace-unknown',?1)",[json!({"conversations":[{"id":"conversation-unknown","title":"完整对话","revision":1,"currentLeafId":"message","messages":[{"id":"message","parentId":null,"ordinal":0,"role":"user","delivery":"COMPLETE","createdAt":"2026-09-01T00:00:00Z","revision":1,"blocks":[{"kind":"TEXT","text":"正文"}]}]}]}).to_string()]).unwrap();
        c.execute("INSERT INTO desktop_sync_jobs VALUES('job-unknown',?1,'workspace-unknown','conversation-unknown','conversation-unknown-document','UNKNOWN','old-hash',0,1,NULL,'COMMIT_RESULT_UNKNOWN',1,1)",[&account]).unwrap();
        let cloud = Cloud(RefCell::new(RemoteEnvelope {
            revision:1,payload_hash:"unverified-change".into(),envelope:"{}".into(),
        }));
        let rejected = reconcile_unknown_commit(
            &mut c,
            &keys,
            &cloud,
            "workspace-unknown",
            "conversation-unknown",
        )
        .is_err();
        assert!(rejected);
        let old_stage: String = c
            .query_row(
                "SELECT stage FROM desktop_sync_jobs WHERE job_id='job-unknown'",
                [],
                |row| row.get(0),
            )
            .unwrap();
        assert_eq!(old_stage, "UNKNOWN");
        assert_eq!(cloud.0.borrow().revision, 1);
    }
    #[test]
    fn changed_remote_after_unknown_commit_is_merged_and_resynced() {
        struct Cloud(RefCell<RemoteEnvelope>);
        impl CloudGateway for Cloud {
            fn read(&self, _: &str) -> Result<Option<RemoteEnvelope>, CloudFailure> {
                Ok(Some(self.0.borrow().clone()))
            }
            fn commit(
                &self,
                _: &str,
                expected: u64,
                envelope: &str,
            ) -> Result<(u64, String), CloudFailure> {
                let value: Value = serde_json::from_str(envelope).unwrap();
                let revision = value["revision"].as_u64().unwrap();
                assert_eq!(revision, expected + 1);
                let hash = value["payloadHash"].as_str().unwrap().to_owned();
                *self.0.borrow_mut() = RemoteEnvelope {
                    revision,
                    payload_hash: hash.clone(),
                    envelope: envelope.into(),
                };
                Ok((revision, hash))
            }
        }
        let mut c = database();
        let keys = Mem(RefCell::new(BTreeMap::new()));
        let session = Session {
            user_id: "verified-user-unknown-merge".into(),
            email: "unknown-merge@example.invalid".into(),
            display_name: None,
            avatar_url: None,
            access_token: "a".into(),
            refresh_token: "r".into(),
            expires_at_epoch_seconds: 9_999_999_999,
        };
        save_session(&keys, &session).unwrap();
        let account = sync_state_v1::account_ref(&session.user_id).unwrap();
        c.execute("INSERT INTO desktop_cloud_account_state VALUES(?1,?2,?3,NULL,NULL,'VERIFYING',0,1,'safe-salt',1,NULL,'COMMIT_RESULT_UNKNOWN','device-safe',1,1,NULL,NULL,NULL)",params![account,session.user_id,session.email]).unwrap();
        c.execute("INSERT INTO workspaces(id,title,semantic_hash,package_hash,created_at) VALUES('workspace-unknown','测试','before','package','2026-09-01T00:00:00Z')", []).unwrap();
        c.execute("INSERT INTO workspace_exchange VALUES('workspace-unknown',?1)",[json!({"conversations":[{"id":"conversation-unknown","title":"完整对话","revision":1,"currentLeafId":"message","messages":[{"id":"message","parentId":null,"ordinal":0,"role":"user","delivery":"COMPLETE","createdAt":"2026-09-01T00:00:00Z","revision":1,"blocks":[{"kind":"TEXT","text":"正文"}]}]}]}).to_string()]).unwrap();
        c.execute("INSERT INTO desktop_sync_jobs VALUES('job-unknown',?1,'workspace-unknown','conversation-unknown','conversation-unknown-document','UNKNOWN','old-hash',0,1,NULL,'COMMIT_RESULT_UNKNOWN',1,1)",[&account]).unwrap();
        let document_id = desktop_data_area::document_id(crate::DesktopDataArea::Chat, "conversation-unknown");
        c.execute("UPDATE desktop_sync_jobs SET document_id=?1 WHERE job_id='job-unknown'",[&document_id]).unwrap();
        let (mut payload, _) = conversation_payload(&c,"workspace-unknown","conversation-unknown",&document_id,1).unwrap();
        let conversation = &mut payload["records"][0]["content"];
        conversation["messages"].as_array_mut().unwrap().push(json!({
            "id":"remote-answer","parentId":"message","ordinal":0,"role":"assistant",
            "delivery":"COMPLETE","createdAt":"2026-09-01T00:01:00Z","revision":1,
            "blocks":[{"kind":"TEXT","text":"synthetic remote answer"}]
        }));
        conversation["currentLeafId"]=json!("remote-answer");
        let envelope=sync_v1::seal_direct(payload).unwrap();
        let parsed:Value=serde_json::from_str(&envelope).unwrap();
        let cloud = Cloud(RefCell::new(RemoteEnvelope {
            revision: 1,
            payload_hash: parsed["payloadHash"].as_str().unwrap().into(),
            envelope,
        }));
        let receipt = reconcile_unknown_commit(
            &mut c,
            &keys,
            &cloud,
            "workspace-unknown",
            "conversation-unknown",
        )
        .unwrap();
        assert_eq!(receipt.status, "SYNCED_AFTER_REMOTE_RECONCILE");
        let old_stage: String = c
            .query_row(
                "SELECT stage FROM desktop_sync_jobs WHERE job_id='job-unknown'",
                [],
                |row| row.get(0),
            )
            .unwrap();
        assert_eq!(old_stage, "SUPERSEDED");
        assert_eq!(cloud.0.borrow().revision, 2);
    }
    #[test]
    fn changed_remote_reconciliation_preserves_new_unknown_result() {
        struct Cloud(RefCell<RemoteEnvelope>);
        impl CloudGateway for Cloud {
            fn read(&self, _: &str) -> Result<Option<RemoteEnvelope>, CloudFailure> {
                Ok(Some(self.0.borrow().clone()))
            }
            fn commit(
                &self,
                _: &str,
                expected: u64,
                envelope: &str,
            ) -> Result<(u64, String), CloudFailure> {
                let value: Value = serde_json::from_str(envelope).unwrap();
                let revision = value["revision"].as_u64().unwrap();
                assert_eq!(revision, expected + 1);
                let hash = value["payloadHash"].as_str().unwrap().to_owned();
                *self.0.borrow_mut() = RemoteEnvelope {
                    revision,
                    payload_hash: hash.clone(),
                    envelope: json!({"format":"unknown-format","revision":revision,"payloadHash":hash}).to_string(),
                };
                Ok((revision, hash))
            }
        }
        let mut c = database();
        let keys = Mem(RefCell::new(BTreeMap::new()));
        let session = Session {
            user_id: "verified-user-unknown-merge".into(),
            email: "unknown-merge@example.invalid".into(),
            display_name: None,
            avatar_url: None,
            access_token: "a".into(),
            refresh_token: "r".into(),
            expires_at_epoch_seconds: 9_999_999_999,
        };
        save_session(&keys, &session).unwrap();
        let account = sync_state_v1::account_ref(&session.user_id).unwrap();
        c.execute("INSERT INTO desktop_cloud_account_state VALUES(?1,?2,?3,NULL,NULL,'VERIFYING',0,1,'safe-salt',1,NULL,'COMMIT_RESULT_UNKNOWN','device-safe',1,1,NULL,NULL,NULL)",params![account,session.user_id,session.email]).unwrap();
        c.execute("INSERT INTO workspaces(id,title,semantic_hash,package_hash,created_at) VALUES('workspace-unknown','测试','before','package','2026-09-01T00:00:00Z')", []).unwrap();
        c.execute("INSERT INTO workspace_exchange VALUES('workspace-unknown',?1)",[json!({"conversations":[{"id":"conversation-unknown","title":"完整对话","revision":1,"currentLeafId":"message","messages":[{"id":"message","parentId":null,"ordinal":0,"role":"user","delivery":"COMPLETE","createdAt":"2026-09-01T00:00:00Z","revision":1,"blocks":[{"kind":"TEXT","text":"正文"}]}]}]}).to_string()]).unwrap();
        c.execute("INSERT INTO desktop_sync_jobs VALUES('job-unknown',?1,'workspace-unknown','conversation-unknown','conversation-unknown-document','UNKNOWN','old-hash',0,1,NULL,'COMMIT_RESULT_UNKNOWN',1,1)",[&account]).unwrap();
        let document_id = desktop_data_area::document_id(crate::DesktopDataArea::Chat, "conversation-unknown");
        c.execute("UPDATE desktop_sync_jobs SET document_id=?1 WHERE job_id='job-unknown'",[&document_id]).unwrap();
        let (mut payload, _) = conversation_payload(&c,"workspace-unknown","conversation-unknown",&document_id,1).unwrap();
        let conversation = &mut payload["records"][0]["content"];
        conversation["messages"].as_array_mut().unwrap().push(json!({
            "id":"remote-answer","parentId":"message","ordinal":0,"role":"assistant",
            "delivery":"COMPLETE","createdAt":"2026-09-01T00:01:00Z","revision":1,
            "blocks":[{"kind":"TEXT","text":"synthetic remote answer"}]
        }));
        conversation["currentLeafId"]=json!("remote-answer");
        let envelope=sync_v1::seal_direct(payload).unwrap();
        let parsed:Value=serde_json::from_str(&envelope).unwrap();
        let cloud = Cloud(RefCell::new(RemoteEnvelope {
            revision: 1,
            payload_hash: parsed["payloadHash"].as_str().unwrap().into(),
            envelope,
        }));
        let receipt = reconcile_unknown_commit(
            &mut c,
            &keys,
            &cloud,
            "workspace-unknown",
            "conversation-unknown",
        )
        .unwrap();
        assert_eq!(receipt.status, "UNKNOWN");
        assert_eq!(receipt.safe_code.as_deref(), Some("COMMIT_RESULT_UNKNOWN"));
        assert!(receipt.synced_at_ms.is_none());
        let old_stage: String = c
            .query_row(
                "SELECT stage FROM desktop_sync_jobs WHERE job_id='job-unknown'",
                [],
                |row| row.get(0),
            )
            .unwrap();
        assert_eq!(old_stage, "SUPERSEDED");
        assert_eq!(cloud.0.borrow().revision, 2);
    }
    #[test]
    #[ignore = "The recovery-code rotation workflow is retired; direct Google-account sync is covered separately."]
    fn recovery_rotation_rewraps_every_selected_document_and_new_device_restores_without_replacing_local(
    ) {
        struct Cloud(RefCell<Option<RemoteEnvelope>>);
        impl CloudGateway for Cloud {
            fn read(&self, _: &str) -> Result<Option<RemoteEnvelope>, CloudFailure> {
                Ok(self.0.borrow().clone())
            }
            fn commit(
                &self,
                _: &str,
                _: u64,
                envelope: &str,
            ) -> Result<(u64, String), CloudFailure> {
                let value: Value = serde_json::from_str(envelope).unwrap();
                let revision = value["revision"].as_u64().unwrap();
                let hash = value["payloadHash"].as_str().unwrap().to_owned();
                *self.0.borrow_mut() = Some(RemoteEnvelope {
                    revision,
                    payload_hash: hash.clone(),
                    envelope: envelope.into(),
                });
                Ok((revision, hash))
            }
            fn list(&self) -> Result<Vec<RemoteEnvelope>, CloudFailure> {
                Ok(self.0.borrow().clone().into_iter().collect())
            }
        }
        let cloud = Cloud(RefCell::new(None));
        let mut source = database();
        let source_keys = Mem(RefCell::new(BTreeMap::new()));
        let session = Session {
            user_id: "verified-user-rotation".into(),
            email: "rotation@example.invalid".into(),
            display_name: None,
            avatar_url: None,
            access_token: "a".into(),
            refresh_token: "r".into(),
            expires_at_epoch_seconds: 9999999999,
        };
        save_session(&source_keys, &session).unwrap();
        let account = sync_state_v1::account_ref(&session.user_id).unwrap();
        source.execute("INSERT INTO desktop_cloud_account_state VALUES(?1,?2,?3,NULL,NULL,'AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION',0,0,NULL,0,NULL,NULL,'source-device',1,1,NULL,NULL,NULL)",params![account,session.user_id,session.email]).unwrap();
        let mut source_store = SqliteMetadataStore {
            connection: &mut source,
        };
        sync_state_v1::authenticate(
            &mut source_store,
            &source_keys,
            "source-auth",
            None,
            "verified-user-rotation",
        )
        .unwrap();
        let (old_code, old_pending) =
            create_recovery_code(source_store.connection, &source_keys).unwrap();
        confirm_recovery(
            source_store.connection,
            &source_keys,
            old_pending,
            &old_code.confirmation_hash,
        )
        .unwrap();
        let metadata = source_store.metadata(&account).unwrap().unwrap();
        sync_state_v1::choose_direction(
            &mut source_store,
            "source-direction",
            &account,
            metadata.revision,
            "LOCAL_SELECTED_REMOTE_GUARDED",
        )
        .unwrap();
        source_store
            .connection
            .execute(
                "UPDATE desktop_cloud_account_state SET state='READY' WHERE account_ref=?1",
                [&account],
            )
            .unwrap();
        source_store.connection.execute("INSERT INTO workspace_exchange VALUES('workspace-rotation',?1)",[json!({"conversations":[{"id":"conversation-rotation","title":"轮换恢复对话","revision":1,"createdAt":"2026-09-01T00:00:00Z","updatedAt":"2026-09-01T00:00:00Z","currentLeafId":"message-rotation","messages":[{"id":"message-rotation","parentId":null,"ordinal":0,"role":"user","delivery":"COMPLETE","createdAt":"2026-09-01T00:00:00Z","revision":1,"blocks":[{"kind":"TEXT","text":"云端恢复正文"}]}]}]}).to_string()]).unwrap();
        source_store.connection.execute("INSERT INTO workspaces VALUES('workspace-rotation','轮换恢复对话','semantic-source','package-source','2026-09-01T00:00:00Z')",[]).unwrap();
        source_store.connection.execute("INSERT INTO desktop_reminder_drafts_v1(draft_id,workspace_id,conversation_id,status,title,instruction,schedule_kind,anchor_local,timezone_id,missed_policy,retry_count,created_at_ms,updated_at_ms) VALUES('draft-rotation','workspace-rotation','conversation-rotation','CONFIRMED','每日检查','检查公开状态','DAILY','2026-09-02T09:00','Asia/Shanghai','RUN_ONCE',0,1,1)",[]).unwrap();
        source_store.connection.execute("INSERT INTO desktop_reminder_plans_v1(plan_id,source_draft_id,workspace_id,conversation_id,title,instruction,schedule_kind,anchor_local,timezone_id,missed_policy,status,next_run_at_ms,latest_result,created_at_ms,updated_at_ms) VALUES('plan-rotation','draft-rotation','workspace-rotation','conversation-rotation','每日检查','检查公开状态','DAILY','2026-09-02T09:00','Asia/Shanghai','RUN_ONCE','ACTIVE',1999999999999,'不应跨设备同步的执行结果',1,1)",[]).unwrap();
        source_store.connection.execute("UPDATE desktop_portable_personalization_v1 SET interests='跨端可靠性与数据保全',revision=1",[]).unwrap();
        assert_eq!(
            sync_selected_conversation(
                source_store.connection,
                &source_keys,
                &cloud,
                "workspace-rotation",
                "conversation-rotation"
            )
            .unwrap()
            .status,
            "SYNCED"
        );
        let old_envelope = cloud.0.borrow().clone().unwrap();
        assert!(sync_v1::open(
            &old_envelope.envelope,
            &old_code.recovery_code,
            APP_ID,
            &envelope_document_id(&old_envelope.envelope).unwrap(),
            1
        )
        .is_ok());
        let (new_code, new_pending) = prepare_custom_recovery_rotation(
            source_store.connection,
            &source_keys,
            "Fixture-custom-recovery-2026",
        )
        .unwrap();
        assert_eq!(new_code.recovery_code, "Fixture-custom-recovery-2026");
        let rotated = rotate_recovery_material(
            source_store.connection,
            &source_keys,
            &cloud,
            Some(new_pending),
            Some(&new_code.confirmation_hash),
        )
        .unwrap();
        assert_eq!(rotated.status, "ROTATED");
        assert_eq!(rotated.completed_documents, 1);
        let new_envelope = cloud.0.borrow().clone().unwrap();
        let document_id = envelope_document_id(&new_envelope.envelope).unwrap();
        assert!(sync_v1::open(
            &new_envelope.envelope,
            &old_code.recovery_code,
            APP_ID,
            &document_id,
            1
        )
        .is_err());
        assert!(sync_v1::open(
            &new_envelope.envelope,
            &new_code.recovery_code,
            APP_ID,
            &document_id,
            1
        )
        .is_ok());

        let mut target = database();
        let target_keys = Mem(RefCell::new(BTreeMap::new()));
        save_session(&target_keys, &session).unwrap();
        target.execute("INSERT INTO desktop_cloud_account_state VALUES(?1,?2,?3,NULL,NULL,'AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION',0,0,NULL,0,NULL,NULL,'target-device',1,1,NULL,NULL,NULL)",params![account,session.user_id,session.email]).unwrap();
        let mut target_store = SqliteMetadataStore {
            connection: &mut target,
        };
        sync_state_v1::authenticate(
            &mut target_store,
            &target_keys,
            "target-auth",
            None,
            "verified-user-rotation",
        )
        .unwrap();
        let restored = restore_remote_conversation(
            target_store.connection,
            &target_keys,
            &cloud,
            &document_id,
        )
        .unwrap();
        assert_eq!(restored.status, "RESTORED_AS_NEW_WORKSPACE");
        assert_eq!(
            target_store
                .connection
                .query_row("SELECT COUNT(*) FROM workspaces", [], |row| row
                    .get::<_, i64>(0))
                .unwrap(),
            1
        );
        assert_eq!(
            target_store
                .connection
                .query_row(
                    "SELECT state FROM desktop_cloud_account_state WHERE account_ref=?1",
                    [&account],
                    |row| row.get::<_, String>(0)
                )
                .unwrap(),
            "READY"
        );
        assert_eq!(
            target_store
                .connection
                .query_row(
                    "SELECT interests FROM desktop_portable_personalization_v1 WHERE id=1",
                    [],
                    |row| row.get::<_, String>(0),
                )
                .unwrap(),
            "跨端可靠性与数据保全"
        );
        let restored_plan: (String, Option<String>, Option<String>) = target_store
            .connection
            .query_row(
                "SELECT status,latest_result,last_safe_error_code FROM desktop_reminder_plans_v1",
                [],
                |row| Ok((row.get(0)?, row.get(1)?, row.get(2)?)),
            )
            .unwrap();
        assert_eq!(
            restored_plan,
            ("PAUSED".into(), None, Some("CLOUD_RESTORED_PAUSED".into()))
        );
    }
    #[test]
    fn localhost_callback_requires_exact_state_and_returns_only_the_code() {
        use std::{net::TcpStream, thread};
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let address = listener.local_addr().unwrap();
        let client = thread::spawn(move || {
            for (request, status) in [
                ("GET /favicon.ico?state=state-safe&code=wrong HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n", "400 Bad Request"),
                ("GET evil/oauth/callback?state=state-safe&code=wrong HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n", "400 Bad Request"),
                ("GET /oauth/callback?state=wrong&code=wrong HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n", "400 Bad Request"),
                ("GET /oauth/callback?state=state-safe&code=code-safe HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n", "200 OK"),
            ] {
                let mut stream = TcpStream::connect(address).unwrap();
                stream.write_all(request.as_bytes()).unwrap();
                let mut response = String::new();
                stream.read_to_string(&mut response).unwrap();
                assert!(response.starts_with(&format!("HTTP/1.1 {status}")));
            }
        });
        assert_eq!(callback_code(listener, "state-safe", &|| Ok(())).unwrap(), "code-safe");
        client.join().unwrap();
    }
    #[test]
    fn localhost_callback_cancel_releases_the_waiting_listener() {
        use std::sync::{atomic::{AtomicBool, Ordering}, Arc};
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let address = listener.local_addr().unwrap();
        let active = Arc::new(AtomicBool::new(true));
        let worker_active = active.clone();
        let worker = std::thread::spawn(move || callback_code(listener, "state-safe", &|| {
            if worker_active.load(Ordering::SeqCst) { Ok(()) } else { Err("synthetic cancelled".into()) }
        }));
        std::thread::sleep(Duration::from_millis(75));
        active.store(false, Ordering::SeqCst);
        assert!(worker.join().unwrap().unwrap_err().contains("synthetic cancelled"));
        assert!(TcpListener::bind(address).is_ok());
    }
    #[test]
    fn expired_rpc_session_refreshes_before_any_sync_request() {
        use std::{net::TcpListener, thread};
        let server = TcpListener::bind("127.0.0.1:0").unwrap();
        let address = server.local_addr().unwrap();
        let response_body = json!({
            "access_token":"fresh-access",
            "refresh_token":"fresh-refresh",
            "expires_in":3600,
            "user":{"id":"refresh-user","email":"refresh@example.invalid","user_metadata":{"name":"Refresh User"}}
        }).to_string();
        let mock = thread::spawn(move || {
            let (mut stream, _) = server.accept().unwrap();
            stream
                .set_read_timeout(Some(Duration::from_secs(2)))
                .unwrap();
            let mut request = [0u8; 8192];
            let count = stream.read(&mut request).unwrap();
            let request = String::from_utf8_lossy(&request[..count]);
            assert!(request.starts_with("POST /auth/v1/token?grant_type=refresh_token "));
            assert!(request.contains("refresh-token-safe"));
            let response = format!(
                "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
                response_body.len(), response_body,
            );
            stream.write_all(response.as_bytes()).unwrap();
        });
        let keys = Mem(RefCell::new(BTreeMap::new()));
        save_session(
            &keys,
            &Session {
                user_id: "refresh-user".into(),
                email: "stale@example.invalid".into(),
                display_name: None,
                avatar_url: None,
                access_token: "expired-access".into(),
                refresh_token: "refresh-token-safe".into(),
                expires_at_epoch_seconds: 0,
            },
        )
        .unwrap();
        let refreshed = active_session(
            &keys,
            &ServiceConfig {
                supabase_url: Url::parse(&format!("http://{address}/")).unwrap(),
                publishable_key: "localhost-publishable".into(),
                localhost_mock: true,
            },
            &Mutex::new(0),
        )
        .unwrap();
        assert_eq!(refreshed.access_token, "fresh-access");
        assert_eq!(refreshed.refresh_token, "fresh-refresh");
        assert_eq!(
            read_session(&keys).unwrap().unwrap().access_token,
            "fresh-access"
        );
        mock.join().unwrap();
    }
    #[test]
    fn sign_out_while_refresh_is_waiting_does_not_restore_the_old_session() {
        use std::sync::{mpsc, Arc};
        #[derive(Clone)]
        struct SharedCredentials(Arc<Mutex<BTreeMap<(String, String), Vec<u8>>>>);
        impl CredentialStore for SharedCredentials {
            fn read_optional(&self, service: &str, account: &str) -> Result<Option<Vec<u8>>, String> {
                Ok(self.0.lock().unwrap().get(&(service.into(), account.into())).cloned())
            }
            fn save(&self, service: &str, account: &str, value: &[u8]) -> Result<(), String> {
                self.0.lock().unwrap().insert((service.into(), account.into()), value.into());
                Ok(())
            }
            fn read(&self, service: &str, account: &str) -> Result<Vec<u8>, String> {
                self.0.lock().unwrap().get(&(service.into(), account.into())).cloned().ok_or_else(|| "missing".into())
            }
            fn delete(&self, service: &str, account: &str) -> Result<(), String> {
                self.0.lock().unwrap().remove(&(service.into(), account.into()));
                Ok(())
            }
        }
        let credentials = SharedCredentials(Arc::new(Mutex::new(BTreeMap::new())));
        save_session(&credentials, &Session {
            user_id: "old-user".into(), email: "old@example.invalid".into(),
            display_name: None, avatar_url: None, access_token: "expired".into(),
            refresh_token: "old-refresh".into(), expires_at_epoch_seconds: 0,
        }).unwrap();
        let generation = Arc::new(Mutex::new(0u64));
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let address = listener.local_addr().unwrap();
        let (request_seen_tx, request_seen_rx) = mpsc::channel();
        let (release_tx, release_rx) = mpsc::channel();
        let server = std::thread::spawn(move || {
            let (mut stream, _) = listener.accept().unwrap();
            stream.set_read_timeout(Some(Duration::from_secs(5))).unwrap();
            let mut request = [0u8; 8192];
            let count = stream.read(&mut request).unwrap();
            assert!(String::from_utf8_lossy(&request[..count]).starts_with("POST /auth/v1/token?grant_type=refresh_token "));
            request_seen_tx.send(()).unwrap();
            release_rx.recv_timeout(Duration::from_secs(5)).unwrap();
            let body = json!({"access_token":"late-access","refresh_token":"late-refresh","expires_in":3600,
                "user":{"id":"old-user","email":"old@example.invalid"}}).to_string();
            stream.write_all(format!("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{body}", body.len()).as_bytes()).unwrap();
        });
        let worker_credentials = credentials.clone();
        let worker_generation = generation.clone();
        let worker = std::thread::spawn(move || active_session(
            &worker_credentials,
            &ServiceConfig { supabase_url: Url::parse(&format!("http://{address}/")).unwrap(),
                publishable_key: "synthetic".into(), localhost_mock: true },
            &worker_generation,
        ));
        request_seen_rx.recv_timeout(Duration::from_secs(5)).unwrap();
        {
            let mut operation = generation.lock().unwrap();
            *operation += 1;
            credentials.delete(SESSION_SERVICE, "active").unwrap();
        }
        release_tx.send(()).unwrap();
        assert!(worker.join().unwrap().unwrap_err().contains("登录状态已变化"));
        assert!(read_session(&credentials).unwrap().is_none());
        server.join().unwrap();
    }
    #[test]
    fn cloud_read_failures_keep_a_safe_specific_recovery_path() {
        assert_eq!(restore_failure_code(&safe_error("标题版本冲突：已保留本机标题")), "TITLE_CONFLICT");
        assert_eq!(restore_failure_code(&safe_error("本机工作区正忙，请稍后重试")), "LOCAL_STORE_BUSY");
        assert_eq!(restore_failure_code(&safe_error("云端恢复内容无效")), "DOCUMENT_RESTORE_FAILED");
        assert_eq!(
            cloud_failure_code(CloudFailure::Unauthorized),
            "REMOTE_AUTH_REJECTED"
        );
        assert_eq!(
            cloud_read_failure_message(CloudFailure::Unauthorized),
            "登录已失效，请重新登录后再同步"
        );
        assert_eq!(
            cloud_failure_code(CloudFailure::MalformedResponse),
            "REMOTE_RESPONSE_INVALID"
        );
        assert_eq!(
            cloud_read_failure_message(CloudFailure::MalformedResponse),
            "南枫云返回格式不兼容，已停止同步"
        );
        assert_eq!(
            classify_rejected_rpc_response(r#"{"code":"2201B","message":"ignored"}"#),
            CloudFailure::CloudValidatorFailure
        );
        assert_eq!(
            cloud_commit_failure_message(CloudFailure::CloudValidatorFailure),
            "南枫云同步校验服务异常，本机和云端内容均未改动"
        );
    }
    #[test]
    fn supabase_empty_read_result_means_document_is_not_yet_in_the_cloud() {
        use std::thread;
        let server = TcpListener::bind("127.0.0.1:0").unwrap();
        let address = server.local_addr().unwrap();
        let mock = thread::spawn(move || {
            let (mut stream, _) = server.accept().unwrap();
            stream
                .set_read_timeout(Some(Duration::from_secs(2)))
                .unwrap();
            let mut request = [0u8; 8192];
            let count = stream.read(&mut request).unwrap();
            let request = String::from_utf8_lossy(&request[..count]);
            assert!(request.starts_with("POST /rest/v1/rpc/nanfeng_sync_read_document "));
            assert!(request.contains("\"p_document_id\":\"conversation-safe\""));
            let response = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 2\r\nConnection: close\r\n\r\n[]";
            stream.write_all(response.as_bytes()).unwrap();
        });
        let gateway = SupabaseGateway::new(
            ServiceConfig {
                supabase_url: Url::parse(&format!("http://{address}/")).unwrap(),
                publishable_key: "localhost-publishable".into(),
                localhost_mock: true,
            },
            &Session {
                user_id: "localhost-user".into(),
                email: "localhost@example.invalid".into(),
                display_name: None,
                avatar_url: None,
                access_token: "localhost-access".into(),
                refresh_token: "localhost-refresh".into(),
                expires_at_epoch_seconds: 9_999_999_999,
            },
        )
        .unwrap();
        assert!(gateway.read("conversation-safe").unwrap().is_none());
        mock.join().unwrap();
    }
    #[test]
    fn supabase_read_accepts_jsonb_and_legacy_json_string_envelopes() {
        use std::thread;
        let server = TcpListener::bind("127.0.0.1:0").unwrap();
        let address = server.local_addr().unwrap();
        let mock = thread::spawn(move || {
            let (mut stream, _) = server.accept().unwrap();
            stream
                .set_read_timeout(Some(Duration::from_secs(2)))
                .unwrap();
            let mut request = [0u8; 8192];
            let _ = stream.read(&mut request).unwrap();
            let envelope = json!({"revision":7,"payloadHash":"a".repeat(64)}).to_string();
            let body = json!([{"revision":7,"payload_hash":"a".repeat(64),"envelope":envelope}])
                .to_string();
            let response = format!("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}", body.len(), body);
            stream.write_all(response.as_bytes()).unwrap();
        });
        let gateway = SupabaseGateway::new(
            ServiceConfig {
                supabase_url: Url::parse(&format!("http://{address}/")).unwrap(),
                publishable_key: "localhost-publishable".into(),
                localhost_mock: true,
            },
            &Session {
                user_id: "localhost-user".into(),
                email: "localhost@example.invalid".into(),
                display_name: None,
                avatar_url: None,
                access_token: "localhost-access".into(),
                refresh_token: "localhost-refresh".into(),
                expires_at_epoch_seconds: 9_999_999_999,
            },
        )
        .unwrap();
        let remote = gateway.read("conversation-safe").unwrap().unwrap();
        assert_eq!(remote.revision, 7);
        assert_eq!(remote.payload_hash, "a".repeat(64));
        mock.join().unwrap();
    }
    #[test]
    fn localhost_oauth_mock_uses_supabase_pkce_and_establishes_app_session_without_google_secret() {
        use std::{net::TcpStream, thread};
        struct CallbackBrowser;
        impl Browser for CallbackBrowser {
            fn open(&self, url: &Url) -> Result<(), String> {
                assert_eq!(url.path(), "/auth/v1/authorize");
                let values = url
                    .query_pairs()
                    .collect::<std::collections::BTreeMap<_, _>>();
                assert_eq!(
                    values.get("provider").map(|value| value.as_ref()),
                    Some("google")
                );
                assert_eq!(
                    values
                        .get("code_challenge_method")
                        .map(|value| value.as_ref()),
                    Some("s256")
                );
                assert!(values
                    .get("code_challenge")
                    .is_some_and(|value| value.len() == 43));
                assert!(values.get("client_id").is_none());
                assert!(values.get("client_secret").is_none());
                let callback = Url::parse(values.get("redirect_to").unwrap()).unwrap();
                let state = callback
                    .query_pairs()
                    .find_map(|(key, value)| (key == "state").then(|| value.to_string()))
                    .unwrap();
                thread::spawn(move || {
                    let mut stream = TcpStream::connect((
                        callback.host_str().unwrap(),
                        callback.port().unwrap(),
                    ))
                    .unwrap();
                    let request = format!(
                        "GET {}?state={}&code=009e5066-fc11-4eca-8c8c-6fd82aa263f2 HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
                        callback.path(),
                        state
                    );
                    stream.write_all(request.as_bytes()).unwrap();
                });
                Ok(())
            }
        }
        let server = TcpListener::bind("127.0.0.1:0").unwrap();
        let address = server.local_addr().unwrap();
        let mock = thread::spawn(move || {
            let (mut stream, _) = server.accept().unwrap();
            stream
                .set_read_timeout(Some(Duration::from_secs(2)))
                .unwrap();
            let mut request = [0u8; 8192];
            let count = stream.read(&mut request).unwrap();
            let first = String::from_utf8_lossy(&request[..count]);
            assert!(first.starts_with("POST /auth/v1/token?grant_type=pkce "));
            assert!(first.contains("apikey: localhost-publishable"));
            let request_body = first.split_once("\r\n\r\n").unwrap().1;
            let request_json = serde_json::from_str::<Value>(request_body).unwrap();
            assert_eq!(
                request_json["auth_code"].as_str(),
                Some("009e5066-fc11-4eca-8c8c-6fd82aa263f2")
            );
            assert!(request_json["code_verifier"]
                .as_str()
                .is_some_and(|value| (43..=128).contains(&value.len())));
            let body = json!({"access_token":"localhost-access","refresh_token":"localhost-refresh","expires_in":3600,"user":{"id":"localhost-user","email":"localhost@example.invalid","user_metadata":{"name":"Localhost User"}}}).to_string();
            let response=format!("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",body.len(),body);
            stream.write_all(response.as_bytes()).unwrap();
        });
        let mut connection = database();
        let credentials = Mem(RefCell::new(BTreeMap::new()));
        let base = Url::parse(&format!("http://{address}/")).unwrap();
        let config = ServiceConfig {
            supabase_url: base.clone(),
            publishable_key: "localhost-publishable".into(),
            localhost_mock: true,
        };
        let session = authorize_with_system_browser(&config, &CallbackBrowser).unwrap();
        mock.join().unwrap();
        assert_eq!(session.email, "localhost@example.invalid");
        assert!(read_session(&credentials).unwrap().is_none());
        persist_authenticated_session(&mut connection, &credentials, &session).unwrap();
        assert_eq!(
            read_session(&credentials).unwrap().unwrap().user_id,
            "localhost-user"
        );
        assert_eq!(
            projection(&connection, &credentials, true, None)
                .unwrap()
                .state,
            "READY"
        );
        let account = sync_state_v1::account_ref(&session.user_id).unwrap();
        assert!(credentials.read(DATA_KEY_SERVICE, &account).is_err());
        assert_eq!(
            projection(&connection, &credentials, true, None)
                .unwrap()
                .recovery_state,
            "NOT_REQUIRED"
        );
    }

    #[test]
    fn shared_google_session_can_initialize_both_areas_without_recovery_material() {
        let mut chat = database();
        let mut work = database();
        let credentials = Mem(RefCell::new(BTreeMap::new()));
        let session = Session {
            user_id: "two-area-direct-user".into(),
            email: "two-area@example.invalid".into(),
            display_name: None,
            avatar_url: None,
            access_token: "fixture-access".into(),
            refresh_token: "fixture-refresh".into(),
            expires_at_epoch_seconds: 9_999_999_999,
        };
        persist_authenticated_session(&mut chat, &credentials, &session).unwrap();
        let persisted = read_session(&credentials).unwrap().unwrap();
        persist_authenticated_session(&mut work, &credentials, &persisted).unwrap();
        let account = sync_state_v1::account_ref(&session.user_id).unwrap();
        assert!(credentials.read(DATA_KEY_SERVICE, &account).is_err());
        for area in [&chat, &work] {
            let account_state = projection(area, &credentials, true, None).unwrap();
            assert_eq!(account_state.state, "READY");
            assert_eq!(account_state.recovery_state, "NOT_REQUIRED");
            set_periodic_enabled(area, &credentials, true).unwrap();
            assert!(projection(area, &credentials, true, None).unwrap().periodic_enabled);
        }
        work.execute("UPDATE desktop_cloud_account_state SET state='FAILED',last_error_code='KEY_MATERIAL_UNAVAILABLE' WHERE account_ref=?1", [&account]).unwrap();
        work.execute("UPDATE sync_account_metadata SET state='FAILED',last_error='KEY_MATERIAL_UNAVAILABLE' WHERE account_ref=?1", [&account]).unwrap();
        ensure_current_area_direct_account(&mut work, &credentials).unwrap();
        assert_eq!(projection(&work, &credentials, true, None).unwrap().state, "READY");
        assert!(credentials.read(DATA_KEY_SERVICE, &account).is_err());

        work.execute("UPDATE desktop_cloud_account_state SET state='CONFLICT',last_error_code='REMOTE_CONFLICT' WHERE account_ref=?1", [&account]).unwrap();
        work.execute("UPDATE sync_account_metadata SET state='FAILED',last_error='REMOTE_CONFLICT' WHERE account_ref=?1", [&account]).unwrap();
        let revision: i64 = work.query_row("SELECT revision FROM desktop_cloud_account_state WHERE account_ref=?1", [&account], |row| row.get(0)).unwrap();
        ensure_current_area_direct_account(&mut work, &credentials).unwrap();
        assert_eq!(projection(&work, &credentials, true, None).unwrap().state, "CONFLICT");
        assert_eq!(work.query_row("SELECT revision FROM desktop_cloud_account_state WHERE account_ref=?1", [&account], |row| row.get::<_, i64>(0)).unwrap(), revision);
    }

    #[test]
    fn verified_legacy_envelope_migrates_with_cas_readback_and_missing_key_never_writes() {
        struct Cloud(RefCell<RemoteEnvelope>, std::cell::Cell<usize>);
        impl CloudGateway for Cloud {
            fn read(&self, _: &str) -> Result<Option<RemoteEnvelope>, CloudFailure> {
                Ok(Some(self.0.borrow().clone()))
            }
            fn commit(&self, _: &str, expected: u64, envelope: &str) -> Result<(u64, String), CloudFailure> {
                let root: Value = serde_json::from_str(envelope).unwrap();
                let revision = root["revision"].as_u64().unwrap();
                assert_eq!(revision, expected + 1);
                let hash = root["payloadHash"].as_str().unwrap().to_owned();
                self.0.replace(RemoteEnvelope { revision, payload_hash: hash.clone(), envelope: envelope.into() });
                self.1.set(self.1.get() + 1);
                Ok((revision, hash))
            }
        }
        let fixture: Value = serde_json::from_str(include_str!("../../../protocol/fixtures/nfai.sync.v1.golden.json")).unwrap();
        let mut payload = fixture["payload"].clone();
        payload["documentId"] = json!("conversation-fixture-v1");
        let material = sync_v1::create_account_wrapping_material("visible-legacy-code").unwrap();
        let encrypted = sync_v1::seal_with_account_wrapping_material(payload, &[3; 32], &material).unwrap();
        let root: Value = serde_json::from_str(&encrypted).unwrap();
        let cloud = Cloud(RefCell::new(RemoteEnvelope {
            revision: 7,
            payload_hash: root["payloadHash"].as_str().unwrap().into(),
            envelope: encrypted,
        }), std::cell::Cell::new(0));
        let connection = database();
        let keys = Mem(RefCell::new(BTreeMap::new()));
        let session = Session {
            user_id: "legacy-migration-user".into(), email: "legacy@example.invalid".into(),
            display_name: None, avatar_url: None, access_token: "fixture-access".into(),
            refresh_token: "fixture-refresh".into(), expires_at_epoch_seconds: 9_999_999_999,
        };
        save_session(&keys, &session).unwrap();
        let account = sync_state_v1::account_ref(&session.user_id).unwrap();
        connection.execute(
            "INSERT INTO desktop_cloud_account_state VALUES(?1,?2,?3,NULL,NULL,'READY',0,1,?4,1,NULL,NULL,'device-safe',1,1,NULL,NULL,NULL)",
            params![account, session.user_id, session.email, URL_SAFE_NO_PAD.encode(&material.salt)],
        ).unwrap();
        let remote = cloud.0.borrow().clone();
        let missing_key = migrate_legacy_document(&connection, &keys, &cloud, &account, remote.clone()).unwrap_err();
        assert!(missing_key.contains("旧加密记录"), "{missing_key}");
        assert_eq!(cloud.1.get(), 0);
        keys.save(RECOVERY_SERVICE, &account, &material.wrapping_key).unwrap();
        let migrated = migrate_legacy_document(&connection, &keys, &cloud, &account, remote).unwrap();
        assert_eq!(cloud.1.get(), 1);
        assert_eq!(migrated.revision, 8);
        assert_eq!(serde_json::from_str::<Value>(&migrated.envelope).unwrap()["format"], sync_v1::DIRECT_ENVELOPE);
        assert!(sync_v1::open_direct(&migrated.envelope, APP_ID, "conversation-fixture-v1", 8).is_ok());
        assert!(keys.read(RECOVERY_SERVICE, &account).is_ok(), "legacy private material remains for other documents");
    }

    #[test]
    fn supabase_list_uses_complete_inventory_rpc() {
        use std::{io::{Read, Write}, net::TcpListener, thread};
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let address = listener.local_addr().unwrap();
        let server = thread::spawn(move || {
            let (mut stream, _) = listener.accept().unwrap();
            stream.set_read_timeout(Some(Duration::from_secs(3))).unwrap();
            let mut request = [0u8; 8192];
            let count = stream.read(&mut request).unwrap();
            let request = String::from_utf8_lossy(&request[..count]);
            assert!(request.starts_with("POST /rest/v1/rpc/nanfeng_sync_inventory "));
            let body = json!({"documentCount":1,"documents":[{"envelope":{"revision":7,"payloadHash":"fixture-hash"}}]}).to_string();
            let response = format!(
                "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
                body.len(), body,
            );
            stream.write_all(response.as_bytes()).unwrap();
        });
        let session = Session {
            user_id: "inventory-fixture-user".into(), email: "inventory@example.invalid".into(),
            display_name: None, avatar_url: None, access_token: "fixture-access".into(),
            refresh_token: "fixture-refresh".into(), expires_at_epoch_seconds: 9_999_999_999,
        };
        let gateway = SupabaseGateway::new(ServiceConfig {
            supabase_url: Url::parse(&format!("http://{address}/")).unwrap(),
            publishable_key: "fixture-publishable".into(), localhost_mock: true,
        }, &session).unwrap();
        let listed = gateway.list().unwrap();
        server.join().unwrap();
        assert_eq!(listed.len(), 1);
        assert_eq!(listed[0].revision, 7);
        assert_eq!(listed[0].payload_hash, "fixture-hash");
    }

    #[test]
    fn inventory_requires_a_complete_bounded_list_before_any_missing_receipt_decision() {
        assert!(parse_sync_inventory(&json!({"documentCount":0,"documents":[]})).unwrap().is_empty());
        assert_eq!(
            parse_sync_inventory(&json!({"documentCount":2,"documents":[{"envelope":{}}]})).unwrap_err(),
            CloudFailure::MalformedResponse,
        );
        assert_eq!(
            parse_sync_inventory(&json!({"documentCount":0,"documents":[{"envelope":{}}]})).unwrap_err(),
            CloudFailure::MalformedResponse,
        );
        assert_eq!(
            parse_sync_inventory(&json!({"documentCount":10001,"documents":vec![Value::Null; 10001]})).unwrap_err(),
            CloudFailure::MalformedResponse,
        );
        let valid = json!({"documentId":"conversation-fixture-1","revision":3,"payloadHash":"fixture-hash"});
        let documents = parse_sync_inventory(&json!({"documentCount":2,"documents":[
            {"envelope":valid},{"envelope":"not-json"}
        ]})).unwrap();
        assert_eq!(documents.len(), 2);
        assert_eq!(documents[0].revision, 3);
        assert_eq!(documents[1].revision, 0);
        assert!(documents.iter().map(|item| envelope_document_id(&item.envelope))
            .collect::<Result<BTreeSet<_>,_>>().is_err(), "bad document must prevent absent-receipt pruning");
    }

    #[test]
    fn empty_inventory_keeps_a_receipt_updated_during_read_in_the_same_millisecond() {
        struct EmptyAfterConcurrentUpdate(std::path::PathBuf);
        impl CloudGateway for EmptyAfterConcurrentUpdate {
            fn read(&self, _: &str) -> Result<Option<RemoteEnvelope>, CloudFailure> { panic!("inventory only") }
            fn commit(&self, _: &str, _: u64, _: &str) -> Result<(u64, String), CloudFailure> { panic!("inventory must not upload") }
            fn list(&self) -> Result<Vec<RemoteEnvelope>, CloudFailure> {
                let other = Connection::open(&self.0).unwrap();
                other.execute("UPDATE desktop_selected_conversation_sync SET remote_revision=2,payload_hash='new-payload' WHERE conversation_id='conversation-race'", []).unwrap();
                Ok(Vec::new())
            }
        }
        let directory = tempfile::tempdir().unwrap();
        let path = directory.path().join("sync.sqlite3");
        let source = database();
        source.execute("VACUUM INTO ?1", [path.to_str().unwrap()]).unwrap();
        drop(source);
        let mut connection = Connection::open(&path).unwrap();
        let keys = Mem(RefCell::new(BTreeMap::new()));
        save_session(&keys, &Session {
            user_id: "race-account".into(), email: "race@example.invalid".into(),
            display_name: None, avatar_url: None, access_token: "fixture-access".into(),
            refresh_token: "fixture-refresh".into(), expires_at_epoch_seconds: 9_999_999_999,
        }).unwrap();
        let account = sync_state_v1::account_ref("race-account").unwrap();
        connection.execute("INSERT INTO desktop_selected_conversation_sync(account_ref,workspace_id,conversation_id,document_id,remote_revision,payload_hash,local_content_hash,last_synced_at_ms) VALUES(?1,'workspace-race','conversation-race','document-race',1,'old-payload','local',1000)", [&account]).unwrap();

        let listed = restore_all_remote_conversations(&mut connection, &keys, &EmptyAfterConcurrentUpdate(path)).unwrap();
        assert_eq!(listed.cloud_conversation_keys, Some(vec!["workspace-race:conversation-race".into()]));
        let retained: (i64, String) = connection.query_row("SELECT remote_revision,payload_hash FROM desktop_selected_conversation_sync WHERE account_ref=?1 AND conversation_id='conversation-race'", [&account], |row| Ok((row.get(0)?,row.get(1)?))).unwrap();
        assert_eq!(retained, (2, "new-payload".into()));
    }

    #[test]
    fn inventory_http_read_rejects_an_oversized_response() {
        use std::{io::{Read, Write}, net::TcpListener, thread};
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let address = listener.local_addr().unwrap();
        let server = thread::spawn(move || {
            let (mut stream, _) = listener.accept().unwrap();
            stream.set_read_timeout(Some(Duration::from_secs(3))).unwrap();
            let mut request = [0u8; 8192];
            let count = stream.read(&mut request).unwrap();
            assert!(String::from_utf8_lossy(&request[..count])
                .starts_with("POST /rest/v1/rpc/nanfeng_sync_inventory "));
            let body = json!({"documentCount":0,"documents":[]}).to_string();
            let response = format!(
                "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
                body.len(), body,
            );
            stream.write_all(response.as_bytes()).unwrap();
        });
        let session = Session {
            user_id: "inventory-limit-user".into(), email: "inventory-limit@example.invalid".into(),
            display_name: None, avatar_url: None, access_token: "fixture-access".into(),
            refresh_token: "fixture-refresh".into(), expires_at_epoch_seconds: 9_999_999_999,
        };
        let gateway = SupabaseGateway::new(ServiceConfig {
            supabase_url: Url::parse(&format!("http://{address}/")).unwrap(),
            publishable_key: "fixture-publishable".into(), localhost_mock: true,
        }, &session).unwrap();
        assert_eq!(gateway.rpc_with_limit("nanfeng_sync_inventory", json!({"p_app_id":APP_ID}), Some(8)).unwrap_err(), CloudFailure::MalformedResponse);
        server.join().unwrap();
    }
}
