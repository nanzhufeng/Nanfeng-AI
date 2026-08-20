//! Unregistered Desktop Compare execution boundary.
//! It has one fixed OpenAI-compatible endpoint, but no production HTTP client,
//! Tauri command, Settings action, or Keychain invocation is registered here.
//! The adapter only persists content-free branch runtime facts.

use crate::desktop_compare_credentials_v1::CompareCredentialStore;
use rusqlite::{params, Connection};
use serde_json::json;
use zeroize::Zeroizing;

pub const OPENROUTER_CHAT_COMPLETIONS_ENDPOINT: &str =
    "https://openrouter.ai/api/v1/chat/completions";
const DIRECT_CLICK_TTL_MS: i64 = 30_000;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CompareLogicalModel {
    ChatGpt,
    Claude,
}

impl CompareLogicalModel {
    fn as_str(self) -> &'static str {
        match self {
            Self::ChatGpt => "CHATGPT",
            Self::Claude => "CLAUDE",
        }
    }

    fn parse(value: &str) -> Result<Self, String> {
        match value {
            "CHATGPT" => Ok(Self::ChatGpt),
            "CLAUDE" => Ok(Self::Claude),
            _ => Err("Desktop Compare receipt is invalid".into()),
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DesktopCompareDirectClickCommand {
    pub provider: String,
    pub logical_models: [CompareLogicalModel; 2],
    pub issued_at_ms: i64,
    pub expires_at_ms: i64,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct VerifiedCompareDeployment {
    pub logical_model: CompareLogicalModel,
    pub provider_model: String,
    /// `None` is deliberately indistinguishable from an unverified catalogue.
    pub price_micros: Option<u64>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct VerifiedCompareCatalog([VerifiedCompareDeployment; 2]);

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CompareAdapterBlocker {
    ModelOrPriceUnverified,
    DirectClickInvalid,
}

impl VerifiedCompareCatalog {
    pub fn try_new(entries: [VerifiedCompareDeployment; 2]) -> Result<Self, CompareAdapterBlocker> {
        let expected = [CompareLogicalModel::ChatGpt, CompareLogicalModel::Claude];
        if entries
            .iter()
            .map(|entry| entry.logical_model)
            .collect::<Vec<_>>()
            != expected
            || entries
                .iter()
                .any(|entry| entry.provider_model.trim().is_empty() || entry.price_micros.is_none())
        {
            return Err(CompareAdapterBlocker::ModelOrPriceUnverified);
        }
        Ok(Self(entries))
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CompareFailureCategory {
    CredentialUnavailable,
    Timeout,
    Network,
    HttpStatus,
    InvalidResponse,
}

impl CompareFailureCategory {
    fn as_str(self) -> &'static str {
        match self {
            Self::CredentialUnavailable => "CREDENTIAL_UNAVAILABLE",
            Self::Timeout => "TIMEOUT",
            Self::Network => "NETWORK",
            Self::HttpStatus => "HTTP_STATUS",
            Self::InvalidResponse => "INVALID_RESPONSE",
        }
    }

    fn parse(value: &str) -> Result<Self, String> {
        match value {
            "CREDENTIAL_UNAVAILABLE" => Ok(Self::CredentialUnavailable),
            "TIMEOUT" => Ok(Self::Timeout),
            "NETWORK" => Ok(Self::Network),
            "HTTP_STATUS" => Ok(Self::HttpStatus),
            "INVALID_RESPONSE" => Ok(Self::InvalidResponse),
            _ => Err("Desktop Compare receipt is invalid".into()),
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum CompareBranchStatus {
    Succeeded,
    Failed(CompareFailureCategory),
}

impl CompareBranchStatus {
    fn as_str(&self) -> String {
        match self {
            Self::Succeeded => "SUCCEEDED".into(),
            Self::Failed(category) => format!("FAILED_{}", category.as_str()),
        }
    }

    fn parse(value: &str) -> Result<Self, String> {
        if value == "SUCCEEDED" {
            return Ok(Self::Succeeded);
        }
        let category = value
            .strip_prefix("FAILED_")
            .ok_or_else(|| "Desktop Compare receipt is invalid".to_owned())?;
        Ok(Self::Failed(CompareFailureCategory::parse(category)?))
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CompareBranchReceipt {
    pub execution_id: String,
    pub logical_model: CompareLogicalModel,
    pub provider_model: String,
    pub status: CompareBranchStatus,
    pub status_code: Option<u16>,
    pub elapsed_ms: u64,
    pub recorded_at_ms: i64,
}

impl CompareBranchReceipt {
    pub fn content_free(&self) -> bool {
        // The type has no fields for a draft, credential, request or provider response.
        !self.execution_id.is_empty() && !self.provider_model.is_empty()
    }
}

pub trait CompareReceiptStore {
    fn save(&self, receipt: CompareBranchReceipt) -> Result<(), String>;
}

pub struct SqliteCompareReceiptStore {
    connection: Connection,
}

impl SqliteCompareReceiptStore {
    pub fn new(connection: Connection) -> Result<Self, String> {
        connection
            .execute_batch(
                "CREATE TABLE IF NOT EXISTS desktop_compare_branch_receipts (
                execution_id TEXT NOT NULL,
                logical_model TEXT NOT NULL,
                provider_model TEXT NOT NULL,
                status TEXT NOT NULL,
                status_code INTEGER,
                elapsed_ms INTEGER NOT NULL,
                recorded_at_ms INTEGER NOT NULL,
                PRIMARY KEY(execution_id, logical_model)
            );",
            )
            .map_err(|_| "Desktop Compare receipt storage unavailable".to_owned())?;
        Ok(Self { connection })
    }

    pub fn read(&self, execution_id: &str) -> Result<Vec<CompareBranchReceipt>, String> {
        let mut statement = self.connection.prepare(
            "SELECT execution_id, logical_model, provider_model, status, status_code, elapsed_ms, recorded_at_ms
             FROM desktop_compare_branch_receipts WHERE execution_id=?1 ORDER BY logical_model ASC"
        ).map_err(|_| "Desktop Compare receipt storage unavailable".to_owned())?;
        let rows = statement
            .query_map([execution_id], |row| {
                let status_code: Option<i64> = row.get(4)?;
                Ok((
                    row.get::<_, String>(0)?,
                    row.get::<_, String>(1)?,
                    row.get::<_, String>(2)?,
                    row.get::<_, String>(3)?,
                    status_code,
                    row.get::<_, i64>(5)?,
                    row.get::<_, i64>(6)?,
                ))
            })
            .map_err(|_| "Desktop Compare receipt storage unavailable".to_owned())?;
        let raw = rows
            .collect::<Result<Vec<_>, _>>()
            .map_err(|_| "Desktop Compare receipt storage unavailable".to_owned())?;
        raw.into_iter()
            .map(
                |(
                    execution_id,
                    logical_model,
                    provider_model,
                    status,
                    status_code,
                    elapsed_ms,
                    recorded_at_ms,
                )| {
                    Ok(CompareBranchReceipt {
                        execution_id,
                        logical_model: CompareLogicalModel::parse(&logical_model)?,
                        provider_model,
                        status: CompareBranchStatus::parse(&status)?,
                        status_code: status_code
                            .map(|value| {
                                u16::try_from(value)
                                    .map_err(|_| "Desktop Compare receipt is invalid".to_owned())
                            })
                            .transpose()?,
                        elapsed_ms: u64::try_from(elapsed_ms)
                            .map_err(|_| "Desktop Compare receipt is invalid".to_owned())?,
                        recorded_at_ms,
                    })
                },
            )
            .collect()
    }

    #[cfg(test)]
    fn column_names(&self) -> Result<Vec<String>, String> {
        let mut statement = self.connection.prepare("SELECT name FROM pragma_table_info('desktop_compare_branch_receipts') ORDER BY cid")
            .map_err(|_| "Desktop Compare receipt storage unavailable".to_owned())?;
        let rows = statement
            .query_map([], |row| row.get(0))
            .map_err(|_| "Desktop Compare receipt storage unavailable".to_owned())?;
        rows.collect::<Result<Vec<String>, _>>()
            .map_err(|_| "Desktop Compare receipt storage unavailable".to_owned())
    }
}

impl CompareReceiptStore for SqliteCompareReceiptStore {
    fn save(&self, receipt: CompareBranchReceipt) -> Result<(), String> {
        self.connection.execute(
            "INSERT INTO desktop_compare_branch_receipts (execution_id, logical_model, provider_model, status, status_code, elapsed_ms, recorded_at_ms)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)",
            params![receipt.execution_id, receipt.logical_model.as_str(), receipt.provider_model, receipt.status.as_str(), receipt.status_code.map(i64::from), i64::try_from(receipt.elapsed_ms).map_err(|_| "Desktop Compare receipt is invalid".to_owned())?, receipt.recorded_at_ms],
        ).map_err(|_| "Desktop Compare receipt storage unavailable".to_owned())?;
        Ok(())
    }
}

pub struct CompareHttpRequest<'a> {
    pub endpoint: &'static str,
    pub model: &'a str,
    pub credential: &'a [u8],
    pub json_body: &'a str,
    pub stream: bool,
}

pub enum CompareHttpOutcome {
    Success {
        status_code: u16,
        elapsed_ms: u64,
    },
    Failure {
        category: CompareFailureCategory,
        elapsed_ms: u64,
    },
}

/// The sole egress seam. Production does not provide an implementation in this phase.
pub trait OpenAiCompatibleCompareHttpPort {
    fn post_chat_completion(&self, request: CompareHttpRequest<'_>) -> CompareHttpOutcome;
}

pub struct DesktopOpenRouterCompareAdapter<'a, C, H, R, N> {
    credentials: &'a C,
    http: &'a H,
    receipts: &'a R,
    now: N,
}

impl<'a, C, H, R, N> DesktopOpenRouterCompareAdapter<'a, C, H, R, N>
where
    C: CompareCredentialStore,
    H: OpenAiCompatibleCompareHttpPort,
    R: CompareReceiptStore,
    N: Fn() -> i64,
{
    pub fn new(credentials: &'a C, http: &'a H, receipts: &'a R, now: N) -> Self {
        Self {
            credentials,
            http,
            receipts,
            now,
        }
    }

    pub fn execute(
        &self,
        execution_id: &str,
        command: DesktopCompareDirectClickCommand,
        catalog: VerifiedCompareCatalog,
        draft: String,
    ) -> Result<Vec<CompareBranchReceipt>, String> {
        let draft = Zeroizing::new(draft);
        if draft.trim().is_empty() {
            return Err("Desktop Compare draft is empty".into());
        }
        self.validate(execution_id, &command)?;
        match self.credentials.with_secret(|credential| {
            self.execute_with_secret(execution_id, catalog.clone(), draft.as_str(), credential)
        }) {
            Ok(receipts) => Ok(receipts),
            Err(_) => self.persist_credential_unavailable(execution_id, catalog),
        }
    }

    fn validate(
        &self,
        execution_id: &str,
        command: &DesktopCompareDirectClickCommand,
    ) -> Result<(), String> {
        let now = (self.now)();
        if !safe_execution_id(execution_id)
            || command.provider != "openrouter"
            || command.logical_models != [CompareLogicalModel::ChatGpt, CompareLogicalModel::Claude]
            || command.expires_at_ms.checked_sub(command.issued_at_ms) != Some(DIRECT_CLICK_TTL_MS)
            || now < command.issued_at_ms
            || now > command.expires_at_ms
        {
            return Err("Desktop Compare direct-click command is invalid".into());
        }
        Ok(())
    }

    fn execute_with_secret(
        &self,
        execution_id: &str,
        catalog: VerifiedCompareCatalog,
        draft: &str,
        credential: &[u8],
    ) -> Result<Vec<CompareBranchReceipt>, String> {
        let mut results = Vec::with_capacity(2);
        for deployment in catalog.0 {
            let body = Zeroizing::new(
                json!({
                    "model": deployment.provider_model,
                    "stream": false,
                    "messages": [{ "role": "user", "content": draft }],
                })
                .to_string(),
            );
            let (status, status_code, elapsed_ms) =
                match self.http.post_chat_completion(CompareHttpRequest {
                    endpoint: OPENROUTER_CHAT_COMPLETIONS_ENDPOINT,
                    model: &deployment.provider_model,
                    credential,
                    json_body: body.as_str(),
                    stream: false,
                }) {
                    CompareHttpOutcome::Success {
                        status_code,
                        elapsed_ms,
                    } if (200..300).contains(&status_code) => (
                        CompareBranchStatus::Succeeded,
                        Some(status_code),
                        elapsed_ms,
                    ),
                    CompareHttpOutcome::Success {
                        status_code,
                        elapsed_ms,
                    } => (
                        CompareBranchStatus::Failed(CompareFailureCategory::HttpStatus),
                        Some(status_code),
                        elapsed_ms,
                    ),
                    CompareHttpOutcome::Failure {
                        category,
                        elapsed_ms,
                    } => (CompareBranchStatus::Failed(category), None, elapsed_ms),
                };
            let receipt = CompareBranchReceipt {
                execution_id: execution_id.into(),
                logical_model: deployment.logical_model,
                provider_model: deployment.provider_model,
                status,
                status_code,
                elapsed_ms,
                recorded_at_ms: (self.now)(),
            };
            self.receipts.save(receipt.clone())?;
            results.push(receipt);
        }
        Ok(results)
    }

    fn persist_credential_unavailable(
        &self,
        execution_id: &str,
        catalog: VerifiedCompareCatalog,
    ) -> Result<Vec<CompareBranchReceipt>, String> {
        let mut results = Vec::with_capacity(2);
        for deployment in catalog.0 {
            let receipt = CompareBranchReceipt {
                execution_id: execution_id.into(),
                logical_model: deployment.logical_model,
                provider_model: deployment.provider_model,
                status: CompareBranchStatus::Failed(CompareFailureCategory::CredentialUnavailable),
                status_code: None,
                elapsed_ms: 0,
                recorded_at_ms: (self.now)(),
            };
            self.receipts.save(receipt.clone())?;
            results.push(receipt);
        }
        Ok(results)
    }
}

fn safe_execution_id(value: &str) -> bool {
    !value.is_empty()
        && value.len() <= 128
        && value
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || byte == b'-' || byte == b'_')
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::desktop_compare_credentials_v1::{CompareCredentialStore, Presence};
    use std::cell::{Cell, RefCell};

    struct MemoryCredentials;
    impl CompareCredentialStore for MemoryCredentials {
        fn presence(&self) -> Presence {
            Presence::Present
        }
        fn save_user_provided_secret(&self, _: &[u8]) -> Result<(), String> {
            Ok(())
        }
        fn with_secret<T>(
            &self,
            operation: impl FnOnce(&[u8]) -> Result<T, String>,
        ) -> Result<T, String> {
            operation(b"mock-only-credential")
        }
    }

    #[derive(Default)]
    struct MockHttp {
        calls: Cell<usize>,
        facts: RefCell<Vec<(String, String, bool)>>,
    }
    impl OpenAiCompatibleCompareHttpPort for MockHttp {
        fn post_chat_completion(&self, request: CompareHttpRequest<'_>) -> CompareHttpOutcome {
            self.calls.set(self.calls.get() + 1);
            self.facts.borrow_mut().push((
                request.endpoint.to_owned(),
                request.model.to_owned(),
                request.stream,
            ));
            if request.model == "mock-chatgpt" {
                CompareHttpOutcome::Success {
                    status_code: 200,
                    elapsed_ms: 12,
                }
            } else {
                CompareHttpOutcome::Failure {
                    category: CompareFailureCategory::Timeout,
                    elapsed_ms: 30,
                }
            }
        }
    }

    fn verified_catalog() -> VerifiedCompareCatalog {
        VerifiedCompareCatalog::try_new([
            VerifiedCompareDeployment {
                logical_model: CompareLogicalModel::ChatGpt,
                provider_model: "mock-chatgpt".into(),
                price_micros: Some(10),
            },
            VerifiedCompareDeployment {
                logical_model: CompareLogicalModel::Claude,
                provider_model: "mock-claude".into(),
                price_micros: Some(20),
            },
        ])
        .unwrap()
    }

    fn command() -> DesktopCompareDirectClickCommand {
        DesktopCompareDirectClickCommand {
            provider: "openrouter".into(),
            logical_models: [CompareLogicalModel::ChatGpt, CompareLogicalModel::Claude],
            issued_at_ms: 100,
            expires_at_ms: 30_100,
        }
    }

    #[test]
    fn unknown_provider_model_or_price_is_rejected_before_credential_http_or_receipt() {
        let rejected = VerifiedCompareCatalog::try_new([
            VerifiedCompareDeployment {
                logical_model: CompareLogicalModel::ChatGpt,
                provider_model: "mock-chatgpt".into(),
                price_micros: Some(10),
            },
            VerifiedCompareDeployment {
                logical_model: CompareLogicalModel::Claude,
                provider_model: "".into(),
                price_micros: None,
            },
        ]);
        assert_eq!(
            rejected.unwrap_err(),
            CompareAdapterBlocker::ModelOrPriceUnverified
        );
    }

    #[test]
    fn fixed_endpoint_mock_adapter_persists_two_content_free_branch_receipts() {
        let credentials = MemoryCredentials;
        let receipts =
            SqliteCompareReceiptStore::new(rusqlite::Connection::open_in_memory().unwrap())
                .unwrap();
        let http = MockHttp::default();
        let adapter =
            DesktopOpenRouterCompareAdapter::new(&credentials, &http, &receipts, || 1_000);

        let result = adapter
            .execute(
                "execution-1",
                command(),
                verified_catalog(),
                "private draft text".into(),
            )
            .unwrap();

        assert_eq!(http.calls.get(), 2);
        assert_eq!(
            http.facts.borrow().as_slice(),
            [
                (
                    OPENROUTER_CHAT_COMPLETIONS_ENDPOINT.into(),
                    "mock-chatgpt".into(),
                    false
                ),
                (
                    OPENROUTER_CHAT_COMPLETIONS_ENDPOINT.into(),
                    "mock-claude".into(),
                    false
                ),
            ]
        );
        assert_eq!(result.len(), 2);
        assert_eq!(result[0].status, CompareBranchStatus::Succeeded);
        assert_eq!(
            result[1].status,
            CompareBranchStatus::Failed(CompareFailureCategory::Timeout)
        );
        assert_eq!(receipts.read("execution-1").unwrap(), result);
        assert!(result.iter().all(|receipt| receipt.content_free()));
    }

    #[test]
    fn empty_draft_or_stale_click_is_rejected_before_mock_http_or_receipts() {
        let credentials = MemoryCredentials;
        let receipts =
            SqliteCompareReceiptStore::new(rusqlite::Connection::open_in_memory().unwrap())
                .unwrap();
        let http = MockHttp::default();
        let adapter =
            DesktopOpenRouterCompareAdapter::new(&credentials, &http, &receipts, || 1_000);

        assert!(adapter
            .execute("execution-1", command(), verified_catalog(), "  ".into())
            .is_err());
        let mut stale = command();
        stale.expires_at_ms = 999;
        assert!(adapter
            .execute(
                "execution-1",
                stale,
                verified_catalog(),
                "private draft text".into()
            )
            .is_err());
        assert_eq!(http.calls.get(), 0);
        assert!(receipts.read("execution-1").unwrap().is_empty());
    }

    #[test]
    fn source_has_no_tauri_registration_or_real_http_client() {
        let source = include_str!("desktop_compare_execution_v1.rs");
        let forbidden = [
            ["#[", "tauri::command]"].concat(),
            ["req", "west"].concat(),
            ["security_", "framework"].concat(),
            ["/usr/bin/", "security"].concat(),
        ];
        assert!(forbidden.iter().all(|token| !source.contains(token)));
    }

    #[test]
    fn sqlite_receipt_store_keeps_only_safe_branch_metadata() {
        let connection = rusqlite::Connection::open_in_memory().unwrap();
        let store = SqliteCompareReceiptStore::new(connection).unwrap();
        store
            .save(CompareBranchReceipt {
                execution_id: "execution-1".into(),
                logical_model: CompareLogicalModel::ChatGpt,
                provider_model: "mock-chatgpt".into(),
                status: CompareBranchStatus::Succeeded,
                status_code: Some(200),
                elapsed_ms: 12,
                recorded_at_ms: 1_000,
            })
            .unwrap();
        let columns = store.column_names().unwrap();
        assert_eq!(
            columns,
            vec![
                "execution_id",
                "logical_model",
                "provider_model",
                "status",
                "status_code",
                "elapsed_ms",
                "recorded_at_ms"
            ]
        );
        assert_eq!(store.read("execution-1").unwrap().len(), 1);
    }
}
