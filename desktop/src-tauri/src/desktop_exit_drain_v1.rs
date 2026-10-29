//! Process-local window/exit ownership. No task data, retry, or persistence.
use std::sync::Mutex;

pub const EXIT_CODE: i32 = -20038;
pub const CANCEL_AFTER:std::time::Duration=std::time::Duration::from_secs(30*60);
pub const FORCE_AFTER:std::time::Duration=std::time::Duration::from_secs(31*60);
#[derive(Default)]
struct State {
    epoch: u64,
    active: Option<u64>,
    requested: Option<(u64, bool)>,
    accepted: Option<u64>,
}
pub struct ExitDrain(Mutex<State>);
pub struct Lease<'a> {owner:&'a ExitDrain,epoch:u64}
impl Drop for Lease<'_> {fn drop(&mut self) {self.owner.finish(self.epoch);}}
impl ExitDrain {
    pub const fn new() -> Self {
        Self(Mutex::new(State { epoch: 0, active: None, requested: None, accepted: None }))
    }
    pub fn lease(&self,epoch:u64)->Lease<'_> {Lease{owner:self,epoch}}
    pub fn begin(&self) -> Option<u64> {
        let mut s=self.0.lock().unwrap_or_else(|e|e.into_inner());
        if s.active.is_some() {return None;}
        s.epoch=s.epoch.wrapping_add(1).max(1);s.active=Some(s.epoch);s.requested=None;s.accepted=None;
        Some(s.epoch)
    }
    pub fn reopen(&self) {
        let mut s=self.0.lock().unwrap_or_else(|e|e.into_inner());
        s.epoch=s.epoch.wrapping_add(1).max(1);s.active=None;s.requested=None;s.accepted=None;
    }
    pub fn owns(&self, epoch:u64)->bool {
        self.0.lock().unwrap_or_else(|e|e.into_inner()).active==Some(epoch)
    }
    pub fn with_owner(&self, epoch:u64, action:impl FnOnce())->bool {
        let s=self.0.lock().unwrap_or_else(|e|e.into_inner());
        if s.active!=Some(epoch) {return false;}
        action();true
    }
    pub fn request_exit(&self, epoch:u64, forced:bool)->bool {
        let mut s=self.0.lock().unwrap_or_else(|e|e.into_inner());
        if s.active!=Some(epoch)||s.requested.is_some() {return false;}
        s.requested=Some((epoch,forced));true
    }
    /// Called again on the native event loop, after an exit event was queued.
    pub fn accept_requested_exit(&self, business_active:bool)->bool {
        let mut s=self.0.lock().unwrap_or_else(|e|e.into_inner());
        let Some((epoch,forced))=s.requested.take() else {return false;};
        if s.active!=Some(epoch)||(!forced&&business_active) {return false;}
        s.active=None;s.accepted=Some(epoch);true
    }
    pub fn was_accepted(&self, epoch:u64)->bool {
        self.0.lock().unwrap_or_else(|e|e.into_inner()).accepted==Some(epoch)
    }
    pub fn finish(&self, epoch:u64) {
        let mut s=self.0.lock().unwrap_or_else(|e|e.into_inner());
        if s.active==Some(epoch) {s.active=None;s.requested=None;}
    }
}
pub fn process_exit_code(code:i32, background:bool)->i32 {
    if !background&&code==EXIT_CODE {0} else {code}
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test] fn queued_old_exit_and_cancellation_are_revoked_on_reopen() {
        let owner=ExitDrain::new();let old=owner.begin().unwrap();assert!(owner.request_exit(old,false));
        owner.reopen();assert!(!owner.accept_requested_exit(false));
        assert!(!owner.with_owner(old,||panic!("stale cancellation")));
        let new=owner.begin().unwrap();owner.finish(old);assert!(owner.owns(new));
        assert!(owner.request_exit(new,false));assert!(owner.accept_requested_exit(false));assert!(owner.was_accepted(new));
    }
    #[test] fn newly_registered_work_is_rechecked_before_exit_and_force_is_explicit() {
        let owner=ExitDrain::new();let epoch=owner.begin().unwrap();assert!(owner.begin().is_none());
        assert!(owner.request_exit(epoch,false));assert!(!owner.accept_requested_exit(true));assert!(owner.owns(epoch));
        assert!(owner.request_exit(epoch,true));assert!(owner.accept_requested_exit(true));
    }
    #[test] fn old_forced_request_cannot_apply_to_the_next_close() {
        let owner=ExitDrain::new();let old=owner.begin().unwrap();assert!(owner.request_exit(old,true));owner.reopen();
        let new=owner.begin().unwrap();assert!(!owner.accept_requested_exit(false));assert!(owner.owns(new));
        assert!(!owner.request_exit(old,true));assert!(owner.request_exit(new,false));assert!(!owner.accept_requested_exit(true));
    }
    #[test] fn background_exit_codes_and_explicit_failures_are_preserved() {
        assert_eq!(process_exit_code(EXIT_CODE,false),0);assert_eq!(process_exit_code(EXIT_CODE,true),EXIT_CODE);
        assert_eq!(process_exit_code(1,true),1);assert_eq!(process_exit_code(1,false),1);
    }
    #[test] fn unwind_and_old_lease_drop_do_not_leave_or_clear_another_drain() {
        let owner=ExitDrain::new();let old=owner.begin().unwrap();
        assert!(std::panic::catch_unwind(||{let _lease=owner.lease(old);panic!("synthetic drain unwind");}).is_err());
        let first=owner.begin().unwrap();let lease=owner.lease(first);owner.reopen();let next=owner.begin().unwrap();drop(lease);assert!(owner.owns(next));
    }

    #[test] fn existing_deadline_boundaries_are_not_shortened() {
        for (seconds,cancel,force) in [(1799,false,false),(1800,true,false),(1859,true,false),(1860,true,true)] {
            let age=std::time::Duration::from_secs(seconds);assert_eq!(age>=CANCEL_AFTER,cancel);assert_eq!(age>=FORCE_AFTER,force);
        }
    }

}
