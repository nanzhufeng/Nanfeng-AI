//! P7-E's Desktop-only isolated workspace staging.  It stores P7-E semantic records, never a
//! P5-D database backup or a P6 exchange package, and deliberately has no Tauri command.

use crate::sync_v1;
use rusqlite::{params, Connection};
use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use std::{
    fs,
    path::{Path, PathBuf},
};

const RECORD_FORMAT: &str = "nfai.sync.semantic-record.v1";
const KINDS: [&str; 6] = [
    "project",
    "conversation",
    "knowledge",
    "memory",
    "relation",
    "safe_settings",
];

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct SemanticRecord {
    pub kind: String,
    pub id: String,
    pub revision: u64,
    pub classification: String,
    pub content_json: String,
}

#[derive(Clone, Debug)]
pub struct SemanticSnapshot {
    pub app_id: String,
    pub document_id: String,
    pub revision: u64,
    pub records: Vec<SemanticRecord>,
}

pub struct IsolatedWorkspaceStore {
    root: PathBuf,
}

impl IsolatedWorkspaceStore {
    pub fn open(root: PathBuf) -> Result<Self, String> {
        fs::create_dir_all(root.join("staging"))
            .map_err(|_| "P7E staging root unavailable".to_owned())?;
        fs::create_dir_all(root.join("workspaces"))
            .map_err(|_| "P7E workspace root unavailable".to_owned())?;
        Ok(Self { root })
    }

    /// Writes a candidate SQLite database under a private staging root. It does not read or alter
    /// DesktopWorkspaceStore's `workspace.sqlite3` and cannot expose a partial workspace.
    pub fn stage(&self, snapshot: &SemanticSnapshot) -> Result<String, String> {
        validate_snapshot(snapshot)?;
        let canonical = canonical_snapshot(snapshot)?;
        let id = sha256(canonical.as_bytes());
        let stage = self.root.join("staging").join(&id);
        if stage.exists() {
            return Ok(id);
        }
        let temporary = self.root.join("staging").join(format!(".{id}.tmp"));
        fs::create_dir(&temporary).map_err(|_| "P7E staging creation failed".to_owned())?;
        let result = (|| -> Result<(), String> {
            let database = temporary.join("workspace.sqlite3");
            let mut db = Connection::open(&database)
                .map_err(|_| "P7E staging SQLite unavailable".to_owned())?;
            let tx = db
                .transaction()
                .map_err(|_| "P7E staging transaction unavailable".to_owned())?;
            tx.execute_batch("CREATE TABLE sync_workspace (id INTEGER PRIMARY KEY CHECK(id=1), app_id TEXT NOT NULL, document_id TEXT NOT NULL, revision INTEGER NOT NULL, snapshot_hash TEXT NOT NULL); CREATE TABLE sync_semantic_records (kind TEXT NOT NULL, id TEXT NOT NULL, revision INTEGER NOT NULL, classification TEXT NOT NULL, content_json TEXT NOT NULL, PRIMARY KEY(kind,id,revision));")
                .map_err(|_| "P7E staging schema failed".to_owned())?;
            tx.execute("INSERT INTO sync_workspace(id,app_id,document_id,revision,snapshot_hash) VALUES(1,?1,?2,?3,?4)", params![snapshot.app_id, snapshot.document_id, snapshot.revision, id])
                .map_err(|_| "P7E staging header failed".to_owned())?;
            for record in sorted_records(&snapshot.records) {
                tx.execute("INSERT INTO sync_semantic_records(kind,id,revision,classification,content_json) VALUES(?1,?2,?3,?4,?5)", params![record.kind, record.id, record.revision, record.classification, record.content_json])
                    .map_err(|_| "P7E staging record failed".to_owned())?;
            }
            tx.commit()
                .map_err(|_| "P7E staging transaction rollback".to_owned())?;
            verify_database(&database, snapshot, &id)
        })();
        if result.is_err() {
            let _ = fs::remove_dir_all(&temporary);
        }
        result?;
        fs::rename(&temporary, &stage)
            .map_err(|_| "P7E staging atomic publish failed".to_owned())?;
        Ok(id)
    }

    /// Atomically exposes a staged candidate only under this explicit isolated workspace name.
    /// Existing workspaces require an explicit replacement flag and are restored on a failed swap.
    pub fn switch_atomically(
        &self,
        staging_id: &str,
        workspace_id: &str,
        replace_confirmed: bool,
    ) -> Result<String, String> {
        valid_segment(staging_id)?;
        valid_segment(workspace_id)?;
        let staged = self.root.join("staging").join(staging_id);
        let database = staged.join("workspace.sqlite3");
        if !database.is_file() {
            return Err("P7E staged workspace missing".into());
        }
        let target = self.root.join("workspaces").join(workspace_id);
        if target.exists() && !replace_confirmed {
            return Err("P7E replacement confirmation required".into());
        }
        let checkpoint = self
            .root
            .join("workspaces")
            .join(format!(".{workspace_id}.rollback"));
        if checkpoint.exists() {
            return Err("P7E rollback checkpoint already exists".into());
        }
        if target.exists() {
            fs::rename(&target, &checkpoint)
                .map_err(|_| "P7E checkpoint switch failed".to_owned())?;
        }
        match fs::rename(&staged, &target) {
            Ok(()) => {
                let _ = fs::remove_dir_all(checkpoint);
                Ok(read_hash(&target.join("workspace.sqlite3"))?)
            }
            Err(_) => {
                if checkpoint.exists() {
                    let _ = fs::rename(&checkpoint, &target);
                }
                Err("P7E atomic switch rolled back".into())
            }
        }
    }

    pub fn rollback_staging(&self, staging_id: &str) -> Result<(), String> {
        valid_segment(staging_id)?;
        let staged = self.root.join("staging").join(staging_id);
        if staged.exists() {
            fs::remove_dir_all(staged).map_err(|_| "P7E staging rollback failed".to_owned())?;
        }
        Ok(())
    }

    /// The P7-E owner opens a real P7-A envelope before it ever creates a workspace.  This is
    /// intentionally not a Tauri command: account state, recovery-code entry and remote RPC
    /// remain owned by P7-B/P7-C/P7-D.  A successful call can expose data only as a *new*
    /// isolated workspace; it cannot replace the current P6 workspace.
    pub fn open_to_new_workspace(
        &self,
        envelope: &str,
        recovery: &str,
        expected_app: &str,
        expected_document: &str,
        minimum_revision: u64,
        workspace_id: &str,
    ) -> Result<String, String> {
        let opened = sync_v1::open(
            envelope,
            recovery,
            expected_app,
            expected_document,
            minimum_revision,
        )?;
        let snapshot = snapshot_from_payload(&opened.payload)?;
        let staging_id = self.stage(&snapshot)?;
        match self.switch_atomically(&staging_id, workspace_id, false) {
            Ok(readback_hash) if readback_hash == staging_id => Ok(readback_hash),
            Ok(_) => {
                let _ = self.rollback_staging(&staging_id);
                Err("P7E isolated workspace readback mismatch".into())
            }
            Err(error) => {
                let _ = self.rollback_staging(&staging_id);
                Err(error)
            }
        }
    }

    /// Reads only the verified semantic records owned by an isolated P7-E workspace.  It does
    /// not read `DesktopWorkspaceStore` or any P6 exchange data.
    pub fn read_workspace_snapshot(&self, workspace_id: &str) -> Result<SemanticSnapshot, String> {
        valid_segment(workspace_id)?;
        let database = self
            .root
            .join("workspaces")
            .join(workspace_id)
            .join("workspace.sqlite3");
        let db = Connection::open(&database)
            .map_err(|_| "P7E isolated workspace unavailable".to_owned())?;
        let (app_id, document_id, revision, expected_hash): (String, String, u64, String) = db
            .query_row(
                "SELECT app_id,document_id,revision,snapshot_hash FROM sync_workspace WHERE id=1",
                [],
                |row| Ok((row.get(0)?, row.get(1)?, row.get(2)?, row.get(3)?)),
            )
            .map_err(|_| "P7E isolated workspace header unreadable".to_owned())?;
        let mut statement = db.prepare("SELECT kind,id,revision,classification,content_json FROM sync_semantic_records ORDER BY kind,id,revision")
            .map_err(|_| "P7E isolated workspace records unreadable".to_owned())?;
        let records = statement
            .query_map([], |row| {
                Ok(SemanticRecord {
                    kind: row.get(0)?,
                    id: row.get(1)?,
                    revision: row.get(2)?,
                    classification: row.get(3)?,
                    content_json: row.get(4)?,
                })
            })
            .map_err(|_| "P7E isolated workspace record query failed".to_owned())?
            .collect::<Result<Vec<_>, _>>()
            .map_err(|_| "P7E isolated workspace record decode failed".to_owned())?;
        let snapshot = SemanticSnapshot {
            app_id,
            document_id,
            revision,
            records,
        };
        validate_snapshot(&snapshot)?;
        if sha256(canonical_snapshot(&snapshot)?.as_bytes()) != expected_hash {
            return Err("P7E isolated workspace hash mismatch".into());
        }
        Ok(snapshot)
    }

    /// P7-A sealing counterpart for a verified isolated workspace.  The caller supplies only a
    /// short-lived data key; this store neither creates nor persists keys or recovery material.
    pub fn seal_workspace(
        &self,
        workspace_id: &str,
        recovery: &str,
        data_key: &[u8],
    ) -> Result<String, String> {
        let snapshot = self.read_workspace_snapshot(workspace_id)?;
        sync_v1::seal(snapshot_to_payload(&snapshot)?, recovery, data_key)
    }
}

fn snapshot_from_payload(payload: &Value) -> Result<SemanticSnapshot, String> {
    let root = payload
        .as_object()
        .ok_or_else(|| "P7E opened payload invalid".to_owned())?;
    let records = root
        .get("records")
        .and_then(Value::as_array)
        .ok_or_else(|| "P7E opened records invalid".to_owned())?
        .iter()
        .map(|record| {
            let object = record
                .as_object()
                .ok_or_else(|| "P7E opened record invalid".to_owned())?;
            Ok(SemanticRecord {
                kind: object
                    .get("kind")
                    .and_then(Value::as_str)
                    .ok_or_else(|| "P7E opened record kind invalid".to_owned())?
                    .into(),
                id: object
                    .get("id")
                    .and_then(Value::as_str)
                    .ok_or_else(|| "P7E opened record id invalid".to_owned())?
                    .into(),
                revision: object
                    .get("revision")
                    .and_then(Value::as_u64)
                    .ok_or_else(|| "P7E opened record revision invalid".to_owned())?,
                classification: object
                    .get("classification")
                    .and_then(Value::as_str)
                    .ok_or_else(|| "P7E opened record classification invalid".to_owned())?
                    .into(),
                content_json: canonical(
                    object
                        .get("content")
                        .ok_or_else(|| "P7E opened record content invalid".to_owned())?,
                )?,
            })
        })
        .collect::<Result<Vec<_>, String>>()?;
    let snapshot = SemanticSnapshot {
        app_id: root
            .get("appId")
            .and_then(Value::as_str)
            .ok_or_else(|| "P7E opened app invalid".to_owned())?
            .into(),
        document_id: root
            .get("documentId")
            .and_then(Value::as_str)
            .ok_or_else(|| "P7E opened document invalid".to_owned())?
            .into(),
        revision: root
            .get("revision")
            .and_then(Value::as_u64)
            .ok_or_else(|| "P7E opened revision invalid".to_owned())?,
        records,
    };
    validate_snapshot(&snapshot)?;
    Ok(snapshot)
}

fn snapshot_to_payload(snapshot: &SemanticSnapshot) -> Result<Value, String> {
    validate_snapshot(snapshot)?;
    let records = sorted_records(&snapshot.records)
        .into_iter()
        .map(|record| {
            Ok(json!({
                "kind":record.kind, "id":record.id, "revision":record.revision,
                "classification":record.classification,
                "content":serde_json::from_str::<Value>(&record.content_json)
                    .map_err(|_| "P7E semantic record JSON invalid".to_owned())?,
            }))
        })
        .collect::<Result<Vec<_>, String>>()?;
    Ok(json!({
        "format":"nfai.sync.payload", "protocolVersion":1, "schemaVersion":1,
        "appId":snapshot.app_id, "documentId":snapshot.document_id, "revision":snapshot.revision,
        "records":records,
    }))
}

fn validate_snapshot(snapshot: &SemanticSnapshot) -> Result<(), String> {
    if snapshot.app_id.is_empty()
        || snapshot.document_id.is_empty()
        || snapshot.revision == 0
        || snapshot.records.is_empty()
        || snapshot.records.len() > 10_000
    {
        return Err("P7E semantic snapshot invalid".into());
    }
    let mut identities = std::collections::BTreeSet::new();
    for record in &snapshot.records {
        if !KINDS.contains(&record.kind.as_str())
            || record.id.is_empty()
            || record.revision == 0
            || record.classification != "NORMAL"
            || !identities.insert((&record.kind, &record.id, record.revision))
        {
            return Err("P7E semantic record identity invalid".into());
        }
        let value: Value = serde_json::from_str(&record.content_json)
            .map_err(|_| "P7E semantic JSON invalid".to_owned())?;
        let object = value
            .as_object()
            .ok_or_else(|| "P7E semantic record must be object".to_owned())?;
        let expected = [
            "format",
            "semanticVersion",
            "kind",
            "id",
            "revision",
            "value",
        ];
        if object.len() != expected.len()
            || expected.iter().any(|key| !object.contains_key(*key))
            || object.get("format").and_then(Value::as_str) != Some(RECORD_FORMAT)
            || object.get("semanticVersion").and_then(Value::as_u64) != Some(1)
            || object.get("kind").and_then(Value::as_str) != Some(record.kind.as_str())
            || object.get("id").and_then(Value::as_str) != Some(record.id.as_str())
            || object.get("revision").and_then(Value::as_u64) != Some(record.revision)
            || !object.get("value").is_some_and(Value::is_object)
        {
            return Err("P7E semantic record shape invalid".into());
        }
        reject_unsafe(object.get("value").expect("checked"))?;
    }
    Ok(())
}

fn reject_unsafe(value: &Value) -> Result<(), String> {
    match value {
        Value::Object(object) => {
            for (key, child) in object {
                if forbidden_key(key) {
                    return Err("P7E forbidden semantic field".into());
                }
                reject_unsafe(child)?;
            }
        }
        Value::Array(values) => {
            for child in values {
                reject_unsafe(child)?;
            }
        }
        Value::String(text) if sensitive(text) => return Err("P7E sensitive semantic text".into()),
        _ => {}
    }
    Ok(())
}

fn forbidden_key(key: &str) -> bool {
    let lower = key.to_ascii_lowercase();
    [
        "credential",
        "api_key",
        "apikey",
        "authorization",
        "token",
        "prompt",
        "runspec",
        "uri",
        "path",
        "storagekey",
        "attachment",
        "diagnostic",
        "runtime",
    ]
    .iter()
    .any(|needle| lower.contains(needle))
}
fn sensitive(value: &str) -> bool {
    let lower = value.to_ascii_lowercase();
    lower.contains("api key:")
        || lower.contains("authorization:")
        || lower.contains("recovery code:")
        || value.contains("密码：")
        || value.contains("密码:")
}
fn valid_segment(value: &str) -> Result<(), String> {
    if value.is_empty()
        || value.len() > 128
        || !value
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || byte == b'-' || byte == b'_')
    {
        Err("P7E workspace reference invalid".into())
    } else {
        Ok(())
    }
}
fn sorted_records(records: &[SemanticRecord]) -> Vec<SemanticRecord> {
    let mut value = records.to_vec();
    value.sort_by(|a, b| (&a.kind, &a.id, a.revision).cmp(&(&b.kind, &b.id, b.revision)));
    value
}
fn canonical_snapshot(snapshot: &SemanticSnapshot) -> Result<String, String> {
    let records: Vec<Value> = sorted_records(&snapshot.records)
        .iter()
        .map(|record| {
            serde_json::from_str(&record.content_json)
                .map_err(|_| "P7E semantic JSON invalid".to_owned())
        })
        .collect::<Result<_, _>>()?;
    canonical(
        &serde_json::json!({"appId":snapshot.app_id,"documentId":snapshot.document_id,"revision":snapshot.revision,"records":records}),
    )
}
fn canonical(value: &Value) -> Result<String, String> {
    match value {
        Value::Null => Ok("null".into()),
        Value::Bool(value) => Ok(value.to_string()),
        Value::Number(value) => Ok(value.to_string()),
        Value::String(value) => {
            serde_json::to_string(value).map_err(|_| "P7E canonical string failed".to_owned())
        }
        Value::Array(values) => Ok(format!(
            "[{}]",
            values
                .iter()
                .map(canonical)
                .collect::<Result<Vec<_>, _>>()?
                .join(",")
        )),
        Value::Object(values) => {
            let mut ordered: Vec<(&String, &Value)> = values.iter().collect();
            ordered.sort_by(|a, b| a.0.cmp(b.0));
            Ok(format!(
                "{{{}}}",
                ordered
                    .into_iter()
                    .map(|(key, value)| Ok(format!(
                        "{}:{}",
                        serde_json::to_string(key)
                            .map_err(|_| "P7E canonical key failed".to_owned())?,
                        canonical(value)?
                    )))
                    .collect::<Result<Vec<_>, String>>()?
                    .join(",")
            ))
        }
    }
}
fn sha256(bytes: &[u8]) -> String {
    format!("{:x}", Sha256::digest(bytes))
}
fn verify_database(
    database: &Path,
    snapshot: &SemanticSnapshot,
    expected_hash: &str,
) -> Result<(), String> {
    let db = Connection::open(database).map_err(|_| "P7E staged SQLite unreadable".to_owned())?;
    let count: usize = db
        .query_row("SELECT COUNT(*) FROM sync_semantic_records", [], |row| {
            row.get(0)
        })
        .map_err(|_| "P7E staged SQLite count unreadable".to_owned())?;
    if count != snapshot.records.len() || read_hash(database)? != expected_hash {
        return Err("P7E staged SQLite readback mismatch".into());
    }
    Ok(())
}
fn read_hash(database: &Path) -> Result<String, String> {
    let db = Connection::open(database).map_err(|_| "P7E staged SQLite unreadable".to_owned())?;
    db.query_row(
        "SELECT snapshot_hash FROM sync_workspace WHERE id=1",
        [],
        |row| row.get(0),
    )
    .map_err(|_| "P7E staged SQLite header unreadable".to_owned())
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

    fn snapshot(title: &str) -> SemanticSnapshot {
        SemanticSnapshot {
            app_id: "com.nanzhufeng.ai".into(),
            document_id: "desktop-p7e".into(),
            revision: 2,
            records: vec![
                SemanticRecord {
                    kind: "project".into(),
                    id: "project-p7e".into(),
                    revision: 2,
                    classification: "NORMAL".into(),
                    content_json: format!(
                        r#"{{"format":"{RECORD_FORMAT}","id":"project-p7e","kind":"project","revision":2,"semanticVersion":1,"value":{{"archived":false,"title":"{title}"}}}}"#
                    ),
                },
                SemanticRecord {
                    kind: "safe_settings".into(),
                    id: "safe-settings".into(),
                    revision: 1,
                    classification: "NORMAL".into(),
                    content_json: format!(
                        r#"{{"format":"{RECORD_FORMAT}","id":"safe-settings","kind":"safe_settings","revision":1,"semanticVersion":1,"value":{{"theme":"SYSTEM"}}}}"#
                    ),
                },
            ],
        }
    }

    fn shared_android_fixture() -> SemanticSnapshot {
        let fixture_path = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
            .join("../../protocol/fixtures/nfai.sync.semantic.v1.golden.json");
        let fixture: Value =
            serde_json::from_slice(&fs::read(fixture_path).expect("shared semantic fixture"))
                .expect("shared semantic fixture JSON");
        let records = fixture["records"]
            .as_array()
            .expect("records")
            .iter()
            .map(|record| SemanticRecord {
                kind: record["kind"].as_str().expect("kind").into(),
                id: record["id"].as_str().expect("id").into(),
                revision: record["revision"].as_u64().expect("revision"),
                classification: record["classification"]
                    .as_str()
                    .expect("classification")
                    .into(),
                content_json: record["content"].to_string(),
            })
            .collect();
        SemanticSnapshot {
            app_id: fixture["appId"].as_str().expect("app id").into(),
            document_id: fixture["documentId"].as_str().expect("document id").into(),
            revision: fixture["revision"].as_u64().expect("revision"),
            records,
        }
    }

    #[test]
    fn stages_new_isolated_sqlite_switches_atomically_and_never_touches_current_workspace() {
        let temp = tempdir().unwrap();
        let store = IsolatedWorkspaceStore::open(temp.path().join("p7e")).unwrap();
        let stage = store
            .stage(&snapshot("from Android semantic record"))
            .unwrap();
        assert!(!temp.path().join("p7e/workspaces/desktop-b").exists());
        assert_eq!(
            stage,
            store.switch_atomically(&stage, "desktop-b", false).unwrap()
        );
        let db = Connection::open(
            temp.path()
                .join("p7e/workspaces/desktop-b/workspace.sqlite3"),
        )
        .unwrap();
        assert_eq!(
            2usize,
            db.query_row("SELECT COUNT(*) FROM sync_semantic_records", [], |row| {
                row.get::<_, usize>(0)
            })
            .unwrap()
        );
    }

    #[test]
    fn accepts_the_same_android_semantic_fixture_without_p6_exchange_preflight() {
        let temp = tempdir().unwrap();
        let store = IsolatedWorkspaceStore::open(temp.path().join("p7e")).unwrap();
        let fixture = shared_android_fixture();
        let stage = store.stage(&fixture).unwrap();
        assert_eq!(
            stage,
            store
                .switch_atomically(&stage, "android-fixture", false)
                .unwrap()
        );
    }

    #[test]
    fn p7a_open_new_workspace_edit_seal_and_second_open_round_trip_are_isolated() {
        let temp = tempdir().unwrap();
        let store = IsolatedWorkspaceStore::open(temp.path().join("p7e")).unwrap();
        let source = snapshot("Android source");
        let payload = snapshot_to_payload(&source).unwrap();
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
        let first_hash = store
            .open_to_new_workspace(
                &envelope,
                "fixture recovery only",
                "com.nanzhufeng.ai",
                "desktop-p7e",
                2,
                "desktop-b",
            )
            .unwrap();
        assert!(temp
            .path()
            .join("p7e/workspaces/desktop-b/workspace.sqlite3")
            .is_file());
        assert!(!temp.path().join("workspace.sqlite3").exists());
        let mut edited = store.read_workspace_snapshot("desktop-b").unwrap();
        let project = edited
            .records
            .iter_mut()
            .find(|record| record.kind == "project")
            .unwrap();
        project.revision = 3;
        project.content_json = format!(
            r#"{{"format":"{RECORD_FORMAT}","id":"project-p7e","kind":"project","revision":3,"semanticVersion":1,"value":{{"archived":false,"title":"Desktop edit"}}}}"#
        );
        edited.revision = 3;
        let staged = store.stage(&edited).unwrap();
        assert_ne!(first_hash, staged);
        assert_eq!(
            staged,
            store.switch_atomically(&staged, "desktop-b", true).unwrap()
        );
        let returned = store
            .seal_workspace("desktop-b", "fixture recovery only", &[9; 32])
            .unwrap();
        let opened = sync_v1::open(
            &returned,
            "fixture recovery only",
            "com.nanzhufeng.ai",
            "desktop-p7e",
            3,
        )
        .unwrap();
        assert_eq!(
            "Desktop edit",
            opened.payload["records"][0]["content"]["value"]["title"]
        );
    }

    #[test]
    fn replacement_requires_confirmation_and_staging_rollback_is_non_destructive() {
        let temp = tempdir().unwrap();
        let store = IsolatedWorkspaceStore::open(temp.path().join("p7e")).unwrap();
        let first = store.stage(&snapshot("first")).unwrap();
        store.switch_atomically(&first, "desktop-b", false).unwrap();
        let second = store.stage(&snapshot("second")).unwrap();
        assert!(store
            .switch_atomically(&second, "desktop-b", false)
            .is_err());
        store.rollback_staging(&second).unwrap();
        let db = Connection::open(
            temp.path()
                .join("p7e/workspaces/desktop-b/workspace.sqlite3"),
        )
        .unwrap();
        assert_eq!("first", db.query_row("SELECT json_extract(content_json, '$.value.title') FROM sync_semantic_records WHERE kind='project'", [], |row| row.get::<_, String>(0)).unwrap());
    }

    #[test]
    fn rejects_p6_shape_and_secret_like_semantic_fields() {
        let temp = tempdir().unwrap();
        let store = IsolatedWorkspaceStore::open(temp.path().join("p7e")).unwrap();
        let mut p6 = snapshot("safe");
        p6.records[0].content_json = r#"{"format":"nfai.exchange","version":1}"#.into();
        assert!(store.stage(&p6).is_err());
        let mut secret = snapshot("safe");
        secret.records[0].content_json = format!(
            r#"{{"format":"{RECORD_FORMAT}","id":"project-p7e","kind":"project","revision":2,"semanticVersion":1,"value":{{"apiKey":"forbidden"}}}}"#
        );
        assert!(store.stage(&secret).is_err());
    }
}
