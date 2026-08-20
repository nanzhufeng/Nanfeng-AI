//! P3-I's standalone, content-free execution receipt boundary. It owns neither IPC nor transport.

use rusqlite::{params, Connection, OptionalExtension};
use std::path::Path;

pub const PROTOCOL: &str = "conversation-real-text-execution-v1";

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Request {
    pub execution_id: String,
    pub idempotency_key: String,
    pub request_fingerprint: String,
    pub conversation_id: String,
    pub user_message_id: String,
    pub assistant_message_id: String,
    pub invocation_id: String,
    pub attempt_id: String,
    pub created_at_ms: i64,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum State {
    Prepared,
    Running,
    Succeeded,
    Failed,
    Cancelled,
}

impl State {
    fn as_str(self) -> &'static str {
        match self {
            Self::Prepared => "PREPARED",
            Self::Running => "RUNNING",
            Self::Succeeded => "SUCCEEDED",
            Self::Failed => "FAILED",
            Self::Cancelled => "CANCELLED",
        }
    }

    fn parse(value: String) -> Result<Self, String> {
        match value.as_str() {
            "PREPARED" => Ok(Self::Prepared),
            "RUNNING" => Ok(Self::Running),
            "SUCCEEDED" => Ok(Self::Succeeded),
            "FAILED" => Ok(Self::Failed),
            "CANCELLED" => Ok(Self::Cancelled),
            _ => Err("P3I persisted state invalid".into()),
        }
    }

    fn terminal(self) -> bool {
        matches!(self, Self::Succeeded | Self::Failed | Self::Cancelled)
    }
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Record {
    pub request: Request,
    pub state: State,
    pub updated_at_ms: i64,
    pub terminal_at_ms: Option<i64>,
    pub safe_error_code: Option<String>,
}

#[derive(Debug, PartialEq, Eq)]
pub enum PrepareResult {
    Prepared(Record),
    Replayed(Record),
    Conflict,
}
#[derive(Debug, PartialEq, Eq)]
pub enum TransitionResult {
    Updated(Box<Record>),
    Rejected,
}

pub struct Ledger {
    connection: Connection,
}

impl Ledger {
    pub fn open(root: &Path) -> Result<Self, String> {
        std::fs::create_dir_all(root)
            .map_err(|_| "P3I local receipt directory unavailable".to_owned())?;
        let connection = Connection::open(root.join("conversation-real-text-execution-v1.sqlite3"))
            .map_err(|_| "P3I local receipt database unavailable".to_owned())?;
        connection.execute_batch("CREATE TABLE IF NOT EXISTS conversation_real_text_executions (execution_id TEXT PRIMARY KEY NOT NULL, idempotency_key TEXT UNIQUE NOT NULL, request_fingerprint TEXT NOT NULL, conversation_id TEXT NOT NULL, user_message_id TEXT NOT NULL, assistant_message_id TEXT NOT NULL, invocation_id TEXT NOT NULL, attempt_id TEXT NOT NULL, state TEXT NOT NULL, created_at_ms INTEGER NOT NULL, updated_at_ms INTEGER NOT NULL, terminal_at_ms INTEGER, safe_error_code TEXT); CREATE INDEX IF NOT EXISTS p3i_execution_conversation ON conversation_real_text_executions(conversation_id); PRAGMA user_version=1;")
            .map_err(|_| "P3I local receipt migration failed".to_owned())?;
        Ok(Self { connection })
    }

    pub fn prepare(&self, request: &Request) -> Result<PrepareResult, String> {
        valid_request(request)?;
        if let Some(existing) = self.by_key(&request.idempotency_key)? {
            return Ok(if existing.request == *request {
                PrepareResult::Replayed(existing)
            } else {
                PrepareResult::Conflict
            });
        }
        if self.by_id(&request.execution_id)?.is_some() {
            return Ok(PrepareResult::Conflict);
        }
        self.connection.execute("INSERT INTO conversation_real_text_executions (execution_id,idempotency_key,request_fingerprint,conversation_id,user_message_id,assistant_message_id,invocation_id,attempt_id,state,created_at_ms,updated_at_ms,terminal_at_ms,safe_error_code) VALUES (?1,?2,?3,?4,?5,?6,?7,?8,'PREPARED',?9,?9,NULL,NULL)", params![request.execution_id,request.idempotency_key,request.request_fingerprint,request.conversation_id,request.user_message_id,request.assistant_message_id,request.invocation_id,request.attempt_id,request.created_at_ms])
            .map_err(|_| "P3I receipt write rejected".to_owned())?;
        self.by_id(&request.execution_id)?
            .map(PrepareResult::Prepared)
            .ok_or_else(|| "P3I receipt readback failed".to_owned())
    }

    pub fn transition(
        &self,
        execution_id: &str,
        expected: State,
        next: State,
        updated_at_ms: i64,
        safe_error_code: Option<&str>,
    ) -> Result<TransitionResult, String> {
        let current = match self.by_id(execution_id)? {
            Some(value) => value,
            None => return Ok(TransitionResult::Rejected),
        };
        if current.state != expected {
            return Ok(TransitionResult::Rejected);
        }
        let candidate = match advance(
            &current,
            next,
            updated_at_ms,
            safe_error_code.map(str::to_owned),
        ) {
            Ok(value) => value,
            Err(_) => return Ok(TransitionResult::Rejected),
        };
        let changed = self.connection.execute("UPDATE conversation_real_text_executions SET state=?1,updated_at_ms=?2,terminal_at_ms=?3,safe_error_code=?4 WHERE execution_id=?5 AND state=?6", params![candidate.state.as_str(),candidate.updated_at_ms,candidate.terminal_at_ms,candidate.safe_error_code,candidate.request.execution_id,expected.as_str()])
            .map_err(|_| "P3I receipt transition failed".to_owned())?;
        if changed != 1 {
            return Ok(TransitionResult::Rejected);
        }
        self.by_id(execution_id)?
            .map(|record| TransitionResult::Updated(Box::new(record)))
            .ok_or_else(|| "P3I receipt transition readback failed".to_owned())
    }

    pub fn by_id(&self, execution_id: &str) -> Result<Option<Record>, String> {
        self.connection.query_row("SELECT execution_id,idempotency_key,request_fingerprint,conversation_id,user_message_id,assistant_message_id,invocation_id,attempt_id,state,created_at_ms,updated_at_ms,terminal_at_ms,safe_error_code FROM conversation_real_text_executions WHERE execution_id=?1", [execution_id], row).optional().map_err(|_| "P3I receipt read failed".to_owned())
    }

    fn by_key(&self, idempotency_key: &str) -> Result<Option<Record>, String> {
        self.connection.query_row("SELECT execution_id,idempotency_key,request_fingerprint,conversation_id,user_message_id,assistant_message_id,invocation_id,attempt_id,state,created_at_ms,updated_at_ms,terminal_at_ms,safe_error_code FROM conversation_real_text_executions WHERE idempotency_key=?1", [idempotency_key], row).optional().map_err(|_| "P3I receipt read failed".to_owned())
    }

    #[cfg(test)]
    fn column_names(&self) -> Vec<String> {
        let mut statement = self
            .connection
            .prepare("PRAGMA table_info(conversation_real_text_executions)")
            .unwrap();
        statement
            .query_map([], |row| row.get(1))
            .unwrap()
            .map(Result::unwrap)
            .collect()
    }
}

fn row(row: &rusqlite::Row<'_>) -> rusqlite::Result<Record> {
    Ok(Record {
        request: Request {
            execution_id: row.get(0)?,
            idempotency_key: row.get(1)?,
            request_fingerprint: row.get(2)?,
            conversation_id: row.get(3)?,
            user_message_id: row.get(4)?,
            assistant_message_id: row.get(5)?,
            invocation_id: row.get(6)?,
            attempt_id: row.get(7)?,
            created_at_ms: row.get(9)?,
        },
        state: State::parse(row.get(8)?).map_err(|_| rusqlite::Error::InvalidQuery)?,
        updated_at_ms: row.get(10)?,
        terminal_at_ms: row.get(11)?,
        safe_error_code: row.get(12)?,
    })
}

fn valid_request(request: &Request) -> Result<(), String> {
    let ids = [
        &request.execution_id,
        &request.idempotency_key,
        &request.conversation_id,
        &request.user_message_id,
        &request.assistant_message_id,
        &request.invocation_id,
        &request.attempt_id,
    ];
    if ids.iter().any(|value| !opaque(value))
        || ids.iter().collect::<std::collections::BTreeSet<_>>().len() != ids.len()
        || request.created_at_ms < 0
        || !hash(&request.request_fingerprint)
    {
        return Err("P3I request invalid".into());
    }
    Ok(())
}

fn advance(
    current: &Record,
    next: State,
    updated_at_ms: i64,
    safe_error_code: Option<String>,
) -> Result<Record, String> {
    if current.state.terminal() || updated_at_ms < current.updated_at_ms {
        return Err("P3I transition rejected".into());
    }
    let legal = match current.state {
        State::Prepared => matches!(next, State::Running | State::Failed | State::Cancelled),
        State::Running => matches!(next, State::Succeeded | State::Failed | State::Cancelled),
        _ => false,
    };
    if !legal
        || (matches!(next, State::Failed | State::Cancelled)
            && !safe_error_code.as_deref().is_some_and(safe_code))
        || (matches!(next, State::Prepared | State::Running | State::Succeeded)
            && safe_error_code.is_some())
    {
        return Err("P3I transition rejected".into());
    }
    Ok(Record {
        request: current.request.clone(),
        state: next,
        updated_at_ms,
        terminal_at_ms: next.terminal().then_some(updated_at_ms),
        safe_error_code,
    })
}

fn opaque(value: &str) -> bool {
    !value.is_empty()
        && value.len() <= 256
        && value
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || matches!(c, '-' | '_' | ':'))
}
fn hash(value: &str) -> bool {
    value.len() == 64
        && value
            .bytes()
            .all(|b| b.is_ascii_digit() || (b'a'..=b'f').contains(&b))
}
fn safe_code(value: &str) -> bool {
    !value.is_empty()
        && value
            .chars()
            .all(|c| c.is_ascii_uppercase() || c.is_ascii_digit() || c == '_')
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

    fn request() -> Request {
        Request {
            execution_id: "execution-1".into(),
            idempotency_key: "intent-1".into(),
            request_fingerprint: "a".repeat(64),
            conversation_id: "conversation-1".into(),
            user_message_id: "user-1".into(),
            assistant_message_id: "assistant-1".into(),
            invocation_id: "invocation-1".into(),
            attempt_id: "attempt-1".into(),
            created_at_ms: 1,
        }
    }

    #[test]
    fn request_is_idempotent_and_conflicts_on_different_fact() {
        let root = tempdir().unwrap();
        let ledger = Ledger::open(root.path()).unwrap();
        let value = request();
        assert!(matches!(
            ledger.prepare(&value).unwrap(),
            PrepareResult::Prepared(_)
        ));
        assert!(matches!(
            ledger.prepare(&value).unwrap(),
            PrepareResult::Replayed(_)
        ));
        assert_eq!(
            ledger
                .prepare(&Request {
                    request_fingerprint: "b".repeat(64),
                    ..value
                })
                .unwrap(),
            PrepareResult::Conflict
        );
    }

    #[test]
    fn failed_and_cancelled_are_terminal_across_reopen() {
        let root = tempdir().unwrap();
        let value = request();
        let ledger = Ledger::open(root.path()).unwrap();
        ledger.prepare(&value).unwrap();
        assert!(matches!(
            ledger
                .transition(
                    &value.execution_id,
                    State::Prepared,
                    State::Failed,
                    2,
                    Some("EGRESS_DISABLED")
                )
                .unwrap(),
            TransitionResult::Updated(_)
        ));
        drop(ledger);
        let reopened = Ledger::open(root.path()).unwrap();
        assert_eq!(
            reopened.by_id(&value.execution_id).unwrap().unwrap().state,
            State::Failed
        );
        assert_eq!(
            reopened
                .transition(
                    &value.execution_id,
                    State::Failed,
                    State::Succeeded,
                    3,
                    None
                )
                .unwrap(),
            TransitionResult::Rejected
        );
    }

    #[test]
    fn persisted_columns_exclude_content_credentials_attachments_usage_and_cost() {
        let root = tempdir().unwrap();
        let ledger = Ledger::open(root.path()).unwrap();
        let forbidden = [
            "prompt",
            "response",
            "credential",
            "secret",
            "authorization",
            "uri",
            "path",
            "attachment",
            "usage",
            "cost",
        ];
        assert!(ledger
            .column_names()
            .iter()
            .all(|name| !forbidden.iter().any(|word| name.contains(word))));
        assert_eq!(PROTOCOL, "conversation-real-text-execution-v1");
    }
}
