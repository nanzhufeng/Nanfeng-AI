//! P7-A local cryptographic boundary. No Tauri command exposes this until a later account/state contract.
use aes_gcm::{
    aead::{rand_core::RngCore, Aead, KeyInit, OsRng, Payload},
    Aes256Gcm, Nonce,
};
use base64::{engine::general_purpose::URL_SAFE_NO_PAD, Engine};
use pbkdf2::pbkdf2_hmac;
use serde_json::{json, Map, Value};
use sha2::{Digest, Sha256};
use std::collections::BTreeSet;
use zeroize::{Zeroize, Zeroizing};

const ENVELOPE: &str = "nfai.sync.envelope";
const PAYLOAD: &str = "nfai.sync.payload";
const VERSION: u64 = 1;
const ITERATIONS: u32 = 210_000;
const MAX_ENVELOPE: usize = 2 * 1024 * 1024;
const MAX_PAYLOAD: usize = 1024 * 1024;
const MAX_RECORDS: usize = 10_000;

#[derive(Clone)]
pub struct KnownMaterial {
    pub data_key: Vec<u8>,
    pub salt: Vec<u8>,
    pub wrapping_nonce: Vec<u8>,
    pub payload_nonce: Vec<u8>,
}
#[derive(Clone)]
pub struct AccountWrappingMaterial {
    pub wrapping_key: Vec<u8>,
    pub salt: Vec<u8>,
}
#[derive(Debug, Clone, PartialEq)]
pub struct Opened {
    pub payload: Value,
}
pub struct RecoveredAccountMaterial {
    pub payload: Value,
    pub data_key: Zeroizing<Vec<u8>>,
    pub wrapping_key: Zeroizing<Vec<u8>>,
    pub salt: Vec<u8>,
}

fn err() -> String {
    "P7-A sync protocol rejected".into()
}
fn hash(bytes: &[u8]) -> String {
    format!("{:x}", Sha256::digest(bytes))
}
fn canonical(value: &Value) -> Result<String, String> {
    match value {
        Value::Null => Ok("null".into()),
        Value::Bool(b) => Ok(b.to_string()),
        Value::String(s) => serde_json::to_string(s).map_err(|_| err()),
        Value::Number(n) if n.as_i64().is_some() || n.as_u64().is_some() => Ok(n.to_string()),
        Value::Number(_) => Err(err()),
        Value::Array(items) => Ok(format!(
            "[{}]",
            items
                .iter()
                .map(canonical)
                .collect::<Result<Vec<_>, _>>()?
                .join(",")
        )),
        Value::Object(values) => Ok(format!(
            "{{{}}}",
            values
                .iter()
                .map(|(k, v)| Ok(format!(
                    "{}:{}",
                    serde_json::to_string(k).map_err(|_| err())?,
                    canonical(v)?
                )))
                .collect::<Result<Vec<_>, String>>()?
                .join(",")
        )),
    }
}
fn object(value: &Value) -> Result<&Map<String, Value>, String> {
    value.as_object().ok_or_else(err)
}
fn text<'a>(object: &'a Map<String, Value>, key: &str) -> Result<&'a str, String> {
    object.get(key).and_then(Value::as_str).ok_or_else(err)
}
fn number(object: &Map<String, Value>, key: &str) -> Result<u64, String> {
    object
        .get(key)
        .and_then(Value::as_u64)
        .filter(|n| *n > 0)
        .ok_or_else(err)
}
fn exact(object: &Map<String, Value>, keys: &[&str]) -> Result<(), String> {
    if object.len() != keys.len() || object.keys().any(|k| !keys.contains(&k.as_str())) {
        Err(err())
    } else {
        Ok(())
    }
}
fn id(value: &str) -> Result<(), String> {
    if (2..=128).contains(&value.len())
        && value
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || matches!(b, b'.' | b'_' | b'-'))
    {
        Ok(())
    } else {
        Err(err())
    }
}
fn sha(value: &str) -> Result<(), String> {
    if value.len() == 64
        && value
            .bytes()
            .all(|b| b.is_ascii_hexdigit() && !b.is_ascii_uppercase())
    {
        Ok(())
    } else {
        Err(err())
    }
}
fn b64(value: &str, exact_size: Option<usize>, max: usize) -> Result<Vec<u8>, String> {
    if value.is_empty()
        || !value
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || matches!(b, b'-' | b'_'))
    {
        return Err(err());
    }
    let bytes = URL_SAFE_NO_PAD.decode(value).map_err(|_| err())?;
    if bytes.len() > max
        || exact_size.is_some_and(|n| n != bytes.len())
        || URL_SAFE_NO_PAD.encode(&bytes) != value
    {
        Err(err())
    } else {
        Ok(bytes)
    }
}
fn forbidden(key: &str) -> bool {
    let lower = key.to_ascii_lowercase();
    [
        "credential",
        "api_key",
        "apikey",
        "authorization",
        "provider",
        "token",
        "prompt",
        "runspec",
        "runtime",
        "diagnostic",
        "uri",
        "path",
        "avatar",
        "recovery",
        "private_key",
        "privatekey",
    ]
    .iter()
    .any(|needle| lower.contains(needle))
}
fn safe_content(value: &Value, depth: u8) -> Result<(), String> {
    if depth > 32 {
        return Err(err());
    }
    match value {
        Value::Object(map) => {
            for (key, child) in map {
                if forbidden(key) || (key == "classification" && child == "HIGH_SENSITIVE") {
                    return Err(err());
                }
                safe_content(child, depth + 1)?;
            }
        }
        Value::Array(values) => {
            for child in values {
                safe_content(child, depth + 1)?;
            }
        }
        _ => {}
    }
    Ok(())
}
/// Classification is the primary P7-A guard; field scanning only catches unsafe unclassified input.
fn reject_sensitive_record(classification: &str, content: &Value) -> Result<(), String> {
    if classification != "NORMAL" {
        return Err(err());
    }
    safe_content(content, 0)
}

// serde_json intentionally tolerates duplicate names. This scanner rejects them before deserialization.
fn reject_duplicate_keys(text: &str) -> Result<(), String> {
    fn ws(b: &[u8], i: &mut usize) {
        while *i < b.len() && b[*i].is_ascii_whitespace() {
            *i += 1
        }
    }
    fn string(b: &[u8], i: &mut usize) -> Result<String, String> {
        let start = *i;
        if b.get(*i) != Some(&b'"') {
            return Err(err());
        };
        *i += 1;
        let mut slash = false;
        while *i < b.len() {
            let c = b[*i];
            *i += 1;
            if slash {
                slash = false;
                continue;
            }
            if c == b'\\' {
                slash = true;
                continue;
            }
            if c == b'"' {
                return serde_json::from_slice(&b[start..*i]).map_err(|_| err());
            }
            if c < 0x20 {
                return Err(err());
            }
        }
        Err(err())
    }
    fn value(b: &[u8], i: &mut usize) -> Result<(), String> {
        ws(b, i);
        match b.get(*i) {
            Some(b'{') => {
                *i += 1;
                ws(b, i);
                let mut seen = BTreeSet::new();
                if b.get(*i) == Some(&b'}') {
                    *i += 1;
                    return Ok(());
                }
                loop {
                    ws(b, i);
                    let key = string(b, i)?;
                    if !seen.insert(key) {
                        return Err(err());
                    }
                    ws(b, i);
                    if b.get(*i) != Some(&b':') {
                        return Err(err());
                    }
                    *i += 1;
                    value(b, i)?;
                    ws(b, i);
                    match b.get(*i) {
                        Some(b',') => *i += 1,
                        Some(b'}') => {
                            *i += 1;
                            return Ok(());
                        }
                        _ => return Err(err()),
                    }
                }
            }
            Some(b'[') => {
                *i += 1;
                ws(b, i);
                if b.get(*i) == Some(&b']') {
                    *i += 1;
                    return Ok(());
                }
                loop {
                    value(b, i)?;
                    ws(b, i);
                    match b.get(*i) {
                        Some(b',') => *i += 1,
                        Some(b']') => {
                            *i += 1;
                            return Ok(());
                        }
                        _ => return Err(err()),
                    }
                }
            }
            Some(b'"') => string(b, i).map(|_| ()),
            Some(_) => {
                let start = *i;
                while *i < b.len()
                    && !matches!(b[*i], b',' | b']' | b'}')
                    && !b[*i].is_ascii_whitespace()
                {
                    *i += 1
                }
                if start == *i {
                    Err(err())
                } else {
                    Ok(())
                }
            }
            None => Err(err()),
        }
    }
    let b = text.as_bytes();
    let mut i = 0;
    value(b, &mut i)?;
    ws(b, &mut i);
    if i == b.len() {
        Ok(())
    } else {
        Err(err())
    }
}
fn strict(text: &str) -> Result<Value, String> {
    if text.len() > MAX_ENVELOPE {
        return Err(err());
    }
    reject_duplicate_keys(text)?;
    serde_json::from_str(text).map_err(|_| err())
}
fn aad(envelope: &Map<String, Value>) -> Result<Vec<u8>, String> {
    Ok(canonical(&json!({"appId":text(envelope,"appId")?,"documentId":text(envelope,"documentId")?,"format":text(envelope,"format")?,"payloadHash":text(envelope,"payloadHash")?,"protocolVersion":number(envelope,"protocolVersion")?,"revision":number(envelope,"revision")?,"schemaVersion":number(envelope,"schemaVersion")?}))?.into_bytes())
}
fn crypt(
    key: &[u8],
    nonce: &[u8],
    input: &[u8],
    aad: &[u8],
    open: bool,
) -> Result<Vec<u8>, String> {
    if key.len() != 32 || nonce.len() != 12 {
        return Err(err());
    }
    let cipher = Aes256Gcm::new_from_slice(key).map_err(|_| err())?;
    let payload = Payload { msg: input, aad };
    if open {
        cipher
            .decrypt(Nonce::from_slice(nonce), payload)
            .map_err(|_| err())
    } else {
        cipher
            .encrypt(Nonce::from_slice(nonce), payload)
            .map_err(|_| err())
    }
}
fn derive(recovery: &str, salt: &[u8]) -> Vec<u8> {
    let mut key = vec![0; 32];
    pbkdf2_hmac::<Sha256>(recovery.as_bytes(), salt, ITERATIONS, &mut key);
    key
}

fn validate_payload(value: &Value) -> Result<(), String> {
    let root = object(value)?;
    exact(
        root,
        &[
            "format",
            "protocolVersion",
            "schemaVersion",
            "appId",
            "documentId",
            "revision",
            "records",
        ],
    )?;
    if text(root, "format")? != PAYLOAD
        || number(root, "protocolVersion")? != VERSION
        || number(root, "schemaVersion")? != VERSION
    {
        return Err(err());
    }
    id(text(root, "appId")?)?;
    id(text(root, "documentId")?)?;
    let records = root
        .get("records")
        .and_then(Value::as_array)
        .filter(|v| v.len() <= MAX_RECORDS)
        .ok_or_else(err)?;
    let mut ids = BTreeSet::new();
    for record in records {
        let r = object(record)?;
        exact(r, &["kind", "id", "revision", "classification", "content"])?;
        if !matches!(
            text(r, "kind")?,
            "project" | "conversation" | "knowledge" | "memory" | "relation" | "safe_settings"
        ) {
            return Err(err());
        }
        id(text(r, "id")?)?;
        if !ids.insert(text(r, "id")?.to_owned()) {
            return Err(err());
        }
        number(r, "revision")?;
        reject_sensitive_record(
            text(r, "classification")?,
            r.get("content")
                .and_then(Value::as_object)
                .map(|_| r.get("content").unwrap())
                .ok_or_else(err)?,
        )?
    }
    Ok(())
}
fn preflight_value(value: &Value) -> Result<(String, String, u64, String, usize), String> {
    let root = object(value)?;
    exact(
        root,
        &[
            "format",
            "protocolVersion",
            "schemaVersion",
            "appId",
            "documentId",
            "revision",
            "payloadHash",
            "payloadByteCount",
            "kdf",
            "wrappedDataKey",
            "payload",
        ],
    )?;
    if text(root, "format")? != ENVELOPE
        || number(root, "protocolVersion")? != VERSION
        || number(root, "schemaVersion")? != VERSION
    {
        return Err(err());
    }
    let app = text(root, "appId")?.to_owned();
    let doc = text(root, "documentId")?.to_owned();
    id(&app)?;
    id(&doc)?;
    let revision = number(root, "revision")?;
    let payload_hash = text(root, "payloadHash")?.to_owned();
    sha(&payload_hash)?;
    let length = number(root, "payloadByteCount")? as usize;
    if length > MAX_PAYLOAD {
        return Err(err());
    }
    let kdf = object(root.get("kdf").ok_or_else(err)?)?;
    exact(kdf, &["algorithm", "version", "iterations", "salt"])?;
    if text(kdf, "algorithm")? != "PBKDF2-HMAC-SHA256"
        || number(kdf, "version")? != VERSION
        || number(kdf, "iterations")? != ITERATIONS as u64
    {
        return Err(err());
    }
    b64(text(kdf, "salt")?, Some(16), 16)?;
    for name in ["wrappedDataKey", "payload"] {
        let item = object(root.get(name).ok_or_else(err)?)?;
        exact(item, &["algorithm", "nonce", "ciphertext"])?;
        if text(item, "algorithm")? != "AES-256-GCM" {
            return Err(err());
        }
        b64(text(item, "nonce")?, Some(12), 12)?;
    }
    let wrap = object(root.get("wrappedDataKey").unwrap())?;
    b64(text(wrap, "ciphertext")?, Some(48), 48)?;
    let payload = object(root.get("payload").unwrap())?;
    b64(text(payload, "ciphertext")?, None, MAX_PAYLOAD + 16)?;
    Ok((app, doc, revision, payload_hash, length))
}

pub fn seal_known(
    payload: Value,
    recovery: &str,
    material: KnownMaterial,
) -> Result<String, String> {
    let wrapping = derive(recovery, &material.salt);
    seal_known_with_wrapping(payload, &wrapping, material)
}

fn seal_known_with_wrapping(
    payload: Value,
    wrapping_key: &[u8],
    mut material: KnownMaterial,
) -> Result<String, String> {
    validate_payload(&payload)?;
    let plain = canonical(&payload)?.into_bytes();
    if plain.len() > MAX_PAYLOAD {
        return Err(err());
    }
    if material.data_key.len() != 32
        || material.salt.len() != 16
        || material.wrapping_nonce.len() != 12
        || material.payload_nonce.len() != 12
        || material.wrapping_nonce == material.payload_nonce
        || wrapping_key.len() != 32
    {
        return Err(err());
    }
    let mut envelope = json!({"format":ENVELOPE,"protocolVersion":VERSION,"schemaVersion":VERSION,"appId":payload["appId"],"documentId":payload["documentId"],"revision":payload["revision"],"payloadHash":hash(&plain),"payloadByteCount":plain.len(),"kdf":{"algorithm":"PBKDF2-HMAC-SHA256","version":VERSION,"iterations":ITERATIONS,"salt":URL_SAFE_NO_PAD.encode(&material.salt)},"wrappedDataKey":{"algorithm":"AES-256-GCM","nonce":URL_SAFE_NO_PAD.encode(&material.wrapping_nonce),"ciphertext":""},"payload":{"algorithm":"AES-256-GCM","nonce":URL_SAFE_NO_PAD.encode(&material.payload_nonce),"ciphertext":""}});
    let root = object(&envelope)?;
    let aad = aad(root)?;
    let wrapped = crypt(
        wrapping_key,
        &material.wrapping_nonce,
        &material.data_key,
        &aad,
        false,
    )?;
    let encrypted = crypt(
        &material.data_key,
        &material.payload_nonce,
        &plain,
        &aad,
        false,
    )?;
    envelope["wrappedDataKey"]["ciphertext"] = Value::String(URL_SAFE_NO_PAD.encode(wrapped));
    envelope["payload"]["ciphertext"] = Value::String(URL_SAFE_NO_PAD.encode(encrypted));
    let result = canonical(&envelope);
    material.data_key.zeroize();
    material.salt.zeroize();
    material.wrapping_nonce.zeroize();
    material.payload_nonce.zeroize();
    result
}

pub fn create_account_wrapping_material(recovery: &str) -> Result<AccountWrappingMaterial, String> {
    if recovery.chars().count() < 12 {
        return Err(err());
    }
    let mut salt = vec![0; 16];
    OsRng.fill_bytes(&mut salt);
    Ok(AccountWrappingMaterial {
        wrapping_key: derive(recovery, &salt),
        salt,
    })
}

pub fn seal_with_account_wrapping_material(
    payload: Value,
    data_key: &[u8],
    material: &AccountWrappingMaterial,
) -> Result<String, String> {
    if data_key.len() != 32 || material.wrapping_key.len() != 32 || material.salt.len() != 16 {
        return Err(err());
    }
    let mut wrapping_nonce = vec![0; 12];
    let mut payload_nonce = vec![0; 12];
    OsRng.fill_bytes(&mut wrapping_nonce);
    loop {
        OsRng.fill_bytes(&mut payload_nonce);
        if payload_nonce != wrapping_nonce {
            break;
        }
    }
    seal_known_with_wrapping(
        payload,
        &material.wrapping_key,
        KnownMaterial {
            data_key: data_key.to_vec(),
            salt: material.salt.clone(),
            wrapping_nonce,
            payload_nonce,
        },
    )
}

/// P7-A accepts a caller-held key; P7-B owns account-level generation and secure storage.
pub fn seal(payload: Value, recovery: &str, data_key: &[u8]) -> Result<String, String> {
    if data_key.len() != 32 {
        return Err(err());
    }
    let mut salt = vec![0; 16];
    let mut wrapping_nonce = vec![0; 12];
    let mut payload_nonce = vec![0; 12];
    OsRng.fill_bytes(&mut salt);
    OsRng.fill_bytes(&mut wrapping_nonce);
    loop {
        OsRng.fill_bytes(&mut payload_nonce);
        if payload_nonce != wrapping_nonce {
            break;
        }
    }
    seal_known(
        payload,
        recovery,
        KnownMaterial {
            data_key: data_key.to_vec(),
            salt,
            wrapping_nonce,
            payload_nonce,
        },
    )
}
pub fn open(
    text_value: &str,
    recovery: &str,
    expected_app: &str,
    expected_document: &str,
    minimum_revision: u64,
) -> Result<Opened, String> {
    let value = strict(text_value)?;
    let root = object(&value)?;
    let kdf = object(root.get("kdf").ok_or_else(err)?)?;
    let salt = b64(text(kdf, "salt")?, Some(16), 16)?;
    let mut wrapping = derive(recovery, &salt);
    let result = open_with_wrapping_value(
        value,
        &wrapping,
        expected_app,
        expected_document,
        minimum_revision,
    );
    wrapping.zeroize();
    result
}

pub fn recover_account_material(
    text_value: &str,
    recovery: &str,
    expected_app: &str,
    expected_document: &str,
    minimum_revision: u64,
) -> Result<RecoveredAccountMaterial, String> {
    if recovery.chars().count() < 12 {
        return Err(err());
    }
    let value = strict(text_value)?;
    let root = object(&value)?;
    let kdf = object(root.get("kdf").ok_or_else(err)?)?;
    let salt = b64(text(kdf, "salt")?, Some(16), 16)?;
    let wrapping_key = Zeroizing::new(derive(recovery, &salt));
    let (payload, data_key) = open_with_wrapping_value_and_key(
        value,
        wrapping_key.as_slice(),
        expected_app,
        expected_document,
        minimum_revision,
    )?;
    Ok(RecoveredAccountMaterial {
        payload,
        data_key: Zeroizing::new(data_key),
        wrapping_key,
        salt,
    })
}

pub fn open_with_account_wrapping_material(
    text_value: &str,
    material: &AccountWrappingMaterial,
    expected_app: &str,
    expected_document: &str,
    minimum_revision: u64,
) -> Result<Opened, String> {
    if material.wrapping_key.len() != 32 || material.salt.len() != 16 {
        return Err(err());
    }
    let value = strict(text_value)?;
    let root = object(&value)?;
    let kdf = object(root.get("kdf").ok_or_else(err)?)?;
    let salt = b64(text(kdf, "salt")?, Some(16), 16)?;
    if salt != material.salt {
        return Err(err());
    }
    open_with_wrapping_value(
        value,
        &material.wrapping_key,
        expected_app,
        expected_document,
        minimum_revision,
    )
}

fn open_with_wrapping_value(
    value: Value,
    wrapping_key: &[u8],
    expected_app: &str,
    expected_document: &str,
    minimum_revision: u64,
) -> Result<Opened, String> {
    let (payload, mut data_key) = open_with_wrapping_value_and_key(
        value,
        wrapping_key,
        expected_app,
        expected_document,
        minimum_revision,
    )?;
    data_key.zeroize();
    Ok(Opened { payload })
}

fn open_with_wrapping_value_and_key(
    value: Value,
    wrapping_key: &[u8],
    expected_app: &str,
    expected_document: &str,
    minimum_revision: u64,
) -> Result<(Value, Vec<u8>), String> {
    if wrapping_key.len() != 32 {
        return Err(err());
    }
    let (app, doc, revision, hash_value, length) = preflight_value(&value)?;
    if app != expected_app || doc != expected_document || revision < minimum_revision {
        return Err(err());
    }
    let root = object(&value)?;
    let wrap = object(root.get("wrappedDataKey").unwrap())?;
    let payload = object(root.get("payload").unwrap())?;
    let aad = aad(root)?;
    let data_key = Zeroizing::new(crypt(
        wrapping_key,
        &b64(text(wrap, "nonce")?, Some(12), 12)?,
        &b64(text(wrap, "ciphertext")?, Some(48), 48)?,
        &aad,
        true,
    )?);
    let plain = crypt(
        &data_key,
        &b64(text(payload, "nonce")?, Some(12), 12)?,
        &b64(text(payload, "ciphertext")?, None, MAX_PAYLOAD + 16)?,
        &aad,
        true,
    )?;
    if plain.len() != length || hash(&plain) != hash_value {
        return Err(err());
    }
    let payload_text = std::str::from_utf8(&plain).map_err(|_| err())?;
    let payload_value = strict(payload_text)?;
    validate_payload(&payload_value)?;
    let p = object(&payload_value)?;
    if text(p, "appId")? != app
        || text(p, "documentId")? != doc
        || number(p, "revision")? != revision
    {
        return Err(err());
    }
    Ok((payload_value, data_key.to_vec()))
}

#[cfg(test)]
mod tests {
    use super::*;
    fn fixture() -> Value {
        serde_json::from_str(include_str!(
            "../../../protocol/fixtures/nfai.sync.v1.golden.json"
        ))
        .unwrap()
    }
    fn material(f: &Value) -> KnownMaterial {
        let m = f["material"].as_object().unwrap();
        KnownMaterial {
            data_key: b64(m["dataKey"].as_str().unwrap(), Some(32), 32).unwrap(),
            salt: b64(m["salt"].as_str().unwrap(), Some(16), 16).unwrap(),
            wrapping_nonce: b64(m["wrappingNonce"].as_str().unwrap(), Some(12), 12).unwrap(),
            payload_nonce: b64(m["payloadNonce"].as_str().unwrap(), Some(12), 12).unwrap(),
        }
    }
    #[test]
    fn desktop_seal_and_open_match_shared_android_golden() {
        let f = fixture();
        let sealed = seal_known(
            f["payload"].clone(),
            f["recoveryCode"].as_str().unwrap(),
            material(&f),
        )
        .unwrap();
        assert_eq!(sealed, canonical(&f["envelope"]).unwrap());
        assert_eq!(
            open(
                &sealed,
                f["recoveryCode"].as_str().unwrap(),
                "com.nanzhufeng.ai",
                "sync-fixture-v1",
                7
            )
            .unwrap()
            .payload,
            f["payload"]
        );
    }
    #[test]
    fn strict_rejections_fail_closed() {
        let f = fixture();
        let e = canonical(&f["envelope"]).unwrap();
        assert!(open(
            &e,
            "wrong recovery code",
            "com.nanzhufeng.ai",
            "sync-fixture-v1",
            1
        )
        .is_err());
        assert!(open(
            &e.replace("d10aa455", "e10aa455"),
            f["recoveryCode"].as_str().unwrap(),
            "com.nanzhufeng.ai",
            "sync-fixture-v1",
            1
        )
        .is_err());
        assert!(open(
            &e[..e.len() - 6],
            f["recoveryCode"].as_str().unwrap(),
            "com.nanzhufeng.ai",
            "sync-fixture-v1",
            1
        )
        .is_err());
        assert!(open(
            &e,
            f["recoveryCode"].as_str().unwrap(),
            "other.app",
            "sync-fixture-v1",
            1
        )
        .is_err());
        assert!(open(
            &e,
            f["recoveryCode"].as_str().unwrap(),
            "com.nanzhufeng.ai",
            "sync-fixture-v1",
            8
        )
        .is_err());
        assert!(strict(&(e[..e.len() - 1].to_owned() + ",\"appId\":\"x\"}")).is_err());
        let mut bad = f["payload"].clone();
        bad["records"][0]["classification"] = Value::String("HIGH_SENSITIVE".into());
        assert!(seal_known(bad, "fixture recovery code only", material(&f)).is_err());
        let mut unclassified_secret = f["payload"].clone();
        unclassified_secret["records"][0]["content"]["apiKey"] = Value::String("forbidden".into());
        assert!(seal_known(
            unclassified_secret,
            "fixture recovery code only",
            material(&f)
        )
        .is_err());
    }

    #[derive(Default)]
    struct LocalTestOnlyCloud {
        documents: std::collections::BTreeMap<(String, String), (String, u64, String)>,
        receipts: std::collections::BTreeMap<(String, String, String), (u64, String)>,
    }
    impl LocalTestOnlyCloud {
        fn commit(
            &mut self,
            account: &str,
            expected: u64,
            envelope: String,
            intent: &str,
        ) -> Result<(u64, String), String> {
            let key: (String, String) = (
                account.into(),
                strict(&envelope)?
                    .get("documentId")
                    .and_then(Value::as_str)
                    .ok_or_else(err)?
                    .into(),
            );
            if let Some(receipt) = self
                .receipts
                .get(&(key.0.clone(), key.1.clone(), intent.into()))
            {
                return Ok(receipt.clone());
            }
            let (_, document, revision, hash_value, _) = preflight_value(&strict(&envelope)?)?;
            let current = self
                .documents
                .get(&key)
                .map(|(_, revision, _)| *revision)
                .unwrap_or(0);
            if document != key.1 || current != expected || revision != expected + 1 {
                return Err("LOCAL_TEST_ONLY_STALE_REVISION".into());
            }
            self.documents
                .insert(key.clone(), (envelope, revision, hash_value.clone()));
            self.receipts.insert(
                (key.0, key.1, intent.into()),
                (revision, hash_value.clone()),
            );
            Ok((revision, hash_value))
        }
        fn read(&self, account: &str, document: &str) -> Option<&(String, u64, String)> {
            self.documents.get(&(account.into(), document.into()))
        }
    }

    #[test]
    fn local_test_only_android_fixture_desktop_restore_edit_commit_and_conflict_are_opaque() {
        // LOCAL_TEST_ONLY fake RPC: no HTTP/Tauri command and no plaintext storage.
        let f = fixture();
        let mut cloud = LocalTestOnlyCloud::default();
        let android_fixture_envelope = canonical(&f["envelope"]).unwrap();
        // The shared fixture is Android-known-answer revision 7; inject only this opaque envelope
        // as already-existing remote state, then exercise normal expected-revision RPC semantics.
        cloud.documents.insert(
            ("account-a".into(), "sync-fixture-v1".into()),
            (
                android_fixture_envelope,
                7,
                f["envelope"]["payloadHash"].as_str().unwrap().into(),
            ),
        );
        let remote = cloud.read("account-a", "sync-fixture-v1").unwrap();
        let mut desktop_snapshot = open(
            &remote.0,
            f["recoveryCode"].as_str().unwrap(),
            "com.nanzhufeng.ai",
            "sync-fixture-v1",
            7,
        )
        .unwrap()
        .payload;
        desktop_snapshot["revision"] = json!(8);
        desktop_snapshot["records"][0]["content"]["title"] = json!("Desktop local edit");
        let desktop_envelope = seal(
            desktop_snapshot,
            f["recoveryCode"].as_str().unwrap(),
            &b64(f["material"]["dataKey"].as_str().unwrap(), Some(32), 32).unwrap(),
        )
        .unwrap();
        let second = cloud
            .commit("account-a", 7, desktop_envelope, "desktop-edit")
            .unwrap();
        assert_eq!(second.0, 8);
        assert_eq!(
            cloud
                .commit(
                    "account-a",
                    7,
                    cloud
                        .read("account-a", "sync-fixture-v1")
                        .unwrap()
                        .0
                        .clone(),
                    "desktop-edit"
                )
                .unwrap(),
            second
        );
        assert!(cloud
            .commit(
                "account-a",
                7,
                cloud
                    .read("account-a", "sync-fixture-v1")
                    .unwrap()
                    .0
                    .clone(),
                "stale-worker"
            )
            .is_err());
        assert!(cloud.read("account-b", "sync-fixture-v1").is_none());
        assert!(open(
            &cloud.read("account-a", "sync-fixture-v1").unwrap().0,
            "wrong",
            "com.nanzhufeng.ai",
            "sync-fixture-v1",
            8
        )
        .is_err());
    }
}
