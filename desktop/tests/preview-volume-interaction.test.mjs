import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { icon as sharedIcon, icons as sharedIcons } from '../src/icon-source.mjs';

test('volume and progress controls share their material rather than an independent bordered panel', async () => {
  const css = await readFile(new URL('../src/image-preview.css', import.meta.url), 'utf8');
  const popover = css.match(/\.attachment-preview-overlay \.preview-volume-popover \{([^}]+)\}/)[1];
  const progress = css.match(/\.attachment-preview-overlay \.video-preview-controls \{([^}]+)\}/)[1];
  for (const rule of [popover, progress]) {
    assert.match(rule, /background: var\(--playback-control-surface/);
    assert.match(rule, /backdrop-filter: blur\(var\(--playback-control-blur/);
    assert.match(rule, /border-radius: 999px/);
  }
  assert.match(popover, /border: 0/);
  assert.match(popover, /box-shadow: none/);
});

test('audio and video volume render a real speaker path, not an undefined local icon', async () => {
  const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
  const start = source.indexOf('function previewVolumeControl(kind)');
  const end = source.indexOf('function videoPreviewDialog()', start);
  const render = new Function('sharedIcon', 'sharedIcons', `${source.slice(start, end)}; return previewVolumeControl;`)(sharedIcon, sharedIcons);
  for (const kind of ['audio', 'video']) {
    const html = render(kind);
    assert.match(html, /<path d="M11/);
    assert.match(html, /M16 9/);
    assert.doesNotMatch(html, /undefined|d="<path/);
    assert.match(html, /aria-orientation="vertical"/);
  }
});

test('video and audio volume buttons open their hidden popover without scrolling', async () => {
  const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
  const start = source.indexOf("if (action === 'toggle-video-volume'");
  const end = source.indexOf("if (action === 'toggle-audio-playback')", start);
  const activate = new Function('action', 'event', 'target', source.slice(start, end));
  for (const kind of ['video', 'audio']) {
    const popover = { hidden: true };
    let focusOptions;
    const range = { hidden: false, focus(options) { focusOptions = options; } };
    const attributes = {};
    const target = {
      closest: () => ({ querySelector: selector => selector === '.preview-volume-popover' ? popover : range }),
      setAttribute: (key, value) => { attributes[key] = value; },
    };
    activate(`toggle-${kind}-volume`, { preventDefault() {} }, target);
    assert.equal(popover.hidden, false);
    assert.equal(attributes['aria-expanded'], 'true');
    assert.deepEqual(focusOptions, { preventScroll: true });
    activate(`toggle-${kind}-volume`, { preventDefault() {} }, target);
    assert.equal(popover.hidden, true);
    assert.equal(attributes['aria-expanded'], 'false');
  }
});
