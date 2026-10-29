//! Reader requests owned by the main WebView. IDs are native allocated;
//! cancellation never waits for the workspace/render lock.
use std::sync::{
    atomic::{AtomicBool, Ordering},
    Arc,
};

struct Active {
    id: u64,
    started: bool,
    cancelled: Arc<AtomicBool>,
}

#[derive(Default)]
pub(crate) struct Requests {
    next: u64,
    active: Option<Active>,
}

impl Requests {
    pub fn begin(&mut self) -> Result<u64, String> {
        let id = self
            .next
            .checked_add(1)
            .filter(|id| *id <= 9_007_199_254_740_991)
            .ok_or("预览请求编号已耗尽，请重启应用")?;
        self.next = id;
        if let Some(previous) = self.active.take() {
            previous.cancelled.store(true, Ordering::SeqCst);
        }
        self.active = Some(Active {
            id,
            started: false,
            cancelled: Arc::new(AtomicBool::new(false)),
        });
        Ok(id)
    }

    pub fn start(&mut self, id: u64) -> Result<Arc<AtomicBool>, String> {
        let active = self
            .active
            .as_mut()
            .filter(|active| active.id == id && !active.started)
            .ok_or("预览请求已取消或已被读取")?;
        active.started = true;
        Ok(Arc::clone(&active.cancelled))
    }

    pub fn cancel(&mut self, id: u64) {
        if self.active.as_ref().is_some_and(|active| active.id == id) {
            if let Some(active) = self.active.take() {
                active.cancelled.store(true, Ordering::SeqCst);
            }
        }
    }

    pub fn finish(&mut self, id: u64) {
        if self.active.as_ref().is_some_and(|active| active.id == id) {
            self.active = None;
        }
    }
}

struct BackgroundRead {
    started: bool,
    created: std::time::Instant,
    cancelled: Arc<AtomicBool>,
}

/// Reservations are bounded, and cancelled running jobs retain their record
/// until the actual worker finishes. Physical concurrency is separately gated
/// by the native semaphore, including when a WebView reload loses JS state.
#[derive(Default)]
pub(crate) struct ConcurrentRequests {
    next: u64,
    reads: std::collections::BTreeMap<u64, BackgroundRead>,
}
impl ConcurrentRequests {
    pub fn begin(&mut self) -> Result<u64, String> {
        self.begin_at(std::time::Instant::now())
    }
    fn begin_at(&mut self, now: std::time::Instant) -> Result<u64, String> {
        self.reads.retain(|_, read| {
            read.started
                || now.saturating_duration_since(read.created) < std::time::Duration::from_secs(30)
        });
        if self.reads.len() >= 32 {
            return Err("后台预览繁忙，请稍后重试".into());
        }
        let id = self
            .next
            .checked_add(1)
            .filter(|id| *id <= 9_007_199_254_740_991)
            .ok_or("预览请求编号已耗尽，请重启应用")?;
        self.next = id;
        self.reads.insert(
            id,
            BackgroundRead {
                started: false,
                created: now,
                cancelled: Arc::new(AtomicBool::new(false)),
            },
        );
        Ok(id)
    }
    pub fn start(&mut self, id: u64) -> Result<Arc<AtomicBool>, String> {
        self.start_at(id, std::time::Instant::now())
    }
    fn start_at(&mut self, id: u64, now: std::time::Instant) -> Result<Arc<AtomicBool>, String> {
        if self.reads.get(&id).is_some_and(|read| {
            !read.started
                && now.saturating_duration_since(read.created) >= std::time::Duration::from_secs(30)
        }) {
            self.reads.remove(&id);
            return Err("预览请求已过期".into());
        }
        let read = self
            .reads
            .get_mut(&id)
            .filter(|r| !r.started && !r.cancelled.load(Ordering::SeqCst))
            .ok_or("预览请求已取消或已被读取")?;
        read.started = true;
        Ok(Arc::clone(&read.cancelled))
    }
    pub fn cancel(&mut self, id: u64) {
        if let Some(read) = self.reads.get(&id) {
            read.cancelled.store(true, Ordering::SeqCst);
            if !read.started {
                self.reads.remove(&id);
            }
        }
    }
    pub fn finish(&mut self, id: u64) {
        self.reads.remove(&id);
    }
}

pub(crate) struct ReadCompletion {
    requests: Arc<std::sync::Mutex<ConcurrentRequests>>,
    id: u64,
}
impl ReadCompletion {
    pub fn new(requests: Arc<std::sync::Mutex<ConcurrentRequests>>, id: u64) -> Self {
        Self { requests, id }
    }
}
impl Drop for ReadCompletion {
    fn drop(&mut self) {
        if let Ok(mut requests) = self.requests.lock() {
            requests.finish(self.id);
        }
    }
}

pub(crate) async fn acquire_read_slot(
    slots: Arc<tokio::sync::Semaphore>,
    cancelled: &AtomicBool,
) -> Result<tokio::sync::OwnedSemaphorePermit, String> {
    let waiting = slots.acquire_owned();
    tokio::pin!(waiting);
    loop {
        if cancelled.load(Ordering::SeqCst) {
            return Err("视频缩略图已取消".into());
        }
        tokio::select! {
            permit=&mut waiting => return permit.map_err(|_|"后台预览已关闭".into()),
            _=tokio::time::sleep(std::time::Duration::from_millis(5))=>{},
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn native_slots_bound_workers_and_cancelled_waiter_never_takes_capacity() {
        let slots = Arc::new(tokio::sync::Semaphore::new(2));
        let no = AtomicBool::new(false);
        let a = acquire_read_slot(Arc::clone(&slots), &no).await.unwrap();
        let b = acquire_read_slot(Arc::clone(&slots), &no).await.unwrap();
        let cancellation = Arc::new(AtomicBool::new(false));
        let token = Arc::clone(&cancellation);
        let waiting_slots = Arc::clone(&slots);
        let waiting = tokio::spawn(async move { acquire_read_slot(waiting_slots, &token).await });
        tokio::time::sleep(std::time::Duration::from_millis(15)).await;
        assert!(!waiting.is_finished());
        cancellation.store(true, Ordering::SeqCst);
        assert!(waiting.await.unwrap().is_err());
        assert_eq!(slots.available_permits(), 0);
        drop(a);
        assert_eq!(slots.available_permits(), 1);
        drop(b);
        assert_eq!(slots.available_permits(), 2);
    }

    #[test]
    fn concurrent_cancel_is_exact_and_running_records_wait_for_worker_completion() {
        let mut reads = ConcurrentRequests::default();
        let a = reads.begin().unwrap();
        let b = reads.begin().unwrap();
        let token_a = reads.start(a).unwrap();
        let token_b = reads.start(b).unwrap();
        assert!(reads.start(a).is_err());
        reads.cancel(a);
        assert!(token_a.load(Ordering::SeqCst));
        assert!(!token_b.load(Ordering::SeqCst));
        assert!(reads.reads.contains_key(&a));
        reads.finish(a);
        assert!(!reads.reads.contains_key(&a));
        reads.cancel(a);
        assert!(!token_b.load(Ordering::SeqCst));
        reads.finish(b);
        assert!(reads.reads.is_empty());
    }
    #[test]
    fn concurrent_abandoned_reservations_expire_but_running_reads_do_not() {
        let mut reads = ConcurrentRequests::default();
        let now = std::time::Instant::now();
        let running = reads.begin_at(now).unwrap();
        reads.start(running).unwrap();
        for _ in 1..32 {
            reads.begin_at(now).unwrap();
        }
        assert!(reads.begin_at(now).is_err());
        let later = reads
            .begin_at(now + std::time::Duration::from_secs(31))
            .unwrap();
        assert_eq!(reads.reads.len(), 2);
        assert!(reads.reads.contains_key(&running));
        reads.cancel(later);
        assert!(reads.start(later).is_err());
        assert_eq!(reads.reads.len(), 1);

        let stale = reads
            .begin_at(now + std::time::Duration::from_secs(31))
            .unwrap();
        assert!(reads
            .start_at(stale, now + std::time::Duration::from_secs(62))
            .unwrap_err()
            .contains("过期"));
        assert!(!reads.reads.contains_key(&stale));
    }

    #[test]
    fn cancelled_reservation_never_starts_and_cannot_cancel_new_request() {
        let mut requests = Requests::default();
        let old = requests.begin().unwrap();
        requests.cancel(old);
        assert!(requests.start(old).is_err());
        let current = requests.begin().unwrap();
        requests.cancel(old);
        let token = requests.start(current).unwrap();
        requests.finish(old);
        assert!(!token.load(Ordering::SeqCst));
        requests.cancel(current);
        assert!(token.load(Ordering::SeqCst));
    }

    #[test]
    fn replacing_a_running_read_cancels_it_and_duplicate_reads_are_rejected() {
        let mut requests = Requests::default();
        let first = requests.begin().unwrap();
        let first_token = requests.start(first).unwrap();
        assert!(requests.start(first).is_err());
        let second = requests.begin().unwrap();
        assert!(first_token.load(Ordering::SeqCst));
        let second_token = requests.start(second).unwrap();
        requests.finish(first);
        requests.cancel(second);
        assert!(second_token.load(Ordering::SeqCst));
    }

    #[test]
    fn abandoned_reservations_are_bounded_and_completion_releases_current_request() {
        let mut requests = Requests::default();
        for _ in 0..1000 {
            requests.begin().unwrap();
        }
        assert!(requests.start(999).is_err());
        assert!(requests.start(1000).is_ok());
        requests.finish(1000);
        assert!(requests.active.is_none());
        assert!(requests.start(1000).is_err());
    }
}
