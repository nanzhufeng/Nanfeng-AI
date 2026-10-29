import test from 'node:test';
import assert from 'node:assert/strict';
import { conversationLocalDate, renderChatFirstShell } from '../src/chat-shell.mjs';
import { resizeComposer } from '../src/composer-size.mjs';

const options = { year: 'numeric', month: '2-digit', day: '2-digit' };
const render = conversations => renderChatFirstShell({ data: { summary: { id: 'synthetic-list-cost' }, exchange: { conversations, projects: [], knowledge: [], memory: [], relations: [] } }, native: true, selectedConversationId: null, composerDraft: '', chatSearch: '', pane: 'chat', status: '', error: '', connection: {}, appearance: { mode: 'system', fontSize: 'standard', themeColor: 'orange' }, sidebarConversationList: 'cloud', cloudConversations: conversations });

test('cloud list keeps every identity and local date without constructing a formatter per row', () => {
 const items = Array.from({ length: 2048 }, (_, i) => ({ id: `synthetic-cloud-${i}`, workspaceId: `synthetic-workspace-${i}`, title: `合成会话${i}`, revision: 1, cloud: true, createdAt: '2026-01-01T23:30:00Z', messages: [] }));
 const Native = Intl.DateTimeFormat; const expected = new Native('zh-CN', options).format(new Date(items[0].createdAt)).replaceAll('-', '/'); let constructions = 0;
 Intl.DateTimeFormat = function(...args) { constructions++; return new Native(...args); };
 let html; try { html = render(items); } finally { Intl.DateTimeFormat = Native; }
 assert.equal((html.match(/data-conversation-row/g) || []).length, 2048);
 assert.equal((html.match(new RegExp(`<small class="chat-history-date">${expected}</small>`, 'g')) || []).length, 2048);
 for (const item of items) assert.ok(html.includes(`data-id="${item.id}"`));
 assert.ok(constructions <= 3, `formatters must be bounded per render, got ${constructions}`);
});

test('dates preserve invalid/fallback semantics and a new render observes the current time zone', () => {
 const old = process.env.TZ;
 try {
  for (const zone of ['Pacific/Honolulu', 'Pacific/Kiritimati']) {
   process.env.TZ = zone;
   const item = { id: 'synthetic-timezone', title: '时区刷新', revision: 1, createdAt: '2026-01-01T23:30:00Z', messages: [] };
   const expected = new Intl.DateTimeFormat('zh-CN', options).format(new Date(item.createdAt)).replaceAll('-', '/');
   assert.equal(conversationLocalDate(item), expected); assert.ok(render([item]).includes(`<small class="chat-history-date">${expected}</small>`));
  }
  assert.equal(conversationLocalDate({ createdAt: 'invalid', updatedAt: '2026-01-01' }), '本地时间未知');
  for (const item of [undefined, {}, { createdAt: '', updatedAt: '2026-01-01' }]) {
   const value = new Date(item?.createdAt || item?.updatedAt || 0);
   assert.equal(conversationLocalDate(item), new Intl.DateTimeFormat('zh-CN', options).format(value).replaceAll('-', '/'));
  }
 } finally { if (old === undefined) delete process.env.TZ; else process.env.TZ = old; }
});

test('composer expands and shrinks within the approved height range using one layout measurement', () => {
 for (const [measured, height, overflow] of [[12,36,'hidden'],[80,80,'hidden'],[320,190,'auto']]) {
  let reads = 0; const input = { style: { height: '190px', overflowY: 'auto' }, get scrollHeight() { reads++; return measured; } };
  resizeComposer(input); assert.equal(input.style.height, `${height}px`); assert.equal(input.style.maxHeight, '190px'); assert.equal(input.style.overflowY, overflow); assert.equal(reads, 1);
 }
 assert.doesNotThrow(() => resizeComposer(null));
});
