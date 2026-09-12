import { icon, icons } from './icon-source.mjs';

const escapeHtml = value => String(value ?? '').replace(
  /[&<>"']/g,
  char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' })[char],
);

const divider = '<div class="android-settings-divider" aria-hidden="true"></div>';

const storageBytes = value => {
  const bytes = Math.max(0, Number(value || 0));
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 ** 2) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 ** 3) return `${(bytes / 1024 ** 2).toFixed(1)} MB`;
  return `${(bytes / 1024 ** 3).toFixed(1)} GB`;
};

const actionRow = ({ title, action = 'open-settings-page', page = '', disabled = false, working = false }) => `<button class="android-settings-action-row" data-action="${action}" ${page ? `data-page="${page}"` : ''} ${disabled ? 'disabled' : ''} ${working ? 'aria-busy="true"' : ''}><span>${escapeHtml(title)}</span>${working ? '<i class="android-settings-spinner" aria-label="正在处理"></i>' : page ? icon(icons.chevronRight, `进入${title}`) : ''}</button>`;
const actionGroup = (title, rows) => `<section class="android-settings-data-group"><h2>${escapeHtml(title)}</h2><div class="android-settings-card">${rows.join(divider)}</div></section>`;

function localBackupGroup(context) {
  const backup = context.localBackup || {};
  const preflight = backup.preflight;
  const disabled = !context.native || backup.working || backup.restartRequired;
  const operation = backup.operation;
  const actions = actionGroup('本机备份与恢复', [
    actionRow({ title: operation === 'EXPORT' ? '正在备份…' : '备份', action: 'export-local-backup', disabled, working: operation === 'EXPORT' }),
    actionRow({ title: operation === 'RESTORE' ? '正在恢复…' : '恢复', action: 'import-local-backup', disabled, working: operation === 'RESTORE' }),
  ]);
  if (backup.restartRequired) return `${actions}<section class="android-settings-card android-settings-status-card"><strong>恢复已完成</strong><p>为避免旧 SQLite 与页面引用，现请手动完全退出并重新打开 App；不会自动继续任何任务。</p></section>`;
  if (!preflight) return `${actions}${backup.notice ? `<p class="android-settings-helper success" role="status">${escapeHtml(backup.notice)}</p>` : ''}${backup.error ? `<p class="android-settings-helper error" role="alert">${escapeHtml(backup.error)}</p>` : ''}`;
  const counts = Object.entries(preflight.tableCounts || {}).sort(([left], [right]) => left.localeCompare(right)).map(([table, count]) => `${table} ${count}`).join(' · ');
  const hasConflict = (preflight.conflicts || []).length > 0;
  return `${actions}<section class="android-settings-card android-settings-backup-preflight">
    <strong>预检：格式 ${escapeHtml(preflight.format)} v${Number(preflight.version)} · Schema ${Number(preflight.schemaVersion)} · 资产 ${storageBytes(preflight.assetBytes)}</strong>
    <p>${escapeHtml(counts || '没有业务表记录')}</p>
    ${hasConflict ? `<p class="error">${escapeHtml(preflight.conflicts.join('\n'))}</p><button data-action="clear-local-backup-replace" ${backup.working ? 'disabled' : ''}>取消，不替换本地</button><button data-action="select-local-backup-replace" ${backup.working ? 'disabled' : ''}>${backup.replaceLocal ? '已选择：替换本地' : '选择替换本地（强确认）'}</button>` : ''}
    <button class="primary" data-action="restore-local-backup" ${backup.working || (hasConflict && !backup.replaceLocal) ? 'disabled' : ''}>恢复并要求重启</button>
    <button data-action="cancel-local-restore" ${backup.working ? 'disabled' : ''}>取消此次恢复</button>
  </section>${backup.notice ? `<p class="android-settings-helper success" role="status">${escapeHtml(backup.notice)}</p>` : ''}${backup.error ? `<p class="android-settings-helper error" role="alert">${escapeHtml(backup.error)}</p>` : ''}`;
}

export function renderLocalDataImportExportPage(context) {
  const { data, native } = context;
  return `<div class="android-settings-page android-settings-data-page">
    ${actionGroup('对话', [actionRow({ title: '导入 ChatGPT JSON', action: 'select-chatgpt-export' }), actionRow({ title: '导入 Claude JSON', action: 'select-claude-export' }), actionRow({ title: '导入结果', page: 'json-import-results' })])}
    ${actionGroup('', [actionRow({ title: '导入 ChatGPT ZIP', action: 'select-p6k-chatgpt-zip' }), actionRow({ title: '导入 Claude ZIP', action: 'select-p6k-claude-zip' }), actionRow({ title: '导入结果', page: 'zip-import-results' })])}
    ${actionGroup('工作区', [
      actionRow({ title: '导入工作区', action: 'start-import', disabled: !native }),
      actionRow({ title: '导出工作区', action: 'start-export', disabled: !native || !data }),
    ])}
    ${localBackupGroup(context)}
  </div>`;
}

function importSummary(values) {
  const count = id => Number(values.get(id)?.count || 0);
  const bytes = id => Number(values.get(id)?.byteCount || 0);
  const importedConversations = count('chatgpt_json_imported_conversations') + count('claude_json_imported_conversations') + count('zip_imported_conversations');
  const importBatches = count('chatgpt_json_import_batches') + count('claude_json_import_batches') + count('zip_import_batches') + count('markdown_tasks') + count('json_tasks') + count('pdf_tasks') + count('web_tasks');
  const importedAttachments = count('zip_imported_attachments');
  const importedAttachmentBytes = bytes('zip_imported_attachments');
  const profileFields = count('zip_imported_profile_fields');
  const transcriptionTasks = count('glm_ocr_tasks');
  const transcriptionFiles = count('glm_ocr_attachments');
  const transcriptionBytes = bytes('glm_ocr_attachments');
  const rows = [
    ['导入批次', importBatches, `${importBatches} 批`],
    ['已导入对话', importedConversations, `${importedConversations} 个`],
    ['已导入附件', importedAttachments, `${importedAttachments} 个 · ${storageBytes(importedAttachmentBytes)}`],
    ['已导入个性化资料', profileFields, `${profileFields} 项`],
    ['南枫转写', transcriptionTasks, `${transcriptionTasks} 条 · ${transcriptionFiles} 个文件 · ${storageBytes(transcriptionBytes)}`],
  ].filter(([, value]) => value > 0);
  if (!rows.length) return '';
  return `<strong class="android-settings-privacy-heading">导入概况</strong><div class="android-settings-import-summary">${rows.map(([label, , value]) => `<div><span>${escapeHtml(label)}</span><small>${escapeHtml(value)}</small></div>`).join('')}</div>`;
}

export function renderLocalDataInventoryPage(context) {
  const inventory = context.privacyInventory || { totalBytes: 0, aggregates: [] };
  const values = new Map((inventory.aggregates || []).map(item => [item.id, item]));
  const value = id => values.get(id) || { count: 0, byteCount: 0 };
  const searchable = value('search_text');
  const searchableAttachments = value('search_attachments');
  const attachmentCount = searchableAttachments.count || ['attachment_images', 'attachment_videos', 'attachment_audio', 'attachment_files'].reduce((sum, id) => sum + Number(value(id).count || 0), 0);
  const rows = (title, items) => {
    const visible = items.filter(item => item.aggregate.count > 0 || item.aggregate.byteCount > 0);
    if (!visible.length) return '';
    return `<strong class="android-settings-privacy-heading">${escapeHtml(title)}</strong><div class="android-settings-privacy-rows">${visible.map(item => `<button data-action="${item.action || 'privacy-noop'}" ${item.category ? `data-category="${item.category}"` : ''}><span>${escapeHtml(item.label)}</span><small>${escapeHtml(item.custom || `${item.aggregate.count} ${item.unit} · ${storageBytes(item.aggregate.byteCount)}`)}</small>${item.action ? icon(icons.chevronRight, `进入${item.label}`) : ''}</button>`).join('')}</div>`;
  };
  const contentRows = [
    { label: '全部', aggregate: { count: searchable.count + attachmentCount, byteCount: searchable.byteCount + searchableAttachments.byteCount }, unit: '项', custom: `${searchable.count} 条正文 · ${attachmentCount} 项附件`, action: 'open-privacy-search-category', category: 'all' },
    { label: '正文', aggregate: searchable, unit: '条', action: 'open-privacy-search-category', category: 'text' },
    { label: '记忆', aggregate: value('memory'), unit: '条', action: 'show-memory' },
    { label: '知识库', aggregate: value('knowledge'), unit: '条', action: 'show-knowledge' },
    { label: '项目', aggregate: value('projects'), unit: '个', action: 'show-projects' },
  ];
  const attachmentRows = [
    { label: '图片', aggregate: value('attachment_images'), unit: '个', action: 'open-privacy-search-category', category: 'image' },
    { label: '视频', aggregate: value('attachment_videos'), unit: '个', action: 'open-privacy-search-category', category: 'video' },
    { label: '音频', aggregate: value('attachment_audio'), unit: '个', action: 'open-privacy-search-category', category: 'audio' },
    { label: '文件', aggregate: value('attachment_files'), unit: '个', action: 'open-privacy-search-category', category: 'file' },
    { label: '其他导入资料', aggregate: value('import_source_assets'), unit: '份' },
  ];
  const totalBytes = ['conversations', 'messages', 'memory', 'knowledge', 'projects', 'attachment_images', 'attachment_videos', 'attachment_audio', 'attachment_files', 'import_source_assets']
    .reduce((sum, id) => sum + Number(value(id).byteCount || 0), 0) || Number(inventory.totalBytes || 0);
  return `<div class="android-settings-page android-settings-privacy"><section class="android-settings-card android-settings-privacy-summary"><header><strong>本机数据</strong><b>${storageBytes(totalBytes)}</b></header>${rows('对话与内容', contentRows)}${rows('附件', attachmentRows)}${importSummary(values)}</section><section class="android-settings-card storage-location-card"><header><strong>数据保存路径</strong><button type="button" class="storage-location-change" data-action="choose-storage-location">更改路径</button></header><p class="storage-location-path">${escapeHtml(inventory.storagePath || '读取中…')}</p></section><button class="android-settings-privacy-cleanup" data-action="open-privacy-cleanup">选择清理范围</button></div>`;
}

export function renderLocalDataCleanupScopeDialog() {
  const scopes = [
    ['TEMPORARY_FAILED_TASK_ASSETS', '清理失败任务', '选择后可逐项清理失败任务的附件。'],
    ['KNOWLEDGE_MEMORY_TRASH', '清空知识与记忆回收站', '只清空已放入知识与记忆回收站的内容。'],
    ['ALL_LOCAL_BUSINESS_DATA', '删除全部本地数据', '删除全部本机业务数据，需输入确认文字。'],
  ];
  return `<div class="scrim"><section class="dialog privacy-cleanup-dialog" role="dialog" aria-modal="true"><h2>选择清理范围</h2><div class="privacy-scope-list">${scopes.map(([scope, title, detail]) => `<button data-action="select-privacy-cleanup-scope" data-scope="${scope}"><span><strong>${title}</strong><small>${detail}</small></span>${icon(icons.chevronRight || icons.info, '进入')}</button>`).join('')}</div><div class="dialog-actions"><button data-action="close-dialog">取消</button></div></section></div>`;
}

export function renderLocalDataCleanupPreviewDialog(dialog, escape = escapeHtml) {
  const preview = dialog.preview;
  const failedTasks = preview.scope === 'TEMPORARY_FAILED_TASK_ASSETS';
  const fullDelete = preview.scope === 'ALL_LOCAL_BUSINESS_DATA';
  const selected = new Set(dialog.selectedTaskIds || []);
  const candidates = preview.taskCandidates || [];
  const hasSelectionPreview = !failedTasks || preview.aggregates?.length > 0;
  return `<div class="scrim"><section class="dialog privacy-cleanup-dialog" role="dialog" aria-modal="true" aria-busy="${Boolean(dialog.submitting)}"><h2>${failedTasks ? '清理失败任务' : fullDelete ? '删除全部本地数据' : '清空知识与记忆回收站'}</h2>${failedTasks ? `<p>选择要清理的失败任务。</p><div class="privacy-task-list">${candidates.map(candidate => `<label><input type="checkbox" data-action="toggle-privacy-task" data-id="${escape(candidate.selectionId)}" ${selected.has(candidate.selectionId) ? 'checked' : ''} ${dialog.submitting ? 'disabled' : ''}><span>失败任务 · 附件 ${Number(candidate.privateAssetCount || 0)} 个<small>${escape(candidate.adapter)} · ${escape(candidate.safeIdSummary)}</small></span></label>`).join('') || '<p class="empty-copy">没有可安全清理的失败任务。</p>'}</div><button class="privacy-preview-selection" data-action="preview-selected-privacy-tasks" ${!selected.size || dialog.submitting ? 'disabled' : ''}>预览已选 ${selected.size} 项</button>` : ''}${hasSelectionPreview ? '<p class="privacy-confirmed">已确认清理范围。</p>' : ''}${fullDelete ? `<label>确认文字<input id="privacy-confirmation" value="${escape(dialog.confirmation || '')}" placeholder="输入：删除全部本地业务数据" ${dialog.submitting ? 'disabled' : ''}></label>` : ''}${dialog.failure ? `<p class="dialog-error" role="alert">${escape(dialog.failure)}</p>` : ''}<div class="dialog-actions"><button data-action="close-dialog" ${dialog.submitting ? 'disabled' : ''}>取消</button>${hasSelectionPreview ? `<button class="primary danger" data-action="confirm-privacy-cleanup" ${dialog.submitting || (fullDelete && dialog.confirmation !== '删除全部本地业务数据') ? 'disabled' : ''}>${dialog.submitting ? '正在清理…' : fullDelete ? '确认删除全部本地业务数据' : '确认删除此范围'}</button>` : ''}</div></section></div>`;
}
