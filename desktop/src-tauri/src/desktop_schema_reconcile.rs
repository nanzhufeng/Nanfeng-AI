//! Build and verify a separate schema-45 candidate and physical CHAT/WORK bundle.
//! The explicit publisher installs the bundle only under the root owner lock.
use crate::desktop_schema_source::{classify, SchemaSource};
use fs2::FileExt;
use rusqlite::{Connection, OpenFlags};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::{
    collections::BTreeSet,
    fs,
    io::Write,
    path::{Path, PathBuf},
};

#[derive(Debug, Deserialize, Serialize)]
#[serde(deny_unknown_fields)]
struct Schema45BundleManifest {
    version: u32,
    source_database_sha256: String,
    source_wal_sha256: Option<String>,
    #[serde(default = "default_candidate_file")]
    candidate_file: String,
    candidate_sha256: String,
}

fn default_candidate_file() -> String {
    "schema45-candidate.sqlite3".into()
}

fn candidate_name_valid(name: &str) -> bool {
    name.starts_with("schema45-candidate")
        && name.ends_with(".sqlite3")
        && name.bytes().all(|byte| {
            byte.is_ascii_lowercase() || byte.is_ascii_digit() || matches!(byte, b'-' | b'.')
        })
}

#[derive(Debug, Deserialize, Serialize)]
#[serde(deny_unknown_fields)]
struct Schema45SourceMarker {
    version: u32,
    source_database_sha256: String,
    source_wal_sha256: Option<String>,
}

#[derive(Debug, Deserialize, Serialize)]
#[serde(deny_unknown_fields)]
struct Schema45InstallMarker {
    version: u32,
    old_chat_sha256: String,
    new_chat_sha256: String,
    work_tree_sha256: String,
    configuration_sha256: String,
}

#[derive(Debug, Deserialize, Serialize)]
#[serde(deny_unknown_fields)]
struct Schema45PairInstallMarker {
    version: u32,
    stage_name: String,
    old_chat_sha256: String,
    new_chat_sha256: String,
    old_work_sha256: String,
    new_work_sha256: String,
}

fn tree_fingerprint(root: &Path) -> Result<String, String> {
    fn visit(directory: &Path, relative: &str, digest: &mut Sha256) -> Result<(), String> {
        ensure_plain_recovery_directory(directory)?;
        let mut entries = fs::read_dir(directory)
            .map_err(|_| "Schema45 文件树不可读".to_owned())?
            .collect::<Result<Vec<_>, _>>()
            .map_err(|_| "Schema45 文件树不可读".to_owned())?;
        entries.sort_by_key(|entry| entry.file_name());
        for entry in entries {
            let name = entry
                .file_name()
                .into_string()
                .map_err(|_| "Schema45 文件树含非 Unicode 名称".to_owned())?;
            let child_relative = if relative.is_empty() {
                name
            } else {
                format!("{relative}/{name}")
            };
            let path = entry.path();
            let metadata =
                fs::symlink_metadata(&path).map_err(|_| "Schema45 文件树状态不可读".to_owned())?;
            if metadata.is_dir() && !metadata.file_type().is_symlink() {
                digest.update(b"D");
                digest.update((child_relative.len() as u64).to_le_bytes());
                digest.update(child_relative.as_bytes());
                visit(&path, &child_relative, digest)?;
            } else if metadata.is_file() && !metadata.file_type().is_symlink() {
                ensure_plain_single_link_file(&path)?;
                digest.update(b"F");
                digest.update((child_relative.len() as u64).to_le_bytes());
                digest.update(child_relative.as_bytes());
                digest.update(crate::sha256_file(&path)?.as_bytes());
            } else {
                return Err("Schema45 文件树包含链接或特殊项".into());
            }
        }
        Ok(())
    }
    let mut digest = Sha256::new();
    visit(root, "", &mut digest)?;
    Ok(format!("{:x}", digest.finalize()))
}

fn wal_path(database: &Path) -> PathBuf {
    let mut path = database.as_os_str().to_os_string();
    path.push("-wal");
    PathBuf::from(path)
}

fn source_fingerprint(database: &Path) -> Result<(String, Option<String>), String> {
    ensure_plain_single_link_file(database)?;
    let main = crate::sha256_file(database)?;
    let wal = wal_path(database);
    let wal_hash = match fs::symlink_metadata(&wal) {
        Ok(_) => {
            ensure_plain_single_link_file(&wal)?;
            if fs::metadata(&wal)
                .map_err(|_| "Schema45 源 WAL 不可读".to_owned())?
                .len()
                == 0
            {
                None
            } else {
                Some(crate::sha256_file(&wal)?)
            }
        }
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => None,
        Err(_) => return Err("Schema45 源 WAL 状态不可读".into()),
    };
    Ok((main, wal_hash))
}

fn read_bundle_manifest(path: &Path) -> Result<Schema45BundleManifest, String> {
    ensure_plain_single_link_file(path)?;
    let metadata = fs::metadata(path).map_err(|_| "Schema45 暂存清单不可读".to_owned())?;
    if metadata.len() > 4096 {
        return Err("Schema45 暂存清单过大".into());
    }
    let bytes = fs::read(path).map_err(|_| "Schema45 暂存清单不可读".to_owned())?;
    let manifest: Schema45BundleManifest =
        serde_json::from_slice(&bytes).map_err(|_| "Schema45 暂存清单无效".to_owned())?;
    if manifest.version != 1 {
        return Err("Schema45 暂存清单版本未知".into());
    }
    Ok(manifest)
}

fn write_bundle_manifest(path: &Path, manifest: &Schema45BundleManifest) -> Result<(), String> {
    let bytes = serde_json::to_vec(manifest).map_err(|_| "Schema45 暂存清单不可编码".to_owned())?;
    write_atomic_new_file(path, &bytes)
}

fn write_atomic_new_file(path: &Path, bytes: &[u8]) -> Result<(), String> {
    let mut staging_name = path.as_os_str().to_os_string();
    staging_name.push(".nfai-writing");
    let staging = PathBuf::from(staging_name);
    match fs::symlink_metadata(&staging) {
        Ok(_) => {
            ensure_plain_single_link_file(&staging)?;
            fs::remove_file(&staging).map_err(|_| "Schema45 半写清单不可清理".to_owned())?;
        }
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
        Err(_) => return Err("Schema45 清单暂存状态不可读".into()),
    }
    let mut file = fs::OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(&staging)
        .map_err(|_| "Schema45 暂存清单不可独占创建".to_owned())?;
    file.write_all(&bytes)
        .and_then(|_| file.sync_all())
        .map_err(|_| "Schema45 暂存清单不可保存".to_owned())?;
    drop(file);
    crate::desktop_legacy_area_split::commit_staged_area_file(&staging, path)
        .map_err(|_| "Schema45 暂存清单未提交".to_owned())
}

fn read_source_marker(path: &Path) -> Result<Schema45SourceMarker, String> {
    ensure_plain_single_link_file(path)?;
    if fs::metadata(path)
        .map_err(|_| "Schema45 来源标记不可读".to_owned())?
        .len()
        > 4096
    {
        return Err("Schema45 来源标记过大".into());
    }
    let bytes = fs::read(path).map_err(|_| "Schema45 来源标记不可读".to_owned())?;
    let marker: Schema45SourceMarker =
        serde_json::from_slice(&bytes).map_err(|_| "Schema45 来源标记无效".to_owned())?;
    if marker.version != 1 {
        return Err("Schema45 来源标记版本未知".into());
    }
    Ok(marker)
}

fn read_install_marker(path: &Path) -> Result<Schema45InstallMarker, String> {
    ensure_plain_single_link_file(path)?;
    if fs::metadata(path)
        .map_err(|_| "Schema45 发布标记不可读".to_owned())?
        .len()
        > 4096
    {
        return Err("Schema45 发布标记过大".into());
    }
    let bytes = fs::read(path).map_err(|_| "Schema45 发布标记不可读".to_owned())?;
    let marker: Schema45InstallMarker =
        serde_json::from_slice(&bytes).map_err(|_| "Schema45 发布标记无效".to_owned())?;
    if marker.version != 1 {
        return Err("Schema45 发布标记版本未知".into());
    }
    Ok(marker)
}

fn check_quiescent_sidecars(database: &Path) -> Result<(), String> {
    for suffix in ["-wal", "-shm", "-journal"] {
        let mut name = database.as_os_str().to_os_string();
        name.push(suffix);
        let path = PathBuf::from(name);
        match fs::symlink_metadata(&path) {
            Ok(metadata) => {
                ensure_plain_single_link_file(&path)?;
                if suffix == "-journal" || (suffix == "-wal" && metadata.len() != 0) {
                    return Err("Schema45 SQLite 仍有待提交 sidecar，未发布".into());
                }
            }
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
            Err(_) => return Err("Schema45 SQLite sidecar 状态不可读".into()),
        }
    }
    Ok(())
}

fn checkpoint_legacy_source_under_existing_lock(database: &Path) -> Result<(), String> {
    ensure_plain_single_link_file(database)?;
    for suffix in ["-wal", "-shm", "-journal"] {
        let mut name = database.as_os_str().to_os_string();
        name.push(suffix);
        let path = PathBuf::from(name);
        match fs::symlink_metadata(&path) {
            Ok(_) => ensure_plain_single_link_file(&path)?,
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
            Err(_) => return Err("Schema45 旧库 sidecar 状态不可读".into()),
        }
    }
    let connection = Connection::open_with_flags(database, OpenFlags::SQLITE_OPEN_READ_WRITE)
        .map_err(|_| "Schema45 旧库无法执行 SQLite 恢复".to_owned())?;
    connection
        .busy_timeout(std::time::Duration::from_secs(5))
        .map_err(|_| "Schema45 旧库等待期限不可设置".to_owned())?;
    let journal_mode: String = connection
        .pragma_query_value(None, "journal_mode", |row| row.get(0))
        .map_err(|_| "Schema45 旧库日志模式不可读".to_owned())?;
    if journal_mode.eq_ignore_ascii_case("wal") {
        let (busy, log, checkpointed): (i64, i64, i64) = connection
            .query_row("PRAGMA wal_checkpoint(TRUNCATE)", [], |row| {
                Ok((row.get(0)?, row.get(1)?, row.get(2)?))
            })
            .map_err(|_| "Schema45 旧库 WAL 无法安全检查点".to_owned())?;
        if busy != 0 || log != 0 || checkpointed != 0 {
            return Err("Schema45 旧库 WAL 仍被占用，未迁移".into());
        }
    }
    let integrity: String = connection
        .query_row("PRAGMA integrity_check", [], |row| row.get(0))
        .map_err(|_| "Schema45 旧库完整性不可验证".to_owned())?;
    let foreign_keys: i64 = connection
        .query_row("SELECT COUNT(*) FROM pragma_foreign_key_check", [], |row| {
            row.get(0)
        })
        .map_err(|_| "Schema45 旧库关联不可验证".to_owned())?;
    if integrity != "ok" || foreign_keys != 0 {
        return Err("Schema45 旧库完整性或关联检查失败，未迁移".into());
    }
    drop(connection);
    check_quiescent_sidecars(database)?;
    fs::OpenOptions::new()
        .read(true)
        .write(true)
        .open(database)
        .and_then(|file| file.sync_all())
        .map_err(|_| "Schema45 旧库检查点未同步到磁盘".to_owned())
}

fn copy_plain_private_file(source: &Path, target: &Path) -> Result<(), String> {
    ensure_plain_single_link_file(source)?;
    let source_hash = crate::sha256_file(source)?;
    let mut staging_name = target.as_os_str().to_os_string();
    staging_name.push(".nfai-stage-copying");
    let staging = PathBuf::from(staging_name);
    match fs::symlink_metadata(target) {
        Ok(_) => {
            ensure_plain_single_link_file(target)?;
            if crate::sha256_file(target)? == source_hash {
                match fs::symlink_metadata(&staging) {
                    Ok(_) => {
                        ensure_plain_single_link_file(&staging)?;
                        fs::remove_file(&staging)
                            .map_err(|_| "Schema45 多余的暂存副本不可清理".to_owned())?;
                    }
                    Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
                    Err(_) => return Err("Schema45 暂存副本状态不可读".into()),
                }
                return Ok(());
            }
            return Err("Schema45 暂存目标已有不同内容，未覆盖".into());
        }
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
        Err(_) => return Err("Schema45 暂存目标状态不可读".into()),
    }
    let staging_present = match fs::symlink_metadata(&staging) {
        Ok(_) => {
            ensure_plain_single_link_file(&staging)?;
            true
        }
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => false,
        Err(_) => return Err("Schema45 暂存副本状态不可读".into()),
    };
    if staging_present && crate::sha256_file(&staging)? != source_hash {
        fs::remove_file(&staging).map_err(|_| "Schema45 半截暂存不可清理".to_owned())?;
    }
    if !staging_present || !staging.exists() {
        let mut input = fs::File::open(source).map_err(|_| "Schema45 私有来源不可读".to_owned())?;
        let mut output = fs::OpenOptions::new()
            .write(true)
            .create_new(true)
            .open(&staging)
            .map_err(|_| "Schema45 私有暂存不可独占创建".to_owned())?;
        std::io::copy(&mut input, &mut output)
            .map(|_| ())
            .and_then(|_| output.sync_all())
            .map_err(|_| "Schema45 私有暂存未完整保存".to_owned())?;
    }
    if crate::sha256_file(source)? != source_hash || crate::sha256_file(&staging)? != source_hash {
        return Err("Schema45 私有暂存复制后校验失败".into());
    }
    crate::desktop_legacy_area_split::commit_staged_area_file(&staging, target)?;
    Ok(())
}

fn copy_plain_private_tree(source: &Path, target: &Path) -> Result<(), String> {
    ensure_plain_recovery_directory(source)?;
    match fs::symlink_metadata(target) {
        Ok(_) => ensure_plain_recovery_directory(target)?,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
            fs::create_dir(target).map_err(|_| "Schema45 私有暂存目录不可创建".to_owned())?;
        }
        Err(_) => return Err("Schema45 私有暂存目录状态不可读".into()),
    }
    ensure_plain_recovery_directory(target)?;
    let mut entries = fs::read_dir(source)
        .map_err(|_| "Schema45 私有来源目录不可读".to_owned())?
        .collect::<Result<Vec<_>, _>>()
        .map_err(|_| "Schema45 私有来源目录不可读".to_owned())?;
    entries.sort_by_key(|entry| entry.file_name());
    let expected: BTreeSet<_> = entries.iter().map(|entry| entry.file_name()).collect();
    for entry in entries {
        let source_child = entry.path();
        let target_child = target.join(entry.file_name());
        let kind = fs::symlink_metadata(&source_child)
            .map_err(|_| "Schema45 私有来源状态不可读".to_owned())?;
        if kind.is_dir() && !kind.file_type().is_symlink() {
            copy_plain_private_tree(&source_child, &target_child)?;
        } else if kind.is_file() && !kind.file_type().is_symlink() {
            copy_plain_private_file(&source_child, &target_child)?;
        } else {
            return Err("Schema45 私有来源包含链接或特殊文件，未暂存".into());
        }
    }
    let actual = fs::read_dir(target)
        .map_err(|_| "Schema45 私有暂存目录不可读".to_owned())?
        .map(|entry| {
            entry
                .map(|item| item.file_name())
                .map_err(|_| "Schema45 私有暂存目录不可读".to_owned())
        })
        .collect::<Result<BTreeSet<_>, _>>()?;
    if actual != expected {
        return Err("Schema45 私有暂存含额外或缺失文件，未续作".into());
    }
    Ok(())
}

fn ensure_plain_single_link_file(path: &Path) -> Result<(), String> {
    let metadata = fs::symlink_metadata(path).map_err(|_| "Schema45 源文件不可读".to_owned())?;
    if !metadata.is_file() || metadata.file_type().is_symlink() {
        return Err("Schema45 源库必须是普通文件".into());
    }
    #[cfg(windows)]
    {
        use std::os::windows::{fs::MetadataExt, io::AsRawHandle};
        use windows::Win32::{
            Foundation::HANDLE,
            Storage::FileSystem::{
                GetFileInformationByHandle, BY_HANDLE_FILE_INFORMATION,
                FILE_ATTRIBUTE_REPARSE_POINT,
            },
        };
        if metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT.0 != 0 {
            return Err("Schema45 源库不能是重解析点".into());
        }
        let file = fs::File::open(path).map_err(|_| "Schema45 源文件不可打开".to_owned())?;
        let mut information = BY_HANDLE_FILE_INFORMATION::default();
        unsafe { GetFileInformationByHandle(HANDLE(file.as_raw_handle()), &mut information) }
            .map_err(|_| "Schema45 源文件链接数不可验证".to_owned())?;
        if information.nNumberOfLinks != 1 {
            return Err("Schema45 源库存在额外硬链接".into());
        }
    }
    #[cfg(unix)]
    {
        use std::os::unix::fs::MetadataExt;
        if metadata.nlink() != 1 {
            return Err("Schema45 源库存在额外硬链接".into());
        }
    }
    Ok(())
}

fn ensure_plain_recovery_directory(path: &Path) -> Result<(), String> {
    let metadata = fs::symlink_metadata(path).map_err(|_| "Schema45 候选目录不可读".to_owned())?;
    if !metadata.is_dir() || metadata.file_type().is_symlink() {
        return Err("Schema45 候选目录不能是链接".into());
    }
    #[cfg(windows)]
    {
        use std::os::windows::fs::MetadataExt;
        use windows::Win32::Storage::FileSystem::FILE_ATTRIBUTE_REPARSE_POINT;
        if metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT.0 != 0 {
            return Err("Schema45 候选目录不能是重解析点".into());
        }
    }
    Ok(())
}

struct RootMigrationLease {
    _file: fs::File,
}

impl RootMigrationLease {
    fn acquire(root: &Path) -> Result<Self, String> {
        ensure_plain_recovery_directory(root)?;
        let path = root.join(".runtime-owner.lock");
        let file = match fs::OpenOptions::new()
            .read(true)
            .write(true)
            .create_new(true)
            .open(&path)
        {
            Ok(file) => file,
            Err(error) if error.kind() == std::io::ErrorKind::AlreadyExists => {
                ensure_plain_single_link_file(&path)?;
                fs::OpenOptions::new()
                    .read(true)
                    .write(true)
                    .open(&path)
                    .map_err(|_| "Schema45 进程锁不可打开".to_owned())?
            }
            Err(_) => return Err("Schema45 进程锁不可创建".into()),
        };
        file.try_lock_exclusive()
            .map_err(|_| "Schema45 数据根仍有进程占用，未暂存".to_owned())?;
        Ok(Self { _file: file })
    }
}

#[allow(
    dead_code,
    reason = "W04A will activate this candidate only after the physical area split is integrated"
)]
pub(crate) fn prepare_schema45_candidate(source: &Path) -> Result<PathBuf, String> {
    prepare_schema45_candidate_named(source, "schema45-candidate.sqlite3")
}

fn prepare_schema45_candidate_named(source: &Path, name: &str) -> Result<PathBuf, String> {
    if !candidate_name_valid(name) {
        return Err("Schema45 候选文件名无效".into());
    }
    if source.file_name().and_then(|name| name.to_str()) != Some("workspace.sqlite3")
        || !source.is_file()
    {
        return Err("Schema45 源库位置无效".into());
    }
    ensure_plain_single_link_file(source)?;
    let root = source.parent().ok_or("Schema45 源根无效")?;
    let recovery = root.join("data-area-migration-v1");
    fs::create_dir_all(&recovery).map_err(|_| "Schema45 候选目录不可用".to_owned())?;
    ensure_plain_recovery_directory(&recovery)?;
    let candidate = recovery.join(name);
    if fs::symlink_metadata(&candidate).is_ok() {
        return Err("Schema45 候选已存在，须先核对中断状态".into());
    }
    let source_db = Connection::open_with_flags(source, OpenFlags::SQLITE_OPEN_READ_ONLY)
        .map_err(|_| "Schema45 源库不可读".to_owned())?;
    let version: u32 = source_db
        .pragma_query_value(None, "user_version", |row| row.get(0))
        .map_err(|_| "Schema45 源版本不可读".to_owned())?;
    let branch = classify(&source_db, version).map_err(|_| "Schema45 源结构不可读".to_owned())?;
    if !matches!(
        branch,
        SchemaSource::Legacy(42)
            | SchemaSource::UpstreamArea43 { .. }
            | SchemaSource::WindowsDraft43
            | SchemaSource::WindowsDraft44
    ) {
        return Err("Schema45 来源未知或尚未升级到 42，原库未改动".into());
    }
    source_db
        .execute("VACUUM INTO ?1", [candidate.to_string_lossy().as_ref()])
        .map_err(|_| "Schema45 源库快照未完成，原库未改动".to_owned())?;
    drop(source_db);

    let mut target = Connection::open_with_flags(&candidate, OpenFlags::SQLITE_OPEN_READ_WRITE)
        .map_err(|_| "Schema45 候选不可写".to_owned())?;
    let transaction = target
        .transaction()
        .map_err(|_| "Schema45 候选事务不可用".to_owned())?;
    let columns = transaction
        .prepare("SELECT name FROM pragma_table_info('desktop_conversation_drafts_v1')")
        .and_then(|mut statement| {
            statement
                .query_map([], |row| row.get::<_, String>(0))?
                .collect::<rusqlite::Result<BTreeSet<_>>>()
        })
        .map_err(|_| "Schema45 草稿字段不可读".to_owned())?;
    for (name, data_type) in [
        ("model_id", "TEXT"),
        ("tone_override", "TEXT"),
        ("web_search_override", "INTEGER"),
    ] {
        if !columns.contains(name) {
            transaction
                .execute_batch(&format!(
                    "ALTER TABLE desktop_conversation_drafts_v1 ADD COLUMN {name} {data_type};"
                ))
                .map_err(|_| format!("Schema45 草稿字段 {name} 无法补全"))?;
        }
    }
    if !matches!(branch, SchemaSource::UpstreamArea43 { .. }) {
        transaction.execute_batch("CREATE TABLE desktop_data_area_v1(id INTEGER PRIMARY KEY CHECK(id=1), area TEXT NOT NULL CHECK(area IN ('CHAT','WORK')), split_done INTEGER NOT NULL DEFAULT 0); INSERT INTO desktop_data_area_v1(id,area,split_done) VALUES(1,'CHAT',0);")
            .map_err(|_| "Schema45 区域身份无法补全".to_owned())?;
    }
    transaction
        .pragma_update(None, "user_version", 45)
        .map_err(|_| "Schema45 版本无法保存".to_owned())?;
    transaction
        .commit()
        .map_err(|_| "Schema45 候选未提交".to_owned())?;
    if !matches!(
        classify(&target, 45).map_err(|_| "Schema45 候选结构不可读".to_owned())?,
        SchemaSource::Common45 { .. }
    ) {
        return Err("Schema45 候选结构不完整".into());
    }
    let integrity: String = target
        .query_row("PRAGMA integrity_check", [], |row| row.get(0))
        .map_err(|_| "Schema45 候选完整性无法验证".to_owned())?;
    if integrity != "ok" {
        return Err("Schema45 候选完整性失败".into());
    }
    let foreign_key_error: i64 = target
        .query_row("SELECT COUNT(*) FROM pragma_foreign_key_check", [], |row| {
            row.get(0)
        })
        .map_err(|_| "Schema45 候选关联无法验证".to_owned())?;
    if foreign_key_error != 0 {
        return Err("Schema45 候选关联不完整".into());
    }
    drop(target);
    fs::OpenOptions::new()
        .read(true)
        .write(true)
        .open(&candidate)
        .and_then(|file| file.sync_all())
        .map_err(|_| "Schema45 候选无法同步到磁盘".to_owned())?;
    Ok(candidate)
}

#[allow(
    dead_code,
    reason = "W04A will publish the verified bundle under the process lock"
)]
pub(crate) fn prepare_schema45_split_bundle(source: &Path) -> Result<PathBuf, String> {
    let root = source.parent().ok_or("Schema45 源根无效")?;
    let lease = RootMigrationLease::acquire(root)?;
    prepare_schema45_split_bundle_under_existing_lock(source, &lease._file)
}

#[allow(
    dead_code,
    reason = "Only startup may use this after it has acquired the root runtime lock"
)]
pub(crate) fn prepare_schema45_split_bundle_under_existing_lock(
    source: &Path,
    _root_lock: &fs::File,
) -> Result<PathBuf, String> {
    use crate::{DesktopDataArea, DesktopStartupMode, DesktopWorkspaceStore};

    let root = source.parent().ok_or("Schema45 源根无效")?;
    ensure_plain_recovery_directory(root)?;
    let source_db = Connection::open_with_flags(source, OpenFlags::SQLITE_OPEN_READ_ONLY)
        .map_err(|_| "Schema45 源库不可读".to_owned())?;
    let source_version: u32 = source_db
        .pragma_query_value(None, "user_version", |row| row.get(0))
        .map_err(|_| "Schema45 源版本不可读".to_owned())?;
    match classify(&source_db, source_version) {
        Ok(SchemaSource::UpstreamArea43 { area, split_done }) if area == "WORK" || split_done => {
            return Err("已有独立双区来源，不能当旧混合 CHAT 分库".into());
        }
        Ok(
            SchemaSource::Legacy(42)
            | SchemaSource::UpstreamArea43 { .. }
            | SchemaSource::WindowsDraft43
            | SchemaSource::WindowsDraft44,
        ) => {}
        Ok(_) => return Err("Schema45 来源未知，未创建暂存".into()),
        Err(_) => return Err("Schema45 源结构不可读".into()),
    }
    drop(source_db);
    match fs::symlink_metadata(root.join("work-area")) {
        Ok(_) => return Err("已有 WORK 目录，须先核对旧双区或中断状态".into()),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
        Err(_) => return Err("WORK 目录状态不可读".into()),
    }
    let recovery = root.join("data-area-migration-v1");
    let stage = recovery.join("schema45-stage");
    let stage_present = match fs::symlink_metadata(&stage) {
        Ok(_) => {
            ensure_plain_recovery_directory(&stage)?;
            true
        }
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => false,
        Err(_) => return Err("Schema45 暂存根状态不可读".into()),
    };
    let manifest_path = recovery.join("schema45-bundle.json");
    let manifest_present = match fs::symlink_metadata(&manifest_path) {
        Ok(_) => true,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => false,
        Err(_) => return Err("Schema45 暂存清单状态不可读".into()),
    };
    if stage_present && !manifest_present {
        return Err("Schema45 暂存根缺少来源清单，未续作".into());
    }
    let source_marker_path = recovery.join("schema45-source.json");
    let candidate = if manifest_present {
        ensure_plain_recovery_directory(&recovery)?;
        let manifest = read_bundle_manifest(&manifest_path)?;
        if !candidate_name_valid(&manifest.candidate_file) {
            return Err("Schema45 暂存候选文件名无效".into());
        }
        let (source_hash, wal_hash) = source_fingerprint(source)?;
        if source_hash != manifest.source_database_sha256 || wal_hash != manifest.source_wal_sha256
        {
            return Err("Schema45 源库已变化，旧暂存未续作".into());
        }
        match fs::symlink_metadata(&source_marker_path) {
            Ok(_) => {
                let marker = read_source_marker(&source_marker_path)?;
                if marker.source_database_sha256 != source_hash
                    || marker.source_wal_sha256 != wal_hash
                {
                    return Err("Schema45 来源标记与暂存清单不符".into());
                }
            }
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
            Err(_) => return Err("Schema45 来源标记状态不可读".into()),
        }
        let candidate = recovery.join(&manifest.candidate_file);
        ensure_plain_single_link_file(&candidate)?;
        if crate::sha256_file(&candidate)? != manifest.candidate_sha256 {
            return Err("Schema45 候选与暂存清单不符，未续作".into());
        }
        candidate
    } else {
        fs::create_dir_all(&recovery).map_err(|_| "Schema45 候选目录不可用".to_owned())?;
        ensure_plain_recovery_directory(&recovery)?;
        let (source_hash, wal_hash) = source_fingerprint(source)?;
        match fs::symlink_metadata(&source_marker_path) {
            Ok(_) => {
                let marker = read_source_marker(&source_marker_path)?;
                if marker.source_database_sha256 != source_hash
                    || marker.source_wal_sha256 != wal_hash
                {
                    return Err("Schema45 源库已变化，孤立候选未续作".into());
                }
            }
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
                match fs::symlink_metadata(recovery.join("schema45-candidate.sqlite3")) {
                    Ok(_) => return Err("Schema45 旧候选缺少来源标记，未覆盖".into()),
                    Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
                    Err(_) => return Err("Schema45 旧候选状态不可读".into()),
                }
                let bytes = serde_json::to_vec(&Schema45SourceMarker {
                    version: 1,
                    source_database_sha256: source_hash.clone(),
                    source_wal_sha256: wal_hash.clone(),
                })
                .map_err(|_| "Schema45 来源标记不可编码".to_owned())?;
                write_atomic_new_file(&source_marker_path, &bytes)?;
            }
            Err(_) => return Err("Schema45 来源标记状态不可读".into()),
        }
        let fixed = recovery.join("schema45-candidate.sqlite3");
        let candidate_name = match fs::symlink_metadata(&fixed) {
            Ok(_) => {
                ensure_plain_single_link_file(&fixed)?;
                format!(
                    "schema45-candidate-{}.sqlite3",
                    crate::ordinary_chat_random_id("retry")?
                )
            }
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => default_candidate_file(),
            Err(_) => return Err("Schema45 旧候选状态不可读".into()),
        };
        let candidate = prepare_schema45_candidate_named(source, &candidate_name)?;
        if source_fingerprint(source)? != (source_hash.clone(), wal_hash.clone()) {
            return Err("Schema45 源库在制作候选时变化，未暂存".into());
        }
        write_bundle_manifest(
            &manifest_path,
            &Schema45BundleManifest {
                version: 1,
                source_database_sha256: source_hash,
                source_wal_sha256: wal_hash,
                candidate_file: candidate_name,
                candidate_sha256: crate::sha256_file(&candidate)?,
            },
        )?;
        candidate
    };
    for suffix in ["-wal", "-shm", "-journal"] {
        let sidecar = PathBuf::from(format!("{}{}", candidate.display(), suffix));
        match fs::symlink_metadata(&sidecar) {
            Ok(_) => return Err("Schema45 候选仍有 SQLite sidecar，未复制".into()),
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
            Err(_) => return Err("Schema45 候选 sidecar 状态不可读".into()),
        }
    }
    if !stage_present {
        fs::create_dir(&stage).map_err(|_| "Schema45 暂存根不可创建".to_owned())?;
    }
    ensure_plain_recovery_directory(&stage)?;
    let stage_database = stage.join("workspace.sqlite3");
    match fs::symlink_metadata(&stage_database) {
        Ok(_) => {
            ensure_plain_single_link_file(&stage_database)?;
            let staged =
                Connection::open_with_flags(&stage_database, OpenFlags::SQLITE_OPEN_READ_ONLY)
                    .map_err(|_| "Schema45 暂存库不可读，未续作".to_owned())?;
            let version: u32 = staged
                .pragma_query_value(None, "user_version", |row| row.get(0))
                .map_err(|_| "Schema45 暂存库版本不可读".to_owned())?;
            if !matches!(
                classify(&staged, version),
                Ok(SchemaSource::Common45 { area, .. }) if area == "CHAT"
            ) {
                return Err("Schema45 暂存库身份不符，未续作".into());
            }
        }
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
            copy_plain_private_file(&candidate, &stage_database)?;
        }
        Err(_) => return Err("Schema45 暂存库状态不可读".into()),
    }
    for name in [
        "assets",
        "chatgpt-import-assets",
        "claude-import-assets",
        "nanfeng-knowledge-import-assets",
        "p6k-zip-import-assets",
    ] {
        let source_tree = root.join(name);
        match fs::symlink_metadata(&source_tree) {
            Ok(_) => copy_plain_private_tree(&source_tree, &stage.join(name))?,
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
            Err(_) => return Err("Schema45 私有来源目录状态不可读".into()),
        }
    }
    let store = DesktopWorkspaceStore {
        root: stage.clone(),
        database: stage.join("workspace.sqlite3"),
        configuration_database: None,
        credentials: crate::desktop_model_service_v1::AppPrivateProviderCredentialStore::at(&stage),
        p7e_isolated_store: crate::p7e_isolated_workspace_v1::IsolatedWorkspaceStore::open(
            stage.join("p7e-isolated-workspace-v1"),
        )
        .map_err(|_| "Schema45 暂存隔离工作区不可用".to_owned())?,
        p8_agent_ledger: crate::p8_agent_ledger_v1::AgentLedgerStore::open(
            stage.join("p8-agent-ledger-v1"),
        )
        .map_err(|_| "Schema45 暂存 Agent 账本不可用".to_owned())?,
    };
    crate::desktop_legacy_area_split::migrate(&store)?;
    drop(store);
    DesktopWorkspaceStore::open_committed_schema45_area(
        stage.clone(),
        DesktopDataArea::Chat,
        DesktopStartupMode::UiSchemaDiagnostic,
    )?;
    DesktopWorkspaceStore::open_committed_schema45_area(
        stage.clone(),
        DesktopDataArea::Work,
        DesktopStartupMode::UiSchemaDiagnostic,
    )?;
    let manifest = read_bundle_manifest(&manifest_path)?;
    if source_fingerprint(source)? != (manifest.source_database_sha256, manifest.source_wal_sha256)
    {
        return Err("Schema45 源库在暂存期间变化，未发布".into());
    }
    Ok(stage)
}

fn replace_active_chat_database(staging: &Path, active: &Path) -> Result<(), String> {
    #[cfg(windows)]
    {
        use std::os::windows::ffi::OsStrExt;
        use windows::{
            core::PCWSTR,
            Win32::Storage::FileSystem::{
                MoveFileExW, MOVEFILE_REPLACE_EXISTING, MOVEFILE_WRITE_THROUGH,
            },
        };
        let wide = |path: &Path| {
            path.as_os_str()
                .encode_wide()
                .chain(std::iter::once(0))
                .collect::<Vec<_>>()
        };
        let from = wide(staging);
        let to = wide(active);
        unsafe {
            MoveFileExW(
                PCWSTR(from.as_ptr()),
                PCWSTR(to.as_ptr()),
                MOVEFILE_REPLACE_EXISTING | MOVEFILE_WRITE_THROUGH,
            )
        }
        .map_err(|_| "Schema45 CHAT 数据库未原子替换".to_owned())
    }
    #[cfg(not(windows))]
    {
        fs::rename(staging, active).map_err(|_| "Schema45 CHAT 数据库未原子替换".to_owned())
    }
}

fn verify_installed_area(database: &Path, expected_area: &str) -> Result<(), String> {
    let connection = Connection::open_with_flags(database, OpenFlags::SQLITE_OPEN_READ_ONLY)
        .map_err(|_| "Schema45 已发布区域不可读".to_owned())?;
    let version: u32 = connection
        .pragma_query_value(None, "user_version", |row| row.get(0))
        .map_err(|_| "Schema45 已发布版本不可读".to_owned())?;
    if !matches!(
        classify(&connection, version),
        Ok(SchemaSource::Common45 { area, split_done: true }
            | SchemaSource::Common46 { area, split_done: true }
            | SchemaSource::Common47 { area, split_done: true }
            | SchemaSource::Common48 { area, split_done: true }
            | SchemaSource::Common49 { area, split_done: true }
            | SchemaSource::Common50 { area, split_done: true }
            | SchemaSource::Common51 { area, split_done: true } | SchemaSource::Common52 { area, split_done: true } | SchemaSource::Common53 { area, split_done: true } | SchemaSource::Common54 { area, split_done: true } | SchemaSource::Common55 { area, split_done: true } | SchemaSource::Common56 { area, split_done: true } | SchemaSource::Common57 { area, split_done: true } | SchemaSource::Common58 { area, split_done: true } | SchemaSource::Common59 { area, split_done: true }) if area == expected_area
    ) {
        return Err("Schema45 已发布区域身份或状态不符".into());
    }
    crate::desktop_legacy_area_split::verify_required_singletons(&connection)?;
    let integrity: String = connection
        .query_row("PRAGMA integrity_check", [], |row| row.get(0))
        .map_err(|_| "Schema45 已发布完整性不可验证".to_owned())?;
    let foreign_keys: i64 = connection
        .query_row("SELECT COUNT(*) FROM pragma_foreign_key_check", [], |row| {
            row.get(0)
        })
        .map_err(|_| "Schema45 已发布关联不可验证".to_owned())?;
    if integrity != "ok" || foreign_keys != 0 {
        return Err("Schema45 已发布区域完整性失败".into());
    }
    Ok(())
}

fn read_pair_install_marker(path: &Path) -> Result<Schema45PairInstallMarker, String> {
    ensure_plain_single_link_file(path)?;
    if fs::metadata(path)
        .map_err(|_| "Schema45 双区标记不可读".to_owned())?
        .len()
        > 4096
    {
        return Err("Schema45 双区标记过大".into());
    }
    let marker: Schema45PairInstallMarker =
        serde_json::from_slice(&fs::read(path).map_err(|_| "Schema45 双区标记不可读".to_owned())?)
            .map_err(|_| "Schema45 双区标记无效".to_owned())?;
    if marker.version != 1
        || !marker.stage_name.starts_with("schema45-pair-stage-")
        || marker.stage_name.len() == "schema45-pair-stage-".len()
        || !marker.stage_name["schema45-pair-stage-".len()..]
            .bytes()
            .all(|byte| byte.is_ascii_digit())
    {
        return Err("Schema45 双区标记版本或暂存名无效".into());
    }
    Ok(marker)
}

fn move_quiescent_sidecars_to_backup(
    database: &Path,
    backup_dir: &Path,
    name: &str,
) -> Result<(), String> {
    check_quiescent_sidecars(database)?;
    for suffix in ["-wal", "-shm"] {
        let mut source_name = database.as_os_str().to_os_string();
        source_name.push(suffix);
        let sidecar = PathBuf::from(source_name);
        match fs::symlink_metadata(&sidecar) {
            Ok(_) => {
                ensure_plain_single_link_file(&sidecar)?;
                let backup = backup_dir.join(format!("{name}{suffix}"));
                crate::desktop_legacy_area_split::commit_staged_area_file(&sidecar, &backup)?;
            }
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
            Err(_) => return Err("Schema45 双区 sidecar 状态不可读".into()),
        }
    }
    Ok(())
}

#[cfg(all(windows, debug_assertions))]
fn pause_schema45_pair_acceptance(root: &Path) -> Result<(), String> {
    if std::env::var("NANFENG_AI_WINDOWS_SCHEMA45_PAIR_PAUSE")
        .ok()
        .as_deref()
        != Some("after-chat")
    {
        return Ok(());
    }
    let verified = crate::desktop_windows_acceptance::resolve(
        Some(root),
        true,
        crate::desktop_windows_acceptance::IDENTIFIER,
        &std::env::temp_dir(),
    )?
    .ok_or("Schema45 双区验收根缺失")?;
    if verified != root {
        return Err("Schema45 双区验收根不一致".into());
    }
    let reached = root.join("schema45-pair-after-chat.reached");
    let file = fs::OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(reached)
        .map_err(|_| "Schema45 双区停点标记不可创建".to_owned())?;
    file.sync_all()
        .map_err(|_| "Schema45 双区停点标记不可保存".to_owned())?;
    loop {
        std::thread::park_timeout(std::time::Duration::from_secs(1));
    }
}

#[cfg(not(all(windows, debug_assertions)))]
fn pause_schema45_pair_acceptance(_root: &Path) -> Result<(), String> {
    Ok(())
}

fn publish_upstream43_pair_under_existing_lock(
    root: &Path,
    before_marker: impl FnOnce() -> Result<(), String>,
    before_work_replace: impl FnOnce() -> Result<(), String>,
) -> Result<(), String> {
    let chat = root.join("workspace.sqlite3");
    let work_dir = root.join("work-area");
    let work = work_dir.join("workspace.sqlite3");
    let recovery = root.join("data-area-migration-v1");
    let marker_path = recovery.join("schema45-pair-install.json");
    ensure_plain_recovery_directory(root)?;
    ensure_plain_recovery_directory(&work_dir)?;
    ensure_plain_single_link_file(&chat)?;
    ensure_plain_single_link_file(&work)?;
    let marker_present = match fs::symlink_metadata(&marker_path) {
        Ok(_) => {
            ensure_plain_single_link_file(&marker_path)?;
            true
        }
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => false,
        Err(_) => return Err("Schema45 双区发布标记状态不可读".into()),
    };
    if !marker_present {
        for (database, expected) in [(&chat, "CHAT"), (&work, "WORK")] {
            let connection =
                Connection::open_with_flags(database, OpenFlags::SQLITE_OPEN_READ_ONLY)
                    .map_err(|_| "Schema45 上游双区源库不可读".to_owned())?;
            let version: u32 = connection
                .pragma_query_value(None, "user_version", |row| row.get(0))
                .map_err(|_| "Schema45 上游双区版本不可读".to_owned())?;
            if !matches!(classify(&connection, version), Ok(SchemaSource::UpstreamArea43 { area, split_done: true }) if area == expected)
            {
                return Err("Schema45 上游双区身份不匹配，未迁移".into());
            }
        }
        checkpoint_legacy_source_under_existing_lock(&chat)?;
        checkpoint_legacy_source_under_existing_lock(&work)?;
        check_quiescent_sidecars(&chat)?;
        check_quiescent_sidecars(&work)?;
        let old_chat_fingerprint = source_fingerprint(&chat)?;
        let old_work_fingerprint = source_fingerprint(&work)?;
        fs::create_dir_all(&recovery).map_err(|_| "Schema45 双区恢复目录不可创建".to_owned())?;
        ensure_plain_recovery_directory(&recovery)?;
        let mut stage = None;
        for index in 0..1000 {
            let name = format!("schema45-pair-stage-{index}");
            let path = recovery.join(&name);
            match fs::create_dir(&path) {
                Ok(()) => {
                    stage = Some((name, path));
                    break;
                }
                Err(error) if error.kind() == std::io::ErrorKind::AlreadyExists => continue,
                Err(_) => return Err("Schema45 双区暂存不可创建".into()),
            }
        }
        let (stage_name, stage) = stage.ok_or("Schema45 双区暂存次数超限")?;
        let stage_chat_dir = stage.join("chat");
        let stage_work_dir = stage.join("work");
        fs::create_dir(&stage_chat_dir).map_err(|_| "Schema45 CHAT 暂存目录不可创建".to_owned())?;
        fs::create_dir(&stage_work_dir).map_err(|_| "Schema45 WORK 暂存目录不可创建".to_owned())?;
        copy_plain_private_file(&chat, &stage_chat_dir.join("workspace.sqlite3"))?;
        copy_plain_private_file(&work, &stage_work_dir.join("workspace.sqlite3"))?;
        let new_chat = prepare_schema45_candidate(&stage_chat_dir.join("workspace.sqlite3"))?;
        let new_work = prepare_schema45_candidate(&stage_work_dir.join("workspace.sqlite3"))?;
        verify_installed_area(&new_chat, "CHAT")?;
        verify_installed_area(&new_work, "WORK")?;
        before_marker()?;
        check_quiescent_sidecars(&chat)?;
        check_quiescent_sidecars(&work)?;
        if source_fingerprint(&chat)? != old_chat_fingerprint
            || source_fingerprint(&work)? != old_work_fingerprint
        {
            return Err("Schema45 双区源库在暂存期间变化，未发布".into());
        }
        let marker = Schema45PairInstallMarker {
            version: 1,
            stage_name,
            old_chat_sha256: old_chat_fingerprint.0,
            new_chat_sha256: crate::sha256_file(&new_chat)?,
            old_work_sha256: old_work_fingerprint.0,
            new_work_sha256: crate::sha256_file(&new_work)?,
        };
        write_atomic_new_file(
            &marker_path,
            &serde_json::to_vec(&marker).map_err(|_| "Schema45 双区标记不可编码".to_owned())?,
        )?;
    }
    let marker = read_pair_install_marker(&marker_path)?;
    let stage = recovery.join(&marker.stage_name);
    ensure_plain_recovery_directory(&stage)?;
    let new_chat = stage.join("chat/data-area-migration-v1/schema45-candidate.sqlite3");
    let new_work = stage.join("work/data-area-migration-v1/schema45-candidate.sqlite3");
    for (candidate, expected_hash, area) in [
        (&new_chat, &marker.new_chat_sha256, "CHAT"),
        (&new_work, &marker.new_work_sha256, "WORK"),
    ] {
        ensure_plain_single_link_file(candidate)?;
        if crate::sha256_file(candidate)? != *expected_hash {
            return Err("Schema45 双区候选字节变化".into());
        }
        verify_installed_area(candidate, area)?;
        check_quiescent_sidecars(candidate)?;
    }
    let active_chat_hash = crate::sha256_file(&chat)?;
    let active_work_hash = crate::sha256_file(&work)?;
    if ![&marker.old_chat_sha256, &marker.new_chat_sha256].contains(&&active_chat_hash)
        || ![&marker.old_work_sha256, &marker.new_work_sha256].contains(&&active_work_hash)
    {
        return Err("Schema45 双区活动库与发布标记不符".into());
    }
    let backup_chat = recovery.join("original-pair-chat.sqlite3");
    let backup_work = recovery.join("original-pair-work.sqlite3");
    if active_chat_hash == marker.old_chat_sha256 {
        check_quiescent_sidecars(&chat)?;
        copy_plain_private_file(&chat, &backup_chat)?;
    }
    if active_work_hash == marker.old_work_sha256 {
        check_quiescent_sidecars(&work)?;
        copy_plain_private_file(&work, &backup_work)?;
    }
    if crate::sha256_file(&backup_chat)? != marker.old_chat_sha256
        || crate::sha256_file(&backup_work)? != marker.old_work_sha256
    {
        return Err("Schema45 双区原库备份与标记不符".into());
    }
    if active_chat_hash == marker.old_chat_sha256 {
        let replacement = recovery.join("install-pair-chat.sqlite3");
        copy_plain_private_file(&new_chat, &replacement)?;
        move_quiescent_sidecars_to_backup(&chat, &recovery, "original-pair-chat.sqlite3")?;
        if crate::sha256_file(&chat)? != marker.old_chat_sha256 {
            return Err("Schema45 CHAT 发布前变化".into());
        }
        replace_active_chat_database(&replacement, &chat)?;
    }
    before_work_replace()?;
    if active_work_hash == marker.old_work_sha256 {
        let replacement = recovery.join("install-pair-work.sqlite3");
        copy_plain_private_file(&new_work, &replacement)?;
        move_quiescent_sidecars_to_backup(&work, &recovery, "original-pair-work.sqlite3")?;
        if crate::sha256_file(&work)? != marker.old_work_sha256 {
            return Err("Schema45 WORK 发布前变化".into());
        }
        replace_active_chat_database(&replacement, &work)?;
    }
    if crate::sha256_file(&chat)? != marker.new_chat_sha256
        || crate::sha256_file(&work)? != marker.new_work_sha256
    {
        return Err("Schema45 双区发布字节不符".into());
    }
    verify_installed_area(&chat, "CHAT")?;
    verify_installed_area(&work, "WORK")?;
    fs::remove_file(&marker_path).map_err(|_| "Schema45 双区已发布但标记未清理".to_owned())?;
    Ok(())
}

#[allow(
    dead_code,
    reason = "W04A will route startup after isolated publish recovery is proven"
)]
pub(crate) fn publish_schema45_bundle(source: &Path) -> Result<(), String> {
    let root = source.parent().ok_or("Schema45 源根无效")?;
    let lease = RootMigrationLease::acquire(root)?;
    publish_schema45_bundle_under_existing_lock(source, &lease._file, || Ok(()))
}

fn publish_schema45_bundle_under_existing_lock(
    source: &Path,
    root_lock: &fs::File,
    before_chat_replace: impl FnOnce() -> Result<(), String>,
) -> Result<(), String> {
    let root = source.parent().ok_or("Schema45 源根无效")?;
    ensure_plain_recovery_directory(root)?;
    let recovery = root.join("data-area-migration-v1");
    let stage = recovery.join("schema45-stage");
    let marker_path = recovery.join("schema45-install.json");
    let marker_present = match fs::symlink_metadata(&marker_path) {
        Ok(_) => true,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => false,
        Err(_) => return Err("Schema45 发布标记状态不可读".into()),
    };
    if !marker_present {
        for path in [
            root.join("work-area"),
            root.join("app-configuration.sqlite3"),
        ] {
            match fs::symlink_metadata(&path) {
                Ok(_) => return Err("Schema45 目标已有独立数据，未发布".into()),
                Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
                Err(_) => return Err("Schema45 目标状态不可读".into()),
            }
        }
        let prepared = prepare_schema45_split_bundle_under_existing_lock(source, root_lock)?;
        if prepared != stage {
            return Err("Schema45 暂存根不一致".into());
        }
        let stage_chat = stage.join("workspace.sqlite3");
        let stage_work = stage.join("work-area");
        let stage_configuration = stage.join("app-configuration.sqlite3");
        for database in [
            source,
            &stage_chat,
            &stage_work.join("workspace.sqlite3"),
            &stage_configuration,
        ] {
            check_quiescent_sidecars(database)?;
        }
        verify_installed_area(&stage_chat, "CHAT")?;
        verify_installed_area(&stage_work.join("workspace.sqlite3"), "WORK")?;
        let marker = Schema45InstallMarker {
            version: 1,
            old_chat_sha256: crate::sha256_file(source)?,
            new_chat_sha256: crate::sha256_file(&stage_chat)?,
            work_tree_sha256: tree_fingerprint(&stage_work)?,
            configuration_sha256: crate::sha256_file(&stage_configuration)?,
        };
        write_atomic_new_file(
            &marker_path,
            &serde_json::to_vec(&marker).map_err(|_| "Schema45 发布标记不可编码".to_owned())?,
        )?;
    }
    let marker = read_install_marker(&marker_path)?;
    ensure_plain_recovery_directory(&stage)?;
    let stage_chat = stage.join("workspace.sqlite3");
    let stage_work = stage.join("work-area");
    let stage_configuration = stage.join("app-configuration.sqlite3");
    for file in [&stage_chat, &stage_configuration] {
        ensure_plain_single_link_file(file)?;
    }
    if crate::sha256_file(&stage_chat)? != marker.new_chat_sha256
        || tree_fingerprint(&stage_work)? != marker.work_tree_sha256
        || crate::sha256_file(&stage_configuration)? != marker.configuration_sha256
    {
        return Err("Schema45 暂存包与发布标记不符，未发布".into());
    }
    ensure_plain_single_link_file(source)?;
    let active_hash = crate::sha256_file(source)?;
    if active_hash != marker.old_chat_sha256 && active_hash != marker.new_chat_sha256 {
        return Err("Schema45 活动库既非旧库也非新库，未继续".into());
    }
    let backup = recovery.join("original-before-install.sqlite3");
    if active_hash == marker.old_chat_sha256 {
        check_quiescent_sidecars(source)?;
        copy_plain_private_file(source, &backup)?;
    } else {
        ensure_plain_single_link_file(&backup)?;
    }
    if crate::sha256_file(&backup)? != marker.old_chat_sha256 {
        return Err("Schema45 旧库备份与发布标记不符".into());
    }
    let active_work = root.join("work-area");
    copy_plain_private_tree(&stage_work, &active_work)?;
    if tree_fingerprint(&active_work)? != marker.work_tree_sha256 {
        return Err("Schema45 WORK 发布副本不完整".into());
    }
    let active_configuration = root.join("app-configuration.sqlite3");
    copy_plain_private_file(&stage_configuration, &active_configuration)?;
    if crate::sha256_file(&active_configuration)? != marker.configuration_sha256 {
        return Err("Schema45 共享配置发布副本不完整".into());
    }
    if active_hash == marker.old_chat_sha256 {
        let replacement = recovery.join("install-chat.sqlite3");
        copy_plain_private_file(&stage_chat, &replacement)?;
        for suffix in ["-wal", "-shm"] {
            let mut source_sidecar_name = source.as_os_str().to_os_string();
            source_sidecar_name.push(suffix);
            let source_sidecar = PathBuf::from(source_sidecar_name);
            let backup_sidecar = recovery.join(format!("original-workspace.sqlite3{suffix}"));
            match fs::symlink_metadata(&source_sidecar) {
                Ok(_) => {
                    ensure_plain_single_link_file(&source_sidecar)?;
                    crate::desktop_legacy_area_split::commit_staged_area_file(
                        &source_sidecar,
                        &backup_sidecar,
                    )?;
                }
                Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
                    if fs::symlink_metadata(&backup_sidecar).is_ok() {
                        ensure_plain_single_link_file(&backup_sidecar)?;
                    }
                }
                Err(_) => return Err("Schema45 旧库 sidecar 状态不可读".into()),
            }
        }
        before_chat_replace()?;
        if crate::sha256_file(source)? != marker.old_chat_sha256 {
            return Err("Schema45 旧库在发布前变化，未替换".into());
        }
        replace_active_chat_database(&replacement, source)?;
    }
    if crate::sha256_file(source)? != marker.new_chat_sha256 {
        return Err("Schema45 CHAT 发布字节不符".into());
    }
    verify_installed_area(source, "CHAT")?;
    verify_installed_area(&active_work.join("workspace.sqlite3"), "WORK")?;
    if tree_fingerprint(&active_work)? != marker.work_tree_sha256 {
        return Err("Schema45 WORK 发布后文件树变化".into());
    }
    fs::remove_file(&marker_path).map_err(|_| "Schema45 已发布但标记未清理".to_owned())?;
    Ok(())
}

/// Called only while startup holds the process-wide root lock. It never opens
/// an old database through the ordinary Store migration before a recoverable
/// Schema45 publication has been prepared.
pub(crate) fn reconcile_schema45_at_startup_under_existing_lock(
    root: &Path,
    root_lock: &fs::File,
) -> Result<(), String> {
    use crate::{DesktopStartupMode, DesktopWorkspaceStore};

    ensure_plain_recovery_directory(root)?;
    ensure_plain_single_link_file(&root.join(".runtime-owner.lock"))?;
    let source = root.join("workspace.sqlite3");
    let install_marker = root.join("data-area-migration-v1/schema45-install.json");
    let pair_marker = root.join("data-area-migration-v1/schema45-pair-install.json");
    match fs::symlink_metadata(&pair_marker) {
        Ok(_) => {
            if fs::symlink_metadata(&install_marker).is_ok() {
                return Err("Schema45 两种安装标记并存，未继续".into());
            }
            return publish_upstream43_pair_under_existing_lock(
                root,
                || Ok(()),
                || pause_schema45_pair_acceptance(root),
            );
        }
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
        Err(_) => return Err("Schema45 双区安装标记状态不可读".into()),
    }
    match fs::symlink_metadata(&install_marker) {
        Ok(_) => {
            return publish_schema45_bundle_under_existing_lock(&source, root_lock, || Ok(()));
        }
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
        Err(_) => return Err("Schema45 安装标记状态不可读".into()),
    }
    match fs::symlink_metadata(&source) {
        Ok(_) => ensure_plain_single_link_file(&source)?,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
            let acceptance_marker = root.join(".nanfeng-windows-acceptance.json");
            let acceptance = match fs::symlink_metadata(&acceptance_marker) {
                Ok(_) => {
                    ensure_plain_single_link_file(&acceptance_marker)?;
                    true
                }
                Err(error) if error.kind() == std::io::ErrorKind::NotFound => false,
                Err(_) => return Err("Schema45 验收标记状态不可读".into()),
            };
            for entry in fs::read_dir(root).map_err(|_| "Schema45 新根不可枚举".to_owned())? {
                let entry = entry.map_err(|_| "Schema45 新根不可枚举".to_owned())?;
                match entry.file_name().to_str() {
                    Some(".runtime-owner.lock" | ".nanfeng-windows-acceptance.json") => {
                        ensure_plain_single_link_file(&entry.path())?;
                    }
                    Some("webview2") => ensure_plain_recovery_directory(&entry.path())?,
                    Some("startup.stdout.log" | "startup.stderr.log") if acceptance => {
                        ensure_plain_single_link_file(&entry.path())?;
                    }
                    _ => {
                        return Err("Schema45 缺少旧库但数据根已有其他内容，未初始化空库".into());
                    }
                }
            }
            let fresh = DesktopWorkspaceStore::open_for_startup(
                root.to_path_buf(),
                DesktopStartupMode::UiSchemaDiagnostic,
            )?;
            drop(fresh);
        }
        Err(_) => return Err("Schema45 源库状态不可读".into()),
    }
    let connection = Connection::open_with_flags(&source, OpenFlags::SQLITE_OPEN_READ_ONLY)
        .map_err(|_| "Schema45 启动源库不可读".to_owned())?;
    let version: u32 = connection
        .pragma_query_value(None, "user_version", |row| row.get(0))
        .map_err(|_| "Schema45 启动源版本不可读".to_owned())?;
    let branch =
        classify(&connection, version).map_err(|_| "Schema45 启动源结构不可读".to_owned())?;
    drop(connection);
    match branch {
        SchemaSource::Common45 {
            area,
            split_done: true,
        }
        | SchemaSource::Common46 {
            area,
            split_done: true,
        }
        | SchemaSource::Common47 {
            area,
            split_done: true,
        }
        | SchemaSource::Common48 {
            area,
            split_done: true,
        }
        | SchemaSource::Common49 {
            area,
            split_done: true,
        }
        | SchemaSource::Common50 {
            area,
            split_done: true,
        }
        | SchemaSource::Common51 {
            area,
            split_done: true,
        } | SchemaSource::Common52 {
            area,
            split_done: true,
        } | SchemaSource::Common53 {
            area,
            split_done: true,
        } | SchemaSource::Common54 {
            area,
            split_done: true,
        } | SchemaSource::Common55 {
            area,
            split_done: true,
        } | SchemaSource::Common56 {
            area,
            split_done: true,
        } | SchemaSource::Common57 {
            area,
            split_done: true,
        } | SchemaSource::Common58 {
            area,
            split_done: true,
        } | SchemaSource::Common59 {
            area,
            split_done: true,
        } if area == "CHAT" => return Ok(()),
        SchemaSource::Legacy(42)
        | SchemaSource::UpstreamArea43 {
            area: _,
            split_done: false,
        }
        | SchemaSource::WindowsDraft43
        | SchemaSource::WindowsDraft44 => {}
        SchemaSource::UpstreamArea43 {
            area,
            split_done: true,
        } if area == "CHAT" => {
            return publish_upstream43_pair_under_existing_lock(
                root,
                || Ok(()),
                || pause_schema45_pair_acceptance(root),
            );
        }
        _ => return Err("Schema45 启动源不是可迁移的旧单区，未修改".into()),
    }
    match fs::symlink_metadata(root.join("work-area")) {
        Ok(_) => return Err("已有 WORK 目录，旧单区未自动迁移".into()),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
        Err(_) => return Err("WORK 目录状态不可读".into()),
    }
    checkpoint_legacy_source_under_existing_lock(&source)?;
    publish_schema45_bundle_under_existing_lock(&source, root_lock, || Ok(()))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{
        DesktopConversationDraftArgs, DesktopDataArea, DesktopStartupMode, DesktopWorkspaceStore,
        DomainMutationArgs, HistoryArgs, ModelMetadataArgs,
    };
    use serde_json::{json, Value};
    use tempfile::tempdir;

    fn mixed_synthetic_source(root: &Path) -> PathBuf {
        let store = DesktopWorkspaceStore::open(root.to_path_buf()).unwrap();
        let mut exchange: Value = serde_json::from_str(include_str!(
            "../../../protocol/fixtures/nfai.exchange.v1.golden.json"
        ))
        .unwrap();
        exchange["conversations"][0]["messages"][1]["blocks"] =
            json!([{ "kind": "TEXT", "ordinal": 0, "text": "发布合成内容" }]);
        let hash = crate::refresh_exchange_hash(&mut exchange).unwrap();
        crate::validate_exchange(&exchange).unwrap();
        let connection = store.connection().unwrap();
        connection
            .execute(
                "INSERT INTO workspaces VALUES('legacy','legacy',?1,?1,'2026-09-23T00:00:00Z')",
                [&hash],
            )
            .unwrap();
        connection
            .execute(
                "INSERT INTO workspace_exchange VALUES('legacy',?1)",
                [exchange.to_string()],
            )
            .unwrap();
        drop(connection);
        drop(store);
        root.join("workspace.sqlite3")
    }

    #[test]
    fn already_split_upstream43_pair_recovers_after_chat_publish_and_keeps_both_areas() {
        let directory = tempdir().unwrap();
        let root = directory.path().join("app-data");
        let chat = mixed_synthetic_source(&root);
        let work_dir = root.join("work-area");
        fs::create_dir(&work_dir).unwrap();
        let work = work_dir.join("workspace.sqlite3");
        fs::copy(&chat, &work).unwrap();
        let configuration = {
            let connection = Connection::open(&chat).unwrap();
            crate::desktop_data_area::configuration_database(&root, &connection).unwrap()
        };
        let configuration_hash = crate::sha256_file(&configuration).unwrap();
        for (database, area, value) in [
            (&chat, "CHAT", "chat-original"),
            (&work, "WORK", "work-original"),
        ] {
            let connection = Connection::open(database).unwrap();
            connection.execute_batch("ALTER TABLE desktop_conversation_drafts_v1 DROP COLUMN model_id;
                ALTER TABLE desktop_conversation_drafts_v1 DROP COLUMN tone_override;
                ALTER TABLE desktop_conversation_drafts_v1 DROP COLUMN web_search_override;
                CREATE TABLE synthetic_pair_marker(value TEXT NOT NULL);
                CREATE TABLE desktop_data_area_v1(id INTEGER PRIMARY KEY CHECK(id=1), area TEXT NOT NULL, split_done INTEGER NOT NULL);") .unwrap();
            connection
                .execute("INSERT INTO desktop_data_area_v1 VALUES(1,?1,1)", [area])
                .unwrap();
            connection
                .execute("INSERT INTO synthetic_pair_marker VALUES(?1)", [value])
                .unwrap();
            connection.pragma_update(None, "user_version", 43).unwrap();
        }
        fs::write(work_dir.join("synthetic-asset.txt"), b"work-private-asset").unwrap();
        {
            let connection = Connection::open(&work).unwrap();
            connection
                .execute("UPDATE desktop_data_area_v1 SET area='CHAT'", [])
                .unwrap();
        }
        let mismatched_chat = crate::sha256_file(&chat).unwrap();
        let mismatched_work = crate::sha256_file(&work).unwrap();
        assert!(publish_upstream43_pair_under_existing_lock(&root, || Ok(()), || Ok(())).is_err());
        assert_eq!(crate::sha256_file(&chat).unwrap(), mismatched_chat);
        assert_eq!(crate::sha256_file(&work).unwrap(), mismatched_work);
        assert!(!root
            .join("data-area-migration-v1/schema45-pair-install.json")
            .exists());
        {
            let connection = Connection::open(&work).unwrap();
            connection
                .execute("UPDATE desktop_data_area_v1 SET area='WORK'", [])
                .unwrap();
        }
        let lease = RootMigrationLease::acquire(&root).unwrap();
        let before_changed_chat = crate::sha256_file(&chat).unwrap();
        assert!(publish_upstream43_pair_under_existing_lock(
            &root,
            || {
                let connection = Connection::open(&work).unwrap();
                connection
                    .execute(
                        "UPDATE synthetic_pair_marker SET value='changed-during-stage'",
                        [],
                    )
                    .unwrap();
                Ok(())
            },
            || Ok(())
        )
        .is_err());
        assert!(!root
            .join("data-area-migration-v1/schema45-pair-install.json")
            .exists());
        assert_eq!(crate::sha256_file(&chat).unwrap(), before_changed_chat);
        assert_eq!(
            Connection::open(&work)
                .unwrap()
                .query_row("SELECT value FROM synthetic_pair_marker", [], |row| row
                    .get::<_, String>(
                    0
                ))
                .unwrap(),
            "changed-during-stage"
        );
        Connection::open(&work)
            .unwrap()
            .execute("UPDATE synthetic_pair_marker SET value='work-original'", [])
            .unwrap();
        let old_chat_hash = crate::sha256_file(&chat).unwrap();
        let old_work_hash = crate::sha256_file(&work).unwrap();
        assert!(publish_upstream43_pair_under_existing_lock(
            &root,
            || Ok(()),
            || Err("synthetic kill point".into())
        )
        .is_err());
        assert!(root
            .join("data-area-migration-v1/schema45-pair-install.json")
            .is_file());
        assert_eq!(crate::sha256_file(&work).unwrap(), old_work_hash);
        assert_ne!(crate::sha256_file(&chat).unwrap(), old_chat_hash);
        reconcile_schema45_at_startup_under_existing_lock(&root, &lease._file).unwrap();
        assert!(!root
            .join("data-area-migration-v1/schema45-pair-install.json")
            .exists());
        for (database, area, value) in [
            (&chat, "CHAT", "chat-original"),
            (&work, "WORK", "work-original"),
        ] {
            verify_installed_area(database, area).unwrap();
            let connection = Connection::open(database).unwrap();
            let stored: String = connection
                .query_row("SELECT value FROM synthetic_pair_marker", [], |row| {
                    row.get(0)
                })
                .unwrap();
            assert_eq!(stored, value);
        }
        assert_eq!(
            fs::read(work_dir.join("synthetic-asset.txt")).unwrap(),
            b"work-private-asset"
        );
        assert_eq!(
            crate::sha256_file(&configuration).unwrap(),
            configuration_hash
        );
        assert_eq!(
            crate::sha256_file(&root.join("data-area-migration-v1/original-pair-chat.sqlite3"))
                .unwrap(),
            old_chat_hash
        );
        assert_eq!(
            crate::sha256_file(&root.join("data-area-migration-v1/original-pair-work.sqlite3"))
                .unwrap(),
            old_work_hash
        );
        drop(lease);
        let (chat_store, work_store) = crate::open_desktop_area_stores_for_startup(
            root.clone(),
            DesktopStartupMode::UiSchemaDiagnostic,
        )
        .unwrap();
        assert_eq!(
            chat_store
                .connection()
                .unwrap()
                .pragma_query_value(None, "user_version", |row| row.get::<_, u32>(0))
                .unwrap(),
            crate::desktop_schema_source::CURRENT_AREA_SCHEMA_VERSION
        );
        assert_eq!(
            work_store
                .unwrap()
                .connection()
                .unwrap()
                .pragma_query_value(None, "user_version", |row| row.get::<_, u32>(0))
                .unwrap(),
            crate::desktop_schema_source::CURRENT_AREA_SCHEMA_VERSION
        );
        assert_eq!(
            crate::sha256_file(&configuration).unwrap(),
            configuration_hash
        );
    }

    #[test]
    #[ignore = "requires an explicitly marked temporary Windows acceptance root"]
    fn prepare_explicit_upstream43_pair_acceptance_root() {
        let requested = std::env::var_os("NANFENG_AI_SCHEMA45_PAIR_ACCEPTANCE_ROOT")
            .expect("set an explicit temporary Windows acceptance root");
        let root = crate::desktop_windows_acceptance::resolve(
            Some(Path::new(&requested)),
            true,
            crate::desktop_windows_acceptance::IDENTIFIER,
            &std::env::temp_dir(),
        )
        .expect("invalid Windows acceptance root")
        .expect("missing Windows acceptance root");
        for path in [
            root.join("workspace.sqlite3"),
            root.join("work-area"),
            root.join("data-area-migration-v1"),
        ] {
            assert!(!path.exists(), "acceptance root must be new and empty");
        }
        let chat = mixed_synthetic_source(&root);
        let work_dir = root.join("work-area");
        fs::create_dir(&work_dir).unwrap();
        let work = work_dir.join("workspace.sqlite3");
        fs::copy(&chat, &work).unwrap();
        {
            let connection = Connection::open(&chat).unwrap();
            crate::desktop_data_area::configuration_database(&root, &connection).unwrap();
        }
        for (database, area, value) in [
            (&chat, "CHAT", "native-chat-43"),
            (&work, "WORK", "native-work-43"),
        ] {
            let connection = Connection::open(database).unwrap();
            connection.execute_batch("ALTER TABLE desktop_conversation_drafts_v1 DROP COLUMN model_id;
                ALTER TABLE desktop_conversation_drafts_v1 DROP COLUMN tone_override;
                ALTER TABLE desktop_conversation_drafts_v1 DROP COLUMN web_search_override;
                CREATE TABLE synthetic_pair_marker(value TEXT NOT NULL);
                CREATE TABLE desktop_data_area_v1(id INTEGER PRIMARY KEY CHECK(id=1), area TEXT NOT NULL, split_done INTEGER NOT NULL);").unwrap();
            connection
                .execute("INSERT INTO desktop_data_area_v1 VALUES(1,?1,1)", [area])
                .unwrap();
            connection
                .execute("INSERT INTO synthetic_pair_marker VALUES(?1)", [value])
                .unwrap();
            connection.pragma_update(None, "user_version", 43).unwrap();
        }
        fs::write(
            work_dir.join("synthetic-asset.txt"),
            b"native-work-asset-43",
        )
        .unwrap();
        println!("SCHEMA45_PAIR_WINDOWS_ACCEPTANCE_ROOT={}", root.display());
    }

    #[test]
    #[ignore = "requires an explicitly marked temporary Windows acceptance root"]
    fn prepare_explicit_windows_schema45_ui_acceptance_root() {
        let requested = std::env::var_os("NANFENG_AI_SCHEMA45_ACCEPTANCE_ROOT")
            .expect("set an explicit temporary Windows acceptance root");
        let root = crate::desktop_windows_acceptance::resolve(
            Some(Path::new(&requested)),
            true,
            crate::desktop_windows_acceptance::IDENTIFIER,
            &std::env::temp_dir(),
        )
        .expect("invalid Windows acceptance root")
        .expect("missing Windows acceptance root");
        for path in [
            root.join("workspace.sqlite3"),
            root.join("work-area"),
            root.join("data-area-migration-v1"),
        ] {
            assert!(!path.exists(), "acceptance root must be new and empty");
        }
        let source = mixed_synthetic_source(&root);
        publish_schema45_bundle(&source).unwrap();
        let (chat, work) =
            crate::open_desktop_area_stores_for_startup(root.clone(), DesktopStartupMode::Normal)
                .unwrap();
        assert_ne!(chat.database, work.unwrap().database);
        println!("SCHEMA45_WINDOWS_ACCEPTANCE_ROOT={}", root.display());
    }

    #[test]
    #[ignore = "requires an explicitly marked temporary Windows acceptance root"]
    fn prepare_explicit_windows_legacy_startup_acceptance_root() {
        let requested = std::env::var_os("NANFENG_AI_SCHEMA45_ACCEPTANCE_ROOT")
            .expect("set an explicit temporary Windows acceptance root");
        let root = crate::desktop_windows_acceptance::resolve(
            Some(Path::new(&requested)),
            true,
            crate::desktop_windows_acceptance::IDENTIFIER,
            &std::env::temp_dir(),
        )
        .expect("invalid Windows acceptance root")
        .expect("missing Windows acceptance root");
        for path in [
            root.join("workspace.sqlite3"),
            root.join("work-area"),
            root.join("data-area-migration-v1"),
        ] {
            assert!(!path.exists(), "acceptance root must be new and empty");
        }
        let source = mixed_synthetic_source(&root);
        let connection =
            Connection::open_with_flags(&source, OpenFlags::SQLITE_OPEN_READ_ONLY).unwrap();
        let version: u32 = connection
            .pragma_query_value(None, "user_version", |row| row.get(0))
            .unwrap();
        assert_eq!(version, 44);
        println!("SCHEMA44_WINDOWS_ACCEPTANCE_ROOT={}", root.display());
    }

    #[test]
    fn locked_startup_publishes_legacy_source_before_opening_area_stores() {
        let directory = tempdir().unwrap();
        let root = directory.path().join("app-data");
        let source = mixed_synthetic_source(&root);
        let lease = RootMigrationLease::acquire(&root).unwrap();
        reconcile_schema45_at_startup_under_existing_lock(&root, &lease._file).unwrap();
        drop(lease);
        let (chat, work) =
            crate::open_desktop_area_stores_for_startup(root.clone(), DesktopStartupMode::Normal)
                .unwrap();
        assert_ne!(chat.database, work.unwrap().database);
        assert!(root
            .join("data-area-migration-v1/original-before-install.sqlite3")
            .is_file());
        assert_eq!(source, chat.database);
    }

    #[test]
    fn locked_startup_bootstraps_a_new_empty_root_and_upgrades_both_areas() {
        for webview_already_created in [false, true] {
            let directory = tempdir().unwrap();
            let root = directory.path().join("app-data");
            fs::create_dir(&root).unwrap();
            if webview_already_created {
                fs::create_dir(root.join("webview2")).unwrap();
                fs::write(root.join(".nanfeng-windows-acceptance.json"), b"synthetic").unwrap();
                fs::write(root.join("startup.stdout.log"), []).unwrap();
                fs::write(root.join("startup.stderr.log"), []).unwrap();
            }
            let lease = RootMigrationLease::acquire(&root).unwrap();
            reconcile_schema45_at_startup_under_existing_lock(&root, &lease._file).unwrap();
            drop(lease);
            let (chat, work) = crate::open_desktop_area_stores_for_startup(
                root.clone(),
                DesktopStartupMode::UiSchemaDiagnostic,
            )
            .unwrap();
            assert_eq!(chat.list_workspaces().unwrap().len(), 0);
            assert_eq!(work.as_ref().unwrap().list_workspaces().unwrap().len(), 0);
            verify_installed_area(&root.join("workspace.sqlite3"), "CHAT").unwrap();
            verify_installed_area(&root.join("work-area/workspace.sqlite3"), "WORK").unwrap();
            for database in [
                root.join("workspace.sqlite3"),
                root.join("work-area/workspace.sqlite3"),
            ] {
                let connection = Connection::open(database).unwrap();
                assert_eq!(
                    connection
                        .pragma_query_value(None, "user_version", |row| row.get::<_, u32>(0))
                        .unwrap(),
                    crate::desktop_schema_source::CURRENT_AREA_SCHEMA_VERSION
                );
            }
            drop(work);
            drop(chat);
            let lease = RootMigrationLease::acquire(&root).unwrap();
            reconcile_schema45_at_startup_under_existing_lock(&root, &lease._file).unwrap();
            drop(lease);
            let (reopened_chat, reopened_work) = crate::open_desktop_area_stores_for_startup(
                root.clone(),
                DesktopStartupMode::UiSchemaDiagnostic,
            )
            .unwrap();
            assert!(reopened_chat.list_workspaces().unwrap().is_empty());
            assert!(reopened_work.unwrap().list_workspaces().unwrap().is_empty());
        }
    }

    #[test]
    fn schema45_chat_backup_restores_from_current_schema_and_requires_fresh_restart() {
        let directory = tempdir().unwrap();
        let root = directory.path().join("app-data");
        fs::create_dir(&root).unwrap();
        let lease = RootMigrationLease::acquire(&root).unwrap();
        reconcile_schema45_at_startup_under_existing_lock(&root, &lease._file).unwrap();
        drop(lease);
        let database = root.join("workspace.sqlite3");
        assert_eq!(
            Connection::open(&database)
                .unwrap()
                .pragma_query_value(None, "user_version", |row| row.get::<_, u32>(0))
                .unwrap(),
            45
        );
        let selected = directory.path().join("old-chat.nfai-backup");
        crate::desktop_local_backup_v1::export_selected(
            &root,
            &database,
            selected.to_str().unwrap(),
            "synthetic",
        )
        .unwrap();
        let (chat, work) = crate::open_desktop_area_stores_for_startup(
            root.clone(),
            DesktopStartupMode::UiSchemaDiagnostic,
        )
        .unwrap();
        assert_eq!(
            Connection::open(&database)
                .unwrap()
                .pragma_query_value(None, "user_version", |row| row.get::<_, u32>(0))
                .unwrap(),
            crate::desktop_schema_source::CURRENT_AREA_SCHEMA_VERSION
        );
        drop(work);
        drop(chat);
        let preflight = crate::desktop_local_backup_v1::preflight_selected(
            &root,
            &database,
            selected.to_str().unwrap(),
        )
        .unwrap();
        assert_eq!(preflight.schema_version, 45);
        crate::desktop_local_backup_v1::restore_preflighted(
            &root,
            &database,
            &preflight.fingerprint,
            true,
        )
        .unwrap();
        assert_eq!(
            Connection::open(&database)
                .unwrap()
                .pragma_query_value(None, "user_version", |row| row.get::<_, u32>(0))
                .unwrap(),
            45
        );
        assert!(crate::desktop_local_backup_v1::requires_restart(&root));
        assert_eq!(
            Connection::open(root.join("work-area/workspace.sqlite3"))
                .unwrap()
                .pragma_query_value(None, "user_version", |row| row.get::<_, u32>(0))
                .unwrap(),
            crate::desktop_schema_source::CURRENT_AREA_SCHEMA_VERSION
        );
    }

    #[test]
    fn current_schema_replay_checks_full_request_and_refuses_legacy_unknown_requests() {
        let directory = tempdir().unwrap();
        let root = directory.path().join("app-data");
        fs::create_dir(&root).unwrap();
        let lease = RootMigrationLease::acquire(&root).unwrap();
        reconcile_schema45_at_startup_under_existing_lock(&root, &lease._file).unwrap();
        drop(lease);
        let legacy = DesktopWorkspaceStore::open_committed_schema45_area(
            root.clone(),
            DesktopDataArea::Chat,
            DesktopStartupMode::UiSchemaDiagnostic,
        )
        .unwrap();
        let workspace_id = legacy.ensure_local_workspace().unwrap().summary.id;
        let legacy_args = || DomainMutationArgs {
            intent_id: "schema45-old-request".into(),
            workspace_id: workspace_id.clone(),
            entity: "project".into(),
            action: "create".into(),
            object_id: None,
            expected_revision: None,
            fields: json!({"title":"旧请求","description":"合成"}),
        };
        legacy.mutate_domain(legacy_args()).unwrap();
        drop(legacy);

        let (chat, work) = crate::open_desktop_area_stores_for_startup(
            root.clone(),
            DesktopStartupMode::UiSchemaDiagnostic,
        )
        .unwrap();
        assert_eq!(
            Connection::open(root.join("workspace.sqlite3"))
                .unwrap()
                .pragma_query_value(None, "user_version", |row| row.get::<_, u32>(0))
                .unwrap(),
            crate::desktop_schema_source::CURRENT_AREA_SCHEMA_VERSION
        );
        assert_eq!(
            Connection::open(root.join("work-area/workspace.sqlite3"))
                .unwrap()
                .pragma_query_value(None, "user_version", |row| row.get::<_, u32>(0))
                .unwrap(),
            crate::desktop_schema_source::CURRENT_AREA_SCHEMA_VERSION
        );
        let replay_error = chat.mutate_domain(legacy_args()).unwrap_err();
        assert!(
            replay_error.contains("旧 intent 无可验证请求指纹"),
            "{replay_error}"
        );
        let new_args = || DomainMutationArgs {
            intent_id: "schema46-new-request".into(),
            workspace_id: workspace_id.clone(),
            entity: "project".into(),
            action: "create".into(),
            object_id: None,
            expected_revision: None,
            fields: json!({"title":"新请求","description":"合成"}),
        };
        let original = chat.mutate_domain(new_args()).unwrap();
        assert!(chat
            .mutate_domain(DomainMutationArgs {
                fields: json!({"title":"被篡改的新请求","description":"合成"}),
                ..new_args()
            })
            .unwrap_err()
            .contains("请求内容与首次提交不符"));
        assert!(chat.mutate_domain(new_args()).unwrap().replayed);
        let metadata = || ModelMetadataArgs {
            intent_id: "schema46-model-metadata".into(),
            workspace_id: workspace_id.clone(),
            expected_revision: 0,
            provider_id: "openrouter".into(),
            model_id: "terra-model".into(),
            metadata: json!({"pricingStatus":"UNKNOWN"}),
        };
        chat.upsert_model_metadata(metadata()).unwrap();
        assert!(chat
            .upsert_model_metadata(ModelMetadataArgs {
                metadata: json!({"pricingStatus":"MANUAL"}),
                ..metadata()
            })
            .unwrap_err()
            .contains("请求内容与首次提交不符"));
        assert!(chat.upsert_model_metadata(metadata()).unwrap().replayed);
        let undo = || HistoryArgs {
            intent_id: "schema46-undo-request".into(),
            workspace_id: workspace_id.clone(),
        };
        chat.apply_history(undo(), false).unwrap();
        assert!(chat.apply_history(undo(), false).unwrap().replayed);
        assert!(
            chat.workspace_projection(&workspace_id).unwrap().exchange["projects"]
                .as_array()
                .unwrap()
                .iter()
                .all(|item| item["id"] != original.object_id)
        );
        let work = work.unwrap();
        assert!(work.list_workspaces().unwrap().is_empty());
        drop(work);
        drop(chat);
        let (reopened, reopened_work) = crate::open_desktop_area_stores_for_startup(
            root,
            DesktopStartupMode::UiSchemaDiagnostic,
        )
        .unwrap();
        assert!(reopened.mutate_domain(new_args()).unwrap().replayed);
        assert!(reopened
            .mutate_domain(DomainMutationArgs {
                fields: json!({"title":"冷启后异字段","description":"合成"}),
                ..new_args()
            })
            .unwrap_err()
            .contains("请求内容与首次提交不符"));
        assert!(reopened.upsert_model_metadata(metadata()).unwrap().replayed);
        assert!(reopened.apply_history(undo(), false).unwrap().replayed);
        assert!(reopened_work.unwrap().list_workspaces().unwrap().is_empty());
    }

    #[test]
    fn startup_resumes_when_chat_upgraded_before_work() {
        let directory = tempdir().unwrap();
        let root = directory.path().join("app-data");
        fs::create_dir(&root).unwrap();
        let lease = RootMigrationLease::acquire(&root).unwrap();
        reconcile_schema45_at_startup_under_existing_lock(&root, &lease._file).unwrap();
        drop(lease);
        let chat_database = root.join("workspace.sqlite3");
        let work_database = root.join("work-area/workspace.sqlite3");
        crate::desktop_intent_request_schema::migrate_committed_area(
            &mut Connection::open(&chat_database).unwrap(),
            "CHAT",
        )
        .unwrap();
        assert_eq!(
            Connection::open(&work_database)
                .unwrap()
                .pragma_query_value(None, "user_version", |row| row.get::<_, u32>(0))
                .unwrap(),
            45
        );
        let lease = RootMigrationLease::acquire(&root).unwrap();
        reconcile_schema45_at_startup_under_existing_lock(&root, &lease._file).unwrap();
        drop(lease);
        let (chat, work) = crate::open_desktop_area_stores_for_startup(
            root,
            DesktopStartupMode::UiSchemaDiagnostic,
        )
        .unwrap();
        assert!(chat.list_workspaces().unwrap().is_empty());
        assert!(work.unwrap().list_workspaces().unwrap().is_empty());
        assert_eq!(
            Connection::open(&chat_database)
                .unwrap()
                .pragma_query_value(None, "user_version", |row| row.get::<_, u32>(0))
                .unwrap(),
            crate::desktop_schema_source::CURRENT_AREA_SCHEMA_VERSION
        );
        assert_eq!(
            Connection::open(&work_database)
                .unwrap()
                .pragma_query_value(None, "user_version", |row| row.get::<_, u32>(0))
                .unwrap(),
            crate::desktop_schema_source::CURRENT_AREA_SCHEMA_VERSION
        );
    }

    #[test]
    fn missing_source_with_existing_private_data_refuses_new_empty_database() {
        let directory = tempdir().unwrap();
        let root = directory.path().join("app-data");
        fs::create_dir(&root).unwrap();
        fs::write(root.join("user-data.txt"), b"keep").unwrap();
        let lease = RootMigrationLease::acquire(&root).unwrap();
        assert!(
            reconcile_schema45_at_startup_under_existing_lock(&root, &lease._file)
                .unwrap_err()
                .contains("已有其他内容")
        );
        assert!(!root.join("workspace.sqlite3").exists());
        assert_eq!(fs::read(root.join("user-data.txt")).unwrap(), b"keep");
    }

    #[test]
    fn active_wal_reader_blocks_source_checkpoint_without_publishing() {
        let directory = tempdir().unwrap();
        let database = directory.path().join("workspace.sqlite3");
        let writer = Connection::open(&database).unwrap();
        writer.execute_batch("PRAGMA journal_mode=WAL; CREATE TABLE probe (value TEXT NOT NULL); INSERT INTO probe VALUES ('before');").unwrap();
        let reader = Connection::open(&database).unwrap();
        reader.execute_batch("BEGIN").unwrap();
        let before: String = reader
            .query_row("SELECT value FROM probe", [], |row| row.get(0))
            .unwrap();
        assert_eq!(before, "before");
        writer
            .execute("INSERT INTO probe VALUES ('after')", [])
            .unwrap();
        assert!(checkpoint_legacy_source_under_existing_lock(&database)
            .unwrap_err()
            .contains("WAL"));
        assert!(database.with_file_name("workspace.sqlite3-wal").exists());
        reader.execute_batch("ROLLBACK").unwrap();
        let rows: i64 = writer
            .query_row("SELECT COUNT(*) FROM probe", [], |row| row.get(0))
            .unwrap();
        assert_eq!(rows, 2);
    }

    #[test]
    fn isolated_publish_installs_both_areas_and_preserves_original_database() {
        let directory = tempdir().unwrap();
        let root = directory.path().join("app-data");
        let source = mixed_synthetic_source(&root);
        let before = crate::sha256_file(&source).unwrap();
        publish_schema45_bundle(&source).unwrap();
        assert_eq!(
            crate::sha256_file(
                &root.join("data-area-migration-v1/original-before-install.sqlite3")
            )
            .unwrap(),
            before
        );
        verify_installed_area(&source, "CHAT").unwrap();
        verify_installed_area(&root.join("work-area/workspace.sqlite3"), "WORK").unwrap();
        assert!(root.join("app-configuration.sqlite3").is_file());
        assert!(!root
            .join("data-area-migration-v1/schema45-install.json")
            .exists());
    }

    #[test]
    fn published_work_area_missing_a_required_setting_refuses_to_open() {
        let directory = tempdir().unwrap();
        let root = directory.path().join("app-data");
        let source = mixed_synthetic_source(&root);
        publish_schema45_bundle(&source).unwrap();
        let work_path = root.join("work-area/workspace.sqlite3");
        let work = Connection::open(&work_path).unwrap();
        work.execute("DELETE FROM desktop_transcription_settings WHERE id=1", [])
            .unwrap();
        drop(work);
        let error = crate::DesktopWorkspaceStore::open_committed_schema45_area(
            root,
            DesktopDataArea::Work,
            DesktopStartupMode::UiSchemaDiagnostic,
        )
        .err()
        .expect("missing required settings must stop startup");
        assert!(error.contains("desktop_transcription_settings"));
    }

    #[test]
    fn published_area_stores_reopen_with_distinct_business_paths_and_shared_configuration() {
        let directory = tempdir().unwrap();
        let root = directory.path().join("app-data");
        let source = mixed_synthetic_source(&root);
        publish_schema45_bundle(&source).unwrap();

        let (chat, work) =
            crate::open_desktop_area_stores_for_startup(root.clone(), DesktopStartupMode::Normal)
                .unwrap();
        let work = work.expect("published root must open WORK");
        assert_eq!(chat.database, root.join("workspace.sqlite3"));
        assert_eq!(work.database, root.join("work-area/workspace.sqlite3"));
        assert_eq!(chat.configuration_database, work.configuration_database);
        assert_eq!(
            chat.configuration_database,
            Some(root.join("app-configuration.sqlite3"))
        );
        for (store, text, filename) in [
            (&chat, "CHAT 的同 ID 草稿", "chat-draft.txt"),
            (&work, "WORK 的同 ID 草稿", "work-draft.txt"),
        ] {
            let attachment = store
                .import_conversation_attachment_bytes(
                    "legacy",
                    text.as_bytes().to_vec(),
                    Some("txt"),
                    filename,
                )
                .unwrap();
            store
                .save_conversation_draft(DesktopConversationDraftArgs {
                    workspace_id: "legacy".into(),
                    conversation_id: None,
                    text: text.into(),
                    attachment_ids: vec![attachment.id],
                    model_id: None,
                    tone_override: None,
                    web_search_override: None,
                })
                .unwrap();
        }
        drop(chat);
        drop(work);
        let (chat, work) =
            crate::open_desktop_area_stores_for_startup(root, DesktopStartupMode::Normal).unwrap();
        let work = work.expect("published root must reopen WORK");
        assert_eq!(
            chat.read_conversation_draft("legacy", None)
                .unwrap()
                .unwrap()
                .text,
            "CHAT 的同 ID 草稿"
        );
        assert_eq!(
            chat.read_conversation_draft("legacy", None)
                .unwrap()
                .unwrap()
                .attachments[0]
                .display_name,
            "chat-draft.txt",
        );
        assert_eq!(
            work.read_conversation_draft("legacy", None)
                .unwrap()
                .unwrap()
                .text,
            "WORK 的同 ID 草稿"
        );
        assert_eq!(
            work.read_conversation_draft("legacy", None)
                .unwrap()
                .unwrap()
                .attachments[0]
                .display_name,
            "work-draft.txt",
        );
        chat.connection().unwrap().execute(
            "UPDATE desktop_conversation_drafts_v1 SET updated_at_ms=?3 WHERE workspace_id=?1 AND conversation_key=?2",
            rusqlite::params![
                "legacy", crate::NEW_CONVERSATION_DRAFT_KEY,
                crate::system_now_millis() - crate::NEW_CONVERSATION_DRAFT_TTL_MS - 1,
            ],
        ).unwrap();
        let chat_expired = chat
            .read_conversation_draft("legacy", None)
            .unwrap()
            .unwrap();
        assert_eq!(chat_expired.text, "");
        assert_eq!(chat_expired.attachments[0].display_name, "chat-draft.txt");
        assert_eq!(
            work.read_conversation_draft("legacy", None)
                .unwrap()
                .unwrap()
                .text,
            "WORK 的同 ID 草稿",
            "CHAT 同 ID 文字到期不得清除 WORK 草稿",
        );
        assert_eq!(
            work.read_conversation_draft("legacy", None)
                .unwrap()
                .unwrap()
                .attachments[0]
                .display_name,
            "work-draft.txt",
        );
    }

    #[test]
    fn full_local_delete_fails_closed_for_both_committed_areas_without_changing_either_database() {
        let directory = tempdir().unwrap();
        let root = directory.path().join("app-data");
        let source = mixed_synthetic_source(&root);
        publish_schema45_bundle(&source).unwrap();
        let (mut chat, work) =
            crate::open_desktop_area_stores_for_startup(root.clone(), DesktopStartupMode::Normal)
                .unwrap();
        let mut work = work.unwrap();
        let chat_before = crate::sha256_file(&chat.database).unwrap();
        let work_before = crate::sha256_file(&work.database).unwrap();
        let config_before = crate::sha256_file(&root.join("app-configuration.sqlite3")).unwrap();
        for store in [&mut chat, &mut work] {
            let error = store
                .preview_desktop_privacy_deletion(crate::DesktopPrivacyPreviewArgs {
                    scope: "ALL_LOCAL_BUSINESS_DATA".into(),
                    selected_task_ids: Default::default(),
                })
                .err()
                .expect("split owner must refuse unsafe preview");
            assert!(error.contains("双区"));
            let error = store
                .replace_all_local_business_data(crate::DesktopPrivacyDeleteArgs {
                    scope: "ALL_LOCAL_BUSINESS_DATA".into(),
                    preview_fingerprint: String::new(),
                    confirmation_phrase: "删除全部本地业务数据".into(),
                    selected_task_ids: Default::default(),
                })
                .err()
                .expect("split owner must refuse unsafe replacement");
            assert!(error.contains("双区"));
        }
        assert_eq!(crate::sha256_file(&chat.database).unwrap(), chat_before);
        assert_eq!(crate::sha256_file(&work.database).unwrap(), work_before);
        assert_eq!(
            crate::sha256_file(&root.join("app-configuration.sqlite3")).unwrap(),
            config_before
        );
        assert_eq!(chat.list_workspaces().unwrap().len(), 1);
        assert_eq!(work.list_workspaces().unwrap().len(), 1);
        assert!(root.join(".runtime-owner.lock").exists());
    }

    #[test]
    fn published_root_missing_work_area_refuses_empty_work_recreation() {
        let directory = tempdir().unwrap();
        let root = directory.path().join("app-data");
        let source = mixed_synthetic_source(&root);
        publish_schema45_bundle(&source).unwrap();
        let work_root = root.join("work-area");
        fs::remove_dir_all(&work_root).unwrap();

        assert!(DesktopWorkspaceStore::open_committed_schema45_area(
            root,
            DesktopDataArea::Work,
            DesktopStartupMode::Normal,
        )
        .is_err());
        assert!(!work_root.exists());
    }

    #[test]
    fn published_root_missing_shared_configuration_refuses_stale_recreation() {
        let directory = tempdir().unwrap();
        let root = directory.path().join("app-data");
        let source = mixed_synthetic_source(&root);
        publish_schema45_bundle(&source).unwrap();
        let configuration = root.join("app-configuration.sqlite3");
        fs::remove_file(&configuration).unwrap();

        assert!(DesktopWorkspaceStore::open_committed_schema45_area(
            root,
            DesktopDataArea::Chat,
            DesktopStartupMode::Normal,
        )
        .is_err());
        assert!(!configuration.exists());
    }

    #[test]
    fn published_root_empty_shared_configuration_refuses_stale_recreation() {
        let directory = tempdir().unwrap();
        let root = directory.path().join("app-data");
        let source = mixed_synthetic_source(&root);
        publish_schema45_bundle(&source).unwrap();
        let configuration = root.join("app-configuration.sqlite3");
        fs::write(&configuration, []).unwrap();

        assert!(DesktopWorkspaceStore::open_committed_schema45_area(
            root,
            DesktopDataArea::Chat,
            DesktopStartupMode::Normal,
        )
        .is_err());
        assert_eq!(fs::metadata(configuration).unwrap().len(), 0);
    }

    #[test]
    fn interrupted_install_before_chat_replace_resumes_without_losing_old_bytes() {
        let directory = tempdir().unwrap();
        let root = directory.path().join("app-data");
        let source = mixed_synthetic_source(&root);
        let before = crate::sha256_file(&source).unwrap();
        let lease = RootMigrationLease::acquire(&root).unwrap();
        assert!(
            publish_schema45_bundle_under_existing_lock(&source, &lease._file, || Err(
                "injected before CHAT replace".into()
            ))
            .is_err()
        );
        drop(lease);
        assert_eq!(crate::sha256_file(&source).unwrap(), before);
        assert!(root.join("work-area/workspace.sqlite3").is_file());
        assert!(root
            .join("data-area-migration-v1/schema45-install.json")
            .is_file());
        assert!(crate::open_desktop_area_stores_for_startup(
            root.clone(),
            DesktopStartupMode::Normal,
        )
        .is_err());
        let lease = RootMigrationLease::acquire(&root).unwrap();
        reconcile_schema45_at_startup_under_existing_lock(&root, &lease._file).unwrap();
        drop(lease);
        verify_installed_area(&source, "CHAT").unwrap();
        verify_installed_area(&root.join("work-area/workspace.sqlite3"), "WORK").unwrap();
    }

    #[test]
    fn interrupted_install_after_chat_replace_finishes_from_new_active_bytes() {
        let directory = tempdir().unwrap();
        let root = directory.path().join("app-data");
        let source = mixed_synthetic_source(&root);
        let lease = RootMigrationLease::acquire(&root).unwrap();
        assert!(
            publish_schema45_bundle_under_existing_lock(&source, &lease._file, || Err(
                "injected before CHAT replace".into()
            ))
            .is_err()
        );
        let recovery = root.join("data-area-migration-v1");
        replace_active_chat_database(&recovery.join("install-chat.sqlite3"), &source).unwrap();
        drop(lease);

        for area in [DesktopDataArea::Chat, DesktopDataArea::Work] {
            assert!(DesktopWorkspaceStore::open_committed_schema45_area(
                root.clone(),
                area,
                DesktopStartupMode::Normal,
            )
            .is_err());
        }

        let lease = RootMigrationLease::acquire(&root).unwrap();
        reconcile_schema45_at_startup_under_existing_lock(&root, &lease._file).unwrap();
        drop(lease);
        verify_installed_area(&source, "CHAT").unwrap();
        verify_installed_area(&root.join("work-area/workspace.sqlite3"), "WORK").unwrap();
        assert!(!recovery.join("schema45-install.json").exists());
    }

    #[test]
    fn changed_source_after_install_marker_blocks_resume_and_preserves_bytes() {
        let directory = tempdir().unwrap();
        let root = directory.path().join("app-data");
        let source = mixed_synthetic_source(&root);
        let lease = RootMigrationLease::acquire(&root).unwrap();
        assert!(
            publish_schema45_bundle_under_existing_lock(&source, &lease._file, || Err(
                "injected before CHAT replace".into()
            ))
            .is_err()
        );
        drop(lease);

        let connection = Connection::open(&source).unwrap();
        connection
            .execute_batch("CREATE TABLE source_changed_after_install_marker(value INTEGER);")
            .unwrap();
        drop(connection);
        let changed = crate::sha256_file(&source).unwrap();
        assert!(publish_schema45_bundle(&source).is_err());
        assert_eq!(crate::sha256_file(&source).unwrap(), changed);
        assert!(root
            .join("data-area-migration-v1/schema45-install.json")
            .is_file());
    }

    #[test]
    fn damaged_staged_bundle_blocks_resume_before_chat_replace() {
        let directory = tempdir().unwrap();
        let root = directory.path().join("app-data");
        let source = mixed_synthetic_source(&root);
        let before = crate::sha256_file(&source).unwrap();
        let lease = RootMigrationLease::acquire(&root).unwrap();
        assert!(
            publish_schema45_bundle_under_existing_lock(&source, &lease._file, || Err(
                "injected before CHAT replace".into()
            ))
            .is_err()
        );
        drop(lease);

        let staged_configuration =
            root.join("data-area-migration-v1/schema45-stage/app-configuration.sqlite3");
        fs::OpenOptions::new()
            .append(true)
            .open(staged_configuration)
            .unwrap()
            .write_all(b"damaged")
            .unwrap();
        assert!(publish_schema45_bundle(&source).is_err());
        assert_eq!(crate::sha256_file(&source).unwrap(), before);
        assert!(root
            .join("data-area-migration-v1/schema45-install.json")
            .is_file());
    }

    #[test]
    fn existing_work_area_blocks_install_without_replacing_its_content() {
        let directory = tempdir().unwrap();
        let root = directory.path().join("app-data");
        let source = mixed_synthetic_source(&root);
        let before = crate::sha256_file(&source).unwrap();
        let work = root.join("work-area");
        fs::create_dir(&work).unwrap();
        fs::write(work.join("user-content.txt"), b"keep this").unwrap();
        assert!(publish_schema45_bundle(&source).is_err());
        assert_eq!(crate::sha256_file(&source).unwrap(), before);
        assert_eq!(
            fs::read(work.join("user-content.txt")).unwrap(),
            b"keep this"
        );
    }

    #[test]
    fn nonempty_source_wal_blocks_install_without_discarding_pending_bytes() {
        let directory = tempdir().unwrap();
        let root = directory.path().join("app-data");
        let source = mixed_synthetic_source(&root);
        let connection = Connection::open(&source).unwrap();
        connection
            .execute_batch(
                "PRAGMA journal_mode=WAL; PRAGMA wal_autocheckpoint=0;
                 CREATE TABLE pending_source_wal(value INTEGER);",
            )
            .unwrap();
        let wal = wal_path(&source);
        assert!(fs::metadata(&wal).unwrap().len() > 0);
        let before = source_fingerprint(&source).unwrap();
        assert!(publish_schema45_bundle(&source).is_err());
        assert_eq!(source_fingerprint(&source).unwrap(), before);
        assert!(!root
            .join("data-area-migration-v1/schema45-install.json")
            .exists());
        drop(connection);
    }

    #[test]
    fn four_known_branches_build_separate_schema45_candidates_without_rewriting_sources() {
        for branch in [
            "legacy42",
            "upstream43",
            "upstream43done",
            "upstream43work",
            "windows43",
            "windows44",
        ] {
            let directory = tempdir().unwrap();
            let root = directory.path().join(branch);
            let store = DesktopWorkspaceStore::open(root.clone()).unwrap();
            store.ensure_local_workspace().unwrap();
            let connection = store.connection().unwrap();
            let workspace_id: String = connection
                .query_row("SELECT id FROM workspaces LIMIT 1", [], |row| row.get(0))
                .unwrap();
            connection.execute("INSERT INTO desktop_conversation_drafts_v1(workspace_id,conversation_key,text,updated_at_ms,model_id,tone_override,web_search_override) VALUES(?1,'new','保留正文',123,'deepseek-v4-pro','直言不讳',0)", [workspace_id]).unwrap();
            match branch {
                "legacy42" | "upstream43" | "upstream43done" | "upstream43work" => connection.execute_batch("ALTER TABLE desktop_conversation_drafts_v1 DROP COLUMN model_id; ALTER TABLE desktop_conversation_drafts_v1 DROP COLUMN tone_override; ALTER TABLE desktop_conversation_drafts_v1 DROP COLUMN web_search_override;").unwrap(),
                "windows43" => connection.execute_batch("ALTER TABLE desktop_conversation_drafts_v1 DROP COLUMN tone_override; ALTER TABLE desktop_conversation_drafts_v1 DROP COLUMN web_search_override;").unwrap(),
                _ => (),
            }
            if matches!(branch, "upstream43" | "upstream43done" | "upstream43work") {
                let (area, split_done) = if branch == "upstream43work" {
                    ("WORK", 1)
                } else if branch == "upstream43done" {
                    ("CHAT", 1)
                } else {
                    ("CHAT", 0)
                };
                connection.execute_batch("CREATE TABLE desktop_data_area_v1(id INTEGER PRIMARY KEY CHECK(id=1), area TEXT NOT NULL, split_done INTEGER NOT NULL DEFAULT 0);").unwrap();
                connection
                    .execute(
                        "INSERT INTO desktop_data_area_v1 VALUES(1,?1,?2)",
                        (area, split_done),
                    )
                    .unwrap();
            }
            let source_version = match branch {
                "legacy42" => 42,
                "windows44" => 44,
                _ => 43,
            };
            connection
                .pragma_update(None, "user_version", source_version)
                .unwrap();
            drop(connection);
            drop(store);
            let source = root.join("workspace.sqlite3");
            let candidate = prepare_schema45_candidate(&source).unwrap();
            let original =
                Connection::open_with_flags(&source, OpenFlags::SQLITE_OPEN_READ_ONLY).unwrap();
            let original_version: u32 = original
                .pragma_query_value(None, "user_version", |row| row.get(0))
                .unwrap();
            assert_eq!(original_version, source_version, "{branch}");
            let migrated =
                Connection::open_with_flags(candidate, OpenFlags::SQLITE_OPEN_READ_ONLY).unwrap();
            let migrated_version: u32 = migrated
                .pragma_query_value(None, "user_version", |row| row.get(0))
                .unwrap();
            assert_eq!(migrated_version, 45, "{branch}");
            let record: (String, Option<String>, Option<String>, Option<i64>) = migrated.query_row("SELECT text,model_id,tone_override,web_search_override FROM desktop_conversation_drafts_v1 WHERE conversation_key='new'", [], |row| Ok((row.get(0)?,row.get(1)?,row.get(2)?,row.get(3)?))).unwrap();
            assert_eq!(record.0, "保留正文", "{branch}");
            assert_eq!(
                record.1.as_deref(),
                if matches!(branch, "windows43" | "windows44") {
                    Some("deepseek-v4-pro")
                } else {
                    None
                },
                "{branch}"
            );
            assert_eq!(
                record.2.as_deref(),
                if branch == "windows44" {
                    Some("直言不讳")
                } else {
                    None
                },
                "{branch}"
            );
            assert_eq!(
                record.3,
                if branch == "windows44" { Some(0) } else { None },
                "{branch}"
            );
            let area: (String, i64) = migrated
                .query_row(
                    "SELECT area,split_done FROM desktop_data_area_v1 WHERE id=1",
                    [],
                    |row| Ok((row.get(0)?, row.get(1)?)),
                )
                .unwrap();
            assert_eq!(
                area,
                if branch == "upstream43work" {
                    ("WORK".into(), 1)
                } else if branch == "upstream43done" {
                    ("CHAT".into(), 1)
                } else {
                    ("CHAT".into(), 0)
                },
                "{branch}"
            );
            if matches!(branch, "upstream43work" | "upstream43done") {
                assert!(prepare_schema45_split_bundle(&source)
                    .unwrap_err()
                    .contains("独立双区来源"));
                assert!(!root.join("data-area-migration-v1/schema45-stage").exists());
            }
        }
    }

    #[test]
    fn unknown_source_or_existing_candidate_never_overwrites_a_database() {
        let directory = tempdir().unwrap();
        let root = directory.path().join("app-data");
        let store = DesktopWorkspaceStore::open(root.clone()).unwrap();
        store
            .connection()
            .unwrap()
            .execute_batch("ALTER TABLE desktop_conversation_drafts_v1 DROP COLUMN model_id;")
            .unwrap();
        drop(store);
        let source = root.join("workspace.sqlite3");
        let target = root.join("data-area-migration-v1/schema45-candidate.sqlite3");
        assert!(prepare_schema45_candidate(&source).is_err());
        assert!(!target.exists());
        let connection = Connection::open(&source).unwrap();
        connection
            .execute_batch("ALTER TABLE desktop_conversation_drafts_v1 ADD COLUMN model_id TEXT;")
            .unwrap();
        drop(connection);
        let candidate = prepare_schema45_candidate(&source).unwrap();
        let before = fs::read(&candidate).unwrap();
        assert!(prepare_schema45_candidate(&source).is_err());
        assert!(prepare_schema45_split_bundle(&source)
            .unwrap_err()
            .contains("旧候选缺少来源标记"));
        assert_eq!(fs::read(candidate).unwrap(), before);
    }

    #[test]
    fn existing_work_directory_is_preserved_before_bundle_creation() {
        let directory = tempdir().unwrap();
        let root = directory.path().join("app-data");
        let store = DesktopWorkspaceStore::open(root.clone()).unwrap();
        drop(store);
        let work = root.join("work-area");
        fs::create_dir(&work).unwrap();
        fs::write(work.join("independent-work-record"), b"keep").unwrap();
        let source = root.join("workspace.sqlite3");
        assert!(prepare_schema45_split_bundle(&source)
            .unwrap_err()
            .contains("已有 WORK 目录"));
        assert_eq!(
            fs::read(work.join("independent-work-record")).unwrap(),
            b"keep"
        );
        assert!(!root
            .join("data-area-migration-v1/schema45-candidate.sqlite3")
            .exists());
    }

    #[test]
    fn occupied_runtime_root_is_rejected_before_candidate_creation() {
        let directory = tempdir().unwrap();
        let root = directory.path().join("app-data");
        let store = DesktopWorkspaceStore::open(root.clone()).unwrap();
        drop(store);
        let lock = fs::OpenOptions::new()
            .read(true)
            .write(true)
            .create_new(true)
            .open(root.join(".runtime-owner.lock"))
            .unwrap();
        lock.try_lock_exclusive().unwrap();
        let source = root.join("workspace.sqlite3");
        assert!(prepare_schema45_split_bundle(&source)
            .unwrap_err()
            .contains("进程占用"));
        assert!(!root
            .join("data-area-migration-v1/schema45-candidate.sqlite3")
            .exists());
        drop(lock);
        assert!(prepare_schema45_split_bundle(&source).is_ok());
    }

    #[test]
    fn bundle_resume_rejects_changed_candidate_without_touching_source() {
        let directory = tempdir().unwrap();
        let root = directory.path().join("app-data");
        let store = DesktopWorkspaceStore::open(root.clone()).unwrap();
        drop(store);
        let source = root.join("workspace.sqlite3");
        let source_hash = crate::sha256_file(&source).unwrap();
        let stage = prepare_schema45_split_bundle(&source).unwrap();
        let candidate = root.join("data-area-migration-v1/schema45-candidate.sqlite3");
        fs::write(&candidate, b"altered candidate").unwrap();
        assert!(prepare_schema45_split_bundle(&source)
            .unwrap_err()
            .contains("候选与暂存清单不符"));
        assert_eq!(crate::sha256_file(&source).unwrap(), source_hash);
        assert!(stage.join("workspace.sqlite3").is_file());
    }

    #[test]
    fn orphan_candidate_with_matching_source_marker_is_preserved_and_rebuilt() {
        let directory = tempdir().unwrap();
        let root = directory.path().join("app-data");
        let store = DesktopWorkspaceStore::open(root.clone()).unwrap();
        drop(store);
        let source = root.join("workspace.sqlite3");
        let recovery = root.join("data-area-migration-v1");
        fs::create_dir(&recovery).unwrap();
        let (source_database_sha256, source_wal_sha256) = source_fingerprint(&source).unwrap();
        write_atomic_new_file(
            &recovery.join("schema45-source.json"),
            &serde_json::to_vec(&Schema45SourceMarker {
                version: 1,
                source_database_sha256,
                source_wal_sha256,
            })
            .unwrap(),
        )
        .unwrap();
        let orphan = prepare_schema45_candidate(&source).unwrap();
        let orphan_hash = crate::sha256_file(&orphan).unwrap();
        fs::write(
            recovery.join("schema45-bundle.json.nfai-writing"),
            b"incomplete manifest",
        )
        .unwrap();
        let stage = prepare_schema45_split_bundle(&source).unwrap();
        let manifest = read_bundle_manifest(&recovery.join("schema45-bundle.json")).unwrap();
        assert_ne!(manifest.candidate_file, "schema45-candidate.sqlite3");
        assert_eq!(crate::sha256_file(&orphan).unwrap(), orphan_hash);
        assert!(!recovery.join("schema45-bundle.json.nfai-writing").exists());
        assert!(stage.join("workspace.sqlite3").is_file());
        assert!(stage.join("work-area/workspace.sqlite3").is_file());
    }

    #[test]
    fn orphan_candidate_is_not_reused_after_source_changes() {
        let directory = tempdir().unwrap();
        let root = directory.path().join("app-data");
        let store = DesktopWorkspaceStore::open(root.clone()).unwrap();
        store.ensure_local_workspace().unwrap();
        let source = root.join("workspace.sqlite3");
        let recovery = root.join("data-area-migration-v1");
        fs::create_dir(&recovery).unwrap();
        let (source_database_sha256, source_wal_sha256) = source_fingerprint(&source).unwrap();
        write_atomic_new_file(
            &recovery.join("schema45-source.json"),
            &serde_json::to_vec(&Schema45SourceMarker {
                version: 1,
                source_database_sha256,
                source_wal_sha256,
            })
            .unwrap(),
        )
        .unwrap();
        let orphan = prepare_schema45_candidate(&source).unwrap();
        let orphan_hash = crate::sha256_file(&orphan).unwrap();
        store
            .connection()
            .unwrap()
            .execute("UPDATE workspaces SET title='changed source'", [])
            .unwrap();
        assert!(prepare_schema45_split_bundle(&source)
            .unwrap_err()
            .contains("源库已变化"));
        assert_eq!(crate::sha256_file(&orphan).unwrap(), orphan_hash);
        assert!(!recovery.join("schema45-bundle.json").exists());
    }

    #[test]
    fn partial_source_marker_write_is_retried_before_candidate_creation() {
        let directory = tempdir().unwrap();
        let root = directory.path().join("app-data");
        let store = DesktopWorkspaceStore::open(root.clone()).unwrap();
        drop(store);
        let recovery = root.join("data-area-migration-v1");
        fs::create_dir(&recovery).unwrap();
        let partial = recovery.join("schema45-source.json.nfai-writing");
        fs::write(&partial, b"partial marker").unwrap();
        let source = root.join("workspace.sqlite3");
        let stage = prepare_schema45_split_bundle(&source).unwrap();
        assert!(!partial.exists());
        assert!(recovery.join("schema45-source.json").is_file());
        assert!(stage.join("work-area/workspace.sqlite3").is_file());
    }

    #[test]
    fn extra_source_hard_link_is_rejected_before_creating_a_candidate() {
        let directory = tempdir().unwrap();
        let root = directory.path().join("app-data");
        let store = DesktopWorkspaceStore::open(root.clone()).unwrap();
        drop(store);
        let source = root.join("workspace.sqlite3");
        fs::hard_link(&source, root.join("workspace-alias.sqlite3")).unwrap();
        let error = prepare_schema45_candidate(&source).unwrap_err();
        assert!(error.contains("硬链接"), "{error}");
        assert!(!root
            .join("data-area-migration-v1/schema45-candidate.sqlite3")
            .exists());
    }
}
