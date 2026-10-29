import fs from 'node:fs/promises';
import assert from 'node:assert/strict';

// Run only against a caller-verified, hidden synthetic acceptance instance.
export async function checkCapsuleControls(page, output) {
  page.setDefaultTimeout(5000);
  if (await page.locator('[data-action="show-settings"]').count()) {
    await page.locator('[data-action="show-settings"]').click();
  }
  await page.locator('[data-action="open-settings-page"][data-page="model"]').click();
  await page.locator('[data-action="open-settings-page"][data-page="model-configuration"]').click();
  const selectors = '.android-settings-segments > button, .android-settings-model-save > button, .android-settings-model-test';
  const read = () => page.locator(selectors).evaluateAll(elements => elements.map(element => {
    const style = getComputedStyle(element), bounds = element.getBoundingClientRect();
    return { label: element.textContent.trim(), width: bounds.width, height: bounds.height,
      radius: style.borderRadius, background: style.backgroundColor, shadow: style.boxShadow,
      outline: style.outlineStyle, backgroundImage: style.backgroundImage,
      focusVisible: element.matches(':focus-visible'), transform: style.transform, whiteSpace: style.whiteSpace,
      overflowing: element.scrollWidth > element.clientWidth };
  }));
  const results = { nativeDpr: await page.evaluate(() => devicePixelRatio), states: [] };
  const session = await page.context().newCDPSession(page);
  try {
    for (const scale of [1, 1.25, 1.5, 2]) {
      await session.send('Emulation.setDeviceMetricsOverride', {
        width: 1440, height: 900, deviceScaleFactor: scale, mobile: false,
      });
      const controls = await read();
      assert.ok(controls.length >= 6);
      for (const control of controls) {
        assert.equal(control.radius, '999px', control.label);
        assert.equal(control.whiteSpace, 'nowrap', control.label);
        assert.equal(control.overflowing, false, control.label);
        if (control.label === '保存') assert.notEqual(control.shadow, 'none');
      }
      results.states.push({ scale, state: 'rest', controls });
      if (scale === 1) await page.screenshot({ path: `${output}/capsule-after.png` });
    }
    await session.send('Emulation.clearDeviceMetricsOverride');
    const button = page.locator('.android-settings-model-save > button');
    await button.hover();
    results.states.push({ state: 'hover', controls: await read() });
    await page.mouse.down();
    results.states.push({ state: 'pressed', controls: await read() });
    await page.mouse.move(1, 1);
    await page.mouse.up();
    await page.keyboard.press('Tab');
    await button.focus();
    results.states.push({ state: 'focus', controls: await read() });
    const focused = results.states.at(-1).controls.find(control => control.label === '保存');
    assert.equal(focused.focusVisible, true);
    assert.notEqual(focused.backgroundImage, 'none');
    assert.notEqual(focused.shadow, 'none');
    for (const state of results.states) {
      for (const control of state.controls) assert.equal(control.radius, '999px');
    }
    await fs.writeFile(`${output}/capsule-native.json`, JSON.stringify(results, null, 2));
    return { scales: [1, 1.25, 1.5, 2], states: results.states.length, controls: results.states[0].controls.length };
  } finally {
    await session.send('Emulation.clearDeviceMetricsOverride').catch(() => {});
    await session.detach();
  }
}
