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
    let environment = spec
        .acceptance_endpoint
        .as_deref()
        .map_or_else(String::new, |endpoint| {
            format!(
                "<key>EnvironmentVariables</key><dict>\
             <key>NANFENG_AI_DESKTOP_ORDINARY_CHAT_ACCEPTANCE</key><string>1</string>\
             <key>NANFENG_AI_DESKTOP_ORDINARY_CHAT_ACCEPTANCE_ROOT</key><string>{}</string>\
             <key>NANFENG_AI_DESKTOP_ORDINARY_CHAT_MOCK_ENDPOINT</key><string>{}</string>\
             </dict>",
                xml(app_root),
                xml(endpoint),
            )
        });
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

#[cfg(not(target_os = "macos"))]
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
