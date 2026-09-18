//! P7-B local key/state foundation. It deliberately has no Tauri command, account UI, HTTP or cloud payload.
use aes_gcm::aead::{rand_core::RngCore, OsRng};
use rusqlite::{params, Connection};
use sha2::{Digest, Sha256};
use zeroize::Zeroize;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum State {
    SignedOut,
    NeedsRecoveryConfirmation,
    DirectionRequired,
    Ready,
    Syncing,
    Conflict,
    Failed,
    SignedOutKeepLocal,
}
impl State {
    fn as_str(self) -> &'static str {
        match self {
            Self::SignedOut => "SIGNED_OUT",
            Self::NeedsRecoveryConfirmation => "AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION",
            Self::DirectionRequired => "DIRECTION_REQUIRED",
            Self::Ready => "READY",
            Self::Syncing => "SYNCING",
            Self::Conflict => "CONFLICT",
            Self::Failed => "FAILED",
            Self::SignedOutKeepLocal => "SIGNED_OUT_KEEP_LOCAL",
        }
    }
}
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Metadata {
    pub account_ref: String,
    pub state: State,
    pub revision: u64,
    pub key_alias_ref: Option<String>,
    pub wrapped_key_ref: Option<String>,
    pub wrapped_key_sha256: Option<String>,
    pub direction_fact: Option<String>,
    pub last_error: Option<String>,
}
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Receipt {
    pub intent_id: String,
    pub account_ref: String,
    pub expected_revision: Option<u64>,
    pub revision: u64,
    pub state: State,
}
fn err() -> String {
    "P7-B local state rejected".into()
}
pub fn account_ref(verified_opaque_id: &str) -> Result<String, String> {
    if !(8..=128).contains(&verified_opaque_id.len())
        || !verified_opaque_id
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || matches!(b, b'.' | b'_' | b'-'))
    {
        return Err(err());
    }
    Ok(format!("{:x}", Sha256::digest(verified_opaque_id.as_bytes()))[..32].to_owned())
}

pub trait CredentialStore {
    fn save(&self, service: &str, account: &str, secret: &[u8]) -> Result<(), String>;
    fn read(&self, service: &str, account: &str) -> Result<Vec<u8>, String>;
    fn delete(&self, service: &str, account: &str) -> Result<(), String>;
}
pub struct SqliteMetadataStore<'a> {
    pub connection: &'a mut Connection,
}
const APP_PRIVATE_KEY_ALIAS: &str = "com.nanzhufeng.ai.app-private-sync.v2";
/// P7-B desktop transition owner. No HTTP or Tauri command may call it in this phase.
pub fn authenticate<C: CredentialStore>(
    store: &mut SqliteMetadataStore<'_>,
    credential: &C,
    intent_id: &str,
    expected_revision: Option<u64>,
    verified_opaque_id: &str,
) -> Result<Receipt, String> {
    if let Some(replay) = store.receipt(intent_id)? {
        return Ok(replay);
    }
    let account = account_ref(verified_opaque_id)?;
    let existing = store.metadata(&account)?;
    if existing
        .as_ref()
        .is_some_and(|value| expected_revision.is_some_and(|revision| revision != value.revision))
    {
        return Err("REVISION_CONFLICT".into());
    }
    let metadata = match existing {
        None => {
            let mut key = [0u8; 32];
            OsRng.fill_bytes(&mut key);
            let fingerprint = format!("{:x}", Sha256::digest(key));
            credential.save(APP_PRIVATE_KEY_ALIAS, &account, &key)?;
            key.zeroize();
            Metadata {
                account_ref: account.clone(),
                state: State::NeedsRecoveryConfirmation,
                revision: 1,
                key_alias_ref: Some(APP_PRIVATE_KEY_ALIAS.into()),
                wrapped_key_ref: Some(format!("app-private/{account}")),
                wrapped_key_sha256: Some(fingerprint),
                direction_fact: None,
                last_error: None,
            }
        }
        Some(current)
            if !credential
                .read(APP_PRIVATE_KEY_ALIAS, &account)
                .is_ok_and(|key| key.len() == 32) =>
        {
            Metadata {
                state: State::Failed,
                revision: current.revision + 1,
                last_error: Some("KEY_MATERIAL_UNAVAILABLE".into()),
                ..current
            }
        }
        Some(current) if matches!(current.state, State::SignedOut | State::SignedOutKeepLocal) => {
            Metadata {
                state: State::DirectionRequired,
                revision: current.revision + 1,
                last_error: None,
                ..current
            }
        }
        Some(current) => current,
    };
    let receipt = Receipt {
        intent_id: intent_id.into(),
        account_ref: account,
        expected_revision,
        revision: metadata.revision,
        state: metadata.state,
    };
    store.save(&metadata, &receipt)?;
    Ok(receipt)
}
/// Direct account sync needs an authenticated identity, never a data-encryption key.
pub fn authenticate_direct(
    store: &mut SqliteMetadataStore<'_>,
    intent_id: &str,
    expected_revision: Option<u64>,
    verified_opaque_id: &str,
) -> Result<Receipt, String> {
    let account = account_ref(verified_opaque_id)?;
    if let Some(replay) = store.receipt(intent_id)? {
        if replay.account_ref != account {
            return Err(err());
        }
        return Ok(replay);
    }
    let existing = store.metadata(&account)?;
    if existing
        .as_ref()
        .is_some_and(|value| expected_revision.is_some_and(|revision| revision != value.revision))
    {
        return Err("REVISION_CONFLICT".into());
    }
    let metadata = match existing {
        None => Metadata {
            account_ref: account.clone(),
            state: State::Ready,
            revision: 1,
            key_alias_ref: None,
            wrapped_key_ref: None,
            wrapped_key_sha256: None,
            direction_fact: None,
            last_error: None,
        },
        Some(current)
            if matches!(
                current.state,
                State::SignedOut
                    | State::SignedOutKeepLocal
                    | State::NeedsRecoveryConfirmation
                    | State::DirectionRequired
            ) || (current.state == State::Failed
                && current.last_error.as_deref() == Some("KEY_MATERIAL_UNAVAILABLE")) =>
        {
            Metadata {
                state: State::Ready,
                revision: current.revision + 1,
                last_error: None,
                ..current
            }
        }
        Some(current) => current,
    };
    let receipt = Receipt {
        intent_id: intent_id.into(),
        account_ref: account,
        expected_revision,
        revision: metadata.revision,
        state: metadata.state,
    };
    store.save(&metadata, &receipt)?;
    Ok(receipt)
}

pub fn confirm_recovery_saved(
    store: &mut SqliteMetadataStore<'_>,
    intent_id: &str,
    account: &str,
    expected_revision: u64,
) -> Result<Receipt, String> {
    transition(
        store,
        intent_id,
        account,
        Some(expected_revision),
        |current| {
            if current.state != State::NeedsRecoveryConfirmation {
                return Err("RECOVERY_CONFIRMATION_REQUIRED".into());
            }
            Ok(Metadata {
                state: State::DirectionRequired,
                revision: current.revision + 1,
                ..current
            })
        },
    )
}

/// Re-open recovery setup only after the caller has independently proved that
/// the account has no remote documents and no selected local sync receipts.
/// This is deliberately a narrow migration escape hatch: it does not create
/// key material, upload data, or weaken the normal existing-code path.
pub fn restart_recovery_setup_for_empty_remote(
    store: &mut SqliteMetadataStore<'_>,
    intent_id: &str,
    account: &str,
    expected_revision: u64,
) -> Result<Receipt, String> {
    transition(
        store,
        intent_id,
        account,
        Some(expected_revision),
        |current| {
            Ok(Metadata {
                state: State::NeedsRecoveryConfirmation,
                revision: current.revision + 1,
                direction_fact: None,
                last_error: None,
                ..current
            })
        },
    )
}
pub fn choose_direction(
    store: &mut SqliteMetadataStore<'_>,
    intent_id: &str,
    account: &str,
    expected_revision: u64,
    fact: &str,
) -> Result<Receipt, String> {
    transition(
        store,
        intent_id,
        account,
        Some(expected_revision),
        |current| {
            if current.state != State::DirectionRequired {
                return Err("DIRECTION_REQUIRED".into());
            }
            let state = if fact == "LOCAL_PRESENT_REMOTE_PRESENT" {
                State::Conflict
            } else {
                State::Ready
            };
            Ok(Metadata {
                state,
                revision: current.revision + 1,
                direction_fact: Some(fact.into()),
                ..current
            })
        },
    )
}
pub fn sign_out_keep_local(
    store: &mut SqliteMetadataStore<'_>,
    intent_id: &str,
    account: &str,
    expected_revision: u64,
) -> Result<Receipt, String> {
    transition(
        store,
        intent_id,
        account,
        Some(expected_revision),
        |current| {
            Ok(Metadata {
                state: State::SignedOutKeepLocal,
                revision: current.revision + 1,
                ..current
            })
        },
    )
}
fn transition(
    store: &mut SqliteMetadataStore<'_>,
    intent_id: &str,
    account: &str,
    expected: Option<u64>,
    mutate: impl FnOnce(Metadata) -> Result<Metadata, String>,
) -> Result<Receipt, String> {
    if let Some(replay) = store.receipt(intent_id)? {
        return Ok(replay);
    }
    let current = store.metadata(account)?.ok_or_else(err)?;
    if expected != Some(current.revision) {
        return Err("REVISION_CONFLICT".into());
    }
    let metadata = mutate(current)?;
    let receipt = Receipt {
        intent_id: intent_id.into(),
        account_ref: account.into(),
        expected_revision: expected,
        revision: metadata.revision,
        state: metadata.state,
    };
    store.save(&metadata, &receipt)?;
    Ok(receipt)
}
impl<'a> SqliteMetadataStore<'a> {
    pub fn receipt(&self, intent: &str) -> Result<Option<Receipt>, String> {
        let mut s=self.connection.prepare("SELECT account_ref,expected_revision,resulting_revision,resulting_state FROM sync_intents WHERE intent_id=?1").map_err(|_|err())?;
        let row = s
            .query_row([intent], |r| {
                Ok(Receipt {
                    intent_id: intent.into(),
                    account_ref: r.get(0)?,
                    expected_revision: r.get::<_, Option<i64>>(1)?.map(|value| value as u64),
                    revision: r.get::<_, i64>(2)? as u64,
                    state: parse_state(&r.get::<_, String>(3)?)
                        .map_err(|_| rusqlite::Error::InvalidQuery)?,
                })
            })
            .ok();
        Ok(row)
    }
    pub fn metadata(&self, account: &str) -> Result<Option<Metadata>, String> {
        let mut s=self.connection.prepare("SELECT state,revision,key_alias_ref,wrapped_key_ref,wrapped_key_sha256,direction_fact,last_error FROM sync_account_metadata WHERE account_ref=?1").map_err(|_|err())?;
        let row = s
            .query_row([account], |r| {
                Ok(Metadata {
                    account_ref: account.into(),
                    state: parse_state(&r.get::<_, String>(0)?)
                        .map_err(|_| rusqlite::Error::InvalidQuery)?,
                    revision: r.get::<_, i64>(1)? as u64,
                    key_alias_ref: r.get(2)?,
                    wrapped_key_ref: r.get(3)?,
                    wrapped_key_sha256: r.get(4)?,
                    direction_fact: r.get(5)?,
                    last_error: r.get(6)?,
                })
            })
            .ok();
        Ok(row)
    }
    pub fn save(&mut self, meta: &Metadata, receipt: &Receipt) -> Result<(), String> {
        let tx = self.connection.transaction().map_err(|_| err())?;
        tx.execute("INSERT OR REPLACE INTO sync_account_metadata(account_ref,state,revision,key_alias_ref,wrapped_key_ref,wrapped_key_sha256,direction_fact,last_error,updated_at) VALUES(?1,?2,?3,?4,?5,?6,?7,?8,'2026-08-13T00:00:00Z')",params![meta.account_ref,meta.state.as_str(),meta.revision as i64,meta.key_alias_ref,meta.wrapped_key_ref,meta.wrapped_key_sha256,meta.direction_fact,meta.last_error]).map_err(|_|err())?;
        tx.execute("INSERT INTO sync_intents(intent_id,account_ref,expected_revision,resulting_revision,resulting_state,created_at) VALUES(?1,?2,?3,?4,?5,'2026-08-13T00:00:00Z')",params![receipt.intent_id,receipt.account_ref,receipt.expected_revision.map(|value| value as i64),receipt.revision as i64,receipt.state.as_str()]).map_err(|_|err())?;
        tx.commit().map_err(|_| err())
    }
}
fn parse_state(value: &str) -> Result<State, String> {
    match value {
        "SIGNED_OUT" => Ok(State::SignedOut),
        "AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION" => Ok(State::NeedsRecoveryConfirmation),
        "DIRECTION_REQUIRED" => Ok(State::DirectionRequired),
        "READY" => Ok(State::Ready),
        "SYNCING" => Ok(State::Syncing),
        "CONFLICT" => Ok(State::Conflict),
        "FAILED" => Ok(State::Failed),
        "SIGNED_OUT_KEEP_LOCAL" => Ok(State::SignedOutKeepLocal),
        _ => Err(err()),
    }
}
/// Ensures the P7-A caller sees an ephemeral key only; byte array is zeroized after callback returns.
pub fn with_key<C: CredentialStore, T>(
    store: &C,
    account_ref: &str,
    callback: impl FnOnce(&[u8]) -> T,
) -> Result<T, String> {
    let service = APP_PRIVATE_KEY_ALIAS;
    let mut key = store.read(service, account_ref)?;
    if key.len() != 32 {
        return Err(err());
    }
    let result = callback(&key);
    key.zeroize();
    Ok(result)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::{cell::RefCell, collections::BTreeMap};
    struct Mem(RefCell<BTreeMap<(String, String), Vec<u8>>>);
    impl CredentialStore for Mem {
        fn save(&self, s: &str, a: &str, k: &[u8]) -> Result<(), String> {
            self.0.borrow_mut().insert((s.into(), a.into()), k.into());
            Ok(())
        }
        fn read(&self, s: &str, a: &str) -> Result<Vec<u8>, String> {
            self.0
                .borrow()
                .get(&(s.into(), a.into()))
                .cloned()
                .ok_or_else(err)
        }
        fn delete(&self, s: &str, a: &str) -> Result<(), String> {
            self.0.borrow_mut().remove(&(s.into(), a.into()));
            Ok(())
        }
    }
    #[test]
    fn account_refs_are_opaque_and_key_callback_is_scoped() {
        let ref_a = account_ref("verified-account-a").unwrap();
        assert_ne!(ref_a, account_ref("verified-account-b").unwrap());
        let m = Mem(RefCell::new(BTreeMap::new()));
        m.save(APP_PRIVATE_KEY_ALIAS, &ref_a, &[7; 32]).unwrap();
        assert_eq!(with_key(&m, &ref_a, |key| key.len()).unwrap(), 32);
        m.delete(APP_PRIVATE_KEY_ALIAS, &ref_a).unwrap();
        assert!(with_key(&m, &ref_a, |_| ()).is_err());
    }
    #[test]
    fn sqlite_metadata_contains_only_safe_refs_and_idempotent_receipts() {
        let mut connection = Connection::open_in_memory().unwrap();
        connection.execute_batch("CREATE TABLE sync_account_metadata (account_ref TEXT PRIMARY KEY NOT NULL, state TEXT NOT NULL, revision INTEGER NOT NULL, key_alias_ref TEXT, wrapped_key_ref TEXT, wrapped_key_sha256 TEXT, direction_fact TEXT, last_error TEXT, updated_at TEXT NOT NULL); CREATE TABLE sync_intents (intent_id TEXT PRIMARY KEY NOT NULL, account_ref TEXT NOT NULL, expected_revision INTEGER, resulting_revision INTEGER NOT NULL, resulting_state TEXT NOT NULL, created_at TEXT NOT NULL);").unwrap();
        let mut store = SqliteMetadataStore {
            connection: &mut connection,
        };
        let account = account_ref("verified-account-p7b").unwrap();
        let meta = Metadata {
            account_ref: account.clone(),
            state: State::NeedsRecoveryConfirmation,
            revision: 1,
            key_alias_ref: Some("app-private-alias-ref".into()),
            wrapped_key_ref: Some("app-private-ref".into()),
            wrapped_key_sha256: Some("safe-hash".into()),
            direction_fact: None,
            last_error: None,
        };
        let receipt = Receipt {
            intent_id: "intent-auth-p7b".into(),
            account_ref: account.clone(),
            expected_revision: None,
            revision: 1,
            state: State::NeedsRecoveryConfirmation,
        };
        store.save(&meta, &receipt).unwrap();
        assert_eq!(store.receipt("intent-auth-p7b").unwrap(), Some(receipt));
        assert_eq!(
            store.metadata(&account).unwrap().unwrap().state,
            State::NeedsRecoveryConfirmation
        );
    }
    #[test]
    fn desktop_state_machine_requires_confirmation_then_direction_and_keeps_local_on_signout() {
        let mut connection = Connection::open_in_memory().unwrap();
        connection.execute_batch("CREATE TABLE sync_account_metadata (account_ref TEXT PRIMARY KEY NOT NULL, state TEXT NOT NULL, revision INTEGER NOT NULL, key_alias_ref TEXT, wrapped_key_ref TEXT, wrapped_key_sha256 TEXT, direction_fact TEXT, last_error TEXT, updated_at TEXT NOT NULL); CREATE TABLE sync_intents (intent_id TEXT PRIMARY KEY NOT NULL, account_ref TEXT NOT NULL, expected_revision INTEGER, resulting_revision INTEGER NOT NULL, resulting_state TEXT NOT NULL, created_at TEXT NOT NULL);").unwrap();
        let mut store = SqliteMetadataStore {
            connection: &mut connection,
        };
        let keys = Mem(RefCell::new(BTreeMap::new()));
        let created =
            authenticate(&mut store, &keys, "auth", None, "verified-desktop-account").unwrap();
        assert_eq!(created.state, State::NeedsRecoveryConfirmation);
        assert_eq!(
            authenticate(&mut store, &keys, "auth", None, "verified-desktop-account").unwrap(),
            created
        );
        let confirmed =
            confirm_recovery_saved(&mut store, "confirm", &created.account_ref, 1).unwrap();
        assert_eq!(confirmed.state, State::DirectionRequired);
        let ready = choose_direction(
            &mut store,
            "direction",
            &created.account_ref,
            2,
            "LOCAL_PRESENT_EMPTY_REMOTE",
        )
        .unwrap();
        assert_eq!(ready.state, State::Ready);
        assert_eq!(
            sign_out_keep_local(&mut store, "logout", &created.account_ref, 3)
                .unwrap()
                .state,
            State::SignedOutKeepLocal
        );
    }
}
