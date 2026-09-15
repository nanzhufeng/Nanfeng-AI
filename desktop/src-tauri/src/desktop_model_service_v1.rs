//! Desktop model-service settings shared by Settings and the future ordinary-chat executor.
//! Provider endpoints and preset ownership are product constants.
//! Safe projections expose credential presence only; a secret is returned only by the explicit
//! user-triggered reveal command.

use aes_gcm::{
    aead::{Aead, KeyInit},
    Aes256Gcm, Nonce,
};
use base64::{engine::general_purpose::STANDARD as BASE64, Engine as _};
use serde::{Deserialize, Serialize};
use serde_json::json;
use std::{
    collections::BTreeMap,
    fs,
    path::{Path, PathBuf},
    sync::Mutex,
};
use zeroize::Zeroizing;
/// Serialize read/write operations across chat, settings and background jobs.
static CREDENTIAL_ACCESS_LOCK: Mutex<()> = Mutex::new(());
// Retired compatibility type. Provider API keys have no system-credential path;
// all settings and model calls use `AppPrivateProviderCredentialStore` below.
#[allow(dead_code)]
pub struct MacSecurityFrameworkProviderCredentialStore;

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

/// App-owned credential storage. API keys stay outside SQLite, backups, sync payloads and logs;
/// the encrypted payload plus its install-local key live only in the app's private data root.
/// This deliberately avoids macOS Keychain and any system authorization UI.
#[derive(Clone)]
pub struct AppPrivateProviderCredentialStore {
    root: PathBuf,
}

#[derive(Debug, Default, Serialize, Deserialize)]
struct PrivateCredentialDocument {
    version: u8,
    #[serde(default)]
    providers: BTreeMap<String, PrivateCredentialRecord>,
}

#[derive(Debug, Serialize, Deserialize)]
struct PrivateCredentialRecord {
    nonce: String,
    ciphertext: String,
}

impl AppPrivateProviderCredentialStore {
    pub fn at(workspace_root: impl AsRef<Path>) -> Self {
        Self {
            root: workspace_root.as_ref().join("provider-credentials-v1"),
        }
    }

    fn key_path(&self) -> PathBuf {
        self.root.join("local.key")
    }
    fn document_path(&self) -> PathBuf {
        self.root.join("credentials.json")
    }

    fn ensure_private_root(&self) -> Result<(), String> {
        fs::create_dir_all(&self.root)
            .map_err(|_| "应用私有凭据目录无法创建；未保存 API Key。".to_owned())?;
        set_owner_only(&self.root, true)
    }

    fn load_key(&self, create_if_missing: bool) -> Result<[u8; 32], String> {
        let path = self.key_path();
        match fs::read(&path) {
            Ok(bytes) => {
                let key: [u8; 32] = bytes
                    .try_into()
                    .map_err(|_| "应用私有凭据密钥无效；请重新保存 API Key。".to_owned())?;
                set_owner_only(&path, false)?;
                Ok(key)
            }
            Err(error) if error.kind() == std::io::ErrorKind::NotFound && create_if_missing => {
                self.ensure_private_root()?;
                let mut key = [0u8; 32];
                getrandom::fill(&mut key)
                    .map_err(|_| "无法生成应用私有凭据密钥；未保存 API Key。".to_owned())?;
                let mut options = fs::OpenOptions::new();
                options.write(true).create_new(true);
                #[cfg(unix)]
                {
                    use std::os::unix::fs::OpenOptionsExt;
                    options.mode(0o600);
                }
                match options.open(&path) {
                    Ok(mut file) => {
                        use std::io::Write as _;
                        file.write_all(&key)
                            .map_err(|_| "应用私有凭据密钥无法写入；未保存 API Key。".to_owned())?;
                        file.sync_all()
                            .map_err(|_| "应用私有凭据密钥无法提交；未保存 API Key。".to_owned())?;
                        set_owner_only(&path, false)?;
                        Ok(key)
                    }
                    Err(error) if error.kind() == std::io::ErrorKind::AlreadyExists => {
                        self.load_key(false)
                    }
                    Err(_) => Err("应用私有凭据密钥无法写入；未保存 API Key。".to_owned()),
                }
            }
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
                Err("本机尚未保存该服务商的 API Key。".to_owned())
            }
            Err(_) => Err("应用私有凭据无法读取；请重新保存 API Key。".to_owned()),
        }
    }

    fn read_document(&self) -> Result<PrivateCredentialDocument, String> {
        match fs::read(self.document_path()) {
            Ok(bytes) => serde_json::from_slice(&bytes)
                .map_err(|_| "应用私有凭据记录无效；请重新保存 API Key。".to_owned()),
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
                Ok(PrivateCredentialDocument {
                    version: 1,
                    ..Default::default()
                })
            }
            Err(_) => Err("应用私有凭据无法读取；请重新保存 API Key。".to_owned()),
        }
    }

    fn write_document(&self, document: &PrivateCredentialDocument) -> Result<(), String> {
        self.ensure_private_root()?;
        let encoded = serde_json::to_vec(document)
            .map_err(|_| "应用私有凭据无法编码；未保存 API Key。".to_owned())?;
        let temporary = self
            .root
            .join(format!(".credentials-{}.tmp", std::process::id()));
        fs::write(&temporary, encoded)
            .map_err(|_| "应用私有凭据无法写入；未保存 API Key。".to_owned())?;
        set_owner_only(&temporary, false)?;
        fs::rename(&temporary, self.document_path())
            .map_err(|_| "应用私有凭据无法提交；未保存 API Key。".to_owned())?;
        set_owner_only(&self.document_path(), false)
    }

    fn decrypt(&self, record: &PrivateCredentialRecord, key: &[u8; 32]) -> Result<Vec<u8>, String> {
        let nonce = BASE64
            .decode(&record.nonce)
            .map_err(|_| "应用私有凭据记录无效；请重新保存 API Key。".to_owned())?;
        let ciphertext = BASE64
            .decode(&record.ciphertext)
            .map_err(|_| "应用私有凭据记录无效；请重新保存 API Key。".to_owned())?;
        if nonce.len() != 12 {
            return Err("应用私有凭据记录无效；请重新保存 API Key。".to_owned());
        }
        Aes256Gcm::new_from_slice(key)
            .map_err(|_| "应用私有凭据密钥无效；请重新保存 API Key。".to_owned())?
            .decrypt(Nonce::from_slice(&nonce), ciphertext.as_ref())
            .map_err(|_| "应用私有凭据无法解密；请重新保存 API Key。".to_owned())
    }

    /// Full local-data deletion removes both ciphertext and its install-local key.
    pub fn delete_all(&self) -> Result<(), String> {
        let _access = CREDENTIAL_ACCESS_LOCK
            .lock()
            .map_err(|_| "应用私有凭据状态忙，请重试。".to_owned())?;
        if self.root.exists() {
            fs::remove_dir_all(&self.root)
                .map_err(|_| "应用私有凭据无法清理；请重试全部本地数据清理。".to_owned())?;
        }
        Ok(())
    }
}

impl ProviderCredentialStore for AppPrivateProviderCredentialStore {
    fn presence(&self, provider_id: &str) -> CredentialPresence {
        if validate_provider(provider_id).is_err() {
            return CredentialPresence::Unavailable;
        }
        match self.read_document() {
            Ok(document) if document.providers.contains_key(provider_id) => {
                CredentialPresence::Stored
            }
            Ok(_) => CredentialPresence::MissingOrUnavailable,
            Err(_) => CredentialPresence::Unavailable,
        }
    }

    fn save_user_provided_secret(&self, provider_id: &str, secret: &[u8]) -> Result<(), String> {
        validate_provider(provider_id)?;
        validate_secret(secret)?;
        let _access = CREDENTIAL_ACCESS_LOCK
            .lock()
            .map_err(|_| "应用私有凭据状态忙，请重试。".to_owned())?;
        let key = self.load_key(true)?;
        let mut nonce = [0u8; 12];
        getrandom::fill(&mut nonce)
            .map_err(|_| "无法生成应用私有凭据随机数；未保存 API Key。".to_owned())?;
        let ciphertext = Aes256Gcm::new_from_slice(&key)
            .map_err(|_| "应用私有凭据密钥无效；未保存 API Key。".to_owned())?
            .encrypt(Nonce::from_slice(&nonce), secret)
            .map_err(|_| "应用私有凭据无法加密；未保存 API Key。".to_owned())?;
        let mut document = self.read_document()?;
        document.version = 1;
        document.providers.insert(
            provider_id.to_owned(),
            PrivateCredentialRecord {
                nonce: BASE64.encode(nonce),
                ciphertext: BASE64.encode(ciphertext),
            },
        );
        self.write_document(&document)
    }

    fn with_secret<T>(
        &self,
        provider_id: &str,
        operation: impl FnOnce(&[u8]) -> Result<T, String>,
    ) -> Result<T, String> {
        validate_provider(provider_id)?;
        let _access = CREDENTIAL_ACCESS_LOCK
            .lock()
            .map_err(|_| "应用私有凭据状态忙，请重试。".to_owned())?;
        let key = self.load_key(false)?;
        let document = self.read_document()?;
        let record = document
            .providers
            .get(provider_id)
            .ok_or_else(|| "本机尚未保存该服务商的 API Key。".to_owned())?;
        let secret = Zeroizing::new(self.decrypt(record, &key)?);
        operation(secret.as_slice())
    }

    fn reveal_user_requested_secret(&self, provider_id: &str) -> Result<Zeroizing<String>, String> {
        self.with_secret(provider_id, |bytes| {
            String::from_utf8(bytes.to_vec())
                .map(Zeroizing::new)
                .map_err(|_| "本机 API Key 格式无效；请重新保存。".to_owned())
        })
    }

    fn delete_user_secret(&self, provider_id: &str) -> Result<(), String> {
        validate_provider(provider_id)?;
        let _access = CREDENTIAL_ACCESS_LOCK
            .lock()
            .map_err(|_| "应用私有凭据状态忙，请重试。".to_owned())?;
        let mut document = self.read_document()?;
        document.providers.remove(provider_id);
        self.write_document(&document)
    }
}

fn set_owner_only(path: &Path, directory: bool) -> Result<(), String> {
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        fs::set_permissions(
            path,
            fs::Permissions::from_mode(if directory { 0o700 } else { 0o600 }),
        )
        .map_err(|_| "应用私有凭据权限无法收紧；未保存 API Key。".to_owned())?;
    }
    #[cfg(not(unix))]
    {
        let _ = (path, directory);
    }
    Ok(())
}

impl ProviderCredentialStore for MacSecurityFrameworkProviderCredentialStore {
    fn presence(&self, _provider_id: &str) -> CredentialPresence {
        // This retired compatibility type is permanently fail-closed. Provider
        // API keys have one product owner: AppPrivateProviderCredentialStore.
        CredentialPresence::Unavailable
    }

    fn save_user_provided_secret(&self, provider_id: &str, secret: &[u8]) -> Result<(), String> {
        validate_provider(provider_id)?;
        validate_secret(secret)?;
        Err("此兼容凭据入口已停用；API Key 仅由应用私有加密存储管理。".to_owned())
    }

    fn with_secret<T>(
        &self,
        provider_id: &str,
        _operation: impl FnOnce(&[u8]) -> Result<T, String>,
    ) -> Result<T, String> {
        validate_provider(provider_id)?;
        Err("此兼容凭据入口已停用；API Key 仅由应用私有加密存储管理。".to_owned())
    }

    fn reveal_user_requested_secret(&self, provider_id: &str) -> Result<Zeroizing<String>, String> {
        validate_provider(provider_id)?;
        Err("此兼容凭据入口已停用；API Key 仅由应用私有加密存储管理。".to_owned())
    }

    fn delete_user_secret(&self, provider_id: &str) -> Result<(), String> {
        validate_provider(provider_id)?;
        Ok(())
    }
}

impl MacSecurityFrameworkProviderCredentialStore {
    /// Retained only for source compatibility; it cannot read any Provider API key.
    pub fn with_user_authorized_secret<T>(
        &self,
        provider_id: &str,
        _operation: impl FnOnce(&[u8]) -> Result<T, String>,
    ) -> Result<T, String> {
        validate_provider(provider_id)?;
        Err("此兼容凭据入口已停用；API Key 仅由应用私有加密存储管理。".to_owned())
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
    PresetDescriptor {
        id: "CLAUDE_FABLE_5_1",
        provider_id: "OPENROUTER",
        model_id: "anthropic/claude-fable-5.1-20260831",
        display_name: "Claude Fable 5.1",
        description: "适合长程代码、研究与复杂知识工作。",
        family: "Anthropic · OpenRouter",
        chat_selectable: true,
    },
    PresetDescriptor {
        id: "GPT_6_ASTRA",
        provider_id: "OPENROUTER",
        model_id: "openai/gpt-6-astra",
        display_name: "GPT-6 Astra",
        description: "适合高难分析、工程与长程复杂任务。",
        family: "OpenAI · OpenRouter",
        chat_selectable: true,
    },
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
        id: "GEMINI_3_8_FLASH",
        provider_id: "OPENROUTER",
        model_id: "google/gemini-3.8-flash",
        display_name: "Gemini 3.8 Flash",
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
        model_id: "deepseek-flash",
        display_name: "DeepSeek V4.1 Flash",
        description: "适合快速问答、高频任务与图片理解。",
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

/// Product-level identity of the DeepSeek entry shown in the 日常 model menu.  Background
/// text jobs must refer to this semantic preset rather than carrying a second model-id copy.
pub const DAILY_DEEPSEEK_PRESET_ID: &str = "DEEPSEEK_V4_FLASH";

/// Title and history refinement share the same low-cost provider fallback order.  The first
/// item is deliberately the 日常 DeepSeek preset above, so changing that descriptor updates
/// ordinary chat, titles and history together.
pub const BACKGROUND_TEXT_REFINEMENT_PRESET_IDS: [&str; 3] =
    [DAILY_DEEPSEEK_PRESET_ID, "GLM_5_3_FLASH", "QWEN_3_6_FLASH"];

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

    #[test]
    fn background_text_jobs_start_with_the_same_daily_deepseek_preset() {
        assert_eq!(
            BACKGROUND_TEXT_REFINEMENT_PRESET_IDS.first(),
            Some(&DAILY_DEEPSEEK_PRESET_ID),
        );
        let daily = chat_presets()
            .into_iter()
            .find(|preset| preset.id == DAILY_DEEPSEEK_PRESET_ID)
            .unwrap();
        assert_eq!(daily.display_name, "DeepSeek V4.1 Flash");
        assert_eq!(daily.model_id, "deepseek-flash");
    }

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
    fn app_private_credentials_are_provider_scoped_encrypted_and_deletable() {
        let root = tempfile::tempdir().unwrap();
        let store = AppPrivateProviderCredentialStore::at(root.path());
        let secret = b"abcdefghijklmnop";
        store.save_user_provided_secret("ZHIPU", secret).unwrap();
        assert_eq!(store.presence("ZHIPU"), CredentialPresence::Stored);
        assert_eq!(
            store
                .with_secret("ZHIPU", |value| Ok(value.to_vec()))
                .unwrap(),
            secret
        );
        let document = fs::read_to_string(store.document_path()).unwrap();
        assert!(!document.contains("abcdefghijklmnop"));
        store.delete_user_secret("ZHIPU").unwrap();
        assert_eq!(
            store.presence("ZHIPU"),
            CredentialPresence::MissingOrUnavailable
        );
        store.delete_all().unwrap();
        assert!(!store.root.exists());
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
mod private_credential_status_tests {
    use super::*;

    #[test]
    fn retired_system_credential_owner_is_permanently_disabled() {
        let retired = MacSecurityFrameworkProviderCredentialStore;
        assert_eq!(
            retired.presence("DEEPSEEK"),
            CredentialPresence::Unavailable
        );
        assert!(retired
            .with_user_authorized_secret("DEEPSEEK", |_| Ok(()))
            .is_err());
    }
}
