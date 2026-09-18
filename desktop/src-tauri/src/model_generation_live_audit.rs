//! Explicit opt-in transport audit; synthetic text only, no workspace mutation or raw logs.
use crate::{desktop_model_service_v1 as catalog, desktop_ordinary_chat_v1 as chat};
use catalog::ProviderCredentialStore;
use serde_json::json;
use std::{
    io::Write,
    sync::{atomic::AtomicBool, Arc},
    time::Instant,
};

#[tokio::test]
#[ignore = "requires explicit authorization and configured live provider credentials"]
async fn configured_model_generation_audit() {
    assert_eq!(
        std::env::var("NFAI_LIVE_MODEL_AUDIT").as_deref(),
        Ok("authorized-synthetic-only")
    );
    let workspace =
        std::env::var("NFAI_AUDIT_CREDENTIAL_ROOT").expect("explicit private credential root");
    let output = std::env::var("NFAI_AUDIT_OUTPUT").expect("explicit safe evidence path");
    let filters = std::env::var("NFAI_AUDIT_MODELS").unwrap_or_default();
    let second_sample =
        std::env::var("NFAI_AUDIT_PROMPT_VARIANT").as_deref() == Ok("second-sample");
    let title = std::env::var("NFAI_AUDIT_TITLE").as_deref() == Ok("1");
    let web = std::env::var("NFAI_AUDIT_WEB").as_deref() == Ok("1");
    let file = Arc::new(std::sync::Mutex::new(
        std::fs::OpenOptions::new()
            .create_new(true)
            .write(true)
            .open(output)
            .unwrap(),
    ));
    let jobs = catalog::PROVIDERS.into_iter().map(|provider| {
        let file = file.clone(); let workspace = workspace.clone(); let filters = filters.clone();
        async move {
            let credentials = catalog::AppPrivateProviderCredentialStore::at(workspace);
            for preset in catalog::chat_presets().into_iter().filter(|p| p.provider_id == provider.id && p.chat_selectable && p.id != "KIMI_K3") {
                if !filters.is_empty() && !filters.split(',').any(|id| id == preset.id) { continue; }
                let secret = credentials.with_secret(provider.id, |key| Ok(zeroize::Zeroizing::new(key.to_vec()))).expect("credential unavailable");
                let route = crate::ordinary_chat_web_search_route(provider.id, preset.model_id, web, false);
                let suffix = if route.ends_with("RESPONSES") { "responses" } else { "chat/completions" };
                let started = Instant::now(); let mut first_visible_ms = None; let mut deltas = 0;
                let mut request = chat::TransportRequest {
                    endpoint: format!("{}/{suffix}", provider.endpoint), provider_id: provider.id.into(), model_id: preset.model_id.into(),
                    messages: json!([{"role":"user","content":if web { if second_sample {"请查阅 Rust 编程语言的官方网站，用一句话说明其用途并给出官网链接。"} else {"请查询 Python 官方网站，用一句话说明 Python 是什么，并给出官方网站链接。"} } else {"请计算 17 加 25，只回答数字，不要解释。"}}]),
                    idempotency_key: format!("model-audit-{}-{}-{}",std::process::id(),preset.id,web),
                    max_output_tokens: chat::ordinary_output_token_limit(provider.id, preset.model_id), web_search_route: route.into(), structured_json: false, disable_thinking: false,
                };
                if title {
                    request.messages = crate::desktop_conversation_title_v1::request_messages(&crate::desktop_conversation_title_v1::Source {
                        assistant_message_id: "synthetic-title-audit".into(), user_text: "如何整理苏美尔王表？".into(),
                        assistant_text: "苏美尔王表可以按洪水前后的王朝、城邦、君王及记载年数制作表格，并区分神话与历史材料。".into(),
                    });
                    request.max_output_tokens = if provider.id == "DEEPSEEK" { 256 } else { 8192 };
                    request.structured_json = provider.id == "DEEPSEEK";
                    request.disable_thinking = provider.id == "DEEPSEEK";
                }
                let result = if title { chat::execute_non_streaming(request, secret).await } else {
                    chat::execute_streaming(request, secret, Arc::new(AtomicBool::new(false)), |delta| { if !delta.is_empty() { deltas += 1; first_visible_ms.get_or_insert(started.elapsed().as_millis()); } Ok(()) }).await
                };
                let mut fact = json!({"preset":preset.id,"provider":provider.id,"model":preset.model_id,"web":web,"title":title,"secondSample":second_sample,"route":route,"elapsedMs":started.elapsed().as_millis(),"firstVisibleMs":first_visible_ms,"deltas":deltas});
                match result {
                    Ok(reply) => { fact["state"]=json!("COMPLETED");fact["visibleChars"]=json!(reply.text.chars().count());fact["expectedAnswer"]=json!(if title { crate::desktop_conversation_title_v1::parse_response(&reply.text).is_some() } else { web || reply.text.trim()=="42" });fact["reasoningChars"]=json!(reply.reasoning.as_deref().unwrap_or("").chars().count());fact["usage"]=json!(reply.usage);fact["actualModel"]=json!(reply.actual_model_id);fact["searchVerified"]=json!(reply.web_search_verified);fact["reportedCostMicros"]=json!(reply.reported_cost_micros); }
                    Err(error) => { fact["state"]=json!("FAILED_OR_UNKNOWN");fact["safeError"]=json!(format!("{error:?}")); }
                }
                writeln!(file.lock().unwrap(),"{fact}").unwrap();
                eprintln!("MODEL_AUDIT {} {} {}ms",preset.id,fact["state"],fact["elapsedMs"]);
            }
        }
    });
    futures_util::future::join_all(jobs).await;
}
