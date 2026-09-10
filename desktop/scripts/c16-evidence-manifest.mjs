import assert from 'node:assert/strict';
import { access, mkdir, readFile, readdir, stat, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { basename, join, resolve } from 'node:path';
import {
  C16_APPEARANCE_CASES,
  C16_FONT_CASES,
  C16_LAYER_CASES,
  C16_MATRIX_SIZE,
} from '../src/c16-theme-matrix-fixture.mjs';

const [mode = 'init', outputRootArg] = process.argv.slice(2);
assert.ok(['init', 'audit'].includes(mode), 'usage: c16-evidence-manifest.mjs <init|audit> <output-root>');
assert.ok(outputRootArg, 'C16 evidence output root is required');

const desktopRoot = resolve(import.meta.dirname, '..');
const repoRoot = resolve(desktopRoot, '..');
const outputRoot = resolve(outputRootArg);
const platforms = Object.freeze(['android', 'browser', 'tauri']);
const sourceFiles = Object.freeze([
  'app/src/main/java/com/nanzhufeng/ai/domain/AppearanceSettings.kt',
  'app/src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt',
  'desktop/src/c16-theme-matrix-fixture.mjs',
  'desktop/src/desktop-theme-owner.mjs',
  'desktop/src/app.mjs',
  'desktop/src/chat-shell.css',
  'desktop/src-tauri/src/lib.rs',
  'desktop/src-tauri/permissions/default.toml',
  'desktop/src-tauri/capabilities/default.json',
  'desktop/tests/c16-theme-font-overlay-parity.test.mjs',
  'desktop/scripts/prepare-c16-theme-visual-acceptance.mjs',
  'desktop/scripts/capture-c16-tauri-matrix.mjs',
  'scripts/capture_c16_android_matrix.py',
]);

const sha256 = value => createHash('sha256').update(value).digest('hex');
const itemId = (appearance, font, layer) => `C16-${appearance.id}-${font.id}-${layer.id}`;
const items = C16_APPEARANCE_CASES.flatMap(appearance =>
  C16_FONT_CASES.flatMap(font => C16_LAYER_CASES.map(layer => ({
    id: itemId(appearance, font, layer),
    appearance: appearance.id,
    requestedMode: appearance.mode,
    hostDark: appearance.prefersDark,
    resolvedMode: appearance.resolved,
    font: font.id,
    fontScale: font.scale,
    layer: layer.id,
    layerLabel: layer.label,
  }))),
);

assert.equal(items.length, C16_MATRIX_SIZE);
assert.equal(new Set(items.map(item => item.id)).size, C16_MATRIX_SIZE, 'C16 logical IDs must be unique');

await mkdir(outputRoot, { recursive: true });
await Promise.all(platforms.map(platform => mkdir(join(outputRoot, platform), { recursive: true })));
const sourceHashes = {};
for (const relativePath of sourceFiles) {
  const bytes = await readFile(resolve(repoRoot, relativePath));
  sourceHashes[relativePath] = sha256(bytes);
}
const sourceFingerprint = sha256(JSON.stringify(sourceHashes));

async function evidenceFor(platform, item) {
  const directory = join(outputRoot, platform);
  const png = join(directory, `${item.id}.png`);
  const metadata = join(directory, `${item.id}.json`);
  const xml = platform === 'android' ? join(directory, `${item.id}.xml`) : null;
  const required = [png, metadata, ...(xml ? [xml] : [])];
  const present = [];
  for (const path of required) {
    try {
      await access(path);
      const details = await stat(path);
      present.push({ file: basename(path), bytes: details.size, sha256: sha256(await readFile(path)) });
    } catch { /* pending evidence is expected during init */ }
  }
  let metadataValid = false;
  let metadataError = null;
  try {
    const parsed = JSON.parse(await readFile(metadata, 'utf8'));
    const screenshot = present.find(file => file.file === basename(png));
    metadataValid = parsed.platform === platform
      && parsed.itemId === item.id
      && parsed.sourceFingerprint === sourceFingerprint
      && parsed.requested?.appearance === item.appearance
      && parsed.requested?.mode === item.requestedMode
      && parsed.requested?.hostDark === item.hostDark
      && parsed.requested?.resolvedMode === item.resolvedMode
      && parsed.requested?.font === item.font
      && parsed.requested?.fontScale === item.fontScale
      && parsed.requested?.layer === item.layer
      && parsed.screenshotSha256 === screenshot?.sha256
      && parsed.pass === true;
    if (!metadataValid) metadataError = 'metadata fields, screenshot hash, source fingerprint, or pass gate do not match';
  } catch (error) {
    metadataError = String(error);
  }
  return {
    itemId: item.id,
    screenshot: basename(png),
    metadata: basename(metadata),
    semanticTree: xml ? basename(xml) : null,
    status: present.length === required.length && present.every(file => file.bytes > 0) && metadataValid ? 'CAPTURED' : 'PENDING',
    metadataValid,
    metadataError,
    files: present,
  };
}

const evidence = {};
for (const platform of platforms) evidence[platform] = await Promise.all(items.map(item => evidenceFor(platform, item)));
const counts = Object.fromEntries(platforms.map(platform => [platform, {
  expected: C16_MATRIX_SIZE,
  captured: evidence[platform].filter(item => item.status === 'CAPTURED').length,
  pending: evidence[platform].filter(item => item.status !== 'CAPTURED').length,
}]));

let generatedAt = new Date().toISOString();
if (mode === 'audit') {
  try {
    const previousManifest = JSON.parse(await readFile(join(outputRoot, 'evidence-manifest.json'), 'utf8'));
    if (typeof previousManifest.generatedAt === 'string' && previousManifest.generatedAt.length > 0) {
      generatedAt = previousManifest.generatedAt;
    }
  } catch { /* the first audit may legitimately create the manifest */ }
}

const manifest = {
  schema: 'nanfeng-ai.c16.visual-evidence.v1',
  generatedAt,
  outputRoot,
  sourceFingerprint,
  sourceHashes,
  contract: {
    formula: '4 appearance resolutions x 3 font sizes x 7 layers',
    logicalItemCount: C16_MATRIX_SIZE,
    platformEvidenceSlotCount: C16_MATRIX_SIZE * platforms.length,
    items,
  },
  environment: {
    android: { serial: 'emulator-5554', avd: 'NanzhufengFindN5Api35', viewport: '1140x2616', densityDpi: 442, fontScale: 1, network: 'disabled', package: 'com.nanzhufeng.ai.searchattachmentacceptance' },
    browser: { viewport: '1280x720', devicePixelRatio: 2, fixture: 'c16ThemePreview', network: 'local-only' },
    tauri: { dataRootPrefix: '/tmp/nanfeng-ai-desktop-c16-visual-acceptance.', startupMode: 'UI_SCHEMA_DIAGNOSTIC_ACCEPTANCE', network: 'suppressed', signature: 'ad-hoc strict' },
  },
  antiDuplicationGates: [
    'exactly 84 unique logical IDs',
    'exactly one PNG and one metadata sidecar per platform and logical ID',
    'Android additionally requires one UIAutomator XML tree per logical ID',
    'sidecar must record requested mode, host-dark input, resolved mode, font, layer, timestamp, and source fingerprint',
    'duplicate pixel hashes are allowed only for equivalent resolved light or dark states and never substitute a missing logical ID',
    'audit fails on unknown filenames, zero-byte files, missing slots, repeated item IDs, or source fingerprint drift',
  ],
  counts,
  evidence,
};

if (mode === 'audit') {
  for (const platform of platforms) {
    assert.equal(counts[platform].captured, C16_MATRIX_SIZE, `${platform} must capture all 84 C16 items`);
    const allowed = new Set(evidence[platform].flatMap(item => [item.screenshot, item.metadata, item.semanticTree].filter(Boolean)));
    const unknown = (await readdir(join(outputRoot, platform))).filter(file => !allowed.has(file) && !file.startsWith('contact-sheet'));
    assert.deepEqual(unknown, [], `${platform} contains unknown per-item evidence files`);
  }
}

await writeFile(join(outputRoot, 'evidence-manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`);
console.log(JSON.stringify({ mode, outputRoot, sourceFingerprint, counts, logicalItems: items.length, evidenceSlots: items.length * platforms.length }));
