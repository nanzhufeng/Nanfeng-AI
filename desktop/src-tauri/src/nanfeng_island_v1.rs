//! Ephemeral metadata projection. Never a task store, credential reader, or transport owner.
use serde::Serialize;
use std::{collections::BTreeMap, sync::{Arc, Mutex, OnceLock, Weak, atomic::{AtomicBool, Ordering}}};
use tauri::{Emitter, Manager};
const LABEL:&str="generation-island";
#[derive(Clone, Debug, Serialize)]
#[serde(rename_all="camelCase")]
pub struct Task {
    pub key:String, pub data_area:String, pub workspace_id:String, pub conversation_id:String,
    pub assistant_message_id:String, pub attempt_id:String, pub model_label:String, pub state:String,
    pub saved_characters:usize, pub started_at:i64, pub updated_at:i64,
}
impl Task { fn running(&self)->bool {matches!(self.state.as_str(),"PENDING"|"RUNNING")} }
struct Entry {task:Task,signal:Option<Weak<AtomicBool>>}
#[derive(Default)]
pub struct Ledger {tasks:BTreeMap<String,Entry>}
impl Ledger {
    fn update(&mut self,mut next:Task) {
        if let Some(old)=self.tasks.get(&next.key) {
            let accounting_advance=matches!((old.task.state.as_str(),next.state.as_str()),
                ("RESPONSE_COMMITTED","COMPLETED_ACCOUNTING_PENDING"|"COMPLETED")
                | ("COMPLETED_ACCOUNTING_PENDING","COMPLETED"));
            if !old.task.running() && !accounting_advance {return;}
            if (old.task.data_area.as_str(),old.task.workspace_id.as_str(),old.task.conversation_id.as_str(),old.task.assistant_message_id.as_str(),old.task.attempt_id.as_str()) != (next.data_area.as_str(),next.workspace_id.as_str(),next.conversation_id.as_str(),next.assistant_message_id.as_str(),next.attempt_id.as_str()) {return;}
            next.started_at=old.task.started_at;next.saved_characters=old.task.saved_characters;
        }
        let signal=self.tasks.get(&next.key).and_then(|e|e.signal.clone());
        self.tasks.insert(next.key.clone(),Entry{task:next,signal});
        let mut terminal:Vec<_>=self.tasks.values().filter(|e|!e.task.running()).map(|e|(e.task.updated_at,e.task.key.clone())).collect();terminal.sort();
        for (_,key) in terminal.iter().take(terminal.len().saturating_sub(8)) {self.tasks.remove(key);}
    }
    fn stop(&self,key:&str)->Result<(),String> {
        let entry=self.tasks.get(key).filter(|e|e.task.running()).ok_or("任务已结束或失效")?;
        let signal=entry.signal.as_ref().and_then(Weak::upgrade).ok_or("任务正在准备或已结束，请稍后重试")?;
        signal.store(true,Ordering::SeqCst);Ok(())
    }
    fn finish(&mut self,key:&str,signal:&Weak<AtomicBool>) {
        if let Some(e)=self.tasks.get_mut(key).filter(|e|e.signal.as_ref().is_some_and(|s|Weak::ptr_eq(s,signal))) {
            e.signal=None;if e.task.running() {e.task.state="UNKNOWN".into();e.task.updated_at=super::system_now_millis();}
        }
    }
    fn snapshot(&self)->Vec<Task> {let mut tasks:Vec<_>=self.tasks.values().map(|e|e.task.clone()).collect();tasks.sort_by(|a,b|(a.started_at,&a.key).cmp(&(b.started_at,&b.key)));tasks}
}
fn ledger()->&'static Mutex<Ledger> {static VALUE:OnceLock<Mutex<Ledger>>=OnceLock::new();VALUE.get_or_init(||Mutex::new(Ledger::default()))}
fn key(area:&str,p:&super::DesktopOrdinaryChatAttemptProjection)->String {
    let identity=serde_json::to_vec(&(area,&p.workspace_id,&p.conversation_id,&p.assistant_message_id,&p.attempt_id)).unwrap_or_default();
    format!("{area}:{}",super::sha256(&identity))
}
pub fn record(app:&super::AreaAppHandle,p:&super::DesktopOrdinaryChatAttemptProjection) {
    if let Ok(mut l)=ledger().lock() {
        let now=super::system_now_millis();
        l.update(Task{key:key(app.area().wire(),p),data_area:app.area().wire().into(),workspace_id:p.workspace_id.clone(),conversation_id:p.conversation_id.clone(),assistant_message_id:p.assistant_message_id.clone(),attempt_id:p.attempt_id.clone(),model_label:p.model_display_name.as_deref().unwrap_or("模型连接中").chars().take(96).collect(),state:p.state.clone(),saved_characters:0,started_at:now,updated_at:now});
    }
}
/// Called only after the actual workspace transaction committed; accepts a count, never body text.
pub fn persisted(area:&str,p:&super::DesktopOrdinaryChatAttemptProjection,count:usize) {
    if let Ok(mut l)=ledger().lock() {if let Some(entry)=l.tasks.get_mut(&key(area,p)).filter(|e|e.task.running()) {entry.task.saved_characters=count;}}
}
pub struct RunGuard {key:String,signal:Weak<AtomicBool>}
impl RunGuard {
    pub fn new(app:&super::AreaAppHandle,p:&super::DesktopOrdinaryChatAttemptProjection,signal:&Arc<AtomicBool>)->Self {
        let key=key(app.area().wire(),p);let weak=Arc::downgrade(signal);
        if let Ok(mut l)=ledger().lock() {if let Some(e)=l.tasks.get_mut(&key).filter(|e|e.task.running()) {e.signal=Some(weak.clone());}}
        Self{key,signal:weak}
    }
}
impl Drop for RunGuard {
    fn drop(&mut self) {if let Ok(mut l)=ledger().lock() {l.finish(&self.key,&self.signal);}}
}

fn caller(window:&tauri::WebviewWindow)->Result<(),String> {if window.label()==LABEL {Ok(())} else {Err("该操作只允许后台状态窗口".into())}}
fn task_open_policy(windows:bool,area:&str)->Result<(),&'static str> {
    if windows && area=="WORK" {Err("工作区查看入口已隐藏，任务和数据仍保留。")} else {Ok(())}
}
#[tauri::command]
pub fn read_generation_island(window:tauri::WebviewWindow)->Result<Vec<Task>,String> {caller(&window)?;ledger().lock().map(|l|l.snapshot()).map_err(|_|"任务状态暂不可用".into())}
#[tauri::command]
pub fn generation_island_action(window:tauri::WebviewWindow,key:String,action:String)->Result<(),String> {
    caller(&window)?;let app=window.app_handle();
    let task={let l=ledger().lock().map_err(|_|"任务状态暂不可用")?;
        if action=="stop" {return l.stop(&key);}
        if action!="open" {return Err("此任务不支持该操作".into());}
        l.tasks.get(&key).map(|e|e.task.clone()).ok_or("任务已失效")?};
    task_open_policy(cfg!(windows),&task.data_area).map_err(str::to_owned)?;
    super::reopen_desktop_window_lifecycle(app);
    if presentation_allowed(app) {if let Some(main)=app.get_webview_window("main") {main.show().map_err(|_|"主窗口未能显示")?;let _=main.unminimize();main.set_focus().map_err(|_|"主窗口未能激活")?;}}
    app.emit_to("main","nanfeng-island-open-v1",task).map_err(|_|"未能打开对话".into())
}
/// The isolated hidden acceptance build never presents either window, including via explicit open IPC.
fn presentation_allowed(app:&tauri::AppHandle)->bool {
    super::desktop_windows_acceptance::presentation_allowed(cfg!(windows), &app.config().identifier,
        std::env::args().any(|arg|arg=="--windows-acceptance-visible"))
}
pub fn foreground_policy(main_visible:bool,minimized:bool,application_active:bool)->bool {main_visible&&!minimized&&application_active}
fn application_foreground(app:&tauri::AppHandle)->bool {
    let Some(main)=app.get_webview_window("main") else {return true;};
    #[cfg(windows)] let active=unsafe {
        use windows_sys::Win32::UI::WindowsAndMessaging::{GetForegroundWindow,GetWindowThreadProcessId};
        let foreground=GetForegroundWindow();let mut pid=0;
        if foreground.is_null() {true} else {let thread=GetWindowThreadProcessId(foreground,&mut pid);thread==0||pid==std::process::id()}
    };
    #[cfg(not(windows))] let active=main.is_focused().unwrap_or(true);
    foreground_policy(main.is_visible().unwrap_or(true),main.is_minimized().unwrap_or(false),active)
}
fn place(app:&tauri::AppHandle,window:&tauri::WebviewWindow,height:f64) {
    let monitor=app.get_webview_window("main").and_then(|main|main.current_monitor().ok().flatten()).or_else(||window.primary_monitor().ok().flatten());
    let Some(monitor)=monitor else {return;};let scale=monitor.scale_factor();if !scale.is_finite()||scale<=0. {return;}
    let work=monitor.work_area();let width=380_f64.min(f64::from(work.size.width)/scale);let height=height.min((f64::from(work.size.height)/scale-24.).max(1.));
    let wanted=tauri::LogicalSize::new(width,height);
    let physical=wanted.to_physical::<u32>(scale);
    if window.inner_size().ok()!=Some(physical) {let _=window.set_size(wanted);}
    let x=work.position.x+(i64::from(work.size.width).saturating_sub(i64::from(physical.width))/2) as i32;
    let y=work.position.y+((12.*scale) as i32).min((i64::from(work.size.height)-i64::from(physical.height)).max(0) as i32);
    let position=tauri::PhysicalPosition::new(x,y);if window.outer_position().ok()!=Some(position) {let _=window.set_position(position);}
}
#[cfg(windows)]
fn configure_nonactivating(window:&tauri::WebviewWindow) {
    use windows_sys::Win32::UI::WindowsAndMessaging::*;
    if let Ok(handle)=window.hwnd() {unsafe {
        let hwnd=handle.0 as _;let style=GetWindowLongPtrW(hwnd,GWL_EXSTYLE);
        SetWindowLongPtrW(hwnd,GWL_EXSTYLE,(style|WS_EX_NOACTIVATE as isize|WS_EX_TOOLWINDOW as isize)&!(WS_EX_APPWINDOW as isize));
    }}
}
#[cfg(windows)]
fn show_without_activation(window:&tauri::WebviewWindow) {
    use windows_sys::Win32::UI::WindowsAndMessaging::*;
    configure_nonactivating(window);
    if let Ok(handle)=window.hwnd() {unsafe {
        let hwnd=handle.0 as _;ShowWindow(hwnd,SW_SHOWNOACTIVATE);
        let _=SetWindowPos(hwnd,HWND_TOPMOST,0,0,0,0,SWP_NOMOVE|SWP_NOSIZE|SWP_NOACTIVATE);
    }}
}
#[cfg(not(windows))] fn show_without_activation(window:&tauri::WebviewWindow) {let _=window.show();}
fn publish_outside_target(window:&tauri::WebviewWindow,active:bool) {
    #[cfg(windows)] let handle=window.hwnd().ok().map(|h|h.0 as isize).unwrap_or(0);
    #[cfg(not(windows))] let handle=0;
    super::desktop_island_outside_click_v1::target().publish(active,handle);
}
#[derive(Serialize)]
#[serde(rename_all="camelCase")]
pub struct VisibilityStatus {outside_click:&'static str}
pub fn foreground_changed(app:&tauri::AppHandle) {
    let Some(window)=app.get_webview_window(LABEL) else {super::desktop_island_outside_click_v1::target().publish(false,0);return;};
    if !presentation_allowed(app) {publish_outside_target(&window,false);if window.is_visible().unwrap_or(false) {let _=window.hide();}return;}
    if application_foreground(app) {
        publish_outside_target(&window,false);
        if window.is_visible().unwrap_or(false) {let _=app.emit_to(LABEL,"nanfeng-island-collapse-v1",());let _=window.hide();}
    }else if ledger().lock().map(|l|l.tasks.values().any(|e|e.task.running())).unwrap_or(false)&&!window.is_visible().unwrap_or(false) {
        place(app,&window,48.);show_without_activation(&window);publish_outside_target(&window,false);
    }
}
#[tauri::command]
pub fn generation_island_visibility(window:tauri::WebviewWindow,visible:bool,height:f64,expanded:Option<bool>)->Result<VisibilityStatus,String> {
    caller(&window)?;if !height.is_finite() {return Err("状态窗口尺寸无效".into());}
    let app=window.app_handle();let allowed=visible&&presentation_allowed(app)&&!application_foreground(app)&&ledger().lock().map(|l|!l.tasks.is_empty()).unwrap_or(false);
    if allowed {place(app,&window,height.clamp(48.,420.));if !window.is_visible().unwrap_or(false) {show_without_activation(&window);}publish_outside_target(&window,expanded.unwrap_or(false)&&window.is_visible().unwrap_or(false));}
    else {publish_outside_target(&window,false);if application_foreground(app) {let _=app.emit_to(LABEL,"nanfeng-island-collapse-v1",());}let _=window.hide();}
    Ok(VisibilityStatus{outside_click:super::desktop_island_outside_click_v1::target().status()})
}
struct MonitorCallback;
impl Drop for MonitorCallback {fn drop(&mut self){MONITOR_QUEUED.store(false,Ordering::SeqCst);}}
static MONITOR_STOP:AtomicBool=AtomicBool::new(false);
static MONITOR_QUEUED:AtomicBool=AtomicBool::new(false);
static MONITOR:Mutex<Option<std::thread::JoinHandle<()>>>=Mutex::new(None);
pub fn install(app:&tauri::AppHandle) {
    #[cfg(not(windows))] {let _=app;return;}
    #[cfg(windows)] {
        if app.get_webview_window(LABEL).is_some() {return;}
        if tauri::WebviewWindowBuilder::new(app,LABEL,tauri::WebviewUrl::App("island.html".into())).title("南枫岛").inner_size(380.,48.).decorations(false).transparent(true).shadow(false).resizable(false).always_on_top(true).visible(false).focused(false).focusable(false).skip_taskbar(true).build().is_err() {return;}
        if let Some(window)=app.get_webview_window(LABEL) {configure_nonactivating(&window);}
        let Ok(mut owner)=MONITOR.lock() else {return;};if owner.is_some() {return;}MONITOR_STOP.store(false,Ordering::SeqCst);
        let handle=app.clone();*owner=std::thread::Builder::new().name("nanfeng-island-foreground".into()).spawn(move|| {
            let mut outside=super::desktop_island_outside_click_v1::Controller::new(super::desktop_island_outside_click_v1::target(),super::desktop_island_outside_click_windows::Worker::start);
            while !MONITOR_STOP.load(Ordering::SeqCst) {
                outside.reconcile();
                if MONITOR_QUEUED.compare_exchange(false,true,Ordering::SeqCst,Ordering::SeqCst).is_ok() {
                    let app=handle.clone();if handle.run_on_main_thread(move||{let _callback=MonitorCallback;foreground_changed(&app);if super::desktop_island_outside_click_v1::target().take_collapse(){let _=app.emit_to(LABEL,"nanfeng-island-collapse-v1",());}}).is_err() {MONITOR_QUEUED.store(false,Ordering::SeqCst);}
                }
                std::thread::sleep(std::time::Duration::from_millis(100));
            }
        }).ok();
    }
}
pub fn shutdown(app:&tauri::AppHandle) {
    super::desktop_island_outside_click_v1::target().publish(false,0);
    MONITOR_STOP.store(true,Ordering::SeqCst);if let Ok(mut owner)=MONITOR.lock() {if let Some(thread)=owner.take() {let _=thread.join();}}
    if let Some(window)=app.get_webview_window(LABEL) {let _=window.destroy();}
}

#[cfg(test)]
mod tests {
    #[test]
    fn windows_work_open_is_rejected_while_chat_and_mac_keep_their_view_policy() {
        assert_eq!(super::task_open_policy(true,"WORK"),Err("工作区查看入口已隐藏，任务和数据仍保留。"));
        assert!(super::task_open_policy(true,"CHAT").is_ok());
        assert!(super::task_open_policy(false,"WORK").is_ok());
        assert!(super::task_open_policy(false,"CHAT").is_ok());
    }
    use super::*;
    fn task(key:&str,state:&str,area:&str)->Task {Task{key:key.into(),data_area:area.into(),workspace_id:"workspace".into(),conversation_id:"conversation".into(),assistant_message_id:key.into(),attempt_id:key.into(),model_label:"6 Astra · RC".into(),state:state.into(),saved_characters:0,started_at:100,updated_at:200}}
    #[test] fn response_commit_advances_to_the_measured_owner_terminal() {
        for middle in [None,Some("COMPLETED_ACCOUNTING_PENDING")] {
            let mut l=Ledger::default();l.update(task("a","RUNNING","CHAT"));l.tasks.get_mut("a").unwrap().task.saved_characters=123;
            let mut committed=task("a","RESPONSE_COMMITTED","CHAT");committed.updated_at=500;l.update(committed);
            if let Some(state)=middle {let mut pending=task("a",state,"CHAT");pending.updated_at=600;l.update(pending);assert_eq!(l.snapshot()[0].state,state);}
            let mut done=task("a","COMPLETED","CHAT");done.updated_at=700;l.update(done);let snapshot=l.snapshot();let t=&snapshot[0];
            assert_eq!(t.state,"COMPLETED");assert_eq!(t.updated_at,700);assert_eq!(t.started_at,100);assert_eq!(t.saved_characters,123);
        }
    }
    #[test] fn measured_terminal_cannot_regress_or_renew_from_late_accounting_callbacks() {
        let mut l=Ledger::default();l.update(task("a","RUNNING","CHAT"));let mut done=task("a","COMPLETED","CHAT");done.updated_at=700;l.update(done);
        for state in ["RUNNING","RESPONSE_COMMITTED","COMPLETED_ACCOUNTING_PENDING","COMPLETED","UNKNOWN","FAILED"] {let mut late=task("a",state,"CHAT");late.updated_at=900;l.update(late);}
        let t=&l.snapshot()[0];assert_eq!(t.state,"COMPLETED");assert_eq!(t.updated_at,700);
    }
    #[test] fn accounting_completion_cannot_cross_a_task_identity_or_revive_cancelled_runs() {
        for field in 0..5 {let mut l=Ledger::default();l.update(task("a","RESPONSE_COMMITTED","CHAT"));let mut next=task("a","COMPLETED","CHAT");match field {0=>next.data_area="WORK".into(),1=>next.workspace_id="other".into(),2=>next.conversation_id="other".into(),3=>next.assistant_message_id="other".into(),_=>next.attempt_id="other".into()};l.update(next);assert_eq!(l.snapshot()[0].state,"RESPONSE_COMMITTED");}
        for state in ["CANCELLED","FAILED","UNKNOWN"] {let mut l=Ledger::default();l.update(task("a",state,"CHAT"));l.update(task("a","COMPLETED","CHAT"));assert_eq!(l.snapshot()[0].state,state);}
    }
    #[test] fn terminal_callbacks_cannot_revive_or_renew_a_task() {
        let mut l=Ledger::default();l.update(task("a","RUNNING","CHAT"));l.tasks.get_mut("a").unwrap().task.saved_characters=123;
        let mut done=task("a","COMPLETED","CHAT");done.updated_at=500;l.update(done);l.update(task("a","RUNNING","CHAT"));
        let t=&l.snapshot()[0];assert_eq!(t.saved_characters,123);assert_eq!(t.started_at,100);assert_eq!(t.updated_at,500);assert!(!t.running());
    }
    #[test] fn stopping_one_run_never_cancels_a_peer_in_the_same_conversation_or_other_area() {
        let mut l=Ledger::default();let signals:Vec<_>=(0..3).map(|_|Arc::new(AtomicBool::new(false))).collect();
        for (index,(key,area)) in [("old","CHAT"),("peer","CHAT"),("work","WORK")].into_iter().enumerate() {l.update(task(key,"RUNNING",area));l.tasks.get_mut(key).unwrap().signal=Some(Arc::downgrade(&signals[index]));}
        l.stop("old").unwrap();assert!(signals[0].load(Ordering::SeqCst));assert!(!signals[1].load(Ordering::SeqCst));assert!(!signals[2].load(Ordering::SeqCst));
        let mut done=task("old","CANCELLED","CHAT");done.updated_at=600;l.update(done);assert!(l.stop("old").is_err());assert!(!signals[1].load(Ordering::SeqCst));
    }
    #[test] fn stale_cleanup_cannot_release_a_new_signal_and_unsettled_cleanup_is_truthful() {
        let mut l=Ledger::default();l.update(task("a","RUNNING","CHAT"));let old=Arc::new(AtomicBool::new(false));let new=Arc::new(AtomicBool::new(false));
        l.tasks.get_mut("a").unwrap().signal=Some(Arc::downgrade(&new));l.finish("a",&Arc::downgrade(&old));assert!(l.tasks["a"].task.running());
        l.finish("a",&Arc::downgrade(&new));assert_eq!(l.tasks["a"].task.state,"UNKNOWN");assert!(l.tasks["a"].signal.is_none());
    }
    #[test] fn completed_history_is_bounded_without_evicting_live_runs() {
        let mut l=Ledger::default();l.update(task("running","RUNNING","CHAT"));for n in 0..50 {let key=format!("done{n}");let mut t=task(&key,"COMPLETED","CHAT");t.updated_at=n;l.update(t);}
        assert_eq!(l.snapshot().len(),9);assert!(l.tasks["running"].task.running());
    }
    #[test] fn conflicting_identity_cannot_replace_a_registered_key() {
        let mut l=Ledger::default();l.update(task("a","RUNNING","CHAT"));l.update(task("a","FAILED","WORK"));assert_eq!(l.tasks["a"].task.data_area,"CHAT");assert!(l.tasks["a"].task.running());
    }
    #[test] fn snapshot_is_metadata_only_and_weak_registration_does_not_own_the_worker() {
        let mut l=Ledger::default();l.update(task("a","RUNNING","CHAT"));let signal=Arc::new(AtomicBool::new(false));l.tasks.get_mut("a").unwrap().signal=Some(Arc::downgrade(&signal));drop(signal);assert!(l.stop("a").is_err());
        let value=serde_json::to_value(l.snapshot()).unwrap();let row=value[0].as_object().unwrap();assert_eq!(row.len(),11);for field in ["text","body","credentials","signal","apiKey","payload"] {assert!(!row.contains_key(field));}
    }
    #[test] fn foreground_policy_accounts_for_owned_dialogs_minimization_and_hidden_main() {
        assert!(foreground_policy(true,false,true));assert!(!foreground_policy(true,false,false));assert!(!foreground_policy(true,true,true));assert!(!foreground_policy(false,false,true));
    }
}
