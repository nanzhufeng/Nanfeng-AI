import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { closeSync, openSync } from 'node:fs';
import { spawn, spawnSync } from 'node:child_process';
import { join, resolve } from 'node:path';

const [outputRootArg, executableArg, dataRootArg] = process.argv.slice(2);
if (!outputRootArg || !executableArg || !dataRootArg) {
  throw new Error('usage: capture-c16-tauri-matrix.mjs <output-root> <acceptance-executable> <acceptance-root>');
}
const outputRoot = resolve(outputRootArg);
const output = join(outputRoot, 'tauri');
const executable = resolve(executableArg);
const dataRoot = resolve(dataRootArg);
const manifest = JSON.parse(await readFile(join(outputRoot, 'evidence-manifest.json'), 'utf8'));
const windowHelper = '/tmp/nanfeng-ai-c16-window-id';
const swiftSource = resolve(import.meta.dirname, 'c16-window-id.swift');
const appearances = [
  { id: 'system-light', mode: 'system', hostDark: false, resolvedMode: 'light' },
  { id: 'system-dark', mode: 'system', hostDark: true, resolvedMode: 'dark' },
  { id: 'light', mode: 'light', hostDark: true, resolvedMode: 'light' },
  { id: 'dark', mode: 'dark', hostDark: false, resolvedMode: 'dark' },
];
const fonts = [
  { id: 'small', fontScale: 0.8 },
  { id: 'standard', fontScale: 1 },
  { id: 'large', fontScale: 1.24 },
];
const layers = [
  { id: 'model-root', tokens: ['选择模型', '自动'] },
  { id: 'model-daily', tokens: ['日常', 'DeepSeek V4 Flash'] },
  { id: 'model-deep', tokens: ['深度', 'DeepSeek V4 Pro'] },
  { id: 'add-root', tokens: ['基础风格和语气', '实时网页搜索'] },
  { id: 'style', tokens: ['基础风格和语气', '直言不讳'] },
  { id: 'search-history', tokens: ['最近搜索', '清空'] },
  { id: 'settings-theme', tokens: ['橙色', '紫色'] },
];
const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');
const delay = milliseconds => new Promise(resolveDelay => setTimeout(resolveDelay, milliseconds));

function run(command, args, options = {}) {
  const result = spawnSync(command, args, { encoding: 'utf8', ...options });
  if (result.status !== 0) throw new Error(`${command} failed: ${result.stderr || result.stdout}`);
  return result.stdout.trim();
}

async function waitForWindow(pid) {
  for (let attempt = 0; attempt < 50; attempt += 1) {
    const result = spawnSync(windowHelper, [String(pid)], { encoding: 'utf8' });
    if (result.status === 0 && result.stdout.trim()) {
      const [id, width, height, ...title] = result.stdout.trim().split('\t');
      return { id: Number(id), width: Number(width), height: Number(height), title: title.join('\t') };
    }
    await delay(100);
  }
  throw new Error(`window did not appear for pid ${pid}`);
}

async function stop(child) {
  if (child.exitCode !== null) return;
  child.kill('SIGTERM');
  await Promise.race([
    new Promise(resolveExit => child.once('exit', resolveExit)),
    delay(1500).then(() => { if (child.exitCode === null) child.kill('SIGKILL'); }),
  ]);
}

await mkdir(output, { recursive: true });
run('swiftc', [swiftSource, '-o', windowHelper]);
run('codesign', ['--verify', '--deep', '--strict', '--verbose=2', executable.slice(0, executable.indexOf('.app/') + 4)]);
const executableHash = run('shasum', ['-a', '256', executable]).split(/\s+/)[0];
const results = [];
for (const appearance of appearances) {
  for (const font of fonts) {
    for (const layer of layers) {
      const state = `${appearance.id}__${font.id}__${layer.id}`;
      const itemId = `C16-${appearance.id}-${font.id}-${layer.id}`;
      const logFile = join(outputRoot, 'probes', `${itemId}.log`);
      const logHandle = openSync(logFile, 'w');
      const child = spawn(executable, ['--diagnostic-ui-schema-acceptance'], {
        env: {
          ...process.env,
          NANFENG_AI_DESKTOP_C16_VISUAL_ACCEPTANCE: '1',
          NANFENG_AI_DESKTOP_C16_VISUAL_ACCEPTANCE_ROOT: dataRoot,
          NANFENG_AI_DESKTOP_C16_VISUAL_STATE: state,
        },
        stdio: ['ignore', logHandle, logHandle],
      });
      try {
        let window = await waitForWindow(child.pid);
        await delay(850);
        // CGWindow reports the opening animation's intermediate size. Read it again only after
        // the native window has settled, otherwise correct 1440x900 pixels can be mislabeled.
        window = await waitForWindow(child.pid);
        if (child.exitCode !== null) throw new Error(`acceptance process exited before capture: ${child.exitCode}`);
        const pngPath = join(output, `${itemId}.png`);
        run('screencapture', ['-x', '-l', String(window.id), pngPath]);
        const screenshot = await readFile(pngPath);
        const axTree = spawnSync('osascript', [
          '-e',
          `tell application "System Events" to tell first application process whose unix id is ${child.pid} to get entire contents of window 1`,
        ], { encoding: 'utf8', maxBuffer: 16 * 1024 * 1024 });
        const axText = `${axTree.stdout || ''}\n${axTree.stderr || ''}`;
        const expectedTokens = Object.fromEntries(layer.tokens.map(token => [token, axText.includes(token)]));
        const logText = await readFile(logFile, 'utf8');
        const metadata = {
          schemaVersion: 1,
          platform: 'tauri',
          itemId,
          requested: {
            appearance: appearance.id,
            mode: appearance.mode,
            hostDark: appearance.hostDark,
            resolvedMode: appearance.resolvedMode,
            font: font.id,
            fontScale: font.fontScale,
            layer: layer.id,
          },
          environment: {
            bundle: executable.slice(0, executable.indexOf('.app/') + 4),
            executable,
            executableSha256: executableHash,
            dataRoot,
            startupMode: 'UI_SCHEMA_DIAGNOSTIC_ACCEPTANCE',
            signature: 'ad-hoc strict verified',
            network: 'automatic work suppressed by diagnostic startup mode',
          },
          sourceFingerprint: manifest.sourceFingerprint,
          capturedAt: new Date().toISOString(),
          window,
          screenshotSha256: sha256(screenshot),
          accessibilityTreeSha256: sha256(Buffer.from(axText)),
          expectedTokens,
          processAliveAtCapture: child.exitCode === null,
          startupGateErrorAbsent: !logText.includes('C16_VISUAL_ACCEPTANCE_GATE'),
        };
        metadata.pass = screenshot.length > 0
          && window.width === 1440
          && window.height === 900
          && metadata.processAliveAtCapture
          && metadata.startupGateErrorAbsent
          && Object.values(expectedTokens).every(Boolean);
        await writeFile(join(output, `${itemId}.json`), `${JSON.stringify(metadata, null, 2)}\n`);
        results.push({ itemId, pass: metadata.pass, sha256: metadata.screenshotSha256 });
        console.log(JSON.stringify({ captured: results.length, itemId, pass: metadata.pass }));
      } finally {
        await stop(child);
        closeSync(logHandle);
      }
    }
  }
}
console.log(JSON.stringify({
  captured: results.length,
  passed: results.filter(item => item.pass).length,
  failed: results.filter(item => !item.pass).map(item => item.itemId),
  uniqueScreenshots: new Set(results.map(item => item.sha256)).size,
}));
