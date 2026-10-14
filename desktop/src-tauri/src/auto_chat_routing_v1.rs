use serde_json::Value;
pub fn task(text: &str, mimes: &[String]) -> &'static str {
    if !mimes.is_empty() { return "ATTACHMENT"; }
    if text.encode_utf16().count() >= 2400 || regex::Regex::new(r"(?i)深入分析|深度分析|系统分析|完整方案|方案设计|架构设计|重构|代码审计|根因|权衡|情景分析|风险传导|投资分析|估值分析|长期规划|多步骤推理|复杂推理|调试|复杂项目|报错|\b(?:debug|bug|stacktrace|exception|compile|architecture|root[ -]?cause|audit|trade[ -]?offs?|migration|refactor|deep analysis|multi[ -]?step reasoning)\b").expect("owned classifier").is_match(text) { "COMPLEX_REASONING" } else { "DEFAULT_TEXT" }
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

#[cfg(test)] mod tests {
 use super::*;
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
