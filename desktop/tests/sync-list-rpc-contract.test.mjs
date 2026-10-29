import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

test('historical encrypted-list RPC remains account-scoped for old clients', async () => {
  const sql = await readFile(resolve(import.meta.dirname, '../../supabase/migrations/202609120002_p7f_list_sync_documents.sql'), 'utf8');
  for (const token of ['create or replace function public.nanfeng_sync_list_documents', 'auth.uid() is null', "p_app_id <> 'com.nanzhufeng.ai'", 'd.user_id = auth.uid()', 'envelope jsonb', 'NFAI_SYNC_LIST_TOO_LARGE', 'grant execute on function public.nanfeng_sync_list_documents(text) to authenticated']) assert.ok(sql.includes(token));
  assert.ok(!sql.includes('service_role'));
});

test('current direct-sync inventory returns a bounded complete account list', async () => {
  const sql = await readFile(resolve(import.meta.dirname, '../../supabase/migrations/202609180007_p8_account_isolated_sync.sql'), 'utf8');
  for (const token of [
    'create or replace function public.nanfeng_sync_inventory(p_app_id text)',
    'returns jsonb',
    'auth.uid() is null',
    'd.user_id = auth.uid() and d.app_id = p_app_id',
    "'documentCount', count(*)",
    "'documents', coalesce(jsonb_agg",
    '10000',
    '33554432',
    'grant execute on function public.nanfeng_sync_inventory(text) to authenticated',
  ]) assert.ok(sql.includes(token), `missing inventory contract: ${token}`);
  assert.ok(!sql.includes('service_role'));
});
