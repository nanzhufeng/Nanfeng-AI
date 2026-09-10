#[path = "../../../../../../desktop/src-tauri/src/desktop_storage_location.rs"]
mod production;

#[cfg(test)]
mod review {
    use std::{fs, path::Path};
    use fs2::FileExt;

    fn seed(path: &Path) {
        fs::create_dir_all(path).unwrap();
        let db = rusqlite::Connection::open(path.join("workspace.sqlite3")).unwrap();
        db.execute_batch("CREATE TABLE workspace_exchange(workspace_id TEXT); INSERT INTO workspace_exchange VALUES ('synthetic-review');").unwrap();
        fs::write(path.join("attachment.bin"), b"synthetic review fixture").unwrap();
    }

    #[test]
    fn recovery_must_not_publish_before_acquiring_source_lock() {
        let temp = tempfile::tempdir().unwrap();
        let old = temp.path().join("old");
        let target = temp.path().join("new");
        let staging = temp.path().join(".new.nanfeng-migration");
        let config = temp.path().join("location.json");
        seed(&old);
        seed(&staging);
        fs::write(&config, serde_json::to_vec(&serde_json::json!({"active":old,"pending":target})).unwrap()).unwrap();
        let lock = fs::OpenOptions::new().create(true).read(true).write(true).open(old.join(".runtime-owner.lock")).unwrap();
        lock.try_lock_exclusive().unwrap();
        let result = super::production::resolve(&config, &old);
        println!("PROBE locked_recovery returned_error={} target_published={} staging_remaining={} source_retained={}",
                 result.is_err(), target.exists(), staging.exists(), old.join("workspace.sqlite3").exists());
        assert!(result.is_err());
        assert!(!target.exists(), "Recovery published target before proving exclusive source ownership");
    }

    #[test]
    fn selected_root_missing_database_must_fail_closed() {
        let temp = tempfile::tempdir().unwrap();
        let old = temp.path().join("old");
        let target = temp.path().join("new");
        let config = temp.path().join("location.json");
        seed(&old);
        seed(&target);
        super::production::schedule(&config, &old, &target).unwrap();
        super::production::resolve(&config, &old).unwrap();
        fs::remove_file(target.join("workspace.sqlite3")).unwrap();
        let result = super::production::resolve(&config, &old);
        println!("PROBE selected_missing_db accepted={} attachment_retained={}", result.is_ok(), target.join("attachment.bin").exists());
        assert!(result.is_err(), "Configured root without its database was accepted");
    }

    #[test]
    fn normal_migration_respects_source_lock() {
        let temp = tempfile::tempdir().unwrap();
        let old = temp.path().join("old");
        let target = temp.path().join("new");
        let config = temp.path().join("location.json");
        seed(&old);
        fs::create_dir(&target).unwrap();
        super::production::schedule(&config, &old, &target).unwrap();
        let lock = fs::OpenOptions::new().create(true).read(true).write(true).open(old.join(".runtime-owner.lock")).unwrap();
        lock.try_lock_exclusive().unwrap();
        assert!(super::production::resolve(&config, &old).is_err());
        assert!(target.read_dir().unwrap().next().is_none());
    }
}
