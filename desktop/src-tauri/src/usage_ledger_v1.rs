//! P0 local-only, append-only Usage Ledger. It owns neither IPC nor provider execution.

use rusqlite::{params, Connection, OptionalExtension};
use serde::{Serialize, Deserialize};
use std::{collections::BTreeSet, path::Path};

pub const PROTOCOL: &str = "usage-ledger-v1";
const RECOVERY_CONFLICT: &str = "Recovery receipt conflict; original facts preserved";

fn has_service_identity(metadata: &crate::chat_call_accounting_v1::Metadata) -> bool {
    metadata.service_id.is_some() || metadata.service_engine_id.is_some()
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
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

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
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

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
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
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
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
    /// Backup validation must not migrate, recover, or rewrite the selected snapshot.
    pub fn validate_recovery_snapshot(path: &Path) -> Result<(),String> {
        let connection=Connection::open_with_flags(path,rusqlite::OpenFlags::SQLITE_OPEN_READ_ONLY)
            .map_err(|_|"Recovery snapshot unavailable")?;
        let ledger=Self {connection};
        let version:i64=ledger.connection.pragma_query_value(None,"user_version",|r|r.get(0)).map_err(|_|"Snapshot version unavailable")?;
        if version>6 {return Err("Usage Ledger snapshot schema is newer; original database preserved".into());}
        if version>=2 {ledger.validate_stored_call_metadata()?;}
        if version>=3 {ledger.recovery_receipts()?;}
        if version>=4 {ledger.retrieval_progress()?;}
        if version>=5 {ledger.validate_call_outcomes()?;}
        Ok(())
    }
    /// Inspect every persisted metadata row through the same typed and registration rules.
    /// Selected backup snapshots are read-only: no migration, repair, or route backfill.
    fn validate_stored_call_metadata(&self)->Result<(),String> {
        let mut statement=self.connection.prepare("SELECT entry_id FROM usage_ledger_call_metadata ORDER BY entry_id")
            .map_err(|_|"Stored call metadata unavailable")?;
        let ids=statement.query_map([],|row|row.get::<_,String>(0)).map_err(|_|"Stored call metadata unavailable")?;
        for id in ids {
            let id=id.map_err(|_|"Stored call metadata invalid")?;
            let metadata=self.call_metadata(&id)?.ok_or("Stored call metadata missing")?;
            let entry=self.entry(&id)?.ok_or("Stored call metadata entry missing")?;
            // Historical quantities keep their existing read semantics; this gate validates metadata.
            self.validate_registered_child_role(&entry,&metadata)?;
        }
        Ok(())
    }

    pub fn open(root: &Path) -> Result<Self, String> {
        std::fs::create_dir_all(root)
            .map_err(|_| "Usage Ledger directory unavailable".to_owned())?;
        let connection = Connection::open(root.join("usage-ledger-v1.sqlite3"))
            .map_err(|_| "Usage Ledger database unavailable".to_owned())?;
        connection.busy_timeout(std::time::Duration::from_secs(5))
            .map_err(|_| "Usage Ledger busy policy unavailable".to_owned())?;
        let version: i64 = connection.pragma_query_value(None, "user_version", |row| row.get(0))
            .map_err(|_| "Usage Ledger schema version unavailable".to_owned())?;
        if version > 6 { return Err("Usage Ledger schema is newer; original database preserved".into()); }
        connection.execute_batch("CREATE TABLE IF NOT EXISTS usage_ledger_entries (entry_id TEXT PRIMARY KEY NOT NULL, replay_token TEXT UNIQUE NOT NULL, execution_id TEXT NOT NULL, conversation_id TEXT NOT NULL, branch_leaf_message_id TEXT NOT NULL, invocation_id TEXT NOT NULL, attempt_id TEXT NOT NULL, kind TEXT NOT NULL, fact_grade TEXT NOT NULL, requested_model_id TEXT NOT NULL, actual_model_id TEXT, input_tokens INTEGER, output_tokens INTEGER, cached_input_tokens INTEGER, charge_micros INTEGER, budget_micros INTEGER, adjustment_micros INTEGER, currency_code TEXT, cost_source TEXT, reconciliation_fingerprint TEXT, reconciles_entry_id TEXT, source TEXT NOT NULL, occurred_at_ms INTEGER NOT NULL); CREATE INDEX IF NOT EXISTS usage_ledger_execution ON usage_ledger_entries(execution_id); CREATE INDEX IF NOT EXISTS usage_ledger_conversation ON usage_ledger_entries(conversation_id); CREATE INDEX IF NOT EXISTS usage_ledger_reconciles ON usage_ledger_entries(reconciles_entry_id); CREATE INDEX IF NOT EXISTS usage_ledger_fingerprint ON usage_ledger_entries(reconciliation_fingerprint); ")
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
        connection.execute_batch("PRAGMA foreign_keys=ON;")
            .map_err(|_| "Usage Ledger foreign key policy unavailable".to_owned())?;
        if version < 2 { connection.execute_batch("BEGIN IMMEDIATE;
            CREATE TABLE IF NOT EXISTS usage_ledger_call_metadata (
                entry_id TEXT PRIMARY KEY NOT NULL REFERENCES usage_ledger_entries(entry_id),
                metadata_json TEXT NOT NULL);
            PRAGMA user_version=2; COMMIT;")
            .map_err(|_| "Usage Ledger call-metadata migration failed".to_owned())?; }
        connection.prepare("SELECT entry_id,metadata_json FROM usage_ledger_call_metadata LIMIT 0")
            .map_err(|_| "Usage Ledger call metadata schema invalid; original database preserved".to_owned())?;
        if version < 3 { connection.execute_batch("BEGIN IMMEDIATE;
            CREATE TABLE IF NOT EXISTS usage_ledger_call_recovery (
                entry_id TEXT PRIMARY KEY NOT NULL,entry_json TEXT NOT NULL,metadata_json TEXT NOT NULL);
            PRAGMA user_version=3; COMMIT;")
            .map_err(|_| "Usage Ledger recovery migration failed".to_owned())?; }
        connection.prepare("SELECT entry_id,entry_json,metadata_json FROM usage_ledger_call_recovery LIMIT 0")
            .map_err(|_| "Usage Ledger recovery schema invalid; original database preserved".to_owned())?;
        if version < 4 { connection.execute_batch("BEGIN IMMEDIATE;
            CREATE TABLE usage_ledger_retrieval_progress (
                pending_id TEXT PRIMARY KEY NOT NULL REFERENCES usage_ledger_entries(entry_id),
                entry_id TEXT UNIQUE NOT NULL,entry_json TEXT NOT NULL,metadata_json TEXT NOT NULL,
                interrupted INTEGER NOT NULL DEFAULT 0 CHECK(interrupted IN (0,1)));
            PRAGMA user_version=4; COMMIT;")
            .map_err(|_| "Usage Ledger progress migration failed".to_owned())?; }
        connection.prepare("SELECT pending_id,entry_id,entry_json,metadata_json,interrupted FROM usage_ledger_retrieval_progress LIMIT 0")
            .map_err(|_| "Usage Ledger progress schema invalid; original database preserved".to_owned())?;
        if version < 5 { connection.execute_batch("BEGIN IMMEDIATE;
            CREATE TABLE usage_ledger_call_outcomes (
                pending_id TEXT PRIMARY KEY NOT NULL REFERENCES usage_ledger_entries(entry_id),
                outcome_json TEXT NOT NULL);
            PRAGMA user_version=5; COMMIT;")
            .map_err(|_| "Usage Ledger call-outcome migration failed".to_owned())?; }
        connection.prepare("SELECT pending_id,outcome_json FROM usage_ledger_call_outcomes LIMIT 0")
            .map_err(|_| "Usage Ledger call-outcome schema invalid; original database preserved".to_owned())?;
        // Version 6 protects the optional frozen route JSON; historical rows stay byte-identical.
        if version < 6 { connection.execute_batch("BEGIN IMMEDIATE; PRAGMA user_version=6; COMMIT;")
            .map_err(|_| "Usage Ledger frozen-route migration failed".to_owned())?; }
        let ledger=Self { connection };
        ledger.recovery_receipts()?;
        ledger.retrieval_progress()?;
        // Local retry only. A blocked write retains the durable receipt and never sends HTTP.
        if let Err(error)=ledger.recover_calls() {
            if error==RECOVERY_CONFLICT {return Err(error);}
        }
        Ok(ledger)
    }

    fn validate_outcome_pending(&self,pending:&Entry)->Result<(),String> {
        valid_entry(pending)?;
        if pending.kind!=Kind::StreamPending || pending.source!=Source::DesktopLocal
            || self.entry(&pending.entry_id)?.as_ref()!=Some(pending)
            || self.call_metadata(&pending.entry_id)?.is_none_or(|m|(!crate::chat_call_accounting_v1::is_model_child_role(&m.role) && m.role!="ANSWER")||has_service_identity(&m)) {
            return Err("Local call outcome pending identity invalid".into());
        }
        Ok(())
    }

    /// Append an observed execution result without changing any quantity or historical metadata.
    pub fn append_call_outcome(&self,pending:&Entry,outcome:&crate::chat_call_accounting_v1::CallOutcome)->Result<(),String> {
        use crate::chat_call_accounting_v1::CallOutcomeOrigin;
        self.validate_outcome_pending(pending)?;outcome.validate()?;
        let tx=self.connection.unchecked_transaction().map_err(|_|"Call outcome transaction unavailable")?;
        if outcome.origin==CallOutcomeOrigin::CancelledBeforeDispatch {
            let received:bool=self.connection.query_row("SELECT EXISTS(SELECT 1 FROM usage_ledger_entries WHERE reconciles_entry_id=?1 AND kind='FINAL_MEASURED') OR EXISTS(SELECT 1 FROM usage_ledger_retrieval_progress WHERE pending_id=?1)",[&pending.entry_id],|r|r.get(0)).map_err(|_|"Call dispatch facts unavailable")?;
            if received {return Err("Received facts cannot be marked undispatched".into());}
        }
        let encoded=serde_json::to_string(outcome).map_err(|_|"Call outcome invalid")?;
        self.connection.execute("INSERT OR IGNORE INTO usage_ledger_call_outcomes(pending_id,outcome_json) VALUES(?1,?2)",params![pending.entry_id,encoded]).map_err(|_|"Call outcome write failed")?;
        let stored:String=self.connection.query_row("SELECT outcome_json FROM usage_ledger_call_outcomes WHERE pending_id=?1",[&pending.entry_id],|r|r.get(0)).map_err(|_|"Call outcome read failed")?;
        let original:crate::chat_call_accounting_v1::CallOutcome=serde_json::from_str(&stored).map_err(|_|"Stored call outcome invalid")?;original.validate()?;
        if original!=*outcome {return Err("Call outcome conflict; original facts preserved".into());}
        tx.commit().map_err(|_|"Call outcome commit failed".into())
    }

    pub fn call_outcome(&self,entry:&Entry)->Result<Option<crate::chat_call_accounting_v1::CallOutcome>,String> {
        use crate::chat_call_accounting_v1::CallOutcomeOrigin;
        if !matches!(entry.kind,Kind::StreamPending|Kind::FinalMeasured) {return Ok(None);}
        let id=entry.reconciles_entry_id.as_deref().unwrap_or(&entry.entry_id);
        let encoded:Option<String>=self.connection.query_row("SELECT outcome_json FROM usage_ledger_call_outcomes WHERE pending_id=?1",[id],|r|r.get(0)).optional().map_err(|_|"Call outcome unavailable")?;
        let Some(encoded)=encoded else{return Ok(None);};
        let pending=self.entry(id)?.ok_or("Call outcome pending missing")?;self.validate_outcome_pending(&pending)?;
        if entry.execution_id!=pending.execution_id || entry.conversation_id!=pending.conversation_id || entry.branch_leaf_message_id!=pending.branch_leaf_message_id
            || entry.invocation_id!=pending.invocation_id || entry.attempt_id!=pending.attempt_id || entry.requested_model_id!=pending.requested_model_id || entry.source!=pending.source {
            return Err("Call outcome does not match requested call".into());
        }
        let outcome:crate::chat_call_accounting_v1::CallOutcome=serde_json::from_str(&encoded).map_err(|_|"Call outcome invalid")?;outcome.validate()?;
        if outcome.origin==CallOutcomeOrigin::CancelledBeforeDispatch {
            let received:bool=self.connection.query_row("SELECT EXISTS(SELECT 1 FROM usage_ledger_entries WHERE reconciles_entry_id=?1 AND kind='FINAL_MEASURED') OR EXISTS(SELECT 1 FROM usage_ledger_retrieval_progress WHERE pending_id=?1)",[id],|r|r.get(0)).map_err(|_|"Call outcome dispatch facts unavailable")?;
            if entry.kind!=Kind::StreamPending || received {return Err("Undispatched call cannot have received facts".into());}
        }
        Ok(Some(outcome))
    }

    /// Full snapshot validation belongs to backup/restore; ordinary lookup validates only its own call.
    fn validate_call_outcomes(&self)->Result<(),String> {
        use crate::chat_call_accounting_v1::CallOutcomeOrigin;
        let ids=self.connection.prepare("SELECT pending_id FROM usage_ledger_call_outcomes ORDER BY pending_id")
            .and_then(|mut s|s.query_map([],|r|r.get::<_,String>(0))?.collect::<Result<Vec<_>,_>>()).map_err(|_|"Call outcome snapshot unavailable")?;
        let receipts=self.recovery_receipts()?;
        for id in ids {
            let pending=self.entry(&id)?.ok_or("Call outcome snapshot pending missing")?;
            let outcome=self.call_outcome(&pending)?.ok_or("Call outcome snapshot missing")?;
            if outcome.origin==CallOutcomeOrigin::CancelledBeforeDispatch {
                let measured:bool=self.connection.query_row("SELECT EXISTS(SELECT 1 FROM usage_ledger_entries WHERE reconciles_entry_id=?1 AND kind='FINAL_MEASURED') OR EXISTS(SELECT 1 FROM usage_ledger_retrieval_progress WHERE pending_id=?1)",[&id],|r|r.get(0)).map_err(|_|"Call outcome snapshot dispatch facts unavailable")?;
                if measured || receipts.iter().any(|(entry,_)|entry.reconciles_entry_id.as_deref()==Some(id.as_str())) {return Err("Undispatched outcome conflicts with received snapshot facts".into());}
            }
        }
        Ok(())
    }

    /// Durable provider snapshot for an unfinished retrieval. Never participates in settled totals.
    pub fn checkpoint_retrieval(&self, entry:&Entry, metadata:&crate::chat_call_accounting_v1::Metadata) -> Result<(),String> {
        self.validate_retrieval_progress(entry,metadata)?;
        let pending=entry.reconciles_entry_id.as_ref().ok_or("Missing retrieval pending identity")?;
        let tx=self.connection.unchecked_transaction().map_err(|_|"Progress transaction unavailable")?;
        if self.entry(&entry.entry_id)?.is_some() {return Err("Retrieval already accounted; checkpoint rejected".into());}
        let existing=self.retrieval_progress()?.into_iter().find(|(e,_,_)|e.entry_id==entry.entry_id);
        let (mut merged,mut meta)=(entry.clone(),metadata.clone());
        if let Some((previous,previous_meta,interrupted))=existing {
            if interrupted {return Err("Interrupted retrieval cannot resume".into());}
            if previous.currency_code.as_ref().zip(entry.currency_code.as_ref()).is_some_and(|(old,new)|old!=new) {
                return Err("Retrieval progress currency changed".into());
            }
            let mut identity=previous.clone();
            identity.actual_model_id=entry.actual_model_id.clone();identity.input_tokens=entry.input_tokens;
            identity.output_tokens=entry.output_tokens;identity.cached_input_tokens=entry.cached_input_tokens;
            identity.charge_micros=entry.charge_micros;identity.currency_code=entry.currency_code.clone();identity.cost_source=entry.cost_source.clone();
            let mut meta_identity=previous_meta.clone();meta_identity.reasoning_tokens=metadata.reasoning_tokens;
            if identity!=*entry || meta_identity!=*metadata {return Err("Retrieval progress identity changed".into());}
            merged.actual_model_id=merged.actual_model_id.or(previous.actual_model_id);
            merged.input_tokens=merged.input_tokens.or(previous.input_tokens);merged.output_tokens=merged.output_tokens.or(previous.output_tokens);
            merged.cached_input_tokens=merged.cached_input_tokens.or(previous.cached_input_tokens);
            if merged.charge_micros.is_none() {merged.charge_micros=previous.charge_micros;merged.currency_code=previous.currency_code;merged.cost_source=previous.cost_source;}
            meta.reasoning_tokens=meta.reasoning_tokens.or(previous_meta.reasoning_tokens);
        }
        self.validate_retrieval_progress(&merged,&meta)?;
        self.connection.execute("INSERT INTO usage_ledger_retrieval_progress(pending_id,entry_id,entry_json,metadata_json,interrupted) VALUES(?1,?2,?3,?4,0)
            ON CONFLICT(pending_id) DO UPDATE SET entry_json=excluded.entry_json,metadata_json=excluded.metadata_json",
            params![pending,merged.entry_id,serde_json::to_string(&merged).map_err(|_|"Progress entry invalid")?,serde_json::to_string(&meta).map_err(|_|"Progress metadata invalid")?])
            .map_err(|_|"Retrieval progress write failed")?;
        tx.commit().map_err(|_|"Retrieval progress commit failed".into())
    }

    fn validate_retrieval_progress(&self,e:&Entry,m:&crate::chat_call_accounting_v1::Metadata)->Result<(),String> {
        valid_entry(e)?;m.validate()?;
        if e.kind!=Kind::FinalMeasured || e.fact_grade!=FactGrade::ProviderReported || e.source!=Source::DesktopLocal
            || !crate::chat_call_accounting_v1::is_model_child_role(&m.role) || has_service_identity(m) || m.price_version.is_some()
            || e.budget_micros.is_some() || e.adjustment_micros.is_some()
            || (e.actual_model_id.is_none() && e.input_tokens.is_none() && e.output_tokens.is_none() && e.cached_input_tokens.is_none() && e.charge_micros.is_none() && m.reasoning_tokens.is_none())
            || (e.charge_micros.is_some() && e.cost_source.as_deref()!=Some("PROVIDER_RESPONSE"))
            || (e.charge_micros.is_none() && (e.cost_source.is_some() || e.currency_code.is_some())) {
            return Err("Invalid provider retrieval progress".into());
        }
        let pending=self.entry(e.reconciles_entry_id.as_deref().ok_or("Retrieval pending identity missing")?)?.ok_or("Retrieval pending missing")?;
        if pending.kind!=Kind::StreamPending || pending.source!=Source::DesktopLocal || pending.execution_id!=e.execution_id
            || pending.conversation_id!=e.conversation_id || pending.branch_leaf_message_id!=e.branch_leaf_message_id
            || pending.invocation_id!=e.invocation_id || pending.attempt_id!=e.attempt_id || pending.requested_model_id!=e.requested_model_id
            || pending.reconciliation_fingerprint!=e.reconciliation_fingerprint || pending.occurred_at_ms!=e.occurred_at_ms
            || self.call_metadata(&pending.entry_id)?.is_none_or(|original|original.role!=m.role || original.route!=m.route || has_service_identity(&original)
                || original.model_display_name!=m.model_display_name || original.context_window_tokens!=m.context_window_tokens || original.context_source_count!=m.context_source_count) {
            return Err("Retrieval progress does not match pending call".into());
        }
        Ok(())
    }

    pub fn retrieval_progress(&self)->Result<Vec<(Entry,crate::chat_call_accounting_v1::Metadata,bool)>,String> {
        let rows=self.connection.prepare("SELECT pending_id,entry_id,entry_json,metadata_json,interrupted FROM usage_ledger_retrieval_progress ORDER BY pending_id")
            .and_then(|mut s|s.query_map([],|r|Ok((r.get::<_,String>(0)?,r.get::<_,String>(1)?,r.get::<_,String>(2)?,r.get::<_,String>(3)?,r.get::<_,i64>(4)?)))?.collect::<Result<Vec<_>,_>>())
            .map_err(|_|"Retrieval progress unavailable")?;
        let mut result=Vec::new();
        for (pending,id,encoded,meta,state) in rows {
            let e:Entry=serde_json::from_str(&encoded).map_err(|_|"Retrieval progress entry invalid")?;
            let m:crate::chat_call_accounting_v1::Metadata=serde_json::from_str(&meta).map_err(|_|"Retrieval progress metadata invalid")?;
            if e.entry_id!=id || e.reconciles_entry_id.as_deref()!=Some(pending.as_str()) || ![0,1].contains(&state) {return Err("Retrieval progress identity invalid".into());}
            self.validate_retrieval_progress(&e,&m)?;
            if state==1 && (self.entry(&id)?.is_some_and(|existing|existing!=e) || self.call_metadata(&id)?.is_some_and(|existing|existing!=m)) {return Err(RECOVERY_CONFLICT.into());}
            result.push((e,m,state==1));
        }
        Ok(result)
    }

    /// Called only after the owning Attempt has been recovered to UNKNOWN/PROCESS_INTERRUPTED.
    pub fn recover_interrupted_retrieval(&self,id:&str)->Result<(),String> {
        let (e,m,_)=self.retrieval_progress()?.into_iter().find(|(e,_,_)|e.entry_id==id).ok_or("Retrieval progress missing")?;
        let pending=self.entry(e.reconciles_entry_id.as_deref().ok_or("Interrupted pending missing")?)?.ok_or("Interrupted pending missing")?;
        if self.call_outcome(&pending)?.is_some_and(|outcome|outcome.origin!=crate::chat_call_accounting_v1::CallOutcomeOrigin::RecoveredInterruption) {
            return Err("Observed execution result cannot be replaced by parent interruption".into());
        }
        self.connection.execute("UPDATE usage_ledger_retrieval_progress SET interrupted=1 WHERE entry_id=?1",[id]).map_err(|_|"Retrieval interruption write failed")?;
        if self.append_recoverable_call(&e,&m)?==AppendResult::Conflict {return Err(RECOVERY_CONFLICT.into());}
        self.append_call_outcome(&pending,&crate::chat_call_accounting_v1::CallOutcome::interrupted())?;
        Ok(())
    }

    pub fn retrieval_was_interrupted(&self,e:&Entry)->Result<bool,String> {
        let row:Option<(String,i64)>=self.connection.query_row(
            "SELECT entry_json,interrupted FROM usage_ledger_retrieval_progress WHERE entry_id=?1",[&e.entry_id],
            |r|Ok((r.get(0)?,r.get(1)?))).optional().map_err(|_|"Retrieval outcome unavailable")?;
        match row {
            Some((encoded,1))=>Ok(serde_json::from_str::<Entry>(&encoded).map_err(|_|"Retrieval outcome invalid")?==*e),
            Some((_,0))|None=>Ok(false),
            _=>Err("Retrieval outcome invalid".into()),
        }
    }

    pub fn finish_live_retrieval(&self,pending:&str)->Result<(),String> {
        self.connection.execute("DELETE FROM usage_ledger_retrieval_progress WHERE pending_id=?1 AND interrupted=0",[pending]).map_err(|_|"Retrieval progress cleanup pending")?;
        Ok(())
    }

    /// Preserve received measurement before attempting its atomic final accounting.
    pub fn append_recoverable_call(&self, entry: &Entry, metadata: &crate::chat_call_accounting_v1::Metadata)
        -> Result<AppendResult,String> {
        valid_entry(entry)?;metadata.validate()?;
        if entry.kind!=Kind::FinalMeasured || !crate::chat_call_accounting_v1::is_model_child_role(&metadata.role) || has_service_identity(metadata) {
            return Err("Recovery receipt is restricted to measured retrieval calls".into());
        }
        self.validate_registered_child_role(entry,metadata)?;
        let encoded=serde_json::to_string(entry).map_err(|_|"Recovery entry invalid")?;
        let meta=serde_json::to_string(metadata).map_err(|_|"Recovery metadata invalid")?;
        self.connection.execute("INSERT OR IGNORE INTO usage_ledger_call_recovery(entry_id,entry_json,metadata_json) VALUES(?1,?2,?3)",
            params![entry.entry_id,encoded,meta]).map_err(|_|"Recovery receipt write failed")?;
        let stored:(String,String)=self.connection.query_row("SELECT entry_json,metadata_json FROM usage_ledger_call_recovery WHERE entry_id=?1",
            [&entry.entry_id],|row|Ok((row.get(0)?,row.get(1)?))).map_err(|_|"Recovery receipt read failed")?;
        if stored!=(encoded,meta) {return Ok(AppendResult::Conflict);}
        let result=self.append_call(entry,metadata)?;
        if result!=AppendResult::Conflict {
            self.connection.execute("DELETE FROM usage_ledger_call_recovery WHERE entry_id=?1",[&entry.entry_id])
                .map_err(|_|"Recovery receipt cleanup pending")?;
        }
        Ok(result)
    }

    pub fn recover_calls(&self) -> Result<usize,String> {
        let receipts=self.recovery_receipts()?;
        let mut recovered=0;
        for (entry,metadata) in receipts {
            if self.append_recoverable_call(&entry,&metadata)?==AppendResult::Conflict {return Err(RECOVERY_CONFLICT.into());}
            recovered+=1;
        }
        Ok(recovered)
    }

    fn recovery_receipts(&self) -> Result<Vec<(Entry,crate::chat_call_accounting_v1::Metadata)>,String> {
        let receipts=self.connection.prepare("SELECT entry_id,entry_json,metadata_json FROM usage_ledger_call_recovery ORDER BY entry_id")
            .and_then(|mut statement|statement.query_map([],|row|Ok((row.get::<_,String>(0)?,row.get::<_,String>(1)?,row.get::<_,String>(2)?)))?
                .collect::<Result<Vec<_>,_>>()).map_err(|_|"Recovery receipts unavailable")?;
        let mut validated=Vec::new();
        for (id,encoded,meta) in receipts {
            let entry:Entry=serde_json::from_str(&encoded).map_err(|_|"Recovery entry invalid")?;
            let metadata:crate::chat_call_accounting_v1::Metadata=serde_json::from_str(&meta).map_err(|_|"Recovery metadata invalid")?;
            if entry.entry_id!=id {return Err("Recovery receipt identity invalid".into());}
            valid_entry(&entry)?;metadata.validate()?;
            if entry.kind!=Kind::FinalMeasured || !crate::chat_call_accounting_v1::is_model_child_role(&metadata.role) || has_service_identity(&metadata) {
                return Err("Recovery receipt role invalid".into());
            }
            self.validate_registered_child_role(&entry,&metadata)?;
            if self.entry(&id)?.is_some_and(|original|original!=entry)
                || self.call_metadata(&id)?.is_some_and(|original|original!=metadata) {
                return Err(RECOVERY_CONFLICT.into());
            }
            validated.push((entry,metadata));
        }
        Ok(validated)
    }

    /// Quantitative entry and whitelisted call metadata commit atomically.
    fn validate_registered_child_role(&self,entry:&Entry,metadata:&crate::chat_call_accounting_v1::Metadata)->Result<(),String> {
        if let Some(pending)=entry.reconciles_entry_id.as_deref() {
            if let Some(original)=self.call_metadata(pending)? {
                if original.route!=metadata.route {return Err("Measured call route does not match original registration".into());}
                if (crate::chat_call_accounting_v1::is_model_child_role(&original.role) || crate::chat_call_accounting_v1::is_model_child_role(&metadata.role)) && original.role!=metadata.role {
                    return Err("Measured child role does not match original registration".into());
                }
            } else if metadata.role=="MATERIAL_READ" {return Err("Material read registration metadata missing".into());}
        }
        Ok(())
    }

    pub fn append_call(&self, entry: &Entry, metadata: &crate::chat_call_accounting_v1::Metadata)
        -> Result<AppendResult, String> {
        metadata.validate()?;
        if has_service_identity(metadata) { return Err("Search-service accounting requires its dedicated receiver owner".into()); }
        self.validate_registered_child_role(entry,metadata)?;
        let transaction = self.connection.unchecked_transaction()
            .map_err(|_| "Usage Ledger call transaction unavailable".to_owned())?;
        let result = self.append(entry)?;
        if result == AppendResult::Conflict { return Ok(result); }
        let encoded = serde_json::to_string(metadata).map_err(|_| "Usage Ledger call metadata invalid".to_owned())?;
        let existing: Option<String> = self.connection.query_row(
            "SELECT metadata_json FROM usage_ledger_call_metadata WHERE entry_id=?1", [&entry.entry_id],
            |row| row.get(0)).optional().map_err(|_| "Usage Ledger call metadata read failed".to_owned())?;
        if existing.as_ref().is_some_and(|previous| previous != &encoded) { return Ok(AppendResult::Conflict); }
        self.connection.execute("INSERT OR IGNORE INTO usage_ledger_call_metadata(entry_id,metadata_json) VALUES(?1,?2)",
            params![entry.entry_id, encoded]).map_err(|_| "Usage Ledger call metadata write failed".to_owned())?;
        transaction.commit().map_err(|_| "Usage Ledger call commit failed".to_owned())?;
        Ok(result)
    }

    pub fn call_metadata(&self, entry_id: &str) -> Result<Option<crate::chat_call_accounting_v1::Metadata>, String> {
        let encoded: Option<String> = self.connection.query_row(
            "SELECT metadata_json FROM usage_ledger_call_metadata WHERE entry_id=?1", [entry_id],
            |row| row.get(0)).optional().map_err(|_| "Usage Ledger call metadata read failed".to_owned())?;
        encoded.map(|value| {
            let metadata: crate::chat_call_accounting_v1::Metadata = serde_json::from_str(&value)
                .map_err(|_| "Usage Ledger call metadata invalid".to_owned())?;
            metadata.validate()?; Ok(metadata)
        }).transpose()
    }

    pub fn entry(&self, entry_id: &str) -> Result<Option<Entry>, String> { self.by_id(entry_id) }

    pub fn append(&self, entry: &Entry) -> Result<AppendResult, String> {
        valid_entry(entry)?;
        if entry.kind==Kind::FinalMeasured {
            if let Some(pending)=entry.reconciles_entry_id.as_deref().map(|id|self.entry(id)).transpose()?.flatten() {
                if self.call_outcome(&pending)?.is_some_and(|o|o.origin==crate::chat_call_accounting_v1::CallOutcomeOrigin::CancelledBeforeDispatch) {
                    return Err("Undispatched call cannot receive measured settlement".into());
                }
            }
        }
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

    /// Read-only UI facts: measured calls and named calls still lacking a settlement.
    pub fn display_entries(&self) -> Result<Vec<Entry>, String> {
        let entries=self.all_entries()?;
        let resolved=entries.iter().filter(|entry|entry.kind==Kind::FinalMeasured)
            .filter_map(|entry|entry.reconciles_entry_id.clone()).collect::<BTreeSet<_>>();
        let mut display=Vec::new();
        for entry in entries {
            if entry.kind==Kind::FinalMeasured || (entry.kind==Kind::StreamPending
                && !resolved.contains(&entry.entry_id) && self.call_metadata(&entry.entry_id)?.is_some()) {
                if entry.kind==Kind::StreamPending && self.call_outcome(&entry)?.is_some_and(|o|o.origin==crate::chat_call_accounting_v1::CallOutcomeOrigin::CancelledBeforeDispatch) {continue;}
                display.push(entry);
            }
        }
        Ok(display)
    }

    pub fn read_model_for_execution(&self, execution_id: &str) -> Result<ReadModel, String> {
        Ok(read_model(self.entries_for_execution(execution_id)?))
    }

    pub(crate) fn by_id(&self, entry_id: &str) -> Result<Option<Entry>, String> {
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
            .is_some_and(|value| !reported_model_id(value))
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
fn reported_model_id(value: &str) -> bool {
    // Returned identity is a bounded provider fact, never an execution route.
    // Keep the historical ASCII vocabulary readable while admitting exactly the
    // canonical values already accepted by the ordinary transport owner.
    model_id(value) || crate::desktop_ordinary_chat_v1::valid_reported_model_id(value)
        .is_some_and(|canonical| canonical == value)
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

    fn measured_model_call(id: &str) -> Entry {
        Entry { input_tokens:Some(5),output_tokens:Some(2),charge_micros:Some(4),
            currency_code:Some("USD".into()),cost_source:Some("PROVIDER_RESPONSE".into()),
            ..entry(id,id,Kind::FinalMeasured,FactGrade::ProviderReported) }
    }
    fn engine_only_metadata(role: &str) -> crate::chat_call_accounting_v1::Metadata {
        crate::chat_call_accounting_v1::Metadata {role:role.into(),model_display_name:"Synthetic model".into(),
            service_engine_id:Some("search_pro".into()),..Default::default()}
    }

    #[test]
    fn service_engine_without_receiver_cannot_enter_model_call_writes() {
        let root=tempdir().unwrap();let ledger=Ledger::open(root.path()).unwrap();
        for role in ["ANSWER","RETRIEVAL","BACKGROUND"] {
            let value=measured_model_call(role);
            assert!(ledger.append_call(&value,&engine_only_metadata(role)).is_err());
            assert!(ledger.entry(&value.entry_id).unwrap().is_none());
            assert!(ledger.call_metadata(&value.entry_id).unwrap().is_none());
        }
        assert!(ledger.all_entries().unwrap().is_empty());
    }

    #[test]
    fn service_engine_without_receiver_cannot_write_a_recovery_receipt() {
        let root=tempdir().unwrap();let ledger=Ledger::open(root.path()).unwrap();
        assert!(ledger.append_recoverable_call(&measured_model_call("orphan-engine"),&engine_only_metadata("RETRIEVAL")).is_err());
        assert!(ledger.all_entries().unwrap().is_empty());
        assert_eq!(ledger.connection.query_row("SELECT COUNT(*) FROM usage_ledger_call_recovery",[],|r|r.get::<_,i64>(0)).unwrap(),0);
    }

    #[test]
    fn cold_recovery_rejects_unowned_engine_without_mutating_pending_facts() {
        let root=tempdir().unwrap();let ledger=Ledger::open(root.path()).unwrap();
        let value=measured_model_call("old-unowned-recovery");
        let encoded=serde_json::to_string(&value).unwrap();
        let metadata=serde_json::to_string(&engine_only_metadata("RETRIEVAL")).unwrap();
        ledger.connection.execute("INSERT INTO usage_ledger_call_recovery VALUES(?1,?2,?3)",params![value.entry_id,encoded,metadata]).unwrap();
        drop(ledger);
        assert!(Ledger::open(root.path()).is_err());
        let raw=Connection::open(root.path().join("usage-ledger-v1.sqlite3")).unwrap();
        assert_eq!(raw.query_row("SELECT entry_json,metadata_json FROM usage_ledger_call_recovery",[],|r|Ok((r.get::<_,String>(0)?,r.get::<_,String>(1)?))).unwrap(),(encoded,metadata));
        assert_eq!(raw.query_row("SELECT COUNT(*) FROM usage_ledger_entries",[],|r|r.get::<_,i64>(0)).unwrap(),0);
    }

    #[test]
    fn historical_engine_only_metadata_remains_readable_and_unchanged() {
        let root=tempdir().unwrap();let ledger=Ledger::open(root.path()).unwrap();
        let value=measured_model_call("historical-engine-only");let metadata=engine_only_metadata("RETRIEVAL");
        ledger.append(&value).unwrap();
        // Seed an existing historical record, not a new admitted receiver call.
        let encoded=serde_json::to_string(&metadata).unwrap();
        ledger.connection.execute("INSERT INTO usage_ledger_call_metadata VALUES(?1,?2)",params![value.entry_id,encoded]).unwrap();
        drop(ledger);let reopened=Ledger::open(root.path()).unwrap();
        assert_eq!(reopened.entry(&value.entry_id).unwrap(),Some(value.clone()));
        assert_eq!(reopened.call_metadata(&value.entry_id).unwrap(),Some(metadata));
        assert_eq!(reopened.display_entries().unwrap(),vec![value]);
        assert_eq!(reopened.connection.query_row("SELECT metadata_json FROM usage_ledger_call_metadata",[],|r|r.get::<_,String>(0)).unwrap(),encoded);
    }

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
    fn reported_model_identity_survives_public_append_replay_and_cold_open() {
        let root=tempdir().unwrap();let ledger=Ledger::open(root.path()).unwrap();
        let models=["x".repeat(200),format!("{}xx","汉".repeat(66)),"reported model α".into(),"<reported&alias>".into(),"null".into()];
        let metadata=crate::chat_call_accounting_v1::Metadata{role:"ANSWER".into(),model_display_name:"Synthetic frozen request".into(),..Default::default()};
        let mut values=Vec::new();
        for (index,model) in models.into_iter().enumerate() {
            let id=format!("reported-model-{index}");let mut value=entry(&id,&id,Kind::FinalMeasured,FactGrade::ProviderReported);
            value.actual_model_id=Some(model);value.input_tokens=Some(3);value.output_tokens=Some(2);
            ledger.append_call(&value,&metadata).unwrap();ledger.append_call(&value,&metadata).unwrap();
            assert_eq!(ledger.entry(&id).unwrap(),Some(value.clone()));values.push(value);
        }
        drop(ledger);let reopened=Ledger::open(root.path()).unwrap();
        assert_eq!(reopened.all_entries().unwrap().len(),values.len());
        for value in values {assert_eq!(reopened.entry(&value.entry_id).unwrap(),Some(value.clone()));assert_eq!(reopened.call_metadata(&value.entry_id).unwrap(),Some(metadata.clone()));assert!(value.charge_micros.is_none());}
    }

    #[test]
    fn reported_model_identity_rejects_invalid_facts_without_relaxing_requested_routes() {
        let root=tempdir().unwrap();let ledger=Ledger::open(root.path()).unwrap();
        for model in ["".into()," ".into()," x ".into(),"bad\nmodel".into(),"bad\0model".into(),"汉".repeat(67)] {
            let mut value=entry("invalid-reported","invalid-reported",Kind::FinalMeasured,FactGrade::ProviderReported);value.actual_model_id=Some(model);
            assert!(ledger.append(&value).is_err());assert!(ledger.all_entries().unwrap().is_empty());
        }
        for requested in ["汉", "model with spaces", "<model>"] {
            let mut value=entry("invalid-route","invalid-route",Kind::FinalMeasured,FactGrade::ProviderReported);value.requested_model_id=requested.into();
            assert!(ledger.append(&value).is_err());assert!(ledger.all_entries().unwrap().is_empty());
        }
    }

    #[test]
    fn display_keeps_unknown_call_until_exact_reconciliation_without_mutating_physical_facts() {
        let root=tempdir().unwrap();let ledger=Ledger::open(root.path()).unwrap();
        let mut pending=entry("pending-answer","pending-answer",Kind::StreamPending,FactGrade::Estimated);
        pending.reconciliation_fingerprint=Some("a".repeat(64));
        let metadata=crate::chat_call_accounting_v1::Metadata{role:"ANSWER".into(),model_display_name:"Opus · RC".into(),..Default::default()};
        ledger.append_call(&pending,&metadata).unwrap();
        let mut unrelated=entry("other-pending","other-pending",Kind::StreamPending,FactGrade::Estimated);
        unrelated.reconciliation_fingerprint=Some("b".repeat(64));
        ledger.append(&unrelated).unwrap();
        assert_eq!(ledger.display_entries().unwrap(),vec![pending.clone()]);
        let mut settled=entry("settled-answer","settled-answer",Kind::FinalMeasured,FactGrade::ProviderReported);
        settled.invocation_id="different-legacy-invocation".into();settled.reconciles_entry_id=Some(pending.entry_id.clone());
        settled.input_tokens=Some(5);settled.output_tokens=Some(2);settled.charge_micros=Some(4);settled.currency_code=Some("CNY".into());
        settled.cost_source=Some("PROVIDER_RESPONSE".into());
        ledger.append_call(&settled,&metadata).unwrap();drop(ledger);
        let reopened=Ledger::open(root.path()).unwrap();assert_eq!(reopened.display_entries().unwrap(),vec![settled]);
        assert_eq!(reopened.all_entries().unwrap().len(),3);assert_eq!(reopened.entry(&pending.entry_id).unwrap(),Some(pending));
    }

    #[test]
    fn measured_retrieval_survives_final_write_fault_and_replays_only_local_accounting_after_reopen() {
        let root=tempdir().unwrap();let ledger=Ledger::open(root.path()).unwrap();
        let mut measured=entry("received-retrieval","received-retrieval",Kind::FinalMeasured,FactGrade::ProviderReported);
        measured.input_tokens=Some(5);measured.output_tokens=Some(2);measured.charge_micros=Some(4);
        measured.currency_code=Some("USD".into());measured.cost_source=Some("PROVIDER_RESPONSE".into());
        let metadata=crate::chat_call_accounting_v1::Metadata{role:"RETRIEVAL".into(),model_display_name:"GPT-6 Luna · OpenRouter".into(),..Default::default()};
        ledger.connection.execute_batch("CREATE TRIGGER reject_final BEFORE INSERT ON usage_ledger_entries BEGIN SELECT RAISE(ABORT,'synthetic final write'); END;").unwrap();
        assert!(ledger.append_recoverable_call(&measured,&metadata).is_err());assert!(ledger.entry(&measured.entry_id).unwrap().is_none());
        let receipt:(String,String)=ledger.connection.query_row("SELECT entry_json,metadata_json FROM usage_ledger_call_recovery",[],|row|Ok((row.get(0)?,row.get(1)?))).unwrap();
        assert_eq!(serde_json::from_str::<Entry>(&receipt.0).unwrap(),measured);drop(ledger);
        let blocked=Ledger::open(root.path()).unwrap();assert!(blocked.entry(&measured.entry_id).unwrap().is_none());
        blocked.connection.execute_batch("DROP TRIGGER reject_final;").unwrap();drop(blocked);
        let recovered=Ledger::open(root.path()).unwrap();assert_eq!(recovered.entry(&measured.entry_id).unwrap(),Some(measured.clone()));
        assert_eq!(recovered.call_metadata(&measured.entry_id).unwrap(),Some(metadata.clone()));
        // Simulate a crash after atomic final commit but before journal cleanup.
        recovered.connection.execute("INSERT INTO usage_ledger_call_recovery(entry_id,entry_json,metadata_json) VALUES(?1,?2,?3)",params![measured.entry_id,receipt.0,receipt.1]).unwrap();drop(recovered);
        let replayed=Ledger::open(root.path()).unwrap();assert_eq!(replayed.all_entries().unwrap(),vec![measured.clone()]);
        assert_eq!(replayed.recover_calls().unwrap(),0);
        let mut conflicting=measured.clone();conflicting.charge_micros=Some(7);
        replayed.connection.execute("INSERT INTO usage_ledger_call_recovery(entry_id,entry_json,metadata_json) VALUES(?1,?2,?3)",
            params![conflicting.entry_id,serde_json::to_string(&conflicting).unwrap(),serde_json::to_string(&metadata).unwrap()]).unwrap();
        drop(replayed);assert!(Ledger::open(root.path()).is_err());
        let preserved=Connection::open(root.path().join("usage-ledger-v1.sqlite3")).unwrap();
        assert_eq!(preserved.query_row("SELECT charge_micros FROM usage_ledger_entries",[],|row|row.get::<_,i64>(0)).unwrap(),4);
    }

    #[test]
    fn recovery_migration_preserves_schema_two_facts_and_rejects_unwhitelisted_receipts() {
        let root=tempdir().unwrap();let ledger=Ledger::open(root.path()).unwrap();
        let value=entry("old-measurement","old-measurement",Kind::FinalMeasured,FactGrade::ProviderReported);
        let metadata=crate::chat_call_accounting_v1::Metadata{role:"ANSWER".into(),model_display_name:"Synthetic original".into(),..Default::default()};
        ledger.append_call(&value,&metadata).unwrap();ledger.connection.execute_batch("DROP TABLE usage_ledger_call_outcomes;DROP TABLE usage_ledger_retrieval_progress;DROP TABLE usage_ledger_call_recovery;PRAGMA user_version=2;").unwrap();drop(ledger);
        let migrated=Ledger::open(root.path()).unwrap();assert_eq!(migrated.entry(&value.entry_id).unwrap(),Some(value.clone()));
        assert_eq!(migrated.call_metadata("old-measurement").unwrap(),Some(metadata.clone()));
        assert_eq!(migrated.connection.pragma_query_value::<i64,_>(None,"user_version",|row|row.get(0)).unwrap(),6);
        let mut invalid=serde_json::to_value(&value).unwrap();invalid["prompt"]=serde_json::json!("forbidden synthetic payload");
        migrated.connection.execute("INSERT INTO usage_ledger_call_recovery(entry_id,entry_json,metadata_json) VALUES('bad',?1,?2)",
            params![invalid.to_string(),serde_json::to_string(&metadata).unwrap()]).unwrap();
        assert!(migrated.recover_calls().is_err());assert_eq!(migrated.all_entries().unwrap().len(),1);
        drop(migrated);assert!(Ledger::open(root.path()).is_err());
    }


    #[test]
    fn call_metadata_rolls_back_failed_write_and_preserves_replay_after_reopen() {
        let root = tempdir().unwrap(); let ledger = Ledger::open(root.path()).unwrap();
        let value = entry("call-entry", "call-token", Kind::FinalMeasured, FactGrade::ProviderReported);
        let metadata = crate::chat_call_accounting_v1::Metadata { role:"RETRIEVAL".into(),
            model_display_name:"GPT-6 Luna · OpenRouter".into(), ..Default::default() };
        ledger.connection.execute_batch("CREATE TRIGGER reject_call_metadata BEFORE INSERT ON usage_ledger_call_metadata BEGIN SELECT RAISE(ABORT,'synthetic-only'); END;").unwrap();
        assert!(ledger.append_call(&value, &metadata).is_err()); assert!(ledger.entry(&value.entry_id).unwrap().is_none());
        ledger.connection.execute_batch("DROP TRIGGER reject_call_metadata;").unwrap();
        assert!(matches!(ledger.append_call(&value, &metadata).unwrap(), AppendResult::Appended(_)));
        drop(ledger); let reopened = Ledger::open(root.path()).unwrap();
        assert_eq!(reopened.call_metadata(&value.entry_id).unwrap(), Some(metadata.clone()));
        assert!(matches!(reopened.append_call(&value, &metadata).unwrap(), AppendResult::Replayed(_)));
        let changed = crate::chat_call_accounting_v1::Metadata { role:"ANSWER".into(), ..metadata.clone() };
        assert_eq!(reopened.append_call(&value, &changed).unwrap(), AppendResult::Conflict);
        assert_eq!(reopened.call_metadata(&value.entry_id).unwrap(), Some(metadata));
    }

    #[test]
    fn call_metadata_migration_preserves_v1_facts_and_refuses_future_schema() {
        let root = tempdir().unwrap(); let ledger = Ledger::open(root.path()).unwrap();
        let value = entry("legacy-entry", "legacy-token", Kind::FinalMeasured, FactGrade::ProviderReported);
        ledger.append(&value).unwrap();
        ledger.connection.execute_batch("DROP TABLE usage_ledger_call_outcomes;DROP TABLE usage_ledger_retrieval_progress;DROP TABLE usage_ledger_call_metadata; PRAGMA user_version=1;").unwrap();
        drop(ledger); let upgraded = Ledger::open(root.path()).unwrap();
        assert_eq!(upgraded.entry(&value.entry_id).unwrap(), Some(value.clone()));
        assert_eq!(upgraded.call_metadata(&value.entry_id).unwrap(), None);
        assert_eq!(upgraded.connection.pragma_query_value(None,"user_version",|row|row.get::<_,i64>(0)).unwrap(),6);
        upgraded.connection.pragma_update(None,"user_version",99).unwrap(); drop(upgraded);
        assert!(Ledger::open(root.path()).is_err());
        let raw = Connection::open(root.path().join("usage-ledger-v1.sqlite3")).unwrap();
        assert_eq!(raw.pragma_query_value(None,"user_version",|row|row.get::<_,i64>(0)).unwrap(),99);
        assert_eq!(raw.query_row("SELECT COUNT(*) FROM usage_ledger_entries",[],|row|row.get::<_,i64>(0)).unwrap(),1);
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
    fn frozen_route_metadata(channel: Option<crate::chat_call_accounting_v1::ModelCallChannel>) -> crate::chat_call_accounting_v1::Metadata {
        crate::chat_call_accounting_v1::Metadata { role:"ANSWER".into(),model_display_name:"Frozen model · Pro3".into(),
            route:channel.map(|channel|crate::chat_call_accounting_v1::ModelCallRoute {provider_id:"RELAYCORE_GPT".into(),channel_id:Some(channel)}),..Default::default() }
    }
    fn frozen_route_pending() -> Entry { Entry {actual_model_id:None,reconciliation_fingerprint:Some("a".repeat(64)),..entry("route-pending","route-pending",Kind::StreamPending,FactGrade::Estimated)} }
    fn frozen_route_final() -> Entry {Entry {reconciles_entry_id:Some("route-pending".into()),..measured_model_call("route-final")} }
    #[test]
    fn frozen_route_ledger_nonempty_schema5_migrates_without_rewriting_old_metadata() {
        let root=tempdir().unwrap();let ledger=Ledger::open(root.path()).unwrap();let value=measured_model_call("old-schema5");let metadata=frozen_route_metadata(None);
        ledger.append_call(&value,&metadata).unwrap();ledger.connection.pragma_update(None,"user_version",5).unwrap();
        let before:String=ledger.connection.query_row("SELECT metadata_json FROM usage_ledger_call_metadata WHERE entry_id=?1",[&value.entry_id],|r|r.get(0)).unwrap();drop(ledger);
        let reopened=Ledger::open(root.path()).unwrap();assert_eq!(reopened.connection.pragma_query_value(None,"user_version",|r|r.get::<_,i64>(0)).unwrap(),6);
        assert_eq!(reopened.entry(&value.entry_id).unwrap(),Some(value.clone()));assert_eq!(reopened.call_metadata(&value.entry_id).unwrap(),Some(metadata));
        assert_eq!(reopened.connection.query_row("SELECT metadata_json FROM usage_ledger_call_metadata WHERE entry_id=?1",[&value.entry_id],|r|r.get::<_,String>(0)).unwrap(),before);
    }
    #[test]
    fn frozen_route_ledger_pending_snapshot_is_immutable_on_replay_and_survives_final_reopen() {
        use crate::chat_call_accounting_v1::ModelCallChannel::*;
        let root=tempdir().unwrap();let ledger=Ledger::open(root.path()).unwrap();let pending=frozen_route_pending();let metadata=frozen_route_metadata(Some(Pro3));ledger.append_call(&pending,&metadata).unwrap();
        assert_eq!(ledger.append_call(&pending,&frozen_route_metadata(Some(Pro2))).unwrap(),AppendResult::Conflict);assert_eq!(ledger.call_metadata(&pending.entry_id).unwrap(),Some(metadata.clone()));
        let final_entry=frozen_route_final();ledger.append_call(&final_entry,&metadata).unwrap();drop(ledger);let reopened=Ledger::open(root.path()).unwrap();assert_eq!(reopened.call_metadata(&final_entry.entry_id).unwrap(),Some(metadata));
    }
    #[test]
    fn frozen_route_ledger_final_cannot_switch_channel_or_backfill_a_legacy_absence() {
        use crate::chat_call_accounting_v1::ModelCallChannel::*;
        for original in [None,Some(Pro3)] {
            let root=tempdir().unwrap();let ledger=Ledger::open(root.path()).unwrap();let pending=frozen_route_pending();let metadata=frozen_route_metadata(original);ledger.append_call(&pending,&metadata).unwrap();let final_entry=frozen_route_final();
            assert!(ledger.append_call(&final_entry,&frozen_route_metadata(Some(Pro2))).is_err());assert_eq!(ledger.entry(&final_entry.entry_id).unwrap(),None);assert_eq!(ledger.call_metadata(&pending.entry_id).unwrap(),Some(metadata));
        }
    }
    #[test]
    fn frozen_route_ledger_recovery_rejects_changed_route_before_receipt_is_created() {
        use crate::chat_call_accounting_v1::ModelCallChannel::*;
        let root=tempdir().unwrap();let ledger=Ledger::open(root.path()).unwrap();let pending=frozen_route_pending();let mut metadata=frozen_route_metadata(Some(Pro3));metadata.role="RETRIEVAL".into();ledger.append_call(&pending,&metadata).unwrap();let final_entry=frozen_route_final();
        let mut changed=frozen_route_metadata(Some(Pro2));changed.role="RETRIEVAL".into();assert!(ledger.append_recoverable_call(&final_entry,&changed).is_err());assert!(ledger.recovery_receipts().unwrap().is_empty());assert_eq!(ledger.entry(&final_entry.entry_id).unwrap(),None);assert_eq!(ledger.call_metadata(&pending.entry_id).unwrap(),Some(metadata));
    }
    #[test]
    fn frozen_route_ledger_checkpoint_rejects_changed_route_and_recovers_exact_original() {
        use crate::chat_call_accounting_v1::ModelCallChannel::*;
        let root=tempdir().unwrap();let ledger=Ledger::open(root.path()).unwrap();let pending=frozen_route_pending();
        let mut metadata=frozen_route_metadata(Some(Pro3));metadata.role="RETRIEVAL".into();ledger.append_call(&pending,&metadata).unwrap();
        let mut final_entry=frozen_route_final();final_entry.reconciliation_fingerprint=pending.reconciliation_fingerprint.clone();
        let mut changed=metadata.clone();changed.route.as_mut().unwrap().channel_id=Some(Pro2);
        assert!(ledger.checkpoint_retrieval(&final_entry,&changed).is_err());assert!(ledger.retrieval_progress().unwrap().is_empty());
        ledger.checkpoint_retrieval(&final_entry,&metadata).unwrap();drop(ledger);
        let ledger=Ledger::open(root.path()).unwrap();ledger.recover_interrupted_retrieval(&final_entry.entry_id).unwrap();
        assert_eq!(ledger.call_metadata(&final_entry.entry_id).unwrap(),Some(metadata.clone()));assert_eq!(ledger.call_metadata(&pending.entry_id).unwrap(),Some(metadata));
    }
    #[test]
    fn backup_call_metadata_snapshot_rejects_changed_settled_channel_without_touching_bytes() {
        use crate::chat_call_accounting_v1::ModelCallChannel::*;
        let root=tempdir().unwrap();let ledger=Ledger::open(root.path()).unwrap();let pending=frozen_route_pending();let metadata=frozen_route_metadata(Some(Pro3));ledger.append_call(&pending,&metadata).unwrap();let value=frozen_route_final();ledger.append_call(&value,&metadata).unwrap();
        let mut changed=metadata.clone();changed.route.as_mut().unwrap().channel_id=Some(Pro2);
        ledger.connection.execute("UPDATE usage_ledger_call_metadata SET metadata_json=?1 WHERE entry_id=?2",params![serde_json::to_string(&changed).unwrap(),value.entry_id]).unwrap();drop(ledger);
        let path=root.path().join("usage-ledger-v1.sqlite3");let before=std::fs::read(&path).unwrap();assert!(Ledger::validate_recovery_snapshot(&path).is_err());assert_eq!(std::fs::read(&path).unwrap(),before);
    }
    #[test]
    fn backup_call_metadata_snapshot_rejects_invalid_settled_json_while_legacy_unknown_is_valid() {
        for payload in ["{}","not-json",r#"{"role":"ANSWER","modelDisplayName":"Old","unexpected":true}"#] {
            let root=tempdir().unwrap();let ledger=Ledger::open(root.path()).unwrap();let value=measured_model_call("backup-legacy");let metadata=frozen_route_metadata(None);ledger.append_call(&value,&metadata).unwrap();drop(ledger);
            let path=root.path().join("usage-ledger-v1.sqlite3");let valid=std::fs::read(&path).unwrap();Ledger::validate_recovery_snapshot(&path).unwrap();assert_eq!(std::fs::read(&path).unwrap(),valid);
            let c=Connection::open(&path).unwrap();c.execute("UPDATE usage_ledger_call_metadata SET metadata_json=?1",[payload]).unwrap();drop(c);let before=std::fs::read(&path).unwrap();assert!(Ledger::validate_recovery_snapshot(&path).is_err(),"malformed stored metadata must not be approved");assert_eq!(std::fs::read(&path).unwrap(),before);
        }
    }
    #[test]
    fn frozen_route_ledger_future_version_is_rejected_by_open_and_readonly_recovery_validation() {
        let root=tempdir().unwrap();let ledger=Ledger::open(root.path()).unwrap();ledger.connection.pragma_update(None,"user_version",7).unwrap();drop(ledger);let file=root.path().join("usage-ledger-v1.sqlite3");let before=std::fs::read(&file).unwrap();
        assert!(Ledger::open(root.path()).is_err());assert!(Ledger::validate_recovery_snapshot(&file).is_err());assert_eq!(std::fs::read(&file).unwrap(),before);
    }

}


#[cfg(test)]
mod retrieval_live_progress_tests {
    use super::*;
    use crate::chat_call_accounting_v1::Metadata;
    fn seed(l:&Ledger)->(Entry,Metadata) {
        let p=Entry{entry_id:"pending-child".into(),replay_token:"pending-child".into(),execution_id:"parent".into(),conversation_id:"conversation".into(),branch_leaf_message_id:"assistant".into(),invocation_id:"child".into(),attempt_id:"child".into(),kind:Kind::StreamPending,fact_grade:FactGrade::Estimated,requested_model_id:"openai/gpt-6.1-sol".into(),actual_model_id:None,input_tokens:None,output_tokens:None,cached_input_tokens:None,charge_micros:None,budget_micros:None,adjustment_micros:None,currency_code:None,cost_source:None,reconciliation_fingerprint:Some("a".repeat(64)),reconciles_entry_id:None,source:Source::DesktopLocal,occurred_at_ms:7};
        let m=Metadata{role:"RETRIEVAL".into(),model_display_name:"Sol".into(),..Default::default()};
        l.append_call(&p,&m).unwrap();
        let mut e=p;e.entry_id="final-child".into();e.replay_token=e.entry_id.clone();e.kind=Kind::FinalMeasured;e.fact_grade=FactGrade::ProviderReported;e.reconciles_entry_id=Some("pending-child".into());e.actual_model_id=Some("openai/gpt-6.1-sol".into());e.input_tokens=Some(17);e.output_tokens=Some(0);e.cached_input_tokens=Some(3);e.charge_micros=Some(4);e.currency_code=Some("USD".into());e.cost_source=Some("PROVIDER_RESPONSE".into());(e,m)
    }
    #[test]
    fn retrieval_progress_cold_open_keeps_pending_until_explicit_interruption_recovery() {
        let root=tempfile::tempdir().unwrap();let l=Ledger::open(root.path()).unwrap();let(e,m)=seed(&l);
        l.checkpoint_retrieval(&e,&m).unwrap();assert!(l.entry(&e.entry_id).unwrap().is_none());drop(l);
        let l=Ledger::open(root.path()).unwrap();assert_eq!(l.display_entries().unwrap()[0].kind,Kind::StreamPending);assert_eq!(l.retrieval_progress().unwrap(),vec![(e.clone(),m.clone(),false)]);
        l.recover_interrupted_retrieval(&e.entry_id).unwrap();assert_eq!(l.display_entries().unwrap(),vec![e.clone()]);assert!(l.retrieval_was_interrupted(&e).unwrap());
        drop(l);let l=Ledger::open(root.path()).unwrap();l.recover_interrupted_retrieval(&e.entry_id).unwrap();assert_eq!(l.display_entries().unwrap(),vec![e.clone()]);assert!(l.checkpoint_retrieval(&e,&m).is_err());
    }
    #[test]
    fn retrieval_progress_sparse_frame_retains_facts_and_explicit_zero_replaces_quantity() {
        let root=tempfile::tempdir().unwrap();let l=Ledger::open(root.path()).unwrap();let(e,m)=seed(&l);l.checkpoint_retrieval(&e,&m).unwrap();
        let mut sparse=e.clone();sparse.actual_model_id=None;sparse.input_tokens=None;sparse.output_tokens=None;sparse.cached_input_tokens=Some(0);sparse.charge_micros=None;sparse.currency_code=None;sparse.cost_source=None;
        l.checkpoint_retrieval(&sparse,&m).unwrap();let (saved,_,_)=l.retrieval_progress().unwrap().remove(0);
        assert_eq!((saved.input_tokens,saved.output_tokens,saved.cached_input_tokens,saved.charge_micros),(Some(17),Some(0),Some(0),Some(4)));assert_eq!(saved.actual_model_id,e.actual_model_id);
    }
    #[test]
    fn retrieval_progress_identity_only_does_not_invent_quantities_or_money() {
        let root=tempfile::tempdir().unwrap();let l=Ledger::open(root.path()).unwrap();let(mut e,m)=seed(&l);
        e.input_tokens=None;e.output_tokens=None;e.cached_input_tokens=None;e.charge_micros=None;e.currency_code=None;e.cost_source=None;
        l.checkpoint_retrieval(&e,&m).unwrap();l.recover_interrupted_retrieval(&e.entry_id).unwrap();assert_eq!(l.entry(&e.entry_id).unwrap(),Some(e));
    }
    #[test]
    fn retrieval_progress_rejects_other_parent_negative_values_and_estimated_money() {
        let root=tempfile::tempdir().unwrap();let l=Ledger::open(root.path()).unwrap();let(e,m)=seed(&l);
        for case in 0..4 {let mut wrong=e.clone();match case {0=>wrong.execution_id="other".into(),1=>wrong.input_tokens=Some(-1),2=>wrong.cost_source=Some("LOCAL_ESTIMATE".into()),_=>wrong.requested_model_id="other/model".into()};assert!(l.checkpoint_retrieval(&wrong,&m).is_err());}
        assert!(l.retrieval_progress().unwrap().is_empty());l.checkpoint_retrieval(&e,&m).unwrap();
        let mut wrong=e.clone();wrong.currency_code=Some("CNY".into());assert!(l.checkpoint_retrieval(&wrong,&m).is_err(),"Captured currency cannot change between frames");
    }
    #[test]
    fn retrieval_progress_final_write_failure_retains_recoverable_snapshot_and_explicit_outcome() {
        let root=tempfile::tempdir().unwrap();let l=Ledger::open(root.path()).unwrap();let(e,m)=seed(&l);l.checkpoint_retrieval(&e,&m).unwrap();
        l.connection.execute_batch("CREATE TRIGGER block_child BEFORE INSERT ON usage_ledger_entries WHEN NEW.kind='FINAL_MEASURED' BEGIN SELECT RAISE(ABORT,'synthetic blocked');END;").unwrap();
        assert!(l.recover_interrupted_retrieval(&e.entry_id).is_err());assert!(l.retrieval_progress().unwrap()[0].2);drop(l);
        let raw=Connection::open(root.path().join("usage-ledger-v1.sqlite3")).unwrap();raw.execute_batch("DROP TRIGGER block_child;").unwrap();drop(raw);
        let l=Ledger::open(root.path()).unwrap();l.recover_interrupted_retrieval(&e.entry_id).unwrap();assert_eq!(l.entry(&e.entry_id).unwrap(),Some(e.clone()));assert!(l.retrieval_was_interrupted(&e).unwrap());assert_eq!(l.recover_calls().unwrap(),0);
    }
    #[test]
    fn retrieval_progress_normal_final_supersedes_checkpoint_without_interruption() {
        let root=tempfile::tempdir().unwrap();let l=Ledger::open(root.path()).unwrap();let(mut e,m)=seed(&l);l.checkpoint_retrieval(&e,&m).unwrap();
        e.output_tokens=Some(9);e.charge_micros=Some(8);l.append_recoverable_call(&e,&m).unwrap();l.finish_live_retrieval("pending-child").unwrap();
        assert!(l.retrieval_progress().unwrap().is_empty());assert!(!l.retrieval_was_interrupted(&e).unwrap());assert_eq!(l.display_entries().unwrap(),vec![e]);
    }
    #[test]
    fn retrieval_progress_schema_three_upgrade_retains_original_entries_and_metadata() {
        let root=tempfile::tempdir().unwrap();let l=Ledger::open(root.path()).unwrap();let(e,m)=seed(&l);let before=l.all_entries().unwrap();
        l.connection.execute_batch("DROP TABLE usage_ledger_call_outcomes;DROP TABLE usage_ledger_retrieval_progress;PRAGMA user_version=3;").unwrap();drop(l);
        let l=Ledger::open(root.path()).unwrap();assert_eq!(l.all_entries().unwrap(),before);assert_eq!(l.call_metadata("pending-child").unwrap(),Some(m.clone()));l.checkpoint_retrieval(&e,&m).unwrap();
        assert_eq!(l.connection.pragma_query_value(None,"user_version",|r|r.get::<_,i64>(0)).unwrap(),6);
    }
}


#[cfg(test)]
mod call_outcome_ledger_tests {
    use super::*;
    use crate::chat_call_accounting_v1::{CallOutcome,CallOutcomeOrigin,CallOutcomeState,Metadata};
    fn seed(l:&Ledger)->(Entry,Metadata) {seed_role(l,"RETRIEVAL")}
    fn seed_role(l:&Ledger,role:&str)->(Entry,Metadata) {
        let entry=Entry{entry_id:"pending-child".into(),replay_token:"pending-child".into(),execution_id:"parent".into(),conversation_id:"conversation".into(),branch_leaf_message_id:"assistant".into(),invocation_id:"child".into(),attempt_id:"child".into(),kind:Kind::StreamPending,fact_grade:FactGrade::Estimated,requested_model_id:"openai/gpt-6.1-sol".into(),actual_model_id:None,input_tokens:None,output_tokens:None,cached_input_tokens:None,charge_micros:None,budget_micros:None,adjustment_micros:None,currency_code:None,cost_source:None,reconciliation_fingerprint:Some("a".repeat(64)),reconciles_entry_id:None,source:Source::DesktopLocal,occurred_at_ms:7};
        let metadata=Metadata{role:role.into(),model_display_name:"Sol".into(),..Default::default()};l.append_call(&entry,&metadata).unwrap();(entry,metadata)
    }
    fn completed()->CallOutcome {CallOutcome{state:CallOutcomeState::Completed,safe_error_code:None,model_elapsed_ms:Some(7),origin:CallOutcomeOrigin::LocalExecutionResult}}
    #[test]
    fn material_read_outcome_and_recovery_preserve_role_and_known_zero_without_a_search() {
        let root=tempfile::tempdir().unwrap();let l=Ledger::open(root.path()).unwrap();let(p,m)=seed_role(&l,"MATERIAL_READ");
        let mut e=p.clone();e.entry_id="read-final".into();e.replay_token=e.entry_id.clone();e.kind=Kind::FinalMeasured;e.fact_grade=FactGrade::ProviderReported;e.reconciles_entry_id=Some(p.entry_id.clone());e.input_tokens=Some(11);e.output_tokens=Some(0);
        l.checkpoint_retrieval(&e,&m).unwrap();let mut wrong=m.clone();wrong.role="RETRIEVAL".into();assert!(l.checkpoint_retrieval(&e,&wrong).is_err());assert!(l.append_call(&e,&wrong).is_err());assert!(l.append_recoverable_call(&e,&wrong).is_err());assert_eq!(l.recover_calls().unwrap(),0);assert!(l.entry(&e.entry_id).unwrap().is_none());
        l.recover_interrupted_retrieval(&e.entry_id).unwrap();assert_eq!(l.call_outcome(&e).unwrap(),Some(CallOutcome::interrupted()));assert_eq!(l.call_metadata(&e.entry_id).unwrap(),Some(m.clone()));drop(l);
        let l=Ledger::open(root.path()).unwrap();assert_eq!(l.entry(&e.entry_id).unwrap(),Some(e.clone()));assert_eq!(l.call_metadata(&e.entry_id).unwrap(),Some(m));assert_eq!(l.call_outcome(&e).unwrap().unwrap().state,CallOutcomeState::Unknown);assert_eq!(l.display_entries().unwrap().len(),1);
    }
    #[test]
    fn material_read_measured_write_failure_recovers_original_role_and_result() {
        let root=tempfile::tempdir().unwrap();let l=Ledger::open(root.path()).unwrap();let(p,m)=seed_role(&l,"MATERIAL_READ");let mut e=p.clone();e.entry_id="read-final".into();e.replay_token=e.entry_id.clone();e.kind=Kind::FinalMeasured;e.fact_grade=FactGrade::ProviderReported;e.reconciles_entry_id=Some(p.entry_id.clone());e.input_tokens=Some(11);
        l.connection.execute_batch("CREATE TRIGGER reject_read BEFORE INSERT ON usage_ledger_entries WHEN NEW.kind='FINAL_MEASURED' BEGIN SELECT RAISE(ABORT,'synthetic read blocked');END;").unwrap();assert!(l.append_recoverable_call(&e,&m).is_err());l.append_call_outcome(&p,&completed()).unwrap();l.connection.execute_batch("DROP TRIGGER reject_read;").unwrap();drop(l);
        let l=Ledger::open(root.path()).unwrap();assert_eq!(l.entry(&e.entry_id).unwrap(),Some(e.clone()));assert_eq!(l.call_metadata(&e.entry_id).unwrap(),Some(m));assert_eq!(l.call_outcome(&e).unwrap(),Some(completed()));assert_eq!(l.recover_calls().unwrap(),0);
    }
    #[test]
    fn call_outcome_replays_and_conflicts_without_rewriting_quantities_or_metadata() {
        let root=tempfile::tempdir().unwrap();let l=Ledger::open(root.path()).unwrap();let(p,m)=seed(&l);let outcome=completed();
        let mut final_entry=p.clone();final_entry.entry_id="final-child".into();final_entry.replay_token=final_entry.entry_id.clone();final_entry.kind=Kind::FinalMeasured;final_entry.fact_grade=FactGrade::ProviderReported;final_entry.reconciles_entry_id=Some(p.entry_id.clone());final_entry.input_tokens=Some(17);final_entry.output_tokens=Some(5);
        l.append_recoverable_call(&final_entry,&m).unwrap();l.append_call_outcome(&p,&outcome).unwrap();l.append_call_outcome(&p,&outcome).unwrap();assert_eq!(l.call_outcome(&final_entry).unwrap(),Some(outcome.clone()));
        assert!(l.append_call_outcome(&p,&CallOutcome::interrupted()).is_err());assert_eq!(l.entry(&final_entry.entry_id).unwrap(),Some(final_entry.clone()));assert_eq!(l.call_metadata(&p.entry_id).unwrap(),Some(m.clone()));drop(l);
        let l=Ledger::open(root.path()).unwrap();assert_eq!(l.call_outcome(&final_entry).unwrap(),Some(outcome));assert_eq!(l.entry(&final_entry.entry_id).unwrap(),Some(final_entry));assert_eq!(l.call_metadata(&p.entry_id).unwrap(),Some(m));
    }
    #[test]
    fn call_outcome_without_quantities_remains_explicit_failure_unknown_or_stop_not_free() {
        for state in [CallOutcomeState::Failed,CallOutcomeState::Unknown,CallOutcomeState::Cancelled] {
            let root=tempfile::tempdir().unwrap();let l=Ledger::open(root.path()).unwrap();let(p,_)=seed(&l);
            let o=CallOutcome{state,safe_error_code:Some(match state {CallOutcomeState::Failed=>"PROVIDER_STATUS",CallOutcomeState::Unknown=>"MISSING_COMPLETION",_=>"CANCELLED"}.into()),model_elapsed_ms:None,origin:CallOutcomeOrigin::LocalExecutionResult};
            l.append_call_outcome(&p,&o).unwrap();let rows=l.display_entries().unwrap();assert_eq!(rows,vec![p.clone()]);assert_eq!(l.call_outcome(&p).unwrap(),Some(o));assert_eq!((p.input_tokens,p.output_tokens,p.charge_micros,p.actual_model_id),(None,None,None,None));
        }
    }
    #[test]
    fn call_outcome_before_dispatch_is_retained_but_not_counted_as_an_actual_call() {
        let root=tempfile::tempdir().unwrap();let l=Ledger::open(root.path()).unwrap();let(p,m)=seed(&l);l.append_call_outcome(&p,&CallOutcome::cancelled_before_dispatch()).unwrap();
        assert!(l.display_entries().unwrap().is_empty());assert_eq!(l.all_entries().unwrap(),vec![p.clone()]);
        let mut e=p.clone();e.entry_id="bad-final".into();e.replay_token=e.entry_id.clone();e.kind=Kind::FinalMeasured;e.fact_grade=FactGrade::ProviderReported;e.reconciles_entry_id=Some(p.entry_id);e.input_tokens=Some(1);assert!(l.append_call(&e,&m).is_err());
    }
    #[test]
    fn call_outcome_wrong_identity_and_write_failure_preserve_pending_and_measurement() {
        let root=tempfile::tempdir().unwrap();let l=Ledger::open(root.path()).unwrap();let(p,_)=seed(&l);let mut wrong=p.clone();wrong.conversation_id="other".into();assert!(l.append_call_outcome(&wrong,&completed()).is_err());
        l.connection.execute_batch("CREATE TRIGGER reject_outcome BEFORE INSERT ON usage_ledger_call_outcomes BEGIN SELECT RAISE(ABORT,'synthetic blocked');END;").unwrap();assert!(l.append_call_outcome(&p,&completed()).is_err());assert_eq!(l.call_outcome(&p).unwrap(),None);assert_eq!(l.entry(&p.entry_id).unwrap(),Some(p));
    }
    #[test]
    fn call_outcome_schema_four_upgrade_keeps_legacy_facts_and_never_guesses_completed() {
        let root=tempfile::tempdir().unwrap();let l=Ledger::open(root.path()).unwrap();let(p,m)=seed(&l);
        let mut final_entry=p.clone();final_entry.entry_id="legacy-final".into();final_entry.replay_token=final_entry.entry_id.clone();final_entry.kind=Kind::FinalMeasured;final_entry.fact_grade=FactGrade::ProviderReported;final_entry.reconciles_entry_id=Some(p.entry_id.clone());final_entry.actual_model_id=Some("openai/gpt-6.1-sol".into());final_entry.input_tokens=Some(17);final_entry.output_tokens=Some(5);final_entry.cached_input_tokens=Some(3);final_entry.charge_micros=Some(6);final_entry.currency_code=Some("USD".into());final_entry.cost_source=Some("PROVIDER_RESPONSE".into());l.append_recoverable_call(&final_entry,&m).unwrap();
        let mut second=p.clone();second.entry_id="pending-second".into();second.replay_token=second.entry_id.clone();second.invocation_id="second".into();second.attempt_id="second".into();second.reconciliation_fingerprint=Some("b".repeat(64));l.append_call(&second,&m).unwrap();
        let mut snapshot=second.clone();snapshot.entry_id="final-second".into();snapshot.replay_token=snapshot.entry_id.clone();snapshot.kind=Kind::FinalMeasured;snapshot.fact_grade=FactGrade::ProviderReported;snapshot.reconciles_entry_id=Some(second.entry_id.clone());snapshot.actual_model_id=Some("openai/gpt-6.1-sol".into());snapshot.input_tokens=Some(0);l.checkpoint_retrieval(&snapshot,&m).unwrap();
        let entries=l.all_entries().unwrap();let progress=l.retrieval_progress().unwrap();
        l.connection.execute_batch("DROP TABLE usage_ledger_call_outcomes;PRAGMA user_version=4;").unwrap();drop(l);
        let l=Ledger::open(root.path()).unwrap();assert_eq!(l.all_entries().unwrap(),entries);assert_eq!(l.call_metadata(&p.entry_id).unwrap(),Some(m));assert_eq!(l.call_outcome(&p).unwrap(),None);assert_eq!(l.call_outcome(&final_entry).unwrap(),None);assert_eq!(l.call_outcome(&second).unwrap(),None);assert_eq!(l.retrieval_progress().unwrap(),progress);assert_eq!(l.connection.pragma_query_value(None,"user_version",|r|r.get::<_,i64>(0)).unwrap(),6);
        l.append_call_outcome(&p,&completed()).unwrap();
    }
    #[test]
    fn call_outcome_observed_terminal_cannot_be_replaced_by_later_parent_interruption() {
        let root=tempfile::tempdir().unwrap();let l=Ledger::open(root.path()).unwrap();let(p,m)=seed(&l);
        let mut snapshot=p.clone();snapshot.entry_id="snapshot-final".into();snapshot.replay_token=snapshot.entry_id.clone();snapshot.kind=Kind::FinalMeasured;snapshot.fact_grade=FactGrade::ProviderReported;snapshot.reconciles_entry_id=Some(p.entry_id.clone());snapshot.input_tokens=Some(17);l.checkpoint_retrieval(&snapshot,&m).unwrap();
        l.append_call_outcome(&p,&completed()).unwrap();let original=l.retrieval_progress().unwrap();assert!(l.recover_interrupted_retrieval(&snapshot.entry_id).is_err());assert_eq!(l.retrieval_progress().unwrap(),original);assert_eq!(l.call_outcome(&p).unwrap(),Some(completed()));assert!(l.entry(&snapshot.entry_id).unwrap().is_none());
    }
    #[test]
    fn call_outcome_malformed_backup_is_rejected_read_only() {
        let root=tempfile::tempdir().unwrap();let l=Ledger::open(root.path()).unwrap();let(p,_)=seed(&l);
        l.connection.execute("INSERT INTO usage_ledger_call_outcomes VALUES(?1,?2)",params![p.entry_id,"{\"state\":\"COMPLETED\",\"origin\":\"LOCAL_EXECUTION_RESULT\",\"safeErrorCode\":null,\"modelElapsedMs\":7,\"payload\":\"forbidden synthetic\"}"]).unwrap();assert!(l.call_outcome(&p).is_err());drop(l);
        let path=root.path().join("usage-ledger-v1.sqlite3");let before=std::fs::read(&path).unwrap();assert!(Ledger::validate_recovery_snapshot(&path).is_err());assert_eq!(std::fs::read(&path).unwrap(),before);
    }

}
