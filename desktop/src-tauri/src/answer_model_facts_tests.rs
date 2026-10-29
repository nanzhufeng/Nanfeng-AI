// Shared synthetic cases exercise the existing sync/SQLite owners, not real accounts.
fn a9_answer_facts_fixture() -> Value {
    serde_json::from_str(include_str!("../../../protocol/fixtures/answer-model-facts-v1.json")).unwrap()
}

fn a9_answer_message(facts: &Value, remote: bool) -> Value {
    let mut message = json!({"id":"answer","parentId":"root","ordinal":1,
        "role":"assistant","delivery":"COMPLETE","createdAt":"2026-09-01T00:01:00Z",
        "revision":1,"blocks":[{"kind":"TEXT","text":if remote {"remote body"} else {"local body"}}]});
    message.as_object_mut().unwrap().extend(facts.as_object().unwrap().clone());
    message
}

fn a9_answer_fact_projection(message: &Value) -> Value {
    let fixture = a9_answer_facts_fixture();
    let mut facts = serde_json::Map::new();
    for key in fixture["factKeys"].as_array().unwrap() {
        let key = key.as_str().unwrap();
        if let Some(value) = message.get(key) { facts.insert(key.into(), value.clone()); }
    }
    Value::Object(facts)
}

fn a9_answer_expected_portable(case: &Value) -> Value {
    let v2: Value = serde_json::from_str(include_str!("../../../protocol/fixtures/answer-model-identity-v2.json")).unwrap();
    v2["legacyProjectionUpgrades"].get(case["name"].as_str().unwrap()).cloned().unwrap_or_else(||case["expectedPortable"].clone())
}

#[test]
fn a9_answer_fact_preservation_is_atomic() {
    for case in a9_answer_facts_fixture()["cases"].as_array().unwrap() {
        let local = a9_answer_message(&case["local"], false);
        let mut updated = a9_answer_message(&case["remote"], true);
        let body = updated["blocks"].clone();
        preserve_local_model_usage(&local, &mut updated).unwrap();
        assert_eq!(a9_answer_fact_projection(&updated), case["expectedFacts"], "{}", case["name"]);
        assert_eq!(updated["blocks"], body, "answer facts must not replace conversation content");
    }
}

#[test]
fn a9_answer_unpriced_facts_survive_both_file_database_merge_paths_and_reopening() {
    let root = std::env::var_os("NFAI_PORTABLE_IDENTITY_TEST_ROOT").map(std::path::PathBuf::from)
        .unwrap_or_else(|| std::env::temp_dir().join(format!("nanfeng-unpriced-facts-{}-{}", std::process::id(),
            std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_nanos())))
        .join("unpriced-facts");
    std::fs::create_dir_all(&root).unwrap();
    let mut proofs = Vec::new();
    for (index, case) in a9_answer_facts_fixture()["cases"].as_array().unwrap().iter().enumerate()
        .filter(|(_, case)| case["unpricedMetadata"] == true)
    {
        for cloud_read in [true, false] {
            let path = root.join(format!("{index}-{cloud_read}.sqlite3"));
            assert!(!path.exists());
            let initial = a9_answer_database(&case["local"]);
            initial.execute("VACUUM INTO ?1", [path.to_str().unwrap()]).unwrap();
            drop(initial);
            let mut connection = Connection::open(&path).unwrap();
            let remote = json!({"title":"facts","revision":2,"updatedAt":"2026-09-01T00:02:00Z",
                "currentLeafId":"answer","messages":[a9_answer_root(),a9_answer_message(&case["remote"],true)]});
            let tx = connection.transaction().unwrap();
            if cloud_read { merge_newer_remote_conversation(&tx,"a9-workspace","a9-conversation",remote.as_object().unwrap(),2).unwrap(); }
            else { merge_remote_additions_for_local_commit(&tx,"a9-workspace","a9-conversation",remote.as_object().unwrap(),2).unwrap(); }
            tx.commit().unwrap();
            drop(connection);
            let cold = Connection::open(&path).unwrap();
            let text: String = cold.query_row("SELECT exchange_json FROM workspace_exchange WHERE workspace_id='a9-workspace'", [], |row| row.get(0)).unwrap();
            let stored: Value = serde_json::from_str(&text).unwrap();
            assert_eq!(a9_answer_fact_projection(&stored["conversations"][0]["messages"][1]), case["expectedFacts"]);
            let (packed, _) = conversation_payload(&cold,"a9-workspace","a9-conversation","a9-document",3).unwrap();
            assert_eq!(packed["records"][0]["content"]["messages"][1]["modelUsage"], case["expectedPortable"]);
            assert!(crate::sync_v1::seal_direct(packed).is_ok());
            drop(cold);
            proofs.push(json!({"case":case["name"],"cloudRead":cloud_read,"path":path,"pass":true}));
        }
    }
    assert_eq!(proofs.len(), 12);
    std::fs::write(root.join("receipt.json"), serde_json::to_vec_pretty(&json!({"pass":true,"cases":proofs})).unwrap()).unwrap();
}

#[test]
fn a9_answer_unknown_estimate_price_version_stays_null() {
    let fixture = a9_answer_facts_fixture();
    let case = &fixture["cases"][0];
    assert_eq!(portable_model_usage(&a9_answer_message(&case["local"], false)).unwrap(), case["expectedPortable"]);
}

fn a9_answer_database(local: &Value) -> Connection {
    let connection = database();
    connection.execute("INSERT INTO workspaces(id,title,semantic_hash,package_hash,created_at) VALUES('a9-workspace','synthetic','before','package','2026-09-01T00:00:00Z')", []).unwrap();
    let value = json!({"conversations":[{"id":"a9-conversation","title":"facts","revision":1,
        "createdAt":"2026-09-01T00:00:00Z","updatedAt":"2026-09-01T00:01:00Z","currentLeafId":"answer",
        "messages":[a9_answer_root(), a9_answer_message(local, false)]}]});
    connection.execute("INSERT INTO workspace_exchange VALUES('a9-workspace',?1)", [value.to_string()]).unwrap();
    connection
}

fn a9_answer_root() -> Value {
    json!({"id":"root","parentId":null,"ordinal":0,"role":"user","delivery":"COMPLETE",
        "createdAt":"2026-09-01T00:00:00Z","revision":1,"blocks":[{"kind":"TEXT","text":"synthetic question"}]})
}

#[test]
fn a9_answer_both_merge_paths_persist_facts_and_repack() {
    for case in a9_answer_facts_fixture()["cases"].as_array().unwrap() {
        for cloud_read in [true, false] {
            let mut connection = a9_answer_database(&case["local"]);
            let remote = json!({"title":"facts","revision":2,"updatedAt":"2026-09-01T00:02:00Z",
                "currentLeafId":"answer","messages":[a9_answer_root(),a9_answer_message(&case["remote"],true)]});
            let tx = connection.transaction().unwrap();
            if cloud_read { merge_newer_remote_conversation(&tx,"a9-workspace","a9-conversation",remote.as_object().unwrap(),2).unwrap(); }
            else { merge_remote_additions_for_local_commit(&tx,"a9-workspace","a9-conversation",remote.as_object().unwrap(),2).unwrap(); }
            tx.commit().unwrap();
            let stored: String = connection.query_row("SELECT exchange_json FROM workspace_exchange WHERE workspace_id='a9-workspace'", [], |row| row.get(0)).unwrap();
            let stored: Value = serde_json::from_str(&stored).unwrap();
            let message = &stored["conversations"][0]["messages"][1];
            assert_eq!(a9_answer_fact_projection(message), case["expectedFacts"], "{} cloud_read={cloud_read}",case["name"]);
            let (payload, _) = conversation_payload(&connection,"a9-workspace","a9-conversation","a9-document",3).unwrap();
            let portable = &payload["records"][0]["content"]["messages"][1]["modelUsage"];
            assert_eq!(portable, &a9_answer_expected_portable(case));
            let envelope = crate::sync_v1::seal_direct(payload.clone()).unwrap();
            let opened = crate::sync_v1::open_direct(&envelope,APP_ID,"a9-document",3).unwrap();
            assert_eq!(opened,payload);
            let mut restored = json!({});
            restore_portable_model_usage(&mut restored,portable.as_object().unwrap());
            assert_eq!(portable_model_usage(&restored).unwrap(),a9_answer_expected_portable(case));
        }
    }
}

#[test]
fn a9_answer_wire_rejects_nonportable_accounting_fields() {
    let fixture = a9_answer_facts_fixture();
    let connection = a9_answer_database(&fixture["cases"][2]["remote"]);
    let (payload, _) = conversation_payload(&connection,"a9-workspace","a9-conversation","a9-document",3).unwrap();
    assert!(crate::sync_v1::seal_direct(payload.clone()).is_ok());
    for name in fixture["forbiddenPortableFields"].as_array().unwrap() {
        let mut bad = payload.clone();
        bad["records"][0]["content"]["messages"][1]["modelUsage"][name.as_str().unwrap()] = json!(1);
        assert!(crate::sync_v1::seal_direct(bad).is_err(),"{}",name);
    }
}

#[test]
fn a9_answer_versioned_returned_identity_survives_strict_direct_roundtrip() {
    let fixture: Value = serde_json::from_str(include_str!("../../../protocol/fixtures/answer-model-identity-v2.json")).unwrap();
    let connection = a9_answer_database(&a9_answer_facts_fixture()["cases"][2]["remote"]);
    let (base, _) = conversation_payload(&connection,"a9-workspace","a9-conversation","a9-document",3).unwrap();
    for case in fixture["valid"].as_array().unwrap() {
        let mut payload=base.clone();
        payload["records"][0]["content"]["messages"][1]["modelUsage"]=case["wire"].clone();
        let envelope=crate::sync_v1::seal_direct(payload.clone()).expect("supported versioned model facts must seal");
        assert_eq!(crate::sync_v1::open_direct(&envelope,APP_ID,"a9-document",3).unwrap(),payload);
        let mut content=payload["records"][0]["content"].clone();
        normalize_android_selected_conversation(content.as_object_mut().unwrap()).unwrap();
        let restored=&content["messages"][1];
        assert_eq!(restored["actualModelId"],case["actualModelId"],"{}",case["name"]);
        assert_eq!(portable_model_usage(restored).unwrap(),case["wire"],"{}",case["name"]);
    }
    for case in fixture["invalid"].as_array().unwrap() {
        let mut payload=base.clone();payload["records"][0]["content"]["messages"][1]["modelUsage"]=case["wire"].clone();
        assert!(crate::sync_v1::seal_direct(payload).is_err(),"{}",case["name"]);
    }
}

#[test]
fn a9_answer_versioned_identity_both_sqlite_merge_paths_survive_close_reopen() {
    let fixture: Value=serde_json::from_str(include_str!("../../../protocol/fixtures/answer-model-identity-v2.json")).unwrap();
    let root=std::env::var_os("NFAI_PORTABLE_IDENTITY_TEST_ROOT").map(std::path::PathBuf::from)
        .unwrap_or_else(||std::env::temp_dir().join(format!("nanfeng-identity-v2-{}-{}",std::process::id(),std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_nanos())));
    std::fs::create_dir_all(&root).unwrap();
    let mut proofs=Vec::new();
    for case in fixture["valid"].as_array().unwrap() { for cloud_read in [true,false] {
        let mut local=json!({"modelSnapshot":{"modelId":case["wire"]["modelId"],"displayName":case["wire"]["modelDisplayName"]},"costSource":null,"chargeMicros":null});
        local["usage"]=json!({});
        let mut connection=a9_answer_database(&local);
        let (mut payload,_)=conversation_payload(&connection,"a9-workspace","a9-conversation","a9-document",2).unwrap();
        payload["records"][0]["content"]["messages"][1]["modelUsage"]=case["wire"].clone();
        let direct=crate::sync_v1::seal_direct(payload).unwrap();
        let opened=crate::sync_v1::open_direct(&direct,APP_ID,"a9-document",2).unwrap();
        let mut remote=opened["records"][0]["content"].clone();
        normalize_android_selected_conversation(remote.as_object_mut().unwrap()).unwrap();
        remote["revision"]=json!(2);remote["updatedAt"]=json!("2026-09-01T00:02:00Z");
        let tx=connection.transaction().unwrap();
        if cloud_read {merge_newer_remote_conversation(&tx,"a9-workspace","a9-conversation",remote.as_object().unwrap(),2).unwrap();}
        else {merge_remote_additions_for_local_commit(&tx,"a9-workspace","a9-conversation",remote.as_object().unwrap(),2).unwrap();}
        tx.commit().unwrap();
        let path=root.join(format!("{}-{}.sqlite3",case["name"].as_str().unwrap(),if cloud_read {"read"} else {"upload"}));
        assert!(!path.exists());connection.execute("VACUUM INTO ?1",[path.to_str().unwrap()]).unwrap();drop(connection);
        let cold=Connection::open(&path).unwrap();
        let text:String=cold.query_row("SELECT exchange_json FROM workspace_exchange WHERE workspace_id='a9-workspace'",[],|row|row.get(0)).unwrap();
        let stored:Value=serde_json::from_str(&text).unwrap();let message=&stored["conversations"][0]["messages"][1];
        assert_eq!(message["actualModelId"],case["actualModelId"]);
        assert_eq!(portable_model_usage(message).unwrap(),case["wire"]);
        let (packed,_)=conversation_payload(&cold,"a9-workspace","a9-conversation","a9-document",3).unwrap();
        assert_eq!(packed["records"][0]["content"]["messages"][1]["modelUsage"],case["wire"]);
        assert!(crate::sync_v1::seal_direct(packed).is_ok());drop(cold);
        proofs.push(json!({"case":case["name"],"cloudRead":cloud_read,"path":path,"pass":true}));
    }}
    std::fs::write(root.join("receipt.json"),serde_json::to_vec_pretty(&json!({"pass":true,"cases":proofs})).unwrap()).unwrap();
}

#[test]
fn a9_answer_settlement_keeps_matching_local_returned_identity_in_both_merge_paths() {
    for cloud_read in [true, false] {
        for (remote_model, returned, expected) in [
            ("requested-model", None, Some("provider-returned-model")),
            ("different-requested-model", None, None),
            ("requested-model", Some("remote-returned-model"), Some("remote-returned-model")),
        ] {
            let local = json!({"modelSnapshot":{"modelId":"requested-model","displayName":"Original model"},
                "actualModelId":"provider-returned-model","usage":{"inputTokens":50,"outputTokens":10},
                "chargeMicros":12,"currencyCode":"CNY","costPriceVersion":"estimate-v1","costSource":"LOCAL_ESTIMATE"});
            let mut remote_facts = json!({"modelSnapshot":{"modelId":remote_model,"displayName":"Reported model"},
                "usage":{"inputTokens":73,"outputTokens":19},"chargeMicros":630,
                "currencyCode":"USD","costPriceVersion":"provider-v1","costSource":"PROVIDER_RESPONSE"});
            if let Some(returned) = returned { remote_facts["actualModelId"] = json!(returned); }
            let mut connection = a9_answer_database(&local);
            let remote = json!({"title":"facts","revision":2,"updatedAt":"2026-09-01T00:02:00Z",
                "currentLeafId":"answer","messages":[a9_answer_root(),a9_answer_message(&remote_facts,true)]});
            let tx = connection.transaction().unwrap();
            if cloud_read { merge_newer_remote_conversation(&tx,"a9-workspace","a9-conversation",remote.as_object().unwrap(),2).unwrap(); }
            else { merge_remote_additions_for_local_commit(&tx,"a9-workspace","a9-conversation",remote.as_object().unwrap(),2).unwrap(); }
            tx.commit().unwrap();
            let stored: String = connection.query_row("SELECT exchange_json FROM workspace_exchange WHERE workspace_id='a9-workspace'", [], |row| row.get(0)).unwrap();
            let stored: Value = serde_json::from_str(&stored).unwrap();
            let message = &stored["conversations"][0]["messages"][1];
            assert_eq!(message["actualModelId"].as_str(), expected, "cloud_read={cloud_read} remote={remote_model}");
            assert_eq!(message["chargeMicros"],630);
            assert_eq!(message["currencyCode"],"USD");
            assert_eq!(message["costSource"],"PROVIDER_RESPONSE");
            assert_eq!(message["usage"],remote_facts["usage"],"settlement counters remain one remote bundle");
            assert_eq!(message["blocks"][0]["text"],"remote body");
            let (payload,_) = conversation_payload(&connection,"a9-workspace","a9-conversation","a9-document",3).unwrap();
            let usage=&payload["records"][0]["content"]["messages"][1]["modelUsage"];
            assert_eq!(usage["modelId"],remote_model);
            assert_eq!(usage["costTotalMicros"],630);
            if let Some(expected) = expected {
                assert_eq!(usage.as_object().unwrap().len(),13);
                assert_eq!(usage["factsVersion"],2);assert_eq!(usage["actualModelId"],expected);
            } else {
                assert_eq!(usage.as_object().unwrap().len(),11);
                assert!(usage.get("actualModelId").is_none());
            }
            assert!(crate::sync_v1::seal_direct(payload).is_ok());
        }
    }
}
