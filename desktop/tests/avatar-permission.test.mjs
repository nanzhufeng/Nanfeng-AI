import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

test('account capability permits the avatar IPC used by the account page', async () => {
  const permissions = await readFile(new URL('../src-tauri/permissions/default.toml', import.meta.url), 'utf8');
  const account = permissions.split('identifier = "allow-desktop-account-sync"')[1]?.split('[[permission]]')[0];
  assert.ok(account?.includes('"read_desktop_google_avatar"'));
});
