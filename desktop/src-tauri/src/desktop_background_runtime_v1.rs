//! macOS background wake-up owner for confirmed reminders and history curation.
//!
//! The agent never receives conversation text, credentials, provider payloads, or arbitrary
//! commands. It can only relaunch the current executable with one fixed background-cycle flag;
//! SQLite owners re-check eligibility and authorization after every wake-up.

use rusqlite::{params, Connection, OptionalExtension, Transaction};
use serde::{Deserialize, Serialize};
use std::fs;
use std::path::{Path, PathBuf};

pub const BACKGROUND_CYCLE_ARGUMENT: &str = "--nanfeng-background-cycle-v1";
pub const BACKGROUND_APP_ROOT_ENV: &str = "NANFENG_AI_DESKTOP_BACKGROUND_ROOT_V1";
pub const BACKGROUND_APP_ROOT_ARGUMENT: &str = "--nanfeng-background-root-v1=";

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct Projection {
    pub desired_enabled: bool,
    pub installed: bool,
    pub safe_code: String,
    pub label: String,
    pub updated_at_ms: i64,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct LaunchAgentSpec {
    pub label: String,
    pub executable: PathBuf,
    pub app_root: PathBuf,
    pub acceptance_endpoint: Option<String>,
}

pub fn migrate(transaction: &Transaction<'_>) -> Result<(), String> {
    transaction
        .execute_batch(
            "CREATE TABLE IF NOT EXISTS desktop_background_runtime_v1 (
                id INTEGER PRIMARY KEY CHECK(id=1),
                desired_enabled INTEGER NOT NULL,
                installed INTEGER NOT NULL,
                safe_code TEXT NOT NULL,
                label TEXT NOT NULL,
                updated_at_ms INTEGER NOT NULL
            );
            INSERT OR IGNORE INTO desktop_background_runtime_v1(
                id,desired_enabled,installed,safe_code,label,updated_at_ms
            ) VALUES(1,0,0,'NOT_CONFIGURED','',0);",
        )
        .map_err(|_| "Desktop 后台运行迁移失败".to_owned())
}

pub fn desired_enabled(connection: &Connection) -> Result<bool, String> {
    let active_reminder = connection
        .query_row(
            "SELECT EXISTS(
                SELECT 1 FROM desktop_reminder_plans_v1
                WHERE status IN ('ACTIVE','RUNNING')
            )",
            [],
            |row| row.get::<_, i64>(0),
        )
        .map_err(|_| "后台提醒计划状态无法读取".to_owned())?
        != 0;
    let history_enabled = connection
        .query_row(
            "SELECT history_library_enabled FROM desktop_app_settings WHERE id=1",
            [],
            |row| row.get::<_, i64>(0),
        )
        .optional()
        .map_err(|_| "后台历史资料设置无法读取".to_owned())?
        .unwrap_or(0)
        != 0;
    Ok(active_reminder || history_enabled)
}

pub fn read_projection(connection: &Connection) -> Result<Projection, String> {
    connection
        .query_row(
            "SELECT desired_enabled,installed,safe_code,label,updated_at_ms
             FROM desktop_background_runtime_v1 WHERE id=1",
            [],
            |row| {
                Ok(Projection {
                    desired_enabled: row.get::<_, i64>(0)? != 0,
                    installed: row.get::<_, i64>(1)? != 0,
                    safe_code: row.get(2)?,
                    label: row.get(3)?,
                    updated_at_ms: row.get(4)?,
                })
            },
        )
        .map_err(|_| "Desktop 后台运行状态无法读取".to_owned())
}

pub fn record_projection(
    connection: &Connection,
    desired: bool,
    installed: bool,
    safe_code: &str,
    label: &str,
    now_ms: i64,
) -> Result<Projection, String> {
    if !matches!(
        safe_code,
        "INSTALLED" | "NOT_REQUIRED" | "UNSUPPORTED" | "INSTALL_FAILED" | "REMOVE_FAILED"
    ) {
        return Err("Desktop 后台运行状态码无效".to_owned());
    }
    connection
        .execute(
            "UPDATE desktop_background_runtime_v1
             SET desired_enabled=?1,installed=?2,safe_code=?3,label=?4,updated_at_ms=?5
             WHERE id=1",
            params![
                i64::from(desired),
                i64::from(installed),
                safe_code,
                label,
                now_ms
            ],
        )
        .map_err(|_| "Desktop 后台运行状态无法保存".to_owned())?;
    read_projection(connection)
}

pub fn launch_agent_label(identifier: &str) -> Result<String, String> {
    if identifier.is_empty() || identifier.len() > 180 {
        return Err("Desktop Bundle ID 无法用于后台运行".to_owned());
    }
    if !identifier
        .chars()
        .all(|value| value.is_ascii_alphanumeric() || matches!(value, '.' | '-'))
    {
        return Err("Desktop Bundle ID 含不安全字符".to_owned());
    }
    Ok(format!("{identifier}.background-v1"))
}

#[cfg(target_os = "macos")]
pub fn runtime_bundle_identifier(fallback: &str) -> String {
    objc2_foundation::NSBundle::mainBundle()
        .bundleIdentifier()
        .map(|value| value.to_string())
        .filter(|value| !value.is_empty())
        .unwrap_or_else(|| fallback.to_owned())
}

#[cfg(not(target_os = "macos"))]
pub fn runtime_bundle_identifier(fallback: &str) -> String {
    fallback.to_owned()
}

pub fn render_launch_agent(spec: &LaunchAgentSpec) -> Result<String, String> {
    let executable = spec
        .executable
        .to_str()
        .ok_or_else(|| "Desktop 可执行文件路径无效".to_owned())?;
    let app_root = spec
        .app_root
        .to_str()
        .ok_or_else(|| "Desktop 私有数据根路径无效".to_owned())?;
    if !spec.label.ends_with(".background-v1") {
        return Err("Desktop 后台 label 无效".to_owned());
    }
    if let Some(endpoint) = spec.acceptance_endpoint.as_deref() {
        if !endpoint.starts_with("http://127.0.0.1:") && !endpoint.starts_with("http://localhost:")
        {
            return Err("后台验收端点必须是 localhost".to_owned());
        }
        if !spec.app_root.starts_with("/tmp") {
            return Err("后台验收端点只能配合 /tmp 数据根".to_owned());
        }
    }
    let acceptance_environment =
        spec.acceptance_endpoint
            .as_deref()
            .map_or_else(String::new, |endpoint| {
                format!(
                    "<key>NANFENG_AI_DESKTOP_ORDINARY_CHAT_ACCEPTANCE</key><string>1</string>\
             <key>NANFENG_AI_DESKTOP_ORDINARY_CHAT_ACCEPTANCE_ROOT</key><string>{}</string>\
             <key>NANFENG_AI_DESKTOP_ORDINARY_CHAT_MOCK_ENDPOINT</key><string>{}</string>",
                    xml(app_root),
                    xml(endpoint),
                )
            });
    let environment = format!(
        "<key>EnvironmentVariables</key><dict>\
         <key>{}</key><string>{}</string>\
         {}\
         </dict>",
        BACKGROUND_APP_ROOT_ENV,
        xml(app_root),
        acceptance_environment,
    );
    Ok(format!(
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n\
         <!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n\
         <plist version=\"1.0\"><dict>\
         <key>Label</key><string>{}</string>\
         <key>ProgramArguments</key><array><string>{}</string><string>{}</string></array>\
         <key>RunAtLoad</key><true/>\
         <key>StartInterval</key><integer>60</integer>\
         <key>ThrottleInterval</key><integer>15</integer>\
         <key>ProcessType</key><string>Background</string>\
         <key>LowPriorityIO</key><true/>\
         {}\
         <key>StandardOutPath</key><string>{}</string>\
         <key>StandardErrorPath</key><string>{}</string>\
         </dict></plist>\n",
        xml(&spec.label),
        xml(executable),
        BACKGROUND_CYCLE_ARGUMENT,
        environment,
        xml(&format!("{app_root}/background-runtime.log")),
        xml(&format!("{app_root}/background-runtime.log")),
    ))
}

#[cfg(target_os = "macos")]
pub fn reconcile_launch_agent(
    desired: bool,
    home: &Path,
    spec: &LaunchAgentSpec,
) -> Result<bool, String> {
    use std::io::Write;
    use std::os::unix::fs::OpenOptionsExt;
    use std::process::{Command, Stdio};

    let launch_agents = home.join("Library").join("LaunchAgents");
    let plist = launch_agents.join(format!("{}.plist", spec.label));
    let domain = format!("gui/{}", unsafe { libc::getuid() });
    let service = format!("{domain}/{}", spec.label);
    if !desired {
        if plist.exists() {
            fs::remove_file(&plist).map_err(|_| "REMOVE_FAILED".to_owned())?;
        }
        let _ = Command::new("/bin/launchctl")
            .args(["bootout", &service])
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .status();
        return Ok(false);
    }
    fs::create_dir_all(&launch_agents).map_err(|_| "INSTALL_FAILED".to_owned())?;
    fs::create_dir_all(&spec.app_root).map_err(|_| "INSTALL_FAILED".to_owned())?;
    let content = render_launch_agent(spec).map_err(|_| "INSTALL_FAILED".to_owned())?;
    let temporary = plist.with_extension("plist.tmp");
    let mut output = fs::OpenOptions::new()
        .create(true)
        .truncate(true)
        .write(true)
        .mode(0o600)
        .open(&temporary)
        .map_err(|_| "INSTALL_FAILED".to_owned())?;
    output
        .write_all(content.as_bytes())
        .and_then(|_| output.sync_all())
        .map_err(|_| "INSTALL_FAILED".to_owned())?;
    fs::rename(&temporary, &plist).map_err(|_| "INSTALL_FAILED".to_owned())?;
    let _ = Command::new("/bin/launchctl")
        .args(["bootout", &service])
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .status();
    let status = Command::new("/bin/launchctl")
        .arg("bootstrap")
        .arg(&domain)
        .arg(&plist)
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .status()
        .map_err(|_| "INSTALL_FAILED".to_owned())?;
    if !status.success() {
        return Err("INSTALL_FAILED".to_owned());
    }
    Ok(true)
}

#[cfg(windows)]
fn windows_task_xml(spec: &LaunchAgentSpec, user_sid: &str) -> Result<String, String> {
    let executable = spec.executable.to_str().ok_or("INSTALL_FAILED")?;
    let app_root = spec.app_root.to_str().ok_or("INSTALL_FAILED")?;
    if !spec.label.ends_with(".background-v1")
        || !spec.executable.is_absolute()
        || !spec.app_root.is_absolute()
        || app_root.contains('"')
        || app_root.contains('\n')
        || !user_sid.starts_with("S-1-")
        || !user_sid.chars().all(|c| c.is_ascii_digit() || c == '-' || c == 'S')
        || spec.acceptance_endpoint.is_some()
    {
        return Err("INSTALL_FAILED".to_owned());
    }
    let arguments = format!("{} \"{}{}\"", BACKGROUND_CYCLE_ARGUMENT, BACKGROUND_APP_ROOT_ARGUMENT, app_root);
    Ok(format!(
        "<?xml version=\"1.0\" encoding=\"UTF-16\"?>\n\
         <Task version=\"1.2\" xmlns=\"http://schemas.microsoft.com/windows/2004/02/mit/task\">\
         <RegistrationInfo><Description>Nanfeng AI authorized background cycle</Description></RegistrationInfo>\
         <Triggers><TimeTrigger><StartBoundary>2020-01-01T00:00:00</StartBoundary>\
         <Repetition><Interval>PT1M</Interval></Repetition></TimeTrigger></Triggers>\
         <Principals><Principal id=\"Author\"><UserId>{}</UserId><LogonType>InteractiveToken</LogonType>\
         <RunLevel>LeastPrivilege</RunLevel></Principal></Principals>\
         <Settings><MultipleInstancesPolicy>IgnoreNew</MultipleInstancesPolicy>\
         <DisallowStartIfOnBatteries>false</DisallowStartIfOnBatteries>\
         <StopIfGoingOnBatteries>false</StopIfGoingOnBatteries>\
         <StartWhenAvailable>true</StartWhenAvailable><AllowStartOnDemand>true</AllowStartOnDemand>\
         <Enabled>true</Enabled><ExecutionTimeLimit>PT10M</ExecutionTimeLimit></Settings>\
         <Actions Context=\"Author\"><Exec><Command>{}</Command><Arguments>{}</Arguments></Exec></Actions></Task>",
        xml(user_sid), xml(executable), xml(&arguments)
    ))
}

#[cfg(windows)]
fn windows_task_xml_bytes(xml: &str) -> Vec<u8> {
    let mut bytes = vec![0xff, 0xfe];
    for unit in xml.encode_utf16() {
        bytes.extend_from_slice(&unit.to_le_bytes());
    }
    bytes
}

#[cfg(windows)]
fn windows_task_query_result(result: Result<(), windows::core::HRESULT>) -> Result<bool, String> {
    match result {
        Ok(()) => Ok(true),
        // Only GetTask's precise file-not-found result establishes absence.
        Err(code) if code.0 as u32 == 0x80070002 => Ok(false),
        Err(_) => Err("REMOVE_FAILED".to_owned()),
    }
}

#[cfg(windows)]
fn windows_task_exists(task_name: &str) -> Result<bool, String> {
    use windows::core::BSTR;
    use windows::Win32::Foundation::RPC_E_CHANGED_MODE;
    use windows::Win32::System::Com::{CoCreateInstance, CoInitializeEx, CoUninitialize,
        CLSCTX_INPROC_SERVER, COINIT_MULTITHREADED};
    use windows::Win32::System::TaskScheduler::{ITaskService, TaskScheduler};
    use windows::Win32::System::Variant::VARIANT;

    // COM initialization is per-thread. Do not uninitialize another owner's apartment.
    let initialized = unsafe { CoInitializeEx(None, COINIT_MULTITHREADED) };
    if initialized.is_err() && initialized != RPC_E_CHANGED_MODE {
        return Err("REMOVE_FAILED".to_owned());
    }
    struct Apartment(bool);
    impl Drop for Apartment {
        fn drop(&mut self) {
            if self.0 { unsafe { CoUninitialize() }; }
        }
    }
    let _apartment = Apartment(initialized.is_ok());
    let service: ITaskService = unsafe { CoCreateInstance(&TaskScheduler, None, CLSCTX_INPROC_SERVER) }
        .map_err(|_| "REMOVE_FAILED".to_owned())?;
    let empty = VARIANT::default();
    unsafe { service.Connect(&empty, &empty, &empty, &empty) }
        .map_err(|_| "REMOVE_FAILED".to_owned())?;
    let folder = unsafe { service.GetFolder(&BSTR::from("\\")) }
        .map_err(|_| "REMOVE_FAILED".to_owned())?;
    windows_task_query_result(unsafe { folder.GetTask(&BSTR::from(task_name)) }
        .map(|_| ()).map_err(|error| error.code()))
}

#[cfg(windows)]
fn remove_windows_task(
    query: impl FnOnce() -> Result<bool, String>,
    remove: impl FnOnce() -> Result<(), String>,
) -> Result<bool, String> {
    if query()? { remove()?; }
    Ok(false)
}

#[cfg(windows)]
pub fn reconcile_launch_agent(
    desired: bool,
    _home: &Path,
    spec: &LaunchAgentSpec,
) -> Result<bool, String> {
    use std::process::{Command, Stdio};
    if !spec.label.ends_with(".background-v1") || spec.label.len() > 240
        || !spec.label.chars().all(|c| c.is_ascii_alphanumeric() || matches!(c, '.' | '-')) {
        return Err(if desired { "INSTALL_FAILED" } else { "REMOVE_FAILED" }.to_owned());
    }
    let task_name = format!(r"\{}", spec.label);
    if !desired {
        return remove_windows_task(|| windows_task_exists(&task_name), || {
            let removed = Command::new("schtasks.exe").args(["/Delete", "/TN", &task_name, "/F"])
                .stdout(Stdio::null()).stderr(Stdio::null()).status()
                .map_err(|_| "REMOVE_FAILED".to_owned())?;
            if removed.success() { Ok(()) } else { Err("REMOVE_FAILED".to_owned()) }
        });
    }
    let whoami = Command::new("whoami.exe").args(["/user", "/fo", "csv", "/nh"])
        .output().map_err(|_| "INSTALL_FAILED".to_owned())?;
    if !whoami.status.success() { return Err("INSTALL_FAILED".to_owned()); }
    let output = String::from_utf8(whoami.stdout).map_err(|_| "INSTALL_FAILED".to_owned())?;
    let sid = output.trim().rsplit(',').next().unwrap_or("").trim().trim_matches('"');
    let task_xml = windows_task_xml(spec, sid)?;
    fs::create_dir_all(&spec.app_root).map_err(|_| "INSTALL_FAILED".to_owned())?;
    let xml_path = spec.app_root.join(format!(".background-task-{}.xml", std::process::id()));
    fs::write(&xml_path, windows_task_xml_bytes(&task_xml)).map_err(|_| "INSTALL_FAILED".to_owned())?;
    let result = Command::new("schtasks.exe")
        .args(["/Create", "/TN", &task_name, "/XML"])
        .arg(&xml_path).arg("/F")
        .output();
    let _ = fs::remove_file(&xml_path);
    match result {
        Ok(output) if output.status.success() => Ok(true),
        Ok(output) => {
            if spec.label.contains("windowsacceptance") {
                // Acceptance roots are synthetic and marked. Keep the scheduler's exact
                // failure bytes there for diagnosis; normal product logs stay content-free.
                eprintln!("background task registration exit={:?} stderr={:?}",
                    output.status.code(), output.stderr);
            }
            Err("INSTALL_FAILED".to_owned())
        }
        Err(error) => {
            if spec.label.contains("windowsacceptance") {
                eprintln!("background task registration spawn error={:?}", error.kind());
            }
            Err("INSTALL_FAILED".to_owned())
        }
    }
}

#[cfg(not(any(target_os = "macos", windows)))]
pub fn reconcile_launch_agent(
    _desired: bool,
    _home: &Path,
    _spec: &LaunchAgentSpec,
) -> Result<bool, String> {
    Err("UNSUPPORTED".to_owned())
}

fn xml(value: &str) -> String {
    value
        .replace('&', "&amp;")
        .replace('<', "&lt;")
        .replace('>', "&gt;")
        .replace('"', "&quot;")
        .replace('\'', "&apos;")
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

    #[cfg(windows)]
    #[test]
    fn windows_task_query_failure_never_runs_removal() {
        use windows::core::HRESULT;
        // Permission denied, scheduler service unavailable, RPC failure, and an
        // unclassified failure are all unknown presence, not missing tasks.
        for code in [0x80070005_u32, 0x80041315, 0x800706ba, 0x80004005] {
            let removed = std::cell::Cell::new(false);
            let result = remove_windows_task(
                || windows_task_query_result(Err(HRESULT(code as i32))),
                || { removed.set(true); Ok(()) },
            );
            assert_eq!(result, Err("REMOVE_FAILED".to_owned()), "HRESULT={code:x}");
            assert!(!removed.get());
        }
    }

    #[cfg(windows)]
    #[test]
    fn windows_task_absence_is_idempotent_and_existing_removal_reports_failure() {
        use windows::core::HRESULT;
        assert_eq!(remove_windows_task(
            || windows_task_query_result(Err(HRESULT(0x80070002_u32 as i32))),
            || panic!("an absent task must not be deleted"),
        ), Ok(false));
        assert_eq!(remove_windows_task(
            || windows_task_query_result(Ok(())),
            || Err("REMOVE_FAILED".to_owned()),
        ), Err("REMOVE_FAILED".to_owned()));
        let deleted = std::cell::Cell::new(0);
        assert_eq!(remove_windows_task(
            || windows_task_query_result(Ok(())),
            || { deleted.set(deleted.get() + 1); Ok(()) },
        ), Ok(false));
        assert_eq!(deleted.get(), 1);
    }

    #[cfg(windows)]
    #[test]
    #[ignore = "Opt-in readonly local Task Scheduler COM probe; never registers or deletes a task"]
    fn windows_task_com_query_missing_task_is_readonly() {
        let mut nonce = [0u8; 16];
        getrandom::fill(&mut nonce).unwrap();
        let nonce: String = nonce.iter().map(|byte| format!("{byte:02x}")).collect();
        let directory = tempdir().unwrap();
        let root = directory.path().join("must-not-be-created");
        let spec = LaunchAgentSpec {
            label: format!("com.nanzhufeng.ai.synthetic.query-only.{nonce}.background-v1"),
            executable: directory.path().join("must-not-run.exe"),
            app_root: root.clone(),
            acceptance_endpoint: None,
        };
        assert_eq!(reconcile_launch_agent(false, directory.path(), &spec), Ok(false));
        // The second query exercises repeated COM initialization and release.
        assert_eq!(reconcile_launch_agent(false, directory.path(), &spec), Ok(false));
        assert!(!root.exists());
    }

    #[cfg(windows)]
    #[test]
    fn windows_scheduler_xml_declares_and_writes_utf16_with_unicode_paths() {
        let spec = LaunchAgentSpec {
            label: "com.nanzhufeng.ai.desktop.windowsacceptance.background-v1".into(),
            executable: PathBuf::from(r"C:\Users\测试\南枫 AI.exe"),
            app_root: PathBuf::from(r"D:\CodexTemp\南枫合成根"),
            acceptance_endpoint: None,
        };
        let xml = windows_task_xml(&spec, "S-1-5-21-123-456-789-1000").unwrap();
        let bytes = windows_task_xml_bytes(&xml);
        assert_eq!(&bytes[..2], &[0xff, 0xfe]);
        let decoded = String::from_utf16(&bytes[2..].chunks_exact(2)
            .map(|pair| u16::from_le_bytes([pair[0], pair[1]])).collect::<Vec<_>>()).unwrap();
        assert_eq!(decoded, xml);
        assert!(decoded.starts_with("<?xml version=\"1.0\" encoding=\"UTF-16\"?>"));
        assert!(decoded.contains("南枫合成根"));
    }

    #[test]
    fn plist_is_fixed_scope_and_acceptance_requires_tmp_localhost() {
        let spec = LaunchAgentSpec {
            label: "com.nanzhufeng.ai.acceptance.background-v1".into(),
            executable: PathBuf::from("/tmp/Nanfeng Acceptance.app/Contents/MacOS/Nanfeng"),
            app_root: PathBuf::from("/tmp/nanfeng-background-owner"),
            acceptance_endpoint: Some("http://127.0.0.1:45678/v1/chat/completions".into()),
        };
        let plist = render_launch_agent(&spec).unwrap();
        assert!(plist.contains(BACKGROUND_CYCLE_ARGUMENT));
        assert!(plist.contains("StartInterval</key><integer>60"));
        assert!(plist.contains("NANFENG_AI_DESKTOP_ORDINARY_CHAT_ACCEPTANCE_ROOT"));
        for forbidden in [
            "prompt",
            "Authorization",
            "conversationId",
            "planId",
            "apiKey",
        ] {
            assert!(!plist.contains(forbidden));
        }
        let mut unsafe_spec = spec.clone();
        unsafe_spec.app_root = PathBuf::from("/Users/example/data");
        assert!(render_launch_agent(&unsafe_spec).is_err());
    }

    #[test]
    fn launch_agent_without_mock_endpoint_keeps_the_exact_workspace_root() {
        let spec = LaunchAgentSpec {
            label: "com.nanzhufeng.ai.production.background-v1".into(),
            executable: PathBuf::from(
                "/Applications/Nanfeng AI.app/Contents/MacOS/nanfeng-ai-desktop",
            ),
            app_root: PathBuf::from(
                "/Users/example/Library/Application Support/com.nanzhufeng.ai.desktop/p6b-workspace",
            ),
            acceptance_endpoint: None,
        };

        let plist = render_launch_agent(&spec).unwrap();
        assert!(plist.contains("NANFENG_AI_DESKTOP_BACKGROUND_ROOT_V1"));
        assert!(plist.contains(
            "/Users/example/Library/Application Support/com.nanzhufeng.ai.desktop/p6b-workspace"
        ));
        assert!(!plist.contains("NANFENG_AI_DESKTOP_ORDINARY_CHAT_ACCEPTANCE"));
    }

    #[test]
    fn desired_state_comes_only_from_confirmed_work() {
        let directory = tempdir().unwrap();
        let connection = Connection::open(directory.path().join("state.sqlite3")).unwrap();
        connection.execute_batch(
            "CREATE TABLE desktop_reminder_plans_v1(status TEXT NOT NULL);
             CREATE TABLE desktop_app_settings(id INTEGER PRIMARY KEY,history_library_enabled INTEGER NOT NULL);
             INSERT INTO desktop_app_settings VALUES(1,0);",
        ).unwrap();
        assert!(!desired_enabled(&connection).unwrap());
        connection
            .execute("INSERT INTO desktop_reminder_plans_v1 VALUES('PAUSED')", [])
            .unwrap();
        assert!(!desired_enabled(&connection).unwrap());
        connection
            .execute("INSERT INTO desktop_reminder_plans_v1 VALUES('ACTIVE')", [])
            .unwrap();
        assert!(desired_enabled(&connection).unwrap());
        connection
            .execute("DELETE FROM desktop_reminder_plans_v1", [])
            .unwrap();
        connection
            .execute(
                "UPDATE desktop_app_settings SET history_library_enabled=1 WHERE id=1",
                [],
            )
            .unwrap();
        assert!(desired_enabled(&connection).unwrap());
    }

    #[cfg(all(target_os = "macos", feature = "launchd-acceptance"))]
    #[test]
    fn isolated_launchd_roundtrip_runs_and_removes_unique_tmp_agent() {
        use std::io::Write;
        use std::os::unix::fs::PermissionsExt;
        use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

        let directory = tempfile::Builder::new()
            .prefix("nanfeng-background-launchd-acceptance.")
            .tempdir_in("/tmp")
            .unwrap();
        let root = directory.path().join("app-root");
        fs::create_dir_all(&root).unwrap();
        let marker = root.join("launchd-ran");
        let executable = directory.path().join("background-fixture.sh");
        let mut script = fs::File::create(&executable).unwrap();
        writeln!(
            script,
            "#!/bin/sh\n/usr/bin/touch '{}'",
            marker.to_string_lossy().replace('\'', "")
        )
        .unwrap();
        script.sync_all().unwrap();
        fs::set_permissions(&executable, fs::Permissions::from_mode(0o700)).unwrap();
        let nonce = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_nanos();
        let spec = LaunchAgentSpec {
            label: format!(
                "com.nanzhufeng.ai.backgroundacceptance.{}.{}.background-v1",
                std::process::id(),
                nonce
            ),
            executable,
            app_root: root,
            acceptance_endpoint: None,
        };
        assert!(reconcile_launch_agent(true, directory.path(), &spec).unwrap());
        let deadline = Instant::now() + Duration::from_secs(8);
        while !marker.exists() && Instant::now() < deadline {
            std::thread::sleep(Duration::from_millis(100));
        }
        let ran = marker.exists();
        let removed = reconcile_launch_agent(false, directory.path(), &spec).unwrap();
        assert!(
            ran,
            "isolated launchd agent did not run within the bounded window"
        );
        assert!(!removed);
        assert!(!directory
            .path()
            .join("Library/LaunchAgents")
            .join(format!("{}.plist", spec.label))
            .exists());
    }
}
