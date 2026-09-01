import { p8InspectCanvas } from './p8-inspect.mjs';
import { activeConversations, clampDesktopSidebarWidth, messagePlainText, renderChatFirstShell, resolveConversation, resolveConversationMenuAnchor } from './chat-shell.mjs';
import { beginConversationRecycle, completeConversationRecycle, failConversationRecycle } from './recycle-confirmation.mjs';
import { DesktopParityPreferences, appearanceProjection, assistantMessageMarkdown, conversationFindMatches as findConversationMatches, conversationMarkdown, normalizeAppearance, normalizeProductSettings } from './desktop-parity-preferences.mjs';
import { MODEL_SERVICE_PREVIEW_SETTINGS } from './android-settings-shell.mjs';
import { bestSearchHistoryMatch } from './desktop-search-page.mjs';
import { renderDesktopTranscriptionPage, TRANSCRIPTION_PREVIEW_STATE } from './desktop-transcription-page.mjs';
import { renderDesktopRemindersPage } from './desktop-reminders-page.mjs';
import { reminderNotificationAction, reminderNotificationExtra } from './reminder-notification-routing.mjs';

const app = document.querySelector('#app');
window.addEventListener('error', event => { console.error('Desktop runtime error', event.error || event.message); });
window.addEventListener('unhandledrejection', event => { console.error('Desktop async runtime error', event.reason); });
const tauriBridge = window.__TAURI__?.core ?? window.__TAURI_INTERNALS__;
const native = Boolean(tauriBridge?.invoke);
const fixture = { summary: { id: 'workspace-preview-01', title: 'P6-D 本地预览', semanticHash: 'local-preview…', packageHash: 'read-only…', projectCount: 1, conversationCount: 1, knowledgeCount: 1, memoryCount: 1, relationCount: 0, assetCount: 0, assetByteCount: 0, highSensitive: false }, exchange: { projects: [{ id: 'project-preview-01', title: '本地项目', description: '浏览器预览不会写入 Desktop SQLite。', pinned: true, archived: false, revision: 1 }], conversations: [{ id: 'conversation-preview-01', title: '本地会话', revision: 1, messages: [{ id: 'message-preview-01', role: 'user', createdAt: '2026-08-13T00:00:00Z', blocks: [{ kind: 'TEXT', text: '本地文本记录；不会执行 Markdown、HTML 或代码。' }] }] }], knowledge: [{ id: 'knowledge-preview-01', title: '安全知识', body: '编辑、撤销与软删除只在 Tauri Desktop 的 Rust SQLite 中执行。', tags: ['local'], status: 'ACTIVE', scope: 'GLOBAL', revision: 1, classification: 'NORMAL' }], memory: [{ id: 'memory-preview-01', body: '本地 Memory 仅作文本 IR。', scope: 'GLOBAL', status: 'ACTIVE', revision: 1 }], relations: [] } };
const previewConnection = { connection: 'ONLINE_CONFIGURATION_REQUIRED', providerConfiguration: 'NOT_CONFIGURED', credentialPresence: 'MISSING', catalogFreshness: 'NOT_AVAILABLE', egressConsent: 'REQUIRED_PER_INTENT', syncCapability: 'ENCRYPTED_SYNC_NOT_CONFIGURED', degradedReasons: ['NO_CREDENTIAL', 'CATALOG_UNAVAILABLE', 'NETWORK_UNVERIFIED', 'EGRESS_CONSENT_REQUIRED', 'SYNC_NOT_CONFIGURED'] };
const settingsLocalKey = 'nanfeng-ai.desktop.settings.sidebar-width.v1';
const parityPreferences = new DesktopParityPreferences(window.localStorage);
const systemDarkQuery = window.matchMedia?.('(prefers-color-scheme: dark)');
function readSidebarWidth() { try { return clampDesktopSidebarWidth(Number(localStorage.getItem(settingsLocalKey)), window.innerWidth); } catch { return 256; } }
function persistSidebarWidth(width) { try { localStorage.setItem(settingsLocalKey, String(width)); } catch { /* browser preview may deny local storage */ } }
const initialProductSettings = parityPreferences.readProductSettings();
const state = { workspaces: [], current: null, pane: 'chat', selectedConversationId: null, chatgptTask: null, claudeTask: null, p6kTask: null, composerDraft: '', composerAttachments: [], temporaryConversation: null, profileOpen: false, sidebarOpen: false, railCollapsed: false, sidebarWidth: readSidebarWidth(), settingsSection: 'personalization', settingsSearch: '', settingsPicker: null, productSettings: initialProductSettings, personalizationDraft: { ...initialProductSettings }, personalizationDirty: false, modelServiceSettings: MODEL_SERVICE_PREVIEW_SETTINGS.map(item => ({ ...item, presets: [...item.presets], nonChatCapabilities: [...item.nonChatCapabilities] })), modelProviderId: 'OPENROUTER', modelServiceDraft: null, modelCredentialDraft: null, modelCredentialEdited: false, modelCredentialVisible: false, modelSettingsSaving: false, modelSettingsTesting: false, modelSettingsNotice: '', modelSettingsError: '', usageLedger: { records: [], inputTokens: 0, outputTokens: 0, cachedInputTokens: 0 }, usageSection: 'conversation', contextSelectionRecords: [], diagnosticRecords: [], invocationRecords: [], privacyInventory: { totalBytes: 0, aggregates: [] }, localBackup: { working: false, preflight: null, replaceLocal: false, notice: '', error: '', restartRequired: false, interrupted: false }, showArchived: false, showDeleted: false, contextMenu: null, composerAddOpen: false, temporaryModelOpen: false, p6gModelPickerOpen: false, p6gCatalog: null, p6gGlobalDefault: { revision: 0, tier: null }, p6gSelection: null, chatScrollPositions: new Map(), chatAtLatest: true, transcriptRailTrackingConversationId: null, pendingChatScrollToLatestId: null, pendingChatSendScrollToLatestId: null, scrollToLatestAnimationId: null, focusComposerAfterScrollToLatest: false, inspectorOpen: true, treeOpen: false, preflight: null, dialog: null, history: { canUndo: false, canRedo: false, recycleBin: [], modelMetadata: [] }, agentRuns: [], connection: previewConnection, p6eAcceptance: { enabled: false, receipt: null }, status: native ? '本地工作区已就绪；联网模型尚未配置。' : 'Web 预览不会读写 Desktop 数据库。', error: '', scale: 1, searchResults: [], searchPage: { hits: [], textCount: 0, attachmentCount: 0, truncated: false }, searchPanel: false, searchCategory: 'all', searchSortMode: 'default', searchFileType: 'all', searchFileTypeOpen: false, searchLoading: false, searchError: '', searchAnchorMessageId: null, searchAnchorAttachmentId: null, searchHistory: [], searchHistoryOpen: false, searchHistoryHighlighted: null, searchHistoryManuallyOpened: false, searchScrollSnapshot: null, searchAttachmentMenu: null, suppressSearchHistoryFocus: false, imageThumbnails: {}, imageThumbnailPending: new Set(), imagePreview: null, pdfPreview: null, videoPreview: null, audioPreview: null, textPreview: null, previewWorkspaceId: null, appearance: parityPreferences.readAppearance(), favoriteConversationIds: new Set(), conversationFindOpen: false, conversationFindQuery: '', conversationFindMatches: [], conversationFindIndex: 0, runtimeInfo: { version: '读取中', platform: navigator.platform || 'Desktop', arch: '本机架构' } };
state.p6gModelPickerTier = null;

state.modelCredentialDraft = '';
state.transcription = { settings: { ...TRANSCRIPTION_PREVIEW_STATE.settings }, tasks: [] };
state.transcriptionMode = 'document';
state.selectedTranscriptionTaskId = null;
state.transcriptionBusyTaskId = null;
state.searchLocatedArchivedConversationId = null;
state.searchReturnActive = false;
state.appSettingsRevision = 0;
state.settingsCapabilities = { ordinaryChatPersonalization: false, historyLibrary: false, monitorNotifications: false, reminderSuggestions: false, unreadIndicators: false, webSearch: false, googleAccountSync: false, updateService: false };
state.accountSync = { configured: false, state: 'NOT_CONFIGURED', recoveryState: 'UNAVAILABLE', periodicEnabled: false, rotationPending: false, selectedConversationCount: 0, diagnostics: [], notifications: [] };
state.accountRecovery = null;
state.historyKnowledge = { enabled: false, paused: true, lastDispatchedAtMs: null, nextEligibleAtMs: null, candidates: [] };
state.settingsMobileHome = false;
state.settingsScrollPositions = new Map();
state.settingsConversationReturn = null;
state.memorySummaryQuery = '';
state.memorySummaryComposer = '';
state.memorySummaryNotice = '';
state.sidebarScrollTop = 0;
state.reminders = { drafts: [], plans: [], diagnostics: [] };
state.reminderNotificationPermission = 'default';
state.reminderNotificationBridge = { supported: false, initialized: false, listenerReady: false, safeCode: 'NOT_READ', pendingActionCount: 0 };
state.selectedReminderPlanId = null;
state.backgroundRuntime = { desiredEnabled: false, installed: false, safeCode: 'NOT_READ', label: '', updatedAtMs: 0 };
state.conversationReadState = { workspaceId: null, conversations: [] };
state.unreadConversationIds = new Set();
state.manualUnreadAtMs = new Map();
state.pendingCreatedConversationRouteWorkspaceId = null;

const tauriEvents = window.__TAURI__?.event;
if (native && tauriEvents?.listen) {
  void tauriEvents.listen('desktop-ordinary-chat-runtime-v1', async event => {
    const payload = event?.payload || {};
    if (!payload.workspaceId || state.current?.summary?.id !== payload.workspaceId) return;
    try {
      state.current = await invoke('read_desktop_workspace', { workspaceId: payload.workspaceId });
      if (!state.selectedConversationId && state.pendingCreatedConversationRouteWorkspaceId === payload.workspaceId) {
        state.selectedConversationId = payload.conversationId || null;
        state.pendingCreatedConversationRouteWorkspaceId = null;
      }
      if (state.selectedConversationId === payload.conversationId) state.pendingChatSendScrollToLatestId = payload.conversationId || null;
      await loadConversationReadState({ observeSelected: true });
      state.error = '';
      render();
    } catch (error) {
      state.error = `聊天增量已持久化，但界面刷新失败：${String(error)}`;
      render();
    }
  });
  void tauriEvents.listen('desktop-reminder-runtime-v1', async () => {
    try {
      await loadDesktopReminders();
      await loadDesktopUsageLedger();
      await flushReminderNotifications();
      state.error = '';
    } catch (error) {
      state.error = `提醒运行结果已保存在本机，但界面或通知回执未刷新：${String(error)}`;
    }
    render();
  });
}

function applyAppearance() {
  const projection = appearanceProjection(state.appearance, systemDarkQuery?.matches);
  const root = document.documentElement;
  root.dataset.appearanceMode = projection.dark ? 'dark' : 'light';
  root.dataset.fontSize = projection.appearance.fontSize;
  root.dataset.themeColor = projection.appearance.themeColor;
  root.style.setProperty('--app-font-scale', String(projection.fontScale));
  root.style.setProperty('--accent-orange', projection.theme.accent);
  root.style.setProperty('--accent-orange-hover', projection.theme.hover);
  root.style.setProperty('--accent-orange-pressed', projection.theme.pressed);
  root.style.setProperty('--accent-orange-soft', projection.dark ? 'color-mix(in srgb, var(--accent-orange) 26%, #252a27)' : projection.theme.soft);
  root.style.setProperty('--accent-subtle-border', projection.theme.border);
  root.style.setProperty('--user-message-bubble', projection.dark ? 'color-mix(in srgb, var(--accent-orange) 24%, #262b28)' : projection.theme.bubble);
}

function reloadFavoriteConversationIds() {
  const workspaceId = state.current?.summary?.id;
  if (!workspaceId) { state.favoriteConversationIds = new Set(); return; }
  if (!native) { state.favoriteConversationIds = parityPreferences.readFavoriteConversationIds(workspaceId); return; }
  void invoke('read_desktop_favorite_conversation_ids', { workspaceId }).then(ids => {
    state.favoriteConversationIds = new Set(ids);
    render();
  }).catch(error => {
    state.error = `收藏会话读取失败：${String(error)}`;
    render();
  });
}

function currentConversation() {
  return resolveConversation(workspace(), state.selectedConversationId);
}

function activeRuntimeConversation(data, preferredConversationId = null) {
  const conversations = data?.exchange?.conversations || [];
  const preferred = preferredConversationId ? conversations.find(item => item.id === preferredConversationId) : null;
  if (preferred) return preferred;
  return conversations
    .filter(conversation => (conversation.messages || []).some(message => message.delivery === 'PARTIAL' && message.attemptId))
    .sort((left, right) => String(right.updatedAt || '').localeCompare(String(left.updatedAt || '')))[0] || null;
}

async function refreshDesktopRuntimeWhilePending(workspaceId, preferredConversationId, isSettled) {
  let discoveredConversationId = preferredConversationId;
  while (!isSettled()) {
    await new Promise(resolve => window.setTimeout(resolve, 120));
    if (isSettled() || state.current?.summary?.id !== workspaceId) continue;
    try {
      const current = await invoke('read_desktop_workspace', { workspaceId });
      const active = activeRuntimeConversation(current, preferredConversationId);
      state.current = current;
      if (!discoveredConversationId && active) {
        discoveredConversationId = active.id;
        if (!state.selectedConversationId && state.pendingCreatedConversationRouteWorkspaceId === workspaceId) {
          state.selectedConversationId = active.id;
          state.pendingCreatedConversationRouteWorkspaceId = null;
        }
      }
      render();
    } catch { /* final command result owns user-visible errors */ }
  }
}

function recomputeConversationFind({ resetIndex = false } = {}) {
  state.conversationFindMatches = findConversationMatches(currentConversation(), state.conversationFindQuery);
  if (resetIndex) state.conversationFindIndex = 0;
  else state.conversationFindIndex = Math.min(state.conversationFindIndex, Math.max(0, state.conversationFindMatches.length - 1));
}

function focusCurrentFindMatch() {
  queueMicrotask(() => {
    const input = document.querySelector('#conversation-find-input');
    input?.focus({ preventScroll: true });
    const match = state.conversationFindMatches[state.conversationFindIndex];
    if (match) document.querySelector(`[data-message-id="${CSS.escape(match.messageId)}"]`)?.scrollIntoView({ block: 'center', behavior: 'smooth' });
  });
}

function safeMarkdownName(value) {
  const clean = String(value || '南枫 AI 会话').replace(/[\\/:*?"<>|\r\n]+/g, ' ').replace(/\s+/g, ' ').trim();
  return `${clean.slice(0, 80) || '南枫 AI 会话'}.md`;
}

async function saveMarkdown(markdown, defaultPath, label) {
  if (!native) { state.status = 'Web 预览不会写文件；请在 Desktop 应用中使用系统保存窗口。'; render(); return; }
  if (!markdown) { state.error = `${label}没有可导出的安全正文。`; render(); return; }
  const selectedPath = await dialogInvoke('save', { defaultPath, filters: [{ name: 'Markdown', extensions: ['md'] }] });
  if (!selectedPath) return;
  try {
    const receipt = await invoke('write_desktop_markdown_to_selected_path', { args: { selectedPath, markdown } });
    state.status = `${label}已导出并回读校验：${bytes(receipt.byteCount)} · ${short(receipt.sha256)}`;
    state.error = '';
  } catch (error) {
    state.error = `${label}导出失败：${String(error)}`;
  }
  render();
}

async function persistNativeAppSettings(appearance, product) {
  if (!native) {
    state.appearance = parityPreferences.writeAppearance(appearance);
    state.productSettings = parityPreferences.writeProductSettings(product);
    return;
  }
  const projection = await invoke('save_desktop_app_settings', { args: { appearance: normalizeAppearance(appearance), product: normalizeProductSettings(product), expectedRevision: state.appSettingsRevision } });
  state.appearance = normalizeAppearance(projection.appearance);
  state.productSettings = normalizeProductSettings(projection.product);
  state.appSettingsRevision = Number(projection.revision || 0);
  state.settingsCapabilities = projection.capabilities || state.settingsCapabilities;
  parityPreferences.clearNativeAppSettingsMigrationSource();
}

async function updateAppearance(field, value) {
  const previous = state.appearance;
  const next = normalizeAppearance({ ...previous, [field]: value });
  state.appearance = next;
  applyAppearance();
  render();
  try {
    await persistNativeAppSettings(next, state.productSettings);
    state.status = '外观偏好已由 Desktop SQLite 保存并立即应用。';
    state.error = '';
  } catch (error) {
    state.appearance = previous;
    applyAppearance();
    state.error = `外观偏好未保存：${String(error)}`;
  }
  render();
}

async function toggleFavoriteConversation(conversationId) {
  const workspaceId = state.current?.summary?.id;
  if (!workspaceId || !conversationId) return;
  const next = new Set(state.favoriteConversationIds);
  const wasFavorite = next.delete(conversationId);
  if (!wasFavorite) next.add(conversationId);
  try {
    const ids = native
      ? await invoke('set_desktop_conversation_favorite', { workspaceId, conversationId, favorite: !wasFavorite })
      : [...parityPreferences.writeFavoriteConversationIds(workspaceId, next)];
    state.favoriteConversationIds = new Set(ids);
    state.status = wasFavorite ? '会话已取消收藏。' : '会话已收藏；归档或删除时会自动取消。';
    state.error = '';
  } catch (error) {
    state.error = `会话收藏状态未更新：${String(error)}`;
  }
  state.contextMenu = null;
  render();
}

function removeFavoriteConversation(conversationId) {
  if (!state.favoriteConversationIds.has(conversationId) || !state.current?.summary?.id) return;
  const next = new Set(state.favoriteConversationIds);
  next.delete(conversationId);
  const workspaceId = state.current.summary.id;
  state.favoriteConversationIds = next;
  if (native) {
    void invoke('set_desktop_conversation_favorite', { workspaceId, conversationId, favorite: false }).then(ids => {
      state.favoriteConversationIds = new Set(ids);
      render();
    }).catch(error => {
      state.error = `会话取消收藏未完成：${String(error)}`;
      render();
    });
  } else {
    state.favoriteConversationIds = parityPreferences.writeFavoriteConversationIds(workspaceId, next);
  }
}

applyAppearance();
systemDarkQuery?.addEventListener?.('change', () => { if (state.appearance.mode === 'system') { applyAppearance(); render(); } });
state.v2CommittedExchanges = [];
globalThis.__nanfengV2CommittedExchanges = state.v2CommittedExchanges;
let p6kManualLink = { assetOrdinal: null, conversationId: null, messageId: null };
globalThis.__nanfengP6kManualLink = p6kManualLink;
async function executeDesktopCompare() {
  state.p6gModelPickerOpen = false;
  state.dialog = null;
  const text = state.composerDraft.trim();
  if (!text && !state.composerAttachments.length) { state.error = '请输入文字或保留附件后再对比。'; render(); return; }
  if (!native || !state.current || state.temporaryConversation) { state.error = native ? 'Compare 只在普通本地会话中可用。' : 'Web 预览不会执行 Compare。'; render(); return; }
  const conversation = currentConversation();
  const routeAtSubmit = state.selectedConversationId;
  const draftKey = chatDraftKey();
  const sentDraft = state.composerDraft;
  const sentAttachments = [...state.composerAttachments];
  try {
    const workspaceId = state.current.summary.id;
    if (!conversation) state.pendingCreatedConversationRouteWorkspaceId = workspaceId;
    const execution = invoke('submit_desktop_compare', { args: {
      workspaceId,
      conversationId: conversation?.id || null,
      expectedRevision: conversation?.revision ?? null,
      text,
      attachmentIds: state.composerAttachments.map(item => item.id),
    } });
    if (draftKey) window.localStorage.removeItem(draftKey);
    state.composerDraft = ''; state.composerAttachments = []; writeComposerAttachments();
    state.error = '';
    state.status = 'Compare 已提交：OpenRouter · GPT-5.6 Terra + Claude Sonnet 5 正在并发生成。';
    render();
    let settled = false;
    const refreshLoop = refreshDesktopRuntimeWhilePending(workspaceId, conversation?.id || null, () => settled);
    let result;
    try { result = await execution; } finally { settled = true; await refreshLoop; }
    const routeStillOwned = routeAtSubmit
      ? state.selectedConversationId === routeAtSubmit
      : state.selectedConversationId === result.conversationId
        || (!state.selectedConversationId && state.pendingCreatedConversationRouteWorkspaceId === workspaceId);
    if (routeStillOwned) {
      state.selectedConversationId = result.conversationId;
      state.pendingChatSendScrollToLatestId = result.conversationId;
    }
    if (state.pendingCreatedConversationRouteWorkspaceId === workspaceId) state.pendingCreatedConversationRouteWorkspaceId = null;
    state.status = result.state === 'COMPLETED' ? 'Compare 两个分支均已完成并写入用量账本。'
      : result.state === 'UNKNOWN' ? 'Compare 至少一个分支结果未知；未自动重发。'
      : result.state === 'FAILED' ? 'Compare 两个分支均失败；可分别明确重试。'
      : result.state === 'CANCELLED' ? 'Compare 已停止；已生成内容保留。'
      : 'Compare 已结束；两个分支结果分别保留。';
    await loadDesktopContextRecords(); await loadDesktopDiagnosticRecords(); await refresh();
  } catch (error) {
    if (state.pendingCreatedConversationRouteWorkspaceId === state.current?.summary?.id) state.pendingCreatedConversationRouteWorkspaceId = null;
    state.composerDraft = sentDraft; state.composerAttachments = sentAttachments; writeComposerAttachments();
    state.error = `Compare 未提交：${String(error)}`;
    render();
  }
}
state.nanfengKnowledgeTask = null;
let p6hDiagnosticsEnabled = false;
function p6hDiagnosticMarker(event) {
  if (!p6hDiagnosticsEnabled) return;
  const divider = event.target.closest?.('.chat-sidebar-divider');
  if (!divider && !event.target.closest?.('.chat-profile-menu')) return;
  const target = event.target.closest('[data-action]');
  let marker = document.querySelector('#p6h-dom-event-marker');
  if (!marker) { marker = document.createElement('p'); marker.id = 'p6h-dom-event-marker'; marker.setAttribute('role', 'status'); marker.style.cssText = 'position:fixed;z-index:9999;left:12px;bottom:12px;padding:6px;background:#173;color:#fff;font-size:12px'; document.body.append(marker); }
  marker.textContent = divider
    ? `P6-H acceptance divider ${event.type}: ${state.sidebarWidth}px`
    : `P6-H acceptance DOM ${event.type}: ${target?.dataset.action || event.target.tagName}`;
}
app.addEventListener('pointerdown', p6hDiagnosticMarker, true);
app.addEventListener('click', p6hDiagnosticMarker, true);
app.addEventListener('keydown', p6hDiagnosticMarker, true);
state.chatSearch = '';
let overlayFocusReturn = null;
function rememberOverlayTrigger(target) { overlayFocusReturn = target?.dataset?.action || null; }
function restoreOverlayFocus() { const action = overlayFocusReturn; overlayFocusReturn = null; if (action) queueMicrotask(() => document.querySelector(`[data-action="${action}"]`)?.focus()); }
/** Single owner for app-owned transient layers; native system pickers intentionally remain outside it. */
function closeTopOverlay({ restoreFocus = true } = {}) {
  if (state.dialog?.kind === 'local-backup-restart-required') return false;
  if (state.contextMenu) state.contextMenu = null;
  else if (state.temporaryModelOpen) state.temporaryModelOpen = false;
  else if (state.p6gModelPickerOpen) { state.p6gModelPickerOpen = false; state.p6gModelPickerTier = null; }
  else if (state.composerAddOpen) state.composerAddOpen = false;
  else if (state.profileOpen) state.profileOpen = false;
  else if (state.dialog) state.dialog = null;
  else if (state.imagePreview) state.imagePreview = null;
  else if (state.pdfPreview) state.pdfPreview = null;
  else if (state.sidebarOpen) state.sidebarOpen = false;
  else return false;
  render();
  if (restoreFocus) restoreOverlayFocus();
  return true;
}
function openTransientOverlay(kind, target, value = true) {
  state.contextMenu = kind === 'context' ? value : null;
  state.composerAddOpen = kind === 'composer-add';
  state.temporaryModelOpen = kind === 'temporary-model';
  state.p6gModelPickerOpen = kind === 'p6g-model-picker';
  if (kind === 'p6g-model-picker') state.p6gModelPickerTier = null;
  if (kind === 'p6g-model-picker' && state.p6gSelection) state.p6gSelection = { ...state.p6gSelection, pickerTier: null };
  state.profileOpen = kind === 'profile';
  rememberOverlayTrigger(target);
  render();
}
app.addEventListener('click', event => {
  const target = event.target.closest?.('[data-action]');
  if (event.target.classList?.contains('scrim')) {
    // A scrim is always cancel-only. It never invokes a destructive confirmation action.
    closeTopOverlay();
    return;
  }
  if (target?.dataset.action === 'toggle-profile' && !state.profileOpen) {
    // The legacy profile trigger remains a simple toggle, but it must replace any lower overlay.
    state.contextMenu = null;
    state.composerAddOpen = false;
    rememberOverlayTrigger(target);
  }
}, true);

app.addEventListener('click', event => {
  const target = event.target.closest?.('[data-action]');
  const action = target?.dataset.action;
  if (!['review-history-knowledge', 'accept-history-knowledge', 'reject-history-knowledge', 'retry-history-knowledge', 'delete-history-knowledge'].includes(action)) return;
  event.preventDefault();
  event.stopImmediatePropagation();
  const candidateId = target.dataset.id;
  const candidate = state.historyKnowledge.candidates?.find(item => item.candidateId === candidateId);
  if (action === 'review-history-knowledge') {
    if (candidate) state.dialog = { kind: 'history-knowledge-review', item: candidate };
    render();
    return;
  }
  void (async () => {
    try {
      if (action === 'accept-history-knowledge') {
        const tags = (document.querySelector('#history-candidate-tags')?.value || '').split(',').map(value => value.trim()).filter(Boolean);
        state.historyKnowledge = await invoke('accept_desktop_history_knowledge', {
          candidateId,
          title: document.querySelector('#history-candidate-title')?.value || '',
          body: document.querySelector('#history-candidate-body')?.value || '',
          tags,
        });
        state.dialog = null;
        state.status = '资料候选已按当前编辑内容保存为本地 Knowledge。';
        await refresh();
      } else if (action === 'reject-history-knowledge') {
        state.historyKnowledge = await invoke('reject_desktop_history_knowledge', { candidateId });
        state.dialog = null;
        state.status = '资料候选已驳回；不会进入长期 Memory 或 Knowledge。';
      } else if (action === 'retry-history-knowledge') {
        state.status = '正在按显式操作重试；UNKNOWN 不会由后台自动重发。';
        render();
        state.historyKnowledge = await invoke('retry_desktop_history_knowledge', { candidateId });
        state.status = '历史资料库显式重试已结束，请核对当前状态。';
      } else {
        state.historyKnowledge = await invoke('delete_desktop_history_knowledge', { candidateId });
        state.dialog = null;
        state.status = '历史资料库候选记录已删除；已采纳的 Knowledge 不会被连带删除。';
      }
      state.error = '';
    } catch (error) {
      state.error = `历史资料库操作未完成：${String(error)}`;
    }
    render();
  })();
}, true);

app.addEventListener('click', event => {
  const target = event.target.closest?.('[data-action]');
  const action = target?.dataset.action;
  const actions = ['generate-reminder-draft', 'create-manual-reminder-draft', 'review-reminder-draft', 'retry-reminder-draft', 'confirm-reminder-draft', 'reject-reminder-draft', 'edit-reminder-plan', 'confirm-edit-reminder-plan', 'set-reminder-paused', 'retry-reminder-plan', 'delete-reminder-plan', 'confirm-delete-reminder-plan', 'request-reminder-notification-permission'];
  if (!actions.includes(action)) return;
  event.preventDefault();
  event.stopImmediatePropagation();
  void (async () => {
    try {
      if (!native) throw new Error('Web 预览不会创建、执行或通知提醒计划');
      if (action === 'request-reminder-notification-permission') {
        const granted = await requestReminderNotificationPermission();
        state.status = granted ? '系统通知权限已授予；后续成功监控可发送不含结果正文的通知。' : '系统未授予通知权限；监控结果仍只保存在本机。';
      } else if (action === 'generate-reminder-draft') {
        const workspaceId = state.current?.summary?.id;
        const conversationId = state.selectedConversationId;
        if (!workspaceId || !conversationId) throw new Error('当前对话不可用');
        state.status = '正在把明确的提醒意图整理成可编辑草案；尚未创建计划。';
        render();
        try {
          state.reminders = await invoke('generate_desktop_reminder_draft', { args: { workspaceId, conversationId, userMessageId: target.dataset.userMessageId, assistantMessageId: target.dataset.assistantMessageId, timezoneId: Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Shanghai' } });
        } catch (error) {
          await loadDesktopReminders();
          throw error;
        }
        const draft = state.reminders.drafts.find(item => item.sourceAssistantMessageId === target.dataset.assistantMessageId);
        if (draft?.status === 'PENDING_REVIEW') state.dialog = { kind: 'reminder-draft', item: draft };
        state.status = draft?.status === 'NOT_ELIGIBLE' ? '模型判断该对话不构成明确提醒；未创建计划。' : '提醒草案已生成，请核对编辑后再确认。';
      } else if (action === 'create-manual-reminder-draft') {
        const workspaceId = state.current?.summary?.id;
        if (!workspaceId) throw new Error('请先打开一个本地工作区');
        state.reminders = await invoke('create_desktop_manual_reminder_draft', { args: { workspaceId, conversationId: state.selectedConversationId || null, timezoneId: Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Shanghai' } });
        const draft = state.reminders.drafts.find(item => item.status === 'PENDING_REVIEW');
        state.dialog = { kind: 'reminder-draft', item: draft };
      } else if (action === 'review-reminder-draft') {
        const draft = state.reminders.drafts.find(item => item.draftId === target.dataset.id);
        if (draft) state.dialog = { kind: 'reminder-draft', item: draft };
      } else if (action === 'retry-reminder-draft') {
        state.status = '正在按显式操作重试草案 refinement；UNKNOWN 不会自动重发。';
        render();
        try { state.reminders = await invoke('retry_desktop_reminder_draft', { draftId: target.dataset.id }); }
        catch (error) { await loadDesktopReminders(); throw error; }
        const draft = state.reminders.drafts.find(item => item.draftId === target.dataset.id);
        if (draft?.status === 'PENDING_REVIEW') state.dialog = { kind: 'reminder-draft', item: draft };
      } else if (action === 'confirm-reminder-draft') {
        const item = state.dialog?.item;
        if (!item) throw new Error('提醒草案已变化');
        state.reminders = await invoke('confirm_desktop_reminder_draft', { args: { draftId: item.draftId, title: document.querySelector('#reminder-title')?.value || '', instruction: document.querySelector('#reminder-instruction')?.value || '', scheduleKind: document.querySelector('#reminder-schedule-kind')?.value || 'ONCE', anchorLocal: document.querySelector('#reminder-anchor-local')?.value || '', timezoneId: document.querySelector('#reminder-timezone')?.value || '', missedPolicy: document.querySelector('#reminder-missed-policy')?.value || 'RUN_ONCE' } });
        state.dialog = null;
        state.status = '提醒计划已确认并持久化；到期后由本机 scheduler 执行。';
      } else if (action === 'reject-reminder-draft') {
        state.reminders = await invoke('reject_desktop_reminder_draft', { draftId: target.dataset.id });
        state.dialog = null;
        state.status = '提醒草案已拒绝；未创建计划。';
      } else if (action === 'edit-reminder-plan') {
        const plan = state.reminders.plans.find(item => item.planId === target.dataset.id);
        if (plan) state.dialog = { kind: 'reminder-plan', item: plan };
      } else if (action === 'confirm-edit-reminder-plan') {
        const item = state.dialog?.item;
        if (!item) throw new Error('提醒计划已变化');
        state.reminders = await invoke('update_desktop_reminder_plan', { args: { planId: item.planId, expectedUpdatedAtMs: item.updatedAtMs, title: document.querySelector('#reminder-title')?.value || '', instruction: document.querySelector('#reminder-instruction')?.value || '', scheduleKind: document.querySelector('#reminder-schedule-kind')?.value || 'ONCE', anchorLocal: document.querySelector('#reminder-anchor-local')?.value || '', timezoneId: document.querySelector('#reminder-timezone')?.value || '', missedPolicy: document.querySelector('#reminder-missed-policy')?.value || 'RUN_ONCE' } });
        state.dialog = null;
        state.status = '提醒计划已更新；原执行记录保留，下一次执行时间已重新计算。';
      } else if (action === 'set-reminder-paused') {
        state.reminders = await invoke('set_desktop_reminder_plan_paused', { planId: target.dataset.id, paused: target.dataset.paused === 'true' });
        state.status = target.dataset.paused === 'true' ? '计划已暂停；运行中的请求已请求停止。' : '计划已恢复，并从当前时间重新计算下一次执行。';
      } else if (action === 'retry-reminder-plan') {
        state.reminders = await invoke('retry_desktop_reminder_plan', { planId: target.dataset.id });
        state.status = '计划已按显式操作恢复待执行；不会重复提交未知 Attempt。';
      } else if (action === 'delete-reminder-plan') {
        const plan = state.reminders.plans.find(item => item.planId === target.dataset.id);
        if (plan) state.dialog = { kind: 'reminder-delete', item: plan };
      } else if (action === 'confirm-delete-reminder-plan') {
        state.reminders = await invoke('delete_desktop_reminder_plan', { planId: target.dataset.id });
        state.dialog = null;
        state.status = '提醒计划及其执行记录已删除；运行中的请求已请求停止。';
      }
      await loadDesktopBackgroundRuntime();
      state.error = '';
    } catch (error) {
      state.error = `提醒操作未完成：${String(error)}`;
    }
    render();
  })();
}, true);
app.addEventListener('click', event => {
  const action = event.target.closest('[data-action]')?.dataset.action;
  if (!state.temporaryConversation || !['new-chat', 'select-chat', 'show-work', 'show-settings'].includes(action)) return;
  event.preventDefault(); event.stopImmediatePropagation();
  state.status = '临时聊天与普通导航隔离；点击右上角“临时聊天”返回普通聊天。';
  render();
}, true);
app.addEventListener('click', event => {
  const target = event.target.closest('[data-action="select-chat"]');
  if (!target || state.pane !== 'work') return;
  // Work is a Conversation scope, not a route to the former work-home dashboard.
  event.stopImmediatePropagation();
  state.selectedConversationId = target.dataset.id;
  state.conversationFindOpen = false;
  state.conversationFindQuery = '';
  recomputeConversationFind({ resetIndex: true });
  state.profileOpen = false;
  state.error = '';
  state.composerDraft = readChatDraft();
  void markConversationOpened(target.dataset.id).finally(render);
}, true);
const invoke = (command, args = {}) => tauriBridge.invoke(command, args);
const dialogInvoke = (command, options) => invoke(`plugin:dialog|${command}`, { options });
function applyConversationReadState(projection) {
  state.conversationReadState = projection || { workspaceId: null, conversations: [] };
  const rows = projection?.conversations || [];
  state.unreadConversationIds = new Set(rows.filter(item => item.unread).map(item => item.conversationId));
  state.manualUnreadAtMs = new Map(rows.filter(item => item.manualUnreadAtMs != null).map(item => [item.conversationId, Number(item.manualUnreadAtMs)]));
}
async function loadConversationReadState({ observeSelected = true } = {}) {
  const workspaceId = state.current?.summary?.id;
  if (!workspaceId || !native) {
    applyConversationReadState({ workspaceId: workspaceId || null, conversations: [] });
    return state.conversationReadState;
  }
  const projection = await invoke('read_desktop_conversation_read_state', {
    workspaceId,
    observedConversationId: observeSelected ? state.selectedConversationId : null,
  });
  applyConversationReadState(projection);
  return projection;
}
async function markConversationOpened(conversationId) {
  const workspaceId = state.current?.summary?.id;
  if (!workspaceId || !conversationId || !native) return;
  applyConversationReadState(await invoke('mark_desktop_conversation_opened', { workspaceId, conversationId }));
}
async function markConversationUnread(conversationId) {
  const workspaceId = state.current?.summary?.id;
  if (!workspaceId || !conversationId || !native) return;
  applyConversationReadState(await invoke('mark_desktop_conversation_unread', { workspaceId, conversationId }));
}
const selectedModelService = () => state.modelServiceSettings.find(item => item.providerId === state.modelProviderId) || state.modelServiceSettings[0] || null;
function resetModelServiceDraft(service = selectedModelService()) {
  state.modelServiceDraft = service ? { providerId: service.providerId, enabled: service.enabled, presetId: service.presetId, revision: service.revision } : null;
  state.modelCredentialDraft = '';
  state.modelCredentialEdited = false;
  state.modelCredentialVisible = false;
  state.modelSettingsNotice = '';
  state.modelSettingsError = '';
  state.settingsPicker = null;
}
function replaceModelService(next) {
  state.modelServiceSettings = state.modelServiceSettings.map(item => item.providerId === next.providerId ? next : item);
}
async function loadModelServiceSettings() {
  if (!native) return;
  const settings = await invoke('read_desktop_model_service_settings');
  if (Array.isArray(settings) && settings.length) {
    state.modelServiceSettings = settings;
    if (!settings.some(item => item.providerId === state.modelProviderId)) state.modelProviderId = settings[0].providerId;
    resetModelServiceDraft();
  }
}
async function loadDesktopAppSettings() {
  if (!native) return;
  const legacyAppearance = normalizeAppearance(state.appearance);
  const legacyProduct = normalizeProductSettings(state.productSettings);
  let projection = await invoke('read_desktop_app_settings');
  if (Number(projection.revision || 0) === 0) {
    projection = await invoke('save_desktop_app_settings', { args: { appearance: legacyAppearance, product: legacyProduct, expectedRevision: 0 } });
  }
  state.appearance = normalizeAppearance(projection.appearance);
  state.productSettings = normalizeProductSettings(projection.product);
  state.personalizationDraft = { ...state.productSettings };
  state.personalizationDirty = false;
  state.appSettingsRevision = Number(projection.revision || 0);
  state.settingsCapabilities = projection.capabilities || state.settingsCapabilities;
  parityPreferences.clearNativeAppSettingsMigrationSource();
  applyAppearance();
}
async function loadDesktopUsageLedger() {
  if (!native) return;
  state.usageLedger = await invoke('read_desktop_usage_ledger');
}
async function loadDesktopContextRecords() {
  if (!native) return;
  state.contextSelectionRecords = await invoke('read_desktop_ordinary_chat_context_records');
}
async function loadDesktopDiagnosticRecords() {
  if (!native) return;
  state.diagnosticRecords = await invoke('read_desktop_ordinary_chat_diagnostic_records');
}
async function loadDesktopPrivacyInventory() {
  if (!native) return;
  state.privacyInventory = await invoke('read_desktop_privacy_inventory');
}
async function loadDesktopAccountSync() {
  if (!native) return state.accountSync;
  const pendingUnknown = state.accountSync?.pendingUnknown || null;
  const remoteDocuments = state.accountSync?.remoteDocuments || [];
  state.accountSync = { ...await invoke('read_desktop_account_sync'), pendingUnknown, remoteDocuments };
  return state.accountSync;
}
async function loadDesktopHistoryKnowledge({ runDue = false } = {}) {
  if (!native) return state.historyKnowledge;
  state.historyKnowledge = await invoke(runDue ? 'run_desktop_history_knowledge_due' : 'read_desktop_history_knowledge');
  return state.historyKnowledge;
}
async function loadDesktopReminders() {
  if (!native) return state.reminders;
  state.reminders = await invoke('read_desktop_reminders');
  return state.reminders;
}
async function loadDesktopBackgroundRuntime() {
  if (!native) return state.backgroundRuntime;
  state.backgroundRuntime = await invoke('read_desktop_background_runtime');
  return state.backgroundRuntime;
}
async function readReminderNotificationPermission() {
  if (!native) {
    state.reminderNotificationPermission = 'unavailable';
    return false;
  }
  try {
    state.reminderNotificationPermission = await invoke('read_desktop_reminder_notification_permission');
    return ['granted', 'legacy'].includes(state.reminderNotificationPermission);
  } catch {
    state.reminderNotificationPermission = 'unavailable';
    return false;
  }
}
async function requestReminderNotificationPermission() {
  if (!native) return false;
  try {
    const permission = await invoke('request_desktop_reminder_notification_permission');
    state.reminderNotificationPermission = ['granted', 'legacy', 'denied'].includes(permission) ? permission : 'default';
    return ['granted', 'legacy'].includes(permission);
  } catch {
    state.reminderNotificationPermission = 'unavailable';
    return false;
  }
}
async function flushReminderNotifications() {
  if (!native) return;
  const pending = await invoke('read_pending_desktop_reminder_notifications');
  if (!pending.length) return;
  const allowed = Boolean(state.productSettings.monitorNotifications) && await readReminderNotificationPermission();
  for (const item of pending) {
    if (!allowed) {
      await invoke('acknowledge_desktop_reminder_notification', { runId: item.runId, sent: false, safeCode: state.productSettings.monitorNotifications ? 'PERMISSION_NOT_GRANTED' : 'SETTING_DISABLED' });
      continue;
    }
    try {
      await invoke('send_pending_desktop_reminder_notification', { runId: item.runId });
    } catch {
      // The Rust owner persists FAILED or UNKNOWN before returning an error.
    }
  }
}
async function routeToReminderPlan(extra = {}) {
  const candidate = reminderNotificationExtra(extra);
  if (candidate.route !== 'desktop-reminder-plan' || !candidate.planId) return false;
  const resolved = await invoke('resolve_desktop_reminder_notification_target', { target: candidate });
  if (!resolved) return false;
  await loadDesktopReminders();
  const plan = state.reminders.plans.find(item => item.planId === resolved.planId);
  if (!plan || plan.workspaceId !== resolved.workspaceId || (plan.conversationId || '') !== resolved.conversationId) return false;
  const nextWorkspace = state.current?.summary?.id === resolved.workspaceId
    ? state.current
    : await invoke('read_desktop_workspace', { workspaceId: resolved.workspaceId });
  if (!await invoke('activate_desktop_reminder_notification_target', { target: resolved })) return false;
  state.current = nextWorkspace;
  if (resolved.conversationId) state.selectedConversationId = resolved.conversationId;
  state.selectedReminderPlanId = resolved.planId;
  state.pane = 'settings';
  state.settingsSection = 'reminders';
  state.settingsMobileHome = false;
  render();
  queueMicrotask(() => document.querySelector(`[data-reminder-plan-id="${CSS.escape(resolved.planId)}"]`)?.scrollIntoView({ block: 'center' }));
  return true;
}
async function handleReminderNotificationAction(event) {
  const action = reminderNotificationAction(event);
  if (!action.clickId) return false;
  const target = await invoke('consume_desktop_reminder_notification_action', { clickId: action.clickId });
  if (!target) return false;
  return routeToReminderPlan(target);
}
async function installReminderNotificationActionListener() {
  if (!native || !tauriEvents?.listen) return false;
  try {
    await tauriEvents.listen('desktop-reminder-notification-action-v1', event => {
      void handleReminderNotificationAction(event).catch(() => {
        state.reminderNotificationBridge = { ...state.reminderNotificationBridge, safeCode: 'ACTION_ROUTE_FAILED' };
        render();
      });
    });
    const status = await invoke('read_desktop_reminder_notification_bridge_status');
    state.reminderNotificationBridge = { ...status, listenerReady: true };
    const pending = await invoke('drain_desktop_reminder_notification_actions');
    for (const target of pending) await routeToReminderPlan(target);
    state.reminderNotificationBridge = { ...state.reminderNotificationBridge, pendingActionCount: 0 };
    return true;
  } catch {
    state.reminderNotificationBridge = { ...state.reminderNotificationBridge, initialized: false, listenerReady: false, safeCode: 'LISTENER_INIT_FAILED' };
    console.warn('Reminder native notification action listener unavailable');
    return false;
  }
}
function notifyAccountSync(kind) {
  const NotificationOwner = globalThis.Notification;
  if (!native || typeof NotificationOwner !== 'function' || NotificationOwner.permission !== 'granted') return;
  const body = kind === 'success' ? '加密同步已完成；可在设置中查看安全回执。' : '加密同步需要处理；请在设置中查看安全诊断。';
  try { new NotificationOwner('南枫云同步', { body, tag: 'nanfeng-account-sync-v1' }); } catch { /* system notification is optional and never blocks local truth */ }
}
async function loadTranscriptionState() {
  if (!native) {
    state.transcription = { settings: { ...TRANSCRIPTION_PREVIEW_STATE.settings }, tasks: [] };
    return state.transcription;
  }
  state.transcription = await invoke('read_desktop_transcription_state', { workspaceId: null });
  if (!state.transcription.tasks.some(task => task.id === state.selectedTranscriptionTaskId)) {
    state.selectedTranscriptionTaskId = state.transcription.tasks[0]?.id || null;
  }
  return state.transcription;
}

function transcriptionTask(taskId = state.selectedTranscriptionTaskId) {
  return state.transcription.tasks.find(task => task.id === taskId) || null;
}

async function pickTranscriptionSource() {
  if (!native) {
    state.error = 'Web 预览不会读取本机音视频。';
    render();
    return;
  }
  const selectedPath = await dialogInvoke('open', { multiple: false, directory: false, filters: [{ name: '音频与视频', extensions: ['aac', 'flac', 'm4a', 'mp3', 'ogg', 'opus', 'wav', 'wma', '3gp', 'avi', 'm4v', 'mkv', 'mov', 'mp4', 'mpeg', 'mpg', 'webm'] }] });
  if (!selectedPath) return;
  try {
    const task = await invoke('import_desktop_transcription_source', { args: { workspaceId: state.current?.summary?.id || '', selectedPath } });
    await loadTranscriptionState();
    state.selectedTranscriptionTaskId = task.id;
    state.status = '音视频已复制到本机私有存储并建立可恢复任务；尚未发送给模型。';
    state.error = '';
  } catch (error) {
    state.error = `音视频未导入：${String(error)}`;
  }
  render();
}

async function pickTranscriptionDocument() {
  if (!native) {
    state.error = 'Web 预览不会读取本机图片或 PDF。';
    render();
    return;
  }
  const selectedPath = await dialogInvoke('open', { multiple: false, directory: false, filters: [{ name: '图片与 PDF', extensions: ['jpg', 'jpeg', 'png', 'pdf'] }] });
  if (!selectedPath) return;
  try {
    const task = await invoke('import_desktop_ocr_source', { args: { workspaceId: state.current?.summary?.id || '', selectedPath } });
    await loadTranscriptionState();
    state.transcriptionMode = 'document';
    state.selectedTranscriptionTaskId = task.id;
    state.status = '图片/PDF 已复制到本机私有存储并建立 GLM-OCR 任务；尚未发送。';
    state.error = '';
  } catch (error) {
    state.error = `图片/PDF 未导入：${String(error)}`;
  }
  render();
}

async function saveTranscriptionSettings(expectedRevision) {
  if (!native) return;
  try {
    const languageCode = document.querySelector('#transcription-language')?.value || null;
    await invoke('save_desktop_transcription_settings', { args: {
      modelId: document.querySelector('#transcription-model')?.value || 'qwen3-asr-flash',
      languageCode,
      outputFormat: document.querySelector('#transcription-output')?.value || 'md',
      expectedRevision,
    } });
    await loadTranscriptionState();
    state.status = '语音转写默认设置已保存；已有任务继续使用创建时快照。';
    state.error = '';
  } catch (error) { state.error = `语音转写设置未保存：${String(error)}`; }
  render();
}

async function executeTranscriptionTask(taskId, retry = false) {
  if (!native || state.transcriptionBusyTaskId) return;
  state.transcriptionBusyTaskId = taskId;
  state.error = '';
  render();
  try {
    if (retry) await invoke('retry_desktop_transcription_task', { taskId });
    const documentTask = transcriptionTask(taskId)?.modelId === 'glm-ocr';
    await invoke(documentTask ? 'run_desktop_ocr_task' : 'run_desktop_transcription_task', { taskId });
    state.status = documentTask ? '图片/PDF 已转换为 Markdown，来源、结果与费用已保存。' : '语音转写已完成，结果、时间轴与费用估算已保存。';
  } catch (error) {
    state.error = `语音转写未完成：${String(error)}`;
  } finally {
    state.transcriptionBusyTaskId = null;
    await loadTranscriptionState().catch(() => {});
    render();
  }
}

async function previewTranscriptionSource(taskId) {
  const task = transcriptionTask(taskId);
  if (!task) return;
  if (['image/jpeg', 'image/png'].includes(task.sourceMimeType)) return openImagePreview(task.sourceAttachmentId, task.workspaceId);
  if (task.sourceMimeType === 'application/pdf') return openPdfPreview(task.sourceAttachmentId, undefined, task.workspaceId);
  if (task.sourceMimeType === 'video/mp4') return openVideoPreview(task.sourceAttachmentId, undefined, task.workspaceId);
  if (['audio/mpeg', 'audio/wav', 'audio/mp4'].includes(task.sourceMimeType)) return openAudioPreview(task.sourceAttachmentId, undefined, task.workspaceId);
  try { await invoke('open_desktop_attachment_with_system', { args: { workspaceId: task.workspaceId, attachmentId: task.sourceAttachmentId } }); }
  catch (error) { state.error = `原文件未打开：${String(error)}`; render(); }
}

async function exportTranscriptionTask(taskId, format) {
  const task = transcriptionTask(taskId);
  if (!task || !native) return;
  const safeName = String(task.sourceDisplayName || '南枫转写').replace(/[\\/:*?"<>|\r\n]+/g, ' ').replace(/\.[^.]+$/, '').slice(0, 80);
  const resultSuffix = task.modelId === 'glm-ocr' ? 'OCR' : '转写';
  const selectedPath = await dialogInvoke('save', { defaultPath: `${safeName}-${resultSuffix}.${format}`, filters: [{ name: `转写 ${format.toUpperCase()}`, extensions: [format] }] });
  if (!selectedPath) return;
  try {
    const receipt = await invoke('export_desktop_transcription_task', { args: { taskId, selectedPath, format } });
    state.status = `转写结果已原子导出并回读：${bytes(receipt.byteCount)} · ${short(receipt.sha256)}`;
    state.error = '';
  } catch (error) { state.error = `转写结果未导出：${String(error)}`; }
  render();
}

async function continueChatWithTranscription(taskId) {
  const task = transcriptionTask(taskId);
  if (!task?.resultAttachmentId) return;
  try {
    const result = await invoke('read_desktop_text_preview', { args: { workspaceId: task.workspaceId, attachmentId: task.resultAttachmentId } });
    state.composerAttachments = [{ id: result.attachmentId, mimeType: result.mimeType, displayName: result.displayName, byteCount: result.byteCount }];
    state.composerDraft = '';
    state.selectedConversationId = null;
    state.pane = 'chat';
    state.status = '转写结果已作为本地 Markdown 附件放入新对话草稿；尚未发送。';
    state.error = '';
  } catch (error) { state.error = `转写结果未加入对话：${String(error)}`; }
  render();
}

app.addEventListener('click', async event => {
  const target = event.target.closest?.('[data-action]');
  const action = target?.dataset.action;
  const actions = ['show-reminders', 'show-transcription', 'select-transcription-mode', 'pick-transcription-document', 'pick-transcription-source', 'select-transcription-task', 'save-transcription-settings', 'run-transcription-task', 'retry-transcription-task', 'cancel-transcription-task', 'preview-transcription-source', 'copy-transcription-result', 'continue-chat-with-transcription', 'export-transcription-task', 'ask-delete-transcription-task', 'confirm-delete-transcription-task'];
  if (!actions.includes(action)) return;
  event.preventDefault();
  event.stopImmediatePropagation();
  if (action === 'show-reminders') { state.pane = 'reminders'; state.sidebarOpen = false; state.profileOpen = false; await loadDesktopReminders().catch(error => { state.error = `定时任务未读取：${String(error)}`; }); render(); return; }
  if (action === 'show-transcription') { state.pane = 'transcription'; state.sidebarOpen = false; state.profileOpen = false; await loadTranscriptionState().catch(error => { state.error = `南枫转写任务未读取：${String(error)}`; }); render(); return; }
  if (action === 'select-transcription-mode') { state.transcriptionMode = target.dataset.mode === 'speech' ? 'speech' : 'document'; state.selectedTranscriptionTaskId = null; render(); return; }
  if (action === 'pick-transcription-document') return pickTranscriptionDocument();
  if (action === 'pick-transcription-source') return pickTranscriptionSource();
  if (action === 'select-transcription-task') { state.selectedTranscriptionTaskId = target.dataset.taskId; render(); return; }
  if (action === 'save-transcription-settings') return saveTranscriptionSettings(Number(target.dataset.revision || 0));
  if (action === 'run-transcription-task') return executeTranscriptionTask(target.dataset.taskId, false);
  if (action === 'retry-transcription-task') return executeTranscriptionTask(target.dataset.taskId, true);
  if (action === 'cancel-transcription-task') { try { await invoke('cancel_desktop_transcription_task', { taskId: target.dataset.taskId }); state.status = '转写任务已取消；已保存的检查点仍保留供后续显式重试。'; state.error = ''; } catch (error) { state.error = `任务未取消：${String(error)}`; } await loadTranscriptionState().catch(() => {}); render(); return; }
  if (action === 'preview-transcription-source') return previewTranscriptionSource(target.dataset.taskId);
  if (action === 'copy-transcription-result') { const text = (transcriptionTask(target.dataset.taskId)?.segments || []).map(segment => segment.text).join('\n\n'); try { await navigator.clipboard.writeText(text); state.status = '转写全文已复制。'; state.error = ''; } catch { state.error = '系统未允许写入剪贴板。'; } render(); return; }
  if (action === 'continue-chat-with-transcription') return continueChatWithTranscription(target.dataset.taskId);
  if (action === 'export-transcription-task') return exportTranscriptionTask(target.dataset.taskId, target.dataset.format);
  if (action === 'ask-delete-transcription-task') { state.dialog = { kind: 'transcription-delete', taskId: target.dataset.taskId, submitting: false, failure: '' }; render(); return; }
  if (action === 'confirm-delete-transcription-task') { state.dialog = { ...state.dialog, submitting: true, failure: '' }; render(); try { await invoke('delete_desktop_transcription_task', { taskId: target.dataset.taskId }); state.dialog = null; state.selectedTranscriptionTaskId = null; state.status = '转写任务已删除；无引用私有字节进入安全清理期。'; await loadTranscriptionState(); } catch (error) { state.dialog = { ...state.dialog, submitting: false, failure: String(error) }; } render(); }
}, true);
async function saveModelServiceSettings() {
  const service = selectedModelService();
  const draft = state.modelServiceDraft;
  if (!service || !draft || state.modelSettingsSaving) return;
  if (!native) {
    state.modelSettingsError = 'Web 预览不会保存 API Key；请在 Desktop 应用中操作。';
    render();
    return;
  }
  state.modelSettingsSaving = true;
  state.modelSettingsNotice = '';
  state.modelSettingsError = '';
  render();
  try {
    const saved = await invoke('save_desktop_model_service_settings', { args: {
      providerId: service.providerId,
      enabled: Boolean(draft.enabled),
      presetId: draft.presetId,
      expectedRevision: service.revision,
      apiKey: state.modelCredentialEdited && state.modelCredentialDraft.trim() ? state.modelCredentialDraft.trim() : null,
    } });
    replaceModelService(saved);
    state.modelServiceDraft = { providerId: saved.providerId, enabled: saved.enabled, presetId: saved.presetId, revision: saved.revision };
    state.modelSettingsNotice = state.modelCredentialEdited && state.modelCredentialDraft.trim() ? 'API Key 已安全保存在本机。尚未测试连接；请点击“测试连接”确认 API Key 是否可用。' : '模型设置已保存。';
    state.modelCredentialDraft = '';
    state.modelCredentialEdited = false;
    state.modelCredentialVisible = false;
  } catch (error) {
    state.modelSettingsError = `保存失败：${String(error)}`;
  } finally {
    state.modelSettingsSaving = false;
    render();
  }
}
async function revealModelServiceCredential() {
  const service = selectedModelService();
  if (!service || state.modelSettingsSaving || state.modelSettingsTesting) return;
  if (state.modelCredentialVisible) {
    state.modelCredentialVisible = false;
    render();
    return;
  }
  if (!native) {
    state.modelSettingsError = 'Web 预览不会读取本机凭据。';
    render();
    return;
  }
  try {
    state.modelCredentialDraft = await invoke('reveal_desktop_model_service_credential', { providerId: service.providerId });
    state.modelCredentialVisible = true;
    state.modelSettingsError = '';
  } catch (error) {
    state.modelSettingsError = `无法显示 API Key：${String(error)}`;
  }
  render();
}
async function testModelServiceConnection() {
  const service = selectedModelService();
  if (!service || state.modelSettingsTesting || !service.credentialStored) return;
  if (!native) {
    state.modelSettingsError = 'Web 预览不会发送连接测试。';
    render();
    return;
  }
  state.modelSettingsTesting = true;
  state.modelSettingsNotice = '';
  state.modelSettingsError = '';
  render();
  try {
    await invoke('test_desktop_model_service_connection', { args: { providerId: service.providerId, presetId: service.presetId } });
    state.modelSettingsNotice = `连接成功：${service.providerDisplayName} · ${service.presetDisplayName}`;
  } catch (error) {
    state.modelSettingsError = `连接测试失败：${String(error)}`;
  } finally {
    state.modelSettingsTesting = false;
    render();
  }
}
const escape = value => String(value ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' }[char]));
const short = value => value?.length > 14 ? `${value.slice(0, 12)}…` : value || '—';
const bytes = value => value < 1024 ? `${value} B` : `${(value / 1024).toFixed(1)} KiB`;
const intent = prefix => `${prefix}-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
const icon = (path, label) => `<svg aria-hidden="true" viewBox="0 0 24 24"><title>${label}</title><path d="${path}"/></svg>`;
let fullSearchDebounce = null;
let fullSearchGeneration = 0;

async function runFullSearch({ recordHistory = false, restoreScroll = false } = {}) {
  const generation = ++fullSearchGeneration;
  state.searchPanel = true;
  state.searchLoading = true;
  state.searchError = '';
  state.searchHistoryHighlighted = bestSearchHistoryMatch(state.chatSearch, state.searchHistory);
  render();
  if (!native) {
    state.searchLoading = false;
    state.searchPage = { hits: [], textCount: 0, attachmentCount: 0, truncated: false };
    state.searchError = 'Web 预览不会读取 Desktop SQLite；请在原生应用中验证真实结果。';
    render();
    return;
  }
  try {
    const page = await invoke('query_desktop_local_index', { args: {
      query: state.chatSearch,
      category: state.searchCategory || 'all',
      sortMode: state.searchSortMode,
      fileType: state.searchCategory === 'file' ? state.searchFileType : 'all',
      recordHistory: Boolean(recordHistory && state.chatSearch.trim()),
      historyWorkspaceId: state.current?.summary?.id || null,
    } });
    if (generation !== fullSearchGeneration) return;
    state.searchPage = page;
    state.searchResults = page.hits || [];
    state.searchLoading = false;
    state.searchError = '';
    if (recordHistory && state.current && state.chatSearch.trim()) state.searchHistory = await invoke('read_desktop_local_search_history', { workspaceId: state.current.summary.id });
    state.searchHistoryHighlighted = bestSearchHistoryMatch(state.chatSearch, state.searchHistory);
    render();
    queueMicrotask(() => {
      const input = document.querySelector('#full-search-input');
      if (!restoreScroll) input?.focus({ preventScroll: true });
      restoreFullSearchScroll();
    });
  } catch (error) {
    if (generation !== fullSearchGeneration) return;
    state.searchLoading = false;
    state.searchError = String(error);
    render();
  }
}

function scheduleFullSearch() {
  if (fullSearchDebounce !== null) window.clearTimeout(fullSearchDebounce);
  fullSearchDebounce = window.setTimeout(() => { fullSearchDebounce = null; void runFullSearch(); }, state.chatSearch.trim() ? 180 : 0);
}

async function openFullSearch(category = 'all') {
  state.searchCategory = category;
  state.searchSortMode = 'default';
  state.searchFileType = 'all';
  state.searchPanel = true;
  state.searchHistoryOpen = false;
  state.searchHistoryManuallyOpened = false;
  state.searchScrollSnapshot = null;
  if (native && state.current) {
    try { state.searchHistory = await invoke('read_desktop_local_search_history', { workspaceId: state.current.summary.id }); } catch { state.searchHistory = []; }
  }
  await runFullSearch();
}

function rememberFullSearchScroll(entryId = null) {
  const owner = document.querySelector('[data-search-scroll-owner]');
  state.searchScrollSnapshot = { top: owner?.scrollTop || 0, entryId };
}

function restoreFullSearchScroll() {
  const owner = document.querySelector('[data-search-scroll-owner]');
  if (!owner || !state.searchScrollSnapshot) return;
  const target = state.searchScrollSnapshot.entryId ? owner.querySelector(`[data-search-entry-id="${CSS.escape(state.searchScrollSnapshot.entryId)}"], [data-entry-id="${CSS.escape(state.searchScrollSnapshot.entryId)}"]`) : null;
  if (target) target.scrollIntoView({ block: 'center' }); else owner.scrollTop = state.searchScrollSnapshot.top;
}

async function submitLocalSearch() {
  state.searchHistoryOpen = false;
  state.searchHistoryManuallyOpened = false;
  await runFullSearch({ recordHistory: true });
}

function fullSearchHit(entryId) {
  return state.searchPage?.hits?.find(hit => hit.entryId === entryId) || null;
}

async function loadSearchWorkspace(workspaceId) {
  if (!native || !workspaceId) return false;
  if (state.current?.summary?.id === workspaceId) return true;
  state.current = await invoke('read_desktop_workspace', { workspaceId });
  state.history = await invoke('read_desktop_workbench_history', { workspaceId });
  reloadFavoriteConversationIds();
  await loadConversationReadState({ observeSelected: false });
  return true;
}

async function locateSearchHit(hit) {
  if (!hit) return;
  rememberFullSearchScroll(hit.entryId);
  try {
    await loadSearchWorkspace(hit.workspaceId);
    if (String(hit.conversationId || '').startsWith('transcription-task:')) {
      state.selectedTranscriptionTaskId = String(hit.conversationId).slice('transcription-task:'.length);
      state.searchReturnActive = true;
      state.searchPanel = false;
      state.pane = 'transcription';
      await loadTranscriptionState();
      state.transcriptionMode = transcriptionTask(state.selectedTranscriptionTaskId)?.modelId === 'glm-ocr' ? 'document' : 'speech';
      render();
      return;
    }
    let conversation = state.current?.exchange?.conversations?.find(item => item.id === hit.conversationId);
    if (!conversation) throw new Error('结果所属会话已变化');
    if (hit.branchLeafId && conversation.currentLeafId !== hit.branchLeafId) {
      await invoke('mutate_desktop_domain', { args: {
        intentId: intent('search-switch-leaf'), workspaceId: hit.workspaceId, entity: 'conversation', action: 'switchLeaf',
        objectId: hit.conversationId, expectedRevision: Number(hit.conversationRevision), fields: { messageId: hit.branchLeafId },
      } });
      state.current = await invoke('read_desktop_workspace', { workspaceId: hit.workspaceId });
      conversation = state.current.exchange.conversations.find(item => item.id === hit.conversationId);
    }
    state.selectedConversationId = hit.conversationId;
    await markConversationOpened(hit.conversationId);
    state.searchAnchorMessageId = hit.messageId || null;
    state.searchAnchorAttachmentId = hit.attachmentId || null;
    state.searchLocatedArchivedConversationId = hit.archived ? hit.conversationId : null;
    state.searchReturnActive = true;
    state.searchPanel = false;
    state.pane = 'chat';
    render();
    queueMicrotask(() => {
      const selector = hit.messageId ? `[data-message-id="${CSS.escape(hit.messageId)}"]` : null;
      const node = selector ? document.querySelector(selector) : null;
      node?.scrollIntoView({ block: 'center' });
      if (node) { node.classList.add('search-anchor-flash'); window.setTimeout(() => node.classList.remove('search-anchor-flash'), 1600); }
    });
  } catch (error) {
    state.searchPanel = true;
    state.searchError = `无法定位这条结果：${String(error)}`;
    render();
  }
}

async function openSearchAttachment(hit) {
  if (!hit?.attachmentId) return;
  rememberFullSearchScroll(hit.entryId);
  const mime = String(hit.mimeType || '').toLowerCase();
  if (hit.contentKind === 'IMAGE') return openImagePreview(hit.attachmentId, hit.workspaceId);
  if (hit.contentKind === 'VIDEO') return openVideoPreview(hit.attachmentId, undefined, hit.workspaceId);
  if (hit.contentKind === 'AUDIO') return openAudioPreview(hit.attachmentId, undefined, hit.workspaceId);
  if (mime === 'application/pdf') return openPdfPreview(hit.attachmentId, undefined, hit.workspaceId);
  if (['text/plain', 'text/markdown', 'application/json', 'text/csv'].includes(mime)) return openTextPreview(hit.attachmentId, hit.workspaceId);
  try {
    await invoke('open_desktop_attachment_with_system', { args: { workspaceId: hit.workspaceId, attachmentId: hit.attachmentId } });
    state.status = `已交给系统打开“${hit.displayName || '附件'}”。`;
  } catch (error) {
    state.searchError = `该文件无法安全打开：${String(error)}`;
    render();
  }
}
const icons = { workspace: 'M3 5.5A2.5 2.5 0 0 1 5.5 3H10l2 2h6.5A2.5 2.5 0 0 1 21 7.5v10a2.5 2.5 0 0 1-2.5 2.5h-13A2.5 2.5 0 0 1 3 17.5z', conversation: 'M4 5.5A2.5 2.5 0 0 1 6.5 3h11A2.5 2.5 0 0 1 20 5.5v8a2.5 2.5 0 0 1-2.5 2.5H11L7 20v-4H6.5A2.5 2.5 0 0 1 4 13.5z', knowledge: 'M5 3.5h11A3 3 0 0 1 19 6.5v13l-6.5-3-6.5 3v-13a3 3 0 0 1 3-3z', import: 'M12 3v12m0 0 4-4m-4 4-4-4M5 17v3h14v-3', export: 'M12 15V3m0 0 4 4m-4-4L8 7M5 17v3h14v-3', close: 'M6 6l12 12M18 6 6 18', undo: 'M9 7 4 12l5 5M5 12h9a5 5 0 1 1 0 10', redo: 'm15 7 5 5-5 5m4-5h-9a5 5 0 1 0 0 10', trash: 'M4 7h16M10 11v6m4-6v6M9 7l1-2h4l1 2M6 7l1 14h10l1-14', plus: 'M12 5v14M5 12h14', edit: 'm4 16 9-9 3 3-9 9H4zM14 6l2-2 3 3-2 2', info: 'M12 17v-6m0-3.5v.01M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z', link: 'M10 13a5 5 0 0 0 7.1.1l2-2a5 5 0 0 0-7.1-7.1l-1.1 1.1m3.1 5.9a5 5 0 0 0-7.1-.1l-2 2A5 5 0 0 0 9 20l1.1-1.1' };
function workspace() { return state.current || (!native ? fixture : null); }
function selectWorkspaceDefaultConversation(data = workspace()) {
  state.selectedConversationId = data ? activeConversations(data)[0]?.id || null : null;
}
function active(data, key) { return data.exchange[key].filter(item => key === 'relations' ? item.status === 'ACTIVE' : item.status !== 'DELETED' && !item.archived && !item.deleted); }
function dialog() {
  if (!state.dialog) return '';
  if (state.dialog === 'import') return `<div class="scrim"><section class="dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="关闭">${icon(icons.close, '关闭')}</button><p class="overline">严格预检已通过</p><h2 id="dialog-title">导入为新的独立工作区</h2><p>不会合并或覆盖。正文仅作为不执行的文本 IR。</p><label>新工作区名称<input id="workspace-title" maxlength="120" value="导入工作区"></label><div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary" data-action="confirm-import">导入</button></div></section></div>`;
  if (state.dialog.kind === 'project') { const item = state.dialog.item; return `<div class="scrim"><section class="dialog edit-dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="取消">${icon(icons.close, '关闭')}</button><p class="overline">本地 revision 写入</p><h2 id="dialog-title">${item ? '编辑 Project' : '新建 Project'}</h2><p>保存由 Rust 检查 expected revision；冲突不会覆盖现有对象。</p><label>名称<input id="project-title" maxlength="120" value="${escape(item?.title || '')}"></label><label>说明<textarea id="project-description" maxlength="2000">${escape(item?.description || '')}</textarea></label><div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary" data-action="save-project">保存 ⌘S</button></div></section></div>`; }
  if (state.dialog.kind === 'knowledge') { const item = state.dialog.item; return `<div class="scrim"><section class="dialog edit-dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="取消">${icon(icons.close, '关闭')}</button><p class="overline">本地 revision 写入</p><h2 id="dialog-title">${item ? '编辑 Knowledge' : '新建 Knowledge'}</h2><p>保存时由 Rust 检查 expected revision；冲突不会覆盖当前记录。</p><label>标题<input id="knowledge-title" maxlength="120" value="${escape(item?.title || '')}"></label><label>正文<textarea id="knowledge-body" maxlength="2000000">${escape(item?.body || '')}</textarea></label><label>标签（逗号分隔）<input id="knowledge-tags" value="${escape((item?.tags || []).join(', '))}"></label><div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary" data-action="save-knowledge">保存 ⌘S</button></div></section></div>`; }
  if (state.dialog?.kind === 'history-knowledge-review') { const item = state.dialog.item; return `<div class="scrim"><section class="dialog edit-dialog history-knowledge-review" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="关闭">${icon(icons.close, '关闭')}</button><p class="overline">历史对话自动整理 · 待核对候选</p><h2 id="dialog-title">核对资料候选</h2><p>候选尚未进入长期 Memory 或 Knowledge。确认后只保存你当前看到并可编辑的内容；原对话不会改写。</p><label>标题<input id="history-candidate-title" maxlength="120" value="${escape(item.title || '')}"></label><label>候选内容<textarea id="history-candidate-body" maxlength="2000000">${escape(item.body || '')}</textarea></label><label>标签（逗号分隔）<input id="history-candidate-tags" value="${escape((item.tags || []).join(', '))}"></label><p class="security-note">来源会话 ${escape(item.conversationId)} · 消息 ${Number(item.sourceMessageIds?.length || 0)} 条 · ${escape(item.providerId || '未发送')} / ${escape(item.actualModelId || item.requestedModelId || '未知模型')} · checkpoint ${escape(short(item.checkpointId))}</p><div class="dialog-actions"><button data-action="reject-history-knowledge" data-id="${escape(item.candidateId)}">驳回</button><button class="primary" data-action="accept-history-knowledge" data-id="${escape(item.candidateId)}">确认保存为本地知识</button></div></section></div>`; }
  if (state.dialog?.kind === 'reminder-draft') { const item = state.dialog.item; const option = (value, label) => `<option value="${value}" ${item.scheduleKind === value ? 'selected' : ''}>${label}</option>`; return `<div class="scrim"><section class="dialog edit-dialog reminder-draft-dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="关闭">${icon(icons.close, '关闭')}</button><p class="overline">提醒草案 · 必须确认</p><h2 id="dialog-title">核对计划与监控</h2><p>模型只整理草案，不会替你创建计划。请核对对象、条件、频率、时区与错过策略。</p><label>计划名称<input id="reminder-title" maxlength="40" value="${escape(item.title || '')}" placeholder="例如：检查项目状态"></label><label>执行说明<textarea id="reminder-instruction" maxlength="8000" rows="5" placeholder="写清要检查什么、何时算完成">${escape(item.instruction || '')}</textarea></label><label>执行频率<select id="reminder-schedule-kind">${option('ONCE', '一次')}${option('HOURLY', '每小时')}${option('DAILY', '每天')}${option('WEEKLY', '每周')}</select></label><label>本地时间<input id="reminder-anchor-local" type="datetime-local" value="${escape(item.anchorLocal || '')}"></label><label>时区<input id="reminder-timezone" maxlength="64" value="${escape(item.timezoneId || '')}" placeholder="Asia/Shanghai"></label><label>错过执行窗口后<select id="reminder-missed-policy"><option value="RUN_ONCE" ${item.missedPolicy === 'RUN_ONCE' ? 'selected' : ''}>恢复后执行一次</option><option value="SKIP" ${item.missedPolicy === 'SKIP' ? 'selected' : ''}>跳过本次</option></select></label><p class="security-note">${escape(item.providerId || '手工草案')} · ${escape(item.actualModelId || item.requestedModelId || '未调用模型')} · ${item.chargeMicros == null ? '费用未报告' : `$${(item.chargeMicros / 1_000_000).toFixed(6)}`}</p><div class="dialog-actions"><button data-action="reject-reminder-draft" data-id="${escape(item.draftId)}">拒绝</button><button class="primary" data-action="confirm-reminder-draft">确认并创建计划</button></div></section></div>`; }
  if (state.dialog?.kind === 'reminder-plan') { const item = state.dialog.item; const option = (value, label) => `<option value="${value}" ${item.scheduleKind === value ? 'selected' : ''}>${label}</option>`; return `<div class="scrim"><section class="dialog edit-dialog reminder-draft-dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="关闭">${icon(icons.close, '关闭')}</button><p class="overline">已确认计划 · 本机更新</p><h2 id="dialog-title">编辑计划与监控</h2><p>保存会保留既有执行记录，并按新频率、时间和时区重新计算下一次执行。</p><label>计划名称<input id="reminder-title" maxlength="40" value="${escape(item.title || '')}"></label><label>执行说明<textarea id="reminder-instruction" maxlength="8000" rows="5">${escape(item.instruction || '')}</textarea></label><label>执行频率<select id="reminder-schedule-kind">${option('ONCE', '一次')}${option('HOURLY', '每小时')}${option('DAILY', '每天')}${option('WEEKLY', '每周')}</select></label><label>本地时间<input id="reminder-anchor-local" type="datetime-local" value="${escape(item.anchorLocal || '')}"></label><label>时区<input id="reminder-timezone" maxlength="64" value="${escape(item.timezoneId || '')}"></label><label>错过执行窗口后<select id="reminder-missed-policy"><option value="RUN_ONCE" ${item.missedPolicy === 'RUN_ONCE' ? 'selected' : ''}>恢复后执行一次</option><option value="SKIP" ${item.missedPolicy === 'SKIP' ? 'selected' : ''}>跳过本次</option></select></label><div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary" data-action="confirm-edit-reminder-plan">保存计划</button></div></section></div>`; }
  if (state.dialog?.kind === 'memory-summary-choice') return `<div class="scrim"><section class="dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><h2 id="dialog-title">如何处理这条内容</h2><p>“询问摘要”只查找已有本机记忆；“补充记忆”保存这条内容。</p><div class="dialog-actions"><button data-action="query-memory-summary">询问摘要</button><button class="primary" data-action="append-memory-summary">补充记忆</button></div></section></div>`;
  if (state.dialog?.kind === 'memory-summary-about') return `<div class="scrim"><section class="dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><h2 id="dialog-title">关于记忆</h2><p>这里显示已保存在本机的记忆摘要。启用后，相关内容可用于回答。</p><div class="dialog-actions"><button class="primary" data-action="close-dialog">知道了</button></div></section></div>`;
  if (state.dialog?.kind === 'memory-summary-delete') return `<div class="scrim"><section class="dialog" role="alertdialog" aria-modal="true"><h2>删除记忆？</h2><p>删除当前记忆摘要；聊天、文件和记忆开关不受影响。</p><div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary danger" data-action="confirm-delete-memory-summary">删除</button></div></section></div>`;
  if (state.dialog?.kind === 'memory-summary-disable') return `<div class="scrim"><section class="dialog" role="alertdialog" aria-modal="true"><h2>关闭记忆摘要生成和应用？</h2><p>停止生成和使用记忆摘要；已保存内容不会删除。</p><div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary danger" data-action="confirm-disable-memory-summary">关闭</button></div></section></div>`;
  if (state.dialog?.kind === 'custom-instructions-fullscreen') return `<div class="scrim custom-instructions-scrim"><section class="dialog custom-instructions-fullscreen" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><header><button class="icon-button" data-action="close-dialog" aria-label="关闭全屏自定义指令编辑">${icon(icons.close, '关闭')}</button><h2 id="dialog-title">自定义指令</h2><button class="primary" data-action="save-custom-instructions-fullscreen" aria-label="保存自定义指令">保存</button></header><label class="android-settings-field"><span>自定义指令</span><textarea id="personalization-instructions-fullscreen" maxlength="6000" placeholder="希望南枫 AI 如何回答你">${escape(state.personalizationDraft.customInstructions || '')}</textarea><small>${String(state.personalizationDraft.customInstructions || '').length} / 6000</small></label></section></div>`;
  if (state.dialog?.kind === 'reminder-delete') { const item = state.dialog.item; return `<div class="scrim"><section class="dialog" role="alertdialog" aria-modal="true"><h2>删除这个提醒计划？</h2><p>将删除“${escape(item.title)}”及其本机执行记录；操作不可撤销，运行中的请求会请求停止。</p><div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary danger" data-action="confirm-delete-reminder-plan" data-id="${escape(item.planId)}">删除计划</button></div></section></div>`; }
  if (state.dialog.kind === 'memory') { const item = state.dialog.item; return `<div class="scrim"><section class="dialog edit-dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="取消">${icon(icons.close, '关闭')}</button><p class="overline">本地 revision 写入</p><h2 id="dialog-title">${item ? '编辑 Memory' : '新建 Memory'}</h2><p>Memory 仅是本地文本 IR，不会进入 Prompt 或网络。</p><label>正文<textarea id="memory-body" maxlength="2000000">${escape(item?.body || '')}</textarea></label><div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary" data-action="save-memory">保存 ⌘S</button></div></section></div>`; }
  if (state.dialog.kind === 'relation') { const data = workspace(); const items = active(data, 'knowledge'); const options = items.map(item => `<option value="${escape(item.id)}">${escape(item.title)} · r${item.revision}</option>`).join(''); return `<div class="scrim"><section class="dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="取消">${icon(icons.close, '关闭')}</button><p class="overline">显式本地 relation</p><h2 id="dialog-title">建立 Knowledge 关系</h2><p>仅同一活动 scope 的两条 Knowledge 可建立；不会自动关联或去重。</p><label>来源<select id="relation-from">${options}</select></label><label>目标<select id="relation-to">${options}</select></label><label>类型<select id="relation-kind"><option value="RELATED">RELATED（对称）</option><option value="DERIVED_FROM">DERIVED_FROM</option><option value="REFERENCES">REFERENCES</option></select></label><div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary" data-action="save-relation" ${items.length < 2 ? 'disabled' : ''}>建立关系</button></div></section></div>`; }
  if (state.dialog?.kind === 'answer-context') { const record = state.dialog.record; const labels = { STYLE: '回答风格', PERSONA: '称呼与职业', MEMORY: '长期记忆', KNOWLEDGE: '知识库', CURRENT_PATH: '当前会话路径' }; return `<div class="scrim"><section class="dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="关闭">${icon(icons.close, '关闭')}</button><p class="overline">本机回答审计</p><h2 id="dialog-title">本次回答使用的上下文</h2><p>${escape(record.providerLabel || record.providerId)} · ${escape(record.modelId)} · 固定输入 Token ${Number(record.fixedInputTokens || 0)}</p><ul>${record.selectedSources.map(source => `<li><strong>${escape(labels[source.kind] || source.kind)}</strong> · ${escape(source.title)}</li>`).join('')}</ul><p>这里只显示类型与标题；正文、文件路径、URI、密钥和原始 Provider 载荷不会复制到审计记录。</p><div class="dialog-actions"><button class="primary" data-action="close-dialog">完成</button></div></section></div>`; }
  if (state.dialog === 'metadata') return `<div class="scrim"><section class="dialog edit-dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="关闭">${icon(icons.close, '关闭')}</button><p class="overline">仅本地安全 metadata</p><h2 id="dialog-title">模型与成本 metadata</h2><p>不存 Key、不联网取 catalog；价格仅标记为 fixture、手工或未知，绝不当作真实费用。</p><label>Provider ID<input id="provider-id" value="provider-local"></label><label>Model ID<input id="model-id" value="model-manual"></label><label>价格版本<input id="price-version" value="manual-v1"></label><label>币种<input id="price-currency" value="CNY"></label><div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary" data-action="save-metadata">保存 metadata</button></div></section></div>`;
  if (state.dialog === 'recycle') return `<div class="scrim"><section class="dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="关闭">${icon(icons.close, '关闭')}</button><p class="overline">本地软删除</p><h2 id="dialog-title">回收站</h2><p>此处恢复通用软删除对象；会话的恢复与永久删除在“设置 → 对话管理 → 回收站”。恢复将生成新 revision。</p><div class="recycle-list">${state.history.recycleBin.length ? state.history.recycleBin.map(item => `<div><span>${escape(item.entity)} · ${escape(item.title)}</span><button data-action="restore" data-entity="${escape(item.entity)}" data-id="${escape(item.id)}" data-revision="${item.revision}">恢复</button></div>`).join('') : '<p class="empty-copy">暂无可恢复对象。</p>'}</div><div class="dialog-actions"><button data-action="close-dialog">关闭</button></div></section></div>`;
  if (state.dialog === 'about') return `<div class="scrim"><section class="dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="关闭">${icon(icons.close, '关闭')}</button><p class="overline">Desktop 本机交付摘要</p><h2 id="dialog-title">关于南枫 AI Desktop</h2><dl class="about-list"><dt>版本</dt><dd>0.6.0-p6d-dev</dd><dt>账号与同步</dt><dd>尚未配置 · 离线可用；无浏览器登录、无 HTTP、无同步队列</dd><dt>签名</dt><dd>ad-hoc 开发签名，未 notarized</dd><dt>最低系统</dt><dd>macOS 11 或更高</dd><dt>本地数据</dt><dd>仅 app-private 容器；此处不显示路径或数据库文件</dd><dt>更新</dt><dd>本地静态状态；未检查网络</dd></dl><p>卸载应用不会主动删除用户本地数据；清除数据必须通过未来独立的安全流程，不暴露 SQLite 文件。</p><div class="dialog-actions"><button data-action="close-dialog">关闭</button></div></section></div>`;
  if (state.dialog?.kind === 'search-attachment-actions') { const hit = state.dialog.hit; return `<div class="scrim"><section class="dialog search-attachment-actions" role="dialog" aria-modal="true"><p class="overline">本机附件</p><h2>${escape(hit.displayName || '附件')}</h2><p>${escape(hit.mimeType || '未知类型')} · ${bytes(Number(hit.byteCount) || 0)}</p><div class="dialog-actions"><button data-action="close-dialog">取消</button><button data-action="locate-search-attachment" data-entry-id="${escape(hit.entryId)}">快速定位</button><button class="danger" data-action="ask-delete-search-attachment" data-entry-id="${escape(hit.entryId)}">删除此引用</button></div></section></div>`; }
  if (state.dialog?.kind === 'transcription-delete') { const task = state.transcription.tasks.find(item => item.id === state.dialog.taskId); const busy = state.dialog.submitting; return `<div class="scrim"><section class="dialog" role="alertdialog" aria-modal="true" aria-busy="${busy}"><h2>删除这条转写任务？</h2><p>将删除“${escape(task?.sourceDisplayName || '转写任务')}”的任务记录与时间轴。原文件或结果仍被会话引用时，私有字节不会删除；无引用字节进入安全清理期。</p>${state.dialog.failure ? `<p class="dialog-error" role="alert">${escape(state.dialog.failure)}</p>` : ''}<div class="dialog-actions"><button data-action="close-dialog" ${busy ? 'disabled' : ''}>取消</button><button class="primary danger" data-action="confirm-delete-transcription-task" data-task-id="${escape(state.dialog.taskId)}" ${busy ? 'disabled' : ''}>${busy ? '正在删除…' : '删除任务'}</button></div></section></div>`; }
  if (state.dialog?.kind === 'search-attachment-delete') { const hit = state.dialog.hit; const busy = state.dialog.submitting; return `<div class="scrim"><section class="dialog" role="alertdialog" aria-modal="true" aria-busy="${busy}"><h2>删除此附件引用？</h2><p>只会从这条消息移除“${escape(hit.displayName || '附件')}”。若其他消息仍在引用，私有副本会继续保留；最后一个引用移除后进入安全清理期。</p>${state.dialog.failure ? `<p class="dialog-error" role="alert">${escape(state.dialog.failure)}</p>` : ''}<div class="dialog-actions"><button data-action="close-dialog" ${busy ? 'disabled' : ''}>取消</button><button class="primary danger" data-action="confirm-delete-search-attachment" data-entry-id="${escape(hit.entryId)}" ${busy ? 'disabled' : ''}>${busy ? '正在删除…' : '删除此引用'}</button></div></section></div>`; }
  if (state.dialog?.kind === 'conversation-rename') return `<div class="scrim"><section class="dialog" role="dialog" aria-modal="true"><label>会话标题<input id="conversation-rename" maxlength="120" value="${escape(state.dialog.title)}"></label><div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary" data-action="save-conversation-rename" data-id="${escape(state.dialog.id)}" data-revision="${state.dialog.revision}">保存</button></div></section></div>`;
  if (state.dialog?.kind === 'conversation-project') { const projects = active(workspace(), 'projects'); const options = [`<option value="">不归入项目</option>`, ...projects.map(project => `<option value="${escape(project.id)}" ${project.id === state.dialog.projectId ? 'selected' : ''}>${escape(project.title)}</option>`)].join(''); return `<div class="scrim"><section class="dialog" role="dialog" aria-modal="true"><h2>项目归属</h2><p>${projects.length ? '选择现有本地项目；切换即为移动，取消不变更。' : '当前没有可选项目。'}</p><label>项目<select id="conversation-project">${options}</select></label><div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary" data-action="save-conversation-project" data-id="${escape(state.dialog.id)}" data-revision="${state.dialog.revision}">保存</button></div></section></div>`; }
  if (state.dialog?.kind === 'conversation-delete') { const busy = state.dialog.submitting; return `<div class="scrim"><section class="dialog" role="dialog" aria-modal="true" aria-busy="${busy}"><h2>移入回收站？</h2><p>删除不同于归档：消息树不会物理删除，可从回收站恢复。</p>${state.dialog.failure ? `<p class="dialog-error" role="alert">${escape(state.dialog.failure)}</p>` : ''}<div class="dialog-actions"><button data-action="close-dialog" ${busy ? 'disabled' : ''}>取消</button><button class="primary" data-action="confirm-conversation-delete" data-id="${escape(state.dialog.id)}" data-revision="${state.dialog.revision}" ${busy ? 'disabled' : ''}>${busy ? '正在移入…' : '移入回收站'}</button></div></section></div>`; }
  if (state.dialog?.kind === 'conversation-permanent-delete') { const busy = state.dialog.submitting; return `<div class="scrim"><section class="dialog" role="dialog" aria-modal="true" aria-busy="${busy}"><h2>永久删除？</h2><p>将永久删除“${escape(state.dialog.title)}”，无法恢复。</p>${state.dialog.failure ? `<p class="dialog-error" role="alert">${escape(state.dialog.failure)}</p>` : ''}<div class="dialog-actions"><button data-action="close-dialog" ${busy ? 'disabled' : ''}>取消</button><button class="primary danger" data-action="confirm-conversation-permanent-delete" data-id="${escape(state.dialog.id)}" data-revision="${state.dialog.revision}" ${busy ? 'disabled' : ''}>${busy ? '正在永久删除…' : '永久删除'}</button></div></section></div>`; }
  if (state.dialog?.kind === 'conversation-bulk-cleanup') { const busy = state.dialog.submitting; const recycle = state.dialog.scope === 'recycle'; return `<div class="scrim"><section class="dialog" role="dialog" aria-modal="true" aria-busy="${busy}"><h2>${recycle ? '清空回收站？' : '清空已归档？'}</h2><p>${recycle ? `将永久删除 ${state.dialog.count} 个会话，无法恢复。` : `${state.dialog.count} 个会话将移入回收站，可恢复。`}</p>${state.dialog.failure ? `<p class="dialog-error" role="alert">${escape(state.dialog.failure)}</p>` : ''}<div class="dialog-actions"><button data-action="close-dialog" ${busy ? 'disabled' : ''}>取消</button><button class="primary ${recycle ? 'danger' : ''}" data-action="confirm-conversation-bulk-cleanup" data-scope="${state.dialog.scope}" ${busy ? 'disabled' : ''}>${busy ? '正在处理…' : recycle ? '永久删除' : '移入回收站'}</button></div></section></div>`; }
  if (state.dialog?.kind === 'local-backup-restart-required') return `<div class="scrim"><section class="dialog" role="alertdialog" aria-modal="true" aria-labelledby="dialog-title"><p class="overline">本机恢复已完成</p><h2 id="dialog-title">请完全重启 App</h2><p>数据库和受控资产已经替换并通过回读。为避免旧 SQLite、页面和任务引用，现请手动完全退出并重新打开 App；不会自动继续任何任务。</p></section></div>`;
  if (state.dialog?.kind === 'privacy-cleanup-scope') return `<div class="scrim"><section class="dialog privacy-cleanup-dialog" role="dialog" aria-modal="true"><h2>选择清理范围</h2><div class="privacy-scope-list"><button data-action="select-privacy-cleanup-scope" data-scope="TEMPORARY_FAILED_TASK_ASSETS"><span><strong>清理失败任务</strong><small>选择后可逐项清理失败任务的附件。</small></span>${icon(icons.chevronRight || icons.info, '进入')}</button><button data-action="select-privacy-cleanup-scope" data-scope="ALL_LOCAL_BUSINESS_DATA"><span><strong>删除全部本地数据</strong><small>删除全部本机业务数据，需输入确认文字。</small></span>${icon(icons.chevronRight || icons.info, '进入')}</button></div><p class="android-settings-helper">已归档与回收站对话统一在“对话管理”中清理。</p><div class="dialog-actions"><button data-action="close-dialog">取消</button></div></section></div>`;
  if (state.dialog?.kind === 'privacy-cleanup-preview') {
    const preview = state.dialog.preview;
    const failedTasks = preview.scope === 'TEMPORARY_FAILED_TASK_ASSETS';
    const fullDelete = preview.scope === 'ALL_LOCAL_BUSINESS_DATA';
    const selected = new Set(state.dialog.selectedTaskIds || []);
    const candidates = preview.taskCandidates || [];
    const hasSelectionPreview = !failedTasks || preview.aggregates?.length > 0;
    return `<div class="scrim"><section class="dialog privacy-cleanup-dialog" role="dialog" aria-modal="true" aria-busy="${Boolean(state.dialog.submitting)}"><h2>${failedTasks ? '清理失败任务' : fullDelete ? '删除全部本地数据' : '清空知识与记忆回收站'}</h2>${failedTasks ? `<p>选择要清理的失败任务。</p><div class="privacy-task-list">${candidates.map(candidate => `<label><input type="checkbox" data-action="toggle-privacy-task" data-id="${escape(candidate.selectionId)}" ${selected.has(candidate.selectionId) ? 'checked' : ''} ${state.dialog.submitting ? 'disabled' : ''}><span>失败任务 · 附件 ${Number(candidate.privateAssetCount || 0)} 个<small>${escape(candidate.adapter)} · ${escape(candidate.safeIdSummary)}</small></span></label>`).join('') || '<p class="empty-copy">没有可安全清理的失败任务。</p>'}</div><button class="privacy-preview-selection" data-action="preview-selected-privacy-tasks" ${!selected.size || state.dialog.submitting ? 'disabled' : ''}>预览已选 ${selected.size} 项</button>` : ''}${hasSelectionPreview ? '<p class="privacy-confirmed">已确认清理范围。</p>' : ''}${fullDelete ? `<label>确认文字<input id="privacy-confirmation" value="${escape(state.dialog.confirmation || '')}" placeholder="输入：删除全部本地业务数据" ${state.dialog.submitting ? 'disabled' : ''}></label>` : ''}${state.dialog.failure ? `<p class="dialog-error" role="alert">${escape(state.dialog.failure)}</p>` : ''}<div class="dialog-actions"><button data-action="close-dialog" ${state.dialog.submitting ? 'disabled' : ''}>取消</button>${hasSelectionPreview ? `<button class="primary danger" data-action="confirm-privacy-cleanup" ${state.dialog.submitting || (fullDelete && state.dialog.confirmation !== '删除全部本地业务数据') ? 'disabled' : ''}>${state.dialog.submitting ? '正在清理…' : fullDelete ? '确认删除全部本地业务数据' : '确认删除此范围'}</button>` : ''}</div></section></div>`;
  }
  return '';
}
function tree(data) { if (!data) return '<div class="tree-empty">还没有本地工作区。<br>先从受控交换包导入。</div>'; const workspaceRows = state.workspaces.map(item => `<button class="tree-row ${item.id === data.summary.id ? 'selected' : ''}" data-action="select-workspace" data-id="${escape(item.id)}"><span>${icon(icons.workspace, '工作区')}</span><span>${escape(item.title)}</span><small>${item.id === data.summary.id ? '当前' : ''}</small></button>`).join(''); return `<div class="tree-section"><p>Workspace</p>${workspaceRows}</div><div class="tree-section"><p>Project <button class="small-add" data-action="new-project" aria-label="新建 Project">+</button></p>${data.exchange.projects.map(item => `<button class="tree-row" data-action="edit-project" data-id="${escape(item.id)}"><span>${icon(icons.workspace, '项目')}</span><span>${escape(item.title)}</span><small>r${item.revision || 0}</small></button>`).join('') || '<span class="tree-muted">暂无 Project</span>'}</div><div class="tree-section"><p>Conversation</p>${data.exchange.conversations.map(item => `<button class="tree-row ${state.pane === 'conversation' ? 'selected' : ''}" data-action="show-conversation"><span>${icon(icons.conversation, '会话')}</span><span>${escape(item.title)}</span><small>${item.messages.length}</small></button>`).join('')}</div><div class="tree-section"><p>Knowledge</p><button class="tree-row ${state.pane === 'knowledge' ? 'selected' : ''}" data-action="show-knowledge"><span>${icon(icons.knowledge, '知识')}</span><span>知识与记忆</span><small>${data.exchange.knowledge.length}</small></button></div>`; }
function conversationCanvas(data) { const conversation = data.exchange.conversations[0]; if (!conversation) return `<section class="canvas empty-canvas"><h2>暂无会话</h2><p>新建 Conversation 会在 Rust transaction 中同时建立明确的本地 root 节点。</p></section>`; return `<section class="canvas conversation-canvas"><div class="canvas-header"><div><p class="overline">Conversation · 本地树</p><h1>${escape(conversation.title)}</h1><p>追加节点只接受明确本地文本，不构造 Prompt 或 RunSpec。</p></div><button class="subtle" data-action="show-knowledge">知识工作台</button></div><div class="message-list">${conversation.messages.map(message => `<article class="message ${escape(message.role)}"><header><span>${escape(message.role)}</span><time>${escape(short(message.createdAt))}</time></header>${message.blocks.map(block => `<pre>${escape(block.text || `附件引用 · ${block.asset?.displayName || ''}`)}</pre>`).join('')}</article>`).join('')}</div></section>`; }
function knowledgeCanvas(data) { const knowledge = active(data, 'knowledge'); const memory = active(data, 'memory'); const relationItems = active(data, 'relations'); const name = id => data.exchange.knowledge.find(item => item.id === id)?.title || short(id); return `<section class="canvas knowledge-canvas"><div class="canvas-header"><div><p class="overline">Knowledge / Memory · 文本 IR</p><h1>知识与记忆</h1><p>保存、取消、冲突、撤销与软删除均由 Rust SQLite 真值驱动。</p></div><div class="canvas-actions"><button class="primary" data-action="new-knowledge">${icon(icons.plus, '新建')}新建 Knowledge</button><button data-action="new-memory">${icon(icons.plus, '新建')}新建 Memory</button><button data-action="new-relation" ${knowledge.length < 2 ? 'disabled' : ''}>${icon(icons.link, '关系')}建立关系</button></div></div><div class="knowledge-list">${knowledge.map(item => `<article class="knowledge-item"><header><div><p class="overline">${escape(item.status)} · r${item.revision} · ${escape(item.classification)}</p><h2>${escape(item.title)}</h2></div><span>${escape((item.tags || []).join(' · '))}</span></header><pre>${escape(item.body)}</pre><div class="item-actions"><button data-action="edit-knowledge" data-id="${escape(item.id)}">${icon(icons.edit, '编辑')}编辑</button><button data-action="delete-knowledge" data-id="${escape(item.id)}" data-revision="${item.revision}">${icon(icons.trash, '软删除')}软删除</button></div></article>`).join('') || '<p class="empty-copy">暂无活动 Knowledge。</p>'}</div><section class="memory-rail"><div class="section-head"><h2>Memory</h2><button data-action="new-memory">新建</button></div><ul>${memory.map(item => `<li><span>${escape(item.scope)} · r${item.revision}</span><button class="memory-button" data-action="edit-memory" data-id="${escape(item.id)}">${escape(item.body)}</button><button data-action="delete-memory" data-id="${escape(item.id)}" data-revision="${item.revision}">软删除</button></li>`).join('') || '<li>暂无活动 Memory。</li>'}</ul></section><section class="memory-rail"><div class="section-head"><h2>Knowledge relation</h2><button data-action="new-relation" ${knowledge.length < 2 ? 'disabled' : ''}>建立</button></div><ul>${relationItems.map(item => `<li><span>${escape(item.kind)} · r${item.revision}</span>${escape(name(item.fromId))} → ${escape(name(item.toId))}<button data-action="delete-relation" data-id="${escape(item.id)}" data-revision="${item.revision}">撤销</button></li>`).join('') || '<li>暂无活动 relation。</li>'}</ul></section></section>`; }
function inspector(data) { if (!data) return `<aside class="inspector"><div class="inspector-head"><h2>Inspector</h2></div><p class="empty-copy">导入后显示本地工作区。</p></aside>`; const meta = state.history.modelMetadata; return `<aside class="inspector"><div class="inspector-head"><div><p class="overline">本地事实</p><h2>Inspector</h2></div><button class="icon-button" data-action="toggle-inspector" aria-label="收起">${icon(icons.close, '收起')}</button></div><section><h3>revision 与恢复</h3><div class="inspector-actions"><button data-action="undo" ${state.history.canUndo && native ? '' : 'disabled'}>${icon(icons.undo, '撤销')}撤销</button><button data-action="redo" ${state.history.canRedo && native ? '' : 'disabled'}>${icon(icons.redo, '重做')}重做</button><button data-action="recycle">${icon(icons.trash, '回收站')}回收站 ${state.history.recycleBin.length}</button></div><p class="security-note">动作栈持久化；重启后仍可恢复。冲突会保留当前 revision，绝不静默覆盖。</p></section><section><h3>模型与成本 metadata</h3>${meta.length ? meta.map(item => `<p class="metadata-row"><b>${escape(item.providerId)} / ${escape(item.modelId)}</b><br>${escape(item.metadata?.pricingStatus || 'UNKNOWN')} · ${escape(item.metadata?.priceVersion || '未配置')} · ${escape(item.metadata?.currency || '—')}</p>`).join('') : '<p class="empty-copy">未配置。无网络 catalog、无 Key、无真实费用。</p>'}<button data-action="metadata">${icon(icons.plus, '配置')}配置本地 metadata</button></section><section><h3>工作区</h3><dl><dt>状态</dt><dd>离线 · Rust SQLite</dd><dt>语义 hash</dt><dd title="${escape(data.summary.semanticHash)}">${escape(short(data.summary.semanticHash))}</dd><dt>项目 / 知识</dt><dd>${data.summary.projectCount} / ${data.summary.knowledgeCount}</dd><dt>关系 / 资产</dt><dd>${data.summary.relationCount} / ${data.summary.assetCount}</dd><dt>资产字节</dt><dd>${bytes(data.summary.assetByteCount)}</dd></dl></section><section class="security-note"><h3>安全边界</h3><p>无账号、无网络、无 Provider、无 Prompt/RunSpec。正文不执行。</p></section></aside>`; }
function render() { const data = workspace(); if (state.pane === 'chat' || state.pane === 'connections') { app.className = 'app-shell chat-first'; app.innerHTML = renderChatFirstShell({ data, native, selectedConversationId: state.selectedConversationId, composerDraft: state.composerDraft, profileOpen: state.profileOpen, pane: state.pane, status: state.status, error: state.error, connection: state.connection }); return; } const canvas = state.pane === 'p8-inspect' ? p8InspectCanvas({ agentRuns: state.agentRuns, native, escape, short }) : data ? (state.pane === 'conversation' ? conversationCanvas(data) : knowledgeCanvas(data)) : `<section class="canvas empty-canvas"><p class="overline">P6-D · 离线 Desktop</p><h1>从受控交换包开始</h1><p>导入后可在 Rust 本地领域链中明确创建、编辑、撤销与软删除。</p><button class="primary" data-action="start-import">选择交换包</button></section>`; app.className = `app-shell scale-${state.scale === 2 ? '2' : '1'}`; app.innerHTML = `<aside class="sidebar ${state.treeOpen ? 'mobile-open' : ''}"><header class="brand"><span class="brand-mark">南</span><span><strong>南枫 AI</strong><small>本地工作台</small></span><button class="icon-button mobile-close" data-action="toggle-tree" aria-label="关闭导航">${icon(icons.close, '关闭')}</button></header><nav class="primary-nav"><button data-action="show-chat"><span>${icon(icons.conversation, '聊天')}</span>返回聊天</button><button class="nav-active"><span>${icon(icons.workspace, '工作区')}</span>工作区</button><button data-action="show-conversation"><span>${icon(icons.conversation, '会话')}</span>会话</button><button data-action="show-knowledge"><span>${icon(icons.knowledge, '知识')}</span>知识</button><button data-action="show-p8-inspect"><span>${icon(icons.info, '本地受控记录')}</span>本地受控记录</button></nav><section class="tree"><div class="tree-label"><span>Workspace</span><button class="icon-button" data-action="start-import" aria-label="导入">${icon(icons.import, '导入')}</button></div>${tree(data)}</section><footer><span class="offline-dot"></span>本地可用 · 联网需配置</footer></aside><section class="main-area"><header class="topbar"><button class="icon-button tree-toggle" data-action="toggle-tree" aria-label="打开导航">${icon(icons.workspace, '导航')}</button><div class="topbar-status"><span class="offline-dot"></span><span>${native ? 'Rust SQLite 本地所有权' : 'Web 预览（不写入）'}</span></div><div class="topbar-actions"><button data-action="undo" ${data && state.history.canUndo && native ? '' : 'disabled'}>${icon(icons.undo, '撤销')}撤销</button><button data-action="redo" ${data && state.history.canRedo && native ? '' : 'disabled'}>${icon(icons.redo, '重做')}重做</button><button data-action="start-export" ${data && native ? '' : 'disabled'}>${icon(icons.export, '导出')}导出</button><button data-action="toggle-scale" aria-label="切换应用缩放">${state.scale.toFixed(1)}×</button><button class="icon-button" data-action="about" aria-label="关于">${icon(icons.info, '关于')}</button><button class="icon-button" data-action="toggle-inspector" aria-label="${state.inspectorOpen ? '收起' : '展开'} Inspector">${icon(icons.knowledge, 'Inspector')}</button></div></header><p class="status ${state.error ? 'error' : ''}" role="status">${escape(state.error || state.status)}</p><div class="workbench ${state.inspectorOpen ? '' : 'inspector-collapsed'}">${canvas}${state.inspectorOpen ? inspector(data) : ''}</div></section>${dialog()}`; if (state.dialog) queueMicrotask(() => document.querySelector('.dialog input, .dialog textarea, .dialog select, .dialog button')?.focus()); }
async function refresh() {
  if (!native) {
    state.workspaces = [fixture.summary];
    state.current = fixture;
    state.p6gCatalog = { revision: 0, snapshot: { catalogVersion: 'web-preview-unconfigured', policyVersion: 1, candidates: [] } };
    state.p6gGlobalDefault = { revision: 0, tier: null };
    state.p6gSelection = null;
    state.agentRuns = [];
    state.p6eAcceptance = { enabled: false, receipt: null };
    state.transcription = { settings: { ...TRANSCRIPTION_PREVIEW_STATE.settings }, tasks: [] };
    if (!state.selectedConversationId) selectWorkspaceDefaultConversation(fixture);
    reloadFavoriteConversationIds();
    await loadConversationReadState({ observeSelected: true });
    recomputeConversationFind();
    render();
    return;
  }
  state.workspaces = await invoke('list_desktop_workspaces');
  state.current = state.workspaces.length ? await invoke('read_desktop_workspace', { workspaceId: state.current?.summary?.id || state.workspaces[0].id }) : null;
  if (state.current && !resolveConversation(state.current, state.selectedConversationId)) selectWorkspaceDefaultConversation(state.current);
  reloadFavoriteConversationIds();
  await loadConversationReadState({ observeSelected: true });
  recomputeConversationFind();
  state.p6gCatalog = await invoke('read_desktop_p6g_catalog');
  state.p6gGlobalDefault = await invoke('read_desktop_p6g_global_default');
  state.p6gSelection = state.current && state.selectedConversationId ? await invoke('read_desktop_p6g_selection', { workspaceId: state.current.summary.id, conversationId: state.selectedConversationId }) : null;
  state.history = state.current ? await invoke('read_desktop_workbench_history', { workspaceId: state.current.summary.id }) : { canUndo: false, canRedo: false, recycleBin: [], modelMetadata: [] };
  state.agentRuns = await invoke('inspect_p8_agent_runs');
  state.p6eAcceptance = await invoke('read_p6e_temporary_maintenance_acceptance_status');
  state.runtimeInfo = await invoke('read_desktop_runtime_info');
  await loadTranscriptionState();
  if (state.p6eAcceptance.enabled && state.pane === 'chat') state.pane = 'connections';
  render();
}

async function runP6eTemporaryMaintenanceAcceptance() {
  if (!state.p6eAcceptance.enabled) return;
  try {
    state.p6eAcceptance = { enabled: true, receipt: await invoke('run_p6e_temporary_maintenance_acceptance') };
    state.status = 'P6-E 固定 23h59 / 24h owner 维护已运行；回执保存在 acceptance 私有根目录，重启后可读。';
    state.error = '';
  } catch (error) {
    state.error = `P6-E 验收维护失败：${String(error)}`;
  }
  render();
}

app.addEventListener('click', event => {
  if (event.target.closest('[data-action]')?.dataset.action !== 'run-p6e-temporary-maintenance-acceptance') return;
  event.preventDefault();
  event.stopImmediatePropagation();
  void runP6eTemporaryMaintenanceAcceptance();
}, true);
async function pickExchange() { if (!native) { state.status = 'Web 预览不会请求文件；请在 Tauri Desktop 开发包中使用系统 picker。'; render(); return; } const selectedPath = await dialogInvoke('open', { multiple: false, directory: false, filters: [{ name: '南枫 AI 交换包', extensions: ['nfai-exchange'] }] }); if (!selectedPath) return; try { state.preflight = await invoke('stage_preflight_selected_exchange', { selectedPath }); state.current = await invoke('import_staged_exchange_as_new_workspace', { args: { stagingId: state.preflight.stagingId, workspaceTitle: '导入工作区' } }); state.preflight = null; state.dialog = null; state.status = '交换包已通过严格预检并直接导入为新的独立工作区。'; state.error = ''; await refresh(); } catch (error) { state.error = `导入被拒绝：${String(error)}`; } render(); }
function p6hImportLifecycleMarker(phase) { if (!p6hDiagnosticsEnabled) return; let marker = document.querySelector('#p6h-dom-event-marker'); if (!marker) { marker = document.createElement('p'); marker.id = 'p6h-dom-event-marker'; marker.setAttribute('role', 'status'); marker.style.cssText = 'position:fixed;z-index:9999;left:12px;bottom:12px;padding:6px;background:#173;color:#fff;font-size:12px'; document.body.append(marker); } marker.textContent = `P6-H acceptance import: ${phase}`; }
async function commitSelectedImport({ taskKey, stageCommand, readCommand, confirmCommand, selectedPath, label }) {
  if (!state.current) throw new Error('请先导入或创建一个本地工作区。');
  const staged = await invoke(stageCommand, { selectedPath });
  let task = await invoke(readCommand, { taskId: staged.id });
  let rejected = 0;
  for (const item of task.items.filter(candidate => candidate.status === 'PENDING_CONFIRMATION')) {
    try {
      await invoke(confirmCommand, { args: { workspaceId: state.current.summary.id, taskId: task.id, itemId: item.id } });
    } catch {
      // The item stays independently recoverable in its persisted task; selection remains one command.
      rejected += 1;
    }
  }
  task = await invoke(readCommand, { taskId: staged.id });
  state[taskKey] = task;
  state.dialog = null;
  if (state.current) await refresh();
  state.status = `${label} 已直接执行导入；失败或拒绝 ${rejected} 项可在回执中查看。`;
  state.error = '';
  return task;
}
async function commitPendingImport({ taskKey, readCommand, confirmCommand, label }) {
  if (!state.current || !state[taskKey]) throw new Error('需要一个本地工作区和可恢复的导入任务。');
  let task = await invoke(readCommand, { taskId: state[taskKey].id });
  let rejected = 0;
  for (const item of task.items.filter(candidate => candidate.status === 'PENDING_CONFIRMATION')) {
    try { await invoke(confirmCommand, { args: { workspaceId: state.current.summary.id, taskId: task.id, itemId: item.id } }); } catch { rejected += 1; }
  }
  task = await invoke(readCommand, { taskId: task.id });
  state[taskKey] = task;
  await refresh();
  state.status = `${label} 已直接续作；失败或拒绝 ${rejected} 项保留独立回执。`;
  state.error = '';
}
async function pickChatGptExport() { if (!native) { state.status = 'Web 预览不会请求文件；请在 Tauri Desktop 开发包中使用系统 picker。'; render(); return; } p6hImportLifecycleMarker('picker-open'); const selectedPath = await dialogInvoke('open', { multiple: false, directory: false, filters: [{ name: 'ChatGPT conversations.json', extensions: ['json'] }] }); if (!selectedPath) { p6hImportLifecycleMarker('picker-cancel'); return; } p6hImportLifecycleMarker('picker-returned'); try { await commitSelectedImport({ taskKey: 'chatgptTask', stageCommand: 'stage_chatgpt_export_selected', readCommand: 'read_chatgpt_import_task', confirmCommand: 'confirm_chatgpt_import_item', selectedPath, label: 'ChatGPT' }); p6hImportLifecycleMarker('direct-commit-succeeded'); } catch (error) { p6hImportLifecycleMarker('stage-error'); state.error = `ChatGPT 导入被拒绝：${String(error)}`; state.status = state.error; } render(); }
async function pickClaudeExport() { if (!native) { state.status = 'Web 预览不会请求文件；请在 Tauri Desktop 开发包中使用系统 picker。'; render(); return; } const selectedPath = await dialogInvoke('open', { multiple: false, directory: false, filters: [{ name: 'Claude conversations.json', extensions: ['json'] }] }); if (!selectedPath) return; try { await commitSelectedImport({ taskKey: 'claudeTask', stageCommand: 'stage_claude_export_selected', readCommand: 'read_claude_import_task', confirmCommand: 'confirm_claude_import_item', selectedPath, label: 'Claude' }); } catch (error) { state.error = `Claude 导入被拒绝：${String(error)}`; state.status = state.error; } render(); }
async function pickP6kZip(provider) { if (!native) { state.status = 'Web 预览不会请求文件；请在 Tauri Desktop 开发包中使用系统 picker。'; render(); return; } if (!state.current) { state.error = '先导入或创建一个本地工作区。'; render(); return; } const selectedPath = await dialogInvoke('open', { multiple: false, directory: false, filters: [{ name: `${provider} data export ZIP`, extensions: ['zip'] }] }); if (!selectedPath) return; try { state.p6kTask = await invoke('stage_p6k_zip_import_selected', { args: { workspaceId: state.current.summary.id, provider, selectedPath } }); state.status = `${provider} ZIP 已完成私有校验并直接导入：${state.p6kTask.importedCount} 个会话；失败 ${state.p6kTask.failedCount}，未关联媒体 ${state.p6kTask.unmappedAssetCount}。`; state.error = ''; await refresh(); } catch (error) { state.error = `ZIP 导入被拒绝：${String(error)}`; state.status = state.error; } render(); }
async function pickV2WorkspaceExchange() {
  if (!native) { state.status = 'Web 预览不会请求文件；请在 Tauri Desktop 开发包中使用系统 picker。'; render(); return; }
  const selectedPath = await dialogInvoke('open', { multiple: false, directory: false, filters: [{ name: '南枫 AI 完整工作区交换（v2）', extensions: ['nfai-exchange', 'zip'] }] });
  if (!selectedPath) return;
  try {
    const receipt = await invoke('import_desktop_workspace_exchange_v2_selected', { selectedPath });
    await refresh();
    state.v2CommittedExchanges = await invoke('list_desktop_workspace_exchange_v2_committed');
    globalThis.__nanfengV2CommittedExchanges = state.v2CommittedExchanges;
    state.status = `完整工作区交换（v2）已私有导入并回读：${short(receipt.semanticHash)} · ${receipt.assetCount} 项附件${receipt.replayed ? ' · 已验证重放回执' : ''}。`;
    state.error = '';
  } catch (error) {
    state.error = `完整工作区交换（v2）被拒绝：${String(error)}`;
    state.status = state.error;
  }
  render();
}
async function reexportV2WorkspaceExchange(workspaceId) {
  if (!native) { state.status = 'Web 预览不会请求保存位置；请在 Tauri Desktop 开发包中使用系统保存窗口。'; render(); return; }
  const selectedPath = await dialogInvoke('save', { defaultPath: 'nanfeng-ai-workspace-v2.nfai-exchange', filters: [{ name: '南枫 AI 完整工作区交换（v2）', extensions: ['nfai-exchange'] }] });
  if (!selectedPath) return;
  try {
    const receipt = await invoke('reexport_desktop_workspace_exchange_v2_selected', { workspaceId, selectedPath });
    state.status = `完整工作区交换（v2）已从私有记录回导并严格回读：${short(receipt.semanticHash)} · ${receipt.assetCount} 项附件。`;
    state.error = '';
  } catch (error) {
    state.error = `完整工作区交换（v2）回导被拒绝：${String(error)}`;
    state.status = state.error;
  }
  render();
}
async function pickNanfengKnowledgeExport() { if (!native) { state.status = 'Web 预览不会请求文件；请在 Tauri Desktop 开发包中使用系统 picker。'; render(); return; } const selectedPath = await dialogInvoke('open', { multiple: false, directory: false, filters: [{ name: '南枫知识库完整 JSON', extensions: ['json'] }] }); if (!selectedPath) return; try { await commitSelectedImport({ taskKey: 'nanfengKnowledgeTask', stageCommand: 'stage_nanfeng_knowledge_export_selected', readCommand: 'read_nanfeng_knowledge_import_task', confirmCommand: 'confirm_nanfeng_knowledge_import_item', selectedPath, label: '南枫知识库' }); } catch (error) { state.error = `知识库导入被拒绝：${String(error)}`; state.status = state.error; } render(); }
app.addEventListener('click', event => { const target = event.target.closest?.('[data-action="select-chatgpt-export"]'); if (!target) return; event.preventDefault(); event.stopImmediatePropagation(); pickChatGptExport(); }, true);
app.addEventListener('click', event => { const target = event.target.closest?.('[data-action="select-claude-export"]'); if (!target) return; event.preventDefault(); event.stopImmediatePropagation(); pickClaudeExport(); }, true);
app.addEventListener('click', event => { const target = event.target.closest?.('[data-action="select-p6k-chatgpt-zip"],[data-action="select-p6k-claude-zip"]'); if (!target) return; event.preventDefault(); event.stopImmediatePropagation(); pickP6kZip(target.dataset.action === 'select-p6k-chatgpt-zip' ? 'CHATGPT' : 'CLAUDE'); }, true);
app.addEventListener('click', event => { const target = event.target.closest?.('[data-action="select-v2-workspace-exchange"]'); if (!target) return; event.preventDefault(); event.stopImmediatePropagation(); pickV2WorkspaceExchange(); }, true);
app.addEventListener('click', event => { const target = event.target.closest?.('[data-action="reexport-v2-workspace-exchange"]'); if (!target) return; event.preventDefault(); event.stopImmediatePropagation(); reexportV2WorkspaceExchange(target.dataset.workspaceId); }, true);
app.addEventListener('click', async event => { const target = event.target.closest?.('[data-action="retry-p6k-zip"],[data-action="skip-p6k-zip-failures"],[data-action="delete-p6k-zip-batch"]'); if (!target || !state.p6kTask) return; event.preventDefault(); event.stopImmediatePropagation(); try { if (target.dataset.action === 'retry-p6k-zip') state.p6kTask = await invoke('retry_p6k_zip_import_task', { taskId: state.p6kTask.id }); else if (target.dataset.action === 'skip-p6k-zip-failures') state.p6kTask = await invoke('skip_p6k_zip_import_failures', { taskId: state.p6kTask.id }); else { await invoke('delete_p6k_zip_import_batch', { taskId: state.p6kTask.id }); state.p6kTask = null; } state.status = target.dataset.action === 'delete-p6k-zip-batch' ? '已软删除该导入批次并移除其回执；私有副本已清除。' : 'ZIP 导入状态已更新。'; state.error = ''; if (state.current) await refresh(); } catch (error) { state.error = `ZIP 导入状态未更新：${String(error)}`; } render(); }, true);
app.addEventListener('click', async event => { const target = event.target.closest?.('[data-action="select-p6k-manual-asset"],[data-action="select-p6k-manual-target"],[data-action="link-p6k-manual-asset"]'); if (!target || !state.p6kTask) return; event.preventDefault(); event.stopImmediatePropagation(); if (target.dataset.action === 'select-p6k-manual-asset') { p6kManualLink = { assetOrdinal: Number(target.dataset.assetOrdinal), conversationId: null, messageId: null }; globalThis.__nanfengP6kManualLink = p6kManualLink; render(); return; } if (target.dataset.action === 'select-p6k-manual-target') { p6kManualLink = { ...p6kManualLink, conversationId: target.dataset.conversationId, messageId: target.dataset.messageId }; globalThis.__nanfengP6kManualLink = p6kManualLink; render(); return; } if (p6kManualLink.assetOrdinal === null || !p6kManualLink.conversationId || !p6kManualLink.messageId || !state.current) return; try { state.p6kTask = await invoke('link_p6k_zip_manual_asset', { args: { taskId: state.p6kTask.id, assetOrdinal: p6kManualLink.assetOrdinal, workspaceId: state.current.summary.id, conversationId: p6kManualLink.conversationId, messageId: p6kManualLink.messageId } }); p6kManualLink = { assetOrdinal: null, conversationId: null, messageId: null }; globalThis.__nanfengP6kManualLink = p6kManualLink; state.status = '已按所选媒体与消息建立精确关联；附件现在只在该消息位置呈现。'; state.error = ''; await refresh(); } catch (error) { state.error = `媒体关联未完成：${String(error)}`; } render(); }, true);
app.addEventListener('click', event => { const target = event.target.closest?.('[data-action="select-nanfeng-knowledge-export"]'); if (!target) return; event.preventDefault(); event.stopImmediatePropagation(); pickNanfengKnowledgeExport(); }, true);
const settingsParent = page => ['model-configuration', 'conversation-cost', 'context-selections', 'diagnostics'].includes(page)
  ? 'model'
  : ['favorites', 'archived', 'recycle'].includes(page) ? 'conversations'
    : page === 'memory-overview' ? 'personalization'
      : ['json-import-results', 'zip-import-results'].includes(page) ? 'data' : null;
function writeProductSettings(patch) {
  const previous = state.productSettings;
  state.productSettings = normalizeProductSettings({ ...state.productSettings, ...patch });
  if (!state.personalizationDirty) state.personalizationDraft = { ...state.productSettings };
  if (!native) {
    state.productSettings = parityPreferences.writeProductSettings(state.productSettings);
    return;
  }
  void persistNativeAppSettings(state.appearance, state.productSettings).then(async () => {
    if (!Object.hasOwn(patch, 'historyLibraryEnabled')) return;
    try {
      await loadDesktopHistoryKnowledge({ runDue: Boolean(state.productSettings.historyLibraryEnabled) });
      await loadDesktopBackgroundRuntime();
      state.error = '';
    } catch (error) {
      state.error = `历史资料库状态未更新：${String(error)}`;
    }
    render();
  }).catch(error => {
    state.productSettings = previous;
    if (!state.personalizationDirty) state.personalizationDraft = { ...previous };
    state.error = `Desktop 设置未保存：${String(error)}`;
    render();
  });
}

async function savePersonalization() {
  if (!state.settingsCapabilities.ordinaryChatPersonalization) return;
  const previous = state.productSettings;
  try {
    await persistNativeAppSettings(state.appearance, state.personalizationDraft);
    state.personalizationDraft = { ...state.productSettings };
    state.personalizationDirty = false;
    state.status = '';
    state.error = '';
  } catch (error) {
    state.productSettings = previous;
    state.error = `个性化设置未保存：${String(error)}`;
  }
  render();
}
app.addEventListener('click', event => {
  const target = event.target.closest?.('[data-action]');
  const action = target?.dataset.action;
  if (!['show-settings', 'show-settings-home', 'open-settings-page', 'settings-back', 'return-to-settings-conversation-list', 'open-settings-picker', 'dismiss-settings-picker', 'select-settings-picker-option', 'toggle-product-setting', 'save-personalization', 'open-custom-instructions-fullscreen', 'save-custom-instructions-fullscreen', 'select-model-service-provider', 'toggle-model-service-enabled', 'select-model-service-preset', 'reveal-model-service-credential', 'save-model-service-settings', 'test-model-service-connection', 'select-usage-section'].includes(action)) return;
  event.preventDefault();
  event.stopImmediatePropagation();
  if (action === 'show-settings') {
    state.pane = 'settings';
    state.settingsSection = 'personalization';
    state.settingsMobileHome = true;
    state.settingsPicker = null;
    state.personalizationDraft = { ...state.productSettings };
    state.personalizationDirty = false;
    state.profileOpen = false;
    state.error = '';
  } else if (action === 'open-settings-page') {
    state.settingsSection = target.dataset.page || 'personalization';
    state.settingsMobileHome = false;
    state.settingsPicker = null;
    if (state.settingsSection === 'personalization') {
      state.personalizationDraft = { ...state.productSettings };
      state.personalizationDirty = false;
    }
    if (state.settingsSection === 'model-configuration' && !state.modelServiceDraft) resetModelServiceDraft();
    if (state.settingsSection === 'reminders' && native) Promise.all([loadDesktopReminders(), loadDesktopBackgroundRuntime(), readReminderNotificationPermission()]).then(render).catch(error => { state.error = `提醒计划读取失败：${String(error)}`; render(); });
    if (state.settingsSection === 'conversation-cost' && native) loadDesktopUsageLedger().then(render).catch(error => { state.error = `费用记录读取失败：${String(error)}`; render(); });
    if (state.settingsSection === 'context-selections' && native) loadDesktopContextRecords().then(render).catch(error => { state.error = `上下文记录读取失败：${String(error)}`; render(); });
    if (state.settingsSection === 'diagnostics' && native) loadDesktopDiagnosticRecords().then(render).catch(error => { state.error = `运行诊断读取失败：${String(error)}`; render(); });
    if (state.settingsSection === 'privacy' && native) loadDesktopPrivacyInventory().then(render).catch(error => { state.error = `本机数据概况读取失败：${String(error)}`; render(); });
    if (state.settingsSection === 'account' && native) loadDesktopAccountSync().then(render).catch(error => { state.error = `账号同步状态读取失败：${String(error)}`; render(); });
    state.error = '';
  } else if (action === 'show-settings-home') {
    state.settingsMobileHome = true;
    state.settingsPicker = null;
    state.error = '';
  } else if (action === 'settings-back') {
    state.settingsSection = settingsParent(state.settingsSection) || 'personalization';
    state.settingsPicker = null;
    state.error = '';
  } else if (action === 'return-to-settings-conversation-list') {
    const destination = state.settingsConversationReturn;
    state.pane = 'settings';
    state.settingsSection = destination?.page || 'conversations';
    state.settingsMobileHome = Boolean(destination?.mobileHome);
    state.settingsConversationReturn = null;
    state.settingsPicker = null;
    state.error = '';
  } else if (action === 'open-settings-picker') {
    state.settingsPicker = target.dataset.picker || null;
  } else if (action === 'dismiss-settings-picker') {
    if (event.target.closest('[data-overlay-surface]')) return;
    state.settingsPicker = null;
  } else if (action === 'select-settings-picker-option') {
    const picker = target.dataset.picker;
    const value = target.dataset.value;
    if (picker === 'mode') updateAppearance('mode', value);
    else if (picker === 'fontSize') updateAppearance('fontSize', value);
    else if (picker === 'themeColor') updateAppearance('themeColor', value);
    else if (picker === 'tone') {
      state.personalizationDraft = { ...state.personalizationDraft, tone: value };
      state.personalizationDirty = true;
    }
    state.settingsPicker = null;
  } else if (action === 'toggle-product-setting') {
    const key = target.dataset.key;
    if (Object.hasOwn(state.productSettings, key) && typeof state.productSettings[key] === 'boolean') {
      const next = !state.productSettings[key];
      if (key === 'monitorNotifications' && next && native) {
        void requestReminderNotificationPermission().then(granted => {
          if (granted) writeProductSettings({ monitorNotifications: true });
          else state.error = '系统未授予通知权限；设置保持关闭，监控结果仍只保存在本机。';
          render();
        });
      } else {
        writeProductSettings({ [key]: next });
      }
      state.status = '';
    }
  } else if (action === 'save-personalization') {
    void savePersonalization();
  } else if (action === 'open-custom-instructions-fullscreen') {
    state.dialog = {
      kind: 'custom-instructions-fullscreen',
      previousDraft: { ...state.personalizationDraft },
      previousDirty: state.personalizationDirty,
    };
  } else if (action === 'save-custom-instructions-fullscreen') {
    state.dialog = null;
    void savePersonalization();
  } else if (action === 'select-model-service-provider') {
    state.modelProviderId = target.dataset.providerId || 'OPENROUTER';
    resetModelServiceDraft();
  } else if (action === 'toggle-model-service-enabled') {
    if (state.modelServiceDraft) state.modelServiceDraft = { ...state.modelServiceDraft, enabled: !state.modelServiceDraft.enabled };
    state.modelSettingsNotice = '';
    state.modelSettingsError = '';
  } else if (action === 'select-model-service-preset') {
    if (state.modelServiceDraft) state.modelServiceDraft = { ...state.modelServiceDraft, presetId: target.dataset.presetId || state.modelServiceDraft.presetId };
    state.settingsPicker = null;
    state.modelSettingsNotice = '';
    state.modelSettingsError = '';
  } else if (action === 'reveal-model-service-credential') {
    void revealModelServiceCredential();
  } else if (action === 'save-model-service-settings') {
    void saveModelServiceSettings();
  } else if (action === 'test-model-service-connection') {
    void testModelServiceConnection();
  } else if (action === 'select-usage-section') {
    state.usageSection = target.dataset.section || 'conversation';
  }
  render();
}, true);
app.addEventListener('click', async event => {
  const target = event.target.closest?.('[data-action="open-privacy-search-category"]');
  if (!target) return;
  event.preventDefault();
  event.stopImmediatePropagation();
  const category = target.dataset.category || 'all';
  state.chatSearch = '';
  state.pane = 'chat';
  state.profileOpen = false;
  state.error = '';
  await openFullSearch(category);
}, true);
async function previewPrivacyCleanup(scope, selectedTaskIds = []) {
  if (!native) {
    state.error = 'Web 预览不能清理本机数据。';
    render();
    return;
  }
  try {
    const preview = await invoke('preview_desktop_privacy_deletion', { args: { scope, selectedTaskIds } });
    state.dialog = { kind: 'privacy-cleanup-preview', preview, selectedTaskIds: [...selectedTaskIds], confirmation: '', submitting: false, failure: '' };
    state.error = '';
  } catch (error) {
    state.error = `清理范围预览失败：${String(error)}`;
    state.dialog = null;
  }
  render();
}
async function confirmPrivacyCleanup() {
  const current = state.dialog;
  if (!native || current?.kind !== 'privacy-cleanup-preview' || current.submitting) return;
  state.dialog = { ...current, submitting: true, failure: '' };
  render();
  try {
    const result = await invoke('delete_desktop_privacy_data', { args: {
      scope: current.preview.scope,
      previewFingerprint: current.preview.fingerprint,
      confirmationPhrase: current.confirmation || '',
      selectedTaskIds: current.selectedTaskIds || [],
    } });
    const fullDelete = current.preview.scope === 'ALL_LOCAL_BUSINESS_DATA';
    if (fullDelete) {
      window.localStorage.clear();
      state.current = null;
      state.workspaces = [];
      state.selectedConversationId = null;
      state.composerDraft = '';
      state.composerAttachments = [];
      state.favoriteConversationIds = new Set();
      applyConversationReadState({ workspaceId: null, conversations: [] });
      state.pendingCreatedConversationRouteWorkspaceId = null;
      state.productSettings = normalizeProductSettings({});
      state.personalizationDraft = { ...state.productSettings };
      state.appearance = normalizeAppearance({});
      state.appSettingsRevision = 0;
      parityPreferences.clearNativeAppSettingsMigrationSource();
      state.modelServiceSettings = MODEL_SERVICE_PREVIEW_SETTINGS.map(item => ({ ...item, presets: [...item.presets], nonChatCapabilities: [...item.nonChatCapabilities] }));
      state.modelProviderId = 'OPENROUTER';
      resetModelServiceDraft();
    }
    state.dialog = null;
    state.error = '';
    state.status = result.credentialCleanupPending
      ? '本机业务数据已删除；部分钥匙串凭据未能清理，请重试全部本地数据清理。'
      : result.cleanupPending
        ? '清理已提交；删除隔离区仍有待完成文件。'
        : '本机数据清理完成。';
    await refresh();
    if (fullDelete) await loadDesktopAppSettings();
    await loadDesktopPrivacyInventory();
  } catch (error) {
    if (state.dialog?.kind === 'privacy-cleanup-preview') {
      state.dialog = { ...state.dialog, submitting: false, failure: String(error) };
    }
    render();
  }
}
app.addEventListener('input', event => {
  if (event.target?.id !== 'privacy-confirmation' || state.dialog?.kind !== 'privacy-cleanup-preview') return;
  state.dialog = { ...state.dialog, confirmation: event.target.value };
  render();
  queueMicrotask(() => {
    const field = document.querySelector('#privacy-confirmation');
    field?.focus({ preventScroll: true });
    field?.setSelectionRange(field.value.length, field.value.length);
  });
}, true);
app.addEventListener('click', event => {
  const target = event.target.closest?.('[data-action]');
  const action = target?.dataset.action;
  if (!['open-privacy-cleanup', 'select-privacy-cleanup-scope', 'toggle-privacy-task', 'preview-selected-privacy-tasks', 'confirm-privacy-cleanup'].includes(action)) return;
  event.preventDefault();
  event.stopImmediatePropagation();
  if (action === 'open-privacy-cleanup') {
    state.dialog = { kind: 'privacy-cleanup-scope' };
    render();
    return;
  }
  if (action === 'select-privacy-cleanup-scope') {
    void previewPrivacyCleanup(target.dataset.scope, []);
    return;
  }
  if (action === 'toggle-privacy-task' && state.dialog?.kind === 'privacy-cleanup-preview') {
    const selected = new Set(state.dialog.selectedTaskIds || []);
    if (target.checked) selected.add(target.dataset.id); else selected.delete(target.dataset.id);
    state.dialog = { ...state.dialog, selectedTaskIds: [...selected], failure: '' };
    render();
    return;
  }
  if (action === 'preview-selected-privacy-tasks' && state.dialog?.kind === 'privacy-cleanup-preview') {
    void previewPrivacyCleanup(state.dialog.preview.scope, state.dialog.selectedTaskIds || []);
    return;
  }
  void confirmPrivacyCleanup();
}, true);
app.addEventListener('click', event => {
  const target = event.target.closest?.('[data-action]');
  const action = target?.dataset.action;
  if (!['open-archived-conversation-delete', 'open-conversation-permanent-delete', 'open-conversation-bulk-cleanup', 'confirm-conversation-permanent-delete', 'confirm-conversation-bulk-cleanup'].includes(action)) return;
  event.preventDefault();
  event.stopImmediatePropagation();
  if (action === 'open-archived-conversation-delete') {
    state.dialog = { kind: 'conversation-delete', id: target.dataset.id, revision: Number(target.dataset.revision) };
    render();
    return;
  }
  if (action === 'open-conversation-permanent-delete') {
    state.dialog = { kind: 'conversation-permanent-delete', id: target.dataset.id, revision: Number(target.dataset.revision), title: target.dataset.title || '未命名会话' };
    render();
    return;
  }
  if (action === 'open-conversation-bulk-cleanup') {
    state.dialog = { kind: 'conversation-bulk-cleanup', scope: target.dataset.scope, count: Number(target.dataset.count) };
    render();
    return;
  }
  if (action === 'confirm-conversation-permanent-delete') {
    void confirmConversationPermanentDelete(target);
    return;
  }
  void confirmConversationBulkCleanup(target.dataset.scope);
}, true);
app.addEventListener('click', event => { const target = event.target.closest?.('[data-action="confirm-import"]'); if (!target) return; event.preventDefault(); event.stopImmediatePropagation(); importPreflighted(); }, true);
app.addEventListener('click', event => { const target = event.target.closest?.('[data-action="resume-chatgpt-import"],[data-action="resume-claude-import"],[data-action="resume-nanfeng-knowledge-import"]'); if (!target) return; event.preventDefault(); event.stopImmediatePropagation(); state.dialog = null; state.status = '已保留历史导入回执；新的文件选择会直接执行，历史待处理项不会要求二次确认。'; state.error = ''; render(); }, true);
app.addEventListener('click', async event => { const target = event.target.closest?.('[data-action="retry-chatgpt-task"]'); if (!target || !state.chatgptTask) return; event.preventDefault(); event.stopImmediatePropagation(); try { await invoke('retry_chatgpt_import_task', { taskId: state.chatgptTask.id }); await commitPendingImport({ taskKey: 'chatgptTask', readCommand: 'read_chatgpt_import_task', confirmCommand: 'confirm_chatgpt_import_item', label: 'ChatGPT' }); } catch (error) { state.error = `ChatGPT 重试未完成：${String(error)}`; } render(); }, true);
async function importPreflighted() { try { state.current = await invoke('import_staged_exchange_as_new_workspace', { args: { stagingId: state.preflight.stagingId, workspaceTitle: document.querySelector('#workspace-title')?.value || '' } }); state.dialog = null; state.status = '导入完成：stable ID、revision、关系与资产 hash 已保真保存。'; await refresh(); } catch (error) { state.error = `导入失败：${String(error)}`; render(); } }
async function mutate(args, success) { if (!native || !state.current) return; try { await invoke('mutate_desktop_domain', { args: { ...args, intentId: intent('intent'), workspaceId: state.current.summary.id } }); state.status = success; state.error = ''; state.dialog = null; await refresh(); } catch (error) { state.error = `写入被拒绝：${String(error)}`; render(); } }
function saveProject() { const item = state.dialog.item; mutate({ entity: 'project', action: item ? 'update' : 'create', objectId: item?.id, expectedRevision: item?.revision, fields: { title: document.querySelector('#project-title')?.value || '', description: document.querySelector('#project-description')?.value || '' } }, item ? 'Project 已保存为新的本地 revision。' : 'Project 已创建；可撤销。'); }
function saveKnowledge() { const item = state.dialog.item; const tags = (document.querySelector('#knowledge-tags')?.value || '').split(',').map(value => value.trim()).filter(Boolean); const fields = { title: document.querySelector('#knowledge-title')?.value || '', body: document.querySelector('#knowledge-body')?.value || '', ...(item ? { tags } : { tags, scope: 'GLOBAL', projectId: null }) }; mutate({ entity: 'knowledge', action: item ? 'update' : 'create', objectId: item?.id, expectedRevision: item?.revision, fields }, item ? 'Knowledge 已保存为新的本地 revision。' : 'Knowledge 已创建；可撤销。'); }
function saveMemory() { const item = state.dialog.item; mutate({ entity: 'memory', action: item ? 'update' : 'create', objectId: item?.id, expectedRevision: item?.revision, fields: item ? { body: document.querySelector('#memory-body')?.value || '' } : { body: document.querySelector('#memory-body')?.value || '', scope: 'GLOBAL', scopeId: null } }, item ? 'Memory 已保存为新的本地 revision。' : 'Memory 已创建；可撤销。'); }
function saveRelation() { mutate({ entity: 'relation', action: 'create', fields: { fromId: document.querySelector('#relation-from')?.value || '', toId: document.querySelector('#relation-to')?.value || '', kind: document.querySelector('#relation-kind')?.value || '' } }, 'Knowledge relation 已显式建立；可撤销。'); }
async function undo(redo = false) { if (!native || !state.current) return; try { await invoke(redo ? 'redo_desktop_domain' : 'undo_desktop_domain', { args: { intentId: intent(redo ? 'redo' : 'undo'), workspaceId: state.current.summary.id } }); state.status = redo ? '已重做为新 revision。' : '已撤销为新 revision。'; state.error = ''; await refresh(); } catch (error) { state.error = `历史动作不可应用：${String(error)}`; render(); } }
async function saveMetadata() { if (!native || !state.current) return; try { const providerId = document.querySelector('#provider-id')?.value || ''; const modelId = document.querySelector('#model-id')?.value || ''; const existing = state.history.modelMetadata.find(item => item.providerId === providerId && item.modelId === modelId); await invoke('upsert_desktop_model_metadata', { args: { intentId: intent('metadata'), workspaceId: state.current.summary.id, expectedRevision: existing?.revision || 0, providerId, modelId, metadata: { capabilities: ['TEXT'], priceVersion: document.querySelector('#price-version')?.value || '', currency: document.querySelector('#price-currency')?.value || '', source: '用户手工本地配置', updatedAt: '2026-08-13T00:00:00Z', pricingStatus: 'MANUAL', safeUsage: { knownCostMicros: null } } } }); state.status = '本地 metadata 已保存；不是实时价格或真实费用。'; state.dialog = null; await refresh(); } catch (error) { state.error = `metadata 被拒绝：${String(error)}`; render(); } }
async function exportCurrent() { if (!native || !state.current) return; const selectedPath = await dialogInvoke('save', { defaultPath: `${state.current.summary.id}.nfai-exchange`, filters: [{ name: '南枫 AI 交换包', extensions: ['nfai-exchange'] }] }); if (!selectedPath) return; try { const receipt = await invoke('export_desktop_workspace_to_selected_path', { workspaceId: state.current.summary.id, selectedPath }); state.status = `导出完成并严格回读：${short(receipt.semanticHash)} · ${bytes(receipt.byteCount)}`; } catch (error) { state.error = `导出失败：${String(error)}`; } render(); }
async function exportLocalBackup() {
  if (!native || state.localBackup.working || state.localBackup.restartRequired) return;
  const selectedPath = await dialogInvoke('save', { defaultPath: 'nanfeng-ai-local-backup.nfai-backup', filters: [{ name: '南枫 AI 本机备份', extensions: ['nfai-backup'] }] });
  if (!selectedPath) return;
  state.localBackup = { ...state.localBackup, working: true, notice: '', error: '' };
  render();
  try {
    const artifact = await invoke('export_desktop_local_backup', { selectedPath });
    state.localBackup = { ...state.localBackup, working: false, notice: `备份已系统位置回读校验：${artifact.sha256.slice(0, 12)}… · Schema ${artifact.schemaVersion}`, error: '' };
  } catch (error) {
    state.localBackup = { ...state.localBackup, working: false, notice: '', error: `备份失败：${String(error)}` };
  }
  render();
}
async function importLocalBackup() {
  if (!native || state.localBackup.working || state.localBackup.restartRequired) return;
  const selectedPath = await dialogInvoke('open', { multiple: false, directory: false, filters: [{ name: '南枫 AI 本机备份', extensions: ['nfai-backup'] }] });
  if (!selectedPath) return;
  state.localBackup = { ...state.localBackup, working: true, preflight: null, replaceLocal: false, notice: '', error: '' };
  render();
  try {
    const preflight = await invoke('preflight_desktop_local_backup', { selectedPath });
    state.localBackup = { ...state.localBackup, working: false, preflight, replaceLocal: !(preflight.conflicts || []).length, notice: `预检通过：Schema ${preflight.schemaVersion}，请确认恢复方式。`, error: '' };
  } catch (error) {
    state.localBackup = { ...state.localBackup, working: false, preflight: null, notice: '', error: `预检失败：${String(error)}` };
  }
  render();
}
async function restoreLocalBackup() {
  const preflight = state.localBackup.preflight;
  if (!native || !preflight || state.localBackup.working) return;
  state.localBackup = { ...state.localBackup, working: true, notice: '', error: '' };
  render();
  try {
    const receipt = await invoke('restore_desktop_local_backup', { fingerprint: preflight.fingerprint, replaceLocal: Boolean(state.localBackup.replaceLocal) });
    state.localBackup = { ...state.localBackup, working: false, preflight: null, notice: '替换已完成。为避免旧 SQLite 与页面引用，现请手动完全重启 App；不会自动继续任务。', error: '', restartRequired: Boolean(receipt.restartRequired) };
    if (receipt.restartRequired) state.dialog = { kind: 'local-backup-restart-required' };
  } catch (error) {
    state.localBackup = { ...state.localBackup, working: false, notice: '', error: `恢复失败：${String(error)}` };
  }
  render();
}
async function cancelLocalRestore() {
  if (!native || state.localBackup.working || state.localBackup.restartRequired) return;
  state.localBackup = { ...state.localBackup, working: true, notice: '', error: '' };
  render();
  try {
    await invoke('cancel_desktop_local_restore');
    state.localBackup = { ...state.localBackup, working: false, preflight: null, replaceLocal: false, notice: '已取消；未改动本地数据。', error: '', interrupted: false };
  } catch (error) {
    state.localBackup = { ...state.localBackup, working: false, error: `取消未完成：${String(error)}` };
  }
  render();
}
async function saveLocalMessage() { const text = state.composerDraft.trim(); if (!text) { state.error = '请输入非空内容。'; render(); return; } if (!native || !state.current) { state.error = native ? '先导入一个本地工作区。' : 'Web 预览不会写入 Desktop SQLite。'; render(); return; } const conversation = resolveConversation(state.current, state.selectedConversationId); try { if (conversation) { await invoke('mutate_desktop_domain', { args: { intentId: intent('chat-message'), workspaceId: state.current.summary.id, entity: 'conversation', action: 'appendMessage', objectId: conversation.id, expectedRevision: conversation.revision, fields: { text, role: 'user' } } }); } else { const receipt = await invoke('mutate_desktop_domain', { args: { intentId: intent('chat-create'), workspaceId: state.current.summary.id, entity: 'conversation', action: 'create', fields: { title: text.replace(/\s+/g, ' ').slice(0, 36), projectId: null, firstMessage: text } } }); state.selectedConversationId = receipt.objectId; } state.composerDraft = ''; state.error = ''; state.status = '已保存为本地记录；没有调用模型。'; await refresh(); } catch (error) { state.error = `本地保存被拒绝：${String(error)}`; render(); } }
app.addEventListener('click', async event => { const target = event.target.closest('[data-action]'); const action = target?.dataset.action; if (!action) return; const data = workspace(); if (action === 'new-chat') { state.settingsConversationReturn = null; state.pane = 'chat'; state.selectedConversationId = null; state.composerDraft = ''; state.profileOpen = false; state.error = ''; state.status = '新对话将先保存到本地；联网回答需另行配置并确认。'; state.conversationFindOpen = false; state.conversationFindQuery = ''; recomputeConversationFind({ resetIndex: true }); render(); } else if (action === 'select-chat') { const settingsPage = target.closest('.android-settings-main') ? state.settingsSection : null; if (settingsPage && ['favorites', 'archived', 'recycle'].includes(settingsPage)) state.settingsConversationReturn = { page: settingsPage, mobileHome: state.settingsMobileHome, label: settingsPage === 'favorites' ? '收藏' : settingsPage === 'archived' ? '已归档' : '回收站', readOnly: ['archived', 'recycle'].includes(settingsPage) }; else state.settingsConversationReturn = null; state.pane = 'chat'; state.selectedConversationId = target.dataset.id; if (!state.chatScrollPositions.has(target.dataset.id)) state.pendingChatScrollToLatestId = target.dataset.id; state.profileOpen = false; state.error = ''; state.conversationFindOpen = false; state.conversationFindQuery = ''; recomputeConversationFind({ resetIndex: true }); if (!state.settingsConversationReturn?.readOnly) await markConversationOpened(target.dataset.id); render(); } else if (action === 'show-chat') { state.settingsConversationReturn = null; state.pane = 'chat'; state.profileOpen = false; render(); } else if (action === 'show-work') { state.settingsConversationReturn = null; state.pane = 'knowledge'; state.profileOpen = false; render(); } else if (action === 'show-connections') { state.pane = 'connections'; state.profileOpen = false; render(); } else if (action === 'toggle-profile') { state.profileOpen = !state.profileOpen; render(); } else if (action === 'save-local-message') saveLocalMessage(); else if (action === 'start-import') pickExchange(); else if (action === 'confirm-import') importPreflighted(); else if (action === 'close-dialog') { if (state.dialog?.kind === 'custom-instructions-fullscreen') { state.personalizationDraft = { ...state.dialog.previousDraft }; state.personalizationDirty = state.dialog.previousDirty; } state.dialog = null; render(); } else if (action === 'select-workspace') { state.current = await invoke('read_desktop_workspace', { workspaceId: target.dataset.id }); state.history = await invoke('read_desktop_workbench_history', { workspaceId: target.dataset.id }); state.selectedConversationId = null; reloadFavoriteConversationIds(); await loadConversationReadState({ observeSelected: false }); state.status = '已切换到独立本地工作区。'; state.error = ''; render(); } else if (action === 'show-conversation') { state.pane = 'conversation'; state.treeOpen = false; render(); } else if (action === 'show-knowledge') { state.pane = 'knowledge'; state.treeOpen = false; render(); } else if (action === 'toggle-inspector') { state.inspectorOpen = !state.inspectorOpen; render(); } else if (action === 'toggle-tree') { state.treeOpen = !state.treeOpen; render(); } else if (action === 'toggle-scale') { state.scale = state.scale === 1 ? 2 : 1; state.status = `应用缩放已切换为 ${state.scale.toFixed(1)}×。`; render(); } else if (action === 'about') { state.dialog = 'about'; render(); } else if (action === 'new-project') { state.dialog = { kind: 'project', item: null }; render(); } else if (action === 'edit-project') { state.dialog = { kind: 'project', item: data.exchange.projects.find(item => item.id === target.dataset.id) }; render(); } else if (action === 'save-project') saveProject(); else if (action === 'new-knowledge') { state.dialog = { kind: 'knowledge', item: null }; render(); } else if (action === 'edit-knowledge') { state.dialog = { kind: 'knowledge', item: data.exchange.knowledge.find(item => item.id === target.dataset.id) }; render(); } else if (action === 'save-knowledge') saveKnowledge(); else if (action === 'new-memory') { state.dialog = { kind: 'memory', item: null }; render(); } else if (action === 'edit-memory') { state.dialog = { kind: 'memory', item: data.exchange.memory.find(item => item.id === target.dataset.id) }; render(); } else if (action === 'save-memory') saveMemory(); else if (action === 'new-relation') { state.dialog = { kind: 'relation' }; render(); } else if (action === 'save-relation') saveRelation(); else if (action === 'delete-knowledge') mutate({ entity: 'knowledge', action: 'softDelete', objectId: target.dataset.id, expectedRevision: Number(target.dataset.revision), fields: {} }, 'Knowledge 已软删除；可在回收站恢复。'); else if (action === 'delete-memory') mutate({ entity: 'memory', action: 'softDelete', objectId: target.dataset.id, expectedRevision: Number(target.dataset.revision), fields: {} }, 'Memory 已软删除；可在回收站恢复。'); else if (action === 'delete-relation') mutate({ entity: 'relation', action: 'softDelete', objectId: target.dataset.id, expectedRevision: Number(target.dataset.revision), fields: {} }, 'relation 已撤销；可在回收站恢复。'); else if (action === 'restore') mutate({ entity: target.dataset.entity, action: 'restore', objectId: target.dataset.id, expectedRevision: Number(target.dataset.revision), fields: {} }, '对象已恢复为新的 revision。'); else if (action === 'undo') undo(); else if (action === 'redo') undo(true); else if (action === 'recycle') { state.dialog = 'recycle'; render(); } else if (action === 'metadata') { state.dialog = 'metadata'; render(); } else if (action === 'save-metadata') saveMetadata(); else if (action === 'start-export') exportCurrent(); });
app.addEventListener('click', event => {
  const action = event.target.closest('[data-action]')?.dataset.action;
  if (action === 'show-work') {
    state.status = '已选择 LOCAL_OFFLINE / LOCAL_ONLY：继续本地工作；没有调用模型或同步。';
    state.error = '';
    render();
  } else if (action === 'show-online-guidance') {
    state.pane = 'settings';
    state.status = 'ONLINE_PROVIDER 未启动：需要单独完成配置、模型选择、费用确认与本次外发同意；本轮没有读取 Key 或发出请求。';
    state.error = '';
    render();
  }
});
app.addEventListener('input', event => {
  if (event.target.id === 'chat-composer') {
    state.composerDraft = event.target.value;
    const send = document.querySelector('[data-action="save-local-message"]');
    const compare = document.querySelector('[data-action="open-compare-confirmation"]');
    const attachmentCount = state.temporaryConversation ? (state.temporaryConversation.draftAttachmentIds || []).length : state.composerAttachments.length;
    if (send) send.disabled = (!state.composerDraft.trim() && !attachmentCount) || !native || (!state.current && !state.temporaryConversation);
    if (compare) compare.disabled = (!state.composerDraft.trim() && !attachmentCount) || !native || !state.current || Boolean(state.temporaryConversation);
  } else if (event.target.id === 'settings-search') {
    state.settingsSearch = event.target.value;
    render();
  } else if (event.target.id === 'memory-summary-composer') {
    state.memorySummaryComposer = event.target.value;
    const submit = document.querySelector('[data-action="submit-memory-summary"]');
    if (submit) submit.disabled = !native || !state.memorySummaryComposer.trim();
  } else if (event.target.id === 'conversation-find-input') {
    state.conversationFindQuery = event.target.value;
    recomputeConversationFind({ resetIndex: true });
    render();
    focusCurrentFindMatch();
  } else if (event.target.id === 'personalization-nickname' || event.target.id === 'personalization-occupation' || event.target.id === 'personalization-instructions' || event.target.id === 'personalization-instructions-fullscreen') {
    const key = event.target.id === 'personalization-nickname' ? 'nickname' : event.target.id === 'personalization-occupation' ? 'occupation' : 'customInstructions';
    state.personalizationDraft = { ...state.personalizationDraft, [key]: event.target.value };
    state.personalizationDirty = true;
    const save = document.querySelector('[data-action="save-personalization"]');
    if (save) save.disabled = false;
    if (key === 'customInstructions') {
      const counter = event.target.closest('.android-settings-field, .custom-instructions-fullscreen')?.querySelector('small');
      if (counter) counter.textContent = `${event.target.value.length} / 6000`;
    }
  }
});

async function clearMemorySummary() {
  if (!native || !state.current) return;
  const memories = active(state.current, 'memory').filter(item => (item.status || 'ACTIVE') === 'ACTIVE');
  let removed = 0;
  try {
    for (const item of memories) {
      await invoke('mutate_desktop_domain', { args: { intentId: intent('memory-summary-delete'), workspaceId: state.current.summary.id, entity: 'memory', action: 'softDelete', objectId: item.id, expectedRevision: Number(item.revision), fields: {} } });
      removed += 1;
    }
    state.dialog = null;
    state.memorySummaryQuery = '';
    state.memorySummaryNotice = memories.length ? '记忆摘要已删除。' : '没有可删除的记忆摘要。';
    state.error = '';
  } catch (error) {
    state.dialog = null;
    state.error = `记忆摘要删除在 ${removed} / ${memories.length} 条后停止：${String(error)}`;
  }
  await refresh();
}

app.addEventListener('click', event => {
  const target = event.target.closest?.('[data-action]');
  const action = target?.dataset.action;
  if (!['submit-memory-summary', 'query-memory-summary', 'append-memory-summary', 'show-memory-summary-about', 'refresh-memory-summary', 'ask-delete-memory-summary', 'confirm-delete-memory-summary', 'ask-disable-memory-summary', 'confirm-disable-memory-summary'].includes(action)) return;
  event.preventDefault();
  event.stopImmediatePropagation();
  if (action === 'submit-memory-summary') {
    const text = state.memorySummaryComposer.trim();
    if (text) state.dialog = { kind: 'memory-summary-choice', text };
  } else if (action === 'query-memory-summary') {
    const text = state.dialog?.text || '';
    state.memorySummaryQuery = text;
    state.memorySummaryComposer = '';
    state.memorySummaryNotice = `已显示与“${text}”相关的本机记忆；没有调用模型或网络。`;
    state.dialog = null;
  } else if (action === 'append-memory-summary') {
    const text = state.dialog?.text || '';
    const memories = active(workspace(), 'memory').filter(item => (item.status || 'ACTIVE') === 'ACTIVE');
    const latest = [...memories].sort((left, right) => String(right.updatedAt || right.createdAt || '').localeCompare(String(left.updatedAt || left.createdAt || '')))[0];
    state.memorySummaryComposer = '';
    state.memorySummaryQuery = '';
    state.memorySummaryNotice = '';
    void mutate({ entity: 'memory', action: latest ? 'update' : 'create', objectId: latest?.id, expectedRevision: latest?.revision, fields: latest ? { body: `${String(latest.body || '').trim()}\n\n${text}` } : { body: text, scope: 'GLOBAL', scopeId: null } }, latest ? '已追加新的本地记忆修订。' : '记忆已在本机确认并保存。');
    return;
  } else if (action === 'show-memory-summary-about') {
    state.dialog = { kind: 'memory-summary-about' };
  } else if (action === 'refresh-memory-summary') {
    state.memorySummaryQuery = '';
    state.memorySummaryNotice = '已刷新本机记忆摘要。';
  } else if (action === 'ask-delete-memory-summary') {
    state.dialog = { kind: 'memory-summary-delete' };
  } else if (action === 'confirm-delete-memory-summary') {
    void clearMemorySummary();
    return;
  } else if (action === 'ask-disable-memory-summary') {
    state.dialog = { kind: 'memory-summary-disable' };
  } else if (action === 'confirm-disable-memory-summary') {
    writeProductSettings({ memoryEnabled: false });
    state.memorySummaryNotice = '已关闭记忆摘要生成和应用；已保存的记忆仍保留在本机。';
    state.dialog = null;
  }
  render();
}, true);
function applyTranscriptRailPosition(rail, target, { emphasize = false } = {}) {
  const currentSlot = Number(target?.dataset.railSlot);
  if (!rail || !Number.isInteger(currentSlot)) return;
  rail.querySelectorAll('button[data-rail-slot]').forEach(item => {
    const distance = Math.abs(Number(item.dataset.railSlot) - currentSlot);
    if (emphasize) item.dataset.railDistance = String(Math.min(6, distance));
    else delete item.dataset.railDistance;
    item.classList.toggle('is-active', emphasize && item === target);
    if (item === target) item.setAttribute('aria-current', 'true');
    else item.removeAttribute('aria-current');
  });
}
function clearTranscriptRailEmphasis(rail) {
  rail?.querySelectorAll('button[data-rail-slot]').forEach(item => {
    delete item.dataset.railDistance;
    item.classList.remove('is-active');
  });
}
function syncTranscriptRailToScroll(scroll) {
  const rail = scroll.parentElement?.querySelector('.chat-transcript-rail');
  const messages = [...scroll.querySelectorAll('.chat-message')];
  if (!rail || !messages.length) return;
  const viewportCenter = scroll.scrollTop + scroll.clientHeight / 2;
  const nearestIndex = messages.reduce((best, message, index) => Math.abs(message.offsetTop + message.offsetHeight / 2 - viewportCenter) < Math.abs(messages[best].offsetTop + messages[best].offsetHeight / 2 - viewportCenter) ? index : best, 0);
  const target = [...rail.querySelectorAll('button[data-index]')].reduce((best, item) => !best || Math.abs(Number(item.dataset.index) - nearestIndex) < Math.abs(Number(best.dataset.index) - nearestIndex) ? item : best, null);
  applyTranscriptRailPosition(rail, target);
}
app.addEventListener('pointerover', event => {
  const target = event.target.closest?.('[data-action="jump-transcript-position"]');
  if (target) applyTranscriptRailPosition(target.closest('.chat-transcript-rail'), target, { emphasize: true });
});
app.addEventListener('pointerout', event => {
  const rail = event.target.closest?.('.chat-transcript-rail');
  if (rail && !rail.contains(event.relatedTarget)) clearTranscriptRailEmphasis(rail);
});
app.addEventListener('focusin', event => {
  const target = event.target.closest?.('[data-action="jump-transcript-position"]');
  if (target) applyTranscriptRailPosition(target.closest('.chat-transcript-rail'), target, { emphasize: true });
});
app.addEventListener('focusout', event => {
  const rail = event.target.closest?.('.chat-transcript-rail');
  if (rail && !rail.contains(event.relatedTarget)) clearTranscriptRailEmphasis(rail);
});
app.addEventListener('scroll', event => {
  const scroll = event.target.closest?.('.chat-scroll[data-conversation-id]');
  if (!scroll) return;
  if (scroll.scrollTop > 1 || state.transcriptRailTrackingConversationId === scroll.dataset.conversationId) {
    state.transcriptRailTrackingConversationId = scroll.dataset.conversationId;
    syncTranscriptRailToScroll(scroll);
  }
  const atLatest = scroll.scrollHeight - scroll.scrollTop - scroll.clientHeight <= 24;
  if (state.scrollToLatestAnimationId === scroll.dataset.conversationId) {
    if (atLatest) {
      state.scrollToLatestAnimationId = null;
      state.chatScrollPositions.set(scroll.dataset.conversationId, scroll.scrollTop);
    }
    return;
  }
  if (atLatest !== state.chatAtLatest) {
    state.chatAtLatest = atLatest;
    render();
  }
}, true);

let sidebarDragPointerId = null;
function updateSidebarWidth(width) {
  state.sidebarWidth = clampDesktopSidebarWidth(width, window.innerWidth);
  persistSidebarWidth(state.sidebarWidth);
  app.style.setProperty('--chat-sidebar-width', `${state.sidebarWidth}px`);
  const divider = document.querySelector('.chat-sidebar-divider');
  divider?.setAttribute('aria-valuenow', String(state.sidebarWidth));
}
app.addEventListener('pointerdown', event => {
  const divider = event.target.closest?.('.chat-sidebar-divider');
  if (!divider || window.innerWidth <= 900) return;
  // WebKit does not consistently focus a tabindex separator after a pointer capture.
  // Keep pointer and keyboard adjustment on this single, visible control.
  divider.focus({ preventScroll: true });
  sidebarDragPointerId = event.pointerId;
  divider.setPointerCapture?.(event.pointerId);
  divider.classList.add('dragging');
  event.preventDefault();
});
app.addEventListener('pointermove', event => { if (sidebarDragPointerId === event.pointerId) updateSidebarWidth(event.clientX); });
app.addEventListener('pointerup', event => { if (sidebarDragPointerId !== event.pointerId) return; sidebarDragPointerId = null; document.querySelector('.chat-sidebar-divider')?.classList.remove('dragging'); });
app.addEventListener('dblclick', event => { if (!event.target.closest?.('.chat-sidebar-divider')) return; updateSidebarWidth(256); render(); });
app.addEventListener('keydown', event => {
  const divider = event.target.closest?.('.chat-sidebar-divider');
  if (!divider || window.innerWidth <= 900) return;
  const step = event.shiftKey ? 32 : 12;
  if (event.key === 'ArrowLeft') updateSidebarWidth(state.sidebarWidth - step);
  else if (event.key === 'ArrowRight') updateSidebarWidth(state.sidebarWidth + step);
  else if (event.key === 'Home') updateSidebarWidth(220);
  else if (event.key === 'End') updateSidebarWidth(440);
  else return;
  event.preventDefault();
});
app.addEventListener('click', event => {
  if (event.target.closest('[data-action]')?.dataset.action !== 'scroll-to-latest') return;
  const scroll = document.querySelector('.chat-scroll[data-conversation-id]');
  if (!scroll) return;
  state.pendingChatScrollToLatestId = scroll.dataset.conversationId;
  // The replacement scroll owner can emit an initial at-top event before
  // restoreChatScroll starts its smooth jump; reserve the owner first.
  state.scrollToLatestAnimationId = scroll.dataset.conversationId;
  state.focusComposerAfterScrollToLatest = true;
  state.chatAtLatest = true;
  scroll.scrollTo({ top: scroll.scrollHeight, behavior: 'smooth' });
  render();
});
app.addEventListener('click', event => {
  const target = event.target.closest?.('[data-action="jump-transcript-position"]');
  if (!target) return;
  const scroll = document.querySelector('.chat-scroll[data-conversation-id]');
  const index = Number(target.dataset.index);
  if (!scroll || !Number.isInteger(index)) return;
  const rail = target.closest('.chat-transcript-rail');
  state.transcriptRailTrackingConversationId = scroll.dataset.conversationId;
  applyTranscriptRailPosition(rail, target);
  const message = scroll.querySelectorAll('.chat-message')[index];
  message?.scrollIntoView({ block: 'center', behavior: 'smooth' });
  // Pointer activation must leave only the compact current-position line;
  // retain focus for keyboard activation so its preview remains discoverable.
  if (event.detail === 0) target.focus({ preventScroll: true });
  else target.blur();
});
function openConversationContextMenu(row) {
  // The render below replaces this row. Keep only its identity, then measure
  // the replacement title after the new root DOM exists.
  openTransientOverlay('context', row, { id: row.dataset.id, revision: Number(row.dataset.revision), pinned: row.dataset.pinned === 'true', favorite: row.dataset.favorite === 'true', archived: row.dataset.archived === 'true' });
}

function positionConversationContextMenu() {
  const menu = document.querySelector('.chat-context-menu');
  if (!menu || !state.contextMenu) return;
  const row = [...document.querySelectorAll('[data-conversation-row]')].find(item => item.dataset.id === state.contextMenu.id);
  if (!row) return;
  // The title, rather than date/action controls or a stale pre-render row,
  // is the only visual anchor for a conversation menu.
  const anchor = row.querySelector('.chat-history-select > span')?.getBoundingClientRect() ?? row.getBoundingClientRect();
  const sidebar = row.closest('.chat-sidebar')?.getBoundingClientRect();
  const appBounds = app.getBoundingClientRect();
  const viewportWidth = Math.max(window.innerWidth || 0, document.documentElement.clientWidth || 0, Math.ceil(appBounds.right));
  const viewportHeight = Math.max(window.innerHeight || 0, document.documentElement.clientHeight || 0, Math.ceil(appBounds.bottom));
  const menuBounds = menu.getBoundingClientRect();
  const position = resolveConversationMenuAnchor(anchor, {
    viewportWidth,
    viewportHeight,
    sidebarRect: sidebar,
    menuWidth: menuBounds.width || 192,
    menuHeight: menuBounds.height || 204,
  });
  menu.style.left = `${position.x}px`;
  menu.style.top = `${position.y}px`;
  menu.dataset.positioned = 'true';
}
function closeConversationContextMenu() {
  if (!state.contextMenu) return;
  state.contextMenu = null;
  render();
}
document.addEventListener('pointerdown', event => {
  if (event.target.closest?.('.dialog, .chat-context-menu, .chat-profile-menu, .composer-add-popover, .p6g-model-popover, [data-overlay-trigger]')) return;
  if (state.videoPreview) { void closeVideoPreview(); return; }
  if (state.audioPreview) { void closeAudioPreview(); return; }
  if (state.textPreview) { state.textPreview = null; render(); return; }
  closeTopOverlay();
}, true);
document.addEventListener('keydown', event => { if (event.key === 'Escape' && state.videoPreview) { event.preventDefault(); void closeVideoPreview(); } else if (event.key === 'Escape' && state.audioPreview) { event.preventDefault(); void closeAudioPreview(); } else if (event.key === 'Escape' && state.textPreview) { event.preventDefault(); state.textPreview = null; render(); } else if (event.key === 'Escape' && closeTopOverlay()) event.preventDefault(); }, true);
window.addEventListener('keydown', event => {
  const modifier = event.metaKey || event.ctrlKey;
  const editing = ['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement?.tagName);
  const row = event.target.closest?.('[data-conversation-row]');
  if ((event.key === 'ContextMenu' || (event.shiftKey && event.key === 'F10')) && row) { event.preventDefault(); openConversationContextMenu(row); }
  else if (event.target.id === 'chat-composer' && event.key === 'Enter' && !event.shiftKey && !event.isComposing) { event.preventDefault(); saveLocalMessage(); }
  else if (modifier && event.key.toLowerCase() === 'f' && currentConversation() && !state.temporaryConversation) { event.preventDefault(); state.conversationFindOpen = true; recomputeConversationFind(); render(); focusCurrentFindMatch(); }
  else if (modifier && event.key.toLowerCase() === 's' && state.dialog?.kind) { event.preventDefault(); if (state.dialog.kind === 'project') saveProject(); else if (state.dialog.kind === 'knowledge') saveKnowledge(); else if (state.dialog.kind === 'memory') saveMemory(); }
  else if (modifier && event.key.toLowerCase() === 'z' && !editing) { event.preventDefault(); undo(event.shiftKey); }
  else if (modifier && event.key.toLowerCase() === 'o') { event.preventDefault(); pickExchange(); }
  else if (modifier && event.key.toLowerCase() === 'e') { event.preventDefault(); exportCurrent(); }
  else if (modifier && event.key === '\\') { event.preventDefault(); state.inspectorOpen = !state.inspectorOpen; render(); }
  else if (event.key === 'Escape' && (state.contextMenu || state.dialog || state.treeOpen || state.profileOpen || state.sidebarOpen)) { state.contextMenu = null; state.dialog = null; state.treeOpen = false; state.profileOpen = false; state.sidebarOpen = false; render(); }
});
window.addEventListener('resize', () => { if (state.contextMenu) { state.contextMenu = null; render(); } });
window.addEventListener('scroll', () => { if (state.contextMenu) { state.contextMenu = null; render(); } }, true);
restoreTemporaryChat().then(refresh).catch(error => { state.error = `无法打开本地工作区：${String(error)}`; render(); });
app.addEventListener('click', event => {
  if (event.target.closest('[data-action]')?.dataset.action !== 'show-p8-inspect') return;
  state.pane = 'p8-inspect';
  state.treeOpen = false;
  state.status = '已读取本机 P8 安全账本 metadata；无 production executor。';
  render();
});

app.addEventListener('click', async event => {
  const target = event.target.closest('[data-action="select-workspace"]');
  if (!target || !native) return;
  event.stopImmediatePropagation();
  state.current = await invoke('read_desktop_workspace', { workspaceId: target.dataset.id });
  state.history = await invoke('read_desktop_workbench_history', { workspaceId: target.dataset.id });
  state.pane = 'work';
  selectWorkspaceDefaultConversation(state.current);
  state.status = '已切换到独立本地工作区，并打开该工作区最近对话。';
  state.error = '';
  render();
}, true);

function connectionCanvas() { const item = state.connection || previewConnection; return `<section class="canvas knowledge-canvas"><div class="canvas-header"><div><p class="overline">双路径 · 状态与配置边界</p><h1>连接与数据路径</h1><p>本地离线是安全底座；Provider 与加密同步必须分别配置、分别同意。</p></div></div><section class="knowledge-grid"><section><h2>本地离线</h2><p><b>LOCAL_OFFLINE / LOCAL_ONLY</b></p><p>始终可用。断网、取消或联网失败时，本地工作不丢失。</p></section><section><h2>联网模型</h2><p>配置：<b>${escape(item.providerConfiguration)}</b></p><p>Key presence：<b>${escape(item.credentialPresence)}</b>（仅存在性，不读取或显示 Key）</p><p>目录：${escape(item.catalogFreshness)} · consent：${escape(item.egressConsent)}</p><p>当前未配置或未验证，联网请求不会被标为本地成功。</p></section><section class="memory-rail"><h2>账号同步</h2><p><b>ENCRYPTED_SYNC</b> · ${escape(item.syncCapability)}</p><p>同步与 Provider 独立；不会因模型设置自动上传本地数据。</p><p>当前原因：${item.degradedReasons.map(escape).join(' · ')}</p></section></section></section>`; }

if (native) invoke('read_dual_path_status').then(value => { state.connection = value; render(); }).catch(() => {});

function workHomeCanvas(data) {
  if (!data) return `<section class="canvas empty-canvas"><p class="overline">本地工作</p><h1>先导入一个工作区</h1><p>工作模式仍在同一浅色外壳中运行；导入不会合并或覆盖既有本地数据。</p><button class="primary" data-action="start-import">选择交换包</button></section>`;
  return `<section class="canvas work-home-canvas"><div class="canvas-header"><div><p class="overline">本地工作区</p><h1>${escape(data.summary.title)}</h1><p>项目、知识、记忆和本地受控记录都在这里管理；没有 Provider、同步或隐式外发。</p></div><button class="primary" data-action="start-import">导入工作区</button></div><div class="work-summary-grid"><article><span>项目</span><strong>${data.summary.projectCount}</strong><button data-action="show-projects">管理项目</button></article><article><span>知识</span><strong>${data.summary.knowledgeCount}</strong><button data-action="show-knowledge">打开知识</button></article><article><span>记忆</span><strong>${data.summary.memoryCount}</strong><button data-action="show-memory">打开记忆</button></article></div></section>`;
}

function projectsCanvas(data) {
  const projects = data?.exchange?.projects ?? [];
  return `<section class="canvas work-projects-canvas"><div class="canvas-header"><div><p class="overline">项目</p><h1>本地项目</h1><p>项目指令、会话归属与知识范围继续由现有 Rust 本地领域链拥有。</p></div><button class="primary" data-action="new-project">新建 Project</button></div><div class="knowledge-list">${projects.map(item => `<article class="knowledge-item"><header><div><p class="overline">${item.archived ? 'ARCHIVED' : 'ACTIVE'} · r${item.revision}</p><h2>${escape(item.title)}</h2></div></header><p>${escape(item.description || '暂无说明。')}</p><div class="item-actions"><button data-action="edit-project" data-id="${escape(item.id)}">编辑</button></div></article>`).join('') || '<p class="empty-copy">暂无本地 Project。</p>'}</div></section>`;
}

function knowledgeOnlyCanvas(data) {
  const knowledge = active(data || { exchange: { knowledge: [] } }, 'knowledge');
  const relations = active(data || { exchange: { relations: [] } }, 'relations');
  const name = id => data?.exchange?.knowledge?.find(item => item.id === id)?.title || short(id);
  const candidates = state.historyKnowledge.candidates || [];
  const statusText = { RESERVED: '等待开始', RUNNING: '正在整理', PENDING_REVIEW: '待核对', NOT_ELIGIBLE: '未发现长期价值', FAILED: '整理失败', UNKNOWN: '结果未知，未自动重发', ACCEPTED: '已采纳', REJECTED: '已驳回' };
  const candidateCards = candidates.map(item => {
    const model = item.actualModelId || item.requestedModelId || '未产生模型结果';
    const usage = [item.inputTokens == null ? null : `输入 ${item.inputTokens}`, item.outputTokens == null ? null : `输出 ${item.outputTokens}`, item.chargeMicros == null ? '费用未报告' : `$${(item.chargeMicros / 1_000_000).toFixed(6)}`].filter(Boolean).join(' · ');
    const actions = item.status === 'PENDING_REVIEW' ? `<button class="primary" data-action="review-history-knowledge" data-id="${escape(item.candidateId)}">核对候选</button><button data-action="reject-history-knowledge" data-id="${escape(item.candidateId)}">驳回</button>`
      : ['FAILED', 'UNKNOWN'].includes(item.status) ? `<button class="primary" data-action="retry-history-knowledge" data-id="${escape(item.candidateId)}">显式重试</button><button data-action="delete-history-knowledge" data-id="${escape(item.candidateId)}">删除记录</button>`
        : `<button data-action="delete-history-knowledge" data-id="${escape(item.candidateId)}">删除记录</button>`;
    return `<article class="knowledge-item history-knowledge-candidate"><header><div><p class="overline">${escape(statusText[item.status] || item.status)} · 历史对话自动整理</p><h2>${escape(item.title || '没有生成可采纳候选')}</h2></div><span>${escape(item.providerId || '本机检查')} · ${escape(model)}</span></header>${item.body ? `<pre>${escape(item.body)}</pre>` : ''}<p class="security-note">来源会话 ${escape(item.conversationId)} · 消息 ${Number(item.sourceMessageIds?.length || 0)} 条 · ${escape(usage)} · checkpoint ${escape(short(item.checkpointId))}${item.safeErrorCode ? ` · 失败原因 ${escape(item.safeErrorCode)}` : ''}</p><div class="item-actions">${actions}</div></article>`;
  }).join('');
  const autoSection = `<section class="history-knowledge-review-list"><div class="section-head"><div><h2>资料候选</h2><p>只显示自动整理的审阅记录；未经采纳不会混入下方本地知识。</p></div></div>${candidateCards || '<p class="empty-copy">暂无资料候选。开启“历史资料库”后，每 12 小时最多整理一条成功完成的当前对话分支。</p>'}</section>`;
  return `<section class="canvas work-knowledge-canvas"><div class="canvas-header"><div><p class="overline">知识</p><h1>本地知识</h1><p>知识编辑、关系、撤销与软删除继续由 Rust SQLite 真值驱动；记忆在左侧“记忆”入口单独管理。</p></div><div class="canvas-actions"><button class="primary" data-action="new-knowledge">新建 Knowledge</button><button data-action="new-relation" ${knowledge.length < 2 ? 'disabled' : ''}>建立关系</button></div></div>${autoSection}<div class="knowledge-list">${knowledge.map(item => `<article class="knowledge-item"><header><div><p class="overline">${escape(item.status)} · r${item.revision} · ${escape(item.classification)}</p><h2>${escape(item.title)}</h2></div><span>${escape((item.tags || []).join(' · '))}</span></header><pre>${escape(item.body)}</pre><div class="item-actions"><button data-action="edit-knowledge" data-id="${escape(item.id)}">编辑</button><button data-action="delete-knowledge" data-id="${escape(item.id)}" data-revision="${item.revision}">软删除</button></div></article>`).join('') || '<p class="empty-copy">暂无活动 Knowledge。</p>'}</div><section class="memory-rail"><div class="section-head"><h2>Knowledge relation</h2><button data-action="new-relation" ${knowledge.length < 2 ? 'disabled' : ''}>建立</button></div><ul>${relations.map(item => `<li><span>${escape(item.kind)} · r${item.revision}</span>${escape(name(item.fromId))} → ${escape(name(item.toId))}<button data-action="delete-relation" data-id="${escape(item.id)}" data-revision="${item.revision}">撤销</button></li>`).join('') || '<li>暂无活动 relation。</li>'}</ul></section></section>`;
}

function memoryCanvas(data) {
  const memory = active(data || { exchange: { memory: [] } }, 'memory');
  return `<section class="canvas work-memory-canvas"><div class="canvas-header"><div><p class="overline">记忆</p><h1>本地 Memory</h1><p>仅用户明确创建、编辑、暂停或软删除；不会自动进入对话上下文或联网请求。</p></div><button class="primary" data-action="new-memory">新建 Memory</button></div><section class="memory-rail"><ul>${memory.map(item => `<li><span>${escape(item.scope)} · r${item.revision}</span><button class="memory-button" data-action="edit-memory" data-id="${escape(item.id)}">${escape(item.body)}</button><button data-action="delete-memory" data-id="${escape(item.id)}" data-revision="${item.revision}">软删除</button></li>`).join('') || '<li>暂无活动 Memory。</li>'}</ul></section></section>`;
}

function rememberChatScroll() {
  const scroll = document.querySelector('.chat-scroll[data-conversation-id]');
  const conversationId = scroll?.dataset.conversationId;
  if (conversationId) {
    if (state.pendingChatScrollToLatestId === conversationId || state.pendingChatSendScrollToLatestId === conversationId) return;
    state.chatScrollPositions.set(conversationId, scroll.scrollTop);
    state.chatAtLatest = scroll.scrollHeight - scroll.scrollTop - scroll.clientHeight <= 24;
  }
}

function rememberSidebarScroll() {
  const sidebarScroll = document.querySelector('.chat-sidebar-scroll');
  if (sidebarScroll) state.sidebarScrollTop = sidebarScroll.scrollTop;
}

function rememberSettingsScroll() {
  document.querySelectorAll('[data-settings-scroll-key]').forEach(element => {
    state.settingsScrollPositions.set(element.dataset.settingsScrollKey, element.scrollTop);
  });
}

function restoreSettingsScroll() {
  document.querySelectorAll('[data-settings-scroll-key]').forEach(element => {
    element.scrollTop = state.settingsScrollPositions.get(element.dataset.settingsScrollKey) || 0;
  });
}

function restoreSidebarScroll() {
  const sidebarScroll = document.querySelector('.chat-sidebar-scroll');
  if (sidebarScroll) sidebarScroll.scrollTop = state.sidebarScrollTop;
}

function restoreChatScroll() {
  const conversation = resolveConversation(workspace(), state.selectedConversationId);
  const scroll = document.querySelector('.chat-scroll[data-conversation-id]');
  if (conversation && scroll?.dataset.conversationId === conversation.id) {
    const restoreSubmittedLatest = state.pendingChatSendScrollToLatestId === conversation.id;
    const restoreLatest = state.pendingChatScrollToLatestId === conversation.id;
    if (restoreSubmittedLatest) {
      // A successful Composer send always returns the transcript directly to its
      // newest message, even when the user had been inspecting older history.
      scroll.scrollTop = scroll.scrollHeight;
      state.chatScrollPositions.set(conversation.id, scroll.scrollHeight);
      state.chatAtLatest = true;
      state.pendingChatSendScrollToLatestId = null;
    } else if (restoreLatest) {
      state.scrollToLatestAnimationId = conversation.id;
      scroll.scrollTo({ top: scroll.scrollHeight, behavior: 'smooth' });
      state.chatScrollPositions.set(conversation.id, scroll.scrollHeight);
      state.pendingChatScrollToLatestId = null;
    } else {
      scroll.scrollTop = state.chatScrollPositions.get(conversation.id) || 0;
    }
    if (state.focusComposerAfterScrollToLatest) {
      state.focusComposerAfterScrollToLatest = false;
      document.querySelector('#chat-composer')?.focus({ preventScroll: true });
    }
  }
}

function renderUnified() {
  rememberChatScroll();
  rememberSidebarScroll();
  rememberSettingsScroll();
  const data = workspace();
  globalThis.__nanfengDesktopSearchState = state;
  const visiblePane = state.p6eAcceptance.enabled ? 'settings' : state.pane;
  document.title = state.p6eAcceptance.enabled ? '南枫 AI Desktop · P6-E 验收' : '南枫 AI Desktop';
  if (state.pane === 'chat' && !state.temporaryConversation && !state.composerDraft) state.composerDraft = readChatDraft();
  if (state.pane === 'chat' && !state.composerAttachments.length) state.composerAttachments = readComposerAttachments();
  const workPanes = new Set(['work', 'projects', 'knowledge', 'memory', 'p8-inspect', 'reminders', 'transcription']);
  // Work is a scope of the selected conversation, not a dashboard.  Let the shared
  // chat shell render its transcript and Composer; only the selected mode changes.
  const workPanel = state.pane === 'projects' ? projectsCanvas(data)
    : state.pane === 'knowledge' ? knowledgeOnlyCanvas(data)
        : state.pane === 'memory' ? memoryCanvas(data)
          : state.pane === 'reminders' ? renderDesktopRemindersPage({ projection: state.reminders, notificationEnabled: Boolean(state.productSettings.monitorNotifications), notificationPermission: state.reminderNotificationPermission, native })
            : state.pane === 'transcription' ? renderDesktopTranscriptionPage({ projection: state.transcription, selectedTaskId: state.selectedTranscriptionTaskId, native, busyTaskId: state.transcriptionBusyTaskId, mode: state.transcriptionMode })
          : state.pane === 'p8-inspect' ? p8InspectCanvas({ agentRuns: state.agentRuns, native, escape, short })
            : null;
  app.className = `app-shell chat-first ${visiblePane === 'settings' ? 'settings-mode' : ''} ${workPanes.has(state.pane) ? 'work-mode' : ''} ${state.sidebarOpen ? 'compact-sidebar-open' : ''} ${state.searchPanel ? 'search-mode' : ''}`;
  app.innerHTML = renderChatFirstShell({ data, native, selectedConversationId: state.selectedConversationId, settingsConversationReturn: state.settingsConversationReturn, composerDraft: state.composerDraft, composerAttachments: state.composerAttachments, temporaryConversation: state.temporaryConversation, chatSearch: state.chatSearch, searchResults: state.searchResults, searchPanel: state.searchPanel, searchCategory: state.searchCategory, searchHistory: state.searchHistory, searchHistoryOpen: state.searchHistoryOpen, profileOpen: state.profileOpen, sidebarOpen: state.sidebarOpen, railCollapsed: state.railCollapsed, sidebarWidth: state.sidebarWidth, settingsSection: state.settingsSection, settingsMobileHome: state.settingsMobileHome, settingsSearch: state.settingsSearch, settingsPicker: state.settingsPicker, productSettings: state.productSettings, personalizationDraft: state.personalizationDraft, personalizationDirty: state.personalizationDirty, memorySummaryQuery: state.memorySummaryQuery, memorySummaryComposer: state.memorySummaryComposer, memorySummaryNotice: state.memorySummaryNotice, modelServiceSettings: state.modelServiceSettings, modelProviderId: state.modelProviderId, modelServiceDraft: state.modelServiceDraft, modelCredentialDraft: state.modelCredentialDraft, modelCredentialVisible: state.modelCredentialVisible, modelSettingsSaving: state.modelSettingsSaving, modelSettingsTesting: state.modelSettingsTesting, modelSettingsNotice: state.modelSettingsNotice, modelSettingsError: state.modelSettingsError, usageLedger: state.usageLedger, usageSection: state.usageSection, contextSelectionRecords: state.contextSelectionRecords, diagnosticRecords: state.diagnosticRecords, invocationRecords: state.invocationRecords, privacyInventory: state.privacyInventory, localBackup: state.localBackup, accountSync: state.accountSync, accountRecovery: state.accountRecovery, settingsCapabilities: state.settingsCapabilities, reminders: state.reminders, reminderNotificationPermission: state.reminderNotificationPermission, reminderNotificationBridge: state.reminderNotificationBridge, backgroundRuntime: state.backgroundRuntime, showArchived: state.showArchived, showDeleted: state.showDeleted, contextMenu: state.contextMenu, composerAddOpen: state.composerAddOpen, temporaryModelOpen: state.temporaryModelOpen, p6gModelPickerOpen: state.p6gModelPickerOpen, p6gCatalog: state.p6gCatalog, p6gGlobalDefault: state.p6gGlobalDefault, p6gSelection: state.p6gSelection, chatgptTask: state.chatgptTask, claudeTask: state.claudeTask, p6kTask: state.p6kTask, workMode: workPanes.has(visiblePane), workspaces: state.workspaces, workPanel, pane: visiblePane, status: state.status, error: state.error, connection: state.connection, p6eAcceptance: state.p6eAcceptance, imageThumbnails: state.imageThumbnails, showScrollToLatest: !state.chatAtLatest, appearance: state.appearance, favoriteConversationIds: state.favoriteConversationIds, unreadConversationIds: state.unreadConversationIds, manualUnreadAtMs: state.manualUnreadAtMs, conversationFindOpen: state.conversationFindOpen, conversationFindQuery: state.conversationFindQuery, conversationFindMatches: state.conversationFindMatches, conversationFindIndex: state.conversationFindIndex, runtimeInfo: state.runtimeInfo });
  app.style.setProperty('--chat-sidebar-width', `${state.sidebarWidth}px`);
  positionConversationContextMenu();
  const modal = dialog() || imagePreviewDialog() || pdfPreviewDialog() || videoPreviewDialog() || audioPreviewDialog() || textPreviewDialog();
  if (modal) app.insertAdjacentHTML('beforeend', modal);
  if (state.dialog?.kind === 'chatgpt-import' && !document.querySelector('[data-action="retry-chatgpt-task"]')) {
    document.querySelector('.dialog-actions')?.insertAdjacentHTML('afterbegin', '<button data-action="retry-chatgpt-task">重试解析</button>');
  }
  queueMicrotask(() => {
    restoreChatScroll();
    restoreSidebarScroll();
    restoreSettingsScroll();
    if (state.searchPanel) restoreFullSearchScroll();
    if (state.dialog) document.querySelector('.dialog input, .dialog textarea, .dialog select, .dialog button')?.focus();
    scheduleImageThumbnailReads();
  });
}

function imagePreviewDialog() {
  const preview = state.imagePreview;
  if (!preview) return '';
  const zoom = Math.max(1, Math.min(4, Number(preview.zoom) || 1));
  const content = preview.loading
    ? '<p class="image-preview-error">正在从本机私有副本读取原图；不会外发。</p>'
    : preview.error
      ? `<p class="image-preview-error">${escape(preview.error)}</p>`
      : `<div class="image-preview-tools" aria-label="图片视口控制"><button data-action="image-zoom-out" ${zoom <= 1 ? 'disabled' : ''}>缩小</button><button data-action="image-zoom-reset" ${zoom === 1 ? 'disabled' : ''}>适应</button><button data-action="image-zoom-in" ${zoom >= 4 ? 'disabled' : ''}>放大</button><small>缩放和滚动只改变当前视口 · ${zoom === 1 ? '完整适应／双击 2.5×' : `${zoom.toFixed(2)}×／拖动查看／双击恢复`}</small></div><figure><div class="image-preview-viewport ${zoom > 1 ? 'zoomed' : ''}" data-image-preview-viewport><img data-image-preview-content draggable="false" src="${escape(preview.dataUrl)}" alt="${escape(preview.displayName)} 原图预览" style="transform:translate(${Number(preview.panX) || 0}px, ${Number(preview.panY) || 0}px) scale(${zoom})"></div><figcaption>${escape(preview.displayName)} · ${preview.width} × ${preview.height} · ${bytes(preview.byteCount)} · 仅本地原图</figcaption></figure>`;
  return `<div class="scrim"><section class="dialog image-preview-dialog" role="dialog" aria-modal="true" aria-label="本地图片预览"><button class="icon-button close" data-action="close-image-preview" aria-label="关闭图片预览">${icon(icons.close, '关闭')}</button><h2>本地图片预览</h2>${content}</section></div>`;
}

function pdfPreviewDialog() {
  const preview = state.pdfPreview;
  if (!preview) return '';
  const content = preview.loading
    ? '<p class="image-preview-error">正在从本机私有副本验证 PDF；不会外发。</p>'
    : preview.error
      ? `<p class="image-preview-error">${escape(preview.error)}</p>`
      : `<div class="pdf-preview-tools" aria-label="PDF 页码控制"><button data-action="pdf-page-previous" ${preview.pageNumber <= 1 ? 'disabled' : ''}>上一页</button><small>第 ${preview.pageNumber} / ${preview.pageCount} 页</small><button data-action="pdf-page-next" ${preview.pageNumber >= preview.pageCount ? 'disabled' : ''}>下一页</button></div><iframe class="pdf-preview-frame" sandbox="" referrerpolicy="no-referrer" src="${escape(preview.dataUrl)}#page=${preview.pageNumber}" title="${escape(preview.displayName)} 本地 PDF 第 ${preview.pageNumber} 页"></iframe><p class="pdf-preview-note">仅从本机私有副本读取；脚本、表单动作、外部资源和自动链接均不执行。</p>`;
  return `<div class="scrim"><section class="dialog image-preview-dialog" role="dialog" aria-modal="true" aria-label="本地 PDF 阅读"><button class="icon-button close" data-action="close-pdf-preview" aria-label="关闭本地 PDF 阅读">${icon(icons.close, '关闭')}</button><h2>本地 PDF 阅读</h2>${content}</section></div>`;
}

function videoPreviewDialog() {
  const preview = state.videoPreview;
  if (!preview) return '';
  const content = preview.loading
    ? '<p class="image-preview-error">正在从本机私有副本验证视频；不会外发。</p>'
    : preview.error
      ? `<p class="image-preview-error">${escape(preview.error)}</p>`
      : `<div class="pdf-preview-tools" aria-label="本地视频播放控制"><button data-action="start-video-preview">开始本地播放</button><small>仅在明确点击后播放</small></div><video class="video-preview-frame" controls preload="metadata" data-video-preview src="${escape(preview.objectUrl)}" aria-label="${escape(preview.displayName)} 本地视频播放器"></video>${preview.positionMillis > 0 ? `<p class="pdf-preview-note">将从 ${formatDuration(preview.positionMillis)} 继续播放</p>` : ''}<p class="pdf-preview-note">${escape(preview.displayName)} · ${bytes(preview.byteCount)} · ${formatDuration(preview.durationMillis)}。仅从本机私有副本播放；不会自动播放、上传或外发。</p>`;
  return `<div class="scrim"><section class="dialog image-preview-dialog" role="dialog" aria-modal="true" aria-label="本地视频预览"><button class="icon-button close" data-action="close-video-preview" aria-label="关闭本地视频预览">${icon(icons.close, '关闭')}</button><h2>本地视频预览</h2>${content}</section></div>`;
}

function audioPreviewDialog() {
  const preview = state.audioPreview;
  if (!preview) return '';
  const content = preview.loading ? '<p class="image-preview-error">正在从本机私有副本验证音频；不会外发。</p>'
    : preview.error ? `<p class="image-preview-error">${escape(preview.error)}</p>`
      : `<div class="pdf-preview-tools" aria-label="本地音频播放控制"><button data-action="start-audio-preview">开始本地播放</button><small>仅在明确点击后播放</small></div><audio controls preload="metadata" data-audio-preview src="${escape(preview.dataUrl)}" aria-label="${escape(preview.displayName)} 本地音频播放器"></audio>${preview.positionMillis > 0 ? `<p class="pdf-preview-note">将从 ${formatDuration(preview.positionMillis)} 继续播放</p>` : ''}<p class="pdf-preview-note">${escape(preview.displayName)} · ${escape(preview.mimeType)} · ${bytes(preview.byteCount)} · 时长由本地播放器读取。仅从本机私有副本播放；不会自动播放、上传或外发。</p>`;
  return `<div class="scrim"><section class="dialog image-preview-dialog" role="dialog" aria-modal="true" aria-label="本地音频播放"><button class="icon-button close" data-action="close-audio-preview" aria-label="关闭本地音频播放">${icon(icons.close, '关闭')}</button><h2>本地音频播放</h2>${content}</section></div>`;
}

function textPreviewDialog() {
  const preview = state.textPreview;
  if (!preview) return '';
  const content = preview.loading ? '<p class="image-preview-error">正在从本机私有副本验证 UTF-8 文本；不会执行内容。</p>'
    : preview.error ? `<p class="image-preview-error">${escape(preview.error)}</p>`
      : `<p class="pdf-preview-note">${escape(preview.displayName)} · ${escape(preview.mimeType)} · ${bytes(preview.byteCount)}${preview.truncated ? ' · 仅显示前 128 KiB' : ''}</p><pre class="local-text-preview">${escape(preview.text)}</pre><p class="pdf-preview-note">内容按 inert 纯文本显示；不会渲染 HTML、执行链接、脚本或 Markdown 指令。</p>`;
  return `<div class="scrim"><section class="dialog image-preview-dialog" role="dialog" aria-modal="true" aria-label="本地安全文本预览"><button class="icon-button close" data-action="close-text-preview" aria-label="关闭本地安全文本预览">${icon(icons.close, '关闭')}</button><h2>本地安全文本预览</h2>${content}</section></div>`;
}

function formatDuration(milliseconds) { const seconds = Math.max(0, Math.floor((Number(milliseconds) || 0) / 1000)); return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`; }

function scheduleImageThumbnailReads() {
  if (!native || state.imagePreview) return;
  document.querySelectorAll('[data-image-thumbnail]').forEach(element => {
    const attachmentId = element.dataset.imageThumbnail;
    const workspaceId = element.dataset.thumbnailWorkspaceId || state.current?.summary?.id;
    const thumbnailKey = `${workspaceId || 'unknown'}:${attachmentId || 'unknown'}`;
    if (!workspaceId || !attachmentId || state.imageThumbnails[attachmentId] || state.imageThumbnailPending.has(thumbnailKey)) return;
    state.imageThumbnailPending.add(thumbnailKey);
    invoke('read_desktop_image_preview', { args: { workspaceId, attachmentId, fullSize: false } })
      .then(preview => { state.imageThumbnails = { ...state.imageThumbnails, [attachmentId]: preview }; })
      .catch(() => { state.imageThumbnails = { ...state.imageThumbnails, [attachmentId]: { error: '本地缩略图不可用' } }; })
      .finally(() => { state.imageThumbnailPending.delete(thumbnailKey); render(); });
  });
}

async function openImagePreview(attachmentId, workspaceId = state.current?.summary?.id) {
  if (!native || !workspaceId || !attachmentId) return;
  state.imagePreview = { loading: true }; render();
  try {
    state.imagePreview = { ...await invoke('read_desktop_image_preview', { args: { workspaceId, attachmentId, fullSize: true } }), workspaceId, zoom: 1, panX: 0, panY: 0 };
  } catch (error) {
    state.imagePreview = { error: '本地原图不可用或校验失败。' };
  }
  render();
}

let imagePanGesture = null;
function clampImagePreviewPan(viewport, zoom, panX, panY) {
  const image = viewport?.querySelector('[data-image-preview-content]');
  if (!viewport || !image || zoom <= 1) return { panX: 0, panY: 0 };
  const maxX = Math.max(0, image.clientWidth * (zoom - 1) / 2);
  const maxY = Math.max(0, image.clientHeight * (zoom - 1) / 2);
  return { panX: Math.max(-maxX, Math.min(maxX, panX)), panY: Math.max(-maxY, Math.min(maxY, panY)) };
}
function applyImagePreviewTransform(viewport) {
  const image = viewport?.querySelector('[data-image-preview-content]');
  if (!image || !state.imagePreview) return;
  image.style.transform = `translate(${Number(state.imagePreview.panX) || 0}px, ${Number(state.imagePreview.panY) || 0}px) scale(${Number(state.imagePreview.zoom) || 1})`;
}
app.addEventListener('pointerdown', event => {
  const viewport = event.target.closest?.('[data-image-preview-viewport]');
  const zoom = Number(state.imagePreview?.zoom) || 1;
  if (!viewport || zoom <= 1) return;
  event.preventDefault();
  viewport.setPointerCapture?.(event.pointerId);
  imagePanGesture = { pointerId: event.pointerId, viewport, startX: event.clientX, startY: event.clientY, panX: Number(state.imagePreview.panX) || 0, panY: Number(state.imagePreview.panY) || 0 };
  viewport.classList.add('dragging');
});
app.addEventListener('pointermove', event => {
  if (!imagePanGesture || event.pointerId !== imagePanGesture.pointerId || !state.imagePreview) return;
  const zoom = Number(state.imagePreview.zoom) || 1;
  const pan = clampImagePreviewPan(imagePanGesture.viewport, zoom, imagePanGesture.panX + event.clientX - imagePanGesture.startX, imagePanGesture.panY + event.clientY - imagePanGesture.startY);
  state.imagePreview = { ...state.imagePreview, ...pan };
  applyImagePreviewTransform(imagePanGesture.viewport);
});
const finishImagePan = event => {
  if (!imagePanGesture || event.pointerId !== imagePanGesture.pointerId) return;
  imagePanGesture.viewport.classList.remove('dragging');
  imagePanGesture = null;
};
app.addEventListener('pointerup', finishImagePan);
app.addEventListener('pointercancel', finishImagePan);
app.addEventListener('dblclick', event => {
  const viewport = event.target.closest?.('[data-image-preview-viewport]');
  if (!viewport || !state.imagePreview?.dataUrl) return;
  event.preventDefault();
  const zoom = (Number(state.imagePreview.zoom) || 1) > 1 ? 1 : 2.5;
  state.imagePreview = { ...state.imagePreview, zoom, panX: 0, panY: 0 };
  render();
});
app.addEventListener('wheel', event => {
  const viewport = event.target.closest?.('[data-image-preview-viewport]');
  if (!viewport || !state.imagePreview?.dataUrl) return;
  event.preventDefault();
  const current = Number(state.imagePreview.zoom) || 1;
  const zoom = Math.max(1, Math.min(4, current + (event.deltaY < 0 ? 0.25 : -0.25)));
  state.imagePreview = { ...state.imagePreview, zoom, panX: zoom === 1 ? 0 : state.imagePreview.panX || 0, panY: zoom === 1 ? 0 : state.imagePreview.panY || 0 };
  render();
}, { passive: false });

async function openPdfPreview(attachmentId, pageNumber = undefined, workspaceId = state.current?.summary?.id) {
  if (!native || !workspaceId || !attachmentId) return;
  state.pdfPreview = { loading: true }; render();
  try {
    state.pdfPreview = { ...await invoke('read_desktop_pdf_preview', { args: { workspaceId, attachmentId, pageNumber } }), workspaceId };
  } catch (error) {
    state.pdfPreview = { error: '本地 PDF 不可用或校验失败。' };
  }
  render();
}

async function openVideoPreview(attachmentId, positionMillis = undefined, workspaceId = state.current?.summary?.id) {
  if (!native || !workspaceId || !attachmentId) return;
  state.videoPreview = { loading: true }; render();
  try {
    const preview = await invoke('read_desktop_video_preview', { args: { workspaceId, attachmentId, positionMillis } });
    state.videoPreview = { ...preview, workspaceId, objectUrl: localVideoBlobUrl(preview.dataUrl) };
  }
  catch (error) { state.videoPreview = { error: '本地视频不可用、已损坏或校验失败。' }; }
  render();
  const restorePosition = Math.max(0, Number(state.videoPreview?.positionMillis) || 0) / 1000;
  if (restorePosition > 0) queueMicrotask(() => {
    const video = document.querySelector('[data-video-preview]');
    const seek = () => { if (video) video.currentTime = Math.min(restorePosition, Math.max(0, video.duration || restorePosition)); };
    if (video?.readyState >= 1) seek(); else video?.addEventListener('loadedmetadata', seek, { once: true });
  });
  queueMicrotask(() => {
    const video = document.querySelector('[data-video-preview]');
    video?.addEventListener('timeupdate', () => {
      const milliseconds = Math.round(video.currentTime * 1000);
      if (milliseconds > 0 && milliseconds < Math.round(video.duration * 1000)) {
        state.videoPreview = { ...state.videoPreview, lastPlaybackPositionMillis: milliseconds };
      }
    });
  });
}

function localVideoBlobUrl(dataUrl) {
  const encoded = String(dataUrl || '').split(',', 2)[1];
  if (!encoded) throw new Error('本地视频 payload 无效');
  const raw = atob(encoded);
  const bytes = Uint8Array.from(raw, value => value.charCodeAt(0));
  return URL.createObjectURL(new Blob([bytes], { type: 'video/mp4' }));
}

async function closeVideoPreview() {
  const video = document.querySelector('[data-video-preview]');
  const preview = state.videoPreview;
  if (!preview?.attachmentId || !preview.workspaceId) return;
  const currentPosition = Math.round((video?.currentTime || 0) * 1000);
  const positionMillis = preview.lastPlaybackPositionMillis || currentPosition;
  try {
    const owner = await invoke('read_desktop_video_preview', {
      args: { workspaceId: preview.workspaceId, attachmentId: preview.attachmentId, positionMillis },
    });
    if (!owner || Number(owner.positionMillis) !== positionMillis) throw new Error('本地视频位置未确认');
    state.videoPreview = null;
    if (preview.objectUrl) URL.revokeObjectURL(preview.objectUrl);
    render();
  } catch (_) {
    state.error = '本地视频位置未保存；预览保持打开，未丢弃当前进度。';
    render();
  }
}

async function openAudioPreview(attachmentId, positionMillis = undefined, workspaceId = state.current?.summary?.id) {
  if (!native || !workspaceId || !attachmentId) return;
  state.audioPreview = { loading: true }; render();
  try { state.audioPreview = { ...await invoke('read_desktop_audio_preview', { args: { workspaceId, attachmentId, positionMillis } }), workspaceId }; }
  catch (_) { state.audioPreview = { error: '本地音频不可用、已损坏或校验失败。' }; }
  render();
  queueMicrotask(() => {
    const audio = document.querySelector('[data-audio-preview]');
    const seek = () => { if (audio) audio.currentTime = Math.min(Math.max(0, Number(state.audioPreview?.positionMillis) || 0) / 1000, Math.max(0, audio.duration || 0)); };
    if (audio?.readyState >= 1) seek(); else audio?.addEventListener('loadedmetadata', seek, { once: true });
    audio?.addEventListener('timeupdate', () => { const ms = Math.round(audio.currentTime * 1000); if (ms > 0 && ms < Math.round(audio.duration * 1000)) state.audioPreview = { ...state.audioPreview, lastPlaybackPositionMillis: ms }; });
  });
}

async function closeAudioPreview() {
  const audio = document.querySelector('[data-audio-preview]'); const preview = state.audioPreview;
  if (!preview?.attachmentId || !preview.workspaceId) return;
  const positionMillis = preview.lastPlaybackPositionMillis || Math.round((audio?.currentTime || 0) * 1000);
  try { const owner = await invoke('read_desktop_audio_preview', { args: { workspaceId: preview.workspaceId, attachmentId: preview.attachmentId, positionMillis } }); if (!owner || Number(owner.positionMillis) !== positionMillis) throw new Error('本地音频位置未确认'); state.audioPreview = null; render(); }
  catch (_) { state.error = '本地音频位置未保存；预览保持打开，未丢弃当前进度。'; render(); }
}

async function openTextPreview(attachmentId, workspaceId = state.current?.summary?.id) {
  if (!native || !workspaceId || !attachmentId) return;
  state.textPreview = { loading: true }; render();
  try { state.textPreview = { ...await invoke('read_desktop_text_preview', { args: { workspaceId, attachmentId } }), workspaceId }; }
  catch (_) { state.textPreview = { error: '本地文本不可用、编码无效或校验失败。' }; }
  render();
}

function focusSearchAfterHistoryDismissal() {
  queueMicrotask(() => {
    document.querySelector('#full-search-input, #chat-search')?.focus();
    window.setTimeout(() => { state.suppressSearchHistoryFocus = false; }, 150);
  });
}

function focusSearchAfterHistoryClear() {
  state.searchHistoryOpen = true;
  state.suppressSearchHistoryFocus = false;
  render();
  window.requestAnimationFrame(() => {
    const search = document.querySelector('#full-search-input, #chat-search');
    search?.focus({ preventScroll: true });
    window.setTimeout(() => {
      if (document.activeElement !== search) search?.focus({ preventScroll: true });
    }, 0);
  });
}

let searchAttachmentLongPressTimer = null;
let searchAttachmentLongPressTriggered = null;
function cancelSearchAttachmentLongPress() {
  if (searchAttachmentLongPressTimer !== null) window.clearTimeout(searchAttachmentLongPressTimer);
  searchAttachmentLongPressTimer = null;
}
app.addEventListener('pointerdown', event => {
  const card = event.target.closest?.('.desktop-search-attachment-card');
  if (!card || event.button !== 0 || event.target.closest('.desktop-search-card-menu')) return;
  cancelSearchAttachmentLongPress();
  const entryId = card.dataset.searchEntryId;
  searchAttachmentLongPressTimer = window.setTimeout(() => {
    searchAttachmentLongPressTimer = null;
    const hit = fullSearchHit(entryId);
    if (!hit) return;
    searchAttachmentLongPressTriggered = entryId;
    rememberFullSearchScroll(entryId);
    state.dialog = { kind: 'search-attachment-actions', hit };
    render();
  }, 520);
});
app.addEventListener('pointerup', cancelSearchAttachmentLongPress);
app.addEventListener('pointercancel', cancelSearchAttachmentLongPress);

render = renderUnified;

function chatDraftKey() {
  const workspaceId = workspace()?.summary?.id;
  if (!workspaceId) return null;
  return `nanfeng-ai.desktop.chat-draft.v1:${workspaceId}:${state.selectedConversationId || 'new'}`;
}

function readChatDraft() {
  const key = chatDraftKey();
  if (!key) return '';
  try { return window.localStorage.getItem(key) || ''; } catch { return ''; }
}
function attachmentDraftKey() { const key = chatDraftKey(); return key ? `${key}:attachments` : null; }
function readComposerAttachments() { const key = attachmentDraftKey(); if (!key) return []; try { const value = JSON.parse(window.localStorage.getItem(key) || '[]'); return Array.isArray(value) ? value.filter(item => item?.id && item?.sha256 && item?.mimeType && item?.displayName) : []; } catch { return []; } }
function writeComposerAttachments() { const key = attachmentDraftKey(); if (!key) return; try { if (state.composerAttachments.length) window.localStorage.setItem(key, JSON.stringify(state.composerAttachments)); else window.localStorage.removeItem(key); } catch {} }

function writeChatDraft(value) {
  const key = chatDraftKey();
  if (!key) return;
  try {
    if (value) window.localStorage.setItem(key, value);
    else window.localStorage.removeItem(key);
  } catch {}
}

async function enterTemporaryChat() {
  if (!native) { state.error = 'Web 预览不保存临时聊天；请在 Tauri Desktop 开发包中使用。'; render(); return; }
  try {
    state.temporaryConversation = await invoke('enter_or_restore_desktop_temporary_conversation', { temporaryId: state.temporaryConversation?.temporaryId || null });
    state.composerDraft = state.temporaryConversation.draft || '';
    state.composerAttachments = [];
    state.selectedConversationId = null;
    state.pane = 'chat'; state.error = ''; state.status = '已进入临时聊天：仅本地隔离恢复，最长保留 24 小时。'; render();
  } catch (error) { state.error = `临时聊天未能打开：${String(error)}`; render(); }
}

async function restoreTemporaryChat() {
  if (!native) return;
  const recovered = await invoke('read_desktop_temporary_conversation');
  if (!recovered) return;
  // Startup only prunes and discovers recoverability. It must not switch the user out of NORMAL.
  state.status = '发现可恢复的临时聊天；点击右上角“临时聊天”可在 24 小时内继续。';
}

async function updateTemporaryDraft(value) {
  if (!state.temporaryConversation) return;
  state.composerDraft = value;
  try {
    state.temporaryConversation = await invoke('update_desktop_temporary_conversation', { args: { temporaryId: state.temporaryConversation.temporaryId, draft: value, modelOverrideId: state.temporaryConversation.modelOverrideId || null } });
  } catch (error) { state.error = `临时草稿未保存：${String(error)}`; render(); }
}

async function updateTemporaryModelOverride(value) {
  if (!state.temporaryConversation) return;
  const modelOverrideId = String(value || '').trim() || null;
  try {
    state.temporaryConversation = await invoke('update_desktop_temporary_conversation', { args: { temporaryId: state.temporaryConversation.temporaryId, draft: state.composerDraft, modelOverrideId } });
    state.temporaryModelOpen = false;
    state.error = '';
    state.status = modelOverrideId ? '已保存临时会话的本地模型选择；没有读取 Key、没有调用 Provider。' : '临时会话已切回自动；仍只保存在隔离恢复记录。';
    render();
  } catch (error) { state.error = `临时模型选择未保存：${String(error)}`; render(); }
}

async function updateP6GConversationOverride(modelId) {
  if (!native || !state.current || !state.selectedConversationId || state.temporaryConversation || !state.p6gSelection) return;
  try {
    const args = { workspaceId: state.current.summary.id, conversationId: state.selectedConversationId, expectedRevision: state.p6gSelection.conversationOverride.revision };
    if (modelId) await invoke('set_desktop_p6g_conversation_override', { args: { ...args, modelId } });
    else await invoke('clear_desktop_p6g_conversation_override', args);
    await invoke('evaluate_desktop_p6g_auto_route', { args: { workspaceId: args.workspaceId, conversationId: args.conversationId, request: { tier: 'BALANCED', requiredCapabilities: ['TEXT'], exactHistoricalCacheHit: false, localSafeRequired: false, unknownCostConfirmed: false, contextTokens: null, budgetMicros: null } } });
    state.p6gModelPickerOpen = false;
    state.status = modelId ? '已保存当前会话的本地手动模型选择；未读取 Key、未调用 Provider。' : '已切回自动；当前会话恢复本地策略。';
    state.error = '';
    await refresh();
  } catch (error) { state.error = `模型选择未保存：${String(error)}`; state.p6gModelPickerOpen = false; render(); }
}

async function saveP6GGlobalDefault() {
  if (!native) return;
  const tier = document.querySelector('#p6g-global-default')?.value || null;
  try {
    await invoke('set_desktop_p6g_global_default', { args: { expectedRevision: state.p6gGlobalDefault.revision, tier } });
    state.status = tier ? `已保存本地全局默认：${tier}。` : '已清除本地全局默认；自动策略将使用当前请求档位。';
    state.error = '';
    await refresh();
  } catch (error) { state.error = `全局默认未保存：${String(error)}`; render(); }
}

async function installP6GLocalFixture() {
  if (!native || !state.p6gCatalog) return;
  const snapshot = state.p6gCatalog.snapshot;
  try {
    await invoke('upsert_desktop_p6g_catalog_candidate', { args: {
      expectedRevision: state.p6gCatalog.revision,
      catalogVersion: 'local-fixture-catalog-v1',
      policyVersion: Number(snapshot?.policyVersion || 0) + 1,
      candidate: {
        providerFamily: 'LOCAL', providerId: 'local-fixture', modelId: 'local-p6g-fixture-balanced-v1',
        displayName: '本地确定性 fixture（仅验收）', tiers: ['BALANCED'], capabilities: ['TEXT'],
        available: true, knownCostMicros: 0, latencyRank: 0, contextWindowTokens: 32768,
      },
    } });
    state.status = '已保存本地确定性 fixture catalog；仅用于验收，不读取 Key、不配置 Provider、不会调用网络。';
    state.error = '';
    await refresh();
  } catch (error) { state.error = `本地 fixture catalog 未保存：${String(error)}`; render(); }
}

function leaveTemporaryChat() {
  if (!state.temporaryConversation) return;
  state.temporaryConversation = null;
  state.composerDraft = readChatDraft();
  state.composerAttachments = [];
  state.temporaryModelOpen = false;
  state.selectedConversationId = null;
  state.pane = 'chat';
  state.status = '已返回普通聊天；临时内容仍在隔离恢复记录中，24 小时内可继续。';
  state.error = '';
  render();
}

async function sendLocalMessage() {
  const text = state.composerDraft.trim();
  if (!text && !state.composerAttachments.length) {
    state.error = '请输入文字或保留附件后再发送。';
    render();
    return;
  }
  if (state.temporaryConversation) {
    try {
      await updateTemporaryDraft(state.composerDraft);
      state.temporaryConversation = await invoke('append_desktop_temporary_message', { args: { temporaryId: state.temporaryConversation.temporaryId } });
      state.composerDraft = ''; state.composerAttachments = []; state.error = ''; state.status = '临时消息已保存到隔离恢复记录；没有调用模型。'; render();
    } catch (error) { state.error = `临时消息未保存：${String(error)}`; render(); }
    return;
  }
  if (!native || !state.current) {
    state.error = native ? '先导入一个本地工作区。' : 'Web 预览不会写入 Desktop SQLite。';
    render();
    return;
  }
  const draftKey = chatDraftKey();
  const conversation = resolveConversation(state.current, state.selectedConversationId);
  const routeAtSubmit = state.selectedConversationId;
  const sentDraft = state.composerDraft;
  const sentAttachments = [...state.composerAttachments];
  try {
    const workspaceId = state.current.summary.id;
    if (!conversation) state.pendingCreatedConversationRouteWorkspaceId = workspaceId;
    const execution = invoke('submit_desktop_ordinary_chat', { args: {
      workspaceId,
      conversationId: conversation?.id || null,
      expectedRevision: conversation?.revision ?? null,
      text,
      attachmentIds: state.composerAttachments.map(item => item.id),
    } });
    if (draftKey) window.localStorage.removeItem(draftKey);
    state.composerAttachments = []; writeComposerAttachments();
    state.composerDraft = '';
    state.error = '';
    state.status = '消息已本地提交，正在等待模型回复。';
    render();
    let settled = false;
    const refreshLoop = refreshDesktopRuntimeWhilePending(workspaceId, conversation?.id || null, () => settled);
    let result;
    try { result = await execution; } finally { settled = true; await refreshLoop; }
    const routeStillOwned = routeAtSubmit
      ? state.selectedConversationId === routeAtSubmit
      : state.selectedConversationId === result.conversationId
        || (!state.selectedConversationId && state.pendingCreatedConversationRouteWorkspaceId === workspaceId);
    if (routeStillOwned) {
      state.selectedConversationId = result.conversationId;
      state.pendingChatSendScrollToLatestId = result.conversationId;
    }
    if (state.pendingCreatedConversationRouteWorkspaceId === workspaceId) state.pendingCreatedConversationRouteWorkspaceId = null;
    state.status = result.state === 'COMPLETED' ? '回复已完成并写入用量账本。'
      : result.state === 'COMPLETED_ACCOUNTING_PENDING' ? '回复已保存；用量账本待本地恢复。'
      : result.state === 'CANCELLED' ? '已停止生成，已产生的文本保留。'
      : result.state === 'UNKNOWN' ? '连接结果未知；未自动重发。'
      : '回复失败；可从失败卡片重试。';
    await loadDesktopContextRecords();
    await loadDesktopDiagnosticRecords();
    await refresh();
  } catch (error) {
    if (state.pendingCreatedConversationRouteWorkspaceId === state.current?.summary?.id) state.pendingCreatedConversationRouteWorkspaceId = null;
    state.composerDraft = sentDraft;
    state.composerAttachments = sentAttachments;
    writeComposerAttachments();
    state.error = `消息未提交：${String(error)}`;
    render();
  }
}

saveLocalMessage = sendLocalMessage;

async function pickComposerAttachment(kind) {
  if (!native || (!state.current && !state.temporaryConversation)) { state.error = native ? '先进入临时聊天或导入本地工作区。' : 'Web 预览不能私有复制附件。'; render(); return; }
  const currentAttachmentCount = state.temporaryConversation ? (state.temporaryConversation.draftAttachmentIds || []).length : state.composerAttachments.length;
  if (currentAttachmentCount >= 4) { state.error = '每条本地消息最多保留 4 个附件。'; render(); return; }
  const image = kind === 'image';
  // The native dialog must not silently gray out a valid WAV/M4A on a host-specific UTI map.
  // Import remains fail-closed in Rust: this picker grants no read capability beyond one explicit selection.
  const selectedPath = await dialogInvoke('open', image
    ? { multiple: false, directory: false, filters: [{ name: '图片', extensions: ['jpg', 'jpeg', 'png', 'webp'] }] }
    : { multiple: false, directory: false },
  );
  if (!selectedPath || Array.isArray(selectedPath)) return;
  try {
    if (state.temporaryConversation) {
      state.temporaryConversation = await invoke('import_desktop_temporary_attachment', { args: { temporaryId: state.temporaryConversation.temporaryId, selectedPath } });
      state.composerDraft = state.temporaryConversation.draft || state.composerDraft;
      state.status = '临时附件已私有复制并绑定 TEMPORARY_SESSION；不会进入普通会话。';
      state.composerAddOpen = false; state.error = ''; render(); return;
    }
    const attachment = await invoke('import_desktop_conversation_attachment', { args: { workspaceId: state.current.summary.id, selectedPath } });
    if (state.composerAttachments.some(item => item.sha256 === attachment.sha256)) { state.status = '相同附件已在当前草稿中；未重复添加。'; }
    else { state.composerAttachments = [...state.composerAttachments, attachment]; writeComposerAttachments(); state.status = '附件已私有复制并完成内容校验；未外发。'; }
    state.composerAddOpen = false; state.error = ''; render();
  } catch (error) { state.error = `附件未加入草稿：${String(error)}`; render(); }
}

async function mutateConversationLifecycle(target, action, fields, success) {
  if (!native || !state.current) return;
  try {
    await invoke('mutate_desktop_domain', { args: {
      intentId: intent('conversation-lifecycle'),
      workspaceId: state.current.summary.id,
      entity: 'conversation',
      action,
      objectId: target.dataset.id,
      expectedRevision: Number(target.dataset.revision),
      fields,
    } });
    if (action === 'archive') {
      removeFavoriteConversation(target.dataset.id);
      if (state.selectedConversationId === target.dataset.id) state.selectedConversationId = null;
    }
    state.error = '';
    state.status = success;
    state.dialog = null;
    await refresh();
  } catch (error) {
    state.error = `会话操作被拒绝：${String(error)}`;
    render();
  }
}

/** FB-P6-037: dialog state follows the typed receipt, never a CSS-only hide. */
async function confirmConversationDelete(target) {
  const dialog = state.dialog;
  const pending = beginConversationRecycle(dialog, target.dataset.id);
  if (!native || !state.current || !pending) return;
  state.dialog = pending;
  render();
  try {
    await invoke('mutate_desktop_domain', { args: {
      intentId: intent('conversation-lifecycle'), workspaceId: state.current.summary.id,
      entity: 'conversation', action: 'softDelete', objectId: dialog.id,
      expectedRevision: dialog.revision, fields: {},
    } });
    removeFavoriteConversation(dialog.id);
    if (state.selectedConversationId === dialog.id) state.selectedConversationId = null;
    const completed = completeConversationRecycle();
    state.dialog = completed.dialog;
    state.error = '';
    state.status = completed.status;
    await refresh();
    document.querySelector(`[data-action="${completed.focusAction}"]`)?.focus({ preventScroll: true });
  } catch (error) {
    if (state.dialog?.kind === 'conversation-delete' && state.dialog.id === dialog.id) {
      state.dialog = failConversationRecycle(dialog, error);
    }
    render();
  }
}

async function confirmConversationPermanentDelete(target) {
  const dialogState = state.dialog;
  if (!native || !state.current || dialogState?.kind !== 'conversation-permanent-delete' || dialogState.submitting) return;
  state.dialog = { ...dialogState, submitting: true, failure: '' };
  render();
  try {
    const receipt = await invoke('permanently_delete_desktop_conversation', { args: {
      workspaceId: state.current.summary.id,
      conversationId: target.dataset.id,
      expectedRevision: Number(target.dataset.revision),
    } });
    removeFavoriteConversation(target.dataset.id);
    if (state.selectedConversationId === target.dataset.id) state.selectedConversationId = null;
    state.dialog = null;
    state.error = '';
    state.status = receipt.cleanupPending ? '已永久删除会话；附件隔离清理将在下次本机维护时重试。' : '已永久删除会话。';
    await refresh();
    await loadDesktopPrivacyInventory();
    render();
  } catch (error) {
    if (state.dialog?.kind === 'conversation-permanent-delete') state.dialog = { ...dialogState, submitting: false, failure: String(error) };
    render();
  }
}

async function confirmConversationBulkCleanup(scope) {
  const dialogState = state.dialog;
  if (!native || !state.current || dialogState?.kind !== 'conversation-bulk-cleanup' || dialogState.submitting) return;
  const targets = (state.current.exchange.conversations || []).filter(item => scope === 'recycle' ? item.deleted : item.archived && !item.deleted);
  state.dialog = { ...dialogState, count: targets.length, submitting: true, failure: '' };
  render();
  let completed = 0;
  let cleanupPending = 0;
  const rejected = [];
  for (const conversation of targets) {
    try {
      if (scope === 'recycle') {
        const receipt = await invoke('permanently_delete_desktop_conversation', { args: {
          workspaceId: state.current.summary.id,
          conversationId: conversation.id,
          expectedRevision: Number(conversation.revision),
        } });
        if (receipt.cleanupPending) cleanupPending += 1;
      } else {
        await invoke('mutate_desktop_domain', { args: {
          intentId: intent('conversation-bulk-recycle'), workspaceId: state.current.summary.id,
          entity: 'conversation', action: 'softDelete', objectId: conversation.id,
          expectedRevision: Number(conversation.revision), fields: {},
        } });
      }
      completed += 1;
      removeFavoriteConversation(conversation.id);
      if (state.selectedConversationId === conversation.id) state.selectedConversationId = null;
    } catch (error) {
      rejected.push(String(error));
    }
  }
  state.dialog = null;
  if (completed > 0) {
    const base = scope === 'recycle' ? `已永久删除 ${completed} 个回收站会话。` : `已将 ${completed} 个会话移入回收站。`;
    state.status = `${base}${cleanupPending ? ` ${cleanupPending} 项附件隔离清理待本机维护重试。` : ''}${rejected.length ? ` ${rejected.length} 个未完成。` : ''}`;
    state.error = rejected.length ? rejected[0] : '';
  } else {
    state.error = `${scope === 'recycle' ? '回收站未清空' : '已归档会话未清空'}：${rejected[0] || '本机操作未完成。'}`;
  }
  await refresh();
  await loadDesktopPrivacyInventory();
  render();
}

app.addEventListener('contextmenu', event => {
  const row = event.target.closest('[data-conversation-row]');
  if (!row) return;
  event.preventDefault();
  openConversationContextMenu(row);
});

app.addEventListener('click', async event => {
  const target = event.target.closest('[data-action]');
  const action = target?.dataset.action;
  if (state.contextMenu && !target?.closest('.chat-context-menu')) closeConversationContextMenu();
  if (action === 'open-image-preview') { event.preventDefault(); await openImagePreview(target.dataset.attachmentId); return; }
  if (action === 'open-pdf-preview') { event.preventDefault(); await openPdfPreview(target.dataset.attachmentId); return; }
  if (action === 'open-video-preview') { event.preventDefault(); await openVideoPreview(target.dataset.attachmentId); return; }
  if (action === 'open-audio-preview') { event.preventDefault(); await openAudioPreview(target.dataset.attachmentId); return; }
  if (action === 'open-text-preview') { event.preventDefault(); await openTextPreview(target.dataset.attachmentId); return; }
  if (action === 'start-video-preview') {
    event.preventDefault();
    const video = document.querySelector('[data-video-preview]');
    if (!video) return;
    try { await video.play(); }
    catch (_) { state.error = '本地视频无法开始播放；私有副本仍未外发。'; render(); }
    return;
  }
  if (action === 'start-audio-preview') {
    event.preventDefault(); const audio = document.querySelector('[data-audio-preview]'); if (!audio) return;
    try { await audio.play(); } catch (_) { state.error = '本地音频无法开始播放；私有副本仍未外发。'; render(); }
    return;
  }
  if (action === 'close-image-preview') { event.preventDefault(); state.imagePreview = null; render(); return; }
  if (action === 'close-pdf-preview') { event.preventDefault(); state.pdfPreview = null; render(); return; }
  if (action === 'close-video-preview') { event.preventDefault(); await closeVideoPreview(); return; }
  if (action === 'close-audio-preview') { event.preventDefault(); await closeAudioPreview(); return; }
  if (action === 'close-text-preview') { event.preventDefault(); state.textPreview = null; render(); return; }
  if (action === 'pdf-page-previous' || action === 'pdf-page-next') { event.preventDefault(); if (!state.pdfPreview?.attachmentId) return; await openPdfPreview(state.pdfPreview.attachmentId, state.pdfPreview.pageNumber + (action === 'pdf-page-next' ? 1 : -1), state.pdfPreview.workspaceId); return; }
  if (action === 'image-zoom-in' || action === 'image-zoom-out' || action === 'image-zoom-reset') { event.preventDefault(); if (!state.imagePreview?.dataUrl) return; const current = Number(state.imagePreview.zoom) || 1; const zoom = action === 'image-zoom-in' ? Math.min(4, current + 0.25) : action === 'image-zoom-out' ? Math.max(1, current - 0.25) : 1; state.imagePreview = { ...state.imagePreview, zoom, panX: zoom === 1 ? 0 : state.imagePreview.panX || 0, panY: zoom === 1 ? 0 : state.imagePreview.panY || 0 }; render(); return; }
  if (action === 'toggle-composer-add') {
    if (state.composerAddOpen) closeTopOverlay(); else openTransientOverlay('composer-add', target);
    return;
  }
  if (action === 'stop-desktop-compare') {
    event.preventDefault();
    const executionId = target.dataset.executionId;
    if (!executionId) return;
    try {
      const accepted = await invoke('cancel_desktop_compare', { args: { executionId } });
      state.status = accepted ? '正在停止 Compare 两个分支；已生成内容会分别保留。' : '当前 Compare 没有可停止的分支。';
      state.error = '';
    } catch (error) { state.error = `Compare 停止失败：${String(error)}`; }
    render();
    return;
  }
  if (action === 'retry-desktop-compare-branch') {
    event.preventDefault();
    const attemptId = target.dataset.attemptId;
    if (!attemptId) return;
    state.status = '正在按该分支原 OpenRouter 模型与幂等键明确重试；另一分支保持不变。';
    state.error = '';
    render();
    try {
      const result = await invoke('retry_desktop_compare_branch', { args: { attemptId } });
      state.status = result.state === 'COMPLETED' ? 'Compare 两个分支均已完成。'
        : result.state === 'UNKNOWN' ? '该分支结果仍未知；未再次自动重发。'
        : 'Compare 分支重试已结束；两个结果分别保留。';
      await loadDesktopContextRecords(); await loadDesktopDiagnosticRecords(); await refresh();
    } catch (error) { state.error = `Compare 分支重试被拒绝：${String(error)}`; render(); }
    return;
  }
  if (action === 'stop-ordinary-chat') {
    event.preventDefault();
    if (!state.selectedConversationId) return;
    try {
      const accepted = await invoke('cancel_desktop_ordinary_chat', { args: { conversationId: state.selectedConversationId } });
      state.status = accepted ? '正在停止；已生成的文本会保留。' : '当前没有可停止的生成。';
      state.error = '';
    } catch (error) { state.error = `停止失败：${String(error)}`; }
    render();
    return;
  }
  if (action === 'retry-ordinary-chat') {
    event.preventDefault();
    const attemptId = target.dataset.attemptId;
    if (!attemptId) return;
    state.status = '已按原 Provider、原模型和原幂等键开始重试。';
    state.error = '';
    render();
    try {
      const result = await invoke('retry_desktop_ordinary_chat', { args: { attemptId } });
      state.status = result.state === 'COMPLETED' ? '重试已完成并写入用量账本。' : result.state === 'UNKNOWN' ? '重试结果仍未知，未再次自动重发。' : '重试未完成。';
      await loadDesktopContextRecords();
      await loadDesktopDiagnosticRecords();
      await refresh();
    } catch (error) { state.error = `重试被拒绝：${String(error)}`; render(); }
    return;
  }
  if (action === 'copy-message') {
    const message = resolveConversation(state.current, state.selectedConversationId)?.messages
      ?.find(item => item.id === target.dataset.messageId);
    const payload = messagePlainText(message);
    if (!payload) { state.error = '该消息没有可复制的安全正文。'; render(); return; }
    try {
      await navigator.clipboard.writeText(payload);
      state.status = '已复制纯文本；未包含路径、URI 或隐藏 metadata。';
      state.error = '';
    } catch (error) {
      state.error = `复制失败：${String(error)}`;
    }
    render();
    return;
  }
  if (action === 'share-message') {
    const message = resolveConversation(state.current, state.selectedConversationId)?.messages
      ?.find(item => item.id === target.dataset.messageId);
    const payload = messagePlainText(message);
    if (!payload) { state.error = '该消息没有可分享的安全正文。'; render(); return; }
    try {
      // WebKit may expose navigator.share without a macOS share owner. In a Tauri
      // bundle that can silently resolve without presenting any user choice. The
      // guaranteed local Desktop action is therefore the verified safe payload copy;
      // browser preview retains its real Web Share path.
      if (!native && typeof navigator.share === 'function') await navigator.share({ text: payload });
      else {
        await navigator.clipboard.writeText(payload);
        state.status = '已复制可分享内容；请在目标应用中通过系统粘贴发送。';
      }
      state.error = '';
    } catch (error) {
      if (error?.name !== 'AbortError') state.error = '系统分享不可用，且未复制分享内容。';
    }
    render();
    return;
  }
  if (action === 'message-provenance') {
    state.status = '该消息来源已保存在本地审计记录中；不会显示 URI、文件路径或密钥。';
    state.error = '';
    render();
    return;
  }
  if (action === 'show-answer-context') {
    const record = state.contextSelectionRecords.find(item => item.assistantMessageId === target.dataset.messageId && item.selectedSources?.length);
    if (!record) { state.error = '这条回复没有可显示的本机上下文记录。'; render(); return; }
    state.dialog = { kind: 'answer-context', record };
    state.error = '';
    render();
    return;
  }
  if (action === 'branch-from-message') {
    const conversation = resolveConversation(state.current, state.selectedConversationId);
    if (!native || !state.current || !conversation) { state.error = '当前没有可分支的本地会话。'; render(); return; }
    try {
      const receipt = await invoke('mutate_desktop_domain', { args: {
        intentId: intent('message-branch'), workspaceId: state.current.summary.id, entity: 'conversation',
        action: 'branchFromMessage', objectId: conversation.id, expectedRevision: conversation.revision,
        fields: { messageId: target.dataset.messageId },
      } });
      state.selectedConversationId = receipt.objectId;
      state.status = '已从该持久化消息创建本地分支；保留前缀与来源，不连接 Provider。';
      state.error = '';
      await refresh();
    } catch (error) { state.error = `创建分支被拒绝：${String(error)}`; render(); }
    return;
  }
  if (action === 'toggle-temporary-chat') { if (state.temporaryConversation) leaveTemporaryChat(); else await enterTemporaryChat(); return; }
  if (action === 'pick-composer-image' || action === 'pick-composer-file') { await pickComposerAttachment(action === 'pick-composer-image' ? 'image' : 'file'); return; }
  if (action === 'remove-composer-attachment') {
    if (state.temporaryConversation) {
      try { state.temporaryConversation = await invoke('remove_desktop_temporary_attachment', { args: { temporaryId: state.temporaryConversation.temporaryId, attachmentId: target.dataset.id } }); state.error = ''; render(); } catch (error) { state.error = `临时附件未移除：${String(error)}`; render(); }
    } else { state.composerAttachments = state.composerAttachments.filter(item => item.id !== target.dataset.id); writeComposerAttachments(); render(); }
    return;
  }
  if (action === 'set-appearance-mode') { updateAppearance('mode', target.dataset.value); return; }
  if (action === 'set-font-size') { updateAppearance('fontSize', target.dataset.value); return; }
  if (action === 'set-theme-color') { updateAppearance('themeColor', target.dataset.value); return; }
  if (action === 'toggle-conversation-favorite' || action === 'context-menu-favorite') { toggleFavoriteConversation(target.dataset.id); return; }
  if (action === 'toggle-conversation-find') {
    state.conversationFindOpen = !state.conversationFindOpen;
    recomputeConversationFind({ resetIndex: true });
    render();
    if (state.conversationFindOpen) focusCurrentFindMatch();
    return;
  }
  if (action === 'close-conversation-find') {
    state.conversationFindOpen = false;
    state.conversationFindQuery = '';
    recomputeConversationFind({ resetIndex: true });
    render();
    return;
  }
  if (action === 'conversation-find-previous' || action === 'conversation-find-next') {
    if (!state.conversationFindMatches.length) return;
    const delta = action === 'conversation-find-next' ? 1 : -1;
    state.conversationFindIndex = (state.conversationFindIndex + delta + state.conversationFindMatches.length) % state.conversationFindMatches.length;
    render();
    focusCurrentFindMatch();
    return;
  }
  if (action === 'export-conversation-markdown') {
    const conversation = currentConversation();
    await saveMarkdown(conversationMarkdown(conversation), safeMarkdownName(conversation?.title), '当前会话');
    return;
  }
  if (action === 'export-assistant-markdown') {
    const conversation = currentConversation();
    await saveMarkdown(assistantMessageMarkdown(conversation, target.dataset.messageId), safeMarkdownName(`${conversation?.title || '南枫 AI'} · 回答`), '当前回答');
    return;
  }
  if (action === 'context-menu-pin') {
    await mutateConversationLifecycle(target, 'setPinned', { pinned: target.dataset.pinned !== 'true' }, target.dataset.pinned === 'true' ? '会话已取消置顶。' : '会话已置顶。');
    state.contextMenu = null;
    return;
  }
  if (action === 'context-menu-unread') {
    try {
      await markConversationUnread(target.dataset.id);
      state.status = '会话已标记未读；真正打开该会话后清除。';
      state.error = '';
    } catch (error) {
      state.error = `会话未读状态未更新：${String(error)}`;
    }
    state.contextMenu = null;
    render();
    return;
  }
  if (action === 'context-menu-archive') {
    await mutateConversationLifecycle(target, target.dataset.archived === 'true' ? 'restore' : 'archive', {}, target.dataset.archived === 'true' ? '会话已恢复。' : '会话已归档。');
    state.contextMenu = null;
    return;
  }
  if (action === 'context-menu-rename') {
    const conversation = workspace()?.exchange?.conversations?.find(item => item.id === target.dataset.id);
    state.contextMenu = null;
    state.dialog = { kind: 'conversation-rename', id: target.dataset.id, revision: Number(target.dataset.revision), title: conversation?.title || '' };
    render();
    return;
  }
  if (action === 'context-menu-project') {
    const conversation = workspace()?.exchange?.conversations?.find(item => item.id === target.dataset.id);
    state.contextMenu = null;
    state.dialog = { kind: 'conversation-project', id: target.dataset.id, revision: Number(target.dataset.revision), projectId: conversation?.projectId || null };
    render();
    return;
  }
  if (action === 'context-menu-delete') {
    state.contextMenu = null;
    state.dialog = { kind: 'conversation-delete', id: target.dataset.id, revision: Number(target.dataset.revision) };
    render();
    return;
  }
  if (action === 'save-conversation-rename') {
    await mutateConversationLifecycle(target, 'update', { title: document.querySelector('#conversation-rename')?.value || '' }, '会话已重命名。');
    return;
  }
  if (action === 'save-conversation-project') {
    const projectId = document.querySelector('#conversation-project')?.value || null;
    await mutateConversationLifecycle(target, 'setProject', { projectId }, projectId ? '会话已移动到所选项目。' : '会话已移出当前项目。');
    return;
  }
  if (action === 'confirm-conversation-delete') {
    await confirmConversationDelete(target);
    return;
  }
  if (action === 'toggle-rail') {
    state.railCollapsed = !state.railCollapsed;
    render();
    return;
  }
  if (action === 'set-conversation-pinned') {
    await mutateConversationLifecycle(target, 'setPinned', { pinned: target.dataset.pinned === 'true' }, target.dataset.pinned === 'true' ? '会话已置顶。' : '会话已取消置顶。');
    return;
  }
  if (action === 'archive-conversation') {
    await mutateConversationLifecycle(target, 'archive', {}, '会话已归档；消息和本地历史仍保留。');
    return;
  }
  if (action === 'restore-conversation') {
    await mutateConversationLifecycle(target, 'restore', {}, '会话已恢复到活动列表。');
    return;
  }
  if (action === 'restore-deleted-conversation') {
    await mutateConversationLifecycle(target, 'restoreDeleted', {}, '会话已从回收站恢复到活动列表。');
    return;
  }
  if (action === 'toggle-chat-sidebar') {
    state.sidebarOpen = !state.sidebarOpen;
    state.profileOpen = false;
    render();
    return;
  }
  if (action === 'show-work') {
    state.pane = 'work';
    state.profileOpen = false;
    state.error = '';
    selectWorkspaceDefaultConversation();
  } else if (action === 'show-workspace') {
    state.pane = 'work';
    state.profileOpen = false;
    selectWorkspaceDefaultConversation();
  } else if (action === 'show-projects') {
    state.pane = 'projects';
    state.profileOpen = false;
  } else if (action === 'show-knowledge') {
    state.pane = 'knowledge';
    state.profileOpen = false;
  } else if (action === 'show-memory') {
    state.pane = 'memory';
    state.profileOpen = false;
  } else if (action === 'show-conversation') {
    state.pane = 'chat';
    state.profileOpen = false;
  }
  if (['show-work', 'show-workspace', 'show-projects', 'show-knowledge', 'show-memory', 'show-conversation'].includes(action)) {
    state.sidebarOpen = false;
    render();
  }
});

app.addEventListener('click', event => {
  const action = event.target.closest('[data-action]')?.dataset.action;
  if (action === 'new-chat' || action === 'select-chat') {
    state.composerDraft = readChatDraft();
    state.composerAttachments = readComposerAttachments();
    if (action === 'new-chat') state.status = '';
    state.sidebarOpen = false;
    render();
  }
});

app.addEventListener('click', event => {
  if (event.target.closest('[data-action]')?.dataset.action !== 'show-settings') return;
  state.sidebarOpen = false;
  render();
});

app.addEventListener('click', async event => {
  const target = event.target.closest('[data-action]');
  const action = target?.dataset.action;
  if (action === 'submit-search') { event.preventDefault(); void submitLocalSearch(); return; }
  if (action === 'set-search-category') { event.preventDefault(); state.searchCategory = target.dataset.category || 'all'; state.searchFileTypeOpen = false; state.searchScrollSnapshot = null; void runFullSearch(); return; }
  if (action === 'set-search-sort') { event.preventDefault(); const column = target.dataset.sortColumn || 'default'; state.searchSortMode = column === 'default' ? 'default' : state.searchSortMode === `${column}Descending` ? `${column}Ascending` : `${column}Descending`; state.searchScrollSnapshot = null; void runFullSearch(); return; }
  if (action === 'toggle-search-file-types') { event.preventDefault(); state.searchFileTypeOpen = !state.searchFileTypeOpen; render(); return; }
  if (action === 'set-search-file-type') { event.preventDefault(); state.searchFileType = target.dataset.fileType || 'all'; state.searchFileTypeOpen = false; state.searchScrollSnapshot = null; void runFullSearch(); return; }
  if (action === 'retry-full-search') { event.preventDefault(); void runFullSearch({ restoreScroll: true }); return; }
  if (action === 'clear-full-search') { event.preventDefault(); state.chatSearch = ''; state.searchHistoryHighlighted = null; state.searchHistoryOpen = false; state.searchScrollSnapshot = null; void runFullSearch(); return; }
  if (action === 'open-search-history') { event.preventDefault(); state.searchHistoryOpen = true; state.searchHistoryManuallyOpened = true; render(); queueMicrotask(() => document.querySelector('#full-search-input')?.focus({ preventScroll: true })); return; }
  if (action === 'fill-search-history') { event.preventDefault(); state.chatSearch = target.dataset.query || ''; state.searchHistoryOpen = false; state.searchHistoryManuallyOpened = false; state.suppressSearchHistoryFocus = true; void submitLocalSearch(); return; }
  if (action === 'close-search-history') { event.preventDefault(); state.searchHistoryOpen = false; state.suppressSearchHistoryFocus = true; render(); focusSearchAfterHistoryDismissal(); return; }
  if (action === 'clear-search-history') { event.preventDefault(); if (native && state.current) invoke('clear_desktop_local_search_history', { workspaceId: state.current.summary.id }).then(() => { state.searchHistory = []; state.searchHistoryHighlighted = null; focusSearchAfterHistoryClear(); }).catch(() => { state.searchError = '本地搜索历史清除未完成。'; render(); }); return; }
  if (action === 'close-search') { event.preventDefault(); state.searchPanel = false; state.searchReturnActive = false; state.searchLocatedArchivedConversationId = null; render(); return; }
  if (action === 'return-to-search') { event.preventDefault(); state.searchPanel = true; state.searchReturnActive = false; state.searchLocatedArchivedConversationId = null; render(); queueMicrotask(restoreFullSearchScroll); return; }
  if (action === 'open-search-result') {
    event.preventDefault();
    await locateSearchHit(fullSearchHit(target.dataset.entryId)); return;
  }
  if (action === 'open-search-attachment') { event.preventDefault(); if (searchAttachmentLongPressTriggered === target.dataset.entryId) { searchAttachmentLongPressTriggered = null; return; } await openSearchAttachment(fullSearchHit(target.dataset.entryId)); return; }
  if (action === 'open-search-attachment-menu') { event.preventDefault(); const hit = fullSearchHit(target.dataset.entryId); if (hit) { rememberFullSearchScroll(hit.entryId); state.dialog = { kind: 'search-attachment-actions', hit }; render(); } return; }
  if (action === 'locate-search-attachment') { event.preventDefault(); const hit = fullSearchHit(target.dataset.entryId) || state.dialog?.hit; state.dialog = null; await locateSearchHit(hit); return; }
  if (action === 'ask-delete-search-attachment') { event.preventDefault(); const hit = fullSearchHit(target.dataset.entryId) || state.dialog?.hit; if (hit) { state.dialog = { kind: 'search-attachment-delete', hit }; render(); } return; }
  if (action === 'confirm-delete-search-attachment') { event.preventDefault(); const hit = fullSearchHit(target.dataset.entryId) || state.dialog?.hit; if (!hit) return; state.dialog = { ...state.dialog, submitting: true, failure: '' }; render(); try { await invoke('mutate_desktop_domain', { args: { intentId: intent('search-remove-attachment'), workspaceId: hit.workspaceId, entity: 'conversation', action: 'removeAttachment', objectId: hit.conversationId, expectedRevision: Number(hit.conversationRevision), fields: { messageId: hit.messageId, attachmentId: hit.attachmentId } } }); state.dialog = null; state.status = '附件引用已删除；共享私有副本仍会保留，最后一个引用移除后进入安全清理期。'; if (state.current?.summary?.id === hit.workspaceId) state.current = await invoke('read_desktop_workspace', { workspaceId: hit.workspaceId }); await runFullSearch({ restoreScroll: true }); } catch (error) { state.dialog = { ...state.dialog, submitting: false, failure: String(error) }; render(); } return; }
  if (action === 'toggle-temporary-model') {
    event.preventDefault();
    if (!state.temporaryConversation) return;
    if (state.temporaryModelOpen) closeTopOverlay(); else openTransientOverlay('temporary-model', target);
  } else if (action === 'open-compare-confirmation') {
    event.preventDefault();
    void executeDesktopCompare();
  } else if (action === 'toggle-p6g-model-picker') {
    event.preventDefault();
    if (state.p6gModelPickerOpen) closeTopOverlay(); else openTransientOverlay('p6g-model-picker', target);
  } else if (action === 'select-p6g-auto') {
    event.preventDefault();
    void updateP6GConversationOverride(null);
  } else if (action === 'select-p6g-tier') {
    event.preventDefault();
    state.p6gSelection = { ...(state.p6gSelection || {}), pickerTier: target.dataset.tier === 'DEEP' ? 'DEEP' : 'DAILY' };
    render();
  } else if (action === 'p6g-picker-back') {
    event.preventDefault();
    state.p6gSelection = { ...(state.p6gSelection || {}), pickerTier: null };
    render();
  } else if (action === 'select-p6g-model') {
    event.preventDefault();
    void updateP6GConversationOverride(target.dataset.modelId || null);
  } else if (action === 'select-temporary-auto') {
    event.preventDefault();
    void updateTemporaryModelOverride(null);
  } else if (action === 'select-temporary-model') {
    event.preventDefault();
    void updateTemporaryModelOverride(target.dataset.modelId || null);
  } else if (action === 'save-p6g-global-default') {
    event.preventDefault();
    void saveP6GGlobalDefault();
  } else if (action === 'install-p6g-local-fixture') {
    event.preventDefault();
    void installP6GLocalFixture();
  }
});

app.addEventListener('input', event => {
  if (event.target.id === 'chat-composer') {
    if (state.temporaryConversation) updateTemporaryDraft(state.composerDraft); else writeChatDraft(state.composerDraft);
  }
  if (event.target.id === 'chat-search') {
    state.chatSearch = event.target.value;
  }
  if (event.target.id === 'full-search-input') {
    state.chatSearch = event.target.value;
    state.searchHistoryHighlighted = bestSearchHistoryMatch(state.chatSearch, state.searchHistory);
    if (!state.searchHistoryManuallyOpened) state.searchHistoryOpen = Boolean(state.searchHistoryHighlighted);
    scheduleFullSearch();
  }
  if (event.target.id === 'model-service-api-key') {
    const storedMask = '••••••••••••••••••••••••••••••••';
    const value = !state.modelCredentialEdited && !state.modelCredentialVisible && event.target.value.startsWith(storedMask)
      ? event.target.value.slice(storedMask.length)
      : event.target.value;
    state.modelCredentialDraft = value;
    state.modelCredentialEdited = true;
    state.modelSettingsNotice = '';
    state.modelSettingsError = '';
  }
});

app.addEventListener('change', event => {
  if (event.target.id === 'p6g-conversation-model') void updateP6GConversationOverride(event.target.value || null);
});

app.addEventListener('focusin', event => {
  if (event.target.id === 'chat-search') { void openFullSearch('all'); return; }
  if (event.target.id !== 'full-search-input') return;
  if (state.suppressSearchHistoryFocus) return;
  if (state.searchHistoryOpen) return;
  if (state.searchHistoryHighlighted) { state.searchHistoryOpen = true; render(); }
});

app.addEventListener('pointerdown', event => {
  if (event.target.id === 'chat-search' || event.target.id === 'full-search-input') state.suppressSearchHistoryFocus = false;
});

app.addEventListener('keydown', event => {
  if ((event.target.id === 'chat-search' || event.target.id === 'full-search-input') && event.key === 'Enter') { event.preventDefault(); void submitLocalSearch(); }
  if (event.key === 'Escape' && state.searchHistoryOpen) { state.searchHistoryOpen = false; state.suppressSearchHistoryFocus = true; render(); focusSearchAfterHistoryDismissal(); }
  else if (event.key === 'Escape' && state.searchPanel) { state.searchPanel = false; render(); }
});

app.addEventListener('click', event => {
  if (!state.searchHistoryOpen || event.target.closest('.chat-search-wrap, .desktop-search-dock, .desktop-search-history-panel')) return;
  state.searchHistoryOpen = false;
  state.suppressSearchHistoryFocus = true;
  render();
  focusSearchAfterHistoryDismissal();
});

let wasCompactChatViewport = window.matchMedia('(max-width: 900px)').matches;
window.addEventListener('resize', () => {
  const isCompactChatViewport = window.matchMedia('(max-width: 900px)').matches;
  if (wasCompactChatViewport && !isCompactChatViewport && state.sidebarOpen) {
    state.sidebarOpen = false;
    render();
  }
  wasCompactChatViewport = isCompactChatViewport;
});

if (native) {
  invoke('read_latest_chatgpt_import_task').then(task => { state.chatgptTask = task; }).catch(() => { /* Settings remains usable; errors surface on explicit resume. */ });
  invoke('read_latest_claude_import_task').then(task => { state.claudeTask = task; }).catch(() => { /* Settings remains usable; errors surface on explicit resume. */ });
  invoke('read_latest_p6k_zip_import_task').then(task => { state.p6kTask = task; }).catch(() => { /* P6-K is native-only. */ });
  invoke('read_p6h_diagnostics_status').then(enabled => { p6hDiagnosticsEnabled = Boolean(enabled); }).catch(() => {});
}

app.addEventListener('click', event => {
  const action = event.target.closest?.('[data-action]')?.dataset.action;
  if (!['export-local-backup', 'import-local-backup', 'select-local-backup-replace', 'clear-local-backup-replace', 'restore-local-backup', 'cancel-local-restore'].includes(action)) return;
  event.preventDefault();
  event.stopImmediatePropagation();
  if (action === 'export-local-backup') void exportLocalBackup();
  else if (action === 'import-local-backup') void importLocalBackup();
  else if (action === 'select-local-backup-replace') { state.localBackup = { ...state.localBackup, replaceLocal: true, error: '' }; render(); }
  else if (action === 'clear-local-backup-replace') { state.localBackup = { ...state.localBackup, replaceLocal: false }; render(); }
  else if (action === 'restore-local-backup') void restoreLocalBackup();
  else if (action === 'cancel-local-restore') void cancelLocalRestore();
}, true);

app.addEventListener('click', event => {
  const target = event.target.closest?.('[data-action]');
  const action = target?.dataset.action;
  if (!['sign-in-google-account', 'sign-out-google-account', 'create-account-recovery', 'confirm-account-recovery', 'create-account-recovery-rotation', 'confirm-account-recovery-rotation', 'retry-account-recovery-rotation', 'load-account-cloud-documents', 'restore-account-cloud-conversation', 'choose-selected-local-sync-start', 'toggle-periodic-account-sync', 'context-menu-sync', 'reconcile-account-sync'].includes(action)) return;
  event.preventDefault();
  event.stopImmediatePropagation();
  void (async () => {
    try {
      if (!native) {
        if (action === 'context-menu-sync') {
          state.contextMenu = null;
          state.pane = 'settings';
          state.settingsSection = 'account';
        }
        state.status = 'Web 预览不会打开 Google、读取凭据或连接南枫云。';
        render();
        return;
      }
      if (action === 'context-menu-sync') {
        state.contextMenu = null;
        if (state.accountSync.state !== 'READY') {
          state.pane = 'settings';
          state.settingsSection = 'account';
          state.status = '请先完成登录、恢复码确认和首次同步方向。';
          await loadDesktopAccountSync();
          render();
          return;
        }
        const workspaceId = state.current?.summary?.id;
        const conversationId = target.dataset.id;
        if (!workspaceId || !conversationId) throw new Error('当前对话不可用');
        state.status = '正在加密并核对云端版本…';
        render();
        const receipt = await invoke('sync_selected_desktop_conversation', { workspaceId, conversationId });
        if (receipt.status === 'UNKNOWN') {
          state.accountSync = { ...state.accountSync, pendingUnknown: { workspaceId, conversationId } };
          state.pane = 'settings';
          state.settingsSection = 'account';
          state.status = '提交结果未知；已停止重发，请先核对云端结果。';
        } else if (receipt.status === 'CONFLICT') {
          state.pane = 'settings';
          state.settingsSection = 'account';
          state.status = '云端版本已变化，本机未覆盖任何云端内容。';
        } else {
          state.status = receipt.status === 'UP_TO_DATE' ? '所选对话已是最新版本。' : '所选对话已加密上传并通过云端回读。';
        }
        notifyAccountSync(['SYNCED', 'UP_TO_DATE'].includes(receipt.status) ? 'success' : 'attention');
        await loadDesktopAccountSync();
      } else if (action === 'sign-in-google-account') {
        state.status = '等待系统浏览器完成 Google 授权…';
        render();
        state.accountSync = await invoke('sign_in_desktop_google_account');
        state.accountRecovery = null;
        state.status = '已建立南枫云应用账号会话；确认恢复码前不会上传。';
      } else if (action === 'sign-out-google-account') {
        state.accountSync = await invoke('sign_out_desktop_google_account');
        state.accountRecovery = null;
        state.status = '已退出账号；本机对话与工作区保留不变。';
      } else if (action === 'create-account-recovery') {
        state.accountRecovery = { ...await invoke('create_desktop_recovery_code'), rotation: false };
        state.status = '恢复码只显示这一次；安全保存后再确认。';
      } else if (action === 'confirm-account-recovery') {
        state.accountSync = await invoke('confirm_desktop_recovery_code', { confirmationHash: target.dataset.confirmationHash });
        state.accountRecovery = null;
        state.status = '恢复保护已确认；请再明确选择首次同步方向。';
      } else if (action === 'create-account-recovery-rotation') {
        state.accountRecovery = { ...await invoke('create_desktop_recovery_rotation'), rotation: true };
        state.status = '新恢复码只显示这一次；确认后才会开始重加密已选云端对话。';
      } else if (action === 'confirm-account-recovery-rotation') {
        const receipt = await invoke('confirm_desktop_recovery_rotation', { confirmationHash: target.dataset.confirmationHash });
        state.accountRecovery = null;
        state.accountSync = { ...state.accountSync, rotationPending: receipt.status !== 'ROTATED' };
        state.status = receipt.status === 'ROTATED' ? `新恢复码已激活；${receipt.completedDocuments} 个云端对话已重加密并回读。` : `恢复码轮换暂停于 ${receipt.status}；已完成 ${receipt.completedDocuments}/${receipt.totalDocuments}，未静默重发。`;
        notifyAccountSync(receipt.status === 'ROTATED' ? 'success' : 'attention');
        await loadDesktopAccountSync();
      } else if (action === 'retry-account-recovery-rotation') {
        const receipt = await invoke('retry_desktop_recovery_rotation');
        state.accountSync = { ...state.accountSync, rotationPending: receipt.status !== 'ROTATED' };
        state.status = receipt.status === 'ROTATED' ? '恢复码轮换已全部完成并回读。' : `轮换仍暂停于 ${receipt.status}；已完成 ${receipt.completedDocuments}/${receipt.totalDocuments}。`;
        notifyAccountSync(receipt.status === 'ROTATED' ? 'success' : 'attention');
        await loadDesktopAccountSync();
      } else if (action === 'load-account-cloud-documents') {
        const remoteDocuments = await invoke('list_desktop_cloud_documents');
        state.accountSync = { ...state.accountSync, remoteDocuments };
        state.status = remoteDocuments.length ? `已读取 ${remoteDocuments.length} 个可恢复的加密云端对话。` : '当前账号没有可恢复的云端对话。';
      } else if (action === 'restore-account-cloud-conversation') {
        const documentId = document.querySelector('#account-cloud-document')?.value || '';
        const input = document.querySelector('#account-cloud-recovery-code');
        const recoveryCode = input?.value || '';
        if (input) input.value = '';
        if (!recoveryCode) throw new Error('请输入恢复码');
        const receipt = await invoke('restore_desktop_cloud_conversation', { documentId, recoveryCode });
        state.status = receipt.status === 'ALREADY_RESTORED' ? '该云端对话已恢复，未重复写入。' : '云端对话已恢复为新的本机工作区，现有数据未被覆盖。';
        notifyAccountSync('success');
        await refresh();
        await loadDesktopAccountSync();
      } else if (action === 'choose-selected-local-sync-start') {
        state.accountSync = await invoke('choose_desktop_selected_sync_start');
        state.status = '已选择以本机所选对话为起点；云端非空时仍会停下并报冲突。';
      } else if (action === 'toggle-periodic-account-sync') {
        state.accountSync = await invoke('set_desktop_periodic_sync', { enabled: !state.accountSync.periodicEnabled });
        state.status = state.accountSync.periodicEnabled ? '已开启定期同步，仅处理已选对话。' : '已关闭定期同步。';
      } else if (action === 'reconcile-account-sync') {
        const receipt = await invoke('reconcile_desktop_conversation_sync', { workspaceId: target.dataset.workspaceId, conversationId: target.dataset.conversationId });
        if (receipt.status === 'SYNCED_AFTER_RECONCILE') state.accountSync = { ...state.accountSync, pendingUnknown: null };
        state.status = receipt.status === 'SYNCED_AFTER_RECONCILE' ? '已回读确认上次提交成功，未重复上传。' : receipt.status === 'SAFE_TO_RETRY_EXPLICITLY' ? '云端未改变；如需重试，请在会话菜单再次明确发起。' : '云端版本冲突，已停止。';
        notifyAccountSync(receipt.status === 'SYNCED_AFTER_RECONCILE' ? 'success' : 'attention');
        await loadDesktopAccountSync();
      }
      state.error = '';
    } catch (error) {
      state.error = `账号与同步操作未完成：${String(error)}`;
      if (['context-menu-sync', 'confirm-account-recovery-rotation', 'retry-account-recovery-rotation', 'restore-account-cloud-conversation', 'reconcile-account-sync'].includes(action)) notifyAccountSync('attention');
    }
    render();
  })();
}, true);

async function bootDesktopShell() {
  let startupStage = 'WORKSPACE_READBACK';
  try {
    await refresh();
    if (native) {
      startupStage = 'LOCAL_BACKUP_READBACK';
      const backupStatus = await invoke('read_desktop_local_backup_status');
      state.localBackup = {
        ...state.localBackup,
        preflight: backupStatus.preflight || null,
        replaceLocal: Boolean(backupStatus.preflight && !(backupStatus.preflight.conflicts || []).length),
        notice: backupStatus.notice || '',
        restartRequired: Boolean(backupStatus.restartRequired),
        interrupted: Boolean(backupStatus.interrupted),
      };
      if (backupStatus.restartRequired) state.dialog = { kind: 'local-backup-restart-required' };
      startupStage = 'APP_SETTINGS_READBACK';
      await loadDesktopAppSettings();
      startupStage = 'REMINDER_READBACK';
      await loadDesktopReminders();
      startupStage = 'BACKGROUND_RUNTIME_READBACK';
      await loadDesktopBackgroundRuntime();
      startupStage = 'REMINDER_NOTIFICATION_PERMISSION';
      await readReminderNotificationPermission();
      startupStage = 'REMINDER_NOTIFICATION_LISTENER';
      await installReminderNotificationActionListener();
      startupStage = 'REMINDER_NOTIFICATION_FLUSH';
      await flushReminderNotifications();
      startupStage = 'MODEL_SETTINGS_READBACK';
      await loadModelServiceSettings();
      startupStage = 'HISTORY_KNOWLEDGE_READBACK';
      await loadDesktopHistoryKnowledge();
      if (state.productSettings.historyLibraryEnabled) {
        // The SQLite owner enforces the global 12-hour window and eligibility gate. Startup
        // merely wakes it; a missing Provider is reported without changing the saved switch.
        try { await loadDesktopHistoryKnowledge({ runDue: true }); } catch { /* visible on the knowledge page after the next explicit action */ }
      }
      startupStage = 'USAGE_READBACK';
      await loadDesktopUsageLedger();
      startupStage = 'CONTEXT_READBACK';
      await loadDesktopContextRecords();
      startupStage = 'DIAGNOSTIC_READBACK';
      await loadDesktopDiagnosticRecords();
      startupStage = 'PRIVACY_READBACK';
      await loadDesktopPrivacyInventory();
      startupStage = 'ACCOUNT_SYNC_READBACK';
      await loadDesktopAccountSync();
      startupStage = 'V2_EXCHANGE_READBACK';
      state.v2CommittedExchanges = await invoke('list_desktop_workspace_exchange_v2_committed');
      globalThis.__nanfengV2CommittedExchanges = state.v2CommittedExchanges;
      render();
    }
  } catch (error) {
    console.error('Desktop startup readback failed', startupStage, error);
    state.error = `本地工作区启动读取未完成（${startupStage}）；请重新打开应用后再试。`;
    state.status = `启动读取失败（${startupStage}）；未修改本地数据。`;
    render();
  }
}

void bootDesktopShell();
