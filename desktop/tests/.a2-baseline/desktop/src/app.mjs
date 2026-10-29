import { createModelSettingsController } from './model-settings-controller.mjs';
import { modelSettingsProviderTarget, installModelSettingsKeyboardNavigation } from './model-settings-browser.mjs';
import { workspaceUiEnabled, chatUiActionAllowed, chatUiRoute } from './chat-ui-policy.mjs';
import { recordDomainConflict, renderDomainConflict, reloadDomainEditor, memorySummaryValue, updateMemorySummaryDraft } from './domain-editor-conflict.mjs';
import { readKnowledgeDuplicates, renderKnowledgeDuplicates } from './knowledge-duplicates.mjs';
import { installModalLayerOwner, installActionMenuLayerOwner, installComposerTransientLayerOwner, installMessageMenuLayerOwner } from './modal-layer-owner.mjs';
import { resizeComposer } from './composer-size.mjs';
import { readComposerDraftRoute, createDraftCloseGuard, transitionComposerDraftRoute } from './composer-draft-recovery.mjs';
import { createLocalWriteBarrier } from './local-write-barrier.mjs';
import { createDraftWriteQueue } from './draft-write-queue.mjs';
import { renderSafeMarkdown } from './safe-markdown.mjs';
import { createPdfKnowledgeController } from './pdf-knowledge-tasks.mjs';
import { createWebKnowledgeController } from './web-knowledge-tasks.mjs';
import { renderAnswerInformation } from './answer-information.mjs';
import { cloudReadFeedback } from './cloud-read-feedback.mjs';
import { createUtilityPageNavigation } from './utility-page-navigation.mjs';
import { installRuntimeChatScrolling, commitRuntimeChatShell, commitRetainedChatShell } from './runtime-chat-commit.mjs';
import { sourceDisplayTitle } from './source-link-presentation.mjs';
import { createSearchResultCache } from './search-result-cache.mjs';
import { attachmentPreviewKey } from './attachment-preview-key.mjs';
import { conversationBranchLeaves } from './conversation-message-order.mjs';
import { memorySummarySuggestion } from './memory-summary-policy.mjs';
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
import { invokeForDataArea, invokeForAuthorizedDataAreaOperation } from './data-area-invoke.mjs';

const app = document.querySelector('#app');
installModalLayerOwner(app);
installActionMenuLayerOwner(app);
installComposerTransientLayerOwner(app, ({ navigateBack = false } = {}) => closeTopOverlay({ navigateComposerLayerBack: navigateBack }));
installMessageMenuLayerOwner(app, () => closeTopOverlay());
installRuntimeChatScrolling(app);
window.addEventListener('error', event => { console.error('Desktop runtime error', event.error || event.message); });
window.addEventListener('unhandledrejection', event => {
  if (event.reason?.code === 'STALE_DATA_AREA') { event.preventDefault(); return; }
  console.error('Desktop async runtime error', event.reason);
});
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
// The selected settings tab is a view preference; service configuration and
// credentials remain owned by native SQLite and the private credential store.
const modelProviderTabKey = 'nanfeng-ai.desktop.settings.model-provider-tab.v1';
// This stores only local workspace/conversation IDs. Row content remains in
// Rust-owned SQLite, so the list can return after restart without a network read.
const cloudConversationCacheKey = 'nanfeng-ai.desktop.sync-cloud-list.v1';
function areaCloudConversationCacheKey(owner = state) {
  return owner.dataArea === 'WORK' ? `${cloudConversationCacheKey}.WORK` : cloudConversationCacheKey;
}
const parityPreferences = new DesktopParityPreferences(window.localStorage);
const systemDarkQuery = window.matchMedia?.('(prefers-color-scheme: dark)');
function readSidebarWidth() { try { return clampDesktopSidebarWidth(Number(localStorage.getItem(settingsLocalKey)), window.innerWidth); } catch { return 256; } }
function persistSidebarWidth(width) { try { localStorage.setItem(settingsLocalKey, String(width)); } catch { /* browser preview may deny local storage */ } }
function readModelProviderTab() {
  try {
    const saved = localStorage.getItem(modelProviderTabKey);
    if (MODEL_SERVICE_PREVIEW_SETTINGS.some(item => item.providerId === saved)) return saved;
  } catch { /* browser preview may deny local storage */ }
  return 'OPENROUTER';
}
function persistModelProviderTab(providerId) {
  if (!MODEL_SERVICE_PREVIEW_SETTINGS.some(item => item.providerId === providerId)) return;
  try { localStorage.setItem(modelProviderTabKey, providerId); } catch { /* browser preview may deny local storage */ }
}
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
const cloudConversationEntryLimit = 10_000;
function readCloudConversationCache(owner = state) {
  try {
    const cached = JSON.parse(window.localStorage.getItem(areaCloudConversationCacheKey(owner)) || 'null');
    if (![1, 2].includes(cached?.version) || typeof cached.email !== 'string' || !Array.isArray(cached.entries)) return null;
    return {
      email: cached.email,
      entries: dedupeCloudConversationEntries(cached.entries).map(item => ({
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
  try { window.localStorage.setItem(areaCloudConversationCacheKey(), JSON.stringify({ version: 2, email, entries })); } catch { /* cache is optional */ }
}
function clearCloudConversationCache() {
  try {
    window.localStorage.removeItem(cloudConversationCacheKey);
    window.localStorage.removeItem(`${cloudConversationCacheKey}.WORK`);
  } catch { /* cache is optional */ }
}
const initialProductSettings = parityPreferences.readProductSettings();
function createAreaState(dataArea = 'CHAT') { return { dataArea, workspaces: [], current: null, pane: 'chat', selectedConversationId: null, sidebarConversationList: 'recent', cloudConversations: [], batchEditing: false, batchSelectedConversationKeys: new Set(), chatgptTask: null, claudeTask: null, p6kTask: null, markdownKnowledgeTask: null, markdownKnowledgeTasks: [], markdownKnowledgeHasMore: false, jsonKnowledgeTask: null, jsonKnowledgeTasks: [], jsonKnowledgeHasMore: false, pdfKnowledgeTask: null, pdfKnowledgeTasks: [], pdfKnowledgeHasMore: false, webKnowledgeTask: null, webKnowledgeTasks: [], webKnowledgeHasMore: false, jsonKnowledgeExportReceipt: null, composerDraft: '', composerAttachments: [], temporaryConversation: null, profileOpen: false, sidebarOpen: false, railCollapsed: false, sidebarWidth: readSidebarWidth(), settingsSection: 'personalization', settingsSearch: '', settingsPicker: null, productSettings: initialProductSettings, personalizationDraft: { ...initialProductSettings }, personalizationDirty: false, modelServiceSettings: MODEL_SERVICE_PREVIEW_SETTINGS.map(item => ({ ...item, presets: [...item.presets], nonChatCapabilities: [...item.nonChatCapabilities] })), modelProviderId: readModelProviderTab(), modelServiceDraft: null, modelCredentialDraft: null, modelCredentialEdited: false, modelCredentialVisible: false, modelSettingsSaving: false, modelSettingsTesting: false, modelSettingsNotice: '', modelSettingsError: '', usageLedger: { records: [], inputTokens: 0, outputTokens: 0, cachedInputTokens: 0 }, usageSection: 'conversation', contextSelectionRecords: [], diagnosticRecords: [], invocationRecords: [], privacyInventory: { totalBytes: 0, aggregates: [] }, localBackup: { working: false, operation: null, preflight: null, replaceLocal: false, notice: '', error: '', restartRequired: false, interrupted: false }, showArchived: false, showDeleted: false, contextMenu: null, composerAddOpen: false, composerAddPage: 'root', cameraCaptureOpen: false, cameraCaptureReady: false, cameraCaptureBusy: false, cameraCaptureError: '', conversationPreferences: { revision: 0, toneOverride: null, webSearchOverride: null }, temporaryModelOpen: false, p6gModelPickerOpen: false, p6gCatalog: null, p6gGlobalDefault: { revision: 0, tier: null }, p6gSelection: null, chatScrollPositions: new Map(), chatAtLatest: true, transcriptRailTrackingConversationId: null, pendingChatScrollToLatestId: null, pendingChatSendScrollToLatestId: null, scrollToLatestAnimationId: null, focusComposerAfterScrollToLatest: false, inspectorOpen: true, treeOpen: false, preflight: null, dialog: null, history: { canUndo: false, canRedo: false, recycleBin: [], modelMetadata: [] }, agentRuns: [], connection: previewConnection, p6eAcceptance: { enabled: false, receipt: null }, status: native ? '本地工作区已就绪；正在读取联网模型配置。' : 'Web 预览不会读写 Desktop 数据库。', error: '', accountSyncProgress: null, scale: 1, searchResults: [], searchPage: { hits: [], textCount: 0, attachmentCount: 0, totalCount: 0, hasMore: false, indexCurrent: true }, searchPanel: false, searchCategory: 'all', searchSortMode: 'default', searchFileType: 'all', searchFileTypeOpen: false, searchLoading: false, searchLoadingMore: false, searchError: '', searchEvidenceLabel: '', searchAnchorMessageId: null, searchAnchorAttachmentId: null, searchRevealTarget: null, searchHistory: [], searchHistoryOpen: false, searchHistoryHighlighted: null, searchHistoryManuallyOpened: false, searchScrollSnapshot: null, searchAttachmentMenu: null, suppressSearchHistoryFocus: false, imageThumbnails: {}, imageThumbnailPending: new Set(), videoThumbnails: {}, videoThumbnailPending: new Set(), searchAttachmentPreviews: {}, searchAttachmentPreviewPending: new Set(), assistantImageSelections: new Map(), imagePreview: null, pdfPreview: null, videoPreview: null, audioPreview: null, textPreview: null, previewBoundary: null, previewWorkspaceId: null, appearance: parityPreferences.readAppearance(), favoriteConversationIds: new Set(), unreadConversationIds: new Set(), manualUnreadAtMs: new Map(), conversationFindOpen: false, conversationFindQuery: '', conversationFindMatches: [], conversationFindIndex: 0, runtimeInfo: { version: '读取中', platform: navigator.platform || 'Desktop', arch: '本机架构' } }; }
function initializeAreaState(areaState) {
  areaState.selectedWorkProjectId = null;
  areaState.assistantMessageMenu = null;
  areaState.cloudConversationOpening = null;
  areaState.pendingNewConversationModelId = null;
  areaState.p6gModelPickerTier = null;
  areaState.modelCredentialDraft = '';
  areaState.transcription = { settings: { ...TRANSCRIPTION_PREVIEW_STATE.settings }, tasks: [] };
  areaState.transcriptionMode = 'document';
  areaState.selectedTranscriptionTaskId = null;
  areaState.transcriptionBusyTaskId = null;
  areaState.searchLocatedArchivedConversationId = null;
  areaState.searchReturnActive = false;
  areaState.appSettingsRevision = 0;
  areaState.settingsCapabilities = { ...DESKTOP_SETTINGS_CAPABILITIES };
  areaState.accountSync = { configured: false, state: 'NOT_CONFIGURED', recoveryState: 'UNAVAILABLE', periodicEnabled: false, rotationPending: false, selectedConversationCount: 0, diagnostics: [], notifications: [] };
  areaState.accountRecovery = null;
  areaState.historyKnowledge = { enabled: false, paused: true, lastDispatchedAtMs: null, nextEligibleAtMs: null, candidates: [] };
  areaState.settingsScrollPositions = new Map();
  areaState.settingsConversationReturn = null;
  areaState.workspaceReturn = null;
  areaState.memorySummaryNotice = '';
  areaState.sidebarScrollTop = 0;
  areaState.reminders = { drafts: [], plans: [], diagnostics: [] };
  areaState.reminderEditor = null;
  areaState.reminderNotificationPermission = 'default';
  areaState.reminderNotificationBridge = { supported: false, initialized: false, listenerReady: false, safeCode: 'NOT_READ', pendingActionCount: 0 };
  areaState.selectedReminderPlanId = null;
  areaState.backgroundRuntime = { desiredEnabled: false, installed: false, safeCode: 'NOT_READ', label: '', updatedAtMs: 0 };
  areaState.conversationReadState = { workspaceId: null, conversations: [] };
  areaState.unreadConversationIds = new Set();
  areaState.manualUnreadAtMs = new Map();
  areaState.pendingCreatedConversationRouteWorkspaceId = null;
  areaState.pendingChatSubmission = null;
  areaState.localBackupArea = areaState.dataArea;
  areaState.archivePreview = null;
  areaState.v2CommittedExchanges = [];
  areaState.nanfengKnowledgeTask = null;
  areaState.chatSearch = '';
  return areaState;
}
let state = initializeAreaState(createAreaState());
const dataAreaStates = new Map([['CHAT', state]]);
function invalidateAccountCloudLists() {
  for (const owner of dataAreaStates.values()) {
    advanceCloudListRead(owner);
    cloudListReadPending.delete(owner);
    owner.cloudConversations = [];
    owner.accountRecovery = null;
    owner.accountSyncProgress = null;
  }
}
function selectedBackupAreaState() {
  const area = state.runtimeInfo?.dataAreasReady && ['CHAT', 'WORK'].includes(state.localBackupArea)
    ? state.localBackupArea : state.dataArea;
  if (area === state.dataArea) return state;
  let owner = dataAreaStates.get(area);
  if (!owner) {
    owner = initializeAreaState(createAreaState(area));
    dataAreaStates.set(area, owner);
  }
  return owner;
}
const invokeSelectedBackup = (owner, command, args = {}) => invokeForDataArea(tauriBridge, command, args, owner, selectedBackupAreaState);
let areaSwitchPending = false;
let areaReferencePending = false;
let startupUiStage = native ? 'pending' : 'ready';
let queuedStartupAreaRoute = null;
globalThis.__nanfengDesktopWorkState = state;
// Presentation policy intercepts retired routes before any business handler.
app.addEventListener('click', event => {
  const control = event.target.closest?.('[data-action]');
  if (control && !chatUiActionAllowed(control.dataset.action, control.dataset, state.runtimeInfo)) {
    event.preventDefault(); event.stopImmediatePropagation();
  }
}, true);

let transientToast = null;
let transientToastTimer = null;
let accountSyncProgressResultTimer = null;
const cloudListReadGenerations = new WeakMap();
const cloudListReadPending = new WeakMap();
function advanceCloudListRead(owner = state) {
  const generation = (cloudListReadGenerations.get(owner) || 0) + 1;
  cloudListReadGenerations.set(owner, generation);
  return generation;
}
function currentCloudListRead(owner) { return cloudListReadGenerations.get(owner) || 0; }
let cloudOpenAreaGeneration = 0;
const pendingSyncReconciliations = new Set();
const accountSyncReadGenerations = new WeakMap();

const openCloudConversationRoute = createCloudConversationOpenRoute({
  reuse: ({ workspaceId, conversationId }) => {
    const current = state.current;
    if (current?.summary?.id !== workspaceId || !resolveConversation(current, conversationId)) return null;
    return {
      current,
      history: state.history,
      alreadySelected: !state.cloudConversationOpening && state.pane === (state.dataArea === 'WORK' ? 'work' : 'chat') && state.selectedConversationId === conversationId,
    };
  },
  load: async ({ workspaceId, conversationId, areaOwner }) => {
    const [current, history] = await Promise.all([
      invoke('read_desktop_workspace', { workspaceId }, areaOwner),
      invoke('read_desktop_workbench_history', { workspaceId }, areaOwner),
    ]);
    if (!resolveConversation(current, conversationId)) throw new Error('云端会话详情暂不可用，请先读取云端列表后重试。');
    return { current, history };
  },
  // Model preferences decorate the composer only. They are intentionally read
  // after the selected conversation is on screen, so a short SQLite delay can
  // never leave a cloud click looking like an empty new conversation.
  loadPreferences: ({ workspaceId, conversationId, areaOwner, areaGeneration }) => {
    if (areaOwner !== state || areaGeneration !== cloudOpenAreaGeneration) return null;
    return invoke('read_desktop_p6g_selection', { workspaceId, conversationId }, areaOwner);
  },
  onPending: route => {
    state.cloudConversationOpening = route;
    state.pane = state.dataArea === 'WORK' ? 'work' : 'chat';
    state.selectedConversationId = null;
    state.p6gSelection = null;
    state.conversationPreferences = { revision: 0, toneOverride: null, webSearchOverride: null };
    state.error = '';
    render();
  },
  onOpened: (route, { current, history }) => {
    if (route.areaOwner !== state || route.areaGeneration !== cloudOpenAreaGeneration) return;
    state.current = current;
    state.history = history;
    if (state.dataArea === 'WORK') {
      const opened = (current?.exchange?.conversations || []).find(item => item.id === route.conversationId);
      state.selectedWorkProjectId = opened?.projectId || null;
    }
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
    if (route.areaOwner !== state || route.areaGeneration !== cloudOpenAreaGeneration) return;
    if (state.current?.summary?.id !== route.workspaceId || state.selectedConversationId !== route.conversationId) return;
    state.p6gSelection = selection;
    state.conversationPreferences = selection?.conversationOverride || { revision: 0, toneOverride: null, webSearchOverride: null };
    render();
  },
  onPreferencesFailure: () => {
    // The transcript is already visible. Preference reads may retry naturally
    // on the next selection; never replace content with a secondary failure.
  },
  onFailure: (route, error) => {
    if (route.areaOwner !== state || route.areaGeneration !== cloudOpenAreaGeneration) return;
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

async function completeAccountSyncProgress(message, kind = 'success', owner = state) {
  const startedAt = owner.accountSyncProgress?.startedAt;
  if (Number.isFinite(startedAt)) {
    const remaining = ACCOUNT_SYNC_PROGRESS_MIN_VISIBLE_MS - (performance.now() - startedAt);
    if (remaining > 0) await new Promise(resolve => window.setTimeout(resolve, remaining));
  }
  if (state !== owner) return;
  const completedProgress = { ...owner.accountSyncProgress, result: { message, kind } };
  owner.accountSyncProgress = completedProgress;
  window.clearTimeout(accountSyncProgressResultTimer);
  render();
  accountSyncProgressResultTimer = window.setTimeout(() => {
    if (state === owner && owner.accountSyncProgress === completedProgress) {
      owner.accountSyncProgress = null;
      render();
    }
  }, ACCOUNT_SYNC_RESULT_VISIBLE_MS);
}
let composerModelPricingRefreshTimer = null;
if (c16PreviewState) {
  state.appearance = {
    mode: c16PreviewState.appearance.mode,
    fontSize: c16PreviewState.font.id,
    themeColor: 'orange',
  };
}

let accountSignInPending = false;
let accountSignInToken = 0;
// A submitted message may move the viewport to its freshly-created reply once.
// It is deliberately a content-free client receipt. Runtime events also fire for
// every streaming checkpoint, accounting update and title update, so a workspace
// or conversation ID alone is not sufficient to identify the originating Send.

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

async function syncPersistedConversationContinuation(workspaceId, conversationId, owner = state,
  selection = { email: owner.accountSync?.email || null, keys: [...(owner.accountSync?.syncedConversationKeys || [])] }) {
  try {
    const invokeOwner = (command, args) => invokeForAuthorizedDataAreaOperation(tauriBridge, command, args, owner);
    if (!selection.email || !selection.keys.includes(`${workspaceId}:${conversationId}`)) return;
    // The visible area's account cache can lag a sign-out or an A→B switch.
    // Check the active native session before continuing the original selection.
    const currentAccount = await invokeOwner('read_desktop_account_sync', {});
    if (currentAccount?.email !== selection.email) return;
    const receipt = await syncSelectedContinuation({ invoke: invokeOwner, workspaceId, conversationId,
      syncedConversationKeys: selection.keys });
    if (state !== owner || owner.current?.summary?.id !== workspaceId
        || owner.selectedConversationId !== conversationId) return;
    if (receipt?.status === 'UNKNOWN') {
      state.accountSync = { ...state.accountSync, pendingUnknown: { workspaceId, conversationId } };
      scheduleUnknownSyncReconciliation(workspaceId, conversationId);
      state.error = '回复已保存在本机；云端提交结果正在核对，尚未确认同步完成。';
    }
  } catch (error) {
    if (state !== owner || owner.current?.summary?.id !== workspaceId
        || owner.selectedConversationId !== conversationId) return;
    // A failed cloud write must never turn a completed provider request into
    // "message not submitted" or restore its draft for accidental resending.
    state.error = `回复已保存在本机，但云端续同步未完成：${String(error)}`;
  }
}

function queueOrdinaryChatRuntimeRefresh(payload) {
  if (!payload?.workspaceId || (payload.dataArea || 'CHAT') !== state.dataArea
    || state.current?.summary?.id !== payload.workspaceId) return;
  // Submission acknowledgement is identity-bound, unlike coalesced display
  // updates. Consume it before another conversation replaces the refresh slot.
  claimSubmittedChatRoute(payload);
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

function claimSubmittedChatRoute(payload, owner = state) {
  const conversationId = payload?.conversationId || null;
  const pending = owner.pendingChatSubmission;
  if (!conversationId || !pending
    || pending.workspaceId !== payload?.workspaceId
    || pending.clientSubmissionId !== payload?.clientSubmissionId) return false;
  if (pending.conversationId && pending.conversationId !== conversationId) {
    owner.pendingChatSubmission = null;
    return false;
  }
  if (!pending.conversationId
    && !owner.selectedConversationId
    && owner.pendingCreatedConversationRouteWorkspaceId === payload.workspaceId) {
    owner.selectedConversationId = conversationId;
    owner.pendingCreatedConversationRouteWorkspaceId = null;
  }
  const ownsSubmittedRoute = owner.selectedConversationId === conversationId;
  owner.pendingChatSubmission = null;
  if (ownsSubmittedRoute) {
    owner.pendingChatSendScrollToLatestId = conversationId;
  }
  return ownsSubmittedRoute;
}

async function flushOrdinaryChatRuntimeRefresh() {
  if (ordinaryChatRuntimeRefreshInFlight) return;
  const owner = state;
  const payload = ordinaryChatRuntimeRefreshPending;
  ordinaryChatRuntimeRefreshPending = null;
  if (!payload) return;
  if (state.dataArea && (payload.dataArea || 'CHAT') !== state.dataArea) return;
  if (state.current?.summary?.id !== payload.workspaceId) return;
  ordinaryChatRuntimeRefreshInFlight = true;
  try {
    const current = await invoke('read_desktop_workspace', { workspaceId: payload.workspaceId });
    if (state !== owner || state.current?.summary?.id !== payload.workspaceId) return;
    state.current = current;
    claimSubmittedChatRoute(payload);
    await loadConversationReadState({ observeSelected: true });
    state.error = '';
    render({ runtimeRefresh: true });
  } catch (error) {
    if (state !== owner || error?.code === 'STALE_DATA_AREA') return;
    state.error = `聊天增量已持久化，但界面刷新失败：${String(error)}`;
    render({ runtimeRefresh: true });
  } finally {
    ordinaryChatRuntimeRefreshInFlight = false;
    if (ordinaryChatRuntimeRefreshPending) scheduleOrdinaryChatRuntimeRefresh();
  }
}

const tauriEvents = window.__TAURI__?.event;
if (native && tauriEvents?.listen) {
  void tauriEvents.listen('desktop-ordinary-chat-runtime-v1', event => {
    const payload = event?.payload || {};
    if ((payload.dataArea || 'CHAT') === state.dataArea) queueOrdinaryChatRuntimeRefresh(payload);
  });
  void tauriEvents.listen('desktop-reminder-runtime-v1', async event => {
    if ((event?.payload?.dataArea || 'CHAT') !== state.dataArea) return;
    const owner = state;
    try {
      await loadDesktopReminders();
      await loadDesktopUsageLedger();
      await flushReminderNotifications();
      if (state !== owner) return;
      state.error = '';
    } catch (error) {
      if (state !== owner || error?.code === 'STALE_DATA_AREA') return;
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

function reloadFavoriteConversationIds({ runtimeRefresh = false } = {}) {
  const owner = state;
  const workspaceId = state.current?.summary?.id;
  if (!workspaceId) { state.favoriteConversationIds = new Set(); return; }
  if (!native) { state.favoriteConversationIds = parityPreferences.readFavoriteConversationIds(workspaceId); return; }
  const generation = advanceFavoriteRead(owner);
  void invoke('read_desktop_favorite_conversation_ids', { workspaceId }).then(ids => {
    if (state !== owner || state.current?.summary?.id !== workspaceId
        || favoriteReadGenerations.get(owner) !== generation) return;
    state.favoriteConversationIds = new Set(ids);
    render({ runtimeRefresh });
  }).catch(error => {
    if (state !== owner || state.current?.summary?.id !== workspaceId || error?.code === 'STALE_DATA_AREA'
        || favoriteReadGenerations.get(owner) !== generation) return;
    state.error = `收藏会话读取失败：${String(error)}`;
    render({ runtimeRefresh });
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
  const owner = state;
  while (!isSettled()) {
    await new Promise(resolve => window.setTimeout(resolve, 120));
    if (state !== owner) return;
    if (isSettled() || state.current?.summary?.id !== workspaceId) continue;
    // Runtime events and this polling backstop share one snapshot reader.
    // A queued event will get a fresh read after this one finishes.
    if (ordinaryChatRuntimeRefreshInFlight) continue;
    ordinaryChatRuntimeRefreshInFlight = true;
    try {
      const current = await invoke('read_desktop_workspace', { workspaceId });
      if (isSettled() || state !== owner || state.current?.summary?.id !== workspaceId) continue;
      state.current = current;
      render({ runtimeRefresh: true });
    } catch { /* final command result owns user-visible errors */ }
    finally {
      ordinaryChatRuntimeRefreshInFlight = false;
      if (ordinaryChatRuntimeRefreshPending) scheduleOrdinaryChatRuntimeRefresh();
    }
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

// All Markdown task requests capture the area/workspace before awaiting picker or IPC.
async function loadMarkdownKnowledgeTask(owner = state) {
  const workspaceId = owner.current?.summary?.id;
  if (!native || !workspaceId) return;
  const generation = owner.markdownKnowledgeReadGeneration = (owner.markdownKnowledgeReadGeneration || 0) + 1;
  const [task, page] = await Promise.all([invoke('read_latest_desktop_markdown_knowledge', { workspaceId }, owner), invoke('list_desktop_markdown_knowledge_tasks', { workspaceId, offset: 0 }, owner)]);
  if (owner.current?.summary?.id !== workspaceId || owner.markdownKnowledgeReadGeneration !== generation) return;
  owner.markdownKnowledgeTask = task; owner.markdownKnowledgeTasks = page.tasks; owner.markdownKnowledgeHasMore = page.hasMore;
  if (state === owner && owner.pane === 'knowledge') render();
}
function markdownKnowledgeTaskCard(task) {
  if (!task) return '';
  const labels = { SELECTED: '正在复制', AWAITING_CONFIRMATION: '等待逐项确认', PARTIALLY_COMPLETED: '部分已处理', COMPLETED: '已处理', FAILED: '导入失败', CANCELLED: '已取消' };
  return `<section class="memory-rail markdown-knowledge-task" data-task-id="${escape(task.id)}"><div class="section-head"><h2>Markdown 导入 · ${escape(labels[task.status] || '待核对')}</h2></div><label>导入任务<select data-action="select-markdown-knowledge-task">${(state.markdownKnowledgeTasks || []).map(item => `<option value="${escape(item.id)}" ${item.id === task.id ? 'selected' : ''}>${escape(item.displayName)} · ${escape(labels[item.status] || item.status)}</option>`).join('')}</select></label>${state.markdownKnowledgeHasMore ? '<button data-action="more-markdown-knowledge-tasks">读取更多导入任务</button>' : ''}<p>${escape(task.displayName)} · 文件只保存在本机，确认前不会写入知识。</p>${task.safeErrorCode ? '<p class="security-note">文件未通过格式或安全校验。原任务已保留，可取消或从私有副本重新检查。</p>' : ''}<ul>${task.items.map(item => `<li><span>${escape(item.candidate.title)} · ${escape({ PENDING_CONFIRMATION: '待确认', CONFIRMED: '已保存', SKIPPED: '已跳过' }[item.status] || item.status)}</span>${item.status === 'PENDING_CONFIRMATION' && !['CANCELLED', 'FAILED'].includes(task.status) ? `<button data-action="review-markdown-knowledge" data-ordinal="${item.ordinal}">审阅并保存</button><button data-action="skip-markdown-knowledge" data-ordinal="${item.ordinal}">跳过</button>` : ''}</li>`).join('')}</ul><div class="item-actions">${task.status === 'FAILED' ? '<button data-action="retry-markdown-knowledge">重新检查私有副本</button>' : ''}${!['CANCELLED', 'COMPLETED'].includes(task.status) ? '<button data-action="cancel-markdown-knowledge">取消此导入</button>' : ''}</div></section>`;
}
async function handleMarkdownKnowledgeAction(event) {
  const target = event.target.closest?.('[data-action]');
  const action = target?.dataset.action;
  if (action === 'select-markdown-knowledge-task' && event.type === 'click') return;
  if (!['select-markdown-knowledge', 'review-markdown-knowledge', 'confirm-markdown-knowledge', 'skip-markdown-knowledge', 'cancel-markdown-knowledge', 'retry-markdown-knowledge', 'select-markdown-knowledge-task', 'more-markdown-knowledge-tasks'].includes(action)) return;
  event.preventDefault(); event.stopImmediatePropagation();
  const owner = state, workspaceId = owner.current?.summary?.id, dialog = owner.dialog;
  const current = () => state === owner && owner.current?.summary?.id === workspaceId;
  if (!native || !workspaceId) { owner.error = '请在本机应用中选择当前工作区后导入。'; render(); return; }
  let startingTaskId = null;
  try {
    if (action === 'select-markdown-knowledge-task') {
      const generation = owner.markdownKnowledgeReadGeneration = (owner.markdownKnowledgeReadGeneration || 0) + 1;
      const task = await invoke('read_desktop_markdown_knowledge', { workspaceId, taskId: target.value }, owner);
      if (!current() || owner.markdownKnowledgeReadGeneration !== generation) return; owner.markdownKnowledgeTask = task;
    } else if (action === 'more-markdown-knowledge-tasks') {
      const page = await invoke('list_desktop_markdown_knowledge_tasks', { workspaceId, offset: owner.markdownKnowledgeTasks.length }, owner);
      if (!current()) return; owner.markdownKnowledgeTasks.push(...page.tasks); owner.markdownKnowledgeHasMore = page.hasMore;
    } else if (action === 'select-markdown-knowledge') {
      const selectedPath = await dialogInvoke('open', { multiple: false, directory: false, filters: [{ name: 'Markdown 知识', extensions: ['md', 'markdown'] }] });
      if (!selectedPath || !current() || owner.dialog !== dialog) return;
      const task = await invoke('stage_desktop_markdown_knowledge', { workspaceId, selectedPath }, owner);
      if (!current() || owner.dialog !== dialog) return;
      owner.markdownKnowledgeTask = task; owner.dialog = null; owner.pane = 'knowledge';
      await loadMarkdownKnowledgeTask(owner); if (!current()) return;
      owner.status = task.status === 'FAILED' ? 'Markdown 未通过校验；失败任务已保留。' : 'Markdown 已在本机解析；请逐项审阅后保存。';
    } else {
      const task = owner.markdownKnowledgeTask; startingTaskId = task?.id || null;
      if (!task || task.workspaceId !== workspaceId) throw new Error('当前工作区没有可处理的 Markdown 任务。');
      if (target.closest?.('[data-task-id]')?.dataset.taskId !== task.id) throw new Error('导入任务已变化，请重新读取。');
      const ordinal = Number(target.dataset.ordinal ?? dialog?.ordinal);
      if (action === 'review-markdown-knowledge') {
        const item = task.items.find(item => item.ordinal === ordinal && item.status === 'PENDING_CONFIRMATION');
        if (!item) throw new Error('待确认条目已变化，请重新读取。');
        owner.dialog = { kind: 'markdown-knowledge-review', taskId: task.id, ordinal, item: item.candidate, draft: {} };
      } else {
        let updated;
        if (action === 'confirm-markdown-knowledge') {
          if (dialog?.kind !== 'markdown-knowledge-review' || dialog.taskId !== task.id) throw new Error('请先审阅当前条目。');
          const candidate = { title: document.querySelector('#markdown-knowledge-title')?.value || '', body: document.querySelector('#markdown-knowledge-body')?.value || '', tags: (document.querySelector('#markdown-knowledge-tags')?.value || '').split(',').map(v => v.trim()).filter(Boolean) };
          const projectId = document.querySelector('#markdown-knowledge-project')?.value || null;
          updated = await invoke('confirm_desktop_markdown_knowledge_item', { args: { workspaceId, taskId: task.id, ordinal, candidate, projectId } }, owner);
        } else {
          const command = { 'skip-markdown-knowledge': 'skip_desktop_markdown_knowledge_item', 'cancel-markdown-knowledge': 'cancel_desktop_markdown_knowledge', 'retry-markdown-knowledge': 'retry_desktop_markdown_knowledge' }[action];
          updated = await invoke(command, { workspaceId, taskId: task.id, ...(action === 'skip-markdown-knowledge' ? { ordinal } : {}) }, owner);
        }
        if (!current() || owner.markdownKnowledgeTask?.id !== task.id) return;
        owner.markdownKnowledgeTask = updated;
        if (owner.dialog === dialog && dialog?.kind === 'markdown-knowledge-review') owner.dialog = null;
        if (action === 'confirm-markdown-knowledge') { owner.status = '已保存为本机知识，原文件与来源回执保留。'; await refresh(); if (!current()) return; }
      }
    }
    owner.error = ''; if (current()) render();
  } catch (error) { if (!current() || (startingTaskId && owner.markdownKnowledgeTask?.id !== startingTaskId) || error?.code === 'STALE_DATA_AREA') return; owner.error = 'Markdown 处理未完成。请检查格式、正文长度、标签或敏感内容后再试；没有自动保存或联网。'; render(); }
}
app.addEventListener('click', event => { void handleMarkdownKnowledgeAction(event); }, true);
app.addEventListener('click', event => {
  const target=event.target.closest?.('[data-action="reload-domain-editor"]');
  if (!target) return;
  event.preventDefault();event.stopImmediatePropagation();
  void reloadDomainEditor({getState:()=>state,invoke,render});
},true);

app.addEventListener('click', event => {
  const target=event.target.closest?.('[data-action="read-knowledge-duplicates"]');
  if (!target) return;
  event.preventDefault();event.stopImmediatePropagation();
  void readKnowledgeDuplicates({getState:()=>state,invoke,render});
},true);

app.addEventListener('change', event => { if (event.target.matches?.('[data-action=select-markdown-knowledge-task]')) void handleMarkdownKnowledgeAction(event); }, true);

async function loadJsonKnowledgeTask(owner = state) {
  const workspaceId = owner.current?.summary?.id;
  if (!native || !workspaceId) return;
  const generation = owner.jsonKnowledgeReadGeneration = (owner.jsonKnowledgeReadGeneration || 0) + 1;
  const [task, page] = await Promise.all([invoke('read_latest_desktop_json_knowledge', { workspaceId }, owner), invoke('list_desktop_json_knowledge_tasks', { workspaceId, offset: 0 }, owner)]);
  if (owner.current?.summary?.id !== workspaceId || owner.jsonKnowledgeReadGeneration !== generation) return;
  owner.jsonKnowledgeTask = task; owner.jsonKnowledgeTasks = page.tasks; owner.jsonKnowledgeHasMore = page.hasMore;
  if (state === owner && owner.pane === 'knowledge') render();
}
function jsonKnowledgeTaskCard(task) {
  if (!task) return '';
  const labels = { SELECTED: '正在复制', AWAITING_CONFIRMATION: '等待逐项确认', PARTIALLY_COMPLETED: '部分已处理', COMPLETED: '已处理', FAILED: '导入失败', CANCELLED: '已取消' };
  return `<section class="memory-rail json-knowledge-task" data-task-id="${escape(task.id)}"><div class="section-head"><h2>JSON 导入 · ${escape(labels[task.status] || '待核对')}</h2></div><label>导入任务<select data-action="select-json-knowledge-task">${(state.jsonKnowledgeTasks || []).map(item => `<option value="${escape(item.id)}" ${item.id === task.id ? 'selected' : ''}>${escape(item.displayName)} · ${escape(labels[item.status] || item.status)}</option>`).join('')}</select></label>${state.jsonKnowledgeHasMore ? '<button data-action="more-json-knowledge-tasks">读取更多导入任务</button>' : ''}<p>${escape(task.displayName)} · 文件只保存在本机，确认前不会写入知识。</p>${task.safeErrorCode ? '<p class="security-note">文件未通过格式或安全校验。原任务已保留，可取消或从私有副本重新检查。</p>' : ''}<ul>${task.items.map(item => `<li><span>${escape(item.candidate.content.title)} · ${escape({ PENDING_CONFIRMATION: '待确认', CONFIRMED: '已保存', SKIPPED: '已跳过' }[item.status] || item.status)}</span>${item.status === 'PENDING_CONFIRMATION' && !['CANCELLED', 'FAILED'].includes(task.status) ? `<button data-action="review-json-knowledge" data-ordinal="${item.ordinal}">审阅并保存</button><button data-action="skip-json-knowledge" data-ordinal="${item.ordinal}">跳过</button>` : ''}</li>`).join('')}</ul><div class="item-actions">${task.status === 'FAILED' ? '<button data-action="retry-json-knowledge">重新检查私有副本</button>' : ''}${!['CANCELLED', 'COMPLETED'].includes(task.status) ? '<button data-action="cancel-json-knowledge">取消此导入</button>' : ''}</div></section>`;
}
async function handleJsonKnowledgeAction(event) {
  const target = event.target.closest?.('[data-action]');
  const action = target?.dataset.action;
  if (action === 'select-json-knowledge-task' && event.type === 'click') return;
  if (!['select-json-knowledge', 'review-json-knowledge', 'confirm-json-knowledge', 'skip-json-knowledge', 'cancel-json-knowledge', 'retry-json-knowledge', 'select-json-knowledge-task', 'more-json-knowledge-tasks'].includes(action)) return;
  event.preventDefault(); event.stopImmediatePropagation();
  const owner = state, workspaceId = owner.current?.summary?.id, dialog = owner.dialog;
  const current = () => state === owner && owner.current?.summary?.id === workspaceId;
  if (!native || !workspaceId) { owner.error = '请在本机应用中选择当前工作区后导入。'; render(); return; }
  let startingTaskId = null;
  try {
    if (action === 'select-json-knowledge-task') {
      const generation = owner.jsonKnowledgeReadGeneration = (owner.jsonKnowledgeReadGeneration || 0) + 1;
      const task = await invoke('read_desktop_json_knowledge', { workspaceId, taskId: target.value }, owner);
      if (!current() || owner.jsonKnowledgeReadGeneration !== generation) return; owner.jsonKnowledgeTask = task;
    } else if (action === 'more-json-knowledge-tasks') {
      const page = await invoke('list_desktop_json_knowledge_tasks', { workspaceId, offset: owner.jsonKnowledgeTasks.length }, owner);
      if (!current()) return; owner.jsonKnowledgeTasks.push(...page.tasks); owner.jsonKnowledgeHasMore = page.hasMore;
    } else if (action === 'select-json-knowledge') {
      const selectedPath = await dialogInvoke('open', { multiple: false, directory: false, filters: [{ name: 'JSON 知识', extensions: ['json'] }] });
      if (!selectedPath || !current() || owner.dialog !== dialog) return;
      const task = await invoke('stage_desktop_json_knowledge', { workspaceId, selectedPath }, owner);
      if (!current() || owner.dialog !== dialog) return;
      owner.jsonKnowledgeTask = task; owner.dialog = null; owner.pane = 'knowledge';
      await loadJsonKnowledgeTask(owner); if (!current()) return;
      owner.status = task.status === 'FAILED' ? 'JSON 未通过校验；失败任务已保留。' : 'JSON 已在本机解析；请逐项审阅后保存。';
    } else {
      const task = owner.jsonKnowledgeTask; startingTaskId = task?.id || null;
      if (!task || task.workspaceId !== workspaceId) throw new Error('当前工作区没有可处理的 JSON 任务。');
      if (target.closest?.('[data-task-id]')?.dataset.taskId !== task.id) throw new Error('导入任务已变化，请重新读取。');
      const ordinal = Number(target.dataset.ordinal ?? dialog?.ordinal);
      if (action === 'review-json-knowledge') {
        const item = task.items.find(item => item.ordinal === ordinal && item.status === 'PENDING_CONFIRMATION');
        if (!item) throw new Error('待确认条目已变化，请重新读取。');
        owner.dialog = { kind: 'json-knowledge-review', taskId: task.id, ordinal, item: item.candidate.content, source: item.candidate.source, draft: {} };
      } else {
        let updated;
        if (action === 'confirm-json-knowledge') {
          if (dialog?.kind !== 'json-knowledge-review' || dialog.taskId !== task.id) throw new Error('请先审阅当前条目。');
          const candidate = { title: document.querySelector('#json-knowledge-title')?.value || '', body: document.querySelector('#json-knowledge-body')?.value || '', tags: (document.querySelector('#json-knowledge-tags')?.value || '').split(',').map(v => v.trim()).filter(Boolean) };
          const projectId = document.querySelector('#json-knowledge-project')?.value || null;
          updated = await invoke('confirm_desktop_json_knowledge_item', { args: { workspaceId, taskId: task.id, ordinal, candidate, projectId } }, owner);
        } else {
          const command = { 'skip-json-knowledge': 'skip_desktop_json_knowledge_item', 'cancel-json-knowledge': 'cancel_desktop_json_knowledge', 'retry-json-knowledge': 'retry_desktop_json_knowledge' }[action];
          updated = await invoke(command, { workspaceId, taskId: task.id, ...(action === 'skip-json-knowledge' ? { ordinal } : {}) }, owner);
        }
        if (!current() || owner.jsonKnowledgeTask?.id !== task.id) return;
        owner.jsonKnowledgeTask = updated;
        if (owner.dialog === dialog && dialog?.kind === 'json-knowledge-review') owner.dialog = null;
        if (action === 'confirm-json-knowledge') { owner.status = '已保存为本机知识，原文件与来源回执保留。'; await refresh(); if (!current()) return; }
      }
    }
    owner.error = ''; if (current()) render();
  } catch (error) { if (!current() || (startingTaskId && owner.jsonKnowledgeTask?.id !== startingTaskId) || error?.code === 'STALE_DATA_AREA') return; owner.error = 'JSON 处理未完成。请检查格式、正文长度、标签或敏感内容后再试；没有自动保存或联网。'; render(); }
}
app.addEventListener('click', event => { void handleJsonKnowledgeAction(event); }, true);
app.addEventListener('change', event => { if (event.target.matches?.('[data-action=select-json-knowledge-task]')) void handleJsonKnowledgeAction(event); }, true);

async function handleJsonKnowledgeExportAction(event) {
  const target = event.target.closest?.('[data-action]'), action = target?.dataset.action;
  if (!['open-json-knowledge-export', 'toggle-json-knowledge-export', 'save-json-knowledge-export'].includes(action)) return;
  if (action === 'toggle-json-knowledge-export' && event.type === 'click') return;
  event.preventDefault(); event.stopImmediatePropagation();
  const owner = state, workspaceId = owner.current?.summary?.id, dialog = owner.dialog;
  let generation;
  const current = () => state === owner && owner.current?.summary?.id === workspaceId && owner.dialog === dialog && (generation === undefined || dialog.exportGeneration === generation);
  try {
    if (!native || !workspaceId) throw new Error('本机工作区尚未就绪。');
    if (action === 'open-json-knowledge-export') {
      const eligible = (owner.current?.exchange?.knowledge || []).filter(item => item.status === 'ACTIVE').map(({ id, title }) => ({ id, title }));
      const id = target.dataset.id;
      if (dialog?.kind !== 'knowledge' || dialog.item?.id !== id || !eligible.some(item => item.id === id)) throw new Error('条目已变化。');
      owner.dialog = { kind: 'json-knowledge-export', workspaceId, eligible, selectedIds: [id], exportGeneration: 0, busy: false };
      render(); return;
    }
    if (dialog?.kind !== 'json-knowledge-export' || dialog.workspaceId !== workspaceId) throw new Error('请重新选择导出条目。');
    if (action === 'toggle-json-knowledge-export') {
      if (dialog.busy || !dialog.eligible.some(item => item.id === target.dataset.id)) return;
      const selected = new Set(dialog.selectedIds);
      if (target.checked) selected.add(target.dataset.id); else selected.delete(target.dataset.id);
      dialog.selectedIds = [...selected]; dialog.exportGeneration++; render(); return;
    }
    if (dialog.busy) return;
    const knowledgeIds = [...dialog.selectedIds];
    if (!knowledgeIds.length || knowledgeIds.length > 100 || new Set(knowledgeIds).size !== knowledgeIds.length || knowledgeIds.some(id => !dialog.eligible.some(item => item.id === id))) throw new Error('选择无效。');
    generation = ++dialog.exportGeneration; dialog.busy = true; render();
    const selectedPath = await dialogInvoke('save', { defaultPath: `knowledge-${new Date().toISOString().slice(0, 10)}.json`, filters: [{ name: 'Knowledge JSON', extensions: ['json'] }] });
    if (!current() || !selectedPath) return;
    const receipt = await invoke('export_desktop_json_knowledge', { workspaceId, knowledgeIds, selectedPath }, owner);
    if (!current()) return;
    if (receipt?.manifest?.itemCount !== knowledgeIds.length || receipt?.manifest?.format !== 'nfai.knowledge.json' || receipt?.manifest?.schemaVersion !== '1' || !/^[a-f0-9]{64}$/.test(receipt?.sha256 || '') || !(receipt.byteCount > 0)) throw new Error('导出回执无效。');
    owner.jsonKnowledgeExportReceipt = receipt; owner.dialog = null; owner.error = ''; owner.status = `已导出 ${knowledgeIds.length} 条已保存知识，文件回读校验通过。`; render();
  } catch (error) {
    if (!current() || error?.code === 'STALE_DATA_AREA') return;
    owner.error = '导出未完成。请确认选择的是已保存且有效的知识，并选择应用私有目录之外的 JSON 目标；超出格式上限或来源校验失败时不会报告成功。'; render();
  } finally {
    if (owner.dialog === dialog && generation !== undefined && dialog.exportGeneration === generation) { dialog.busy = false; if (current()) render(); }
  }
}
app.addEventListener('click', event => { void handleJsonKnowledgeExportAction(event); }, true);
app.addEventListener('change', event => { if (event.target.matches?.('[data-action=toggle-json-knowledge-export]')) void handleJsonKnowledgeExportAction(event); }, true);


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
const localSettingsWrites = createLocalWriteBarrier();
const pendingAppSettingsPatches = [];
function persistNativeAppSettings(appearance, product) {
  const request = { appearance: { ...appearance }, product: { ...product } };
  pendingAppSettingsPatches.push(request);
  const save = appSettingsSaveQueue.catch(() => {}).then(() => persistAppSettingsPatch(request)).finally(() => {
    const index = pendingAppSettingsPatches.indexOf(request);
    if (index >= 0) pendingAppSettingsPatches.splice(index, 1);
  });
  appSettingsSaveQueue = save;
  return localSettingsWrites.track(save, 'app-settings');
}
function applyAppSettingsProjection(projection, request) {
  // A completed earlier write cannot repaint over a later local selection.
  const later = pendingAppSettingsPatches.slice(pendingAppSettingsPatches.indexOf(request) + 1);
  state.appearance = normalizeAppearance(Object.assign({}, projection.appearance, ...later.map(item => item.appearance)));
  state.productSettings = normalizeProductSettings(Object.assign({}, projection.product, ...later.map(item => item.product)));
  state.appSettingsRevision = Number(projection.revision || 0);
  state.settingsCapabilities = projection.capabilities || state.settingsCapabilities;
}
async function persistAppSettingsPatch(request) {
  const { appearance, product } = request;
  if (!native) {
    state.appearance = parityPreferences.writeAppearance({ ...state.appearance, ...appearance });
    state.productSettings = parityPreferences.writeProductSettings({ ...state.productSettings, ...product });
    return;
  }
  let projection;
  try {
    const current = await invoke('read_desktop_app_settings');
    projection = await invoke('save_desktop_app_settings', { args: { appearance: normalizeAppearance({ ...current.appearance, ...appearance }), product: normalizeProductSettings({ ...current.product, ...product }), expectedRevision: current.revision } });
  } catch (error) {
    // Read the committed revision after an ambiguous save failure. Keep newer
    // local choices visible while their queued writes are still outstanding.
    try {
      applyAppSettingsProjection(await invoke('read_desktop_app_settings'), request);
      if (!state.personalizationDirty) state.personalizationDraft = { ...state.productSettings };
    } catch (readError) {
      throw Object.assign(new Error(`设置保存结果无法回读确认：${String(readError)}`), { code: 'APP_SETTINGS_COMMIT_UNKNOWN', cause: error });
    }
    throw error;
  }
  applyAppSettingsProjection(projection, request);
  parityPreferences.clearNativeAppSettingsMigrationSource();
}

async function updateAppearance(field, value) {
  const next = normalizeAppearance({ ...state.appearance, [field]: value });
  state.appearance = next;
  applyAppearance();
  render();
  try {
    await persistNativeAppSettings({ [field]: value }, {});
    state.status = '';
    state.error = '';
  } catch (error) {
    applyAppearance();
    state.error = error?.code === 'APP_SETTINGS_COMMIT_UNKNOWN'
      ? `外观偏好保存结果未确认，当前选择保留在界面：${String(error)}`
      : `外观偏好未保存：${String(error)}`;
  }
  render();
}

const favoriteReadGenerations = new WeakMap();
function advanceFavoriteRead(owner) {
  const generation = (favoriteReadGenerations.get(owner) || 0) + 1;
  favoriteReadGenerations.set(owner, generation);
  return generation;
}
async function toggleFavoriteConversation(conversationId, { renderAfter = true } = {}) {
  const owner = state;
  const workspaceId = owner.current?.summary?.id;
  if (!workspaceId || !conversationId) return;
  advanceFavoriteRead(owner);
  const next = new Set(owner.favoriteConversationIds);
  const wasFavorite = next.delete(conversationId);
  if (!wasFavorite) next.add(conversationId);
  try {
    const ids = native
      ? await invoke('set_desktop_conversation_favorite', { workspaceId, conversationId, favorite: !wasFavorite }, owner)
      : [...parityPreferences.writeFavoriteConversationIds(workspaceId, next)];
    advanceFavoriteRead(owner);
    if (state !== owner || owner.current?.summary?.id !== workspaceId) return;
    owner.favoriteConversationIds = new Set(ids);
    owner.status = wasFavorite ? '会话已取消收藏。' : '会话已收藏；归档或删除时会自动取消。';
    owner.error = '';
  } catch (error) {
    if (state !== owner || owner.current?.summary?.id !== workspaceId || error?.code === 'STALE_DATA_AREA') return;
    owner.error = `会话收藏状态未更新：${String(error)}`;
  }
  owner.contextMenu = null;
  if (renderAfter) render();
}

function removeFavoriteConversation(conversationId) {
  const owner = state;
  if (!owner.favoriteConversationIds.has(conversationId) || !owner.current?.summary?.id) return;
  const next = new Set(owner.favoriteConversationIds);
  next.delete(conversationId);
  const workspaceId = owner.current.summary.id;
  advanceFavoriteRead(owner);
  owner.favoriteConversationIds = next;
  if (native) {
    void invoke('set_desktop_conversation_favorite', { workspaceId, conversationId, favorite: false }, owner).then(ids => {
      advanceFavoriteRead(owner);
      if (state !== owner || owner.current?.summary?.id !== workspaceId) return;
      owner.favoriteConversationIds = new Set(ids);
      render();
    }).catch(error => {
      if (state !== owner || owner.current?.summary?.id !== workspaceId || error?.code === 'STALE_DATA_AREA') return;
      owner.error = `会话取消收藏未完成：${String(error)}`;
      render();
    });
  } else {
    owner.favoriteConversationIds = parityPreferences.writeFavoriteConversationIds(workspaceId, next);
  }
}

applyAppearance();
systemDarkQuery?.addEventListener?.('change', () => { if (state.appearance.mode === 'system') { applyAppearance(); render(); } });
globalThis.__nanfengV2CommittedExchanges = state.v2CommittedExchanges;
let p6kManualLink = { taskId: null, assetOrdinal: null, conversationId: null, messageId: null };
globalThis.__nanfengP6kManualLink = p6kManualLink;
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
  else if (state.pdfPreview) cancelPdfPreviewLoad();
  else if (state.archivePreview) cancelArchivePreviewLoad();
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
  const returningToChat = ['return-workspace-origin', 'show-chat'].includes(action) && state.dataArea === 'WORK';
  const reopeningWork = action === 'show-work' && state.dataArea === 'WORK';
  if (native && startupUiStage !== 'ready' && (workspaceAction || returningToChat)) {
    event.preventDefault();
    event.stopImmediatePropagation();
    if (startupUiStage === 'pending') {
      queuedStartupAreaRoute = {
        area: returningToChat ? 'CHAT' : 'WORK',
        pane: returningToChat
          ? (action === 'return-workspace-origin' ? dataAreaStates.get('CHAT')?.pane || 'chat' : 'chat')
          : ({ 'show-projects': 'projects', 'show-knowledge': 'knowledge', 'show-memory': 'memory' }[action] || 'work'),
      };
      state.status = '正在完成本地启动；完成后进入所选数据区。';
    } else {
      state.error = '本地启动读取未完成；请重新打开应用后再试。';
    }
    render();
    return;
  }
  if (native && startupUiStage === 'pending' && ['show-chat', 'new-chat'].includes(action)) queuedStartupAreaRoute = null;
  if (areaSwitchPending) { event.preventDefault(); event.stopImmediatePropagation(); return; }
  if (state.runtimeInfo?.dataAreasReady && ((workspaceAction && state.dataArea === 'CHAT') || returningToChat || reopeningWork)) {
    event.preventDefault();
    event.stopImmediatePropagation();
    const nextArea = returningToChat ? 'CHAT' : 'WORK';
    const nextPane = returningToChat
      ? (action === 'return-workspace-origin' ? dataAreaStates.get('CHAT')?.pane || 'chat' : 'chat')
      : ({ 'show-projects': 'projects', 'show-knowledge': 'knowledge', 'show-memory': 'memory' }[action] || 'work');
    void switchDataArea(nextArea, nextPane).catch(error => {
      if (error?.code === 'STALE_DATA_AREA') return;
      state.error = `切换数据区失败：${String(error)}`;
      render();
    });
    return;
  }
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
  // The gallery already changed on its connected owner. Keep the rendered
  // snapshot aligned so the next shell-only interaction retains that owner.
  if (renderedTranscriptRetention) renderedTranscriptRetention.gallerySelection = assistantGallerySelectionKey();
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
  const owner = state;
  const workspaceId = owner.current?.summary?.id;
  const dialog = owner.dialog;
  const currentRoute = () => state === owner && owner.current?.summary?.id === workspaceId;
  void (async () => {
    try {
      if (action === 'accept-history-knowledge') {
        const tags = (document.querySelector('#history-candidate-tags')?.value || '').split(',').map(value => value.trim()).filter(Boolean);
        const receipt = await invoke('accept_desktop_history_knowledge', {
          candidateId,
          title: document.querySelector('#history-candidate-title')?.value || '',
          body: document.querySelector('#history-candidate-body')?.value || '',
          tags,
        }, owner);
        if (!currentRoute()) return;
        owner.historyKnowledge = receipt;
        if (owner.dialog === dialog) owner.dialog = null;
        owner.status = '资料候选已按当前编辑内容保存为本地 Knowledge。';
        await refresh();
      } else if (action === 'reject-history-knowledge') {
        const receipt = await invoke('reject_desktop_history_knowledge', { candidateId }, owner);
        if (!currentRoute()) return;
        owner.historyKnowledge = receipt;
        if (owner.dialog === dialog) owner.dialog = null;
        owner.status = '资料候选已驳回；不会进入长期 Memory 或 Knowledge。';
      } else if (action === 'retry-history-knowledge') {
        owner.status = '正在按显式操作重试；UNKNOWN 不会由后台自动重发。';
        render();
        const receipt = await invoke('retry_desktop_history_knowledge', { candidateId }, owner);
        if (!currentRoute()) return;
        owner.historyKnowledge = receipt;
        owner.status = '历史资料库显式重试已结束，请核对当前状态。';
      } else {
        const receipt = await invoke('delete_desktop_history_knowledge', { candidateId }, owner);
        if (!currentRoute()) return;
        owner.historyKnowledge = receipt;
        if (owner.dialog === dialog) owner.dialog = null;
        owner.status = '历史资料库候选记录已删除；已采纳的 Knowledge 不会被连带删除。';
      }
      if (!currentRoute()) return;
      owner.error = '';
    } catch (error) {
      if (!currentRoute() || error?.code === 'STALE_DATA_AREA') return;
      owner.error = `历史资料库操作未完成：${String(error)}`;
    }
    if (currentRoute()) render();
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
  const owner = state;
  event.preventDefault();
  event.stopImmediatePropagation();
  const saveAction = ['confirm-reminder-draft', 'confirm-edit-reminder-plan'].includes(action);
  const saveEditor = saveAction ? owner.reminderEditor : null;
  const editorKey = editor => editor?.item ? `${editor.item.planId ? 'edit' : 'create'}:${editor.item.planId || editor.item.draftId}` : null;
  const saveKey = editorKey(saveEditor);
  let savePending = null;
  if (saveEditor?.item) {
    if (owner.reminderEditorSavePending?.key === saveKey) return;
    savePending = { key: saveKey };
    owner.reminderEditorSavePending = savePending;
    saveEditor.submitting = true;
    saveEditor.pendingSaveBinding = savePending;
    target.disabled = true;
  }
  const adoptSavedReminderForContinuedDraft = projection => {
    const current = owner.reminderEditor;
    if (!savePending || current === saveEditor || current?.pendingSaveBinding !== savePending
        || owner.reminderEditorSavePending !== savePending) return;
    const matches = (projection?.plans || []).filter(plan => saveEditor.item.planId
      ? plan.planId === saveEditor.item.planId : plan.sourceDraftId === saveEditor.item.draftId);
    if (matches.length !== 1 || !matches[0].planId || !Number.isSafeInteger(matches[0].updatedAtMs)) return;
    const fields = Object.fromEntries(['title', 'instruction', 'scheduleKind', 'anchorLocal', 'timezoneId', 'missedPolicy']
      .filter(key => Object.hasOwn(current.item, key)).map(key => [key, current.item[key]]));
    current.item = { ...matches[0], ...fields };
    current.mode = 'edit';
    current.discardOnCancel = false;
    current.pendingSaveBinding = null;
    savePending.key = editorKey(current);
  };
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
        if (state !== owner || error?.code === 'STALE_DATA_AREA') return;
        state.error = `手工提醒草案未取消：${String(error)}`;
      }
      if (state === owner) render();
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
        const granted = await requestReminderNotificationPermission(owner);
        if (state !== owner) return;
        state.status = granted ? '系统通知权限已授予；后续成功监控可发送不含结果正文的通知。'
          : owner.reminderNotificationPermission === 'unavailable'
            ? '暂时无法确认系统通知权限；监控结果仍会保存在本机。'
            : '系统未授予通知权限；监控结果仍只保存在本机。';
      } else if (action === 'generate-reminder-draft') {
        if (!native) throw new Error('Web 只读交互样本不调用模型');
        const workspaceId = state.current?.summary?.id;
        const conversationId = state.selectedConversationId;
        if (!workspaceId || !conversationId) throw new Error('当前对话不可用');
        state.status = '正在把明确的提醒意图整理成可编辑草案；尚未创建计划。';
        render();
        try {
          state.reminders = await invoke('generate_desktop_reminder_draft', { args: { workspaceId, conversationId, userMessageId: target.dataset.userMessageId, assistantMessageId: target.dataset.assistantMessageId, timezoneId: Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Shanghai' } });
        } catch (error) { if (state !== owner || error?.code === 'STALE_DATA_AREA') return; await loadDesktopReminders(owner); throw error; }
        if (state !== owner) return;
        state.pane = 'reminders';
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
        catch (error) { if (state !== owner || error?.code === 'STALE_DATA_AREA') return; await loadDesktopReminders(owner); throw error; }
        const draft = state.reminders.drafts.find(item => item.draftId === target.dataset.id);
        if (draft?.status === 'PENDING_REVIEW') state.reminderEditor = { kind: 'reminder-editor', mode: 'create', item: draft };
      } else if (action === 'confirm-reminder-draft') {
        const item = saveEditor?.item;
        if (!item) throw new Error('提醒草案已变化');
        const values = reminderFormValues();
        if (!native) {
          const plan = { ...item, ...values, planId: `c09-preview-${Date.now()}`, status: 'ACTIVE', nextRunAtMs: Date.parse(values.anchorLocal), latestResult: null, lastSafeErrorCode: null, updatedAtMs: Date.now() };
          state.reminders = { ...state.reminders, plans: [...state.reminders.plans, plan], drafts: state.reminders.drafts.filter(draft => draft.draftId !== item.draftId) };
          state.status = 'Web 只读交互样本已演示返回列表；未写入 Desktop SQLite，未启动 scheduler。';
        } else {
          state.reminders = await invoke('confirm_desktop_reminder_draft', { args: { draftId: item.draftId, ...values } });
          state.status = '提醒计划已确认并持久化。';
        }
        adoptSavedReminderForContinuedDraft(state.reminders);
        if (state.reminderEditor === saveEditor) state.reminderEditor = null;
      } else if (action === 'reject-reminder-draft') {
        if (!native) state.reminders = { ...state.reminders, drafts: state.reminders.drafts.filter(item => item.draftId !== target.dataset.id) };
        else state.reminders = await invoke('reject_desktop_reminder_draft', { draftId: target.dataset.id });
        state.reminderEditor = null;
        state.status = '提醒草案已拒绝；未创建计划。';
      } else if (action === 'edit-reminder-plan') {
        const plan = state.reminders.plans.find(item => item.planId === target.dataset.id);
        if (plan) state.reminderEditor = { kind: 'reminder-editor', mode: 'edit', item: plan };
      } else if (action === 'confirm-edit-reminder-plan') {
        const item = saveEditor?.item;
        if (!item) throw new Error('提醒计划已变化');
        const values = reminderFormValues();
        if (!native) updateC09PreviewPlan(item.planId, { ...values, updatedAtMs: Date.now() });
        else state.reminders = await invoke('update_desktop_reminder_plan', { args: { planId: item.planId, expectedUpdatedAtMs: item.updatedAtMs, ...values } });
        adoptSavedReminderForContinuedDraft(state.reminders);
        if (state.reminderEditor === saveEditor) state.reminderEditor = null;
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
      if (state !== owner) return;
      if (native) {
        await loadDesktopBackgroundRuntime(owner);
        if (state !== owner) return;
        if (['confirm-reminder-draft', 'confirm-edit-reminder-plan', 'retry-reminder-plan'].includes(action)
            || (action === 'set-reminder-paused' && target.dataset.paused !== 'true')) {
          if (state.backgroundRuntime.desiredEnabled) {
            state.status += state.backgroundRuntime.installed
              ? ' 系统后台任务已注册。'
              : ' 系统后台任务未注册；关闭应用后不会按计划执行。';
          }
        }
      }
      state.error = '';
    } catch (error) {
      if (state !== owner || error?.code === 'STALE_DATA_AREA') return;
      state.error = `提醒操作未完成：${String(error)}`;
    } finally {
      if (saveEditor) saveEditor.submitting = false;
      if (savePending && owner.reminderEditorSavePending === savePending) {
        owner.reminderEditorSavePending = null;
        if (owner.reminderEditor && editorKey(owner.reminderEditor) === savePending.key) owner.reminderEditor.submitting = false;
      }
      if (state === owner) render();
    }
  })();
}, true);
app.addEventListener('click', event => {
  const action = event.target.closest('[data-action]')?.dataset.action;
  if (!state.temporaryConversation || !['new-chat', 'select-chat', 'show-work', 'show-settings'].includes(action)) return;
  event.preventDefault(); event.stopImmediatePropagation();
  state.status = '临时聊天与普通导航隔离；点击右上角“临时聊天”返回普通聊天。';
  render();
}, true);
// Leaving a cloud row invalidates its pending workspace read and optional
// preference projection, so a late reply cannot undo the user's navigation.
app.addEventListener('click', event => {
  const target = event.target.closest?.('[data-action]');
  const action = target?.dataset.action;
  if (action === 'select-chat' && target.dataset.workspaceId) return;
  if (!['new-chat', 'select-chat', 'select-workspace', 'select-chat-source', 'show-chat', 'show-work', 'show-settings'].includes(action)) return;
  openCloudConversationRoute.cancel();
  state.cloudConversationOpening = null;
}, true);
let conversationSelectionGeneration = 0;
async function loadConversationSelectionProjection(owner, workspaceId, conversationId, generation) {
  if (!native || !workspaceId || !conversationId) return;
  const selection = await invoke('read_desktop_p6g_selection', { workspaceId, conversationId }, owner);
  if (state !== owner || generation !== conversationSelectionGeneration
      || state.current?.summary?.id !== workspaceId || state.selectedConversationId !== conversationId) return;
  state.p6gSelection = selection;
  state.conversationPreferences = selection?.conversationOverride
    || { revision: 0, toneOverride: null, webSearchOverride: null };
  render();
}
app.addEventListener('click', event => {
  const target = event.target.closest('[data-action="select-chat"]');
  if (!target || target.dataset.workspaceId || state.pane !== 'work') return;
  // Work is a Conversation scope, not a route to the former work-home dashboard.
  event.stopImmediatePropagation();
  const owner = state;
  const workspaceId = owner.current?.summary?.id;
  const conversationId = target.dataset.id;
  const generation = ++conversationSelectionGeneration;
  state.selectedConversationId = target.dataset.id;
  state.p6gSelection = null;
  state.conversationPreferences = { revision: 0, toneOverride: null, webSearchOverride: null };
  state.conversationFindOpen = false;
  state.conversationFindQuery = '';
  recomputeConversationFind({ resetIndex: true });
  state.profileOpen = false;
  state.error = '';
  resetComposerPresentation();
  void Promise.all([
    markConversationOpened(conversationId),
    loadOrdinaryComposerDraft(),
    loadConversationSelectionProjection(owner, workspaceId, conversationId, generation),
  ]).catch(error => {
    if (state !== owner || generation !== conversationSelectionGeneration) return;
    state.error = `WORK 会话未完全读取：${String(error)}`;
  }).finally(render);
}, true);
const invoke = (command, args = {}, owner = state) => invokeForDataArea(tauriBridge, command, args, owner, () => state);
async function switchDataArea(nextArea, nextPane) {
  if (!native || !state.runtimeInfo?.dataAreasReady || areaSwitchPending) return;
  if ([...dataAreaStates.values()].some(area => area.localBackup?.working)) {
    state.error = '本机备份或恢复正在进行，请等待完成后切换数据区。';
    render();
    return;
  }
  if ([...dataAreaStates.values()].some(area => area.localBackup?.restartRequired)) {
    state.dialog = { kind: 'local-backup-restart-required' };
    render();
    return;
  }
  if (state.dataArea === nextArea) {
    if (nextArea === 'WORK' && nextPane === 'work') {
      await flushOrdinaryComposerDraft();
      state.selectedWorkProjectId = null;
      selectWorkspaceDefaultConversation(state.current);
      if (state.current && state.selectedConversationId) {
        state.p6gSelection = await invoke('read_desktop_p6g_selection', { workspaceId: state.current.summary.id, conversationId: state.selectedConversationId });
        state.conversationPreferences = state.p6gSelection.conversationOverride;
      } else {
        state.p6gSelection = null;
        state.conversationPreferences = { revision: 0, toneOverride: null, webSearchOverride: null };
      }
      await loadOrdinaryComposerDraft();
    }
    state.pane = nextPane;
    render();
    return;
  }
  areaSwitchPending = true;
  const previous = state;
  try {
    await flushOrdinaryComposerDraft();
    // Drain the temporary timer and local queue while CHAT still owns invoke.
    // A failed save keeps this route open instead of sending its ID to WORK.
    if (previous.temporaryConversation) await flushTemporaryDraft();
    await localSettingsWrites.flush();
    stopComposerCamera();
    // Search requests and cached pages belong to the area being left. A quick
    // CHAT -> WORK -> CHAT round trip must not revive an older CHAT response.
    supersedeFullSearch();
    fullSearchResultCache.clear();
    fullSearchCacheScope = '';
    ordinaryChatRuntimeRefreshPending = null;
    composerDraftRouteGeneration += 1;
    cloudOpenAreaGeneration += 1;
    previous.cloudConversationOpening = null;
    state = dataAreaStates.get(nextArea) || initializeAreaState(createAreaState(nextArea));
    dataAreaStates.set(nextArea, state);
    state.selectedWorkProjectId ??= null;
    state.assistantMessageMenu ??= null;
    state.cloudConversationOpening ??= null;
    globalThis.__nanfengDesktopWorkState = state;
    if (!state.runtimeInfo?.dataAreasReady) state.runtimeInfo = previous.runtimeInfo;
    state.pane = nextPane;
    state.profileOpen = false;
    state.sidebarOpen = true;
    app.innerHTML = '<main class="desktop-area-switch" role="status" aria-live="polite">正在切换数据区…</main>';
    await loadDesktopLocalBackupStatus();
    if (state.localBackup.restartRequired) {
      render();
      return;
    }
    if (nextArea === 'WORK' && !state.current) {
      state.current = await invoke('ensure_desktop_local_workspace');
      state.workspaces = [state.current.summary];
    }
    await loadDesktopAppSettings({ preserveUnsavedDraft: true });
    await loadModelServiceSettings({ preserveUnsavedDraft: true });
    await loadDesktopHistoryKnowledge();
    await loadDesktopReminders(state);
    await refresh();
    if (nextArea === 'WORK' && nextPane === 'work') {
      state.selectedWorkProjectId = null;
      selectWorkspaceDefaultConversation(state.current);
      if (state.current && state.selectedConversationId) {
        state.p6gSelection = await invoke('read_desktop_p6g_selection', { workspaceId: state.current.summary.id, conversationId: state.selectedConversationId });
        state.conversationPreferences = state.p6gSelection.conversationOverride;
      } else {
        state.p6gSelection = null;
        state.conversationPreferences = { revision: 0, toneOverride: null, webSearchOverride: null };
      }
    }
    await loadDesktopAccountSync();
    if (!state.cloudConversations.length) await restoreCachedCloudConversationList();
    if (!state.current && !state.runtimeInfo?.automaticWorkSuppressed) {
      state.current = await invoke('ensure_desktop_local_workspace');
      state.workspaces = [state.current.summary];
    }
    await loadOrdinaryComposerDraft();
    render();
    await drainReminderNotificationActions().catch(() => {});
  } catch (error) {
    state = previous;
    globalThis.__nanfengDesktopWorkState = state;
    render();
    throw error;
  } finally {
    areaSwitchPending = false;
    render();
  }
}
const dialogInvoke = (command, options) => invoke(`plugin:dialog|${command}`, { options });
let conversationReadStateRequestId = 0;
function applyConversationReadState(projection) {
  state.conversationReadState = projection || { workspaceId: null, conversations: [] };
  const rows = projection?.conversations || [];
  state.unreadConversationIds = new Set(rows.filter(item => item.unread).map(item => item.conversationId));
  state.manualUnreadAtMs = new Map(rows.filter(item => item.manualUnreadAtMs != null).map(item => [item.conversationId, Number(item.manualUnreadAtMs)]));
}
async function loadConversationReadState({ observeSelected = true, isCurrent = () => true } = {}) {
  const owner = state;
  const workspaceId = state.current?.summary?.id;
  const requestId = ++conversationReadStateRequestId;
  if (!workspaceId || !native) {
    applyConversationReadState({ workspaceId: workspaceId || null, conversations: [] });
    return state.conversationReadState;
  }
  const projection = await invoke('read_desktop_conversation_read_state', {
    workspaceId,
    observedConversationId: observeSelected ? state.selectedConversationId : null,
  });
  if (owner === state && state.current?.summary?.id === workspaceId && requestId === conversationReadStateRequestId && isCurrent()) applyConversationReadState(projection);
  return projection;
}
async function markConversationOpened(conversationId, isCurrent = () => true) {
  const owner = state;
  const workspaceId = state.current?.summary?.id;
  if (!workspaceId || !conversationId || !native) return;
  const requestId = ++conversationReadStateRequestId;
  const projection = await invoke('mark_desktop_conversation_opened', { workspaceId, conversationId });
  if (owner === state && state.current?.summary?.id === workspaceId && requestId === conversationReadStateRequestId && isCurrent()) applyConversationReadState(projection);
}
async function markConversationUnread(conversationId) {
  const owner = state;
  const workspaceId = state.current?.summary?.id;
  if (!workspaceId || !conversationId || !native) return;
  const requestId = ++conversationReadStateRequestId;
  const projection = await invoke('mark_desktop_conversation_unread', { workspaceId, conversationId });
  if (owner === state && state.current?.summary?.id === workspaceId && requestId === conversationReadStateRequestId) applyConversationReadState(projection);
}
const selectedModelService = (owner = state) => owner.modelServiceSettings.find(item => item.providerId === owner.modelProviderId) || owner.modelServiceSettings[0] || null;
const modelSettingsController = createModelSettingsController({
  native, currentOwner: () => state, render: () => render(),
  invoke: (owner, command, args) => invokeForAuthorizedDataAreaOperation(tauriBridge, command, args, owner),
  trackWrite: (operation, recoveryOwner) => localSettingsWrites.track(operation, recoveryOwner),
  updateStatus: owner => { owner.status = modelServiceConfigurationStatus(owner.modelServiceSettings); },
});
installModelSettingsKeyboardNavigation(document);

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
  const feedback = state.modelProviderFeedback?.[service?.providerId];
  state.modelSettingsNotice = feedback?.notice || '';
  state.modelSettingsError = feedback?.error || '';
  state.modelCredentialRevealRequest = null;
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
function replaceModelService(next, owner = state) {
  owner.modelServiceSettings = owner.modelServiceSettings.map(item => item.providerId === next.providerId
    && Number(next.revision || 0) >= Number(item.revision || 0) ? next : item);
}
async function loadModelServiceSettings({ preserveUnsavedDraft = false } = {}) {
  if (!native) return;
  const owner = state;
  const keyDraft = owner.modelCredentialDraft;
  const keyEdited = owner.modelCredentialEdited;
  const updated = await modelSettingsController.read(owner, { preserveUnsavedDraft });
  if (!updated || state !== owner) return;
  if (!preserveUnsavedDraft && owner.modelCredentialDraft === keyDraft && owner.modelCredentialEdited === keyEdited) clearOwnedModelCredentialDraft(owner);
}
async function setModelServiceEnabled() {
  const owner = state;
  const service = selectedModelService(owner);
  if (!service) return;
  return modelSettingsController.setEnabled(owner, !Boolean(owner.modelServiceDraft?.enabled ?? service.enabled));
}
async function setModelServicePreset(presetId) {
  return modelSettingsController.setPreset(state, presetId);
}

async function loadDesktopAppSettings({ preserveUnsavedDraft = false } = {}) {
  if (!native) return;
  const legacyAppearance = normalizeAppearance(state.appearance);
  const legacyProduct = normalizeProductSettings(state.productSettings);
  let projection = await invoke('read_desktop_app_settings');
  if (Number(projection.revision || 0) === 0 && !state.runtimeInfo?.automaticWorkSuppressed) {
    projection = await invoke('save_desktop_app_settings', { args: { appearance: legacyAppearance, product: legacyProduct, expectedRevision: 0 } });
  }
  state.appearance = normalizeAppearance(projection.appearance);
  state.productSettings = normalizeProductSettings(projection.product);
  if (!preserveUnsavedDraft || !state.personalizationDirty) {
    state.personalizationDraft = { ...state.productSettings };
    state.personalizationDirty = false;
  }
  state.appSettingsRevision = Number(projection.revision || 0);
  state.settingsCapabilities = projection.capabilities || state.settingsCapabilities;
  if (!state.runtimeInfo?.automaticWorkSuppressed) parityPreferences.clearNativeAppSettingsMigrationSource();
  applyAppearance();
}
async function loadDesktopLocalBackupStatus() {
  if (!native) return;
  const backupStatus = await invoke('read_desktop_local_backup_status');
  applyDesktopLocalBackupStatus(state, backupStatus);
}
function applyDesktopLocalBackupStatus(owner, backupStatus) {
  owner.localBackup = {
    ...owner.localBackup,
    preflight: backupStatus.preflight || null,
    replaceLocal: Boolean(backupStatus.preflight && !(backupStatus.preflight.conflicts || []).length),
    notice: backupStatus.notice || '',
    restartRequired: Boolean(backupStatus.restartRequired),
    interrupted: Boolean(backupStatus.interrupted),
    rollbackPending: Boolean(backupStatus.rollbackPending),
  };
  if (backupStatus.restartRequired) state.dialog = { kind: 'local-backup-restart-required' };
}
async function loadSelectedBackupStatus() {
  if (!native) return;
  const owner = selectedBackupAreaState();
  applyDesktopLocalBackupStatus(owner, await invokeSelectedBackup(owner, 'read_desktop_local_backup_status'));
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
async function loadDesktopAccountSync(owner = state) {
  if (!native) return owner.accountSync;
  const pendingUnknown = owner.accountSync?.pendingUnknown || null;
  // `null` means the user has not requested a cloud read in this settings
  // session.  An empty array is a real, authenticated read that returned no
  // documents; collapsing the two made the page claim "none found" before it
  // had contacted the cloud at all.
  const remoteDocuments = Array.isArray(owner.accountSync?.remoteDocuments)
    ? owner.accountSync.remoteDocuments
    : null;
  const previous = owner.accountSync;
  const generation = (accountSyncReadGenerations.get(owner) || 0) + 1;
  accountSyncReadGenerations.set(owner, generation);
  const latest = await invoke('read_desktop_account_sync', {}, owner);
  if (state !== owner || accountSyncReadGenerations.get(owner) !== generation || owner.accountSync !== previous) return owner.accountSync;
  const sameAccount = previous?.email === latest.email;
  owner.accountSync = { ...latest, pendingUnknown: sameAccount ? pendingUnknown : null, remoteDocuments: sameAccount ? remoteDocuments : null };
  if (sameAccount) owner.accountSync.avatarDataUrl ||= previous?.avatarDataUrl;
  else owner.cloudConversations = [];
  if (owner.accountSync.email && !owner.accountSync.avatarDataUrl && !accountAvatarPending) {
    const email = owner.accountSync.email;
    accountAvatarPending = true;
    invoke('read_desktop_google_avatar', {}, owner).then(avatar => {
      if (avatar && state === owner && owner.accountSync?.email === email) {
        owner.accountSync.avatarDataUrl = avatar;
        if (owner.settingsSection === 'account') render();
      }
    }).catch(() => {}).finally(() => { accountAvatarPending = false; });
  }
  return owner.accountSync;
}

async function cloudRowsFromLocalEntries(entries, owner = state) {
  if (!native || !Array.isArray(entries)) return [];
  const uniqueEntries = dedupeCloudConversationEntries(entries);
  // Restoring a cloud list often returns several rows from one workspace.  The
  // old per-row IPC pattern made every row wait for two SQLite reads.
  const workspaceIds = [...new Set(uniqueEntries.map(item => item?.workspaceId).filter(Boolean))];
  if (!workspaceIds.length) return [];
  const batch = await invoke('read_desktop_cloud_workspace_projections', { workspaceIds }, owner);
  const byWorkspace = new Map(batch.projections.map(item => [item.workspaceId, item]));
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
  const owner = state;
  const email = owner.accountSync?.email;
  const cached = readCloudConversationCache(owner);
  // A list is private to the signed-in Google identity. A prior account's
  // cached IDs are never shown after an account switch or sign-out.
  if (!cached || !email || cached.email !== email) return;
  if (cached.entries.length > cloudConversationEntryLimit) {
    owner.status = '本机云列表缓存超过 10,000 项读取上限，缓存已保留；请重新读取云端列表。';
    return;
  }
  const generation = advanceCloudListRead(owner);
  const current = () => state === owner && currentCloudListRead(owner) === generation
    && owner.accountSync?.email === email;
  let rows;
  try {
    rows = await cloudRowsFromLocalEntries(cached.entries, owner);
  } catch (error) {
    // The area adapter can reject before returning the projection. That old
    // read must not roll back navigation to the newer area's state.
    if (!current()) return;
    throw error;
  }
  if (!current()) return;
  owner.cloudConversations = rows;
  // A local projection is not a remote membership receipt. Keep cached IDs
  // even if a missing or unreadable local workspace cannot be displayed yet.
  const missingCount = cached.entries.length - rows.length;
  if (missingCount > 0) owner.status = `本机云列表有 ${missingCount} 项暂时未能显示，原缓存已保留；请核对本机聊天数据或重新读取云端列表。`;
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
async function loadDesktopReminders(owner = state) {
  if (!native) return owner.reminders;
  owner.reminders = await invoke('read_desktop_reminders', {}, owner);
  return owner.reminders;
}
async function loadDesktopBackgroundRuntime(owner = state) {
  if (!native) return owner.backgroundRuntime;
  owner.backgroundRuntime = await invoke('read_desktop_background_runtime', {}, owner);
  return owner.backgroundRuntime;
}
async function readReminderNotificationPermission(owner = state) {
  if (!native) {
    owner.reminderNotificationPermission = 'unavailable';
    return false;
  }
  try {
    owner.reminderNotificationPermission = await invoke('read_desktop_reminder_notification_permission', {}, owner);
    return ['granted', 'legacy'].includes(owner.reminderNotificationPermission);
  } catch {
    if (state === owner) owner.reminderNotificationPermission = 'unavailable';
    return false;
  }
}
async function requestReminderNotificationPermission(owner = state) {
  if (!native) return false;
  try {
    const permission = await invoke('request_desktop_reminder_notification_permission', {}, owner);
    owner.reminderNotificationPermission = ['granted', 'legacy', 'denied'].includes(permission) ? permission : 'default';
    return ['granted', 'legacy'].includes(permission);
  } catch {
    if (state === owner) owner.reminderNotificationPermission = 'unavailable';
    return false;
  }
}
async function flushReminderNotifications() {
  if (!native) return;
  const pending = await invoke('read_pending_desktop_reminder_notifications');
  if (!pending.length) return;
  const permissionGranted = await readReminderNotificationPermission();
  // An unreadable Windows setting must not silently suppress an authorized reminder.
  // The native owner attempts delivery and records UNKNOWN instead of claiming SENT.
  const windowsPermissionUnknown = /^Win/i.test(navigator.platform || '') && state.reminderNotificationPermission === 'unavailable';
  const allowed = Boolean(state.productSettings.monitorNotifications) && (permissionGranted || windowsPermissionUnknown);
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
function showUnavailableReminderNotificationTarget() {
  state.status = '该通知已处理，或对应的提醒与会话已不可用；没有打开其他对象。';
  render();
}
async function routeToReminderPlan(extra = {}) {
  const candidate = reminderNotificationExtra(extra);
  if (candidate.route !== 'desktop-reminder-plan' || !candidate.planId) return false;
  const owner = state;
  const resolved = await invoke('resolve_desktop_reminder_notification_target', { target: candidate }, owner);
  if (state !== owner) return false;
  if (!resolved) { showUnavailableReminderNotificationTarget(); return false; }
  await loadDesktopReminders(owner);
  if (state !== owner) return false;
  const plan = owner.reminders.plans.find(item => item.planId === resolved.planId);
  if (!plan || plan.workspaceId !== resolved.workspaceId || (plan.conversationId || '') !== resolved.conversationId) {
    showUnavailableReminderNotificationTarget();
    return false;
  }
  const nextWorkspace = owner.current?.summary?.id === resolved.workspaceId
    ? owner.current
    : await invoke('read_desktop_workspace', { workspaceId: resolved.workspaceId }, owner);
  if (state !== owner) return false;
  const routeChanged = owner.current?.summary?.id !== resolved.workspaceId
    || (owner.selectedConversationId || '') !== (resolved.conversationId || '');
  if (routeChanged) {
    await flushOrdinaryComposerDraft();
    if (state !== owner) return false;
  }
  if (!await invoke('activate_desktop_reminder_notification_target', { target: resolved }, owner)) {
    showUnavailableReminderNotificationTarget();
    return false;
  }
  if (state !== owner) return false;
  if (routeChanged) {
    owner.current = nextWorkspace;
    owner.selectedConversationId = resolved.conversationId || null;
    owner.selectedWorkProjectId = null;
    owner.newConversationDraftWorkspaceId = null;
    resetComposerPresentation();
    owner.p6gSelection = null;
    owner.conversationPreferences = { revision: 0, toneOverride: null, webSearchOverride: null };
    owner.favoriteConversationIds = new Set();
    applyConversationReadState({ workspaceId: resolved.workspaceId, conversations: [] });
    owner.history = { canUndo: false, canRedo: false, recycleBin: [], modelMetadata: [] };
    owner.contextMenu = null;
    owner.assistantMessageMenu = null;
  }
  owner.selectedReminderPlanId = resolved.planId;
  owner.pane = 'reminders';
  owner.settingsSection = 'reminders';
  if (routeChanged) {
    await refresh({ observeSelected: false });
    if (state !== owner) return false;
    await loadOrdinaryComposerDraft();
    if (state !== owner) return false;
  }
  render();
  queueMicrotask(() => document.querySelector(`[data-reminder-plan-id="${CSS.escape(resolved.planId)}"]`)?.scrollIntoView({ block: 'center' }));
  return true;
}
async function handleReminderNotificationAction(event) {
  const action = reminderNotificationAction(event);
  if (!action.clickId) return false;
  if (action.dataArea !== state.dataArea) {
    await switchDataArea(action.dataArea, action.dataArea === 'WORK' ? 'work' : 'chat');
    if (state.dataArea !== action.dataArea) return false;
  }
  const target = await invoke('consume_desktop_reminder_notification_action', { clickId: action.clickId });
  if (!target) { showUnavailableReminderNotificationTarget(); return false; }
  return routeToReminderPlan(target);
}
async function drainReminderNotificationActions() {
  const pending = await invoke('drain_desktop_reminder_notification_actions');
  for (const target of pending) await routeToReminderPlan(target);
  state.reminderNotificationBridge = { ...state.reminderNotificationBridge, pendingActionCount: 0 };
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
    const activationArea = await invoke('read_pending_desktop_reminder_activation_area');
    if (activationArea && activationArea !== state.dataArea) {
      await switchDataArea(activationArea, activationArea === 'WORK' ? 'work' : 'chat');
    }
    await drainReminderNotificationActions();
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
  const body = kind === 'success' ? '南枫云同步已完成；可在设置中查看回执。' : '南枫云同步需要处理；请在设置中查看诊断。';
  try { new NotificationOwner('南枫云同步', { body, tag: 'nanfeng-account-sync-v1' }); } catch { /* system notification is optional and never blocks local truth */ }
}

function scheduleUnknownSyncReconciliation(workspaceId, conversationId) {
  const owner = state;
  const email = owner.accountSync?.email;
  if (!native || !workspaceId || !conversationId || !email) return;
  const sourceWorkspaceId = owner.current?.summary?.id;
  const sourcePane = owner.pane;
  const sourceSettingsPage = owner.settingsSection;
  let pendingMarker = owner.accountSync?.pendingUnknown;
  const isCurrent = () => state === owner && owner.current?.summary?.id === sourceWorkspaceId
    && owner.pane === sourcePane && owner.settingsSection === sourceSettingsPage
    && owner.accountSync?.email === email && owner.accountSync?.pendingUnknown === pendingMarker;
  const key = JSON.stringify([owner.dataArea, email, workspaceId, conversationId]);
  if (pendingSyncReconciliations.has(key)) return;
  pendingSyncReconciliations.add(key);
  // The submit response was uncertain, not a failed write.  Reconcile after
  // yielding the click frame so the user sees the honest pending state first.
  window.setTimeout(() => {
    void invokeForAuthorizedDataAreaOperation(tauriBridge, 'reconcile_desktop_conversation_sync',
      { workspaceId, conversationId, expectedAccountEmail: email }, owner)
      .then(receipt => {
        if (!isCurrent()) return;
        if (receipt.status === 'SYNCED_AFTER_RECONCILE' || receipt.status === 'SYNCED_AFTER_REMOTE_RECONCILE') {
          owner.accountSync = { ...owner.accountSync, pendingUnknown: null };
          owner.status = receipt.status === 'SYNCED_AFTER_REMOTE_RECONCILE' ? '已合并云端最新对话并完成同步。' : '同步成功，已回读校验。';
          owner.error = '';
          showTransientToast(owner.status);
          notifyAccountSync('success');
        } else if (receipt.status === 'SAFE_TO_RETRY_EXPLICITLY') {
          owner.accountSync = { ...owner.accountSync, pendingUnknown: { workspaceId, conversationId } };
          owner.status = '云端未确认本次提交；未重复上传。';
          notifyAccountSync('attention');
        } else {
          owner.accountSync = { ...owner.accountSync, pendingUnknown: { workspaceId, conversationId } };
          owner.status = '云端仍在核对，未覆盖任何内容。';
          notifyAccountSync('attention');
        }
        pendingMarker = owner.accountSync.pendingUnknown;
      })
      .catch(() => {
        if (!isCurrent()) return;
        owner.accountSync = { ...owner.accountSync, pendingUnknown: { workspaceId, conversationId } };
        owner.status = '云端提交正在后台核对；不会重复上传。';
        pendingMarker = owner.accountSync.pendingUnknown;
      })
      .finally(() => {
        pendingSyncReconciliations.delete(key);
        if (!isCurrent()) return;
        render();
        void loadDesktopAccountSync(owner).then(() => { if (isCurrent()) render(); }).catch(() => {});
      });
  }, 0);
}
async function loadTranscriptionState(owner = state) {
  if (!native) {
    owner.transcription = c10TranscriptionPreview
      ? createC10TranscriptionPreviewProjection(c10TranscriptionPreview)
      : { settings: { ...TRANSCRIPTION_PREVIEW_STATE.settings }, tasks: [] };
    owner.selectedTranscriptionTaskId = owner.transcription.tasks[0]?.id || null;
    return owner.transcription;
  }
  owner.transcription = await invoke('read_desktop_transcription_state', { workspaceId: null }, owner);
  if (!owner.transcription.tasks.some(task => task.id === owner.selectedTranscriptionTaskId)) {
    owner.selectedTranscriptionTaskId = owner.transcription.tasks[0]?.id || null;
  }
  return owner.transcription;
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
  const owner = state;
  if ((!native && !c10TranscriptionPreview) || state.transcriptionBusyTaskId) return;
  const documentTask = transcriptionTask(taskId)?.modelId === 'glm-ocr';
  if (!documentTask) {
    state.error = '旧版音视频任务仅供查看和导出。';
    render();
    return;
  }
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
    if (retry) await invokeForAuthorizedDataAreaOperation(tauriBridge,
      'retry_desktop_transcription_task', { taskId }, owner);
    const outcome = await invokeForAuthorizedDataAreaOperation(tauriBridge,
      'run_desktop_ocr_task', { taskId }, owner);
    if (state !== owner) return;
    state.status = outcome.state === 'COMPLETED'
      ? '图片/PDF 已转换为 Markdown，来源、结果与费用已保存。'
      : outcome.state === 'CANCELLED' ? '转写任务已取消；迟到结果不会写入本机任务。'
        : '转写任务状态已更新，请查看任务详情。';
  } catch (error) {
    if (state !== owner || error?.code === 'STALE_DATA_AREA') return;
    state.error = `图片/PDF 转写未完成：${String(error)}`;
  } finally {
    owner.transcriptionBusyTaskId = null;
    if (state === owner) {
      await loadTranscriptionState(owner).catch(() => {});
      if (state === owner) render();
    }
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
  const owner = state;
  const task = transcriptionTask(taskId);
  if (!task?.resultAttachmentId) return;
  try {
    if (owner.temporaryConversation) throw new Error('请先退出临时会话，再将转写结果加入新对话。');
    await flushOrdinaryComposerDraft();
    const result = await invokeForAuthorizedDataAreaOperation(tauriBridge, 'read_desktop_text_preview', { args: { workspaceId: task.workspaceId, attachmentId: task.resultAttachmentId } }, owner);
    const currentDraft = await invokeForAuthorizedDataAreaOperation(tauriBridge, 'read_desktop_conversation_draft', { workspaceId: task.workspaceId, conversationId: null }, owner);
    const saved = await persistOrdinaryComposerDraft({
      dataArea: owner.dataArea, workspaceId: task.workspaceId, conversationId: null,
      text: currentDraft?.text || '',
      attachmentIds: [...new Set([...(currentDraft?.attachments || []).map(item => item.id), result.attachmentId])],
      modelId: currentDraft?.modelId || null,
      toneOverride: currentDraft?.toneOverride || null,
      webSearchOverride: currentDraft?.webSearchOverride ?? null,
    });
    if (!saved?.attachments?.some(item => item.id === result.attachmentId)) throw new Error('转写结果附件未写入新对话草稿');
    if (state !== owner) return;
    if (!await ensureConversationActionWorkspace({ workspaceId: task.workspaceId })) return;
    if (state !== owner) return;
    resetComposerPresentation();
    state.selectedConversationId = null;
    state.newConversationDraftWorkspaceId = task.workspaceId;
    state.composerDraft = saved.text;
    state.composerAttachments = saved.attachments;
    state.pendingNewConversationModelId = saved.modelId || null;
    state.conversationPreferences = { revision: 0, toneOverride: saved.toneOverride || null, webSearchOverride: saved.webSearchOverride ?? null };
    armNewConversationDraftExpiry(saved.expiresAtMs);
    state.pane = state.dataArea === 'WORK' ? 'work' : 'chat';
    state.status = '转写结果已作为本地 Markdown 附件放入新对话草稿；尚未发送。';
    state.error = '';
  } catch (error) { if (state !== owner) return; owner.error = `转写结果未加入对话：${String(error)}`; }
  render();
}

const utilityPagesLoading = new WeakMap();
const loadUtilityPage = createUtilityPageNavigation({
  currentOwner: () => state,
  isCurrent: (page, owner) => state === owner && state.pane === page,
  render: () => render(),
  loading: (page, value, owner) => {
    let pages = utilityPagesLoading.get(owner);
    if (!pages) { pages = new Set(); utilityPagesLoading.set(owner, pages); }
    if (value) pages.add(page);
    else pages.delete(page);
  },
  failed: (page, error) => {
    state.error = `${page === 'reminders' ? '定时任务' : '南枫转写任务'}未读取：${String(error)}`;
  },
});

app.addEventListener('click', async event => {
  const target = event.target.closest?.('[data-action]');
  const action = target?.dataset.action;
  const actions = ['show-reminders', 'show-transcription', 'pick-transcription-document', 'select-transcription-task', 'run-transcription-task', 'retry-transcription-task', 'cancel-transcription-task', 'preview-transcription-source', 'preview-transcription-result', 'copy-transcription-result', 'continue-chat-with-transcription', 'export-transcription-task', 'ask-delete-transcription-task', 'confirm-delete-transcription-task'];
  if (!actions.includes(action)) return;
  event.preventDefault();
  event.stopImmediatePropagation();
  if (action === 'show-reminders' || action === 'show-transcription') {
    state.utilitySidebarWorkMode = false;
    state.pane = action === 'show-reminders' ? 'reminders' : 'transcription';
    state.sidebarOpen = false;
    state.profileOpen = false;
    state.error = '';
    const page = state.pane;
    await loadUtilityPage(page, owner => page === 'reminders'
      ? loadDesktopReminders(owner) : loadTranscriptionState(owner));
    return;
  }
  if (action === 'pick-transcription-document') return pickTranscriptionDocument();
  if (action === 'select-transcription-task') { state.selectedTranscriptionTaskId = target.dataset.taskId; render(); return; }
  if (action === 'run-transcription-task') return executeTranscriptionTask(target.dataset.taskId, false);
  if (action === 'retry-transcription-task') return executeTranscriptionTask(target.dataset.taskId, true);
  if (action === 'cancel-transcription-task' && transcriptionTask(target.dataset.taskId)?.modelId !== 'glm-ocr') {
    state.error = '旧版音视频任务仅供查看和导出。';
    render();
    return;
  }
  if (action === 'cancel-transcription-task') { try { await invoke('cancel_desktop_transcription_task', { taskId: target.dataset.taskId }); state.status = '转写任务已取消；已保存的检查点仍保留供后续显式重试。'; state.error = ''; } catch (error) { state.error = `任务未取消：${String(error)}`; } await loadTranscriptionState().catch(() => {}); render(); return; }
  if (action === 'preview-transcription-source') return previewTranscriptionSource(target.dataset.taskId);
  if (action === 'preview-transcription-result') return previewTranscriptionResult(target.dataset.taskId);
  if (action === 'copy-transcription-result') { const text = (transcriptionTask(target.dataset.taskId)?.segments || []).map(segment => segment.text).join('\n\n'); try { await navigator.clipboard.writeText(text); showCopyIconFeedback(target); state.error = ''; } catch { state.error = '系统未允许写入剪贴板。'; render(); } return; }
  if (action === 'continue-chat-with-transcription') return continueChatWithTranscription(target.dataset.taskId);
  if (action === 'export-transcription-task') return exportTranscriptionTask(target.dataset.taskId, target.dataset.format);
  if (action === 'ask-delete-transcription-task') { state.dialog = { kind: 'transcription-delete', taskId: target.dataset.taskId, submitting: false, failure: '' }; render(); return; }
  if (action === 'confirm-delete-transcription-task') { state.dialog = { ...state.dialog, submitting: true, failure: '' }; render(); try { await invoke('delete_desktop_transcription_task', { taskId: target.dataset.taskId }); state.dialog = null; state.selectedTranscriptionTaskId = null; state.status = '转写任务已删除；无引用私有字节进入安全清理期。'; await loadTranscriptionState(); } catch (error) { state.dialog = { ...state.dialog, submitting: false, failure: String(error) }; } render(); }
}, true);
function modelServiceEditorIsCurrent(owner, providerId) {
  return state === owner && owner.pane === 'settings' && owner.settingsSection === 'model-configuration'
    && owner.modelProviderId === providerId;
}
function clearOwnedModelCredentialDraft(owner) {
  owner.modelCredentialDraft = '';
  owner.modelCredentialEdited = false;
  owner.modelCredentialVisible = false;
}
async function saveModelServiceSettings() {
  const owner = state;
  const service = selectedModelService(owner);
  const draft = owner.modelServiceDraft;
  if (!service || !draft || owner.modelSettingsSaving) return;
  if (draft.revision !== service.revision) {
    owner.modelSettingsNotice = '';
    owner.modelSettingsError = '模型配置已在另一数据区更新；请重新选择服务商并确认设置后保存。';
    render();
    return;
  }
  if (!native) {
    owner.modelSettingsError = 'Web 预览不会保存 API Key；请在 Desktop 应用中操作。';
    render();
    return;
  }
  const credentialDraft = owner.modelCredentialDraft;
  const credentialEdited = owner.modelCredentialEdited;
  const editorUnchanged = () => owner.modelServiceDraft?.providerId === service.providerId
    && owner.modelCredentialDraft === credentialDraft && owner.modelCredentialEdited === credentialEdited;
  owner.modelSettingsSaving = true;
  owner.modelSettingsNotice = '';
  owner.modelSettingsError = '';
  render();
  try {
    const saved = await localSettingsWrites.track(modelSettingsController.enqueue(owner, () => {
      const committed = owner.modelServiceSettings.find(item => item.providerId === service.providerId);
      return invokeForAuthorizedDataAreaOperation(tauriBridge,
        'save_desktop_model_service_settings', { args: {
          providerId: service.providerId, enabled: Boolean(committed.enabled), presetId: committed.presetId,
          expectedRevision: committed.revision, preserveEnabled: true, preservePreset: true,
          apiKey: credentialEdited && credentialDraft.trim() ? credentialDraft.trim() : null,
        } }, owner);
    }), `model-settings:${service.providerId}:credential`);
    replaceModelService(saved, owner);
    if (!modelServiceEditorIsCurrent(owner, service.providerId) || !editorUnchanged()) return;
    owner.status = modelServiceConfigurationStatus(owner.modelServiceSettings);
    owner.modelServiceDraft = { providerId: saved.providerId, enabled: saved.enabled, presetId: saved.presetId, revision: saved.revision };
    if (credentialEdited && credentialDraft.trim()) {
      owner.modelSettingsNotice = '';
      owner.dialog = { kind: 'model-credential-saved' };
    } else owner.modelSettingsNotice = '模型设置已保存。';
    modelSettingsController.feedback(owner, service.providerId, 'API Key 已保存。');
    clearOwnedModelCredentialDraft(owner);
  } catch (error) {
    if (editorUnchanged()) modelSettingsController.feedback(owner, service.providerId, '', `保存失败：${String(error)}`);
  } finally {
    owner.modelSettingsSaving = false;
    if (!modelServiceEditorIsCurrent(owner, service.providerId) && editorUnchanged()) clearOwnedModelCredentialDraft(owner);
    if (state === owner) render();
  }
}
async function deleteModelServiceCredential() {
  const owner = state;
  const request = owner.dialog;
  if (!native || request?.kind !== 'model-credential-delete' || request.submitting) return;
  const pendingRequest = { ...request, submitting: true, failure: '' };
  const draft = owner.modelServiceDraft;
  owner.dialog = pendingRequest;
  owner.modelSettingsSaving = true;
  render();
  const currentRequest = () => state === owner && owner.dialog === pendingRequest;
  try {
    const updated = await localSettingsWrites.track(invokeForAuthorizedDataAreaOperation(tauriBridge,
      'delete_desktop_model_service_credential', { providerId: request.providerId }, owner));
    replaceModelService(updated, owner);
    if (!currentRequest()) return;
    if (modelServiceEditorIsCurrent(owner, updated.providerId) && owner.modelServiceDraft === draft) {
      owner.modelServiceDraft = { providerId: updated.providerId, enabled: updated.enabled, presetId: updated.presetId, revision: updated.revision };
      clearOwnedModelCredentialDraft(owner);
    }
    owner.status = modelServiceConfigurationStatus(owner.modelServiceSettings);
    owner.dialog = { kind: 'model-settings-feedback', failed: false, message: `“${updated.providerDisplayName}”的 API Key 已从本机删除。` };
    owner.modelSettingsError = '';
  } catch (error) {
    if (currentRequest()) owner.dialog = { ...request, submitting: false, failure: String(error) };
  } finally {
    owner.modelSettingsSaving = false;
    if (state === owner) render();
  }
}
async function revealModelServiceCredential() {
  const owner = state;
  const service = selectedModelService(owner);
  if (!service || owner.modelSettingsSaving || owner.modelSettingsTesting) return;
  if (owner.modelCredentialVisible) {
    owner.modelCredentialVisible = false;
    render();
    return;
  }
  if (typeof owner.modelCredentialDraft === 'string' && owner.modelCredentialDraft.length > 0) {
    owner.modelCredentialVisible = true;
    render();
    return;
  }
  if (!native) {
    owner.modelSettingsError = 'Web 预览不会读取本机凭据。';
    render();
    return;
  }
  const draft = owner.modelServiceDraft;
  const credentialDraft = owner.modelCredentialDraft;
  const currentReveal = () => modelServiceEditorIsCurrent(owner, service.providerId)
    && owner.modelServiceDraft === draft && owner.modelCredentialDraft === credentialDraft
    && !owner.modelCredentialEdited && selectedModelService(owner)?.revision === service.revision;
  try {
    const revealed = await invokeForAuthorizedDataAreaOperation(tauriBridge,
      'reveal_desktop_model_service_credential', { providerId: service.providerId }, owner);
    if (!currentReveal()) return;
    owner.modelCredentialDraft = revealed;
    owner.modelCredentialVisible = true;
    owner.modelSettingsError = '';
  } catch (error) {
    if (!currentReveal()) return;
    owner.modelSettingsError = `无法显示 API Key：${String(error)}`;
  }
  if (state === owner) render();
}
async function refreshOpenrouterRegistry() {
  if (state.openrouterRegistryRefreshing) return;
  if (!native) {
    state.openrouterRegistryError = 'Web 预览不能核验在线模型目录。';
    render();
    return;
  }
  state.openrouterRegistryRefreshing = true;
  state.openrouterRegistryNotice = '';
  state.openrouterRegistryError = '';
  render();
  try {
    const status = await invoke('refresh_desktop_openrouter_registry');
    for (const owner of dataAreaStates.values()) owner.openrouterRegistryStatus = status;
    state.openrouterRegistryNotice = '公开模型目录已核验并保存。';
  } catch (error) {
    state.openrouterRegistryError = `目录核验失败：${String(error)}`;
    try { state.openrouterRegistryStatus = await invoke('read_desktop_openrouter_registry_status'); } catch { /* Keep the last displayed status. */ }
  } finally {
    state.openrouterRegistryRefreshing = false;
    render();
  }
}
async function testModelServiceConnection() {
  const owner = state;
  const service = selectedModelService(owner);
  if (!service || owner.modelSettingsTesting || !service.credentialStored) return;
  if (owner.modelCredentialEdited) { modelSettingsController.feedback(owner, service.providerId, '', '请先保存新的 API Key，再测试连接。'); render(); return; }
  if (!native) {
    owner.modelSettingsError = 'Web 预览不会发送连接测试。';
    render();
    return;
  }
  const probePresetId = owner.modelServiceDraft?.providerId === service.providerId ? owner.modelServiceDraft.presetId : service.presetId;
  const probePreset = [...(service.presets || []), ...(service.nonChatCapabilities || [])].find(item => item.id === probePresetId);
  const currentTest = () => modelServiceEditorIsCurrent(owner, service.providerId)
    && selectedModelService(owner)?.revision === service.revision
    && selectedModelService(owner)?.presetId === service.presetId;
  owner.modelSettingsTesting = true;
  owner.modelSettingsNotice = '';
  owner.modelSettingsError = '';
  render();
  try {
    await invokeForAuthorizedDataAreaOperation(tauriBridge, 'test_desktop_model_service_connection', {
      args: { providerId: service.providerId, presetId: probePresetId },
    }, owner);
    if (currentTest()) modelSettingsController.feedback(owner, service.providerId, `连接成功：${service.providerDisplayName} · ${probePreset?.displayName || service.presetDisplayName}`);
  } catch (error) {
    if (currentTest()) modelSettingsController.feedback(owner, service.providerId, '', `连接测试失败：${String(error)}`);
  } finally {
    owner.modelSettingsTesting = false;
    if (state === owner) render();
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
let searchLocateGeneration = 0;
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
    // The compact local/cloud switch owns both its selected pill and the list
    // below it. Retaining the old sidebar here would leave the control visually
    // frozen even though the state has already changed.
    && previous.sidebarConversationList === next.sidebarConversationList
    && previous.cloudConversations === next.cloudConversations
    && previous.batchEditing === next.batchEditing
    && previous.batchSelectedConversationKeys === next.batchSelectedConversationKeys
    && previous.chatSearch === next.chatSearch
    && previous.searchHistory === next.searchHistory
    && previous.searchHistoryOpen === next.searchHistoryOpen
    && previous.favoriteConversationIds === next.favoriteConversationIds
    // Cloud rows intentionally omit the local synced marker (shared Mac UI).
    // Metadata-only membership refresh must not rebuild this unchanged list.
    && (next.sidebarConversationList === 'cloud'
      || previous.syncedConversationKeys === next.syncedConversationKeys)
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
  searchLocateGeneration += 1;
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
  const cacheScope = JSON.stringify([state.dataArea, state.workspaces, state.current?.summary]);
  if (cacheScope !== fullSearchCacheScope) { fullSearchResultCache.clear(); fullSearchCacheScope = cacheScope; }
  const appendBaseHits = append ? [...(state.searchPage?.hits || [])] : [];
  const offset = appendBaseHits.length;
  const searchArgs = { query: state.chatSearch, category: state.searchCategory || 'all', sortMode: state.searchSortMode, fileType: state.searchCategory === 'file' ? state.searchFileType : 'all', offset };
  const cachedPage = native ? fullSearchResultCache.get(searchArgs) : null;
  state.searchPanel = true;
  state.searchLoading = !append && !cachedPage;
  state.searchLoadingMore = append && !cachedPage;
  if (cachedPage) {
    state.searchPage = append ? { ...cachedPage, hits: [...appendBaseHits, ...(cachedPage.hits || [])] } : cachedPage;
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
    if (generation !== fullSearchGeneration) return;
    if (cacheScope === fullSearchCacheScope) fullSearchResultCache.set(searchArgs, page);
    if (cachedPage || append) rememberFullSearchScroll();
    state.searchPage = append ? { ...page, hits: [...appendBaseHits, ...(page.hits || [])] } : page;
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
    if (error?.code === 'STALE_DATA_AREA') return;
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

async function loadSearchWorkspace(workspaceId, isCurrent = () => true) {
  if (!native || !workspaceId) return false;
  if (state.current?.summary?.id === workspaceId) return true;
  const current = await invoke('read_desktop_workspace', { workspaceId });
  if (!isCurrent()) return false;
  state.current = current;
  const history = await invoke('read_desktop_workbench_history', { workspaceId });
  if (!isCurrent()) return false;
  state.history = history;
  reloadFavoriteConversationIds();
  await loadConversationReadState({ observeSelected: false, isCurrent });
  return isCurrent();
}

async function locateSearchHit(hit) {
  if (!hit) return;
  const owner = state;
  const generation = ++searchLocateGeneration;
  const isCurrent = () => state === owner && generation === searchLocateGeneration;
  const alreadyShowingWorkspace = owner.current?.summary?.id === hit.workspaceId;
  let staleHit = false;
  rememberFullSearchScroll(hit.entryId);
  try {
    await loadSearchWorkspace(hit.workspaceId, isCurrent);
    if (!isCurrent()) return;
    if (String(hit.conversationId || '').startsWith('transcription-task:')) {
      const taskId = String(hit.conversationId).slice('transcription-task:'.length);
      await loadTranscriptionState(owner);
      if (!isCurrent()) return;
      const task = owner.transcription.tasks.find(item => item.id === taskId);
      if (!task) {
        staleHit = true;
        throw new Error('结果所属转写任务已删除');
      }
      state.selectedTranscriptionTaskId = taskId;
      state.searchReturnActive = true;
      state.searchPanel = false;
      state.pane = 'transcription';
      state.transcriptionMode = task.modelId === 'glm-ocr' ? 'document' : 'speech';
      render();
      return;
    }
    if (alreadyShowingWorkspace) {
      const refreshed = await invoke('read_desktop_workspace', { workspaceId: hit.workspaceId });
      if (!isCurrent()) return;
      state.current = refreshed;
    }
    let conversation = state.current?.exchange?.conversations?.find(item => item.id === hit.conversationId);
    if (!conversation || conversation.deleted) {
      staleHit = true;
      throw new Error('结果所属会话已变化或删除');
    }
    if (hit.messageId && !conversation.messages?.some(message => message.id === hit.messageId)) {
      staleHit = true;
      throw new Error('结果所属消息已删除');
    }
    if (hit.attachmentId && !conversation.messages?.some(message => message.id === hit.messageId &&
      message.blocks?.some(block => block.kind === 'ASSET_REF' && block.asset?.id === hit.attachmentId))) {
      staleHit = true;
      throw new Error('结果所属附件引用已删除');
    }
    if (hit.branchLeafId && conversation.currentLeafId !== hit.branchLeafId) {
      await invoke('mutate_desktop_domain', { args: {
        intentId: intent('search-switch-leaf'), workspaceId: hit.workspaceId, entity: 'conversation', action: 'switchLeaf',
        objectId: hit.conversationId, expectedRevision: Number(conversation.revision), fields: { messageId: hit.branchLeafId },
      } });
      if (!isCurrent()) return;
      const current = await invoke('read_desktop_workspace', { workspaceId: hit.workspaceId });
      if (!isCurrent()) return;
      state.current = current;
      conversation = state.current.exchange.conversations.find(item => item.id === hit.conversationId);
    }
    state.selectedConversationId = hit.conversationId;
    await markConversationOpened(hit.conversationId, isCurrent);
    if (!isCurrent()) return;
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
      if (!isCurrent() || state.pane !== 'chat' || state.selectedConversationId !== hit.conversationId) return;
      const { target } = resolveSearchResultAnchor(document, hit);
      target?.scrollIntoView({ block: 'center', inline: 'nearest' });
      if (target) { target.classList.add('search-anchor-flash'); window.setTimeout(() => target.classList.remove('search-anchor-flash'), 1600); }
    });
  } catch (error) {
    if (!isCurrent()) return;
    if (staleHit) {
      fullSearchResultCache.clear();
      try { await runFullSearch({ restoreScroll: true }); } catch { /* Keep the original stale-hit error. */ }
      if (!isCurrent()) return;
    }
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
  state.selectedConversationId = data?.dataArea === 'WORK'
    ? resolveWorkConversationState(data, null, null).conversations[0]?.id || null
    : data ? activeConversations(data)[0]?.id || null : null;
}
function active(data, key) { return data.exchange[key].filter(item => key === 'relations' ? item.status === 'ACTIVE' : item.status !== 'DELETED' && !item.archived && !item.deleted); }
function workItemScopeField(item, id, scopeIdKey, selectedProjectId = '') {
  const projects = active(workspace(), 'projects');
  if (item) {
    const projectId = item[scopeIdKey];
    const title = projects.find(project => project.id === projectId)?.title || '已归档项目';
    const scopeLabel = item.scope === 'PROJECT' ? `仅项目「${escape(title)}」` : item.scope === 'CONVERSATION' ? '仅所属对话' : '所有对话';
    return `<p class="security-note">适用范围：${scopeLabel}。现有记录的范围保持不变。</p>`;
  }
  return `<label>适用范围<select id="${id}"><option value="" ${selectedProjectId ? '' : 'selected'}>所有对话</option>${projects.map(project => `<option value="${escape(project.id)}" ${project.id === selectedProjectId ? 'selected' : ''}>仅项目「${escape(project.title)}」</option>`).join('')}</select></label>`;
}
function dialog() {
  if (!state.dialog) return '';
  const saveShortcut = /Mac/i.test(navigator.platform || '') ? '⌘S' : 'Ctrl+S';
  if (state.dialog?.kind === 'conversation-branches') {
    const conversation = state.current?.summary?.id === state.dialog.workspaceId
      ? resolveConversation(state.current, state.dialog.conversationId) : null;
    const leaves = conversationBranchLeaves(conversation);
    return `<div class="scrim"><section class="dialog edit-dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title" aria-busy="${Boolean(state.dialog.submitting)}"><h2 id="dialog-title">分支历史</h2><p>选择一条已保存的消息树路径；旧回答与编辑版本会保留。</p><div class="recycle-list" aria-label="可切换的分支">${leaves.map((leaf, index) => {
      const current = leaf.id === conversation.currentLeafId;
      const preview = messagePlainText(leaf).trim().slice(0, 80) || (leaf.role === 'user' ? '用户消息' : '回答');
      return `<div><span>分支 ${index + 1} · ${escape(preview)}${current ? ' · 当前' : ''}</span><button data-action="select-conversation-branch" data-leaf-id="${escape(leaf.id)}" ${current || conversation.archived || conversation.deleted || state.dialog.submitting ? 'disabled' : ''}>${current ? '当前' : '切换'}</button></div>`;
    }).join('') || '<p class="empty-copy">没有可切换的分支。</p>'}</div>${state.dialog.failure ? `<p class="dialog-error" role="alert">${escape(state.dialog.failure)}</p>` : ''}<div class="dialog-actions"><button data-action="close-dialog" ${state.dialog.submitting ? 'disabled' : ''}>关闭</button></div></section></div>`;
  }
  if (state.dialog?.kind === 'edit-conversation-user-message') {
    const edit = state.dialog;
    return `<div class="scrim"><section class="dialog edit-dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title" aria-busy="${Boolean(edit.submitting)}"><h2 id="dialog-title">编辑消息</h2><p>保存后会在本机创建新的用户消息分支；原消息和回答仍可从分支历史切回。</p><label>消息文字<textarea id="conversation-user-message-edit" maxlength="2000000" ${edit.submitting ? 'disabled' : ''}>${escape(edit.text)}</textarea></label>${edit.failure ? `<p class="dialog-error" role="alert">${escape(edit.failure)}</p>` : ''}<div class="dialog-actions"><button data-action="close-dialog" ${edit.submitting ? 'disabled' : ''}>取消</button><button class="primary" data-action="save-conversation-user-message-edit" ${edit.submitting ? 'disabled' : ''}>${edit.submitting ? '正在保存…' : '保存为新分支'}</button></div></section></div>`;
  }
  if (state.dialog?.kind === 'change-answer-model') {
    const selection = state.dialog;
    const conversation = state.current?.summary?.id === selection.workspaceId
      ? resolveConversation(state.current, selection.conversationId) : null;
    const source = conversation?.messages.find(item => item.id === selection.messageId);
    const currentSource = conversation?.currentLeafId === selection.messageId && !conversation.archived
      && !conversation.deleted && source?.role === 'assistant' && Boolean(source.attemptId)
      && !source.compareExecutionId && ['COMPLETE', 'FAILED', 'CANCELLED', 'UNKNOWN'].includes(source.delivery);
    const choices = (currentSource ? state.modelServiceSettings || [] : []).filter(service => service.enabled && service.credentialStored)
      .flatMap(service => (service.presets || []).filter(preset => preset.chatSelectable !== false
        && !(service.providerId === source?.modelSnapshot?.providerId && preset.modelId === source?.modelSnapshot?.modelId))
        .map(preset => ({ ...preset, providerId: service.providerId, providerName: service.providerDisplayName })));
    const notice = !currentSource ? '当前回答已变化，请关闭后重新选择。'
      : source.delivery === 'UNKNOWN'
        ? '原请求结果未知，服务商可能已处理。选择目标模型会把当前问题、分支上下文及引用附件发给所选服务商，可能产生另一笔费用。'
        : '选择目标模型会把当前问题、分支上下文及引用附件发给所选服务商。原回答、分支和用量记录保留。';
    return `<div class="scrim"><section class="dialog edit-dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><h2 id="dialog-title">换模型重答</h2><p>${notice}</p><div class="recycle-list" aria-label="可用模型">${choices.map(choice => `<div><span>${escape(choice.providerName)} · ${escape(choice.displayName)}</span><button data-action="confirm-change-answer-model" data-message-id="${escape(selection.messageId)}" data-target-preset-id="${escape(choice.id)}">选择并生成</button></div>`).join('') || '<p class="empty-copy">没有已启用且保存凭据的其他聊天模型。</p>'}</div><div class="dialog-actions"><button data-action="close-dialog">取消</button></div></section></div>`;
  }
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
  if (state.dialog?.kind === 'model-credential-delete') { const busy = state.dialog.submitting; return `<div class="scrim"><section class="dialog" role="alertdialog" aria-modal="true" aria-labelledby="model-credential-delete-title" aria-busy="${busy}"><h2 id="model-credential-delete-title">删除这个 API Key？</h2><p>只删除“${escape(state.dialog.providerDisplayName)}”保存在本机的 API Key；其他服务商、模型选择、聊天和文件不受影响。删除后如需使用该服务商，必须重新填写。</p>${state.dialog.failure ? `<p class="dialog-error" role="alert">${escape(state.dialog.failure)}</p>` : ''}<div class="dialog-actions"><button data-action="close-dialog" ${busy ? 'disabled' : ''}>取消</button><button class="primary danger" data-action="confirm-delete-model-service-credential" ${busy ? 'disabled' : ''}>${busy ? '正在删除…' : '删除 API Key'}</button></div></section></div>`; }
  if (state.dialog === 'import') return `<div class="scrim"><section class="dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="关闭">${icon(icons.close, '关闭')}</button><p class="overline">严格预检已通过</p><h2 id="dialog-title">导入为新的独立工作区</h2><p>不会合并或覆盖。正文仅作为不执行的文本 IR。</p><label>新工作区名称<input id="workspace-title" maxlength="120" value="导入工作区"></label><div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary" data-action="confirm-import">导入</button></div></section></div>`;
  if (state.dialog.kind === 'project') { const item = state.dialog.item; const draft = state.dialog.draft || {}; return `<div class="scrim"><section class="dialog edit-dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="取消">${icon(icons.close, '关闭')}</button><p class="overline">本地 revision 写入</p><h2 id="dialog-title">${item ? '编辑 Project' : '新建 Project'}</h2><p>保存由 Rust 检查 expected revision；冲突不会覆盖现有对象。</p><label>名称<input id="project-title" maxlength="120" value="${escape(draft['project-title'] ?? item?.title ?? '')}"></label><label>说明<textarea id="project-description" maxlength="2000">${escape(draft['project-description'] ?? item?.description ?? '')}</textarea></label>${renderDomainConflict(state.dialog, escape)}<div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary" data-action="save-project">保存 ${saveShortcut}</button></div></section></div>`; }
  if (state.dialog.kind === 'markdown-knowledge-review') { const item = state.dialog.item, draft = state.dialog.draft || {}; return `<div class="scrim"><section class="dialog edit-dialog" data-task-id="${escape(state.dialog.taskId)}" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="取消">${icon(icons.close, '取消')}</button><p class="overline">Markdown 导入 · 等待明确确认</p><h2 id="dialog-title">审阅知识条目</h2><p>只保存下面明确确认的内容。Markdown、代码和链接不执行，选择文件不会联网。</p>${workItemScopeField(null, 'markdown-knowledge-project', 'projectId', draft['markdown-knowledge-project'] || '')}<label>标题<input id="markdown-knowledge-title" maxlength="120" value="${escape(draft['markdown-knowledge-title'] ?? item.title)}"></label><label>正文<textarea id="markdown-knowledge-body" maxlength="24000">${escape(draft['markdown-knowledge-body'] ?? item.body)}</textarea></label><label>标签（逗号分隔）<input id="markdown-knowledge-tags" value="${escape(draft['markdown-knowledge-tags'] ?? item.tags.join(', '))}"></label><div class="dialog-actions"><button data-action="close-dialog">返回待确认列表</button><button class="primary" data-action="confirm-markdown-knowledge">确认保存为本机知识</button></div></section></div>`; }
  if (state.dialog.kind === 'web-knowledge-url') { const draft = state.dialog.draft || {}; return `<div class="scrim"><section class="dialog edit-dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="取消">${icon(icons.close, '取消')}</button><p class="overline">网页文字快照 · 需要明确抓取</p><h2 id="dialog-title">${state.dialog.retry ? '重新输入网页地址' : '从 HTTPS 网页提取文字'}</h2><p>确认后将直接访问此公开网页。仅提取文字，不登录网站，不使用浏览器 Cookie，也不加载图片或执行脚本。确认前不联网，正文逐条审阅后才保存为知识。</p><label>完整 HTTPS 地址<input id="web-knowledge-url" type="url" autocomplete="off" spellcheck="false" placeholder="https://example.com/article" value="${escape(draft['web-knowledge-url'] || '')}"></label><p class="security-note">请勿输入含密钥、令牌或登录凭据的地址。地址 query 仅用于本次请求；失败重试必须重新输入，不从历史摘要恢复。</p>${state.error ? `<p role="alert">${escape(state.error)}</p>` : ''}<div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary" data-action="fetch-web-knowledge">确认抓取网页文字</button></div></section></div>`; }
  if (state.dialog.kind === 'web-knowledge-review') { const item = state.dialog.item, draft = state.dialog.draft || {}; return `<div class="scrim"><section class="dialog edit-dialog" data-task-id="${escape(state.dialog.taskId)}" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="取消">${icon(icons.close, '取消')}</button><p class="overline">网页文字快照 · 等待明确确认</p><h2 id="dialog-title">审阅条目 ${state.dialog.ordinal + 1}</h2><p>${escape(state.dialog.requestedUrl)} · 只保存下面明确确认的内容，原网页与来源回执保留在本机。</p>${workItemScopeField(null, 'web-knowledge-project', 'projectId', draft['web-knowledge-project'] || '')}<label>标题<input id="web-knowledge-title" maxlength="120" value="${escape(draft['web-knowledge-title'] ?? item.title)}"></label><label>正文<textarea id="web-knowledge-body" maxlength="24000">${escape(draft['web-knowledge-body'] ?? item.body)}</textarea></label><label>标签（逗号分隔）<input id="web-knowledge-tags" value="${escape(draft['web-knowledge-tags'] ?? item.tags.join(', '))}"></label>${state.error ? `<p role="alert">${escape(state.error)}</p>` : ''}<div class="dialog-actions"><button data-action="close-dialog">返回待确认列表</button><button class="primary" data-action="confirm-web-knowledge">确认保存为本机知识</button></div></section></div>`; }
  if (state.dialog.kind === 'pdf-knowledge-review') { const item = state.dialog.item, draft = state.dialog.draft || {}; return `<div class="scrim"><section class="dialog edit-dialog" data-task-id="${escape(state.dialog.taskId)}" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="取消">${icon(icons.close, '取消')}</button><p class="overline">PDF 文字导入 · 等待明确确认</p><h2 id="dialog-title">审阅第 ${state.dialog.pageNumber} 页</h2><p>只保存下面明确确认的内容。只提取可读取文字层，不还原原版排版，不扫描识别，也不执行文件中的脚本、链接和附件。</p>${workItemScopeField(null, 'pdf-knowledge-project', 'projectId', draft['pdf-knowledge-project'] || '')}<label>标题<input id="pdf-knowledge-title" maxlength="120" value="${escape(draft['pdf-knowledge-title'] ?? item.title)}"></label><label>正文<textarea id="pdf-knowledge-body" maxlength="24000">${escape(draft['pdf-knowledge-body'] ?? item.body)}</textarea></label><label>标签（逗号分隔）<input id="pdf-knowledge-tags" value="${escape(draft['pdf-knowledge-tags'] ?? item.tags.join(', '))}"></label><div class="dialog-actions"><button data-action="close-dialog">返回待确认列表</button><button class="primary" data-action="confirm-pdf-knowledge">确认保存为本机知识</button></div></section></div>`; }
  if (state.dialog.kind === 'json-knowledge-review') { const item = state.dialog.item, draft = state.dialog.draft || {}; return `<div class="scrim"><section class="dialog edit-dialog" data-task-id="${escape(state.dialog.taskId)}" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="取消">${icon(icons.close, '取消')}</button><p class="overline">JSON 导入 · 等待明确确认</p><h2 id="dialog-title">审阅知识条目</h2><p>只保存下面明确确认的内容。JSON、代码和链接不执行，选择文件不会联网。</p><p>来源：${escape(state.dialog.source?.kind || '未提供')} · ${escape(state.dialog.source?.summary || '')} · 版本 ${escape(state.dialog.source?.revision || '')}。${state.dialog.source?.requestedScope === 'PROJECT' ? '来源请求项目范围；请选择目标项目，否则保存到全局。' : '项目只按下面明确选择的目标保存。'}</p>${workItemScopeField(null, 'json-knowledge-project', 'projectId', draft['json-knowledge-project'] || '')}<label>标题<input id="json-knowledge-title" maxlength="120" value="${escape(draft['json-knowledge-title'] ?? item.title)}"></label><label>正文<textarea id="json-knowledge-body" maxlength="24000">${escape(draft['json-knowledge-body'] ?? item.body)}</textarea></label><label>标签（逗号分隔）<input id="json-knowledge-tags" value="${escape(draft['json-knowledge-tags'] ?? item.tags.join(', '))}"></label><div class="dialog-actions"><button data-action="close-dialog">返回待确认列表</button><button class="primary" data-action="confirm-json-knowledge">确认保存为本机知识</button></div></section></div>`; }

  if (state.dialog.kind === 'json-knowledge-export') { const dialog = state.dialog; return `<div class="scrim"><section class="dialog edit-dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="取消">${icon(icons.close, '取消')}</button><h2 id="dialog-title">导出已保存知识</h2><p>仅导出勾选的有效知识。未保存的编辑草稿、附件、记忆和关系不包含在文件中。重新导入时生成新的本地 ID。</p><p>已选择 ${dialog.selectedIds.length} 条，单次最多 100 条。</p><ul>${dialog.eligible.map(item => `<li><label><input type="checkbox" data-action="toggle-json-knowledge-export" data-id="${escape(item.id)}" ${dialog.selectedIds.includes(item.id) ? 'checked' : ''} ${dialog.busy ? 'disabled' : ''}>${escape(item.title)}</label></li>`).join('')}</ul><div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary" data-action="save-json-knowledge-export" ${dialog.busy || !dialog.selectedIds.length || dialog.selectedIds.length > 100 ? 'disabled' : ''}>${dialog.busy ? '正在导出…' : '选择位置并导出'}</button></div></section></div>`; }

  if (state.dialog.kind === 'knowledge') { const item = state.dialog.item; const draft = state.dialog.draft || {}; return `<div class="scrim"><section class="dialog edit-dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="取消">${icon(icons.close, '关闭')}</button><p class="overline">本地 revision 写入</p><h2 id="dialog-title">${item ? '编辑 Knowledge' : '新建 Knowledge'}</h2><p>保存时由 Rust 检查 expected revision；冲突不会覆盖当前记录。</p>${workItemScopeField(item, 'knowledge-project', 'projectId', draft['knowledge-project'] || '')}<label>标题<input id="knowledge-title" maxlength="120" value="${escape(draft['knowledge-title'] ?? item?.title ?? '')}"></label><label>正文<textarea id="knowledge-body" maxlength="2000000">${escape(draft['knowledge-body'] ?? item?.body ?? '')}</textarea></label><label>标签（逗号分隔）<input id="knowledge-tags" value="${escape(draft['knowledge-tags'] ?? (item?.tags || []).join(', '))}"></label>${item ? renderKnowledgeDuplicates(state.dialog, escape) : ''}${renderDomainConflict(state.dialog, escape)}<div class="dialog-actions">${item?.status === 'ACTIVE' && native ? `<button data-action="read-knowledge-duplicates" ${state.dialog.duplicateLoading || state.dialog.conflict ? 'disabled' : ''}>${state.dialog.duplicateLoading ? '读取重复候选…' : '查看重复候选'}</button><button data-action="open-json-knowledge-export" data-id="${escape(item.id)}">导出已保存知识…</button>` : ''}${!item ? '<button data-action="select-markdown-knowledge">从 Markdown 导入…</button><button data-action="select-json-knowledge">从 Knowledge JSON 导入…</button><button data-action="select-pdf-knowledge">从 PDF 文字导入…</button><button data-action="select-web-knowledge">从 HTTPS 网页文字导入…</button>' : ''}<button data-action="close-dialog">取消</button><button class="primary" data-action="save-knowledge">保存 ${saveShortcut}</button></div></section></div>`; }
  if (state.dialog?.kind === 'history-knowledge-review') { const item = state.dialog.item; return `<div class="scrim"><section class="dialog edit-dialog history-knowledge-review" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="关闭">${icon(icons.close, '关闭')}</button><p class="overline">历史对话自动整理 · 待核对候选</p><h2 id="dialog-title">核对资料候选</h2><p>候选尚未进入长期 Memory 或 Knowledge。确认后只保存你当前看到并可编辑的内容；原对话不会改写。</p><label>标题<input id="history-candidate-title" maxlength="120" value="${escape(state.dialog.draft?.['history-candidate-title'] ?? item.title ?? '')}"></label><label>候选内容<textarea id="history-candidate-body" maxlength="2000000">${escape(state.dialog.draft?.['history-candidate-body'] ?? item.body ?? '')}</textarea></label><label>标签（逗号分隔）<input id="history-candidate-tags" value="${escape(state.dialog.draft?.['history-candidate-tags'] ?? (item.tags || []).join(', '))}"></label><p class="security-note">来源会话 ${escape(item.conversationId)} · 消息 ${Number(item.sourceMessageIds?.length || 0)} 条 · ${escape(item.providerId || '未发送')} / ${escape(item.actualModelId || item.requestedModelId || '未知模型')} · checkpoint ${escape(short(item.checkpointId))}</p><div class="dialog-actions"><button data-action="reject-history-knowledge" data-id="${escape(item.candidateId)}">驳回</button><button class="primary" data-action="accept-history-knowledge" data-id="${escape(item.candidateId)}">确认保存为本地知识</button></div></section></div>`; }
  if (state.dialog?.kind === 'reminder-draft') { const item = state.dialog.item; const option = (value, label) => `<option value="${value}" ${item.scheduleKind === value ? 'selected' : ''}>${label}</option>`; return `<div class="scrim"><section class="dialog edit-dialog reminder-draft-dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="关闭">${icon(icons.close, '关闭')}</button><p class="overline">提醒草案 · 必须确认</p><h2 id="dialog-title">核对计划与监控</h2><p>模型只整理草案，不会替你创建计划。请核对对象、条件、频率、时区与错过策略。</p><label>计划名称<input id="reminder-title" maxlength="40" value="${escape(item.title || '')}" placeholder="例如：检查项目状态"></label><label>执行说明<textarea id="reminder-instruction" maxlength="8000" rows="5" placeholder="写清要检查什么、何时算完成">${escape(item.instruction || '')}</textarea></label><label>执行频率<select id="reminder-schedule-kind">${option('ONCE', '一次')}${option('HOURLY', '每小时')}${option('DAILY', '每天')}${option('WEEKLY', '每周')}</select></label><label>本地时间<input id="reminder-anchor-local" type="datetime-local" value="${escape(item.anchorLocal || '')}"></label><label>时区<input id="reminder-timezone" maxlength="64" value="${escape(item.timezoneId || '')}" placeholder="Asia/Shanghai"></label><label>错过执行窗口后<select id="reminder-missed-policy"><option value="RUN_ONCE" ${item.missedPolicy === 'RUN_ONCE' ? 'selected' : ''}>恢复后执行一次</option><option value="SKIP" ${item.missedPolicy === 'SKIP' ? 'selected' : ''}>跳过本次</option></select></label><p class="security-note">${escape(item.providerId || '手工草案')} · ${escape(item.actualModelId || item.requestedModelId || '未调用模型')} · ${item.chargeMicros == null ? '费用未报告' : `$${(item.chargeMicros / 1_000_000).toFixed(6)}`}</p><div class="dialog-actions"><button data-action="reject-reminder-draft" data-id="${escape(item.draftId)}">拒绝</button><button class="primary" data-action="confirm-reminder-draft">确认并创建计划</button></div></section></div>`; }
  if (state.dialog?.kind === 'reminder-plan') { const item = state.dialog.item; const option = (value, label) => `<option value="${value}" ${item.scheduleKind === value ? 'selected' : ''}>${label}</option>`; return `<div class="scrim"><section class="dialog edit-dialog reminder-draft-dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="关闭">${icon(icons.close, '关闭')}</button><p class="overline">已确认计划 · 本机更新</p><h2 id="dialog-title">编辑计划与监控</h2><p>保存会保留既有执行记录，并按新频率、时间和时区重新计算下一次执行。</p><label>计划名称<input id="reminder-title" maxlength="40" value="${escape(item.title || '')}"></label><label>执行说明<textarea id="reminder-instruction" maxlength="8000" rows="5">${escape(item.instruction || '')}</textarea></label><label>执行频率<select id="reminder-schedule-kind">${option('ONCE', '一次')}${option('HOURLY', '每小时')}${option('DAILY', '每天')}${option('WEEKLY', '每周')}</select></label><label>本地时间<input id="reminder-anchor-local" type="datetime-local" value="${escape(item.anchorLocal || '')}"></label><label>时区<input id="reminder-timezone" maxlength="64" value="${escape(item.timezoneId || '')}"></label><label>错过执行窗口后<select id="reminder-missed-policy"><option value="RUN_ONCE" ${item.missedPolicy === 'RUN_ONCE' ? 'selected' : ''}>恢复后执行一次</option><option value="SKIP" ${item.missedPolicy === 'SKIP' ? 'selected' : ''}>跳过本次</option></select></label><div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary" data-action="confirm-edit-reminder-plan">保存计划</button></div></section></div>`; }
  if (state.dialog?.kind === 'memory-summary-about') return `<div class="scrim"><section class="dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><h2 id="dialog-title">关于记忆</h2><p>这里显示已保存在本机的记忆摘要。启用后，相关内容可用于回答。</p><div class="dialog-actions"><button class="primary" data-action="close-dialog">知道了</button></div></section></div>`;
  if (state.dialog?.kind === 'memory-audit') {
    const draft = state.dialog;
    const audit = draft.audit;
    const actionLabel = action => ({ create: '创建', update: '修订', softDelete: '删除', restore: '恢复', undo: '撤销操作', redo: '重做操作', replaceMemorySummary: '全文修订', deleteMemorySummary: '摘要删除', saveReviewedMemory: '确认加入', resolvePendingMemoryTopic: '冲突处理' })[action] || '本机变更';
    const statusLabel = status => ({ ACTIVE: '使用中', PAUSED: '已暂停', DELETED: '已删除', PENDING_REVIEW: '待审阅' })[status] || '状态未知';
    const localTime = value => { const date = new Date(value); return Number.isNaN(date.getTime()) ? '时间未记录' : date.toLocaleString('zh-CN', { hour12: false }); };
    return `<div class="scrim"><section class="dialog" role="dialog" aria-modal="true" aria-labelledby="memory-audit-title"><h2 id="memory-audit-title">来源与修订 · ${escape(draft.title)}</h2>${draft.loading ? '<p role="status">正在读取本机记录…</p>' : draft.failure ? `<p class="dialog-error" role="alert">${escape(draft.failure)}</p>` : `<p>当前版本 ${Number(audit.currentRevision)} · ${audit.importedRevision != null ? '曾从历史资料导入' : audit.source === 'LOCAL_EDIT' ? '本机创建或编辑' : '来源未记录'}</p>${audit.olderRevisionsOmitted ? '<p>仅显示最近 100 次变更；更早记录未在此页展示。</p>' : ''}<ol style="max-height:60vh;overflow:auto">${audit.revisions.map(item => `<li><strong>版本 ${Number(item.revision)} · ${actionLabel(item.action)} · ${escape(localTime(item.changedAt))}${item.undone ? ' · 已撤销' : ''}</strong><p>${escape(item.title)} · ${statusLabel(item.status)}</p><p>${escape(item.body)}</p></li>`).join('')}</ol>${audit.revisions.length ? '' : '<p>当前记录可用；此前修订记录不可回读。</p>'}` }<div class="dialog-actions"><button class="primary" data-action="close-dialog">关闭</button></div></section></div>`;
  }
  if (state.dialog?.kind === 'memory-summary-suggestion') return `<div class="scrim"><section class="dialog" role="dialog" aria-modal="true" aria-labelledby="memory-suggestion-title" aria-busy="${Boolean(state.dialog.submitting)}"><h2 id="memory-suggestion-title">加入记忆摘要？</h2><p>仅将下列内容保存在本机。后续普通聊天开启记忆时，相关内容可能随本次请求发送给所选模型服务。</p><p><strong>${escape(state.dialog.title)}</strong></p><p>${escape(state.dialog.body)}</p>${state.dialog.failure ? `<p class="dialog-error" role="alert">${escape(state.dialog.failure)}</p>` : ''}<div class="dialog-actions"><button data-action="close-dialog" ${state.dialog.submitting ? 'disabled' : ''}>取消</button><button class="primary" data-action="confirm-memory-summary-suggestion" ${state.dialog.submitting ? 'disabled' : ''}>${state.dialog.submitting ? '正在保存…' : '确认加入'}</button></div></section></div>`;
  if (state.dialog?.kind === 'memory-topic-conflict') { const draft = state.dialog; return `<div class="scrim"><section class="dialog" role="dialog" aria-modal="true" aria-labelledby="memory-topic-conflict-title" aria-busy="${Boolean(draft.submitting)}"><h2 id="memory-topic-conflict-title">已有同主题记忆</h2><p>主题：${escape(draft.title)}。现有 ${draft.expectedEntries.length} 条，本次内容与现有内容不同。</p><p><strong>现有内容</strong><br>${escape(draft.existing.body)}</p><p><strong>本次内容</strong><br>${escape(draft.body)}</p>${draft.failure ? `<p class="dialog-error" role="alert">${escape(draft.failure)}</p>` : ''}<div class="dialog-actions"><button data-action="close-dialog" ${draft.submitting ? 'disabled' : ''}>取消</button><button data-action="keep-memory-topic" ${draft.submitting ? 'disabled' : ''}>保留现有</button><button data-action="parallel-memory-topic" ${draft.submitting ? 'disabled' : ''}>并存</button><button class="primary" data-action="revise-memory-topic" ${draft.submitting ? 'disabled' : ''}>修订显示的这条</button></div></section></div>`; }
  if (state.dialog?.kind === 'pending-memory-topic') { const draft = state.dialog; return `<div class="scrim"><section class="dialog" role="dialog" aria-modal="true" aria-labelledby="pending-memory-topic-title" aria-busy="${Boolean(draft.submitting)}"><h2 id="pending-memory-topic-title">处理待审阅记忆</h2><p>主题：${escape(draft.candidate.title)}。候选内容尚未用于后续回答。</p><p><strong>现有内容</strong><br>${escape(draft.existing?.body || '已无现有内容')}</p><p><strong>候选内容</strong><br>${escape(draft.candidate.body)}</p>${draft.failure ? `<p class="dialog-error" role="alert">${escape(draft.failure)}</p>` : ''}<div class="dialog-actions"><button data-action="close-dialog" ${draft.submitting ? 'disabled' : ''}>稍后处理</button><button data-action="resolve-pending-memory-topic" data-resolution="KEEP_EXISTING" ${draft.submitting ? 'disabled' : ''}>保留现有</button><button data-action="resolve-pending-memory-topic" data-resolution="CREATE_PARALLEL" ${draft.submitting ? 'disabled' : ''}>并存</button>${draft.existing ? `<button class="primary" data-action="resolve-pending-memory-topic" data-resolution="REVISE_EXISTING" ${draft.submitting ? 'disabled' : ''}>修订显示的这条</button>` : ''}</div></section></div>`; }
if (state.dialog?.kind === 'memory-summary-editor') return `<div class="scrim custom-instructions-scrim"><section class="dialog memory-summary-editor" role="dialog" aria-modal="true" aria-labelledby="memory-summary-editor-title"><header><button data-action="close-dialog">取消</button><h2 id="memory-summary-editor-title">编辑记忆摘要</h2><button class="primary" data-action="save-memory-summary-editor" ${String(state.dialog.value || '').trim() && !(state.memorySummarySavePending && state.memorySummarySavePending.workspaceId === state.current?.summary?.id) ? '' : 'disabled'}>保存</button></header>${renderDomainConflict(state.dialog, escape)}<label><span>完整摘要</span><textarea id="memory-summary-editor" maxlength="2000000" placeholder="输入或粘贴新的记忆摘要">${escape(state.dialog.value || '')}</textarea><small>${String(state.dialog.value || '').length} / 2000000 字</small></label></section></div>`;
  if (state.dialog?.kind === 'memory-summary-delete') return `<div class="scrim"><section class="dialog" role="alertdialog" aria-modal="true"><h2>删除记忆？</h2><p>删除当前记忆摘要；聊天、文件和记忆开关不受影响。</p><div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary danger" data-action="confirm-delete-memory-summary">删除</button></div></section></div>`;
  if (state.dialog?.kind === 'memory-summary-disable') return `<div class="scrim"><section class="dialog" role="alertdialog" aria-modal="true"><h2>关闭记忆摘要生成和应用？</h2><p>停止生成和使用记忆摘要；已保存内容不会删除。</p><div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary danger" data-action="confirm-disable-memory-summary">关闭</button></div></section></div>`;
  if (state.dialog?.kind === 'model-settings-feedback') return `<div class="scrim"><section class="dialog" role="alertdialog" aria-modal="true" aria-labelledby="model-feedback-title"><h2 id="model-feedback-title">${state.dialog.failed ? '操作未完成' : '操作完成'}</h2><p>${escape(state.dialog.message)}</p><div class="dialog-actions"><button class="primary" data-action="close-dialog">知道了</button></div></section></div>`;
  if (state.dialog?.kind === 'custom-instructions-fullscreen') return `<div class="scrim custom-instructions-scrim"><section class="dialog custom-instructions-fullscreen" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><header><button class="icon-button" data-action="close-dialog" aria-label="关闭全屏自定义指令编辑">${icon(icons.close, '关闭')}</button><h2 id="dialog-title">自定义指令</h2><button class="icon-button" data-action="save-custom-instructions-fullscreen" aria-label="保存自定义指令" title="保存">${icon(icons.check, '保存')}</button></header><label class="android-settings-field"><span>自定义指令</span><textarea id="personalization-instructions-fullscreen" maxlength="${CUSTOM_INSTRUCTIONS_MAX_LENGTH}" placeholder="希望南枫 AI 如何回答你">${escape(state.personalizationDraft.customInstructions || '')}</textarea><small>${String(state.personalizationDraft.customInstructions || '').length} / ${CUSTOM_INSTRUCTIONS_MAX_LENGTH} 字</small></label></section></div>`;
  if (state.dialog?.kind === 'reminder-delete') { const item = state.dialog.item; return `<div class="scrim"><section class="dialog" role="alertdialog" aria-modal="true"><h2>删除这个提醒计划？</h2><p>将删除“${escape(item.title)}”及其本机执行记录；操作不可撤销，运行中的请求会请求停止。</p><div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary danger" data-action="confirm-delete-reminder-plan" data-id="${escape(item.planId)}">删除计划</button></div></section></div>`; }
  if (state.dialog.kind === 'memory') { const item = state.dialog.item; const draft = state.dialog.draft || {}; return `<div class="scrim"><section class="dialog edit-dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="取消">${icon(icons.close, '关闭')}</button><p class="overline">本地 revision 写入</p><h2 id="dialog-title">${item ? '编辑 Memory' : '新建 Memory'}</h2><p>保存只写入本机；普通聊天开启记忆时，会按当前问题选取相关内容。点击发送后，选中的内容才会交给本次显示的模型服务。</p>${workItemScopeField(item, 'memory-project', 'scopeId', draft['memory-project'] || '')}<label>主题<input id="memory-title" maxlength="120" value="${escape(draft['memory-title'] ?? item?.title ?? '概览')}"></label><label>正文<textarea id="memory-body" maxlength="2000000">${escape(draft['memory-body'] ?? item?.body ?? '')}</textarea></label>${renderDomainConflict(state.dialog, escape)}<div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary" data-action="save-memory">保存 ${saveShortcut}</button></div></section></div>`; }
  if (state.dialog.kind === 'relation') { const data = workspace(); const items = active(data, 'knowledge'); const draft = state.dialog.draft || {}; const options = field => items.map(item => `<option value="${escape(item.id)}" ${draft[field] === item.id ? 'selected' : ''}>${escape(item.title)} · r${item.revision}</option>`).join(''); return `<div class="scrim"><section class="dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="取消">${icon(icons.close, '关闭')}</button><p class="overline">显式本地 relation</p><h2 id="dialog-title">建立 Knowledge 关系</h2><p>仅同一活动 scope 的两条 Knowledge 可建立；不会自动关联或去重。</p><label>来源<select id="relation-from">${options('relation-from')}</select></label><label>目标<select id="relation-to">${options('relation-to')}</select></label><label>类型<select id="relation-kind"><option value="RELATED" ${draft['relation-kind'] === 'RELATED' ? 'selected' : ''}>RELATED（对称）</option><option value="DERIVED_FROM" ${draft['relation-kind'] === 'DERIVED_FROM' ? 'selected' : ''}>DERIVED_FROM</option><option value="REFERENCES" ${draft['relation-kind'] === 'REFERENCES' ? 'selected' : ''}>REFERENCES</option></select></label><div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary" data-action="save-relation" ${items.length < 2 ? 'disabled' : ''}>建立关系</button></div></section></div>`; }
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
  if (state.dialog?.kind === 'privacy-cleanup-scope') return renderLocalDataCleanupScopeDialog(state);
  if (state.dialog?.kind === 'privacy-cleanup-preview') return renderLocalDataCleanupPreviewDialog(state.dialog, escape);
  return '';
}
function tree(data) { if (!data) return '<div class="tree-empty">还没有本地工作区。<br>先从受控交换包导入。</div>'; const workspaceRows = state.workspaces.map(item => `<button class="tree-row ${item.id === data.summary.id ? 'selected' : ''}" data-action="select-workspace" data-id="${escape(item.id)}"><span>${icon(icons.workspace, '工作区')}</span><span>${escape(item.title)}</span><small>${item.id === data.summary.id ? '当前' : ''}</small></button>`).join(''); return `<div class="tree-section"><p>Workspace</p>${workspaceRows}</div><div class="tree-section"><p>Project <button class="small-add" data-action="new-project" aria-label="新建 Project">+</button></p>${data.exchange.projects.map(item => `<button class="tree-row" data-action="edit-project" data-id="${escape(item.id)}"><span>${icon(icons.workspace, '项目')}</span><span>${escape(item.title)}</span><small>r${item.revision || 0}</small></button>`).join('') || '<span class="tree-muted">暂无 Project</span>'}</div><div class="tree-section"><p>Conversation</p>${data.exchange.conversations.map(item => `<button class="tree-row ${state.pane === 'conversation' ? 'selected' : ''}" data-action="show-conversation"><span>${icon(icons.conversation, '会话')}</span><span>${escape(item.title)}</span><small>${item.messages.length}</small></button>`).join('')}</div><div class="tree-section"><p>Knowledge</p><button class="tree-row ${state.pane === 'knowledge' ? 'selected' : ''}" data-action="show-knowledge"><span>${icon(icons.knowledge, '知识')}</span><span>知识与记忆</span><small>${data.exchange.knowledge.length}</small></button></div>`; }
function conversationCanvas(data) { const conversation = data.exchange.conversations[0]; if (!conversation) return `<section class="canvas empty-canvas"><h2>暂无会话</h2><p>新建 Conversation 会在 Rust transaction 中同时建立明确的本地 root 节点。</p></section>`; return `<section class="canvas conversation-canvas"><div class="canvas-header"><div><p class="overline">Conversation · 本地树</p><h1>${escape(conversation.title)}</h1><p>追加节点只接受明确本地文本，不构造 Prompt 或 RunSpec。</p></div><button class="subtle" data-action="show-knowledge">知识工作台</button></div><div class="message-list">${orderedConversationMessages(conversation).map(message => `<article class="message ${escape(message.role)}"><header><span>${escape(message.role)}</span><time>${escape(short(message.createdAt))}</time></header>${message.blocks.map(block => `<pre>${escape(block.text || `附件引用 · ${block.asset?.displayName || ''}`)}</pre>`).join('')}</article>`).join('')}</div></section>`; }
function knowledgeCanvas(data) { const knowledge = active(data, 'knowledge'); const memory = active(data, 'memory'); const relationItems = active(data, 'relations'); const name = id => data.exchange.knowledge.find(item => item.id === id)?.title || short(id); return `<section class="canvas knowledge-canvas"><div class="canvas-header"><div><p class="overline">Knowledge / Memory · 文本 IR</p><h1>知识与记忆</h1><p>保存、取消、冲突、撤销与软删除均由 Rust SQLite 真值驱动。</p></div><div class="canvas-actions"><button class="primary" data-action="new-knowledge">${icon(icons.plus, '新建')}新建 Knowledge</button><button data-action="new-memory">${icon(icons.plus, '新建')}新建 Memory</button><button data-action="new-relation" ${knowledge.length < 2 ? 'disabled' : ''}>${icon(icons.link, '关系')}建立关系</button></div></div><div class="knowledge-list">${knowledge.map(item => `<article class="knowledge-item"><header><div><p class="overline">${escape(item.status)} · r${item.revision} · ${escape(item.classification)}</p><h2>${escape(item.title)}</h2></div><span>${escape((item.tags || []).join(' · '))}</span></header><pre>${escape(item.body)}</pre><div class="item-actions"><button data-action="edit-knowledge" data-id="${escape(item.id)}">${icon(icons.edit, '编辑')}编辑</button><button data-action="delete-knowledge" data-id="${escape(item.id)}" data-revision="${item.revision}">${icon(icons.trash, '软删除')}软删除</button></div></article>`).join('') || '<p class="empty-copy">尚无已保存的本地知识。</p>'}</div><section class="memory-rail"><div class="section-head"><h2>Memory</h2><button data-action="new-memory">新建</button></div><ul>${memory.map(item => `<li><span>${escape(item.title || '概览')} · ${escape(item.scope)} · r${item.revision}</span><button class="memory-button" data-action="edit-memory" data-id="${escape(item.id)}">${escape(item.body)}</button><button data-action="delete-memory" data-id="${escape(item.id)}" data-revision="${item.revision}">软删除</button></li>`).join('') || '<li>暂无活动 Memory。</li>'}</ul></section><section class="memory-rail"><div class="section-head"><h2>Knowledge relation</h2><button data-action="new-relation" ${knowledge.length < 2 ? 'disabled' : ''}>建立</button></div><ul>${relationItems.map(item => `<li><span>${escape(item.kind)} · r${item.revision}</span>${escape(name(item.fromId))} → ${escape(name(item.toId))}<button data-action="delete-relation" data-id="${escape(item.id)}" data-revision="${item.revision}">撤销</button></li>`).join('') || '<li>暂无活动 relation。</li>'}</ul></section></section>`; }
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
        : result.kind === 'attention' ? (reading ? '读取需要核对' : '同步需要核对')
        : reading ? '读取失败' : '同步失败'
    : reading ? '正在读取云端列表' : '正在同步到南枫云';
  const detail = result?.message || (result ? '' : reading ? '正在核对并合并云端会话，请稍候。' : '正在加密提交并核对云端回执，请稍候。');
  const resultIcon = result
    ? icon(result.kind === 'success' ? icons.check : icons.info, result.kind === 'success' ? '成功' : '提示')
    : '<span class="account-sync-progress-spinner" aria-hidden="true"></span>';
  return `<div class="scrim account-sync-progress-scrim"><section class="dialog account-sync-progress-dialog ${result ? `result ${escape(result.kind)}` : ''}" role="status" aria-live="polite" aria-busy="${result ? 'false' : 'true'}">${resultIcon}<h2>${title}</h2>${detail ? `<p>${escape(detail)}</p>` : ''}</section></div>`;
}
async function refresh() {
  const runtimeRefresh = arguments[0]?.runtimeRefresh === true;
  const observeSelected = arguments[0]?.observeSelected ?? !state.runtimeInfo?.automaticWorkSuppressed;
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
    if (!c13ConversationLifecyclePreview) reloadFavoriteConversationIds({ runtimeRefresh });
    await loadConversationReadState({ observeSelected: true });
    recomputeConversationFind();
    render({ runtimeRefresh });
    return;
  }
  state.runtimeInfo = await invoke('read_desktop_runtime_info');
  void loadMarkdownKnowledgeTask().catch(() => {});
  void loadJsonKnowledgeTask().catch(() => {});
  void pdfKnowledge.load().catch(() => {});
  void webKnowledge.load().catch(() => {});
  state.workspaces = await invoke('list_desktop_workspaces');
  state.current = state.workspaces.length ? await invoke('read_desktop_workspace', { workspaceId: state.current?.summary?.id || state.workspaces[0].id }) : null;
  if (state.current && !resolveConversation(state.current, state.selectedConversationId)
      && state.newConversationDraftWorkspaceId !== state.current.summary.id) selectWorkspaceDefaultConversation(state.current);
  reloadFavoriteConversationIds({ runtimeRefresh });
  const selectionWorkspaceId = state.current?.summary?.id || null;
  const selectionConversationId = state.selectedConversationId || null;
  // These are independent read projections after the workspace and selected
  // conversation are known. Waiting for each IPC receipt serially delayed the
  // first usable Desktop frame even though no mutation depends on another.
  const [
    ,
    p6gCatalog,
    openrouterRegistryStatus,
    p6gGlobalDefault,
    p6gSelection,
    history,
    agentRuns,
    p6eAcceptance,
    transcription,
    p6kTask,
    chatgptTask,
    claudeTask,
    knowledgeTask,
  ] = await Promise.all([
    loadConversationReadState({ observeSelected }),
    invoke('read_desktop_p6g_catalog'),
    invoke('read_desktop_openrouter_registry_status'),
    invoke('read_desktop_p6g_global_default'),
    selectionWorkspaceId && selectionConversationId ? invoke('read_desktop_p6g_selection', { workspaceId: selectionWorkspaceId, conversationId: selectionConversationId }) : Promise.resolve(null),
    state.current ? invoke('read_desktop_workbench_history', { workspaceId: state.current.summary.id }) : Promise.resolve({ canUndo: false, canRedo: false, recycleBin: [], modelMetadata: [] }),
    workspaceUiEnabled(state.runtimeInfo) ? invoke('inspect_p8_agent_runs') : Promise.resolve([]),
    invoke('read_p6e_temporary_maintenance_acceptance_status'),
    loadTranscriptionState(),
    state.current ? invoke('read_latest_p6k_zip_import_task', { workspaceId: state.current.summary.id }) : Promise.resolve(null),
    state.current ? invoke('read_latest_chatgpt_import_task', { workspaceId: state.current.summary.id }) : Promise.resolve(null),
    state.current ? invoke('read_latest_claude_import_task', { workspaceId: state.current.summary.id }) : Promise.resolve(null),
    state.current ? invoke('read_latest_nanfeng_knowledge_import_task', { workspaceId: state.current.summary.id }) : Promise.resolve(null),
  ]);
  recomputeConversationFind();
  state.p6gCatalog = p6gCatalog;
  state.openrouterRegistryStatus = openrouterRegistryStatus;
  state.p6gGlobalDefault = p6gGlobalDefault;
  if (state.current?.summary?.id === selectionWorkspaceId
      && (state.selectedConversationId || null) === selectionConversationId) {
    state.p6gSelection = p6gSelection;
    // Runtime refreshes from a prior reply must not erase choices already made
    // for a new conversation. Route transitions and draft reads own that state.
    if (selectionConversationId) {
      state.conversationPreferences = p6gSelection?.conversationOverride
        || { revision: 0, toneOverride: null, webSearchOverride: null };
    } else if (!state.conversationPreferences) {
      state.conversationPreferences = { revision: 0, toneOverride: null, webSearchOverride: null };
    }
  }
  state.history = history;
  state.agentRuns = agentRuns;
  state.p6eAcceptance = p6eAcceptance;
  state.transcription = transcription;
  state.p6kTask = p6kTask?.workspaceId === state.current?.summary?.id ? p6kTask : null;
  state.chatgptTask = chatgptTask?.workspaceId === state.current?.summary?.id ? chatgptTask : null;
  state.claudeTask = claudeTask?.workspaceId === state.current?.summary?.id ? claudeTask : null;
  state.nanfengKnowledgeTask = knowledgeTask?.workspaceId === state.current?.summary?.id ? knowledgeTask : null;
  if (state.p6eAcceptance.enabled && state.pane === 'chat') state.pane = 'connections';
  render({ runtimeRefresh });
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
function importRouteIsCurrent(owner, workspaceId) {
  return state === owner && state.current?.summary?.id === workspaceId;
}
async function commitSelectedImport({ taskKey, stageCommand, readCommand, confirmCommand, selectedPath, label, owner = state, workspaceId = owner.current?.summary?.id }) {
  if (!workspaceId) throw new Error('请先导入或创建一个本地工作区。');
  if (!importRouteIsCurrent(owner, workspaceId)) return;
  const staged = await invoke(stageCommand, { selectedPath, workspaceId });
  if (!importRouteIsCurrent(owner, workspaceId)) return;
  let task = await invoke(readCommand, { taskId: staged.id });
  let rejected = 0;
  for (const item of task.items.filter(candidate => candidate.status === 'PENDING_CONFIRMATION' && candidate.failure !== 'CONFLICT_REIMPORT')) {
    if (!importRouteIsCurrent(owner, workspaceId)) break;
    try {
      await invoke(confirmCommand, { args: { workspaceId, taskId: task.id, itemId: item.id } });
    } catch {
      // The item stays independently recoverable in its persisted task; selection remains one command.
      rejected += 1;
    }
  }
  if (!importRouteIsCurrent(owner, workspaceId)) return;
  task = await invoke(readCommand, { taskId: staged.id });
  if (!importRouteIsCurrent(owner, workspaceId)) return;
  owner[taskKey] = task;
  owner.dialog = null;
  await refresh();
  if (!importRouteIsCurrent(owner, workspaceId)) return task;
  const conflicts = task.items.filter(item => item.status === 'PENDING_CONFIRMATION' && item.failure === 'CONFLICT_REIMPORT').length;
  owner.status = `${label} 已直接执行导入；失败或拒绝 ${rejected} 项${conflicts ? `，同源冲突 ${conflicts} 项待您在导入结果中确认` : ''}。`;
  owner.error = '';
  return task;
}
async function commitPendingImport({ taskKey, readCommand, confirmCommand, label, owner = state, workspaceId = owner.current?.summary?.id }) {
  if (!workspaceId || !owner[taskKey]) throw new Error('需要一个本地工作区和可恢复的导入任务。');
  if (!importRouteIsCurrent(owner, workspaceId)) return;
  let task = await invoke(readCommand, { taskId: owner[taskKey].id });
  let rejected = 0;
  for (const item of task.items.filter(candidate => candidate.status === 'PENDING_CONFIRMATION' && candidate.failure !== 'CONFLICT_REIMPORT')) {
    if (!importRouteIsCurrent(owner, workspaceId)) break;
    try { await invoke(confirmCommand, { args: { workspaceId, taskId: task.id, itemId: item.id } }); } catch { rejected += 1; }
  }
  if (!importRouteIsCurrent(owner, workspaceId)) return;
  task = await invoke(readCommand, { taskId: task.id });
  if (!importRouteIsCurrent(owner, workspaceId)) return;
  owner[taskKey] = task;
  await refresh();
  if (!importRouteIsCurrent(owner, workspaceId)) return;
  owner.status = `${label} 已直接续作；失败或拒绝 ${rejected} 项保留独立回执。`;
  owner.error = '';
}
async function pickChatGptExport() {
  if (!native) { state.status = 'Web 预览不会请求文件；请在 Tauri Desktop 开发包中使用系统 picker。'; render(); return; }
  const owner = state;
  const workspaceId = owner.current?.summary?.id;
  p6hImportLifecycleMarker('picker-open');
  const selectedPath = await dialogInvoke('open', { multiple: false, directory: false, filters: [{ name: 'ChatGPT conversations.json', extensions: ['json'] }] });
  if (!selectedPath) { p6hImportLifecycleMarker('picker-cancel'); return; }
  if (!importRouteIsCurrent(owner, workspaceId)) return;
  p6hImportLifecycleMarker('picker-returned');
  try { const task = await commitSelectedImport({ taskKey: 'chatgptTask', stageCommand: 'stage_chatgpt_export_selected', readCommand: 'read_chatgpt_import_task', confirmCommand: 'confirm_chatgpt_import_item', selectedPath, label: 'ChatGPT', owner, workspaceId }); if (task) p6hImportLifecycleMarker('direct-commit-succeeded'); }
  catch (error) { p6hImportLifecycleMarker('stage-error'); if (state === owner) { owner.error = `ChatGPT 导入被拒绝：${String(error)}`; owner.status = owner.error; } }
  if (state === owner) render();
}
async function pickClaudeExport() {
  if (!native) { state.status = 'Web 预览不会请求文件；请在 Tauri Desktop 开发包中使用系统 picker。'; render(); return; }
  const owner = state;
  const workspaceId = owner.current?.summary?.id;
  const selectedPath = await dialogInvoke('open', { multiple: false, directory: false, filters: [{ name: 'Claude conversations.json', extensions: ['json'] }] });
  if (!selectedPath || !importRouteIsCurrent(owner, workspaceId)) return;
  try { await commitSelectedImport({ taskKey: 'claudeTask', stageCommand: 'stage_claude_export_selected', readCommand: 'read_claude_import_task', confirmCommand: 'confirm_claude_import_item', selectedPath, label: 'Claude', owner, workspaceId }); }
  catch (error) { if (state === owner) { owner.error = `Claude 导入被拒绝：${String(error)}`; owner.status = owner.error; } }
  if (state === owner) render();
}
async function pickP6kZip(provider) {
  if (!native) { state.status = 'Web 预览不会请求文件；请在 Tauri Desktop 开发包中使用系统 picker。'; render(); return; }
  const owner = state;
  const workspaceId = owner.current?.summary?.id;
  if (!workspaceId) { owner.error = '先导入或创建一个本地工作区。'; render(); return; }
  const selectedPath = await dialogInvoke('open', { multiple: false, directory: false, filters: [{ name: `${provider} data export ZIP`, extensions: ['zip'] }] });
  if (!selectedPath || !importRouteIsCurrent(owner, workspaceId)) return;
  try {
    const task = await invoke('stage_p6k_zip_import_selected', { args: { workspaceId, provider, selectedPath } });
    if (!importRouteIsCurrent(owner, workspaceId)) return;
    owner.p6kTask = task;
    owner.status = `${provider} ZIP 已完成私有校验并直接导入：${task.importedCount} 个会话；失败 ${task.failedCount}，未关联媒体 ${task.unmappedAssetCount}。`;
    owner.error = '';
    await refresh();
  } catch (error) { if (state === owner) { owner.error = `ZIP 导入被拒绝：${String(error)}`; owner.status = owner.error; } }
  if (state === owner) render();
}
async function pickV2WorkspaceExchange() {
  if (!native) { state.status = 'Web 预览不会请求文件；请在 Tauri Desktop 开发包中使用系统 picker。'; render(); return; }
  const owner = state;
  const selectedPath = await dialogInvoke('open', { multiple: false, directory: false, filters: [{ name: '南枫 AI 完整工作区交换（v2）', extensions: ['nfai-exchange', 'zip'] }] });
  if (!selectedPath || state !== owner) return;
  try {
    const receipt = await invoke('import_desktop_workspace_exchange_v2_selected', { selectedPath }, owner);
    if (state !== owner) return;
    await refresh();
    if (state !== owner) return;
    const committed = await invoke('list_desktop_workspace_exchange_v2_committed', {}, owner);
    if (state !== owner) return;
    owner.v2CommittedExchanges = committed;
    globalThis.__nanfengV2CommittedExchanges = committed;
    owner.status = `完整工作区交换（v2）已私有导入并回读：${short(receipt.semanticHash)} · ${receipt.assetCount} 项附件${receipt.replayed ? ' · 已验证重放回执' : ''}。`;
    owner.error = '';
  } catch (error) {
    if (state !== owner) return;
    owner.error = `完整工作区交换（v2）被拒绝：${String(error)}`;
    owner.status = owner.error;
  }
  if (state === owner) render();
}
async function reexportV2WorkspaceExchange(workspaceId) {
  if (!native) { state.status = 'Web 预览不会请求保存位置；请在 Tauri Desktop 开发包中使用系统保存窗口。'; render(); return; }
  const owner = state;
  const selectedPath = await dialogInvoke('save', { defaultPath: 'nanfeng-ai-workspace-v2.nfai-exchange', filters: [{ name: '南枫 AI 完整工作区交换（v2）', extensions: ['nfai-exchange'] }] });
  if (!selectedPath || state !== owner) return;
  try {
    const receipt = await invoke('reexport_desktop_workspace_exchange_v2_selected', { workspaceId, selectedPath }, owner);
    if (state !== owner) return;
    owner.status = `完整工作区交换（v2）已从私有记录回导并严格回读：${short(receipt.semanticHash)} · ${receipt.assetCount} 项附件。`;
    owner.error = '';
  } catch (error) {
    if (state !== owner) return;
    owner.error = `完整工作区交换（v2）回导被拒绝：${String(error)}`;
    owner.status = owner.error;
  }
  if (state === owner) render();
}
async function pickNanfengKnowledgeExport() {
  if (!native) { state.status = 'Web 预览不会请求文件；请在 Tauri Desktop 开发包中使用系统 picker。'; render(); return; }
  const owner = state;
  const workspaceId = owner.current?.summary?.id;
  const selectedPath = await dialogInvoke('open', { multiple: false, directory: false, filters: [{ name: '南枫知识库完整 JSON', extensions: ['json'] }] });
  if (!selectedPath || !importRouteIsCurrent(owner, workspaceId)) return;
  try { await commitSelectedImport({ taskKey: 'nanfengKnowledgeTask', stageCommand: 'stage_nanfeng_knowledge_export_selected', readCommand: 'read_nanfeng_knowledge_import_task', confirmCommand: 'confirm_nanfeng_knowledge_import_item', selectedPath, label: '南枫知识库', owner, workspaceId }); }
  catch (error) { if (state === owner) { owner.error = `知识库导入被拒绝：${String(error)}`; owner.status = owner.error; } }
  if (state === owner) render();
}
app.addEventListener('click', event => { const target = event.target.closest?.('[data-action="select-chatgpt-export"]'); if (!target) return; event.preventDefault(); event.stopImmediatePropagation(); pickChatGptExport(); }, true);
app.addEventListener('click', event => { const target = event.target.closest?.('[data-action="select-claude-export"]'); if (!target) return; event.preventDefault(); event.stopImmediatePropagation(); pickClaudeExport(); }, true);
app.addEventListener('click', event => { const target = event.target.closest?.('[data-action="select-p6k-chatgpt-zip"],[data-action="select-p6k-claude-zip"]'); if (!target) return; event.preventDefault(); event.stopImmediatePropagation(); pickP6kZip(target.dataset.action === 'select-p6k-chatgpt-zip' ? 'CHATGPT' : 'CLAUDE'); }, true);
app.addEventListener('click', event => { const target = event.target.closest?.('[data-action="select-v2-workspace-exchange"]'); if (!target) return; event.preventDefault(); event.stopImmediatePropagation(); pickV2WorkspaceExchange(); }, true);
app.addEventListener('click', event => { const target = event.target.closest?.('[data-action="reexport-v2-workspace-exchange"]'); if (!target) return; event.preventDefault(); event.stopImmediatePropagation(); reexportV2WorkspaceExchange(target.dataset.workspaceId); }, true);
app.addEventListener('click', async event => {
  const target = event.target.closest?.('[data-action="retry-p6k-zip"],[data-action="skip-p6k-zip-failures"],[data-action="delete-p6k-zip-batch"],[data-action="confirm-delete-p6k-zip-batch"]');
  const owner = state;
  const workspaceId = owner.current?.summary?.id;
  const task = owner.p6kTask;
  if (!target || !task || task.workspaceId !== workspaceId) return;
  event.preventDefault();
  event.stopImmediatePropagation();
  const action = target.dataset.action;
  if (action === 'delete-p6k-zip-batch') {
    owner.dialog = { kind: 'p6k-batch-delete', taskId: task.id, submitting: false, failure: '' };
    render();
    return;
  }
  if (action === 'confirm-delete-p6k-zip-batch' && (owner.dialog?.kind !== 'p6k-batch-delete' || owner.dialog.taskId !== task.id || owner.dialog.submitting)) return;
  try {
    if (action === 'retry-p6k-zip' || action === 'skip-p6k-zip-failures') {
      const command = action === 'retry-p6k-zip' ? 'retry_p6k_zip_import_task' : 'skip_p6k_zip_import_failures';
      const updated = await invoke(command, { taskId: task.id });
      if (!importRouteIsCurrent(owner, workspaceId)) return;
      owner.p6kTask = updated;
    } else {
      owner.dialog = { ...owner.dialog, submitting: true, failure: '' };
      render();
      await invoke('delete_p6k_zip_import_batch', { taskId: task.id });
      if (!importRouteIsCurrent(owner, workspaceId)) return;
      owner.p6kTask = null;
      owner.dialog = null;
    }
    owner.status = action === 'confirm-delete-p6k-zip-batch' ? '已删除该导入批次与私有副本；官方会话删除标记已保留，不会在后续导入中复活。' : 'ZIP 导入状态已更新。';
    owner.error = '';
    await refresh();
  } catch (error) {
    if (!importRouteIsCurrent(owner, workspaceId)) return;
    if (action === 'confirm-delete-p6k-zip-batch' && owner.dialog?.kind === 'p6k-batch-delete') owner.dialog = { ...owner.dialog, submitting: false, failure: String(error) };
    else owner.error = `ZIP 导入状态未更新：${String(error)}`;
  }
  if (importRouteIsCurrent(owner, workspaceId)) render();
}, true);
app.addEventListener('click', async event => {
  const target = event.target.closest?.('[data-action="select-p6k-manual-asset"],[data-action="select-p6k-manual-target"],[data-action="link-p6k-manual-asset"]');
  const owner = state;
  const workspaceId = owner.current?.summary?.id;
  const task = owner.p6kTask;
  if (!target || !task || task.workspaceId !== workspaceId) return;
  event.preventDefault();
  event.stopImmediatePropagation();
  if (target.dataset.action === 'select-p6k-manual-asset') {
    p6kManualLink = { taskId: task.id, assetOrdinal: Number(target.dataset.assetOrdinal), conversationId: null, messageId: null };
    globalThis.__nanfengP6kManualLink = p6kManualLink;
    render();
    return;
  }
  if (target.dataset.action === 'select-p6k-manual-target') {
    if (p6kManualLink.taskId !== task.id || p6kManualLink.assetOrdinal === null) return;
    p6kManualLink = { ...p6kManualLink, conversationId: target.dataset.conversationId, messageId: target.dataset.messageId || null };
    globalThis.__nanfengP6kManualLink = p6kManualLink;
    render();
    return;
  }
  if (p6kManualLink.taskId !== task.id || p6kManualLink.assetOrdinal === null || !p6kManualLink.conversationId || !p6kManualLink.messageId) return;
  const selectedLink = { ...p6kManualLink };
  try {
    const updated = await invoke('link_p6k_zip_manual_asset', { args: { taskId: task.id, assetOrdinal: selectedLink.assetOrdinal, workspaceId, conversationId: selectedLink.conversationId, messageId: selectedLink.messageId } });
    if (!importRouteIsCurrent(owner, workspaceId)) return;
    owner.p6kTask = updated;
    p6kManualLink = { taskId: null, assetOrdinal: null, conversationId: null, messageId: null };
    globalThis.__nanfengP6kManualLink = p6kManualLink;
    owner.status = '已按所选媒体与消息建立精确关联；附件现在只在该消息位置呈现。';
    owner.error = '';
    await refresh();
  } catch (error) { if (importRouteIsCurrent(owner, workspaceId)) owner.error = `媒体关联未完成：${String(error)}`; }
  if (importRouteIsCurrent(owner, workspaceId)) render();
}, true);
app.addEventListener('click', event => { const target = event.target.closest?.('[data-action="select-nanfeng-knowledge-export"]'); if (!target) return; event.preventDefault(); event.stopImmediatePropagation(); pickNanfengKnowledgeExport(); }, true);
const jsonConflictActions = Object.freeze({
  chatgpt: { taskKey: 'chatgptTask', confirm: 'confirm_chatgpt_conflict_reimport_item', skip: 'skip_chatgpt_import_item', label: 'ChatGPT' },
  claude: { taskKey: 'claudeTask', confirm: 'confirm_claude_conflict_reimport_item', skip: 'skip_claude_import_item', label: 'Claude' },
  'nanfeng-knowledge': { taskKey: 'nanfengKnowledgeTask', confirm: 'confirm_nanfeng_knowledge_conflict_reimport_item', skip: 'skip_nanfeng_knowledge_import_item', label: '南枫知识库' },
});
app.addEventListener('click', async event => {
  const target = event.target.closest?.('[data-action$="-conflict"]');
  if (!target || !native) return;
  const match = /^(confirm|skip)-(chatgpt|claude|nanfeng-knowledge)-conflict$/.exec(target.dataset.action || '');
  if (!match) return;
  const config = jsonConflictActions[match[2]];
  event.preventDefault();
  event.stopImmediatePropagation();
  const owner = state;
  const workspaceId = owner.current?.summary?.id;
  const task = owner[config.taskKey];
  const item = task?.items?.find(candidate => candidate.id === target.dataset.itemId && candidate.status === 'PENDING_CONFIRMATION' && candidate.failure === 'CONFLICT_REIMPORT');
  if (!workspaceId || task?.workspaceId !== workspaceId || !item) return;
  target.disabled = true;
  try {
    const confirmed = match[1] === 'confirm';
    await invoke(confirmed ? config.confirm : config.skip, { args: { workspaceId, taskId: task.id, itemId: item.id } });
    if (!importRouteIsCurrent(owner, workspaceId)) return;
    await refresh();
    if (!importRouteIsCurrent(owner, workspaceId)) return;
    owner.status = confirmed ? `已按您的确认再次导入该 ${config.label} 会话；旧会话保留。` : `已跳过该 ${config.label} 冲突项；旧会话保留。`;
    owner.error = '';
  } catch (error) {
    if (importRouteIsCurrent(owner, workspaceId)) owner.error = `${config.label} 冲突处理失败：${String(error)}`;
  }
  if (state === owner) render();
}, true);
const settingsParent = page => ['model-configuration', 'conversation-cost', 'context-selections', 'diagnostics'].includes(page)
  ? 'model'
  : ['favorites', 'archived', 'recycle'].includes(page) ? 'conversations'
    : page === 'memory-overview' ? 'personalization'
      : ['json-import-results', 'zip-import-results'].includes(page) ? 'data' : null;
function writeProductSettings(patch) {
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
    state.error = error?.code === 'APP_SETTINGS_COMMIT_UNKNOWN'
      ? `Desktop 设置保存结果未确认，当前选择保留在界面：${String(error)}`
      : `Desktop 设置未保存：${String(error)}`;
    render();
  });
}

async function savePersonalization() {
  if (!state.settingsCapabilities.ordinaryChatPersonalization) return;
  const fields = ['tone', 'nickname', 'occupation', 'customInstructions'];
  const patch = Object.fromEntries(fields.map(key => [key, state.personalizationDraft[key]]));
  try {
    await persistNativeAppSettings({}, patch);
    if (fields.every(key => state.personalizationDraft[key] === patch[key])) {
      state.personalizationDraft = { ...state.productSettings };
      state.personalizationDirty = false;
      if (state.dialog?.kind === 'custom-instructions-fullscreen') state.dialog = null;
    }
    state.status = '';
    state.error = '';
  } catch (error) {
    state.error = error?.code === 'APP_SETTINGS_COMMIT_UNKNOWN'
      ? `个性化设置保存结果未确认，编辑稿已保留：${String(error)}`
      : `个性化设置未保存：${String(error)}`;
  }
  render();
}
app.addEventListener('click', event => {
  const target = event.target.closest?.('[data-action]');
  const action = target?.dataset.action;
  if (!['show-settings', 'open-settings-page', 'settings-back', 'return-to-settings-conversation-list', 'open-settings-picker', 'dismiss-settings-picker', 'select-settings-picker-option', 'toggle-product-setting', 'save-personalization', 'open-custom-instructions-fullscreen', 'save-custom-instructions-fullscreen', 'select-model-service-provider', 'toggle-model-service-enabled', 'select-model-service-preset', 'reveal-model-service-credential', 'save-model-service-settings', 'ask-delete-model-service-credential', 'confirm-delete-model-service-credential', 'test-model-service-connection', 'refresh-openrouter-registry', 'select-usage-section'].includes(action)) return;
  event.preventDefault();
  event.stopImmediatePropagation();
  if (action === 'show-settings') {
    state.pane = 'settings';
    state.sidebarOpen = false;
    state.settingsSection = 'personalization';
    state.settingsPicker = null;
    if (!state.personalizationDirty) state.personalizationDraft = { ...state.productSettings };
    state.profileOpen = false;
    state.error = '';
  } else if (action === 'open-settings-page') {
    const nextSettingsSection = target.dataset.page || 'personalization';
    if (nextSettingsSection !== 'account' && state.accountRecovery?.rotation === true) state.accountRecovery = null;
    if (nextSettingsSection !== 'model-configuration') clearModelCredentialDraftOutsideEditor(true);
    state.settingsSection = nextSettingsSection;
    state.settingsPicker = null;
    if (state.settingsSection === 'personalization') {
      if (!state.personalizationDirty) state.personalizationDraft = { ...state.productSettings };
    }
    if (state.settingsSection === 'model-configuration' && !state.modelServiceDraft) resetModelServiceDraft();
    const settingsReadOwner = state;
    const settingsReadGeneration = (state.settingsReadGeneration || 0) + 1;
    state.settingsReadGeneration = settingsReadGeneration;
    const settingsReadIsCurrent = () => state === settingsReadOwner && state.pane === 'settings'
      && state.settingsSection === nextSettingsSection && state.settingsReadGeneration === settingsReadGeneration;
    const readSettings = (promise, label, message = String) => promise.then(() => {
      if (settingsReadIsCurrent()) render();
    }).catch(error => {
      if (!settingsReadIsCurrent() || error?.code === 'STALE_DATA_AREA') return;
      settingsReadOwner.error = `${label}：${message(error)}`;
      render();
    });
    if (state.settingsSection === 'reminders' && native) readSettings(Promise.all([loadDesktopReminders(), loadDesktopBackgroundRuntime(), readReminderNotificationPermission()]), '提醒计划读取失败');
    if (state.settingsSection === 'conversation-cost' && native) readSettings(loadDesktopUsageLedger(), '费用记录读取失败');
    if (state.settingsSection === 'context-selections' && native) readSettings(loadDesktopContextRecords(), '上下文记录读取失败');
    if (state.settingsSection === 'diagnostics' && native) readSettings(loadDesktopDiagnosticRecords(), '运行诊断读取失败');
    if (state.settingsSection === 'privacy' && native) readSettings(loadDesktopPrivacyInventory(), '本机数据概况读取失败');
    if (state.settingsSection === 'data' && native) readSettings(loadSelectedBackupStatus(), '本机备份状态读取失败', localBackupErrorMessage);
    if (state.settingsSection === 'account' && native) readSettings(loadDesktopAccountSync(), '账号同步状态读取失败');
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
      const next = key === 'memoryEnabled'
        ? !(state.productSettings.memoryEnabled && state.productSettings.memoryRetrievalEnabled)
        : !state.productSettings[key];
      if (key === 'monitorNotifications' && next && native) {
        void requestReminderNotificationPermission().then(granted => {
          if (granted) writeProductSettings({ monitorNotifications: true });
          else state.error = '系统未授予通知权限；设置保持关闭，监控结果仍只保存在本机。';
          render();
        });
      } else {
        writeProductSettings(key === 'memoryEnabled'
          ? { memoryEnabled: next, memoryRetrievalEnabled: next }
          : { [key]: next });
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
    const providerId = modelSettingsProviderTarget(state.modelProviderId, target.dataset.providerId, target.dataset.providerPlatform === 'true', state.lastRelayModelProvider || 'RELAYCORE_CLAUDE');
    if (providerId?.startsWith('RELAYCORE_')) state.lastRelayModelProvider = providerId;
    if (!state.modelSettingsSaving && !state.modelSettingsTesting && state.modelServiceSettings.some(item => item.providerId === providerId)) {
      state.modelProviderId = providerId;
      persistModelProviderTab(providerId);
      resetModelServiceDraft();
    }
  } else if (action === 'toggle-model-service-enabled') {
    void setModelServiceEnabled();

  } else if (action === 'select-model-service-preset') {
    void setModelServicePreset(target.dataset.presetId || state.modelServiceDraft?.presetId);
    state.settingsPicker = null;

  } else if (action === 'reveal-model-service-credential') {
    void revealModelServiceCredential();
  } else if (action === 'save-model-service-settings') {
    void saveModelServiceSettings();
  } else if (action === 'ask-delete-model-service-credential') {
    const selected = selectedModelService();
    if (selected?.credentialStored) state.dialog = { kind: 'model-credential-delete', providerId: selected.providerId, providerDisplayName: selected.providerDisplayName, submitting: false, failure: '' };
  } else if (action === 'confirm-delete-model-service-credential') {
    void deleteModelServiceCredential();
  } else if (action === 'test-model-service-connection') {
    void testModelServiceConnection();
  } else if (action === 'refresh-openrouter-registry') {
    void refreshOpenrouterRegistry();
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
    if (fullDelete && result.restartRequired) {
      state.dialog = null;
      state.error = '';
      state.status = '正在安全退出并删除双区本机数据；应用将重新打开并回读结果。';
      render();
      return;
    }
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
app.addEventListener('click', async event => {
  const target = event.target.closest?.('[data-action="retry-chatgpt-task"]');
  if (!target || !state.chatgptTask) return;
  event.preventDefault();
  event.stopImmediatePropagation();
  const owner = state;
  const workspaceId = owner.current?.summary?.id;
  const taskId = owner.chatgptTask.id;
  try {
    await invoke('retry_chatgpt_import_task', { taskId, workspaceId });
    if (importRouteIsCurrent(owner, workspaceId)) await commitPendingImport({ taskKey: 'chatgptTask', readCommand: 'read_chatgpt_import_task', confirmCommand: 'confirm_chatgpt_import_item', label: 'ChatGPT', owner, workspaceId });
  } catch (error) { if (state === owner) owner.error = `ChatGPT 重试未完成：${String(error)}`; }
  if (state === owner) render();
}, true);
async function importPreflighted() { try { state.current = await invoke('import_staged_exchange_as_new_workspace', { args: { stagingId: state.preflight.stagingId, workspaceTitle: document.querySelector('#workspace-title')?.value || '' } }); state.dialog = null; state.status = '导入完成：stable ID、revision、关系与资产 hash 已保真保存。'; await refresh(); } catch (error) { state.error = `导入失败：${String(error)}`; render(); } }
async function mutate(args, success, onSaved = null) {
  if (!native || !state.current) return;
  const owner = state;
  const workspaceId = owner.current.summary.id;
  const dialog = owner.dialog;
  const currentRoute = () => state === owner && owner.current?.summary?.id === workspaceId;
  try {
    const receipt = await invoke('mutate_desktop_domain', { args: { ...args, intentId: intent('intent'), workspaceId } }, owner);
    if (!currentRoute()) return;
    if (typeof onSaved === 'function') onSaved(receipt);
    owner.status = success;
    owner.error = '';
    if (owner.dialog === dialog) owner.dialog = null;
    await refresh();
  } catch (error) {
    if (!currentRoute() || error?.code === 'STALE_DATA_AREA') return;
    owner.error = `写入被拒绝：${String(error)}`;
    render();
    if (error?.code === 'REVISION_CONFLICT' || String(error).includes('REVISION_CONFLICT')) {
      if (owner.dialog === dialog) { recordDomainConflict(dialog, args, {}); render(); }
      const conflictMessage = owner.error;
      try {
        const current = await invoke('read_desktop_workspace', { workspaceId }, owner);
        if (!currentRoute()) return;
        owner.current = current;
        if (owner.dialog === dialog) recordDomainConflict(dialog, args, current);
      } catch (readError) {
        if (!currentRoute() || readError?.code === 'STALE_DATA_AREA') return;
        owner.error = `${conflictMessage}；最新版本未回读：${String(readError)}`;
      }
      render();
    }

  }
}
function saveProject() { const item = state.dialog.item; mutate({ entity: 'project', action: item ? 'update' : 'create', objectId: item?.id, expectedRevision: item?.revision, fields: { title: document.querySelector('#project-title')?.value || '', description: document.querySelector('#project-description')?.value || '' } }, item ? 'Project 已保存为新的本地 revision。' : 'Project 已创建；可撤销。'); }
function saveKnowledge() { const item = state.dialog.item; const tags = (document.querySelector('#knowledge-tags')?.value || '').split(',').map(value => value.trim()).filter(Boolean); const projectId = document.querySelector('#knowledge-project')?.value || null; const fields = { title: document.querySelector('#knowledge-title')?.value || '', body: document.querySelector('#knowledge-body')?.value || '', ...(item ? { tags } : { tags, scope: projectId ? 'PROJECT' : 'GLOBAL', projectId }) }; mutate({ entity: 'knowledge', action: item ? 'update' : 'create', objectId: item?.id, expectedRevision: item?.revision, fields }, item ? 'Knowledge 已保存为新的本地 revision。' : 'Knowledge 已创建；可撤销。'); }
function saveMemory() { const item = state.dialog.item; const projectId = document.querySelector('#memory-project')?.value || null; const title = document.querySelector('#memory-title')?.value || ''; const body = document.querySelector('#memory-body')?.value || ''; mutate({ entity: 'memory', action: item ? 'update' : 'create', objectId: item?.id, expectedRevision: item?.revision, fields: item ? { title, body } : { title, body, scope: projectId ? 'PROJECT' : 'GLOBAL', scopeId: projectId } }, item ? 'Memory 已保存为新的本地 revision。' : 'Memory 已创建；可撤销。'); }
app.addEventListener('change', event => {
  if (state.dialog?.kind === 'relation' && ['relation-from','relation-to','relation-kind'].includes(event.target?.id)) {
    state.dialog.draft ??= {};
    state.dialog.draft[event.target.id] = event.target.value;
  }
}, true);
function saveRelation() { mutate({ entity: 'relation', action: 'create', fields: { fromId: document.querySelector('#relation-from')?.value || '', toId: document.querySelector('#relation-to')?.value || '', kind: document.querySelector('#relation-kind')?.value || '' } }, 'Knowledge relation 已显式建立；可撤销。'); }
async function undo(redo = false) {
  if (!native || !state.current) return;
  const owner = state;
  const workspaceId = owner.current.summary.id;
  const currentRoute = () => state === owner && owner.current?.summary?.id === workspaceId;
  try {
    await invoke(redo ? 'redo_desktop_domain' : 'undo_desktop_domain', {
      args: { intentId: intent(redo ? 'redo' : 'undo'), workspaceId },
    }, owner);
    if (!currentRoute()) return;
    owner.status = redo ? '已重做为新 revision。' : '已撤销为新 revision。';
    owner.error = '';
    await refresh();
  } catch (error) {
    if (!currentRoute() || error?.code === 'STALE_DATA_AREA') return;
    owner.error = `历史动作不可应用：${String(error)}`;
    render();
  }
}
async function saveMetadata() { if (!native || !state.current) return; try { const providerId = document.querySelector('#provider-id')?.value || ''; const modelId = document.querySelector('#model-id')?.value || ''; const existing = state.history.modelMetadata.find(item => item.providerId === providerId && item.modelId === modelId); await invoke('upsert_desktop_model_metadata', { args: { intentId: intent('metadata'), workspaceId: state.current.summary.id, expectedRevision: existing?.revision || 0, providerId, modelId, metadata: { capabilities: ['TEXT'], priceVersion: document.querySelector('#price-version')?.value || '', currency: document.querySelector('#price-currency')?.value || '', source: '用户手工本地配置', updatedAt: '2026-08-13T00:00:00Z', pricingStatus: 'MANUAL', safeUsage: { knownCostMicros: null } } } }); state.status = '本地 metadata 已保存；不是实时价格或真实费用。'; state.dialog = null; await refresh(); } catch (error) { state.error = `metadata 被拒绝：${String(error)}`; render(); } }
async function exportCurrent() { if (!native || !state.current) return; const selectedPath = await dialogInvoke('save', { defaultPath: `${state.current.summary.id}.nfai-exchange`, filters: [{ name: '南枫 AI 交换包', extensions: ['nfai-exchange'] }] }); if (!selectedPath) return; try { const receipt = await invoke('export_desktop_workspace_to_selected_path', { workspaceId: state.current.summary.id, selectedPath }); state.status = `导出完成并严格回读：${short(receipt.semanticHash)} · ${bytes(receipt.byteCount)}`; } catch (error) { state.error = `导出失败：${String(error)}`; } render(); }
function localBackupErrorMessage(error) {
  let detail = error;
  if (typeof detail === 'string' && detail.trimStart().startsWith('{')) {
    try { detail = JSON.parse(detail); } catch { /* A plain native message remains readable. */ }
  }
  if (detail && typeof detail === 'object' && typeof detail.message === 'string' && detail.message.trim()) return detail.message;
  return String(error);
}
async function exportLocalBackup() {
  const owner = selectedBackupAreaState();
  if (!native || owner.localBackup.working || owner.localBackup.restartRequired || owner.localBackup.rollbackPending) return;
  const selectedPath = await dialogInvoke('save', { defaultPath: 'nanfeng-ai-local-backup.nfai-backup', filters: [{ name: '南枫 AI 本机备份', extensions: ['nfai-backup'] }] });
  if (!selectedPath || selectedBackupAreaState() !== owner) return;
  owner.localBackup = { ...owner.localBackup, working: true, operation: 'EXPORT', notice: '', error: '' };
  render();
  try {
    const artifact = await invokeSelectedBackup(owner, 'export_desktop_local_backup', { selectedPath });
    owner.localBackup = { ...owner.localBackup, working: false, operation: null, notice: `所选${owner.dataArea === 'WORK' ? '工作' : '对话'}区备份已回读校验：${artifact.sha256.slice(0, 12)}… · Schema ${artifact.schemaVersion}`, error: '' };
  } catch (error) {
    owner.localBackup = { ...owner.localBackup, working: false, operation: null, notice: '', error: `备份失败：${localBackupErrorMessage(error)}` };
  }
  render();
}
async function importLocalBackup() {
  const owner = selectedBackupAreaState();
  if (!native || owner.localBackup.working || owner.localBackup.restartRequired || owner.localBackup.rollbackPending) return;
  const selectedPath = await dialogInvoke('open', { multiple: false, directory: false, filters: [{ name: '南枫 AI 本机备份', extensions: ['nfai-backup'] }] });
  if (!selectedPath || selectedBackupAreaState() !== owner) return;
  owner.localBackup = { ...owner.localBackup, working: true, operation: 'RESTORE', preflight: null, replaceLocal: false, notice: '', error: '' };
  render();
  try {
    const preflight = await invokeSelectedBackup(owner, 'preflight_desktop_local_backup', { selectedPath });
    owner.localBackup = { ...owner.localBackup, working: false, operation: null, preflight, replaceLocal: !(preflight.conflicts || []).length, notice: `所选${owner.dataArea === 'WORK' ? '工作' : '对话'}区预检通过：Schema ${preflight.schemaVersion}，请确认恢复方式。`, error: '' };
  } catch (error) {
    owner.localBackup = { ...owner.localBackup, working: false, operation: null, preflight: null, notice: '', error: `预检失败：${localBackupErrorMessage(error)}` };
  }
  render();
}
async function restoreLocalBackup() {
  const owner = selectedBackupAreaState();
  const preflight = owner.localBackup.preflight;
  if (!native || !preflight || owner.localBackup.working || owner.localBackup.restartRequired || owner.localBackup.rollbackPending) return;
  owner.localBackup = { ...owner.localBackup, working: true, operation: 'RESTORE', notice: '', error: '' };
  render();
  try {
    const receipt = await invokeSelectedBackup(owner, 'restore_desktop_local_backup', { fingerprint: preflight.fingerprint, replaceLocal: Boolean(owner.localBackup.replaceLocal) });
    owner.localBackup = { ...owner.localBackup, working: false, operation: null, preflight: null, notice: '替换已完成。为避免旧 SQLite 与页面引用，现请手动完全重启 App；不会自动继续任务。', error: '', restartRequired: Boolean(receipt.restartRequired) };
    if (receipt.restartRequired) state.dialog = { kind: 'local-backup-restart-required' };
  } catch (error) {
    try { applyDesktopLocalBackupStatus(owner, await invokeSelectedBackup(owner, 'read_desktop_local_backup_status')); } catch { /* Keep the actionable native error. */ }
    owner.localBackup = { ...owner.localBackup, working: false, operation: null, error: `恢复失败：${localBackupErrorMessage(error)}` };
  }
  render();
}
async function cancelLocalRestore() {
  const owner = selectedBackupAreaState();
  if (!native || owner.localBackup.working || owner.localBackup.restartRequired || owner.localBackup.rollbackPending) return;
  owner.localBackup = { ...owner.localBackup, working: true, operation: 'RESTORE', notice: '', error: '' };
  render();
  try {
    await invokeSelectedBackup(owner, 'cancel_desktop_local_restore');
    owner.localBackup = { ...owner.localBackup, working: false, operation: null, preflight: null, replaceLocal: false, notice: '已取消；未改动本地数据。', error: '', interrupted: false };
  } catch (error) {
    owner.localBackup = { ...owner.localBackup, working: false, operation: null, error: `取消未完成：${localBackupErrorMessage(error)}` };
  }
  render();
}
app.addEventListener('click', event => {
  const action = event.target.closest?.('[data-action]')?.dataset.action;
  if (!['new-chat', 'select-chat', 'select-workspace', 'select-chat-source'].includes(action)) return;
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
// Local CHAT rows own their route here so a delayed model projection cannot
// overwrite the next conversation. Cloud rows keep their dedicated opener.
app.addEventListener('click', event => {
  const target = event.target.closest?.('[data-action="select-chat"]');
  if (!target || target.dataset.workspaceId || state.pane === 'work') return;
  event.preventDefault();
  event.stopImmediatePropagation();
  const owner = state;
  const workspaceId = owner.current?.summary?.id;
  const conversationId = target.dataset.id;
  const generation = ++conversationSelectionGeneration;
  const settingsPage = target.closest('.android-settings-main') ? owner.settingsSection : null;
  owner.settingsConversationReturn = settingsPage && ['favorites', 'archived', 'recycle'].includes(settingsPage)
    ? { page: settingsPage, label: settingsPage === 'favorites' ? '收藏' : settingsPage === 'archived' ? '已归档' : '回收站', readOnly: ['archived', 'recycle'].includes(settingsPage) }
    : null;
  owner.pane = owner.dataArea === 'WORK' ? 'work' : 'chat';
  owner.selectedConversationId = conversationId;
  owner.newConversationDraftWorkspaceId = null;
  owner.p6gSelection = null;
  owner.conversationPreferences = { revision: 0, toneOverride: null, webSearchOverride: null };
  if (!owner.chatScrollPositions.has(conversationId)) owner.pendingChatScrollToLatestId = conversationId;
  owner.profileOpen = false;
  owner.error = '';
  owner.conversationFindOpen = false;
  owner.conversationFindQuery = '';
  recomputeConversationFind({ resetIndex: true });
  resetComposerPresentation();
  render();
  void Promise.all([
    loadConversationSelectionProjection(owner, workspaceId, conversationId, generation),
    loadOrdinaryComposerDraft(),
    owner.settingsConversationReturn?.readOnly ? Promise.resolve() : markConversationOpened(conversationId),
  ]).catch(error => {
    if (state !== owner || generation !== conversationSelectionGeneration) return;
    owner.error = `会话未完全读取：${String(error)}`;
  }).finally(render);
}, true);
async function saveLocalMessage() { const text = state.composerDraft.trim(); if (!text) { state.error = '请输入非空内容。'; render(); return; } if (!native || !state.current) { state.error = native ? '本机聊天数据尚未就绪，请关闭后重新打开应用。' : 'Web 预览不会写入 Desktop SQLite。'; render(); return; } const conversation = resolveConversation(state.current, state.selectedConversationId); try { if (conversation) { await invoke('mutate_desktop_domain', { args: { intentId: intent('chat-message'), workspaceId: state.current.summary.id, entity: 'conversation', action: 'appendMessage', objectId: conversation.id, expectedRevision: conversation.revision, fields: { text, role: 'user' } } }); } else { const receipt = await invoke('mutate_desktop_domain', { args: { intentId: intent('chat-create'), workspaceId: state.current.summary.id, entity: 'conversation', action: 'create', fields: { title: text.replace(/\s+/g, ' ').slice(0, 36), projectId: state.pane === 'work' ? state.selectedWorkProjectId : null, firstMessage: text } } }); state.selectedConversationId = receipt.objectId; } state.composerDraft = ''; state.error = ''; state.status = '已保存为本地记录；没有调用模型。'; await refresh(); } catch (error) { state.error = `本地保存被拒绝：${String(error)}`; render(); } }
app.addEventListener('click', async event => { const target = event.target.closest('[data-action]'); const action = target?.dataset.action; if (!action) return; const data = workspace(); if (action === 'new-chat') { state.settingsConversationReturn = null; state.pane = state.dataArea === 'WORK' ? 'work' : 'chat'; state.selectedConversationId = null; state.conversationPreferences = { revision: 0, toneOverride: null, webSearchOverride: null }; state.composerDraft = ''; state.profileOpen = false; state.error = ''; state.status = '新对话将先保存到本地；联网回答需另行配置并确认。'; state.conversationFindOpen = false; state.conversationFindQuery = ''; recomputeConversationFind({ resetIndex: true }); render(); } else if (action === 'select-chat') { const settingsPage = target.closest('.android-settings-main') ? state.settingsSection : null; if (settingsPage && ['favorites', 'archived', 'recycle'].includes(settingsPage)) state.settingsConversationReturn = { page: settingsPage, label: settingsPage === 'favorites' ? '收藏' : settingsPage === 'archived' ? '已归档' : '回收站', readOnly: ['archived', 'recycle'].includes(settingsPage) }; else state.settingsConversationReturn = null; state.pane = state.dataArea === 'WORK' ? 'work' : 'chat'; state.selectedConversationId = target.dataset.id; if (native && state.current) { state.p6gSelection = await invoke('read_desktop_p6g_selection', { workspaceId: state.current.summary.id, conversationId: state.selectedConversationId }); state.conversationPreferences = state.p6gSelection.conversationOverride; } else { state.conversationPreferences = { revision: 0, toneOverride: null, webSearchOverride: null }; } if (!state.chatScrollPositions.has(target.dataset.id)) state.pendingChatScrollToLatestId = target.dataset.id; state.profileOpen = false; state.error = ''; state.conversationFindOpen = false; state.conversationFindQuery = ''; recomputeConversationFind({ resetIndex: true }); if (!state.settingsConversationReturn?.readOnly) await markConversationOpened(target.dataset.id); render(); } else if (action === 'show-chat') { state.settingsConversationReturn = null; state.pane = state.dataArea === 'WORK' ? 'work' : 'chat'; state.profileOpen = false; render(); } else if (action === 'show-work') { state.settingsConversationReturn = null; state.pane = 'work'; state.selectedConversationId = null; state.selectedWorkProjectId = null; state.profileOpen = false; render(); } else if (action === 'show-connections') { state.pane = 'connections'; state.profileOpen = false; render(); } else if (action === 'toggle-profile') { state.profileOpen = !state.profileOpen; render(); } else if (action === 'save-local-message') saveLocalMessage(); else if (action === 'start-import') pickExchange(); else if (action === 'confirm-import') importPreflighted(); else if (action === 'close-dialog') { if (state.dialog?.kind === 'custom-instructions-fullscreen') { state.personalizationDraft = { ...state.dialog.previousDraft }; state.personalizationDirty = state.dialog.previousDirty; } state.dialog = null; render(); } else if (action === 'select-workspace' || action === 'select-chat-source') { state.current = await invoke('read_desktop_workspace', { workspaceId: target.dataset.id }); state.history = await invoke('read_desktop_workbench_history', { workspaceId: target.dataset.id }); state.selectedConversationId = null; state.conversationPreferences = { revision: 0, toneOverride: null, webSearchOverride: null }; reloadFavoriteConversationIds(); await loadConversationReadState({ observeSelected: false }); state.status = workspaceUiEnabled(state.runtimeInfo) ? '已切换到独立本地工作区。' : '已切换聊天记录来源。'; state.error = ''; render(); } else if (action === 'show-conversation') { state.pane = 'conversation'; state.treeOpen = false; render(); } else if (action === 'show-knowledge') { state.pane = 'knowledge'; state.treeOpen = false; render(); } else if (action === 'toggle-inspector') { state.inspectorOpen = !state.inspectorOpen; render(); } else if (action === 'toggle-tree') { state.treeOpen = !state.treeOpen; render(); } else if (action === 'toggle-scale') { state.scale = state.scale === 1 ? 2 : 1; state.status = `应用缩放已切换为 ${state.scale.toFixed(1)}×。`; render(); } else if (action === 'about') { state.dialog = 'about'; render(); } else if (action === 'new-project') { state.dialog = { kind: 'project', item: null }; render(); } else if (action === 'edit-project') { state.dialog = { kind: 'project', item: data.exchange.projects.find(item => item.id === target.dataset.id) }; render(); } else if (action === 'save-project') saveProject(); else if (action === 'new-knowledge') { state.dialog = { kind: 'knowledge', item: null }; render(); } else if (action === 'edit-knowledge') { state.dialog = { kind: 'knowledge', item: data.exchange.knowledge.find(item => item.id === target.dataset.id) }; render(); } else if (action === 'save-knowledge') saveKnowledge(); else if (action === 'new-memory') { state.dialog = { kind: 'memory', item: null }; render(); } else if (action === 'edit-memory') { state.dialog = { kind: 'memory', item: data.exchange.memory.find(item => item.id === target.dataset.id) }; render(); } else if (action === 'save-memory') saveMemory(); else if (action === 'new-relation') { state.dialog = { kind: 'relation' }; render(); } else if (action === 'save-relation') saveRelation(); else if (action === 'delete-knowledge') mutate({ entity: 'knowledge', action: 'softDelete', objectId: target.dataset.id, expectedRevision: Number(target.dataset.revision), fields: {} }, 'Knowledge 已软删除；可在回收站恢复。'); else if (action === 'delete-memory') mutate({ entity: 'memory', action: 'softDelete', objectId: target.dataset.id, expectedRevision: Number(target.dataset.revision), fields: {} }, 'Memory 已软删除；可在回收站恢复。'); else if (action === 'delete-relation') mutate({ entity: 'relation', action: 'softDelete', objectId: target.dataset.id, expectedRevision: Number(target.dataset.revision), fields: {} }, 'relation 已撤销；可在回收站恢复。'); else if (action === 'restore') mutate({ entity: target.dataset.entity, action: 'restore', objectId: target.dataset.id, expectedRevision: Number(target.dataset.revision), fields: {} }, '对象已恢复为新的 revision。'); else if (action === 'undo') undo(); else if (action === 'redo') undo(true); else if (action === 'recycle') { state.dialog = 'recycle'; render(); } else if (action === 'metadata') { state.dialog = 'metadata'; render(); } else if (action === 'save-metadata') saveMetadata(); else if (action === 'start-export') exportCurrent(); });
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
  const editorKind = state.dialog?.kind;
  const reminderField = { 'reminder-title': 'title', 'reminder-instruction': 'instruction', 'reminder-anchor-local': 'anchorLocal', 'reminder-timezone': 'timezoneId', 'reminder-missed-policy': 'missedPolicy', 'reminder-schedule-kind': 'scheduleKind' }[event.target?.id];
  if (reminderField && state.reminderEditor) {
    state.reminderEditor = { ...state.reminderEditor, item: { ...state.reminderEditor.item, [reminderField]: event.target.value } };
  }
  if ((['project', 'knowledge', 'memory', 'relation'].includes(editorKind) && event.target?.id?.startsWith(`${editorKind}-`)) || (editorKind === 'history-knowledge-review' && ['history-candidate-title', 'history-candidate-body', 'history-candidate-tags'].includes(event.target?.id)) || (editorKind === 'markdown-knowledge-review' && event.target?.id?.startsWith('markdown-knowledge-')) || (editorKind === 'json-knowledge-review' && event.target?.id?.startsWith('json-knowledge-')) || (editorKind === 'pdf-knowledge-review' && event.target?.id?.startsWith('pdf-knowledge-')) || (['web-knowledge-review','web-knowledge-url'].includes(editorKind) && event.target?.id?.startsWith('web-knowledge-'))) {
    state.dialog.draft ??= {};
    state.dialog.draft[event.target.id] = event.target.value;
  }
  if (event.target.id === 'conversation-rename' && state.dialog?.kind === 'conversation-rename') {
    // A streaming answer can redraw the modal before Save is clicked. Keep
    // the edited value in its owner instead of restoring the original title.
    state.dialog.title = event.target.value;
  }
  if (event.target.id === 'conversation-user-message-edit' && state.dialog?.kind === 'edit-conversation-user-message') {
    state.dialog.text = event.target.value;
  }
  if (event.target.id === 'chat-composer') {
    state.composerDraft = event.target.value;
    resizeComposer(event.target);
    const send = document.querySelector('[data-action="save-local-message"]');
    const attachmentCount = state.temporaryConversation ? (state.temporaryConversation.draftAttachmentIds || []).length : state.composerAttachments.length;
    if (send) send.disabled = (!state.composerDraft.trim() && !attachmentCount) || !native || (!state.current && !state.temporaryConversation);
    scheduleOrdinaryEgressPreview();
    updateOrdinaryEgressDisclosure();
  } else if (event.target.id === 'settings-search') {
    state.settingsSearch = event.target.value;
    scheduleSettingsSearchRender();
  } else if (event.target.id === 'memory-summary-editor' && state.dialog?.kind === 'memory-summary-editor') {
    state.dialog = updateMemorySummaryDraft(state.dialog, event.target.value);
    const save = document.querySelector('[data-action="save-memory-summary-editor"]');
    if (save) save.disabled = !String(event.target.value || '').trim() || Boolean(state.memorySummarySavePending && state.memorySummarySavePending.workspaceId === state.current?.summary?.id);
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

let composerImeTarget = null;
let composerImeRoute = null;
let composerImePaintPending = false;
function isCurrentComposerImeRoute() {
  return composerImeRoute?.owner === state
    && composerImeRoute.pane === state.pane
    && composerImeRoute.conversationId === state.selectedConversationId
    && composerImeRoute.workspaceId === state.current?.summary?.id;
}
let composerImeEndedAtMs = Number.NEGATIVE_INFINITY;
let searchImeTarget = null;
let searchImeOwner = null;
let searchImeEndedAtMs = Number.NEGATIVE_INFINITY;
app.addEventListener('compositionstart', event => {
  if (event.target.id === 'chat-composer') {
    composerImeTarget = event.target;
    composerImeRoute = { owner: state, pane: state.pane,
      conversationId: state.selectedConversationId, workspaceId: state.current?.summary?.id };
    composerImePaintPending = false;
    return;
  }
  if (event.target.id === 'chat-search' || event.target.id === 'full-search-input') {
    searchImeTarget = event.target;
    searchImeOwner = state;
    supersedeFullSearch();
    if (fullSearchDebounce !== null) window.clearTimeout(fullSearchDebounce);
    fullSearchDebounce = null;
    return;
  }
  if (event.target.id !== 'conversation-find-input') return;
  conversationFindInputController.onCompositionStart();
});

app.addEventListener('compositionend', event => {
  if (event.target.id === 'chat-composer') {
    if (composerImeTarget !== event.target) return;
    const flush = composerImePaintPending && isCurrentComposerImeRoute();
    const route = composerImeRoute;
    composerImeTarget = null;
    composerImeRoute = null;
    composerImePaintPending = false;
    composerImeEndedAtMs = Date.now();
    if (flush) queueMicrotask(() => {
      if (state === route.owner && state.pane === route.pane
          && state.selectedConversationId === route.conversationId
          && state.current?.summary?.id === route.workspaceId) render();
    });
    return;
  }
  if (event.target.id === 'chat-search' || event.target.id === 'full-search-input') {
    const stillOwned = searchImeTarget === event.target && searchImeOwner === state;
    if (searchImeTarget === event.target) { searchImeTarget = null; searchImeOwner = null; }
    if (!stillOwned) return;
    searchImeEndedAtMs = Date.now();
    state.chatSearch = event.target.value;
    if (state.searchPanel) scheduleFullSearch();
    return;
  }
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

function memorySummaryEditorValue(memories) {
  return memorySummaryValue(memories);
}

function activeGlobalMemoryTopic(memories, title) {
  return memories.filter(item => (item.status || 'ACTIVE') === 'ACTIVE' && (item.scope || 'GLOBAL') === 'GLOBAL'
    && !item.scopeId && String(item.title || '概览').trim() === String(title).trim());
}

async function clearMemorySummary() {
  if (!native || !state.current) return;
  const memories = active(state.current, 'memory').filter(item => (item.status || 'ACTIVE') === 'ACTIVE' && (item.scope || 'GLOBAL') === 'GLOBAL' && !item.scopeId);
  if (!memories.length) { state.dialog = null; state.memorySummaryNotice = '没有可删除的记忆摘要。'; render(); return; }
  const owner = state;
  const workspaceId = state.current.summary.id;
  const primary = memories[0];
  try {
    await invoke('mutate_desktop_domain', { args: { intentId: intent('memory-summary-delete'), workspaceId, entity: 'memory', action: 'deleteMemorySummary', objectId: primary.id, expectedRevision: Number(primary.revision), fields: { expectedEntries: memories.map(item => ({ id: item.id, revision: Number(item.revision) })) } } });
    if (state === owner && state.current?.summary.id === workspaceId) {
      state.dialog = null;
      state.memorySummaryNotice = '记忆摘要已删除。';
      state.error = '';
    }
  } catch (error) {
    if (state === owner && state.current?.summary.id === workspaceId) {
      state.dialog = null;
      state.error = `记忆摘要未删除：${String(error)}`;
    }
  }
  if (state === owner && state.current?.summary.id === workspaceId) await refresh();
}

app.addEventListener('click', event => {
  const target = event.target.closest?.('[data-action]');
  const action = target?.dataset.action;
  if (!['suggest-memory-summary', 'confirm-memory-summary-suggestion', 'keep-memory-topic', 'parallel-memory-topic', 'revise-memory-topic', 'review-pending-memory-topic', 'resolve-pending-memory-topic', 'show-memory-audit', 'edit-memory-summary', 'save-memory-summary-editor', 'show-memory-summary-about', 'refresh-memory-summary', 'ask-delete-memory-summary', 'confirm-delete-memory-summary', 'ask-disable-memory-summary', 'confirm-disable-memory-summary'].includes(action)) return;
  event.preventDefault();
  event.stopImmediatePropagation();
  if (action === 'suggest-memory-summary') {
    const messages = orderedConversationMessages(currentConversation());
    const assistant = messages.at(-1);
    const user = messages.at(-2);
    if (!native || !state.current || !state.productSettings.memoryEnabled || !state.productSettings.memoryRetrievalEnabled || state.selectedConversationId !== currentConversation()?.id || assistant?.id !== target.dataset.assistantMessageId || user?.id !== target.dataset.userMessageId || String(assistant?.delivery || '') !== 'COMPLETE') return;
    const draft = memorySummarySuggestion(messagePlainText(user), messagePlainText(assistant));
    if (!draft) return;
    state.dialog = { kind: 'memory-summary-suggestion', workspaceId: state.current.summary.id, conversationId: state.selectedConversationId, assistantMessageId: assistant.id, ...draft, submitting: false };
  } else if (action === 'confirm-memory-summary-suggestion') {
    const draft = state.dialog;
    if (draft?.kind !== 'memory-summary-suggestion' || draft.submitting || !native || !state.current || !state.productSettings.memoryEnabled || !state.productSettings.memoryRetrievalEnabled || draft.workspaceId !== state.current.summary.id || draft.conversationId !== state.selectedConversationId || orderedConversationMessages(currentConversation()).at(-1)?.id !== draft.assistantMessageId) return;
    if ((state.current.exchange?.memory || []).some(item => (item.status || 'ACTIVE') === 'ACTIVE' && String(item.body || '').trim() === draft.body)) { state.dialog = null; render(); return; }
    const sameTopic = activeGlobalMemoryTopic(state.current.exchange?.memory || [], draft.title);
    if (sameTopic.length) {
      draft.kind = 'memory-topic-conflict';
      draft.existing = [...sameTopic].sort((left, right) => String(right.updatedAt || right.createdAt || '').localeCompare(String(left.updatedAt || left.createdAt || '')))[0];
      draft.expectedEntries = sameTopic.map(item => ({ id: item.id, revision: Number(item.revision) }));
      render();
      return;
    }
    const owner = state;
    draft.submitting = true;
    render();
    void invoke('mutate_desktop_domain', { args: { intentId: intent('memory-suggestion'), workspaceId: draft.workspaceId, entity: 'memory', action: 'saveReviewedMemory', fields: { title: draft.title, body: draft.body, resolution: 'CREATE_NEW', expectedEntries: [] } } }).then(async () => {
      if (owner.dialog === draft) { owner.dialog = null; owner.status = '记忆摘要已保存到本机。'; }
      if (state === owner) await refresh();
    }).catch(error => { if (owner.dialog === draft) { draft.submitting = false; draft.failure = `保存失败：${String(error)}`; if (state === owner) render(); } });
    return;
  } else if (['keep-memory-topic', 'parallel-memory-topic', 'revise-memory-topic'].includes(action)) {
    const draft = state.dialog;
    if (draft?.kind !== 'memory-topic-conflict' || draft.submitting || !native || !state.current || draft.workspaceId !== state.current.summary.id || draft.conversationId !== state.selectedConversationId || orderedConversationMessages(currentConversation()).at(-1)?.id !== draft.assistantMessageId) return;
    if (action === 'keep-memory-topic') { state.dialog = null; state.status = '已保留现有记忆，本次内容未写入。'; render(); return; }
    const owner = state;
    draft.submitting = true;
    render();
    const resolution = action === 'parallel-memory-topic' ? 'CREATE_PARALLEL' : 'REVISE_EXISTING';
    void invoke('mutate_desktop_domain', { args: { intentId: intent('memory-topic-resolution'), workspaceId: draft.workspaceId,
      entity: 'memory', action: 'saveReviewedMemory', objectId: draft.existing.id, expectedRevision: Number(draft.existing.revision),
      fields: { title: draft.title, body: draft.body, resolution, expectedEntries: draft.expectedEntries } } }).then(async () => {
      if (owner.dialog === draft) { owner.dialog = null; owner.status = resolution === 'CREATE_PARALLEL' ? '已明确并存保存同主题记忆。' : '已修订现有记忆。'; }
      if (state === owner) await refresh();
    }).catch(error => { if (owner.dialog === draft) { draft.submitting = false; draft.failure = `保存失败：${String(error)}`; if (state === owner) render(); } });
    return;
  } else if (action === 'review-pending-memory-topic') {
    if (!native || !state.current) return;
    const candidate = (state.current.exchange?.memory || []).find(item => item.id === target.dataset.id && item.status === 'PENDING_REVIEW');
    if (!candidate) { state.memorySummaryNotice = '候选已变化，请刷新。'; render(); return; }
    const matches = activeGlobalMemoryTopic(state.current.exchange?.memory || [], candidate.title);
    const existing = [...matches].sort((left, right) => String(right.updatedAt || right.createdAt || '').localeCompare(String(left.updatedAt || left.createdAt || '')))[0];
    state.dialog = { kind: 'pending-memory-topic', workspaceId: state.current.summary.id, candidate, existing, expectedEntries: matches.map(item => ({ id: item.id, revision: Number(item.revision) })), submitting: false };
  } else if (action === 'resolve-pending-memory-topic') {
    const draft = state.dialog;
    const resolution = target.dataset.resolution;
    if (draft?.kind !== 'pending-memory-topic' || draft.submitting || !native || !state.current || draft.workspaceId !== state.current.summary.id || !['KEEP_EXISTING', 'CREATE_PARALLEL', 'REVISE_EXISTING'].includes(resolution) || (resolution === 'REVISE_EXISTING' && !draft.existing)) return;
    const owner = state;
    draft.submitting = true;
    render();
    void invoke('mutate_desktop_domain', { args: { intentId: intent('pending-memory-topic'), workspaceId: draft.workspaceId, entity: 'memory', action: 'resolvePendingMemoryTopic', objectId: draft.candidate.id, expectedRevision: Number(draft.candidate.revision), fields: { resolution, expectedEntries: draft.expectedEntries, ...(resolution === 'REVISE_EXISTING' ? { existingId: draft.existing.id } : {}) } } }).then(async () => {
      if (owner.dialog === draft) { owner.dialog = null; owner.memorySummaryNotice = resolution === 'KEEP_EXISTING' ? '已保留现有记忆。' : resolution === 'CREATE_PARALLEL' ? '候选已并存。' : '现有记忆已修订。'; }
      if (state === owner) await refresh();
    }).catch(error => { if (owner.dialog === draft) { draft.submitting = false; draft.failure = `处理失败：${String(error)}`; if (state === owner) render(); } });
    return;
  } else if (action === 'show-memory-audit') {
    if (!native || !state.current) return;
    const item = (state.current.exchange?.memory || []).find(memory => memory.id === target.dataset.id && memory.status === 'ACTIVE');
    if (!item) { state.memorySummaryNotice = '记忆已变化，请刷新。'; render(); return; }
    const owner = state;
    const draft = { kind: 'memory-audit', workspaceId: owner.current.summary.id, memoryId: item.id, title: item.title || '概览', loading: true };
    owner.dialog = draft;
    render();
    void invoke('read_desktop_memory_audit', { workspaceId: draft.workspaceId, memoryId: draft.memoryId }).then(audit => {
      if (owner.dialog === draft) { draft.audit = audit; draft.loading = false; if (state === owner) render(); }
    }).catch(error => {
      if (owner.dialog === draft) { draft.failure = `读取失败：${String(error)}`; draft.loading = false; if (state === owner) render(); }
    });
    return;
  } else if (action === 'edit-memory-summary') {
    const memories = active(workspace(), 'memory').filter(item => (item.status || 'ACTIVE') === 'ACTIVE' && (item.scope || 'GLOBAL') === 'GLOBAL' && !item.scopeId);
    const latest = [...memories].sort((left, right) => String(right.updatedAt || right.createdAt || '').localeCompare(String(left.updatedAt || left.createdAt || '')))[0];
    state.dialog = { kind: 'memory-summary-editor', memoryId: latest?.id, expectedRevision: latest?.revision,
      expectedEntries: memories.map(item => ({ id: item.id, revision: Number(item.revision) })),
      multipleEntries: memories.length > 1,
      value: memorySummaryEditorValue(memories) };
  } else if (action === 'save-memory-summary-editor') {
    const owner = state;
    const workspaceId = owner.current?.summary?.id;
    const editor = owner.dialog;
    const body = String(editor?.value || '').trim();
    if (editor?.kind !== 'memory-summary-editor' || !body || !native || !workspaceId || owner.memorySummarySavePending?.workspaceId === workspaceId) return;
    const pending = { workspaceId };
    owner.memorySummarySavePending = pending;
    editor.pendingSaveBinding = pending;
    render();
    void mutate({ entity: 'memory', action: editor.memoryId ? 'replaceMemorySummary' : 'create', objectId: editor.memoryId, expectedRevision: editor.expectedRevision, fields: editor.memoryId ? { body, expectedEntries: editor.expectedEntries, ...(editor.multipleEntries ? { title: '概览' } : {}) } : { body, scope: 'GLOBAL', scopeId: null } }, '记忆摘要已保存，后续检索使用修改后的内容。', receipt => {
      const current = owner.dialog;
      if (current?.kind !== 'memory-summary-editor' || current.pendingSaveBinding !== pending
          || receipt?.workspaceId !== workspaceId || typeof receipt.objectId !== 'string' || !receipt.objectId
          || !Number.isSafeInteger(receipt.revision) || receipt.revision <= 0
          || (editor.memoryId && editor.memoryId !== receipt.objectId)) return;
      current.memoryId = receipt.objectId;
      current.expectedRevision = receipt.revision;
      current.expectedEntries = [{ id: receipt.objectId, revision: receipt.revision }];
      current.multipleEntries = false;
      current.pendingSaveBinding = null;
    }).finally(() => {
      if (owner.memorySummarySavePending === pending) owner.memorySummarySavePending = null;
      if (state === owner && owner.current?.summary?.id === workspaceId) render();
    });
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
    writeProductSettings({ memoryRetrievalEnabled: false });
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
  const message = resolveConversation(state.current, state.selectedConversationId)?.messages
    ?.find(item => item.id === messageId);
  if (!message || !['user', 'assistant'].includes(String(message.role || '').toLowerCase())) return;
  const conversation = resolveConversation(state.current, state.selectedConversationId);
  const editable = String(message.role).toLowerCase() === 'user'
    && !conversation?.archived && !conversation?.deleted
    && orderedConversationMessages(conversation).some(item => item.id === messageId)
    && Array.isArray(message.blocks) && message.blocks.length > 0
    && message.blocks.every(block => block.kind === 'TEXT');
  const canGenerateAnswer = String(message.role).toLowerCase() === 'user'
    && conversation?.currentLeafId === messageId
    && !conversation.archived && !conversation.deleted
    && !conversation.messages.some(item => item.parentId === messageId);
  const canRegenerate = String(message.role).toLowerCase() === 'assistant'
    && conversation?.currentLeafId === messageId
    && message.delivery === 'COMPLETE' && Boolean(message.attemptId)
    && !message.compareExecutionId && !conversation.archived && !conversation.deleted;
  const canChangeModel = String(message.role).toLowerCase() === 'assistant'
    && conversation?.currentLeafId === messageId
    && ['COMPLETE', 'FAILED', 'CANCELLED', 'UNKNOWN'].includes(message.delivery)
    && Boolean(message.attemptId) && !message.compareExecutionId
    && !conversation.archived && !conversation.deleted;
  state.error = '';
  if (state.assistantMessageMenu?.messageId === messageId) closeTopOverlay();
  else openTransientOverlay('assistant-message-menu', trigger, { messageId, role: String(message.role).toLowerCase(), editable, canGenerateAnswer, canRegenerate, canChangeModel, anchorRect: menuAnchorRect(trigger) });
}
function bindAssistantMessageMenuTriggers() {
  document.querySelectorAll('[data-action="open-assistant-message-menu"]').forEach(button => {
    if (assistantMessageMenuTriggers.has(button)) return;
    assistantMessageMenuTriggers.add(button);
    button.addEventListener('keydown', event => {
      if (event.key !== 'ContextMenu' && !(event.shiftKey && event.key === 'F10')) return;
      event.preventDefault();
      event.stopImmediatePropagation();
      if (!state.assistantMessageMenu) toggleAssistantMessageMenu(button);
    });
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
  if (state.archivePreview) { cancelArchivePreviewLoad(); render(); return; }
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
  else if (event.target.id === 'chat-composer' && event.key === 'Enter' && !event.shiftKey) {
    if (composerImeTarget === event.target || event.isComposing || event.keyCode === 229 || Date.now() - composerImeEndedAtMs < 100) return;
    event.preventDefault();
    saveLocalMessage();
  }
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
restoreTemporaryChat().then(refresh).catch(error => { state.error = `无法打开本机聊天数据：${String(error)}`; render(); });
app.addEventListener('click', event => {
  if (event.target.closest('[data-action]')?.dataset.action !== 'show-p8-inspect') return;
  state.pane = 'p8-inspect';
  state.treeOpen = false;
  state.status = '已读取本机 P8 安全账本 metadata；无 production executor。';
  render();
});

let workspaceSelectionRequestId = 0;
app.addEventListener('click', async event => {
  const target = event.target.closest('[data-action="select-workspace"], [data-action="select-chat-source"]');
  if (!target || !native) return;
  event.stopImmediatePropagation();
  const owner = state;
  const selectionPane = owner.pane;
  const selectionSettingsSection = owner.settingsSection;
  const requestId = ++workspaceSelectionRequestId;
  const workspaceId = target.dataset.id;
  const isCurrent = () => state === owner && requestId === workspaceSelectionRequestId;
  finishConversationBatchEditing();
  if (owner.dialog?.kind === 'conversation-batch-delete') owner.dialog = null;
  try {
    await flushOrdinaryComposerDraft();
    if (!isCurrent()) return;
    const current = await invoke('read_desktop_workspace', { workspaceId }, owner);
    if (!isCurrent()) return;
    owner.current = current;
    owner.selectedWorkProjectId = null;
    // Keep a page the user opened while the workspace read was pending.
    if (owner.pane === selectionPane && owner.settingsSection === selectionSettingsSection) {
      owner.pane = owner.dataArea === 'WORK' ? 'work' : 'chat';
    }
    selectWorkspaceDefaultConversation(current);
    owner.newConversationDraftWorkspaceId = null;
    resetComposerPresentation();
    owner.p6gSelection = null;
    owner.conversationPreferences = { revision: 0, toneOverride: null, webSearchOverride: null };
    owner.favoriteConversationIds = new Set();
    applyConversationReadState({ workspaceId, conversations: [] });
    const readRequestId = ++conversationReadStateRequestId;
    owner.history = { canUndo: false, canRedo: false, recycleBin: [], modelMetadata: [] };
    owner.contextMenu = null;
    owner.assistantMessageMenu = null;
    owner.conversationFindOpen = false;
    owner.conversationFindQuery = '';
    recomputeConversationFind({ resetIndex: true });
    owner.p6kTask = null;
    owner.chatgptTask = null;
    owner.claudeTask = null;
    owner.nanfengKnowledgeTask = null;
    p6kManualLink = { taskId: null, assetOrdinal: null, conversationId: null, messageId: null };
    globalThis.__nanfengP6kManualLink = p6kManualLink;
    render();
    const selectedConversationId = owner.selectedConversationId;
    const [history, p6kTask, chatgptTask, claudeTask, nanfengKnowledgeTask, favoriteIds, readState, selection] = await Promise.all([
      invoke('read_desktop_workbench_history', { workspaceId }, owner),
      invoke('read_latest_p6k_zip_import_task', { workspaceId }, owner),
      invoke('read_latest_chatgpt_import_task', { workspaceId }, owner),
      invoke('read_latest_claude_import_task', { workspaceId }, owner),
      invoke('read_latest_nanfeng_knowledge_import_task', { workspaceId }, owner),
      invoke('read_desktop_favorite_conversation_ids', { workspaceId }, owner),
      invoke('read_desktop_conversation_read_state', { workspaceId, observedConversationId: null }, owner),
      selectedConversationId ? invoke('read_desktop_p6g_selection', { workspaceId, conversationId: selectedConversationId }, owner) : Promise.resolve(null),
    ]);
    if (!isCurrent() || owner.current?.summary?.id !== workspaceId) return;
    owner.history = history;
    owner.p6kTask = p6kTask;
    owner.chatgptTask = chatgptTask;
    owner.claudeTask = claudeTask;
    owner.nanfengKnowledgeTask = nanfengKnowledgeTask;
    owner.favoriteConversationIds = new Set(favoriteIds);
    if (readRequestId === conversationReadStateRequestId) applyConversationReadState(readState);
    if (owner.selectedConversationId === selectedConversationId) {
      owner.p6gSelection = selection;
      owner.conversationPreferences = selection?.conversationOverride || { revision: 0, toneOverride: null, webSearchOverride: null };
    }
    owner.status = workspaceUiEnabled(owner.runtimeInfo)
      ? '已切换到独立本地工作区，并打开该工作区最近对话。'
      : '已切换聊天记录来源，并打开最近对话。';
    owner.error = '';
    render();
    await loadOrdinaryComposerDraft();
    if (isCurrent()) render();
  } catch (error) {
    if (!isCurrent() || error?.code === 'STALE_DATA_AREA') return;
    owner.error = `${workspaceUiEnabled(owner.runtimeInfo) ? '切换工作区' : '切换聊天记录来源'}未完成：${String(error)}`;
    render();
  }
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
  return `<section class="canvas work-knowledge-canvas"><div class="workspace-content-actions"><button class="primary" data-action="new-knowledge">新建知识</button><button data-action="new-relation" ${knowledge.length < 2 ? 'disabled' : ''}>建立关系</button></div>${autoSection}${markdownKnowledgeTaskCard(state.markdownKnowledgeTask)}${jsonKnowledgeTaskCard(state.jsonKnowledgeTask)}${pdfKnowledge.card(state.pdfKnowledgeTask)}${webKnowledge.card(state.webKnowledgeTask)}<div class="knowledge-list">${knowledge.map(item => `<article class="knowledge-item"><header><div><p class="overline">${escape(item.status)} · r${item.revision} · ${escape(item.classification)}</p><h2>${escape(item.title)}</h2></div><span>${escape((item.tags || []).join(' · '))}</span></header><pre>${escape(item.body)}</pre><div class="item-actions"><button data-action="edit-knowledge" data-id="${escape(item.id)}">编辑</button><button data-action="delete-knowledge" data-id="${escape(item.id)}" data-revision="${item.revision}">软删除</button></div></article>`).join('') || '<p class="empty-copy">尚无已保存的本地知识。</p>'}</div><section class="memory-rail"><div class="section-head"><h2>Knowledge relation</h2><button data-action="new-relation" ${knowledge.length < 2 ? 'disabled' : ''}>建立</button></div><ul>${relations.map(item => `<li><span>${escape(item.kind)} · r${item.revision}</span>${escape(name(item.fromId))} → ${escape(name(item.toId))}<button data-action="delete-relation" data-id="${escape(item.id)}" data-revision="${item.revision}">撤销</button></li>`).join('') || '<li>暂无活动 relation。</li>'}</ul></section></section>`;
}

function memoryCanvas(data) {
  const memory = active(data || { exchange: { memory: [] } }, 'memory');
  return `<section class="canvas work-memory-canvas"><div class="workspace-content-actions"><button class="primary" data-action="new-memory">新建 Memory</button></div><section class="memory-rail"><ul>${memory.map(item => `<li><span>${escape(item.title || '概览')} · ${escape(item.scope)} · r${item.revision}</span><button class="memory-button" data-action="edit-memory" data-id="${escape(item.id)}">${escape(item.body)}</button><button data-action="delete-memory" data-id="${escape(item.id)}" data-revision="${item.revision}">软删除</button></li>`).join('') || '<li>暂无活动 Memory。</li>'}</ul></section></section>`;
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

const ordinaryEgressPreviews = new WeakMap();
function ordinaryEgressPreviewInput(owner = state) {
  const text = owner.composerDraft?.trim() || '';
  if (!native || !owner.current?.summary?.id || owner.temporaryConversation
      || (!text && !owner.composerAttachments.length)) return null;
  const conversation = resolveConversation(owner.current, owner.selectedConversationId);
  const input = {
    workspaceId: owner.current.summary.id,
    conversationId: conversation?.id || null,
    text,
    attachmentIds: owner.composerAttachments.map(item => item.id),
    modelId: conversation ? null : owner.pendingNewConversationModelId,
    toneOverride: conversation ? null : owner.conversationPreferences?.toneOverride ?? null,
    webSearchOverride: conversation ? null : owner.conversationPreferences?.webSearchOverride ?? null,
  };
  const signature = JSON.stringify({
    dataArea: owner.dataArea, input, conversationRevision: conversation?.revision ?? null,
    attachments: owner.composerAttachments.map(item => [item.id, item.sha256, item.mimeType, item.byteCount]),
    selection: owner.p6gSelection?.conversationOverride,
    preferences: owner.conversationPreferences,
    globalDefault: owner.p6gGlobalDefault,
    services: owner.modelServiceSettings?.map(item => [item.providerId, item.enabled, item.credentialStored, item.revision, item.presetId]),
    registry: [owner.openrouterRegistryStatus?.status, owner.openrouterRegistryStatus?.catalogVersion],
  });
  return { input, signature };
}
function currentOrdinaryEgressPreview(owner = state) {
  const route = ordinaryEgressPreviewInput(owner);
  const saved = ordinaryEgressPreviews.get(owner);
  return route && saved?.signature === route.signature && saved.status === 'ready' ? saved.preview : null;
}
function scheduleOrdinaryEgressPreview(owner = state) {
  const route = ordinaryEgressPreviewInput(owner);
  const previous = ordinaryEgressPreviews.get(owner);
  if (!route) {
    if (previous?.timer) clearTimeout(previous.timer);
    ordinaryEgressPreviews.delete(owner);
    return;
  }
  if (previous?.signature === route.signature && previous.status !== 'cancelled') return;
  if (previous?.timer) clearTimeout(previous.timer);
  const next = { signature: route.signature, status: 'pending', preview: null, errorCopy: null, timer: null };
  ordinaryEgressPreviews.set(owner, next);
  next.timer = setTimeout(async () => {
    next.timer = null;
    if (state !== owner) { next.status = 'cancelled'; return; }
    try {
      const preview = await invokeForAuthorizedDataAreaOperation(tauriBridge,
        'preview_desktop_ordinary_chat_egress', { args: route.input }, owner);
      if (ordinaryEgressPreviews.get(owner) !== next
          || ordinaryEgressPreviewInput(owner)?.signature !== next.signature) return;
      next.preview = preview;
      next.status = 'ready';
    } catch (error) {
      if (ordinaryEgressPreviews.get(owner) !== next
          || ordinaryEgressPreviewInput(owner)?.signature !== next.signature) return;
      next.status = 'unavailable';
      next.errorCopy = String(error).includes('不能处理本次附件')
        ? '当前模型不能处理本次附件，请更换模型或移除附件'
        : '当前接收方不可用，请检查模型设置';
    }
    if (state === owner) render();
  }, 160);
}
function updateOrdinaryEgressDisclosure(owner = state) {
  if (state !== owner) return;
  const controls = document.querySelector('.chat-composer-actions');
  const existing = controls?.querySelector('.chat-composer-route');
  const route = ordinaryEgressPreviewInput(owner);
  if (!controls || !route) { existing?.remove(); return; }
  const saved = ordinaryEgressPreviews.get(owner);
  const preview = saved?.signature === route.signature && saved.status === 'ready' ? saved.preview : null;
  const line = existing || document.createElement('div');
  if (!existing) {
    line.className = 'chat-composer-route';
    line.setAttribute('role', 'status');
    line.setAttribute('aria-live', 'polite');
    controls.append(line);
  }
  const providerName = owner.modelServiceSettings?.find(item => item.providerId === preview?.recipientProviderId)?.providerDisplayName
    || preview?.recipientProviderId || '';
  line.textContent = preview
    ? `${preview.recipientChoiceId === 'AUTO' ? '自动 · ' : ''}${providerName} · ${preview.recipientDisplayName}`
    : saved?.signature === route.signature && saved.status === 'unavailable'
      ? saved.errorCopy
      : '正在确认本次接收方…';
  line.dataset.routeReady = String(Boolean(preview));
  const send = controls.querySelector('[data-action="save-local-message"]');
  if (send && !preview) send.disabled = true;
}

function renderUnified() {
  if (areaSwitchPending) {
    app.innerHTML = '<main class="desktop-area-switch" role="status" aria-live="polite">正在切换数据区…</main>';
    return;
  }
  if (isCurrentComposerImeRoute() && composerImeTarget?.isConnected
      && document.activeElement === composerImeTarget && !state.searchPanel
      && !state.dialog && !state.accountSyncProgress && !state.contextMenu
      && !state.assistantMessageMenu && !state.composerAddOpen && !state.p6gModelPickerOpen) {
    composerImePaintPending = true;
    updateOrdinaryEgressDisclosure();
    return;
  }
  // Async readbacks may finish just after search opens. Keep the focused IME
  // input connected until its candidate is committed; the queued search then
  // paints the latest owner state. Explicit navigation and dialogs still paint.
  if (searchImeOwner === state && searchImeTarget?.isConnected
      && document.activeElement === searchImeTarget && state.searchPanel
      && !state.dialog && !state.accountSyncProgress && !state.contextMenu
      && !state.assistantMessageMenu) return;
  if (composerCameraOwner && !isCurrentComposerAttachmentRoute(composerCameraOwner, composerCameraRoute)) stopComposerCamera();
  const runtimeRefresh = arguments[0]?.runtimeRefresh === true;
  clearModelCredentialDraftOutsideEditor();
  queueMicrotask(() => resizeComposer(document.getElementById('chat-composer')));
  preserveSearchInputFocus();
  const focusedComposer = document.activeElement?.id === 'chat-composer' ? document.activeElement : null;
  const composerSelection = focusedComposer ? [focusedComposer.selectionStart, focusedComposer.selectionEnd] : null;
  const focusedDialogControl = document.activeElement?.closest?.('.dialog') ? document.activeElement : null;
  const focusedDialogOwner = focusedDialogControl ? state.dialog : null;
  const focusedDialogId = focusedDialogControl?.id || '';
  const dialogSelection = focusedDialogControl && Number.isInteger(focusedDialogControl.selectionStart)
    ? [focusedDialogControl.selectionStart, focusedDialogControl.selectionEnd, focusedDialogControl.selectionDirection]
    : null;
  if (composerSelection) queueMicrotask(() => {
    if (state.dialog || state.contextMenu || state.assistantMessageMenu || state.composerAddOpen || state.p6gModelPickerOpen) return;
    const replacement = document.getElementById('chat-composer');
    if (replacement && !replacement.isSameNode(focusedComposer)) {
      replacement.focus({ preventScroll: true });
      replacement.setSelectionRange(...composerSelection);
    }
  });
  if (!workspaceUiEnabled(state.runtimeInfo)) {
    if (state.dataArea === 'WORK' && !areaSwitchPending) {
      void switchDataArea('CHAT', 'chat');
      return;
    }
    const uiRoute = chatUiRoute(state.pane, state.settingsSection, state.runtimeInfo);
    state.pane = uiRoute.pane; state.settingsSection = uiRoute.settingsPage;
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
          : state.pane === 'reminders' ? renderDesktopRemindersPage({ loading: Boolean(utilityPagesLoading.get(state)?.has('reminders')), projection: state.reminders, notificationEnabled: Boolean(state.productSettings.monitorNotifications), notificationPermission: state.reminderNotificationPermission, native, previewInteractive: Boolean(c09ReminderPreview), evidenceLabel: c09ReminderPreview ? 'Web 只读交互样本不会写入 Desktop SQLite' : '', editor: state.reminderEditor, embedded: true })
            : state.pane === 'transcription' ? renderDesktopTranscriptionPage({ loading: Boolean(utilityPagesLoading.get(state)?.has('transcription')), projection: state.transcription, selectedTaskId: state.selectedTranscriptionTaskId, native, busyTaskId: state.transcriptionBusyTaskId, previewInteractive: Boolean(c10TranscriptionPreview), evidenceLabel: c10TranscriptionPreview ? 'Web 只读交互样本不会写入 Desktop SQLite' : '', embedded: true })
          : state.pane === 'p8-inspect' ? p8InspectCanvas({ agentRuns: state.agentRuns, native, escape, short })
            : null;
  const standaloneWorkspacePage = (!utilityPane && Boolean(workPanel)) || (data?.dataArea !== 'WORK' && isWorkspaceRoot(data, visiblePane, state.selectedWorkProjectId, state.selectedConversationId));
  const nextTranscriptRetention = currentTranscriptRetention(data);
  const existingTranscript = document.querySelector('.chat-scroll[data-conversation-id]');
  const preserveTranscript = !runtimeRefresh && canRetainTranscript(renderedTranscriptRetention, nextTranscriptRetention)
    && existingTranscript?.dataset.conversationId === nextTranscriptRetention.conversation.id;
  // Most shell interactions do not change the transcript. Patch around the
  // connected scroll owner so opening a menu also preserves text selection.
  const retainedTranscript = preserveTranscript ? existingTranscript : null;
  const retainedTranscriptTop = retainedTranscript?.scrollTop;
  const nextSidebarRetention = currentSidebarRetention(data);
  const existingSidebar = document.querySelector('.chat-sidebar');
  const preserveSidebar = !runtimeRefresh && canRetainSidebar(renderedSidebarRetention, nextSidebarRetention) && Boolean(existingSidebar);
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
  app.className = `app-shell chat-first ${standaloneWorkspacePage ? 'workspace-standalone-shell' : ''} ${visiblePane === 'settings' ? 'settings-mode' : ''} ${sidebarWorkMode ? 'work-mode' : ''} ${state.sidebarOpen ? 'fallback-sidebar-open' : ''} ${state.searchPanel ? 'search-mode' : ''}`;
  const nextShellHtml = renderChatFirstShell({ data, native, selectedConversationId: state.selectedConversationId, settingsConversationReturn: state.settingsConversationReturn, composerDraft: state.composerDraft, composerAttachments: state.composerAttachments, temporaryConversation: state.temporaryConversation, chatSearch: state.chatSearch, searchResults: state.searchResults, searchPanel: state.searchPanel, searchCategory: state.searchCategory, searchHistory: state.searchHistory, searchHistoryOpen: state.searchHistoryOpen, searchLoadingMore: state.searchLoadingMore, profileOpen: state.profileOpen, sidebarOpen: state.sidebarOpen, railCollapsed: state.railCollapsed, sidebarWidth: state.sidebarWidth, settingsSection: state.settingsSection, settingsSearch: state.settingsSearch, settingsPicker: state.settingsPicker, productSettings: state.productSettings, conversationPreferences: state.conversationPreferences, personalizationDraft: state.personalizationDraft, personalizationDirty: state.personalizationDirty, memorySummaryNotice: state.memorySummaryNotice, modelServiceSettings: state.modelServiceSettings, modelProviderId: state.modelProviderId, modelServiceDraft: state.modelServiceDraft, modelCredentialDraft: state.modelCredentialDraft, modelCredentialVisible: state.modelCredentialVisible, modelCredentialEdited: state.modelCredentialEdited, modelSettingsSaving: state.modelSettingsSaving, modelSettingsTesting: state.modelSettingsTesting, modelSettingsNotice: state.modelSettingsNotice, modelSettingsError: state.modelSettingsError, openrouterRegistryStatus: state.openrouterRegistryStatus, openrouterRegistryRefreshing: state.openrouterRegistryRefreshing, openrouterRegistryNotice: state.openrouterRegistryNotice, openrouterRegistryError: state.openrouterRegistryError, usageLedger: state.usageLedger, usageSection: state.usageSection, contextSelectionRecords: state.contextSelectionRecords, diagnosticRecords: state.diagnosticRecords, invocationRecords: state.invocationRecords, privacyInventory: state.privacyInventory, localBackup: selectedBackupAreaState().localBackup, localBackupArea: selectedBackupAreaState().dataArea, backupAreaBusy: [...dataAreaStates.values()].some(owner => owner.localBackup?.working), accountSync: { ...state.accountSync, signInPending: accountSignInPending }, accountRecovery: state.accountRecovery, settingsCapabilities: state.settingsCapabilities, reminders: state.reminders, reminderNotificationPermission: state.reminderNotificationPermission, reminderNotificationBridge: state.reminderNotificationBridge, backgroundRuntime: state.backgroundRuntime, showArchived: state.showArchived, showDeleted: state.showDeleted, contextMenu: state.contextMenu, assistantMessageMenu: state.assistantMessageMenu, composerAddOpen: state.composerAddOpen, composerAddPage: state.composerAddPage, temporaryModelOpen: state.temporaryModelOpen, p6gModelPickerOpen: state.p6gModelPickerOpen, p6gCatalog: state.p6gCatalog, p6gGlobalDefault: state.p6gGlobalDefault, p6gSelection: state.p6gSelection, pendingNewConversationModelId: state.pendingNewConversationModelId, chatgptTask: state.chatgptTask, claudeTask: state.claudeTask, nanfengKnowledgeTask: state.nanfengKnowledgeTask, p6kTask: state.p6kTask, workMode: sidebarWorkMode, workspaces: state.workspaces, workPanel, pane: visiblePane, status: state.status, error: state.error, connection: state.connection, p6eAcceptance: state.p6eAcceptance, imageThumbnails: state.imageThumbnails, videoThumbnails: state.videoThumbnails, searchAttachmentPreviews: state.searchAttachmentPreviews, assistantImageSelections: state.assistantImageSelections, showScrollToLatest: !state.chatAtLatest, appearance: state.appearance, favoriteConversationIds: state.favoriteConversationIds, unreadConversationIds: state.unreadConversationIds, manualUnreadAtMs: state.manualUnreadAtMs, syncedConversationKeys: new Set(state.accountSync?.syncedConversationKeys || []), conversationFindOpen: state.conversationFindOpen, conversationFindQuery: state.conversationFindQuery, conversationFindMatches: state.conversationFindMatches, conversationFindIndex: state.conversationFindIndex, runtimeInfo: state.runtimeInfo, preserveTranscript: Boolean(retainedTranscript), preserveSidebar: Boolean(retainedSidebar) });
  const runtimePatched = runtimeRefresh && commitRuntimeChatShell(app, nextShellHtml);
  const shellPatched = !runtimeRefresh && commitRetainedChatShell(app, nextShellHtml, retainedTranscript, retainedSidebar);
  if (!runtimePatched && !shellPatched) {
    if (retainedTranscript) retainedTranscript.replaceWith(document.createComment('retained-transcript'));
    if (retainedSidebar) retainedSidebar.replaceWith(document.createComment('retained-sidebar'));
    app.innerHTML = nextShellHtml;
  }
  if (retainedTranscript && !shellPatched) {
    const slot = app.querySelector('[data-preserved-transcript-slot]');
    if (slot) slot.replaceWith(retainedTranscript);
    retainedTranscript.scrollTop = retainedTranscriptTop;
  }
  if (retainedSidebar) {
    if (!shellPatched) app.querySelector('[data-preserved-sidebar-slot]')?.replaceWith(retainedSidebar);
    patchRetainedSidebarRows();
  }
  if (!retainedTranscript) bindAssistantMessageMenuTriggers();
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
    // Fallback reinsertion restores its offset above. Fresh nodes use the
    // conversation owner; runtime patches keep the live viewport themselves.
    // Explicit bottom navigation still runs after the shell commit.
    if ((!retainedTranscript && !runtimePatched) || state.pendingChatScrollToLatestId || state.pendingChatSendScrollToLatestId) restoreChatScroll();
    restoreSidebarScroll();
    restoreSettingsScroll();
    if (state.searchPanel) restoreFullSearchScroll();
    if (state.dialog) {
      const replacement = focusedDialogId && state.dialog === focusedDialogOwner
        ? document.getElementById(focusedDialogId) : null;
      const focusTarget = replacement?.closest('.dialog') ? replacement
        : document.querySelector('.dialog input, .dialog textarea, .dialog select, .dialog button');
      focusTarget?.focus({ preventScroll: true });
      if (focusTarget === replacement && dialogSelection && typeof replacement.setSelectionRange === 'function') {
        replacement.setSelectionRange(...dialogSelection);
      }
    }
    // The menu is rendered after the retained transcript.  WebKit's default
    // focus scroll can otherwise move the surrounding document to the menu's
    // DOM position even though the visible menu itself is fixed.
    // The message-menu owner restores the exact control after late renders.
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
  scheduleComposerModelPricingRefresh();
  scheduleOrdinaryEgressPreview();
  updateOrdinaryEgressDisclosure();
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
      ? `<div class="video-preview-error" role="alert"><strong>${escape(preview.error)}</strong>${preview.displayName ? `<small>${escape(preview.displayName)} · MP4 · ${bytes(preview.byteCount)}</small>` : ''}</div>`
      : `<div class="video-preview-stage"><video class="video-preview-frame" autoplay playsinline preload="auto" data-video-preview data-action="toggle-video-playback" src="${escape(preview.mediaUrl)}" aria-label="${escape(preview.displayName)} 视频播放器"></video><div class="preview-playback-controls video-preview-controls" aria-label="视频播放控制"><button class="preview-playback-toggle" data-action="toggle-video-playback" aria-label="暂停视频">${icon('M7 5h3v14H7zM14 5h3v14h-3z', '暂停')}</button><time data-video-preview-elapsed>${formatDuration(preview.positionMillis || 0)}</time>${previewProgressRange('video', preview, '视频播放进度')}${previewVolumeControl('video')}<time data-video-preview-duration>${preview.durationMillis ? formatDuration(preview.durationMillis) : '--:--'}</time></div></div><p class="pdf-preview-note">${escape(preview.displayName)} · ${bytes(preview.byteCount)} · <span data-video-preview-duration-note>${preview.durationMillis ? formatDuration(preview.durationMillis) : '--:--'}</span></p>`;
  return `<div class="scrim attachment-preview-overlay"><section class="dialog image-preview-dialog" role="dialog" aria-modal="true" aria-label="视频预览">${previewTopActions('video', preview)}${content}</section></div>`;
}

function audioPreviewDialog() {
  const preview = state.audioPreview;
  if (!preview) return '';
  const content = preview.loading ? '<div class="media-preview-loading" role="status" aria-label="正在读取本地音频"></div>'
    : preview.error ? `<p class="image-preview-error audio-preview-error" role="alert">${escape(preview.error)}</p>`
      : `<div class="audio-preview-player"><audio autoplay preload="auto" data-audio-preview src="${escape(preview.mediaUrl)}" aria-label="${escape(preview.displayName)} 音频播放器"></audio><button class="audio-preview-toggle" data-action="toggle-audio-playback" aria-label="暂停音频">${icon('M7 5h3v14H7zM14 5h3v14h-3z', '暂停')}</button><div class="audio-preview-progress-row">${previewProgressRange('audio', preview, '音频播放进度')}${previewVolumeControl('audio')}</div><div class="audio-preview-times"><time data-audio-preview-elapsed>${formatDuration(preview.positionMillis || 0)}</time><time data-audio-preview-duration>${formatDuration(preview.durationMillis || 0)}</time></div></div>`;
  const identity = preview.attachmentId && preview.displayName
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
let imageThumbnailGeneration = 0;
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
  const owner = state;
  if (!retainObserved) {
    imageThumbnailObserver?.disconnect();
    imageThumbnailGeneration += 1;
    imageThumbnailObserver = null;
    imageThumbnailQueue.forEach(job => job.owner.imageThumbnailPending.delete(job.thumbnailKey));
    imageThumbnailQueue = [];
  }
  if (!native || state.imagePreview) return;
  const generation = imageThumbnailGeneration;
  const selector = composerOnly ? '.chat-composer [data-image-thumbnail]' : '[data-image-thumbnail]';
  const enqueue = element => {
    if (state !== owner || generation !== imageThumbnailGeneration || !element.isConnected) return;
    const attachmentId = element.dataset.imageThumbnail;
    const workspaceId = element.dataset.thumbnailWorkspaceId;
    const thumbnailKey = attachmentPreviewKey(workspaceId, attachmentId);
    if (!workspaceId || !attachmentId) return;
    if (state.imageThumbnails[thumbnailKey]) { hydrateImageThumbnail(workspaceId, attachmentId, state.imageThumbnails[thumbnailKey]); return; }
    if (state.imageThumbnailPending.has(thumbnailKey)) return;
    state.imageThumbnailPending.add(thumbnailKey);
    const job = () => {
      if (state !== owner || generation !== imageThumbnailGeneration || !element.isConnected) { owner.imageThumbnailPending.delete(thumbnailKey); return Promise.resolve(); }
      const temporaryId = owner.temporaryConversation?.temporaryId === workspaceId ? workspaceId : null;
      const command = temporaryId ? 'read_desktop_temporary_image_preview' : 'read_desktop_image_preview';
      const args = temporaryId ? { temporaryId, attachmentId, fullSize: false } : { workspaceId, attachmentId, fullSize: false };
      return withFullSearchDeadline(invoke(command, { args }, owner))
      .then(preview => {
        if (state !== owner) return;
        owner.imageThumbnails = { ...owner.imageThumbnails, [thumbnailKey]: preview };
        hydrateImageThumbnail(workspaceId, attachmentId, preview);
      })
      .catch(error => {
        if (state !== owner || error?.code === 'STALE_DATA_AREA') return;
        const preview = { error: '本地缩略图不可用' };
        owner.imageThumbnails = { ...owner.imageThumbnails, [thumbnailKey]: preview };
        hydrateImageThumbnail(workspaceId, attachmentId, preview);
      })
      .finally(() => { owner.imageThumbnailPending.delete(thumbnailKey); });
    };
    job.owner = owner;
    job.thumbnailKey = thumbnailKey;
    imageThumbnailQueue.push(job);
    pumpImageThumbnailReads();
  };
  const elements = [...document.querySelectorAll(selector)];
  if (!('IntersectionObserver' in globalThis)) { elements.slice(0, 12).forEach(enqueue); return; }
  if (!imageThumbnailObserver) {
    const observer = new IntersectionObserver(entries => {
      if (generation !== imageThumbnailGeneration) return;
      for (const entry of entries) if (entry.isIntersecting) {
        observer.unobserve(entry.target);
        enqueue(entry.target);
      }
    }, { root: state.searchPanel ? document.querySelector('[data-search-scroll-owner]') : null, rootMargin: '160px' });
    imageThumbnailObserver = observer;
  }
  elements.forEach(element => imageThumbnailObserver.observe(element));
}

function hydrateImageThumbnail(workspaceId, attachmentId, preview) {
  if (!preview) return;
  document.querySelectorAll('[data-image-thumbnail]').forEach(element => {
    if (element.dataset.imageThumbnail !== String(attachmentId) || element.dataset.thumbnailWorkspaceId !== String(workspaceId)) return;
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
  delete state.imageThumbnails[attachmentPreviewKey(element.dataset.thumbnailWorkspaceId, element.dataset.retryImageThumbnail)];
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
const videoThumbnailActiveReads = new Map();

function cancelVideoThumbnailJob(job) {
  if (!job || job.cancelled) return;
  job.cancelled = true;
  if (job.requestId != null) {
    void invokePreviewOwner(job.owner, 'cancel_desktop_video_thumbnail', { requestId: job.requestId }).catch(() => {});
  }
}

async function runVideoThumbnailJob(job) {
  try {
    const requestId = await invokePreviewOwner(job.owner, 'begin_desktop_video_thumbnail');
    job.requestId = requestId;
    if (job.cancelled || state !== job.owner) {
      await invokePreviewOwner(job.owner, 'cancel_desktop_video_thumbnail', { requestId }).catch(() => {});
      return;
    }
    const preview = await invokePreviewOwner(job.owner, 'read_desktop_video_thumbnail', {
      args: { workspaceId: job.workspaceId, attachmentId: job.attachmentId },
      requestId,
    });
    if (job.cancelled || state !== job.owner) return;
    job.owner.videoThumbnails = { ...job.owner.videoThumbnails, [job.thumbnailKey]: preview };
    hydrateVideoThumbnail(job.workspaceId, job.attachmentId, preview);
  } catch (error) {
    if (!job.cancelled && state === job.owner && error?.code !== 'STALE_DATA_AREA') {
      job.owner.videoThumbnails = {
        ...job.owner.videoThumbnails,
        [job.thumbnailKey]: { error: '本地视频缩略图不可用' },
      };
    }
  } finally {
    if (videoThumbnailActiveReads.get(job.thumbnailKey) === job) {
      videoThumbnailActiveReads.delete(job.thumbnailKey);
    }
    videoThumbnailReadsInFlight = Math.max(0, videoThumbnailReadsInFlight - 1);
    job.owner.videoThumbnailPending.delete(job.thumbnailKey);
    pumpVideoThumbnailReads();
    // The card may have returned while the cancelled native worker was still
    // releasing its decoder. Re-scan only then so that it is not left blank.
    const cardReturned = state === job.owner && job.cancelled && [...document.querySelectorAll('[data-video-thumbnail]')].some(element =>
      element.dataset.videoThumbnail === String(job.attachmentId)
      && element.dataset.thumbnailWorkspaceId === String(job.workspaceId));
    if (cardReturned) {
      queueMicrotask(scheduleVideoThumbnailReads);
    }
  }
}

function pumpVideoThumbnailReads() {
  while (videoThumbnailReadsInFlight < MAX_VIDEO_THUMBNAIL_READS && videoThumbnailReadQueue.length) {
    const job = videoThumbnailReadQueue.shift();
    if (state !== job.owner || job.cancelled || job.generation !== videoThumbnailGeneration) {
      job.owner.videoThumbnailPending.delete(job.thumbnailKey);
      continue;
    }
    videoThumbnailReadsInFlight += 1;
    videoThumbnailActiveReads.set(job.thumbnailKey, job);
    void runVideoThumbnailJob(job);
  }
}

function scheduleVideoThumbnailReads() {
  const owner = state;
  videoThumbnailObserver?.disconnect();
  videoThumbnailObserver = null;
  videoThumbnailGeneration += 1;
  const cards = native && !state.videoPreview ? [...document.querySelectorAll('[data-video-thumbnail]')] : [];
  const currentKeys = new Set(cards.map(card => attachmentPreviewKey(card.dataset.thumbnailWorkspaceId, card.dataset.videoThumbnail)));
  videoThumbnailReadQueue = videoThumbnailReadQueue.filter(job => {
    if (job.owner === owner && currentKeys.has(job.thumbnailKey)) {
      job.generation = videoThumbnailGeneration;
      return true;
    }
    job.cancelled = true;
    job.owner.videoThumbnailPending.delete(job.thumbnailKey);
    return false;
  });
  for (const [thumbnailKey, job] of videoThumbnailActiveReads) {
    if (job.owner !== owner || !currentKeys.has(thumbnailKey)) cancelVideoThumbnailJob(job);
  }
  if (!native || state.videoPreview) return;
  if (!cards.length) return;
  const generation = videoThumbnailGeneration;
  const enqueue = element => {
    if (state !== owner || generation !== videoThumbnailGeneration || !element.isConnected) return;
    const attachmentId = element.dataset.videoThumbnail;
    const workspaceId = element.dataset.thumbnailWorkspaceId;
    const thumbnailKey = attachmentPreviewKey(workspaceId, attachmentId);
    if (!workspaceId || !attachmentId) return;
    const cached = state.videoThumbnails[thumbnailKey];
    if (cached) { hydrateVideoThumbnail(workspaceId, attachmentId, cached); return; }
    if (state.videoThumbnailPending.has(thumbnailKey)) return;
    state.videoThumbnailPending.add(thumbnailKey);
    videoThumbnailReadQueue.push({ owner, attachmentId, workspaceId, thumbnailKey, generation, requestId: null, cancelled: false });
    pumpVideoThumbnailReads();
  };
  if (!('IntersectionObserver' in globalThis)) {
    cards.slice(0, 9).forEach(enqueue);
    return;
  }
  const root = state.searchPanel
    ? document.querySelector('[data-search-scroll-owner]')
    : document.querySelector('.chat-scroll[data-scroll-owner="message-list"]');
  const observer = new IntersectionObserver(entries => {
    if (generation !== videoThumbnailGeneration) return;
    entries.filter(entry => entry.isIntersecting).forEach(entry => {
      observer.unobserve(entry.target);
      enqueue(entry.target);
    });
  }, { root, rootMargin: '160px 0px' });
  videoThumbnailObserver = observer;
  cards.forEach(card => observer.observe(card));
}

function hydrateVideoThumbnail(workspaceId, attachmentId, preview) {
  if (!preview?.dataUrl) return;
  document.querySelectorAll('[data-video-thumbnail]').forEach(element => {
    if (element.dataset.videoThumbnail !== String(attachmentId) || element.dataset.thumbnailWorkspaceId !== String(workspaceId)) return;
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

function hydrateAttachmentCardPreview(workspaceId, attachmentId, preview) {
  document.querySelectorAll('[data-search-attachment-preview], [data-chat-attachment-preview]').forEach(element => {
    const requestedId = element.dataset.searchAttachmentPreview || element.dataset.chatAttachmentPreview;
    if (requestedId !== String(attachmentId) || element.dataset.thumbnailWorkspaceId !== String(workspaceId)) return;
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
    if (state !== job.owner || job.generation !== searchAttachmentPreviewGeneration) {
      job.owner.searchAttachmentPreviewPending.delete(job.previewKey);
      continue;
    }
    searchAttachmentPreviewReadsInFlight += 1;
    invoke('read_desktop_search_attachment_preview', { args: { workspaceId: job.workspaceId, attachmentId: job.attachmentId } }, job.owner)
      .then(preview => {
        if (state !== job.owner) return;
        job.owner.searchAttachmentPreviews = { ...job.owner.searchAttachmentPreviews, [job.previewKey]: preview };
        // Renders replace DOM nodes while the same read remains in flight.
        // Match current nodes by both owners instead of discarding useful work
        // solely because the render generation changed.
        hydrateAttachmentCardPreview(job.workspaceId, job.attachmentId, preview);
      })
      .catch(error => {
        if (state !== job.owner || error?.code === 'STALE_DATA_AREA') return;
        job.owner.searchAttachmentPreviews = { ...job.owner.searchAttachmentPreviews, [job.previewKey]: { error: '本地附件预览不可用' } };
      })
      .finally(() => {
        searchAttachmentPreviewReadsInFlight = Math.max(0, searchAttachmentPreviewReadsInFlight - 1);
        job.owner.searchAttachmentPreviewPending.delete(job.previewKey);
        pumpSearchAttachmentPreviewReads();
      });
  }
}

function scheduleAttachmentCardPreviewReads() {
  const owner = state;
  searchAttachmentPreviewObserver?.disconnect();
  searchAttachmentPreviewObserver = null;
  searchAttachmentPreviewGeneration += 1;
  searchAttachmentPreviewQueue.forEach(job => job.owner.searchAttachmentPreviewPending.delete(job.previewKey));
  searchAttachmentPreviewQueue = [];
  if (!native) return;
  const cards = [...document.querySelectorAll('[data-search-attachment-preview], [data-chat-attachment-preview]')];
  if (!cards.length) return;
  const generation = searchAttachmentPreviewGeneration;
  const enqueue = element => {
    if (state !== owner || generation !== searchAttachmentPreviewGeneration || !element.isConnected) return;
    const attachmentId = element.dataset.searchAttachmentPreview || element.dataset.chatAttachmentPreview;
    const workspaceId = element.dataset.thumbnailWorkspaceId;
    if (!workspaceId || !attachmentId) return;
    const previewKey = attachmentPreviewKey(workspaceId, attachmentId);
    const cached = state.searchAttachmentPreviews[previewKey];
    if (cached) { hydrateAttachmentCardPreview(workspaceId, attachmentId, cached); return; }
    if (state.searchAttachmentPreviewPending.has(previewKey)) return;
    state.searchAttachmentPreviewPending.add(previewKey);
    searchAttachmentPreviewQueue.push({ owner, attachmentId, workspaceId, previewKey, generation });
    pumpSearchAttachmentPreviewReads();
  };
  if (!('IntersectionObserver' in globalThis)) {
    cards.slice(0, 12).forEach(enqueue);
    return;
  }
  const root = state.searchPanel
    ? document.querySelector('[data-search-scroll-owner]')
    : document.querySelector('.chat-scroll[data-scroll-owner="message-list"]');
  const observer = new IntersectionObserver(entries => {
    if (generation !== searchAttachmentPreviewGeneration) return;
    entries.filter(entry => entry.isIntersecting).forEach(entry => {
      observer.unobserve(entry.target);
      enqueue(entry.target);
    });
  }, { root, rootMargin: '160px 0px' });
  searchAttachmentPreviewObserver = observer;
  cards.forEach(card => observer.observe(card));
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

function imagePreviewPayloadKey(dataArea, workspaceId, attachmentId) {
  return `${dataArea}:${workspaceId}:${attachmentId}`;
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

function loadImagePreviewPayload(owner, workspaceId, attachmentId) {
  const key = imagePreviewPayloadKey(owner.dataArea, workspaceId, attachmentId);
  const cached = imagePreviewPayloadCache.get(key);
  if (cached) {
    retainImagePreviewPayload(key, cached);
    return cached;
  }
  const temporaryId = owner.temporaryConversation?.temporaryId === workspaceId ? workspaceId : null;
  const command = temporaryId ? 'read_desktop_temporary_image_preview' : 'read_desktop_image_preview';
  const args = temporaryId ? { temporaryId, attachmentId, fullSize: true } : { workspaceId, attachmentId, fullSize: true };
  const request = invoke(command, { args }, owner)
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

function warmImagePreviewNeighbors(preview, owner) {
  if (!preview?.dataUrl || !preview.workspaceId || !preview.attachmentId) return;
  const navigation = imagePreviewNavigation(preview.imageIds, preview.attachmentId);
  const neighbors = [navigation.nextId, navigation.previousId].filter(Boolean);
  if (!neighbors.length) return;
  const warm = () => {
    if (state !== owner || state.imagePreview?.attachmentId !== preview.attachmentId || state.imagePreview?.workspaceId !== preview.workspaceId) return;
    neighbors.forEach(attachmentId => { void loadImagePreviewPayload(owner, preview.workspaceId, attachmentId).catch(() => {}); });
  };
  if (typeof requestIdleCallback === 'function') requestIdleCallback(warm, { timeout: 700 });
  else setTimeout(warm, 0);
}

function cancelImagePreviewLoad() {
  imagePreviewLoadRequest = null;
}

async function openImagePreview(attachmentId, workspaceId = state.current?.summary?.id, imageIds = relatedImageIds(currentConversation(), attachmentId)) {
  if (!native || !workspaceId || !attachmentId) return;
  const owner = state;
  const normalizedAttachmentId = String(attachmentId);
  const normalizedImageIds = Array.isArray(imageIds) ? imageIds.map(String) : [];
  const safeImageIds = normalizedImageIds.includes(normalizedAttachmentId) ? normalizedImageIds : [normalizedAttachmentId];
  if (imagePreviewLoadRequest?.owner === owner && imagePreviewLoadRequest.attachmentId === normalizedAttachmentId && imagePreviewLoadRequest.workspaceId === workspaceId) return;
  const previousPreview = state.imagePreview?.dataUrl ? state.imagePreview : null;
  const request = { owner, attachmentId: normalizedAttachmentId, workspaceId, imageIds: safeImageIds };
  imagePreviewLoadRequest = request;
  // Keep the current decoded frame in place while the next original is read.
  // Initial opening still has an honest loading state.
  if (!previousPreview) {
    state.imagePreview = { loading: true, ...request };
    render();
  }
  try {
    const result = await loadImagePreviewPayload(owner, workspaceId, normalizedAttachmentId);
    if (imagePreviewLoadRequest !== request || state !== owner) {
      if (imagePreviewLoadRequest === request) imagePreviewLoadRequest = null;
      return;
    }
    imagePreviewLoadRequest = null;
    state.imagePreview = { ...result, ...request, zoom: 1, panX: 0, panY: 0, navigationError: '' };
  } catch (error) {
    if (imagePreviewLoadRequest !== request || state !== owner) {
      if (imagePreviewLoadRequest === request) imagePreviewLoadRequest = null;
      return;
    }
    imagePreviewLoadRequest = null;
    // A failed adjacent read must not blank the image already being viewed.
    state.imagePreview = previousPreview
      ? { ...previousPreview, navigationError: '相邻图片不可用或校验失败。' }
      : { error: '本地原图不可用或校验失败。', ...request };
  }
  render();
  if (state.imagePreview?.dataUrl) warmImagePreviewNeighbors(state.imagePreview, owner);
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

let pdfPreviewPreparation = Promise.resolve();
const invokePreviewOwner = (owner, command, args = {}) =>
  invokeForAuthorizedDataAreaOperation(tauriBridge, command, args, owner);
function cancelPdfPreviewLoad() {
  const owner = state;
  const requestId = state.pdfPreview?.request?.id;
  state.pdfPreview = null;
  if (native && requestId) void invokePreviewOwner(owner, 'cancel_desktop_pdf_preview', { requestId }).catch(() => {});
}

async function openPdfPreview(attachmentId, pageNumber = undefined, workspaceId = state.current?.summary?.id) {
  if (!native || !workspaceId || !attachmentId) return;
  const owner = state;
  cancelPdfPreviewLoad();
  const request = {};
  state.pdfPreview = { loading: true, request }; render();
  try {
    // Serialize only native ID allocation. Old readers are cancelled separately;
    // their slow render must not delay the newest reader's cancellation handle.
    const preparation = pdfPreviewPreparation.catch(() => {}).then(() => invokePreviewOwner(owner, 'begin_desktop_pdf_preview'));
    pdfPreviewPreparation = preparation;
    request.id = await preparation;
    if (state !== owner || state.pdfPreview?.request !== request) return;
    const preview = await invoke('read_desktop_pdf_preview', { requestId: request.id, args: { workspaceId, attachmentId, pageNumber } }, owner);
    if (state !== owner || state.pdfPreview?.request !== request) return;
    state.pdfPreview = { ...preview, workspaceId };
  } catch (error) {
    if (state !== owner || state.pdfPreview?.request !== request) return;
    state.pdfPreview = { error: '本地 PDF 不可用或校验失败。' };
  } finally {
    // Also releases a reservation if the panel closed before begin returned or
    // the read was rejected before its Rust worker started.
    if (request.id) void invokePreviewOwner(owner, 'cancel_desktop_pdf_preview', { requestId: request.id }).catch(() => {});
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
  const owner = state;
  const request = {};
  state.videoPreview = { loading: true, request, workspaceId, attachmentId }; render();
  try {
    const preview = await invoke('read_desktop_video_preview', { args: { workspaceId, attachmentId, positionMillis } }, owner);
    if (state !== owner || state.videoPreview?.request !== request) return;
    if (!preview?.attachmentId || !preview?.mediaUrl || !Number.isFinite(Number(preview.byteCount)) || !Number.isFinite(Number(preview.durationMillis))) throw new Error('本地视频预览状态无效');
    state.videoPreview = { ...preview, workspaceId };
  }
  catch (error) { if (state !== owner || state.videoPreview?.request !== request) return; state.videoPreview = { error: '本地视频不可用、已损坏或校验失败。' }; }
  render();
  const restorePosition = Math.max(0, Number(state.videoPreview?.positionMillis) || 0) / 1000;
  queueMicrotask(() => {
    if (state !== owner || state.videoPreview?.attachmentId !== attachmentId || state.videoPreview?.workspaceId !== workspaceId) return;
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
      if (state !== owner || !video.isConnected || state.videoPreview?.attachmentId !== attachmentId || state.videoPreview?.workspaceId !== workspaceId) return;
      const milliseconds = Math.round(video.currentTime * 1000);
      syncVideoUi();
      if (milliseconds > 0 && milliseconds < Math.round(video.duration * 1000)) {
        state.videoPreview = { ...state.videoPreview, lastPlaybackPositionMillis: milliseconds };
      }
    });
    video.addEventListener('play', syncVideoUi);
    video.addEventListener('pause', syncVideoUi);
    let errorHandled = false;
    const handleVideoError = () => {
      if (errorHandled) return;
      errorHandled = true;
      const active = state.videoPreview;
      if (state !== owner || !active || active.attachmentId !== attachmentId || active.workspaceId !== workspaceId) return;
      state.videoPreview = { ...active, mediaUrl: '', error: '当前系统无法解码此视频。' };
      render();
    };
    video.addEventListener('error', handleVideoError, { once: true });
    if (video.error) handleVideoError();
  });
}

async function closeVideoPreview() {
  const owner = state;
  const video = document.querySelector('[data-video-preview]');
  const preview = state.videoPreview;
  const currentPosition = Math.round((video?.currentTime || 0) * 1000);
  const positionMillis = preview?.lastPlaybackPositionMillis || currentPosition;
  state.videoPreview = null;
  if (video) {
    video.pause();
    video.removeAttribute('src');
    video.load();
  }
  render();
  if (!preview?.attachmentId || !preview.workspaceId) return;
  try { await invokePreviewOwner(owner, 'save_desktop_video_preview_position', { args: { workspaceId: preview.workspaceId, attachmentId: preview.attachmentId, positionMillis } }); }
  catch (_) { /* Closing an overlay never waits for a non-critical resume write. */ }
}

async function openAudioPreview(attachmentId, positionMillis = undefined, workspaceId = state.current?.summary?.id) {
  if (!native || !workspaceId || !attachmentId) return;
  const owner = state;
  const request = {};
  state.audioPreview = { loading: true, request }; render();
  try {
    const preview = await invoke('read_desktop_audio_preview', { args: { workspaceId, attachmentId, positionMillis } }, owner);
    if (state !== owner || state.audioPreview?.request !== request) return;
    if (!preview?.attachmentId || !preview?.mediaUrl) throw new Error('本地音频预览状态无效');
    state.audioPreview = { ...preview, workspaceId };
  }
  catch (_) { if (state !== owner || state.audioPreview?.request !== request) return; state.audioPreview = { error: '本地音频不可用、已损坏或校验失败。' }; }
  render();
  queueMicrotask(() => {
    if (state !== owner || state.audioPreview?.attachmentId !== attachmentId || state.audioPreview?.workspaceId !== workspaceId) return;
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
    audio?.addEventListener('timeupdate', () => { if (state !== owner || !audio.isConnected || state.audioPreview?.attachmentId !== attachmentId || state.audioPreview?.workspaceId !== workspaceId) return; const ms = Math.round(audio.currentTime * 1000); syncAudioUi(); if (ms > 0 && ms < Math.round(audio.duration * 1000)) state.audioPreview = { ...state.audioPreview, lastPlaybackPositionMillis: ms }; });
    audio?.addEventListener('play', syncAudioUi);
    audio?.addEventListener('pause', syncAudioUi);
    audio?.addEventListener('error', () => {
      const active = state.audioPreview;
      if (state !== owner || !active || active.attachmentId !== attachmentId || active.workspaceId !== workspaceId) return;
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
  const durationNote = kind === 'video' ? document.querySelector('[data-video-preview-duration-note]') : null;
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
  if (durationNote) durationNote.textContent = formatDuration(durationMillis);
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
  const owner = state;
  const audio = document.querySelector('[data-audio-preview]'); const preview = state.audioPreview;
  if (!preview) return;
  const positionMillis = preview.lastPlaybackPositionMillis || Math.round((audio?.currentTime || 0) * 1000);
  state.audioPreview = null;
  if (audio) {
    audio.pause();
    audio.removeAttribute('src');
    audio.load();
  }
  render();
  if (!preview.attachmentId || !preview.workspaceId) return;
  try { await invokePreviewOwner(owner, 'save_desktop_audio_preview_position', { args: { workspaceId: preview.workspaceId, attachmentId: preview.attachmentId, positionMillis } }); }
  catch (_) { /* Closing an overlay never depends on a non-critical resume write. */ }
}

async function openTextPreview(attachmentId, workspaceId = state.current?.summary?.id) {
  if (!native || !workspaceId || !attachmentId) return;
  const owner = state;
  const request = {};
  state.textPreview = { loading: true, request }; render();
  try {
    const preview = await invoke('read_desktop_text_preview', { args: { workspaceId, attachmentId } }, owner);
    if (state !== owner || state.textPreview?.request !== request) return;
    state.textPreview = { ...preview, workspaceId };
  } catch (_) {
    if (state !== owner || state.textPreview?.request !== request) return;
    state.textPreview = { error: '本地文本不可用、编码无效或校验失败。' };
  }
  render();
}

let archivePreviewPreparation = Promise.resolve();
function cancelArchivePreviewLoad() {
  const owner = state;
  const requestId = state.archivePreview?.request?.id;
  state.archivePreview = null;
  if (native && requestId) void invokePreviewOwner(owner, 'cancel_desktop_archive_preview', { requestId }).catch(() => {});
}

function showArchiveDirectory(directory = state.archivePreview?.directory || '') {
  const preview = state.archivePreview;
  if (!preview) return;
  cancelArchivePreviewLoad();
  state.archivePreview = { ...preview, request: null, directory, entry: null };
  render();
}

async function reserveArchivePreviewRequest(request, owner) {
  const preparation = archivePreviewPreparation.catch(() => {}).then(() => invokePreviewOwner(owner, 'begin_desktop_archive_preview'));
  archivePreviewPreparation = preparation;
  request.id = await preparation;
  return state === owner && state.archivePreview?.request === request;
}

async function openArchivePreview(attachmentId, workspaceId = state.current?.summary?.id) {
  if (!native || !workspaceId || !attachmentId) return;
  const owner = state;
  cancelArchivePreviewLoad();
  const request = {};
  state.archivePreview = { loading: true, request, attachmentId, workspaceId, directory: '', entry: null }; render();
  try {
    if (!await reserveArchivePreviewRequest(request, owner)) return;
    const preview = await invoke('read_desktop_archive_preview', { requestId: request.id, args: { workspaceId, attachmentId } }, owner);
    if (state !== owner || state.archivePreview?.request !== request) return;
    state.archivePreview = { ...preview, workspaceId, directory: '', entry: null };
  } catch (_) {
    if (state !== owner || state.archivePreview?.request !== request) return;
    state.archivePreview = { error: '本地压缩包不可用、结构无效或校验失败。' };
  } finally {
    if (request.id) void invokePreviewOwner(owner, 'cancel_desktop_archive_preview', { requestId: request.id }).catch(() => {});
  }
  render();
}

async function openArchiveEntryPreview(entryPath, pageNumber = 1) {
  const preview = state.archivePreview;
  if (!native || !preview?.workspaceId || !preview?.attachmentId || !entryPath) return;
  const owner = state;
  cancelArchivePreviewLoad();
  const request = {};
  state.archivePreview = { ...preview, request, entry: { loading: true } }; render();
  try {
    if (!await reserveArchivePreviewRequest(request, owner)) return;
    const entry = await invoke('read_desktop_archive_entry_preview', { requestId: request.id, args: { workspaceId: preview.workspaceId, attachmentId: preview.attachmentId, entryPath, pageNumber } }, owner);
    if (state !== owner || state.archivePreview?.request !== request) return;
    state.archivePreview = { ...state.archivePreview, request: null, entry };
  } catch (_) {
    if (state !== owner || state.archivePreview?.request !== request) return;
    state.archivePreview = { ...state.archivePreview, entry: { error: 'ZIP 内文件不可用、路径不安全、内容损坏或超过预览上限。' } };
  } finally {
    if (request.id) void invokePreviewOwner(owner, 'cancel_desktop_archive_preview', { requestId: request.id }).catch(() => {});
  }
  render();
}

function safeAttachmentSaveName(value) {
  const leaf = String(value || '附件').split(/[\\/]/).pop() || '附件';
  return leaf.replace(/[\u0000-\u001f:]/g, '-').slice(0, 180) || '附件';
}

async function saveAttachmentCopy({ workspaceId, attachmentId, displayName }) {
  if (!native || !workspaceId || !attachmentId) return false;
  const owner = state;
  const defaultPath = safeAttachmentSaveName(displayName);
  const extension = defaultPath.includes('.') ? defaultPath.split('.').pop().toLowerCase() : '';
  const selectedPath = await dialogInvoke('save', { defaultPath, ...(extension ? { filters: [{ name: '附件副本', extensions: [extension] }] } : {}) });
  if (!selectedPath) return false;
  await invokePreviewOwner(owner, 'export_desktop_attachment_to_selected_path', { args: { workspaceId, attachmentId, selectedPath } });
  owner.status = `已保存经 hash 回读的附件副本“${defaultPath}”。`;
  owner.error = '';
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

function preserveSearchInputFocus() {
  const input = document.activeElement;
  if (!['chat-search', 'full-search-input'].includes(input?.id)) return;
  const owner = state;
  const searchPanel = owner.searchPanel;
  const dialog = owner.dialog;
  const selection = [input.selectionStart, input.selectionEnd, input.selectionDirection];
  queueMicrotask(() => {
    if (state !== owner || owner.searchPanel !== searchPanel || owner.dialog !== dialog) return;
    const next = document.getElementById(input.id);
    if (!next || next === input || !next.isConnected) return;
    if (document.activeElement !== document.body && document.activeElement !== next) return;
    next.focus({ preventScroll: true });
    if (Number.isInteger(selection[0]) && Number.isInteger(selection[1])) next.setSelectionRange(...selection);
  });
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
  button.innerHTML = icon(icons.check, '已复制');
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
  const previewKey = attachmentPreviewKey(state.current?.summary?.id, attachmentId);
  const duration = state.videoThumbnails?.[previewKey]?.durationMillis || state.searchAttachmentPreviews?.[previewKey]?.durationMillis;
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
const pdfKnowledge = createPdfKnowledgeController({ getState: () => state, native, invoke, dialogInvoke, render, refresh, escape, document });
const webKnowledge = createWebKnowledgeController({ getState: () => state, native, invoke, render, refresh, escape, document });
app.addEventListener('click', event => { void webKnowledge.handle(event); }, true);
app.addEventListener('change', event => { if (event.target.matches?.('[data-action=select-web-knowledge-task]')) void webKnowledge.handle(event); }, true);
app.addEventListener('click', event => { void pdfKnowledge.handle(event); }, true);
app.addEventListener('change', event => { if (event.target.matches?.('[data-action=select-pdf-knowledge-task]')) void pdfKnowledge.handle(event); }, true);


const ordinaryComposerDraftQueue = createDraftWriteQueue({
  routeKey: args => JSON.stringify([args.dataArea, args.workspaceId, args.conversationId ?? null]),
  prepare: async args => {
    if (args.workspaceId) return args;
    // Initialization is part of the first explicit input, including diagnostic
    // mode. A failed initialization retains the same snapshot for a later retry.
    const projection = await tauriBridge.invoke('ensure_desktop_local_workspace', {}, {
      headers: { 'x-nanfeng-data-area': args.dataArea },
    });
    if (state.dataArea === args.dataArea && !state.current) {
      state.current = projection;
      state.workspaces = [projection.summary];
      state.newConversationDraftWorkspaceId = projection.summary.id;
      render();
    }
    return { ...args, workspaceId: projection.summary.id };
  },
  write: ({ dataArea, ...args }) => tauriBridge.invoke('save_desktop_conversation_draft', { args }, {
    headers: { 'x-nanfeng-data-area': dataArea },
  }),
});
let ordinaryComposerDraftTimer = null;
let scheduledOrdinaryComposerDraft = null;
let composerDraftRouteGeneration = 0;
let pendingOrdinaryComposerDraftRead = null;
let newConversationDraftExpiryTimer = null;
function armNewConversationDraftExpiry(expiresAtMs) {
  if (newConversationDraftExpiryTimer) clearTimeout(newConversationDraftExpiryTimer);
  newConversationDraftExpiryTimer = null;
  if (!expiresAtMs || state.selectedConversationId || state.temporaryConversation) return;
  const generation = composerDraftRouteGeneration;
  const workspaceId = state.current?.summary?.id;
  newConversationDraftExpiryTimer = setTimeout(() => {
    newConversationDraftExpiryTimer = null;
    if (generation !== composerDraftRouteGeneration || state.selectedConversationId || state.current?.summary?.id !== workspaceId) return;
    void loadOrdinaryComposerDraft().then(render).catch(error => {
      state.error = `新对话草稿未读取：${String(error)}`;
      render();
    });
  }, Math.max(0, expiresAtMs - Date.now()));
}
function ordinaryComposerDraftArgs() {
  if (!native || state.temporaryConversation) return null;
  return {
    dataArea: state.dataArea,
    workspaceId: state.current?.summary?.id || null,
    conversationId: state.selectedConversationId || null,
    text: state.composerDraft,
    attachmentIds: state.composerAttachments.map(item => item.id),
    modelId: state.selectedConversationId ? null : state.pendingNewConversationModelId,
    toneOverride: state.selectedConversationId ? null : state.conversationPreferences.toneOverride,
    webSearchOverride: state.selectedConversationId ? null : state.conversationPreferences.webSearchOverride,
  };
}
function persistOrdinaryComposerDraft() {
  const args = arguments.length ? arguments[0] : ordinaryComposerDraftArgs();
  if (!args) return Promise.resolve();
  return ordinaryComposerDraftQueue.save(args);
}
function resetComposerPresentation() {
  composerDraftRouteGeneration += 1;
  armNewConversationDraftExpiry(null);
  state.composerDraft = '';
  state.composerAttachments = [];
}
function resetNewConversationComposer() {
  resetComposerPresentation();
  if (!native || state.temporaryConversation || state.selectedConversationId) return;
  void loadOrdinaryComposerDraft().then(render).catch(error => {
    state.error = `新对话草稿未读取：${String(error)}`;
    render();
  });
}
function scheduleOrdinaryComposerDraft(changedField = 'text', removedAttachmentId = null) {
  composerDraftRouteGeneration += 1;
  armNewConversationDraftExpiry(null);
  const generation = composerDraftRouteGeneration;
  const args = ordinaryComposerDraftArgs();
  const owner = state;
  if (!args) return;
  const pending = pendingOrdinaryComposerDraftRead;
  if (pending && pending.owner === state && pending.args.workspaceId === args.workspaceId
      && pending.args.conversationId === args.conversationId) {
    pending.changedFields.add(changedField);
    if (changedField === 'attachments') {
      if (removedAttachmentId) pending.removedAttachmentIds.add(removedAttachmentId);
      else for (const id of args.attachmentIds) pending.removedAttachmentIds.delete(id);
    }
    pending.editedArgs = args;
    return;
  }
  scheduledOrdinaryComposerDraft = args;
  if (ordinaryComposerDraftTimer) clearTimeout(ordinaryComposerDraftTimer);
  ordinaryComposerDraftTimer = setTimeout(() => {
    ordinaryComposerDraftTimer = null;
    const scheduled = scheduledOrdinaryComposerDraft;
    scheduledOrdinaryComposerDraft = null;
    void persistOrdinaryComposerDraft(scheduled).then(draft => {
      if (owner !== state || generation !== composerDraftRouteGeneration) return;
      armNewConversationDraftExpiry(draft?.expiresAtMs);
      // A selection-only write can discover that the unbound text has just expired.
      // The native owner has already cleared it; do not keep the stale text visible.
      if (!scheduled.conversationId && draft && draft.text !== scheduled.text) {
        state.composerDraft = draft.text;
        render();
      }
    }).catch(error => {
      if (owner !== state || generation !== composerDraftRouteGeneration) return;
      state.error = `普通会话草稿未保存：${String(error)}`;
      render();
    });
  }, 180);
}
async function flushOrdinaryComposerDraft() {
  const pending = pendingOrdinaryComposerDraftRead;
  if (pending?.changedFields.size) {
    const result = await pending.finished;
    if (result.error) throw result.error;
  }
  if (ordinaryComposerDraftTimer) {
    clearTimeout(ordinaryComposerDraftTimer);
    ordinaryComposerDraftTimer = null;
  }
  const scheduled = scheduledOrdinaryComposerDraft;
  scheduledOrdinaryComposerDraft = null;
  if (scheduled) {
    await persistOrdinaryComposerDraft(scheduled);
  }
  await ordinaryComposerDraftQueue.flush();
}
async function loadOrdinaryComposerDraft({ preferUnbound = false } = {}) {
  const owner = state;
  const args = ordinaryComposerDraftArgs();
  if (!args?.workspaceId) return;
  const routeGeneration = ++composerDraftRouteGeneration;
  try {
    await flushOrdinaryComposerDraft();
    if (owner !== state || routeGeneration !== composerDraftRouteGeneration) return;
    let finishRead;
    const pending = {
      owner, args, changedFields: new Set(), removedAttachmentIds: new Set(), editedArgs: null,
      finished: new Promise(resolve => { finishRead = resolve; }),
    };
    pendingOrdinaryComposerDraftRead = pending;
    let readError = null;
    try {
    const recovered = await readComposerDraftRoute({
      workspaceId: args.workspaceId, conversationId: args.conversationId, preferUnbound,
      readDraft: (workspaceId, conversationId) => invoke('read_desktop_conversation_draft', { workspaceId, conversationId }, owner),
    });
    if (pending.changedFields.size) {
      if (recovered.conversationId !== args.conversationId) throw new Error('草稿读取期间范围改变；未覆盖已有草稿');
      const draft = recovered.draft || {};
      const changed = pending.changedFields;
      const edited = pending.editedArgs;
      const recoveredAttachmentIds = (draft.attachments || []).map(item => item.id);
      const mergedAttachmentIds = changed.has('attachments')
        ? [...new Set([...recoveredAttachmentIds, ...edited.attachmentIds])]
          .filter(id => !pending.removedAttachmentIds.has(id))
        : recoveredAttachmentIds;
      const merged = {
        dataArea: owner.dataArea,
        workspaceId: args.workspaceId, conversationId: args.conversationId,
        text: changed.has('text') ? edited.text : draft.text || '',
        attachmentIds: mergedAttachmentIds,
        modelId: changed.has('model') ? edited.modelId : draft.modelId || null,
        toneOverride: changed.has('tone') ? edited.toneOverride : draft.toneOverride || null,
        webSearchOverride: changed.has('web') ? edited.webSearchOverride : draft.webSearchOverride ?? null,
      };
      if (pendingOrdinaryComposerDraftRead === pending) pendingOrdinaryComposerDraftRead = null;
      const editGeneration = composerDraftRouteGeneration;
      const stillVisible = owner === state && !state.temporaryConversation
        && state.current?.summary?.id === args.workspaceId
        && (state.selectedConversationId || null) === args.conversationId;
      if (stillVisible) {
        const attachmentsById = new Map([...(draft.attachments || []), ...state.composerAttachments]
          .map(item => [item.id, item]));
        state.composerDraft = merged.text;
        state.composerAttachments = merged.attachmentIds.map(id => attachmentsById.get(id)).filter(Boolean);
        if (!state.selectedConversationId) {
          state.pendingNewConversationModelId = merged.modelId;
          state.conversationPreferences = {
            revision: 0, toneOverride: merged.toneOverride,
            webSearchOverride: merged.webSearchOverride,
          };
        }
        render();
      }
      // A data-area switch may already have changed the visible owner. Keep the
      // captured area on this local write instead of resolving it from state.
      const saved = await persistOrdinaryComposerDraft(merged);
      if (owner !== state || editGeneration !== composerDraftRouteGeneration || state.temporaryConversation
          || state.current?.summary?.id !== args.workspaceId || (state.selectedConversationId || null) !== args.conversationId) return;
      state.composerDraft = saved?.text || '';
      state.composerAttachments = Array.isArray(saved?.attachments) ? saved.attachments : [];
      if (!state.selectedConversationId) {
        state.pendingNewConversationModelId = saved?.modelId || null;
        state.conversationPreferences = {
          revision: 0, toneOverride: saved?.toneOverride || null,
          webSearchOverride: typeof saved?.webSearchOverride === 'boolean' ? saved.webSearchOverride : null,
        };
      }
      armNewConversationDraftExpiry(saved?.expiresAtMs);
      return;
    }
    if (owner !== state || routeGeneration !== composerDraftRouteGeneration || state.temporaryConversation || state.current?.summary?.id !== args.workspaceId || (state.selectedConversationId || null) !== args.conversationId) return;
    const { draft } = recovered;
    if (recovered.conversationId !== args.conversationId) {
      state.selectedConversationId = recovered.conversationId;
      state.newConversationDraftWorkspaceId = args.workspaceId;
      state.p6gSelection = null;
      state.pendingNewConversationModelId = null;
      state.conversationPreferences = { revision: 0, toneOverride: null, webSearchOverride: null };
    }
    if (!draft) {
      armNewConversationDraftExpiry(null);
      state.composerDraft = '';
      state.composerAttachments = [];
      if (!state.selectedConversationId) {
        state.pendingNewConversationModelId = null;
        state.conversationPreferences = { revision: 0, toneOverride: null, webSearchOverride: null };
      }
      return;
    }
    state.composerDraft = draft.text || '';
    state.composerAttachments = Array.isArray(draft.attachments) ? draft.attachments : [];
    if (!state.selectedConversationId) {
      state.pendingNewConversationModelId = draft.modelId || null;
      state.conversationPreferences = {
        revision: 0,
        toneOverride: draft.toneOverride || null,
        webSearchOverride: typeof draft.webSearchOverride === 'boolean' ? draft.webSearchOverride : null,
      };
    }
    armNewConversationDraftExpiry(draft.expiresAtMs);
    } catch (error) { readError = error; throw error; }
    finally {
      if (pendingOrdinaryComposerDraftRead === pending) pendingOrdinaryComposerDraftRead = null;
      finishRead({ error: readError });
    }
  } catch (error) {
    if (owner !== state || error?.code === 'STALE_DATA_AREA') return;
    throw error;
  }
}
function writeComposerAttachments(removedAttachmentId = null) { scheduleOrdinaryComposerDraft('attachments', removedAttachmentId); }

// Composer drafts have exactly one route owner. This listener runs after the route action has
// selected its destination and never consults browser storage, eliminating click-order races.
app.addEventListener('click', event => {
  const action = event.target.closest?.('[data-action]')?.dataset.action;
  if (action === 'new-chat') {
    state.newConversationDraftWorkspaceId = state.current?.summary?.id || null;
    void flushOrdinaryComposerDraft();
    resetNewConversationComposer();
    render();
    return;
  }
  if (action !== 'select-chat') return;
  state.newConversationDraftWorkspaceId = null;
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

function captureComposerAttachmentRoute(owner = state) {
  return {
    workspaceId: owner.current?.summary?.id ?? null,
    conversationId: owner.selectedConversationId ?? null,
    temporaryId: owner.temporaryConversation?.temporaryId ?? null,
  };
}

function isCurrentComposerAttachmentRoute(owner, route) {
  return state === owner
    && (state.current?.summary?.id ?? null) === route.workspaceId
    && (state.selectedConversationId ?? null) === route.conversationId
    && (state.temporaryConversation?.temporaryId ?? null) === route.temporaryId;
}

async function importPastedComposerClipboardFiles(clipboardData, fallbackFiles) {
  const owner = state;
  const route = captureComposerAttachmentRoute(owner);
  let files = fallbackFiles;
  // macOS frequently exposes one copied image as TIFF, AVIF, JPEG and PNG at once. Prefer the
  // standard raster representation while handling this exact paste gesture; a failed read keeps
  // the concrete File delivered by the paste event as the only fallback. Windows WebView2 can
  // expose Clipboard.read() while leaving its promise pending without a permission surface, so
  // it must use the concrete event file immediately instead of making paste appear unresponsive.
  const clipboardPlatform = String(navigator.userAgentData?.platform || navigator.platform || '');
  if (/^mac/i.test(clipboardPlatform)
      && fallbackFiles.some(file => String(file?.type || '').toLowerCase().startsWith('image/'))
      && navigator.clipboard?.read) {
    try {
      let readTimeout;
      const items = await Promise.race([
        navigator.clipboard.read(),
        new Promise((_, reject) => { readTimeout = setTimeout(() => reject(new Error('剪贴板读取超时')), 800); }),
      ]).finally(() => clearTimeout(readTimeout));
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
  await importComposerClipboardFiles(files, { owner, route });
}

async function importComposerClipboardFiles(files, { owner = state, route = captureComposerAttachmentRoute(owner) } = {}) {
  if (!isCurrentComposerAttachmentRoute(owner, route)) return;
  if (!native || (!state.current && !state.temporaryConversation)) { state.error = native ? '本机聊天数据尚未就绪，请重新打开应用后添加附件。' : 'Web 预览不能私有复制粘贴附件。'; render(); return; }
  const currentCount = state.temporaryConversation ? (state.temporaryConversation.draftAttachmentIds || []).length : state.composerAttachments.length;
  const accepted = files.slice(0, Math.max(0, 4 - currentCount));
  if (!accepted.length) { state.error = '每条本地消息最多保留 4 个附件。'; render(); return; }
  const failures = [];
  let imported = 0;
  for (const [index, file] of accepted.entries()) {
    if (!isCurrentComposerAttachmentRoute(owner, route)) return;
    try {
      const bytesBase64 = await clipboardFileBase64(file);
      if (!isCurrentComposerAttachmentRoute(owner, route)) return;
      const displayName = clipboardAttachmentName(file, index);
      if (route.temporaryId) {
        const temporary = await invoke('import_desktop_temporary_clipboard_attachment', { args: { temporaryId: route.temporaryId, displayName, bytesBase64 } }, owner);
        if (!isCurrentComposerAttachmentRoute(owner, route)) return;
        state.temporaryConversation = temporary;
        state.composerDraft = state.temporaryConversation.draft || state.composerDraft;
      } else {
        const attachment = await invoke('import_desktop_conversation_clipboard_attachment', { args: { workspaceId: route.workspaceId, displayName, bytesBase64 } }, owner);
        if (!isCurrentComposerAttachmentRoute(owner, route)) return;
        if (!state.composerAttachments.some(item => item.sha256 === attachment.sha256)) state.composerAttachments = [...state.composerAttachments, attachment];
        writeComposerAttachments();
      }
      imported += 1;
    } catch (error) {
      if (route.temporaryId && handleExpiredTemporary(error, route.temporaryId)) return;
      if (!isCurrentComposerAttachmentRoute(owner, route) || error?.code === 'STALE_DATA_AREA') return;
      failures.push(`${clipboardAttachmentName(file, index)}：${String(error)}`);
    }
  }
  if (!isCurrentComposerAttachmentRoute(owner, route)) return;
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
  const generation = ++composerDraftRouteGeneration;
  try {
    await transitionComposerDraftRoute({
      flush: flushOrdinaryComposerDraft,
      read: () => invoke('enter_or_restore_desktop_temporary_conversation', { temporaryId: null }),
      isCurrent: () => generation === composerDraftRouteGeneration && !state.temporaryConversation,
      commit: recovered => {
        state.temporaryConversation = recovered;
        state.composerDraft = recovered.draft || '';
        state.composerAttachments = [];
        state.selectedConversationId = null;
        state.pane = 'chat'; state.error = ''; state.status = '已进入临时聊天：仅本地隔离恢复，最长保留 24 小时。'; render();
      },
    });
  } catch (error) {
    if (generation === composerDraftRouteGeneration) { state.error = `临时聊天未能打开：${String(error)}`; render(); }
  }
}

async function restoreTemporaryChat() {
  if (!native) return;
  const recovered = await invoke('read_desktop_temporary_conversation');
  if (!recovered) return;
  // Startup only prunes and discovers recoverability. It must not switch the user out of NORMAL.
  state.status = '发现可恢复的临时聊天；点击右上角“临时聊天”可在 24 小时内继续。';
}

const temporaryDraftQueue = createDraftWriteQueue({
  routeKey: args => args.temporaryId,
  write: async args => {
    let updated;
    try { updated = await invoke('update_desktop_temporary_conversation', { args }); }
    catch (error) { handleExpiredTemporary(error, args.temporaryId); throw error; }
    // A delayed response must never replace newer in-memory input or another route.
    if (state.temporaryConversation?.temporaryId === args.temporaryId
        && state.composerDraft === args.draft
        && (state.temporaryConversation.modelOverrideId || null) === args.modelOverrideId) state.temporaryConversation = updated;
  },
});
let temporaryDraftTimer = null;
let scheduledTemporaryDraft = null;
function handleExpiredTemporary(error, temporaryId) {
  if (!String(error).includes('临时会话已过期或不存在')) return false;
  temporaryDraftQueue.discardRoute(temporaryId);
  if (state.temporaryConversation?.temporaryId !== temporaryId) return true;
  if (temporaryDraftTimer) clearTimeout(temporaryDraftTimer);
  temporaryDraftTimer = null;
  scheduledTemporaryDraft = null;
  stopComposerCamera();
  state.temporaryConversation = null;
  resetComposerPresentation();
  state.temporaryModelOpen = false;
  state.composerAddOpen = false;
  state.selectedConversationId = null;
  state.pane = 'chat';
  state.error = '';
  state.status = '临时聊天已到期，已返回普通聊天。';
  render();
  void loadOrdinaryComposerDraft().then(() => render()).catch(readError => {
    if (state.temporaryConversation) return;
    state.error = `普通会话草稿未读取：${String(readError)}`;
    render();
  });
  return true;
}
function temporaryDraftArgs(value = state.composerDraft) {
  if (!state.temporaryConversation) return null;
  return { temporaryId: state.temporaryConversation.temporaryId, draft: value, modelOverrideId: state.temporaryConversation.modelOverrideId || null };
}
function persistTemporaryDraft(args) {
  if (!args) return Promise.resolve();
  return temporaryDraftQueue.save(args);
}
function scheduleTemporaryDraft(value) {
  const args = temporaryDraftArgs(value);
  if (!args) return;
  composerDraftRouteGeneration += 1;
  state.composerDraft = value;
  scheduledTemporaryDraft = args;
  if (temporaryDraftTimer) clearTimeout(temporaryDraftTimer);
  temporaryDraftTimer = setTimeout(() => {
    temporaryDraftTimer = null;
    const scheduled = scheduledTemporaryDraft;
    scheduledTemporaryDraft = null;
    void persistTemporaryDraft(scheduled).catch(error => {
      if (handleExpiredTemporary(error, scheduled.temporaryId)) return;
      state.error = `临时草稿未保存：${String(error)}`;
      render();
    });
  }, 180);
}
async function flushTemporaryDraft() {
  if (temporaryDraftTimer) {
    clearTimeout(temporaryDraftTimer);
    temporaryDraftTimer = null;
  }
  // Viewing, leaving or closing an unchanged temporary chat must not renew its
  // 24-hour lifetime. Pending edits and retained failed writes are the only work.
  const scheduled = scheduledTemporaryDraft;
  scheduledTemporaryDraft = null;
  if (scheduled) await persistTemporaryDraft(scheduled);
  await temporaryDraftQueue.flush();
}
async function updateTemporaryDraft(value) {
  const owner = state;
  const temporaryId = owner.temporaryConversation?.temporaryId;
  scheduleTemporaryDraft(value);
  try { await flushTemporaryDraft(); } catch (error) {
    if (handleExpiredTemporary(error, temporaryId)) throw error;
    if (state === owner && owner.temporaryConversation?.temporaryId === temporaryId && error?.code !== 'STALE_DATA_AREA') {
      state.error = `临时草稿未保存：${String(error)}`;
      render();
    }
    throw error;
  }
}
window.addEventListener('pagehide', () => {
  // Best-effort final flush; normal submit already awaits the same queues.
  void flushOrdinaryComposerDraft();
  void flushTemporaryDraft();
});

async function installComposerDraftCloseGuard() {
  if (!native) return;
  const nativeWindow = window.__TAURI__?.window?.getCurrentWindow();
  if (!nativeWindow) throw new Error('原生窗口草稿保护不可用');
  await nativeWindow.onCloseRequested(createDraftCloseGuard({
    flush: async () => {
      // A key arriving while SQLite is committing starts another short queue. Do not
      // destroy the WebView until the final user edit in that queue is durable.
      let generation, settingsVersion;
      do {
        generation = composerDraftRouteGeneration;
        settingsVersion = localSettingsWrites.version;
        await flushOrdinaryComposerDraft();
        await flushTemporaryDraft();
        await localSettingsWrites.flush();
      } while (generation !== composerDraftRouteGeneration || settingsVersion !== localSettingsWrites.version);
    },
    close: () => nativeWindow.destroy(),
    failed: error => {
      state.error = `本机修改未能安全保存，窗口保持打开：${String(error)}`;
      render();
    },
  }));
}

async function updateTemporaryModelOverride(value) {
  if (!state.temporaryConversation) return;
  const modelOverrideId = String(value || '').trim() || null;
  const temporaryId = state.temporaryConversation.temporaryId;
  // Draft and model are one native recovery snapshot. A separate model IPC can
  // be overwritten by an older debounced draft (or overwrite newer input).
  state.temporaryConversation = { ...state.temporaryConversation, modelOverrideId };
  scheduleTemporaryDraft(state.composerDraft);
  state.temporaryModelOpen = false;
  render();
  const isCurrent = () => state.temporaryConversation?.temporaryId === temporaryId
    && (state.temporaryConversation.modelOverrideId || null) === modelOverrideId;
  try {
    await flushTemporaryDraft();
    if (!isCurrent()) return;
    state.error = '';
    state.status = modelOverrideId ? '已保存临时会话的本地模型选择；没有读取 Key、没有调用 Provider。' : '临时会话已切回自动；仍只保存在隔离恢复记录。';
    render();
  } catch (error) {
    if (!isCurrent()) return;
    state.error = `临时模型选择未保存：${String(error)}`;
    render();
  }
}

let conversationChoiceSaveQueue = Promise.resolve();
// Finish each already authorized read/write/read in its captured area.
// Visible projections still require the current conversation owner below.
const invokeConversationChoiceOwner = (command, args, owner) =>
  invokeForAuthorizedDataAreaOperation(tauriBridge, command, args, owner);
function enqueueConversationChoiceSave(write) {
  const save = conversationChoiceSaveQueue.catch(() => {}).then(write);
  conversationChoiceSaveQueue = save;
  return localSettingsWrites.track(save);
}
async function flushConversationChoiceSaves() {
  let observed;
  do { observed = conversationChoiceSaveQueue; await observed; }
  while (observed !== conversationChoiceSaveQueue);
}
function isCurrentConversationChoiceRoute(route, owner = state) {
  return state === owner && !state.temporaryConversation && state.current?.summary.id === route.workspaceId
    && state.selectedConversationId === route.conversationId;
}
function applyConversationChoiceProjection(projection) {
  state.p6gSelection = projection;
  state.conversationPreferences = projection.conversationOverride;
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
    scheduleOrdinaryComposerDraft('model');
    state.status = modelId
      ? '已为这次新对话选择具体模型；发送时会与首条消息原子保存。'
      : '这次新对话已切回自动选择。';
    state.error = '';
    render();
    return;
  }
  if (!native || !state.p6gSelection) return;
  const owner = state;
  const route = { workspaceId: owner.current.summary.id, conversationId: owner.selectedConversationId };
  try {
    const projection = await enqueueConversationChoiceSave(async () => {
      const current = await invokeConversationChoiceOwner('read_desktop_p6g_selection', route, owner);
      const args = { ...route, expectedRevision: current.conversationOverride.revision };
      if (modelId) await invokeConversationChoiceOwner('set_desktop_p6g_conversation_override', { args: { ...args, modelId } }, owner);
      else await invokeConversationChoiceOwner('clear_desktop_p6g_conversation_override', args, owner);
      return invokeConversationChoiceOwner('read_desktop_p6g_selection', route, owner);
    });
    if (!isCurrentConversationChoiceRoute(route, owner)) return;
    applyConversationChoiceProjection(projection);
    state.p6gModelPickerOpen = false;
    state.status = modelId ? '已保存当前会话的本地手动模型选择；未读取 Key、未调用 Provider。' : '已切回自动；当前会话恢复本地策略。';
    state.error = '';
    render();
  } catch (error) {
    if (!isCurrentConversationChoiceRoute(route, owner)) return;
    state.error = `模型选择保存或回读失败：${String(error)}`;
    state.p6gModelPickerOpen = false;
    render();
  }
}

async function updateConversationPreferences(patch, { close = false, toggleWebSearch = false } = {}) {
  if (state.temporaryConversation) return;
  const next = { ...state.conversationPreferences, ...patch };
  const defaultWebSearch = Boolean(state.productSettings.webSearchEnabled);
  if (toggleWebSearch) next.webSearchOverride = !(typeof next.webSearchOverride === 'boolean' ? next.webSearchOverride : defaultWebSearch);
  if (!state.selectedConversationId) {
    state.conversationPreferences = { ...next, revision: 0 };
    scheduleOrdinaryComposerDraft(toggleWebSearch ? 'web' : 'tone');
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
  const owner = state;
  const route = { workspaceId: owner.current.summary.id, conversationId: owner.selectedConversationId };
  const changes = { ...patch };
  try {
    const projection = await enqueueConversationChoiceSave(async () => {
      const current = await invokeConversationChoiceOwner('read_desktop_p6g_selection', route, owner);
      const merged = { ...current.conversationOverride, ...changes };
      if (toggleWebSearch) merged.webSearchOverride = !(typeof merged.webSearchOverride === 'boolean' ? merged.webSearchOverride : defaultWebSearch);
      await invokeConversationChoiceOwner('set_desktop_conversation_preferences', { args: {
        ...route,
        expectedRevision: current.conversationOverride.revision,
        toneOverride: merged.toneOverride ?? null,
        webSearchOverride: typeof merged.webSearchOverride === 'boolean' ? merged.webSearchOverride : null,
      } }, owner);
      return invokeConversationChoiceOwner('read_desktop_p6g_selection', route, owner);
    });
    if (!isCurrentConversationChoiceRoute(route, owner)) return;
    applyConversationChoiceProjection(projection);
    if (close) { state.composerAddOpen = false; state.composerAddPage = 'root'; }
    state.status = patch.toneOverride
      ? '已保存当前会话的基础风格；只影响后续回答。'
      : '已保存当前会话的实时网页搜索覆盖；全局设置未改变。';
    state.error = '';
    render();
    if (close) restoreOverlayFocus();
  } catch (error) {
    if (!isCurrentConversationChoiceRoute(route, owner)) return;
    state.error = `会话偏好保存或回读失败：${String(error)}`;
    if (close) { state.composerAddOpen = false; state.composerAddPage = 'root'; }
    render();
    if (close) restoreOverlayFocus();
  }
}

async function saveP6GGlobalDefault() {
  if (!native) return;
  const owner = state;
  const tier = document.querySelector('#p6g-global-default')?.value || null;
  try {
    const saved = await enqueueConversationChoiceSave(async () => {
      const current = await invokeConversationChoiceOwner('read_desktop_p6g_global_default', {}, owner);
      await invokeConversationChoiceOwner('set_desktop_p6g_global_default', { args: { expectedRevision: current.revision, tier } }, owner);
      return invokeConversationChoiceOwner('read_desktop_p6g_global_default', {}, owner);
    });
    if (state !== owner) return;
    state.p6gGlobalDefault = saved;
    if (state.p6gSelection) state.p6gSelection = { ...state.p6gSelection, globalDefault: saved };
    state.status = tier ? `已保存本地全局默认：${tier}。` : '已清除本地全局默认；自动策略将使用当前请求档位。';
    state.error = '';
    render();
  } catch (error) { if (state !== owner) return; state.error = `全局默认保存或回读失败：${String(error)}`; render(); }
}

async function leaveTemporaryChat() {
  if (!state.temporaryConversation) return;
  const temporaryId = state.temporaryConversation.temporaryId;
  const generation = ++composerDraftRouteGeneration;
  try {
    await transitionComposerDraftRoute({
      flush: flushTemporaryDraft,
      read: async () => null,
      isCurrent: () => generation === composerDraftRouteGeneration && state.temporaryConversation?.temporaryId === temporaryId,
      commit: async () => {
        state.temporaryConversation = null;
        resetComposerPresentation();
        state.temporaryModelOpen = false;
        state.selectedConversationId = null;
        state.pane = 'chat';
        state.status = '已返回普通聊天；临时内容仍在隔离恢复记录中，24 小时内可继续。';
        state.error = '';
        render();
        await loadOrdinaryComposerDraft();
        render();
      },
    });
  } catch (error) {
    if (handleExpiredTemporary(error, temporaryId)) return;
    state.error = `草稿未能安全切换：${String(error)}`; render();
  }
}

async function sendLocalMessage() {
  const owner = state;
  const sendPressedAtMs = Date.now();
  const text = state.composerDraft.trim();
  const attachmentCount = state.temporaryConversation
    ? (state.temporaryConversation.draftAttachmentIds || []).length : state.composerAttachments.length;
  if (!text && !attachmentCount) {
    state.error = '请输入文字或保留附件后再发送。';
    render();
    return;
  }
  if (state.temporaryConversation) {
    if (owner.temporarySendPreparing) return;
    const temporaryId = owner.temporaryConversation.temporaryId;
    const materialSignature = () => JSON.stringify([owner.composerDraft, owner.temporaryConversation?.draftAttachmentIds || []]);
    const pressedMaterial = materialSignature();
    const currentRoute = () => state === owner && owner.temporaryConversation?.temporaryId === temporaryId;
    owner.temporarySendPreparing = true;
    try {
      await updateTemporaryDraft(owner.composerDraft);
      if (!currentRoute()) return;
      if (materialSignature() !== pressedMaterial) {
        owner.error = '保存期间临时输入已改变，请再次点击发送。'; render(); return;
      }
      const updated = await invoke('append_desktop_temporary_message', { args: { temporaryId } }, owner);
      if (!currentRoute()) return;
      if (materialSignature() === pressedMaterial) {
        owner.temporaryConversation = updated;
        owner.composerDraft = ''; owner.composerAttachments = [];
      } else {
        // The completed local message belongs to the pressed snapshot. Keep input
        // typed while its receipt was pending as the next temporary draft.
        owner.temporaryConversation = { ...updated, ...owner.temporaryConversation, messages: updated.messages, draft: owner.composerDraft };
        scheduleTemporaryDraft(owner.composerDraft);
      }
      owner.error = ''; owner.status = '临时消息已保存到隔离恢复记录；没有调用模型。'; render();
    } catch (error) {
      if (!currentRoute() || error?.code === 'STALE_DATA_AREA') return;
      if (handleExpiredTemporary(error, temporaryId)) return;
      owner.error = `临时消息未保存：${String(error)}`; render();
    } finally {
      owner.temporarySendPreparing = false;
    }
    return;
  }
  if (!native || !state.current) {
    state.error = native ? '本机聊天数据尚未就绪，请关闭后重新打开应用。' : 'Web 预览不会写入 Desktop SQLite。';
    render();
    return;
  }
  if (owner.ordinarySendPreparing || owner.pendingChatSubmission) return;
  owner.ordinarySendPreparing = true;
  const sendRoute = { workspaceId: state.current.summary.id, conversationId: state.selectedConversationId,
    draft: state.composerDraft, attachmentIds: state.composerAttachments.map(item => item.id).join('\n') };
  try {
    await flushConversationChoiceSaves();
    if (state !== owner) return;
    // A save can outlive navigation or another edit. This Send press must not
    // authorize the destination conversation or newer, different material.
    if (state.temporaryConversation || state.current?.summary.id !== sendRoute.workspaceId
        || state.selectedConversationId !== sendRoute.conversationId) return;
    await flushOrdinaryComposerDraft();
    if (state !== owner) return;
    if (!sendRoute.conversationId) {
      // A suspended WebView can miss its expiry timer. The native read clears
      // stale text before Send can authorize it, while retaining choices/files.
      const persisted = await invoke('read_desktop_conversation_draft', {
        workspaceId: sendRoute.workspaceId, conversationId: null,
      });
      if (state !== owner) return;
      if ((persisted?.text || '') !== sendRoute.draft) {
        if (!state.temporaryConversation && state.current?.summary.id === sendRoute.workspaceId
            && state.selectedConversationId === sendRoute.conversationId
            && state.composerDraft === sendRoute.draft) {
          state.composerDraft = persisted?.text || '';
          state.composerAttachments = Array.isArray(persisted?.attachments) ? persisted.attachments : [];
          state.status = '草稿文字已到期；附件和选择仍保留，请检查后再发送。';
          render();
        }
        return;
      }
    }
  } catch (error) {
    if (state !== owner || error?.code === 'STALE_DATA_AREA') return;
    state.error = `草稿或会话设置未能确认保存：${String(error)}`;
    render();
    return;
  } finally {
    owner.ordinarySendPreparing = false;
  }
  if (state !== owner || state.temporaryConversation || state.current?.summary.id !== sendRoute.workspaceId
      || state.selectedConversationId !== sendRoute.conversationId) return;
  if (state.composerDraft !== sendRoute.draft
      || state.composerAttachments.map(item => item.id).join('\n') !== sendRoute.attachmentIds) {
    state.status = '输入内容已变化，请再次点击发送。';
    render();
    return;
  }
  const conversation = resolveConversation(state.current, state.selectedConversationId);
  const sentDraft = state.composerDraft;
  const sentAttachments = [...state.composerAttachments];
  const pendingModelId = conversation ? null : state.pendingNewConversationModelId;
  const pendingPreferences = conversation ? null : { ...state.conversationPreferences };
  const disclosedPreview = currentOrdinaryEgressPreview(owner);
  if (!disclosedPreview) {
    state.status = '本次接收方尚未确认，请核对显示的服务商与模型后再次点击发送。';
    render();
    return;
  }
  const sendSelection = { email: owner.accountSync?.email || null,
    keys: [...(owner.accountSync?.syncedConversationKeys || [])] };
  let clientSubmissionId = null;
  let submissionCompleted = false;
  let submittedConversationId = null;
  try {
    const workspaceId = state.current.summary.id;
    clientSubmissionId = createChatSubmissionId();
    if (!conversation) state.pendingCreatedConversationRouteWorkspaceId = workspaceId;
    state.pendingChatSubmission = {
      workspaceId,
      conversationId: conversation?.id || null,
      clientSubmissionId,
    };
    const attachmentIds = sentAttachments.map(item => item.id);
    const preview = disclosedPreview;
    if (state !== owner || owner.current?.summary?.id !== workspaceId
        || (owner.selectedConversationId || null) !== (conversation?.id || null)
        || owner.composerDraft !== sentDraft
        || owner.composerAttachments.map(item => item.id).join('\n') !== sendRoute.attachmentIds) {
      throw new Error('发送预览期间草稿或会话已变化，请再次点击发送');
    }
    const execution = invokeForAuthorizedDataAreaOperation(tauriBridge, 'submit_desktop_ordinary_chat', { args: {
      workspaceId,
      conversationId: conversation?.id || null,
      projectId: conversation ? null : state.pane === 'work' ? state.selectedWorkProjectId : null,
      expectedRevision: conversation?.revision ?? null,
      text,
      attachmentIds,
      modelId: pendingModelId,
      toneOverride: pendingPreferences?.toneOverride ?? null,
      webSearchOverride: typeof pendingPreferences?.webSearchOverride === 'boolean' ? pendingPreferences.webSearchOverride : null,
      // The same Send press binds the selected recipient and private draft fingerprint.
      egressAuthorization: { approvedAtMs: sendPressedAtMs, disclosureVersion: 'normal-chat-egress-v2',
        clientSubmissionId, draftFingerprint: preview.draftFingerprint,
        recipientChoiceId: preview.recipientChoiceId, recipientProviderId: preview.recipientProviderId,
        recipientModelId: preview.recipientModelId, retrievalRecipient: preview.retrievalRecipient || null },
    } }, owner);
    // Native submit consumes the draft in the message transaction. Optimistic
    // clearing must only change the view: scheduling here would write the old
    // text back after commit (or erase recovery data before a rejected submit).
    resetComposerPresentation();
    state.error = '';
    state.status = '消息已本地提交，正在等待模型回复。';
    render();
    let settled = false;
    const refreshLoop = refreshDesktopRuntimeWhilePending(workspaceId, conversation?.id || null, () => settled);
    let result;
    try { result = await execution; } finally { settled = true; await refreshLoop; }
    submissionCompleted = true;
    submittedConversationId = result.conversationId;
    // A short request can settle before its first runtime refresh reaches this
    // window. Claim that one submission here; never re-arm it after streaming.
    claimSubmittedChatRoute({ workspaceId, conversationId: result.conversationId, clientSubmissionId }, owner);
    const ownsCompletedRoute = owner.current?.summary?.id === workspaceId
      && owner.selectedConversationId === result.conversationId;
    if (!conversation && ownsCompletedRoute) owner.pendingNewConversationModelId = null;
    if (!owner.pendingChatSubmission && owner.pendingCreatedConversationRouteWorkspaceId === workspaceId) owner.pendingCreatedConversationRouteWorkspaceId = null;
    if (ownsCompletedRoute) owner.status = result.state === 'COMPLETED' ? '回复已完成并写入用量账本。'
      : result.state === 'COMPLETED_ACCOUNTING_PENDING' ? '回复已保存；用量账本待本地恢复。'
      : result.state === 'CANCELLED' ? '已停止生成，已产生的文本保留。'
      : result.state === 'UNKNOWN' ? '连接结果未知；未自动重发。'
      : '回复失败；可从失败卡片重试。';
    await syncPersistedConversationContinuation(workspaceId, result.conversationId, owner, sendSelection);
    if (state !== owner) return;
    await loadDesktopContextRecords();
    if (state !== owner) return;
    await loadDesktopDiagnosticRecords();
    if (state !== owner) return;
    await refresh({ runtimeRefresh: true });
  } catch (error) {
    const ownsPending = owner.pendingChatSubmission?.clientSubmissionId === clientSubmissionId;
    const sameRoute = owner.current?.summary?.id === sendRoute.workspaceId
      && owner.selectedConversationId === sendRoute.conversationId;
    const sameCommittedRoute = owner.current?.summary?.id === sendRoute.workspaceId
      && owner.selectedConversationId === submittedConversationId;
    if (ownsPending) {
      owner.pendingChatSubmission = null;
      if (owner.pendingCreatedConversationRouteWorkspaceId === sendRoute.workspaceId) owner.pendingCreatedConversationRouteWorkspaceId = null;
    }
    if (!submissionCompleted && ownsPending && sameRoute) {
      owner.composerDraft = sentDraft;
      owner.composerAttachments = sentAttachments;
      if (!conversation) owner.pendingNewConversationModelId = pendingModelId;
      if (state === owner) writeComposerAttachments();
    }
    if (submissionCompleted ? sameCommittedRoute : sameRoute) {
      owner.error = submissionCompleted
        ? `回复已写入本机，但界面回读未完成：${String(error)}`
        : `消息未提交：${String(error)}`;
      if (state === owner) render();
    }
  }
}

saveLocalMessage = sendLocalMessage;

let composerCameraStream = null;
let composerCameraOwner = null;
let composerCameraRoute = null;
let composerCameraGeneration = 0;
function stopComposerCamera({ close = true } = {}) {
  composerCameraGeneration += 1;
  for (const track of composerCameraStream?.getTracks?.() || []) track.stop();
  composerCameraStream = null;
  const owner = composerCameraOwner || state;
  composerCameraOwner = null;
  composerCameraRoute = null;
  if (close) {
    owner.cameraCaptureOpen = false;
    owner.cameraCaptureReady = false;
    owner.cameraCaptureBusy = false;
    owner.cameraCaptureError = '';
  }
}
function attachComposerCameraStream() {
  const video = document.querySelector('[data-composer-camera-video]');
  if (!video || !composerCameraStream || video.srcObject === composerCameraStream) return;
  const owner = composerCameraOwner;
  const route = composerCameraRoute;
  const generation = composerCameraGeneration;
  const stream = composerCameraStream;
  owner.cameraCaptureReady = false;
  const capture = document.querySelector('[data-action="capture-composer-camera"]');
  if (capture) capture.disabled = true;
  const markReady = () => {
    if (generation !== composerCameraGeneration || composerCameraStream !== stream
        || !isCurrentComposerAttachmentRoute(owner, route) || !owner.cameraCaptureOpen
        || document.querySelector('[data-composer-camera-video]') !== video
        || !video.videoWidth || !video.videoHeight) return;
    owner.cameraCaptureReady = true;
    const button = document.querySelector('[data-action="capture-composer-camera"]');
    if (button) button.disabled = false;
  };
  video.addEventListener?.('loadedmetadata', markReady);
  video.addEventListener?.('resize', markReady);
  video.srcObject = stream;
  void video.play().catch(() => {});
  markReady();
}
async function openComposerCamera() {
  const owner = state;
  const route = captureComposerAttachmentRoute(owner);
  if (!native || (!state.current && !state.temporaryConversation)) {
    state.error = native ? '本机聊天数据尚未就绪，请重新打开应用后添加附件。' : 'Web 预览不能把相机原图写入私有草稿。';
    render();
    return;
  }
  const count = state.temporaryConversation ? (state.temporaryConversation.draftAttachmentIds || []).length : state.composerAttachments.length;
  if (count >= 4) { state.error = '每条本地消息最多保留 4 个附件。'; render(); return; }
  if (!navigator.mediaDevices?.getUserMedia) { state.error = '当前系统 WebView 不提供相机采集；草稿没有改变。'; render(); return; }
  stopComposerCamera();
  composerCameraOwner = owner;
  composerCameraRoute = route;
  const generation = composerCameraGeneration;
  state.composerAddOpen = false;
  state.cameraCaptureOpen = true;
  state.cameraCaptureReady = false;
  state.cameraCaptureBusy = false;
  state.cameraCaptureError = '';
  render();
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' }, audio: false });
    if (generation !== composerCameraGeneration || !isCurrentComposerAttachmentRoute(owner, route) || !owner.cameraCaptureOpen) {
      for (const track of stream.getTracks?.() || []) track.stop();
      return;
    }
    composerCameraStream = stream;
    state.cameraCaptureReady = false;
    render();
    queueMicrotask(attachComposerCameraStream);
  } catch (error) {
    if (generation !== composerCameraGeneration || !isCurrentComposerAttachmentRoute(owner, route)) return;
    stopComposerCamera({ close: false });
    state.cameraCaptureError = error?.name === 'NotAllowedError'
      ? '相机权限未授予；请在系统隐私设置中允许南枫 AI 使用相机后重试。草稿没有改变。'
      : error?.name === 'NotFoundError'
      ? '未检测到可用相机；草稿没有改变。'
      : ['NotReadableError', 'AbortError'].includes(error?.name)
      ? '相机可能被其他应用占用；请关闭占用后重试。草稿没有改变。'
      : '相机当前不可用；请检查是否被其他应用占用后重试。草稿没有改变。';
    render();
  }
}
async function captureComposerCamera() {
  const owner = state;
  const route = composerCameraRoute;
  const generation = composerCameraGeneration;
  if (!route || !isCurrentComposerAttachmentRoute(owner, route)) return;
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
    if (route.temporaryId) {
      const temporary = await invoke('import_desktop_temporary_camera_capture', { args: { temporaryId: route.temporaryId, dataUrl } }, owner);
      if (generation !== composerCameraGeneration) return;
      if (!isCurrentComposerAttachmentRoute(owner, route)) { stopComposerCamera(); return; }
      state.temporaryConversation = temporary;
      state.composerDraft = state.temporaryConversation.draft || state.composerDraft;
    } else {
      const attachment = await invoke('import_desktop_camera_capture', { args: { workspaceId: route.workspaceId, dataUrl } }, owner);
      if (generation !== composerCameraGeneration) return;
      if (!isCurrentComposerAttachmentRoute(owner, route)) { stopComposerCamera(); return; }
      if (!state.composerAttachments.some(item => item.sha256 === attachment.sha256)) {
        state.composerAttachments = [...state.composerAttachments, attachment];
        writeComposerAttachments();
      }
    }
    stopComposerCamera();
    state.status = '相机原图已私有复制到当前草稿；尚未发送。';
    state.error = '';
  } catch (error) {
    if (generation !== composerCameraGeneration) return;
    if (route.temporaryId && handleExpiredTemporary(error, route.temporaryId)) return;
    if (!isCurrentComposerAttachmentRoute(owner, route) || error?.code === 'STALE_DATA_AREA') { stopComposerCamera(); return; }
    state.cameraCaptureBusy = false;
    state.cameraCaptureError = `相机原图未加入草稿：${String(error)}`;
  }
  render();
}

async function pickComposerAttachment(kind) {
  const owner = state;
  const route = captureComposerAttachmentRoute(owner);
  if (!native || (!state.current && !state.temporaryConversation)) { state.error = native ? '本机聊天数据尚未就绪，请重新打开应用后添加附件。' : 'Web 预览不能私有复制附件。'; render(); return; }
  const currentAttachmentCount = state.temporaryConversation ? (state.temporaryConversation.draftAttachmentIds || []).length : state.composerAttachments.length;
  if (currentAttachmentCount >= 4) { state.error = '每条本地消息最多保留 4 个附件。'; render(); return; }
  const image = kind === 'image';
  // The native dialog must not silently gray out a valid WAV/M4A on a host-specific UTI map.
  // Import remains fail-closed in Rust: this picker grants no read capability beyond one explicit selection.
  const selectedPath = await dialogInvoke('open', image
    ? { multiple: false, directory: false, filters: [{ name: '图片', extensions: ['jpg', 'jpeg', 'png', 'webp'] }] }
    : { multiple: false, directory: false },
  );
  if (!isCurrentComposerAttachmentRoute(owner, route)) return;
  if (!selectedPath || Array.isArray(selectedPath)) return;
  try {
    if (route.temporaryId) {
      const temporary = await invoke('import_desktop_temporary_attachment', { args: { temporaryId: route.temporaryId, selectedPath } }, owner);
      if (!isCurrentComposerAttachmentRoute(owner, route)) return;
      state.temporaryConversation = temporary;
      state.composerDraft = state.temporaryConversation.draft || state.composerDraft;
      state.status = '临时附件已私有复制并绑定 TEMPORARY_SESSION；不会进入普通会话。';
      state.composerAddOpen = false; state.error = ''; render(); return;
    }
    const attachment = await invoke('import_desktop_conversation_attachment', { args: { workspaceId: route.workspaceId, selectedPath } }, owner);
    if (!isCurrentComposerAttachmentRoute(owner, route)) return;
    if (state.composerAttachments.some(item => item.sha256 === attachment.sha256)) { state.status = '相同附件已在当前草稿中；未重复添加。'; }
    else { state.composerAttachments = [...state.composerAttachments, attachment]; writeComposerAttachments(); state.status = '附件已私有复制并完成内容校验；未外发。'; }
    state.composerAddOpen = false; state.error = ''; render();
  } catch (error) {
    if (route.temporaryId && handleExpiredTemporary(error, route.temporaryId)) return;
    if (!isCurrentComposerAttachmentRoute(owner, route) || error?.code === 'STALE_DATA_AREA') return;
    state.error = `附件未加入草稿：${String(error)}`; render();
  }
}

async function mutateConversationLifecycle(target, action, fields, success) {
  const owner = state;
  const sidebarWorkspace = captureCloudSidebarWorkspace();
  const cloudWorkspaceId = target.dataset.workspaceId || state.dialog?.workspaceId || null;
  let cloudSyncVerificationPending = false;
  try { if (!await ensureConversationActionWorkspace(target)) return; }
  catch (error) { if (state !== owner || error?.code === 'STALE_DATA_AREA') return; throw error; }
  if (state !== owner) return;
  if (!native || !state.current) return;
  const workspaceId = state.current.summary.id;
  const conversationId = target.dataset.id;
  const syncedConversationKeys = state.accountSync?.syncedConversationKeys || [];
  const shouldSync = Boolean(cloudWorkspaceId) || syncedConversationKeys.includes(`${workspaceId}:${conversationId}`);
  let localSaved = false;
  const refreshSavedCloudRow = async () => {
    if (state !== owner) return;
    const existing = owner.cloudConversations?.find(row => row.workspaceId === workspaceId && row.id === conversationId);
    if (!existing) return;
    const [updated] = await cloudRowsFromLocalEntries([{ workspaceId, conversationId, cloudPinned: existing.cloudPinned }], owner);
    if (state !== owner) return;
    if (!updated) throw new Error('已保存会话，但列表回读失败。');
    owner.cloudConversations = owner.cloudConversations.map(row => row === existing ? updated : row);
    persistCloudConversationCache();
  };
  try {
    await invokeForAuthorizedDataAreaOperation(tauriBridge, 'mutate_desktop_domain', { args: {
      intentId: intent('conversation-lifecycle'),
      workspaceId,
      entity: 'conversation',
      action,
      objectId: conversationId,
      expectedRevision: Number(target.dataset.revision),
      fields,
    } }, owner);
    localSaved = true;
    if (state === owner && action === 'archive') {
      removeFavoriteConversation(conversationId);
      if (state.selectedConversationId === conversationId) state.selectedConversationId = null;
    }
    if (state === owner) state.error = '';
    if (shouldSync) {
      // A user-authorized local edit still needs its selected-cloud continuation
      // after navigation; the RPC stays bound to the original data area.
      const invokeOwner = (command, args) => invokeForAuthorizedDataAreaOperation(tauriBridge, command, args, owner);
      const receipt = await syncSelectedContinuation({ invoke: invokeOwner, workspaceId, conversationId,
        syncedConversationKeys: cloudWorkspaceId ? [...syncedConversationKeys, `${workspaceId}:${conversationId}`] : syncedConversationKeys });
      if (state !== owner) return;
      if (receipt.status === 'UNKNOWN') {
        cloudSyncVerificationPending = true;
        state.accountSync = { ...state.accountSync, pendingUnknown: { workspaceId, conversationId } };
        scheduleUnknownSyncReconciliation(workspaceId, conversationId);
      } else if (!['SYNCED', 'UP_TO_DATE'].includes(receipt.status)) {
        throw new Error(`云端回写未完成：${receipt.status}`);
      }
    }
    if (state !== owner) return;
    await refreshSavedCloudRow();
    if (state !== owner) return;
    state.status = shouldSync
      ? (cloudSyncVerificationPending ? `${success}；云端提交正在后台核对。` : `${success} 已同步到云端。`)
      : success;
    state.dialog = null;
    await restoreCloudSidebarWorkspace(sidebarWorkspace);
    if (state !== owner) return;
    // A title readback replaces the conversation projection, but its transcript
    // is still the same reading surface. Patch through the runtime DOM owner so
    // selection, code scrolling and connected media survive the metadata edit.
    await refresh({ runtimeRefresh: action === 'update' && Object.keys(fields).length === 1 && Object.hasOwn(fields, 'title') });
  } catch (error) {
    if (state !== owner || error?.code === 'STALE_DATA_AREA') return;
    if (localSaved) {
      await refreshSavedCloudRow().catch(() => {});
      if (state !== owner) return;
      state.dialog = null;
    }
    await restoreCloudSidebarWorkspace(sidebarWorkspace).catch(() => {});
    if (state !== owner) return;
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
  const owner = state;
  const pinnedConversationIds = new Set(await invoke('set_desktop_cloud_list_pinned', {
    conversationId: target.dataset.id,
    pinned,
  }, owner));
  if (state !== owner) return;
  owner.cloudConversations = owner.cloudConversations.map(item => (
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
  if (!native || !workspaceId || state.current?.summary?.id === workspaceId) return true;
  const owner = state;
  const sourceWorkspace = owner.current;
  const sourceConversationId = owner.selectedConversationId;
  const sourcePane = owner.pane;
  const isCurrent = () => state === owner && owner.current === sourceWorkspace
    && owner.selectedConversationId === sourceConversationId && owner.pane === sourcePane;
  let current;
  let history;
  try {
    [current, history] = await Promise.all([
      invoke('read_desktop_workspace', { workspaceId }, owner),
      invoke('read_desktop_workbench_history', { workspaceId }, owner),
    ]);
  } catch (error) {
    if (!isCurrent() || error?.code === 'STALE_DATA_AREA') return false;
    throw error;
  }
  if (!isCurrent()) return false;
  owner.current = current;
  owner.selectedConversationId = null;
  owner.history = history;
  reloadFavoriteConversationIds();
  await loadConversationReadState({ observeSelected: false });
  return state === owner && owner.current === current;
}

// Cloud-list actions may briefly load a restored workspace to mutate that row.
// Only opening a cloud row navigates; list actions always return here.
function captureCloudSidebarWorkspace() {
  if (state.sidebarConversationList !== 'cloud' || !state.current?.summary?.id) return null;
  return { workspaceId: state.current.summary.id, selectedConversationId: state.selectedConversationId };
}

async function restoreCloudSidebarWorkspace(snapshot) {
  if (!snapshot || !native || state.current?.summary?.id === snapshot.workspaceId) return;
  const owner = state;
  const sourceWorkspaceId = owner.current?.summary?.id;
  const sourceConversationId = owner.selectedConversationId;
  const sourcePane = owner.pane;
  const [current, history, favoriteIds] = await Promise.all([
    invoke('read_desktop_workspace', { workspaceId: snapshot.workspaceId }, owner),
    invoke('read_desktop_workbench_history', { workspaceId: snapshot.workspaceId }, owner),
    invoke('read_desktop_favorite_conversation_ids', { workspaceId: snapshot.workspaceId }, owner),
  ]);
  if (state !== owner || owner.current?.summary?.id !== sourceWorkspaceId
      || owner.selectedConversationId !== sourceConversationId || owner.pane !== sourcePane) return;
  owner.current = current;
  owner.selectedConversationId = resolveConversation(current, snapshot.selectedConversationId) ? snapshot.selectedConversationId : null;
  owner.history = history;
  owner.favoriteConversationIds = new Set(favoriteIds);
  await loadConversationReadState({ observeSelected: false });
}

function batchConversationKey(item) {
  return `${item?.workspaceId || state.current?.summary?.id || 'local'}:${item?.id || ''}`;
}

function currentBatchCandidates() {
  const current = workspace();
  const sidebarData = state.dataArea === 'WORK'
    ? { ...current, exchange: { ...current?.exchange, conversations: resolveWorkConversationState(current, state.selectedWorkProjectId, state.selectedConversationId).conversations } }
    : current;
  const source = state.sidebarConversationList === 'cloud'
    ? state.cloudConversations
    : activeConversations(sidebarData, state.manualUnreadAtMs);
  return source.filter(item => item && !item.archived && !item.deleted);
}

function finishConversationBatchEditing() {
  state.batchEditing = false;
  state.batchSelectedConversationKeys = new Set();
}

async function confirmConversationBatchDelete() {
  const owner = state;
  const dialog = state.dialog;
  if (!native || dialog?.kind !== 'conversation-batch-delete' || dialog.submitting || !Array.isArray(dialog.items) || !dialog.items.length) return;
  const sourceWorkspaceId = owner.current?.summary?.id;
  const items = dialog.items.map(item => ({ ...item, revision: Number(item.revision) }));
  const sidebarWorkspace = captureCloudSidebarWorkspace();
  const pendingDialog = { ...dialog, items, submitting: true, failure: '' };
  owner.dialog = pendingDialog;
  let expectedWorkspaceId = sourceWorkspaceId;
  const isCurrentBatch = () => state === owner && owner.dialog === pendingDialog
    && owner.current?.summary?.id === expectedWorkspaceId;
  render();
  let completed = 0;
  let cloudSyncVerificationPending = false;
  try {
    for (const item of items) {
      if (!isCurrentBatch()) return;
      const workspaceId = item.workspaceId || sourceWorkspaceId;
      let workspaceReady;
      try {
        workspaceReady = await ensureConversationActionWorkspace(item);
      } catch (error) {
        // The helper may adopt the source before a secondary read fails.
        if (state === owner && owner.dialog === pendingDialog && owner.current?.summary?.id === workspaceId) expectedWorkspaceId = workspaceId;
        throw error;
      }
      if (!workspaceReady) {
        if (isCurrentBatch()) owner.dialog = { ...pendingDialog, submitting: false };
        return;
      }
      expectedWorkspaceId = workspaceId;
      if (!isCurrentBatch()) return;
      await invokeForAuthorizedDataAreaOperation(tauriBridge, 'mutate_desktop_domain', { args: {
        intentId: intent('conversation-lifecycle'), workspaceId,
        entity: 'conversation', action: 'softDelete', objectId: item.id,
        expectedRevision: Number(item.revision), fields: {},
      } }, owner);
      completed += 1;
      if (isCurrentBatch()) removeFavoriteConversation(item.id);
      if (item.workspaceId) {
        const receipt = await invokeForAuthorizedDataAreaOperation(tauriBridge, 'sync_selected_desktop_conversation', {
          workspaceId,
          conversationId: item.id,
        }, owner);
        if (receipt.status === 'UNKNOWN') {
          cloudSyncVerificationPending = true;
          if (isCurrentBatch()) {
            owner.accountSync = { ...owner.accountSync, pendingUnknown: { workspaceId, conversationId: item.id } };
            scheduleUnknownSyncReconciliation(workspaceId, item.id);
          }
        } else if (!['SYNCED', 'UP_TO_DATE'].includes(receipt.status)) {
          throw new Error(`云端回写未完成：${receipt.status}`);
        }
      }
    }
    if (!isCurrentBatch()) return;
    if (state.selectedConversationId && dialog.items.some(item => item.id === state.selectedConversationId && (!item.workspaceId || item.workspaceId === state.current?.summary?.id))) state.selectedConversationId = null;
    const cloudKeys = new Set(dialog.items.filter(item => item.workspaceId).map(batchConversationKey));
    if (cloudKeys.size) {
      state.cloudConversations = state.cloudConversations.filter(item => !cloudKeys.has(batchConversationKey(item)));
      advanceCloudListRead();
      persistCloudConversationCache();
    }
    state.dialog = null;
    finishConversationBatchEditing();
    state.error = '';
    state.status = cloudSyncVerificationPending
      ? `已将 ${completed} 个会话移入回收站；云端提交正在后台核对。`
      : `已将 ${completed} 个会话移入回收站，可恢复。`;
    await restoreCloudSidebarWorkspace(sidebarWorkspace);
    if (state !== owner || owner.current?.summary?.id !== (sidebarWorkspace?.workspaceId || expectedWorkspaceId) || owner.dialog !== null) return;
    await refresh();
  } catch (error) {
    if (!isCurrentBatch()) return;
    await restoreCloudSidebarWorkspace(sidebarWorkspace).catch(() => {});
    if (state !== owner || owner.dialog !== pendingDialog || owner.current?.summary?.id !== (sidebarWorkspace?.workspaceId || expectedWorkspaceId)) return;
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
  void openCloudConversationRoute({ workspaceId, conversationId, title: conversation?.title || '云端会话', areaOwner: state, areaGeneration: cloudOpenAreaGeneration });
}, true);

/** FB-P6-037: dialog state follows the typed receipt, never a CSS-only hide. */
async function confirmConversationDelete(target) {
  const owner = state;
  const workspaceId = owner.current?.summary?.id;
  const sourcePane = owner.pane;
  const sourceSettingsPage = owner.settingsSection;
  const dialog = owner.dialog;
  const pending = beginConversationRecycle(dialog, target.dataset.id);
  if (!native || !state.current || !pending) return;
  owner.dialog = pending;
  const isCurrentSource = () => state === owner && owner.current?.summary?.id === workspaceId;
  const isCurrentDialog = () => isCurrentSource() && owner.dialog === pending;
  render();
  try {
    await invokeForAuthorizedDataAreaOperation(tauriBridge, 'mutate_desktop_domain', { args: {
      intentId: intent('conversation-lifecycle'), workspaceId,
      entity: 'conversation', action: 'softDelete', objectId: dialog.id,
      expectedRevision: dialog.revision, fields: {},
    } }, owner);
    if (!isCurrentDialog()) return;
    removeFavoriteConversation(dialog.id);
    if (state.selectedConversationId === dialog.id) state.selectedConversationId = null;
    const completed = completeConversationRecycle();
    state.dialog = completed.dialog;
    state.error = '';
    state.status = completed.status;
    await refresh();
    if (!isCurrentSource() || owner.pane !== sourcePane || owner.settingsSection !== sourceSettingsPage) return;
    document.querySelector(`[data-action="${completed.focusAction}"]`)?.focus({ preventScroll: true });
  } catch (error) {
    if (!isCurrentDialog()) return;
    state.dialog = failConversationRecycle(dialog, error);
    render();
  }
}

async function confirmConversationPermanentDelete(target) {
  const owner = state;
  const workspaceId = owner.current?.summary?.id;
  const dialogState = owner.dialog;
  if (!native || !state.current || dialogState?.kind !== 'conversation-permanent-delete' || dialogState.submitting) return;
  const pendingDialog = { ...dialogState, submitting: true, failure: '' };
  owner.dialog = pendingDialog;
  const isCurrentSource = () => state === owner && owner.current?.summary?.id === workspaceId;
  const isCurrentDialog = () => isCurrentSource() && owner.dialog === pendingDialog;
  render();
  try {
    const receipt = await invokeForAuthorizedDataAreaOperation(tauriBridge, 'permanently_delete_desktop_conversation', { args: {
      workspaceId,
      conversationId: target.dataset.id,
      expectedRevision: Number(target.dataset.revision),
    } }, owner);
    if (!isCurrentDialog()) return;
    removeFavoriteConversation(target.dataset.id);
    if (state.selectedConversationId === target.dataset.id) state.selectedConversationId = null;
    state.dialog = null;
    state.error = '';
    state.status = receipt.cleanupPending ? '已永久删除会话；附件隔离清理将在下次本机维护时重试。' : '已永久删除会话。';
    await refresh();
    if (!isCurrentSource()) return;
    await loadDesktopPrivacyInventory();
    if (isCurrentSource()) render();
  } catch (error) {
    if (!isCurrentDialog()) return;
    state.dialog = { ...dialogState, submitting: false, failure: String(error) };
    render();
  }
}

async function confirmConversationBulkCleanup(scope) {
  const owner = state;
  const workspaceId = owner.current?.summary?.id;
  const dialogState = owner.dialog;
  if (!native || !state.current || dialogState?.kind !== 'conversation-bulk-cleanup' || dialogState.submitting) return;
  const targets = (owner.current.exchange.conversations || [])
    .filter(item => scope === 'recycle' ? item.deleted : item.archived && !item.deleted)
    .map(item => ({ id: item.id, revision: Number(item.revision) }));
  const pendingDialog = { ...dialogState, count: targets.length, submitting: true, failure: '' };
  owner.dialog = pendingDialog;
  const isCurrentSource = () => state === owner && owner.current?.summary?.id === workspaceId;
  const isCurrentDialog = () => isCurrentSource() && owner.dialog === pendingDialog;
  render();
  let completed = 0;
  let cleanupPending = 0;
  const rejected = [];
  for (const conversation of targets) {
    try {
      if (scope === 'recycle') {
        const receipt = await invokeForAuthorizedDataAreaOperation(tauriBridge, 'permanently_delete_desktop_conversation', { args: {
          workspaceId,
          conversationId: conversation.id,
          expectedRevision: Number(conversation.revision),
        } }, owner);
        if (receipt.cleanupPending) cleanupPending += 1;
      } else {
        await invokeForAuthorizedDataAreaOperation(tauriBridge, 'mutate_desktop_domain', { args: {
          intentId: intent('conversation-bulk-recycle'), workspaceId,
          entity: 'conversation', action: 'softDelete', objectId: conversation.id,
          expectedRevision: Number(conversation.revision), fields: {},
        } }, owner);
      }
      completed += 1;
      if (isCurrentSource()) {
        removeFavoriteConversation(conversation.id);
        if (owner.selectedConversationId === conversation.id) owner.selectedConversationId = null;
      }
    } catch (error) {
      rejected.push(String(error));
    }
  }
  if (!isCurrentDialog()) return;
  state.dialog = null;
  if (completed > 0) {
    const base = scope === 'recycle' ? `已永久删除 ${completed} 个回收站会话。` : `已将 ${completed} 个会话移入回收站。`;
    state.status = `${base}${cleanupPending ? ` ${cleanupPending} 项附件隔离清理待本机维护重试。` : ''}${rejected.length ? ` ${rejected.length} 个未完成。` : ''}`;
    state.error = rejected.length ? rejected[0] : '';
  } else {
    state.error = `${scope === 'recycle' ? '回收站未清空' : '已归档会话未清空'}：${rejected[0] || '本机操作未完成。'}`;
  }
  await refresh();
  if (!isCurrentSource()) return;
  await loadDesktopPrivacyInventory();
  if (isCurrentSource()) render();
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
    const owner = state;
    try {
      const selectedPath = await dialogInvoke('open', { directory: true, multiple: false });
      if (!selectedPath) return;
      await invokeForAuthorizedDataAreaOperation(tauriBridge, 'choose_desktop_storage_location', { selectedPath }, owner);
      if (state !== owner || state.pane !== 'settings' || state.settingsSection !== 'privacy') return;
      let inventoryRefreshed = true;
      try { await loadDesktopPrivacyInventory(); }
      catch { inventoryRefreshed = false; }
      if (state !== owner || state.pane !== 'settings' || state.settingsSection !== 'privacy') return;
      state.dialog = { kind: 'model-settings-feedback', message: `保存路径已设置，下次启动生效。空目录会复制现有数据；已有南枫 AI 数据的目录会直接读取。旧目录保留。${inventoryRefreshed ? '' : '路径清单暂未刷新，请重新进入本机数据页查看。'}` };
    } catch (error) {
      if (state === owner && state.pane === 'settings' && state.settingsSection === 'privacy') {
        state.dialog = { kind: 'model-settings-feedback', failed: true, message: String(error) };
      } else {
        owner.error = `数据保存路径未设置：${String(error)}`;
      }
    }
    render(); return;
  }
  if (action === 'open-conversation-row-menu') { openConversationContextMenu(target.closest('[data-conversation-row]'), target); return; }
  if (action === 'open-conversation-header-menu') { openConversationHeaderMenu(target); return; }
  if (action === 'open-image-preview') { event.preventDefault(); await openImagePreview(target.dataset.attachmentId, state.temporaryConversation?.temporaryId || state.current?.summary?.id); return; }
  if (action === 'open-pdf-preview') { event.preventDefault(); await openPdfPreview(target.dataset.attachmentId); return; }
  if (action === 'open-video-preview') { event.preventDefault(); await openVideoPreview(target.dataset.attachmentId); return; }
  if (action === 'open-audio-preview') { event.preventDefault(); await openAudioPreview(target.dataset.attachmentId); return; }
  if (action === 'open-text-preview') { event.preventDefault(); await openTextPreview(target.dataset.attachmentId); return; }
  if (action === 'open-archive-preview') { event.preventDefault(); await openArchivePreview(target.dataset.attachmentId); return; }
  if (action === 'open-archive-directory') { event.preventDefault(); showArchiveDirectory(target.dataset.archivePath || ''); return; }
  if (action === 'open-archive-entry-preview') { event.preventDefault(); await openArchiveEntryPreview(target.dataset.archiveEntryPath); return; }
  if (action === 'archive-preview-back') { event.preventDefault(); showArchiveDirectory(); return; }
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
  if (action === 'close-pdf-preview') { event.preventDefault(); cancelPdfPreviewLoad(); render(); return; }
  if (action === 'close-video-preview') { event.preventDefault(); await closeVideoPreview(); return; }
  if (action === 'close-audio-preview') { event.preventDefault(); await closeAudioPreview(); return; }
  if (action === 'close-text-preview') { event.preventDefault(); state.textPreview = null; render(); return; }
  if (action === 'close-archive-preview') { event.preventDefault(); cancelArchivePreviewLoad(); render(); return; }
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
    void updateConversationPreferences({}, { toggleWebSearch: true });
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
    const owner = state;
    const conversationId = owner.selectedConversationId;
    const workspaceId = owner.current?.summary?.id;
    if (!native || !conversationId || !workspaceId) return;
    const ownsRoute = () => state === owner
      && owner.current?.summary?.id === workspaceId
      && owner.selectedConversationId === conversationId;
    try {
      const accepted = await invokeForAuthorizedDataAreaOperation(tauriBridge,
        'cancel_desktop_ordinary_chat', { args: { conversationId } }, owner);
      if (!ownsRoute()) return;
      owner.status = accepted ? '正在停止；已生成的文本会保留。' : '当前没有可停止的生成。';
      owner.error = '';
    } catch (error) {
      if (!ownsRoute()) return;
      owner.error = `停止失败：${String(error)}`;
    }
    render();
    return;
  }
  if (action === 'retry-ordinary-chat') {
    event.preventDefault();
    const attemptId = target.dataset.attemptId;
    if (!attemptId) return;
    const owner = state;
    const sendPressedAtMs = Date.now();
    const retryConversationId = owner.selectedConversationId;
    const retrySelection = { email: owner.accountSync?.email || null,
      keys: [...(owner.accountSync?.syncedConversationKeys || [])] };
    const retryWorkspaceId = state.current?.summary?.id;
    const retryConversation = resolveConversation(owner.current, retryConversationId);
    const retrySourceId = retryConversation?.currentLeafId;
    const retrySource = retryConversation?.messages.find(item => item.id === retrySourceId);
    if (!native || !retryWorkspaceId || owner.ordinarySendPreparing || owner.pendingChatSubmission
        || retrySource?.attemptId !== attemptId || !['FAILED', 'UNKNOWN'].includes(retrySource.delivery)) return;
    owner.ordinarySendPreparing = true;
    let preparationOwned = true;
    let clientSubmissionId = null;
    try {
      const preview = await invokeForAuthorizedDataAreaOperation(tauriBridge,
        'preview_desktop_ordinary_chat_retry_egress', { args: { attemptId } }, owner);
      const current = resolveConversation(owner.current, retryConversationId);
      if (state !== owner || owner.current?.summary?.id !== retryWorkspaceId
          || owner.selectedConversationId !== retryConversationId
          || current?.revision !== retryConversation.revision
          || current?.currentLeafId !== retrySourceId) {
        throw new Error('重试预览期间来源消息或会话已变化，请再次点击');
      }
      clientSubmissionId = createChatSubmissionId();
      owner.pendingChatSubmission = { workspaceId: retryWorkspaceId, conversationId: retryConversationId, clientSubmissionId };
      const resultPromise = invokeForAuthorizedDataAreaOperation(tauriBridge, 'retry_desktop_ordinary_chat', { args: {
        attemptId, egressAuthorization: { approvedAtMs: sendPressedAtMs,
          disclosureVersion: 'normal-chat-egress-v2', clientSubmissionId,
          draftFingerprint: preview.draftFingerprint, recipientChoiceId: preview.recipientChoiceId,
          recipientProviderId: preview.recipientProviderId, recipientModelId: preview.recipientModelId, retrievalRecipient: preview.retrievalRecipient || null },
      } }, owner);
      owner.ordinarySendPreparing = false;
      preparationOwned = false;
      state.status = '已按原 Provider、原模型和原幂等键开始重试。';
      state.error = '';
      render();
      const result = await resultPromise;
      if (owner.pendingChatSubmission?.clientSubmissionId === clientSubmissionId) owner.pendingChatSubmission = null;
      if (state !== owner || owner.current?.summary?.id !== retryWorkspaceId
          || owner.selectedConversationId !== result.conversationId) {
        await syncPersistedConversationContinuation(retryWorkspaceId, result.conversationId, owner, retrySelection);
        return;
      }
      state.status = result.state === 'COMPLETED' ? '重试已完成并写入用量账本。' : result.state === 'UNKNOWN' ? '重试结果仍未知，未再次自动重发。' : '重试未完成。';
      await syncPersistedConversationContinuation(retryWorkspaceId, result.conversationId, owner, retrySelection);
      if (state !== owner) return;
      await loadDesktopContextRecords();
      if (state !== owner) return;
      await loadDesktopDiagnosticRecords();
      if (state !== owner) return;
      await refresh({ runtimeRefresh: true });
    } catch (error) {
      if (owner.pendingChatSubmission?.clientSubmissionId === clientSubmissionId) owner.pendingChatSubmission = null;
      if (state !== owner || owner.current?.summary?.id !== retryWorkspaceId
          || owner.selectedConversationId !== retryConversationId) return;
      state.error = `重试被拒绝：${String(error)}`;
      render();
    } finally {
      if (preparationOwned) owner.ordinarySendPreparing = false;
    }
    return;
  }
  if (action === 'change-model-completed-answer') {
    event.preventDefault();
    const owner = state;
    owner.assistantMessageMenu = null;
    const conversation = resolveConversation(owner.current, owner.selectedConversationId);
    const messageId = target.dataset.messageId;
    const source = conversation?.messages.find(item => item.id === messageId);
    if (!native || !source || source.role !== 'assistant'
        || !['COMPLETE', 'FAILED', 'CANCELLED', 'UNKNOWN'].includes(source.delivery)
        || !source.attemptId || source.compareExecutionId || conversation.currentLeafId !== messageId
        || conversation.archived || conversation.deleted || owner.ordinarySendPreparing) return;
    owner.dialog = { kind: 'change-answer-model', workspaceId: owner.current.summary.id,
      conversationId: conversation.id, messageId };
    render();
    return;
  }
  if (action === 'generate-answer-for-saved-user' || action === 'regenerate-completed-answer'
      || action === 'confirm-change-answer-model') {
    event.preventDefault();
    const changeModel = action === 'confirm-change-answer-model';
    const regenerate = action !== 'generate-answer-for-saved-user';
    const targetPresetId = changeModel ? target.dataset.targetPresetId : null;
    const owner = state;
    const sendPressedAtMs = Date.now();
    const modelDialog = changeModel ? owner.dialog : null;
    owner.assistantMessageMenu = null;
    if (owner.ordinarySendPreparing || owner.pendingChatSubmission) {
      owner.status = '上一提交正在准备或等待本机确认，请稍后再次选择生成。';
      render();
      return;
    }
    if (!native || owner.temporaryConversation || !owner.current) return;
    const workspaceId = owner.current.summary.id;
    const conversationId = owner.selectedConversationId;
    const sourceMessageId = target.dataset.messageId;
    if (changeModel && (!targetPresetId || modelDialog?.kind !== 'change-answer-model'
        || modelDialog.workspaceId !== workspaceId || modelDialog.conversationId !== conversationId
        || modelDialog.messageId !== sourceMessageId)) return;
    if (changeModel) owner.dialog = null;
    owner.ordinarySendPreparing = true;
    let preparationOwned = true;
    let clientSubmissionId = null;
    try {
      await flushConversationChoiceSaves();
      if (state !== owner || owner.current?.summary?.id !== workspaceId || owner.selectedConversationId !== conversationId) return;
      const conversation = resolveConversation(owner.current, conversationId);
      const sourceMessage = conversation?.messages.find(item => item.id === sourceMessageId);
      if (!conversation || conversation.currentLeafId !== sourceMessageId
          || conversation.archived || conversation.deleted
          || (regenerate
            ? sourceMessage?.role !== 'assistant'
              || !(changeModel
                ? ['COMPLETE', 'FAILED', 'CANCELLED', 'UNKNOWN'].includes(sourceMessage.delivery)
                : sourceMessage.delivery === 'COMPLETE')
              || !sourceMessage.attemptId || Boolean(sourceMessage.compareExecutionId)
            : sourceMessage?.role !== 'user'
              || conversation.messages.some(item => item.parentId === sourceMessageId))) {
        owner.error = '当前消息已变化，请刷新会话后重试。';
        render();
        return;
      }
      const selection = { email: owner.accountSync?.email || null,
        keys: [...(owner.accountSync?.syncedConversationKeys || [])] };
      clientSubmissionId = createChatSubmissionId();
      owner.pendingChatSubmission = { workspaceId, conversationId, clientSubmissionId };
      const preview = await invokeForAuthorizedDataAreaOperation(tauriBridge,
        'preview_desktop_existing_ordinary_chat_egress', { args: {
          workspaceId, conversationId, sourceMessageId,
          expectedRevision: conversation.revision,
          operation: changeModel ? 'changeModel' : regenerate ? 'regenerate' : 'savedUser',
          targetPresetId,
        } }, owner);
      const currentConversation = resolveConversation(owner.current, conversationId);
      if (state !== owner || owner.current?.summary?.id !== workspaceId
          || owner.selectedConversationId !== conversationId
          || currentConversation?.revision !== conversation.revision
          || currentConversation?.currentLeafId !== sourceMessageId) {
        throw new Error('预览期间来源消息或会话已变化，请重新选择');
      }
      const execution = invokeForAuthorizedDataAreaOperation(tauriBridge,
        regenerate ? 'regenerate_desktop_ordinary_chat' : 'generate_desktop_ordinary_chat_for_saved_user', { args: {
        workspaceId, conversationId,
        [regenerate ? 'assistantMessageId' : 'userMessageId']: sourceMessageId,
        ...(changeModel ? { targetPresetId } : {}),
        expectedRevision: conversation.revision,
        egressAuthorization: { approvedAtMs: sendPressedAtMs, disclosureVersion: 'normal-chat-egress-v2',
          clientSubmissionId, draftFingerprint: preview.draftFingerprint,
          recipientChoiceId: preview.recipientChoiceId, recipientProviderId: preview.recipientProviderId,
          recipientModelId: preview.recipientModelId, retrievalRecipient: preview.retrievalRecipient || null },
      } }, owner);
      // Dispatch ends preparation; identity-bound admission still guards a
      // duplicate click. Later settlement must not unlock a newer preparation.
      owner.ordinarySendPreparing = false;
      preparationOwned = false;
      owner.status = changeModel ? '已选择目标模型，正在生成新的回答分支。'
        : regenerate ? '正在生成新回答，原回答保留在分支历史。' : '已选择当前用户消息，正在等待模型生成回答。';
      owner.error = '';
      render();
      let settled = false;
      const refreshLoop = refreshDesktopRuntimeWhilePending(workspaceId, conversationId, () => settled);
      let result;
      try { result = await execution; } finally { settled = true; await refreshLoop; }
      claimSubmittedChatRoute({ workspaceId, conversationId: result.conversationId, clientSubmissionId }, owner);
      if (owner.current?.summary?.id === workspaceId && owner.selectedConversationId === result.conversationId) {
        owner.status = result.state === 'COMPLETED' ? '新回答已保存为本地分支。'
          : result.state === 'COMPLETED_ACCOUNTING_PENDING' ? '新回答已保存；用量账本待本地恢复。'
          : result.state === 'CANCELLED' ? '已停止生成；已有内容保留。'
          : result.state === 'UNKNOWN' ? '本次生成结果未知，未自动重发。'
          : '本次生成未完成；已有内容保留。';
      }
      await syncPersistedConversationContinuation(workspaceId, result.conversationId, owner, selection);
      if (state !== owner || owner.current?.summary?.id !== workspaceId || owner.selectedConversationId !== result.conversationId) return;
      await loadDesktopContextRecords();
      if (state !== owner) return;
      await loadDesktopDiagnosticRecords();
      if (state !== owner) return;
      await refresh({ runtimeRefresh: true });
    } catch (error) {
      if (owner.pendingChatSubmission?.clientSubmissionId === clientSubmissionId) owner.pendingChatSubmission = null;
      if (state !== owner || owner.current?.summary?.id !== workspaceId || owner.selectedConversationId !== conversationId) return;
      owner.error = `本次生成未能确认结果；请检查会话记录：${String(error)}`;
      render();
    } finally {
      if (preparationOwned) owner.ordinarySendPreparing = false;
    }
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
  if (action === 'open-assistant-message-menu') {
    event.preventDefault();
    toggleAssistantMessageMenu(target);
    return;
  }
  if (action === 'show-conversation-branches') {
    event.preventDefault();
    const owner = state;
    owner.assistantMessageMenu = null;
    const conversation = resolveConversation(owner.current, owner.selectedConversationId);
    if (!conversation || !owner.current) return;
    owner.dialog = { kind: 'conversation-branches', workspaceId: owner.current.summary.id, conversationId: conversation.id, submitting: false, failure: '' };
    render();
    return;
  }
  if (action === 'edit-conversation-user-message') {
    event.preventDefault();
    const owner = state;
    const conversation = resolveConversation(owner.current, owner.selectedConversationId);
    const messageId = target.dataset.messageId;
    const message = orderedConversationMessages(conversation).find(item => item.id === messageId);
    owner.assistantMessageMenu = null;
    if (!native || !owner.current || !message || message.role !== 'user' || conversation.archived || conversation.deleted
        || !Array.isArray(message.blocks) || !message.blocks.length
        || !message.blocks.every(block => block.kind === 'TEXT')) return;
    owner.dialog = { kind: 'edit-conversation-user-message', workspaceId: owner.current.summary.id,
      conversationId: conversation.id, messageId, text: messagePlainText(message), submitting: false, failure: '' };
    render();
    return;
  }
  if (action === 'save-conversation-user-message-edit') {
    event.preventDefault();
    const owner = state;
    const edit = owner.dialog;
    if (edit?.kind !== 'edit-conversation-user-message' || edit.submitting || !native) return;
    const text = document.querySelector('#conversation-user-message-edit')?.value?.trim() || '';
    if (!text) { edit.failure = '请输入消息文字。'; render(); return; }
    const workspaceId = edit.workspaceId;
    const conversationId = edit.conversationId;
    const currentEditRoute = () => state === owner && owner.dialog === edit
      && owner.selectedConversationId === conversationId && owner.current?.summary?.id === workspaceId;
    const conversation = owner.current?.summary?.id === workspaceId ? resolveConversation(owner.current, conversationId) : null;
    if (!conversation || conversation.archived || conversation.deleted || owner.selectedConversationId !== conversationId) return;
    edit.text = text;
    edit.submitting = true;
    edit.failure = '';
    render();
    let saved = false;
    try {
      await invokeForAuthorizedDataAreaOperation(tauriBridge, 'mutate_desktop_domain', { args: {
        intentId: intent('edit-user-message'), workspaceId, entity: 'conversation', action: 'editUserMessage',
        objectId: conversationId, expectedRevision: conversation.revision, fields: { messageId: edit.messageId, text },
      } }, owner);
      saved = true;
      if (!currentEditRoute()) return;
      const current = await invoke('read_desktop_workspace', { workspaceId }, owner);
      if (!currentEditRoute()) return;
      owner.current = current;
      let historyReadFailed = false;
      try {
        const history = await invoke('read_desktop_workbench_history', { workspaceId }, owner);
        if (!currentEditRoute()) return;
        owner.history = history;
      } catch { historyReadFailed = true; }
      if (!currentEditRoute()) return;
      owner.dialog = null;
      owner.status = historyReadFailed ? '编辑版本已保存；历史操作状态暂未回读。' : '编辑版本已保存为本地新分支；旧消息和回答仍在分支历史中。';
      owner.error = '';
      recomputeConversationFind();
      render();
    } catch (error) {
      if (!currentEditRoute()) return;
      edit.submitting = false;
      edit.failure = saved ? `编辑已保存，但回读未完成：${String(error)}` : `编辑未保存：${String(error)}`;
      render();
    }
    return;
  }
  if (action === 'select-conversation-branch') {
    event.preventDefault();
    const owner = state;
    const branchDialog = owner.dialog;
    if (branchDialog?.kind !== 'conversation-branches' || branchDialog.submitting || !native) return;
    const workspaceId = branchDialog.workspaceId;
    const conversationId = branchDialog.conversationId;
    const currentBranchRoute = () => state === owner && owner.dialog === branchDialog
      && owner.selectedConversationId === conversationId && owner.current?.summary?.id === workspaceId;
    const conversation = owner.current?.summary?.id === workspaceId ? resolveConversation(owner.current, conversationId) : null;
    const leafId = target.dataset.leafId;
    if (!conversation || conversation.archived || conversation.deleted || owner.selectedConversationId !== conversationId || !conversationBranchLeaves(conversation).some(leaf => leaf.id === leafId)) return;
    branchDialog.submitting = true;
    branchDialog.failure = '';
    render();
    let saved = false;
    try {
      await invokeForAuthorizedDataAreaOperation(tauriBridge, 'mutate_desktop_domain', { args: {
        intentId: intent('switch-branch'), workspaceId, entity: 'conversation', action: 'switchLeaf',
        objectId: conversationId, expectedRevision: conversation.revision, fields: { messageId: leafId },
      } }, owner);
      saved = true;
      if (!currentBranchRoute()) return;
      const current = await invoke('read_desktop_workspace', { workspaceId }, owner);
      if (!currentBranchRoute()) return;
      owner.current = current;
      let historyReadFailed = false;
      try {
        const history = await invoke('read_desktop_workbench_history', { workspaceId }, owner);
        if (!currentBranchRoute()) return;
        owner.history = history;
      } catch { historyReadFailed = true; }
      if (!currentBranchRoute()) return;
      owner.dialog = null;
      owner.status = historyReadFailed ? '分支已切换；历史操作状态暂未回读。' : '已切换到选中的本地消息分支；其他分支仍可切回。';
      owner.error = '';
      recomputeConversationFind();
      render();
    } catch (error) {
      if (!currentBranchRoute()) return;
      branchDialog.submitting = false;
      branchDialog.failure = saved ? `分支已保存，但回读未完成：${String(error)}` : `分支切换未完成：${String(error)}`;
      render();
    }
    return;
  }
  if (action === 'reference-message-to-other-area') {
    event.preventDefault();
    if (areaReferencePending) return;
    const sourceOwner = state;
    const conversation = resolveConversation(sourceOwner.current, sourceOwner.selectedConversationId);
    const message = conversation?.messages?.find(item => item.id === target.dataset.messageId);
    sourceOwner.assistantMessageMenu = null;
    if (!native || !sourceOwner.runtimeInfo?.dataAreasReady || !sourceOwner.current || !conversation || !message) {
      sourceOwner.error = '当前消息或另一数据区不可用，未写入引用。';
      render();
      return;
    }
    const targetArea = sourceOwner.dataArea === 'CHAT' ? 'WORK' : 'CHAT';
    areaReferencePending = true;
    sourceOwner.status = '正在把所选文字写入另一数据区草稿…';
    render();
    try {
      const receipt = await invoke('reference_desktop_message_to_other_area', {
        workspaceId: sourceOwner.current.summary.id,
        conversationId: conversation.id,
        messageId: message.id,
        expectedConversationRevision: Number(conversation.revision),
        targetWorkspaceId: dataAreaStates.get(targetArea)?.current?.summary?.id || null,
      }, sourceOwner);
      await switchDataArea(targetArea, targetArea === 'WORK' ? 'work' : 'chat');
      if (state.dataArea !== targetArea) throw new Error('目标数据区尚未就绪；引用已留在目标草稿');
      if (state.current?.summary?.id !== receipt.workspaceId) {
        state.current = await invoke('read_desktop_workspace', { workspaceId: receipt.workspaceId });
      }
      state.selectedConversationId = null;
      state.selectedWorkProjectId = null;
      await loadOrdinaryComposerDraft();
      state.status = '所选文字已写入另一数据区的未发送草稿；来源保持不变。';
      state.error = '';
      render();
    } catch (error) {
      if (error?.code === 'STALE_DATA_AREA') return;
      if (state === sourceOwner) sourceOwner.error = `文字引用未完成：${String(error)}`;
      else state.error = `目标草稿回读未完成：${String(error)}`;
      render();
    } finally {
      areaReferencePending = false;
    }
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
    const owner = state;
    const workspaceId = owner.current?.summary?.id;
    const conversationId = owner.selectedConversationId;
    const assistantMessageId = target.dataset.messageId;
    owner.assistantMessageMenu = null;
    render();
    try {
      const record = native && workspaceId && conversationId && assistantMessageId
        ? await invoke('read_desktop_ordinary_chat_context_record', { workspaceId, conversationId, assistantMessageId }, owner)
        : owner.contextSelectionRecords.find(item => item.assistantMessageId === assistantMessageId) || null;
      if (state !== owner || state.current?.summary?.id !== workspaceId
        || state.selectedConversationId !== conversationId) return;
      owner.dialog = { kind: 'assistant-answer-information', record };
      owner.error = '';
    } catch (error) {
      if (state !== owner || error?.code === 'STALE_DATA_AREA') return;
      owner.error = `回答信息读取失败：${String(error)}`;
    }
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
    const owner = state;
    owner.assistantMessageMenu = null;
    owner.dialog = null;
    const conversation = resolveConversation(owner.current, owner.selectedConversationId);
    if (!native || !owner.current || !conversation) { owner.error = '当前没有可分支的本地会话。'; render(); return; }
    const workspaceId = owner.current.summary.id;
    const sourceConversationId = conversation.id;
    let savedBranchId = null;
    try {
      const receipt = await invokeForAuthorizedDataAreaOperation(tauriBridge, 'mutate_desktop_domain', { args: {
        intentId: intent('message-branch'), workspaceId, entity: 'conversation',
        action: 'branchFromMessage', objectId: sourceConversationId, expectedRevision: conversation.revision,
        fields: { messageId: target.dataset.messageId },
      } }, owner);
      savedBranchId = receipt.objectId;
      if (state !== owner || owner.current?.summary?.id !== workspaceId) return;
      if (owner.selectedConversationId === sourceConversationId) {
        owner.selectedConversationId = savedBranchId;
        owner.status = '已从该持久化消息创建本地分支；保留前缀与来源，不连接 Provider。';
        owner.error = '';
      }
      await refresh();
    } catch (error) {
      if (state !== owner || owner.current?.summary?.id !== workspaceId) return;
      if (savedBranchId && owner.selectedConversationId === savedBranchId) {
        owner.error = `分支已保存在本机，但列表回读未完成：${String(error)}`;
      } else if (owner.selectedConversationId === sourceConversationId) {
        owner.error = `创建分支被拒绝：${String(error)}`;
      } else return;
      render();
    }
    return;
  }
  if (action === 'toggle-temporary-chat') { if (state.temporaryConversation) await leaveTemporaryChat(); else await enterTemporaryChat(); return; }
  if (action === 'pick-composer-image' || action === 'pick-composer-file') { await pickComposerAttachment(action === 'pick-composer-image' ? 'image' : 'file'); return; }
  if (action === 'remove-composer-attachment') {
    if (state.temporaryConversation) {
      const temporaryId = state.temporaryConversation.temporaryId;
      try { state.temporaryConversation = await invoke('remove_desktop_temporary_attachment', { args: { temporaryId, attachmentId: target.dataset.id } }); state.error = ''; render(); } catch (error) { if (!handleExpiredTemporary(error, temporaryId)) { state.error = `临时附件未移除：${String(error)}`; render(); } }
    } else { state.composerAttachments = state.composerAttachments.filter(item => item.id !== target.dataset.id); writeComposerAttachments(target.dataset.id); render(); }
    return;
  }
  if (action === 'set-appearance-mode') { updateAppearance('mode', target.dataset.value); return; }
  if (action === 'set-font-size') { updateAppearance('fontSize', target.dataset.value); return; }
  if (action === 'set-theme-color') { updateAppearance('themeColor', target.dataset.value); return; }
  if (action === 'toggle-conversation-favorite' || action === 'context-menu-favorite') {
    const owner = state;
    const sidebarWorkspace = captureCloudSidebarWorkspace();
    if (!await ensureConversationActionWorkspace(target)) return;
    if (state !== owner) return;
    const workspaceId = owner.current?.summary?.id;
    await toggleFavoriteConversation(target.dataset.id, { renderAfter: false });
    if (state !== owner || owner.current?.summary?.id !== workspaceId) return;
    await restoreCloudSidebarWorkspace(sidebarWorkspace);
    if (state !== owner) return;
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
    if (!await ensureConversationActionWorkspace(target)) return;
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
    if (!await ensureConversationActionWorkspace(target)) return;
    state.contextMenu = null;
    const conversation = workspace()?.exchange?.conversations?.find(item => item.id === target.dataset.id);
    if (!conversation) return;
    await saveMarkdown(conversationMarkdown(conversation), safeMarkdownName(conversation?.title), '当前会话');
    return;
  }
  if (action === 'context-menu-cancel-sync') {
    const owner = state;
    const sidebarWorkspace = captureCloudSidebarWorkspace();
    if (!await ensureConversationActionWorkspace(target)) return;
    if (state !== owner) return;
    if (!native || !state.current) return;
    const cloudWorkspaceId = owner.current.summary.id;
    try {
      await invoke('cancel_desktop_conversation_sync', {
        workspaceId: cloudWorkspaceId,
        conversationId: target.dataset.id,
      }, owner);
      if (state !== owner) return;
      await loadDesktopAccountSync();
      if (state !== owner) return;
      state.cloudConversations = state.cloudConversations.filter(item => !(item.workspaceId === cloudWorkspaceId && item.id === target.dataset.id));
      advanceCloudListRead();
      persistCloudConversationCache();
      state.contextMenu = null;
      state.status = '已取消同步；云端副本已删除，本地对话保留。';
      state.error = '';
    } catch (error) {
      if (state !== owner || error?.code === 'STALE_DATA_AREA') return;
      state.error = `取消同步未完成：${String(error)}`;
    }
    if (state !== owner) return;
    await restoreCloudSidebarWorkspace(sidebarWorkspace).catch(() => {});
    if (state !== owner) return;
    render();
    return;
  }
  if (action === 'context-menu-pin') {
    if (isCloudListConversationTarget(target)) {
      await setCloudListConversationPinned(target, target.dataset.pinned !== 'true');
      return;
    }
    state.contextMenu = null;
    render();
    await mutateConversationLifecycle(target, 'setPinned', { pinned: target.dataset.pinned !== 'true' }, target.dataset.pinned === 'true' ? '会话已取消置顶。' : '会话已置顶。');
    return;
  }
  if (action === 'context-menu-unread') {
    const sidebarWorkspace = captureCloudSidebarWorkspace();
    if (!await ensureConversationActionWorkspace(target)) return;
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
    state.contextMenu = null;
    render();
    await mutateConversationLifecycle(target, target.dataset.archived === 'true' ? 'restore' : 'archive', {}, target.dataset.archived === 'true' ? '会话已恢复。' : '会话已归档。');
    return;
  }
  if (action === 'context-menu-rename' || action === 'open-conversation-rename') {
    if (!await ensureConversationActionWorkspace(target)) return;
    const conversation = workspace()?.exchange?.conversations?.find(item => item.id === target.dataset.id);
    state.contextMenu = null;
    state.dialog = { kind: 'conversation-rename', id: target.dataset.id, revision: Number(target.dataset.revision), title: conversation?.title || '', workspaceId: target.dataset.workspaceId || null };
    render();
    // Select only on explicit entry, never on input or a later modal render.
    const input = document.querySelector('#conversation-rename');
    input?.focus({ preventScroll: true });
    input?.select();
    return;
  }
  if (action === 'context-menu-project') {
    if (!await ensureConversationActionWorkspace(target)) return;
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
    resetNewConversationComposer();
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
    void loadMarkdownKnowledgeTask().catch(() => {});
  void loadJsonKnowledgeTask().catch(() => {});
  void pdfKnowledge.load().catch(() => {});
  void webKnowledge.load().catch(() => {});
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
    state.p6gSelection = { ...(state.p6gSelection || {}), pickerTier: ['DEEP', 'DAILY', 'RELAYCORE'].includes(target.dataset.tier) ? target.dataset.tier : 'DAILY' };
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
    if (searchImeTarget === event.target || event.isComposing) {
      supersedeFullSearch();
      if (fullSearchDebounce !== null) window.clearTimeout(fullSearchDebounce);
      fullSearchDebounce = null;
    } else scheduleFullSearch();
  }
  if (event.target.id === 'model-service-api-key') {
    const storedMask = '••••••••••••••••••••••••••••••••';
    const value = !state.modelCredentialEdited && !state.modelCredentialVisible && event.target.value.startsWith(storedMask)
      ? event.target.value.slice(storedMask.length)
      : event.target.value;
    state.modelCredentialDraft = value;
    state.modelCredentialEdited = true;
    state.modelSettingsNotice = '当前修改尚未保存，请先保存再测试连接。';
    state.modelSettingsError = '';
    const feedback = app.querySelector('[data-model-provider-feedback]');
    if (feedback?.dataset.providerId === state.modelProviderId) {
      feedback.hidden = false;
      feedback.textContent = state.modelSettingsNotice;
      feedback.classList.remove('error');
      feedback.setAttribute('role', 'status');
    }
    // Update the controls without replacing the focused password input.
    const busy = state.modelSettingsSaving || state.modelSettingsTesting;
    const save = app.querySelector('[data-action="save-model-service-settings"]');
    if (save) save.disabled = busy || !value.trim();
    const probe = app.querySelector('[data-action="test-model-service-connection"]');
    if (probe) probe.disabled = true;
    const reveal = app.querySelector('[data-action="reveal-model-service-credential"]');
    if (reveal) reveal.disabled = busy || (!selectedModelService()?.credentialStored && !value);
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
  const editorKind = state.dialog?.kind;
  if ((['knowledge', 'memory'].includes(editorKind) && event.target?.id === `${editorKind}-project`) || (editorKind === 'markdown-knowledge-review' && event.target?.id === 'markdown-knowledge-project') || (editorKind === 'json-knowledge-review' && event.target?.id === 'json-knowledge-project') || (editorKind === 'pdf-knowledge-review' && event.target?.id === 'pdf-knowledge-project') || (editorKind === 'web-knowledge-review' && event.target?.id === 'web-knowledge-project')) {
    state.dialog.draft ??= {};
    state.dialog.draft[event.target.id] = event.target.value;
  }
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
  if ((event.target.id === 'chat-search' || event.target.id === 'full-search-input') && event.key === 'Escape'
      && (searchImeTarget === event.target || event.isComposing || event.keyCode === 229)) return;
  if ((event.target.id === 'chat-search' || event.target.id === 'full-search-input') && event.key === 'Enter') {
    if (searchImeTarget === event.target || event.isComposing || event.keyCode === 229 || Date.now() - searchImeEndedAtMs < 100) return;
    event.preventDefault();
    void submitLocalSearch();
  }
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
  invoke('read_p6h_diagnostics_status').then(enabled => { p6hDiagnosticsEnabled = Boolean(enabled); }).catch(() => {});
}

app.addEventListener('click', event => {
  const target = event.target.closest?.('[data-action]');
  const action = target?.dataset.action;
  if (!['select-local-backup-area', 'export-local-backup', 'import-local-backup', 'select-local-backup-replace', 'clear-local-backup-replace', 'restore-local-backup', 'cancel-local-restore'].includes(action)) return;
  event.preventDefault();
  event.stopImmediatePropagation();
  if (action === 'select-local-backup-area') {
    const area = target.dataset.area;
    if (!native || !state.runtimeInfo?.dataAreasReady || !['CHAT', 'WORK'].includes(area)
        || [...dataAreaStates.values()].some(owner => owner.localBackup?.working)) return;
    state.localBackupArea = area;
    void loadSelectedBackupStatus().catch(error => {
      if (error?.code !== 'STALE_DATA_AREA') selectedBackupAreaState().localBackup.error = `本机备份状态读取失败：${localBackupErrorMessage(error)}`;
    }).finally(render);
    render();
  } else if (action === 'export-local-backup') void exportLocalBackup();
  else if (action === 'import-local-backup') void importLocalBackup();
  else if (action === 'select-local-backup-replace') { const owner = selectedBackupAreaState(); owner.localBackup = { ...owner.localBackup, replaceLocal: true, error: '' }; render(); }
  else if (action === 'clear-local-backup-replace') { const owner = selectedBackupAreaState(); owner.localBackup = { ...owner.localBackup, replaceLocal: false }; render(); }
  else if (action === 'restore-local-backup') void restoreLocalBackup();
  else if (action === 'cancel-local-restore') void cancelLocalRestore();
}, true);

app.addEventListener('click', event => {
  const target = event.target.closest?.('[data-action]');
  const action = target?.dataset.action;
  if (!['show-google-login-requirements', 'sign-in-google-account', 'cancel-google-account-sign-in', 'sign-out-google-account', 'load-account-cloud-documents', 'show-recent-conversation-list', 'show-cloud-conversation-list', 'open-cloud-conversation', 'choose-selected-local-sync-start', 'toggle-periodic-account-sync', 'context-menu-sync', 'reconcile-account-sync'].includes(action)) return;
  const owner = state;
  const readToken = action === 'load-account-cloud-documents' ? Symbol('cloud-list-read') : null;
  event.preventDefault();
  event.stopImmediatePropagation();
  if (action === 'load-account-cloud-documents') {
    if (cloudListReadPending.has(owner)) return;
    cloudListReadPending.set(owner, readToken);
  }
  void (async () => {
    let signInToken = null;
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
        if (state.dataArea === 'WORK') {
          const opened = (state.current?.exchange?.conversations || []).find(item => item.id === state.selectedConversationId);
          state.selectedWorkProjectId = opened?.projectId || null;
        }
        state.pane = state.dataArea === 'WORK' ? 'work' : 'chat';
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
        if (state !== owner) return;
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
          state.status = receipt.status === 'UP_TO_DATE' ? '所选对话已是最新版本。' : '所选对话已同步到南枫云并通过回读校验。';
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
        signInToken = ++accountSignInToken;
        accountSignInPending = true;
        state.status = '等待系统浏览器完成 Google 授权…';
        render();
        const signedIn = await invoke('sign_in_desktop_google_account');
        if (signInToken !== accountSignInToken) return;
        invalidateAccountCloudLists();
        state.accountSync = signedIn;
        await restoreCachedCloudConversationList();
        state.status = '已建立南枫云应用账号会话；可直接读取云端会话。';
      } else if (action === 'cancel-google-account-sign-in') {
        if (!accountSignInPending) return;
        await invoke('cancel_desktop_google_account_sign_in');
        ++accountSignInToken;
        accountSignInPending = false;
        await loadDesktopAccountSync();
        state.status = state.accountSync?.email
          ? '已停止授权等待；账号状态已回读，当前仍为已登录。'
          : '已取消本次 Google 授权等待；本机数据未改变。';
      } else if (action === 'sign-out-google-account') {
        state.accountSync = await invoke('sign_out_desktop_google_account');
        ++accountSignInToken;
        accountSignInPending = false;
        invalidateAccountCloudLists();
        clearCloudConversationCache();
        state.status = '已退出账号；本机对话与工作区保留不变。';
      } else if (action === 'load-account-cloud-documents') {
        const readGeneration = advanceCloudListRead(owner);
        beginAccountSyncProgress('read');
        state.status = '正在读取云端列表…';
        render();
        await waitForAccountSyncProgressPaint();
        if (state !== owner) return;
        const receipt = await invoke('restore_all_desktop_cloud_conversations');
        if (state !== owner) return;
        const restored = Array.isArray(receipt?.restored) ? receipt.restored : [];
        const refreshedRows = await cloudRowsFromLocalEntries(restored);
        if (state !== owner) return;
        // A newer read, explicit cloud removal, or sign-out won the race. Its
        // list state is authoritative; this old response must not put rows back.
        if (readGeneration !== currentCloudListRead(owner)) return;
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
        state.pane = state.dataArea === 'WORK' ? 'work' : 'chat';
        const projectedKeys = new Set(refreshedRows.map(item => `${item.workspaceId}:${item.id}`));
        const projectionFailedCount = dedupeCloudConversationEntries(restored)
          .filter(item => !projectedKeys.has(`${item.workspaceId}:${item.conversationId}`)).length;
        const feedback = cloudReadFeedback({ ...receipt, projectionFailedCount });
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
      if (state !== owner || error?.code === 'STALE_DATA_AREA') return;
      if (action === 'sign-in-google-account' && signInToken !== accountSignInToken) return;
      state.error = `账号与同步操作未完成：${accountSyncErrorMessage(error)}`;
      if (action === 'sign-in-google-account') state.status = 'Google 登录未完成；请查看错误说明，保留本机数据并核对账号状态后重试。';
      if (action === 'context-menu-sync') await completeAccountSyncProgress('请检查网络或登录后重试。', 'error');
      if (action === 'load-account-cloud-documents') await completeAccountSyncProgress('请检查网络或登录后重试。', 'error');
      if (['context-menu-sync', 'reconcile-account-sync'].includes(action)) notifyAccountSync('attention');
    } finally {
      // completeAccountSyncProgress owns the result lifetime. Clearing it here
      // immediately after the receipt/error turns the acknowledgement into a
      // one-frame flash, so sync/read must leave the completed card in place
      // until its own timeout expires.
      if (action === 'sign-in-google-account' && signInToken === accountSignInToken) accountSignInPending = false;
      if (action === 'load-account-cloud-documents' && cloudListReadPending.get(owner) === readToken) cloudListReadPending.delete(owner);
      if (state !== owner && ['context-menu-sync', 'load-account-cloud-documents'].includes(action)) owner.accountSyncProgress = null;
      if (state === owner) render();
      else if (action === 'sign-in-google-account' && signInToken === accountSignInToken) {
        // Login is shared across areas. Its old-area IPC receipt is deliberately
        // discarded after navigation; read the actual session into the visible area.
        const visibleOwner = state;
        render();
        void loadDesktopAccountSync().then(() => { if (state === visibleOwner) render(); }).catch(() => {});
      }
    }
  })();
}, true);

async function bootDesktopShell() {
  let startupStage = 'WORKSPACE_READBACK';
  const initialDraftGeneration = composerDraftRouteGeneration;
  try {
    if (native) {
      startupStage = 'FULL_DELETE_OUTCOME_READBACK';
      const outcome = await invoke('read_desktop_full_delete_outcome');
      if (outcome?.status === 'COMPLETED') {
        window.localStorage.clear();
        await invoke('acknowledge_desktop_full_delete_outcome');
        const notice = outcome.cleanupPending
          ? '双区本机业务数据已删除；旧隔离区清理待下次启动继续。'
          : '双区本机业务数据已删除并经重启回读。';
        try { window.sessionStorage.setItem('nfai-full-delete-notice', notice); } catch { /* optional UI notice */ }
        window.location.reload();
        return;
      }
      if (outcome) {
        state.error = outcome.status === 'ROLLED_BACK'
          ? '全部删除未提交；旧双区数据已恢复。请核对数据后重新预览。'
          : '全部删除未开始；原双区数据保留。请核对数据后重新预览。';
        await invoke('acknowledge_desktop_full_delete_outcome');
      }
      try {
        const notice = window.sessionStorage.getItem('nfai-full-delete-notice');
        if (notice) {
          state.status = notice;
          window.sessionStorage.removeItem('nfai-full-delete-notice');
        }
      } catch { /* optional UI notice */ }
    }
    startupStage = 'DRAFT_CLOSE_GUARD';
    await installComposerDraftCloseGuard();
    startupStage = 'WORKSPACE_READBACK';
    await refresh();
    if (native && !state.current && !state.runtimeInfo?.automaticWorkSuppressed) {
      startupStage = 'LOCAL_WORKSPACE_INITIALIZATION';
      state.current = await invoke('ensure_desktop_local_workspace');
      state.workspaces = [state.current.summary];
    }
    startupStage = 'ORDINARY_DRAFT_READBACK';
    if (initialDraftGeneration === composerDraftRouteGeneration) await loadOrdinaryComposerDraft({ preferUnbound: true });
    if (native) {
      startupStage = 'C16_VISUAL_ACCEPTANCE_GATE';
      c16ThemePreview = await invoke('read_desktop_c16_visual_acceptance_state') || '';
      c16PreviewState = parseC16Preview(c16ThemePreview);
    }
    if (native) {
      startupStage = 'LOCAL_BACKUP_READBACK';
      await loadDesktopLocalBackupStatus();
      startupStage = 'APP_SETTINGS_READBACK';
      await loadDesktopAppSettings({ preserveUnsavedDraft: true });
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
      await loadModelServiceSettings({ preserveUnsavedDraft: true });
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
    startupUiStage = 'ready';
    const queuedRoute = queuedStartupAreaRoute;
    queuedStartupAreaRoute = null;
    if (queuedRoute) void switchDataArea(queuedRoute.area, queuedRoute.pane).catch(error => {
      if (error?.code === 'STALE_DATA_AREA') return;
      state.error = `切换数据区失败：${String(error)}`;
      render();
    });
  } catch (error) {
    startupUiStage = 'failed';
    queuedStartupAreaRoute = null;
    console.error('Desktop startup readback failed', startupStage, error);
    state.error = `本地工作区启动读取未完成（${startupStage}）；请重新打开应用后再试。`;
    state.status = `启动未完成（${startupStage}）；已提交的本机数据保留。`;
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
