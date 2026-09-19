const sourceLabels = {
  PERSONA: '个性化资料', '个性化资料': '个性化资料', MEMORY: '长期记忆', Memory: '长期记忆', '记忆': '长期记忆',
  KNOWLEDGE: '资料库', '知识库': '资料库', '资料库': '资料库', CURRENT_PATH: '当前对话路径',
};
const isStyle = source => ['STYLE', '对话风格'].includes(source.kind);
const escape = value => String(value ?? '').replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));

// Only facts bound to this answer belong here; never read current preferences.
export function answerInformation(record) {
  const sources = Array.isArray(record?.selectedSources) ? record.selectedSources : [];
  const styles = [...new Set(sources.filter(isStyle).map(source => String(source.title || '').trim())
    .filter(title => title && title !== '基础风格和语气'))];
  const grouped = new Map();
  for (const source of sources.filter(source => !isStyle(source))) {
    const label = sourceLabels[source.kind] || String(source.kind || '本地来源');
    const titles = grouped.get(label) || [];
    const title = String(source.title || '').trim();
    if (title && title !== label && !titles.includes(title)) titles.push(title);
    grouped.set(label, titles);
  }
  return {
    styleLabel: styles.join('、') || '未记录（旧回答）',
    networkLabel: record?.webSearchVerified === true ? '已实际使用'
      : record?.webSearchRequested === true ? '联网未完成'
      : record?.webSearchRequested === false && record?.webSearchVerified === false ? '本次未使用' : '无法确认（旧记录）',
    networkUsed: record?.webSearchVerified === true,
    sources: [...grouped].map(([label, titles]) => ({label, titles})),
  };
}

export function renderAnswerInformation(record) {
  const info = answerInformation(record);
  return `<div class="scrim"><section class="dialog answer-information-dialog" role="dialog" aria-modal="true" aria-labelledby="answer-information-title">
    <h2 id="answer-information-title">本次回答信息</h2>
    <div class="answer-information-body">
      <section><h3>回答设置</h3><dl class="answer-information-list">
        <div><dt>基础风格和语气</dt><dd>${escape(info.styleLabel)}</dd></div>
        <div><dt>实时网络</dt><dd${info.networkUsed ? ' class="answer-information-used"' : ''}>${escape(info.networkLabel)}</dd></div>
      </dl></section>
      ${info.sources.length ? `<section><h3>本次上下文来源</h3><ul class="answer-information-sources">${info.sources.map(source => `<li><span>${escape(source.label)}</span>${source.titles.length ? `<ul>${source.titles.map(title => `<li>${escape(title)}</li>`).join('')}</ul>` : ''}</li>`).join('')}</ul></section>` : ''}
    </div><div class="dialog-actions"><button class="answer-information-dismiss" data-action="close-dialog">知道了</button></div>
  </section></div>`;
}
