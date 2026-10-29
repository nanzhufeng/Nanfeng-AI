import { setTimeout as delay } from 'node:timers/promises';

// Caller must verify its hidden synthetic native instance and CDP ancestry.
// The breakpoint holds the actual UI settings owner before its native write,
// without changing stored data, secrets, permissions, or shipped source.
export async function checkSettingsCloseWait(page) {
  const before = await page.evaluate(() => globalThis.__TAURI__.core.invoke('read_desktop_app_settings'));
  if (await page.locator('[data-action="show-settings"]').count()) {
    await page.locator('[data-action="show-settings"]').click();
  }
  const session = await page.context().newCDPSession(page);
  let scriptId;
  session.on('Debugger.scriptParsed', event => {
    if (event.url.endsWith('/app.mjs')) scriptId = event.scriptId;
  });
  await session.send('Debugger.enable');
  if (!scriptId) throw new Error('Native app module was not observed');
  const { scriptSource } = await session.send('Debugger.getScriptSource', { scriptId });
  const line = scriptSource.split('\n').findIndex(value => value.startsWith('function persistNativeAppSettings('));
  if (line < 0) throw new Error('Settings save owner not found');
  const { breakpointId } = await session.send('Debugger.setBreakpoint', { location: { scriptId, lineNumber: line + 1 } });
  let injected = false;
  let injectionError;
  session.once('Debugger.paused', async event => {
    try {
      const result = await session.send('Debugger.evaluateOnCallFrame', {
        callFrameId: event.callFrames[0].callFrameId,
        expression: `(() => {
          const original = persistAppSettingsPatch;
          globalThis.__settingsCloseHeld = false;
          persistAppSettingsPatch = (...args) => new Promise((resolve, reject) => {
            globalThis.__settingsCloseHeld = true;
            globalThis.__releaseSettingsSave = () => original(...args).then(resolve, reject);
          });
          return true;
        })()`, returnByValue: true,
      });
      if (result.exceptionDetails || result.result.value !== true) throw new Error('Settings delay injection failed');
      injected = true;
    } catch (error) { injectionError = error; }
    finally {
      await session.send('Debugger.removeBreakpoint', { breakpointId });
      await session.send('Debugger.resume');
    }
  });
  try {
    await page.locator('[data-action="toggle-product-setting"][data-key="memoryEnabled"]').click();
    if (injectionError) throw injectionError;
    await page.waitForFunction(() => globalThis.__settingsCloseHeld === true, {}, { timeout: 5000 });
    if (!injected) throw new Error('Delay was not applied');
    const requested = !before.product.memoryEnabled;
    const durableBeforeClose = await page.evaluate(() => globalThis.__TAURI__.core.invoke('read_desktop_app_settings'));
    try { await page.evaluate(() => globalThis.__TAURI__.event.emitTo('main', 'tauri://close-requested', {})); }
    catch (error) { if (!String(error).includes('closed')) throw error; }
    await delay(300);
    const remainedOpenUntilRelease = !page.isClosed();
    if (remainedOpenUntilRelease) {
      try { await page.evaluate(() => globalThis.__releaseSettingsSave()); }
      catch (error) { if (!String(error).includes('closed')) throw error; }
      await delay(300);
    }
    return { requested, original: before.product.memoryEnabled,
      durableBeforeClose: durableBeforeClose.product.memoryEnabled,
      remainedOpenUntilRelease, closedAfterRelease: page.isClosed(),
      closeSource: 'native close-requested event; not an OS keyboard action' };
  } finally {
    await session.send('Debugger.disable').catch(() => {});
    if (!page.isClosed()) await page.evaluate(() => globalThis.__releaseSettingsSave?.()).catch(() => {});
    await session.detach().catch(() => {});
  }
}
