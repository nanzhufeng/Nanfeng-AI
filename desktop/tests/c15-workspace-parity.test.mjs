import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { renderChatFirstShell } from '../src/chat-shell.mjs';
import { isWorkspaceRoot } from '../src/workspace-view.mjs';
import {
  C15_PROJECT_ID,
  C15_WORKSPACE_COPY,
  createC15WorkspacePreview,
} from '../src/c15-workspace-preview-fixture.mjs';

const root = resolve(import.meta.dirname, '..');
const [appSource, shellSource, fixtureSource, cssSource, workspaceViewSource] = await Promise.all([
  readFile(resolve(root, 'src/app.mjs'), 'utf8'),
  readFile(resolve(root, 'src/chat-shell.mjs'), 'utf8'),
  readFile(resolve(root, 'src/c15-workspace-preview-fixture.mjs'), 'utf8'),
  readFile(resolve(root, 'src/chat-shell.css'), 'utf8'),
  readFile(resolve(root, 'src/workspace-view.mjs'), 'utf8'),
]);
const fixture = createC15WorkspacePreview();

function renderWork(overrides = {}) {
  return renderChatFirstShell({
    data: fixture,
    native: false,
    selectedConversationId: null,
    selectedWorkProjectId: null,
    composerDraft: '',
    chatSearch: '',
    profileOpen: false,
    sidebarOpen: false,
    railCollapsed: false,
    showArchived: false,
    showDeleted: false,
    pane: 'work',
    workMode: true,
    workPanel: null,
    workspaces: [fixture.summary],
    status: '',
    error: '',
    connection: {},
    ...overrides,
  });
}

test('C15 work entry opens the Android-aligned empty work state, not ordinary chat', () => {
  const html = renderWork();
  assert.ok(html.includes(C15_WORKSPACE_COPY.emptyWorkTitle));
  assert.ok(html.includes('class="workspace-shell"'));
  assert.ok(html.includes('class="workspace-sidebar"'));
  assert.ok(html.includes('work-root-canvas'));
  assert.ok(html.includes('<h1>工作</h1>'));
  assert.ok(!html.includes('C15 普通对话不可进入工作态'));
  assert.ok(!html.includes('id="chat-composer"'));
  assert.ok(!html.includes('data-scroll-owner="message-list"'));
  assert.ok(!html.includes('class="chat-sidebar'));
  assert.match(appSource, /action === 'show-work'[\s\S]{0,180}state\.pane = 'work'/);
  assert.ok(!appSource.includes("action === 'show-work') { state.settingsConversationReturn = null; state.pane = 'knowledge'"));
});

test('C15 selected project exposes one project-scoped draft without creating a fake conversation', () => {
  const html = renderWork({ selectedWorkProjectId: C15_PROJECT_ID });
  assert.ok(html.includes(C15_WORKSPACE_COPY.projectTitle));
  assert.ok(html.includes('暂无工作对话'));
  assert.ok(!html.includes(C15_WORKSPACE_COPY.emptyWorkDetail));
  assert.ok(html.includes('id="chat-composer"'));
  assert.ok(!html.includes('data-scroll-owner="message-list"'));
  assert.ok(!html.includes('C15 普通对话不可进入工作态'));
});

test('C15 work navigation and Settings reuse the same project and knowledge route owners', () => {
  const html = renderWork({ selectedWorkProjectId: C15_PROJECT_ID });
  for (const token of [
    'class="chat-work-nav"',
    'data-action="return-workspace-origin"',
    '返回',
    'data-action="show-work"',
    'data-action="show-projects"',
    'data-action="show-knowledge"',
    'data-action="show-memory"',
  ]) assert.ok(html.includes(token), token);
  for (const forbidden of ['chat-work-projects', 'data-action="new-project"', 'data-action="select-work-project"']) assert.ok(!html.includes(forbidden), forbidden);
  for (const token of [
    "from './workspace-view.mjs'",
    'renderWorkspaceNavigation',
    'renderWorkConversationState',
    "state.pane = 'projects'",
    "state.pane = 'knowledge'",
  ]) assert.ok(`${appSource}\n${shellSource}`.includes(token), token);
  const activeSubmit = appSource.slice(
    appSource.indexOf('async function sendLocalMessage()'),
    appSource.indexOf('async function cancelOrdinaryChat()', appSource.indexOf('async function sendLocalMessage()')),
  );
  assert.ok(activeSubmit.includes("invoke('submit_desktop_ordinary_chat'"));
  assert.ok(activeSubmit.includes("projectId: conversation ? null : state.pane === 'work' ? state.selectedWorkProjectId : null"));
});

test('C15 workspace return restores the settings page that opened it', () => {
  const html = renderWork({ workspaceReturn: { pane: 'settings', settingsSection: 'workspace', label: '返回' } });
  assert.ok(html.includes('data-action="return-workspace-origin"'));
  assert.ok(html.includes('返回'));
  assert.doesNotMatch(workspaceViewSource, /<header><strong>工作区<\/strong><\/header>/);
  assert.doesNotMatch(shellSource, /workspace-rail-title/);
  for (const token of ["target.closest('.android-settings-main')", 'state.workspaceReturn', "destination?.pane === 'settings'", 'state.settingsSection = destination.settingsSection']) assert.ok(appSource.includes(token), token);
});

test('C15 workspace pages use a dedicated workspace shell and keep chat functions out', () => {
  const html = renderWork({
    pane: 'memory',
    workPanel: '<section class="canvas"><h1>本地 Memory</h1></section>',
  });
  for (const token of [
    'class="workspace-shell"',
    'class="workspace-sidebar"',
    'class="workspace-sidebar-pages"',
    'data-action="return-workspace-origin"',
    '返回',
    '本地 Memory',
  ]) assert.ok(html.includes(token), token);
  assert.ok(!html.includes('workspace-rail-title'));
  for (const forbidden of ['class="chat-sidebar', 'id="chat-search"', 'aria-label="对话导航"', 'class="chat-mode-switch"']) assert.ok(!html.includes(forbidden), forbidden);
  assert.ok(!html.includes('class="chat-mode-switch"'));
  assert.ok(!html.includes('chat-temporary-button'));
});

test('C15 workspace root and its peer pages share one standalone shell contract', () => {
  assert.match(shellSource, /const workspaceRootPanel = isWorkspaceRoot\(data, pane, selectedWorkProjectId, selectedConversationId\)/);
  assert.match(shellSource, /const standaloneWorkspacePage = \(showWorkPanel && !utilityPane\) \|\| Boolean\(workspaceRootPanel\)/);
  assert.match(cssSource, /\.workspace-shell \{[^}]*grid-template-columns: var\(--chat-sidebar-width, 256px\) minmax\(0, 1fr\);/s);
  assert.match(cssSource, /\.workspace-standalone-content > \.canvas \{[^}]*border: 0;[^}]*background: transparent;[^}]*box-shadow: none;/s);
});

test('C15 work root claims the same full-width standalone owner as its peer pages', () => {
  assert.equal(isWorkspaceRoot(fixture, 'work', null, null), true);
  assert.equal(isWorkspaceRoot(fixture, 'work', C15_PROJECT_ID, null), false);
  assert.equal(isWorkspaceRoot(fixture, 'settings', null, null), false);
  assert.match(appSource, /const standaloneWorkspacePage = \(!utilityPane && Boolean\(workPanel\)\) \|\| isWorkspaceRoot\(data, visiblePane, state\.selectedWorkProjectId, state\.selectedConversationId\);/);
  assert.match(cssSource, /\.app-shell\.workspace-standalone-shell \{[^}]*display: block;[^}]*height: 100vh;/s);
});

test('C15 workspace sidebars share one vertical navigation rule with centered labels', () => {
  assert.match(cssSource, /\.workspace-sidebar-pages,\s*\.chat-work-primary \{[^}]*grid-template-columns: minmax\(0, 1fr\);/s);
  assert.match(cssSource, /\.workspace-sidebar-pages button,\s*\.chat-work-primary button \{[^}]*display: flex;[^}]*align-items: center;[^}]*justify-content: center;[^}]*text-align: center;/s);
  assert.doesNotMatch(cssSource, /\.chat-work-primary \{[^}]*grid-template-columns: repeat\(4,/s);
  assert.doesNotMatch(workspaceViewSource, /workspace-page-tabs|renderWorkspacePageTabs/);
});

test('C15 workspace sidebars keep the return route visually separate from page navigation', () => {
  assert.match(workspaceViewSource, /class="workspace-return-group"/);
  assert.match(workspaceViewSource, /class="chat-work-return-group"/);
  assert.match(cssSource, /\.chat-work-nav \{[^}]*gap: 18px;/s);
  assert.match(cssSource, /\.chat-work-return-group \{[^}]*padding: 0 0 14px;[^}]*border-bottom:/s);
  assert.match(cssSource, /\.workspace-return-group \{[^}]*padding: 0 0 14px;[^}]*border-bottom:/s);
  assert.doesNotMatch(cssSource, /\.chat-work-nav \{[^}]*gap: 3px;/s);
});

test('C15 Browser fixture is explicit, isolated and routable across only C15 states', () => {
  for (const token of [
    'c15WorkspacePreview',
    'createC15WorkspacePreview',
    "state.pane = 'work'",
    "['workspace', 'development'].includes(c15WorkspacePreview)",
    'state.settingsSection = c15WorkspacePreview',
  ]) assert.ok(appSource.includes(token), token);
  assert.ok(fixtureSource.includes('c15-browser-read-only'));
});
