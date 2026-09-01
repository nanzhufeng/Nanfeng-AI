import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { renderChatFirstShell } from '../src/chat-shell.mjs';

const root = resolve(fileURLToPath(new URL('..', import.meta.url)));
const fixture = {
  summary: { id: 'workspace-compare', title: 'Compare fixture' },
  exchange: { projects: [], knowledge: [], memory: [], relations: [], conversations: [{
    id: 'conversation-compare', title: 'Compare fixture', revision: 3,
    messages: [
      { id: 'user-1', role: 'user', blocks: [{ kind: 'TEXT', text: 'compare this' }] },
      { id: 'assistant-chatgpt', parentId: 'user-1', role: 'assistant', delivery: 'PARTIAL', attemptId: 'attempt-chatgpt', compareExecutionId: 'execution-1', compareLogicalModel: 'CHATGPT', source: 'PROVIDER', modelSnapshot: { providerId: 'OPENROUTER', modelId: 'openai/gpt-5.6-terra', displayName: 'GPT-5.6 Terra' }, blocks: [{ kind: 'TEXT', text: 'partial' }] },
      { id: 'assistant-claude', parentId: 'user-1', role: 'assistant', delivery: 'UNKNOWN', safeErrorCode: 'MISSING_COMPLETION', attemptId: 'attempt-claude', compareExecutionId: 'execution-1', compareLogicalModel: 'CLAUDE', source: 'PROVIDER', modelSnapshot: { providerId: 'OPENROUTER', modelId: 'anthropic/claude-sonnet-5', displayName: 'Claude Sonnet 5' }, chargeMicros: 7, blocks: [{ kind: 'TEXT', text: 'uncertain' }] },
    ],
  }] },
};

test('Desktop Compare has no Composer entry or model-menu duplicate', async () => {
  const html = renderChatFirstShell({ data: fixture, native: true, selectedConversationId: 'conversation-compare', composerDraft: 'compare this', composerAttachments: [], pane: 'chat', status: '', error: '', connection: {} , p6gModelPickerOpen: true });
  assert.equal((html.match(/data-action="open-compare-confirmation"/g) || []).length, 0);
  assert.ok(!html.includes('title="OpenRouter · GPT-5.6 Terra + Claude Sonnet 5"'));
  assert.ok(!html.includes('data-compare-long-press'));
  const modelMenu = html.substring(html.indexOf('p6g-model-popover'), html.indexOf('</div>', html.indexOf('p6g-model-popover')));
  assert.ok(!modelMenu.includes('对比 ChatGPT + Claude'));

  const app = await readFile(resolve(root, 'src/app.mjs'), 'utf8');
  for (const token of ["invoke('submit_desktop_compare'", "invoke('cancel_desktop_compare'", "invoke('retry_desktop_compare_branch'", 'GPT-5.6 Terra + Claude Sonnet 5', 'refreshDesktopRuntimeWhilePending', "message.delivery === 'PARTIAL'"]) assert.ok(app.includes(token), token);
  for (const token of ['const compare = document.querySelector(\'[data-action="open-compare-confirmation"]\')', 'if (compare) compare.disabled =']) assert.ok(app.includes(token), token);
  for (const removed of ['DesktopCompareExecutionOwner', 'compareLongPressTimer', 'compareLongPressTriggered', 'data-compare-long-press']) assert.ok(!app.includes(removed), removed);
});

test('Compare branches expose independent provider model terminal stop retry and cost facts', () => {
  const html = renderChatFirstShell({ data: fixture, native: true, selectedConversationId: 'conversation-compare', composerDraft: '', composerAttachments: [], pane: 'chat', status: '', error: '', connection: {} });
  for (const token of ['ChatGPT', 'Claude', 'OpenRouter · 独立分支', '5.6 Terra', 'Sonnet 5', 'stop-desktop-compare', 'data-execution-id="execution-1"', 'retry-desktop-compare-branch', '连接结果未知，未自动重发', '¥0.0000']) assert.ok(html.includes(token), token);
});

test('build and native permissions expose the real Compare owner without the retired JS and mock-only Rust owners', async () => {
  const [build, permission, capability, rust] = await Promise.all([
    readFile(resolve(root, 'scripts/build.mjs'), 'utf8'),
    readFile(resolve(root, 'src-tauri/permissions/default.toml'), 'utf8'),
    readFile(resolve(root, 'src-tauri/capabilities/default.json'), 'utf8'),
    readFile(resolve(root, 'src-tauri/src/lib.rs'), 'utf8'),
  ]);
  assert.ok(!build.includes('desktop-compare-execution-owner.mjs'));
  for (const token of ['allow-desktop-compare', 'submit_desktop_compare', 'retry_desktop_compare_branch', 'cancel_desktop_compare', 'read_desktop_compare_execution']) assert.ok(`${permission}\n${capability}\n${rust}`.includes(token), token);
  for (const removed of ['desktop_compare_credentials_v1', 'desktop_compare_execution_v1']) assert.ok(!rust.includes(`pub mod ${removed}`), removed);
});
