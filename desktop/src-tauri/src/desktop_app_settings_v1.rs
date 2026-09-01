//! Native owner for Desktop appearance and Android-parity settings.
//!
//! The browser preview may keep ephemeral localStorage values, but a native build reads and
//! writes this single SQLite row. Capability flags are runtime facts: persisted preferences do
//! not make an unavailable Desktop consumer appear implemented.

use rusqlite::{params, Connection, OptionalExtension, Transaction};
use serde::{Deserialize, Serialize};
use std::time::{SystemTime, UNIX_EPOCH};

const VALID_MODES: &[&str] = &["system", "light", "dark"];
const VALID_FONT_SIZES: &[&str] = &["small", "standard", "large"];
const VALID_THEME_COLORS: &[&str] = &[
    "orange", "blue", "black", "green", "yellow", "pink", "purple",
];
const VALID_TONES: &[&str] = &[
    "default",
    "professional",
    "friendly",
    "direct",
    "efficient",
    "humorous",
];

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct AppearanceSettings {
    pub mode: String,
    pub font_size: String,
    pub theme_color: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct ProductSettings {
    pub memory_enabled: bool,
    pub history_library_enabled: bool,
    pub tone: String,
    pub nickname: String,
    pub occupation: String,
    /// Hidden compatibility field retained from Android's live personalization owner.
    /// It is deliberately not rendered as an editable Desktop field.
    pub interests: String,
    pub custom_instructions: String,
    pub monitor_notifications: bool,
    pub reminder_suggestions: bool,
    pub unread_indicators: bool,
    pub web_search_enabled: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct RuntimeCapabilities {
    pub ordinary_chat_personalization: bool,
    pub history_library: bool,
    pub monitor_notifications: bool,
    pub reminder_suggestions: bool,
    pub unread_indicators: bool,
    pub web_search: bool,
    pub google_account_sync: bool,
    pub update_service: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct Projection {
    pub appearance: AppearanceSettings,
    pub product: ProductSettings,
    pub capabilities: RuntimeCapabilities,
    pub revision: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct SaveArgs {
    pub appearance: AppearanceSettings,
    pub product: ProductSettings,
    pub expected_revision: i64,
}

pub fn migrate(transaction: &Transaction<'_>) -> Result<(), String> {
    transaction
        .execute_batch(
            "CREATE TABLE desktop_app_settings (
            id INTEGER PRIMARY KEY CHECK(id=1),
            appearance_mode TEXT NOT NULL,
            font_size TEXT NOT NULL,
            theme_color TEXT NOT NULL,
            memory_enabled INTEGER NOT NULL,
            history_library_enabled INTEGER NOT NULL,
            tone TEXT NOT NULL,
            nickname TEXT NOT NULL,
            occupation TEXT NOT NULL,
            custom_instructions TEXT NOT NULL,
            monitor_notifications INTEGER NOT NULL,
            reminder_suggestions INTEGER NOT NULL,
            unread_indicators INTEGER NOT NULL,
            web_search_enabled INTEGER NOT NULL,
            revision INTEGER NOT NULL,
            updated_at_ms INTEGER NOT NULL
        );
        INSERT INTO desktop_app_settings(
            id,appearance_mode,font_size,theme_color,memory_enabled,history_library_enabled,tone,
            nickname,occupation,custom_instructions,monitor_notifications,reminder_suggestions,
            unread_indicators,web_search_enabled,revision,updated_at_ms
        ) VALUES(1,'system','standard','orange',1,0,'default','','','',1,1,1,1,0,0);
        CREATE TABLE desktop_conversation_favorites (
            workspace_id TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
            conversation_id TEXT NOT NULL,
            favorited_at_ms INTEGER NOT NULL,
            PRIMARY KEY(workspace_id,conversation_id)
        );",
        )
        .map_err(|_| "Desktop 设置迁移失败".to_owned())
}

/// Portable, non-secret profile compatibility data is kept outside the device-only settings
/// row. Local backup/restore and encrypted account sync may preserve this one hidden field
/// without changing the contract that appearance, provider choices and window state stay local.
pub fn migrate_portable_personalization(transaction: &Transaction<'_>) -> Result<(), String> {
    transaction
        .execute_batch(
            "CREATE TABLE IF NOT EXISTS desktop_portable_personalization_v1 (
                id INTEGER PRIMARY KEY CHECK(id=1),
                interests TEXT NOT NULL,
                revision INTEGER NOT NULL,
                updated_at_ms INTEGER NOT NULL
            );
            INSERT OR IGNORE INTO desktop_portable_personalization_v1(id,interests,revision,updated_at_ms)
            VALUES(1,'',0,0);",
        )
        .map_err(|_| "Desktop 隐藏个性化字段迁移失败".to_owned())
}

pub fn read(connection: &Connection) -> Result<Projection, String> {
    connection.query_row(
        "SELECT appearance_mode,font_size,theme_color,memory_enabled,history_library_enabled,tone,
                nickname,occupation,
                COALESCE((SELECT interests FROM desktop_portable_personalization_v1 WHERE id=1),''),
                custom_instructions,monitor_notifications,reminder_suggestions,
                unread_indicators,web_search_enabled,revision
         FROM desktop_app_settings WHERE id=1",
        [],
        |row| Ok(Projection {
            appearance: AppearanceSettings { mode: row.get(0)?, font_size: row.get(1)?, theme_color: row.get(2)? },
            product: ProductSettings {
                memory_enabled: row.get::<_, i64>(3)? != 0,
                history_library_enabled: row.get::<_, i64>(4)? != 0,
                tone: row.get(5)?, nickname: row.get(6)?, occupation: row.get(7)?, interests: row.get(8)?, custom_instructions: row.get(9)?,
                monitor_notifications: row.get::<_, i64>(10)? != 0,
                reminder_suggestions: row.get::<_, i64>(11)? != 0,
                unread_indicators: row.get::<_, i64>(12)? != 0,
                web_search_enabled: row.get::<_, i64>(13)? != 0,
            },
            capabilities: unavailable_capabilities(),
            revision: row.get(14)?,
        }),
    ).map_err(|_| "Desktop 设置无法读取".to_owned())
}

pub fn save(connection: &mut Connection, args: SaveArgs) -> Result<Projection, String> {
    validate(&args)?;
    let transaction = connection
        .transaction()
        .map_err(|_| "Desktop 设置无法开始保存".to_owned())?;
    let changed = transaction
        .execute(
            "UPDATE desktop_app_settings SET appearance_mode=?1,font_size=?2,theme_color=?3,
            memory_enabled=?4,history_library_enabled=?5,tone=?6,nickname=?7,occupation=?8,
            custom_instructions=?9,monitor_notifications=?10,reminder_suggestions=?11,
            unread_indicators=?12,web_search_enabled=?13,revision=revision+1,updated_at_ms=?14
         WHERE id=1 AND revision=?15",
            params![
                args.appearance.mode,
                args.appearance.font_size,
                args.appearance.theme_color,
                bool_i64(args.product.memory_enabled),
                bool_i64(args.product.history_library_enabled),
                args.product.tone,
                args.product.nickname,
                args.product.occupation,
                args.product.custom_instructions,
                bool_i64(args.product.monitor_notifications),
                bool_i64(args.product.reminder_suggestions),
                bool_i64(args.product.unread_indicators),
                bool_i64(args.product.web_search_enabled),
                now_ms(),
                args.expected_revision,
            ],
        )
        .map_err(|_| "Desktop 设置保存失败".to_owned())?;
    if changed != 1 {
        return Err("Desktop 设置已在其他窗口更新；请刷新后重试".to_owned());
    }
    transaction
        .execute(
            "INSERT INTO desktop_portable_personalization_v1(id,interests,revision,updated_at_ms)
             VALUES(1,?1,1,?2)
             ON CONFLICT(id) DO UPDATE SET interests=excluded.interests,
                 revision=desktop_portable_personalization_v1.revision+1,
                 updated_at_ms=excluded.updated_at_ms",
            params![args.product.interests, now_ms()],
        )
        .map_err(|_| "关注方向兼容值无法保存".to_owned())?;
    transaction
        .commit()
        .map_err(|_| "Desktop 设置无法提交".to_owned())?;
    read(connection)
}

pub fn read_favorite_conversation_ids(
    connection: &Connection,
    workspace_id: &str,
) -> Result<Vec<String>, String> {
    validate_id(workspace_id)?;
    let mut statement = connection.prepare(
        "SELECT conversation_id FROM desktop_conversation_favorites WHERE workspace_id=?1 ORDER BY favorited_at_ms DESC,conversation_id",
    ).map_err(|_| "收藏会话无法读取".to_owned())?;
    let rows = statement
        .query_map([workspace_id], |row| row.get(0))
        .map_err(|_| "收藏会话无法读取".to_owned())?
        .collect::<Result<Vec<String>, _>>()
        .map_err(|_| "收藏会话无法读取".to_owned())?;
    Ok(rows)
}

pub fn set_conversation_favorite(
    connection: &mut Connection,
    workspace_id: &str,
    conversation_id: &str,
    favorite: bool,
) -> Result<Vec<String>, String> {
    validate_id(workspace_id)?;
    validate_id(conversation_id)?;
    let exchange: Option<String> = connection
        .query_row(
            "SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?1",
            [workspace_id],
            |row| row.get(0),
        )
        .optional()
        .map_err(|_| "无法核对收藏会话".to_owned())?;
    let exists = exchange
        .as_deref()
        .and_then(|raw| serde_json::from_str::<serde_json::Value>(raw).ok())
        .and_then(|value| {
            value
                .get("conversations")
                .and_then(|items| items.as_array())
                .cloned()
        })
        .is_some_and(|items| {
            items
                .iter()
                .any(|item| item.get("id").and_then(|id| id.as_str()) == Some(conversation_id))
        });
    if !exists {
        return Err("收藏目标会话不存在".to_owned());
    }
    if favorite {
        connection.execute(
            "INSERT INTO desktop_conversation_favorites(workspace_id,conversation_id,favorited_at_ms) VALUES(?1,?2,?3)
             ON CONFLICT(workspace_id,conversation_id) DO UPDATE SET favorited_at_ms=excluded.favorited_at_ms",
            params![workspace_id, conversation_id, now_ms()],
        ).map_err(|_| "会话收藏未保存".to_owned())?;
    } else {
        connection.execute(
            "DELETE FROM desktop_conversation_favorites WHERE workspace_id=?1 AND conversation_id=?2",
            params![workspace_id, conversation_id],
        ).map_err(|_| "会话取消收藏未保存".to_owned())?;
    }
    read_favorite_conversation_ids(connection, workspace_id)
}

fn validate(args: &SaveArgs) -> Result<(), String> {
    if !VALID_MODES.contains(&args.appearance.mode.as_str())
        || !VALID_FONT_SIZES.contains(&args.appearance.font_size.as_str())
        || !VALID_THEME_COLORS.contains(&args.appearance.theme_color.as_str())
        || !VALID_TONES.contains(&args.product.tone.as_str())
    {
        return Err("Desktop 设置包含无效选项".to_owned());
    }
    if args.product.nickname.chars().count() > 80
        || args.product.occupation.chars().count() > 120
        || args.product.interests.chars().count() > 500
        || args.product.custom_instructions.chars().count() > 6000
        || args.product.nickname.contains('\0')
        || args.product.occupation.contains('\0')
        || args.product.interests.contains('\0')
        || args.product.custom_instructions.contains('\0')
    {
        return Err("个性化内容超过可保存范围".to_owned());
    }
    Ok(())
}

fn validate_id(value: &str) -> Result<(), String> {
    if value.is_empty()
        || value.len() > 200
        || !value.chars().all(|character| {
            character.is_ascii_alphanumeric() || matches!(character, '.' | '_' | ':' | '-')
        })
    {
        return Err("本机对话标识无效".to_owned());
    }
    Ok(())
}

fn unavailable_capabilities() -> RuntimeCapabilities {
    RuntimeCapabilities {
        ordinary_chat_personalization: true,
        history_library: true,
        monitor_notifications: true,
        reminder_suggestions: true,
        unread_indicators: true,
        web_search: true,
        google_account_sync: true,
        update_service: false,
    }
}

fn bool_i64(value: bool) -> i64 {
    if value {
        1
    } else {
        0
    }
}
fn now_ms() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as i64
}

#[cfg(test)]
mod tests {
    use super::*;

    fn database() -> Connection {
        let mut connection = Connection::open_in_memory().unwrap();
        connection.execute_batch("PRAGMA foreign_keys=ON; CREATE TABLE workspaces(id TEXT PRIMARY KEY); CREATE TABLE workspace_exchange(workspace_id TEXT PRIMARY KEY REFERENCES workspaces(id) ON DELETE CASCADE,exchange_json TEXT NOT NULL);").unwrap();
        let transaction = connection.transaction().unwrap();
        migrate(&transaction).unwrap();
        migrate_portable_personalization(&transaction).unwrap();
        transaction.commit().unwrap();
        connection
    }

    #[test]
    fn defaults_are_native_and_capabilities_match_real_consumers() {
        let projection = read(&database()).unwrap();
        assert_eq!(projection.appearance.mode, "system");
        assert_eq!(projection.revision, 0);
        assert!(projection.capabilities.ordinary_chat_personalization);
        assert!(projection.capabilities.web_search);
        assert!(projection.capabilities.monitor_notifications);
        assert!(projection.capabilities.reminder_suggestions);
        assert!(projection.capabilities.unread_indicators);
        assert!(projection.capabilities.google_account_sync);
    }

    #[test]
    fn save_roundtrips_and_rejects_stale_revision() {
        let mut connection = database();
        let mut first = read(&connection).unwrap();
        first.appearance.theme_color = "purple".into();
        first.product.nickname = "南烛枫".into();
        first.product.interests = "长期维护可靠的跨端产品".into();
        let args = SaveArgs {
            appearance: first.appearance.clone(),
            product: first.product.clone(),
            expected_revision: 0,
        };
        let saved = save(&mut connection, args.clone()).unwrap();
        assert_eq!(saved.revision, 1);
        assert_eq!(saved.product.nickname, "南烛枫");
        assert_eq!(saved.product.interests, "长期维护可靠的跨端产品");
        assert!(save(&mut connection, args)
            .unwrap_err()
            .contains("其他窗口"));
    }

    #[test]
    fn invalid_and_oversized_values_are_rejected_without_mutation() {
        let mut connection = database();
        let mut current = read(&connection).unwrap();
        current.appearance.mode = "sepia".into();
        let error = save(
            &mut connection,
            SaveArgs {
                appearance: current.appearance,
                product: current.product,
                expected_revision: 0,
            },
        )
        .unwrap_err();
        assert!(error.contains("无效选项"));
        assert_eq!(read(&connection).unwrap().revision, 0);
    }

    #[test]
    fn favorites_are_workspace_scoped_validated_and_idempotent() {
        let mut connection = database();
        connection
            .execute("INSERT INTO workspaces(id) VALUES('workspace-safe-1')", [])
            .unwrap();
        connection.execute("INSERT INTO workspace_exchange(workspace_id,exchange_json) VALUES('workspace-safe-1',?1)", [r#"{"conversations":[{"id":"conversation-safe-1"}]}"#]).unwrap();
        assert_eq!(
            set_conversation_favorite(
                &mut connection,
                "workspace-safe-1",
                "conversation-safe-1",
                true
            )
            .unwrap(),
            vec!["conversation-safe-1"]
        );
        assert_eq!(
            set_conversation_favorite(
                &mut connection,
                "workspace-safe-1",
                "conversation-safe-1",
                true
            )
            .unwrap(),
            vec!["conversation-safe-1"]
        );
        assert!(
            set_conversation_favorite(&mut connection, "workspace-safe-1", "missing", true)
                .unwrap_err()
                .contains("不存在")
        );
        assert!(set_conversation_favorite(
            &mut connection,
            "../unsafe",
            "conversation-safe-1",
            true
        )
        .is_err());
        assert!(set_conversation_favorite(
            &mut connection,
            "workspace-safe-1",
            "conversation-safe-1",
            false
        )
        .unwrap()
        .is_empty());
    }
}
