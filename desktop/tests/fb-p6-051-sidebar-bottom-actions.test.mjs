import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';

const root = new URL('../', import.meta.url);
const [shell, css] = await Promise.all([
  readFile(new URL('src/chat-shell.mjs', root), 'utf8'),
  readFile(new URL('src/chat-shell.css', root), 'utf8'),
]);

test('FB-P6-051 keeps settings and new chat as independent bottom actions at opposite sides', () => {
  const footer = shell.slice(shell.indexOf('<footer class="chat-sidebar-footer">'), shell.indexOf('</footer>', shell.indexOf('<footer class="chat-sidebar-footer">')));
  assert.match(footer, /chat-sidebar-bottom-actions/);
  assert.match(footer, /class="chat-profile chat-sidebar-settings" data-action="show-settings" aria-label="设置"/);
  assert.match(footer, /class="chat-sidebar-function" data-action="new-chat" aria-label="新对话"/);
  assert.doesNotMatch(footer, /chat-sidebar-bottom-actions[^>]*chat-profile[^>]*chat-sidebar-function/);
  for (const token of ['justify-content: space-between', 'background: transparent', 'width: 96px', 'border-radius: 22px', 'border: 0', 'data-action="new-chat"] { flex: 0 0 auto; min-height: 44px; margin-left: auto', 'box-shadow: 0 6px 16px']) assert.ok(css.includes(token));
});

test('FB-P6-051 collapsed sidebar keeps settings and new chat as separate circular actions that fit the rail', () => {
  for (const token of [
    'grid-template-columns: 72px 2px minmax(0, 1fr);',
    '.chat-sidebar.rail-collapsed .chat-profile:not(.chat-sidebar-settings),',
    'flex-direction: column; justify-content: flex-end; gap: 10px; padding: 0;',
    'flex: 0 0 44px; width: 44px; min-width: 44px; min-height: 44px;',
    'border-radius: 50%;',
    '.chat-sidebar.rail-collapsed .chat-sidebar-bottom-actions .chat-profile.chat-sidebar-settings { display: inline-flex; background: var(--foreground-surface, #fff); box-shadow: 0 6px 16px #1f2e2426; }',
    '.chat-sidebar.rail-collapsed .chat-sidebar-bottom-actions .chat-sidebar-function[data-action="new-chat"] { order: 1; }',
    '.chat-sidebar.rail-collapsed .chat-sidebar-bottom-actions .chat-profile.chat-sidebar-settings { order: 2; }',
  ]) assert.ok(css.includes(token), token);
});

test('FB-P6-051 collapsed brand icon is the only expand control', () => {
  assert.match(shell, /railCollapsed\s*\?\s*'<button class="chat-rail-toggle chat-rail-brand-toggle" data-action="toggle-rail" aria-label="展开导航栏" aria-expanded="false" title="展开导航栏"><img src="\.\/nanfeng-ai-icon\.png" alt=""><\/button>'/);
  assert.match(shell, /\$\{railCollapsed \? '' : `<button class="chat-rail-toggle" data-action="toggle-rail" aria-label="折叠导航栏"/);
  for (const token of ['.chat-sidebar.rail-collapsed .chat-brand { width: 100%; justify-content: center;', '.chat-sidebar.rail-collapsed .chat-rail-brand-toggle { width: 48px;', '.chat-sidebar.rail-collapsed .chat-rail-brand-toggle > img { width: 44px; height: 44px;']) assert.ok(css.includes(token), token);
});
