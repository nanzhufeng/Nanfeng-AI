//! Desktop model-service settings shared by Settings and the future ordinary-chat executor.
//! Provider endpoints and preset ownership are product constants.
//! Safe projections expose credential presence only; a secret is returned only by the explicit
//! user-triggered reveal command.

use aes_gcm::{
    aead::{Aead, KeyInit, Payload},
    Aes256Gcm, Nonce,
};
use base64::{engine::general_purpose::STANDARD as BASE64, Engine as _};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
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

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
struct PrivateCredentialRecord {
    nonce: String,
    ciphertext: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct ModelSettingState {
    pub provider_id: String,
    pub enabled: bool,
    pub preset_id: String,
    pub revision: u64,
}

const PENDING_MODEL_SETTING_VERSION: u8 = 2;
const PENDING_MODEL_SETTING_CONTEXT: &str = "nanfeng-ai-model-setting-pending-v2";

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct PendingModelSetting {
    version: u8,
    before: ModelSettingState,
    after: ModelSettingState,
    previous: Option<PrivateCredentialRecord>,
    replacement: PrivateCredentialRecord,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    integrity: Option<PendingModelSettingIntegrity>,
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct PendingModelSettingIntegrity {
    nonce: String,
    tag: String,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) enum ModelSettingResolution {
    RestoredPrevious,
    CommittedNew,
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

    fn pending_model_setting_path(&self) -> PathBuf {
        self.root.join("model-setting-pending-v1.json")
    }

    fn read_pending_model_setting(&self) -> Result<Option<PendingModelSetting>, String> {
        let path = self.pending_model_setting_path();
        let metadata = match fs::symlink_metadata(&path) {
            Ok(metadata) => metadata,
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(None),
            Err(_) => return Err("模型凭据待决记录不可读；请重开应用。".to_owned()),
        };
        if !metadata.is_file() || metadata.len() == 0 || metadata.len() > 8192 {
            return Err("模型凭据待决记录无效；请重开应用。".to_owned());
        }
        set_owner_only(&path, false)?;
        let bytes = fs::read(&path)
            .map_err(|_| "模型凭据待决记录不可读；请重开应用。".to_owned())?;
        let pending: PendingModelSetting = serde_json::from_slice(&bytes)
            .map_err(|_| "模型凭据待决记录无效；请重开应用。".to_owned())?;
        if pending.version != PENDING_MODEL_SETTING_VERSION
            || pending.before.provider_id != pending.after.provider_id
            || pending.before.revision.checked_add(1) != Some(pending.after.revision)
            || stored_preset(&pending.before.provider_id, &pending.before.preset_id).is_err()
            || stored_preset(&pending.after.provider_id, &pending.after.preset_id).is_err()
        {
            return Err("模型凭据待决记录无效；请重开应用。".to_owned());
        }
        self.verify_pending_model_setting_integrity(&pending)?;
        Ok(Some(pending))
    }

    fn pending_model_setting_payload(pending: &PendingModelSetting) -> Result<Vec<u8>, String> {
        serde_json::to_vec(&(
            PENDING_MODEL_SETTING_CONTEXT,
            pending.version,
            &pending.before,
            &pending.after,
            &pending.previous,
            &pending.replacement,
        ))
        .map_err(|_| "模型凭据待决记录无法编码；未保存 API Key。".to_owned())
    }

    fn sign_pending_model_setting(
        &self,
        pending: &PendingModelSetting,
    ) -> Result<PendingModelSettingIntegrity, String> {
        let key = Zeroizing::new(self.load_key(false)?);
        let mut nonce = [0_u8; 12];
        getrandom::fill(&mut nonce)
            .map_err(|_| "模型凭据待决记录无法签封；未保存 API Key。".to_owned())?;
        let payload = Self::pending_model_setting_payload(pending)?;
        let tag = Aes256Gcm::new_from_slice(&key[..])
            .map_err(|_| "模型凭据待决记录无法签封；未保存 API Key。".to_owned())?
            .encrypt(
                Nonce::from_slice(&nonce),
                Payload {
                    msg: &[],
                    aad: &payload,
                },
            )
            .map_err(|_| "模型凭据待决记录无法签封；未保存 API Key。".to_owned())?;
        Ok(PendingModelSettingIntegrity {
            nonce: BASE64.encode(nonce),
            tag: BASE64.encode(tag),
        })
    }

    fn verify_pending_model_setting_integrity(
        &self,
        pending: &PendingModelSetting,
    ) -> Result<(), String> {
        let invalid = || "模型凭据待决记录完整性无效；保留记录并停止启动。".to_owned();
        let integrity = pending.integrity.as_ref().ok_or_else(invalid)?;
        let nonce = BASE64.decode(&integrity.nonce).map_err(|_| invalid())?;
        let tag = BASE64.decode(&integrity.tag).map_err(|_| invalid())?;
        if nonce.len() != 12 || tag.len() != 16 {
            return Err(invalid());
        }
        let key = Zeroizing::new(self.load_key(false)?);
        let payload = Self::pending_model_setting_payload(pending).map_err(|_| invalid())?;
        let verified = Aes256Gcm::new_from_slice(&key[..])
            .map_err(|_| invalid())?
            .decrypt(
                Nonce::from_slice(&nonce),
                Payload {
                    msg: &tag,
                    aad: &payload,
                },
            )
            .map_err(|_| invalid())?;
        if !verified.is_empty() {
            return Err(invalid());
        }
        Ok(())
    }

    fn write_pending_model_setting(&self, pending: &PendingModelSetting) -> Result<(), String> {
        self.ensure_private_root()?;
        if pending.version != PENDING_MODEL_SETTING_VERSION {
            return Err("模型凭据待决记录版本无效；未保存 API Key。".to_owned());
        }
        let mut pending = pending.clone();
        pending.integrity = Some(self.sign_pending_model_setting(&pending)?);
        let bytes = serde_json::to_vec(&pending)
            .map_err(|_| "模型凭据待决记录无法编码；未保存 API Key。".to_owned())?;
        #[cfg(windows)]
        {
            crate::desktop_windows_private_store::atomic_write_private(
                &self.pending_model_setting_path(),
                &bytes,
            )
            .map_err(|_| "模型凭据待决记录无法提交；未保存 API Key。".to_owned())
        }
        #[cfg(not(windows))]
        {
            let temporary = self.root.join(format!(
                ".model-setting-pending-{}.tmp",
                std::process::id()
            ));
            let result = (|| {
                use std::io::Write as _;
                let mut options = fs::OpenOptions::new();
                options.write(true).create_new(true);
                #[cfg(unix)]
                {
                    use std::os::unix::fs::OpenOptionsExt;
                    options.mode(0o600);
                }
                let mut file = options
                    .open(&temporary)
                    .map_err(|_| "模型凭据待决记录无法写入；未保存 API Key。".to_owned())?;
                file.write_all(&bytes)
                    .map_err(|_| "模型凭据待决记录无法写入；未保存 API Key。".to_owned())?;
                file.sync_all()
                    .map_err(|_| "模型凭据待决记录无法提交；未保存 API Key。".to_owned())?;
                drop(file);
                set_owner_only(&temporary, false)?;
                fs::rename(&temporary, self.pending_model_setting_path())
                    .map_err(|_| "模型凭据待决记录无法提交；未保存 API Key。".to_owned())?;
                Ok(())
            })();
            if result.is_err() {
                let _ = fs::remove_file(&temporary);
            }
            result
        }
    }

    fn ensure_private_root(&self) -> Result<(), String> {
        fs::create_dir_all(&self.root)
            .map_err(|_| "应用私有凭据目录无法创建；未保存 API Key。".to_owned())?;
        set_owner_only(&self.root, true)
    }

    fn load_key(&self, create_if_missing: bool) -> Result<[u8; 32], String> {
        let path = self.key_path();
        #[cfg(windows)]
        {
            use crate::desktop_windows_private_store::KeyLoadError;
            return crate::desktop_windows_private_store::load_or_create_key(
                &self.root,
                &path,
                create_if_missing,
            )
            .map_err(|error| match error {
                KeyLoadError::Missing => "本机尚未保存该服务商的 API Key。".to_owned(),
                KeyLoadError::Invalid => {
                    "应用私有凭据密钥无效；请重新保存 API Key。".to_owned()
                }
                KeyLoadError::Unavailable => {
                    "应用私有凭据无法读取；请重新保存 API Key。".to_owned()
                }
            });
        }
        #[cfg(not(windows))]
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
        let path = self.document_path();
        match fs::symlink_metadata(&path) {
            Ok(_) => set_owner_only(&path, false)?,
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
            Err(_) => return Err("应用私有凭据无法读取；请重新保存 API Key。".to_owned()),
        }
        match fs::read(path) {
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
        #[cfg(windows)]
        {
            crate::desktop_windows_private_store::atomic_write_private(
                &self.document_path(),
                &encoded,
            )
            .map_err(|_| "应用私有凭据无法提交；未保存 API Key。".to_owned())
        }
        #[cfg(not(windows))]
        {
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
    }

    fn encrypt_record(&self, secret: &[u8]) -> Result<PrivateCredentialRecord, String> {
        let key = self.load_key(true)?;
        let mut nonce = [0u8; 12];
        getrandom::fill(&mut nonce)
            .map_err(|_| "无法生成应用私有凭据随机数；未保存 API Key。".to_owned())?;
        let ciphertext = Aes256Gcm::new_from_slice(&key)
            .map_err(|_| "应用私有凭据密钥无效；未保存 API Key。".to_owned())?
            .encrypt(Nonce::from_slice(&nonce), secret)
            .map_err(|_| "应用私有凭据无法加密；未保存 API Key。".to_owned())?;
        Ok(PrivateCredentialRecord {
            nonce: BASE64.encode(nonce),
            ciphertext: BASE64.encode(ciphertext),
        })
    }

    pub(crate) fn begin_model_setting_secret_update(
        &self,
        before: ModelSettingState,
        after: ModelSettingState,
        secret: &[u8],
    ) -> Result<(), String> {
        validate_secret(secret)?;
        if before.provider_id != after.provider_id
            || before.revision.checked_add(1) != Some(after.revision)
        {
            return Err("模型设置待决版本无效；未保存 API Key。".to_owned());
        }
        // Recovery also accepts known retired identities; new choices are
        // validated against the active catalog at the command boundary.
        stored_preset(&before.provider_id, &before.preset_id)?;
        stored_preset(&after.provider_id, &after.preset_id)?;
        let _access = CREDENTIAL_ACCESS_LOCK
            .lock()
            .map_err(|_| "应用私有凭据状态忙，请重试。".to_owned())?;
        #[cfg(windows)]
        let _process_access = crate::desktop_windows_private_store::lock_store(&self.root)
            .map_err(|_| "应用私有凭据状态忙，请重试。".to_owned())?;
        if self.read_pending_model_setting()?.is_some() {
            return Err("模型凭据更新待恢复；请重开应用。".to_owned());
        }
        let replacement = self.encrypt_record(secret)?;
        let mut document = self.read_document()?;
        let pending = PendingModelSetting {
            version: PENDING_MODEL_SETTING_VERSION,
            previous: document.providers.get(&before.provider_id).cloned(),
            replacement: replacement.clone(),
            before,
            after,
            integrity: None,
        };
        self.write_pending_model_setting(&pending)?;
        document
            .providers
            .insert(pending.after.provider_id.clone(), replacement);
        self.write_document(&document)
    }

    pub(crate) fn pending_model_setting_states(
        &self,
    ) -> Result<Option<(ModelSettingState, ModelSettingState)>, String> {
        let _access = CREDENTIAL_ACCESS_LOCK
            .lock()
            .map_err(|_| "应用私有凭据状态忙，请重试。".to_owned())?;
        #[cfg(windows)]
        let _process_access = crate::desktop_windows_private_store::lock_store(&self.root)
            .map_err(|_| "应用私有凭据状态忙，请重试。".to_owned())?;
        Ok(self
            .read_pending_model_setting()?
            .map(|pending| (pending.before, pending.after)))
    }

    pub(crate) fn settle_model_setting_secret_update(
        &self,
        observed: &ModelSettingState,
    ) -> Result<Option<ModelSettingResolution>, String> {
        let _access = CREDENTIAL_ACCESS_LOCK
            .lock()
            .map_err(|_| "应用私有凭据状态忙，请重试。".to_owned())?;
        #[cfg(windows)]
        let _process_access = crate::desktop_windows_private_store::lock_store(&self.root)
            .map_err(|_| "应用私有凭据状态忙，请重试。".to_owned())?;
        let Some(pending) = self.read_pending_model_setting()? else {
            return Ok(None);
        };
        let (record, resolution) = if *observed == pending.before {
            if let Some(previous) = pending.previous.as_ref() {
                let key = self.load_key(false)?;
                let verified = Zeroizing::new(self.decrypt(previous, &key)?);
                validate_secret(verified.as_slice())?;
            }
            (pending.previous, ModelSettingResolution::RestoredPrevious)
        } else if *observed == pending.after {
            let key = self.load_key(false)?;
            let verified = Zeroizing::new(self.decrypt(&pending.replacement, &key)?);
            validate_secret(verified.as_slice())?;
            (Some(pending.replacement), ModelSettingResolution::CommittedNew)
        } else {
            return Err("模型配置与待决凭据不一致；未读取或覆盖 API Key。".to_owned());
        };
        let mut document = self.read_document()?;
        if document.providers.get(&observed.provider_id) != record.as_ref() {
            match record {
                Some(record) => {
                    document.providers.insert(observed.provider_id.clone(), record);
                }
                None => {
                    document.providers.remove(&observed.provider_id);
                }
            }
            self.write_document(&document)?;
        }
        fs::remove_file(self.pending_model_setting_path())
            .map_err(|_| "模型凭据待决记录未完成清理；请重开应用。".to_owned())?;
        Ok(Some(resolution))
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
        #[cfg(windows)]
        let _process_access = crate::desktop_windows_private_store::lock_store(&self.root)
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
        if !self.root.exists() {
            return CredentialPresence::MissingOrUnavailable;
        }
        let Ok(_access) = CREDENTIAL_ACCESS_LOCK.lock() else {
            return CredentialPresence::Unavailable;
        };
        #[cfg(windows)]
        let Ok(_process_access) = crate::desktop_windows_private_store::lock_store(&self.root)
        else {
            return CredentialPresence::Unavailable;
        };
        match self.read_pending_model_setting() {
            Ok(Some(pending)) if pending.before.provider_id == provider_id => {
                return CredentialPresence::Unavailable;
            }
            Ok(_) => {}
            Err(_) => return CredentialPresence::Unavailable,
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
        #[cfg(windows)]
        let _process_access = crate::desktop_windows_private_store::lock_store(&self.root)
            .map_err(|_| "应用私有凭据状态忙，请重试。".to_owned())?;
        if self.read_pending_model_setting()?.is_some() {
            return Err("模型凭据更新待恢复；请重开应用。".to_owned());
        }
        let record = self.encrypt_record(secret)?;
        let mut document = self.read_document()?;
        document.version = 1;
        document.providers.insert(provider_id.to_owned(), record);
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
        #[cfg(windows)]
        let _process_access = crate::desktop_windows_private_store::lock_store(&self.root)
            .map_err(|_| "应用私有凭据状态忙，请重试。".to_owned())?;
        if self
            .read_pending_model_setting()?
            .is_some_and(|pending| pending.before.provider_id == provider_id)
        {
            return Err("模型凭据更新待恢复；请重开应用。".to_owned());
        }
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
        #[cfg(windows)]
        let _process_access = crate::desktop_windows_private_store::lock_store(&self.root)
            .map_err(|_| "应用私有凭据状态忙，请重试。".to_owned())?;
        if self.read_pending_model_setting()?.is_some() {
            return Err("模型凭据更新待恢复；请重开应用。".to_owned());
        }
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
    #[cfg(windows)]
    {
        crate::desktop_windows_private_store::secure_path(path, directory)
            .map_err(|_| "应用私有凭据权限无法收紧；未保存 API Key。".to_owned())?;
    }
    #[cfg(not(any(unix, windows)))]
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

#[cfg(test)]
mod windows_alignment_catalog_tests {
    use super::*;

    #[test]
    fn windows_current_catalog_matches_mac_deployments_and_preserves_groups() {
        assert_eq!(PROVIDERS.iter().map(|p| p.id).collect::<Vec<_>>(),
            vec!["OPENROUTER", "DEEPSEEK", "ZHIPU", "QWEN", "RELAYCORE_CLAUDE", "RELAYCORE_GPT", "RELAYCORE_GEMINI"]);
        let expected = [
            ("OPENROUTER", "CLAUDE_OPUS_5_5", "anthropic/claude-opus-5.5"),
            ("OPENROUTER", "CLAUDE_SONNET_5_5", "anthropic/claude-sonnet-5.5"),
            ("OPENROUTER", "CLAUDE_HAIKU_5_5", "anthropic/claude-haiku-5.5"),
            ("OPENROUTER", "GPT_6_1_SOL", "openai/gpt-6.1-sol"),
            ("OPENROUTER", "GPT_6_LUNA", "openai/gpt-6-luna"),
            ("OPENROUTER", "MUSE_WONDER", "meta/muse-wonder"),
            ("OPENROUTER", "GEMINI_4_ARGON", "google/gemini-4-argon"),
            ("RELAYCORE_CLAUDE", "RELAYCORE_OPUS_5_5", "claude-opus-5.5"),
            ("RELAYCORE_CLAUDE", "RELAYCORE_SONNET_5_5", "claude-sonnet-5-5"),
            ("RELAYCORE_GPT", "RELAYCORE_GPT_6_1_SOL", "gpt-6.1-sol"),
            ("RELAYCORE_GPT", "RELAYCORE_GPT_6_ASTRA", "gpt-6-astra"),
            ("RELAYCORE_GEMINI", "RELAYCORE_GEMINI_3_8_FLASH", "gemini-3.8-flash"),
            ("RELAYCORE_GEMINI", "RELAYCORE_GEMINI_3_1_PRO", "gemini-3.1-pro"),
        ];
        for (provider, id, wire_id) in expected {
            let selected = preset(provider, id).expect("current deployment must be selectable");
            assert_eq!(selected.model_id, wire_id);
        }
        assert_eq!(chat_presets().iter().filter(|p| p.chat_selectable).count(), 23);
        assert!(validate_configuration("ZHIPU", "GLM_OCR").is_err());
        assert!(validate_configuration("RELAYCORE_GPT", "RELAYCORE_OPUS_5_5").is_err());
        assert_eq!(preset("OPENROUTER", "CLAUDE_OPUS_5").unwrap().id, "CLAUDE_OPUS_5_5");
    }

    #[test]
    fn retired_configuration_can_recover_its_previous_pending_credential() {
        let root = tempfile::tempdir().unwrap();
        let store = AppPrivateProviderCredentialStore::at(root.path());
        store.save_user_provided_secret("OPENROUTER", b"previous-synthetic-secret").unwrap();
        let before = ModelSettingState { provider_id: "OPENROUTER".into(), enabled: true,
            preset_id: "CLAUDE_FABLE_5_1".into(), revision: 4 };
        let after = ModelSettingState { preset_id: "GPT_6_LUNA".into(), revision: 5, ..before.clone() };
        store.begin_model_setting_secret_update(before.clone(), after, b"replacement-synthetic-secret").unwrap();
        let reopened = AppPrivateProviderCredentialStore::at(root.path());
        assert_eq!(reopened.settle_model_setting_secret_update(&before).unwrap(),
            Some(ModelSettingResolution::RestoredPrevious));
        assert_eq!(reopened.with_secret("OPENROUTER", |secret| Ok(secret.to_vec())).unwrap(), b"previous-synthetic-secret");
        assert!(preset("OPENROUTER", "CLAUDE_FABLE_5_1").is_err());
    }
}

pub const PROVIDERS: [ProviderDescriptor; 7] = [
    ProviderDescriptor {
        id: "OPENROUTER",
        display_name: "OpenRouter",
        endpoint: "https://openrouter.ai/api/v1",
        default_preset_id: "GPT_6_LUNA",
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
    ProviderDescriptor { id: "RELAYCORE_CLAUDE", display_name: "RelayCore Claude · Kiro正价", endpoint: "https://api.relaycore.cn/v1", default_preset_id: "RELAYCORE_SONNET_5_5" },
    ProviderDescriptor { id: "RELAYCORE_GPT", display_name: "RelayCore GPT · Pro3", endpoint: "https://api.relaycore.cn/v1", default_preset_id: "RELAYCORE_GPT_6_1_SOL" },
    ProviderDescriptor { id: "RELAYCORE_GEMINI", display_name: "RelayCore Gemini", endpoint: "https://api.relaycore.cn/v1", default_preset_id: "RELAYCORE_GEMINI_3_8_FLASH" },
];

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct PresetDescriptor {
    pub id: &'static str,
    pub provider_id: &'static str,
    pub model_id: &'static str,
    pub display_name: &'static str,
    pub description: &'static str,
    pub family: &'static str,
    pub chat_selectable: bool,
}

impl Serialize for PresetDescriptor {
    fn serialize<S:serde::Serializer>(&self,serializer:S)->Result<S::Ok,S::Error> {
        use serde::ser::SerializeStruct;
        let mut row=serializer.serialize_struct("PresetDescriptor",8)?;
        row.serialize_field("id",self.id)?;row.serialize_field("providerId",self.provider_id)?;
        row.serialize_field("modelId",self.model_id)?;row.serialize_field("displayName",self.display_name)?;
        row.serialize_field("description",self.description)?;row.serialize_field("family",self.family)?;
        row.serialize_field("chatSelectable",&self.chat_selectable)?;
        row.serialize_field("nativeInputTypes",&crate::attachment_input_policy_v1::native_types(self.provider_id,self.model_id))?;
        row.end()
    }
}

pub const PRESETS: [PresetDescriptor; 32] = [
    PresetDescriptor {
        id: "CLAUDE_OPUS_5_5",
        provider_id: "OPENROUTER",
        model_id: "anthropic/claude-opus-5.5",
        display_name: "Claude Opus 5.5",
        description: "适合复杂任务，默认 Medium effort。",
        family: "Anthropic · OpenRouter",
        chat_selectable: true,
    },
    PresetDescriptor {
        id: "CLAUDE_SONNET_5_5",
        provider_id: "OPENROUTER",
        model_id: "anthropic/claude-sonnet-5.5",
        display_name: "Claude Sonnet 5.5",
        description: "高效处理日常工作，默认 High effort。",
        family: "Anthropic · OpenRouter",
        chat_selectable: true,
    },
    PresetDescriptor {
        id: "CLAUDE_HAIKU_5_5",
        provider_id: "OPENROUTER",
        model_id: "anthropic/claude-haiku-5.5",
        display_name: "Claude Haiku 5.5",
        description: "快速处理高频、轻量任务。",
        family: "Anthropic · OpenRouter",
        chat_selectable: true,
    },
    PresetDescriptor {
        id: "GPT_6_1_SOL",
        provider_id: "OPENROUTER",
        model_id: "openai/gpt-6.1-sol",
        display_name: "GPT-6.1 Sol",
        description: "适合专业复杂任务和深度分析。",
        family: "OpenAI · OpenRouter",
        chat_selectable: true,
    },
    PresetDescriptor {
        id: "GPT_6_1_TERRA",
        provider_id: "OPENROUTER",
        model_id: "openai/gpt-6.1-terra",
        display_name: "GPT-6.1 Terra",
        description: "适合日常工作、图像理解与代码任务。",
        family: "OpenAI · OpenRouter",
        chat_selectable: true,
    },
    PresetDescriptor {
        id: "GPT_6_LUNA",
        provider_id: "OPENROUTER",
        model_id: "openai/gpt-6-luna",
        display_name: "GPT-6 Luna",
        description: "适合高频、轻量任务。",
        family: "OpenAI · OpenRouter",
        chat_selectable: true,
    },

    PresetDescriptor {
        id: "MERCURY_3", provider_id: "OPENROUTER", model_id: "inception/mercury-3",
        display_name: "Mercury 3", description: "适合快速文字问答、代码与联网检索。",
        family: "Inception · OpenRouter", chat_selectable: true,
    },
    PresetDescriptor {
        id: "GEMINI_4_ARGON", provider_id: "OPENROUTER", model_id: "google/gemini-4-argon",
        display_name: "Gemini 4 Argon", description: "适合深度推理、复杂分析与代码任务。",
        family: "Google · OpenRouter", chat_selectable: true,
    },
    PresetDescriptor {
        id: "MUSE_WONDER", provider_id: "OPENROUTER", model_id: "meta/muse-wonder",
        display_name: "Muse Wonder", description: "适合复杂分析与长程任务。",
        family: "Meta · OpenRouter", chat_selectable: true,
    },

    PresetDescriptor {
        id: "CLAUDE_FABLE_5_1",
        provider_id: "OPENROUTER",
        model_id: "anthropic/claude-fable-5.1-20260831",
        display_name: "Claude Fable 5.1",
        description: "适合长程代码、研究与复杂知识工作。",
        family: "Anthropic · OpenRouter",
        chat_selectable: false,
    },
    PresetDescriptor {
        id: "GPT_6_ASTRA",
        provider_id: "OPENROUTER",
        model_id: "openai/gpt-6-astra",
        display_name: "GPT-6 Astra",
        description: "适合高难分析、工程与长程复杂任务。",
        family: "OpenAI · OpenRouter",
        chat_selectable: false,
    },
    PresetDescriptor {
        id: "CLAUDE_FABLE_5",
        provider_id: "OPENROUTER",
        model_id: "anthropic/claude-fable-5",
        display_name: "Claude Fable 5",
        description: "应对最棘手的复杂任务。",
        family: "Anthropic · OpenRouter",
        chat_selectable: false,
    },
    PresetDescriptor {
        id: "CLAUDE_OPUS_5",
        provider_id: "OPENROUTER",
        model_id: "anthropic/claude-opus-5",
        display_name: "Claude Opus 5",
        description: "适合复杂任务与深度推理。",
        family: "Anthropic · OpenRouter",
        chat_selectable: false,
    },
    PresetDescriptor {
        id: "CLAUDE_SONNET_5",
        provider_id: "OPENROUTER",
        model_id: "anthropic/claude-sonnet-5",
        display_name: "Claude Sonnet 5",
        description: "高效处理日常工作。",
        family: "Anthropic · OpenRouter",
        chat_selectable: false,
    },
    PresetDescriptor {
        id: "CLAUDE_HAIKU_4_5",
        provider_id: "OPENROUTER",
        model_id: "anthropic/claude-haiku-4.5",
        display_name: "Claude Haiku 4.5",
        description: "快速获得简洁答案。",
        family: "Anthropic · OpenRouter",
        chat_selectable: false,
    },
    PresetDescriptor {
        id: "GPT_5_6_SOL",
        provider_id: "OPENROUTER",
        model_id: "openai/gpt-5.6-sol",
        display_name: "GPT-5.6 Sol",
        description: "前沿能力，适合专业复杂任务。",
        family: "OpenAI · OpenRouter",
        chat_selectable: false,
    },
    PresetDescriptor {
        id: "GPT_5_6_TERRA",
        provider_id: "OPENROUTER",
        model_id: "openai/gpt-5.6-terra",
        display_name: "GPT-5.6 Terra",
        description: "能力与成本更均衡。",
        family: "OpenAI · OpenRouter",
        chat_selectable: false,
    },
    PresetDescriptor {
        id: "GPT_5_6_LUNA",
        provider_id: "OPENROUTER",
        model_id: "openai/gpt-5.6-luna",
        display_name: "GPT-5.6 Luna",
        description: "适合高频、轻量任务。",
        family: "OpenAI · OpenRouter",
        chat_selectable: false,
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
        chat_selectable: false,
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
    PresetDescriptor { id: "RELAYCORE_OPUS_5_5", provider_id: "RELAYCORE_CLAUDE", model_id: "claude-opus-5.5", display_name: "Opus 5.5 · Kiro正价", description: "平台列出，返回身份以响应为准。", family: "RelayCore", chat_selectable: true },
    PresetDescriptor { id: "RELAYCORE_SONNET_5_5", provider_id: "RELAYCORE_CLAUDE", model_id: "claude-sonnet-5-5", display_name: "Sonnet 5.5 · Kiro正价", description: "平台列出，返回身份以响应为准。", family: "RelayCore", chat_selectable: true },
    PresetDescriptor { id: "RELAYCORE_GPT_6_1_SOL", provider_id: "RELAYCORE_GPT", model_id: "gpt-6.1-sol", display_name: "GPT-6.1 Sol · Pro3", description: "平台列出，返回身份以响应为准。", family: "RelayCore", chat_selectable: true },
    PresetDescriptor { id: "RELAYCORE_GPT_6_ASTRA", provider_id: "RELAYCORE_GPT", model_id: "gpt-6-astra", display_name: "GPT-6 Astra · Pro3", description: "平台列出，返回身份以响应为准。", family: "RelayCore", chat_selectable: true },
    PresetDescriptor { id: "RELAYCORE_GEMINI_3_8_FLASH", provider_id: "RELAYCORE_GEMINI", model_id: "gemini-3.8-flash", display_name: "Gemini 3.8 Flash · Gemini", description: "平台列出，返回身份以响应为准。", family: "RelayCore", chat_selectable: true },
    PresetDescriptor { id: "RELAYCORE_GEMINI_3_1_PRO", provider_id: "RELAYCORE_GEMINI", model_id: "gemini-3.1-pro", display_name: "Gemini 3.1 Pro · Gemini", description: "平台列出，返回身份以响应为准。", family: "RelayCore", chat_selectable: true },
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

/// Known transcription identities stay outside chat even in a custom/legacy catalog.
pub fn is_known_non_chat_model_id(value: &str) -> bool {
    [GLM_OCR, QWEN_ASR].iter().any(|preset| {
        preset.id.eq_ignore_ascii_case(value) || preset.model_id.eq_ignore_ascii_case(value)
    })
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ChatModelExclusion { NonChat, Retired }

impl ChatModelExclusion {
    pub fn message(self) -> &'static str {
        match self {
            Self::NonChat => "OCR／语音转写型号不能用于聊天；请使用对应的独立转写或OCR连接测试入口。",
            Self::Retired => "此型号已退场，仅保留历史识别；请选择当前可用的聊天型号。",
        }
    }
}

/// Readable old identities never grant new selection or execution permission.
/// Compatible published aliases are checked against the active catalog first.
pub fn chat_model_exclusion(value: &str) -> Option<ChatModelExclusion> {
    if is_known_non_chat_model_id(value) { return Some(ChatModelExclusion::NonChat); }
    let retired_aliases = [
        ("x-ai/grok-4.1-fast", "GROK_4_1_FAST"),
        ("x-ai/grok-4.5", "GROK_4_5"),
        ("x-ai/grok-4.6", "GROK_4_6_HIGH"),
        ("logical:deep:kimi-k3", "KIMI_K3"),
        ("logical:deep:claude-fable", "CLAUDE_FABLE_5"),
        ("logical:deep:claude-fable-5.1", "CLAUDE_FABLE_5_1"),
        ("logical:deep:gpt-astra", "GPT_6_ASTRA"),
        ("logical:daily:grok-4.1-fast", "GROK_4_1_FAST"),
        ("logical:daily:grok-4.5", "GROK_4_5"),
        ("logical:deep:grok-4.6-high", "GROK_4_6_HIGH"),
    ];
    let identity = retired_aliases.iter().find_map(|(alias, id)| (*alias == value).then_some(*id)).unwrap_or(value);
    let current = current_preset_id(identity);
    if chat_presets().iter().any(|p| p.chat_selectable && (p.id == current || p.model_id == current)) {
        return None;
    }
    (PRESETS.iter().any(|p| !p.chat_selectable && (p.id == identity || p.model_id == identity))
        || matches!(identity, "GROK_4_1_FAST" | "GROK_4_5" | "GROK_4_6_HIGH"))
        .then_some(ChatModelExclusion::Retired)
}

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

pub fn current_preset_id(value: &str) -> &str {
    match value {
        "CLAUDE_OPUS_5" => "CLAUDE_OPUS_5_5",
        "CLAUDE_SONNET_5" => "CLAUDE_SONNET_5_5",
        "CLAUDE_HAIKU_4_5" => "CLAUDE_HAIKU_5_5",
        "GPT_5_6_SOL" => "GPT_6_1_SOL",
        "GPT_5_6_TERRA" => "GPT_6_LUNA",
        "GPT_5_6_LUNA" => "GPT_6_LUNA",
        "anthropic/claude-opus-5" | "claude-opus-5" => "CLAUDE_OPUS_5_5",
        "anthropic/claude-sonnet-5" | "claude-sonnet-5" => "CLAUDE_SONNET_5_5",
        "anthropic/claude-haiku-4.5" | "claude-haiku-4.5" => "CLAUDE_HAIKU_5_5",
        "openai/gpt-5.6-sol" | "gpt-5.6-sol" => "GPT_6_1_SOL",
        "openai/gpt-5.6-terra" | "gpt-5.6-terra" => "GPT_6_LUNA",
        "openai/gpt-5.6-luna" | "gpt-5.6-luna" => "GPT_6_LUNA",
        _ => value,
    }
}


/// Same-provider published wire alias, used only for identity comparison, never fallback or ledger rewriting.
pub fn canonical_relay_model_id<'a>(provider: &str, id: &'a str) -> &'a str {
    if provider != "RELAYCORE_CLAUDE" { return id; }
    match id { "claude-opus-5-5" => "claude-opus-5.5", "claude-sonnet-5.5" => "claude-sonnet-5-5", _ => id }
}

#[cfg(test)]
#[test]
fn v6_canonical_relay_alias_is_provider_scoped_and_never_changes_other_models() {
    assert_eq!(canonical_relay_model_id("RELAYCORE_CLAUDE", "claude-opus-5-5"), "claude-opus-5.5");
    assert_eq!(canonical_relay_model_id("OPENROUTER", "claude-opus-5-5"), "claude-opus-5-5");
    assert_eq!(canonical_relay_model_id("RELAYCORE_CLAUDE", "claude-sonnet-5-5"), "claude-sonnet-5-5");
    assert_ne!(canonical_relay_model_id("RELAYCORE_CLAUDE", "claude-opus-5.5"), canonical_relay_model_id("RELAYCORE_CLAUDE", "claude-sonnet-5-5"));
}

pub fn preset(provider_id: &str, preset_id: &str) -> Result<PresetDescriptor, String> {
    let preset_id = current_preset_id(preset_id);
    presets_for(provider_id)?
        .into_iter()
        .find(|item| item.id == preset_id)
        .ok_or_else(|| "所选模型不属于当前服务商，未保存设置。".to_owned())
}

pub fn validate_provider(provider_id: &str) -> Result<(), String> {
    provider(provider_id).map(|_| ())
}

/// Read a known historical configuration without making retired models
/// selectable or substituting another provider.
pub fn stored_preset(provider_id: &str, preset_id: &str) -> Result<PresetDescriptor, String> {
    validate_provider(provider_id)?;
    let current = current_preset_id(preset_id);
    chat_presets().into_iter()
        .find(|item| item.provider_id == provider_id && (item.id == current || item.id == preset_id))
        .ok_or_else(|| "已保存的模型身份无效；原设置保留，请明确选择当前型号。".to_owned())
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
pub fn default_reasoning_effort(model_id: &str) -> Option<&'static str> {
    match model_id {
        "anthropic/claude-opus-5.5" => Some("medium"),
        "anthropic/claude-sonnet-5.5" | "openai/gpt-6.1-sol" => Some("high"),
        _ => None,
    }
}

pub fn connection_test_preset(provider_id: &str, preset_id: &str) -> Result<PresetDescriptor, String> {
    if provider_id == "ZHIPU" && preset_id == "GLM_OCR" { return Ok(GLM_OCR); }
    preset(provider_id, preset_id)
}

fn connection_test_request(provider_id: &str, preset_id: &str) -> Result<(String, Value), String> {
    let provider = provider(provider_id)?;
    let preset = connection_test_preset(provider_id, preset_id)?;
    if preset.id == "GLM_OCR" {
        let fixed_png = include_str!("../../../protocol/fixtures/model-connection-probe-ocr-base64.txt").trim();
        return Ok(("https://open.bigmodel.cn/api/paas/v4/layout_parsing".to_owned(), json!({
            "model": "glm-ocr", "file": format!("data:image/png;base64,{fixed_png}")
        })));
    }
    let url = if provider_id == "RELAYCORE_CLAUDE" { format!("{}/messages", provider.endpoint.trim_end_matches('/')) } else { format!("{}/chat/completions", provider.endpoint.trim_end_matches('/')) };
    let mut probe_body = json!({
        "model": preset.model_id,
        "messages": [{ "role": "user", "content": "hi" }],
        "max_tokens": 1,
        "stream": false
    });
    if let Some(effort) = default_reasoning_effort(preset.model_id) {
        probe_body["reasoning"] = json!({"effort": effort});
    }
    if provider_id == "RELAYCORE_CLAUDE" {
        probe_body["output_config"] = json!({"effort":if matches!(preset.model_id,"claude-opus-5-5"|"claude-opus-5.5") {"medium"}else{"high"}});
    }
    Ok((url, probe_body))
}

const MAX_CONNECTION_TEST_RESPONSE_BYTES: usize = 256 * 1024;

fn validate_connection_probe_response(provider_id: &str, preset_id: &str, value: &Value) -> Result<(), String> {
    if value.get("error").is_some_and(|error| !error.is_null()) || value.get("type").and_then(Value::as_str) == Some("error") {
        return Err("连接测试返回服务错误；请检查 API Key、余额和模型权限。".to_owned());
    }
    let valid = if provider_id == "ZHIPU" && preset_id == "GLM_OCR" {
        crate::glm_ocr_response_v1::parts(value).is_some()
    } else if provider_id == "RELAYCORE_CLAUDE" {
        value["type"] == "message" && value["content"].is_array()
            && value.get("role").map_or(true, |role| role == "assistant")
            && value["stop_reason"].as_str().is_some_and(|reason| !reason.is_empty())
    } else {
        value["choices"].as_array().and_then(|choices| choices.first()).is_some_and(|choice| {
            let message = &choice["message"];
            message.is_object() && message.get("role").map_or(true, |role| role == "assistant")
                && choice["finish_reason"].as_str().is_some_and(|reason| !reason.is_empty())
                && (message.get("content").is_some_and(|content| content.is_string() || content.is_null())
                    || message["reasoning_content"].is_string() || message["tool_calls"].is_array() || message["refusal"].is_string())
        })
    };
    if valid { Ok(()) } else { Err("连接测试未返回可核验的模型响应；请检查服务状态和模型权限。".to_owned()) }
}

pub async fn test_saved_connection(
    provider_id: &str,
    preset_id: &str,
    secret: Zeroizing<Vec<u8>>,
    acceptance_endpoint: Option<&str>,
) -> Result<(), String> {
    let preset = connection_test_preset(provider_id, preset_id)?;
    validate_secret(secret.as_slice())?;
    let provider = provider(provider_id)?;
    let (default_url, probe_body) = connection_test_request(provider_id, preset_id)?;
    let authorization = Zeroizing::new(
        String::from_utf8(secret.to_vec())
            .map_err(|_| "本机 API Key 格式无效；请重新保存。".to_owned())?,
    );
    let url = match acceptance_endpoint {
        Some(base) => {
            let suffix = default_url.strip_prefix(provider.endpoint)
                .ok_or_else(|| "连接测试端点身份无效；未发送请求。".to_owned())?;
            format!("{}{}", base.trim_end_matches('/'), suffix)
        },
        None => default_url,
    };
    let mut builder = reqwest::Client::builder()
        .connect_timeout(std::time::Duration::from_secs(10))
        .timeout(std::time::Duration::from_secs(if preset.id == "GLM_OCR" { 60 } else { 25 }))
        .redirect(reqwest::redirect::Policy::none())
        .build()
        .map_err(|_| "无法创建安全连接；未发送测试请求。".to_owned())?
        .post(url)
        .bearer_auth(authorization.as_str());
    if provider_id == "RELAYCORE_CLAUDE" {
        builder = builder.header("x-api-key", authorization.as_str()).header("anthropic-version", "2023-06-01");
    }
    let mut response = builder.json(&probe_body)
        .send()
        .await
        .map_err(|_| "连接测试失败；请检查网络、服务状态和 API Key。".to_owned())?;
    if !response.status().is_success() {
        return Err(format!(
            "连接测试未通过（HTTP {}）；请检查 API Key、余额和模型权限。",
            response.status().as_u16()
        ));
    }
    if response.content_length().is_some_and(|length| length > MAX_CONNECTION_TEST_RESPONSE_BYTES as u64) {
        return Err("连接测试响应异常过大；未确认连接，请检查服务状态后重试。".to_owned());
    }
    let mut bytes = Vec::new();
    while let Some(chunk) = response.chunk().await.map_err(|_| "连接测试响应未完整读取；请检查网络后重试。".to_owned())? {
        if bytes.len().saturating_add(chunk.len()) > MAX_CONNECTION_TEST_RESPONSE_BYTES {
            return Err("连接测试响应异常过大；未确认连接，请检查服务状态后重试。".to_owned());
        }
        bytes.extend_from_slice(&chunk);
    }
    let value: Value = serde_json::from_slice(&bytes).map_err(|_| "连接测试返回的内容不是预期模型响应；请检查服务状态后重试。".to_owned())?;
    validate_connection_probe_response(provider_id, preset_id, &value)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::{
        cell::RefCell,
        collections::BTreeMap,
        io::{Read, Write},
        net::TcpListener,
        sync::mpsc,
        thread,
        time::Duration,
    };

    async fn probe_reply(provider_id: &str, preset_id: &str, body: Vec<u8>, chunked: bool) -> Result<(), String> {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let endpoint = format!("http://{}", listener.local_addr().unwrap());
        let server = thread::spawn(move || {
            let (mut socket, _) = listener.accept().unwrap();
            socket.set_read_timeout(Some(Duration::from_secs(5))).unwrap();
            socket.set_write_timeout(Some(Duration::from_secs(5))).unwrap();
            let mut request = Vec::new();
            loop {
                let mut chunk = [0u8; 4096];
                let count = socket.read(&mut chunk).unwrap();
                if count == 0 { break; }
                request.extend_from_slice(&chunk[..count]);
                assert!(request.len() < 65536);
                if let Some(end) = request.windows(4).position(|part| part == b"\r\n\r\n") {
                    let headers = String::from_utf8_lossy(&request[..end]);
                    let length = headers.lines().find_map(|line| line.to_ascii_lowercase().strip_prefix("content-length:").map(str::trim).and_then(|value| value.parse::<usize>().ok())).unwrap_or(0);
                    if request.len() >= end + 4 + length { break; }
                }
            }
            if chunked {
                socket.write_all(b"HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n").unwrap();
                let _ = socket.write_all(format!("{:x}\r\n", body.len()).as_bytes());
                let _ = socket.write_all(&body);
                let _ = socket.write_all(b"\r\n0\r\n\r\n");
            } else {
                socket.write_all(format!("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n", body.len()).as_bytes()).unwrap();
                let _ = socket.write_all(&body);
            }
        });
        let outcome = test_saved_connection(provider_id, preset_id, Zeroizing::new(b"synthetic-response-probe-key".to_vec()), Some(&endpoint)).await;
        server.join().unwrap();
        outcome
    }

    #[tokio::test]
    async fn connection_probe_rejects_http_success_without_a_protocol_result() {
        for (provider, preset, body) in [
            ("QWEN", "QWEN_3_7_PLUS", "<html>sign in</html>"),
            ("QWEN", "QWEN_3_7_PLUS", r#"{"error":{"message":"SYNTHETIC_RESPONSE_SECRET_MUST_NOT_LEAK"}}"#),
            ("QWEN", "QWEN_3_7_PLUS", "{}"),
            ("QWEN", "QWEN_3_7_PLUS", r#"{"choices":["#),
            ("RELAYCORE_CLAUDE", "RELAYCORE_SONNET_5_5", r#"{"type":"error","error":{"message":"SYNTHETIC_RESPONSE_SECRET_MUST_NOT_LEAK"}}"#),
            ("ZHIPU", "GLM_OCR", r#"{"error":"SYNTHETIC_RESPONSE_SECRET_MUST_NOT_LEAK","md_results":"partial"}"#),
        ] {
            let error = probe_reply(provider, preset, body.as_bytes().to_vec(), false).await.expect_err("HTTP 200 alone must not qualify a connection probe");
            assert!(!error.contains("SYNTHETIC_RESPONSE_SECRET_MUST_NOT_LEAK"));
        }
    }

    #[tokio::test]
    async fn connection_probe_accepts_protocol_results_with_no_visible_text() {
        for (provider, preset, body) in [
            ("QWEN", "QWEN_3_7_PLUS", r#"{"choices":[{"message":{"role":"assistant","content":""},"finish_reason":"length"}],"error":null}"#),
            ("RELAYCORE_CLAUDE", "RELAYCORE_SONNET_5_5", r#"{"type":"message","role":"assistant","content":[],"stop_reason":"max_tokens"}"#),
            ("ZHIPU", "GLM_OCR", r#"{"md_results":""}"#),
            ("ZHIPU", "GLM_OCR", r#"{"md_results":[]}"#),
            ("ZHIPU", "GLM_OCR", r#"{"md_results":{"markdown":""}}"#),
        ] {
            probe_reply(provider, preset, body.as_bytes().to_vec(), false).await.unwrap();
        }
    }

    #[tokio::test]
    async fn connection_probe_bounds_sized_and_chunked_http_success_bodies() {
        for chunked in [false, true] {
            let error = probe_reply("QWEN", "QWEN_3_7_PLUS", vec![b'x'; 262145], chunked).await.expect_err("a fixed probe cannot accept an unbounded response");
            assert!(error.contains("过大"), "{error}");
        }
    }

    #[tokio::test]
    async fn saved_connection_probe_sends_the_secret_only_as_bearer_to_acceptance_loopback() {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let address = listener.local_addr().unwrap();
        let endpoint = format!("http://{address}");
        let (sender, receiver) = mpsc::channel();
        thread::spawn(move || {
            let (mut socket, _) = listener.accept().unwrap();
            socket.set_read_timeout(Some(Duration::from_secs(5))).unwrap();
            let mut request = Vec::new();
            loop {
                let mut chunk = [0u8; 4096];
                let read = socket.read(&mut chunk).unwrap();
                if read == 0 {
                    break;
                }
                request.extend_from_slice(&chunk[..read]);
                let Some(header_end) = request.windows(4).position(|part| part == b"\r\n\r\n") else {
                    continue;
                };
                let headers = String::from_utf8_lossy(&request[..header_end]);
                let content_length = headers
                    .lines()
                    .find_map(|line| line.to_ascii_lowercase().strip_prefix("content-length:").map(str::trim).and_then(|value| value.parse::<usize>().ok()))
                    .unwrap_or(0);
                if request.len() >= header_end + 4 + content_length {
                    break;
                }
            }
            let text = String::from_utf8_lossy(&request);
            let lower = text.to_ascii_lowercase();
            let valid = text.starts_with("POST /chat/completions HTTP/1.1")
                && lower.contains("authorization: bearer synthetic-connection-key")
                && text.contains("\"model\":\"qwen3.7-plus\"")
                && text.contains("\"max_tokens\":1")
                && text.contains("\"content\":\"hi\"")
                && !text.split("\r\n\r\n").nth(1).unwrap_or_default().contains("synthetic-connection-key");
            sender.send(valid).unwrap();
            let body = br#"{"choices":[{"message":{"role":"assistant","content":""},"finish_reason":"length"}]}"#;
            socket.write_all(format!("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n", body.len()).as_bytes()).unwrap();
            socket.write_all(body).unwrap();
        });

        test_saved_connection(
            "QWEN",
            "QWEN_3_7_PLUS",
            Zeroizing::new(b"synthetic-connection-key".to_vec()),
            Some(&endpoint),
        )
        .await
        .unwrap();
        assert!(receiver.recv_timeout(Duration::from_secs(5)).unwrap());
    }

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
        let updated = b"ponmlkjihgfedcba";
        store.save_user_provided_secret("ZHIPU", updated).unwrap();
        assert_eq!(
            store
                .with_secret("ZHIPU", |value| Ok(value.to_vec()))
                .unwrap(),
            updated
        );
        let document = fs::read_to_string(store.document_path()).unwrap();
        assert!(!document.contains("abcdefghijklmnop"));
        assert!(!document.contains("ponmlkjihgfedcba"));
        store.delete_user_secret("ZHIPU").unwrap();
        assert_eq!(
            store.presence("ZHIPU"),
            CredentialPresence::MissingOrUnavailable
        );
        store.delete_all().unwrap();
        assert!(!store.root.exists());
    }

    #[test]
    fn pending_model_setting_blocks_secret_use_and_recovers_both_commit_sides() {
        let root = tempfile::tempdir().unwrap();
        let store = AppPrivateProviderCredentialStore::at(root.path());
        store
            .save_user_provided_secret("QWEN", b"previous-provider-secret")
            .unwrap();
        store
            .save_user_provided_secret("DEEPSEEK", b"unrelated-provider-secret")
            .unwrap();
        let before = ModelSettingState {
            provider_id: "QWEN".into(),
            enabled: false,
            preset_id: "QWEN_3_7_PLUS".into(),
            revision: 0,
        };
        let after = ModelSettingState {
            provider_id: "QWEN".into(),
            enabled: true,
            preset_id: "QWEN_3_8_MAX".into(),
            revision: 1,
        };
        store
            .begin_model_setting_secret_update(
                before.clone(),
                after.clone(),
                b"replacement-provider-secret",
            )
            .unwrap();
        let pending_bytes = fs::read(store.pending_model_setting_path()).unwrap();
        assert!(!String::from_utf8_lossy(&pending_bytes).contains("previous-provider-secret"));
        assert!(!String::from_utf8_lossy(&pending_bytes).contains("replacement-provider-secret"));
        assert_eq!(store.presence("QWEN"), CredentialPresence::Unavailable);
        assert!(store.with_secret("QWEN", |secret| Ok(secret.to_vec())).is_err());
        assert_eq!(
            store
                .with_secret("DEEPSEEK", |secret| Ok(secret.to_vec()))
                .unwrap(),
            b"unrelated-provider-secret"
        );
        assert!(store
            .save_user_provided_secret("QWEN", b"another-provider-secret")
            .is_err());

        let reopened = AppPrivateProviderCredentialStore::at(root.path());
        assert_eq!(
            reopened.settle_model_setting_secret_update(&before).unwrap(),
            Some(ModelSettingResolution::RestoredPrevious)
        );
        assert_eq!(reopened.presence("QWEN"), CredentialPresence::Stored);
        assert_eq!(
            reopened
                .with_secret("QWEN", |secret| Ok(secret.to_vec()))
                .unwrap(),
            b"previous-provider-secret"
        );
        assert!(!reopened.pending_model_setting_path().exists());
        assert_eq!(reopened.settle_model_setting_secret_update(&before).unwrap(), None);

        reopened
            .begin_model_setting_secret_update(
                before.clone(),
                after.clone(),
                b"replacement-provider-secret",
            )
            .unwrap();
        assert!(reopened
            .settle_model_setting_secret_update(&ModelSettingState {
                revision: 9,
                ..after.clone()
            })
            .is_err());
        assert_eq!(reopened.presence("QWEN"), CredentialPresence::Unavailable);
        assert_eq!(
            reopened.settle_model_setting_secret_update(&after).unwrap(),
            Some(ModelSettingResolution::CommittedNew)
        );
        assert_eq!(
            reopened
                .with_secret("QWEN", |secret| Ok(secret.to_vec()))
                .unwrap(),
            b"replacement-provider-secret"
        );
    }

    #[test]
    fn pending_model_setting_replays_new_ciphertext_if_document_write_was_lost() {
        let root = tempfile::tempdir().unwrap();
        let store = AppPrivateProviderCredentialStore::at(root.path());
        store
            .save_user_provided_secret("QWEN", b"previous-provider-secret")
            .unwrap();
        let old_document = fs::read(store.document_path()).unwrap();
        let before = ModelSettingState {
            provider_id: "QWEN".into(),
            enabled: false,
            preset_id: "QWEN_3_7_PLUS".into(),
            revision: 0,
        };
        let after = ModelSettingState {
            provider_id: "QWEN".into(),
            enabled: true,
            preset_id: "QWEN_3_8_MAX".into(),
            revision: 1,
        };
        store
            .begin_model_setting_secret_update(
                before,
                after.clone(),
                b"replacement-provider-secret",
            )
            .unwrap();
        #[cfg(windows)]
        crate::desktop_windows_private_store::atomic_write_private(
            &store.document_path(),
            &old_document,
        )
        .unwrap();
        #[cfg(not(windows))]
        fs::write(store.document_path(), &old_document).unwrap();
        let reopened = AppPrivateProviderCredentialStore::at(root.path());
        assert_eq!(
            reopened.settle_model_setting_secret_update(&after).unwrap(),
            Some(ModelSettingResolution::CommittedNew)
        );
        assert_eq!(
            reopened
                .with_secret("QWEN", |secret| Ok(secret.to_vec()))
                .unwrap(),
            b"replacement-provider-secret"
        );
    }

    #[test]
    fn pending_model_setting_rollback_removes_a_new_provider_key() {
        let root = tempfile::tempdir().unwrap();
        let store = AppPrivateProviderCredentialStore::at(root.path());
        let before = ModelSettingState {
            provider_id: "QWEN".into(),
            enabled: false,
            preset_id: "QWEN_3_7_PLUS".into(),
            revision: 0,
        };
        let after = ModelSettingState {
            enabled: true,
            revision: 1,
            ..before.clone()
        };
        store
            .begin_model_setting_secret_update(
                before.clone(),
                after,
                b"temporary-provider-secret",
            )
            .unwrap();
        let reopened = AppPrivateProviderCredentialStore::at(root.path());
        assert_eq!(
            reopened.settle_model_setting_secret_update(&before).unwrap(),
            Some(ModelSettingResolution::RestoredPrevious)
        );
        assert_eq!(
            reopened.presence("QWEN"),
            CredentialPresence::MissingOrUnavailable
        );
        assert!(!reopened.pending_model_setting_path().exists());
    }

    #[test]
    fn pending_model_setting_keeps_recovery_record_when_previous_key_is_missing() {
        let root = tempfile::tempdir().unwrap();
        let store = AppPrivateProviderCredentialStore::at(root.path());
        store
            .save_user_provided_secret("QWEN", b"previous-provider-secret")
            .unwrap();
        let before = ModelSettingState {
            provider_id: "QWEN".into(),
            enabled: false,
            preset_id: "QWEN_3_7_PLUS".into(),
            revision: 0,
        };
        let after = ModelSettingState {
            enabled: true,
            revision: 1,
            ..before.clone()
        };
        store
            .begin_model_setting_secret_update(
                before.clone(),
                after,
                b"replacement-provider-secret",
            )
            .unwrap();
        let saved_key = fs::read(store.key_path()).unwrap();
        fs::remove_file(store.key_path()).unwrap();
        assert!(store.settle_model_setting_secret_update(&before).is_err());
        assert!(store.pending_model_setting_path().exists());
        assert_eq!(store.presence("QWEN"), CredentialPresence::Unavailable);
        #[cfg(windows)]
        crate::desktop_windows_private_store::atomic_write_private(&store.key_path(), &saved_key)
            .unwrap();
        #[cfg(not(windows))]
        fs::write(store.key_path(), &saved_key).unwrap();
        assert_eq!(
            store.settle_model_setting_secret_update(&before).unwrap(),
            Some(ModelSettingResolution::RestoredPrevious)
        );
        assert_eq!(
            store.with_secret("QWEN", |secret| Ok(secret.to_vec())).unwrap(),
            b"previous-provider-secret"
        );
    }

    #[test]
    fn pending_model_setting_keeps_recovery_record_when_previous_ciphertext_is_corrupt() {
        let root = tempfile::tempdir().unwrap();
        let store = AppPrivateProviderCredentialStore::at(root.path());
        store
            .save_user_provided_secret("QWEN", b"previous-provider-secret")
            .unwrap();
        let before = ModelSettingState {
            provider_id: "QWEN".into(),
            enabled: false,
            preset_id: "QWEN_3_7_PLUS".into(),
            revision: 0,
        };
        let after = ModelSettingState {
            enabled: true,
            revision: 1,
            ..before.clone()
        };
        store
            .begin_model_setting_secret_update(
                before.clone(),
                after,
                b"replacement-provider-secret",
            )
            .unwrap();
        let pending_path = store.pending_model_setting_path();
        let original = fs::read(&pending_path).unwrap();
        let mut pending: PendingModelSetting = serde_json::from_slice(&original).unwrap();
        pending.previous.as_mut().unwrap().ciphertext = BASE64.encode(b"corrupt");
        let corrupt = serde_json::to_vec(&pending).unwrap();
        #[cfg(windows)]
        crate::desktop_windows_private_store::atomic_write_private(&pending_path, &corrupt)
            .unwrap();
        #[cfg(not(windows))]
        fs::write(&pending_path, &corrupt).unwrap();
        assert!(store.settle_model_setting_secret_update(&before).is_err());
        assert!(pending_path.exists());
        assert_eq!(store.presence("QWEN"), CredentialPresence::Unavailable);
        #[cfg(windows)]
        crate::desktop_windows_private_store::atomic_write_private(&pending_path, &original)
            .unwrap();
        #[cfg(not(windows))]
        fs::write(&pending_path, &original).unwrap();
        assert_eq!(
            store.settle_model_setting_secret_update(&before).unwrap(),
            Some(ModelSettingResolution::RestoredPrevious)
        );
    }

    #[test]
    fn pending_model_setting_rejects_valid_json_with_removed_previous_record() {
        let root = tempfile::tempdir().unwrap();
        let store = AppPrivateProviderCredentialStore::at(root.path());
        store
            .save_user_provided_secret("QWEN", b"previous-provider-secret")
            .unwrap();
        let before = ModelSettingState {
            provider_id: "QWEN".into(),
            enabled: true,
            preset_id: "QWEN_3_7_PLUS".into(),
            revision: 1,
        };
        let after = ModelSettingState {
            preset_id: "QWEN_3_8_MAX".into(),
            revision: 2,
            ..before.clone()
        };
        store
            .begin_model_setting_secret_update(
                before.clone(),
                after,
                b"replacement-provider-secret",
            )
            .unwrap();
        let pending_path = store.pending_model_setting_path();
        let original = fs::read(&pending_path).unwrap();
        let mut tampered: serde_json::Value = serde_json::from_slice(&original).unwrap();
        tampered["previous"] = serde_json::Value::Null;
        let tampered = serde_json::to_vec(&tampered).unwrap();
        #[cfg(windows)]
        crate::desktop_windows_private_store::atomic_write_private(&pending_path, &tampered)
            .unwrap();
        #[cfg(not(windows))]
        fs::write(&pending_path, tampered).unwrap();

        assert!(store.settle_model_setting_secret_update(&before).is_err());
        assert!(pending_path.exists());
        assert_eq!(store.presence("QWEN"), CredentialPresence::Unavailable);

        let mut downgraded: serde_json::Value = serde_json::from_slice(&original).unwrap();
        downgraded["version"] = serde_json::json!(1);
        downgraded.as_object_mut().unwrap().remove("integrity");
        let downgraded = serde_json::to_vec(&downgraded).unwrap();
        #[cfg(windows)]
        crate::desktop_windows_private_store::atomic_write_private(&pending_path, &downgraded)
            .unwrap();
        #[cfg(not(windows))]
        fs::write(&pending_path, downgraded).unwrap();
        assert!(store.settle_model_setting_secret_update(&before).is_err());
        assert!(pending_path.exists());

        let mut changed_after: serde_json::Value = serde_json::from_slice(&original).unwrap();
        changed_after["after"]["presetId"] = serde_json::json!("QWEN_3_7_PLUS");
        let changed_after = serde_json::to_vec(&changed_after).unwrap();
        #[cfg(windows)]
        crate::desktop_windows_private_store::atomic_write_private(&pending_path, &changed_after)
            .unwrap();
        #[cfg(not(windows))]
        fs::write(&pending_path, changed_after).unwrap();
        assert!(store.settle_model_setting_secret_update(&before).is_err());
        assert!(pending_path.exists());

        #[cfg(windows)]
        crate::desktop_windows_private_store::atomic_write_private(&pending_path, &original)
            .unwrap();
        #[cfg(not(windows))]
        fs::write(&pending_path, original).unwrap();
        assert_eq!(
            store.settle_model_setting_secret_update(&before).unwrap(),
            Some(ModelSettingResolution::RestoredPrevious)
        );
        assert_eq!(
            store.with_secret("QWEN", |secret| Ok(secret.to_vec())).unwrap(),
            b"previous-provider-secret"
        );
    }

    #[cfg(windows)]
    #[test]
    fn provider_credential_busy_commit_preserves_the_previous_secret() {
        use std::os::windows::fs::OpenOptionsExt;
        use windows::Win32::Storage::FileSystem::FILE_SHARE_READ;

        let root = tempfile::tempdir().unwrap();
        let store = AppPrivateProviderCredentialStore::at(root.path());
        let old = b"provider-old-secret";
        store.save_user_provided_secret("QWEN", old).unwrap();
        let busy = fs::OpenOptions::new()
            .read(true)
            .share_mode(FILE_SHARE_READ.0)
            .open(store.document_path())
            .unwrap();
        assert!(store
            .save_user_provided_secret("QWEN", b"provider-new-secret")
            .is_err());
        drop(busy);
        assert_eq!(
            store
                .with_secret("QWEN", |value| Ok(value.to_vec()))
                .unwrap(),
            old
        );
    }

    #[cfg(windows)]
    #[test]
    fn pending_model_setting_keeps_rollback_record_when_credential_file_is_busy() {
        use std::os::windows::fs::OpenOptionsExt;
        use windows::Win32::Storage::FileSystem::FILE_SHARE_READ;

        let root = tempfile::tempdir().unwrap();
        let store = AppPrivateProviderCredentialStore::at(root.path());
        store
            .save_user_provided_secret("QWEN", b"previous-provider-secret")
            .unwrap();
        let before = ModelSettingState {
            provider_id: "QWEN".into(),
            enabled: false,
            preset_id: "QWEN_3_7_PLUS".into(),
            revision: 0,
        };
        let after = ModelSettingState {
            enabled: true,
            preset_id: "QWEN_3_8_MAX".into(),
            revision: 1,
            ..before.clone()
        };
        store
            .begin_model_setting_secret_update(
                before.clone(),
                after,
                b"replacement-provider-secret",
            )
            .unwrap();
        let pending_path = store.pending_model_setting_path();
        let pending_bytes = fs::read(&pending_path).unwrap();
        let replacement_document = fs::read(store.document_path()).unwrap();
        let busy = fs::OpenOptions::new()
            .read(true)
            .share_mode(FILE_SHARE_READ.0)
            .open(store.document_path())
            .unwrap();

        assert!(store.settle_model_setting_secret_update(&before).is_err());
        assert_eq!(fs::read(&pending_path).unwrap(), pending_bytes);
        assert_eq!(fs::read(store.document_path()).unwrap(), replacement_document);
        assert_eq!(store.presence("QWEN"), CredentialPresence::Unavailable);
        assert!(store.with_secret("QWEN", |value| Ok(value.to_vec())).is_err());

        drop(busy);
        let reopened = AppPrivateProviderCredentialStore::at(root.path());
        assert_eq!(
            reopened.settle_model_setting_secret_update(&before).unwrap(),
            Some(ModelSettingResolution::RestoredPrevious)
        );
        assert!(!pending_path.exists());
        assert_eq!(
            reopened
                .with_secret("QWEN", |value| Ok(value.to_vec()))
                .unwrap(),
            b"previous-provider-secret"
        );
    }

    #[cfg(windows)]
    #[test]
    fn pending_model_setting_retries_cleanup_when_pending_file_is_busy() {
        use std::os::windows::fs::OpenOptionsExt;
        use windows::Win32::Storage::FileSystem::FILE_SHARE_READ;

        let root = tempfile::tempdir().unwrap();
        let store = AppPrivateProviderCredentialStore::at(root.path());
        store
            .save_user_provided_secret("QWEN", b"previous-provider-secret")
            .unwrap();
        let before = ModelSettingState {
            provider_id: "QWEN".into(),
            enabled: false,
            preset_id: "QWEN_3_7_PLUS".into(),
            revision: 0,
        };
        let after = ModelSettingState {
            enabled: true,
            preset_id: "QWEN_3_8_MAX".into(),
            revision: 1,
            ..before.clone()
        };
        store
            .begin_model_setting_secret_update(
                before,
                after.clone(),
                b"replacement-provider-secret",
            )
            .unwrap();
        let pending_path = store.pending_model_setting_path();
        let pending_bytes = fs::read(&pending_path).unwrap();
        let replacement_document = fs::read(store.document_path()).unwrap();
        let busy = fs::OpenOptions::new()
            .read(true)
            .share_mode(FILE_SHARE_READ.0)
            .open(&pending_path)
            .unwrap();

        assert!(store.settle_model_setting_secret_update(&after).is_err());
        assert_eq!(fs::read(&pending_path).unwrap(), pending_bytes);
        assert_eq!(fs::read(store.document_path()).unwrap(), replacement_document);
        assert_eq!(store.presence("QWEN"), CredentialPresence::Unavailable);

        drop(busy);
        let reopened = AppPrivateProviderCredentialStore::at(root.path());
        assert_eq!(
            reopened.settle_model_setting_secret_update(&after).unwrap(),
            Some(ModelSettingResolution::CommittedNew)
        );
        assert!(!pending_path.exists());
        assert_eq!(
            reopened
                .with_secret("QWEN", |value| Ok(value.to_vec()))
                .unwrap(),
            b"replacement-provider-secret"
        );
    }

    #[test]
    fn provider_order_endpoints_and_defaults_match_current_android_contract() {
        assert_eq!(
            PROVIDERS.map(|item| item.id),
            ["OPENROUTER", "DEEPSEEK", "ZHIPU", "QWEN", "RELAYCORE_CLAUDE", "RELAYCORE_GPT", "RELAYCORE_GEMINI"]
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

#[cfg(test)] mod terra_mercury_update_tests {
 use super::*;
 #[test] fn new_models_keep_exact_identity_and_original_defaults() {
  assert_eq!(preset("OPENROUTER","GPT_6_1_TERRA").unwrap().model_id,"openai/gpt-6.1-terra");
  assert_eq!(preset("OPENROUTER","MERCURY_3").unwrap().model_id,"inception/mercury-3");
  assert!(preset("QWEN","MERCURY_3").is_err());
  assert_eq!(provider("OPENROUTER").unwrap().default_preset_id,"GPT_6_LUNA");
 }
}
