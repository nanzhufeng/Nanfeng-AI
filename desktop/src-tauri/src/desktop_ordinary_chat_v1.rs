//! Ordinary-chat OpenAI-compatible transport core.
//!
//! The workspace owner persists messages/attempts; this module only owns one bounded HTTP call
//! and normalized response facts. Raw payloads, prompts and credentials never appear in results.

use futures_util::StreamExt;
use reqwest::{Client, Url};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::{
    sync::{
        atomic::{AtomicBool, Ordering},
        Arc,
    },
    time::{Duration, Instant},
};
use zeroize::{Zeroize, Zeroizing};

const MAX_RESPONSE_BYTES: usize = 16 * 1024 * 1024;
const STREAM_IDLE_TIMEOUT: Duration = Duration::from_secs(90);
const STREAM_RESPONSE_TIMEOUT: Duration = Duration::from_secs(180);
const STREAM_TOTAL_TIMEOUT: Duration = Duration::from_secs(600);

// Semantic progress (answer, reasoning, or a provider-owned search event) extends the
// idle budget. Heartbeats do not. Reasoning remains private and never becomes an answer.
#[derive(Default)]
struct StreamProgressBudget {
    last_progress: Duration,
}
impl StreamProgressBudget {
    fn advance(&mut self, elapsed: Duration) {
        self.last_progress = elapsed;
    }
    fn remaining(&self, elapsed: Duration) -> Duration {
        STREAM_IDLE_TIMEOUT
            .saturating_sub(elapsed.saturating_sub(self.last_progress))
            .min(STREAM_TOTAL_TIMEOUT.saturating_sub(elapsed))
    }
}

pub fn migrate(connection: &rusqlite::Connection) -> Result<(), String> {
    connection.execute_batch(
        "CREATE TABLE desktop_ordinary_chat_attempts (
            attempt_id TEXT PRIMARY KEY NOT NULL,
            workspace_id TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
            conversation_id TEXT NOT NULL,
            user_message_id TEXT NOT NULL,
            assistant_message_id TEXT NOT NULL,
            retry_of_attempt_id TEXT,
            retry_count INTEGER NOT NULL DEFAULT 0,
            idempotency_key TEXT NOT NULL,
            request_fingerprint TEXT NOT NULL,
            provider_id TEXT,
            requested_model_id TEXT,
            actual_model_id TEXT,
            model_display_name TEXT,
            state TEXT NOT NULL,
            safe_error_code TEXT,
            input_tokens INTEGER,
            output_tokens INTEGER,
            cached_input_tokens INTEGER,
            reasoning_tokens INTEGER,
            charge_micros INTEGER,
            currency_code TEXT,
            cost_source TEXT,
            latency_ms INTEGER,
            created_at_ms INTEGER NOT NULL,
            updated_at_ms INTEGER NOT NULL,
            terminal_at_ms INTEGER
        );
        CREATE INDEX desktop_ordinary_chat_attempt_conversation
            ON desktop_ordinary_chat_attempts(workspace_id, conversation_id, created_at_ms DESC);
        CREATE TABLE desktop_ordinary_chat_diagnostics (
            diagnostic_id TEXT PRIMARY KEY NOT NULL,
            attempt_id TEXT NOT NULL REFERENCES desktop_ordinary_chat_attempts(attempt_id) ON DELETE CASCADE,
            provider_id TEXT,
            model_id TEXT,
            state TEXT NOT NULL,
            safe_error_code TEXT,
            http_status INTEGER,
            event_count INTEGER NOT NULL,
            latency_ms INTEGER,
            created_at_ms INTEGER NOT NULL
        );
        CREATE INDEX desktop_ordinary_chat_diagnostic_attempt
            ON desktop_ordinary_chat_diagnostics(attempt_id, created_at_ms);"
    ).map_err(|_| "普通聊天 SQLite migration 失败".to_owned())
}

/// Adds the answer-bound, content-free context audit used by the Android-parity
/// personalization/Memory/Knowledge request assembler. Bodies stay in the workspace IR and are
/// never duplicated into this audit table.
pub fn migrate_context_audit(connection: &rusqlite::Connection) -> Result<(), String> {
    connection.execute_batch(
        "ALTER TABLE desktop_ordinary_chat_attempts ADD COLUMN web_search_route TEXT NOT NULL DEFAULT 'NONE';
        CREATE TABLE desktop_ordinary_chat_context_sources (
            attempt_id TEXT NOT NULL REFERENCES desktop_ordinary_chat_attempts(attempt_id) ON DELETE CASCADE,
            ordinal INTEGER NOT NULL,
            source_kind TEXT NOT NULL,
            source_id TEXT NOT NULL,
            title TEXT NOT NULL,
            PRIMARY KEY(attempt_id, ordinal)
        );
        CREATE INDEX desktop_ordinary_chat_context_attempt
            ON desktop_ordinary_chat_context_sources(attempt_id, ordinal);"
    ).map_err(|_| "普通聊天上下文审计 migration 失败".to_owned())
}

/// Adds an answer-bound network evidence bit.  `web_search_route` is the durable fact that the
/// request asked the provider to use its web capability; this bit is set only when the completed
/// provider response contained a safe, provider-owned source or tool-result signal.
pub fn migrate_web_search_provenance(connection: &rusqlite::Connection) -> Result<(), String> {
    let has_attempts: bool = connection
        .query_row(
            "SELECT EXISTS(SELECT 1 FROM sqlite_master WHERE type='table' AND name='desktop_ordinary_chat_attempts')",
            [],
            |row| row.get(0),
        )
        .map_err(|_| "普通聊天联网核验表状态无法读取".to_owned())?;
    // Narrow historical migration fixtures can intentionally stop before ordinary chat exists.
    // The next real open will run the earlier migrations before this version, so this is a safe
    // no-op rather than a failed upgrade.
    if !has_attempts {
        return Ok(());
    }
    let mut statement = connection
        .prepare("PRAGMA table_info(desktop_ordinary_chat_attempts)")
        .map_err(|_| "普通聊天联网核验列状态无法读取".to_owned())?;
    let columns = statement
        .query_map([], |row| row.get::<_, String>(1))
        .map_err(|_| "普通聊天联网核验列状态无效".to_owned())?
        .collect::<Result<std::collections::BTreeSet<_>, _>>()
        .map_err(|_| "普通聊天联网核验列状态无效".to_owned())?;
    if columns.contains("web_search_verified") {
        return Ok(());
    }
    connection
        .execute_batch(
            "ALTER TABLE desktop_ordinary_chat_attempts
                 ADD COLUMN web_search_verified INTEGER;",
        )
        .map_err(|_| "普通聊天联网核验字段 migration 失败".to_owned())
}

/// Adds the durable Compare execution envelope while retaining ordinary-chat attempts as the
/// only branch runtime, transport, context-audit, and usage owner. No user text or provider
/// payload is duplicated into the Compare table.
pub fn migrate_compare(connection: &rusqlite::Connection) -> Result<(), String> {
    connection.execute_batch(
        "ALTER TABLE desktop_ordinary_chat_attempts ADD COLUMN compare_execution_id TEXT;
        ALTER TABLE desktop_ordinary_chat_attempts ADD COLUMN compare_logical_model TEXT;
        CREATE TABLE desktop_compare_executions (
            execution_id TEXT PRIMARY KEY NOT NULL,
            workspace_id TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
            conversation_id TEXT NOT NULL,
            user_message_id TEXT NOT NULL,
            chatgpt_attempt_id TEXT NOT NULL UNIQUE REFERENCES desktop_ordinary_chat_attempts(attempt_id) ON DELETE CASCADE,
            claude_attempt_id TEXT NOT NULL UNIQUE REFERENCES desktop_ordinary_chat_attempts(attempt_id) ON DELETE CASCADE,
            state TEXT NOT NULL,
            created_at_ms INTEGER NOT NULL,
            updated_at_ms INTEGER NOT NULL,
            terminal_at_ms INTEGER
        );
        CREATE INDEX desktop_compare_execution_conversation
            ON desktop_compare_executions(workspace_id, conversation_id, created_at_ms DESC);
        CREATE UNIQUE INDEX desktop_compare_attempt_branch
            ON desktop_ordinary_chat_attempts(compare_execution_id, compare_logical_model)
            WHERE compare_execution_id IS NOT NULL;"
    ).map_err(|_| "Desktop Compare SQLite migration 失败".to_owned())
}

/// Records the one visible send approval without duplicating user text, attachments, or Key bytes.
pub fn migrate_egress_authorization(connection: &rusqlite::Connection) -> Result<(), String> {
    let exists: bool = connection
        .query_row("SELECT EXISTS(SELECT 1 FROM sqlite_master WHERE type='table' AND name='desktop_ordinary_chat_attempts')", [], |row| row.get(0))
        .map_err(|_| "Desktop 普通聊天外发授权表状态无法读取".to_owned())?;
    // Focused migration fixtures may intentionally model only a later subsystem. They must not
    // invent an absent ordinary-chat table merely to advance the global schema marker.
    if !exists {
        return Ok(());
    }
    let mut statement = connection
        .prepare("SELECT name FROM pragma_table_info('desktop_ordinary_chat_attempts')")
        .map_err(|_| "Desktop 普通聊天外发授权字段无法读取".to_owned())?;
    let columns = statement
        .query_map([], |row| row.get::<_, String>(0))
        .map_err(|_| "Desktop 普通聊天外发授权字段无法枚举".to_owned())?
        .collect::<Result<std::collections::BTreeSet<_>, _>>()
        .map_err(|_| "Desktop 普通聊天外发授权字段无效".to_owned())?;
    if !columns.contains("egress_approved_at_ms") {
        connection.execute_batch("ALTER TABLE desktop_ordinary_chat_attempts ADD COLUMN egress_approved_at_ms INTEGER;")
            .map_err(|_| "Desktop 普通聊天外发授权时间字段迁移失败".to_owned())?;
    }
    if !columns.contains("egress_disclosure_version") {
        connection.execute_batch("ALTER TABLE desktop_ordinary_chat_attempts ADD COLUMN egress_disclosure_version TEXT;")
            .map_err(|_| "Desktop 普通聊天外发授权版本字段迁移失败".to_owned())?;
    }
    Ok(())
}

#[derive(Debug, Clone)]
pub struct TransportRequest {
    pub endpoint: String,
    pub provider_id: String,
    pub model_id: String,
    pub messages: Value,
    pub idempotency_key: String,
    pub max_output_tokens: u32,
    pub web_search_route: String,
    pub structured_json: bool,
    pub disable_thinking: bool,
    pub grounded_answer: bool,
    pub explicit_search_query: Option<String>,
}

#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Usage {
    pub input_tokens: Option<i64>,
    pub output_tokens: Option<i64>,
    pub cached_input_tokens: Option<i64>,
    pub reasoning_tokens: Option<i64>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Completed {
    pub text: String,
    pub reasoning: Option<String>,
    pub usage: Usage,
    pub reported_cost_micros: Option<i64>,
    pub actual_model_id: Option<String>,
    pub web_search_verified: bool,
    pub native_sources: Vec<(String, String)>,
    pub elapsed_ms: i64,
}

/// Provider-reported non-content facts from a terminal response whose answer is not usable.
/// It deliberately carries no prompt, response text, source URL or credential.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ReportedMeasurement {
    pub usage: Usage,
    pub reported_cost_micros: Option<i64>,
    pub actual_model_id: Option<String>,
    pub elapsed_ms: i64,
}

impl ReportedMeasurement {
    fn has_reported_facts(&self) -> bool {
        self.usage.input_tokens.is_some()
            || self.usage.output_tokens.is_some()
            || self.usage.cached_input_tokens.is_some()
            || self.usage.reasoning_tokens.is_some()
            || self.reported_cost_micros.is_some()
            || self.actual_model_id.as_deref().and_then(valid_reported_model_id).is_some()
    }

    pub fn content_free_completed(self) -> Completed {
        Completed {
            text: String::new(),
            reasoning: None,
            usage: self.usage,
            reported_cost_micros: self.reported_cost_micros,
            actual_model_id: self.actual_model_id,
            web_search_verified: false,
            elapsed_ms: self.elapsed_ms,

            native_sources: Vec::new(),
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Failure {
    Cancelled,
    MeasuredCancelled {
        measurement: ReportedMeasurement,
    },
    Explicit {
        code: &'static str,
        http_status: Option<u16>,
    },
    MeasuredExplicit {
        code: &'static str,
        http_status: Option<u16>,
        measurement: ReportedMeasurement,
    },
    Unknown {
        code: &'static str,
    },
    MeasuredUnknown {
        code: &'static str,
        measurement: ReportedMeasurement,
    },
}

impl Failure {
    /// Only non-content reported facts survive a failed retrieval; no unverified answer
    /// body is promoted into a successful reply or diagnostics.
    pub fn quantitative_completed(&self) -> Option<Completed> {
        match self { Self::MeasuredExplicit { measurement, .. } | Self::MeasuredCancelled { measurement } | Self::MeasuredUnknown { measurement, .. } =>
            Some(measurement.clone().content_free_completed()), _ => None }
    }
}

fn cancelled_with_progress(usage: &Usage, reported_cost_micros: Option<i64>, actual_model_id: &Option<String>, elapsed: Duration) -> Failure {
    let measurement = ReportedMeasurement { usage: usage.clone(), reported_cost_micros,
        actual_model_id: actual_model_id.clone(), elapsed_ms: elapsed.as_millis().min(i64::MAX as u128) as i64 };
    if measurement.has_reported_facts() { Failure::MeasuredCancelled { measurement } } else { Failure::Cancelled }
}

fn format_failure_with_progress(status:u16, usage:&Usage, reported_cost_micros:Option<i64>, actual_model_id:&Option<String>, elapsed:Duration) -> Failure {
    let measurement=ReportedMeasurement {usage:usage.clone(),reported_cost_micros,
        actual_model_id:actual_model_id.clone(),elapsed_ms:elapsed.as_millis().min(i64::MAX as u128) as i64};
    if measurement.has_reported_facts() {
        Failure::MeasuredExplicit {code:"RESPONSE_FORMAT",http_status:Some(status),measurement}
    } else {
        Failure::Explicit {code:"RESPONSE_FORMAT",http_status:Some(status)}
    }
}

fn unknown_with_progress(code:&'static str, usage:&Usage, reported_cost_micros:Option<i64>, actual_model_id:&Option<String>, elapsed:Duration) -> Failure {
    let measurement=ReportedMeasurement {usage:usage.clone(),reported_cost_micros,
        actual_model_id:actual_model_id.clone(),elapsed_ms:elapsed.as_millis().min(i64::MAX as u128) as i64};
    if measurement.has_reported_facts() {Failure::MeasuredUnknown {code,measurement}} else {Failure::Unknown {code}}
}

fn safe_http_code(status: u16) -> &'static str {
    match status {
        401 => "AUTHENTICATION",
        402 => "BALANCE",
        403 => "AUTHORIZATION",
        408 | 504 => "TIMEOUT",
        429 => "RATE_LIMIT",
        400..=499 => "INVALID_REQUEST",
        500..=599 => "SERVICE_UNAVAILABLE",
        _ => "HTTP_FAILURE",
    }
}

fn cached_input_tokens(value: Option<&Value>) -> Option<i64> {
    let usage = value?;
    usage.pointer("/prompt_tokens_details/cached_tokens")
        .or_else(|| usage.pointer("/input_tokens_details/cached_tokens"))
        .or_else(|| usage.get("prompt_cache_hit_tokens"))
        .or_else(|| usage.get("cache_read_input_tokens"))
        .or_else(|| usage.get("cached_input_tokens"))
        .and_then(Value::as_i64)
}

fn parse_usage(value: Option<&Value>) -> Usage {
    let completion_details = value.and_then(|usage| usage.get("completion_tokens_details"));
    Usage {
        input_tokens: value
            .and_then(|usage| usage.get("prompt_tokens"))
            .and_then(Value::as_i64),
        output_tokens: value
            .and_then(|usage| usage.get("completion_tokens"))
            .and_then(Value::as_i64),
        cached_input_tokens: cached_input_tokens(value),
        reasoning_tokens: completion_details
            .and_then(|details| details.get("reasoning_tokens"))
            .and_then(Value::as_i64),
    }
}

fn merge_usage(target: &mut Usage, value: Usage) {
    if value.input_tokens.is_some() {
        target.input_tokens = value.input_tokens;
    }
    if value.output_tokens.is_some() {
        target.output_tokens = value.output_tokens;
    }
    if value.cached_input_tokens.is_some() {
        target.cached_input_tokens = value.cached_input_tokens;
    }
    if value.reasoning_tokens.is_some() {
        target.reasoning_tokens = value.reasoning_tokens;
    }
}

#[derive(Default)]
struct MessagesUsageAccumulator {
    uncached_input: Option<i64>,
    cache_read_input: Option<i64>,
    cache_creation_input: Option<i64>,
    output: Option<i64>,
}

impl MessagesUsageAccumulator {
    fn update(&mut self, reported: &Value) -> Usage {
        // Messages counters are cumulative per field. Sparse frames retain the
        // earlier components; an explicit zero replaces them, never adds to them.
        self.uncached_input = reported["input_tokens"].as_i64().or(self.uncached_input);
        self.cache_read_input = reported["cache_read_input_tokens"].as_i64().or(self.cache_read_input);
        self.cache_creation_input = reported["cache_creation_input_tokens"].as_i64().or(self.cache_creation_input);
        self.output = reported["output_tokens"].as_i64().or(self.output);
        Usage {
            input_tokens: self.uncached_input
                .and_then(|count| count.checked_add(self.cache_read_input.unwrap_or(0)))
                .and_then(|count| count.checked_add(self.cache_creation_input.unwrap_or(0))),
            output_tokens: self.output,
            cached_input_tokens: self.cache_read_input,
            reasoning_tokens: None,
        }
    }
}

fn cost_micros(value: Option<&Value>) -> Option<i64> {
    let number = value?.as_f64()?;
    (number.is_finite() && number >= 0.0).then(|| (number * 1_000_000.0).round() as i64)
}

fn text_delta(value: &Value) -> Option<&str> {
    value
        .get("choices")?
        .get(0)?
        .get("delta")?
        .get("content")?
        .as_str()
}

/// OpenAI-compatible providers are permitted to return the visible assistant message either as
/// a single string or as typed text parts. Android accepts both forms; keep the same projection
/// here and deliberately exclude reasoning/tool parts from a user-visible reply or title.
fn message_content_text(value: &Value) -> Option<String> {
    match value {
        Value::String(text) => (!text.trim().is_empty()).then(|| text.trim().to_owned()),
        Value::Array(parts) => {
            let text = parts
                .iter()
                .filter_map(|part| {
                    matches!(
                        part.get("type").and_then(Value::as_str),
                        Some("text" | "output_text")
                    )
                    .then(|| part.get("text").and_then(Value::as_str))
                    .flatten()
                })
                .collect::<String>();
            (!text.trim().is_empty()).then(|| text.trim().to_owned())
        }
        _ => None,
    }
}

fn reasoning_delta(value: &Value) -> Option<&str> {
    let delta = value.get("choices")?.get(0)?.get("delta")?;
    delta
        .get("reasoning_content")
        .or_else(|| delta.get("reasoning"))?
        .as_str()
}

fn has_structured_reasoning_progress(value: &Value) -> bool {
    value
        .pointer("/choices/0/delta/reasoning_details")
        .and_then(Value::as_array)
        .is_some_and(|details| {
            details.iter().any(|detail| {
                let field = match detail.get("type").and_then(Value::as_str) {
                    Some("reasoning.text") => "text",
                    Some("reasoning.summary") => "summary",
                    Some("reasoning.encrypted") => "data",
                    _ => return false,
                };
                detail
                    .get(field)
                    .and_then(Value::as_str)
                    .is_some_and(|text| !text.is_empty())
            })
        })
}

/// Some OpenAI-compatible gateways close an SSE response after an explicit choice
/// terminal reason but before emitting the optional `[DONE]` sentinel. The terminal
/// choice is sufficient completion evidence; a missing/empty/null value is not.
fn has_explicit_choice_finish_reason(value: &Value) -> bool {
    value
        .get("choices")
        .and_then(Value::as_array)
        .into_iter()
        .flatten()
        .any(|choice| {
            choice
                .get("finish_reason")
                .and_then(Value::as_str)
                .is_some_and(|reason| !reason.trim().is_empty() && reason != "null")
        })
}

fn responses_input(messages: &Value) -> Value {
    Value::Array(messages.as_array().into_iter().flatten().filter_map(|message| {
        let role = message.get("role")?.as_str()?;
        let content = message.get("content")?;
        let parts = if let Some(text) = content.as_str() {
            vec![json!({"type":"input_text","text":text})]
        } else {
            content.as_array()?.iter().filter_map(|part| match part.get("type")?.as_str()? {
                "text" => Some(json!({"type":"input_text","text":part.get("text")?})),
                "image_url" => Some(json!({"type":"input_image","image_url":part.get("image_url")?.get("url")?})),
                "file" => Some(json!({"type":"input_file",
                    "filename":part.get("file")?.get("filename")?,
                    "file_data":part.get("file")?.get("file_data")?})),
                _ => None,
            }).collect()
        };
        Some(json!({"role":role,"content":parts}))
    }).collect())
}

fn relay_responses_input(messages: &Value) -> Value {
    let mut input = responses_input(messages);
    for message in input.as_array_mut().into_iter().flatten() {
        if message["role"] == "system" { message["role"] = json!("developer"); }
        if let Some(parts) = message["content"].as_array() {
            if parts.iter().all(|part| part["type"] == "input_text") {
                let text = parts.iter().filter_map(|part| part["text"].as_str())
                    .collect::<Vec<_>>().join("\n\n");
                message["content"] = json!([{"type":"input_text","text":text}]);
            }
        }
        // Keep the Windows fix: assistant history must use output_text.
        if message["role"] == "assistant" {
            for part in message["content"].as_array_mut().into_iter().flatten() {
                if part["type"] == "input_text" { part["type"] = json!("output_text"); }
            }
        }
    }
    input
}

fn provider_client_builder(endpoint: &str) -> reqwest::ClientBuilder {
    let builder=Client::builder();
    let loopback=reqwest::Url::parse(endpoint).ok().is_some_and(|u|u.host_str().is_some_and(|h| h=="localhost" || h=="[::1]" || h.parse::<std::net::IpAddr>().is_ok_and(|a|a.is_loopback())));
    if loopback {builder.no_proxy()} else {builder}
}

pub fn provider_endpoint(base: &str, route: &str) -> String {
    let base = base.trim_end_matches('/');
    match route {
        "DEEPSEEK_MESSAGES" => format!("{}/anthropic/v1/messages", base.strip_suffix("/v1").unwrap_or(base)),
        "QWEN_RESPONSES" | "RELAYCORE_GPT_RESPONSES" => format!("{base}/responses"),
        _ => format!("{base}/chat/completions"),
    }
}

pub fn ordinary_provider_endpoint(base: &str, provider_id: &str, route: &str) -> String {
    if provider_id == "RELAYCORE_CLAUDE" && matches!(route, "NONE" | "RELAYCORE_COMPANION") {
        format!("{}/messages", base.trim_end_matches('/'))
    } else {
        provider_endpoint(base, route)
    }
}

fn messages_api_input(messages: &Value) -> (String, Vec<Value>) {
    let input = messages.as_array().cloned().unwrap_or_default();
    let system = input.iter().filter(|item| item["role"] == "system")
        .filter_map(|item| item["content"].as_str()).collect::<Vec<_>>().join("\n");
    let messages = input.into_iter().filter(|item| item["role"] != "system").map(|mut item| {
        if let Some(parts) = item["content"].as_array() {
            item["content"] = Value::Array(parts.iter().map(|part| {
                if part["type"] != "image_url" { return part.clone(); }
                let url = part["image_url"]["url"].as_str().unwrap_or("");
                if let Some((mime, data)) = url.strip_prefix("data:").and_then(|value| value.split_once(";base64,")) {
                    json!({"type":"image","source":{"type":"base64","media_type":mime,"data":data}})
                } else { json!({"type":"image","source":{"type":"url","url":url}}) }
            }).collect());
        }
        item
    }).collect();
    (system, messages)
}

pub(crate) const LIVE_SEARCH_INSTRUCTION: &str = "本轮已启用实时网页搜索。以联网开关为准，必须实际执行搜索，不按问题关键词判断是否联网。若本请求已附服务端实际搜索资料则据此作答；否则在回答前调用已提供的搜索工具。只查询与问题或图片相关的最小公开关键词，不搜索私人信息。引用实际返回来源；不得把模型记忆或自行编写的网址当作搜索结果。工具失败或实际结果为空时如实说明。";

fn live_search_messages(messages: &Value) -> Value {
    let Some(mut values) = messages.as_array().cloned() else { return messages.clone(); };
    if !values.iter().any(|m| m["role"] == "system" && m["content"].as_str() == Some(LIVE_SEARCH_INSTRUCTION)) {
        values.insert(0, json!({"role":"system","content":LIVE_SEARCH_INSTRUCTION}));
    }
    Value::Array(values)
}

fn muse_responses_search(request: &TransportRequest) -> bool {
    request.provider_id == "OPENROUTER" && request.model_id == "meta/muse-wonder"
        && request.web_search_route == "OPENROUTER_SERVER_TOOL"
}

fn transport_endpoint(request: &TransportRequest) -> String {
    if muse_responses_search(request) {
        request.endpoint.strip_suffix("/chat/completions")
            .map(|base| format!("{base}/responses")).unwrap_or_else(|| request.endpoint.clone())
    } else { request.endpoint.clone() }
}

pub(crate) fn valid_reported_model_id(value: &str) -> Option<&str> {
    let model=value.trim();
    (!model.is_empty() && model!="null" && value.len()<=200 && !value.chars().any(char::is_control)).then_some(model)
}
fn merge_relay_model_identity(current: &mut Option<String>, reported: Option<&str>) {
    if let Some(model) = reported.and_then(valid_reported_model_id) {
        match current.as_deref() {
            None => *current = Some(model.to_owned()),
            Some(previous) if previous != model => *current = Some("inconsistent-response-model".into()),
            _ => {}
        }
    }
}

#[cfg(test)]
fn public_provider_error_metadata(value: &Value) -> Value {
    fn safe_code(value: &Value) -> Value {
        if let Some(n)=value.as_u64().filter(|n|*n<=999999) {return json!(n);}
        let code=value.as_str().unwrap_or("");
        if matches!(code,"rate_limit_exceeded"|"rate_limit_error"|"usage_limit_reached"|"insufficient_quota"|"overloaded_error"|"server_error"|"internal_server_error"|"invalid_request_error"|"authentication_error"|"permission_error"|"billing_error"|"context_length_exceeded"|"request_timeout") {json!(code)}
        else if !code.is_empty() && code.len()<=6 && code.bytes().all(|b|b.is_ascii_digit()) {json!(code)}
        else {Value::Null}
    }
    let error=&value["error"];
    if let Some(raw)=error.pointer("/metadata/raw").and_then(Value::as_str) {
        if let Ok(nested)=serde_json::from_str::<Value>(raw) {
            if nested.get("error").is_some() {return public_provider_error_metadata(&nested);}
        }
    }
    let message=error["message"].as_str().or(value["message"].as_str()).unwrap_or("");
    let field_mentions=["model","input","instructions","max_output_tokens","tools","tool_choice","reasoning","store","stream","service_tier","temperature","search_context_size","name","namespace","additional_tools","type","function","allowed_tools","parameters","cover_all_turns","web_search","web_search_preview"].into_iter().filter(|field|message.contains(field)).collect::<Vec<_>>();
    let known_reasons=[("unsupported_parameter","Unsupported parameter"),("unsupported_value","Unsupported value"),("missing_parameter","Missing required parameter"),("context_limit","context length"),("invalid_model","model does not exist"),("tool_limit","Too many tools"),("rate_limit","rate limit"),("quota_exceeded","quota"),("region_restricted","region"),("country_restricted","country"),("policy_restricted","data policy"),("permission_denied","permission"),("account_disabled","disabled")].into_iter().filter_map(|(code,needle)|message.to_ascii_lowercase().contains(&needle.to_ascii_lowercase()).then_some(code)).collect::<Vec<_>>();
    let terms=["unsupported","supported","not","cannot","required","available","allowed","hosted","built-in","web_search","search","account","plan","subscription","model","streaming","parallel","tool_choice","incompatible","disabled","must","parameter","list","non-empty","nonempty","provided","present","passed","responses Lite","top-level","additional_tools","function","custom","name","namespace","type","allowed_tools","parameters","cover_all_turns","web_search_preview","include","contain","omitted","set","array","dictionary","object","string","format","supplied","null","empty","live","external_web_access","context","value"].into_iter().filter(|term|message.to_ascii_lowercase().contains(term)).collect::<Vec<_>>();
    json!({"reasonTerms":terms,"fieldMentions":field_mentions,"knownReasons":known_reasons,"messageChars":message.chars().count(),"code":safe_code(&error["code"]),"errorType":safe_code(&error["type"]),"topLevelCode":safe_code(&value["code"]),
        "rateLimits": value["rate_limits"].as_object().map(|o|o.iter().filter_map(|(k,v)| {
            if !matches!(k.as_str(),"primary"|"secondary") {return None;}
            let numbers=v.as_object().map(|n|n.iter().filter_map(|(key,val)| {
                if matches!(key.as_str(),"used_percent"|"window_minutes"|"reset_at"|"resets_at"|"reset_after_seconds") && val.is_number() {Some((key.clone(),val.clone()))}else{None}
            }).collect::<std::collections::BTreeMap<_,_>>()).unwrap_or_default();Some((k.clone(),numbers))
        }).collect::<std::collections::BTreeMap<_,_>>())})
}

#[cfg(test)]
pub(crate) async fn public_native_search_shape_probe(request:TransportRequest,secret:zeroize::Zeroizing<Vec<u8>>,kind:&str)->Result<serde_json::Value,Failure> {
    let mut body=transport_body(&request);
    if kind=="native-forced" {body["tool_choice"]=json!({"type":"tool","name":"web_search"});}
    if kind=="plain-noeffort" {body.as_object_mut().unwrap().remove("output_config");}
    if kind=="public-glm-search-pro" {body["tools"][0]["web_search"]["search_engine"]=json!("search_pro");}
    if kind=="public-glm-search-max" {body["reasoning_effort"]=json!("max");}
    if kind=="public-glm-search-low" {body["reasoning_effort"]=json!("low");}
    if kind=="public-glm-search-std" {body["tools"][0]["web_search"]["search_engine"]=json!("search_std");}
    if kind=="plain-completions" {
        body=json!({"model":request.model_id,"messages":request.messages,"max_tokens":8192,"reasoning_effort":"medium"});
    }
    body["stream"]=json!(false);
    let auth=std::str::from_utf8(&secret).map_err(|_|Failure::Explicit{code:"AUTHENTICATION",http_status:None})?;
    let response=reqwest::Client::builder().redirect(reqwest::redirect::Policy::none()).timeout(std::time::Duration::from_secs(180)).build().unwrap()
        .post(transport_endpoint(&request)).bearer_auth(auth).header("x-api-key",auth).header("anthropic-version","2023-06-01").json(&body).send().await.map_err(|_|Failure::Unknown{code:"NETWORK"})?;
    let status=response.status().as_u16();let value:serde_json::Value=response.json().await.map_err(|_|Failure::Explicit{code:"RESPONSE_FORMAT",http_status:Some(status)})?;
    let blocks=value["content"].as_array().into_iter().flatten().collect::<Vec<_>>();
    let choice_text=value.pointer("/choices/0/message/content").and_then(Value::as_str).unwrap_or("");
    let fields=["tool_choice","thinking","output_config","web_search","model"].iter().filter(|field|value["error"]["message"].as_str().unwrap_or("").contains(**field)).copied().collect::<Vec<_>>();
    Ok(json!({"httpStatus":status,"model":value["model"],"stopReason":value["stop_reason"],"choiceFinishReason":value.pointer("/choices/0/finish_reason"),"blockTypes":blocks.iter().filter_map(|x|x["type"].as_str()).collect::<Vec<_>>(),
        "toolNames":blocks.iter().filter_map(|x|x["name"].as_str()).collect::<Vec<_>>(),"resultKinds":blocks.iter().filter(|x|x["type"]=="web_search_tool_result").flat_map(|x|x["content"].as_array().into_iter().flatten()).filter_map(|x|x["type"].as_str()).collect::<Vec<_>>(),
        "errorType":value["error"]["type"],
        "errorFields":fields,
        "nativeSourceCount":response_sources(&value).len(),
        "correct":choice_text.contains("5%")||blocks.iter().filter_map(|x|x["text"].as_str()).any(|text|text.contains("5%")||text.contains("百分之五")),"textChars":choice_text.chars().count()+blocks.iter().filter_map(|x|x["text"].as_str()).map(|x|x.chars().count()).sum::<usize>(),
        "requestedEffort":body["reasoning_effort"],"maxTokens":body["max_tokens"],"thinking":body["thinking"],"engine":body["tools"][0]["web_search"]["search_engine"],"inputTokens":value["usage"]["input_tokens"].as_i64().or(value["usage"]["prompt_tokens"].as_i64()),"outputTokens":value["usage"]["output_tokens"].as_i64().or(value["usage"]["completion_tokens"].as_i64())}))
}

fn relay_search_complex_question(messages: &Value) -> bool {
    let question = messages.as_array().into_iter().flatten().filter(|item| item["role"] == "user").last()
        .map(|item| match &item["content"] {
            Value::String(text) => text.clone(),
            Value::Array(parts) => parts.iter().filter(|part| part["type"] == "text" || part["type"] == "input_text")
                .filter_map(|part| part["text"].as_str()).collect::<Vec<_>>().join("\n"),
            _ => String::new(),
        }).unwrap_or_default().to_lowercase();
    question.chars().count() >= 2400 || ["深入分析","深度分析","系统分析","完整方案","方案设计","架构设计","重构","代码审计","根因","权衡","情景分析","风险传导","投资分析","估值分析","长期规划","多步骤推理","复杂推理","调试","复杂项目","报错","debug","bug","stacktrace","exception","compile","architecture","root cause","root-cause","audit","trade-off","trade off","migration","refactor","deep analysis","multi-step reasoning","multi step reasoning"]
        .iter().any(|signal| question.contains(signal))
}

// Refusal is a typed Provider result, not a phrase inferred from answer text.
fn has_provider_refusal(value: &Value, responses: bool) -> bool {
    let nonempty = |v: &Value| v.as_str().is_some_and(|s| !s.trim().is_empty());
    let parts = |item: &Value| item["content"].as_array().is_some_and(|parts|
        parts.iter().any(|p| p["type"] == "refusal" && nonempty(&p["refusal"])));
    if !responses {
        return nonempty(&value["choices"][0]["delta"]["refusal"])
            || nonempty(&value["choices"][0]["message"]["refusal"]);
    }
    match value["type"].as_str() {
        Some("response.refusal.delta") => nonempty(&value["delta"]),
        Some("response.refusal.done") => nonempty(&value["refusal"]),
        Some("response.content_part.added" | "response.content_part.done") =>
            value["part"]["type"] == "refusal" && nonempty(&value["part"]["refusal"]),
        Some("response.output_item.added" | "response.output_item.done") => parts(&value["item"]),
        _ => value.get("response").unwrap_or(value)["output"].as_array().is_some_and(|items| items.iter().any(parts)),
    }
}

pub(crate) fn transport_body(request: &TransportRequest) -> Value {
    if request.provider_id == "RELAYCORE_CLAUDE" && request.web_search_route == "NONE" {
        let (system, messages) = messages_api_input(&request.messages);
        return json!({"model":request.model_id,"system":system,"messages":messages,"stream":true,
            "max_tokens":request.max_output_tokens,"output_config":{"effort":if matches!(request.model_id.as_str(),
                "claude-opus-5-5" | "claude-opus-5.5") {"medium"} else {"high"}}});
    }
    if muse_responses_search(request) {
        let messages = if request.grounded_answer { request.messages.clone() } else { live_search_messages(&request.messages) };
        let mut body = json!({"model":request.model_id,"input":responses_input(&messages),
            "store":false,"stream":true,"max_output_tokens":request.max_output_tokens});
        if !request.grounded_answer { body["tools"] = json!([{"type":"openrouter:web_search"}]); }
        return body;
    }
    let mut body = match request.web_search_route.as_str() {
        "RELAYCORE_GPT_RESPONSES" => {
            let input = relay_responses_input(&request.messages);
            json!({"model":request.model_id,"input":input,
                "tools":[{"type":"web_search","external_web_access":true}],"tool_choice":"required",
                "include":["web_search_call.action.sources"],"store":false,"stream":true,
                "max_output_tokens":request.max_output_tokens})
        },
        "QWEN_RESPONSES" => json!({
            "model":request.model_id,"input":responses_input(&request.messages),"tools":[{"type":"web_search"}],
            "store":false,"stream":true,"max_output_tokens":request.max_output_tokens
        }),
        "DEEPSEEK_MESSAGES" => {
            let messages = request.messages.as_array().cloned().unwrap_or_default();
            let system = messages.iter().filter(|m| m["role"] == "system").filter_map(|m| m["content"].as_str()).collect::<Vec<_>>().join("\n");
            let messages: Vec<Value> = messages.into_iter().filter(|m| m["role"] != "system").map(|mut m| {
                if let Some(parts) = m["content"].as_array() {
                    m["content"] = Value::Array(parts.iter().map(|part| {
                        if part["type"] == "image_url" {
                            let url = part["image_url"]["url"].as_str().unwrap_or("");
                            if let Some((mime, data)) = url.strip_prefix("data:").and_then(|v| v.split_once(";base64,")) {
                                json!({"type":"image","source":{"type":"base64","media_type":mime,"data":data}})
                            } else { json!({"type":"image","source":{"type":"url","url":url}}) }
                        } else { part.clone() }
                    }).collect());
                }
                m
            }).collect();
            json!({"model":request.model_id,"system":system,"messages":messages,
                "tools":[{"type":"web_search_20250305","name":"web_search","max_uses":5}],
                "tool_choice":{"type":"tool","name":"web_search"},"stream":false,"max_tokens":request.max_output_tokens})
        },
        _ => {
            let mut body = json!({
                "model":request.model_id,"messages":request.messages,"stream":true,
                "stream_options":{"include_usage":true},"max_tokens":request.max_output_tokens,"temperature":0.2
            });
            let object = body.as_object_mut().expect("request body is an object");
            if request.provider_id == "OPENROUTER" && request.web_search_route == "OPENROUTER_SERVER_TOOL" && !request.grounded_answer {
                object.insert("messages".into(), live_search_messages(&request.messages));
            }
            if request.provider_id.starts_with("RELAYCORE_") { object.remove("temperature"); }
            if request.structured_json {
                object.insert("response_format".into(), json!({"type":"json_object"}));
            }
            if request.disable_thinking && request.provider_id == "DEEPSEEK" {
                object.insert("thinking".into(), json!({"type":"disabled"}));
            }
            if request.provider_id == "ZHIPU"
                && matches!(request.model_id.as_str(), "glm-5.3" | "glm-5.3-flash")
            {
                object.insert("thinking".into(), json!({"type":"enabled"}));
                object.insert("reasoning_effort".into(), json!("max"));
            }
            if request.provider_id == "OPENROUTER"
                && matches!(
                    request.model_id.as_str(),
                    "anthropic/claude-fable-5.1-20260831"
                        | "anthropic/claude-opus-5"
                        | "openai/gpt-6-astra"
                        | "openai/gpt-5.6-sol"
                )
            {
                object.insert("reasoning".into(), json!({"effort":"high"}));
            }
            match request.web_search_route.as_str() {
                "OPENROUTER_LUNA_SEARCH" => {
                    object.insert("tools".into(), json!([{"type":"openrouter:web_search","parameters":{"engine":"native"}}]));
                    object.insert("tool_choice".into(), json!("required"));
                    object.insert("provider".into(), json!({"only":["OpenAI"],"allow_fallbacks":false}));
                }
                "OPENROUTER_SERVER_TOOL" => {
                    if request.provider_id == "OPENROUTER" && matches!(request.model_id.as_str(), "google/gemini-3.8-flash" | "google/gemini-4-argon" | "anthropic/claude-haiku-5.5") {
                        object.insert("plugins".into(), json!([{"id":"web","engine":if request.model_id == "anthropic/claude-haiku-5.5" {"exa"} else {"native"},"max_results":5}]));
                    } else {
                        object.insert("tools".into(), json!([{"type":"openrouter:web_search","parameters":{"max_results":5,"max_total_results":10}}]));
                    }
                }
                "QWEN_CHAT_COMPLETIONS" => {
                    object.insert("enable_search".into(), Value::Bool(true));
                    object.insert("search_options".into(), json!({"forced_search":true}));
                }
                "ZHIPU_CHAT_COMPLETIONS" => {
                    object.insert("tools".into(), json!([{"type":"web_search","web_search":{"enable":true,"search_engine":"search_std","search_result":true,"count":5,"content_size":"medium"}}]));
                    object.insert("tool_choice".into(), Value::String("auto".into()));
                }
                _ => {}
            }
            if request.provider_id == "OPENROUTER" && request.model_id == "openai/gpt-6.1-terra" { object.remove("temperature"); }
            if request.provider_id == "OPENROUTER" {
                if let Some(effort) = crate::desktop_model_service_v1::default_reasoning_effort(&request.model_id) {
                    object.insert("reasoning".into(), json!({"effort":effort}));
                    object.remove("temperature");
                }
            }
            body
        }
    };
    if request.web_search_route == "RELAYCORE_GPT_RESPONSES" {
        let complex = relay_search_complex_question(&request.messages);
        body["tools"][0]["search_context_size"] = json!(if complex {"medium"} else {"low"});
        body["reasoning"] = json!({"effort":if complex {"medium"} else {"low"}});
        use sha2::Digest;
        body["prompt_cache_key"] = json!(format!("{:x}", sha2::Sha256::digest(format!(
            "nanfeng-relay-routing-v1:{}:{}:{}", request.provider_id, request.model_id, request.idempotency_key).as_bytes())));
    }
    if request.provider_id == "QWEN" && request.model_id == "qwen3.8-max" {
        if request.web_search_route == "QWEN_RESPONSES" {
            body["enable_thinking"] = json!(true);
            body["reasoning"] = json!({"effort":"low"});
        } else {
            body.as_object_mut().unwrap().remove("max_tokens");
            body["max_completion_tokens"] = json!(request.max_output_tokens.min(16_384));
            body["reasoning_effort"] = json!("low");
            body["preserve_thinking"] = json!(false);
        }
    }
    if request.grounded_answer {
        if let Some(object) = body.as_object_mut() {
            for key in ["tools", "tool_choice", "enable_search", "search_options", "plugins"] { object.remove(key); }
            if request.provider_id == "RELAYCORE_GEMINI" { object.remove("max_tokens"); }
        }
    }
    if request.provider_id=="OPENROUTER" && request.messages.as_array().into_iter().flatten().filter_map(|m|m["content"].as_array()).flatten().any(|p|p["type"]=="file") {
        if let Some(plugins)=body.as_object_mut().unwrap().entry("plugins").or_insert_with(||json!([])).as_array_mut(){plugins.push(json!({"id":"file-parser","pdf":{"engine":"native"}}));}
    }
    body
}

pub fn ordinary_output_token_limit(provider: &str, model: &str) -> u32 {
    if provider == "ZHIPU" && matches!(model, "glm-5.3" | "glm-5.3-flash") {
        32_768
    } else if provider == "QWEN" && model == "qwen3.8-max" {
        16_384
    } else {
        8192
    }
}

/// Background title generation is deliberately a normal JSON request.  It shares the hardened
/// provider decoder with ordinary chat but never opens a streaming UI lifecycle.
fn non_streaming_body(request: &TransportRequest) -> Value {
    let mut body = transport_body(request);
    body["stream"] = Value::Bool(false);
    body.as_object_mut()
        .map(|object| object.remove("stream_options"));
    if request.provider_id == "ZHIPU" {
        body["thinking"] = json!({"type":"enabled"});
        body["reasoning_effort"] = json!("max");
    }
    body
}

pub async fn execute_non_streaming(
    request: TransportRequest,
    mut secret: Zeroizing<Vec<u8>>,
) -> Result<Completed, Failure> {
    let authorization =
        Zeroizing::new(
            String::from_utf8(secret.to_vec()).map_err(|_| Failure::Explicit {
                code: "CREDENTIAL_FORMAT",
                http_status: None,
            })?,
        );
    secret.zeroize();
    let body = non_streaming_body(&request);
    let started = Instant::now();
    let client = provider_client_builder(&request.endpoint)
        .redirect(reqwest::redirect::Policy::none())
        .connect_timeout(Duration::from_secs(15))
        .timeout(Duration::from_secs(if request.provider_id == "DEEPSEEK" {
            90
        } else {
            180
        }))
        .build()
        .map_err(|_| Failure::Unknown { code: "CLIENT" })?;
    let mut builder = client.post(transport_endpoint(&request))
        .bearer_auth(authorization.as_str())
        .header("Idempotency-Key", &request.idempotency_key)
        .header("Accept", "application/json");
    if request.provider_id == "RELAYCORE_CLAUDE" || request.web_search_route == "DEEPSEEK_MESSAGES" {
        builder = builder.header("x-api-key", authorization.as_str()).header("anthropic-version", "2023-06-01");
    }
    let response = builder.json(&body)
        .send()
        .await
        .map_err(|error| {
            if error.is_timeout() {
                Failure::Unknown { code: "TIMEOUT" }
            } else {
                Failure::Unknown { code: "NETWORK" }
            }
        })?;
    let status = response.status().as_u16();
    if !(200..300).contains(&status) {
        return Err(Failure::Explicit {
            code: safe_http_code(status),
            http_status: Some(status),
        });
    }
    let bytes = read_bounded_response(response, Duration::from_secs(180).saturating_sub(started.elapsed()),
        &Arc::new(AtomicBool::new(false))).await?;
    decode_non_streaming_request(&request, &bytes, started.elapsed().as_millis().min(i64::MAX as u128) as i64)

}

fn decoded_measurement(reply: &Completed) -> ReportedMeasurement {
    ReportedMeasurement { usage: reply.usage.clone(), reported_cost_micros:reply.reported_cost_micros,
        actual_model_id:reply.actual_model_id.clone(), elapsed_ms:reply.elapsed_ms }
}
fn measured_non_streaming_failure(code: &'static str, measurement: ReportedMeasurement) -> Failure {
    if measurement.has_reported_facts() { Failure::MeasuredExplicit { code, http_status:None, measurement } }
    else { Failure::Explicit { code, http_status:None } }
}
fn responses_measurement(value:&Value,elapsed_ms:i64)->ReportedMeasurement {
    let usage=&value["usage"];
    ReportedMeasurement { usage:Usage {
            input_tokens:usage["input_tokens"].as_i64().or_else(||usage["prompt_tokens"].as_i64()),
            output_tokens:usage["output_tokens"].as_i64().or_else(||usage["completion_tokens"].as_i64()),
            cached_input_tokens:usage.pointer("/input_tokens_details/cached_tokens").and_then(Value::as_i64).or_else(||usage.pointer("/prompt_tokens_details/cached_tokens").and_then(Value::as_i64)),
            reasoning_tokens:usage.pointer("/output_tokens_details/reasoning_tokens").and_then(Value::as_i64),
        },reported_cost_micros:cost_micros(usage.get("cost")),actual_model_id:value["model"].as_str().and_then(valid_reported_model_id).map(str::to_owned),elapsed_ms}
}

fn decode_non_streaming_request(request:&TransportRequest, bytes:&[u8], elapsed_ms:i64) -> Result<Completed,Failure> {
    let responses = muse_responses_search(request) || matches!(request.web_search_route.as_str(),"QWEN_RESPONSES"|"RELAYCORE_GPT_RESPONSES");
    let value = if responses { Some(serde_json::from_slice::<Value>(bytes).map_err(|_|Failure::Explicit{code:"RESPONSE_FORMAT",http_status:None})?) } else { None };
    let decoded = if let Some(value)=value.as_ref() {
        let measurement=responses_measurement(value,elapsed_ms);
        if has_provider_refusal(value,true) {
            Err(measured_non_streaming_failure("PROVIDER_REFUSAL",measurement))
        } else if value["status"]!="completed" {
            let code=if value.pointer("/incomplete_details/reason").and_then(Value::as_str)==Some("max_output_tokens") {"OUTPUT_LIMIT"} else {"RESPONSE_INCOMPLETE"};
            Err(measured_non_streaming_failure(code,measurement))
        } else {
            decode_responses_non_streaming(bytes,elapsed_ms).map_err(|failure| match failure {
                Failure::Explicit{code,..}=>measured_non_streaming_failure(code,measurement),other=>other,
            })
        }
    } else if matches!(request.web_search_route.as_str(),"DEEPSEEK_MESSAGES"|"RELAYCORE_CLAUDE_MESSAGES") || request.provider_id=="RELAYCORE_CLAUDE" {
        decode_messages(bytes,elapsed_ms,!request.grounded_answer&&request.web_search_route!="NONE")
    } else {decode_non_streaming(bytes,elapsed_ms)};
    let normalize=|measurement:&mut ReportedMeasurement| {
        if request.provider_id.starts_with("RELAYCORE_") {
            let mut identity=None;merge_relay_model_identity(&mut identity,measurement.actual_model_id.as_deref());measurement.actual_model_id=identity;
            measurement.reported_cost_micros=None;
        }
    };
    let mut reply=decoded.map_err(|mut failure| {if let Failure::MeasuredExplicit{measurement,..}=&mut failure {normalize(measurement);}failure})?;
    if request.provider_id.starts_with("RELAYCORE_") {let mut m=decoded_measurement(&reply);normalize(&mut m);reply.actual_model_id=m.actual_model_id;reply.reported_cost_micros=None;}
    let completed_tool=value.as_ref().is_some_and(completed_web_search_call);
    if !request.grounded_answer && request.web_search_route!="NONE" && (!reply.web_search_verified || reply.native_sources.is_empty() || request.web_search_route=="RELAYCORE_GPT_RESPONSES"&&!completed_tool) {
        return Err(measured_non_streaming_failure("WEB_SEARCH_NO_SOURCES",decoded_measurement(&reply)));
    }
    Ok(reply)
}

fn public_source_url(url: &str) -> Option<String> {
    let parsed = Url::parse(url).ok()?;
    if !matches!(parsed.scheme(), "http" | "https")
        || parsed.host_str().is_none()
        || !parsed.username().is_empty()
        || parsed.password().is_some()
    {
        return None;
    }
    Some(parsed.to_string())
}

fn response_sources(value: &Value) -> Vec<(String, String)> {
    fn source(value: &Value) -> Option<(String, String)> {
        let url = value
            .get("url")
            .or_else(|| value.get("link"))?
            .as_str()?
            .trim();
        let canonical_url = public_source_url(url)?;
        let title = value
            .get("title")
            .or_else(|| value.get("name"))
            .and_then(Value::as_str)
            .unwrap_or(&canonical_url)
            .trim();
        Some((title.to_owned(), canonical_url))
    }

    fn push_unique(values: &mut Vec<(String, String)>, candidate: Option<(String, String)>) {
        if let Some(candidate) = candidate {
            if values.len() < 10 && !values.iter().any(|item| item.1 == candidate.1) {
                values.push(candidate);
            }
        }
    }

    let mut values = value
        .get("output")
        .and_then(Value::as_array)
        .into_iter()
        .flatten()
        .filter(|item| item.get("type").and_then(Value::as_str) == Some("web_search_call")
            && item.get("status").is_none_or(|status| status.as_str() == Some("completed")))
        .flat_map(|item| {
            item.get("action")
                .and_then(|action| action.get("sources"))
                .and_then(Value::as_array)
                .into_iter()
                .flatten()
        })
        .filter_map(source)
        .fold(Vec::<(String, String)>::new(), |mut values, source| {
            if !values.iter().any(|item| item.1 == source.1) && values.len() < 10 {
                values.push(source);
            }
            values
        });

    // Responses places provider-owned citations on output message content, not choices.
    // Only consume that typed annotation surface; tool arguments and arbitrary text are not proof.
    for message in value.get("output").and_then(Value::as_array).into_iter().flatten().filter(|m|m["type"]=="message") {
        for content in message.get("content").and_then(Value::as_array).into_iter().flatten().filter(|c|matches!(c["type"].as_str(),Some("output_text"|"text"))) {
            for annotation in content.get("annotations").and_then(Value::as_array).into_iter().flatten() {
                if annotation["type"] == "url_citation" {
                    push_unique(&mut values, source(annotation.get("url_citation").unwrap_or(annotation)));
                }
            }
        }
    }

    for choice in value
        .get("choices")
        .and_then(Value::as_array)
        .into_iter()
        .flatten()
    {
        for envelope in [choice.get("message"), choice.get("delta")]
            .into_iter()
            .flatten()
        {
            for annotation in envelope
                .get("annotations")
                .and_then(Value::as_array)
                .into_iter()
                .flatten()
            {
                // An explicit discriminator cannot fall back to a URL-shaped
                // file/other annotation. Keep existing untyped legacy replies.
                if annotation.get("type").is_some_and(|kind| kind.as_str() != Some("url_citation")) {
                    continue;
                }
                push_unique(
                    &mut values,
                    source(annotation.get("url_citation").unwrap_or(annotation)),
                );
            }
        }
    }

    for item in value
        .get("web_search")
        .and_then(Value::as_array)
        .into_iter()
        .flatten()
    {
        push_unique(&mut values, source(item));
        for key in ["search_result", "search_results", "results", "sources"] {
            for candidate in item
                .get(key)
                .and_then(Value::as_array)
                .into_iter()
                .flatten()
            {
                push_unique(&mut values, source(candidate));
            }
        }
    }
    values
}

fn completed_web_search_call(value: &Value) -> bool {
    value.get("output").and_then(Value::as_array).into_iter().flatten()
        .any(|item| item["type"] == "web_search_call" && item["status"] == "completed")
}

fn has_web_search_evidence(value: &Value) -> bool {
    !response_sources(value).is_empty()
        || value
            .get("output")
            .and_then(Value::as_array)
            .into_iter()
            .flatten()
            .any(|item| item.get("type").and_then(Value::as_str) == Some("web_search_call") && matches!(item.get("status").and_then(Value::as_str), None | Some("completed")))
}

fn markdown_source_label(title: &str) -> String {
    let mut label = String::new();
    for character in title.chars().take(200) {
        if character.is_whitespace() || character.is_control() {
            if !label.ends_with(' ') {
                label.push(' ');
            }
        } else {
            if matches!(character, '\\' | '[' | ']' | '<' | '>' | '`' | '*' | '_' | '~') {
                label.push('\\');
            }
            label.push(character);
        }
    }
    label.trim().to_owned()
}

pub(crate) fn append_sources(text: &mut String, sources: &[(String, String)]) {
    if sources.is_empty() {
        return;
    }
    text.push_str("\n\n来源：");
    for (title, url) in sources {
        let title = markdown_source_label(title);
        let url = url.replace('(', "%28").replace(')', "%29");
        text.push_str(&format!("\n- [{title}]({url})"));
    }
}

fn decode_responses_non_streaming(bytes: &[u8], elapsed_ms: i64) -> Result<Completed, Failure> {
    if bytes.is_empty() || bytes.len() > MAX_RESPONSE_BYTES {
        return Err(Failure::Unknown {
            code: "RESPONSE_SIZE",
        });
    }
    let value: Value = serde_json::from_slice(bytes).map_err(|_| Failure::Explicit {
        code: "RESPONSE_FORMAT",
        http_status: None,
    })?;
    if has_provider_refusal(&value,true) {
        return Err(measured_non_streaming_failure("PROVIDER_REFUSAL",responses_measurement(&value,elapsed_ms)));
    }
    let mut text = value
        .get("output_text")
        .and_then(Value::as_str)
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .map(ToOwned::to_owned)
        .or_else(|| {
            value
                .get("output")
                .and_then(Value::as_array)
                .into_iter()
                .flatten()
                .filter(|item| item.get("type").and_then(Value::as_str) == Some("message"))
                .flat_map(|item| {
                    item.get("content")
                        .and_then(Value::as_array)
                        .into_iter()
                        .flatten()
                })
                .filter(|item| {
                    matches!(
                        item.get("type").and_then(Value::as_str),
                        Some("output_text" | "text")
                    )
                })
                .filter_map(|item| item.get("text").and_then(Value::as_str))
                .collect::<String>()
                .trim()
                .to_owned()
                .into()
        })
        .filter(|value| !value.is_empty())
        .ok_or(Failure::Explicit {
            code: "RESPONSE_FORMAT",
            http_status: None,
        })?;
    let sources = response_sources(&value);
    let web_search_verified = !sources.is_empty();
    append_sources(&mut text, &sources);
    let usage = value.get("usage");
    Ok(Completed {
        text,
        reasoning: None,
        usage: Usage {
            input_tokens: usage
                .and_then(|v| v.get("input_tokens").or_else(|| v.get("prompt_tokens")))
                .and_then(Value::as_i64),
            output_tokens: usage
                .and_then(|v| {
                    v.get("output_tokens")
                        .or_else(|| v.get("completion_tokens"))
                })
                .and_then(Value::as_i64),
            cached_input_tokens: usage
                .and_then(|v| {
                    v.get("input_tokens_details")
                        .or_else(|| v.get("prompt_tokens_details"))
                })
                .and_then(|v| v.get("cached_tokens"))
                .and_then(Value::as_i64),
            reasoning_tokens: usage
                .and_then(|v| {
                    v.get("output_tokens_details")
                        .or_else(|| v.get("completion_tokens_details"))
                })
                .and_then(|v| v.get("reasoning_tokens"))
                .and_then(Value::as_i64),
        },
        reported_cost_micros: cost_micros(usage.and_then(|v| v.get("cost"))),
        actual_model_id: value
            .get("model")
            .and_then(Value::as_str)
            .and_then(valid_reported_model_id)
            .map(ToOwned::to_owned),
        web_search_verified,
        elapsed_ms,

        native_sources: sources,
    })
}

pub fn decode_non_streaming(bytes: &[u8], elapsed_ms: i64) -> Result<Completed, Failure> {
    if bytes.is_empty() || bytes.len() > MAX_RESPONSE_BYTES {
        return Err(Failure::Unknown {
            code: "RESPONSE_SIZE",
        });
    }
    let value: Value = serde_json::from_slice(bytes).map_err(|_| Failure::Explicit {
        code: "RESPONSE_FORMAT",
        http_status: None,
    })?;
    let measurement = ReportedMeasurement {
        usage: parse_usage(value.get("usage")),
        reported_cost_micros: cost_micros(value.get("usage").and_then(|usage| usage.get("cost"))),
        actual_model_id: value.get("model").and_then(Value::as_str).and_then(valid_reported_model_id).map(ToOwned::to_owned),
        elapsed_ms,
    };
    let failed = |code| {
        if measurement.has_reported_facts() {
            Failure::MeasuredExplicit { code, http_status: None, measurement: measurement.clone() }
        } else {
            Failure::Explicit { code, http_status: None }
        }
    };
    if has_provider_refusal(&value,false) { return Err(failed("PROVIDER_REFUSAL")); }
    match value.pointer("/choices/0/finish_reason").and_then(Value::as_str) {
        Some("length" | "max_tokens") => return Err(failed("OUTPUT_LIMIT")),
        Some("content_filter") => return Err(failed("CONTENT_FILTER")),
        _ => {}
    }
    let text = value
        .get("choices")
        .and_then(|choices| choices.get(0))
        .and_then(|choice| choice.get("message"))
        .and_then(|message| message.get("content"))
        .and_then(message_content_text)
        .ok_or_else(|| failed("RESPONSE_FORMAT"))?;
    let reasoning = value
        .get("choices")
        .and_then(|choices| choices.get(0))
        .and_then(|choice| choice.get("message"))
        .and_then(|message| {
            message
                .get("reasoning_content")
                .or_else(|| message.get("reasoning"))
        })
        .and_then(Value::as_str)
        .map(str::trim)
        .filter(|text| !text.is_empty())
        .map(ToOwned::to_owned);
    Ok(Completed {
        text,
        reasoning,
        usage: measurement.usage,
        reported_cost_micros: measurement.reported_cost_micros,
        actual_model_id: measurement.actual_model_id,
        web_search_verified: !response_sources(&value).is_empty(),
        elapsed_ms,

        native_sources: response_sources(&value),
    })
}

fn decode_deepseek_messages(bytes: &[u8], elapsed_ms: i64) -> Result<Completed, Failure> {
    decode_messages(bytes, elapsed_ms, true)
}

fn decode_messages(bytes: &[u8], elapsed_ms: i64, require_sources: bool) -> Result<Completed, Failure> {
    let fail = |code| Failure::Explicit { code, http_status: None };
    if bytes.len() > MAX_RESPONSE_BYTES { return Err(fail("RESPONSE_SIZE")); }
    let value: Value = serde_json::from_slice(bytes).map_err(|_| fail("RESPONSE_FORMAT"))?;
    let usage = &value["usage"];
    let measurement = ReportedMeasurement {
        usage: MessagesUsageAccumulator::default().update(usage),
        reported_cost_micros: cost_micros(usage.get("cost")),
        actual_model_id: value["model"].as_str().and_then(valid_reported_model_id).map(ToOwned::to_owned),
        elapsed_ms,
    };
    let blocks = value["content"].as_array()
        .ok_or_else(|| measured_non_streaming_failure("RESPONSE_FORMAT",measurement.clone()))?;
    if value["stop_reason"] == "refusal" {
        return Err(measured_non_streaming_failure("PROVIDER_REFUSAL",measurement));
    }
    if value["stop_reason"] != "end_turn" {
        let code = if value["stop_reason"] == "max_tokens" {
            "OUTPUT_LIMIT"
        } else {
            "RESPONSE_INCOMPLETE"
        };
        return Err(if measurement.has_reported_facts() {
            Failure::MeasuredExplicit { code, http_status: None, measurement }
        } else {
            fail(code)
        });
    }
    if !require_sources {
        if blocks.iter().any(|block| block["type"] == "tool_use") {
            return Err(measured_non_streaming_failure("TOOL_CALL_UNSUPPORTED",measurement));
        }
        let text = blocks.iter().filter(|block| block["type"] == "text")
            .filter_map(|block| block["text"].as_str()).collect::<Vec<_>>().join("\n");
        if text.trim().is_empty() {
            return Err(if measurement.has_reported_facts() {
                Failure::MeasuredExplicit { code: "RESPONSE_FORMAT", http_status: None, measurement }
            } else { fail("RESPONSE_FORMAT") });
        }
        return Ok(Completed { text: text.trim().to_owned(), reasoning: None, usage: measurement.usage,
            reported_cost_micros: measurement.reported_cost_micros,
            actual_model_id: value["model"].as_str().and_then(valid_reported_model_id).map(ToOwned::to_owned),
            web_search_verified: false, elapsed_ms ,
                native_sources: Vec::new(),
            });
    }
    let results: Vec<_> = blocks.iter().filter(|b| b["type"] == "web_search_tool_result").collect();
    if results.is_empty() || results.iter().any(|b| !b["content"].is_array()) {
        return Err(if measurement.has_reported_facts() {
            Failure::MeasuredExplicit { code: "WEB_SEARCH_NO_SOURCES", http_status: None, measurement }
        } else {
            fail("WEB_SEARCH_NO_SOURCES")
        });
    }
    let last = blocks.iter().rposition(|b| b["type"] == "web_search_tool_result").unwrap();
    let mut text = blocks.iter().skip(last + 1).filter(|b| b["type"] == "text").filter_map(|b| b["text"].as_str()).collect::<Vec<_>>().join("\n");
    let mut sources = Vec::new();
    for result in results {
        for item in result["content"].as_array().unwrap() {
            if item["type"] == "web_search_result" {
                if let Some(url) = item["url"].as_str().and_then(public_source_url) {
                    if !sources.iter().any(|(_, existing)| existing == &url) {
                        sources.push((item["title"].as_str().unwrap_or(&url).to_owned(), url));
                    }
                }
            }
        }
    }
    if sources.is_empty() {
        return Err(if measurement.has_reported_facts() {
            Failure::MeasuredExplicit { code: "WEB_SEARCH_NO_SOURCES", http_status: None, measurement }
        } else {
            fail("WEB_SEARCH_NO_SOURCES")
        });
    }
    if text.trim().is_empty() {
        return Err(if measurement.has_reported_facts() {
            Failure::MeasuredExplicit { code: "RESPONSE_FORMAT", http_status: None, measurement }
        } else {
            fail("RESPONSE_FORMAT")
        });
    }
    append_sources(&mut text, &sources);
    Ok(Completed { text, reasoning: None,
        usage: measurement.usage,
        reported_cost_micros: measurement.reported_cost_micros, actual_model_id: measurement.actual_model_id, web_search_verified: true, elapsed_ms ,
            native_sources: sources,
        })
}

async fn await_http_or_cancel<T>(
    future: impl std::future::Future<Output = Result<T, reqwest::Error>>,
    wait: Duration,
    cancelled: &AtomicBool,
    timeout_code: &'static str,
) -> Result<T, Failure> {
    if cancelled.load(Ordering::SeqCst) { return Err(Failure::Cancelled); }
    let mut cancellation_tick = tokio::time::interval(Duration::from_millis(50));
    tokio::pin!(future);
    let value = tokio::time::timeout(wait, async {
        loop {
            tokio::select! {
                biased;
                _ = cancellation_tick.tick() => {
                    if cancelled.load(Ordering::SeqCst) { return Err(Failure::Cancelled); }
                }
                result = &mut future => {
                    return result.map_err(|error| if error.is_timeout() {
                        Failure::Unknown { code: "TIMEOUT" }
                    } else {
                        Failure::Unknown { code: "NETWORK" }
                    });
                }
            }
        }
    }).await.map_err(|_| Failure::Unknown { code: timeout_code })??;
    if cancelled.load(Ordering::SeqCst) { return Err(Failure::Cancelled); }
    Ok(value)
}

async fn read_bounded_response(mut response: reqwest::Response, budget: Duration,
    cancelled: &Arc<AtomicBool>) -> Result<Vec<u8>, Failure> {
    let started = Instant::now();
    let mut body = Vec::new();
    while let Some(chunk) = await_http_or_cancel(response.chunk(),
        budget.saturating_sub(started.elapsed()), cancelled, "FIRST_VISIBLE_TIMEOUT").await? {
        if body.len().saturating_add(chunk.len()) > MAX_RESPONSE_BYTES {
            return Err(Failure::Explicit { code: "RESPONSE_SIZE", http_status: None });
        }
        body.extend_from_slice(&chunk);
    }
    Ok(body)
}

pub async fn execute_streaming(
    request: TransportRequest, secret: Zeroizing<Vec<u8>>, cancelled: Arc<AtomicBool>,
    on_delta: impl FnMut(&str) -> Result<(), Failure>,
) -> Result<Completed, Failure> {
    execute_streaming_observed(request,secret,cancelled,on_delta,|_|Ok(())).await
}

fn publish_stream_measurement(
    last: &mut Option<ReportedMeasurement>,
    notify: &mut impl FnMut(&ReportedMeasurement) -> Result<(), Failure>,
    usage: &Usage, reported_cost_micros: Option<i64>, actual_model_id: &Option<String>, elapsed: Duration,
) -> Result<(), Failure> {
    let value=ReportedMeasurement {usage:usage.clone(),reported_cost_micros,actual_model_id:actual_model_id.clone(),
        elapsed_ms:elapsed.as_millis().min(i64::MAX as u128) as i64};
    if !value.has_reported_facts() || last.as_ref().is_some_and(|old|old.usage==value.usage
        && old.reported_cost_micros==value.reported_cost_micros && old.actual_model_id==value.actual_model_id) { return Ok(()); }
    notify(&value).map_err(|failure|match failure {
        Failure::Cancelled|Failure::MeasuredCancelled{..}=>Failure::MeasuredCancelled{measurement:value.clone()},
        Failure::Explicit{code,http_status}|Failure::MeasuredExplicit{code,http_status,..}=>Failure::MeasuredExplicit{code,http_status,measurement:value.clone()},
        Failure::Unknown{code}|Failure::MeasuredUnknown{code,..}=>Failure::MeasuredUnknown{code,measurement:value.clone()},
    })?;
    *last=Some(value);Ok(())
}

pub async fn execute_streaming_observed(
    request: TransportRequest,
    mut secret: Zeroizing<Vec<u8>>,
    cancelled: Arc<AtomicBool>,
    mut on_delta: impl FnMut(&str) -> Result<(), Failure>,
    mut on_measurement: impl FnMut(&ReportedMeasurement) -> Result<(), Failure>,
) -> Result<Completed, Failure> {
    if cancelled.load(Ordering::SeqCst) {
        return Err(Failure::Cancelled);
    }
    if request.provider_id.starts_with("RELAYCORE_") && request.web_search_route != "NONE"
        && !(request.provider_id == "RELAYCORE_GPT" && request.web_search_route == "RELAYCORE_GPT_RESPONSES") {
        // Companion search is an explicitly authorized second call owned above
        // this transport. Never silently substitute an offline reply here.
        return Err(Failure::Explicit { code: "WEB_SEARCH_UNAVAILABLE", http_status: None });
    }
    let authorization =
        Zeroizing::new(
            String::from_utf8(secret.to_vec()).map_err(|_| Failure::Explicit {
                code: "CREDENTIAL_FORMAT",
                http_status: None,
            })?,
        );
    secret.zeroize();
    let body = transport_body(&request);
    let started = Instant::now();
    let client = provider_client_builder(&request.endpoint)
        .redirect(reqwest::redirect::Policy::none())
        .connect_timeout(Duration::from_secs(15))
        .timeout(STREAM_TOTAL_TIMEOUT)
        .build()
        .map_err(|_| Failure::Unknown { code: "CLIENT" })?;
    let mut builder = client.post(transport_endpoint(&request)).bearer_auth(authorization.as_str())
        .header("Idempotency-Key", &request.idempotency_key);
    if request.web_search_route == "DEEPSEEK_MESSAGES" || request.provider_id == "RELAYCORE_CLAUDE" {
        builder = builder.header("x-api-key", authorization.as_str()).header("anthropic-version", "2023-06-01")
            .header("Accept", if request.provider_id == "RELAYCORE_CLAUDE" { "text/event-stream" } else { "application/json" });
    } else { builder = builder.header("Accept", "text/event-stream"); }
    let response = await_http_or_cancel(
        builder.json(&body).send(), STREAM_RESPONSE_TIMEOUT, &cancelled, "FIRST_RESPONSE_TIMEOUT",
    ).await?;
    let status = response.status().as_u16();
    if !(200..300).contains(&status) {
        return Err(Failure::Explicit {
            code: safe_http_code(status),
            http_status: Some(status),
        });
    }
    if request.web_search_route == "DEEPSEEK_MESSAGES" {
        let bytes = read_bounded_response(response, STREAM_TOTAL_TIMEOUT.saturating_sub(started.elapsed()),
            &cancelled).await?;
        let result = decode_messages(
            &bytes, started.elapsed().as_millis().min(i64::MAX as u128) as i64, !request.grounded_answer,
        );
        let facts = match &result { Ok(reply)=>Some(reply.clone()), Err(failure)=>failure.quantitative_completed() };
        if let Some(reply)=facts {
            publish_stream_measurement(&mut None, &mut on_measurement, &reply.usage,
                reply.reported_cost_micros, &reply.actual_model_id, started.elapsed())?;
        }
        return result;
    }
    let responses_stream = matches!(request.web_search_route.as_str(), "QWEN_RESPONSES" | "RELAYCORE_GPT_RESPONSES")
        || muse_responses_search(&request);
    let messages_stream = request.provider_id == "RELAYCORE_CLAUDE";
    let mut messages_end_turn = false;
    let mut text = String::new();
    let mut reasoning = String::new();
    let mut sources = Vec::<(String, String)>::new();
    let mut usage = Usage::default();
    let mut messages_usage = MessagesUsageAccumulator::default();
    let mut last_measurement = None;
    let mut reported_cost_micros = None;
    let mut actual_model_id = None;
    let mut bytes_read = 0usize;
    let mut completed = false;
    let mut stream_ended = false;
    let mut terminal_failure_code: Option<&'static str> = None;
    let mut search_evidence = false;
    let mut native_search_completed = false;
    let mut progress = StreamProgressBudget::default();
    progress.advance(started.elapsed());
    let mut stream = response.bytes_stream();
    let mut pending = Vec::<u8>::new();
    let mut first_stream_line = true;
    let mut skip_line_feed = false;
    let mut json_fragment = String::new();
    'stream: loop {
        if cancelled.load(Ordering::SeqCst) {
            return Err(cancelled_with_progress(&usage, reported_cost_micros, &actual_model_id, started.elapsed()));
        }
        let next = {
            let next_chunk = stream.next();
            tokio::pin!(next_chunk);
            let wait_for = progress.remaining(started.elapsed());
            if wait_for.is_zero() {
                if terminal_failure_code.is_some() { break 'stream; }
                return Err(unknown_with_progress("STREAM_IDLE_TIMEOUT",&usage,reported_cost_micros,&actual_model_id,started.elapsed()));
            }
            let idle = tokio::time::sleep(wait_for);
            tokio::pin!(idle);
            loop {
                tokio::select! {
                    value = &mut next_chunk => break value,
                    _ = &mut idle => {
                        if terminal_failure_code.is_some() { break 'stream; }
                        return Err(unknown_with_progress("STREAM_IDLE_TIMEOUT",&usage,reported_cost_micros,&actual_model_id,started.elapsed()));
                    },
                    _ = tokio::time::sleep(Duration::from_millis(100)) => {
                        if cancelled.load(Ordering::SeqCst) {
                            return Err(cancelled_with_progress(&usage, reported_cost_micros, &actual_model_id, started.elapsed()));
                        }
                    }
                }
            }
        };
        let reached_eof = next.is_none();
        if let Some(chunk) = next {
        let chunk = match chunk {
            Ok(chunk) => chunk,
            Err(error) => {
                if terminal_failure_code.is_some() { break 'stream; }
                return Err(if error.is_timeout() {
                    unknown_with_progress("TIMEOUT",&usage,reported_cost_micros,&actual_model_id,started.elapsed())
                } else {
                    unknown_with_progress("NETWORK",&usage,reported_cost_micros,&actual_model_id,started.elapsed())
                });
            }
        };
        bytes_read = bytes_read.saturating_add(chunk.len());
        if bytes_read > MAX_RESPONSE_BYTES {
            return Err(unknown_with_progress("RESPONSE_SIZE",&usage,reported_cost_micros,&actual_model_id,started.elapsed()));
        }
        pending.extend_from_slice(&chunk);
        } else if pending.is_empty() { break; }
        else { pending.push(b'\n'); }
        // Keep CRLF as one line ending even across chunks: its LF is not an
        // empty event boundary inside a multiline data payload.
        while let Some(end) = pending.iter().position(|byte| matches!(byte, b'\n' | b'\r')) {
            let mut line = pending.drain(..=end).collect::<Vec<_>>();
            let delimiter = line.pop().unwrap();
            if skip_line_feed && delimiter == b'\n' && line.is_empty() {
                skip_line_feed = false;
                continue;
            }
            skip_line_feed = delimiter == b'\r';
            let line = std::str::from_utf8(&line).map_err(|_| format_failure_with_progress(
                status,&usage,reported_cost_micros,&actual_model_id,started.elapsed()))?;
            // SSE ignores one leading UTF-8 BOM, even when its bytes span chunks.
            let line = if std::mem::take(&mut first_stream_line) {
                line.strip_prefix('\u{feff}').unwrap_or(line)
            } else {
                line
            };
            if line.is_empty() && !json_fragment.is_empty() {
                return Err(format_failure_with_progress(status,&usage,reported_cost_micros,&actual_model_id,started.elapsed()));
            }
            let Some(data) = line.strip_prefix("data:").map(str::trim) else {
                continue;
            };
            if data == "[DONE]" {
                if !json_fragment.is_empty() {
                    return Err(format_failure_with_progress(status,&usage,reported_cost_micros,&actual_model_id,started.elapsed()));
                }
                stream_ended = true;
                // Responses requires its own terminal event; [DONE] only closes the wire.
                if !responses_stream && !messages_stream {
                    completed = true;
                }
                break;
            }
            if data.is_empty() {
                continue;
            }
            // A JSON event may span several SSE data fields. Preserve the
            // newline between fields; never repair syntax or concatenate strings.
            // Complete single-line provider frames retain their existing latency.
            if !json_fragment.is_empty() { json_fragment.push('\n'); }
            json_fragment.push_str(data);
            let value: Value = match serde_json::from_str(&json_fragment) {
                Ok(value) => value,
                Err(error) if error.is_eof() => continue,
                Err(_) => return Err(format_failure_with_progress(
                    status,&usage,reported_cost_micros,&actual_model_id,started.elapsed())),
            };
            json_fragment.clear();
            if messages_stream {
                let kind = value["type"].as_str().unwrap_or("");
                let message = &value["message"];
                let delta = &value["delta"];
                let block = &value["content_block"];
                if kind == "message_start" {
                    let reported = &message["usage"];
                    merge_usage(&mut usage, messages_usage.update(reported));
                    merge_relay_model_identity(&mut actual_model_id, message["model"].as_str());
                }
                publish_stream_measurement(&mut last_measurement, &mut on_measurement, &usage, reported_cost_micros, &actual_model_id, started.elapsed())?;
                let answer = if block["type"] == "text" { block["text"].as_str() }
                    else if delta["type"] == "text_delta" { delta["text"].as_str() } else { None };
                if terminal_failure_code.is_none() {
                    if let Some(answer) = answer.filter(|answer| !answer.is_empty()) {
                        progress.advance(started.elapsed()); text.push_str(answer); on_delta(answer).map_err(|failure| match failure {
                            Failure::Cancelled => cancelled_with_progress(&usage, reported_cost_micros, &actual_model_id, started.elapsed()), other => other,
                        })?;
                    }
                    if delta["type"] == "thinking_delta" {
                        if let Some(thinking) = delta["thinking"].as_str().filter(|value| !value.is_empty()) {
                            progress.advance(started.elapsed()); reasoning.push_str(thinking);
                        }
                    }
                }
                if block["type"] == "tool_use" { terminal_failure_code = Some("TOOL_CALL_UNSUPPORTED"); }
                if kind == "error" || value.get("error").is_some_and(|error| !error.is_null()) {
                    terminal_failure_code = Some("PROVIDER_RESPONSE_FAILED");
                }
                if kind == "message_delta" {
                    merge_usage(&mut usage, messages_usage.update(&value["usage"]));
                    if let Some(reason) = delta["stop_reason"].as_str() {
                        if reason == "end_turn" { messages_end_turn = true; }
                        else { terminal_failure_code = Some(match reason { "max_tokens"=>"OUTPUT_LIMIT", "refusal"=>"PROVIDER_REFUSAL", _=>"RESPONSE_INCOMPLETE" }); }
                    }
                }
                publish_stream_measurement(&mut last_measurement, &mut on_measurement, &usage, reported_cost_micros, &actual_model_id, started.elapsed())?;
                if kind == "message_stop" {
                    if !messages_end_turn && terminal_failure_code.is_none() { terminal_failure_code = Some("RESPONSE_INCOMPLETE"); }
                    completed = true; stream_ended = true; break;
                }
                continue;
            }
            // A gateway may report failure inside an otherwise successful HTTP/SSE
            // response after sending visible deltas or a choice finish reason.
            // Never treat the later [DONE] or clean EOF as a successful answer.
            if value.get("error").is_some_and(|error| !error.is_null())
                || value.get("type").and_then(Value::as_str) == Some("error")
            {
                let reported_usage = value.get("usage");
                if responses_stream {
                    merge_usage(&mut usage, Usage {
                        input_tokens: reported_usage.and_then(|item| item.get("input_tokens")).and_then(Value::as_i64),
                        output_tokens: reported_usage.and_then(|item| item.get("output_tokens")).and_then(Value::as_i64),
                        cached_input_tokens: reported_usage.and_then(|item| item.pointer("/input_tokens_details/cached_tokens")).and_then(Value::as_i64),
                        reasoning_tokens: reported_usage.and_then(|item| item.pointer("/output_tokens_details/reasoning_tokens")).and_then(Value::as_i64),
                    });
                } else {
                    merge_usage(&mut usage, parse_usage(reported_usage));
                }
                if let Some(cost) = cost_micros(reported_usage.and_then(|item| item.get("cost"))) {
                    reported_cost_micros = Some(cost);
                }
                if let Some(model) = value.get("model").and_then(Value::as_str).and_then(valid_reported_model_id) {
                    if request.provider_id.starts_with("RELAYCORE_") {
                        merge_relay_model_identity(&mut actual_model_id, Some(model));
                    } else { actual_model_id = Some(model.to_owned()); }
                }
                publish_stream_measurement(&mut last_measurement, &mut on_measurement, &usage, reported_cost_micros, &actual_model_id, started.elapsed())?;
                let measurement = ReportedMeasurement {
                    usage, reported_cost_micros, actual_model_id,
                    elapsed_ms: started.elapsed().as_millis().min(i64::MAX as u128) as i64,
                };
                let code = terminal_failure_code.unwrap_or("PROVIDER_RESPONSE_FAILED");
                return Err(if measurement.has_reported_facts() {
                    Failure::MeasuredExplicit { code, http_status: Some(status), measurement }
                } else {
                    Failure::Explicit { code, http_status: Some(status) }
                });
            }
            if has_provider_refusal(&value, responses_stream) {
                terminal_failure_code.get_or_insert("PROVIDER_REFUSAL");
            }
            if !search_evidence && has_web_search_evidence(&value) {
                progress.advance(started.elapsed());
                search_evidence = true;
            }
            for source in response_sources(&value) {
                if sources.len() < 10 && !sources.iter().any(|item| item.1 == source.1) {
                    sources.push(source);
                }
            }
            if responses_stream {
                if let Some(response) = value.get("response") {
                    let u = response.get("usage");
                    merge_usage(&mut usage, Usage {
                        input_tokens:u.and_then(|v|v.get("input_tokens")).and_then(Value::as_i64),
                        output_tokens:u.and_then(|v|v.get("output_tokens")).and_then(Value::as_i64),
                        cached_input_tokens:u.and_then(|v|v.pointer("/input_tokens_details/cached_tokens")).and_then(Value::as_i64),
                        reasoning_tokens:u.and_then(|v|v.pointer("/output_tokens_details/reasoning_tokens")).and_then(Value::as_i64),
                    });
                    if let Some(cost)=cost_micros(u.and_then(|v|v.get("cost"))) { reported_cost_micros=Some(cost); }
                    if let Some(model)=response.get("model").and_then(Value::as_str).and_then(valid_reported_model_id) {
                        if request.provider_id.starts_with("RELAYCORE_") { merge_relay_model_identity(&mut actual_model_id,Some(model)); }
                        else { actual_model_id=Some(model.to_owned()); }
                    }
                }
                publish_stream_measurement(&mut last_measurement, &mut on_measurement, &usage, reported_cost_micros, &actual_model_id, started.elapsed())?;
                let event_type = value.get("type").and_then(Value::as_str);
                match event_type {
                    Some("response.output_text.delta") if terminal_failure_code.is_none() => {
                        if let Some(delta) = value
                            .get("delta")
                            .and_then(Value::as_str)
                            .filter(|delta| !delta.is_empty())
                        {
                            progress.advance(started.elapsed());
                            text.push_str(delta);
                            on_delta(delta).map_err(|failure| match failure {
                                Failure::Cancelled => cancelled_with_progress(&usage, reported_cost_micros, &actual_model_id, started.elapsed()), other => other,
                            })?;
                        }
                    }
                    Some("response.reasoning_text.delta") if terminal_failure_code.is_none() => {
                        if let Some(delta) = value
                            .get("delta")
                            .and_then(Value::as_str)
                            .filter(|delta| !delta.is_empty())
                        {
                            progress.advance(started.elapsed());
                            reasoning.push_str(delta);
                        }
                    }
                    Some("response.completed" | "response.incomplete" | "response.failed") => {
                        {
                            let response = value.get("response").unwrap_or(&value);
                            search_evidence |= has_web_search_evidence(response);
                            if event_type == Some("response.completed") {
                                native_search_completed = completed_web_search_call(response);
                            }
                            let response_usage = response.get("usage");
                            merge_usage(
                                &mut usage,
                                Usage {
                                    input_tokens: response_usage
                                        .and_then(|usage| usage.get("input_tokens"))
                                        .and_then(Value::as_i64),
                                    output_tokens: response_usage
                                        .and_then(|usage| usage.get("output_tokens"))
                                        .and_then(Value::as_i64),
                                    cached_input_tokens: response_usage
                                        .and_then(|usage| usage.get("input_tokens_details"))
                                        .and_then(|details| details.get("cached_tokens"))
                                        .and_then(Value::as_i64),
                                    reasoning_tokens: response_usage
                                        .and_then(|usage| usage.get("output_tokens_details"))
                                        .and_then(|details| details.get("reasoning_tokens"))
                                        .and_then(Value::as_i64),
                                },
                            );
                            if let Some(cost) =
                                cost_micros(response_usage.and_then(|usage| usage.get("cost")))
                            {
                                reported_cost_micros = Some(cost);
                            }
                            if let Some(model) = response.get("model").and_then(Value::as_str).and_then(valid_reported_model_id) {
                                if request.provider_id.starts_with("RELAYCORE_") {
                        merge_relay_model_identity(&mut actual_model_id, Some(model));
                    } else { actual_model_id = Some(model.to_owned()); }
                            }
                            for source in response_sources(response) {
                                if sources.len() < 10
                                    && !sources.iter().any(|item| item.1 == source.1)
                                {
                                    sources.push(source);
                                }
                            }
                        }
                        let terminal_response = value.get("response").unwrap_or(&value);
                        let explicitly_noncompleted = terminal_response.get("status")
                            .is_some_and(|status| status.as_str() != Some("completed"));
                        if event_type != Some("response.completed") || explicitly_noncompleted {
                            let code = terminal_failure_code.unwrap_or(if event_type == Some("response.failed")
                                || terminal_response["status"] == "failed" {
                                "PROVIDER_RESPONSE_FAILED"
                            } else if terminal_response.pointer("/incomplete_details/reason")
                                .and_then(Value::as_str) == Some("max_output_tokens") {
                                "OUTPUT_LIMIT"
                            } else {
                                "INCOMPLETE_RESPONSE"
                            });
                            let measurement = ReportedMeasurement {
                                usage: usage.clone(), reported_cost_micros,
                                actual_model_id: actual_model_id.clone(),
                                elapsed_ms: started.elapsed().as_millis().min(i64::MAX as u128) as i64,
                            };
                            return Err(if measurement.has_reported_facts() {
                                Failure::MeasuredExplicit { code, http_status: Some(status), measurement }
                            } else {
                                Failure::Explicit { code, http_status: Some(status) }
                            });
                        }
                        completed = true;
                    }
                    _ => {}
                }
                if completed {
                    break;
                }
                continue;
            }
            merge_usage(&mut usage, parse_usage(value.get("usage")));
            if let Some(cost) = cost_micros(value.get("usage").and_then(|item| item.get("cost"))) {
                reported_cost_micros = Some(cost);
            }
            if let Some(model) = value.get("model").and_then(Value::as_str).and_then(valid_reported_model_id) {
                if request.provider_id.starts_with("RELAYCORE_") {
                        merge_relay_model_identity(&mut actual_model_id, Some(model));
                    } else { actual_model_id = Some(model.to_owned()); }
            }
            publish_stream_measurement(&mut last_measurement, &mut on_measurement, &usage, reported_cost_micros, &actual_model_id, started.elapsed())?;
            if terminal_failure_code.is_none() {
                if let Some(delta) = text_delta(&value).filter(|delta| !delta.is_empty()) {
                    progress.advance(started.elapsed());
                    text.push_str(delta);
                    on_delta(delta).map_err(|failure| match failure {
                        Failure::Cancelled => cancelled_with_progress(&usage, reported_cost_micros, &actual_model_id, started.elapsed()), other => other,
                    })?;
                }
                if let Some(delta) = reasoning_delta(&value).filter(|delta| !delta.is_empty()) {
                    progress.advance(started.elapsed());
                    reasoning.push_str(delta);
                }
                // OpenRouter can send only structured or opaque reasoning, without plaintext.
                // It is progress, but must never be copied into visible text or diagnostics.
                if has_structured_reasoning_progress(&value) {
                    progress.advance(started.elapsed());
                }
            }
            if let Some(reason) = value
                .pointer("/choices/0/finish_reason")
                .and_then(Value::as_str)
            {
                if matches!(reason, "length" | "max_tokens" | "content_filter") {
                    terminal_failure_code = Some(if reason == "content_filter" {
                            "CONTENT_FILTER"
                        } else {
                            "OUTPUT_LIMIT"
                    });
                }
            }
            if has_explicit_choice_finish_reason(&value) {
                completed = true;
            }
        }
        if reached_eof || stream_ended || (responses_stream && completed) {
            break;
        }
    }
    if cancelled.load(Ordering::SeqCst) {
        return Err(cancelled_with_progress(&usage, reported_cost_micros, &actual_model_id, started.elapsed()));
    }
    if !json_fragment.is_empty() {
        return Err(format_failure_with_progress(status,&usage,reported_cost_micros,&actual_model_id,started.elapsed()));
    }
    if let Some(code) = terminal_failure_code {
        let measurement = ReportedMeasurement {
            usage, reported_cost_micros, actual_model_id,
            elapsed_ms: started.elapsed().as_millis().min(i64::MAX as u128) as i64,
        };
        return Err(if measurement.has_reported_facts() {
            Failure::MeasuredExplicit { code, http_status: Some(status), measurement }
        } else {
            Failure::Explicit { code, http_status: Some(status) }
        });
    }
    // Keep every Chat Completions provider aligned with Android: a clean EOF after
    // visible answer text is terminal even if that provider omitted `[DONE]` and
    // `finish_reason`. Responses API routes retain their explicit terminal-event rule.
    if !completed && !responses_stream && !messages_stream && !text.trim().is_empty() {
        completed = true;
    }
    if !completed {
        return Err(unknown_with_progress("MISSING_COMPLETION",&usage,reported_cost_micros,&actual_model_id,started.elapsed()));
    }
    let mut text = text.trim().to_owned();
    if request.web_search_route != "NONE" && !request.grounded_answer
        && (sources.is_empty() || request.web_search_route == "RELAYCORE_GPT_RESPONSES" && !native_search_completed) {
        let measurement = ReportedMeasurement {
            usage: usage.clone(),
            reported_cost_micros,
            actual_model_id: actual_model_id.clone(),
            elapsed_ms: started.elapsed().as_millis().min(i64::MAX as u128) as i64,
        };
        return Err(if measurement.has_reported_facts() {
            Failure::MeasuredExplicit { code: "WEB_SEARCH_NO_SOURCES", http_status: Some(status), measurement }
        } else {
            Failure::Explicit { code: "WEB_SEARCH_NO_SOURCES", http_status: Some(status) }
        });
    }
    if text.is_empty() {
        let measurement = ReportedMeasurement {
            usage, reported_cost_micros, actual_model_id,
            elapsed_ms: started.elapsed().as_millis().min(i64::MAX as u128) as i64,
        };
        return Err(if measurement.has_reported_facts() {
            Failure::MeasuredExplicit { code: "RESPONSE_FORMAT", http_status: Some(status), measurement }
        } else {
            Failure::Explicit { code: "RESPONSE_FORMAT", http_status: Some(status) }
        });
    }
    append_sources(&mut text, &sources);
    Ok(Completed {
        text,
        reasoning: (!reasoning.trim().is_empty()).then(|| reasoning.trim().to_owned()),
        usage,
        reported_cost_micros,
        actual_model_id,
        web_search_verified: !sources.is_empty(),
        elapsed_ms: started.elapsed().as_millis().min(i64::MAX as u128) as i64,

        native_sources: sources,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn messages_usage_json_failures_keep_the_same_total_as_success() {
        for (kind,reason,content,code) in [
            ("refusal","refusal",json!([{"type":"text","text":"partial"}]),"PROVIDER_REFUSAL"),
            ("limit","max_tokens",json!([{"type":"text","text":"partial"}]),"OUTPUT_LIMIT"),
            ("empty","end_turn",json!([{"type":"text","text":""}]),"RESPONSE_FORMAT"),
            ("tool","end_turn",json!([{"type":"tool_use","id":"synthetic","name":"unapproved"}]),"TOOL_CALL_UNSUPPORTED"),
            ("missing","end_turn",Value::Null,"RESPONSE_FORMAT"),
        ] {
            let body=json!({"model":"synthetic-messages","stop_reason":reason,"content":content,
                "usage":{"input_tokens":17,"cache_read_input_tokens":5,"cache_creation_input_tokens":3,"output_tokens":7}});
            let mut req=request("http://127.0.0.1:1".into());req.provider_id="RELAYCORE_CLAUDE".into();req.grounded_answer=true;
            let failure=decode_non_streaming_request(&req,&serde_json::to_vec(&body).unwrap(),1).unwrap_err();
            match failure {Failure::MeasuredExplicit{code:actual,measurement,..}=>{
                assert_eq!(actual,code,"{kind}");assert_eq!(measurement.usage.input_tokens,Some(25),"{kind}");
                assert_eq!(measurement.usage.cached_input_tokens,Some(5),"{kind}");assert_eq!(measurement.usage.output_tokens,Some(7),"{kind}");
                assert!(measurement.reported_cost_micros.is_none(),"RelayCore money policy unchanged");
            },other=>panic!("{kind} lost received measurement: {other:?}")}
        }
    }

    #[test]
    fn messages_usage_json_web_and_plain_success_share_cache_total() {
        for web in [false,true] {
            let content=if web {json!([{"type":"web_search_tool_result","content":[{"type":"web_search_result","title":"synthetic public source","url":"https://example.com/material"}]},{"type":"text","text":"synthetic answer"}])}else{json!([{"type":"text","text":"synthetic answer"}])};
            let body=json!({"model":"synthetic-messages","stop_reason":"end_turn","content":content,
                "usage":{"input_tokens":17,"cache_read_input_tokens":5,"cache_creation_input_tokens":3,"output_tokens":7}});
            let mut req=request("http://127.0.0.1:1".into());req.provider_id="DEEPSEEK".into();req.web_search_route="DEEPSEEK_MESSAGES".into();req.grounded_answer=!web;
            let reply=decode_non_streaming_request(&req,&serde_json::to_vec(&body).unwrap(),1).unwrap();
            assert_eq!(reply.usage.input_tokens,Some(25),"web={web}");assert_eq!(reply.usage.cached_input_tokens,Some(5));
            assert_eq!(reply.usage.output_tokens,Some(7));assert_eq!(reply.web_search_verified,web);
        }
    }

    #[tokio::test]
    async fn messages_usage_stream_cumulative_sparse_frames_keep_terminal_measurement() {
        for mode in ["success","error","eof","cancel"] {
            let mut events=vec![
                json!({"type":"message_start","message":{"model":"claude-opus-5.5","usage":{"input_tokens":17,"cache_read_input_tokens":5,"cache_creation_input_tokens":3,"output_tokens":0}}}),
                json!({"type":"content_block_delta","delta":{"type":"text_delta","text":"synthetic partial"}}),
                json!({"type":"message_delta","delta":{},"usage":{"cache_read_input_tokens":7,"output_tokens":4}}),
                json!({"type":"message_delta","delta":{},"usage":{"input_tokens":19,"output_tokens":9}}),
                json!({"type":"message_delta","delta":{},"usage":{"input_tokens":19,"output_tokens":9}}),
                json!({"type":"message_delta","delta":{},"usage":{"output_tokens":11}}),
            ];
            if mode=="success" {events.push(json!({"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":11}}));events.push(json!({"type":"message_stop"}));}
            if mode=="error" {events.push(json!({"type":"error","error":{"type":"overloaded_error"}}));events.push(json!({"type":"message_stop"}));}
            let stream=events.into_iter().map(|v|format!("data: {v}\n\n")).collect::<String>();
            let response=format!("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\n{stream}");
            let mut req=request(mock_server(Box::leak(response.into_bytes().into_boxed_slice())));req.provider_id="RELAYCORE_CLAUDE".into();req.model_id="claude-opus-5.5".into();
            let mut measurements=Vec::new();let mut visible=String::new();
            let result=execute_streaming_observed(req,Zeroizing::new(b"synthetic".to_vec()),Arc::new(AtomicBool::new(false)),|delta|{visible.push_str(delta);Ok(())},|m|{
                measurements.push(m.clone());if mode=="cancel"&&m.usage.output_tokens==Some(9){Err(Failure::Cancelled)}else{Ok(())}
            }).await;
            let usage=match (mode,result) {
                ("success",Ok(reply))=>reply.usage,
                ("error",Err(Failure::MeasuredExplicit{code:"PROVIDER_RESPONSE_FAILED",measurement,..}))=>measurement.usage,
                ("eof",Err(Failure::MeasuredUnknown{code:"MISSING_COMPLETION",measurement}))=>measurement.usage,
                ("cancel",Err(Failure::MeasuredCancelled{measurement}))=>measurement.usage,
                (_,other)=>panic!("{mode}: {other:?}"),
            };
            assert_eq!(visible,"synthetic partial");assert_eq!(usage.input_tokens,Some(29),"{mode}");assert_eq!(usage.cached_input_tokens,Some(7),"{mode}");
            assert_eq!(usage.output_tokens,Some(if mode=="cancel"{9}else{11}),"{mode}");
            assert!(measurements.iter().any(|m|m.usage.input_tokens==Some(27)&&m.usage.cached_input_tokens==Some(7)));
        }
    }

    #[tokio::test]
    async fn messages_usage_stream_explicit_zero_replaces_while_null_keeps_prior_fields() {
        let events=[
            json!({"type":"message_start","message":{"model":"claude-opus-5.5","usage":{"input_tokens":17,"cache_read_input_tokens":5,"cache_creation_input_tokens":3,"output_tokens":0}}}),
            json!({"type":"content_block_delta","delta":{"type":"text_delta","text":"synthetic answer"}}),
            json!({"type":"message_delta","delta":{},"usage":{"input_tokens":0,"cache_read_input_tokens":0,"output_tokens":0}}),
            json!({"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"input_tokens":null,"cache_read_input_tokens":null,"output_tokens":4}}),
            json!({"type":"message_stop"}),
        ];
        let stream=events.into_iter().map(|v|format!("data: {v}\n\n")).collect::<String>();
        let response=format!("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\n{stream}");
        let mut req=request(mock_server(Box::leak(response.into_bytes().into_boxed_slice())));req.provider_id="RELAYCORE_CLAUDE".into();req.model_id="claude-opus-5.5".into();
        let reply=execute_streaming(req,Zeroizing::new(b"synthetic".to_vec()),Arc::new(AtomicBool::new(false)),|_|Ok(())).await.unwrap();
        assert_eq!(reply.usage.input_tokens,Some(3));assert_eq!(reply.usage.cached_input_tokens,Some(0));assert_eq!(reply.usage.output_tokens,Some(4));
    }
    #[test]
    fn json_messages_refusal_is_explicit_and_keeps_reported_measurements() {
        let replies=[
            ("CHAT",json!({"model":"fixture-actual","choices":[{"message":{"refusal":"SYNTHETIC_REFUSAL","content":"must not promote"},"finish_reason":"stop"}],"usage":{"prompt_tokens":17,"completion_tokens":5,"cost":0.000005}})),
            ("RESPONSES",json!({"status":"completed","model":"fixture-actual","output":[{"type":"message","content":[{"type":"refusal","refusal":"SYNTHETIC_REFUSAL"}]}],"usage":{"input_tokens":17,"output_tokens":5,"cost":0.000005}})),
            ("MESSAGES",json!({"model":"fixture-actual","stop_reason":"refusal","content":[{"type":"text","text":"must not promote"}],"usage":{"input_tokens":17,"output_tokens":5,"cost":0.000005}})),
        ];
        for (kind,body) in replies {
            let mut req=request("http://127.0.0.1:1".into());
            if kind=="RESPONSES" {req.provider_id="QWEN".into();req.web_search_route="QWEN_RESPONSES".into();}
            if kind=="MESSAGES" {req.provider_id="DEEPSEEK".into();req.web_search_route="DEEPSEEK_MESSAGES".into();}
            let bytes=serde_json::to_vec(&body).unwrap();
            let failure=decode_non_streaming_request(&req,&bytes,12).unwrap_err();
            if kind=="RESPONSES" {assert!(matches!(decode_responses_non_streaming(&bytes,12),Err(Failure::MeasuredExplicit{code:"PROVIDER_REFUSAL",..})));}
            match failure {Failure::MeasuredExplicit{code,measurement,..}=>{assert_eq!(code,"PROVIDER_REFUSAL","{kind}");assert_eq!(measurement.usage.input_tokens,Some(17));assert_eq!(measurement.usage.output_tokens,Some(5));assert_eq!(measurement.reported_cost_micros,Some(5));assert_eq!(measurement.actual_model_id.as_deref(),Some("fixture-actual"));},other=>panic!("{kind}: {other:?}")}
        }
    }
    #[test]
    fn json_messages_refusal_does_not_infer_from_answer_words_or_null_fields() {
        let replies=[
            ("CHAT",json!({"choices":[{"message":{"refusal":null,"content":"documentation mentions refusal"},"finish_reason":"stop"}]})),
            ("RESPONSES",json!({"status":"completed","output":[{"type":"message","content":[{"type":"refusal","refusal":" "},{"type":"output_text","text":"documentation mentions refusal"}]}]})),
            ("MESSAGES",json!({"stop_reason":"end_turn","content":[{"type":"text","text":"documentation mentions refusal"}]})),
        ];
        for (kind,body) in replies {
            let mut req=request("http://127.0.0.1:1".into());req.grounded_answer=true;
            if kind=="RESPONSES" {req.provider_id="QWEN".into();req.web_search_route="QWEN_RESPONSES".into();}
            if kind=="MESSAGES" {req.provider_id="DEEPSEEK".into();req.web_search_route="DEEPSEEK_MESSAGES".into();}
            let reply=decode_non_streaming_request(&req,&serde_json::to_vec(&body).unwrap(),1).unwrap();assert_eq!(reply.text,"documentation mentions refusal");
        }
    }
    #[test]
    fn json_messages_refusal_preserves_other_explicit_finish_causes() {
        for (reason,code) in [("length","OUTPUT_LIMIT"),("max_tokens","OUTPUT_LIMIT"),("content_filter","CONTENT_FILTER")] {
            let bytes=serde_json::to_vec(&json!({"choices":[{"message":{"content":"partial"},"finish_reason":reason}],"usage":{"prompt_tokens":17,"completion_tokens":5,"cost":0.000005}})).unwrap();
            match decode_non_streaming(&bytes,1).unwrap_err(){Failure::MeasuredExplicit{code:actual,measurement,..}=>{assert_eq!(actual,code);assert_eq!(measurement.usage.input_tokens,Some(17));assert_eq!(measurement.reported_cost_micros,Some(5));},other=>panic!("{other:?}")}
        }
    }
    #[tokio::test]
    async fn json_messages_refusal_stream_keeps_terminal_usage() {
        let stream=[json!({"type":"message_start","message":{"model":"claude-opus-5.5","usage":{"input_tokens":17}}}),json!({"type":"message_delta","delta":{"stop_reason":"refusal"},"usage":{"output_tokens":5}}),json!({"type":"message_stop"})].into_iter().map(|v|format!("data: {v}\n\n")).collect::<String>();
        let response=format!("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\n{stream}");let mut req=request(mock_server(Box::leak(response.into_bytes().into_boxed_slice())));req.provider_id="RELAYCORE_CLAUDE".into();req.model_id="claude-opus-5.5".into();
        let failure=execute_streaming(req,Zeroizing::new(b"synthetic".to_vec()),Arc::new(AtomicBool::new(false)),|_|panic!("refusal must not be promoted")).await.unwrap_err();
        match failure {Failure::MeasuredExplicit{code,measurement,..}=>{assert_eq!(code,"PROVIDER_REFUSAL");assert_eq!(measurement.usage.input_tokens,Some(17));assert_eq!(measurement.usage.output_tokens,Some(5));},other=>panic!("{other:?}")}
    }

    #[tokio::test]
    async fn provider_refusal_streams_preserve_tail_measurement_without_promoting_content() {
        for kind in ["chat", "response-delta", "response-done", "response-final"] {
            let mut events = Vec::new();
            if kind == "chat" {
                events.push(json!({"choices":[{"delta":{"refusal":"SYNTHETIC_REFUSAL"},"finish_reason":"stop"}]}));
                events.push(json!({"model":"fixture-actual","usage":{"prompt_tokens":17,"completion_tokens":5,"cost":0.000005}}));
            } else {
                if kind == "response-delta" { events.push(json!({"type":"response.refusal.delta","delta":"SYNTHETIC_REFUSAL"})); }
                if kind == "response-done" { events.push(json!({"type":"response.refusal.done","refusal":"SYNTHETIC_REFUSAL"})); }
                events.push(json!({"type":"response.completed","response":{"status":"completed","model":"fixture-actual",
                    "output":if kind=="response-final" {json!([{"type":"message","content":[{"type":"refusal","refusal":"SYNTHETIC_REFUSAL"}]}])} else {json!([])},
                    "usage":{"input_tokens":17,"output_tokens":5,"cost":0.000005}}}));
            }
            let stream=events.into_iter().map(|v|format!("data: {v}\n\n")).collect::<String>()+"data: [DONE]\n\n";
            let response=format!("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\n{stream}");
            let mut req=request(mock_server(Box::leak(response.into_bytes().into_boxed_slice())));
            if kind!="chat" {req.provider_id="QWEN".into();req.model_id="qwen3.7-plus".into();req.web_search_route="QWEN_RESPONSES".into();}
            let mut visible=String::new();let failure=execute_streaming(req,Zeroizing::new(b"synthetic".to_vec()),Arc::new(AtomicBool::new(false)),|delta|{visible.push_str(delta);Ok(())}).await.unwrap_err();
            assert!(visible.is_empty(),"{kind}");
            match failure {
                Failure::MeasuredExplicit{code,measurement,..}=>{assert_eq!(code,"PROVIDER_REFUSAL","{kind}");assert_eq!(measurement.usage.input_tokens,Some(17));assert_eq!(measurement.usage.output_tokens,Some(5));assert_eq!(measurement.reported_cost_micros,Some(5));assert_eq!(measurement.actual_model_id.as_deref(),Some("fixture-actual"));},
                other=>panic!("expected measured explicit refusal for {kind}: {other:?}")
            }
        }
    }
    #[test]
    fn provider_refusal_requires_typed_nonempty_signal_not_answer_words() {
        assert!(!has_provider_refusal(&json!({"choices":[{"delta":{"content":"The documentation mentions refusal.","refusal":null}}]}),false));
        assert!(!has_provider_refusal(&json!({"type":"response.refusal.delta","delta":"  "}),true));
        assert!(!has_provider_refusal(&json!({"type":"response.output_text.delta","delta":"refusal"}),true));
        assert!(has_provider_refusal(&json!({"type":"response.content_part.done","part":{"type":"refusal","refusal":"synthetic"}}),true));
    }

    #[tokio::test]
    async fn cancelled_chat_delta_keeps_measurement_from_the_same_frame() {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let address = listener.local_addr().unwrap();
        let server = thread::spawn(move || {
            let (mut socket, _) = listener.accept().unwrap();
            let mut incoming = [0u8; 8192]; socket.read(&mut incoming).unwrap();
            let body = json!({"model":"fixture-actual", "usage":{"prompt_tokens":6,"prompt_tokens_details":{"cached_tokens":1},"cost":0.000004},
                "choices":[{"delta":{"content":"PRIVATE_PARTIAL_SENTINEL"}}]}).to_string();
            write!(socket, "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\ndata: {body}\n\n").unwrap();
        });
        let failure = execute_streaming(request(format!("http://{address}/chat/completions")), Zeroizing::new(b"test".to_vec()),
            Arc::new(AtomicBool::new(false)), |_| Err(Failure::Cancelled)).await.unwrap_err();
        server.join().unwrap();
        let measured = failure.quantitative_completed().expect("same-frame facts survive rejected delta");
        assert_eq!((measured.usage.input_tokens, measured.usage.cached_input_tokens, measured.usage.output_tokens), (Some(6),Some(1),None));
        assert_eq!(measured.reported_cost_micros, Some(4));
        assert_eq!(measured.actual_model_id.as_deref(), Some("fixture-actual"));
        assert!(measured.text.is_empty() && measured.reasoning.is_none() && measured.native_sources.is_empty());
        assert!(!format!("{failure:?}").contains("PRIVATE_PARTIAL_SENTINEL"));
    }

    #[tokio::test]
    async fn cancelled_stream_keeps_already_reported_usage_without_promoting_partial_text() {
        let listener=TcpListener::bind("127.0.0.1:0").unwrap();let address=listener.local_addr().unwrap();
        let cancelled=Arc::new(AtomicBool::new(false));let server_signal=cancelled.clone();
        let server=thread::spawn(move||{
            let (mut socket,_)=listener.accept().unwrap();let mut incoming=[0u8;8192];socket.read(&mut incoming).unwrap();
            socket.write_all(b"HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\ndata: {\"type\":\"message_start\",\"message\":{\"model\":\"fixture-actual\",\"usage\":{\"input_tokens\":5,\"cache_read_input_tokens\":1}}}\n\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"PRIVATE_PARTIAL_SENTINEL\"}}\n\n").unwrap();
            let deadline=std::time::Instant::now()+Duration::from_secs(2);
            while !server_signal.load(Ordering::SeqCst)&&std::time::Instant::now()<deadline {thread::sleep(Duration::from_millis(1));}
            assert!(server_signal.load(Ordering::SeqCst));
        });
        let mut req=request(format!("http://{address}/messages"));req.provider_id="RELAYCORE_CLAUDE".into();req.model_id="claude-opus-5.5".into();
        let mut visible=String::new();let callback_signal=cancelled.clone();
        let failure=execute_streaming(req,Zeroizing::new(b"test".to_vec()),cancelled,|delta|{visible.push_str(delta);callback_signal.store(true,Ordering::SeqCst);Ok(())}).await.unwrap_err();
        server.join().unwrap();assert_eq!(visible,"PRIVATE_PARTIAL_SENTINEL");
        let measurement=failure.quantitative_completed().expect("already reported facts survive user cancellation");
        assert_eq!(measurement.usage.input_tokens,Some(6));assert_eq!(measurement.usage.cached_input_tokens,Some(1));
        assert_eq!(measurement.usage.output_tokens,None);assert_eq!(measurement.reported_cost_micros,None);
        assert_eq!(measurement.actual_model_id.as_deref(),Some("fixture-actual"));
        assert!(measurement.text.is_empty()&&measurement.reasoning.is_none()&&measurement.native_sources.is_empty());
        assert!(!format!("{failure:?}").contains("PRIVATE_PARTIAL_SENTINEL"));
    }

    #[tokio::test]
    async fn relay_stream_citations_require_completed_native_search_and_keep_measurement() {
        for (provider, route, call_status, grounded, accepted) in [
            ("RELAYCORE_GPT", "RELAYCORE_GPT_RESPONSES", Some("in_progress"), false, false),
            ("RELAYCORE_GPT", "RELAYCORE_GPT_RESPONSES", Some("failed"), false, false),
            ("RELAYCORE_GPT", "RELAYCORE_GPT_RESPONSES", None, false, false),
            ("RELAYCORE_GPT", "RELAYCORE_GPT_RESPONSES", Some("completed"), false, true),
            ("RELAYCORE_GPT", "RELAYCORE_GPT_RESPONSES", None, true, true),
            ("QWEN", "QWEN_RESPONSES", None, false, true),
        ] {
            let listener = TcpListener::bind("127.0.0.1:0").unwrap();
            let address = listener.local_addr().unwrap();
            let mut output = vec![json!({"type":"message","role":"assistant","content":[{
                "type":"output_text","text":"provider answer","annotations":[{
                    "type":"url_citation","url":"https://example.test/proof","title":"Structured source"
                }]
            }]})];
            if let Some(status) = call_status { output.insert(0,json!({"type":"web_search_call","status":status})); }
            let terminal = json!({"type":"response.completed","response":{
                "status":"completed","model":"fixture-actual","output":output,
                "usage":{"input_tokens":100,"output_tokens":20,"input_tokens_details":{"cached_tokens":10}}
            }});
            let body = format!("data: {{\"type\":\"response.output_item.done\",\"item\":{{\"type\":\"web_search_call\",\"status\":\"completed\"}}}}\n\ndata: {{\"type\":\"response.output_text.delta\",\"delta\":\"provider answer\"}}\n\ndata: {terminal}\n\ndata: [DONE]\n\n");
            let server = thread::spawn(move || {
                let (mut socket, _) = listener.accept().unwrap();
                let mut incoming = [0u8;8192];socket.read(&mut incoming).unwrap();
                write!(socket,"HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",body.len(),body).unwrap();
            });
            let mut req = request(format!("http://{address}/responses"));
            req.provider_id=provider.into();req.web_search_route=route.into();req.grounded_answer=grounded;
            let outcome=execute_streaming(req,Zeroizing::new(b"test".to_vec()),Arc::new(AtomicBool::new(false)),|_|Ok(())).await;
            server.join().unwrap();
            if accepted {
                let reply=outcome.unwrap();assert!(reply.text.starts_with("provider answer"));
                assert_eq!(reply.native_sources,vec![("Structured source".into(),"https://example.test/proof".into())]);
                assert_eq!(reply.usage.cached_input_tokens,Some(10));
            } else {
                let failure=outcome.unwrap_err();
                assert!(matches!(&failure,Failure::MeasuredExplicit{code:"WEB_SEARCH_NO_SOURCES",..}));
                let measured=failure.quantitative_completed().unwrap();
                assert_eq!((measured.usage.input_tokens,measured.usage.output_tokens,measured.usage.cached_input_tokens),(Some(100),Some(20),Some(10)));
                assert_eq!(measured.actual_model_id.as_deref(),Some("fixture-actual"));
                assert!(measured.text.is_empty()&&measured.native_sources.is_empty());
            }
        }
    }

    #[test]
    fn relay_responses_input_keeps_instructions_material_and_assistant_history() {
        let mut req = request("http://127.0.0.1/unused".into());
        req.provider_id = "RELAYCORE_GPT".into();
        req.model_id = "gpt-6.1-sol".into();
        req.web_search_route = "RELAYCORE_GPT_RESPONSES".into();
        req.messages = json!([
            {"role":"system","content":[{"type":"text","text":"policy one"},{"type":"text","text":"policy two"}]},
            {"role":"user","content":[{"type":"text","text":"original question"},{"type":"text","text":"full original attachment"}]},
            {"role":"assistant","content":[{"type":"text","text":"checkpoint one"},{"type":"text","text":"checkpoint two"}]},
            {"role":"user","content":[{"type":"text","text":"before image"},{"type":"image_url","image_url":{"url":"data:image/png;base64,cHVibGlj"}},{"type":"text","text":"after image"}]}
        ]);
        let original = req.messages.clone();
        let body = transport_body(&req);
        assert_eq!(body["input"][0],json!({"role":"developer","content":[{"type":"input_text","text":"policy one\n\npolicy two"}]}));
        assert_eq!(body["input"][1]["content"],json!([{"type":"input_text","text":"original question\n\nfull original attachment"}]));
        assert_eq!(body["input"][2]["role"],"assistant");
        assert_eq!(body["input"][2]["content"],json!([{"type":"output_text","text":"checkpoint one\n\ncheckpoint two"}]));
        assert_eq!(body["input"][3]["content"],json!([
            {"type":"input_text","text":"before image"},
            {"type":"input_image","image_url":"data:image/png;base64,cHVibGlj"},
            {"type":"input_text","text":"after image"}
        ]));
        assert_eq!(req.messages,original);
        assert_eq!(body["tools"][0]["type"],"web_search");
        assert_eq!(body["tool_choice"],"required");
        req.web_search_route = "NONE".into();
        assert_eq!(transport_body(&req)["messages"],original);
        req.provider_id = "QWEN".into();
        req.web_search_route = "QWEN_RESPONSES".into();
        assert_eq!(transport_body(&req)["input"][0]["role"],"system");
    }

    #[test]
    fn chat_cache_usage_aliases_survive_success_and_measured_failure() {
        for usage in [
            json!({"prompt_tokens_details":{"cached_tokens":7}}),
            json!({"input_tokens_details":{"cached_tokens":7}}),
            json!({"prompt_cache_hit_tokens":7}),
            json!({"cache_read_input_tokens":7}),
            json!({"cached_input_tokens":7}),
        ] {
            let reply = json!({"model":"fixture-actual","usage":usage,
                "choices":[{"message":{"content":"answer"},"finish_reason":"stop"}]});
            let decoded = decode_non_streaming(&serde_json::to_vec(&reply).unwrap(), 2).unwrap();
            assert_eq!(decoded.usage.cached_input_tokens, Some(7), "{usage}");
            assert_eq!(decoded.usage.input_tokens, None);
            assert_eq!(decoded.text, "answer");
            let mut failed = reply;
            failed["choices"][0]["finish_reason"] = json!("length");
            let failure = decode_non_streaming(&serde_json::to_vec(&failed).unwrap(), 2).unwrap_err();
            let measured = failure.quantitative_completed().expect("cache facts survive failure");
            assert_eq!(measured.usage.cached_input_tokens, Some(7));
            assert!(measured.text.is_empty());
            assert!(measured.native_sources.is_empty());
        }
        let precedence = parse_usage(Some(&json!({"prompt_tokens_details":{"cached_tokens":0},
            "input_tokens_details":{"cached_tokens":7},"prompt_cache_hit_tokens":9})));
        assert_eq!(precedence.cached_input_tokens, Some(0));
        assert_eq!(parse_usage(Some(&json!({}))).cached_input_tokens, None);
    }

    #[tokio::test]
    async fn chat_cache_usage_trailing_stream_keeps_deepseek_reported_facts() {
        let endpoint = mock_server(b"HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\ndata: {\"choices\":[{\"delta\":{\"content\":\"42\"},\"finish_reason\":\"stop\"}]}\n\ndata: {\"choices\":[],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":2,\"prompt_cache_hit_tokens\":7}}\n\ndata: [DONE]\n\n");
        let mut req = request(endpoint);
        req.provider_id = "DEEPSEEK".into();
        let reply = execute_streaming(req, Zeroizing::new(b"test".to_vec()),
            Arc::new(AtomicBool::new(false)), |_| Ok(())).await.unwrap();
        assert_eq!(reply.text, "42");
        assert_eq!(reply.usage.input_tokens, Some(10));
        assert_eq!(reply.usage.output_tokens, Some(2));
        assert_eq!(reply.usage.cached_input_tokens, Some(7));
    }

    #[test]
    fn argon_native_plugin_preserves_route_and_off_grounded_boundaries() {
        let mut request = request("http://127.0.0.1/unused".into());
        request.model_id = "google/gemini-4-argon".into();
        let plain = transport_body(&request);
        assert!(plain.get("plugins").is_none());
        request.web_search_route = "OPENROUTER_SERVER_TOOL".into();
        let web = transport_body(&request);
        assert_eq!(web["plugins"][0]["engine"], "native");
        assert!(web.get("tools").is_none());
        assert_eq!(web["model"], "google/gemini-4-argon");
        assert!(web["messages"][0]["content"].as_str().unwrap().contains("不搜索私人信息"));
        request.grounded_answer = true;
        let final_answer = transport_body(&request);
        assert!(final_answer.get("plugins").is_none());
        assert!(final_answer.get("tools").is_none());
        assert_eq!(final_answer["messages"], request.messages);
    }
    use std::{
        io::{Read, Write},
        net::TcpListener,
        thread,
    };

    fn mock_server(response: &'static [u8]) -> String {
        mock_server_with_stream(response, true)
    }

    fn mock_server_with_stream(response: &'static [u8], expects_stream: bool) -> String {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let address = listener.local_addr().unwrap();
        thread::spawn(move || {
            let (mut socket, _) = listener.accept().unwrap();
            let mut request = [0u8; 8192];
            let read = socket.read(&mut request).unwrap();
            let request = String::from_utf8_lossy(&request[..read]);
            assert!(request
                .to_ascii_lowercase()
                .contains("idempotency-key: attempt-fixture"));
            assert_eq!(request.contains("\"stream\":true"), expects_stream);
            socket.write_all(response).unwrap();
        });
        format!("http://{address}/chat/completions")
    }

    fn request(endpoint: String) -> TransportRequest {
        TransportRequest {
            endpoint,
            provider_id: "OPENROUTER".into(),
            model_id: "fixture-model".into(),
            messages: json!([{"role":"user","content":"hello"}]),
            idempotency_key: "attempt-fixture".into(),
            max_output_tokens: 128,
            web_search_route: "NONE".into(),
            structured_json: false,
            disable_thinking: false,

            grounded_answer: false,
            explicit_search_query: None,
        }
    }

    #[tokio::test]
    async fn windows_alignment_rc_messages_preserves_text_usage_and_terminal() {
        let wire = b"HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\ndata: {\"type\":\"message_start\",\"message\":{\"model\":\"claude-opus-5.5\",\"usage\":{\"input_tokens\":12,\"cache_read_input_tokens\":4}}}\n\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"answer\"}}\n\ndata: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":3}}\n\ndata: {\"type\":\"message_stop\"}";
        let mut request = request(mock_server(wire));
        request.provider_id = "RELAYCORE_CLAUDE".into();
        request.model_id = "claude-opus-5.5".into();
        let mut deltas = String::new();
        let reply = execute_streaming(request, Zeroizing::new(b"synthetic-only".to_vec()),
            Arc::new(AtomicBool::new(false)), |delta| { deltas.push_str(delta); Ok(()) }).await.unwrap();
        assert_eq!(reply.text, "answer"); assert_eq!(deltas, "answer");
        assert_eq!(reply.actual_model_id.as_deref(), Some("claude-opus-5.5"));
        // Messages input_tokens excludes cache reads; the ledger total includes them.
        assert_eq!(reply.usage.input_tokens, Some(16));
        assert_eq!(reply.usage.cached_input_tokens, Some(4));
        assert_eq!(reply.usage.output_tokens, Some(3));
    }


    fn multiline_wire(body: &str) -> String {
        let wire=format!("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",body.len(),body);
        mock_server(Box::leak(wire.into_bytes().into_boxed_slice()))
    }


    #[tokio::test]
    async fn stream_multiline_json_split_crlf_delivers_before_eof() {
        let listener=TcpListener::bind("127.0.0.1:0").unwrap();let endpoint=format!("http://{}/chat/completions",listener.local_addr().unwrap());
        let (sent,received)=std::sync::mpsc::channel();
        let server=thread::spawn(move||{
            let(mut socket,_)=listener.accept().unwrap();socket.set_nodelay(true).unwrap();let mut request=[0u8;8192];socket.read(&mut request).unwrap();
            socket.write_all(b"HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\n").unwrap();
            for bytes in [&[0xef][..],&[0xbb,0xbf],b"data: {\r",b"\ndata: \"choices\":[{\"delta\":{\"content\":\"chunked\"}}]}\r"] {socket.write_all(bytes).unwrap();thread::sleep(Duration::from_millis(5));}
            let delivered=received.recv_timeout(Duration::from_secs(2)).is_ok();
            socket.write_all(b"\n\r\ndata: {\r\ndata: \"choices\":[{\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":17,\"completion_tokens\":5}}\r\n\r\ndata: [DONE]\r\n\r\n").unwrap();delivered
        });
        let mut signal=Some(sent);let mut text=String::new();let reply=execute_streaming(request(endpoint),Zeroizing::new(b"synthetic-only".to_vec()),Arc::new(AtomicBool::new(false)),|d|{text.push_str(d);if let Some(s)=signal.take(){s.send(()).unwrap();}Ok(())}).await.unwrap();
        assert!(server.join().unwrap(),"multiline frame waited for EOF");assert_eq!(text,"chunked");assert_eq!(reply.text,text);assert_eq!(reply.usage.input_tokens,Some(17));assert_eq!(reply.usage.output_tokens,Some(5));
    }

    #[tokio::test]
    async fn stream_multiline_json_cr_empty_data_and_no_final_newline() {
        let body="data: {\rdata:\rdata: \"model\":\"final-model\",\rdata: \"choices\":[{\"delta\":{\"content\":\"final\"},\"finish_reason\":\"stop\"}],\rdata: \"usage\":{\"prompt_tokens\":17,\"completion_tokens\":5}}";
        let reply=execute_streaming(request(multiline_wire(body)),Zeroizing::new(b"synthetic-only".to_vec()),Arc::new(AtomicBool::new(false)),|_|Ok(())).await.unwrap();
        assert_eq!(reply.text,"final");assert_eq!(reply.usage.input_tokens,Some(17));assert_eq!(reply.usage.output_tokens,Some(5));assert_eq!(reply.actual_model_id.as_deref(),Some("final-model"));
    }

    #[tokio::test]
    async fn stream_multiline_json_error_retains_measured_failure_without_promoting_text() {
        let body=concat!("data: {\ndata: \"model\":\"error-model\",\ndata: \"choices\":[{\"delta\":{\"content\":\"partial\"},\"finish_reason\":\"stop\"}],\ndata: \"usage\":{\"prompt_tokens\":17,\"completion_tokens\":5,\"cost\":0.000009}}\n\n",
            "data: {\ndata: \"error\":{\"message\":\"synthetic-only\"}}\n\ndata: [DONE]\n\n");
        let failure=execute_streaming(request(multiline_wire(body)),Zeroizing::new(b"synthetic-only".to_vec()),Arc::new(AtomicBool::new(false)),|_|Ok(())).await.unwrap_err();
        let Failure::MeasuredExplicit{code,measurement,..}=failure else{panic!("lost multiline failure facts: {failure:?}")};
        assert_eq!(code,"PROVIDER_RESPONSE_FAILED");assert_eq!(measurement.usage.input_tokens,Some(17));assert_eq!(measurement.usage.output_tokens,Some(5));assert_eq!(measurement.reported_cost_micros,Some(9));assert_eq!(measurement.actual_model_id.as_deref(),Some("error-model"));
    }

    #[tokio::test]
    async fn stream_multiline_json_chat_keeps_answer_tail_usage_and_identity() {
        let body=concat!(
            "data: {\n: keepalive\nevent: message\ndata: \"model\":\"multiline-model\",\ndata: \"choices\":[{\"delta\":{\"content\":\"完整回答\"},\"finish_reason\":\"stop\"}]}\n\n",
            "data: {\r\ndata: \"usage\":{\"prompt_tokens\":17,\r\ndata: \"completion_tokens\":5,\"cost\":0.000009}}\r\n\r\n",
            "data: [DONE]\n\n");
        let mut seen=String::new();let reply=execute_streaming(request(multiline_wire(body)),Zeroizing::new(b"synthetic-only".to_vec()),Arc::new(AtomicBool::new(false)),|d|{seen.push_str(d);Ok(())}).await.unwrap();
        assert_eq!(seen,"完整回答");assert_eq!(reply.text,seen);assert_eq!(reply.actual_model_id.as_deref(),Some("multiline-model"));
        assert_eq!(reply.usage.input_tokens,Some(17));assert_eq!(reply.usage.output_tokens,Some(5));assert_eq!(reply.reported_cost_micros,Some(9));
    }

    #[tokio::test]
    async fn stream_multiline_json_messages_keeps_terminal_usage() {
        let body=concat!(
            "data: {\ndata: \"type\":\"message_start\",\ndata: \"message\":{\"model\":\"claude-opus-5.5\",\"usage\":{\"input_tokens\":17}}}\n\n",
            "data: {\ndata: \"type\":\"content_block_delta\",\ndata: \"delta\":{\"type\":\"text_delta\",\"text\":\"Messages回答\"}}\n\n",
            "data: {\ndata: \"type\":\"message_delta\",\ndata: \"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":5}}\n\n",
            "data: {\ndata: \"type\":\"message_stop\"}\n\n");
        let mut req=request(multiline_wire(body));req.provider_id="RELAYCORE_CLAUDE".into();
        let reply=execute_streaming(req,Zeroizing::new(b"synthetic-only".to_vec()),Arc::new(AtomicBool::new(false)),|_|Ok(())).await.unwrap();
        assert_eq!(reply.text,"Messages回答");assert_eq!(reply.usage.input_tokens,Some(17));assert_eq!(reply.usage.output_tokens,Some(5));assert_eq!(reply.actual_model_id.as_deref(),Some("claude-opus-5.5"));
    }

    #[tokio::test]
    async fn stream_multiline_json_responses_keeps_terminal_usage() {
        let body=concat!(
            "data: {\ndata: \"type\":\"response.output_text.delta\",\ndata: \"delta\":\"Responses回答\"}\n\n",
            "data: {\ndata: \"type\":\"response.completed\",\ndata: \"response\":{\"status\":\"completed\",\"model\":\"qwen3.7-max\",\"usage\":{\"input_tokens\":17,\"output_tokens\":5}}}\n\n");
        let mut req=request(multiline_wire(body));req.provider_id="QWEN".into();req.web_search_route="QWEN_RESPONSES".into();req.grounded_answer=true;
        let reply=execute_streaming(req,Zeroizing::new(b"synthetic-only".to_vec()),Arc::new(AtomicBool::new(false)),|_|Ok(())).await.unwrap();
        assert_eq!(reply.text,"Responses回答");assert_eq!(reply.usage.input_tokens,Some(17));assert_eq!(reply.usage.output_tokens,Some(5));assert_eq!(reply.actual_model_id.as_deref(),Some("qwen3.7-max"));
    }

    #[tokio::test]
    async fn stream_multiline_json_bad_tail_preserves_prior_measured_facts() {
        for tail in ["data: {\ndata: \"choices\":[", "data: {\n\ndata: \"choices\":[]}\n\n", "data: {\ndata: [DONE]\n\n", "data: {\ndata: BAD_JSON}\n\n"] {
            let body=format!("data: {{\"model\":\"prior-model\",\"choices\":[{{\"delta\":{{\"content\":\"partial\"}}}}],\"usage\":{{\"prompt_tokens\":17,\"completion_tokens\":5,\"cost\":0.000009}}}}\n\n{tail}");
            let failure=execute_streaming(request(multiline_wire(&body)),Zeroizing::new(b"synthetic-only".to_vec()),Arc::new(AtomicBool::new(false)),|_|Ok(())).await.unwrap_err();
            let Failure::MeasuredExplicit {code,measurement,..}=failure else{panic!("lost prior facts: {failure:?}")};
            assert_eq!(code,"RESPONSE_FORMAT");assert_eq!(measurement.usage.input_tokens,Some(17));assert_eq!(measurement.usage.output_tokens,Some(5));assert_eq!(measurement.reported_cost_micros,Some(9));assert_eq!(measurement.actual_model_id.as_deref(),Some("prior-model"));
        }
    }

    #[tokio::test]
    async fn windows_alignment_valid_final_sse_frame_without_newline_is_not_lost() {
        let wire = b"HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\ndata: {\"model\":\"gpt-6.1-sol\",\"choices\":[{\"delta\":{\"content\":\"answer\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":7,\"completion_tokens\":2}}";
        let reply = execute_streaming(request(mock_server(wire)), Zeroizing::new(b"synthetic-only".to_vec()),
            Arc::new(AtomicBool::new(false)), |_| Ok(())).await.unwrap();
        assert_eq!(reply.text, "answer"); assert_eq!(reply.usage.input_tokens, Some(7));
    }

    #[tokio::test]
    async fn stream_valid_cr_line_endings_preserve_every_answer_frame_and_usage() {
        let body = concat!(
            "data: {\"model\":\"reported-cr-model\",\"choices\":[{\"delta\":{\"content\":\"首段，\"}}],\"usage\":{\"prompt_tokens\":7}}\r\r",
            "data: {\"choices\":[{\"delta\":{\"content\":\"尾段\"},\"finish_reason\":\"stop\"}],\"usage\":{\"completion_tokens\":3,\"cost\":0.000004}}\r\r",
            "data: [DONE]\r\r"
        );
        let wire=format!("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",body.len(),body);
        let mut deltas=String::new();
        let reply=execute_streaming(request(mock_server(Box::leak(wire.into_bytes().into_boxed_slice()))),
            Zeroizing::new(b"synthetic-only".to_vec()),Arc::new(AtomicBool::new(false)),|delta|{deltas.push_str(delta);Ok(())}).await.unwrap();
        assert_eq!(reply.text,"首段，尾段");assert_eq!(deltas,"首段，尾段");
        assert_eq!(reply.usage.input_tokens,Some(7));assert_eq!(reply.usage.output_tokens,Some(3));
        assert_eq!(reply.reported_cost_micros,Some(4));assert_eq!(reply.actual_model_id.as_deref(),Some("reported-cr-model"));
    }

    #[tokio::test]
    async fn stream_valid_cr_line_endings_preserve_failed_tail_quantitative_facts() {
        let body=concat!(
            "data: {\"model\":\"reported-cr-model\",\"choices\":[{\"delta\":{\"content\":\"partial\"}}],\"usage\":{\"prompt_tokens\":7,\"completion_tokens\":3,\"cost\":0.000004}}\r\r",
            "data: {\"error\":{\"message\":\"synthetic failure\"}}\r\r"
        );
        let wire=format!("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",body.len(),body);
        let failure=execute_streaming(request(mock_server(Box::leak(wire.into_bytes().into_boxed_slice()))),
            Zeroizing::new(b"synthetic-only".to_vec()),Arc::new(AtomicBool::new(false)),|_|Ok(())).await.unwrap_err();
        let Failure::MeasuredExplicit {code,measurement,..}=failure else{panic!("CR failure lost prior facts: {failure:?}")};
        assert_eq!(code,"PROVIDER_RESPONSE_FAILED");assert_eq!(measurement.usage.input_tokens,Some(7));
        assert_eq!(measurement.usage.output_tokens,Some(3));assert_eq!(measurement.reported_cost_micros,Some(4));
        assert_eq!(measurement.actual_model_id.as_deref(),Some("reported-cr-model"));
    }

    #[tokio::test]
    async fn stream_valid_cr_line_endings_emit_before_eof_and_accept_split_crlf() {
        let listener=TcpListener::bind("127.0.0.1:0").unwrap();
        let endpoint=format!("http://{}/chat/completions",listener.local_addr().unwrap());
        let (sent,received)=std::sync::mpsc::channel();
        let server=thread::spawn(move||{
            let (mut socket,_)=listener.accept().unwrap();socket.set_nodelay(true).unwrap();
            let mut input=[0u8;8192];socket.read(&mut input).unwrap();
            socket.write_all(b"HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\n").unwrap();
            socket.write_all(&[0xef]).unwrap();
            socket.write_all(&[0xbb,0xbf]).unwrap();
            socket.write_all("data: {\"choices\":[{\"delta\":{\"content\":\"首段，\"}}]}\r".as_bytes()).unwrap();
            // Do not send the LF or close until the actual consumer sees the CR frame.
            let streamed=received.recv_timeout(Duration::from_secs(2)).is_ok();
            socket.write_all("\ndata: {\"choices\":[{\"delta\":{\"content\":\"尾段\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":7,\"completion_tokens\":3}}\n\ndata: [DONE]\r".as_bytes()).unwrap();
            streamed
        });
        let mut signal=Some(sent);let mut visible=String::new();
        let reply=execute_streaming(request(endpoint),Zeroizing::new(b"synthetic-only".to_vec()),
            Arc::new(AtomicBool::new(false)),|delta|{
                visible.push_str(delta);if let Some(sent)=signal.take(){sent.send(()).unwrap();}Ok(())
            }).await.unwrap();
        assert!(server.join().unwrap(),"CR line waited for LF/EOF rather than streaming");
        assert_eq!(visible,"首段，尾段");assert_eq!(reply.text,visible);
        assert_eq!(reply.usage.input_tokens,Some(7));assert_eq!(reply.usage.output_tokens,Some(3));
    }

    #[tokio::test]
    async fn stream_leading_bom_preserves_first_answer_usage_and_model() {
        let body = concat!(
            "\u{feff}data: {\"model\":\"reported-first-model\",\"choices\":[{\"delta\":{\"content\":\"首段，\"},\"finish_reason\":null}],\"usage\":{\"prompt_tokens\":7}}\r\n\r\n",
            "data: {\"choices\":[{\"delta\":{\"content\":\"尾段\"},\"finish_reason\":\"stop\"}],\"usage\":{\"completion_tokens\":3}}\r\n\r\n",
            "data: [DONE]\r\n\r\n"
        );
        let wire = format!("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}", body.len(), body);
        let mut deltas = String::new();
        let reply = execute_streaming(request(mock_server(Box::leak(wire.into_bytes().into_boxed_slice()))),
            Zeroizing::new(b"synthetic-only".to_vec()), Arc::new(AtomicBool::new(false)),
            |delta| { deltas.push_str(delta); Ok(()) }).await.unwrap();
        assert_eq!(reply.text, "首段，尾段");
        assert_eq!(deltas, "首段，尾段");
        assert_eq!(reply.usage.input_tokens, Some(7));
        assert_eq!(reply.usage.output_tokens, Some(3));
        assert_eq!(reply.actual_model_id.as_deref(), Some("reported-first-model"));
    }

    #[tokio::test]
    async fn malformed_stream_tail_keeps_previously_reported_quantitative_facts() {
        let body = concat!(
            "data: {\"model\":\"reported-first-model\",\"choices\":[{\"delta\":{\"content\":\"部分正文\"}}],\"usage\":{\"prompt_tokens\":7,\"completion_tokens\":3,\"prompt_tokens_details\":{\"cached_tokens\":2},\"cost\":0.000004}}\n\n",
            "data: {\"choices\":["
        );
        let wire=format!("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",body.len(),body);
        let failure=execute_streaming(request(mock_server(Box::leak(wire.into_bytes().into_boxed_slice()))),
            Zeroizing::new(b"synthetic-only".to_vec()),Arc::new(AtomicBool::new(false)),|_|Ok(())).await.unwrap_err();
        let Failure::MeasuredExplicit {code,http_status,measurement}=failure else {panic!("Previously reported usage lost: {failure:?}")};
        assert_eq!((code,http_status),("RESPONSE_FORMAT",Some(200)));
        assert_eq!(measurement.usage.input_tokens,Some(7));
        assert_eq!(measurement.usage.output_tokens,Some(3));
        assert_eq!(measurement.usage.cached_input_tokens,Some(2));
        assert_eq!(measurement.reported_cost_micros,Some(4));
        assert_eq!(measurement.actual_model_id.as_deref(),Some("reported-first-model"));
        let accounting=measurement.content_free_completed();
        assert!(accounting.text.is_empty() && accounting.reasoning.is_none() && accounting.native_sources.is_empty());
        assert!(!accounting.web_search_verified);
    }

    #[tokio::test]
    async fn unknown_messages_eof_keeps_received_measurement_without_promoting_answer() {
        let body=concat!(
            "data: {\"type\":\"message_start\",\"message\":{\"model\":\"reported-first-model\",\"usage\":{\"input_tokens\":7}}}\n\n",
            "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"未知结果前的部分正文\"}}\n\n",
            "data: {\"type\":\"message_delta\",\"usage\":{\"output_tokens\":3}}\n\n"
        );
        let wire=format!("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",body.len(),body);
        let mut req=request(mock_server(Box::leak(wire.into_bytes().into_boxed_slice())));req.provider_id="RELAYCORE_CLAUDE".into();
        let failure=execute_streaming(req,Zeroizing::new(b"synthetic-only".to_vec()),Arc::new(AtomicBool::new(false)),|_|Ok(())).await.unwrap_err();
        assert!(matches!(&failure,Failure::MeasuredUnknown {code:"MISSING_COMPLETION",..}));
        let facts=failure.quantitative_completed().expect("UNKNOWN discarded previously received measurement");
        assert_eq!(facts.usage.input_tokens,Some(7));assert_eq!(facts.usage.output_tokens,Some(3));
        assert_eq!(facts.actual_model_id.as_deref(),Some("reported-first-model"));
        assert!(facts.text.is_empty() && facts.reasoning.is_none() && facts.native_sources.is_empty());
        assert!(!facts.web_search_verified);
    }

    #[tokio::test]
    async fn invalid_utf8_tail_keeps_reported_zero_cost_without_inventing_unknown_tokens() {
        for (reported,measured) in [("{\"cost\":0}",true),("null",false)] {
            let first=format!("data: {{\"model\":\"reported-first-model\",\"choices\":[{{\"delta\":{{\"content\":\"部分正文\"}}}}],\"usage\":{reported}}}\n\n");
            let mut body=first.into_bytes();body.extend_from_slice(b"data: \xff\n\n");
            let mut wire=format!("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: {}\r\nConnection: close\r\n\r\n",body.len()).into_bytes();wire.extend(body);
            let failure=execute_streaming(request(mock_server(Box::leak(wire.into_boxed_slice()))),
                Zeroizing::new(b"synthetic-only".to_vec()),Arc::new(AtomicBool::new(false)),|_|Ok(())).await.unwrap_err();
            if measured {
                let Failure::MeasuredExplicit {code,http_status,measurement}=failure else {panic!("Reported zero cost lost: {failure:?}")};
                assert_eq!((code,http_status),("RESPONSE_FORMAT",Some(200)));
                assert_eq!(measurement.reported_cost_micros,Some(0));
                assert_eq!(measurement.actual_model_id.as_deref(),Some("reported-first-model"));
                assert!(measurement.usage.input_tokens.is_none() && measurement.usage.output_tokens.is_none()
                    && measurement.usage.cached_input_tokens.is_none() && measurement.usage.reasoning_tokens.is_none());
            } else {
                let Failure::MeasuredExplicit {code,http_status,measurement}=failure else {panic!("Reported identity lost: {failure:?}")};
                assert_eq!((code,http_status),("RESPONSE_FORMAT",Some(200)));
                assert_eq!(measurement.actual_model_id.as_deref(),Some("reported-first-model"));
                assert_eq!(measurement.reported_cost_micros,None);
                assert_eq!(measurement.usage,Usage::default());
            }
        }
    }

    #[tokio::test]
    async fn stream_leading_bom_messages_and_responses_keep_identity_and_content_bom() {
        let messages = concat!(
            "\u{feff}data: {\"type\":\"message_start\",\"message\":{\"model\":\"reported-first-model\",\"usage\":{\"input_tokens\":7}}}\n\n",
            "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"\u{feff}正文\"}}\n\n",
            "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":3}}\n\n",
            "data: {\"type\":\"message_stop\"}\n\n"
        );
        let responses = concat!(
            "\u{feff}data: {\"type\":\"response.output_text.delta\",\"delta\":\"\u{feff}正文\"}\n\n",
            "data: {\"type\":\"response.completed\",\"response\":{\"status\":\"completed\",\"model\":\"reported-first-model\",\"usage\":{\"input_tokens\":7,\"output_tokens\":3}}}\n\n"
        );
        for (provider, route, body) in [("RELAYCORE_CLAUDE", "NONE", messages), ("QWEN", "QWEN_RESPONSES", responses)] {
            let wire = format!("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}", body.len(), body);
            let mut req = request(mock_server(Box::leak(wire.into_bytes().into_boxed_slice())));
            req.provider_id = provider.into(); req.web_search_route = route.into(); req.grounded_answer = true;
            let reply = execute_streaming(req, Zeroizing::new(b"synthetic-only".to_vec()),
                Arc::new(AtomicBool::new(false)), |_| Ok(())).await.unwrap();
            assert_eq!(reply.text, "\u{feff}正文", "{provider}: only the stream BOM may be removed");
            assert_eq!(reply.usage.input_tokens, Some(7));
            assert_eq!(reply.usage.output_tokens, Some(3));
            assert_eq!(reply.actual_model_id.as_deref(), Some("reported-first-model"));
        }
    }

    #[tokio::test]
    async fn windows_alignment_messages_eof_without_stop_is_unknown() {
        let wire = b"HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"partial\"}}\n\n";
        let mut request = request(mock_server(wire)); request.provider_id = "RELAYCORE_CLAUDE".into();
        let failure = execute_streaming(request, Zeroizing::new(b"synthetic-only".to_vec()),
            Arc::new(AtomicBool::new(false)), |_| Ok(())).await.unwrap_err();
        assert_eq!(failure, Failure::Unknown { code: "MISSING_COMPLETION" });
    }


    #[test]
    fn windows_alignment_current_protocol_profiles_and_endpoints() {
        let mut req = request("https://example.test/v1/chat/completions".into());
        req.messages = json!([{"role":"system","content":"policy"},{"role":"assistant","content":"history"},
            {"role":"user","content":[{"type":"text","text":"question"},{"type":"image_url","image_url":{"url":"data:image/png;base64,cHVibGlj"}}]}]);
        req.provider_id = "RELAYCORE_CLAUDE".into(); req.model_id = "claude-opus-5.5".into();
        let body = transport_body(&req);
        assert_eq!(body["system"], "policy"); assert_eq!(body["messages"].as_array().unwrap().len(), 2);
        assert_eq!(body["messages"][1]["content"][1]["source"]["type"], "base64");
        assert_eq!(body["output_config"]["effort"], "medium"); assert!(body.get("temperature").is_none());
        assert_eq!(ordinary_provider_endpoint("https://example.test/v1", "RELAYCORE_CLAUDE", "NONE"), "https://example.test/v1/messages");
        req.model_id = "claude-sonnet-5-5".into(); assert_eq!(transport_body(&req)["output_config"]["effort"], "high");
        req.provider_id = "RELAYCORE_GPT".into(); req.model_id = "gpt-6.1-sol".into(); req.web_search_route = "RELAYCORE_GPT_RESPONSES".into();
        let body = transport_body(&req);
        assert_eq!(body["input"][1]["content"][0]["type"], "output_text");
        assert_eq!(body["tool_choice"], "required"); assert_eq!(body["tools"][0]["external_web_access"], true);
        assert_eq!(body["include"][0], "web_search_call.action.sources"); assert_eq!(body["reasoning"]["effort"], "low");
        assert_eq!(ordinary_provider_endpoint("https://example.test/v1", "RELAYCORE_GPT", &req.web_search_route), "https://example.test/v1/responses");
        req.messages = json!([{"role":"user","content":"深入分析这个问题"}]);
        assert_eq!(transport_body(&req)["reasoning"]["effort"], "medium");
        req.provider_id = "RELAYCORE_GEMINI".into(); req.model_id = "gemini-3.8-flash".into(); req.web_search_route = "NONE".into();
        assert!(transport_body(&req).get("temperature").is_none());
        req.provider_id = "OPENROUTER".into(); req.model_id = "meta/muse-wonder".into(); req.web_search_route = "OPENROUTER_SERVER_TOOL".into();
        assert_eq!(transport_endpoint(&req), "https://example.test/v1/responses"); assert!(transport_body(&req).get("input").is_some());
        req.model_id = "google/gemini-3.8-flash".into();
        let body = transport_body(&req); assert!(body.get("tools").is_none()); assert_eq!(body["plugins"][0]["engine"], "native");
        req.model_id = "openai/gpt-6.1-sol".into(); assert_eq!(transport_body(&req)["reasoning"]["effort"], "high");
        assert_eq!(ordinary_output_token_limit("ZHIPU", "glm-5.3"), 32768);
    }

    #[tokio::test]
    async fn windows_alignment_rc_responses_requires_completed_and_structured_sources() {
        let wire = b"HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\ndata: {\"type\":\"response.output_text.delta\",\"delta\":\"grounded\"}\n\ndata: {\"type\":\"response.completed\",\"response\":{\"model\":\"gpt-6.1-sol\",\"usage\":{\"input_tokens\":11,\"output_tokens\":4},\"output\":[{\"type\":\"web_search_call\",\"status\":\"completed\",\"action\":{\"sources\":[{\"url\":\"https://example.test/evidence\",\"title\":\"Public evidence\"}]}}]}}";
        let mut req = request(mock_server(wire)); req.provider_id = "RELAYCORE_GPT".into();
        req.model_id = "gpt-6.1-sol".into(); req.web_search_route = "RELAYCORE_GPT_RESPONSES".into();
        let reply = execute_streaming(req, Zeroizing::new(b"synthetic-only".to_vec()), Arc::new(AtomicBool::new(false)), |_| Ok(())).await.unwrap();
        assert!(reply.web_search_verified); assert!(reply.text.contains("https://example.test/evidence"));
        assert_eq!(reply.actual_model_id.as_deref(), Some("gpt-6.1-sol")); assert_eq!(reply.usage.input_tokens, Some(11));
    }

    #[tokio::test]
    async fn windows_alignment_messages_output_limit_keeps_only_measured_failure_facts() {
        let wire = b"HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\ndata: {\"type\":\"message_start\",\"message\":{\"model\":\"claude-opus-5.5\",\"usage\":{\"input_tokens\":12}}}\n\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"private-answer-body\"}}\n\ndata: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"max_tokens\"},\"usage\":{\"output_tokens\":7}}\n\ndata: {\"type\":\"message_stop\"}\n\n";
        let mut req = request(mock_server(wire)); req.provider_id = "RELAYCORE_CLAUDE".into();
        let failure = execute_streaming(req, Zeroizing::new(b"synthetic-only".to_vec()), Arc::new(AtomicBool::new(false)), |_| Ok(())).await.unwrap_err();
        assert!(!format!("{failure:?}").contains("private-answer-body"));
        let Failure::MeasuredExplicit { code, measurement, .. } = failure else { panic!("missing measured failure") };
        assert_eq!(code, "OUTPUT_LIMIT"); assert_eq!(measurement.usage.output_tokens, Some(7));
        assert!(measurement.content_free_completed().text.is_empty());
    }

    #[test]
    fn returned_model_tail_non_streaming_formats_keep_unknown_identity_and_usage() {
        for model in ["", "null", " \t ", "invalid\nmodel", &"x".repeat(201)] {
            let chat=json!({"model":model,"choices":[{"message":{"content":"answer"},"finish_reason":"stop"}],"usage":{"prompt_tokens":5,"completion_tokens":2,"cost":0.000004}});
            let responses=json!({"model":model,"status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"answer"}]}],"usage":{"input_tokens":5,"output_tokens":2,"cost":0.000004}});
            let messages=json!({"model":model,"type":"message","stop_reason":"end_turn","content":[{"type":"text","text":"answer"}],"usage":{"input_tokens":5,"output_tokens":2}});
            for reply in [decode_non_streaming(&serde_json::to_vec(&chat).unwrap(),1).unwrap(),decode_responses_non_streaming(&serde_json::to_vec(&responses).unwrap(),1).unwrap(),decode_messages(&serde_json::to_vec(&messages).unwrap(),1,false).unwrap()] {
                assert_eq!(reply.actual_model_id,None);assert_eq!(reply.text,"answer");assert_eq!(reply.usage.input_tokens,Some(5));assert_eq!(reply.usage.output_tokens,Some(2));
            }
        }
    }
    #[test]
    fn returned_model_tail_non_streaming_failure_keeps_usage_without_fake_identity() {
        let body=json!({"model":"null","error":{"code":"server_error"},"usage":{"prompt_tokens":5,"completion_tokens":2,"cost":0.000004}});
        let failure=decode_non_streaming(&serde_json::to_vec(&body).unwrap(),1).unwrap_err();
        let Failure::MeasuredExplicit{measurement,..}=failure else {panic!("measurement missing")};
        assert_eq!(measurement.actual_model_id,None);assert_eq!(measurement.usage.input_tokens,Some(5));assert_eq!(measurement.reported_cost_micros,Some(4));
    }
    #[tokio::test]
    async fn returned_model_tail_blank_chat_frame_keeps_reported_identity() {
        let wire=b"HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\ndata: {\"model\":\"reported-fixture-model\",\"choices\":[{\"delta\":{\"content\":\"answer\"}}]}\n\ndata: {\"model\":\"\",\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":2}}\n\ndata: [DONE]\n\n";
        let reply=execute_streaming(request(mock_server(wire)),Zeroizing::new(b"synthetic-only".to_vec()),Arc::new(AtomicBool::new(false)),|_|Ok(())).await.unwrap();
        assert_eq!(reply.actual_model_id.as_deref(),Some("reported-fixture-model"));assert_eq!(reply.text,"answer");assert_eq!(reply.usage.input_tokens,Some(5));
    }
    #[tokio::test]
    async fn returned_model_tail_null_string_is_unknown_without_substituting_requested() {
        let wire=b"HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\ndata: {\"model\":\"null\",\"choices\":[{\"delta\":{\"content\":\"answer\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":2}}\n\ndata: [DONE]\n\n";
        let reply=execute_streaming(request(mock_server(wire)),Zeroizing::new(b"synthetic-only".to_vec()),Arc::new(AtomicBool::new(false)),|_|Ok(())).await.unwrap();
        assert_eq!(reply.actual_model_id,None);assert_eq!(reply.text,"answer");assert_eq!(reply.usage.output_tokens,Some(2));
    }
    #[tokio::test]
    async fn returned_model_tail_blank_error_frame_preserves_identity_and_measurement() {
        let wire=b"HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\ndata: {\"model\":\"reported-fixture-model\",\"choices\":[{\"delta\":{\"content\":\"partial answer\"}}]}\n\ndata: {\"model\":\"\",\"error\":{\"code\":\"server_error\"},\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":2,\"cost\":0.000004}}\n\n";
        let failure=execute_streaming(request(mock_server(wire)),Zeroizing::new(b"synthetic-only".to_vec()),Arc::new(AtomicBool::new(false)),|_|Ok(())).await.unwrap_err();
        let Failure::MeasuredExplicit{measurement,..}=failure else {panic!("reported quantitative facts must survive failure")};
        assert_eq!(measurement.actual_model_id.as_deref(),Some("reported-fixture-model"));assert_eq!(measurement.usage.input_tokens,Some(5));assert_eq!(measurement.reported_cost_micros,Some(4));
    }
    #[tokio::test]
    async fn windows_alignment_rc_conflicting_returned_models_are_not_replaced_by_last_frame() {
        let wire = b"HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\ndata: {\"model\":\"gpt-6.1-sol\",\"choices\":[{\"delta\":{\"content\":\"answer\"}}]}\n\ndata: {\"model\":\"gpt-6-astra\",\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n";
        let mut req = request(mock_server(wire)); req.provider_id = "RELAYCORE_GPT".into();
        let reply = execute_streaming(req, Zeroizing::new(b"synthetic-only".to_vec()), Arc::new(AtomicBool::new(false)), |_| Ok(())).await.unwrap();
        assert_eq!(reply.actual_model_id.as_deref(), Some("inconsistent-response-model"));
    }

    #[tokio::test]
    async fn cancellation_interrupts_waiting_for_headers_and_deepseek_body() {
        for wait_for_body in [false, true] {
            let listener = TcpListener::bind("127.0.0.1:0").unwrap();
            let address = listener.local_addr().unwrap();
            let (ready_tx, ready_rx) = std::sync::mpsc::channel();
            let server = thread::spawn(move || {
                let (mut socket, _) = listener.accept().unwrap();
                let mut incoming = [0u8; 8192];
                socket.read(&mut incoming).unwrap();
                if wait_for_body {
                    socket.write_all(b"HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 2\r\nConnection: close\r\n\r\n").unwrap();
                }
                ready_tx.send(()).unwrap();
                thread::sleep(Duration::from_millis(450));
                let _ = socket.write_all(if wait_for_body {
                    b"{}".as_slice()
                } else {
                    b"HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 2\r\nConnection: close\r\n\r\n{}".as_slice()
                });
            });
            let cancelled = Arc::new(AtomicBool::new(false));
            let mut req = request(format!("http://{address}/anthropic/v1/messages"));
            req.provider_id = "DEEPSEEK".into();
            req.web_search_route = "DEEPSEEK_MESSAGES".into();
            let task = tokio::spawn(execute_streaming(
                req, Zeroizing::new(b"synthetic-key".to_vec()), cancelled.clone(), |_| Ok(()),
            ));
            tokio::task::spawn_blocking(move || ready_rx.recv_timeout(Duration::from_secs(5)))
                .await.unwrap().unwrap();
            cancelled.store(true, Ordering::SeqCst);
            let result = tokio::time::timeout(Duration::from_millis(250), task)
                .await.expect("cancellation must not wait for the provider response").unwrap();
            assert!(matches!(result, Err(Failure::Cancelled)));
            server.join().unwrap();
        }
    }

    #[test]
    fn native_search_endpoint_is_same_provider_and_offline_retains_chat() {
        assert_eq!(provider_endpoint("https://api.deepseek.com/v1", "DEEPSEEK_MESSAGES"), "https://api.deepseek.com/anthropic/v1/messages");
        assert_eq!(provider_endpoint("https://api.deepseek.com/v1", "NONE"), "https://api.deepseek.com/v1/chat/completions");
    }

    #[test]
    fn native_search_projects_only_final_answer_and_rejects_tool_errors() {
        let good = json!({"model":"deepseek-flash","stop_reason":"end_turn","content":[
            {"type":"text","text":"planning"},
            {"type":"web_search_tool_result","content":[{"type":"web_search_result","url":"https://example.test/a","title":"Official"}]},
            {"type":"text","text":"answer"}],"usage":{"input_tokens":8,"output_tokens":5,"cache_read_input_tokens":2}});
        let result = decode_deepseek_messages(&serde_json::to_vec(&good).unwrap(), 1).unwrap();
        assert_eq!(result.text, "answer\n\n来源：\n- [Official](https://example.test/a)");
        assert!(result.web_search_verified);
        // Messages reports 8 uncached + 2 cached input tokens.
        assert_eq!(result.usage.input_tokens, Some(10));
        assert_eq!(result.usage.cached_input_tokens, Some(2));
        for (stop, code) in [("pause_turn", "RESPONSE_INCOMPLETE"), ("max_tokens", "OUTPUT_LIMIT")] {
            let mut bad = good.clone(); bad["stop_reason"] = json!(stop); bad["usage"]["cost"] = json!(0.000012);
            let result = decode_deepseek_messages(&serde_json::to_vec(&bad).unwrap(), 1);
            match result {
                Err(Failure::MeasuredExplicit { code: actual, measurement, .. }) => {
                    assert_eq!(actual, code);
                    assert_eq!(measurement.usage.input_tokens, Some(10));
                    assert_eq!(measurement.usage.output_tokens, Some(5));
                    assert_eq!(measurement.reported_cost_micros, Some(12));
                    assert_eq!(measurement.actual_model_id.as_deref(), Some("deepseek-flash"));
                }
                other => panic!("{stop} must keep the provider measurement without an answer: {other:?}"),
            }
        }
        let mut no_public_source = good.clone();
        no_public_source["content"][1]["content"] = json!([{"type":"web_search_result","url":"https://user@example.com/private"}]);
        assert!(matches!(decode_deepseek_messages(&serde_json::to_vec(&no_public_source).unwrap(), 1), Err(Failure::MeasuredExplicit { code: "WEB_SEARCH_NO_SOURCES", measurement: ReportedMeasurement { usage: Usage { input_tokens: Some(10), output_tokens: Some(5), .. }, .. }, .. })));
        let mut bad = good; bad["content"][1]["content"] = json!({"type":"web_search_tool_result_error","error_code":"unavailable"});
        assert!(matches!(decode_deepseek_messages(&serde_json::to_vec(&bad).unwrap(), 1), Err(Failure::MeasuredExplicit { code: "WEB_SEARCH_NO_SOURCES", measurement: ReportedMeasurement { usage: Usage { input_tokens: Some(10), output_tokens: Some(5), .. }, .. }, .. })));
        bad["content"][2]["text"] = json!("");
        assert!(matches!(decode_deepseek_messages(&serde_json::to_vec(&bad).unwrap(), 1), Err(Failure::MeasuredExplicit { code: "WEB_SEARCH_NO_SOURCES", measurement: ReportedMeasurement { usage: Usage { input_tokens: Some(10), output_tokens: Some(5), .. }, .. }, .. })));
    }

    #[test]
    fn deepseek_empty_answer_keeps_reported_usage_as_failed_attempt() {
        let response = json!({"model":"deepseek-flash","stop_reason":"end_turn","content":[
            {"type":"web_search_tool_result","content":[{"type":"web_search_result","url":"https://example.test/a","title":"Official"}]},
            {"type":"text","text":"  "}
        ],"usage":{"input_tokens":8,"output_tokens":5,"cost":0.000012}});
        let result = decode_deepseek_messages(&serde_json::to_vec(&response).unwrap(), 1);
        assert!(matches!(result, Err(Failure::MeasuredExplicit {
            code: "RESPONSE_FORMAT",
            measurement: ReportedMeasurement {
                usage: Usage { input_tokens: Some(8), output_tokens: Some(5), .. },
                reported_cost_micros: Some(12), ..
            }, ..
        })), "empty answer result: {result:?}");
    }

    #[test]
    #[ignore = "explicit user-authorized live DeepSeek search only"]
    fn live_deepseek_messages_search() {
        use crate::desktop_model_service_v1::{AppPrivateProviderCredentialStore, ProviderCredentialStore};
        let root = std::env::var("NANFENG_LIVE_SEARCH_WORKSPACE").expect("explicit workspace required");
        let store = AppPrivateProviderCredentialStore::at(root);
        let mut req = request("https://api.deepseek.com/anthropic/v1/messages".into());
        req.provider_id = "DEEPSEEK".into();
        req.model_id = "deepseek-flash".into();
        req.messages = json!([{"role":"user","content":"请联网搜索 DeepSeek 官方 API 文档的 Anthropic 兼容接口地址，用一句话回答并给出来源。"}]);
        req.web_search_route = "DEEPSEEK_MESSAGES".into();
        req.max_output_tokens = 2048;
        let result = store.with_secret("DEEPSEEK", |secret| {
            tokio::runtime::Runtime::new().unwrap().block_on(execute_streaming(req, Zeroizing::new(secret.to_vec()), Arc::new(AtomicBool::new(false)), |_| Ok(()))).map_err(|failure| format!("{failure:?}"))
        }).expect("native search must succeed");
        assert!(result.web_search_verified);
        assert!(result.text.contains("https://"));
        println!("live_search verified={} input_tokens={:?} output_tokens={:?} elapsed_ms={} actual_model={:?}", result.web_search_verified, result.usage.input_tokens, result.usage.output_tokens, result.elapsed_ms, result.actual_model_id);
    }

    #[test]
    fn structured_reasoning_counts_as_progress_without_becoming_visible_text() {
        for (kind, field) in [
            ("reasoning.text", "text"),
            ("reasoning.summary", "summary"),
            ("reasoning.encrypted", "data"),
        ] {
            let mut detail = json!({"type":kind});
            detail[field] = json!("fixture");
            let frame = json!({"choices":[{"delta":{"reasoning_details":[detail]}}]});
            assert!(has_structured_reasoning_progress(&frame));
            assert!(text_delta(&frame).is_none());
        }
        assert!(!has_structured_reasoning_progress(
            &json!({"choices":[{"delta":{}}]})
        ));
    }

    #[test]
    fn qwen_max_uses_low_reasoning_and_combined_token_budget() {
        let mut fixture = request("https://provider.invalid/chat/completions".into());
        fixture.provider_id = "QWEN".into();
        fixture.model_id = "qwen3.8-max".into();
        fixture.max_output_tokens = ordinary_output_token_limit("QWEN", "qwen3.8-max");
        for route in ["NONE", "QWEN_CHAT_COMPLETIONS"] {
            fixture.web_search_route = route.into();
            let body = transport_body(&fixture);
            assert_eq!(body["reasoning_effort"], "low");
            assert_eq!(body["preserve_thinking"], false);
            assert_eq!(body["max_completion_tokens"], 16384);
            assert!(body.get("max_tokens").is_none());
        }
    }

    #[tokio::test]
    #[ignore = "80-second production-timeout regression; explicit release verification only"]
    async fn real_stream_reasoning_past_old_75_second_limit_completes() {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let address = listener.local_addr().unwrap();
        let server = thread::spawn(move || {
            let (mut socket, _) = listener.accept().unwrap();
            let mut incoming = [0u8; 8192];
            socket.read(&mut incoming).unwrap();
            socket.write_all(b"HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\n").unwrap();
            for _ in 0..2 {
                thread::sleep(Duration::from_secs(40));
                socket.write_all(b"data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"fixture\"}}]}\n\n").unwrap();
            }
            socket.write_all(b"data: {\"choices\":[{\"delta\":{\"content\":\"42\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n").unwrap();
        });
        let mut visible = String::new();
        let result = execute_streaming(
            request(format!("http://{address}/chat/completions")),
            Zeroizing::new(b"test".to_vec()),
            Arc::new(AtomicBool::new(false)),
            |delta| {
                visible.push_str(delta);
                Ok(())
            },
        )
        .await
        .unwrap();
        server.join().unwrap();
        assert_eq!(result.text, "42");
        assert_eq!(visible, "42");
        assert!(result.elapsed_ms >= 80_000);
    }

    #[tokio::test]
    async fn finish_reason_does_not_discard_trailing_usage_or_cost() {
        let endpoint = mock_server(b"HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\ndata: {\"choices\":[{\"delta\":{\"content\":\"42\"},\"finish_reason\":\"stop\"}]}\n\ndata: {\"choices\":[],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":2,\"cost\":0.0001}}\n\ndata: [DONE]\n\n");
        let reply = execute_streaming(
            request(endpoint),
            Zeroizing::new(b"test".to_vec()),
            Arc::new(AtomicBool::new(false)),
            |_| Ok(()),
        )
        .await
        .unwrap();
        assert_eq!(reply.text, "42");
        assert_eq!(reply.usage.output_tokens, Some(2));
        assert_eq!(reply.reported_cost_micros, Some(100));
    }

    #[tokio::test]
    async fn streaming_empty_answer_keeps_reported_usage_as_failed_attempt() {
        let endpoint = mock_server(b"HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\ndata: {\"choices\":[{\"finish_reason\":\"stop\"}]}\n\ndata: {\"model\":\"fixture-actual\",\"usage\":{\"prompt_tokens\":9,\"completion_tokens\":2,\"cost\":0.000012}}\n\ndata: [DONE]\n\n");
        let result = execute_streaming(
            request(endpoint), Zeroizing::new(b"test".to_vec()),
            Arc::new(AtomicBool::new(false)), |_| Ok(()),
        ).await;
        assert!(matches!(result, Err(Failure::MeasuredExplicit {
            code: "RESPONSE_FORMAT",
            measurement: ReportedMeasurement {
                usage: Usage { input_tokens: Some(9), output_tokens: Some(2), .. },
                reported_cost_micros: Some(12), ..
            }, ..
        })), "empty answer result: {result:?}");
    }

    #[tokio::test]
    async fn in_band_error_after_partial_answer_is_a_measured_failure() {
        let endpoint = mock_server(b"HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\ndata: {\"choices\":[{\"delta\":{\"content\":\"partial\"},\"finish_reason\":\"stop\"}]}\n\ndata: {\"model\":\"fixture-actual\",\"usage\":{\"prompt_tokens\":9,\"completion_tokens\":2,\"cost\":0.000012}}\n\ndata: {\"error\":{\"message\":\"synthetic private detail\",\"code\":\"server_error\"}}\n\ndata: [DONE]\n\n");
        let mut visible = String::new();
        let result = execute_streaming(
            request(endpoint), Zeroizing::new(b"test".to_vec()),
            Arc::new(AtomicBool::new(false)), |delta| { visible.push_str(delta); Ok(()) },
        ).await;
        assert_eq!(visible, "partial");
        assert!(matches!(result, Err(Failure::MeasuredExplicit {
            code: "PROVIDER_RESPONSE_FAILED",
            measurement: ReportedMeasurement {
                usage: Usage { input_tokens: Some(9), output_tokens: Some(2), .. },
                reported_cost_micros: Some(12), ..
            }, ..
        })), "in-band result: {result:?}");
    }

    #[tokio::test]
    async fn responses_error_event_without_usage_is_explicit_failure() {
        let endpoint = mock_server(b"HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\ndata: {\"type\":\"response.output_text.delta\",\"delta\":\"partial\"}\n\ndata: {\"type\":\"error\",\"error\":{\"message\":\"synthetic private detail\"}}\n\ndata: [DONE]\n\n");
        let mut fixture = request(endpoint);
        fixture.provider_id = "QWEN".into();
        fixture.web_search_route = "QWEN_RESPONSES".into();
        let result = execute_streaming(
            fixture, Zeroizing::new(b"test".to_vec()),
            Arc::new(AtomicBool::new(false)), |_| Ok(()),
        ).await;
        assert!(matches!(result, Err(Failure::Explicit {
            code: "PROVIDER_RESPONSE_FAILED", ..
        })), "in-band response result: {result:?}");
    }

    #[tokio::test]
    async fn output_limit_keeps_partial_text_but_never_claims_complete() {
        let endpoint = mock_server(b"HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\ndata: {\"choices\":[{\"delta\":{\"content\":\"partial\"},\"finish_reason\":\"length\"}]}\n\ndata: [DONE]\n\n");
        let mut visible = String::new();
        let result = execute_streaming(
            request(endpoint),
            Zeroizing::new(b"test".to_vec()),
            Arc::new(AtomicBool::new(false)),
            |delta| {
                visible.push_str(delta);
                Ok(())
            },
        )
        .await;
        assert_eq!(visible, "partial");
        assert!(matches!(
            result,
            Err(Failure::Explicit {
                code: "OUTPUT_LIMIT",
                ..
            })
        ));
    }

    #[tokio::test]
    async fn truncated_or_filtered_stream_reads_reported_usage_after_finish_reason() {
        for (reason, code) in [("length", "OUTPUT_LIMIT"), ("content_filter", "CONTENT_FILTER")] {
            let response = format!("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\ndata: {{\"choices\":[{{\"delta\":{{\"content\":\"partial\"}}}}]}}\n\ndata: {{\"choices\":[{{\"finish_reason\":\"{reason}\"}}]}}\n\ndata: {{\"model\":\"fixture-actual\",\"usage\":{{\"prompt_tokens\":9,\"completion_tokens\":2,\"cost\":0.000012}}}}\n\ndata: [DONE]\n\n");
            let response: &'static [u8] = Box::leak(response.into_bytes().into_boxed_slice());
            let mut visible = String::new();
            let result = execute_streaming(
                request(mock_server(response)), Zeroizing::new(b"test".to_vec()),
                Arc::new(AtomicBool::new(false)), |delta| { visible.push_str(delta); Ok(()) },
            ).await;
            assert_eq!(visible, "partial");
            match result {
                Err(Failure::MeasuredExplicit { code: actual, measurement, .. }) => {
                    assert_eq!(actual, code);
                    assert_eq!(measurement.usage.input_tokens, Some(9));
                    assert_eq!(measurement.usage.output_tokens, Some(2));
                    assert_eq!(measurement.reported_cost_micros, Some(12));
                    assert_eq!(measurement.actual_model_id.as_deref(), Some("fixture-actual"));
                }
                other => panic!("expected measured {reason} failure, got {other:?}"),
            }
        }
    }

    #[tokio::test]
    async fn known_output_limit_survives_a_broken_tail_connection() {
        let body = "data: {\"choices\":[{\"delta\":{\"content\":\"partial\"},\"finish_reason\":\"length\"}]}\n\ndata: {\"model\":\"fixture-actual\",\"usage\":{\"prompt_tokens\":9,\"completion_tokens\":2,\"cost\":0.000012}}\n\n";
        let response = format!("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: 8192\r\nConnection: close\r\n\r\n{body}");
        let response: &'static [u8] = Box::leak(response.into_bytes().into_boxed_slice());
        let mut visible = String::new();
        let result = execute_streaming(
            request(mock_server(response)), Zeroizing::new(b"test".to_vec()),
            Arc::new(AtomicBool::new(false)), |delta| { visible.push_str(delta); Ok(()) },
        ).await;
        assert_eq!(visible, "partial");
        assert!(matches!(result, Err(Failure::MeasuredExplicit {
            code: "OUTPUT_LIMIT",
            measurement: ReportedMeasurement {
                usage: Usage { input_tokens: Some(9), output_tokens: Some(2), .. },
                reported_cost_micros: Some(12), ..
            }, ..
        })), "broken tail result: {result:?}");

        let unconfirmed = "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: 8192\r\nConnection: close\r\n\r\ndata: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n";
        let result = execute_streaming(
            request(mock_server(Box::leak(unconfirmed.as_bytes().to_vec().into_boxed_slice()))),
            Zeroizing::new(b"test".to_vec()), Arc::new(AtomicBool::new(false)), |_| Ok(()),
        ).await;
        assert!(matches!(result, Err(Failure::Unknown { code: "NETWORK" })));
    }

    #[test]
    fn reasoning_progress_survives_first_visible_deadline_but_is_bounded() {
        let mut budget = StreamProgressBudget::default();
        budget.advance(Duration::from_secs(60));
        assert_eq!(
            budget.remaining(Duration::from_secs(80)),
            Duration::from_secs(70)
        );
        // Continued reasoning can legitimately last minutes before visible text.
        budget.advance(Duration::from_secs(120));
        assert!(!budget.remaining(Duration::from_secs(180)).is_zero());
        budget.advance(Duration::from_secs(590));
        assert_eq!(
            budget.remaining(Duration::from_secs(595)),
            Duration::from_secs(5)
        );
        assert!(budget.remaining(Duration::from_secs(600)).is_zero());
    }

    #[test]
    fn heartbeats_and_stalled_reasoning_cannot_keep_an_empty_answer_alive() {
        let mut budget = StreamProgressBudget::default();
        assert!(budget.remaining(Duration::from_secs(90)).is_zero());
        budget.advance(Duration::from_secs(20));
        assert!(budget.remaining(Duration::from_secs(110)).is_zero());
    }

    #[test]
    fn ordinary_reasoning_parameters_match_android_for_plain_and_web_requests() {
        let mut fixture = request("https://provider.invalid/chat/completions".into());
        fixture.provider_id = "ZHIPU".into();
        for model in ["glm-5.3", "glm-5.3-flash"] {
            fixture.model_id = model.into();
            for route in ["NONE", "ZHIPU_CHAT_COMPLETIONS"] {
                fixture.web_search_route = route.into();
                let body = transport_body(&fixture);
                assert_eq!(body["thinking"]["type"], "enabled");
                assert_eq!(body["reasoning_effort"], "max");
            }
        }
        fixture.provider_id = "OPENROUTER".into();
        for model in [
            "anthropic/claude-fable-5.1-20260831",
            "anthropic/claude-opus-5",
            "openai/gpt-6-astra",
            "openai/gpt-5.6-sol",
        ] {
            fixture.model_id = model.into();
            for route in ["NONE", "OPENROUTER_SERVER_TOOL"] {
                fixture.web_search_route = route.into();
                assert_eq!(transport_body(&fixture)["reasoning"]["effort"], "high");
            }
        }
        fixture.model_id = "openai/gpt-5.6-luna".into();
        assert!(transport_body(&fixture).get("reasoning").is_none());
    }

    #[test]
    fn web_search_request_shapes_match_android_provider_contracts() {
        let mut fixture = request("https://provider.invalid/chat/completions".into());
        let ordinary = transport_body(&fixture);
        assert_eq!(ordinary["stream"], true);
        assert_eq!(ordinary["stream_options"]["include_usage"], true);

        fixture.web_search_route = "OPENROUTER_SERVER_TOOL".into();
        let body = transport_body(&fixture);
        assert_eq!(body["tools"][0]["type"], "openrouter:web_search");
        assert_eq!(body["tools"][0]["parameters"]["max_results"], 5);

        fixture.web_search_route = "ZHIPU_CHAT_COMPLETIONS".into();
        let body = transport_body(&fixture);
        assert_eq!(body["tools"][0]["type"], "web_search");
        assert_eq!(
            body["tools"][0]["web_search"]["search_engine"],
            "search_std"
        );
        assert_eq!(body["tool_choice"], "auto");

        fixture.web_search_route = "QWEN_CHAT_COMPLETIONS".into();
        let body = transport_body(&fixture);
        assert_eq!(body["enable_search"], true);
        assert_eq!(body["search_options"]["forced_search"], true);

        fixture.web_search_route = "QWEN_RESPONSES".into();
        let body = transport_body(&fixture);
        assert_eq!(body["tools"][0]["type"], "web_search");
        assert_eq!(body["store"], false);
        assert_eq!(body["stream"], true);
        assert_eq!(body["input"][0]["content"][0]["type"], "input_text");

        fixture.web_search_route = "DEEPSEEK_MESSAGES".into();
        let body = transport_body(&fixture);
        assert_eq!(body["tools"][0]["type"], "web_search_20250305");
        assert_eq!(body["tool_choice"]["name"], "web_search");
        assert_eq!(body["stream"], false);
    }

    #[test]
    fn deepseek_messages_preserves_images_and_requested_identity() {
        let mut fixture = request("http://127.0.0.1:1".into());
        fixture.model_id = "deepseek-flash".into();
        fixture.messages = json!([{"role":"user","content":[{"type":"text","text":"look"},{"type":"image_url","image_url":{"url":"data:image/png;base64,AQID"}}]}]);
        fixture.web_search_route = "DEEPSEEK_MESSAGES".into();
        let body = transport_body(&fixture);
        assert_eq!(body["model"], "deepseek-flash");
        assert_eq!(
            body["messages"][0]["content"][1],
            json!({"type":"image","source":{"type":"base64","media_type":"image/png","data":"AQID"}})
        );
        assert_eq!(body["stream"], false);
        fixture.web_search_route = "NONE".into();
        assert_eq!(transport_body(&fixture)["messages"], fixture.messages);
    }

    #[test]
    fn responses_decoder_keeps_usage_and_appends_safe_deduplicated_sources() {
        let result = decode_responses_non_streaming(
            br#"{"model":"deepseek-chat","output_text":"answer","output":[{"type":"web_search_call","action":{"sources":[{"title":"Official","url":"https://example.com/a"},{"title":"Duplicate","url":"https://example.com/a"},{"title":"Unsafe","url":"file:///tmp/a"}]}}],"usage":{"input_tokens":5,"output_tokens":7,"input_tokens_details":{"cached_tokens":2},"output_tokens_details":{"reasoning_tokens":3},"cost":0.000009}}"#,
            12,
        )
        .unwrap();
        assert_eq!(
            result.text,
            "answer\n\n来源：\n- [Official](https://example.com/a)"
        );
        assert_eq!(result.usage.input_tokens, Some(5));
        assert_eq!(result.usage.cached_input_tokens, Some(2));
        assert_eq!(result.usage.reasoning_tokens, Some(3));
        assert_eq!(result.reported_cost_micros, Some(9));
        assert_eq!(result.actual_model_id.as_deref(), Some("deepseek-chat"));
    }

    #[test]
    fn deepseek_responses_marks_answer_without_search_evidence_unverified() {
        let result = decode_responses_non_streaming(
            br#"{"model":"deepseek-chat","output_text":"provider answer","usage":{"input_tokens":3,"output_tokens":2}}"#,
            12,
        )
        .expect("a completed answer without search evidence remains usable");
        assert_eq!(result.text, "provider answer");
        assert_eq!(result.usage.input_tokens, Some(3));
        assert_eq!(result.usage.output_tokens, Some(2));
        assert!(!result.web_search_verified);
    }

    #[test]
    fn deepseek_messages_refuses_success_without_search_results() {
        let body = b"HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"model\":\"deepseek-chat\",\"content\":[{\"type\":\"text\",\"text\":\"provider answer\"}],\"stop_reason\":\"end_turn\"}";
        let mut request = request(mock_server_with_stream(body, false));
        request.web_search_route = "DEEPSEEK_MESSAGES".into();
        let mut deltas = Vec::new();
        let completed = tokio::runtime::Runtime::new()
            .unwrap()
            .block_on(execute_streaming(
                request,
                Zeroizing::new(b"fixture-secret-123".to_vec()),
                Arc::new(AtomicBool::new(false)),
                |delta| {
                    deltas.push(delta.to_owned());
                    Ok(())
                },
            ))
            .expect_err("enabled search must not silently degrade");
        assert!(matches!(&completed, Failure::MeasuredExplicit { code: "WEB_SEARCH_NO_SOURCES", .. }));
        let facts=completed.quantitative_completed().expect("Received identity remains known despite absent search sources and usage");
        assert_eq!(facts.actual_model_id.as_deref(),Some("deepseek-chat"));
        assert_eq!(facts.usage,Usage::default());assert_eq!(facts.reported_cost_micros,None);assert!(facts.text.is_empty());
        assert!(deltas.is_empty());
    }

    #[test]
    fn responses_stream_requires_public_sources_even_when_search_tool_completed() {
        for output in [
            json!([]),
            json!([{"type":"web_search_call","status":"completed","action":{"sources":[]}}]),
            json!([{"type":"web_search_call","status":"completed","action":{"sources":[{"title":"private","url":"https://user@example.com/private"}]}}]),
        ] {
            let event = json!({"type":"response.completed","response":{"model":"fixture-model","output":output,"usage":{"input_tokens":11,"output_tokens":7}}});
            let response = format!("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\ndata: {{\"type\":\"response.output_text.delta\",\"delta\":\"complete answer\"}}\n\ndata: {event}\n\n");
            let response: &'static [u8] = Box::leak(response.into_bytes().into_boxed_slice());
            let mut fixture = request(mock_server(response));
            fixture.provider_id = "QWEN".into();
            fixture.web_search_route = "QWEN_RESPONSES".into();
            let completed = tokio::runtime::Runtime::new()
                .unwrap()
                .block_on(execute_streaming(
                    fixture,
                    Zeroizing::new(b"fixture-secret-123".to_vec()),
                    Arc::new(AtomicBool::new(false)),
                    |_| Ok(()),
                ))
                ;
            assert!(matches!(completed, Err(Failure::MeasuredExplicit { code: "WEB_SEARCH_NO_SOURCES", measurement: ReportedMeasurement { usage: Usage { input_tokens: Some(11), output_tokens: Some(7), .. }, .. }, .. })));
        }
        let event = json!({"type":"response.completed","response":{"model":"fixture-model","output":[],"usage":{"input_tokens":11,"output_tokens":0,"cost":0.000009}}});
        let response = format!("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\ndata: {event}\n\n");
        let response: &'static [u8] = Box::leak(response.into_bytes().into_boxed_slice());
        let mut fixture = request(mock_server(response));
        fixture.provider_id = "QWEN".into();
        fixture.web_search_route = "QWEN_RESPONSES".into();
        let empty = tokio::runtime::Runtime::new().unwrap().block_on(execute_streaming(
            fixture, Zeroizing::new(b"fixture-secret-123".to_vec()),
            Arc::new(AtomicBool::new(false)), |_| Ok(()),
        ));
        assert!(matches!(empty, Err(Failure::MeasuredExplicit { code: "WEB_SEARCH_NO_SOURCES", measurement: ReportedMeasurement { usage: Usage { input_tokens: Some(11), output_tokens: Some(0), .. }, reported_cost_micros: Some(9), .. }, .. })));
    }

    #[test]
    fn responses_terminal_failure_retains_reported_measurement_without_accepting_answer() {
        for (terminal, expected_code) in [
            ("response.incomplete", "INCOMPLETE_RESPONSE"),
            ("response.failed", "PROVIDER_RESPONSE_FAILED"),
        ] {
            let event = json!({"type":terminal,"response":{"model":"fixture-actual","usage":{"input_tokens":13,"output_tokens":4,"cost":0.000009}}});
            let response = format!("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\ndata: {{\"type\":\"response.output_text.delta\",\"delta\":\"unverified answer\"}}\n\ndata: {event}\n\n");
            let response: &'static [u8] = Box::leak(response.into_bytes().into_boxed_slice());
            let mut fixture = request(mock_server(response));
            fixture.provider_id = "QWEN".into();
            fixture.web_search_route = "QWEN_RESPONSES".into();
            let result = tokio::runtime::Runtime::new().unwrap().block_on(execute_streaming(
                fixture, Zeroizing::new(b"fixture-secret-123".to_vec()),
                Arc::new(AtomicBool::new(false)), |_| Ok(()),
            ));
            match result {
                Err(Failure::MeasuredExplicit { code, measurement, .. }) => {
                    assert_eq!(code, expected_code);
                    assert_eq!(measurement.usage.input_tokens, Some(13));
                    assert_eq!(measurement.usage.output_tokens, Some(4));
                    assert_eq!(measurement.reported_cost_micros, Some(9));
                    assert_eq!(measurement.actual_model_id.as_deref(), Some("fixture-actual"));
                }
                other => panic!("expected measured terminal failure, got {other:?}"),
            }
        }
        let event = json!({"type":"response.failed","model":"fixture-root",
            "usage":{"input_tokens":3,"output_tokens":0,"cost":0.000002}});
        let response = format!("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\ndata: {event}\n\n");
        let response: &'static [u8] = Box::leak(response.into_bytes().into_boxed_slice());
        let mut fixture = request(mock_server(response));
        fixture.provider_id = "QWEN".into();
        fixture.web_search_route = "QWEN_RESPONSES".into();
        let result = tokio::runtime::Runtime::new().unwrap().block_on(execute_streaming(
            fixture, Zeroizing::new(b"fixture-secret-123".to_vec()),
            Arc::new(AtomicBool::new(false)), |_| Ok(()),
        ));
        assert!(matches!(result, Err(Failure::MeasuredExplicit {
            code: "PROVIDER_RESPONSE_FAILED",
            measurement: ReportedMeasurement {
                usage: Usage { input_tokens: Some(3), output_tokens: Some(0), .. },
                reported_cost_micros: Some(2), ..
            }, ..
        })));
    }

    #[test]
    fn responses_done_marker_without_completed_event_never_commits_grounded_answer() {
        let event = json!({"type":"response.output_text.delta","delta":"unconfirmed answer",
            "output":[{"type":"web_search_call","action":{"sources":[{"title":"Official","url":"https://example.com/source"}]}}]});
        let response = format!("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\ndata: {event}\n\ndata: [DONE]\n\n");
        let response: &'static [u8] = Box::leak(response.into_bytes().into_boxed_slice());
        let mut fixture = request(mock_server(response));
        fixture.provider_id = "QWEN".into();
        fixture.web_search_route = "QWEN_RESPONSES".into();
        let result = tokio::runtime::Runtime::new().unwrap().block_on(execute_streaming(
            fixture, Zeroizing::new(b"fixture-secret-123".to_vec()),
            Arc::new(AtomicBool::new(false)), |_| Ok(()),
        ));
        assert!(matches!(result, Err(Failure::Unknown { code: "MISSING_COMPLETION" })));
    }

    #[test]
    fn title_transport_is_a_non_streaming_json_request() {
        let body = b"HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"choices\":[{\"message\":{\"content\":\"{\\\"title\\\":\\\"KFKPlan\\\"}\"}}]}";
        let request = request(mock_server_with_stream(body, false));
        let completed = tokio::runtime::Runtime::new()
            .unwrap()
            .block_on(execute_non_streaming(
                request,
                Zeroizing::new(b"fixture-secret-123".to_vec()),
            ))
            .unwrap();
        assert_eq!(completed.text, "{\"title\":\"KFKPlan\"}");
    }

    #[test]
    fn deepseek_title_request_disables_thinking_and_requires_json() {
        let mut fixture = request("https://provider.invalid/chat/completions".into());
        fixture.provider_id = "DEEPSEEK".into();
        fixture.structured_json = true;
        fixture.disable_thinking = true;

        let body = transport_body(&fixture);
        assert_eq!(body["response_format"]["type"], "json_object");
        assert_eq!(body["thinking"]["type"], "disabled");
    }

    #[test]
    fn glm_background_request_preserves_max_reasoning_and_a_real_answer_budget() {
        let mut fixture = request("https://provider.invalid/chat/completions".into());
        fixture.provider_id = "ZHIPU".into();
        fixture.model_id = "glm-5.3-flash".into();
        fixture.max_output_tokens = 8192;
        let body = non_streaming_body(&fixture);
        assert_eq!(body["max_tokens"], 8192);
        assert_eq!(body["thinking"]["type"], "enabled");
        assert_eq!(body["reasoning_effort"], "max");
        assert_eq!(body["stream"], false);
    }

    #[test]
    fn reasoning_exhaustion_is_an_output_limit_not_a_format_error() {
        let response = br#"{"choices":[{"finish_reason":"length","message":{"content":"","reasoning_content":"fixture reasoning"}}]}"#;
        assert!(matches!(
            decode_non_streaming(response, 1),
            Err(Failure::Explicit {
                code: "OUTPUT_LIMIT",
                ..
            })
        ));
    }

    #[test]
    fn non_streaming_failed_title_keeps_provider_measurement() {
        for (finish_reason, content, expected_code) in [
            ("length", "unfinished", "OUTPUT_LIMIT"),
            ("stop", "  ", "RESPONSE_FORMAT"),
        ] {
            let response = json!({
                "model":"fixture-actual",
                "choices":[{"finish_reason":finish_reason,"message":{"content":content}}],
                "usage":{"prompt_tokens":31,"completion_tokens":9,"cost":0.000077}
            });
            let result = decode_non_streaming(&serde_json::to_vec(&response).unwrap(), 12);
            assert!(matches!(result, Err(Failure::MeasuredExplicit {
                code,
                measurement: ReportedMeasurement {
                    usage: Usage { input_tokens: Some(31), output_tokens: Some(9), .. },
                    reported_cost_micros: Some(77), ..
                }, ..
            }) if code == expected_code), "{finish_reason} result: {result:?}");
        }
    }

    #[test]
    fn non_streaming_title_accepts_the_same_content_parts_as_android() {
        let result = decode_non_streaming(
            r#"{"model":"deepseek-flash","choices":[{"message":{"content":[{"type":"text","text":"{\"title\":"},{"type":"output_text","text":"\"KFK资料整理方案\"}"}]}}],"usage":{"prompt_tokens":9,"completion_tokens":7}}"#.as_bytes(),
            12,
        )
        .unwrap();

        assert_eq!(result.text, "{\"title\":\"KFK资料整理方案\"}");
        assert_eq!(result.usage.input_tokens, Some(9));
        assert_eq!(result.usage.output_tokens, Some(7));
    }

    #[test]
    fn chat_completions_requires_search_evidence_when_enabled() {
        let without_sources = b"HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\ndata: {\"choices\":[{\"delta\":{\"content\":\"provider answer\"}}]}\n\ndata: [DONE]\n\n";
        let mut missing = request(mock_server(without_sources));
        missing.web_search_route = "OPENROUTER_SERVER_TOOL".into();
        let completed = tokio::runtime::Runtime::new()
            .unwrap()
            .block_on(execute_streaming(
                missing,
                Zeroizing::new(b"fixture-secret-123".to_vec()),
                Arc::new(AtomicBool::new(false)),
                |_| Ok(()),
            ))
            .expect_err("enabled search cannot complete without execution evidence");
        assert!(matches!(completed, Failure::Explicit { code: "WEB_SEARCH_NO_SOURCES", .. }));

        let tool_only = b"HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\ndata: {\"choices\":[{\"delta\":{\"content\":\"provider answer\"}}],\"output\":[{\"type\":\"web_search_call\",\"status\":\"completed\",\"action\":{\"sources\":[]}}]}\n\ndata: [DONE]\n\n";
        let mut missing = request(mock_server(tool_only));
        missing.web_search_route = "OPENROUTER_SERVER_TOOL".into();
        let completed = tokio::runtime::Runtime::new()
            .unwrap()
            .block_on(execute_streaming(
                missing,
                Zeroizing::new(b"fixture-secret-123".to_vec()),
                Arc::new(AtomicBool::new(false)),
                |_| Ok(()),
            ))
            .expect_err("completed tool state without public sources is not a grounded answer");
        assert!(matches!(completed, Failure::Explicit { code: "WEB_SEARCH_NO_SOURCES", .. }));

        let with_sources = b"HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\ndata: {\"choices\":[{\"delta\":{\"content\":\"grounded answer\",\"annotations\":[{\"url_citation\":{\"title\":\"Official\",\"url\":\"https://example.com/source\"}},{\"url_citation\":{\"title\":\"Unsafe\",\"url\":\"https://user@example.com/private\"}}]}}]}\n\ndata: [DONE]\n\n";
        let mut grounded = request(mock_server(with_sources));
        grounded.web_search_route = "OPENROUTER_SERVER_TOOL".into();
        let completed = tokio::runtime::Runtime::new()
            .unwrap()
            .block_on(execute_streaming(
                grounded,
                Zeroizing::new(b"fixture-secret-123".to_vec()),
                Arc::new(AtomicBool::new(false)),
                |_| Ok(()),
            ))
            .unwrap();
        assert_eq!(
            completed.text,
            "grounded answer\n\n来源：\n- [Official](https://example.com/source)"
        );
        assert!(completed.web_search_verified);
    }

    fn responses_terminal_status_transport(event: &str, status: Option<Value>, reason: Option<&str>) -> Result<Completed, Failure> {
        let mut response=json!({"status":status,"model":"meta/muse-wonder","output":[
            {"type":"web_search_call","status":"completed","action":{"type":"search","sources":[{"url":"https://example.com/terminal-status","title":"合成终态来源"}]}},
            {"type":"message","content":[{"type":"output_text","text":"合成终态回答"}]}],
            "usage":{"input_tokens":3,"output_tokens":2}});
        if status.is_none(){response.as_object_mut().unwrap().remove("status");}
        if let Some(reason)=reason{response["incomplete_details"]=json!({"reason":reason});}
        let wire=format!("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\ndata: {{\"type\":\"response.output_text.delta\",\"delta\":\"合成终态回答\"}}\n\ndata: {}\n\n",json!({"type":event,"response":response}));
        let mut args=request(mock_server_with_stream(Box::leak(wire.into_bytes().into_boxed_slice()),true));
        args.provider_id="OPENROUTER".into();args.model_id="meta/muse-wonder".into();args.web_search_route="OPENROUTER_SERVER_TOOL".into();
        tokio::runtime::Runtime::new().unwrap().block_on(execute_streaming(args,Zeroizing::new(b"synthetic-terminal-status".to_vec()),Arc::new(AtomicBool::new(false)), |_|Ok(())))
    }

    #[test]
    fn responses_terminal_explicit_noncompleted_does_not_complete() {
        for status in [json!("failed"),json!("incomplete"),json!("in_progress"),json!("queued"),json!("cancelled"),json!("unknown"),Value::Null,json!(7)] {
            let expected=if status=="failed"{"PROVIDER_RESPONSE_FAILED"}else{"INCOMPLETE_RESPONSE"};
            let failure=responses_terminal_status_transport("response.completed",Some(status),None).expect_err("completed event cannot override an explicit non-completed response");
            assert!(matches!(&failure,Failure::MeasuredExplicit{code,..} if *code==expected));
            let measured=failure.quantitative_completed().unwrap();assert_eq!(measured.usage.input_tokens,Some(3));assert_eq!(measured.usage.output_tokens,Some(2));assert!(measured.text.is_empty());
        }
    }

    #[test]
    fn responses_terminal_output_limit_keeps_received_usage() {
        for event in ["response.completed","response.incomplete"] {
            let failure=responses_terminal_status_transport(event,Some(json!("incomplete")),Some("max_output_tokens")).expect_err("output limit cannot become a complete answer");
            assert!(matches!(&failure,Failure::MeasuredExplicit{code:"OUTPUT_LIMIT",..}));
            let measured=failure.quantitative_completed().unwrap();assert_eq!(measured.usage.input_tokens,Some(3));assert_eq!(measured.usage.output_tokens,Some(2));assert!(measured.text.is_empty());
        }
    }

    #[test]
    fn responses_terminal_completed_and_legacy_remain_compatible() {
        for status in [Some(json!("completed")),None] {
            let completed=responses_terminal_status_transport("response.completed",status,None).unwrap();assert!(completed.web_search_verified);assert!(completed.text.starts_with("合成终态回答"));assert_eq!(completed.usage.input_tokens,Some(3));
        }
    }

    #[test]
    fn responses_terminal_failed_event_cannot_be_upgraded() {
        for (event,expected) in [("response.failed","PROVIDER_RESPONSE_FAILED"),("response.incomplete","INCOMPLETE_RESPONSE")] {
            let failure=responses_terminal_status_transport(event,Some(json!("completed")),None).unwrap_err();assert!(matches!(failure,Failure::MeasuredExplicit{code,..} if code==expected));
        }
    }

    fn source_tool_status_transport(status: Option<Value>, streaming: bool) -> Result<Completed, Failure> {
        let mut response=json!({"status":"completed","model":"meta/muse-wonder","output":[
            {"type":"web_search_call","status":status,"action":{"type":"search","sources":[{"url":"https://example.com/tool-status","title":"工具来源"}]}},
            {"type":"message","content":[{"type":"output_text","text":"合成状态回答"}]}],
            "usage":{"input_tokens":3,"output_tokens":2}});
        if status.is_none() {response["output"][0].as_object_mut().unwrap().remove("status");}
        let wire=if streaming {
            format!("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\ndata: {{\"type\":\"response.output_text.delta\",\"delta\":\"合成状态回答\"}}\n\ndata: {}\n\n",json!({"type":"response.completed","response":response}))
        } else {format!("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{response}")};
        let mut args=request(mock_server_with_stream(Box::leak(wire.into_bytes().into_boxed_slice()),streaming));
        args.provider_id="OPENROUTER".into();args.model_id="meta/muse-wonder".into();args.web_search_route="OPENROUTER_SERVER_TOOL".into();
        let runtime=tokio::runtime::Runtime::new().unwrap();
        if streaming {runtime.block_on(execute_streaming(args,Zeroizing::new(b"synthetic-tool-status".to_vec()),Arc::new(AtomicBool::new(false)), |_|Ok(())))}
        else {runtime.block_on(execute_non_streaming(args,Zeroizing::new(b"synthetic-tool-status".to_vec())))}
    }

    #[test]
    fn source_tool_status_noncompleted_cannot_verify_search() {
        for streaming in [false,true] {
            for status in [json!("failed"),json!("incomplete"),json!("in_progress"),json!("searching"),json!("unknown"),Value::Null,json!(7)] {
                let failure=source_tool_status_transport(Some(status),streaming).expect_err("explicit non-completed tool sources cannot verify search");
                assert!(matches!(failure,Failure::Explicit{code:"WEB_SEARCH_NO_SOURCES",..}|Failure::MeasuredExplicit{code:"WEB_SEARCH_NO_SOURCES",..}));
                let measured=failure.quantitative_completed().unwrap();assert_eq!(measured.usage.input_tokens,Some(3));assert_eq!(measured.usage.output_tokens,Some(2));assert!(measured.text.is_empty());
            }
        }
    }

    #[test]
    fn source_tool_status_completed_retains_sources_and_measurement() {
        for streaming in [false,true] {
          for status in [Some(json!("completed")),None] {
            let completed=source_tool_status_transport(status,streaming).unwrap();
            assert!(completed.web_search_verified);assert_eq!(completed.native_sources,vec![("工具来源".into(),"https://example.com/tool-status".into())]);
            assert_eq!(completed.usage.input_tokens,Some(3));assert_eq!(completed.usage.output_tokens,Some(2));
          }
        }
    }

    fn source_annotation_transport(annotation: Value, streaming: bool) -> Result<Completed, Failure> {
        let envelope = if streaming {
            json!({"choices":[{"delta":{"content":"annotation answer", "annotations":[annotation]}}],"usage":{"prompt_tokens":3,"completion_tokens":2}})
        } else {
            json!({"choices":[{"message":{"content":"annotation answer", "annotations":[annotation]},"finish_reason":"stop"}],"usage":{"prompt_tokens":3,"completion_tokens":2}})
        };
        let wire = if streaming {
            format!("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\ndata: {envelope}\n\ndata: [DONE]\n\n")
        } else {
            format!("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{envelope}")
        };
        let mut args = request(mock_server_with_stream(Box::leak(wire.into_bytes().into_boxed_slice()), streaming));
        args.web_search_route = "OPENROUTER_SERVER_TOOL".into();
        let runtime = tokio::runtime::Runtime::new().unwrap();
        if streaming {
            runtime.block_on(execute_streaming(args,Zeroizing::new(b"synthetic-source-type-key".to_vec()),Arc::new(AtomicBool::new(false)), |_| Ok(())))
        } else {
            runtime.block_on(execute_non_streaming(args,Zeroizing::new(b"synthetic-source-type-key".to_vec())))
        }
    }

    #[test]
    fn source_annotation_wrong_declared_types_are_not_search_evidence() {
        for streaming in [false,true] {
            for annotation in [
                json!({"type":"file_citation","url":"https://example.com/file","title":"File"}),
                json!({"type":"unknown","url_citation":{"url":"https://example.com/nested","title":"Conflicting"}}),
                json!({"type":null,"url":"https://example.com/null","title":"Null"}),
                json!({"type":7,"url":"https://example.com/number","title":"Number"}),
            ] {
                let failure = source_annotation_transport(annotation,streaming).expect_err("non-web annotation cannot verify search");
                assert!(matches!(failure, Failure::Explicit{code:"WEB_SEARCH_NO_SOURCES",..}|Failure::MeasuredExplicit{code:"WEB_SEARCH_NO_SOURCES",..}));
                let measured = failure.quantitative_completed().expect("received token counts survive source rejection");
                assert_eq!(measured.usage.input_tokens,Some(3));
                assert_eq!(measured.usage.output_tokens,Some(2));
                assert!(measured.text.is_empty());
            }
        }
    }

    #[test]
    fn source_annotation_typed_and_legacy_citations_remain_compatible() {
        for streaming in [false,true] {
            for annotation in [
                json!({"type":"url_citation","url_citation":{"url":"https://example.com/valid","title":"Official"}}),
                json!({"type":"url_citation","url":"https://example.com/valid","title":"Official"}),
                json!({"url_citation":{"url":"https://example.com/valid","title":"Official"}}),
                json!({"url":"https://example.com/valid","title":"Official"}),
            ] {
                let completed = source_annotation_transport(annotation,streaming).unwrap();
                assert_eq!(completed.native_sources,vec![("Official".into(),"https://example.com/valid".into())]);
                assert!(completed.web_search_verified);
            }
        }
    }

    #[test]
    fn source_titles_cannot_create_extra_markdown_links() {
        let mut answer = String::from("grounded answer");
        append_sources(&mut answer, &[(
            "Official](https://attacker.example)[Fake\n<script>".into(),
            "https://example.com/source(a)".into(),
        )]);
        assert_eq!(answer, "grounded answer\n\n来源：\n- [Official\\](https://attacker.example)\\[Fake \\<script\\>](https://example.com/source%28a%29)");

        let sources = response_sources(&json!({"choices":[{"delta":{"annotations":[
            {"url_citation":{"title":"Official","url":"https://example.com/a\nb"}}
        ]}}]}));
        assert_eq!(sources, vec![("Official".into(), "https://example.com/ab".into())]);
    }

    #[test]
    fn chat_completions_providers_accept_android_parity_eof_after_answer() {
        let body = b"HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\ndata: {\"choices\":[{\"delta\":{\"content\":\"grounded answer\",\"annotations\":[{\"url_citation\":{\"title\":\"Official\",\"url\":\"https://example.com/source\"}}]}}]}\n\n";
        let mut request = request(mock_server(body));
        request.web_search_route = "OPENROUTER_SERVER_TOOL".into();
        let completed = tokio::runtime::Runtime::new()
            .unwrap()
            .block_on(execute_streaming(
                request,
                Zeroizing::new(b"fixture-secret-123".to_vec()),
                Arc::new(AtomicBool::new(false)),
                |_| Ok(()),
            ))
            .unwrap();
        assert_eq!(
            completed.text,
            "grounded answer\n\n来源：\n- [Official](https://example.com/source)"
        );
    }

    #[test]
    fn local_mock_stream_normalizes_deltas_usage_cost_and_completion() {
        let body = b"HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\ndata: {\"model\":\"fixture-actual\",\"choices\":[{\"delta\":{\"content\":\"hello \"}}]}\n\ndata: {\"choices\":[{\"delta\":{\"content\":\"world\",\"reasoning_content\":\"private\"}}],\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":2,\"cost\":0.000004}}\n\ndata: [DONE]\n\n";
        let endpoint = mock_server(body);
        let mut deltas = Vec::new();
        let completed = tokio::runtime::Runtime::new()
            .unwrap()
            .block_on(execute_streaming(
                request(endpoint),
                Zeroizing::new(b"fixture-secret-123".to_vec()),
                Arc::new(AtomicBool::new(false)),
                |delta| {
                    deltas.push(delta.to_owned());
                    Ok(())
                },
            ))
            .unwrap();
        assert_eq!(deltas, ["hello ", "world"]);
        assert_eq!(completed.text, "hello world");
        assert_eq!(completed.reasoning.as_deref(), Some("private"));
        assert_eq!(completed.usage.input_tokens, Some(3));
        assert_eq!(completed.reported_cost_micros, Some(4));
        assert_eq!(completed.actual_model_id.as_deref(), Some("fixture-actual"));
    }

    #[test]
    fn openai_terminal_finish_reason_completes_when_done_frame_is_absent() {
        let body = b"HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\ndata: {\"model\":\"anthropic/claude-sonnet-5\",\"choices\":[{\"delta\":{\"content\":\"completed before socket close\"},\"finish_reason\":\"stop\"}]}\n\n";
        let completed = tokio::runtime::Runtime::new()
            .unwrap()
            .block_on(execute_streaming(
                request(mock_server(body)),
                Zeroizing::new(b"fixture-secret-123".to_vec()),
                Arc::new(AtomicBool::new(false)),
                |_| Ok(()),
            ))
            .expect("an explicit OpenAI finish_reason is a completed provider result");
        assert_eq!(completed.text, "completed before socket close");
        assert_eq!(
            completed.actual_model_id.as_deref(),
            Some("anthropic/claude-sonnet-5")
        );
    }

    #[test]
    fn chat_completions_eof_after_text_completes_without_finish_reason() {
        let body = b"HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\ndata: {\"choices\":[{\"delta\":{\"content\":\"completed at clean EOF\"},\"finish_reason\":null}]}\n\n";
        let completed = tokio::runtime::Runtime::new()
            .unwrap()
            .block_on(execute_streaming(
                request(mock_server(body)),
                Zeroizing::new(b"fixture-secret-123".to_vec()),
                Arc::new(AtomicBool::new(false)),
                |_| Ok(()),
            ))
            .expect("Android-parity Chat Completions EOF must complete visible text");
        assert_eq!(completed.text, "completed at clean EOF");
    }

    #[test]
    fn cancellation_after_first_delta_never_accepts_late_content() {
        let body = b"HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\ndata: {\"choices\":[{\"delta\":{\"content\":\"first\"}}]}\n\ndata: {\"choices\":[{\"delta\":{\"content\":\"late\"}}]}\n\ndata: [DONE]\n\n";
        let endpoint = mock_server(body);
        let cancelled = Arc::new(AtomicBool::new(false));
        let signal = cancelled.clone();
        let result = tokio::runtime::Runtime::new()
            .unwrap()
            .block_on(execute_streaming(
                request(endpoint),
                Zeroizing::new(b"fixture-secret-123".to_vec()),
                cancelled,
                move |_| {
                    signal.store(true, Ordering::SeqCst);
                    Ok(())
                },
            ));
        assert_eq!(result, Err(Failure::Cancelled));
    }

    #[test]
    fn cancellation_interrupts_a_stalled_stream_without_waiting_for_another_delta() {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let address = listener.local_addr().unwrap();
        thread::spawn(move || {
            let (mut socket, _) = listener.accept().unwrap();
            let mut request = [0u8; 8192];
            let _ = socket.read(&mut request).unwrap();
            socket.write_all(b"HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\n").unwrap();
            thread::sleep(Duration::from_secs(3));
        });
        let cancelled = Arc::new(AtomicBool::new(false));
        let signal = cancelled.clone();
        thread::spawn(move || {
            thread::sleep(Duration::from_millis(150));
            signal.store(true, Ordering::SeqCst);
        });
        let started = Instant::now();
        let result = tokio::runtime::Runtime::new()
            .unwrap()
            .block_on(execute_streaming(
                request(format!("http://{address}/chat/completions")),
                Zeroizing::new(b"fixture-secret-123".to_vec()),
                cancelled,
                |_| Ok(()),
            ));
        assert_eq!(result, Err(Failure::Cancelled));
        assert!(started.elapsed() < Duration::from_secs(1));
    }

    #[test]
    fn non_streaming_decoder_keeps_reasoning_usage_and_reported_cost_separate() {
        let result = decode_non_streaming(br#"{"model":"fixture","choices":[{"message":{"content":"answer","reasoning_content":"thought"}}],"usage":{"prompt_tokens":5,"completion_tokens":7,"completion_tokens_details":{"reasoning_tokens":2},"cost":0.000009}}"#, 12).unwrap();
        assert_eq!(result.text, "answer");
        assert_eq!(result.reasoning.as_deref(), Some("thought"));
        assert_eq!(result.usage.reasoning_tokens, Some(2));
        assert_eq!(result.reported_cost_micros, Some(9));
    }
}


#[cfg(test)]
mod unified_web_protocol_tests {
    use super::*;
    use std::{io::{Read, Write}, net::TcpListener, thread};
    fn request(provider: &str, model: &str, route: &str) -> TransportRequest {
        TransportRequest { endpoint: "http://127.0.0.1/unused".into(), provider_id:provider.into(), model_id:model.into(),
            messages:json!([{"role":"user","content":"A historical public question, no trigger keywords"}]), idempotency_key:"unified-protocol-synthetic".into(),
            max_output_tokens:128, web_search_route:route.into(), structured_json:false, disable_thinking:false, grounded_answer:false, explicit_search_query:None }
    }
    async fn execute(mut request: TransportRequest, response: Value) -> (Result<Completed, Failure>, Value) {
        let listener=TcpListener::bind("127.0.0.1:0").unwrap();
        request.endpoint=format!("http://{}/chat/completions",listener.local_addr().unwrap());
        let bytes=serde_json::to_vec(&response).unwrap();
        let server=thread::spawn(move||{
            let (mut stream,_)=listener.accept().unwrap();stream.set_read_timeout(Some(Duration::from_secs(5))).unwrap();
            let mut input=Vec::new();let (header_end,body_len)=loop{
                let mut chunk=[0;4096];let count=stream.read(&mut chunk).unwrap();assert!(count>0);input.extend_from_slice(&chunk[..count]);assert!(input.len()<128*1024);
                if let Some(end)=input.windows(4).position(|s|s==b"\r\n\r\n"){
                    let header=String::from_utf8_lossy(&input[..end]).to_lowercase();
                    let len=header.lines().find_map(|line|line.strip_prefix("content-length:").map(|v|v.trim().parse::<usize>().unwrap())).unwrap();
                    if input.len()>=end+4+len {break (end+4,len);}
                }
            };
            let received=serde_json::from_slice(&input[header_end..header_end+body_len]).unwrap();
            write!(stream,"HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n",bytes.len()).unwrap();stream.write_all(&bytes).unwrap();received
        });
        let reply=execute_non_streaming(request,Zeroizing::new(b"synthetic-only".to_vec())).await;
        (reply,server.join().unwrap())
    }
    fn response(model:&str,protocol:&str,proof:bool)->Value {
        let source=json!({"type":"url_citation","url":"https://example.test/unified-proof","title":"Synthetic proof"});
        match protocol {
            "responses"=>json!({"object":"response","status":"completed","model":model,"output":[{"type":"web_search_call","status":"completed"},{"type":"message","content":[{"type":"output_text","text":"Synthetic answer","annotations":if proof{json!([source])}else{json!([])}}]}],"usage":{"input_tokens":100,"output_tokens":20,"input_tokens_details":{"cached_tokens":10},"cost":0.0003}}),
            "messages"=>json!({"model":model,"content":if proof{json!([{"type":"web_search_tool_result","content":[{"type":"web_search_result","url":"https://example.test/unified-proof","title":"Synthetic proof"}]},{"type":"text","text":"Synthetic answer"}])}else{json!([{"type":"text","text":"Synthetic answer"}])},"stop_reason":"end_turn","usage":{"input_tokens":100,"output_tokens":20,"cache_read_input_tokens":10,"cost":0.0003}}),
            _=>json!({"model":model,"choices":[{"message":{"content":"Synthetic answer","annotations":if proof{json!([{"type":"url_citation","url_citation":{"url":"https://example.test/unified-proof","title":"Synthetic proof"}}])}else{json!([])}},"finish_reason":"stop"}],"usage":{"prompt_tokens":100,"completion_tokens":20,"cost":0.0003}})
        }
    }
    #[test]
    fn unified_haiku_uses_exa_plugin_without_server_tools_and_keeps_off_grounded_boundaries(){
        let mut r=request("OPENROUTER","anthropic/claude-haiku-5.5","OPENROUTER_SERVER_TOOL");
        let on=transport_body(&r);assert_eq!(on["plugins"][0]["engine"],"exa");assert!(on.get("tools").is_none());assert_eq!(on["model"],r.model_id);
        r.grounded_answer=true;let final_body=transport_body(&r);assert!(final_body.get("plugins").is_none());assert!(final_body.get("tools").is_none());
        r.grounded_answer=false;r.web_search_route="NONE".into();assert!(transport_body(&r).get("plugins").is_none());
    }
    #[test]
    fn unified_openrouter_and_muse_share_one_public_search_instruction_and_final_has_no_tools(){
        for model in ["openai/gpt-6.1-sol","anthropic/claude-opus-5.5","google/gemini-3.8-flash","google/gemini-4-argon","meta/muse-wonder"]{
            let mut r=request("OPENROUTER",model,"OPENROUTER_SERVER_TOOL");let on=transport_body(&r);
            assert_eq!(on.to_string().matches("以联网开关为准").count(),1,"{model}");assert!(on.to_string().contains("不搜索私人信息"));
            r.grounded_answer=true;let final_body=transport_body(&r);assert!(final_body.get("tools").is_none(),"{model}");assert!(final_body.get("plugins").is_none(),"{model}");
        }
    }
    #[tokio::test]
    async fn muse_responses_pdf_input_preserves_original_files_order_and_images_over_http() {
        use base64::Engine;
        let pdf=base64::engine::general_purpose::STANDARD.encode(include_bytes!("../../../protocol/fixtures/attachment-readable-media-v1.pdf"));
        let scan=base64::engine::general_purpose::STANDARD.encode(include_bytes!("../../tests/fixtures/scanned-material-v1.pdf"));
        let first=format!("data:application/pdf;base64,{pdf}");let second=format!("data:application/pdf;base64,{scan}");
        for grounded in [false,true] {
            let mut r=request("OPENROUTER","meta/muse-wonder","OPENROUTER_SERVER_TOOL");r.grounded_answer=grounded;
            r.messages=json!([{"role":"system","content":"synthetic trusted policy"},{"role":"user","content":[
                {"type":"text","text":"原问题"},
                {"type":"file","file":{"filename":"合成原件.pdf","file_data":first}},
                {"type":"image_url","image_url":{"url":"data:image/png;base64,AQID"}},
                {"type":"file","file":{"filename":"synthetic-scan.pdf","file_data":second}},
                {"type":"text","text":"末尾独立材料"}
            ]}]);
            let (reply,body)=execute(r,response("meta/muse-wonder","responses",true)).await;
            assert!(reply.is_ok(),"{reply:?}");
            let user=body["input"].as_array().unwrap().iter().find(|m|m["role"]=="user").unwrap();
            assert_eq!(user["content"],json!([
                {"type":"input_text","text":"原问题"},
                {"type":"input_file","filename":"合成原件.pdf","file_data":first},
                {"type":"input_image","image_url":"data:image/png;base64,AQID"},
                {"type":"input_file","filename":"synthetic-scan.pdf","file_data":second},
                {"type":"input_text","text":"末尾独立材料"}
            ]),"Responses conversion must not silently omit accepted original files");
            assert_eq!(body["store"],false);assert_eq!(body["model"],"meta/muse-wonder");
            assert_eq!(body.get("tools").is_some(),!grounded);
        }
    }

    #[tokio::test]
    async fn qwen_responses_alignment_max_http_preserves_reasoning_budget_and_sources() {
        let mut r=request("QWEN","qwen3.8-max","QWEN_RESPONSES");
        r.max_output_tokens=ordinary_output_token_limit("QWEN","qwen3.8-max");
        let (reply,body)=execute(r,response("qwen3.8-max","responses",true)).await;
        let reply=reply.unwrap();
        assert_eq!(body["enable_thinking"],true);
        assert_eq!(body["reasoning"]["effort"],"low");
        assert_eq!(body["max_output_tokens"],16384);
        assert_eq!(body["stream"],false);
        assert_eq!(body["store"],false);
        assert!(body.get("max_tokens").is_none() && body.get("max_completion_tokens").is_none());
        assert_eq!(reply.actual_model_id.as_deref(),Some("qwen3.8-max"));
        assert_eq!(reply.usage.input_tokens,Some(100));
        assert_eq!(reply.usage.cached_input_tokens,Some(10));
        assert!(reply.web_search_verified && reply.native_sources.len()==1);
    }

    #[tokio::test]
    async fn unified_qwen_responses_decode_text_sources_usage_and_actual_identity(){
        for model in ["qwen3.7-plus","qwen3.8-max","qwen3.6-flash"]{
            let (reply,body)=execute(request("QWEN",model,"QWEN_RESPONSES"),response(model,"responses",true)).await;
            let reply=reply.unwrap();assert!(reply.text.contains("Synthetic answer"));assert_eq!(reply.actual_model_id.as_deref(),Some(model));assert_eq!(reply.usage.input_tokens,Some(100));assert_eq!(reply.usage.cached_input_tokens,Some(10));assert!(reply.web_search_verified);assert_eq!(reply.native_sources.len(),1);assert_eq!(body["stream"],false);assert!(body.get("input").is_some());
        }
    }
    #[tokio::test]
    async fn unified_messages_decode_deepseek_and_relay_without_unqualified_relay_money(){
        for (provider,model,route) in [("DEEPSEEK","deepseek-flash","DEEPSEEK_MESSAGES"),("RELAYCORE_CLAUDE","claude-opus-5.5","RELAYCORE_CLAUDE_MESSAGES"),("RELAYCORE_CLAUDE","claude-sonnet-5-5","NONE")]{
            let (reply,_) = execute(request(provider,model,route),response(model,"messages",route!="NONE")).await;
            let reply=reply.unwrap();assert_eq!(reply.usage.input_tokens,Some(110));assert_eq!(reply.usage.cached_input_tokens,Some(10));assert_eq!(reply.web_search_verified,route!="NONE");
            if provider.starts_with("RELAYCORE_"){assert_eq!(reply.reported_cost_micros,None);}
        }
    }
    #[tokio::test]
    async fn unified_missing_sources_keep_only_quantitative_facts(){
        for (provider,model,route,protocol) in [("OPENROUTER","openai/gpt-6.1-sol","OPENROUTER_SERVER_TOOL","chat"),("QWEN","qwen3.7-plus","QWEN_RESPONSES","responses"),("DEEPSEEK","deepseek-flash","DEEPSEEK_MESSAGES","messages")]{
            let (reply,_) = execute(request(provider,model,route),response(model,protocol,false)).await;
            let failure=reply.unwrap_err();assert!(matches!(&failure,Failure::MeasuredExplicit{code:"WEB_SEARCH_NO_SOURCES",..}),"{failure:?}");let facts=failure.quantitative_completed().unwrap();assert_eq!(facts.text,"");assert!(facts.native_sources.is_empty());assert_eq!(facts.usage.input_tokens,Some(if protocol=="messages" {110}else{100}));assert_eq!(facts.reported_cost_micros,Some(300));
        }
    }
    #[tokio::test]
    async fn unified_grounded_final_does_not_require_a_second_search(){
        for (provider,model,route,protocol) in [("QWEN","qwen3.7-plus","QWEN_RESPONSES","responses"),("DEEPSEEK","deepseek-flash","DEEPSEEK_MESSAGES","messages"),("OPENROUTER","meta/muse-wonder","OPENROUTER_SERVER_TOOL","responses")]{
            let mut r=request(provider,model,route);r.grounded_answer=true;let (reply,body)=execute(r,response(model,protocol,false)).await;
            let reply=reply.unwrap();assert_eq!(reply.text,"Synthetic answer");assert!(!reply.web_search_verified);assert!(body.get("tools").is_none());assert!(body.get("plugins").is_none());
        }
    }
    #[tokio::test]
    async fn unified_incomplete_responses_never_promote_visible_text_to_success(){
        let mut value=response("qwen3.7-plus","responses",true);value["status"]=json!("incomplete");value["incomplete_details"]=json!({"reason":"max_output_tokens"});
        let (reply,_)=execute(request("QWEN","qwen3.7-plus","QWEN_RESPONSES"),value).await;
        let failure=reply.unwrap_err();assert!(matches!(&failure,Failure::MeasuredExplicit{code:"OUTPUT_LIMIT",..}));assert_eq!(failure.quantitative_completed().unwrap().usage.output_tokens,Some(20));
    }

    #[test]
    fn unified_trusted_instruction_is_not_duplicated_by_transport() {
        for model in ["anthropic/claude-haiku-5.5", "meta/muse-wonder", "google/gemini-4-argon"] {
            let mut r=request("OPENROUTER",model,"OPENROUTER_SERVER_TOOL");
            r.messages=json!([{"role":"system","content":LIVE_SEARCH_INSTRUCTION},{"role":"user","content":"Synthetic public question"}]);
            assert_eq!(transport_body(&r).to_string().matches("以联网开关为准").count(),1);
        }
    }
    #[tokio::test]
    async fn unified_relay_responses_requires_completed_tool_and_discards_unqualified_cost() {
        let req=request("RELAYCORE_GPT","gpt-6.1-sol","RELAYCORE_GPT_RESPONSES");
        let (reply,_)=execute(req.clone(),response("gpt-6.1-sol","responses",true)).await;
        let reply=reply.unwrap();assert!(reply.web_search_verified);assert_eq!(reply.reported_cost_micros,None);
        for status in ["in_progress","failed"] {
            let mut value=response("gpt-6.1-sol","responses",true);value["output"][0]["status"]=json!(status);
            let (reply,_)=execute(req.clone(),value).await;
            let failure=reply.unwrap_err();assert!(matches!(&failure,Failure::MeasuredExplicit{code:"WEB_SEARCH_NO_SOURCES",..}));
            let facts=failure.quantitative_completed().unwrap();assert!(facts.text.is_empty());assert_eq!(facts.usage.output_tokens,Some(20));assert_eq!(facts.reported_cost_micros,None);
        }
    }
    #[tokio::test]
    async fn unified_tool_arguments_and_unsafe_annotations_are_not_sources() {
        for annotation in [json!({"type":"tool_call","url":"https://example.test/fabricated"}),json!({"type":"url_citation","url":"javascript:alert(1)"}),json!({"type":"url_citation","url":"https://user:password@example.test/private"})] {
            let mut value=response("qwen3.7-plus","responses",false);
            value["output"][0]["arguments"]=json!({"url":"https://example.test/arguments-only"});
            value["output"][1]["content"][0]["annotations"]=json!([annotation]);
            let (reply,_)=execute(request("QWEN","qwen3.7-plus","QWEN_RESPONSES"),value).await;
            assert!(matches!(reply,Err(Failure::MeasuredExplicit{code:"WEB_SEARCH_NO_SOURCES",..})));
        }
    }
}



#[cfg(test)]
mod failure_model_identity_tests {
    use super::*;
    use std::{io::{Read,Write},net::TcpListener,thread};
    #[tokio::test]
    async fn identity_only_stream_failure_keeps_known_model_without_inventing_quantities() {
        for (provider,route,model,wire) in [
            ("OPENROUTER","NONE","openai/gpt-6.1-sol","data: {\"model\":\"openai/gpt-6.1-sol\",\"choices\":[{\"delta\":{}}]}\n\n"),
            ("RELAYCORE_CLAUDE","NONE","claude-opus-5.5","data: {\"type\":\"message_start\",\"message\":{\"model\":\"claude-opus-5.5\"}}\n\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"partial body\"}}\n\n"),
            ("QWEN","QWEN_RESPONSES","qwen3.7-plus","data: {\"type\":\"response.completed\",\"response\":{\"model\":\"qwen3.7-plus\",\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"partial body without required sources\"}]}]}}\n\n"),
        ] {
            let listener=TcpListener::bind("127.0.0.1:0").unwrap();let address=listener.local_addr().unwrap();
            let server=thread::spawn(move || {let(mut socket,_)=listener.accept().unwrap();socket.set_read_timeout(Some(Duration::from_secs(5))).unwrap();let mut buffer=[0;8192];socket.read(&mut buffer).unwrap();write!(socket,"HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\n{wire}").unwrap();});
            let request=TransportRequest {endpoint:format!("http://{address}/chat/completions"),provider_id:provider.into(),model_id:model.into(),messages:json!([{"role":"user","content":"synthetic identity-only failure"}]),idempotency_key:"identity-only-fixture".into(),max_output_tokens:128,web_search_route:route.into(),structured_json:false,disable_thinking:false,grounded_answer:false,explicit_search_query:None};
            let failure=execute_streaming(request,Zeroizing::new(b"synthetic-only".to_vec()),Arc::new(AtomicBool::new(false)),|_|Ok(())).await.unwrap_err();server.join().unwrap();
            let facts=failure.quantitative_completed().expect("A known returned model must survive even when all quantities are unknown");
            assert_eq!(facts.actual_model_id.as_deref(),Some(model));assert_eq!(facts.usage,Usage::default());assert_eq!(facts.reported_cost_micros,None);
            assert!(facts.text.is_empty()&&facts.reasoning.is_none()&&facts.native_sources.is_empty()&&!facts.web_search_verified);
            assert!(matches!(failure,Failure::MeasuredUnknown{code:"MISSING_COMPLETION",..}|Failure::MeasuredExplicit{code:"WEB_SEARCH_NO_SOURCES",..}));
        }
    }
    #[test]
    fn identity_only_nonstream_failure_distinguishes_reported_from_missing_or_invalid_model() {
        for model in [Some("openai/gpt-6.1-sol"),None,Some(""),Some("null"),Some("bad\nmodel")] {
            let mut value=json!({"choices":[{"finish_reason":"length","message":{"content":"partial"}}]});
            if let Some(model)=model {value["model"]=json!(model);}
            let failure=decode_non_streaming(&serde_json::to_vec(&value).unwrap(),7).unwrap_err();
            if model==Some("openai/gpt-6.1-sol") {
                let facts=failure.quantitative_completed().expect("Reported identity alone is a durable fact");assert_eq!(facts.actual_model_id.as_deref(),model);assert_eq!(facts.usage,Usage::default());assert_eq!(facts.reported_cost_micros,None);assert!(facts.text.is_empty());
            } else {assert!(matches!(failure,Failure::Explicit{code:"OUTPUT_LIMIT",..}),"Missing or invalid model must not manufacture received facts");}
        }
    }
}


#[cfg(test)]
mod live_call_checkpoint_tests {
    use super::*;
    use std::{io::{BufRead,BufReader,Read,Write},net::TcpListener,thread};
    fn server(body:String,hold:bool)->(String,thread::JoinHandle<()>) {
        let listener=TcpListener::bind("127.0.0.1:0").unwrap();let address=listener.local_addr().unwrap();
        let task=thread::spawn(move||{let(socket,_)=listener.accept().unwrap();socket.set_read_timeout(Some(Duration::from_secs(5))).unwrap();let mut reader=BufReader::new(socket);let mut length=0;
            loop {let mut line=String::new();reader.read_line(&mut line).unwrap();if line=="\r\n" {break;}if let Some((key,value))=line.split_once(':'){if key.eq_ignore_ascii_case("content-length"){length=value.trim().parse::<usize>().unwrap();}}}
            assert!(length<65536);reader.read_exact(&mut vec![0;length]).unwrap();let mut socket=reader.into_inner();write!(socket,"HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\n{body}").unwrap();socket.flush().unwrap();
            if hold {let mut byte=[0];assert_eq!(socket.read(&mut byte).unwrap(),0,"Dropping the observed future must close its owned HTTP stream");}
        });(format!("http://{address}/chat/completions"),task)
    }
    fn request(endpoint:String,provider:&str,model:&str,route:&str)->TransportRequest {
        TransportRequest{endpoint,provider_id:provider.into(),model_id:model.into(),messages:json!([{"role":"user","content":"synthetic live measurement"}]),idempotency_key:"live-progress-fixture".into(),max_output_tokens:128,web_search_route:route.into(),structured_json:false,disable_thinking:false,grounded_answer:true,explicit_search_query:None}
    }
    #[tokio::test]
    async fn live_checkpoint_is_reported_before_text_for_chat_messages_and_responses() {
        for (provider,model,route,body,cost) in [
            ("OPENROUTER","openai/gpt-6.1-sol","NONE",concat!("data: {\"model\":\"openai/gpt-6.1-sol\",\"usage\":{\"prompt_tokens\":17,\"completion_tokens\":0,\"prompt_tokens_details\":{\"cached_tokens\":3},\"cost\":0}}\n\n","data: {\"choices\":[{\"delta\":{\"content\":\"SYNTHETIC_BODY\"}}]}\n\n","data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n"),Some(0)),
            ("RELAYCORE_CLAUDE","claude-opus-5.5","NONE",concat!("data: {\"type\":\"message_start\",\"message\":{\"model\":\"claude-opus-5.5\",\"usage\":{\"input_tokens\":14,\"cache_read_input_tokens\":3,\"output_tokens\":0}}}\n\n","data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"SYNTHETIC_BODY\"}}\n\n","data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":2}}\n\ndata: {\"type\":\"message_stop\"}\n\n"),None),
            ("QWEN","qwen3.7-plus","QWEN_RESPONSES",concat!("data: {\"type\":\"response.created\",\"response\":{\"model\":\"qwen3.7-plus\",\"usage\":{\"input_tokens\":17,\"output_tokens\":0,\"input_tokens_details\":{\"cached_tokens\":3},\"cost\":0}}}\n\n","data: {\"type\":\"response.output_text.delta\",\"delta\":\"SYNTHETIC_BODY\"}\n\n","data: {\"type\":\"response.completed\",\"response\":{\"status\":\"completed\",\"model\":\"qwen3.7-plus\",\"usage\":{\"input_tokens\":17,\"output_tokens\":2,\"input_tokens_details\":{\"cached_tokens\":3},\"cost\":0}}}\n\n"),Some(0)),
        ] {
            let(endpoint,server)=server(body.into(),false);let observations=std::sync::Mutex::new(Vec::<ReportedMeasurement>::new());
            let reply=execute_streaming_observed(request(endpoint,provider,model,route),Zeroizing::new(b"synthetic-only".to_vec()),Arc::new(AtomicBool::new(false)),
                |_| {let rows=observations.lock().unwrap();assert!(!rows.is_empty(),"Metadata must reach its checkpoint owner before visible text");assert_eq!(rows[0].usage.input_tokens,Some(17));assert_eq!(rows[0].usage.output_tokens,Some(0));assert_eq!(rows[0].usage.cached_input_tokens,Some(3));assert_eq!(rows[0].reported_cost_micros,cost);assert_eq!(rows[0].actual_model_id.as_deref(),Some(model));Ok(())},
                |value|{observations.lock().unwrap().push(value.clone());Ok(())}).await.unwrap();server.join().unwrap();assert_eq!(reply.text,"SYNTHETIC_BODY");assert!(!format!("{:?}",observations.lock().unwrap()).contains("SYNTHETIC_BODY"));
        }
    }
    #[tokio::test]
    async fn live_checkpoint_survives_outer_future_drop_without_terminal_callback() {
        let(endpoint,server)=server("data: {\"model\":\"openai/gpt-6.1-sol\",\"usage\":{\"prompt_tokens\":17,\"completion_tokens\":0,\"cost\":0.000004}}\n\n".into(),true);
        let observed=std::sync::Mutex::new(Vec::<ReportedMeasurement>::new());let(tx,rx)=tokio::sync::oneshot::channel();let mut tx=Some(tx);
        {let work=execute_streaming_observed(request(endpoint,"OPENROUTER","openai/gpt-6.1-sol","NONE"),Zeroizing::new(b"synthetic-only".to_vec()),Arc::new(AtomicBool::new(false)),|_|Ok(()),|value|{observed.lock().unwrap().push(value.clone());if let Some(tx)=tx.take(){let _=tx.send(());}Ok(())});tokio::pin!(work);
         tokio::time::timeout(Duration::from_secs(3),async {tokio::select!{_ = rx=>{},result=&mut work=>panic!("No upstream terminal was sent: {result:?}")}}).await.expect("Received metadata must be observable before an outer owner abandons the future");}
        tokio::task::spawn_blocking(move||server.join().unwrap()).await.unwrap();let rows=observed.lock().unwrap();assert_eq!(rows.len(),1);assert_eq!(rows[0].usage.input_tokens,Some(17));assert_eq!(rows[0].usage.output_tokens,Some(0));assert_eq!(rows[0].reported_cost_micros,Some(4));
    }
}
