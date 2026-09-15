import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { renderChatFirstShell } from '../src/chat-shell.mjs';

const data = {
  summary: { id: 'workspace-c04-c06' },
  exchange: {
    conversations: [], projects: [], knowledge: [], memory: [], relations: [],
  },
};

const renderPicker = ({ tier = null, webSearchEnabled = false, now = '2026-09-02T02:00:00Z' } = {}) => renderChatFirstShell({
  data,
  native: true,
  selectedConversationId: null,
  composerDraft: '',
  pane: 'chat',
  status: '',
  error: '',
  connection: {},
  p6gModelPickerOpen: true,
  p6gSelection: tier ? { pickerTier: tier } : null,
  productSettings: { webSearchEnabled },
  modelPickerNow: now,
});
const visibleText = html => html.replaceAll(/<[^>]+>/g, '').replaceAll(/\s+/g, ' ');

test('C04 model root is a Composer-linked sheet with scrim, handle, hierarchy, and card selection', () => {
  const html = renderPicker();
  for (const token of [
    'composer-model-sheet-scrim',
    'composer-model-sheet',
    'composer-model-sheet-handle',
    'composer-model-sheet-header',
    'composer-model-sheet-section',
    '自动选择',
    '按任务选择',
    'data-action="select-p6g-auto"',
    'data-tier="DAILY"',
    'data-tier="DEEP"',
    'aria-selected="true"',
  ]) assert.ok(html.includes(token), token);
  assert.equal((html.match(/class="composer-model-sheet-card/g) || []).length, 3);
  assert.ok(!html.includes('p6g-model-popover'));
});

test('C05 daily candidates show the section title and runtime Provider plus live session state', () => {
  const html = renderPicker({ tier: 'DAILY', webSearchEnabled: true });
  const text = visibleText(html);
  for (const token of [
    '选择具体模型',
    'Claude Sonnet 5',
    'DeepSeek V4.1 Flash',
    'GPT-5.6 Terra',
    'GLM-5.3 Flash',
    'Qwen3.7-Plus',
    'Gemini 3.8 Flash',
    'OpenRouter · 实时联网',
    '当前高峰 · 实时联网',
    '智谱 · 实时联网',
    '千问 · 实时联网',
  ]) assert.ok(text.includes(token), token);
  for (const stale of [
    '高效处理日常工作。',
    '适合快速问答与高频文本任务。',
    '能力与成本更均衡。',
    '智谱官方直连的快速文本任务。',
    '日常问答与轻量多媒体任务。',
    '快速处理文字、图片和文件任务。',
  ]) assert.ok(!html.includes(stale), stale);
});

test('C06 deep candidates use Qwen3.8-Max and truthful offline runtime labels', () => {
  const html = renderPicker({ tier: 'DEEP', webSearchEnabled: false, now: '2026-09-02T04:00:00Z' });
  const text = visibleText(html);
  for (const token of [
    '选择具体模型',
    'Claude Fable 5',
    'Claude Opus 5',
    'DeepSeek V4 Pro',
    'GPT-5.6 Sol',
    'GLM-5.3',
    'Qwen3.8-Max',
    'OpenRouter · 未联网',
    '当前低谷 · 未联网',
    '智谱 · 未联网',
    '千问 · 未联网',
  ]) assert.ok(text.includes(token), token);
  assert.ok(!text.includes('Kimi K3'));
});

test('C04-C06 CSS has one shared rounded sheet owner and no legacy model popover selector', async () => {
  const css = await readFile(resolve(import.meta.dirname, '../src/chat-shell.css'), 'utf8');
  for (const token of [
    '.composer-model-sheet-scrim',
    '.composer-model-sheet {',
    '.composer-model-sheet-handle',
    '.composer-model-sheet-card {',
    '.composer-model-sheet-card[aria-selected="true"]',
  ]) assert.ok(css.includes(token), token);
  assert.ok(!css.includes('.p6g-model-popover'));
});

test('C04 keyboard Back returns from a candidate tier before dismissing the root sheet', async () => {
  const app = await readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8');
  for (const token of [
    'navigateComposerLayerBack = false',
    'navigateComposerLayerBack && state.p6gSelection?.pickerTier',
    "state.p6gSelection = { ...state.p6gSelection, pickerTier: null }",
    "closeTopOverlay({ navigateComposerLayerBack: true })",
  ]) assert.ok(app.includes(token), token);
});

test('C06 pricing-period refresh follows every open model-sheet render, not sidebar resizing', async () => {
  const app = await readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8');
  for (const token of [
    'function scheduleComposerModelPricingRefresh()',
    'millisecondsUntilDeepSeekPricingTransition() + 80',
    'scheduleComposerModelPricingRefresh();',
  ]) assert.ok(app.includes(token), token);
  const sidebarStart = app.indexOf('function updateSidebarWidth(width)');
  const sidebarEnd = app.indexOf("app.addEventListener('pointerdown'", sidebarStart);
  assert.ok(sidebarStart >= 0 && sidebarEnd > sidebarStart);
  assert.ok(!app.slice(sidebarStart, sidebarEnd).includes('composerModelPricingRefreshTimer'));
});
