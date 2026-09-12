import { icon, icons } from './icon-source.mjs';
import { renderSafeMarkdown } from './safe-markdown.mjs';

const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, character => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' })[character]);

export const TRANSCRIPTION_PREVIEW_STATE = Object.freeze({
  settings: { modelId: 'qwen3-asr-flash', languageCode: null, outputFormat: 'md', revision: 0, senseVoiceAvailable: false, senseVoiceStatus: '验证中／保留实验；Desktop 本地引擎与许可尚未完成', sourceMaxBytes: 2147483648, sourceMaxDurationMillis: 43200000 },
  tasks: [],
});

const STATE_LABELS = Object.freeze({ QUEUED: '等待开始', WAITING_INPUT: '等待输入', WAITING_MODEL: '等待模型', PREPARING: '准备文件', TRANSCRIBING: '正在转写', EXPORTING: '生成结果', RECOVERY_REQUIRED: '需要恢复', NO_SPEECH: '未识别到语音', COMPLETED: '已完成', FAILED: '失败', CANCELLED: '已取消' });
const DOCUMENT_STATE_LABELS = Object.freeze({ QUEUED: '待确认', PREPARING: '等待处理', TRANSCRIBING: '正在转写', EXPORTING: '正在保存', COMPLETED: '已生成', FAILED: '转写失败', RECOVERY_REQUIRED: '需要恢复', CANCELLED: '已取消' });
const terminal = state => ['COMPLETED', 'NO_SPEECH', 'FAILED', 'CANCELLED'].includes(state);
const canRetry = state => ['FAILED', 'RECOVERY_REQUIRED', 'WAITING_MODEL', 'CANCELLED'].includes(state);
const canCancel = state => ['QUEUED', 'WAITING_INPUT', 'WAITING_MODEL', 'PREPARING', 'TRANSCRIBING', 'EXPORTING', 'RECOVERY_REQUIRED', 'FAILED'].includes(state);
const isDocumentTask = task => task?.modelId === 'glm-ocr';
const stateLabel = task => (isDocumentTask(task) ? DOCUMENT_STATE_LABELS : STATE_LABELS)[task.state] || task.state;
const formatBytes = value => Number(value || 0) < 1024 * 1024 ? `${Math.max(1, Math.round(Number(value || 0) / 1024))} KB` : `${(Number(value || 0) / 1024 / 1024).toFixed(1)} MB`;
const formatTime = value => { const total = Math.max(0, Number(value || 0)); const hours = Math.floor(total / 3600000); const minutes = Math.floor(total / 60000) % 60; const seconds = Math.floor(total / 1000) % 60; return `${hours ? `${String(hours).padStart(2, '0')}:` : ''}${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`; };
const tokenSummary = task => task.inputTokens == null && task.outputTokens == null ? 'Token 未返回' : `${Number(task.inputTokens || 0)} / ${Number(task.outputTokens || 0)} Token`;
const costSummary = task => task.inputTokens == null && task.outputTokens == null ? '费用未返回' : `¥${(Number(task.estimatedChargeMicros || 0) / 1000000).toFixed(6)}（估算）`;
const copyResultAction = taskId => `<button class="transcription-copy-action" data-action="copy-transcription-result" data-copy-action data-task-id="${escapeHtml(taskId)}" aria-label="复制全文" title="复制全文">${icon(icons.copy, '复制全文')}</button>`;
// Older persisted transcription task markup is retained below; normalize it at the
// single render seam so both legacy audio/video and current document results use
// the same icon-only control.
const withCopyIcons = markup => markup.replaceAll(/<button data-action="copy-transcription-result" data-task-id="([^"]+)">复制全文<\/button>/g, (_, taskId) => copyResultAction(taskId));

function taskRow(task, selectedTaskId, busyTaskId) {
  const progress = task.totalDurationMillis ? Math.min(100, Math.round(Number(task.progressMillis || 0) * 100 / Number(task.totalDurationMillis))) : task.state === 'COMPLETED' ? 100 : 0;
  const documentTask = isDocumentTask(task);
  const glyph = documentTask ? icons.fileText : task.sourceMimeType?.startsWith('video/') ? icons.play : icons.audio;
  const documentFacts = `${task.pageCount == null ? '页数未返回' : `${Number(task.pageCount)} 页`} · ${tokenSummary(task)} · ${costSummary(task)} · ${new Date(Number(task.updatedAtMillis || 0)).toLocaleString('zh-CN')}`;
  return `<button class="transcription-task-row ${task.id === selectedTaskId ? 'selected' : ''}" data-action="select-transcription-task" data-task-id="${escapeHtml(task.id)}"><span class="transcription-file-icon" aria-hidden="true">${icon(glyph, documentTask ? '图片或 PDF' : '音频或视频')}</span><span><strong>${escapeHtml(task.sourceDisplayName)}${documentTask && task.state === 'COMPLETED' ? ' → Markdown' : ''}</strong><small>${escapeHtml(stateLabel(task))} · ${formatBytes(task.sourceByteCount)}${busyTaskId === task.id ? ' · 执行中' : ''}</small>${documentTask ? `<small>${escapeHtml(documentFacts)}</small>` : ''}</span><span class="transcription-task-percent">${progress}%</span></button>`;
}

function emptyState() {
  return `<section class="transcription-empty">${icon(icons.fileText, '图片或 PDF')}<h2>还没有转写结果</h2><p>拖入图片或 PDF，或点击下方按钮开始。</p></section>`;
}

function sourceCard(task) {
  const sourceKind = task.sourceMimeType === 'application/pdf' ? 'PDF' : '图片';
  return `<section class="transcription-source-section"><h3>原始文件</h3><button class="transcription-file-card" data-action="preview-transcription-source" data-task-id="${escapeHtml(task.id)}"><span class="transcription-file-icon" aria-hidden="true">${icon(icons.fileText, sourceKind)}</span><span><strong>${escapeHtml(task.sourceDisplayName)}</strong><small>${escapeHtml(task.sourceMimeType)} · ${formatBytes(task.sourceByteCount)}</small><small class="transcription-file-hint">点击查看原始 ${sourceKind}</small></span>${icon(icons.chevronRight, '查看')}</button></section>`;
}

function documentLifecycle(task, busyTaskId) {
  const running = busyTaskId === task.id || ['PREPARING', 'TRANSCRIBING', 'EXPORTING'].includes(task.state);
  if (task.state === 'QUEUED') return `<section class="transcription-lifecycle-card"><h3>开始转写</h3><button class="primary transcription-full-action" data-action="run-transcription-task" data-task-id="${escapeHtml(task.id)}" ${running ? 'disabled' : ''}>开始转写</button></section>`;
  if (['PREPARING', 'TRANSCRIBING', 'EXPORTING'].includes(task.state)) return `<section class="transcription-lifecycle-card transcription-running" role="status"><span class="transcription-spinner" aria-hidden="true"></span><span><strong>${escapeHtml(stateLabel(task))}</strong><small>离开页面后任务仍会继续。</small></span></section>`;
  if (task.state === 'FAILED' || task.state === 'RECOVERY_REQUIRED') return `<section class="transcription-lifecycle-card transcription-failure" role="status"><strong>${escapeHtml(task.userMessage || stateLabel(task))}</strong><button class="primary transcription-full-action" data-action="retry-transcription-task" data-task-id="${escapeHtml(task.id)}">重新转写</button></section>`;
  return '';
}

function completedDocument(task, text) {
  return `<section class="transcription-result transcription-document-result"><div class="transcription-section-head"><div><h3>Markdown 文件</h3></div><button data-action="continue-chat-with-transcription" data-task-id="${escapeHtml(task.id)}">加入新对话</button></div><button class="transcription-file-card" data-action="preview-transcription-result" data-task-id="${escapeHtml(task.id)}"><span class="transcription-file-icon" aria-hidden="true">${icon(icons.fileText, 'Markdown')}</span><span><strong>${escapeHtml(task.sourceDisplayName.replace(/\.[^.]+$/, '') || 'GLM-OCR')}-OCR.md</strong><small>text/markdown</small><small class="transcription-file-hint">点击查看文本内容</small></span>${icon(icons.chevronRight, '查看')}</button><article class="transcription-text chat-markdown-body">${renderSafeMarkdown(text)}</article><div class="transcription-export">${copyResultAction(task.id)}<span>导出</span>${['txt', 'md', 'docx'].map(format => `<button data-action="export-transcription-task" data-task-id="${escapeHtml(task.id)}" data-format="${format}">${format.toUpperCase()}</button>`).join('')}</div></section>`;
}

function taskDetail(task, busyTaskId) {
  const documentTask = isDocumentTask(task);
  const progress = task.totalDurationMillis ? Math.min(100, Math.round(Number(task.progressMillis || 0) * 100 / Number(task.totalDurationMillis))) : task.state === 'COMPLETED' ? 100 : 0;
  const text = (task.segments || []).map(segment => segment.text).join('\n\n');
  const running = busyTaskId === task.id || ['PREPARING', 'TRANSCRIBING', 'EXPORTING'].includes(task.state);
  if (documentTask) return `<section class="transcription-detail transcription-document-detail" aria-label="转写任务详情"><header><div><h2>${escapeHtml(task.sourceDisplayName)}</h2><small>GLM-OCR · ${formatBytes(task.sourceByteCount)}</small></div><span class="transcription-state state-${escapeHtml(task.state.toLowerCase())}">${escapeHtml(stateLabel(task))}</span></header>${sourceCard(task)}${documentLifecycle(task, busyTaskId)}<div class="transcription-facts"><span>页数 <b>${task.pageCount == null ? '未返回' : Number(task.pageCount)}</b></span><span>Token <b>${escapeHtml(tokenSummary(task))}</b></span><span>Attempt <b>${Number(task.attemptCount || 0)}</b></span><span>请求 <b>${Number(task.providerRequestCount || 0)} 次</b></span><span>请求 ID <b>${escapeHtml(task.providerRequestId || '未返回')}</b></span><span>费用 <b>${escapeHtml(costSummary(task))}</b></span>${task.errorCode ? `<span>错误 <b>${escapeHtml(task.errorCode)}</b></span>` : ''}</div>${task.state === 'COMPLETED' ? completedDocument(task, text) : ''}${terminal(task.state) ? `<div class="transcription-actions"><button class="danger" data-action="ask-delete-transcription-task" data-task-id="${escapeHtml(task.id)}">删除任务</button></div>` : ''}</section>`;
  const exports = ['txt', 'md', 'srt', 'docx'];
  return `<section class="transcription-detail" aria-label="转写任务详情"><header><div><p>语音转写任务</p><h2>${escapeHtml(task.sourceDisplayName)}</h2><small>${escapeHtml(task.modelId)} · ${task.languageCode ? escapeHtml(task.languageCode.toUpperCase()) : '自动识别'} · ${formatBytes(task.sourceByteCount)}</small></div><span class="transcription-state state-${escapeHtml(task.state.toLowerCase())}">${escapeHtml(stateLabel(task))}</span></header><div class="transcription-progress" aria-label="任务进度 ${progress}%"><i style="width:${progress}%"></i></div><div class="transcription-facts"><span>进度 <b>${formatTime(task.progressMillis)} / ${task.totalDurationMillis ? formatTime(task.totalDurationMillis) : '--:--'}</b></span><span>请求 <b>${Number(task.providerRequestCount || 0)} 次</b></span><span>费用 <b>${escapeHtml(costSummary(task))}</b></span></div>${task.userMessage ? `<p class="transcription-notice ${task.state === 'FAILED' ? 'error' : ''}" role="status">${escapeHtml(task.userMessage)}</p>` : ''}<div class="transcription-actions"><button data-action="preview-transcription-source" data-task-id="${escapeHtml(task.id)}">预览原文件</button>${task.state === 'QUEUED' ? `<button class="primary" data-action="run-transcription-task" data-task-id="${escapeHtml(task.id)}" ${running ? 'disabled' : ''}>开始转写</button>` : ''}${canCancel(task.state) ? `<button data-action="cancel-transcription-task" data-task-id="${escapeHtml(task.id)}">取消任务</button>` : ''}${canRetry(task.state) ? `<button class="primary" data-action="retry-transcription-task" data-task-id="${escapeHtml(task.id)}">重试</button>` : ''}${terminal(task.state) ? `<button class="danger" data-action="ask-delete-transcription-task" data-task-id="${escapeHtml(task.id)}">删除任务</button>` : ''}</div>${task.state === 'COMPLETED' ? `<section class="transcription-result"><div class="transcription-section-head"><div><p>转写结果</p><h3>正文与切片级时间轴</h3></div><div><button data-action="copy-transcription-result" data-task-id="${escapeHtml(task.id)}">复制全文</button><button data-action="continue-chat-with-transcription" data-task-id="${escapeHtml(task.id)}">继续对话</button></div></div><article class="transcription-text">${text.split('\n').map(line => `<p>${escapeHtml(line) || '&nbsp;'}</p>`).join('')}</article><ol class="transcription-timeline">${(task.segments || []).map(segment => `<li><time>${formatTime(segment.startMillis)} → ${formatTime(segment.endMillis)}</time><p>${escapeHtml(segment.text)}</p></li>`).join('')}</ol><div class="transcription-export"><span>导出</span>${exports.map(format => `<button data-action="export-transcription-task" data-task-id="${escapeHtml(task.id)}" data-format="${format}">${format.toUpperCase()}</button>`).join('')}</div></section>` : ''}</section>`;
}

function pageHeader({ embedded = false } = {}) {
  if (embedded) return `<header class="desktop-utility-panel-header"><h1>南枫转写</h1></header>`;
  return `<header class="desktop-work-page-header"><span></span><h1>南枫转写</h1><button data-action="show-chat" aria-label="关闭南枫转写">${icon(icons.close, '关闭')}</button></header>`;
}

export function renderDesktopTranscriptionPage({ projection = TRANSCRIPTION_PREVIEW_STATE, selectedTaskId = null, native = false, busyTaskId = null, previewInteractive = false, evidenceLabel = '', embedded = false } = {}) {
  const tasks = projection?.tasks || [];
  const documentTasks = tasks.filter(isDocumentTask);
  const legacySpeechTasks = tasks.filter(task => !isDocumentTask(task));
  const selected = tasks.find(task => task.id === selectedTaskId) || documentTasks[0] || legacySpeechTasks[0] || null;
  const rows = [...documentTasks.map(task => taskRow(task, selected?.id, busyTaskId)), ...(legacySpeechTasks.length ? [`<p class="transcription-list-subhead">旧版音视频任务 · 仅保留已有记录</p>`, ...legacySpeechTasks.map(task => taskRow(task, selected?.id, busyTaskId))] : [])].join('');
  const pageBody = tasks.length ? `<div class="transcription-page-body"><div class="transcription-workspace"><aside><div class="transcription-list-head"><strong>全部转写</strong><span>${tasks.length}</span></div>${rows}</aside>${taskDetail(selected, busyTaskId)}</div></div>` : `<div class="transcription-empty-canvas transcription-page-body">${evidenceLabel ? `<p class="transcription-evidence">${escapeHtml(evidenceLabel)}</p>` : ''}${emptyState()}</div>`;
  const pickEnabled = native || previewInteractive;
  return `<section class="canvas transcription-page" aria-label="南枫转写">${pageHeader({ embedded })}${withCopyIcons(pageBody)}<footer class="transcription-page-footer"><button class="primary transcription-pick" data-action="pick-transcription-document" ${pickEnabled ? '' : 'disabled'}>${icon(icons.import, '选择文件')}<span>选择图片或 PDF</span></button></footer></section>`;
}
