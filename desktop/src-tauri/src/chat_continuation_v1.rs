//! Exact, bounded checkpoint echo removal. No semantic rewriting of saved answers.
use serde_json::Value;
pub const INSTRUCTION: &str = "上一段回答因连接中断而未完成。请只从上面已生成内容的末尾自然继续，不要重复、改写或概述已有文字；继续完成原问题。";
const MIN_ECHO: usize = 16;
const MAX_OVERLAP: usize = 8192;

fn content_text(message: &Value) -> String {
    message["content"].as_str().map(ToOwned::to_owned).unwrap_or_else(|| {
        message["content"].as_array().into_iter().flatten()
            .filter(|part| part["type"] == "text")
            .filter_map(|part| part["text"].as_str()).collect::<String>()
    })
}
pub fn checkpoint_text(messages: &Value) -> String {
    let Some(items) = messages.as_array() else { return String::new() };
    if !items.last().is_some_and(|m| m["role"] == "user" && m["content"] == INSTRUCTION) { return String::new() }
    let fragments: Vec<String> = items[..items.len() - 1].iter().rev()
        .take_while(|m| m["role"] == "assistant").map(content_text).collect();
    fragments.into_iter().rev().collect()
}
pub fn strip_echo(checkpoint: &str, generated: &str) -> String {
    if checkpoint.is_empty() || generated.is_empty() { return generated.into() }
    if checkpoint.chars().count() >= MIN_ECHO && generated.starts_with(checkpoint) { return generated[checkpoint.len()..].into() }
    let pattern: Vec<char> = generated.chars().take(MAX_OVERLAP).collect();
    if pattern.len() < MIN_ECHO { return generated.into() }
    let mut failure = vec![0; pattern.len()];
    for i in 1..pattern.len() {
        let mut j = failure[i - 1];
        while j > 0 && pattern[i] != pattern[j] { j = failure[j - 1]; }
        if pattern[i] == pattern[j] { j += 1; }
        failure[i] = j;
    }
    let tail: Vec<char> = checkpoint.chars().rev().take(MAX_OVERLAP).collect();
    let mut overlap = 0;
    for point in tail.into_iter().rev() {
        while overlap > 0 && (overlap == pattern.len() || point != pattern[overlap]) { overlap = failure[overlap - 1]; }
        if overlap < pattern.len() && point == pattern[overlap] { overlap += 1; }
    }
    if overlap >= MIN_ECHO { generated.chars().skip(overlap).collect() } else { generated.into() }
}

/// A completed upstream response that adds no text must remain retryable locally.
pub fn completed_suffix(checkpoint: &str, generated: &str) -> Option<String> {
    let suffix = strip_echo(checkpoint, generated);
    if !checkpoint.is_empty() && suffix.trim().is_empty() { None } else { Some(suffix) }
}

#[cfg(test)] mod tests {
    use super::*;
    #[test] fn shared_checkpoint_fixtures_preserve_literal_prefix() {
        let fixtures: Vec<Value> = serde_json::from_str(include_str!("../../../protocol/fixtures/chat-continuation-v1.json")).unwrap();
        for sample in fixtures {
            assert_eq!(strip_echo(sample["checkpoint"].as_str().unwrap(),sample["generated"].as_str().unwrap()),sample["suffix"].as_str().unwrap(),"{}",sample["name"]);
        }
    }
    #[test] fn echo_only_completion_is_not_progress() {
        let prefix="这是足够长的原始回答断点，只有回显不能算续写完成。";
        assert_eq!(completed_suffix(prefix,prefix),None);
        assert_eq!(completed_suffix(prefix,&format!("{prefix}新的内容")),Some("新的内容".into()));
        assert_eq!(completed_suffix("","完整回答"),Some("完整回答".into()));
    }
    #[test] fn checkpoint_projection_joins_only_this_turns_assistant_fragments() {
        let messages=serde_json::json!([{"role":"assistant","content":"unrelated history"},{"role":"user","content":"question"},{"role":"assistant","content":"first"},{"role":"assistant","content":[{"type":"text","text":"second"}]},{"role":"user","content":INSTRUCTION}]);
        assert_eq!(checkpoint_text(&messages),"firstsecond");
        assert_eq!(checkpoint_text(&serde_json::json!([])),"");
    }
}
