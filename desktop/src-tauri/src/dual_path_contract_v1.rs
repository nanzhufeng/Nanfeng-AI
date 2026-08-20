//! P10-A Desktop status and guard seam. This module intentionally has no Keychain implementation,
//! network client, SQLite persistence, or Tauri registration for test fakes.

use serde::Serialize;
use std::collections::{BTreeMap, BTreeSet};

#[derive(Debug, Clone, Copy, Serialize, PartialEq, Eq)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum CredentialPresence {
    Present,
    Missing,
}
#[derive(Debug, Clone, Copy, Serialize, PartialEq, Eq)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum ExecutionPath {
    LocalOffline,
    OnlineProvider,
}
#[derive(Debug, Clone, Copy, Serialize, PartialEq, Eq)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum DataPath {
    LocalOnly,
    EncryptedSync,
}
#[derive(Debug, Clone, Serialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct ConnectionCapability {
    pub connection: &'static str,
    pub provider_configuration: &'static str,
    pub credential_presence: CredentialPresence,
    pub catalog_freshness: &'static str,
    pub egress_consent: &'static str,
    pub sync_capability: &'static str,
    pub degraded_reasons: Vec<&'static str>,
}

/// Release implementations may ask the OS credential store only for presence. They must never
/// expose the key through SQLite, frontend state, diagnostics, or logs.
pub trait OsCredentialStore {
    fn presence(&self, service: &str) -> CredentialPresence;
}
pub struct NoCredentialStore;
impl OsCredentialStore for NoCredentialStore {
    fn presence(&self, _: &str) -> CredentialPresence {
        CredentialPresence::Missing
    }
}

pub fn current_status(store: &dyn OsCredentialStore) -> ConnectionCapability {
    let presence = store.presence("openrouter");
    let mut reasons = vec![
        "CATALOG_UNAVAILABLE",
        "NETWORK_UNVERIFIED",
        "EGRESS_CONSENT_REQUIRED",
        "SYNC_NOT_CONFIGURED",
    ];
    if presence == CredentialPresence::Missing {
        reasons.insert(0, "NO_CREDENTIAL");
    }
    ConnectionCapability {
        connection: "ONLINE_CONFIGURATION_REQUIRED",
        provider_configuration: "NOT_CONFIGURED",
        credential_presence: presence,
        catalog_freshness: "NOT_AVAILABLE",
        egress_consent: "REQUIRED_PER_INTENT",
        sync_capability: "ENCRYPTED_SYNC_NOT_CONFIGURED",
        degraded_reasons: reasons,
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum GuardResult {
    LocalReady,
    OnlineReady,
    OnlineBlocked(BTreeSet<&'static str>),
    Cancelled,
    Replayed,
}
pub struct LoopbackGuard {
    status: ConnectionCapability,
    seen: BTreeMap<String, GuardResult>,
}
impl LoopbackGuard {
    pub fn new(status: ConnectionCapability) -> Self {
        Self {
            status,
            seen: BTreeMap::new(),
        }
    }
    pub fn select_local(&self) -> GuardResult {
        GuardResult::LocalReady
    }
    pub fn select_online(
        &mut self,
        id: &str,
        data: DataPath,
        consented: bool,
        cost_known: bool,
        model_available: bool,
        cancelled: bool,
    ) -> GuardResult {
        if self.seen.contains_key(id) {
            return GuardResult::Replayed;
        }
        let result = if cancelled {
            GuardResult::Cancelled
        } else {
            let mut reasons = self
                .status
                .degraded_reasons
                .iter()
                .copied()
                .collect::<BTreeSet<_>>();
            if !consented {
                reasons.insert("EGRESS_CONSENT_REQUIRED");
            }
            if !cost_known {
                reasons.insert("UNKNOWN_COST");
            }
            if !model_available {
                reasons.insert("MODEL_UNAVAILABLE");
            }
            if data == DataPath::EncryptedSync
                && self.status.sync_capability != "ENCRYPTED_SYNC_READY"
            {
                reasons.insert("SYNC_NOT_CONFIGURED");
            }
            if reasons.is_empty() {
                GuardResult::OnlineReady
            } else {
                GuardResult::OnlineBlocked(reasons)
            }
        };
        self.seen.insert(id.to_owned(), result.clone());
        result
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    struct MemoryCredentialStore(CredentialPresence);
    impl OsCredentialStore for MemoryCredentialStore {
        fn presence(&self, _: &str) -> CredentialPresence {
            self.0
        }
    }
    #[test]
    fn local_online_cancel_retry_cost_and_rebuild_are_not_conflated() {
        let status = current_status(&MemoryCredentialStore(CredentialPresence::Present));
        let mut guard = LoopbackGuard::new(status.clone());
        assert_eq!(guard.select_local(), GuardResult::LocalReady);
        assert!(
            matches!(guard.select_online("online", DataPath::LocalOnly, true, false, true, false), GuardResult::OnlineBlocked(ref reasons) if reasons.contains("UNKNOWN_COST"))
        );
        assert_eq!(
            guard.select_online("online", DataPath::LocalOnly, true, false, true, false),
            GuardResult::Replayed
        );
        assert_eq!(
            guard.select_online("cancel", DataPath::LocalOnly, true, true, true, true),
            GuardResult::Cancelled
        );
        assert!(
            matches!(LoopbackGuard::new(status).select_online("rebuilt", DataPath::EncryptedSync, true, true, true, false), GuardResult::OnlineBlocked(ref reasons) if reasons.contains("SYNC_NOT_CONFIGURED"))
        );
    }
}
