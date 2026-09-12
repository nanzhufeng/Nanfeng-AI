import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { renderChatFirstShell } from '../src/chat-shell.mjs';

const root = resolve(import.meta.dirname, '..');
const [source, shell, css] = await Promise.all([
  readFile(resolve(root, 'src/app.mjs'), 'utf8'),
  readFile(resolve(root, 'src/chat-shell.mjs'), 'utf8'),
  readFile(resolve(root, 'src/chat-shell.css'), 'utf8'),
]);

const data = {
  summary: { id: 'fb-p6-043-workspace' },
  exchange: { conversations: [{
    id: 'fb-p6-043-conversation', title: '回到底部', archived: false, revision: 1,
    messages: [{ id: 'message-1', role: 'assistant', createdAt: '2026-08-14T00:00:00Z', blocks: [{ kind: 'TEXT', text: '本地消息' }] }],
  }] },
};

test('FB-P6-043 keeps a stable latest control and only reveals it away from the end', () => {
  const atLatest = renderChatFirstShell({ data, native: true, selectedConversationId: 'fb-p6-043-conversation', pane: 'chat', status: '', error: '', connection: {}, showScrollToLatest: false });
  const awayFromLatest = renderChatFirstShell({ data, native: true, selectedConversationId: 'fb-p6-043-conversation', pane: 'chat', status: '', error: '', connection: {}, showScrollToLatest: true });
  assert.match(atLatest, /data-action="scroll-to-latest"[^>]*hidden/);
  assert.match(awayFromLatest, /chat-composer-dock[\s\S]*data-action="scroll-to-latest"[\s\S]*data-anchor="composer-top"[\s\S]*chat-composer-wrap/);
  assert.doesNotMatch(awayFromLatest, /data-action="scroll-to-latest"[^>]*hidden/);
  for (const token of ['.chat-composer-dock { position: absolute;', 'pointer-events: none;', '.chat-composer-dock .chat-composer-wrap, .chat-composer-dock .chat-scroll-to-latest { pointer-events: auto;', '.chat-transcript-stage > .chat-scroll { grid-column: 1 / -1; grid-row: 1; padding-top: 80px; padding-bottom: 190px;', '.chat-composer-wrap { width: min(var(--chat-composer-width), calc(100% - 40px)); margin: 0 auto;', 'bottom: calc(100% + 12px)', 'left: 50%', 'transform: translateX(-50%)']) assert.ok(css.includes(token), token);
  const latestControlStyles = css.slice(css.indexOf('.chat-scroll-to-latest {'), css.indexOf('.chat-scroll-to-latest svg {'));
  assert.match(latestControlStyles, /border:\s*0\s*!important/);
  assert.doesNotMatch(latestControlStyles, /border:\s*1px/);
  assert.ok(!css.includes('.chat-scroll-to-latest { position: absolute; z-index: 4; right:'));
});

test('FB-P6-043 scrolls through the sole message-list owner without an intermediate render interrupting the animation', () => {
  const scrollHandlerStart = source.indexOf("app.addEventListener('scroll'");
  const scrollHandlerEnd = source.indexOf("}, true);", scrollHandlerStart) + "}, true);".length;
  const scrollHandler = source.slice(scrollHandlerStart, scrollHandlerEnd);
  for (const token of [
    "event.target.closest?.('.chat-scroll[data-conversation-id]')",
    'state.chatAtLatest = atLatest',
    "action !== 'scroll-to-latest'",
    'state.pendingChatScrollToLatestId = scroll.dataset.conversationId',
    'state.focusComposerAfterScrollToLatest = true',
    'state.chatAtLatest = true',
    'state.scrollToLatestAnimationId = conversation.id',
    'state.scrollToLatestAnimationId === scroll.dataset.conversationId',
    'state.scrollToLatestAnimationId = null',
    "scroll.scrollTo({ top: scroll.scrollHeight, behavior: 'smooth' })",
    'if (restoreLatest)',
    "document.querySelector('#chat-composer')?.focus({ preventScroll: true })",
  ]) assert.ok(source.includes(token), token);
  assert.match(scrollHandler, /syncScrollToLatestControl\(scroll, atLatest\)/);
  assert.doesNotMatch(scrollHandler, /render\(\)/);
});
