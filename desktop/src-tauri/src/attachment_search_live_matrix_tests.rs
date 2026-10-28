//! Explicit opt-in public service verification. Never logs credentials or private material.
use serde_json::Value;
fn public_wire_shape(body:&Value)->Value {
    fn known(v:&Value)->Value {match v.as_str() {Some(s) if matches!(s,"web_search"|"web_search_preview"|"web_search_2025_08_26"|"web_search_20250305"|"allowed_tools"|"function"|"namespace"|"additional_tools"|"custom"|"required"|"auto"|"none"|"system"|"developer"|"user"|"assistant"|"input_text"|"input_image"|"message")=>json!(s),_=>Value::Null}}
    json!({"toolsCount":body["tools"].as_array().map(Vec::len).unwrap_or(0),
        "tools":body["tools"].as_array().map(|a|a.iter().map(|t|json!({"type":known(&t["type"]),"keys":t.as_object().map(|o|o.keys().collect::<Vec<_>>()),"externalWebAccess":t["external_web_access"],"searchContextSize":t["search_context_size"]})).collect::<Vec<_>>()),
        "choiceKind":if body["tool_choice"].is_object(){"object"}else if body["tool_choice"].is_string(){"string"}else{"absent"},"choiceValue":known(&body["tool_choice"]),"choiceType":known(&body["tool_choice"]["type"]),"choiceMode":known(&body["tool_choice"]["mode"]),
        "input":body["input"].as_array().map(|a|a.iter().map(|v|json!({"role":known(&v["role"]),"type":known(&v["type"]),"partTypes":v["content"].as_array().map(|a|a.iter().map(|v|known(&v["type"])).collect::<Vec<_>>())})).collect::<Vec<_>>()),
        "routingKeyPresent":body["prompt_cache_key"].as_str().is_some(),"routingKeyFingerprint":body["prompt_cache_key"].as_str().map(|v|{use sha2::Digest;format!("{:x}",sha2::Sha256::digest(v.as_bytes()))}),"maxOutputTokens":body["max_output_tokens"],"stream":body["stream"],"store":body["store"]})
}
use super::*;
use crate::desktop_model_service_v1::{AppPrivateProviderCredentialStore, ProviderCredentialStore};

#[tokio::test]
#[ignore = "independent paid public search-engine control, explicit opt-in"]
async fn public_glm_official_search_engine_control() {
    assert_eq!(std::env::var("NFAI_PUBLIC_MATRIX").as_deref(),Ok("1"));
    let store=AppPrivateProviderCredentialStore::at(std::env::var("NFAI_CREDENTIAL_ROOT").unwrap());
    let key=store.with_secret("ZHIPU",|v|Ok(Zeroizing::new(v.to_vec()))).unwrap();
    let request=TransportRequest {endpoint:"https://open.bigmodel.cn/api/paas/v4/chat/completions".into(),provider_id:"ZHIPU".into(),model_id:"glm-5.3-flash".into(),
        messages:json!([{"role":"system","content":"使用真实网页资料核验公开编程语言发布事项，以[A1] 已核实或未核实开头，说明发布日期并引用实际来源。"},{"role":"user","content":"[A1] Rust 1.90.0正式发布，请核验blog.rust-lang.org官方发布日期。"}]),
        idempotency_key:format!("public-glm-engine-control-{}",crate::system_now_millis()),max_output_tokens:32768,web_search_route:"ZHIPU_CHAT_COMPLETIONS".into(),structured_json:false,disable_thinking:false,grounded_answer:false,explicit_search_query:Some("Rust 1.90.0正式发布，请核验blog.rust-lang.org官方发布日期。".into())};
    let kind=if std::env::var("NFAI_PUBLIC_GLM_ENGINE").as_deref()==Ok("std") {"public-glm-search-std"}else{"public-glm-search-pro"};
    let result=chat::public_native_search_shape_probe(request,key,kind).await;
    let safe=match result {Ok(v)=>v,Err(e)=>json!({"error":format!("{e:?}")})};
    std::fs::write(std::env::var("NFAI_MATRIX_OUTPUT").unwrap(),serde_json::to_vec_pretty(&safe).unwrap()).unwrap();
    println!("PUBLIC_GLM_ENGINE_CONTROL {safe}");assert_eq!(safe["choiceFinishReason"],"stop");assert!(safe["textChars"].as_u64().unwrap_or(0)>0);assert!(safe["nativeSourceCount"].as_u64().unwrap_or(0)>0);
}

#[test]
#[ignore = "one-shot credential capability for explicitly authorized isolated Android native acceptance"]
fn public_android_one_shot_credential_broker() {
    use std::io::{Read, Write};
    use std::process::{Command, Stdio};
    assert_eq!(std::env::var("NFAI_PUBLIC_MATRIX").as_deref(),Ok("1"));
    const DEVICE:&str="emulator-5592";
    const PACKAGE:&str="com.nanzhufeng.ai.publicmodelacceptance";
    let adb=std::env::var("NFAI_ADB").unwrap();
    let listener=std::net::TcpListener::bind(("127.0.0.1",0)).unwrap();
    listener.set_nonblocking(true).unwrap();
    let port=listener.local_addr().unwrap().port();
    let target=format!("tcp:{port}");
    struct Cleanup {adb:String,target:String}
    impl Drop for Cleanup {fn drop(&mut self){
        let _=Command::new(&self.adb).args(["-s",DEVICE,"reverse","--remove",&self.target]).stdout(Stdio::null()).stderr(Stdio::null()).status();
        let _=Command::new(&self.adb).args(["-s",DEVICE,"shell","run-as",PACKAGE,"rm","-f","files/public-bootstrap.json"]).stdout(Stdio::null()).stderr(Stdio::null()).status();
    }}
    let _cleanup=Cleanup{adb:adb.clone(),target:target.clone()};
    assert!(Command::new(&adb).args(["-s",DEVICE,"reverse",&target,&target]).stdout(Stdio::null()).status().unwrap().success());
    let mut random=[0u8;32];std::fs::File::open("/dev/urandom").unwrap().read_exact(&mut random).unwrap();
    let token=Zeroizing::new(random.iter().map(|b|format!("{b:02x}")).collect::<String>());
    let run=std::env::var("NFAI_ANDROID_RUN").unwrap();assert!(run.chars().all(|c|c.is_ascii_alphanumeric()||c=='-'));
    let mut config_value=json!({"port":port,"token":token.as_str(),"runId":run,"filter":std::env::var("NFAI_MATRIX_FILTER").unwrap_or_default(),"fixture":std::env::var("NFAI_PUBLIC_FIXTURE").unwrap_or_default(),"web":std::env::var("NFAI_ROUNDTRIP_WEB").as_deref()==Ok("1"),"requestText":std::env::var("NFAI_ROUNDTRIP_TEXT").unwrap_or_default(),"budgetCnyMicros":20_000_000,"replayReader":std::env::var("NFAI_REPLAY_READER").as_deref()==Ok("1"),"outputLimit":if std::env::var("NFAI_PUBLIC_FIXTURE").as_deref()==Ok("attachment-roundtrip"){serde_json::json!(8192)}else{serde_json::Value::Null},"claimCountPerFile":std::env::var("NFAI_PUBLIC_MULTIFILE_COUNT").ok().and_then(|v|v.parse::<usize>().ok()).unwrap_or(2).clamp(1,8)});if config_value["outputLimit"].is_null(){config_value.as_object_mut().unwrap().remove("outputLimit");}let config=Zeroizing::new(serde_json::to_vec(&config_value).unwrap());
    let mut child=Command::new(&adb).args(["-s",DEVICE,"shell","run-as com.nanzhufeng.ai.publicmodelacceptance sh -c 'mkdir -p files; cat > files/public-bootstrap.json'"])
        .stdin(Stdio::piped()).stdout(Stdio::null()).stderr(Stdio::null()).spawn().unwrap();
    child.stdin.take().unwrap().write_all(&config).unwrap();assert!(child.wait().unwrap().success());
    assert!(Command::new(&adb).args(["-s",DEVICE,"shell","am","start","-n",&format!("{PACKAGE}/com.nanzhufeng.ai.acceptance.PublicModelAcceptanceActivity")]).stdout(Stdio::null()).status().unwrap().success());
    let start=std::time::Instant::now();
    let mut socket=loop {
        match listener.accept(){Ok((s,_))=>break s,Err(e) if e.kind()==std::io::ErrorKind::WouldBlock=>{},Err(_)=>panic!("capability accept failed")}
        assert!(start.elapsed()<std::time::Duration::from_secs(90),"isolated app did not consume capability");
        std::thread::sleep(std::time::Duration::from_millis(100));
    };
    socket.set_nonblocking(false).unwrap();
    socket.set_read_timeout(Some(std::time::Duration::from_secs(10))).unwrap();
    let mut received=Zeroizing::new(Vec::new());
    loop {let mut b=[0u8];socket.read_exact(&mut b).unwrap();if b[0]==b'\n'{break;}received.push(b[0]);assert!(received.len()<=64);}
    assert!(received.as_slice()==token.as_bytes(),"capability mismatch");
    let owner=AppPrivateProviderCredentialStore::at(std::env::var("NFAI_CREDENTIAL_ROOT").unwrap());
    let mut payload=Zeroizing::new(vec![b'{']);
    let filter=std::env::var("NFAI_MATRIX_FILTER").ok().filter(|f|!f.trim().is_empty());
    let mut providers=crate::desktop_model_service_v1::chat_presets().into_iter().filter(|p|filter.as_ref().is_none_or(|f|f.split(',').any(|id|id==p.id))).map(|p|p.provider_id).collect::<std::collections::BTreeSet<_>>();
    if providers.contains("RELAYCORE_CLAUDE") || providers.contains("RELAYCORE_GEMINI") {providers.insert("RELAYCORE_GPT");}
    if std::env::var("NFAI_PUBLIC_DIRECT_SEARCH").as_deref()==Ok("1") {providers.insert("ZHIPU");}
    if std::env::var("NFAI_PUBLIC_FIXTURE").as_deref()==Ok("attachment-roundtrip") {providers.insert("OPENROUTER");}
    assert!(!providers.is_empty(),"no authorized preset selected");
    for (index,provider) in providers.iter().enumerate(){
        if index>0 {payload.push(b',');}payload.extend_from_slice(format!("\"{provider}\":").as_bytes());
        owner.with_secret(provider,|key|{
            let text=std::str::from_utf8(key).map_err(|_|"PRIVATE_CREDENTIAL_INVALID".to_string())?;
            let serialized=Zeroizing::new(serde_json::to_vec(text).unwrap());payload.extend_from_slice(&serialized);Ok(())
        }).unwrap();
    }
    payload.push(b'}');socket.write_all(&payload).unwrap();socket.shutdown(std::net::Shutdown::Write).unwrap();
    println!("PUBLIC_ANDROID_CAPABILITY delivered_once providers={} device={DEVICE} run={run}",providers.len());
}
fn public_matrix_presets(filter: Option<&str>) -> Result<Vec<crate::desktop_model_service_v1::PresetDescriptor>, &'static str> {
    let current=crate::desktop_model_service_v1::chat_presets().into_iter()
        .filter(|p|p.chat_selectable).collect::<Vec<_>>();
    if current.is_empty() { return Err("no current chat models selected"); }
    let Some(filter)=filter else { return Ok(current); };
    let requested=filter.split(',').map(str::trim).collect::<std::collections::BTreeSet<_>>();
    if requested.iter().any(|id|id.is_empty() || !current.iter().any(|p|p.id==*id)) {
        return Err("matrix filter must contain only current chat preset IDs");
    }
    Ok(current.into_iter().filter(|p|requested.contains(p.id)).collect())
}

#[test]
fn public_matrix_selection_matches_current_chat_catalog_and_rejects_missing_cases() {
    let manifest: Value=serde_json::from_str(include_str!("../../../protocol/model-service/model-service-v1.json")).unwrap();
    let expected=manifest["models"].as_array().unwrap().iter().map(|m|m["presetId"].as_str().unwrap()).collect::<std::collections::BTreeSet<_>>();
    let selected=public_matrix_presets(None).unwrap();
    let actual=selected.iter().map(|p|p.id).collect::<std::collections::BTreeSet<_>>();
    assert_eq!(actual,expected);
    assert_eq!(public_matrix_presets(Some("MERCURY_3")).unwrap().len(),1);
    for filter in ["", "MISSING", "MERCURY_3,MISSING", "GLM_OCR", "CLAUDE_FABLE_5"] {
        assert!(public_matrix_presets(Some(filter)).is_err(), "invalid paid matrix selection: {filter}");
    }
}

#[tokio::test]
#[ignore = "paid public API calls; requires explicit user-authorized opt-in"]
async fn all_current_models_public_search_and_original_answer() {
    assert_eq!(
        std::env::var("NFAI_PUBLIC_MATRIX").ok().as_deref(),
        Some("1")
    );
    let root = std::env::var("NFAI_CREDENTIAL_ROOT").expect("authorized credential root");
    let output = std::env::var("NFAI_MATRIX_OUTPUT").expect("safe evidence output");
    let store = AppPrivateProviderCredentialStore::at(root);
    let mut rows = Vec::new();
    let filter = std::env::var("NFAI_MATRIX_FILTER").ok();
    let selected=public_matrix_presets(filter.as_deref()).expect("invalid paid matrix scope; no requests sent");
    let expected=selected.len();
    for preset in selected {
        let route =
            crate::ordinary_chat_web_search_route(preset.provider_id, preset.model_id, true, true);
        let request = TransportRequest {
            endpoint: chat::ordinary_provider_endpoint(
                preset.provider_id,
                crate::desktop_model_service_v1::provider(preset.provider_id)
                    .unwrap()
                    .endpoint,
                route,
            ),
            provider_id: preset.provider_id.into(),
            model_id: preset.model_id.into(),
            messages: json!([{"role":"user","content":[{"type":"text","text":"联网核验附件报告，用中文简短回答，附官方来源。"},{"type":"text","text":"- Python发布：Python 3.14.0于2025年10月7日发布，请核验python.org官方公告。"}]}]),
            idempotency_key: format!("public-matrix-{}-{}", crate::system_now_millis(), preset.id),
            max_output_tokens: chat::ordinary_output_token_limit(
                preset.provider_id,
                preset.model_id,
            ),
            web_search_route: route.into(),
            structured_json: false,
            disable_thinking: false,
            grounded_answer: false,
            explicit_search_query: None,
        };
        let mut request = request;
        if std::env::var("NFAI_PUBLIC_MULTIFILE").as_deref() == Ok("1") {
            let count=std::env::var("NFAI_PUBLIC_MULTIFILE_COUNT").ok().and_then(|v|v.parse::<usize>().ok()).unwrap_or(8).clamp(1,8);
            let first = [
                "3.14.0", "3.13.0", "3.12.0", "3.11.0", "3.10.0", "3.9.0", "3.8.0", "3.7.0",
            ]
            .iter().take(count)
            .map(|v| format!("- Python {v} 正式发布，核验python.org官方发布日期。"))
            .collect::<Vec<_>>()
            .join("\n");
            let second = [
                "1.90.0", "1.89.0", "1.88.0", "1.87.0", "1.86.0", "1.85.0", "1.84.0", "1.83.0",
            ]
            .iter().take(count)
            .map(|v| format!("- Rust {v} 正式发布，核验blog.rust-lang.org官方发布日期。"))
            .collect::<Vec<_>>()
            .join("\n");
            request.messages = json!([{"role":"user","content":[{"type":"text","text":"分析两份公开发布报告，所有事项都实际联网核验，中文简短回答并引用来源。"},{"type":"text","text":first},{"type":"text","text":second}]}]);
            if std::env::var("NFAI_PUBLIC_FIXTURE").as_deref()==Ok("node-cmake") {
                let first=["22.0.0","21.0.0","20.0.0","19.0.0","18.0.0","17.0.0","16.0.0","15.0.0"].iter().take(count).map(|v|format!("- Node.js {v} 正式发布，核验nodejs.org官方发布日期。")).collect::<Vec<_>>().join("\n");
                let second=["3.31.0","3.30.0","3.29.0","3.28.0","3.27.0","3.26.0","3.25.0","3.24.0"].iter().take(count).map(|v|format!("- CMake {v} 正式发布，核验cmake.org官方发布日期。")).collect::<Vec<_>>().join("\n");
                request.messages=json!([{"role":"user","content":[{"type":"text","text":"独立公开Node与CMake两文件核验，逐项真实搜索官方发布日期，中文简短回答并引用来源。"},{"type":"text","text":first},{"type":"text","text":second}]}]);
            }
            if std::env::var("NFAI_PUBLIC_FIXTURE").as_deref()==Ok("go-firefox") {
                let first=["1.24.0","1.23.0","1.22.0","1.21.0","1.20.0","1.19.0","1.18.0","1.17.0"].iter().take(count).map(|v|format!("- Go {v} 正式发布，核验go.dev官方发布日期。")).collect::<Vec<_>>().join("\n");
                let second=["139.0","138.0","137.0","136.0","135.0","134.0","133.0","132.0"].iter().take(count).map(|v|format!("- Firefox {v} 正式发布，核验mozilla.org官方发布日期。")).collect::<Vec<_>>().join("\n");
                request.messages=json!([{"role":"user","content":[{"type":"text","text":"独立公开Go与Firefox两文件核验，逐项真实搜索官方发布日期，中文简短回答并引用来源。"},{"type":"text","text":first},{"type":"text","text":second}]}]);
            }
        }
        let key = store
            .with_secret(preset.provider_id, |v| Ok(Zeroizing::new(v.to_vec())))
            .unwrap();
        let search_key = if route == "RELAYCORE_COMPANION" {
            Some(
                store
                    .with_secret("OPENROUTER", |v| Ok(Zeroizing::new(v.to_vec())))
                    .unwrap(),
            )
        } else {
            None
        };
        let mut physical = Vec::new();
        let errors = Arc::new(std::sync::Mutex::new(Vec::new()));
        let search_errors = errors.clone();
        let result=execute_cached_core(request,key,search_key,Arc::new(AtomicBool::new(false)),checkpoint::None,|r,reply|{if let Some(c)=reply{physical.push(json!({"requestShape":public_wire_shape(&chat::transport_body(r)),"wire": {"effort":chat::transport_body(r)["reasoning_effort"],"maxTokens":chat::transport_body(r)["max_tokens"],"grounded":r.grounded_answer,"explicitQuery":r.explicit_search_query.is_some(),"searchEngine":chat::transport_body(r)["tools"][0]["web_search"]["search_engine"]},"requested":r.model_id,"returned":c.actual_model_id,"webProof":c.web_search_verified,"nativeSources":c.native_sources.iter().map(|(_,url)|url).collect::<Vec<_>>(),"input":c.usage.input_tokens,"output":c.usage.output_tokens,"reportedCostMicros":c.reported_cost_micros,"elapsedMs":c.elapsed_ms,"publicFixtureReply":c.text}));}Ok(())},move |r,k,c|{let errors=search_errors.clone();async move {let mut r=r;println!("PUBLIC_RETRIEVAL_WIRE_SHAPE {}",public_wire_shape(&chat::transport_body(&r)));let result=chat::execute_streaming(r,k,c,|_|Ok(())).await;if let Err(ref e)=result {errors.lock().unwrap().push(format!("{e:?}"));}result}},|r,k,c| {
            if std::env::var("NFAI_PUBLIC_SHAPE_TRACE").as_deref()==Ok("1") {
                let dir=std::path::Path::new(&output).parent().unwrap();
                let path=dir.join(format!("public-final-request-{}.json",preset.id));
                std::fs::write(path,serde_json::to_vec_pretty(&json!({"provider":r.provider_id,"model":r.model_id,"route":r.web_search_route,"grounded":r.grounded_answer,"body":chat::transport_body(&r)})).unwrap()).unwrap();
            }
            chat::execute_streaming(r,k,c,|_|Ok(()))
        }).await;
        let row = match result {
            Ok(c) => {
                json!({"preset":preset.id,"provider":preset.provider_id,"model":preset.model_id,"scopeComplete":c.complete,"planned":c.planned,"covered":c.covered,"webProof":c.reply.web_search_verified,"returned":c.reply.actual_model_id,"answerNonempty":!c.reply.text.trim().is_empty(),"publicAnswer":c.reply.text,"nativeSources":c.reply.native_sources.iter().map(|(_,url)|url).collect::<Vec<_>>(),"searchErrors":*errors.lock().unwrap(),"physical":physical})
            }
            Err(e) => {
                json!({"preset":preset.id,"provider":preset.provider_id,"model":preset.model_id,"error":format!("{e:?}"),"searchErrors":*errors.lock().unwrap(),"physical":physical})
            }
        };
        println!(
            "{} scope={} error={}",
            preset.id, row["scopeComplete"], row["error"]
        );
        rows.push(row);
        std::fs::write(&output, serde_json::to_vec_pretty(&rows).unwrap()).unwrap();
    }
    assert_eq!(rows.len(), expected);
    assert!(
        rows.iter().all(|r| r["scopeComplete"] == true
            && r["webProof"] == true
            && r["answerNonempty"] == true),
        "some services failed; see safe matrix"
    );
}

#[tokio::test]
#[ignore = "paid public API control; explicit authorization required"]
async fn public_sonnet_compatible_endpoint_control() {
    assert_eq!(std::env::var("NFAI_PUBLIC_MATRIX").as_deref(),Ok("1"));
    let store=AppPrivateProviderCredentialStore::at(std::env::var("NFAI_CREDENTIAL_ROOT").unwrap());
    let key=store.with_secret("RELAYCORE_CLAUDE",|v|Ok(Zeroizing::new(v.to_vec()))).unwrap();
    let request=TransportRequest {
        endpoint:"https://api.relaycore.cn/v1/chat/completions".into(),provider_id:"RELAYCORE_CLAUDE".into(),model_id:"claude-sonnet-5-5".into(),
        messages:json!([{"role":"user","content":"这是一次独立的公开接口验证：附件甲是100次请求中5次超时，附件乙是200次请求中10次超时。分析两份材料，中文给出计算结果，不联网，不涉及私人内容。"}]),
        idempotency_key:format!("public-compatible-control-{}",crate::system_now_millis()),max_output_tokens:8192,
        web_search_route:"NONE".into(),structured_json:false,disable_thinking:false,grounded_answer:false,explicit_search_query:None,
    };
    let result=chat::public_native_search_shape_probe(request,key,"plain-completions").await;
    let safe=match result {Ok(v)=>v,Err(e)=>json!({"error":format!("{e:?}")})};
    std::fs::write(std::env::var("NFAI_MATRIX_OUTPUT").unwrap(),serde_json::to_vec_pretty(&safe).unwrap()).unwrap();
    println!("PUBLIC_COMPATIBLE_CONTROL {safe}");
    assert_eq!(safe["httpStatus"],200);assert_eq!(safe["choiceFinishReason"],"stop");assert!(safe["textChars"].as_u64().unwrap_or(0)>0);assert_eq!(safe["correct"],true);
}

#[tokio::test]
#[ignore = "authorized live model metadata GET; no user material"]
async fn public_relay_claude_model_metadata() {
    assert_eq!(std::env::var("NFAI_PUBLIC_MATRIX").as_deref(),Ok("1"));
    let store=AppPrivateProviderCredentialStore::at(std::env::var("NFAI_CREDENTIAL_ROOT").unwrap());
    let key=store.with_secret("RELAYCORE_CLAUDE",|v|Ok(Zeroizing::new(v.to_vec()))).unwrap();
    let auth=std::str::from_utf8(&key).unwrap();
    let response=reqwest::Client::new().get("https://api.relaycore.cn/v1/models").bearer_auth(auth).send().await.unwrap();
    let status=response.status().as_u16();let value:serde_json::Value=response.json().await.unwrap();
    let ids=value["data"].as_array().into_iter().flatten().filter_map(|x|x["id"].as_str()).filter(|s|s.contains("sonnet")||s.contains("opus")).collect::<Vec<_>>();
    let safe=json!({"httpStatus":status,"modelIds":ids});
    std::fs::write(std::env::var("NFAI_MATRIX_OUTPUT").unwrap(),serde_json::to_vec_pretty(&safe).unwrap()).unwrap();
    println!("PUBLIC_MODEL_METADATA {safe}");assert_eq!(status,200);
}

#[tokio::test]
#[ignore = "paid public response-shape control; no private input"]
async fn public_gemini_grounded_answer_shape_control() {
    assert_eq!(std::env::var("NFAI_PUBLIC_MATRIX").as_deref(),Ok("1"));
    let store=AppPrivateProviderCredentialStore::at(std::env::var("NFAI_CREDENTIAL_ROOT").unwrap());
    let key=store.with_secret("OPENROUTER",|v|Ok(Zeroizing::new(v.to_vec()))).unwrap();
    let request=TransportRequest {
        endpoint:"https://openrouter.ai/api/v1/chat/completions".into(),provider_id:"OPENROUTER".into(),model_id:"google/gemini-3.8-flash".into(),
        messages:json!([{"role":"user","content":"独立的公开回答格式验证：附件甲是Python 3.14.0发布日期2025年10月7日，附件乙是Rust 1.90.0发布日期2025年9月18日。本次不要求新搜索；用中文简短整理这两份已提交资料，保留出处https://www.python.org/downloads/release/python-3140/和https://blog.rust-lang.org/2025/09/18/Rust-1.90.0/。"}]),
        idempotency_key:format!("public-grounded-format-{}",crate::system_now_millis()),max_output_tokens:8192,
        web_search_route:"OPENROUTER_SERVER_TOOL".into(),structured_json:false,disable_thinking:false,grounded_answer:true,explicit_search_query:None,
    };
    let result=chat::execute_streaming(request,key,Arc::new(AtomicBool::new(false)),|_|Ok(())).await;
    let safe=match result {Ok(c)=>json!({"complete":true,"chars":c.text.chars().count(),"returned":c.actual_model_id,"input":c.usage.input_tokens,"output":c.usage.output_tokens}),Err(e)=>json!({"error":format!("{e:?}")})};
    std::fs::write(std::env::var("NFAI_MATRIX_OUTPUT").unwrap(),serde_json::to_vec_pretty(&safe).unwrap()).unwrap();
    println!("PUBLIC_GROUNDED_FORMAT {safe}");assert_eq!(safe["complete"],true);
}

#[tokio::test]
#[ignore = "paid matched public GLM effort control, explicit opt-in"]
async fn public_glm_matched_retrieval_effort_control() {
    assert_eq!(std::env::var("NFAI_PUBLIC_MATRIX").as_deref(),Ok("1"));
    let owner=AppPrivateProviderCredentialStore::at(std::env::var("NFAI_CREDENTIAL_ROOT").unwrap());
    let parent=TransportRequest {
        endpoint:"https://open.bigmodel.cn/api/paas/v4/chat/completions".into(),provider_id:"ZHIPU".into(),model_id:"glm-5.3".into(),
        messages:json!([{"role":"user","content":[{"type":"text","text":"分析两份公开发布报告，所有事项都实际联网核验，中文简短回答并引用来源。"},{"type":"text","text":"- Python 3.14.0 正式发布，核验python.org官方发布日期。\n- Python 3.13.0 正式发布，核验python.org官方发布日期。"},{"type":"text","text":"- Rust 1.90.0 正式发布，核验blog.rust-lang.org官方发布日期。\n- Rust 1.89.0 正式发布，核验blog.rust-lang.org官方发布日期。"}]}]),
        idempotency_key:format!("public-glm-effort-control-{}",crate::system_now_millis()),max_output_tokens:32768,web_search_route:"ZHIPU_CHAT_COMPLETIONS".into(),structured_json:false,disable_thinking:false,grounded_answer:false,explicit_search_query:None,
    };
    let plan=scope::from_messages(&parent.messages);
    assert_eq!(plan.items.len(),4);
    let batch=scope::Plan {items:vec![plan.items[3].clone()],limited:false};
    let request=batch_request(&parent,&batch,4).unwrap();
    let mode=std::env::var("NFAI_PUBLIC_GLM_EFFORT").unwrap();
    assert!(matches!(mode.as_str(),"max"|"low"));
    let key=owner.with_secret("ZHIPU",|v|Ok(Zeroizing::new(v.to_vec()))).unwrap();
    let result=chat::public_native_search_shape_probe(request,key,if mode=="low" {"public-glm-search-low"} else {"public-glm-search-max"}).await;
    let safe=match result {Ok(v)=>v,Err(e)=>json!({"error":format!("{e:?}")})};
    std::fs::write(std::env::var("NFAI_MATRIX_OUTPUT").unwrap(),serde_json::to_vec_pretty(&safe).unwrap()).unwrap();
    println!("PUBLIC_GLM_EFFORT_CONTROL {safe}");
    assert_eq!(safe["choiceFinishReason"],"stop");assert!(safe["textChars"].as_u64().unwrap_or(0)>0);assert!(safe["nativeSourceCount"].as_u64().unwrap_or(0)>0);
}

#[tokio::test]
#[ignore = "paid independent public batch granularity control; no business retry"]
async fn public_relay_single_query_granularity_control() {
    assert_eq!(std::env::var("NFAI_PUBLIC_MATRIX").as_deref(),Ok("1"));
    let owner=AppPrivateProviderCredentialStore::at(std::env::var("NFAI_CREDENTIAL_ROOT").unwrap());
    let parent=TransportRequest {endpoint:"https://api.relaycore.cn/v1/messages".into(),provider_id:"RELAYCORE_CLAUDE".into(),model_id:"claude-sonnet-5-5".into(),
        messages:json!([{"role":"user","content":[{"type":"text","text":"分析公开版本发布报告，逐项核验官方发布日期，中文简短回答。"},{"type":"text","text":"- Python 3.10.0 正式发布，核验python.org官方发布日期。\n- Python 3.9.0 正式发布，核验python.org官方发布日期。"},{"type":"text","text":"- Rust 1.86.0 正式发布，核验blog.rust-lang.org官方发布日期。\n- Rust 1.85.0 正式发布，核验blog.rust-lang.org官方发布日期。"}]}]),
        idempotency_key:format!("public-single-query-control-{}",crate::system_now_millis()),max_output_tokens:8192,web_search_route:"RELAYCORE_COMPANION".into(),structured_json:false,disable_thinking:false,grounded_answer:false,explicit_search_query:None};
    let plan=scope::from_messages(&parent.messages);assert_eq!(plan.items.len(),4);
    let limit=std::env::var("NFAI_PUBLIC_CONTROL_LIMIT").ok().and_then(|v|v.parse::<usize>().ok()).unwrap_or(4).clamp(1,4);
    let mut rows=Vec::new();
    for (index,item) in plan.items.iter().take(limit).enumerate() {
        let batch=scope::Plan {items:vec![item.clone()],limited:false};
        let mut request=batch_request(&parent,&batch,index+1).unwrap();
        if std::env::var("NFAI_PUBLIC_RELAY_ROLE").as_deref()==Ok("developer") {request.messages[0]["role"]=json!("developer");}
        println!("PUBLIC_WIRE_SHAPE {}",public_wire_shape(&chat::transport_body(&request)));
        let key=owner.with_secret("RELAYCORE_GPT",|v|Ok(Zeroizing::new(v.to_vec()))).unwrap();
        let result=chat::execute_streaming(request,key,Arc::new(AtomicBool::new(false)),|_|Ok(())).await;
        let row=match result {Ok(c)=>json!({"item":item.id,"webProof":c.web_search_verified,"sources":c.native_sources.len(),"chars":c.text.chars().count(),"input":c.usage.input_tokens,"output":c.usage.output_tokens,"elapsedMs":c.elapsed_ms,"evidenceValid":evidence(&batch,&c).is_ok()}),Err(e)=>json!({"item":item.id,"error":format!("{e:?}")})};
        println!("PUBLIC_SINGLE_QUERY_CONTROL {row}");let failed=row["webProof"]!=true || row["evidenceValid"]!=true;rows.push(row);
        std::fs::write(std::env::var("NFAI_MATRIX_OUTPUT").unwrap(),serde_json::to_vec_pretty(&rows).unwrap()).unwrap();
        if failed {break;}
    }
    assert_eq!(rows.len(),limit);assert!(rows.iter().all(|r|r["webProof"]==true && r["evidenceValid"]==true));
}

#[tokio::test]
#[ignore = "one paid recovery control after a recorded explicit initial tools rejection; never UNKNOWN"]
async fn public_recorded_initial_tools_rejection_route_recovery_control() {
    assert_eq!(std::env::var("NFAI_PUBLIC_MATRIX").as_deref(),Ok("1"));
    let output=std::env::var("NFAI_MATRIX_OUTPUT").unwrap();let root=std::path::Path::new(&output).parent().unwrap();
    let previous:Value=serde_json::from_slice(&std::fs::read(root.join("mac-sonnet16-single-batch-control.json")).unwrap()).unwrap();
    assert_eq!(previous[0]["covered"],0);assert_eq!(previous[0]["planned"],16);assert_eq!(previous[0]["webProof"],false);assert_eq!(previous[0]["physical"].as_array().unwrap().len(),0);
    assert_eq!(previous[0]["searchErrors"].as_array().unwrap().len(),1);assert!(previous[0]["searchErrors"][0].as_str().unwrap().contains("STREAM_PROVIDER_ERROR"));
    let log=std::fs::read_to_string(root.join("preview-control/sonnet16-single-batch.log")).unwrap();
    let metadata:Value=log.lines().filter_map(|l|l.strip_prefix("PUBLIC_EVENT_SHAPE ")).filter_map(|v|serde_json::from_str::<Value>(v).ok()).find(|v|v["type"]=="error").unwrap();
    assert_eq!(metadata["safeProviderError"]["topLevelCode"],"invalid_request_error");assert!(metadata["safeProviderError"]["fieldMentions"].as_array().unwrap().contains(&json!("tools")));
    let original_shape:Value=log.lines().find_map(|l|l.strip_prefix("PUBLIC_RETRIEVAL_WIRE_SHAPE ").and_then(|v|serde_json::from_str(v).ok())).unwrap();
    assert_eq!(original_shape["toolsCount"],1);assert_eq!(original_shape["tools"][0]["type"],"web_search");assert_eq!(original_shape["tools"][0]["externalWebAccess"],true);assert_eq!(original_shape["choiceValue"],"required");
    let first=["1.24.0","1.23.0","1.22.0","1.21.0","1.20.0","1.19.0","1.18.0","1.17.0"].iter().map(|v|format!("- Go {v} 正式发布，核验go.dev官方发布日期。")).collect::<Vec<_>>().join("\n");
    let second=["139.0","138.0","137.0","136.0","135.0","134.0","133.0","132.0"].iter().map(|v|format!("- Firefox {v} 正式发布，核验mozilla.org官方发布日期。")).collect::<Vec<_>>().join("\n");
    let parent=TransportRequest {endpoint:"https://api.relaycore.cn/v1/messages".into(),provider_id:"RELAYCORE_CLAUDE".into(),model_id:"claude-sonnet-5-5".into(),messages:json!([{"role":"user","content":[{"type":"text","text":"独立公开Go与Firefox两文件核验，逐项真实搜索官方发布日期，中文简短回答并引用来源。"},{"type":"text","text":first},{"type":"text","text":second}]}]),idempotency_key:format!("public-recorded-initial-recovery-{}-route-rebind-1",crate::system_now_millis()),max_output_tokens:8192,web_search_route:"RELAYCORE_COMPANION".into(),structured_json:false,disable_thinking:false,grounded_answer:false,explicit_search_query:None};
    let plan=scope::from_messages(&parent.messages);assert_eq!(plan.items.len(),16);let request=batch_request(&parent,&plan,2).unwrap();
    let shape=public_wire_shape(&chat::transport_body(&request));assert_eq!(shape["choiceValue"],"required");assert_eq!(shape["toolsCount"],1);assert_eq!(shape["tools"][0]["externalWebAccess"],true);
    let store=AppPrivateProviderCredentialStore::at(std::env::var("NFAI_CREDENTIAL_ROOT").unwrap());let key=store.with_secret("RELAYCORE_GPT",|v|Ok(Zeroizing::new(v.to_vec()))).unwrap();
    let result=chat::execute_streaming(request,key,Arc::new(AtomicBool::new(false)),|_|Ok(())).await;
    let record=match result {Ok(c)=>json!({"initialRecordedExplicitRejection":true,"recoveryCalls":1,"initialFee":"unknown","queryCount":plan.items.len(),"requestShape":shape,"webProof":c.web_search_verified,"sources":c.native_sources.len(),"returned":c.actual_model_id,"evidenceValid":evidence(&plan,&c).is_ok(),"input":c.usage.input_tokens,"output":c.usage.output_tokens,"elapsedMs":c.elapsed_ms,"publicFixtureReply":c.text}),Err(e)=>json!({"initialRecordedExplicitRejection":true,"recoveryCalls":1,"initialFee":"unknown","requestShape":shape,"error":format!("{e:?}")})};
    std::fs::write(&output,serde_json::to_vec_pretty(&record).unwrap()).unwrap();assert_eq!(record["webProof"],true);assert_eq!(record["evidenceValid"],true);assert_eq!(record["returned"],"gpt-6.1-sol");
}
