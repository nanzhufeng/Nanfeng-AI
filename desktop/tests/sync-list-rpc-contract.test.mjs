import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

test('cloud recovery listing is account-scoped, encrypted-only and authenticated', async () => {
  const sql = await readFile(resolve(import.meta.dirname, '../../supabase/migrations/202609120002_p7f_list_sync_documents.sql'), 'utf8');
  for (const token of ['create or replace function public.nanfeng_sync_list_documents', 'auth.uid() is null', "p_app_id <> 'com.nanzhufeng.ai'", 'd.user_id = auth.uid()', 'envelope jsonb', 'NFAI_SYNC_LIST_TOO_LARGE', 'grant execute on function public.nanfeng_sync_list_documents(text) to authenticated']) assert.ok(sql.includes(token));
  assert.ok(!sql.includes('service_role'));
});
