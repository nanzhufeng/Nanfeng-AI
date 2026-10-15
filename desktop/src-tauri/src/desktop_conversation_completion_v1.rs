//! Process-local single owner for optional title work, scoped to the physical data area.
use std::collections::BTreeSet;
use std::sync::{Mutex, OnceLock};
type Key = (String, String, String);
fn active() -> &'static Mutex<BTreeSet<Key>> {
    static ACTIVE: OnceLock<Mutex<BTreeSet<Key>>> = OnceLock::new();
    ACTIVE.get_or_init(|| Mutex::new(BTreeSet::new()))
}
pub struct TitleWorkLease(Key);
impl TitleWorkLease {
    pub fn acquire(area: &str, workspace: &str, conversation: &str) -> Option<Self> {
        let key = (area.into(), workspace.into(), conversation.into());
        if active().lock().ok()?.insert(key.clone()) { Some(Self(key)) } else { None }
    }
}
impl Drop for TitleWorkLease {
    fn drop(&mut self) {
        if let Ok(mut keys) = active().lock() { keys.remove(&self.0); }
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn blocked_title_is_single_owner_and_release_allows_later_work() {
        let first = TitleWorkLease::acquire("CHAT", "title-test", "conversation").unwrap();
        assert!(TitleWorkLease::acquire("CHAT", "title-test", "conversation").is_none());
        let other_area = TitleWorkLease::acquire("WORK", "title-test", "conversation").unwrap();
        let other_workspace = TitleWorkLease::acquire("CHAT", "other-title-test", "conversation").unwrap();
        drop(first);
        assert!(TitleWorkLease::acquire("CHAT", "title-test", "conversation").is_some());
        drop((other_area, other_workspace));
    }
}
