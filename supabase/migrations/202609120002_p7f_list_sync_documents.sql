-- P7-F: read-only encrypted document listing for recovery.  No plaintext, table grant,
-- service role or cross-account access is exposed.
begin;

create or replace function public.nanfeng_sync_list_documents(p_app_id text)
returns table(document_id text, revision bigint, payload_hash text, envelope jsonb)
language plpgsql security definer set search_path = public, auth as $$
declare document_count integer;
begin
  if auth.uid() is null or p_app_id <> 'com.nanzhufeng.ai' then
    raise exception 'NFAI_SYNC_UNAUTHORIZED';
  end if;

  select count(*) into document_count
    from public.nfai_sync_documents d
    where d.user_id = auth.uid() and d.app_id = p_app_id;
  if document_count > 10000 then
    raise exception 'NFAI_SYNC_LIST_TOO_LARGE';
  end if;

  return query
    select d.document_id, d.revision, d.payload_hash, d.envelope
      from public.nfai_sync_documents d
      where d.user_id = auth.uid() and d.app_id = p_app_id
      order by d.updated_at desc, d.document_id asc;
end;
$$;

revoke all on function public.nanfeng_sync_list_documents(text) from public, anon;
grant execute on function public.nanfeng_sync_list_documents(text) to authenticated;
commit;
