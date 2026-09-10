import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { renderChatFirstShell } from '../src/chat-shell.mjs';
import {
  C15_PROJECT_ID,
  C15_WORKSPACE_COPY,
  createC15WorkspacePreview,
} from '../src/c15-workspace-preview-fixture.mjs';

const root = resolve(import.meta.dirname, '..');
const [appSource, shellSource, fixtureSource] = await Promise.all([
  readFile(resolve(root, 'src/app.mjs'), 'utf8'),
  readFile(resolve(root, 'src/chat-shell.mjs'), 'utf8'),
  readFile(resolve(root, 'src/c15-workspace-preview-fixture.mjs'), 'utf8'),
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
  assert.ok(html.includes(C15_WORKSPACE_COPY.emptyWorkDetail));
  assert.ok(!html.includes('C15 普通对话不可进入工作态'));
  assert.ok(!html.includes('id="chat-composer"'));
  assert.ok(!html.includes('data-scroll-owner="message-list"'));
  assert.match(appSource, /action === 'show-work'[\s\S]{0,180}state\.pane = 'work'/);
  assert.ok(!appSource.includes("action === 'show-work') { state.settingsConversationReturn = null; state.pane = 'knowledge'"));
});

test('C15 selected project exposes one project-scoped draft without creating a fake conversation', () => {
  const html = renderWork({ selectedWorkProjectId: C15_PROJECT_ID });
  assert.ok(html.includes(C15_WORKSPACE_COPY.projectTitle));
  assert.ok(html.includes('0 个工作对话'));
  assert.ok(html.includes('在此项目中创建第一条工作对话'));
  assert.ok(html.includes('id="chat-composer"'));
  assert.ok(!html.includes('data-scroll-owner="message-list"'));
  assert.ok(!html.includes('C15 普通对话不可进入工作态'));
});

test('C15 work navigation and Settings reuse the same project and knowledge route owners', () => {
  const html = renderWork({ selectedWorkProjectId: C15_PROJECT_ID });
  for (const token of [
    'class="chat-work-nav"',
    'data-action="show-work"',
    'data-action="show-projects"',
    'data-action="show-knowledge"',
    'data-action="show-memory"',
    'data-action="select-work-project"',
    C15_WORKSPACE_COPY.projectTitle,
  ]) assert.ok(html.includes(token), token);
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
