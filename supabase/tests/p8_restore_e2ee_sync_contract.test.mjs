import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'

const migration = await readFile(new URL('../migrations/202609150006_p8_restore_e2ee_sync.sql', import.meta.url), 'utf8')

test('P8 follow-up accepts only AES-GCM recovery-wrapped envelopes', () => {
  assert.match(migration, /p_envelope->>'format' = 'nfai\.sync\.envelope'/)
  assert.match(migration, /public\.nanfeng_sync_valid_key_metadata/)
  assert.match(migration, /p_envelope #>> '\{payload,algorithm\}' = 'AES-256-GCM'/)
  assert.match(migration, /nanfeng_sync_valid_envelope\(p_envelope, p_app_id, p_document_id, next_revision\)/)
  assert.doesNotMatch(migration, /nanfeng_sync_valid_direct_envelope\(p_envelope/)
  assert.match(migration, /drop function if exists public\.nanfeng_sync_valid_direct_envelope/)
})
