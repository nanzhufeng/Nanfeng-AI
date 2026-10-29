//! P4L independent private JSON tasks and atomic formal Knowledge source owner.
use rusqlite::{params, Connection, OptionalExtension, Transaction};
use serde::{Deserialize, Serialize};
use std::{fs, io::{Read, Write}, path::Path};
use crate::desktop_json_knowledge_v1::{parse, Candidate, MAX_BYTES};
use crate::desktop_markdown_knowledge_v1::{Candidate as Content, validate_candidate};

pub(crate) fn create(connection: &Connection) -> Result<(), String> {
    connection.execute_batch("CREATE TABLE desktop_json_knowledge_tasks_v1(
        id TEXT PRIMARY KEY NOT NULL, workspace_id TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
        status TEXT NOT NULL CHECK(status IN ('SELECTED','AWAITING_CONFIRMATION','PARTIALLY_COMPLETED','COMPLETED','FAILED','CANCELLED')),
        storage_key TEXT, display_name TEXT NOT NULL, byte_count INTEGER, source_hash TEXT,
        exported_at TEXT, safe_error_code TEXT, retry_count INTEGER NOT NULL DEFAULT 0, created_at_ms INTEGER NOT NULL, updated_at_ms INTEGER NOT NULL);
        CREATE TABLE desktop_json_knowledge_items_v1(
        task_id TEXT NOT NULL REFERENCES desktop_json_knowledge_tasks_v1(id) ON DELETE CASCADE, ordinal INTEGER NOT NULL,
        candidate_json TEXT NOT NULL, status TEXT NOT NULL CHECK(status IN ('PENDING_CONFIRMATION','CONFIRMED','SKIPPED')),
        knowledge_id TEXT, PRIMARY KEY(task_id,ordinal));
        CREATE TABLE desktop_json_knowledge_sources_v1(
        workspace_id TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE, knowledge_id TEXT NOT NULL,
        task_id TEXT NOT NULL, ordinal INTEGER NOT NULL, adapter_version INTEGER NOT NULL,
        source_hash TEXT NOT NULL, confirmed_content_hash TEXT NOT NULL,
        PRIMARY KEY(workspace_id,knowledge_id), UNIQUE(task_id,ordinal),
        FOREIGN KEY(task_id,ordinal) REFERENCES desktop_json_knowledge_items_v1(task_id,ordinal) ON DELETE CASCADE);
        CREATE INDEX desktop_json_knowledge_tasks_owner_v1 ON desktop_json_knowledge_tasks_v1(workspace_id,created_at_ms DESC);")
        .map_err(|_| "JSON_KNOWLEDGE_SCHEMA_CREATE".into())
}

pub(crate) fn schema_valid(connection: &Connection) -> rusqlite::Result<bool> {
    for (table, column, ty) in [("desktop_json_knowledge_tasks_v1", "workspace_id", "TEXT"), ("desktop_json_knowledge_tasks_v1", "exported_at", "TEXT"), ("desktop_json_knowledge_items_v1", "candidate_json", "TEXT"), ("desktop_json_knowledge_sources_v1", "source_hash", "TEXT")] {
        let found: Option<String> = connection.query_row(&format!("SELECT type FROM pragma_table_info('{table}') WHERE name=?1"), [column], |r|r.get(0)).optional()?;
        if found.as_deref() != Some(ty) { return Ok(false); }
    }
    Ok(true)
}

pub(crate) fn migrate_committed_area(connection: &mut Connection, expected: &str) -> Result<(), String> {
    use crate::desktop_schema_source::{classify, SchemaSource};
    let version = connection.pragma_query_value(None, "user_version", |r|r.get(0)).map_err(|_| "JSON_KNOWLEDGE_SCHEMA_READ")?;
    match classify(connection, version).map_err(|_| "JSON_KNOWLEDGE_SCHEMA_READ")? {
        SchemaSource::Common54 { area, split_done: true } | SchemaSource::Common55 { area, split_done: true } | SchemaSource::Common56 { area, split_done: true } | SchemaSource::Common57 { area, split_done: true } | SchemaSource::Common58 { area, split_done: true } | SchemaSource::Common59 { area, split_done: true } if area == expected => {
            connection.execute("UPDATE desktop_json_knowledge_tasks_v1 SET status='FAILED',safe_error_code='INTERRUPTED' WHERE status='SELECTED'",[]).map_err(|_|"JSON_KNOWLEDGE_RECOVERY_WRITE")?;
            return Ok(());
        },
        SchemaSource::Common53 { area, split_done: true } if area == expected => {},
        _ => return Err("JSON_KNOWLEDGE_SCHEMA_SOURCE_REJECTED".into()),
    }
    let tx = connection.transaction().map_err(|_| "JSON_KNOWLEDGE_SCHEMA_TRANSACTION")?;
    create(&tx)?;
    tx.pragma_update(None, "user_version", 54).map_err(|_| "JSON_KNOWLEDGE_SCHEMA_VERSION")?;
    if !matches!(classify(&tx,54).map_err(|_|"JSON_KNOWLEDGE_SCHEMA_READ")?,SchemaSource::Common54 {area,split_done:true} | SchemaSource::Common55 {area,split_done:true} | SchemaSource::Common56 {area,split_done:true} | SchemaSource::Common57 {area,split_done:true} | SchemaSource::Common58 {area,split_done:true} | SchemaSource::Common59 {area,split_done:true} if area==expected) { return Err("JSON_KNOWLEDGE_SCHEMA_TARGET_REJECTED".into()); }
    tx.commit().map_err(|_| "JSON_KNOWLEDGE_SCHEMA_COMMIT".into())
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct Item { pub ordinal: i64, pub candidate: Candidate, pub status: String, pub knowledge_id: Option<String> }
#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct Task {
    pub id: String, pub workspace_id: String, pub status: String, pub display_name: String,
    pub source_hash: Option<String>, pub exported_at: Option<String>, pub safe_error_code: Option<String>, pub retry_count: i64, pub items: Vec<Item>,
}

pub(crate) fn read(connection: &Connection, workspace: &str, id: &str) -> Result<Task, String> {
    let mut task = connection.query_row("SELECT id,workspace_id,status,display_name,source_hash,safe_error_code,retry_count,exported_at FROM desktop_json_knowledge_tasks_v1 WHERE id=?1 AND workspace_id=?2",params![id,workspace],|r|Ok(Task{id:r.get(0)?,workspace_id:r.get(1)?,status:r.get(2)?,display_name:r.get(3)?,source_hash:r.get(4)?,safe_error_code:r.get(5)?,retry_count:r.get(6)?,exported_at:r.get(7)?,items:vec![]})).map_err(|_| "JSON_KNOWLEDGE_TASK_NOT_IN_WORKSPACE")?;
    let mut stmt = connection.prepare("SELECT ordinal,candidate_json,status,knowledge_id FROM desktop_json_knowledge_items_v1 WHERE task_id=?1 ORDER BY ordinal").map_err(|_|"JSON_KNOWLEDGE_ITEMS_READ")?;
    let rows = stmt.query_map([id], |r|Ok((r.get::<_,i64>(0)?,r.get::<_,String>(1)?,r.get::<_,String>(2)?,r.get::<_,Option<String>>(3)?))).map_err(|_|"JSON_KNOWLEDGE_ITEMS_READ")?;
    for row in rows { let (ordinal,candidate,status,knowledge_id)=row.map_err(|_|"JSON_KNOWLEDGE_ITEMS_READ")?; task.items.push(Item{ordinal,candidate:serde_json::from_str(&candidate).map_err(|_|"JSON_KNOWLEDGE_CANDIDATE_INVALID")?,status,knowledge_id}); }
    Ok(task)
}

pub(crate) fn latest(connection: &Connection, workspace: &str) -> Result<Option<Task>, String> {
    let id: Option<String> = connection.query_row("SELECT id FROM desktop_json_knowledge_tasks_v1 WHERE workspace_id=?1 ORDER BY created_at_ms DESC,rowid DESC LIMIT 1",[workspace],|r|r.get(0)).optional().map_err(|_|"JSON_KNOWLEDGE_TASK_READ")?;
    id.map(|id|read(connection,workspace,&id)).transpose()
}

#[derive(Debug,Serialize)]
#[serde(rename_all="camelCase")]
pub(crate) struct TaskSummary { pub id:String,pub display_name:String,pub status:String }
#[derive(Debug,Serialize)]
#[serde(rename_all="camelCase")]
pub(crate) struct TaskPage { pub tasks:Vec<TaskSummary>,pub has_more:bool }
pub(crate) fn list(connection:&Connection,workspace:&str,offset:i64)->Result<TaskPage,String> {
    if !(0..=1_000_000).contains(&offset) { return Err("JSON_KNOWLEDGE_TASK_PAGE_INVALID".into()); }
    let mut stmt=connection.prepare("SELECT id,display_name,status FROM desktop_json_knowledge_tasks_v1 WHERE workspace_id=?1 ORDER BY created_at_ms DESC,rowid DESC LIMIT 51 OFFSET ?2").map_err(|_|"JSON_KNOWLEDGE_TASK_LIST")?;
    let mut tasks=stmt.query_map(params![workspace,offset],|r|Ok(TaskSummary{id:r.get(0)?,display_name:r.get(1)?,status:r.get(2)?})).map_err(|_|"JSON_KNOWLEDGE_TASK_LIST")?.collect::<rusqlite::Result<Vec<_>>>().map_err(|_|"JSON_KNOWLEDGE_TASK_LIST")?;
    let has_more=tasks.len()>50;tasks.truncate(50);Ok(TaskPage{tasks,has_more})
}

fn safe_asset(root: &Path, key: &str) -> Result<std::path::PathBuf, String> {
    if !key.starts_with("json-knowledge-import/v1/") || Path::new(key).components().any(|c|!matches!(c,std::path::Component::Normal(_))) { return Err("JSON_KNOWLEDGE_ASSET_KEY".into()); }
    let path=root.join(key);
    for ancestor in path.ancestors().take_while(|p|p.starts_with(root)) {
        if let Ok(m)=fs::symlink_metadata(ancestor) {
            #[cfg(windows)] { use std::os::windows::fs::MetadataExt; if m.file_attributes() & 0x400 != 0 { return Err("JSON_KNOWLEDGE_ASSET_LINK".into()); } }
            if m.file_type().is_symlink() { return Err("JSON_KNOWLEDGE_ASSET_LINK".into()); }
        }
    }
    Ok(path)
}

pub(crate) fn stage(connection: &mut Connection, root: &Path, workspace: &str, selected: &str, now: i64) -> Result<Task, String> {
    let exists:bool=connection.query_row("SELECT EXISTS(SELECT 1 FROM workspaces WHERE id=?1)",[workspace],|r|r.get(0)).map_err(|_|"JSON_KNOWLEDGE_WORKSPACE_READ")?;
    if !exists { return Err("JSON_KNOWLEDGE_WORKSPACE_MISSING".into()); }
    let source=Path::new(selected);
    if !source.extension().and_then(|v|v.to_str()).is_some_and(|v|v.eq_ignore_ascii_case("json")) { return Err("NOT_JSON".into()); }
    let display=source.file_name().and_then(|v|v.to_str()).unwrap_or("Knowledge JSON").chars().filter(|c|!c.is_control()).take(160).collect::<String>();
    let id=crate::ordinary_chat_random_id("json-knowledge-task")?;
    connection.execute("INSERT INTO desktop_json_knowledge_tasks_v1(id,workspace_id,status,display_name,created_at_ms,updated_at_ms) VALUES(?1,?2,'SELECTED',?3,?4,?4)",params![id,workspace,display,now]).map_err(|_|"JSON_KNOWLEDGE_TASK_CREATE")?;
    let copied=(|| -> Result<(),String> {
        if !crate::desktop_legacy_area_split::plain_file_if_present(source)? { return Err("MISSING_SOURCE".into()); }
        let mut file=fs::File::open(source).map_err(|_|"SOURCE_READ_FAILED")?;
        if file.metadata().map_err(|_|"SOURCE_READ_FAILED")?.len()>MAX_BYTES as u64 { return Err("TOO_LARGE".into()); }
        let mut bytes=Vec::new();Read::by_ref(&mut file).take(MAX_BYTES as u64+1).read_to_end(&mut bytes).map_err(|_|"SOURCE_READ_FAILED")?;
        if bytes.len()>MAX_BYTES { return Err("TOO_LARGE".into()); }
        let key=format!("json-knowledge-import/v1/{id}.json");let path=safe_asset(root,&key)?;
        fs::create_dir_all(path.parent().ok_or("PRIVATE_COPY_FAILED")?).map_err(|_|"PRIVATE_COPY_FAILED")?;
        let part=path.with_extension("part");let mut target=fs::OpenOptions::new().write(true).create_new(true).open(&part).map_err(|_|"PRIVATE_COPY_FAILED")?;
        target.write_all(&bytes).and_then(|_|target.sync_all()).map_err(|_|"PRIVATE_COPY_FAILED")?;drop(target);
        fs::rename(&part,&path).map_err(|_|"PRIVATE_COPY_FAILED")?;
        let hash=crate::sha256(&bytes);
        connection.execute("UPDATE desktop_json_knowledge_tasks_v1 SET storage_key=?2,byte_count=?3,source_hash=?4 WHERE id=?1",params![id,key,bytes.len() as i64,hash]).map_err(|_|"PRIVATE_COPY_RECEIPT_FAILED")?;
        parse_into_task(connection,&id,&bytes,now)
    })();
    if let Err(code)=copied { connection.execute("UPDATE desktop_json_knowledge_tasks_v1 SET status='FAILED',safe_error_code=?2,updated_at_ms=?3 WHERE id=?1",params![id, safe_failure(&code),now]).map_err(|_|"JSON_KNOWLEDGE_TASK_FAILURE_WRITE")?; }
    read(connection,workspace,&id)
}

fn safe_failure(code:&str)->&str {
    match code { "TOO_LARGE"|"INVALID_UTF8"|"MALFORMED_JSON"|"DUPLICATE_KEY"|"TOO_DEEP"|"UNKNOWN_FORMAT"|"UNKNOWN_VERSION"|"UNKNOWN_FIELD"|"TYPE_MISMATCH"|"EMPTY_ITEMS"|"EMPTY_ITEM"|"ITEM_INVALID"|"ITEM_TOO_LARGE"|"TOO_MANY_ITEMS"|"STRING_TOO_LONG"|"UNSAFE_METADATA"|"HIGH_SENSITIVITY"|"INVALID_TAG"|"JSON_KNOWLEDGE_ASSET_LINK"|"MISSING_SOURCE"=>code,_=>"PRIVATE_COPY_FAILED" }
}

fn parse_into_task(connection:&mut Connection,id:&str,bytes:&[u8],now:i64)->Result<(),String> {
    let document=parse(bytes)?;let items=&document.items;let tx=connection.transaction().map_err(|_|"JSON_KNOWLEDGE_PARSE_TRANSACTION")?;
    tx.execute("DELETE FROM desktop_json_knowledge_items_v1 WHERE task_id=?1",[id]).map_err(|_|"JSON_KNOWLEDGE_ITEMS_WRITE")?;
    for (ordinal,item) in items.iter().enumerate() { tx.execute("INSERT INTO desktop_json_knowledge_items_v1(task_id,ordinal,candidate_json,status) VALUES(?1,?2,?3,'PENDING_CONFIRMATION')",params![id,ordinal as i64,serde_json::to_string(item).map_err(|_|"JSON_KNOWLEDGE_CANDIDATE_INVALID")?]).map_err(|_|"JSON_KNOWLEDGE_ITEMS_WRITE")?; }
    tx.execute("UPDATE desktop_json_knowledge_tasks_v1 SET status='AWAITING_CONFIRMATION',safe_error_code=NULL,updated_at_ms=?2,exported_at=?3 WHERE id=?1",params![id,now,document.exported_at]).map_err(|_|"JSON_KNOWLEDGE_TASK_WRITE")?;
    tx.commit().map_err(|_|"JSON_KNOWLEDGE_PARSE_COMMIT".into())
}

pub(crate) fn retry(connection:&mut Connection,root:&Path,workspace:&str,id:&str,now:i64)->Result<Task,String> {
    let task=read(connection,workspace,id)?;
    if task.status!="FAILED" { return Err("JSON_KNOWLEDGE_RETRY_NOT_FAILED".into()); }
    let (key,size,hash):(String,i64,String)=connection.query_row("SELECT storage_key,byte_count,source_hash FROM desktop_json_knowledge_tasks_v1 WHERE id=?1",[id],|r|Ok((r.get(0)?,r.get(1)?,r.get(2)?))).map_err(|_|"JSON_KNOWLEDGE_PRIVATE_ASSET_MISSING")?;
    let path=safe_asset(root,&key)?;let file=fs::File::open(path).map_err(|_|"JSON_KNOWLEDGE_PRIVATE_ASSET_MISSING")?;let mut bytes=Vec::new();file.take(MAX_BYTES as u64+1).read_to_end(&mut bytes).map_err(|_|"JSON_KNOWLEDGE_PRIVATE_ASSET_MISSING")?;
    if bytes.len()>MAX_BYTES || bytes.len() as i64!=size || crate::sha256(&bytes)!=hash { return Err("JSON_KNOWLEDGE_PRIVATE_ASSET_CHANGED".into()); }
    connection.execute("UPDATE desktop_json_knowledge_tasks_v1 SET retry_count=retry_count+1 WHERE id=?1",[id]).map_err(|_|"JSON_KNOWLEDGE_RETRY_WRITE")?;
    if let Err(code)=parse_into_task(connection,id,&bytes,now) { connection.execute("UPDATE desktop_json_knowledge_tasks_v1 SET safe_error_code=?2,updated_at_ms=?3 WHERE id=?1",params![id,safe_failure(&code),now]).map_err(|_|"JSON_KNOWLEDGE_TASK_FAILURE_WRITE")?; }
    read(connection,workspace,id)
}

fn finish(tx:&Transaction<'_>,id:&str,now:i64)->Result<(),String> {
    tx.execute("UPDATE desktop_json_knowledge_tasks_v1 SET status=CASE WHEN EXISTS(SELECT 1 FROM desktop_json_knowledge_items_v1 WHERE task_id=?1 AND status='PENDING_CONFIRMATION') THEN 'PARTIALLY_COMPLETED' ELSE 'COMPLETED' END,updated_at_ms=?2 WHERE id=?1",params![id,now]).map_err(|_|"JSON_KNOWLEDGE_TASK_WRITE")?;Ok(())
}

pub(crate) fn decide(connection:&mut Connection,workspace:&str,id:&str,ordinal:Option<i64>,now:i64)->Result<Task,String> {
    let tx=connection.transaction().map_err(|_|"JSON_KNOWLEDGE_DECISION_TRANSACTION")?;let task=read(&tx,workspace,id)?;
    if matches!(task.status.as_str(),"CANCELLED"|"COMPLETED") { return Err("JSON_KNOWLEDGE_TASK_TERMINAL".into()); }
    if let Some(ordinal)=ordinal {
        let count=tx.execute("UPDATE desktop_json_knowledge_items_v1 SET status='SKIPPED' WHERE task_id=?1 AND ordinal=?2 AND status='PENDING_CONFIRMATION'",params![id,ordinal]).map_err(|_|"JSON_KNOWLEDGE_ITEM_WRITE")?;
        if count!=1 { return Err("JSON_KNOWLEDGE_ITEM_NOT_PENDING".into()); }finish(&tx,id,now)?;
    } else { tx.execute("UPDATE desktop_json_knowledge_tasks_v1 SET status='CANCELLED',updated_at_ms=?2 WHERE id=?1",params![id,now]).map_err(|_|"JSON_KNOWLEDGE_TASK_WRITE")?; }
    tx.commit().map_err(|_|"JSON_KNOWLEDGE_DECISION_COMMIT")?;read(connection,workspace,id)
}

#[derive(Debug, Deserialize)]
#[serde(rename_all="camelCase",deny_unknown_fields)]
pub(crate) struct Confirm { pub workspace_id:String,pub task_id:String,pub ordinal:i64,pub candidate:Content,pub project_id:Option<String> }

pub(crate) fn confirmation_args(connection:&Connection,args:&Confirm)->Result<crate::DomainMutationArgs,String> {
    let task=read(connection,&args.workspace_id,&args.task_id)?;
    if matches!(task.status.as_str(),"CANCELLED"|"FAILED"|"SELECTED") { return Err("JSON_KNOWLEDGE_TASK_NOT_CONFIRMABLE".into()); }
    let item=task.items.iter().find(|i|i.ordinal==args.ordinal).ok_or("JSON_KNOWLEDGE_ITEM_MISSING")?;
    if item.status=="SKIPPED" { return Err("JSON_KNOWLEDGE_ITEM_SKIPPED".into()); }
    let candidate=validate_candidate(args.candidate.clone())?;
    // The domain owner derives object IDs from the first 32 intent characters.
    // Put a digest of the complete task/item identity at the front, rather than
    // a long shared task prefix that would alias every item in one batch.
    // Previously confirmed items retain their actually recorded creation intent.
    let intent_id = if item.status=="CONFIRMED" {
        connection.query_row("SELECT intent_id FROM domain_intents WHERE workspace_id=?1 AND entity='knowledge' AND action='create' AND object_id=?2",params![args.workspace_id,item.knowledge_id.as_deref().ok_or("JSON_KNOWLEDGE_CONFIRMATION_RECORD_MISSING")?],|r|r.get::<_,String>(0)).map_err(|_|"JSON_KNOWLEDGE_CONFIRMATION_RECORD_MISSING")?
    } else {
        let hash=crate::sha256(format!("nfai-json-knowledge-confirm-v1:{}:{}",args.task_id,args.ordinal).as_bytes());
        format!("jsk-{}",&hash[..48])
    };
    Ok(crate::DomainMutationArgs{intent_id,workspace_id:args.workspace_id.clone(),entity:"knowledge".into(),action:"create".into(),object_id:None,expected_revision:None,fields:serde_json::json!({"title":candidate.title,"body":candidate.body,"tags":candidate.tags,"scope":if args.project_id.is_some(){"PROJECT"}else{"GLOBAL"},"projectId":args.project_id})})
}

pub(crate) fn commit_confirmation(tx:&Transaction<'_>,args:&Confirm,receipt:&crate::MutationReceipt,now:i64)->Result<(),String> {
    let task=read(tx,&args.workspace_id,&args.task_id)?;
    if matches!(task.status.as_str(),"CANCELLED"|"FAILED"|"SELECTED") { return Err("JSON_KNOWLEDGE_TASK_NOT_CONFIRMABLE".into()); }
    let item=task.items.iter().find(|i|i.ordinal==args.ordinal).ok_or("JSON_KNOWLEDGE_ITEM_MISSING")?;
    let candidate=validate_candidate(args.candidate.clone())?;
    let hash=crate::sha256(crate::canonical_json(&serde_json::json!({"candidate":candidate,"projectId":args.project_id}))?.as_bytes());
    if item.status=="CONFIRMED" {
        if item.knowledge_id.as_deref()!=Some(&receipt.object_id) || !receipt.replayed { return Err("JSON_KNOWLEDGE_CONFIRMATION_CONFLICT".into()); }
        let recorded:String=tx.query_row("SELECT confirmed_content_hash FROM desktop_json_knowledge_sources_v1 WHERE workspace_id=?1 AND knowledge_id=?2",params![args.workspace_id,receipt.object_id],|r|r.get(0)).map_err(|_|"JSON_KNOWLEDGE_SOURCE_MISSING")?;
        if recorded!=hash { return Err("JSON_KNOWLEDGE_CONFIRMATION_CONFLICT".into()); }
        return Ok(());
    }
    if item.status!="PENDING_CONFIRMATION" { return Err("JSON_KNOWLEDGE_ITEM_NOT_PENDING".into()); }
    tx.execute("UPDATE desktop_json_knowledge_items_v1 SET status='CONFIRMED',knowledge_id=?3 WHERE task_id=?1 AND ordinal=?2",params![args.task_id,args.ordinal,receipt.object_id]).map_err(|_|"JSON_KNOWLEDGE_ITEM_WRITE")?;
    tx.execute("INSERT INTO desktop_json_knowledge_sources_v1(workspace_id,knowledge_id,task_id,ordinal,adapter_version,source_hash,confirmed_content_hash) VALUES(?1,?2,?3,?4,1,?5,?6)",params![args.workspace_id,receipt.object_id,args.task_id,args.ordinal,task.source_hash.ok_or("JSON_KNOWLEDGE_SOURCE_MISSING")?,hash]).map_err(|_|"JSON_KNOWLEDGE_SOURCE_WRITE")?;
    finish(tx,&args.task_id,now)
}

#[cfg(test)]
pub(crate) mod tests {
    use super::*;
    pub(crate) fn previous(area:&str)->Connection {
        let mut c=Connection::open_in_memory().unwrap();
        c.execute_batch("CREATE TABLE workspaces(id TEXT PRIMARY KEY); INSERT INTO workspaces VALUES('synthetic-workspace');
            CREATE TABLE desktop_conversation_drafts_v1(workspace_id TEXT,conversation_key TEXT,text TEXT,updated_at_ms INTEGER,model_id TEXT,tone_override TEXT,web_search_override INTEGER);
            INSERT INTO desktop_conversation_drafts_v1 VALUES('synthetic-workspace','','preserve old draft',1,NULL,NULL,0);
            CREATE TABLE desktop_data_area_v1(id INTEGER PRIMARY KEY,area TEXT,split_done INTEGER);
            CREATE TABLE domain_intents(request_fingerprint TEXT);
            CREATE TABLE chatgpt_import_tasks(workspace_id TEXT);CREATE TABLE chatgpt_import_receipts(workspace_id TEXT);
            CREATE TABLE claude_import_tasks(workspace_id TEXT);CREATE TABLE claude_import_receipts(workspace_id TEXT);
            CREATE TABLE nanfeng_knowledge_import_tasks(workspace_id TEXT);CREATE TABLE nanfeng_knowledge_import_receipts(workspace_id TEXT);
            CREATE TABLE p6k_zip_import_attachment_summary(id INTEGER);CREATE TABLE desktop_app_settings(memory_retrieval_enabled INTEGER);
            PRAGMA user_version=52;").unwrap();
        crate::desktop_ocr_attempt_schema::create(&c).unwrap();c.execute("INSERT INTO desktop_data_area_v1 VALUES(1,?1,1)",[area]).unwrap();crate::desktop_markdown_knowledge_v1::migrate_committed_area(&mut c,area).unwrap();c
    }

    fn payload() -> Vec<u8> {
        serde_json::to_vec(&serde_json::json!({"format":"nfai.knowledge.json","schemaVersion":"1","exportedAt":"2026-08-13T00:00:00Z","items":[{"id":"old-alpha","title":"Alpha","body":"original body","tags":["local"],"scope":"PROJECT","projectRef":"external-project","revision":"4","hash":"a".repeat(64),"source":{"kind":"LOCAL_KNOWLEDGE","summary":"confirmed local knowledge"}}]})).unwrap()
    }
    #[test] fn schema53_both_areas_upgrade_preserve_markdown_and_drafts() {
        for area in ["CHAT","WORK"] {
            let mut c=previous(area);let dir=tempfile::tempdir().unwrap();let path=dir.path().join("existing.md");fs::write(&path,"# Preserve\nprior body").unwrap();
            let old=crate::desktop_markdown_knowledge_v1::stage(&mut c,dir.path(),"synthetic-workspace",path.to_str().unwrap(),1).unwrap();
            assert!(migrate_committed_area(&mut c,if area=="CHAT"{"WORK"}else{"CHAT"}).is_err());assert_eq!(c.pragma_query_value(None,"user_version",|r|r.get::<_,i64>(0)).unwrap(),53);
            migrate_committed_area(&mut c,area).unwrap();migrate_committed_area(&mut c,area).unwrap();assert_eq!(c.pragma_query_value(None,"user_version",|r|r.get::<_,i64>(0)).unwrap(),54);
            assert_eq!(c.query_row("SELECT text FROM desktop_conversation_drafts_v1",[],|r|r.get::<_,String>(0)).unwrap(),"preserve old draft");
            assert_eq!(crate::desktop_markdown_knowledge_v1::read(&c,"synthetic-workspace",&old.id).unwrap().items[0].candidate.body,"prior body");
            crate::desktop_markdown_knowledge_v1::migrate_committed_area(&mut c,area).unwrap();
            c.execute("DROP TABLE desktop_json_knowledge_sources_v1",[]).unwrap();assert!(migrate_committed_area(&mut c,area).is_err());
        }
    }
    #[test] fn selected_recovery_private_hash_retry_and_explicit_decisions_are_owned() {
        let dir=tempfile::tempdir().unwrap();let path=dir.path().join("source.json");fs::write(&path,payload()).unwrap();let mut c=previous("WORK");migrate_committed_area(&mut c,"WORK").unwrap();
        let task=stage(&mut c,dir.path(),"synthetic-workspace",path.to_str().unwrap(),1).unwrap();assert_eq!(task.status,"AWAITING_CONFIRMATION");assert_eq!(task.exported_at.as_deref(),Some("2026-08-13T00:00:00Z"));assert_eq!(task.items[0].candidate.source.revision,"4");assert!(read(&c,"other",&task.id).is_err());assert!(decide(&mut c,"other",&task.id,None,2).is_err());
        c.execute("UPDATE desktop_json_knowledge_tasks_v1 SET status='SELECTED' WHERE id=?1",[&task.id]).unwrap();migrate_committed_area(&mut c,"WORK").unwrap();let failed=read(&c,"synthetic-workspace",&task.id).unwrap();assert_eq!(failed.status,"FAILED");assert_eq!(failed.safe_error_code.as_deref(),Some("INTERRUPTED"));
        let recovered=retry(&mut c,dir.path(),"synthetic-workspace",&task.id,3).unwrap();assert_eq!(recovered.retry_count,1);assert_eq!(recovered.status,"AWAITING_CONFIRMATION");assert_eq!(recovered.items[0].candidate,task.items[0].candidate);
        c.execute("UPDATE desktop_json_knowledge_tasks_v1 SET status='FAILED' WHERE id=?1",[&task.id]).unwrap();let key:String=c.query_row("SELECT storage_key FROM desktop_json_knowledge_tasks_v1 WHERE id=?1",[&task.id],|r|r.get(0)).unwrap();assert!(key.starts_with("json-knowledge-import/v1/"));fs::write(dir.path().join(key),"changed").unwrap();assert_eq!(retry(&mut c,dir.path(),"synthetic-workspace",&task.id,4).unwrap_err(),"JSON_KNOWLEDGE_PRIVATE_ASSET_CHANGED");
        let skip=stage(&mut c,dir.path(),"synthetic-workspace",path.to_str().unwrap(),5).unwrap();let skip=decide(&mut c,"synthetic-workspace",&skip.id,Some(0),6).unwrap();assert_eq!(skip.status,"COMPLETED");assert_eq!(skip.items[0].status,"SKIPPED");assert!(confirmation_args(&c,&Confirm{workspace_id:"synthetic-workspace".into(),task_id:skip.id,ordinal:0,candidate:skip.items[0].candidate.content.clone(),project_id:None}).is_err());
        let cancel=stage(&mut c,dir.path(),"synthetic-workspace",path.to_str().unwrap(),7).unwrap();let cancel=decide(&mut c,"synthetic-workspace",&cancel.id,None,8).unwrap();assert_eq!(cancel.status,"CANCELLED");assert!(retry(&mut c,dir.path(),"synthetic-workspace",&cancel.id,9).is_err());
    }
    #[test] fn strict_parse_failure_retains_private_bytes_without_candidate_or_formal_writes() {
        let dir=tempfile::tempdir().unwrap();let path=dir.path().join("bad.json");fs::write(&path,b"{\"format\":\"x\",\"format\":\"y\"}").unwrap();let mut c=previous("CHAT");migrate_committed_area(&mut c,"CHAT").unwrap();
        let task=stage(&mut c,dir.path(),"synthetic-workspace",path.to_str().unwrap(),1).unwrap();assert_eq!(task.status,"FAILED");assert_eq!(task.safe_error_code.as_deref(),Some("DUPLICATE_KEY"));assert!(task.items.is_empty());assert!(task.source_hash.is_some());assert_eq!(retry(&mut c,dir.path(),"synthetic-workspace",&task.id,2).unwrap().safe_error_code.as_deref(),Some("DUPLICATE_KEY"));
        assert_eq!(c.query_row("SELECT COUNT(*) FROM desktop_json_knowledge_sources_v1",[],|r|r.get::<_,i64>(0)).unwrap(),0);
    }
    #[test] fn history_pages_are_bounded_and_foreign_workspace_delete_is_isolated() {
        let mut c=previous("WORK");c.execute_batch("PRAGMA foreign_keys=ON; INSERT INTO workspaces VALUES('other');").unwrap();migrate_committed_area(&mut c,"WORK").unwrap();
        for n in 0..51 {c.execute("INSERT INTO desktop_json_knowledge_tasks_v1(id,workspace_id,status,display_name,created_at_ms,updated_at_ms) VALUES(?1,'synthetic-workspace','CANCELLED','synthetic.json',?2,?2)",params![format!("json-task-{n}"),n]).unwrap();}
        let page=list(&c,"synthetic-workspace",0).unwrap();assert_eq!(page.tasks.len(),50);assert!(page.has_more);assert_eq!(list(&c,"synthetic-workspace",50).unwrap().tasks.len(),1);assert!(list(&c,"other",0).unwrap().tasks.is_empty());assert!(list(&c,"synthetic-workspace",-1).is_err());
        c.execute("DELETE FROM workspaces WHERE id='synthetic-workspace'",[]).unwrap();assert!(list(&c,"synthetic-workspace",0).unwrap().tasks.is_empty());assert_eq!(c.query_row("SELECT COUNT(*) FROM workspaces WHERE id='other'",[],|r|r.get::<_,i64>(0)).unwrap(),1);
    }
}
