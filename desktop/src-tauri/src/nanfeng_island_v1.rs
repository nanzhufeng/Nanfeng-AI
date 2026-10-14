//! Metadata-only presentation. No store lock, message body, credential or network dependency.
use serde::Serialize;
use std::{collections::BTreeMap, sync::{Mutex, OnceLock}};
use tauri::{Manager, Emitter};

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all="camelCase")]
pub struct Task {
    pub key: String, pub data_area: String, pub workspace_id: String, pub conversation_id: String,
    pub model_label: String, pub state: String, pub saved_characters: usize,
    pub started_at: i64, pub updated_at: i64,
}
impl Task {
    fn running(&self) -> bool { matches!(self.state.as_str(),"PENDING"|"RUNNING") }
}
#[derive(Default)]
pub struct Ledger { tasks: BTreeMap<String,Task> }
impl Ledger {
    pub fn update(&mut self, mut next: Task) {
        if let Some(old)=self.tasks.get(&next.key) {
            if !old.running() { return; } // accounting/title callbacks cannot revive a finished answer
            next.started_at=old.started_at;
            next.saved_characters=next.saved_characters.max(old.saved_characters);
        }
        self.tasks.insert(next.key.clone(),next);
        let mut completed: Vec<_>=self.tasks.values().filter(|t| !t.running()).map(|t|(t.updated_at,t.key.clone())).collect();
        completed.sort();
        let excess=completed.len().saturating_sub(8);
        for (_,key) in completed.into_iter().take(excess) {self.tasks.remove(&key);}
    }
    pub fn checkpoint(&mut self,key: &str,count: usize) {
        if let Some(task)=self.tasks.get_mut(key).filter(|t|t.running()) {task.saved_characters=count.max(task.saved_characters);}
    }
    fn snapshot(&self) -> Vec<Task> {let mut tasks:Vec<_>=self.tasks.values().cloned().collect();tasks.sort_by_key(|t|t.started_at);tasks}
}
fn ledger()-> &'static Mutex<Ledger> {static VALUE:OnceLock<Mutex<Ledger>>=OnceLock::new();VALUE.get_or_init(||Mutex::new(Ledger::default()))}
pub fn checkpoint(area: &str, assistant_id: &str, count: usize) {
    if let Ok(mut value)=ledger().lock() {value.checkpoint(&format!("{area}:{assistant_id}"),count);}
}
pub struct OnFinish<F:FnOnce()>(Option<F>);
impl<F:FnOnce()> OnFinish<F> {pub fn new(callback:F)->Self {Self(Some(callback))}}
impl<F:FnOnce()> Drop for OnFinish<F> {fn drop(&mut self) {if let Some(callback)=self.0.take() {callback();}}}
pub struct RunGuard(String);
impl RunGuard { pub fn new(area:&str,id:&str)->Self {Self(format!("{area}:{id}"))} }
impl Drop for RunGuard {
    fn drop(&mut self) {if let Ok(mut value)=ledger().lock() {if let Some(task)=value.tasks.get_mut(&self.0).filter(|t|t.running()) {task.state="UNKNOWN".into();task.updated_at=super::system_now_millis();}}}
}
pub fn generation_island_foreground(main_visible: bool, minimized: bool, application_active: bool, main_focused: bool) -> bool {
    main_visible && !minimized && if cfg!(target_os="macos") { application_active } else { main_focused }
}

fn application_foreground(app: &tauri::AppHandle) -> bool {
    let Some(main) = app.get_webview_window("main") else { return false; };
    let focused = main.is_focused().unwrap_or(false);
    #[cfg(target_os="macos")]
    let active = objc2::MainThreadMarker::new()
        .map(|marker| objc2_app_kit::NSApplication::sharedApplication(marker).isActive())
        .unwrap_or(true); // Unknown activation conservatively suppresses a foreground overlay.
    #[cfg(not(target_os="macos"))]
    let active = focused;
    generation_island_foreground(main.is_visible().unwrap_or(false), main.is_minimized().unwrap_or(false), active, focused)
}

pub fn foreground_changed(app:&tauri::AppHandle) {
    let foreground=application_foreground(app);
    if let Some(window)=app.get_webview_window("generation-island") {
        if foreground {let _=app.emit_to("generation-island","nanfeng-island-collapse-v1",());let _=window.hide();}
        else if read_generation_island().iter().any(Task::running) {let _=window.show();}
    }
}
pub fn final_checkpoint(area:&str,assistant_id:&str,count:usize) {
    if let Ok(mut value)=ledger().lock() {if let Some(task)=value.tasks.get_mut(&format!("{area}:{assistant_id}")).filter(|t|t.running()) {task.saved_characters=count;}}
}
pub fn record(app:&super::AreaAppHandle, projection:&super::DesktopOrdinaryChatAttemptProjection) {
    let mut newly_running=false;
    if let Ok(mut value)=ledger().lock() {
        let now=super::system_now_millis();
        newly_running=!value.tasks.contains_key(&format!("{}:{}",app.area().wire(),projection.assistant_message_id)) && matches!(projection.state.as_str(),"PENDING"|"RUNNING");
        value.update(Task {key:format!("{}:{}",app.area().wire(),projection.assistant_message_id),data_area:app.area().wire().into(),workspace_id:projection.workspace_id.clone(),conversation_id:projection.conversation_id.clone(),model_label:projection.model_display_name.clone().map(|name| {
            if projection.provider_id.as_deref().is_some_and(|id|id.starts_with("RELAYCORE_")) {
                let short=name.split(" · ").next().unwrap_or(&name).replace("GPT-6 Astra","6 Astra").replace("GPT-6.1 Sol","6.1 Sol");format!("{short} · RC")
            } else {name}
        }).unwrap_or_else(||"模型连接中".into()),state:projection.state.clone(),saved_characters:0,started_at:now,updated_at:now});
    }
    if newly_running {let app=std::ops::Deref::deref(app).clone();let ui=app.clone();let _=app.run_on_main_thread(move ||foreground_changed(&ui));}
}
#[tauri::command]
pub fn read_generation_island()->Vec<Task> {ledger().lock().map(|v|v.snapshot()).unwrap_or_default()}
#[tauri::command]
pub fn generation_island_action(app:tauri::AppHandle,key:String,action:String)->Result<(),String> {
    let task=ledger().lock().map_err(|_|"任务状态暂不可用")?.tasks.get(&key).cloned().ok_or("任务已失效")?;
    if action=="open" {
        super::GENERATION_EXIT_DRAIN.advance();
        app.state::<super::AppState>().deferred_exit_started.store(false,std::sync::atomic::Ordering::SeqCst);
        if let Some(window)=app.get_webview_window("main") {let _=window.show();let _=window.unminimize();let _=window.set_focus();}
        app.emit("nanfeng-island-open-v1",task).map_err(|_|"未能打开对话".into())
    } else if action=="stop" && task.running() {
        let area=super::DesktopDataArea::parse(&task.data_area)?;
        let handle=super::AreaAppHandle::new(app,area);let state=handle.area_state();
        let signals=state.ordinary_chat_cancellations.lock().map_err(|_|"任务状态暂不可用")?;
        if let Some(signals)=signals.get(&task.conversation_id) {for signal in signals {signal.store(true,std::sync::atomic::Ordering::SeqCst);}}
        Ok(())
    } else {Err("此任务不支持该操作".into())}
}

#[cfg(target_os="macos")]
thread_local! {static OUTSIDE_MONITOR:std::cell::RefCell<Option<objc2::rc::Retained<objc2::runtime::AnyObject>>> = const {std::cell::RefCell::new(None)};}
#[cfg(target_os="macos")]
fn install_outside_monitor(app:&tauri::AppHandle) {
    use objc2_app_kit::{NSEvent,NSEventMask};
    let app=app.clone();
    // Mouse-down only, no key events, coordinates, logging or event interception.
    let block=block2::RcBlock::new(move |_event:std::ptr::NonNull<NSEvent>| {let _=app.emit_to("generation-island","nanfeng-island-collapse-v1",());});
    let token=NSEvent::addGlobalMonitorForEventsMatchingMask_handler(NSEventMask::LeftMouseDown,&block);
    OUTSIDE_MONITOR.with(|value|*value.borrow_mut()=token);
}
pub fn remove_outside_monitor() {
    #[cfg(target_os="macos")]
    OUTSIDE_MONITOR.with(|value| {if let Some(token)=value.borrow_mut().take() {unsafe {objc2_app_kit::NSEvent::removeMonitor(&token);}}});
}

pub fn install(app:&tauri::AppHandle) {
    // Create on the UI thread, then keep background presentation independent of the main webview.
    let window=tauri::WebviewWindowBuilder::new(app,"generation-island",tauri::WebviewUrl::App("island.html".into()))
        .title("南枫岛").inner_size(380.0,420.0).decorations(false).transparent(true)
        .accept_first_mouse(true).shadow(false).resizable(false).always_on_top(true).visible(false).focused(false).focusable(false).skip_taskbar(true).build();
    let Ok(island)=window else {return};
    #[cfg(target_os="macos")] install_outside_monitor(app);
    if let Ok(Some(monitor))=island.primary_monitor() {
        let scale=monitor.scale_factor();let size=monitor.size().to_logical::<f64>(scale);let origin=monitor.position().to_logical::<f64>(scale);
        let _=island.set_position(tauri::LogicalPosition::new(origin.x+(size.width-380.0)/2.0,origin.y+34.0));
    }
}
#[tauri::command]
pub fn generation_island_visibility(app:tauri::AppHandle,visible:bool,height:f64)->Result<(),String> {
    if app.get_webview_window("generation-island").is_none() {return Err("灵动岛窗口不可用".into());}
    let ui=app.clone();
    app.run_on_main_thread(move || {
        let Some(window)=ui.get_webview_window("generation-island") else {return;};
        if height.is_finite() {let _=window.set_size(tauri::LogicalSize::new(380.0,height.clamp(48.0,420.0)));}
        let foreground=application_foreground(&ui);
        if foreground {let _=ui.emit_to("generation-island","nanfeng-island-collapse-v1",());}
        if visible && !foreground && !read_generation_island().is_empty() {let _=window.show();}
        else {let _=window.hide();}
    }).map_err(|_|"灵动岛显示调度失败".into())
}

#[cfg(test)] mod tests {
    use super::*;
    #[cfg(target_os="macos")]
    #[test] fn own_dialog_keeps_the_application_foreground_without_a_focused_main_window() {
        assert!(generation_island_foreground(true, false, true, false));
    }
    #[test] fn minimizing_or_switching_apps_allows_background_progress() {
        assert!(!generation_island_foreground(true, true, true, false));
        assert!(!generation_island_foreground(true, false, false, false));
        assert!(!generation_island_foreground(false, false, true, false));
    }
    fn task(key:&str,state:&str,at:i64)->Task {Task {key:key.into(),data_area:"CHAT".into(),workspace_id:"w".into(),conversation_id:"c".into(),model_label:"6 Astra · RC".into(),state:state.into(),saved_characters:0,started_at:at,updated_at:at}}
    #[test] fn checkpoints_survive_terminal_accounting_and_delayed_events() {
        let mut l=Ledger::default();l.update(task("CHAT:a","RUNNING",100));l.checkpoint("CHAT:a",123);
        l.update(task("CHAT:a","RESPONSE_COMMITTED",500));l.update(task("CHAT:a","RUNNING",800));l.checkpoint("CHAT:a",999);
        let t=&l.snapshot()[0];assert_eq!(t.saved_characters,123);assert_eq!(t.started_at,100);assert_eq!(t.updated_at,500);assert!(!t.running());
    }
    #[test] fn retry_and_data_area_have_independent_identities() {
        let mut l=Ledger::default();l.update(task("CHAT:a","UNKNOWN",100));l.update(task("WORK:a","RUNNING",100));l.update(task("CHAT:b","RUNNING",200));
        assert_eq!(l.snapshot().len(),3);assert_eq!(l.snapshot().iter().filter(|t|t.running()).count(),2);
    }
    #[test] fn completed_history_is_bounded_without_evicting_running_tasks() {
        let mut l=Ledger::default();l.update(task("running","RUNNING",0));for n in 0..50 {l.update(task(&format!("done{n}"),"COMPLETED",n));}
        assert_eq!(l.snapshot().len(),9);assert!(l.tasks.contains_key("running"));
        assert!(!serde_json::to_string(&l.snapshot()).unwrap().contains("secret"));
    }
    #[test] fn error_and_panic_release_registration_without_touching_peer_tasks() {
        use std::sync::{Arc,atomic::AtomicBool};
        for panic in [false,true] {
            let registry=Arc::new(Mutex::new(vec![Arc::new(AtomicBool::new(false)),Arc::new(AtomicBool::new(false))]));
            let original=registry.lock().unwrap()[0].clone();let peer=registry.lock().unwrap()[1].clone();
            let cleanup=registry.clone();let own=original.clone();
            let _=std::panic::catch_unwind(move || {
                let _guard=OnFinish::new(move || {cleanup.lock().unwrap().retain(|signal|!Arc::ptr_eq(signal,&own));});
                if panic {panic!("synthetic worker failure");}
                Err::<(),_>("synthetic persistence failure")
            });
            let signals=registry.lock().unwrap();assert_eq!(signals.len(),1);assert!(Arc::ptr_eq(&signals[0],&peer));
        }
    }

}
