import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'

const migration = await readFile(new URL('../migrations/202608130001_p7c_secure_sync.sql', import.meta.url), 'utf8')

test('P7-C migration has default-deny tables and only authenticated RPC access', () => {
  assert.match(migration, /create table if not exists public\.nfai_account_keys/)
  assert.match(migration, /create table if not exists public\.nfai_sync_documents/)
  assert.match(migration, /enable row level security/)
  assert.match(migration, /force row level security/)
  assert.match(migration, /revoke all on public\.nfai_account_keys, public\.nfai_sync_documents from anon, authenticated/)
  assert.match(migration, /grant execute on function[\s\S]+to authenticated/)
  assert.doesNotMatch(migration, /grant\s+(select|insert|update|delete)[\s\S]+to\s+(anon|authenticated)/i)
})

test('P7-C commit is locked, optimistic, envelope-only, and cross-user scoped', () => {
  assert.match(migration, /pg_advisory_xact_lock/)
  assert.match(migration, /coalesce\(current_revision, 0\) <> p_expected_revision/)
  assert.match(migration, /NFAI_SYNC_STALE_REVISION/)
  assert.match(migration, /nanfeng_sync_valid_envelope\(p_envelope, p_app_id, p_document_id, next_revision\)/)
  assert.match(migration, /where d\.user_id = auth\.uid\(\)/)
  assert.match(migration, /auth\.uid\(\) is null/)
  assert.match(migration, /payloadHash/)
  assert.doesNotMatch(migration, /business_plaintext|recovery_code|data_key/i)
})

test('P7-C deliberately exposes no document deletion RPC before a product deletion contract', () => {
  assert.doesNotMatch(migration, /nanfeng_sync_delete_document/)
})
