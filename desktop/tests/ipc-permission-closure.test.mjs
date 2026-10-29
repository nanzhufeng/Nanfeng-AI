import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const native = new URL('../src-tauri/', import.meta.url);
const source = await readFile(new URL('src/lib.rs', native), 'utf8');
const permissions = await readFile(new URL('permissions/default.toml', native), 'utf8');
const handlers = [...source.matchAll(/generate_handler!\s*\[([^\]]+)\]/g)];
assert.equal(handlers.length, 1, 'review a new handler composition explicitly');
const handlerNames = handlers[0][1].split(',').map(name => name.trim()).filter(Boolean);
for (const name of handlerNames) assert.match(name, /^[a-z][a-z_0-9]*(?:::[a-z][a-z_0-9]*)*$/, 'review changed handler macro syntax explicitly');
const registered = new Set(handlerNames.map(name => name.split('::').at(-1)));
assert.equal(registered.size, handlerNames.length, 'duplicate handler registration');
const blocks = permissions.split('[[permission]]').slice(1).map(block => ({
  identifier: block.match(/identifier\s*=\s*"([^"]+)"/)?.[1],
  commands: [...(block.match(/commands\.allow\s*=\s*\[([^\]]*)\]/)?.[1] || '').matchAll(/"([^"]+)"/g)].map(match => match[1]),
}));

test('every allowed application IPC command has an actual registered handler', () => {
  const orphaned = blocks.flatMap(block => block.commands.filter(command => !registered.has(command)).map(command => `${block.identifier}: ${command}`));
  assert.deepEqual(orphaned, [], 'stale grants must be reconciled with the actual IPC owner');
});

test('main and generation island capabilities retain distinct windows and grants', async () => {
  const main = JSON.parse(await readFile(new URL('capabilities/default.json', native), 'utf8'));
  const island = JSON.parse(await readFile(new URL('capabilities/generation-island.json', native), 'utf8'));
  assert.deepEqual(main.windows, ['main']);
  assert.deepEqual(island.windows, ['generation-island']);
  assert.deepEqual(island.permissions, ['allow-generation-island', 'core:event:allow-listen', 'core:event:allow-unlisten']);
  assert.ok(main.permissions.includes('allow-read-desktop-runtime-info'));
  assert.ok(main.permissions.includes('allow-open-desktop-source-link'));
  const defined = new Set(blocks.map(block => block.identifier));
  for (const grant of [...main.permissions, ...island.permissions].filter(grant => !grant.includes(':'))) {
    assert.ok(defined.has(grant), `undeclared application grant ${grant}`);
  }
});
