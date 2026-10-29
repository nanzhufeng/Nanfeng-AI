import { lstat, mkdir, readFile, readdir, writeFile as writeBytes } from 'node:fs/promises';
import { dirname, relative, resolve, sep } from 'node:path';
import { MODEL_BRAND_NAMES } from '../src/model-settings-browser.mjs';

const root = resolve(import.meta.dirname, '..');
const dist = resolve(root, 'dist');
const expected = new Map();

async function directory(path) {
  if (path !== dist && !path.startsWith(dist + sep)) throw new Error('构建资源目录越界');
  if (path !== dist) await directory(dirname(path));
  const info = await lstat(path).catch(error => { if (error.code !== 'ENOENT') throw error; });
  if (info && (!info.isDirectory() || info.isSymbolicLink())) throw new Error(`构建资源目录不可重定向：${relative(dist, path)}`);
  await mkdir(path, { recursive: true });
}
async function output(path, bytes) {
  await directory(dirname(path));
  const info = await lstat(path).catch(error => { if (error.code !== 'ENOENT') throw error; });
  if (info && (!info.isFile() || info.isSymbolicLink())) throw new Error(`构建资源文件不可重定向：${relative(dist, path)}`);
  await writeBytes(path, bytes);
  expected.set(relative(dist, path).split(sep).join('/'), Buffer.from(bytes));
}
async function cp(source, destination) {
  const info = await lstat(source);
  if (info.isSymbolicLink()) throw new Error(`构建输入不可重定向：${relative(root, source)}`);
  if (info.isDirectory()) {
    await directory(destination);
    for (const entry of await readdir(source)) await cp(resolve(source, entry), resolve(destination, entry));
  } else {
    if (!info.isFile()) throw new Error(`构建输入不是普通文件：${relative(root, source)}`);
    await output(destination, await readFile(source));
  }
}
const writeFile = output;
async function packagedFiles(path = dist) {
  const result = [];
  for (const entry of await readdir(path, { withFileTypes: true })) {
    const file = resolve(path, entry.name);
    if (entry.isSymbolicLink()) throw new Error(`构建输出不可重定向：${relative(dist, file)}`);
    if (entry.isDirectory()) result.push(...await packagedFiles(file));
    else if (entry.isFile()) result.push(relative(dist, file).split(sep).join('/'));
    else throw new Error(`构建输出不是普通文件：${relative(dist, file)}`);
  }
  return result.sort();
}
function references(path, source) {
  if (path.endsWith('.mjs')) {
    const imports = [...source.matchAll(/\b(?:import|export)\s*(?:[^'";]*?\bfrom\s*)?['"](\.[^'"]+)['"]/g)];
    const dynamic = [...source.matchAll(/\bimport\s*\(\s*['"](\.[^'"]+)['"]\s*\)/g)];
    if ([...source.matchAll(/\bimport\s*\(/g)].length !== dynamic.length) throw new Error(`构建资源含未声明的动态导入：${path}`);
    return [...imports, ...dynamic].map(match => match[1]);
  }
  if (path.endsWith('.html')) return [...source.matchAll(/\b(?:src|href)=["']([^"']+)["']/g)].map(match => match[1]);
  if (path.endsWith('.css')) return [
    ...[...source.matchAll(/\burl\(\s*(['"]?)(.*?)\1\s*\)/g)].map(match => match[2]),
    ...[...source.matchAll(/@import\s+['"]([^'"]+)['"]/g)].map(match => match[1]),
  ];
  return [];
}
async function verifyResources() {
  const files = await packagedFiles();
  const extra = files.filter(file => !expected.has(file));
  if (extra.length) throw new Error(`构建输出包含未声明的旧资源，请核对后处理，原文件已保留：${extra.slice(0, 20).join('、')}`);
  for (const [name, bytes] of expected) {
    if (!(await readFile(resolve(dist, name))).equals(bytes)) throw new Error(`构建资源字节不一致：${name}`);
  }
  const pending = ['index.html', 'island.html', 'nanfeng-ai-icon.png', ...new Set(Object.values(MODEL_BRAND_NAMES).map(brand => `assets/model-brands/${brand}.png`))];
  const visited = new Set();
  while (pending.length) {
    const name = pending.pop();
    if (visited.has(name)) continue;
    visited.add(name);
    if (!expected.has(name)) throw new Error(`构建缺少引用资源：${name}；请将它加入显式构建清单`);
    for (const reference of references(name, expected.get(name).toString('utf8'))) {
      if (/^(?:[a-z][a-z0-9+.-]*:|\/\/|#)/i.test(reference)) continue;
      const local = decodeURIComponent(reference.split(/[?#]/)[0]);
      if (!local) continue;
      const target = resolve(local.startsWith('/') ? dist : dirname(resolve(dist, name)), local.startsWith('/') ? local.slice(1) : local);
      if (!target.startsWith(dist + sep)) throw new Error(`构建资源引用越界：${name}`);
      pending.push(relative(dist, target).split(sep).join('/'));
    }
  }
  return { files: files.length, reachable: visited.size };
}
await directory(dist);
await cp(resolve(root, 'src', 'search-attachment-menu.mjs'), resolve(dist, 'search-attachment-menu.mjs'));
await cp(resolve(root, 'src', 'search-result-cache.mjs'), resolve(dist, 'search-result-cache.mjs'));
await cp(resolve(root, 'src', 'local-write-barrier.mjs'), resolve(dist, 'local-write-barrier.mjs'));
for (const file of ['desktop-app-entry.mjs', 'generation-continuity.mjs', 'inline-conversation-title.mjs']) await cp(resolve(root, 'src', file), resolve(dist, file));
for (const file of ['markdown-link.mjs', 'code-syntax.mjs', 'answer-information.mjs', 'cloud-read-feedback.mjs', 'utility-page-navigation.mjs', 'runtime-chat-commit.mjs']) await cp(resolve(root, 'src', file), resolve(dist, file));
for (const file of ['index.html', 'styles.css', 'viewport-shell.css', 'chat-shell.css', 'scroll-surfaces.css', 'image-preview.css', 'modal-layer-owner.mjs', 'p8-inspect.css', 'desktop-transcription-page.css', 'desktop-reminders-page.css', 'app.mjs', 'chat-ui-policy.mjs', 'data-area-invoke.mjs', 'composer-size.mjs', 'composer-draft-recovery.mjs', 'draft-write-queue.mjs', 'android-action-icons.mjs', 'chat-shell.mjs', 'memory-summary-policy.mjs', 'conversation-message-order.mjs', 'conversation-long-press.mjs', 'conversation-lifecycle-view.mjs', 'conversation-find-interaction.mjs', 'selected-conversation-continuation.mjs', 'local-data-view.mjs', 'desktop-search-page.mjs', 'desktop-attachment-preview-owner.mjs', 'attachment-preview-key.mjs', 'image-preview-navigation.mjs', 'search-result-anchor.mjs', 'desktop-theme-owner.mjs', 'cloud-conversation-list-merge.mjs', 'cloud-conversation-open-route.mjs', 'c08-search-preview-fixture.mjs', 'c09-reminder-preview-fixture.mjs', 'c10-transcription-preview-fixture.mjs', 'c12-model-network-preview-fixture.mjs', 'c13-conversation-lifecycle-preview-fixture.mjs', 'c14-local-data-preview-fixture.mjs', 'c15-workspace-preview-fixture.mjs', 'c16-theme-matrix-fixture.mjs', 'workspace-view.mjs', 'desktop-transcription-page.mjs', 'desktop-reminders-page.mjs', 'safe-markdown.mjs', 'reply-text-emphasis.mjs', 'pdf-knowledge-tasks.mjs', 'web-knowledge-tasks.mjs', 'knowledge-duplicates.mjs', 'domain-editor-conflict.mjs', 'source-link-presentation.mjs', 'icon-source.mjs', 'desktop-parity-preferences.mjs', 'desktop-cost-estimator.mjs', 'usage-ledger-display.mjs', 'android-settings-shell.mjs', 'p8-inspect.mjs', 'pwa-boundaries.mjs', 'recycle-confirmation.mjs', 'reminder-notification-routing.mjs']) await cp(resolve(root, 'src', file), resolve(dist, file));
await cp(resolve(root, '../app/src/main/res/drawable-nodpi/nanfeng_ai_icon_foreground_image.png'), resolve(dist, 'nanfeng-ai-icon.png'));
const report = JSON.parse((await readFile(resolve(root, '../protocol/fixtures/nfai.exchange.v1.golden.json'), 'utf8'))); await writeFile(resolve(dist, 'fixture-summary.json'), JSON.stringify({ format: report.format, version: report.version, semanticHash: report.export.semanticHash }));

for (const file of ['model-settings-controller.mjs', 'model-settings-browser.mjs', 'relaycore-models.mjs', 'auto-chat-routing.mjs', 'attachment-input-policy.mjs', 'attachment-document-types.mjs']) await cp(resolve(root, 'src', file), resolve(dist, file));
await cp(resolve(root, 'src/assets/model-brands'), resolve(dist, 'assets/model-brands'), { recursive: true });
const documentMimeTypes = JSON.parse(await readFile(resolve(root, '../protocol/fixtures/attachment-document-types-v1.json'), 'utf8')).mimeTypes;
await writeFile(resolve(dist, 'attachment-document-types.mjs'), `export const ATTACHMENT_DOCUMENT_MIME_TYPES = Object.freeze(${JSON.stringify(documentMimeTypes)});\n`);

for (const file of ['island.html', 'island-window.mjs', 'nanfeng-island.mjs', 'nanfeng-island.css']) await cp(resolve(root, 'src', file), resolve(dist, file));
const resources = await verifyResources();
console.log(`desktop static spike built: ${dist} (${resources.files} verified resources, ${resources.reachable} reachable)`);
