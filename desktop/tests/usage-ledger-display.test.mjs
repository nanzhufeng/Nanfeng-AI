import test from 'node:test';
import assert from 'node:assert/strict';
import { groupRetrievalUsageRecords } from '../src/usage-ledger-display.mjs';
import { renderAndroidSettingsShell } from '../src/android-settings-shell.mjs';

const search = (id, patch = {}) => ({ entryId: id, attemptId: 'question', conversationId: 'conversation',
  requestedModelId: 'openai/gpt-6-luna', modelId: 'openai/gpt-6-luna', kind: 'FINAL_MEASURED',
  inputTokens: 100, outputTokens: 20, cachedInputTokens: 10, chargeMicros: 100000, currencyCode: 'USD', costSource: 'PROVIDER_RESPONSE',
  occurredAtMs: 1, callMetadata: { role: 'RETRIEVAL', modelDisplayName: 'GPT-6 Luna', reasoningTokens: 5 }, ...patch });

test('six Luna searches collapse to one fee row and answers remain separate', () => {
  const searches = Array.from({ length: 6 }, (_, i) => search(`search-${i}`));
  const answer = search('answer', { callMetadata: { role: 'ANSWER', modelDisplayName: 'Sonnet 5.5 · RC' }, chargeMicros: 18087, currencyCode: 'CNY' });
  const input = [...searches, answer];
  const before = structuredClone(input);
  const rows = groupRetrievalUsageRecords(input);
  assert.equal(rows.length, 2);
  const retrieval = rows.find(row => row.callMetadata.role === 'RETRIEVAL');
  assert.equal(retrieval.inputTokens, 600);
  assert.equal(retrieval.outputTokens, 120);
  assert.equal(retrieval.callMetadata.reasoningTokens, 30);
  assert.equal(retrieval.groupedCalls.length, 6);
  assert.equal(rows.find(row => row.callMetadata.role === 'ANSWER'), answer);
  assert.deepEqual(input, before);
});

test('different roots models channels and missing links never merge', () => {
  const records = [search('a'), search('b', { attemptId: 'other' }), search('c', { attemptId: undefined }),
    search('d', { attemptId: undefined }), search('e', { requestedModelId: 'openai/gpt-6.1-sol' }),
    search('f', { callMetadata: { role: 'RETRIEVAL', modelDisplayName: 'Different channel' } })];
  assert.equal(groupRetrievalUsageRecords(records).length, 6);
});

test('legacy retrieval metadata without a frozen channel label remains separate', () => {
  const metadata = { role: 'RETRIEVAL' };
  assert.equal(groupRetrievalUsageRecords([search('a', { callMetadata: metadata }), search('b', { callMetadata: metadata })]).length, 2);
});

test('unknown and pending members keep their original money and state', () => {
  const rows = groupRetrievalUsageRecords([search('done'), search('pending', { kind: 'STREAM_PENDING', chargeMicros: null, inputTokens: null })]);
  assert.equal(rows.length, 1);
  assert.equal(rows[0].kind, 'STREAM_PENDING');
  assert.equal(rows[0].inputTokens, null);
  assert.deepEqual(rows[0].groupedCalls.map(call => call.chargeMicros), [100000, null]);
});

test('rendered ledger shows one retrieval amount, separate answer and unchanged total', () => {
  const searches = Array.from({ length: 6 }, (_, i) => search(`search-${i}`));
  const answer = search('answer', { requestedModelId: 'claude-sonnet-5-5', modelId: 'claude-sonnet-5-5',
    callMetadata: { role: 'ANSWER', modelDisplayName: 'Sonnet 5.5 · RC' }, chargeMicros: 18087, currencyCode: 'CNY' });
  const html = renderAndroidSettingsShell({ page: 'conversation-cost', usageLedger: { records: [...searches, answer] } });
  assert.equal((html.match(/android-settings-ledger-row/g) || []).length, 2);
  assert.ok(html.includes('¥4.032185'));
  assert.ok(html.includes('¥0.018087'));
  assert.ok(html.includes('¥4.050272'));
  assert.ok(html.includes('次数 <b>2 次</b>'));
  assert.equal((html.match(/GPT-6 Luna/g) || []).length, 1);
});

test('rendered partially unknown group retains known charges in row and total', () => {
  const html = renderAndroidSettingsShell({ page: 'conversation-cost', usageLedger: { records: [search('done'),
    search('pending', { kind: 'STREAM_PENDING', inputTokens: null, outputTokens: null, chargeMicros: null })] } });
  assert.equal((html.match(/android-settings-ledger-row/g) || []).length, 1);
  assert.ok(html.includes('¥0.672031（另有 1 次费用未知）'));
  assert.ok(html.includes('未取得完整计量'));
});
