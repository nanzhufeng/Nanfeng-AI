import { cp, mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { resolve } from 'node:path';
import { execFileSync } from 'node:child_process';

const desktopRoot = resolve(import.meta.dirname, '..');
const iconRoot = resolve(desktopRoot, 'src-tauri', 'icons');
const master = resolve(iconRoot, 'nanfeng_ai_icon_master.png');
const output = resolve(iconRoot, 'icon.png');
const rgbaOutput = resolve(iconRoot, 'nanfeng_ai_icon_rgba.png');
const icnsOutput = resolve(iconRoot, 'nanfeng_ai_icon.icns');
const canvasSize = 1024;
// The Desktop Dock/Finder renderer has more visible outer padding than the
// artwork preview, so keep the existing orange material and make only this
// macOS export 5.7% more prominent than the prior 1.23x rendering.
const subjectScale = 1.30;

function run(command, args) {
  execFileSync(command, args, { stdio: 'inherit' });
}

const scratch = await mkdtemp(resolve(tmpdir(), 'nanfeng-ai-macos-icon-'));
try {
  const enlarged = resolve(scratch, 'enlarged.png');
  const rendered = resolve(scratch, 'rendered.png');
  const iconset = resolve(scratch, 'icon.iconset');
  const enlargedSize = Math.round(canvasSize * subjectScale);
  run('sips', ['-z', String(enlargedSize), String(enlargedSize), master, '--out', enlarged]);
  run('sips', ['--cropToHeightWidth', String(canvasSize), String(canvasSize), enlarged, '--out', rendered]);
  await cp(rendered, output);
  await cp(rendered, rgbaOutput);
  run('mkdir', ['-p', iconset]);
  for (const [name, pixels] of [
    ['icon_16x16.png', 16], ['icon_16x16@2x.png', 32],
    ['icon_32x32.png', 32], ['icon_32x32@2x.png', 64],
    ['icon_128x128.png', 128], ['icon_128x128@2x.png', 256],
    ['icon_256x256.png', 256], ['icon_256x256@2x.png', 512],
    ['icon_512x512.png', 512], ['icon_512x512@2x.png', 1024],
  ]) run('sips', ['-z', String(pixels), String(pixels), rendered, '--out', resolve(iconset, name)]);
  run('iconutil', ['-c', 'icns', iconset, '-o', icnsOutput]);
} finally {
  await rm(scratch, { recursive: true, force: true });
}
