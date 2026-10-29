use super::*;
fn setup() -> (tempfile::TempDir, Mutex<DesktopWorkspaceStore>, desktop_model_service_v1::AppPrivateProviderCredentialStore, String, String) {
    let (dir,store,imported)=tests::imported_store();
    for (provider,preset) in [("QWEN","QWEN_3_6_FLASH"),("DEEPSEEK","DEEPSEEK_V4_FLASH")] {
        store.save_desktop_model_service_setting_record(provider,true,preset,0).unwrap();
        store.credentials.save_user_provided_secret(provider,b"synthetic-title-only").unwrap();
    }
    let opening=store.prepare_ordinary_chat(DesktopOrdinaryChatSubmitArgs {
        workspace_id:imported.summary.id.clone(),conversation_id:None,project_id:None,expected_revision:None,
        text:"请整理KFK资料".into(),attachment_ids:vec![],model_id:None,tone_override:None,web_search_override:Some(false),egress_authorization:None,
    },Some("http://127.0.0.1:1/chat/completions")).unwrap();
    store.update_ordinary_chat_message(&opening.projection.attempt_id,"KFK资料的公开整理。","COMPLETE",None,None,None).unwrap();
    let credentials=desktop_model_service_v1::AppPrivateProviderCredentialStore::at(&store.root);
    (dir,Mutex::new(store),credentials,imported.summary.id,opening.projection.conversation_id)
}
fn reply(text:&str)->desktop_ordinary_chat_v1::Completed {
    desktop_ordinary_chat_v1::Completed {text:text.into(),reasoning:None,usage:desktop_ordinary_chat_v1::Usage {input_tokens:Some(12),output_tokens:Some(4),cached_input_tokens:None,reasoning_tokens:None},reported_cost_micros:None,actual_model_id:Some("qwen3.6-flash".into()),web_search_verified:false,elapsed_ms:3,native_sources:vec![]}
}
#[test]
fn title_background_unknown_and_cancellation_never_send_next_provider() {
    for cancelled in [false,true] {
        let (_dir,store,credentials,ws,conv)=setup();let mut calls=0;
        run_desktop_conversation_title_work(&store,&credentials,&ws,&conv,Some("http://127.0.0.1:1"),true,|request,_| {
            calls+=1;assert_eq!(request.model_id,"qwen3.6-flash");assert_eq!(request.max_output_tokens,256);assert_eq!(request.web_search_route,"NONE");
            Err(if cancelled {desktop_ordinary_chat_v1::Failure::Cancelled}else{desktop_ordinary_chat_v1::Failure::Unknown{code:"TIMEOUT"}})
        },||panic!("failed title cannot emit applied metadata"));
        assert_eq!(calls,1);let connection=store.lock().unwrap().connection().unwrap();let states:Vec<String>=connection.prepare("SELECT state FROM desktop_conversation_title_attempts").unwrap().query_map([],|r|r.get(0)).unwrap().map(Result::unwrap).collect();assert_eq!(states,vec![if cancelled{"CANCELLED"}else{"UNKNOWN"}]);if !cancelled {run_desktop_conversation_title_work(&store,&credentials,&ws,&conv,Some("http://127.0.0.1:1"),true,|_,_|panic!("a later optional job must not resend an UNKNOWN title"),||{});}
    }
}
#[test]
fn title_background_response_receipt_failure_prevents_external_fallback() {
    let (_dir,store,credentials,ws,conv)=setup();let mut calls=0;
    run_desktop_conversation_title_work(&store,&credentials,&ws,&conv,Some("http://127.0.0.1:1"),true,|_,_| {
        calls+=1;store.lock().unwrap().connection().unwrap().execute_batch("CREATE TRIGGER title_receipt_refused BEFORE UPDATE ON desktop_conversation_title_attempts BEGIN SELECT RAISE(ABORT,'synthetic refusal'); END;").unwrap();Ok(reply("not a title"))
    },||panic!("invalid title cannot emit applied metadata"));assert_eq!(calls,1);
}
#[test]
fn title_background_success_uses_own_policy_and_preserves_activity() {
    let (_dir,store,credentials,ws,conv)=setup();let before=store.lock().unwrap().workspace_projection(&ws).unwrap().exchange["conversations"].as_array().unwrap().iter().find(|c|c["id"]==conv).unwrap().clone();let mut calls=0;let mut applied=0;
    run_desktop_conversation_title_work(&store,&credentials,&ws,&conv,Some("http://127.0.0.1:1"),true,|request,_| {calls+=1;assert_eq!(request.max_output_tokens,256);Ok(reply("{\"title\":\"KFK资料整理方案\"}"))},||applied+=1);
    assert_eq!((calls,applied),(1,1));let after=store.lock().unwrap().workspace_projection(&ws).unwrap().exchange["conversations"].as_array().unwrap().iter().find(|c|c["id"]==conv).unwrap().clone();assert_eq!(after["updatedAt"],before["updatedAt"]);assert_eq!(after["title"],"KFK资料整理方案");assert_eq!(desktop_conversation_title_v1::REQUEST_TIMEOUT_SECS,15);
}

#[test]
fn title_background_new_reported_currency_is_cny_but_legacy_currency_is_preserved() {
    for legacy in [false,true] {
        let (_dir,store,credentials,ws,conv)=setup();
        run_desktop_conversation_title_work(&store,&credentials,&ws,&conv,Some("http://127.0.0.1:1"),true,|_,_| {
            if legacy {store.lock().unwrap().connection().unwrap().execute("UPDATE desktop_conversation_title_attempts SET currency_code='USD'",[]).unwrap();}
            let mut result=reply("{\"title\":\"KFK资料整理方案\"}");result.reported_cost_micros=Some(23);Ok(result)
        },||{});
        let owner=store.lock().unwrap();let currency:String=owner.connection().unwrap().query_row("SELECT currency_code FROM desktop_conversation_title_attempts",[],|r|r.get(0)).unwrap();assert_eq!(currency,if legacy{"USD"}else{"CNY"});
        owner.recover_desktop_conversation_title_accounting().unwrap();let after:String=owner.connection().unwrap().query_row("SELECT currency_code FROM desktop_conversation_title_attempts",[],|r|r.get(0)).unwrap();assert_eq!(after,currency);let attempt:String=owner.connection().unwrap().query_row("SELECT attempt_id FROM desktop_conversation_title_attempts",[],|r|r.get(0)).unwrap();let ledger=usage_ledger_v1::Ledger::open(&owner.root.join("usage-ledger")).unwrap().read_model_for_execution(&attempt).unwrap();assert_eq!(ledger.entries.len(),1);assert_eq!(ledger.entries[0].currency_code.as_deref(),Some(currency.as_str()));assert_eq!(ledger.entries[0].charge_micros,Some(23));
    }
}

#[test]
fn title_background_measured_failure_accounting_refusal_never_sends_next_provider() {
    let (dir,store,credentials,ws,conv)=setup();let mut calls=0;
    run_desktop_conversation_title_work(&store,&credentials,&ws,&conv,Some("http://127.0.0.1:1"),true,|_,_| {
        calls+=1;assert_eq!(calls,1,"local accounting failure must not send another provider request");
        let root=store.lock().unwrap().root.clone();assert!(root.starts_with(dir.path()));let ledger=root.join("usage-ledger");if ledger.is_dir(){fs::rename(&ledger,root.join("synthetic-held-ledger")).unwrap();}fs::write(&ledger,b"synthetic accounting refusal").unwrap();
        let sample=reply("not an accepted body");Err(desktop_ordinary_chat_v1::Failure::MeasuredExplicit {code:"RESPONSE_FORMAT",http_status:None,measurement:desktop_ordinary_chat_v1::ReportedMeasurement{usage:sample.usage,reported_cost_micros:Some(23),actual_model_id:sample.actual_model_id,elapsed_ms:3}})
    },||panic!("failed title cannot apply"));assert_eq!(calls,1);
    run_desktop_conversation_title_work(&store,&credentials,&ws,&conv,Some("http://127.0.0.1:1"),true,|_,_|panic!("pending local receipt must block a later optional owner"),||{});
}

#[test]
fn title_background_measured_cancelled_recovers_without_fallback_or_state_promotion() {
    let (_dir,store,credentials,ws,conv)=setup(); let mut calls=0;
    let root=store.lock().unwrap().root.clone();
    let sidecar=root.join("usage-ledger"); let parked=root.join("synthetic-title-ledger-parked");
    run_desktop_conversation_title_work(&store,&credentials,&ws,&conv,Some("http://127.0.0.1:1"),true,|_,_| {
        calls+=1;
        usage_ledger_v1::Ledger::open(&sidecar).unwrap();
        fs::rename(&sidecar,&parked).unwrap(); fs::write(&sidecar,b"synthetic unavailable ledger").unwrap();
        Err(desktop_ordinary_chat_v1::Failure::MeasuredCancelled { measurement: desktop_ordinary_chat_v1::ReportedMeasurement {
            usage: desktop_ordinary_chat_v1::Usage {input_tokens:Some(6),cached_input_tokens:Some(1),..Default::default()},
            reported_cost_micros:None,actual_model_id:Some("fixture-actual".into()),elapsed_ms:7,
        }})
    },||panic!("cancelled title must not apply"));
    assert_eq!(calls,1);
    let attempt:String=store.lock().unwrap().connection().unwrap().query_row("SELECT attempt_id FROM desktop_conversation_title_attempts",[],|r|r.get(0)).unwrap();
    drop(store); fs::remove_file(&sidecar).unwrap(); fs::rename(&parked,&sidecar).unwrap();
    let reopened=DesktopWorkspaceStore::open(root).unwrap();
    reopened.recover_desktop_conversation_title_accounting().unwrap();
    let state:String=reopened.connection().unwrap().query_row("SELECT state FROM desktop_conversation_title_attempts WHERE attempt_id=?1",[&attempt],|r|r.get(0)).unwrap();
    assert_eq!(state,"CANCELLED");
    let rows=usage_ledger_v1::Ledger::open(&sidecar).unwrap().entries_for_execution(&attempt).unwrap();
    assert_eq!(rows.len(),1); assert_eq!((rows[0].input_tokens,rows[0].cached_input_tokens,rows[0].output_tokens,rows[0].charge_micros),(Some(6),Some(1),None,None));
}

#[test]
fn title_background_measured_unknown_recovers_without_fallback_or_state_promotion() {
    let (_dir,store,credentials,ws,conv)=setup(); let mut calls=0;
    let root=store.lock().unwrap().root.clone();
    let sidecar=root.join("usage-ledger"); let parked=root.join("synthetic-title-ledger-parked");
    run_desktop_conversation_title_work(&store,&credentials,&ws,&conv,Some("http://127.0.0.1:1"),true,|_,_| {
        calls+=1;
        usage_ledger_v1::Ledger::open(&sidecar).unwrap();
        fs::rename(&sidecar,&parked).unwrap(); fs::write(&sidecar,b"synthetic unavailable ledger").unwrap();
        Err(desktop_ordinary_chat_v1::Failure::MeasuredUnknown { code:"TIMEOUT",measurement: desktop_ordinary_chat_v1::ReportedMeasurement {
            usage: desktop_ordinary_chat_v1::Usage {input_tokens:Some(6),cached_input_tokens:Some(1),..Default::default()},
            reported_cost_micros:None,actual_model_id:Some("fixture-actual".into()),elapsed_ms:7,
        }})
    },||panic!("unknown title must not apply"));
    assert_eq!(calls,1);
    let attempt:String=store.lock().unwrap().connection().unwrap().query_row("SELECT attempt_id FROM desktop_conversation_title_attempts",[],|r|r.get(0)).unwrap();
    drop(store); fs::remove_file(&sidecar).unwrap(); fs::rename(&parked,&sidecar).unwrap();
    let reopened=DesktopWorkspaceStore::open(root).unwrap();
    reopened.recover_desktop_conversation_title_accounting().unwrap();
    let state:String=reopened.connection().unwrap().query_row("SELECT state FROM desktop_conversation_title_attempts WHERE attempt_id=?1",[&attempt],|r|r.get(0)).unwrap();
    assert_eq!(state,"UNKNOWN");
    let rows=usage_ledger_v1::Ledger::open(&sidecar).unwrap().entries_for_execution(&attempt).unwrap();
    assert_eq!(rows.len(),1); assert_eq!((rows[0].input_tokens,rows[0].cached_input_tokens,rows[0].output_tokens,rows[0].charge_micros),(Some(6),Some(1),None,None));
}
