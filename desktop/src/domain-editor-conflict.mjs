export function updateMemorySummaryDraft(dialog, value) {
  if (dialog.conflictLoading) { dialog.value = value; return dialog; }
  return { ...dialog, value };
}
const isSummary = dialog => dialog?.kind === 'memory-summary-editor';
const summaryEntries = current => (current.exchange?.memory || []).filter(item => item.status === 'ACTIVE' && !item.deleted && item.scope === 'GLOBAL' && !item.scopeId);
const validEntries = entries => entries.length > 0 && entries.every(item => typeof item.id === 'string' && item.id && Number.isSafeInteger(item.revision) && item.revision > 0) && new Set(entries.map(item => item.id)).size === entries.length;
const entryStamp = entries => JSON.stringify(entries.map(item => ({ id: item.id, revision: item.revision })).sort((a,b)=>a.id.localeCompare(b.id)));
export function memorySummaryValue(memories) {
  return memories.map(item => `${memories.length > 1 ? `【${String(item.title || '概览').trim()}】\n` : ''}${String(item.body || '').trim()}`).filter(Boolean).join('\n\n');
}
function renderSummaryConflict(dialog, escape) {
  const entries = dialog.conflict.latestEntries || [];
  const available = validEntries(entries) && entryStamp(entries) !== entryStamp(dialog.expectedEntries || []);
  return `<section class="memory-rail domain-editor-conflict" role="status"><p>记忆主题清单或修订已变化，本次整篇草稿已保留。</p>${available ? `<details><summary>查看最新 ${entries.length} 个主题</summary><pre>${escape(memorySummaryValue(entries))}</pre></details><button data-action="reload-domain-editor" ${dialog.conflictLoading ? 'disabled' : ''}>${dialog.conflictLoading ? '读取最新修订…' : '重新载入最新修订（替换本次草稿）'}</button>` : '<p>没有可载入的最新活动主题，草稿保留；请关闭后核对记忆摘要。</p>'}${dialog.conflictError ? `<p role="alert">${escape(dialog.conflictError)}</p>` : ''}</section>`;
}
async function reloadSummary({getState,invoke,render}) {
  const owner=getState(), dialog=owner?.dialog, workspaceId=owner?.current?.summary?.id;
  if (!isSummary(dialog) || !dialog.conflict || dialog.conflictLoading || !workspaceId) return;
  const memoryId=dialog.memoryId, revision=dialog.expectedRevision, stamp=entryStamp(dialog.expectedEntries || []), value=dialog.value;
  const current=()=>getState()===owner && owner.dialog===dialog && owner.current?.summary?.id===workspaceId && dialog.memoryId===memoryId && dialog.expectedRevision===revision && entryStamp(dialog.expectedEntries || [])===stamp;
  dialog.conflictLoading=true;dialog.conflictError='';render();
  try {
    const latest=await invoke('read_desktop_workspace',{workspaceId},owner);
    if (!current()) return;
    if (dialog.value!==value) throw Error('DRAFT_CHANGED');
    const entries=summaryEntries(latest);
    if (latest.summary?.id!==workspaceId || !validEntries(entries) || entryStamp(entries)===stamp) throw Error('INELIGIBLE');
    const primary=[...entries].sort((a,b)=>String(b.updatedAt || b.createdAt || '').localeCompare(String(a.updatedAt || a.createdAt || '')))[0];
    owner.current=latest;
    dialog.memoryId=primary.id;dialog.expectedRevision=primary.revision;
    dialog.expectedEntries=entries.map(item=>({id:item.id,revision:item.revision}));dialog.multipleEntries=entries.length>1;dialog.value=memorySummaryValue(entries);dialog.conflict=null;
    owner.error='';owner.status='已载入最新完整主题清单；继续编辑后再明确保存。';
  } catch(error) {
    if (!current()) return;
    dialog.conflictError=String(error).includes('DRAFT_CHANGED')?'读取期间草稿有变化，已保留；请再次明确载入。':'最新活动主题未能载入，原整篇草稿保留。';
  } finally {
    dialog.conflictLoading=false;
    if(getState()===owner && owner.dialog===dialog && owner.current?.summary?.id===workspaceId)render();
  }
}
const collections = { knowledge: 'knowledge', memory: 'memory', project: 'projects' };
const eligible = (kind, item) => item && Number.isSafeInteger(item.revision) && (kind === 'project' ? !item.archived && !item.deleted : item.status === 'ACTIVE');
export function recordDomainConflict(dialog, args, current) {
  if (isSummary(dialog) && args.entity === 'memory' && args.action === 'replaceMemorySummary' && dialog.memoryId === args.objectId) {
    dialog.conflict = { latestEntries: structuredClone(summaryEntries(current)) }; return;
  }
  if (!dialog?.item || dialog.kind !== args.entity || dialog.item.id !== args.objectId) return;
  const collection = collections[dialog.kind];
  if (!collection) return;
  const latest = current.exchange?.[collection]?.find(item => item.id === dialog.item.id);
  dialog.conflict = { latestItem: latest ? structuredClone(latest) : null };
  dialog.duplicateResult = null;
}
export function renderDomainConflict(dialog, escape) {
  if (!dialog?.conflict) return '';
  if (isSummary(dialog)) return renderSummaryConflict(dialog, escape);
  const latest = dialog.conflict.latestItem;
  const available = eligible(dialog.kind, latest) && latest.revision > dialog.item.revision;
  return `<section class="memory-rail domain-editor-conflict" role="status"><p>保存版本已变化，本次编辑草稿已保留。</p>${available ? `<details><summary>查看已保存的 r${latest.revision}</summary><h3>${escape(latest.title || '')}</h3><pre>${escape(latest.body ?? latest.description ?? '')}</pre></details><button data-action="reload-domain-editor" ${dialog.conflictLoading ? 'disabled' : ''}>${dialog.conflictLoading ? '读取最新修订…' : '重新载入最新修订（替换本次草稿）'}</button>` : '<p>记录已删除、归档或最新修订未能读取；草稿保留，请关闭后核对原记录。</p>'}${dialog.conflictError ? `<p role="alert">${escape(dialog.conflictError)}</p>` : ''}</section>`;
}
export async function reloadDomainEditor({ getState, invoke, render }) {
  if (isSummary(getState()?.dialog)) return reloadSummary({getState,invoke,render});
  const owner = getState(), dialog = owner?.dialog;
  if (!dialog?.conflict || dialog.conflictLoading || !collections[dialog.kind]) return;
  const workspaceId = owner.current?.summary?.id, id = dialog.item?.id, revision = dialog.item?.revision;
  const draft = JSON.stringify(dialog.draft || {});
  if (!workspaceId || !id) return;
  const current = () => getState() === owner && owner.current?.summary?.id === workspaceId && owner.dialog === dialog && dialog.item?.id === id && dialog.item?.revision === revision;
  dialog.conflictLoading = true; dialog.conflictError = ''; render();
  try {
    const latest = await invoke('read_desktop_workspace', { workspaceId }, owner);
    if (!current()) return;
    if (JSON.stringify(dialog.draft || {}) !== draft) throw Error('DRAFT_CHANGED');
    const item = latest.exchange?.[collections[dialog.kind]]?.find(row => row.id === id);
    if (!eligible(dialog.kind, item) || item.revision <= revision) throw Error('INELIGIBLE');
    owner.current = latest;
    dialog.item = structuredClone(item);
    dialog.draft = {}; dialog.conflict = null; dialog.duplicateResult = null;
    owner.error = ''; owner.status = '已载入最新已保存修订；继续编辑后再明确保存。';
  } catch (error) {
    if (!current()) return;
    dialog.conflictError = String(error).includes('DRAFT_CHANGED') ? '读取期间草稿有变化，已保留；请再次明确载入。' : '最新活动修订未能载入，原草稿保留。';
  } finally {
    dialog.conflictLoading = false;
    if (getState() === owner && owner.dialog === dialog && owner.current?.summary?.id === workspaceId) render();
  }
}
