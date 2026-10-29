//! P4L versioned Knowledge JSON. Separate from Markdown and P6J packages.
//! This adapter only produces inert review candidates; it never writes Knowledge.
use serde::{de::{self, DeserializeSeed, MapAccess, SeqAccess, Visitor}, Deserialize, Serialize};
use serde_json::{Map, Value};
use std::{collections::BTreeSet, fmt};

pub(crate) const MAX_BYTES: usize = 768 * 1024;
const MAX_DEPTH: usize = 24;
const MAX_STRING: usize = 12_000;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub(crate) struct Source {
    pub stable_id: String,
    pub revision: String,
    pub content_hash: String,
    pub kind: String,
    pub summary: String,
    pub requested_scope: String,
    pub project_ref: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub(crate) struct Candidate {
    pub content: crate::desktop_markdown_knowledge_v1::Candidate,
    pub source: Source,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub(crate) struct Document {
    pub exported_at: String,
    pub items: Vec<Candidate>,
}

// Value's default deserializer overwrites duplicate object keys. Walk with a
// seed instead, including nested keys and decoded Unicode-escaped keys.
struct StrictValue(usize);
impl<'de> DeserializeSeed<'de> for StrictValue {
    type Value = Value;
    fn deserialize<D: de::Deserializer<'de>>(self, d: D) -> Result<Value, D::Error> {
        if self.0 > MAX_DEPTH { return Err(de::Error::custom("TOO_DEEP")); }
        d.deserialize_any(self)
    }
}
impl<'de> Visitor<'de> for StrictValue {
    type Value = Value;
    fn expecting(&self, f: &mut fmt::Formatter) -> fmt::Result { f.write_str("bounded numberless JSON") }
    fn visit_bool<E: de::Error>(self, v: bool) -> Result<Value, E> { Ok(Value::Bool(v)) }
    fn visit_unit<E: de::Error>(self) -> Result<Value, E> { Ok(Value::Null) }
    fn visit_str<E: de::Error>(self, v: &str) -> Result<Value, E> {
        if v.chars().count() > MAX_STRING { return Err(E::custom("STRING_TOO_LONG")); }
        Ok(Value::String(v.into()))
    }
    fn visit_string<E: de::Error>(self, v: String) -> Result<Value, E> { self.visit_str(&v) }
    fn visit_i64<E: de::Error>(self, _: i64) -> Result<Value, E> { Err(E::custom("MALFORMED_JSON")) }
    fn visit_u64<E: de::Error>(self, _: u64) -> Result<Value, E> { Err(E::custom("MALFORMED_JSON")) }
    fn visit_f64<E: de::Error>(self, _: f64) -> Result<Value, E> { Err(E::custom("MALFORMED_JSON")) }
    fn visit_seq<A: SeqAccess<'de>>(self, mut a: A) -> Result<Value, A::Error> {
        let mut values = Vec::new();
        while let Some(v) = a.next_element_seed(StrictValue(self.0 + 1))? { values.push(v); }
        Ok(Value::Array(values))
    }
    fn visit_map<A: MapAccess<'de>>(self, mut a: A) -> Result<Value, A::Error> {
        let mut fields = Map::new();
        while let Some(k) = a.next_key::<String>()? {
            if k.chars().count() > 120 { return Err(de::Error::custom("STRING_TOO_LONG")); }
            if fields.contains_key(&k) { return Err(de::Error::custom("DUPLICATE_KEY")); }
            fields.insert(k, a.next_value_seed(StrictValue(self.0 + 1))?);
        }
        Ok(Value::Object(fields))
    }
}

fn parse_error(e: serde_json::Error) -> String {
    let message = e.to_string();
    for code in ["TOO_DEEP", "STRING_TOO_LONG", "DUPLICATE_KEY"] {
        if message.starts_with(code) { return code.into(); }
    }
    "MALFORMED_JSON".into()
}
fn object<'a>(v: &'a Value, keys: &[&str]) -> Result<&'a Map<String, Value>, String> {
    let o = v.as_object().ok_or("TYPE_MISMATCH")?;
    if o.keys().map(String::as_str).collect::<BTreeSet<_>>() != keys.iter().copied().collect() {
        return Err("UNKNOWN_FIELD".into());
    }
    Ok(o)
}
fn string(o: &Map<String, Value>, k: &str) -> Result<String, String> {
    o.get(k).and_then(Value::as_str).map(str::to_owned).ok_or_else(|| "TYPE_MISMATCH".into())
}
fn safe_id(v: &str) -> bool {
    !v.is_empty() && v.len() <= 160 && v.bytes().all(|b| b.is_ascii_alphanumeric() || b"._:-".contains(&b))
}
fn safe_metadata(kind: &str, summary: &str) -> Result<(), String> {
    let k = regex::Regex::new(r"^[A-Z][A-Z0-9_]{0,39}$").expect("source kind grammar");
    let unsafe_text = regex::Regex::new(r"(?i)([a-z][a-z0-9+.-]*://|content:|file:|authorization|bearer|api[_ -]?key|provider[_ -]?payload|[a-z]:[\\/]|\\\\|(^|\s)/|(^|[\\/\s])\.\.([\\/]|$))").expect("source metadata grammar");
    if !k.is_match(kind) || summary.chars().count() > 160 || unsafe_text.is_match(summary)
        || summary.chars().any(|c| c.is_control())
        || crate::require_safe_memory_body(summary).is_err() {
        return Err("UNSAFE_METADATA".into());
    }
    Ok(())
}

pub(crate) fn parse(bytes: &[u8]) -> Result<Document, String> {
    if bytes.len() > MAX_BYTES { return Err("TOO_LARGE".into()); }
    let text = std::str::from_utf8(bytes).map_err(|_| "INVALID_UTF8")?;
    let mut deserializer = serde_json::Deserializer::from_str(text.strip_prefix('\u{feff}').unwrap_or(text));
    let value = StrictValue(0).deserialize(&mut deserializer).map_err(parse_error)?;
    deserializer.end().map_err(parse_error)?;
    let root = object(&value, &["format", "schemaVersion", "exportedAt", "items"])?;
    if string(root, "format")? != "nfai.knowledge.json" { return Err("UNKNOWN_FORMAT".into()); }
    if string(root, "schemaVersion")? != "1" { return Err("UNKNOWN_VERSION".into()); }
    let exported_at = string(root, "exportedAt")?;
    chrono::DateTime::parse_from_rfc3339(&exported_at).map_err(|_| "TYPE_MISMATCH")?;
    let raw = root["items"].as_array().ok_or("TYPE_MISMATCH")?;
    if raw.is_empty() { return Err("EMPTY_ITEMS".into()); }
    if raw.len() > 100 { return Err("TOO_MANY_ITEMS".into()); }
    let mut items = Vec::with_capacity(raw.len());
    for v in raw {
        let o = object(v, &["id", "title", "body", "tags", "scope", "projectRef", "revision", "hash", "source"])?;
        let stable_id = string(o, "id")?;
        if !safe_id(&stable_id) { return Err("UNSAFE_METADATA".into()); }
        let scope = string(o, "scope")?;
        if !matches!(scope.as_str(), "GLOBAL" | "PROJECT") { return Err("ITEM_INVALID".into()); }
        let project_ref = match &o["projectRef"] {
            Value::Null if scope == "GLOBAL" => None,
            Value::String(s) if safe_id(s) => Some(s.clone()),
            Value::String(_) => return Err("UNSAFE_METADATA".into()),
            _ => return Err("TYPE_MISMATCH".into()),
        };
        let revision = string(o, "revision")?;
        if revision.is_empty() || !revision.bytes().all(|b| b.is_ascii_digit())
            || revision.parse::<i32>().ok().filter(|n| *n > 0).is_none() { return Err("ITEM_INVALID".into()); }
        let content_hash = string(o, "hash")?;
        if content_hash.len() != 64 || !content_hash.bytes().all(|b| b.is_ascii_digit() || (b'a'..=b'f').contains(&b)) { return Err("ITEM_INVALID".into()); }
        let source = object(&o["source"], &["kind", "summary"])?;
        let kind = string(source, "kind")?;
        let summary = string(source, "summary")?;
        safe_metadata(&kind, &summary)?;
        let tags = o["tags"].as_array().ok_or("TYPE_MISMATCH")?.iter()
            .map(|v| v.as_str().map(str::to_owned).ok_or_else(|| "TYPE_MISMATCH".to_string())).collect::<Result<Vec<_>, _>>()?;
        let content = crate::desktop_markdown_knowledge_v1::validate_candidate(crate::desktop_markdown_knowledge_v1::Candidate {
            title: string(o, "title")?, body: string(o, "body")?, tags,
        })?;
        items.push(Candidate { content, source: Source { stable_id, revision, content_hash, kind, summary, requested_scope: scope, project_ref } });
    }
    Ok(Document { exported_at, items })
}

#[cfg(test)]
mod tests {
    use super::*;
    fn fixture() -> Value { serde_json::json!({"format":"nfai.knowledge.json","schemaVersion":"1","exportedAt":"2026-08-13T00:00:00Z","items":[{"id":"old-alpha","title":"Alpha","body":"正文","tags":["local"],"scope":"GLOBAL","projectRef":null,"revision":"1","hash":"a".repeat(64),"source":{"kind":"LOCAL","summary":"safe local summary"}}]}) }
    fn parse_value(v: &Value) -> Result<Document, String> { parse(&serde_json::to_vec(v).unwrap()) }
    #[test] fn android_wire_strings_inert_content_and_source_facts() {
        let mut v=fixture();v["items"][0]["body"]=Value::String("</textarea><script>inert()</script>\n[jump](javascript:alert(1))".into());
        let d=parse_value(&v).unwrap();assert_eq!(d.items[0].content.body,v["items"][0]["body"].as_str().unwrap());
        assert_eq!(d.items[0].source.stable_id,"old-alpha");assert_eq!(d.items[0].source.revision,"1");assert_eq!(d.items[0].source.kind,"LOCAL");
        let mut bytes="\u{feff}".as_bytes().to_vec();bytes.extend(serde_json::to_vec(&v).unwrap());assert_eq!(parse(&bytes).unwrap(),d);
    }
    #[test] fn duplicate_keys_rejected_at_every_level_including_escaped_keys() {
        for raw in ["{\"format\":\"a\",\"format\":\"b\"}","{\"items\":[{\"source\":{\"kind\":\"A\",\"kind\":\"B\"}}]}","{\"id\":\"a\",\"\\u0069d\":\"b\"}"] {
            assert_eq!(parse(raw.as_bytes()).unwrap_err(),"DUPLICATE_KEY");
        }
    }
    #[test] fn strict_shapes_versions_numbers_and_trailing_payload_reject() {
        for (field,value,error) in [("schemaVersion",serde_json::json!(1),"MALFORMED_JSON"),("schemaVersion",serde_json::json!("99"),"UNKNOWN_VERSION"),("format",serde_json::json!("P6J"),"UNKNOWN_FORMAT"),("exportedAt",serde_json::json!("yesterday"),"TYPE_MISMATCH"),("items",serde_json::json!([]),"EMPTY_ITEMS")] {
            let mut v=fixture();v[field]=value;assert_eq!(parse_value(&v).unwrap_err(),error);
        }
        let mut v=fixture();v["providerPayload"]=Value::Null;assert_eq!(parse_value(&v).unwrap_err(),"UNKNOWN_FIELD");
        let mut v=fixture();v["items"][0]["source"]["uri"]=Value::Null;assert_eq!(parse_value(&v).unwrap_err(),"UNKNOWN_FIELD");
        let text=serde_json::to_string(&fixture()).unwrap();for tail in [" {}"," NaN"," Infinity"] {assert_eq!(parse(format!("{text}{tail}").as_bytes()).unwrap_err(),"MALFORMED_JSON");}
    }
    #[test] fn unsafe_source_metadata_and_invalid_scope_cannot_be_candidates() {
        for text in ["https://example.invalid","content:private","Authorization secret","Bearer abcdefghijklmnopqrst","C:\\private\\secret","/tmp/file","../file","provider_payload"] {
            let mut v=fixture();v["items"][0]["source"]["summary"]=serde_json::json!(text);assert_eq!(parse_value(&v).unwrap_err(),"UNSAFE_METADATA", "{text}");
        }
        let mut v=fixture();v["items"][0]["source"]["kind"]=serde_json::json!("file:private");assert_eq!(parse_value(&v).unwrap_err(),"UNSAFE_METADATA");
        let mut v=fixture();v["items"][0]["scope"]=serde_json::json!("PROJECT");assert_eq!(parse_value(&v).unwrap_err(),"TYPE_MISMATCH");v["items"][0]["projectRef"]=serde_json::json!("project-safe");assert_eq!(parse_value(&v).unwrap().items[0].source.project_ref.as_deref(),Some("project-safe"));
        v["items"][0]["projectRef"]=serde_json::json!("/tmp/x");assert_eq!(parse_value(&v).unwrap_err(),"UNSAFE_METADATA");
        let mut v=fixture();v["items"][0]["body"]=serde_json::json!("Bearer abcdefghijklmnopqrst");assert_eq!(parse_value(&v).unwrap_err(),"HIGH_SENSITIVITY");
    }
    #[test] fn size_depth_utf8_string_item_and_version_fact_boundaries() {
        assert_eq!(parse(&vec![b' ';MAX_BYTES+1]).unwrap_err(),"TOO_LARGE");assert_eq!(parse(&[0xc3]).unwrap_err(),"INVALID_UTF8");
        let nested=format!("{}null{}","[".repeat(25),"]".repeat(25));assert_eq!(parse(nested.as_bytes()).unwrap_err(),"TOO_DEEP");
        let mut v=fixture();v["items"][0]["body"]=serde_json::json!("x".repeat(12_001));assert_eq!(parse_value(&v).unwrap_err(),"STRING_TOO_LONG");
        v["items"][0]["body"]=serde_json::json!("界".repeat(12_000));assert!(parse_value(&v).is_ok());
        let mut v=fixture();let item=v["items"][0].clone();v["items"]=Value::Array(vec![item.clone();100]);assert_eq!(parse_value(&v).unwrap().items.len(),100);v["items"]=Value::Array(vec![item;101]);assert_eq!(parse_value(&v).unwrap_err(),"TOO_MANY_ITEMS");
        for revision in ["0","-1","1.0","2147483648"] {let mut v=fixture();v["items"][0]["revision"]=serde_json::json!(revision);assert_eq!(parse_value(&v).unwrap_err(),"ITEM_INVALID");}
        let mut v=fixture();v["items"][0]["hash"]=serde_json::json!("A".repeat(64));assert_eq!(parse_value(&v).unwrap_err(),"ITEM_INVALID");
    }
}
