import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'

const migration = await readFile(new URL('../migrations/202609130005_p8_cancel_direct_sync.sql', import.meta.url), 'utf8')

test('P8 direct-sync cancellation deletes only the caller-owned, revision-checked document', () => {
  assert.match(migration, /create or replace function public\.nanfeng_sync_delete_document/)
  assert.match(migration, /auth\.uid\(\) is null/)
  assert.match(migration, /p_expected_revision < 1/)
  assert.match(migration, /pg_advisory_xact_lock/)
  assert.match(migration, /current_revision <> p_expected_revision/)
  assert.match(migration, /where d\.user_id = auth\.uid\(\).*d\.app_id = p_app_id.*d\.document_id = p_document_id/s)
  assert.match(migration, /revoke all on function public\.nanfeng_sync_delete_document[\s\S]+from public, anon/)
  assert.match(migration, /grant execute on function public\.nanfeng_sync_delete_document[\s\S]+to authenticated/)
})
