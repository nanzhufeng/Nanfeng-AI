-- P7-G: PostgreSQL's regular-expression engine rejects the previous
-- oversized repeat bound at function execution time (SQLSTATE 2201B).
-- Validate alphabet and byte-safe encoded length independently instead.
create or replace function public.nanfeng_sync_valid_envelope(
  p_envelope jsonb,
  p_app_id text,
  p_document_id text,
  p_revision bigint
)
returns boolean
language sql
immutable
set search_path = ''
as $$
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

notify pgrst, 'reload schema';
