//! Best-effort bounded local metadata; never a second task store or a retry owner.
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::{
    path::{Path, PathBuf},
    sync::Mutex,
    time::{Instant, SystemTime, UNIX_EPOCH},
};
static JOURNAL_LOCK: Mutex<()> = Mutex::new(());
fn valid_stage(stage:&str)->bool {
    !stage.is_empty() && stage.len()<=80 && stage.bytes().all(|b|b.is_ascii_uppercase()||b.is_ascii_digit()||b==b'_')
}
#[derive(Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
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
        Self::begin_stage(root,task,"START")
    }
    pub fn begin_stage(root:&Path,task:&str,stage:&str)->Self {
        let value = Self {
            root: root.to_owned(),
            task: hex::encode(Sha256::digest(task.as_bytes()))[..12].into(),
            started: Instant::now(),
            last_progress: None,
            finished: false,
        };
        value.record(stage);
        value
    }
    fn record(&self, stage: &str) {
        if !valid_stage(stage) { return; }
        let _ = (|| -> Result<(), Box<dyn std::error::Error>> {
            use std::io::{Read, Write};
            let _lock = JOURNAL_LOCK.lock().map_err(|_| "journal lock")?;
            let root_meta=std::fs::symlink_metadata(&self.root)?;
            if !root_meta.is_dir() || root_meta.file_type().is_symlink() {return Err("journal root".into());}
            let path=self.root.join("generation-continuity-v1.json");
            let mut events:Vec<Event>=match std::fs::symlink_metadata(&path) {
                Ok(meta)=>{
                    if !meta.is_file() || meta.file_type().is_symlink() || meta.len()>32768 {return Err("journal shape".into());}
                    let mut bytes=Vec::new();std::fs::File::open(&path)?.take(32769).read_to_end(&mut bytes)?;
                    if bytes.len()>32768 {return Err("journal size".into());}
                    let rows:Vec<Event>=serde_json::from_slice(&bytes)?;
                    if rows.len()>64 || rows.iter().any(|e| !valid_stage(&e.stage) || e.task.len()!=12 || !e.task.bytes().all(|b|b.is_ascii_hexdigit())) {return Err("journal entries".into());}
                    rows
                }
                Err(error) if error.kind()==std::io::ErrorKind::NotFound=>Vec::new(),
                Err(error)=>return Err(error.into()),
            };
            events.push(Event {at_ms:SystemTime::now().duration_since(UNIX_EPOCH)?.as_millis(),task:self.task.clone(),stage:stage.into(),age_ms:self.started.elapsed().as_millis()});
            if events.len()>64 {events.drain(..events.len()-64);}
            let bytes=serde_json::to_vec(&events)?;if bytes.len()>32768 {return Err("journal serialized size".into());}
            // A new file inherits this same private root's ACL. Never reuse an unknown
            // pending slot; a stale slot pauses diagnostics instead of accumulating files.
            // Close our handle before Windows replacement semantics apply.
            let pending=self.root.join("generation-continuity-v1.pending");
            let mut file=std::fs::OpenOptions::new().write(true).create_new(true).open(&pending)?;
            if let Err(error)=file.write_all(&bytes) {drop(file);let _=std::fs::remove_file(&pending);return Err(error.into());}
            drop(file);
            if let Err(error)=std::fs::rename(&pending,&path) {let _=std::fs::remove_file(&pending);return Err(error.into());}
            Ok(())
        })();
    }
    pub fn stage(&self, stage:&str) {self.record(stage);}
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
    #[test]
    fn unavailable_diagnostic_target_does_not_mutate_business_data() {
        let root=tempfile::tempdir().unwrap();let target=root.path().join("generation-continuity-v1.json");std::fs::create_dir(&target).unwrap();
        let marker=root.path().join("business.txt");std::fs::write(&marker,b"unchanged").unwrap();
        let mut trace=Trace::begin(root.path(),"synthetic-task");trace.progress();trace.finish("COMPLETED");
        assert!(target.is_dir());assert_eq!(std::fs::read(marker).unwrap(),b"unchanged");
    }
    #[test]
    fn unknown_existing_payload_and_invalid_stage_are_not_rewritten_or_retained() {
        let root=tempfile::tempdir().unwrap();let target=root.path().join("generation-continuity-v1.json");
        let original=b"unknown existing input";std::fs::write(&target,original).unwrap();{let mut trace=Trace::begin(root.path(),"secret-task-name");trace.finish("COMPLETED");}
        assert_eq!(std::fs::read(&target).unwrap(),original);
        std::fs::remove_file(&target).unwrap();{let mut trace=Trace::begin(root.path(),"secret-task-name");trace.finish("private body: secret");}
        let bytes=std::fs::read(&target).unwrap();assert!(!String::from_utf8(bytes).unwrap().contains("secret"));
    }

    #[test]
    fn concurrent_replacement_and_unwind_keep_bounded_owned_records() {
        let root=tempfile::tempdir().unwrap();let mut workers=Vec::new();
        for index in 0..4 {let path=root.path().to_owned();workers.push(std::thread::spawn(move||{for _ in 0..10 {let mut trace=Trace::begin(&path,&format!("synthetic-{index}"));trace.progress();trace.finish("COMPLETED");}}));}
        for worker in workers {worker.join().unwrap();}
        let path=root.path().to_owned();assert!(std::panic::catch_unwind(move||{let _trace=Trace::begin(&path,"synthetic-unwind");panic!("synthetic owner interruption");}).is_err());
        let bytes=std::fs::read(root.path().join("generation-continuity-v1.json")).unwrap();assert!(bytes.len()<=32768);
        let events:Vec<Event>=serde_json::from_slice(&bytes).unwrap();assert_eq!(events.len(),64);assert_eq!(events.last().unwrap().stage,"OWNER_EXIT_UNSETTLED");
        assert!(std::fs::read_dir(root.path()).unwrap().all(|entry|!entry.unwrap().file_name().to_string_lossy().ends_with(".pending")));
    }

    #[test]
    fn stale_pending_slot_is_preserved_without_creating_more_files() {
        let root=tempfile::tempdir().unwrap();let pending=root.path().join("generation-continuity-v1.pending");std::fs::write(&pending,b"unknown interrupted input").unwrap();
        for _ in 0..70 {let mut trace=Trace::begin(root.path(),"synthetic-task");trace.progress();trace.finish("COMPLETED");}
        assert_eq!(std::fs::read(&pending).unwrap(),b"unknown interrupted input");assert_eq!(std::fs::read_dir(root.path()).unwrap().count(),1);
    }

}
