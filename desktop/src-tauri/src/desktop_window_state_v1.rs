//! Device-local persistence for the Desktop main-window geometry.
//!
//! This is intentionally kept in the app config directory instead of workspace SQLite:
//! the window belongs to this device and must not follow an imported, restored, or synced
//! workspace. Corrupt or stale state is ignored so it can never block opening the app.

use serde::{Deserialize, Serialize};
use std::{
    fs,
    path::{Path, PathBuf},
    sync::{Arc, Mutex},
    time::Duration,
};
use tauri::{Manager, PhysicalPosition, PhysicalSize, Runtime, WebviewWindow, WindowEvent};

const STATE_FILE_NAME: &str = "window-state.json";
const STATE_VERSION: u8 = 1;
const DEFAULT_WIDTH: u32 = 1440;
const DEFAULT_HEIGHT: u32 = 900;
const MIN_WIDTH: u32 = 780;
const MIN_HEIGHT: u32 = 560;
const MAX_DIMENSION: u32 = 16_384;
const SAVE_INTERVAL: Duration = Duration::from_millis(300);

#[derive(Debug, Clone, Copy, Deserialize, Serialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct WindowGeometry {
    pub x: i32,
    pub y: i32,
    pub width: u32,
    pub height: u32,
    pub maximized: bool,
}

impl Default for WindowGeometry {
    fn default() -> Self {
        Self {
            x: 0,
            y: 0,
            width: DEFAULT_WIDTH,
            height: DEFAULT_HEIGHT,
            maximized: false,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct MonitorBounds {
    pub x: i32,
    pub y: i32,
    pub width: u32,
    pub height: u32,
}

#[derive(Debug, Clone, Copy, Deserialize, Serialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
struct PersistedWindowState {
    version: u8,
    geometry: WindowGeometry,
}

#[derive(Debug)]
struct PersistenceState {
    geometry: WindowGeometry,
    revision: u64,
}

fn valid_geometry(geometry: WindowGeometry) -> Option<WindowGeometry> {
    (geometry.width >= MIN_WIDTH
        && geometry.height >= MIN_HEIGHT
        && geometry.width <= MAX_DIMENSION
        && geometry.height <= MAX_DIMENSION)
        .then_some(geometry)
}

pub fn is_geometry_visible(geometry: WindowGeometry, monitors: &[MonitorBounds]) -> bool {
    const VISIBLE_TITLEBAR_WIDTH: i64 = 80;
    const VISIBLE_TITLEBAR_HEIGHT: i64 = 32;
    let x = i64::from(geometry.x);
    let y = i64::from(geometry.y);
    monitors.iter().any(|monitor| {
        let left = i64::from(monitor.x);
        let top = i64::from(monitor.y);
        let right = left + i64::from(monitor.width);
        let bottom = top + i64::from(monitor.height);
        x + VISIBLE_TITLEBAR_WIDTH > left
            && x < right - VISIBLE_TITLEBAR_WIDTH
            && y + VISIBLE_TITLEBAR_HEIGHT > top
            && y < bottom - VISIBLE_TITLEBAR_HEIGHT
    })
}

fn read_geometry(path: &Path) -> Option<WindowGeometry> {
    let raw = fs::read(path).ok()?;
    let state: PersistedWindowState = serde_json::from_slice(&raw).ok()?;
    (state.version == STATE_VERSION)
        .then_some(state.geometry)
        .and_then(valid_geometry)
}

pub fn persist_geometry(path: &Path, geometry: WindowGeometry) -> Result<(), String> {
    let Some(geometry) = valid_geometry(geometry) else {
        return Err("窗口几何无效".to_owned());
    };
    let parent = path.parent().ok_or_else(|| "窗口状态路径无效".to_owned())?;
    fs::create_dir_all(parent).map_err(|_| "窗口状态目录不可写".to_owned())?;
    let payload = serde_json::to_vec(&PersistedWindowState {
        version: STATE_VERSION,
        geometry,
    })
    .map_err(|_| "窗口状态无法序列化".to_owned())?;
    let temporary = parent.join(format!(".{STATE_FILE_NAME}.{}.tmp", std::process::id()));
    fs::write(&temporary, payload).map_err(|_| "窗口状态无法写入".to_owned())?;
    let committed = replace_state_file(&temporary, path);
    if committed.is_err() {
        let _ = fs::remove_file(&temporary);
    }
    committed.map_err(|_| "窗口状态无法提交".to_owned())
}

#[cfg(windows)]
fn replace_state_file(temporary: &Path, destination: &Path) -> std::io::Result<()> {
    use std::os::windows::ffi::OsStrExt;
    use windows_sys::Win32::Storage::FileSystem::{
        MoveFileExW, MOVEFILE_REPLACE_EXISTING, MOVEFILE_WRITE_THROUGH,
    };
    let temporary: Vec<u16> = temporary.as_os_str().encode_wide().chain(Some(0)).collect();
    let destination: Vec<u16> = destination
        .as_os_str()
        .encode_wide()
        .chain(Some(0))
        .collect();
    let moved = unsafe {
        MoveFileExW(
            temporary.as_ptr(),
            destination.as_ptr(),
            MOVEFILE_REPLACE_EXISTING | MOVEFILE_WRITE_THROUGH,
        )
    };
    if moved == 0 {
        Err(std::io::Error::last_os_error())
    } else {
        Ok(())
    }
}

#[cfg(not(windows))]
fn replace_state_file(temporary: &Path, destination: &Path) -> std::io::Result<()> {
    fs::rename(temporary, destination)
}

fn resolve_state_path(config_directory: Option<PathBuf>, isolated_path: Option<PathBuf>) -> Option<PathBuf> {
    isolated_path.or_else(|| config_directory.map(|directory| directory.join(STATE_FILE_NAME)))
}

fn state_path<R: Runtime>(app: &tauri::App<R>, isolated_path: Option<PathBuf>) -> Option<PathBuf> {
    resolve_state_path(app.path().app_config_dir().ok(), isolated_path)
}

fn monitors<R: Runtime>(window: &WebviewWindow<R>) -> Vec<MonitorBounds> {
    window
        .available_monitors()
        .unwrap_or_default()
        .into_iter()
        .map(|monitor| {
            let position = monitor.position();
            let size = monitor.size();
            MonitorBounds {
                x: position.x,
                y: position.y,
                width: size.width,
                height: size.height,
            }
        })
        .collect()
}

fn snapshot<R: Runtime>(
    window: &WebviewWindow<R>,
    previous: WindowGeometry,
) -> Option<WindowGeometry> {
    let position = window.outer_position().ok()?;
    let size = window.inner_size().ok()?;
    let maximized = window.is_maximized().ok()?;
    if maximized {
        // Keep the most recent normal rectangle so unmaximizing after a restart is useful.
        Some(WindowGeometry {
            maximized: true,
            ..previous
        })
    } else {
        valid_geometry(WindowGeometry {
            x: position.x,
            y: position.y,
            width: size.width,
            height: size.height,
            maximized: false,
        })
    }
}

pub fn restore_geometry<R: Runtime>(window: &WebviewWindow<R>, geometry: WindowGeometry) {
    let Some(geometry) = valid_geometry(geometry) else {
        return;
    };
    let _ = window.set_size(PhysicalSize::new(geometry.width, geometry.height));
    let monitor_bounds = monitors(window);
    if !monitor_bounds.is_empty() && is_geometry_visible(geometry, &monitor_bounds) {
        let _ = window.set_position(PhysicalPosition::new(geometry.x, geometry.y));
    }
    if geometry.maximized {
        let _ = window.maximize();
    }
}

fn restore_desktop_window_state<R: Runtime>(
    window: &WebviewWindow<R>,
    path: &Path,
) -> WindowGeometry {
    let saved = read_geometry(path);
    if let Some(geometry) = saved {
        restore_geometry(window, geometry);
    }
    saved.unwrap_or_default()
}

/// Restores the last valid geometry before the first normal render, then records real native
/// movement, resize and close events. Diagnostics and background cycles do not call this owner.
pub fn install_desktop_window_state_persistence<R: Runtime>(app: &tauri::App<R>, isolated_path: Option<PathBuf>) {
    let Some(window) = app.get_webview_window("main") else {
        return;
    };
    let Some(path) = state_path(app, isolated_path) else {
        return;
    };
    let initial = restore_desktop_window_state(&window, &path);
    let persistence = Arc::new(Mutex::new(PersistenceState {
        geometry: initial,
        revision: 0,
    }));
    let event_window = window.clone();
    window.on_window_event(move |event| {
        let should_save = matches!(
            event,
            WindowEvent::Moved(_) | WindowEvent::Resized(_) | WindowEvent::CloseRequested { .. }
        );
        if !should_save {
            return;
        }
        let closing = matches!(event, WindowEvent::CloseRequested { .. });
        let revision = {
            let Ok(mut state) = persistence.lock() else {
                return;
            };
            state.revision = state.revision.saturating_add(1);
            if closing {
                if let Some(geometry) = snapshot(&event_window, state.geometry) {
                    state.geometry = geometry;
                    let _ = persist_geometry(&path, geometry);
                }
                return;
            }
            state.revision
        };
        let delayed_window = event_window.clone();
        let delayed_persistence = Arc::clone(&persistence);
        let delayed_path = path.clone();
        tauri::async_runtime::spawn(async move {
            // Windows can dispatch the final maximized resize before its
            // maximized flag is observable. Debouncing also avoids replacing
            // the state file for every pixel of a drag.
            tokio::time::sleep(SAVE_INTERVAL).await;
            let Ok(mut state) = delayed_persistence.lock() else {
                return;
            };
            if state.revision != revision {
                return;
            }
            let Some(geometry) = snapshot(&delayed_window, state.geometry) else {
                return;
            };
            state.geometry = geometry;
            let _ = persist_geometry(&delayed_path, geometry);
        });
    });
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

    #[test]
    fn marked_acceptance_roots_keep_independent_geometry_and_preserve_shared_config() {
        let directory = tempdir().unwrap();
        let shared = directory.path().join("shared-config");
        let shared_path = resolve_state_path(Some(shared.clone()), None).unwrap();
        persist_geometry(&shared_path, WindowGeometry::default()).unwrap();
        let shared_before = fs::read(&shared_path).unwrap();
        let first = directory.path().join("acceptance-one/.device-config/window-state.json");
        let second = directory.path().join("acceptance-two/.device-config/window-state.json");
        let one = WindowGeometry { x: 40, y: 60, width: 1000, height: 700, maximized: false };
        let two = WindowGeometry { x: 120, y: 80, width: 1200, height: 800, maximized: true };
        persist_geometry(&resolve_state_path(Some(shared.clone()), Some(first.clone())).unwrap(), one).unwrap();
        persist_geometry(&resolve_state_path(Some(shared.clone()), Some(second.clone())).unwrap(), two).unwrap();
        assert_eq!(read_geometry(&first), Some(one));
        assert_eq!(read_geometry(&second), Some(two));
        assert_eq!(fs::read(&shared_path).unwrap(), shared_before);
        assert_eq!(resolve_state_path(Some(shared), None), Some(shared_path));
        assert_eq!(resolve_state_path(None, Some(first.clone())), Some(first));
        assert_eq!(resolve_state_path(None, None), None);
    }

    #[test]
    fn visible_geometry_accepts_a_second_monitor_with_negative_coordinates() {
        let geometry = WindowGeometry {
            x: -1_600,
            y: 120,
            width: 1_100,
            height: 700,
            maximized: false,
        };
        assert!(is_geometry_visible(
            geometry,
            &[
                MonitorBounds {
                    x: -1_920,
                    y: 0,
                    width: 1_920,
                    height: 1_080
                },
                MonitorBounds {
                    x: 0,
                    y: 0,
                    width: 2_560,
                    height: 1_440
                },
            ]
        ));
    }

    #[test]
    fn offscreen_geometry_is_rejected_without_losing_a_valid_size_record() {
        let geometry = WindowGeometry {
            x: 9_000,
            y: 9_000,
            width: 1_440,
            height: 900,
            maximized: false,
        };
        assert!(!is_geometry_visible(
            geometry,
            &[MonitorBounds {
                x: 0,
                y: 0,
                width: 1_920,
                height: 1_080
            }]
        ));
        assert!(valid_geometry(geometry).is_some());
    }

    #[test]
    fn persist_geometry_round_trips_only_a_valid_device_local_record() {
        let directory = tempdir().unwrap();
        let path = directory.path().join(STATE_FILE_NAME);
        let geometry = WindowGeometry {
            x: 44,
            y: 66,
            width: 1_600,
            height: 980,
            maximized: true,
        };
        persist_geometry(&path, geometry).unwrap();
        assert_eq!(read_geometry(&path), Some(geometry));

        let latest = WindowGeometry {
            x: -280,
            y: 48,
            width: 1_320,
            height: 840,
            maximized: false,
        };
        persist_geometry(&path, latest).unwrap();
        assert_eq!(read_geometry(&path), Some(latest));
        assert!(persist_geometry(&path, WindowGeometry { width: 1, ..latest }).is_err());
        assert_eq!(read_geometry(&path), Some(latest));
    }
}
