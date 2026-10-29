// Transient saved-record projection inside the existing Knowledge editor.
export function renderKnowledgeDuplicates(dialog, escape) {
  if (!dialog?.item || dialog.item.status !== 'ACTIVE') return '';
  const result = dialog.duplicateResult;
  return `<section class="memory-rail knowledge-duplicates"><p>只比较已保存的本机知识；未保存编辑不参与，不会合并或删除。</p>${dialog.duplicateError ? `<p role="alert">${escape(dialog.duplicateError)}</p>` : ''}${result ? `<p role="status">${result.candidates.length ? '重复候选（最多 20 条）' : '没有符合规则的重复候选。'}</p><ul>${result.candidates.map(item => `<li><span>${escape(item.title)} · r${item.revision} · ${item.reasons.map(reason => reason === 'EXACT_CONTENT' ? '规范化内容相同' : '规范化标题相同').join('、')}</span></li>`).join('')}</ul>` : ''}</section>`;
}
export async function readKnowledgeDuplicates({ getState, invoke, render }) {
  const owner = getState(), dialog = owner?.dialog;
  if (dialog?.kind !== 'knowledge' || dialog.item?.status !== 'ACTIVE' || dialog.duplicateLoading || dialog.conflict) return;
  const workspaceId = owner.current?.summary?.id, knowledgeId = dialog.item.id, revision = dialog.item.revision;
  if (!workspaceId) return;
  const current = () => !dialog.conflict && getState() === owner && owner.current?.summary?.id === workspaceId && owner.dialog === dialog && dialog.item?.id === knowledgeId && dialog.item?.revision === revision;
  dialog.duplicateLoading = true; dialog.duplicateError = ''; dialog.duplicateResult = null; render();
  try {
    const result = await invoke('read_desktop_knowledge_duplicates', { workspaceId, knowledgeId, expectedRevision: revision }, owner);
    if (!current()) return;
    if (result.anchorId !== knowledgeId || result.anchorRevision !== revision) throw Error('REVISION_CONFLICT');
    dialog.duplicateResult = result;
  } catch (error) {
    if (!current()) return;
    dialog.duplicateError = String(error).includes('REVISION_CONFLICT') ? '知识修订已变化，请重新打开详情后查看。' : '重复候选未能读取；原知识和编辑草稿均保留。';
  } finally {
    dialog.duplicateLoading = false;
    if (current()) render();
  }
}
