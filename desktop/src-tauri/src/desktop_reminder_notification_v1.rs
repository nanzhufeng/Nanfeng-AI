//! Content-safe Desktop reminder notification routing and the macOS native click bridge.

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
    pub route: String,
    pub plan_id: String,
    pub workspace_id: String,
    pub conversation_id: String,
}

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
        if self.handled_index.contains(&action.click_id) {
            return false;
        }
        self.handled_index.insert(action.click_id.clone());
        self.handled.push_back(action.click_id.clone());
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

    fn take(&mut self, click_id: &str) -> Option<ReminderNotificationAction> {
        let index = self
            .pending
            .iter()
            .position(|item| item.click_id == click_id)?;
        self.pending.remove(index)
    }

    fn drain(&mut self) -> Vec<ReminderNotificationAction> {
        self.pending.drain(..).collect()
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
            safe_code: if cfg!(target_os = "macos") {
                "NOT_INITIALIZED".into()
            } else {
                "UNSUPPORTED_PLATFORM".into()
            },
        }
    }
}

static ACTION_QUEUE: OnceLock<Mutex<ActionQueue>> = OnceLock::new();
static BRIDGE_STATUS: OnceLock<Mutex<BridgeRuntimeStatus>> = OnceLock::new();

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
        supported: cfg!(target_os = "macos"),
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

fn record_action(action: ReminderNotificationAction) -> bool {
    if !validate_action(&action) {
        return false;
    }
    queue().lock().is_ok_and(|mut queue| queue.record(action))
}

pub fn take_action(click_id: &str) -> Option<ReminderNotificationAction> {
    if !safe_opaque_id(click_id) {
        return None;
    }
    queue().lock().ok()?.take(click_id)
}

pub fn drain_actions() -> Vec<ReminderNotificationAction> {
    queue()
        .lock()
        .map(|mut queue| queue.drain())
        .unwrap_or_default()
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
    #[cfg(not(target_os = "macos"))]
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
    #[cfg(not(target_os = "macos"))]
    {
        Ok("unavailable".into())
    }
}

pub fn request_permission() -> Result<String, &'static str> {
    #[cfg(target_os = "macos")]
    {
        macos::request_permission()
    }
    #[cfg(not(target_os = "macos"))]
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
) -> NotificationSendOutcome {
    #[cfg(target_os = "macos")]
    {
        let _ = app;
        macos::send(run_id, title, body, target)
    }
    #[cfg(not(target_os = "macos"))]
    {
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
        assert_eq!(queue.take("run-2").unwrap().click_id, "run-2");
        assert_eq!(queue.drain(), vec![action("run-1")]);
    }
}
