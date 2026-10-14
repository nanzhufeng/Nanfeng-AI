//! Attempt-owned, device-local normalized evidence. Never export or store raw provider bodies.
use crate::desktop_ordinary_chat_v1::Failure;
use serde::{Deserialize, Serialize};
#[derive(Clone, Serialize, Deserialize)]
pub struct Entry {
    pub text: String,
    pub footer: String,
    pub model: String,
    pub created_at: i64,
    pub invocation: String,
}
pub trait Cache {
    fn get(&mut self, item: &str) -> Result<Option<Entry>, Failure>;
    fn put(&mut self, item: &str, value: Entry) -> Result<(), Failure>;
    fn coverage(&mut self, _planned: usize, _covered: usize) -> Result<(), Failure> {
        Ok(())
    }
}
pub struct None;
impl Cache for None {
    fn get(&mut self, _: &str) -> Result<Option<Entry>, Failure> {
        Ok(std::option::Option::None)
    }
    fn put(&mut self, _: &str, _: Entry) -> Result<(), Failure> {
        Ok(())
    }
}
pub struct Callbacks<L, S, C> {
    pub load: L,
    pub save: S,
    pub coverage: C,
}
impl<L, S, C> Cache for Callbacks<L, S, C>
where
    L: FnMut(&str) -> Result<Option<Entry>, Failure>,
    S: FnMut(&str, Entry) -> Result<(), Failure>,
    C: FnMut(usize, usize) -> Result<(), Failure>,
{
    fn get(&mut self, item: &str) -> Result<Option<Entry>, Failure> {
        (self.load)(item)
    }
    fn put(&mut self, item: &str, value: Entry) -> Result<(), Failure> {
        (self.save)(item, value)
    }
    fn coverage(&mut self, planned: usize, covered: usize) -> Result<(), Failure> {
        (self.coverage)(planned, covered)
    }
}
pub fn migrate(c: &rusqlite::Connection) -> Result<(), String> {
    c.execute_batch("CREATE TABLE IF NOT EXISTS desktop_attachment_search_checkpoints(attempt_id TEXT NOT NULL,scope_fingerprint TEXT NOT NULL,item_id TEXT NOT NULL,evidence_json TEXT NOT NULL,PRIMARY KEY(attempt_id,scope_fingerprint,item_id),FOREIGN KEY(attempt_id) REFERENCES desktop_ordinary_chat_attempts(attempt_id) ON DELETE CASCADE);").map_err(|_|"核验检查点迁移失败".into())
}
pub fn fingerprint(value: &str) -> String {
    use sha2::Digest;
    format!("{:x}", sha2::Sha256::digest(value.as_bytes()))
}
pub fn fresh(entry: &Entry, model: &str, query: &str, now: i64) -> bool {
    let volatile =
        regex::Regex::new("当前|最新|实时|今日|今天|股价|汇率|收益率|天气|气温|价格").unwrap();
    (0..=600_000).contains(&(now - entry.created_at))
        && entry.model == model
        && !volatile.is_match(query)
        && entry.text.len() <= 32768
        && entry.footer.len() <= 32768
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn forward_migration_and_checkpoint_reopen_preserve_attempt_and_old_receipts() {
        let root = tempfile::tempdir().unwrap();
        let path = root.path().join("workspace.db");
        let c = rusqlite::Connection::open(&path).unwrap();
        c.execute_batch("PRAGMA foreign_keys=ON;CREATE TABLE desktop_ordinary_chat_attempts(attempt_id TEXT PRIMARY KEY,disclosure_version TEXT);INSERT INTO desktop_ordinary_chat_attempts VALUES('old','normal-chat-relay-search-v2');").unwrap();
        migrate(&c).unwrap();
        let value = Entry {
            text: "A1 Python在2025年发布\n已核实：公开日期。".into(),
            footer: "- [官方](https://example.org/)".into(),
            model: "gpt-6.1-sol".into(),
            created_at: 1000,
            invocation: "call-original".into(),
        };
        c.execute(
            "INSERT INTO desktop_attachment_search_checkpoints VALUES('old','fingerprint','A1',?1)",
            [serde_json::to_string(&value).unwrap()],
        )
        .unwrap();
        drop(c);
        let c = rusqlite::Connection::open(&path).unwrap();
        let json: String = c
            .query_row(
                "SELECT evidence_json FROM desktop_attachment_search_checkpoints",
                [],
                |r| r.get(0),
            )
            .unwrap();
        let row: Entry = serde_json::from_str(&json).unwrap();
        assert!(fresh(&row, "gpt-6.1-sol", "Python日期", 1001));
        assert!(!fresh(&row, "gpt-6.1-sol", "Python日期", 601001));
        assert!(!fresh(&row, "gpt-6-astra", "Python日期", 1001));
        assert!(!fresh(&row, "gpt-6.1-sol", "最新股价", 1001));
        let old: String = c
            .query_row(
                "SELECT disclosure_version FROM desktop_ordinary_chat_attempts",
                [],
                |r| r.get(0),
            )
            .unwrap();
        assert_eq!(old, "normal-chat-relay-search-v2");
    }
}
