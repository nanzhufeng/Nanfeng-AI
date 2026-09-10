//! Desktop model-service settings shared by Settings and the future ordinary-chat executor.
//! Provider endpoints, preset ownership, and Keychain service names are product constants.
//! Safe projections expose credential presence only; a secret is returned only by the explicit
//! user-triggered reveal command.

use serde::Serialize;
use serde_json::json;
use zeroize::Zeroizing;
use std::sync::Mutex;
static DENIED_PROVIDERS: Mutex<Vec<String>> = Mutex::new(Vec::new());

pub fn allow_user_credential_retry(provider_id: &str) {
    if let Ok(mut denied) = DENIED_PROVIDERS.lock() { denied.retain(|id| id != provider_id); }
}

pub const KEYCHAIN_ACCOUNT: &str = "api-key";

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CredentialPresence {
    Stored,
    MissingOrUnavailable,
    Unavailable,
}

pub trait ProviderCredentialStore {
    fn presence(&self, provider_id: &str) -> CredentialPresence;
    fn save_user_provided_secret(&self, provider_id: &str, secret: &[u8]) -> Result<(), String>;
    fn with_secret<T>(
        &self,
        provider_id: &str,
        operation: impl FnOnce(&[u8]) -> Result<T, String>,
    ) -> Result<T, String>;
    fn reveal_user_requested_secret(&self, provider_id: &str) -> Result<Zeroizing<String>, String>;
    fn delete_user_secret(&self, provider_id: &str) -> Result<(), String>;
}

pub struct MacSecurityFrameworkProviderCredentialStore;

impl ProviderCredentialStore for MacSecurityFrameworkProviderCredentialStore {
    fn presence(&self, provider_id: &str) -> CredentialPresence {
        #[cfg(target_os = "macos")]
        {
            let Ok(service) = keychain_service(provider_id) else { return CredentialPresence::Unavailable; };
            return credential_presence_from_status(keychain_presence_status(&service));
        }
        #[cfg(not(target_os = "macos"))]
        { CredentialPresence::Unavailable }
    }

    fn save_user_provided_secret(&self, provider_id: &str, secret: &[u8]) -> Result<(), String> {
        validate_provider(provider_id)?;
        validate_secret(secret)?;
        allow_user_credential_retry(provider_id);
        #[cfg(target_os = "macos")]
        {
            security_framework::passwords::set_generic_password(
                keychain_service(provider_id)?.as_str(),
                KEYCHAIN_ACCOUNT,
                secret,
            )
            .map_err(|_| "API Key 未能安全写入本机钥匙串；原配置保持不变。".to_owned())
        }
        #[cfg(not(target_os = "macos"))]
        {
            let _ = secret;
            Err("当前 Desktop 平台尚未接通系统凭据存储；未保存 API Key。".to_owned())
        }
    }

    fn with_secret<T>(
        &self,
        provider_id: &str,
        operation: impl FnOnce(&[u8]) -> Result<T, String>,
    ) -> Result<T, String> {
        let secret = Zeroizing::new(self.read_secret(provider_id)?);
        operation(secret.as_slice())
    }

    fn reveal_user_requested_secret(&self, provider_id: &str) -> Result<Zeroizing<String>, String> {
        allow_user_credential_retry(provider_id);
        let bytes = Zeroizing::new(self.read_secret(provider_id)?);
        let value = String::from_utf8(bytes.to_vec())
            .map_err(|_| "本机 API Key 格式无效；请重新保存。".to_owned())?;
        Ok(Zeroizing::new(value))
    }

    fn delete_user_secret(&self, provider_id: &str) -> Result<(), String> {
        validate_provider(provider_id)?;
        #[cfg(target_os = "macos")]
        {
            match security_framework::passwords::delete_generic_password(
                keychain_service(provider_id)?.as_str(),
                KEYCHAIN_ACCOUNT,
            ) {
                Ok(()) => Ok(()),
                // Security.framework's stable errSecItemNotFound value. A missing key already
                // satisfies the requested post-condition; all other keychain failures stay
                // visible instead of being silently treated as an empty credential store.
                Err(error) if error.code() == -25300 => Ok(()),
                Err(_) => Err("API Key 未能从本机钥匙串删除；请重试。".to_owned()),
            }
        }
        #[cfg(not(target_os = "macos"))]
        {
            Ok(())
        }
    }
}

fn credential_presence_from_status(status: i32) -> CredentialPresence {
    match status {
        0 => CredentialPresence::Stored,
        -25300 => CredentialPresence::MissingOrUnavailable,
        _ => CredentialPresence::Unavailable,
    }
}

fn credential_read_error(status: i32) -> &'static str {
    match status {
        -25300 => "本机尚未保存该服务商的 API Key。",
        -128 | -25293 => "系统未准许读取此密钥；现有记录保留，应用不会弹出系统授权框。",
        -25308 => "系统暂不允许无弹窗访问此密钥；请解锁钥匙串后重试，现有记录保留。",
        _ => "暂时无法读取钥匙串；现有密钥状态未知，请主动重试。",
    }
}

#[cfg(target_os = "macos")]
fn keychain_presence_status(service: &str) -> i32 {
    use core_foundation::{base::TCFType, boolean::CFBoolean, dictionary::CFDictionary, string::CFString};
    use security_framework_sys::{item::*, keychain_item::SecItemCopyMatching};
    unsafe extern "C" { static kSecUseAuthenticationUIFail: core_foundation::string::CFStringRef; }
    unsafe {
        let string = |value| CFString::wrap_under_get_rule(value).into_CFType();
        let query = CFDictionary::from_CFType_pairs(&[
            (string(kSecClass), string(kSecClassGenericPassword)),
            (string(kSecAttrService), CFString::new(service).into_CFType()),
            (string(kSecAttrAccount), CFString::new(KEYCHAIN_ACCOUNT).into_CFType()),
            (string(kSecReturnAttributes), CFBoolean::true_value().into_CFType()),
            (string(kSecReturnData), CFBoolean::false_value().into_CFType()),
            (string(kSecUseAuthenticationUI), string(kSecUseAuthenticationUIFail)),
        ]);
        let mut result = std::ptr::null();
        let status = SecItemCopyMatching(query.as_concrete_TypeRef(), &mut result);
        if !result.is_null() { let _owned = core_foundation::base::CFType::wrap_under_create_rule(result); }
        status
    }
}

impl MacSecurityFrameworkProviderCredentialStore {
    fn read_secret(&self, provider_id: &str) -> Result<Vec<u8>, String> {
        validate_provider(provider_id)?;
        let mut denied = DENIED_PROVIDERS.lock().map_err(|_| "密钥读取状态忙，请重试。".to_owned())?;
        if denied.iter().any(|id| id == provider_id) { return Err(credential_read_error(-128).to_owned()); }
        #[cfg(target_os = "macos")]
        {
            security_framework::passwords::get_generic_password(
                keychain_service(provider_id)?.as_str(),
                KEYCHAIN_ACCOUNT,
            )
            .map_err(|error| {
                if matches!(error.code(), -128 | -25293 | -25308) { denied.push(provider_id.to_owned()); }
                credential_read_error(error.code()).to_owned()
            })
        }
        #[cfg(not(target_os = "macos"))]
        {
            Err("当前 Desktop 平台尚未接通系统凭据存储。".to_owned())
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct ProviderDescriptor {
    pub id: &'static str,
    pub display_name: &'static str,
    pub endpoint: &'static str,
    pub default_preset_id: &'static str,
}

pub const PROVIDERS: [ProviderDescriptor; 4] = [
    ProviderDescriptor {
        id: "OPENROUTER",
        display_name: "OpenRouter",
        endpoint: "https://openrouter.ai/api/v1",
        default_preset_id: "GPT_5_6_TERRA",
    },
    ProviderDescriptor {
        id: "DEEPSEEK",
        display_name: "DeepSeek 官方直连",
        endpoint: "https://api.deepseek.com/v1",
        default_preset_id: "DEEPSEEK_V4_PRO",
    },
    ProviderDescriptor {
        id: "ZHIPU",
        display_name: "智谱 BigModel 官方直连",
        endpoint: "https://open.bigmodel.cn/api/paas/v4",
        default_preset_id: "GLM_5_3_FLASH",
    },
    ProviderDescriptor {
        id: "QWEN",
        display_name: "Qwen 官方直连",
        endpoint: "https://dashscope.aliyuncs.com/compatible-mode/v1",
        default_preset_id: "QWEN_3_7_PLUS",
    },
];

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PresetDescriptor {
    pub id: &'static str,
    pub provider_id: &'static str,
    pub model_id: &'static str,
    pub display_name: &'static str,
    pub description: &'static str,
    pub family: &'static str,
    pub chat_selectable: bool,
}

pub const PRESETS: [PresetDescriptor; 17] = [
    PresetDescriptor { id: "CLAUDE_FABLE_5_1", provider_id: "OPENROUTER", model_id: "anthropic/claude-fable-5.1-20260831", display_name: "Claude Fable 5.1", description: "适合长程代码、研究与复杂知识工作。", family: "Anthropic · OpenRouter", chat_selectable: true },
    PresetDescriptor { id: "GPT_6_ASTRA", provider_id: "OPENROUTER", model_id: "openai/gpt-6-astra", display_name: "GPT-6 Astra", description: "适合高难分析、工程与长程复杂任务。", family: "OpenAI · OpenRouter", chat_selectable: true },
    PresetDescriptor {
        id: "CLAUDE_FABLE_5",
        provider_id: "OPENROUTER",
        model_id: "anthropic/claude-fable-5",
        display_name: "Claude Fable 5",
        description: "应对最棘手的复杂任务。",
        family: "Anthropic · OpenRouter",
        chat_selectable: true,
    },
    PresetDescriptor {
        id: "CLAUDE_OPUS_5",
        provider_id: "OPENROUTER",
        model_id: "anthropic/claude-opus-5",
        display_name: "Claude Opus 5",
        description: "适合复杂任务与深度推理。",
        family: "Anthropic · OpenRouter",
        chat_selectable: true,
    },
    PresetDescriptor {
        id: "CLAUDE_SONNET_5",
        provider_id: "OPENROUTER",
        model_id: "anthropic/claude-sonnet-5",
        display_name: "Claude Sonnet 5",
        description: "高效处理日常工作。",
        family: "Anthropic · OpenRouter",
        chat_selectable: true,
    },
    PresetDescriptor {
        id: "CLAUDE_HAIKU_4_5",
        provider_id: "OPENROUTER",
        model_id: "anthropic/claude-haiku-4.5",
        display_name: "Claude Haiku 4.5",
        description: "快速获得简洁答案。",
        family: "Anthropic · OpenRouter",
        chat_selectable: true,
    },
    PresetDescriptor {
        id: "GPT_5_6_SOL",
        provider_id: "OPENROUTER",
        model_id: "openai/gpt-5.6-sol",
        display_name: "GPT-5.6 Sol",
        description: "前沿能力，适合专业复杂任务。",
        family: "OpenAI · OpenRouter",
        chat_selectable: true,
    },
    PresetDescriptor {
        id: "GPT_5_6_TERRA",
        provider_id: "OPENROUTER",
        model_id: "openai/gpt-5.6-terra",
        display_name: "GPT-5.6 Terra",
        description: "能力与成本更均衡。",
        family: "OpenAI · OpenRouter",
        chat_selectable: true,
    },
    PresetDescriptor {
        id: "GPT_5_6_LUNA",
        provider_id: "OPENROUTER",
        model_id: "openai/gpt-5.6-luna",
        display_name: "GPT-5.6 Luna",
        description: "适合高频、轻量任务。",
        family: "OpenAI · OpenRouter",
        chat_selectable: true,
    },
    PresetDescriptor {
        id: "GEMINI_3_7_FLASH",
        provider_id: "OPENROUTER",
        model_id: "google/gemini-3.7-flash",
        display_name: "Gemini 3.7 Flash",
        description: "快速处理文字、图片和文件任务。",
        family: "Google · OpenRouter",
        chat_selectable: true,
    },
    PresetDescriptor {
        id: "KIMI_K3",
        provider_id: "OPENROUTER",
        model_id: "moonshotai/kimi-k3",
        display_name: "Kimi K3",
        description: "复杂分析 · Agent · 长上下文",
        family: "Moonshot AI · OpenRouter",
        chat_selectable: true,
    },
    PresetDescriptor {
        id: "DEEPSEEK_V4_PRO",
        provider_id: "DEEPSEEK",
        model_id: "deepseek-v4-pro",
        display_name: "DeepSeek V4 Pro",
        description: "适合深度推理与专业分析。",
        family: "DeepSeek · 官方直连",
        chat_selectable: true,
    },
    PresetDescriptor {
        id: "DEEPSEEK_V4_FLASH",
        provider_id: "DEEPSEEK",
        model_id: "deepseek-v4-flash",
        display_name: "DeepSeek V4 Flash",
        description: "适合快速问答与高频文本任务。",
        family: "DeepSeek · 官方直连",
        chat_selectable: true,
    },
    PresetDescriptor {
        id: "GLM_5_3",
        provider_id: "ZHIPU",
        model_id: "glm-5.3",
        display_name: "GLM-5.3",
        description: "适合深度推理、复杂分析与 Agent 任务。",
        family: "智谱 · 官方直连",
        chat_selectable: true,
    },
    PresetDescriptor {
        id: "GLM_5_3_FLASH",
        provider_id: "ZHIPU",
        model_id: "glm-5.3-flash",
        display_name: "GLM-5.3 Flash",
        description: "智谱官方直连的快速文本任务。",
        family: "智谱 · 官方直连",
        chat_selectable: true,
    },
    PresetDescriptor {
        id: "QWEN_3_7_PLUS",
        provider_id: "QWEN",
        model_id: "qwen3.7-plus",
        display_name: "Qwen3.7-Plus",
        description: "日常问答与轻量多媒体任务。",
        family: "Qwen · 官方直连",
        chat_selectable: true,
    },
    PresetDescriptor {
        id: "QWEN_3_8_MAX",
        provider_id: "QWEN",
        model_id: "qwen3.8-max",
        display_name: "Qwen3.8-Max",
        description: "适合深度分析与复杂任务。",
        family: "Qwen · 官方直连",
        chat_selectable: true,
    },
];

pub const QWEN_FLASH: PresetDescriptor = PresetDescriptor {
    id: "QWEN_3_6_FLASH",
    provider_id: "QWEN",
    model_id: "qwen3.6-flash",
    display_name: "Qwen3.6 Flash",
    description: "适合大批量知识整理与快速检索。",
    family: "Qwen · 官方直连",
    chat_selectable: true,
};
pub const GLM_OCR: PresetDescriptor = PresetDescriptor {
    id: "GLM_OCR",
    provider_id: "ZHIPU",
    model_id: "glm-ocr",
    display_name: "GLM-OCR",
    description: "图片与 PDF 转 Markdown；仅在南枫转写中使用。",
    family: "智谱 · 官方直连",
    chat_selectable: false,
};
pub const QWEN_ASR: PresetDescriptor = PresetDescriptor {
    id: "QWEN_3_ASR",
    provider_id: "QWEN",
    model_id: "qwen3-asr-flash",
    display_name: "Qwen3-ASR Flash",
    description: "音频与视频转文字；仅在语音转写任务中使用。",
    family: "Qwen · 官方直连",
    chat_selectable: false,
};

pub fn provider(provider_id: &str) -> Result<&'static ProviderDescriptor, String> {
    PROVIDERS
        .iter()
        .find(|item| item.id == provider_id)
        .ok_or_else(|| "模型服务商无效。".to_owned())
}

pub fn presets_for(provider_id: &str) -> Result<Vec<PresetDescriptor>, String> {
    validate_provider(provider_id)?;
    let mut items = PRESETS
        .iter()
        .copied()
        .filter(|item| item.provider_id == provider_id && item.chat_selectable)
        .collect::<Vec<_>>();
    if provider_id == "QWEN" {
        items.push(QWEN_FLASH);
    }
    Ok(items)
}

pub fn chat_presets() -> Vec<PresetDescriptor> {
    PRESETS
        .into_iter()
        .chain(std::iter::once(QWEN_FLASH))
        .collect()
}

pub fn preset(provider_id: &str, preset_id: &str) -> Result<PresetDescriptor, String> {
    presets_for(provider_id)?
        .into_iter()
        .find(|item| item.id == preset_id)
        .ok_or_else(|| "所选模型不属于当前服务商，未保存设置。".to_owned())
}

pub fn validate_provider(provider_id: &str) -> Result<(), String> {
    provider(provider_id).map(|_| ())
}

pub fn validate_configuration(provider_id: &str, preset_id: &str) -> Result<(), String> {
    preset(provider_id, preset_id).map(|_| ())
}

pub fn keychain_service(provider_id: &str) -> Result<String, String> {
    validate_provider(provider_id)?;
    Ok(format!(
        "com.nanzhufeng.ai.desktop.provider.{}.v1",
        provider_id.to_ascii_lowercase()
    ))
}

pub fn validate_secret(secret: &[u8]) -> Result<(), String> {
    if !(16..=512).contains(&secret.len()) || !secret.iter().all(|value| value.is_ascii_graphic()) {
        return Err("API Key 格式无效；请输入 16–512 个可见字符。".to_owned());
    }
    Ok(())
}

/// Sends one bounded, user-triggered connectivity probe to the fixed provider endpoint. The
/// response body is intentionally discarded so a provider error can never project prompt,
/// credential, account, or model output into Settings or logs.
pub async fn test_saved_connection(
    provider_id: &str,
    preset_id: &str,
    secret: Zeroizing<Vec<u8>>,
) -> Result<(), String> {
    validate_configuration(provider_id, preset_id)?;
    validate_secret(secret.as_slice())?;
    let provider = provider(provider_id)?;
    let preset = preset(provider_id, preset_id)?;
    let authorization = Zeroizing::new(
        String::from_utf8(secret.to_vec())
            .map_err(|_| "本机 API Key 格式无效；请重新保存。".to_owned())?,
    );
    let url = format!(
        "{}/chat/completions",
        provider.endpoint.trim_end_matches('/')
    );
    let response = reqwest::Client::builder()
        .connect_timeout(std::time::Duration::from_secs(10))
        .timeout(std::time::Duration::from_secs(25))
        .build()
        .map_err(|_| "无法创建安全连接；未发送测试请求。".to_owned())?
        .post(url)
        .bearer_auth(authorization.as_str())
        .json(&json!({
            "model": preset.model_id,
            "messages": [{ "role": "user", "content": "hi" }],
            "max_tokens": 1,
            "stream": false
        }))
        .send()
        .await
        .map_err(|_| "连接测试失败；请检查网络、服务状态和 API Key。".to_owned())?;
    if !response.status().is_success() {
        return Err(format!(
            "连接测试未通过（HTTP {}）；请检查 API Key、余额和模型权限。",
            response.status().as_u16()
        ));
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::{cell::RefCell, collections::BTreeMap};

    struct MemoryCredentials(RefCell<BTreeMap<String, Vec<u8>>>);
    impl ProviderCredentialStore for MemoryCredentials {
        fn presence(&self, provider_id: &str) -> CredentialPresence {
            if self.0.borrow().contains_key(provider_id) {
                CredentialPresence::Stored
            } else {
                CredentialPresence::MissingOrUnavailable
            }
        }
        fn save_user_provided_secret(
            &self,
            provider_id: &str,
            secret: &[u8],
        ) -> Result<(), String> {
            validate_provider(provider_id)?;
            validate_secret(secret)?;
            self.0
                .borrow_mut()
                .insert(provider_id.to_owned(), secret.to_vec());
            Ok(())
        }
        fn with_secret<T>(
            &self,
            provider_id: &str,
            operation: impl FnOnce(&[u8]) -> Result<T, String>,
        ) -> Result<T, String> {
            let value = Zeroizing::new(
                self.0
                    .borrow()
                    .get(provider_id)
                    .cloned()
                    .ok_or_else(|| "missing".to_owned())?,
            );
            operation(value.as_slice())
        }
        fn reveal_user_requested_secret(
            &self,
            provider_id: &str,
        ) -> Result<Zeroizing<String>, String> {
            let value = self
                .0
                .borrow()
                .get(provider_id)
                .cloned()
                .ok_or_else(|| "missing".to_owned())?;
            Ok(Zeroizing::new(String::from_utf8(value).unwrap()))
        }
        fn delete_user_secret(&self, provider_id: &str) -> Result<(), String> {
            validate_provider(provider_id)?;
            self.0.borrow_mut().remove(provider_id);
            Ok(())
        }
    }

    #[test]
    fn provider_order_endpoints_and_defaults_match_current_android_contract() {
        assert_eq!(
            PROVIDERS.map(|item| item.id),
            ["OPENROUTER", "DEEPSEEK", "ZHIPU", "QWEN"]
        );
        assert_eq!(
            provider("DEEPSEEK").unwrap().endpoint,
            "https://api.deepseek.com/v1"
        );
        assert_eq!(
            provider("ZHIPU").unwrap().default_preset_id,
            "GLM_5_3_FLASH"
        );
        assert_eq!(provider("QWEN").unwrap().default_preset_id, "QWEN_3_7_PLUS");
    }

    #[test]
    fn retired_grok_models_cannot_enter_current_settings() {
        for retired in ["GROK_4_1_FAST", "GROK_4_5", "GROK_4_6_HIGH"] {
            assert!(preset("OPENROUTER", retired).is_err());
        }
    }

    #[test]
    fn glm_ocr_is_visible_capability_but_never_chat_selectable() {
        assert_eq!(GLM_OCR.provider_id, "ZHIPU");
        assert!(!GLM_OCR.chat_selectable);
        assert!(!presets_for("ZHIPU")
            .unwrap()
            .iter()
            .any(|item| item.id == GLM_OCR.id));
    }

    #[test]
    fn legacy_qwen_asr_definition_remains_non_chat_selectable_for_saved_tasks() {
        assert_eq!(QWEN_ASR.provider_id, "QWEN");
        assert_eq!(QWEN_ASR.model_id, "qwen3-asr-flash");
        assert!(!QWEN_ASR.chat_selectable);
        assert!(!presets_for("QWEN")
            .unwrap()
            .iter()
            .any(|item| item.id == QWEN_ASR.id));
    }

    #[test]
    fn credentials_are_fixed_provider_scoped_and_only_revealed_explicitly() {
        let store = MemoryCredentials(RefCell::new(BTreeMap::new()));
        assert_eq!(
            store.presence("OPENROUTER"),
            CredentialPresence::MissingOrUnavailable
        );
        store
            .save_user_provided_secret("OPENROUTER", b"abcdefghijklmnop")
            .unwrap();
        assert_eq!(store.presence("OPENROUTER"), CredentialPresence::Stored);
        assert_eq!(
            store
                .with_secret("OPENROUTER", |value| Ok(value.len()))
                .unwrap(),
            16
        );
        assert_eq!(
            store
                .reveal_user_requested_secret("OPENROUTER")
                .unwrap()
                .as_str(),
            "abcdefghijklmnop"
        );
        assert!(store
            .save_user_provided_secret("unknown", b"abcdefghijklmnop")
            .is_err());
        assert!(store
            .save_user_provided_secret("QWEN", b"too-short")
            .is_err());
        store.delete_user_secret("OPENROUTER").unwrap();
        assert_eq!(
            store.presence("OPENROUTER"),
            CredentialPresence::MissingOrUnavailable
        );
    }
}

#[cfg(test)]
mod keychain_status_tests {
    use super::*;
    #[test]
    fn permission_failure_never_means_deleted() {
        assert_eq!(credential_presence_from_status(0), CredentialPresence::Stored);
        assert_eq!(credential_presence_from_status(-25300), CredentialPresence::MissingOrUnavailable);
        for status in [-128, -25293, -25308, -50] {
            assert_eq!(credential_presence_from_status(status), CredentialPresence::Unavailable);
            assert!(!credential_read_error(status).contains("尚未保存"));
        }
    }
    #[test]
    fn denied_provider_stays_blocked_until_user_retry() {
        let id = "KEYCHAIN_TEST_ONLY_DENIED";
        DENIED_PROVIDERS.lock().unwrap().push(id.into());
        allow_user_credential_retry("KEYCHAIN_TEST_OTHER_PROVIDER");
        assert!(DENIED_PROVIDERS.lock().unwrap().iter().any(|p| p == id));
        allow_user_credential_retry(id);
        assert!(!DENIED_PROVIDERS.lock().unwrap().iter().any(|p| p == id));
    }
}
