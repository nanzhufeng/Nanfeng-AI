-- User-approved account-isolated sync: HTTPS + authenticated per-user RPCs.
-- No rows are deleted. Old ciphertext remains readable for source-device migration.
begin;

create or replace function public.nanfeng_sync_valid_direct_envelope(p_envelope jsonb, p_app_id text, p_document_id text, p_revision bigint)
returns boolean language plpgsql immutable set search_path = '' as $$
begin
  return coalesce( jsonb_typeof(p_envelope) = 'object'
    and (select array_agg(k order by k) from jsonb_object_keys(p_envelope) k)
        = array['appId','documentId','format','payload','payloadByteCount','payloadHash','protocolVersion','revision','schemaVersion']
    and p_envelope->>'format' = 'nfai.sync.direct'
    and (p_envelope->>'protocolVersion')::integer = 1
    and (p_envelope->>'schemaVersion')::integer = 1
    and p_envelope->>'appId' = p_app_id
    and p_envelope->>'documentId' = p_document_id
    and (p_envelope->>'revision')::bigint = p_revision
    and p_envelope->>'payloadHash' ~ '^[a-f0-9]{64}$'
    and (p_envelope->>'payloadByteCount')::integer between 1 and 1048576
    and jsonb_typeof(p_envelope->'payload') = 'object'
    and p_envelope #>> '{payload,format}' = 'nfai.sync.payload'
    and p_envelope #>> '{payload,appId}' = p_app_id
    and p_envelope #>> '{payload,documentId}' = p_document_id
    and (p_envelope #>> '{payload,revision}')::bigint = p_revision
    and (p_envelope #>> '{payload,protocolVersion}')::integer = 1
    and (p_envelope #>> '{payload,schemaVersion}')::integer = 1
    and jsonb_typeof(p_envelope #> '{payload,records}') = 'array'
    and jsonb_array_length(p_envelope #> '{payload,records}') <= 10000
    , false);
exception when invalid_text_representation or numeric_value_out_of_range or invalid_parameter_value then
  return false;
end;
$$;

create or replace function public.nanfeng_sync_commit_document(p_app_id text, p_document_id text, p_expected_revision bigint, p_envelope jsonb)
returns table(revision bigint, payload_hash text)
language plpgsql security definer set search_path = public, auth as $$
declare current_revision bigint; next_revision bigint; envelope_bytes integer;
begin
  if auth.uid() is null or p_app_id is distinct from 'com.nanzhufeng.ai' or p_document_id is null or p_document_id !~ '^[A-Za-z0-9._-]{2,128}$' or p_expected_revision is null or p_expected_revision < 0 then raise exception 'NFAI_SYNC_UNAUTHORIZED'; end if;
  perform pg_advisory_xact_lock(hashtextextended(auth.uid()::text || ':' || p_app_id || ':' || p_document_id, 0));
  select d.revision into current_revision from public.nfai_sync_documents d where d.user_id = auth.uid() and d.app_id = p_app_id and d.document_id = p_document_id for update;
  if coalesce(current_revision, 0) <> p_expected_revision then raise exception 'NFAI_SYNC_STALE_REVISION'; end if;
  next_revision := p_expected_revision + 1; envelope_bytes := octet_length(p_envelope::text);
  if p_envelope is null or envelope_bytes not between 1 and 2097152 or not public.nanfeng_sync_valid_direct_envelope(p_envelope, p_app_id, p_document_id, next_revision) then raise exception 'NFAI_SYNC_INVALID_ENVELOPE'; end if;
  insert into public.nfai_sync_documents(user_id, app_id, document_id, revision, protocol_version, schema_version, payload_hash, payload_byte_count, envelope_byte_count, envelope)
    values(auth.uid(), p_app_id, p_document_id, next_revision, 1, 1, p_envelope->>'payloadHash', (p_envelope->>'payloadByteCount')::integer, envelope_bytes, p_envelope)
  on conflict (user_id, app_id, document_id) do update set revision = excluded.revision, payload_hash = excluded.payload_hash, payload_byte_count = excluded.payload_byte_count, envelope_byte_count = excluded.envelope_byte_count, envelope = excluded.envelope, updated_at = now();
  return query select next_revision, p_envelope->>'payloadHash';
end;
$$;

-- Keep historical validators for read compatibility; only direct writes are accepted.
revoke all on function public.nanfeng_sync_commit_document(text,text,bigint,jsonb) from public, anon;
grant execute on function public.nanfeng_sync_commit_document(text,text,bigint,jsonb) to authenticated;
revoke all on public.nfai_sync_documents from anon, authenticated;
alter table public.nfai_sync_documents enable row level security;
alter table public.nfai_sync_documents force row level security;

-- A single JSON result cannot be silently truncated by PostgREST's row limit.
create or replace function public.nanfeng_sync_inventory(p_app_id text)
returns jsonb language plpgsql security definer set search_path = public, auth as $$
declare result jsonb;
begin
  if auth.uid() is null or p_app_id is distinct from 'com.nanzhufeng.ai' then raise exception 'NFAI_SYNC_UNAUTHORIZED'; end if;
  select jsonb_build_object('documentCount', count(*), 'documents', coalesce(jsonb_agg(jsonb_build_object('envelope', d.envelope) order by d.updated_at desc, d.document_id asc), '[]'::jsonb))
    into result from public.nfai_sync_documents d where d.user_id = auth.uid() and d.app_id = p_app_id;
  if (result->>'documentCount')::integer > 10000 or octet_length(result::text) > 33554432 then raise exception 'NFAI_SYNC_LIST_TOO_LARGE'; end if;
  return result;
end;
$$;
revoke all on function public.nanfeng_sync_inventory(text) from public, anon;
grant execute on function public.nanfeng_sync_inventory(text) to authenticated;

notify pgrst, 'reload schema';
commit;
