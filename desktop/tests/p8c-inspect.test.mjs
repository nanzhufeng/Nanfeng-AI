import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const root = new URL('../', import.meta.url);

test('P8-C inspect page is reachable and only calls the read-only command', async () => {
  const [app, settings, page, rust, capability, permissions] = await Promise.all([
    readFile(new URL('src/app.mjs', root), 'utf8'),
    readFile(new URL('src/android-settings-shell.mjs', root), 'utf8'),
    readFile(new URL('src/p8-inspect.mjs', root), 'utf8'),
    readFile(new URL('src-tauri/src/lib.rs', root), 'utf8'),
    readFile(new URL('src-tauri/capabilities/default.json', root), 'utf8'),
    readFile(new URL('src-tauri/permissions/default.toml', root), 'utf8'),
  ]);
  assert.match(settings, /show-p8-inspect/);
  assert.match(app, /invoke\('inspect_p8_agent_runs'\)/);
  assert.match(page, /READ_ONLY · LOCAL_READ · NONE/);
  assert.match(page, /未连接模型与外部工具/);
  assert.match(page, /不会创建计划、批准、执行、暂停、恢复、取消或调用工具/);
  assert.match(rust, /fn inspect_p8_agent_runs/);
  assert.match(capability, /allow-inspect-p8-agent-runs/);
  assert.match(permissions, /commands.allow = \["inspect_p8_agent_runs"\]/);
  for (const forbidden of ['start_p8_agent', 'approve_p8_agent', 'execute_p8_agent', 'pause_p8_agent', 'resume_p8_agent', 'cancel_p8_agent']) {
    assert.doesNotMatch(rust, new RegExp(`fn ${forbidden}`));
  }
});
