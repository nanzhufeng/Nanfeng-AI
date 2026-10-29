//! Synthetic tests at the startup/store owner; never opens application data.
use super::*;
use tempfile::tempdir;

#[test]
fn windows_startup_preservation_corrupt_chat_is_rejected_without_replacement() {
    let directory = tempdir().unwrap();
    let root = directory.path().join("synthetic-startup");
    fs::create_dir(&root).unwrap();
    let bytes = b"SYNTHETIC_INVALID_SQLITE_PRESERVE_THIS_FILE";
    fs::write(root.join("workspace.sqlite3"), bytes).unwrap();
    let error = open_desktop_area_stores_for_startup(root.clone(), DesktopStartupMode::Normal)
        .err()
        .expect("corrupt CHAT must refuse startup");
    assert!(error.contains("版本不可读"), "{error}");
    assert_eq!(fs::read(root.join("workspace.sqlite3")).unwrap(), bytes);
    assert_eq!(
        fs::read_dir(root).unwrap().count(),
        1,
        "no empty fallback store"
    );
}

#[test]
fn windows_startup_preservation_unknown_schema_keeps_version_and_rows() {
    let directory = tempdir().unwrap();
    let root = directory.path().join("synthetic-startup");
    fs::create_dir(&root).unwrap();
    let database = root.join("workspace.sqlite3");
    let connection = Connection::open(&database).unwrap();
    connection.execute_batch("CREATE TABLE synthetic_future_data(id TEXT PRIMARY KEY, body TEXT NOT NULL);
        INSERT INTO synthetic_future_data VALUES('original','SYNTHETIC_KEEP'); PRAGMA user_version=999;").unwrap();
    drop(connection);
    assert!(open_desktop_area_stores_for_startup(root, DesktopStartupMode::Normal).is_err());
    let connection =
        Connection::open_with_flags(database, OpenFlags::SQLITE_OPEN_READ_ONLY).unwrap();
    assert_eq!(
        connection
            .pragma_query_value(None, "user_version", |row| row.get::<_, u32>(0))
            .unwrap(),
        999
    );
    assert_eq!(
        connection
            .query_row(
                "SELECT body FROM synthetic_future_data WHERE id='original'",
                [],
                |row| row.get::<_, String>(0)
            )
            .unwrap(),
        "SYNTHETIC_KEEP"
    );
    let tables: i64 = connection
        .query_row(
            "SELECT count(*) FROM sqlite_master WHERE type='table'",
            [],
            |row| row.get(0),
        )
        .unwrap();
    assert_eq!(
        tables, 1,
        "unknown schema must not receive replacement business tables"
    );
}

#[test]
fn windows_startup_preservation_locked_bootstrap_refuses_then_recovers_once() {
    let directory = tempdir().unwrap();
    let root = directory.path().join("synthetic-startup");
    let store = DesktopWorkspaceStore::open(root.clone()).unwrap();
    assert!(store.list_workspaces().unwrap().is_empty());
    let lock = store.connection().unwrap();
    lock.execute_batch("BEGIN IMMEDIATE").unwrap();
    let error = store
        .ensure_local_workspace()
        .err()
        .expect("locked first-write must fail");
    assert!(error.contains("初始化"), "{error}");
    assert!(
        store.list_workspaces().unwrap().is_empty(),
        "failed write cannot fabricate a workspace"
    );
    lock.execute_batch("ROLLBACK").unwrap();
    drop(lock);
    let initialized = store.ensure_local_workspace().unwrap();
    drop(store);
    let reopened = DesktopWorkspaceStore::open(root).unwrap();
    assert_eq!(
        reopened.ensure_local_workspace().unwrap().summary.id,
        initialized.summary.id
    );
    assert_eq!(reopened.list_workspaces().unwrap().len(), 1);
    assert!(initialized.exchange["conversations"]
        .as_array()
        .unwrap()
        .is_empty());
}
