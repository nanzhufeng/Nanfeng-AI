import test from 'node:test';
import assert from 'node:assert/strict';
import { renderDesktopTranscriptionPage } from '../src/desktop-transcription-page.mjs';

const task = { id: 'synthetic-partial-usage', sourceDisplayName: '合成.pdf', sourceMimeType: 'application/pdf', sourceByteCount: 5, modelId: 'glm-ocr', state: 'FAILED', segments: [], inputTokens: null, outputTokens: 17, estimatedChargeMicros: 4 };
const render = overrides => renderDesktopTranscriptionPage({ native: true, projection: { tasks: [{ ...task, ...overrides }] }, selectedTaskId: task.id });
test('a missing input token count is not shown as a confirmed zero', () => {
  const html = render({}); assert.match(html, /未返回 \/ 17 Token/); assert.doesNotMatch(html, /0 \/ 17 Token/);
});
test('a missing output token count is not shown as a confirmed zero', () => {
  const html = render({ inputTokens: 23, outputTokens: null }); assert.match(html, /23 \/ 未返回 Token/); assert.doesNotMatch(html, /23 \/ 0 Token/);
});
test('actual zero counts stay zero while fully unknown usage stays unknown', () => {
  assert.match(render({ inputTokens: 0, outputTokens: 0, estimatedChargeMicros: 0 }), /0 \/ 0 Token/);
  assert.match(render({ inputTokens: null, outputTokens: null }), /Token 未返回/);
});
