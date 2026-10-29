import { setTimeout as delay } from 'node:timers/promises';
import assert from 'node:assert/strict';

// Caller verifies its hidden synthetic instance. Pause only the local queue;
// do not alter native permissions, data, credentials or any shipped source.
export async function checkConversationChoiceClose(page, route) {
  await page.locator('[data-action="toggle-p6g-model-picker"]').click();
  await page.locator('[data-action="select-p6g-tier"][data-tier="DAILY"]').click();
  const modelId = await page.locator('[data-action="select-p6g-model"]').first().getAttribute('data-model-id');
  const session = await page.context().newCDPSession(page);
  let scriptId;
  session.on('Debugger.scriptParsed', event => { if (event.url.endsWith('/app.mjs')) scriptId = event.scriptId; });
  await session.send('Debugger.enable');
  const { scriptSource } = await session.send('Debugger.getScriptSource', { scriptId });
  const line = scriptSource.split('\n').findIndex(text => text.startsWith('async function updateP6GConversationOverride('));
  assert.ok(line >= 0);
  const { breakpointId } = await session.send('Debugger.setBreakpoint', { location: { scriptId, lineNumber: line + 1 } });
  let injectionError;
  session.once('Debugger.paused', async event => {
    try {
      const result = await session.send('Debugger.evaluateOnCallFrame', {
        callFrameId: event.callFrames[0].callFrameId,
        expression: `(() => {
          const original = enqueueConversationChoiceSave;
          enqueueConversationChoiceSave = write => original(() => new Promise((resolve, reject) => {
            globalThis.__choiceSaveHeld = true;
            globalThis.__releaseChoiceSave = () => Promise.resolve().then(write).then(resolve, reject);
          }));
          return true;
        })()`, returnByValue: true,
      });
      if (result.exceptionDetails || result.result.value !== true) throw new Error('Unable to hold local choice queue');
    } catch (error) { injectionError = error; }
    finally {
      await session.send('Debugger.removeBreakpoint', { breakpointId });
      await session.send('Debugger.resume');
    }
  });
  try {
    await page.locator('[data-action="select-p6g-model"]').first().click();
    if (injectionError) throw injectionError;
    await page.waitForFunction(() => globalThis.__choiceSaveHeld, {}, { timeout: 5000 });
    const beforeRelease = await page.evaluate(args => __TAURI__.core.invoke('read_desktop_p6g_selection', args), route);
    await page.evaluate(() => __TAURI__.event.emitTo('main', 'tauri://close-requested', {}));
    await delay(250);
    assert.equal(page.isClosed(), false, 'normal close must wait for the model save');
    try { await page.evaluate(() => globalThis.__releaseChoiceSave()); }
    catch (error) { if (!String(error).includes('closed')) throw error; }
    await delay(350);
    assert.equal(page.isClosed(), true);
    return { requestedModelId: modelId, durableModelBeforeRelease: beforeRelease.conversationOverride.modelId,
      stayedOpenUntilSave: true, closedAfterSave: true, closeSource: 'native close-requested event, not OS keys' };
  } finally {
    await session.send('Debugger.disable').catch(() => {});
    if (!page.isClosed()) await page.evaluate(() => globalThis.__releaseChoiceSave?.()).catch(() => {});
    await session.detach().catch(() => {});
  }
}
