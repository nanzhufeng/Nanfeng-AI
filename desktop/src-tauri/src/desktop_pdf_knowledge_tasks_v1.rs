//! P4N independent private PDF tasks, page generations, and atomic formal source receipts.
use rusqlite::{params, Connection, OptionalExtension, Transaction};
use serde::{Deserialize, Serialize};
use std::{fs, io::{Read, Write}, path::Path};
use crate::desktop_pdf_text_knowledge_v1::{self as adapter, Parsed};
use crate::desktop_markdown_knowledge_v1::{Candidate as Content, validate_candidate};

pub(crate) fn create(c: &Connection) -> Result<(), String> {
    c.execute_batch("CREATE TABLE desktop_pdf_knowledge_tasks_v1(
        id TEXT PRIMARY KEY,workspace_id TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
        status TEXT NOT NULL CHECK(status IN ('SELECTED','PRIVATE_COPIED','PREFLIGHT','EXTRACTING','AWAITING_CONFIRMATION','PARTIALLY_COMPLETED','COMPLETED','FAILED','CANCELLED')),
        storage_key TEXT,display_name TEXT NOT NULL,byte_count INTEGER,source_hash TEXT,safe_error_code TEXT,
        generation INTEGER NOT NULL DEFAULT 0,extraction_version INTEGER,candidate_version INTEGER,extracted_hash TEXT,
        completed_pages INTEGER NOT NULL DEFAULT 0,total_pages INTEGER NOT NULL DEFAULT 0,
        retry_count INTEGER NOT NULL DEFAULT 0,created_at_ms INTEGER NOT NULL,updated_at_ms INTEGER NOT NULL);
        CREATE TABLE desktop_pdf_knowledge_pages_v1(
        task_id TEXT NOT NULL REFERENCES desktop_pdf_knowledge_tasks_v1(id) ON DELETE CASCADE,generation INTEGER NOT NULL,
        page_number INTEGER NOT NULL,text TEXT NOT NULL,text_hash TEXT NOT NULL,code_point_count INTEGER NOT NULL,
        extraction_version INTEGER NOT NULL,PRIMARY KEY(task_id,generation,page_number));
        CREATE TABLE desktop_pdf_knowledge_items_v1(
        task_id TEXT NOT NULL REFERENCES desktop_pdf_knowledge_tasks_v1(id) ON DELETE CASCADE,generation INTEGER NOT NULL,
        ordinal INTEGER NOT NULL,page_number INTEGER NOT NULL,candidate_json TEXT NOT NULL,candidate_hash TEXT NOT NULL,
        candidate_version INTEGER NOT NULL,status TEXT NOT NULL CHECK(status IN ('PENDING_CONFIRMATION','CONFIRMED','SKIPPED')),
        knowledge_id TEXT,PRIMARY KEY(task_id,generation,ordinal),
        FOREIGN KEY(task_id,generation,page_number) REFERENCES desktop_pdf_knowledge_pages_v1(task_id,generation,page_number));
        CREATE TABLE desktop_pdf_knowledge_sources_v1(
        workspace_id TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,knowledge_id TEXT NOT NULL,
        task_id TEXT NOT NULL,generation INTEGER NOT NULL,ordinal INTEGER NOT NULL,page_number INTEGER NOT NULL,
        adapter_version INTEGER NOT NULL,extraction_version INTEGER NOT NULL,candidate_version INTEGER NOT NULL,
        source_hash TEXT NOT NULL,page_text_hash TEXT NOT NULL,candidate_hash TEXT NOT NULL,confirmed_content_hash TEXT NOT NULL,
        PRIMARY KEY(workspace_id,knowledge_id),UNIQUE(task_id,generation,ordinal),
        FOREIGN KEY(task_id,generation,ordinal) REFERENCES desktop_pdf_knowledge_items_v1(task_id,generation,ordinal));
        CREATE INDEX desktop_pdf_knowledge_task_owner_v1 ON desktop_pdf_knowledge_tasks_v1(workspace_id,created_at_ms DESC);")
        .map_err(|_| "PDF_KNOWLEDGE_SCHEMA_CREATE".into())
}

pub(crate) fn schema_valid(c: &Connection) -> rusqlite::Result<bool> {
    for (table,column,ty) in [("tasks","workspace_id","TEXT"),("tasks","generation","INTEGER"),("tasks","completed_pages","INTEGER"),
        ("pages","text_hash","TEXT"),("pages","generation","INTEGER"),("items","candidate_json","TEXT"),("items","generation","INTEGER"),
        ("sources","page_text_hash","TEXT"),("sources","confirmed_content_hash","TEXT"),("sources","generation","INTEGER")] {
        let found:Option<String>=c.query_row(&format!("SELECT type FROM pragma_table_info('desktop_pdf_knowledge_{table}_v1') WHERE name=?1"),[column],|r|r.get(0)).optional()?;
        if found.as_deref()!=Some(ty) {return Ok(false);}
    }
    Ok(true)
}

pub(crate) fn migrate_committed_area(c: &mut Connection, expected: &str) -> Result<(),String> {
    use crate::desktop_schema_source::{classify,SchemaSource};
    let version=c.pragma_query_value(None,"user_version",|r|r.get(0)).map_err(|_|"PDF_KNOWLEDGE_SCHEMA_READ")?;
    match classify(c,version).map_err(|_|"PDF_KNOWLEDGE_SCHEMA_READ")? {
        SchemaSource::Common55{area,split_done:true} | SchemaSource::Common56{area,split_done:true} | SchemaSource::Common57{area,split_done:true} | SchemaSource::Common58{area,split_done:true} | SchemaSource::Common59{area,split_done:true} if area==expected => {
            c.execute("UPDATE desktop_pdf_knowledge_tasks_v1 SET status='FAILED',safe_error_code='INTERRUPTED' WHERE status IN ('SELECTED','PRIVATE_COPIED','PREFLIGHT','EXTRACTING')",[]).map_err(|_|"PDF_KNOWLEDGE_RECOVERY_WRITE")?;
            return Ok(());
        },
        SchemaSource::Common54{area,split_done:true} if area==expected => {},
        _=>return Err("PDF_KNOWLEDGE_SCHEMA_SOURCE_REJECTED".into()),
    }
    let tx=c.transaction().map_err(|_|"PDF_KNOWLEDGE_SCHEMA_TRANSACTION")?;create(&tx)?;
    tx.pragma_update(None,"user_version",55).map_err(|_|"PDF_KNOWLEDGE_SCHEMA_VERSION")?;
    if !matches!(classify(&tx,55).map_err(|_|"PDF_KNOWLEDGE_SCHEMA_READ")?,SchemaSource::Common55{area,split_done:true} | SchemaSource::Common56{area,split_done:true} | SchemaSource::Common57{area,split_done:true} | SchemaSource::Common58{area,split_done:true} | SchemaSource::Common59{area,split_done:true} if area==expected) {return Err("PDF_KNOWLEDGE_SCHEMA_TARGET_REJECTED".into());}
    tx.commit().map_err(|_|"PDF_KNOWLEDGE_SCHEMA_COMMIT".into())
}

#[derive(Debug,Serialize)]
#[serde(rename_all="camelCase")]
pub(crate) struct Item {pub ordinal:i64,pub page_number:i64,pub candidate:Content,pub status:String,pub knowledge_id:Option<String>}
#[derive(Debug,Serialize)]
#[serde(rename_all="camelCase")]
pub(crate) struct Task {
    pub id:String,pub workspace_id:String,pub status:String,pub display_name:String,pub source_hash:Option<String>,
    pub safe_error_code:Option<String>,pub retry_count:i64,pub generation:i64,pub completed_pages:i64,pub total_pages:i64,pub items:Vec<Item>,
}
pub(crate) fn read(c:&Connection,workspace:&str,id:&str)->Result<Task,String> {
    let mut task=c.query_row("SELECT id,workspace_id,status,display_name,source_hash,safe_error_code,retry_count,generation,completed_pages,total_pages FROM desktop_pdf_knowledge_tasks_v1 WHERE id=?1 AND workspace_id=?2",params![id,workspace],|r|Ok(Task{id:r.get(0)?,workspace_id:r.get(1)?,status:r.get(2)?,display_name:r.get(3)?,source_hash:r.get(4)?,safe_error_code:r.get(5)?,retry_count:r.get(6)?,generation:r.get(7)?,completed_pages:r.get(8)?,total_pages:r.get(9)?,items:vec![]})).map_err(|_|"PDF_KNOWLEDGE_TASK_NOT_IN_WORKSPACE")?;
    // Failed/cancelled processing has no active partial candidate projection.
    if matches!(task.status.as_str(),"AWAITING_CONFIRMATION"|"PARTIALLY_COMPLETED"|"COMPLETED") {
        let mut stmt=c.prepare("SELECT ordinal,page_number,candidate_json,status,knowledge_id FROM desktop_pdf_knowledge_items_v1 WHERE task_id=?1 AND generation=?2 ORDER BY ordinal").map_err(|_|"PDF_KNOWLEDGE_ITEMS_READ")?;
        let rows=stmt.query_map(params![id,task.generation],|r|Ok((r.get::<_,i64>(0)?,r.get::<_,i64>(1)?,r.get::<_,String>(2)?,r.get::<_,String>(3)?,r.get::<_,Option<String>>(4)?))).map_err(|_|"PDF_KNOWLEDGE_ITEMS_READ")?;
        for row in rows {let (ordinal,page_number,raw,status,knowledge_id)=row.map_err(|_|"PDF_KNOWLEDGE_ITEMS_READ")?;task.items.push(Item{ordinal,page_number,candidate:serde_json::from_str(&raw).map_err(|_|"PDF_KNOWLEDGE_CANDIDATE_INVALID")?,status,knowledge_id});}
    }
    Ok(task)
}
pub(crate) fn latest(c:&Connection,workspace:&str)->Result<Option<Task>,String> {
    let id:Option<String>=c.query_row("SELECT id FROM desktop_pdf_knowledge_tasks_v1 WHERE workspace_id=?1 ORDER BY created_at_ms DESC,rowid DESC LIMIT 1",[workspace],|r|r.get(0)).optional().map_err(|_|"PDF_KNOWLEDGE_TASK_READ")?;id.map(|id|read(c,workspace,&id)).transpose()
}
#[derive(Debug,Serialize)]
#[serde(rename_all="camelCase")]
pub(crate) struct TaskSummary{pub id:String,pub display_name:String,pub status:String}
#[derive(Debug,Serialize)]
#[serde(rename_all="camelCase")]
pub(crate) struct TaskPage{pub tasks:Vec<TaskSummary>,pub has_more:bool}
pub(crate) fn list(c:&Connection,workspace:&str,offset:i64)->Result<TaskPage,String> {
    if !(0..=1_000_000).contains(&offset){return Err("PDF_KNOWLEDGE_TASK_PAGE_INVALID".into());}
    let mut s=c.prepare("SELECT id,display_name,status FROM desktop_pdf_knowledge_tasks_v1 WHERE workspace_id=?1 ORDER BY created_at_ms DESC,rowid DESC LIMIT 51 OFFSET ?2").map_err(|_|"PDF_KNOWLEDGE_TASK_LIST")?;
    let mut tasks=s.query_map(params![workspace,offset],|r|Ok(TaskSummary{id:r.get(0)?,display_name:r.get(1)?,status:r.get(2)?})).map_err(|_|"PDF_KNOWLEDGE_TASK_LIST")?.collect::<rusqlite::Result<Vec<_>>>().map_err(|_|"PDF_KNOWLEDGE_TASK_LIST")?;let has_more=tasks.len()>50;tasks.truncate(50);Ok(TaskPage{tasks,has_more})
}
fn safe_asset(root:&Path,key:&str)->Result<std::path::PathBuf,String> {
    if !key.starts_with("pdf-knowledge-import/v1/") || Path::new(key).components().any(|c|!matches!(c,std::path::Component::Normal(_))){return Err("PRIVATE_ASSET_INVALID".into());}
    let path=root.join(key);
    for p in path.ancestors().take_while(|p|p.starts_with(root)) {
        if let Ok(m)=fs::symlink_metadata(p){
            #[cfg(windows)] {use std::os::windows::fs::MetadataExt;if m.file_attributes()&0x400!=0{return Err("PRIVATE_ASSET_LINK".into());}}
            if m.file_type().is_symlink(){return Err("PRIVATE_ASSET_LINK".into());}
        }
    }
    Ok(path)
}
fn safe_failure(code:&str)->&str {
    match code {"NOT_PDF"|"TOO_LARGE"|"TOO_MANY_OBJECTS"|"TOO_MANY_STREAMS"|"TOO_COMPLEX"|"TOO_MANY_PAGES"|"EMPTY_DOCUMENT"|"NO_TEXT_LAYER"|"MALFORMED"|"ENCRYPTED"|"EXTRACTION_NOT_PERMITTED"|"PAGE_TEXT_TOO_LONG"|"TOTAL_TEXT_TOO_LONG"|"HIGH_SENSITIVITY"|"UNSUPPORTED_TEXT_STRUCTURE"|"INTERRUPTED"|"WORKER_TIMEOUT"|"WORKER_EXIT_FAILED"|"WORKER_PROTOCOL"|"WORKER_LIMIT_UNAVAILABLE"|"WORKER_START_FAILED"|"OUTPUT_TOO_LARGE"|"PRIVATE_ASSET_INVALID"|"PRIVATE_ASSET_LINK"|"PRIVATE_ASSET_CHANGED"|"PRIVATE_ASSET_MISSING"=>code,_=>"PRIVATE_COPY_FAILED"}
}
fn failed(c:&Connection,id:&str,code:&str,now:i64)->Result<(),String> {
    c.execute("UPDATE desktop_pdf_knowledge_tasks_v1 SET status='FAILED',safe_error_code=?2,updated_at_ms=?3 WHERE id=?1 AND status!='CANCELLED'",params![id,safe_failure(code),now]).map_err(|_|"PDF_KNOWLEDGE_FAILURE_WRITE")?;Ok(())
}
pub(crate) fn stage(c:&Connection,root:&Path,workspace:&str,selected:&str,now:i64)->Result<Task,String> {
    let exists:bool=c.query_row("SELECT EXISTS(SELECT 1 FROM workspaces WHERE id=?1)",[workspace],|r|r.get(0)).map_err(|_|"PDF_KNOWLEDGE_WORKSPACE_READ")?;
    if !exists{return Err("PDF_KNOWLEDGE_WORKSPACE_MISSING".into());}
    let source=Path::new(selected);if !source.is_absolute() || !source.extension().and_then(|s|s.to_str()).is_some_and(|s|s.eq_ignore_ascii_case("pdf")){return Err("NOT_PDF".into());}
    let display=source.file_name().and_then(|s|s.to_str()).unwrap_or("PDF").chars().filter(|c|!c.is_control()).take(160).collect::<String>();
    let id=crate::ordinary_chat_random_id("pdf-knowledge-task")?;
    c.execute("INSERT INTO desktop_pdf_knowledge_tasks_v1(id,workspace_id,status,display_name,created_at_ms,updated_at_ms) VALUES(?1,?2,'SELECTED',?3,?4,?4)",params![id,workspace,display,now]).map_err(|_|"PDF_KNOWLEDGE_TASK_CREATE")?;
    let key=format!("pdf-knowledge-import/v1/{id}.pdf");let path=match safe_asset(root,&key){Ok(path)=>path,Err(code)=>{failed(c,&id,&code,now)?;return read(c,workspace,&id);}};let part=path.with_extension("part");let mut owns_part=false;
    let copied=(||->Result<(),String>{
        if !crate::desktop_legacy_area_split::plain_file_if_present(source)? {return Err("PRIVATE_ASSET_MISSING".into());}
        let file=fs::File::open(source).map_err(|_|"PRIVATE_COPY_FAILED")?;if file.metadata().map_err(|_|"PRIVATE_COPY_FAILED")?.len()>adapter::MAX_BYTES as u64{return Err("TOO_LARGE".into());}
        let mut bytes=Vec::new();file.take(adapter::MAX_BYTES as u64+1).read_to_end(&mut bytes).map_err(|_|"PRIVATE_COPY_FAILED")?;
        if bytes.len()>adapter::MAX_BYTES{return Err("TOO_LARGE".into());}
        fs::create_dir_all(path.parent().ok_or("PRIVATE_COPY_FAILED")?).map_err(|_|"PRIVATE_COPY_FAILED")?;
        let mut target=fs::OpenOptions::new().write(true).create_new(true).open(&part).map_err(|_|"PRIVATE_COPY_FAILED")?;owns_part=true;
        target.write_all(&bytes).and_then(|_|target.sync_all()).map_err(|_|"PRIVATE_COPY_FAILED")?;drop(target);fs::rename(&part,&path).map_err(|_|"PRIVATE_COPY_FAILED")?;owns_part=false;
        c.execute("UPDATE desktop_pdf_knowledge_tasks_v1 SET status='PRIVATE_COPIED',storage_key=?2,byte_count=?3,source_hash=?4 WHERE id=?1",params![id,key,bytes.len() as i64,crate::sha256(&bytes)]).map_err(|_|"PRIVATE_COPY_FAILED")?;Ok(())
    })();
    if owns_part{let _=fs::remove_file(&part);}
    if let Err(code)=copied{failed(c,&id,&code,now)?;}
    read(c,workspace,&id)
}
fn private_bytes(c:&Connection,root:&Path,id:&str)->Result<Vec<u8>,String> {
    let (key,size,hash):(String,i64,String)=c.query_row("SELECT storage_key,byte_count,source_hash FROM desktop_pdf_knowledge_tasks_v1 WHERE id=?1",[id],|r|Ok((r.get(0)?,r.get(1)?,r.get(2)?))).map_err(|_|"PRIVATE_ASSET_MISSING")?;
    let file=fs::File::open(safe_asset(root,&key)?).map_err(|_|"PRIVATE_ASSET_MISSING")?;let mut bytes=Vec::new();file.take(adapter::MAX_BYTES as u64+1).read_to_end(&mut bytes).map_err(|_|"PRIVATE_ASSET_MISSING")?;
    if bytes.len()>adapter::MAX_BYTES || bytes.len() as i64!=size || crate::sha256(&bytes)!=hash{return Err("PRIVATE_ASSET_CHANGED".into());}Ok(bytes)
}
fn saved_artifacts(c:&Connection,task:&Task,bytes:&[u8])->Result<Parsed,String> {
    let (extraction,candidate,hash):(u32,u32,String)=c.query_row("SELECT extraction_version,candidate_version,extracted_hash FROM desktop_pdf_knowledge_tasks_v1 WHERE id=?1",[&task.id],|r|Ok((r.get(0)?,r.get(1)?,r.get(2)?))).map_err(|_|"ARTIFACT_INVALID")?;
    let mut parsed=Parsed{source_sha256:crate::sha256(bytes),pages:vec![],items:vec![],extracted_sha256:hash,extraction_version:extraction,candidate_version:candidate};
    let mut statement=c.prepare("SELECT page_number,text,text_hash,code_point_count,extraction_version FROM desktop_pdf_knowledge_pages_v1 WHERE task_id=?1 AND generation=?2 ORDER BY page_number").map_err(|_|"ARTIFACT_INVALID")?;
    parsed.pages=statement.query_map(params![task.id,task.generation],|r|Ok(adapter::Page{page_number:r.get(0)?,text:r.get(1)?,text_sha256:r.get(2)?,code_point_count:r.get(3)?,extraction_version:r.get(4)?})).map_err(|_|"ARTIFACT_INVALID")?.collect::<rusqlite::Result<Vec<_>>>().map_err(|_|"ARTIFACT_INVALID")?;
    let mut statement=c.prepare("SELECT ordinal,page_number,candidate_json,candidate_hash,candidate_version FROM desktop_pdf_knowledge_items_v1 WHERE task_id=?1 AND generation=?2 ORDER BY ordinal").map_err(|_|"ARTIFACT_INVALID")?;
    let rows=statement.query_map(params![task.id,task.generation],|r|Ok((r.get::<_,usize>(0)?,r.get::<_,u32>(1)?,r.get::<_,String>(2)?,r.get::<_,String>(3)?,r.get::<_,u32>(4)?))).map_err(|_|"ARTIFACT_INVALID")?;
    for row in rows{let (ordinal,page_number,raw,candidate_sha256,candidate_version)=row.map_err(|_|"ARTIFACT_INVALID")?;parsed.items.push(adapter::Item{ordinal,page_number,content:serde_json::from_str(&raw).map_err(|_|"ARTIFACT_INVALID")?,candidate_sha256,candidate_version});}
    crate::desktop_pdf_worker_v1::validate_artifacts(bytes,&parsed)?;Ok(parsed)
}
pub(crate) fn process(c:&mut Connection,root:&Path,workspace:&str,id:&str,now:i64,
    decode:impl FnOnce(&[u8],&mut dyn FnMut(usize,usize)->Result<(),String>)->Result<Parsed,String>,retry:bool)->Result<Task,String> {
    let task=read(c,workspace,id)?;
    if (retry && task.status!="FAILED") || (!retry && task.status!="PRIVATE_COPIED"){return Err("PDF_KNOWLEDGE_TASK_NOT_PROCESSABLE".into());}
    let claimed=c.execute("UPDATE desktop_pdf_knowledge_tasks_v1 SET status='PREFLIGHT',safe_error_code=NULL,completed_pages=0,total_pages=0,retry_count=retry_count+?4 WHERE id=?1 AND workspace_id=?2 AND status=?3",params![id,workspace,task.status,i64::from(retry)]).map_err(|_|"PDF_KNOWLEDGE_CLAIM_WRITE")?;
    if claimed!=1{return Err("PDF_KNOWLEDGE_TASK_ALREADY_CLAIMED".into());}
    let bytes=match private_bytes(c,root,id){Ok(bytes)=>bytes,Err(code)=>{failed(c,id,&code,now)?;return read(c,workspace,id);}};
    if retry {
        if let Ok(parsed)=saved_artifacts(c,&task,&bytes){
            c.execute("UPDATE desktop_pdf_knowledge_tasks_v1 SET status=CASE WHEN EXISTS(SELECT 1 FROM desktop_pdf_knowledge_items_v1 WHERE task_id=?1 AND generation=?2 AND status='PENDING_CONFIRMATION') THEN CASE WHEN EXISTS(SELECT 1 FROM desktop_pdf_knowledge_items_v1 WHERE task_id=?1 AND generation=?2 AND status!='PENDING_CONFIRMATION') THEN 'PARTIALLY_COMPLETED' ELSE 'AWAITING_CONFIRMATION' END ELSE 'COMPLETED' END,safe_error_code=NULL,completed_pages=?3,total_pages=?3,updated_at_ms=?4 WHERE id=?1 AND status='PREFLIGHT'",params![id,task.generation,parsed.pages.len(),now]).map_err(|_|"PDF_KNOWLEDGE_REUSE_WRITE")?;
            return read(c,workspace,id);
        }
    }
    let mut last_progress=None;
    let decoded=decode(&bytes,&mut |completed,total| {
        if completed>total || total>adapter::MAX_PAGES{return Err("WORKER_PROTOCOL".into());}
        if last_progress==Some((completed,total)) {
            let status:String=c.query_row("SELECT status FROM desktop_pdf_knowledge_tasks_v1 WHERE id=?1",[id],|r|r.get(0)).map_err(|_|"INTERRUPTED")?;
            return if matches!(status.as_str(),"PREFLIGHT"|"EXTRACTING"){Ok(())}else{Err("INTERRUPTED".into())};
        }
        let count=c.execute("UPDATE desktop_pdf_knowledge_tasks_v1 SET status='EXTRACTING',completed_pages=?2,total_pages=?3,updated_at_ms=?4 WHERE id=?1 AND status IN ('PREFLIGHT','EXTRACTING')",params![id,completed,total,crate::local_now_millis()]).map_err(|_|"PDF_KNOWLEDGE_PROGRESS_WRITE")?;
        if count!=1{return Err("INTERRUPTED".into());}last_progress=Some((completed,total));Ok(())
    });
    match decoded {
        Ok(parsed)=>{
            let committed=(||->Result<(),String>{
            crate::desktop_pdf_worker_v1::validate_artifacts(&bytes,&parsed)?;
            let tx=c.transaction().map_err(|_|"PDF_KNOWLEDGE_PARSE_TRANSACTION")?;
            let current=read(&tx,workspace,id)?;if current.status=="CANCELLED"{return Ok(());}
            let generation=current.generation.checked_add(1).ok_or("PDF_KNOWLEDGE_GENERATION_OVERFLOW")?;
            for page in &parsed.pages {tx.execute("INSERT INTO desktop_pdf_knowledge_pages_v1(task_id,generation,page_number,text,text_hash,code_point_count,extraction_version) VALUES(?1,?2,?3,?4,?5,?6,?7)",params![id,generation,page.page_number,page.text,page.text_sha256,page.code_point_count,page.extraction_version]).map_err(|_|"PDF_KNOWLEDGE_PAGE_WRITE")?;}
            for item in &parsed.items {tx.execute("INSERT INTO desktop_pdf_knowledge_items_v1(task_id,generation,ordinal,page_number,candidate_json,candidate_hash,candidate_version,status) VALUES(?1,?2,?3,?4,?5,?6,?7,'PENDING_CONFIRMATION')",params![id,generation,item.ordinal,item.page_number,serde_json::to_string(&item.content).map_err(|_|"PDF_KNOWLEDGE_CANDIDATE_INVALID")?,item.candidate_sha256,item.candidate_version]).map_err(|_|"PDF_KNOWLEDGE_ITEM_WRITE")?;}
            tx.execute("UPDATE desktop_pdf_knowledge_tasks_v1 SET status='AWAITING_CONFIRMATION',generation=?2,extraction_version=?3,candidate_version=?4,extracted_hash=?5,completed_pages=?6,total_pages=?6,updated_at_ms=?7 WHERE id=?1",params![id,generation,parsed.extraction_version,parsed.candidate_version,parsed.extracted_sha256,parsed.pages.len(),now]).map_err(|_|"PDF_KNOWLEDGE_TASK_WRITE")?;
            tx.commit().map_err(|_|"PDF_KNOWLEDGE_PARSE_COMMIT")?;
            Ok(())
            })();
            if let Err(code)=committed{failed(c,id,&code,now)?;}
        },
        Err(code)=>failed(c,id,&code,now)?,
    }
    read(c,workspace,id)
}

fn finish(tx:&Transaction<'_>,id:&str,generation:i64,now:i64)->Result<(),String> {
    tx.execute("UPDATE desktop_pdf_knowledge_tasks_v1 SET status=CASE WHEN EXISTS(SELECT 1 FROM desktop_pdf_knowledge_items_v1 WHERE task_id=?1 AND generation=?2 AND status='PENDING_CONFIRMATION') THEN 'PARTIALLY_COMPLETED' ELSE 'COMPLETED' END,updated_at_ms=?3 WHERE id=?1",params![id,generation,now]).map_err(|_|"PDF_KNOWLEDGE_TASK_WRITE")?;Ok(())
}
pub(crate) fn decide(c:&mut Connection,workspace:&str,id:&str,generation:Option<i64>,ordinal:Option<i64>,now:i64)->Result<Task,String> {
    let tx=c.transaction().map_err(|_|"PDF_KNOWLEDGE_DECISION_TRANSACTION")?;let task=read(&tx,workspace,id)?;
    if matches!(task.status.as_str(),"COMPLETED"|"CANCELLED"){return Err("PDF_KNOWLEDGE_TASK_TERMINAL".into());}
    if let Some(ordinal)=ordinal {
        if generation!=Some(task.generation) || !matches!(task.status.as_str(),"AWAITING_CONFIRMATION"|"PARTIALLY_COMPLETED"){return Err("PDF_KNOWLEDGE_GENERATION_CHANGED".into());}
        if tx.execute("UPDATE desktop_pdf_knowledge_items_v1 SET status='SKIPPED' WHERE task_id=?1 AND generation=?2 AND ordinal=?3 AND status='PENDING_CONFIRMATION'",params![id,task.generation,ordinal]).map_err(|_|"PDF_KNOWLEDGE_ITEM_WRITE")?!=1{return Err("PDF_KNOWLEDGE_ITEM_NOT_PENDING".into());}finish(&tx,id,task.generation,now)?;
    }else{tx.execute("UPDATE desktop_pdf_knowledge_tasks_v1 SET status='CANCELLED',updated_at_ms=?2 WHERE id=?1",params![id,now]).map_err(|_|"PDF_KNOWLEDGE_TASK_WRITE")?;}
    tx.commit().map_err(|_|"PDF_KNOWLEDGE_DECISION_COMMIT")?;read(c,workspace,id)
}

#[derive(Debug,Deserialize)]
#[serde(rename_all="camelCase",deny_unknown_fields)]
pub(crate) struct Confirm{pub workspace_id:String,pub task_id:String,pub generation:i64,pub ordinal:i64,pub candidate:Content,pub project_id:Option<String>}
fn confirmable(c:&Connection,args:&Confirm)->Result<(Task,Item),String> {
    let mut task=read(c,&args.workspace_id,&args.task_id)?;
    if task.generation!=args.generation || !matches!(task.status.as_str(),"AWAITING_CONFIRMATION"|"PARTIALLY_COMPLETED"|"COMPLETED"){return Err("PDF_KNOWLEDGE_TASK_NOT_CONFIRMABLE".into());}
    let index=task.items.iter().position(|i|i.ordinal==args.ordinal).ok_or("PDF_KNOWLEDGE_ITEM_MISSING")?;
    let item=task.items.remove(index);if !matches!(item.status.as_str(),"PENDING_CONFIRMATION"|"CONFIRMED"){return Err("PDF_KNOWLEDGE_ITEM_NOT_PENDING".into());}Ok((task,item))
}
pub(crate) fn confirmation_args(c:&Connection,args:&Confirm)->Result<crate::DomainMutationArgs,String> {
    let (_,item)=confirmable(c,args)?;let candidate=validate_candidate(args.candidate.clone())?;
    let intent_id=if item.status=="CONFIRMED" {
        c.query_row("SELECT intent_id FROM domain_intents WHERE workspace_id=?1 AND entity='knowledge' AND action='create' AND object_id=?2",params![args.workspace_id,item.knowledge_id.as_deref().ok_or("PDF_KNOWLEDGE_CONFIRMATION_RECORD_MISSING")?],|r|r.get::<_,String>(0)).map_err(|_|"PDF_KNOWLEDGE_CONFIRMATION_RECORD_MISSING")?
    }else{let hash=crate::sha256(format!("nfai-pdf-knowledge-confirm-v1:{}:{}:{}",args.task_id,args.generation,args.ordinal).as_bytes());format!("pdk-{}",&hash[..48])};
    Ok(crate::DomainMutationArgs{intent_id,workspace_id:args.workspace_id.clone(),entity:"knowledge".into(),action:"create".into(),object_id:None,expected_revision:None,fields:serde_json::json!({"title":candidate.title,"body":candidate.body,"tags":candidate.tags,"scope":if args.project_id.is_some(){"PROJECT"}else{"GLOBAL"},"projectId":args.project_id})})
}
pub(crate) fn commit_confirmation(tx:&Transaction<'_>,args:&Confirm,receipt:&crate::MutationReceipt,now:i64)->Result<(),String> {
    let (task,item)=confirmable(tx,args)?;let candidate=validate_candidate(args.candidate.clone())?;
    let hash=crate::sha256(crate::canonical_json(&serde_json::json!({"candidate":candidate,"projectId":args.project_id}))?.as_bytes());
    if item.status=="CONFIRMED" {
        let recorded:String=tx.query_row("SELECT confirmed_content_hash FROM desktop_pdf_knowledge_sources_v1 WHERE workspace_id=?1 AND knowledge_id=?2",params![args.workspace_id,receipt.object_id],|r|r.get(0)).map_err(|_|"PDF_KNOWLEDGE_SOURCE_MISSING")?;
        if !receipt.replayed || item.knowledge_id.as_deref()!=Some(&receipt.object_id) || recorded!=hash{return Err("PDF_KNOWLEDGE_CONFIRMATION_CONFLICT".into());}return Ok(());
    }
    let (page_hash,extraction,candidate_hash,candidate_version):(String,u32,String,u32)=tx.query_row("SELECT p.text_hash,p.extraction_version,i.candidate_hash,i.candidate_version FROM desktop_pdf_knowledge_items_v1 i JOIN desktop_pdf_knowledge_pages_v1 p ON p.task_id=i.task_id AND p.generation=i.generation AND p.page_number=i.page_number WHERE i.task_id=?1 AND i.generation=?2 AND i.ordinal=?3",params![args.task_id,args.generation,args.ordinal],|r|Ok((r.get(0)?,r.get(1)?,r.get(2)?,r.get(3)?))).map_err(|_|"PDF_KNOWLEDGE_SOURCE_MISSING")?;
    tx.execute("UPDATE desktop_pdf_knowledge_items_v1 SET status='CONFIRMED',knowledge_id=?4 WHERE task_id=?1 AND generation=?2 AND ordinal=?3",params![args.task_id,args.generation,args.ordinal,receipt.object_id]).map_err(|_|"PDF_KNOWLEDGE_ITEM_WRITE")?;
    tx.execute("INSERT INTO desktop_pdf_knowledge_sources_v1(workspace_id,knowledge_id,task_id,generation,ordinal,page_number,adapter_version,extraction_version,candidate_version,source_hash,page_text_hash,candidate_hash,confirmed_content_hash) VALUES(?1,?2,?3,?4,?5,?6,1,?7,?8,?9,?10,?11,?12)",params![args.workspace_id,receipt.object_id,args.task_id,args.generation,args.ordinal,item.page_number,extraction,candidate_version,task.source_hash.ok_or("PDF_KNOWLEDGE_SOURCE_MISSING")?,page_hash,candidate_hash,hash]).map_err(|_|"PDF_KNOWLEDGE_SOURCE_WRITE")?;
    finish(tx,&args.task_id,args.generation,now)
}

#[cfg(test)]
mod tests {
    use super::*;
    fn previous(area:&str)->Connection {
        let mut c=crate::desktop_json_knowledge_tasks_v1::tests::previous(area);
        crate::desktop_json_knowledge_tasks_v1::migrate_committed_area(&mut c,area).unwrap();c
    }
    fn current()->Connection{let mut c=previous("WORK");migrate_committed_area(&mut c,"WORK").unwrap();c.execute_batch("PRAGMA foreign_keys=ON").unwrap();c}
    fn bytes()->Vec<u8>{adapter::tests::pdf(&["First page","","Third page"],true)}
    fn ready(c:&mut Connection,root:&Path)->Task {
        let path=root.join("synthetic.pdf");fs::write(&path,bytes()).unwrap();let task=stage(c,root,"synthetic-workspace",path.to_str().unwrap(),1).unwrap();
        process(c,root,"synthetic-workspace",&task.id,2,|bytes,progress|adapter::parse("synthetic.pdf","application/pdf",bytes,progress),false).unwrap()
    }
    #[test]
    fn pdf_knowledge_tasks_schema55_preserves54_tasks_and_rejects_wrong_area_or_missing_owner() {
        for area in ["CHAT","WORK"] {
            let mut c=previous(area);let dir=tempfile::tempdir().unwrap();let md=dir.path().join("old.md");fs::write(&md,"# Old\nretained text").unwrap();
            let prior=crate::desktop_markdown_knowledge_v1::stage(&mut c,dir.path(),"synthetic-workspace",md.to_str().unwrap(),1).unwrap();
            assert!(migrate_committed_area(&mut c,if area=="CHAT"{"WORK"}else{"CHAT"}).is_err());
            migrate_committed_area(&mut c,area).unwrap();migrate_committed_area(&mut c,area).unwrap();
            assert_eq!(c.pragma_query_value(None,"user_version",|r|r.get::<_,i64>(0)).unwrap(),55);
            assert_eq!(crate::desktop_markdown_knowledge_v1::read(&c,"synthetic-workspace",&prior.id).unwrap().items[0].candidate.body,"retained text");
            assert_eq!(c.query_row("SELECT text FROM desktop_conversation_drafts_v1",[],|r|r.get::<_,String>(0)).unwrap(),"preserve old draft");
            crate::desktop_json_knowledge_tasks_v1::migrate_committed_area(&mut c,area).unwrap();
            c.execute("DROP TABLE desktop_pdf_knowledge_sources_v1",[]).unwrap();assert!(migrate_committed_area(&mut c,area).is_err());
        }
    }
    #[test]
    fn pdf_knowledge_tasks_private_copy_progress_pages_and_workspace_are_exact() {
        let mut c=current();let dir=tempfile::tempdir().unwrap();let task=ready(&mut c,dir.path());
        assert_eq!(task.status,"AWAITING_CONFIRMATION");assert_eq!(task.generation,1);assert_eq!(task.completed_pages,3);assert_eq!(task.total_pages,3);
        assert_eq!(task.items.len(),2);assert_eq!(task.items[1].page_number,3);
        assert!(read(&c,"other",&task.id).is_err());assert!(decide(&mut c,"other",&task.id,None,None,3).is_err());
        assert_eq!(private_bytes(&c,dir.path(),&task.id).unwrap(),bytes());
        assert_eq!(saved_artifacts(&c,&task,&bytes()).unwrap().pages.len(),3);
        assert_eq!(c.query_row("SELECT COUNT(*) FROM desktop_pdf_knowledge_sources_v1",[],|r|r.get::<_,i64>(0)).unwrap(),0);
    }
    #[test]
    fn pdf_knowledge_tasks_failed_worker_and_cancellation_have_no_candidates() {
        for cancel in [false,true] {
            let mut c=current();let dir=tempfile::tempdir().unwrap();let path=dir.path().join("synthetic.pdf");fs::write(&path,bytes()).unwrap();
            let task=stage(&c,dir.path(),"synthetic-workspace",path.to_str().unwrap(),1).unwrap();let id=task.id.clone();
            let task=process(&mut c,dir.path(),"synthetic-workspace",&id,2,|_,progress|{
                progress(1,3)?;
                if cancel {return Err("INTERRUPTED".into());}Err("WORKER_EXIT_FAILED".into())
            },false).unwrap();assert_eq!(task.status,"FAILED");assert!(task.items.is_empty());assert_eq!(task.completed_pages,1);
            assert_eq!(c.query_row("SELECT COUNT(*) FROM desktop_pdf_knowledge_pages_v1",[],|r|r.get::<_,i64>(0)).unwrap(),0);
            let cancelled=decide(&mut c,"synthetic-workspace",&id,None,None,3).unwrap();assert_eq!(cancelled.status,"CANCELLED");
        }
    }
    #[test]
    fn pdf_knowledge_tasks_separate_connection_cancellation_interrupts_progress_and_discards_reply() {
        let dir=tempfile::tempdir().unwrap();let store=crate::DesktopWorkspaceStore::open(dir.path().join("store")).unwrap();
        let workspace=store.ensure_local_workspace().unwrap().summary.id;
        let mut c=store.connection().unwrap();create(&c).unwrap();let mut other=store.connection().unwrap();
        let source=dir.path().join("synthetic.pdf");fs::write(&source,bytes()).unwrap();
        let task=stage(&c,dir.path(),&workspace,source.to_str().unwrap(),1).unwrap();let id=task.id.clone();
        let cancelled=process(&mut c,dir.path(),&workspace,&id,2,|b,p|{
            p(1,3)?;let observed=read(&other,&workspace,&id)?;
            assert_eq!(observed.status,"EXTRACTING");assert_eq!(observed.completed_pages,1);
            decide(&mut other,&workspace,&id,None,None,3)?;
            assert_eq!(p(1,3).unwrap_err(),"INTERRUPTED");
            // A late successful child reply must still never publish candidates.
            adapter::parse("synthetic.pdf","application/pdf",b,|_,_|Ok(()))
        },false).unwrap();
        assert_eq!(cancelled.status,"CANCELLED");assert!(cancelled.items.is_empty());
        assert_eq!(c.query_row("SELECT COUNT(*) FROM desktop_pdf_knowledge_pages_v1",[],|r|r.get::<_,i64>(0)).unwrap(),0);
        assert!(private_bytes(&c,dir.path(),&id).is_ok());
    }
    #[test]
    fn pdf_knowledge_tasks_startup_recovers_only_processing_without_replay() {
        let mut c=current();let dir=tempfile::tempdir().unwrap();let task=ready(&mut c,dir.path());
        for status in ["SELECTED","PRIVATE_COPIED","PREFLIGHT","EXTRACTING"] {
            c.execute("UPDATE desktop_pdf_knowledge_tasks_v1 SET status=?2 WHERE id=?1",params![task.id,status]).unwrap();migrate_committed_area(&mut c,"WORK").unwrap();
            let failed=read(&c,"synthetic-workspace",&task.id).unwrap();assert_eq!(failed.status,"FAILED");assert_eq!(failed.safe_error_code.as_deref(),Some("INTERRUPTED"));assert!(failed.items.is_empty());
        }
        let reused=process(&mut c,dir.path(),"synthetic-workspace",&task.id,4,|_,_|panic!("verified artifacts should be reused"),true).unwrap();assert_eq!(reused.generation,1);assert_eq!(reused.items.len(),2);assert_eq!(reused.retry_count,1);
    }
    #[test]
    fn pdf_knowledge_tasks_invalidated_versions_redecode_new_generation_and_stale_skip_is_refused() {
        let mut c=current();let dir=tempfile::tempdir().unwrap();let task=ready(&mut c,dir.path());
        c.execute("UPDATE desktop_pdf_knowledge_tasks_v1 SET status='FAILED',extraction_version=99 WHERE id=?1",[&task.id]).unwrap();
        let next=process(&mut c,dir.path(),"synthetic-workspace",&task.id,4,|bytes,progress|adapter::parse("synthetic.pdf","application/pdf",bytes,progress),true).unwrap();assert_eq!(next.generation,2);
        assert!(decide(&mut c,"synthetic-workspace",&task.id,Some(1),Some(0),5).is_err());
        let skipped=decide(&mut c,"synthetic-workspace",&task.id,Some(2),Some(0),5).unwrap();assert_eq!(skipped.status,"PARTIALLY_COMPLETED");assert_eq!(skipped.items[0].status,"SKIPPED");
        assert_eq!(c.query_row("SELECT COUNT(*) FROM desktop_pdf_knowledge_pages_v1 WHERE generation=1",[],|r|r.get::<_,i64>(0)).unwrap(),3);
    }
    #[test]
    fn pdf_knowledge_tasks_private_tamper_fails_without_retrying_external_source() {
        let mut c=current();let dir=tempfile::tempdir().unwrap();let task=ready(&mut c,dir.path());
        let key:String=c.query_row("SELECT storage_key FROM desktop_pdf_knowledge_tasks_v1 WHERE id=?1",[&task.id],|r|r.get(0)).unwrap();
        fs::write(dir.path().join(key),"changed bytes").unwrap();c.execute("UPDATE desktop_pdf_knowledge_tasks_v1 SET status='FAILED' WHERE id=?1",[&task.id]).unwrap();
        let failed=process(&mut c,dir.path(),"synthetic-workspace",&task.id,4,|_,_|panic!("changed private bytes must not decode"),true).unwrap();assert_eq!(failed.safe_error_code.as_deref(),Some("PRIVATE_ASSET_CHANGED"));assert!(failed.items.is_empty());
    }
    #[test]
    fn pdf_knowledge_tasks_invalid_decoder_bundle_fails_without_partial_artifacts() {
        let mut c=current();let dir=tempfile::tempdir().unwrap();let path=dir.path().join("synthetic.pdf");fs::write(&path,bytes()).unwrap();let task=stage(&c,dir.path(),"synthetic-workspace",path.to_str().unwrap(),1).unwrap();
        let failed=process(&mut c,dir.path(),"synthetic-workspace",&task.id,4,|b,p|{let mut parsed=adapter::parse("synthetic.pdf","application/pdf",b,p)?;parsed.pages[0].text_sha256="0".repeat(64);Ok(parsed)},false).unwrap();assert_eq!(failed.status,"FAILED");assert_eq!(failed.safe_error_code.as_deref(),Some("WORKER_PROTOCOL"));
        assert_eq!(c.query_row("SELECT COUNT(*) FROM desktop_pdf_knowledge_pages_v1",[],|r|r.get::<_,i64>(0)).unwrap(),0);
    }
    #[test]
    fn pdf_knowledge_tasks_confirmed_generation_survives_redecode_and_stale_confirmation_refuses() {
        let dir=tempfile::tempdir().unwrap();let root=dir.path().join("store");
        let store=crate::DesktopWorkspaceStore::open(root.clone()).unwrap();let workspace=store.ensure_local_workspace().unwrap().summary.id;
        let mut c=store.connection().unwrap();create(&c).unwrap();let source=dir.path().join("source.pdf");fs::write(&source,bytes()).unwrap();
        let staged=stage(&c,&root,&workspace,source.to_str().unwrap(),1).unwrap();
        let task=process(&mut c,&root,&workspace,&staged.id,2,|b,p|adapter::parse("source.pdf","application/pdf",b,p),false).unwrap();
        let mut args=Confirm{workspace_id:workspace.clone(),task_id:task.id.clone(),generation:1,ordinal:0,candidate:task.items[0].candidate.clone(),project_id:None};
        let mutation=confirmation_args(&c,&args).unwrap();let first=store.mutate_domain_with_commit(mutation,|tx,r|commit_confirmation(tx,&args,r,3)).unwrap();
        let original_source:String=c.query_row("SELECT confirmed_content_hash FROM desktop_pdf_knowledge_sources_v1 WHERE knowledge_id=?1",[&first.object_id],|r|r.get(0)).unwrap();
        c.execute("UPDATE desktop_pdf_knowledge_tasks_v1 SET status='FAILED',extraction_version=99 WHERE id=?1",[&task.id]).unwrap();
        let next=process(&mut c,&root,&workspace,&task.id,4,|b,p|adapter::parse("source.pdf","application/pdf",b,p),true).unwrap();assert_eq!(next.generation,2);
        assert!(confirmation_args(&c,&args).is_err());
        args.generation=2;let mutation=confirmation_args(&c,&args).unwrap();let second=store.mutate_domain_with_commit(mutation,|tx,r|commit_confirmation(tx,&args,r,5)).unwrap();assert_ne!(first.object_id,second.object_id);
        assert_eq!(c.query_row("SELECT knowledge_id FROM desktop_pdf_knowledge_items_v1 WHERE task_id=?1 AND generation=1 AND ordinal=0",[&task.id],|r|r.get::<_,String>(0)).unwrap(),first.object_id);
        assert_eq!(c.query_row("SELECT confirmed_content_hash FROM desktop_pdf_knowledge_sources_v1 WHERE knowledge_id=?1",[&first.object_id],|r|r.get::<_,String>(0)).unwrap(),original_source);
        let issues=c.prepare("PRAGMA foreign_key_check").unwrap().query_map([],|_|Ok(())).unwrap().count();assert_eq!(issues,0);
        assert_eq!(c.query_row("SELECT COUNT(*) FROM domain_intents WHERE entity='knowledge' AND action='create'",[],|r|r.get::<_,i64>(0)).unwrap(),2);
    }
    #[test]
    fn pdf_knowledge_tasks_actual_domain_confirmation_source_rollback_replay_and_reopen() {
        let dir=tempfile::tempdir().unwrap();let root=dir.path().join("store");let store=crate::DesktopWorkspaceStore::open(root.clone()).unwrap();let workspace=store.ensure_local_workspace().unwrap().summary.id;
        let mut c=store.connection().unwrap();create(&c).unwrap();let source=dir.path().join("source.pdf");fs::write(&source,bytes()).unwrap();let task=stage(&c,&root,&workspace,source.to_str().unwrap(),1).unwrap();
        let task=process(&mut c,&root,&workspace,&task.id,2,|b,p|adapter::parse("source.pdf","application/pdf",b,p),false).unwrap();
        let mut args=Confirm{workspace_id:workspace.clone(),task_id:task.id.clone(),generation:1,ordinal:0,candidate:Content{title:"Edited title".into(),body:"edited inert text".into(),tags:vec!["local".into()]},project_id:None};
        c.execute_batch("CREATE TRIGGER reject_pdf_source BEFORE INSERT ON desktop_pdf_knowledge_sources_v1 BEGIN SELECT RAISE(ABORT,'synthetic rollback'); END").unwrap();
        let mutation=confirmation_args(&c,&args).unwrap();assert!(store.mutate_domain_with_commit(mutation,|tx,r|commit_confirmation(tx,&args,r,3)).is_err());
        assert_eq!(c.query_row("SELECT COUNT(*) FROM domain_intents WHERE entity='knowledge' AND action='create'",[],|r|r.get::<_,i64>(0)).unwrap(),0);assert_eq!(read(&c,&workspace,&task.id).unwrap().items[0].status,"PENDING_CONFIRMATION");
        c.execute("DROP TRIGGER reject_pdf_source",[]).unwrap();let mutation=confirmation_args(&c,&args).unwrap();let first=store.mutate_domain_with_commit(mutation,|tx,r|commit_confirmation(tx,&args,r,4)).unwrap();
        let mutation=confirmation_args(&c,&args).unwrap();assert!(store.mutate_domain_with_commit(mutation,|tx,r|commit_confirmation(tx,&args,r,5)).unwrap().replayed);
        args.candidate.body="changed retry".into();let mutation=confirmation_args(&c,&args).unwrap();assert!(store.mutate_domain_with_commit(mutation,|tx,r|commit_confirmation(tx,&args,r,6)).is_err());
        args.ordinal=1;args.candidate=task.items[1].candidate.clone();let mutation=confirmation_args(&c,&args).unwrap();let second=store.mutate_domain_with_commit(mutation,|tx,r|commit_confirmation(tx,&args,r,7)).unwrap();assert_ne!(first.object_id,second.object_id);
        assert_eq!(read(&c,&workspace,&task.id).unwrap().status,"COMPLETED");drop(c);drop(store);let reopened=crate::DesktopWorkspaceStore::open(root).unwrap();let c=reopened.connection().unwrap();assert_eq!(read(&c,&workspace,&task.id).unwrap().items[0].knowledge_id.as_deref(),Some(first.object_id.as_str()));
        assert_eq!(c.query_row("SELECT COUNT(*) FROM desktop_pdf_knowledge_sources_v1",[],|r|r.get::<_,i64>(0)).unwrap(),2);
        let issues=c.prepare("PRAGMA foreign_key_check").unwrap().query_map([],|_|Ok(())).unwrap().count();assert_eq!(issues,0);
    }
}
