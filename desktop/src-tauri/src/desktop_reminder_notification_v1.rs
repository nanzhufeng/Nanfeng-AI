//! Content-safe Desktop reminder notification routing and native click bridges.

use serde::{Deserialize, Serialize};
use std::collections::{BTreeSet, VecDeque};
use std::sync::{Mutex, OnceLock};

pub const ACTION_EVENT_NAME: &str = "desktop-reminder-notification-action-v1";
pub const ROUTE: &str = "desktop-reminder-plan";
pub const SAFE_BODY: &str = "计划监控已完成，点按查看本机结果。";
const MAX_OPAQUE_ID_BYTES: usize = 256;
const MAX_PENDING_ACTIONS: usize = 32;
const MAX_HANDLED_CLICK_IDS: usize = 128;

#[derive(Debug, Clone, Deserialize, Serialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct ReminderNotificationTarget {
    pub route: String,
    pub plan_id: String,
    pub workspace_id: String,
    pub conversation_id: String,
}

#[derive(Debug, Clone, Deserialize, Serialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct ReminderNotificationAction {
    pub click_id: String,
    #[serde(default = "default_chat_area")]
    pub data_area: String,
    pub route: String,
    pub plan_id: String,
    pub workspace_id: String,
    pub conversation_id: String,
}

fn default_chat_area() -> String { "CHAT".into() }

#[derive(Debug, Clone, Serialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct ReminderNotificationBridgeStatus {
    pub supported: bool,
    pub initialized: bool,
    pub safe_code: String,
    pub pending_action_count: usize,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum NotificationSendOutcome {
    Sent,
    Failed,
    Unknown,
}

#[derive(Default)]
struct ActionQueue {
    pending: VecDeque<ReminderNotificationAction>,
    handled: VecDeque<String>,
    handled_index: BTreeSet<String>,
}

impl ActionQueue {
    fn record(&mut self, action: ReminderNotificationAction) -> bool {
        let identity = format!("{}:{}", action.data_area, action.click_id);
        if self.handled_index.contains(&identity) {
            return false;
        }
        self.handled_index.insert(identity.clone());
        self.handled.push_back(identity);
        while self.handled.len() > MAX_HANDLED_CLICK_IDS {
            if let Some(expired) = self.handled.pop_front() {
                self.handled_index.remove(&expired);
            }
        }
        self.pending.push_back(action);
        while self.pending.len() > MAX_PENDING_ACTIONS {
            self.pending.pop_front();
        }
        true
    }

    fn take(&mut self, click_id: &str, data_area: &str) -> Option<ReminderNotificationAction> {
        let index = self
            .pending
            .iter()
            .position(|item| item.click_id == click_id && item.data_area == data_area)?;
        self.pending.remove(index)
    }

    fn drain(&mut self, data_area: &str) -> Vec<ReminderNotificationAction> {
        let mut selected = Vec::new();
        self.pending.retain(|item| {
            if item.data_area == data_area {
                selected.push(item.clone());
                false
            } else {
                true
            }
        });
        selected
    }
}

#[derive(Debug, Clone)]
struct BridgeRuntimeStatus {
    initialized: bool,
    safe_code: String,
}

impl Default for BridgeRuntimeStatus {
    fn default() -> Self {
        Self {
            initialized: false,
            safe_code: if cfg!(any(target_os = "macos", windows)) {
                "NOT_INITIALIZED".into()
            } else {
                "UNSUPPORTED_PLATFORM".into()
            },
        }
    }
}

static ACTION_QUEUE: OnceLock<Mutex<ActionQueue>> = OnceLock::new();
static BRIDGE_STATUS: OnceLock<Mutex<BridgeRuntimeStatus>> = OnceLock::new();
#[cfg(windows)]
static WINDOWS_ACTIVATIONS: OnceLock<Mutex<VecDeque<(String, String)>>> = OnceLock::new();

fn queue() -> &'static Mutex<ActionQueue> {
    ACTION_QUEUE.get_or_init(|| Mutex::new(ActionQueue::default()))
}

fn runtime_status() -> &'static Mutex<BridgeRuntimeStatus> {
    BRIDGE_STATUS.get_or_init(|| Mutex::new(BridgeRuntimeStatus::default()))
}

fn safe_opaque_id(value: &str) -> bool {
    !value.is_empty()
        && value.len() <= MAX_OPAQUE_ID_BYTES
        && value
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'-' | b'_' | b'.' | b':'))
}

pub fn validate_target(target: &ReminderNotificationTarget) -> bool {
    target.route == ROUTE
        && safe_opaque_id(&target.plan_id)
        && safe_opaque_id(&target.workspace_id)
        && (target.conversation_id.is_empty() || safe_opaque_id(&target.conversation_id))
}

fn validate_action(action: &ReminderNotificationAction) -> bool {
    safe_opaque_id(&action.click_id)
        && matches!(action.data_area.as_str(), "CHAT" | "WORK")
        && validate_target(&ReminderNotificationTarget {
            route: action.route.clone(),
            plan_id: action.plan_id.clone(),
            workspace_id: action.workspace_id.clone(),
            conversation_id: action.conversation_id.clone(),
        })
}

pub fn status() -> ReminderNotificationBridgeStatus {
    let status = runtime_status().lock().ok();
    let pending_action_count = queue().lock().map(|queue| queue.pending.len()).unwrap_or(0);
    ReminderNotificationBridgeStatus {
        supported: cfg!(any(target_os = "macos", windows)),
        initialized: status.as_ref().is_some_and(|value| value.initialized),
        safe_code: status
            .as_ref()
            .map(|value| value.safe_code.clone())
            .unwrap_or_else(|| "STATUS_LOCK_FAILED".into()),
        pending_action_count,
    }
}

fn update_status(initialized: bool, safe_code: &str) {
    if let Ok(mut status) = runtime_status().lock() {
        status.initialized = initialized;
        status.safe_code = safe_code.to_owned();
    }
}

#[cfg(any(target_os = "macos", test))]
fn record_action(action: ReminderNotificationAction) -> bool {
    if !validate_action(&action) {
        return false;
    }
    queue().lock().is_ok_and(|mut queue| queue.record(action))
}

pub fn take_action(click_id: &str, data_area: &str) -> Option<ReminderNotificationAction> {
    if !safe_opaque_id(click_id) {
        return None;
    }
    queue().lock().ok()?.take(click_id, data_area)
}

pub fn drain_actions(data_area: &str) -> Vec<ReminderNotificationAction> {
    queue()
        .lock()
        .map(|mut queue| queue.drain(data_area))
        .unwrap_or_default()
}

#[cfg(windows)]
fn windows_activations() -> &'static Mutex<VecDeque<(String, String)>> {
    WINDOWS_ACTIVATIONS.get_or_init(|| Mutex::new(VecDeque::new()))
}

#[cfg(windows)]
fn activation_scheme(app_id: &str) -> Option<&'static str> {
    match app_id {
        "com.nanzhufeng.ai.desktop" => Some("nanfeng-ai-reminder"),
        "com.nanzhufeng.ai.desktop.windowsacceptance" =>
            Some("nanfeng-ai-windows-acceptance-reminder"),
        _ => None,
    }
}

#[cfg(windows)]
pub fn windows_activation_url(app_id: &str, data_area: &str, run_id: &str) -> Option<String> {
    if !matches!(data_area, "CHAT" | "WORK") || !safe_opaque_id(run_id) || run_id.len() > 200 {
        return None;
    }
    Some(format!("{}://open/{data_area}/{run_id}", activation_scheme(app_id)?))
}

#[cfg(windows)]
pub fn parse_windows_activation_args(args: &[String], app_id: &str) -> Option<(String, String)> {
    let url = match args {
        [_, url] => url,
        [_, isolated, visible, url]
            if app_id == "com.nanzhufeng.ai.desktop.windowsacceptance"
                && isolated == "--windows-isolated-normal-startup"
                && visible == "--windows-acceptance-visible" => url,
        _ => return None,
    };
    let prefix = format!("{}://open/", activation_scheme(app_id)?);
    let (area, run_id) = url.strip_prefix(&prefix)?.split_once('/')?;
    if !matches!(area, "CHAT" | "WORK") || !safe_opaque_id(run_id) || run_id.len() > 200 {
        return None;
    }
    Some((area.to_owned(), run_id.to_owned()))
}

#[cfg(windows)]
pub fn record_windows_activation(app: &tauri::AppHandle, args: &[String]) -> bool {
    use tauri::Emitter;
    let Some((area, run_id)) = parse_windows_activation_args(args, &app.config().identifier) else {
        return false;
    };
    let Ok(mut pending) = windows_activations().lock() else { return false };
    if !pending.iter().any(|item| item.0 == area && item.1 == run_id) {
        pending.push_back((area.clone(), run_id.clone()));
        while pending.len() > MAX_PENDING_ACTIONS {
            pending.pop_front();
        }
    }
    drop(pending);
    let _ = app.emit(ACTION_EVENT_NAME, serde_json::json!({"clickId":run_id,"dataArea":area}));
    true
}

#[cfg(windows)]
pub fn pending_windows_activation_area() -> Option<String> {
    windows_activations().lock().ok()?.front().map(|item| item.0.clone())
}

#[cfg(windows)]
pub fn take_windows_activation(run_id: &str, data_area: &str) -> bool {
    let Ok(mut pending) = windows_activations().lock() else { return false };
    let Some(index) = pending.iter().position(|item| item.0 == data_area && item.1 == run_id) else {
        return false;
    };
    pending.remove(index);
    true
}

#[cfg(windows)]
pub fn drain_windows_activations(data_area: &str) -> Vec<String> {
    let Ok(mut pending) = windows_activations().lock() else { return Vec::new() };
    let mut selected = Vec::new();
    pending.retain(|item| {
        if item.0 == data_area {
            selected.push(item.1.clone());
            false
        } else { true }
    });
    selected
}

#[cfg(windows)]
mod windows_bridge {
    use super::*;
    use windows::core::HSTRING;
    use windows::Data::Xml::Dom::XmlDocument;
    use windows::UI::Notifications::{NotificationSetting, ToastNotification, ToastNotificationManager};
    use windows::Win32::Foundation::RPC_E_CHANGED_MODE;
    use windows::Win32::System::WinRT::{RoInitialize, RoUninitialize, RO_INIT_MULTITHREADED, RO_INIT_SINGLETHREADED};

    // Scope only the successful initialization performed by this call.
    struct WinRtApartment(std::marker::PhantomData<std::rc::Rc<()>>);
    impl Drop for WinRtApartment {
        fn drop(&mut self) { unsafe { RoUninitialize() }; }
    }
    fn initialize_apartment() -> windows::core::Result<WinRtApartment> {
        match unsafe { RoInitialize(RO_INIT_MULTITHREADED) } {
            Ok(()) => {},
            Err(error) if error.code() == RPC_E_CHANGED_MODE => {
                // A UI caller may already own a classic STA. Initialize WinRT with
                // that compatible model instead of treating the caller as unavailable.
                unsafe { RoInitialize(RO_INIT_SINGLETHREADED) }?;
            },
            Err(error) => return Err(error),
        }
        // Both S_OK and S_FALSE count as one successful call to balance on this thread.
        Ok(WinRtApartment(std::marker::PhantomData))
    }

    #[cfg(test)]
    mod apartment_tests {
        use super::*;
        use windows::Win32::Foundation::RPC_E_CHANGED_MODE;
        use windows::Win32::System::Com::{CoInitializeEx, CoUninitialize, COINIT_APARTMENTTHREADED, COINIT_MULTITHREADED};
        struct ExistingCom;
        impl Drop for ExistingCom { fn drop(&mut self) { unsafe { CoUninitialize() }; } }
        #[test]
        fn sta_caller_can_initialize_and_retains_its_com_apartment() {
            std::thread::spawn(|| {
                unsafe { CoInitializeEx(None, COINIT_APARTMENTTHREADED) }.ok().unwrap();
                let _existing = ExistingCom;
                assert_eq!(unsafe { RoInitialize(RO_INIT_MULTITHREADED) }.unwrap_err().code(), RPC_E_CHANGED_MODE);
                let apartment = initialize_apartment().expect("STA caller must reach notification APIs");
                drop(apartment);
                assert_eq!(unsafe { RoInitialize(RO_INIT_MULTITHREADED) }.unwrap_err().code(), RPC_E_CHANGED_MODE,
                    "Notification scope must not uninitialize the caller's STA");
            }).join().unwrap();
        }
        #[test]
        fn mta_caller_keeps_its_existing_com_apartment_after_nested_scopes() {
            std::thread::spawn(|| {
                unsafe { CoInitializeEx(None, COINIT_MULTITHREADED) }.ok().unwrap();
                let _existing = ExistingCom;
                let first = initialize_apartment().unwrap();
                let second = initialize_apartment().unwrap();
                drop(second); drop(first);
                assert_eq!(unsafe { CoInitializeEx(None, COINIT_APARTMENTTHREADED) }, RPC_E_CHANGED_MODE,
                    "Each successful WinRT scope must release only its own initialization count");
            }).join().unwrap();
        }
        #[test]
        fn fresh_thread_can_reinitialize_sta_after_the_winrt_scope_ends() {
            std::thread::spawn(|| {
                drop(initialize_apartment().unwrap());
                unsafe { CoInitializeEx(None, COINIT_APARTMENTTHREADED) }.ok().unwrap();
                let _existing = ExistingCom;
            }).join().unwrap();
        }
    }

    static APP_ID: OnceLock<String> = OnceLock::new();

    pub fn install(app: &tauri::AppHandle) -> Result<(), &'static str> {
        APP_ID.set(app.config().identifier.clone()).map_err(|_| "BRIDGE_ALREADY_INSTALLED")?;
        update_status(true, "HOT_CLICK_READY");
        Ok(())
    }

    pub fn permission_state() -> Result<String, &'static str> {
        let _apartment = initialize_apartment().map_err(|_| "PERMISSION_APARTMENT_UNAVAILABLE")?;
        let app_id = APP_ID.get().ok_or("BRIDGE_NOT_INITIALIZED")?;
        let notifier = ToastNotificationManager::CreateToastNotifierWithId(&HSTRING::from(app_id))
            .map_err(|_| "PERMISSION_NOTIFIER_UNAVAILABLE")?;
        let setting = notifier.Setting().map_err(|_| "PERMISSION_READ_UNKNOWN")?;
        Ok(if setting == NotificationSetting::Enabled { "granted" } else { "denied" }.into())
    }

    pub fn send(
        _app: &tauri::AppHandle,
        run_id: &str,
        title: &str,
        body: &str,
        target: &ReminderNotificationTarget,
        data_area: &str,
    ) -> NotificationSendOutcome {
        if !matches!(data_area, "CHAT" | "WORK")
            || !safe_opaque_id(run_id)
            || title.trim().is_empty()
            || title.len() > 160
            || body != SAFE_BODY
            || !validate_target(target)
        {
            return NotificationSendOutcome::Failed;
        }
        let permission = permission_state();
        if matches!(permission.as_deref(), Ok("denied")) {
            return NotificationSendOutcome::Failed;
        }
        let Some(app_id) = APP_ID.get() else { return NotificationSendOutcome::Failed };
        let Some(launch) = windows_activation_url(app_id, data_area, run_id) else {
            return NotificationSendOutcome::Failed;
        };
        let Ok(_apartment) = initialize_apartment() else { return NotificationSendOutcome::Failed };
        let xml = format!(
            "<toast activationType=\"protocol\" launch=\"{launch}\"><visual><binding template=\"ToastGeneric\"><text>{}</text><text>{}</text></binding></visual></toast>",
            quick_xml::escape::escape(title), quick_xml::escape::escape(body),
        );
        let delivered = (|| -> windows::core::Result<()> {
            let document = XmlDocument::new()?;
            document.LoadXml(&HSTRING::from(xml))?;
            let toast = ToastNotification::CreateToastNotification(&document)?;
            toast.SetTag(&HSTRING::from(run_id))?;
            toast.SetGroup(&HSTRING::from("nanfeng-reminders-v1"))?;
            let notifier = ToastNotificationManager::CreateToastNotifierWithId(&HSTRING::from(app_id))?;
            notifier.Show(&toast)
        })();
        if delivered.is_err() { return NotificationSendOutcome::Failed; }
        if permission.is_err() {
            NotificationSendOutcome::Unknown
        } else {
            NotificationSendOutcome::Sent
        }
    }
}

#[cfg(target_os = "macos")]
#[allow(deprecated)]
mod macos {
    use super::*;
    use block2::RcBlock;
    use objc2::rc::Retained;
    use objc2::runtime::{AnyObject, Bool, ProtocolObject};
    use objc2::{define_class, msg_send, AnyThread};
    use objc2_foundation::{
        NSDictionary, NSError, NSObject, NSObjectProtocol, NSString, NSUserNotification,
        NSUserNotificationActivationType, NSUserNotificationCenter,
        NSUserNotificationCenterDelegate, NSUserNotificationDefaultSoundName,
    };
    use objc2_user_notifications::{
        UNAuthorizationOptions, UNAuthorizationStatus, UNMutableNotificationContent,
        UNNotification, UNNotificationDefaultActionIdentifier, UNNotificationPresentationOptions,
        UNNotificationRequest, UNNotificationResponse, UNNotificationSettings,
        UNUserNotificationCenter, UNUserNotificationCenterDelegate,
    };
    use std::collections::BTreeMap;
    use std::ptr::NonNull;
    use std::sync::mpsc;
    use std::time::Duration;
    use tauri::{AppHandle, Emitter};

    static APP_HANDLE: OnceLock<AppHandle> = OnceLock::new();
    static DELIVERY_RECEIPTS: OnceLock<Mutex<BTreeMap<String, mpsc::SyncSender<()>>>> =
        OnceLock::new();

    fn delivery_receipts() -> &'static Mutex<BTreeMap<String, mpsc::SyncSender<()>>> {
        DELIVERY_RECEIPTS.get_or_init(|| Mutex::new(BTreeMap::new()))
    }

    define_class!(
        #[unsafe(super(NSObject))]
        #[name = "NanfengModernReminderNotificationDelegateV1"]
        struct ModernReminderNotificationDelegate;

        unsafe impl NSObjectProtocol for ModernReminderNotificationDelegate {}

        unsafe impl UNUserNotificationCenterDelegate for ModernReminderNotificationDelegate {
            #[unsafe(method(userNotificationCenter:willPresentNotification:withCompletionHandler:))]
            fn will_present(
                &self,
                _center: &UNUserNotificationCenter,
                _notification: &UNNotification,
                completion_handler: &block2::DynBlock<dyn Fn(UNNotificationPresentationOptions)>,
            ) {
                completion_handler.call((UNNotificationPresentationOptions::Banner
                    | UNNotificationPresentationOptions::List
                    | UNNotificationPresentationOptions::Sound,));
            }

            #[unsafe(method(userNotificationCenter:didReceiveNotificationResponse:withCompletionHandler:))]
            fn did_receive(
                &self,
                _center: &UNUserNotificationCenter,
                response: &UNNotificationResponse,
                completion_handler: &block2::DynBlock<dyn Fn()>,
            ) {
                let action_identifier = response.actionIdentifier();
                if &*action_identifier == unsafe { UNNotificationDefaultActionIdentifier } {
                    if let Some(action) = action_from_modern_response(response) {
                        emit_action(action);
                    }
                }
                completion_handler.call(());
            }
        }
    );

    define_class!(
        #[unsafe(super(NSObject))]
        #[name = "NanfengReminderNotificationDelegateV1"]
        struct ReminderNotificationDelegate;

        unsafe impl NSObjectProtocol for ReminderNotificationDelegate {}

        unsafe impl NSUserNotificationCenterDelegate for ReminderNotificationDelegate {
            #[unsafe(method(userNotificationCenter:didDeliverNotification:))]
            fn did_deliver(
                &self,
                _center: &NSUserNotificationCenter,
                notification: &NSUserNotification,
            ) {
                let Some(identifier) = notification.identifier().map(|value| value.to_string())
                else {
                    return;
                };
                let sender = delivery_receipts()
                    .lock()
                    .ok()
                    .and_then(|mut receipts| receipts.remove(&identifier));
                if let Some(sender) = sender {
                    let _ = sender.send(());
                }
            }

            #[unsafe(method(userNotificationCenter:didActivateNotification:))]
            fn did_activate(
                &self,
                _center: &NSUserNotificationCenter,
                notification: &NSUserNotification,
            ) {
                let activation_type = notification.activationType();
                if activation_type == NSUserNotificationActivationType::ContentsClicked
                    || activation_type == NSUserNotificationActivationType::ActionButtonClicked
                {
                    if let Some(action) = action_from_notification(notification) {
                        emit_action(action);
                    }
                }
            }

            #[unsafe(method(userNotificationCenter:shouldPresentNotification:))]
            fn should_present(
                &self,
                _center: &NSUserNotificationCenter,
                _notification: &NSUserNotification,
            ) -> bool {
                true
            }
        }
    );

    fn emit_action(action: ReminderNotificationAction) {
        if record_action(action.clone()) {
            if let Some(app) = APP_HANDLE.get() {
                let _ = app.emit(ACTION_EVENT_NAME, action);
            }
        }
    }

    fn modern_dictionary_string(dictionary: &NSDictionary, key: &str) -> Option<String> {
        let key = NSString::from_str(key);
        let object = dictionary.objectForKey(&*key as &AnyObject)?;
        Some(object.downcast_ref::<NSString>()?.to_string())
    }

    fn action_from_modern_response(
        response: &UNNotificationResponse,
    ) -> Option<ReminderNotificationAction> {
        let request = response.notification().request();
        let user_info = request.content().userInfo();
        let action = ReminderNotificationAction {
            click_id: request.identifier().to_string(),
            data_area: default_chat_area(),
            route: modern_dictionary_string(&user_info, "route")?,
            plan_id: modern_dictionary_string(&user_info, "planId")?,
            workspace_id: modern_dictionary_string(&user_info, "workspaceId")?,
            conversation_id: modern_dictionary_string(&user_info, "conversationId")
                .unwrap_or_default(),
        };
        validate_action(&action).then_some(action)
    }

    fn dictionary_string(
        dictionary: &NSDictionary<NSString, AnyObject>,
        key: &str,
    ) -> Option<String> {
        let key = NSString::from_str(key);
        let object = dictionary.objectForKey(&key)?;
        let value = object.downcast_ref::<NSString>()?.to_string();
        Some(value)
    }

    fn action_from_notification(
        notification: &NSUserNotification,
    ) -> Option<ReminderNotificationAction> {
        let click_id = notification.identifier()?.to_string();
        let user_info = notification.userInfo()?;
        let action = ReminderNotificationAction {
            click_id,
            data_area: default_chat_area(),
            route: dictionary_string(&user_info, "route")?,
            plan_id: dictionary_string(&user_info, "planId")?,
            workspace_id: dictionary_string(&user_info, "workspaceId")?,
            conversation_id: dictionary_string(&user_info, "conversationId").unwrap_or_default(),
        };
        validate_action(&action).then_some(action)
    }

    pub fn install(app: &AppHandle) -> Result<(), &'static str> {
        let _ = APP_HANDLE.set(app.clone());
        let modern_center = UNUserNotificationCenter::currentNotificationCenter();
        let modern_delegate: Retained<ModernReminderNotificationDelegate> =
            unsafe { msg_send![ModernReminderNotificationDelegate::alloc(), init] };
        modern_center.setDelegate(Some(ProtocolObject::from_ref(&*modern_delegate)));
        let _ = Retained::into_raw(modern_delegate);

        let center = NSUserNotificationCenter::defaultUserNotificationCenter();
        let delegate: Retained<ReminderNotificationDelegate> =
            unsafe { msg_send![ReminderNotificationDelegate::alloc(), init] };
        let protocol = ProtocolObject::from_ref(&*delegate);
        unsafe { center.setDelegate(Some(protocol)) };
        let _ = Retained::into_raw(delegate);
        update_status(true, "READY");
        Ok(())
    }

    pub fn permission_state() -> Result<String, &'static str> {
        let center = UNUserNotificationCenter::currentNotificationCenter();
        let (sender, receiver) = mpsc::sync_channel(1);
        let completion = RcBlock::new(move |settings: NonNull<UNNotificationSettings>| {
            let value = unsafe { settings.as_ref() }.authorizationStatus();
            let state = if value == UNAuthorizationStatus::Authorized
                || value == UNAuthorizationStatus::Provisional
                || value == UNAuthorizationStatus::Ephemeral
            {
                "granted"
            } else if value == UNAuthorizationStatus::Denied {
                "denied"
            } else {
                "default"
            };
            let _ = sender.send(state.to_owned());
        });
        center.getNotificationSettingsWithCompletionHandler(&completion);
        receiver
            .recv_timeout(Duration::from_secs(5))
            .map_err(|_| "PERMISSION_READ_UNKNOWN")
    }

    pub fn request_permission() -> Result<String, &'static str> {
        let center = UNUserNotificationCenter::currentNotificationCenter();
        let (sender, receiver) = mpsc::sync_channel(1);
        let completion = RcBlock::new(move |granted: Bool, error: *mut NSError| {
            // An ad-hoc development bundle is rejected by modern UN registration. The legacy
            // owner remains available and lets macOS present its own authorization prompt.
            let state = if !error.is_null() {
                "legacy"
            } else if granted.as_bool() {
                "granted"
            } else {
                "denied"
            };
            let _ = sender.send(state.to_owned());
        });
        center.requestAuthorizationWithOptions_completionHandler(
            UNAuthorizationOptions::Alert
                | UNAuthorizationOptions::Sound
                | UNAuthorizationOptions::Badge,
            &completion,
        );
        receiver
            .recv_timeout(Duration::from_secs(10))
            .map_err(|_| "PERMISSION_REQUEST_UNKNOWN")
    }

    fn legacy_send(
        run_id: &str,
        title: &str,
        body: &str,
        target: &ReminderNotificationTarget,
    ) -> NotificationSendOutcome {
        if !safe_opaque_id(run_id)
            || title.trim().is_empty()
            || title.len() > 160
            || body != SAFE_BODY
            || !validate_target(target)
        {
            return NotificationSendOutcome::Failed;
        }
        let keys = [
            NSString::from_str("route"),
            NSString::from_str("planId"),
            NSString::from_str("workspaceId"),
            NSString::from_str("conversationId"),
        ];
        let values = [
            NSString::from_str(&target.route),
            NSString::from_str(&target.plan_id),
            NSString::from_str(&target.workspace_id),
            NSString::from_str(&target.conversation_id),
        ];
        let string_dictionary: Retained<NSDictionary<NSString, NSString>> =
            NSDictionary::from_slices(
                &[&*keys[0], &*keys[1], &*keys[2], &*keys[3]],
                &[&*values[0], &*values[1], &*values[2], &*values[3]],
            );
        // Foundation declares this as NSString -> AnyObject. All values are retained NSStrings.
        let user_info: &NSDictionary<NSString, AnyObject> = unsafe {
            &*((&*string_dictionary as *const NSDictionary<NSString, NSString>)
                .cast::<NSDictionary<NSString, AnyObject>>())
        };
        let notification = NSUserNotification::new();
        notification.setIdentifier(Some(&NSString::from_str(run_id)));
        notification.setTitle(Some(&NSString::from_str(title)));
        notification.setInformativeText(Some(&NSString::from_str(body)));
        notification.setSoundName(Some(unsafe { NSUserNotificationDefaultSoundName }));
        unsafe { notification.setUserInfo(Some(user_info)) };

        let (sender, receiver) = mpsc::sync_channel(1);
        let Ok(mut receipts) = delivery_receipts().lock() else {
            return NotificationSendOutcome::Unknown;
        };
        receipts.insert(run_id.to_owned(), sender);
        drop(receipts);

        NSUserNotificationCenter::defaultUserNotificationCenter()
            .deliverNotification(&notification);
        let outcome = match receiver.recv_timeout(Duration::from_secs(2)) {
            Ok(()) => NotificationSendOutcome::Sent,
            Err(_) => NotificationSendOutcome::Unknown,
        };
        if let Ok(mut receipts) = delivery_receipts().lock() {
            receipts.remove(run_id);
        }
        outcome
    }

    fn modern_send(
        run_id: &str,
        title: &str,
        body: &str,
        target: &ReminderNotificationTarget,
    ) -> NotificationSendOutcome {
        let keys = [
            NSString::from_str("route"),
            NSString::from_str("planId"),
            NSString::from_str("workspaceId"),
            NSString::from_str("conversationId"),
        ];
        let values = [
            NSString::from_str(&target.route),
            NSString::from_str(&target.plan_id),
            NSString::from_str(&target.workspace_id),
            NSString::from_str(&target.conversation_id),
        ];
        let user_info = NSDictionary::from_slices(
            &[&*keys[0], &*keys[1], &*keys[2], &*keys[3]],
            &[&*values[0], &*values[1], &*values[2], &*values[3]],
        );
        let content = UNMutableNotificationContent::new();
        content.setTitle(&NSString::from_str(title));
        content.setBody(&NSString::from_str(body));
        content.setThreadIdentifier(&NSString::from_str("nanfeng-reminders-v1"));
        let erased_user_info: &NSDictionary = unsafe {
            &*((&*user_info as *const NSDictionary<NSString, NSString>).cast::<NSDictionary>())
        };
        unsafe { content.setUserInfo(erased_user_info) };
        let request = UNNotificationRequest::requestWithIdentifier_content_trigger(
            &NSString::from_str(run_id),
            &content,
            None,
        );
        let (sender, receiver) = mpsc::sync_channel(1);
        let completion = RcBlock::new(move |error: *mut NSError| {
            let _ = sender.send(error.is_null());
        });
        UNUserNotificationCenter::currentNotificationCenter()
            .addNotificationRequest_withCompletionHandler(&request, Some(&completion));
        match receiver.recv_timeout(Duration::from_secs(5)) {
            Ok(true) => NotificationSendOutcome::Sent,
            Ok(false) => NotificationSendOutcome::Failed,
            Err(_) => NotificationSendOutcome::Unknown,
        }
    }

    pub fn send(
        run_id: &str,
        title: &str,
        body: &str,
        target: &ReminderNotificationTarget,
    ) -> NotificationSendOutcome {
        if !safe_opaque_id(run_id)
            || title.trim().is_empty()
            || title.len() > 160
            || body != SAFE_BODY
            || !validate_target(target)
        {
            return NotificationSendOutcome::Failed;
        }
        match modern_send(run_id, title, body, target) {
            NotificationSendOutcome::Failed => legacy_send(run_id, title, body, target),
            outcome => outcome,
        }
    }
}

pub fn install(app: &tauri::AppHandle) -> Result<(), &'static str> {
    #[cfg(target_os = "macos")]
    {
        macos::install(app)
    }
    #[cfg(windows)]
    {
        windows_bridge::install(app)
    }
    #[cfg(not(any(target_os = "macos", windows)))]
    {
        let _ = app;
        update_status(false, "UNSUPPORTED_PLATFORM");
        Ok(())
    }
}

pub fn permission_state() -> Result<String, &'static str> {
    #[cfg(target_os = "macos")]
    {
        macos::permission_state()
    }
    #[cfg(windows)]
    {
        windows_bridge::permission_state()
    }
    #[cfg(not(any(target_os = "macos", windows)))]
    {
        Ok("unavailable".into())
    }
}

pub fn request_permission() -> Result<String, &'static str> {
    #[cfg(target_os = "macos")]
    {
        macos::request_permission()
    }
    #[cfg(windows)]
    {
        windows_bridge::permission_state()
    }
    #[cfg(not(any(target_os = "macos", windows)))]
    {
        Ok("unavailable".into())
    }
}

pub fn send(
    app: &tauri::AppHandle,
    run_id: &str,
    title: &str,
    body: &str,
    target: &ReminderNotificationTarget,
    data_area: &str,
) -> NotificationSendOutcome {
    #[cfg(target_os = "macos")]
    {
        let _ = (app, data_area);
        macos::send(run_id, title, body, target)
    }
    #[cfg(windows)]
    {
        windows_bridge::send(app, run_id, title, body, target, data_area)
    }
    #[cfg(not(any(target_os = "macos", windows)))]
    {
        let _ = data_area;
        use tauri_plugin_notification::NotificationExt;
        if !safe_opaque_id(run_id) || body != SAFE_BODY || !validate_target(target) {
            return NotificationSendOutcome::Failed;
        }
        match app.notification().builder().title(title).body(body).show() {
            Ok(()) => NotificationSendOutcome::Sent,
            Err(_) => NotificationSendOutcome::Failed,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn action(click_id: &str) -> ReminderNotificationAction {
        ReminderNotificationAction {
            click_id: click_id.into(),
            data_area: "CHAT".into(),
            route: ROUTE.into(),
            plan_id: "plan-safe".into(),
            workspace_id: "workspace-safe".into(),
            conversation_id: "conversation-safe".into(),
        }
    }

    #[test]
    fn payload_whitelist_rejects_bad_routes_and_non_opaque_ids() {
        let valid = action("run-safe");
        assert!(validate_action(&valid));
        assert!(!validate_action(&ReminderNotificationAction {
            route: "desktop-conversation".into(),
            ..valid.clone()
        }));
        assert!(!validate_action(&ReminderNotificationAction {
            plan_id: "../../private".into(),
            ..valid.clone()
        }));
        let serialized = serde_json::to_string(&valid).unwrap();
        for forbidden in ["body", "prompt", "path", "provider", "key", "attachment"] {
            assert!(!serialized.to_ascii_lowercase().contains(forbidden));
        }
    }

    #[test]
    fn action_queue_handles_multiple_clicks_and_deduplicates_repeats() {
        let mut queue = ActionQueue::default();
        assert!(queue.record(action("run-1")));
        assert!(queue.record(action("run-2")));
        assert!(!queue.record(action("run-1")));
        assert_eq!(queue.take("run-2", "CHAT").unwrap().click_id, "run-2");
        assert_eq!(queue.drain("CHAT"), vec![action("run-1")]);
    }

    #[test]
    fn notification_actions_keep_their_data_area_until_that_area_consumes_them() {
        let mut queue = ActionQueue::default();
        let mut work = action("work-run");
        work.data_area = "WORK".into();
        assert!(queue.record(work.clone()));
        assert!(queue.take("work-run", "CHAT").is_none());
        assert!(queue.drain("CHAT").is_empty());
        assert_eq!(queue.drain("WORK"), vec![work]);
        assert!(queue.record(action("shared-id")));
        let mut work_same_id = action("shared-id");
        work_same_id.data_area = "WORK".into();
        assert!(queue.record(work_same_id));
    }

    #[cfg(windows)]
    #[test]
    fn windows_activation_accepts_only_its_exact_opaque_run_and_area() {
        let app_id = "com.nanzhufeng.ai.desktop.windowsacceptance";
        let url = windows_activation_url(app_id, "WORK", "reminder-run-1").unwrap();
        let args = vec!["app.exe".into(), url.clone()];
        assert_eq!(parse_windows_activation_args(&args, app_id), Some(("WORK".into(), "reminder-run-1".into())));
        for bad in [
            url.replace("/WORK/", "/OTHER/"),
            url.replace("reminder-run-1", "../private"),
            format!("{url}?path=private"),
            url.replace("windows-acceptance-reminder", "reminder"),
        ] {
            assert!(parse_windows_activation_args(&["app.exe".into(), bad], app_id).is_none());
        }
        assert!(parse_windows_activation_args(&["app.exe".into(), url, "extra".into()], app_id).is_none());
        assert_eq!(parse_windows_activation_args(&["app.exe".into(), "--windows-isolated-normal-startup".into(), "--windows-acceptance-visible".into(), windows_activation_url(app_id, "CHAT", "run-2").unwrap()], app_id), Some(("CHAT".into(), "run-2".into())));
        assert!(windows_activation_url(app_id, "CHAT", "run/other").is_none());
    }
}
