import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';

const root = new URL('../', import.meta.url);
const [shell, css, app] = await Promise.all([
  readFile(new URL('src/chat-shell.mjs', root), 'utf8'),
  readFile(new URL('src/chat-shell.css', root), 'utf8'),
  readFile(new URL('src/app.mjs', root), 'utf8'),
]);

test('sidebar search has no static title or duplicate button while preserving its accessible input', () => {
  const navigation = shell.slice(shell.indexOf('const chatNavigation'), shell.indexOf('return `', shell.indexOf('const chatNavigation')));
  assert.match(navigation, /<div class="chat-search-wrap"><label><span class="chat-search-glyph"[^>]*>.*<\/span><input id="chat-search" class="chat-search" type="search" placeholder="搜索" aria-label="搜索"/);
  assert.doesNotMatch(navigation, /<button data-action="submit-search"/);
  assert.doesNotMatch(navigation, /chat-search-wrap"><span/);
  assert.doesNotMatch(css, /\.chat-search-wrap > span/);
});

test('located conversation header puts its title before the optional return-to-search action', () => {
  const header = shell.slice(shell.indexOf('const header = `'), shell.indexOf('const workspacePageHeader'));
  assert.ok(header.indexOf('<div class="chat-title">') < header.indexOf('data-action="return-to-search"'));
});

test('selecting a left-sidebar conversation exits search drill-down instead of preserving its return route', () => {
  const owner = app.slice(app.indexOf('Search-result drill-down owns'), app.indexOf('async function saveLocalMessage'));
  for (const token of [
    'target?.closest(\'.chat-sidebar\')',
    'state.searchPanel = false',
    'state.searchReturnActive = false',
    'state.searchLocatedArchivedConversationId = null',
    'state.searchAnchorMessageId = null',
    'state.searchAnchorAttachmentId = null',
  ]) assert.ok(owner.includes(token), token);
});
