export function p8InspectCanvas({ agentRuns, native, escape, short }) {
  if (!native) {
    return `<section class="canvas p8-inspect-canvas"><p class="overline">P8-C · 只读检查</p><h1>本地受控记录</h1><p>Web 预览不读取 Desktop 私有账本。请在已安装的 Desktop 应用中查看只读元数据。</p></section>`;
  }
  const eventRows = events => events.length
    ? events.map(event => `<li>#${event.sequence} · ${escape(event.kind)}</li>`).join('')
    : '<li>无 durable Event。</li>';
  const checkpointRows = checkpoints => checkpoints.length
    ? checkpoints.map(checkpoint => `<li>#${checkpoint.sequence} · ${escape(checkpoint.status)} · 下一 Step ${checkpoint.nextStepSequence}</li>`).join('')
    : '<li>无 durable Checkpoint。</li>';
  const stepRows = steps => steps.length
    ? steps.map(step => `<li><b>Step ${step.sequence}</b> · ${escape(step.toolId)} · ${escape(step.status)}<br><span>${escape(step.riskLevel)} / ${escape(step.sideEffectClass)}${step.safeError ? ` · ${escape(step.safeError)}` : ''}</span></li>`).join('')
    : '<li>无 durable Step。</li>';
  const runRows = agentRuns.length
    ? agentRuns.map(run => `<article class="p8-run"><header><div><p class="overline">${escape(run.status)} · ${escape(short(run.id))}</p><h2>本地账本 Run</h2></div><span>${escape(run.riskCeiling)} / ${escape(run.permissionGrant)}</span></header><dl><dt>预算（已用 / 上限）</dt><dd>${run.usedSteps}/${run.maxSteps} · ${run.usedToolCalls}/${run.maxToolCalls} · ${run.usedSideEffects}/${run.maxSideEffects}</dd><dt>安全错误</dt><dd>${run.safeError ? escape(run.safeError) : '无'}</dd></dl><div class="p8-ledger-columns"><section><h3>Steps</h3><ul>${stepRows(run.steps)}</ul></section><section><h3>Events（稳定序号）</h3><ul>${eventRows(run.events)}</ul></section><section><h3>Checkpoints</h3><ul>${checkpointRows(run.checkpoints)}</ul></section></div></article>`).join('')
    : '<section class="p8-empty"><h2>账本当前为空</h2><p>这是已知空账本，不代表未知，也不会创建任何本地计划或执行。</p></section>';
  return `<section class="canvas p8-inspect-canvas"><div class="canvas-header"><div><p class="overline">P8-C · 只读检查</p><h1>本地受控记录</h1><p>仅显示本机 P8 账本的安全 metadata。未连接模型与外部工具。</p></div></div><section class="p8-boundary"><b>只读边界</b><span>READ_ONLY · LOCAL_READ · NONE</span><p>此页不会创建计划、批准、执行、暂停、恢复、取消或调用工具；不访问 Provider、HTTP、Key、文件或跨应用数据。</p></section><div class="p8-run-list">${runRows}</div></section>`;
}
