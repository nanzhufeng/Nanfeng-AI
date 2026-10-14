//! Best-effort bounded local metadata; never a second task store or a retry owner.
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::{
    path::{Path, PathBuf},
    sync::Mutex,
    time::{Instant, SystemTime, UNIX_EPOCH},
};
static JOURNAL_LOCK: Mutex<()> = Mutex::new(());
pub struct ExitDrainEpoch(std::sync::atomic::AtomicU64);
impl ExitDrainEpoch {
    pub const fn new() -> Self {
        Self(std::sync::atomic::AtomicU64::new(0))
    }
    pub fn advance(&self) -> u64 {
        self.0.fetch_add(1, std::sync::atomic::Ordering::SeqCst) + 1
    }
    pub fn owns(&self, epoch: u64) -> bool {
        self.0.load(std::sync::atomic::Ordering::SeqCst) == epoch
    }
}
#[derive(Serialize, Deserialize)]
struct Event {
    at_ms: u128,
    task: String,
    stage: String,
    age_ms: u128,
}
pub struct Trace {
    root: PathBuf,
    task: String,
    started: Instant,
    last_progress: Option<Instant>,
    finished: bool,
}
impl Trace {
    pub fn begin(root: &Path, task: &str) -> Self {
        let value = Self {
            root: root.to_owned(),
            task: hex::encode(Sha256::digest(task.as_bytes()))[..12].into(),
            started: Instant::now(),
            last_progress: None,
            finished: false,
        };
        value.record("START");
        value
    }
    fn record(&self, stage: &str) {
        if stage.len() > 80
            || !stage
                .bytes()
                .all(|b| b.is_ascii_uppercase() || b.is_ascii_digit() || b == b'_')
        {
            return;
        }
        let _ = (|| -> Result<(), Box<dyn std::error::Error>> {
            let _lock = JOURNAL_LOCK.lock().map_err(|_| "journal lock")?;
            let path = self.root.join("generation-continuity-v1.json");
            let mut events: Vec<Event> =
                if std::fs::metadata(&path).is_ok_and(|m| m.len() <= 32_768) {
                    serde_json::from_slice(&std::fs::read(&path)?).unwrap_or_default()
                } else {
                    Vec::new()
                };
            events.push(Event {
                at_ms: SystemTime::now().duration_since(UNIX_EPOCH)?.as_millis(),
                task: self.task.clone(),
                stage: stage.into(),
                age_ms: self.started.elapsed().as_millis(),
            });
            if events.len() > 64 {
                events.drain(..events.len() - 64);
            }
            let pending = path.with_extension("pending");
            std::fs::write(&pending, serde_json::to_vec(&events)?)?;
            std::fs::rename(pending, path)?;
            Ok(())
        })();
    }
    pub fn progress(&mut self) {
        if self.last_progress.is_none()
            || self
                .last_progress
                .is_some_and(|last| last.elapsed().as_secs() >= 10)
        {
            self.record(if self.last_progress.is_none() {
                "FIRST_TEXT"
            } else {
                "PROGRESS"
            });
            self.last_progress = Some(Instant::now());
        }
    }
    pub fn finish(&mut self, stage: &str) {
        self.record(stage);
        self.finished = true;
    }
}
impl Drop for Trace {
    fn drop(&mut self) {
        if !self.finished {
            self.record("OWNER_EXIT_UNSETTLED");
        }
    }
}

/// Prevent App Nap while a user-requested generation is active. System sleep stays allowed.
pub struct ActivityGuard {
    #[cfg(target_os = "macos")]
    token:
        objc2::rc::Retained<objc2::runtime::ProtocolObject<dyn objc2_foundation::NSObjectProtocol>>,
}
impl ActivityGuard {
    pub fn begin() -> Self {
        #[cfg(target_os = "macos")]
        {
            Self {
                token: objc2_foundation::NSProcessInfo::processInfo()
                    .beginActivityWithOptions_reason(
                        objc2_foundation::NSActivityOptions::UserInitiatedAllowingIdleSystemSleep,
                        &objc2_foundation::NSString::from_str("User requested answer generation"),
                    ),
            }
        }
        #[cfg(not(target_os = "macos"))]
        {
            Self {}
        }
    }
}
impl Drop for ActivityGuard {
    fn drop(&mut self) {
        #[cfg(target_os = "macos")]
        // This is the exact token returned by beginActivity; the owning guard ends it once.
        unsafe {
            objc2_foundation::NSProcessInfo::processInfo().endActivity(&self.token);
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn reopening_revokes_old_exit_even_if_the_user_quits_again() {
        let owner = ExitDrainEpoch::new();
        let old_exit = owner.advance();
        assert!(owner.owns(old_exit));
        owner.advance(); // Reopen / focus.
        assert!(!owner.owns(old_exit));
        let new_exit = owner.advance();
        assert!(owner.owns(new_exit));
        assert!(!owner.owns(old_exit));
    }
    #[test]
    fn generation_activity_has_bounded_ownership() {
        let activity = ActivityGuard::begin();
        drop(activity);
    }
    #[test]
    fn trace_is_bounded_redacted_and_records_unsettled_exit() {
        let root = tempfile::tempdir().unwrap();
        for _ in 0..70 {
            let mut trace = Trace::begin(root.path(), "private-conversation-name");
            trace.progress();
            trace.finish("COMPLETED");
        }
        {
            let _trace = Trace::begin(root.path(), "private-conversation-name");
        }
        let bytes = std::fs::read(root.path().join("generation-continuity-v1.json")).unwrap();
        let events: Vec<Event> = serde_json::from_slice(&bytes).unwrap();
        assert_eq!(events.len(), 64);
        assert_eq!(events.last().unwrap().stage, "OWNER_EXIT_UNSETTLED");
        assert!(!String::from_utf8(bytes)
            .unwrap()
            .contains("private-conversation-name"));
    }
}
