-- P8 follow-up: direct envelopes placed conversation plaintext in the cloud.
-- Retire that write format without deleting existing rows: clients identify a
-- retired record and explicitly replace it from a verified local source.
begin;

create or replace function public.nanfeng_sync_valid_envelope(p_envelope jsonb, p_app_id text, p_document_id text, p_revision bigint)
returns boolean language sql immutable set search_path = '' as $$
  select jsonb_typeof(p_envelope) = 'object'
    and (select array_agg(k order by k) from jsonb_object_keys(p_envelope) k)
        = array['appId','documentId','format','kdf','payload','payloadByteCount','payloadHash','protocolVersion','revision','schemaVersion','wrappedDataKey']
    and p_envelope->>'format' = 'nfai.sync.envelope'
    and (p_envelope->>'protocolVersion')::integer = 1
    and (p_envelope->>'schemaVersion')::integer = 1
    and p_envelope->>'appId' = p_app_id
    and p_envelope->>'documentId' = p_document_id
    and (p_envelope->>'revision')::bigint = p_revision
    and p_envelope->>'payloadHash' ~ '^[a-f0-9]{64}$'
    and (p_envelope->>'payloadByteCount')::integer between 1 and 1048576
    and public.nanfeng_sync_valid_key_metadata(jsonb_build_object('kdf', p_envelope->'kdf', 'wrappedDataKey', p_envelope->'wrappedDataKey'))
    and p_envelope #>> '{payload,algorithm}' = 'AES-256-GCM'
    and (select array_agg(k order by k) from jsonb_object_keys(p_envelope->'payload') k)
        = array['algorithm','ciphertext','nonce']
    and p_envelope #>> '{payload,nonce}' ~ '^[A-Za-z0-9_-]{16}$'
    and p_envelope #>> '{payload,ciphertext}' ~ '^[A-Za-z0-9_-]+$'
    and length(p_envelope #>> '{payload,ciphertext}') between 1 and 1398123;
$$;

create or replace function public.nanfeng_sync_commit_document(p_app_id text, p_document_id text, p_expected_revision bigint, p_envelope jsonb)
returns table(revision bigint, payload_hash text)
language plpgsql security definer set search_path = public, auth as $$
declare current_revision bigint; next_revision bigint; envelope_bytes integer;
begin
  if auth.uid() is null or p_app_id <> 'com.nanzhufeng.ai' or p_document_id !~ '^[A-Za-z0-9._-]{2,128}$' or p_expected_revision < 0 then raise exception 'NFAI_SYNC_UNAUTHORIZED'; end if;
  perform pg_advisory_xact_lock(hashtextextended(auth.uid()::text || ':' || p_app_id || ':' || p_document_id, 0));
  select d.revision into current_revision from public.nfai_sync_documents d where d.user_id = auth.uid() and d.app_id = p_app_id and d.document_id = p_document_id for update;
  if coalesce(current_revision, 0) <> p_expected_revision then raise exception 'NFAI_SYNC_STALE_REVISION'; end if;
  next_revision := p_expected_revision + 1; envelope_bytes := octet_length(p_envelope::text);
  if envelope_bytes not between 1 and 2097152 or not public.nanfeng_sync_valid_envelope(p_envelope, p_app_id, p_document_id, next_revision) then raise exception 'NFAI_SYNC_INVALID_ENVELOPE'; end if;
  insert into public.nfai_sync_documents(user_id, app_id, document_id, revision, protocol_version, schema_version, payload_hash, payload_byte_count, envelope_byte_count, envelope)
    values(auth.uid(), p_app_id, p_document_id, next_revision, 1, 1, p_envelope->>'payloadHash', (p_envelope->>'payloadByteCount')::integer, envelope_bytes, p_envelope)
  on conflict (user_id, app_id, document_id) do update set revision = excluded.revision, payload_hash = excluded.payload_hash,
    payload_byte_count = excluded.payload_byte_count, envelope_byte_count = excluded.envelope_byte_count, envelope = excluded.envelope, updated_at = now();
  return query select next_revision, p_envelope->>'payloadHash';
end;
$$;

drop function if exists public.nanfeng_sync_valid_direct_envelope(jsonb, text, text, bigint);
revoke all on function public.nanfeng_sync_commit_document(text,text,bigint,jsonb) from public, anon;
grant execute on function public.nanfeng_sync_commit_document(text,text,bigint,jsonb) to authenticated;
notify pgrst, 'reload schema';
commit;
