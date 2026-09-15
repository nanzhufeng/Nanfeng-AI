//! Private P6 workspace-exchange v2 kernel.
//!
//! This module deliberately has no Tauri command or picker binding.  It owns only the strict
//! package reader, app-private archive, SQLite owner tables and readback/re-export contract.

use crate::{canonical_json, json_error, safe_entry, sha256, validate_exchange_v2_ir};
use rusqlite::{params, Connection, OptionalExtension, Transaction, TransactionBehavior};
use serde_json::{json, Map, Value};
use std::{
    collections::{BTreeMap, BTreeSet},
    fs,
    io::{Cursor, Read, Write},
    path::{Path, PathBuf},
};
use zip::{write::SimpleFileOptions, CompressionMethod, ZipArchive, ZipWriter};

const MAX_PACKAGE_BYTES: u64 = 128 * 1024 * 1024;
const MAX_ENTRIES: usize = 100_000;

#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct V2PreflightReceipt {
    pub package_hash: String,
    pub semantic_hash: String,
    pub origin: String,
    pub sensitivity: String,
    pub root_counts: BTreeMap<String, u64>,
    pub asset_count: u64,
    pub asset_bytes: u64,
    pub owner_field_hashes: BTreeMap<String, String>,
}

#[derive(Clone, Debug)]
pub(crate) struct V2PreflightedPackage {
    pub bytes: Vec<u8>,
    pub exchange: Value,
    pub assets: BTreeMap<String, Vec<u8>>,
    pub receipt: V2PreflightReceipt,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct V2CommitReceipt {
    pub workspace_id: String,
    pub package_hash: String,
    pub semantic_hash: String,
    pub owner_field_hashes: BTreeMap<String, String>,
    pub replayed: bool,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum FailurePoint {
    Begin,
    Import,
    Asset,
    Provenance,
    Journal,
    Receipt,
    BeforeCommit,
}

fn err(message: &str) -> String {
    json_error(message)
}

fn exact_keys(value: &Map<String, Value>, expected: &[&str], where_: &str) -> Result<(), String> {
    let actual: BTreeSet<&str> = value.keys().map(String::as_str).collect();
    let expected: BTreeSet<&str> = expected.iter().copied().collect();
    (actual == expected)
        .then_some(())
        .ok_or_else(|| err(&format!("v2 {where_} 字段无效")))
}

fn object<'a>(value: &'a Value, where_: &str) -> Result<&'a Map<String, Value>, String> {
    value
        .as_object()
        .ok_or_else(|| err(&format!("v2 {where_} 必须是对象")))
}

fn array<'a>(value: &'a Map<String, Value>, key: &str) -> Result<&'a Vec<Value>, String> {
    value
        .get(key)
        .and_then(Value::as_array)
        .ok_or_else(|| err(&format!("v2 {key} 必须是数组")))
}

fn string<'a>(value: &'a Map<String, Value>, key: &str) -> Result<&'a str, String> {
    value
        .get(key)
        .and_then(Value::as_str)
        .ok_or_else(|| err(&format!("v2 {key} 缺失或不是文本")))
}

fn is_hash(value: &str) -> bool {
    value.len() == 64
        && value
            .bytes()
            .all(|byte| byte.is_ascii_hexdigit() && !byte.is_ascii_uppercase())
}

fn is_stable_id(value: &str) -> bool {
    let bytes = value.as_bytes();
    (2..=64).contains(&bytes.len())
        && (bytes[0].is_ascii_lowercase() || bytes[0].is_ascii_digit())
        && bytes.iter().all(|byte| {
            byte.is_ascii_lowercase() || byte.is_ascii_digit() || matches!(*byte, b'_' | b'-')
        })
}

fn asset_metadata(value: &Value) -> Result<(String, Value), String> {
    let asset = object(value, "attachment")?;
    exact_keys(
        asset,
        &[
            "id",
            "entry",
            "mimeType",
            "displayName",
            "byteCount",
            "sha256",
            "classification",
        ],
        "attachment",
    )?;
    let id = string(asset, "id")?;
    let entry = string(asset, "entry")?;
    let hash = string(asset, "sha256")?;
    if !is_stable_id(id)
        || !is_hash(hash)
        || entry != format!("assets/{hash}")
        || asset.get("byteCount").and_then(Value::as_u64).is_none()
        || !matches!(asset.get("mimeType").and_then(Value::as_str), Some(value) if !value.is_empty() && value.len() <= 127)
        || !matches!(asset.get("displayName").and_then(Value::as_str), Some(value) if !value.is_empty() && value.chars().count() <= 240)
        || !matches!(
            asset.get("classification").and_then(Value::as_str),
            Some("NORMAL" | "HIGH_SENSITIVE")
        )
    {
        return Err(err("v2 attachment 元数据无效"));
    }
    Ok((id.to_owned(), value.clone()))
}

fn collect_asset_ledger(exchange: &Value) -> Result<BTreeMap<String, Value>, String> {
    let root = object(exchange, "exchange")?;
    let mut by_id = BTreeMap::<String, Value>::new();
    let mut record = |value: &Value| -> Result<(), String> {
        let (id, metadata) = asset_metadata(value)?;
        if let Some(existing) = by_id.get(&id) {
            if existing != &metadata {
                return Err(err("v2 attachment ID 元数据不一致"));
            }
        } else {
            by_id.insert(id, metadata);
        }
        Ok(())
    };
    for conversation in array(root, "conversations")? {
        let conversation = object(conversation, "conversation")?;
        for message in array(conversation, "messages")? {
            let message = object(message, "message")?;
            for block in array(message, "blocks")? {
                let block = object(block, "message block")?;
                if block.get("kind").and_then(Value::as_str) == Some("ASSET_REF") {
                    exact_keys(block, &["kind", "ordinal", "asset"], "ASSET_REF block")?;
                    record(
                        block
                            .get("asset")
                            .ok_or_else(|| err("v2 ASSET_REF 缺 asset"))?,
                    )?;
                }
            }
        }
    }
    for knowledge in array(root, "knowledge")? {
        let knowledge = object(knowledge, "knowledge")?;
        for attachment in array(knowledge, "attachments")? {
            record(attachment)?;
        }
    }
    Ok(by_id)
}

fn manifest_files(entries: &BTreeMap<String, Vec<u8>>) -> Vec<Value> {
    entries.iter().map(|(path, bytes)| json!({"path": path, "byteCount": bytes.len(), "sha256": sha256(bytes)})).collect()
}

/// Strictly packages only canonical v2 IR and its ledger-accounted assets.
pub(crate) fn package(
    exchange: &Value,
    assets: &BTreeMap<String, Vec<u8>>,
) -> Result<Vec<u8>, String> {
    validate_exchange_v2_ir(exchange)?;
    let ledger = collect_asset_ledger(exchange)?;
    let expected_assets: BTreeSet<String> = ledger
        .values()
        .map(|value| {
            string(
                object(value, "attachment").expect("verified attachment"),
                "entry",
            )
            .expect("verified entry")
            .to_owned()
        })
        .collect();
    if assets.keys().cloned().collect::<BTreeSet<_>>() != expected_assets {
        return Err(err("v2 package asset 账本不完整"));
    }
    for metadata in ledger.values() {
        let metadata = object(metadata, "attachment")?;
        let entry = string(metadata, "entry")?;
        let bytes = assets
            .get(entry)
            .ok_or_else(|| err("v2 package asset 缺失"))?;
        if bytes.len() as u64
            != metadata
                .get("byteCount")
                .and_then(Value::as_u64)
                .unwrap_or(u64::MAX)
            || sha256(bytes) != string(metadata, "sha256")?
        {
            return Err(err("v2 package asset hash 不符"));
        }
    }
    let mut entries = BTreeMap::new();
    entries.insert(
        "exchange.json".to_owned(),
        canonical_json(exchange)?.into_bytes(),
    );
    entries.extend(
        assets
            .iter()
            .map(|(path, bytes)| (path.clone(), bytes.clone())),
    );
    let root = object(exchange, "exchange")?;
    let manifest = json!({"format":"nfai.exchange.package","packageVersion":2,"exchangeVersion":2,"export":root.get("export").ok_or_else(|| err("v2 export 缺失"))?,"files":manifest_files(&entries)});
    let options = SimpleFileOptions::default()
        .compression_method(CompressionMethod::Deflated)
        .compression_level(Some(9));
    let mut writer = ZipWriter::new(Cursor::new(Vec::new()));
    writer
        .start_file("manifest.json", options)
        .map_err(|_| err("无法创建 v2 manifest"))?;
    writer
        .write_all(canonical_json(&manifest)?.as_bytes())
        .map_err(|_| err("无法写入 v2 manifest"))?;
    for (path, bytes) in entries {
        writer
            .start_file(path, options)
            .map_err(|_| err("无法创建 v2 package entry"))?;
        writer
            .write_all(&bytes)
            .map_err(|_| err("无法写入 v2 package entry"))?;
    }
    Ok(writer
        .finish()
        .map_err(|_| err("无法完成 v2 ZIP"))?
        .into_inner())
}

/// Pure package reader: it performs no database or filesystem writes.
pub(crate) fn preflight(bytes: Vec<u8>) -> Result<V2PreflightedPackage, String> {
    if bytes.is_empty() || bytes.len() as u64 > MAX_PACKAGE_BYTES {
        return Err(err("v2 package 为空或超过 128 MiB 限制"));
    }
    let package_hash = sha256(&bytes);
    let mut zip = ZipArchive::new(Cursor::new(&bytes)).map_err(|_| err("v2 ZIP 包无效"))?;
    if zip.is_empty() || zip.len() > MAX_ENTRIES {
        return Err(err("v2 ZIP entry 数量不安全"));
    }
    let mut entries = BTreeMap::<String, Vec<u8>>::new();
    let mut total = 0u64;
    for index in 0..zip.len() {
        let mut entry = zip.by_index(index).map_err(|_| err("v2 ZIP entry 无效"))?;
        let name = entry.name().to_owned();
        if !safe_entry(&name)
            || !entry.is_file()
            || entries.contains_key(&name)
            || entry.size() > MAX_PACKAGE_BYTES
        {
            return Err(err("v2 ZIP 包含不安全或重复 entry"));
        }
        total = total
            .checked_add(entry.size())
            .ok_or_else(|| err("v2 ZIP size overflow"))?;
        if total > MAX_PACKAGE_BYTES {
            return Err(err("v2 ZIP 解压总量超过限制"));
        }
        let mut content = Vec::with_capacity(entry.size() as usize);
        entry
            .read_to_end(&mut content)
            .map_err(|_| err("v2 ZIP entry 读取失败"))?;
        if content.len() as u64 != entry.size() {
            return Err(err("v2 ZIP entry size 不符"));
        }
        entries.insert(name, content);
    }
    let manifest: Value = serde_json::from_slice(
        entries
            .get("manifest.json")
            .ok_or_else(|| err("v2 缺少 manifest"))?,
    )
    .map_err(|_| err("v2 manifest JSON 无效"))?;
    let manifest = object(&manifest, "manifest")?;
    exact_keys(
        manifest,
        &[
            "format",
            "packageVersion",
            "exchangeVersion",
            "export",
            "files",
        ],
        "manifest",
    )?;
    if manifest.get("format").and_then(Value::as_str) != Some("nfai.exchange.package")
        || manifest.get("packageVersion").and_then(Value::as_u64) != Some(2)
        || manifest.get("exchangeVersion").and_then(Value::as_u64) != Some(2)
    {
        return Err(err("v2 manifest 版本不支持"));
    }
    let files = array(manifest, "files")?;
    if files.len() + 1 != entries.len() {
        return Err(err("v2 package 包含未知或未列 entry"));
    }
    let mut listed = BTreeSet::new();
    for file in files {
        let file = object(file, "manifest file")?;
        exact_keys(file, &["path", "byteCount", "sha256"], "manifest file")?;
        let path = string(file, "path")?;
        let content = entries
            .get(path)
            .ok_or_else(|| err("v2 manifest entry 不存在"))?;
        if path == "manifest.json"
            || !listed.insert(path.to_owned())
            || file.get("byteCount").and_then(Value::as_u64) != Some(content.len() as u64)
            || string(file, "sha256")? != sha256(content)
        {
            return Err(err("v2 manifest hash、大小或清单不符"));
        }
    }
    if listed
        != entries
            .keys()
            .filter(|key| key.as_str() != "manifest.json")
            .cloned()
            .collect()
    {
        return Err(err("v2 manifest entry 集合不一致"));
    }
    let exchange: Value = serde_json::from_slice(
        entries
            .get("exchange.json")
            .ok_or_else(|| err("v2 缺少 exchange.json"))?,
    )
    .map_err(|_| err("v2 exchange JSON 无效"))?;
    let semantic_hash = validate_exchange_v2_ir(&exchange)?;
    let root = object(&exchange, "exchange")?;
    let export = root.get("export").ok_or_else(|| err("v2 export 缺失"))?;
    if canonical_json(
        manifest
            .get("export")
            .ok_or_else(|| err("v2 manifest export 缺失"))?,
    )? != canonical_json(export)?
    {
        return Err(err("v2 manifest export 与 exchange 不一致"));
    }
    let ledger = collect_asset_ledger(&exchange)?;
    let mut assets = BTreeMap::new();
    let mut asset_bytes = 0u64;
    let mut expected_entries = BTreeSet::new();
    for metadata in ledger.values() {
        let metadata = object(metadata, "attachment")?;
        let entry = string(metadata, "entry")?;
        let hash = string(metadata, "sha256")?;
        let content = entries
            .get(entry)
            .ok_or_else(|| err("v2 attachment 引用的 asset 不存在"))?;
        if content.len() as u64
            != metadata
                .get("byteCount")
                .and_then(Value::as_u64)
                .unwrap_or(u64::MAX)
            || sha256(content) != hash
        {
            return Err(err("v2 attachment asset hash 或大小不符"));
        }
        if expected_entries.insert(entry.to_owned()) {
            asset_bytes = asset_bytes
                .checked_add(content.len() as u64)
                .ok_or_else(|| err("v2 asset size overflow"))?;
        }
        assets.insert(entry.to_owned(), content.clone());
    }
    let actual_assets: BTreeSet<String> = entries
        .keys()
        .filter(|key| key.starts_with("assets/"))
        .cloned()
        .collect();
    if actual_assets != expected_entries
        || entries.keys().any(|key| {
            key != "manifest.json" && key != "exchange.json" && !key.starts_with("assets/")
        })
    {
        return Err(err("v2 asset 账本与 package entry 不一致"));
    }
    let mut owner_field_hashes = BTreeMap::new();
    for (kind, group) in [
        ("project", "projects"),
        ("conversation", "conversations"),
        ("knowledge", "knowledge"),
        ("memory", "memory"),
        ("relation", "relations"),
    ] {
        for owner in array(root, group)? {
            let owner = object(owner, kind)?;
            owner_field_hashes.insert(
                format!("{kind}/{}", string(owner, "id")?),
                sha256(canonical_json(&Value::Object(owner.clone()))?.as_bytes()),
            );
        }
    }
    owner_field_hashes.insert(
        "settings/root".to_owned(),
        sha256(
            canonical_json(
                root.get("settings")
                    .ok_or_else(|| err("v2 settings 缺失"))?,
            )?
            .as_bytes(),
        ),
    );
    for metadata in ledger.values() {
        let metadata = object(metadata, "attachment")?;
        owner_field_hashes.insert(
            format!("asset/{}", string(metadata, "sha256")?),
            string(metadata, "sha256")?.to_owned(),
        );
    }
    let mut root_counts = BTreeMap::new();
    for group in [
        "projects",
        "conversations",
        "knowledge",
        "memory",
        "relations",
    ] {
        root_counts.insert(group.to_owned(), array(root, group)?.len() as u64);
    }
    let origin = object(export, "export")?
        .get("origin")
        .and_then(Value::as_object)
        .and_then(|origin| origin.get("platform"))
        .and_then(Value::as_str)
        .ok_or_else(|| err("v2 origin 无效"))?
        .to_owned();
    let sensitivity = object(export, "export")?
        .get("sensitivity")
        .and_then(Value::as_str)
        .ok_or_else(|| err("v2 sensitivity 无效"))?
        .to_owned();
    Ok(V2PreflightedPackage {
        bytes,
        exchange,
        assets,
        receipt: V2PreflightReceipt {
            package_hash,
            semantic_hash,
            origin,
            sensitivity,
            root_counts,
            asset_count: expected_entries.len() as u64,
            asset_bytes,
            owner_field_hashes,
        },
    })
}

fn archive_root(root: &Path, package_hash: &str) -> PathBuf {
    root.join("exchange-v2").join("archives").join(package_hash)
}

fn write_synced(path: &Path, bytes: &[u8]) -> Result<(), String> {
    let mut file = fs::File::create(path).map_err(|_| err("v2 private archive 写入失败"))?;
    file.write_all(bytes)
        .map_err(|_| err("v2 private archive 写入失败"))?;
    file.sync_all()
        .map_err(|_| err("v2 private archive fsync 失败"))
}

fn verify_archive(root: &Path, expected: &V2PreflightedPackage) -> Result<(), String> {
    let archive = archive_root(root, &expected.receipt.package_hash);
    let package = fs::read(archive.join("package.nfai-exchange"))
        .map_err(|_| err("v2 private package 缺失"))?;
    let readback = preflight(package)?;
    if readback.receipt != expected.receipt {
        return Err(err("v2 private package readback 不一致"));
    }
    for (entry, bytes) in &expected.assets {
        let hash = entry
            .strip_prefix("assets/")
            .ok_or_else(|| err("v2 asset entry 无效"))?;
        let archived = fs::read(archive.join("assets").join(hash))
            .map_err(|_| err("v2 private asset 缺失"))?;
        if archived != *bytes || sha256(&archived) != hash {
            return Err(err("v2 private asset readback 不一致"));
        }
    }
    Ok(())
}

fn prepare_archive(root: &Path, package: &V2PreflightedPackage) -> Result<(), String> {
    let final_dir = archive_root(root, &package.receipt.package_hash);
    if final_dir.exists() {
        return verify_archive(root, package);
    }
    let parent = final_dir.parent().ok_or_else(|| err("v2 archive 根无效"))?;
    fs::create_dir_all(parent).map_err(|_| err("无法创建 v2 private archive 根"))?;
    let mut nonce = [0u8; 16];
    getrandom::fill(&mut nonce).map_err(|_| err("无法生成 v2 private prepare 随机名"))?;
    let prepare = parent.join(format!(
        ".prepare-{}-{}",
        package.receipt.package_hash,
        hex::encode(nonce)
    ));
    fs::create_dir(&prepare).map_err(|_| err("无法创建 v2 private prepare"))?;
    let result = (|| -> Result<(), String> {
        write_synced(&prepare.join("package.nfai-exchange"), &package.bytes)?;
        let assets = prepare.join("assets");
        fs::create_dir(&assets).map_err(|_| err("无法创建 v2 private asset 根"))?;
        for (entry, bytes) in &package.assets {
            let hash = entry
                .strip_prefix("assets/")
                .ok_or_else(|| err("v2 asset entry 无效"))?;
            write_synced(&assets.join(hash), bytes)?;
            if sha256(&fs::read(assets.join(hash)).map_err(|_| err("v2 private asset 回读失败"))?)
                != hash
            {
                return Err(err("v2 private asset hash 回读不符"));
            }
        }
        fs::rename(&prepare, &final_dir).map_err(|_| err("无法原子完成 v2 private archive"))?;
        verify_archive(root, package)
    })();
    if result.is_err() && prepare.exists() {
        let _ = fs::remove_dir_all(&prepare);
    }
    result
}

pub(crate) fn migrate(transaction: &Transaction<'_>) -> Result<(), String> {
    transaction.execute_batch("CREATE TABLE exchange_v2_imports (workspace_id TEXT PRIMARY KEY NOT NULL, package_hash TEXT UNIQUE NOT NULL, semantic_hash TEXT NOT NULL, exchange_json TEXT NOT NULL, origin_platform TEXT NOT NULL, sensitivity TEXT NOT NULL, created_at TEXT NOT NULL); CREATE TABLE exchange_v2_assets (workspace_id TEXT NOT NULL, sha256 TEXT NOT NULL, byte_count INTEGER NOT NULL, PRIMARY KEY(workspace_id,sha256)); CREATE TABLE exchange_v2_owner_provenance (workspace_id TEXT NOT NULL, owner_kind TEXT NOT NULL, owner_id TEXT NOT NULL, origin_semantic_hash TEXT NOT NULL, field_hash TEXT NOT NULL, imported_revision INTEGER NOT NULL, PRIMARY KEY(workspace_id,owner_kind,owner_id)); CREATE TABLE exchange_v2_import_journal (package_hash TEXT PRIMARY KEY NOT NULL, workspace_id TEXT NOT NULL, semantic_hash TEXT NOT NULL, receipt_json_hash TEXT NOT NULL, committed_at TEXT NOT NULL); CREATE TABLE exchange_v2_import_receipts (package_hash TEXT PRIMARY KEY NOT NULL, semantic_hash TEXT NOT NULL, owner_field_hashes_json TEXT NOT NULL, root_counts_json TEXT NOT NULL, asset_count INTEGER NOT NULL, asset_byte_count INTEGER NOT NULL, committed_at TEXT NOT NULL);").map_err(|_| err("无法创建 v2 SQLite owner 表"))
}

fn receipt_json(package: &V2PreflightedPackage) -> Result<String, String> {
    canonical_json(
        &json!({"semanticHash": package.receipt.semantic_hash, "ownerFieldHashes": package.receipt.owner_field_hashes, "rootCounts": package.receipt.root_counts, "assetCount": package.receipt.asset_count, "assetByteCount": package.receipt.asset_bytes}),
    )
}

fn inject(point: Option<FailurePoint>, here: FailurePoint) -> Result<(), String> {
    (point != Some(here))
        .then_some(())
        .ok_or_else(|| err("v2 failure injection"))
}

fn committed_from_preflight(
    root: &Path,
    connection: &Connection,
    package: &V2PreflightedPackage,
) -> Result<Option<V2CommitReceipt>, String> {
    let row = connection.query_row("SELECT workspace_id,semantic_hash,receipt_json_hash FROM exchange_v2_import_journal WHERE package_hash=?1", [&package.receipt.package_hash], |row| Ok((row.get::<_, String>(0)?, row.get::<_, String>(1)?, row.get::<_, String>(2)?))).optional().map_err(|_| err("无法读取 v2 import journal"))?;
    let Some((workspace_id, semantic_hash_value, receipt_hash)) = row else {
        return Ok(None);
    };
    let (exchange_json, receipt_semantic, hashes_json): (String, String, String) = connection.query_row("SELECT i.exchange_json,r.semantic_hash,r.owner_field_hashes_json FROM exchange_v2_imports i JOIN exchange_v2_import_receipts r ON r.package_hash=i.package_hash WHERE i.package_hash=?1", [&package.receipt.package_hash], |row| Ok((row.get(0)?,row.get(1)?,row.get(2)?))).map_err(|_| err("v2 committed owner 或 receipt 缺失"))?;
    let exchange: Value =
        serde_json::from_str(&exchange_json).map_err(|_| err("v2 committed exchange 无效"))?;
    if validate_exchange_v2_ir(&exchange)? != package.receipt.semantic_hash
        || semantic_hash_value != package.receipt.semantic_hash
        || receipt_semantic != package.receipt.semantic_hash
        || sha256(receipt_json(package)?.as_bytes()) != receipt_hash
    {
        return Err(err("v2 committed journal 完整性不一致"));
    }
    let hashes: BTreeMap<String, String> =
        serde_json::from_str(&hashes_json).map_err(|_| err("v2 committed receipt 无效"))?;
    if hashes != package.receipt.owner_field_hashes {
        return Err(err("v2 committed owner field hash 不一致"));
    }
    verify_archive(root, package)?;
    Ok(Some(V2CommitReceipt {
        workspace_id,
        package_hash: package.receipt.package_hash.clone(),
        semantic_hash: package.receipt.semantic_hash.clone(),
        owner_field_hashes: hashes,
        replayed: true,
    }))
}

pub(crate) fn import(
    root: &Path,
    connection: &mut Connection,
    package: &V2PreflightedPackage,
    failure: Option<FailurePoint>,
) -> Result<V2CommitReceipt, String> {
    if let Some(receipt) = committed_from_preflight(root, connection, package)? {
        return Ok(receipt);
    }
    prepare_archive(root, package)?;
    inject(failure, FailurePoint::Begin)?;
    let transaction = connection
        .transaction_with_behavior(TransactionBehavior::Immediate)
        .map_err(|_| err("无法开启 v2 BEGIN IMMEDIATE"))?;
    let result = (|| -> Result<V2CommitReceipt, String> {
        let workspace_id = format!("workspace-v2-{}", &package.receipt.package_hash[..24]);
        let exchange_json = canonical_json(&package.exchange)?;
        transaction.execute("INSERT INTO exchange_v2_imports(workspace_id,package_hash,semantic_hash,exchange_json,origin_platform,sensitivity,created_at) VALUES(?1,?2,?3,?4,?5,?6,'2026-08-20T00:00:00Z')", params![workspace_id,package.receipt.package_hash,package.receipt.semantic_hash,exchange_json,package.receipt.origin,package.receipt.sensitivity]).map_err(|_| err("无法写入 v2 import"))?;
        inject(failure, FailurePoint::Import)?;
        for (entry, bytes) in &package.assets {
            transaction.execute("INSERT INTO exchange_v2_assets(workspace_id,sha256,byte_count) VALUES(?1,?2,?3)", params![workspace_id,entry.strip_prefix("assets/").ok_or_else(|| err("v2 asset entry 无效"))?,bytes.len() as u64]).map_err(|_| err("无法写入 v2 asset index"))?;
        }
        inject(failure, FailurePoint::Asset)?;
        for (key, field_hash) in &package.receipt.owner_field_hashes {
            let (kind, id) = key
                .split_once('/')
                .ok_or_else(|| err("v2 owner field hash key 无效"))?;
            transaction.execute("INSERT INTO exchange_v2_owner_provenance(workspace_id,owner_kind,owner_id,origin_semantic_hash,field_hash,imported_revision) VALUES(?1,?2,?3,?4,?5,0)", params![workspace_id,kind,id,package.receipt.semantic_hash,field_hash]).map_err(|_| err("无法写入 v2 owner provenance"))?;
        }
        inject(failure, FailurePoint::Provenance)?;
        let receipt_json = receipt_json(package)?;
        let receipt_hash = sha256(receipt_json.as_bytes());
        transaction.execute("INSERT INTO exchange_v2_import_journal(package_hash,workspace_id,semantic_hash,receipt_json_hash,committed_at) VALUES(?1,?2,?3,?4,'2026-08-20T00:00:00Z')", params![package.receipt.package_hash,workspace_id,package.receipt.semantic_hash,receipt_hash]).map_err(|_| err("无法写入 v2 import journal"))?;
        inject(failure, FailurePoint::Journal)?;
        transaction.execute("INSERT INTO exchange_v2_import_receipts(package_hash,semantic_hash,owner_field_hashes_json,root_counts_json,asset_count,asset_byte_count,committed_at) VALUES(?1,?2,?3,?4,?5,?6,'2026-08-20T00:00:00Z')", params![package.receipt.package_hash,package.receipt.semantic_hash,serde_json::to_string(&package.receipt.owner_field_hashes).map_err(|_| err("v2 receipt 无法编码"))?,serde_json::to_string(&package.receipt.root_counts).map_err(|_| err("v2 receipt 无法编码"))?,package.receipt.asset_count,package.receipt.asset_bytes]).map_err(|_| err("无法写入 v2 import receipt"))?;
        inject(failure, FailurePoint::Receipt)?;
        inject(failure, FailurePoint::BeforeCommit)?;
        Ok(V2CommitReceipt {
            workspace_id,
            package_hash: package.receipt.package_hash.clone(),
            semantic_hash: package.receipt.semantic_hash.clone(),
            owner_field_hashes: package.receipt.owner_field_hashes.clone(),
            replayed: false,
        })
    })();
    match result {
        Ok(receipt) => {
            transaction
                .commit()
                .map_err(|_| err("v2 transaction 未提交；已回滚"))?;
            Ok(receipt)
        }
        Err(error) => Err(error),
    }
}

pub(crate) fn reexport(
    root: &Path,
    connection: &Connection,
    package_hash: &str,
    target: &Path,
) -> Result<V2PreflightReceipt, String> {
    if !is_hash(package_hash)
        || target.extension().and_then(|value| value.to_str()) != Some("nfai-exchange")
    {
        return Err(err("v2 回导参数无效"));
    }
    let (exchange_json, expected_hashes): (String, String) = connection.query_row("SELECT i.exchange_json,r.owner_field_hashes_json FROM exchange_v2_imports i JOIN exchange_v2_import_receipts r ON r.package_hash=i.package_hash WHERE i.package_hash=?1", [package_hash], |row| Ok((row.get(0)?,row.get(1)?))).map_err(|_| err("v2 已提交 import 不存在"))?;
    let exchange: Value =
        serde_json::from_str(&exchange_json).map_err(|_| err("v2 已提交 exchange 无效"))?;
    let mut assets = BTreeMap::new();
    for metadata in collect_asset_ledger(&exchange)?.values() {
        let metadata = object(metadata, "attachment")?;
        let entry = string(metadata, "entry")?;
        let hash = string(metadata, "sha256")?;
        let bytes = fs::read(archive_root(root, package_hash).join("assets").join(hash))
            .map_err(|_| err("v2 回导 private asset 缺失"))?;
        if sha256(&bytes) != hash {
            return Err(err("v2 回导 private asset hash 不符"));
        }
        assets.insert(entry.to_owned(), bytes);
    }
    let output = preflight(package(&exchange, &assets)?)?;
    let stored: BTreeMap<String, String> =
        serde_json::from_str(&expected_hashes).map_err(|_| err("v2 receipt 无效"))?;
    if output.receipt.owner_field_hashes != stored {
        return Err(err("v2 回导 owner field hash 不一致"));
    }
    let temporary = target.with_extension("nfai-exchange.part");
    write_synced(&temporary, &output.bytes)?;
    fs::rename(&temporary, target).map_err(|_| err("无法原子完成 v2 回导"))?;
    let readback = preflight(fs::read(target).map_err(|_| err("v2 回导回读失败"))?)?;
    if readback.receipt.semantic_hash != output.receipt.semantic_hash
        || readback.receipt.owner_field_hashes != output.receipt.owner_field_hashes
    {
        return Err(err("v2 回导 readback 不一致"));
    }
    Ok(readback.receipt)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::semantic_hash;
    use tempfile::tempdir;

    fn fixture() -> V2PreflightedPackage {
        let mut exchange: Value = serde_json::from_str(include_str!(
            "../../../protocol/fixtures/nfai.exchange.v2.golden.json"
        ))
        .unwrap();
        let hash = sha256(b"a");
        for attachment in exchange
            .get_mut("knowledge")
            .unwrap()
            .as_array_mut()
            .unwrap()[0]
            .get_mut("attachments")
            .unwrap()
            .as_array_mut()
            .unwrap()
        {
            attachment["entry"] = Value::String(format!("assets/{hash}"));
            attachment["sha256"] = Value::String(hash.clone());
        }
        exchange["export"]["semanticHash"] = Value::String(semantic_hash(&exchange).unwrap());
        preflight(
            package(
                &exchange,
                &BTreeMap::from([(format!("assets/{hash}"), b"a".to_vec())]),
            )
            .unwrap(),
        )
        .unwrap()
    }

    fn database() -> (tempfile::TempDir, Connection) {
        let root = tempdir().unwrap();
        let mut connection = Connection::open(root.path().join("workspace.sqlite3")).unwrap();
        let transaction = connection.transaction().unwrap();
        migrate(&transaction).unwrap();
        transaction.commit().unwrap();
        (root, connection)
    }

    fn visible_rows(connection: &Connection) -> i64 {
        connection.query_row("SELECT (SELECT COUNT(*) FROM exchange_v2_imports)+(SELECT COUNT(*) FROM exchange_v2_assets)+(SELECT COUNT(*) FROM exchange_v2_owner_provenance)+(SELECT COUNT(*) FROM exchange_v2_import_journal)+(SELECT COUNT(*) FROM exchange_v2_import_receipts)", [], |row| row.get(0)).unwrap()
    }

    #[test]
    fn preflight_rejects_manifest_or_attachment_ledger_drift() {
        let fixture = fixture();
        let mut exchange = fixture.exchange.clone();
        exchange["knowledge"][0]["attachments"][0]["byteCount"] = Value::Number(2.into());
        exchange["export"]["semanticHash"] = Value::String(semantic_hash(&exchange).unwrap());
        let bytes = package(&exchange, &fixture.assets).unwrap_err();
        assert!(bytes.contains("asset hash"));
        let mut malformed = fixture.bytes.clone();
        malformed[0] ^= 1;
        assert!(preflight(malformed).is_err());
    }

    #[test]
    fn android_writer_package_is_accepted_by_the_desktop_reader_when_supplied() {
        let Ok(path) = std::env::var("NANFENG_AI_ANDROID_V2_PACKAGE_GOLDEN") else {
            return;
        };
        let package = preflight(fs::read(path).expect("Android contract package must be readable"))
            .expect("Android v2 package must pass the Desktop strict reader");
        assert_eq!(package.receipt.origin, "ANDROID");
        assert_eq!(
            package.receipt.root_counts,
            BTreeMap::from([
                ("conversations".into(), 1),
                ("knowledge".into(), 1),
                ("memory".into(), 1),
                ("projects".into(), 1),
                ("relations".into(), 1),
            ])
        );
        assert_eq!(package.receipt.asset_count, 1);
        assert_eq!(package.receipt.asset_count, package.assets.len() as u64);
        for owner in [
            "project/project-v2-01",
            "conversation/conversation-v2-01",
            "knowledge/knowledge-v2-01",
            "memory/memory-v2-01",
            "relation/relation-v2-01",
            "settings/root",
        ] {
            assert!(
                package.receipt.owner_field_hashes.contains_key(owner),
                "missing {owner}"
            );
        }
        assert!(package.receipt.owner_field_hashes.contains_key(
            "asset/f16d05ec6b29248d2c61adb1e9263f78e4f7bace1b955014a2d17872cfe4064d",
        ));
    }

    #[test]
    fn every_transaction_injection_leaves_no_v2_rows() {
        for point in [
            FailurePoint::Begin,
            FailurePoint::Import,
            FailurePoint::Asset,
            FailurePoint::Provenance,
            FailurePoint::Journal,
            FailurePoint::Receipt,
            FailurePoint::BeforeCommit,
        ] {
            let package = fixture();
            let (root, mut connection) = database();
            assert!(import(root.path(), &mut connection, &package, Some(point)).is_err());
            assert_eq!(visible_rows(&connection), 0, "{point:?}");
        }
    }

    #[test]
    fn committed_reopen_replays_and_reexport_preserves_every_field_hash() {
        let package = fixture();
        let (root, mut connection) = database();
        let committed = import(root.path(), &mut connection, &package, None).unwrap();
        assert!(!committed.replayed);
        let replay = import(root.path(), &mut connection, &package, None).unwrap();
        assert!(replay.replayed);
        assert!(visible_rows(&connection) > 0);
        let target = root.path().join("roundtrip.nfai-exchange");
        let readback =
            reexport(root.path(), &connection, &committed.package_hash, &target).unwrap();
        assert_eq!(readback.semantic_hash, committed.semantic_hash);
        assert_eq!(readback.owner_field_hashes, committed.owner_field_hashes);
        assert!(target.exists());
    }

    #[test]
    fn production_migration_is_v1_isolated_after_later_desktop_schema() {
        let root = tempdir().unwrap();
        let store = crate::DesktopWorkspaceStore::open(root.path().to_path_buf()).unwrap();
        let mut connection = store.connection().unwrap();
        let version: u32 = connection
            .pragma_query_value(None, "user_version", |row| row.get(0))
            .unwrap();
        assert_eq!(version, 42);
        let package = fixture();
        import(root.path(), &mut connection, &package, None).unwrap();
        let v1_rows: i64 = connection
            .query_row(
                "SELECT (SELECT COUNT(*) FROM workspaces)+(SELECT COUNT(*) FROM workspace_exchange)+(SELECT COUNT(*) FROM import_journal)",
                [],
                |row| row.get(0),
            )
            .unwrap();
        assert_eq!(v1_rows, 0);
        assert!(visible_rows(&connection) > 0);
    }
}
