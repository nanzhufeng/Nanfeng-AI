import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { renderChatFirstShell } from '../src/chat-shell.mjs';

const data = {
  summary: { id: 'workspace-deep-visual-parity' },
  exchange: {
    conversations: [{
      id: 'conversation-deep-visual-parity',
      title: '深层双端同步',
      revision: 1,
      messages: [{
        id: 'assistant-markdown', role: 'assistant', createdAt: '2026-09-02T00:00:00Z',
        blocks: [{ kind: 'TEXT', text: '## 结论\n\n- 第一项\n- 第二项\n\n| 项目 | 状态 | 说明 |\n| --- | --- | --- |\n| 设置 | 完成 | 已同步 |' }],
      }],
    }],
  },
};

test('Desktop settings use one spacious wide-screen density owner without scaling the chat composer', async () => {
  const css = await readFile(resolve(import.meta.dirname, '../src/chat-shell.css'), 'utf8');
  const settings = css.slice(css.indexOf('/* Android current settings IA'), css.indexOf(':root[data-appearance-mode="dark"] .android-settings-main'));

  for (const token of [
    '--settings-desktop-page-max: 880px',
    '--settings-desktop-row-height: 52px',
    'grid-template-columns: clamp(320px, 30vw, 420px) minmax(0, 1fr)',
    'max-width: var(--settings-desktop-page-max)',
    '.chat-first .android-settings-row',
    'min-height: var(--settings-desktop-row-height)',
    '.android-settings-tone-picker',
    'width: min(760px, calc(100vw - 48px))',
  ]) assert.ok(settings.includes(token), token);

  assert.ok(!settings.includes('.chat-composer-wrap { width: min(880px'));
});

test('Desktop settings keep the 1440x900 density at every viewport and use small windows only as overflow checks', async () => {
  const css = await readFile(resolve(import.meta.dirname, '../src/chat-shell.css'), 'utf8');
  const settings = css.slice(css.indexOf('/* Android current settings IA'), css.indexOf(':root[data-appearance-mode="dark"] .android-settings-main'));

  for (const token of [
    '--settings-desktop-header-height: 72px',
    '--settings-desktop-row-height: 52px',
    '--settings-desktop-primary-row-height: var(--settings-desktop-row-height)',
    '--settings-desktop-group-gap: 18px',
    '--settings-desktop-section-gap: 10px',
    '--settings-desktop-divider-height: 4px',
  ]) assert.ok(settings.includes(token), token);

  for (const forbidden of [
    '@media (min-width: 561px) and (max-width: 1180px)',
    'max-height: 820px',
    '--settings-desktop-primary-row-height: 48px',
    '.android-settings-layout.mobile-home',
    '.android-settings-layout.mobile-detail',
  ]) assert.ok(!settings.includes(forbidden), forbidden);
});

test('appearance values share one fixed right-aligned geometry owner', async () => {
  const css = await readFile(resolve(import.meta.dirname, '../src/chat-shell.css'), 'utf8');
  const html = renderChatFirstShell({ data, native: true, pane: 'settings', settingsSection: 'personalization', status: '', error: '', connection: {}, appearance: { mode: 'system', fontSize: 'standard', themeColor: 'orange' } });

  assert.match(css, /grid-template-columns: 24px minmax\(0, 1fr\) minmax\(108px, auto\)/);
  assert.match(css, /\.android-settings-row-value \{[^}]*min-width: 108px;[^}]*justify-content: flex-end;[^}]*justify-self: end;/s);
  for (const value of ['系统（默认）', '标准', '橙色']) assert.match(html, new RegExp(`<span class="android-settings-row-value">[^<]*(?:<i[^>]*></i>)?<small>${value}</small></span>`));
});

test('the six screenshot-heavy settings surfaces share spacious cards and complete Android wording', () => {
  const common = { data, native: true, pane: 'settings', status: '', error: '', connection: {}, settingsCapabilities: { ordinaryChatPersonalization: true, historyLibrary: true, webSearch: true } };
  const pages = ['personalization', 'model', 'conversations', 'data', 'workspace', 'development'];
  const html = pages.map(settingsSection => renderChatFirstShell({ ...common, settingsSection })).join('\n');

  for (const token of [
    '当前风格会用于每次普通对话；只改变表达方式，不改变模型、联网、记忆或资料库功能。',
    '模型设置', '费用与用量', '上下文记录', '运行诊断',
    '收藏', '已归档', '回收站',
    '导入 ChatGPT ZIP', '导入工作区', '导出工作区', '本机备份与恢复',
    '管理 Projects', '管理知识库', '本次 Context 控制', '离线评测',
  ]) assert.ok(html.includes(token), token);
});

test('model settings is the emphasized Android primary entry and operational records remain secondary', () => {
  const html = renderChatFirstShell({ data, native: true, pane: 'settings', settingsSection: 'model', status: '', error: '', connection: {}, settingsCapabilities: { webSearch: true } });
  const primaryStart = html.indexOf('android-settings-model-primary-entry');
  const recordsStart = html.indexOf('android-settings-model-records');

  assert.ok(primaryStart > -1, 'dedicated model settings entry');
  assert.ok(recordsStart > primaryStart, 'operational records follow the primary entry');
  const primary = html.slice(primaryStart, recordsStart);
  for (const token of ['data-page="model-configuration"', '<strong>\u6a21\u578b\u8bbe\u7f6e</strong>', 'OpenRouter\u3001Qwen\u3001DeepSeek\u3001\u667a\u8c31\u7684 API Key \u4e0e\u6a21\u578b']) assert.ok(primary.includes(token), token);
  assert.ok(!primary.includes('\u8d39\u7528\u4e0e\u7528\u91cf'));
  const records = html.slice(recordsStart);
  for (const token of ['<h2>\u8c03\u7528\u8bb0\u5f55</h2>', '\u8d39\u7528\u4e0e\u7528\u91cf', '\u4e0a\u4e0b\u6587\u8bb0\u5f55', '\u8fd0\u884c\u8bca\u65ad']) assert.ok(records.includes(token), token);
});

test('assistant Markdown owns a desktop reading measure and stable scrollable table geometry', async () => {
  const css = await readFile(resolve(import.meta.dirname, '../src/chat-shell.css'), 'utf8');
  for (const token of [
    '--chat-reading-width: 1040px',
    '--chat-composer-width: 920px',
    'max-width: var(--chat-reading-width)',
    'font-size: 15px',
    'line-height: 1.72',
    'min-width: 140px',
    'overflow-x: auto',
  ]) assert.ok(css.includes(token), token);
  assert.match(css, /\.chat-scroll \{[^}]*scrollbar-gutter: stable both-edges;[^}]*padding: 0 max\(24px, calc\(\(100% - var\(--chat-reading-width\)\) \/ 2\)\) 24px;/s);
  assert.match(css, /\.chat-transcript-stage > \.chat-scroll \{ grid-column: 1 \/ -1; grid-row: 1;/);
  assert.match(css, /\.chat-composer-wrap \{ width: min\(var\(--chat-composer-width\), calc\(100% - 40px\)\); margin: 0 auto;/);

  const html = renderChatFirstShell({ data, native: true, pane: 'chat', selectedConversationId: 'conversation-deep-visual-parity', status: '', error: '', connection: {} });
  for (const token of ['chat-markdown-body', '<h3>结论</h3>', 'chat-markdown-list chat-markdown-unordered', 'chat-markdown-table-wrap', '<th>项目</th>', '<td>已同步</td>']) assert.ok(html.includes(token), token);
});
