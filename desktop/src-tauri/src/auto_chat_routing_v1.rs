use serde_json::Value;
use std::collections::{BTreeMap, BTreeSet};

/// Selection metadata only. This does not authorize or load historical file bytes.
pub fn with_ancestor_mimes(current: &[String], messages: &[Value], start: Option<&str>) -> Result<Vec<String>, &'static str> {
    let mut by_id = BTreeMap::new();
    for message in messages {
        let id = message.get("id").and_then(Value::as_str).ok_or("Auto消息身份无效")?;
        if by_id.insert(id, message).is_some() { return Err("Auto消息身份重复"); }
    }
    let mut result = current.to_vec();
    let mut cursor = start;
    let mut seen = BTreeSet::new();
    while let Some(id) = cursor {
        if !seen.insert(id) { return Err("Auto祖先路径存在环"); }
        let message = by_id.get(id).ok_or("Auto祖先路径缺少消息")?;
        for block in message.get("blocks").and_then(Value::as_array).into_iter().flatten() {
            if block.get("kind").and_then(Value::as_str) == Some("ASSET_REF") {
                result.push(block.get("asset").and_then(|asset| asset.get("mimeType"))
                    .and_then(Value::as_str).unwrap_or("application/octet-stream").to_owned());
            }
        }
        cursor = message.get("parentId").and_then(Value::as_str);
    }
    Ok(result)
}
pub fn task(text: &str, mimes: &[String]) -> &'static str {
    if !mimes.is_empty() { return "ATTACHMENT"; }
    if text.encode_utf16().count() >= 2400 || regex::Regex::new(r"(?i)深入分析|深度分析|系统分析|完整方案|方案设计|架构设计|重构|代码审计|根因|权衡|情景分析|风险传导|投资分析|估值分析|长期规划|多步骤推理|复杂推理|调试|复杂项目|报错|(?i-u:\b(?:debug|bug|stacktrace|exception|compile|architecture|root[ -]?cause|audit|trade[ -]?offs?|migration|refactor|deep analysis|multi[ -]?step reasoning)\b)").expect("owned classifier").is_match(text) { "COMPLEX_REASONING" } else { "DEFAULT_TEXT" }
}
pub fn candidates(text: &str, mimes: &[String]) -> Vec<String> {
    let policy: Value = serde_json::from_str(include_str!("../../../protocol/fixtures/auto-routing-rc-domestic-20261010.json")).expect("owned Auto fixture");
    policy["plans"][task(text,mimes)].as_array().expect("owned task").iter().map(|id| id.as_str().expect("preset").to_owned()).collect()
}
pub fn projects(provider: &str, model: &str, mime: &str) -> bool {
    crate::attachment_document_text_v1::supports(mime) ||
    (matches!(mime,"image/jpeg"|"image/png"|"image/webp"|"image/gif") && (matches!(provider,"OPENROUTER"|"QWEN") || provider == "DEEPSEEK" && model == "deepseek-flash")) || mime == "application/pdf" && provider == "OPENROUTER"
}
pub fn projects_with_search(provider:&str,model:&str,mime:&str,web:bool)->bool {
    projects(provider,model,mime) && (!web || provider=="OPENROUTER" || !mime.starts_with("image/") && !mime.starts_with("audio/") && !mime.starts_with("video/"))
}

#[cfg(test)] mod ancestor_material_tests {
 use super::*;
 use serde_json::json;
 #[test] fn auto_ancestor_keeps_current_then_exact_parent_path_without_sibling_material() {
  let messages=vec![json!({"id":"root","blocks":[{"kind":"ASSET_REF","asset":{"mimeType":"image/png"}}]}),
   json!({"id":"selected","parentId":"root","blocks":[]}),
   json!({"id":"unrelated","parentId":"root","blocks":[{"kind":"ASSET_REF","asset":{"mimeType":"audio/wav"}}]})];
  let before=messages.clone();
  assert_eq!(with_ancestor_mimes(&["text/plain".into()],&messages,Some("selected")).unwrap(),vec!["text/plain","image/png"]);
  assert_eq!(messages,before);
 }
 #[test] fn auto_ancestor_branch_switch_changes_only_selected_path() {
  let messages=vec![json!({"id":"root","blocks":[]}),json!({"id":"clean","parentId":"root","blocks":[]}),
   json!({"id":"asset","parentId":"root","blocks":[{"kind":"ASSET_REF","asset":{"mimeType":"application/pdf"}}]})];
  assert!(with_ancestor_mimes(&[],&messages,Some("clean")).unwrap().is_empty());
  assert_eq!(with_ancestor_mimes(&[],&messages,Some("asset")).unwrap(),vec!["application/pdf"]);
 }
 #[test] fn auto_ancestor_invalid_tree_fails_closed_without_partial_success() {
  assert!(with_ancestor_mimes(&[],&[json!({"id":"a","parentId":"a"})],Some("a")).is_err());
  assert!(with_ancestor_mimes(&[],&[json!({"id":"a","parentId":"missing"})],Some("a")).is_err());
  assert!(with_ancestor_mimes(&[],&[json!({"id":"a"}),json!({"id":"a"})],Some("a")).is_err());
 }
 #[test] fn auto_ancestor_unknown_mime_is_not_promoted_to_text() {
  assert_eq!(with_ancestor_mimes(&[],&[json!({"id":"a","blocks":[{"kind":"ASSET_REF","asset":{}}]})],Some("a")).unwrap(),vec!["application/octet-stream"]);
 }
}

#[cfg(test)] mod tests {
 use super::*;
 #[test] fn shared_task_cases_match_js_utf16_and_word_boundaries() {
  let cases:Vec<Value>=serde_json::from_str(include_str!("../../../protocol/fixtures/auto-routing-task-cases-v1.json")).unwrap();
  for row in cases {let text=row["text"].as_str().map(str::to_owned).unwrap_or_else(||row["glyph"].as_str().unwrap().repeat(row["count"].as_u64().unwrap() as usize));
   let mimes:Vec<String>=row["mimes"].as_array().map(|list|list.iter().map(|m|m.as_str().unwrap().to_owned()).collect()).unwrap_or_default();
   assert_eq!(task(&text,&mimes),row["task"].as_str().unwrap(),"{text}");}
 }

 #[test] fn classifier_and_projection_match_actual_transport() {
  assert_eq!(task("分析一下这家公司",&[]),"DEFAULT_TEXT"); assert_eq!(task("debugging",&[]),"DEFAULT_TEXT");assert_eq!(task("debug this",&[]),"COMPLEX_REASONING");assert_eq!(task(&"a".repeat(2400),&[]),"COMPLEX_REASONING");
  assert_eq!(task("架构设计",&["image/png".into()]),"ATTACHMENT");
  assert!(!projects("RELAYCORE_GPT","gpt-6.1-sol","image/png"));assert!(!projects("QWEN","qwen-plus","application/pdf"));assert!(projects("OPENROUTER","fixture","application/pdf"));
  assert!(projects_with_search("QWEN","qwen-plus","image/png",false));
  assert!(!projects_with_search("QWEN","qwen-plus","image/png",true));
  assert!(!projects_with_search("DEEPSEEK","deepseek-flash","image/png",true));
  assert!(projects_with_search("OPENROUTER","fixture","image/png",true));
  assert!(projects_with_search("QWEN","qwen-plus","text/markdown",true));
 }
}
