fn a9_catalog_priced_payload() -> Value {
    let mut entries = ["anthropic/claude-opus-5", "openai/gpt-5.6-sol", "openai/gpt-5.6-terra", "google/gemini-3.8-flash"]
        .map(|id| serde_json::json!({"id":id,"name":id,"context_length":8192,
            "architecture":{"input_modalities":["text"],"output_modalities":["text"]}})).to_vec();
    let public: Value = serde_json::from_str(include_str!("../../../protocol/fixtures/gemini-4-argon-20261015.json")).unwrap();
    entries.push(public["data"][0].clone());
    serde_json::json!({"data":entries})
}

fn a9_catalog_argon(catalog: &VerifiedCatalog) -> Value {
    serde_json::to_value(catalog.models.iter().find(|model| model.id == "google/gemini-4-argon").unwrap()).unwrap()
}

#[test]
fn a9_catalog_argon_sub_micro_price_survives_verified_cache() {
    let catalog = verify_payload(&serde_json::to_vec(&a9_catalog_priced_payload()).unwrap(), None).unwrap();
    let model = a9_catalog_argon(&catalog);
    assert_eq!(model["inputMicrosPerToken"], 2);
    assert_eq!(model["outputMicrosPerToken"], 10);
    assert_eq!(model["cachedInputMicrosPerToken"], Value::Null, "legacy integer rate must not claim free cached input");
    assert_eq!(model["inputMicrosPerMillionTokens"], 2_000_000);
    assert_eq!(model["outputMicrosPerMillionTokens"], 10_000_000);
    assert_eq!(model["cachedInputMicrosPerMillionTokens"], 100_000);
    let root = tempfile::tempdir().unwrap();
    let stored = persist(root.path(), catalog, 2).unwrap();
    assert_eq!(load_stable(root.path()).unwrap(), (stored, false));
    assert_eq!(budget_for(root.path(), "GEMINI_4_ARGON", "google/gemini-4-argon"), Some((Some(1_048_576), Some(65_536))));
}

#[test]
fn a9_catalog_precision_never_rounds_tiny_invalid_or_overflow_prices_to_free() {
    for (raw, expected) in [
        ("0", Some(0)), ("0.0000001", Some(100_000)), ("0.000000000001", Some(1)),
        ("0.0000000000001", None), ("1.234567890123000", Some(1_234_567_890_123)),
        ("2.5500", Some(2_550_000_000_000)), ("+0.000001", None), ("-0.000001", None),
        ("1e-7", None), ("1.2.3", None), ("18446744.073709551615", Some(u64::MAX)),
        ("18446744.073709551616", None), ("18446745", None),
    ] {
        let mut payload = a9_catalog_priced_payload();
        payload["data"][4]["pricing"]["prompt"] = Value::String(raw.into());
        let catalog = verify_payload(&serde_json::to_vec(&payload).unwrap(), None).unwrap();
        assert_eq!(a9_catalog_argon(&catalog).get("inputMicrosPerMillionTokens").and_then(Value::as_u64), expected, "{raw}");
    }
}

#[test]
fn a9_catalog_frozen_legacy_v1_hash_remains_valid_without_optional_prices() {
    let stored: StoredCatalog = serde_json::from_str(include_str!("../../../protocol/fixtures/openrouter-registry-legacy-v1.json")).unwrap();
    assert!(valid_catalog(&stored.catalog), "frozen pre-change schema must retain its original typed hash");
    let root = tempfile::tempdir().unwrap();
    fs::write(root.path().join("model-registry-openrouter-current-v1.json"), serde_json::to_vec(&stored).unwrap()).unwrap();
    assert_eq!(load_stable(root.path()).unwrap(), (stored.clone(), false));
    let model = a9_catalog_argon(&stored.catalog);
    assert!(model.get("cachedInputMicrosPerMillionTokens").is_none());
    assert_eq!(model["cachedInputMicrosPerToken"], Value::Null);
}

#[test]
fn a9_catalog_precise_current_tamper_falls_back_to_unchanged_legacy_snapshot() {
    let old: StoredCatalog = serde_json::from_str(include_str!("../../../protocol/fixtures/openrouter-registry-legacy-v1.json")).unwrap();
    let root = tempfile::tempdir().unwrap();
    fs::write(root.path().join("model-registry-openrouter-current-v1.json"), serde_json::to_vec(&old).unwrap()).unwrap();
    let new = verify_payload(&serde_json::to_vec(&a9_catalog_priced_payload()).unwrap(), None).unwrap();
    let stored = persist(root.path(), new, 2).unwrap();
    assert_eq!(a9_catalog_argon(&stored.catalog)["cachedInputMicrosPerMillionTokens"], 100_000);
    let previous = root.path().join("model-registry-openrouter-previous-v1.json");
    assert_eq!(fs::read(&previous).unwrap(), serde_json::to_vec(&old).unwrap());
    let mut corrupt = serde_json::to_value(&stored).unwrap();
    let argon = corrupt["catalog"]["models"].as_array_mut().unwrap().iter_mut().find(|model| model["id"] == "google/gemini-4-argon").unwrap();
    argon["cachedInputMicrosPerMillionTokens"] = serde_json::json!(0);
    fs::write(root.path().join("model-registry-openrouter-current-v1.json"), serde_json::to_vec(&corrupt).unwrap()).unwrap();
    assert_eq!(load_stable(root.path()).unwrap(), (old, true));
}

#[test]
fn a9_catalog_missing_prices_are_not_backfilled_from_current_tariffs() {
    let catalog = verify_payload(&serde_json::to_vec(&a9_catalog_priced_payload()).unwrap(), None).unwrap();
    let legacy = catalog.models.iter().find(|model| model.id == "openai/gpt-5.6-sol").unwrap();
    let model = serde_json::to_value(legacy).unwrap();
    for key in ["inputMicrosPerMillionTokens", "outputMicrosPerMillionTokens", "cachedInputMicrosPerMillionTokens"] {
        assert!(model.get(key).is_none());
    }
    assert_eq!(model["inputMicrosPerToken"], Value::Null);
}

#[test]
fn a9_catalog_cached_aliases_use_one_source_for_both_precision_views() {
    for (preferred, alternate, legacy, precise) in [
        (Value::Null, "0.0000001", None, Some(100_000)),
        (serde_json::json!("0.0000001"), "0.000009", None, Some(100_000)),
        (serde_json::json!("0.000001"), "0.0000001", Some(1), Some(1_000_000)),
        (serde_json::json!("bad"), "0.000001", None, None),
        (serde_json::json!("0.0000000000001"), "0.000001", None, None),
        (serde_json::json!("0"), "0.000001", Some(0), Some(0)),
    ] {
        let mut payload = a9_catalog_priced_payload();
        payload["data"][4]["pricing"]["cache_read"] = preferred.clone();
        payload["data"][4]["pricing"]["input_cache_read"] = Value::String(alternate.into());
        let catalog = verify_payload(&serde_json::to_vec(&payload).unwrap(), None).unwrap();
        let model = a9_catalog_argon(&catalog);
        assert_eq!(model["cachedInputMicrosPerToken"].as_u64(), legacy, "preferred={preferred}");
        assert_eq!(model.get("cachedInputMicrosPerMillionTokens").and_then(Value::as_u64), precise, "preferred={preferred}");
    }
}

#[test]
fn a9_catalog_oversized_normalized_cache_never_replaces_stable_files() {
    let old: StoredCatalog = serde_json::from_str(include_str!("../../../protocol/fixtures/openrouter-registry-legacy-v1.json")).unwrap();
    let root = tempfile::tempdir().unwrap();
    let current = root.path().join("model-registry-openrouter-current-v1.json");
    let before = serde_json::to_vec(&old).unwrap();
    fs::write(&current, &before).unwrap();
    let mut payload = a9_catalog_priced_payload();
    for index in 0..20_000 {
        payload["data"].as_array_mut().unwrap().push(serde_json::json!({
            "id":format!("synthetic/price-{index}"),"name":"synthetic bounded metadata", "pricing":{"prompt":"0.0000001"}
        }));
    }
    let raw = serde_json::to_vec(&payload).unwrap();
    assert!(raw.len() < MAX_RESPONSE_BYTES);
    let catalog = verify_payload(&raw, None).unwrap();
    assert!(serde_json::to_vec(&catalog).unwrap().len() > MAX_RESPONSE_BYTES);
    assert!(persist(root.path(), catalog, 2).is_err());
    assert!(fs::read(&current).unwrap() == before, "oversized normalized output replaced the stable catalog");
    assert!(!root.path().join("model-registry-openrouter-previous-v1.json").exists(), "rejected output must not rotate the stable pair");
    assert_eq!(load_stable(root.path()).unwrap(), (old, false));
}
