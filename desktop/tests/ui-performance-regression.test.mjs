import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { renderChatFirstShell } from '../src/chat-shell.mjs';

const root = new URL('../', import.meta.url);
const app = await readFile(new URL('src/app.mjs', root), 'utf8');
const rust = await readFile(new URL('src-tauri/src/lib.rs', root), 'utf8');

function rustCommand(name) {
  const asyncMarker = rust.indexOf(`#[tauri::command]\nasync fn ${name}`);
  const syncMarker = rust.indexOf(`#[tauri::command]\nfn ${name}`);
  const marker = asyncMarker === -1 ? syncMarker : asyncMarker;
  assert.notEqual(marker, -1, `${name} command must exist`);
  const start = marker;
  const end = rust.indexOf('\n#[tauri::command]', marker + 1);
  return rust.slice(start, end === -1 ? rust.length : end);
}

const data = { summary: { id: 'performance' }, exchange: { conversations: [{
  id: 'long-chat', title: '长对话', archived: false, revision: 1,
  messages: Array.from({ length: 80 }, (_, index) => ({
    id: `message-${index}`, role: index % 2 ? 'assistant' : 'user', createdAt: '2026-09-12T00:00:00Z',
    blocks: [{ kind: 'TEXT', text: `第 ${index} 条消息` }],
  })),
}] } };

test('steady shell interactions retain an unchanged transcript instead of serializing it again', () => {
  const normal = renderChatFirstShell({ data, native: true, selectedConversationId: 'long-chat', pane: 'chat', status: '', error: '', connection: {} });
  const retained = renderChatFirstShell({ data, native: true, selectedConversationId: 'long-chat', pane: 'chat', status: '', error: '', connection: {}, preserveTranscript: true });
  assert.match(normal, /data-message-id="message-79"/);
  assert.match(normal, /data-message-index="79"/);
  assert.doesNotMatch(normal, /data-preserved-transcript-slot/);
  assert.match(retained, /data-preserved-transcript-slot/);
  assert.doesNotMatch(retained, /data-message-id="message-79"/);
  assert.match(retained, /chat-transcript-rail/);
});

test('high-frequency inputs and transcript tracking avoid a full render per native event', () => {
  const inputOwner = app.slice(app.lastIndexOf("app.addEventListener('input'", app.indexOf('function composerHasFileTransfer')), app.indexOf('function composerHasFileTransfer'));
  assert.match(app, /let settingsSearchRenderFrame = null/);
  assert.match(inputOwner, /scheduleSettingsSearchRender\(\)/);
  assert.match(inputOwner, /scheduleConversationFindRender\(\)/);
  assert.doesNotMatch(inputOwner, /event\.target\.id === 'settings-search'[\s\S]{0,120}render\(\)/);
  assert.doesNotMatch(inputOwner, /event\.target\.id === 'conversation-find-input'[\s\S]{0,180}render\(\)/);
  assert.match(app, /function canRetainTranscript/);
  assert.match(app, /retained-transcript/);
  assert.match(app, /preserveTranscript: Boolean\(retainedTranscript\)/);
  assert.match(app, /function scheduleTranscriptRailSync/);
  const scrollOwner = app.slice(app.indexOf("app.addEventListener('scroll'"), app.indexOf('let sidebarDragPointerId'));
  assert.match(scrollOwner, /scheduleTranscriptRailSync\(scroll\)/);
  assert.doesNotMatch(scrollOwner, /syncTranscriptRailToScroll\(scroll\)/);
});

test('destructive confirmation, sidebar drag, image zoom and retained shells stay off the full-render hot path', () => {
  const privacyInput = app.slice(app.indexOf("if (event.target?.id !== 'privacy-confirmation'"), app.indexOf("app.addEventListener('click'", app.indexOf("if (event.target?.id !== 'privacy-confirmation'")));
  assert.match(privacyInput, /confirm\.disabled = event\.target\.value !== '删除全部本地业务数据'/);
  assert.doesNotMatch(privacyInput, /render\(\)/);
  const sidebarOwner = app.slice(app.indexOf('let sidebarDragPointerId'), app.indexOf("app.addEventListener('click', event => {\n  if (event.target.closest('[data-action]')?.dataset.action !== 'scroll-to-latest')"));
  assert.match(sidebarOwner, /function finishSidebarDrag/);
  assert.match(sidebarOwner, /app\.addEventListener\('pointercancel', finishSidebarDrag\)/);
  assert.match(sidebarOwner, /function updateSidebarWidth\(width\)[\s\S]{0,100}const persist = Boolean\(arguments\[1\]\?\.persist\)/);
  assert.doesNotMatch(sidebarOwner, /renderMessageCopyFeedback/);
  const wheelOwner = app.slice(app.indexOf("app.addEventListener('wheel'"), app.indexOf('async function openPdfPreview'));
  assert.match(wheelOwner, /applyImagePreviewTransform\(viewport\)/);
  assert.doesNotMatch(wheelOwner, /render\(\)/);
  assert.doesNotMatch(app, /scheduleImageThumbnailReads\(\{ composerOnly: Boolean\(retainedTranscript\) \}\)/);
  assert.match(app, /scheduleImageThumbnailReads\(\);\s*scheduleVideoThumbnailReads/);
});

test('composer typing batches local persistence and preserves the current draft before submit', () => {
  const ordinaryOwner = app.slice(app.indexOf('let ordinaryComposerDraftQueue'), app.indexOf('async function enterTemporaryChat'));
  assert.match(ordinaryOwner, /let ordinaryComposerDraftTimer = null/);
  assert.match(ordinaryOwner, /void persistOrdinaryComposerDraft\(scheduled\);\n  \}, 180\)/);
  assert.match(ordinaryOwner, /async function flushOrdinaryComposerDraft\(\)[\s\S]{0,400}persistOrdinaryComposerDraft\(scheduled\)/);
  const writeOwner = ordinaryOwner.slice(ordinaryOwner.indexOf('function writeChatDraft'), ordinaryOwner.indexOf('async function enterTemporaryChat'));
  assert.doesNotMatch(writeOwner, /localStorage\.(setItem|removeItem)/);
  assert.doesNotMatch(writeOwner, /persistOrdinaryComposerDraft\(/);
  const temporaryOwner = app.slice(app.indexOf('let temporaryDraftQueue'), app.indexOf('async function updateTemporaryModelOverride'));
  assert.match(temporaryOwner, /let temporaryDraftTimer = null/);
  assert.match(temporaryOwner, /let temporaryDraftQueue = Promise\.resolve\(\)/);
  assert.match(temporaryOwner, /async function flushTemporaryDraft\(\)/);
  const composerPersistenceInput = app.slice(app.lastIndexOf("app.addEventListener('input'"), app.indexOf("app.addEventListener('change'", app.lastIndexOf("app.addEventListener('input'")));
  assert.match(composerPersistenceInput, /scheduleTemporaryDraft\(state\.composerDraft\)/);
  assert.doesNotMatch(composerPersistenceInput, /updateTemporaryDraft\(state\.composerDraft\)/);
});

test('closed row menus do not make ordinary scrolling scan every card', async () => {
  const layer = await readFile(new URL('../src/modal-layer-owner.mjs', import.meta.url), 'utf8');
  assert.match(layer, /const openMenus = new Set\(\)/);
  assert.match(layer, /for \(const menu of openMenus\)/);
  const scrollOwner = layer.slice(layer.indexOf("document.addEventListener('scroll'"));
  assert.doesNotMatch(scrollOwner, /root\.querySelectorAll\(selector\)/);
  assert.match(layer, /if \(resizeFrame !== null\) return/);
});

test('assistant answer menu survives residual transcript scroll and closes on a fresh wheel gesture', () => {
  const scrollOwner = app.slice(app.indexOf("window.addEventListener('scroll'"), app.indexOf('restoreTemporaryChat()', app.indexOf("window.addEventListener('scroll'")));
  assert.doesNotMatch(scrollOwner, /state\.assistantMessageMenu/);
  const wheelStart = app.indexOf("document.addEventListener('wheel'");
  const wheelOwner = app.slice(wheelStart, app.indexOf("document.addEventListener('keydown'", wheelStart));
  assert.match(wheelOwner, /state\.assistantMessageMenu/);
  assert.match(wheelOwner, /\.chat-scroll\[data-conversation-id\]/);
  assert.match(wheelOwner, /closeTopOverlay\(\{ restoreFocus: false \}\)/);
  const renderOwner = app.slice(app.lastIndexOf('function render()'), app.indexOf('function cameraCaptureDialog'));
  assert.match(renderOwner, /if \(!retainedTranscript \|\| state\.pendingChatScrollToLatestId \|\| state\.pendingChatSendScrollToLatestId\) restoreChatScroll\(\)/);
});

test('opening the assistant answer menu never scrolls the preserved transcript to reveal its focused item', () => {
  const renderOwner = app.slice(app.indexOf('function renderUnified()'), app.indexOf('function cameraCaptureDialog'));
  assert.match(renderOwner, /else if \(state\.assistantMessageMenu\) document\.querySelector\('\.assistant-message-menu \[role="menuitem"\]'\)\?\.focus\(\{ preventScroll: true \}\)/);
});

test('assistant answer menu opens on primary pointerdown while click is keyboard-only fallback', async () => {
  assert.match(app, /const assistantMessageMenuTriggers = new WeakSet\(\)/);
  assert.match(app, /function bindAssistantMessageMenuTriggers\(\)/);
  assert.match(app, /button\.addEventListener\('pointerdown', event => \{[\s\S]*?event\.button !== 0[\s\S]*?toggleAssistantMessageMenu\(button\);/);
  assert.match(app, /button\.addEventListener\('click', event => \{[\s\S]*?event\.detail === 0[\s\S]*?toggleAssistantMessageMenu\(button\);/);
  const renderStart = app.lastIndexOf('app.innerHTML = renderChatFirstShell');
  const renderCall = app.slice(renderStart, app.indexOf('if (retainedTranscript)', renderStart));
  assert.match(renderCall, /assistantMessageMenu: state\.assistantMessageMenu/);
  const css = await readFile(new URL('src/chat-shell.css', root), 'utf8');
  assert.match(css, /\.chat-message-action-more svg \{[^}]*pointer-events: none;/);
});

test('search, startup projections and preview reads never run blocking work on the AppKit thread', () => {
  assert.match(rust, /async fn run_desktop_store_blocking/);
  assert.match(rust, /tauri::async_runtime::spawn_blocking/);
  for (const name of [
    'list_desktop_workspaces',
    'read_desktop_workspace',
    'search_desktop_local_index',
    'catalog_desktop_local_index',
    'read_desktop_local_search_history',
    'read_desktop_pdf_preview',
    'read_desktop_video_preview',
    'read_desktop_audio_preview',
    'read_desktop_text_preview',
    'read_desktop_local_backup_status',
    'read_desktop_privacy_inventory',
  ]) {
    const command = rustCommand(name);
    assert.match(command, new RegExp(`async fn ${name}`));
    assert.match(command, /run_desktop_store_blocking/);
  }
  const localIndexQuery = rustCommand('query_desktop_local_index');
  assert.match(localIndexQuery, /run_desktop_store_paths_blocking/);
  assert.doesNotMatch(localIndexQuery, /run_desktop_store_blocking\(/);
  assert.match(localIndexQuery, /query_local_index_at\(&paths\.root, &paths\.database/);
  assert.match(rustCommand('read_desktop_usage_ledger'), /async fn read_desktop_usage_ledger[\s\S]*spawn_blocking/);
  for (const name of [
    'read_desktop_conversation_draft',
    'read_desktop_workbench_history',
    'read_desktop_p6g_selection',
  ]) {
    const command = rustCommand(name);
    assert.match(command, new RegExp(`async fn ${name}`));
    assert.match(command, /run_desktop_store_blocking/);
  }
  assert.match(rust, /async fn run_desktop_store_paths_blocking/);
  const imagePreview = rustCommand('read_desktop_image_preview');
  assert.match(imagePreview, /run_desktop_store_paths_blocking/);
  assert.match(imagePreview, /read_desktop_image_preview_from_paths/);
  assert.doesNotMatch(imagePreview, /run_desktop_store_blocking\(/);
  const videoThumbnail = rustCommand('read_desktop_video_thumbnail');
  assert.match(videoThumbnail, /run_desktop_store_paths_blocking/);
  assert.doesNotMatch(videoThumbnail, /run_desktop_store_blocking\(/);
  const searchOwner = app.slice(app.indexOf('async function runFullSearch('), app.indexOf('function scheduleFullSearch()'));
  assert.match(searchOwner, /withFullSearchDeadline\(pending\)/);
});

test('video thumbnail hydration is bounded so background previews cannot starve imported chat actions', () => {
  const owner = app.slice(app.indexOf('let videoThumbnailObserver'), app.indexOf('function hydrateVideoThumbnail'));
  assert.match(owner, /const MAX_VIDEO_THUMBNAIL_READS = 2/);
  assert.match(owner, /videoThumbnailReadQueue/);
  assert.match(owner, /videoThumbnailReadsInFlight < MAX_VIDEO_THUMBNAIL_READS/);
  assert.match(owner, /videoThumbnailGeneration/);
});
