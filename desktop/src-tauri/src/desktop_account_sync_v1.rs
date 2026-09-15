//! Desktop Google account and selected-conversation encrypted sync owner.
//! Secrets live only in the app-owned credential store; SQLite keeps safe state, receipts and diagnostics.
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
use rusqlite::{params, Connection, OptionalExtension, Transaction};
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
    sync::Mutex,
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
        match fs::read(self.document_path()) {
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
        let temporary = self
            .root
            .join(format!(".credentials-{}.tmp", std::process::id()));
        fs::write(&temporary, encoded).map_err(|_| safe_error("应用私有同步凭据无法写入"))?;
        set_private_permissions(&temporary, false)?;
        fs::rename(&temporary, self.document_path())
            .map_err(|_| safe_error("应用私有同步凭据无法提交"))?;
        set_private_permissions(&self.document_path(), false)
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
        let record_id = Self::record_id(service, account)?;
        let _access = ACCOUNT_CREDENTIAL_ACCESS_LOCK
            .lock()
            .map_err(|_| safe_error("应用私有同步凭据状态忙"))?;
        let key = self.load_key(false)?;
        let document = self.read_document()?;
        let record = document
            .records
            .get(&record_id)
            .ok_or_else(|| safe_error("应用私有同步凭据不存在"))?;
        self.decrypt(record, &key)
    }

    fn delete(&self, service: &str, account: &str) -> Result<(), String> {
        let record_id = Self::record_id(service, account)?;
        let _access = ACCOUNT_CREDENTIAL_ACCESS_LOCK
            .lock()
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
    #[cfg(not(unix))]
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
    let timestamp = now_ms();
    let id = format!(
        "sync-diag-{}-{}",
        timestamp,
        &sha256(format!("{event}:{outcome}:{timestamp}").as_bytes())[..12]
    );
    let _ = connection.execute("INSERT INTO desktop_sync_diagnostics(id,account_ref,event,outcome,safe_code,occurred_at_ms) VALUES(?1,?2,?3,?4,?5,?6)", params![id,account,event,outcome,code,timestamp]);
    let _ = connection.execute("DELETE FROM desktop_sync_diagnostics WHERE id NOT IN (SELECT id FROM desktop_sync_diagnostics ORDER BY occurred_at_ms DESC LIMIT 100)", []);
}
fn write_notification(connection: &Connection, account: Option<&str>, kind: &str, message: &str) {
    let timestamp = now_ms();
    let id = format!(
        "sync-note-{}-{}",
        timestamp,
        &sha256(format!("{kind}:{timestamp}").as_bytes())[..12]
    );
    let _ = connection.execute("INSERT INTO desktop_sync_notifications(id,account_ref,kind,message,occurred_at_ms,unread) VALUES(?1,?2,?3,?4,?5,1)", params![id,account,kind,message,timestamp]);
    let _ = connection.execute("DELETE FROM desktop_sync_notifications WHERE id NOT IN (SELECT id FROM desktop_sync_notifications ORDER BY occurred_at_ms DESC LIMIT 50)", []);
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
    let mut encoded = match credentials.read(SESSION_SERVICE, "active") {
        Ok(value) => value,
        Err(_) => return Ok(None),
    };
    let result = serde_json::from_slice(&encoded).map_err(|_| safe_error("账号会话无法读取"));
    encoded.zeroize();
    result.map(Some)
}

/// Exchanges an expiring Supabase access token without opening Google again. The refresh token
/// stays inside the app-owned credential boundary and is never included in diagnostics.
fn refresh_session(config: &ServiceConfig, current: &Session) -> Result<Session, String> {
    let client = Client::builder()
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
    credentials: &C,
    config: &ServiceConfig,
) -> Result<Session, String> {
    let current = read_session(credentials)?.ok_or_else(|| safe_error("请先登录 Google 账号"))?;
    let now = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs();
    if current.expires_at_epoch_seconds > now + SESSION_REFRESH_SKEW_SECONDS {
        return Ok(current);
    }
    let refreshed = refresh_session(config, &current)?;
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

fn callback_code(listener: TcpListener, expected_state: &str) -> Result<String, String> {
    listener
        .set_nonblocking(true)
        .map_err(|_| safe_error("OAuth callback 无法等待"))?;
    let deadline = Instant::now() + Duration::from_secs(180);
    let (mut stream, peer) = loop {
        match listener.accept() {
            Ok(value) => break value,
            Err(error)
                if error.kind() == std::io::ErrorKind::WouldBlock && Instant::now() < deadline =>
            {
                std::thread::sleep(Duration::from_millis(25))
            }
            Err(_) => return Err(safe_error("OAuth callback 未完成")),
        }
    };
    if !peer.ip().is_loopback() {
        return Err(safe_error("OAuth callback 来源无效"));
    }
    // `TcpListener` is non-blocking only while we wait for a browser to
    // connect. Its accepted stream can inherit that mode, which would turn a
    // perfectly valid callback into an intermittent `WouldBlock` before the
    // browser has written its request. Restore bounded blocking reads on the
    // trusted loopback stream before parsing the callback.
    stream
        .set_nonblocking(false)
        .map_err(|_| safe_error("OAuth callback 无法读取"))?;
    stream.set_read_timeout(Some(Duration::from_secs(10))).ok();
    let mut bytes = Vec::with_capacity(16 * 1024);
    while !bytes.windows(2).any(|value| value == b"\r\n") && bytes.len() < 16 * 1024 {
        let mut chunk = [0u8; 1024];
        let count = stream
            .read(&mut chunk)
            .map_err(|_| safe_error("OAuth callback 无法读取"))?;
        if count == 0 {
            return Err(safe_error("OAuth callback 无法读取"));
        }
        bytes.extend_from_slice(&chunk[..count]);
    }
    let first = std::str::from_utf8(&bytes)
        .ok()
        .and_then(|value| value.lines().next())
        .ok_or_else(|| safe_error("OAuth callback 无效"))?;
    let target = first
        .strip_prefix("GET ")
        .and_then(|value| value.split_whitespace().next())
        .ok_or_else(|| safe_error("OAuth callback 无效"))?;
    let url = Url::parse(&format!("http://127.0.0.1{target}"))
        .map_err(|_| safe_error("OAuth callback 无效"))?;
    let values = url
        .query_pairs()
        .collect::<std::collections::BTreeMap<_, _>>();
    let valid = values
        .get("state")
        .is_some_and(|value| value.as_ref() == expected_state);
    let code = values
        .get("code")
        .filter(|_| valid)
        .map(|value| value.to_string());
    let body = if code.is_some() {
        "授权已完成，可以返回南枫 AI。"
    } else {
        "授权未完成，请返回南枫 AI 重试。"
    };
    let response = format!("HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}", body.as_bytes().len(), body);
    let _ = stream.write_all(response.as_bytes());
    code.ok_or_else(|| safe_error("Google 授权未完成或状态校验失败"))
}

pub fn authorize_with_system_browser<B: Browser>(
    config: &ServiceConfig,
    browser: &B,
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
    let code = callback_code(listener, &state)?;
    let client = Client::builder()
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
    save_session(credentials, session)?;
    let account = sync_state_v1::account_ref(&session.user_id)?;
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
    connection.execute("INSERT INTO desktop_cloud_account_state(account_ref,user_id,email,display_name,avatar_url,state,periodic_enabled,recovery_confirmed,recovery_salt_b64,recovery_generation,last_success_at_ms,last_error_code,device_id,revision,updated_at_ms) VALUES(?1,?2,?3,?4,?5,'AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION',0,0,NULL,0,NULL,NULL,?6,1,?7) ON CONFLICT(account_ref) DO UPDATE SET user_id=excluded.user_id,email=excluded.email,display_name=excluded.display_name,avatar_url=excluded.avatar_url,state=CASE WHEN desktop_cloud_account_state.recovery_confirmed=1 THEN 'DIRECTION_REQUIRED' ELSE 'AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION' END,revision=desktop_cloud_account_state.revision+1,updated_at_ms=excluded.updated_at_ms", params![account,session.user_id,session.email,session.display_name,session.avatar_url,device_id,now_ms()]).map_err(|_| safe_error("账号状态未保存"))?;
    let mut state_store = SqliteMetadataStore { connection };
    let current = state_store.metadata(&account)?.map(|value| value.revision);
    sync_state_v1::authenticate(
        &mut state_store,
        credentials,
        &format!("desktop-google-auth-{}-{}", &account[..12], now_ms()),
        current,
        &session.user_id,
    )?;
    write_diagnostic(
        state_store.connection,
        Some(&account),
        "OAUTH_CALLBACK",
        "SUCCESS",
        None,
    );
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

pub struct SupabaseGateway {
    client: Client,
    config: ServiceConfig,
    access_token: Zeroizing<String>,
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
        })
    }
    fn rpc(&self, name: &str, body: Value) -> Result<Value, CloudFailure> {
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
        response.json().map_err(|_| CloudFailure::MalformedResponse)
    }
}
impl CloudGateway for SupabaseGateway {
    fn list(&self) -> Result<Vec<RemoteEnvelope>, CloudFailure> {
        let response = self.rpc("nanfeng_sync_list_documents", json!({"p_app_id":APP_ID}))?;
        let rows = response.as_array().ok_or(CloudFailure::MalformedResponse)?;
        rows.iter()
            .map(|value| {
                let envelope_value = value
                    .get("envelope")
                    .cloned()
                    .ok_or(CloudFailure::MalformedResponse)?;
                let envelope = match envelope_value {
                    Value::String(value) => value,
                    value => serde_json::to_string(&value)
                        .map_err(|_| CloudFailure::MalformedResponse)?,
                };
                let root: Value =
                    serde_json::from_str(&envelope).map_err(|_| CloudFailure::MalformedResponse)?;
                Ok(RemoteEnvelope {
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
                })
            })
            .collect()
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

fn envelope_document_id(envelope: &str) -> Result<String, String> {
    serde_json::from_str::<Value>(envelope)
        .ok()
        .and_then(|value| {
            value
                .get("documentId")
                .and_then(Value::as_str)
                .map(str::to_owned)
        })
        .filter(|value| {
            (value.starts_with("conversation-") && value.len() <= 64)
                || value == CLOUD_LIST_PRESENTATION_DOCUMENT_ID
        })
        .ok_or_else(|| safe_error("云端文档标识无效"))
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
    /// None means inventory validity is unknown, never an empty account.
    pub cloud_conversation_keys: Option<Vec<String>>,
    /// Historic envelopes are intentionally not surfaced in the direct-sync
    /// list. They cannot be opened after the recovery-code protocol was
    /// retired, and one such record must never prevent current documents from
    /// becoming available.
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
                    })
            });
        if same_messages
            && local_title.as_ref() == remote_conversation.get("title")
            && local_title_revision.as_ref() == remote_conversation.get("titleRevision")
            && local_leaf.as_ref() == remote_conversation.get("currentLeafId")
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
        let local_only_blocks = local_message
            .get("blocks")
            .and_then(Value::as_array)
            .into_iter()
            .flatten()
            .filter(|block| block.get("kind").and_then(Value::as_str) != Some("TEXT"))
            .cloned()
            .collect::<Vec<_>>();
        if !local_only_blocks.is_empty() {
            let blocks = updated
                .get_mut("blocks")
                .and_then(Value::as_array_mut)
                .ok_or_else(|| safe_error("云端消息内容无效"))?;
            blocks.extend(local_only_blocks);
        }
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
    // Older local workspaces can predate the full export envelope. They are still valid owners
    // of an already-synced conversation; merge the title and retain their existing workspace
    // hash rather than rejecting a safe cross-device update solely for that legacy shape.
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
        let local_only_blocks = local_message
            .get("blocks")
            .and_then(Value::as_array)
            .into_iter()
            .flatten()
            .filter(|block| block.get("kind").and_then(Value::as_str) != Some("TEXT"))
            .cloned()
            .collect::<Vec<_>>();
        if !local_only_blocks.is_empty() {
            updated
                .get_mut("blocks")
                .and_then(Value::as_array_mut)
                .ok_or_else(|| safe_error("云端消息内容无效"))?
                .extend(local_only_blocks);
        }
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
                "usage",
                "chargeMicros",
                "currencyCode",
                "costPriceVersion",
                "costSource",
            ] {
                if let Some(value) = local_message.get(key) {
                    updated_object.insert(key.into(), value.clone());
                }
            }
        }
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
            if document_id == CLOUD_LIST_PRESENTATION_DOCUMENT_ID {
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
    let remote = gateway
        .read(document_id)
        .map_err(|_| safe_error("云端恢复文档无法读取"))?
        .ok_or_else(|| safe_error("云端恢复文档不存在"))?;
    restore_remote_envelope(connection, credentials, remote)
}

// The batch list RPC already returns each full encrypted envelope.  Reusing it
// avoids one extra network read per conversation during a cloud-list refresh.
fn restore_remote_envelope<C: CredentialStore>(
    connection: &mut Connection,
    credentials: &C,
    remote: RemoteEnvelope,
) -> Result<RestoreReceipt, String> {
    let document_id = envelope_document_id(&remote.envelope)?;
    let session = read_session(credentials)?.ok_or_else(|| safe_error("请先登录 Google 账号"))?;
    let account = sync_state_v1::account_ref(&session.user_id)?;
    let payload = sync_v1::open_direct(&remote.envelope, APP_ID, &document_id, remote.revision)
        .map_err(|_| safe_error("该云端对话仍是旧加密格式，正在等待原设备迁移"))?;
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
    let mut conversation = record
        .get("content")
        .and_then(Value::as_object)
        .cloned()
        .ok_or_else(|| safe_error("云端对话内容无效"))?;
    normalize_android_selected_conversation(&mut conversation)?;
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
    if let Some((existing_workspace_id, existing_conversation_id)) = connection
        .query_row(
            "SELECT workspace_id, conversation_id FROM desktop_selected_conversation_sync WHERE account_ref=?1 AND document_id=?2 ORDER BY last_synced_at_ms DESC LIMIT 1",
            params![account, document_id],
            |row| Ok((row.get::<_, String>(0)?, row.get::<_, String>(1)?)),
        )
        .optional()
        .map_err(|_| safe_error("云端恢复回执无法读取"))?
    {
        let workspace_exists: bool = connection
            .query_row(
                "SELECT EXISTS(SELECT 1 FROM workspaces WHERE id=?1)",
                [&existing_workspace_id],
                |row| row.get(0),
            )
            .unwrap_or(false);
        if workspace_exists {
            recover_local_conversation_occurrences(connection, &account, &existing_workspace_id, &existing_conversation_id)?;
            let remote_semantic_revision = record
                .get("revision")
                .and_then(Value::as_u64)
                .unwrap_or(1);
            let transaction = connection
                .transaction()
                .map_err(|_| safe_error("云端对话合并 transaction 无法开启"))?;
            let merge = merge_newer_remote_conversation(
                &transaction,
                &existing_workspace_id,
                &existing_conversation_id,
                &conversation,
                remote_semantic_revision,
            )?;
            transaction
                .execute(
                    "INSERT INTO desktop_selected_conversation_sync(account_ref,workspace_id,conversation_id,document_id,remote_revision,payload_hash,local_content_hash,last_synced_at_ms) VALUES(?1,?2,?3,?4,?5,?6,?7,?8) ON CONFLICT(account_ref,workspace_id,conversation_id) DO UPDATE SET document_id=excluded.document_id,remote_revision=excluded.remote_revision,payload_hash=excluded.payload_hash,local_content_hash=excluded.local_content_hash,last_synced_at_ms=excluded.last_synced_at_ms",
                    params![account, existing_workspace_id, existing_conversation_id, document_id, remote.revision, remote.payload_hash, restored_local_hash, now_ms()],
                )
                .map_err(|_| safe_error("云端恢复回执未更新"))?;
            transaction
                .commit()
                .map_err(|_| safe_error("云端对话合并未提交"))?;
            return Ok(RestoreReceipt {
                status: if merge == ExistingRemoteConversationMerge::Updated { "UPDATED_LOCAL" } else { "ALREADY_LOCAL" }.into(),
                workspace_id: existing_workspace_id,
                conversation_id: existing_conversation_id,
                title: selected_local_conversation_title(connection, &document_id)
                    .unwrap_or(restored_title),
                remote_revision: remote.revision,
            });
        }
        // The local workspace may have been removed while its old sync
        // receipt remained.  It is no longer a usable local copy, so discard
        // only that stale receipt before creating the recovered workspace.
        connection
            .execute(
                "DELETE FROM desktop_selected_conversation_sync WHERE account_ref=?1 AND workspace_id=?2 AND conversation_id=?3 AND document_id=?4",
                params![account, existing_workspace_id, existing_conversation_id, document_id],
            )
            .map_err(|_| safe_error("过期云端恢复回执无法清理"))?;
    }
    let workspace_id = format!(
        "workspace-cloud-{}",
        &sha256(format!("{account}:{document_id}").as_bytes())[..40]
    );
    let now = "2026-09-01T00:00:00Z";
    let mut exchange = json!({
        "format":"nfai.exchange","version":1,
        "export":{"id":format!("export-cloud-{}", &sha256(document_id.as_bytes())[..24]),"createdAt":now,"origin":{"platform":"DESKTOP","appVersion":"cloud-restore-v1"},"sensitivity":"NORMAL"},
        "projects":[],"conversations":[Value::Object(conversation)],"knowledge":[],"memory":[],"relations":[],
        "settings":{"theme":"SYSTEM","uiLanguage":"zh-CN"}
    });
    let semantic_hash = crate::semantic_hash(&exchange)?;
    exchange["export"]["semanticHash"] = Value::String(semantic_hash.clone());
    let encoded = serde_json::to_string(&exchange).map_err(|_| safe_error("恢复工作区无法编码"))?;
    let package_hash = sha256(encoded.as_bytes());
    if let Some(existing_hash) = connection
        .query_row(
            "SELECT package_hash FROM workspaces WHERE id=?1",
            [&workspace_id],
            |row| row.get::<_, String>(0),
        )
        .optional()
        .map_err(|_| safe_error("恢复工作区无法读取"))?
    {
        if existing_hash == package_hash {
            return Ok(RestoreReceipt {
                status: "ALREADY_RESTORED".into(),
                workspace_id,
                conversation_id,
                title: restored_title,
                remote_revision: remote.revision,
            });
        }
        return Err(safe_error("同一云端文档已有不同的本机恢复版本"));
    }
    let transaction = connection
        .transaction()
        .map_err(|_| safe_error("云端恢复 transaction 无法开启"))?;
    let result = (|| -> Result<(), String> {
        transaction.execute("INSERT INTO workspaces(id,title,semantic_hash,package_hash,created_at) VALUES(?1,?2,?3,?4,?5)",params![workspace_id,restored_title,semantic_hash,package_hash,now]).map_err(|_| safe_error("云端恢复工作区未写入"))?;
        transaction
            .execute(
                "INSERT INTO workspace_exchange(workspace_id,exchange_json) VALUES(?1,?2)",
                params![workspace_id, encoded],
            )
            .map_err(|_| safe_error("云端恢复对话未写入"))?;
        if restored_favorited {
            transaction
                .execute(
                    "INSERT INTO desktop_conversation_favorites(workspace_id,conversation_id,favorited_at_ms) VALUES(?1,?2,?3)
                     ON CONFLICT(workspace_id,conversation_id) DO UPDATE SET favorited_at_ms=excluded.favorited_at_ms",
                    params![workspace_id, conversation_id, now_ms()],
                )
                .map_err(|_| safe_error("云端恢复收藏状态未写入"))?;
        }
        transaction.execute("INSERT INTO desktop_selected_conversation_sync(account_ref,workspace_id,conversation_id,document_id,remote_revision,payload_hash,local_content_hash,last_synced_at_ms) VALUES(?1,?2,?3,?4,?5,?6,?7,?8)",params![account,workspace_id,conversation_id,document_id,remote.revision,remote.payload_hash,restored_local_hash,now_ms()]).map_err(|_| safe_error("云端恢复回执未写入"))?;
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
        if let Some(interests) = safe_settings
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
        transaction.execute("UPDATE desktop_cloud_account_state SET state='READY',last_success_at_ms=?2,last_error_code=NULL,revision=revision+1,updated_at_ms=?2 WHERE account_ref=?1",params![account,now_ms()]).map_err(|_| safe_error("云端恢复账号状态未写入"))?;
        let metadata_revision: i64 = transaction
            .query_row(
                "SELECT revision FROM sync_account_metadata WHERE account_ref=?1",
                [&account],
                |row| row.get(0),
            )
            .map_err(|_| safe_error("账号密钥状态不存在"))?;
        transaction.execute("UPDATE sync_account_metadata SET state='READY',revision=?2,direction_fact='EMPTY_LOCAL_REMOTE_PRESENT',last_error=NULL,updated_at=?3 WHERE account_ref=?1",params![account,metadata_revision+1,now]).map_err(|_| safe_error("云端恢复状态未写入"))?;
        transaction.execute("INSERT INTO sync_intents(intent_id,account_ref,expected_revision,resulting_revision,resulting_state,created_at) VALUES(?1,?2,?3,?4,'READY',?5)",params![format!("desktop-cloud-restore-{}",now_ms()),account,metadata_revision,metadata_revision+1,now]).map_err(|_| safe_error("云端恢复意图回执未写入"))?;
        Ok(())
    })();
    if let Err(error) = result.and_then(|_| {
        transaction
            .commit()
            .map_err(|_| safe_error("云端恢复未原子提交"))
    }) {
        return Err(error);
    }
    recover_local_conversation_occurrences(connection, &account, &workspace_id, &conversation_id)?;
    write_diagnostic(connection, Some(&account), "CLOUD_RESTORE", "SUCCESS", None);
    write_notification(
        connection,
        Some(&account),
        "SUCCESS",
        "云端对话已恢复为新的本机工作区，未覆盖现有数据。",
    );
    Ok(RestoreReceipt {
        status: "RESTORED_AS_NEW_WORKSPACE".into(),
        workspace_id,
        conversation_id,
        title: restored_title,
        remote_revision: remote.revision,
    })
}

/// Reading cloud history is a complete user action: every eligible cloud conversation is
/// restored immediately.  The renderer never receives document IDs as a second, manual
/// recovery step.  Each existing restore remains individually atomic, so an interrupted
/// batch reports partial failures without hiding successfully restored conversations.
pub fn restore_all_remote_conversations<C: CredentialStore, G: CloudGateway>(
    connection: &mut Connection,
    credentials: &C,
    gateway: &G,
) -> Result<RestoreAllReceipt, String> {
    let account = read_session(credentials)?.map(|s| sync_state_v1::account_ref(&s.user_id)).transpose()?;
    // Snapshot receipts before the network request. Do not prune a selection
    // created or updated while that request was in flight.
    let before: Vec<(String,String,String,i64)> = if let Some(account) = account.as_ref() {
        let mut statement = connection.prepare("SELECT workspace_id,conversation_id,document_id,last_synced_at_ms FROM desktop_selected_conversation_sync WHERE account_ref=?1")
            .map_err(|_| safe_error("本机同步列表无法读取"))?;
        let rows = statement.query_map([account], |r| Ok((r.get(0)?,r.get(1)?,r.get(2)?,r.get(3)?)))
            .map_err(|_| safe_error("本机同步列表无法读取"))?;
        rows.collect::<Result<Vec<_>,_>>().map_err(|_| safe_error("本机同步列表无法读取"))?
    } else { Vec::new() };
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
    let present_ids = documents.iter().map(|d| envelope_document_id(&d.envelope)).collect::<Result<BTreeSet<_>,_>>();
    // Reading never uploads legacy rewrites. Explicit selected sync owns
    // those writes; their failure must not delay or abort cloud discovery.
    let upgraded_legacy_direct_count = 0;
    let mut restored = Vec::with_capacity(documents.len());
    let mut failed_count = 0;
    let mut skipped_legacy_count = 0;
    let mut cloud_pinned_conversation_ids = BTreeSet::new();
    let mut cloud_list_presentation_present = false;
    for document in documents {
        let Ok(envelope) = serde_json::from_str::<Value>(&document.envelope) else {
            failed_count += 1;
            continue;
        };
        let Ok(document_id) = envelope_document_id(&document.envelope) else {
            failed_count += 1;
            continue;
        };
        if document_id == CLOUD_LIST_PRESENTATION_DOCUMENT_ID {
            if let Ok(pinned) = cloud_list_presentation_ids(&document) {
                cloud_pinned_conversation_ids = pinned;
                cloud_list_presentation_present = true;
            }
            continue;
        }
        if envelope.get("format").and_then(Value::as_str) != Some(sync_v1::DIRECT_ENVELOPE) {
            skipped_legacy_count += 1;
            continue;
        }
        match restore_remote_envelope(connection, credentials, document) {
            Ok(receipt) => restored.push(receipt),
            Err(_) => failed_count += 1,
        }
    }
    if skipped_legacy_count != 0 {
        write_diagnostic(
            connection,
            None,
            "CLOUD_LIST_LEGACY_SKIPPED",
            "SUCCESS",
            Some("LEGACY_DIRECT_PROTOCOL_RETIRED"),
        );
    }
    let cloud_conversation_keys = if let (Some(account), Ok(present_ids)) = (account.as_ref(), present_ids) {
        let transaction = connection.transaction().map_err(|_| safe_error("云端列表核对事务未开启"))?;
        for (workspace, conversation, document, timestamp) in before {
            if present_ids.contains(&document) { continue; }
            let removed = transaction.execute("DELETE FROM desktop_selected_conversation_sync WHERE account_ref=?1 AND workspace_id=?2 AND conversation_id=?3 AND document_id=?4 AND last_synced_at_ms=?5",
                params![account,workspace,conversation,document,timestamp]).map_err(|_| safe_error("失效同步身份未清理"))?;
            if removed > 0 {
                transaction.execute("DELETE FROM desktop_sync_jobs WHERE account_ref=?1 AND workspace_id=?2 AND conversation_id=?3 AND stage<>'UNKNOWN'",
                    params![account,workspace,conversation]).map_err(|_| safe_error("失效同步任务未清理"))?;
            }
        }
        transaction.commit().map_err(|_| safe_error("云端列表核对未保存"))?;
        let mut statement = connection.prepare("SELECT workspace_id || ':' || conversation_id FROM desktop_selected_conversation_sync WHERE account_ref=?1")
            .map_err(|_| safe_error("云端列表身份无法读取"))?;
        let rows = statement.query_map([account], |r| r.get(0)).map_err(|_| safe_error("云端列表身份无法读取"))?;
        Some(rows.collect::<Result<Vec<String>,_>>().map_err(|_| safe_error("云端列表身份无法读取"))?)
    } else { None };
    Ok(RestoreAllReceipt {
        restored,
        failed_count,
        cloud_conversation_keys,
        skipped_legacy_count,
        cloud_pinned_conversation_ids: cloud_pinned_conversation_ids.into_iter().collect(),
        cloud_list_presentation_present,
        upgraded_legacy_direct_count,
    })
}


fn cloud_list_presentation_ids(remote: &RemoteEnvelope) -> Result<BTreeSet<String>, String> {
    let payload = sync_v1::open_direct(
        &remote.envelope,
        APP_ID,
        CLOUD_LIST_PRESENTATION_DOCUMENT_ID,
        remote.revision,
    )?;
    let records = payload
        .get("records")
        .and_then(Value::as_array)
        .ok_or_else(|| safe_error("云端列表投影无效"))?;
    let record = records
        .iter()
        .find(|record| {
            record.get("kind").and_then(Value::as_str) == Some("safe_settings")
                && record.get("id").and_then(Value::as_str)
                    == Some(CLOUD_LIST_PRESENTATION_DOCUMENT_ID)
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
    read_session(credentials)?.ok_or_else(|| safe_error("请先登录 Google 账号"))?;
    let remote = gateway
        .read(CLOUD_LIST_PRESENTATION_DOCUMENT_ID)
        .map_err(|failure| safe_error(cloud_read_failure_message(failure)))?;
    let (expected_revision, mut values) = match remote {
        Some(remote) => (remote.revision, cloud_list_presentation_ids(&remote)?),
        None => (0, BTreeSet::new()),
    };
    if pinned {
        values.insert(conversation_id.to_owned());
    } else {
        values.remove(conversation_id);
    }
    let payload = json!({
        "format":"nfai.sync.payload", "protocolVersion":1, "schemaVersion":1,
        "appId":APP_ID, "documentId":CLOUD_LIST_PRESENTATION_DOCUMENT_ID,
        "revision":expected_revision + 1,
        "records":[{"kind":"safe_settings","id":CLOUD_LIST_PRESENTATION_DOCUMENT_ID,
          "revision":expected_revision + 1,"classification":"NORMAL",
          "content":{"type":"CLOUD_CONVERSATION_LIST_V1","pinnedConversationIds":values.iter().collect::<Vec<_>>()}}]
    });
    let envelope = sync_v1::seal_direct(payload).map_err(|_| safe_error("云端列表置顶无法准备"))?;
    let (revision, hash) = gateway
        .commit(
            CLOUD_LIST_PRESENTATION_DOCUMENT_ID,
            expected_revision,
            &envelope,
        )
        .map_err(|failure| safe_error(cloud_read_failure_message(failure)))?;
    let read_back = gateway
        .read(CLOUD_LIST_PRESENTATION_DOCUMENT_ID)
        .map_err(|failure| safe_error(cloud_read_failure_message(failure)))?
        .ok_or_else(|| safe_error("云端列表置顶回读失败"))?;
    if read_back.revision != revision || read_back.payload_hash != hash {
        return Err(safe_error("云端列表置顶回读不一致"));
    }
    Ok(values.into_iter().collect())
}

/// Android's selected-conversation wire shape is intentionally text-only but names tree fields
/// differently from Desktop's older local exchange shape. Normalize it at the encrypted boundary
/// so neither the restore writer nor user-visible history needs a second schema.
fn normalize_android_selected_conversation(
    conversation: &mut serde_json::Map<String, Value>,
) -> Result<(), String> {
    let Some(nodes) = conversation.get("nodes").and_then(Value::as_array) else {
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
            let model_id = usage
                .get("modelId")
                .and_then(Value::as_str)
                .filter(|value| !value.is_empty() && value.len() <= 256);
            let display_name = usage
                .get("modelDisplayName")
                .and_then(Value::as_str)
                .filter(|value| !value.is_empty() && value.len() <= 256);
            if let (Some(model_id), Some(display_name)) = (model_id, display_name) {
                restored["modelSnapshot"] = json!({"modelId":model_id,"displayName":display_name});
                restored["usage"] = json!({});
                if let Some(value) = usage.get("inputTokens").and_then(Value::as_u64) {
                    restored["usage"]["inputTokens"] = json!(value);
                }
                if let Some(value) = usage.get("outputTokens").and_then(Value::as_u64) {
                    restored["usage"]["outputTokens"] = json!(value);
                }
                if let Some(value) = usage.get("totalTokens").and_then(Value::as_u64) {
                    restored["usage"]["totalTokens"] = json!(value);
                }
                if let Some(value) = usage.get("cachedInputTokens").and_then(Value::as_u64) {
                    restored["usage"]["cachedInputTokens"] = json!(value);
                }
                if let Some(value) = usage.get("reasoningTokens").and_then(Value::as_u64) {
                    restored["usage"]["reasoningTokens"] = json!(value);
                }
                if let Some(value) = usage.get("costTotalMicros").and_then(Value::as_u64) {
                    restored["chargeMicros"] = json!(value);
                    restored["currencyCode"] = usage
                        .get("costCurrencyCode")
                        .cloned()
                        .unwrap_or_else(|| json!("USD"));
                    restored["costPriceVersion"] = usage
                        .get("costPriceVersion")
                        .cloned()
                        .unwrap_or_else(|| json!("android-history-v1"));
                    restored["costSource"] = usage
                        .get("costSource")
                        .cloned()
                        .unwrap_or_else(|| json!("PROVIDER_RESPONSE"));
                }
            }
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
        .filter(|usage| usage.get("inputTokens").and_then(Value::as_u64).is_some()
            && usage.get("outputTokens").and_then(Value::as_u64).is_some())
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
    Some(json!({
        "modelId":model_id,
        "modelDisplayName":display_name,
        "inputTokens":usage_token("inputTokens"),
        "outputTokens":usage_token("outputTokens"),
        "totalTokens":usage_token("totalTokens"),
        "cachedInputTokens":usage_token("cachedInputTokens"),
        "reasoningTokens":usage_token("reasoningTokens"),
        "costPriceVersion": message.get("costPriceVersion").and_then(Value::as_str).filter(|value| !value.is_empty() && value.len() <= 128).or_else(|| cost.map(|_| "desktop-provider-reported-v1")),
        "costCurrencyCode": message.get("currencyCode").and_then(Value::as_str).filter(|value| value.len() == 3 && value.bytes().all(|byte| byte.is_ascii_uppercase())).or_else(|| cost.map(|_| "USD")),
        "costTotalMicros":cost,
        "costSource":message.get("costSource").and_then(Value::as_str).filter(|value| matches!(*value,"PROVIDER_RESPONSE"|"LOCAL_ESTIMATE")).or_else(|| cost.map(|_| "PROVIDER_RESPONSE")),
    }))
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
    let document_id = format!("conversation-{}", &sha256(conversation_id.as_bytes())[..40]);
    let remote = gateway
        .read(&document_id)
        .map_err(|failure| safe_error(cloud_read_failure_message(failure)))?;
    if let Some(remote) = remote {
        let deleted = gateway
            .delete(&document_id, remote.revision)
            .map_err(|failure| safe_error(cloud_commit_failure_message(failure)))?;
        if !deleted {
            return Err(safe_error("云端对话未删除"));
        }
    }
    connection
        .execute(
            "DELETE FROM desktop_selected_conversation_sync WHERE account_ref=?1 AND workspace_id=?2 AND conversation_id=?3",
            params![account, workspace_id, conversation_id],
        )
        .map_err(|_| safe_error("本机同步回执未清除"))?;
    connection.execute("DELETE FROM desktop_sync_jobs WHERE account_ref=?1 AND workspace_id=?2 AND conversation_id=?3 AND stage<>'UNKNOWN'",
        params![account,workspace_id,conversation_id]).map_err(|_| safe_error("失效同步任务未清理"))?;
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
    connection.execute("UPDATE desktop_sync_jobs SET stage='UNKNOWN',safe_error_code='COMMIT_RESULT_UNKNOWN',updated_at_ms=?2 WHERE job_id=?1", params![job_id,now_ms()]).ok();
    // A commit may have reached the service.  This state is pending
    // verification, not a failed write and never authorizes a duplicate send.
    connection.execute("UPDATE desktop_cloud_account_state SET state='VERIFYING',last_error_code='COMMIT_RESULT_UNKNOWN',updated_at_ms=?2 WHERE account_ref=?1", params![account,now_ms()]).ok();
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
    let candidates: Vec<String> = {
        let mut statement = connection.prepare(
            "SELECT c.value FROM workspace_exchange w, json_each(w.exchange_json,'$.conversations') c
             WHERE w.workspace_id<>?1 AND json_extract(c.value,'$.id')=?2
             AND coalesce(json_extract(c.value,'$.deleted'),0)=0
             AND NOT EXISTS(SELECT 1 FROM desktop_selected_conversation_sync s
                 WHERE s.workspace_id=w.workspace_id AND s.conversation_id=?2 AND s.account_ref<>?3)
             ORDER BY w.workspace_id LIMIT 32"
        ).map_err(|_| safe_error("本机同源对话无法核对"))?;
        let rows = statement.query_map(params![workspace_id, conversation_id, account], |row| row.get(0))
            .map_err(|_| safe_error("本机同源对话无法核对"))?;
        rows.collect::<Result<Vec<_>, _>>().map_err(|_| safe_error("本机同源对话无法读取"))?
    };
    if candidates.is_empty() { return Ok(()); }
    let transaction = connection.transaction().map_err(|_| safe_error("本机对话修复事务未开启"))?;
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
        merge_remote_additions_for_local_commit(&transaction, workspace_id, conversation_id,
            recovery.as_object().ok_or_else(|| safe_error("本机同源对话结构无效"))?,
            target.get("revision").and_then(Value::as_u64).unwrap_or(1))?;
    }
    transaction.commit().map_err(|_| safe_error("本机对话修复未提交"))
}

pub fn sync_selected_conversation<C: CredentialStore, G: CloudGateway>(
    connection: &mut Connection,
    credentials: &C,
    gateway: &G,
    workspace_id: &str,
    conversation_id: &str,
) -> Result<SyncReceipt, String> {
    sync_selected_conversation_internal(connection, credentials, gateway, workspace_id, conversation_id, false)
}

pub fn continue_selected_conversation<C: CredentialStore, G: CloudGateway>(
    connection: &mut Connection, credentials: &C, gateway: &G,
    workspace_id: &str, conversation_id: &str,
) -> Result<SyncReceipt, String> {
    sync_selected_conversation_internal(connection, credentials, gateway, workspace_id, conversation_id, true)
}

fn sync_selected_conversation_internal<C: CredentialStore, G: CloudGateway>(
    connection: &mut Connection, credentials: &C, gateway: &G,
    workspace_id: &str, conversation_id: &str, require_existing_selection: bool,
) -> Result<SyncReceipt, String> {
    let session = read_session(credentials)?.ok_or_else(|| safe_error("请先登录 Google 账号"))?;
    let account = sync_state_v1::account_ref(&session.user_id)?;
    let unresolved_unknown: i64 = connection.query_row(
        "SELECT COUNT(*) FROM desktop_sync_jobs WHERE account_ref=?1 AND workspace_id=?2 AND conversation_id=?3 AND stage='UNKNOWN'",
        params![account,workspace_id,conversation_id],
        |row| row.get(0),
    ).unwrap_or(0);
    if unresolved_unknown > 0 {
        return Ok(SyncReceipt {
            status: "UNKNOWN".into(),
            safe_code: Some("COMMIT_RESULT_UNKNOWN".into()),
            synced_at_ms: None,
        });
    }
    let document_id = format!("conversation-{}", &sha256(conversation_id.as_bytes())[..40]);
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
    let remote = match gateway.read(&document_id) {
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
        let transaction = connection.transaction().map_err(|_| safe_error("同步身份清理事务未开启"))?;
        transaction.execute("DELETE FROM desktop_selected_conversation_sync WHERE account_ref=?1 AND workspace_id=?2 AND conversation_id=?3", params![account,workspace_id,conversation_id])
            .map_err(|_| safe_error("失效同步身份未清理"))?;
        transaction.execute("DELETE FROM desktop_sync_jobs WHERE account_ref=?1 AND workspace_id=?2 AND conversation_id=?3 AND stage<>'UNKNOWN'", params![account,workspace_id,conversation_id])
            .map_err(|_| safe_error("失效同步任务未清理"))?;
        transaction.commit().map_err(|_| safe_error("同步身份清理未保存"))?;
        return Err(safe_error("该对话已从云端删除，已停止续同步；本机内容保留。"));
    }
    recover_local_conversation_occurrences(connection, &account, workspace_id, conversation_id)?;
    // Before this explicit local write, fold any verified remote additions
    // into the local source.  Optimistic revision alone cannot tell which
    // device has a new turn: without this union a stale desktop could commit a
    // valid envelope that simply omits the phone's latest completed answer.
    // Old non-direct documents are intentionally migrated by the local source;
    // a malformed current direct document is rejected rather than overwritten.
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
    if remote_is_direct {
        let remote = remote.as_ref().expect("direct remote exists");
        let payload = sync_v1::open_direct(&remote.envelope, APP_ID, &document_id, remote.revision)
            .map_err(|_| safe_error("云端对话完整性校验失败"))?;
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
    connection.execute("UPDATE desktop_cloud_account_state SET state='SYNCING',last_error_code=NULL,updated_at_ms=?2 WHERE account_ref=?1", params![account,now_ms()]).map_err(|_| safe_error("同步状态未保存"))?;
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
    let job_id = format!("sync-job-{}-{}", &account[..12], now_ms());
    connection.execute("INSERT INTO desktop_sync_jobs(job_id,account_ref,workspace_id,conversation_id,document_id,stage,payload_hash,expected_remote_revision,attempt,next_retry_at_ms,safe_error_code,created_at_ms,updated_at_ms) VALUES(?1,?2,?3,?4,?5,'COMMITTING',?6,?7,1,NULL,NULL,?8,?8)", params![job_id,account,workspace_id,conversation_id,document_id,payload_hash,expected as i64,now_ms()]).map_err(|_| safe_error("同步任务未保存"))?;
    let committed = match gateway.commit(&document_id, expected, &envelope) {
        Ok(value) => value,
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
    let readback = match gateway.read(&document_id) {
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
    let timestamp = now_ms();
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
    let mut statement = connection.prepare("SELECT j.job_id,j.workspace_id,j.conversation_id,j.payload_hash FROM desktop_sync_jobs j JOIN desktop_selected_conversation_sync s ON s.account_ref=j.account_ref AND s.workspace_id=j.workspace_id AND s.conversation_id=j.conversation_id WHERE j.account_ref=?1 AND j.stage IN ('TITLE_PENDING','DELETE_PENDING') AND coalesce(j.next_retry_at_ms,0)<=?2 ORDER BY j.updated_at_ms LIMIT 32")
        .map_err(|_| safe_error("标题续同步任务无法读取"))?;
    let targets = statement.query_map(params![account,now_ms()], |r| Ok((r.get(0)?,r.get(1)?,r.get(2)?,r.get(3)?)))
        .map_err(|_| safe_error("标题续同步任务无法读取"))?.collect::<Result<Vec<_>,_>>().map_err(|_| safe_error("标题续同步任务无效"))?;
    Ok(targets)
}

pub(crate) fn finish_pending_title_attempt(connection: &Connection, job: &str, marker: &str, result: &Result<SyncReceipt,String>) -> Result<(),String> {
    if result.as_ref().is_ok_and(|r| matches!(r.status.as_str(),"SYNCED"|"UP_TO_DATE")) {
        connection.execute("DELETE FROM desktop_sync_jobs WHERE job_id=?1 AND payload_hash=?2",params![job,marker])
            .map_err(|_| safe_error("标题续同步回执未确认"))?;
    } else {
        let conflict = result.as_ref().err().is_some_and(|e| e.contains("标题版本冲突"));
        connection.execute("UPDATE desktop_sync_jobs SET stage=CASE WHEN stage='DELETE_PENDING' THEN stage ELSE ?3 END,attempt=attempt+1,next_retry_at_ms=?4+MIN(1800000,30000*(1 << MIN(attempt,6))),updated_at_ms=?4 WHERE job_id=?1 AND payload_hash=?2",params![job,marker,if conflict { "TITLE_CONFLICT" } else { "TITLE_PENDING" },now_ms()])
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
    if require_private_sync_materials(connection, credentials, &account).is_err() {
        return Ok(Vec::new());
    }
    let enabled = connection.query_row(
        "SELECT periodic_enabled=1 AND state='READY' FROM desktop_cloud_account_state WHERE account_ref=?1",
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
            "SELECT document_id,payload_hash,expected_remote_revision FROM desktop_sync_jobs WHERE account_ref=?1 AND workspace_id=?2 AND conversation_id=?3 AND stage='UNKNOWN' ORDER BY updated_at_ms DESC LIMIT 1",
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
    match remote {
        Some(remote)
            if remote.revision == expected_revision as u64 + 1
                && remote.payload_hash == payload_hash =>
        {
            let (_, local_hash) = conversation_payload(
                connection,
                workspace_id,
                conversation_id,
                &document_id,
                remote.revision,
            )?;
            let timestamp = now_ms();
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
                    "UPDATE desktop_sync_jobs SET stage='COMPLETED',safe_error_code=NULL,updated_at_ms=?4 WHERE account_ref=?1 AND workspace_id=?2 AND conversation_id=?3 AND stage='UNKNOWN'",
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
        _ => {
            // A different verified remote revision is not a user-facing
            // content conflict.  It can be a phone update that landed while
            // this client was waiting for its uncertain submit result.  Mark
            // only the old attempt as superseded, then take the ordinary
            // read-union-commit path.  That path preserves both completed
            // trees and never re-sends the unknown envelope.
            connection.execute("UPDATE desktop_sync_jobs SET stage='SUPERSEDED',safe_error_code='REMOTE_CHANGED',updated_at_ms=?4 WHERE account_ref=?1 AND workspace_id=?2 AND conversation_id=?3 AND stage='UNKNOWN'", params![account,workspace_id,conversation_id,now_ms()]).map_err(|_| safe_error("核对任务未更新"))?;
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
    if enabled {
        require_private_sync_materials(connection, credentials, &account)?;
    }
    connection.execute("UPDATE desktop_cloud_account_state SET periodic_enabled=?2,revision=revision+1,updated_at_ms=?3 WHERE account_ref=?1", params![account,enabled as i64,now_ms()]).map_err(|_| safe_error("定期同步设置未保存"))?;
    Ok(())
}

pub fn choose_selected_local_start<C: CredentialStore>(
    connection: &mut Connection,
    credentials: &C,
) -> Result<(), String> {
    let session = read_session(credentials)?.ok_or_else(|| safe_error("请先登录 Google 账号"))?;
    let account = sync_state_v1::account_ref(&session.user_id)?;
    ensure_initial_data_key_for_direction(connection, credentials, &account)?;
    require_private_sync_materials(connection, credentials, &account)?;
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
    let Some(session) = read_session(credentials)? else {
        return Ok(());
    };
    let account = sync_state_v1::account_ref(&session.user_id)?;
    let mut store = SqliteMetadataStore { connection };
    if let Some(metadata) = store.metadata(&account)? {
        if !matches!(
            metadata.state,
            sync_state_v1::State::SignedOut | sync_state_v1::State::SignedOutKeepLocal
        ) {
            sync_state_v1::sign_out_keep_local(
                &mut store,
                &format!("desktop-signout-{}", now_ms()),
                &account,
                metadata.revision,
            )?;
        }
    }
    credentials
        .delete(SESSION_SERVICE, "active")
        .map_err(|_| safe_error("本机账号会话未清除"))?;
    store.connection.execute("UPDATE desktop_cloud_account_state SET state='SIGNED_OUT_KEEP_LOCAL',periodic_enabled=0,revision=revision+1,updated_at_ms=?2 WHERE account_ref=?1", params![account,now_ms()]).map_err(|_| safe_error("退出状态未保存"))?;
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
    let account = session
        .as_ref()
        .and_then(|value| sync_state_v1::account_ref(&value.user_id).ok());
    let row = account.as_ref().and_then(|account| connection.query_row("SELECT state,periodic_enabled,recovery_confirmed,last_success_at_ms,last_error_code,device_id,pending_recovery_created_at_ms FROM desktop_cloud_account_state WHERE account_ref=?1", [account], |row| Ok((row.get::<_,String>(0)?,row.get::<_,i64>(1)? != 0,row.get::<_,i64>(2)? != 0,row.get::<_,Option<i64>>(3)?,row.get::<_,Option<String>>(4)?,row.get::<_,String>(5)?,row.get::<_,Option<i64>>(6)?))).optional().ok().flatten());
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
    let rotation_pending = account
        .as_ref()
        .and_then(|value| {
            connection
                .query_row(
                    "SELECT COUNT(*) FROM desktop_recovery_rotation WHERE account_ref=?1",
                    [value],
                    |row| row.get::<_, u64>(0),
                )
                .ok()
        })
        .unwrap_or(0)
        > 0;
    let diagnostics = connection.prepare("SELECT event,outcome,safe_code,occurred_at_ms FROM desktop_sync_diagnostics ORDER BY occurred_at_ms DESC LIMIT 12").map_err(|_| safe_error("同步诊断无法读取"))?.query_map([], |row| Ok(DiagnosticProjection { event:row.get(0)?, outcome:row.get(1)?, safe_code:row.get(2)?, occurred_at_ms:row.get(3)? })).map_err(|_| safe_error("同步诊断无法读取"))?.collect::<Result<Vec<_>,_>>().map_err(|_| safe_error("同步诊断无效"))?;
    let notifications = connection.prepare("SELECT kind,message,occurred_at_ms,unread FROM desktop_sync_notifications ORDER BY occurred_at_ms DESC LIMIT 8").map_err(|_| safe_error("同步通知无法读取"))?.query_map([], |row| Ok(NotificationProjection { kind:row.get(0)?, message:row.get(1)?, occurred_at_ms:row.get(2)?, unread:row.get::<_,i64>(3)? != 0 })).map_err(|_| safe_error("同步通知无法读取"))?.collect::<Result<Vec<_>,_>>().map_err(|_| safe_error("同步通知无效"))?;
    let recovery_material_ready = account.as_ref().is_some_and(|value| {
        row.as_ref().is_some_and(|state| state.2)
            && private_recovery_material_present(credentials, value)
    });
    let sync_materials_ready = account.as_ref().is_some_and(|value| {
        row.as_ref().is_some_and(|state| state.2)
            && private_sync_materials_present(credentials, value)
    });
    let material_reconnect_required =
        session.is_some() && row.as_ref().is_some_and(|value| value.2) && !recovery_material_ready;
    let state = if material_reconnect_required {
        "AUTHENTICATED_NEEDS_RECOVERY_MATERIAL".into()
    } else {
        row.as_ref()
            .map(|value| value.0.clone())
            .unwrap_or_else(|| {
                if configured {
                    "SIGNED_OUT".into()
                } else {
                    "NOT_CONFIGURED".into()
                }
            })
    };
    Ok(AccountProjection {
        configured,
        state: state.clone(),
        email: session.as_ref().map(|value| value.email.clone()),
        display_name: session
            .as_ref()
            .and_then(|value| value.display_name.clone()),
        avatar_data_url,
        recovery_state: if recovery_material_ready {
            "CONFIRMED".into()
        } else if material_reconnect_required {
            "NEEDS_EXISTING_CODE".into()
        } else if session.is_some() {
            "NEEDS_CONFIRMATION".into()
        } else {
            "UNAVAILABLE".into()
        },
        recovery_confirmation_pending: row
            .as_ref()
            .is_some_and(|state| !state.2 && state.6.is_some()),
        periodic_enabled: sync_materials_ready && row.as_ref().is_some_and(|value| value.1),
        rotation_pending,
        last_success_at_ms: row.as_ref().and_then(|value| value.3),
        selected_conversation_count: selected,
        synced_conversation_keys,
        sync_stage: state,
        notice: if material_reconnect_required {
            Some("RECOVERY_MATERIAL_REQUIRED".into())
        } else {
            row.as_ref().and_then(|value| value.4.clone())
        },
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
    use std::{cell::RefCell, collections::BTreeMap};

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
        let mut c = Connection::open_in_memory().unwrap();
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
    fn stale_recovery_confirmation_without_private_material_requires_existing_code() {
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
        assert_eq!(projection.state, "AUTHENTICATED_NEEDS_RECOVERY_MATERIAL");
        assert_eq!(projection.recovery_state, "NEEDS_EXISTING_CODE");
        assert!(!projection.periodic_enabled);

        let error = set_periodic_enabled(&c, &keys, true).unwrap_err();
        assert!(error.contains("已有恢复码"));
        let state: String = c
            .query_row(
                "SELECT state FROM desktop_cloud_account_state WHERE account_ref=?1",
                [&account],
                |row| row.get(0),
            )
            .unwrap();
        assert_eq!(state, "AUTHENTICATED_NEEDS_RECOVERY_MATERIAL");
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
        assert_eq!(
            projection.state,
            "AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION"
        );
        assert_eq!(projection.recovery_state, "NEEDS_CONFIRMATION");
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
        assert!(credentials.key_path().starts_with(root.path()));
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
    fn legacy_confirmed_account_repairs_only_its_missing_initial_data_key_on_direction_choice() {
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

        assert_eq!(keys.read(DATA_KEY_SERVICE, &account).unwrap().len(), 32);
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
        let cloud = Readback(RemoteEnvelope {
            revision: 1,
            payload_hash: "payload-safe".into(),
            envelope: "{}".into(),
        });
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
        let cloud = Cloud(RefCell::new(RemoteEnvelope {
            revision: 1,
            payload_hash: "remote-change".into(),
            envelope: "{}".into(),
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
            let mut stream = TcpStream::connect(address).unwrap();
            stream.write_all(b"GET /oauth/callback?state=state-safe&code=code-safe HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n").unwrap();
        });
        assert_eq!(callback_code(listener, "state-safe").unwrap(), "code-safe");
        client.join().unwrap();
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
    fn cloud_read_failures_keep_a_safe_specific_recovery_path() {
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
            "AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION"
        );
    }
}
