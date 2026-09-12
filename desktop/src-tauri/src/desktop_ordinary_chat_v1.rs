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
const STREAM_IDLE_TIMEOUT: Duration = Duration::from_secs(75);

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

fn reasoning_delta(value: &Value) -> Option<&str> {
    let delta = value.get("choices")?.get(0)?.get("delta")?;
    delta
        .get("reasoning_content")
        .or_else(|| delta.get("reasoning"))?
        .as_str()
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

fn transport_body(request: &TransportRequest) -> Value {
    match request.web_search_route.as_str() {
        "QWEN_RESPONSES" => json!({
            "model":request.model_id,"input":responses_input(&request.messages),"tools":[{"type":"web_search"}],
            "store":false,"stream":true,"max_output_tokens":request.max_output_tokens
        }),
        "DEEPSEEK_RESPONSES" => json!({
            "model":request.model_id,"input":responses_input(&request.messages),"tools":[{"type":"web_search"}],
            "tool_choice":{"type":"web_search"},"stream":false,"max_output_tokens":request.max_output_tokens
        }),
        _ => {
            let mut body = json!({
                "model":request.model_id,"messages":request.messages,"stream":true,
                "stream_options":{"include_usage":true},"max_tokens":request.max_output_tokens,"temperature":0.2
            });
            let object = body.as_object_mut().expect("request body is an object");
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
    }
}

/// Background title generation is deliberately a normal JSON request.  It shares the hardened
/// provider decoder with ordinary chat but never opens a streaming UI lifecycle.
pub async fn execute_non_streaming(
    request: TransportRequest,
    mut secret: Zeroizing<Vec<u8>>,
) -> Result<Completed, Failure> {
    let authorization = Zeroizing::new(String::from_utf8(secret.to_vec()).map_err(|_| Failure::Explicit { code: "CREDENTIAL_FORMAT", http_status: None })?);
    secret.zeroize();
    let mut body = transport_body(&request);
    body["stream"] = Value::Bool(false);
    body.as_object_mut().map(|object| object.remove("stream_options"));
    let started = Instant::now();
    let response = Client::builder().connect_timeout(Duration::from_secs(15)).timeout(Duration::from_secs(90)).build()
        .map_err(|_| Failure::Unknown { code: "CLIENT" })?
        .post(&request.endpoint).bearer_auth(authorization.as_str())
        .header("Idempotency-Key", &request.idempotency_key).header("Accept", "application/json")
        .json(&body).send().await.map_err(|error| if error.is_timeout() { Failure::Unknown { code: "TIMEOUT" } } else { Failure::Unknown { code: "NETWORK" } })?;
    let status = response.status().as_u16();
    if !(200..300).contains(&status) { return Err(Failure::Explicit { code: safe_http_code(status), http_status: Some(status) }); }
    let bytes = response.bytes().await.map_err(|error| if error.is_timeout() { Failure::Unknown { code: "TIMEOUT" } } else { Failure::Unknown { code: "NETWORK" } })?;
    decode_non_streaming(&bytes, started.elapsed().as_millis().min(i64::MAX as u128) as i64)
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
    append_sources(&mut text, &response_sources(&value));
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
    let text = value
        .get("choices")
        .and_then(|choices| choices.get(0))
        .and_then(|choice| choice.get("message"))
        .and_then(|message| message.get("content"))
        .and_then(Value::as_str)
        .map(str::trim)
        .filter(|text| !text.is_empty())
        .ok_or(Failure::Explicit {
            code: "RESPONSE_FORMAT",
            http_status: None,
        })?
        .to_owned();
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
        elapsed_ms,
    })
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
    let response = Client::builder()
        .connect_timeout(Duration::from_secs(15))
        .timeout(Duration::from_secs(600))
        .build()
        .map_err(|_| Failure::Unknown { code: "CLIENT" })?
        .post(&request.endpoint)
        .bearer_auth(authorization.as_str())
        .header("Idempotency-Key", &request.idempotency_key)
        .header("Accept", "text/event-stream")
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
    if request.web_search_route == "DEEPSEEK_RESPONSES" {
        let bytes = response.bytes().await.map_err(|error| {
            if error.is_timeout() {
                Failure::Unknown { code: "TIMEOUT" }
            } else {
                Failure::Unknown { code: "NETWORK" }
            }
        })?;
        // Android keeps a valid DeepSeek answer even when the provider does not
        // return citation metadata. `decode_responses_non_streaming` appends only
        // provider-owned, safe sources when they are present.
        // DeepSeek's Android adapter declares this Responses route non-streaming.
        // Do not publish a partial delta from a fully-buffered JSON reply: the
        // workspace owner must receive one terminal `Completed` result.
        let completed = decode_responses_non_streaming(
            &bytes,
            started.elapsed().as_millis().min(i64::MAX as u128) as i64,
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
    let mut stream = response.bytes_stream();
    let mut pending = Vec::<u8>::new();
    loop {
        if cancelled.load(Ordering::SeqCst) {
            return Err(Failure::Cancelled);
        }
        // A provider may take time to reason, but a silent socket cannot remain a fake
        // “正在生成” forever. Keep polling cancellation while one `next()` future is alive;
        // each actual SSE chunk resets the idle window.
        let next = {
            let next_chunk = stream.next();
            tokio::pin!(next_chunk);
            let idle = tokio::time::sleep(STREAM_IDLE_TIMEOUT);
            tokio::pin!(idle);
            loop {
                tokio::select! {
                    value = &mut next_chunk => break value,
                    _ = &mut idle => return Err(Failure::Unknown { code: "TIMEOUT" }),
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
                            reasoning.push_str(delta);
                        }
                    }
                    Some("response.completed") => {
                        if let Some(response) = value.get("response") {
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
                text.push_str(delta);
                on_delta(delta)?;
            }
            if let Some(delta) = reasoning_delta(&value).filter(|delta| !delta.is_empty()) {
                reasoning.push_str(delta);
            }
            merge_usage(&mut usage, parse_usage(value.get("usage")));
            if let Some(cost) = cost_micros(value.get("usage").and_then(|item| item.get("cost"))) {
                reported_cost_micros = Some(cost);
            }
            if let Some(model) = value.get("model").and_then(Value::as_str) {
                actual_model_id = Some(model.to_owned());
            }
            if has_explicit_choice_finish_reason(&value) {
                completed = true;
            }
        }
        if completed {
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
    // Provider citations are optional metadata on Android. Preserve a valid answer when
    // a search provider did not return them; append only verified provider-owned sources.
    append_sources(&mut text, &sources);
    Ok(Completed {
        text,
        reasoning: (!reasoning.trim().is_empty()).then(|| reasoning.trim().to_owned()),
        usage,
        reported_cost_micros,
        actual_model_id,
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
        }
    }

    #[test]
    fn web_search_request_shapes_match_android_provider_contracts() {
        let mut fixture = request("https://provider.invalid/chat/completions".into());

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

        fixture.web_search_route = "DEEPSEEK_RESPONSES".into();
        let body = transport_body(&fixture);
        assert_eq!(body["tools"][0]["type"], "web_search");
        assert_eq!(body["tool_choice"]["type"], "web_search");
        assert_eq!(body["stream"], false);
    }

    #[test]
    fn deepseek_flash_responses_preserves_images_and_requested_identity() {
        let mut fixture = request("http://127.0.0.1:1".into());
        fixture.model_id = "deepseek-flash".into();
        fixture.messages = json!([{"role":"user","content":[{"type":"text","text":"look"},{"type":"image_url","image_url":{"url":"data:image/png;base64,AQID"}}]}]);
        fixture.web_search_route = "DEEPSEEK_RESPONSES".into();
        let body = transport_body(&fixture);
        assert_eq!(body["model"], "deepseek-flash");
        assert_eq!(
            body["input"][0]["content"][1],
            json!({"type":"input_image","image_url":"data:image/png;base64,AQID"})
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
    fn deepseek_responses_keeps_valid_answer_without_search_sources() {
        let result = decode_responses_non_streaming(
            br#"{"model":"deepseek-chat","output_text":"provider answer","usage":{"input_tokens":3,"output_tokens":2}}"#,
            12,
        )
        .expect("Android parity keeps a valid DeepSeek answer without citation metadata");
        assert_eq!(result.text, "provider answer");
        assert_eq!(result.usage.input_tokens, Some(3));
        assert_eq!(result.usage.output_tokens, Some(2));
    }

    #[test]
    fn deepseek_responses_commits_once_without_stream_deltas() {
        let body = b"HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"model\":\"deepseek-chat\",\"output_text\":\"provider answer\"}";
        let mut request = request(mock_server_with_stream(body, false));
        request.web_search_route = "DEEPSEEK_RESPONSES".into();
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
            .expect("DeepSeek Responses must return one terminal result");
        assert_eq!(completed.text, "provider answer");
        assert!(deltas.is_empty());
    }

    #[test]
    fn title_transport_is_a_non_streaming_json_request() {
        let body = b"HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"choices\":[{\"message\":{\"content\":\"{\\\"title\\\":\\\"KFKPlan\\\"}\"}}]}";
        let request = request(mock_server_with_stream(body, false));
        let completed = tokio::runtime::Runtime::new().unwrap().block_on(execute_non_streaming(request, Zeroizing::new(b"fixture-secret-123".to_vec()))).unwrap();
        assert_eq!(completed.text, "{\"title\":\"KFKPlan\"}");
    }

    #[test]
    fn chat_completions_web_search_keeps_answers_without_sources_and_appends_safe_citations() {
        let without_sources = b"HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\ndata: {\"choices\":[{\"delta\":{\"content\":\"provider answer\"}}]}\n\ndata: [DONE]\n\n";
        let mut missing = request(mock_server(without_sources));
        missing.web_search_route = "OPENROUTER_SERVER_TOOL".into();
        let completed_without_sources = tokio::runtime::Runtime::new()
            .unwrap()
            .block_on(execute_streaming(
                missing,
                Zeroizing::new(b"fixture-secret-123".to_vec()),
                Arc::new(AtomicBool::new(false)),
                |_| Ok(()),
            ))
            .expect("Android parity preserves a valid answer when citations are absent");
        assert_eq!(completed_without_sources.text, "provider answer");

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
