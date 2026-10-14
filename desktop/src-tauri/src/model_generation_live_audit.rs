//! Explicit opt-in: sequential public synthetic calls, scoped credentials, metadata-only evidence.
use crate::{desktop_model_service_v1 as catalog,desktop_ordinary_chat_v1 as chat,chat_call_accounting_v1 as pricing};
use catalog::ProviderCredentialStore;
use serde_json::{json,Value};
use std::{io::Write,sync::{atomic::AtomicBool,Arc},time::{Instant,SystemTime,UNIX_EPOCH}};
use zeroize::Zeroizing;
fn now()->i64 {SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_millis() as i64}
async fn credential(store:&catalog::AppPrivateProviderCredentialStore,id:&str)->Zeroizing<Vec<u8>> {
 if !id.starts_with("RELAYCORE_"){return store.with_secret(id,|key|Ok(Zeroizing::new(key.to_vec()))).expect("configured credential unavailable")}
 let path=std::env::var("NFAI_AUDIT_RELAY_CONTROL").expect("authorized temporary memory receiver required");
 let control:Value=serde_json::from_slice(&std::fs::read(path).unwrap()).unwrap();
 let base=control["url"].as_str().unwrap();let url=reqwest::Url::parse(base).unwrap();
 assert_eq!(url.host_str(),Some("127.0.0.1"));assert_eq!(url.scheme(),"http");
 let client=reqwest::Client::builder().redirect(reqwest::redirect::Policy::none()).build().unwrap();
 let response=client.get(format!("{base}/secret/{id}")).bearer_auth(control["cap"].as_str().unwrap()).send().await.unwrap();
 assert_eq!(response.status(),200);Zeroizing::new(response.bytes().await.unwrap().to_vec())
}
fn local_cost(reply:&chat::Completed,id:&str,name:&str,provider:&str,at:i64)->Option<f64>{
 if let Some(amount)=reply.reported_cost_micros {
  let currency=pricing::provider_billing_currency(provider)?;
  return Some(amount as f64/1e6/if currency=="CNY"{6.72}else{1.});
 }
 let id=pricing::pricing_model_id(id,reply.actual_model_id.as_deref());
 let cost=pricing::estimate(&id,name,reply.usage.input_tokens,reply.usage.output_tokens,reply.usage.cached_input_tokens,at)?;
 Some(cost.micros as f64/1e6/if cost.currency=="CNY"{6.72}else{1.})
}
#[tokio::test]
#[ignore="requires fresh explicit $1 authorization; public synthetic only, never automatic"]
async fn configured_model_generation_audit(){
 assert_eq!(std::env::var("NFAI_LIVE_MODEL_AUDIT").as_deref(),Ok("authorized-synthetic-only-budget-1.00"));
 let workspace=std::env::var("NFAI_AUDIT_CREDENTIAL_ROOT").expect("explicit installed credential root");
 let output=std::env::var("NFAI_AUDIT_OUTPUT").expect("metadata evidence path");
 struct Lock(std::path::PathBuf);impl Drop for Lock{fn drop(&mut self){let _=std::fs::remove_dir(&self.0);}}
 let lock=std::path::PathBuf::from(format!("{output}.lock"));std::fs::create_dir(&lock).expect("another paid verification is active; not sent");let _guard=Lock(lock);
 let filter=std::env::var("NFAI_AUDIT_MODELS").unwrap_or_default();
 let web=std::env::var("NFAI_AUDIT_WEB").as_deref()==Ok("1");
 let store=catalog::AppPrivateProviderCredentialStore::at(workspace);
 let old=std::fs::read_to_string(&output).unwrap_or_default();
 let mut spent:f64=old.lines().map(|line|serde_json::from_str::<Value>(line).unwrap()["budgetChargeUsd"].as_f64().unwrap_or(0.)).sum();
 let mut options=std::fs::OpenOptions::new();options.create(true).append(true);
 #[cfg(unix)] {use std::os::unix::fs::OpenOptionsExt;options.mode(0o600);}
 let mut file=options.open(&output).unwrap();
 for preset in catalog::chat_presets().into_iter().filter(|p|p.chat_selectable){
  if !filter.is_empty() && !filter.split(',').any(|id|id==preset.id){continue}
  let reserve=if web && ["QWEN_3_8_MAX","RELAYCORE_SONNET_5_5"].contains(&preset.id){0.04}else if web && preset.id=="QWEN_3_6_FLASH"{0.01}else if web{0.20}else{0.05};
  if spent+reserve>1. {writeln!(file,"{}",json!({"preset":preset.id,"state":"BUDGET_STOP","budgetChargeUsd":0})).unwrap();break}
  let provider=catalog::provider(preset.provider_id).unwrap();
  let route=crate::ordinary_chat_web_search_route(provider.id,preset.model_id,web,false);
  let endpoint=chat::provider_endpoint(provider.endpoint,route);
  let at=now();let request=chat::TransportRequest{endpoint,provider_id:provider.id.into(),model_id:preset.model_id.into(),messages:if web{json!([{"role":"user","content":"请查询Python官方网站，用一句话介绍Python并附官网来源链接。"}])}else{json!([{"role":"system","content":"这是公开合成的功能校验。按用户要求简短回答。"},{"role":"user","content":"本轮校验词是NF42。"},{"role":"assistant","content":"已记住本轮校验词。"},{"role":"user","content":"只回复前面给出的校验词，不解释。"}])},idempotency_key:format!("public-model-verification-{}-{at}-{web}",preset.id),max_output_tokens:2048,web_search_route:route.into(),structured_json:false,disable_thinking: false, grounded_answer:false,explicit_search_query:None};
  let secret=credential(&store,provider.id).await;
  let mut child:Option<chat::Completed>=None;let started=Instant::now();
  let result=if route=="RELAYCORE_COMPANION"{
   crate::relaycore_web_search_v1::execute_companion(request,secret,credential(&store,"RELAYCORE_GPT").await,Arc::new(AtomicBool::new(false)),|reply|{if let Some(reply)=reply{child=Some(reply.clone())}Ok(())},||Ok(()),|_|Ok(())).await
  }else{chat::execute_streaming(request,secret,Arc::new(AtomicBool::new(false)),|_|Ok(())).await};
  let child_fee=child.as_ref().and_then(|r|local_cost(r,"gpt-6.1-sol","GPT-6.1 Sol · Pro3","RELAYCORE_GPT",at));
  let mut fact=json!({"preset":preset.id,"provider":provider.id,"requestedModel":preset.model_id,"web":web,"route":route,"elapsedMs":started.elapsed().as_millis(),"requestedAtMs":at});
  let amount=match result{
   Ok(reply)=>{let answer_ok=if web{reply.web_search_verified && reply.text.contains("python.org")}else{reply.text.trim()=="NF42"};fact["state"]=json!(if answer_ok{"PASSED"}else{"ANSWER_OR_SEARCH_NOT_VERIFIED"});fact["usage"]=json!(reply.usage);fact["actualModel"]=json!(reply.actual_model_id);fact["searchVerified"]=json!(reply.web_search_verified);fact["visibleChars"]=json!(reply.text.chars().count());fact["childUsage"]=json!(child.as_ref().map(|r|&r.usage));fact["reportedCostMicros"]=json!(reply.reported_cost_micros);let main=local_cost(&reply,preset.model_id,preset.display_name,provider.id,at);fact["estimatedMainUsd"]=json!(main);fact["estimatedRetrievalUsd"]=json!(child_fee);main.and_then(|m|if route=="RELAYCORE_COMPANION"{child_fee.map(|c|m+c)}else{Some(m)}).unwrap_or(reserve)},
   Err(error)=>{fact["state"]=json!("FAILED_OR_UNKNOWN");fact["safeError"]=json!(format!("{error:?}"));fact["childUsage"]=json!(child.as_ref().map(|r|&r.usage));fact["retrievalCompleted"]=json!(child.is_some());reserve}
  };
  spent+=amount;fact["budgetChargeUsd"]=json!(amount);fact["budgetUsedUsd"]=json!(spent);
  writeln!(file,"{fact}").unwrap();file.flush().unwrap();
  eprintln!("MODEL_VERIFICATION {} {} {}ms budget={:.4}",preset.id,fact["state"],started.elapsed().as_millis(),spent);
  assert!(spent<=1.,"approved budget reached; no further calls");
 }
}
