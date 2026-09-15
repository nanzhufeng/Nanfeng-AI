import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

test('main-chat attachment locate keeps the full catalogue and flashes the exact stable target', async () => {
  const source = await readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8');
  const action = source.slice(source.indexOf("if (action === 'chat-attachment-search')"), source.indexOf("if (action === 'chat-attachment-save'"));
  const reveal = source.slice(source.indexOf('function fullSearchRevealHit'), source.indexOf('async function loadSearchWorkspace'));
  const page = source.slice(source.indexOf('async function runFullSearch'), source.indexOf('function scheduleFullSearch'));

  assert.match(action, /state\.chatSearch = '';/);
  assert.match(action, /state\.searchCategory = 'all';/);
  assert.match(action, /state\.searchRevealTarget = \{/);
  assert.match(action, /openFullSearch\('all'\)/);
  assert.doesNotMatch(action, /item\.displayName/);
  assert.doesNotMatch(action, /openFullSearch\(item\.category\)/);
  assert.match(reveal, /workspaceId === target\.workspaceId/);
  assert.match(reveal, /conversationId === target\.conversationId/);
  assert.match(reveal, /messageId === target\.messageId/);
  assert.match(reveal, /attachmentId === target\.attachmentId/);
  assert.match(reveal, /card\.classList\.add\('search-anchor-flash'\)/);
  assert.match(page, /page\.hasMore\) void runFullSearch\(\{ append: true, restoreScroll: true \}\)/);
  assert.match(page, /revealFullSearchAttachment\(\)/);
});
