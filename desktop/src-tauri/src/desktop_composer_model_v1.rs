//! Area-local last composer choice. A null model is an explicit Auto choice.
use rusqlite::{Connection,OptionalExtension,params};

pub(crate) const TABLE: &str = "CREATE TABLE p6g_composer_default (id INTEGER PRIMARY KEY CHECK(id=1),revision INTEGER NOT NULL CHECK(revision>=0),model_id TEXT)";
pub(crate) fn schema_valid(c:&Connection)->rusqlite::Result<bool> {
    let columns=c.prepare("PRAGMA table_info(p6g_composer_default)")?.query_map([],|row|Ok((row.get::<_,String>(1)?,row.get::<_,String>(2)?)))?.collect::<Result<Vec<_>,_>>()?;
    Ok(columns==vec![("id".into(),"INTEGER".into()),("revision".into(),"INTEGER".into()),("model_id".into(),"TEXT".into())])
}
pub(crate) fn read(c:&Connection)->Result<(u64,Option<String>),String> {
    if !schema_valid(c).map_err(|_|"Composer schema unavailable")? {return Ok((0,None));}
    c.query_row("SELECT revision,model_id FROM p6g_composer_default WHERE id=1",[],|row|Ok((row.get(0)?,row.get(1)?))).optional()
        .map(|row|row.unwrap_or((0,None))).map_err(|_|"Composer choice unavailable".into())
}
pub(crate) fn save(c:&Connection,model:Option<&str>)->Result<u64,String> {
    let next=read(c)?.0.checked_add(1).ok_or("Composer revision overflow")?;
    c.execute("INSERT INTO p6g_composer_default(id,revision,model_id) VALUES(1,?1,?2) ON CONFLICT(id) DO UPDATE SET revision=excluded.revision,model_id=excluded.model_id",params![next,model])
        .map_err(|_|"Composer choice write failed")?;
    Ok(next)
}
pub(crate) fn migrate_committed_area(c:&mut Connection,expected:&str)->Result<(),String> {
    use crate::desktop_schema_source::{classify,SchemaSource};
    let version=c.pragma_query_value(None,"user_version",|r|r.get(0)).map_err(|_|"Composer schema version unavailable")?;
    match classify(c,version).map_err(|_|"Composer schema source unavailable")? {
        SchemaSource::Common57{area,split_done:true} | SchemaSource::Common58{area,split_done:true} | SchemaSource::Common59{area,split_done:true} if area==expected=>return Ok(()),
        SchemaSource::Common56{area,split_done:true} if area==expected=>{},
        _=>return Err("Composer schema source rejected; original preserved".into()),
    }
    let tx=c.transaction().map_err(|_|"Composer migration transaction unavailable")?;
    tx.execute_batch(TABLE).map_err(|_|"Composer schema migration failed")?;
    tx.pragma_update(None,"user_version",57).map_err(|_|"Composer schema version write failed")?;
    if !matches!(classify(&tx,57).map_err(|_|"Composer schema verification failed")?,SchemaSource::Common57{area,split_done:true} | SchemaSource::Common58{area,split_done:true} | SchemaSource::Common59{area,split_done:true} if area==expected) {
        return Err("Composer migrated schema invalid; rolled back".into());
    }
    tx.commit().map_err(|_|"Composer schema commit failed".into())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn explicit_auto_and_revision_survive_reopen_and_failed_writes_preserve_choice() {
        let dir=tempfile::tempdir().unwrap();let path=dir.path().join("choice.sqlite3");
        let c=Connection::open(&path).unwrap();c.execute_batch(TABLE).unwrap();
        assert_eq!(read(&c).unwrap(),(0,None));
        assert_eq!(save(&c,Some("RELAYCORE_GPT_6_ASTRA")).unwrap(),1);
        c.execute_batch("CREATE TRIGGER reject_choice BEFORE UPDATE ON p6g_composer_default BEGIN SELECT RAISE(ABORT,'synthetic write'); END;").unwrap();
        assert!(save(&c,None).is_err());assert_eq!(read(&c).unwrap(),(1,Some("RELAYCORE_GPT_6_ASTRA".into())));
        c.execute_batch("DROP TRIGGER reject_choice;").unwrap();assert_eq!(save(&c,None).unwrap(),2);drop(c);
        assert_eq!(read(&Connection::open(path).unwrap()).unwrap(),(2,None));
    }
}
