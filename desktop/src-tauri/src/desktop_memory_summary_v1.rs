//! The explicit conversational memory command shares Android's narrow policy.
//! Provider text is parsed here, before it can become a visible assistant answer.

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Draft {
    pub title: String,
    pub body: String,
}

fn sensitive(text: &str) -> bool {
    let lower = text.to_lowercase();
    ["密码", "密钥", "私钥", "验证码", "支付信息", "银行卡", "详细住址", "apikey", "api key", "api_key", "api-key", "authorization", "恢复码", "passcode", "bearer "]
        .iter()
        .any(|term| lower.contains(term))
}

pub fn is_explicit_command(text: &str) -> bool {
    if sensitive(text) { return false; }
    let mut rest = text.trim_start();
    rest = rest.strip_prefix('请').unwrap_or(rest);
    let Some(rest) = rest.strip_prefix("记住") else { return false };
    let rest = ["这些事", "这件事", "这事", "一下", "了"]
        .iter()
        .find_map(|suffix| rest.strip_prefix(suffix))
        .unwrap_or(rest);
    rest.is_empty() || rest.chars().next().is_some_and(|ch| matches!(ch, '：' | ':' | '，' | ',') || ch.is_whitespace())
}

pub const COMMAND_INSTRUCTION: &str = "用户刚刚明确要求‘记住’。请仅从本轮及必要的相邻对话中提取长期有效、会影响未来回答的非敏感信息，整理成一条本机记忆摘要。\n不要保存密码、密钥、验证码、支付信息、详细住址或一次性安排；证据不足时不要编造。\n回复必须严格使用：\n【记忆主题】一个简短分类（如：概览、个人项目、开发与设计偏好、长期研究方向、工作流程偏好、技术与工具、背景与长期规划）\n【记忆摘要】2-5 条简洁、可复用的事实或偏好";

pub fn parse_model_summary(text: &str) -> Option<Draft> {
    let after_title = text.split_once("【记忆主题】")?.1;
    let (title, body) = after_title.split_once("【记忆摘要】")?;
    let title: String = title.trim().chars().take(120).collect();
    let body: String = body.trim().chars().take(1_500).collect();
    if title.is_empty() || body.is_empty() || sensitive(&title) || sensitive(&body) { return None; }
    Some(Draft { title, body })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn explicit_command_requires_a_boundary_and_rejects_secrets() {
        assert!(is_explicit_command("记住了：我长期关注 AI 与影视工具"));
        assert!(is_explicit_command("请记住一下，我偏好清晰说明"));
        assert!(!is_explicit_command("记住了吗"));
        assert!(!is_explicit_command("记住了：API Key: abcdef"));
        assert!(!is_explicit_command("记住了：APIKey abcdef"));
    }

    #[test]
    fn model_summary_is_structured_and_sensitive_text_never_passes() {
        assert_eq!(parse_model_summary("【记忆主题】技术与工具\n【记忆摘要】\n- 长期使用 Codex。"), Some(Draft {
            title: "技术与工具".into(), body: "- 长期使用 Codex。".into(),
        }));
        assert_eq!(parse_model_summary("【记忆主题】概览\n【记忆摘要】密码：123456"), None);
        assert_eq!(parse_model_summary("【记忆主题】概览\n【记忆摘要】验证码：123456"), None);
        assert_eq!(parse_model_summary("普通回答"), None);
    }
}
