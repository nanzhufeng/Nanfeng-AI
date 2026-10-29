import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { renderAndroidSettingsShell } from '../src/android-settings-shell.mjs';
import { createC14LocalDataPreview } from '../src/c14-local-data-preview-fixture.mjs';
import { renderLocalDataCleanupScopeDialog, renderLocalDataCleanupPreviewDialog, renderLocalDataInventoryPage } from '../src/local-data-view.mjs';

test('Windows share cleanup discloses receiver impact and requires nonempty preview', () => {
  assert.match(renderLocalDataCleanupScopeDialog({ runtimeInfo: { platform: 'windows' } }), /WINDOWS_SHARE_COPIES/);
  assert.doesNotMatch(renderLocalDataCleanupScopeDialog({ runtimeInfo: { platform: 'macos' } }), /WINDOWS_SHARE_COPIES/);
  const preview = { scope: 'WINDOWS_SHARE_COPIES', aggregates: [{ id: 'windows_share_copies', count: 2, byteCount: 86 }] };
  const html = renderLocalDataCleanupPreviewDialog({ preview });
  assert.match(html, /2 份分享副本 · 86 B/);
  assert.match(html, /接收应用尚未保存的文件路径将失效/);
  assert.match(html, /原始附件保留/);
  assert.match(html, /confirm-privacy-cleanup/);
  const empty = renderLocalDataCleanupPreviewDialog({ preview: { ...preview, aggregates: [{ count: 0, byteCount: 0 }] } });
  assert.doesNotMatch(empty, /data-action="confirm-privacy-cleanup"/);
  assert.match(renderLocalDataInventoryPage({ privacyInventory: { aggregates: preview.aggregates } }), /Windows 分享副本/);
});

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

test('a titled memory points workspace export to the lossless local backup', () => {
  const titled = { ...workspace, exchange: { ...workspace.exchange,
    memory: [{ id: 'memory-c14-title', title: '工作流程偏好', body: '合成记忆' }] } };
  const html = renderAndroidSettingsShell({ page: 'data', data: titled, native: true });
  assert.match(html, /data-action="start-export"[^>]*disabled/);
  assert.match(html, /当前工作区含有记忆主题，工作区导出暂不能完整保留；请使用下方本机备份/);
  assert.match(html, /data-action="export-local-backup"/);
  assert.match(render('data'), /data-action="start-export"(?![^>]*disabled)/);
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

test('unfinished rollback blocks backup actions and explains the required restart', () => {
  const html = renderAndroidSettingsShell({ page: 'data', data: workspace, native: true, localBackup: { rollbackPending: true, notice: '请释放占用文件并完全重启 App' } });
  assert.match(html, /data-action="export-local-backup"[^>]*disabled/);
  assert.match(html, /data-action="import-local-backup"[^>]*disabled/);
  assert.match(html, /自动回滚尚未完成/);
  assert.match(html, /请释放占用文件并完全重启 App/);
  assert.doesNotMatch(html, /恢复已完成/);
});

test('dual-area backup selection is explicit and recorded in feature review', () => {
  const html = renderAndroidSettingsShell({ page: 'data', data: workspace, native: true, runtimeInfo: { dataAreasReady: true }, localBackupArea: 'WORK' });
  assert.match(html, /data-action="select-local-backup-area" data-area="CHAT"[^>]*aria-pressed="false"/);
  assert.match(html, /data-action="select-local-backup-area" data-area="WORK"[^>]*aria-pressed="true"/);
  const busy = renderAndroidSettingsShell({ page: 'data', data: workspace, native: true, runtimeInfo: { dataAreasReady: true }, localBackupArea: 'WORK', backupAreaBusy: true });
  assert.match(busy, /data-action="select-local-backup-area" data-area="CHAT"[^>]*disabled/);
  assert.match(html, /账号和模型凭据不会进入备份/);
  const review = renderAndroidSettingsShell({ page: 'feature-review', data: workspace, native: true });
  assert.match(review, /功能审阅/);
  assert.match(review, /建议保留：备份区域选择/);
  assert.match(review, /待您判断/);
  assert.match(review, /不在工作主界面增加常驻按钮/);
  assert.match(review, /建议保留：工作区独立对话与文字引用/);
  assert.match(review, /只进入目标区未发送草稿，不自动发送或夹带附件/);
  const workspaceReview = renderAndroidSettingsShell({ page: 'workspace', data: workspace, native: true });
  assert.match(workspaceReview, /工作区独立对话：保留/);
  assert.match(workspaceReview, /在消息菜单中选择引用文字到另一区域/);
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

test('storage location distinguishes the active root from a scheduled restart target', () => {
  const inventory = { ...preview.privacyInventory, storagePath: 'C:\\active', pendingStoragePath: 'D:\\next<&>' };
  const html = renderAndroidSettingsShell({ page: 'privacy', data: workspace, native: true, privacyInventory: inventory });
  assert.match(html, /当前使用<\/p><p class="storage-location-path">C:\\active/);
  assert.match(html, /待下次启动切换至<\/p><p class="storage-location-path">D:\\next&lt;&amp;&gt;/);
  const activeOnly = renderAndroidSettingsShell({ page: 'privacy', data: workspace, native: true,
    privacyInventory: { ...inventory, pendingStoragePath: null } });
  assert.doesNotMatch(activeOnly, /待下次启动切换至/);
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
  assert.match(loadingStart, /state\.searchLoading = !append && !cachedPage/);
  assert.match(loadingStart, /render\(\)/);
});
