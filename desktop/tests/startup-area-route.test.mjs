import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';

const source = (await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8')).replace(/\r\n/g, '\n');
const captureStart = source.indexOf("app.addEventListener('click', event => {\n  const target = event.target.closest('[data-action]');\n  const action = target?.dataset.action;\n  const workspaceAction =");
const captureEnd = source.indexOf("\napp.addEventListener('click'", captureStart + 1);
const completeStart = source.indexOf("    startupUiStage = 'ready';\n    const queuedRoute = queuedStartupAreaRoute;");
const completeEnd = source.indexOf('\n  } catch (error) {', completeStart);
assert.ok(captureStart > 0 && captureEnd > captureStart && completeStart > 0 && completeEnd > completeStart);

test('early WORK click waits for native startup instead of rendering WORK in CHAT', async () => {
  let handler;
  const calls = [];
  const state = { dataArea: 'CHAT', pane: 'chat', status: '', runtimeInfo: { dataAreasReady: true } };
  const context = vm.createContext({
    native: true, state, startupUiStage: 'pending', queuedStartupAreaRoute: null,
    areaSwitchPending: false, dataAreaStates: new Map([['CHAT', state]]),
    app: { addEventListener: (_type, callback) => { handler = callback; } },
    render: () => calls.push('render'),
    switchDataArea: async (area, pane) => { calls.push(`${area}:${pane}`); },
  });
  vm.runInContext(source.slice(captureStart, captureEnd), context);
  let prevented = false;
  let stopped = false;
  handler({ target: { closest: () => ({ dataset: { action: 'show-work' }, closest: () => null }) },
    preventDefault: () => { prevented = true; }, stopImmediatePropagation: () => { stopped = true; } });
  assert.equal(prevented, true);
  assert.equal(stopped, true);
  assert.equal(state.pane, 'chat');
  assert.equal(state.dataArea, 'CHAT');
  assert.deepEqual({ ...context.queuedStartupAreaRoute }, { area: 'WORK', pane: 'work' });
  assert.deepEqual(calls, ['render']);
  vm.runInContext(source.slice(completeStart, completeEnd), context);
  await new Promise(setImmediate);
  assert.equal(context.startupUiStage, 'ready');
  assert.equal(context.queuedStartupAreaRoute, null);
  assert.deepEqual(calls, ['render', 'WORK:work']);
});

test('returning to CHAT while startup is pending cancels an earlier WORK route', () => {
  let handler;
  const state = { dataArea: 'CHAT', pane: 'chat', runtimeInfo: { dataAreasReady: true } };
  const context = vm.createContext({ native: true, state, startupUiStage: 'pending', queuedStartupAreaRoute: { area: 'WORK', pane: 'work' },
    areaSwitchPending: false, dataAreaStates: new Map([['CHAT', state]]),
    app: { addEventListener: (_type, callback) => { handler = callback; } }, render() {} });
  vm.runInContext(source.slice(captureStart, captureEnd), context);
  handler({ target: { closest: () => ({ dataset: { action: 'show-chat' }, closest: () => null }) } });
  assert.equal(context.queuedStartupAreaRoute, null);
});
