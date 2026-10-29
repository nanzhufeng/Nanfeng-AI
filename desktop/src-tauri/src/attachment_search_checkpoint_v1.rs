//! Attempt-owned, device-local normalized evidence. Never export or store raw provider bodies.
use crate::desktop_ordinary_chat_v1::Failure;
use serde::{Deserialize, Serialize};
#[derive(Clone, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Entry {
    pub text: String,
    pub footer: String,
    pub model: String,
    pub created_at: i64,
    pub invocation: String,
}
pub trait Cache {
    fn get(&mut self, item: &str) -> Result<Option<Entry>, Failure>;
    fn put(&mut self, item: &str, value: Entry) -> Result<(), Failure>;
    fn coverage(&mut self, _planned: usize, _covered: usize) -> Result<(), Failure> {
        Ok(())
    }
}
pub struct None;
impl Cache for None {
    fn get(&mut self, _: &str) -> Result<Option<Entry>, Failure> {
        Ok(std::option::Option::None)
    }
    fn put(&mut self, _: &str, _: Entry) -> Result<(), Failure> {
        Ok(())
    }
}
pub struct Callbacks<L, S, C> {
    pub load: L,
    pub save: S,
    pub coverage: C,
}
impl<L, S, C> Cache for Callbacks<L, S, C>
where
    L: FnMut(&str) -> Result<Option<Entry>, Failure>,
    S: FnMut(&str, Entry) -> Result<(), Failure>,
    C: FnMut(usize, usize) -> Result<(), Failure>,
{
    fn get(&mut self, item: &str) -> Result<Option<Entry>, Failure> {
        (self.load)(item)
    }
    fn put(&mut self, item: &str, value: Entry) -> Result<(), Failure> {
        (self.save)(item, value)
    }
    fn coverage(&mut self, planned: usize, covered: usize) -> Result<(), Failure> {
        (self.coverage)(planned, covered)
    }
}
pub const SCHEMA: &str = "CREATE TABLE IF NOT EXISTS desktop_attachment_search_checkpoints(attempt_id TEXT NOT NULL,scope_fingerprint TEXT NOT NULL,item_id TEXT NOT NULL,evidence_json TEXT NOT NULL,created_at_ms INTEGER NOT NULL CHECK(created_at_ms>0),PRIMARY KEY(attempt_id,scope_fingerprint,item_id),FOREIGN KEY(attempt_id) REFERENCES desktop_ordinary_chat_attempts(attempt_id) ON DELETE CASCADE);
CREATE TABLE IF NOT EXISTS desktop_attachment_search_coverage(attempt_id TEXT PRIMARY KEY,scope_fingerprint TEXT NOT NULL,planned INTEGER NOT NULL CHECK(planned>=0),covered INTEGER NOT NULL CHECK(covered>=0 AND covered<=planned),limited INTEGER NOT NULL CHECK(limited IN (0,1)),updated_at_ms INTEGER NOT NULL,FOREIGN KEY(attempt_id) REFERENCES desktop_ordinary_chat_attempts(attempt_id) ON DELETE CASCADE);";
pub fn migrate(c: &rusqlite::Connection) -> Result<(), String> {c.execute_batch(SCHEMA).map_err(|_|"核验检查点迁移失败".into())}
pub(crate) fn schema_valid(c:&rusqlite::Connection)->rusqlite::Result<bool>{
 if !c.query_row("SELECT EXISTS(SELECT 1 FROM sqlite_master WHERE type='table' AND name='desktop_ordinary_chat_attempts')",[],|r|r.get::<_,bool>(0))?{return Ok(false);}
 for (table,expected) in [("desktop_attachment_search_checkpoints",vec![("attempt_id","TEXT"),("scope_fingerprint","TEXT"),("item_id","TEXT"),("evidence_json","TEXT"),("created_at_ms","INTEGER")]),("desktop_attachment_search_coverage",vec![("attempt_id","TEXT"),("scope_fingerprint","TEXT"),("planned","INTEGER"),("covered","INTEGER"),("limited","INTEGER"),("updated_at_ms","INTEGER")])]{
  let columns=c.prepare(&format!("PRAGMA table_info({table})"))?.query_map([],|r|Ok((r.get::<_,String>(1)?,r.get::<_,String>(2)?)))?.collect::<Result<Vec<_>,_>>()?;
  if columns!=expected.iter().map(|(n,t)|(n.to_string(),t.to_string())).collect::<Vec<_>>(){return Ok(false);}
  let keys=c.prepare(&format!("PRAGMA foreign_key_list({table})"))?.query_map([],|r|Ok((r.get::<_,String>(2)?,r.get::<_,String>(3)?,r.get::<_,String>(4)?,r.get::<_,String>(6)?)))?.collect::<Result<Vec<_>,_>>()?;
  if keys!=vec![("desktop_ordinary_chat_attempts".into(),"attempt_id".into(),"attempt_id".into(),"CASCADE".into())]{return Ok(false);}
 }
 Ok(true)
}
pub(crate) fn migrate_committed_area(c:&mut rusqlite::Connection,expected:&str)->Result<(),String>{
 use crate::desktop_schema_source::{classify,SchemaSource};
 let version=c.pragma_query_value(std::option::Option::None,"user_version",|r|r.get(0)).map_err(|_|"检查点版本不可读")?;
 match classify(c,version).map_err(|_|"检查点来源不可读")?{
  SchemaSource::Common58{area,split_done:true} | SchemaSource::Common59{area,split_done:true} if area==expected=>return Ok(()),
  SchemaSource::Common57{area,split_done:true} if area==expected=>{},
  _=>return Err("检查点迁移来源拒绝；原库保留".into()),
 }
 for table in ["desktop_attachment_search_checkpoints","desktop_attachment_search_coverage"]{let exists:bool=c.query_row("SELECT EXISTS(SELECT 1 FROM sqlite_master WHERE name=?1)",[table],|r|r.get(0)).map_err(|_|"检查点目录不可读")?;if exists{return Err("发现无版本检查点结构；原库保留".into());}}
 let tx=c.transaction().map_err(|_|"检查点事务不可用")?;migrate(&tx)?;tx.pragma_update(std::option::Option::None,"user_version",58).map_err(|_|"检查点版本写入失败")?;
 if !matches!(classify(&tx,58).map_err(|_|"检查点结构核对失败")?,SchemaSource::Common58{area,split_done:true} | SchemaSource::Common59{area,split_done:true} if area==expected){return Err("检查点迁移结构不符；已回滚".into());}
 tx.commit().map_err(|_|"检查点迁移提交失败".into())
}
pub fn linked_receipt(ledger:&crate::usage_ledger_v1::Ledger,attempt:&str,value:&Entry)->Result<bool,String>{
 let suffix=format!("-{}",crate::sha256(value.invocation.as_bytes()));
 let mut matches=0;
 for row in ledger.entries_for_execution(attempt)?{
  if row.kind==crate::usage_ledger_v1::Kind::FinalMeasured && row.invocation_id.ends_with(&suffix) && row.requested_model_id==value.model && row.actual_model_id.as_deref()==Some(value.model.as_str()) && row.occurred_at_ms<=value.created_at && ledger.call_metadata(&row.entry_id)?.is_some_and(|m|m.role=="RETRIEVAL"){
   if ledger.call_outcome(&row)?.is_some_and(|outcome|outcome.state!=crate::chat_call_accounting_v1::CallOutcomeState::Completed){return Err("检索检查点关联的调用未完成或结果未确认".into());}
   matches+=1;
  }
 }
 Ok(matches==1)
}
pub fn load(c:&rusqlite::Connection,ledger:&crate::usage_ledger_v1::Ledger,attempt:&str,scope:&str,item:&str)->Result<Option<Entry>,String>{
 purge_expired(c,crate::system_now_millis())?;
 if !parent_is_live(c,attempt)? {return Ok(std::option::Option::None);}
 use rusqlite::OptionalExtension;
 let row:Option<(String,i64)>=c.query_row("SELECT evidence_json,created_at_ms FROM desktop_attachment_search_checkpoints WHERE attempt_id=?1 AND scope_fingerprint=?2 AND item_id=?3",rusqlite::params![attempt,scope,item],|r|Ok((r.get(0)?,r.get(1)?))).optional().map_err(|_|"检查点读取失败")?;
 let Some((encoded,created))=row else{return Ok(std::option::Option::None)};
 let value:Entry=serde_json::from_str(&encoded).map_err(|_|"检查点结构不符")?;
 if value.created_at!=created || !linked_receipt(ledger,attempt,&value)?{return Ok(std::option::Option::None);}
 Ok(Some(value))
}
pub fn save(c:&rusqlite::Connection,ledger:&crate::usage_ledger_v1::Ledger,attempt:&str,scope:&str,item:&str,value:Entry)->Result<(),String>{
 purge_expired(c,crate::system_now_millis())?;
 if !parent_is_live(c,attempt)? {return Err("会话或消息已移除；未保存检索证据".into());}
 if value.created_at<=0 || value.invocation.trim().is_empty() || value.text.len()>32768 || value.footer.len()>32768 || !linked_receipt(ledger,attempt,&value)?{return Err("检查点缺少已完成检索计量关联".into());}
 let encoded=serde_json::to_string(&value).map_err(|_|"检查点编码失败")?;
 c.execute("INSERT INTO desktop_attachment_search_checkpoints VALUES(?1,?2,?3,?4,?5) ON CONFLICT(attempt_id,scope_fingerprint,item_id) DO UPDATE SET evidence_json=excluded.evidence_json,created_at_ms=excluded.created_at_ms",rusqlite::params![attempt,scope,item,encoded,value.created_at]).map_err(|_|"检查点保存失败")?;Ok(())
}
pub fn coverage(c:&rusqlite::Connection,attempt:&str,scope:&str,planned:usize,covered:usize,limited:bool)->Result<(),String>{
 if !parent_is_live(c,attempt)? {return Err("会话或消息已移除；未保存检索范围".into());}
 if covered>planned{return Err("检查点范围计数无效".into());}
 if planned==0 && !limited {c.execute("DELETE FROM desktop_attachment_search_coverage WHERE attempt_id=?1",[attempt]).map_err(|_|"空检索范围清理失败")?;return Ok(());}
 c.execute("INSERT INTO desktop_attachment_search_coverage VALUES(?1,?2,?3,?4,?5,?6) ON CONFLICT(attempt_id) DO UPDATE SET scope_fingerprint=excluded.scope_fingerprint,planned=excluded.planned,covered=excluded.covered,limited=excluded.limited,updated_at_ms=excluded.updated_at_ms",rusqlite::params![attempt,scope,planned as i64,covered as i64,limited as i64,crate::system_now_millis()]).map_err(|_|"检查点范围保存失败")?;Ok(())
}
pub fn fingerprint(value: &str) -> String {
    use sha2::Digest;
    format!("{:x}", sha2::Sha256::digest(value.as_bytes()))
}
/// Remove only local derived evidence; usage receipts belong to the separate ledger.
pub(crate) fn purge_expired(c: &rusqlite::Connection, now: i64) -> Result<(), String> {
    if now <= 0 { return Err("检查点清理时间无效".into()); }
    let cutoff = now.saturating_sub(600_000);
    let tx = c.unchecked_transaction().map_err(|_| "检查点清理事务不可用")?;
    tx.execute("DELETE FROM desktop_attachment_search_checkpoints WHERE created_at_ms<=0 OR created_at_ms<?1 OR created_at_ms>?2", rusqlite::params![cutoff,now]).map_err(|_| "检查点过期清理失败")?;
    // Coverage is durable progress, not reusable evidence. It lives with the parent Attempt.
    tx.commit().map_err(|_| "检查点清理提交失败".into())
}

pub(crate) fn workspace_coverage(c: &rusqlite::Connection, workspace: &str) -> Result<std::collections::HashMap<String, serde_json::Value>, String> {
    let exists: bool = c.query_row("SELECT EXISTS(SELECT 1 FROM sqlite_master WHERE type='table' AND name='desktop_attachment_search_coverage')", [], |r| r.get(0)).map_err(|_| "检索范围目录读取失败")?;
    if !exists { return Ok(Default::default()); }
    let mut statement=c.prepare("SELECT a.assistant_message_id,c.planned,c.covered,c.limited FROM desktop_attachment_search_coverage c JOIN desktop_ordinary_chat_attempts a ON a.attempt_id=c.attempt_id WHERE a.workspace_id=?1").map_err(|_| "检索范围读取失败")?;
    let rows=statement.query_map([workspace],|r|Ok((r.get::<_,String>(0)?,r.get::<_,i64>(1)?,r.get::<_,i64>(2)?,r.get::<_,i64>(3)?))).map_err(|_| "检索范围读取失败")?;
    let mut projection=std::collections::HashMap::new();
    for row in rows {
        let (message,planned,covered,limited)=row.map_err(|_| "检索范围记录无效")?;
        if planned<0 || covered<0 || covered>planned || !matches!(limited,0|1) { return Err("检索范围计数无效".into()); }
        if planned==0 && limited==0 {continue;}
        projection.insert(message,serde_json::json!({"planned":planned,"covered":covered,"limited":limited==1}));
    }
    Ok(projection)
}
fn live_in_exchange(exchange: &serde_json::Value, conversation: &str, user: &str, assistant: &str) -> Result<bool,String> {
    let conversations=exchange["conversations"].as_array().ok_or("检索检查点的会话集合无效")?;
    let Some(item)=conversations.iter().find(|item|item["id"].as_str()==Some(conversation)) else {return Ok(false)};
    if item["deleted"]==true {return Ok(false);}
    let messages=item["messages"].as_array().ok_or("检索检查点的消息集合无效")?;
    Ok(messages.iter().any(|m|m["id"].as_str()==Some(user)) && messages.iter().any(|m|m["id"].as_str()==Some(assistant)))
}
pub(crate) fn parent_is_live(c:&rusqlite::Connection,attempt:&str)->Result<bool,String> {
    use rusqlite::OptionalExtension;
    let row:Option<(String,String,String,String,String)>=c.query_row("SELECT x.exchange_json,a.conversation_id,a.user_message_id,a.assistant_message_id,a.state FROM desktop_ordinary_chat_attempts a JOIN workspace_exchange x ON x.workspace_id=a.workspace_id WHERE a.attempt_id=?1",[attempt],|r|Ok((r.get(0)?,r.get(1)?,r.get(2)?,r.get(3)?,r.get(4)?))).optional().map_err(|_|"检索检查点的父引用不可读")?;
    let Some((encoded,conversation,user,assistant,state))=row else {return Ok(false)};
    if !matches!(state.as_str(),"PENDING"|"RUNNING"|"RESPONSE_COMMITTED"|"COMPLETED_ACCOUNTING_PENDING"|"COMPLETED") {return Ok(false);}
    live_in_exchange(&serde_json::from_str(&encoded).map_err(|_|"检索检查点的工作区无效")?,&conversation,&user,&assistant)
}
fn source_blocks<'a>(exchange: &'a serde_json::Value, conversation: &str, user: &str) -> Option<&'a serde_json::Value> {
    exchange["conversations"].as_array()?.iter().find(|c|c["id"].as_str()==Some(conversation))?["messages"]
        .as_array()?.iter().find(|m|m["id"].as_str()==Some(user)).map(|m|&m["blocks"])
}
/// In the workspace transaction, stop removed active contexts while retaining Attempts and receipts.
pub(crate) fn reconcile_workspace(c:&rusqlite::Connection,workspace:&str,exchange:&serde_json::Value)->Result<(),String> {
    let exists:bool=c.query_row("SELECT EXISTS(SELECT 1 FROM sqlite_master WHERE type='table' AND name='desktop_attachment_search_checkpoints')",[],|r|r.get(0)).map_err(|_|"检索检查点目录不可读")?;
    if !exists {return Ok(());}
    let mut statement=c.prepare("SELECT attempt_id,conversation_id,user_message_id,assistant_message_id FROM desktop_ordinary_chat_attempts WHERE workspace_id=?1").map_err(|_|"检索检查点父记录不可读")?;
    let rows=statement.query_map([workspace],|r|Ok((r.get::<_,String>(0)?,r.get::<_,String>(1)?,r.get::<_,String>(2)?,r.get::<_,String>(3)?))).map_err(|_|"检索检查点父记录不可读")?.collect::<Result<Vec<_>,_>>().map_err(|_|"检索检查点父记录无效")?;
    drop(statement);
    use rusqlite::OptionalExtension;
    let previous:Option<String>=c.query_row("SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?1",[workspace],|r|r.get(0)).optional().map_err(|_|"原检索材料不可读")?;
    let previous=previous.map(|text|serde_json::from_str::<serde_json::Value>(&text)).transpose().map_err(|_|"原检索材料无效")?;
    for (attempt,conversation,user,assistant) in rows {
        let source_changed=previous.as_ref().is_some_and(|before|source_blocks(before,&conversation,&user)!=source_blocks(exchange,&conversation,&user));
        let live=live_in_exchange(exchange,&conversation,&user,&assistant)?;
        if !live || source_changed {
            let code=if live {"SOURCE_MESSAGE_CHANGED"} else {"CONVERSATION_REMOVED"};
            c.execute("UPDATE desktop_ordinary_chat_attempts SET state='CANCELLED',safe_error_code=?3,updated_at_ms=?2,terminal_at_ms=?2 WHERE attempt_id=?1 AND state IN ('PENDING','RUNNING')",rusqlite::params![attempt,crate::system_now_millis(),code]).map_err(|_|"已移除上下文的检索执行未停止")?;
            for table in ["desktop_attachment_search_checkpoints","desktop_attachment_search_coverage"] {
                c.execute(&format!("DELETE FROM {table} WHERE attempt_id=?1"),[&attempt]).map_err(|_|"检索派生记录清理失败")?;
            }
        }
    }
    Ok(())
}
pub(crate) fn reconcile_startup(c:&rusqlite::Connection)->Result<(),String> {
    let tx=c.unchecked_transaction().map_err(|_|"检索检查点恢复事务不可用")?;
    let rows=tx.prepare("SELECT workspace_id,exchange_json FROM workspace_exchange").map_err(|_|"检索检查点工作区不可读")?.query_map([],|r|Ok((r.get::<_,String>(0)?,r.get::<_,String>(1)?))).map_err(|_|"检索检查点工作区不可读")?.collect::<Result<Vec<_>,_>>().map_err(|_|"检索检查点工作区无效")?;
    for (workspace,encoded) in rows {reconcile_workspace(&tx,&workspace,&serde_json::from_str(&encoded).map_err(|_|"检索检查点工作区无效")?)?;}
    // A missing projection must invalidate derived state without deleting historical Attempts.
    let missing=tx.prepare("SELECT DISTINCT a.workspace_id FROM desktop_ordinary_chat_attempts a WHERE NOT EXISTS(SELECT 1 FROM workspace_exchange x WHERE x.workspace_id=a.workspace_id)").map_err(|_|"检索检查点父工作区不可读")?.query_map([],|r|r.get::<_,String>(0)).map_err(|_|"检索检查点父工作区不可读")?.collect::<Result<Vec<_>,_>>().map_err(|_|"检索检查点父工作区无效")?;
    for workspace in missing {reconcile_workspace(&tx,&workspace,&serde_json::json!({"conversations":[]}))?;}
    tx.commit().map_err(|_|"检索检查点恢复清理提交失败".into())
}
pub fn fresh(entry: &Entry, model: &str, query: &str, now: i64) -> bool {
 static VOLATILE:std::sync::LazyLock<regex::Regex>=std::sync::LazyLock::new(||regex::Regex::new(r"(?i)当前|最新|实时|今日|今天|股价|汇率|收益率|天气|气温|价格|\b(?:latest|current|today|real.time|exchange rate|stock price|weather)\b").unwrap());
 entry.created_at>0 && now.checked_sub(entry.created_at).is_some_and(|age|(0..=600_000).contains(&age)) && entry.model==model && !VOLATILE.is_match(query)
 && !entry.invocation.trim().is_empty() && !entry.text.trim().is_empty() && entry.text.len()<=32768 && entry.footer.len()<=32768
}

#[cfg(test)]
mod tests {
    fn seed_parent(c:&rusqlite::Connection,attempt:&str,workspace:&str,assistant:&str) {
        c.execute_batch("PRAGMA foreign_keys=ON;CREATE TABLE IF NOT EXISTS desktop_ordinary_chat_attempts(attempt_id TEXT PRIMARY KEY,workspace_id TEXT,conversation_id TEXT,user_message_id TEXT,assistant_message_id TEXT,state TEXT,safe_error_code TEXT,updated_at_ms INTEGER,terminal_at_ms INTEGER);CREATE TABLE IF NOT EXISTS workspace_exchange(workspace_id TEXT PRIMARY KEY,exchange_json TEXT);").unwrap();
        c.execute("INSERT INTO desktop_ordinary_chat_attempts(attempt_id,workspace_id,conversation_id,user_message_id,assistant_message_id,state) VALUES(?1,?2,'conversation','user',?3,'RUNNING')",rusqlite::params![attempt,workspace,assistant]).unwrap();
        c.execute("INSERT INTO workspace_exchange VALUES(?1,?2)",rusqlite::params![workspace,serde_json::json!({"conversations":[{"id":"conversation","deleted":false,"messages":[{"id":"user"},{"id":assistant}]}]}).to_string()]).unwrap();
    }
    fn missing_projection_fixture()->rusqlite::Connection {
        let c=rusqlite::Connection::open_in_memory().unwrap();seed_parent(&c,"lost-active","missing-workspace","lost-reply");super::migrate(&c).unwrap();
        c.execute("DELETE FROM workspace_exchange WHERE workspace_id='missing-workspace'",[]).unwrap();
        c.execute("INSERT INTO desktop_ordinary_chat_attempts(attempt_id,workspace_id,conversation_id,user_message_id,assistant_message_id,state) VALUES('lost-complete','missing-workspace','conversation','user','complete-reply','COMPLETED')",[]).unwrap();
        c.execute("INSERT INTO desktop_ordinary_chat_attempts(attempt_id,workspace_id,conversation_id,user_message_id,assistant_message_id,state) VALUES('live','retained-workspace','conversation','user','retained-reply','RUNNING')",[]).unwrap();
        c.execute("INSERT INTO workspace_exchange VALUES('retained-workspace',?1)",[serde_json::json!({"conversations":[{"id":"conversation","messages":[{"id":"user","blocks":[{"kind":"TEXT","text":"合成保留材料"}]},{"id":"retained-reply"}]}]}).to_string()]).unwrap();
        for attempt in ["lost-active","lost-complete","live"] {
            c.execute("INSERT INTO desktop_attachment_search_checkpoints VALUES(?1,'scope','A1',?2,1000)",rusqlite::params![attempt,serde_json::to_string(&synthetic_entry()).unwrap()]).unwrap();
            c.execute("INSERT INTO desktop_attachment_search_coverage VALUES(?1,'scope',1,1,0,1000)",[attempt]).unwrap();
        }
        c
    }
    #[test]
    fn startup_missing_projection_cleans_only_derived_state_and_preserves_terminal_history() {
        let c=missing_projection_fixture();let retained:String=c.query_row("select exchange_json from workspace_exchange where workspace_id='retained-workspace'",[],|r|r.get(0)).unwrap();
        super::reconcile_startup(&c).unwrap();
        for table in ["desktop_attachment_search_checkpoints","desktop_attachment_search_coverage"] {let rows=c.prepare(&format!("select attempt_id from {table}")).unwrap().query_map([],|r|r.get::<_,String>(0)).unwrap().collect::<Result<Vec<_>,_>>().unwrap();assert_eq!(rows,vec!["live"],"{table}");}
        let states=c.prepare("select attempt_id,state from desktop_ordinary_chat_attempts order by attempt_id").unwrap().query_map([],|r|Ok((r.get::<_,String>(0)?,r.get::<_,String>(1)?))).unwrap().collect::<Result<Vec<_>,_>>().unwrap();assert_eq!(states,vec![("live".into(),"RUNNING".into()),("lost-active".into(),"CANCELLED".into()),("lost-complete".into(),"COMPLETED".into())]);
        assert_eq!(c.query_row("select exchange_json from workspace_exchange where workspace_id='retained-workspace'",[],|r|r.get::<_,String>(0)).unwrap(),retained);super::reconcile_startup(&c).unwrap();assert_eq!(c.query_row("select count(*) from desktop_ordinary_chat_attempts",[],|r|r.get::<_,i64>(0)).unwrap(),3);assert!(c.prepare("pragma foreign_key_check").unwrap().query([]).unwrap().next().unwrap().is_none());
    }
    #[test]
    fn startup_missing_projection_cleanup_rolls_back_on_derived_delete_failure() {
        let c=missing_projection_fixture();c.execute_batch("CREATE TRIGGER owned_orphan_cleanup_fault BEFORE DELETE ON desktop_attachment_search_checkpoints WHEN OLD.attempt_id='lost-active' BEGIN SELECT RAISE(ABORT,'owned rollback fixture');END;").unwrap();assert!(super::reconcile_startup(&c).is_err());
        assert_eq!(c.query_row("select state from desktop_ordinary_chat_attempts where attempt_id='lost-active'",[],|r|r.get::<_,String>(0)).unwrap(),"RUNNING");for table in ["desktop_attachment_search_checkpoints","desktop_attachment_search_coverage"] {assert_eq!(c.query_row(&format!("select count(*) from {table}"),[],|r|r.get::<_,i64>(0)).unwrap(),3);}
        c.execute_batch("DROP TRIGGER owned_orphan_cleanup_fault").unwrap();super::reconcile_startup(&c).unwrap();assert_eq!(c.query_row("select count(*) from desktop_attachment_search_checkpoints",[],|r|r.get::<_,i64>(0)).unwrap(),1);
    }
    #[test]
    fn startup_invalid_projection_is_an_error_without_committing_orphan_cleanup() {
        let c=missing_projection_fixture();c.execute("UPDATE workspace_exchange SET exchange_json='invalid' WHERE workspace_id='retained-workspace'",[]).unwrap();
        assert!(super::reconcile_startup(&c).is_err());
        assert_eq!(c.query_row("select state from desktop_ordinary_chat_attempts where attempt_id='lost-active'",[],|r|r.get::<_,String>(0)).unwrap(),"RUNNING");
        for table in ["desktop_attachment_search_checkpoints","desktop_attachment_search_coverage"] {assert_eq!(c.query_row(&format!("select count(*) from {table}"),[],|r|r.get::<_,i64>(0)).unwrap(),3);}
    }

    #[test]
    fn metadata_keeps_progress_but_changed_asset_identity_clears_completed_progress() {
        let c=rusqlite::Connection::open_in_memory().unwrap();seed_parent(&c,"first","workspace","reply");super::migrate(&c).unwrap();
        let original=serde_json::json!({"conversations":[{"id":"conversation","title":"old","messages":[{"id":"user","blocks":[{"kind":"ASSET_REF","asset":{"id":"asset","sha256":"old"}}]},{"id":"reply"}]}]});
        c.execute("UPDATE workspace_exchange SET exchange_json=?1 WHERE workspace_id='workspace'",[original.to_string()]).unwrap();
        super::coverage(&c,"first","scope",8,4,false).unwrap();
        let mut renamed=original.clone();renamed["conversations"][0]["title"]=serde_json::json!("new");
        super::reconcile_workspace(&c,"workspace",&renamed).unwrap();assert_eq!(super::workspace_coverage(&c,"workspace").unwrap().len(),1);
        c.execute("UPDATE desktop_ordinary_chat_attempts SET state='COMPLETED' WHERE attempt_id='first'",[]).unwrap();
        let mut changed=original.clone();changed["conversations"][0]["messages"][0]["blocks"][0]["asset"]["sha256"]=serde_json::json!("new");
        super::reconcile_workspace(&c,"workspace",&changed).unwrap();assert!(super::workspace_coverage(&c,"workspace").unwrap().is_empty());
        assert_eq!(c.query_row("SELECT state FROM desktop_ordinary_chat_attempts WHERE attempt_id='first'",[],|r|r.get::<_,String>(0)).unwrap(),"COMPLETED");
    }

    #[test]
    fn removing_a_source_attachment_invalidates_only_its_attempt_in_the_transaction() {
        let mut c=rusqlite::Connection::open_in_memory().unwrap();
        seed_parent(&c,"first","workspace","reply");seed_parent(&c,"second","other","other-reply");super::migrate(&c).unwrap();
        let original=serde_json::json!({"conversations":[{"id":"conversation","messages":[{"id":"user","blocks":[{"kind":"TEXT","text":"核验材料"},{"kind":"ASSET_REF","asset":{"id":"asset1","sha256":"original"}}]},{"id":"reply"}]}]});
        c.execute("UPDATE workspace_exchange SET exchange_json=?1 WHERE workspace_id='workspace'",[original.to_string()]).unwrap();
        super::coverage(&c,"first","scope",8,4,false).unwrap();super::coverage(&c,"second","scope",8,4,false).unwrap();
        c.execute("INSERT INTO desktop_attachment_search_checkpoints VALUES('first','scope','A1','{}',1000)",[]).unwrap();
        let mut changed=original.clone();changed["conversations"][0]["messages"][0]["blocks"].as_array_mut().unwrap().pop();
        {let tx=c.transaction().unwrap();super::reconcile_workspace(&tx,"workspace",&changed).unwrap();
         assert_eq!(tx.query_row("SELECT count(*) FROM desktop_attachment_search_checkpoints",[],|r|r.get::<_,i64>(0)).unwrap(),0);}
        assert_eq!(c.query_row("SELECT count(*) FROM desktop_attachment_search_checkpoints",[],|r|r.get::<_,i64>(0)).unwrap(),1);
        {let tx=c.transaction().unwrap();super::reconcile_workspace(&tx,"workspace",&changed).unwrap();tx.execute("UPDATE workspace_exchange SET exchange_json=?1 WHERE workspace_id='workspace'",[changed.to_string()]).unwrap();tx.commit().unwrap();}
        assert!(!super::parent_is_live(&c,"first").unwrap());assert!(super::parent_is_live(&c,"second").unwrap());
        assert!(super::workspace_coverage(&c,"workspace").unwrap().is_empty());assert_eq!(super::workspace_coverage(&c,"other").unwrap().len(),1);
        assert_eq!(c.query_row("SELECT safe_error_code FROM desktop_ordinary_chat_attempts WHERE attempt_id='first'",[],|r|r.get::<_,String>(0)).unwrap(),"SOURCE_MESSAGE_CHANGED");
        c.execute("UPDATE workspace_exchange SET exchange_json=?1 WHERE workspace_id='workspace'",[original.to_string()]).unwrap();
        assert!(!super::parent_is_live(&c,"first").unwrap());
    }

    #[test]
    fn deletion_cleanup_rolls_back_and_late_writes_cannot_resurrect_evidence() {
        let mut c=rusqlite::Connection::open_in_memory().unwrap();seed_parent(&c,"first","workspace","reply");seed_parent(&c,"second","other","other-reply");super::migrate(&c).unwrap();
        super::coverage(&c,"first","scope",8,4,false).unwrap();super::coverage(&c,"second","scope",2,2,false).unwrap();
        c.execute("INSERT INTO desktop_attachment_search_checkpoints VALUES('first','scope','A1','{}',1000)",[]).unwrap();
        let removed=serde_json::json!({"conversations":[{"id":"conversation","deleted":true,"messages":[{"id":"user"},{"id":"reply"}]}]});
        {let tx=c.transaction().unwrap();super::reconcile_workspace(&tx,"workspace",&removed).unwrap();assert_eq!(tx.query_row("SELECT COUNT(*) FROM desktop_attachment_search_checkpoints",[],|r|r.get::<_,i64>(0)).unwrap(),0);}
        assert_eq!(c.query_row("SELECT COUNT(*) FROM desktop_attachment_search_checkpoints",[],|r|r.get::<_,i64>(0)).unwrap(),1);
        c.execute("UPDATE workspace_exchange SET exchange_json=?1 WHERE workspace_id='workspace'",[removed.to_string()]).unwrap();
        super::reconcile_startup(&c).unwrap();assert_eq!(c.query_row("SELECT COUNT(*) FROM desktop_attachment_search_checkpoints",[],|r|r.get::<_,i64>(0)).unwrap(),0);
        assert!(!super::parent_is_live(&c,"first").unwrap());assert!(super::coverage(&c,"first","scope",8,4,false).is_err());
        let restored=serde_json::json!({"conversations":[{"id":"conversation","deleted":false,"messages":[{"id":"user"},{"id":"reply"}]}]});
        c.execute("UPDATE workspace_exchange SET exchange_json=?1 WHERE workspace_id='workspace'",[restored.to_string()]).unwrap();
        assert!(!super::parent_is_live(&c,"first").unwrap());assert!(super::coverage(&c,"first","scope",8,4,false).is_err());
        assert_eq!(c.query_row("SELECT state FROM desktop_ordinary_chat_attempts WHERE attempt_id='first'",[],|r|r.get::<_,String>(0)).unwrap(),"CANCELLED");
        assert!(super::workspace_coverage(&c,"workspace").unwrap().is_empty());assert_eq!(super::workspace_coverage(&c,"other").unwrap().len(),1);
        let missing=serde_json::json!({"conversations":[{"id":"conversation","messages":[{"id":"user"}]}]});
        assert!(!super::live_in_exchange(&missing,"conversation","user","reply").unwrap());
        assert!(!super::live_in_exchange(&serde_json::json!({"conversations":[]}),"conversation","user","reply").unwrap());
    }
    #[test]
    fn zero_item_limited_scope_survives_reopen_without_inventing_completed_queries() {
        let root=tempfile::tempdir().unwrap();let path=root.path().join("scope.sqlite3");
        let c=rusqlite::Connection::open(&path).unwrap();seed_parent(&c,"first","workspace","reply");super::migrate(&c).unwrap();
        super::coverage(&c,"first","scope",0,0,true).unwrap();drop(c);
        let c=rusqlite::Connection::open(&path).unwrap();super::purge_expired(&c,i64::MAX).unwrap();
        assert_eq!(super::workspace_coverage(&c,"workspace").unwrap().get("reply"),Some(&serde_json::json!({"planned":0,"covered":0,"limited":true})));
        super::coverage(&c,"first","scope",0,0,false).unwrap();
        assert!(super::workspace_coverage(&c,"workspace").unwrap().is_empty());
        assert_eq!(c.query_row("SELECT COUNT(*) FROM desktop_attachment_search_coverage",[],|r|r.get::<_,i64>(0)).unwrap(),0);
    }

    #[test]
    fn durable_coverage_is_workspace_scoped_and_survives_evidence_expiry() {
        let c=rusqlite::Connection::open_in_memory().unwrap();
        assert!(super::workspace_coverage(&c,"workspace").unwrap().is_empty());
        seed_parent(&c,"first","workspace","reply");seed_parent(&c,"second","other","other-reply");
        super::migrate(&c).unwrap();
        super::coverage(&c,"first","scope",8,4,true).unwrap();super::coverage(&c,"second","other",2,2,false).unwrap();
        super::purge_expired(&c,i64::MAX).unwrap();
        let projection=super::workspace_coverage(&c,"workspace").unwrap();
        assert_eq!(projection.len(),1);assert_eq!(projection["reply"],serde_json::json!({"planned":8,"covered":4,"limited":true}));
        c.execute("DELETE FROM desktop_ordinary_chat_attempts WHERE attempt_id='first'",[]).unwrap();
        assert!(super::workspace_coverage(&c,"workspace").unwrap().is_empty());
        assert_eq!(super::workspace_coverage(&c,"other").unwrap().len(),1);
        super::coverage(&c,"second","scope",0,0,false).unwrap();
        assert!(super::workspace_coverage(&c,"other").unwrap().is_empty());
    }
    #[test]
    fn expiry_cleans_local_evidence_but_preserves_durable_coverage_and_attempt() {
        let c=rusqlite::Connection::open_in_memory().unwrap();
        c.execute_batch("PRAGMA foreign_keys=ON;CREATE TABLE desktop_ordinary_chat_attempts(attempt_id TEXT PRIMARY KEY);INSERT INTO desktop_ordinary_chat_attempts VALUES('old');").unwrap();
        super::migrate(&c).unwrap();
        for (id,time) in [("expired",999),("boundary",1000),("recent",600999),("future",601001)] {
            c.execute("INSERT INTO desktop_attachment_search_checkpoints VALUES('old','scope',?1,'{}',?2)",rusqlite::params![id,time]).unwrap();
        }
        c.execute_batch("INSERT INTO desktop_attachment_search_coverage VALUES('old','scope',4,2,0,999)").unwrap();
        super::purge_expired(&c,601000).unwrap();
        let remaining=c.prepare("SELECT item_id FROM desktop_attachment_search_checkpoints ORDER BY item_id").unwrap().query_map([],|r|r.get::<_,String>(0)).unwrap().collect::<Result<Vec<_>,_>>().unwrap();
        assert_eq!(remaining,vec!["boundary","recent"]);
        assert_eq!(c.query_row("SELECT COUNT(*) FROM desktop_attachment_search_coverage",[],|r|r.get::<_,i64>(0)).unwrap(),1);
        assert_eq!(c.query_row("SELECT COUNT(*) FROM desktop_ordinary_chat_attempts",[],|r|r.get::<_,i64>(0)).unwrap(),1);
    }
    #[test]
    fn call_outcome_known_failure_is_not_reusable_evidence_or_a_silent_cache_miss() {
        use crate::{usage_ledger_v1 as usage,chat_call_accounting_v1::{Metadata,CallOutcome,CallOutcomeState,CallOutcomeOrigin}};
        let dir=tempfile::tempdir().unwrap();let c=rusqlite::Connection::open(dir.path().join("cache.sqlite3")).unwrap();seed_parent(&c,"old","workspace","reply");super::migrate(&c).unwrap();
        let ledger=usage::Ledger::open(&dir.path().join("ledger")).unwrap();let mut value=synthetic_entry();value.created_at=crate::system_now_millis();
        let pending:usage::Entry=serde_json::from_value(serde_json::json!({"entry_id":"pending","replay_token":"pending","execution_id":"old","conversation_id":"conversation","branch_leaf_message_id":"leaf","invocation_id":format!("old-retrieval-0-{}",crate::sha256(value.invocation.as_bytes())),"attempt_id":"retrieval","kind":"StreamPending","fact_grade":"Estimated","requested_model_id":"model","actual_model_id":null,"input_tokens":null,"output_tokens":null,"charge_micros":null,"currency_code":null,"cost_source":null,"reconciliation_fingerprint":crate::sha256(b"pending"),"source":"DesktopLocal","occurred_at_ms":1000})).unwrap();
        let metadata=Metadata{role:"RETRIEVAL".into(),model_display_name:"Model".into(),..Default::default()};ledger.append_call(&pending,&metadata).unwrap();
        let mut receipt=pending.clone();receipt.entry_id="final".into();receipt.replay_token=receipt.entry_id.clone();receipt.kind=usage::Kind::FinalMeasured;receipt.fact_grade=usage::FactGrade::ProviderReported;receipt.actual_model_id=Some("model".into());receipt.input_tokens=Some(17);receipt.output_tokens=Some(5);receipt.reconciles_entry_id=Some(pending.entry_id.clone());ledger.append_call(&receipt,&metadata).unwrap();
        super::save(&c,&ledger,"old","scope","A1",value.clone()).unwrap();assert!(super::load(&c,&ledger,"old","scope","A1").unwrap().is_some(),"Legacy absent result keeps its original proven receipt policy");
        ledger.append_call_outcome(&pending,&CallOutcome{state:CallOutcomeState::Failed,safe_error_code:Some("OUTPUT_LIMIT".into()),model_elapsed_ms:Some(7),origin:CallOutcomeOrigin::LocalExecutionResult}).unwrap();
        assert!(super::load(&c,&ledger,"old","scope","A1").is_err(),"Known failed execution cannot be silently treated as missing evidence and dispatched again");assert!(super::save(&c,&ledger,"old","scope","A1",value).is_err());assert_eq!(ledger.entry(&receipt.entry_id).unwrap(),Some(receipt));
    }

    #[test]
    fn ambiguous_cache_retry_identity_never_selects_an_arbitrary_legacy_receipt() {
        use crate::{usage_ledger_v1 as usage,chat_call_accounting_v1::Metadata};
        let dir=tempfile::tempdir().unwrap();let c=rusqlite::Connection::open(dir.path().join("cache.sqlite3")).unwrap();seed_parent(&c,"old","workspace","reply");super::migrate(&c).unwrap();let ledger=usage::Ledger::open(&dir.path().join("ledger")).unwrap();let mut value=synthetic_entry();value.created_at=crate::system_now_millis();
        let receipt=|retry:usize,id:&str|serde_json::from_value::<usage::Entry>(serde_json::json!({"entry_id":id,"replay_token":id,"execution_id":"old","conversation_id":"conversation","branch_leaf_message_id":"leaf","invocation_id":format!("old-retrieval-{}-{}",retry,crate::sha256(value.invocation.as_bytes())),"attempt_id":format!("retrieval-{retry}"),"kind":"FinalMeasured","fact_grade":"ProviderReported","requested_model_id":"model","actual_model_id":"model","input_tokens":1,"output_tokens":1,"charge_micros":1,"currency_code":"USD","cost_source":"PROVIDER_RESPONSE","source":"DesktopLocal","occurred_at_ms":1000+retry})).unwrap();
        let metadata=Metadata{role:"RETRIEVAL".into(),model_display_name:"Model".into(),..Default::default()};let first=receipt(0,"first");let second=receipt(1,"second");ledger.append_call(&first,&metadata).unwrap();super::save(&c,&ledger,"old","scope","A1",value.clone()).unwrap();assert!(super::load(&c,&ledger,"old","scope","A1").unwrap().is_some());
        ledger.append_call(&second,&metadata).unwrap();assert!(super::load(&c,&ledger,"old","scope","A1").unwrap().is_none(),"An ambiguous old key must not yield a cache hit");assert!(super::save(&c,&ledger,"old","scope","A1",value).is_err());assert_eq!(ledger.entry("first").unwrap(),Some(first));assert_eq!(ledger.entry("second").unwrap(),Some(second));assert_eq!(c.query_row("select count(*) from desktop_attachment_search_checkpoints",[],|r|r.get::<_,i64>(0)).unwrap(),1,"Ambiguous historical evidence is retained, not rewritten or deleted");
    }

    #[test] fn cache_requires_final_retrieval_receipt_and_keeps_ledger_after_cascade(){
      use crate::{usage_ledger_v1 as usage,chat_call_accounting_v1::Metadata};
      let dir=tempfile::tempdir().unwrap();let path=dir.path().join("cache.sqlite3");let c=rusqlite::Connection::open(&path).unwrap();
      seed_parent(&c,"old","workspace","reply");super::migrate(&c).unwrap();
      let ledger=usage::Ledger::open(&dir.path().join("ledger")).unwrap();let mut value=synthetic_entry();value.created_at=crate::system_now_millis();
      assert!(super::save(&c,&ledger,"old","scope","A1",value.clone()).is_err());
      let receipt=|call:&str,id:&str|serde_json::from_value::<usage::Entry>(serde_json::json!({"entry_id":id,"replay_token":id,"execution_id":"old","conversation_id":"conversation","branch_leaf_message_id":"leaf","invocation_id":format!("old-retrieval-0-{}",crate::sha256(call.as_bytes())),"attempt_id":"retrieval","kind":"FinalMeasured","fact_grade":"ProviderReported","requested_model_id":"model","actual_model_id":"model","input_tokens":1,"output_tokens":1,"charge_micros":1,"currency_code":"USD","cost_source":"PROVIDER_RESPONSE","source":"DesktopLocal","occurred_at_ms":1000})).unwrap();
      let first=receipt("owned-call","receipt-first");ledger.append(&first).unwrap();assert!(super::save(&c,&ledger,"old","scope","A1",value.clone()).is_err());
      ledger.append_call(&first,&Metadata{role:"ANSWER".into(),model_display_name:"Model".into(),..Default::default()}).unwrap();assert!(super::save(&c,&ledger,"old","scope","A1",value.clone()).is_err());
      value.invocation="owned-next".into();let next=receipt("owned-next","receipt-next");ledger.append_call(&next,&Metadata{role:"RETRIEVAL".into(),model_display_name:"Model".into(),..Default::default()}).unwrap();
      super::save(&c,&ledger,"old","scope","A1",value.clone()).unwrap();drop(c);let c=rusqlite::Connection::open(path).unwrap();c.execute_batch("PRAGMA foreign_keys=ON").unwrap();
      assert_eq!(super::load(&c,&ledger,"old","scope","A1").unwrap().unwrap().text,value.text);assert!(super::load(&c,&ledger,"old","different-scope","A1").unwrap().is_none());
      super::coverage(&c,"old","scope",8,4,false).unwrap();c.execute("DELETE FROM desktop_ordinary_chat_attempts WHERE attempt_id='old'",[]).unwrap();
      assert!(super::load(&c,&ledger,"old","scope","A1").unwrap().is_none());assert_eq!(c.query_row("SELECT COUNT(*) FROM desktop_attachment_search_coverage",[],|r|r.get::<_,i64>(0)).unwrap(),0);assert!(ledger.entry("receipt-next").unwrap().is_some());
    }

    fn synthetic_entry()->super::Entry {super::Entry{text:"A1 Python\n未核实".into(),footer:String::new(),model:"model".into(),created_at:1000,invocation:"owned-call".into()}}
    #[test] fn checkpoint_without_invocation_cannot_be_reused(){let mut e=synthetic_entry();e.invocation.clear();assert!(!super::fresh(&e,"model","Python发布日期",1001));}
    #[test] fn extreme_clock_values_do_not_panic_or_reuse(){let mut e=synthetic_entry();e.created_at=i64::MIN;assert!(!super::fresh(&e,"model","Python发布日期",i64::MAX));}
    #[test] fn checkpoint_unknown_payload_fields_are_rejected(){let mut value=serde_json::to_value(synthetic_entry()).unwrap();value["raw_provider_body"]=serde_json::json!("forbidden");assert!(serde_json::from_value::<super::Entry>(value).is_err());}
    #[test] fn english_realtime_queries_are_not_reused(){for query in ["latest market price","exchange rate today","current weather"]{assert!(!super::fresh(&synthetic_entry(),"model",query,1001));}}

    use super::*;
    #[test]
    fn forward_migration_and_checkpoint_reopen_preserve_attempt_and_old_receipts() {
        let root = tempfile::tempdir().unwrap();
        let path = root.path().join("workspace.db");
        let c = rusqlite::Connection::open(&path).unwrap();
        c.execute_batch("PRAGMA foreign_keys=ON;CREATE TABLE desktop_ordinary_chat_attempts(attempt_id TEXT PRIMARY KEY,disclosure_version TEXT);INSERT INTO desktop_ordinary_chat_attempts VALUES('old','normal-chat-relay-search-v2');").unwrap();
        migrate(&c).unwrap();
        let value = Entry {
            text: "A1 Python在2025年发布\n已核实：公开日期。".into(),
            footer: "- [官方](https://example.org/)".into(),
            model: "gpt-6.1-sol".into(),
            created_at: 1000,
            invocation: "call-original".into(),
        };
        c.execute(
            "INSERT INTO desktop_attachment_search_checkpoints VALUES('old','fingerprint','A1',?1,1000)",
            [serde_json::to_string(&value).unwrap()],
        )
        .unwrap();
        drop(c);
        let c = rusqlite::Connection::open(&path).unwrap();
        let json: String = c
            .query_row(
                "SELECT evidence_json FROM desktop_attachment_search_checkpoints",
                [],
                |r| r.get(0),
            )
            .unwrap();
        let row: Entry = serde_json::from_str(&json).unwrap();
        assert!(fresh(&row, "gpt-6.1-sol", "Python日期", 1001));
        assert!(!fresh(&row, "gpt-6.1-sol", "Python日期", 601001));
        assert!(!fresh(&row, "gpt-6-astra", "Python日期", 1001));
        assert!(!fresh(&row, "gpt-6.1-sol", "最新股价", 1001));
        let old: String = c
            .query_row(
                "SELECT disclosure_version FROM desktop_ordinary_chat_attempts",
                [],
                |r| r.get(0),
            )
            .unwrap();
        assert_eq!(old, "normal-chat-relay-search-v2");
    }
}
