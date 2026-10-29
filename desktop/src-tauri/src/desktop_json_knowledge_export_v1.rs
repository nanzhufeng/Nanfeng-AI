//! Explicit ACTIVE Knowledge selection -> strict P4L JSON -> atomic file/readback.
use rusqlite::{Connection, OptionalExtension, params};
use serde::Serialize;
use serde_json::{Value, json};
use std::{collections::BTreeSet, fs, io::Write, path::Path};
const FIDELITY:&str="title,body,tags,scope,safe source kind/summary; local IDs/revisions/hash are source facts and reimport remaps IDs; excludes paths,credentials,attachments,relationships,memory,context,temporary selections";

#[derive(Debug, Clone, Serialize, PartialEq, Eq)]
#[serde(rename_all="camelCase")]
pub(crate) struct Manifest {
    pub format: String, pub schema_version: String, pub exported_at: String,
    pub item_count: usize, pub fidelity: String,
}
#[derive(Debug, Serialize)]
#[serde(rename_all="camelCase")]
pub(crate) struct Receipt { pub byte_count: usize, pub sha256: String, pub manifest: Manifest }
pub(crate) struct Prepared { pub bytes: Vec<u8>, pub manifest: Manifest }

pub(crate) fn prepare(c:&mut Connection, workspace:&str, ids:&[String], exported_at:&str)->Result<Prepared,String> {
    if ids.is_empty() || ids.len()>100 { return Err("JSON_EXPORT_SELECTION_INVALID".into()); }
    let selected:BTreeSet<_>=ids.iter().collect();
    if selected.len()!=ids.len() { return Err("JSON_EXPORT_SELECTION_DUPLICATE".into()); }
    let tx=c.transaction().map_err(|_|"JSON_EXPORT_SNAPSHOT")?;
    let raw:String=tx.query_row("SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?1",[workspace],|r|r.get(0)).map_err(|_|"JSON_EXPORT_WORKSPACE")?;
    let exchange:Value=serde_json::from_str(&raw).map_err(|_|"JSON_EXPORT_WORKSPACE_INVALID")?;
    let records=exchange["knowledge"].as_array().ok_or("JSON_EXPORT_KNOWLEDGE_INVALID")?;
    let pdf_table:bool=tx.query_row("SELECT EXISTS(SELECT 1 FROM sqlite_master WHERE type='table' AND name='desktop_pdf_knowledge_sources_v1')",[],|r|r.get(0)).map_err(|_|"JSON_EXPORT_SOURCE_READ")?;
    let web_table:bool=tx.query_row("SELECT EXISTS(SELECT 1 FROM sqlite_master WHERE type='table' AND name='desktop_web_knowledge_sources_v1')",[],|r|r.get(0)).map_err(|_|"JSON_EXPORT_SOURCE_READ")?;
    let mut items=Vec::with_capacity(ids.len());
    for id in selected {
        let matches:Vec<_>=records.iter().filter(|k|k["id"].as_str()==Some(id)).collect();
        if matches.len()!=1 || matches[0]["status"]!="ACTIVE" { return Err("JSON_EXPORT_NOT_ACTIVE".into()); }
        let k=matches[0];
        let revision=k["revision"].as_i64().filter(|v|*v>0 && *v<=i32::MAX as i64).ok_or("JSON_EXPORT_REVISION_INVALID")?;
        let origin:Option<String>=tx.query_row("SELECT i.candidate_json FROM desktop_json_knowledge_sources_v1 s JOIN desktop_json_knowledge_items_v1 i ON i.task_id=s.task_id AND i.ordinal=s.ordinal WHERE s.workspace_id=?1 AND s.knowledge_id=?2",params![workspace,id],|r|r.get(0)).optional().map_err(|_|"JSON_EXPORT_SOURCE_READ")?;
        let markdown:bool=tx.query_row("SELECT EXISTS(SELECT 1 FROM desktop_markdown_knowledge_sources_v1 WHERE workspace_id=?1 AND knowledge_id=?2)",params![workspace,id],|r|r.get(0)).map_err(|_|"JSON_EXPORT_SOURCE_READ")?;
        let pdf_page:Option<i64>=if pdf_table {tx.query_row("SELECT page_number FROM desktop_pdf_knowledge_sources_v1 WHERE workspace_id=?1 AND knowledge_id=?2",params![workspace,id],|r|r.get(0)).optional().map_err(|_|"JSON_EXPORT_SOURCE_READ")?}else{None};
        let web_url:Option<String>=if web_table {tx.query_row("SELECT display_url FROM desktop_web_knowledge_sources_v1 WHERE workspace_id=?1 AND knowledge_id=?2",params![workspace,id],|r|r.get(0)).optional().map_err(|_|"JSON_EXPORT_SOURCE_READ")?}else{None};
        if usize::from(origin.is_some())+usize::from(markdown)+usize::from(pdf_page.is_some())+usize::from(web_url.is_some())>1 { return Err("JSON_EXPORT_SOURCE_AMBIGUOUS".into()); }
        let (kind,summary)=if let Some(raw)=origin {
            let candidate:crate::desktop_json_knowledge_v1::Candidate=serde_json::from_str(&raw).map_err(|_|"JSON_EXPORT_SOURCE_INVALID")?;
            (candidate.source.kind,candidate.source.summary)
        } else {
            if markdown { ("MARKDOWN_IMPORT".into(),"confirmed local Markdown knowledge".into()) }
            else if let Some(page)=pdf_page {
                if !(1..=80).contains(&page){return Err("JSON_EXPORT_SOURCE_INVALID".into());}
                ("PDF_TEXT_IMPORT".into(),format!("confirmed local PDF text, page {page}"))
            } else if let Some(url)=web_url {
                // The strict exchange format forbids URLs in metadata. Validate the
                // private source fact, then export its kind and a content-free summary.
                let safe=crate::desktop_web_text_snapshot_v1::validate_url(&url).map_err(|_|"JSON_EXPORT_SOURCE_INVALID")?;
                if safe.display_url!=url {return Err("JSON_EXPORT_SOURCE_INVALID".into());}
                ("WEB_TEXT_SNAPSHOT".into(),"confirmed local webpage text snapshot".into())
            } else { ("LOCAL_KNOWLEDGE".into(),"confirmed local knowledge".into()) }
        };
        items.push(json!({"id":id,"title":k["title"],"body":k["body"],"tags":k["tags"],"scope":k["scope"],"projectRef":k["projectId"],"revision":revision.to_string(),"hash":k["contentHash"],"source":{"kind":kind,"summary":summary}}));
    }
    let doc=json!({"format":"nfai.knowledge.json","schemaVersion":"1","exportedAt":exported_at,"items":items});
    let bytes=crate::canonical_json(&doc)?.into_bytes();
    let parsed=crate::desktop_json_knowledge_v1::parse(&bytes)?;
    for (item,original) in parsed.items.iter().zip(doc["items"].as_array().ok_or("JSON_EXPORT_DOCUMENT_INVALID")?) {
        if json!(item.content.title)!=original["title"] || json!(item.content.body)!=original["body"] || json!(item.content.tags)!=original["tags"] {
            return Err("JSON_EXPORT_NOT_LOSSLESS".into());
        }
    }
    let manifest=Manifest{format:"nfai.knowledge.json".into(),schema_version:"1".into(),exported_at:exported_at.into(),item_count:items.len(),fidelity:FIDELITY.into()};
    tx.commit().map_err(|_|"JSON_EXPORT_SNAPSHOT_CLOSE")?;
    Ok(Prepared{bytes,manifest})
}

pub(crate) fn verify(bytes:&[u8],hash:&str,manifest:&Manifest)->Result<(),String> {
    if crate::sha256(bytes)!=hash { return Err("JSON_EXPORT_READBACK_HASH".into()); }
    let doc=crate::desktop_json_knowledge_v1::parse(bytes)?;
    if manifest.format!="nfai.knowledge.json" || manifest.schema_version!="1" || manifest.exported_at!=doc.exported_at || manifest.item_count!=doc.items.len() || manifest.fidelity!=FIDELITY {
        return Err("JSON_EXPORT_READBACK_MANIFEST".into());
    }
    Ok(())
}
fn safe_target(path:&Path)->Result<(),String> {
    if !path.is_absolute() || !path.extension().and_then(|v|v.to_str()).is_some_and(|v|v.eq_ignore_ascii_case("json")) { return Err("JSON_EXPORT_TARGET_INVALID".into()); }
    if path.components().any(|c| matches!(c,std::path::Component::ParentDir)) { return Err("JSON_EXPORT_TARGET_INVALID".into()); }
    for p in path.ancestors() {
        match fs::symlink_metadata(p) {
            Ok(m)=>{
                #[cfg(windows)] {use std::os::windows::fs::MetadataExt;if m.file_attributes()&0x400!=0 {return Err("JSON_EXPORT_TARGET_LINK".into());}}
                if m.file_type().is_symlink(){return Err("JSON_EXPORT_TARGET_LINK".into());}
            },
            Err(e) if e.kind()==std::io::ErrorKind::NotFound=>{},
            Err(_)=>return Err("JSON_EXPORT_TARGET_UNREADABLE".into()),
        }
    }
    Ok(())
}
pub(crate) fn write(prepared:Prepared,target:&Path)->Result<Receipt,String> {
    safe_target(target)?;
    let hash=crate::sha256(&prepared.bytes);verify(&prepared.bytes,&hash,&prepared.manifest)?;
    let nonce=crate::ordinary_chat_random_id("knowledge-export")?;
    let part=target.with_extension(format!("json.{nonce}.part"));
    let mut file=fs::OpenOptions::new().write(true).create_new(true).open(&part).map_err(|_|"JSON_EXPORT_WRITE")?;
    let result=(||{
        file.write_all(&prepared.bytes).and_then(|_|file.sync_all()).map_err(|_|"JSON_EXPORT_WRITE")?;
        drop(file);
        verify(&fs::read(&part).map_err(|_|"JSON_EXPORT_PART_READBACK")?,&hash,&prepared.manifest)?;
        safe_target(target)?;
        fs::rename(&part,target).map_err(|_|"JSON_EXPORT_ATOMIC_MOVE")?;
        let bytes=fs::read(target).map_err(|_|"JSON_EXPORT_FINAL_READBACK")?;
        verify(&bytes,&hash,&prepared.manifest)?;
        Ok(Receipt{byte_count:bytes.len(),sha256:hash,manifest:prepared.manifest})
    })();
    if result.is_err() {let _=fs::remove_file(&part);}
    result
}

#[cfg(test)]
mod tests {
    use super::*;
    fn fixture()->Connection {
        let c=Connection::open_in_memory().unwrap();c.execute_batch("CREATE TABLE workspace_exchange(workspace_id TEXT PRIMARY KEY,exchange_json TEXT); CREATE TABLE desktop_json_knowledge_sources_v1(workspace_id TEXT,knowledge_id TEXT,task_id TEXT,ordinal INTEGER); CREATE TABLE desktop_json_knowledge_items_v1(task_id TEXT,ordinal INTEGER,candidate_json TEXT); CREATE TABLE desktop_markdown_knowledge_sources_v1(workspace_id TEXT,knowledge_id TEXT);").unwrap();
        let item=|id:&str,status:&str| json!({"id":id,"title":format!("Title {id}"),"body":"inert body\n</textarea><script>data()</script>","tags":["local"],"scope":"GLOBAL","projectId":null,"status":status,"revision":3,"contentHash":"a".repeat(64)});
        let exchange=json!({"knowledge":[item("a","ACTIVE"),item("b","ACTIVE"),item("deleted","DELETED"),item("archived","ARCHIVED")],"memory":[{"body":"private unrelated memory"}],"attachments":[{"path":"private unrelated path"}]});c.execute("INSERT INTO workspace_exchange VALUES('w',?1)",[serde_json::to_string(&exchange).unwrap()]).unwrap();c
    }
    fn prepare_two(c:&mut Connection)->Prepared { prepare(c,"w",&["b".into(),"a".into()],"2026-10-03T00:00:00Z").unwrap() }
    #[test] fn only_exact_active_selection_is_exported_sorted_with_version_facts() {
        let mut c=fixture();let p=prepare_two(&mut c);let d=crate::desktop_json_knowledge_v1::parse(&p.bytes).unwrap();assert_eq!(d.items.iter().map(|x|x.source.stable_id.as_str()).collect::<Vec<_>>(),vec!["a","b"]);assert_eq!(d.items[0].source.revision,"3");assert_eq!(d.items[0].source.content_hash,"a".repeat(64));assert_eq!(d.items[0].source.kind,"LOCAL_KNOWLEDGE");assert_eq!(p.manifest.item_count,2);
        let raw=String::from_utf8(p.bytes).unwrap();assert!(!raw.contains("private unrelated") && !raw.contains("deleted") && !raw.contains("archived"));
        for id in ["missing","deleted","archived"] {assert!(prepare(&mut c,"w",&[id.into()],"2026-10-03T00:00:00Z").is_err());}assert!(prepare(&mut c,"other",&["a".into()],"2026-10-03T00:00:00Z").is_err());assert!(prepare(&mut c,"w",&[],"2026-10-03T00:00:00Z").is_err());assert!(prepare(&mut c,"w",&["a".into(),"a".into()],"2026-10-03T00:00:00Z").is_err());
    }
    #[test] fn imported_safe_source_and_markdown_origin_survive_without_private_paths() {
        let mut c=fixture();let candidate=json!({"content":{"title":"Original","body":"original","tags":[]},"source":{"stableId":"old-source","revision":"7","contentHash":"b".repeat(64),"kind":"IMPORTED_TEXT","summary":"safe origin summary","requestedScope":"GLOBAL","projectRef":null}});
        c.execute("INSERT INTO desktop_json_knowledge_items_v1 VALUES('t',0,?1)",[candidate.to_string()]).unwrap();c.execute_batch("INSERT INTO desktop_json_knowledge_sources_v1 VALUES('w','a','t',0);INSERT INTO desktop_markdown_knowledge_sources_v1 VALUES('w','b');").unwrap();let p=prepare_two(&mut c);let d=crate::desktop_json_knowledge_v1::parse(&p.bytes).unwrap();assert_eq!(d.items[0].source.kind,"IMPORTED_TEXT");assert_eq!(d.items[0].source.summary,"safe origin summary");assert_eq!(d.items[0].source.revision,"3");assert_eq!(d.items[0].source.stable_id,"a");assert_eq!(d.items[1].source.kind,"MARKDOWN_IMPORT");
        c.execute("INSERT INTO desktop_markdown_knowledge_sources_v1 VALUES('w','a')",[]).unwrap();assert!(prepare(&mut c,"w",&["a".into()],"2026-10-03T00:00:00Z").is_err());
    }
    #[test] fn pdf_page_origin_exports_safe_summary_and_refuses_mixed_origin() {
        let mut c=fixture();c.execute_batch("CREATE TABLE desktop_pdf_knowledge_sources_v1(workspace_id TEXT,knowledge_id TEXT,page_number INTEGER,source_hash TEXT);INSERT INTO desktop_pdf_knowledge_sources_v1 VALUES('w','a',3,'private provenance is not exported');").unwrap();
        let p=prepare_two(&mut c);let d=crate::desktop_json_knowledge_v1::parse(&p.bytes).unwrap();
        assert_eq!(d.items[0].source.kind,"PDF_TEXT_IMPORT");assert_eq!(d.items[0].source.summary,"confirmed local PDF text, page 3");
        assert!(!String::from_utf8(p.bytes).unwrap().contains("private provenance"));
        c.execute("INSERT INTO desktop_markdown_knowledge_sources_v1 VALUES('w','a')",[]).unwrap();
        assert_eq!(prepare(&mut c,"w",&["a".into()],"2026-10-03T00:00:00Z").err().unwrap(),"JSON_EXPORT_SOURCE_AMBIGUOUS");
    }
    #[test] fn atomic_overwrite_readback_and_strict_adapter_roundtrip() {
        let mut c=fixture();let dir=tempfile::tempdir().unwrap();let target=dir.path().join("selected.json");fs::write(&target,b"previous export").unwrap();let p=prepare_two(&mut c);let expected=p.bytes.clone();let receipt=write(p,&target).unwrap();let bytes=fs::read(&target).unwrap();assert_eq!(bytes,expected);assert_eq!(receipt.sha256,crate::sha256(&bytes));assert_eq!(receipt.byte_count,bytes.len());verify(&bytes,&receipt.sha256,&receipt.manifest).unwrap();assert_eq!(crate::desktop_json_knowledge_v1::parse(&bytes).unwrap().items.len(),2);assert_eq!(fs::read_dir(dir.path()).unwrap().count(),1);
    }
    #[test] fn web_source_exports_only_safe_kind_and_refuses_ambiguous_or_corrupt_facts() {
        let mut c=fixture();
        c.execute_batch("CREATE TABLE desktop_web_knowledge_sources_v1(workspace_id TEXT,knowledge_id TEXT,display_url TEXT,raw_html_hash TEXT);INSERT INTO desktop_web_knowledge_sources_v1 VALUES('w','a','https://public.example.org/path','private raw provenance');").unwrap();
        let p=prepare_two(&mut c);let d=crate::desktop_json_knowledge_v1::parse(&p.bytes).unwrap();
        assert_eq!(d.items[0].source.kind,"WEB_TEXT_SNAPSHOT");
        assert_eq!(d.items[0].source.summary,"confirmed local webpage text snapshot");
        assert_eq!(d.items[1].source.kind,"LOCAL_KNOWLEDGE");
        let raw=String::from_utf8(p.bytes).unwrap();
        assert!(!raw.contains("public.example.org") && !raw.contains("private raw provenance"));
        c.execute("INSERT INTO desktop_markdown_knowledge_sources_v1 VALUES('w','a')",[]).unwrap();
        assert_eq!(prepare(&mut c,"w",&["a".into()],"2026-10-03T00:00:00Z").err().unwrap(),"JSON_EXPORT_SOURCE_AMBIGUOUS");
        c.execute("DELETE FROM desktop_markdown_knowledge_sources_v1",[]).unwrap();
        for url in ["https://public.example.org/path?ordinary=private", "https://public.example.org/path#secret", "https://user:secret@public.example.org/", "http://public.example.org/", "https://127.0.0.1/", "https://public.example.org/?api_key=synthetic"] {
            c.execute("UPDATE desktop_web_knowledge_sources_v1 SET display_url=?1",[url]).unwrap();
            assert_eq!(prepare(&mut c,"w",&["a".into()],"2026-10-03T00:00:00Z").err().unwrap(),"JSON_EXPORT_SOURCE_INVALID");
        }
        c.execute_batch("ALTER TABLE desktop_web_knowledge_sources_v1 RENAME COLUMN display_url TO invalid_url").unwrap();
        assert_eq!(prepare(&mut c,"w",&["a".into()],"2026-10-03T00:00:00Z").err().unwrap(),"JSON_EXPORT_SOURCE_READ");
    }
    #[test] fn tamper_manifest_and_invalid_destination_cannot_report_success() {
        let mut c=fixture();let p=prepare_two(&mut c);let hash=crate::sha256(&p.bytes);let mut changed=p.bytes.clone();changed[0]=b'[';assert_eq!(verify(&changed,&hash,&p.manifest).unwrap_err(),"JSON_EXPORT_READBACK_HASH");let mut manifest=p.manifest.clone();manifest.item_count=99;assert_eq!(verify(&p.bytes,&hash,&manifest).unwrap_err(),"JSON_EXPORT_READBACK_MANIFEST");manifest=p.manifest.clone();manifest.fidelity="unverified expanded fidelity".into();assert_eq!(verify(&p.bytes,&hash,&manifest).unwrap_err(),"JSON_EXPORT_READBACK_MANIFEST");
        let dir=tempfile::tempdir().unwrap();let target=dir.path().join("kept.json");fs::write(&target,b"keep").unwrap();let broken=Prepared{bytes:b"{}".to_vec(),manifest:p.manifest.clone()};assert!(write(broken,&target).is_err());assert_eq!(fs::read(target).unwrap(),b"keep");assert!(write(prepare_two(&mut c),Path::new("relative.json")).is_err());assert!(write(prepare_two(&mut c),&dir.path().join("wrong.txt")).is_err());assert!(write(prepare_two(&mut c),&dir.path().join("absent/target.json")).is_err());assert_eq!(fs::read_dir(dir.path()).unwrap().count(),1);
    }
    #[test] fn unsafe_or_nonlossless_formal_records_refuse_before_file_creation() {
        let mut c=fixture();let raw:String=c.query_row("SELECT exchange_json FROM workspace_exchange",[],|r|r.get(0)).unwrap();let original:Value=serde_json::from_str(&raw).unwrap();
        for (field,value) in [("body",json!(" trailing space ")),("tags",json!(["LOCAL"])),("contentHash",json!("unknown")),("revision",json!(0)),("body",json!("Bearer abcdefghijklmnopqrst"))] {
            let mut changed=original.clone();changed["knowledge"][0][field]=value;c.execute("UPDATE workspace_exchange SET exchange_json=?1",[changed.to_string()]).unwrap();assert!(prepare(&mut c,"w",&["a".into()],"2026-10-03T00:00:00Z").is_err(),"{field}");
        }
    }
}
