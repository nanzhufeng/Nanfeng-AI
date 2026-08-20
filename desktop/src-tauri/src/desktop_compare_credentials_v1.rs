//! Desktop Compare credential boundary. This module is intentionally not registered with Tauri.
//! It uses macOS Security.framework directly when a future user-initiated Settings action needs
//! it; this phase never invokes the implementation against a real Keychain.

use zeroize::{Zeroize, Zeroizing};

pub const OPENROUTER_COMPARE_SERVICE: &str = "com.nanzhufeng.ai.desktop.compare.openrouter.v1";
pub const OPENROUTER_COMPARE_ACCOUNT: &str = "api-key";

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Presence {
    Present,
    MissingOrUnavailable,
}

pub trait CompareCredentialStore {
    fn presence(&self) -> Presence;
    fn save_user_provided_secret(&self, secret: &[u8]) -> Result<(), String>;
    fn with_secret<T>(
        &self,
        operation: impl FnOnce(&[u8]) -> Result<T, String>,
    ) -> Result<T, String>;
}

/// App-owned service/account only. It never enumerates, resets, deletes, logs, or exposes a key.
pub struct MacSecurityFrameworkCompareCredentialStore;

impl CompareCredentialStore for MacSecurityFrameworkCompareCredentialStore {
    fn presence(&self) -> Presence {
        match self.read_secret() {
            Ok(mut secret) => {
                secret.zeroize();
                Presence::Present
            }
            Err(_) => Presence::MissingOrUnavailable,
        }
    }

    fn save_user_provided_secret(&self, secret: &[u8]) -> Result<(), String> {
        if !valid_secret(secret) {
            return Err(rejected());
        }
        #[cfg(target_os = "macos")]
        {
            security_framework::passwords::set_generic_password(
                OPENROUTER_COMPARE_SERVICE,
                OPENROUTER_COMPARE_ACCOUNT,
                secret,
            )
            .map_err(|_| rejected())
        }
        #[cfg(not(target_os = "macos"))]
        {
            let _ = secret;
            Err(rejected())
        }
    }

    fn with_secret<T>(
        &self,
        operation: impl FnOnce(&[u8]) -> Result<T, String>,
    ) -> Result<T, String> {
        // Drop also runs during unwinding, so a future transport callback cannot
        // bypass temporary-secret cleanup by panicking.
        let secret = Zeroizing::new(self.read_secret()?);
        operation(secret.as_slice())
    }
}

impl MacSecurityFrameworkCompareCredentialStore {
    fn read_secret(&self) -> Result<Vec<u8>, String> {
        #[cfg(target_os = "macos")]
        {
            security_framework::passwords::get_generic_password(
                OPENROUTER_COMPARE_SERVICE,
                OPENROUTER_COMPARE_ACCOUNT,
            )
            .map_err(|_| rejected())
        }
        #[cfg(not(target_os = "macos"))]
        {
            Err(rejected())
        }
    }
}

fn valid_secret(secret: &[u8]) -> bool {
    (16..=512).contains(&secret.len()) && secret.iter().all(|value| value.is_ascii_graphic())
}

fn rejected() -> String {
    "Desktop Compare credential unavailable".to_owned()
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::cell::RefCell;

    struct MemoryStore(RefCell<Option<Vec<u8>>>);
    impl CompareCredentialStore for MemoryStore {
        fn presence(&self) -> Presence {
            if self.0.borrow().is_some() {
                Presence::Present
            } else {
                Presence::MissingOrUnavailable
            }
        }
        fn save_user_provided_secret(&self, secret: &[u8]) -> Result<(), String> {
            if !valid_secret(secret) {
                return Err(rejected());
            }
            *self.0.borrow_mut() = Some(secret.to_vec());
            Ok(())
        }
        fn with_secret<T>(
            &self,
            operation: impl FnOnce(&[u8]) -> Result<T, String>,
        ) -> Result<T, String> {
            let secret = Zeroizing::new(self.0.borrow().clone().ok_or_else(rejected)?);
            operation(secret.as_slice())
        }
    }

    #[test]
    fn memory_fake_reports_only_safe_presence_and_zeroizes_its_temporary_copy() {
        let store = MemoryStore(RefCell::new(None));
        assert_eq!(store.presence(), Presence::MissingOrUnavailable);
        store
            .save_user_provided_secret(b"abcdefghijklmnop")
            .unwrap();
        assert_eq!(store.presence(), Presence::Present);
        assert_eq!(store.with_secret(|secret| Ok(secret.len())).unwrap(), 16);
        assert_eq!(store.presence(), Presence::Present);
    }

    #[test]
    fn credential_validation_rejects_blank_or_non_printable_input() {
        assert!(!valid_secret(b"too-short"));
        assert!(!valid_secret(&[b'x'; 513]));
        assert!(!valid_secret(b"abcdefghijklmn\0op"));
    }

    #[test]
    fn source_uses_only_the_fixed_app_owned_service_and_account() {
        assert_eq!(
            OPENROUTER_COMPARE_SERVICE,
            "com.nanzhufeng.ai.desktop.compare.openrouter.v1"
        );
        assert_eq!(OPENROUTER_COMPARE_ACCOUNT, "api-key");
    }
}
