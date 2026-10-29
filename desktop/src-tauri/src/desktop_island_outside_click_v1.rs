//! Mouse-down only; always pass input through. No keyboard, body, credential or coordinate logs.
use std::sync::{
    atomic::{AtomicBool, AtomicIsize, AtomicU64, AtomicU8, Ordering},
    Arc, Mutex, OnceLock,
};
pub(crate) const INACTIVE: u8 = 0;
pub(crate) const STARTING: u8 = 1;
pub(crate) const ACTIVE: u8 = 2;
pub(crate) const UNAVAILABLE: u8 = 3;
#[derive(Default)]
struct Published {
    enabled: bool,
    window: isize,
    epoch: u64,
}
#[derive(Default)]
pub struct Target {
    published: Mutex<Published>,
    enabled: AtomicBool,
    window: AtomicIsize,
    epoch: AtomicU64,
    pending: AtomicU64,
    status: AtomicU8,
}
impl Target {
    pub fn publish(&self, enabled: bool, window: isize) {
        let enabled = enabled && window != 0;
        let window = if enabled { window } else { 0 };
        let mut p = self.published.lock().unwrap_or_else(|e| e.into_inner());
        if p.enabled == enabled && p.window == window {
            return;
        }
        self.enabled.store(false, Ordering::SeqCst);
        p.epoch = p.epoch.wrapping_add(1).max(1);
        p.enabled = enabled;
        p.window = window;
        self.pending.store(0, Ordering::SeqCst);
        self.window.store(window, Ordering::SeqCst);
        self.epoch.store(p.epoch, Ordering::SeqCst);
        self.enabled.store(enabled, Ordering::SeqCst);
    }
    pub fn enabled(&self) -> bool {
        self.enabled.load(Ordering::SeqCst)
    }
    pub fn take_collapse(&self) -> bool {
        let p = self.pending.swap(0, Ordering::SeqCst);
        if p != 0 && self.enabled() && p == self.epoch.load(Ordering::SeqCst) {
            self.publish(false, 0);
            true
        } else {
            false
        }
    }
    pub fn status(&self) -> &'static str {
        if !self.enabled() {
            return "inactive";
        }
        match self.status.load(Ordering::SeqCst) {
            ACTIVE => "active",
            UNAVAILABLE => "unavailable",
            _ => "starting",
        }
    }
    fn observe(&self, code: i32, message: u32, hit: impl FnOnce() -> Option<isize>) {
        if code < 0 || !matches!(message, 0x0201 | 0x0204 | 0x0207 | 0x020b) || !self.enabled() {
            return;
        }
        let epoch = self.epoch.load(Ordering::SeqCst);
        let own = self.window.load(Ordering::SeqCst);
        if hit().is_some_and(|h| h != 0 && h != own)
            && self.enabled()
            && epoch == self.epoch.load(Ordering::SeqCst)
            && own == self.window.load(Ordering::SeqCst)
        {
            self.pending.store(epoch, Ordering::SeqCst);
        }
    }
}
pub fn target() -> Arc<Target> {
    static TARGET: OnceLock<Arc<Target>> = OnceLock::new();
    TARGET.get_or_init(|| Arc::new(Target::default())).clone()
}
pub(crate) fn dispatch(
    t: &Target,
    code: i32,
    message: u32,
    hit: impl FnOnce() -> Option<isize>,
    next: impl FnOnce() -> isize,
) -> isize {
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        t.observe(code, message, hit)
    }));
    next()
}
pub trait WorkerStatus {
    fn status(&self) -> u8;
}
pub struct Controller<W: WorkerStatus, F: Fn(Arc<Target>) -> Result<W, ()>> {
    target: Arc<Target>,
    worker: Option<W>,
    failed: bool,
    attempted_epoch: u64,
    start: F,
}
impl<W: WorkerStatus, F: Fn(Arc<Target>) -> Result<W, ()>> Controller<W, F> {
    pub fn new(target: Arc<Target>, start: F) -> Self {
        Self {
            target,
            worker: None,
            failed: false,
            attempted_epoch: 0,
            start,
        }
    }
    pub fn reconcile(&mut self) {
        if !self.target.enabled() {
            drop(self.worker.take());
            self.failed = false;
            self.target.status.store(INACTIVE, Ordering::SeqCst);
            return;
        }
        // A fast collapse/re-expand may occur between two monitor ticks.
        // Retry the new presentation even when the monitor missed its disabled frame.
        let epoch = self.target.epoch.load(Ordering::SeqCst);
        if self.failed && self.attempted_epoch != epoch {
            self.failed = false;
        }
        if self.worker.is_none() && !self.failed {
            self.attempted_epoch = epoch;
            self.target.status.store(STARTING, Ordering::SeqCst);
            match (self.start)(self.target.clone()) {
                Ok(w) => self.worker = Some(w),
                Err(()) => self.failed = true,
            }
        }
        if let Some(w) = &self.worker {
            let status = w.status();
            if status == UNAVAILABLE || status == INACTIVE {
                drop(self.worker.take());
                self.failed = true;
            } else {
                self.target.status.store(status, Ordering::SeqCst);
            }
        }
        if self.failed {
            self.target.status.store(UNAVAILABLE, Ordering::SeqCst);
        }
    }
}
impl<W: WorkerStatus, F: Fn(Arc<Target>) -> Result<W, ()>> Drop for Controller<W, F> {
    fn drop(&mut self) {
        self.target.publish(false, 0);
        drop(self.worker.take());
        self.target.status.store(INACTIVE, Ordering::SeqCst);
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn reexpand_between_monitor_ticks_retries_one_failed_registration() {
        let target = Arc::new(Target::default());
        let attempts = AtomicU64::new(0);
        let mut controller = Controller::<Fake, _>::new(target.clone(), |_| {
            attempts.fetch_add(1, Ordering::SeqCst);
            Err(())
        });
        target.publish(true, 10);
        controller.reconcile();
        controller.reconcile();
        assert_eq!(attempts.load(Ordering::SeqCst), 1);
        target.publish(false, 0);
        target.publish(true, 10);
        controller.reconcile();
        controller.reconcile();
        assert_eq!(attempts.load(Ordering::SeqCst), 2);
    }
    #[test]
    fn hidden_collapsed_motion_release_and_negative_code_do_not_read_pointer_data() {
        let t = Target::default();
        assert_eq!(dispatch(&t, 0, 0x201, || panic!("hidden"), || 37), 37);
        t.publish(true, 10);
        for (code, msg) in [(-1, 0x201), (0, 0x200), (0, 0x202), (0, 0x20a)] {
            assert_eq!(
                dispatch(&t, code, msg, || panic!("irrelevant"), || -17),
                -17
            );
        }
        assert!(!t.take_collapse());
    }
    #[test]
    fn outside_down_only_and_next_result_is_unchanged() {
        let t = Target::default();
        t.publish(true, 10);
        dispatch(&t, 0, 0x201, || Some(10), || 0);
        assert!(!t.take_collapse());
        dispatch(&t, 0, 0x201, || None, || 0);
        assert!(!t.take_collapse());
        for msg in [0x201, 0x204, 0x207, 0x20b] {
            t.publish(true, 10);
            assert_eq!(dispatch(&t, 0, msg, || Some(20), || -23), -23);
            assert!(t.take_collapse());
            assert!(!t.enabled());
        }
    }
    #[test]
    fn old_click_cannot_collapse_reexpanded_or_replaced_view() {
        let t = Target::default();
        t.publish(true, 10);
        dispatch(&t, 0, 0x201, || Some(20), || 0);
        t.publish(false, 0);
        t.publish(true, 10);
        assert!(!t.take_collapse());
        dispatch(&t, 0, 0x201, || Some(20), || 0);
        t.publish(true, 11);
        assert!(!t.take_collapse());
    }
    #[test]
    fn observation_failure_cannot_swallow_input() {
        let t = Target::default();
        t.publish(true, 10);
        let next = AtomicBool::new(false);
        assert_eq!(
            dispatch(
                &t,
                0,
                0x201,
                || panic!("synthetic hit failure"),
                || {
                    next.store(true, Ordering::SeqCst);
                    77
                }
            ),
            77
        );
        assert!(next.load(Ordering::SeqCst));
        assert!(!t.take_collapse());
    }
    #[test]
    fn unchanged_publish_keeps_pending_click() {
        let t = Target::default();
        t.publish(true, 10);
        dispatch(&t, 0, 0x201, || Some(20), || 0);
        t.publish(true, 10);
        assert!(t.take_collapse());
    }
    #[test]
    fn target_changed_during_hit_test_cannot_queue_an_old_click() {
        let t = Target::default();
        t.publish(true, 10);
        assert_eq!(
            dispatch(
                &t,
                0,
                0x201,
                || {
                    t.publish(false, 0);
                    t.publish(true, 11);
                    Some(20)
                },
                || 19
            ),
            19
        );
        assert!(!t.take_collapse());
        assert!(t.enabled());
    }
    struct Fake {
        state: Arc<AtomicU8>,
        drops: Arc<AtomicU64>,
    }
    impl WorkerStatus for Fake {
        fn status(&self) -> u8 {
            self.state.load(Ordering::SeqCst)
        }
    }
    impl Drop for Fake {
        fn drop(&mut self) {
            self.drops.fetch_add(1, Ordering::SeqCst);
        }
    }
    #[test]
    fn lazy_single_registration_and_failure_waits_for_reexpand() {
        let t = Arc::new(Target::default());
        let starts = Arc::new(AtomicU64::new(0));
        let drops = Arc::new(AtomicU64::new(0));
        let state = Arc::new(AtomicU8::new(ACTIVE));
        let (s, d, st) = (starts.clone(), drops.clone(), state.clone());
        let mut c = Controller::new(t.clone(), move |_| {
            s.fetch_add(1, Ordering::SeqCst);
            Ok(Fake {
                state: st.clone(),
                drops: d.clone(),
            })
        });
        c.reconcile();
        assert_eq!(starts.load(Ordering::SeqCst), 0);
        t.publish(true, 10);
        c.reconcile();
        c.reconcile();
        assert_eq!(starts.load(Ordering::SeqCst), 1);
        state.store(UNAVAILABLE, Ordering::SeqCst);
        c.reconcile();
        c.reconcile();
        assert_eq!(starts.load(Ordering::SeqCst), 1);
        assert_eq!(drops.load(Ordering::SeqCst), 1);
        assert_eq!(t.status(), "unavailable");
        t.publish(false, 0);
        c.reconcile();
        state.store(ACTIVE, Ordering::SeqCst);
        t.publish(true, 10);
        c.reconcile();
        assert_eq!(starts.load(Ordering::SeqCst), 2);
        drop(c);
        assert_eq!(drops.load(Ordering::SeqCst), 2);
        assert_eq!(t.status(), "inactive");
    }
    #[test]
    fn creation_failure_does_not_busy_retry() {
        let t = Arc::new(Target::default());
        let count = AtomicU64::new(0);
        let mut c = Controller::<Fake, _>::new(t.clone(), |_| {
            count.fetch_add(1, Ordering::SeqCst);
            Err(())
        });
        t.publish(true, 10);
        for _ in 0..50 {
            c.reconcile();
        }
        assert_eq!(count.load(Ordering::SeqCst), 1);
        assert_eq!(t.status(), "unavailable");
    }
}
