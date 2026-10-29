//! Windows video frames from the verified private asset. No shell, network,
//! temporary movie copy or dependency on a user's ffmpeg/PATH installation.
use crate::desktop_media_tools_v1::{self, MediaCommandError};
use std::{
    io::Cursor,
    path::Path,
    process::Command,
    sync::atomic::{AtomicBool, Ordering},
    time::{Duration, Instant},
};
use windows::{
    core::{Error, Interface, Result, HRESULT, HSTRING},
    Foundation::{IClosable, TimeSpan},
    Media::Editing::{MediaClip, MediaComposition, VideoFramePrecision},
    Storage::{StorageFile, Streams::DataReader},
    Win32::System::WinRT::{RoInitialize, RoUninitialize, RO_INIT_MULTITHREADED},
};

struct Apartment;
impl Drop for Apartment {
    fn drop(&mut self) {
        unsafe { RoUninitialize() }
    }
}
#[derive(Default)]
struct Closables(Vec<IClosable>);
impl Closables {
    fn add<T: Interface>(&mut self, value: &T) -> Result<()> {
        self.0.push(value.cast()?);
        Ok(())
    }
}
impl Drop for Closables {
    fn drop(&mut self) {
        for value in self.0.iter().rev() {
            let _ = value.Close();
        }
    }
}
fn invalid() -> Error {
    Error::from_hresult(HRESULT(0x80070057u32 as i32))
}

fn cancelled() -> Error {
    Error::from_hresult(HRESULT(0x800704C7u32 as i32))
}

fn winrt_path(path: &Path) -> HSTRING {
    use std::os::windows::ffi::OsStrExt;
    let normalized: std::path::PathBuf = path.components().collect();
    let mut wide: Vec<u16> = normalized.as_os_str().encode_wide().collect();
    // Rust canonicalize returns verbatim paths. StorageFile rejects that prefix
    // even for ordinary private files. Preserve UTF-16 and the same disk/UNC
    // target; unknown device namespaces remain unsupported, never reinterpreted.
    if wide.starts_with(&[92, 92, 63, 92, 85, 78, 67, 92]) {
        wide.splice(0..8, [92, 92]);
    } else if wide.starts_with(&[92, 92, 63, 92])
        && wide.len() >= 7
        && ((65..=90).contains(&wide[4]) || (97..=122).contains(&wide[4]))
        && wide[5..7] == [58, 92]
    {
        wide.drain(0..4);
    }
    HSTRING::from_wide(&wide)
}

macro_rules! finish {
    ($start:expr, $deadline:expr, $cancelled:expr) => {{
        let operation = $start?;
        let mut cancel_requested = false;
        loop {
            if !cancel_requested && $cancelled.load(Ordering::SeqCst) {
                let _ = operation.Cancel();
                cancel_requested = true;
            }
            if Instant::now() >= $deadline {
                let _ = operation.Cancel();
                return Err(if cancel_requested {
                    cancelled()
                } else {
                    Error::from_hresult(HRESULT(0x800705B4u32 as i32))
                });
            }
            if operation.Status()?.0 != 0 {
                break;
            }
            std::thread::sleep(Duration::from_millis(5));
        }
        if cancel_requested {
            let _ = operation.GetResults();
            return Err(cancelled());
        }
        operation.GetResults()?
    }};
    ($start:expr, $deadline:expr) => {{
        let cancelled = AtomicBool::new(false);
        finish!($start, $deadline, &cancelled)
    }};
}

fn read_frame(path: &Path, timeout: Duration, cancellation: &AtomicBool) -> Result<Vec<u8>> {
    if cancellation.load(Ordering::SeqCst) {
        return Err(cancelled());
    }
    unsafe {
        RoInitialize(RO_INIT_MULTITHREADED)?;
    }
    let _apartment = Apartment;
    let mut closables = Closables::default();
    let deadline = Instant::now() + timeout;
    let file = finish!(
        StorageFile::GetFileFromPathAsync(&winrt_path(path)),
        deadline,
        cancellation
    );
    let clip = finish!(
        MediaClip::CreateFromFileAsync(&file),
        deadline,
        cancellation
    );
    let duration = clip.OriginalDuration()?.Duration;
    if duration <= 0 {
        return Err(invalid());
    }
    let composition = MediaComposition::new()?;
    composition.Clips()?.Append(&clip)?;
    // Match the Mac ffmpeg representative frame at 0.2s. Very short clips use
    // their midpoint instead of seeking beyond the end. Only width is supplied
    // so WinRT preserves the displayed aspect ratio, including rotation.
    let stream = finish!(
        composition.GetThumbnailAsync(
            TimeSpan {
                Duration: 2_000_000.min(duration / 2)
            },
            640,
            0,
            VideoFramePrecision::NearestFrame,
        ),
        deadline,
        cancellation
    );
    closables.add(&stream)?;
    let length = stream.Size()?;
    if length == 0 || length > 4 * 1024 * 1024 {
        return Err(invalid());
    }
    let input = stream.GetInputStreamAt(0)?;
    closables.add(&input)?;
    let reader = DataReader::CreateDataReader(&input)?;
    closables.add(&reader)?;
    if finish!(reader.LoadAsync(length as u32), deadline, cancellation) as u64 != length {
        return Err(invalid());
    }
    let mut bytes = vec![0u8; length as usize];
    reader.ReadBytes(&mut bytes)?;
    // ImageStream encoding is an OS detail. Existing IPC promises PNG bytes.
    let image = image::load_from_memory(&bytes).map_err(|_| invalid())?;
    let mut output = Cursor::new(Vec::new());
    image
        .thumbnail(640, 640)
        .write_to(&mut output, image::ImageFormat::Png)
        .map_err(|_| invalid())?;
    if cancellation.load(Ordering::SeqCst) {
        return Err(cancelled());
    }
    Ok(output.into_inner())
}

pub fn thumbnail_cancellable(
    path: &Path,
    resource_dir: Option<&Path>,
    cancellation: &AtomicBool,
) -> std::result::Result<Vec<u8>, String> {
    let result = std::thread::scope(|scope| {
        std::thread::Builder::new()
            .name("nf-video-thumbnail".into())
            .spawn_scoped(scope, || {
                read_frame(path, Duration::from_secs(15), cancellation)
            })
            .map_err(|_| "Windows 视频缩略图线程不可用".to_owned())?
            .join()
            .map_err(|_| "Windows 视频缩略图线程异常".to_owned())
    })?;
    match result {
        Err(error) if error.code().0 as u32 == 0xC00D5212 => {
            ffmpeg_thumbnail(path, resource_dir, cancellation)
        }
        other => other.map_err(|error| {
                if error.code().0 as u32 == 0x800704C7 {
                    "视频缩略图已取消".to_owned()
                } else {
                    format!("Windows 无法生成该视频缩略图（代码 {:08X}）；文件或系统解码器可能不支持，或读取超时", error.code().0 as u32)
                }
            }),
    }
}

fn ffmpeg_thumbnail(
    path: &Path,
    resource_dir: Option<&Path>,
    cancellation: &AtomicBool,
) -> std::result::Result<Vec<u8>, String> {
    if cancellation.load(Ordering::SeqCst) {
        return Err("视频缩略图已取消".to_owned());
    }
    let tools = desktop_media_tools_v1::resolve(resource_dir)
        .map_err(|_| "应用视频缩略图解码器不可用".to_owned())?;
    let mut command = Command::new(&tools.ffmpeg);
    command
        .args([
            "-nostdin",
            "-hide_banner",
            "-loglevel",
            "error",
            "-ss",
            "0.2",
            "-i",
        ])
        .arg(path)
        .args([
            "-frames:v",
            "1",
            "-vf",
            "scale=640:640:force_original_aspect_ratio=decrease",
            "-f",
            "image2pipe",
            "-vcodec",
            "png",
            "pipe:1",
        ]);
    let output =
        desktop_media_tools_v1::run_command_with_cancel(command, Duration::from_secs(8), || {
            Ok(cancellation.load(Ordering::SeqCst))
        })
        .map_err(|error| match error {
            MediaCommandError::Cancelled => "视频缩略图已取消".to_owned(),
            MediaCommandError::Failed(_) => "无法解码本地视频缩略图".to_owned(),
        })?;
    if !output.status.success() || output.stdout.len() > 4 * 1024 * 1024 {
        return Err("无法解码本地视频缩略图".to_owned());
    }
    let image = image::load_from_memory_with_format(&output.stdout, image::ImageFormat::Png)
        .map_err(|_| "本地视频缩略图无效".to_owned())?;
    if image.width() == 0 || image.height() == 0 || image.width() > 640 || image.height() > 640 {
        return Err("本地视频缩略图无效".to_owned());
    }
    if cancellation.load(Ordering::SeqCst) {
        return Err("视频缩略图已取消".to_owned());
    }
    Ok(output.stdout)
}

#[cfg(test)]
fn thumbnail(path: &Path) -> std::result::Result<Vec<u8>, String> {
    thumbnail_cancellable(path, None, &AtomicBool::new(false))
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn winrt_video_path_preserves_disk_unc_and_unicode_identity() {
        assert_eq!(
            winrt_path(Path::new(r"\\?\C:\中文 空格\asset")).to_string(),
            r"C:\中文 空格\asset"
        );
        assert_eq!(
            winrt_path(Path::new(r"\\?\UNC\server\share\asset")).to_string(),
            r"\\server\share\asset"
        );
        assert_eq!(
            winrt_path(Path::new("C:/test/asset")).to_string(),
            r"C:\test\asset"
        );
        assert_eq!(
            winrt_path(Path::new(r"\\?\GLOBALROOT\Device\asset")).to_string(),
            r"\\?\GLOBALROOT\Device\asset"
        );
    }
    fn fixture(name: &str) -> std::path::PathBuf {
        Path::new(env!("CARGO_MANIFEST_DIR"))
            .join("tests/fixtures")
            .join(name)
            .components()
            .collect()
    }

    #[test]
    fn windows_video_thumbnail_decodes_private_extensionless_assets_and_releases_files() {
        for (name, expected, red) in [
            ("windows-video-red.mp4", (320, 180), true),
            ("windows-video-blue.mp4", (180, 320), false),
        ] {
            let root = tempfile::tempdir().unwrap();
            let path = root.path().join("中文 空格 视频资产");
            let bytes = std::fs::read(fixture(name))
                .expect("generate synthetic Windows video fixtures first");
            std::fs::write(&path, &bytes).unwrap();
            let png = thumbnail(&path.canonicalize().unwrap()).unwrap();
            assert!(png.starts_with(b"\x89PNG\r\n\x1a\n"));
            let image = image::load_from_memory(&png).unwrap().to_rgb8();
            // WinRT may upscale; enforce shape and maximum edge, not exact OS size.
            assert!(image.width() <= 640 && image.height() <= 640);
            assert!(
                (image.width() as f32 / image.height() as f32
                    - expected.0 as f32 / expected.1 as f32)
                    .abs()
                    < 0.02
            );
            let pixel = image.get_pixel(image.width() / 2, image.height() / 2);
            // H.264 YUV colour conversion need not preserve exact RGB samples.
            // A strongly dominant channel proves a decoded frame, unlike a
            // black/default icon or the other workspace's coloured fixture.
            assert!(
                if red {
                    i32::from(pixel[0]) - i32::from(pixel[2]) > 120
                } else {
                    i32::from(pixel[2]) - i32::from(pixel[0]) > 120
                },
                "fixture {name}, center pixel {pixel:?}, size {}x{}",
                image.width(),
                image.height()
            );
            assert_eq!(std::fs::read(&path).unwrap(), bytes);
            std::fs::remove_file(&path).expect("decoder must release asset handles");
        }
    }

    #[test]
    fn windows_video_thumbnail_rejects_corrupt_files_without_private_paths() {
        let root = tempfile::tempdir().unwrap();
        let path = root.path().join("private-name.mp4");
        std::fs::write(&path, b"broken mp4").unwrap();
        let error = thumbnail(&path).unwrap_err();
        assert!(!error.contains("private-name"));
        assert!(error.contains("代码"));
        std::fs::remove_file(&path).unwrap();
    }

    #[test]
    fn windows_video_thumbnail_timeout_is_bounded() {
        let started = Instant::now();
        let error = std::thread::spawn(|| {
            read_frame(
                &fixture("windows-video-red.mp4"),
                Duration::ZERO,
                &AtomicBool::new(false),
            )
        })
        .join()
        .unwrap()
        .unwrap_err();
        assert_eq!(error.code().0 as u32, 0x800705B4);
        assert!(started.elapsed() < Duration::from_secs(2));
    }

    #[test]
    fn windows_video_thumbnail_pre_cancel_is_bounded_and_private() {
        let cancellation = AtomicBool::new(true);
        let started = Instant::now();
        let error = thumbnail_cancellable(Path::new("private-missing-video"), None, &cancellation)
            .unwrap_err();
        assert_eq!(error, "视频缩略图已取消");
        assert!(!error.contains("private-missing-video"));
        assert!(started.elapsed() < Duration::from_secs(2));
    }

    #[test]
    fn windows_video_thumbnail_decodes_synthetic_vp9_with_bundled_tool() {
        let resources = Path::new(env!("CARGO_MANIFEST_DIR")).join("resources");
        let tools = desktop_media_tools_v1::resolve(Some(&resources)).unwrap();
        let root = tempfile::tempdir().unwrap();
        let path = root.path().join("中文 VP9 无扩展资产");
        let status = Command::new(&tools.ffmpeg)
            .args([
                "-nostdin",
                "-hide_banner",
                "-loglevel",
                "error",
                "-f",
                "lavfi",
                "-i",
                "color=c=green:s=320x180:d=1",
                "-c:v",
                "libvpx-vp9",
                "-b:v",
                "0",
                "-crf",
                "35",
                "-f",
                "mp4",
                "-y",
            ])
            .arg(&path)
            .status()
            .unwrap();
        assert!(status.success());
        let original = std::fs::read(&path).unwrap();
        let fallback = ffmpeg_thumbnail(&path, Some(&resources), &AtomicBool::new(false)).unwrap();
        assert!(fallback.starts_with(b"\x89PNG\r\n\x1a\n"));
        let png = thumbnail_cancellable(&path, Some(&resources), &AtomicBool::new(false)).unwrap();
        let image = image::load_from_memory_with_format(&png, image::ImageFormat::Png).unwrap();
        assert!(image.width() <= 640 && image.height() <= 640);
        assert!((image.width() as f32 / image.height() as f32 - 16.0 / 9.0).abs() < 0.02);
        assert_eq!(std::fs::read(&path).unwrap(), original);
        std::fs::remove_file(&path).unwrap();
    }

    // Reproducible, synthetic solid-colour MP4s encoded by Windows itself.
    // Explicit fixture-generation command only; normal tests never write repo files.
    #[test]
    #[ignore = "explicit fixture generation"]
    fn generate_windows_video_fixtures() {
        std::thread::spawn(|| -> Result<()> {
            use windows::{
                Media::{
                    MediaProperties::{MediaEncodingProfile, VideoEncodingQuality},
                    Transcoding::TranscodeFailureReason,
                },
                UI::Color,
            };
            unsafe {
                RoInitialize(RO_INIT_MULTITHREADED)?;
            }
            let _apartment = Apartment;
            let deadline = Instant::now() + Duration::from_secs(60);
            for (name, width, height, color) in [
                (
                    "windows-video-red.mp4",
                    320,
                    180,
                    Color {
                        A: 255,
                        R: 230,
                        G: 25,
                        B: 25,
                    },
                ),
                (
                    "windows-video-blue.mp4",
                    180,
                    320,
                    Color {
                        A: 255,
                        R: 25,
                        G: 25,
                        B: 230,
                    },
                ),
            ] {
                let path = fixture(name);
                std::fs::OpenOptions::new()
                    .write(true)
                    .create_new(true)
                    .open(&path)
                    .map_err(|_| invalid())?;
                let file = finish!(
                    StorageFile::GetFileFromPathAsync(&HSTRING::from(path.as_os_str())),
                    deadline
                );
                let clip = MediaClip::CreateFromColor(
                    color,
                    TimeSpan {
                        Duration: 10_000_000,
                    },
                )?;
                let composition = MediaComposition::new()?;
                composition.Clips()?.Append(&clip)?;
                let profile = MediaEncodingProfile::CreateMp4(VideoEncodingQuality::Vga)?;
                profile.Video()?.SetWidth(width)?;
                profile.Video()?.SetHeight(height)?;
                let result = finish!(
                    composition.RenderToFileWithProfileAsync(
                        &file,
                        windows::Media::Editing::MediaTrimmingPreference::Precise,
                        &profile
                    ),
                    deadline
                );
                if result != TranscodeFailureReason::None {
                    return Err(invalid());
                }
            }
            Ok(())
        })
        .join()
        .unwrap()
        .unwrap();
    }
}
