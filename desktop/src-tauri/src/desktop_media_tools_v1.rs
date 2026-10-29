//! Fixed Desktop media-tool resolver.
//!
//! Windows production builds use one pinned LGPL FFmpeg bundle under the Tauri
//! resource directory.  The resolver never falls back to PATH, so a developer
//! machine installation cannot make an otherwise incomplete package appear valid.

use serde::Deserialize;
use sha2::{Digest, Sha256};
#[cfg(windows)]
use std::sync::OnceLock;
use std::{
    collections::BTreeSet,
    fs,
    io::Read,
    path::{Path, PathBuf},
    process::{Command, Stdio},
    thread,
    time::{Duration, Instant},
};

const MANIFEST_SCHEMA_VERSION: u32 = 1;
const WINDOWS_BUNDLE_VERSION: &str = "n9.0.1-69-g3e11912860-20260917";
const WINDOWS_TARGET: &str = "x86_64-pc-windows-msvc";
const WINDOWS_LICENSE: &str = "LGPL-3.0-or-later";
const WINDOWS_ARCHIVE_SHA256: &str =
    "3fa84213e8c38fae99cdab620cb47f8cebfef6c550d5e950700948133edce5c6";
const WINDOWS_LICENSE_BYTES: u64 = 7_651;
const WINDOWS_LICENSE_SHA256: &str =
    "da7eabb7bafdf7d3ae5e9f223aa5bdc1eece45ac569dc21b3b037520b4464768";
const WINDOWS_SOURCE_BYTES: u64 = 463;
const WINDOWS_SOURCE_SHA256: &str =
    "4d98c6ce49419a9c249a97afeda21d7d217a8d15867443eff9a45113f9ac1541";
const WINDOWS_RUNTIME_FILES: [(&str, u64, &str); 9] = [
    (
        "avcodec-63.dll",
        90_353_664,
        "76c90b2146a6c26d195273bec51f347ed35340cc3bdf79f48fec7d35270deb2d",
    ),
    (
        "avdevice-63.dll",
        4_047_872,
        "fcfde53726e6e5e45def2443e37609d72b054083475b2bfc8ba360d2a1e6ce7d",
    ),
    (
        "avfilter-12.dll",
        30_806_016,
        "27455514b1ceab60f968532108edefb26716b7f0072beb855cf006c11c23304f",
    ),
    (
        "avformat-63.dll",
        22_668_800,
        "2cd3c7fa0e037bb9d5787c129d4a71d351d4d073fee4b91edb63df6fc4aafee1",
    ),
    (
        "avutil-61.dll",
        3_014_144,
        "198c72089402cd83ddd5c66fa50226ad4f716eacc43d10ef7c6a81e5af3a4f05",
    ),
    (
        "ffmpeg.exe",
        538_112,
        "2deb3e0609ef34435248c0f46c8e597638924e1390028c124a154302186c1098",
    ),
    (
        "ffprobe.exe",
        228_864,
        "437a508fcd30fb4d8a02ca91e85af64278e3217c208899a0c02c8a3cc1fa0950",
    ),
    (
        "swresample-7.dll",
        733_696,
        "85846e143863936ec50ca5a64e27d6ca4b5a4e035efe521b1e9e2fdfe80e327f",
    ),
    (
        "swscale-10.dll",
        2_330_624,
        "f56f60a3b4299d16be4b7682683fac39d5686a42aa88bc93583abc9cd7e803cf",
    ),
];

#[derive(Debug, Clone)]
pub struct MediaTools {
    pub ffmpeg: PathBuf,
    pub ffprobe: PathBuf,
    pub version: String,
}

#[derive(Debug)]
pub(crate) enum MediaCommandError {
    Cancelled,
    Failed(String),
}

pub(crate) struct MediaCommandOutput {
    pub status: std::process::ExitStatus,
    pub stdout: Vec<u8>,
    pub stderr: Vec<u8>,
}

#[cfg(windows)]
static WINDOWS_TOOLS: OnceLock<Result<MediaTools, String>> = OnceLock::new();

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct RuntimeManifest {
    schema_version: u32,
    bundle_version: String,
    target: String,
    license: String,
    archive_sha256: String,
    files: Vec<RuntimeFile>,
}

#[derive(Debug, Clone, Deserialize)]
struct RuntimeFile {
    name: String,
    bytes: u64,
    sha256: String,
}

pub fn resolve(resource_dir: Option<&Path>) -> Result<MediaTools, String> {
    #[cfg(windows)]
    {
        let resource_dir = resource_dir.ok_or_else(|| "应用媒体资源目录不可用".to_owned())?;
        return WINDOWS_TOOLS
            .get_or_init(|| {
                resolve_windows_bundle(&resource_dir.join("media-tools/windows-x86_64"))
            })
            .clone();
    }

    #[cfg(target_os = "macos")]
    {
        let _ = resource_dir;
        return resolve_macos_tools();
    }

    #[allow(unreachable_code)]
    Err("当前平台尚未提供受控媒体工具".to_owned())
}

pub fn probe_duration_millis(
    resource_dir: Option<&Path>,
    asset_path: &Path,
) -> Result<u64, String> {
    let tools = resolve(resource_dir)?;
    let mut command = Command::new(&tools.ffprobe);
    command
        .args([
            "-v",
            "error",
            "-show_entries",
            "format=duration",
            "-of",
            "default=noprint_wrappers=1:nokey=1",
        ])
        .arg(asset_path);
    let output = run_command_with_cancel(command, Duration::from_secs(30), || Ok(false)).map_err(
        |error| match error {
            MediaCommandError::Cancelled => "媒体时长探测已取消".to_owned(),
            MediaCommandError::Failed(detail) => detail,
        },
    )?;
    if !output.status.success() {
        return Err(format!(
            "ffprobe exit={}",
            output.status.code().unwrap_or(-1)
        ));
    }
    let seconds = String::from_utf8_lossy(&output.stdout)
        .trim()
        .parse::<f64>()
        .map_err(|_| "ffprobe duration invalid".to_owned())?;
    if !seconds.is_finite() || seconds <= 0.0 || seconds > 4.0 * 60.0 * 60.0 {
        return Err("ffprobe duration outside preview limit".to_owned());
    }
    Ok((seconds * 1000.0).round() as u64)
}

pub(crate) fn run_command_with_cancel<F>(
    mut command: Command,
    timeout: Duration,
    mut should_cancel: F,
) -> Result<MediaCommandOutput, MediaCommandError>
where
    F: FnMut() -> Result<bool, String>,
{
    command
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        command.creation_flags(0x0800_0000);
    }
    let mut child = command
        .spawn()
        .map_err(|_| MediaCommandError::Failed("media tool launch failed".to_owned()))?;
    let stdout = child
        .stdout
        .take()
        .ok_or_else(|| MediaCommandError::Failed("media tool stdout unavailable".to_owned()))?;
    let stderr = child
        .stderr
        .take()
        .ok_or_else(|| MediaCommandError::Failed("media tool stderr unavailable".to_owned()))?;
    let stdout_reader = thread::spawn(move || read_bounded_pipe(stdout));
    let stderr_reader = thread::spawn(move || read_bounded_pipe(stderr));
    let started = Instant::now();
    let result = loop {
        match should_cancel() {
            Ok(true) => {
                terminate_exact_child(&mut child);
                break Err(MediaCommandError::Cancelled);
            }
            Ok(false) => {}
            Err(detail) => {
                terminate_exact_child(&mut child);
                break Err(MediaCommandError::Failed(format!(
                    "media cancellation state unavailable: {detail}"
                )));
            }
        }
        match child.try_wait() {
            Ok(Some(status)) => break Ok(status),
            Ok(None) if started.elapsed() < timeout => thread::sleep(Duration::from_millis(25)),
            Ok(None) => {
                terminate_exact_child(&mut child);
                break Err(MediaCommandError::Failed("media tool timed out".to_owned()));
            }
            Err(_) => {
                terminate_exact_child(&mut child);
                break Err(MediaCommandError::Failed(
                    "media tool wait failed".to_owned(),
                ));
            }
        }
    };
    let stdout = stdout_reader.join().unwrap_or_default();
    let stderr = stderr_reader.join().unwrap_or_default();
    result.map(|status| MediaCommandOutput {
        status,
        stdout,
        stderr,
    })
}

fn read_bounded_pipe(mut pipe: impl Read) -> Vec<u8> {
    const MAX_CAPTURE_BYTES: usize = 1024 * 1024;
    let mut captured = Vec::new();
    let mut buffer = [0u8; 8192];
    loop {
        let Ok(count) = pipe.read(&mut buffer) else {
            break;
        };
        if count == 0 {
            break;
        }
        let remaining = MAX_CAPTURE_BYTES.saturating_sub(captured.len());
        captured.extend_from_slice(&buffer[..count.min(remaining)]);
    }
    captured
}

fn terminate_exact_child(child: &mut std::process::Child) {
    let _ = child.kill();
    let _ = child.wait();
}

#[cfg(any(windows, test))]
fn resolve_windows_bundle(root: &Path) -> Result<MediaTools, String> {
    let expected_files = WINDOWS_RUNTIME_FILES
        .iter()
        .map(|(name, bytes, sha256)| RuntimeFile {
            name: (*name).to_owned(),
            bytes: *bytes,
            sha256: (*sha256).to_owned(),
        })
        .collect::<Vec<_>>();
    let tools = validate_windows_bundle(root, &expected_files)?;
    validate_fixed_notice(
        root,
        "LICENSE-FFMPEG-LGPL-3.0.txt",
        WINDOWS_LICENSE_BYTES,
        WINDOWS_LICENSE_SHA256,
    )?;
    validate_fixed_notice(
        root,
        "SOURCE.txt",
        WINDOWS_SOURCE_BYTES,
        WINDOWS_SOURCE_SHA256,
    )?;
    Ok(tools)
}

#[cfg(any(windows, test))]
fn validate_fixed_notice(root: &Path, name: &str, bytes: u64, sha256: &str) -> Result<(), String> {
    let path = root.join(name);
    let metadata = fs::symlink_metadata(&path)
        .map_err(|_| "Windows 媒体工具许可信息缺失，请重新安装南枫 AI".to_owned())?;
    if !metadata.is_file() || metadata.file_type().is_symlink() || metadata.len() != bytes {
        return Err("Windows 媒体工具许可信息无效，请重新安装南枫 AI".to_owned());
    }
    if sha256_file(&path)? != sha256 {
        return Err("Windows 媒体工具许可信息校验失败，请重新安装南枫 AI".to_owned());
    }
    Ok(())
}

#[cfg(any(windows, test))]
fn validate_windows_bundle(
    root: &Path,
    expected_files: &[RuntimeFile],
) -> Result<MediaTools, String> {
    let root_metadata = fs::symlink_metadata(root)
        .map_err(|_| "Windows 媒体工具包缺失，请重新安装南枫 AI".to_owned())?;
    if !root_metadata.is_dir() || root_metadata.file_type().is_symlink() {
        return Err("Windows 媒体工具包目录无效，请重新安装南枫 AI".to_owned());
    }
    let canonical_root = root
        .canonicalize()
        .map_err(|_| "Windows 媒体工具包目录不可验证".to_owned())?;
    let manifest_bytes = fs::read(root.join("runtime-manifest.json"))
        .map_err(|_| "Windows 媒体工具清单缺失，请重新安装南枫 AI".to_owned())?;
    if manifest_bytes.len() > 65_536 {
        return Err("Windows 媒体工具清单过大，请重新安装南枫 AI".to_owned());
    }
    let manifest: RuntimeManifest = serde_json::from_slice(&manifest_bytes)
        .map_err(|_| "Windows 媒体工具清单损坏，请重新安装南枫 AI".to_owned())?;
    if manifest.schema_version != MANIFEST_SCHEMA_VERSION
        || manifest.bundle_version != WINDOWS_BUNDLE_VERSION
        || manifest.target != WINDOWS_TARGET
        || manifest.license != WINDOWS_LICENSE
        || manifest.archive_sha256 != WINDOWS_ARCHIVE_SHA256
    {
        return Err("Windows 媒体工具版本或架构不匹配，请重新安装南枫 AI".to_owned());
    }

    let expected = expected_files
        .iter()
        .map(|file| file.name.as_str())
        .collect::<BTreeSet<_>>();
    let actual = manifest
        .files
        .iter()
        .map(|file| file.name.as_str())
        .collect::<BTreeSet<_>>();
    if actual.len() != manifest.files.len() || actual != expected {
        return Err("Windows 媒体工具文件清单不完整，请重新安装南枫 AI".to_owned());
    }

    let bin = root.join("bin");
    for expected_file in expected_files {
        let file = manifest
            .files
            .iter()
            .find(|file| file.name == expected_file.name)
            .ok_or_else(|| "Windows 媒体工具文件清单不完整，请重新安装南枫 AI".to_owned())?;
        if file.bytes != expected_file.bytes
            || file.sha256.to_ascii_lowercase() != expected_file.sha256
        {
            return Err(format!(
                "Windows 媒体工具固定清单不匹配：{}",
                expected_file.name
            ));
        }
        let path = bin.join(&expected_file.name);
        let metadata = fs::symlink_metadata(&path)
            .map_err(|_| format!("Windows 媒体工具文件缺失：{}", expected_file.name))?;
        if !metadata.is_file()
            || metadata.file_type().is_symlink()
            || metadata.len() != expected_file.bytes
        {
            return Err(format!("Windows 媒体工具文件无效：{}", expected_file.name));
        }
        let canonical = path
            .canonicalize()
            .map_err(|_| format!("Windows 媒体工具文件不可验证：{}", expected_file.name))?;
        if !canonical.starts_with(&canonical_root) {
            return Err(format!("Windows 媒体工具文件越界：{}", expected_file.name));
        }
        if sha256_file(&canonical)? != expected_file.sha256 {
            return Err(format!(
                "Windows 媒体工具文件校验失败：{}",
                expected_file.name
            ));
        }
        validate_x64_pe(&canonical)
            .map_err(|_| format!("Windows 媒体工具架构不匹配：{}", expected_file.name))?;
    }
    for notice in ["LICENSE-FFMPEG-LGPL-3.0.txt", "SOURCE.txt"] {
        let metadata = fs::symlink_metadata(root.join(notice))
            .map_err(|_| "Windows 媒体工具许可信息缺失，请重新安装南枫 AI".to_owned())?;
        if !metadata.is_file() || metadata.file_type().is_symlink() {
            return Err("Windows 媒体工具许可信息无效，请重新安装南枫 AI".to_owned());
        }
    }
    Ok(MediaTools {
        ffmpeg: bin.join("ffmpeg.exe"),
        ffprobe: bin.join("ffprobe.exe"),
        version: manifest.bundle_version,
    })
}

#[cfg(target_os = "macos")]
fn resolve_macos_tools() -> Result<MediaTools, String> {
    fn find(name: &str) -> Option<PathBuf> {
        [
            format!("/opt/homebrew/bin/{name}"),
            format!("/usr/local/bin/{name}"),
            format!("/usr/bin/{name}"),
        ]
        .into_iter()
        .map(PathBuf::from)
        .find(|path| path.is_file())
    }
    let ffmpeg = find("ffmpeg").ok_or_else(|| "未找到 ffmpeg；请安装 FFmpeg 后重试".to_owned())?;
    let ffprobe =
        find("ffprobe").ok_or_else(|| "未找到 ffprobe；请安装 FFmpeg 后重试".to_owned())?;
    Ok(MediaTools {
        ffmpeg,
        ffprobe,
        version: "system-macos".to_owned(),
    })
}

#[cfg(any(windows, test))]
fn sha256_file(path: &Path) -> Result<String, String> {
    let mut file = fs::File::open(path).map_err(|_| "媒体工具文件不可读".to_owned())?;
    let mut digest = Sha256::new();
    let mut buffer = [0u8; 64 * 1024];
    loop {
        let count = file
            .read(&mut buffer)
            .map_err(|_| "媒体工具文件读取失败".to_owned())?;
        if count == 0 {
            break;
        }
        digest.update(&buffer[..count]);
    }
    Ok(format!("{:x}", digest.finalize()))
}

#[cfg(any(windows, test))]
fn validate_x64_pe(path: &Path) -> Result<(), ()> {
    let mut file = fs::File::open(path).map_err(|_| ())?;
    let mut header = [0u8; 64];
    file.read_exact(&mut header).map_err(|_| ())?;
    if &header[..2] != b"MZ" {
        return Err(());
    }
    let offset = u32::from_le_bytes(header[0x3c..0x40].try_into().map_err(|_| ())?) as u64;
    use std::io::{Seek, SeekFrom};
    file.seek(SeekFrom::Start(offset)).map_err(|_| ())?;
    let mut signature = [0u8; 6];
    file.read_exact(&mut signature).map_err(|_| ())?;
    if &signature[..4] != b"PE\0\0" || u16::from_le_bytes([signature[4], signature[5]]) != 0x8664 {
        return Err(());
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;
    use tempfile::tempdir;

    fn fake_x64_pe() -> Vec<u8> {
        let mut bytes = vec![0u8; 256];
        bytes[..2].copy_from_slice(b"MZ");
        bytes[0x3c..0x40].copy_from_slice(&0x80u32.to_le_bytes());
        bytes[0x80..0x84].copy_from_slice(b"PE\0\0");
        bytes[0x84..0x86].copy_from_slice(&0x8664u16.to_le_bytes());
        bytes
    }

    fn fixture() -> (tempfile::TempDir, PathBuf, Vec<RuntimeFile>) {
        let dir = tempdir().unwrap();
        let root = dir.path().join("windows-x86_64");
        let bin = root.join("bin");
        fs::create_dir_all(&bin).unwrap();
        let bytes = fake_x64_pe();
        let sha = format!("{:x}", Sha256::digest(&bytes));
        let expected = WINDOWS_RUNTIME_FILES
            .iter()
            .map(|(name, _, _)| {
                fs::write(bin.join(name), &bytes).unwrap();
                RuntimeFile {
                    name: (*name).to_owned(),
                    bytes: bytes.len() as u64,
                    sha256: sha.clone(),
                }
            })
            .collect::<Vec<_>>();
        let files = expected
            .iter()
            .map(|file| json!({"name":file.name,"bytes":file.bytes,"sha256":file.sha256}))
            .collect::<Vec<_>>();
        fs::write(root.join("LICENSE-FFMPEG-LGPL-3.0.txt"), "license").unwrap();
        fs::write(root.join("SOURCE.txt"), "source").unwrap();
        fs::write(
            root.join("runtime-manifest.json"),
            serde_json::to_vec(&json!({
                "schemaVersion":MANIFEST_SCHEMA_VERSION,
                "bundleVersion":WINDOWS_BUNDLE_VERSION,
                "target":WINDOWS_TARGET,
                "license":WINDOWS_LICENSE,
                "archiveSha256":WINDOWS_ARCHIVE_SHA256,
                "files":files
            }))
            .unwrap(),
        )
        .unwrap();
        (dir, root, expected)
    }

    #[test]
    fn fixed_windows_bundle_requires_exact_manifest_hashes_and_x64_pe_files() {
        let (_dir, root, expected) = fixture();
        let tools = validate_windows_bundle(&root, &expected).unwrap();
        assert_eq!(tools.version, WINDOWS_BUNDLE_VERSION);
        assert_eq!(tools.ffmpeg.file_name().unwrap(), "ffmpeg.exe");
        fs::write(
            &tools.ffprobe,
            fake_x64_pe().into_iter().chain([1]).collect::<Vec<_>>(),
        )
        .unwrap();
        assert!(validate_windows_bundle(&root, &expected)
            .unwrap_err()
            .contains("ffprobe.exe"));
    }

    #[test]
    fn fixed_windows_bundle_rejects_wrong_target_before_execution() {
        let (_dir, root, expected) = fixture();
        let manifest = root.join("runtime-manifest.json");
        let mut value: serde_json::Value =
            serde_json::from_slice(&fs::read(&manifest).unwrap()).unwrap();
        value["target"] = json!("aarch64-pc-windows-msvc");
        fs::write(manifest, serde_json::to_vec(&value).unwrap()).unwrap();
        assert!(validate_windows_bundle(&root, &expected)
            .unwrap_err()
            .contains("架构不匹配"));
    }

    #[test]
    fn runtime_manifest_cannot_redefine_the_compiled_file_hashes() {
        let (_dir, root, _expected) = fixture();
        assert!(resolve_windows_bundle(&root)
            .unwrap_err()
            .contains("固定清单不匹配"));
    }

    #[cfg(windows)]
    #[test]
    #[ignore = "requires the prepared pinned Windows FFmpeg resource bundle"]
    fn prepared_windows_bundle_runs_from_unicode_path_without_path_lookup() {
        use std::os::windows::process::CommandExt;
        use std::process::{Command, Stdio};

        let root = PathBuf::from(
            std::env::var_os("NANFENG_WINDOWS_MEDIA_TOOLS_ROOT")
                .expect("NANFENG_WINDOWS_MEDIA_TOOLS_ROOT must point at the prepared bundle"),
        );
        let tools = resolve_windows_bundle(&root).unwrap();
        let directory = tempdir().unwrap();
        let output = directory.path().join("南枫 媒体 空格.wav");
        let status = Command::new(&tools.ffmpeg)
            .args([
                "-nostdin",
                "-hide_banner",
                "-loglevel",
                "error",
                "-f",
                "lavfi",
                "-i",
                "sine=frequency=440:duration=1",
                "-c:a",
                "pcm_s16le",
                "-y",
            ])
            .arg(&output)
            .stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(Stdio::piped())
            .creation_flags(0x0800_0000)
            .status()
            .unwrap();
        assert!(status.success());
        let probe = Command::new(&tools.ffprobe)
            .args([
                "-v",
                "error",
                "-show_entries",
                "format=duration",
                "-of",
                "default=noprint_wrappers=1:nokey=1",
            ])
            .arg(&output)
            .creation_flags(0x0800_0000)
            .output()
            .unwrap();
        assert!(probe.status.success());
        let duration = String::from_utf8(probe.stdout)
            .unwrap()
            .trim()
            .parse::<f64>()
            .unwrap();
        assert!((0.95..=1.05).contains(&duration));
        let resource_dir = root.parent().and_then(Path::parent).unwrap();
        assert!((950..=1050).contains(&probe_duration_millis(Some(resource_dir), &output).unwrap()));
    }
}
