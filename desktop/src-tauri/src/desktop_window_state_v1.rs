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
    time::{Duration, Instant},
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
    last_write: Option<Instant>,
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
    fs::rename(&temporary, path).map_err(|_| "窗口状态无法提交".to_owned())
}

fn state_path<R: Runtime>(app: &tauri::App<R>) -> Option<PathBuf> {
    app.path()
        .app_config_dir()
        .ok()
        .map(|directory| directory.join(STATE_FILE_NAME))
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
pub fn install_desktop_window_state_persistence<R: Runtime>(app: &tauri::App<R>) {
    let Some(window) = app.get_webview_window("main") else {
        return;
    };
    let Some(path) = state_path(app) else {
        return;
    };
    let initial = restore_desktop_window_state(&window, &path);
    let persistence = Arc::new(Mutex::new(PersistenceState {
        geometry: initial,
        last_write: None,
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
        let Ok(mut state) = persistence.lock() else {
            return;
        };
        let closing = matches!(event, WindowEvent::CloseRequested { .. });
        let Some(geometry) = snapshot(&event_window, state.geometry) else {
            return;
        };
        state.geometry = geometry;
        if !closing
            && state
                .last_write
                .is_some_and(|written| written.elapsed() < SAVE_INTERVAL)
        {
            return;
        }
        if persist_geometry(&path, geometry).is_ok() {
            state.last_write = Some(Instant::now());
        }
    });
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

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
        assert!(persist_geometry(
            &path,
            WindowGeometry {
                width: 1,
                ..geometry
            }
        )
        .is_err());
        assert_eq!(read_geometry(&path), Some(geometry));
    }
}
