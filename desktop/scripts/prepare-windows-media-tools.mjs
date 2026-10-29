import { createHash } from 'node:crypto';
import { createReadStream } from 'node:fs';
import { copyFile, mkdir, readFile, rename, rm, stat, writeFile } from 'node:fs/promises';
import { spawnSync } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'win32') throw new Error('Windows 媒体工具只能在 Windows 构建机准备。');

const desktopRoot = resolve(fileURLToPath(new URL('..', import.meta.url)));
const lockPath = resolve(desktopRoot, 'scripts/windows-media-tools.lock.json');
const lock = JSON.parse(await readFile(lockPath, 'utf8'));
const generatedParent = resolve(desktopRoot, 'src-tauri/resources/media-tools');
const target = resolve(generatedParent, 'windows-x86_64');
const cacheRoot = resolve(desktopRoot, '.cache/media-tools');
const archive = resolve(cacheRoot, 'archives', `${lock.archiveSha256}.zip`);
const extracted = resolve(cacheRoot, 'extracted', lock.archiveSha256);
const sourceNotice = [
  `FFmpeg ${lock.bundleVersion}`,
  `Target: ${lock.target}`,
  `License: ${lock.license}`,
  `Upstream source: ${lock.upstreamSource}`,
  `Build scripts: ${lock.buildSource}`,
  `Release asset: ${lock.archiveUrl}`,
  `Archive SHA-256: ${lock.archiveSha256}`,
  ''
].join('\n');

function requireSafeGeneratedPath(path) {
  const parent = `${generatedParent.toLowerCase()}\\`;
  if (!path.toLowerCase().startsWith(parent)) throw new Error(`拒绝修改媒体工具目录外的路径：${path}`);
}

async function sha256(path) {
  const digest = createHash('sha256');
  for await (const chunk of createReadStream(path)) digest.update(chunk);
  return digest.digest('hex');
}

async function verifyFile(path, expected) {
  const metadata = await stat(path).catch(() => null);
  if (!metadata?.isFile() || metadata.size !== expected.bytes) return false;
  return await sha256(path) === expected.sha256.toLowerCase();
}

async function verifyPrepared() {
  const manifest = JSON.parse(await readFile(resolve(target, 'runtime-manifest.json'), 'utf8').catch(() => 'null'));
  if (!manifest || manifest.schemaVersion !== lock.schemaVersion || manifest.bundleVersion !== lock.bundleVersion
      || manifest.target !== lock.target || manifest.license !== lock.license
      || manifest.archiveSha256 !== lock.archiveSha256) return false;
  for (const file of lock.runtimeFiles) {
    if (!await verifyFile(resolve(target, 'bin', file.name), file)) return false;
  }
  if (!await verifyFile(resolve(target, lock.licenseFile.name), lock.licenseFile)) return false;
  return await readFile(resolve(target, 'SOURCE.txt'), 'utf8').catch(() => null) === sourceNotice;
}

async function downloadArchive() {
  if (await verifyFile(archive, { bytes: (await stat(archive).catch(() => ({ size: -1 }))).size, sha256: lock.archiveSha256 })) return;
  await mkdir(dirname(archive), { recursive: true });
  const pending = `${archive}.${process.pid}.pending`;
  await rm(pending, { force: true });
  const child = spawnSync('curl.exe', [
    '--location', '--fail', '--silent', '--show-error',
    '--output', pending, lock.archiveUrl
  ], { stdio: 'inherit', shell: false });
  try {
    if (child.error) throw child.error;
    if (child.status !== 0) throw new Error(`FFmpeg 固定资产下载失败：${child.status ?? 'unknown'}`);
    if (await sha256(pending) !== lock.archiveSha256) throw new Error('FFmpeg 固定资产 SHA-256 不匹配。');
    await rm(archive, { force: true });
    await rename(pending, archive);
  } catch (error) {
    await rm(pending, { force: true });
    throw error;
  }
}

async function extractArchive() {
  const marker = resolve(extracted, '.complete');
  if (await readFile(marker, 'utf8').catch(() => '') === lock.archiveSha256) return;
  const parent = dirname(extracted);
  await mkdir(parent, { recursive: true });
  const pending = resolve(parent, `${lock.archiveSha256}.${process.pid}.pending`);
  await rm(pending, { recursive: true, force: true });
  await mkdir(pending, { recursive: true });
  const child = spawnSync('tar.exe', ['-xf', archive, '-C', pending], { stdio: 'inherit', shell: false });
  if (child.error) throw child.error;
  if (child.status !== 0) throw new Error(`FFmpeg 固定资产解压失败：${child.status ?? 'unknown'}`);
  await writeFile(resolve(pending, '.complete'), lock.archiveSha256, 'utf8');
  await rm(extracted, { recursive: true, force: true });
  await rename(pending, extracted);
}

async function prepareRuntime() {
  if (await verifyPrepared()) return;
  await downloadArchive();
  await extractArchive();
  const sourceRoot = resolve(extracted, lock.archiveRoot);
  for (const file of lock.runtimeFiles) {
    if (!await verifyFile(resolve(sourceRoot, 'bin', file.name), file)) throw new Error(`FFmpeg 运行文件校验失败：${file.name}`);
  }
  if (!await verifyFile(resolve(sourceRoot, 'LICENSE.txt'), lock.licenseFile)) throw new Error('FFmpeg 许可证文件校验失败。');

  requireSafeGeneratedPath(target);
  const pending = resolve(generatedParent, `windows-x86_64.${process.pid}.pending`);
  requireSafeGeneratedPath(pending);
  await rm(pending, { recursive: true, force: true });
  await mkdir(resolve(pending, 'bin'), { recursive: true });
  for (const file of lock.runtimeFiles) await copyFile(resolve(sourceRoot, 'bin', file.name), resolve(pending, 'bin', file.name));
  await copyFile(resolve(sourceRoot, 'LICENSE.txt'), resolve(pending, lock.licenseFile.name));
  await writeFile(resolve(pending, 'SOURCE.txt'), sourceNotice, 'utf8');
  await writeFile(resolve(pending, 'runtime-manifest.json'), `${JSON.stringify({
    schemaVersion: lock.schemaVersion,
    bundleVersion: lock.bundleVersion,
    target: lock.target,
    license: lock.license,
    archiveSha256: lock.archiveSha256,
    upstreamSource: lock.upstreamSource,
    buildSource: lock.buildSource,
    files: lock.runtimeFiles
  }, null, 2)}\n`, 'utf8');
  await rm(target, { recursive: true, force: true });
  await rename(pending, target);
  if (!await verifyPrepared()) throw new Error('FFmpeg 运行资源发布后校验失败。');
}

await prepareRuntime();
console.log(`Windows 媒体工具已校验：FFmpeg ${lock.bundleVersion} (${lock.archiveSha256.slice(0, 12)})`);
