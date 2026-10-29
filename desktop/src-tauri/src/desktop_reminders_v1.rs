//! Desktop owner for reviewable reminder drafts, persisted schedules and monitor runs.
//!
//! Conversation text is used only while the user explicitly asks for a draft or while an
//! already-confirmed plan runs. Draft audit rows retain stable message ids and a source hash,
//! never the source transcript. A draft cannot become a plan without `confirm_draft`.

use chrono::{DateTime, Datelike, Duration, LocalResult, NaiveDateTime, TimeZone, Utc};
use chrono_tz::Tz;
use rusqlite::{params, Connection, OptionalExtension, Transaction};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use sha2::{Digest, Sha256};

const MISSED_GRACE_MS: i64 = 60_000;
const MAX_TITLE_CHARS: usize = 40;
const MAX_INSTRUCTION_CHARS: usize = 8_000;
const MAX_SOURCE_CHARS: usize = 4_000;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct DraftProjection {
    pub draft_id: String,
    pub workspace_id: String,
    pub conversation_id: Option<String>,
    pub source_user_message_id: Option<String>,
    pub source_assistant_message_id: Option<String>,
    pub status: String,
    pub title: String,
    pub instruction: String,
    pub schedule_kind: String,
    pub anchor_local: String,
    pub timezone_id: String,
    pub missed_policy: String,
    pub provider_id: Option<String>,
    pub requested_model_id: Option<String>,
    pub actual_model_id: Option<String>,
    pub input_tokens: Option<i64>,
    pub output_tokens: Option<i64>,
    pub cached_input_tokens: Option<i64>,
    pub charge_micros: Option<i64>,
    pub currency_code: Option<String>,
    pub safe_error_code: Option<String>,
    pub retry_count: i64,
    pub created_at_ms: i64,
    pub updated_at_ms: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct PlanProjection {
    pub plan_id: String,
    pub source_draft_id: String,
    pub workspace_id: String,
    pub conversation_id: Option<String>,
    pub title: String,
    pub instruction: String,
    pub schedule_kind: String,
    pub anchor_local: String,
    pub timezone_id: String,
    pub missed_policy: String,
    pub status: String,
    pub next_run_at_ms: Option<i64>,
    pub last_run_at_ms: Option<i64>,
    pub latest_result: Option<String>,
    pub last_provider_id: Option<String>,
    pub last_model_id: Option<String>,
    pub last_input_tokens: Option<i64>,
    pub last_output_tokens: Option<i64>,
    pub last_charge_micros: Option<i64>,
    pub last_currency_code: Option<String>,
    pub last_safe_error_code: Option<String>,
    pub created_at_ms: i64,
    pub updated_at_ms: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct DiagnosticProjection {
    pub kind: String,
    pub title: String,
    pub summary: String,
    pub occurred_at_ms: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct Projection {
    pub drafts: Vec<DraftProjection>,
    pub plans: Vec<PlanProjection>,
    pub diagnostics: Vec<DiagnosticProjection>,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct GenerateDraftArgs {
    pub workspace_id: String,
    pub conversation_id: String,
    pub user_message_id: String,
    pub assistant_message_id: String,
    pub timezone_id: String,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ManualDraftArgs {
    pub workspace_id: String,
    pub conversation_id: Option<String>,
    pub timezone_id: String,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ConfirmDraftArgs {
    pub draft_id: String,
    pub title: String,
    pub instruction: String,
    pub schedule_kind: String,
    pub anchor_local: String,
    pub timezone_id: String,
    pub missed_policy: String,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdatePlanArgs {
    pub plan_id: String,
    pub expected_updated_at_ms: i64,
    pub title: String,
    pub instruction: String,
    pub schedule_kind: String,
    pub anchor_local: String,
    pub timezone_id: String,
    pub missed_policy: String,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PortablePlanArgs {
    pub source_key: String,
    pub workspace_id: String,
    pub conversation_id: String,
    pub title: String,
    pub instruction: String,
    pub schedule_kind: String,
    pub anchor_local: String,
    pub timezone_id: String,
    pub missed_policy: String,
    pub source_status: String,
    pub next_run_at_ms: Option<i64>,
}

#[derive(Debug, Clone)]
pub struct DraftReservation {
    pub draft_id: String,
    pub attempt_id: String,
    pub workspace_id: String,
    pub conversation_id: String,
    pub user_message_id: String,
    pub assistant_message_id: String,
    pub user_text: String,
    pub assistant_text: String,
    pub timezone_id: String,
    pub retry_count: i64,
}

#[derive(Debug, Clone)]
pub struct RunReservation {
    pub run_id: String,
    pub attempt_id: String,
    pub plan: PlanProjection,
    pub scheduled_at_ms: i64,
}

fn in_reminder_transaction<T>(connection:&Connection, action:impl FnOnce(&Connection)->Result<T,String>)->Result<T,String>{
    if !connection.is_autocommit(){return action(connection);}
    let transaction=connection.unchecked_transaction().map_err(|_|"提醒运行事务未开始".to_owned())?;
    let result=action(&transaction)?;
    transaction.commit().map_err(|_|"提醒运行事务未提交".to_owned())?;
    Ok(result)
}

fn require_current_run(connection:&Connection,reservation:&RunReservation)->Result<(),String>{
    let current:bool=connection.query_row("SELECT EXISTS(SELECT 1 FROM desktop_reminder_runs_v1 r JOIN desktop_reminder_plans_v1 p ON p.plan_id=r.plan_id WHERE r.run_id=?1 AND r.plan_id=?2 AND r.attempt_id=?3 AND r.scheduled_at_ms=?4 AND r.status='RUNNING' AND p.workspace_id=?5 AND p.status!='DELETED')",
        params![reservation.run_id,reservation.plan.plan_id,reservation.attempt_id,reservation.scheduled_at_ms,reservation.plan.workspace_id],|r|r.get(0)).map_err(|_|"提醒运行归属无法核对".to_owned())?;
    if current{Ok(())}else{Err("REMINDER_RUN_CHANGED".into())}
}

pub fn bind_run_provider(connection:&Connection,reservation:&RunReservation,provider_id:&str,requested_model_id:&str)->Result<(),String>{
    if provider_id!="OPENROUTER"||crate::desktop_ordinary_chat_v1::valid_reported_model_id(requested_model_id).is_none(){return Err("提醒运行模型身份无效".into());}
    in_reminder_transaction(connection,|connection|{
        require_current_run(connection,reservation)?;
        let changed=connection.execute("UPDATE desktop_reminder_runs_v1 SET provider_id=?2,requested_model_id=?3 WHERE run_id=?1 AND (provider_id IS NULL OR provider_id=?2) AND (requested_model_id IS NULL OR requested_model_id=?3)",params![reservation.run_id,provider_id,requested_model_id]).map_err(|_|"提醒运行模型身份未保存".to_owned())?;
        if changed==1{Ok(())}else{Err("REMINDER_RUN_CHANGED".into())}
    })
}

fn validate_reminder_measurement(measurement:&crate::desktop_ordinary_chat_v1::ReportedMeasurement)->Result<(),String>{
    let u=&measurement.usage;
    if [u.input_tokens,u.output_tokens,u.cached_input_tokens,u.reasoning_tokens,measurement.reported_cost_micros].iter().flatten().any(|v|*v<0)
        ||measurement.elapsed_ms<0||measurement.actual_model_id.as_deref().is_some_and(|v|crate::desktop_ordinary_chat_v1::valid_reported_model_id(v).is_none()){
        return Err("REMINDER_MEASUREMENT_INVALID".into());
    }
    Ok(())
}

/// Provider-owned metadata only; no partial response, prompt, or credential.
pub fn checkpoint_run_measurement(connection:&Connection,reservation:&RunReservation,provider_id:&str,requested_model_id:&str,measurement:&crate::desktop_ordinary_chat_v1::ReportedMeasurement)->Result<(),String>{
    validate_reminder_measurement(measurement)?;
    let u=&measurement.usage;
    in_reminder_transaction(connection,|connection|{
        require_current_run(connection,reservation)?;
        let changed=connection.execute("UPDATE desktop_reminder_runs_v1 SET actual_model_id=COALESCE(?4,actual_model_id),input_tokens=COALESCE(?5,input_tokens),output_tokens=COALESCE(?6,output_tokens),cached_input_tokens=COALESCE(?7,cached_input_tokens),charge_micros=COALESCE(?8,charge_micros),currency_code=CASE WHEN ?8 IS NULL THEN currency_code ELSE 'USD' END WHERE run_id=?1 AND provider_id=?2 AND requested_model_id=?3",
            params![reservation.run_id,provider_id,requested_model_id,measurement.actual_model_id,u.input_tokens,u.output_tokens,u.cached_input_tokens,measurement.reported_cost_micros]).map_err(|_|"提醒途中计量未保存".to_owned())?;
        if changed==1{Ok(())}else{Err("REMINDER_RUN_CHANGED".into())}
    })
}

pub fn require_current_draft(connection:&Connection,reservation:&DraftReservation)->Result<(),String>{
    let current:bool=connection.query_row("SELECT EXISTS(SELECT 1 FROM desktop_reminder_drafts_v1 WHERE draft_id=?1 AND attempt_id=?2 AND retry_count=?3 AND workspace_id=?4 AND conversation_id=?5 AND source_user_message_id=?6 AND source_assistant_message_id=?7 AND status='RUNNING')",
        params![reservation.draft_id,reservation.attempt_id,reservation.retry_count,reservation.workspace_id,reservation.conversation_id,reservation.user_message_id,reservation.assistant_message_id],|r|r.get(0)).map_err(|_|"提醒草案调用归属无法核对".to_owned())?;
    if current{Ok(())}else{Err("REMINDER_DRAFT_CHANGED".into())}
}

pub fn bind_draft_provider(connection:&Connection,reservation:&DraftReservation,provider_id:&str,requested_model_id:&str)->Result<(),String>{
    if provider_id!="QWEN"||crate::desktop_ordinary_chat_v1::valid_reported_model_id(requested_model_id).is_none(){return Err("提醒草案模型身份无效".into());}
    in_reminder_transaction(connection,|connection|{
        require_current_draft(connection,reservation)?;
        let changed=connection.execute("UPDATE desktop_reminder_drafts_v1 SET provider_id=?2,requested_model_id=?3 WHERE draft_id=?1 AND (provider_id IS NULL OR provider_id=?2) AND (requested_model_id IS NULL OR requested_model_id=?3)",params![reservation.draft_id,provider_id,requested_model_id]).map_err(|_|"提醒草案模型身份未保存".to_owned())?;
        if changed==1{Ok(())}else{Err("REMINDER_DRAFT_CHANGED".into())}
    })
}

pub fn checkpoint_draft_measurement(connection:&Connection,reservation:&DraftReservation,provider_id:&str,requested_model_id:&str,measurement:&crate::desktop_ordinary_chat_v1::ReportedMeasurement,now_ms:i64)->Result<(),String>{
    validate_reminder_measurement(measurement)?;let u=&measurement.usage;
    let currency=crate::chat_call_accounting_v1::provider_billing_currency(provider_id).ok_or("提醒草案服务商币种无效")?;
    in_reminder_transaction(connection,|connection|{
        require_current_draft(connection,reservation)?;
        let changed=connection.execute("UPDATE desktop_reminder_drafts_v1 SET actual_model_id=COALESCE(?4,actual_model_id),input_tokens=COALESCE(?5,input_tokens),output_tokens=COALESCE(?6,output_tokens),cached_input_tokens=COALESCE(?7,cached_input_tokens),charge_micros=COALESCE(?8,charge_micros),currency_code=CASE WHEN ?8 IS NULL THEN currency_code ELSE ?9 END,updated_at_ms=?10 WHERE draft_id=?1 AND provider_id=?2 AND requested_model_id=?3",
            params![reservation.draft_id,provider_id,requested_model_id,measurement.actual_model_id,u.input_tokens,u.output_tokens,u.cached_input_tokens,measurement.reported_cost_micros,currency,now_ms]).map_err(|_|"提醒草案途中计量未保存".to_owned())?;
        if changed==1{Ok(())}else{Err("REMINDER_DRAFT_CHANGED".into())}
    })
}

#[derive(Debug, Clone)]
pub struct Completion<'a> {
    pub provider_id: &'a str,
    pub requested_model_id: &'a str,
    pub actual_model_id: Option<&'a str>,
    pub text: &'a str,
    pub input_tokens: Option<i64>,
    pub output_tokens: Option<i64>,
    pub cached_input_tokens: Option<i64>,
    pub charge_micros: Option<i64>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct NotificationProjection {
    pub run_id: String,
    pub plan_id: String,
    pub workspace_id: String,
    pub conversation_id: Option<String>,
    pub title: String,
    pub body: String,
}

pub fn migrate(transaction: &Transaction<'_>) -> Result<(), String> {
    transaction.execute_batch(
        "CREATE TABLE IF NOT EXISTS desktop_reminder_drafts_v1 (
            draft_id TEXT PRIMARY KEY NOT NULL,
            workspace_id TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
            conversation_id TEXT,
            source_user_message_id TEXT,
            source_assistant_message_id TEXT,
            source_hash TEXT,
            attempt_id TEXT UNIQUE,
            status TEXT NOT NULL,
            title TEXT NOT NULL,
            instruction TEXT NOT NULL,
            schedule_kind TEXT NOT NULL,
            anchor_local TEXT NOT NULL,
            timezone_id TEXT NOT NULL,
            missed_policy TEXT NOT NULL,
            provider_id TEXT,
            requested_model_id TEXT,
            actual_model_id TEXT,
            input_tokens INTEGER,
            output_tokens INTEGER,
            cached_input_tokens INTEGER,
            charge_micros INTEGER,
            currency_code TEXT,
            safe_error_code TEXT,
            retry_count INTEGER NOT NULL,
            created_at_ms INTEGER NOT NULL,
            updated_at_ms INTEGER NOT NULL
        );
        CREATE INDEX IF NOT EXISTS desktop_reminder_drafts_status_v1 ON desktop_reminder_drafts_v1(status,updated_at_ms DESC);
        CREATE TABLE IF NOT EXISTS desktop_reminder_plans_v1 (
            plan_id TEXT PRIMARY KEY NOT NULL,
            source_draft_id TEXT NOT NULL UNIQUE REFERENCES desktop_reminder_drafts_v1(draft_id) ON DELETE RESTRICT,
            workspace_id TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
            conversation_id TEXT,
            title TEXT NOT NULL,
            instruction TEXT NOT NULL,
            schedule_kind TEXT NOT NULL,
            anchor_local TEXT NOT NULL,
            timezone_id TEXT NOT NULL,
            missed_policy TEXT NOT NULL,
            status TEXT NOT NULL,
            next_run_at_ms INTEGER,
            last_run_at_ms INTEGER,
            latest_result TEXT,
            last_provider_id TEXT,
            last_model_id TEXT,
            last_input_tokens INTEGER,
            last_output_tokens INTEGER,
            last_charge_micros INTEGER,
            last_currency_code TEXT,
            last_safe_error_code TEXT,
            created_at_ms INTEGER NOT NULL,
            updated_at_ms INTEGER NOT NULL
        );
        CREATE INDEX IF NOT EXISTS desktop_reminder_plans_due_v1 ON desktop_reminder_plans_v1(status,next_run_at_ms);
        CREATE TABLE IF NOT EXISTS desktop_reminder_runs_v1 (
            run_id TEXT PRIMARY KEY NOT NULL,
            plan_id TEXT NOT NULL REFERENCES desktop_reminder_plans_v1(plan_id) ON DELETE CASCADE,
            attempt_id TEXT NOT NULL UNIQUE,
            scheduled_at_ms INTEGER NOT NULL,
            started_at_ms INTEGER NOT NULL,
            completed_at_ms INTEGER,
            status TEXT NOT NULL,
            result TEXT,
            provider_id TEXT,
            requested_model_id TEXT,
            actual_model_id TEXT,
            input_tokens INTEGER,
            output_tokens INTEGER,
            cached_input_tokens INTEGER,
            charge_micros INTEGER,
            currency_code TEXT,
            safe_error_code TEXT,
            notification_state TEXT NOT NULL,
            notification_safe_code TEXT
        );
        CREATE INDEX IF NOT EXISTS desktop_reminder_runs_plan_v1 ON desktop_reminder_runs_v1(plan_id,started_at_ms DESC);
        CREATE INDEX IF NOT EXISTS desktop_reminder_runs_notification_v1 ON desktop_reminder_runs_v1(notification_state,completed_at_ms);"
    ).map_err(|_| "Desktop 提醒 SQLite migration 失败".to_owned())
}

pub fn recover_interrupted(connection: &Connection, now_ms: i64) -> Result<(), String> {
    in_reminder_transaction(connection,|connection|{
        connection.execute("UPDATE desktop_reminder_drafts_v1 SET status='UNKNOWN',safe_error_code='PROCESS_INTERRUPTED',updated_at_ms=?1 WHERE status='RUNNING'",[now_ms]).map_err(|_|"提醒草案中断状态无法恢复".to_owned())?;
        // Read the precise still-running run before closing it. Other terminal
        // runs and manually paused/deleted plans cannot supply this projection.
        connection.execute("UPDATE desktop_reminder_plans_v1 SET status='UNKNOWN',next_run_at_ms=NULL,last_run_at_ms=?1,last_safe_error_code='PROCESS_INTERRUPTED',updated_at_ms=?1,
            (last_provider_id,last_model_id,last_input_tokens,last_output_tokens,last_charge_micros,last_currency_code)=
            (SELECT provider_id,COALESCE(actual_model_id,requested_model_id),input_tokens,output_tokens,charge_micros,CASE WHEN charge_micros IS NULL THEN NULL ELSE currency_code END FROM desktop_reminder_runs_v1 r WHERE r.plan_id=desktop_reminder_plans_v1.plan_id AND r.status='RUNNING' ORDER BY r.started_at_ms DESC,r.run_id DESC LIMIT 1)
            WHERE status='RUNNING'",[now_ms]).map_err(|_|"计划中断投影无法恢复".to_owned())?;
        connection.execute("UPDATE desktop_reminder_runs_v1 SET status='UNKNOWN',completed_at_ms=?1,safe_error_code='PROCESS_INTERRUPTED',notification_state='SUPPRESSED',notification_safe_code='UNKNOWN' WHERE status='RUNNING'",[now_ms]).map_err(|_|"计划运行中断状态无法恢复".to_owned())?;
        Ok(())
    })
}

pub fn explicit_future_intent(text: &str) -> bool {
    let normalized = text.to_lowercase().split_whitespace().collect::<String>();
    let phrases = [
        "提醒我",
        "提醒一下",
        "给我提醒",
        "帮我监控",
        "持续监控",
        "持续跟踪",
        "帮我跟踪",
        "持续关注",
        "有变化告诉我",
        "有消息告诉我",
        "定期告诉我",
        "每天告诉我",
        "每周告诉我",
        "每小时告诉我",
        "到时候告诉我",
        "到时提醒我",
        "明天提醒我",
        "下周提醒我",
    ];
    phrases.iter().any(|phrase| normalized.contains(phrase))
        && normalized
            .chars()
            .filter(|value| value.is_alphanumeric())
            .count()
            >= 6
}

pub fn reserve_generated_draft(
    connection: &mut Connection,
    args: &GenerateDraftArgs,
    user_text: &str,
    assistant_text: &str,
    now_ms: i64,
) -> Result<DraftReservation, String> {
    validate_id(&args.workspace_id)?;
    validate_id(&args.conversation_id)?;
    validate_id(&args.user_message_id)?;
    validate_id(&args.assistant_message_id)?;
    validate_timezone(&args.timezone_id)?;
    if !explicit_future_intent(user_text) {
        return Err("这段对话没有明确的未来提醒或持续监控意图".to_owned());
    }
    let user_text = bounded_source(user_text)?;
    let assistant_text = bounded_source(assistant_text)?;
    let source_hash = hash(format!("{}\n{}", user_text, assistant_text).as_bytes());
    if let Some(existing) = connection.query_row(
        "SELECT draft_id FROM desktop_reminder_drafts_v1 WHERE workspace_id=?1 AND conversation_id=?2 AND source_hash=?3 AND status IN ('RUNNING','PENDING_REVIEW','CONFIRMED') ORDER BY created_at_ms DESC LIMIT 1",
        params![args.workspace_id,args.conversation_id,source_hash],
        |row| row.get::<_,String>(0),
    ).optional().map_err(|_| "提醒草案无法核对".to_owned())? {
        return Err(format!("这段对话已有提醒草案：{}", &existing[..existing.len().min(16)]));
    }
    let draft_id = unique_id("reminder-draft", now_ms, &source_hash);
    let attempt_id = unique_id("reminder-refinement", now_ms, &draft_id);
    let anchor_local = default_anchor_local(&args.timezone_id, now_ms)?;
    let transaction = connection
        .transaction()
        .map_err(|_| "提醒草案无法开始保存".to_owned())?;
    transaction.execute(
        "INSERT INTO desktop_reminder_drafts_v1(draft_id,workspace_id,conversation_id,source_user_message_id,source_assistant_message_id,source_hash,attempt_id,status,title,instruction,schedule_kind,anchor_local,timezone_id,missed_policy,retry_count,created_at_ms,updated_at_ms) VALUES(?1,?2,?3,?4,?5,?6,?7,'RUNNING','','','DAILY',?8,?9,'RUN_ONCE',0,?10,?10)",
        params![draft_id,args.workspace_id,args.conversation_id,args.user_message_id,args.assistant_message_id,source_hash,attempt_id,anchor_local,args.timezone_id,now_ms],
    ).map_err(|_| "提醒草案未保存".to_owned())?;
    transaction
        .commit()
        .map_err(|_| "提醒草案未提交".to_owned())?;
    Ok(DraftReservation {
        draft_id,
        attempt_id,
        workspace_id: args.workspace_id.clone(),
        conversation_id: args.conversation_id.clone(),
        user_message_id: args.user_message_id.clone(),
        assistant_message_id: args.assistant_message_id.clone(),
        user_text,
        assistant_text,
        timezone_id: args.timezone_id.clone(),
        retry_count: 0,
    })
}

pub fn reserve_draft_retry(
    connection: &mut Connection,
    draft_id: &str,
    user_text: &str,
    assistant_text: &str,
    now_ms: i64,
) -> Result<DraftReservation, String> {
    validate_id(draft_id)?;
    let draft = read_draft(connection, draft_id)?;
    if !matches!(draft.status.as_str(), "FAILED" | "UNKNOWN") {
        return Err("只有失败或结果未知的草案才能显式重试".to_owned());
    }
    let user_message_id = draft
        .source_user_message_id
        .clone()
        .ok_or_else(|| "提醒草案缺少来源消息".to_owned())?;
    let assistant_message_id = draft
        .source_assistant_message_id
        .clone()
        .ok_or_else(|| "提醒草案缺少来源回复".to_owned())?;
    let conversation_id = draft
        .conversation_id
        .clone()
        .ok_or_else(|| "提醒草案缺少来源对话".to_owned())?;
    let user_text = bounded_source(user_text)?;
    let assistant_text = bounded_source(assistant_text)?;
    let source_hash = hash(format!("{}\n{}", user_text, assistant_text).as_bytes());
    let stored_hash: Option<String> = connection
        .query_row(
            "SELECT source_hash FROM desktop_reminder_drafts_v1 WHERE draft_id=?1",
            [draft_id],
            |row| row.get(0),
        )
        .map_err(|_| "提醒草案无法核对".to_owned())?;
    if stored_hash.as_deref() != Some(source_hash.as_str()) {
        return Err("来源对话已变化；为避免用新内容重放旧草案，已停止".to_owned());
    }
    let retry_count = draft.retry_count + 1;
    let attempt_id = unique_id(
        "reminder-refinement",
        now_ms,
        &format!("{draft_id}-{retry_count}"),
    );
    connection.execute(
        "UPDATE desktop_reminder_drafts_v1 SET attempt_id=?2,status='RUNNING',provider_id=NULL,requested_model_id=NULL,actual_model_id=NULL,input_tokens=NULL,output_tokens=NULL,cached_input_tokens=NULL,charge_micros=NULL,currency_code=NULL,safe_error_code=NULL,retry_count=?3,updated_at_ms=?4 WHERE draft_id=?1 AND status IN ('FAILED','UNKNOWN')",
        params![draft_id,attempt_id,retry_count,now_ms],
    ).map_err(|_| "提醒草案重试未保存".to_owned())?;
    Ok(DraftReservation {
        draft_id: draft_id.to_owned(),
        attempt_id,
        workspace_id: draft.workspace_id,
        conversation_id,
        user_message_id,
        assistant_message_id,
        user_text,
        assistant_text,
        timezone_id: draft.timezone_id,
        retry_count,
    })
}

pub fn create_manual_draft(
    connection: &Connection,
    args: &ManualDraftArgs,
    now_ms: i64,
) -> Result<Projection, String> {
    validate_id(&args.workspace_id)?;
    if let Some(value) = args.conversation_id.as_deref() {
        validate_id(value)?;
    }
    validate_timezone(&args.timezone_id)?;
    let draft_id = unique_id(
        "reminder-draft",
        now_ms,
        &format!("{}-manual", args.workspace_id),
    );
    connection.execute(
        "INSERT INTO desktop_reminder_drafts_v1(draft_id,workspace_id,conversation_id,status,title,instruction,schedule_kind,anchor_local,timezone_id,missed_policy,retry_count,created_at_ms,updated_at_ms) VALUES(?1,?2,?3,'PENDING_REVIEW','新的监控','','ONCE',?4,?5,'RUN_ONCE',0,?6,?6)",
        params![draft_id,args.workspace_id,args.conversation_id,default_anchor_local(&args.timezone_id,now_ms)?,args.timezone_id,now_ms],
    ).map_err(|_|"提醒草案未保存".to_owned())?;
    read_projection(connection)
}

/// Restores only portable, already-confirmed plan fields from encrypted account sync.
/// Active remote plans are deliberately restored paused so a second device cannot duplicate
/// monitoring work before the user explicitly resumes it.
pub fn restore_portable_plan(
    transaction: &Transaction<'_>,
    args: &PortablePlanArgs,
    now_ms: i64,
) -> Result<String, String> {
    validate_id(&args.workspace_id)?;
    validate_id(&args.conversation_id)?;
    if args.source_key.is_empty() || args.source_key.len() > 128 {
        return Err("云端提醒标识无效".to_owned());
    }
    validate_schedule(
        &args.schedule_kind,
        &args.anchor_local,
        &args.timezone_id,
        &args.missed_policy,
    )?;
    validate_title_instruction(&args.title, &args.instruction)?;
    let title = args.title.trim().to_owned();
    let instruction = args.instruction.trim().to_owned();
    let status = match args.source_status.as_str() {
        "COMPLETED" => "COMPLETED",
        "ACTIVE" | "PAUSED" => "PAUSED",
        "FAILED" | "UNKNOWN" => "UNKNOWN",
        _ => return Err("云端提醒状态无效".to_owned()),
    };
    let stable_seed = format!("{}:{}", args.conversation_id, args.source_key);
    let draft_id = unique_id("reminder-draft-cloud", 0, &stable_seed);
    let plan_id = unique_id("reminder-plan-cloud", 0, &stable_seed);
    if transaction
        .query_row(
            "SELECT EXISTS(SELECT 1 FROM desktop_reminder_plans_v1 WHERE plan_id=?1)",
            [&plan_id],
            |row| row.get::<_, i64>(0),
        )
        .unwrap_or(0)
        != 0
    {
        return Ok(plan_id);
    }
    transaction.execute(
        "INSERT INTO desktop_reminder_drafts_v1(draft_id,workspace_id,conversation_id,status,title,instruction,schedule_kind,anchor_local,timezone_id,missed_policy,safe_error_code,retry_count,created_at_ms,updated_at_ms) VALUES(?1,?2,?3,'CONFIRMED',?4,?5,?6,?7,?8,?9,'CLOUD_RESTORED',0,?10,?10)",
        params![draft_id,args.workspace_id,args.conversation_id,title,instruction,args.schedule_kind,args.anchor_local,args.timezone_id,args.missed_policy,now_ms],
    ).map_err(|_|"云端提醒草案未恢复".to_owned())?;
    transaction.execute(
        "INSERT INTO desktop_reminder_plans_v1(plan_id,source_draft_id,workspace_id,conversation_id,title,instruction,schedule_kind,anchor_local,timezone_id,missed_policy,status,next_run_at_ms,last_safe_error_code,created_at_ms,updated_at_ms) VALUES(?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,?11,?12,?13,?14,?14)",
        params![plan_id,draft_id,args.workspace_id,args.conversation_id,title,instruction,args.schedule_kind,args.anchor_local,args.timezone_id,args.missed_policy,status,if status=="COMPLETED"{None}else{args.next_run_at_ms},if status=="PAUSED"{Some("CLOUD_RESTORED_PAUSED")}else if status=="UNKNOWN"{Some("CLOUD_RESTORED_UNKNOWN")}else{None},now_ms],
    ).map_err(|_|"云端提醒计划未恢复".to_owned())?;
    Ok(plan_id)
}

pub fn prompt(reservation: &DraftReservation, now_ms: i64) -> Value {
    json!([
        {"role":"system","content":format!("你只把用户明确提出的未来提醒或持续监控意图整理为可编辑草案。不得创建计划，不得补入未出现的对象、条件或频率。当前 UTC 毫秒为 {now_ms}，用户时区为 {}。只返回严格 JSON：{{\"eligible\":true,\"title\":\"...\",\"instruction\":\"...\",\"scheduleKind\":\"ONCE|HOURLY|DAILY|WEEKLY\",\"anchorLocal\":\"YYYY-MM-DDTHH:MM\",\"missedPolicy\":\"RUN_ONCE|SKIP\"}}；不符合则 eligible=false。",reservation.timezone_id)},
        {"role":"user","content":format!("用户发言：\n{}\n\n南枫AI回答：\n{}",reservation.user_text,reservation.assistant_text)}
    ])
}

pub fn complete_draft(
    connection: &Connection,
    draft_id: &str,
    completion: Completion<'_>,
    now_ms: i64,
) -> Result<DraftProjection, String> {
    let parsed = parse_refinement(completion.text)?;
    let (status, title, instruction, schedule_kind, anchor_local, missed_policy) = match parsed {
        ParsedRefinement::NotEligible => (
            "NOT_ELIGIBLE",
            "".into(),
            "".into(),
            "DAILY".into(),
            read_draft(connection, draft_id)?.anchor_local,
            "RUN_ONCE".into(),
        ),
        ParsedRefinement::Draft {
            title,
            instruction,
            schedule_kind,
            anchor_local,
            missed_policy,
        } => (
            "PENDING_REVIEW",
            title,
            instruction,
            schedule_kind,
            anchor_local,
            missed_policy,
        ),
    };
    connection.execute(
        "UPDATE desktop_reminder_drafts_v1 SET status=?2,title=?3,instruction=?4,schedule_kind=?5,anchor_local=?6,missed_policy=?7,provider_id=?8,requested_model_id=?9,actual_model_id=?10,input_tokens=?11,output_tokens=?12,cached_input_tokens=?13,charge_micros=?14,currency_code=CASE WHEN ?14 IS NULL THEN NULL ELSE ?16 END,safe_error_code=NULL,updated_at_ms=?15 WHERE draft_id=?1 AND status='RUNNING'",
        params![draft_id,status,title,instruction,schedule_kind,anchor_local,missed_policy,completion.provider_id,completion.requested_model_id,completion.actual_model_id,completion.input_tokens,completion.output_tokens,completion.cached_input_tokens,completion.charge_micros,now_ms,crate::chat_call_accounting_v1::provider_billing_currency(completion.provider_id)],
    ).map_err(|_|"提醒草案结果未保存".to_owned())?;
    read_draft(connection, draft_id)
}

pub fn fail_draft_reported(connection: &Connection, draft_id: &str, safe_code: &str, completion: Completion<'_>, now_ms: i64) -> Result<DraftProjection,String> { fail_draft_reported_outcome(connection,draft_id,safe_code,completion,now_ms,false) }

pub fn unknown_draft_reported(connection: &Connection, draft_id: &str, safe_code: &str, completion: Completion<'_>, now_ms: i64) -> Result<DraftProjection,String> { fail_draft_reported_outcome(connection,draft_id,safe_code,completion,now_ms,true) }

fn fail_draft_reported_outcome(connection: &Connection, draft_id: &str, safe_code: &str, completion: Completion<'_>, now_ms: i64, unknown:bool) -> Result<DraftProjection,String> {
    connection.execute("UPDATE desktop_reminder_drafts_v1 SET status=?11,provider_id=?2,requested_model_id=?3,actual_model_id=?4,input_tokens=?5,output_tokens=?6,cached_input_tokens=?7,charge_micros=?8,currency_code=CASE WHEN ?8 IS NULL THEN NULL ELSE ?12 END,safe_error_code=?9,updated_at_ms=?10 WHERE draft_id=?1 AND status='RUNNING'",params![draft_id,completion.provider_id,completion.requested_model_id,completion.actual_model_id,completion.input_tokens,completion.output_tokens,completion.cached_input_tokens,completion.charge_micros,safe_code,now_ms,if unknown {"UNKNOWN"} else {"FAILED"},crate::chat_call_accounting_v1::provider_billing_currency(completion.provider_id)]).map_err(|_| "提醒草案失败计量未保存".to_owned())?;
    read_draft(connection, draft_id)
}

pub fn fail_draft(
    connection: &Connection,
    draft_id: &str,
    provider_id: Option<&str>,
    requested_model_id: Option<&str>,
    unknown: bool,
    safe_code: &str,
    now_ms: i64,
) -> Result<DraftProjection, String> {
    connection.execute(
        "UPDATE desktop_reminder_drafts_v1 SET status=?2,provider_id=?3,requested_model_id=?4,safe_error_code=?5,updated_at_ms=?6 WHERE draft_id=?1 AND status='RUNNING'",
        params![draft_id,if unknown{"UNKNOWN"}else{"FAILED"},provider_id,requested_model_id,safe_code,now_ms],
    ).map_err(|_|"提醒草案失败状态未保存".to_owned())?;
    read_draft(connection, draft_id)
}

pub fn reject_draft(
    connection: &Connection,
    draft_id: &str,
    now_ms: i64,
) -> Result<Projection, String> {
    let changed=connection.execute("UPDATE desktop_reminder_drafts_v1 SET status='REJECTED',updated_at_ms=?2 WHERE draft_id=?1 AND status='PENDING_REVIEW'",params![draft_id,now_ms]).map_err(|_|"提醒草案拒绝状态未保存".to_owned())?;
    if changed != 1 {
        return Err("提醒草案已变化；未创建计划".to_owned());
    }
    read_projection(connection)
}

pub fn confirm_draft(
    connection: &mut Connection,
    args: &ConfirmDraftArgs,
    now_ms: i64,
) -> Result<Projection, String> {
    validate_id(&args.draft_id)?;
    validate_title_instruction(&args.title, &args.instruction)?;
    validate_schedule(
        &args.schedule_kind,
        &args.anchor_local,
        &args.timezone_id,
        &args.missed_policy,
    )?;
    let next_run_at_ms = first_occurrence_ms(
        &args.schedule_kind,
        &args.anchor_local,
        &args.timezone_id,
        now_ms,
    )?;
    let transaction = connection
        .transaction()
        .map_err(|_| "提醒计划无法开始保存".to_owned())?;
    let draft: DraftProjection = read_draft(&transaction, &args.draft_id)?;
    if draft.status != "PENDING_REVIEW" {
        return Err("只有待审阅草案可以确认".to_owned());
    }
    let plan_id = unique_id("reminder-plan", now_ms, &args.draft_id);
    transaction.execute(
        "INSERT INTO desktop_reminder_plans_v1(plan_id,source_draft_id,workspace_id,conversation_id,title,instruction,schedule_kind,anchor_local,timezone_id,missed_policy,status,next_run_at_ms,created_at_ms,updated_at_ms) VALUES(?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,'ACTIVE',?11,?12,?12)",
        params![plan_id,args.draft_id,draft.workspace_id,draft.conversation_id,args.title.trim(),args.instruction.trim(),args.schedule_kind,args.anchor_local,args.timezone_id,args.missed_policy,next_run_at_ms,now_ms],
    ).map_err(|_|"提醒计划未保存".to_owned())?;
    transaction.execute("UPDATE desktop_reminder_drafts_v1 SET status='CONFIRMED',title=?2,instruction=?3,schedule_kind=?4,anchor_local=?5,timezone_id=?6,missed_policy=?7,updated_at_ms=?8 WHERE draft_id=?1",params![args.draft_id,args.title.trim(),args.instruction.trim(),args.schedule_kind,args.anchor_local,args.timezone_id,args.missed_policy,now_ms]).map_err(|_|"提醒草案确认状态未保存".to_owned())?;
    transaction
        .commit()
        .map_err(|_| "提醒计划未提交".to_owned())?;
    read_projection(connection)
}

pub fn set_paused(
    connection: &Connection,
    plan_id: &str,
    paused: bool,
    now_ms: i64,
) -> Result<Projection, String> {
    validate_id(plan_id)?;
    let current = read_plan(connection, plan_id)?;
    let (status, next) = if paused {
        ("PAUSED", current.next_run_at_ms)
    } else {
        (
            "ACTIVE",
            Some(first_occurrence_ms(
                &current.schedule_kind,
                &current.anchor_local,
                &current.timezone_id,
                now_ms,
            )?),
        )
    };
    let changed=connection.execute("UPDATE desktop_reminder_plans_v1 SET status=?2,next_run_at_ms=?3,updated_at_ms=?4 WHERE plan_id=?1 AND status IN ('ACTIVE','RUNNING','PAUSED')",params![plan_id,status,next,now_ms]).map_err(|_|"提醒计划状态未保存".to_owned())?;
    if changed != 1 {
        return Err("提醒计划当前不能暂停或恢复".to_owned());
    }
    read_projection(connection)
}

pub fn update_plan(
    connection: &mut Connection,
    args: &UpdatePlanArgs,
    now_ms: i64,
) -> Result<Projection, String> {
    validate_id(&args.plan_id)?;
    validate_title_instruction(&args.title, &args.instruction)?;
    validate_schedule(
        &args.schedule_kind,
        &args.anchor_local,
        &args.timezone_id,
        &args.missed_policy,
    )?;
    let next_run_at_ms = first_occurrence_ms(
        &args.schedule_kind,
        &args.anchor_local,
        &args.timezone_id,
        now_ms,
    )?;
    let transaction = connection
        .transaction()
        .map_err(|_| "提醒计划无法开始更新".to_owned())?;
    let current = read_plan(&transaction, &args.plan_id)?;
    if !matches!(current.status.as_str(), "ACTIVE" | "PAUSED") {
        return Err("只有运行中或已暂停的计划可以编辑".to_owned());
    }
    if current.updated_at_ms != args.expected_updated_at_ms {
        return Err("提醒计划已变化；请重新打开后编辑".to_owned());
    }
    let changed = transaction.execute(
        "UPDATE desktop_reminder_plans_v1 SET title=?2,instruction=?3,schedule_kind=?4,anchor_local=?5,timezone_id=?6,missed_policy=?7,next_run_at_ms=?8,updated_at_ms=?9 WHERE plan_id=?1 AND updated_at_ms=?10 AND status IN ('ACTIVE','PAUSED')",
        params![args.plan_id,args.title.trim(),args.instruction.trim(),args.schedule_kind,args.anchor_local,args.timezone_id,args.missed_policy,next_run_at_ms,now_ms,args.expected_updated_at_ms],
    ).map_err(|_|"提醒计划未更新".to_owned())?;
    if changed != 1 {
        return Err("提醒计划已变化；请重新打开后编辑".to_owned());
    }
    transaction.execute(
        "UPDATE desktop_reminder_drafts_v1 SET title=?2,instruction=?3,schedule_kind=?4,anchor_local=?5,timezone_id=?6,missed_policy=?7,updated_at_ms=?8 WHERE draft_id=?1 AND status='CONFIRMED'",
        params![current.source_draft_id,args.title.trim(),args.instruction.trim(),args.schedule_kind,args.anchor_local,args.timezone_id,args.missed_policy,now_ms],
    ).map_err(|_|"提醒计划来源草案未同步".to_owned())?;
    transaction
        .commit()
        .map_err(|_| "提醒计划更新未提交".to_owned())?;
    read_projection(connection)
}

pub fn retry_plan(
    connection: &Connection,
    plan_id: &str,
    now_ms: i64,
) -> Result<Projection, String> {
    validate_id(plan_id)?;
    let changed=connection.execute("UPDATE desktop_reminder_plans_v1 SET status='ACTIVE',next_run_at_ms=?2,last_safe_error_code=NULL,updated_at_ms=?2 WHERE plan_id=?1 AND status IN ('FAILED','UNKNOWN')",params![plan_id,now_ms]).map_err(|_|"提醒计划重试状态未保存".to_owned())?;
    if changed != 1 {
        return Err("只有失败或结果未知的计划才能显式重试".to_owned());
    }
    read_projection(connection)
}

pub fn delete_plan(connection: &Connection, plan_id: &str) -> Result<Projection, String> {
    validate_id(plan_id)?;
    // Keep only the identity and measured terminal facts needed by the immutable
    // usage ledger. Removing the FK parent would cascade those facts away.
    let transaction = connection.unchecked_transaction()
        .map_err(|_| "提醒删除事务未开始".to_owned())?;
    let changed = transaction.execute(
        "UPDATE desktop_reminder_plans_v1 SET status='DELETED',title='',instruction='',anchor_local='',schedule_kind='ONCE',timezone_id='UTC',missed_policy='SKIP',next_run_at_ms=NULL,latest_result=NULL WHERE plan_id=?1 AND status!='DELETED'",
        [plan_id],
    ).map_err(|_| "提醒计划未删除".to_owned())?;
    if changed != 1 { return Err("提醒计划已不存在".to_owned()); }
    transaction.execute(
        "UPDATE desktop_reminder_runs_v1 SET result=NULL,status=CASE WHEN status='RUNNING' THEN 'CANCELLED' ELSE status END,safe_error_code=CASE WHEN status='RUNNING' THEN 'PLAN_DELETED' ELSE safe_error_code END,notification_state='SUPPRESSED',notification_safe_code='PLAN_DELETED' WHERE plan_id=?1",
        [plan_id],
    ).map_err(|_| "提醒删除未保留调用事实".to_owned())?;
    transaction.execute(
        "UPDATE desktop_reminder_drafts_v1 SET title='',instruction='',anchor_local='',schedule_kind='ONCE',timezone_id='UTC',missed_policy='SKIP',source_hash=NULL,source_user_message_id=NULL,source_assistant_message_id=NULL WHERE draft_id=(SELECT source_draft_id FROM desktop_reminder_plans_v1 WHERE plan_id=?1)",
        [plan_id],
    ).map_err(|_| "提醒删除未清除草案正文".to_owned())?;
    transaction.commit().map_err(|_| "提醒删除事务未提交".to_owned())?;
    read_projection(connection)
}

pub fn claim_due(
    connection: &mut Connection,
    now_ms: i64,
) -> Result<Option<RunReservation>, String> {
    reconcile_missed(connection, now_ms)?;
    let transaction = connection
        .transaction()
        .map_err(|_| "计划调度无法开始".to_owned())?;
    let plan_id:Option<String>=transaction.query_row("SELECT plan_id FROM desktop_reminder_plans_v1 WHERE status='ACTIVE' AND next_run_at_ms IS NOT NULL AND next_run_at_ms<=?1 ORDER BY next_run_at_ms,created_at_ms LIMIT 1",[now_ms],|row|row.get(0)).optional().map_err(|_|"到期计划无法读取".to_owned())?;
    let Some(plan_id) = plan_id else {
        transaction
            .commit()
            .map_err(|_| "计划调度无法结束".to_owned())?;
        return Ok(None);
    };
    let plan = read_plan(&transaction, &plan_id)?;
    let scheduled_at_ms = plan
        .next_run_at_ms
        .ok_or_else(|| "到期计划缺少运行时间".to_owned())?;
    let run_id = unique_id("reminder-run", now_ms, &plan_id);
    let attempt_id = unique_id("reminder-monitor", now_ms, &run_id);
    transaction.execute("UPDATE desktop_reminder_plans_v1 SET status='RUNNING',updated_at_ms=?2 WHERE plan_id=?1 AND status='ACTIVE'",params![plan_id,now_ms]).map_err(|_|"计划运行状态未保存".to_owned())?;
    transaction.execute("INSERT INTO desktop_reminder_runs_v1(run_id,plan_id,attempt_id,scheduled_at_ms,started_at_ms,status,notification_state) VALUES(?1,?2,?3,?4,?5,'RUNNING','NONE')",params![run_id,plan_id,attempt_id,scheduled_at_ms,now_ms]).map_err(|_|"计划运行记录未保存".to_owned())?;
    transaction
        .commit()
        .map_err(|_| "计划运行未提交".to_owned())?;
    Ok(Some(RunReservation {
        run_id,
        attempt_id,
        plan,
        scheduled_at_ms,
    }))
}

pub fn run_prompt(reservation: &RunReservation, now_ms: i64) -> Value {
    json!([
        {"role":"system","content":format!("当前 UTC 毫秒为 {now_ms}。这是用户已审阅确认的计划监控。只检查任务要求直接相关的公开最新资料，优先一手来源；先说明本次是否有重要变化，再列出可核验来源。无法核验必须明确说明，不得编造。")},
        {"role":"user","content":format!("计划名称：{}\n\n监控要求：{}",reservation.plan.title,reservation.plan.instruction)}
    ])
}

pub fn complete_run(
    connection: &Connection,
    reservation: &RunReservation,
    completion: Completion<'_>,
    now_ms: i64,
) -> Result<PlanProjection, String> {
    in_reminder_transaction(connection,|connection|{
    require_current_run(connection,reservation)?;
    let current = read_plan(connection, &reservation.plan.plan_id)?;
    let (status, next) = next_after_terminal(&current, now_ms, true)?;
    connection.execute("UPDATE desktop_reminder_runs_v1 SET completed_at_ms=?2,status='SUCCEEDED',result=?3,provider_id=?4,requested_model_id=?5,actual_model_id=?6,input_tokens=?7,output_tokens=?8,cached_input_tokens=?9,charge_micros=?10,currency_code=CASE WHEN ?10 IS NULL THEN NULL ELSE 'USD' END,safe_error_code=NULL,notification_state='PENDING' WHERE run_id=?1 AND status='RUNNING'",params![reservation.run_id,now_ms,completion.text,completion.provider_id,completion.requested_model_id,completion.actual_model_id,completion.input_tokens,completion.output_tokens,completion.cached_input_tokens,completion.charge_micros]).map_err(|_|"计划结果未保存".to_owned())?;
    connection.execute("UPDATE desktop_reminder_plans_v1 SET status=?2,next_run_at_ms=?3,last_run_at_ms=?4,latest_result=?5,last_provider_id=?6,last_model_id=?7,last_input_tokens=?8,last_output_tokens=?9,last_charge_micros=?10,last_currency_code=CASE WHEN ?10 IS NULL THEN NULL ELSE 'USD' END,last_safe_error_code=NULL,updated_at_ms=?4 WHERE plan_id=?1",params![reservation.plan.plan_id,status,next,now_ms,completion.text,completion.provider_id,completion.actual_model_id.unwrap_or(completion.requested_model_id),completion.input_tokens,completion.output_tokens,completion.charge_micros]).map_err(|_|"计划投影未更新".to_owned())?;
    read_plan(connection, &reservation.plan.plan_id)
    })
}

pub fn fail_run_reported(connection: &mut Connection, reservation: &RunReservation, safe_code: &str, completion: Completion<'_>, now_ms: i64) -> Result<PlanProjection,String> { fail_run_reported_outcome(connection,reservation,safe_code,completion,now_ms,false) }

pub fn unknown_run_reported(connection: &mut Connection, reservation: &RunReservation, safe_code: &str, completion: Completion<'_>, now_ms: i64) -> Result<PlanProjection,String> { fail_run_reported_outcome(connection,reservation,safe_code,completion,now_ms,true) }

fn fail_run_reported_outcome(connection: &mut Connection, reservation: &RunReservation, safe_code: &str, completion: Completion<'_>, now_ms: i64, unknown:bool) -> Result<PlanProjection,String> {
    let transaction = connection.transaction().map_err(|_| "计划失败计量事务未开始".to_owned())?;
    transaction.execute("UPDATE desktop_reminder_runs_v1 SET actual_model_id=?2,input_tokens=?3,output_tokens=?4,cached_input_tokens=?5,charge_micros=?6,currency_code=CASE WHEN ?6 IS NULL THEN NULL ELSE 'USD' END WHERE run_id=?1 AND status='RUNNING'",params![reservation.run_id,completion.actual_model_id,completion.input_tokens,completion.output_tokens,completion.cached_input_tokens,completion.charge_micros]).map_err(|_| "计划失败计量未保存".to_owned())?;
    fail_run(&transaction, reservation, Some(completion.provider_id), Some(completion.requested_model_id), unknown, safe_code, now_ms)?;
    transaction.execute("UPDATE desktop_reminder_plans_v1 SET last_model_id=?2,last_input_tokens=?3,last_output_tokens=?4,last_charge_micros=?5,last_currency_code=CASE WHEN ?5 IS NULL THEN NULL ELSE 'USD' END WHERE plan_id=?1",params![reservation.plan.plan_id,completion.actual_model_id.unwrap_or(completion.requested_model_id),completion.input_tokens,completion.output_tokens,completion.charge_micros]).map_err(|_| "计划失败计量投影未保存".to_owned())?;
    transaction.commit().map_err(|_| "计划失败计量事务未提交".to_owned())?;
    read_plan(connection, &reservation.plan.plan_id)
}

pub fn fail_run(
    connection: &Connection,
    reservation: &RunReservation,
    provider_id: Option<&str>,
    requested_model_id: Option<&str>,
    unknown: bool,
    safe_code: &str,
    now_ms: i64,
) -> Result<PlanProjection, String> {
    in_reminder_transaction(connection,|connection|{
    require_current_run(connection,reservation)?;
    let current = read_plan(connection, &reservation.plan.plan_id)?;
    let (status, next) = if unknown {
        ("UNKNOWN".to_owned(), None)
    } else {
        next_after_terminal(&current, now_ms, false)?
    };
    connection.execute("UPDATE desktop_reminder_runs_v1 SET completed_at_ms=?2,status=?3,provider_id=?4,requested_model_id=?5,safe_error_code=?6,notification_state='SUPPRESSED',notification_safe_code=?7 WHERE run_id=?1 AND status='RUNNING'",params![reservation.run_id,now_ms,if unknown{"UNKNOWN"}else{"FAILED"},provider_id,requested_model_id,safe_code,if unknown{"UNKNOWN"}else{"FAILED"}]).map_err(|_|"计划失败状态未保存".to_owned())?;
    connection.execute("UPDATE desktop_reminder_plans_v1 SET status=?2,next_run_at_ms=?3,last_run_at_ms=?4,last_provider_id=?5,last_model_id=?6,last_safe_error_code=?7,updated_at_ms=?4 WHERE plan_id=?1",params![reservation.plan.plan_id,status,next,now_ms,provider_id,requested_model_id,safe_code]).map_err(|_|"计划失败投影未保存".to_owned())?;
    read_plan(connection, &reservation.plan.plan_id)
    })
}

pub fn pending_notifications(
    connection: &Connection,
) -> Result<Vec<NotificationProjection>, String> {
    let mut statement=connection.prepare("SELECT run.run_id,plan.plan_id,plan.workspace_id,plan.conversation_id,plan.title FROM desktop_reminder_runs_v1 run JOIN desktop_reminder_plans_v1 plan ON plan.plan_id=run.plan_id WHERE plan.status!='DELETED' AND run.notification_state='PENDING' AND run.status='SUCCEEDED' ORDER BY run.completed_at_ms LIMIT 20").map_err(|_|"待发送通知无法读取".to_owned())?;
    let notifications = statement
        .query_map([], |row| {
            Ok(NotificationProjection {
                run_id: row.get(0)?,
                plan_id: row.get(1)?,
                workspace_id: row.get(2)?,
                conversation_id: row.get(3)?,
                title: safe_notification_title(row.get::<_, String>(4)?),
                body: "计划监控已完成，点按查看本机结果。".into(),
            })
        })
        .map_err(|_| "待发送通知无法读取".to_owned())?
        .collect::<Result<Vec<_>, _>>()
        .map_err(|_| "待发送通知无效".to_owned())?;
    Ok(notifications)
}

pub fn resolve_notification_target(
    connection: &Connection,
    target: &crate::desktop_reminder_notification_v1::ReminderNotificationTarget,
) -> Result<Option<crate::desktop_reminder_notification_v1::ReminderNotificationTarget>, String> {
    if !crate::desktop_reminder_notification_v1::validate_target(target) {
        return Ok(None);
    }
    let stored = connection
        .query_row(
            "SELECT workspace_id,COALESCE(conversation_id,'') FROM desktop_reminder_plans_v1 WHERE plan_id=?1 AND status!='DELETED' LIMIT 1",
            [&target.plan_id],
            |row| Ok((row.get::<_, String>(0)?, row.get::<_, String>(1)?)),
        )
        .optional()
        .map_err(|_| "提醒通知目标无法读取".to_owned())?;
    let Some((workspace_id, conversation_id)) = stored else {
        return Ok(None);
    };
    if workspace_id != target.workspace_id || conversation_id != target.conversation_id {
        return Ok(None);
    }
    Ok(Some(target.clone()))
}

/// Resolve an opaque notification run back to the current local plan. The
/// operating system activation payload must never be the source of workspace
/// or conversation identity.
pub fn resolve_notification_run(
    connection: &Connection,
    run_id: &str,
) -> Result<Option<crate::desktop_reminder_notification_v1::ReminderNotificationTarget>, String> {
    if validate_id(run_id).is_err() {
        return Ok(None);
    }
    let target = connection
        .query_row(
            "SELECT plan.plan_id,plan.workspace_id,COALESCE(plan.conversation_id,'') \
             FROM desktop_reminder_runs_v1 run \
             JOIN desktop_reminder_plans_v1 plan ON plan.plan_id=run.plan_id \
             WHERE run.run_id=?1 AND plan.status!='DELETED' AND run.status='SUCCEEDED' \
               AND run.notification_state IN ('PENDING','SENT') LIMIT 1",
            [run_id],
            |row| {
                Ok(crate::desktop_reminder_notification_v1::ReminderNotificationTarget {
                    route: crate::desktop_reminder_notification_v1::ROUTE.into(),
                    plan_id: row.get(0)?,
                    workspace_id: row.get(1)?,
                    conversation_id: row.get(2)?,
                })
            },
        )
        .optional()
        .map_err(|_| "提醒通知运行目标无法读取".to_owned())?;
    Ok(target.filter(crate::desktop_reminder_notification_v1::validate_target))
}

pub fn acknowledge_notification(
    connection: &Connection,
    run_id: &str,
    sent: bool,
    safe_code: Option<&str>,
) -> Result<(), String> {
    validate_id(run_id)?;
    let changed=connection.execute("UPDATE desktop_reminder_runs_v1 SET notification_state=?2,notification_safe_code=?3 WHERE run_id=?1 AND notification_state='PENDING'",params![run_id,if sent{"SENT"}else{"SUPPRESSED"},safe_code]).map_err(|_|"通知回执未保存".to_owned())?;
    if changed != 1 {
        return Err("通知回执已处理或不存在".to_owned());
    }
    Ok(())
}

pub fn read_projection(connection: &Connection) -> Result<Projection, String> {
    let drafts = {
        let mut statement=connection.prepare("SELECT draft_id,workspace_id,conversation_id,source_user_message_id,source_assistant_message_id,status,title,instruction,schedule_kind,anchor_local,timezone_id,missed_policy,provider_id,requested_model_id,actual_model_id,input_tokens,output_tokens,cached_input_tokens,charge_micros,currency_code,safe_error_code,retry_count,created_at_ms,updated_at_ms FROM desktop_reminder_drafts_v1 WHERE NOT EXISTS(SELECT 1 FROM desktop_reminder_plans_v1 p WHERE p.source_draft_id=desktop_reminder_drafts_v1.draft_id AND p.status='DELETED') ORDER BY updated_at_ms DESC LIMIT 100").map_err(|_|"提醒草案无法读取".to_owned())?;
        let rows = statement
            .query_map([], draft_row)
            .map_err(|_| "提醒草案无法读取".to_owned())?
            .collect::<Result<Vec<_>, _>>()
            .map_err(|_| "提醒草案无效".to_owned())?;
        rows
    };
    let plans = {
        let mut statement = connection
            .prepare(&(PLAN_SELECT.to_owned() + " WHERE status!='DELETED' ORDER BY updated_at_ms DESC"))
            .map_err(|_| "提醒计划无法读取".to_owned())?;
        let rows = statement
            .query_map([], plan_row)
            .map_err(|_| "提醒计划无法读取".to_owned())?
            .collect::<Result<Vec<_>, _>>()
            .map_err(|_| "提醒计划无效".to_owned())?;
        rows
    };
    let diagnostics = drafts
        .iter()
        .filter_map(|draft| {
            draft
                .safe_error_code
                .as_ref()
                .map(|code| DiagnosticProjection {
                    kind: "提醒草案".into(),
                    title: "提醒草案整理".into(),
                    summary: safe_summary(code),
                    occurred_at_ms: draft.updated_at_ms,
                })
        })
        .chain(plans.iter().filter_map(|plan| {
            plan.last_safe_error_code
                .as_ref()
                .map(|code| DiagnosticProjection {
                    kind: "计划监控".into(),
                    title: plan.title.clone(),
                    summary: safe_summary(code),
                    occurred_at_ms: plan.updated_at_ms,
                })
        }))
        .take(100)
        .collect();
    Ok(Projection {
        drafts,
        plans,
        diagnostics,
    })
}

pub fn read_draft(connection: &Connection, draft_id: &str) -> Result<DraftProjection, String> {
    connection.query_row("SELECT draft_id,workspace_id,conversation_id,source_user_message_id,source_assistant_message_id,status,title,instruction,schedule_kind,anchor_local,timezone_id,missed_policy,provider_id,requested_model_id,actual_model_id,input_tokens,output_tokens,cached_input_tokens,charge_micros,currency_code,safe_error_code,retry_count,created_at_ms,updated_at_ms FROM desktop_reminder_drafts_v1 WHERE draft_id=?1",[draft_id],draft_row).optional().map_err(|_|"提醒草案无法读取".to_owned())?.ok_or_else(||"提醒草案不存在".to_owned())
}

pub fn read_plan(connection: &Connection, plan_id: &str) -> Result<PlanProjection, String> {
    connection
        .query_row(
            &(PLAN_SELECT.to_owned() + " WHERE plan_id=?1 AND status!='DELETED'"),
            [plan_id],
            plan_row,
        )
        .optional()
        .map_err(|_| "提醒计划无法读取".to_owned())?
        .ok_or_else(|| "提醒计划不存在".to_owned())
}

const PLAN_SELECT:&str="SELECT plan_id,source_draft_id,workspace_id,conversation_id,title,instruction,schedule_kind,anchor_local,timezone_id,missed_policy,status,next_run_at_ms,last_run_at_ms,latest_result,last_provider_id,last_model_id,last_input_tokens,last_output_tokens,last_charge_micros,last_currency_code,last_safe_error_code,created_at_ms,updated_at_ms FROM desktop_reminder_plans_v1";

fn draft_row(row: &rusqlite::Row<'_>) -> rusqlite::Result<DraftProjection> {
    Ok(DraftProjection {
        draft_id: row.get(0)?,
        workspace_id: row.get(1)?,
        conversation_id: row.get(2)?,
        source_user_message_id: row.get(3)?,
        source_assistant_message_id: row.get(4)?,
        status: row.get(5)?,
        title: row.get(6)?,
        instruction: row.get(7)?,
        schedule_kind: row.get(8)?,
        anchor_local: row.get(9)?,
        timezone_id: row.get(10)?,
        missed_policy: row.get(11)?,
        provider_id: row.get(12)?,
        requested_model_id: row.get(13)?,
        actual_model_id: row.get(14)?,
        input_tokens: row.get(15)?,
        output_tokens: row.get(16)?,
        cached_input_tokens: row.get(17)?,
        charge_micros: row.get(18)?,
        currency_code: row.get(19)?,
        safe_error_code: row.get(20)?,
        retry_count: row.get(21)?,
        created_at_ms: row.get(22)?,
        updated_at_ms: row.get(23)?,
    })
}
fn plan_row(row: &rusqlite::Row<'_>) -> rusqlite::Result<PlanProjection> {
    Ok(PlanProjection {
        plan_id: row.get(0)?,
        source_draft_id: row.get(1)?,
        workspace_id: row.get(2)?,
        conversation_id: row.get(3)?,
        title: row.get(4)?,
        instruction: row.get(5)?,
        schedule_kind: row.get(6)?,
        anchor_local: row.get(7)?,
        timezone_id: row.get(8)?,
        missed_policy: row.get(9)?,
        status: row.get(10)?,
        next_run_at_ms: row.get(11)?,
        last_run_at_ms: row.get(12)?,
        latest_result: row.get(13)?,
        last_provider_id: row.get(14)?,
        last_model_id: row.get(15)?,
        last_input_tokens: row.get(16)?,
        last_output_tokens: row.get(17)?,
        last_charge_micros: row.get(18)?,
        last_currency_code: row.get(19)?,
        last_safe_error_code: row.get(20)?,
        created_at_ms: row.get(21)?,
        updated_at_ms: row.get(22)?,
    })
}

enum ParsedRefinement {
    NotEligible,
    Draft {
        title: String,
        instruction: String,
        schedule_kind: String,
        anchor_local: String,
        missed_policy: String,
    },
}
fn parse_refinement(text: &str) -> Result<ParsedRefinement, String> {
    let value: Value = serde_json::from_str(
        text.trim()
            .trim_start_matches("```json")
            .trim_start_matches("```")
            .trim_end_matches("```")
            .trim(),
    )
    .map_err(|_| "REMINDER_DRAFT_FORMAT".to_owned())?;
    if value.get("eligible").and_then(Value::as_bool) != Some(true) {
        return Ok(ParsedRefinement::NotEligible);
    }
    let title = value
        .get("title")
        .and_then(Value::as_str)
        .unwrap_or_default()
        .trim()
        .to_owned();
    let instruction = value
        .get("instruction")
        .and_then(Value::as_str)
        .unwrap_or_default()
        .trim()
        .to_owned();
    let schedule_kind = value
        .get("scheduleKind")
        .and_then(Value::as_str)
        .unwrap_or("DAILY")
        .to_owned();
    let anchor_local = value
        .get("anchorLocal")
        .and_then(Value::as_str)
        .unwrap_or_default()
        .to_owned();
    let missed_policy = value
        .get("missedPolicy")
        .and_then(Value::as_str)
        .unwrap_or("RUN_ONCE")
        .to_owned();
    validate_title_instruction(&title, &instruction)
        .map_err(|_| "REMINDER_DRAFT_FORMAT".to_owned())?;
    if !matches!(
        schedule_kind.as_str(),
        "ONCE" | "HOURLY" | "DAILY" | "WEEKLY"
    ) || parse_anchor(&anchor_local).is_err()
        || !matches!(missed_policy.as_str(), "RUN_ONCE" | "SKIP")
    {
        return Err("REMINDER_DRAFT_FORMAT".to_owned());
    }
    Ok(ParsedRefinement::Draft {
        title,
        instruction,
        schedule_kind,
        anchor_local,
        missed_policy,
    })
}

fn reconcile_missed(connection: &Connection, now_ms: i64) -> Result<(), String> {
    let mut statement=connection.prepare(&(PLAN_SELECT.to_owned()+" WHERE status='ACTIVE' AND next_run_at_ms IS NOT NULL AND next_run_at_ms<?1 AND missed_policy='SKIP' ORDER BY next_run_at_ms")).map_err(|_|"错过计划无法读取".to_owned())?;
    let plans = statement
        .query_map([now_ms - MISSED_GRACE_MS], plan_row)
        .map_err(|_| "错过计划无法读取".to_owned())?
        .collect::<Result<Vec<_>, _>>()
        .map_err(|_| "错过计划无效".to_owned())?;
    drop(statement);
    for plan in plans {
        let (status, next) = if plan.schedule_kind == "ONCE" {
            ("COMPLETED".to_owned(), None)
        } else {
            (
                "ACTIVE".to_owned(),
                Some(next_occurrence_ms(
                    &plan.schedule_kind,
                    &plan.anchor_local,
                    &plan.timezone_id,
                    now_ms,
                )?),
            )
        };
        connection.execute("UPDATE desktop_reminder_plans_v1 SET status=?2,next_run_at_ms=?3,last_safe_error_code='MISSED_SKIPPED',updated_at_ms=?4 WHERE plan_id=?1 AND status='ACTIVE'",params![plan.plan_id,status,next,now_ms]).map_err(|_|"错过策略未保存".to_owned())?;
    }
    Ok(())
}

fn next_after_terminal(
    plan: &PlanProjection,
    now_ms: i64,
    succeeded: bool,
) -> Result<(String, Option<i64>), String> {
    if plan.status == "PAUSED" {
        return Ok(("PAUSED".into(), plan.next_run_at_ms));
    }
    if plan.schedule_kind == "ONCE" {
        return Ok((if succeeded { "COMPLETED" } else { "FAILED" }.into(), None));
    }
    Ok((
        "ACTIVE".into(),
        Some(next_occurrence_ms(
            &plan.schedule_kind,
            &plan.anchor_local,
            &plan.timezone_id,
            now_ms,
        )?),
    ))
}

fn first_occurrence_ms(
    kind: &str,
    anchor_local: &str,
    timezone_id: &str,
    now_ms: i64,
) -> Result<i64, String> {
    validate_schedule(kind, anchor_local, timezone_id, "RUN_ONCE")?;
    let anchor = resolve_local(timezone_id, parse_anchor(anchor_local)?)?.timestamp_millis();
    if kind == "ONCE" {
        if anchor <= now_ms {
            return Err("一次性提醒时间必须晚于当前时间".to_owned());
        }
        Ok(anchor)
    } else if anchor > now_ms {
        Ok(anchor)
    } else {
        next_occurrence_ms(kind, anchor_local, timezone_id, now_ms)
    }
}

pub fn next_occurrence_ms(
    kind: &str,
    anchor_local: &str,
    timezone_id: &str,
    after_ms: i64,
) -> Result<i64, String> {
    let tz: Tz = timezone_id.parse().map_err(|_| "时区无效".to_owned())?;
    let anchor = parse_anchor(anchor_local)?;
    let after = Utc
        .timestamp_millis_opt(after_ms)
        .single()
        .ok_or_else(|| "时间无效".to_owned())?
        .with_timezone(&tz);
    match kind {
        "HOURLY" => {
            // Missed checks must keep the original cadence. Advancing from the
            // time of the check would drift the plan's minute on every delay.
            let anchor_ms = resolve_local(timezone_id, anchor)?.timestamp_millis();
            if after_ms < anchor_ms {
                return Ok(anchor_ms);
            }
            let hour_ms = Duration::hours(1).num_milliseconds();
            let elapsed = after_ms.checked_sub(anchor_ms)
                .ok_or_else(|| "计划时间超出范围".to_owned())?;
            let steps = elapsed.div_euclid(hour_ms).checked_add(1)
                .ok_or_else(|| "计划时间超出范围".to_owned())?;
            let next = anchor_ms.checked_add(steps.checked_mul(hour_ms)
                .ok_or_else(|| "计划时间超出范围".to_owned())?)
                .ok_or_else(|| "计划时间超出范围".to_owned())?;
            Utc.timestamp_millis_opt(next).single()
                .ok_or_else(|| "计划时间超出范围".to_owned())?;
            Ok(next)
        }
        "DAILY" | "WEEKLY" => {
            let step = if kind == "DAILY" { 1 } else { 7 };
            let mut date = after.date_naive();
            if kind == "WEEKLY" {
                let delta = (7 + anchor.weekday().num_days_from_monday() as i64
                    - date.weekday().num_days_from_monday() as i64)
                    % 7;
                date = date
                    .checked_add_signed(Duration::days(delta))
                    .ok_or_else(|| "计划日期超出范围".to_owned())?;
            }
            for _ in 0..800 {
                let local = NaiveDateTime::new(date, anchor.time());
                let candidate = resolve_local(timezone_id, local)?;
                if candidate.timestamp_millis() > after_ms {
                    return Ok(candidate.timestamp_millis());
                }
                date = date
                    .checked_add_signed(Duration::days(step))
                    .ok_or_else(|| "计划日期超出范围".to_owned())?;
            }
            Err("无法计算下一次计划时间".to_owned())
        }
        _ => Err("重复频率无效".to_owned()),
    }
}

fn resolve_local(timezone_id: &str, mut local: NaiveDateTime) -> Result<DateTime<Utc>, String> {
    let tz: Tz = timezone_id.parse().map_err(|_| "时区无效".to_owned())?;
    for _ in 0..181 {
        match tz.from_local_datetime(&local) {
            LocalResult::Single(value) => return Ok(value.with_timezone(&Utc)),
            LocalResult::Ambiguous(first, second) => {
                return Ok(first.min(second).with_timezone(&Utc))
            }
            LocalResult::None => {
                local = local
                    .checked_add_signed(Duration::minutes(1))
                    .ok_or_else(|| "DST 时间超出范围".to_owned())?
            }
        }
    }
    Err("该本地时间在时区中不可用".to_owned())
}

fn validate_schedule(
    kind: &str,
    anchor_local: &str,
    timezone_id: &str,
    missed_policy: &str,
) -> Result<(), String> {
    if !matches!(kind, "ONCE" | "HOURLY" | "DAILY" | "WEEKLY") {
        return Err("计划频率无效".to_owned());
    }
    parse_anchor(anchor_local)?;
    validate_timezone(timezone_id)?;
    if !matches!(missed_policy, "RUN_ONCE" | "SKIP") {
        return Err("错过策略无效".to_owned());
    }
    Ok(())
}
fn validate_title_instruction(title: &str, instruction: &str) -> Result<(), String> {
    let title = title.trim();
    let instruction = instruction.trim();
    if title.chars().count() < 2
        || title.chars().count() > MAX_TITLE_CHARS
        || instruction.chars().count() < 2
        || instruction.chars().count() > MAX_INSTRUCTION_CHARS
        || title.contains('\0')
        || instruction.contains('\0')
    {
        return Err("请写清计划名称和要求，并保持在可保存范围内".to_owned());
    }
    Ok(())
}
fn validate_timezone(value: &str) -> Result<(), String> {
    value
        .parse::<Tz>()
        .map(|_| ())
        .map_err(|_| "时区无效".to_owned())
}
fn parse_anchor(value: &str) -> Result<NaiveDateTime, String> {
    NaiveDateTime::parse_from_str(value, "%Y-%m-%dT%H:%M")
        .map_err(|_| "计划时间格式无效".to_owned())
}
fn default_anchor_local(timezone_id: &str, now_ms: i64) -> Result<String, String> {
    let tz: Tz = timezone_id.parse().map_err(|_| "时区无效".to_owned())?;
    let now = Utc
        .timestamp_millis_opt(now_ms)
        .single()
        .ok_or_else(|| "当前时间无效".to_owned())?
        .with_timezone(&tz)
        + Duration::minutes(15);
    Ok(now.format("%Y-%m-%dT%H:%M").to_string())
}
fn bounded_source(value: &str) -> Result<String, String> {
    let value = value.trim();
    if value.is_empty() {
        return Err("提醒草案来源为空".to_owned());
    }
    Ok(value.chars().take(MAX_SOURCE_CHARS).collect())
}
fn validate_id(value: &str) -> Result<(), String> {
    if value.is_empty()
        || value.len() > 200
        || !value.chars().all(|character| {
            character.is_ascii_alphanumeric() || matches!(character, '.' | '_' | ':' | '-')
        })
    {
        Err("本机标识无效".to_owned())
    } else {
        Ok(())
    }
}
fn hash(bytes: &[u8]) -> String {
    format!("{:x}", Sha256::digest(bytes))
}
fn unique_id(prefix: &str, now_ms: i64, seed: &str) -> String {
    format!(
        "{prefix}-{}",
        &hash(format!("{now_ms}-{seed}").as_bytes())[..32]
    )
}
fn safe_notification_title(value: String) -> String {
    let clean = value
        .chars()
        .filter(|value| !value.is_control())
        .take(40)
        .collect::<String>();
    if clean.trim().is_empty() {
        "计划监控".into()
    } else {
        clean
    }
}
fn safe_summary(code: &str) -> String {
    match code {
        "PROCESS_INTERRUPTED" => "上次运行中断，结果未知；不会自动重发。",
        "MISSED_SKIPPED" => "错过运行时间，已按用户设置跳过。",
        "CREDENTIAL" | "CREDENTIAL_MISSING" => "模型凭据不可用；请检查设置后显式重试。",
        "TIMEOUT" | "NETWORK" => "连接未得到明确结果；不会自动重发。",
        "AUTHENTICATION" => "服务商鉴权失败；请检查模型设置。",
        "RATE_LIMIT" => "服务商限流；请稍后显式重试。",
        _ => "任务未完成；请在计划详情中显式处理。",
    }
    .into()
}

#[cfg(test)]
mod tests {
    use super::*;
    fn database() -> Connection {
        let mut connection = Connection::open_in_memory().unwrap();
        connection.execute_batch("PRAGMA foreign_keys=ON;CREATE TABLE workspaces(id TEXT PRIMARY KEY);INSERT INTO workspaces VALUES('workspace-1');").unwrap();
        let transaction = connection.transaction().unwrap();
        migrate(&transaction).unwrap();
        transaction.commit().unwrap();
        connection
    }
    fn manual(connection: &Connection, now: i64) -> DraftProjection {
        create_manual_draft(
            connection,
            &ManualDraftArgs {
                workspace_id: "workspace-1".into(),
                conversation_id: Some("conversation-1".into()),
                timezone_id: "Asia/Shanghai".into(),
            },
            now,
        )
        .unwrap();
        read_projection(connection).unwrap().drafts[0].clone()
    }
    fn confirm(
        connection: &mut Connection,
        draft: &DraftProjection,
        kind: &str,
        anchor: &str,
        policy: &str,
        now: i64,
    ) -> PlanProjection {
        confirm_draft(
            connection,
            &ConfirmDraftArgs {
                draft_id: draft.draft_id.clone(),
                title: "关注发布进展".into(),
                instruction: "检查公开一手来源并报告可核验变化".into(),
                schedule_kind: kind.into(),
                anchor_local: anchor.into(),
                timezone_id: "Asia/Shanghai".into(),
                missed_policy: policy.into(),
            },
            now,
        )
        .unwrap()
        .plans[0]
            .clone()
    }

    #[test]
    fn draft_never_creates_plan_until_confirmed_and_rejection_is_terminal() {
        let mut connection = database();
        let draft = manual(&connection, 1_700_000_000_000);
        assert_eq!(draft.title, "新的监控");
        assert!(read_projection(&connection).unwrap().plans.is_empty());
        reject_draft(&connection, &draft.draft_id, 1_700_000_000_001).unwrap();
        assert!(read_projection(&connection).unwrap().plans.is_empty());
        assert!(confirm_draft(
            &mut connection,
            &ConfirmDraftArgs {
                draft_id: draft.draft_id,
                title: "有效名称".into(),
                instruction: "有效要求".into(),
                schedule_kind: "DAILY".into(),
                anchor_local: "2026-09-02T09:00".into(),
                timezone_id: "Asia/Shanghai".into(),
                missed_policy: "RUN_ONCE".into()
            },
            1_700_000_000_002
        )
        .is_err());
    }

    #[test]
    fn reported_draft_and_run_failures_keep_usage_without_results_or_notifications() {
        let mut connection = database();
        let now = 1_788_285_000_000;
        let draft = manual(&connection, now);
        connection.execute("UPDATE desktop_reminder_drafts_v1 SET status='RUNNING' WHERE draft_id=?1", [&draft.draft_id]).unwrap();
        let measurement = || Completion {
            provider_id: "QWEN", requested_model_id: "fixture", actual_model_id: Some("actual-fixture"),
            text: "partial text must not become a result", input_tokens: Some(20), output_tokens: Some(8),
            cached_input_tokens: Some(2), charge_micros: Some(7),
        };
        let failed = fail_draft_reported(&connection, &draft.draft_id, "OUTPUT_LIMIT", measurement(), now + 1).unwrap();
        assert_eq!(failed.status, "FAILED");
        assert_eq!(failed.input_tokens, Some(20));
        assert_eq!(failed.output_tokens, Some(8));
        assert_eq!(failed.charge_micros, Some(7));
        assert_eq!(failed.actual_model_id.as_deref(), Some("actual-fixture"));
        assert!(read_projection(&connection).unwrap().plans.is_empty());
        let draft = manual(&connection, now + 2);
        let plan = confirm(&mut connection, &draft, "ONCE", "2026-09-02T09:00", "RUN_ONCE", now + 3);
        let run = claim_due(&mut connection, plan.next_run_at_ms.unwrap()).unwrap().unwrap();
        let failed_plan = fail_run_reported(&mut connection, &run, "OUTPUT_LIMIT", measurement(), plan.next_run_at_ms.unwrap() + 1).unwrap();
        assert_eq!(failed_plan.last_safe_error_code.as_deref(), Some("OUTPUT_LIMIT"));
        assert_eq!(failed_plan.last_input_tokens, Some(20));
        assert_eq!(failed_plan.last_output_tokens, Some(8));
        assert_eq!(failed_plan.last_charge_micros, Some(7));
        assert!(failed_plan.latest_result.is_none());
        assert!(pending_notifications(&connection).unwrap().is_empty());
        let run_usage: (String, Option<i64>, Option<i64>, Option<i64>, Option<String>) = connection.query_row("SELECT status,input_tokens,output_tokens,charge_micros,result FROM desktop_reminder_runs_v1 WHERE run_id=?1", [&run.run_id], |row| Ok((row.get(0)?,row.get(1)?,row.get(2)?,row.get(3)?,row.get(4)?))).unwrap();
        assert_eq!(run_usage, ("FAILED".into(), Some(20), Some(8), Some(7), None));
    }

    #[test]
    fn reported_unknown_draft_and_run_keep_facts_without_results_notifications_or_resend() {
        let mut connection = database();
        let now = 1_788_285_000_000;
        let draft = manual(&connection, now);
        connection.execute("UPDATE desktop_reminder_drafts_v1 SET status='RUNNING' WHERE draft_id=?1", [&draft.draft_id]).unwrap();
        let measurement = || Completion {
            provider_id: "QWEN", requested_model_id: "fixture", actual_model_id: Some("actual-fixture"),
            text: "partial text must not become a result", input_tokens: Some(20), output_tokens: Some(8),
            cached_input_tokens: Some(2), charge_micros: Some(7),
        };
        let failed = unknown_draft_reported(&connection, &draft.draft_id, "NETWORK", measurement(), now + 1).unwrap();
        assert_eq!(failed.status, "UNKNOWN");
        assert_eq!(failed.input_tokens, Some(20));
        assert_eq!(failed.output_tokens, Some(8));
        assert_eq!(failed.charge_micros, Some(7));
        assert_eq!(failed.actual_model_id.as_deref(), Some("actual-fixture"));
        assert!(read_projection(&connection).unwrap().plans.is_empty());
        let draft = manual(&connection, now + 2);
        let plan = confirm(&mut connection, &draft, "ONCE", "2026-09-02T09:00", "RUN_ONCE", now + 3);
        let run = claim_due(&mut connection, plan.next_run_at_ms.unwrap()).unwrap().unwrap();
        let failed_plan = unknown_run_reported(&mut connection, &run, "NETWORK", measurement(), plan.next_run_at_ms.unwrap() + 1).unwrap();
        assert_eq!(failed_plan.last_safe_error_code.as_deref(), Some("NETWORK"));
        assert_eq!(failed_plan.last_input_tokens, Some(20));
        assert_eq!(failed_plan.last_output_tokens, Some(8));
        assert_eq!(failed_plan.last_charge_micros, Some(7));
        assert!(failed_plan.latest_result.is_none());
        assert_eq!(failed_plan.status,"UNKNOWN");
        assert!(failed_plan.next_run_at_ms.is_none());
        assert!(claim_due(&mut connection,plan.next_run_at_ms.unwrap()+2000).unwrap().is_none());
        assert!(pending_notifications(&connection).unwrap().is_empty());
        let run_usage: (String, Option<i64>, Option<i64>, Option<i64>, Option<String>) = connection.query_row("SELECT status,input_tokens,output_tokens,charge_micros,result FROM desktop_reminder_runs_v1 WHERE run_id=?1", [&run.run_id], |row| Ok((row.get(0)?,row.get(1)?,row.get(2)?,row.get(3)?,row.get(4)?))).unwrap();
        assert_eq!(run_usage, ("UNKNOWN".into(), Some(20), Some(8), Some(7), None));
    }

    #[test]
    fn confirmed_one_time_plan_runs_once_and_notifies_with_safe_body() {
        let mut connection = database();
        let now = 1_788_285_000_000;
        let draft = manual(&connection, now);
        let plan = confirm(
            &mut connection,
            &draft,
            "ONCE",
            "2026-09-02T09:00",
            "RUN_ONCE",
            now,
        );
        let due = plan.next_run_at_ms.unwrap();
        let run = claim_due(&mut connection, due).unwrap().unwrap();
        complete_run(
            &connection,
            &run,
            Completion {
                provider_id: "OPENROUTER",
                requested_model_id: "openai/gpt-5.6-terra",
                actual_model_id: Some("actual-terra"),
                text: "有一项可核验变化。",
                input_tokens: Some(10),
                output_tokens: Some(20),
                cached_input_tokens: None,
                charge_micros: Some(7),
            },
            due + 1,
        )
        .unwrap();
        let projection = read_projection(&connection).unwrap();
        assert_eq!(projection.plans[0].status, "COMPLETED");
        let notices = pending_notifications(&connection).unwrap();
        assert_eq!(notices.len(), 1);
        assert!(!notices[0].body.contains("变化"));
        assert!(resolve_notification_run(&connection, &notices[0].run_id)
            .unwrap()
            .is_some());
        acknowledge_notification(&connection, &notices[0].run_id, true, None).unwrap();
        assert!(pending_notifications(&connection).unwrap().is_empty());
        let target = resolve_notification_run(&connection, &notices[0].run_id)
            .unwrap()
            .expect("sent notification resolves from its persisted run");
        assert_eq!(target.plan_id, plan.plan_id);
        assert_eq!(target.workspace_id, plan.workspace_id);
        assert_eq!(target.conversation_id, plan.conversation_id.unwrap_or_default());
        assert!(resolve_notification_run(&connection, "invalid/run")
            .unwrap()
            .is_none());
        connection.execute("UPDATE desktop_reminder_runs_v1 SET notification_state='SUPPRESSED' WHERE run_id=?1", [&notices[0].run_id]).unwrap();
        assert!(resolve_notification_run(&connection, &notices[0].run_id)
            .unwrap()
            .is_none());
        connection.execute("UPDATE desktop_reminder_runs_v1 SET notification_state='SENT' WHERE run_id=?1", [&notices[0].run_id]).unwrap();
        delete_plan(&connection, &plan.plan_id).unwrap();
        assert!(resolve_notification_run(&connection, &notices[0].run_id)
            .unwrap()
            .is_none());
    }

    #[test]
    fn daily_wall_clock_survives_dst_transition() {
        let before = resolve_local(
            "America/New_York",
            parse_anchor("2026-03-07T09:00").unwrap(),
        )
        .unwrap()
        .timestamp_millis();
        let next =
            next_occurrence_ms("DAILY", "2026-03-07T09:00", "America/New_York", before).unwrap();
        let local = Utc
            .timestamp_millis_opt(next)
            .single()
            .unwrap()
            .with_timezone(&"America/New_York".parse::<Tz>().unwrap());
        assert_eq!(
            local.format("%Y-%m-%dT%H:%M").to_string(),
            "2026-03-08T09:00"
        );
        assert_eq!(next - before, 23 * 60 * 60 * 1000);
    }

    #[test]
    fn hourly_occurrence_stays_on_anchor_after_a_late_check() {
        let anchor = resolve_local("Asia/Shanghai", parse_anchor("2026-09-26T10:30").unwrap())
            .unwrap()
            .timestamp_millis();
        let checked_late = anchor + 7 * 60 * 1000;
        assert_eq!(
            next_occurrence_ms("HOURLY", "2026-09-26T10:30", "Asia/Shanghai", checked_late)
                .unwrap(),
            anchor + 60 * 60 * 1000
        );
        assert_eq!(
            next_occurrence_ms("HOURLY", "2026-09-26T10:30", "Asia/Shanghai", anchor + 2 * 60 * 60 * 1000 + 7 * 60 * 1000)
                .unwrap(),
            anchor + 3 * 60 * 60 * 1000
        );
    }

    #[test]
    fn hourly_occurrence_keeps_elapsed_anchor_across_dst_gap() {
        let anchor = resolve_local("America/New_York", parse_anchor("2026-03-08T01:30").unwrap())
            .unwrap()
            .timestamp_millis();
        let checked_late = anchor + 35 * 60 * 1000;
        let next = next_occurrence_ms("HOURLY", "2026-03-08T01:30", "America/New_York", checked_late)
            .unwrap();
        assert_eq!(next, anchor + 60 * 60 * 1000);
        let local = Utc.timestamp_millis_opt(next).single().unwrap()
            .with_timezone(&"America/New_York".parse::<Tz>().unwrap());
        assert_eq!(local.format("%Y-%m-%dT%H:%M").to_string(), "2026-03-08T03:30");
    }

    #[test]
    fn missed_skip_advances_recurring_but_run_once_claims() {
        let mut connection = database();
        let now = 1_788_285_000_000;
        let draft = manual(&connection, now);
        confirm(
            &mut connection,
            &draft,
            "DAILY",
            "2026-09-02T09:00",
            "SKIP",
            now,
        );
        let due = read_projection(&connection).unwrap().plans[0]
            .next_run_at_ms
            .unwrap();
        assert!(claim_due(&mut connection, due + MISSED_GRACE_MS + 1)
            .unwrap()
            .is_none());
        let plan = &read_projection(&connection).unwrap().plans[0];
        assert_eq!(plan.status, "ACTIVE");
        assert!(plan.next_run_at_ms.unwrap() > due);
    }

    #[test]
    fn missed_hourly_skip_keeps_the_confirmed_minute() {
        let mut connection = database();
        let anchor = resolve_local("Asia/Shanghai", parse_anchor("2026-09-26T10:30").unwrap())
            .unwrap().timestamp_millis();
        let draft = manual(&connection, anchor - 10 * 60 * 1000);
        let plan = confirm(&mut connection, &draft, "HOURLY", "2026-09-26T10:30", "SKIP", anchor - 10 * 60 * 1000);
        assert_eq!(plan.next_run_at_ms, Some(anchor));
        assert!(claim_due(&mut connection, anchor + MISSED_GRACE_MS + 7 * 60 * 1000)
            .unwrap().is_none());
        let after = read_projection(&connection).unwrap();
        assert_eq!(after.plans[0].status, "ACTIVE");
        assert_eq!(after.plans[0].next_run_at_ms, Some(anchor + 60 * 60 * 1000));
    }

    #[test]
    fn interrupted_runs_recover_unknown_and_require_explicit_retry() {
        let mut connection = database();
        let now = 1_788_285_000_000;
        let draft = manual(&connection, now);
        let plan = confirm(
            &mut connection,
            &draft,
            "ONCE",
            "2026-09-02T09:00",
            "RUN_ONCE",
            now,
        );
        let due = plan.next_run_at_ms.unwrap();
        claim_due(&mut connection, due).unwrap().unwrap();
        recover_interrupted(&connection, due + 1).unwrap();
        assert_eq!(
            read_projection(&connection).unwrap().plans[0].status,
            "UNKNOWN"
        );
        assert!(claim_due(&mut connection, due + 2).unwrap().is_none());
        retry_plan(&connection, &plan.plan_id, due + 3).unwrap();
        assert!(claim_due(&mut connection, due + 3).unwrap().is_some());
    }

    #[test]
    fn pause_resume_delete_and_explicit_intent_are_owner_checked() {
        let mut connection = database();
        let now = 1_788_285_000_000;
        let draft = manual(&connection, now);
        let plan = confirm(
            &mut connection,
            &draft,
            "DAILY",
            "2026-09-02T09:00",
            "RUN_ONCE",
            now,
        );
        set_paused(&connection, &plan.plan_id, true, now + 1).unwrap();
        assert_eq!(
            read_projection(&connection).unwrap().plans[0].status,
            "PAUSED"
        );
        set_paused(&connection, &plan.plan_id, false, now + 2).unwrap();
        assert_eq!(
            read_projection(&connection).unwrap().plans[0].status,
            "ACTIVE"
        );
        delete_plan(&connection, &plan.plan_id).unwrap();
        assert!(read_projection(&connection).unwrap().plans.is_empty());
        assert!(explicit_future_intent("明天提醒我核对公告"));
        assert!(!explicit_future_intent("解释一下提醒功能"));
    }

    #[test]
    fn deleted_plan_preserves_terminal_facts_without_body_or_reactivation() {
        for failed in [false, true] {
            let mut connection = database();
            let now = 1_788_285_000_000;
            let draft = manual(&connection, now);
            let plan = confirm(&mut connection, &draft, "ONCE", "2026-09-02T09:00", "RUN_ONCE", now);
            let run = claim_due(&mut connection, plan.next_run_at_ms.unwrap()).unwrap().unwrap();
            let facts = Completion {
                provider_id: "OPENROUTER", requested_model_id: "fixture", actual_model_id: Some("actual-fixture"),
                text: "private-result-body", input_tokens: Some(20), output_tokens: Some(8), cached_input_tokens: Some(2), charge_micros: Some(7),
            };
            if failed { fail_run_reported(&mut connection, &run, "OUTPUT_LIMIT", facts, now + 100).unwrap(); }
            else { complete_run(&connection, &run, facts, now + 100).unwrap(); }
            delete_plan(&connection, &plan.plan_id).unwrap();
            let persisted: (String, Option<String>, Option<i64>, Option<i64>, Option<i64>) = connection.query_row(
                "SELECT status,result,input_tokens,output_tokens,charge_micros FROM desktop_reminder_runs_v1 WHERE run_id=?1", [&run.run_id],
                |row| Ok((row.get(0)?,row.get(1)?,row.get(2)?,row.get(3)?,row.get(4)?)),
            ).unwrap();
            assert_eq!(persisted, (if failed { "FAILED" } else { "SUCCEEDED" }.into(), None, Some(20), Some(8), Some(7)));
            assert!(read_projection(&connection).unwrap().plans.is_empty());
            assert!(read_projection(&connection).unwrap().drafts.is_empty());
            let identity: (String,String,String,String,Option<String>) = connection.query_row(
                "SELECT status,title,instruction,anchor_local,latest_result FROM desktop_reminder_plans_v1 WHERE plan_id=?1", [&plan.plan_id],
                |row| Ok((row.get(0)?,row.get(1)?,row.get(2)?,row.get(3)?,row.get(4)?)),
            ).unwrap();
            assert_eq!(identity, ("DELETED".into(), "".into(), "".into(), "".into(), None));
            assert!(read_plan(&connection, &plan.plan_id).is_err());
            assert!(set_paused(&connection, &plan.plan_id, false, now+200).is_err());
            assert!(delete_plan(&connection, &plan.plan_id).is_err());
            assert!(claim_due(&mut connection, now+300).unwrap().is_none());
            assert!(pending_notifications(&connection).unwrap().is_empty());
            assert!(resolve_notification_run(&connection, &run.run_id).unwrap().is_none());
            assert!(complete_run(&connection, &run, Completion { provider_id:"OPENROUTER",requested_model_id:"fixture",actual_model_id:None,text:"late-result-must-not-reappear",input_tokens:None,output_tokens:None,cached_input_tokens:None,charge_micros:None }, now+400).is_err());
            assert_eq!(connection.query_row("SELECT result FROM desktop_reminder_runs_v1 WHERE run_id=?1",[&run.run_id],|row|row.get::<_,Option<String>>(0)).unwrap(), None);
        }
    }

    #[test]
    fn deletion_failure_rolls_back_plan_body_run_and_draft() {
        let mut connection=database();let now=1_788_285_000_000;
        let draft=manual(&connection,now);let plan=confirm(&mut connection,&draft,"ONCE","2026-09-02T09:00","RUN_ONCE",now);
        let run=claim_due(&mut connection,plan.next_run_at_ms.unwrap()).unwrap().unwrap();
        complete_run(&connection,&run,Completion{provider_id:"OPENROUTER",requested_model_id:"fixture",actual_model_id:None,text:"retained-until-delete-commits",input_tokens:Some(20),output_tokens:Some(8),cached_input_tokens:None,charge_micros:Some(7)},now+100).unwrap();
        let before=read_projection(&connection).unwrap();
        connection.execute_batch("CREATE TRIGGER synthetic_receipt_denial BEFORE UPDATE ON desktop_reminder_runs_v1 BEGIN SELECT RAISE(ABORT,'synthetic receipt denial'); END;").unwrap();
        assert!(delete_plan(&connection,&plan.plan_id).is_err());
        assert_eq!(read_projection(&connection).unwrap(),before);
        assert_eq!(connection.query_row("SELECT result FROM desktop_reminder_runs_v1 WHERE run_id=?1",[&run.run_id],|r|r.get::<_,String>(0)).unwrap(),"retained-until-delete-commits");
        connection.execute_batch("DROP TRIGGER synthetic_receipt_denial;").unwrap();delete_plan(&connection,&plan.plan_id).unwrap();
        assert!(read_projection(&connection).unwrap().plans.is_empty());
    }

    #[test]
    fn deleting_running_plan_blocks_late_result_and_restart_claim() {
        let mut connection=database();let now=1_788_285_000_000;
        let draft=manual(&connection,now);let plan=confirm(&mut connection,&draft,"ONCE","2026-09-02T09:00","RUN_ONCE",now);
        let run=claim_due(&mut connection,plan.next_run_at_ms.unwrap()).unwrap().unwrap();
        delete_plan(&connection,&plan.plan_id).unwrap();
        let facts=Completion{provider_id:"OPENROUTER",requested_model_id:"fixture",actual_model_id:None,text:"late-private-result",input_tokens:Some(20),output_tokens:Some(8),cached_input_tokens:None,charge_micros:Some(7)};
        assert!(fail_run_reported(&mut connection,&run,"OUTPUT_LIMIT",facts,now+100).is_err());
        recover_interrupted(&connection,now+200).unwrap();assert!(claim_due(&mut connection,now+300).unwrap().is_none());
        assert_eq!(connection.query_row("SELECT status,result,input_tokens FROM desktop_reminder_runs_v1 WHERE run_id=?1",[&run.run_id],|r|Ok((r.get::<_,String>(0)?,r.get::<_,Option<String>>(1)?,r.get::<_,Option<i64>>(2)?))).unwrap(),("CANCELLED".into(),None,None));
        assert!(pending_notifications(&connection).unwrap().is_empty());assert!(read_projection(&connection).unwrap().plans.is_empty());
    }

    #[test]
    fn confirmed_plan_edit_is_transactional_and_rejects_stale_projection() {
        let mut connection = database();
        let now = 1_788_285_000_000;
        let draft = manual(&connection, now);
        let plan = confirm(
            &mut connection,
            &draft,
            "DAILY",
            "2026-09-02T09:00",
            "RUN_ONCE",
            now,
        );
        let args = UpdatePlanArgs {
            plan_id: plan.plan_id.clone(),
            expected_updated_at_ms: plan.updated_at_ms,
            title: "更新后的每日检查".into(),
            instruction: "只核对公开状态页并保留来源".into(),
            schedule_kind: "WEEKLY".into(),
            anchor_local: "2026-09-03T10:30".into(),
            timezone_id: "Asia/Shanghai".into(),
            missed_policy: "SKIP".into(),
        };
        let projection = update_plan(&mut connection, &args, now + 1).unwrap();
        assert_eq!(projection.plans[0].title, "更新后的每日检查");
        assert_eq!(projection.plans[0].schedule_kind, "WEEKLY");
        assert_eq!(projection.plans[0].status, "ACTIVE");
        assert!(projection.plans[0].next_run_at_ms.unwrap() > now);
        let source = read_draft(&connection, &draft.draft_id).unwrap();
        assert_eq!(source.title, "更新后的每日检查");
        assert!(update_plan(&mut connection, &args, now + 2).is_err());
    }

    #[test]
    fn notification_target_resolves_exact_plan_and_deleted_or_mismatched_targets_are_noop() {
        let mut connection = database();
        let now = 1_788_285_000_000;
        let draft = manual(&connection, now);
        let plan = confirm(
            &mut connection,
            &draft,
            "DAILY",
            "2026-09-02T09:00",
            "RUN_ONCE",
            now,
        );
        let target = crate::desktop_reminder_notification_v1::ReminderNotificationTarget {
            route: crate::desktop_reminder_notification_v1::ROUTE.into(),
            plan_id: plan.plan_id.clone(),
            workspace_id: plan.workspace_id.clone(),
            conversation_id: plan.conversation_id.clone().unwrap_or_default(),
        };
        assert_eq!(
            resolve_notification_target(&connection, &target).unwrap(),
            Some(target.clone())
        );
        assert!(resolve_notification_target(
            &connection,
            &crate::desktop_reminder_notification_v1::ReminderNotificationTarget {
                workspace_id: "workspace-other".into(),
                ..target.clone()
            }
        )
        .unwrap()
        .is_none());
        delete_plan(&connection, &plan.plan_id).unwrap();
        assert!(resolve_notification_target(&connection, &target)
            .unwrap()
            .is_none());
    }

    #[test]
    fn run_progress_recovery_projects_reported_facts_without_completing_or_notifying(){
        let mut c=database();let now=1_800_000_000_000i64;let draft=manual(&c,now);
        let tz:chrono_tz::Tz="Asia/Shanghai".parse().unwrap();
        let anchor=chrono::DateTime::from_timestamp_millis(now+120_000).unwrap().with_timezone(&tz).format("%Y-%m-%dT%H:%M").to_string();
        let plan=confirm(&mut c,&draft,"ONCE",&anchor,"RUN_ONCE",now);let run=claim_due(&mut c,now+180_000).unwrap().unwrap();
        // Arrange an already reported, not yet terminal run snapshot. Recovery
        // must expose these independent provider facts without inventing text.
        c.execute("UPDATE desktop_reminder_runs_v1 SET provider_id='OPENROUTER',requested_model_id='requested-reminder',actual_model_id='reported-reminder',input_tokens=17,output_tokens=0,cached_input_tokens=3,charge_micros=4,currency_code='USD' WHERE run_id=?1",[&run.run_id]).unwrap();
        recover_interrupted(&c,now+181_000).unwrap();let p=read_plan(&c,&plan.plan_id).unwrap();
        assert_eq!(p.status,"UNKNOWN");assert_eq!(p.next_run_at_ms,None);assert_eq!(p.latest_result,None);
        assert_eq!(p.last_input_tokens,Some(17));assert_eq!(p.last_output_tokens,Some(0));assert_eq!(p.last_charge_micros,Some(4));assert_eq!(p.last_model_id.as_deref(),Some("reported-reminder"));
        let state:(String,String,Option<String>)=c.query_row("SELECT status,notification_state,result FROM desktop_reminder_runs_v1 WHERE run_id=?1",[&run.run_id],|r|Ok((r.get(0)?,r.get(1)?,r.get(2)?))).unwrap();assert_eq!(state,("UNKNOWN".into(),"SUPPRESSED".into(),None));
        recover_interrupted(&c,now+182_000).unwrap();assert_eq!(read_plan(&c,&plan.plan_id).unwrap(),p);
    }

    fn run_progress_setup()->(Connection,RunReservation){
        let mut c=database();let now=1_800_000_000_000i64;let draft=manual(&c,now);let tz:chrono_tz::Tz="Asia/Shanghai".parse().unwrap();let anchor=chrono::DateTime::from_timestamp_millis(now+120_000).unwrap().with_timezone(&tz).format("%Y-%m-%dT%H:%M").to_string();
        confirm(&mut c,&draft,"ONCE",&anchor,"RUN_ONCE",now);let run=claim_due(&mut c,now+180_000).unwrap().unwrap();bind_run_provider(&c,&run,"OPENROUTER","requested-reminder").unwrap();(c,run)
    }
    fn run_progress_measurement()->crate::desktop_ordinary_chat_v1::ReportedMeasurement{
        crate::desktop_ordinary_chat_v1::ReportedMeasurement{usage:crate::desktop_ordinary_chat_v1::Usage{input_tokens:Some(17),output_tokens:Some(0),cached_input_tokens:Some(3),reasoning_tokens:None},reported_cost_micros:Some(4),actual_model_id:Some("reported-reminder".into()),elapsed_ms:1}
    }
    #[test]
    fn run_progress_sparse_updates_preserve_facts_and_do_not_finalize_or_save_partial_text(){
        let(c,run)=run_progress_setup();checkpoint_run_measurement(&c,&run,"OPENROUTER","requested-reminder",&run_progress_measurement()).unwrap();
        let mut sparse=run_progress_measurement();sparse.usage.input_tokens=None;sparse.usage.output_tokens=Some(2);sparse.usage.cached_input_tokens=None;sparse.reported_cost_micros=None;sparse.actual_model_id=None;
        checkpoint_run_measurement(&c,&run,"OPENROUTER","requested-reminder",&sparse).unwrap();
        let row:(i64,i64,i64,i64,String,String,Option<String>,String)=c.query_row("SELECT input_tokens,output_tokens,cached_input_tokens,charge_micros,actual_model_id,status,result,notification_state FROM desktop_reminder_runs_v1 WHERE run_id=?1",[&run.run_id],|r|Ok((r.get(0)?,r.get(1)?,r.get(2)?,r.get(3)?,r.get(4)?,r.get(5)?,r.get(6)?,r.get(7)?))).unwrap();
        assert_eq!(row,(17,2,3,4,"reported-reminder".into(),"RUNNING".into(),None,"NONE".into()));assert_eq!(read_plan(&c,&run.plan.plan_id).unwrap().last_input_tokens,None);assert!(c.is_autocommit());
    }
    #[test]
    fn run_progress_rejects_other_attempt_schedule_workspace_and_bound_model(){
        let(c,run)=run_progress_setup();let m=run_progress_measurement();
        for field in ["attempt","schedule","workspace"]{let mut stale=run.clone();match field{"attempt"=>stale.attempt_id.push_str("-old"),"schedule"=>stale.scheduled_at_ms+=1,_=>stale.plan.workspace_id.push_str("-other")};assert!(checkpoint_run_measurement(&c,&stale,"OPENROUTER","requested-reminder",&m).is_err());}
        assert!(checkpoint_run_measurement(&c,&run,"QWEN","requested-reminder",&m).is_err());assert!(checkpoint_run_measurement(&c,&run,"OPENROUTER","other-model",&m).is_err());assert!(bind_run_provider(&c,&run,"OPENROUTER","other-model").is_err());
        let n:i64=c.query_row("SELECT COUNT(*) FROM desktop_reminder_runs_v1 WHERE input_tokens IS NOT NULL",[],|r|r.get(0)).unwrap();assert_eq!(n,0);
    }
    #[test]
    fn run_progress_invalid_measurements_are_rejected_but_reported_zero_is_preserved(){
        let(c,run)=run_progress_setup();for field in ["negative","model","elapsed"]{let mut m=run_progress_measurement();match field{"negative"=>m.reported_cost_micros=Some(-1),"model"=>m.actual_model_id=Some("raw\nbody".into()),_=>m.elapsed_ms=-1};assert!(checkpoint_run_measurement(&c,&run,"OPENROUTER","requested-reminder",&m).is_err());}
        let mut m=run_progress_measurement();m.reported_cost_micros=Some(0);checkpoint_run_measurement(&c,&run,"OPENROUTER","requested-reminder",&m).unwrap();let zero:(i64,String)=c.query_row("SELECT charge_micros,currency_code FROM desktop_reminder_runs_v1 WHERE run_id=?1",[&run.run_id],|r|Ok((r.get(0)?,r.get(1)?))).unwrap();assert_eq!(zero,(0,"USD".into()));
    }
    #[test]
    fn run_progress_after_pause_keeps_measurement_without_resuming_the_plan(){
        let(c,run)=run_progress_setup();set_paused(&c,&run.plan.plan_id,true,1_800_000_181_000).unwrap();let paused=read_plan(&c,&run.plan.plan_id).unwrap();checkpoint_run_measurement(&c,&run,"OPENROUTER","requested-reminder",&run_progress_measurement()).unwrap();recover_interrupted(&c,1_800_000_182_000).unwrap();assert_eq!(read_plan(&c,&run.plan.plan_id).unwrap(),paused);
    }
    #[test]
    fn run_progress_deleted_plan_rejects_late_metadata_success_and_failure_without_resurrection(){
        let(c,run)=run_progress_setup();delete_plan(&c,&run.plan.plan_id).unwrap();let snapshot=||c.query_row("SELECT status,title,instruction,next_run_at_ms,latest_result FROM desktop_reminder_plans_v1 WHERE plan_id=?1",[&run.plan.plan_id],|r|Ok((r.get::<_,String>(0)?,r.get::<_,String>(1)?,r.get::<_,String>(2)?,r.get::<_,Option<i64>>(3)?,r.get::<_,Option<String>>(4)?))).unwrap();let deleted=snapshot();assert_eq!(deleted.0,"DELETED");assert!(read_plan(&c,&run.plan.plan_id).is_err());assert!(checkpoint_run_measurement(&c,&run,"OPENROUTER","requested-reminder",&run_progress_measurement()).is_err());
        let completion=Completion{provider_id:"OPENROUTER",requested_model_id:"requested-reminder",actual_model_id:Some("reported-reminder"),text:"NOT_A_NEW_RESULT",input_tokens:Some(17),output_tokens:Some(2),cached_input_tokens:Some(3),charge_micros:Some(4)};
        assert!(complete_run(&c,&run,completion,1_800_000_182_000).is_err());assert!(fail_run(&c,&run,Some("OPENROUTER"),Some("requested-reminder"),true,"NETWORK",1_800_000_182_000).is_err());assert_eq!(snapshot(),deleted);assert!(read_projection(&c).unwrap().plans.iter().all(|p|p.plan_id!=run.plan.plan_id));
    }

    #[test]
    fn reminder_stale_run_cannot_complete_fail_or_checkpoint_a_new_retry(){
        let(mut c,old)=run_progress_setup();fail_run(&c,&old,Some("OPENROUTER"),Some("requested-reminder"),true,"NETWORK",1_800_000_181_000).unwrap();retry_plan(&c,&old.plan.plan_id,1_800_000_182_000).unwrap();let next=claim_due(&mut c,1_800_000_183_000).unwrap().unwrap();bind_run_provider(&c,&next,"OPENROUTER","requested-reminder").unwrap();let before=read_plan(&c,&old.plan.plan_id).unwrap();
        let completion=Completion{provider_id:"OPENROUTER",requested_model_id:"requested-reminder",actual_model_id:Some("old-reported"),text:"OLD_RESULT_MUST_NOT_RETURN",input_tokens:Some(17),output_tokens:Some(2),cached_input_tokens:Some(3),charge_micros:Some(4)};
        assert!(complete_run(&c,&old,completion,1_800_000_184_000).is_err());assert!(fail_run(&c,&old,Some("OPENROUTER"),Some("requested-reminder"),true,"NETWORK",1_800_000_184_000).is_err());assert!(checkpoint_run_measurement(&c,&old,"OPENROUTER","requested-reminder",&run_progress_measurement()).is_err());assert_eq!(read_plan(&c,&old.plan.plan_id).unwrap(),before);
        checkpoint_run_measurement(&c,&next,"OPENROUTER","requested-reminder",&run_progress_measurement()).unwrap();
    }

    #[test]
    fn draft_progress_new_qwen_failure_keeps_cny_currency_without_converting_amount(){
        let mut c=database();let now=1_800_000_000_000;
        let args=GenerateDraftArgs{workspace_id:"workspace-1".into(),conversation_id:"conversation-1".into(),user_message_id:"user-1".into(),assistant_message_id:"assistant-1".into(),timezone_id:"UTC".into()};
        let draft=reserve_generated_draft(&mut c,&args,"提醒我明天核对公开进展","可生成待确认草案",now).unwrap();
        let p=unknown_draft_reported(&c,&draft.draft_id,"NETWORK",Completion{provider_id:"QWEN",requested_model_id:"qwen3.7-plus",actual_model_id:Some("reported-qwen"),text:"",input_tokens:Some(23),output_tokens:Some(0),cached_input_tokens:Some(5),charge_micros:Some(9)},now+1).unwrap();
        assert_eq!(p.status,"UNKNOWN");assert_eq!(p.charge_micros,Some(9));assert_eq!(p.currency_code.as_deref(),Some("CNY"));
    }

    fn draft_progress_setup()->(Connection,DraftReservation){
        let mut c=database();let args=GenerateDraftArgs{workspace_id:"workspace-1".into(),conversation_id:"conversation-1".into(),user_message_id:"user-1".into(),assistant_message_id:"assistant-1".into(),timezone_id:"UTC".into()};let d=reserve_generated_draft(&mut c,&args,"提醒我明天核对公开进展","请核对草案后确认",1_800_000_000_000).unwrap();bind_draft_provider(&c,&d,"QWEN","qwen3.7-plus").unwrap();(c,d)
    }
    fn draft_progress_measurement()->crate::desktop_ordinary_chat_v1::ReportedMeasurement{crate::desktop_ordinary_chat_v1::ReportedMeasurement{usage:crate::desktop_ordinary_chat_v1::Usage{input_tokens:Some(23),output_tokens:Some(0),cached_input_tokens:Some(5),reasoning_tokens:None},reported_cost_micros:Some(9),actual_model_id:Some("reported-qwen".into()),elapsed_ms:1}}
    #[test]
    fn draft_progress_sparse_cny_facts_survive_without_creating_a_review_or_plan(){
        let(c,d)=draft_progress_setup();checkpoint_draft_measurement(&c,&d,"QWEN","qwen3.7-plus",&draft_progress_measurement(),1_800_000_000_001).unwrap();let mut sparse=draft_progress_measurement();sparse.usage.input_tokens=None;sparse.usage.output_tokens=Some(3);sparse.usage.cached_input_tokens=None;sparse.reported_cost_micros=None;sparse.actual_model_id=None;checkpoint_draft_measurement(&c,&d,"QWEN","qwen3.7-plus",&sparse,1_800_000_000_002).unwrap();
        let p=read_draft(&c,&d.draft_id).unwrap();assert_eq!(p.status,"RUNNING");assert_eq!((p.input_tokens,p.output_tokens,p.cached_input_tokens,p.charge_micros),(Some(23),Some(3),Some(5),Some(9)));assert_eq!(p.currency_code.as_deref(),Some("CNY"));assert_eq!(p.actual_model_id.as_deref(),Some("reported-qwen"));assert!(read_projection(&c).unwrap().plans.is_empty());assert!(!p.instruction.contains("reported-qwen"));
    }
    #[test]
    fn draft_progress_rejects_wrong_attempt_retry_workspace_and_both_source_ids(){
        let(c,d)=draft_progress_setup();for field in ["attempt","retry","workspace","conversation","user","assistant"]{let mut other=d.clone();match field{"attempt"=>other.attempt_id.push_str("-other"),"retry"=>other.retry_count+=1,"workspace"=>other.workspace_id.push_str("-other"),"conversation"=>other.conversation_id.push_str("-other"),"user"=>other.user_message_id.push_str("-other"),_=>other.assistant_message_id.push_str("-other")};assert!(checkpoint_draft_measurement(&c,&other,"QWEN","qwen3.7-plus",&draft_progress_measurement(),1_800_000_000_001).is_err());}
        assert!(checkpoint_draft_measurement(&c,&d,"OPENROUTER","qwen3.7-plus",&draft_progress_measurement(),1_800_000_000_001).is_err());assert!(bind_draft_provider(&c,&d,"QWEN","other-model").is_err());assert_eq!(read_draft(&c,&d.draft_id).unwrap().input_tokens,None);
    }
    #[test]
    fn draft_progress_old_attempt_cannot_touch_a_new_explicit_retry(){
        let(mut c,d)=draft_progress_setup();fail_draft(&c,&d.draft_id,Some("QWEN"),Some("qwen3.7-plus"),true,"NETWORK",1_800_000_000_001).unwrap();let next=reserve_draft_retry(&mut c,&d.draft_id,&d.user_text,&d.assistant_text,1_800_000_000_002).unwrap();bind_draft_provider(&c,&next,"QWEN","qwen3.7-plus").unwrap();assert!(require_current_draft(&c,&d).is_err());assert!(checkpoint_draft_measurement(&c,&d,"QWEN","qwen3.7-plus",&draft_progress_measurement(),1_800_000_000_003).is_err());checkpoint_draft_measurement(&c,&next,"QWEN","qwen3.7-plus",&draft_progress_measurement(),1_800_000_000_003).unwrap();assert_eq!(next.retry_count,d.retry_count+1);
    }
    #[test]
    fn draft_progress_not_eligible_zero_stays_cny_and_never_creates_a_plan(){
        let(c,d)=draft_progress_setup();let p=complete_draft(&c,&d.draft_id,Completion{provider_id:"QWEN",requested_model_id:"qwen3.7-plus",actual_model_id:Some("reported-qwen"),text:"{\"eligible\":false}",input_tokens:Some(23),output_tokens:Some(3),cached_input_tokens:Some(5),charge_micros:Some(0)},1_800_000_000_001).unwrap();assert_eq!(p.status,"NOT_ELIGIBLE");assert_eq!(p.charge_micros,Some(0));assert_eq!(p.currency_code.as_deref(),Some("CNY"));assert!(read_projection(&c).unwrap().plans.is_empty());assert!(require_current_draft(&c,&d).is_err());
    }
    #[test]
    fn draft_progress_recovery_does_not_relabel_a_legacy_known_usd_receipt(){
        let(c,d)=draft_progress_setup();checkpoint_draft_measurement(&c,&d,"QWEN","qwen3.7-plus",&draft_progress_measurement(),1_800_000_000_001).unwrap();c.execute("UPDATE desktop_reminder_drafts_v1 SET currency_code='USD' WHERE draft_id=?1",[&d.draft_id]).unwrap();recover_interrupted(&c,1_800_000_000_002).unwrap();let p=read_draft(&c,&d.draft_id).unwrap();assert_eq!(p.status,"UNKNOWN");assert_eq!(p.charge_micros,Some(9));assert_eq!(p.currency_code.as_deref(),Some("USD"));assert!(read_projection(&c).unwrap().plans.is_empty());
    }
}
