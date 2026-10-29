import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';
import { invokeForDataArea, invokeForAuthorizedDataAreaOperation } from '../src/data-area-invoke.mjs';
import { resolveConversation, renderChatFirstShell } from '../src/chat-shell.mjs';

const app = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
const stopOwner = app.slice(app.indexOf("  if (action === 'stop-ordinary-chat') {"), app.indexOf("  if (action === 'retry-ordinary-chat') {"));
function harness() {
  let finish, reject;
  const result = new Promise((resolve, fail) => { finish = resolve; reject = fail; });
  const calls = [];
  const message = { id: 'assistant-A', attemptId: 'attempt-A', role: 'ASSISTANT', delivery: 'PARTIAL', runtimeState: 'RUNNING', blocks: [{ kind: 'TEXT', text: 'Synthetic saved partial' }] };
  const conversation = { id: 'conversation-A', title: 'Synthetic Stop', messages: [message], currentLeafId: message.id };
  const state = { dataArea: 'CHAT', pane: 'chat', current: { summary: { id: 'workspace-A' }, exchange: { conversations: [conversation] } },
    selectedConversationId: 'conversation-A', status: 'A generating', error: '', composerDraft: 'A draft' };
  let paints = 0, context;
  const bridge = { invoke: async (command, payload, options) => {
    calls.push({ command, payload, area: options.headers['x-nanfeng-data-area'] }); return result;
  } };
  context = vm.createContext({ state, native: true, action: 'stop-ordinary-chat', event: { preventDefault() {} },
    target: { dataset: { attemptId: 'attempt-A' } }, resolveConversation,
    desktopEntryRouteGeneration: 0, cloudOpenAreaGeneration: 0, ordinaryStopRequests: new WeakMap(),
    tauriBridge: bridge, invokeForAuthorizedDataAreaOperation,
    invoke: (command, args, owner = context.state) => invokeForDataArea(bridge, command, args, owner, () => context.state),
    render: () => { paints++; } });
  vm.runInContext(`async function stop() { ${stopOwner} }`, context);
  return { state, message, conversation, context, calls, finish, reject, paints: () => paints,
    stop: () => vm.runInContext('stop()', context) };
}
for (const change of ['conversation', 'workspace', 'area']) {
  test(`late Stop acknowledgement cannot repaint another ${change}`, async () => {
    const h = harness(); const stopping = h.stop();
    const destination = change === 'area' ? { dataArea: 'WORK', current: { summary: { id: 'workspace-A' } },
      selectedConversationId: 'conversation-A' } : h.state;
    if (change === 'conversation') destination.selectedConversationId = 'conversation-B';
    if (change === 'workspace') destination.current = { summary: { id: 'workspace-B' } };
    destination.status = 'B generating'; destination.error = 'B independent error'; destination.composerDraft = 'B next draft';
    h.context.state = destination;
    h.finish(true); await stopping;
    assert.equal(h.calls.length, 1);
    assert.equal(h.calls[0].command, 'cancel_desktop_ordinary_chat');
    assert.equal(h.calls[0].area, 'CHAT');
    assert.equal(h.calls[0].payload.args.conversationId, 'conversation-A');
    assert.equal(h.calls[0].payload.args.workspaceId, 'workspace-A', 'Stop must bind the original workspace even when conversation IDs repeat');
    assert.equal(destination.status, 'B generating'); assert.equal(destination.error, 'B independent error');
    assert.equal(destination.composerDraft, 'B next draft'); assert.equal(h.paints(), 0);
  });
}
test('late Stop failure does not overwrite the new conversation error', async () => {
  const h = harness(); const stopping = h.stop();
  h.state.selectedConversationId = 'conversation-B'; h.state.error = 'B failed Send';
  h.reject(new Error('A cancellation rejected')); await stopping;
  assert.equal(h.state.error, 'B failed Send'); assert.equal(h.paints(), 0);
});
test('Stop result remains visible when its original conversation is still selected', async () => {
  const h = harness(); const stopping = h.stop(); h.finish(true); await stopping;
  assert.match(h.state.status, /正在停止/); assert.equal(h.state.error, ''); assert.equal(h.paints(), 1);
});
test('Stop failure remains visible on its original conversation', async () => {
  const h = harness(); const stopping = h.stop(); h.reject(new Error('cancellation unavailable')); await stopping;
  assert.match(h.state.error, /停止失败.*cancellation unavailable/); assert.equal(h.paints(), 1);
});

test('Stop binds the Attempt shown by the clicked control', async () => {
  const h = harness(); const stopping = h.stop(); h.finish(true); await stopping;
  assert.equal(h.calls[0].payload.args.expectedAttemptId, 'attempt-A');
});

test('a stale Stop control cannot dispatch against a newer run in the same conversation', async () => {
  const h = harness(); h.message.attemptId = 'attempt-B';
  h.finish(true); await h.stop();
  assert.equal(h.calls.length, 0); assert.equal(h.paints(), 0);
});

for (const outcome of ['success', 'failure']) {
  test(`late Stop ${outcome} cannot overwrite the next run in the same conversation`, async () => {
    const h = harness(); const stopping = h.stop();
    h.message.runtimeState = 'COMPLETED'; h.message.delivery = 'COMPLETE';
    h.conversation.messages.push({ id: 'assistant-B', attemptId: 'attempt-B', role: 'ASSISTANT', delivery: 'PARTIAL', runtimeState: 'RUNNING' });
    h.state.status = 'B generating'; h.state.error = 'B independent error';
    if (outcome === 'success') h.finish(true); else h.reject(new Error('old Stop failed'));
    await stopping;
    assert.equal(h.state.status, 'B generating'); assert.equal(h.state.error, 'B independent error'); assert.equal(h.paints(), 0);
  });
}

test('leaving and returning to the same conversation invalidates old Stop feedback', async () => {
  const h = harness(); const stopping = h.stop();
  h.context.desktopEntryRouteGeneration += 2;
  h.state.status = 'Returned to A'; h.finish(true); await stopping;
  assert.equal(h.state.status, 'Returned to A'); assert.equal(h.paints(), 0);
});

test('Stop cannot clear a newer error while the same Attempt is still running', async () => {
  const h = harness(); const stopping = h.stop();
  h.state.error = 'A later explicit operation failed'; h.finish(true); await stopping;
  assert.equal(h.state.error, 'A later explicit operation failed'); assert.equal(h.paints(), 0);
});

test('both ordinary Stop controls carry the actual running Attempt identity', () => {
  const h = harness();
  const html = renderChatFirstShell({ data: h.state.current, native: true, selectedConversationId: 'conversation-A',
    composerDraft: '', chatSearch: '', pane: 'chat', status: '', error: '', connection: {},
    appearance: { mode: 'system', fontSize: 'standard', themeColor: 'orange' } });
  const controls = [...html.matchAll(/<button\b[^>]*data-action="stop-ordinary-chat"[^>]*>/g)].map(x => x[0]);
  assert.equal(controls.length, 2);
  assert.ok(controls.every(control => control.includes('data-attempt-id="attempt-A"')));
});
