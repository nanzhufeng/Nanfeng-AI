-- P7-C: encrypted P7-A envelopes only. This migration intentionally contains no project URL,
-- key, account, business row, recovery-code plaintext, service_role use, or direct table grant.
begin;

create table if not exists public.nfai_account_keys (
  user_id uuid not null references auth.users(id) on delete cascade,
  app_id text not null check (app_id = 'com.nanzhufeng.ai'),
  protocol_version integer not null check (protocol_version = 1),
  key_document_id text not null check (key_document_id ~ '^[A-Za-z0-9._-]{2,128}$'),
  key_revision bigint not null check (key_revision > 0),
  key_payload_hash text not null check (key_payload_hash ~ '^[a-f0-9]{64}$'),
  recovery_wrap_metadata jsonb not null,
  metadata_hash text not null check (metadata_hash ~ '^[a-f0-9]{64}$'),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (user_id, app_id)
);

create table if not exists public.nfai_sync_documents (
  user_id uuid not null references auth.users(id) on delete cascade,
  app_id text not null check (app_id = 'com.nanzhufeng.ai'),
  document_id text not null check (document_id ~ '^[A-Za-z0-9._-]{2,128}$'),
  revision bigint not null check (revision > 0),
  protocol_version integer not null check (protocol_version = 1),
  schema_version integer not null check (schema_version = 1),
  payload_hash text not null check (payload_hash ~ '^[a-f0-9]{64}$'),
  payload_byte_count integer not null check (payload_byte_count between 1 and 1048576),
  envelope_byte_count integer not null check (envelope_byte_count between 1 and 2097152),
  envelope jsonb not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (user_id, app_id, document_id)
);

create or replace function public.nanfeng_sync_touch_updated_at()
returns trigger language plpgsql set search_path = '' as $$
begin new.updated_at = now(); return new; end;
$$;
drop trigger if exists nfai_account_keys_touch_updated_at on public.nfai_account_keys;
create trigger nfai_account_keys_touch_updated_at before update on public.nfai_account_keys
  for each row execute function public.nanfeng_sync_touch_updated_at();
drop trigger if exists nfai_sync_documents_touch_updated_at on public.nfai_sync_documents;
create trigger nfai_sync_documents_touch_updated_at before update on public.nfai_sync_documents
  for each row execute function public.nanfeng_sync_touch_updated_at();

-- The account-key record is a field-exact P7-A wrapping projection (kdf + wrappedDataKey + AAD
-- header fields). It is not a second crypto format and never contains a plaintext data key.
create or replace function public.nanfeng_sync_valid_key_metadata(p_metadata jsonb)
returns boolean language sql immutable set search_path = '' as $$
  select jsonb_typeof(p_metadata) = 'object'
    and (select array_agg(k order by k) from jsonb_object_keys(p_metadata) k)
        = array['kdf','wrappedDataKey']
    and p_metadata #>> '{kdf,algorithm}' = 'PBKDF2-HMAC-SHA256'
    and (select array_agg(k order by k) from jsonb_object_keys(p_metadata->'kdf') k)
        = array['algorithm','iterations','salt','version']
    and (p_metadata #>> '{kdf,version}')::integer = 1
    and (p_metadata #>> '{kdf,iterations}')::integer = 210000
    and p_metadata #>> '{kdf,salt}' ~ '^[A-Za-z0-9_-]{22}$'
    and p_metadata #>> '{wrappedDataKey,algorithm}' = 'AES-256-GCM'
    and (select array_agg(k order by k) from jsonb_object_keys(p_metadata->'wrappedDataKey') k)
        = array['algorithm','ciphertext','nonce']
    and p_metadata #>> '{wrappedDataKey,nonce}' ~ '^[A-Za-z0-9_-]{16}$'
    and p_metadata #>> '{wrappedDataKey,ciphertext}' ~ '^[A-Za-z0-9_-]{64}$';
$$;

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
    -- PostgreSQL rejects an upper repetition bound above its regex limit.
    -- Keep syntax and size validation separate so valid large encrypted payloads
    -- do not fail during regex compilation.
    and p_envelope #>> '{payload,ciphertext}' ~ '^[A-Za-z0-9_-]+$'
    and length(p_envelope #>> '{payload,ciphertext}') between 1 and 1398123;
$$;

alter table public.nfai_account_keys enable row level security;
alter table public.nfai_account_keys force row level security;
alter table public.nfai_sync_documents enable row level security;
alter table public.nfai_sync_documents force row level security;
-- No table policies and no table grants: default deny. SECURITY DEFINER RPCs below re-check auth.uid().
revoke all on public.nfai_account_keys, public.nfai_sync_documents from anon, authenticated;

create or replace function public.nanfeng_sync_read_account_key(p_app_id text)
returns table(key_document_id text, key_revision bigint, key_payload_hash text, recovery_wrap_metadata jsonb, metadata_hash text)
language plpgsql security definer set search_path = public, auth as $$
begin
  if auth.uid() is null or p_app_id <> 'com.nanzhufeng.ai' then raise exception 'NFAI_SYNC_UNAUTHORIZED'; end if;
  return query select k.key_document_id, k.key_revision, k.key_payload_hash, k.recovery_wrap_metadata, k.metadata_hash
    from public.nfai_account_keys k where k.user_id = auth.uid() and k.app_id = p_app_id;
end;
$$;

create or replace function public.nanfeng_sync_put_account_key(
  p_app_id text, p_key_document_id text, p_key_revision bigint, p_key_payload_hash text,
  p_recovery_wrap_metadata jsonb, p_metadata_hash text
) returns table(inserted boolean, metadata_hash text)
language plpgsql security definer set search_path = public, auth as $$
declare existing public.nfai_account_keys%rowtype;
begin
  if auth.uid() is null or p_app_id <> 'com.nanzhufeng.ai' or p_key_document_id !~ '^[A-Za-z0-9._-]{2,128}$'
     or p_key_revision <= 0 or p_key_payload_hash !~ '^[a-f0-9]{64}$' or p_metadata_hash !~ '^[a-f0-9]{64}$'
     or not public.nanfeng_sync_valid_key_metadata(p_recovery_wrap_metadata) then raise exception 'NFAI_SYNC_INVALID_KEY_RECORD'; end if;
  perform pg_advisory_xact_lock(hashtextextended(auth.uid()::text || ':' || p_app_id || ':account-key', 0));
  select * into existing from public.nfai_account_keys where user_id = auth.uid() and app_id = p_app_id for update;
  if found then
    if existing.metadata_hash <> p_metadata_hash then raise exception 'NFAI_SYNC_ACCOUNT_KEY_EXISTS'; end if;
    return query select false, existing.metadata_hash; return;
  end if;
  insert into public.nfai_account_keys(user_id, app_id, protocol_version, key_document_id, key_revision, key_payload_hash, recovery_wrap_metadata, metadata_hash)
    values(auth.uid(), p_app_id, 1, p_key_document_id, p_key_revision, p_key_payload_hash, p_recovery_wrap_metadata, p_metadata_hash);
  return query select true, p_metadata_hash;
end;
$$;

create or replace function public.nanfeng_sync_read_document(p_app_id text, p_document_id text)
returns table(revision bigint, payload_hash text, envelope jsonb)
language plpgsql security definer set search_path = public, auth as $$
begin
  if auth.uid() is null or p_app_id <> 'com.nanzhufeng.ai' or p_document_id !~ '^[A-Za-z0-9._-]{2,128}$' then raise exception 'NFAI_SYNC_UNAUTHORIZED'; end if;
  return query select d.revision, d.payload_hash, d.envelope from public.nfai_sync_documents d
    where d.user_id = auth.uid() and d.app_id = p_app_id and d.document_id = p_document_id;
end;
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

revoke all on function public.nanfeng_sync_read_account_key(text), public.nanfeng_sync_put_account_key(text,text,bigint,text,jsonb,text), public.nanfeng_sync_read_document(text,text), public.nanfeng_sync_commit_document(text,text,bigint,jsonb) from public, anon;
grant execute on function public.nanfeng_sync_read_account_key(text), public.nanfeng_sync_put_account_key(text,text,bigint,text,jsonb,text), public.nanfeng_sync_read_document(text,text), public.nanfeng_sync_commit_document(text,text,bigint,jsonb) to authenticated;
commit;
