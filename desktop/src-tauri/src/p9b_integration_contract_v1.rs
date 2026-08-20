//! P9-B local contract foundation. There is no target registry, Tauri command, IPC, file scan or network path.
use rusqlite::{params, Connection};
use serde_json::Value;
use sha2::{Digest, Sha256};
use std::path::Path;

pub const FORMAT: &str = "nfai.integration-contract";
pub const VERSION: u64 = 1;

#[derive(Clone, Debug, PartialEq)]
pub struct Request {
    pub request_id: String,
    pub idempotency_key: String,
    pub app_handle: String,
    pub subject_handle: String,
    pub revision: u64,
    pub content_hash: String,
    pub limit: u64,
    pub expires_at: Option<i64>,
}
#[derive(Clone, Debug, PartialEq)]
pub struct Preview {
    pub revision: u64,
    pub content_hash: String,
    pub item_count: u64,
    pub next_cursor: Option<String>,
}
#[derive(Clone, Debug, PartialEq)]
pub struct Session {
    pub request: Request,
    pub state: String,
    pub preview: Option<Preview>,
    pub result_hash: Option<String>,
    pub error: Option<String>,
}

pub trait LocalTestOnlyTarget {
    fn app_handle(&self) -> &str;
    fn preview(&self, subject: &str, limit: u64) -> Preview;
    fn readback(&self, subject: &str) -> Preview;
}

pub fn parse_preflight(raw: &str) -> Result<Request, &'static str> {
    let value: Value = serde_json::from_str(raw).map_err(|_| "UNKNOWN_OR_INVALID_SCHEMA")?;
    let root = value.as_object().ok_or("UNKNOWN_OR_INVALID_SCHEMA")?;
    allowed(
        root,
        &[
            "format",
            "version",
            "mode",
            "requestId",
            "idempotencyKey",
            "appHandle",
            "subjectHandle",
            "capability",
            "permission",
            "classification",
            "provenance",
            "page",
            "expiresAtEpochMs",
        ],
    )?;
    if string(root, "format")? != FORMAT
        || number(root, "version")? != VERSION
        || string(root, "mode")? != "LOCAL_TEST_ONLY"
    {
        return Err("UNKNOWN_OR_INVALID_SCHEMA");
    }
    if string(root, "capability")? != "READ_ONLY_PREVIEW"
        || string(root, "permission")? != "READ_ONLY"
    {
        return Err("PERMISSION_OR_CAPABILITY_DENIED");
    }
    if string(root, "classification")? != "NON_SENSITIVE" {
        return Err("HIGH_SENSITIVE_OR_UNKNOWN_DENIED");
    }
    let provenance = root
        .get("provenance")
        .and_then(Value::as_object)
        .ok_or("UNKNOWN_OR_INVALID_SCHEMA")?;
    allowed(provenance, &["source", "revision", "contentHash"])?;
    let page = root
        .get("page")
        .and_then(Value::as_object)
        .ok_or("UNKNOWN_OR_INVALID_SCHEMA")?;
    allowed(page, &["limit", "cursor"])?;
    let request = Request {
        request_id: opaque(string(root, "requestId")?)?,
        idempotency_key: opaque(string(root, "idempotencyKey")?)?,
        app_handle: opaque(string(root, "appHandle")?)?,
        subject_handle: opaque(string(root, "subjectHandle")?)?,
        revision: number(provenance, "revision")?,
        content_hash: string(provenance, "contentHash")?.to_owned(),
        limit: number(page, "limit")?,
        expires_at: root.get("expiresAtEpochMs").and_then(Value::as_i64),
    };
    if string(provenance, "source")? != "LOCAL_TEST_ONLY"
        || request.revision == 0
        || !hash(&request.content_hash)
    {
        return Err("PROVENANCE_INVALID");
    }
    if !(1..=25).contains(&request.limit) {
        return Err("PAGINATION_LIMIT_DENIED");
    }
    if let Some(cursor) = page.get("cursor").and_then(Value::as_str) {
        opaque(cursor)?;
    }
    if root
        .get("expiresAtEpochMs")
        .is_some_and(|v| !v.is_null() && v.as_i64().is_none())
    {
        return Err("EXPIRY_INVALID");
    }
    Ok(request)
}

pub struct Ledger {
    connection: Connection,
}
impl Ledger {
    pub fn open(root: &Path) -> Result<Self, String> {
        std::fs::create_dir_all(root).map_err(|_| "P9B local ledger unavailable".to_owned())?;
        let connection = Connection::open(root.join("p9b-local-test-only.sqlite3"))
            .map_err(|_| "P9B local ledger unavailable".to_owned())?;
        connection.execute_batch("CREATE TABLE IF NOT EXISTS sessions (request_id TEXT PRIMARY KEY, idempotency_key TEXT UNIQUE NOT NULL, app_handle TEXT NOT NULL, subject_handle TEXT NOT NULL, revision INTEGER NOT NULL, content_hash TEXT NOT NULL, page_limit INTEGER NOT NULL, expires_at INTEGER, state TEXT NOT NULL, preview_revision INTEGER, preview_hash TEXT, preview_count INTEGER, result_hash TEXT, error TEXT); CREATE TABLE IF NOT EXISTS events (request_id TEXT NOT NULL, sequence INTEGER NOT NULL, kind TEXT NOT NULL, fingerprint TEXT NOT NULL, PRIMARY KEY(request_id,sequence)); CREATE TABLE IF NOT EXISTS receipts (idempotency_key TEXT PRIMARY KEY, request_id TEXT NOT NULL, result_hash TEXT NOT NULL); PRAGMA user_version=1;").map_err(|_| "P9B migration failed".to_owned())?;
        Ok(Self { connection })
    }
    pub fn request(&self, request: &Request) -> Result<Session, String> {
        if let Some(existing) = self.by_key(&request.idempotency_key)? {
            return Ok(existing);
        }
        self.connection.execute("INSERT INTO sessions (request_id,idempotency_key,app_handle,subject_handle,revision,content_hash,page_limit,expires_at,state) VALUES (?1,?2,?3,?4,?5,?6,?7,?8,'REQUESTED')", params![request.request_id,request.idempotency_key,request.app_handle,request.subject_handle,request.revision,request.content_hash,request.limit,request.expires_at]).map_err(|_| "P9B request write failed".to_owned())?;
        self.event(&request.request_id, "REQUESTED")?;
        self.by_id(&request.request_id)?
            .ok_or_else(|| "P9B request readback failed".to_owned())
    }
    pub fn advance<T: LocalTestOnlyTarget>(
        &self,
        request_id: &str,
        action: &str,
        target: &T,
        now_ms: i64,
    ) -> Result<Session, String> {
        let session = self.by_id(request_id)?.ok_or("REQUEST_NOT_FOUND")?;
        if action == "RESULT"
            && matches!(
                session.state.as_str(),
                "RESULT_READY" | "READBACK_VERIFIED" | "REVOKED"
            )
        {
            return Ok(session);
        }
        let allowed = match action {
            "AUTHORIZE" => session.state == "REQUESTED",
            "PREVIEW" => session.state == "AUTHORIZED",
            "CONFIRM" => session.state == "PREVIEWED",
            "RESULT" => session.state == "CONFIRMED",
            "READBACK" => session.state == "RESULT_READY",
            "REVOKE" => matches!(
                session.state.as_str(),
                "AUTHORIZED" | "PREVIEWED" | "CONFIRMED" | "RESULT_READY" | "READBACK_VERIFIED"
            ),
            "CANCEL" => matches!(
                session.state.as_str(),
                "REQUESTED" | "AUTHORIZED" | "PREVIEWED" | "CONFIRMED" | "RESULT_READY"
            ),
            _ => false,
        };
        if !allowed {
            return Err("INVALID_STATE_TRANSITION".into());
        }
        if matches!(
            action,
            "AUTHORIZE" | "PREVIEW" | "CONFIRM" | "RESULT" | "READBACK"
        ) && session
            .request
            .expires_at
            .is_some_and(|value| now_ms > value)
        {
            return self.expire(&session);
        }
        if matches!(
            action,
            "AUTHORIZE" | "PREVIEW" | "CONFIRM" | "RESULT" | "READBACK"
        ) && session.request.app_handle != target.app_handle()
        {
            return self.reject(&session, "APP_SCOPE_DENIED");
        }
        if action == "PREVIEW" {
            let preview = target.preview(&session.request.subject_handle, session.request.limit);
            if preview.revision != session.request.revision
                || preview.content_hash != session.request.content_hash
                || preview.item_count > session.request.limit
            {
                return self.reject(&session, "SOURCE_PROVENANCE_MISMATCH");
            }
            return self.save(
                &session,
                "PREVIEWED",
                Some(preview),
                None,
                None,
                "PREVIEWED",
            );
        }
        if action == "READBACK" {
            let actual = target.readback(&session.request.subject_handle);
            let expected = session.preview.as_ref().ok_or("INVALID_STATE_TRANSITION")?;
            if actual.revision != expected.revision || actual.content_hash != expected.content_hash
            {
                return self.reject(&session, "TARGET_UPDATED");
            }
        }
        let state = match action {
            "AUTHORIZE" => "AUTHORIZED",
            "CONFIRM" => "CONFIRMED",
            "RESULT" => "RESULT_READY",
            "READBACK" => "READBACK_VERIFIED",
            "REVOKE" => "REVOKED",
            "CANCEL" => "CANCELLED",
            _ => return Err("INVALID_STATE_TRANSITION".into()),
        };
        let result = if action == "RESULT" {
            Some(digest(&format!(
                "{}|{}|{}",
                session.request.request_id, session.request.revision, session.request.content_hash
            )))
        } else {
            None
        };
        self.save(
            &session,
            state,
            session.preview.clone(),
            result,
            None,
            state,
        )
    }
    fn save(
        &self,
        session: &Session,
        state: &str,
        preview: Option<Preview>,
        result: Option<String>,
        error: Option<&str>,
        event: &str,
    ) -> Result<Session, String> {
        let result_ref = result.as_deref().or(session.result_hash.as_deref());
        self.connection.execute("UPDATE sessions SET state=?2,preview_revision=?3,preview_hash=?4,preview_count=?5,result_hash=?6,error=?7 WHERE request_id=?1", params![session.request.request_id,state,preview.as_ref().map(|p|p.revision),preview.as_ref().map(|p|p.content_hash.as_str()),preview.as_ref().map(|p|p.item_count),result_ref,error]).map_err(|_| "P9B state write failed".to_owned())?;
        if let Some(hash) = result {
            self.connection
                .execute(
                    "INSERT OR IGNORE INTO receipts VALUES (?1,?2,?3)",
                    params![
                        session.request.idempotency_key,
                        session.request.request_id,
                        hash
                    ],
                )
                .map_err(|_| "P9B receipt write failed".to_owned())?;
        }
        self.event(&session.request.request_id, event)?;
        self.by_id(&session.request.request_id)?
            .ok_or_else(|| "P9B state readback failed".to_owned())
    }
    fn reject(&self, session: &Session, code: &str) -> Result<Session, String> {
        self.save(
            session,
            "REJECTED",
            session.preview.clone(),
            None,
            Some(code),
            code,
        )
    }
    fn expire(&self, session: &Session) -> Result<Session, String> {
        self.save(
            session,
            "EXPIRED",
            session.preview.clone(),
            None,
            Some("PERMISSION_EXPIRED"),
            "PERMISSION_EXPIRED",
        )
    }
    fn event(&self, request_id: &str, kind: &str) -> Result<(), String> {
        let sequence: u64 = self
            .connection
            .query_row(
                "SELECT COUNT(*) FROM events WHERE request_id=?1",
                [request_id],
                |row| row.get(0),
            )
            .map_err(|_| "P9B event count failed".to_owned())?;
        self.connection
            .execute(
                "INSERT INTO events VALUES (?1,?2,?3,?4)",
                params![
                    request_id,
                    sequence,
                    kind,
                    digest(&format!("{request_id}|{sequence}|{kind}"))
                ],
            )
            .map_err(|_| "P9B event write failed".to_owned())?;
        Ok(())
    }
    pub fn events(&self, request_id: &str) -> Result<Vec<String>, String> {
        let mut statement = self
            .connection
            .prepare("SELECT kind FROM events WHERE request_id=?1 ORDER BY sequence ASC")
            .map_err(|_| "P9B events read failed".to_owned())?;
        let result = statement
            .query_map([request_id], |row| row.get(0))
            .map_err(|_| "P9B events read failed".to_owned())?
            .collect::<Result<Vec<String>, _>>()
            .map_err(|_| "P9B events read failed".to_owned());
        result
    }
    fn by_key(&self, key: &str) -> Result<Option<Session>, String> {
        self.find(
            "SELECT request_id FROM sessions WHERE idempotency_key=?1",
            key,
        )
    }
    fn by_id(&self, id: &str) -> Result<Option<Session>, String> {
        self.find("SELECT request_id FROM sessions WHERE request_id=?1", id)
    }
    fn find(&self, query: &str, value: &str) -> Result<Option<Session>, String> {
        let id = self
            .connection
            .query_row(query, [value], |row| row.get::<_, String>(0))
            .ok();
        let Some(id) = id else { return Ok(None) };
        self.connection.query_row("SELECT request_id,idempotency_key,app_handle,subject_handle,revision,content_hash,page_limit,expires_at,state,preview_revision,preview_hash,preview_count,result_hash,error FROM sessions WHERE request_id=?1", [&id], |r| { let preview = match (r.get::<_,Option<u64>>(9)?,r.get::<_,Option<String>>(10)?,r.get::<_,Option<u64>>(11)?) { (Some(revision),Some(content_hash),Some(item_count))=>Some(Preview{revision,content_hash,item_count,next_cursor:None}), _=>None }; Ok(Session{request:Request{request_id:r.get(0)?,idempotency_key:r.get(1)?,app_handle:r.get(2)?,subject_handle:r.get(3)?,revision:r.get(4)?,content_hash:r.get(5)?,limit:r.get(6)?,expires_at:r.get(7)?},state:r.get(8)?,preview,result_hash:r.get(12)?,error:r.get(13)?}) }).map(Some).map_err(|_| "P9B session read failed".to_owned())
    }
}

fn allowed(value: &serde_json::Map<String, Value>, keys: &[&str]) -> Result<(), &'static str> {
    if value.keys().any(|key| {
        !keys.contains(&key.as_str())
            || matches!(
                key.as_str(),
                "uri"
                    | "path"
                    | "token"
                    | "database"
                    | "db"
                    | "credential"
                    | "authorization"
                    | "body"
                    | "content"
            )
    }) {
        Err("UNKNOWN_OR_INVALID_SCHEMA")
    } else {
        Ok(())
    }
}
fn string<'a>(
    value: &'a serde_json::Map<String, Value>,
    key: &str,
) -> Result<&'a str, &'static str> {
    value
        .get(key)
        .and_then(Value::as_str)
        .ok_or("UNKNOWN_OR_INVALID_SCHEMA")
}
fn number(value: &serde_json::Map<String, Value>, key: &str) -> Result<u64, &'static str> {
    value
        .get(key)
        .and_then(Value::as_u64)
        .ok_or("UNKNOWN_OR_INVALID_SCHEMA")
}
fn opaque(value: &str) -> Result<String, &'static str> {
    if value.len() >= 3
        && value.len() <= 64
        && value.as_bytes().first().is_some_and(u8::is_ascii_lowercase)
        && value
            .bytes()
            .all(|b| b.is_ascii_lowercase() || b.is_ascii_digit() || b == b'_' || b == b'-')
    {
        Ok(value.into())
    } else {
        Err("UNKNOWN_OR_INVALID_SCHEMA")
    }
}
fn hash(value: &str) -> bool {
    value.len() == 64
        && value
            .bytes()
            .all(|b| b.is_ascii_hexdigit() && !b.is_ascii_uppercase())
}
fn digest(value: &str) -> String {
    format!("{:x}", Sha256::digest(value.as_bytes()))
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;
    struct Target {
        app_handle: String,
        current: Preview,
    }
    impl LocalTestOnlyTarget for Target {
        fn app_handle(&self) -> &str {
            &self.app_handle
        }
        fn preview(&self, _: &str, _: u64) -> Preview {
            self.current.clone()
        }
        fn readback(&self, _: &str) -> Preview {
            self.current.clone()
        }
    }
    fn raw(extra: &str) -> String {
        format!(
            r#"{{"format":"nfai.integration-contract","version":1,"mode":"LOCAL_TEST_ONLY","requestId":"request_one","idempotencyKey":"idem_one","appHandle":"app_fixture","subjectHandle":"subject_one","capability":"READ_ONLY_PREVIEW","permission":"READ_ONLY","classification":"NON_SENSITIVE","provenance":{{"source":"LOCAL_TEST_ONLY","revision":1,"contentHash":"{}"}},"page":{{"limit":1,"cursor":null}},"expiresAtEpochMs":null{extra}}}"#,
            "a".repeat(64)
        )
    }
    #[test]
    fn local_harness_is_read_only_replayable_readbacked_and_revocable() {
        let dir = tempdir().unwrap();
        let ledger = Ledger::open(dir.path()).unwrap();
        let target = Target {
            app_handle: "app_fixture".into(),
            current: Preview {
                revision: 1,
                content_hash: "a".repeat(64),
                item_count: 1,
                next_cursor: None,
            },
        };
        let request = parse_preflight(&raw("")).unwrap();
        assert_eq!(ledger.request(&request).unwrap().state, "REQUESTED");
        assert_eq!(ledger.request(&request).unwrap().state, "REQUESTED");
        for action in [
            "AUTHORIZE",
            "PREVIEW",
            "CONFIRM",
            "RESULT",
            "READBACK",
            "REVOKE",
        ] {
            ledger.advance("request_one", action, &target, 0).unwrap();
        }
        assert_eq!(
            ledger.by_id("request_one").unwrap().unwrap().state,
            "REVOKED"
        );
        assert_eq!(
            ledger.events("request_one").unwrap(),
            vec![
                "REQUESTED",
                "AUTHORIZED",
                "PREVIEWED",
                "CONFIRMED",
                "RESULT_READY",
                "READBACK_VERIFIED",
                "REVOKED"
            ]
        );
    }
    #[test]
    fn strict_parser_and_target_update_fail_closed() {
        assert_eq!(
            parse_preflight(&raw(",\"uri\":\"forbidden\"")),
            Err("UNKNOWN_OR_INVALID_SCHEMA")
        );
        let dir = tempdir().unwrap();
        let ledger = Ledger::open(dir.path()).unwrap();
        let mut target = Target {
            app_handle: "app_fixture".into(),
            current: Preview {
                revision: 1,
                content_hash: "a".repeat(64),
                item_count: 1,
                next_cursor: None,
            },
        };
        let request = parse_preflight(&raw("")).unwrap();
        ledger.request(&request).unwrap();
        for action in ["AUTHORIZE", "PREVIEW", "CONFIRM", "RESULT"] {
            ledger.advance("request_one", action, &target, 0).unwrap();
        }
        target.current.revision = 2;
        target.current.content_hash = "b".repeat(64);
        let rejected = ledger
            .advance("request_one", "READBACK", &target, 0)
            .unwrap();
        assert_eq!(rejected.state, "REJECTED");
        assert_eq!(rejected.error.as_deref(), Some("TARGET_UPDATED"));
    }
    #[test]
    fn expiry_and_target_reselection_prevent_continuation_before_synthetic_receipt() {
        let dir = tempdir().unwrap();
        let ledger = Ledger::open(dir.path()).unwrap();
        let mut target = Target {
            app_handle: "app_fixture".into(),
            current: Preview {
                revision: 1,
                content_hash: "a".repeat(64),
                item_count: 1,
                next_cursor: None,
            },
        };
        let request = parse_preflight(&raw("")).unwrap();
        ledger.request(&request).unwrap();
        ledger
            .advance("request_one", "AUTHORIZE", &target, 0)
            .unwrap();
        target.app_handle = "app_reselected".into();
        let reselected = ledger
            .advance("request_one", "PREVIEW", &target, 0)
            .unwrap();
        assert_eq!(reselected.state, "REJECTED");
        assert_eq!(reselected.error.as_deref(), Some("APP_SCOPE_DENIED"));
        assert_eq!(reselected.result_hash, None);

        let dir = tempdir().unwrap();
        let ledger = Ledger::open(dir.path()).unwrap();
        let target = Target {
            app_handle: "app_fixture".into(),
            current: Preview {
                revision: 1,
                content_hash: "a".repeat(64),
                item_count: 1,
                next_cursor: None,
            },
        };
        let request = Request {
            expires_at: Some(0),
            ..parse_preflight(&raw("")).unwrap()
        };
        ledger.request(&request).unwrap();
        ledger
            .advance("request_one", "AUTHORIZE", &target, 0)
            .unwrap();
        ledger
            .advance("request_one", "PREVIEW", &target, 0)
            .unwrap();
        ledger
            .advance("request_one", "CONFIRM", &target, 0)
            .unwrap();
        let expired = ledger.advance("request_one", "RESULT", &target, 1).unwrap();
        assert_eq!(expired.state, "EXPIRED");
        assert_eq!(expired.error.as_deref(), Some("PERMISSION_EXPIRED"));
        assert_eq!(expired.result_hash, None);
    }
}
