//! P6-L1 local exact-reuse index. It stores only hashes and existing message references.

use rusqlite::{params, Connection, OptionalExtension};
use std::collections::BTreeMap;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Outcome {
    LocalExactHit,
    Miss,
    Ineligible,
    Unknown,
}
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Sensitivity {
    Low,
    Medium,
    High,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ExactKey {
    pub scope_id: String,
    pub provider_id: String,
    pub model_snapshot_id: String,
    pub endpoint_mode: String,
    pub generation_parameters_hash: String,
    pub tool_schema_hash: String,
    pub context_manifest_hash: String,
    pub message_tree_hash: String,
    pub attachment_hash: String,
    pub template_version: String,
    pub policy_version: u64,
    pub canonical_request_hash: String,
    pub sensitivity: Sensitivity,
}
impl ExactKey {
    pub fn validate(&self) -> Result<(), &'static str> {
        if self.policy_version == 0
            || [
                self.scope_id.as_str(),
                self.provider_id.as_str(),
                self.model_snapshot_id.as_str(),
                self.endpoint_mode.as_str(),
                self.template_version.as_str(),
            ]
            .iter()
            .any(|id| !stable_id(id))
        {
            return Err("local exact reuse key 无效");
        }
        if [
            self.generation_parameters_hash.as_str(),
            self.tool_schema_hash.as_str(),
            self.context_manifest_hash.as_str(),
            self.message_tree_hash.as_str(),
            self.attachment_hash.as_str(),
            self.canonical_request_hash.as_str(),
        ]
        .iter()
        .any(|hash| !sha256(hash))
        {
            return Err("local exact reuse hash 无效");
        }
        Ok(())
    }
}
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Entry {
    pub key: ExactKey,
    pub response_message_id: String,
    pub created_at_ms: u64,
    pub expires_at_ms: u64,
    pub revoked: bool,
}
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Decision {
    pub outcome: Outcome,
    pub response_message_id: Option<String>,
    pub reason: &'static str,
}

/// P6-L3's content-free route result. It does not render a message or start an execution.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum DispatchResult {
    Reused { response_message_id: String },
    Continued { decision: Decision },
}

/// A future explicit execution owner may implement this port. P6-L3 never sees text, a
/// credential, a transport, a Provider Attempt or a Usage Ledger.
pub trait DispatchPort {
    fn reuse_existing_local_response(&mut self, response_message_id: &str);
    fn continue_without_reuse(&mut self, decision: &Decision);
}

pub fn dispatch(decision: Decision, port: &mut impl DispatchPort) -> DispatchResult {
    if decision.outcome == Outcome::LocalExactHit {
        if let Some(response_message_id) = decision.response_message_id.as_deref() {
            port.reuse_existing_local_response(response_message_id);
            return DispatchResult::Reused {
                response_message_id: response_message_id.to_owned(),
            };
        }
        let safe = make_decision(Outcome::Unknown, None, "本地精确复用记录缺少消息引用");
        port.continue_without_reuse(&safe);
        return DispatchResult::Continued { decision: safe };
    }
    port.continue_without_reuse(&decision);
    DispatchResult::Continued { decision }
}
#[derive(Default)]
pub struct LocalExactReuseIndex {
    entries: BTreeMap<String, Entry>,
}

impl LocalExactReuseIndex {
    pub fn record(&mut self, entry: Entry) -> Result<bool, &'static str> {
        entry.key.validate()?;
        if !stable_id(&entry.response_message_id) || entry.expires_at_ms <= entry.created_at_ms {
            return Err("local exact reuse entry 无效");
        }
        if entry.key.sensitivity == Sensitivity::High || entry.revoked {
            return Ok(false);
        }
        match self.entries.get(&entry.key.canonical_request_hash) {
            Some(old) if old != &entry => Ok(false),
            Some(_) => Ok(true),
            None => {
                self.entries
                    .insert(entry.key.canonical_request_hash.clone(), entry);
                Ok(true)
            }
        }
    }
    pub fn restore(&mut self, entry: Entry) -> Result<bool, &'static str> {
        entry.key.validate()?;
        if !stable_id(&entry.response_message_id)
            || entry.expires_at_ms <= entry.created_at_ms
            || entry.key.sensitivity == Sensitivity::High
        {
            return Ok(false);
        }
        match self.entries.get(&entry.key.canonical_request_hash) {
            Some(old) if old != &entry => Ok(false),
            Some(_) => Ok(true),
            None => {
                self.entries
                    .insert(entry.key.canonical_request_hash.clone(), entry);
                Ok(true)
            }
        }
    }
    pub fn resolve(&self, key: Option<&ExactKey>, temporary: bool, now_ms: u64) -> Decision {
        let Some(key) = key else {
            return make_decision(Outcome::Unknown, None, "缺少完整精确复用键");
        };
        if key.validate().is_err() {
            return make_decision(Outcome::Unknown, None, "精确复用键无效");
        }
        if temporary {
            return make_decision(Outcome::Ineligible, None, "临时会话不建立本地精确复用");
        }
        if key.sensitivity == Sensitivity::High {
            return make_decision(Outcome::Ineligible, None, "高敏感请求不建立本地精确复用");
        }
        let Some(entry) = self.entries.get(&key.canonical_request_hash) else {
            return make_decision(Outcome::Miss, None, "没有完全一致的本地结果");
        };
        if entry.key != *key {
            return make_decision(Outcome::Miss, None, "精确键字段不一致");
        }
        if entry.revoked || now_ms >= entry.expires_at_ms {
            return make_decision(Outcome::Miss, None, "本地结果已撤销或过期");
        }
        make_decision(
            Outcome::LocalExactHit,
            Some(entry.response_message_id.clone()),
            "复用既有本地消息；不会请求 Provider",
        )
    }
}

/// P6-L2's durable adapter. The table contains hashes, safe identifiers and lifecycle metadata only.
pub struct SqliteLocalExactReuseStore;

impl SqliteLocalExactReuseStore {
    pub fn migrate(connection: &Connection) -> Result<(), String> {
        connection.execute_batch("CREATE TABLE IF NOT EXISTS local_exact_reuse_entries (canonical_request_hash TEXT PRIMARY KEY NOT NULL, scope_id TEXT NOT NULL, provider_id TEXT NOT NULL, model_snapshot_id TEXT NOT NULL, endpoint_mode TEXT NOT NULL, generation_parameters_hash TEXT NOT NULL, tool_schema_hash TEXT NOT NULL, context_manifest_hash TEXT NOT NULL, message_tree_hash TEXT NOT NULL, attachment_hash TEXT NOT NULL, template_version TEXT NOT NULL, policy_version INTEGER NOT NULL, sensitivity TEXT NOT NULL, response_message_id TEXT NOT NULL, created_at_ms INTEGER NOT NULL, expires_at_ms INTEGER NOT NULL, revoked INTEGER NOT NULL); CREATE INDEX IF NOT EXISTS local_exact_reuse_entries_expiry ON local_exact_reuse_entries(expires_at_ms); CREATE INDEX IF NOT EXISTS local_exact_reuse_entries_revoked ON local_exact_reuse_entries(revoked);").map_err(|_| "local exact reuse migration failed".to_owned())
    }

    pub fn record(connection: &Connection, entry: &Entry) -> Result<bool, String> {
        entry.key.validate().map_err(str::to_owned)?;
        if !stable_id(&entry.response_message_id) || entry.expires_at_ms <= entry.created_at_ms {
            return Err("local exact reuse entry invalid".into());
        }
        if entry.key.sensitivity == Sensitivity::High || entry.revoked {
            return Ok(false);
        }
        let existing = Self::find(connection, &entry.key.canonical_request_hash)?;
        if let Some(existing) = existing {
            return Ok(existing == *entry);
        }
        connection.execute("INSERT INTO local_exact_reuse_entries(canonical_request_hash,scope_id,provider_id,model_snapshot_id,endpoint_mode,generation_parameters_hash,tool_schema_hash,context_manifest_hash,message_tree_hash,attachment_hash,template_version,policy_version,sensitivity,response_message_id,created_at_ms,expires_at_ms,revoked) VALUES(?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,?11,?12,?13,?14,?15,?16,0)", params![entry.key.canonical_request_hash, entry.key.scope_id, entry.key.provider_id, entry.key.model_snapshot_id, entry.key.endpoint_mode, entry.key.generation_parameters_hash, entry.key.tool_schema_hash, entry.key.context_manifest_hash, entry.key.message_tree_hash, entry.key.attachment_hash, entry.key.template_version, entry.key.policy_version, sensitivity_name(entry.key.sensitivity), entry.response_message_id, entry.created_at_ms, entry.expires_at_ms]).map_err(|_| "local exact reuse insert failed".to_owned())?;
        Ok(true)
    }

    pub fn resolve(
        connection: &Connection,
        key: Option<&ExactKey>,
        temporary: bool,
        now_ms: u64,
    ) -> Decision {
        let Some(key) = key else {
            return LocalExactReuseIndex::default().resolve(None, temporary, now_ms);
        };
        let Ok(entry) = Self::find(connection, &key.canonical_request_hash) else {
            return make_decision(Outcome::Unknown, None, "本地精确复用记录不可读");
        };
        let mut index = LocalExactReuseIndex::default();
        if let Some(entry) = entry {
            if index.restore(entry).ok() != Some(true) {
                return make_decision(Outcome::Unknown, None, "本地精确复用记录无效");
            }
        }
        index.resolve(Some(key), temporary, now_ms)
    }

    pub fn revoke(connection: &Connection, canonical_request_hash: &str) -> Result<bool, String> {
        if !sha256(canonical_request_hash) {
            return Ok(false);
        }
        Ok(connection.execute("UPDATE local_exact_reuse_entries SET revoked=1 WHERE canonical_request_hash=?1 AND revoked=0", [canonical_request_hash]).map_err(|_| "local exact reuse revoke failed".to_owned())? > 0)
    }

    pub fn purge_expired_or_revoked(connection: &Connection, now_ms: u64) -> Result<usize, String> {
        connection
            .execute(
                "DELETE FROM local_exact_reuse_entries WHERE revoked=1 OR expires_at_ms<=?1",
                [now_ms],
            )
            .map_err(|_| "local exact reuse purge failed".to_owned())
    }

    fn find(
        connection: &Connection,
        canonical_request_hash: &str,
    ) -> Result<Option<Entry>, String> {
        connection.query_row("SELECT scope_id,provider_id,model_snapshot_id,endpoint_mode,generation_parameters_hash,tool_schema_hash,context_manifest_hash,message_tree_hash,attachment_hash,template_version,policy_version,sensitivity,response_message_id,created_at_ms,expires_at_ms,revoked FROM local_exact_reuse_entries WHERE canonical_request_hash=?1", [canonical_request_hash], |row| {
            let sensitivity: String = row.get(11)?;
            let sensitivity = match sensitivity.as_str() { "LOW" => Sensitivity::Low, "MEDIUM" => Sensitivity::Medium, "HIGH" => Sensitivity::High, _ => return Err(rusqlite::Error::InvalidQuery) };
            Ok(Entry { key: ExactKey { scope_id: row.get(0)?, provider_id: row.get(1)?, model_snapshot_id: row.get(2)?, endpoint_mode: row.get(3)?, generation_parameters_hash: row.get(4)?, tool_schema_hash: row.get(5)?, context_manifest_hash: row.get(6)?, message_tree_hash: row.get(7)?, attachment_hash: row.get(8)?, template_version: row.get(9)?, policy_version: row.get(10)?, canonical_request_hash: canonical_request_hash.to_owned(), sensitivity }, response_message_id: row.get(12)?, created_at_ms: row.get(13)?, expires_at_ms: row.get(14)?, revoked: row.get::<_, i64>(15)? != 0 })
        }).optional().map_err(|_| "local exact reuse read failed".to_owned())
    }
}

fn sensitivity_name(value: Sensitivity) -> &'static str {
    match value {
        Sensitivity::Low => "LOW",
        Sensitivity::Medium => "MEDIUM",
        Sensitivity::High => "HIGH",
    }
}
fn make_decision(
    outcome: Outcome,
    response_message_id: Option<String>,
    reason: &'static str,
) -> Decision {
    Decision {
        outcome,
        response_message_id,
        reason,
    }
}
fn stable_id(value: &str) -> bool {
    !value.is_empty()
        && value.len() <= 160
        && value
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || b"._:-".contains(&byte))
}
fn sha256(value: &str) -> bool {
    value.len() == 64
        && value
            .bytes()
            .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte))
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;
    fn key(hash: char, sensitivity: Sensitivity) -> ExactKey {
        ExactKey {
            scope_id: "workspace:fixture".into(),
            provider_id: "openrouter".into(),
            model_snapshot_id: "model:fixture".into(),
            endpoint_mode: "chat-completions".into(),
            generation_parameters_hash: "b".repeat(64),
            tool_schema_hash: "c".repeat(64),
            context_manifest_hash: "d".repeat(64),
            message_tree_hash: "e".repeat(64),
            attachment_hash: "f".repeat(64),
            template_version: "template:v1".into(),
            policy_version: 1,
            canonical_request_hash: hash.to_string().repeat(64),
            sensitivity,
        }
    }
    #[test]
    fn exact_only_normal_entry_hits() {
        let mut index = LocalExactReuseIndex::default();
        let exact = key('a', Sensitivity::Low);
        assert!(index
            .record(Entry {
                key: exact.clone(),
                response_message_id: "message:fixture".into(),
                created_at_ms: 10,
                expires_at_ms: 20,
                revoked: false
            })
            .unwrap());
        assert_eq!(
            index.resolve(Some(&exact), false, 19).outcome,
            Outcome::LocalExactHit
        );
        assert_eq!(
            index
                .resolve(Some(&key('9', Sensitivity::Low)), false, 19)
                .outcome,
            Outcome::Miss
        );
        assert_eq!(
            index.resolve(Some(&exact), false, 20).outcome,
            Outcome::Miss
        );
    }
    #[test]
    fn temporary_sensitive_revoked_and_missing_fail_closed() {
        let mut index = LocalExactReuseIndex::default();
        let exact = key('a', Sensitivity::Low);
        assert!(index
            .record(Entry {
                key: exact.clone(),
                response_message_id: "message:fixture".into(),
                created_at_ms: 10,
                expires_at_ms: 30,
                revoked: false
            })
            .unwrap());
        assert_eq!(
            index.resolve(Some(&exact), true, 11).outcome,
            Outcome::Ineligible
        );
        let high = key('b', Sensitivity::High);
        assert!(!index
            .record(Entry {
                key: high.clone(),
                response_message_id: "message:high".into(),
                created_at_ms: 10,
                expires_at_ms: 30,
                revoked: false
            })
            .unwrap());
        assert_eq!(
            index.resolve(Some(&high), false, 11).outcome,
            Outcome::Ineligible
        );
        assert_eq!(index.resolve(None, false, 11).outcome, Outcome::Unknown);
        assert!(!index
            .record(Entry {
                key: key('c', Sensitivity::Low),
                response_message_id: "message:revoked".into(),
                created_at_ms: 10,
                expires_at_ms: 30,
                revoked: true
            })
            .unwrap());
    }

    #[test]
    fn sqlite_store_reopens_and_invalidates_without_storing_text() {
        let directory = tempdir().unwrap();
        let database = directory.path().join("reuse.sqlite3");
        let exact = key('a', Sensitivity::Low);
        let connection = Connection::open(&database).unwrap();
        SqliteLocalExactReuseStore::migrate(&connection).unwrap();
        assert!(SqliteLocalExactReuseStore::record(
            &connection,
            &Entry {
                key: exact.clone(),
                response_message_id: "message:fixture".into(),
                created_at_ms: 10,
                expires_at_ms: 30,
                revoked: false
            }
        )
        .unwrap());
        assert_eq!(
            SqliteLocalExactReuseStore::resolve(&connection, Some(&exact), false, 11).outcome,
            Outcome::LocalExactHit
        );
        assert_eq!(connection.query_row("SELECT COUNT(*) FROM local_exact_reuse_entries WHERE response_message_id='message:fixture'", [], |row| row.get::<_, i64>(0)).unwrap(), 1);
        drop(connection);
        let reopened = Connection::open(&database).unwrap();
        assert_eq!(
            SqliteLocalExactReuseStore::resolve(&reopened, Some(&exact), false, 11).outcome,
            Outcome::LocalExactHit
        );
        assert!(
            SqliteLocalExactReuseStore::revoke(&reopened, &exact.canonical_request_hash).unwrap()
        );
        assert_eq!(
            SqliteLocalExactReuseStore::resolve(&reopened, Some(&exact), false, 11).outcome,
            Outcome::Miss
        );
        assert_eq!(
            SqliteLocalExactReuseStore::purge_expired_or_revoked(&reopened, 11).unwrap(),
            1
        );
    }

    #[test]
    fn dispatches_exact_hit_only_to_existing_local_response() {
        struct Port {
            reused: Vec<String>,
            continued: Vec<Outcome>,
        }
        impl DispatchPort for Port {
            fn reuse_existing_local_response(&mut self, response_message_id: &str) {
                self.reused.push(response_message_id.to_owned());
            }
            fn continue_without_reuse(&mut self, decision: &Decision) {
                self.continued.push(decision.outcome);
            }
        }

        let mut index = LocalExactReuseIndex::default();
        let exact = key('a', Sensitivity::Low);
        index
            .record(Entry {
                key: exact.clone(),
                response_message_id: "message:fixture".into(),
                created_at_ms: 10,
                expires_at_ms: 30,
                revoked: false,
            })
            .unwrap();
        let mut port = Port {
            reused: vec![],
            continued: vec![],
        };

        assert_eq!(
            dispatch(index.resolve(Some(&exact), false, 11), &mut port),
            DispatchResult::Reused {
                response_message_id: "message:fixture".into()
            },
        );
        assert_eq!(port.reused, vec!["message:fixture"]);
        assert!(port.continued.is_empty());
    }

    #[test]
    fn dispatches_miss_ineligible_and_unknown_only_to_content_free_continuation() {
        struct Port {
            reused: Vec<String>,
            continued: Vec<Outcome>,
        }
        impl DispatchPort for Port {
            fn reuse_existing_local_response(&mut self, response_message_id: &str) {
                self.reused.push(response_message_id.to_owned());
            }
            fn continue_without_reuse(&mut self, decision: &Decision) {
                self.continued.push(decision.outcome);
            }
        }
        let index = LocalExactReuseIndex::default();
        let mut port = Port {
            reused: vec![],
            continued: vec![],
        };
        for decision in [
            index.resolve(Some(&key('a', Sensitivity::Low)), false, 11),
            index.resolve(Some(&key('a', Sensitivity::Low)), true, 11),
            index.resolve(None, false, 11),
        ] {
            assert!(matches!(
                dispatch(decision, &mut port),
                DispatchResult::Continued { .. }
            ));
        }
        assert!(port.reused.is_empty());
        assert_eq!(
            port.continued,
            vec![Outcome::Miss, Outcome::Ineligible, Outcome::Unknown]
        );
    }
}
