import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { renderAndroidSettingsShell } from '../src/android-settings-shell.mjs';
import { createC12ModelNetworkPreview } from '../src/c12-model-network-preview-fixture.mjs';

const base = {
  page: 'model',
  settings: { webSearchEnabled: true },
  data: { summary: { id: 'workspace-c12' }, exchange: { conversations: [{ id: 'conversation-c12', title: 'C12 固定会话' }] } },
};

const usageRecord = (entryId, overrides = {}) => ({
  entryId,
  conversationId: 'conversation-c12',
  modelId: 'openai/gpt-5.6-terra',
  factGrade: 'PROVIDER_REPORTED',
  inputTokens: 120,
  outputTokens: 80,
  cachedInputTokens: 20,
  chargeMicros: 1250,
  currencyCode: 'USD',
  occurredAtMs: Date.parse('2026-09-03T10:00:00+08:00'),
  ...overrides,
});

test('C12 model overview exposes the implemented shared web-search owner without stale blockers', () => {
  const html = renderAndroidSettingsShell(base);
  for (const label of ['OpenAI / Claude / Gemini', 'Qwen', 'DeepSeek', '智谱 GLM']) assert.ok(html.includes(label), label);
  assert.match(html, /data-key="webSearchEnabled"[^>]*role="switch"[^>]*aria-checked="true"(?![^>]*disabled)/);
  assert.ok(html.includes('开启后每次普通对话均检索公开网页并标注来源；可能产生服务费用'));
  assert.ok(!html.includes('Desktop 普通发送尚未建立网页检索执行器'));
  for (const entry of ['模型设置', '费用与用量', '上下文记录', '运行诊断']) assert.ok(html.includes(entry), entry);
});

test('C12 populated usage keeps Android four-way summary and selector while automatic task records stay visible', () => {
  const records = [
    usageRecord('usage-conversation-c12'),
    usageRecord('usage-title-c12'),
    usageRecord('usage-history-c12'),
    usageRecord('usage-transcription-c12'),
    usageRecord('usage-reminder-c12'),
  ];
  const html = renderAndroidSettingsShell({
    ...base,
    page: 'conversation-cost',
    usageLedger: { records, inputTokens: 600, outputTokens: 400, cachedInputTokens: 100 },
  });
  const grid = html.match(/android-settings-ledger-grid[^>]*>([\s\S]*?)<\/section>/)?.[1] || '';
  const segments = html.match(/android-settings-ledger-segments[^>]*>([\s\S]*?)<\/div>/)?.[1] || '';
  assert.equal((grid.match(/<div>/g) || []).length, 4);
  assert.equal((segments.match(/data-action="select-usage-section"/g) || []).length, 4);
  for (const label of ['会话', '会话标题整理', '历史资料整理', '南枫转写']) {
    assert.ok(grid.includes(label), `grid ${label}`);
    assert.ok(segments.includes(label), `segments ${label}`);
  }
  assert.ok(!segments.includes('计划与提醒'));
  assert.ok(html.includes('其他自动任务'));
  assert.ok(html.includes('计划与提醒'));
});

test('C12 browser fixture contains only deterministic read-only projections for every populated record page', () => {
  const preview = createC12ModelNetworkPreview();
  assert.equal(preview.usageLedger.records.length, 5);
  assert.equal(preview.contextSelectionRecords.length, 1);
  assert.equal(preview.diagnosticRecords.length, 1);
  assert.equal(preview.invocationRecords.length, 1);
  assert.ok(!JSON.stringify(preview).match(/api[_-]?key|credential|authorization/i));
});

test('C12 context and diagnostics preserve Android data hierarchy and time facts', () => {
  const context = renderAndroidSettingsShell({
    ...base,
    page: 'context-selections',
    contextSelectionRecords: [{
      conversationId: 'conversation-c12',
      providerLabel: 'OpenRouter',
      modelId: 'openai/gpt-5.6-terra',
      fixedInputTokens: 321,
      createdAtMs: Date.parse('2026-09-03T10:00:00+08:00'),
      selectedSources: [{ kind: '资料库', title: 'C12 本机资料' }],
    }],
  });
  assert.ok(context.includes('C12 固定会话'));
  assert.ok(context.includes('1 项'));
  assert.ok(context.includes('资料库'));
  assert.match(context, /本轮输入约 321 Token · \d{4}\/\d{1,2}\/\d{1,2}/);

  const diagnostics = renderAndroidSettingsShell({
    ...base,
    page: 'diagnostics',
    diagnosticRecords: [{
      conversationTitle: 'C12 固定会话',
      providerLabel: 'OpenRouter',
      modelId: 'openai/gpt-5.6-terra',
      summary: '网络连接失败，未自动重发。',
      latencyMs: 1450,
      createdAtMs: Date.parse('2026-09-03T10:00:00+08:00'),
    }],
  });
  assert.ok(diagnostics.includes('连接失败'));
  assert.ok(diagnostics.includes('网络连接失败，未自动重发。'));
  assert.ok(diagnostics.includes('耗时 1.45 秒'));
  assert.match(diagnostics, /2026\/9\/3/);
});

test('C12 Desktop request owner follows Android enabled-search semantics for ordinary text and projected attachments', async () => {
  const source = await readFile(new URL('../src-tauri/src/lib.rs', import.meta.url), 'utf8');
  const route = source.slice(source.indexOf('fn ordinary_chat_web_search_route('), source.indexOf('impl DesktopWorkspaceStore', source.indexOf('fn ordinary_chat_web_search_route(')));
  assert.ok(!route.includes('ordinary_chat_needs_current_web_information'));
  assert.match(route, /if !enabled \{\s*return "NONE";/);
  assert.match(route, /"DEEPSEEK"\s*=>\s*"DEEPSEEK_MESSAGES"/);
  assert.match(route, /"ZHIPU"\s*=>\s*"ZHIPU_CHAT_COMPLETIONS"/);
});

test('C07-C12 native acceptance is diagnostic-only, uniquely rooted, and restartable', async () => {
  const [rust, packager] = await Promise.all([
    readFile(new URL('../src-tauri/src/lib.rs', import.meta.url), 'utf8'),
    readFile(new URL('../scripts/prepare-c07-c12-offline-acceptance.mjs', import.meta.url), 'utf8'),
  ]);
  assert.match(rust, /NANFENG_AI_DESKTOP_C07_C12_OFFLINE_ACCEPTANCE/);
  assert.match(rust, /\/tmp\/nanfeng-ai-desktop-c07-c12-acceptance\./);
  assert.match(rust, /UiSchemaDiagnostic/);
  assert.match(rust, /seed_c07_c12_offline_acceptance/);
  assert.match(rust, /RECOVERY_REQUIRED/);
  assert.match(packager, /mkdtemp\('\/tmp\/nanfeng-ai-desktop-c07-c12-acceptance\.'/);
  assert.match(packager, /codesign/);
});
