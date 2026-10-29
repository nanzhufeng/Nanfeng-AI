//! Quantitative metadata only. Provider money is authoritative; tariff calculations are estimates.
#[cfg(test)]
#[test]
fn argon_cached_tariff_is_a_versioned_usd_estimate() {
    let cost = estimate("google/gemini-4-argon", "Gemini 4 Argon", Some(1000), Some(100), Some(500), 1792022400000).unwrap();
    assert_eq!((cost.micros, cost.currency.as_str(), cost.version.as_str()), (2050, "USD", "openrouter-gemini-4-argon-2026-10-15-v1"));
    assert!(estimate("google/gemini-4-argon", "", None, Some(100), Some(500), 1792022400000).is_none());
}
use serde::{Deserialize, Serialize};

#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all="camelCase", deny_unknown_fields)]
pub struct Metadata {
    pub role: String,
    pub model_display_name: String,
    pub reasoning_tokens: Option<i64>,
    pub price_version: Option<String>,
    pub context_window_tokens: Option<i64>,
    pub context_source_count: Option<i64>,
    #[serde(default,skip_serializing_if="Option::is_none")]
    pub service_id:Option<String>,
    #[serde(default,skip_serializing_if="Option::is_none")]
    pub service_engine_id:Option<String>,
    #[serde(default,skip_serializing_if="Option::is_none")]
    pub route:Option<ModelCallRoute>,
}
/// A local frozen request route, never proof of an opaque provider's downstream execution.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all="SCREAMING_SNAKE_CASE")]
pub enum ModelCallChannel { Kiro, KiroStandard, Pro1, Pro2, Pro3, Gemini }
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all="camelCase",deny_unknown_fields)]
pub struct ModelCallRoute {
    pub provider_id:String,
    pub channel_id:Option<ModelCallChannel>,
}
impl ModelCallRoute {
    /// Only the original request label may provide an advertised channel. Never consult settings.
    pub fn capture(provider:&str, frozen_display:&str)->Result<Self,String> {
        use ModelCallChannel::*;
        let channel_id=match (provider,frozen_display.rsplit_once(" · ").map(|(_,c)|c)) {
            ("RELAYCORE_CLAUDE",Some("Kiro"))=>Some(Kiro),
            ("RELAYCORE_CLAUDE",Some("Kiro正价"))=>Some(KiroStandard),
            ("RELAYCORE_GPT",Some("Pro1"))=>Some(Pro1),
            ("RELAYCORE_GPT",Some("Pro2"))=>Some(Pro2),
            ("RELAYCORE_GPT",Some("Pro3"))=>Some(Pro3),
            ("RELAYCORE_GEMINI",Some("Gemini"))=>Some(Gemini),
            _=>None,
        };
        let route=Self{provider_id:provider.into(),channel_id};route.validate()?;Ok(route)
    }
    pub fn validate(&self)->Result<(),String> {
        use ModelCallChannel::*;
        let provider=self.provider_id.as_str();
        // This is an append-only fact vocabulary, not the active provider settings list.
        if !matches!(provider,"OPENROUTER"|"QWEN"|"DEEPSEEK"|"ZHIPU"|"RELAYCORE_CLAUDE"|"RELAYCORE_GPT"|"RELAYCORE_GEMINI") {
            return Err("Invalid frozen call provider".into());
        }
        let valid=match self.channel_id.as_ref() {
            None=>true,
            Some(Kiro|KiroStandard)=>provider=="RELAYCORE_CLAUDE",
            Some(Pro1|Pro2|Pro3)=>provider=="RELAYCORE_GPT",
            Some(Gemini)=>provider=="RELAYCORE_GEMINI",
        };
        if !valid {return Err("Frozen call channel does not belong to its provider".into());}
        Ok(())
    }
}
impl Metadata {
    pub fn validate(&self) -> Result<(), String> {
        if let Some(route)=&self.route {
            route.validate()?;
            if self.service_id.is_some() || self.service_engine_id.is_some() {return Err("A legacy service identity is not a model route".into());}
        }
        if self.service_id.is_some() && (self.role!="RETRIEVAL" || self.service_id.as_deref()!=Some("ZHIPU_WEB_SEARCH") || self.service_engine_id.as_deref()!=Some("search_pro")) {
            return Err("Invalid search service metadata".into());
        }
        if !["ANSWER","RETRIEVAL","MATERIAL_READ","BACKGROUND"].contains(&self.role.as_str())
            || self.model_display_name.len()>240 || self.model_display_name.chars().any(char::is_control)
            || self.price_version.as_ref().is_some_and(|s| s.len()>180 || s.chars().any(char::is_control))
            || [self.reasoning_tokens,self.context_window_tokens,self.context_source_count].iter().flatten().any(|n| *n<0) {
            return Err("Invalid quantitative call metadata".into());
        }
        Ok(())
    }
}
/// Local model children share receipt/recovery semantics; service search is checked separately.
pub fn is_model_child_role(role:&str)->bool {matches!(role,"RETRIEVAL"|"MATERIAL_READ")}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Cost { pub micros:i64, pub currency:String, pub version:String }

pub fn provider_billing_currency(provider:&str)->Option<&'static str> {
    match provider {"OPENROUTER"=>Some("USD"),"DEEPSEEK"|"QWEN"|"ZHIPU"=>Some("CNY"),_=>None}
}

pub fn pricing_model_id(requested:&str, actual:Option<&str>)->String {
    match actual {
        None=>requested.into(),
        Some(actual) if !actual.contains('/') && requested.contains('/') =>format!("{}/{actual}",requested.split('/').next().unwrap_or_default()),
        Some(actual)=>actual.into(),
    }
}

pub fn estimate(id:&str, display:&str, input:Option<i64>, output:Option<i64>, cached:Option<i64>, at:i64) -> Option<Cost> {
    let (input,output)=(input?,output?);
    if input<0 || output<0 || cached.is_some_and(|v|v<0) || at<0 {return None}
    let current=at>=1791590400000; // 2026-10-10 UTC. Historical tariff facts remain untouched.
    let long=input>256000;
    let large=input>271999;
    let (a,b,c,currency,version):(f64,f64,Option<f64>,&str,String)=match id {
        "claude-opus-5-5"|"claude-opus-5.5"|"claude-sonnet-5-5"|"gpt-6.1-sol"|"gpt-6-astra"|"gemini-3.8-flash"|"gemini-3.1-pro" => {
            let channel=display.split(" · ").nth(1).or_else(||current.then_some(match id {"claude-opus-5-5"|"claude-opus-5.5"|"claude-sonnet-5-5"=>"Kiro正价","gpt-6.1-sol"|"gpt-6-astra"=>"Pro3",_=>"Gemini"}))?;
            let m=match channel {"Kiro"=>0.08,"Kiro正价"=>0.35,"Pro1"=>0.12,"Pro2"=>0.18,"Pro3"=>0.25,"Gemini"=>0.28,_=>return None};
            let (a,b,c)=match id {"claude-opus-5-5"|"claude-opus-5.5"=>(4.,20.,0.2),"claude-sonnet-5-5"=>(2.,10.,0.2),"gpt-6.1-sol"=>(2.,10.,0.1),"gpt-6-astra"=>(10.,50.,1.),"gemini-3.8-flash"=>(0.75,3.75,0.075),_=>(2.,12.,0.2)};
            (a*m,b*m,Some(c*m),"CNY",format!("relaycore-cny-nominal-user-confirmed-2026-10-10-{channel}"))
        }
        "qwen3.7-plus"|"qwen3.6-flash" if current && input<=1000000 => {
            let (a,b)=if id=="qwen3.7-plus" {if long {(6.,24.)} else {(2.,8.)}} else if long {(4.8,28.8)} else {(1.2,7.2)};
            (a,b,Some(a*0.2),"CNY","qwen-cn-beijing-published-standard-2026-10-10".into())
        }
        "qwen3.8-max"=>(12.,36.,Some(if current {2.4}else{1.5}),"CNY",if current {"qwen-cn-beijing-published-standard-2026-10-10"} else {"qwen-cn-beijing-standard-2026-08-v2"}.into()),
        "qwen3.7-plus" if input<=1000000=>{let (a,b)=if long {(0.826,3.301)} else {(0.276,1.101)};(a,b,None,"USD","qwen-cn-beijing-standard-2026-08-v2".into())},
        "qwen3.6-flash" if input<=1000000=>{let (a,b)=if long {(0.66,3.961)} else {(0.165,0.99)};(a,b,None,"USD","qwen-cn-beijing-standard-2026-08-v2".into())},
        "deepseek-flash"|"deepseek-v4-pro"|"deepseek-v4-flash"=>{
            let day=at/86400000; let weekday=(day+3)%7<5; let hour=at/3600000%24;
            let peak=weekday && ((1..4).contains(&hour)||(6..10).contains(&hour));
            if id=="deepseek-v4-flash" && at>=1788998400000{return None}
            let (a,b,c,v)=if id=="deepseek-flash" {if peak {(0.3,1.2,0.006,"deepseek-v4.1-flash-peak-2026-09-10-v1")} else {(0.15,0.6,0.003,"deepseek-v4.1-flash-off-peak-2026-09-10-v1")}}
            else if at<1786896000000 {if id=="deepseek-v4-pro" {(0.435,0.87,0.003625,"deepseek-public-prices-before-2026-08-17-v1")} else {(0.14,0.28,0.0028,"deepseek-public-prices-before-2026-08-17-v1")}}
            else if id=="deepseek-v4-pro" {if peak {(1.32,3.96,0.044,"deepseek-peak-2026-08-v2")}else{(0.66,1.98,0.022,"deepseek-off-peak-2026-08-v2")}}
            else if peak {(0.44,1.32,0.014,"deepseek-peak-2026-08-v2")}else{(0.22,0.66,0.007,"deepseek-off-peak-2026-08-v2")};
            (a,b,Some(c),"USD",v.into())
        }
        "glm-5.3-flash"=>if at<1788192000000 {(0.4,1.4,Some(0.115),"CNY","zhipu-glm-5.3-flash-promo-2026-08-v1".into())}else{(0.8,2.8,Some(0.23),"CNY","zhipu-glm-5.3-flash-standard-2026-08-v1".into())},
        _=>{
            let v="openrouter-public-prices-2026-10-09-v1";
            let (a,b,c)=match id {
                "anthropic/claude-opus-5.5"=>(4.,20.,Some(0.2)),"anthropic/claude-sonnet-5.5"=>(2.,10.,Some(0.1)),
                "anthropic/claude-haiku-5.5"=>if input<=99999 {(0.1,0.5,Some(0.01))}else{(0.5,2.5,Some(0.05))},
                "openai/gpt-6.1-sol"=>if large {(4.,15.,Some(0.2))}else{(2.,10.,Some(0.1))},
                "openai/gpt-6-luna"=>if large {(0.2,0.75,Some(0.02))}else{(0.1,0.5,Some(0.01))},
                "inception/mercury-3"=>(0.1,0.35,Some(0.01)),
                "openai/gpt-6.1-terra"=>if large {(2.,7.5,Some(0.1))}else{(1.,5.,Some(0.05))},
                "meta/muse-wonder"=>(4.,20.,Some(0.2)),
                "google/gemini-4-argon"=>(2.,10.,Some(0.1)),
                "google/gemini-3.8-flash"=>(0.75,3.75,Some(0.075)),"moonshotai/kimi-k3"=>(2.55,12.75,Some(0.256)),
                "x-ai/grok-4.6"=>if input>199999 {(4.,12.,Some(1.))}else{(2.,6.,Some(0.5))},
                "anthropic/claude-fable-5"|"anthropic/claude-opus-5:fast"=>(10.,50.,None),"anthropic/claude-opus-5"=>(5.,25.,None),"anthropic/claude-sonnet-5"=>(2.,10.,None),"anthropic/claude-haiku-4.5"=>(1.,5.,None),"google/gemini-3.7-flash"=>(0.375,1.875,None),
                "openai/gpt-5.6-sol"=>if large {(4.,15.,None)}else{(2.,10.,None)},"openai/gpt-5.6-terra"=>if large {(4.,18.,None)}else{(2.,12.,None)},"openai/gpt-5.6-luna"=>if large {(0.4,1.8,None)}else{(0.2,1.2,None)},_=>return None};
            let v=if id=="inception/mercury-3" {"openrouter-mercury-3-2026-10-20-v1"}else if id=="openai/gpt-6.1-terra" {"openrouter-public-prices-2026-10-27-v1"}else if id=="google/gemini-4-argon" {"openrouter-gemini-4-argon-2026-10-15-v1"}else if id=="meta/muse-wonder" {"openrouter-muse-wonder-2026-10-13-v1"}else if id=="google/gemini-3.8-flash" {"openrouter-gemini-3.8-flash-intro-2026-09-v1"}else if id.starts_with("openai/gpt-5.6") || ["moonshotai/kimi-k3","x-ai/grok-4.6","anthropic/claude-fable-5","anthropic/claude-opus-5:fast","anthropic/claude-opus-5","anthropic/claude-sonnet-5","anthropic/claude-haiku-4.5","google/gemini-3.7-flash"].contains(&id){"openrouter-public-prices-2026-08-v1"}else{v};
            (a,b,c,"USD",v.into())
        }
    };
    let cached=cached.unwrap_or(0).min(input);
    let amount=((input-cached) as f64*a+cached as f64*c.unwrap_or(a)+output as f64*b).round();
    if !amount.is_finite() || amount>=i64::MAX as f64{return None}
    Some(Cost{micros:amount as i64,currency:currency.into(),version})
}
#[cfg(test)] mod tests {
    use super::*;
    #[test] fn model_prices_and_unknown_facts_are_honest(){
        assert_eq!(estimate("claude-sonnet-5-5","Sonnet 5.5 · Kiro正价",Some(1000),Some(100),Some(500),1791590400000).unwrap().micros,735);
        assert_eq!(estimate("claude-sonnet-5-5","Sonnet 5.5 · Kiro正价",Some(1000),Some(100),Some(500),1791590400000).unwrap().currency,"CNY");
        assert_eq!(estimate("qwen3.7-plus","",Some(1000000),Some(1000000),None,1791590400000).unwrap().micros,30000000);
        assert!(estimate("glm-5.3","",Some(1),Some(1),None,1791590400000).is_none());
        assert!(estimate("gpt-6.1-sol","",None,Some(0),None,1791590400000).is_none());
        assert_eq!(estimate("gpt-6.1-sol","GPT-6.1 Sol · Pro1",Some(0),Some(0),None,1791590400000).unwrap().micros,0);
        assert!(estimate("gpt-6.1-sol","",Some(1),Some(1),None,1781590400000).is_none());
    }
}

#[cfg(test)]
#[test]
fn published_opus_aliases_keep_same_standard_group_cny_estimate() {
    let old=estimate("claude-opus-5-5","Opus 5.5 · Kiro正价",Some(100),Some(20),None,1791676800000).unwrap();
    let current=estimate("claude-opus-5.5","Opus 5.5 · Kiro正价",Some(100),Some(20),None,1791676800000).unwrap();
    assert_eq!((old.micros,old.currency,old.version),(current.micros,current.currency,current.version));
}


/// Execution outcome is distinct from quantitative settlement and verification of evidence.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all="SCREAMING_SNAKE_CASE")]
pub enum CallOutcomeState { Completed, Failed, Cancelled, Unknown }
impl CallOutcomeState {
    pub fn as_str(self)->&'static str {match self {Self::Completed=>"COMPLETED",Self::Failed=>"FAILED",Self::Cancelled=>"CANCELLED",Self::Unknown=>"UNKNOWN"}}
}
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all="SCREAMING_SNAKE_CASE")]
pub enum CallOutcomeOrigin { LocalExecutionResult, CancelledBeforeDispatch, RecoveredInterruption }
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all="camelCase",deny_unknown_fields)]
pub struct CallOutcome {
    pub state:CallOutcomeState,
    pub safe_error_code:Option<String>,
    pub model_elapsed_ms:Option<i64>,
    pub origin:CallOutcomeOrigin,
}
impl CallOutcome {
    pub fn validate(&self)->Result<(),String> {
        if self.model_elapsed_ms.is_some_and(|n|n<0)
            || self.safe_error_code.as_ref().is_some_and(|s|s.is_empty() || s.len()>80 || !s.bytes().all(|b|b.is_ascii_uppercase()||b.is_ascii_digit()||b==b'_'))
            || ((self.state==CallOutcomeState::Completed)!=self.safe_error_code.is_none()) {
            return Err("Invalid local call outcome".into());
        }
        match self.origin {
            CallOutcomeOrigin::CancelledBeforeDispatch if self.state!=CallOutcomeState::Cancelled || self.safe_error_code.as_deref()!=Some("CANCELLED_BEFORE_SEND") || self.model_elapsed_ms.is_some()=>Err("Invalid undispatched call outcome".into()),
            CallOutcomeOrigin::RecoveredInterruption if self.state!=CallOutcomeState::Unknown || self.safe_error_code.as_deref()!=Some("PROCESS_INTERRUPTED") || self.model_elapsed_ms.is_some()=>Err("Invalid recovered call outcome".into()),
            _=>Ok(()),
        }
    }
    pub fn from_result(result:&Result<crate::desktop_ordinary_chat_v1::Completed,crate::desktop_ordinary_chat_v1::Failure>)->Self {
        use crate::desktop_ordinary_chat_v1::Failure;
        let (state,code,elapsed)=match result {
            Ok(reply)=>(CallOutcomeState::Completed,None,Some(reply.elapsed_ms)),
            Err(failure)=>{
                let (state,code)=match failure {
                    Failure::Explicit{code,..}|Failure::MeasuredExplicit{code,..}=>(CallOutcomeState::Failed,*code),
                    Failure::Unknown{code}|Failure::MeasuredUnknown{code,..}=>(CallOutcomeState::Unknown,*code),
                    Failure::Cancelled|Failure::MeasuredCancelled{..}=>(CallOutcomeState::Cancelled,"CANCELLED"),
                };
                (state,Some(code.to_owned()),failure.quantitative_completed().map(|reply|reply.elapsed_ms))
            }
        };
        Self{state,safe_error_code:code,model_elapsed_ms:elapsed,origin:CallOutcomeOrigin::LocalExecutionResult}
    }
    pub fn cancelled_before_dispatch()->Self {Self{state:CallOutcomeState::Cancelled,safe_error_code:Some("CANCELLED_BEFORE_SEND".into()),model_elapsed_ms:None,origin:CallOutcomeOrigin::CancelledBeforeDispatch}}
    pub fn interrupted()->Self {Self{state:CallOutcomeState::Unknown,safe_error_code:Some("PROCESS_INTERRUPTED".into()),model_elapsed_ms:None,origin:CallOutcomeOrigin::RecoveredInterruption}}
}


#[cfg(test)]
mod call_outcome_type_tests {
    use super::*;
    use crate::desktop_ordinary_chat_v1::{Completed,Failure,Usage,ReportedMeasurement};
    #[test]
    fn call_outcome_is_explicit_execution_result_not_presence_of_quantities() {
        let reply=Completed{text:"synthetic".into(),reasoning:None,usage:Usage::default(),reported_cost_micros:None,actual_model_id:None,web_search_verified:false,native_sources:vec![],elapsed_ms:7};
        let completed=CallOutcome::from_result(&Ok(reply));assert_eq!(completed.state,CallOutcomeState::Completed);assert_eq!(completed.model_elapsed_ms,Some(7));assert_eq!(completed.safe_error_code,None);completed.validate().unwrap();
        for (failure,state,code) in [(Failure::Explicit{code:"PROVIDER_STATUS",http_status:Some(503)},CallOutcomeState::Failed,"PROVIDER_STATUS"),(Failure::Unknown{code:"MISSING_COMPLETION"},CallOutcomeState::Unknown,"MISSING_COMPLETION"),(Failure::Cancelled,CallOutcomeState::Cancelled,"CANCELLED")] {
            let result=CallOutcome::from_result(&Err(failure));assert_eq!(result.state,state);assert_eq!(result.safe_error_code.as_deref(),Some(code));assert_eq!(result.model_elapsed_ms,None);result.validate().unwrap();
        }
        let measured=Failure::MeasuredUnknown{code:"TIMEOUT",measurement:ReportedMeasurement{usage:Usage{input_tokens:Some(0),..Default::default()},reported_cost_micros:Some(0),actual_model_id:None,elapsed_ms:0}};
        let result=CallOutcome::from_result(&Err(measured));assert_eq!(result.state,CallOutcomeState::Unknown);assert_eq!(result.model_elapsed_ms,Some(0));
    }
    #[test]
    fn call_outcome_rejects_payloads_unsafe_codes_and_inconsistent_origin() {
        let value=CallOutcome::interrupted();value.validate().unwrap();
        let mut json=serde_json::to_value(&value).unwrap();json["payload"]=serde_json::json!("synthetic forbidden body");assert!(serde_json::from_value::<CallOutcome>(json).is_err());
        let mut invalid=value.clone();invalid.safe_error_code=Some("https://private.invalid/body".into());assert!(invalid.validate().is_err());
        invalid=value.clone();invalid.state=CallOutcomeState::Completed;assert!(invalid.validate().is_err());
        invalid=CallOutcome::cancelled_before_dispatch();invalid.model_elapsed_ms=Some(0);assert!(invalid.validate().is_err());
        invalid=value;invalid.model_elapsed_ms=Some(-1);assert!(invalid.validate().is_err());
    }
}

#[cfg(test)]
mod route_snapshot_tests {
    use super::*;
    fn metadata(route: serde_json::Value) -> serde_json::Value {
        serde_json::json!({"role":"ANSWER","modelDisplayName":"GPT-6.1 Sol · Pro3","reasoningTokens":null,"priceVersion":null,"contextWindowTokens":null,"contextSourceCount":null,"route":route})
    }
    #[test]
    fn route_snapshot_capture_uses_only_explicit_original_provider_and_suffix() {
        use super::ModelCallChannel::*;
        for (provider,label,channel) in [("RELAYCORE_GPT","Sol · Pro3",Some(Pro3)),("RELAYCORE_GPT","Sol · Pro2",Some(Pro2)),("RELAYCORE_CLAUDE","Claude · Kiro正价",Some(KiroStandard)),("RELAYCORE_GEMINI","Gemini · Gemini",Some(Gemini)),("OPENROUTER","Model · Pro3",None),("RELAYCORE_GPT","Sol",None),("RELAYCORE_GPT","Sol · future",None)] {
            assert_eq!(ModelCallRoute::capture(provider,label).unwrap().channel_id,channel);
        }
        assert!(ModelCallRoute::capture("https://secret.invalid","Sol · Pro3").is_err());
    }
    #[test]
    fn frozen_route_json_accepts_explicit_provider_and_channel_without_current_catalog() {
        let value=metadata(serde_json::json!({"providerId":"RELAYCORE_GPT","channelId":"PRO3"}));
        let parsed:Metadata=serde_json::from_value(value.clone()).expect("explicit call route must be readable");
        parsed.validate().unwrap();assert_eq!(serde_json::to_value(parsed).unwrap(),value);
    }
    #[test]
    fn old_metadata_without_route_keeps_the_same_serialized_shape() {
        let mut value=metadata(serde_json::Value::Null);value.as_object_mut().unwrap().remove("route");
        let parsed:Metadata=serde_json::from_value(value.clone()).unwrap();parsed.validate().unwrap();assert_eq!(serde_json::to_value(parsed).unwrap(),value);
    }
    #[test]
    fn direct_provider_route_does_not_invent_a_subchannel() {
        let value=metadata(serde_json::json!({"providerId":"OPENROUTER","channelId":null}));
        let parsed:Metadata=serde_json::from_value(value.clone()).expect("direct route must be readable");parsed.validate().unwrap();assert_eq!(serde_json::to_value(parsed).unwrap(),value);
    }
    #[test]
    fn route_rejects_uri_secret_like_provider_and_incompatible_or_unknown_channels() {
        for route in [serde_json::json!({"providerId":"https://private.invalid/key","channelId":null}),serde_json::json!({"providerId":"sk-synthetic-not-a-provider","channelId":null}),serde_json::json!({"providerId":"OPENROUTER","channelId":"PRO3"}),serde_json::json!({"providerId":"RELAYCORE_GPT","channelId":"KIRO_STANDARD"}),serde_json::json!({"providerId":"RELAYCORE_GPT","channelId":"invented-channel"})] {
            let rejected=match serde_json::from_value::<Metadata>(metadata(route)){Err(_)=>true,Ok(value)=>value.validate().is_err()};assert!(rejected);
        }
    }
}
