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
    // A sufficiently long, unfinished replay of the checkpoint has not reached
    // any new content. Terminal success and failure must agree with the stream
    // checkpoint gate; short repeated words still follow the literal policy.
    if generated.chars().count() >= MIN_ECHO && checkpoint.starts_with(generated) { return String::new() }
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

/// Streaming projection may defer an ambiguous replay; terminal literal policy is separate.
pub fn stream_suffix(checkpoint: &str, generated: &str) -> Option<String> {
    if generated.is_empty() { return None }
    if checkpoint.is_empty() { return Some(generated.into()) }
    if checkpoint.starts_with(generated) { return None }
    // Until the next characters disambiguate it, a prefix of a sufficiently
    // long saved tail is provisional rather than new durable answer text.
    // Search only the existing scalar overlap window; never normalize words.
    if generated.chars().take(MAX_OVERLAP + 1).count() <= MAX_OVERLAP {
        let tail: String = checkpoint.chars().rev().take(MAX_OVERLAP)
            .collect::<Vec<_>>().into_iter().rev().collect();
        if tail.find(generated).is_some_and(|offset| tail[offset..].chars().count() >= MIN_ECHO) {
            return None;
        }
    }
    let suffix = strip_echo(checkpoint, generated);
    (!suffix.is_empty()).then_some(suffix)
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
    #[test] fn partial_prefix_completion_remains_incomplete_without_rewriting_short_repetition() {
        let prefix="这是原始回答的完整长断点，继续之前必须保留全部已保存文字。";
        let partial=prefix.chars().take(18).collect::<String>();
        assert_eq!(completed_suffix(prefix,&partial),None);
        assert_eq!(completed_suffix(prefix,"这是"),Some("这是".into()));
        assert_eq!(strip_echo(prefix,&format!("{partial}独立新增内容")),format!("{partial}独立新增内容"));
    }
    #[test] fn checkpoint_projection_joins_only_this_turns_assistant_fragments() {
        let messages=serde_json::json!([{"role":"assistant","content":"unrelated history"},{"role":"user","content":"question"},{"role":"assistant","content":"first"},{"role":"assistant","content":[{"type":"text","text":"second"}]},{"role":"user","content":INSTRUCTION}]);
        assert_eq!(checkpoint_text(&messages),"firstsecond");
        assert_eq!(checkpoint_text(&serde_json::json!([])),"");
    }
    #[test] fn streaming_suffix_replay_is_held_until_it_reaches_new_text() {
        let tail="TAIL_REPLAY_abcdefghijklmnopqrstuvwxyz_0123456789";
        let checkpoint=format!("DIFFERENT_BEGIN_{tail}");
        assert_eq!(stream_suffix(&checkpoint,&tail[..8]),None);
        assert_eq!(stream_suffix(&checkpoint,&tail[..20]),None);
        assert_eq!(stream_suffix(&checkpoint,tail),None);
        assert_eq!(stream_suffix(&checkpoint,&format!("{tail}新增正文")),Some("新增正文".into()));
    }
    #[test] fn streaming_defer_does_not_delete_a_literal_terminal_internal_phrase() {
        let phrase="这是先前中部出现的一句足够长的相同文字";
        let checkpoint=format!("不同开头{phrase}但旧回答仍有其他末尾内容");
        assert_eq!(stream_suffix(&checkpoint,phrase),None);
        assert_eq!(completed_suffix(&checkpoint,phrase),Some(phrase.into()));
        assert_eq!(stream_suffix(&checkpoint,&format!("{phrase}全新分叉")),Some(format!("{phrase}全新分叉")));
    }
    #[test] fn streaming_short_terminal_repetition_and_ordinary_text_keep_literal_policy() {
        let checkpoint="不同起点和足够长的旧内容末尾只剩哈哈";
        assert_eq!(stream_suffix(checkpoint,"哈哈"),Some("哈哈".into()));
        assert_eq!(completed_suffix(checkpoint,"哈哈"),Some("哈哈".into()));
        assert_eq!(stream_suffix("","首次实时正文"),Some("首次实时正文".into()));
    }
    #[test] fn streaming_suffix_ambiguity_is_unicode_exact_and_bounded() {
        let tail="😀甲乙丙丁戊己庚辛壬癸子丑寅卯辰巳午未申酉戌亥";
        let checkpoint=format!("另一处开头{tail}");let partial=tail.chars().take(5).collect::<String>();
        assert_eq!(stream_suffix(&checkpoint,&partial),None);
        assert_eq!(stream_suffix(&checkpoint,&format!("{tail}新的末尾")),Some("新的末尾".into()));
        let outside=format!("OTHER_早期命中{}","x".repeat(MAX_OVERLAP));
        assert_eq!(stream_suffix(&outside,"早期命中"),Some("早期命中".into()));
    }

}
