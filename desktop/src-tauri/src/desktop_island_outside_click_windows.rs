//! Windows mouse observer; no window activation, input injection, or keyboard subscription.
use super::desktop_island_outside_click_v1::{
    dispatch, Target, WorkerStatus, ACTIVE, INACTIVE, STARTING, UNAVAILABLE,
};
use std::sync::{
    atomic::{AtomicBool, AtomicU8, Ordering},
    Arc,
};
use windows_sys::Win32::{
    Foundation::*,
    System::{
        LibraryLoader::GetModuleHandleW,
        Threading::{CreateEventW, SetEvent, WaitForSingleObject},
    },
    UI::WindowsAndMessaging::*,
};
thread_local! {static CONTEXT:std::cell::RefCell<Option<Arc<Target>>>=const{std::cell::RefCell::new(None)};}
fn hit_root(point: POINT) -> Option<isize> {
    let hit = unsafe { WindowFromPoint(point) };
    if hit.is_null() {
        return None;
    }
    let root = unsafe { GetAncestor(hit, GA_ROOT) };
    (!root.is_null()).then_some(root as isize)
}
unsafe extern "system" fn mouse_hook(code: i32, message: WPARAM, data: LPARAM) -> LRESULT {
    CONTEXT.with(|context| {
        let context = context.borrow();
        if let Some(t) = context.as_ref() {
            dispatch(
                t,
                code,
                message as u32,
                || {
                    if data == 0 {
                        return None;
                    }
                    let point = unsafe { (*(data as *const MSLLHOOKSTRUCT)).pt };
                    hit_root(point)
                },
                || unsafe { CallNextHookEx(std::ptr::null_mut(), code, message, data) },
            )
        } else {
            unsafe { CallNextHookEx(std::ptr::null_mut(), code, message, data) }
        }
    })
}
struct Context;
impl Drop for Context {
    fn drop(&mut self) {
        CONTEXT.with(|c| *c.borrow_mut() = None);
    }
}
struct Hook {
    handle: HHOOK,
    removed: Arc<AtomicBool>,
}
impl Drop for Hook {
    fn drop(&mut self) {
        self.removed.store(
            unsafe { UnhookWindowsHookEx(self.handle) } != 0,
            Ordering::SeqCst,
        );
    }
}
struct StopEvent(isize);
impl StopEvent {
    fn handle(&self) -> HANDLE {
        self.0 as HANDLE
    }
}
impl Drop for StopEvent {
    fn drop(&mut self) {
        unsafe {
            CloseHandle(self.handle());
        }
    }
}
pub struct Worker {
    stop: Arc<AtomicBool>,
    wake: Arc<StopEvent>,
    state: Arc<AtomicU8>,
    thread: Option<std::thread::JoinHandle<()>>,
    removed: Arc<AtomicBool>,
}
impl Worker {
    pub fn start(target: Arc<Target>) -> Result<Self, ()> {
        Self::spawn(target, None)
    }
    fn spawn(target: Arc<Target>, desktop: Option<isize>) -> Result<Self, ()> {
        Self::spawn_after_registration(target, desktop, || {})
    }
    fn spawn_after_registration<T: 'static>(
        target: Arc<Target>,
        desktop: Option<isize>,
        after_registration: impl FnOnce() -> T + Send + 'static,
    ) -> Result<Self, ()> {
        let event = unsafe { CreateEventW(std::ptr::null(), 1, 0, std::ptr::null()) };
        if event.is_null() {
            return Err(());
        }
        let wake = Arc::new(StopEvent(event as isize));
        let stop = Arc::new(AtomicBool::new(false));
        let state = Arc::new(AtomicU8::new(STARTING));
        let removed = Arc::new(AtomicBool::new(false));
        let (stopped, woken, status, unhooked) =
            (stop.clone(), wake.clone(), state.clone(), removed.clone());
        let thread = std::thread::Builder::new()
            .name("nanfeng-island-mouse".into())
            .spawn(move || {
                #[cfg(test)]
                if let Some(d) = desktop {
                    if unsafe {
                        windows_sys::Win32::System::StationsAndDesktops::SetThreadDesktop(d as _)
                    } == 0
                    {
                        status.store(UNAVAILABLE, Ordering::SeqCst);
                        return;
                    }
                }
                #[cfg(not(test))]
                let _ = desktop;
                let mut msg: MSG = unsafe { std::mem::zeroed() };
                unsafe {
                    PeekMessageW(&mut msg, std::ptr::null_mut(), 0, 0, PM_NOREMOVE);
                }
                if stopped.load(Ordering::SeqCst)
                    || unsafe { WaitForSingleObject(woken.handle(), 0) } == WAIT_OBJECT_0
                {
                    status.store(INACTIVE, Ordering::SeqCst);
                    return;
                }
                CONTEXT.with(|c| *c.borrow_mut() = Some(target));
                let _context = Context;
                let hook = unsafe {
                    SetWindowsHookExW(
                        WH_MOUSE_LL,
                        Some(mouse_hook),
                        GetModuleHandleW(std::ptr::null()),
                        0,
                    )
                };
                if hook.is_null() {
                    status.store(UNAVAILABLE, Ordering::SeqCst);
                    return;
                }
                let _hook = Hook {
                    handle: hook,
                    removed: unhooked,
                };
                status.store(ACTIVE, Ordering::SeqCst);
                let _registered_resource = after_registration();
                'messages: while !stopped.load(Ordering::SeqCst) {
                    let handles = [woken.handle()];
                    let received = unsafe {
                        MsgWaitForMultipleObjectsEx(
                            1,
                            handles.as_ptr(),
                            100,
                            QS_ALLINPUT,
                            MWMO_INPUTAVAILABLE,
                        )
                    };
                    if received == WAIT_OBJECT_0 || received == WAIT_FAILED {
                        break;
                    }
                    if received == WAIT_OBJECT_0 + 1 {
                        while unsafe {
                            PeekMessageW(&mut msg, std::ptr::null_mut(), 0, 0, PM_REMOVE)
                        } != 0
                        {
                            if stopped.load(Ordering::SeqCst) || msg.message == WM_QUIT {
                                break 'messages;
                            }
                            unsafe {
                                TranslateMessage(&msg);
                                DispatchMessageW(&msg);
                            }
                        }
                    }
                }
                status.store(INACTIVE, Ordering::SeqCst);
            })
            .map_err(|_| ())?;
        Ok(Self {
            stop,
            wake,
            state,
            thread: Some(thread),
            removed,
        })
    }
    fn stop_and_join(&mut self) {
        self.stop.store(true, Ordering::SeqCst);
        if unsafe { SetEvent(self.wake.handle()) } == 0 {
            self.state.store(UNAVAILABLE, Ordering::SeqCst);
        }
        // The 100ms wait also notices stop if signaling fails. No thread-ID messages.
        if let Some(t) = self.thread.take() {
            let _ = t.join();
        }
    }
}
impl WorkerStatus for Worker {
    fn status(&self) -> u8 {
        if self
            .thread
            .as_ref()
            .is_some_and(std::thread::JoinHandle::is_finished)
        {
            UNAVAILABLE
        } else {
            self.state.load(Ordering::SeqCst)
        }
    }
}
impl Drop for Worker {
    fn drop(&mut self) {
        self.stop_and_join();
    }
}

#[cfg(test)]
struct PrivateFailureDesktop(isize);
#[cfg(test)]
impl PrivateFailureDesktop {
    fn new() -> Self {
        use windows_sys::Win32::System::StationsAndDesktops::CreateDesktopW;
        let name: Vec<u16> = format!(
            "NanfengIslandFailure-{}-{}",
            std::process::id(),
            super::system_now_millis()
        )
        .encode_utf16()
        .chain(Some(0))
        .collect();
        let handle = unsafe {
            CreateDesktopW(
                name.as_ptr(),
                std::ptr::null(),
                std::ptr::null(),
                0,
                0x10000000,
                std::ptr::null(),
            )
        };
        assert!(
            !handle.is_null(),
            "owned private desktop creation failed: {}",
            unsafe { GetLastError() }
        );
        Self(handle as isize)
    }
}
#[cfg(test)]
impl Drop for PrivateFailureDesktop {
    fn drop(&mut self) {
        assert_ne!(
            unsafe { windows_sys::Win32::System::StationsAndDesktops::CloseDesktop(self.0 as _) },
            0
        );
    }
}
#[cfg(test)]
fn await_worker_finished(worker: &Worker) {
    let until = std::time::Instant::now() + std::time::Duration::from_secs(3);
    while !worker.thread.as_ref().unwrap().is_finished() && std::time::Instant::now() < until {
        std::thread::sleep(std::time::Duration::from_millis(1));
    }
    assert!(
        worker.thread.as_ref().unwrap().is_finished(),
        "owned failure worker did not exit"
    );
}
#[cfg(test)]
#[test]
fn finished_hook_worker_does_not_report_active_after_unwinding() {
    let desktop = PrivateFailureDesktop::new();
    let mut worker =
        Worker::spawn_after_registration(Arc::new(Target::default()), Some(desktop.0), || {
            panic!("synthetic owned hook worker failure")
        })
        .unwrap();
    await_worker_finished(&worker);
    assert!(
        worker.removed.load(Ordering::SeqCst),
        "actual native hook must have been removed by unwind"
    );
    assert_eq!(
        worker.status(),
        UNAVAILABLE,
        "an exited native listener cannot be active"
    );
    worker.stop_and_join();
}

#[cfg(test)]
#[test]
fn failed_native_hook_worker_waits_for_reexpand_then_recovers() {
    use super::desktop_island_outside_click_v1::Controller;
    use std::sync::{atomic::AtomicU64, Mutex};
    let desktop = PrivateFailureDesktop::new();
    let target = Arc::new(Target::default());
    let attempts = Arc::new(AtomicU64::new(0));
    let removals = Arc::new(Mutex::new(Vec::new()));
    let (count, handles) = (attempts.clone(), removals.clone());
    let desktop_id = desktop.0;
    let mut controller = Controller::new(target.clone(), move |t| {
        let worker = if count.fetch_add(1, Ordering::SeqCst) == 0 {
            Worker::spawn_after_registration(t, Some(desktop_id), || {
                panic!("synthetic first native listener failure")
            })?
        } else {
            Worker::spawn(t, Some(desktop_id))?
        };
        handles.lock().unwrap().push(worker.removed.clone());
        Ok(worker)
    });
    let reconcile_until = |controller: &mut Controller<Worker, _>, expected| {
        let until = std::time::Instant::now() + std::time::Duration::from_secs(3);
        while target.status() != expected && std::time::Instant::now() < until {
            controller.reconcile();
            std::thread::sleep(std::time::Duration::from_millis(1));
        }
        assert_eq!(target.status(), expected);
    };
    target.publish(true, 10);
    reconcile_until(&mut controller, "unavailable");
    assert!(removals.lock().unwrap()[0].load(Ordering::SeqCst));
    for _ in 0..5 {
        controller.reconcile();
    }
    assert_eq!(
        attempts.load(Ordering::SeqCst),
        1,
        "failed presentation must not hot-loop registrations"
    );
    target.publish(false, 0);
    target.publish(true, 11);
    reconcile_until(&mut controller, "active");
    assert_eq!(
        attempts.load(Ordering::SeqCst),
        2,
        "new presentation retries exactly once"
    );
    assert!(!removals.lock().unwrap()[1].load(Ordering::SeqCst));
    target.publish(false, 0);
    controller.reconcile();
    assert_eq!(target.status(), "inactive");
    assert!(
        removals.lock().unwrap()[1].load(Ordering::SeqCst),
        "collapse removes recovered native hook"
    );
}

#[cfg(test)]
struct StopQueueState {
    first: Arc<StopEvent>,
    release: Arc<StopEvent>,
    dispatched: std::sync::atomic::AtomicUsize,
    callback_failed: AtomicBool,
    window_destroyed: AtomicBool,
}
#[cfg(test)]
const STOP_QUEUE_MESSAGE: u32 = WM_APP + 83;
#[cfg(test)]
unsafe extern "system" fn stop_queue_window_proc(
    window: HWND,
    message: u32,
    wparam: WPARAM,
    lparam: LPARAM,
) -> LRESULT {
    if message == STOP_QUEUE_MESSAGE {
        let pointer = GetWindowLongPtrW(window, GWLP_USERDATA) as *const StopQueueState;
        if !pointer.is_null() {
            let state = &*pointer;
            if state.dispatched.fetch_add(1, Ordering::SeqCst) == 0 {
                let signaled = SetEvent(state.first.handle()) != 0;
                let released = WaitForSingleObject(state.release.handle(), 3000) == WAIT_OBJECT_0;
                state
                    .callback_failed
                    .store(!signaled || !released, Ordering::SeqCst);
            }
        }
        return 0;
    }
    DefWindowProcW(window, message, wparam, lparam)
}
#[cfg(test)]
struct StopQueueWindow {
    window: HWND,
    class_name: Vec<u16>,
    state: Arc<StopQueueState>,
}
#[cfg(test)]
impl StopQueueWindow {
    fn new(state: Arc<StopQueueState>) -> Self {
        let class_name: Vec<u16> = format!(
            "NanfengStopQueue-{}-{}",
            std::process::id(),
            super::system_now_millis()
        )
        .encode_utf16()
        .chain(Some(0))
        .collect();
        let instance = unsafe { GetModuleHandleW(std::ptr::null()) };
        let class = WNDCLASSW {
            lpfnWndProc: Some(stop_queue_window_proc),
            hInstance: instance,
            lpszClassName: class_name.as_ptr(),
            ..unsafe { std::mem::zeroed() }
        };
        assert_ne!(unsafe { RegisterClassW(&class) }, 0);
        let window = unsafe {
            CreateWindowExW(
                WS_EX_NOACTIVATE | WS_EX_TOOLWINDOW,
                class_name.as_ptr(),
                class_name.as_ptr(),
                0,
                0,
                0,
                8,
                8,
                std::ptr::null_mut(),
                std::ptr::null_mut(),
                instance,
                std::ptr::null(),
            )
        };
        if window.is_null() {
            unsafe {
                UnregisterClassW(class_name.as_ptr(), instance);
            }
            panic!("owned queue probe window could not be created");
        }
        let result = Self {
            window,
            class_name,
            state,
        };
        assert_eq!(unsafe { IsWindowVisible(window) }, 0);
        let pointer = Arc::as_ptr(&result.state) as isize;
        unsafe {
            SetWindowLongPtrW(window, GWLP_USERDATA, pointer);
        }
        assert_eq!(unsafe { GetWindowLongPtrW(window, GWLP_USERDATA) }, pointer);
        for _ in 0..128 {
            assert_ne!(unsafe { PostMessageW(window, STOP_QUEUE_MESSAGE, 0, 0) }, 0);
        }
        result
    }
}
#[cfg(test)]
impl Drop for StopQueueWindow {
    fn drop(&mut self) {
        assert_ne!(unsafe { DestroyWindow(self.window) }, 0);
        self.state.window_destroyed.store(true, Ordering::SeqCst);
        assert_ne!(
            unsafe {
                UnregisterClassW(self.class_name.as_ptr(), GetModuleHandleW(std::ptr::null()))
            },
            0
        );
    }
}
#[cfg(test)]
#[test]
fn stop_signal_preempts_remaining_owned_window_messages() {
    let desktop = PrivateFailureDesktop::new();
    let event = || {
        let handle = unsafe { CreateEventW(std::ptr::null(), 1, 0, std::ptr::null()) };
        assert!(!handle.is_null());
        Arc::new(StopEvent(handle as isize))
    };
    let state = Arc::new(StopQueueState {
        first: event(),
        release: event(),
        dispatched: std::sync::atomic::AtomicUsize::new(0),
        callback_failed: AtomicBool::new(false),
        window_destroyed: AtomicBool::new(false),
    });
    let queue_state = state.clone();
    let mut worker =
        Worker::spawn_after_registration(Arc::new(Target::default()), Some(desktop.0), move || {
            StopQueueWindow::new(queue_state)
        })
        .unwrap();
    assert_eq!(
        unsafe { WaitForSingleObject(state.first.handle(), 3000) },
        WAIT_OBJECT_0
    );
    let stopped = worker.stop.clone();
    std::thread::scope(|scope| {
        let join = scope.spawn(move || {
            worker.stop_and_join();
            worker
        });
        let until = std::time::Instant::now() + std::time::Duration::from_secs(2);
        while !stopped.load(Ordering::SeqCst) && std::time::Instant::now() < until {
            std::thread::sleep(std::time::Duration::from_millis(1));
        }
        assert!(
            stopped.load(Ordering::SeqCst),
            "native stop request was not published"
        );
        assert_ne!(unsafe { SetEvent(state.release.handle()) }, 0);
        let worker = join.join().unwrap();
        assert!(
            worker.removed.load(Ordering::SeqCst),
            "actual hook must be removed before stop returns"
        );
    });
    assert!(!state.callback_failed.load(Ordering::SeqCst));
    assert!(state.window_destroyed.load(Ordering::SeqCst));
    assert_eq!(
        state.dispatched.load(Ordering::SeqCst),
        1,
        "stop must preempt the 127 already queued owned callbacks"
    );
}
#[cfg(test)]
#[test]
fn native_hook_lifecycle_on_a_desktop_never_switched_to() {
    use windows_sys::Win32::System::StationsAndDesktops::{CloseDesktop, CreateDesktopW};
    let name: Vec<u16> = format!(
        "NanfengIslandTest-{}-{}",
        std::process::id(),
        super::system_now_millis()
    )
    .encode_utf16()
    .chain(Some(0))
    .collect();
    let desktop = unsafe {
        CreateDesktopW(
            name.as_ptr(),
            std::ptr::null(),
            std::ptr::null(),
            0,
            0x10000000,
            std::ptr::null(),
        )
    };
    assert!(
        !desktop.is_null(),
        "isolated desktop creation failed: {}",
        unsafe { GetLastError() }
    );
    struct Desktop(isize);
    impl Drop for Desktop {
        fn drop(&mut self) {
            assert_ne!(unsafe { CloseDesktop(self.0 as _) }, 0);
        }
    }
    let desktop = Desktop(desktop as isize);
    for _ in 0..20 {
        let mut w = Worker::spawn(Arc::new(Target::default()), Some(desktop.0)).unwrap();
        let until = std::time::Instant::now() + std::time::Duration::from_secs(3);
        while w.state.load(Ordering::SeqCst) == STARTING && std::time::Instant::now() < until {
            std::thread::sleep(std::time::Duration::from_millis(1));
        }
        assert_eq!(
            w.state.load(Ordering::SeqCst),
            ACTIVE,
            "native hook not registered"
        );
        w.stop_and_join();
        assert!(w.removed.load(Ordering::SeqCst), "native unhook failed");
    }
}

#[cfg(test)]
#[test]
fn hit_test_does_not_wait_for_another_threads_window_on_an_unshown_desktop() {
    use windows_sys::Win32::System::StationsAndDesktops::{
        CloseDesktop, CreateDesktopW, SetThreadDesktop,
    };
    let name: Vec<u16> = format!(
        "NanfengIslandHitTest-{}-{}",
        std::process::id(),
        super::system_now_millis()
    )
    .encode_utf16()
    .chain(Some(0))
    .collect();
    let d = unsafe {
        CreateDesktopW(
            name.as_ptr(),
            std::ptr::null(),
            std::ptr::null(),
            0,
            0x10000000,
            std::ptr::null(),
        )
    };
    assert!(!d.is_null());
    let desktop = d as isize;
    let (tx, rx) = std::sync::mpsc::channel();
    let (release, wait) = std::sync::mpsc::channel();
    let window_thread = std::thread::spawn(move || unsafe {
        assert_ne!(SetThreadDesktop(desktop as _), 0);
        let module = GetModuleHandleW(std::ptr::null());
        let mut class: WNDCLASSW = std::mem::zeroed();
        class.lpfnWndProc = Some(DefWindowProcW);
        class.hInstance = module;
        class.lpszClassName = name.as_ptr();
        assert_ne!(RegisterClassW(&class), 0);
        // WS_VISIBLE exists only on this non-input desktop. No SwitchDesktop, focus,
        // user-desktop window or injected input is used by this test.
        let window = CreateWindowExW(
            WS_EX_NOACTIVATE | WS_EX_TOOLWINDOW,
            name.as_ptr(),
            name.as_ptr(),
            WS_POPUP | WS_VISIBLE,
            100,
            100,
            200,
            200,
            std::ptr::null_mut(),
            std::ptr::null_mut(),
            module,
            std::ptr::null(),
        );
        assert!(!window.is_null());
        tx.send(window as isize).unwrap();
        wait.recv().unwrap();
        let mut msg: MSG = std::mem::zeroed();
        PeekMessageW(&mut msg, std::ptr::null_mut(), 0, 0, PM_NOREMOVE);
        DestroyWindow(window);
        UnregisterClassW(name.as_ptr(), module);
    });
    let expected = rx.recv_timeout(std::time::Duration::from_secs(3)).unwrap();
    let (found_tx, found_rx) = std::sync::mpsc::channel();
    let query = std::thread::spawn(move || {
        assert_ne!(unsafe { SetThreadDesktop(desktop as _) }, 0);
        let started = std::time::Instant::now();
        let found = hit_root(POINT { x: 120, y: 120 });
        found_tx.send((found, started.elapsed())).unwrap();
    });
    let result = found_rx.recv_timeout(std::time::Duration::from_millis(200));
    release.send(()).unwrap();
    window_thread.join().unwrap();
    query.join().unwrap();
    assert_ne!(unsafe { CloseDesktop(desktop as _) }, 0);
    let (found, elapsed) =
        result.expect("cross-thread hit testing exceeded the 200ms probe budget");
    assert_eq!(found, Some(expected));
    eprintln!("isolated hit-test elapsed_us={}", elapsed.as_micros());
}
