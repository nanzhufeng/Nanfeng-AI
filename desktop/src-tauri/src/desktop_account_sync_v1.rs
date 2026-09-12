//! Desktop Google account and selected-conversation encrypted sync owner.
//! Secrets live only in the app-owned credential store; SQLite keeps safe state, receipts and diagnostics.
use crate::sync_state_v1::{self, CredentialStore, SqliteMetadataStore};
use crate::sync_v1::{self, AccountWrappingMaterial};
use base64::{engine::general_purpose::URL_SAFE_NO_PAD, Engine as _};
use getrandom::fill as random_fill;
use reqwest::{blocking::Client, redirect::Policy, Url};
use rusqlite::{params, Connection, OptionalExtension, Transaction};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use std::{
    io::{Read, Write},
    net::{IpAddr, Ipv4Addr, SocketAddr, TcpListener},
    process::Command,
    time::{Duration, Instant, SystemTime, UNIX_EPOCH},
};
use zeroize::{Zeroize, Zeroizing};

const APP_ID: &str = "com.nanzhufeng.ai";
const SESSION_SERVICE: &str = "com.nanzhufeng.ai.desktop.google-session.v1";
const RECOVERY_SERVICE: &str = "com.nanzhufeng.ai.desktop.recovery-wrap.v1";
const RECOVERY_ROTATION_SERVICE: &str = "com.nanzhufeng.ai.desktop.recovery-rotation.v1";
const DATA_KEY_SERVICE: &str = "com.nanzhufeng.ai.p7b.v1";
const AVATAR_MAX_BYTES: usize = 2 * 1024 * 1024;
const SESSION_MAX_BYTES: usize = 16 * 1024;

/// Account-sync credential adapter. Session JSON is bounded but may exceed the
/// fixed 32-byte key material accepted by the older P7-B adapter.
pub struct DesktopAccountCredentialStore;
fn valid_credential_scope(service: &str, account: &str) -> bool {
    if service == SESSION_SERVICE {
        return account == "active";
    }
    matches!(
        service,
        RECOVERY_SERVICE | RECOVERY_ROTATION_SERVICE | DATA_KEY_SERVICE
    ) && account.len() == 32
        && account
            .bytes()
            .all(|value| value.is_ascii_hexdigit() && !value.is_ascii_uppercase())
}
impl CredentialStore for DesktopAccountCredentialStore {
    fn save(&self, service: &str, account: &str, secret: &[u8]) -> Result<(), String> {
        let valid = valid_credential_scope(service, account)
            && match service {
                SESSION_SERVICE => !secret.is_empty() && secret.len() <= SESSION_MAX_BYTES,
                RECOVERY_SERVICE | RECOVERY_ROTATION_SERVICE | DATA_KEY_SERVICE => {
                    secret.len() == 32
                }
                _ => false,
            };
        if !cfg!(target_os = "macos") || !valid {
            return Err(safe_error("本机凭据未通过安全校验"));
        }
        #[cfg(target_os = "macos")]
        return security_framework::passwords::set_generic_password(service, account, secret)
            .map_err(|_| safe_error("本机凭据未安全保存"));
        #[cfg(not(target_os = "macos"))]
        Err(safe_error("本机凭据存储不可用"))
    }
    fn read(&self, service: &str, account: &str) -> Result<Vec<u8>, String> {
        if !cfg!(target_os = "macos") || !valid_credential_scope(service, account) {
            return Err(safe_error("本机凭据未通过安全校验"));
        }
        #[cfg(target_os = "macos")]
        {
            let secret = security_framework::passwords::get_generic_password(service, account)
                .map_err(|_| safe_error("本机凭据不可用"))?;
            let valid = if service == SESSION_SERVICE {
                !secret.is_empty() && secret.len() <= SESSION_MAX_BYTES
            } else {
                secret.len() == 32
            };
            return if valid {
                Ok(secret)
            } else {
                Err(safe_error("本机凭据格式无效"))
            };
        }
        #[cfg(not(target_os = "macos"))]
        Err(safe_error("本机凭据存储不可用"))
    }
    fn delete(&self, service: &str, account: &str) -> Result<(), String> {
        if !cfg!(target_os = "macos") || !valid_credential_scope(service, account) {
            return Err(safe_error("本机凭据未通过安全校验"));
        }
        #[cfg(target_os = "macos")]
        return security_framework::passwords::delete_generic_password(service, account)
            .map_err(|_| safe_error("本机凭据未清除"));
        #[cfg(not(target_os = "macos"))]
        Err(safe_error("本机凭据存储不可用"))
    }
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
    pub periodic_enabled: bool,
    pub rotation_pending: bool,
    pub last_success_at_ms: Option<i64>,
    pub selected_conversation_count: u64,
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
            created_at_ms: now_ms(),
            rotation: false,
        },
    ))
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
    credentials
        .save(
            RECOVERY_SERVICE,
            &pending.account_ref,
            &pending.wrapping_key,
        )
        .map_err(|_| safe_error("恢复保护无法安全保存"))?;
    let salt = URL_SAFE_NO_PAD.encode(&pending.salt);
    let current = connection
        .query_row(
            "SELECT recovery_generation FROM desktop_cloud_account_state WHERE account_ref=?1",
            [&pending.account_ref],
            |row| row.get::<_, i64>(0),
        )
        .map_err(|_| safe_error("账号状态不存在"))?;
    connection.execute("UPDATE desktop_cloud_account_state SET recovery_confirmed=1,recovery_salt_b64=?2,recovery_generation=?3,state='DIRECTION_REQUIRED',revision=revision+1,updated_at_ms=?4 WHERE account_ref=?1", params![pending.account_ref,salt,current+1,now_ms()]).map_err(|_| safe_error("恢复保护状态未保存"))?;
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
    Ok(())
}

pub fn prepare_custom_recovery_rotation<C: CredentialStore>(
    connection: &Connection,
    credentials: &C,
    code: &str,
) -> Result<(RecoveryCodeProjection, PendingRecovery), String> {
    if code.chars().count() < 12 || code.len() > 128 || code.trim() != code || code.chars().any(char::is_control) {
        return Err(safe_error("恢复码需为 12–128 字节，且不能包含首尾空格或控制字符"));
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

#[derive(Debug, Clone)]
pub struct RemoteEnvelope {
    pub revision: u64,
    pub payload_hash: String,
    pub envelope: String,
}
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CloudFailure {
    Known,
    UnknownCommit,
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
}

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
        self.client
            .post(url)
            .header("apikey", &self.config.publishable_key)
            .bearer_auth(self.access_token.as_str())
            .json(&body)
            .send()
            .map_err(|_| CloudFailure::Known)?
            .error_for_status()
            .map_err(|_| CloudFailure::Known)?
            .json()
            .map_err(|_| CloudFailure::Known)
    }
}
impl CloudGateway for SupabaseGateway {
    fn list(&self) -> Result<Vec<RemoteEnvelope>, CloudFailure> {
        let response = self.rpc("nanfeng_sync_list_documents", json!({"p_app_id":APP_ID}))?;
        let rows = response.as_array().ok_or(CloudFailure::Known)?;
        rows.iter()
            .map(|value| {
                let envelope_value = value.get("envelope").cloned().ok_or(CloudFailure::Known)?;
                let envelope =
                    serde_json::to_string(&envelope_value).map_err(|_| CloudFailure::Known)?;
                let root: Value =
                    serde_json::from_str(&envelope).map_err(|_| CloudFailure::Known)?;
                Ok(RemoteEnvelope {
                    revision: root
                        .get("revision")
                        .and_then(Value::as_u64)
                        .ok_or(CloudFailure::Known)?,
                    payload_hash: root
                        .get("payloadHash")
                        .and_then(Value::as_str)
                        .ok_or(CloudFailure::Known)?
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
        let value = response
            .as_array()
            .and_then(|items| items.first())
            .unwrap_or(&response);
        if value.get("missing").and_then(Value::as_bool) == Some(true) || value.is_null() {
            return Ok(None);
        }
        let envelope = value.get("envelope").cloned().ok_or(CloudFailure::Known)?;
        let envelope = serde_json::to_string(&envelope).map_err(|_| CloudFailure::Known)?;
        let root: Value = serde_json::from_str(&envelope).map_err(|_| CloudFailure::Known)?;
        Ok(Some(RemoteEnvelope {
            revision: root
                .get("revision")
                .and_then(Value::as_u64)
                .ok_or(CloudFailure::Known)?,
            payload_hash: root
                .get("payloadHash")
                .and_then(Value::as_str)
                .ok_or(CloudFailure::Known)?
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
        let value: Value = response
            .error_for_status()
            .map_err(|_| CloudFailure::Known)?
            .json()
            .map_err(|_| CloudFailure::Known)?;
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
        .filter(|value| value.starts_with("conversation-") && value.len() <= 64)
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
    pub revision: u64,
    pub payload_hash_prefix: String,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RestoreReceipt {
    pub status: String,
    pub workspace_id: String,
    pub conversation_id: String,
    pub remote_revision: u64,
}

pub fn list_remote_documents<G: CloudGateway>(
    gateway: &G,
) -> Result<Vec<RemoteDocumentProjection>, String> {
    let mut documents = gateway
        .list()
        .map_err(|_| safe_error("云端文档列表无法读取"))?
        .into_iter()
        .map(|remote| {
            let document_id = envelope_document_id(&remote.envelope)?;
            Ok(RemoteDocumentProjection {
                document_id,
                revision: remote.revision,
                payload_hash_prefix: remote.payload_hash.chars().take(12).collect(),
            })
        })
        .collect::<Result<Vec<_>, String>>()?;
    documents.sort_by(|left, right| left.document_id.cmp(&right.document_id));
    Ok(documents)
}

pub fn restore_remote_conversation<C: CredentialStore, G: CloudGateway>(
    connection: &mut Connection,
    credentials: &C,
    gateway: &G,
    document_id: &str,
    recovery_code: &str,
) -> Result<RestoreReceipt, String> {
    if !document_id.starts_with("conversation-") || document_id.len() > 64 {
        return Err(safe_error("云端文档标识无效"));
    }
    let session = read_session(credentials)?.ok_or_else(|| safe_error("请先登录 Google 账号"))?;
    let account = sync_state_v1::account_ref(&session.user_id)?;
    let remote = gateway
        .read(document_id)
        .map_err(|_| safe_error("云端恢复文档无法读取"))?
        .ok_or_else(|| safe_error("云端恢复文档不存在"))?;
    let recovered = sync_v1::recover_account_material(
        &remote.envelope,
        recovery_code,
        APP_ID,
        document_id,
        remote.revision,
    )
    .map_err(|_| safe_error("恢复码不匹配或云端密文无效"))?;
    let records = recovered
        .payload
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
    conversation.insert("pinned".into(), Value::Bool(false));
    conversation.insert("archived".into(), Value::Bool(false));
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
                remote_revision: remote.revision,
            });
        }
        return Err(safe_error("同一云端文档已有不同的本机恢复版本"));
    }
    let old_data_key = credentials.read(DATA_KEY_SERVICE, &account).ok();
    let old_recovery_key = credentials.read(RECOVERY_SERVICE, &account).ok();
    credentials
        .save(DATA_KEY_SERVICE, &account, recovered.data_key.as_slice())
        .map_err(|_| safe_error("恢复的账号密钥无法安全保存"))?;
    if let Err(error) = credentials.save(
        RECOVERY_SERVICE,
        &account,
        recovered.wrapping_key.as_slice(),
    ) {
        if let Some(old) = old_data_key.as_ref() {
            let _ = credentials.save(DATA_KEY_SERVICE, &account, old);
        }
        return Err(error);
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
        transaction.execute("UPDATE desktop_cloud_account_state SET recovery_confirmed=1,recovery_salt_b64=?2,state='READY',last_success_at_ms=?3,last_error_code=NULL,revision=revision+1,updated_at_ms=?3 WHERE account_ref=?1",params![account,URL_SAFE_NO_PAD.encode(&recovered.salt),now_ms()]).map_err(|_| safe_error("云端恢复账号状态未写入"))?;
        let metadata_revision: i64 = transaction
            .query_row(
                "SELECT revision FROM sync_account_metadata WHERE account_ref=?1",
                [&account],
                |row| row.get(0),
            )
            .map_err(|_| safe_error("账号密钥状态不存在"))?;
        transaction.execute("UPDATE sync_account_metadata SET state='READY',revision=?2,wrapped_key_sha256=?3,direction_fact='EMPTY_LOCAL_REMOTE_PRESENT',last_error=NULL,updated_at=?4 WHERE account_ref=?1",params![account,metadata_revision+1,sha256(recovered.data_key.as_slice()),now]).map_err(|_| safe_error("云端恢复密钥状态未写入"))?;
        transaction.execute("INSERT INTO sync_intents(intent_id,account_ref,expected_revision,resulting_revision,resulting_state,created_at) VALUES(?1,?2,?3,?4,'READY',?5)",params![format!("desktop-cloud-restore-{}",now_ms()),account,metadata_revision,metadata_revision+1,now]).map_err(|_| safe_error("云端恢复意图回执未写入"))?;
        Ok(())
    })();
    if let Err(error) = result.and_then(|_| {
        transaction
            .commit()
            .map_err(|_| safe_error("云端恢复未原子提交"))
    }) {
        if let Some(old) = old_data_key.as_ref() {
            let _ = credentials.save(DATA_KEY_SERVICE, &account, old);
        } else {
            let _ = credentials.delete(DATA_KEY_SERVICE, &account);
        }
        if let Some(old) = old_recovery_key.as_ref() {
            let _ = credentials.save(RECOVERY_SERVICE, &account, old);
        } else {
            let _ = credentials.delete(RECOVERY_SERVICE, &account);
        }
        return Err(error);
    }
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
        remote_revision: remote.revision,
    })
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
        messages.push(json!({
            "id":id,
            "parentId":object.get("parentMessageId").cloned().unwrap_or(Value::Null),
            "ordinal":object.get("siblingPosition").and_then(Value::as_u64).unwrap_or(0),
            "role":role,
            "createdAt":timestamp(object.get("createdAtEpochMs"))?,
            "revision":object.get("revision").and_then(Value::as_u64).unwrap_or(1),
            "delivery":delivery,
            "blocks":blocks,
        }));
    }
    let created = timestamp(conversation.get("createdAtEpochMs"))?;
    let updated = timestamp(conversation.get("updatedAtEpochMs"))?;
    let leaf = conversation
        .get("currentLeafMessageId")
        .cloned()
        .unwrap_or_else(|| messages.last().and_then(|message| message.get("id")).cloned().unwrap_or(Value::Null));
    conversation.remove("nodes");
    conversation.remove("currentLeafMessageId");
    conversation.remove("createdAtEpochMs");
    conversation.remove("updatedAtEpochMs");
    conversation.remove("surface");
    conversation.insert("createdAt".into(), Value::String(created));
    conversation.insert("updatedAt".into(), Value::String(updated));
    conversation.insert("currentLeafId".into(), leaf);
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
    let mut safe_messages = Vec::with_capacity(messages.len());
    for message in messages {
        if message
            .get("delivery")
            .and_then(Value::as_str)
            .is_some_and(|value| value != "COMPLETE")
        {
            return Err(safe_error("未完成的回复不会同步"));
        }
        let blocks = message
            .get("blocks")
            .and_then(Value::as_array)
            .ok_or_else(|| safe_error("对话消息无效"))?;
        if blocks
            .iter()
            .any(|block| block.get("kind").and_then(Value::as_str) != Some("TEXT"))
        {
            return Err(safe_error("该对话含附件或工具结果，当前不会部分上传"));
        }
        safe_messages.push(json!({
            "id":message.get("id"), "parentId":message.get("parentId"), "ordinal":message.get("ordinal").and_then(Value::as_u64).unwrap_or(0),
            "role":message.get("role"), "createdAt":message.get("createdAt"), "revision":message.get("revision").and_then(Value::as_u64).unwrap_or(1),
            "delivery":"COMPLETE",
            "blocks":blocks.iter().map(|block| json!({"kind":"TEXT","text":block.get("text").and_then(Value::as_str).unwrap_or_default()})).collect::<Vec<_>>()
        }));
    }
    let content = json!({"title":conversation.get("title").and_then(Value::as_str).unwrap_or("未命名会话"),"createdAt":conversation.get("createdAt"),"updatedAt":conversation.get("updatedAt"),"currentLeafId":conversation.get("currentLeafId"),"messages":safe_messages});
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
        {"kind":"safe_settings","id":"profile-interests","revision":1,"classification":"NORMAL","content":{"interests":interests}},
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

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SyncReceipt {
    pub status: String,
    pub safe_code: Option<String>,
    pub synced_at_ms: Option<i64>,
}

pub fn sync_selected_conversation<C: CredentialStore, G: CloudGateway>(
    connection: &mut Connection,
    credentials: &C,
    gateway: &G,
    workspace_id: &str,
    conversation_id: &str,
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
    let material = wrapping_material(connection, credentials, &account)?;
    let document_id = format!("conversation-{}", &sha256(conversation_id.as_bytes())[..40]);
    let ledger: Option<(i64,String)> = connection.query_row("SELECT remote_revision,payload_hash FROM desktop_selected_conversation_sync WHERE account_ref=?1 AND workspace_id=?2 AND conversation_id=?3", params![account,workspace_id,conversation_id], |row| Ok((row.get(0)?,row.get(1)?))).optional().map_err(|_| safe_error("同步回执无法读取"))?;
    let remote = gateway
        .read(&document_id)
        .map_err(|_| safe_error("无法读取云端状态"))?;
    if let Some(remote) = remote.as_ref() {
        if ledger
            .as_ref()
            .is_none_or(|known| known.0 as u64 != remote.revision || known.1 != remote.payload_hash)
        {
            connection.execute("UPDATE desktop_cloud_account_state SET state='CONFLICT',last_error_code='REMOTE_CHANGED',updated_at_ms=?2 WHERE account_ref=?1", params![account,now_ms()]).ok();
            write_diagnostic(
                connection,
                Some(&account),
                "SYNC_REMOTE_CHECK",
                "CONFLICT",
                Some("REMOTE_CHANGED"),
            );
            write_notification(
                connection,
                Some(&account),
                "CONFLICT",
                "云端版本已变化，需要选择处理方向。",
            );
            return Ok(SyncReceipt {
                status: "CONFLICT".into(),
                safe_code: Some("REMOTE_CHANGED".into()),
                synced_at_ms: None,
            });
        }
    }
    let expected = remote.as_ref().map(|value| value.revision).unwrap_or(0);
    let (payload, local_hash) = conversation_payload(
        connection,
        workspace_id,
        conversation_id,
        &document_id,
        expected + 1,
    )?;
    if ledger.is_some() && connection.query_row("SELECT local_content_hash FROM desktop_selected_conversation_sync WHERE account_ref=?1 AND workspace_id=?2 AND conversation_id=?3", params![account,workspace_id,conversation_id], |row| row.get::<_,String>(0)).ok().as_deref() == Some(&local_hash) {
        return Ok(SyncReceipt { status:"UP_TO_DATE".into(), safe_code:None, synced_at_ms:connection.query_row("SELECT last_synced_at_ms FROM desktop_selected_conversation_sync WHERE account_ref=?1 AND workspace_id=?2 AND conversation_id=?3", params![account,workspace_id,conversation_id], |row| row.get(0)).ok() });
    }
    connection.execute("UPDATE desktop_cloud_account_state SET state='SYNCING',last_error_code=NULL,updated_at_ms=?2 WHERE account_ref=?1", params![account,now_ms()]).map_err(|_| safe_error("同步状态未保存"))?;
    let mut data_key = credentials
        .read(DATA_KEY_SERVICE, &account)
        .map_err(|_| safe_error("账号加密密钥不可用"))?;
    let envelope = sync_v1::seal_with_account_wrapping_material(payload, &data_key, &material)
        .map_err(|_| safe_error("对话无法安全加密"))?;
    data_key.zeroize();
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
            connection.execute("UPDATE desktop_sync_jobs SET stage='UNKNOWN',safe_error_code='COMMIT_RESULT_UNKNOWN',updated_at_ms=?2 WHERE job_id=?1", params![job_id,now_ms()]).ok();
            connection.execute("UPDATE desktop_cloud_account_state SET state='FAILED',last_error_code='COMMIT_RESULT_UNKNOWN',updated_at_ms=?2 WHERE account_ref=?1", params![account,now_ms()]).ok();
            write_diagnostic(
                connection,
                Some(&account),
                "SYNC_COMMIT",
                "UNKNOWN",
                Some("COMMIT_RESULT_UNKNOWN"),
            );
            return Ok(SyncReceipt {
                status: "UNKNOWN".into(),
                safe_code: Some("COMMIT_RESULT_UNKNOWN".into()),
                synced_at_ms: None,
            });
        }
        Err(CloudFailure::Known) => {
            connection.execute("UPDATE desktop_sync_jobs SET stage='RETRY_WAIT',next_retry_at_ms=?2,safe_error_code='NETWORK_OR_REMOTE_REJECTED',updated_at_ms=?3 WHERE job_id=?1",params![job_id,now_ms()+12*60*60*1000,now_ms()]).ok();
            connection.execute("UPDATE desktop_cloud_account_state SET state='FAILED',last_error_code='NETWORK_OR_REMOTE_REJECTED',updated_at_ms=?2 WHERE account_ref=?1",params![account,now_ms()]).ok();
            write_diagnostic(
                connection,
                Some(&account),
                "SYNC_COMMIT",
                "RETRY_WAIT",
                Some("NETWORK_OR_REMOTE_REJECTED"),
            );
            return Err(safe_error("云端拒绝同步，已记录待重试"));
        }
    };
    let readback = gateway
        .read(&document_id)
        .map_err(|_| safe_error("云端回读校验失败"))?
        .ok_or_else(|| safe_error("云端回读不存在"))?;
    if committed.0 != expected + 1
        || committed.1 != payload_hash
        || readback.revision != committed.0
        || readback.payload_hash != payload_hash
    {
        return Err(safe_error("云端回读与本机密文不一致"));
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
        "所选对话已完成加密同步。",
    );
    Ok(SyncReceipt {
        status: "SYNCED".into(),
        safe_code: None,
        synced_at_ms: Some(timestamp),
    })
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
            connection.execute("UPDATE desktop_cloud_account_state SET state='CONFLICT',last_error_code='REMOTE_CHANGED',updated_at_ms=?2 WHERE account_ref=?1", params![account,now_ms()]).ok();
            write_diagnostic(
                connection,
                Some(&account),
                "SYNC_RECONCILE",
                "CONFLICT",
                Some("REMOTE_CHANGED"),
            );
            write_notification(
                connection,
                Some(&account),
                "CONFLICT",
                "云端结果与本机待提交版本不一致，已停止重试。",
            );
            Ok(SyncReceipt {
                status: "CONFLICT".into(),
                safe_code: Some("REMOTE_CHANGED".into()),
                synced_at_ms: None,
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
    let ready: i64 = connection
        .query_row(
            "SELECT recovery_confirmed FROM desktop_cloud_account_state WHERE account_ref=?1",
            [&account],
            |row| row.get(0),
        )
        .map_err(|_| safe_error("账号状态不存在"))?;
    if ready != 1 {
        return Err(safe_error("请先确认恢复码"));
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
    let recovery_confirmed: i64 = connection
        .query_row(
            "SELECT recovery_confirmed FROM desktop_cloud_account_state WHERE account_ref=?1",
            [&account],
            |row| row.get(0),
        )
        .map_err(|_| safe_error("账号状态不存在"))?;
    if recovery_confirmed != 1 {
        return Err(safe_error("请先确认恢复码"));
    }
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
    let row = account.as_ref().and_then(|account| connection.query_row("SELECT state,periodic_enabled,recovery_confirmed,last_success_at_ms,last_error_code,device_id FROM desktop_cloud_account_state WHERE account_ref=?1", [account], |row| Ok((row.get::<_,String>(0)?,row.get::<_,i64>(1)? != 0,row.get::<_,i64>(2)? != 0,row.get::<_,Option<i64>>(3)?,row.get::<_,Option<String>>(4)?,row.get::<_,String>(5)?))).optional().ok().flatten());
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
    let rotation_pending = account
        .as_ref()
        .and_then(|value| {
            connection
                .query_row(
                    "SELECT COUNT(*) FROM desktop_recovery_rotation_journal WHERE account_ref=?1",
                    [value],
                    |row| row.get::<_, u64>(0),
                )
                .ok()
        })
        .unwrap_or(0)
        > 0;
    let diagnostics = connection.prepare("SELECT event,outcome,safe_code,occurred_at_ms FROM desktop_sync_diagnostics ORDER BY occurred_at_ms DESC LIMIT 12").map_err(|_| safe_error("同步诊断无法读取"))?.query_map([], |row| Ok(DiagnosticProjection { event:row.get(0)?, outcome:row.get(1)?, safe_code:row.get(2)?, occurred_at_ms:row.get(3)? })).map_err(|_| safe_error("同步诊断无法读取"))?.collect::<Result<Vec<_>,_>>().map_err(|_| safe_error("同步诊断无效"))?;
    let notifications = connection.prepare("SELECT kind,message,occurred_at_ms,unread FROM desktop_sync_notifications ORDER BY occurred_at_ms DESC LIMIT 8").map_err(|_| safe_error("同步通知无法读取"))?.query_map([], |row| Ok(NotificationProjection { kind:row.get(0)?, message:row.get(1)?, occurred_at_ms:row.get(2)?, unread:row.get::<_,i64>(3)? != 0 })).map_err(|_| safe_error("同步通知无法读取"))?.collect::<Result<Vec<_>,_>>().map_err(|_| safe_error("同步通知无效"))?;
    let state = row
        .as_ref()
        .map(|value| value.0.clone())
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
        recovery_state: if row.as_ref().is_some_and(|value| value.2) {
            "CONFIRMED".into()
        } else if session.is_some() {
            "NEEDS_CONFIRMATION".into()
        } else {
            "UNAVAILABLE".into()
        },
        periodic_enabled: row.as_ref().is_some_and(|value| value.1),
        rotation_pending,
        last_success_at_ms: row.as_ref().and_then(|value| value.3),
        selected_conversation_count: selected,
        sync_stage: state,
        notice: row.as_ref().and_then(|value| value.4.clone()),
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
        migrate_recovery_rotation(&tx).unwrap();
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
        c.execute("INSERT INTO desktop_cloud_account_state VALUES(?1,?2,?3,NULL,NULL,'AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION',0,0,NULL,0,NULL,NULL,'device-safe',1,1)",params![account,session.user_id,session.email]).unwrap();
        let mut store = SqliteMetadataStore { connection: &mut c };
        sync_state_v1::authenticate(&mut store, &keys, "auth", None, "verified-user-123").unwrap();
        let (shown, pending) = create_recovery_code(store.connection, &keys).unwrap();
        assert!(shown.recovery_code.starts_with("NF-"));
        confirm_recovery(store.connection, &keys, pending, &shown.confirmation_hash).unwrap();
        let dump = format!("{:?}", keys.0.borrow());
        assert!(!dump.contains(&shown.recovery_code));
        assert!(!dump.contains("access-secret"));
    }
    #[test]
    fn selected_conversation_sync_readback_and_conflict_are_receipted() {
        struct Cloud(RefCell<Option<RemoteEnvelope>>);
        impl CloudGateway for Cloud {
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
        c.execute("INSERT INTO desktop_cloud_account_state VALUES(?1,?2,?3,NULL,NULL,'DIRECTION_REQUIRED',0,0,NULL,0,NULL,NULL,'device-safe',1,1)",params![account,session.user_id,session.email]).unwrap();
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
        store.connection.execute("INSERT INTO workspace_exchange VALUES('workspace-safe',?1)",[json!({"conversations":[{"id":"conversation-safe","title":"安全对话","revision":1,"currentLeafId":"message-safe","messages":[{"id":"message-safe","parentId":null,"ordinal":0,"role":"user","delivery":"COMPLETE","createdAt":"2026-09-01T00:00:00Z","revision":1,"blocks":[{"kind":"TEXT","text":"安全正文"}]}]}]}).to_string()]).unwrap();
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
        assert_eq!(
            projection(store.connection, &keys, true, None)
                .unwrap()
                .selected_conversation_count,
            1
        );
        cloud.0.borrow_mut().as_mut().unwrap().payload_hash = "f".repeat(64);
        let conflict = sync_selected_conversation(
            store.connection,
            &keys,
            &cloud,
            "workspace-safe",
            "conversation-safe",
        )
        .unwrap();
        assert_eq!(conflict.status, "CONFLICT");
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
        c.execute("INSERT INTO desktop_cloud_account_state VALUES(?1,?2,?3,NULL,NULL,'FAILED',0,1,'safe-salt',1,NULL,'COMMIT_RESULT_UNKNOWN','device-safe',1,1)",params![account,session.user_id,session.email]).unwrap();
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
        source.execute("INSERT INTO desktop_cloud_account_state VALUES(?1,?2,?3,NULL,NULL,'AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION',0,0,NULL,0,NULL,NULL,'source-device',1,1)",params![account,session.user_id,session.email]).unwrap();
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
        let (new_code, new_pending) =
            prepare_custom_recovery_rotation(source_store.connection, &source_keys, "Fixture-custom-recovery-2026").unwrap();
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
        target.execute("INSERT INTO desktop_cloud_account_state VALUES(?1,?2,?3,NULL,NULL,'AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION',0,0,NULL,0,NULL,NULL,'target-device',1,1)",params![account,session.user_id,session.email]).unwrap();
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
            &new_code.recovery_code,
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
