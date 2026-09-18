-- Run after 202609180007. Synthetic validator and privilege checks; no user rows written.
begin;
do $$
declare e jsonb;
begin
  e := jsonb_build_object('appId','com.nanzhufeng.ai','documentId','test-doc','format','nfai.sync.direct','payloadByteCount',200,'payloadHash',repeat('a',64),'protocolVersion',1,'schemaVersion',1,'revision',1,'payload',jsonb_build_object('appId','com.nanzhufeng.ai','documentId','test-doc','format','nfai.sync.payload','protocolVersion',1,'schemaVersion',1,'revision',1,'records','[]'::jsonb));
  if not public.nanfeng_sync_valid_direct_envelope(e,'com.nanzhufeng.ai','test-doc',1) then raise exception 'VALID_DIRECT_REJECTED'; end if;
  if public.nanfeng_sync_valid_direct_envelope(null,'com.nanzhufeng.ai','test-doc',1)
    or public.nanfeng_sync_valid_direct_envelope('{}','com.nanzhufeng.ai','test-doc',1)
    or public.nanfeng_sync_valid_direct_envelope('42','com.nanzhufeng.ai','test-doc',1)
    or public.nanfeng_sync_valid_direct_envelope(jsonb_set(e,'{payload,documentId}','"different"'),'com.nanzhufeng.ai','test-doc',1)
    then raise exception 'INVALID_ACCEPTED'; end if;
  if has_function_privilege('anon','public.nanfeng_sync_inventory(text)','EXECUTE')
    or has_function_privilege('anon','public.nanfeng_sync_commit_document(text,text,bigint,jsonb)','EXECUTE')
    or has_table_privilege('authenticated','public.nfai_sync_documents','SELECT')
    then raise exception 'ACCESS_EXPANDED'; end if;
  begin
    perform public.nanfeng_sync_inventory('com.nanzhufeng.ai');
    raise exception 'ANONYMOUS_ACCEPTED';
  exception when others then
    if sqlerrm <> 'NFAI_SYNC_UNAUTHORIZED' then raise; end if;
  end;
end $$;
select 'VALIDATOR_AND_ACCOUNT_GATES_PASSED' as result;
rollback;
