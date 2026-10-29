//! Fail-closed root selection for a separately identified Windows acceptance build.
use serde::Deserialize;
use std::fs;
use std::path::{Path, PathBuf};

pub const ROOT_ENV: &str = "NANFENG_AI_WINDOWS_ACCEPTANCE_ROOT";
pub const IDENTIFIER: &str = "com.nanzhufeng.ai.desktop.windowsacceptance";
/// A hidden acceptance window must never activate the user's desktop.
pub fn presentation_allowed(is_windows: bool, identifier: &str, visible_requested: bool) -> bool {
    !is_windows || identifier != IDENTIFIER || visible_requested
}
pub const DIRECTORY_PREFIX: &str = "nanfeng-ai-windows-acceptance.";
pub const MARKER: &str = ".nanfeng-windows-acceptance.json";
pub const NORMAL_STARTUP_ARGUMENT: &str = "--windows-isolated-normal-startup";
pub const STORAGE_LOCATION_ENV: &str = "NANFENG_AI_WINDOWS_STORAGE_LOCATION_ACCEPTANCE";

// One-shot, marked-root-only pause after persistent claim and before signal registration.
#[cfg(all(windows, debug_assertions))]
pub(crate) fn reminder_registration_checkpoint(root: &Path) -> Result<(), String> {
    let arm = root.join(".reminder-registration-arm");
    let metadata = match fs::symlink_metadata(&arm) {
        Ok(metadata) => metadata,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(()),
        Err(_) => return Err("隔离提醒停点不可读".into()),
    };
    resolve(Some(root), true, IDENTIFIER, root.parent().ok_or("隔离提醒目录无效")?)?;
    if !metadata.is_file() || is_link(&metadata) || metadata.len() != 0 {
        return Err("隔离提醒停点标记无效".into());
    }
    fs::remove_file(&arm).map_err(|_| "隔离提醒停点无法消费")?;
    let resume = root.join(".reminder-registration-continue");
    if fs::symlink_metadata(&resume).is_ok() { return Err("隔离提醒继续标记已存在".into()); }
    fs::OpenOptions::new().write(true).create_new(true)
        .open(root.join(".reminder-registration-reached"))
        .and_then(|marker| marker.sync_all()).map_err(|_| "隔离提醒停点收据未保存")?;
    let deadline = std::time::Instant::now() + std::time::Duration::from_secs(60);
    loop {
        match fs::symlink_metadata(&resume) {
            Ok(metadata) if metadata.is_file() && !is_link(&metadata) && metadata.len() == 0 => return Ok(()),
            Ok(_) => return Err("隔离提醒继续标记无效".into()),
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {},
            Err(_) => return Err("隔离提醒继续标记不可读".into()),
        }
        if std::time::Instant::now() >= deadline { return Err("隔离提醒停点等待超时".into()); }
        std::thread::sleep(std::time::Duration::from_millis(20));
    }
}

// One-shot, marked-root-only pause after persistent claim and before signal registration.
#[cfg(all(windows, debug_assertions))]
pub(crate) fn ordinary_registration_checkpoint(root: &Path) -> Result<(), String> {
    let arm = root.join(".ordinary-registration-arm");
    let metadata = match fs::symlink_metadata(&arm) {
        Ok(metadata) => metadata,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(()),
        Err(_) => return Err("隔离聊天停点不可读".into()),
    };
    resolve(Some(root), true, IDENTIFIER, root.parent().ok_or("隔离聊天目录无效")?)?;
    if !metadata.is_file() || is_link(&metadata) || metadata.len() != 0 {
        return Err("隔离聊天停点标记无效".into());
    }
    fs::remove_file(&arm).map_err(|_| "隔离聊天停点无法消费")?;
    let resume = root.join(".ordinary-registration-continue");
    if fs::symlink_metadata(&resume).is_ok() { return Err("隔离聊天继续标记已存在".into()); }
    fs::OpenOptions::new().write(true).create_new(true)
        .open(root.join(".ordinary-registration-reached"))
        .and_then(|marker| marker.sync_all()).map_err(|_| "隔离聊天停点收据未保存")?;
    let deadline = std::time::Instant::now() + std::time::Duration::from_secs(60);
    loop {
        match fs::symlink_metadata(&resume) {
            Ok(metadata) if metadata.is_file() && !is_link(&metadata) && metadata.len() == 0 => return Ok(()),
            Ok(_) => return Err("隔离聊天继续标记无效".into()),
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {},
            Err(_) => return Err("隔离聊天继续标记不可读".into()),
        }
        if std::time::Instant::now() >= deadline { return Err("隔离聊天停点等待超时".into()); }
        std::thread::sleep(std::time::Duration::from_millis(20));
    }
}

// This gate is called only by the debug-only loopback account acceptance path.
// The marked root is validated again before any checkpoint file is read/written.
#[cfg(all(windows, debug_assertions))]
pub(crate) fn account_login_checkpoint(root: &Path, phase: &str) -> Result<(), String> {
    account_checkpoint(root, phase, "login")
}

#[cfg(all(windows, debug_assertions))]
pub(crate) fn account_signout_checkpoint(root: &Path, phase: &str) -> Result<(), String> {
    account_checkpoint(root, phase, "signout")
}

#[cfg(all(windows, debug_assertions))]
fn account_checkpoint(root: &Path, phase: &str, operation: &str) -> Result<(), String> {
    let parent = root.parent().ok_or("隔离账户停点目录无效")?;
    resolve(Some(root), true, IDENTIFIER, parent)?;
    let arm = root.join(format!(".account-{operation}-checkpoint-arm"));
    let metadata = match fs::symlink_metadata(&arm) {
        Ok(metadata) => metadata,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(()),
        Err(_) => return Err("隔离账户停点不可读".into()),
    };
    if !metadata.is_file() || is_link(&metadata) || metadata.len() > 32 {
        return Err("隔离账户停点标记无效".into());
    }
    let requested = fs::read_to_string(&arm).map_err(|_| "隔离账户停点不可读")?;
    if !matches!(requested.as_str(), "before-session" | "after-session" | "after-commit") {
        return Err("隔离账户停点阶段无效".into());
    }
    if requested != phase { return Ok(()); }
    fs::write(root.join(format!(".account-{operation}-checkpoint-reached")), phase)
        .map_err(|_| "隔离账户停点收据未保存")?;
    let deadline = std::time::Instant::now() + std::time::Duration::from_secs(30);
    while arm.exists() {
        if std::time::Instant::now() >= deadline {
            return Err("隔离账户停点等待超时".into());
        }
        std::thread::sleep(std::time::Duration::from_millis(10));
    }
    Ok(())
}

// A one-shot debug probe invokes the unchanged production periodic caller.
// It never changes the real 12-hour timer or opens an unmarked business root.
#[cfg(all(windows, debug_assertions))]
pub(crate) fn take_account_periodic_probe(root: &Path) -> Result<bool, String> {
    let parent = root.parent().ok_or("隔离账户周期目录无效")?;
    resolve(Some(root), true, IDENTIFIER, parent)?;
    let arm = root.join(".account-periodic-probe-arm");
    let metadata = match fs::symlink_metadata(&arm) {
        Ok(metadata) => metadata,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(false),
        Err(_) => return Err("隔离账户周期标记不可读".into()),
    };
    if !metadata.is_file() || is_link(&metadata) || metadata.len() != 8 {
        return Err("隔离账户周期标记无效".into());
    }
    if fs::read_to_string(&arm).map_err(|_| "隔离账户周期标记不可读")? != "periodic" {
        return Err("隔离账户周期标记无效".into());
    }
    fs::remove_file(arm).map_err(|_| "隔离账户周期标记未消费")?;
    Ok(true)
}

pub(crate) fn resolve_startup(
    requested: Option<&Path>,
    mode: crate::DesktopStartupMode,
    explicit_normal: bool,
    windows: bool,
    identifier: &str,
    temporary_parent: &Path,
) -> Result<Option<PathBuf>, String> {
    if explicit_normal
        && (!windows || mode != crate::DesktopStartupMode::Normal || requested.is_none())
    {
        return Err(
            "隔离普通启动只允许 Windows 独立身份与显式验收根；不能混用诊断或后台模式".into(),
        );
    }
    // A scheduled cycle starts in a fresh process: Task Scheduler does not inherit the
    // foreground process's acceptance-root environment or custom TEMP. Its explicit
    // background-root argument must still pass the same marker and canonical-path gate.
    let isolated_background = windows
        && mode == crate::DesktopStartupMode::BackgroundCycle
        && identifier == IDENTIFIER
        && !explicit_normal;
    let parent = if isolated_background {
        requested.and_then(Path::parent).ok_or_else(||
            "Windows 独立验收后台数据根缺失；未打开业务数据".to_owned())?
    } else {
        temporary_parent
    };
    resolve(
        requested,
        mode == crate::DesktopStartupMode::UiSchemaDiagnostic || explicit_normal || isolated_background,
        identifier,
        parent,
    )
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct Marker {
    schema_version: u32,
    identifier: String,
    run_id: String,
}

fn is_link(metadata: &fs::Metadata) -> bool {
    #[cfg(windows)]
    {
        use std::os::windows::fs::MetadataExt;
        metadata.file_attributes() & 0x400 != 0 // FILE_ATTRIBUTE_REPARSE_POINT
    }
    #[cfg(not(windows))]
    {
        metadata.file_type().is_symlink()
    }
}

pub fn resolve(
    requested: Option<&Path>,
    isolated_startup: bool,
    identifier: &str,
    temporary_parent: &Path,
) -> Result<Option<PathBuf>, String> {
    const INVALID: &str = "Windows 独立验收身份、目录或标记无效；未打开业务数据";
    let Some(root) = requested else {
        return if identifier == IDENTIFIER {
            Err(INVALID.into())
        } else {
            Ok(None)
        };
    };
    if !isolated_startup
        || identifier != IDENTIFIER
        || !root.is_absolute()
        || root
            .components()
            .any(|c| matches!(c, std::path::Component::ParentDir))
    {
        return Err(INVALID.into());
    }
    let name = root.file_name().and_then(|s| s.to_str()).ok_or(INVALID)?;
    let run_id = name.strip_prefix(DIRECTORY_PREFIX).ok_or(INVALID)?;
    if !(8..=64).contains(&run_id.len())
        || !run_id.bytes().all(|b| b.is_ascii_hexdigit() || b == b'-')
    {
        return Err(INVALID.into());
    }
    let metadata = fs::symlink_metadata(root).map_err(|_| INVALID)?;
    if !metadata.is_dir() || is_link(&metadata) {
        return Err(INVALID.into());
    }
    let canonical = root.canonicalize().map_err(|_| INVALID)?;
    let parent = temporary_parent.canonicalize().map_err(|_| INVALID)?;
    if canonical.parent() != Some(parent.as_path()) {
        return Err(INVALID.into());
    }
    let marker_path = canonical.join(MARKER);
    let metadata = fs::symlink_metadata(&marker_path).map_err(|_| INVALID)?;
    if !metadata.is_file() || is_link(&metadata) || metadata.len() > 4096 {
        return Err(INVALID.into());
    }
    let marker: Marker = serde_json::from_slice(&fs::read(marker_path).map_err(|_| INVALID)?)
        .map_err(|_| INVALID)?;
    if marker.schema_version != 1 || marker.identifier != identifier || marker.run_id != run_id {
        return Err(INVALID.into());
    }
    Ok(Some(canonical))
}

/// Validates the legacy selector envelope after the Windows identity/root gate.
/// Offline fixtures may reuse their existing seed owner, never another data root.
pub fn validate_fixture_environment<F>(
    root: Option<&Path>,
    windows: bool,
    allow_fixtures: bool,
    get: F,
) -> Result<(), String>
where
    F: Fn(&str) -> Option<std::ffi::OsString>,
{
    if !windows && root.is_none() {
        return Ok(());
    }
    const INVALID: &str = "Windows 验收夹具必须唯一，并使用同一已验证隔离根";
    let forbidden = [
        crate::P6E_ACCEPTANCE_ENV,
        crate::P6H_ACCEPTANCE_ENV,
        crate::P6I_ACCEPTANCE_ENV,
        crate::P6J_ACCEPTANCE_ENV,
        crate::FB_P6_050_ACCEPTANCE_ENV,
        crate::ORDINARY_CHAT_ACCEPTANCE_ENV,
        crate::ORDINARY_CHAT_ACCEPTANCE_ROOT_ENV,
        crate::ORDINARY_CHAT_MOCK_ENDPOINT_ENV,
        crate::ACCOUNT_SYNC_ACCEPTANCE_ROOT_ENV,
    ];
    if forbidden.iter().any(|key| get(key).is_some()) {
        return Err(INVALID.into());
    }
    let pairs = [
        (
            crate::C02_C06_ACCEPTANCE_ENV,
            crate::C02_C06_ACCEPTANCE_ROOT_ENV,
        ),
        (
            crate::C07_C12_ACCEPTANCE_ENV,
            crate::C07_C12_ACCEPTANCE_ROOT_ENV,
        ),
        (crate::C13_ACCEPTANCE_ENV, crate::C13_ACCEPTANCE_ROOT_ENV),
        (crate::C14_ACCEPTANCE_ENV, crate::C14_ACCEPTANCE_ROOT_ENV),
        (crate::C15_ACCEPTANCE_ENV, crate::C15_ACCEPTANCE_ROOT_ENV),
        (
            crate::C16_VISUAL_ACCEPTANCE_ENV,
            crate::C16_VISUAL_ACCEPTANCE_ROOT_ENV,
        ),
    ];
    let matches_root = |value: std::ffi::OsString| {
        let path = PathBuf::from(value);
        path.is_absolute()
            && !path
                .components()
                .any(|part| matches!(part, std::path::Component::ParentDir))
            && fs::symlink_metadata(&path)
                .is_ok_and(|metadata| metadata.is_dir() && !is_link(&metadata))
            && root.is_some_and(|root| path.canonicalize().ok().as_deref() == Some(root))
    };
    let mut selected = 0;
    for (flag, key) in pairs {
        let enabled = get(flag);
        let path = get(key);
        if enabled.is_none() && path.is_none() {
            continue;
        }
        if enabled.as_deref() != Some(std::ffi::OsStr::new("1")) || !path.is_some_and(&matches_root)
        {
            return Err(INVALID.into());
        }
        selected += 1;
    }
    if let Some(path) = get(crate::P6_V2_PICKER_ACCEPTANCE_ROOT_ENV) {
        if !matches_root(path) {
            return Err(INVALID.into());
        }
        selected += 1;
    }
    if selected > 1 || (!allow_fixtures && selected > 0) {
        return Err(INVALID.into());
    }
    if get(crate::C16_VISUAL_ACCEPTANCE_STATE_ENV).is_some()
        && get(crate::C16_VISUAL_ACCEPTANCE_ENV).as_deref() != Some(std::ffi::OsStr::new("1"))
    {
        return Err(INVALID.into());
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn hidden_presentation_policy_preserves_production_and_explicit_user_visibility() {
        assert!(!presentation_allowed(true, IDENTIFIER, false));
        assert!(presentation_allowed(true, IDENTIFIER, true));
        assert!(presentation_allowed(true, "com.nanzhufeng.ai.desktop", false));
        assert!(presentation_allowed(false, IDENTIFIER, false));
    }

    #[cfg(all(windows, debug_assertions))]
    #[test]
    fn reminder_registration_checkpoint_rejects_unmarked_and_invalid_arm() {
        let temporary = tempfile::tempdir().unwrap();
        let root = fixture(temporary.path());
        let arm = root.join(".reminder-registration-arm");
        assert!(reminder_registration_checkpoint(&root).is_ok());
        fs::write(&arm, "invalid").unwrap();
        assert!(reminder_registration_checkpoint(&root).is_err());
        assert!(arm.exists());
        assert!(!root.join(".reminder-registration-reached").exists());
        fs::write(&arm, "").unwrap();
        fs::remove_file(root.join(MARKER)).unwrap();
        assert!(reminder_registration_checkpoint(&root).is_err());
        assert!(arm.exists());
        assert!(!root.join(".reminder-registration-reached").exists());
    }

    #[cfg(all(windows, debug_assertions))]
    #[test]
    fn ordinary_registration_checkpoint_rejects_unmarked_and_invalid_arm() {
        let temporary = tempfile::tempdir().unwrap();
        let root = fixture(temporary.path());
        let arm = root.join(".ordinary-registration-arm");
        assert!(ordinary_registration_checkpoint(&root).is_ok());
        fs::write(&arm, "invalid").unwrap();
        assert!(ordinary_registration_checkpoint(&root).is_err());
        assert!(arm.exists());
        assert!(!root.join(".ordinary-registration-reached").exists());
        fs::write(&arm, "").unwrap();
        fs::remove_file(root.join(MARKER)).unwrap();
        assert!(ordinary_registration_checkpoint(&root).is_err());
        assert!(arm.exists());
        assert!(!root.join(".ordinary-registration-reached").exists());
    }

    #[cfg(all(windows, debug_assertions))]
    #[test]
    fn periodic_probe_is_one_shot_and_rejects_invalid_or_unmarked_roots() {
        let temporary = tempfile::tempdir().unwrap();
        let root = fixture(temporary.path());
        let arm = root.join(".account-periodic-probe-arm");
        assert!(!take_account_periodic_probe(&root).unwrap());
        fs::write(&arm, "periodic").unwrap();
        assert!(take_account_periodic_probe(&root).unwrap());
        assert!(!arm.exists());
        assert!(!take_account_periodic_probe(&root).unwrap());
        for value in ["", "periodic-extra", "invalid!"] {
            fs::write(&arm, value).unwrap();
            assert!(take_account_periodic_probe(&root).is_err());
            assert!(arm.exists());
        }
        fs::write(&arm, "periodic").unwrap();
        fs::remove_file(root.join(MARKER)).unwrap();
        assert!(take_account_periodic_probe(&root).is_err());
        assert!(arm.exists());
    }

    use std::fs;

    #[test]
    fn explicit_normal_startup_uses_the_same_marked_root_without_reseeding() {
        let temporary = tempfile::tempdir().unwrap();
        let root = fixture(temporary.path());
        let selected = resolve_startup(
            Some(&root),
            crate::DesktopStartupMode::Normal,
            true,
            true,
            IDENTIFIER,
            temporary.path(),
        )
        .unwrap()
        .unwrap();
        fs::write(selected.join("normal-startup-sentinel"), "preserve").unwrap();
        let reopened = resolve_startup(
            Some(&root),
            crate::DesktopStartupMode::Normal,
            true,
            true,
            IDENTIFIER,
            temporary.path(),
        )
        .unwrap()
        .unwrap();
        assert_eq!(
            fs::read_to_string(reopened.join("normal-startup-sentinel")).unwrap(),
            "preserve"
        );
        assert!(!root.join("workspace.sqlite3").exists());
    }

    #[test]
    fn isolated_normal_startup_never_falls_back_or_mixes_modes() {
        let temporary = tempfile::tempdir().unwrap();
        let root = fixture(temporary.path());
        for mode in [
            crate::DesktopStartupMode::UiSchemaDiagnostic,
            crate::DesktopStartupMode::BackgroundCycle,
        ] {
            assert!(
                resolve_startup(Some(&root), mode, true, true, IDENTIFIER, temporary.path())
                    .is_err()
            );
        }
        for (requested, explicit, windows, identifier) in [
            (None, true, true, IDENTIFIER),
            (None, true, true, "com.nanzhufeng.ai.desktop"),
            (Some(root.as_path()), false, true, IDENTIFIER),
            (Some(root.as_path()), true, false, IDENTIFIER),
            (
                Some(root.as_path()),
                true,
                true,
                "com.nanzhufeng.ai.desktop",
            ),
        ] {
            assert!(resolve_startup(
                requested,
                crate::DesktopStartupMode::Normal,
                explicit,
                windows,
                identifier,
                temporary.path()
            )
            .is_err());
        }
        assert_eq!(
            resolve_startup(
                None,
                crate::DesktopStartupMode::Normal,
                false,
                true,
                "com.nanzhufeng.ai.desktop",
                temporary.path()
            )
            .unwrap(),
            None
        );
        fs::remove_file(root.join(MARKER)).unwrap();
        assert!(resolve_startup(
            Some(&root),
            crate::DesktopStartupMode::Normal,
            true,
            true,
            IDENTIFIER,
            temporary.path()
        )
        .is_err());
    }

    #[test]
    fn scheduled_cycle_reopens_only_a_marked_acceptance_root() {
        let temporary = tempfile::tempdir().unwrap();
        let unrelated_temp = tempfile::tempdir().unwrap();
        let root = fixture(temporary.path());
        let selected = resolve_startup(Some(&root), crate::DesktopStartupMode::BackgroundCycle,
            false, true, IDENTIFIER, unrelated_temp.path()).unwrap();
        assert_eq!(selected, Some(root.canonicalize().unwrap()));
        assert!(resolve_startup(None, crate::DesktopStartupMode::BackgroundCycle,
            false, true, IDENTIFIER, unrelated_temp.path()).is_err());
        assert!(resolve_startup(Some(&root), crate::DesktopStartupMode::BackgroundCycle,
            false, false, IDENTIFIER, unrelated_temp.path()).is_err());
        fs::remove_file(root.join(MARKER)).unwrap();
        assert!(resolve_startup(Some(&root), crate::DesktopStartupMode::BackgroundCycle,
            false, true, IDENTIFIER, unrelated_temp.path()).is_err());
    }

    fn fixture(parent: &Path) -> PathBuf {
        let root = parent.join(format!("{DIRECTORY_PREFIX}01234567-abcd"));
        fs::create_dir(&root).unwrap();
        fs::write(
            root.join(MARKER),
            serde_json::to_vec(&serde_json::json!({
                "schemaVersion": 1, "identifier": IDENTIFIER, "runId": "01234567-abcd"
            }))
            .unwrap(),
        )
        .unwrap();
        root
    }

    #[test]
    fn offline_selector_uses_the_same_validated_windows_root() {
        let temporary = tempfile::tempdir().unwrap();
        let root = fixture(temporary.path());
        let selected = resolve(Some(&root), true, IDENTIFIER, temporary.path())
            .unwrap()
            .unwrap();
        let variables = std::collections::BTreeMap::from([
            (crate::C13_ACCEPTANCE_ENV, std::ffi::OsString::from("1")),
            (
                crate::C13_ACCEPTANCE_ROOT_ENV,
                selected.clone().into_os_string(),
            ),
        ]);
        assert!(
            validate_fixture_environment(Some(&selected), true, true, |key| variables
                .get(key)
                .cloned())
            .is_ok()
        );
    }

    #[test]
    fn normal_startup_allows_blank_roots_but_rejects_seed_selectors() {
        use std::{collections::BTreeMap, ffi::OsString};
        let temporary = tempfile::tempdir().unwrap();
        let root = fixture(temporary.path()).canonicalize().unwrap();
        assert!(validate_fixture_environment(Some(&root), true, false, |_| None).is_ok());
        for (flag, key) in [
            (
                crate::C02_C06_ACCEPTANCE_ENV,
                crate::C02_C06_ACCEPTANCE_ROOT_ENV,
            ),
            (crate::C13_ACCEPTANCE_ENV, crate::C13_ACCEPTANCE_ROOT_ENV),
        ] {
            let variables = BTreeMap::from([
                (flag, OsString::from("1")),
                (key, root.clone().into_os_string()),
            ]);
            assert!(
                validate_fixture_environment(Some(&root), true, false, |key| variables
                    .get(key)
                    .cloned())
                .is_err()
            );
        }
        assert!(
            validate_fixture_environment(Some(&root), true, false, |key| {
                (key == crate::P6_V2_PICKER_ACCEPTANCE_ROOT_ENV)
                    .then(|| root.clone().into_os_string())
            })
            .is_err()
        );
        assert!(!root.join("workspace.sqlite3").exists());
    }

    #[test]
    fn selectors_reject_production_roots_mixed_modes_and_incomplete_envelopes() {
        use std::{collections::BTreeMap, ffi::OsString};
        let temporary = tempfile::tempdir().unwrap();
        let other = tempfile::tempdir().unwrap();
        let root = fixture(temporary.path()).canonicalize().unwrap();
        let valid = BTreeMap::from([
            (crate::C13_ACCEPTANCE_ENV, OsString::from("1")),
            (
                crate::C13_ACCEPTANCE_ROOT_ENV,
                root.clone().into_os_string(),
            ),
        ]);
        assert!(
            validate_fixture_environment(None, true, true, |key| valid.get(key).cloned()).is_err()
        );
        assert!(validate_fixture_environment(None, true, true, |_| None).is_ok());
        // Original non-Windows routing remains the responsibility of its owner.
        assert!(
            validate_fixture_environment(None, false, true, |key| valid.get(key).cloned()).is_ok()
        );
        for (key, value) in [
            (crate::C13_ACCEPTANCE_ENV, OsString::from("0")),
            (
                crate::C13_ACCEPTANCE_ROOT_ENV,
                other.path().as_os_str().to_owned(),
            ),
            (crate::C14_ACCEPTANCE_ENV, OsString::from("1")),
            (crate::ORDINARY_CHAT_ACCEPTANCE_ENV, OsString::from("1")),
            (
                crate::C16_VISUAL_ACCEPTANCE_STATE_ENV,
                OsString::from("dark__large__style"),
            ),
        ] {
            let mut variables = valid.clone();
            variables.insert(key, value);
            assert!(
                validate_fixture_environment(Some(&root), true, true, |key| variables
                    .get(key)
                    .cloned())
                .is_err()
            );
        }
        for missing in [crate::C13_ACCEPTANCE_ENV, crate::C13_ACCEPTANCE_ROOT_ENV] {
            let mut variables = valid.clone();
            variables.remove(missing);
            assert!(
                validate_fixture_environment(Some(&root), true, true, |key| variables
                    .get(key)
                    .cloned())
                .is_err()
            );
        }
        let mut mixed = valid;
        mixed.insert(crate::C14_ACCEPTANCE_ENV, OsString::from("1"));
        mixed.insert(
            crate::C14_ACCEPTANCE_ROOT_ENV,
            root.clone().into_os_string(),
        );
        assert!(
            validate_fixture_environment(Some(&root), true, true, |key| mixed.get(key).cloned())
                .is_err()
        );
        assert!(!root.join("workspace.sqlite3").exists());
        assert!(!other.path().join("workspace.sqlite3").exists());
    }

    #[test]
    fn explicitly_marked_diagnostic_root_reopens_without_reseeding() {
        let temporary = tempfile::tempdir().unwrap();
        let root = fixture(temporary.path());
        let selected = resolve(Some(&root), true, IDENTIFIER, temporary.path())
            .unwrap()
            .unwrap();
        assert_eq!(selected, root.canonicalize().unwrap());
        fs::write(selected.join("saved-draft.txt"), "用户验收草稿").unwrap();
        let reopened = resolve(Some(&root), true, IDENTIFIER, temporary.path())
            .unwrap()
            .unwrap();
        assert_eq!(
            fs::read_to_string(reopened.join("saved-draft.txt")).unwrap(),
            "用户验收草稿"
        );
    }

    #[test]
    fn production_never_accepts_an_injected_root_and_diagnostic_identity_never_falls_back() {
        let temporary = tempfile::tempdir().unwrap();
        let root = fixture(temporary.path());
        assert_eq!(
            resolve(None, false, "com.nanzhufeng.ai.desktop", temporary.path()).unwrap(),
            None
        );
        assert!(resolve(None, true, IDENTIFIER, temporary.path()).is_err());
        assert!(resolve(Some(&root), false, IDENTIFIER, temporary.path()).is_err());
        assert!(resolve(
            Some(&root),
            true,
            "com.nanzhufeng.ai.desktop",
            temporary.path()
        )
        .is_err());
        assert!(resolve(
            Some(Path::new("relative-root")),
            true,
            IDENTIFIER,
            temporary.path()
        )
        .is_err());
    }

    #[test]
    fn invalid_marker_or_parent_cannot_open_a_workspace() {
        let temporary = tempfile::tempdir().unwrap();
        let outside = tempfile::tempdir().unwrap();
        let root = fixture(temporary.path());
        assert!(resolve(Some(&root), true, IDENTIFIER, outside.path()).is_err());
        for marker in [
            "{}".to_owned(),
            serde_json::json!({"schemaVersion": 1,"identifier": IDENTIFIER,"runId": "different-run"}).to_string(),
            serde_json::json!({"schemaVersion": 2,"identifier": IDENTIFIER,"runId": "01234567-abcd"}).to_string(),
            serde_json::json!({"schemaVersion": 1,"identifier": IDENTIFIER,"runId": "01234567-abcd","unexpected": true}).to_string(),
            "x".repeat(4097),
        ] {
            fs::write(root.join(MARKER), marker).unwrap();
            assert!(resolve(Some(&root), true, IDENTIFIER, temporary.path()).is_err());
        }
        fs::remove_file(root.join(MARKER)).unwrap();
        assert!(resolve(Some(&root), true, IDENTIFIER, temporary.path()).is_err());
        assert!(!root.join("workspace.sqlite3").exists());
    }
}
