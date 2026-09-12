//! P0 local-only, append-only Usage Ledger. It owns neither IPC nor provider execution.

use rusqlite::{params, Connection, OptionalExtension};
use std::{collections::BTreeSet, path::Path};

pub const PROTOCOL: &str = "usage-ledger-v1";

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum FactGrade {
    Estimated,
    ProviderReported,
    Reconciled,
}

impl FactGrade {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::Estimated => "ESTIMATED",
            Self::ProviderReported => "PROVIDER_REPORTED",
            Self::Reconciled => "RECONCILED",
        }
    }
    fn parse(value: String) -> Result<Self, String> {
        match value.as_str() {
            "ESTIMATED" => Ok(Self::Estimated),
            "PROVIDER_REPORTED" => Ok(Self::ProviderReported),
            "RECONCILED" => Ok(Self::Reconciled),
            _ => Err("Usage Ledger persisted fact grade invalid".into()),
        }
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Kind {
    StreamPending,
    FinalMeasured,
    ReconciliationAdjustment,
    BudgetReservation,
    BudgetRelease,
}

impl Kind {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::StreamPending => "STREAM_PENDING",
            Self::FinalMeasured => "FINAL_MEASURED",
            Self::ReconciliationAdjustment => "RECONCILIATION_ADJUSTMENT",
            Self::BudgetReservation => "BUDGET_RESERVATION",
            Self::BudgetRelease => "BUDGET_RELEASE",
        }
    }
    fn parse(value: String) -> Result<Self, String> {
        match value.as_str() {
            "STREAM_PENDING" => Ok(Self::StreamPending),
            "FINAL_MEASURED" => Ok(Self::FinalMeasured),
            "RECONCILIATION_ADJUSTMENT" => Ok(Self::ReconciliationAdjustment),
            "BUDGET_RESERVATION" => Ok(Self::BudgetReservation),
            "BUDGET_RELEASE" => Ok(Self::BudgetRelease),
            _ => Err("Usage Ledger persisted kind invalid".into()),
        }
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Source {
    AndroidLocal,
    DesktopLocal,
    CrossPlatformImport,
}

impl Source {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::AndroidLocal => "ANDROID_LOCAL",
            Self::DesktopLocal => "DESKTOP_LOCAL",
            Self::CrossPlatformImport => "CROSS_PLATFORM_IMPORT",
        }
    }
    fn parse(value: String) -> Result<Self, String> {
        match value.as_str() {
            "ANDROID_LOCAL" => Ok(Self::AndroidLocal),
            "DESKTOP_LOCAL" => Ok(Self::DesktopLocal),
            "CROSS_PLATFORM_IMPORT" => Ok(Self::CrossPlatformImport),
            _ => Err("Usage Ledger persisted source invalid".into()),
        }
    }
}

/// Immutable quantitative fact; none of its fields can carry prompts, responses, secrets, paths or attachments.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Entry {
    pub entry_id: String,
    pub replay_token: String,
    pub execution_id: String,
    pub conversation_id: String,
    pub branch_leaf_message_id: String,
    pub invocation_id: String,
    pub attempt_id: String,
    pub kind: Kind,
    pub fact_grade: FactGrade,
    pub requested_model_id: String,
    pub actual_model_id: Option<String>,
    pub input_tokens: Option<i64>,
    pub output_tokens: Option<i64>,
    pub cached_input_tokens: Option<i64>,
    pub charge_micros: Option<i64>,
    pub budget_micros: Option<i64>,
    pub adjustment_micros: Option<i64>,
    pub currency_code: Option<String>,
    /// The source of a persisted charge.  Imported Android historical answers
    /// can carry a local estimate, which must never be relabelled as provider
    /// reported merely because Desktop stores the same numeric amount.
    pub cost_source: Option<String>,
    pub reconciliation_fingerprint: Option<String>,
    pub reconciles_entry_id: Option<String>,
    pub source: Source,
    pub occurred_at_ms: i64,
}

#[derive(Debug, PartialEq, Eq)]
pub enum AppendResult {
    Appended(Entry),
    Replayed(Entry),
    Conflict,
}

/// Read-only, in-memory projection of immutable source facts.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ReadModel {
    pub entries: Vec<Entry>,
    pub pending_stream_entries: Vec<Entry>,
    pub requested_model_ids: BTreeSet<String>,
    pub actual_model_ids: BTreeSet<String>,
    pub input_tokens: i64,
    pub output_tokens: i64,
    pub cached_input_tokens: i64,
    pub charge_micros: i64,
    pub reserved_budget_micros: i64,
    pub released_budget_micros: i64,
    pub reconciliation_adjustment_micros: i64,
}

pub struct Ledger {
    connection: Connection,
}

impl Ledger {
    pub fn open(root: &Path) -> Result<Self, String> {
        std::fs::create_dir_all(root)
            .map_err(|_| "Usage Ledger directory unavailable".to_owned())?;
        let connection = Connection::open(root.join("usage-ledger-v1.sqlite3"))
            .map_err(|_| "Usage Ledger database unavailable".to_owned())?;
        connection.execute_batch("CREATE TABLE IF NOT EXISTS usage_ledger_entries (entry_id TEXT PRIMARY KEY NOT NULL, replay_token TEXT UNIQUE NOT NULL, execution_id TEXT NOT NULL, conversation_id TEXT NOT NULL, branch_leaf_message_id TEXT NOT NULL, invocation_id TEXT NOT NULL, attempt_id TEXT NOT NULL, kind TEXT NOT NULL, fact_grade TEXT NOT NULL, requested_model_id TEXT NOT NULL, actual_model_id TEXT, input_tokens INTEGER, output_tokens INTEGER, cached_input_tokens INTEGER, charge_micros INTEGER, budget_micros INTEGER, adjustment_micros INTEGER, currency_code TEXT, cost_source TEXT, reconciliation_fingerprint TEXT, reconciles_entry_id TEXT, source TEXT NOT NULL, occurred_at_ms INTEGER NOT NULL); CREATE INDEX IF NOT EXISTS usage_ledger_execution ON usage_ledger_entries(execution_id); CREATE INDEX IF NOT EXISTS usage_ledger_conversation ON usage_ledger_entries(conversation_id); CREATE INDEX IF NOT EXISTS usage_ledger_reconciles ON usage_ledger_entries(reconciles_entry_id); CREATE INDEX IF NOT EXISTS usage_ledger_fingerprint ON usage_ledger_entries(reconciliation_fingerprint); PRAGMA user_version=1;")
            .map_err(|_| "Usage Ledger migration failed".to_owned())?;
        let has_cost_source = connection
            .prepare("PRAGMA table_info(usage_ledger_entries)")
            .and_then(|mut statement| {
                statement
                    .query_map([], |row| row.get::<_, String>(1))?
                    .collect::<Result<Vec<_>, _>>()
            })
            .map_err(|_| "Usage Ledger schema read failed".to_owned())?
            .iter()
            .any(|column| column == "cost_source");
        if !has_cost_source {
            connection
                .execute(
                    "ALTER TABLE usage_ledger_entries ADD COLUMN cost_source TEXT",
                    [],
                )
                .map_err(|_| "Usage Ledger cost-source migration failed".to_owned())?;
        }
        Ok(Self { connection })
    }

    pub fn append(&self, entry: &Entry) -> Result<AppendResult, String> {
        valid_entry(entry)?;
        if let Some(existing) = self.by_id(&entry.entry_id)? {
            return Ok(if existing == *entry {
                AppendResult::Replayed(existing)
            } else {
                AppendResult::Conflict
            });
        }
        if let Some(existing) = self.by_replay_token(&entry.replay_token)? {
            return Ok(if existing == *entry {
                AppendResult::Replayed(existing)
            } else {
                AppendResult::Conflict
            });
        }
        if entry.kind == Kind::ReconciliationAdjustment {
            let pending = self.by_id(
                entry
                    .reconciles_entry_id
                    .as_deref()
                    .expect("validated reconciliation target"),
            )?;
            let Some(pending) = pending else {
                return Ok(AppendResult::Conflict);
            };
            if pending.kind != Kind::StreamPending
                || pending.execution_id != entry.execution_id
                || pending.reconciliation_fingerprint != entry.reconciliation_fingerprint
            {
                return Ok(AppendResult::Conflict);
            }
        }
        self.connection.execute("INSERT INTO usage_ledger_entries (entry_id,replay_token,execution_id,conversation_id,branch_leaf_message_id,invocation_id,attempt_id,kind,fact_grade,requested_model_id,actual_model_id,input_tokens,output_tokens,cached_input_tokens,charge_micros,budget_micros,adjustment_micros,currency_code,cost_source,reconciliation_fingerprint,reconciles_entry_id,source,occurred_at_ms) VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,?11,?12,?13,?14,?15,?16,?17,?18,?19,?20,?21,?22,?23)", params![entry.entry_id,entry.replay_token,entry.execution_id,entry.conversation_id,entry.branch_leaf_message_id,entry.invocation_id,entry.attempt_id,entry.kind.as_str(),entry.fact_grade.as_str(),entry.requested_model_id,entry.actual_model_id,entry.input_tokens,entry.output_tokens,entry.cached_input_tokens,entry.charge_micros,entry.budget_micros,entry.adjustment_micros,entry.currency_code,entry.cost_source,entry.reconciliation_fingerprint,entry.reconciles_entry_id,entry.source.as_str(),entry.occurred_at_ms])
            .map_err(|_| "Usage Ledger append rejected".to_owned())?;
        self.by_id(&entry.entry_id)?
            .map(AppendResult::Appended)
            .ok_or_else(|| "Usage Ledger append readback failed".to_owned())
    }

    pub fn entries_for_execution(&self, execution_id: &str) -> Result<Vec<Entry>, String> {
        let mut statement = self.connection.prepare("SELECT entry_id,replay_token,execution_id,conversation_id,branch_leaf_message_id,invocation_id,attempt_id,kind,fact_grade,requested_model_id,actual_model_id,input_tokens,output_tokens,cached_input_tokens,charge_micros,budget_micros,adjustment_micros,currency_code,cost_source,reconciliation_fingerprint,reconciles_entry_id,source,occurred_at_ms FROM usage_ledger_entries WHERE execution_id=?1 ORDER BY occurred_at_ms ASC,entry_id ASC").map_err(|_| "Usage Ledger read failed".to_owned())?;
        let entries = statement
            .query_map([execution_id], row)
            .map_err(|_| "Usage Ledger read failed".to_owned())?
            .collect::<Result<Vec<_>, _>>()
            .map_err(|_| "Usage Ledger persisted row invalid".to_owned());
        entries
    }

    pub fn all_entries(&self) -> Result<Vec<Entry>, String> {
        let mut statement = self.connection.prepare("SELECT entry_id,replay_token,execution_id,conversation_id,branch_leaf_message_id,invocation_id,attempt_id,kind,fact_grade,requested_model_id,actual_model_id,input_tokens,output_tokens,cached_input_tokens,charge_micros,budget_micros,adjustment_micros,currency_code,cost_source,reconciliation_fingerprint,reconciles_entry_id,source,occurred_at_ms FROM usage_ledger_entries ORDER BY occurred_at_ms DESC,entry_id DESC").map_err(|_| "Usage Ledger read failed".to_owned())?;
        let entries = statement
            .query_map([], row)
            .map_err(|_| "Usage Ledger read failed".to_owned())?
            .collect::<Result<Vec<_>, _>>()
            .map_err(|_| "Usage Ledger persisted row invalid".to_owned())?;
        Ok(entries)
    }

    pub fn read_model_for_execution(&self, execution_id: &str) -> Result<ReadModel, String> {
        Ok(read_model(self.entries_for_execution(execution_id)?))
    }

    fn by_id(&self, entry_id: &str) -> Result<Option<Entry>, String> {
        self.connection.query_row("SELECT entry_id,replay_token,execution_id,conversation_id,branch_leaf_message_id,invocation_id,attempt_id,kind,fact_grade,requested_model_id,actual_model_id,input_tokens,output_tokens,cached_input_tokens,charge_micros,budget_micros,adjustment_micros,currency_code,cost_source,reconciliation_fingerprint,reconciles_entry_id,source,occurred_at_ms FROM usage_ledger_entries WHERE entry_id=?1", [entry_id], row).optional().map_err(|_| "Usage Ledger read failed".to_owned())
    }

    fn by_replay_token(&self, replay_token: &str) -> Result<Option<Entry>, String> {
        self.connection.query_row("SELECT entry_id,replay_token,execution_id,conversation_id,branch_leaf_message_id,invocation_id,attempt_id,kind,fact_grade,requested_model_id,actual_model_id,input_tokens,output_tokens,cached_input_tokens,charge_micros,budget_micros,adjustment_micros,currency_code,cost_source,reconciliation_fingerprint,reconciles_entry_id,source,occurred_at_ms FROM usage_ledger_entries WHERE replay_token=?1", [replay_token], row).optional().map_err(|_| "Usage Ledger read failed".to_owned())
    }

    #[cfg(test)]
    fn column_names(&self) -> Vec<String> {
        self.connection
            .prepare("PRAGMA table_info(usage_ledger_entries)")
            .unwrap()
            .query_map([], |row| row.get(1))
            .unwrap()
            .map(Result::unwrap)
            .collect()
    }
}

fn row(row: &rusqlite::Row<'_>) -> rusqlite::Result<Entry> {
    Ok(Entry {
        entry_id: row.get(0)?,
        replay_token: row.get(1)?,
        execution_id: row.get(2)?,
        conversation_id: row.get(3)?,
        branch_leaf_message_id: row.get(4)?,
        invocation_id: row.get(5)?,
        attempt_id: row.get(6)?,
        kind: Kind::parse(row.get(7)?).map_err(|_| rusqlite::Error::InvalidQuery)?,
        fact_grade: FactGrade::parse(row.get(8)?).map_err(|_| rusqlite::Error::InvalidQuery)?,
        requested_model_id: row.get(9)?,
        actual_model_id: row.get(10)?,
        input_tokens: row.get(11)?,
        output_tokens: row.get(12)?,
        cached_input_tokens: row.get(13)?,
        charge_micros: row.get(14)?,
        budget_micros: row.get(15)?,
        adjustment_micros: row.get(16)?,
        currency_code: row.get(17)?,
        cost_source: row.get(18)?,
        reconciliation_fingerprint: row.get(19)?,
        reconciles_entry_id: row.get(20)?,
        source: Source::parse(row.get(21)?).map_err(|_| rusqlite::Error::InvalidQuery)?,
        occurred_at_ms: row.get(22)?,
    })
}

fn read_model(entries: Vec<Entry>) -> ReadModel {
    let reconciled: BTreeSet<String> = entries
        .iter()
        .filter_map(|entry| entry.reconciles_entry_id.clone())
        .collect();
    let measured: Vec<&Entry> = entries
        .iter()
        .filter(|entry| entry.kind != Kind::StreamPending)
        .collect();
    let sum = |values: Vec<Option<i64>>| values.into_iter().flatten().sum();
    ReadModel {
        pending_stream_entries: entries
            .iter()
            .filter(|entry| {
                entry.kind == Kind::StreamPending && !reconciled.contains(&entry.entry_id)
            })
            .cloned()
            .collect(),
        requested_model_ids: entries
            .iter()
            .map(|entry| entry.requested_model_id.clone())
            .collect(),
        actual_model_ids: entries
            .iter()
            .filter_map(|entry| entry.actual_model_id.clone())
            .collect(),
        input_tokens: sum(measured.iter().map(|entry| entry.input_tokens).collect()),
        output_tokens: sum(measured.iter().map(|entry| entry.output_tokens).collect()),
        cached_input_tokens: sum(measured
            .iter()
            .map(|entry| entry.cached_input_tokens)
            .collect()),
        charge_micros: sum(measured.iter().map(|entry| entry.charge_micros).collect()),
        reserved_budget_micros: sum(entries
            .iter()
            .filter(|entry| entry.kind == Kind::BudgetReservation)
            .map(|entry| entry.budget_micros)
            .collect()),
        released_budget_micros: sum(entries
            .iter()
            .filter(|entry| entry.kind == Kind::BudgetRelease)
            .map(|entry| entry.budget_micros)
            .collect()),
        reconciliation_adjustment_micros: sum(entries
            .iter()
            .filter(|entry| entry.kind == Kind::ReconciliationAdjustment)
            .map(|entry| entry.adjustment_micros)
            .collect()),
        entries,
    }
}

fn valid_entry(entry: &Entry) -> Result<(), String> {
    let opaque_ids = [
        &entry.entry_id,
        &entry.execution_id,
        &entry.conversation_id,
        &entry.branch_leaf_message_id,
        &entry.invocation_id,
        &entry.attempt_id,
    ];
    if opaque_ids.iter().any(|value| !opaque(value))
        || !replay_token(&entry.replay_token)
        || !model_id(&entry.requested_model_id)
        || entry
            .actual_model_id
            .as_deref()
            .is_some_and(|value| !model_id(value))
        || entry
            .currency_code
            .as_deref()
            .is_some_and(|value| !currency(value))
        || entry
            .cost_source
            .as_deref()
            .is_some_and(|value| !matches!(value, "PROVIDER_RESPONSE" | "LOCAL_ESTIMATE"))
        || entry
            .reconciliation_fingerprint
            .as_deref()
            .is_some_and(|value| !hash(value))
        || entry.occurred_at_ms < 0
        || [
            entry.input_tokens,
            entry.output_tokens,
            entry.cached_input_tokens,
            entry.charge_micros,
            entry.budget_micros,
        ]
        .into_iter()
        .flatten()
        .any(|value| value < 0)
        || (entry.charge_micros.is_some()
            || entry.budget_micros.is_some()
            || entry.adjustment_micros.is_some())
            && entry.currency_code.is_none()
        || entry.charge_micros.is_some() && entry.cost_source.is_none()
    {
        return Err("Usage Ledger entry invalid".into());
    }
    match entry.kind {
        Kind::StreamPending
            if entry.fact_grade == FactGrade::Estimated
                && entry.reconciliation_fingerprint.is_some()
                && entry.reconciles_entry_id.is_none() =>
        {
            Ok(())
        }
        Kind::ReconciliationAdjustment
            if entry.fact_grade == FactGrade::Reconciled
                && entry.reconciliation_fingerprint.is_some()
                && entry
                    .reconciles_entry_id
                    .as_deref()
                    .is_some_and(|id| id != entry.entry_id) =>
        {
            Ok(())
        }
        Kind::FinalMeasured if entry.fact_grade != FactGrade::Estimated => Ok(()),
        Kind::BudgetReservation | Kind::BudgetRelease if entry.budget_micros.is_some() => Ok(()),
        _ => Err("Usage Ledger entry invalid".into()),
    }
}

fn opaque(value: &str) -> bool {
    !value.is_empty()
        && value.len() <= 256
        && value
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || matches!(c, '-' | '_' | ':'))
}
fn replay_token(value: &str) -> bool {
    !value.is_empty()
        && value.len() <= 160
        && value
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || matches!(c, '.' | '_' | ':' | '-'))
}
fn model_id(value: &str) -> bool {
    !value.is_empty()
        && value.len() <= 200
        && value
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || matches!(c, '.' | '_' | ':' | '/' | '-'))
}
fn currency(value: &str) -> bool {
    value.len() == 3 && value.chars().all(|c| c.is_ascii_uppercase())
}
fn hash(value: &str) -> bool {
    value.len() == 64
        && value
            .bytes()
            .all(|b| b.is_ascii_digit() || (b'a'..=b'f').contains(&b))
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

    fn entry(id: &str, token: &str, kind: Kind, grade: FactGrade) -> Entry {
        Entry {
            entry_id: id.into(),
            replay_token: token.into(),
            execution_id: "execution".into(),
            conversation_id: "conversation".into(),
            branch_leaf_message_id: "branch".into(),
            invocation_id: "invocation".into(),
            attempt_id: "attempt".into(),
            kind,
            fact_grade: grade,
            requested_model_id: "provider/requested".into(),
            actual_model_id: Some("provider/actual".into()),
            input_tokens: None,
            output_tokens: None,
            cached_input_tokens: None,
            charge_micros: None,
            budget_micros: None,
            adjustment_micros: None,
            currency_code: None,
            cost_source: None,
            reconciliation_fingerprint: None,
            reconciles_entry_id: None,
            source: Source::DesktopLocal,
            occurred_at_ms: 1,
        }
    }

    #[test]
    fn exact_append_replays_and_changed_fact_conflicts_across_reopen() {
        let root = tempdir().unwrap();
        let value = entry(
            "entry",
            "replay",
            Kind::FinalMeasured,
            FactGrade::ProviderReported,
        );
        let ledger = Ledger::open(root.path()).unwrap();
        assert!(matches!(
            ledger.append(&value).unwrap(),
            AppendResult::Appended(_)
        ));
        assert!(matches!(
            ledger.append(&value).unwrap(),
            AppendResult::Replayed(_)
        ));
        assert_eq!(ledger.all_entries().unwrap(), vec![value.clone()]);
        assert_eq!(
            ledger
                .append(&Entry {
                    actual_model_id: Some("provider/other".into()),
                    ..value.clone()
                })
                .unwrap(),
            AppendResult::Conflict
        );
        drop(ledger);
        assert!(matches!(
            Ledger::open(root.path()).unwrap().append(&value).unwrap(),
            AppendResult::Replayed(_)
        ));
    }

    #[test]
    fn pending_stream_requires_matching_append_only_reconciliation_and_read_model_preserves_models()
    {
        let root = tempdir().unwrap();
        let ledger = Ledger::open(root.path()).unwrap();
        let pending = Entry {
            actual_model_id: None,
            reconciliation_fingerprint: Some("a".repeat(64)),
            ..entry(
                "pending",
                "pending-token",
                Kind::StreamPending,
                FactGrade::Estimated,
            )
        };
        assert!(matches!(
            ledger.append(&pending).unwrap(),
            AppendResult::Appended(_)
        ));
        let mismatch = Entry {
            entry_id: "bad".into(),
            replay_token: "bad-token".into(),
            reconciliation_fingerprint: Some("b".repeat(64)),
            reconciles_entry_id: Some(pending.entry_id.clone()),
            ..entry(
                "unused",
                "unused-token",
                Kind::ReconciliationAdjustment,
                FactGrade::Reconciled,
            )
        };
        assert_eq!(ledger.append(&mismatch).unwrap(), AppendResult::Conflict);
        let adjustment = Entry {
            entry_id: "adjustment".into(),
            replay_token: "adjustment-token".into(),
            actual_model_id: Some("provider/actual".into()),
            input_tokens: Some(12),
            output_tokens: Some(8),
            charge_micros: Some(37),
            adjustment_micros: Some(-3),
            currency_code: Some("USD".into()),
            cost_source: Some("PROVIDER_RESPONSE".into()),
            reconciliation_fingerprint: Some("a".repeat(64)),
            reconciles_entry_id: Some(pending.entry_id.clone()),
            ..entry(
                "unused",
                "unused-token",
                Kind::ReconciliationAdjustment,
                FactGrade::Reconciled,
            )
        };
        assert!(matches!(
            ledger.append(&adjustment).unwrap(),
            AppendResult::Appended(_)
        ));
        let model = ledger.read_model_for_execution("execution").unwrap();
        assert!(model.pending_stream_entries.is_empty());
        assert_eq!(
            model.requested_model_ids,
            BTreeSet::from(["provider/requested".into()])
        );
        assert_eq!(
            model.actual_model_ids,
            BTreeSet::from(["provider/actual".into()])
        );
        assert_eq!(
            (
                model.input_tokens,
                model.output_tokens,
                model.charge_micros,
                model.reconciliation_adjustment_micros
            ),
            (12, 8, 37, -3)
        );
    }

    #[test]
    fn budget_and_cross_platform_origin_are_read_without_mutating_source_facts() {
        let root = tempdir().unwrap();
        let ledger = Ledger::open(root.path()).unwrap();
        let reserve = Entry {
            budget_micros: Some(100),
            currency_code: Some("USD".into()),
            source: Source::CrossPlatformImport,
            ..entry(
                "reserve",
                "reserve-token",
                Kind::BudgetReservation,
                FactGrade::Estimated,
            )
        };
        let release = Entry {
            budget_micros: Some(40),
            currency_code: Some("USD".into()),
            ..entry(
                "release",
                "release-token",
                Kind::BudgetRelease,
                FactGrade::Reconciled,
            )
        };
        ledger.append(&reserve).unwrap();
        ledger.append(&release).unwrap();
        let model = ledger.read_model_for_execution("execution").unwrap();
        assert_eq!(
            (model.reserved_budget_micros, model.released_budget_micros),
            (100, 40)
        );
        assert_eq!(
            model
                .entries
                .iter()
                .find(|entry| entry.entry_id == "reserve")
                .unwrap()
                .source,
            Source::CrossPlatformImport
        );
    }

    #[test]
    fn schema_whitelist_excludes_content_secrets_locations_attachments_and_mutation_paths() {
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
            "payload",
        ];
        assert!(ledger
            .column_names()
            .iter()
            .all(|name| !forbidden.iter().any(|word| name.contains(word))));
        assert_eq!(
            ledger.column_names().into_iter().collect::<BTreeSet<_>>(),
            BTreeSet::from([
                "entry_id".into(),
                "replay_token".into(),
                "execution_id".into(),
                "conversation_id".into(),
                "branch_leaf_message_id".into(),
                "invocation_id".into(),
                "attempt_id".into(),
                "kind".into(),
                "fact_grade".into(),
                "requested_model_id".into(),
                "actual_model_id".into(),
                "input_tokens".into(),
                "output_tokens".into(),
                "cached_input_tokens".into(),
                "charge_micros".into(),
                "budget_micros".into(),
                "adjustment_micros".into(),
                "currency_code".into(),
                "cost_source".into(),
                "reconciliation_fingerprint".into(),
                "reconciles_entry_id".into(),
                "source".into(),
                "occurred_at_ms".into(),
            ])
        );
        assert_eq!(PROTOCOL, "usage-ledger-v1");
    }
}
