//! Ordinary-chat OpenAI-compatible transport core.
//!
//! The workspace owner persists messages/attempts; this module only owns one bounded HTTP call
//! and normalized response facts. Raw payloads, prompts and credentials never appear in results.

use futures_util::StreamExt;
use reqwest::Client;
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

fn responses_input(messages: &Value) -> Value {
    Value::Array(messages.as_array().into_iter().flatten().filter_map(|message| {
        let role = message.get("role")?.as_str()?;
        let content = message.get("content")?;
        let text = content.as_str().map(ToOwned::to_owned).or_else(|| content.as_array().map(|parts| parts.iter().filter_map(|part| part.get("text").and_then(Value::as_str)).collect::<Vec<_>>().join("\n")))?;
        Some(json!({"role":role,"content":[{"type":"input_text","text":text}]}))
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
                "OPENROUTER_SERVER_TOOL" => { object.insert("tools".into(), json!([{"type":"openrouter:web_search","parameters":{"max_results":5,"max_total_results":10}}])); }
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

fn response_sources(value: &Value) -> Vec<(String, String)> {
    value.get("output").and_then(Value::as_array).into_iter().flatten()
        .filter(|item| item.get("type").and_then(Value::as_str) == Some("web_search_call"))
        .flat_map(|item| item.get("action").and_then(|action| action.get("sources")).and_then(Value::as_array).into_iter().flatten())
        .filter_map(|source| {
            let url = source.get("url")?.as_str()?.trim();
            if !(url.starts_with("https://") || url.starts_with("http://")) { return None; }
            let title = source.get("title").or_else(|| source.get("name")).and_then(Value::as_str).unwrap_or(url).trim();
            Some((title.to_owned(), url.to_owned()))
        }).fold(Vec::<(String,String)>::new(), |mut values, source| { if !values.iter().any(|item| item.1 == source.1) && values.len() < 10 { values.push(source); } values })
}

fn append_sources(text: &mut String, sources: &[(String, String)]) {
    if sources.is_empty() { return; }
    text.push_str("\n\n来源：");
    for (title, url) in sources { text.push_str(&format!("\n- [{title}]({url})")); }
}

fn decode_responses_non_streaming(bytes: &[u8], elapsed_ms: i64) -> Result<Completed, Failure> {
    if bytes.is_empty() || bytes.len() > MAX_RESPONSE_BYTES { return Err(Failure::Unknown { code: "RESPONSE_SIZE" }); }
    let value: Value = serde_json::from_slice(bytes).map_err(|_| Failure::Explicit { code:"RESPONSE_FORMAT", http_status:None })?;
    let mut text = value.get("output_text").and_then(Value::as_str).map(str::trim).filter(|value| !value.is_empty()).map(ToOwned::to_owned)
        .or_else(|| value.get("output").and_then(Value::as_array).into_iter().flatten().filter(|item| item.get("type").and_then(Value::as_str) == Some("message")).flat_map(|item| item.get("content").and_then(Value::as_array).into_iter().flatten()).filter(|item| matches!(item.get("type").and_then(Value::as_str), Some("output_text" | "text"))).filter_map(|item| item.get("text").and_then(Value::as_str)).collect::<String>().trim().to_owned().into())
        .filter(|value| !value.is_empty()).ok_or(Failure::Explicit { code:"RESPONSE_FORMAT", http_status:None })?;
    append_sources(&mut text, &response_sources(&value));
    let usage = value.get("usage");
    Ok(Completed {
        text, reasoning:None,
        usage:Usage { input_tokens:usage.and_then(|v|v.get("input_tokens").or_else(||v.get("prompt_tokens"))).and_then(Value::as_i64), output_tokens:usage.and_then(|v|v.get("output_tokens").or_else(||v.get("completion_tokens"))).and_then(Value::as_i64), cached_input_tokens:usage.and_then(|v|v.get("input_tokens_details").or_else(||v.get("prompt_tokens_details"))).and_then(|v|v.get("cached_tokens")).and_then(Value::as_i64), reasoning_tokens:usage.and_then(|v|v.get("output_tokens_details").or_else(||v.get("completion_tokens_details"))).and_then(|v|v.get("reasoning_tokens")).and_then(Value::as_i64) },
        reported_cost_micros:cost_micros(usage.and_then(|v|v.get("cost"))), actual_model_id:value.get("model").and_then(Value::as_str).map(ToOwned::to_owned), elapsed_ms,
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
        let completed = decode_responses_non_streaming(
            &bytes,
            started.elapsed().as_millis().min(i64::MAX as u128) as i64,
        )?;
        on_delta(&completed.text)?;
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
        let next = tokio::select! {
            value = stream.next() => value,
            _ = tokio::time::sleep(Duration::from_millis(100)) => continue,
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
                            if let Some(cost) = cost_micros(
                                response_usage.and_then(|usage| usage.get("cost")),
                            ) {
                                reported_cost_micros = Some(cost);
                            }
                            if let Some(model) = response.get("model").and_then(Value::as_str) {
                                actual_model_id = Some(model.to_owned());
                            }
                            sources = response_sources(response);
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
        }
        if completed {
            break;
        }
    }
    if cancelled.load(Ordering::SeqCst) {
        return Err(Failure::Cancelled);
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
            assert!(request.contains("\"stream\":true"));
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
        assert_eq!(body["tools"][0]["web_search"]["search_engine"], "search_std");
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
    fn responses_decoder_keeps_usage_and_appends_safe_deduplicated_sources() {
        let result = decode_responses_non_streaming(
            br#"{"model":"deepseek-chat","output_text":"answer","output":[{"type":"web_search_call","action":{"sources":[{"title":"Official","url":"https://example.com/a"},{"title":"Duplicate","url":"https://example.com/a"},{"title":"Unsafe","url":"file:///tmp/a"}]}}],"usage":{"input_tokens":5,"output_tokens":7,"input_tokens_details":{"cached_tokens":2},"output_tokens_details":{"reasoning_tokens":3},"cost":0.000009}}"#,
            12,
        )
        .unwrap();
        assert_eq!(result.text, "answer\n\n来源：\n- [Official](https://example.com/a)");
        assert_eq!(result.usage.input_tokens, Some(5));
        assert_eq!(result.usage.cached_input_tokens, Some(2));
        assert_eq!(result.usage.reasoning_tokens, Some(3));
        assert_eq!(result.reported_cost_micros, Some(9));
        assert_eq!(result.actual_model_id.as_deref(), Some("deepseek-chat"));
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
