import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { renderAndroidSettingsShell } from '../src/android-settings-shell.mjs';
import { createC14LocalDataPreview } from '../src/c14-local-data-preview-fixture.mjs';
import { renderLocalDataCleanupScopeDialog } from '../src/local-data-view.mjs';

const workspace = {
  summary: { id: 'workspace-c14', title: 'C14 本机数据验收' },
  exchange: { projects: [], conversations: [], knowledge: [], memory: [], relations: [] },
};
const preview = createC14LocalDataPreview();
const render = page => renderAndroidSettingsShell({
  page,
  data: workspace,
  native: true,
  privacyInventory: preview.privacyInventory,
  chatgptTask: preview.chatgptTask,
  claudeTask: preview.claudeTask,
  p6kTask: preview.p6kTask,
});

test('C14 import and export uses the current Android user-facing hierarchy without engineering entries', () => {
  const html = render('data');
  const labels = [
    '对话',
    '导入 ChatGPT JSON',
    '导入 Claude JSON',
    '导入结果',
    '导入 ChatGPT ZIP',
    '导入 Claude ZIP',
    '导入结果',
    '工作区',
    '导入工作区',
    '导出工作区',
    '本机备份与恢复',
    '备份',
    '恢复',
  ];
  let previous = -1;
  for (const label of labels) {
    const index = html.indexOf(label, previous + 1);
    assert.ok(index > previous, label);
    previous = index;
  }
  for (const forbidden of ['导入为独立工作区', '私有归档 v2 交换包', '导出当前工作区']) {
    assert.ok(!html.includes(forbidden), forbidden);
  }
});

test('C14 local backup identifies only the operation that is running', () => {
  const exporting = renderAndroidSettingsShell({ page: 'data', data: workspace, native: true, localBackup: { working: true, operation: 'EXPORT' } });
  assert.match(exporting, /data-action="export-local-backup"[^>]*aria-busy="true"/);
  assert.doesNotMatch(exporting, /data-action="import-local-backup"[^>]*aria-busy="true"/);
  assert.ok(exporting.includes('正在备份…'));
  const restoring = renderAndroidSettingsShell({ page: 'data', data: workspace, native: true, localBackup: { working: true, operation: 'RESTORE' } });
  assert.match(restoring, /data-action="import-local-backup"[^>]*aria-busy="true"/);
  assert.doesNotMatch(restoring, /data-action="export-local-backup"[^>]*aria-busy="true"/);
  assert.ok(restoring.includes('正在恢复…'));
});

test('C14 local data keeps the Android inventory groups, safe drill-downs, and cleanup entry', () => {
  const html = render('privacy');
  for (const token of ['本机数据', '对话与内容', '全部', '3 条正文 · 2 项附件', '正文', '记忆', '知识库', '项目', '附件', '图片', '文件', '其他导入资料', '导入概况', '已导入个性化资料', '南枫转写', '选择清理范围']) {
    assert.ok(html.includes(token), token);
  }
  for (const category of ['all', 'text', 'image', 'file']) {
    assert.match(html, new RegExp(`data-action="open-privacy-search-category"[^>]*data-category="${category}"`));
  }
});

test('C14 cleanup scope and typed full-delete gate match the current Android owner', async () => {
  const [app, owner] = await Promise.all([
    readFile(new URL('../src/app.mjs', import.meta.url), 'utf8'),
    readFile(new URL('../src/local-data-view.mjs', import.meta.url), 'utf8'),
  ]);
  const source = `${app}\n${owner}`;
  for (const token of [
    '清理失败任务',
    '清空知识与记忆回收站',
    '删除全部本地数据',
    '输入：删除全部本地业务数据',
    "dialog.confirmation !== '删除全部本地业务数据'",
  ]) assert.ok(source.includes(token), token);
  assert.match(renderLocalDataCleanupScopeDialog(), /data-scope="KNOWLEDGE_MEMORY_TRASH"/);
});

test('C14 browser fixture is read-only and C14 native fixture requires the diagnostic marker plus unique temp root', async () => {
  const [app, native] = await Promise.all([
    readFile(new URL('../src/app.mjs', import.meta.url), 'utf8'),
    readFile(new URL('../src-tauri/src/lib.rs', import.meta.url), 'utf8'),
  ]);
  for (const token of ['c14LocalDataPreview', 'createC14LocalDataPreview']) assert.ok(app.includes(token), token);
  for (const token of [
    'NANFENG_AI_DESKTOP_C14_ACCEPTANCE',
    'NANFENG_AI_DESKTOP_C14_ACCEPTANCE_ROOT',
    '/tmp/nanfeng-ai-desktop-c14-acceptance.',
    'seed_c14_local_data_acceptance',
    'DesktopStartupMode::UiSchemaDiagnostic',
  ]) assert.ok(native.includes(token), token);
});

test('C14 category changes paint the selected category before its local search completes', async () => {
  const app = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
  const loadingStart = app.slice(app.indexOf('async function runFullSearch('), app.indexOf('if (!native)', app.indexOf('async function runFullSearch(')));
  assert.match(loadingStart, /state\.searchLoading = !cachedPage/);
  assert.match(loadingStart, /render\(\)/);
});
