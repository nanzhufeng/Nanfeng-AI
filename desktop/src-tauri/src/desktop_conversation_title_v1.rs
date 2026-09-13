//! Android-parity conversation title contract.
//!
//! This module deliberately contains no storage or credential code.  It only owns the
//! deterministic, cross-client eligibility and response-validation rules, so a provider can
//! never leak an arbitrary reply into the drawer label.

use serde_json::{json, Value};

pub const NEW_CONVERSATION_TITLE: &str = "新对话";
const MAX_SOURCE_CHARS: usize = 4_000;
const GENERIC_TITLES: &[&str] = &["继续说", "分析", "总结", "问题", "请求", "聊天", "对话", "更新文档", "事实核验", "工程观点"];

pub const TITLE_CONTRACT: &str = "你只负责为下方同一对话的开头用户发言与南枫AI开头回答生成一个会话标题。\n标题必须是明确对象加具体意图、问题或任务的紧凑短语，让未打开会话的用户立即知道讨论什么、要做什么。\n优先复用原文明确出现的主体、产品、组织或术语，并用准确动作收束。不得把回答里的 Markdown 小节、论证步骤、抽象方法词或一句结论片段当标题；不得引入两段文字未明确支持的人名、事实或偏好。\n禁止只写继续说、分析、总结、问题、请求、聊天、对话、更新文档等没有讨论对象的空泛标题；无法同时确认对象和意图时返回空 title，不得猜测。\n标题必须是 6 到 13 个字符的一句话总结，优先约 8 个字符。只能使用汉字、英文字母或阿拉伯数字；英文短语的单词之间允许一个普通空格。严禁标点、引号、Markdown、编号符号、emoji、括号、斜杠、下划线、连字符和任何其他符号。只返回严格 JSON：{\"title\":\"...\"}。";

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Source {
    /// An opaque local reference only.  It lets the quantitative title receipt remain tied to
    /// the exact completed answer without persisting either side of the title prompt.
    pub assistant_message_id: String,
    pub user_text: String,
    pub assistant_text: String,
}

fn message_text(message: &Value) -> String {
    message.get("blocks").and_then(Value::as_array).into_iter().flatten()
        .filter(|block| matches!(block.get("kind").and_then(Value::as_str), Some("TEXT" | "MARKDOWN" | "CODE")))
        .filter_map(|block| block.get("text").and_then(Value::as_str))
        .collect::<Vec<_>>().join("\n").trim().to_owned()
}

/// Mirrors Android `ConversationSnapshot.openingTitleSource`: only the root USER and its first
/// completed assistant child are eligible.  Attachments may qualify an otherwise empty USER.
pub fn opening_source(conversation: &Value) -> Option<Source> {
    let messages = conversation.get("messages")?.as_array()?;
    let user = messages.iter().find(|message| message.get("role").and_then(Value::as_str) == Some("user") && message.get("parentId").map_or(true, Value::is_null))?;
    let user_id = user.get("id")?.as_str()?;
    let assistant = messages.iter().find(|message| message.get("role").and_then(Value::as_str) == Some("assistant") && message.get("parentId").and_then(Value::as_str) == Some(user_id) && message.get("delivery").and_then(Value::as_str) == Some("COMPLETE"))?;
    let source = Source {
        assistant_message_id: assistant.get("id")?.as_str()?.to_owned(),
        user_text: message_text(user),
        assistant_text: message_text(assistant),
    };
    let has_attachment = user.get("blocks").and_then(Value::as_array).into_iter().flatten().any(|block| block.get("kind").and_then(Value::as_str) == Some("ASSET_REF"));
    (!source.assistant_text.is_empty() && (!source.user_text.is_empty() || has_attachment)).then_some(source)
}

pub fn request_messages(source: &Source) -> Value {
    let user = source.user_text.chars().take(MAX_SOURCE_CHARS).collect::<String>();
    let assistant = source.assistant_text.chars().take(MAX_SOURCE_CHARS).collect::<String>();
    let input = if user.is_empty() { format!("首条用户消息仅含附件。不要读取或推断附件内容；只根据下方南枫AI开头回答生成标题：\n{assistant}") } else { format!("用户开头发言：\n{user}\n\n南枫AI开头回答：\n{assistant}") };
    json!([{"role":"system","content":TITLE_CONTRACT},{"role":"user","content":input}])
}

pub fn parse_response(raw: &str) -> Option<String> {
    let raw = raw.trim().strip_prefix("```json").or_else(|| raw.trim().strip_prefix("```")).unwrap_or(raw.trim()).trim();
    let raw = raw.strip_suffix("```").unwrap_or(raw).trim();
    let title = serde_json::from_str::<Value>(raw).ok()?.get("title")?.as_str()?.split_whitespace().collect::<Vec<_>>().join(" ");
    let count = title.chars().count();
    if !(6..=13).contains(&count) || GENERIC_TITLES.contains(&title.as_str()) { return None; }
    title.split(' ').all(|word| !word.is_empty() && word.chars().all(|character| character.is_ascii_alphanumeric() || ('\u{4e00}'..='\u{9fff}').contains(&character))).then_some(title)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test] fn parser_matches_android_title_contract() {
        assert_eq!(parse_response("{\"title\":\"KFK资料整理方案\"}"), Some("KFK资料整理方案".into()));
        assert_eq!(parse_response("{\"title\":\"完整的整理当年KFK在豆...\"}"), None);
        assert_eq!(parse_response("{\"title\":\"更新文档\"}"), None);
    }
    #[test] fn source_requires_first_completed_pair() {
        let conversation = json!({"messages":[{"id":"u","parentId":null,"role":"user","blocks":[{"kind":"TEXT","text":"整理 KFK"}]},{"id":"a","parentId":"u","role":"assistant","delivery":"COMPLETE","blocks":[{"kind":"TEXT","text":"我会整理 KFK 资料。"}]}]});
        assert_eq!(opening_source(&conversation), Some(Source { assistant_message_id:"a".into(), user_text:"整理 KFK".into(), assistant_text:"我会整理 KFK 资料。".into() }));
    }
}
