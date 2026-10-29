//! A request captures its database owner once; clones keep that owner across awaits.
//! Neither a mutable global selection nor conversation IDs decide where a request writes.
use crate::{AppState, DesktopDataArea};
use std::{ops::Deref, sync::Arc};
use tauri::{
    ipc::{CommandArg, CommandItem, InvokeError},
    Manager, State,
};

pub(crate) enum AreaState<'a> {
    Chat(State<'a, AppState>),
    Work(Arc<AppState>),
}

impl Deref for AreaState<'_> {
    type Target = AppState;

    fn deref(&self) -> &Self::Target {
        match self {
            Self::Chat(state) => state,
            Self::Work(state) => state,
        }
    }
}

fn request_area(command: &CommandItem<'_, tauri::Wry>) -> Result<DesktopDataArea, InvokeError> {
    let raw = command
        .message
        .headers()
        .get("x-nanfeng-data-area")
        .map(|value| {
            value
                .to_str()
                .map_err(|_| InvokeError::from("数据区域无效"))
        })
        .transpose()?
        .unwrap_or("CHAT");
    DesktopDataArea::parse(raw).map_err(InvokeError::from)
}

impl<'a> CommandArg<'a, tauri::Wry> for AreaState<'a> {
    fn from_command(command: CommandItem<'a, tauri::Wry>) -> Result<Self, InvokeError> {
        let area = request_area(&command)?;
        let state = command.message.state_ref().get::<AppState>();
        match area {
            DesktopDataArea::Chat => Ok(Self::Chat(state)),
            DesktopDataArea::Work => state
                .work_area
                .clone()
                .map(Self::Work)
                .ok_or_else(|| InvokeError::from("工作区数据库尚未就绪")),
        }
    }
}

#[derive(Clone)]
pub(crate) struct AreaAppHandle {
    native: tauri::AppHandle,
    area: DesktopDataArea,
}

impl Deref for AreaAppHandle {
    type Target = tauri::AppHandle;

    fn deref(&self) -> &Self::Target {
        &self.native
    }
}

impl<'a> CommandArg<'a, tauri::Wry> for AreaAppHandle {
    fn from_command(command: CommandItem<'a, tauri::Wry>) -> Result<Self, InvokeError> {
        let area = request_area(&command)?;
        if area == DesktopDataArea::Work
            && command
                .message
                .state_ref()
                .get::<AppState>()
                .work_area
                .is_none()
        {
            return Err(InvokeError::from("工作区数据库尚未就绪"));
        }
        Ok(Self::new(
            command.message.webview_ref().app_handle().clone(),
            area,
        ))
    }
}

impl AreaAppHandle {
    pub(crate) fn new(native: tauri::AppHandle, area: DesktopDataArea) -> Self {
        Self { native, area }
    }

    pub(crate) fn for_area(&self, area: DesktopDataArea) -> Self {
        Self::new(self.native.clone(), area)
    }

    pub(crate) fn root_state(&self) -> State<'_, AppState> {
        self.native.state::<AppState>()
    }

    pub(crate) fn area(&self) -> DesktopDataArea {
        self.area
    }

    pub(crate) fn area_state(&self) -> Result<AreaState<'_>, String> {
        let root = self.native.state::<AppState>();
        match self.area {
            DesktopDataArea::Chat => Ok(AreaState::Chat(root)),
            DesktopDataArea::Work => root
                .work_area
                .as_ref()
                .cloned()
                .map(AreaState::Work)
                .ok_or_else(|| "工作区数据库尚未就绪".to_owned()),
        }
    }

    pub(crate) fn emit<S: serde::Serialize + Clone>(
        &self,
        event: &str,
        payload: S,
    ) -> tauri::Result<()> {
        let mut value = serde_json::to_value(payload)?;
        if let Some(object) = value.as_object_mut() {
            object.insert("dataArea".into(), self.area.wire().into());
        }
        tauri::Emitter::emit(&self.native, event, value)
    }
}
pub(crate) fn migrate(connection: &rusqlite::Connection) -> Result<(), String> {
    connection.execute_batch("CREATE TABLE desktop_data_area_v1(id INTEGER PRIMARY KEY CHECK(id=1), area TEXT NOT NULL CHECK(area IN ('CHAT','WORK')), split_done INTEGER NOT NULL DEFAULT 0); INSERT INTO desktop_data_area_v1(id,area) VALUES(1,'CHAT');")
        .map_err(|_| "无法建立数据库区域身份".to_owned())
}
pub(crate) fn read_area(connection: &rusqlite::Connection) -> Result<DesktopDataArea, String> {
    let exists: bool = connection.query_row("SELECT EXISTS(SELECT 1 FROM sqlite_master WHERE type='table' AND name='desktop_data_area_v1')", [], |r| r.get(0)).map_err(|_| "区域身份读取失败")?;
    if !exists {
        return Ok(DesktopDataArea::Chat);
    } // Pre-split, versioned legacy database only.
    let raw: String = connection
        .query_row(
            "SELECT area FROM desktop_data_area_v1 WHERE id=1",
            [],
            |r| r.get(0),
        )
        .map_err(|_| "区域身份缺失")?;
    DesktopDataArea::parse(&raw)
}
pub(crate) fn content_area(content: &serde_json::Value) -> Result<DesktopDataArea, String> {
    match content.get("surface") {
        None => Ok(DesktopDataArea::Chat), // Old Desktop writer omitted this field.
        Some(value) => DesktopDataArea::parse(value.as_str().ok_or("云端区域身份无效")?),
    }
}
pub(crate) fn document_id(area: DesktopDataArea, conversation_id: &str) -> String {
    let identity = match area {
        DesktopDataArea::Chat => conversation_id.to_owned(),
        DesktopDataArea::Work => format!("WORK:{conversation_id}"),
    };
    format!("conversation-{}", &super::sha256(identity.as_bytes())[..40])
}

/// Shared app identity and the peer database are outside an area's deletion unit.
pub(crate) fn business_entries(root: &std::path::Path) -> Result<Vec<std::path::PathBuf>, String> {
    let mut entries = Vec::new();
    for entry in std::fs::read_dir(root).map_err(|_| "区域文件目录不可读")? {
        let entry = entry.map_err(|_| "区域文件不可读")?;
        let name = entry.file_name();
        if matches!(
            name.to_str(),
            Some(
                "work-area"
                    | "provider-credentials-v1"
                    | "account-sync-credentials-v2"
                    | ".runtime-owner.lock"
                    | "data-area-migration-v1"
                    | "app-configuration.sqlite3"
                    | "app-configuration.sqlite3-wal"
                    | "app-configuration.sqlite3-shm"
            )
        ) {
            continue;
        }
        if entry
            .file_type()
            .map_err(|_| "区域文件类型不可读")?
            .is_symlink()
        {
            return Err("区域文件包含符号链接，未继续".into());
        }
        entries.push(entry.path());
    }
    Ok(entries)
}

pub(crate) const CONFIG_APPLICATION_ID: i64 = 0x4e464343;
/// Only provider switches/catalog/defaults are app-wide. No conversation, context or history
/// table is eligible for this small configuration database.
pub(crate) fn configuration_database(
    root: &std::path::Path,
    legacy: &rusqlite::Connection,
) -> Result<std::path::PathBuf, String> {
    let path = root.join("app-configuration.sqlite3");
    let mut connection = rusqlite::Connection::open(&path).map_err(|_| "应用模型配置无法打开")?;
    connection
        .busy_timeout(std::time::Duration::from_secs(5))
        .map_err(|_| "应用模型配置超时无法设置")?;
    let tx = connection
        .transaction_with_behavior(rusqlite::TransactionBehavior::Immediate)
        .map_err(|_| "应用模型配置迁移暂不可用")?;
    let version: i64 = tx
        .pragma_query_value(None, "user_version", |r| r.get(0))
        .map_err(|_| "应用模型配置版本无法读取")?;
    if version > 1 {
        return Err("应用模型配置版本较新，未覆盖".into());
    }
    if version == 0 {
        for table in [
            "desktop_provider_settings",
            "p6g_catalog",
            "p6g_global_default",
        ] {
            let ddl: String = legacy
                .query_row(
                    "SELECT sql FROM sqlite_master WHERE type='table' AND name=?1",
                    [table],
                    |r| r.get(0),
                )
                .map_err(|_| "旧模型配置结构不可用")?;
            tx.execute_batch(&ddl).map_err(|_| "模型配置结构无法迁移")?;
            let mut statement = legacy
                .prepare(&format!("SELECT * FROM {table}"))
                .map_err(|_| "旧模型配置无法读取")?;
            let count = statement.column_count();
            let placeholders = std::iter::repeat_n("?", count)
                .collect::<Vec<_>>()
                .join(",");
            let rows = statement
                .query_map([], |row| {
                    (0..count)
                        .map(|i| row.get::<_, rusqlite::types::Value>(i))
                        .collect::<Result<Vec<_>, _>>()
                })
                .map_err(|_| "旧模型配置无法读取")?;
            for row in rows {
                tx.execute(
                    &format!("INSERT INTO {table} VALUES({placeholders})"),
                    rusqlite::params_from_iter(row.map_err(|_| "旧模型配置行无效")?),
                )
                .map_err(|_| "旧模型配置未迁移")?;
            }
        }
        tx.pragma_update(None, "application_id", CONFIG_APPLICATION_ID)
            .map_err(|_| "模型配置身份无法保存")?;
        tx.pragma_update(None, "user_version", 1)
            .map_err(|_| "模型配置版本无法保存")?;
    }
    let identity: i64 = tx
        .pragma_query_value(None, "application_id", |r| r.get(0))
        .map_err(|_| "模型配置身份无法读取")?;
    if identity != CONFIG_APPLICATION_ID {
        return Err("模型配置数据库身份不一致".into());
    }
    tx.commit().map_err(|_| "模型配置迁移未提交")?;
    Ok(path)
}
