//! Read-only OpenRouter catalog verification. This owner never handles credentials or prompts.
#[cfg(test)]
#[test]
fn argon_cached_mapping_budget_and_missing_previous_snapshot_are_honest() {
    let base = ["anthropic/claude-opus-5", "openai/gpt-5.6-sol", "openai/gpt-5.6-terra", "google/gemini-3.8-flash"];
    let mut entries = base.map(|id| serde_json::json!({"id":id,"name":id,"context_length":8192,"architecture":{"input_modalities":["text"],"output_modalities":["text"]}})).to_vec();
    let old = verify_payload(&serde_json::to_vec(&serde_json::json!({"data":entries})).unwrap(), None).unwrap();
    let public: serde_json::Value = serde_json::from_str(include_str!("../../../protocol/fixtures/gemini-4-argon-20261015.json")).unwrap();
    entries.push(public["data"][0].clone());
    let current = verify_payload(&serde_json::to_vec(&serde_json::json!({"data":entries})).unwrap(), None).unwrap();
    let root = tempfile::tempdir().unwrap();
    persist(root.path(), old, 1).unwrap();
    assert_eq!(is_mapped_in_stable_catalog(root.path(), "GEMINI_4_ARGON", "google/gemini-4-argon"), Some(false));
    persist(root.path(), current, 2).unwrap();
    assert_eq!(budget_for(root.path(), "GEMINI_4_ARGON", "google/gemini-4-argon"), Some((Some(1048576), Some(65536))));
    assert_eq!(supports_input_modality(root.path(), "GEMINI_4_ARGON", "google/gemini-4-argon", "image"), Some(true));
    assert_eq!(is_mapped_in_stable_catalog(root.path(), "CLAUDE_OPUS_5_5", "google/gemini-4-argon"), Some(false));
    std::fs::write(root.path().join("model-registry-openrouter-current-v1.json"), b"corrupt synthetic current").unwrap();
    assert_eq!(is_mapped_in_stable_catalog(root.path(), "GEMINI_4_ARGON", "google/gemini-4-argon"), Some(false));
}

use serde::{Deserialize, Serialize};
use serde_json::Value;
use sha2::{Digest, Sha256};
use std::collections::BTreeSet;
use std::fs;
use std::io::Read;
use std::path::Path;
use std::time::Duration;

pub const MODELS_URL: &str = "https://openrouter.ai/api/v1/models";
const MAX_RESPONSE_BYTES: usize = 4 * 1024 * 1024;

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Model {
    pub id: String,
    pub display_name: String,
    pub context_window_tokens: Option<u64>,
    pub max_output_tokens: Option<u64>,
    pub input_modalities: Vec<String>,
    pub output_modalities: Vec<String>,
    pub supported_parameters: Vec<String>,
    pub input_micros_per_token: Option<u64>,
    pub output_micros_per_token: Option<u64>,
    pub cached_input_micros_per_token: Option<u64>,
    // USD catalog prices in integer microdollars per million tokens. Optional
    // and omitted when unknown so pre-existing v1 snapshots retain their hash.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub input_micros_per_million_tokens: Option<u64>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub output_micros_per_million_tokens: Option<u64>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub cached_input_micros_per_million_tokens: Option<u64>,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct VerifiedCatalog {
    pub catalog_version: String,
    pub catalog_sha256: String,
    pub source_etag: Option<String>,
    pub models: Vec<Model>,
    pub preset_mappings: Vec<PresetMapping>,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PresetMapping {
    pub preset_id: String,
    pub model_id: String,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct StoredCatalog {
    pub format: String,
    pub version: u32,
    pub verified_at_ms: u64,
    pub catalog: VerifiedCatalog,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RegistryStatus {
    pub status: &'static str,
    pub source_url: Option<&'static str>,
    pub verified_at_ms: Option<u64>,
    pub catalog_version: Option<String>,
    pub mapped_preset_count: usize,
    pub mapped_preset_ids: Vec<String>,
    pub mapped_model_ids: Vec<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub input_modalities_by_preset: Option<std::collections::BTreeMap<String, Vec<String>>>,
}

pub fn status(root: &Path) -> RegistryStatus {
    match load_stable(root) {
        Some((stored, previous)) => RegistryStatus {
            status: if previous { "STABLE_FALLBACK" } else { "VERIFIED" },
            source_url: Some(MODELS_URL), verified_at_ms: Some(stored.verified_at_ms),
            catalog_version: Some(stored.catalog.catalog_version),
            mapped_preset_count: stored.catalog.preset_mappings.len(),
            mapped_preset_ids: stored.catalog.preset_mappings.iter().map(|item| item.preset_id.clone()).collect(),
            mapped_model_ids: stored.catalog.preset_mappings.iter().map(|item| item.model_id.clone()).collect(),
            input_modalities_by_preset: Some(stored.catalog.preset_mappings.iter().filter_map(|mapping|
                stored.catalog.models.iter().find(|model|model.id==mapping.model_id)
                    .map(|model|(mapping.preset_id.clone(),model.input_modalities.clone()))).collect()),
        },
        None => RegistryStatus {
            status: "NOT_VERIFIED", source_url: None, verified_at_ms: None,
            catalog_version: None, mapped_preset_count: 0,
            mapped_preset_ids: Vec::new(),
            mapped_model_ids: Vec::new(),
            input_modalities_by_preset: None,
        },
    }
}

fn valid_catalog(catalog: &VerifiedCatalog) -> bool {
    if catalog.catalog_version.len() != 16 || catalog.catalog_sha256.len() != 64
        || catalog.models.is_empty() || catalog.preset_mappings.is_empty() { return false; }
    let mut ids = BTreeSet::new();
    if catalog.models.iter().any(|model| !ids.insert(model.id.as_str())) { return false; }
    let mut presets = BTreeSet::new();
    if catalog.preset_mappings.iter().any(|mapping| {
        mapping.preset_id.trim().is_empty() || !presets.insert(mapping.preset_id.as_str())
            || !ids.contains(mapping.model_id.as_str())
            || crate::desktop_model_service_v1::PRESETS.iter()
                .find(|preset| preset.provider_id == "OPENROUTER" && preset.id == mapping.preset_id)
                .is_some_and(|preset| preset.model_id != mapping.model_id)
    }) { return false; }
    let Ok(encoded) = serde_json::to_vec(&(&catalog.models, &catalog.preset_mappings)) else { return false; };
    let hash = format!("{:x}", Sha256::digest(encoded));
    hash == catalog.catalog_sha256 && hash[..16] == catalog.catalog_version
}

fn read_stored(path: &Path) -> Option<StoredCatalog> {
    #[cfg(windows)]
    crate::desktop_windows_private_store::secure_path(path, false).ok()?;
    let bytes = fs::read(path).ok()?;
    if bytes.len() > MAX_RESPONSE_BYTES { return None; }
    let stored: StoredCatalog = serde_json::from_slice(&bytes).ok()?;
    (stored.format == "nfai.openrouter-registry" && stored.version == 1
        && valid_catalog(&stored.catalog)).then_some(stored)
}

pub fn load_stable(root: &Path) -> Option<(StoredCatalog, bool)> {
    let current = root.join("model-registry-openrouter-current-v1.json");
    let previous = root.join("model-registry-openrouter-previous-v1.json");
    read_stored(&current).map(|stored| (stored, false))
        .or_else(|| read_stored(&previous).map(|stored| (stored, true)))
}

pub fn budget_for(root: &Path, preset_id: &str, model_id: &str) -> Option<(Option<u64>, Option<u64>)> {
    let (stored, _) = load_stable(root)?;
    stored.catalog.preset_mappings.iter()
        .find(|mapping| mapping.preset_id == preset_id && mapping.model_id == model_id)?;
    let model = stored.catalog.models.iter().find(|model| model.id == model_id)?;
    Some((model.context_window_tokens, model.max_output_tokens))
}

pub fn is_mapped_in_stable_catalog(root: &Path, preset_id: &str, model_id: &str) -> Option<bool> {
    let (stored, _) = load_stable(root)?;
    Some(stored.catalog.preset_mappings.iter().any(|mapping|
        mapping.preset_id == preset_id && mapping.model_id == model_id))
}

pub fn supports_input_modality(root: &Path, preset_id: &str, model_id: &str, modality: &str) -> Option<bool> {
    let (stored, _) = load_stable(root)?;
    let mapped = stored.catalog.preset_mappings.iter().any(|mapping|
        mapping.preset_id == preset_id && mapping.model_id == model_id);
    Some(mapped && stored.catalog.models.iter().any(|model|
        model.id == model_id && model.input_modalities.iter().any(|value| value == modality)))
}

fn write_atomic(path: &Path, bytes: &[u8]) -> Result<(), &'static str> {
    #[cfg(windows)]
    {
        crate::desktop_windows_private_store::atomic_write_private(path, bytes)
            .map_err(|_| "REGISTRY_STORE_WRITE")
    }
    #[cfg(not(windows))]
    {
        use std::io::Write;
        let temporary = path.with_extension(format!("{}.tmp", std::process::id()));
        let result = (|| {
            let mut file = fs::File::create(&temporary).map_err(|_| "REGISTRY_STORE_WRITE")?;
            file.write_all(bytes).map_err(|_| "REGISTRY_STORE_WRITE")?;
            file.sync_all().map_err(|_| "REGISTRY_STORE_WRITE")?;
            fs::rename(&temporary, path).map_err(|_| "REGISTRY_STORE_WRITE")
        })();
        if result.is_err() { let _ = fs::remove_file(&temporary); }
        result
    }
}

pub fn persist(root: &Path, catalog: VerifiedCatalog, verified_at_ms: u64) -> Result<StoredCatalog, &'static str> {
    if !valid_catalog(&catalog) { return Err("REGISTRY_STORE_INVALID"); }
    #[cfg(windows)]
    crate::desktop_windows_private_store::secure_path(root, true).map_err(|_| "REGISTRY_STORE_ROOT")?;
    #[cfg(not(windows))]
    if !root.is_dir() { return Err("REGISTRY_STORE_ROOT"); }
    let stored = StoredCatalog {
        format: "nfai.openrouter-registry".into(), version: 1, verified_at_ms, catalog,
    };
    let bytes = serde_json::to_vec(&stored).map_err(|_| "REGISTRY_STORE_INVALID")?;
    if bytes.len() > MAX_RESPONSE_BYTES {
        return Err("公开模型目录超过保存上限，已保留原稳定目录，请稍后重试");
    }
    let current = root.join("model-registry-openrouter-current-v1.json");
    let previous = root.join("model-registry-openrouter-previous-v1.json");
    if let Some(old) = read_stored(&current) {
        let encoded = serde_json::to_vec(&old).map_err(|_| "REGISTRY_STORE_INVALID")?;
        write_atomic(&previous, &encoded)?;
        if read_stored(&previous).as_ref() != Some(&old) { return Err("REGISTRY_STORE_VERIFY"); }
    }
    write_atomic(&current, &bytes)?;
    if read_stored(&current).as_ref() != Some(&stored) { return Err("REGISTRY_STORE_VERIFY"); }
    Ok(stored)
}

fn strings(value: &Value, key: &str) -> Vec<String> {
    let mut values = value.get(key).and_then(Value::as_array).into_iter().flatten()
        .filter_map(Value::as_str)
        .map(|item| item.trim().to_ascii_lowercase())
        .filter(|item| !item.is_empty() && item.len() <= 80)
        .collect::<BTreeSet<_>>()
        .into_iter().collect::<Vec<_>>();
    values.sort();
    values
}

fn positive_integer(value: Option<&Value>) -> Option<u64> {
    value.and_then(Value::as_u64).filter(|number| *number > 0)
}

fn micros(value: Option<&Value>) -> Option<u64> {
    scaled_usd_per_token(value, 6)
}

fn micros_per_million_tokens(value: Option<&Value>) -> Option<u64> {
    // 10^6 microdollars/USD * 10^6 tokens/million. No float, rounding,
    // tariff lookup or inferred price is involved.
    scaled_usd_per_token(value, 12)
}

fn scaled_usd_per_token(value: Option<&Value>, decimal_places: u32) -> Option<u64> {
    let raw = value?.as_str()?.trim();
    if raw.is_empty() || raw.starts_with('-') || raw.starts_with('+') { return None; }
    let (whole, fraction) = raw.split_once('.').unwrap_or((raw, ""));
    if whole.is_empty() || !whole.bytes().all(|byte| byte.is_ascii_digit())
        || !fraction.bytes().all(|byte| byte.is_ascii_digit()) { return None; }
    let significant = fraction.trim_end_matches('0');
    if significant.len() > decimal_places as usize { return None; }
    let whole = whole.parse::<u64>().ok()?;
    let fractional = if significant.is_empty() { 0 } else {
        significant.parse::<u64>().ok()?.checked_mul(10_u64.checked_pow(decimal_places - significant.len() as u32)?)?
    };
    whole.checked_mul(10_u64.checked_pow(decimal_places)?)?.checked_add(fractional)
}

pub fn verify_payload(bytes: &[u8], source_etag: Option<&str>) -> Result<VerifiedCatalog, &'static str> {
    if bytes.is_empty() || bytes.len() > MAX_RESPONSE_BYTES { return Err("REGISTRY_RESPONSE_SIZE"); }
    let response: Value = serde_json::from_slice(bytes).map_err(|_| "REGISTRY_MALFORMED")?;
    let entries = response.get("data").and_then(Value::as_array).ok_or("REGISTRY_MALFORMED")?;
    let mut models = Vec::new();
    let mut seen = BTreeSet::new();
    for entry in entries {
        let Some(id) = entry.get("id").and_then(Value::as_str).map(str::trim) else { continue };
        let Some(name) = entry.get("name").and_then(Value::as_str).map(str::trim) else { continue };
        if id.is_empty() || name.is_empty() || id.len() > 240 || name.chars().count() > 160 { continue; }
        if !seen.insert(id.to_owned()) { return Err("REGISTRY_DUPLICATE_MODEL"); }
        let architecture = &entry["architecture"];
        let pricing = &entry["pricing"];
        // Both integer views describe the same chosen public rate. A fractional
        // or malformed preferred value must not turn into a different alias's
        // integer price; a missing/null preferred field may use its old alias.
        let cached_price = pricing.get("cache_read").filter(|value| !value.is_null())
            .or_else(|| pricing.get("input_cache_read"));
        models.push(Model {
            id: id.into(), display_name: name.into(),
            context_window_tokens: positive_integer(entry.get("context_length")),
            max_output_tokens: positive_integer(entry.get("top_provider").and_then(|value| value.get("max_completion_tokens"))),
            input_modalities: strings(architecture, "input_modalities"),
            output_modalities: strings(architecture, "output_modalities"),
            supported_parameters: strings(entry, "supported_parameters"),
            input_micros_per_token: micros(pricing.get("prompt")),
            output_micros_per_token: micros(pricing.get("completion")),
            cached_input_micros_per_token: micros(cached_price),
            input_micros_per_million_tokens: micros_per_million_tokens(pricing.get("prompt")),
            output_micros_per_million_tokens: micros_per_million_tokens(pricing.get("completion")),
            cached_input_micros_per_million_tokens: micros_per_million_tokens(cached_price),
        });
    }
    if models.is_empty() { return Err("REGISTRY_EMPTY"); }
    models.sort_by(|left, right| left.id.cmp(&right.id));
    let preset_mappings = crate::desktop_model_service_v1::PRESETS.iter()
        .filter(|preset| preset.provider_id == "OPENROUTER" && preset.id != "KIMI_K3")
        .filter(|preset| models.iter().any(|model| model.id == preset.model_id
            && model.input_modalities.iter().any(|value| value == "text")
            && model.output_modalities.iter().any(|value| value == "text")))
        .map(|preset| PresetMapping { preset_id: preset.id.into(), model_id: preset.model_id.into() })
        .collect::<Vec<_>>();
    let covers = |required: &[&str]| required.iter().all(|id|
        preset_mappings.iter().any(|mapping| mapping.preset_id == *id));
    // Keep readable legacy snapshots, but never use a legacy mapping as proof
    // for a replacement deployment. Availability matches logical and wire ID.
    if !covers(&["CLAUDE_OPUS_5_5", "GPT_6_1_SOL", "GPT_6_LUNA", "GEMINI_3_8_FLASH"])
        && !covers(&["CLAUDE_OPUS_5", "GPT_5_6_SOL", "GPT_5_6_TERRA", "GEMINI_3_8_FLASH"]) {
        return Err("REGISTRY_PRESET_MAPPING");
    }
    let canonical = serde_json::to_vec(&(&models, &preset_mappings)).map_err(|_| "REGISTRY_MALFORMED")?;
    let hash = format!("{:x}", Sha256::digest(canonical));
    Ok(VerifiedCatalog {
        catalog_version: hash[..16].into(), catalog_sha256: hash,
        source_etag: source_etag.filter(|value| value.len() <= 256).map(str::to_owned),
        models, preset_mappings,
    })
}

pub fn fetch() -> Result<VerifiedCatalog, &'static str> {
    let client = reqwest::blocking::Client::builder()
        .redirect(reqwest::redirect::Policy::none())
        .connect_timeout(Duration::from_secs(8))
        .timeout(Duration::from_secs(20))
        .build().map_err(|_| "REGISTRY_NETWORK")?;
    let response = client.get(MODELS_URL).header(reqwest::header::ACCEPT, "application/json")
        .send().map_err(|_| "REGISTRY_NETWORK")?;
    if response.status().is_redirection() { return Err("REGISTRY_REDIRECT"); }
    if response.status() != reqwest::StatusCode::OK { return Err("REGISTRY_HTTP_STATUS"); }
    let etag = response.headers().get(reqwest::header::ETAG).and_then(|value| value.to_str().ok()).map(str::to_owned);
    let mut bytes = Vec::new();
    response.take((MAX_RESPONSE_BYTES + 1) as u64).read_to_end(&mut bytes)
        .map_err(|_| "REGISTRY_NETWORK")?;
    verify_payload(&bytes, etag.as_deref())
}

#[cfg(test)]
mod tests {
    use super::*;
    include!("openrouter_price_precision_tests.rs");

    #[test]
    #[ignore = "explicit fixed public GET smoke; no credential or user content"]
    fn p2j_live_public_catalog_smoke() {
        let catalog = fetch().unwrap();
        assert!(!catalog.models.is_empty());
        assert!(catalog.preset_mappings.len() >= 4);
        assert!(valid_catalog(&catalog));
        println!("models={} mapped={} version={}", catalog.models.len(),
            catalog.preset_mappings.len(), catalog.catalog_version);
    }

    fn sample_catalog(context: u64) -> VerifiedCatalog {
        let entries = ["anthropic/claude-opus-5", "openai/gpt-5.6-sol",
            "openai/gpt-5.6-terra", "google/gemini-3.8-flash"]
            .map(|id| serde_json::json!({"id":id,"name":id,"context_length":context,
                "architecture":{"input_modalities":["text"],"output_modalities":["text"]}}));
        verify_payload(&serde_json::to_vec(&serde_json::json!({"data":entries})).unwrap(), None).unwrap()
    }

    fn registry_mapping_rehash(catalog: &mut VerifiedCatalog) {
        let bytes=serde_json::to_vec(&(&catalog.models,&catalog.preset_mappings)).unwrap();
        catalog.catalog_sha256=format!("{:x}",Sha256::digest(bytes));
        catalog.catalog_version=catalog.catalog_sha256[..16].into();
    }

    fn registry_mapping_fixtures() -> Vec<(&'static str,StoredCatalog)> {
        let make=|image:Option<&str>| {
            let ids=["anthropic/claude-opus-5.5","openai/gpt-6.1-sol","openai/gpt-6-luna","google/gemini-3.8-flash","moonshotai/kimi-k3"];
            let entries=ids.map(|id|serde_json::json!({"id":id,"name":id,"context_length":32768,
                "architecture":{"input_modalities":if image==Some(id)||id=="moonshotai/kimi-k3"{vec!["text","image"]}else{vec!["text"]},"output_modalities":["text"]}}));
            verify_payload(&serde_json::to_vec(&serde_json::json!({"data":entries})).unwrap(),Some("synthetic-registry-mapping")).unwrap()
        };
        let text=make(None);let image=make(Some("openai/gpt-6-luna"));
        let mut duplicate=text.clone();duplicate.preset_mappings.push(PresetMapping {preset_id:"GEMINI_3_8_FLASH".into(),model_id:"moonshotai/kimi-k3".into()});registry_mapping_rehash(&mut duplicate);
        let mut wrong=text.clone();wrong.preset_mappings.iter_mut().find(|p|p.preset_id=="GEMINI_3_8_FLASH").unwrap().model_id="moonshotai/kimi-k3".into();registry_mapping_rehash(&mut wrong);
        let mut empty=text.clone();empty.preset_mappings[0].preset_id=String::new();registry_mapping_rehash(&mut empty);
        let wrap=|catalog,verified_at_ms|StoredCatalog {format:"nfai.openrouter-registry".into(),version:1,verified_at_ms,catalog};
        vec![("text-only",wrap(text,1)),("luna-image",wrap(image,3)),("duplicate-preset",wrap(duplicate,2)),("wrong-wire",wrap(wrong,2)),("empty-preset",wrap(empty,2))]
    }

    fn registry_mapping_export_fixtures(fixtures: &[(&str,StoredCatalog)]) {
        let target=std::path::Path::new(env!("CARGO_MANIFEST_DIR")).join("../../docs/review/20261022-windows-registry-mapping-isolation/registry-fixtures.json");
        let bytes=serde_json::to_vec_pretty(fixtures).unwrap();
        if target.exists(){assert_eq!(std::fs::read(target).unwrap(),bytes)}else{use std::io::Write;std::fs::OpenOptions::new().write(true).create_new(true).open(target).unwrap().write_all(&bytes).unwrap()}
    }

    #[test]
    fn registry_mapping_persist_rejects_hash_matching_ambiguity_without_changing_cache() {
        let fixtures=registry_mapping_fixtures();registry_mapping_export_fixtures(&fixtures);
        let root=tempfile::tempdir().unwrap();let previous=&fixtures[0].1;persist(root.path(),previous.catalog.clone(),1).unwrap();
        let path=root.path().join("model-registry-openrouter-current-v1.json");let before=fs::read(&path).unwrap();
        for (name,bad) in &fixtures[2..] {
            assert!(persist(root.path(),bad.catalog.clone(),2).is_err(),"invalid mapping {name} was published despite its matching hash");
            assert_eq!(fs::read(&path).unwrap(),before);assert_eq!(load_stable(root.path()).unwrap().0.catalog,previous.catalog);
        }
    }

    #[test]
    fn registry_mapping_reader_falls_back_without_promoting_ambiguous_capabilities() {
        let fixtures=registry_mapping_fixtures();registry_mapping_export_fixtures(&fixtures);
        for (name,bad) in &fixtures[2..] {
            let root=tempfile::tempdir().unwrap();persist(root.path(),fixtures[0].1.catalog.clone(),1).unwrap();persist(root.path(),fixtures[1].1.catalog.clone(),3).unwrap();
            let path=root.path().join("model-registry-openrouter-current-v1.json");let bytes=serde_json::to_vec_pretty(bad).unwrap();write_atomic(&path,&bytes).unwrap();
            let projection=serde_json::to_value(status(root.path())).unwrap();assert_eq!(projection["status"],"STABLE_FALLBACK","{name}");
            assert_eq!(projection["inputModalitiesByPreset"]["GEMINI_3_8_FLASH"],serde_json::json!(["text"]));
            assert_eq!(supports_input_modality(root.path(),"GEMINI_3_8_FLASH","google/gemini-3.8-flash","image"),Some(false));
            assert_eq!(fs::read(&path).unwrap(),bytes,"invalid current cache is retained as evidence");
        }
    }

    #[test]
    fn registry_mapping_legacy_distinct_presets_may_share_a_model_without_rewriting() {
        let mut catalog=sample_catalog(8192);let model=catalog.models[0].id.clone();catalog.preset_mappings.push(PresetMapping {preset_id:"SYNTHETIC_HISTORY_ALIAS".into(),model_id:model.clone()});registry_mapping_rehash(&mut catalog);
        let root=tempfile::tempdir().unwrap();let stored=persist(root.path(),catalog,1).unwrap();let path=root.path().join("model-registry-openrouter-current-v1.json");let before=fs::read(&path).unwrap();
        assert_eq!(load_stable(root.path()).unwrap(),(stored,false));assert_eq!(budget_for(root.path(),"SYNTHETIC_HISTORY_ALIAS",&model),Some((Some(8192),None)));assert_eq!(fs::read(path).unwrap(),before);
    }

    #[test]
    fn p2j_whitelist_hash_ignores_unknown_fields_and_rejects_duplicates() {
        let entry = |id: &str| serde_json::json!({
            "id":id,"name":id,"context_length":8192,
            "architecture":{"input_modalities":["text"],"output_modalities":["text"]},
            "supported_parameters":["stream"],
            "pricing":{"prompt":"0.000001","completion":"0.000002","cache_read":null},
            "private_token":"must-not-persist"
        });
        let mut entries = ["anthropic/claude-opus-5", "openai/gpt-5.6-sol",
            "openai/gpt-5.6-terra", "google/gemini-3.8-flash"]
            .map(entry).to_vec();
        let first = serde_json::to_vec(&serde_json::json!({"data":entries})).unwrap();
        entries.reverse();
        for item in &mut entries { item.as_object_mut().unwrap().remove("private_token"); }
        let second = serde_json::to_vec(&serde_json::json!({"secret":"different","data":entries})).unwrap();
        let verified = verify_payload(&first, Some("etag-fixture")).unwrap();
        assert_eq!(verified.catalog_sha256, verify_payload(&second, None).unwrap().catalog_sha256);
        assert_eq!(verified.models[0].input_micros_per_token, Some(1));
        assert_eq!(verified.models[0].cached_input_micros_per_token, None);
        assert_eq!(micros(Some(&serde_json::json!("0.000001"))), Some(1));
        assert_eq!(micros(Some(&serde_json::json!("0.0000005"))), None);
        assert_eq!(verified.preset_mappings.len(), 4);
        assert!(!serde_json::to_string(&verified).unwrap().contains("must-not-persist"));
        let duplicate = br#"{"data":[{"id":"a","name":"A"},{"id":"a","name":"B"}]}"#;
        assert_eq!(verify_payload(duplicate, None).unwrap_err(), "REGISTRY_DUPLICATE_MODEL");
        let incomplete = br#"{"data":[{"id":"openai/gpt-5.6-terra","name":"Terra","architecture":{"input_modalities":["text"],"output_modalities":["text"]}}]}"#;
        assert_eq!(verify_payload(incomplete, None).unwrap_err(), "REGISTRY_PRESET_MAPPING");
    }

    #[test]
    fn p2j_private_store_keeps_previous_verified_catalog_on_corrupt_current() {
        let directory = tempfile::tempdir().unwrap();
        let old = persist(directory.path(), sample_catalog(8192), 1).unwrap();
        let new = persist(directory.path(), sample_catalog(16384), 2).unwrap();
        assert_eq!(load_stable(directory.path()).unwrap(), (new.clone(), false));
        let current = directory.path().join("model-registry-openrouter-current-v1.json");
        fs::write(&current, b"corrupt synthetic current").unwrap();
        assert_eq!(load_stable(directory.path()).unwrap(), (old, true));
        assert_eq!(persist(directory.path(), new.catalog.clone(), 3).unwrap().verified_at_ms, 3);
        assert_eq!(load_stable(directory.path()).unwrap().0.verified_at_ms, 3);
    }
    #[test]
    fn image_capability_projection_matches_verified_owner_without_rewriting_the_cache() {
        let entries=["anthropic/claude-opus-5.5","openai/gpt-6.1-sol","openai/gpt-6-luna","google/gemini-3.8-flash"].map(|id|serde_json::json!({"id":id,"name":id,"context_length":32768,"architecture":{"input_modalities":if id=="google/gemini-3.8-flash"{vec!["text"]}else{vec!["text","image"]},"output_modalities":["text"]}}));
        let catalog=verify_payload(&serde_json::to_vec(&serde_json::json!({"data":entries})).unwrap(),Some("synthetic-image-modality")).unwrap();
        let root=tempfile::tempdir().unwrap();let stored=persist(root.path(),catalog.clone(),1_792_310_400_000).unwrap();
        let path=root.path().join("model-registry-openrouter-current-v1.json");let before=std::fs::read(&path).unwrap();
        let projection=serde_json::to_value(status(root.path())).unwrap();
        assert_eq!(projection["inputModalitiesByPreset"]["GEMINI_3_8_FLASH"],serde_json::json!(["text"]));
        assert_eq!(projection["inputModalitiesByPreset"]["GPT_6_LUNA"],serde_json::json!(["image","text"]));
        assert_eq!(supports_input_modality(root.path(),"GEMINI_3_8_FLASH","google/gemini-3.8-flash","image"),Some(false));
        assert_eq!(supports_input_modality(root.path(),"GPT_6_LUNA","openai/gpt-6-luna","image"),Some(true));
        assert_eq!(std::fs::read(&path).unwrap(),before);
        persist(root.path(),catalog,1_792_310_400_001).unwrap();std::fs::write(&path,b"synthetic corrupt current").unwrap();
        let fallback=serde_json::to_value(status(root.path())).unwrap();assert_eq!(fallback["status"],"STABLE_FALLBACK");assert_eq!(fallback["inputModalitiesByPreset"],projection["inputModalitiesByPreset"]);
        let empty=tempfile::tempdir().unwrap();assert!(serde_json::to_value(status(empty.path())).unwrap().get("inputModalitiesByPreset").is_none());
        let target=std::path::Path::new(env!("CARGO_MANIFEST_DIR")).join("../../docs/review/20261018-windows-auto-verified-image/synthetic-registry-v1.json");
        let bytes=serde_json::to_vec_pretty(&stored).unwrap();
        if target.exists(){assert_eq!(std::fs::read(target).unwrap(),bytes);}else{use std::io::Write;std::fs::OpenOptions::new().write(true).create_new(true).open(target).unwrap().write_all(&bytes).unwrap();}
        let entries=["anthropic/claude-opus-5.5","openai/gpt-6.1-sol","openai/gpt-6-luna","google/gemini-3.8-flash"].map(|id|serde_json::json!({"id":id,"name":id,"context_length":32768,"architecture":{"input_modalities":["text"],"output_modalities":["text"]}}));
        let catalog=verify_payload(&serde_json::to_vec(&serde_json::json!({"data":entries})).unwrap(),Some("synthetic-text-only")).unwrap();
        let root=tempfile::tempdir().unwrap();let stored=persist(root.path(),catalog,1_792_310_400_000).unwrap();
        assert_eq!(supports_input_modality(root.path(),"GPT_6_LUNA","openai/gpt-6-luna","image"),Some(false));
        let target=std::path::Path::new(env!("CARGO_MANIFEST_DIR")).join("../../docs/review/20261018-windows-auto-verified-image/synthetic-registry-text-only-v1.json");
        let bytes=serde_json::to_vec_pretty(&stored).unwrap();
        if target.exists(){assert_eq!(std::fs::read(target).unwrap(),bytes);}else{use std::io::Write;std::fs::OpenOptions::new().write(true).create_new(true).open(target).unwrap().write_all(&bytes).unwrap();}
    }

}
