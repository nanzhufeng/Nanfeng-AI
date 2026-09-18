import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

test('full local-index query does not queue behind the mutable Desktop store', async () => {
  const rust = await readFile(resolve(import.meta.dirname, '../src-tauri/src/lib.rs'), 'utf8');
  const start = rust.indexOf('async fn query_desktop_local_index(');
  const end = rust.indexOf('\n#[tauri::command]', start + 1);
  const command = rust.slice(start, end);
  assert.match(command, /run_desktop_store_paths_blocking/);
  assert.doesNotMatch(command, /run_desktop_store_blocking/);
  assert.match(command, /query_local_index_at_cancellable\(\s*&paths\.root,\s*&paths\.database/);
  const pathOwner = rust.slice(rust.indexOf('async fn run_desktop_store_paths_blocking'), rust.indexOf('\nconst DESKTOP_MEDIA_RANGE_CHUNK_BYTES'));
  assert.match(pathOwner, /app\.area_state\(\)\.store_paths\.clone\(\)/);
  assert.doesNotMatch(pathOwner, /state\.store\.lock\(\)/);
});
