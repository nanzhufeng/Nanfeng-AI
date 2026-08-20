//! P6-L1 local exact-reuse index. It stores only hashes and existing message references.

use std::collections::BTreeMap;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Outcome {
    LocalExactHit,
    Miss,
    Ineligible,
    Unknown,
}
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Sensitivity {
    Low,
    Medium,
    High,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ExactKey {
    pub scope_id: String,
    pub provider_id: String,
    pub model_snapshot_id: String,
    pub endpoint_mode: String,
    pub generation_parameters_hash: String,
    pub tool_schema_hash: String,
    pub context_manifest_hash: String,
    pub message_tree_hash: String,
    pub attachment_hash: String,
    pub template_version: String,
    pub policy_version: u64,
    pub canonical_request_hash: String,
    pub sensitivity: Sensitivity,
}
impl ExactKey {
    pub fn validate(&self) -> Result<(), &'static str> {
        if self.policy_version == 0
            || [
                self.scope_id.as_str(),
                self.provider_id.as_str(),
                self.model_snapshot_id.as_str(),
                self.endpoint_mode.as_str(),
                self.template_version.as_str(),
            ]
            .iter()
            .any(|id| !stable_id(id))
        {
            return Err("local exact reuse key 无效");
        }
        if [
            self.generation_parameters_hash.as_str(),
            self.tool_schema_hash.as_str(),
            self.context_manifest_hash.as_str(),
            self.message_tree_hash.as_str(),
            self.attachment_hash.as_str(),
            self.canonical_request_hash.as_str(),
        ]
        .iter()
        .any(|hash| !sha256(hash))
        {
            return Err("local exact reuse hash 无效");
        }
        Ok(())
    }
}
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Entry {
    pub key: ExactKey,
    pub response_message_id: String,
    pub created_at_ms: u64,
    pub expires_at_ms: u64,
    pub revoked: bool,
}
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Decision {
    pub outcome: Outcome,
    pub response_message_id: Option<String>,
    pub reason: &'static str,
}
#[derive(Default)]
pub struct LocalExactReuseIndex {
    entries: BTreeMap<String, Entry>,
}

impl LocalExactReuseIndex {
    pub fn record(&mut self, entry: Entry) -> Result<bool, &'static str> {
        entry.key.validate()?;
        if !stable_id(&entry.response_message_id) || entry.expires_at_ms <= entry.created_at_ms {
            return Err("local exact reuse entry 无效");
        }
        if entry.key.sensitivity == Sensitivity::High || entry.revoked {
            return Ok(false);
        }
        match self.entries.get(&entry.key.canonical_request_hash) {
            Some(old) if old != &entry => Ok(false),
            Some(_) => Ok(true),
            None => {
                self.entries
                    .insert(entry.key.canonical_request_hash.clone(), entry);
                Ok(true)
            }
        }
    }
    pub fn resolve(&self, key: Option<&ExactKey>, temporary: bool, now_ms: u64) -> Decision {
        let Some(key) = key else {
            return decision(Outcome::Unknown, None, "缺少完整精确复用键");
        };
        if key.validate().is_err() {
            return decision(Outcome::Unknown, None, "精确复用键无效");
        }
        if temporary {
            return decision(Outcome::Ineligible, None, "临时会话不建立本地精确复用");
        }
        if key.sensitivity == Sensitivity::High {
            return decision(Outcome::Ineligible, None, "高敏感请求不建立本地精确复用");
        }
        let Some(entry) = self.entries.get(&key.canonical_request_hash) else {
            return decision(Outcome::Miss, None, "没有完全一致的本地结果");
        };
        if entry.key != *key {
            return decision(Outcome::Miss, None, "精确键字段不一致");
        }
        if entry.revoked || now_ms >= entry.expires_at_ms {
            return decision(Outcome::Miss, None, "本地结果已撤销或过期");
        }
        decision(
            Outcome::LocalExactHit,
            Some(entry.response_message_id.clone()),
            "复用既有本地消息；不会请求 Provider",
        )
    }
}
fn decision(
    outcome: Outcome,
    response_message_id: Option<String>,
    reason: &'static str,
) -> Decision {
    Decision {
        outcome,
        response_message_id,
        reason,
    }
}
fn stable_id(value: &str) -> bool {
    !value.is_empty()
        && value.len() <= 160
        && value
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || b"._:-".contains(&byte))
}
fn sha256(value: &str) -> bool {
    value.len() == 64
        && value
            .bytes()
            .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte))
}

#[cfg(test)]
mod tests {
    use super::*;
    fn key(hash: char, sensitivity: Sensitivity) -> ExactKey {
        ExactKey {
            scope_id: "workspace:fixture".into(),
            provider_id: "openrouter".into(),
            model_snapshot_id: "model:fixture".into(),
            endpoint_mode: "chat-completions".into(),
            generation_parameters_hash: "b".repeat(64),
            tool_schema_hash: "c".repeat(64),
            context_manifest_hash: "d".repeat(64),
            message_tree_hash: "e".repeat(64),
            attachment_hash: "f".repeat(64),
            template_version: "template:v1".into(),
            policy_version: 1,
            canonical_request_hash: hash.to_string().repeat(64),
            sensitivity,
        }
    }
    #[test]
    fn exact_only_normal_entry_hits() {
        let mut index = LocalExactReuseIndex::default();
        let exact = key('a', Sensitivity::Low);
        assert!(index
            .record(Entry {
                key: exact.clone(),
                response_message_id: "message:fixture".into(),
                created_at_ms: 10,
                expires_at_ms: 20,
                revoked: false
            })
            .unwrap());
        assert_eq!(
            index.resolve(Some(&exact), false, 19).outcome,
            Outcome::LocalExactHit
        );
        assert_eq!(
            index
                .resolve(Some(&key('9', Sensitivity::Low)), false, 19)
                .outcome,
            Outcome::Miss
        );
        assert_eq!(
            index.resolve(Some(&exact), false, 20).outcome,
            Outcome::Miss
        );
    }
    #[test]
    fn temporary_sensitive_revoked_and_missing_fail_closed() {
        let mut index = LocalExactReuseIndex::default();
        let exact = key('a', Sensitivity::Low);
        assert!(index
            .record(Entry {
                key: exact.clone(),
                response_message_id: "message:fixture".into(),
                created_at_ms: 10,
                expires_at_ms: 30,
                revoked: false
            })
            .unwrap());
        assert_eq!(
            index.resolve(Some(&exact), true, 11).outcome,
            Outcome::Ineligible
        );
        let high = key('b', Sensitivity::High);
        assert!(!index
            .record(Entry {
                key: high.clone(),
                response_message_id: "message:high".into(),
                created_at_ms: 10,
                expires_at_ms: 30,
                revoked: false
            })
            .unwrap());
        assert_eq!(
            index.resolve(Some(&high), false, 11).outcome,
            Outcome::Ineligible
        );
        assert_eq!(index.resolve(None, false, 11).outcome, Outcome::Unknown);
        assert!(!index
            .record(Entry {
                key: key('c', Sensitivity::Low),
                response_message_id: "message:revoked".into(),
                created_at_ms: 10,
                expires_at_ms: 30,
                revoked: true
            })
            .unwrap());
    }
}
