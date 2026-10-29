//! Registrations live until worker/runtime cleanup, including a dropped IPC waiter.
use std::{
    collections::BTreeMap,
    sync::{
        atomic::{AtomicBool, Ordering},
        Arc, Mutex,
    },
};
type Key = (String, String);
#[derive(Default)]
struct State {
    closing: bool,
    jobs: BTreeMap<Key, Arc<AtomicBool>>,
}
#[derive(Default)]
pub(crate) struct Jobs(Mutex<State>);
pub(crate) struct Registration {
    owner: Arc<Jobs>,
    key: Key,
    pub signal: Arc<AtomicBool>,
}
impl Jobs {
    pub(crate) fn begin(
        self: &Arc<Self>,
        workspace: &str,
        id: &str,
    ) -> Result<Registration, String> {
        let mut state = self.0.lock().map_err(|_| "WEB_JOBS_LOCK")?;
        if state.closing {
            return Err("WEB_JOBS_CLOSING".into());
        }
        let key = (workspace.to_owned(), id.to_owned());
        if state.jobs.contains_key(&key) {
            return Err("WEB_TASK_ALREADY_RUNNING".into());
        }
        if state.jobs.len() >= 2 {
            return Err("WEB_JOBS_BUSY".into());
        }
        let signal = Arc::new(AtomicBool::new(false));
        state.jobs.insert(key.clone(), signal.clone());
        Ok(Registration {
            owner: self.clone(),
            key,
            signal,
        })
    }
    pub(crate) fn cancel(&self, workspace: &str, id: &str) -> Result<(), String> {
        let state = self.0.lock().map_err(|_| "WEB_JOBS_LOCK")?;
        if let Some(signal) = state.jobs.get(&(workspace.to_owned(), id.to_owned())) {
            signal.store(true, Ordering::SeqCst);
        }
        Ok(())
    }
    pub(crate) fn close(&self) {
        if let Ok(mut state) = self.0.lock() {
            state.closing = true;
            for signal in state.jobs.values() {
                signal.store(true, Ordering::SeqCst);
            }
        }
    }
    pub(crate) fn reopen(&self) {
        if let Ok(mut state)=self.0.lock() {state.closing=false;}
    }
    pub(crate) fn active(&self) -> bool {
        self.0.lock().map_or(true, |s| !s.jobs.is_empty())
    }
}
impl Drop for Registration {
    fn drop(&mut self) {
        if let Ok(mut state) = self.owner.0.lock() {
            if state
                .jobs
                .get(&self.key)
                .is_some_and(|s| Arc::ptr_eq(s, &self.signal))
            {
                state.jobs.remove(&self.key);
            }
        }
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn reopen_admits_new_work_without_resurrecting_cancelled_registrations() {
        let jobs=Arc::new(Jobs::default());let old=jobs.begin("w","old").unwrap();jobs.close();
        assert!(old.signal.load(Ordering::SeqCst));assert!(jobs.begin("w","new").is_err());jobs.reopen();
        assert!(old.signal.load(Ordering::SeqCst));assert!(jobs.begin("w","old").is_err());
        let new=jobs.begin("w","new").unwrap();assert!(!new.signal.load(Ordering::SeqCst));drop(old);drop(new);assert!(!jobs.active());
    }
    #[test]
    fn duplicate_and_scoped_cancel_hold_capacity_until_actual_registration_drop() {
        let jobs = Arc::new(Jobs::default());
        let one = jobs.begin("w1", "id").unwrap();
        assert!(jobs.begin("w1", "id").is_err());
        let two = jobs.begin("w2", "id").unwrap();
        assert!(jobs.begin("w3", "id").is_err());
        jobs.cancel("w1", "id").unwrap();
        assert!(one.signal.load(Ordering::SeqCst));
        assert!(!two.signal.load(Ordering::SeqCst));
        assert!(jobs.active());
        assert!(jobs.begin("w1", "id").is_err());
        drop(one);
        let fresh = jobs.begin("w1", "id").unwrap();
        assert!(!fresh.signal.load(Ordering::SeqCst));
        drop(fresh);
        drop(two);
        assert!(!jobs.active());
    }
    #[test]
    fn closing_cancels_all_and_refuses_new_dispatch_until_workers_finish() {
        let jobs = Arc::new(Jobs::default());
        let one = jobs.begin("w1", "id").unwrap();
        let two = jobs.begin("w2", "id").unwrap();
        jobs.close();
        assert!(one.signal.load(Ordering::SeqCst) && two.signal.load(Ordering::SeqCst));
        assert!(jobs.active());
        assert!(jobs.begin("w3", "id").is_err());
        drop(one);
        assert!(jobs.active());
        drop(two);
        assert!(!jobs.active());
        assert!(jobs.begin("w1", "id").is_err());
    }
}
