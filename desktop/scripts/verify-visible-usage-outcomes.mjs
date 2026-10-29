import assert from 'node:assert/strict';

// Only used by scripts which already verified a marked synthetic acceptance root.
export async function verifyVisibleUsageOutcomes({ port, evaluate, click, until, area = 'WORK', section, modelId, labels }) {
  await until(() => evaluate(port, 'globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady===true'), '费用双区就绪');
  if (area === 'WORK' && !(await evaluate(port, 'globalThis.__nanfengDesktopWorkState?.dataArea==="WORK"'))) {
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=show-work]"))'), '工作区入口');
    if (!(await evaluate(port, 'globalThis.__nanfengDesktopWorkState?.dataArea==="WORK"'))) assert.equal(await click(port, '[data-action="show-work"]'), true);
    await until(() => evaluate(port, 'globalThis.__nanfengDesktopWorkState?.dataArea==="WORK"'), '费用工作区');
  }
  if (!(await evaluate(port, 'globalThis.__nanfengDesktopWorkState?.pane==="settings"'))) {
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=show-settings]"))'), '设置入口');
    await until(async () => { await click(port, '[data-action="show-settings"]'); return evaluate(port, 'globalThis.__nanfengDesktopWorkState?.pane==="settings"'); }, '设置已打开');
  }
  await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=open-settings-page][data-page=model]"))'), '模型设置');
  await until(async () => { await click(port, '[data-action="open-settings-page"][data-page="model"]'); return evaluate(port, 'globalThis.__nanfengDesktopWorkState?.settingsSection==="model"'); }, '模型设置已打开');
  await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=open-settings-page][data-page=conversation-cost]"))'), '费用入口');
  assert.equal(await click(port, '[data-action="open-settings-page"][data-page="conversation-cost"]'), true);
  if (section) {
    const selector = `[data-action="select-usage-section"][data-section="${section}"]`;
    await until(() => evaluate(port, `Boolean(document.querySelector(${JSON.stringify(selector)}))`), '费用分类');
    assert.equal(await click(port, selector), true);
  }
  const rows = await until(() => evaluate(port, `[...document.querySelectorAll('.android-settings-ledger-row')].filter(n=>n.textContent.includes(${JSON.stringify(modelId)})).map(n=>n.textContent)`).then(rows => rows?.length === labels.length && rows), '调用费用明细');
  const unmatched = [...rows];
  for (const label of labels) {
    const index = unmatched.findIndex(row => row.includes(label));
    assert.ok(index >= 0, `缺少费用状态 ${label}`);
    unmatched.splice(index, 1);
  }
  assert.ok(rows.every(row => !row.includes('模型耗时')), '辅助调用不显示推测的模型耗时');
  return true;
}
