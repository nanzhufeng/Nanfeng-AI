//! Send boundary owns its SQLite connection; no store mutex spans DNS/HTTP or joins.
use crate::desktop_web_knowledge_tasks_v1::{self as owner, Task};
use crate::desktop_web_text_snapshot_v1::Transport;
use rusqlite::Connection;
use std::{
    path::PathBuf,
    sync::{
        atomic::{AtomicBool, Ordering},
        Arc,
    },
};

struct CancelOnDrop(Arc<AtomicBool>);
impl Drop for CancelOnDrop {
    fn drop(&mut self) {
        self.0.store(true, Ordering::SeqCst);
    }
}
pub(crate) async fn run_registered(
    c: Connection,
    root: PathBuf,
    workspace: String,
    id: String,
    raw_url: String,
    retry: bool,
    registration: crate::desktop_web_jobs_v1::Registration,
) -> Result<Task, String> {
    let cancel = registration.signal.clone();
    let transport_root = root.clone();
    run_with(
        c,
        root,
        workspace,
        id,
        raw_url,
        retry,
        cancel,
        Some(registration),
        move || crate::desktop_web_text_transport_v1::HttpsTransport::new_for_root(&transport_root),
    )
    .await
}
async fn run_with<T: Transport + Send + 'static>(
    mut c: Connection,
    root: PathBuf,
    workspace: String,
    id: String,
    raw_url: String,
    retry: bool,
    cancel: Arc<AtomicBool>,
    registration: Option<crate::desktop_web_jobs_v1::Registration>,
    factory: impl FnOnce() -> Result<T, &'static str> + Send + 'static,
) -> Result<Task, String> {
    let _cancel_on_drop = CancelOnDrop(cancel.clone());
    tokio::task::spawn_blocking(move || {
        let _registration = registration;
        let mut transport = factory().map_err(str::to_owned)?;
        let runtime = tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
            .map_err(|_| "WEB_WORKER_RUNTIME")?;
        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map_err(|_| "WEB_WORKER_CLOCK")?
            .as_millis()
            .min(i64::MAX as u128) as i64;
        runtime.block_on(owner::process_with_cancel(
            &mut c,
            &root,
            &workspace,
            &id,
            &raw_url,
            now,
            retry,
            &mut transport,
            || cancel.load(Ordering::SeqCst),
        ))
        // runtime drop waits for owned DNS helper cleanup before this worker returns.
    })
    .await
    .map_err(|_| "WEB_WORKER_JOIN".to_owned())?
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::desktop_web_text_snapshot_v1::{Operation, Response, SafeUrl};
    use std::time::Duration;
    struct Fake {
        delay: Duration,
        calls: Arc<std::sync::atomic::AtomicUsize>,
    }
    impl Transport for Fake {
        fn resolve<'a>(&'a mut self, _: &'a str) -> Operation<'a, Vec<std::net::IpAddr>> {
            self.calls.fetch_add(1, Ordering::SeqCst);
            Box::pin(async { Ok(vec!["8.8.8.8".parse().unwrap()]) })
        }
        fn request<'a>(
            &'a mut self,
            _: &'a SafeUrl,
            _: &'a [std::net::IpAddr],
        ) -> Operation<'a, Response> {
            self.calls.fetch_add(1, Ordering::SeqCst);
            Box::pin(async move {
                tokio::time::sleep(self.delay).await;
                Ok(Response {
                    status: 200,
                    headers: vec![("content-type".into(), "text/html".into())],
                    body:
                        b"<!doctype html><title>synthetic</title><p>background pending content</p>"
                            .to_vec(),
                })
            })
        }
    }
    fn fixture() -> (tempfile::TempDir, Connection, String, PathBuf) {
        let dir = tempfile::tempdir().unwrap();
        let db = dir.path().join("synthetic.sqlite3");
        let c = Connection::open(&db).unwrap();
        c.execute_batch("PRAGMA journal_mode=WAL; CREATE TABLE workspaces(id TEXT PRIMARY KEY);INSERT INTO workspaces VALUES('synthetic');").unwrap();
        owner::create(&c).unwrap();
        let task = owner::queue(&c, "synthetic", "https://example.org/?ordinary=value", 1).unwrap();
        (dir, c, task.id, db)
    }
    #[test]
    fn background_owns_connection_and_publishes_pending_only() {
        let (dir, c, id, db) = fixture();
        let root = dir.path().to_path_buf();
        let calls = Arc::new(std::sync::atomic::AtomicUsize::new(0));
        let count = calls.clone();
        let rt = tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
            .unwrap();
        let result = rt
            .block_on(run_with(
                c,
                root,
                "synthetic".into(),
                id.clone(),
                "https://example.org/?ordinary=value".into(),
                false,
                Arc::new(AtomicBool::new(false)),
                None,
                move || {
                    Ok(Fake {
                        delay: Duration::ZERO,
                        calls: count,
                    })
                },
            ))
            .unwrap();
        assert_eq!(result.status, "AWAITING_CONFIRMATION");
        assert_eq!(calls.load(Ordering::SeqCst), 2);
        assert_eq!(result.items.len(), 1);
        let c = Connection::open(db).unwrap();
        assert_eq!(
            owner::read(&c, "synthetic", &id).unwrap().status,
            result.status
        );
    }
    #[test]
    fn background_waiter_abort_cancels_without_late_candidates_and_releases_connection() {
        let (dir, c, id, db) = fixture();
        let root = dir.path().to_path_buf();
        let calls = Arc::new(std::sync::atomic::AtomicUsize::new(0));
        let count = calls.clone();
        let jobs = Arc::new(crate::desktop_web_jobs_v1::Jobs::default());
        let registration = jobs.begin("synthetic", &id).unwrap();
        let cancel = registration.signal.clone();
        let flag = cancel.clone();
        let observer = Connection::open(&db).unwrap();
        let rt = tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
            .unwrap();
        rt.block_on(async {
            let task = tokio::spawn(run_with(
                c,
                root,
                "synthetic".into(),
                id.clone(),
                "https://example.org/?ordinary=value".into(),
                false,
                flag,
                Some(registration),
                move || {
                    Ok(Fake {
                        delay: Duration::from_secs(60),
                        calls: count,
                    })
                },
            ));
            tokio::time::timeout(Duration::from_secs(3), async {
                while calls.load(Ordering::SeqCst) < 2 {
                    tokio::time::sleep(Duration::from_millis(10)).await;
                }
            })
            .await
            .unwrap();
            assert_eq!(
                owner::read(&observer, "synthetic", &id).unwrap().status,
                "FETCHING"
            );
            task.abort();
            assert!(task.await.unwrap_err().is_cancelled());
            tokio::time::timeout(Duration::from_secs(2), async {
                while owner::read(&observer, "synthetic", &id).unwrap().status != "CANCELLED" {
                    tokio::time::sleep(Duration::from_millis(10)).await;
                }
            })
            .await
            .unwrap();
        });
        assert!(!jobs.active());
        assert!(cancel.load(Ordering::SeqCst));
        assert!(owner::read(&observer, "synthetic", &id)
            .unwrap()
            .items
            .is_empty());
        assert_eq!(
            observer
                .query_row(
                    "SELECT count(*) FROM desktop_web_knowledge_assets_v1",
                    [],
                    |r| r.get::<_, i64>(0)
                )
                .unwrap(),
            0
        );
        observer.execute_batch("BEGIN EXCLUSIVE;ROLLBACK").unwrap();
    }
    #[test]
    fn background_pre_cancel_never_dispatches_dns_or_http() {
        let (dir, c, id, _) = fixture();
        let root = dir.path().to_path_buf();
        let calls = Arc::new(std::sync::atomic::AtomicUsize::new(0));
        let count = calls.clone();
        let rt = tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
            .unwrap();
        let result = rt
            .block_on(run_with(
                c,
                root,
                "synthetic".into(),
                id,
                "https://example.org/?ordinary=value".into(),
                false,
                Arc::new(AtomicBool::new(true)),
                None,
                move || {
                    Ok(Fake {
                        delay: Duration::ZERO,
                        calls: count,
                    })
                },
            ))
            .unwrap();
        assert_eq!(result.status, "CANCELLED");
        assert_eq!(calls.load(Ordering::SeqCst), 0);
    }
}
