import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { invokeForDataArea, invokeForAuthorizedDataAreaOperation } from '../src/data-area-invoke.mjs';

test('registered Desktop commands capture an area owner instead of raw single-store state', async () => {
  const rust = await readFile(resolve(import.meta.dirname, '../src-tauri/src/lib.rs'), 'utf8');
  const commands = [...rust.matchAll(/#\[tauri::command\]\s*(?:async\s+)?fn\s+(\w+)\s*\((.*?)\)\s*(?:->|\{)/gs)];
  assert.ok(commands.length > 150, 'audit must cover the registered Desktop command surface');
  const unscoped = commands.filter(([, , args]) => /State<'_, AppState>|tauri::AppHandle/.test(args));
  assert.deepEqual(unscoped.map(([, name]) => name), []);
  const area = await readFile(resolve(import.meta.dirname, '../src-tauri/src/desktop_data_area.rs'), 'utf8');
  assert.match(area, /headers\(\)\s*\.get\("x-nanfeng-data-area"\)/);
  assert.match(area, /DesktopDataArea::Work[\s\S]*work_area[\s\S]*工作区数据库尚未就绪/);
});

test('renderer binds native calls and runtime events to their data area', async () => {
  const app = await readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8');
  assert.match(app, /function createAreaState\(dataArea = 'CHAT'\)/);
  assert.match(app, /invokeForDataArea\(tauriBridge, command, args, owner, \(\) => state\)/);
  assert.match(app, /state\.runtimeInfo\?\.dataAreasReady/);
  assert.match(app, /payload\.dataArea \|\| 'CHAT'/);
  assert.match(app, /owner\.dataArea === 'WORK' \? `\$\{cloudConversationCacheKey\}\.WORK` : cloudConversationCacheKey/);
  const cacheKey = app.slice(app.indexOf('function areaCloudConversationCacheKey('), app.indexOf('const parityPreferences ='));
  const resolveKey = new Function('state', 'cloudConversationCacheKey', `${cacheKey}; return { current: areaCloudConversationCacheKey(), work: areaCloudConversationCacheKey({dataArea:'WORK'}), chat: areaCloudConversationCacheKey({dataArea:'CHAT'}) };`);
  for (const dataArea of ['CHAT', 'WORK']) assert.deepEqual(resolveKey({dataArea}, 'cache'), { current: dataArea === 'WORK' ? 'cache.WORK' : 'cache', work: 'cache.WORK', chat: 'cache' });
  assert.match(app, /window\.localStorage\.removeItem\(`\$\{cloudConversationCacheKey\}\.WORK`\)/);
  assert.match(app, /sameAccount \? pendingUnknown : null/);
  const rust = await readFile(resolve(import.meta.dirname, '../src-tauri/src/lib.rs'), 'utf8');
  assert.match(rust, /data_areas_ready: app\.root_state\(\)\.work_area\.is_some\(\)/);
  assert.match(rust, /fn emit_ordinary_chat_event\(\s*app: &AreaAppHandle/);
  assert.match(rust, /fn desktop_media_area_paths<'a>\(/);
  assert.match(rust, /root_state\.work_area\.as_deref\(\)\.map\(\|work\| &work\.store_paths\)/);
  assert.match(rust, /fn data_areas_have_active_execution\(root: &AppState\)/);
  assert.match(rust, /fn cancel_data_area_executions\(root: &AppState\)/);
});

test('a late success and a late failure remain owned by the area that issued them', async () => {
  const chat = { dataArea: 'CHAT' };
  const work = { dataArea: 'WORK' };
  let visible = chat;
  const pending = [];
  const bridge = { invoke(command, args, options) {
    return new Promise((resolve, reject) => pending.push({ command, args, options, resolve, reject }));
  } };
  const success = invokeForDataArea(bridge, 'read_desktop_workspace', { workspaceId: 'same-id' }, chat, () => visible);
  assert.equal(pending[0].options.headers['x-nanfeng-data-area'], 'CHAT');
  visible = work;
  pending[0].resolve({ exchange: { marker: 'CHAT' } });
  await assert.rejects(success, error => error.code === 'STALE_DATA_AREA');

  const failure = invokeForDataArea(bridge, 'read_desktop_workspace', { workspaceId: 'same-id' }, work, () => visible);
  assert.equal(pending[1].options.headers['x-nanfeng-data-area'], 'WORK');
  visible = chat;
  pending[1].reject(new Error('original WORK failure'));
  await assert.rejects(failure, error => error.code === 'STALE_DATA_AREA');

  const current = invokeForDataArea(bridge, 'read_desktop_workspace', { workspaceId: 'same-id' }, chat, () => visible);
  pending[2].resolve({ exchange: { marker: 'CHAT' } });
  assert.deepEqual(await current, { exchange: { marker: 'CHAT' }, dataArea: 'CHAT' });
});

test('an authorized write returns its original-area receipt after navigation for ordered follow-up', async () => {
  let finish;
  let request;
  const bridge = { invoke(command, args, options) {
    request = { command, args, area: options.headers['x-nanfeng-data-area'] };
    return new Promise(resolve => { finish = resolve; });
  } };
  const chat = { dataArea: 'CHAT' };
  const operation = invokeForAuthorizedDataAreaOperation(bridge, 'mutate_desktop_domain', { args: { intentId: 'i' } }, chat);
  const visible = { dataArea: 'WORK' };
  finish({ objectId: 'c' });
  assert.equal(visible.dataArea, 'WORK');
  assert.deepEqual(request, { command: 'mutate_desktop_domain', args: { args: { intentId: 'i' } }, area: 'CHAT' });
  assert.deepEqual(await operation, { objectId: 'c' });
});
