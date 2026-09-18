import { renderAnswerInformation } from './answer-information.mjs';
import { cloudReadFeedback } from './cloud-read-feedback.mjs';
import { installModalLayerOwner, installActionMenuLayerOwner } from './modal-layer-owner.mjs';
import { resizeComposer } from './composer-size.mjs';
import { renderSafeMarkdown } from './safe-markdown.mjs';
import { sourceDisplayTitle } from './source-link-presentation.mjs';
import { createSearchResultCache } from './search-result-cache.mjs';
import { mergeCloudConversationList } from './cloud-conversation-list-merge.mjs';
import { createCloudConversationOpenRoute } from './cloud-conversation-open-route.mjs';
import { syncSelectedContinuation } from './selected-conversation-continuation.mjs';
import { renderSearchAttachmentMenu, renderChatAttachmentMenu, attachmentMenuAnchor } from './search-attachment-menu.mjs';
import { icon as sharedIcon, icons as sharedIcons } from './icon-source.mjs';
import { p8InspectCanvas } from './p8-inspect.mjs';
import { activeConversations, clampDesktopSidebarWidth, messagePlainText, millisecondsUntilDeepSeekPricingTransition, orderedConversationMessages, renderChatFirstShell, resolveAssistantMessageMenuAnchor, resolveConversation, resolveConversationMenuAnchor } from './chat-shell.mjs';
import { beginConversationRecycle, completeConversationRecycle, failConversationRecycle } from './recycle-confirmation.mjs';
import { CUSTOM_INSTRUCTIONS_MAX_LENGTH, DESKTOP_SETTINGS_CAPABILITIES, DesktopParityPreferences, assistantMessageMarkdown, conversationFindMatches as findConversationMatches, conversationMarkdown, normalizeAppearance, normalizeProductSettings } from './desktop-parity-preferences.mjs';
import { applyDesktopThemeToRoot } from './desktop-theme-owner.mjs';
import { parseC16Preview } from './c16-theme-matrix-fixture.mjs';
import { MODEL_SERVICE_PREVIEW_SETTINGS } from './android-settings-shell.mjs';
import { bestSearchHistoryMatch } from './desktop-search-page.mjs';
import { attachmentPreviewCapability } from './desktop-attachment-preview-owner.mjs';
import { imagePreviewNavigation, imagePreviewTargetId, relatedImageIds } from './image-preview-navigation.mjs';
import { resolveSearchResultAnchor } from './search-result-anchor.mjs';
import { createC08BrowserSearchPage } from './c08-search-preview-fixture.mjs';
import { createC09ReminderPreviewDraft, createC09ReminderPreviewProjection } from './c09-reminder-preview-fixture.mjs';
import { createC10ImportedPreviewTask, createC10TranscriptionPreviewProjection } from './c10-transcription-preview-fixture.mjs';
import { createC12ModelNetworkPreview } from './c12-model-network-preview-fixture.mjs';
import { C13_FAVORITE_CONVERSATION_ID, createC13ConversationLifecyclePreview } from './c13-conversation-lifecycle-preview-fixture.mjs';
import { createC14LocalDataPreview } from './c14-local-data-preview-fixture.mjs';
import { C15_PROJECT_ID, createC15WorkspacePreview } from './c15-workspace-preview-fixture.mjs';
import { renderLocalDataCleanupPreviewDialog, renderLocalDataCleanupScopeDialog } from './local-data-view.mjs';
import { renderDesktopTranscriptionPage, TRANSCRIPTION_PREVIEW_STATE } from './desktop-transcription-page.mjs';
import { renderDesktopRemindersPage } from './desktop-reminders-page.mjs';
import { reminderNotificationAction, reminderNotificationExtra } from './reminder-notification-routing.mjs';
import { createConversationLongPressController } from './conversation-long-press.mjs';
import { conversationFindScrollTop, createConversationFindInputController } from './conversation-find-interaction.mjs';
import { isWorkspaceRoot, resolveWorkConversationState } from './workspace-view.mjs';

const app = document.querySelector('#app');
installModalLayerOwner(app);
installActionMenuLayerOwner(app);
window.addEventListener('error', event => { console.error('Desktop runtime error', event.error || event.message); });
window.addEventListener('unhandledrejection', event => { console.error('Desktop async runtime error', event.reason); });
const tauriBridge = window.__TAURI__?.core ?? window.__TAURI_INTERNALS__;
const native = Boolean(tauriBridge?.invoke);
const c09ReminderPreview = native ? '' : new URLSearchParams(window.location.search).get('c09ReminderPreview') || '';
const c10TranscriptionPreview = native ? '' : new URLSearchParams(window.location.search).get('c10TranscriptionPreview') || '';
const c12ModelNetworkPreview = native ? '' : new URLSearchParams(window.location.search).get('c12ModelNetworkPreview') || '';
const c13ConversationLifecyclePreview = native ? '' : new URLSearchParams(window.location.search).get('c13ConversationLifecyclePreview') || '';
const c14LocalDataPreview = native ? '' : new URLSearchParams(window.location.search).get('c14LocalDataPreview') || '';
const c15WorkspacePreview = native ? '' : new URLSearchParams(window.location.search).get('c15WorkspacePreview') || '';
let c16ThemePreview = native ? '' : new URLSearchParams(window.location.search).get('c16ThemePreview') || '';
let c16PreviewState = parseC16Preview(c16ThemePreview);
const fixture = {
  summary: { id: 'workspace-preview-01', title: 'P6-D 本地预览', semanticHash: 'local-preview…', packageHash: 'read-only…', projectCount: 1, conversationCount: 1, knowledgeCount: 1, memoryCount: 1, relationCount: 0, assetCount: 0, assetByteCount: 0, highSensitive: false },
  exchange: {
    projects: [{ id: 'project-preview-01', title: '本地项目', description: '浏览器预览不会写入 Desktop SQLite。', pinned: true, archived: false, revision: 1 }],
    conversations: [{
      id: 'conversation-preview-01',
      title: '新对话',
      createdAt: '2026-09-03T00:42:00Z',
      updatedAt: '2026-09-03T00:42:00Z',
      revision: 1,
      messages: [
        { id: 'message-preview-01', role: 'user', createdAt: '2026-09-03T00:42:00Z', blocks: [{ kind: 'TEXT', text: 'C02-local-visual-fixture' }] },
        { id: 'message-preview-02', role: 'assistant', delivery: 'FAILED', attemptId: 'attempt-preview-02', source: 'PROVIDER', safeErrorCode: 'PROVIDER_NOT_ENABLED', createdAt: '2026-09-03T00:42:00Z', blocks: [{ kind: 'TEXT', text: '' }] },
      ],
    }],
    knowledge: [{ id: 'knowledge-preview-01', title: '安全知识', body: '编辑、撤销与软删除只在 Tauri Desktop 的 Rust SQLite 中执行。', tags: ['local'], status: 'ACTIVE', scope: 'GLOBAL', revision: 1, classification: 'NORMAL' }],
    memory: [{ id: 'memory-preview-01', body: '本地 Memory 仅作文本 IR。', scope: 'GLOBAL', status: 'ACTIVE', revision: 1 }],
    relations: [],
  },
};
const previewConnection = { connection: 'ONLINE_CONFIGURATION_REQUIRED', providerConfiguration: 'NOT_CONFIGURED', credentialPresence: 'MISSING', catalogFreshness: 'NOT_AVAILABLE', egressConsent: 'REQUIRED_PER_INTENT', syncCapability: 'ENCRYPTED_SYNC_NOT_CONFIGURED', degradedReasons: ['NO_CREDENTIAL', 'CATALOG_UNAVAILABLE', 'NETWORK_UNVERIFIED', 'EGRESS_CONSENT_REQUIRED', 'SYNC_NOT_CONFIGURED'] };
const settingsLocalKey = 'nanfeng-ai.desktop.settings.sidebar-width.v1';
// This stores only local workspace/conversation IDs. Row content remains in
// Rust-owned SQLite, so the list can return after restart without a network read.
const cloudConversationCacheKey = 'nanfeng-ai.desktop.sync-cloud-list.v1';
const parityPreferences = new DesktopParityPreferences(window.localStorage);
const systemDarkQuery = window.matchMedia?.('(prefers-color-scheme: dark)');
function readSidebarWidth() { try { return clampDesktopSidebarWidth(Number(localStorage.getItem(settingsLocalKey)), window.innerWidth); } catch { return 256; } }
function persistSidebarWidth(width) { try { localStorage.setItem(settingsLocalKey, String(width)); } catch { /* browser preview may deny local storage */ } }
function dedupeCloudConversationEntries(entries) {
  const seen = new Set();
  return (Array.isArray(entries) ? entries : []).filter(item => {
    const workspaceId = item?.workspaceId;
    const conversationId = item?.conversationId ?? item?.id;
    if (typeof workspaceId !== 'string' || typeof conversationId !== 'string') return false;
    const key = `${workspaceId}:${conversationId}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}
function readCloudConversationCache() {
  try {
    const cached = JSON.parse(window.localStorage.getItem(cloudConversationCacheKey) || 'null');
    if (![1, 2].includes(cached?.version) || typeof cached.email !== 'string' || !Array.isArray(cached.entries)) return null;
    return {
      email: cached.email,
      entries: dedupeCloudConversationEntries(cached.entries).slice(0, 200).map(item => ({
        workspaceId: item.workspaceId,
        conversationId: item.conversationId ?? item.id,
        // This is presentation state for the cloud list only. It must never
        // borrow the restored local conversation's pinned flag.
        cloudPinned: Boolean(item.cloudPinned),
      })),
    };
  } catch { return null; }
}
function persistCloudConversationCache(rows = state.cloudConversations) {
  const email = state.accountSync?.email;
  if (!email) return;
  const entries = dedupeCloudConversationEntries(rows).map(item => ({ workspaceId: item.workspaceId, conversationId: item.id, cloudPinned: Boolean(item.cloudPinned) }));
  try { window.localStorage.setItem(cloudConversationCacheKey, JSON.stringify({ version: 2, email, entries })); } catch { /* cache is optional */ }
}
function clearCloudConversationCache() {
  try { window.localStorage.removeItem(cloudConversationCacheKey); } catch { /* cache is optional */ }
}
const initialProductSettings = parityPreferences.readProductSettings();
const state = { workspaces: [], current: null, pane: 'chat', selectedConversationId: null, sidebarConversationList: 'recent', cloudConversations: [], batchEditing: false, batchSelectedConversationKeys: new Set(), chatgptTask: null, claudeTask: null, p6kTask: null, composerDraft: '', composerAttachments: [], temporaryConversation: null, profileOpen: false, sidebarOpen: false, railCollapsed: false, sidebarWidth: readSidebarWidth(), settingsSection: 'personalization', settingsSearch: '', settingsPicker: null, productSettings: initialProductSettings, personalizationDraft: { ...initialProductSettings }, personalizationDirty: false, modelServiceSettings: MODEL_SERVICE_PREVIEW_SETTINGS.map(item => ({ ...item, presets: [...item.presets], nonChatCapabilities: [...item.nonChatCapabilities] })), modelProviderId: 'OPENROUTER', modelServiceDraft: null, modelCredentialDraft: null, modelCredentialEdited: false, modelCredentialVisible: false, modelSettingsSaving: false, modelSettingsTesting: false, modelSettingsNotice: '', modelSettingsError: '', usageLedger: { records: [], inputTokens: 0, outputTokens: 0, cachedInputTokens: 0 }, usageSection: 'conversation', contextSelectionRecords: [], diagnosticRecords: [], invocationRecords: [], privacyInventory: { totalBytes: 0, aggregates: [] }, localBackup: { working: false, operation: null, preflight: null, replaceLocal: false, notice: '', error: '', restartRequired: false, interrupted: false }, showArchived: false, showDeleted: false, contextMenu: null, composerAddOpen: false, composerAddPage: 'root', cameraCaptureOpen: false, cameraCaptureReady: false, cameraCaptureBusy: false, cameraCaptureError: '', conversationPreferences: { revision: 0, toneOverride: null, webSearchOverride: null }, temporaryModelOpen: false, p6gModelPickerOpen: false, p6gCatalog: null, p6gGlobalDefault: { revision: 0, tier: null }, p6gSelection: null, chatScrollPositions: new Map(), chatAtLatest: true, transcriptRailTrackingConversationId: null, pendingChatScrollToLatestId: null, pendingChatSendScrollToLatestId: null, scrollToLatestAnimationId: null, focusComposerAfterScrollToLatest: false, inspectorOpen: true, treeOpen: false, preflight: null, dialog: null, history: { canUndo: false, canRedo: false, recycleBin: [], modelMetadata: [] }, agentRuns: [], connection: previewConnection, p6eAcceptance: { enabled: false, receipt: null }, status: native ? '本地工作区已就绪；正在读取联网模型配置。' : 'Web 预览不会读写 Desktop 数据库。', error: '', accountSyncProgress: null, scale: 1, searchResults: [], searchPage: { hits: [], textCount: 0, attachmentCount: 0, totalCount: 0, hasMore: false, indexCurrent: true }, searchPanel: false, searchCategory: 'all', searchSortMode: 'default', searchFileType: 'all', searchFileTypeOpen: false, searchLoading: false, searchLoadingMore: false, searchError: '', searchEvidenceLabel: '', searchAnchorMessageId: null, searchAnchorAttachmentId: null, searchRevealTarget: null, searchHistory: [], searchHistoryOpen: false, searchHistoryHighlighted: null, searchHistoryManuallyOpened: false, searchScrollSnapshot: null, searchAttachmentMenu: null, suppressSearchHistoryFocus: false, imageThumbnails: {}, imageThumbnailPending: new Set(), videoThumbnails: {}, videoThumbnailPending: new Set(), searchAttachmentPreviews: {}, searchAttachmentPreviewPending: new Set(), assistantImageSelections: new Map(), imagePreview: null, pdfPreview: null, videoPreview: null, audioPreview: null, textPreview: null, previewBoundary: null, previewWorkspaceId: null, appearance: parityPreferences.readAppearance(), favoriteConversationIds: new Set(), unreadConversationIds: new Set(), manualUnreadAtMs: new Map(), conversationFindOpen: false, conversationFindQuery: '', conversationFindMatches: [], conversationFindIndex: 0, runtimeInfo: { version: '读取中', platform: navigator.platform || 'Desktop', arch: '本机架构', buildEpochSeconds: 0 } };
state.selectedWorkProjectId = null;
state.assistantMessageMenu = null;
state.cloudConversationOpening = null;
globalThis.__nanfengDesktopWorkState = state;
let transientToast = null;
let transientToastTimer = null;
let accountSyncProgressResultTimer = null;
let cloudListReadGeneration = 0;
let cloudListReadPending = false;
const pendingSyncReconciliations = new Set();

const openCloudConversationRoute = createCloudConversationOpenRoute({
  reuse: ({ workspaceId, conversationId }) => {
    const current = state.current;
    if (current?.summary?.id !== workspaceId || !resolveConversation(current, conversationId)) return null;
    return {
      current,
      history: state.history,
      alreadySelected: !state.cloudConversationOpening && state.pane === 'chat' && state.selectedConversationId === conversationId,
    };
  },
  load: async ({ workspaceId, conversationId }) => {
    const [current, history] = await Promise.all([
      invoke('read_desktop_workspace', { workspaceId }),
      invoke('read_desktop_workbench_history', { workspaceId }),
    ]);
    if (!resolveConversation(current, conversationId)) throw new Error('云端会话详情暂不可用，请先读取云端列表后重试。');
    return { current, history };
  },
  // Model preferences decorate the composer only. They are intentionally read
  // after the selected conversation is on screen, so a short SQLite delay can
  // never leave a cloud click looking like an empty new conversation.
  loadPreferences: ({ workspaceId, conversationId }) => invoke('read_desktop_p6g_selection', { workspaceId, conversationId }),
  onPending: route => {
    state.cloudConversationOpening = route;
    state.pane = 'chat';
    state.selectedConversationId = null;
    state.p6gSelection = null;
    state.conversationPreferences = { revision: 0, toneOverride: null, webSearchOverride: null };
    state.error = '';
    render();
  },
  onOpened: (route, { current, history }) => {
    state.current = current;
    state.history = history;
    state.selectedConversationId = route.conversationId;
    state.cloudConversationOpening = null;
    state.p6gSelection = null;
    state.conversationPreferences = { revision: 0, toneOverride: null, webSearchOverride: null };
    state.profileOpen = false;
    state.error = '';
    if (!state.chatScrollPositions.has(route.conversationId)) state.pendingChatScrollToLatestId = route.conversationId;
    reloadFavoriteConversationIds();
    void loadConversationReadState({ observeSelected: true }).catch(() => {});
    render();
  },
  onPreferences: (route, selection) => {
    if (state.current?.summary?.id !== route.workspaceId || state.selectedConversationId !== route.conversationId) return;
    state.p6gSelection = selection;
    state.conversationPreferences = selection?.conversationOverride || { revision: 0, toneOverride: null, webSearchOverride: null };
    render();
  },
  onPreferencesFailure: () => {
    // The transcript is already visible. Preference reads may retry naturally
    // on the next selection; never replace content with a secondary failure.
  },
  onFailure: (_route, error) => {
    state.cloudConversationOpening = null;
    state.error = `云端会话未打开：${String(error)}`;
    render();
  },
});

function showTransientToast(message, kind = 'success') {
  transientToast = { message, kind };
  window.clearTimeout(transientToastTimer);
  render();
  transientToastTimer = window.setTimeout(() => {
    transientToast = null;
    render();
  }, 2600);
}

const ACCOUNT_SYNC_PROGRESS_MIN_VISIBLE_MS = 520;
const ACCOUNT_SYNC_RESULT_VISIBLE_MS = 2600;

function beginAccountSyncProgress(operation) {
  state.accountSyncProgress = { operation, startedAt: performance.now() };
  render();
}

// A single requestAnimationFrame callback runs before its frame is painted.
// Waiting for its successor guarantees the progress surface was actually
// visible before an IPC or network operation can occupy the renderer.
function waitForAccountSyncProgressPaint() {
  return new Promise(resolve => window.requestAnimationFrame(() => window.requestAnimationFrame(resolve)));
}

async function completeAccountSyncProgress(message, kind = 'success') {
  const startedAt = state.accountSyncProgress?.startedAt;
  if (Number.isFinite(startedAt)) {
    const remaining = ACCOUNT_SYNC_PROGRESS_MIN_VISIBLE_MS - (performance.now() - startedAt);
    if (remaining > 0) await new Promise(resolve => window.setTimeout(resolve, remaining));
  }
  const completedProgress = { ...state.accountSyncProgress, result: { message, kind } };
  state.accountSyncProgress = completedProgress;
  window.clearTimeout(accountSyncProgressResultTimer);
  render();
  accountSyncProgressResultTimer = window.setTimeout(() => {
    if (state.accountSyncProgress === completedProgress) {
      state.accountSyncProgress = null;
      render();
    }
  }, kind === 'success' ? ACCOUNT_SYNC_RESULT_VISIBLE_MS : 8000);
}
let composerModelPricingRefreshTimer = null;
state.pendingNewConversationModelId = null;
state.p6gModelPickerTier = null;

if (c16PreviewState) {
  state.appearance = {
    mode: c16PreviewState.appearance.mode,
    fontSize: c16PreviewState.font.id,
    themeColor: 'orange',
  };
}

state.modelCredentialDraft = '';
state.transcription = { settings: { ...TRANSCRIPTION_PREVIEW_STATE.settings }, tasks: [] };
state.transcriptionMode = 'document';
state.selectedTranscriptionTaskId = null;
state.transcriptionBusyTaskId = null;
state.searchLocatedArchivedConversationId = null;
state.searchReturnActive = false;
state.appSettingsRevision = 0;
state.settingsCapabilities = { ...DESKTOP_SETTINGS_CAPABILITIES };
state.accountSync = { configured: false, state: 'NOT_CONFIGURED', recoveryState: 'UNAVAILABLE', periodicEnabled: false, rotationPending: false, selectedConversationCount: 0, diagnostics: [], notifications: [] };
state.accountRecovery = null;
let accountSignInPending = false;
state.historyKnowledge = { enabled: false, paused: true, lastDispatchedAtMs: null, nextEligibleAtMs: null, candidates: [] };
state.settingsScrollPositions = new Map();
state.settingsConversationReturn = null;
state.workspaceReturn = null;
state.memorySummaryNotice = '';
state.sidebarScrollTop = 0;
state.reminders = { drafts: [], plans: [], diagnostics: [] };
state.reminderEditor = null;
state.reminderNotificationPermission = 'default';
state.reminderNotificationBridge = { supported: false, initialized: false, listenerReady: false, safeCode: 'NOT_READ', pendingActionCount: 0 };
state.selectedReminderPlanId = null;
state.backgroundRuntime = { desiredEnabled: false, installed: false, safeCode: 'NOT_READ', label: '', updatedAtMs: 0 };
state.conversationReadState = { workspaceId: null, conversations: [] };
state.unreadConversationIds = new Set();
state.manualUnreadAtMs = new Map();
state.pendingCreatedConversationRouteWorkspaceId = null;
// A submitted message may move the viewport to its freshly-created reply once.
// It is deliberately a content-free client receipt. Runtime events also fire for
// every streaming checkpoint, accounting update and title update, so a workspace
// or conversation ID alone is not sufficient to identify the originating Send.
state.pendingChatSubmission = null;

const ORDINARY_CHAT_RUNTIME_REFRESH_INTERVAL_MS = 80;
let ordinaryChatRuntimeRefreshPending = null;
let ordinaryChatRuntimeRefreshTimer = null;
let ordinaryChatRuntimeRefreshInFlight = false;

function scheduleOrdinaryChatRuntimeRefresh() {
  if (ordinaryChatRuntimeRefreshTimer !== null || ordinaryChatRuntimeRefreshInFlight) return;
  ordinaryChatRuntimeRefreshTimer = window.setTimeout(() => {
    ordinaryChatRuntimeRefreshTimer = null;
    void flushOrdinaryChatRuntimeRefresh();
  }, ORDINARY_CHAT_RUNTIME_REFRESH_INTERVAL_MS);
}

async function syncPersistedConversationContinuation(workspaceId, conversationId) {
  try {
    const receipt = await syncSelectedContinuation({ invoke, workspaceId, conversationId,
      syncedConversationKeys: state.accountSync?.syncedConversationKeys || [] });
    if (receipt?.status === 'UNKNOWN') {
      state.accountSync = { ...state.accountSync, pendingUnknown: { workspaceId, conversationId } };
      scheduleUnknownSyncReconciliation(workspaceId, conversationId);
      state.error = '回复已保存在本机；云端提交结果正在核对，尚未确认同步完成。';
    }
  } catch (error) {
    // A failed cloud write must never turn a completed provider request into
    // "message not submitted" or restore its draft for accidental resending.
    state.error = `回复已保存在本机，但云端续同步未完成：${String(error)}`;
  }
}

function queueOrdinaryChatRuntimeRefresh(payload) {
  if (!payload?.workspaceId || state.current?.summary?.id !== payload.workspaceId) return;
  ordinaryChatRuntimeRefreshPending = payload;
  scheduleOrdinaryChatRuntimeRefresh();
}

function createChatSubmissionId() {
  const id = globalThis.crypto?.randomUUID?.();
  if (typeof id === 'string' && id.length >= 12) return id;
  const bytes = new Uint8Array(16);
  const filled = typeof globalThis.crypto?.getRandomValues === 'function';
  if (filled) globalThis.crypto.getRandomValues(bytes);
  const suffix = Array.from(bytes, value => value.toString(16).padStart(2, '0')).join('');
  return `desktop-send-${Date.now().toString(36)}-${filled ? suffix : Math.random().toString(36).slice(2)}`;
}

function claimSubmittedChatRoute(payload) {
  const conversationId = payload?.conversationId || null;
  const pending = state.pendingChatSubmission;
  if (!conversationId || !pending
    || pending.workspaceId !== payload?.workspaceId
    || pending.clientSubmissionId !== payload?.clientSubmissionId) return false;
  if (pending.conversationId && pending.conversationId !== conversationId) {
    state.pendingChatSubmission = null;
    return false;
  }
  if (!pending.conversationId
    && !state.selectedConversationId
    && state.pendingCreatedConversationRouteWorkspaceId === payload.workspaceId) {
    state.selectedConversationId = conversationId;
    state.pendingCreatedConversationRouteWorkspaceId = null;
  }
  const ownsSubmittedRoute = state.selectedConversationId === conversationId;
  state.pendingChatSubmission = null;
  if (ownsSubmittedRoute) {
    state.pendingChatSendScrollToLatestId = conversationId;
  }
  return ownsSubmittedRoute;
}

async function flushOrdinaryChatRuntimeRefresh() {
  if (ordinaryChatRuntimeRefreshInFlight) return;
  const payload = ordinaryChatRuntimeRefreshPending;
  ordinaryChatRuntimeRefreshPending = null;
  if (!payload) return;
  if (state.current?.summary?.id !== payload.workspaceId) return;
  ordinaryChatRuntimeRefreshInFlight = true;
  try {
    state.current = await invoke('read_desktop_workspace', { workspaceId: payload.workspaceId });
    claimSubmittedChatRoute(payload);
    await loadConversationReadState({ observeSelected: true });
    state.error = '';
    render();
  } catch (error) {
    state.error = `聊天增量已持久化，但界面刷新失败：${String(error)}`;
    render();
  } finally {
    ordinaryChatRuntimeRefreshInFlight = false;
    if (ordinaryChatRuntimeRefreshPending) scheduleOrdinaryChatRuntimeRefresh();
  }
}

const tauriEvents = window.__TAURI__?.event;
if (native && tauriEvents?.listen) {
  void tauriEvents.listen('desktop-ordinary-chat-runtime-v1', event => queueOrdinaryChatRuntimeRefresh(event?.payload || {}));
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
  const prefersDark = c16PreviewState?.appearance.mode === 'system'
    ? c16PreviewState.appearance.prefersDark
    : systemDarkQuery?.matches;
  applyDesktopThemeToRoot(document.documentElement, state.appearance, prefersDark);
}

function applyC16Preview(surface = 'Browser') {
  if (!c16PreviewState) return;
  const layer = c16PreviewState.layer.id;
  state.appearance = { mode: c16PreviewState.appearance.mode, fontSize: c16PreviewState.font.id, themeColor: 'orange' };
  state.pane = layer === 'settings-theme' ? 'settings' : 'chat';
  state.settingsSection = layer === 'settings-theme' ? 'appearance' : state.settingsSection;
  state.settingsPicker = layer === 'settings-theme' ? 'themeColor' : null;
  state.searchPanel = layer === 'search-history';
  state.searchHistory = layer === 'search-history' ? ['主题字体弹层验收', '本地搜索历史'] : [];
  state.searchHistoryOpen = layer === 'search-history';
  state.searchHistoryHighlighted = layer === 'search-history' ? '主题字体弹层验收' : null;
  state.composerAddOpen = layer === 'add-root' || layer === 'style';
  state.composerAddPage = layer === 'style' ? 'tone' : 'root';
  state.p6gModelPickerOpen = ['model-root', 'model-daily', 'model-deep'].includes(layer);
  state.p6gSelection = {
    ...(state.p6gSelection || {}),
    pickerTier: layer === 'model-daily' ? 'DAILY' : layer === 'model-deep' ? 'DEEP' : null,
    conversationOverride: state.p6gSelection?.conversationOverride || { revision: 0, modelId: null },
  };
  state.status = `C16 ${surface}只读矩阵 · ${c16PreviewState.appearance.id} · ${c16PreviewState.font.id} · ${layer}`;
  applyAppearance();
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
  while (!isSettled()) {
    await new Promise(resolve => window.setTimeout(resolve, 120));
    if (isSettled() || state.current?.summary?.id !== workspaceId) continue;
    try {
      const current = await invoke('read_desktop_workspace', { workspaceId });
      state.current = current;
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
  const query = state.conversationFindQuery;
  requestAnimationFrame(() => {
    if (!state.conversationFindOpen || state.conversationFindQuery !== query) return;
    const input = document.querySelector('#conversation-find-input');
    if (input && document.activeElement !== input) {
      input.focus({ preventScroll: true });
      input.setSelectionRange(input.value.length, input.value.length);
    }
    const match = state.conversationFindMatches[state.conversationFindIndex];
    const message = match ? document.querySelector(`[data-message-id="${CSS.escape(match.messageId)}"]`) : null;
    const owner = message?.closest('.chat-scroll[data-scroll-owner="message-list"]');
    const target = message?.querySelector('.chat-message-body mark') || message;
    if (!owner || !target) return;
    const ownerBounds = owner.getBoundingClientRect();
    const targetBounds = target.getBoundingClientRect();
    if (ownerBounds.height <= 0 || targetBounds.height <= 0) return;
    owner.scrollTo({
      top: conversationFindScrollTop({
        scrollTop: owner.scrollTop,
        ownerTop: ownerBounds.top,
        ownerHeight: ownerBounds.height,
        targetTop: targetBounds.top,
        targetHeight: targetBounds.height,
      }),
      behavior: 'smooth',
    });
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

let appSettingsSaveQueue = Promise.resolve();
function persistNativeAppSettings(appearance, product) {
  const appearancePatch = { ...appearance };
  const productPatch = { ...product };
  const save = appSettingsSaveQueue.catch(() => {}).then(() => persistAppSettingsPatch(appearancePatch, productPatch));
  appSettingsSaveQueue = save;
  return save;
}
async function persistAppSettingsPatch(appearance, product) {
  if (!native) {
    state.appearance = parityPreferences.writeAppearance({ ...state.appearance, ...appearance });
    state.productSettings = parityPreferences.writeProductSettings({ ...state.productSettings, ...product });
    return;
  }
  const current = await invoke('read_desktop_app_settings');
  const projection = await invoke('save_desktop_app_settings', { args: { appearance: normalizeAppearance({ ...current.appearance, ...appearance }), product: normalizeProductSettings({ ...current.product, ...product }), expectedRevision: current.revision } });
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
    await persistNativeAppSettings({ [field]: value }, {});
    state.status = '';
    state.error = '';
  } catch (error) {
    state.appearance = previous;
    applyAppearance();
    state.error = `外观偏好未保存：${String(error)}`;
  }
  render();
}

async function toggleFavoriteConversation(conversationId, { renderAfter = true } = {}) {
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
  if (renderAfter) render();
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

state.archivePreview = null;
applyAppearance();
systemDarkQuery?.addEventListener?.('change', () => { if (state.appearance.mode === 'system') { applyAppearance(); render(); } });
state.v2CommittedExchanges = [];
globalThis.__nanfengV2CommittedExchanges = state.v2CommittedExchanges;
let p6kManualLink = { assetOrdinal: null, conversationId: null, messageId: null };
globalThis.__nanfengP6kManualLink = p6kManualLink;
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
function rememberOverlayTrigger(target) {
  overlayFocusReturn = target ? ['action', 'messageId', 'entryId', 'id', 'attachmentId']
    .filter(key => target.dataset?.[key])
    .map(key => `[data-${key.replace(/[A-Z]/g, letter => `-${letter.toLowerCase()}`)}="${CSS.escape(target.dataset[key])}"]`).join('') : null;
}
function restoreOverlayFocus() { const selector = overlayFocusReturn; overlayFocusReturn = null; if (selector) queueMicrotask(() => document.querySelector(selector)?.focus({ preventScroll: true })); }
/** Single owner for app-owned transient layers; native system pickers intentionally remain outside it. */
function closeTopOverlay({ restoreFocus = true, navigateComposerLayerBack = false } = {}) {
  if (state.dialog?.kind === 'local-backup-restart-required') return false;
  if (navigateComposerLayerBack && state.composerAddOpen && state.composerAddPage === 'tone') {
    state.composerAddPage = 'root';
    render();
    queueMicrotask(() => document.querySelector('[data-action="open-composer-tone-picker"]')?.focus());
    return true;
  }
  if (state.assistantMessageMenu) state.assistantMessageMenu = null;
  else if (state.contextMenu) state.contextMenu = null;
  else if (state.temporaryModelOpen) state.temporaryModelOpen = false;
  else if (state.p6gModelPickerOpen) {
    if (navigateComposerLayerBack && state.p6gSelection?.pickerTier) {
      state.p6gSelection = { ...state.p6gSelection, pickerTier: null };
      state.p6gModelPickerTier = null;
      render();
      queueMicrotask(() => document.querySelector('.composer-model-sheet-nav')?.focus());
      return true;
    }
    state.p6gModelPickerOpen = false;
    state.p6gModelPickerTier = null;
  }
  else if (state.composerAddOpen) { state.composerAddOpen = false; state.composerAddPage = 'root'; }
  else if (state.cameraCaptureOpen) stopComposerCamera();
  else if (state.profileOpen) state.profileOpen = false;
  else if (state.dialog) state.dialog = null;
  else if (state.imagePreview) { cancelImagePreviewLoad(); state.imagePreview = null; }
  else if (state.pdfPreview) state.pdfPreview = null;
  else if (state.archivePreview) state.archivePreview = null;
  else if (state.previewBoundary) state.previewBoundary = null;
  else if (state.sidebarOpen) state.sidebarOpen = false;
  else return false;
  render();
  if (restoreFocus) restoreOverlayFocus();
  return true;
}
function openTransientOverlay(kind, target, value = true) {
  state.contextMenu = kind === 'context' ? value : null;
  state.assistantMessageMenu = kind === 'assistant-message-menu' ? value : null;
  state.composerAddOpen = kind === 'composer-add';
  if (kind === 'composer-add') state.composerAddPage = 'root';
  state.temporaryModelOpen = kind === 'temporary-model';
  state.p6gModelPickerOpen = kind === 'p6g-model-picker';
  if (kind === 'p6g-model-picker') state.p6gModelPickerTier = null;
  if (kind === 'p6g-model-picker' && state.p6gSelection) state.p6gSelection = { ...state.p6gSelection, pickerTier: null };
  state.profileOpen = kind === 'profile';
  rememberOverlayTrigger(target);
  render();
}
app.addEventListener('click', event => {
  const target = event.target.closest('[data-action]');
  const action = target?.dataset.action;
  const workspaceAction = ['show-work', 'show-workspace', 'show-projects', 'show-knowledge', 'show-memory'].includes(action);
  if (workspaceAction && target.closest('.android-settings-main')) state.workspaceReturn = { pane: 'settings', settingsSection: state.settingsSection, label: '返回' };
  else if (action === 'show-work' && state.pane === 'chat') state.workspaceReturn = { pane: 'chat', conversationId: state.selectedConversationId || null, label: '返回' };
  if (action === 'show-work' && state.pane === 'chat' && state.selectedConversationId) state.chatReturnConversationId = state.selectedConversationId;
  if (action === 'show-chat' && !state.selectedConversationId && state.chatReturnConversationId) state.selectedConversationId = state.chatReturnConversationId;
}, true);

app.addEventListener('click', event => {
  const target = event.target.closest?.('[data-action="select-assistant-gallery-image"]');
  if (!target) return;
  event.preventDefault();
  event.stopImmediatePropagation();
  const messageId = String(target.dataset.messageId || '');
  const attachmentId = String(target.dataset.attachmentId || '');
  if (!messageId || !attachmentId) return;
  state.assistantImageSelections.set(messageId, attachmentId);
  setAssistantGallerySelection(target, attachmentId);
}, true);

function setAssistantGallerySelection(target, attachmentId) {
  const gallery = target.closest?.('.chat-assistant-image-gallery');
  if (!gallery) return;
  gallery.querySelectorAll('[data-assistant-gallery-main-image]').forEach(surface => {
    surface.hidden = surface.dataset.attachmentId !== attachmentId;
  });
  gallery.querySelectorAll('[data-action="select-assistant-gallery-image"]').forEach(control => {
    control.setAttribute('aria-pressed', String(control.dataset.attachmentId === attachmentId));
  });
}

app.addEventListener('click', event => {
  const target = event.target.closest?.('[data-action]');
  if (event.target.classList?.contains('scrim')) {
    // A scrim is always cancel-only. It never invokes a destructive confirmation action.
    event.preventDefault();
    event.stopImmediatePropagation();
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

function reminderFormValues() {
  return {
    title: document.querySelector('#reminder-title')?.value || '',
    instruction: document.querySelector('#reminder-instruction')?.value || '',
    scheduleKind: document.querySelector('#reminder-schedule-kind')?.value || 'ONCE',
    anchorLocal: document.querySelector('#reminder-anchor-local')?.value || '',
    timezoneId: document.querySelector('#reminder-timezone')?.value || '',
    missedPolicy: document.querySelector('#reminder-missed-policy')?.value || 'RUN_ONCE',
  };
}

function updateC09PreviewPlan(planId, patch) {
  state.reminders = { ...state.reminders, plans: state.reminders.plans.map(item => item.planId === planId ? { ...item, ...patch } : item) };
}

app.addEventListener('click', event => {
  const target = event.target.closest?.('[data-action]');
  const action = target?.dataset.action;
  const actions = ['generate-reminder-draft', 'create-manual-reminder-draft', 'review-reminder-draft', 'retry-reminder-draft', 'confirm-reminder-draft', 'reject-reminder-draft', 'edit-reminder-plan', 'confirm-edit-reminder-plan', 'set-reminder-paused', 'retry-reminder-plan', 'delete-reminder-plan', 'confirm-delete-reminder-plan', 'request-reminder-notification-permission', 'cancel-reminder-editor', 'set-reminder-schedule-mode', 'set-reminder-cadence'];
  if (!actions.includes(action)) return;
  event.preventDefault();
  event.stopImmediatePropagation();
  if (action === 'cancel-reminder-editor') {
    const editor = state.reminderEditor;
    if (!editor?.discardOnCancel || !editor.item?.draftId) {
      state.reminderEditor = null;
      render();
      return;
    }
    void (async () => {
      try {
        if (!native) state.reminders = { ...state.reminders, drafts: state.reminders.drafts.filter(item => item.draftId !== editor.item.draftId) };
        else state.reminders = await invoke('reject_desktop_reminder_draft', { draftId: editor.item.draftId });
        state.reminderEditor = null;
        state.status = native ? '已取消手工新建；未创建计划。' : 'Web 只读交互样本已返回列表；未写入 Desktop SQLite。';
        state.error = '';
      } catch (error) {
        state.error = `手工提醒草案未取消：${String(error)}`;
      }
      render();
    })();
    return;
  }
  if (action === 'set-reminder-schedule-mode') {
    if (state.reminderEditor) state.reminderEditor = { ...state.reminderEditor, item: { ...state.reminderEditor.item, ...reminderFormValues(), scheduleKind: target.dataset.mode === 'ONCE' ? 'ONCE' : 'DAILY' } };
    render();
    return;
  }
  if (action === 'set-reminder-cadence') {
    if (state.reminderEditor) state.reminderEditor = { ...state.reminderEditor, item: { ...state.reminderEditor.item, ...reminderFormValues(), scheduleKind: target.dataset.value || 'DAILY' } };
    render();
    return;
  }
  void (async () => {
    try {
      if (!native && !c09ReminderPreview) throw new Error('Web 预览不会创建、执行或通知提醒计划');
      if (action === 'request-reminder-notification-permission') {
        if (!native) throw new Error('Web 只读交互样本不会请求系统通知');
        const granted = await requestReminderNotificationPermission();
        state.status = granted ? '系统通知权限已授予；后续成功监控可发送不含结果正文的通知。' : '系统未授予通知权限；监控结果仍只保存在本机。';
      } else if (action === 'generate-reminder-draft') {
        if (!native) throw new Error('Web 只读交互样本不调用模型');
        const workspaceId = state.current?.summary?.id;
        const conversationId = state.selectedConversationId;
        if (!workspaceId || !conversationId) throw new Error('当前对话不可用');
        state.status = '正在把明确的提醒意图整理成可编辑草案；尚未创建计划。';
        render();
        try {
          state.reminders = await invoke('generate_desktop_reminder_draft', { args: { workspaceId, conversationId, userMessageId: target.dataset.userMessageId, assistantMessageId: target.dataset.assistantMessageId, timezoneId: Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Shanghai' } });
        } catch (error) { await loadDesktopReminders(); throw error; }
        const draft = state.reminders.drafts.find(item => item.sourceAssistantMessageId === target.dataset.assistantMessageId);
        if (draft?.status === 'PENDING_REVIEW') state.reminderEditor = { kind: 'reminder-editor', mode: 'create', item: draft };
        state.status = draft?.status === 'NOT_ELIGIBLE' ? '模型判断该对话不构成明确提醒；未创建计划。' : '提醒草案已生成，请核对编辑后再确认。';
      } else if (action === 'create-manual-reminder-draft') {
        const timezoneId = Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Shanghai';
        if (!native) {
          state.reminderEditor = { kind: 'reminder-editor', mode: 'create', discardOnCancel: true, item: createC09ReminderPreviewDraft(timezoneId) };
        } else {
          const workspaceId = state.current?.summary?.id;
          if (!workspaceId) throw new Error('请先打开一个本地工作区');
          state.reminders = await invoke('create_desktop_manual_reminder_draft', { args: { workspaceId, conversationId: state.selectedConversationId || null, timezoneId } });
          const draft = state.reminders.drafts.find(item => item.status === 'PENDING_REVIEW');
          state.reminderEditor = { kind: 'reminder-editor', mode: 'create', discardOnCancel: true, item: draft };
        }
      } else if (action === 'review-reminder-draft') {
        const draft = state.reminders.drafts.find(item => item.draftId === target.dataset.id);
        if (draft) state.reminderEditor = { kind: 'reminder-editor', mode: 'create', item: draft };
      } else if (action === 'retry-reminder-draft') {
        if (!native) throw new Error('Web 只读交互样本不调用模型');
        state.status = '正在按显式操作重试草案 refinement；UNKNOWN 不会自动重发。';
        render();
        try { state.reminders = await invoke('retry_desktop_reminder_draft', { draftId: target.dataset.id }); }
        catch (error) { await loadDesktopReminders(); throw error; }
        const draft = state.reminders.drafts.find(item => item.draftId === target.dataset.id);
        if (draft?.status === 'PENDING_REVIEW') state.reminderEditor = { kind: 'reminder-editor', mode: 'create', item: draft };
      } else if (action === 'confirm-reminder-draft') {
        const item = state.reminderEditor?.item;
        if (!item) throw new Error('提醒草案已变化');
        const values = reminderFormValues();
        if (!native) {
          const plan = { ...item, ...values, planId: `c09-preview-${Date.now()}`, status: 'ACTIVE', nextRunAtMs: Date.parse(values.anchorLocal), latestResult: null, lastSafeErrorCode: null, updatedAtMs: Date.now() };
          state.reminders = { ...state.reminders, plans: [...state.reminders.plans, plan], drafts: state.reminders.drafts.filter(draft => draft.draftId !== item.draftId) };
          state.status = 'Web 只读交互样本已演示返回列表；未写入 Desktop SQLite，未启动 scheduler。';
        } else {
          state.reminders = await invoke('confirm_desktop_reminder_draft', { args: { draftId: item.draftId, ...values } });
          state.status = '提醒计划已确认并持久化；到期后由本机 scheduler 执行。';
        }
        state.reminderEditor = null;
      } else if (action === 'reject-reminder-draft') {
        if (!native) state.reminders = { ...state.reminders, drafts: state.reminders.drafts.filter(item => item.draftId !== target.dataset.id) };
        else state.reminders = await invoke('reject_desktop_reminder_draft', { draftId: target.dataset.id });
        state.reminderEditor = null;
        state.status = '提醒草案已拒绝；未创建计划。';
      } else if (action === 'edit-reminder-plan') {
        const plan = state.reminders.plans.find(item => item.planId === target.dataset.id);
        if (plan) state.reminderEditor = { kind: 'reminder-editor', mode: 'edit', item: plan };
      } else if (action === 'confirm-edit-reminder-plan') {
        const item = state.reminderEditor?.item;
        if (!item) throw new Error('提醒计划已变化');
        const values = reminderFormValues();
        if (!native) updateC09PreviewPlan(item.planId, { ...values, updatedAtMs: Date.now() });
        else state.reminders = await invoke('update_desktop_reminder_plan', { args: { planId: item.planId, expectedUpdatedAtMs: item.updatedAtMs, ...values } });
        state.reminderEditor = null;
        state.status = native ? '提醒计划已更新；原执行记录保留，下一次执行时间已重新计算。' : 'Web 只读交互样本已演示编辑返回；未写入 Desktop SQLite。';
      } else if (action === 'set-reminder-paused') {
        const paused = target.dataset.paused === 'true';
        if (!native) updateC09PreviewPlan(target.dataset.id, { status: paused ? 'PAUSED' : 'ACTIVE', nextRunAtMs: paused ? null : 4092091800000 });
        else state.reminders = await invoke('set_desktop_reminder_plan_paused', { planId: target.dataset.id, paused });
        state.status = paused ? '计划已暂停；运行中的请求已请求停止。' : '计划已恢复，并从当前时间重新计算下一次执行。';
      } else if (action === 'retry-reminder-plan') {
        if (!native) updateC09PreviewPlan(target.dataset.id, { status: 'ACTIVE', lastSafeErrorCode: null });
        else state.reminders = await invoke('retry_desktop_reminder_plan', { planId: target.dataset.id });
        state.status = '计划已按显式操作恢复待执行；不会重复提交未知 Attempt。';
      } else if (action === 'delete-reminder-plan') {
        const plan = state.reminders.plans.find(item => item.planId === target.dataset.id);
        if (plan) state.dialog = { kind: 'reminder-delete', item: plan };
      } else if (action === 'confirm-delete-reminder-plan') {
        if (!native) state.reminders = { ...state.reminders, plans: state.reminders.plans.filter(item => item.planId !== target.dataset.id) };
        else state.reminders = await invoke('delete_desktop_reminder_plan', { planId: target.dataset.id });
        state.dialog = null;
        state.status = native ? '提醒计划及其执行记录已删除；运行中的请求已请求停止。' : 'Web 只读交互样本已演示删除；未写入 Desktop SQLite。';
      }
      if (native) await loadDesktopBackgroundRuntime();
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
  resetComposerPresentation();
  void Promise.all([markConversationOpened(target.dataset.id), loadOrdinaryComposerDraft()]).finally(render);
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
  if (!native) throw new Error('当前环境不支持保存未读标记');
  if (!workspaceId || !conversationId) throw new Error('当前会话不可用');
  const projection = await invoke('mark_desktop_conversation_unread', { workspaceId, conversationId });
  const marker = projection?.conversations?.find(item => item.conversationId === conversationId);
  if (marker?.manualUnreadAtMs == null) throw new Error('未读标记没有完成回读确认');
  applyConversationReadState(projection);
}
const selectedModelService = () => state.modelServiceSettings.find(item => item.providerId === state.modelProviderId) || state.modelServiceSettings[0] || null;
function modelServiceConfigurationStatus(settings = state.modelServiceSettings) {
  const enabled = (settings || []).filter(item => item?.enabled);
  const configured = enabled.filter(item => item?.credentialStored);
  if (configured.length) return `本地工作区已就绪；联网模型已配置：${configured.length} 个服务商已启用且凭据已保存，发送前仍会逐次确认。`;
  if (enabled.length) return `本地工作区已就绪；已有 ${enabled.length} 个联网服务商启用，尚未检测到已保存凭据。`;
  return '本地工作区已就绪；尚未启用联网模型。';
}
function resetModelServiceDraft(service = selectedModelService()) {
  state.modelServiceDraft = service ? { providerId: service.providerId, enabled: service.enabled, presetId: service.presetId, revision: service.revision } : null;
  state.modelCredentialDraft = '';
  state.modelCredentialEdited = false;
  state.modelCredentialVisible = false;
  state.modelSettingsNotice = '';
  state.modelSettingsError = '';
  state.settingsPicker = null;
}
function clearModelCredentialDraftOutsideEditor(force = false) {
  if (!force && state.pane === 'settings' && state.settingsSection === 'model-configuration') return;
  // A revealed or typed key must not survive in JavaScript state after the
  // settings editor is gone. Unsaved input is intentionally discarded here.
  state.modelCredentialDraft = '';
  state.modelCredentialEdited = false;
  state.modelCredentialVisible = false;
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
    state.status = modelServiceConfigurationStatus(settings);
  }
}
async function loadDesktopAppSettings() {
  if (!native) return;
  const legacyAppearance = normalizeAppearance(state.appearance);
  const legacyProduct = normalizeProductSettings(state.productSettings);
  let projection = await invoke('read_desktop_app_settings');
  if (Number(projection.revision || 0) === 0 && !state.runtimeInfo?.automaticWorkSuppressed) {
    projection = await invoke('save_desktop_app_settings', { args: { appearance: legacyAppearance, product: legacyProduct, expectedRevision: 0 } });
  }
  state.appearance = normalizeAppearance(projection.appearance);
  state.productSettings = normalizeProductSettings(projection.product);
  state.personalizationDraft = { ...state.productSettings };
  state.personalizationDirty = false;
  state.appSettingsRevision = Number(projection.revision || 0);
  state.settingsCapabilities = projection.capabilities || state.settingsCapabilities;
  if (!state.runtimeInfo?.automaticWorkSuppressed) parityPreferences.clearNativeAppSettingsMigrationSource();
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
  // `null` means the user has not requested a cloud read in this settings
  // session.  An empty array is a real, authenticated read that returned no
  // documents; collapsing the two made the page claim "none found" before it
  // had contacted the cloud at all.
  const remoteDocuments = Array.isArray(state.accountSync?.remoteDocuments)
    ? state.accountSync.remoteDocuments
    : null;
  const previous = state.accountSync;
  state.accountSync = { ...await invoke('read_desktop_account_sync'), pendingUnknown, remoteDocuments };
  if (previous?.email && previous.email === state.accountSync.email) state.accountSync.avatarDataUrl ||= previous.avatarDataUrl;
  if (state.accountSync.email && !state.accountSync.avatarDataUrl && !accountAvatarPending) {
    const email = state.accountSync.email;
    accountAvatarPending = true;
    invoke('read_desktop_google_avatar').then(avatar => {
      if (avatar && state.accountSync?.email === email) {
        state.accountSync.avatarDataUrl = avatar;
        if (state.settingsSection === 'account') render();
      }
    }).catch(() => {}).finally(() => { accountAvatarPending = false; });
  }
  return state.accountSync;
}

async function cloudRowsFromLocalEntries(entries) {
  if (!native || !Array.isArray(entries)) return [];
  const uniqueEntries = dedupeCloudConversationEntries(entries);
  // Restoring a cloud list often returns several rows from one workspace.  The
  // old per-row IPC pattern made every row wait for two SQLite reads.
  const workspaceIds = [...new Set(uniqueEntries.map(item => item?.workspaceId).filter(Boolean))];
  const projections = await Promise.all(workspaceIds.map(async workspaceId => {
    try {
      const [workspace, favoriteIds] = await Promise.all([
        invoke('read_desktop_workspace', { workspaceId }),
        invoke('read_desktop_favorite_conversation_ids', { workspaceId }),
      ]);
      return [workspaceId, { workspace, favoriteIds }];
    } catch { return null; }
  }));
  const byWorkspace = new Map(projections.filter(Boolean));
  const rows = uniqueEntries.map(item => {
    if (!item?.workspaceId || !item?.conversationId) return null;
    const projection = byWorkspace.get(item.workspaceId);
    const conversation = projection?.workspace?.exchange?.conversations?.find(entry => entry.id === item.conversationId);
    // A cloud list is a separate presentation surface. The restored workspace
    // is only used to render/open its content, never as the owner of cloud
    // list ordering or pinning.
    return conversation ? { ...conversation, workspaceId: item.workspaceId, favorite: projection.favoriteIds.includes(item.conversationId), pinned: Boolean(item.cloudPinned), cloudPinned: Boolean(item.cloudPinned) } : null;
  });
  return dedupeCloudConversationEntries(rows);
}

async function restoreCachedCloudConversationList() {
  const cached = readCloudConversationCache();
  // A list is private to the signed-in Google identity. A prior account's
  // cached IDs are never shown after an account switch or sign-out.
  if (!cached || !state.accountSync?.email || cached.email !== state.accountSync.email) return;
  state.cloudConversations = await cloudRowsFromLocalEntries(cached.entries);
  persistCloudConversationCache();
}

function accountSyncErrorMessage(error) {
  const raw = String(error || '').trim();
  try {
    const parsed = JSON.parse(raw);
    if (typeof parsed?.error === 'string' && parsed.error.trim()) return parsed.error.trim();
  } catch (_) { /* Native failures are not required to be JSON. */ }
  return raw || '同步操作失败，请稍后重试。';
}
let accountAvatarPending = false;
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

function scheduleUnknownSyncReconciliation(workspaceId, conversationId) {
  if (!native || !workspaceId || !conversationId) return;
  const key = `${workspaceId}:${conversationId}`;
  if (pendingSyncReconciliations.has(key)) return;
  pendingSyncReconciliations.add(key);
  // The submit response was uncertain, not a failed write.  Reconcile after
  // yielding the click frame so the user sees the honest pending state first.
  window.setTimeout(() => {
    void invoke('reconcile_desktop_conversation_sync', { workspaceId, conversationId })
      .then(receipt => {
        if (receipt.status === 'SYNCED_AFTER_RECONCILE' || receipt.status === 'SYNCED_AFTER_REMOTE_RECONCILE') {
          state.accountSync = { ...state.accountSync, pendingUnknown: null };
          state.status = receipt.status === 'SYNCED_AFTER_REMOTE_RECONCILE' ? '已合并云端最新对话并完成同步。' : '同步成功，已回读校验。';
          state.error = '';
          showTransientToast(state.status);
          notifyAccountSync('success');
        } else if (receipt.status === 'SAFE_TO_RETRY_EXPLICITLY') {
          state.accountSync = { ...state.accountSync, pendingUnknown: { workspaceId, conversationId } };
          state.status = '云端未确认本次提交；未重复上传。';
          notifyAccountSync('attention');
        } else {
          state.accountSync = { ...state.accountSync, pendingUnknown: { workspaceId, conversationId } };
          state.status = '云端仍在核对，未覆盖任何内容。';
          notifyAccountSync('attention');
        }
      })
      .catch(() => {
        state.accountSync = { ...state.accountSync, pendingUnknown: { workspaceId, conversationId } };
        state.status = '云端提交正在后台核对；不会重复上传。';
      })
      .finally(() => {
        pendingSyncReconciliations.delete(key);
        render();
        void loadDesktopAccountSync().then(render).catch(() => {});
      });
  }, 0);
}
async function loadTranscriptionState() {
  if (!native) {
    state.transcription = c10TranscriptionPreview
      ? createC10TranscriptionPreviewProjection(c10TranscriptionPreview)
      : { settings: { ...TRANSCRIPTION_PREVIEW_STATE.settings }, tasks: [] };
    state.selectedTranscriptionTaskId = state.transcription.tasks[0]?.id || null;
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

async function importDroppedTranscriptionFiles(files) {
  if (!native) return;
  const failures = [];
  for (const file of files) {
    try {
      const task = await invoke('import_desktop_ocr_drop', { args: { workspaceId: state.current?.summary?.id || '', displayName: file.name, bytesBase64: await clipboardFileBase64(file) } });
      state.selectedTranscriptionTaskId = task.id;
    } catch { failures.push(file.name); }
  }
  await loadTranscriptionState();
  if (failures.length) state.dialog = { kind: 'model-settings-feedback', failed: true, message: '部分文件未导入，请使用 JPG、PNG 或 PDF 文件。' };
  render();
}

async function pickTranscriptionDocument() {
  if (!native) {
    if (c10TranscriptionPreview) {
      const task = createC10ImportedPreviewTask();
      state.transcription = { ...state.transcription, tasks: [task, ...state.transcription.tasks.filter(item => item.id !== task.id)] };
      state.selectedTranscriptionTaskId = task.id;
      state.status = 'Web 只读交互样本已演示文件返回；未读取字节，未写入 Desktop SQLite。';
      state.error = '';
    } else state.error = 'Web 预览不会读取本机图片或 PDF。';
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

async function executeTranscriptionTask(taskId, retry = false) {
  if ((!native && !c10TranscriptionPreview) || state.transcriptionBusyTaskId) return;
  const documentTask = transcriptionTask(taskId)?.modelId === 'glm-ocr';
  if (!native) {
    state.transcription = { ...state.transcription, tasks: state.transcription.tasks.map(task => task.id === taskId ? { ...task, state: 'TRANSCRIBING', errorCode: null, userMessage: null } : task) };
    state.transcriptionBusyTaskId = taskId;
    state.status = 'Web 只读交互样本只演示处理状态；未调用 Provider，未写入 Desktop SQLite。';
    state.error = '';
    render();
    return;
  }
  state.transcriptionBusyTaskId = taskId;
  state.error = '';
  render();
  try {
    if (retry) await invoke('retry_desktop_transcription_task', { taskId });
    await invoke(documentTask ? 'run_desktop_ocr_task' : 'run_desktop_transcription_task', { taskId });
    state.status = documentTask ? '图片/PDF 已转换为 Markdown，来源、结果与费用已保存。' : '语音转写已完成，结果、时间轴与费用已保存。';
  } catch (error) {
    state.error = `${documentTask ? '图片/PDF 转写' : '语音转写'}未完成：${String(error)}`;
  } finally {
    state.transcriptionBusyTaskId = null;
    await loadTranscriptionState().catch(() => {});
    render();
  }
}

async function previewTranscriptionSource(taskId) {
  const task = transcriptionTask(taskId);
  if (!task) return;
  if (!native) {
    state.previewBoundary = { attachmentId: task.sourceAttachmentId, workspaceId: task.workspaceId, displayName: task.sourceDisplayName, mimeType: task.sourceMimeType, source: 'browser' };
    render();
    return;
  }
  if (['image/jpeg', 'image/png'].includes(task.sourceMimeType)) return openImagePreview(task.sourceAttachmentId, task.workspaceId);
  if (task.sourceMimeType === 'application/pdf') return openPdfPreview(task.sourceAttachmentId, undefined, task.workspaceId);
  if (task.sourceMimeType === 'video/mp4') return openVideoPreview(task.sourceAttachmentId, undefined, task.workspaceId);
  if (['audio/mpeg', 'audio/wav', 'audio/mp4'].includes(task.sourceMimeType)) return openAudioPreview(task.sourceAttachmentId, undefined, task.workspaceId);
  try { await invoke('open_desktop_attachment_with_system', { args: { workspaceId: task.workspaceId, attachmentId: task.sourceAttachmentId } }); }
  catch (error) { state.error = `原文件未打开：${String(error)}`; render(); }
}

async function previewTranscriptionResult(taskId) {
  const task = transcriptionTask(taskId);
  if (!task?.resultAttachmentId) return;
  if (!native) {
    state.previewBoundary = { attachmentId: task.resultAttachmentId, workspaceId: task.workspaceId, displayName: `${String(task.sourceDisplayName || 'GLM-OCR').replace(/\.[^.]+$/, '')}-OCR.md`, mimeType: 'text/markdown', source: 'browser' };
    render();
    return;
  }
  return openTextPreview(task.resultAttachmentId, task.workspaceId);
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
  const actions = ['show-reminders', 'show-transcription', 'pick-transcription-document', 'select-transcription-task', 'run-transcription-task', 'retry-transcription-task', 'cancel-transcription-task', 'preview-transcription-source', 'preview-transcription-result', 'copy-transcription-result', 'continue-chat-with-transcription', 'export-transcription-task', 'ask-delete-transcription-task', 'confirm-delete-transcription-task'];
  if (!actions.includes(action)) return;
  event.preventDefault();
  event.stopImmediatePropagation();
  if (action === 'show-reminders') { state.utilitySidebarWorkMode = false; state.pane = 'reminders'; state.sidebarOpen = false; state.profileOpen = false; await loadDesktopReminders().catch(error => { state.error = `定时任务未读取：${String(error)}`; }); render(); return; }
  if (action === 'show-transcription') { state.utilitySidebarWorkMode = false; state.pane = 'transcription'; state.sidebarOpen = false; state.profileOpen = false; await loadTranscriptionState().catch(error => { state.error = `南枫转写任务未读取：${String(error)}`; }); render(); return; }
  if (action === 'pick-transcription-document') return pickTranscriptionDocument();
  if (action === 'select-transcription-task') { state.selectedTranscriptionTaskId = target.dataset.taskId; render(); return; }
  if (action === 'run-transcription-task') return executeTranscriptionTask(target.dataset.taskId, false);
  if (action === 'retry-transcription-task') return executeTranscriptionTask(target.dataset.taskId, true);
  if (action === 'cancel-transcription-task') { try { await invoke('cancel_desktop_transcription_task', { taskId: target.dataset.taskId }); state.status = '转写任务已取消；已保存的检查点仍保留供后续显式重试。'; state.error = ''; } catch (error) { state.error = `任务未取消：${String(error)}`; } await loadTranscriptionState().catch(() => {}); render(); return; }
  if (action === 'preview-transcription-source') return previewTranscriptionSource(target.dataset.taskId);
  if (action === 'preview-transcription-result') return previewTranscriptionResult(target.dataset.taskId);
  if (action === 'copy-transcription-result') { const text = (transcriptionTask(target.dataset.taskId)?.segments || []).map(segment => segment.text).join('\n\n'); try { await navigator.clipboard.writeText(text); showCopyIconFeedback(target); state.error = ''; } catch { state.error = '系统未允许写入剪贴板。'; render(); } return; }
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
    state.status = modelServiceConfigurationStatus();
    state.modelServiceDraft = { providerId: saved.providerId, enabled: saved.enabled, presetId: saved.presetId, revision: saved.revision };
    if (state.modelCredentialEdited && state.modelCredentialDraft.trim()) {
      state.modelSettingsNotice = '';
      state.dialog = { kind: 'model-credential-saved' };
    } else {
      state.modelSettingsNotice = '模型设置已保存。';
    }
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
  // A typed or already revealed key is already available in this editor.
  if (typeof state.modelCredentialDraft === 'string' && state.modelCredentialDraft.length > 0) {
    state.modelCredentialVisible = true;
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
const bytes = value => {
  const count = Math.max(0, Number(value) || 0);
  return count < 1024 ? `${count} B` : `${(count / 1024).toFixed(1)} KiB`;
};
const intent = prefix => `${prefix}-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
const icon = (path, label) => `<svg aria-hidden="true" viewBox="0 0 24 24"><title>${label}</title><path d="${path}"/></svg>`;
let fullSearchDebounce = null;
let fullSearchGeneration = 0;
let fullSearchRequestId = 0;
let fullSearchCategoryFrame = null;
let settingsSearchRenderFrame = null;
let transcriptRailSyncFrame = null;
let pendingTranscriptRailScroll = null;
let renderedTranscriptRetention = null;
let renderedSidebarRetention = null;
let renderedAttachmentPreview = null;
const conversationFindInputController = createConversationFindInputController({
  onSettled: query => {
    if (!state.conversationFindOpen || state.conversationFindQuery !== query) return;
    recomputeConversationFind({ resetIndex: true });
    render();
    focusCurrentFindMatch();
  },
  setTimer: (callback, milliseconds) => window.setTimeout(callback, milliseconds),
  clearTimer: timer => window.clearTimeout(timer),
});

function assistantGallerySelectionKey(selections = state.assistantImageSelections) {
  return selections instanceof Map
    ? Array.from(selections, ([messageId, attachmentId]) => `${messageId}:${attachmentId}`).join('|')
    : JSON.stringify(selections || {});
}

function currentTranscriptRetention(data) {
  if (state.searchPanel || state.temporaryConversation || state.p6eAcceptance.enabled) return null;
  const conversation = state.pane === 'work'
    ? resolveWorkConversationState(data, state.selectedWorkProjectId, state.selectedConversationId).conversation
    : state.pane === 'chat' ? resolveConversation(data, state.selectedConversationId) : null;
  const messages = orderedConversationMessages(conversation);
  if (!conversation || !messages.length) return null;
  const last = messages.at(-1) || {};
  return {
    pane: state.pane,
    readOnly: Boolean(state.settingsConversationReturn?.readOnly),
    conversation,
    revision: conversation.revision,
    updatedAt: conversation.updatedAt,
    messageCount: messages.length,
    lastMessageId: last.id,
    lastMessageDelivery: last.delivery,
    lastMessageRuntimeState: last.runtimeState,
    findOpen: state.conversationFindOpen,
    findQuery: state.conversationFindQuery,
    findIndex: state.conversationFindIndex,
    contextSelectionRecords: state.contextSelectionRecords,
    productSettings: state.productSettings,
    settingsCapabilities: state.settingsCapabilities,
    reminders: state.reminders,
    gallerySelection: assistantGallerySelectionKey(),
  };
}

function canRetainTranscript(previous, next) {
  return previous && next
    && previous.pane === next.pane
    && previous.readOnly === next.readOnly
    && previous.conversation === next.conversation
    && previous.revision === next.revision
    && previous.updatedAt === next.updatedAt
    && previous.messageCount === next.messageCount
    && previous.lastMessageId === next.lastMessageId
    && previous.lastMessageDelivery === next.lastMessageDelivery
    && previous.lastMessageRuntimeState === next.lastMessageRuntimeState
    && previous.findOpen === next.findOpen
    && previous.findQuery === next.findQuery
    && previous.findIndex === next.findIndex
    && previous.contextSelectionRecords === next.contextSelectionRecords
    && previous.productSettings === next.productSettings
    && previous.settingsCapabilities === next.settingsCapabilities
    && previous.reminders === next.reminders
    && previous.gallerySelection === next.gallerySelection;
}

function currentSidebarRetention(data) {
  if (state.pane !== 'chat' || state.searchPanel || state.temporaryConversation || state.p6eAcceptance.enabled) return null;
  return {
    data,
    selectedConversationId: state.selectedConversationId,
    sidebarOpen: state.sidebarOpen,
    railCollapsed: state.railCollapsed,
    profileOpen: state.profileOpen,
    sidebarConversationList: state.sidebarConversationList,
    cloudConversations: state.cloudConversations,
    batchEditing: state.batchEditing,
    batchSelectedConversationKeys: state.batchSelectedConversationKeys,
    chatSearch: state.chatSearch,
    searchHistory: state.searchHistory,
    searchHistoryOpen: state.searchHistoryOpen,
    favoriteConversationIds: state.favoriteConversationIds,
    syncedConversationKeys: state.accountSync?.syncedConversationKeys || [],
    unreadConversationIds: state.unreadConversationIds,
    manualUnreadAtMs: state.manualUnreadAtMs,
  };
}

function sameSetExceptId(previous, next, exceptId) {
  const left = previous || new Set();
  const right = next || new Set();
  for (const value of left) if (value !== exceptId && !right.has(value)) return false;
  for (const value of right) if (value !== exceptId && !left.has(value)) return false;
  return true;
}

function sameMapEntries(previous, next) {
  if (previous === next) return true;
  if (!previous || !next || previous.size !== next.size) return false;
  for (const [key, value] of previous) if (next.get(key) !== value) return false;
  return true;
}

function canRetainSidebar(previous, next) {
  return previous && next
    && previous.data === next.data
    && previous.sidebarOpen === next.sidebarOpen
    && previous.railCollapsed === next.railCollapsed
    && previous.profileOpen === next.profileOpen
    // Cloud rows participate in the same sidebar projection as local rows.
    // A new cloud array must therefore invalidate the retained list.
    && previous.sidebarConversationList === next.sidebarConversationList
    && previous.cloudConversations === next.cloudConversations
    && previous.batchEditing === next.batchEditing
    && previous.batchSelectedConversationKeys === next.batchSelectedConversationKeys
    && previous.chatSearch === next.chatSearch
    && previous.searchHistory === next.searchHistory
    && previous.searchHistoryOpen === next.searchHistoryOpen
    && previous.favoriteConversationIds === next.favoriteConversationIds
    && previous.syncedConversationKeys === next.syncedConversationKeys
    // Opening a conversation clears only its automatic unread dot. That is a
    // local row patch, not a reason to sort and recreate every sidebar row.
    && sameSetExceptId(previous.unreadConversationIds, next.unreadConversationIds, next.selectedConversationId)
    // Manual unread changes ordering, so it deliberately remains a full list
    // update instead of risking a stale recency order.
    && sameMapEntries(previous.manualUnreadAtMs, next.manualUnreadAtMs);
}

function patchRetainedSidebarRows() {
  const showAutomaticUnread = state.productSettings.unreadIndicators !== false;
  document.querySelectorAll('.chat-history-row[data-conversation-row]').forEach(row => {
    const conversationId = row.dataset.id;
    row.classList.toggle('selected', conversationId === state.selectedConversationId);
    const line = row.querySelector('.chat-history-title-line');
    const manualUnread = Number(state.manualUnreadAtMs?.get?.(conversationId) || 0) > 0;
    const unread = manualUnread || (showAutomaticUnread && state.unreadConversationIds.has(conversationId));
    const indicator = line?.querySelector('.chat-history-unread');
    if (!unread) {
      indicator?.remove();
      return;
    }
    if (indicator) {
      indicator.setAttribute('aria-label', manualUnread ? '已标记未读' : '有未查看的新内容');
      return;
    }
    const marker = document.createElement('i');
    marker.className = 'chat-history-unread';
    marker.setAttribute('aria-label', manualUnread ? '已标记未读' : '有未查看的新内容');
    line?.insertBefore(marker, line.querySelector('.chat-history-running, .chat-history-title') || null);
  });
}

function scheduleSettingsSearchRender() {
  if (settingsSearchRenderFrame !== null) cancelAnimationFrame(settingsSearchRenderFrame);
  settingsSearchRenderFrame = requestAnimationFrame(() => {
    settingsSearchRenderFrame = null;
    render();
  });
}

function cancelConversationFindRender() {
  conversationFindInputController.cancel();
}

function scheduleConversationFindRender({ isComposing = false } = {}) {
  // Matching and highlighted Markdown both touch every message. Keep the field
  // native through an IME composition and the user's brief typing pause, then
  // commit one settled query instead of rebuilding a long transcript mid-word.
  conversationFindInputController.onInput({ value: state.conversationFindQuery, isComposing });
}

function paintSearchCategorySelection(category) {
  const tabs = document.querySelectorAll('.desktop-search-tabs [data-action="set-search-category"]');
  if (!tabs.length) return false;
  for (const tab of tabs) {
    const selected = tab.dataset.category === category;
    tab.classList.toggle('selected', selected);
    tab.setAttribute('aria-selected', String(selected));
  }
  return true;
}

function selectFullSearchCategory(category) {
  category ||= 'all';
  if (state.searchCategory === category && !state.searchError) return;
  // Cancel the native SQLite statement before yielding a frame, not merely
  // its renderer result after it has already finished.
  supersedeFullSearch();
  state.searchCategory = category;
  state.searchFileTypeOpen = false;
  state.searchScrollSnapshot = null;
  paintSearchCategorySelection(category);
  if (fullSearchCategoryFrame !== null) cancelAnimationFrame(fullSearchCategoryFrame);
  fullSearchCategoryFrame = requestAnimationFrame(() => {
    fullSearchCategoryFrame = null;
    void runFullSearch({ inPlace: true });
  });
}

// Desktop pointer activation must not wait for a click on a node that an
// asynchronous result can replace between pointerdown and pointerup.
document.addEventListener('pointerdown', event => {
  const tab = event.target.closest?.('[data-action="set-search-category"]');
  if (!tab || event.button !== 0 || event.pointerType === 'touch') return;
  event.preventDefault();
  event.stopImmediatePropagation();
  selectFullSearchCategory(tab.dataset.category);
}, true);

function paintSearchLoadingState(category) {
  if (!paintSearchCategorySelection(category)) return false;
  const results = document.querySelector('[data-search-scroll-owner]');
  if (!results) return false;
  results.setAttribute('aria-busy', 'true');
  results.innerHTML = '<div class="desktop-search-state" role="status"><span class="desktop-search-spinner"></span><strong>正在读取本机索引…</strong></div>';
  return true;
}

function cancelDesktopLocalSearch(requestId) {
  if (native && requestId > 0) void invoke('cancel_desktop_local_search', { requestId }).catch(() => {});
}

function supersedeFullSearch() {
  fullSearchGeneration += 1;
  const requestId = ++fullSearchRequestId;
  cancelDesktopLocalSearch(requestId);
  return requestId;
}

function withFullSearchDeadline(promise, requestId, timeoutMillis = 8_000) {
  let timer = null;
  const timeout = new Promise((_, reject) => {
    timer = window.setTimeout(() => {
      cancelDesktopLocalSearch(requestId);
      reject(new Error('本机索引读取超时，请重试。'));
    }, timeoutMillis);
  });
  return Promise.race([promise, timeout]).finally(() => {
    if (timer !== null) window.clearTimeout(timer);
  });
}

let fullSearchRepairPending = false;
const fullSearchResultCache = createSearchResultCache();
let fullSearchCacheScope = '';
async function runFullSearch({ recordHistory = false, restoreScroll = false, inPlace = false, append = false } = {}) {
  const generation = ++fullSearchGeneration;
  const requestId = ++fullSearchRequestId;
  // This command may arrive after the query command. Cancel only older
  // requests so that out-of-order IPC can never abort this request itself.
  cancelDesktopLocalSearch(requestId - 1);
  const cacheScope = JSON.stringify([state.workspaces, state.current?.summary]);
  if (cacheScope !== fullSearchCacheScope) { fullSearchResultCache.clear(); fullSearchCacheScope = cacheScope; }
  const offset = append ? (state.searchPage?.hits?.length || 0) : 0;
  const searchArgs = { query: state.chatSearch, category: state.searchCategory || 'all', sortMode: state.searchSortMode, fileType: state.searchCategory === 'file' ? state.searchFileType : 'all', offset };
  const cachedPage = native ? fullSearchResultCache.get(searchArgs) : null;
  state.searchPanel = true;
  state.searchLoading = !append && !cachedPage;
  state.searchLoadingMore = append && !cachedPage;
  if (cachedPage) {
    state.searchPage = append ? { ...cachedPage, hits: [...(state.searchPage?.hits || []), ...(cachedPage.hits || [])] } : cachedPage;
    state.searchResults = state.searchPage.hits;
  }
  state.searchError = '';
  state.searchHistoryHighlighted = bestSearchHistoryMatch(state.chatSearch, state.searchHistory);
  // A category is a local, immediate selection.  Keep this existing search
  // shell alive for the first frame instead of rebuilding it before SQLite has
  // returned, so the clicked pill can paint without DOM teardown.
  const paintedInPlace = !append && !cachedPage && inPlace && paintSearchLoadingState(state.searchCategory);
  if (!paintedInPlace) render();
  if (!native) {
    state.searchLoading = false;
    state.searchPage = createC08BrowserSearchPage({
      query: state.chatSearch,
      category: state.searchCategory || 'all',
      sortMode: state.searchSortMode,
      fileType: state.searchFileType,
    });
    state.searchResults = state.searchPage.hits;
    state.searchError = '';
    state.searchEvidenceLabel = '只读视觉样本 · 不代表 Desktop SQLite 实值';
    render();
    queueMicrotask(() => {
      const input = document.querySelector('#full-search-input');
      if (!restoreScroll && !inPlace) input?.focus({ preventScroll: true });
      restoreFullSearchScroll();
    });
    return;
  }
  try {
    // The native request id cancels any older SQLite statement. Generation is
    // retained only as a UI guard for already-delivered renderer work.
    if (generation !== fullSearchGeneration) return;
    const pending = invoke('query_desktop_local_index', { args: {
      query: state.chatSearch,
      category: state.searchCategory || 'all',
      sortMode: state.searchSortMode,
      fileType: state.searchCategory === 'file' ? state.searchFileType : 'all',
      offset,
      recordHistory: false,
      historyWorkspaceId: null,
    }, requestId });
    const page = await withFullSearchDeadline(pending, requestId);
    if (cacheScope === fullSearchCacheScope) fullSearchResultCache.set(searchArgs, page);
    if (generation !== fullSearchGeneration) return;
    if (cachedPage || append) rememberFullSearchScroll();
    state.searchPage = append ? { ...page, hits: [...(state.searchPage?.hits || []), ...(page.hits || [])] } : page;
    state.searchResults = state.searchPage.hits || [];
    state.searchLoading = false;
    state.searchLoadingMore = false;
    state.searchError = '';
    state.searchEvidenceLabel = page.indexCurrent === false ? '本机索引正在后台恢复；当前显示的是已就绪的结果。' : '';
    state.searchHistoryHighlighted = bestSearchHistoryMatch(state.chatSearch, state.searchHistory);
    // A main-chat attachment locator keeps the complete search canvas. Page forward until
    // the exact stable tuple is present; it never turns the attachment name into a query.
    if (state.searchRevealTarget && !fullSearchRevealHit()) {
      if (page.hasMore) void runFullSearch({ append: true, restoreScroll: true });
      else {
        state.searchRevealTarget = null;
        state.searchEvidenceLabel = '目标附件已不在当前本机索引中；完整搜索结果保持不变。';
      }
    }
    render();
    queueMicrotask(() => {
      const input = document.querySelector('#full-search-input');
      if (!restoreScroll && !inPlace) input?.focus({ preventScroll: true });
      if (inPlace) document.querySelector(`.desktop-search-tabs [data-action="set-search-category"][data-category="${CSS.escape(state.searchCategory)}"]`)?.focus({ preventScroll: true });
      restoreFullSearchScroll();
      revealFullSearchAttachment();
    });
    if (recordHistory && !append && state.current && state.chatSearch.trim()) {
      void invoke('record_desktop_local_search_history', { workspaceId: state.current.summary.id, query: state.chatSearch }).then(history => {
        if (generation !== fullSearchGeneration) return;
        state.searchHistory = history;
        state.searchHistoryHighlighted = bestSearchHistoryMatch(state.chatSearch, history);
        render();
      }).catch(() => {});
    }
    if (page.indexCurrent === false && !fullSearchRepairPending) {
      fullSearchRepairPending = true;
      void invoke('repair_desktop_local_search_index_if_stale').then(repaired => {
        if (repaired && generation === fullSearchGeneration && state.searchPanel) void runFullSearch({ restoreScroll: true, inPlace: true });
      }).catch(() => {}).finally(() => { fullSearchRepairPending = false; });
    }
  } catch (error) {
    if (generation !== fullSearchGeneration) return;
    state.searchLoading = false;
    state.searchLoadingMore = false;
    state.searchError = cachedPage ? '' : String(error);
    state.searchEvidenceLabel = cachedPage ? '当前为上次结果，本次更新未完成。' : '';
    render();
  }
}

function scheduleFullSearch() {
  supersedeFullSearch();
  if (fullSearchDebounce !== null) window.clearTimeout(fullSearchDebounce);
  fullSearchDebounce = window.setTimeout(() => { fullSearchDebounce = null; void runFullSearch(); }, state.chatSearch.trim() ? 180 : 0);
}

function openFullSearch(category = 'all') {
  state.searchCategory = category;
  state.searchSortMode = 'default';
  state.searchFileType = 'all';
  state.searchPanel = true;
  state.searchHistoryOpen = false;
  state.searchHistoryManuallyOpened = false;
  state.searchScrollSnapshot = null;
  const nextGeneration = fullSearchGeneration + 1;
  void runFullSearch();
  if (native && state.current) {
    void invoke('read_desktop_local_search_history', { workspaceId: state.current.summary.id })
      .then(history => {
        if (!state.searchPanel || fullSearchGeneration !== nextGeneration) return;
        state.searchHistory = history;
        state.searchHistoryHighlighted = bestSearchHistoryMatch(state.chatSearch, history);
        render();
      })
      .catch(() => {});
  }
}

function rememberFullSearchScroll(entryId = null) {
  const owner = document.querySelector('[data-search-scroll-owner]');
  const target = entryId && owner ? owner.querySelector(`[data-search-entry-id="${CSS.escape(entryId)}"], [data-entry-id="${CSS.escape(entryId)}"]`) : null;
  state.searchScrollSnapshot = { top: owner?.scrollTop || 0, entryId, entryOffset: target ? target.offsetTop - owner.scrollTop : null };
}

function restoreFullSearchScroll() {
  const owner = document.querySelector('[data-search-scroll-owner]');
  if (!owner || !state.searchScrollSnapshot) return;
  const target = state.searchScrollSnapshot.entryId ? owner.querySelector(`[data-search-entry-id="${CSS.escape(state.searchScrollSnapshot.entryId)}"], [data-entry-id="${CSS.escape(state.searchScrollSnapshot.entryId)}"]`) : null;
  if (target && Number.isFinite(state.searchScrollSnapshot.entryOffset)) owner.scrollTop = target.offsetTop - state.searchScrollSnapshot.entryOffset;
  else owner.scrollTop = state.searchScrollSnapshot.top;
}

async function submitLocalSearch() {
  state.searchHistoryOpen = false;
  state.searchHistoryManuallyOpened = false;
  await runFullSearch({ recordHistory: true });
}

function fullSearchHit(entryId) {
  return state.searchPage?.hits?.find(hit => hit.entryId === entryId) || null;
}

function fullSearchRevealHit(target = state.searchRevealTarget) {
  if (!target) return null;
  return state.searchPage?.hits?.find(hit =>
    hit.workspaceId === target.workspaceId &&
    hit.conversationId === target.conversationId &&
    hit.messageId === target.messageId &&
    hit.attachmentId === target.attachmentId,
  ) || null;
}

function revealFullSearchAttachment(target = state.searchRevealTarget) {
  const hit = fullSearchRevealHit(target);
  if (!hit) return false;
  queueMicrotask(() => {
    if (state.searchRevealTarget !== target || !state.searchPanel) return;
    const card = document.querySelector(`[data-search-entry-id="${CSS.escape(hit.entryId)}"]`);
    const owner = document.querySelector('[data-search-scroll-owner]');
    if (!card || !owner) return;
    owner.scrollTo({ top: Math.max(0, card.offsetTop - (owner.clientHeight - card.offsetHeight) / 2), behavior: 'smooth' });
    card.classList.remove('search-anchor-flash');
    void card.offsetWidth;
    card.classList.add('search-anchor-flash');
    window.setTimeout(() => card.classList.remove('search-anchor-flash'), 1600);
    if (state.searchRevealTarget === target) state.searchRevealTarget = null;
  });
  return true;
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
    // A generated-image group keeps every image surface mounted but only one visible. Select the
    // indexed asset before rendering so an image search returns to that exact image, not merely
    // the surrounding assistant message.
    if (hit.messageId && hit.attachmentId) state.assistantImageSelections.set(hit.messageId, hit.attachmentId);
    state.searchLocatedArchivedConversationId = hit.archived ? hit.conversationId : null;
    state.searchReturnActive = true;
    state.searchPanel = false;
    state.pane = 'chat';
    render();
    queueMicrotask(() => {
      if (state.pane !== 'chat' || state.selectedConversationId !== hit.conversationId) return;
      const { target } = resolveSearchResultAnchor(document, hit);
      target?.scrollIntoView({ block: 'center', inline: 'nearest' });
      if (target) { target.classList.add('search-anchor-flash'); window.setTimeout(() => target.classList.remove('search-anchor-flash'), 1600); }
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
  return openAttachmentPreview(hit, hit.workspaceId);
}
const icons = { check: 'M20 6 9 17l-5-5', workspace: 'M3 5.5A2.5 2.5 0 0 1 5.5 3H10l2 2h6.5A2.5 2.5 0 0 1 21 7.5v10a2.5 2.5 0 0 1-2.5 2.5h-13A2.5 2.5 0 0 1 3 17.5z', conversation: 'M4 5.5A2.5 2.5 0 0 1 6.5 3h11A2.5 2.5 0 0 1 20 5.5v8a2.5 2.5 0 0 1-2.5 2.5H11L7 20v-4H6.5A2.5 2.5 0 0 1 4 13.5z', knowledge: 'M5 3.5h11A3 3 0 0 1 19 6.5v13l-6.5-3-6.5 3v-13a3 3 0 0 1 3-3z', import: 'M12 3v12m0 0 4-4m-4 4-4-4M5 17v3h14v-3', export: 'M12 15V3m0 0 4 4m-4-4L8 7M5 17v3h14v-3', download: 'M12 3v12m0 0 4-4m-4 4-4-4M5 19h14', copy: 'M9 8V5a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-3M5 9h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2z', close: 'M6 6l12 12M18 6 6 18', chevronLeft: 'm14.5 5-7 7 7 7', chevronRight: 'm9.5 5 7 7-7 7', undo: 'M9 7 4 12l5 5M5 12h9a5 5 0 1 1 0 10', redo: 'm15 7 5 5-5 5m4-5h-9a5 5 0 1 0 0 10', trash: 'M4 7h16M10 11v6m4-6v6M9 7l1-2h4l1 2M6 7l1 14h10l1-14', plus: 'M12 5v14M5 12h14', edit: 'm4 16 9-9 3 3-9 9H4zM14 6l2-2 3 3-2 2', info: 'M12 17v-6m0-3.5v.01M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z', branch: 'M5 4v4c0 1.1.9 2 2 2h12m0 0-3-3m3 3-3 3M5 4v12c0 1.1.9 2 2 2h12m0 0-3-3m3 3-3 3', link: 'M10 13a5 5 0 0 0 7.1.1l2-2a5 5 0 0 0-7.1-7.1l-1.1 1.1m3.1 5.9a5 5 0 0 0-7.1-.1l-2 2A5 5 0 0 0 9 20l1.1-1.1' };
function workspace() { return state.current || (!native ? fixture : null); }
function selectWorkspaceDefaultConversation(data = workspace()) {
  state.selectedConversationId = data ? activeConversations(data)[0]?.id || null : null;
}
function active(data, key) { return data.exchange[key].filter(item => key === 'relations' ? item.status === 'ACTIVE' : item.status !== 'DELETED' && !item.archived && !item.deleted); }
function dialog() {
  if (!state.dialog) return '';
  if (state.dialog?.kind === 'source-links') {
    const sources = Array.isArray(state.dialog.sources) ? state.dialog.sources : [];
    const sourceRows = sources.map(source => {
      let href = null;
      try {
        const parsed = new URL(String(source?.href || ''));
        href = ['http:', 'https:'].includes(parsed.protocol) ? parsed.href : null;
      } catch { /* invalid imported source is not rendered as an active destination */ }
      if (!href) return '';
      const label = sourceDisplayTitle({ href, label: source?.label });
      return `<button type="button" class="source-link-row" data-action="open-source-link" data-href="${escape(href)}" aria-label="在浏览器打开来源：${escape(label)}">${sharedIcon(sharedIcons.globe, '打开来源')}<span><strong>${escape(label)}</strong><small>${escape(href)}</small></span></button>`;
    }).filter(Boolean).join('');
    return `<div class="scrim"><section class="dialog source-links-dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><h2 id="dialog-title">来源网站</h2><div class="source-links-list">${sourceRows || '<p class="empty-copy">没有可安全打开的来源网站。</p>'}</div><div class="dialog-actions"><button class="primary" data-action="close-dialog">关闭</button></div></section></div>`;
  }
  if (state.dialog?.kind === 'model-credential-saved') return `<div class="scrim"><section class="dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><h2 id="dialog-title">API Key 已保存</h2><p>API Key 已安全保存在本机。尚未测试连接；请点击“测试连接”确认 API Key 是否可用。</p><div class="dialog-actions"><button class="primary" data-action="close-dialog">知道了</button></div></section></div>`;
  if (state.dialog === 'import') return `<div class="scrim"><section class="dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="关闭">${icon(icons.close, '关闭')}</button><p class="overline">严格预检已通过</p><h2 id="dialog-title">导入为新的独立工作区</h2><p>不会合并或覆盖。正文仅作为不执行的文本 IR。</p><label>新工作区名称<input id="workspace-title" maxlength="120" value="导入工作区"></label><div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary" data-action="confirm-import">导入</button></div></section></div>`;
  if (state.dialog.kind === 'project') { const item = state.dialog.item; return `<div class="scrim"><section class="dialog edit-dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="取消">${icon(icons.close, '关闭')}</button><p class="overline">本地 revision 写入</p><h2 id="dialog-title">${item ? '编辑 Project' : '新建 Project'}</h2><p>保存由 Rust 检查 expected revision；冲突不会覆盖现有对象。</p><label>名称<input id="project-title" maxlength="120" value="${escape(item?.title || '')}"></label><label>说明<textarea id="project-description" maxlength="2000">${escape(item?.description || '')}</textarea></label><div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary" data-action="save-project">保存 ⌘S</button></div></section></div>`; }
  if (state.dialog.kind === 'knowledge') { const item = state.dialog.item; return `<div class="scrim"><section class="dialog edit-dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="取消">${icon(icons.close, '关闭')}</button><p class="overline">本地 revision 写入</p><h2 id="dialog-title">${item ? '编辑 Knowledge' : '新建 Knowledge'}</h2><p>保存时由 Rust 检查 expected revision；冲突不会覆盖当前记录。</p><label>标题<input id="knowledge-title" maxlength="120" value="${escape(item?.title || '')}"></label><label>正文<textarea id="knowledge-body" maxlength="2000000">${escape(item?.body || '')}</textarea></label><label>标签（逗号分隔）<input id="knowledge-tags" value="${escape((item?.tags || []).join(', '))}"></label><div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary" data-action="save-knowledge">保存 ⌘S</button></div></section></div>`; }
  if (state.dialog?.kind === 'history-knowledge-review') { const item = state.dialog.item; return `<div class="scrim"><section class="dialog edit-dialog history-knowledge-review" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="关闭">${icon(icons.close, '关闭')}</button><p class="overline">历史对话自动整理 · 待核对候选</p><h2 id="dialog-title">核对资料候选</h2><p>候选尚未进入长期 Memory 或 Knowledge。确认后只保存你当前看到并可编辑的内容；原对话不会改写。</p><label>标题<input id="history-candidate-title" maxlength="120" value="${escape(item.title || '')}"></label><label>候选内容<textarea id="history-candidate-body" maxlength="2000000">${escape(item.body || '')}</textarea></label><label>标签（逗号分隔）<input id="history-candidate-tags" value="${escape((item.tags || []).join(', '))}"></label><p class="security-note">来源会话 ${escape(item.conversationId)} · 消息 ${Number(item.sourceMessageIds?.length || 0)} 条 · ${escape(item.providerId || '未发送')} / ${escape(item.actualModelId || item.requestedModelId || '未知模型')} · checkpoint ${escape(short(item.checkpointId))}</p><div class="dialog-actions"><button data-action="reject-history-knowledge" data-id="${escape(item.candidateId)}">驳回</button><button class="primary" data-action="accept-history-knowledge" data-id="${escape(item.candidateId)}">确认保存为本地知识</button></div></section></div>`; }
  if (state.dialog?.kind === 'reminder-draft') { const item = state.dialog.item; const option = (value, label) => `<option value="${value}" ${item.scheduleKind === value ? 'selected' : ''}>${label}</option>`; return `<div class="scrim"><section class="dialog edit-dialog reminder-draft-dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="关闭">${icon(icons.close, '关闭')}</button><p class="overline">提醒草案 · 必须确认</p><h2 id="dialog-title">核对计划与监控</h2><p>模型只整理草案，不会替你创建计划。请核对对象、条件、频率、时区与错过策略。</p><label>计划名称<input id="reminder-title" maxlength="40" value="${escape(item.title || '')}" placeholder="例如：检查项目状态"></label><label>执行说明<textarea id="reminder-instruction" maxlength="8000" rows="5" placeholder="写清要检查什么、何时算完成">${escape(item.instruction || '')}</textarea></label><label>执行频率<select id="reminder-schedule-kind">${option('ONCE', '一次')}${option('HOURLY', '每小时')}${option('DAILY', '每天')}${option('WEEKLY', '每周')}</select></label><label>本地时间<input id="reminder-anchor-local" type="datetime-local" value="${escape(item.anchorLocal || '')}"></label><label>时区<input id="reminder-timezone" maxlength="64" value="${escape(item.timezoneId || '')}" placeholder="Asia/Shanghai"></label><label>错过执行窗口后<select id="reminder-missed-policy"><option value="RUN_ONCE" ${item.missedPolicy === 'RUN_ONCE' ? 'selected' : ''}>恢复后执行一次</option><option value="SKIP" ${item.missedPolicy === 'SKIP' ? 'selected' : ''}>跳过本次</option></select></label><p class="security-note">${escape(item.providerId || '手工草案')} · ${escape(item.actualModelId || item.requestedModelId || '未调用模型')} · ${item.chargeMicros == null ? '费用未报告' : `$${(item.chargeMicros / 1_000_000).toFixed(6)}`}</p><div class="dialog-actions"><button data-action="reject-reminder-draft" data-id="${escape(item.draftId)}">拒绝</button><button class="primary" data-action="confirm-reminder-draft">确认并创建计划</button></div></section></div>`; }
  if (state.dialog?.kind === 'reminder-plan') { const item = state.dialog.item; const option = (value, label) => `<option value="${value}" ${item.scheduleKind === value ? 'selected' : ''}>${label}</option>`; return `<div class="scrim"><section class="dialog edit-dialog reminder-draft-dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="关闭">${icon(icons.close, '关闭')}</button><p class="overline">已确认计划 · 本机更新</p><h2 id="dialog-title">编辑计划与监控</h2><p>保存会保留既有执行记录，并按新频率、时间和时区重新计算下一次执行。</p><label>计划名称<input id="reminder-title" maxlength="40" value="${escape(item.title || '')}"></label><label>执行说明<textarea id="reminder-instruction" maxlength="8000" rows="5">${escape(item.instruction || '')}</textarea></label><label>执行频率<select id="reminder-schedule-kind">${option('ONCE', '一次')}${option('HOURLY', '每小时')}${option('DAILY', '每天')}${option('WEEKLY', '每周')}</select></label><label>本地时间<input id="reminder-anchor-local" type="datetime-local" value="${escape(item.anchorLocal || '')}"></label><label>时区<input id="reminder-timezone" maxlength="64" value="${escape(item.timezoneId || '')}"></label><label>错过执行窗口后<select id="reminder-missed-policy"><option value="RUN_ONCE" ${item.missedPolicy === 'RUN_ONCE' ? 'selected' : ''}>恢复后执行一次</option><option value="SKIP" ${item.missedPolicy === 'SKIP' ? 'selected' : ''}>跳过本次</option></select></label><div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary" data-action="confirm-edit-reminder-plan">保存计划</button></div></section></div>`; }
  if (state.dialog?.kind === 'memory-summary-about') return `<div class="scrim"><section class="dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><h2 id="dialog-title">关于记忆</h2><p>这里显示已保存在本机的记忆摘要。启用后，相关内容可用于回答。</p><div class="dialog-actions"><button class="primary" data-action="close-dialog">知道了</button></div></section></div>`;
if (state.dialog?.kind === 'memory-summary-editor') return `<div class="scrim custom-instructions-scrim"><section class="dialog memory-summary-editor" role="dialog" aria-modal="true" aria-labelledby="memory-summary-editor-title"><header><button data-action="close-dialog">取消</button><h2 id="memory-summary-editor-title">编辑记忆摘要</h2><button class="primary" data-action="save-memory-summary-editor" ${String(state.dialog.value || '').trim() ? '' : 'disabled'}>保存</button></header><label><span>完整摘要</span><textarea id="memory-summary-editor" maxlength="2000000" placeholder="输入或粘贴新的记忆摘要">${escape(state.dialog.value || '')}</textarea><small>${String(state.dialog.value || '').length} / 2000000 字</small></label></section></div>`;
  if (state.dialog?.kind === 'memory-summary-delete') return `<div class="scrim"><section class="dialog" role="alertdialog" aria-modal="true"><h2>删除记忆？</h2><p>删除当前记忆摘要；聊天、文件和记忆开关不受影响。</p><div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary danger" data-action="confirm-delete-memory-summary">删除</button></div></section></div>`;
  if (state.dialog?.kind === 'memory-summary-disable') return `<div class="scrim"><section class="dialog" role="alertdialog" aria-modal="true"><h2>关闭记忆摘要生成和应用？</h2><p>停止生成和使用记忆摘要；已保存内容不会删除。</p><div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary danger" data-action="confirm-disable-memory-summary">关闭</button></div></section></div>`;
  if (state.dialog?.kind === 'model-settings-feedback') return `<div class="scrim"><section class="dialog" role="alertdialog" aria-modal="true" aria-labelledby="model-feedback-title"><h2 id="model-feedback-title">${state.dialog.failed ? '操作未完成' : '操作完成'}</h2><p>${escape(state.dialog.message)}</p><div class="dialog-actions"><button class="primary" data-action="close-dialog">知道了</button></div></section></div>`;
  if (state.dialog?.kind === 'custom-instructions-fullscreen') return `<div class="scrim custom-instructions-scrim"><section class="dialog custom-instructions-fullscreen" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><header><button class="icon-button" data-action="close-dialog" aria-label="关闭全屏自定义指令编辑">${icon(icons.close, '关闭')}</button><h2 id="dialog-title">自定义指令</h2><button class="icon-button" data-action="save-custom-instructions-fullscreen" aria-label="保存自定义指令" title="保存">${icon(icons.check, '保存')}</button></header><label class="android-settings-field"><span>自定义指令</span><textarea id="personalization-instructions-fullscreen" maxlength="${CUSTOM_INSTRUCTIONS_MAX_LENGTH}" placeholder="希望南枫 AI 如何回答你">${escape(state.personalizationDraft.customInstructions || '')}</textarea><small>${String(state.personalizationDraft.customInstructions || '').length} / ${CUSTOM_INSTRUCTIONS_MAX_LENGTH} 字</small></label></section></div>`;
  if (state.dialog?.kind === 'reminder-delete') { const item = state.dialog.item; return `<div class="scrim"><section class="dialog" role="alertdialog" aria-modal="true"><h2>删除这个提醒计划？</h2><p>将删除“${escape(item.title)}”及其本机执行记录；操作不可撤销，运行中的请求会请求停止。</p><div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary danger" data-action="confirm-delete-reminder-plan" data-id="${escape(item.planId)}">删除计划</button></div></section></div>`; }
  if (state.dialog.kind === 'memory') { const item = state.dialog.item; return `<div class="scrim"><section class="dialog edit-dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="取消">${icon(icons.close, '关闭')}</button><p class="overline">本地 revision 写入</p><h2 id="dialog-title">${item ? '编辑 Memory' : '新建 Memory'}</h2><p>Memory 仅是本地文本 IR，不会进入 Prompt 或网络。</p><label>正文<textarea id="memory-body" maxlength="2000000">${escape(item?.body || '')}</textarea></label><div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary" data-action="save-memory">保存 ⌘S</button></div></section></div>`; }
  if (state.dialog.kind === 'relation') { const data = workspace(); const items = active(data, 'knowledge'); const options = items.map(item => `<option value="${escape(item.id)}">${escape(item.title)} · r${item.revision}</option>`).join(''); return `<div class="scrim"><section class="dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="取消">${icon(icons.close, '关闭')}</button><p class="overline">显式本地 relation</p><h2 id="dialog-title">建立 Knowledge 关系</h2><p>仅同一活动 scope 的两条 Knowledge 可建立；不会自动关联或去重。</p><label>来源<select id="relation-from">${options}</select></label><label>目标<select id="relation-to">${options}</select></label><label>类型<select id="relation-kind"><option value="RELATED">RELATED（对称）</option><option value="DERIVED_FROM">DERIVED_FROM</option><option value="REFERENCES">REFERENCES</option></select></label><div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary" data-action="save-relation" ${items.length < 2 ? 'disabled' : ''}>建立关系</button></div></section></div>`; }
  if (state.dialog?.kind === 'assistant-answer-information') return renderAnswerInformation(state.dialog.record);
  if (state.dialog === 'metadata') return `<div class="scrim"><section class="dialog edit-dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="关闭">${icon(icons.close, '关闭')}</button><p class="overline">仅本地安全 metadata</p><h2 id="dialog-title">模型与成本 metadata</h2><p>不存 Key、不联网取 catalog；价格仅标记为 fixture、手工或未知，绝不当作真实费用。</p><label>Provider ID<input id="provider-id" value="provider-local"></label><label>Model ID<input id="model-id" value="model-manual"></label><label>价格版本<input id="price-version" value="manual-v1"></label><label>币种<input id="price-currency" value="CNY"></label><div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary" data-action="save-metadata">保存 metadata</button></div></section></div>`;
  if (state.dialog === 'recycle') return `<div class="scrim"><section class="dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="关闭">${icon(icons.close, '关闭')}</button><p class="overline">本地软删除</p><h2 id="dialog-title">回收站</h2><p>此处恢复通用软删除对象；会话的恢复与永久删除在“设置 → 对话管理 → 回收站”。恢复将生成新 revision。</p><div class="recycle-list">${state.history.recycleBin.length ? state.history.recycleBin.map(item => `<div><span>${escape(item.entity)} · ${escape(item.title)}</span><button data-action="restore" data-entity="${escape(item.entity)}" data-id="${escape(item.id)}" data-revision="${item.revision}">恢复</button></div>`).join('') : '<p class="empty-copy">暂无可恢复对象。</p>'}</div><div class="dialog-actions"><button data-action="close-dialog">关闭</button></div></section></div>`;
  if (state.dialog === 'about') return `<div class="scrim"><section class="dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="关闭">${icon(icons.close, '关闭')}</button><p class="overline">Desktop 本机交付摘要</p><h2 id="dialog-title">关于南枫 AI Desktop</h2><dl class="about-list"><dt>版本</dt><dd>0.6.0-p6d-dev</dd><dt>账号与同步</dt><dd>尚未配置 · 离线可用；无浏览器登录、无 HTTP、无同步队列</dd><dt>签名</dt><dd>ad-hoc 开发签名，未 notarized</dd><dt>最低系统</dt><dd>macOS 11 或更高</dd><dt>本地数据</dt><dd>仅 app-private 容器；此处不显示路径或数据库文件</dd><dt>更新</dt><dd>本地静态状态；未检查网络</dd></dl><p>卸载应用不会主动删除用户本地数据；清除数据必须通过未来独立的安全流程，不暴露 SQLite 文件。</p><div class="dialog-actions"><button data-action="close-dialog">关闭</button></div></section></div>`;
  if (state.dialog?.kind === 'search-attachment-actions') return renderSearchAttachmentMenu(state.dialog.hit, state.dialog.anchor);
  if (state.dialog?.kind === 'chat-attachment-actions') return renderChatAttachmentMenu(state.dialog.item, state.dialog.anchor);
  if (state.dialog?.kind === 'transcription-delete') { const task = state.transcription.tasks.find(item => item.id === state.dialog.taskId); const busy = state.dialog.submitting; return `<div class="scrim"><section class="dialog" role="alertdialog" aria-modal="true" aria-busy="${busy}"><h2>删除这条转写任务？</h2><p>将删除“${escape(task?.sourceDisplayName || '转写任务')}”的任务记录与时间轴。原文件或结果仍被会话引用时，私有字节不会删除；无引用字节进入安全清理期。</p>${state.dialog.failure ? `<p class="dialog-error" role="alert">${escape(state.dialog.failure)}</p>` : ''}<div class="dialog-actions"><button data-action="close-dialog" ${busy ? 'disabled' : ''}>取消</button><button class="primary danger" data-action="confirm-delete-transcription-task" data-task-id="${escape(state.dialog.taskId)}" ${busy ? 'disabled' : ''}>${busy ? '正在删除…' : '删除任务'}</button></div></section></div>`; }
  if (state.dialog?.kind === 'search-attachment-delete') { const hit = state.dialog.hit; const busy = state.dialog.submitting; return `<div class="scrim"><section class="dialog" role="alertdialog" aria-modal="true" aria-busy="${busy}"><h2>删除此附件引用？</h2><p>只会从这条消息移除“${escape(hit.displayName || '附件')}”。若其他消息仍在引用，私有副本会继续保留；最后一个引用移除后进入安全清理期。</p>${state.dialog.failure ? `<p class="dialog-error" role="alert">${escape(state.dialog.failure)}</p>` : ''}<div class="dialog-actions"><button data-action="close-dialog" ${busy ? 'disabled' : ''}>取消</button><button class="primary danger" data-action="confirm-delete-search-attachment" data-entry-id="${escape(hit.entryId)}" ${busy ? 'disabled' : ''}>${busy ? '正在删除…' : '删除此引用'}</button></div></section></div>`; }
  if (state.dialog?.kind === 'p6k-batch-delete') { const busy = state.dialog.submitting; return `<div class="scrim"><section class="dialog" role="alertdialog" aria-modal="true" aria-busy="${busy}"><h2>删除这个 ZIP 导入批次？</h2><p>将移除该批次导入的本地对话与 ZIP 私有副本。对话的删除标记会长期保留，以后重新导入同一官方会话也不会复活。</p>${state.dialog.failure ? `<p class="dialog-error" role="alert">${escape(state.dialog.failure)}</p>` : ''}<div class="dialog-actions"><button data-action="close-dialog" ${busy ? 'disabled' : ''}>取消</button><button class="primary danger" data-action="confirm-delete-p6k-zip-batch" data-task-id="${escape(state.dialog.taskId)}" ${busy ? 'disabled' : ''}>${busy ? '正在删除…' : '删除批次'}</button></div></section></div>`; }
  if (state.dialog?.kind === 'conversation-rename') return `<div class="scrim"><section class="dialog" role="dialog" aria-modal="true"><label>会话标题<input id="conversation-rename" maxlength="120" value="${escape(state.dialog.title)}"></label><div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary" data-action="save-conversation-rename" data-id="${escape(state.dialog.id)}" data-revision="${state.dialog.revision}">保存</button></div></section></div>`;
  if (state.dialog?.kind === 'conversation-project') { const projects = active(workspace(), 'projects'); const options = [`<option value="">不归入项目</option>`, ...projects.map(project => `<option value="${escape(project.id)}" ${project.id === state.dialog.projectId ? 'selected' : ''}>${escape(project.title)}</option>`)].join(''); return `<div class="scrim"><section class="dialog" role="dialog" aria-modal="true"><h2>项目归属</h2><p>${projects.length ? '选择现有本地项目；切换即为移动，取消不变更。' : '当前没有可选项目。'}</p><label>项目<select id="conversation-project">${options}</select></label><div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary" data-action="save-conversation-project" data-id="${escape(state.dialog.id)}" data-revision="${state.dialog.revision}">保存</button></div></section></div>`; }
  if (state.dialog?.kind === 'conversation-delete') { const busy = state.dialog.submitting; const copy = state.dialog.source === 'archived' ? `“${escape(state.dialog.title)}”将移入回收站，可恢复。` : '删除不同于归档：消息树不会物理删除，可从回收站恢复。'; return `<div class="scrim"><section class="dialog" role="dialog" aria-modal="true" aria-busy="${busy}"><h2>移入回收站？</h2><p>${copy}</p>${state.dialog.failure ? `<p class="dialog-error" role="alert">${escape(state.dialog.failure)}</p>` : ''}<div class="dialog-actions"><button data-action="close-dialog" ${busy ? 'disabled' : ''}>取消</button><button class="primary" data-action="confirm-conversation-delete" data-id="${escape(state.dialog.id)}" data-revision="${state.dialog.revision}" ${busy ? 'disabled' : ''}>${busy ? '正在移入…' : '移入回收站'}</button></div></section></div>`; }
  if (state.dialog?.kind === 'conversation-batch-delete') { const busy = state.dialog.submitting; const count = state.dialog.items?.length || 0; return `<div class="scrim"><section class="dialog" role="dialog" aria-modal="true" aria-busy="${busy}"><h2>移入回收站？</h2><p>${count} 个会话将移入回收站，可恢复。</p>${state.dialog.failure ? `<p class="dialog-error" role="alert">${escape(state.dialog.failure)}</p>` : ''}<div class="dialog-actions"><button data-action="close-dialog" ${busy ? 'disabled' : ''}>取消</button><button class="primary" data-action="confirm-conversation-batch-delete" ${busy ? 'disabled' : ''}>${busy ? '正在移入…' : '移入回收站'}</button></div></section></div>`; }
  if (state.dialog?.kind === 'conversation-permanent-delete') { const busy = state.dialog.submitting; return `<div class="scrim"><section class="dialog" role="dialog" aria-modal="true" aria-busy="${busy}"><h2>永久删除？</h2><p>将永久删除“${escape(state.dialog.title)}”，无法恢复。</p>${state.dialog.failure ? `<p class="dialog-error" role="alert">${escape(state.dialog.failure)}</p>` : ''}<div class="dialog-actions"><button data-action="close-dialog" ${busy ? 'disabled' : ''}>取消</button><button class="primary danger" data-action="confirm-conversation-permanent-delete" data-id="${escape(state.dialog.id)}" data-revision="${state.dialog.revision}" ${busy ? 'disabled' : ''}>${busy ? '正在永久删除…' : '永久删除'}</button></div></section></div>`; }
  if (state.dialog?.kind === 'conversation-bulk-cleanup') { const busy = state.dialog.submitting; const recycle = state.dialog.scope === 'recycle'; return `<div class="scrim"><section class="dialog" role="dialog" aria-modal="true" aria-busy="${busy}"><h2>${recycle ? '清空回收站？' : '清空已归档？'}</h2><p>${recycle ? `将永久删除 ${state.dialog.count} 个会话，无法恢复。` : `${state.dialog.count} 个会话将移入回收站，可恢复。`}</p>${state.dialog.failure ? `<p class="dialog-error" role="alert">${escape(state.dialog.failure)}</p>` : ''}<div class="dialog-actions"><button data-action="close-dialog" ${busy ? 'disabled' : ''}>取消</button><button class="primary ${recycle ? 'danger' : ''}" data-action="confirm-conversation-bulk-cleanup" data-scope="${state.dialog.scope}" ${busy ? 'disabled' : ''}>${busy ? '正在处理…' : recycle ? '永久删除' : '移入回收站'}</button></div></section></div>`; }
  if (state.dialog?.kind === 'local-backup-restart-required') return `<div class="scrim"><section class="dialog" role="alertdialog" aria-modal="true" aria-labelledby="dialog-title"><p class="overline">本机恢复已完成</p><h2 id="dialog-title">请完全重启 App</h2><p>数据库和受控资产已经替换并通过回读。为避免旧 SQLite、页面和任务引用，现请手动完全退出并重新打开 App；不会自动继续任何任务。</p></section></div>`;
  if (state.dialog?.kind === 'privacy-cleanup-scope') return renderLocalDataCleanupScopeDialog();
  if (state.dialog?.kind === 'privacy-cleanup-preview') return renderLocalDataCleanupPreviewDialog(state.dialog, escape);
  return '';
}
function tree(data) { if (!data) return '<div class="tree-empty">还没有本地工作区。<br>先从受控交换包导入。</div>'; const workspaceRows = state.workspaces.map(item => `<button class="tree-row ${item.id === data.summary.id ? 'selected' : ''}" data-action="select-workspace" data-id="${escape(item.id)}"><span>${icon(icons.workspace, '工作区')}</span><span>${escape(item.title)}</span><small>${item.id === data.summary.id ? '当前' : ''}</small></button>`).join(''); return `<div class="tree-section"><p>Workspace</p>${workspaceRows}</div><div class="tree-section"><p>Project <button class="small-add" data-action="new-project" aria-label="新建 Project">+</button></p>${data.exchange.projects.map(item => `<button class="tree-row" data-action="edit-project" data-id="${escape(item.id)}"><span>${icon(icons.workspace, '项目')}</span><span>${escape(item.title)}</span><small>r${item.revision || 0}</small></button>`).join('') || '<span class="tree-muted">暂无 Project</span>'}</div><div class="tree-section"><p>Conversation</p>${data.exchange.conversations.map(item => `<button class="tree-row ${state.pane === 'conversation' ? 'selected' : ''}" data-action="show-conversation"><span>${icon(icons.conversation, '会话')}</span><span>${escape(item.title)}</span><small>${item.messages.length}</small></button>`).join('')}</div><div class="tree-section"><p>Knowledge</p><button class="tree-row ${state.pane === 'knowledge' ? 'selected' : ''}" data-action="show-knowledge"><span>${icon(icons.knowledge, '知识')}</span><span>知识与记忆</span><small>${data.exchange.knowledge.length}</small></button></div>`; }
function conversationCanvas(data) { const conversation = data.exchange.conversations[0]; if (!conversation) return `<section class="canvas empty-canvas"><h2>暂无会话</h2><p>新建 Conversation 会在 Rust transaction 中同时建立明确的本地 root 节点。</p></section>`; return `<section class="canvas conversation-canvas"><div class="canvas-header"><div><p class="overline">Conversation · 本地树</p><h1>${escape(conversation.title)}</h1><p>追加节点只接受明确本地文本，不构造 Prompt 或 RunSpec。</p></div><button class="subtle" data-action="show-knowledge">知识工作台</button></div><div class="message-list">${orderedConversationMessages(conversation).map(message => `<article class="message ${escape(message.role)}"><header><span>${escape(message.role)}</span><time>${escape(short(message.createdAt))}</time></header>${message.blocks.map(block => `<pre>${escape(block.text || `附件引用 · ${block.asset?.displayName || ''}`)}</pre>`).join('')}</article>`).join('')}</div></section>`; }
function knowledgeCanvas(data) { const knowledge = active(data, 'knowledge'); const memory = active(data, 'memory'); const relationItems = active(data, 'relations'); const name = id => data.exchange.knowledge.find(item => item.id === id)?.title || short(id); return `<section class="canvas knowledge-canvas"><div class="canvas-header"><div><p class="overline">Knowledge / Memory · 文本 IR</p><h1>知识与记忆</h1><p>保存、取消、冲突、撤销与软删除均由 Rust SQLite 真值驱动。</p></div><div class="canvas-actions"><button class="primary" data-action="new-knowledge">${icon(icons.plus, '新建')}新建 Knowledge</button><button data-action="new-memory">${icon(icons.plus, '新建')}新建 Memory</button><button data-action="new-relation" ${knowledge.length < 2 ? 'disabled' : ''}>${icon(icons.link, '关系')}建立关系</button></div></div><div class="knowledge-list">${knowledge.map(item => `<article class="knowledge-item"><header><div><p class="overline">${escape(item.status)} · r${item.revision} · ${escape(item.classification)}</p><h2>${escape(item.title)}</h2></div><span>${escape((item.tags || []).join(' · '))}</span></header><pre>${escape(item.body)}</pre><div class="item-actions"><button data-action="edit-knowledge" data-id="${escape(item.id)}">${icon(icons.edit, '编辑')}编辑</button><button data-action="delete-knowledge" data-id="${escape(item.id)}" data-revision="${item.revision}">${icon(icons.trash, '软删除')}软删除</button></div></article>`).join('') || '<p class="empty-copy">尚无已保存的本地知识。</p>'}</div><section class="memory-rail"><div class="section-head"><h2>Memory</h2><button data-action="new-memory">新建</button></div><ul>${memory.map(item => `<li><span>${escape(item.scope)} · r${item.revision}</span><button class="memory-button" data-action="edit-memory" data-id="${escape(item.id)}">${escape(item.body)}</button><button data-action="delete-memory" data-id="${escape(item.id)}" data-revision="${item.revision}">软删除</button></li>`).join('') || '<li>暂无活动 Memory。</li>'}</ul></section><section class="memory-rail"><div class="section-head"><h2>Knowledge relation</h2><button data-action="new-relation" ${knowledge.length < 2 ? 'disabled' : ''}>建立</button></div><ul>${relationItems.map(item => `<li><span>${escape(item.kind)} · r${item.revision}</span>${escape(name(item.fromId))} → ${escape(name(item.toId))}<button data-action="delete-relation" data-id="${escape(item.id)}" data-revision="${item.revision}">撤销</button></li>`).join('') || '<li>暂无活动 relation。</li>'}</ul></section></section>`; }
function inspector(data) { if (!data) return `<aside class="inspector"><div class="inspector-head"><h2>Inspector</h2></div><p class="empty-copy">导入后显示本地工作区。</p></aside>`; const meta = state.history.modelMetadata; return `<aside class="inspector"><div class="inspector-head"><div><p class="overline">本地事实</p><h2>Inspector</h2></div><button class="icon-button" data-action="toggle-inspector" aria-label="收起">${icon(icons.close, '收起')}</button></div><section><h3>revision 与恢复</h3><div class="inspector-actions"><button data-action="undo" ${state.history.canUndo && native ? '' : 'disabled'}>${icon(icons.undo, '撤销')}撤销</button><button data-action="redo" ${state.history.canRedo && native ? '' : 'disabled'}>${icon(icons.redo, '重做')}重做</button><button data-action="recycle">${icon(icons.trash, '回收站')}回收站 ${state.history.recycleBin.length}</button></div><p class="security-note">动作栈持久化；重启后仍可恢复。冲突会保留当前 revision，绝不静默覆盖。</p></section><section><h3>模型与成本 metadata</h3>${meta.length ? meta.map(item => `<p class="metadata-row"><b>${escape(item.providerId)} / ${escape(item.modelId)}</b><br>${escape(item.metadata?.pricingStatus || 'UNKNOWN')} · ${escape(item.metadata?.priceVersion || '未配置')} · ${escape(item.metadata?.currency || '—')}</p>`).join('') : '<p class="empty-copy">未配置。无网络 catalog、无 Key、无真实费用。</p>'}<button data-action="metadata">${icon(icons.plus, '配置')}配置本地 metadata</button></section><section><h3>工作区</h3><dl><dt>状态</dt><dd>离线 · Rust SQLite</dd><dt>语义 hash</dt><dd title="${escape(data.summary.semanticHash)}">${escape(short(data.summary.semanticHash))}</dd><dt>项目 / 知识</dt><dd>${data.summary.projectCount} / ${data.summary.knowledgeCount}</dd><dt>关系 / 资产</dt><dd>${data.summary.relationCount} / ${data.summary.assetCount}</dd><dt>资产字节</dt><dd>${bytes(data.summary.assetByteCount)}</dd></dl></section><section class="security-note"><h3>安全边界</h3><p>无账号、无网络、无 Provider、无 Prompt/RunSpec。正文不执行。</p></section></aside>`; }
function scheduleComposerModelPricingRefresh() {
  if (composerModelPricingRefreshTimer !== null) window.clearTimeout(composerModelPricingRefreshTimer);
  composerModelPricingRefreshTimer = state.p6gModelPickerOpen
    ? window.setTimeout(() => { composerModelPricingRefreshTimer = null; if (state.p6gModelPickerOpen) render(); }, millisecondsUntilDeepSeekPricingTransition() + 80)
    : null;
}

function transientToastMarkup() { return transientToast ? `<div class="desktop-transient-toast ${escape(transientToast.kind)}" role="status" aria-live="polite">${escape(transientToast.message)}</div>` : ''; }
function accountSyncProgressDialog() {
  const progress = state.accountSyncProgress;
  if (!progress) return '';
  const reading = progress.operation === 'read';
  const result = progress.result;
  const title = result
    ? result.kind === 'success' ? (reading ? '读取完成' : '同步成功')
      : result.kind === 'pending' ? '同步尚未确认'
        : result.kind === 'attention' ? '读取完成，部分待处理'
          : reading ? '读取失败' : '同步失败'
    : reading ? '正在读取云端列表' : '正在同步到南枫云';
  const detail = result?.message || (result ? '' : reading ? '正在核对并合并云端会话，请稍候。' : '正在提交并核对云端回执，请稍候。');
  const resultIcon = result
    ? icon(result.kind === 'success' ? icons.check : icons.info, result.kind === 'success' ? '成功' : '提示')
    : '<span class="account-sync-progress-spinner" aria-hidden="true"></span>';
  return `<div class="scrim account-sync-progress-scrim"><section class="dialog account-sync-progress-dialog ${result ? `result ${escape(result.kind)}` : ''}" role="status" aria-live="polite" aria-busy="${result ? 'false' : 'true'}">${resultIcon}<h2>${title}</h2>${detail ? `<p>${escape(detail)}</p>` : ''}</section></div>`;
}
async function refresh() {
  if (!native) {
    const browserWorkspace = c15WorkspacePreview
      ? createC15WorkspacePreview()
      : c13ConversationLifecyclePreview ? createC13ConversationLifecyclePreview() : fixture;
    state.workspaces = [browserWorkspace.summary];
    state.current = browserWorkspace;
    state.p6gCatalog = { revision: 0, snapshot: { catalogVersion: 'web-preview-unconfigured', policyVersion: 1, candidates: [] } };
    state.p6gGlobalDefault = { revision: 0, tier: null };
    state.p6gSelection = null;
    state.conversationPreferences = { revision: 0, toneOverride: null, webSearchOverride: null };
    state.agentRuns = [];
    state.p6eAcceptance = { enabled: false, receipt: null };
    state.transcription = { settings: { ...TRANSCRIPTION_PREVIEW_STATE.settings }, tasks: [] };
    if (c09ReminderPreview) {
      state.pane = 'reminders';
      state.reminders = createC09ReminderPreviewProjection(c09ReminderPreview);
    }
    if (c10TranscriptionPreview) {
      state.pane = 'transcription';
      state.transcription = createC10TranscriptionPreviewProjection(c10TranscriptionPreview);
      state.selectedTranscriptionTaskId = state.transcription.tasks[0]?.id || null;
    }
    if (c12ModelNetworkPreview) {
      const preview = createC12ModelNetworkPreview();
      state.pane = 'settings';
      state.settingsSection = ['model', 'model-configuration', 'conversation-cost', 'context-selections', 'diagnostics'].includes(c12ModelNetworkPreview)
        ? c12ModelNetworkPreview
        : 'model';
      state.usageLedger = preview.usageLedger;
      state.contextSelectionRecords = preview.contextSelectionRecords;
      state.diagnosticRecords = preview.diagnosticRecords;
      state.invocationRecords = preview.invocationRecords;
    }
    if (c13ConversationLifecyclePreview) {
      state.pane = 'settings';
      state.settingsSection = ['conversations', 'favorites', 'archived', 'recycle'].includes(c13ConversationLifecyclePreview)
        ? c13ConversationLifecyclePreview
        : 'conversations';
      state.favoriteConversationIds = new Set([C13_FAVORITE_CONVERSATION_ID]);
    }
    if (c14LocalDataPreview) {
      const preview = createC14LocalDataPreview();
      state.pane = 'settings';
      state.settingsSection = ['data', 'privacy', 'json-import-results', 'zip-import-results'].includes(c14LocalDataPreview)
        ? c14LocalDataPreview
        : 'privacy';
      state.privacyInventory = preview.privacyInventory;
      state.chatgptTask = preview.chatgptTask;
      state.claudeTask = preview.claudeTask;
      state.p6kTask = preview.p6kTask;
      if (c14LocalDataPreview === 'cleanup-scope') state.dialog = { kind: 'privacy-cleanup-scope' };
      if (c14LocalDataPreview === 'delete-all') state.dialog = { kind: 'privacy-cleanup-preview', preview: { scope: 'ALL_LOCAL_BUSINESS_DATA', fingerprint: 'c14-browser-read-only', aggregates: [{ id: 'all_local_business_data', count: 9, byteCount: 31744 }], taskCandidates: [] }, selectedTaskIds: [], confirmation: '', submitting: false, failure: '' };
    }
    if (c15WorkspacePreview) {
      state.selectedConversationId = null;
      state.selectedWorkProjectId = c15WorkspacePreview === 'work-project' ? C15_PROJECT_ID : null;
      state.status = 'C15 Browser 隔离样本 · 不读取或写入 Desktop SQLite。';
      if (['workspace', 'development'].includes(c15WorkspacePreview)) {
        state.pane = 'settings';
        state.settingsSection = c15WorkspacePreview;
      } else {
        state.pane = ['projects', 'knowledge', 'memory'].includes(c15WorkspacePreview) ? c15WorkspacePreview : 'work';
      }
    }
    applyC16Preview('Browser ');
    if (!c15WorkspacePreview && !state.selectedConversationId) selectWorkspaceDefaultConversation(browserWorkspace);
    if (!c13ConversationLifecyclePreview) reloadFavoriteConversationIds();
    await loadConversationReadState({ observeSelected: true });
    recomputeConversationFind();
    render();
    return;
  }
  state.runtimeInfo = await invoke('read_desktop_runtime_info');
  state.workspaces = await invoke('list_desktop_workspaces');
  state.current = state.workspaces.length ? await invoke('read_desktop_workspace', { workspaceId: state.current?.summary?.id || state.workspaces[0].id }) : null;
  if (state.current && !resolveConversation(state.current, state.selectedConversationId)) selectWorkspaceDefaultConversation(state.current);
  reloadFavoriteConversationIds();
  // These are independent read projections after the workspace and selected
  // conversation are known. Waiting for each IPC receipt serially delayed the
  // first usable Desktop frame even though no mutation depends on another.
  const [
    ,
    p6gCatalog,
    p6gGlobalDefault,
    p6gSelection,
    history,
    agentRuns,
    p6eAcceptance,
    transcription,
  ] = await Promise.all([
    loadConversationReadState({ observeSelected: !state.runtimeInfo?.automaticWorkSuppressed }),
    invoke('read_desktop_p6g_catalog'),
    invoke('read_desktop_p6g_global_default'),
    state.current && state.selectedConversationId ? invoke('read_desktop_p6g_selection', { workspaceId: state.current.summary.id, conversationId: state.selectedConversationId }) : Promise.resolve(null),
    state.current ? invoke('read_desktop_workbench_history', { workspaceId: state.current.summary.id }) : Promise.resolve({ canUndo: false, canRedo: false, recycleBin: [], modelMetadata: [] }),
    invoke('inspect_p8_agent_runs'),
    invoke('read_p6e_temporary_maintenance_acceptance_status'),
    loadTranscriptionState(),
  ]);
  recomputeConversationFind();
  state.p6gCatalog = p6gCatalog;
  state.p6gGlobalDefault = p6gGlobalDefault;
  state.p6gSelection = p6gSelection;
  state.conversationPreferences = state.p6gSelection?.conversationOverride || { revision: 0, toneOverride: null, webSearchOverride: null };
  state.history = history;
  state.agentRuns = agentRuns;
  state.p6eAcceptance = p6eAcceptance;
  state.transcription = transcription;
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
app.addEventListener('click', async event => { const target = event.target.closest?.('[data-action="retry-p6k-zip"],[data-action="skip-p6k-zip-failures"],[data-action="delete-p6k-zip-batch"],[data-action="confirm-delete-p6k-zip-batch"]'); if (!target || !state.p6kTask) return; event.preventDefault(); event.stopImmediatePropagation(); const action = target.dataset.action; if (action === 'delete-p6k-zip-batch') { state.dialog = { kind: 'p6k-batch-delete', taskId: state.p6kTask.id, submitting: false, failure: '' }; render(); return; } if (action === 'confirm-delete-p6k-zip-batch' && (state.dialog?.kind !== 'p6k-batch-delete' || state.dialog.taskId !== state.p6kTask.id || state.dialog.submitting)) return; try { if (action === 'retry-p6k-zip') state.p6kTask = await invoke('retry_p6k_zip_import_task', { taskId: state.p6kTask.id }); else if (action === 'skip-p6k-zip-failures') state.p6kTask = await invoke('skip_p6k_zip_import_failures', { taskId: state.p6kTask.id }); else { state.dialog = { ...state.dialog, submitting: true, failure: '' }; render(); await invoke('delete_p6k_zip_import_batch', { taskId: state.p6kTask.id }); state.p6kTask = null; state.dialog = null; } state.status = action === 'confirm-delete-p6k-zip-batch' ? '已删除该导入批次与私有副本；官方会话删除标记已保留，不会在后续导入中复活。' : 'ZIP 导入状态已更新。'; state.error = ''; if (state.current) await refresh(); } catch (error) { if (action === 'confirm-delete-p6k-zip-batch' && state.dialog?.kind === 'p6k-batch-delete') state.dialog = { ...state.dialog, submitting: false, failure: String(error) }; else state.error = `ZIP 导入状态未更新：${String(error)}`; } render(); }, true);
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
  void persistNativeAppSettings({}, patch).then(async () => {
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
    await persistNativeAppSettings({}, Object.fromEntries(['tone', 'nickname', 'occupation', 'customInstructions'].map(key => [key, state.personalizationDraft[key]])));
    state.personalizationDraft = { ...state.productSettings };
    state.personalizationDirty = false;
    if (state.dialog?.kind === 'custom-instructions-fullscreen') state.dialog = null;
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
  if (!['show-settings', 'open-settings-page', 'settings-back', 'return-to-settings-conversation-list', 'open-settings-picker', 'dismiss-settings-picker', 'select-settings-picker-option', 'toggle-product-setting', 'save-personalization', 'open-custom-instructions-fullscreen', 'save-custom-instructions-fullscreen', 'select-model-service-provider', 'toggle-model-service-enabled', 'select-model-service-preset', 'reveal-model-service-credential', 'save-model-service-settings', 'test-model-service-connection', 'select-usage-section'].includes(action)) return;
  event.preventDefault();
  event.stopImmediatePropagation();
  if (action === 'show-settings') {
    state.pane = 'settings';
    state.settingsSection = 'personalization';
    state.settingsPicker = null;
    state.personalizationDraft = { ...state.productSettings };
    state.personalizationDirty = false;
    state.profileOpen = false;
    state.error = '';
  } else if (action === 'open-settings-page') {
    const nextSettingsSection = target.dataset.page || 'personalization';
    if (nextSettingsSection !== 'account' && state.accountRecovery?.rotation === true) state.accountRecovery = null;
    if (nextSettingsSection !== 'model-configuration') clearModelCredentialDraftOutsideEditor(true);
    state.settingsSection = nextSettingsSection;
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
  } else if (action === 'settings-back') {
    if (state.accountRecovery?.rotation === true) state.accountRecovery = null;
    if (settingsParent(state.settingsSection) !== 'model-configuration') clearModelCredentialDraftOutsideEditor(true);
    state.settingsSection = settingsParent(state.settingsSection) || 'personalization';
    state.settingsPicker = null;
    state.error = '';
  } else if (action === 'return-to-settings-conversation-list') {
    if (state.accountRecovery?.rotation === true) state.accountRecovery = null;
    const destination = state.settingsConversationReturn;
    state.pane = 'settings';
    state.settingsSection = destination?.page || 'conversations';
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
      // Android commits the global tone as soon as the user chooses it. Keeping
      // it only in the personalization draft meant any render re-read SQLite's
      // old `default` value and made the selected style appear to jump back.
      writeProductSettings({ tone: value });
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
      state.pendingChatSubmission = null;
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
      ? '本机业务数据已删除；部分应用私有凭据未能清理，请重试全部本地数据清理。'
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
  const confirm = document.querySelector('[data-action="confirm-privacy-cleanup"]');
  if (confirm) confirm.disabled = event.target.value !== '删除全部本地业务数据';
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
    state.dialog = { kind: 'conversation-delete', source: 'archived', id: target.dataset.id, revision: Number(target.dataset.revision), title: target.dataset.title || '未命名会话' };
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
  state.localBackup = { ...state.localBackup, working: true, operation: 'EXPORT', notice: '', error: '' };
  render();
  try {
    const artifact = await invoke('export_desktop_local_backup', { selectedPath });
    state.localBackup = { ...state.localBackup, working: false, operation: null, notice: `备份已系统位置回读校验：${artifact.sha256.slice(0, 12)}… · Schema ${artifact.schemaVersion}`, error: '' };
  } catch (error) {
    state.localBackup = { ...state.localBackup, working: false, operation: null, notice: '', error: `备份失败：${String(error)}` };
  }
  render();
}
async function importLocalBackup() {
  if (!native || state.localBackup.working || state.localBackup.restartRequired) return;
  const selectedPath = await dialogInvoke('open', { multiple: false, directory: false, filters: [{ name: '南枫 AI 本机备份', extensions: ['nfai-backup'] }] });
  if (!selectedPath) return;
  state.localBackup = { ...state.localBackup, working: true, operation: 'RESTORE', preflight: null, replaceLocal: false, notice: '', error: '' };
  render();
  try {
    const preflight = await invoke('preflight_desktop_local_backup', { selectedPath });
    state.localBackup = { ...state.localBackup, working: false, operation: null, preflight, replaceLocal: !(preflight.conflicts || []).length, notice: `预检通过：Schema ${preflight.schemaVersion}，请确认恢复方式。`, error: '' };
  } catch (error) {
    state.localBackup = { ...state.localBackup, working: false, operation: null, preflight: null, notice: '', error: `预检失败：${String(error)}` };
  }
  render();
}
async function restoreLocalBackup() {
  const preflight = state.localBackup.preflight;
  if (!native || !preflight || state.localBackup.working) return;
  state.localBackup = { ...state.localBackup, working: true, operation: 'RESTORE', notice: '', error: '' };
  render();
  try {
    const receipt = await invoke('restore_desktop_local_backup', { fingerprint: preflight.fingerprint, replaceLocal: Boolean(state.localBackup.replaceLocal) });
    state.localBackup = { ...state.localBackup, working: false, operation: null, preflight: null, notice: '替换已完成。为避免旧 SQLite 与页面引用，现请手动完全重启 App；不会自动继续任务。', error: '', restartRequired: Boolean(receipt.restartRequired) };
    if (receipt.restartRequired) state.dialog = { kind: 'local-backup-restart-required' };
  } catch (error) {
    state.localBackup = { ...state.localBackup, working: false, operation: null, notice: '', error: `恢复失败：${String(error)}` };
  }
  render();
}
async function cancelLocalRestore() {
  if (!native || state.localBackup.working || state.localBackup.restartRequired) return;
  state.localBackup = { ...state.localBackup, working: true, operation: 'RESTORE', notice: '', error: '' };
  render();
  try {
    await invoke('cancel_desktop_local_restore');
    state.localBackup = { ...state.localBackup, working: false, operation: null, preflight: null, replaceLocal: false, notice: '已取消；未改动本地数据。', error: '', interrupted: false };
  } catch (error) {
    state.localBackup = { ...state.localBackup, working: false, operation: null, error: `取消未完成：${String(error)}` };
  }
  render();
}
app.addEventListener('click', event => {
  const action = event.target.closest?.('[data-action]')?.dataset.action;
  if (!['new-chat', 'select-chat', 'select-workspace'].includes(action)) return;
  state.pendingNewConversationModelId = null;
  if (action === 'new-chat') state.p6gSelection = null;
}, true);
app.addEventListener('click', event => {
  const target = event.target.closest?.('[data-action="select-chat"]');
  // Search-result drill-down owns an optional return route, but a deliberate
  // sidebar selection starts a normal conversation navigation instead.
  if (!target?.closest('.chat-sidebar')) return;
  state.searchPanel = false;
  state.searchReturnActive = false;
  state.searchLocatedArchivedConversationId = null;
  state.searchAnchorMessageId = null;
  state.searchAnchorAttachmentId = null;
}, true);
async function saveLocalMessage() { const text = state.composerDraft.trim(); if (!text) { state.error = '请输入非空内容。'; render(); return; } if (!native || !state.current) { state.error = native ? '先导入一个本地工作区。' : 'Web 预览不会写入 Desktop SQLite。'; render(); return; } const conversation = resolveConversation(state.current, state.selectedConversationId); try { if (conversation) { await invoke('mutate_desktop_domain', { args: { intentId: intent('chat-message'), workspaceId: state.current.summary.id, entity: 'conversation', action: 'appendMessage', objectId: conversation.id, expectedRevision: conversation.revision, fields: { text, role: 'user' } } }); } else { const receipt = await invoke('mutate_desktop_domain', { args: { intentId: intent('chat-create'), workspaceId: state.current.summary.id, entity: 'conversation', action: 'create', fields: { title: text.replace(/\s+/g, ' ').slice(0, 36), projectId: state.pane === 'work' ? state.selectedWorkProjectId : null, firstMessage: text } } }); state.selectedConversationId = receipt.objectId; } state.composerDraft = ''; state.error = ''; state.status = '已保存为本地记录；没有调用模型。'; await refresh(); } catch (error) { state.error = `本地保存被拒绝：${String(error)}`; render(); } }
app.addEventListener('click', async event => { const target = event.target.closest('[data-action]'); const action = target?.dataset.action; if (!action) return; const data = workspace(); if (action === 'new-chat') { state.settingsConversationReturn = null; state.pane = 'chat'; state.selectedConversationId = null; state.conversationPreferences = { revision: 0, toneOverride: null, webSearchOverride: null }; state.composerDraft = ''; state.profileOpen = false; state.error = ''; state.status = '新对话将先保存到本地；联网回答需另行配置并确认。'; state.conversationFindOpen = false; state.conversationFindQuery = ''; recomputeConversationFind({ resetIndex: true }); render(); } else if (action === 'select-chat') { const settingsPage = target.closest('.android-settings-main') ? state.settingsSection : null; if (settingsPage && ['favorites', 'archived', 'recycle'].includes(settingsPage)) state.settingsConversationReturn = { page: settingsPage, label: settingsPage === 'favorites' ? '收藏' : settingsPage === 'archived' ? '已归档' : '回收站', readOnly: ['archived', 'recycle'].includes(settingsPage) }; else state.settingsConversationReturn = null; state.pane = 'chat'; state.selectedConversationId = target.dataset.id; if (native && state.current) { state.p6gSelection = await invoke('read_desktop_p6g_selection', { workspaceId: state.current.summary.id, conversationId: state.selectedConversationId }); state.conversationPreferences = state.p6gSelection.conversationOverride; } else { state.conversationPreferences = { revision: 0, toneOverride: null, webSearchOverride: null }; } if (!state.chatScrollPositions.has(target.dataset.id)) state.pendingChatScrollToLatestId = target.dataset.id; state.profileOpen = false; state.error = ''; state.conversationFindOpen = false; state.conversationFindQuery = ''; recomputeConversationFind({ resetIndex: true }); if (!state.settingsConversationReturn?.readOnly) await markConversationOpened(target.dataset.id); render(); } else if (action === 'show-chat') { state.settingsConversationReturn = null; state.pane = 'chat'; state.profileOpen = false; render(); } else if (action === 'show-work') { state.settingsConversationReturn = null; state.pane = 'work'; state.selectedConversationId = null; state.selectedWorkProjectId = null; state.profileOpen = false; render(); } else if (action === 'show-connections') { state.pane = 'connections'; state.profileOpen = false; render(); } else if (action === 'toggle-profile') { state.profileOpen = !state.profileOpen; render(); } else if (action === 'save-local-message') saveLocalMessage(); else if (action === 'start-import') pickExchange(); else if (action === 'confirm-import') importPreflighted(); else if (action === 'close-dialog') { if (state.dialog?.kind === 'custom-instructions-fullscreen') { state.personalizationDraft = { ...state.dialog.previousDraft }; state.personalizationDirty = state.dialog.previousDirty; } state.dialog = null; render(); } else if (action === 'select-workspace') { state.current = await invoke('read_desktop_workspace', { workspaceId: target.dataset.id }); state.history = await invoke('read_desktop_workbench_history', { workspaceId: target.dataset.id }); state.selectedConversationId = null; state.conversationPreferences = { revision: 0, toneOverride: null, webSearchOverride: null }; reloadFavoriteConversationIds(); await loadConversationReadState({ observeSelected: false }); state.status = '已切换到独立本地工作区。'; state.error = ''; render(); } else if (action === 'show-conversation') { state.pane = 'conversation'; state.treeOpen = false; render(); } else if (action === 'show-knowledge') { state.pane = 'knowledge'; state.treeOpen = false; render(); } else if (action === 'toggle-inspector') { state.inspectorOpen = !state.inspectorOpen; render(); } else if (action === 'toggle-tree') { state.treeOpen = !state.treeOpen; render(); } else if (action === 'toggle-scale') { state.scale = state.scale === 1 ? 2 : 1; state.status = `应用缩放已切换为 ${state.scale.toFixed(1)}×。`; render(); } else if (action === 'about') { state.dialog = 'about'; render(); } else if (action === 'new-project') { state.dialog = { kind: 'project', item: null }; render(); } else if (action === 'edit-project') { state.dialog = { kind: 'project', item: data.exchange.projects.find(item => item.id === target.dataset.id) }; render(); } else if (action === 'save-project') saveProject(); else if (action === 'new-knowledge') { state.dialog = { kind: 'knowledge', item: null }; render(); } else if (action === 'edit-knowledge') { state.dialog = { kind: 'knowledge', item: data.exchange.knowledge.find(item => item.id === target.dataset.id) }; render(); } else if (action === 'save-knowledge') saveKnowledge(); else if (action === 'new-memory') { state.dialog = { kind: 'memory', item: null }; render(); } else if (action === 'edit-memory') { state.dialog = { kind: 'memory', item: data.exchange.memory.find(item => item.id === target.dataset.id) }; render(); } else if (action === 'save-memory') saveMemory(); else if (action === 'new-relation') { state.dialog = { kind: 'relation' }; render(); } else if (action === 'save-relation') saveRelation(); else if (action === 'delete-knowledge') mutate({ entity: 'knowledge', action: 'softDelete', objectId: target.dataset.id, expectedRevision: Number(target.dataset.revision), fields: {} }, 'Knowledge 已软删除；可在回收站恢复。'); else if (action === 'delete-memory') mutate({ entity: 'memory', action: 'softDelete', objectId: target.dataset.id, expectedRevision: Number(target.dataset.revision), fields: {} }, 'Memory 已软删除；可在回收站恢复。'); else if (action === 'delete-relation') mutate({ entity: 'relation', action: 'softDelete', objectId: target.dataset.id, expectedRevision: Number(target.dataset.revision), fields: {} }, 'relation 已撤销；可在回收站恢复。'); else if (action === 'restore') mutate({ entity: target.dataset.entity, action: 'restore', objectId: target.dataset.id, expectedRevision: Number(target.dataset.revision), fields: {} }, '对象已恢复为新的 revision。'); else if (action === 'undo') undo(); else if (action === 'redo') undo(true); else if (action === 'recycle') { state.dialog = 'recycle'; render(); } else if (action === 'metadata') { state.dialog = 'metadata'; render(); } else if (action === 'save-metadata') saveMetadata(); else if (action === 'start-export') exportCurrent(); });
app.addEventListener('click', event => {
  const action = event.target.closest('[data-action]')?.dataset.action;
  if (action === 'return-workspace-origin') {
    const destination = state.workspaceReturn;
    state.workspaceReturn = null;
    state.profileOpen = false;
    state.error = '';
    state.selectedWorkProjectId = null;
    state.selectedConversationId = destination?.conversationId || null;
    if (destination?.pane === 'settings') {
      state.pane = 'settings';
      state.settingsSection = destination.settingsSection || 'workspace';
      state.settingsPicker = null;
    } else state.pane = 'chat';
  } else if (action === 'show-work') {
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
    resizeComposer(event.target);
    const send = document.querySelector('[data-action="save-local-message"]');
    const attachmentCount = state.temporaryConversation ? (state.temporaryConversation.draftAttachmentIds || []).length : state.composerAttachments.length;
    if (send) send.disabled = (!state.composerDraft.trim() && !attachmentCount) || !native || (!state.current && !state.temporaryConversation);
  } else if (event.target.id === 'settings-search') {
    state.settingsSearch = event.target.value;
    scheduleSettingsSearchRender();
  } else if (event.target.id === 'memory-summary-editor' && state.dialog?.kind === 'memory-summary-editor') {
    state.dialog = { ...state.dialog, value: event.target.value };
    const save = document.querySelector('[data-action="save-memory-summary-editor"]');
    if (save) save.disabled = !String(event.target.value || '').trim();
    const counter = event.target.closest('.memory-summary-editor')?.querySelector('small');
    if (counter) counter.textContent = `${event.target.value.length} / 2000000 字`;
  } else if (event.target.id === 'conversation-find-input') {
    state.conversationFindQuery = event.target.value;
    if (event.isComposing) scheduleConversationFindRender({ isComposing: true });
    else scheduleConversationFindRender();
  } else if (event.target.id === 'personalization-nickname' || event.target.id === 'personalization-occupation' || event.target.id === 'personalization-instructions' || event.target.id === 'personalization-instructions-fullscreen') {
    const key = event.target.id === 'personalization-nickname' ? 'nickname' : event.target.id === 'personalization-occupation' ? 'occupation' : 'customInstructions';
    state.personalizationDraft = { ...state.personalizationDraft, [key]: event.target.value };
    state.personalizationDirty = true;
    const save = document.querySelector('[data-action="save-personalization"]');
    if (save) save.disabled = false;
    if (key === 'customInstructions') {
      const counter = event.target.closest('.android-settings-field, .custom-instructions-fullscreen')?.querySelector('small');
      if (counter) counter.textContent = `${event.target.value.length} / ${CUSTOM_INSTRUCTIONS_MAX_LENGTH} 字`;
    }
  }
});

app.addEventListener('compositionstart', event => {
  if (event.target.id !== 'conversation-find-input') return;
  conversationFindInputController.onCompositionStart();
});

app.addEventListener('compositionend', event => {
  if (event.target.id !== 'conversation-find-input') return;
  state.conversationFindQuery = event.target.value;
  conversationFindInputController.onCompositionEnd(event.target.value);
});

function composerHasFileTransfer(transfer) {
  return Array.from(transfer?.types || []).includes('Files')
    || Array.from(transfer?.items || []).some(item => item.kind === 'file');
}

function composerFileTransferTarget(event) {
  return event.target?.closest?.('.chat-composer, .transcription-page') || null;
}

app.addEventListener('paste', event => {
  if (event.target?.id !== 'chat-composer') return;
  const files = pastedComposerFiles(event.clipboardData);
  if (!files.length) return;
  // File paste must never turn into a private path string in the outgoing text. Plain text keeps
  // the platform's normal paste behavior when no concrete File was supplied.
  event.preventDefault();
  void importPastedComposerClipboardFiles(event.clipboardData, files);
});

app.addEventListener('dragenter', event => {
  const target = composerFileTransferTarget(event);
  if (!target || !composerHasFileTransfer(event.dataTransfer)) return;
  event.preventDefault();
  target.classList.add('composer-file-drop-active');
});

app.addEventListener('dragover', event => {
  const target = composerFileTransferTarget(event);
  if (!target || !composerHasFileTransfer(event.dataTransfer)) return;
  event.preventDefault();
  event.dataTransfer.dropEffect = 'copy';
  target.classList.add('composer-file-drop-active');
});

app.addEventListener('dragleave', event => {
  const target = composerFileTransferTarget(event);
  if (!target || target.contains(event.relatedTarget)) return;
  target.classList.remove('composer-file-drop-active');
});

app.addEventListener('drop', event => {
  const target = composerFileTransferTarget(event);
  if (!target) return;
  const files = pastedComposerFiles(event.dataTransfer);
  target.classList.remove('composer-file-drop-active');
  if (!files.length) return;
  event.preventDefault();
  void (target.matches('.transcription-page') ? importDroppedTranscriptionFiles(files) : importComposerClipboardFiles(files));
});

async function clearMemorySummary() {
  if (!native || !state.current) return;
  const memories = active(state.current, 'memory').filter(item => (item.status || 'ACTIVE') === 'ACTIVE' && (item.scope || 'GLOBAL') === 'GLOBAL' && !item.scopeId);
  let removed = 0;
  try {
    for (const item of memories) {
      await invoke('mutate_desktop_domain', { args: { intentId: intent('memory-summary-delete'), workspaceId: state.current.summary.id, entity: 'memory', action: 'softDelete', objectId: item.id, expectedRevision: Number(item.revision), fields: {} } });
      removed += 1;
    }
    state.dialog = null;
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
  if (!['edit-memory-summary', 'save-memory-summary-editor', 'show-memory-summary-about', 'refresh-memory-summary', 'ask-delete-memory-summary', 'confirm-delete-memory-summary', 'ask-disable-memory-summary', 'confirm-disable-memory-summary'].includes(action)) return;
  event.preventDefault();
  event.stopImmediatePropagation();
  if (action === 'edit-memory-summary') {
    const memories = active(workspace(), 'memory').filter(item => (item.status || 'ACTIVE') === 'ACTIVE' && (item.scope || 'GLOBAL') === 'GLOBAL' && !item.scopeId);
    const latest = [...memories].sort((left, right) => String(right.updatedAt || right.createdAt || '').localeCompare(String(left.updatedAt || left.createdAt || '')))[0];
    state.dialog = { kind: 'memory-summary-editor', memoryId: latest?.id, expectedRevision: latest?.revision, value: memories.map(item => String(item.body || '').trim()).filter(Boolean).join('\n\n') };
  } else if (action === 'save-memory-summary-editor') {
    const editor = state.dialog;
    const body = String(editor?.value || '').trim();
    if (editor?.kind !== 'memory-summary-editor' || !body) return;
    void mutate({ entity: 'memory', action: editor.memoryId ? 'replaceMemorySummary' : 'create', objectId: editor.memoryId, expectedRevision: editor.expectedRevision, fields: editor.memoryId ? { body } : { body, scope: 'GLOBAL', scopeId: null } }, '记忆摘要已保存，后续检索使用修改后的内容。');
    return;
  } else if (action === 'show-memory-summary-about') {
    state.dialog = { kind: 'memory-summary-about' };
  } else if (action === 'refresh-memory-summary') {
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
  if (!emphasize && rail.dataset.activeRailSlot === String(currentSlot)) return;
  rail.dataset.activeRailSlot = String(currentSlot);
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
  delete rail?.dataset.activeRailSlot;
  rail?.querySelectorAll('button[data-rail-slot]').forEach(item => {
    delete item.dataset.railDistance;
    item.classList.remove('is-active');
  });
}
function syncScrollToLatestControl(scroll, atLatest) {
  const control = scroll.closest('.chat-main')?.querySelector('[data-action="scroll-to-latest"]');
  if (!control) return;
  control.hidden = atLatest;
}
function syncTranscriptRailToScroll(scroll) {
  const rail = scroll.parentElement?.querySelector('.chat-transcript-rail');
  if (!rail) return;
  const bounds = scroll.getBoundingClientRect();
  const centerY = bounds.top + bounds.height / 2;
  const centerTarget = [0.5, 0.78, 0.22]
    .map(fraction => document.elementFromPoint(bounds.left + bounds.width * fraction, centerY)?.closest?.('.chat-message'))
    .find(message => message && scroll.contains(message));
  let nearestIndex = Number(centerTarget?.dataset.messageIndex);
  // A date divider or a gap can occupy the center point. That uncommon case
  // keeps the exact previous fallback, while ordinary scroll frames perform
  // one hit-test rather than forcing layout on every message in the transcript.
  if (!Number.isInteger(nearestIndex) || !scroll.contains(centerTarget)) {
    const messages = [...scroll.querySelectorAll('.chat-message')];
    if (!messages.length) return;
    const viewportCenter = scroll.scrollTop + scroll.clientHeight / 2;
    nearestIndex = messages.reduce((best, message, index) => Math.abs(message.offsetTop + message.offsetHeight / 2 - viewportCenter) < Math.abs(messages[best].offsetTop + messages[best].offsetHeight / 2 - viewportCenter) ? index : best, 0);
  }
  const target = [...rail.querySelectorAll('button[data-index]')].reduce((best, item) => !best || Math.abs(Number(item.dataset.index) - nearestIndex) < Math.abs(Number(best.dataset.index) - nearestIndex) ? item : best, null);
  applyTranscriptRailPosition(rail, target);
}

function scheduleTranscriptRailSync(scroll) {
  pendingTranscriptRailScroll = scroll;
  if (transcriptRailSyncFrame !== null) return;
  transcriptRailSyncFrame = requestAnimationFrame(() => {
    transcriptRailSyncFrame = null;
    const pending = pendingTranscriptRailScroll;
    pendingTranscriptRailScroll = null;
    if (pending?.isConnected) syncTranscriptRailToScroll(pending);
  });
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
    scheduleTranscriptRailSync(scroll);
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
    syncScrollToLatestControl(scroll, atLatest);
  }
}, true);

let sidebarDragPointerId = null;
function updateSidebarWidth(width) {
  const persist = Boolean(arguments[1]?.persist);
  state.sidebarWidth = clampDesktopSidebarWidth(width, window.innerWidth);
  if (persist) persistSidebarWidth(state.sidebarWidth);
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
function finishSidebarDrag(event) {
  if (sidebarDragPointerId !== event.pointerId) return;
  sidebarDragPointerId = null;
  persistSidebarWidth(state.sidebarWidth);
  document.querySelector('.chat-sidebar-divider')?.classList.remove('dragging');
}
app.addEventListener('pointerup', finishSidebarDrag);
app.addEventListener('pointercancel', finishSidebarDrag);
app.addEventListener('dblclick', event => { if (!event.target.closest?.('.chat-sidebar-divider')) return; updateSidebarWidth(256, { persist: true }); render(); });
app.addEventListener('keydown', event => {
  const divider = event.target.closest?.('.chat-sidebar-divider');
  if (!divider || window.innerWidth <= 900) return;
  const step = event.shiftKey ? 32 : 12;
  if (event.key === 'ArrowLeft') updateSidebarWidth(state.sidebarWidth - step, { persist: true });
  else if (event.key === 'ArrowRight') updateSidebarWidth(state.sidebarWidth + step, { persist: true });
  else if (event.key === 'Home') updateSidebarWidth(220, { persist: true });
  else if (event.key === 'End') updateSidebarWidth(440, { persist: true });
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
function menuAnchorRect(target) {
  const rect = target?.getBoundingClientRect?.();
  if (!rect || !(rect.width > 0) || !(rect.height > 0)) return null;
  return { left: rect.left, right: rect.right, top: rect.top, bottom: rect.bottom };
}

// Imported mobile conversations are often long enough to be opened while the
// transcript still has inertial momentum. WebKit can suppress the later click
// in that state, so primary-pointer activation belongs to pointerdown. Keep a
// keyboard-only click fallback for accessibility.
const assistantMessageMenuTriggers = new WeakSet();
function toggleAssistantMessageMenu(trigger) {
  const messageId = String(trigger?.dataset?.messageId || '');
  if (!messageId) return;
  state.error = '';
  if (state.assistantMessageMenu?.messageId === messageId) closeTopOverlay();
  else openTransientOverlay('assistant-message-menu', trigger, { messageId, anchorRect: menuAnchorRect(trigger) });
}
function bindAssistantMessageMenuTriggers() {
  document.querySelectorAll('[data-action="open-assistant-message-menu"]').forEach(button => {
    if (assistantMessageMenuTriggers.has(button)) return;
    assistantMessageMenuTriggers.add(button);
    button.addEventListener('pointerdown', event => {
      if (event.button !== 0) return;
      event.preventDefault();
      event.stopImmediatePropagation();
      toggleAssistantMessageMenu(button);
    });
    button.addEventListener('click', event => {
      event.preventDefault();
      event.stopImmediatePropagation();
      if (event.detail === 0) toggleAssistantMessageMenu(button);
    });
  });
}

function openConversationContextMenu(row, trigger = row) {
  // Rendering replaces sidebar rows. Preserve the actual trigger rectangle at
  // click time so every row menu stays attached to the clicked three-dot
  // button instead of being re-anchored to a similarly named replacement row.
  openTransientOverlay('context', trigger, {
    id: row.dataset.id,
    revision: Number(row.dataset.revision),
    pinned: row.dataset.pinned === 'true',
    favorite: row.dataset.favorite === 'true',
    archived: row.dataset.archived === 'true',
    workspaceId: row.dataset.workspaceId || null,
    source: 'sidebar',
    anchorRect: menuAnchorRect(trigger),
    title: row.querySelector('.chat-history-select > span')?.textContent || '',
  });
}

function openConversationHeaderMenu(trigger) {
  const conversation = currentConversation();
  if (!conversation) return;
  openTransientOverlay('context', trigger, {
    id: conversation.id,
    revision: Number(conversation.revision),
    pinned: Boolean(conversation.pinned),
    favorite: state.favoriteConversationIds.has(conversation.id),
    archived: Boolean(conversation.archived),
    source: 'header',
    anchorRect: menuAnchorRect(trigger),
    title: conversation.title,
  });
}

function positionConversationContextMenu() {
  const menu = document.querySelector('.chat-context-menu');
  if (!menu || !state.contextMenu) return;
  const row = [...document.querySelectorAll('[data-conversation-row]')].find(item => item.dataset.id === state.contextMenu.id);
  const headerTrigger = state.contextMenu.source === 'header' ? document.querySelector('.chat-header-more') : null;
  const storedAnchor = state.contextMenu.anchorRect;
  if (!storedAnchor && (state.contextMenu.source === 'header' ? !headerTrigger : !row)) return;
  // Both header and sidebar menus use the trigger captured before render. The
  // live-node fallback only covers keyboard and legacy callers without one.
  const anchor = storedAnchor ?? headerTrigger?.getBoundingClientRect() ?? row.querySelector('.chat-history-select > span')?.getBoundingClientRect() ?? row.getBoundingClientRect();
  const sidebar = row?.closest('.chat-sidebar')?.getBoundingClientRect() ?? document.querySelector('.chat-sidebar')?.getBoundingClientRect();
  const appBounds = app.getBoundingClientRect();
  const viewportWidth = Math.max(window.innerWidth || 0, document.documentElement.clientWidth || 0, Math.ceil(appBounds.right));
  const viewportHeight = Math.max(window.innerHeight || 0, document.documentElement.clientHeight || 0, Math.ceil(appBounds.bottom));
  const menuBounds = menu.getBoundingClientRect();
  const position = resolveConversationMenuAnchor(anchor, {
    viewportWidth,
    viewportHeight,
    sidebarRect: sidebar,
    source: state.contextMenu.source,
    menuWidth: menuBounds.width || 192,
    menuHeight: menuBounds.height || 204,
  });
  menu.style.left = `${position.x}px`;
  menu.style.top = `${position.y}px`;
  menu.style.right = 'auto';
  menu.dataset.positioned = 'true';
}

function positionAssistantMessageMenu() {
  const menu = document.querySelector('.assistant-message-menu');
  const anchor = state.assistantMessageMenu?.anchorRect;
  if (!menu || !anchor) return;
  const appBounds = app.getBoundingClientRect();
  const viewportWidth = Math.max(window.innerWidth || 0, document.documentElement.clientWidth || 0, Math.ceil(appBounds.right));
  const viewportHeight = Math.max(window.innerHeight || 0, document.documentElement.clientHeight || 0, Math.ceil(appBounds.bottom));
  const menuBounds = menu.getBoundingClientRect();
  const position = resolveAssistantMessageMenuAnchor(anchor, {
    viewportWidth,
    viewportHeight,
    menuWidth: menuBounds.width || 208,
    menuHeight: menuBounds.height || 112,
  });
  menu.style.left = `${position.x}px`;
  menu.style.top = `${position.y}px`;
  menu.dataset.positioned = 'true';
}

const conversationLongPress = createConversationLongPressController({
  onOpen: conversationId => {
    const row = [...document.querySelectorAll('[data-conversation-row]')].find(item => item.dataset.id === conversationId);
    if (row) openConversationContextMenu(row);
  },
});
document.addEventListener('pointerdown', event => {
  const row = event.target.closest?.('[data-conversation-row]');
  if (state.pdfPreview && !editing && !modifier && ['ArrowLeft', 'ArrowRight'].includes(event.key)) {
    event.preventDefault();
    if (!event.repeat) navigatePdfPage(event.key === 'ArrowRight' ? 1 : -1);
    return;
  }
  if (event.target.closest?.('.chat-row-actions')) return;
  if (!row) return;
  conversationLongPress.pointerDown(event, row.dataset.id);
}, true);
document.addEventListener('pointermove', event => { conversationLongPress.pointerMove(event); }, true);
document.addEventListener('pointerup', event => { conversationLongPress.pointerUp(event); }, true);
document.addEventListener('pointercancel', event => { conversationLongPress.pointerCancel(event); }, true);
document.addEventListener('click', event => {
  const row = event.target.closest?.('[data-conversation-row]');
  if (!row || !conversationLongPress.consumeClick(row.dataset.id)) return;
  event.preventDefault();
  event.stopImmediatePropagation();
}, true);
function closeConversationContextMenu() {
  if (!state.contextMenu) return;
  state.contextMenu = null;
  render();
}
document.addEventListener('pointerdown', event => {
  if (event.target.closest?.('.dialog, .chat-context-menu, .assistant-message-menu, .chat-profile-menu, .composer-transient-sheet, [data-overlay-trigger], [data-action="open-assistant-message-menu"]')) return;
  if (state.assistantMessageMenu) { event.preventDefault(); event.stopImmediatePropagation(); closeTopOverlay(); return; }
  if (state.videoPreview) { void closeVideoPreview(); return; }
  if (state.audioPreview) { void closeAudioPreview(); return; }
  if (state.textPreview) { state.textPreview = null; render(); return; }
  if (state.archivePreview) { state.archivePreview = null; render(); return; }
  closeTopOverlay();
}, true);
document.addEventListener('wheel', event => {
  if (!state.assistantMessageMenu) return;
  if (!event.target.closest?.('.chat-scroll[data-conversation-id]')) return;
  closeTopOverlay({ restoreFocus: false });
}, true);
document.addEventListener('keydown', event => { if (event.key === 'Escape' && state.videoPreview) { event.preventDefault(); void closeVideoPreview(); } else if (event.key === 'Escape' && state.audioPreview) { event.preventDefault(); void closeAudioPreview(); } else if (event.key === 'Escape' && state.textPreview) { event.preventDefault(); state.textPreview = null; render(); } else if (event.key === 'Escape' && state.archivePreview?.entry) { event.preventDefault(); state.archivePreview = { ...state.archivePreview, entry: null }; render(); } else if (event.key === 'Escape' && state.archivePreview) { event.preventDefault(); state.archivePreview = null; render(); } else if (event.key === 'Escape' && closeTopOverlay({ navigateComposerLayerBack: true })) event.preventDefault(); }, true);
function navigateImagePreview(direction) {
  const preview = state.imagePreview;
  if (!preview || preview.loading) return false;
  // A quick second swipe may arrive while the adjacent image is still being
  // decoded. Navigate from that requested target rather than dropping it.
  const activeAttachmentId = imagePreviewLoadRequest?.workspaceId === preview.workspaceId
    ? imagePreviewLoadRequest.attachmentId
    : preview.attachmentId;
  const targetId = imagePreviewTargetId(preview.imageIds, activeAttachmentId, direction);
  if (!targetId) return false;
  void openImagePreview(targetId, preview.workspaceId, preview.imageIds);
  return true;
}
window.addEventListener('keydown', event => {
  const modifier = event.metaKey || event.ctrlKey;
  const editing = ['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement?.tagName);
  const row = event.target.closest?.('[data-conversation-row]');
  if (state.imagePreview && !editing && (event.key === 'ArrowLeft' || event.key === 'ArrowRight')) { event.preventDefault(); if (!event.repeat) navigateImagePreview(event.key === 'ArrowRight' ? 'next' : 'previous'); }
  else if ((event.key === 'ContextMenu' || (event.shiftKey && event.key === 'F10')) && row) { event.preventDefault(); openConversationContextMenu(row); }
  else if (event.target.id === 'chat-composer' && event.key === 'Enter' && !event.shiftKey && !event.isComposing) { event.preventDefault(); saveLocalMessage(); }
  else if (modifier && event.key.toLowerCase() === 'f' && currentConversation() && !state.temporaryConversation) { event.preventDefault(); state.conversationFindOpen = true; recomputeConversationFind(); render(); focusCurrentFindMatch(); }
  else if (modifier && event.key.toLowerCase() === 's' && state.dialog?.kind) { event.preventDefault(); if (state.dialog.kind === 'project') saveProject(); else if (state.dialog.kind === 'knowledge') saveKnowledge(); else if (state.dialog.kind === 'memory') saveMemory(); }
  else if (modifier && event.key.toLowerCase() === 'z' && !editing) { event.preventDefault(); undo(event.shiftKey); }
  else if (modifier && event.key.toLowerCase() === 'o') { event.preventDefault(); pickExchange(); }
  else if (modifier && event.key.toLowerCase() === 'e') { event.preventDefault(); exportCurrent(); }
  else if (modifier && event.key === '\\') { event.preventDefault(); state.inspectorOpen = !state.inspectorOpen; render(); }
  else if (event.key === 'Escape' && (state.assistantMessageMenu || state.contextMenu || state.dialog || state.treeOpen || state.profileOpen || state.sidebarOpen)) { state.assistantMessageMenu = null; state.contextMenu = null; state.dialog = null; state.treeOpen = false; state.profileOpen = false; state.sidebarOpen = false; render(); }
});
window.addEventListener('resize', () => { if (state.contextMenu || state.assistantMessageMenu) { state.contextMenu = null; state.assistantMessageMenu = null; render(); } });
window.addEventListener('scroll', () => {
  // A residual momentum/programmatic scroll is not a fresh dismissal intent.
  // Fresh transcript wheel gestures are handled above before scrolling begins.
  if (state.contextMenu) positionConversationContextMenu();
  else conversationLongPress.cancel('scroll');
}, true);
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

function connectionCanvas() { const item = state.connection || previewConnection; return `<section class="canvas knowledge-canvas"><div class="canvas-header"><div><p class="overline">双路径 · 状态与配置边界</p><h1>连接与数据路径</h1><p>本地离线是安全底座；Provider 与加密同步必须分别配置、分别同意。</p></div></div><section class="knowledge-grid"><section><h2>本地离线</h2><p><b>LOCAL_OFFLINE / LOCAL_ONLY</b></p><p>始终可用。断网、取消或联网失败时，本地工作不丢失。</p></section><section><h2>联网模型</h2><p>配置：<b>${escape(item.providerConfiguration)}</b></p><p>Key presence：<b>${escape(item.credentialPresence)}</b>（仅存在性，不读取或显示 Key）</p><p>目录：${escape(item.catalogFreshness)} · consent：${escape(item.egressConsent)}</p><p>此处只显示双路径安全合同；实际模型设置会按服务商和凭据存在性另行读取。无论设置状态，联网请求仍需逐次确认，未经验证不会标记成功。</p></section><section class="memory-rail"><h2>账号同步</h2><p><b>ENCRYPTED_SYNC</b> · ${escape(item.syncCapability)}</p><p>同步与 Provider 独立；不会因模型设置自动上传本地数据。</p><p>当前原因：${item.degradedReasons.map(escape).join(' · ')}</p></section></section></section>`; }

if (native) invoke('read_dual_path_status').then(value => { state.connection = value; render(); }).catch(() => {});

function workHomeCanvas(data) {
  if (!data) return `<section class="canvas empty-canvas"><p class="overline">本地工作</p><h1>先导入一个工作区</h1><p>工作模式仍在同一浅色外壳中运行；导入不会合并或覆盖既有本地数据。</p><button class="primary" data-action="start-import">选择交换包</button></section>`;
  return `<section class="canvas work-home-canvas"><div class="canvas-header"><div><p class="overline">本地工作区</p><h1>${escape(data.summary.title)}</h1><p>项目、知识、记忆和本地受控记录都在这里管理；没有 Provider、同步或隐式外发。</p></div><button class="primary" data-action="start-import">导入工作区</button></div><div class="work-summary-grid"><article><span>项目</span><strong>${data.summary.projectCount}</strong><button data-action="show-projects">管理项目</button></article><article><span>知识</span><strong>${data.summary.knowledgeCount}</strong><button data-action="show-knowledge">打开知识</button></article><article><span>记忆</span><strong>${data.summary.memoryCount}</strong><button data-action="show-memory">打开记忆</button></article></div></section>`;
}

function projectsCanvas(data) {
  const projects = data?.exchange?.projects ?? [];
  return `<section class="canvas work-projects-canvas"><div class="workspace-content-actions"><button class="primary" data-action="new-project">新建项目</button></div><div class="knowledge-list">${projects.map(item => `<article class="knowledge-item"><header><div><p class="overline">${item.archived ? 'ARCHIVED' : 'ACTIVE'} · r${item.revision}</p><h2>${escape(item.title)}</h2></div></header><p>${escape(item.description || '暂无说明。')}</p><div class="item-actions"><button data-action="select-work-project" data-project-id="${escape(item.id)}">打开工作对话</button><button data-action="new-work-chat" data-project-id="${escape(item.id)}">新建工作对话</button><button data-action="edit-project" data-id="${escape(item.id)}">编辑</button></div></article>`).join('') || '<p class="empty-copy">暂无活动项目。</p>'}</div></section>`;
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
  return `<section class="canvas work-knowledge-canvas"><div class="workspace-content-actions"><button class="primary" data-action="new-knowledge">新建知识</button><button data-action="new-relation" ${knowledge.length < 2 ? 'disabled' : ''}>建立关系</button></div>${autoSection}<div class="knowledge-list">${knowledge.map(item => `<article class="knowledge-item"><header><div><p class="overline">${escape(item.status)} · r${item.revision} · ${escape(item.classification)}</p><h2>${escape(item.title)}</h2></div><span>${escape((item.tags || []).join(' · '))}</span></header><pre>${escape(item.body)}</pre><div class="item-actions"><button data-action="edit-knowledge" data-id="${escape(item.id)}">编辑</button><button data-action="delete-knowledge" data-id="${escape(item.id)}" data-revision="${item.revision}">软删除</button></div></article>`).join('') || '<p class="empty-copy">尚无已保存的本地知识。</p>'}</div><section class="memory-rail"><div class="section-head"><h2>Knowledge relation</h2><button data-action="new-relation" ${knowledge.length < 2 ? 'disabled' : ''}>建立</button></div><ul>${relations.map(item => `<li><span>${escape(item.kind)} · r${item.revision}</span>${escape(name(item.fromId))} → ${escape(name(item.toId))}<button data-action="delete-relation" data-id="${escape(item.id)}" data-revision="${item.revision}">撤销</button></li>`).join('') || '<li>暂无活动 relation。</li>'}</ul></section></section>`;
}

function memoryCanvas(data) {
  const memory = active(data || { exchange: { memory: [] } }, 'memory');
  return `<section class="canvas work-memory-canvas"><div class="workspace-content-actions"><button class="primary" data-action="new-memory">新建 Memory</button></div><section class="memory-rail"><ul>${memory.map(item => `<li><span>${escape(item.scope)} · r${item.revision}</span><button class="memory-button" data-action="edit-memory" data-id="${escape(item.id)}">${escape(item.body)}</button><button data-action="delete-memory" data-id="${escape(item.id)}" data-revision="${item.revision}">软删除</button></li>`).join('') || '<li>暂无活动 Memory。</li>'}</ul></section></section>`;
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
  clearModelCredentialDraftOutsideEditor();
  queueMicrotask(() => resizeComposer(document.getElementById('chat-composer')));
  const focusedComposer = document.activeElement?.id === 'chat-composer' ? document.activeElement : null;
  const composerSelection = focusedComposer ? [focusedComposer.selectionStart, focusedComposer.selectionEnd] : null;
  if (composerSelection) queueMicrotask(() => {
    if (state.dialog || state.contextMenu || state.assistantMessageMenu || state.composerAddOpen || state.p6gModelPickerOpen) return;
    const replacement = document.getElementById('chat-composer');
    if (replacement && !replacement.isSameNode(focusedComposer)) {
      replacement.focus({ preventScroll: true });
      replacement.setSelectionRange(...composerSelection);
    }
  });
  if (state.modelSettingsError || state.modelSettingsNotice) {
    state.dialog = { kind: 'model-settings-feedback', message: state.modelSettingsError || state.modelSettingsNotice, failed: Boolean(state.modelSettingsError) };
    state.modelSettingsError = '';
    state.modelSettingsNotice = '';
  }
  rememberChatScroll();
  rememberSidebarScroll();
  rememberSettingsScroll();
  const data = workspace();
  globalThis.__nanfengDesktopSearchState = state;
  const visiblePane = state.p6eAcceptance.enabled ? 'settings' : state.pane;
  document.title = state.p6eAcceptance.enabled ? '南枫 AI Desktop · P6-E 验收' : '南枫 AI Desktop';
  const workPanes = new Set(['work', 'projects', 'knowledge', 'memory', 'p8-inspect']);
  const utilityPane = ['reminders', 'transcription'].includes(visiblePane);
  const sidebarWorkMode = utilityPane ? false : workPanes.has(visiblePane);
  // Work is a scope of the selected conversation, not a dashboard.  Let the shared
  // chat shell render its transcript and Composer; only the selected mode changes.
  const workPanel = state.pane === 'projects' ? projectsCanvas(data)
    : state.pane === 'knowledge' ? knowledgeOnlyCanvas(data)
        : state.pane === 'memory' ? memoryCanvas(data)
          : state.pane === 'reminders' ? renderDesktopRemindersPage({ projection: state.reminders, notificationEnabled: Boolean(state.productSettings.monitorNotifications), notificationPermission: state.reminderNotificationPermission, native, previewInteractive: Boolean(c09ReminderPreview), evidenceLabel: c09ReminderPreview ? 'Web 只读交互样本不会写入 Desktop SQLite' : '', editor: state.reminderEditor, embedded: true })
            : state.pane === 'transcription' ? renderDesktopTranscriptionPage({ projection: state.transcription, selectedTaskId: state.selectedTranscriptionTaskId, native, busyTaskId: state.transcriptionBusyTaskId, previewInteractive: Boolean(c10TranscriptionPreview), evidenceLabel: c10TranscriptionPreview ? 'Web 只读交互样本不会写入 Desktop SQLite' : '', embedded: true })
          : state.pane === 'p8-inspect' ? p8InspectCanvas({ agentRuns: state.agentRuns, native, escape, short })
            : null;
  const standaloneWorkspacePage = (!utilityPane && Boolean(workPanel)) || isWorkspaceRoot(data, visiblePane, state.selectedWorkProjectId, state.selectedConversationId);
  const nextTranscriptRetention = currentTranscriptRetention(data);
  const existingTranscript = document.querySelector('.chat-scroll[data-conversation-id]');
  const preserveTranscript = canRetainTranscript(renderedTranscriptRetention, nextTranscriptRetention)
    && existingTranscript?.dataset.conversationId === nextTranscriptRetention.conversation.id;
  // Most shell interactions (menus, sidebar, dialogs and header controls) do
  // not change a transcript. Move that existing scroll owner out before the
  // shell commit and place it back into the new slot, so a long conversation is
  // neither serialized nor parsed again just because a surrounding control changed.
  const retainedTranscript = preserveTranscript ? existingTranscript : null;
  const retainedTranscriptTop = retainedTranscript?.scrollTop;
  const nextSidebarRetention = currentSidebarRetention(data);
  const existingSidebar = document.querySelector('.chat-sidebar');
  const preserveSidebar = canRetainSidebar(renderedSidebarRetention, nextSidebarRetention) && Boolean(existingSidebar);
  // A long imported history can contain hundreds of sidebar rows. Menus,
  // Composer sheets and dialogs do not alter those rows, so keep this owner in
  // place instead of serializing and parsing the whole list on every overlay.
  const retainedSidebar = preserveSidebar ? existingSidebar : null;
  const previewIdentity = state.imagePreview || state.pdfPreview || state.videoPreview || state.audioPreview || state.textPreview || state.archivePreview;
  const retainedPreview = !state.dialog && !state.cameraCaptureOpen && previewIdentity && previewIdentity === renderedAttachmentPreview
    ? app.querySelector('.attachment-preview-overlay') : null;
  const previewScrolls = retainedPreview ? [retainedPreview, ...retainedPreview.querySelectorAll('*')]
    .filter(node => node.scrollTop || node.scrollLeft)
    .map(node => ({ node, top: node.scrollTop, left: node.scrollLeft })) : [];
  if (retainedPreview) retainedPreview.remove();
  if (retainedTranscript) retainedTranscript.replaceWith(document.createComment('retained-transcript'));
  if (retainedSidebar) retainedSidebar.replaceWith(document.createComment('retained-sidebar'));
  app.className = `app-shell chat-first ${standaloneWorkspacePage ? 'workspace-standalone-shell' : ''} ${visiblePane === 'settings' ? 'settings-mode' : ''} ${sidebarWorkMode ? 'work-mode' : ''} ${state.sidebarOpen ? 'fallback-sidebar-open' : ''} ${state.searchPanel ? 'search-mode' : ''}`;
  app.innerHTML = renderChatFirstShell({ data, native, selectedConversationId: state.selectedConversationId, settingsConversationReturn: state.settingsConversationReturn, composerDraft: state.composerDraft, composerAttachments: state.composerAttachments, temporaryConversation: state.temporaryConversation, chatSearch: state.chatSearch, searchResults: state.searchResults, searchPanel: state.searchPanel, searchCategory: state.searchCategory, searchHistory: state.searchHistory, searchHistoryOpen: state.searchHistoryOpen, searchLoadingMore: state.searchLoadingMore, profileOpen: state.profileOpen, sidebarOpen: state.sidebarOpen, railCollapsed: state.railCollapsed, sidebarWidth: state.sidebarWidth, settingsSection: state.settingsSection, settingsSearch: state.settingsSearch, settingsPicker: state.settingsPicker, productSettings: state.productSettings, conversationPreferences: state.conversationPreferences, personalizationDraft: state.personalizationDraft, personalizationDirty: state.personalizationDirty, memorySummaryNotice: state.memorySummaryNotice, modelServiceSettings: state.modelServiceSettings, modelProviderId: state.modelProviderId, modelServiceDraft: state.modelServiceDraft, modelCredentialDraft: state.modelCredentialDraft, modelCredentialVisible: state.modelCredentialVisible, modelSettingsSaving: state.modelSettingsSaving, modelSettingsTesting: state.modelSettingsTesting, modelSettingsNotice: state.modelSettingsNotice, modelSettingsError: state.modelSettingsError, usageLedger: state.usageLedger, usageSection: state.usageSection, contextSelectionRecords: state.contextSelectionRecords, diagnosticRecords: state.diagnosticRecords, invocationRecords: state.invocationRecords, privacyInventory: state.privacyInventory, localBackup: state.localBackup, accountSync: state.accountSync, accountRecovery: state.accountRecovery, settingsCapabilities: state.settingsCapabilities, reminders: state.reminders, reminderNotificationPermission: state.reminderNotificationPermission, reminderNotificationBridge: state.reminderNotificationBridge, backgroundRuntime: state.backgroundRuntime, showArchived: state.showArchived, showDeleted: state.showDeleted, contextMenu: state.contextMenu, assistantMessageMenu: state.assistantMessageMenu, composerAddOpen: state.composerAddOpen, composerAddPage: state.composerAddPage, temporaryModelOpen: state.temporaryModelOpen, p6gModelPickerOpen: state.p6gModelPickerOpen, p6gCatalog: state.p6gCatalog, p6gGlobalDefault: state.p6gGlobalDefault, p6gSelection: state.p6gSelection, pendingNewConversationModelId: state.pendingNewConversationModelId, chatgptTask: state.chatgptTask, claudeTask: state.claudeTask, p6kTask: state.p6kTask, workMode: sidebarWorkMode, workspaces: state.workspaces, workPanel, pane: visiblePane, status: state.status, error: state.error, connection: state.connection, p6eAcceptance: state.p6eAcceptance, imageThumbnails: state.imageThumbnails, videoThumbnails: state.videoThumbnails, searchAttachmentPreviews: state.searchAttachmentPreviews, assistantImageSelections: state.assistantImageSelections, showScrollToLatest: !state.chatAtLatest, appearance: state.appearance, favoriteConversationIds: state.favoriteConversationIds, unreadConversationIds: state.unreadConversationIds, manualUnreadAtMs: state.manualUnreadAtMs, syncedConversationKeys: new Set(state.accountSync?.syncedConversationKeys || []), conversationFindOpen: state.conversationFindOpen, conversationFindQuery: state.conversationFindQuery, conversationFindMatches: state.conversationFindMatches, conversationFindIndex: state.conversationFindIndex, runtimeInfo: state.runtimeInfo, preserveTranscript: Boolean(retainedTranscript), preserveSidebar: Boolean(retainedSidebar) });
  scheduleComposerModelPricingRefresh();
  if (retainedTranscript) {
    const slot = app.querySelector('[data-preserved-transcript-slot]');
    if (slot) slot.replaceWith(retainedTranscript);
    retainedTranscript.scrollTop = retainedTranscriptTop;
  }
  if (retainedSidebar) {
    app.querySelector('[data-preserved-sidebar-slot]')?.replaceWith(retainedSidebar);
    patchRetainedSidebarRows();
  }
  bindAssistantMessageMenuTriggers();
  renderedTranscriptRetention = nextTranscriptRetention;
  renderedSidebarRetention = nextSidebarRetention;
  app.style.setProperty('--chat-sidebar-width', `${state.sidebarWidth}px`);
  positionConversationContextMenu();
  positionAssistantMessageMenu();
  if (state.contextMenu?.source === 'header') requestAnimationFrame(() => {
    if (state.contextMenu?.source === 'header') positionConversationContextMenu();
  });
  const modal = accountSyncProgressDialog() || cameraCaptureDialog() || dialog() || imagePreviewDialog() || pdfPreviewDialog() || videoPreviewDialog() || audioPreviewDialog() || textPreviewDialog() || archivePreviewDialog() || previewBoundaryDialog();
  if (retainedPreview) app.append(retainedPreview);
  else if (modal) app.insertAdjacentHTML('beforeend', modal);
  const attachmentMenu = app.querySelector('.search-attachment-menu');
  if (attachmentMenu && state.dialog?.anchor) {
    const bounds = attachmentMenu.getBoundingClientRect();
    const { x, y } = state.dialog.anchor;
    attachmentMenu.style.left = `${Math.max(12, Math.min(window.innerWidth - bounds.width - 12, x))}px`;
    attachmentMenu.style.top = `${Math.max(12, Math.min(window.innerHeight - bounds.height - 12, y))}px`;
  }
  for (const saved of previewScrolls) { saved.node.scrollTop = saved.top; saved.node.scrollLeft = saved.left; }
  renderedAttachmentPreview = previewIdentity;
  if (state.dialog?.kind === 'chatgpt-import' && !document.querySelector('[data-action="retry-chatgpt-task"]')) {
    document.querySelector('.dialog-actions')?.insertAdjacentHTML('afterbegin', '<button data-action="retry-chatgpt-task">重试解析</button>');
  }
  queueMicrotask(() => {
    // Reinserted scroll nodes may lose their scroll offset in WebKit.
    // Restore synchronously above; fresh nodes use the conversation owner here.
    // Explicit navigation must run after reinsertion, which cancels WebKit's
    // in-flight smooth scroll. Passive rerenders still preserve the viewport.
    if (!retainedTranscript || state.pendingChatScrollToLatestId || state.pendingChatSendScrollToLatestId) restoreChatScroll();
    restoreSidebarScroll();
    restoreSettingsScroll();
    if (state.searchPanel) restoreFullSearchScroll();
    if (state.dialog) document.querySelector('.dialog input, .dialog textarea, .dialog select, .dialog button')?.focus({ preventScroll: true });
    // The menu is rendered after the retained transcript.  WebKit's default
    // focus scroll can otherwise move the surrounding document to the menu's
    // DOM position even though the visible menu itself is fixed.
    else if (state.assistantMessageMenu) document.querySelector('.assistant-message-menu [role="menuitem"]')?.focus({ preventScroll: true });
    attachComposerCameraStream();
    scheduleImageThumbnailReads({ composerOnly: Boolean(retainedTranscript), retainObserved: Boolean(retainedTranscript) });
    // Retained transcript cards already belong to the active observers. The
    // Composer only has image cards; re-querying all video/document cards here
    // made opening a menu or sheet scale with the entire conversation length.
    if (!retainedTranscript) {
      scheduleVideoThumbnailReads();
      scheduleAttachmentCardPreviewReads();
    }
  });
}

function cameraCaptureDialog() {
  if (!state.cameraCaptureOpen) return '';
  const body = state.cameraCaptureError
    ? `<p class="camera-capture-error" role="alert">${escape(state.cameraCaptureError)}</p>`
    : `<div class="camera-capture-stage"><video data-composer-camera-video autoplay playsinline muted aria-label="当前相机画面"></video>${state.cameraCaptureReady ? '' : '<p role="status">正在请求本机相机；尚未拍摄或写入草稿。</p>'}</div>`;
  return `<div class="scrim"><section class="dialog camera-capture-dialog" role="dialog" aria-modal="true" aria-label="相机"><button class="icon-button close" data-action="close-composer-camera" aria-label="停止相机并关闭">${icon(icons.close, '停止相机')}</button><h2>相机</h2><p>只有点击“拍照并加入草稿”后，当前画面才会私有保存；不会自动发送。</p>${body}<div class="dialog-actions"><button data-action="close-composer-camera">取消</button><button class="primary" data-action="capture-composer-camera" ${state.cameraCaptureReady && !state.cameraCaptureBusy ? '' : 'disabled'}>${state.cameraCaptureBusy ? '正在私有保存…' : '拍照并加入草稿'}</button></div></section></div>`;
}

function previewTopActions(kind, preview, { copy = false } = {}) {
  const closeAction = {
    image: 'close-image-preview',
    pdf: 'close-pdf-preview',
    video: 'close-video-preview',
    audio: 'close-audio-preview',
    text: 'close-text-preview',
    archive: 'close-archive-preview',
  }[kind];
  const ready = Boolean(preview?.attachmentId && preview?.workspaceId && !preview?.loading && !preview?.error);
  return `<div class="file-preview-top-actions ${copy ? 'text-preview-actions' : 'media-preview-actions'}" aria-label="附件操作"><button class="file-preview-action" data-action="save-preview-attachment" data-preview-kind="${kind}" aria-label="下载文件" title="下载文件" ${ready ? '' : 'disabled'}>${icon(icons.download, '下载')}</button><button class="file-preview-action file-preview-share-action" data-action="share-preview-attachment" data-preview-kind="${kind}" aria-label="分享文件" title="分享文件" ${ready ? '' : 'disabled'}><span>分享</span></button>${copy ? `<button class="file-preview-action" data-action="copy-text-preview" data-copy-action aria-label="复制文本" title="复制文本" ${ready ? '' : 'disabled'}>${icon(icons.copy, '复制')}</button>` : ''}<button class="file-preview-action" data-action="${closeAction}" aria-label="关闭预览" title="关闭预览">${icon(icons.close, '关闭')}</button></div>`;
}

function imagePreviewDialog() {
  const preview = state.imagePreview;
  if (!preview) return '';
  const zoom = Math.max(1, Math.min(4, Number(preview.zoom) || 1));
  const navigation = imagePreviewNavigation(preview.imageIds, preview.attachmentId);
  const imageCount = (preview.imageIds || []).length;
  const galleryControls = imageCount > 1
    ? `<button class="image-preview-nav previous" data-action="image-preview-previous" ${navigation.previousId ? '' : 'disabled'} aria-label="上一张图片">${icon(icons.chevronLeft, '上一张')}</button><span class="image-preview-index" aria-live="polite">${navigation.index + 1} / ${imageCount}</span><button class="image-preview-nav next" data-action="image-preview-next" ${navigation.nextId ? '' : 'disabled'} aria-label="下一张图片">${icon(icons.chevronRight, '下一张')}</button>`
    : '';
  const navigationNotice = preview.navigationError ? `<span class="image-preview-navigation-error" role="alert">${escape(preview.navigationError)}</span>` : '';
  const content = preview.loading
    ? '<div class="media-preview-loading" role="status" aria-label="正在读取图片"></div>'
    : preview.error
      ? `<p class="image-preview-error">${escape(preview.error)}</p>`
      : `<figure><div class="image-preview-viewport ${zoom > 1 ? 'zoomed' : ''}" data-image-preview-viewport><img data-image-preview-content draggable="false" src="${escape(preview.dataUrl)}" alt="${escape(preview.displayName)} 原图预览" style="transform:translate(${Number(preview.panX) || 0}px, ${Number(preview.panY) || 0}px) scale(${zoom})">${galleryControls}${navigationNotice}</div></figure>`;
  return `<div class="scrim attachment-preview-overlay"><section class="dialog image-preview-dialog" role="dialog" aria-modal="true" aria-label="图片预览">${previewTopActions('image', preview)}${content}</section></div>`;
}

function pdfPreviewDialog() {
  const preview = state.pdfPreview;
  if (!preview) return '';
  const content = preview.loading
    ? '<div class="media-preview-loading" role="status" aria-label="正在读取 PDF"></div>'
    : preview.error
      ? `<p class="image-preview-error">${escape(preview.error)}</p>`
      : `<div class="pdf-preview-tools" aria-label="PDF 页码控制"><button data-action="pdf-page-previous" ${preview.pageNumber <= 1 ? 'disabled' : ''}>上一页</button><small>第 ${preview.pageNumber} / ${preview.pageCount} 页</small><button data-action="pdf-page-next" ${preview.pageNumber >= preview.pageCount ? 'disabled' : ''}>下一页</button></div><img class="pdf-preview-frame" src="${escape(preview.dataUrl)}" alt="${escape(preview.displayName)} 第 ${preview.pageNumber} 页">`;
  return `<div class="scrim attachment-preview-overlay"><section class="dialog image-preview-dialog pdf-preview-dialog" role="dialog" aria-modal="true" aria-label="PDF 阅读">${previewTopActions('pdf', preview)}${content}</section></div>`;
}

function previewProgressRange(kind, preview, label) {
  const duration = Math.max(1, Number(preview.durationMillis) || 1);
  const position = Math.max(0, Math.min(duration, Number(preview.positionMillis) || 0));
  const progress = Math.round((position / duration) * 10000) / 100;
  return `<input class="preview-progress-range" data-${kind}-preview-range type="range" min="0" max="${duration}" value="${position}" style="--preview-progress: ${progress}%" aria-label="${label}">`;
}

function previewVolumeControl(kind) {
  const label = kind === 'audio' ? '音频' : '视频';
  return `<span class="preview-volume-control"><button class="preview-volume-toggle" data-action="toggle-${kind}-volume" aria-label="调节${label}音量" aria-expanded="false" aria-controls="${kind}-preview-volume">${sharedIcon(sharedIcons.audio, '音量')}</button><span class="preview-volume-popover" id="${kind}-preview-volume" hidden><output class="preview-volume-value" data-${kind}-preview-volume-value>100%</output><input class="preview-volume-range" aria-orientation="vertical" data-${kind}-preview-volume type="range" min="0" max="100" value="100" style="--preview-progress: 100%" aria-label="${label}音量"></span></span>`;
}

function videoPreviewDialog() {
  const preview = state.videoPreview;
  if (!preview) return '';
  const content = preview.loading
    ? '<div class="media-preview-loading" role="status" aria-label="正在读取视频"></div>'
    : preview.error
      ? `<p class="image-preview-error">${escape(preview.error)}</p>`
      : `<div class="video-preview-stage"><video class="video-preview-frame" autoplay playsinline preload="auto" data-video-preview data-action="toggle-video-playback" src="${escape(preview.mediaUrl)}" aria-label="${escape(preview.displayName)} 视频播放器"></video><div class="preview-playback-controls video-preview-controls" aria-label="视频播放控制"><button class="preview-playback-toggle" data-action="toggle-video-playback" aria-label="暂停视频">${icon('M7 5h3v14H7zM14 5h3v14h-3z', '暂停')}</button><time data-video-preview-elapsed>${formatDuration(preview.positionMillis || 0)}</time>${previewProgressRange('video', preview, '视频播放进度')}${previewVolumeControl('video')}<time data-video-preview-duration>${formatDuration(preview.durationMillis)}</time></div></div><p class="pdf-preview-note">${escape(preview.displayName)} · ${bytes(preview.byteCount)} · ${formatDuration(preview.durationMillis)}</p>`;
  return `<div class="scrim attachment-preview-overlay"><section class="dialog image-preview-dialog" role="dialog" aria-modal="true" aria-label="视频预览">${previewTopActions('video', preview)}${content}</section></div>`;
}

function audioPreviewDialog() {
  const preview = state.audioPreview;
  if (!preview) return '';
  const content = preview.loading ? '<div class="media-preview-loading" role="status" aria-label="正在读取本地音频"></div>'
    : preview.error ? `<p class="image-preview-error">${escape(preview.error)}</p>`
      : `<div class="audio-preview-player"><audio autoplay preload="auto" data-audio-preview src="${escape(preview.mediaUrl)}" aria-label="${escape(preview.displayName)} 音频播放器"></audio><button class="audio-preview-toggle" data-action="toggle-audio-playback" aria-label="暂停音频">${icon('M7 5h3v14H7zM14 5h3v14h-3z', '暂停')}</button><div class="audio-preview-progress-row">${previewProgressRange('audio', preview, '音频播放进度')}${previewVolumeControl('audio')}</div><div class="audio-preview-times"><time data-audio-preview-elapsed>${formatDuration(preview.positionMillis || 0)}</time><time data-audio-preview-duration>${formatDuration(preview.durationMillis || 0)}</time></div></div>`;
  const identity = !preview.loading && !preview.error
    ? `<div class="audio-preview-identity"><strong>${escape(preview.displayName)}</strong><small>${escape(String(preview.mimeType || '').split('/').pop()?.toUpperCase() || '音频')} · ${bytes(preview.byteCount)}</small></div>`
    : '';
  return `<div class="scrim attachment-preview-overlay audio-preview-overlay"><section class="dialog image-preview-dialog audio-preview-dialog" role="dialog" aria-modal="true" aria-label="音频播放"><header class="audio-preview-header">${identity}${previewTopActions('audio', preview)}</header>${content}</section></div>`;
}

function textPreviewDialog() {
  const preview = state.textPreview;
  if (!preview) return '';
  const facts = !preview.loading && !preview.error
    ? `<p class="pdf-preview-note text-preview-facts">${escape(preview.displayName)} · ${escape(preview.mimeType)} · ${bytes(preview.byteCount)}${preview.truncated ? ' · 仅显示前 128 KiB' : ''}</p>`
    : '';
  const content = preview.loading ? '<div class="media-preview-loading" role="status" aria-label="正在读取文本"></div>'
    : preview.error ? `<p class="image-preview-error">${escape(preview.error)}</p>`
      : /[.。]\s*zip\s*$/i.test(preview.displayName || '') || ['application/zip', 'application/x-zip-compressed'].includes(preview.mimeType)
        ? `<article class="local-text-preview" aria-label="压缩包目录"><pre>${escape(preview.text)}</pre></article>`
        : `<article class="local-text-preview local-markdown-preview chat-markdown">${renderSafeMarkdown(preview.text, { inert: true })}</article>`;
  return `<div class="scrim attachment-preview-overlay text-preview-overlay"><section class="dialog image-preview-dialog text-preview-surface" role="dialog" aria-modal="true" aria-label="文本预览"><header class="text-preview-header">${facts}${previewTopActions('text', preview, { copy: true })}</header>${content}</section></div>`;
}

function archiveDirectoryRows(entries, directory = '') {
  const prefix = directory ? `${directory.replace(/\/+$/, '')}/` : '';
  const children = new Map();
  for (const entry of entries || []) {
    const path = String(entry.path || '');
    if (!path.startsWith(prefix)) continue;
    const remaining = path.slice(prefix.length);
    if (!remaining) continue;
    const slash = remaining.indexOf('/');
    if (slash >= 0) {
      const name = remaining.slice(0, slash);
      const childPath = `${prefix}${name}/`;
      if (!children.has(childPath)) children.set(childPath, { path: childPath, displayName: name, isDirectory: true, byteCount: 0, previewKind: 'directory' });
    } else {
      children.set(path, entry);
    }
  }
  return [...children.values()].sort((left, right) => Number(Boolean(right.isDirectory)) - Number(Boolean(left.isDirectory)) || String(left.displayName).localeCompare(String(right.displayName), 'zh-Hans-CN'));
}

function archiveBreadcrumb(directory) {
  const parts = String(directory || '').split('/').filter(Boolean);
  let path = '';
  const crumbs = [`<button data-action="open-archive-directory" data-archive-path="">压缩包</button>`];
  for (const part of parts) { path += `${part}/`; crumbs.push(`<span aria-hidden="true">/</span><button data-action="open-archive-directory" data-archive-path="${escape(path)}">${escape(part)}</button>`); }
  return `<nav class="archive-preview-breadcrumb" aria-label="压缩包路径">${crumbs.join('')}</nav>`;
}

function archiveEntryContent(entry) {
  if (entry.loading) return '<div class="media-preview-loading" role="status" aria-label="正在读取压缩包内文件"></div>';
  if (entry.error) return `<p class="image-preview-error">${escape(entry.error)}</p>`;
  const facts = `<p class="pdf-preview-note archive-entry-facts">${escape(entry.displayName)} · ${escape(entry.mimeType || '未知类型')} · ${bytes(entry.byteCount)}${entry.truncated ? ' · 仅显示前 128 KiB' : ''}</p>`;
  if (entry.kind === 'text') return `${facts}<article class="local-text-preview archive-text-preview" aria-label="${escape(entry.displayName)}"><pre>${escape(entry.text || '')}</pre></article>`;
  if (entry.kind === 'image') return `${facts}<figure class="archive-image-preview"><div class="image-preview-viewport"><img src="${escape(entry.dataUrl || '')}" alt="${escape(entry.displayName)}"></div></figure>`;
  if (entry.kind === 'pdf') return `${facts}<div class="pdf-preview-tools" aria-label="ZIP 内 PDF 页码控制"><button data-action="archive-pdf-page-previous" ${entry.pageNumber <= 1 ? 'disabled' : ''}>上一页</button><small>第 ${entry.pageNumber} / ${entry.pageCount} 页</small><button data-action="archive-pdf-page-next" ${entry.pageNumber >= entry.pageCount ? 'disabled' : ''}>下一页</button></div><img class="pdf-preview-frame" src="${escape(entry.dataUrl || '')}" alt="${escape(entry.displayName)} 第 ${entry.pageNumber} 页">`;
  return `${facts}<section class="archive-preview-unsupported"><strong>此文件已保留在压缩包中</strong><p>当前类型没有经过验证的应用内预览器，未解压到磁盘、未交给系统应用，也未上传。</p></section>`;
}

function archivePreviewDialog() {
  const preview = state.archivePreview;
  if (!preview) return '';
  const facts = !preview.loading && !preview.error ? `<p class="pdf-preview-note text-preview-facts">${escape(preview.displayName)} · ${escape(preview.mimeType)} · ${bytes(preview.byteCount)}</p>` : '';
  const content = preview.loading ? '<div class="media-preview-loading" role="status" aria-label="正在读取压缩包目录"></div>'
    : preview.error ? `<p class="image-preview-error">${escape(preview.error)}</p>`
      : preview.entry ? `<header class="archive-preview-entry-header"><button data-action="archive-preview-back" aria-label="返回压缩包目录">${icon(icons.chevronLeft, '返回')}</button>${archiveBreadcrumb(preview.directory)}</header>${archiveEntryContent(preview.entry)}`
        : (() => {
          const rows = archiveDirectoryRows(preview.entries, preview.directory);
          return `<header class="archive-preview-directory-header">${archiveBreadcrumb(preview.directory)}<small>${rows.length} 项</small></header><section class="archive-preview-list" aria-label="ZIP 文件列表">${rows.map(entry => entry.isDirectory ? `<button class="archive-preview-row archive-directory-row" data-action="open-archive-directory" data-archive-path="${escape(entry.path)}"><span class="archive-preview-row-icon">${icon(icons.workspace, '目录')}</span><span><strong>${escape(entry.displayName)}</strong><small>文件夹</small></span>${icon(icons.chevronRight, '进入')}</button>` : `<button class="archive-preview-row" data-action="open-archive-entry-preview" data-archive-entry-path="${escape(entry.path)}"><span class="archive-preview-row-icon">${icon(icons.knowledge, '文件')}</span><span><strong>${escape(entry.displayName)}</strong><small>${escape(entry.previewKind === 'unsupported' ? '暂不支持预览' : entry.previewKind.toUpperCase())} · ${bytes(entry.byteCount)}</small></span>${icon(icons.chevronRight, '预览')}</button>`).join('') || '<p class="archive-preview-empty">此目录为空。</p>'}</section>`;
        })();
  return `<div class="scrim attachment-preview-overlay archive-preview-overlay"><section class="dialog image-preview-dialog text-preview-surface archive-preview-surface" role="dialog" aria-modal="true" aria-label="压缩包浏览"><header class="text-preview-header">${facts}${previewTopActions('archive', preview)}</header>${content}</section></div>`;
}

function previewBoundaryDialog() {
  const preview = state.previewBoundary;
  if (!preview) return '';
  const browserBoundary = preview.source === 'browser'
    ? '浏览器只验证搜索、分类、排序、应用内入口和返回状态；没有读取或伪造附件字节。原生预览须由隔离 Tauri 数据根验证。'
    : preview.reason;
  return `<div class="scrim attachment-preview-overlay"><section class="dialog image-preview-dialog preview-boundary-dialog" role="dialog" aria-modal="true" aria-label="应用内安全预览边界"><button class="icon-button close" data-action="close-preview-boundary" aria-label="关闭应用内安全预览边界">${icon(icons.close, '关闭')}</button><h2>应用内安全预览边界</h2><p class="pdf-preview-note"><strong>${escape(preview.displayName || '本地附件')}</strong>${preview.mimeType ? ` · ${escape(preview.mimeType)}` : ''}</p><p class="image-preview-error">${escape(browserBoundary || '当前无法安全预览；文件留在应用内且未外发。')}</p><p class="pdf-preview-note">关闭后返回原搜索分类、关键词、排序与结果位置。</p></section></div>`;
}

function formatDuration(milliseconds) { const seconds = Math.max(0, Math.floor((Number(milliseconds) || 0) / 1000)); return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`; }

let imageThumbnailObserver = null;
let imageThumbnailQueue = [];
let imageThumbnailInFlight = 0;
function pumpImageThumbnailReads() {
  while (imageThumbnailInFlight < 2 && imageThumbnailQueue.length) {
    const job = imageThumbnailQueue.shift();
    imageThumbnailInFlight += 1;
    job().finally(() => { imageThumbnailInFlight -= 1; pumpImageThumbnailReads(); });
  }
}
function scheduleImageThumbnailReads({ composerOnly = false, retainObserved = false } = {}) {
  // A retained transcript keeps both its cards and this observer alive. A
  // surrounding menu/dialog render may only create a fresh Composer; do not
  // rescan several hundred preserved attachment nodes just to observe it.
  if (!retainObserved) {
    imageThumbnailObserver?.disconnect();
    imageThumbnailObserver = null;
    imageThumbnailQueue.forEach(job => state.imageThumbnailPending.delete(job.thumbnailKey));
    imageThumbnailQueue = [];
  }
  if (!native || state.imagePreview) return;
  const selector = composerOnly ? '.chat-composer [data-image-thumbnail]' : '[data-image-thumbnail]';
  const enqueue = element => {
    const attachmentId = element.dataset.imageThumbnail;
    const workspaceId = element.dataset.thumbnailWorkspaceId || state.current?.summary?.id;
    const thumbnailKey = `${workspaceId || 'unknown'}:${attachmentId || 'unknown'}`;
    if (!workspaceId || !attachmentId) return;
    if (state.imageThumbnails[attachmentId]) { hydrateImageThumbnail(attachmentId, state.imageThumbnails[attachmentId]); return; }
    if (state.imageThumbnailPending.has(thumbnailKey)) return;
    state.imageThumbnailPending.add(thumbnailKey);
    const job = () => {
      if (!element.isConnected) { state.imageThumbnailPending.delete(thumbnailKey); return Promise.resolve(); }
      return withFullSearchDeadline(invoke('read_desktop_image_preview', { args: { workspaceId, attachmentId, fullSize: false } }))
      .then(preview => {
        state.imageThumbnails = { ...state.imageThumbnails, [attachmentId]: preview };
        hydrateImageThumbnail(attachmentId, preview);
      })
      .catch(() => {
        const preview = { error: '本地缩略图不可用' };
        state.imageThumbnails = { ...state.imageThumbnails, [attachmentId]: preview };
        hydrateImageThumbnail(attachmentId, preview);
      })
      .finally(() => { state.imageThumbnailPending.delete(thumbnailKey); });
    };
    job.thumbnailKey = thumbnailKey;
    imageThumbnailQueue.push(job);
    pumpImageThumbnailReads();
  };
  const elements = [...document.querySelectorAll(selector)];
  if (!('IntersectionObserver' in globalThis)) { elements.slice(0, 12).forEach(enqueue); return; }
  if (!imageThumbnailObserver) {
    imageThumbnailObserver = new IntersectionObserver(entries => {
      for (const entry of entries) if (entry.isIntersecting) {
        imageThumbnailObserver?.unobserve(entry.target);
        enqueue(entry.target);
      }
    }, { root: state.searchPanel ? document.querySelector('[data-search-scroll-owner]') : null, rootMargin: '160px' });
  }
  elements.forEach(element => imageThumbnailObserver.observe(element));
}

function hydrateImageThumbnail(attachmentId, preview) {
  if (!preview) return;
  document.querySelectorAll('[data-image-thumbnail]').forEach(element => {
    if (element.dataset.imageThumbnail !== String(attachmentId)) return;
    const placeholder = element.querySelector('.chat-image-placeholder, .chat-assistant-image-gallery-placeholder, .chat-composer-image-placeholder, .desktop-search-image-preview > .desktop-search-file-preview');
    if (!placeholder) return;
    if (preview.error) {
      placeholder.textContent = '图片读取失败，点击重试';
      element.dataset.retryImageThumbnail = String(attachmentId);
      return;
    }
    if (!preview.dataUrl) return;
    delete element.dataset.retryImageThumbnail;
    const image = document.createElement('img');
    image.src = preview.dataUrl;
    image.alt = element.classList.contains('chat-assistant-image-gallery-thumb') ? '' : (element.getAttribute('aria-label') || '本地图片预览');
    placeholder.replaceWith(image);
  });
}

app.addEventListener('click', event => {
  const element = event.target.closest?.('[data-retry-image-thumbnail]');
  if (!element) return;
  event.preventDefault(); event.stopImmediatePropagation();
  delete state.imageThumbnails[element.dataset.retryImageThumbnail];
  delete element.dataset.retryImageThumbnail;
  const placeholder = element.querySelector('.chat-image-placeholder, .chat-assistant-image-gallery-placeholder, .chat-composer-image-placeholder');
  if (placeholder) placeholder.textContent = '正在读取本地缩略图';
  scheduleImageThumbnailReads();
}, true);

let videoThumbnailObserver = null;
const MAX_VIDEO_THUMBNAIL_READS = 2;
let videoThumbnailReadQueue = [];
let videoThumbnailReadsInFlight = 0;
let videoThumbnailGeneration = 0;

function pumpVideoThumbnailReads() {
  while (videoThumbnailReadsInFlight < MAX_VIDEO_THUMBNAIL_READS && videoThumbnailReadQueue.length) {
    const job = videoThumbnailReadQueue.shift();
    if (job.generation !== videoThumbnailGeneration) {
      state.videoThumbnailPending.delete(job.thumbnailKey);
      continue;
    }
    videoThumbnailReadsInFlight += 1;
    invoke('read_desktop_video_thumbnail', { args: { workspaceId: job.workspaceId, attachmentId: job.attachmentId } })
      .then(preview => {
        state.videoThumbnails = { ...state.videoThumbnails, [job.attachmentId]: preview };
        hydrateVideoThumbnail(job.attachmentId, preview);
      })
      .catch(() => {
        state.videoThumbnails = { ...state.videoThumbnails, [job.attachmentId]: { error: '本地视频缩略图不可用' } };
      })
      .finally(() => {
        videoThumbnailReadsInFlight = Math.max(0, videoThumbnailReadsInFlight - 1);
        state.videoThumbnailPending.delete(job.thumbnailKey);
        pumpVideoThumbnailReads();
      });
  }
}

function scheduleVideoThumbnailReads() {
  videoThumbnailObserver?.disconnect();
  videoThumbnailObserver = null;
  videoThumbnailGeneration += 1;
  videoThumbnailReadQueue.forEach(job => state.videoThumbnailPending.delete(job.thumbnailKey));
  videoThumbnailReadQueue = [];
  if (!native || state.videoPreview) return;
  const cards = [...document.querySelectorAll('[data-video-thumbnail]')];
  if (!cards.length) return;
  const generation = videoThumbnailGeneration;
  const enqueue = element => {
    const attachmentId = element.dataset.videoThumbnail;
    const workspaceId = element.dataset.thumbnailWorkspaceId || state.current?.summary?.id;
    const thumbnailKey = `${workspaceId || 'unknown'}:${attachmentId || 'unknown'}`;
    if (!workspaceId || !attachmentId || state.videoThumbnails[attachmentId] || state.videoThumbnailPending.has(thumbnailKey)) return;
    state.videoThumbnailPending.add(thumbnailKey);
    videoThumbnailReadQueue.push({ attachmentId, workspaceId, thumbnailKey, generation });
    pumpVideoThumbnailReads();
  };
  if (!('IntersectionObserver' in globalThis)) {
    cards.slice(0, 9).forEach(enqueue);
    return;
  }
  const root = state.searchPanel
    ? document.querySelector('[data-search-scroll-owner]')
    : document.querySelector('.chat-scroll[data-scroll-owner="message-list"]');
  videoThumbnailObserver = new IntersectionObserver(entries => {
    entries.filter(entry => entry.isIntersecting).forEach(entry => {
      videoThumbnailObserver?.unobserve(entry.target);
      enqueue(entry.target);
    });
  }, { root, rootMargin: '160px 0px' });
  cards.forEach(card => videoThumbnailObserver.observe(card));
}

function hydrateVideoThumbnail(attachmentId, preview) {
  if (!preview?.dataUrl) return;
  document.querySelectorAll('[data-video-thumbnail]').forEach(element => {
    if (element.dataset.videoThumbnail !== String(attachmentId)) return;
    const placeholder = element.querySelector('.desktop-search-video-preview > .desktop-search-file-preview, .chat-video-frame > .chat-file-preview-fallback');
    if (!placeholder) return;
    const image = document.createElement('img');
    image.src = preview.dataUrl;
    image.alt = element.getAttribute('aria-label') || `${element.querySelector('.desktop-search-video-facts strong')?.textContent || '视频'}预览图`;
    placeholder.replaceWith(image);
  });
}

let searchAttachmentPreviewObserver = null;
let searchAttachmentPreviewQueue = [];
let searchAttachmentPreviewReadsInFlight = 0;
let searchAttachmentPreviewGeneration = 0;
const MAX_SEARCH_ATTACHMENT_PREVIEW_READS = 2;

function hydrateAttachmentCardPreview(attachmentId, preview) {
  document.querySelectorAll('[data-search-attachment-preview], [data-chat-attachment-preview]').forEach(element => {
    const requestedId = element.dataset.searchAttachmentPreview || element.dataset.chatAttachmentPreview;
    if (requestedId !== String(attachmentId)) return;
    if (Number(preview?.durationMillis) > 0) {
      const duration = Math.floor(Number(preview.durationMillis) / 1000);
      const time = element.querySelector('.desktop-search-audio-heading time, .chat-audio-heading time');
      if (time) time.textContent = `${Math.floor(duration / 60)}:${String(duration % 60).padStart(2, '0')}`;
    }
    const container = element.querySelector('.desktop-search-text-file-preview, .desktop-search-pdf-preview, .desktop-search-generic-file-preview, .chat-text-content-preview, .chat-pdf-content-preview, .chat-file-format-preview');
    if (!container) return;
    if (preview?.dataUrl) {
      const isMainCard = container.classList.contains('chat-attachment-visual');
      container.className = isMainCard
        ? 'chat-attachment-visual chat-pdf-content-preview'
        : 'desktop-search-visual-preview desktop-search-pdf-preview';
      const image = document.createElement('img');
      image.src = preview.dataUrl;
      image.alt = element.getAttribute('aria-label') || `${element.querySelector('.desktop-search-card-details strong')?.textContent || 'PDF'}首页预览`;
      container.replaceChildren(image);
      return;
    }
    if (preview?.text) {
      const existingFormat = container.querySelector('.chat-file-format-label')?.textContent || '';
      const name = element.dataset.attachmentName || element.querySelector('.desktop-search-card-details strong')?.textContent || '';
      const extension = existingFormat || name.split('.').pop()?.toUpperCase() || 'TXT';
      const isMainCard = container.classList.contains('chat-attachment-visual');
      container.className = isMainCard
        ? 'chat-attachment-visual chat-text-content-preview'
        : 'desktop-search-visual-preview desktop-search-text-file-preview';
      const label = document.createElement('b');
      if (isMainCard) label.className = 'chat-file-format-label';
      label.textContent = extension === 'MARKDOWN' ? 'MD' : extension.slice(0, 5);
      const content = document.createElement('pre');
      content.textContent = preview.text;
      container.replaceChildren(label, content);
    }
  });
}

function pumpSearchAttachmentPreviewReads() {
  while (searchAttachmentPreviewReadsInFlight < MAX_SEARCH_ATTACHMENT_PREVIEW_READS && searchAttachmentPreviewQueue.length) {
    const job = searchAttachmentPreviewQueue.shift();
    if (job.generation !== searchAttachmentPreviewGeneration) {
      state.searchAttachmentPreviewPending.delete(job.previewKey);
      continue;
    }
    searchAttachmentPreviewReadsInFlight += 1;
    invoke('read_desktop_search_attachment_preview', { args: { workspaceId: job.workspaceId, attachmentId: job.attachmentId } })
      .then(preview => {
        state.searchAttachmentPreviews = { ...state.searchAttachmentPreviews, [job.attachmentId]: preview };
        hydrateAttachmentCardPreview(job.attachmentId, preview);
      })
      .catch(() => {
        state.searchAttachmentPreviews = { ...state.searchAttachmentPreviews, [job.attachmentId]: { error: '本地附件预览不可用' } };
      })
      .finally(() => {
        searchAttachmentPreviewReadsInFlight = Math.max(0, searchAttachmentPreviewReadsInFlight - 1);
        state.searchAttachmentPreviewPending.delete(job.previewKey);
        pumpSearchAttachmentPreviewReads();
      });
  }
}

function scheduleAttachmentCardPreviewReads() {
  searchAttachmentPreviewObserver?.disconnect();
  searchAttachmentPreviewObserver = null;
  searchAttachmentPreviewGeneration += 1;
  searchAttachmentPreviewQueue.forEach(job => state.searchAttachmentPreviewPending.delete(job.previewKey));
  searchAttachmentPreviewQueue = [];
  if (!native) return;
  const cards = [...document.querySelectorAll('[data-search-attachment-preview], [data-chat-attachment-preview]')];
  if (!cards.length) return;
  const generation = searchAttachmentPreviewGeneration;
  const enqueue = element => {
    const attachmentId = element.dataset.searchAttachmentPreview || element.dataset.chatAttachmentPreview;
    const workspaceId = element.dataset.thumbnailWorkspaceId || state.current?.summary?.id;
    const previewKey = `${workspaceId || 'unknown'}:${attachmentId || 'unknown'}`;
    if (!workspaceId || !attachmentId || state.searchAttachmentPreviews[attachmentId] || state.searchAttachmentPreviewPending.has(previewKey)) return;
    state.searchAttachmentPreviewPending.add(previewKey);
    searchAttachmentPreviewQueue.push({ attachmentId, workspaceId, previewKey, generation });
    pumpSearchAttachmentPreviewReads();
  };
  if (!('IntersectionObserver' in globalThis)) {
    cards.slice(0, 12).forEach(enqueue);
    return;
  }
  const root = state.searchPanel
    ? document.querySelector('[data-search-scroll-owner]')
    : document.querySelector('.chat-scroll[data-scroll-owner="message-list"]');
  searchAttachmentPreviewObserver = new IntersectionObserver(entries => {
    entries.filter(entry => entry.isIntersecting).forEach(entry => {
      searchAttachmentPreviewObserver?.unobserve(entry.target);
      enqueue(entry.target);
    });
  }, { root, rootMargin: '160px 0px' });
  cards.forEach(card => searchAttachmentPreviewObserver.observe(card));
}

async function openAttachmentPreview(attachment, workspaceId = state.current?.summary?.id) {
  const attachmentId = attachment?.attachmentId || attachment?.id;
  if (!attachmentId) return;
  const capability = attachmentPreviewCapability(attachment);
  if (!native || !capability.supported) {
    state.previewBoundary = {
      attachmentId,
      workspaceId,
      displayName: attachment.displayName || '本地附件',
      mimeType: attachment.mimeType || '',
      reason: capability.reason,
      source: native ? 'native' : 'browser',
    };
    render();
    return;
  }
  if (capability.kind === 'image') return openImagePreview(attachmentId, workspaceId);
  if (capability.kind === 'video') return openVideoPreview(attachmentId, undefined, workspaceId);
  if (capability.kind === 'audio') return openAudioPreview(attachmentId, undefined, workspaceId);
  if (capability.kind === 'pdf') return openPdfPreview(attachmentId, undefined, workspaceId);
  if (capability.kind === 'text') return openTextPreview(attachmentId, workspaceId);
  if (capability.kind === 'archive') return openArchivePreview(attachmentId, workspaceId);
}

const imagePreviewPayloadCache = new Map();
const MAX_IMAGE_PREVIEW_PAYLOAD_CACHE_ENTRIES = 2;
let imagePreviewLoadRequest = null;

function imagePreviewPayloadKey(workspaceId, attachmentId) {
  return `${workspaceId}:${attachmentId}`;
}

function retainImagePreviewPayload(key, payload) {
  imagePreviewPayloadCache.delete(key);
  imagePreviewPayloadCache.set(key, payload);
  while (imagePreviewPayloadCache.size > MAX_IMAGE_PREVIEW_PAYLOAD_CACHE_ENTRIES) {
    imagePreviewPayloadCache.delete(imagePreviewPayloadCache.keys().next().value);
  }
}

async function decodeImagePreviewPayload(dataUrl) {
  // Do not replace the visible image until WebKit has decoded the next frame.
  // Swapping a freshly-created <img> before decode caused the visible flash.
  if (!dataUrl || typeof Image === 'undefined') return;
  const image = new Image();
  image.decoding = 'async';
  const loaded = new Promise((resolve, reject) => {
    image.onload = resolve;
    image.onerror = reject;
  });
  image.src = dataUrl;
  if (typeof image.decode === 'function') {
    try { await image.decode(); return; } catch (_) { /* fall through to load */ }
  }
  await loaded;
}

function loadImagePreviewPayload(workspaceId, attachmentId) {
  const key = imagePreviewPayloadKey(workspaceId, attachmentId);
  const cached = imagePreviewPayloadCache.get(key);
  if (cached) {
    retainImagePreviewPayload(key, cached);
    return cached;
  }
  const request = invoke('read_desktop_image_preview', { args: { workspaceId, attachmentId, fullSize: true } })
    .then(async result => {
      await decodeImagePreviewPayload(result.dataUrl);
      return result;
    })
    .catch(error => {
      if (imagePreviewPayloadCache.get(key) === request) imagePreviewPayloadCache.delete(key);
      throw error;
    });
  retainImagePreviewPayload(key, request);
  return request;
}

function warmImagePreviewNeighbors(preview) {
  if (!preview?.dataUrl || !preview.workspaceId || !preview.attachmentId) return;
  const navigation = imagePreviewNavigation(preview.imageIds, preview.attachmentId);
  const neighbors = [navigation.nextId, navigation.previousId].filter(Boolean);
  if (!neighbors.length) return;
  const warm = () => {
    if (state.imagePreview?.attachmentId !== preview.attachmentId || state.imagePreview?.workspaceId !== preview.workspaceId) return;
    neighbors.forEach(attachmentId => { void loadImagePreviewPayload(preview.workspaceId, attachmentId).catch(() => {}); });
  };
  if (typeof requestIdleCallback === 'function') requestIdleCallback(warm, { timeout: 700 });
  else setTimeout(warm, 0);
}

function cancelImagePreviewLoad() {
  imagePreviewLoadRequest = null;
}

async function openImagePreview(attachmentId, workspaceId = state.current?.summary?.id, imageIds = relatedImageIds(currentConversation(), attachmentId)) {
  if (!native || !workspaceId || !attachmentId) return;
  const normalizedAttachmentId = String(attachmentId);
  const normalizedImageIds = Array.isArray(imageIds) ? imageIds.map(String) : [];
  const safeImageIds = normalizedImageIds.includes(normalizedAttachmentId) ? normalizedImageIds : [normalizedAttachmentId];
  if (imagePreviewLoadRequest?.attachmentId === normalizedAttachmentId && imagePreviewLoadRequest.workspaceId === workspaceId) return;
  const previousPreview = state.imagePreview?.dataUrl ? state.imagePreview : null;
  const request = { attachmentId: normalizedAttachmentId, workspaceId, imageIds: safeImageIds };
  imagePreviewLoadRequest = request;
  // Keep the current decoded frame in place while the next original is read.
  // Initial opening still has an honest loading state.
  if (!previousPreview) {
    state.imagePreview = { loading: true, ...request };
    render();
  }
  try {
    const result = await loadImagePreviewPayload(workspaceId, normalizedAttachmentId);
    if (imagePreviewLoadRequest !== request) return;
    imagePreviewLoadRequest = null;
    state.imagePreview = { ...result, ...request, zoom: 1, panX: 0, panY: 0, navigationError: '' };
  } catch (error) {
    if (imagePreviewLoadRequest !== request) return;
    imagePreviewLoadRequest = null;
    // A failed adjacent read must not blank the image already being viewed.
    state.imagePreview = previousPreview
      ? { ...previousPreview, navigationError: '相邻图片不可用或校验失败。' }
      : { error: '本地原图不可用或校验失败。', ...request };
  }
  render();
  if (state.imagePreview?.dataUrl) warmImagePreviewNeighbors(state.imagePreview);
}

let imagePanGesture = null;
let imagePreviewHorizontalSwipe = { attachmentId: '', deltaX: 0, lastAt: 0, lockUntil: 0, axis: null, consumed: false };
const imagePreviewHorizontalSwipeThreshold = 36;
const imagePreviewHorizontalSwipeIdleMillis = 160;
const imagePreviewHorizontalSwipeAxisRatio = 1.25;
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
function consumeImagePreviewHorizontalSwipe(event, viewport) {
  const preview = state.imagePreview;
  if (!preview || (Number(preview.zoom) || 1) > 1 || event.ctrlKey) return false;
  event.preventDefault();
  const now = performance.now();
  if (imagePreviewHorizontalSwipe.attachmentId !== preview.attachmentId || now - imagePreviewHorizontalSwipe.lastAt > imagePreviewHorizontalSwipeIdleMillis) {
    imagePreviewHorizontalSwipe = { attachmentId: preview.attachmentId, deltaX: 0, lastAt: now, lockUntil: 0, axis: null, consumed: false };
  }
  imagePreviewHorizontalSwipe.lastAt = now;
  if (imagePreviewHorizontalSwipe.consumed) return true;
  const horizontal = Math.abs(event.deltaX);
  const vertical = Math.abs(event.deltaY);
  if (!imagePreviewHorizontalSwipe.axis) {
    if (horizontal < 1 && vertical < 1) return true;
    imagePreviewHorizontalSwipe.axis = horizontal > vertical * imagePreviewHorizontalSwipeAxisRatio ? 'horizontal' : 'vertical';
  }
  // Once classified, a wheel gesture has one owner. A diagonal horizontal
  // swipe can therefore never fall through to the zoom path.
  if (imagePreviewHorizontalSwipe.axis !== 'horizontal') return true;
  imagePreviewHorizontalSwipe.deltaX += event.deltaX;
  if (Math.abs(imagePreviewHorizontalSwipe.deltaX) < imagePreviewHorizontalSwipeThreshold) return true;
  const direction = imagePreviewHorizontalSwipe.deltaX > 0 ? 'next' : 'previous';
  imagePreviewHorizontalSwipe.deltaX = 0;
  imagePreviewHorizontalSwipe.lockUntil = now + 280;
  imagePreviewHorizontalSwipe.consumed = true;
  navigateImagePreview(direction);
  return true;
}
app.addEventListener('wheel', event => {
  const viewport = event.target.closest?.('[data-image-preview-viewport], .image-preview-dialog');
  if (!viewport || !state.imagePreview) return;
  if (consumeImagePreviewHorizontalSwipe(event, viewport)) return;
  // macOS WebKit exposes a two-finger pinch as Ctrl-wheel. Ordinary wheel
  // motion is not zoom, so a horizontal swipe cannot magnify the image.
  event.preventDefault();
  if (!event.ctrlKey) return;
  if (!state.imagePreview.dataUrl) return;
  const current = Number(state.imagePreview.zoom) || 1;
  const zoom = Math.max(1, Math.min(4, current + (event.deltaY < 0 ? 0.25 : -0.25)));
  state.imagePreview = { ...state.imagePreview, zoom, panX: zoom === 1 ? 0 : state.imagePreview.panX || 0, panY: zoom === 1 ? 0 : state.imagePreview.panY || 0 };
  applyImagePreviewTransform(viewport);
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

function navigatePdfPage(step) {
  const preview = state.pdfPreview;
  if (!preview?.attachmentId || preview.loading) return;
  const page = preview.pageNumber + step;
  if (page < 1 || page > preview.pageCount) return;
  void openPdfPreview(preview.attachmentId, page, preview.workspaceId);
}
let pdfSwipe = { lastAt: 0, delta: 0, consumed: false };
app.addEventListener('wheel', event => {
  if (!state.pdfPreview || !event.target.closest?.('.pdf-preview-surface, .pdf-preview-dialog') || event.ctrlKey || Math.abs(event.deltaX) <= Math.abs(event.deltaY)) return;
  event.preventDefault();
  const now = performance.now();
  if (now - pdfSwipe.lastAt > 250) pdfSwipe = { lastAt: now, delta: 0, consumed: false };
  pdfSwipe.lastAt = now;
  if (pdfSwipe.consumed) return;
  pdfSwipe.delta += event.deltaX;
  if (Math.abs(pdfSwipe.delta) >= 60) {
    pdfSwipe.consumed = true;
    navigatePdfPage(pdfSwipe.delta > 0 ? 1 : -1);
  }
}, { passive: false });

async function openVideoPreview(attachmentId, positionMillis = undefined, workspaceId = state.current?.summary?.id) {
  if (!native || !workspaceId || !attachmentId) return;
  const request = {};
  state.videoPreview = { loading: true, request }; render();
  try {
    const preview = await invoke('read_desktop_video_preview', { args: { workspaceId, attachmentId, positionMillis } });
    if (state.videoPreview?.request !== request) return;
    if (!preview?.attachmentId || !preview?.mediaUrl || !Number.isFinite(Number(preview.byteCount)) || !Number.isFinite(Number(preview.durationMillis))) throw new Error('本地视频预览状态无效');
    state.videoPreview = { ...preview, workspaceId };
  }
  catch (error) { if (state.videoPreview?.request !== request) return; state.videoPreview = { error: '本地视频不可用、已损坏或校验失败。' }; }
  render();
  const restorePosition = Math.max(0, Number(state.videoPreview?.positionMillis) || 0) / 1000;
  queueMicrotask(() => {
    const video = document.querySelector('[data-video-preview]');
    if (!video) return;
    const syncVideoUi = () => syncPreviewPlaybackUi('video', video);
    const restoreAndPlay = async () => {
      if (restorePosition > 0) video.currentTime = Math.min(restorePosition, Math.max(0, video.duration || restorePosition));
      try { await video.play(); }
      catch (_) { video.dataset.autoplayFailed = 'true'; }
      syncVideoUi();
    };
    if (video.readyState >= 1) void restoreAndPlay();
    else video.addEventListener('loadedmetadata', () => { void restoreAndPlay(); }, { once: true });
    video?.addEventListener('timeupdate', () => {
      if (!video.isConnected || state.videoPreview?.attachmentId !== attachmentId || state.videoPreview?.workspaceId !== workspaceId) return;
      const milliseconds = Math.round(video.currentTime * 1000);
      syncVideoUi();
      if (milliseconds > 0 && milliseconds < Math.round(video.duration * 1000)) {
        state.videoPreview = { ...state.videoPreview, lastPlaybackPositionMillis: milliseconds };
      }
    });
    video.addEventListener('play', syncVideoUi);
    video.addEventListener('pause', syncVideoUi);
  });
}

async function closeVideoPreview() {
  const video = document.querySelector('[data-video-preview]');
  const preview = state.videoPreview;
  const currentPosition = Math.round((video?.currentTime || 0) * 1000);
  const positionMillis = preview?.lastPlaybackPositionMillis || currentPosition;
  state.videoPreview = null;
  render();
  if (!preview?.attachmentId || !preview.workspaceId) return;
  try { await invoke('save_desktop_video_preview_position', { args: { workspaceId: preview.workspaceId, attachmentId: preview.attachmentId, positionMillis } }); }
  catch (_) { /* Closing an overlay never waits for a non-critical resume write. */ }
}

async function openAudioPreview(attachmentId, positionMillis = undefined, workspaceId = state.current?.summary?.id) {
  if (!native || !workspaceId || !attachmentId) return;
  const request = {};
  state.audioPreview = { loading: true, request }; render();
  try {
    const preview = await invoke('read_desktop_audio_preview', { args: { workspaceId, attachmentId, positionMillis } });
    if (state.audioPreview?.request !== request) return;
    if (!preview?.attachmentId || !preview?.mediaUrl) throw new Error('本地音频预览状态无效');
    state.audioPreview = { ...preview, workspaceId };
  }
  catch (_) { if (state.audioPreview?.request !== request) return; state.audioPreview = { error: '本地音频不可用、已损坏或校验失败。' }; }
  render();
  queueMicrotask(() => {
    const audio = document.querySelector('[data-audio-preview]');
    const syncAudioUi = () => syncPreviewPlaybackUi('audio', audio);
    const seekAndPlay = async () => {
      if (!audio) return;
      audio.currentTime = Math.min(Math.max(0, Number(state.audioPreview?.positionMillis) || 0) / 1000, Math.max(0, audio.duration || 0));
      try { await audio.play(); }
      catch (_) { audio.dataset.autoplayFailed = 'true'; }
      syncAudioUi();
    };
    if (audio?.readyState >= 1) void seekAndPlay(); else audio?.addEventListener('loadedmetadata', () => { void seekAndPlay(); }, { once: true });
    audio?.addEventListener('timeupdate', () => { if (!audio.isConnected || state.audioPreview?.attachmentId !== attachmentId || state.audioPreview?.workspaceId !== workspaceId) return; const ms = Math.round(audio.currentTime * 1000); syncAudioUi(); if (ms > 0 && ms < Math.round(audio.duration * 1000)) state.audioPreview = { ...state.audioPreview, lastPlaybackPositionMillis: ms }; });
    audio?.addEventListener('play', syncAudioUi);
    audio?.addEventListener('pause', syncAudioUi);
    audio?.addEventListener('error', () => {
      const active = state.audioPreview;
      if (!active || active.attachmentId !== attachmentId) return;
      state.audioPreview = { ...active, mediaUrl: '', error: '当前系统无法解码此音频。' };
      render();
    }, { once: true });
  });
}

function syncPreviewPlaybackUi(kind, media) {
  if (!media || !media.isConnected || document.querySelector(`[data-${kind}-preview]`) !== media) return;
  const currentMillis = Math.max(0, Math.round((Number(media.currentTime) || 0) * 1000));
  const durationMillis = Math.max(1, Math.round((Number(media.duration) || 0) * 1000));
  const range = document.querySelector(`[data-${kind}-preview-range]`);
  const elapsed = document.querySelector(`[data-${kind}-preview-elapsed]`);
  const duration = document.querySelector(`[data-${kind}-preview-duration]`);
  const toggle = document.querySelector(`button[data-action="toggle-${kind}-playback"]`);
  if (range) {
    const positionMillis = Math.min(currentMillis, durationMillis);
    range.max = String(durationMillis);
    range.value = String(positionMillis);
    range.style.setProperty('--preview-progress', `${Math.round((positionMillis / durationMillis) * 10000) / 100}%`);
  }
  const volumeRange = document.querySelector(`[data-${kind}-preview-volume]`);
  if (volumeRange) {
    const volume = Math.round(Math.max(0, Math.min(1, Number(media.volume) || 0)) * 100);
    volumeRange.value = String(media.muted ? 0 : volume);
    const volumeValue = document.querySelector(`[data-${kind}-preview-volume-value]`);
    if (volumeValue) volumeValue.textContent = `${media.muted ? 0 : volume}%`;
    volumeRange.style.setProperty('--preview-progress', `${media.muted ? 0 : volume}%`);
  }
  if (elapsed) elapsed.textContent = formatDuration(currentMillis);
  if (duration) duration.textContent = formatDuration(durationMillis);
  if (toggle) {
    const paused = media.paused;
    toggle.setAttribute('aria-label', paused ? `播放${kind === 'audio' ? '音频' : '视频'}` : `暂停${kind === 'audio' ? '音频' : '视频'}`);
    toggle.innerHTML = paused ? icon('M7 5v14l11-7z', '播放') : icon('M7 5h3v14H7zM14 5h3v14h-3z', '暂停');
  }
}

async function togglePreviewPlayback(kind) {
  const media = document.querySelector(`[data-${kind}-preview]`);
  if (!media) return;
  if (media.paused) { try { await media.play(); } catch (_) { media.dataset.autoplayFailed = 'true'; } }
  else media.pause();
  syncPreviewPlaybackUi(kind, media);
}

async function closeAudioPreview() {
  const audio = document.querySelector('[data-audio-preview]'); const preview = state.audioPreview;
  if (!preview) return;
  const positionMillis = preview.lastPlaybackPositionMillis || Math.round((audio?.currentTime || 0) * 1000);
  state.audioPreview = null;
  render();
  if (!preview.attachmentId || !preview.workspaceId) return;
  try { await invoke('save_desktop_audio_preview_position', { args: { workspaceId: preview.workspaceId, attachmentId: preview.attachmentId, positionMillis } }); }
  catch (_) { /* Closing an overlay never depends on a non-critical resume write. */ }
}

async function openTextPreview(attachmentId, workspaceId = state.current?.summary?.id) {
  if (!native || !workspaceId || !attachmentId) return;
  state.textPreview = { loading: true }; render();
  try { state.textPreview = { ...await invoke('read_desktop_text_preview', { args: { workspaceId, attachmentId } }), workspaceId }; }
  catch (_) { state.textPreview = { error: '本地文本不可用、编码无效或校验失败。' }; }
  render();
}

async function openArchivePreview(attachmentId, workspaceId = state.current?.summary?.id) {
  if (!native || !workspaceId || !attachmentId) return;
  state.archivePreview = { loading: true, attachmentId, workspaceId, directory: '', entry: null }; render();
  try { state.archivePreview = { ...await invoke('read_desktop_archive_preview', { args: { workspaceId, attachmentId } }), workspaceId, directory: '', entry: null }; }
  catch (_) { state.archivePreview = { error: '本地压缩包不可用、结构无效或校验失败。' }; }
  render();
}

async function openArchiveEntryPreview(entryPath, pageNumber = 1) {
  const preview = state.archivePreview;
  if (!native || !preview?.workspaceId || !preview?.attachmentId || !entryPath) return;
  state.archivePreview = { ...preview, entry: { loading: true } }; render();
  try {
    const entry = await invoke('read_desktop_archive_entry_preview', { args: { workspaceId: preview.workspaceId, attachmentId: preview.attachmentId, entryPath, pageNumber } });
    state.archivePreview = { ...state.archivePreview, entry };
  } catch (_) {
    state.archivePreview = { ...state.archivePreview, entry: { error: 'ZIP 内文件不可用、路径不安全、内容损坏或超过预览上限。' } };
  }
  render();
}

function safeAttachmentSaveName(value) {
  const leaf = String(value || '附件').split(/[\\/]/).pop() || '附件';
  return leaf.replace(/[\u0000-\u001f:]/g, '-').slice(0, 180) || '附件';
}

async function saveAttachmentCopy({ workspaceId, attachmentId, displayName }) {
  if (!native || !workspaceId || !attachmentId) return false;
  const defaultPath = safeAttachmentSaveName(displayName);
  const extension = defaultPath.includes('.') ? defaultPath.split('.').pop().toLowerCase() : '';
  const selectedPath = await dialogInvoke('save', { defaultPath, ...(extension ? { filters: [{ name: '附件副本', extensions: [extension] }] } : {}) });
  if (!selectedPath) return false;
  await invoke('export_desktop_attachment_to_selected_path', { args: { workspaceId, attachmentId, selectedPath } });
  state.status = `已保存经 hash 回读的附件副本“${defaultPath}”。`;
  state.error = '';
  return true;
}

function activePreviewForKind(kind) {
  return ({ image: state.imagePreview, pdf: state.pdfPreview, video: state.videoPreview, audio: state.audioPreview, text: state.textPreview, archive: state.archivePreview })[kind] || null;
}

async function sharePreviewAttachment(preview) {
  if (!preview?.workspaceId || !preview?.attachmentId) throw new Error('附件引用无效');
  if (native) {
    await invoke('share_desktop_attachment', { args: { workspaceId: preview.workspaceId, attachmentId: preview.attachmentId } });
    return;
  }
  if (typeof navigator.share !== 'function') throw new Error('当前预览环境不支持系统分享');
  await navigator.share({ title: preview.displayName, ...(preview.text ? { text: preview.text } : {}) });
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

const COPY_SUCCESS_DURATION_MS = 1200;

function showMessageCopyFeedback(button) {
  showCopyIconFeedback(button);
}

function showPreviewActionError(button, message) {
  if (!button?.isConnected) return;
  const owner = button.closest('.file-preview-top-actions');
  if (!owner) return;
  owner.querySelector('.preview-action-error')?.remove();
  const notice = document.createElement('span');
  notice.className = 'preview-action-error';
  notice.setAttribute('role', 'alert');
  notice.textContent = message;
  owner.append(notice);
}

function showCopyIconFeedback(button) {
  if (!button?.isConnected) return;
  const prior = button.__copyIconFeedback || {
    html: button.innerHTML,
    ariaLabel: button.getAttribute('aria-label'),
    title: button.getAttribute('title'),
  };
  const token = Symbol('copy-success');
  button.__copyIconFeedback = prior;
  button.__copyIconFeedbackToken = token;
  button.classList.add('is-copy-success');
  button.setAttribute('aria-label', '已复制');
  button.setAttribute('title', '已复制');
  button.innerHTML = icon(icons.check, '已复制') + (prior.html.includes('code-copy-label') ? '<span class="code-copy-label">已复制</span>' : '');
  window.setTimeout(() => {
    if (!button.isConnected || button.__copyIconFeedbackToken !== token) return;
    button.innerHTML = prior.html;
    if (prior.ariaLabel == null) button.removeAttribute('aria-label'); else button.setAttribute('aria-label', prior.ariaLabel);
    if (prior.title == null) button.removeAttribute('title'); else button.setAttribute('title', prior.title);
    button.classList.remove('is-copy-success');
    delete button.__copyIconFeedback;
    delete button.__copyIconFeedbackToken;
  }, COPY_SUCCESS_DURATION_MS);
}

let chatAttachmentMenuSuppressUntil = 0;
app.addEventListener('click', event => {
  if (!event.target.matches?.('.search-attachment-menu-backdrop')) return;
  event.preventDefault(); event.stopImmediatePropagation();
  state.dialog = null; render();
}, true);
function openChatAttachmentMenu(card) {
  const attachmentId = card.dataset.attachmentId;
  const messageId = card.closest('[data-message-id]')?.dataset.messageId;
  const message = currentConversation()?.messages?.find(item => item.id === messageId);
  const asset = message?.blocks?.map(block => block.asset).find(item => item?.id === attachmentId);
  if (!asset) return;
  const mime = asset.mimeType || '';
  const category = mime.startsWith('image/') ? 'image' : mime.startsWith('video/') ? 'video' : mime.startsWith('audio/') ? 'audio' : 'file';
  const duration = state.videoThumbnails?.[attachmentId]?.durationMillis || state.searchAttachmentPreviews?.[attachmentId]?.durationMillis;
  const item = { workspaceId: state.current.summary.id, conversationId: currentConversation()?.id, messageId, attachmentId, displayName: asset.displayName || '附件', category,
    details: [({ image: '图片', video: '视频', audio: '音频', file: '文件' })[category], duration ? formatDuration(duration) : '', bytes(Number(asset.byteCount) || 0)].filter(Boolean).join(' · '),
    sentAt: message.createdAt ? `发送于 ${new Date(message.createdAt).toLocaleString('zh-CN', { month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit' })}` : '' };
  state.dialog = { kind: 'chat-attachment-actions', item, anchor: attachmentMenuAnchor(card) };
  render();
}
app.addEventListener('click', event => {
  if (Date.now() < chatAttachmentMenuSuppressUntil && event.target.closest?.('[data-attachment-id]')) { event.preventDefault(); event.stopImmediatePropagation(); }
}, true);
let searchAttachmentLongPressTimer = null;
let searchAttachmentLongPressTriggered = null;
let searchAttachmentPressOrigin = null;
app.addEventListener('contextmenu', event => {
  const chatCard = event.target.closest?.('.chat-message [data-attachment-id][data-action]');
  if (chatCard) { event.preventDefault(); event.stopPropagation(); cancelSearchAttachmentLongPress(); openChatAttachmentMenu(chatCard); return; }
  const card = event.target.closest?.('.desktop-search-attachment-card');
  if (!card) return;
  event.preventDefault();
  event.stopPropagation();
  cancelSearchAttachmentLongPress();
  const entryId = card.dataset.searchEntryId;
  const hit = fullSearchHit(entryId);
  if (!hit) return;
  rememberFullSearchScroll(entryId);
  state.dialog = { kind: 'search-attachment-actions', hit, anchor: attachmentMenuAnchor(card) };
  render();
});
function cancelSearchAttachmentLongPress() {
  if (searchAttachmentLongPressTimer !== null) window.clearTimeout(searchAttachmentLongPressTimer);
  searchAttachmentLongPressTimer = null;
}
app.addEventListener('pointerdown', event => {
  const chatCard = event.target.closest?.('.chat-message [data-attachment-id][data-action]');
  if (chatCard && event.button === 0) {
    cancelSearchAttachmentLongPress();
    searchAttachmentPressOrigin = { x: event.clientX, y: event.clientY };
    searchAttachmentLongPressTimer = window.setTimeout(() => { searchAttachmentLongPressTimer = null; chatAttachmentMenuSuppressUntil = Date.now() + 800; openChatAttachmentMenu(chatCard); }, 520);
    return;
  }
  const card = event.target.closest?.('.desktop-search-attachment-card');
  if (!card || event.button !== 0 || event.target.closest('.desktop-search-card-menu')) return;
  cancelSearchAttachmentLongPress();
  searchAttachmentPressOrigin = { x: event.clientX, y: event.clientY };
  const entryId = card.dataset.searchEntryId;
  searchAttachmentLongPressTimer = window.setTimeout(() => {
    searchAttachmentLongPressTimer = null;
    const hit = fullSearchHit(entryId);
    if (!hit) return;
    searchAttachmentLongPressTriggered = entryId;
    rememberFullSearchScroll(entryId);
    state.dialog = { kind: 'search-attachment-actions', hit, anchor: attachmentMenuAnchor(card) };
    render();
  }, 520);
});
app.addEventListener('pointerup', cancelSearchAttachmentLongPress);
app.addEventListener('pointercancel', cancelSearchAttachmentLongPress);
app.addEventListener('pointermove', event => {
  if (searchAttachmentPressOrigin && Math.hypot(event.clientX - searchAttachmentPressOrigin.x, event.clientY - searchAttachmentPressOrigin.y) > 10) cancelSearchAttachmentLongPress();
});

const render = renderUnified;

let ordinaryComposerDraftQueue = Promise.resolve();
let ordinaryComposerDraftTimer = null;
let scheduledOrdinaryComposerDraft = null;
let composerDraftRouteGeneration = 0;
function ordinaryComposerDraftArgs() {
  if (!native || state.temporaryConversation || !state.current) return null;
  return {
    workspaceId: state.current.summary.id,
    conversationId: state.selectedConversationId || null,
    text: state.composerDraft,
    attachmentIds: state.composerAttachments.map(item => item.id),
  };
}
function persistOrdinaryComposerDraft() {
  const args = arguments.length ? arguments[0] : ordinaryComposerDraftArgs();
  if (!args) return Promise.resolve();
  const save = ordinaryComposerDraftQueue.catch(() => {}).then(() => invoke('save_desktop_conversation_draft', { args }));
  ordinaryComposerDraftQueue = save;
  return save;
}
function resetComposerPresentation() {
  composerDraftRouteGeneration += 1;
  state.composerDraft = '';
  state.composerAttachments = [];
}
function resetNewConversationComposer() {
  resetComposerPresentation();
  const workspaceId = workspace()?.summary?.id;
  if (!native || !workspaceId || state.temporaryConversation || state.selectedConversationId) return;
  // A user explicitly entering a new chat abandons only the unbound native draft. Browser
  // storage is deliberately not a recovery owner, so it cannot repopulate this route later.
  void persistOrdinaryComposerDraft({ workspaceId, conversationId: null, text: '', attachmentIds: [] }).catch(error => {
    state.error = `新对话草稿未清除：${String(error)}`;
    render();
  });
}
function scheduleOrdinaryComposerDraft() {
  const args = ordinaryComposerDraftArgs();
  if (!args) return;
  scheduledOrdinaryComposerDraft = args;
  if (ordinaryComposerDraftTimer) clearTimeout(ordinaryComposerDraftTimer);
  ordinaryComposerDraftTimer = setTimeout(() => {
    ordinaryComposerDraftTimer = null;
    const scheduled = scheduledOrdinaryComposerDraft;
    scheduledOrdinaryComposerDraft = null;
    void persistOrdinaryComposerDraft(scheduled);
  }, 180);
}
async function flushOrdinaryComposerDraft() {
  if (ordinaryComposerDraftTimer) {
    clearTimeout(ordinaryComposerDraftTimer);
    ordinaryComposerDraftTimer = null;
  }
  const scheduled = scheduledOrdinaryComposerDraft;
  scheduledOrdinaryComposerDraft = null;
  if (scheduled) {
    await persistOrdinaryComposerDraft(scheduled);
  }
  await ordinaryComposerDraftQueue;
}
async function loadOrdinaryComposerDraft() {
  const args = ordinaryComposerDraftArgs();
  if (!args) return;
  const routeGeneration = ++composerDraftRouteGeneration;
  const draft = await invoke('read_desktop_conversation_draft', { workspaceId: args.workspaceId, conversationId: args.conversationId });
  if (routeGeneration !== composerDraftRouteGeneration || state.current?.summary?.id !== args.workspaceId || (state.selectedConversationId || null) !== args.conversationId) return;
  if (!draft) {
    state.composerDraft = '';
    state.composerAttachments = [];
    return;
  }
  state.composerDraft = draft.text || '';
  state.composerAttachments = Array.isArray(draft.attachments) ? draft.attachments : [];
}
function writeComposerAttachments() { scheduleOrdinaryComposerDraft(); }

// Composer drafts have exactly one route owner. This listener runs after the route action has
// selected its destination and never consults browser storage, eliminating click-order races.
app.addEventListener('click', event => {
  const action = event.target.closest?.('[data-action]')?.dataset.action;
  if (action === 'new-chat') {
    void flushOrdinaryComposerDraft();
    resetNewConversationComposer();
    render();
    return;
  }
  if (action !== 'select-chat') return;
  resetComposerPresentation();
  render();
  void loadOrdinaryComposerDraft().then(render).catch(error => {
    state.error = `普通会话草稿未读取：${String(error)}`;
    render();
  });
});

const MAX_COMPOSER_ATTACHMENT_BYTES = 20 * 1024 * 1024;
const PREFERRED_CLIPBOARD_IMAGE_TYPES = ['image/png', 'image/jpeg', 'image/webp'];
const CLIPBOARD_IMAGE_EXTENSIONS = { 'image/png': 'png', 'image/jpeg': 'jpg', 'image/webp': 'webp' };

function clipboardAttachmentName(file, index) {
  const supplied = String(file?.name || '').trim();
  if (supplied && !/[\\/]/.test(supplied)) return supplied;
  const extension = ({
    'image/png': 'png', 'image/jpeg': 'jpg', 'image/webp': 'webp', 'application/pdf': 'pdf',
    'video/mp4': 'mp4', 'video/webm': 'webm', 'audio/mpeg': 'mp3', 'audio/wav': 'wav',
  })[String(file?.type || '').toLowerCase()] || 'bin';
  return `粘贴附件-${Date.now()}-${index + 1}.${extension}`;
}

async function clipboardFileBase64(file) {
  if (!(file instanceof Blob) || !file.size || file.size > MAX_COMPOSER_ATTACHMENT_BYTES) throw new Error('附件必须是小于 20 MB 的普通文件');
  const dataUrl = await new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(new Error('无法读取剪贴板文件'));
    reader.onload = () => resolve(String(reader.result || ''));
    reader.readAsDataURL(file);
  });
  const encoded = String(dataUrl).split(',', 2)[1] || '';
  if (!encoded) throw new Error('剪贴板文件编码无效');
  return encoded;
}

function pastedComposerFiles(clipboardData) {
  const seen = new Set();
  const files = [];
  for (const item of [...(clipboardData?.items || [])]) {
    if (item.kind !== 'file') continue;
    const file = item.getAsFile?.();
    if (file && !seen.has(file)) { seen.add(file); files.push(file); }
  }
  for (const file of [...(clipboardData?.files || [])]) {
    if (file && !seen.has(file)) { seen.add(file); files.push(file); }
  }
  return files;
}

async function importPastedComposerClipboardFiles(clipboardData, fallbackFiles) {
  let files = fallbackFiles;
  // macOS frequently exposes one copied image as TIFF, AVIF, JPEG and PNG at once. Prefer the
  // standard raster representation while handling this exact paste gesture; a failed read keeps
  // the concrete File delivered by the paste event as the only fallback.
  if (fallbackFiles.some(file => String(file?.type || '').toLowerCase().startsWith('image/')) && navigator.clipboard?.read) {
    try {
      const items = await navigator.clipboard.read();
      for (const item of items) {
        const type = PREFERRED_CLIPBOARD_IMAGE_TYPES.find(candidate => item.types.includes(candidate));
        if (!type) continue;
        const blob = await item.getType(type);
        if (blob.size > 0 && blob.size <= MAX_COMPOSER_ATTACHMENT_BYTES) {
          files = [new File([blob], `粘贴图片-${Date.now()}.${CLIPBOARD_IMAGE_EXTENSIONS[type]}`, { type })];
          break;
        }
      }
    } catch {
      // The event-provided File remains valid when a WebView denies Clipboard.read().
    }
  }
  await importComposerClipboardFiles(files);
}

async function importComposerClipboardFiles(files) {
  if (!native || (!state.current && !state.temporaryConversation)) { state.error = native ? '先进入临时聊天或导入本地工作区。' : 'Web 预览不能私有复制粘贴附件。'; render(); return; }
  const currentCount = state.temporaryConversation ? (state.temporaryConversation.draftAttachmentIds || []).length : state.composerAttachments.length;
  const accepted = files.slice(0, Math.max(0, 4 - currentCount));
  if (!accepted.length) { state.error = '每条本地消息最多保留 4 个附件。'; render(); return; }
  const failures = [];
  let imported = 0;
  for (const [index, file] of accepted.entries()) {
    try {
      const bytesBase64 = await clipboardFileBase64(file);
      const displayName = clipboardAttachmentName(file, index);
      if (state.temporaryConversation) {
        state.temporaryConversation = await invoke('import_desktop_temporary_clipboard_attachment', { args: { temporaryId: state.temporaryConversation.temporaryId, displayName, bytesBase64 } });
        state.composerDraft = state.temporaryConversation.draft || state.composerDraft;
      } else {
        const attachment = await invoke('import_desktop_conversation_clipboard_attachment', { args: { workspaceId: state.current.summary.id, displayName, bytesBase64 } });
        if (!state.composerAttachments.some(item => item.sha256 === attachment.sha256)) state.composerAttachments = [...state.composerAttachments, attachment];
        writeComposerAttachments();
      }
      imported += 1;
    } catch (error) { failures.push(`${clipboardAttachmentName(file, index)}：${String(error)}`); }
  }
  state.status = imported ? `已将 ${imported} 个粘贴附件私有复制到当前草稿；尚未发送。${files.length > accepted.length ? ' 其余附件超过本条 4 个上限，未加入。' : ''}` : '';
  state.error = failures.length ? `以下粘贴附件未加入草稿：${failures.join('；')}` : '';
  render();
}

function writeChatDraft(value) {
  // Keystrokes must not synchronously touch localStorage or enqueue an IPC call.
  // SQLite remains the recovery owner; submit flushes this short debounce window.
  scheduleOrdinaryComposerDraft();
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

let temporaryDraftQueue = Promise.resolve();
let temporaryDraftTimer = null;
let scheduledTemporaryDraft = null;
function temporaryDraftArgs(value = state.composerDraft) {
  if (!state.temporaryConversation) return null;
  return { temporaryId: state.temporaryConversation.temporaryId, draft: value, modelOverrideId: state.temporaryConversation.modelOverrideId || null };
}
function persistTemporaryDraft(args) {
  if (!args) return Promise.resolve();
  const save = temporaryDraftQueue.catch(() => {}).then(async () => {
    const updated = await invoke('update_desktop_temporary_conversation', { args });
    // A delayed response must never replace newer in-memory input.
    if (state.temporaryConversation?.temporaryId === args.temporaryId && state.composerDraft === args.draft) state.temporaryConversation = updated;
  });
  temporaryDraftQueue = save;
  return save;
}
function scheduleTemporaryDraft(value) {
  const args = temporaryDraftArgs(value);
  if (!args) return;
  state.composerDraft = value;
  scheduledTemporaryDraft = args;
  if (temporaryDraftTimer) clearTimeout(temporaryDraftTimer);
  temporaryDraftTimer = setTimeout(() => {
    temporaryDraftTimer = null;
    const scheduled = scheduledTemporaryDraft;
    scheduledTemporaryDraft = null;
    void persistTemporaryDraft(scheduled);
  }, 180);
}
async function flushTemporaryDraft() {
  if (temporaryDraftTimer) {
    clearTimeout(temporaryDraftTimer);
    temporaryDraftTimer = null;
  }
  const scheduled = scheduledTemporaryDraft || temporaryDraftArgs();
  scheduledTemporaryDraft = null;
  if (scheduled) await persistTemporaryDraft(scheduled);
  await temporaryDraftQueue;
}
async function updateTemporaryDraft(value) {
  scheduleTemporaryDraft(value);
  try { await flushTemporaryDraft(); } catch (error) { state.error = `临时草稿未保存：${String(error)}`; render(); throw error; }
}
window.addEventListener('pagehide', () => {
  // Best-effort final flush; normal submit already awaits the same queues.
  void flushOrdinaryComposerDraft();
  void flushTemporaryDraft();
});

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
  if (!state.current || state.temporaryConversation) return;
  if (!state.selectedConversationId) {
    state.pendingNewConversationModelId = modelId || null;
    state.p6gSelection = {
      ...(state.p6gSelection || {}),
      catalog: state.p6gCatalog,
      conversationOverride: { revision: 0, modelId: state.pendingNewConversationModelId },
      pickerTier: null,
    };
    state.p6gModelPickerOpen = false;
    state.status = modelId
      ? '已为这次新对话选择具体模型；发送时会与首条消息原子保存。'
      : '这次新对话已切回自动选择。';
    state.error = '';
    render();
    return;
  }
  if (!native || !state.p6gSelection) return;
  try {
    const args = { workspaceId: state.current.summary.id, conversationId: state.selectedConversationId, expectedRevision: state.p6gSelection.conversationOverride.revision };
    if (modelId) await invoke('set_desktop_p6g_conversation_override', { args: { ...args, modelId } });
    else await invoke('clear_desktop_p6g_conversation_override', args);
    state.p6gModelPickerOpen = false;
    state.status = modelId ? '已保存当前会话的本地手动模型选择；未读取 Key、未调用 Provider。' : '已切回自动；当前会话恢复本地策略。';
    state.error = '';
    await refresh();
  } catch (error) { state.error = `模型选择未保存：${String(error)}`; state.p6gModelPickerOpen = false; render(); }
}

async function updateConversationPreferences(patch, { close = false } = {}) {
  if (state.temporaryConversation) return;
  const next = { ...state.conversationPreferences, ...patch };
  if (!state.selectedConversationId) {
    state.conversationPreferences = { ...next, revision: 0 };
    if (close) { state.composerAddOpen = false; state.composerAddPage = 'root'; }
    state.status = patch.toneOverride
      ? '已为这次新对话选择基础风格；发送时会与首条消息原子保存。'
      : '已为这次新对话选择实时网页搜索状态；发送时会与首条消息原子保存。';
    state.error = '';
    render();
    if (close) restoreOverlayFocus();
    return;
  }
  if (!native) {
    state.conversationPreferences = { ...next, revision: Number(next.revision || 0) + 1 };
    if (close) { state.composerAddOpen = false; state.composerAddPage = 'root'; }
    render();
    if (close) restoreOverlayFocus();
    return;
  }
  if (!state.current) return;
  try {
    await invoke('set_desktop_conversation_preferences', { args: {
      workspaceId: state.current.summary.id,
      conversationId: state.selectedConversationId,
      expectedRevision: Number(state.conversationPreferences.revision || 0),
      toneOverride: next.toneOverride ?? null,
      webSearchOverride: typeof next.webSearchOverride === 'boolean' ? next.webSearchOverride : null,
    } });
    if (close) { state.composerAddOpen = false; state.composerAddPage = 'root'; }
    state.status = patch.toneOverride
      ? '已保存当前会话的基础风格；只影响后续回答。'
      : '已保存当前会话的实时网页搜索覆盖；全局设置未改变。';
    state.error = '';
    await refresh();
    if (close) restoreOverlayFocus();
  } catch (error) {
    state.error = `会话偏好未保存：${String(error)}`;
    if (close) { state.composerAddOpen = false; state.composerAddPage = 'root'; }
    render();
    if (close) restoreOverlayFocus();
  }
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

function leaveTemporaryChat() {
  if (!state.temporaryConversation) return;
  state.temporaryConversation = null;
  resetComposerPresentation();
  state.temporaryModelOpen = false;
  state.selectedConversationId = null;
  state.pane = 'chat';
  state.status = '已返回普通聊天；临时内容仍在隔离恢复记录中，24 小时内可继续。';
  state.error = '';
  render();
  void loadOrdinaryComposerDraft().then(render).catch(error => { state.error = `普通会话草稿未读取：${String(error)}`; render(); });
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
  try { await flushOrdinaryComposerDraft(); } catch (error) {
    state.error = `草稿未写入本机：${String(error)}`;
    render();
    return;
  }
  const conversation = resolveConversation(state.current, state.selectedConversationId);
  const sentDraft = state.composerDraft;
  const sentAttachments = [...state.composerAttachments];
  const pendingModelId = conversation ? null : state.pendingNewConversationModelId;
  const pendingPreferences = conversation ? null : { ...state.conversationPreferences };
  let clientSubmissionId = null;
  try {
    const workspaceId = state.current.summary.id;
    clientSubmissionId = createChatSubmissionId();
    if (!conversation) state.pendingCreatedConversationRouteWorkspaceId = workspaceId;
    state.pendingChatSubmission = {
      workspaceId,
      conversationId: conversation?.id || null,
      clientSubmissionId,
    };
    const execution = invoke('submit_desktop_ordinary_chat', { args: {
      workspaceId,
      conversationId: conversation?.id || null,
      projectId: conversation ? null : state.pane === 'work' ? state.selectedWorkProjectId : null,
      expectedRevision: conversation?.revision ?? null,
      text,
      attachmentIds: state.composerAttachments.map(item => item.id),
      modelId: pendingModelId,
      toneOverride: pendingPreferences?.toneOverride ?? null,
      webSearchOverride: typeof pendingPreferences?.webSearchOverride === 'boolean' ? pendingPreferences.webSearchOverride : null,
      // The visible Send press is the only authorization action; the Rust boundary refuses
      // to construct a provider request without this short, content-free receipt.
      egressAuthorization: { approvedAtMs: Date.now(), disclosureVersion: 'normal-chat-egress-v1', clientSubmissionId },
    } });
    state.composerAttachments = []; writeComposerAttachments();
    state.composerDraft = '';
    state.error = '';
    state.status = '消息已本地提交，正在等待模型回复。';
    render();
    let settled = false;
    const refreshLoop = refreshDesktopRuntimeWhilePending(workspaceId, conversation?.id || null, () => settled);
    let result;
    try { result = await execution; } finally { settled = true; await refreshLoop; }
    // A short request can settle before its first runtime refresh reaches this
    // window. Claim that one submission here; never re-arm it after streaming.
    claimSubmittedChatRoute({ workspaceId, conversationId: result.conversationId, clientSubmissionId });
    if (!conversation) state.pendingNewConversationModelId = null;
    if (state.pendingCreatedConversationRouteWorkspaceId === workspaceId) state.pendingCreatedConversationRouteWorkspaceId = null;
    state.status = result.state === 'COMPLETED' ? '回复已完成并写入用量账本。'
      : result.state === 'COMPLETED_ACCOUNTING_PENDING' ? '回复已保存；用量账本待本地恢复。'
      : result.state === 'CANCELLED' ? '已停止生成，已产生的文本保留。'
      : result.state === 'UNKNOWN' ? '连接结果未知；未自动重发。'
      : '回复失败；可从失败卡片重试。';
    await loadDesktopContextRecords();
    await loadDesktopDiagnosticRecords();
    await syncPersistedConversationContinuation(workspaceId, result.conversationId);
    await refresh();
  } catch (error) {
    if (state.pendingCreatedConversationRouteWorkspaceId === state.current?.summary?.id) state.pendingCreatedConversationRouteWorkspaceId = null;
    if (state.pendingChatSubmission?.clientSubmissionId === clientSubmissionId) state.pendingChatSubmission = null;
    state.composerDraft = sentDraft;
    state.composerAttachments = sentAttachments;
    if (!conversation) state.pendingNewConversationModelId = pendingModelId;
    writeComposerAttachments();
    state.error = `消息未提交：${String(error)}`;
    render();
  }
}

saveLocalMessage = sendLocalMessage;

let composerCameraStream = null;
function stopComposerCamera({ close = true } = {}) {
  for (const track of composerCameraStream?.getTracks?.() || []) track.stop();
  composerCameraStream = null;
  if (close) {
    state.cameraCaptureOpen = false;
    state.cameraCaptureReady = false;
    state.cameraCaptureBusy = false;
    state.cameraCaptureError = '';
  }
}
function attachComposerCameraStream() {
  const video = document.querySelector('[data-composer-camera-video]');
  if (!video || !composerCameraStream || video.srcObject === composerCameraStream) return;
  video.srcObject = composerCameraStream;
  void video.play().catch(() => {});
}
async function openComposerCamera() {
  if (!native || (!state.current && !state.temporaryConversation)) {
    state.error = native ? '先进入临时聊天或导入本地工作区。' : 'Web 预览不能把相机原图写入私有草稿。';
    render();
    return;
  }
  const count = state.temporaryConversation ? (state.temporaryConversation.draftAttachmentIds || []).length : state.composerAttachments.length;
  if (count >= 4) { state.error = '每条本地消息最多保留 4 个附件。'; render(); return; }
  if (!navigator.mediaDevices?.getUserMedia) { state.error = '当前 macOS WebView 不提供相机采集；草稿没有改变。'; render(); return; }
  stopComposerCamera();
  state.composerAddOpen = false;
  state.cameraCaptureOpen = true;
  state.cameraCaptureReady = false;
  state.cameraCaptureBusy = false;
  state.cameraCaptureError = '';
  render();
  try {
    composerCameraStream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' }, audio: false });
    state.cameraCaptureReady = true;
    render();
    queueMicrotask(attachComposerCameraStream);
  } catch (error) {
    stopComposerCamera({ close: false });
    state.cameraCaptureError = error?.name === 'NotAllowedError'
      ? '相机权限未授予；可在“系统设置 → 隐私与安全性 → 相机”允许后重试。草稿没有改变。'
      : '相机当前不可用；请检查是否被其他应用占用后重试。草稿没有改变。';
    render();
  }
}
async function captureComposerCamera() {
  const video = document.querySelector('[data-composer-camera-video]');
  if (!video || !composerCameraStream || !state.cameraCaptureReady || state.cameraCaptureBusy) return;
  const width = Number(video.videoWidth) || 0;
  const height = Number(video.videoHeight) || 0;
  if (!width || !height) { state.cameraCaptureError = '相机画面尚未就绪，请稍后再拍。'; render(); return; }
  state.cameraCaptureBusy = true;
  state.cameraCaptureError = '';
  render();
  attachComposerCameraStream();
  try {
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    canvas.getContext('2d', { alpha: false })?.drawImage(video, 0, 0, width, height);
    const dataUrl = canvas.toDataURL('image/png');
    if (state.temporaryConversation) {
      state.temporaryConversation = await invoke('import_desktop_temporary_camera_capture', { args: { temporaryId: state.temporaryConversation.temporaryId, dataUrl } });
      state.composerDraft = state.temporaryConversation.draft || state.composerDraft;
    } else {
      const attachment = await invoke('import_desktop_camera_capture', { args: { workspaceId: state.current.summary.id, dataUrl } });
      if (!state.composerAttachments.some(item => item.sha256 === attachment.sha256)) {
        state.composerAttachments = [...state.composerAttachments, attachment];
        writeComposerAttachments();
      }
    }
    stopComposerCamera();
    state.status = '相机原图已私有复制到当前草稿；尚未发送。';
    state.error = '';
  } catch (error) {
    state.cameraCaptureBusy = false;
    state.cameraCaptureError = `相机原图未加入草稿：${String(error)}`;
  }
  render();
}

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
  const sidebarWorkspace = captureCloudSidebarWorkspace();
  const cloudWorkspaceId = target.dataset.workspaceId || state.dialog?.workspaceId || null;
  let cloudSyncVerificationPending = false;
  await ensureConversationActionWorkspace(target);
  if (!native || !state.current) return;
  const workspaceId = state.current.summary.id;
  const conversationId = target.dataset.id;
  const syncedConversationKeys = state.accountSync?.syncedConversationKeys || [];
  const shouldSync = Boolean(cloudWorkspaceId) || syncedConversationKeys.includes(`${workspaceId}:${conversationId}`);
  let localSaved = false;
  const refreshSavedCloudRow = async () => {
    const existing = state.cloudConversations?.find(row => row.workspaceId === workspaceId && row.id === conversationId);
    if (!existing) return;
    const [updated] = await cloudRowsFromLocalEntries([{ workspaceId, conversationId, cloudPinned: existing.cloudPinned }]);
    if (!updated) throw new Error('已保存会话，但列表回读失败。');
    state.cloudConversations = state.cloudConversations.map(row => row === existing ? updated : row);
    persistCloudConversationCache();
  };
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
    localSaved = true;
    if (action === 'archive') {
      removeFavoriteConversation(target.dataset.id);
      if (state.selectedConversationId === target.dataset.id) state.selectedConversationId = null;
    }
    state.error = '';
    if (shouldSync) {
      const receipt = await syncSelectedContinuation({ invoke, workspaceId, conversationId,
        syncedConversationKeys: cloudWorkspaceId ? [...syncedConversationKeys, `${workspaceId}:${conversationId}`] : syncedConversationKeys });
      if (receipt.status === 'UNKNOWN') {
        cloudSyncVerificationPending = true;
        state.accountSync = { ...state.accountSync, pendingUnknown: { workspaceId: state.current.summary.id, conversationId: target.dataset.id } };
        scheduleUnknownSyncReconciliation(state.current.summary.id, target.dataset.id);
      } else if (!['SYNCED', 'UP_TO_DATE'].includes(receipt.status)) {
        throw new Error(`云端回写未完成：${receipt.status}`);
      }
    }
    await refreshSavedCloudRow();
    state.status = shouldSync
      ? (cloudSyncVerificationPending ? `${success}；云端提交正在后台核对。` : `${success} 已同步到云端。`)
      : success;
    state.dialog = null;
    await restoreCloudSidebarWorkspace(sidebarWorkspace);
    await refresh();
  } catch (error) {
    if (localSaved) {
      await refreshSavedCloudRow().catch(() => {});
      state.dialog = null;
    }
    await restoreCloudSidebarWorkspace(sidebarWorkspace).catch(() => {});
    state.error = localSaved
      ? `会话修改已保存在本机，但同步或列表回读未完成：${String(error)}`
      : `会话操作被拒绝：${String(error)}`;
    render();
  }
}

function isCloudListConversationTarget(target) {
  return state.sidebarConversationList === 'cloud' && Boolean(target?.dataset?.workspaceId);
}

async function setCloudListConversationPinned(target, pinned) {
  if (!isCloudListConversationTarget(target)) return;
  const pinnedConversationIds = new Set(await invoke('set_desktop_cloud_list_pinned', {
    conversationId: target.dataset.id,
    pinned,
  }));
  state.cloudConversations = state.cloudConversations.map(item => (
    { ...item, pinned: pinnedConversationIds.has(item.id), cloudPinned: pinnedConversationIds.has(item.id) }
  ));
  persistCloudConversationCache();
  state.contextMenu = null;
  state.error = '';
  state.status = pinned ? '云端列表已置顶；本地列表未改动。' : '已取消云端列表置顶；本地列表未改动。';
  render();
}

async function ensureConversationActionWorkspace(target) {
  const workspaceId = target?.dataset?.workspaceId || target?.workspaceId || state.dialog?.workspaceId || state.contextMenu?.workspaceId || null;
  if (!native || !workspaceId || state.current?.summary?.id === workspaceId) return;
  state.current = await invoke('read_desktop_workspace', { workspaceId });
  state.selectedConversationId = null;
  state.history = await invoke('read_desktop_workbench_history', { workspaceId });
  reloadFavoriteConversationIds();
  await loadConversationReadState({ observeSelected: false });
}

// Cloud-list actions may briefly load a restored workspace to mutate that row.
// Only opening a cloud row navigates; list actions always return here.
function captureCloudSidebarWorkspace() {
  if (state.sidebarConversationList !== 'cloud' || !state.current?.summary?.id) return null;
  return { workspaceId: state.current.summary.id, selectedConversationId: state.selectedConversationId };
}

async function restoreCloudSidebarWorkspace(snapshot) {
  if (!snapshot || !native || state.current?.summary?.id === snapshot.workspaceId) return;
  const current = await invoke('read_desktop_workspace', { workspaceId: snapshot.workspaceId });
  state.current = current;
  state.selectedConversationId = resolveConversation(current, snapshot.selectedConversationId) ? snapshot.selectedConversationId : null;
  state.history = await invoke('read_desktop_workbench_history', { workspaceId: snapshot.workspaceId });
  state.favoriteConversationIds = new Set(await invoke('read_desktop_favorite_conversation_ids', { workspaceId: snapshot.workspaceId }));
  await loadConversationReadState({ observeSelected: false });
}

function batchConversationKey(item) {
  return `${item?.workspaceId || 'local'}:${item?.id || ''}`;
}

function currentBatchCandidates() {
  const source = state.sidebarConversationList === 'cloud'
    ? state.cloudConversations
    : activeConversations(workspace(), state.manualUnreadAtMs);
  return source.filter(item => item && !item.archived && !item.deleted);
}

function finishConversationBatchEditing() {
  state.batchEditing = false;
  state.batchSelectedConversationKeys = new Set();
}

async function confirmConversationBatchDelete() {
  const dialog = state.dialog;
  if (!native || dialog?.kind !== 'conversation-batch-delete' || dialog.submitting || !Array.isArray(dialog.items) || !dialog.items.length) return;
  const sidebarWorkspace = captureCloudSidebarWorkspace();
  state.dialog = { ...dialog, submitting: true, failure: '' };
  render();
  let completed = 0;
  let cloudSyncVerificationPending = false;
  try {
    for (const item of dialog.items) {
      await ensureConversationActionWorkspace(item);
      if (!state.current) throw new Error('本机工作区不可用');
      await invoke('mutate_desktop_domain', { args: {
        intentId: intent('conversation-lifecycle'), workspaceId: state.current.summary.id,
        entity: 'conversation', action: 'softDelete', objectId: item.id,
        expectedRevision: Number(item.revision), fields: {},
      } });
      removeFavoriteConversation(item.id);
      if (item.workspaceId) {
        const receipt = await invoke('sync_selected_desktop_conversation', {
          workspaceId: state.current.summary.id,
          conversationId: item.id,
        });
        if (receipt.status === 'UNKNOWN') {
          cloudSyncVerificationPending = true;
          state.accountSync = { ...state.accountSync, pendingUnknown: { workspaceId: state.current.summary.id, conversationId: item.id } };
          scheduleUnknownSyncReconciliation(state.current.summary.id, item.id);
        } else if (!['SYNCED', 'UP_TO_DATE'].includes(receipt.status)) {
          throw new Error(`云端回写未完成：${receipt.status}`);
        }
      }
      completed += 1;
    }
    if (state.selectedConversationId && dialog.items.some(item => item.id === state.selectedConversationId && (!item.workspaceId || item.workspaceId === state.current?.summary?.id))) state.selectedConversationId = null;
    const cloudKeys = new Set(dialog.items.filter(item => item.workspaceId).map(batchConversationKey));
    if (cloudKeys.size) {
      state.cloudConversations = state.cloudConversations.filter(item => !cloudKeys.has(batchConversationKey(item)));
      cloudListReadGeneration += 1;
      persistCloudConversationCache();
    }
    state.dialog = null;
    finishConversationBatchEditing();
    state.error = '';
    state.status = cloudSyncVerificationPending
      ? `已将 ${completed} 个会话移入回收站；云端提交正在后台核对。`
      : `已将 ${completed} 个会话移入回收站，可恢复。`;
    await restoreCloudSidebarWorkspace(sidebarWorkspace);
    await refresh();
  } catch (error) {
    await restoreCloudSidebarWorkspace(sidebarWorkspace).catch(() => {});
    state.dialog = { ...dialog, submitting: false, failure: `${completed ? `已处理 ${completed} 个；` : ''}${String(error)}` };
    render();
  }
}

// A cloud row is rendered from a restored local workspace, but opening it has
// its own asynchronous route. Do not mutate its DOM and synthesize a second
// click: that allowed a late workspace read or optional model preference read
// to strand the visual surface on the old empty conversation.
app.addEventListener('click', event => {
  const target = event.target.closest?.('[data-action="select-chat"]');
  const workspaceId = target?.dataset?.workspaceId;
  if (!target || !workspaceId) return;
  event.preventDefault();
  event.stopImmediatePropagation();
  const conversationId = target.dataset.id || '';
  const conversation = state.cloudConversations.find(item => item.workspaceId === workspaceId && item.id === conversationId);
  void openCloudConversationRoute({ workspaceId, conversationId, title: conversation?.title || '云端会话' });
}, true);

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
  conversationLongPress.cancel('contextmenu');
  event.preventDefault();
  openConversationContextMenu(row, event.target);
});

app.addEventListener('click', async event => {
  const target = event.target.closest('[data-action]');
  const action = target?.dataset.action;
  if (state.contextMenu && !target?.closest('.chat-context-menu')) closeConversationContextMenu();
  if (action === 'choose-storage-location') {
    if (!native) return;
    const selectedPath = await dialogInvoke('open', { directory: true, multiple: false });
    if (!selectedPath) return;
    try {
      await invoke('choose_desktop_storage_location', { selectedPath });
      state.dialog = { kind: 'model-settings-feedback', message: '保存路径已设置，下次启动生效。空目录会复制现有数据；已有南枫 AI 数据的目录会直接读取。旧目录保留。' };
    } catch (error) { state.dialog = { kind: 'model-settings-feedback', failed: true, message: String(error) }; }
    render(); return;
  }
  if (action === 'open-conversation-row-menu') { openConversationContextMenu(target.closest('[data-conversation-row]'), target); return; }
  if (action === 'open-conversation-header-menu') { openConversationHeaderMenu(target); return; }
  if (action === 'open-image-preview') { event.preventDefault(); await openImagePreview(target.dataset.attachmentId); return; }
  if (action === 'open-pdf-preview') { event.preventDefault(); await openPdfPreview(target.dataset.attachmentId); return; }
  if (action === 'open-video-preview') { event.preventDefault(); await openVideoPreview(target.dataset.attachmentId); return; }
  if (action === 'open-audio-preview') { event.preventDefault(); await openAudioPreview(target.dataset.attachmentId); return; }
  if (action === 'open-text-preview') { event.preventDefault(); await openTextPreview(target.dataset.attachmentId); return; }
  if (action === 'open-archive-preview') { event.preventDefault(); await openArchivePreview(target.dataset.attachmentId); return; }
  if (action === 'open-archive-directory') { event.preventDefault(); if (state.archivePreview) { state.archivePreview = { ...state.archivePreview, directory: target.dataset.archivePath || '', entry: null }; render(); } return; }
  if (action === 'open-archive-entry-preview') { event.preventDefault(); await openArchiveEntryPreview(target.dataset.archiveEntryPath); return; }
  if (action === 'archive-preview-back') { event.preventDefault(); if (state.archivePreview) { state.archivePreview = { ...state.archivePreview, entry: null }; render(); } return; }
  if (action === 'archive-pdf-page-previous' || action === 'archive-pdf-page-next') { event.preventDefault(); const entry = state.archivePreview?.entry; if (entry?.entryPath) await openArchiveEntryPreview(entry.entryPath, Number(entry.pageNumber) + (action === 'archive-pdf-page-next' ? 1 : -1)); return; }
  if (action === 'toggle-video-playback') { event.preventDefault(); await togglePreviewPlayback('video'); return; }
  if (action === 'toggle-video-volume' || action === 'toggle-audio-volume') {
    event.preventDefault();
    const kind = action === 'toggle-video-volume' ? 'video' : 'audio';
    const control = target.closest('.preview-volume-control');
    const volumeRange = control?.querySelector(`[data-${kind}-preview-volume]`);
    const popover = control?.querySelector('.preview-volume-popover');
    if (!volumeRange || !popover) return;
    const opening = popover.hidden;
    popover.hidden = !opening;
    target.setAttribute('aria-expanded', String(opening));
    if (opening) volumeRange.focus({ preventScroll: true });
    return;
  }
  if (action === 'toggle-audio-playback') { event.preventDefault(); await togglePreviewPlayback('audio'); return; }
  if (action === 'open-preview-boundary') {
    event.preventDefault();
    await openAttachmentPreview({
      id: target.dataset.attachmentId,
      displayName: target.dataset.attachmentName || '本地附件',
      mimeType: target.dataset.attachmentMime || '',
    });
    return;
  }
  if (action === 'copy-text-preview') {
    event.preventDefault();
    const preview = state.textPreview;
    if (!preview?.text) return;
    try {
      await navigator.clipboard.writeText(preview.text);
      showCopyIconFeedback(target);
      state.error = '';
    } catch (error) {
      if (error?.name !== 'AbortError') showPreviewActionError(target, '复制未完成。');
    }
    return;
  }
  if (action === 'chat-attachment-search') {
    event.preventDefault();
    const item = state.dialog?.item;
    if (!item) return;
    state.dialog = null;
    // This is a navigation request, not a filename search. Start from the complete catalogue
    // and reveal the exact attachment only after its normal page has been loaded.
    state.chatSearch = '';
    state.searchCategory = 'all';
    state.searchSortMode = 'default';
    state.searchFileType = 'all';
    state.searchRevealTarget = {
      workspaceId: item.workspaceId,
      conversationId: item.conversationId,
      messageId: item.messageId,
      attachmentId: item.attachmentId,
    };
    openFullSearch('all');
    return;
  }
  if (action === 'chat-attachment-save' || action === 'chat-attachment-share') {
    event.preventDefault();
    const item = state.dialog?.item;
    if (!item) return;
    try {
      if (action === 'chat-attachment-save') await saveAttachmentCopy(item);
      else await sharePreviewAttachment(item);
    } catch (error) { if (error?.name !== 'AbortError') showPreviewActionError(target, action === 'chat-attachment-save' ? '附件副本未保存。' : '分享未打开。'); }
    return;
  }
  if (action === 'save-preview-attachment') {
    event.preventDefault();
    const preview = activePreviewForKind(target.dataset.previewKind);
    try { await saveAttachmentCopy(preview || {}); }
    catch (error) { showPreviewActionError(target, '附件副本未保存。'); }
    return;
  }
  if (action === 'share-preview-attachment') {
    event.preventDefault();
    const preview = activePreviewForKind(target.dataset.previewKind);
    try { await sharePreviewAttachment(preview); state.error = ''; }
    catch (error) { if (error?.name !== 'AbortError') showPreviewActionError(target, '分享未打开。'); }
    return;
  }
  if (action === 'close-image-preview') { event.preventDefault(); cancelImagePreviewLoad(); state.imagePreview = null; render(); return; }
  if (action === 'image-preview-previous' || action === 'image-preview-next') { event.preventDefault(); navigateImagePreview(action === 'image-preview-next' ? 'next' : 'previous'); return; }
  if (action === 'close-pdf-preview') { event.preventDefault(); state.pdfPreview = null; render(); return; }
  if (action === 'close-video-preview') { event.preventDefault(); await closeVideoPreview(); return; }
  if (action === 'close-audio-preview') { event.preventDefault(); await closeAudioPreview(); return; }
  if (action === 'close-text-preview') { event.preventDefault(); state.textPreview = null; render(); return; }
  if (action === 'close-archive-preview') { event.preventDefault(); state.archivePreview = null; render(); return; }
  if (action === 'close-preview-boundary') { event.preventDefault(); state.previewBoundary = null; render(); return; }
  if (action === 'pdf-page-previous' || action === 'pdf-page-next') { event.preventDefault(); if (!state.pdfPreview?.attachmentId) return; await openPdfPreview(state.pdfPreview.attachmentId, state.pdfPreview.pageNumber + (action === 'pdf-page-next' ? 1 : -1), state.pdfPreview.workspaceId); return; }
  if (action === 'toggle-composer-add') {
    if (state.composerAddOpen) closeTopOverlay(); else openTransientOverlay('composer-add', target);
    return;
  }
  if (action === 'close-composer-add') { closeTopOverlay(); return; }
  if (action === 'open-composer-camera') { event.preventDefault(); await openComposerCamera(); return; }
  if (action === 'close-composer-camera') { event.preventDefault(); stopComposerCamera(); render(); return; }
  if (action === 'capture-composer-camera') { event.preventDefault(); await captureComposerCamera(); return; }
  if (action === 'open-composer-tone-picker') {
    event.preventDefault();
    state.composerAddPage = 'tone';
    render();
    queueMicrotask(() => document.querySelector('.composer-add-sheet-back')?.focus());
    return;
  }
  if (action === 'composer-add-back') {
    event.preventDefault();
    state.composerAddPage = 'root';
    render();
    queueMicrotask(() => document.querySelector('[data-action="open-composer-tone-picker"]')?.focus());
    return;
  }
  if (action === 'select-conversation-tone') {
    event.preventDefault();
    void updateConversationPreferences({ toneOverride: target.dataset.tone || null }, { close: true });
    return;
  }
  if (action === 'toggle-conversation-web-search') {
    event.preventDefault();
    const current = typeof state.conversationPreferences.webSearchOverride === 'boolean'
      ? state.conversationPreferences.webSearchOverride
      : Boolean(state.productSettings.webSearchEnabled);
    void updateConversationPreferences({ webSearchOverride: !current });
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
    const retryWorkspaceId = state.current?.summary?.id;
    state.status = '已按原 Provider、原模型和原幂等键开始重试。';
    state.error = '';
    render();
    try {
      const result = await invoke('retry_desktop_ordinary_chat', { args: { attemptId } });
      state.status = result.state === 'COMPLETED' ? '重试已完成并写入用量账本。' : result.state === 'UNKNOWN' ? '重试结果仍未知，未再次自动重发。' : '重试未完成。';
      await loadDesktopContextRecords();
      await loadDesktopDiagnosticRecords();
      await syncPersistedConversationContinuation(retryWorkspaceId, result.conversationId);
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
      showMessageCopyFeedback(target);
      state.error = '';
    } catch (error) {
      state.error = `复制失败：${String(error)}`;
      render();
    }
    return;
  }
  if (action === 'copy-markdown-block') {
    event.preventDefault();
    const payload = String(target.dataset.copyText || '');
    const label = String(target.dataset.copyLabel || '内容');
    if (!payload) { state.error = '该内容块没有可复制文本。'; render(); return; }
    try {
      await navigator.clipboard.writeText(payload);
      showCopyIconFeedback(target);
      state.error = '';
    } catch (error) {
      state.error = `复制失败：${String(error)}`;
      render();
    }
    return;
  }
  if (action === 'copy-account-recovery-code') {
    event.preventDefault();
    const payload = String(target.dataset.copyValue || '');
    if (!payload) { state.error = '恢复码不可用，请重新创建。'; render(); return; }
    try {
      await navigator.clipboard.writeText(payload);
      showCopyIconFeedback(target);
      state.error = '';
    } catch (error) {
      state.error = `复制失败：${String(error)}`;
      render();
    }
    return;
  }
  if (action === 'open-assistant-message-menu') {
    event.preventDefault();
    toggleAssistantMessageMenu(target);
    return;
  }
  if (action === 'open-source-links') {
    event.preventDefault();
    let sources = [];
    try { sources = JSON.parse(target.dataset.sources || '[]'); } catch { /* malformed imported metadata remains inert */ }
    state.dialog = { kind: 'source-links', sources: Array.isArray(sources) ? sources.slice(0, 32) : [] };
    state.error = '';
    render();
    return;
  }
  if (action === 'open-source-link') {
    event.preventDefault();
    try {
      await invoke('open_desktop_source_link', { args: { href: String(target.dataset.href || '') } });
      state.error = '';
    } catch (error) {
      state.error = `无法打开来源网站：${String(error)}`;
      render();
    }
    return;
  }
  if (action === 'show-assistant-answer-information' || action === 'show-answer-context') {
    const record = state.contextSelectionRecords.find(item => item.assistantMessageId === target.dataset.messageId) || null;
    state.assistantMessageMenu = null;
    state.dialog = { kind: 'assistant-answer-information', record };
    state.error = '';
    render();
    return;
  }
  if (action === 'share-message') {
    const conversation = resolveConversation(state.current, state.selectedConversationId);
    const message = conversation?.messages
      ?.find(item => item.id === target.dataset.messageId);
    const payload = messagePlainText(message);
    if (!payload) { state.error = '该消息没有可分享的安全正文。'; render(); return; }
    try {
      if (String(message?.role || '').trim().toLocaleLowerCase() === 'assistant') {
        const sequence = String(Math.max(1, orderedConversationMessages(conversation).findIndex(item => item.id === message.id) + 1)).padStart(2, '0');
        await saveMarkdown(assistantMessageMarkdown(conversation, message.id), safeMarkdownName(`${conversation.title || '南枫 AI'}-${sequence}`), '当前回答');
        return;
      }
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
  if (action === 'branch-from-message') {
    state.assistantMessageMenu = null;
    state.dialog = null;
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
  if (action === 'toggle-conversation-favorite' || action === 'context-menu-favorite') {
    const sidebarWorkspace = captureCloudSidebarWorkspace();
    await ensureConversationActionWorkspace(target);
    await toggleFavoriteConversation(target.dataset.id, { renderAfter: false });
    await restoreCloudSidebarWorkspace(sidebarWorkspace);
    render();
    return;
  }
  if (action === 'toggle-conversation-find') {
    cancelConversationFindRender();
    state.conversationFindOpen = !state.conversationFindOpen;
    recomputeConversationFind({ resetIndex: true });
    render();
    if (state.conversationFindOpen) focusCurrentFindMatch();
    return;
  }
  if (action === 'context-menu-find') {
    await ensureConversationActionWorkspace(target);
    cancelConversationFindRender();
    state.selectedConversationId = target.dataset.id;
    state.contextMenu = null;
    state.conversationFindOpen = true;
    recomputeConversationFind({ resetIndex: true });
    render();
    focusCurrentFindMatch();
    return;
  }
  if (action === 'close-conversation-find') {
    cancelConversationFindRender();
    state.conversationFindOpen = false;
    state.conversationFindQuery = '';
    recomputeConversationFind({ resetIndex: true });
    render();
    return;
  }
  if (action === 'conversation-find-previous' || action === 'conversation-find-next') {
    cancelConversationFindRender();
    recomputeConversationFind();
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
  if (action === 'context-menu-share') {
    await ensureConversationActionWorkspace(target);
    state.contextMenu = null;
    const conversation = workspace()?.exchange?.conversations?.find(item => item.id === target.dataset.id);
    if (!conversation) return;
    await saveMarkdown(conversationMarkdown(conversation), safeMarkdownName(conversation?.title), '当前会话');
    return;
  }
  if (action === 'context-menu-cancel-sync') {
    const sidebarWorkspace = captureCloudSidebarWorkspace();
    await ensureConversationActionWorkspace(target);
    if (!native || !state.current) return;
    const cloudWorkspaceId = state.current.summary.id;
    try {
      await invoke('cancel_desktop_conversation_sync', {
        workspaceId: cloudWorkspaceId,
        conversationId: target.dataset.id,
      });
      await loadDesktopAccountSync();
      state.cloudConversations = state.cloudConversations.filter(item => !(item.workspaceId === cloudWorkspaceId && item.id === target.dataset.id));
      cloudListReadGeneration += 1;
      persistCloudConversationCache();
      state.contextMenu = null;
      state.status = '已取消同步；云端副本已删除，本地对话保留。';
      state.error = '';
    } catch (error) {
      state.error = `取消同步未完成：${String(error)}`;
    }
    await restoreCloudSidebarWorkspace(sidebarWorkspace).catch(() => {});
    render();
    return;
  }
  if (action === 'context-menu-pin') {
    if (isCloudListConversationTarget(target)) {
      await setCloudListConversationPinned(target, target.dataset.pinned !== 'true');
      return;
    }
    await mutateConversationLifecycle(target, 'setPinned', { pinned: target.dataset.pinned !== 'true' }, target.dataset.pinned === 'true' ? '会话已取消置顶。' : '会话已置顶。');
    state.contextMenu = null;
    return;
  }
  if (action === 'context-menu-unread') {
    const sidebarWorkspace = captureCloudSidebarWorkspace();
    await ensureConversationActionWorkspace(target);
    try {
      await markConversationUnread(target.dataset.id);
      state.status = '会话已标记未读；真正打开该会话后清除。';
      state.error = '';
    } catch (error) {
      state.error = `会话未读状态未更新：${String(error)}`;
    }
    await restoreCloudSidebarWorkspace(sidebarWorkspace).catch(() => {});
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
    await ensureConversationActionWorkspace(target);
    const conversation = workspace()?.exchange?.conversations?.find(item => item.id === target.dataset.id);
    state.contextMenu = null;
    state.dialog = { kind: 'conversation-rename', id: target.dataset.id, revision: Number(target.dataset.revision), title: conversation?.title || '', workspaceId: target.dataset.workspaceId || null };
    render();
    return;
  }
  if (action === 'open-conversation-rename') {
    await ensureConversationActionWorkspace(target);
    const conversation = workspace()?.exchange?.conversations?.find(item => item.id === target.dataset.id);
    state.dialog = { kind: 'conversation-rename', id: target.dataset.id, revision: Number(target.dataset.revision), title: conversation?.title || '', workspaceId: target.dataset.workspaceId || null };
    render();
    return;
  }
  if (action === 'context-menu-project') {
    await ensureConversationActionWorkspace(target);
    const conversation = workspace()?.exchange?.conversations?.find(item => item.id === target.dataset.id);
    state.contextMenu = null;
    state.dialog = { kind: 'conversation-project', id: target.dataset.id, revision: Number(target.dataset.revision), projectId: conversation?.projectId || null, workspaceId: target.dataset.workspaceId || null };
    render();
    return;
  }
  if (action === 'context-menu-delete') {
    state.contextMenu = null;
    state.dialog = { kind: 'conversation-delete', id: target.dataset.id, revision: Number(target.dataset.revision), workspaceId: target.dataset.workspaceId || null };
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
  if (action === 'toggle-conversation-batch-edit') {
    if (state.batchEditing) finishConversationBatchEditing();
    else if (currentBatchCandidates().length) {
      state.batchEditing = true;
      state.batchSelectedConversationKeys = new Set();
    }
    render();
    return;
  }
  if (action === 'toggle-batch-conversation') {
    const key = target.dataset.batchKey;
    if (!key) return;
    const selected = new Set(state.batchSelectedConversationKeys);
    if (selected.has(key)) selected.delete(key); else selected.add(key);
    state.batchSelectedConversationKeys = selected;
    render();
    return;
  }
  if (action === 'toggle-batch-select-all') {
    const candidates = currentBatchCandidates();
    const keys = candidates.map(batchConversationKey);
    const allSelected = keys.length > 0 && keys.every(key => state.batchSelectedConversationKeys.has(key));
    state.batchSelectedConversationKeys = allSelected ? new Set() : new Set(keys);
    render();
    return;
  }
  if (action === 'request-batch-conversation-delete') {
    const selected = state.batchSelectedConversationKeys;
    const items = currentBatchCandidates().filter(item => selected.has(batchConversationKey(item))).map(item => ({
      id: item.id,
      revision: item.revision,
      workspaceId: item.workspaceId || null,
      title: item.title || '未命名会话',
    }));
    if (!items.length) return;
    state.dialog = { kind: 'conversation-batch-delete', items, submitting: false, failure: '' };
    render();
    return;
  }
  if (action === 'confirm-conversation-batch-delete') {
    await confirmConversationBatchDelete();
    return;
  }
  if (action === 'toggle-rail') {
    state.railCollapsed = !state.railCollapsed;
    render();
    return;
  }
  if (action === 'set-conversation-pinned') {
    if (isCloudListConversationTarget(target)) {
      await setCloudListConversationPinned(target, target.dataset.pinned === 'true');
      return;
    }
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
    state.selectedConversationId = null;
    state.selectedWorkProjectId = null;
  } else if (action === 'show-workspace') {
    state.pane = 'work';
    state.profileOpen = false;
    state.selectedConversationId = null;
    state.selectedWorkProjectId = null;
  } else if (action === 'select-work-project' || action === 'new-work-chat') {
    state.pane = 'work';
    state.profileOpen = false;
    state.selectedWorkProjectId = target.dataset.projectId || null;
    state.selectedConversationId = null;
  } else if (action === 'select-work-conversation') {
    state.pane = 'work';
    state.profileOpen = false;
    state.selectedWorkProjectId = target.dataset.projectId || null;
    state.selectedConversationId = target.dataset.id || null;
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
  if (['return-workspace-origin', 'show-work', 'show-workspace', 'select-work-project', 'new-work-chat', 'select-work-conversation', 'show-projects', 'show-knowledge', 'show-memory', 'show-conversation'].includes(action)) {
    state.sidebarOpen = false;
    render();
  }
});

app.addEventListener('click', event => {
  const action = event.target.closest('[data-action]')?.dataset.action;
  if (action === 'new-chat' || action === 'select-chat') {
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
  if (action === 'set-search-category') { event.preventDefault(); selectFullSearchCategory(target.dataset.category); return; }
  if (action === 'set-search-sort') { event.preventDefault(); const column = target.dataset.sortColumn || 'default'; state.searchSortMode = column === 'default' ? 'default' : state.searchSortMode === `${column}Descending` ? `${column}Ascending` : `${column}Descending`; state.searchScrollSnapshot = null; void runFullSearch(); return; }
  if (action === 'toggle-search-file-types') { event.preventDefault(); state.searchFileTypeOpen = !state.searchFileTypeOpen; render(); return; }
  if (action === 'set-search-file-type') { event.preventDefault(); state.searchFileType = target.dataset.fileType || 'all'; state.searchFileTypeOpen = false; state.searchScrollSnapshot = null; void runFullSearch(); return; }
  if (action === 'retry-full-search') { event.preventDefault(); void runFullSearch({ restoreScroll: true }); return; }
  if (action === 'load-more-search-results') { event.preventDefault(); void runFullSearch({ restoreScroll: true, append: true }); return; }
  if (action === 'clear-full-search') { event.preventDefault(); state.chatSearch = ''; state.searchHistoryHighlighted = null; state.searchHistoryOpen = false; state.searchScrollSnapshot = null; void runFullSearch(); return; }
  if (action === 'open-search-history') { event.preventDefault(); state.searchHistoryOpen = !state.searchHistoryOpen; state.searchHistoryManuallyOpened = state.searchHistoryOpen; state.suppressSearchHistoryFocus = !state.searchHistoryOpen; render(); return; }
  if (action === 'fill-search-history') { event.preventDefault(); state.chatSearch = target.dataset.query || ''; state.searchHistoryOpen = false; state.searchHistoryManuallyOpened = false; state.suppressSearchHistoryFocus = true; void submitLocalSearch(); return; }
  if (action === 'close-search-history') { event.preventDefault(); state.searchHistoryOpen = false; state.suppressSearchHistoryFocus = true; render(); focusSearchAfterHistoryDismissal(); return; }
  if (action === 'clear-search-history') { event.preventDefault(); if (native && state.current) invoke('clear_desktop_local_search_history', { workspaceId: state.current.summary.id }).then(() => { state.searchHistory = []; state.searchHistoryHighlighted = null; focusSearchAfterHistoryClear(); }).catch(() => { state.searchError = '本地搜索历史清除未完成。'; render(); }); return; }
  if (action === 'close-search') { event.preventDefault(); if (fullSearchCategoryFrame !== null) { cancelAnimationFrame(fullSearchCategoryFrame); fullSearchCategoryFrame = null; } supersedeFullSearch(); state.searchLoading = false; state.searchPanel = false; state.searchReturnActive = false; state.searchLocatedArchivedConversationId = null; render(); return; }
  if (action === 'return-to-search') { event.preventDefault(); state.searchPanel = true; state.searchReturnActive = false; state.searchLocatedArchivedConversationId = null; render(); queueMicrotask(restoreFullSearchScroll); return; }
  if (action === 'open-search-result') {
    event.preventDefault();
    await locateSearchHit(fullSearchHit(target.dataset.entryId)); return;
  }
  if (action === 'open-search-attachment') { event.preventDefault(); if (searchAttachmentLongPressTriggered === target.dataset.entryId) { searchAttachmentLongPressTriggered = null; return; } await openSearchAttachment(fullSearchHit(target.dataset.entryId)); return; }
  if (action === 'open-search-attachment-menu') { event.preventDefault(); const hit = fullSearchHit(target.dataset.entryId); if (hit) { rememberFullSearchScroll(hit.entryId); state.dialog = { kind: 'search-attachment-actions', hit, anchor: attachmentMenuAnchor(target.closest('.desktop-search-attachment-card') || target) }; render(); } return; }
  if (action === 'preview-search-attachment') { event.preventDefault(); const hit = fullSearchHit(target.dataset.entryId) || state.dialog?.hit; state.dialog = null; await openSearchAttachment(hit); return; }
  if (action === 'save-search-attachment') { event.preventDefault(); const hit = fullSearchHit(target.dataset.entryId) || state.dialog?.hit; if (!hit) return; try { if (await saveAttachmentCopy(hit)) state.dialog = null; } catch (error) { state.error = `附件副本未保存：${String(error)}`; } render(); return; }
  if (action === 'locate-search-attachment') { event.preventDefault(); const hit = fullSearchHit(target.dataset.entryId) || state.dialog?.hit; state.dialog = null; await locateSearchHit(hit); return; }
  if (action === 'ask-delete-search-attachment') { event.preventDefault(); const hit = fullSearchHit(target.dataset.entryId) || state.dialog?.hit; if (hit) { state.dialog = { kind: 'search-attachment-delete', hit }; render(); } return; }
  if (action === 'confirm-delete-search-attachment') { event.preventDefault(); const hit = fullSearchHit(target.dataset.entryId) || state.dialog?.hit; if (!hit) return; state.dialog = { ...state.dialog, submitting: true, failure: '' }; render(); try { await invoke('mutate_desktop_domain', { args: { intentId: intent('search-remove-attachment'), workspaceId: hit.workspaceId, entity: 'conversation', action: 'removeAttachment', objectId: hit.conversationId, expectedRevision: Number(hit.conversationRevision), fields: { messageId: hit.messageId, attachmentId: hit.attachmentId } } }); state.dialog = null; state.status = '附件引用已删除；共享私有副本仍会保留，最后一个引用移除后进入安全清理期。'; if (state.current?.summary?.id === hit.workspaceId) state.current = await invoke('read_desktop_workspace', { workspaceId: hit.workspaceId }); await runFullSearch({ restoreScroll: true }); } catch (error) { state.dialog = { ...state.dialog, submitting: false, failure: String(error) }; render(); } return; }
  if (action === 'toggle-temporary-model') {
    event.preventDefault();
    if (!state.temporaryConversation) return;
    if (state.temporaryModelOpen) closeTopOverlay(); else openTransientOverlay('temporary-model', target);
  } else if (action === 'toggle-p6g-model-picker') {
    event.preventDefault();
    if (state.p6gModelPickerOpen) closeTopOverlay(); else openTransientOverlay('p6g-model-picker', target);
  } else if (action === 'close-p6g-model-picker' || action === 'close-temporary-model-picker') {
    event.preventDefault();
    closeTopOverlay();
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
  }
});

app.addEventListener('input', event => {
  if (event.target.id === 'chat-composer') {
    if (state.temporaryConversation) scheduleTemporaryDraft(state.composerDraft); else writeChatDraft(state.composerDraft);
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
  const previewRange = event.target.closest?.('[data-video-preview-range], [data-audio-preview-range]');
  if (previewRange) {
    const kind = previewRange.hasAttribute('data-video-preview-range') ? 'video' : 'audio';
    const media = document.querySelector(`[data-${kind}-preview]`);
    if (media) { media.currentTime = Math.max(0, Number(previewRange.value) || 0) / 1000; syncPreviewPlaybackUi(kind, media); }
  }
  const volumeRange = event.target.closest?.('[data-video-preview-volume], [data-audio-preview-volume]');
  if (volumeRange) {
    const kind = volumeRange.hasAttribute('data-video-preview-volume') ? 'video' : 'audio';
    const media = document.querySelector(`[data-${kind}-preview]`);
    if (media) {
      media.muted = false;
      media.volume = Math.max(0, Math.min(1, (Number(volumeRange.value) || 0) / 100));
      syncPreviewPlaybackUi(kind, media);
    }
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
  else if (event.key === 'Escape' && state.searchPanel) { supersedeFullSearch(); state.searchLoading = false; state.searchPanel = false; render(); }
});

app.addEventListener('click', event => {
  if (!state.searchHistoryOpen || event.target.closest('.chat-search-wrap, .desktop-search-dock, .desktop-search-history-panel')) return;
  state.searchHistoryOpen = false;
  state.suppressSearchHistoryFocus = true;
  render();
  focusSearchAfterHistoryDismissal();
});

let wasSidebarFallbackViewport = window.matchMedia('(max-width: 900px)').matches;
window.addEventListener('resize', () => {
  const isSidebarFallbackViewport = window.matchMedia('(max-width: 900px)').matches;
  if (wasSidebarFallbackViewport && !isSidebarFallbackViewport && state.sidebarOpen) {
    state.sidebarOpen = false;
    render();
  }
  wasSidebarFallbackViewport = isSidebarFallbackViewport;
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
  if (!['show-google-login-requirements', 'sign-in-google-account', 'sign-out-google-account', 'load-account-cloud-documents', 'show-recent-conversation-list', 'show-cloud-conversation-list', 'open-cloud-conversation', 'choose-selected-local-sync-start', 'toggle-periodic-account-sync', 'context-menu-sync', 'reconcile-account-sync'].includes(action)) return;
  event.preventDefault();
  event.stopImmediatePropagation();
  if (action === 'load-account-cloud-documents') {
    if (cloudListReadPending) return;
    cloudListReadPending = true;
  }
  void (async () => {
    try {
      if (!native) {
        if (action === 'context-menu-sync') {
          state.contextMenu = null;
          state.status = '同步尚未就绪：Web 预览不会打开 Google、读取凭据或连接南枫云。';
          render();
          return;
        }
        state.status = 'Web 预览不会打开 Google、读取凭据或连接南枫云。';
        render();
        return;
      }
      if (action === 'show-recent-conversation-list') {
        finishConversationBatchEditing();
        state.sidebarConversationList = 'recent';
        state.status = '';
      } else if (action === 'show-cloud-conversation-list') {
        finishConversationBatchEditing();
        state.sidebarConversationList = 'cloud';
        state.status = '';
      } else if (action === 'open-cloud-conversation') {
        state.current = await invoke('read_desktop_workspace', { workspaceId: target.dataset.workspaceId || '' });
        state.selectedConversationId = target.dataset.conversationId || null;
        state.pane = 'chat';
        state.status = '';
      } else if (action === 'context-menu-sync') {
        state.contextMenu = null;
        const workspaceId = state.current?.summary?.id;
        const conversationId = target.dataset.id;
        if (!workspaceId || !conversationId) throw new Error('当前对话不可用');
        // Render before any IPC so a slow network never makes the click look
        // inert. The native sync owner still verifies login and cloud state.
        state.status = '正在核对云端版本并提交…';
        beginAccountSyncProgress('sync');
        await waitForAccountSyncProgressPaint();
        const receipt = await invoke('sync_selected_desktop_conversation', { workspaceId, conversationId });
        if (receipt.status === 'UNKNOWN') {
          state.accountSync = { ...state.accountSync, pendingUnknown: { workspaceId, conversationId } };
          state.status = '云端提交正在后台核对；不会重复上传。';
          await completeAccountSyncProgress('已提交 1 个对话，正在后台核对。', 'pending');
          scheduleUnknownSyncReconciliation(workspaceId, conversationId);
        } else if (receipt.status === 'CONFLICT') {
          state.pane = 'settings';
          state.settingsSection = 'account';
          state.status = '云端版本已变化，本机未覆盖任何云端内容。';
          await completeAccountSyncProgress('云端版本已变化，本机未覆盖。', 'error');
        } else if (['SYNCED', 'UP_TO_DATE'].includes(receipt.status)) {
          state.status = receipt.status === 'UP_TO_DATE' ? '所选对话已是最新版本。' : '所选对话已加密上传并通过云端回读。';
          await completeAccountSyncProgress('', 'success');
        } else {
          throw new Error(`云端回写未完成：${receipt.status}`);
        }
        notifyAccountSync(['SYNCED', 'UP_TO_DATE'].includes(receipt.status) ? 'success' : 'attention');
        // The receipt has already completed the visible action. Refresh the
        // account card in the background instead of delaying its success cue.
        void loadDesktopAccountSync().then(render).catch(() => {});
      } else if (action === 'show-google-login-requirements') {
        state.status = '此 Desktop 尚未写入南枫云地址或公开访问密钥；不会打开浏览器、读取账号或上传数据。';
      } else if (action === 'sign-in-google-account') {
        if (accountSignInPending) {
          state.status = '仍在等待系统浏览器的 Google 授权；未重复打开登录窗口。';
          return;
        }
        accountSignInPending = true;
        state.status = '等待系统浏览器完成 Google 授权…';
        render();
        state.accountSync = await invoke('sign_in_desktop_google_account');
        state.accountRecovery = null;
        await restoreCachedCloudConversationList();
        state.status = '已建立南枫云应用账号会话；可直接读取云端会话。';
      } else if (action === 'sign-out-google-account') {
        state.accountSync = await invoke('sign_out_desktop_google_account');
        state.accountRecovery = null;
        state.cloudConversations = [];
        cloudListReadGeneration += 1;
        clearCloudConversationCache();
        state.status = '已退出账号；本机对话与工作区保留不变。';
      } else if (action === 'load-account-cloud-documents') {
        const readGeneration = ++cloudListReadGeneration;
        beginAccountSyncProgress('read');
        state.status = '正在读取云端列表…';
        render();
        await waitForAccountSyncProgressPaint();
        const receipt = await invoke('restore_all_desktop_cloud_conversations');
        const restored = Array.isArray(receipt?.restored) ? receipt.restored : [];
        const refreshedRows = await cloudRowsFromLocalEntries(restored);
        // A newer read, explicit cloud removal, or sign-out won the race. Its
        // list state is authoritative; this old response must not put rows back.
        if (readGeneration !== cloudListReadGeneration) return;
        state.accountSync = { ...state.accountSync, remoteDocuments: [] };
        state.sidebarConversationList = 'cloud';
        state.cloudConversations = mergeCloudConversationList(state.cloudConversations, refreshedRows, receipt?.cloudConversationKeys);
        // A successfully decoded remote presentation is authoritative even
        // when an older bridge has omitted its optional ID array.  In that
        // case it means zero pins; retaining a cached pin would make Desktop
        // disagree with Android's direct cloud read.
        let pinnedConversationIds = receipt?.cloudListPresentationPresent
          ? new Set(Array.isArray(receipt?.cloudPinnedConversationIds) ? receipt.cloudPinnedConversationIds : [])
          : null;
        // Missing presentation keeps the cached pins. A read must not wait on
        // (or implicitly perform) a series of cloud pin writes.
        if (pinnedConversationIds) {
          state.cloudConversations = state.cloudConversations.map(item => ({
            ...item,
            pinned: pinnedConversationIds.has(item.id),
            cloudPinned: pinnedConversationIds.has(item.id),
          }));
        }
        persistCloudConversationCache();
        state.pane = 'chat';
        const feedback = cloudReadFeedback(receipt);
        state.status = feedback.message;
        notifyAccountSync(feedback.kind === 'success' ? 'success' : 'attention');
        await completeAccountSyncProgress(feedback.message, feedback.kind);
        // Account metadata is not a prerequisite for already-restored rows.
        void loadDesktopAccountSync().then(render).catch(() => {});
      } else if (action === 'choose-selected-local-sync-start') {
        state.accountSync = await invoke('choose_desktop_selected_sync_start');
        state.status = '已选择以本机所选对话为起点；云端非空时仍会停下并报冲突。';
      } else if (action === 'toggle-periodic-account-sync') {
        state.accountSync = await invoke('set_desktop_periodic_sync', { enabled: !state.accountSync.periodicEnabled });
        state.status = '';
      } else if (action === 'reconcile-account-sync') {
        const receipt = await invoke('reconcile_desktop_conversation_sync', { workspaceId: target.dataset.workspaceId, conversationId: target.dataset.conversationId });
        if (receipt.status === 'SYNCED_AFTER_RECONCILE' || receipt.status === 'SYNCED_AFTER_REMOTE_RECONCILE') state.accountSync = { ...state.accountSync, pendingUnknown: null };
        state.status = receipt.status === 'SYNCED_AFTER_RECONCILE' ? '已回读确认上次提交成功，未重复上传。' : receipt.status === 'SYNCED_AFTER_REMOTE_RECONCILE' ? '已合并云端最新对话并完成同步。' : receipt.status === 'SAFE_TO_RETRY_EXPLICITLY' ? '云端未改变；如需重试，请在会话菜单再次明确发起。' : '云端仍在核对，未覆盖任何内容。';
        notifyAccountSync(receipt.status === 'SYNCED_AFTER_RECONCILE' || receipt.status === 'SYNCED_AFTER_REMOTE_RECONCILE' ? 'success' : 'attention');
        await loadDesktopAccountSync();
      }
      state.error = '';
    } catch (error) {
      state.error = `账号与同步操作未完成：${accountSyncErrorMessage(error)}`;
      if (action === 'sign-in-google-account') state.status = 'Google 授权未完成；本机数据与云端均未改变，可检查配置后重试。';
      if (action === 'context-menu-sync') await completeAccountSyncProgress('请检查网络或登录后重试。', 'error');
      if (action === 'load-account-cloud-documents') await completeAccountSyncProgress(accountSyncErrorMessage(error), 'error');
      if (['context-menu-sync', 'reconcile-account-sync'].includes(action)) notifyAccountSync('attention');
    } finally {
      // completeAccountSyncProgress owns the result lifetime. Clearing it here
      // immediately after the receipt/error turns the acknowledgement into a
      // one-frame flash, so sync/read must leave the completed card in place
      // until its own timeout expires.
      if (action === 'sign-in-google-account') accountSignInPending = false;
      if (action === 'load-account-cloud-documents') cloudListReadPending = false;
      if (action === 'copy-account-recovery-code' && target?.__copyIconFeedback) window.setTimeout(render, COPY_SUCCESS_DURATION_MS);
      else render();
    }
  })();
}, true);

async function bootDesktopShell() {
  let startupStage = 'WORKSPACE_READBACK';
  try {
    await refresh();
    startupStage = 'ORDINARY_DRAFT_READBACK';
    await loadOrdinaryComposerDraft();
    if (native) {
      startupStage = 'C16_VISUAL_ACCEPTANCE_GATE';
      c16ThemePreview = await invoke('read_desktop_c16_visual_acceptance_state') || '';
      c16PreviewState = parseC16Preview(c16ThemePreview);
    }
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
      if (!state.runtimeInfo?.automaticWorkSuppressed) {
        startupStage = 'REMINDER_NOTIFICATION_PERMISSION';
        await readReminderNotificationPermission();
        startupStage = 'REMINDER_NOTIFICATION_LISTENER';
        await installReminderNotificationActionListener();
        startupStage = 'REMINDER_NOTIFICATION_FLUSH';
        await flushReminderNotifications();
      }
      startupStage = 'MODEL_SETTINGS_READBACK';
      await loadModelServiceSettings();
      startupStage = 'HISTORY_KNOWLEDGE_READBACK';
      await loadDesktopHistoryKnowledge();
      if (state.productSettings.historyLibraryEnabled && !state.runtimeInfo?.automaticWorkSuppressed) {
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
      startupStage = 'CLOUD_LIST_CACHE_READBACK';
      await restoreCachedCloudConversationList();
      startupStage = 'V2_EXCHANGE_READBACK';
      state.v2CommittedExchanges = await invoke('list_desktop_workspace_exchange_v2_committed');
      globalThis.__nanfengV2CommittedExchanges = state.v2CommittedExchanges;
      applyC16Preview('Tauri 原生');
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

// Dismiss only the memory overflow surface; its actions retain their existing owners.
document.addEventListener('click', event => {
  for (const menu of document.querySelectorAll('.memory-reference-menu[open]')) {
    if (!menu.contains(event.target) || event.target.closest('[data-action]')) menu.open = false;
  }
});
document.addEventListener('keydown', event => {
  if (event.key !== 'Escape') return;
  const menu = document.querySelector('.memory-reference-menu[open]');
  if (menu) { menu.open = false; event.preventDefault(); event.stopImmediatePropagation(); menu.querySelector('summary')?.focus(); }
}, true);

// Only transient action menus participate; persistent content disclosures stay open.
document.addEventListener('click', event => {
  const menus = [...document.querySelectorAll('.conversation-lifecycle-actions[open], .memory-reference-menu[open]')];
  const outside = menus.filter(menu => !menu.contains(event.target));
  if (!outside.length) return;
  outside.forEach(menu => { menu.open = false; });
  if (!menus.some(menu => menu.contains(event.target))) {
    event.preventDefault();
    event.stopImmediatePropagation();
  }
}, true);
document.addEventListener('keydown', event => {
  if (event.key !== 'Escape') return;
  const menu = document.querySelector('.conversation-lifecycle-actions[open]');
  if (!menu) return;
  menu.open = false;
  event.preventDefault();
  event.stopImmediatePropagation();
  menu.querySelector('summary')?.focus();
}, true);

// Reflow wrapped drafts when the window or sidebar changes the available width.
const composerResizeObserver = new ResizeObserver(entries => {
  if (entries.length) resizeComposer(document.getElementById("chat-composer"));
});
composerResizeObserver.observe(app);
