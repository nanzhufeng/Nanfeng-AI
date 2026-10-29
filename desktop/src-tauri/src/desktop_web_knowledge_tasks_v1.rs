//! P4O independently persisted web attempts/assets/candidates and atomic Knowledge provenance.
//! Schema 56 owner: startup recovery never replays network work; IPC is not active.
use crate::desktop_markdown_knowledge_v1::{validate_candidate, Candidate};
use crate::desktop_web_text_snapshot_v1::{self as adapter, Transport};
use rusqlite::{params, Connection, OptionalExtension, Transaction};
use serde::{Deserialize, Serialize};
use std::{
    fs,
    io::Write,
    path::{Path, PathBuf},
};

pub(crate) fn create(c: &Connection) -> Result<(), String> {
    c.execute_batch("CREATE TABLE desktop_web_knowledge_tasks_v1(
      id TEXT PRIMARY KEY NOT NULL,workspace_id TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
      status TEXT NOT NULL CHECK(status IN ('QUEUED','FETCHING','EXTRACTING','AWAITING_CONFIRMATION','PARTIALLY_COMPLETED','COMPLETED','FAILED','CANCELLED')),
      requested_url TEXT NOT NULL,generation INTEGER NOT NULL DEFAULT 0 CHECK(generation>=0),safe_error_code TEXT,
      retry_count INTEGER NOT NULL DEFAULT 0,created_at_ms INTEGER NOT NULL,updated_at_ms INTEGER NOT NULL);
      CREATE TABLE desktop_web_knowledge_assets_v1(
      task_id TEXT NOT NULL REFERENCES desktop_web_knowledge_tasks_v1(id) ON DELETE CASCADE,generation INTEGER NOT NULL CHECK(generation>0),
      storage_key TEXT NOT NULL UNIQUE,display_url TEXT NOT NULL,host TEXT NOT NULL,byte_count INTEGER NOT NULL,
      raw_html_hash TEXT NOT NULL,adapter_version INTEGER NOT NULL,extraction_version INTEGER NOT NULL,candidate_version INTEGER NOT NULL,
      extracted_text TEXT,extracted_hash TEXT,PRIMARY KEY(task_id,generation));
      CREATE TABLE desktop_web_knowledge_items_v1(
      task_id TEXT NOT NULL,generation INTEGER NOT NULL,ordinal INTEGER NOT NULL,candidate_json TEXT NOT NULL,candidate_hash TEXT NOT NULL,
      status TEXT NOT NULL CHECK(status IN ('PENDING_CONFIRMATION','CONFIRMED','SKIPPED')),knowledge_id TEXT,
      PRIMARY KEY(task_id,generation,ordinal),FOREIGN KEY(task_id,generation) REFERENCES desktop_web_knowledge_assets_v1(task_id,generation));
      CREATE TABLE desktop_web_knowledge_sources_v1(
      workspace_id TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,knowledge_id TEXT NOT NULL,
      task_id TEXT NOT NULL,generation INTEGER NOT NULL,ordinal INTEGER NOT NULL,display_url TEXT NOT NULL,
      raw_html_hash TEXT NOT NULL,extracted_hash TEXT NOT NULL,candidate_hash TEXT NOT NULL,confirmed_content_hash TEXT NOT NULL,
      adapter_version INTEGER NOT NULL,extraction_version INTEGER NOT NULL,candidate_version INTEGER NOT NULL,
      PRIMARY KEY(workspace_id,knowledge_id),UNIQUE(task_id,generation,ordinal),
      FOREIGN KEY(task_id,generation,ordinal) REFERENCES desktop_web_knowledge_items_v1(task_id,generation,ordinal));
      CREATE INDEX desktop_web_knowledge_task_owner_v1 ON desktop_web_knowledge_tasks_v1(workspace_id,created_at_ms DESC);")
      .map_err(|_|"WEB_TASK_SCHEMA_CREATE".into())
}
// This schema has one canonical creator and no third-party schema import. Compare the
// complete owned DDL, including CHECK/default/PK/FK/UNIQUE and its owner index,
// rather than accepting a few similarly named columns from a partial database.
pub(crate) fn schema_valid(c: &Connection) -> rusqlite::Result<bool> {
    let canonical = Connection::open_in_memory()?;
    canonical.execute_batch("CREATE TABLE workspaces(id TEXT PRIMARY KEY)")?;
    create(&canonical).map_err(|_| rusqlite::Error::InvalidQuery)?;
    for suffix in ["tasks", "assets", "items", "sources"] {
        let name = format!("desktop_web_knowledge_{suffix}_v1");
        if !owned_ddl_matches(c, &canonical, "table", &name)? {
            return Ok(false);
        }
    }
    owned_ddl_matches(
        c,
        &canonical,
        "index",
        "desktop_web_knowledge_task_owner_v1",
    )
}
fn owned_ddl_matches(
    c: &Connection,
    canonical: &Connection,
    kind: &str,
    name: &str,
) -> rusqlite::Result<bool> {
    let read = |db: &Connection| {
        db.query_row(
            "SELECT sql FROM sqlite_schema WHERE type=?1 AND name=?2",
            params![kind, name],
            |r| r.get::<_, String>(0),
        )
        .optional()
    };
    // SQLite preserves CREATE statements. Ignore formatting only, not identifier,
    // literal, type, key, constraint, or default changes.
    let normalize = |s: String| {
        let mut quote = None;
        s.chars()
            .filter(|ch| {
                if matches!(*ch, '\'' | '"') {
                    if quote == Some(*ch) {
                        quote = None;
                    } else if quote.is_none() {
                        quote = Some(*ch);
                    }
                    return true;
                }
                quote.is_some() || !ch.is_ascii_whitespace()
            })
            .collect::<String>()
    };
    Ok(read(c)?.map(normalize) == read(canonical)?.map(normalize))
}
pub(crate) fn migrate_committed_area(c: &mut Connection, expected: &str) -> Result<(), String> {
    use crate::desktop_schema_source::{classify, SchemaSource};
    let version = c
        .pragma_query_value(None, "user_version", |r| r.get(0))
        .map_err(|_| "WEB_SCHEMA_READ")?;
    match classify(c, version).map_err(|_| "WEB_SCHEMA_READ")? {
        SchemaSource::Common56 {
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
        } if area == expected => {
            recover_interrupted(
                c,
                std::time::SystemTime::now()
                    .duration_since(std::time::UNIX_EPOCH)
                    .map_err(|_| "WEB_RECOVERY_CLOCK")?
                    .as_millis()
                    .min(i64::MAX as u128) as i64,
            )?;
            return Ok(());
        }
        SchemaSource::Common55 {
            area,
            split_done: true,
        } if area == expected => {}
        _ => return Err("WEB_SCHEMA_SOURCE_REJECTED".into()),
    }
    let tx = c.transaction().map_err(|_| "WEB_SCHEMA_TRANSACTION")?;
    create(&tx)?;
    tx.pragma_update(None, "user_version", 56)
        .map_err(|_| "WEB_SCHEMA_VERSION")?;
    if !matches!(classify(&tx,56).map_err(|_|"WEB_SCHEMA_READ")?, SchemaSource::Common56 { area, split_done:true } | SchemaSource::Common57 { area, split_done:true } | SchemaSource::Common58 { area, split_done:true } | SchemaSource::Common59 { area, split_done:true } if area == expected)
    {
        return Err("WEB_SCHEMA_TARGET_REJECTED".into());
    }
    tx.commit().map_err(|_| "WEB_SCHEMA_COMMIT".into())
}
#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct Item {
    pub ordinal: i64,
    pub candidate: Candidate,
    pub status: String,
    pub knowledge_id: Option<String>,
}
#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct Task {
    pub id: String,
    pub workspace_id: String,
    pub status: String,
    pub requested_url: String,
    pub generation: i64,
    pub safe_error_code: Option<String>,
    pub retry_count: i64,
    pub items: Vec<Item>,
}
#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct TaskSummary {
    pub id: String,
    pub requested_url: String,
    pub status: String,
    pub generation: i64,
}
#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct TaskPage {
    pub tasks: Vec<TaskSummary>,
    pub has_more: bool,
}
pub(crate) fn list(c: &Connection, w: &str, offset: i64) -> Result<TaskPage, String> {
    if !(0..=1_000_000).contains(&offset) {
        return Err("WEB_TASK_PAGE_INVALID".into());
    }
    let mut stmt=c.prepare("SELECT id,requested_url,status,generation FROM desktop_web_knowledge_tasks_v1 WHERE workspace_id=?1 ORDER BY created_at_ms DESC,rowid DESC LIMIT 51 OFFSET ?2").map_err(|_|"WEB_TASK_LIST")?;
    let mut tasks = stmt
        .query_map(params![w, offset], |r| {
            Ok(TaskSummary {
                id: r.get(0)?,
                requested_url: r.get(1)?,
                status: r.get(2)?,
                generation: r.get(3)?,
            })
        })
        .map_err(|_| "WEB_TASK_LIST")?
        .collect::<rusqlite::Result<Vec<_>>>()
        .map_err(|_| "WEB_TASK_LIST")?;
    let has_more = tasks.len() > 50;
    tasks.truncate(50);
    Ok(TaskPage { tasks, has_more })
}
pub(crate) fn latest(c: &Connection, w: &str) -> Result<Option<Task>, String> {
    let id:Option<String>=c.query_row("SELECT id FROM desktop_web_knowledge_tasks_v1 WHERE workspace_id=?1 ORDER BY created_at_ms DESC,rowid DESC LIMIT 1",[w],|r|r.get(0)).optional().map_err(|_|"WEB_TASK_LATEST")?;
    id.map(|id| read(c, w, &id)).transpose()
}
pub(crate) fn read(c: &Connection, w: &str, id: &str) -> Result<Task, String> {
    let mut t=c.query_row("SELECT id,workspace_id,status,requested_url,generation,safe_error_code,retry_count FROM desktop_web_knowledge_tasks_v1 WHERE id=?1 AND workspace_id=?2",params![id,w],|r|Ok(Task{id:r.get(0)?,workspace_id:r.get(1)?,status:r.get(2)?,requested_url:r.get(3)?,generation:r.get(4)?,safe_error_code:r.get(5)?,retry_count:r.get(6)?,items:vec![]})).map_err(|_|"WEB_TASK_NOT_IN_WORKSPACE")?;
    if matches!(
        t.status.as_str(),
        "AWAITING_CONFIRMATION" | "PARTIALLY_COMPLETED" | "COMPLETED"
    ) {
        let mut s=c.prepare("SELECT ordinal,candidate_json,status,knowledge_id FROM desktop_web_knowledge_items_v1 WHERE task_id=?1 AND generation=?2 ORDER BY ordinal").map_err(|_|"WEB_ITEM_READ")?;
        let rows = s
            .query_map(params![id, t.generation], |r| {
                Ok((
                    r.get::<_, i64>(0)?,
                    r.get::<_, String>(1)?,
                    r.get::<_, String>(2)?,
                    r.get::<_, Option<String>>(3)?,
                ))
            })
            .map_err(|_| "WEB_ITEM_READ")?;
        for row in rows {
            let (ordinal, json, status, knowledge_id) = row.map_err(|_| "WEB_ITEM_READ")?;
            t.items.push(Item {
                ordinal,
                candidate: serde_json::from_str(&json).map_err(|_| "WEB_ITEM_INVALID")?,
                status,
                knowledge_id,
            });
        }
    }
    Ok(t)
}
pub(crate) fn queue(c: &Connection, w: &str, raw_url: &str, now: i64) -> Result<Task, String> {
    let safe = adapter::validate_url(raw_url).map_err(str::to_owned)?;
    let exists: bool = c
        .query_row(
            "SELECT EXISTS(SELECT 1 FROM workspaces WHERE id=?1)",
            [w],
            |r| r.get(0),
        )
        .map_err(|_| "WEB_WORKSPACE_READ")?;
    if !exists {
        return Err("WEB_WORKSPACE_MISSING".into());
    }
    let id = crate::ordinary_chat_random_id("web-text-task")?;
    c.execute("INSERT INTO desktop_web_knowledge_tasks_v1(id,workspace_id,status,requested_url,created_at_ms,updated_at_ms) VALUES(?1,?2,'QUEUED',?3,?4,?4)",params![id,w,safe.display_url,now]).map_err(|_|"WEB_TASK_CREATE")?;
    read(c, w, &id)
}
pub(crate) fn recover_interrupted(c: &Connection, now: i64) -> Result<usize, String> {
    c.execute("UPDATE desktop_web_knowledge_tasks_v1 SET status='FAILED',safe_error_code='INTERRUPTED',updated_at_ms=?1 WHERE status IN ('QUEUED','FETCHING','EXTRACTING')",[now]).map_err(|_|"WEB_TASK_RECOVERY".into())
}
fn safe_asset(root: &Path, key: &str) -> Result<PathBuf, String> {
    let pattern = regex::Regex::new(
        r"^web-text-snapshots/v1/web-text-task-[a-f0-9]+/g[1-9][0-9]*/snapshot\.html$",
    )
    .expect("fixed asset key");
    if !root.is_absolute() || !pattern.is_match(key) {
        return Err("WEB_ASSET_KEY".into());
    }
    let path = root.join(key);
    for ancestor in path.ancestors().take_while(|p| p.starts_with(root)) {
        if let Ok(m) = fs::symlink_metadata(ancestor) {
            if m.file_type().is_symlink() {
                return Err("WEB_ASSET_LINK".into());
            }
            #[cfg(windows)]
            {
                use std::os::windows::fs::MetadataExt;
                if m.file_attributes() & 0x400 != 0 {
                    return Err("WEB_ASSET_LINK".into());
                }
            }
        }
    }
    Ok(path)
}
fn persist_html(
    c: &Connection,
    root: &Path,
    id: &str,
    generation: i64,
    url: &str,
    host: &str,
    html: &[u8],
    now: i64,
) -> Result<(), &'static str> {
    if html.len() > adapter::MAX_HTML_BYTES {
        return Err("RESPONSE_TOO_LARGE");
    }
    let safe = adapter::validate_url(url)?;
    if safe.display_url != url || safe.host != host {
        return Err("PRIVATE_COPY_FAILED");
    }
    let key = format!("web-text-snapshots/v1/{id}/g{generation}/snapshot.html");
    let path = safe_asset(root, &key).map_err(|_| "PRIVATE_COPY_FAILED")?;
    let part = path.with_extension("part");
    let mut owns_part = false;
    let copied = (|| -> Result<(), &'static str> {
        fs::create_dir_all(path.parent().ok_or("PRIVATE_COPY_FAILED")?)
            .map_err(|_| "PRIVATE_COPY_FAILED")?;
        safe_asset(root, &key).map_err(|_| "PRIVATE_COPY_FAILED")?;
        let mut f = fs::OpenOptions::new()
            .write(true)
            .create_new(true)
            .open(&part)
            .map_err(|_| "PRIVATE_COPY_FAILED")?;
        owns_part = true;
        f.write_all(html)
            .and_then(|_| f.sync_all())
            .map_err(|_| "PRIVATE_COPY_FAILED")?;
        drop(f);
        if path.exists() {
            return Err("PRIVATE_COPY_FAILED");
        }
        fs::rename(&part, &path).map_err(|_| "PRIVATE_COPY_FAILED")?;
        owns_part = false;
        if crate::sha256(&fs::read(&path).map_err(|_| "PRIVATE_COPY_FAILED")?)
            != crate::sha256(html)
        {
            return Err("PRIVATE_COPY_FAILED");
        }
        Ok(())
    })();
    if owns_part {
        let _ = fs::remove_file(&part);
    }
    copied?;
    let tx = c
        .unchecked_transaction()
        .map_err(|_| "PRIVATE_COPY_FAILED")?;
    let active=tx.execute("UPDATE desktop_web_knowledge_tasks_v1 SET status='EXTRACTING',updated_at_ms=?3 WHERE id=?1 AND generation=?2 AND status='FETCHING'",params![id,generation,now]).map_err(|_|"PRIVATE_COPY_FAILED")?==1;
    tx.execute("INSERT INTO desktop_web_knowledge_assets_v1(task_id,generation,storage_key,display_url,host,byte_count,raw_html_hash,adapter_version,extraction_version,candidate_version) VALUES(?1,?2,?3,?4,?5,?6,?7,?8,1,1)",params![id,generation,key,url,host,html.len(),crate::sha256(html),adapter::ADAPTER_VERSION]).map_err(|_|"PRIVATE_COPY_FAILED")?;
    tx.commit().map_err(|_| "PRIVATE_COPY_FAILED")?;
    if active {
        Ok(())
    } else {
        Err("CANCELLED")
    }
}
fn cancelled(c: &Connection, id: &str, generation: i64) -> bool {
    c.query_row(
        "SELECT generation,status FROM desktop_web_knowledge_tasks_v1 WHERE id=?1",
        [id],
        |r| Ok((r.get::<_, i64>(0)?, r.get::<_, String>(1)?)),
    )
    .map_or(true, |(g, s)| {
        g != generation || !matches!(s.as_str(), "FETCHING" | "EXTRACTING")
    })
}
fn safe_failure(error: &str) -> &str {
    match error {
        "INVALID_URL"
        | "HIGH_SENSITIVITY_URL"
        | "DNS_NOT_PUBLIC"
        | "REDIRECT_REJECTED"
        | "TOO_MANY_REDIRECTS"
        | "HTTP_STATUS"
        | "CONTENT_TYPE"
        | "CONTENT_ENCODING"
        | "RESPONSE_TOO_LARGE"
        | "TIMEOUT"
        | "CERTIFICATE"
        | "NETWORK"
        | "MALFORMED_RESPONSE"
        | "INVALID_UTF8"
        | "MALFORMED_HTML"
        | "HTML_TOO_COMPLEX"
        | "NO_VISIBLE_TEXT"
        | "TEXT_TOO_LARGE"
        | "HIGH_SENSITIVITY"
        | "PRIVATE_COPY_FAILED"
        | "KNOWLEDGE_REJECTED"
        | "CANCELLED" => error,
        _ => "WEB_FETCH_FAILED",
    }
}
/// Caller supplies an independent connection and a freshly user-confirmed URL each time.
/// This local future does not hold the global store mutex; Send/IPC integration is separate.
pub(crate) async fn process(
    c: &mut Connection,
    root: &Path,
    w: &str,
    id: &str,
    raw_url: &str,
    now: i64,
    retry: bool,
    transport: &mut impl Transport,
) -> Result<Task, String> {
    process_with_cancel(c, root, w, id, raw_url, now, retry, transport, || false).await
}
pub(crate) async fn process_with_cancel(
    c: &mut Connection,
    root: &Path,
    w: &str,
    id: &str,
    raw_url: &str,
    now: i64,
    retry: bool,
    transport: &mut impl Transport,
    external_cancelled: impl Fn() -> bool,
) -> Result<Task, String> {
    let safe = adapter::validate_url(raw_url).map_err(str::to_owned)?;
    let task = read(c, w, id)?;
    let expected = if retry { "FAILED" } else { "QUEUED" };
    if task.status != expected {
        return Err("WEB_TASK_NOT_STARTABLE".into());
    }
    if !retry && task.requested_url != safe.display_url {
        return Err("WEB_REQUESTED_URL_CHANGED".into());
    }
    if external_cancelled() {
        return decide(c, w, id, task.generation, None, now);
    }
    let generation = task
        .generation
        .checked_add(1)
        .ok_or("WEB_GENERATION_LIMIT")?;
    if c.execute("UPDATE desktop_web_knowledge_tasks_v1 SET status='FETCHING',generation=?3,requested_url=?4,safe_error_code=NULL,retry_count=retry_count+?5,updated_at_ms=?6 WHERE id=?1 AND workspace_id=?2 AND status=?7 AND generation=?8",params![id,w,generation,safe.display_url,i64::from(retry),now,expected,task.generation]).map_err(|_|"WEB_TASK_CLAIM")?!=1{return Err("WEB_TASK_ALREADY_CLAIMED".into());}
    let fetched = adapter::fetch_with_private_copy(
        transport,
        raw_url,
        || external_cancelled() || cancelled(c, id, generation),
        |url, host, html| persist_html(c, root, id, generation, url, host, html, now),
    )
    .await;
    let fetched = match fetched {
        Ok(f) => f,
        Err(error) => {
            if external_cancelled() {
                cancel_processing(c, id, generation, now)?;
                return read(c, w, id);
            }
            c.execute("UPDATE desktop_web_knowledge_tasks_v1 SET status='FAILED',safe_error_code=?3,updated_at_ms=?4 WHERE id=?1 AND generation=?2 AND status IN ('FETCHING','EXTRACTING')",params![id,generation,safe_failure(error),now]).map_err(|_|"WEB_TASK_FAILURE")?;
            return read(c, w, id);
        }
    };
    let saved = (|| -> Result<(), String> {
        let tx = c.transaction().map_err(|_| "WEB_RESULT_TRANSACTION")?;
        if external_cancelled() || cancelled(&tx, id, generation) {
            drop(tx);
            if external_cancelled() {
                cancel_processing(c, id, generation, now)?;
            }
            return Ok(());
        }
        let raw_hash:String=tx.query_row("SELECT raw_html_hash FROM desktop_web_knowledge_assets_v1 WHERE task_id=?1 AND generation=?2",params![id,generation],|r|r.get(0)).map_err(|_|"WEB_ASSET_MISSING")?;
        if raw_hash != fetched.snapshot.raw_html_hash
            || raw_hash != crate::sha256(&fetched.html)
            || fetched.snapshot.extracted_hash != crate::sha256(fetched.snapshot.text.as_bytes())
            || fetched.snapshot.candidates.len() != fetched.snapshot.candidate_hashes.len()
        {
            return Err("WEB_RESULT_HASH".into());
        }
        tx.execute("UPDATE desktop_web_knowledge_assets_v1 SET extracted_text=?3,extracted_hash=?4 WHERE task_id=?1 AND generation=?2",params![id,generation,fetched.snapshot.text,fetched.snapshot.extracted_hash]).map_err(|_|"WEB_EXTRACT_WRITE")?;
        for (ordinal, (candidate, hash)) in fetched
            .snapshot
            .candidates
            .iter()
            .zip(&fetched.snapshot.candidate_hashes)
            .enumerate()
        {
            let candidate = validate_candidate(candidate.clone())?;
            if *hash != crate::sha256(format!("{}\n{}", candidate.title, candidate.body).as_bytes())
            {
                return Err("WEB_RESULT_HASH".into());
            }
            tx.execute("INSERT INTO desktop_web_knowledge_items_v1(task_id,generation,ordinal,candidate_json,candidate_hash,status) VALUES(?1,?2,?3,?4,?5,'PENDING_CONFIRMATION')",params![id,generation,ordinal,serde_json::to_string(&candidate).map_err(|_|"WEB_CANDIDATE_INVALID")?,hash]).map_err(|_|"WEB_ITEM_WRITE")?;
        }
        if external_cancelled() {
            drop(tx);
            cancel_processing(c, id, generation, now)?;
            return Ok(());
        }
        tx.execute("UPDATE desktop_web_knowledge_tasks_v1 SET status='AWAITING_CONFIRMATION',updated_at_ms=?3 WHERE id=?1 AND generation=?2 AND status='EXTRACTING'",params![id,generation,now]).map_err(|_|"WEB_TASK_WRITE")?;
        tx.commit().map_err(|_| "WEB_RESULT_COMMIT")?;
        Ok(())
    })();
    if let Err(error) = saved {
        c.execute("UPDATE desktop_web_knowledge_tasks_v1 SET status='FAILED',safe_error_code='WEB_RESULT_SAVE_FAILED',updated_at_ms=?3 WHERE id=?1 AND generation=?2 AND status IN ('FETCHING','EXTRACTING')",params![id,generation,now]).map_err(|_|"WEB_TASK_FAILURE")?;
        return Err(error);
    }
    read(c, w, id)
}
fn finish(tx: &Transaction<'_>, id: &str, generation: i64, now: i64) -> Result<(), String> {
    tx.execute("UPDATE desktop_web_knowledge_tasks_v1 SET status=CASE WHEN EXISTS(SELECT 1 FROM desktop_web_knowledge_items_v1 WHERE task_id=?1 AND generation=?2 AND status='PENDING_CONFIRMATION') THEN 'PARTIALLY_COMPLETED' ELSE 'COMPLETED' END,updated_at_ms=?3 WHERE id=?1 AND generation=?2",params![id,generation,now]).map_err(|_|"WEB_TASK_WRITE")?;
    Ok(())
}
fn cancel_processing(c: &Connection, id: &str, generation: i64, now: i64) -> Result<(), String> {
    c.execute("UPDATE desktop_web_knowledge_tasks_v1 SET status='CANCELLED',safe_error_code=NULL,updated_at_ms=?3 WHERE id=?1 AND generation=?2 AND status IN ('FETCHING','EXTRACTING')",params![id,generation,now]).map_err(|_|"WEB_TASK_CANCEL_WRITE")?;
    Ok(())
}
pub(crate) fn decide(
    c: &mut Connection,
    w: &str,
    id: &str,
    generation: i64,
    ordinal: Option<i64>,
    now: i64,
) -> Result<Task, String> {
    let tx = c.transaction().map_err(|_| "WEB_DECISION_TRANSACTION")?;
    let task = read(&tx, w, id)?;
    if task.generation != generation || matches!(task.status.as_str(), "CANCELLED" | "COMPLETED") {
        return Err("WEB_TASK_TERMINAL_OR_STALE".into());
    }
    if let Some(ordinal) = ordinal {
        if !matches!(
            task.status.as_str(),
            "AWAITING_CONFIRMATION" | "PARTIALLY_COMPLETED"
        ) {
            return Err("WEB_ITEM_NOT_PENDING".into());
        }
        if tx.execute("UPDATE desktop_web_knowledge_items_v1 SET status='SKIPPED' WHERE task_id=?1 AND generation=?2 AND ordinal=?3 AND status='PENDING_CONFIRMATION'",params![id,generation,ordinal]).map_err(|_|"WEB_ITEM_WRITE")?!=1{return Err("WEB_ITEM_NOT_PENDING".into());}
        finish(&tx, id, generation, now)?;
    } else {
        tx.execute("UPDATE desktop_web_knowledge_tasks_v1 SET status='CANCELLED',updated_at_ms=?3 WHERE id=?1 AND generation=?2",params![id,generation,now]).map_err(|_|"WEB_TASK_WRITE")?;
    }
    tx.commit().map_err(|_| "WEB_DECISION_COMMIT")?;
    read(c, w, id)
}
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub(crate) struct Confirm {
    pub workspace_id: String,
    pub task_id: String,
    pub generation: i64,
    pub ordinal: i64,
    pub candidate: Candidate,
    pub project_id: Option<String>,
}
fn confirmable(c: &Connection, a: &Confirm) -> Result<Item, String> {
    let mut task = read(c, &a.workspace_id, &a.task_id)?;
    if task.generation != a.generation
        || !matches!(
            task.status.as_str(),
            "AWAITING_CONFIRMATION" | "PARTIALLY_COMPLETED" | "COMPLETED"
        )
    {
        return Err("WEB_TASK_NOT_CONFIRMABLE".into());
    }
    let index = task
        .items
        .iter()
        .position(|i| i.ordinal == a.ordinal)
        .ok_or("WEB_ITEM_MISSING")?;
    let item = task.items.remove(index);
    if !matches!(item.status.as_str(), "PENDING_CONFIRMATION" | "CONFIRMED") {
        return Err("WEB_ITEM_NOT_PENDING".into());
    }
    let (text,extracted_hash,candidate_hash,raw_hash,url):(String,String,String,String,String)=c.query_row(
        "SELECT a.extracted_text,a.extracted_hash,i.candidate_hash,a.raw_html_hash,a.display_url FROM desktop_web_knowledge_assets_v1 a JOIN desktop_web_knowledge_items_v1 i ON i.task_id=a.task_id AND i.generation=a.generation WHERE i.task_id=?1 AND i.generation=?2 AND i.ordinal=?3",
        params![a.task_id,a.generation,a.ordinal],|r|Ok((r.get(0)?,r.get(1)?,r.get(2)?,r.get(3)?,r.get(4)?))).map_err(|_|"WEB_SOURCE_MISSING")?;
    let original = validate_candidate(item.candidate.clone())?;
    if crate::sha256(text.as_bytes()) != extracted_hash
        || crate::sha256(format!("{}\n{}", original.title, original.body).as_bytes())
            != candidate_hash
        || raw_hash.len() != 64
        || !raw_hash.bytes().all(|b| b.is_ascii_hexdigit())
        || adapter::validate_url(&url).map_or(true, |safe| safe.display_url != url)
    {
        return Err("WEB_SOURCE_INTEGRITY".into());
    }
    Ok(item)
}
pub(crate) fn confirmation_args(
    c: &Connection,
    a: &Confirm,
) -> Result<crate::DomainMutationArgs, String> {
    let item = confirmable(c, a)?;
    let candidate = validate_candidate(a.candidate.clone())?;
    let intent_id = if item.status == "CONFIRMED" {
        c.query_row("SELECT intent_id FROM domain_intents WHERE workspace_id=?1 AND entity='knowledge' AND action='create' AND object_id=?2",params![a.workspace_id,item.knowledge_id.ok_or("WEB_CONFIRMATION_MISSING")?],|r|r.get(0)).map_err(|_|"WEB_CONFIRMATION_MISSING")?
    } else {
        format!(
            "wsk-{}",
            &crate::sha256(
                format!(
                    "nfai-web-knowledge-confirm-v1:{}:{}:{}",
                    a.task_id, a.generation, a.ordinal
                )
                .as_bytes()
            )[..48]
        )
    };
    Ok(crate::DomainMutationArgs {
        intent_id,
        workspace_id: a.workspace_id.clone(),
        entity: "knowledge".into(),
        action: "create".into(),
        object_id: None,
        expected_revision: None,
        fields: serde_json::json!({"title":candidate.title,"body":candidate.body,"tags":candidate.tags,"scope":if a.project_id.is_some(){"PROJECT"}else{"GLOBAL"},"projectId":a.project_id}),
    })
}
pub(crate) fn commit_confirmation(
    tx: &Transaction<'_>,
    a: &Confirm,
    r: &crate::MutationReceipt,
    now: i64,
) -> Result<(), String> {
    let item = confirmable(tx, a)?;
    let candidate = validate_candidate(a.candidate.clone())?;
    let confirmed_hash = crate::sha256(
        crate::canonical_json(
            &serde_json::json!({"candidate":candidate,"projectId":a.project_id}),
        )?
        .as_bytes(),
    );
    if item.status == "CONFIRMED" {
        let saved:String=tx.query_row("SELECT confirmed_content_hash FROM desktop_web_knowledge_sources_v1 WHERE workspace_id=?1 AND knowledge_id=?2",params![a.workspace_id,r.object_id],|r|r.get(0)).map_err(|_|"WEB_SOURCE_MISSING")?;
        if !r.replayed
            || item.knowledge_id.as_deref() != Some(&r.object_id)
            || saved != confirmed_hash
        {
            return Err("WEB_CONFIRMATION_CONFLICT".into());
        }
        return Ok(());
    }
    if tx.execute("INSERT INTO desktop_web_knowledge_sources_v1(workspace_id,knowledge_id,task_id,generation,ordinal,display_url,raw_html_hash,extracted_hash,candidate_hash,confirmed_content_hash,adapter_version,extraction_version,candidate_version)
      SELECT ?1,?2,a.task_id,a.generation,i.ordinal,a.display_url,a.raw_html_hash,a.extracted_hash,i.candidate_hash,?6,a.adapter_version,a.extraction_version,a.candidate_version
      FROM desktop_web_knowledge_assets_v1 a JOIN desktop_web_knowledge_items_v1 i ON i.task_id=a.task_id AND i.generation=a.generation WHERE a.task_id=?3 AND a.generation=?4 AND i.ordinal=?5",params![a.workspace_id,r.object_id,a.task_id,a.generation,a.ordinal,confirmed_hash]).map_err(|_|"WEB_SOURCE_WRITE")?!=1{return Err("WEB_SOURCE_MISSING".into());}
    if tx.execute("UPDATE desktop_web_knowledge_items_v1 SET status='CONFIRMED',knowledge_id=?4 WHERE task_id=?1 AND generation=?2 AND ordinal=?3 AND status='PENDING_CONFIRMATION'",params![a.task_id,a.generation,a.ordinal,r.object_id]).map_err(|_|"WEB_ITEM_WRITE")?!=1{return Err("WEB_CONFIRMATION_CONFLICT".into());}
    finish(tx, &a.task_id, a.generation, now)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::desktop_web_text_snapshot_v1::{Operation, Response, SafeUrl};
    fn previous(area: &str) -> Connection {
        let mut c = crate::desktop_json_knowledge_tasks_v1::tests::previous(area);
        crate::desktop_json_knowledge_tasks_v1::migrate_committed_area(&mut c, area).unwrap();
        crate::desktop_pdf_knowledge_tasks_v1::migrate_committed_area(&mut c, area).unwrap();
        c
    }
    fn dump_old(c: &Connection) -> Vec<(String, Vec<Vec<String>>)> {
        let mut names=c.prepare("SELECT name FROM sqlite_schema WHERE type='table' AND name NOT LIKE 'desktop_web_%' ORDER BY name").unwrap();
        names
            .query_map([], |r| r.get::<_, String>(0))
            .unwrap()
            .map(|n| {
                let n = n.unwrap();
                let mut q = c
                    .prepare(&format!("SELECT * FROM \"{n}\" ORDER BY rowid"))
                    .unwrap();
                let columns = q.column_count();
                let rows = q
                    .query_map([], |r| {
                        Ok((0..columns)
                            .map(|i| format!("{:?}", r.get_ref(i).unwrap()))
                            .collect())
                    })
                    .unwrap()
                    .map(Result::unwrap)
                    .collect();
                (n, rows)
            })
            .collect()
    }
    #[test]
    fn schema56_preserves_prior_owners_and_drafts_in_both_areas() {
        for area in ["CHAT", "WORK"] {
            let mut c = previous(area);
            for kind in ["markdown", "json", "pdf"] {
                c.execute(&format!("INSERT INTO desktop_{kind}_knowledge_tasks_v1(id,workspace_id,status,display_name,created_at_ms,updated_at_ms) VALUES(?1,'synthetic-workspace','AWAITING_CONFIRMATION','synthetic retained source',1,2)"),[kind]).unwrap();
            }
            c.execute_batch("PRAGMA foreign_keys=ON;
                INSERT INTO desktop_markdown_knowledge_items_v1 VALUES('markdown',0,'{}','CONFIRMED','old-md');
                INSERT INTO desktop_markdown_knowledge_sources_v1 VALUES('synthetic-workspace','old-md','markdown',0,1,'synthetic-old-raw','synthetic-old-content');
                INSERT INTO desktop_json_knowledge_items_v1 VALUES('json',0,'{}','CONFIRMED','old-json');
                INSERT INTO desktop_json_knowledge_sources_v1 VALUES('synthetic-workspace','old-json','json',0,1,'synthetic-old-raw','synthetic-old-content');
                INSERT INTO desktop_pdf_knowledge_pages_v1 VALUES('pdf',1,1,'retained page text','synthetic-old-page',18,3);
                INSERT INTO desktop_pdf_knowledge_items_v1 VALUES('pdf',1,0,1,'{}','synthetic-old-candidate',1,'CONFIRMED','old-pdf');
                INSERT INTO desktop_pdf_knowledge_sources_v1 VALUES('synthetic-workspace','old-pdf','pdf',1,0,1,1,3,1,'synthetic-old-raw','synthetic-old-page','synthetic-old-candidate','synthetic-old-content');").unwrap();
            let before = dump_old(&c);
            assert!(
                migrate_committed_area(&mut c, if area == "CHAT" { "WORK" } else { "CHAT" })
                    .is_err()
            );
            assert_eq!(dump_old(&c), before);
            migrate_committed_area(&mut c, area).unwrap();
            assert_eq!(
                c.pragma_query_value(None, "user_version", |r| r.get::<_, i64>(0))
                    .unwrap(),
                56
            );
            assert_eq!(dump_old(&c), before);
            // All earlier owners recognize the new version without destructive fallback.
            crate::desktop_markdown_knowledge_v1::migrate_committed_area(&mut c, area).unwrap();
            crate::desktop_json_knowledge_tasks_v1::migrate_committed_area(&mut c, area).unwrap();
            crate::desktop_pdf_knowledge_tasks_v1::migrate_committed_area(&mut c, area).unwrap();
            migrate_committed_area(&mut c, area).unwrap();
            assert_eq!(dump_old(&c), before);
            assert_eq!(
                c.query_row("PRAGMA integrity_check", [], |r| r.get::<_, String>(0))
                    .unwrap(),
                "ok"
            );
        }
    }
    #[test]
    fn schema56_startup_interrupts_processing_only_without_network_replay() {
        let mut c = previous("WORK");
        migrate_committed_area(&mut c, "WORK").unwrap();
        let statuses = [
            "QUEUED",
            "FETCHING",
            "EXTRACTING",
            "AWAITING_CONFIRMATION",
            "PARTIALLY_COMPLETED",
            "COMPLETED",
            "FAILED",
            "CANCELLED",
        ];
        for status in statuses {
            c.execute("INSERT INTO desktop_web_knowledge_tasks_v1(id,workspace_id,status,requested_url,created_at_ms,updated_at_ms) VALUES(?1,'synthetic-workspace',?1,'https://example.org/',1,2)",[status]).unwrap();
        }
        migrate_committed_area(&mut c, "WORK").unwrap();
        for status in statuses {
            let (actual, error): (String, Option<String>) = c
                .query_row(
                    "SELECT status,safe_error_code FROM desktop_web_knowledge_tasks_v1 WHERE id=?1",
                    [status],
                    |r| Ok((r.get(0)?, r.get(1)?)),
                )
                .unwrap();
            if matches!(status, "QUEUED" | "FETCHING" | "EXTRACTING") {
                assert_eq!(actual, "FAILED");
                assert_eq!(error.as_deref(), Some("INTERRUPTED"));
            } else {
                assert_eq!(actual, status);
                assert!(error.is_none());
            }
        }
    }
    #[test]
    fn schema56_refuses_partial_or_unknown_sources_and_rolls_back_creation() {
        let mut c = previous("WORK");
        let before = dump_old(&c);
        c.execute_batch("CREATE TABLE desktop_web_knowledge_items_v1(blocker TEXT)")
            .unwrap();
        assert!(migrate_committed_area(&mut c, "WORK").is_err());
        assert_eq!(
            c.pragma_query_value(None, "user_version", |r| r.get::<_, i64>(0))
                .unwrap(),
            55
        );
        assert_eq!(dump_old(&c), before);
        assert_eq!(
            c.query_row(
                "SELECT count(*) FROM sqlite_schema WHERE name='desktop_web_knowledge_tasks_v1'",
                [],
                |r| r.get::<_, i64>(0)
            )
            .unwrap(),
            0
        );
        c.execute_batch("DROP TABLE desktop_web_knowledge_items_v1; PRAGMA user_version=57")
            .unwrap();
        assert!(migrate_committed_area(&mut c, "WORK").is_err());
        assert_eq!(dump_old(&c), before);
    }
    #[test]
    fn schema56_rejects_missing_table_column_index_and_weakened_constraints() {
        for ddl in [
            "DROP TABLE desktop_web_knowledge_sources_v1",
            "ALTER TABLE desktop_web_knowledge_assets_v1 DROP COLUMN extracted_text",
            "DROP INDEX desktop_web_knowledge_task_owner_v1",
            "PRAGMA writable_schema=ON; UPDATE sqlite_schema SET sql=replace(sql,'QUEUED','QUE UED') WHERE name='desktop_web_knowledge_tasks_v1'; PRAGMA writable_schema=OFF",
            "PRAGMA writable_schema=ON; UPDATE sqlite_schema SET sql=replace(sql,'id TEXT PRIMARY KEY NOT NULL','id TEXT PRIMARY KEY') WHERE name='desktop_web_knowledge_tasks_v1'; PRAGMA writable_schema=OFF",
            "PRAGMA writable_schema=ON; UPDATE sqlite_schema SET sql=replace(sql,'UNIQUE(task_id,generation,ordinal)','CHECK(1)') WHERE name='desktop_web_knowledge_sources_v1'; PRAGMA writable_schema=OFF",
            "PRAGMA writable_schema=ON; UPDATE sqlite_schema SET sql=replace(sql,'REFERENCES desktop_web_knowledge_items_v1','REFERENCES invalid_owner') WHERE name='desktop_web_knowledge_sources_v1'; PRAGMA writable_schema=OFF",
        ] {
            let mut c=previous("CHAT"); migrate_committed_area(&mut c,"CHAT").unwrap(); c.execute_batch(ddl).unwrap();
            assert!(!schema_valid(&c).unwrap(),"{ddl}"); assert!(migrate_committed_area(&mut c,"CHAT").is_err(),"{ddl}");
        }
    }
    #[test]
    fn web_task_history_is_bounded_scoped_stable_and_query_free() {
        let c = Connection::open_in_memory().unwrap();
        c.execute_batch("CREATE TABLE workspaces(id TEXT PRIMARY KEY);INSERT INTO workspaces VALUES('one'),('two');").unwrap();
        create(&c).unwrap();
        let mut ids = Vec::new();
        for _ in 0..52 {
            ids.push(
                queue(
                    &c,
                    "one",
                    "https://example.org/?ordinary=private-ephemeral",
                    7,
                )
                .unwrap()
                .id,
            );
        }
        let other = queue(&c, "two", "https://example.org/other", 8).unwrap();
        let first = list(&c, "one", 0).unwrap();
        assert_eq!(first.tasks.len(), 50);
        assert!(first.has_more);
        assert_eq!(first.tasks[0].id, ids[51]);
        assert!(first
            .tasks
            .iter()
            .all(|t| t.requested_url == "https://example.org/"));
        let second = list(&c, "one", 50).unwrap();
        assert_eq!(second.tasks.len(), 2);
        assert!(!second.has_more);
        assert_eq!(second.tasks[0].id, ids[1]);
        assert_eq!(latest(&c, "one").unwrap().unwrap().id, ids[51]);
        assert_eq!(latest(&c, "two").unwrap().unwrap().id, other.id);
        assert!(latest(&c, "missing").unwrap().is_none());
        assert!(list(&c, "one", -1).is_err());
        assert!(list(&c, "one", 1_000_001).is_err());
    }
    struct Fake {
        body: Vec<u8>,
        calls: usize,
        delay: std::time::Duration,
        fail: bool,
    }
    impl Transport for Fake {
        fn resolve<'a>(&'a mut self, _: &'a str) -> Operation<'a, Vec<std::net::IpAddr>> {
            self.calls += 1;
            Box::pin(async { Ok(vec!["8.8.8.8".parse().unwrap()]) })
        }
        fn request<'a>(
            &'a mut self,
            _: &'a SafeUrl,
            _: &'a [std::net::IpAddr],
        ) -> Operation<'a, Response> {
            self.calls += 1;
            Box::pin(async move {
                tokio::time::sleep(self.delay).await;
                if self.fail {
                    Err("NETWORK")
                } else {
                    Ok(Response {
                        status: 200,
                        headers: vec![("content-type".into(), "text/html".into())],
                        body: self.body.clone(),
                    })
                }
            })
        }
    }
    fn fake(text: &str) -> Fake {
        Fake {
            body: format!("<title>Snapshot</title><p>{text}</p>").into_bytes(),
            calls: 0,
            delay: std::time::Duration::ZERO,
            fail: false,
        }
    }
    #[test]
    fn web_tasks_cancelled_private_copy_retains_raw_receipt_without_candidates() {
        let dir = tempfile::tempdir().unwrap();
        let (_s, w, mut c) = store(dir.path());
        let t = queue(&c, &w, "https://public.example.org/", 1).unwrap();
        c.execute(
            "UPDATE desktop_web_knowledge_tasks_v1 SET status='FETCHING',generation=1 WHERE id=?1",
            [&t.id],
        )
        .unwrap();
        decide(&mut c, &w, &t.id, 1, None, 2).unwrap();
        assert_eq!(
            persist_html(
                &c,
                dir.path(),
                &t.id,
                1,
                "https://public.example.org/",
                "public.example.org",
                b"<p>retained raw</p>",
                3
            )
            .err(),
            Some("CANCELLED")
        );
        assert_eq!(read(&c, &w, &t.id).unwrap().status, "CANCELLED");
        assert_eq!(
            c.query_row(
                "SELECT COUNT(*) FROM desktop_web_knowledge_assets_v1 WHERE extracted_text IS NULL",
                [],
                |r| r.get::<_, i64>(0)
            )
            .unwrap(),
            1
        );
    }
    #[tokio::test]
    async fn web_tasks_tampered_or_missing_source_facts_refuse_confirmation() {
        let dir = tempfile::tempdir().unwrap();
        let (_s, w, mut c) = store(dir.path());
        let t = ready(&mut c, dir.path(), &w, "Body").await;
        let a = Confirm {
            workspace_id: w.clone(),
            task_id: t.id.clone(),
            generation: 1,
            ordinal: 0,
            candidate: t.items[0].candidate.clone(),
            project_id: None,
        };
        let hash: String = c
            .query_row(
                "SELECT candidate_hash FROM desktop_web_knowledge_items_v1",
                [],
                |r| r.get(0),
            )
            .unwrap();
        c.execute(
            "UPDATE desktop_web_knowledge_items_v1 SET candidate_hash='invalid'",
            [],
        )
        .unwrap();
        assert!(confirmation_args(&c, &a).is_err());
        c.execute(
            "UPDATE desktop_web_knowledge_items_v1 SET candidate_hash=?1",
            [hash],
        )
        .unwrap();
        c.execute(
            "UPDATE desktop_web_knowledge_assets_v1 SET extracted_hash='invalid'",
            [],
        )
        .unwrap();
        assert!(confirmation_args(&c, &a).is_err());
        c.execute_batch("PRAGMA foreign_keys=OFF; DELETE FROM desktop_web_knowledge_assets_v1;")
            .unwrap();
        assert!(confirmation_args(&c, &a).is_err());
    }
    #[tokio::test]
    async fn web_tasks_result_transaction_failure_is_terminal_retryable_and_keeps_raw_asset() {
        let dir = tempfile::tempdir().unwrap();
        let (_s, w, mut c) = store(dir.path());
        let t = queue(&c, &w, "https://public.example.org/", 1).unwrap();
        c.execute_batch("CREATE TRIGGER reject_web_item BEFORE INSERT ON desktop_web_knowledge_items_v1 BEGIN SELECT RAISE(ABORT,'synthetic result rollback'); END;").unwrap();
        assert!(process(
            &mut c,
            dir.path(),
            &w,
            &t.id,
            "https://public.example.org/",
            2,
            false,
            &mut fake("body")
        )
        .await
        .is_err());
        let failed = read(&c, &w, &t.id).unwrap();
        assert_eq!(failed.status, "FAILED");
        assert_eq!(
            failed.safe_error_code.as_deref(),
            Some("WEB_RESULT_SAVE_FAILED")
        );
        assert!(failed.items.is_empty());
        assert_eq!(
            c.query_row(
                "SELECT COUNT(*) FROM desktop_web_knowledge_assets_v1 WHERE extracted_hash IS NULL",
                [],
                |r| r.get::<_, i64>(0)
            )
            .unwrap(),
            1
        );
        c.execute("DROP TRIGGER reject_web_item", []).unwrap();
        let ready = process(
            &mut c,
            dir.path(),
            &w,
            &t.id,
            "https://public.example.org/",
            3,
            true,
            &mut fake("body"),
        )
        .await
        .unwrap();
        assert_eq!(ready.generation, 2);
        assert_eq!(ready.status, "AWAITING_CONFIRMATION");
    }
    fn store(root: &Path) -> (crate::DesktopWorkspaceStore, String, Connection) {
        let s = crate::DesktopWorkspaceStore::open(root.to_owned()).unwrap();
        let w = s.ensure_local_workspace().unwrap().summary.id;
        let c = s.connection().unwrap();
        c.execute_batch("PRAGMA foreign_keys=ON").unwrap();
        create(&c).unwrap();
        (s, w, c)
    }
    async fn ready(c: &mut Connection, root: &Path, w: &str, text: &str) -> Task {
        let t = queue(c, w, "https://public.example.org/path?q=ordinary", 1).unwrap();
        process(
            c,
            root,
            w,
            &t.id,
            "https://public.example.org/path?q=ordinary",
            2,
            false,
            &mut fake(text),
        )
        .await
        .unwrap()
    }
    #[tokio::test]
    async fn web_tasks_export_roundtrip_preserves_safe_kind_and_formal_content_with_new_id() {
        let dir = tempfile::tempdir().unwrap();
        let (store, w, mut c) = store(dir.path());
        crate::desktop_markdown_knowledge_v1::create(&c).unwrap();
        crate::desktop_json_knowledge_tasks_v1::create(&c).unwrap();
        let task = ready(&mut c, dir.path(), &w, "Original webpage text").await;
        let mut candidate = task.items[0].candidate.clone();
        candidate.body = "Reviewed webpage text".into();
        let args = Confirm { workspace_id:w.clone(), task_id:task.id.clone(), generation:task.generation, ordinal:0, candidate, project_id:None };
        let receipt = store.mutate_domain_with_commit(confirmation_args(&c,&args).unwrap(), |tx,r| commit_confirmation(tx,&args,r,3)).unwrap();
        let prepared = crate::desktop_json_knowledge_export_v1::prepare(&mut c,&w,&[receipt.object_id.clone()],"2026-10-03T00:00:00Z").unwrap();
        let doc = crate::desktop_json_knowledge_v1::parse(&prepared.bytes).unwrap();
        assert_eq!(doc.items[0].source.kind,"WEB_TEXT_SNAPSHOT");
        assert_eq!(doc.items[0].source.summary,"confirmed local webpage text snapshot");
        assert_eq!(doc.items[0].content.body,"Reviewed webpage text");
        let raw = String::from_utf8(prepared.bytes.clone()).unwrap();
        for private in ["public.example.org", "q=ordinary", "snapshot.html", "Original webpage text", &task.id] { assert!(!raw.contains(private)); }
        let path=dir.path().join("webpage-roundtrip.json");
        crate::desktop_json_knowledge_export_v1::write(prepared,&path).unwrap();
        let imported=crate::desktop_json_knowledge_tasks_v1::stage(&mut c,dir.path(),&w,path.to_str().unwrap(),4).unwrap();
        assert_eq!(imported.items[0].candidate.source.kind,"WEB_TEXT_SNAPSHOT");
        let confirm=crate::desktop_json_knowledge_tasks_v1::Confirm{workspace_id:w.clone(),task_id:imported.id.clone(),ordinal:0,candidate:imported.items[0].candidate.content.clone(),project_id:None};
        let copy=store.mutate_domain_with_commit(crate::desktop_json_knowledge_tasks_v1::confirmation_args(&c,&confirm).unwrap(),|tx,r|crate::desktop_json_knowledge_tasks_v1::commit_confirmation(tx,&confirm,r,5)).unwrap();
        assert_ne!(copy.object_id,receipt.object_id);
        let second=crate::desktop_json_knowledge_export_v1::prepare(&mut c,&w,&[copy.object_id.clone()],"2026-10-03T00:00:01Z").unwrap();
        let second_doc=crate::desktop_json_knowledge_v1::parse(&second.bytes).unwrap();
        assert_eq!(second_doc.items[0].source.kind,"WEB_TEXT_SNAPSHOT");
        assert_eq!(second_doc.items[0].content,doc.items[0].content);
        assert_eq!(c.query_row("SELECT COUNT(*) FROM desktop_web_knowledge_sources_v1",[],|r|r.get::<_,i64>(0)).unwrap(),1);
        drop(c);drop(store);
        let reopened=crate::DesktopWorkspaceStore::open(dir.path().to_owned()).unwrap();
        let mut c=reopened.connection().unwrap();
        let cold=crate::desktop_json_knowledge_export_v1::prepare(&mut c,&w,&[receipt.object_id,copy.object_id],"2026-10-03T00:00:02Z").unwrap();
        assert!(crate::desktop_json_knowledge_v1::parse(&cold.bytes).unwrap().items.iter().all(|i|i.source.kind=="WEB_TEXT_SNAPSHOT" && i.content.body=="Reviewed webpage text"));
    }
    #[tokio::test]
    async fn web_tasks_private_html_extract_versions_query_and_workspace_are_separate() {
        let dir = tempfile::tempdir().unwrap();
        let (_s, w, mut c) = store(dir.path());
        let t = ready(&mut c, dir.path(), &w, "Body &amp; facts").await;
        assert_eq!(t.status, "AWAITING_CONFIRMATION");
        assert_eq!(t.generation, 1);
        assert_eq!(t.items[0].candidate.body, "Body & facts");
        assert_eq!(t.requested_url, "https://public.example.org/path");
        assert!(read(&c, "other", &t.id).is_err());
        assert!(schema_valid(&c).unwrap());
        let (key,hash,text,eh):(String,String,String,String)=c.query_row("SELECT storage_key,raw_html_hash,extracted_text,extracted_hash FROM desktop_web_knowledge_assets_v1",[],|r|Ok((r.get(0)?,r.get(1)?,r.get(2)?,r.get(3)?))).unwrap();
        assert_eq!(
            crate::sha256(&fs::read(safe_asset(dir.path(), &key).unwrap()).unwrap()),
            hash
        );
        assert_eq!(crate::sha256(text.as_bytes()), eh);
        assert!(!serde_json::to_string(&t).unwrap().contains("q=ordinary"));
        assert_eq!(
            c.query_row(
                "SELECT COUNT(*) FROM domain_intents WHERE entity='knowledge'",
                [],
                |r| r.get::<_, i64>(0)
            )
            .unwrap(),
            0
        );
    }
    #[tokio::test]
    async fn web_tasks_raw_copy_precedes_failed_extraction_without_partial_items() {
        let dir = tempfile::tempdir().unwrap();
        let (_s, w, mut c) = store(dir.path());
        let t = ready(&mut c, dir.path(), &w, "password: synthetic-only").await;
        assert_eq!(t.status, "FAILED");
        assert_eq!(t.safe_error_code.as_deref(), Some("HIGH_SENSITIVITY"));
        assert!(t.items.is_empty());
        assert_eq!(
            c.query_row(
                "SELECT COUNT(*) FROM desktop_web_knowledge_assets_v1 WHERE extracted_hash IS NULL",
                [],
                |r| r.get::<_, i64>(0)
            )
            .unwrap(),
            1
        );
        assert_eq!(
            c.query_row(
                "SELECT COUNT(*) FROM desktop_web_knowledge_items_v1",
                [],
                |r| r.get::<_, i64>(0)
            )
            .unwrap(),
            0
        );
    }
    #[tokio::test]
    async fn web_tasks_recovery_requires_fresh_url_and_retains_generations() {
        let dir = tempfile::tempdir().unwrap();
        let (_s, w, mut c) = store(dir.path());
        let t = ready(&mut c, dir.path(), &w, "First retained body").await;
        c.execute(
            "UPDATE desktop_web_knowledge_tasks_v1 SET status='EXTRACTING' WHERE id=?1",
            [&t.id],
        )
        .unwrap();
        assert_eq!(recover_interrupted(&c, 3).unwrap(), 1);
        assert!(read(&c, &w, &t.id).unwrap().items.is_empty());
        let mut network = fake("New body");
        assert!(
            process(&mut c, dir.path(), &w, &t.id, "", 4, true, &mut network)
                .await
                .is_err()
        );
        assert_eq!(network.calls, 0);
        let next = process(
            &mut c,
            dir.path(),
            &w,
            &t.id,
            "https://next.example.org/new?q=new",
            5,
            true,
            &mut network,
        )
        .await
        .unwrap();
        assert_eq!(next.generation, 2);
        assert_eq!(next.retry_count, 1);
        assert_eq!(next.items[0].candidate.body, "New body");
        assert!(decide(&mut c, &w, &t.id, 1, Some(0), 6).is_err());
        assert_eq!(
            c.query_row(
                "SELECT COUNT(*) FROM desktop_web_knowledge_assets_v1",
                [],
                |r| r.get::<_, i64>(0)
            )
            .unwrap(),
            2
        );
        assert_eq!(
            c.query_row(
                "SELECT COUNT(*) FROM desktop_web_knowledge_items_v1",
                [],
                |r| r.get::<_, i64>(0)
            )
            .unwrap(),
            2
        );
        let done = decide(&mut c, &w, &t.id, 2, Some(0), 6).unwrap();
        assert_eq!(done.status, "COMPLETED");
    }
    #[tokio::test]
    async fn web_tasks_cross_connection_cancellation_never_publishes_late_reply() {
        let dir = tempfile::tempdir().unwrap();
        let (s, w, mut c) = store(dir.path());
        let t = queue(&c, &w, "https://public.example.org/", 1).unwrap();
        let mut other = s.connection().unwrap();
        let w2 = w.clone();
        let id = t.id.clone();
        let thread = std::thread::spawn(move || {
            std::thread::sleep(std::time::Duration::from_millis(10));
            decide(&mut other, &w2, &id, 1, None, 3).unwrap();
        });
        let mut network = fake("late body");
        network.delay = std::time::Duration::from_millis(100);
        let result = process(
            &mut c,
            dir.path(),
            &w,
            &t.id,
            "https://public.example.org/",
            2,
            false,
            &mut network,
        )
        .await
        .unwrap();
        thread.join().unwrap();
        assert_eq!(result.status, "CANCELLED");
        assert!(result.items.is_empty());
        assert_eq!(
            c.query_row(
                "SELECT COUNT(*) FROM desktop_web_knowledge_assets_v1",
                [],
                |r| r.get::<_, i64>(0)
            )
            .unwrap(),
            0
        );
    }
    #[tokio::test]
    async fn web_tasks_atomic_confirmation_replay_distinct_items_source_and_cold_reopen() {
        let dir = tempfile::tempdir().unwrap();
        let (s, w, mut c) = store(dir.path());
        let t = ready(&mut c, dir.path(), &w, &"你".repeat(13_000)).await;
        assert_eq!(t.items.len(), 2);
        let mut ids = vec![];
        for item in &t.items {
            let mut a = Confirm {
                workspace_id: w.clone(),
                task_id: t.id.clone(),
                generation: t.generation,
                ordinal: item.ordinal,
                candidate: item.candidate.clone(),
                project_id: None,
            };
            a.candidate.body = format!("Reviewed {}", item.ordinal);
            if item.ordinal == 0 {
                c.execute_batch("CREATE TRIGGER reject_web_source BEFORE INSERT ON desktop_web_knowledge_sources_v1 BEGIN SELECT RAISE(ABORT,'synthetic rollback'); END;").unwrap();
                let args = confirmation_args(&c, &a).unwrap();
                assert!(s
                    .mutate_domain_with_commit(args, |tx, r| commit_confirmation(tx, &a, r, 3))
                    .is_err());
                assert_eq!(
                    c.query_row(
                        "SELECT COUNT(*) FROM domain_intents WHERE entity='knowledge'",
                        [],
                        |r| r.get::<_, i64>(0)
                    )
                    .unwrap(),
                    0
                );
                assert_eq!(
                    read(&c, &w, &t.id).unwrap().items[0].status,
                    "PENDING_CONFIRMATION"
                );
                c.execute("DROP TRIGGER reject_web_source", []).unwrap();
            }
            let receipt = s
                .mutate_domain_with_commit(confirmation_args(&c, &a).unwrap(), |tx, r| {
                    commit_confirmation(tx, &a, r, 4)
                })
                .unwrap();
            let replay = s
                .mutate_domain_with_commit(confirmation_args(&c, &a).unwrap(), |tx, r| {
                    commit_confirmation(tx, &a, r, 5)
                })
                .unwrap();
            assert!(replay.replayed);
            assert_eq!(receipt.object_id, replay.object_id);
            ids.push(receipt.object_id);
            a.candidate.body = "changed replay".into();
            assert!(s
                .mutate_domain_with_commit(confirmation_args(&c, &a).unwrap(), |tx, r| {
                    commit_confirmation(tx, &a, r, 6)
                })
                .is_err());
            a.generation = 99;
            assert!(confirmation_args(&c, &a).is_err());
        }
        assert_ne!(ids[0], ids[1]);
        assert_eq!(read(&c, &w, &t.id).unwrap().status, "COMPLETED");
        assert_eq!(c.query_row("SELECT COUNT(*) FROM desktop_web_knowledge_sources_v1 WHERE adapter_version=1 AND extraction_version=1 AND candidate_version=1",[],|r|r.get::<_,i64>(0)).unwrap(),2);
        drop(c);
        drop(s);
        let s = crate::DesktopWorkspaceStore::open(dir.path().to_owned()).unwrap();
        let c = s.connection().unwrap();
        assert_eq!(read(&c, &w, &t.id).unwrap().items.len(), 2);
        assert!(c
            .prepare("PRAGMA foreign_key_check")
            .unwrap()
            .query([])
            .unwrap()
            .next()
            .unwrap()
            .is_none());
    }
    #[tokio::test]
    async fn web_tasks_secret_query_missing_workspace_duplicate_start_and_failed_retry_are_guarded()
    {
        let dir = tempfile::tempdir().unwrap();
        let (_s, w, mut c) = store(dir.path());
        assert!(queue(&c, &w, "https://public.example.org/?token=synthetic", 1).is_err());
        assert!(queue(&c, "missing", "https://public.example.org/", 1).is_err());
        let t = queue(&c, &w, "https://public.example.org/", 1).unwrap();
        let mut failed = fake("body");
        failed.fail = true;
        let r = process(
            &mut c,
            dir.path(),
            &w,
            &t.id,
            "https://public.example.org/",
            2,
            false,
            &mut failed,
        )
        .await
        .unwrap();
        assert_eq!(r.status, "FAILED");
        assert_eq!(r.safe_error_code.as_deref(), Some("NETWORK"));
        let mut ok = fake("body");
        assert!(process(
            &mut c,
            dir.path(),
            &w,
            &t.id,
            "https://public.example.org/",
            3,
            false,
            &mut ok
        )
        .await
        .is_err());
        assert_eq!(ok.calls, 0);
        let ready = process(
            &mut c,
            dir.path(),
            &w,
            &t.id,
            "https://public.example.org/",
            4,
            true,
            &mut ok,
        )
        .await
        .unwrap();
        assert_eq!(ready.generation, 2);
        assert!(process(
            &mut c,
            dir.path(),
            &w,
            &t.id,
            "https://public.example.org/",
            5,
            true,
            &mut ok
        )
        .await
        .is_err());
        assert_eq!(ok.calls, 2);
    }
}
