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
    pub elapsed_ms: i64,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Failure {
    Cancelled,
    Explicit {
        code: &'static str,
        http_status: Option<u16>,
    },
    Unknown {
        code: &'static str,
    },
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

fn parse_usage(value: Option<&Value>) -> Usage {
    let details = value.and_then(|usage| usage.get("prompt_tokens_details"));
    let completion_details = value.and_then(|usage| usage.get("completion_tokens_details"));
    Usage {
        input_tokens: value
            .and_then(|usage| usage.get("prompt_tokens"))
            .and_then(Value::as_i64),
        output_tokens: value
            .and_then(|usage| usage.get("completion_tokens"))
            .and_then(Value::as_i64),
        cached_input_tokens: details
            .and_then(|details| details.get("cached_tokens"))
            .and_then(Value::as_i64),
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
                _ => None,
            }).collect()
        };
        Some(json!({"role":role,"content":parts}))
    }).collect())
}

pub fn provider_endpoint(base: &str, route: &str) -> String {
    let base = base.trim_end_matches('/');
    match route {
        "DEEPSEEK_MESSAGES" => format!("{}/anthropic/v1/messages", base.strip_suffix("/v1").unwrap_or(base)),
        "QWEN_RESPONSES" => format!("{base}/responses"),
        _ => format!("{base}/chat/completions"),
    }
}

fn transport_body(request: &TransportRequest) -> Value {
    let mut body = match request.web_search_route.as_str() {
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
                "OPENROUTER_SERVER_TOOL" => {
                    object.insert("tools".into(), json!([{"type":"openrouter:web_search","parameters":{"max_results":5,"max_total_results":10}}]));
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
            body
        }
    };
    if request.provider_id == "QWEN" && request.model_id == "qwen3.8-max" {
        if request.web_search_route == "QWEN_RESPONSES" {
            body["reasoning"] = json!({"effort":"low"});
        } else {
            body.as_object_mut().unwrap().remove("max_tokens");
            body["max_completion_tokens"] = json!(request.max_output_tokens.min(16_384));
            body["reasoning_effort"] = json!("low");
            body["preserve_thinking"] = json!(false);
        }
    }
    body
}

pub fn ordinary_output_token_limit(provider: &str, model: &str) -> u32 {
    if provider == "QWEN" && model == "qwen3.8-max" {
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
    let response = Client::builder()
        .connect_timeout(Duration::from_secs(15))
        .timeout(Duration::from_secs(if request.provider_id == "DEEPSEEK" {
            90
        } else {
            180
        }))
        .build()
        .map_err(|_| Failure::Unknown { code: "CLIENT" })?
        .post(&request.endpoint)
        .bearer_auth(authorization.as_str())
        .header("Idempotency-Key", &request.idempotency_key)
        .header("Accept", "application/json")
        .json(&body)
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
    let bytes = response.bytes().await.map_err(|error| {
        if error.is_timeout() {
            Failure::Unknown { code: "TIMEOUT" }
        } else {
            Failure::Unknown { code: "NETWORK" }
        }
    })?;
    decode_non_streaming(
        &bytes,
        started.elapsed().as_millis().min(i64::MAX as u128) as i64,
    )
}

fn response_sources(value: &Value) -> Vec<(String, String)> {
    fn source(value: &Value) -> Option<(String, String)> {
        let url = value
            .get("url")
            .or_else(|| value.get("link"))?
            .as_str()?
            .trim();
        let parsed = Url::parse(url).ok()?;
        if !matches!(parsed.scheme(), "http" | "https")
            || parsed.host_str().is_none()
            || !parsed.username().is_empty()
            || parsed.password().is_some()
        {
            return None;
        }
        let title = value
            .get("title")
            .or_else(|| value.get("name"))
            .and_then(Value::as_str)
            .unwrap_or(url)
            .trim();
        Some((title.to_owned(), url.to_owned()))
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
        .filter(|item| item.get("type").and_then(Value::as_str) == Some("web_search_call"))
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

fn has_web_search_evidence(value: &Value) -> bool {
    !response_sources(value).is_empty()
        || value
            .get("output")
            .and_then(Value::as_array)
            .into_iter()
            .flatten()
            .any(|item| item.get("type").and_then(Value::as_str) == Some("web_search_call") && matches!(item.get("status").and_then(Value::as_str), None | Some("completed")))
}

fn append_sources(text: &mut String, sources: &[(String, String)]) {
    if sources.is_empty() {
        return;
    }
    text.push_str("\n\n来源：");
    for (title, url) in sources {
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
    let web_search_verified = !sources.is_empty() || has_web_search_evidence(&value);
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
            .map(ToOwned::to_owned),
        web_search_verified,
        elapsed_ms,
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
    if value
        .pointer("/choices/0/finish_reason")
        .and_then(Value::as_str)
        == Some("length")
    {
        return Err(Failure::Explicit {
            code: "OUTPUT_LIMIT",
            http_status: None,
        });
    }
    let text = value
        .get("choices")
        .and_then(|choices| choices.get(0))
        .and_then(|choice| choice.get("message"))
        .and_then(|message| message.get("content"))
        .and_then(message_content_text)
        .ok_or(Failure::Explicit {
            code: "RESPONSE_FORMAT",
            http_status: None,
        })?;
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
        usage: parse_usage(value.get("usage")),
        reported_cost_micros: cost_micros(value.get("usage").and_then(|usage| usage.get("cost"))),
        actual_model_id: value
            .get("model")
            .and_then(Value::as_str)
            .map(ToOwned::to_owned),
        web_search_verified: has_web_search_evidence(&value),
        elapsed_ms,
    })
}

fn decode_deepseek_messages(bytes: &[u8], elapsed_ms: i64) -> Result<Completed, Failure> {
    let fail = |code| Failure::Explicit { code, http_status: None };
    if bytes.len() > MAX_RESPONSE_BYTES { return Err(fail("RESPONSE_SIZE")); }
    let value: Value = serde_json::from_slice(bytes).map_err(|_| fail("RESPONSE_FORMAT"))?;
    let blocks = value["content"].as_array().ok_or_else(|| fail("RESPONSE_FORMAT"))?;
    let results: Vec<_> = blocks.iter().filter(|b| b["type"] == "web_search_tool_result").collect();
    if results.is_empty() || results.iter().any(|b| !b["content"].is_array()) {
        return Err(fail("WEB_SEARCH_NO_SOURCES"));
    }
    if value["stop_reason"] != "end_turn" { return Err(fail("RESPONSE_INCOMPLETE")); }
    let last = blocks.iter().rposition(|b| b["type"] == "web_search_tool_result").unwrap();
    let mut text = blocks.iter().skip(last + 1).filter(|b| b["type"] == "text").filter_map(|b| b["text"].as_str()).collect::<Vec<_>>().join("\n");
    if text.trim().is_empty() { return Err(fail("RESPONSE_FORMAT")); }
    let mut sources = Vec::new();
    for result in results {
        for item in result["content"].as_array().unwrap() {
            if item["type"] == "web_search_result" {
                if let Some(url) = item["url"].as_str().filter(|url| reqwest::Url::parse(url).is_ok_and(|u| matches!(u.scheme(), "http" | "https") && u.host_str().is_some() && u.username().is_empty() && u.password().is_none())) {
                    if !sources.iter().any(|(_, existing)| existing == url) {
                        sources.push((item["title"].as_str().unwrap_or(url).to_owned(), url.to_owned()));
                    }
                }
            }
        }
    }
    append_sources(&mut text, &sources);
    let usage = &value["usage"];
    Ok(Completed { text, reasoning: None,
        usage: Usage { input_tokens: usage["input_tokens"].as_i64(), output_tokens: usage["output_tokens"].as_i64(), cached_input_tokens: usage["cache_read_input_tokens"].as_i64(), reasoning_tokens: None },
        reported_cost_micros: None, actual_model_id: value["model"].as_str().map(ToOwned::to_owned), web_search_verified: true, elapsed_ms })
}

pub async fn execute_streaming(
    request: TransportRequest,
    mut secret: Zeroizing<Vec<u8>>,
    cancelled: Arc<AtomicBool>,
    mut on_delta: impl FnMut(&str) -> Result<(), Failure>,
) -> Result<Completed, Failure> {
    if cancelled.load(Ordering::SeqCst) {
        return Err(Failure::Cancelled);
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
    let client = Client::builder()
        .redirect(reqwest::redirect::Policy::none())
        .connect_timeout(Duration::from_secs(15))
        .timeout(STREAM_TOTAL_TIMEOUT)
        .build()
        .map_err(|_| Failure::Unknown { code: "CLIENT" })?;
    let mut builder = client.post(&request.endpoint).bearer_auth(authorization.as_str())
        .header("Idempotency-Key", &request.idempotency_key);
    if request.web_search_route == "DEEPSEEK_MESSAGES" {
        builder = builder.header("x-api-key", authorization.as_str()).header("anthropic-version", "2023-06-01").header("Accept", "application/json");
    } else { builder = builder.header("Accept", "text/event-stream"); }
    let response = tokio::time::timeout(
        STREAM_RESPONSE_TIMEOUT,
        builder.json(&body).send(),
    )
    .await
    .map_err(|_| Failure::Unknown {
        code: "FIRST_RESPONSE_TIMEOUT",
    })?
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
    if request.web_search_route == "DEEPSEEK_MESSAGES" {
        let bytes = tokio::time::timeout(
            STREAM_TOTAL_TIMEOUT.saturating_sub(started.elapsed()),
            response.bytes(),
        )
        .await
        .map_err(|_| Failure::Unknown {
            code: "FIRST_VISIBLE_TIMEOUT",
        })?
        .map_err(|error| {
            if error.is_timeout() {
                Failure::Unknown { code: "TIMEOUT" }
            } else {
                Failure::Unknown { code: "NETWORK" }
            }
        })?;
        let completed = decode_deepseek_messages(
            &bytes, started.elapsed().as_millis().min(i64::MAX as u128) as i64,
        )?;
        return Ok(completed);
    }
    let responses_stream = request.web_search_route == "QWEN_RESPONSES";
    let mut text = String::new();
    let mut reasoning = String::new();
    let mut sources = Vec::<(String, String)>::new();
    let mut usage = Usage::default();
    let mut reported_cost_micros = None;
    let mut actual_model_id = None;
    let mut bytes_read = 0usize;
    let mut completed = false;
    let mut stream_ended = false;
    let mut search_evidence = false;
    let mut progress = StreamProgressBudget::default();
    progress.advance(started.elapsed());
    let mut stream = response.bytes_stream();
    let mut pending = Vec::<u8>::new();
    loop {
        if cancelled.load(Ordering::SeqCst) {
            return Err(Failure::Cancelled);
        }
        let next = {
            let next_chunk = stream.next();
            tokio::pin!(next_chunk);
            let wait_for = progress.remaining(started.elapsed());
            if wait_for.is_zero() {
                return Err(Failure::Unknown {
                    code: "STREAM_IDLE_TIMEOUT",
                });
            }
            let idle = tokio::time::sleep(wait_for);
            tokio::pin!(idle);
            loop {
                tokio::select! {
                    value = &mut next_chunk => break value,
                    _ = &mut idle => return Err(Failure::Unknown {
                        code: "STREAM_IDLE_TIMEOUT",
                    }),
                    _ = tokio::time::sleep(Duration::from_millis(100)) => {
                        if cancelled.load(Ordering::SeqCst) {
                            return Err(Failure::Cancelled);
                        }
                    }
                }
            }
        };
        let Some(chunk) = next else { break };
        let chunk = chunk.map_err(|error| {
            if error.is_timeout() {
                Failure::Unknown { code: "TIMEOUT" }
            } else {
                Failure::Unknown { code: "NETWORK" }
            }
        })?;
        bytes_read = bytes_read.saturating_add(chunk.len());
        if bytes_read > MAX_RESPONSE_BYTES {
            return Err(Failure::Unknown {
                code: "RESPONSE_SIZE",
            });
        }
        pending.extend_from_slice(&chunk);
        while let Some(end) = pending.iter().position(|byte| *byte == b'\n') {
            let mut line = pending.drain(..=end).collect::<Vec<_>>();
            while matches!(line.last(), Some(b'\n' | b'\r')) {
                line.pop();
            }
            let line = std::str::from_utf8(&line).map_err(|_| Failure::Explicit {
                code: "RESPONSE_FORMAT",
                http_status: Some(status),
            })?;
            let Some(data) = line.strip_prefix("data:").map(str::trim) else {
                continue;
            };
            if data == "[DONE]" {
                stream_ended = true;
                completed = true;
                break;
            }
            if data.is_empty() {
                continue;
            }
            let value: Value = serde_json::from_str(data).map_err(|_| Failure::Explicit {
                code: "RESPONSE_FORMAT",
                http_status: Some(status),
            })?;
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
                match value.get("type").and_then(Value::as_str) {
                    Some("response.output_text.delta") => {
                        if let Some(delta) = value
                            .get("delta")
                            .and_then(Value::as_str)
                            .filter(|delta| !delta.is_empty())
                        {
                            progress.advance(started.elapsed());
                            text.push_str(delta);
                            on_delta(delta)?;
                        }
                    }
                    Some("response.reasoning_text.delta") => {
                        if let Some(delta) = value
                            .get("delta")
                            .and_then(Value::as_str)
                            .filter(|delta| !delta.is_empty())
                        {
                            progress.advance(started.elapsed());
                            reasoning.push_str(delta);
                        }
                    }
                    Some("response.completed") => {
                        if let Some(response) = value.get("response") {
                            search_evidence |= has_web_search_evidence(response);
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
                            if let Some(model) = response.get("model").and_then(Value::as_str) {
                                actual_model_id = Some(model.to_owned());
                            }
                            for source in response_sources(response) {
                                if sources.len() < 10
                                    && !sources.iter().any(|item| item.1 == source.1)
                                {
                                    sources.push(source);
                                }
                            }
                        }
                        completed = true;
                    }
                    Some("response.failed" | "response.incomplete") => {
                        return Err(Failure::Unknown {
                            code: "INCOMPLETE_RESPONSE",
                        });
                    }
                    _ => {}
                }
                if completed {
                    break;
                }
                continue;
            }
            if let Some(delta) = text_delta(&value).filter(|delta| !delta.is_empty()) {
                progress.advance(started.elapsed());
                text.push_str(delta);
                on_delta(delta)?;
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
            merge_usage(&mut usage, parse_usage(value.get("usage")));
            if let Some(cost) = cost_micros(value.get("usage").and_then(|item| item.get("cost"))) {
                reported_cost_micros = Some(cost);
            }
            if let Some(model) = value.get("model").and_then(Value::as_str) {
                actual_model_id = Some(model.to_owned());
            }
            if let Some(reason) = value
                .pointer("/choices/0/finish_reason")
                .and_then(Value::as_str)
            {
                if matches!(reason, "length" | "max_tokens" | "content_filter") {
                    return Err(Failure::Explicit {
                        code: if reason == "content_filter" {
                            "CONTENT_FILTER"
                        } else {
                            "OUTPUT_LIMIT"
                        },
                        http_status: Some(status),
                    });
                }
            }
            if has_explicit_choice_finish_reason(&value) {
                completed = true;
            }
        }
        if stream_ended || (responses_stream && completed) {
            break;
        }
    }
    if cancelled.load(Ordering::SeqCst) {
        return Err(Failure::Cancelled);
    }
    // Keep every Chat Completions provider aligned with Android: a clean EOF after
    // visible answer text is terminal even if that provider omitted `[DONE]` and
    // `finish_reason`. Responses API routes retain their explicit terminal-event rule.
    if !completed && !responses_stream && !text.trim().is_empty() {
        completed = true;
    }
    if !completed {
        return Err(Failure::Unknown {
            code: "MISSING_COMPLETION",
        });
    }
    let mut text = text.trim().to_owned();
    if text.is_empty() {
        return Err(Failure::Explicit {
            code: "RESPONSE_FORMAT",
            http_status: Some(status),
        });
    }
    if request.web_search_route != "NONE" && !search_evidence && sources.is_empty() {
        return Err(Failure::Explicit { code: "WEB_SEARCH_NO_SOURCES", http_status: Some(status) });
    }
    append_sources(&mut text, &sources);
    Ok(Completed {
        text,
        reasoning: (!reasoning.trim().is_empty()).then(|| reasoning.trim().to_owned()),
        usage,
        reported_cost_micros,
        actual_model_id,
        web_search_verified: search_evidence || !sources.is_empty(),
        elapsed_ms: started.elapsed().as_millis().min(i64::MAX as u128) as i64,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
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
        assert_eq!(result.usage.input_tokens, Some(8));
        assert_eq!(result.usage.cached_input_tokens, Some(2));
        for stop in ["pause_turn", "max_tokens"] {
            let mut bad = good.clone(); bad["stop_reason"] = json!(stop);
            assert!(matches!(decode_deepseek_messages(&serde_json::to_vec(&bad).unwrap(), 1), Err(Failure::Explicit { code: "RESPONSE_INCOMPLETE", .. })));
        }
        let mut bad = good; bad["content"][1]["content"] = json!({"type":"web_search_tool_result_error","error_code":"unavailable"});
        assert!(matches!(decode_deepseek_messages(&serde_json::to_vec(&bad).unwrap(), 1), Err(Failure::Explicit { code: "WEB_SEARCH_NO_SOURCES", .. })));
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
        assert!(matches!(completed, Failure::Explicit { code: "WEB_SEARCH_NO_SOURCES", .. }));
        assert!(deltas.is_empty());
    }

    #[test]
    fn responses_stream_completion_keeps_usage_without_sources_and_separates_tool_evidence() {
        for evidence in [false, true] {
            let output = if evidence {
                json!([{"type":"web_search_call","status":"completed"}])
            } else {
                json!([])
            };
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
            if !evidence {
                assert!(matches!(completed, Err(Failure::Explicit { code: "WEB_SEARCH_NO_SOURCES", .. })));
                continue;
            }
            let completed = completed.unwrap();
            assert_eq!(completed.text, "complete answer");
            assert_eq!(completed.usage.input_tokens, Some(11));
            assert_eq!(completed.usage.output_tokens, Some(7));
            assert_eq!(completed.web_search_verified, evidence);
        }
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
