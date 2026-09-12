import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { renderChatFirstShell } from '../src/chat-shell.mjs';

const root = new URL('../', import.meta.url);
const [app, css] = await Promise.all([
  readFile(new URL('src/app.mjs', root), 'utf8'),
  readFile(new URL('src/chat-shell.css', root), 'utf8'),
]);

test('FB-P6-050 desktop rail previews only on hover or keyboard focus and jumps through the existing transcript scroll owner', () => {
  const data = { summary: { id: 'rail' }, exchange: { conversations: [{ id: 'rail-chat', title: '位置', archived: false, revision: 1, messages: [
    { id: 'm1', role: 'user', createdAt: '2026-08-14T00:00:00Z', blocks: [{ kind: 'TEXT', text: '第一条' }] },
    { id: 'm2', role: 'assistant', createdAt: '2026-08-14T00:01:00Z', blocks: [{ kind: 'TEXT', text: '第二条' }] },
  ] }] } };
  const html = renderChatFirstShell({ data, native: true, selectedConversationId: 'rail-chat', pane: 'chat', status: '', error: '', connection: {} });
  assert.match(html, /chat-transcript-rail/);
  assert.doesNotMatch(html, /class="is-active"|aria-current="true"/, 'initial rail has no fabricated current position');
  assert.match(html, /data-action="jump-transcript-position" data-index="0" data-rail-slot="0"/);
  assert.match(html, /data-scroll-owner="message-list"/);
  assert.ok(app.includes("[data-action=\"jump-transcript-position\"]"));
  assert.ok(app.includes("message?.scrollIntoView({ block: 'center', behavior: 'smooth' })"));
  assert.ok(css.includes('.chat-transcript-rail button:hover em'));
  assert.ok(css.includes('.chat-transcript-rail button:focus-visible em'));
  assert.ok(!css.includes('.chat-transcript-rail button.is-active em'));
  for (const token of ['justify-content: center', 'gap: 3.5px', 'width: 5.33px', 'height: 2px', 'width: 20px', 'width: 14px', 'width: 10px', 'width: 7px', 'left: 44px', 'data-rail-distance="1"', 'data-rail-distance="6"']) assert.ok(css.includes(token));
  assert.ok(css.includes('.chat-transcript-rail button.is-active > span { width: 28px'));
  for (const token of ['width: min(320px, calc(100vw - 132px))', 'max-height: 72px', 'border-radius: 16px']) assert.ok(css.includes(token));
  assert.ok(app.includes("item.classList.toggle('is-active', emphasize && item === target)"));
  for (const token of ['function applyTranscriptRailPosition', 'function clearTranscriptRailEmphasis', 'function syncTranscriptRailToScroll', 'scroll.scrollTop > 1', 'transcriptRailTrackingConversationId', 'target?.dataset.railSlot', "item.dataset.railDistance", "Math.min(6, distance)", "delete item.dataset.railDistance", 'pointerover', 'pointerout', 'focusin', 'focusout']) assert.ok(app.includes(token));
  assert.ok(app.includes('if (event.detail === 0) target.focus'));
  assert.ok(app.includes('else target.blur()'));
  assert.ok(css.includes('.chat-transcript-rail { display: none; }'));
});
