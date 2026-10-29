//! P4F: an explicit read-only projection of saved Knowledge in one area/workspace.
use serde::Serialize;
use serde_json::Value;

#[derive(Debug, Serialize, PartialEq, Eq)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub(crate) enum Reason { ExactContent, SameNormalizedTitle }
#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct Candidate {
    knowledge_id: String,
    title: String,
    revision: u64,
    reasons: Vec<Reason>,
}
#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct Projection {
    anchor_id: String,
    anchor_revision: u64,
    candidates: Vec<Candidate>,
}
fn failure() -> String { "KNOWLEDGE_DUPLICATE_SNAPSHOT_INVALID".into() }
fn text<'a>(v: &'a Value, field: &str) -> Result<&'a str, String> {
    v.get(field).and_then(Value::as_str).ok_or_else(failure)
}
// Kotlin Char.isWhitespace trims Unicode separators, while its Regex("\\s+")
// collapses the six ASCII whitespace characters. Keep the P4F source semantics.
fn trim_char(c: char) -> bool {
    (c.is_whitespace() && c != '\u{85}') || ('\u{1c}'..='\u{1f}').contains(&c)
}
fn canonical(value: &str) -> String {
    let lower = value.trim_matches(trim_char).to_lowercase();
    let mut out = String::new();
    let mut whitespace = false;
    for c in lower.chars() {
        if matches!(c, ' ' | '\t' | '\n' | '\r' | '\u{b}' | '\u{c}') {
            if !whitespace { out.push(' '); }
            whitespace = true;
        } else { out.push(c); whitespace = false; }
    }
    out
}
fn scope(v: &Value) -> Result<(&str, Option<&str>), String> {
    match (text(v,"scope")?, v.get("projectId")) {
        ("GLOBAL", Some(Value::Null)) => Ok(("GLOBAL",None)),
        ("PROJECT", Some(Value::String(id))) if !id.is_empty() => Ok(("PROJECT",Some(id))),
        _ => Err(failure()),
    }
}
fn revision(v: &Value) -> Result<u64,String> {
    v.get("revision").and_then(Value::as_u64).filter(|n| *n>0 && *n<=9_007_199_254_740_991).ok_or_else(failure)
}
fn sensitive(v: &Value) -> Result<(),String> {
    crate::require_safe_memory_body(&format!("{}\n{}",text(v,"title")?,text(v,"body")?))
        .map_err(|_| "HIGH_SENSITIVITY".into())
}
fn content_hash(v: &Value) -> Result<String,String> {
    Ok(crate::sha256(format!("{}\n{}",canonical(text(v,"title")?),canonical(text(v,"body")?)).as_bytes()))
}
pub(crate) fn candidates(exchange: &Value, anchor_id: &str, expected_revision: u64) -> Result<Projection,String> {
    let rows = exchange.get("knowledge").and_then(Value::as_array).ok_or_else(failure)?;
    let mut ids = std::collections::BTreeSet::new();
    for row in rows { if !ids.insert(text(row,"id")?) { return Err(failure()); } }
    let anchor = rows.iter().find(|v| v.get("id").and_then(Value::as_str)==Some(anchor_id))
        .ok_or("MISSING_KNOWLEDGE")?;
    if text(anchor,"status")? != "ACTIVE" { return Err("INELIGIBLE_KNOWLEDGE".into()); }
    let anchor_revision=revision(anchor)?;
    if anchor_revision!=expected_revision {return Err("REVISION_CONFLICT".into());}
    sensitive(anchor)?;
    let anchor_scope=scope(anchor)?;
    let title=canonical(text(anchor,"title")?);
    let hash=content_hash(anchor)?;
    let mut matched=Vec::new();
    for row in rows {
        let id=text(row,"id")?;
        if id==anchor_id || text(row,"status")?!="ACTIVE" {continue;}
        if scope(row)?!=anchor_scope {continue;}
        let same_title=canonical(text(row,"title")?)==title;
        let exact=content_hash(row)?==hash;
        if !same_title && !exact {continue;}
        // Reject all matching sensitive rows before truncation, including row 21+.
        sensitive(row)?;
        let when=chrono::DateTime::parse_from_rfc3339(text(row,"updatedAt")?).map_err(|_|failure())?;
        let mut reasons=Vec::new();
        if exact {reasons.push(Reason::ExactContent);}
        if same_title {reasons.push(Reason::SameNormalizedTitle);}
        matched.push((exact,when,Candidate{knowledge_id:id.into(),title:text(row,"title")?.into(),revision:revision(row)?,reasons}));
    }
    matched.sort_by(|a,b| b.0.cmp(&a.0).then_with(|| b.1.cmp(&a.1)).then_with(|| a.2.knowledge_id.cmp(&b.2.knowledge_id)));
    Ok(Projection{anchor_id:anchor_id.into(),anchor_revision,candidates:matched.into_iter().take(20).map(|v|v.2).collect()})
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;
    fn row(id:&str,title:&str,body:&str)->Value {json!({"id":id,"title":title,"body":body,"scope":"GLOBAL","projectId":null,"status":"ACTIVE","revision":1,"updatedAt":"2026-10-03T10:00:00Z"})}
    #[test]
    fn duplicates_preserve_source_rules_scope_order_and_safe_projection_without_mutation() {
        let a=row("a","  Local\tTitle  ","Body\nline");let exact=row("exact","local title"," body LINE ");let title=row("title","LOCAL TITLE","different");
        let mut project=exact.clone();project["id"]=json!("other");project["scope"]=json!("PROJECT");project["projectId"]=json!("p");
        let mut archived=exact.clone();archived["id"]=json!("archived");archived["status"]=json!("ARCHIVED");
        let exchange=json!({"knowledge":[a,title,project,archived,exact]});let before=exchange.clone();
        let out=candidates(&exchange,"a",1).unwrap();assert_eq!(out.candidates.len(),2);assert_eq!(out.candidates[0].knowledge_id,"exact");assert_eq!(out.candidates[0].reasons,vec![Reason::ExactContent,Reason::SameNormalizedTitle]);
        let wire=serde_json::to_value(&out).unwrap();assert_eq!(wire["candidates"][0].as_object().unwrap().len(),4);assert!(wire["candidates"][0].get("body").is_none());assert_eq!(exchange,before);
        assert_eq!(canonical("\u{a0}A\t\nB\u{a0}"),"a b");assert_eq!(canonical("A\u{a0}B"),"a\u{a0}b");
    }
    #[test]
    fn duplicates_are_limited_after_sort_and_sensitive_checks() {
        let mut rows=vec![row("a","same","content")];
        for n in (0..25).rev(){rows.push(row(&format!("k{n:02}"),"same","content"));}
        let mut exchange=json!({"knowledge":rows});let out=candidates(&exchange,"a",1).unwrap();assert_eq!(out.candidates.len(),20);assert_eq!(out.candidates[0].knowledge_id,"k00");assert_eq!(out.candidates[19].knowledge_id,"k19");
        exchange["knowledge"].as_array_mut().unwrap().push(row("z-sensitive","same","password: synthetic-only"));assert_eq!(candidates(&exchange,"a",1).unwrap_err(),"HIGH_SENSITIVITY");
    }
    #[test]
    fn duplicates_reject_missing_inactive_stale_and_ambiguous_anchor() {
        let mut e=json!({"knowledge":[row("a","title","body")]});assert_eq!(candidates(&e,"missing",1).unwrap_err(),"MISSING_KNOWLEDGE");assert_eq!(candidates(&e,"a",2).unwrap_err(),"REVISION_CONFLICT");
        e["knowledge"][0]["status"]=json!("DELETED");assert_eq!(candidates(&e,"a",1).unwrap_err(),"INELIGIBLE_KNOWLEDGE");
        e["knowledge"][0]["status"]=json!("ACTIVE");let duplicate=e["knowledge"][0].clone();e["knowledge"].as_array_mut().unwrap().push(duplicate);assert_eq!(candidates(&e,"a",1).unwrap_err(),"KNOWLEDGE_DUPLICATE_SNAPSHOT_INVALID");
    }
    #[test]
    fn duplicates_respect_exact_project_and_chronological_offsets() {
        let mut rows=vec![row("a","title","body"),row("b","title","different"),row("c","title","different"),row("d","title","body")];
        for v in &mut rows {v["scope"]=json!("PROJECT");v["projectId"]=json!("p");}
        rows[1]["updatedAt"]=json!("2026-10-03T12:00:00+02:00");rows[2]["updatedAt"]=json!("2026-10-03T11:00:00Z");rows[3]["projectId"]=json!("other");
        assert_eq!(candidates(&json!({"knowledge":rows}),"a",1).unwrap().candidates.into_iter().map(|v|v.knowledge_id).collect::<Vec<_>>(),vec!["c","b"]);
    }
}
