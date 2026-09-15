-- Direct Google-account sync cancellation.  Deletes only the caller's one
-- document after an optimistic revision check; no table access is granted.
begin;

create or replace function public.nanfeng_sync_delete_document(
  p_app_id text,
  p_document_id text,
  p_expected_revision bigint
)
returns table(deleted boolean)
language plpgsql security definer set search_path = public, auth as $$
declare current_revision bigint;
begin
  if auth.uid() is null
    or p_app_id <> 'com.nanzhufeng.ai'
    or p_document_id !~ '^[A-Za-z0-9._-]{2,128}$'
    or p_expected_revision < 1 then
    raise exception 'NFAI_SYNC_UNAUTHORIZED';
  end if;

  perform pg_advisory_xact_lock(hashtextextended(auth.uid()::text || ':' || p_app_id || ':' || p_document_id, 0));
  select d.revision into current_revision
    from public.nfai_sync_documents d
    where d.user_id = auth.uid() and d.app_id = p_app_id and d.document_id = p_document_id
    for update;
  if current_revision is null then
    return query select false;
    return;
  end if;
  if current_revision <> p_expected_revision then
    raise exception 'NFAI_SYNC_STALE_REVISION';
  end if;
  delete from public.nfai_sync_documents d
    where d.user_id = auth.uid() and d.app_id = p_app_id and d.document_id = p_document_id;
  return query select true;
end;
$$;

revoke all on function public.nanfeng_sync_delete_document(text, text, bigint) from public, anon;
grant execute on function public.nanfeng_sync_delete_document(text, text, bigint) to authenticated;
commit;
