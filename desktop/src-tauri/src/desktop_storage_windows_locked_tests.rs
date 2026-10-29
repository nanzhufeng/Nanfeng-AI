use super::*;
use std::os::windows::fs::OpenOptionsExt;

fn seed(root: &Path) {
    fs::create_dir_all(root).unwrap();
    let db = rusqlite::Connection::open(root.join("workspace.sqlite3")).unwrap();
    db.execute_batch(
        "CREATE TABLE workspace_exchange(workspace_id TEXT);
        INSERT INTO workspace_exchange VALUES ('SYNTHETIC_RETAINED');",
    )
    .unwrap();
    fs::write(
        root.join("attachment.bin"),
        b"SYNTHETIC_RETAINED_ATTACHMENT",
    )
    .unwrap();
}

#[test]
fn windows_storage_locked_source_file_preserves_selection_and_retries_after_unlock() {
    let temp = tempfile::tempdir().unwrap();
    let old = temp.path().join("old");
    let target = temp.path().join("new");
    let config = temp.path().join("location.json");
    seed(&old);
    fs::create_dir(&target).unwrap();
    schedule(&config, &old, &target).unwrap();
    let config_before = fs::read(&config).unwrap();
    let database_before = fs::read(old.join("workspace.sqlite3")).unwrap();
    let locked = fs::OpenOptions::new()
        .read(true)
        .share_mode(0)
        .open(old.join("attachment.bin"))
        .unwrap();
    assert!(
        resolve(&config, &old).is_err(),
        "real Windows sharing violation must stop copy"
    );
    assert_eq!(fs::read(&config).unwrap(), config_before);
    assert!(
        target.read_dir().unwrap().next().is_none(),
        "failed copy cannot publish target"
    );
    assert!(
        !temp.path().join(".new.nanfeng-migration").exists(),
        "incomplete owned staging cannot prevent an explicit retry"
    );
    drop(locked);
    assert_eq!(
        fs::read(old.join("workspace.sqlite3")).unwrap(),
        database_before
    );
    assert_eq!(
        resolve(&config, &old).unwrap(),
        fs::canonicalize(&target).unwrap()
    );
    assert_eq!(
        fs::read(target.join("attachment.bin")).unwrap(),
        b"SYNTHETIC_RETAINED_ATTACHMENT"
    );
    assert_eq!(
        fs::read(old.join("attachment.bin")).unwrap(),
        b"SYNTHETIC_RETAINED_ATTACHMENT"
    );
    assert_eq!(
        fs::read(target.join("workspace.sqlite3")).unwrap(),
        database_before
    );
    assert_eq!(
        resolve(&config, &old).unwrap(),
        fs::canonicalize(target).unwrap()
    );
}

#[test]
fn windows_storage_locked_config_preserves_original_and_completed_target_then_recovers() {
    let temp = tempfile::tempdir().unwrap();
    let old = temp.path().join("old");
    let target = temp.path().join("new");
    let config = temp.path().join("location.json");
    seed(&old);
    fs::create_dir(&target).unwrap();
    schedule(&config, &old, &target).unwrap();
    let config_before = fs::read(&config).unwrap();
    let database_before = fs::read(old.join("workspace.sqlite3")).unwrap();
    let locked = fs::OpenOptions::new()
        .read(true)
        .share_mode(1) // FILE_SHARE_READ: readable, but replacement/deletion is denied.
        .open(&config)
        .unwrap();
    assert!(
        resolve(&config, &old).is_err(),
        "blocked path publication cannot report success"
    );
    drop(locked);
    assert_eq!(fs::read(&config).unwrap(), config_before);
    for root in [&old, &target] {
        assert_eq!(
            fs::read(root.join("workspace.sqlite3")).unwrap(),
            database_before
        );
        assert_eq!(
            fs::read(root.join("attachment.bin")).unwrap(),
            b"SYNTHETIC_RETAINED_ATTACHMENT"
        );
    }
    assert_eq!(
        pending_path(&config, &old).unwrap(),
        Some(fs::canonicalize(&target).unwrap())
    );
    assert_eq!(
        resolve(&config, &old).unwrap(),
        fs::canonicalize(&target).unwrap()
    );
    assert_eq!(pending_path(&config, &old).unwrap(), None);
    assert!(
        old.join("workspace.sqlite3").exists(),
        "recovery must retain source root"
    );
}
