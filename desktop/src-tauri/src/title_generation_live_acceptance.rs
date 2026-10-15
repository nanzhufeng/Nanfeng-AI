//! Explicit opt-in, public title samples only. No production database writes or raw payload logs.
use crate::*;
use desktop_model_service_v1::{ProviderCredentialStore, AppPrivateProviderCredentialStore};
use serde_json::json;
use zeroize::Zeroize;
use std::{io::{Read,Write},net::TcpListener,time::{Duration,Instant}};

#[test]
#[ignore="explicit authorization required; real title calls, <=6 calls per platform, total budget CNY3"]
fn authorized_titles_and_android_one_shot_bridge() {
    assert_eq!(std::env::var("NFAI_TITLE_LIVE").as_deref(),Ok("public-titles-budget-cny3"));
    let output=PathBuf::from(std::env::var("NFAI_TITLE_OUTPUT").unwrap());
    assert!(!output.exists(),"never auto-repeat an unknown paid run");
    let source=AppPrivateProviderCredentialStore::at(std::env::var("NFAI_TITLE_CREDENTIAL_ROOT").unwrap());
    let mut rows=Vec::new();let mut sent=0;
    for case in ["qwen-primary","deepseek-only","qwen-timeout-backup"] {
        let (_dir,mut store,imported)=tests::imported_store();
        store.credentials=AppPrivateProviderCredentialStore::at(store.root.join("title-only-credentials"));
        for provider in ["QWEN","DEEPSEEK"] {
            let key=source.with_secret(provider,|key|Ok(Zeroizing::new(key.to_vec()))).unwrap();
            store.credentials.save_user_provided_secret(provider,&key).unwrap();
            let current=store.read_desktop_model_service_setting_records().unwrap().into_iter().find(|s|s.provider_id==provider).unwrap();
            let enabled=if case=="qwen-primary" {provider=="QWEN"} else if case=="deepseek-only" {provider=="DEEPSEEK"} else {true};
            store.save_desktop_model_service_setting_record(provider,enabled,desktop_model_service_v1::provider(provider).unwrap().default_preset_id,current.revision).unwrap();
        }
        let prepared=store.prepare_ordinary_chat(DesktopOrdinaryChatSubmitArgs{
            workspace_id:imported.summary.id.clone(),conversation_id:None,project_id:None,expected_revision:None,
            text:"请帮我整理应用设置页面的层级".into(),attachment_ids:vec![],model_id:None,tone_override:None,web_search_override:Some(false),egress_authorization:None,
        },Some("http://127.0.0.1:1/chat/completions")).unwrap();
        store.update_ordinary_chat_message(&prepared.projection.attempt_id,"可以按功能分类和使用频率整理设置页面。","COMPLETE",None,None,None).unwrap();
        let root=store.root.clone();let credentials=store.credentials.clone();let owner=Mutex::new(store);
        let mut physical=Vec::new();let mut applied=0;
        run_desktop_conversation_title_work(&owner,&credentials,&imported.summary.id,&prepared.projection.conversation_id,true,|request,key| {
            let start=Instant::now();
            if case=="qwen-timeout-backup" && request.provider_id=="QWEN" {
                physical.push(json!({"provider":"QWEN","model":request.model_id,"faultInjected":true,"error":"TIMEOUT","sent":false}));
                return Err(desktop_ordinary_chat_v1::Failure::Unknown{code:"TIMEOUT"});
            }
            sent+=1;assert!(sent<=6,"paid call ceiling reached");
            assert_eq!(request.max_output_tokens,256);assert!(request.disable_thinking && request.structured_json);
            let provider=request.provider_id.clone();let model=request.model_id.clone();
            let result=tauri::async_runtime::block_on(desktop_ordinary_chat_v1::execute_non_streaming(request,key));
            let mut row=json!({"provider":provider,"model":model,"faultInjected":false,"sent":true,"elapsedMs":start.elapsed().as_millis()});
            match &result {
                Ok(reply)=>{row["status"]=json!("COMPLETED");row["returnedModel"]=json!(reply.actual_model_id);row["inputTokens"]=json!(reply.usage.input_tokens);row["outputTokens"]=json!(reply.usage.output_tokens);row["cachedInputTokens"]=json!(reply.usage.cached_input_tokens);row["titleContractValid"]=json!(desktop_conversation_title_v1::parse_response(&reply.text).is_some());}
                Err(error)=>{let (state,code,http)=match error {desktop_ordinary_chat_v1::Failure::Unknown{code}=>("UNKNOWN",*code,None),desktop_ordinary_chat_v1::Failure::Cancelled=>("CANCELLED","CANCELLED",None),desktop_ordinary_chat_v1::Failure::Explicit{code,http_status}|desktop_ordinary_chat_v1::Failure::Quantified{code,http_status,..}=>("FAILED",*code,*http_status)};row["status"]=json!(state);row["error"]=json!(code);row["http"]=json!(http);}
            }
            physical.push(row);result
        },||{applied+=1});
        let ledger=usage_ledger_v1::Ledger::open(&root.join("usage-ledger")).unwrap().all_entries().unwrap().into_iter().filter(|e|e.entry_id.starts_with("usage-title-")).map(|e|json!({"model":e.requested_model_id,"inputTokens":e.input_tokens,"outputTokens":e.output_tokens,"chargeMicros":e.charge_micros,"currency":e.currency_code,"costSource":e.cost_source})).collect::<Vec<_>>();
        drop(owner);let reopened=DesktopWorkspaceStore::open(root).unwrap();
        let raw:String=reopened.connection().unwrap().query_row("SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?1",[&imported.summary.id],|r|r.get(0)).unwrap();
        let exchange:Value=serde_json::from_str(&raw).unwrap();let conversation=exchange["conversations"].as_array().unwrap().iter().find(|c|c["id"]==prepared.projection.conversation_id).unwrap();
        let valid=desktop_conversation_title_v1::parse_response(&json!({"title":conversation["title"]}).to_string()).is_some();
        rows.push(json!({"platform":"Mac native Rust title owner","case":case,"mainAnswerSynthetic":true,"physical":physical,"appliedCallbacks":applied,"reopenedTitle":conversation["title"],"pending":conversation["autoTitlePending"],"titleValid":valid,"ledger":ledger}));
        fs::write(&output,serde_json::to_vec_pretty(&json!({"rows":rows,"paidCalls":sent})).unwrap()).unwrap();
    }
    // Optional one-shot memory-only credential transfer to the isolated Android acceptance app.
    if let Ok(control_path)=std::env::var("NFAI_TITLE_BRIDGE_CONTROL") {
        let control:Value=serde_json::from_slice(&fs::read(&control_path).unwrap()).unwrap();
        let port=control["port"].as_u64().unwrap() as u16;
        let listener=TcpListener::bind(("127.0.0.1",port)).unwrap();listener.set_nonblocking(true).unwrap();
        fs::write(format!("{control_path}.ready"),b"ready").unwrap();let deadline=Instant::now()+Duration::from_secs(180);
        loop {
            match listener.accept() {
                Ok((mut stream,_))=>{
                    stream.set_read_timeout(Some(Duration::from_secs(10))).unwrap();let mut token=String::new();
                    loop {let mut b=[0u8;1];stream.read_exact(&mut b).unwrap();if b[0]==b'\n'{break}token.push(b[0] as char);assert!(token.len()<=128);}
                    assert_eq!(token,control["token"].as_str().unwrap());
                    let mut payload=serde_json::Map::new();
                    for id in ["QWEN","DEEPSEEK"] {let secret=source.with_secret(id,|key|Ok(Zeroizing::new(key.to_vec()))).unwrap();payload.insert(id.into(),Value::String(String::from_utf8(secret.to_vec()).unwrap()));}
                    let mut bytes=Zeroizing::new(serde_json::to_vec(&payload).unwrap());payload.clear();stream.write_all(&bytes).unwrap();bytes.zeroize();break;
                }
                Err(e) if e.kind()==std::io::ErrorKind::WouldBlock=>{assert!(Instant::now()<deadline,"Android credential handoff expired");std::thread::sleep(Duration::from_millis(100));}
                Err(e)=>panic!("local credential handoff failed: {}",e.kind()),
            }
        }
    }
    eprintln!("TITLE_NATIVE_ACCEPTANCE_DONE cases={} paid_calls={}",rows.len(),sent);
}

/// One-shot OpenRouter credential transfer for the independent Haiku image acceptance only.
#[test]
#[ignore="explicit Haiku public-image acceptance authorization; memory-only bridge"]
fn authorized_haiku_credential_bridge() {
    assert_eq!(std::env::var("NFAI_HAIKU_LIVE").as_deref(),Ok("public-image-budget-cny3"));
    let source=AppPrivateProviderCredentialStore::at(std::env::var("NFAI_CREDENTIAL_ROOT").unwrap());
    let path=std::env::var("NFAI_HAIKU_BRIDGE_CONTROL").unwrap();
    let control:Value=serde_json::from_slice(&fs::read(&path).unwrap()).unwrap();
    let listener=TcpListener::bind(("127.0.0.1",control["port"].as_u64().unwrap() as u16)).unwrap();
    listener.set_nonblocking(true).unwrap();fs::write(format!("{path}.ready"),b"ready").unwrap();
    let deadline=Instant::now()+Duration::from_secs(180);
    loop {
        match listener.accept() {
            Ok((mut stream,_))=>{
                stream.set_read_timeout(Some(Duration::from_secs(10))).unwrap();let mut token=String::new();
                loop {let mut b=[0u8;1];stream.read_exact(&mut b).unwrap();if b[0]==b'\n'{break}token.push(b[0] as char);assert!(token.len()<=128);}
                assert_eq!(token,control["token"].as_str().unwrap());
                let secret=source.with_secret("OPENROUTER",|key|Ok(Zeroizing::new(key.to_vec()))).unwrap();
                let payload=json!({"OPENROUTER":String::from_utf8(secret.to_vec()).unwrap()});
                let bytes=Zeroizing::new(serde_json::to_vec(&payload).unwrap());stream.write_all(&bytes).unwrap();break;
            }
            Err(e) if e.kind()==std::io::ErrorKind::WouldBlock=>{assert!(Instant::now()<deadline,"bridge expired");std::thread::sleep(Duration::from_millis(100));}
            Err(e)=>panic!("bridge failed: {}",e.kind()),
        }
    }
}
